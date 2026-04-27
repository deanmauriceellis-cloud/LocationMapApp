// GeocodesTab — S190
//
// Standalone view for the address ↔ geocode deep scan. The same scan was
// previously hidden behind the Lint tab's "address_geocode_mismatch"
// on-demand check; promoting it to its own top-level tab gives the
// operator a dedicated workspace for hunting POIs whose stored lat/lng
// disagree with their address.
//
// Backend endpoints (already implemented by lib/admin-lint.js):
//   POST /admin/salem/lint/address-geocode/run    — kicks off a pass
//   GET  /admin/salem/lint/address-geocode/status — { state, progress,
//       total, items: GeocodeMismatchItem[] | null, error, started_at,
//       finished_at }
//
// Items are sorted by distance descending (worst first), capped at 300.
// Distance threshold for inclusion is 100 m (set server-side).
//
// The tab does NOT block on the running pass — operator can navigate
// elsewhere and the background pass continues. The status header shows
// progress when revisiting.

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

interface GeocodeMismatchItem {
  entity_type: 'poi'
  entity_id: string
  entity_label: string
  lat: number
  lng: number
  message: string
  fix_hint: string
  distance_m: number | null
  rating: number | null
  suggested_lat?: number
  suggested_lng?: number
}

interface AddrGeocodeStatus {
  state: 'idle' | 'running' | 'done' | 'error'
  progress: number
  total: number
  started_at: string | null
  finished_at: string | null
  items: GeocodeMismatchItem[] | null
  error: string | null
}

const POLL_MS = 2_000

export interface GeocodesTabProps {
  onOpenPoi: (poiId: string) => void | Promise<void>
  onShowPoiOnMap: (poiId: string, allPoiIds: string[], label: string) => void | Promise<void>
  onOpenGeocodes: (poiId: string) => void
}

