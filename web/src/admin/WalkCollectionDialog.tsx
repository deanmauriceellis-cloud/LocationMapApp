// WalkCollectionDialog — S269 — "Build Collection from Walk" modal.
//
// Per-tour collection authoring. Walks the tour's polyline server-side, gets
// every POI in the **global pool** that's within bbox, plus that POI's
// minimum-distance from the polyline. The dialog renders the polyline +
// in-radius POIs on an embedded Leaflet map. The operator can:
//
//   • View the currently-saved collection list (default on open).
//   • Click "Compute Candidates" to WIPE the editing list and rebuild it
//     from `pool ∩ proximity` at the current slider radius. A confirm
//     dialog gates the wipe so accidental clicks don't lose manual edits.
//   • Manually tick / untick individual POIs after compute.
//   • Save → POSTs the operator's edited list to
//     `/admin/salem/tours/:tourId/walk-derived-collection`, which UPSERTs a
//     synthetic `salem_collections` row (auto_bake=false) and replaces
//     `salem_collection_entries` with the new IDs in walk order.
//
// The slider DOES NOT live-filter the saved list — it's only a parameter
// for the next Compute click. This matches the operator's stated model
// (S269): "Recompute wipes everything; manual edits persist until next
// wipe."

import { useEffect, useMemo, useRef, useState } from 'react'
import { MapContainer, TileLayer, Polyline, CircleMarker, Tooltip, useMap } from 'react-leaflet'
import L from 'leaflet'

interface Candidate {
  id: string
  name: string
  category: string
  lat: number
  lng: number
  geofence_radius_m: number
  year_established: number | null
  min_distance_m: number
  encounter_index: number
}

interface PoolMeta {
  id: string
  name: string
  categories: string[]
  require_historical_narration: boolean
  min_geofence_radius_m: number
  min_year_established: number | null
  max_year_established: number | null
}

interface WalkCandidatesResponse {
  tour: { id: string; name: string; theme: string }
  tour_polyline: { lat: number; lng: number }[]
  polyline_length_pts: number
  candidate_count: number
  candidates: Candidate[]
  saved: { filter_id: string; filter_name: string; poi_ids: string[] } | null
  pool: PoolMeta | null
  warning?: string
}

interface SaveResponse {
  ok: boolean
  filter_id: string
  filter_name: string
  poi_count: number
  note: string
}

interface WalkCollectionDialogProps {
  tourId: string
  tourName: string
  onClose: () => void
  onSaved: (result: SaveResponse) => void
}

const TILE_URL = '/api/admin/tiles/Salem-Custom/{z}/{x}/{y}'
const TILE_ATTRIBUTION = 'Bundled Witchy tiles — OSM + MassGIS'

const DEFAULT_RADIUS_M = 50
const MIN_RADIUS_M = 10
const MAX_RADIUS_M = 300

const POLYLINE_GREEN = '#15803d'
const POLYLINE_BORDER = '#052e16'
const POI_SELECTED = '#2563eb'   // blue — currently in editing list
const POI_UNCHECKED = '#94a3b8'  // slate — pool POI, in-radius, unchecked
const POI_FAR = '#fbbf24'        // amber — pool POI, out of radius (visible if toggle on)
// red `#dc2626` reserved for orphan markers — not currently rendered (S271).

function metresToFeet(m: number): number {
  return m * 3.28084
}

import { fetchJson } from '../lib/fetchJson'

/** Re-fit the map to the tour polyline + in-radius POIs on first load. */
function FitToContent({ polyline, pois }: { polyline: { lat: number; lng: number }[]; pois: Candidate[] }) {
  const map = useMap()
  const didFit = useRef(false)
  useEffect(() => {
    if (didFit.current) return
    if (!polyline.length && !pois.length) return
    const latlngs: L.LatLngExpression[] = []
    for (const p of polyline) latlngs.push([p.lat, p.lng])
    for (const p of pois) latlngs.push([p.lat, p.lng])
    if (!latlngs.length) return
    const bounds = L.latLngBounds(latlngs as L.LatLngTuple[])
    map.fitBounds(bounds, { padding: [40, 40] })
    didFit.current = true
  }, [map, polyline, pois])
  return null
}

