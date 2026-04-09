// PoiTree — Phase 9P.B Step 9P.8
//
// Left-pane tree view for the Salem POI admin tool. Lists all 1,720 active
// POIs across the three kinds (tour, business, narration) grouped under their
// kind and category, with a name search and a soft-deleted toggle. Activating
// a POI row emits an `onSelect` event which the AdminLayout will route to the
// admin map (9P.9) and the edit dialog (9P.10) in subsequent steps.
//
// ─── Data shape recon (Session 103, 2026-04-08) ─────────────────────────────
// The three POI tables use *different category vocabularies*:
//   tour      → 7 distinct values  in `category`        (witch_trials,
//                                                        visitor_services, …)
//   business  → 19 distinct values in `business_type`   (shop_retail,
//                                                        restaurant, …)
//   narration → 29 distinct values in `category`        (shop, restaurant,
//                                                        historic_site, …)
// `salem_narration_points.subcategory` is currently NULL for all 814 rows;
// `salem_tour_pois.subcategories` is a JSONB list of refinements (not a
// single column). Because of that the tree is **3 levels** —
//     kind → category → POI rows
// not the 4 levels (kind → category → subcategory → POI rows) implied by
// earlier planning notes. If/when narration's subcategory column is back-
// filled (or tour's JSONB list is promoted to a real column), add the fourth
// level here without changing the rest of the file.
//
// ─── Auth flow ──────────────────────────────────────────────────────────────
// The first call to /api/admin/salem/pois triggers the browser's native
// HTTP Basic Auth dialog (the cache-proxy /admin middleware returns 401 with
// WWW-Authenticate: Basic). To avoid racing the dialog with three parallel
// 401 responses (which can cause some browsers to show the prompt multiple
// times), the loader fires the smallest kind (`tour`, 45 rows) first and
// awaits it. Once the browser has cached credentials, the other two kinds
// fetch in parallel reusing the cache with no additional prompts.
//
// ─── Data caching strategy ──────────────────────────────────────────────────
// We fetch with `include_deleted=true` once at mount and filter the deleted
// rows out client-side. Toggling "Show soft-deleted POIs" rebuilds the tree
// from the cached rows — no re-fetch, no re-prompt.

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Tree, type NodeApi, type NodeRendererProps } from 'react-arborist'

// ─── Types ───────────────────────────────────────────────────────────────────

export type PoiKind = 'tour' | 'business' | 'narration'

/**
 * Raw POI row as returned by GET /admin/salem/pois. Each kind has its own
 * column set (see cache-proxy/salem-schema.sql); the fields below are the
 * minimum the tree needs. Other fields ride along in the index signature so
 * the edit dialog (9P.10) gets the full row when it consumes a selection.
 */
export interface PoiRow {
  id: string
  name: string
  lat: number
  lng: number
  category?: string         // tour & narration
  business_type?: string    // business
  deleted_at?: string | null
  [key: string]: unknown
}

export interface PoiSelection {
  kind: PoiKind
  poi: PoiRow
}

interface KindResponse {
  kind: PoiKind
  count: number
  pois: PoiRow[]
}

// ─── Tree node model ─────────────────────────────────────────────────────────

interface TreeNode {
  /** Unique node id used by react-arborist; dotted form for debugging. */
  id: string
  /** Visible label. */
  label: string
  type: 'kind' | 'category' | 'poi'
  /** Child count (kind & category nodes only). */
  count?: number
  /** Always present except on synthetic future nodes. */
  kind?: PoiKind
  /** Present on category & poi nodes. */
  category?: string
  /** Present on poi nodes only. */
  poi?: PoiRow
  /** Mirrors poi.deleted_at != null; lets the renderer style ghosted rows. */
  isDeleted?: boolean
  children?: TreeNode[]
}

// ─── Constants & helpers ─────────────────────────────────────────────────────

const KINDS: PoiKind[] = ['tour', 'business', 'narration']
const KIND_LABELS: Record<PoiKind, string> = {
  tour: 'Tour POIs',
  business: 'Businesses',
  narration: 'Narration Points',
}

function getCategory(kind: PoiKind, row: PoiRow): string {
  if (kind === 'business') return (row.business_type as string) || '(uncategorized)'
  return (row.category as string) || '(uncategorized)'
}