export function GeocodesTab({ onOpenPoi, onShowPoiOnMap, onOpenGeocodes }: GeocodesTabProps) {
  const [status, setStatus] = useState<AddrGeocodeStatus | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [runBusy, setRunBusy] = useState(false)
  const [search, setSearch] = useState('')
  // Ref to avoid stale closures when the polling interval reads `status`.
  const statusRef = useRef<AddrGeocodeStatus | null>(null)
  statusRef.current = status

  const fetchStatus = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/salem/lint/address-geocode/status', {
        credentials: 'same-origin',
      })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const body = (await res.json()) as AddrGeocodeStatus
      setStatus(body)
      setLoadError(null)
    } catch (e) {
      setLoadError(e instanceof Error ? e.message : String(e))
    }
  }, [])

  // Initial + polling fetch. Polls only while a pass is running so we don't
  // hammer the backend while idle.
  useEffect(() => {
    void fetchStatus()
  }, [fetchStatus])

  useEffect(() => {
    if (status?.state !== 'running') return
    const id = window.setInterval(() => {
      void fetchStatus()
    }, POLL_MS)
    return () => window.clearInterval(id)
  }, [status?.state, fetchStatus])

  const runPass = useCallback(async () => {
    setRunBusy(true)
    try {
      const res = await fetch('/api/admin/salem/lint/address-geocode/run', {
        method: 'POST',
        credentials: 'same-origin',
      })
      if (!res.ok) {
        const txt = await res.text().catch(() => '')
        throw new Error(`HTTP ${res.status} ${txt}`)
      }
      // Optimistically pull the new running state so the UI flips to a
      // progress bar without a polling delay.
      await fetchStatus()
    } catch (e) {
      window.alert(`Failed to start pass: ${e instanceof Error ? e.message : String(e)}`)
    } finally {
      setRunBusy(false)
    }
  }, [fetchStatus])

  const filteredItems = useMemo(() => {
    if (!status?.items) return []
    const term = search.trim().toLowerCase()
    if (!term) return status.items
    return status.items.filter((it) => {
      return (
        it.entity_label.toLowerCase().includes(term) ||
        it.entity_id.toLowerCase().includes(term) ||
        it.message.toLowerCase().includes(term)
      )
    })
  }, [status?.items, search])

  const allPoiIds = useMemo(
    () => (status?.items ?? []).map((it) => it.entity_id),
    [status?.items],
  )

  return (
    <div className="flex flex-col h-full min-h-0 bg-slate-50">
      {/* Header / control strip */}
      <div className="px-4 py-3 border-b border-slate-300 bg-white flex items-center gap-3 flex-wrap">
        <h2 className="text-base font-semibold text-slate-800">
          Address ↔ Geocode Verification
        </h2>
        <span className="text-xs text-slate-500">
          Compares stored POI coords against the Tiger geocode of the POI's address.
          Flags rows where the two are &gt; 100 m apart.
        </span>

        <div className="flex-1" />

        <button
          type="button"
          onClick={runPass}
          disabled={runBusy || status?.state === 'running'}
          className="px-3 py-1.5 text-sm rounded bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed"
          title="Run the deep scan against all POIs with addresses"
        >
          {status?.state === 'running'
            ? `Running… ${status.progress}/${status.total}`
            : runBusy
              ? 'Starting…'
              : status?.state === 'done'
                ? 'Re-run scan'
                : 'Run scan'}
        </button>
      </div>

      {/* Status / progress bar */}
      {status?.state === 'running' && (
        <div className="px-4 py-2 bg-amber-50 border-b border-amber-200 text-sm text-amber-900 flex items-center gap-3">
          <div className="flex-1 max-w-md">
            <div className="h-2 bg-amber-200 rounded overflow-hidden">
              <div
                className="h-full bg-amber-500 transition-[width] duration-500"
                style={{
                  width: status.total
                    ? `${Math.min(100, (status.progress / status.total) * 100)}%`
                    : '5%',
                }}
              />
            </div>
          </div>
          <span className="tabular-nums text-xs text-amber-800">
            {status.progress.toLocaleString()} / {status.total.toLocaleString()} POIs scanned
          </span>
        </div>
      )}

      {status?.state === 'error' && (
        <div className="px-4 py-2 bg-rose-50 border-b border-rose-200 text-sm text-rose-800">
          Scan failed: {status.error ?? 'unknown error'}
        </div>
      )}

      {loadError && (
        <div className="px-4 py-2 bg-rose-50 border-b border-rose-200 text-sm text-rose-800">
          Status fetch failed: {loadError}
        </div>
      )}

      {/* Empty / never-run state */}
      {status?.state === 'idle' && (
        <div className="flex-1 flex items-center justify-center text-center px-8">
          <div className="max-w-lg text-slate-600">
            <div className="text-2xl font-semibold text-slate-700 mb-2">
              No scan results yet
            </div>
            <p className="text-sm">
              Click <strong>Run scan</strong> to compare every POI's stored coordinates
              against the Tiger geocode of its address. Background pass — typically
              ~5–10 minutes. Results show the worst distance mismatches (capped at 300).
            </p>
          </div>
        </div>
      )}

      {/* Results list */}
      {status?.items && status.items.length > 0 && (
        <>
          <div className="px-4 py-2 border-b border-slate-300 bg-white flex items-center gap-3">
            <span className="text-sm text-slate-700">
              <strong>{status.items.length}</strong> mismatch
              {status.items.length === 1 ? '' : 'es'} found
              {status.finished_at && (
                <span className="text-xs text-slate-500 ml-2">
                  · finished {new Date(status.finished_at).toLocaleString()}
                </span>
              )}
            </span>
            <input
              type="search"
              placeholder="Filter by name, id, or message…"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="ml-auto w-72 px-2 py-1 text-sm border border-slate-300 rounded"
            />
            {filteredItems.length > 0 && (
              <button
                type="button"
                onClick={() =>
                  onShowPoiOnMap(
                    filteredItems[0].entity_id,
                    filteredItems.map((it) => it.entity_id),
                    'Geocode mismatches',
                  )
                }
                className="px-2 py-1 text-xs rounded bg-slate-700 text-white hover:bg-slate-800"
                title="Filter the POI map to all mismatched POIs"
              >
                Show all on map
              </button>
            )}
          </div>

          <div className="flex-1 min-h-0 overflow-y-auto">
            <ul className="divide-y divide-slate-200">
              {filteredItems.map((it) => (
                <li key={it.entity_id} className="px-4 py-3 hover:bg-slate-100">
                  <div className="flex items-start gap-3">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-medium text-slate-900 truncate">
                          {it.entity_label}
                        </span>
                        {it.distance_m !== null ? (
                          <span
                            className={`text-xs font-mono px-1.5 py-0.5 rounded ${
                              it.distance_m >= 1000
                                ? 'bg-rose-100 text-rose-800'
                                : it.distance_m >= 300
                                  ? 'bg-amber-100 text-amber-800'
                                  : 'bg-yellow-50 text-yellow-800'
                            }`}
                          >
                            {it.distance_m.toLocaleString()} m off
                          </span>
                        ) : (
                          <span className="text-xs font-mono px-1.5 py-0.5 rounded bg-slate-100 text-slate-700">
                            no geocode
                          </span>
                        )}
                        {it.rating !== null && (
                          <span
                            className="text-xs font-mono text-slate-500"
                            title="Tiger geocode rating (lower is better)"
                          >
                            rating {it.rating}
                          </span>
                        )}
                        <span className="text-xs font-mono text-slate-400 truncate">
                          {it.entity_id}
                        </span>
                      </div>
                      <div className="text-sm text-slate-700 mt-1">{it.message}</div>
                      <div className="text-xs text-slate-500 mt-0.5 italic">
                        {it.fix_hint}
                      </div>
                    </div>

                    <div className="flex flex-col gap-1 flex-shrink-0">
                      <button
                        type="button"
                        onClick={() => void onOpenPoi(it.entity_id)}
                        className="px-2 py-0.5 text-xs rounded bg-indigo-600 text-white hover:bg-indigo-700"
                      >
                        Open Editor
                      </button>
                      <button
                        type="button"
                        onClick={() =>
                          void onShowPoiOnMap(
                            it.entity_id,
                            allPoiIds,
                            'Geocode mismatches',
                          )
                        }
                        className="px-2 py-0.5 text-xs rounded bg-white border border-slate-300 hover:bg-slate-50"
                      >
                        Show on Map
                      </button>
                      <button
                        type="button"
                        onClick={() => onOpenGeocodes(it.entity_id)}
                        className="px-2 py-0.5 text-xs rounded bg-fuchsia-600 text-white hover:bg-fuchsia-700"
                        title="Open the Geocodes modal to compare candidates"
                      >
                        Geocodes
                      </button>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
            {filteredItems.length === 0 && (
              <div className="px-4 py-8 text-center text-sm text-slate-500">
                No results match the filter.
              </div>
            )}
          </div>
        </>
      )}

      {/* Done with zero items — nothing to flag */}
      {status?.state === 'done' && (status.items?.length ?? 0) === 0 && (
        <div className="flex-1 flex items-center justify-center text-center px-8">
          <div className="max-w-lg text-slate-600">
            <div className="text-2xl font-semibold text-emerald-700 mb-2">
              No mismatches found
            </div>
            <p className="text-sm">
              All POIs with addresses geocode within 100 m of their stored
              coordinates. Re-run the scan after editing addresses or moving markers.
            </p>
          </div>
        </div>
      )}
    </div>
  )
}
