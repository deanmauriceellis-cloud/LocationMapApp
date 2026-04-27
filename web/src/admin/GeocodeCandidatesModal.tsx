// GeocodeCandidatesModal — S187
//
// Opens from the Lint tab "Geocodes" button on a POI item. Calls
// GET /api/admin/salem/lint/poi/:id/geocode-candidates, lists up to 5
// tiger.geocode() candidates with rating/lat/lng/normalized_address/distance.
// Each row has [Use This Location] which POSTs to /admin/salem/pois/:id/move.
// A bottom action [Validate Current Location] POSTs to
// /admin/salem/lint/poi/:id/mark-verified — marks the stored coords as the
// truth-of-record without changing them.

import { useCallback, useEffect, useState } from 'react'

interface GeocodePoi {
  id: string
  name: string
  lat: number
  lng: number
  address: string | null
  location_status: string | null
  location_verified_at: string | null
}

interface GeocodeCandidate {
  rating: number
  lat: number
  lng: number
  normalized_address: string
  distance_m: number
}

interface DuplicatePoi {
  id: string
  name: string
  lat: number
  lng: number
  address: string | null
  location_status: string | null
  deleted_at: string | null
  distance_m: number
  candidates?: GeocodeCandidate[]
  geocode_warning?: string
}

interface BestMatch {
  source_poi_id: string
  source_poi_name: string
  rating: number
  lat: number
  lng: number
  normalized_address: string
  distance_m: number
}

interface GeocodeResponse {
  poi: GeocodePoi
  candidates: GeocodeCandidate[]
  duplicates?: DuplicatePoi[]
  best_match?: BestMatch | null
  warning?: string
  error?: string
}

export interface GeocodeCandidatesModalProps {
  poiId: string | null
  open: boolean
  onClose: () => void
  /** Called after a successful Override or Validate so the parent can refresh. */
  onChanged: () => void
  /** Open the editor for a sibling duplicate POI. Modal closes first. */
  onOpenSibling?: (id: string) => void
  /** Pan/zoom the map to a sibling duplicate POI. Modal closes first. */
  onShowSiblingOnMap?: (id: string) => void
  /** Filter the POI map to ONLY focal + dupe ids and fly there. Modal closes. */
  onShowClusterOnMap?: (focalId: string, dupeIds: string[], label: string) => void
  /** Show this geocode candidate on the map relative to its source POI, with
   *  an accept / ignore / cancel panel. Modal closes first.
   *
   *  `allCandidates` carries every Tiger candidate from the cluster (focal +
   *  every dupe), enriched with their source POI metadata, so the preview
   *  panel's "Show all candidates" toggle can render them all on the map. */
  onPreviewCandidate?: (
    sourcePoiId: string,
    candidate: { lat: number; lng: number; rating: number; normalized_address: string; distance_m: number },
    allCandidates: Array<{
      source_poi_id: string
      source_poi_name: string
      source_lat: number
      source_lng: number
      source_address: string | null
      lat: number
      lng: number
      rating: number
      normalized_address: string
      distance_m: number
    }>,
  ) => void
}