async function fetchKind(kind: PoiKind): Promise<PoiRow[]> {
  // include_deleted=true so we can show/hide soft-deleted rows client-side
  // without re-fetching. limit=2000 covers business (861) and narration (814)
  // with headroom; the cache-proxy caps at 5000 anyway.
  const res = await fetch(
    `/api/admin/salem/pois?kind=${kind}&include_deleted=true&limit=2000`,
    { credentials: 'same-origin' },
  )
  if (!res.ok) {
    throw new Error(
      `GET /admin/salem/pois?kind=${kind} → ${res.status} ${res.statusText}`,
    )
  }
  const body = (await res.json()) as KindResponse
  // PG `DOUBLE PRECISION` may arrive as a JSON number; node-postgres normally
  // hands them back as numbers, but defensively coerce in case the proxy
  // serializer ever string-ifies. The map view (9P.9) will need real numbers.
  return body.pois.map((p) => ({
    ...p,
    lat: typeof p.lat === 'string' ? parseFloat(p.lat) : p.lat,
    lng: typeof p.lng === 'string' ? parseFloat(p.lng) : p.lng,
  }))
}

function buildTree(
  byKind: Record<PoiKind, PoiRow[]>,
  showDeleted: boolean,
): TreeNode[] {
  const roots: TreeNode[] = []
  for (const kind of KINDS) {
    const rows = byKind[kind]
    const filtered = showDeleted ? rows : rows.filter((r) => !r.deleted_at)

    // Group by category
    const byCat = new Map<string, PoiRow[]>()
    for (const row of filtered) {
      const cat = getCategory(kind, row)
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

    const catNodes: TreeNode[] = catEntries.map(([cat, rowsInCat]) => {
      const sortedRows = [...rowsInCat].sort((a, b) =>
        a.name.localeCompare(b.name),
      )
      const poiNodes: TreeNode[] = sortedRows.map((row) => ({
        id: `kind:${kind}|cat:${cat}|poi:${row.id}`,
        label: row.deleted_at ? `${row.name} (deleted)` : row.name,
        type: 'poi',
        kind,
        category: cat,
        poi: row,
        isDeleted: !!row.deleted_at,
      }))
      return {
        id: `kind:${kind}|cat:${cat}`,
        label: cat,
        type: 'category',
        kind,
        category: cat,
        count: poiNodes.length,
        children: poiNodes,
      }
    })

    roots.push({
      id: `kind:${kind}`,
      label: KIND_LABELS[kind],
      type: 'kind',
      kind,
      count: filtered.length,
      children: catNodes,
    })
  }
  return roots
}

// ─── ResizeObserver hook ─────────────────────────────────────────────────────
// react-arborist needs explicit pixel dimensions for its virtualized list.
// Measure the scroll container with ResizeObserver and feed them in. Updating
// on resize keeps the tree responsive when the window resizes (and gives a
// future drag-handle for the left pane something to hook into).

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
// react-arborist's `searchMatch` predicate is asked about every node. The
// library handles ancestor visibility automatically — when a leaf matches,
// its kind/category ancestors are kept open in the rendered output even if
// the predicate returns false for them. We therefore only have to express
// "does THIS node match", which for our tree is "is this a POI whose name
// contains the search term."

function poiSearchMatch(node: NodeApi<TreeNode>, term: string): boolean {
  const t = term.trim().toLowerCase()
  if (!t) return true
  if (node.data.type === 'poi') {
    return node.data.label.toLowerCase().includes(t)
  }
  return false
}

// ─── Node renderer ───────────────────────────────────────────────────────────

function PoiNode({ node, style, dragHandle }: NodeRendererProps<TreeNode>) {
  const data = node.data
  const isLeaf = data.type === 'poi'
  const isContainer = !isLeaf

  const handleClick = () => {
    if (isContainer) {
      node.toggle()
    } else {
      node.select()
      node.activate()
    }
  }

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
      <span className="truncate flex-1">{data.label}</span>
      {isContainer && data.count !== undefined && (
        <span className="ml-auto text-xs text-slate-500 tabular-nums pl-2">
          {data.count}
        </span>
      )}
    </div>
  )
}

// ─── Component ───────────────────────────────────────────────────────────────

