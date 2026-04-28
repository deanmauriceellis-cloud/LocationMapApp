// PoiTree — Phase 9U (unified salem_pois table)
//
// Left-pane tree view for the Salem POI admin tool. Lists all POIs from the
// unified `salem_pois` table grouped by category, with filters for tour POIs,
// narrated POIs, and soft-deleted rows.
//
// ─── Tree structure ────────────────────────────────────────────────────────
// Single fetch: GET /api/admin/salem/pois?include_deleted=true&limit=5000
// Tree levels: category → POI rows (2 levels)
//
// ─── Auth flow ──────────────────────────────────────────────────────────────
// The first call to /api/admin/salem/pois triggers the browser's native
// HTTP Basic Auth dialog. Single fetch now — no race condition.

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { Tree, type NodeApi, type NodeRendererProps } from 'react-arborist'

// Context that lets PoiNode call onCategorySelect directly without going
// through node.activate() → onActivate (which react-arborist doesn't
// reliably fire for container nodes).
const CategorySelectCtx = createContext<((cat: string | null) => void) | null>(null)

// ─── Types ───────────────────────────────────────────────────────────────────

/**
 * Raw POI row as returned by GET /admin/salem/pois (unified table).
 */
export interface PoiRow {
  id: string
  name: string
  lat: number
  lng: number
  category?: string
  subcategory?: string
  // S190 — used by the admin tree to split HISTORICAL_BUILDINGS into
  // "Historic Buildings" (curated) vs "POI Hist. Landmark" (MassGIS MHC).
  // The DB column stays `category=HISTORICAL_BUILDINGS` for both.
  data_source?: string
  mhc_id?: string | null
  is_tour_poi?: boolean
  is_narrated?: boolean
  default_visible?: boolean
  has_announce_narration?: boolean
  // Classification flags (S165)
  is_historical_property?: boolean
  is_witch_trial_site?: boolean
  is_free_admission?: boolean
  is_indoor?: boolean
  is_family_friendly?: boolean
  deleted_at?: string | null
  // S162 — POI location verification
  lat_proposed?: number | null
  lng_proposed?: number | null
  location_status?: LocationStatus
  location_source?: string | null
  location_drift_m?: number | null
  location_geocoder_rating?: number | null
  location_verified_at?: string | null
  location_truth_of_record?: boolean
  [key: string]: unknown
}

// S190 — canonical taxonomy from salem_poi_categories /
// salem_poi_subcategories. Defined here so PoiTree, PoiEditDialog, and
// AdminLayout can share one type. The admin tool fetches both lists
// once at mount and re-fetches after a successful add.
export interface CategoryRow {
  id: string
  label: string
  color: string
  display_order: number
}

export interface SubcategoryRow {
  id: string
  category_id: string
  label: string
  slug: string
  display_order: number
}

export type LocationStatus =
  | 'unverified'
  | 'no_address'
  | 'no_match'
  | 'needs_review'
  | 'verified'
  | 'accepted'

export type LocationFilter = 'all' | LocationStatus

export interface PoiSelection {
  poi: PoiRow
}

export interface CategorySelection {
  /** Category key as stored on salem_pois.category, or null to clear. */
  category: string | null
}

// ─── Tree node model ─────────────────────────────────────────────────────────

type Severity = 'error' | 'warn' | 'info'

function severityRank(s: Severity | undefined): number {
  if (s === 'error') return 3
  if (s === 'warn') return 2
  if (s === 'info') return 1
  return 0
}

function maxSeverity(a: Severity | undefined, b: Severity | undefined): Severity | undefined {
  return severityRank(a) >= severityRank(b) ? a : b
}

interface TreeNode {
  id: string
  label: string
  type: 'category' | 'poi'
  count?: number
  category?: string
  poi?: PoiRow
  isDeleted?: boolean
  /** Aggregated lint severity. For POI leaves: this row's flag. For containers: max of descendants. */
  severity?: Severity
  /** For containers: count of descendant POIs that have a lint flag. */
  flaggedCount?: number
  children?: TreeNode[]
}

interface LintItem {
  entity_type?: string
  entity_id: string
}
interface LintCheck {
  id: string
  severity: Severity
  items: LintItem[]
}
interface LintResponse {
  checks: LintCheck[]
}

