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

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Tree, type NodeApi, type NodeRendererProps } from 'react-arborist'

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
  is_tour_poi?: boolean
  is_narrated?: boolean
  default_visible?: boolean
  deleted_at?: string | null
  [key: string]: unknown
}

export interface PoiSelection {
  poi: PoiRow
}

// ─── Tree node model ─────────────────────────────────────────────────────────

interface TreeNode {
  id: string
  label: string
  type: 'category' | 'poi'
  count?: number
  category?: string
  poi?: PoiRow
  isDeleted?: boolean
  children?: TreeNode[]
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
}

/** Category display names for nicer labels */
const CATEGORY_LABELS: Record<string, string> = {
  FOOD_DRINK: 'Food & Drink',
  HISTORICAL_BUILDINGS: 'Historic Sites',
  WITCH_SHOP: 'Witch & Occult',
  PSYCHIC: 'Psychic & Tarot',
  TOUR_COMPANIES: 'Tour Companies',
  ENTERTAINMENT: 'Entertainment',
  PARKS_REC: 'Parks & Recreation',
  LODGING: 'Lodging',
  CIVIC: 'Civic',
  EDUCATION: 'Education',
  SHOPPING: 'Shopping',
  WORSHIP: 'Places of Worship',
  HEALTHCARE: 'Healthcare',
  OFFICES: 'Professional Services',
  FINANCE: 'Finance',
  AUTO_SERVICES: 'Auto Services',
}

function categoryLabel(cat: string): string {
  return CATEGORY_LABELS[cat] || cat
}

function buildTree(allPois: PoiRow[], filter: TreeFilter): TreeNode[] {
  let filtered = allPois
  if (!filter.showDeleted) filtered = filtered.filter((r) => !r.deleted_at)
  if (filter.tourOnly) filtered = filtered.filter((r) => r.is_tour_poi)
  if (filter.narratedOnly) filtered = filtered.filter((r) => r.is_narrated)
  if (filter.visibleOnly) filtered = filtered.filter((r) => r.default_visible)

  // Group by category → subcategory
  const byCat = new Map<string, PoiRow[]>()
  for (const row of filtered) {
    const cat = (row.category as string) || '(uncategorized)'
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

  return catEntries.map(([cat, rowsInCat]) => {
    // Check if this category has subcategories
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

    let children: TreeNode[]

    if (bySubcat.size === 0) {
      // No subcategories — flat list of POIs
      const sortedRows = [...rowsInCat].sort((a, b) => a.name.localeCompare(b.name))
      children = sortedRows.map((row) => ({
        id: `cat:${cat}|poi:${row.id}`,
        label: row.deleted_at ? `${row.name} (deleted)` : row.name,
        type: 'poi',
        category: cat,
        poi: row,
        isDeleted: !!row.deleted_at,
      }))
    } else {
      // Has subcategories — 3-level tree
      children = []

      // POIs without subcategory come first
      if (noSubcat.length > 0) {
        const sorted = [...noSubcat].sort((a, b) => a.name.localeCompare(b.name))
        for (const row of sorted) {
          children.push({
            id: `cat:${cat}|poi:${row.id}`,
            label: row.deleted_at ? `${row.name} (deleted)` : row.name,
            type: 'poi',
            category: cat,
            poi: row,
            isDeleted: !!row.deleted_at,
          })
        }
      }

      // Subcategory nodes
      const subEntries = [...bySubcat.entries()].sort((a, b) => b[1].length - a[1].length)
      for (const [sub, subRows] of subEntries) {
        const sortedSub = [...subRows].sort((a, b) => a.name.localeCompare(b.name))
        const subLabel = sub.replace(/^[A-Z_]+__/, '').replace(/_/g, ' ')
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
    }

    return {
      id: `cat:${cat}`,
      label: categoryLabel(cat),
      type: 'category',
      category: cat,
      count: rowsInCat.length,
      children,
    }
  })
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
  onSelect?: (selection: PoiSelection) => void
  onDataLoaded?: (pois: PoiRow[]) => void
  externalPois?: PoiRow[] | null
}

export function PoiTree({ onSelect, onDataLoaded, externalPois }: PoiTreeProps) {
  const [pois, setPois] = useState<PoiRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [searchTerm, setSearchTerm] = useState('')
  const [showDeleted, setShowDeleted] = useState(false)
  const [tourOnly, setTourOnly] = useState(false)
  const [narratedOnly, setNarratedOnly] = useState(false)
  const [visibleOnly, setVisibleOnly] = useState(false)
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

  const treeData = useMemo(() => {
    if (!effectivePois) return []
    return buildTree(effectivePois, { showDeleted, tourOnly, narratedOnly, visibleOnly })
  }, [effectivePois, showDeleted, tourOnly, narratedOnly, visibleOnly])

  const handleActivate = useCallback(
    (node: NodeApi<TreeNode>) => {
      if (node.data.type !== 'poi' || !node.data.poi) return
      onSelect?.({ poi: node.data.poi })
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
    return rows.length
  }, [effectivePois, showDeleted, tourOnly, narratedOnly, visibleOnly])

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
