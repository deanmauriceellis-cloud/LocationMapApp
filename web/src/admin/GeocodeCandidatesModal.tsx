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

interface GeocodeResponse {
  poi: GeocodePoi
  candidates: GeocodeCandidate[]
  warning?: string
  error?: string
}

export interface GeocodeCandidatesModalProps {
  poiId: string | null
  open: boolean
  onClose: () => void
  /** Called after a successful Override or Validate so the parent can refresh. */
  onChanged: () => void
}

export function GeocodeCandidatesModal({
  poiId, open, onClose, onChanged,
}: GeocodeCandidatesModalProps) {
  const [data, setData] = useState<GeocodeResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [busyAction, setBusyAction] = useState<string | null>(null)

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
    setBusyAction(`use:${c.rating}:${c.lat}`)
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
        className="bg-white rounded-lg shadow-xl w-full max-w-2xl max-h-[85vh] flex flex-col"
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

              {/* Warning */}
              {data.warning && (
                <div className="px-5 py-2 bg-amber-50 border-b border-amber-200 text-xs text-amber-800">
                  {data.warning}
                </div>
              )}

              {/* Candidates */}
              {data.candidates.length === 0 && !data.warning && (
                <div className="px-5 py-4 text-slate-600 text-sm">
                  Tiger geocoder returned no candidates for this address.
                  <div className="text-xs text-slate-500 mt-1">
                    This usually means MA street-level address data isn't loaded yet, or the
                    address format isn't matching. You can still validate the current
                    coordinates as correct using the button below.
                  </div>
                </div>
              )}
              {data.candidates.length > 0 && (
                <ul className="divide-y divide-slate-200">
                  {data.candidates.map((c, i) => {
                    const key = `use:${c.rating}:${c.lat}`
                    const busy = busyAction === key
                    return (
                      <li key={`${i}-${c.lat}-${c.lng}`} className="px-5 py-3 hover:bg-slate-50">
                        <div className="flex items-start justify-between gap-3">
                          <div className="min-w-0">
                            <div className="text-sm font-medium text-slate-800">
                              <RatingBadge rating={c.rating} />
                              <span className="ml-2">{c.normalized_address}</span>
                            </div>
                            <div className="text-xs text-slate-500 mt-1 tabular-nums">
                              {c.lat.toFixed(5)}, {c.lng.toFixed(5)} ·{' '}
                              <DistanceLabel meters={c.distance_m} />
                            </div>
                          </div>
                          <button
                            type="button"
                            disabled={busy || busyAction != null}
                            onClick={() => void useCandidate(c)}
                            className="px-2 py-1 text-[11px] rounded bg-amber-600 hover:bg-amber-700 disabled:opacity-50 text-white whitespace-nowrap flex-none"
                          >
                            {busy ? 'Updating…' : 'Use This Location'}
                          </button>
                        </div>
                      </li>
                    )
                  })}
                </ul>
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

// ─── Subcomponents ──────────────────────────────────────────────────────────

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