export interface PoiTreeProps {
  /** Called when the user activates a POI row (click or Enter). */
  onSelect?: (selection: PoiSelection) => void
  /**
   * Called once after the initial load completes successfully. The admin
   * map (9P.9) consumes the same dataset and uses this to avoid a second
   * fetch (and a second Basic Auth prompt race). Also fired again if the
   * tree later refetches (e.g. after a publish — wired in 9P.13).
   */
  onDataLoaded?: (byKind: Record<PoiKind, PoiRow[]>) => void
  /**
   * Externally-mutated POI data. When provided, the tree uses this snapshot
   * instead of its own fetched copy. The admin map drives this after a
   * successful drag-to-move so the tree's coordinate columns (which are not
   * displayed but ride along to the edit dialog) stay consistent with the
   * map's marker positions.
   */
  externalByKind?: Record<PoiKind, PoiRow[]> | null
}

export function PoiTree({ onSelect, onDataLoaded, externalByKind }: PoiTreeProps) {
  const [byKind, setByKind] = useState<Record<PoiKind, PoiRow[]> | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [searchTerm, setSearchTerm] = useState('')
  const [showDeleted, setShowDeleted] = useState(false)
  const [containerRef, size] = useElementSize()

  // External overrides the internal copy when present. The internal copy is
  // still loaded once at mount so the tree works standalone (and so the
  // onDataLoaded callback fires for parents that want to share the dataset).
  const effectiveByKind = externalByKind ?? byKind

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        // Sequential first call so the browser's Basic Auth prompt fires
        // exactly once. `tour` is the smallest (45 rows) so the user gets to
        // the auth dialog as quickly as possible.
        const tour = await fetchKind('tour')
        const [business, narration] = await Promise.all([
          fetchKind('business'),
          fetchKind('narration'),
        ])
        if (cancelled) return
        const data = { tour, business, narration }
        setByKind(data)
        onDataLoaded?.(data)
      } catch (e) {
        if (cancelled) return
        setError(e instanceof Error ? e.message : String(e))
      }
    }
    load()
    return () => {
      cancelled = true
    }
    // onDataLoaded is intentionally excluded from deps — we only want to
    // load once at mount, and the parent should pass a stable callback.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const treeData = useMemo(() => {
    if (!effectiveByKind) return []
    return buildTree(effectiveByKind, showDeleted)
  }, [effectiveByKind, showDeleted])

  const handleActivate = useCallback(
    (node: NodeApi<TreeNode>) => {
      if (node.data.type !== 'poi' || !node.data.poi || !node.data.kind) return
      onSelect?.({ kind: node.data.kind, poi: node.data.poi })
    },
    [onSelect],
  )

  // Counts for the toolbar — visible (filtered) totals so the user can see
  // the impact of the show-deleted toggle.
  const visibleCounts = useMemo(() => {
    if (!effectiveByKind) return null
    const visible = (rows: PoiRow[]) =>
      showDeleted ? rows.length : rows.filter((r) => !r.deleted_at).length
    const tour = visible(effectiveByKind.tour)
    const business = visible(effectiveByKind.business)
    const narration = visible(effectiveByKind.narration)
    return { tour, business, narration, total: tour + business + narration }
  }, [effectiveByKind, showDeleted])

  return (
    <div className="flex flex-col h-full min-h-0">
      {/* Toolbar — search + show-deleted toggle + counts */}
      <div className="px-3 py-2 border-b border-slate-200 bg-slate-50 space-y-2">
        <input
          type="search"
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          placeholder="Search POIs by name…"
          className="w-full px-2 py-1 text-sm border border-slate-300 rounded focus:outline-none focus:ring-1 focus:ring-sky-400"
        />
        <label className="flex items-center gap-2 text-xs text-slate-600 cursor-pointer">
          <input
            type="checkbox"
            checked={showDeleted}
            onChange={(e) => setShowDeleted(e.target.checked)}
          />
          Show soft-deleted POIs
        </label>
        {visibleCounts && (
          <div className="text-xs text-slate-500 tabular-nums">
            {visibleCounts.total.toLocaleString()} POIs
            <span className="text-slate-400">
              {' '}
              (tour {visibleCounts.tour} · biz {visibleCounts.business} ·
              narration {visibleCounts.narration})
            </span>
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
        ) : !effectiveByKind ? (
          <p className="p-3 text-xs text-slate-500 italic">Loading POIs…</p>
        ) : size.height === 0 || size.width === 0 ? null : (
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
        )}
      </div>
    </div>
  )
}