export function WalkCollectionDialog({ tourId, tourName, onClose, onSaved }: WalkCollectionDialogProps) {
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [data, setData] = useState<WalkCandidatesResponse | null>(null)
  const [radius, setRadius] = useState(DEFAULT_RADIUS_M)
  const [showOutOfRadius, setShowOutOfRadius] = useState(false)
  const [selectedIds, setSelectedIds] = useState<Set<string>>(() => new Set())
  const [saving, setSaving] = useState(false)
  const [saveErr, setSaveErr] = useState<string | null>(null)
  // S269 — what the editing list was last sourced from. Drives the
  // "currently viewing" label + the Save-button-needs-Compute-first check
  // for fresh tours that have no saved state yet.
  const [listSource, setListSource] = useState<'saved' | 'computed' | 'empty'>('empty')

  // Fetch candidates once on open.
  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    fetchJson<WalkCandidatesResponse>(
      `/api/admin/salem/tours/${encodeURIComponent(tourId)}/collection-walk-candidates`,
    )
      .then((res) => {
        if (cancelled) return
        setData(res)
        // S269 — open with the SAVED state (operator's last manual edits).
        // Compute is now an explicit, destructive action via the button.
        if (res.saved?.poi_ids?.length) {
          setSelectedIds(new Set(res.saved.poi_ids))
          setListSource('saved')
        } else {
          setSelectedIds(new Set())
          setListSource('empty')
        }
        setLoading(false)
      })
      .catch((e: Error) => {
        if (cancelled) return
        setError(e.message)
        setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [tourId])

  const candidates = data?.candidates ?? []
  const candidateById = useMemo(() => {
    const m = new Map<string, Candidate>()
    for (const c of candidates) m.set(c.id, c)
    return m
  }, [candidates])
  const inRadius = useMemo(
    () => candidates.filter((c) => c.min_distance_m <= radius),
    [candidates, radius],
  )
  // POIs in the saved/editing list that aren't in the current candidate
  // set (e.g. global pool changed and dropped them). Surface these so the
  // operator knows their save will not include them (or can decide).
  const orphanedSelected = useMemo(() => {
    const out: string[] = []
    for (const id of selectedIds) if (!candidateById.has(id)) out.push(id)
    return out
  }, [selectedIds, candidateById])
  const visibleOnMap = showOutOfRadius ? candidates : inRadius

  const radiusFt = Math.round(metresToFeet(radius))

  const toggleSelected = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const handleCompute = () => {
    // S269 — explicit wipe-and-rebuild. Confirmation prompt before any
    // manual edits get tossed.
    const hasEdits = selectedIds.size > 0
    if (hasEdits) {
      const ok = window.confirm(
        `Wipe the current list (${selectedIds.size} POIs) and replace with ` +
          `pool ∩ proximity at ${radius} m? Manual edits will be lost.`,
      )
      if (!ok) return
    }
    const next = new Set<string>()
    for (const c of inRadius) next.add(c.id)
    setSelectedIds(next)
    setListSource('computed')
  }

  const clearAll = () => {
    if (selectedIds.size === 0) return
    const ok = window.confirm(`Clear all ${selectedIds.size} selected POIs?`)
    if (!ok) return
    setSelectedIds(new Set())
  }

  const handleSave = async () => {
    if (!data) return
    setSaving(true)
    setSaveErr(null)
    try {
      // Order saved IDs by encounter index (so display_order in PG matches
      // walk order). POIs not in the current candidate set (orphans from
      // pool changes) are dropped from the persisted list — they wouldn't
      // pass the pool filter on the runtime side anyway.
      const ordered = candidates
        .filter((c) => selectedIds.has(c.id))
        .map((c) => c.id)
      const result = await fetchJson<SaveResponse>(
        `/api/admin/salem/tours/${encodeURIComponent(tourId)}/walk-derived-collection`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ poi_ids: ordered }),
        },
      )
      onSaved(result)
    } catch (e) {
      setSaveErr((e as Error).message)
    } finally {
      setSaving(false)
    }
  }

  const selectedTotal = selectedIds.size
  const poolName = data?.pool?.name ?? null
  const poolMatchCount = data?.candidate_count ?? 0
  const sourceLabel =
    listSource === 'saved'
      ? `Showing saved state (${selectedTotal} POIs)`
      : listSource === 'computed'
      ? `Computed: ${selectedTotal} POIs from pool ∩ proximity ≤ ${radius} m`
      : 'No saved state — click "Compute Candidates" to populate'

  return (
    // z-[2000]: Leaflet's default pane z-indices climb to ~700, so a stock
    // Tailwind z-50 modal sits behind the underlying AdminMap. Anything
    // above the Leaflet popup pane works; 2000 leaves headroom.
    <div
      className="fixed inset-0 z-[2000] bg-black/60 flex items-center justify-center p-4"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-lg shadow-2xl w-full max-w-6xl max-h-[92vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200">
          <div>
            <h2 className="text-sm font-semibold text-slate-800">
              Build Collection from Walk
            </h2>
            <p className="text-xs text-slate-500">
              {tourName} <span className="text-slate-400">·</span> {tourId}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-slate-500 hover:text-slate-800 text-xl leading-none px-2"
            aria-label="Close"
          >
            ×
          </button>
        </div>

        {/* Pool banner */}
        <div className="px-4 py-2 border-b border-slate-200 bg-amber-50 text-xs text-amber-900">
          {poolName ? (
            <>
              <span className="font-semibold">Pool:</span> {poolName}{' '}
              <span className="text-amber-700">
                ({poolMatchCount} POIs match the pool ∩ tour bbox)
              </span>
              {poolMatchCount === 0 ? (
                <span className="ml-2 text-red-700 font-semibold">
                  ⚠ Empty — check the pool's filter parameters in the Collections tab.
                </span>
              ) : null}
            </>
          ) : (
            <span>
              No global pool authored. Showing all POIs with historical_narration as fallback.
            </span>
          )}
        </div>

        {/* Compute controls */}
        <div className="px-4 py-2 border-b border-slate-200 bg-slate-50 flex items-center gap-4 text-xs">
          <label className="flex items-center gap-2">
            <span className="text-slate-700 font-semibold">Proximity radius:</span>
            <input
              type="range"
              min={MIN_RADIUS_M}
              max={MAX_RADIUS_M}
              step={5}
              value={radius}
              onChange={(e) => setRadius(parseInt(e.target.value, 10))}
              className="w-48"
              disabled={loading}
            />
            <span className="text-slate-700 font-mono tabular-nums w-20 text-right">
              {radius} m / {radiusFt} ft
            </span>
          </label>
          <button
            type="button"
            onClick={handleCompute}
            disabled={loading || !!error || candidates.length === 0}
            className="text-xs px-3 py-1 rounded bg-indigo-600 text-white hover:bg-indigo-500 disabled:opacity-40 font-semibold"
            title="Wipe the current list and replace with pool ∩ proximity at the slider radius"
          >
            Compute Candidates
          </button>
          <label className="flex items-center gap-1 text-slate-600">
            <input
              type="checkbox"
              checked={showOutOfRadius}
              onChange={(e) => setShowOutOfRadius(e.target.checked)}
            />
            Show out-of-radius pool POIs
          </label>
          <div className="flex-1" />
          <button
            type="button"
            onClick={clearAll}
            disabled={loading || selectedIds.size === 0}
            className="text-xs px-2 py-1 rounded bg-slate-200 hover:bg-slate-300 disabled:opacity-40"
          >
            Clear all
          </button>
        </div>

        {/* Source / orphan banner */}
        <div className="px-4 py-1.5 border-b border-slate-200 bg-white text-xs text-slate-700 flex items-center gap-3">
          <span>{sourceLabel}</span>
          {orphanedSelected.length > 0 ? (
            <span className="text-red-700">
              · {orphanedSelected.length} saved POI{orphanedSelected.length === 1 ? '' : 's'} no
              longer in pool — will be dropped on Save.
            </span>
          ) : null}
        </div>

        {/* Body */}
        <div className="flex-1 min-h-0 flex">
          {/* Left: candidate list */}
          <div className="w-96 border-r border-slate-200 flex flex-col">
            <div className="flex-1 overflow-y-auto">
              {loading ? (
                <div className="px-3 py-8 text-xs text-slate-500 text-center">Loading…</div>
              ) : error ? (
                <div className="px-3 py-8 text-xs text-red-600 text-center">{error}</div>
              ) : (
                <>
                  {visibleOnMap.map((c) => {
                    const isSel = selectedIds.has(c.id)
                    const isFar = c.min_distance_m > radius
                    return (
                      <label
                        key={c.id}
                        className={`flex items-start gap-2 px-3 py-2 border-b border-slate-100 hover:bg-slate-50 cursor-pointer ${
                          isFar ? 'opacity-60' : ''
                        }`}
                      >
                        <input
                          type="checkbox"
                          checked={isSel}
                          onChange={() => toggleSelected(c.id)}
                          className="mt-0.5"
                        />
                        <div className="flex-1 min-w-0">
                          <div className="text-xs font-semibold text-slate-800 truncate">
                            {c.name}
                          </div>
                          <div className="text-[11px] text-slate-500 flex flex-wrap gap-x-2">
                            <span>{c.category}</span>
                            <span>
                              {Math.round(c.min_distance_m)} m
                              {isFar ? ' · out of radius' : ''}
                            </span>
                            <span>r={c.geofence_radius_m}m</span>
                            {c.year_established ? <span>{c.year_established}</span> : null}
                          </div>
                        </div>
                      </label>
                    )
                  })}
                  {visibleOnMap.length === 0 ? (
                    <div className="px-3 py-8 text-xs text-slate-500 text-center">
                      No pool POIs within radius. Widen the slider or check
                      "Show out-of-radius pool POIs".
                    </div>
                  ) : null}
                  {/* Orphans section: saved POIs no longer in the pool. */}
                  {orphanedSelected.length > 0 ? (
                    <div className="px-3 py-2 border-t border-red-200 bg-red-50">
                      <div className="text-[11px] font-semibold text-red-800 mb-1">
                        Saved but no longer in pool ({orphanedSelected.length})
                      </div>
                      {orphanedSelected.map((id) => (
                        <div key={id} className="text-[11px] text-red-700 truncate">
                          {id}
                        </div>
                      ))}
                    </div>
                  ) : null}
                </>
              )}
            </div>
          </div>

          {/* Right: map */}
          <div className="flex-1 relative">
            {loading || !data ? (
              <div className="absolute inset-0 flex items-center justify-center text-xs text-slate-500">
                {loading ? 'Loading polyline…' : (error ?? 'No data')}
              </div>
            ) : (
              <MapContainer
                center={[42.5215, -70.8967]}
                zoom={15}
                maxZoom={22}
                className="h-full w-full"
                zoomControl={true}
              >
                <TileLayer url={TILE_URL} attribution={TILE_ATTRIBUTION} maxZoom={22} maxNativeZoom={19} />
                <FitToContent polyline={data.tour_polyline} pois={inRadius} />
                {/* Tour polyline */}
                {data.tour_polyline.length >= 2 ? (
                  <>
                    <Polyline
                      positions={data.tour_polyline.map((p) => [p.lat, p.lng]) as L.LatLngTuple[]}
                      pathOptions={{
                        color: POLYLINE_BORDER,
                        weight: 9,
                        opacity: 0.9,
                        lineCap: 'round',
                        lineJoin: 'round',
                      }}
                    />
                    <Polyline
                      positions={data.tour_polyline.map((p) => [p.lat, p.lng]) as L.LatLngTuple[]}
                      pathOptions={{
                        color: POLYLINE_GREEN,
                        weight: 5,
                        opacity: 1,
                        lineCap: 'round',
                        lineJoin: 'round',
                      }}
                    />
                  </>
                ) : null}
                {/* Candidate POIs */}
                {visibleOnMap.map((c) => {
                  const isSel = selectedIds.has(c.id)
                  const isFar = c.min_distance_m > radius
                  const color = isFar ? POI_FAR : isSel ? POI_SELECTED : POI_UNCHECKED
                  return (
                    <CircleMarker
                      key={c.id}
                      center={[c.lat, c.lng]}
                      radius={isSel ? 7 : 5}
                      pathOptions={{
                        color,
                        fillColor: color,
                        fillOpacity: isSel ? 0.85 : 0.5,
                        weight: isSel ? 2 : 1,
                      }}
                      eventHandlers={{
                        click: () => toggleSelected(c.id),
                      }}
                    >
                      <Tooltip>
                        <div className="text-xs">
                          <div className="font-semibold">{c.name}</div>
                          <div>
                            {c.category} · {Math.round(c.min_distance_m)} m from path
                          </div>
                          <div className="text-slate-500">
                            {isSel ? 'Selected' : isFar ? 'Out of radius' : 'In radius'} — click to toggle
                          </div>
                        </div>
                      </Tooltip>
                    </CircleMarker>
                  )
                })}
                {/* Orphan markers (saved but pool-excluded) drawn in red. */}
                {orphanedSelected.map((_id) => null)}
              </MapContainer>
            )}
            {/* Legend */}
            <div className="absolute bottom-2 right-2 z-[1000] bg-white/95 rounded shadow border border-slate-200 px-2 py-1 text-[10px] text-slate-700">
              <div className="flex items-center gap-1"><span className="inline-block w-2.5 h-2.5 rounded-full" style={{ background: POI_SELECTED }} /> Selected</div>
              <div className="flex items-center gap-1"><span className="inline-block w-2.5 h-2.5 rounded-full" style={{ background: POI_UNCHECKED }} /> In pool, in radius</div>
              {showOutOfRadius ? (
                <div className="flex items-center gap-1"><span className="inline-block w-2.5 h-2.5 rounded-full" style={{ background: POI_FAR }} /> Out of radius</div>
              ) : null}
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="px-4 py-3 border-t border-slate-200 bg-slate-50 flex items-center gap-3">
          <div className="text-xs text-slate-600">
            {data ? (
              <>
                <span className="font-semibold text-slate-800">
                  {selectedTotal} POI{selectedTotal === 1 ? '' : 's'}
                </span>{' '}
                will be saved as this tour's collection.
              </>
            ) : null}
          </div>
          {saveErr ? <div className="text-xs text-red-600 truncate">{saveErr}</div> : null}
          <div className="flex-1" />
          <button
            type="button"
            onClick={onClose}
            disabled={saving}
            className="text-xs px-3 py-1.5 rounded bg-slate-200 hover:bg-slate-300 disabled:opacity-40"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleSave}
            disabled={saving || loading || !!error}
            className="text-xs px-3 py-1.5 rounded bg-indigo-600 text-white hover:bg-indigo-500 disabled:opacity-40"
          >
            {saving ? 'Saving…' : 'Save as Tour Collection'}
          </button>
        </div>
      </div>
    </div>
  )
}