async function fetchQualityFlags(): Promise<Map<string, Severity>> {
  const flags = new Map<string, Severity>()
  try {
    const res = await fetch('/api/admin/salem/lint', { credentials: 'same-origin' })
    if (!res.ok) return flags
    const data = (await res.json()) as LintResponse
    for (const chk of data.checks ?? []) {
      for (const item of chk.items ?? []) {
        if (item.entity_type && item.entity_type !== 'poi') continue
        const existing = flags.get(item.entity_id)
        const next = maxSeverity(existing, chk.severity)
        if (next) flags.set(item.entity_id, next)
      }
    }
  } catch {
    // Lint endpoint failure is non-fatal — tree just renders without dots.
  }
  return flags
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

async function fetchPois(): Promise<PoiRow[]> {
  const res = await fetch(
    `/api/admin/salem/pois?include_deleted=true&limit=5000`,
    { credentials: 'same-origin' },
  )
  if (!res.ok) {
    throw new Error(`GET /admin/salem/pois → ${res.status} ${res.statusText}`)
  }
  const body = await res.json()
  return (body.pois as PoiRow[]).map((p) => ({
    ...p,
    lat: typeof p.lat === 'string' ? parseFloat(p.lat) : p.lat,
    lng: typeof p.lng === 'string' ? parseFloat(p.lng) : p.lng,
  }))
}

interface TreeFilter {
  showDeleted: boolean
  tourOnly: boolean
  narratedOnly: boolean
  visibleOnly: boolean
  location: LocationFilter
}

// S190 — overrides for synthetic admin-tree-only category keys. The
// underlying DB category stays HISTORICAL_BUILDINGS for both buckets;
// these keys exist only so the tree node + map filter can distinguish
// curated entries from MassGIS MHC imports. Real categories pull their
// labels from the DB (salem_poi_categories) via makeCategoryLabelLookup.
const SYNTHETIC_CATEGORY_LABELS: Record<string, string> = {
  'HISTORICAL_BUILDINGS:non_mhc': 'Historic Buildings',
  'HISTORICAL_BUILDINGS:mhc': 'POI Hist. Landmark',
  // S191 — cross-cutting data_source views. Not real categories; show every
  // POI whose data_source mentions the source, regardless of stored category.
  'SOURCE:destination_salem': 'Destination Salem (source)',
  'SOURCE:haunted_happenings': 'Haunted Happenings (source)',
}

/**
 * Build a label-lookup function from the canonical category list. The
 * DB row's `label` is the source of truth — tree, editor dropdown, and
 * map legend all share this lookup so they can never drift apart.
 */
export function makeCategoryLabelLookup(
  categories: CategoryRow[],
): (cat: string) => string {
  const byId = new Map<string, string>()
  for (const c of categories) byId.set(c.id, c.label)
  return (cat: string): string => {
    if (Object.prototype.hasOwnProperty.call(SYNTHETIC_CATEGORY_LABELS, cat)) {
      return SYNTHETIC_CATEGORY_LABELS[cat]
    }
    return byId.get(cat) ?? cat
  }
}

/** Fallback used when the categories list hasn't loaded yet. Returns the raw id. */
export function categoryLabel(cat: string): string {
  return SYNTHETIC_CATEGORY_LABELS[cat] ?? cat
}

// ─── S190 synthetic Historic-Buildings split ─────────────────────────────────
// The admin tree splits HISTORICAL_BUILDINGS rows into two display groups:
//   - "Historic Buildings"    (data_source NOT LIKE '%massgis%')
//   - "POI Hist. Landmark"    (data_source LIKE '%massgis%')
// Both still save with category=HISTORICAL_BUILDINGS in the DB, and the
// Android module / narration / layer logic is untouched. Only the tree node
// keying and the AdminMap categoryFilter understand the synthetic suffixes.
export const HIST_BUILDINGS_FILTER = 'HISTORICAL_BUILDINGS:non_mhc'
export const HIST_LANDMARK_FILTER = 'HISTORICAL_BUILDINGS:mhc'

// S191 — synthetic source-based filters. Cross-cutting (any category).
export const SOURCE_DESTSALEM_FILTER = 'SOURCE:destination_salem'
export const SOURCE_HAUNTED_FILTER = 'SOURCE:haunted_happenings'

const SOURCE_FILTERS: Record<string, string> = {
  [SOURCE_DESTSALEM_FILTER]: 'destination_salem',
  [SOURCE_HAUNTED_FILTER]: 'haunted_happenings',
}

export function isMassgisHistorical(poi: { category?: string; data_source?: string }): boolean {
  if (poi.category !== 'HISTORICAL_BUILDINGS') return false
  return ((poi.data_source ?? '') as string).toLowerCase().includes('massgis')
}

function dataSourceContains(poi: { data_source?: string }, needle: string): boolean {
  return ((poi.data_source ?? '') as string).toLowerCase().includes(needle)
}

/** Predicate used by AdminMap to interpret synthetic category filter keys. */
export function categoryFilterMatches(
  poi: { category?: string; data_source?: string },
  filter: string,
): boolean {
  if (filter === HIST_LANDMARK_FILTER) return isMassgisHistorical(poi)
  if (filter === HIST_BUILDINGS_FILTER) {
    return poi.category === 'HISTORICAL_BUILDINGS' && !isMassgisHistorical(poi)
  }
  const sourceNeedle = SOURCE_FILTERS[filter]
  if (sourceNeedle) return dataSourceContains(poi, sourceNeedle)
  return (poi.category ?? '') === filter
}

function buildTree(
  allPois: PoiRow[],
  filter: TreeFilter,
  allSubcategories: SubcategoryRow[],
  labelLookup: (cat: string) => string,
  qualityFlags: Map<string, Severity>,
): TreeNode[] {
  // Roll a leaf-POI severity up to its enclosing container nodes by walking
  // children once at the end of buildTree. Defined inline so it shares the
  // qualityFlags closure.
  function rollUp(node: TreeNode): { severity: Severity | undefined; flagged: number } {
    if (node.type === 'poi') {
      const sev = node.poi ? qualityFlags.get(node.poi.id) : undefined
      node.severity = sev
      return { severity: sev, flagged: sev ? 1 : 0 }
    }
    let acc: Severity | undefined
    let flagged = 0
    for (const child of node.children ?? []) {
      const childAgg = rollUp(child)
      acc = maxSeverity(acc, childAgg.severity)
      flagged += childAgg.flagged
    }
    node.severity = acc
    node.flaggedCount = flagged
    return { severity: acc, flagged }
  }
  // (rollUp invoked at end of buildTree below)

  let filtered = allPois
  if (!filter.showDeleted) filtered = filtered.filter((r) => !r.deleted_at)
  if (filter.tourOnly) filtered = filtered.filter((r) => r.is_tour_poi)
  if (filter.narratedOnly) filtered = filtered.filter((r) => r.is_narrated)
  if (filter.visibleOnly) filtered = filtered.filter((r) => r.default_visible)
  if (filter.location !== 'all') {
    filtered = filtered.filter((r) => (r.location_status ?? 'unverified') === filter.location)
  }

  // Group by category → subcategory.
  //
  // S190 — split HISTORICAL_BUILDINGS into two synthetic display buckets so
  // the tree distinguishes curated Historic Buildings from MassGIS MHC Hist.
  // Landmarks. Both keep DB category=HISTORICAL_BUILDINGS; only the tree key
  // (and the AdminMap filter that mirrors it) carries the suffix.
  const byCat = new Map<string, PoiRow[]>()
  for (const row of filtered) {
    let cat = (row.category as string) || '(uncategorized)'
    if (cat === 'HISTORICAL_BUILDINGS') {
      cat = isMassgisHistorical(row) ? HIST_LANDMARK_FILTER : HIST_BUILDINGS_FILTER
    }
    let bucket = byCat.get(cat)
    if (!bucket) {
      bucket = []
      byCat.set(cat, bucket)
    }
    bucket.push(row)
  }

  // Build category nodes — sorted by descending count, ties by name
  const catEntries = [...byCat.entries()].sort((a, b) => {
    if (b[1].length !== a[1].length) return b[1].length - a[1].length
    return a[0].localeCompare(b[0])
  })

  // Index canonical subcategories by category_id for fast lookup. Synthetic
  // category keys (HISTORICAL_BUILDINGS:mhc / :non_mhc) reuse the base
  // HISTORICAL_BUILDINGS subcategory list.
  const subsByCat = new Map<string, SubcategoryRow[]>()
  for (const s of allSubcategories) {
    const arr = subsByCat.get(s.category_id) ?? []
    if (arr.length === 0) subsByCat.set(s.category_id, arr)
    arr.push(s)
  }
  for (const arr of subsByCat.values()) {
    arr.sort((a, b) => a.display_order - b.display_order)
  }

  // S191 — synthetic source-based virtual categories prepended at the top of
  // the tree. These cross-cut real categories: a POI from data_source
  // "destination_salem+openstreetmap" appears here AND under its real category.
  // Flat children list (no subcategory layer) since these are saved-search views.
  const sourceNodes: TreeNode[] = []
  for (const [key, needle] of Object.entries(SOURCE_FILTERS)) {
    const sourceRows = filtered
      .filter((r) => dataSourceContains(r, needle))
      .sort((a, b) => a.name.localeCompare(b.name))
    const node: TreeNode = {
      id: `cat:${key}`,
      label: SYNTHETIC_CATEGORY_LABELS[key],
      type: 'category',
      category: key,
      count: sourceRows.length,
      children: sourceRows.map((row): TreeNode => ({
        id: `cat:${key}|poi:${row.id}`,
        label: row.deleted_at ? `${row.name} (deleted)` : row.name,
        type: 'poi',
        category: key,
        poi: row,
        isDeleted: !!row.deleted_at,
      })),
    }
    sourceNodes.push(node)
  }

  const realNodes: TreeNode[] = catEntries.map(([cat, rowsInCat]): TreeNode => {
    // S190 — bucket POIs by their stored subcategory; collect rows whose
    // subcategory is null/empty into a separate "(no subcategory)" bucket
    // so the operator can see which POIs still need a subcategory assigned.
    const bySubcat = new Map<string, PoiRow[]>()
    const noSubcat: PoiRow[] = []
    for (const row of rowsInCat) {
      const sub = row.subcategory as string | undefined
      if (sub) {
        let bucket = bySubcat.get(sub)
        if (!bucket) {
          bucket = []
          bySubcat.set(sub, bucket)
        }
        bucket.push(row)
      } else {
        noSubcat.push(row)
      }
    }

    const baseCat = cat.split(':')[0]
    const canonSubs = subsByCat.get(baseCat) ?? []

    const children: TreeNode[] = []

    // "(no subcategory)" bucket first — only when there are POIs without
    // an assignment, otherwise we'd add an empty noise node to every category.
    if (noSubcat.length > 0) {
      const sorted = [...noSubcat].sort((a, b) => a.name.localeCompare(b.name))
      children.push({
        id: `cat:${cat}|sub:__none__`,
        label: '(no subcategory)',
        type: 'category',
        category: cat,
        count: sorted.length,
        children: sorted.map((row) => ({
          id: `cat:${cat}|sub:__none__|poi:${row.id}`,
          label: row.deleted_at ? `${row.name} (deleted)` : row.name,
          type: 'poi',
          category: cat,
          poi: row,
          isDeleted: !!row.deleted_at,
        })),
      })
    }

    // Canonical subcategories in display_order, including empty ones — the
    // operator needs to see what subcategories exist so they can assign POIs
    // and create new ones when something doesn't fit.
    for (const sub of canonSubs) {
      const subRows = bySubcat.get(sub.id) ?? []
      bySubcat.delete(sub.id)
      const sortedSub = [...subRows].sort((a, b) => a.name.localeCompare(b.name))
      children.push({
        id: `cat:${cat}|sub:${sub.id}`,
        label: sub.label,
        type: 'category',
        category: cat,
        count: sortedSub.length,
        children: sortedSub.map((row) => ({
          id: `cat:${cat}|sub:${sub.id}|poi:${row.id}`,
          label: row.deleted_at ? `${row.name} (deleted)` : row.name,
          type: 'poi',
          category: cat,
          poi: row,
          isDeleted: !!row.deleted_at,
        })),
      })
    }

    // Anything left in bySubcat is a legacy/non-canonical subcategory value
    // assigned to actual POIs (e.g. HISTORICAL_BUILDINGS rows holding the
    // ENTERTAINMENT__tour_operators value). Surface it tagged as legacy
    // rather than dropping it silently — operator can drill in and fix.
    const leftover = [...bySubcat.entries()].sort((a, b) => b[1].length - a[1].length)
    for (const [sub, subRows] of leftover) {
      const sortedSub = [...subRows].sort((a, b) => a.name.localeCompare(b.name))
      const subLabel = sub.replace(/^[A-Z_]+__/, '').replace(/_/g, ' ') + ' (legacy)'
      children.push({
        id: `cat:${cat}|sub:${sub}`,
        label: subLabel,
        type: 'category',
        category: cat,
        count: sortedSub.length,
        children: sortedSub.map((row) => ({
          id: `cat:${cat}|sub:${sub}|poi:${row.id}`,
          label: row.deleted_at ? `${row.name} (deleted)` : row.name,
          type: 'poi',
          category: cat,
          poi: row,
          isDeleted: !!row.deleted_at,
        })),
      })
    }

    return {
      id: `cat:${cat}`,
      label: labelLookup(cat),
      type: 'category',
      category: cat,
      count: rowsInCat.length,
      children,
    }
  })

  const allTopNodes = [...sourceNodes, ...realNodes]
  for (const top of allTopNodes) rollUp(top)
  return allTopNodes
}

// ─── ResizeObserver hook ─────────────────────────────────────────────────────

function useElementSize(): [
  React.RefObject<HTMLDivElement | null>,
  { width: number; height: number },
] {
  const ref = useRef<HTMLDivElement>(null)
  const [size, setSize] = useState({ width: 0, height: 0 })
  useEffect(() => {
    const el = ref.current
    if (!el) return
    const ro = new ResizeObserver((entries) => {
      const e = entries[0]
      if (!e) return
      setSize({
        width: Math.floor(e.contentRect.width),
        height: Math.floor(e.contentRect.height),
      })
    })
    ro.observe(el)
    return () => ro.disconnect()
  }, [])
  return [ref, size]
}

// ─── Search match ────────────────────────────────────────────────────────────

function poiSearchMatch(node: NodeApi<TreeNode>, term: string): boolean {
  const t = term.trim().toLowerCase()
  if (!t) return true
  if (node.data.type === 'poi') {
    return node.data.label.toLowerCase().includes(t)
  }
  return false
}

// ─── Node renderer ───────────────────────────────────────────────────────────

function severityDotClass(s: Severity | undefined): string {
  if (s === 'error') return 'bg-rose-500'
  if (s === 'warn') return 'bg-amber-400'
  if (s === 'info') return 'bg-sky-400'
  return ''
}

function PoiNode({ node, style, dragHandle }: NodeRendererProps<TreeNode>) {
  const data = node.data
  const isLeaf = data.type === 'poi'
  const isContainer = !isLeaf
  const onCategorySelect = useContext(CategorySelectCtx)

  const handleClick = () => {
    if (isContainer) {
      node.toggle()
      // Top-level category nodes (no |sub: segment) drive the map filter.
      // Call the context callback directly — node.activate() is unreliable
      // for container nodes in react-arborist.
      if (data.type === 'category' && !data.poi && !node.data.id.includes('|sub:') && onCategorySelect) {
        onCategorySelect(data.category ?? null)
      }
    } else {
      node.select()
      node.activate()
    }
  }

  const dotClass = severityDotClass(data.severity)
  const dotTitle =
    data.severity && isContainer
      ? `${data.flaggedCount ?? 0} POI${(data.flaggedCount ?? 0) === 1 ? '' : 's'} flagged (${data.severity})`
      : data.severity
        ? `Lint: ${data.severity}`
        : ''

  return (
    <div
      style={style}
      ref={dragHandle}
      onClick={handleClick}
      className={[
        'flex items-center gap-1 px-1 cursor-pointer text-sm select-none',
        node.isSelected ? 'bg-sky-100' : 'hover:bg-slate-100',
        data.isDeleted ? 'text-slate-400 italic' : '',
      ].join(' ')}
      title={isLeaf ? data.poi?.id : data.label}
    >
      {isContainer ? (
        <span className="w-4 inline-flex justify-center text-slate-500">
          {node.isOpen ? '▾' : '▸'}
        </span>
      ) : (
        <span className="w-4 inline-flex justify-center text-slate-300">·</span>
      )}
      {dotClass ? (
        <span
          className={`inline-block w-2 h-2 rounded-full flex-shrink-0 ${dotClass}`}
          title={dotTitle}
          aria-label={dotTitle}
        />
      ) : (
        <span className="inline-block w-2 h-2 flex-shrink-0" aria-hidden />
      )}
      <span className="truncate flex-1">{data.label}</span>
      {isContainer && data.count !== undefined && (
        <span className="ml-auto text-xs text-slate-500 tabular-nums pl-2">
          {data.flaggedCount && data.flaggedCount > 0 ? (
            <>
              <span className={`mr-1 ${data.severity === 'error' ? 'text-rose-600' : data.severity === 'warn' ? 'text-amber-600' : 'text-sky-600'}`}>
                {data.flaggedCount}
              </span>
              <span className="text-slate-400">/</span>{' '}
            </>
          ) : null}
          {data.count}
        </span>
      )}
    </div>
  )
}

// ─── Component ───────────────────────────────────────────────────────────────

export interface PoiTreeProps {
  onSelect?: (selection: PoiSelection) => void
  /** Fires when a category node is clicked. Same category twice = toggle off (null). */
  onCategorySelect?: (selection: CategorySelection) => void
  onDataLoaded?: (pois: PoiRow[]) => void
  externalPois?: PoiRow[] | null
  /**
   * S190 — canonical categories from salem_poi_categories. Drives the
   * label lookup for tree category nodes so the tree, the editor
   * dropdown, and the map legend all show identical labels (the DB row's
   * `label` is the single source of truth).
   */
  categories?: CategoryRow[]
  /**
   * S190 — canonical subcategories from salem_poi_subcategories. Drives
   * the tree's subcategory nodes so EVERY defined subcategory shows under
   * its parent category (even with zero POIs assigned), plus a synthetic
   * "(no subcategory)" bucket for unassigned POIs.
   */
  subcategories?: SubcategoryRow[]
}

export function PoiTree({
  onSelect,
  onCategorySelect,
  onDataLoaded,
  externalPois,
  categories,
  subcategories,
}: PoiTreeProps) {
  const lastCategoryRef = useRef<string | null>(null)
  const [pois, setPois] = useState<PoiRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [searchTerm, setSearchTerm] = useState('')
  const [showDeleted, setShowDeleted] = useState(false)
  const [tourOnly, setTourOnly] = useState(false)
  const [narratedOnly, setNarratedOnly] = useState(false)
  const [visibleOnly, setVisibleOnly] = useState(false)
  const [locationFilter, setLocationFilter] = useState<LocationFilter>('all')
  const [qualityFlags, setQualityFlags] = useState<Map<string, Severity>>(() => new Map())
  const [containerRef, size] = useElementSize()

  const effectivePois = externalPois ?? pois

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const data = await fetchPois()
        if (cancelled) return
        setPois(data)
        onDataLoaded?.(data)
      } catch (e) {
        if (cancelled) return
        setError(e instanceof Error ? e.message : String(e))
      }
    }
    load()
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Quality-flag fetch — drives the colored indicator dots. Refreshed whenever
  // the POI list reloads so a Suppress / save in another tab eventually clears
  // the dot. S195 — also refreshes on the 'salem-lint-changed' window event,
  // dispatched by PoiEditDialog after the operator suppresses a flag inline.
  useEffect(() => {
    let cancelled = false
    const refresh = () => {
      fetchQualityFlags().then((flags) => {
        if (!cancelled) setQualityFlags(flags)
      })
    }
    refresh()
    window.addEventListener('salem-lint-changed', refresh)
    return () => {
      cancelled = true
      window.removeEventListener('salem-lint-changed', refresh)
    }
  }, [pois])

  const labelLookup = useMemo(
    () => makeCategoryLabelLookup(categories ?? []),
    [categories],
  )

  const treeData = useMemo(() => {
    if (!effectivePois) return []
    return buildTree(
      effectivePois,
      { showDeleted, tourOnly, narratedOnly, visibleOnly, location: locationFilter },
      subcategories ?? [],
      labelLookup,
      qualityFlags,
    )
  }, [effectivePois, showDeleted, tourOnly, narratedOnly, visibleOnly, locationFilter, subcategories, labelLookup, qualityFlags])

  // Category toggle handler — called directly from PoiNode via context.
  const handleCategoryClick = useCallback((cat: string | null) => {
    if (!cat) return
    const next = lastCategoryRef.current === cat ? null : cat
    lastCategoryRef.current = next
    onCategorySelect?.({ category: next })
  }, [onCategorySelect])

  const handleActivate = useCallback(
    (node: NodeApi<TreeNode>) => {
      if (node.data.type === 'poi' && node.data.poi) {
        onSelect?.({ poi: node.data.poi })
      }
    },
    [onSelect],
  )

  const visibleCount = useMemo(() => {
    if (!effectivePois) return null
    let rows = effectivePois
    if (!showDeleted) rows = rows.filter((r) => !r.deleted_at)
    if (tourOnly) rows = rows.filter((r) => r.is_tour_poi)
    if (narratedOnly) rows = rows.filter((r) => r.is_narrated)
    if (visibleOnly) rows = rows.filter((r) => r.default_visible)
    if (locationFilter !== 'all') {
      rows = rows.filter((r) => (r.location_status ?? 'unverified') === locationFilter)
    }
    return rows.length
  }, [effectivePois, showDeleted, tourOnly, narratedOnly, visibleOnly, locationFilter])

  return (
    <div className="flex flex-col h-full min-h-0">
      {/* Toolbar */}
      <div className="px-3 py-2 border-b border-slate-200 bg-slate-50 space-y-2">
        <input
          type="search"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          placeholder="Search POIs by name…"
          className="w-full px-2 py-1 text-sm border border-slate-300 rounded focus:outline-none focus:ring-1 focus:ring-sky-400"
        />
        <div className="flex flex-wrap gap-x-4 gap-y-1">
          <label className="flex items-center gap-1.5 text-xs text-slate-600 cursor-pointer">
            <input type="checkbox" checked={showDeleted} onChange={(e) => setShowDeleted(e.target.checked)} />
            Deleted
          </label>
          <label className="flex items-center gap-1.5 text-xs text-slate-600 cursor-pointer">
            <input type="checkbox" checked={tourOnly} onChange={(e) => setTourOnly(e.target.checked)} />
            Tour only
          </label>
          <label className="flex items-center gap-1.5 text-xs text-slate-600 cursor-pointer">
            <input type="checkbox" checked={narratedOnly} onChange={(e) => setNarratedOnly(e.target.checked)} />
            Narrated only
          </label>
          <label className="flex items-center gap-1.5 text-xs text-slate-600 cursor-pointer">
            <input type="checkbox" checked={visibleOnly} onChange={(e) => setVisibleOnly(e.target.checked)} />
            Visible only
          </label>
          <label className="flex items-center gap-1.5 text-xs text-slate-600">
            Location:
            <select
              value={locationFilter}
              onChange={(e) => setLocationFilter(e.target.value as LocationFilter)}
              className="text-xs border border-slate-300 rounded px-1 py-0.5 bg-white"
            >
              <option value="all">any</option>
              <option value="needs_review">needs review</option>
              <option value="no_address">no address</option>
              <option value="no_match">no match</option>
              <option value="unverified">unverified</option>
              <option value="verified">verified</option>
              <option value="accepted">accepted</option>
            </select>
          </label>
        </div>
        {visibleCount !== null && (
          <div className="text-xs text-slate-500 tabular-nums">
            {visibleCount.toLocaleString()} POIs
            {effectivePois && (
              <span className="text-slate-400"> / {effectivePois.length.toLocaleString()} total</span>
            )}
          </div>
        )}
      </div>

      {/* Tree pane */}
      <div ref={containerRef} className="flex-1 min-h-0 overflow-hidden">
        {error ? (
          <div className="p-3 m-2 text-xs text-rose-700 bg-rose-50 border border-rose-200 rounded">
            <p className="font-semibold mb-1">Failed to load POIs.</p>
            <p className="font-mono whitespace-pre-wrap break-words">{error}</p>
          </div>
        ) : !effectivePois ? (
          <p className="p-3 text-xs text-slate-500 italic">Loading POIs…</p>
        ) : size.height === 0 || size.width === 0 ? null : (
          <CategorySelectCtx.Provider value={handleCategoryClick}>
            <Tree<TreeNode>
              data={treeData}
              width={size.width}
              height={size.height}
              indent={16}
              rowHeight={24}
              paddingTop={4}
              paddingBottom={4}
              openByDefault={false}
              disableMultiSelection={true}
              disableEdit={true}
              disableDrag={true}
              disableDrop={true}
              searchTerm={searchTerm}
              searchMatch={poiSearchMatch}
              onActivate={handleActivate}
            >
              {PoiNode}
            </Tree>
          </CategorySelectCtx.Provider>
        )}
      </div>
    </div>
  )
}