export function GeocodeCandidatesModal({
  poiId, open, onClose, onChanged,
  onOpenSibling, onShowSiblingOnMap, onShowClusterOnMap, onPreviewCandidate,
}: GeocodeCandidatesModalProps) {
  const [data, setData] = useState<GeocodeResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [busyAction, setBusyAction] = useState<string | null>(null)

  // Wrap onPreviewCandidate to also build the all-candidates flat list (focal
  // + every dupe), so the map preview's "Show all candidates" toggle can
  // render them all at once.
  const handlePreviewCandidate = useCallback((sourcePoiId: string, c: GeocodeCandidate) => {
    if (!onPreviewCandidate || !data) return
    const all: Array<{
      source_poi_id: string
      source_poi_name: string
      source_lat: number
      source_lng: number
      source_address: string | null
      lat: number
      lng: number
      rating: number
      normalized_address: string
      distance_m: number
    }> = []
    for (const fc of data.candidates) {
      all.push({
        source_poi_id: data.poi.id,
        source_poi_name: data.poi.name,
        source_lat: data.poi.lat,
        source_lng: data.poi.lng,
        source_address: data.poi.address,
        ...fc,
      })
    }
    for (const d of (data.duplicates ?? [])) {
      for (const dc of (d.candidates ?? [])) {
        all.push({
          source_poi_id: d.id,
          source_poi_name: d.name,
          source_lat: d.lat,
          source_lng: d.lng,
          source_address: d.address,
          ...dc,
        })
      }
    }
    onPreviewCandidate(sourcePoiId, c, all)
  }, [onPreviewCandidate, data])

  const fetchCandidates = useCallback(async () => {
    if (!poiId) return
    setLoading(true)
    setErr(null)
    setData(null)
    try {
      const res = await fetch(
        `/api/admin/salem/lint/poi/${encodeURIComponent(poiId)}/geocode-candidates`,
        { credentials: 'same-origin' },
      )
      if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
      const body = (await res.json()) as GeocodeResponse
      setData(body)
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [poiId])

  useEffect(() => {
    if (open && poiId) void fetchCandidates()
  }, [open, poiId, fetchCandidates])

  const useCandidate = useCallback(async (c: GeocodeCandidate) => {
    if (!poiId || !data) return
    if (!window.confirm(
      `Override ${data.poi.name}\n` +
      `from (${data.poi.lat.toFixed(5)}, ${data.poi.lng.toFixed(5)})\n` +
      `to   (${c.lat.toFixed(5)}, ${c.lng.toFixed(5)}) — ${c.distance_m} m away?\n\n` +
      `Tiger normalized: ${c.normalized_address}`,
    )) return
    setBusyAction(`use:${poiId}:${c.rating}:${c.lat}`)
    try {
      const res = await fetch(`/api/admin/salem/pois/${encodeURIComponent(poiId)}/move`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ lat: c.lat, lng: c.lng }),
      })
      if (!res.ok) {
        const body = await res.json().catch(() => ({}))
        throw new Error(body.error || `${res.status} ${res.statusText}`)
      }
      onChanged()
      await fetchCandidates() // refresh distances
    } catch (e) {
      window.alert(`Override failed: ${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusyAction(null)
    }
  }, [poiId, data, fetchCandidates, onChanged])

  // Apply a dupe's geocode candidate to the FOCAL POI and soft-delete the
  // source dupe in one flow. Single confirm, two API calls. If the move
  // succeeds but the soft-delete fails, the focal is still updated and the
  // operator gets an alert with the failure reason.
  const useFromDupe = useCallback(async (
    dupe: DuplicatePoi, c: GeocodeCandidate,
  ) => {
    if (!poiId || !data) return
    if (!window.confirm(
      `Update focal POI "${data.poi.name}"\n` +
      `  from (${data.poi.lat.toFixed(5)}, ${data.poi.lng.toFixed(5)})\n` +
      `  to   (${c.lat.toFixed(5)}, ${c.lng.toFixed(5)})\n\n` +
      `Tiger normalized (from dupe "${dupe.name}"):\n  ${c.normalized_address}\n\n` +
      `Then soft-delete duplicate "${dupe.name}" (id ${dupe.id}).\n\n` +
      `Continue?`,
    )) return
    setBusyAction(`use-dupe:${dupe.id}:${c.rating}:${c.lat}`)
    try {
      // 1. Move focal
      const moveRes = await fetch(
        `/api/admin/salem/pois/${encodeURIComponent(poiId)}/move`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'same-origin',
          body: JSON.stringify({ lat: c.lat, lng: c.lng }),
        },
      )
      if (!moveRes.ok) {
        const body = await moveRes.json().catch(() => ({}))
        throw new Error(`move focal failed: ${body.error || `${moveRes.status} ${moveRes.statusText}`}`)
      }
      // 2. Soft-delete dupe
      const delRes = await fetch(
        `/api/admin/salem/pois/${encodeURIComponent(dupe.id)}`,
        { method: 'DELETE', credentials: 'same-origin' },
      )
      if (!delRes.ok) {
        const body = await delRes.json().catch(() => ({}))
        window.alert(
          `Focal POI was updated, but soft-deleting "${dupe.name}" failed: ` +
          `${body.error || `${delRes.status} ${delRes.statusText}`}`,
        )
      }
      onChanged()
      await fetchCandidates()
    } catch (e) {
      window.alert(`Action failed: ${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusyAction(null)
    }
  }, [poiId, data, fetchCandidates, onChanged])

  // Soft-delete a duplicate without touching the focal POI's coords.
  const softDeleteDupe = useCallback(async (dupe: DuplicatePoi) => {
    if (!data) return
    if (dupe.deleted_at) {
      window.alert(`"${dupe.name}" is already soft-deleted.`)
      return
    }
    if (!window.confirm(
      `Soft-delete duplicate "${dupe.name}" (id ${dupe.id})?\n\n` +
      `Sets deleted_at = NOW(). Reversible via the POI editor's restore action.`,
    )) return
    setBusyAction(`soft-del:${dupe.id}`)
    try {
      const res = await fetch(
        `/api/admin/salem/pois/${encodeURIComponent(dupe.id)}`,
        { method: 'DELETE', credentials: 'same-origin' },
      )
      if (!res.ok) {
        const body = await res.json().catch(() => ({}))
        throw new Error(body.error || `${res.status} ${res.statusText}`)
      }
      onChanged()
      await fetchCandidates()
    } catch (e) {
      window.alert(`Soft-delete failed: ${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusyAction(null)
    }
  }, [data, fetchCandidates, onChanged])

  const markVerified = useCallback(async () => {
    if (!poiId || !data) return
    if (!window.confirm(
      `Mark ${data.poi.name} as VERIFIED at its current coordinates ` +
      `(${data.poi.lat.toFixed(5)}, ${data.poi.lng.toFixed(5)})?\n\n` +
      `Sets location_status='verified' and stamps location_verified_at to now.`,
    )) return
    setBusyAction('verify')
    try {
      const res = await fetch(
        `/api/admin/salem/lint/poi/${encodeURIComponent(poiId)}/mark-verified`,
        { method: 'POST', credentials: 'same-origin' },
      )
      if (!res.ok) {
        const body = await res.json().catch(() => ({}))
        throw new Error(body.error || `${res.status} ${res.statusText}`)
      }
      onChanged()
      await fetchCandidates()
    } catch (e) {
      window.alert(`Validate failed: ${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setBusyAction(null)
    }
  }, [poiId, data, fetchCandidates, onChanged])

  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-[1000] flex items-center justify-center bg-black/40 p-4"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-lg shadow-xl w-full max-w-3xl max-h-[90vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <header className="px-5 py-3 border-b border-slate-200 flex items-baseline justify-between">
          <div className="min-w-0">
            <h2 className="text-base font-semibold text-slate-800 truncate">
              Geocode candidates
            </h2>
            {data && (
              <p className="text-xs text-slate-500 mt-0.5 truncate">{data.poi.name}</p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            className="text-slate-500 hover:text-slate-700 text-2xl leading-none"
            aria-label="Close"
          >
            ×
          </button>
        </header>

        {/* Body */}
        <div className="flex-1 min-h-0 overflow-y-auto">
          {loading && (
            <div className="px-5 py-4 text-slate-500 text-sm">Geocoding…</div>
          )}
          {err && (
            <div className="px-5 py-3 m-3 text-rose-700 bg-rose-50 border border-rose-200 rounded text-xs">
              {err}
            </div>
          )}
          {data && (
            <>
              {/* Current state */}
              <div className="px-5 py-3 bg-slate-50 border-b border-slate-200 text-xs">
                <div className="grid grid-cols-[100px_1fr] gap-y-1">
                  <span className="text-slate-500">Address</span>
                  <span className="text-slate-800 break-words">{data.poi.address || <em className="text-slate-400">none</em>}</span>
                  <span className="text-slate-500">Stored coords</span>
                  <span className="text-slate-800 tabular-nums">
                    {data.poi.lat.toFixed(5)}, {data.poi.lng.toFixed(5)}
                  </span>
                  <span className="text-slate-500">Status</span>
                  <span>
                    <StatusBadge status={data.poi.location_status} />
                    {data.poi.location_verified_at && (
                      <span className="text-slate-400 ml-2">
                        verified {new Date(data.poi.location_verified_at).toLocaleString()}
                      </span>
                    )}
                  </span>
                </div>
              </div>

              {/* Best match across the cluster */}
              {data.best_match && (
                <div className="px-5 py-2 bg-emerald-50 border-b border-emerald-200 text-xs">
                  <div className="font-medium text-emerald-800 mb-0.5">
                    Best match{(data.duplicates && data.duplicates.length > 0) ? ' across cluster' : ''}
                  </div>
                  <div className="text-emerald-900">
                    <RatingBadge rating={data.best_match.rating} />
                    <span className="ml-2 font-medium">{data.best_match.normalized_address}</span>
                  </div>
                  <div className="text-emerald-700 mt-0.5 tabular-nums">
                    {data.best_match.lat.toFixed(5)}, {data.best_match.lng.toFixed(5)} ·{' '}
                    <DistanceLabel meters={data.best_match.distance_m} /> ·
                    {' '}from <span className="font-medium">{data.best_match.source_poi_name}</span>
                    {data.best_match.source_poi_id !== data.poi.id && (
                      <span className="text-emerald-600"> (a duplicate)</span>
                    )}
                  </div>
                </div>
              )}

              {/* Warning */}
              {data.warning && (
                <div className="px-5 py-2 bg-amber-50 border-b border-amber-200 text-xs text-amber-800">
                  {data.warning}
                </div>
              )}

              {/* Focal POI candidates */}
              <CandidatesPanel
                heading={data.poi.name}
                subheading={data.poi.address || undefined}
                candidates={data.candidates}
                bestMatch={data.best_match ?? null}
                sourcePoiId={data.poi.id}
                useCandidate={useCandidate}
                busyAction={busyAction}
                isFocal
                onPreviewCandidate={onPreviewCandidate ? handlePreviewCandidate : undefined}
              />

              {/* Duplicate siblings within 15m — same threshold as the dupes
                  lint check. Operator can compare addr/lat/lng AND geocoded
                  candidates per dupe, and jump to any sibling's editor or
                  the map. */}
              {data.duplicates && data.duplicates.length > 0 && (
                <div className="border-t-4 border-rose-200">
                  <div className="px-5 py-2 bg-rose-50 text-xs font-medium text-rose-800 flex items-center justify-between gap-3">
                    <span>
                      {data.duplicates.length} duplicate POI{data.duplicates.length === 1 ? '' : 's'} within 15 m
                    </span>
                    <div className="flex items-center gap-2">
                      <span className="text-rose-600 font-normal">
                        pick a canonical record; soft-delete the rest
                      </span>
                      {onShowClusterOnMap && (
                        <button
                          type="button"
                          onClick={() => onShowClusterOnMap(
                            data.poi.id,
                            (data.duplicates ?? []).map(d => d.id),
                            `Dupe cluster · ${data.poi.name}`,
                          )}
                          className="px-2 py-1 text-[11px] rounded bg-rose-700 hover:bg-rose-800 text-white whitespace-nowrap"
                          title="Filter the POI map to ONLY this cluster (focal + dupes)"
                        >
                          Show cluster on map
                        </button>
                      )}
                    </div>
                  </div>
                  {data.duplicates.map((d) => (
                    <div key={d.id} className="border-t border-rose-100">
                      <div className="px-5 py-2 bg-rose-50/40">
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0 flex-1">
                            <div className="text-sm font-medium text-slate-800 truncate">
                              {d.name}
                              {d.deleted_at && (
                                <span className="ml-2 inline-block text-[10px] uppercase tracking-wide px-1.5 py-0.5 rounded bg-slate-300 text-slate-700">
                                  soft-deleted
                                </span>
                              )}
                              {d.location_status && (
                                <span className="ml-2">
                                  <StatusBadge status={d.location_status} />
                                </span>
                              )}
                            </div>
                            <div className="mt-1 text-xs grid grid-cols-[80px_1fr] gap-y-0.5">
                              <span className="text-slate-500">Address</span>
                              <span className="text-slate-800 break-words">
                                {d.address || <em className="text-slate-400">none</em>}
                              </span>
                              <span className="text-slate-500">Coords</span>
                              <span className="text-slate-800 tabular-nums">
                                {d.lat.toFixed(5)}, {d.lng.toFixed(5)}
                                <span className="text-slate-500 ml-2">
                                  · <DistanceLabel meters={d.distance_m} /> from focal
                                </span>
                              </span>
                              <span className="text-slate-500">ID</span>
                              <span className="text-slate-700"><code className="text-[10px]">{d.id}</code></span>
                            </div>
                          </div>
                          <div className="flex flex-col gap-1 flex-none">
                            {onOpenSibling && (
                              <button
                                type="button"
                                onClick={() => onOpenSibling(d.id)}
                                className="px-2 py-1 text-[11px] rounded bg-slate-700 hover:bg-slate-800 text-white whitespace-nowrap"
                              >
                                Open Editor
                              </button>
                            )}
                            {onShowSiblingOnMap && (
                              <button
                                type="button"
                                onClick={() => onShowSiblingOnMap(d.id)}
                                className="px-2 py-1 text-[11px] rounded bg-slate-200 hover:bg-slate-300 text-slate-700 whitespace-nowrap"
                              >
                                Show on Map
                              </button>
                            )}
                            {!d.deleted_at && (
                              <button
                                type="button"
                                disabled={busyAction != null}
                                onClick={() => void softDeleteDupe(d)}
                                className="px-2 py-1 text-[11px] rounded bg-rose-600 hover:bg-rose-700 disabled:opacity-50 text-white whitespace-nowrap"
                                title="Soft-delete this duplicate (sets deleted_at; reversible)"
                              >
                                {busyAction === `soft-del:${d.id}` ? 'Deleting…' : 'Soft-delete'}
                              </button>
                            )}
                          </div>
                        </div>
                      </div>
                      {/* Per-dupe candidates */}
                      {d.geocode_warning && (
                        <div className="px-5 py-1 text-[11px] text-amber-700 bg-amber-50/60 border-t border-amber-100">
                          {d.geocode_warning}
                        </div>
                      )}
                      <DupeCandidatesPanel
                        dupe={d}
                        candidates={d.candidates ?? []}
                        bestMatch={data.best_match ?? null}
                        useFromDupe={useFromDupe}
                        busyAction={busyAction}
                        onPreviewCandidate={onPreviewCandidate ? handlePreviewCandidate : undefined}
                      />
                    </div>
                  ))}
                </div>
              )}
            </>
          )}
        </div>

        {/* Footer actions */}
        {data && (
          <footer className="px-5 py-3 border-t border-slate-200 bg-slate-50 flex items-center justify-between">
            <button
              type="button"
              onClick={() => void markVerified()}
              disabled={busyAction != null}
              className="px-3 py-1.5 text-xs rounded bg-emerald-600 hover:bg-emerald-700 disabled:opacity-50 text-white"
              title="Mark current coordinates as verified without changing them"
            >
              {busyAction === 'verify' ? 'Verifying…' : 'Validate Current Location'}
            </button>
            <button
              type="button"
              onClick={onClose}
              className="px-3 py-1.5 text-xs rounded bg-slate-200 hover:bg-slate-300 text-slate-700"
            >
              Close
            </button>
          </footer>
        )}
      </div>
    </div>
  )
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function isBestMatchCandidate(
  c: GeocodeCandidate, sourcePoiId: string, best: BestMatch | null,
): boolean {
  if (!best) return false
  if (best.source_poi_id !== sourcePoiId) return false
  if (best.rating !== c.rating) return false
  // Lat/lng come from postgis ST_Y/ST_X — compare to 7 decimal places
  // (~1 cm) which is well below tiger geocoder precision.
  return Math.abs(best.lat - c.lat) < 1e-6 && Math.abs(best.lng - c.lng) < 1e-6
}

// ─── Subcomponents ──────────────────────────────────────────────────────────

interface CandidatesPanelProps {
  heading: string
  subheading?: string
  candidates: GeocodeCandidate[]
  bestMatch: BestMatch | null
  sourcePoiId: string
  useCandidate: (c: GeocodeCandidate) => Promise<void> | void
  busyAction: string | null
  isFocal?: boolean
  onPreviewCandidate?: (sourcePoiId: string, c: GeocodeCandidate) => void
}

function CandidatesPanel({
  heading, subheading, candidates, bestMatch, sourcePoiId,
  useCandidate, busyAction, isFocal, onPreviewCandidate,
}: CandidatesPanelProps) {
  return (
    <div className={isFocal ? 'border-b border-slate-200' : ''}>
      <div className="px-5 py-2 bg-slate-100 text-xs font-medium text-slate-700 border-b border-slate-200 flex items-baseline justify-between gap-3">
        <span className="truncate">
          {isFocal ? 'Focal POI · ' : ''}{heading}
        </span>
        {subheading && (
          <span className="text-slate-500 font-normal text-[11px] truncate">{subheading}</span>
        )}
      </div>
      {candidates.length === 0 ? (
        <div className="px-5 py-3 text-slate-600 text-xs">
          Tiger returned no candidates for this address.
        </div>
      ) : (
        <ul className="divide-y divide-slate-200">
          {candidates.map((c, i) => {
            const key = `use:${sourcePoiId}:${c.rating}:${c.lat}`
            const busy = busyAction === key
            const isBest = isBestMatchCandidate(c, sourcePoiId, bestMatch)
            return (
              <li
                key={`${i}-${c.lat}-${c.lng}`}
                className={`px-5 py-3 hover:bg-slate-50 ${isBest ? 'bg-emerald-50/40' : ''}`}
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="text-sm font-medium text-slate-800">
                      <RatingBadge rating={c.rating} />
                      {isBest && (
                        <span className="ml-1 inline-block text-[10px] uppercase tracking-wide px-1.5 py-0.5 rounded bg-emerald-600 text-white">
                          best
                        </span>
                      )}
                      <span className="ml-2">{c.normalized_address}</span>
                    </div>
                    <div className="text-xs text-slate-500 mt-1 tabular-nums">
                      {c.lat.toFixed(5)}, {c.lng.toFixed(5)} ·{' '}
                      <DistanceLabel meters={c.distance_m} />
                    </div>
                  </div>
                  <div className="flex flex-col gap-1 flex-none">
                    {onPreviewCandidate && (
                      <button
                        type="button"
                        onClick={() => onPreviewCandidate(sourcePoiId, c)}
                        className="px-2 py-1 text-[11px] rounded bg-fuchsia-600 hover:bg-fuchsia-700 text-white whitespace-nowrap"
                        title="Show this candidate on the map alongside the POI"
                      >
                        Show on Map
                      </button>
                    )}
                    {isFocal && (
                      <button
                        type="button"
                        disabled={busy || busyAction != null}
                        onClick={() => void useCandidate(c)}
                        className="px-2 py-1 text-[11px] rounded bg-amber-600 hover:bg-amber-700 disabled:opacity-50 text-white whitespace-nowrap"
                      >
                        {busy ? 'Updating…' : 'Use This Location'}
                      </button>
                    )}
                  </div>
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

interface DupeCandidatesPanelProps {
  dupe: DuplicatePoi
  candidates: GeocodeCandidate[]
  bestMatch: BestMatch | null
  useFromDupe: (dupe: DuplicatePoi, c: GeocodeCandidate) => Promise<void> | void
  busyAction: string | null
  onPreviewCandidate?: (sourcePoiId: string, c: GeocodeCandidate) => void
}

function DupeCandidatesPanel({
  dupe, candidates, bestMatch, useFromDupe, busyAction, onPreviewCandidate,
}: DupeCandidatesPanelProps) {
  if (candidates.length === 0) return null
  return (
    <ul className="divide-y divide-slate-200 bg-white">
      {candidates.map((c, i) => {
        const key = `use-dupe:${dupe.id}:${c.rating}:${c.lat}`
        const busy = busyAction === key
        const isBest = isBestMatchCandidate(c, dupe.id, bestMatch)
        return (
          <li
            key={`${i}-${c.lat}-${c.lng}`}
            className={`px-5 py-2 ${isBest ? 'bg-emerald-50/40' : ''}`}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="text-xs text-slate-700">
                  <RatingBadge rating={c.rating} />
                  {isBest && (
                    <span className="ml-1 inline-block text-[10px] uppercase tracking-wide px-1.5 py-0.5 rounded bg-emerald-600 text-white">
                      best
                    </span>
                  )}
                  <span className="ml-2">{c.normalized_address}</span>
                </div>
                <div className="text-[11px] text-slate-500 mt-0.5 tabular-nums">
                  {c.lat.toFixed(5)}, {c.lng.toFixed(5)} ·{' '}
                  <DistanceLabel meters={c.distance_m} />
                </div>
              </div>
              <div className="flex flex-col gap-1 flex-none">
                {onPreviewCandidate && (
                  <button
                    type="button"
                    onClick={() => onPreviewCandidate(dupe.id, c)}
                    className="px-2 py-1 text-[11px] rounded bg-fuchsia-600 hover:bg-fuchsia-700 text-white whitespace-nowrap"
                    title="Show this candidate on the map alongside the dupe"
                  >
                    Show on Map
                  </button>
                )}
                {!dupe.deleted_at && (
                  <button
                    type="button"
                    disabled={busy || busyAction != null}
                    onClick={() => void useFromDupe(dupe, c)}
                    className="px-2 py-1 text-[11px] rounded bg-amber-600 hover:bg-amber-700 disabled:opacity-50 text-white whitespace-nowrap"
                    title="Apply this coord to the focal POI, then soft-delete this duplicate"
                  >
                    {busy ? 'Updating…' : 'Use & soft-delete dupe'}
                  </button>
                )}
              </div>
            </div>
          </li>
        )
      })}
    </ul>
  )
}


function StatusBadge({ status }: { status: string | null }) {
  const norm = status || 'unverified'
  const cls =
    norm === 'verified' || norm === 'accepted' ? 'bg-emerald-100 text-emerald-700' :
    norm === 'needs_review' ? 'bg-amber-100 text-amber-700' :
    norm === 'no_match' || norm === 'no_address' ? 'bg-rose-100 text-rose-700' :
    'bg-slate-200 text-slate-600'
  return (
    <span className={`inline-block text-[10px] uppercase tracking-wide px-1.5 py-0.5 rounded ${cls}`}>
      {norm}
    </span>
  )
}

function RatingBadge({ rating }: { rating: number }) {
  // Tiger geocoder rating: 0 = exact match, 100 = city-centroid fallback.
  const cls =
    rating <= 5 ? 'bg-emerald-100 text-emerald-700' :
    rating <= 30 ? 'bg-lime-100 text-lime-700' :
    rating <= 70 ? 'bg-amber-100 text-amber-700' :
    'bg-rose-100 text-rose-700'
  return (
    <span
      className={`inline-block text-[10px] uppercase tracking-wide px-1.5 py-0.5 rounded ${cls}`}
      title="Tiger geocoder rating: 0 = exact match, 100 = city centroid fallback"
    >
      r{rating}
    </span>
  )
}

function DistanceLabel({ meters }: { meters: number }) {
  if (meters < 1000) return <>{meters} m from stored</>
  return <>{(meters / 1000).toFixed(2)} km from stored</>
}
