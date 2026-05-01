// TourTree — left panel for the Tours admin view (S174).
//
// Owns the tour list and the selected-tour detail UI:
//   • create / delete / pick a tour
//   • edit tour metadata (name, theme, description, etc.)
//   • list waypoints with per-row reorder / delete
//   • toggle map-add modes (free waypoint click, pick existing POI)
//   • on any backend mutation, re-fetch the active tour's stops
//
// The map (AdminMap) renders waypoints + handles drag-to-move; this panel
// handles everything else.

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { PoiRow } from './PoiTree'
import { isMassgisHistorical } from './PoiTree'
import type {
  ComputeRouteResponse,
  TourDetailResponse,
  TourLeg,
  TourLegsResponse,
  TourStop,
  TourSummary,
  ToursListResponse,
} from './tourTypes'

export type AddStopMode = 'none' | 'free' | 'poi'

interface TourTreeProps {
  selectedTourId: string | null
  onTourSelect: (tour: TourSummary, stops: TourStop[]) => void
  /** Bumped by parent when stops change externally (e.g. after a drag-save). */
  refreshKey: number
  /** Add-stop interaction mode. Forwarded to the map. */
  addStopMode: AddStopMode
  onAddStopModeChange: (mode: AddStopMode) => void
  /** Notifies the parent (AdminLayout) so AdminMap can render the legs. */
  onLegsChange?: (legs: TourLeg[] | null) => void
  /** Currently-selected leg (highlighted red on the map). null = none. */
  selectedLegOrder?: number | null
  /** Click handler for a leg row (toggles selection). */
  onLegSelect?: (legOrder: number) => void
  /** Click handler for a waypoint row — recenters the map on that stop. */
  onFocusStop?: (stopId: number) => void
  /** S214 — POI list (for tour-mode preview counts). */
  pois: PoiRow[] | null
  /** S214 — Tour-mode preview filter. Mirrors device S186 narration gate:
   *  is_tour_poi is the always-narrate baseline; histLandmark + civic are
   *  opt-in classes the user can toggle in tour mode. */
  tourModeFilter: { tourPois: boolean; histLandmark: boolean; civic: boolean }
  onTourModeFilterChange: (next: { tourPois: boolean; histLandmark: boolean; civic: boolean }) => void
}

const ENDPOINT = '/api/admin/salem/tours'

async function fetchJson<T>(input: RequestInfo, init?: RequestInit): Promise<T> {
  const res = await fetch(input, { credentials: 'same-origin', ...(init ?? {}) })
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText}`
    try {
      const body = await res.json()
      if (body?.error) msg = `${res.status} ${body.error}`
    } catch { /* not json */ }
    throw new Error(msg)
  }
  return (await res.json()) as T
}

export function TourTree({
  selectedTourId,
  onTourSelect,
  refreshKey,
  addStopMode,
  onAddStopModeChange,
  onLegsChange,
  selectedLegOrder = null,
  onLegSelect,
  onFocusStop,
  pois,
  tourModeFilter,
  onTourModeFilterChange,
}: TourTreeProps) {
  const [tours, setTours] = useState<TourSummary[] | null>(null)
  const [tour, setTour] = useState<TourSummary | null>(null)
  const [stops, setStops] = useState<TourStop[] | null>(null)
  const [legs, setLegs] = useState<TourLeg[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [computing, setComputing] = useState(false)
  const [showCreate, setShowCreate] = useState(false)
  // S190 — keep the most recent compute-route response so the panel can
  // show a "suspicious legs" call-out under the Compute button.
  const [lastCompute, setLastCompute] = useState<ComputeRouteResponse | null>(null)

  // Push legs up to AdminLayout whenever they change so AdminMap can render.
  useEffect(() => {
    onLegsChange?.(legs)
  }, [legs, onLegsChange])

  const loadTours = useCallback(async () => {
    setError(null)
    try {
      const body = await fetchJson<ToursListResponse>(ENDPOINT)
      setTours(body.tours)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }, [])

  const loadLegs = useCallback(async (tourId: string) => {
    try {
      const body = await fetchJson<TourLegsResponse>(
        `${ENDPOINT}/${encodeURIComponent(tourId)}/legs`,
      )
      setLegs(body.legs)
    } catch (e) {
      // Legs endpoint failure is non-fatal — operator may not have computed yet.
      console.warn('[TourTree] loadLegs:', e instanceof Error ? e.message : e)
      setLegs(null)
    }
  }, [])

  const loadTour = useCallback(
    async (tourId: string) => {
      setError(null)
      try {
        const body = await fetchJson<TourDetailResponse>(
          `${ENDPOINT}/${encodeURIComponent(tourId)}`,
        )
        setTour(body.tour)
        setStops(body.stops)
        onTourSelect(body.tour, body.stops)
        // Fetch legs in parallel — won't block the stops view.
        void loadLegs(tourId)
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e))
      }
    },
    [onTourSelect, loadLegs],
  )

  const handleComputeRoute = useCallback(
    async (force = false) => {
      if (!tour) return
      setComputing(true)
      setError(null)
      try {
        const body = await fetchJson<ComputeRouteResponse>(
          `${ENDPOINT}/${encodeURIComponent(tour.id)}/compute-route`,
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ force }),
          },
        )
        setLegs(body.legs)
        setLastCompute(body)
        // S190 — verbose console output so the operator can compare per-leg
        // detour ratios without tailing the cache-proxy log. console.table
        // gives a clean tabular view in DevTools.
        console.groupCollapsed(
          `[TourTree] compute-route done · tour=${body.tour_id} · ` +
            `legs=${body.leg_count} · skipped=${body.skipped.length} · ` +
            `suspicious=${body.suspicious_count ?? 0} · ` +
            `total=${body.total_distance_m}m / ${body.total_duration_s}s`,
        )
        const tableRows = body.legs.map((leg) => ({
          leg: leg.leg_order,
          from: leg.from_label ?? `stop ${leg.from_stop_id}`,
          to: leg.to_label ?? `stop ${leg.to_stop_id}`,
          straight_m: leg.diagnostics?.straight_m ?? null,
          routed_m: leg.diagnostics?.routed_m ?? leg.distance_m,
          ratio: leg.diagnostics?.detour_ratio ?? null,
          pts: leg.diagnostics?.point_count ?? leg.polyline_json.length,
          dur_s: leg.duration_s,
          ms: leg.diagnostics?.elapsed_ms ?? null,
          flag: leg.diagnostics?.suspicious ? '⚠' : leg.preserved ? 'preserved' : '',
        }))
        // eslint-disable-next-line no-console
        console.table(tableRows)
        if (body.skipped.length) {
          // eslint-disable-next-line no-console
          console.warn('[TourTree] skipped legs:', body.skipped)
        }
        console.groupEnd()
        // Roll-up may have updated tour.distance_km / estimated_minutes.
        await loadTours()
        await loadTour(tour.id)
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e))
      } finally {
        setComputing(false)
      }
    },
    [tour, loadTour, loadTours],
  )

  const handleRecomputeLeg = useCallback(
    async (legOrder: number, force = false) => {
      if (!tour) return
      setBusy(true)
      setError(null)
      try {
        const body = await fetchJson<TourLeg>(
          `${ENDPOINT}/${encodeURIComponent(tour.id)}/legs/${legOrder}/recompute`,
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ force }),
          },
        )
        // S190 — log per-leg diagnostics for ad-hoc recomputes too.
        const d = body.diagnostics
        const flag = d?.suspicious ? ' ⚠ SUSPICIOUS' : ''
        // eslint-disable-next-line no-console
        console.log(
          `[TourTree] recompute leg=${legOrder}  ${body.from_label ?? ''} → ${body.to_label ?? ''}  ` +
            `straight=${d?.straight_m ?? '?'}m routed=${d?.routed_m ?? body.distance_m}m ` +
            `ratio=${d?.detour_ratio ?? '?'} pts=${d?.point_count ?? body.polyline_json.length} ` +
            `dur=${body.duration_s}s${flag}`,
        )
        await loadLegs(tour.id)
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e))
      } finally {
        setBusy(false)
      }
    },
    [tour, loadLegs],
  )

  useEffect(() => {
    void loadTours()
  }, [loadTours])

  // External refresh (after drag-save in the map).
  useEffect(() => {
    if (selectedTourId && refreshKey > 0) void loadTour(selectedTourId)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshKey])

  const handleCreate = useCallback(
    async (input: CreateTourInput) => {
      setBusy(true)
      setError(null)
      try {
        const created = await fetchJson<TourSummary>(ENDPOINT, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(input),
        })
        setShowCreate(false)
        await loadTours()
        await loadTour(created.id)
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e))
      } finally {
        setBusy(false)
      }
    },
    [loadTour, loadTours],
  )

  const handleDeleteTour = useCallback(
    async (tourId: string) => {
      if (!window.confirm(`Delete tour "${tourId}" and all its waypoints? This cannot be undone.`)) {
        return
      }
      setBusy(true)
      setError(null)
      try {
        await fetchJson(`${ENDPOINT}/${encodeURIComponent(tourId)}`, { method: 'DELETE' })
        if (tourId === selectedTourId) {
          setTour(null)
          setStops(null)
        }
        await loadTours()
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e))
      } finally {
        setBusy(false)
      }
    },
    [selectedTourId, loadTours],
  )

  const handleSaveMeta = useCallback(
    async (patch: Partial<TourSummary>) => {
      if (!tour) return
      setBusy(true)
      setError(null)
      try {
        await fetchJson(`${ENDPOINT}/${encodeURIComponent(tour.id)}`, {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(patch),
        })
        await loadTours()
        await loadTour(tour.id)
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e))
      } finally {
        setBusy(false)
      }
    },
    [tour, loadTour, loadTours],
  )

  const handleDeleteStop = useCallback(
    async (stopId: number) => {
      if (!tour) return
      if (!window.confirm('Delete this waypoint?')) return
      setBusy(true)
      setError(null)
      try {
        await fetchJson(
          `${ENDPOINT}/${encodeURIComponent(tour.id)}/stops/${stopId}`,
          { method: 'DELETE' },
        )
        await loadTour(tour.id)
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e))
      } finally {
        setBusy(false)
      }
    },
    [tour, loadTour],
  )

  const handleReorder = useCallback(
    async (newOrderIds: number[]) => {
      if (!tour) return
      setBusy(true)
      setError(null)
      try {
        await fetchJson(
          `${ENDPOINT}/${encodeURIComponent(tour.id)}/stops/reorder`,
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ order: newOrderIds }),
          },
        )
        await loadTour(tour.id)
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e))
      } finally {
        setBusy(false)
      }
    },
    [tour, loadTour],
  )

  const moveStop = useCallback(
    (stopId: number, dir: -1 | 1) => {
      if (!stops) return
      const ids = stops.map((s) => s.stop_id)
      const idx = ids.indexOf(stopId)
      const swap = idx + dir
      if (idx < 0 || swap < 0 || swap >= ids.length) return
      const next = ids.slice()
      ;[next[idx], next[swap]] = [next[swap], next[idx]]
      void handleReorder(next)
    },
    [stops, handleReorder],
  )

  return (
    <div className="h-full flex flex-col text-sm">
      <div className="px-3 py-2 border-b border-slate-200 flex items-center gap-2 bg-slate-50">
        <button
          type="button"
          onClick={() => setShowCreate(true)}
          className="text-xs px-2 py-1 rounded bg-emerald-600 text-white hover:bg-emerald-500 disabled:opacity-50"
          disabled={busy}
        >
          + New tour
        </button>
        <button
          type="button"
          onClick={() => void loadTours()}
          className="text-xs px-2 py-1 rounded bg-slate-200 text-slate-700 hover:bg-slate-300"
          title="Reload tour list"
        >
          ↻
        </button>
      </div>

      {error && (
        <div className="px-3 py-2 text-xs font-mono text-rose-700 bg-rose-50 border-b border-rose-200 break-words">
          {error}
          <button
            type="button"
            onClick={() => setError(null)}
            className="ml-2 underline"
          >
            dismiss
          </button>
        </div>
      )}

      {showCreate && (
        <CreateTourForm
          busy={busy}
          onCancel={() => setShowCreate(false)}
          onSubmit={handleCreate}
        />
      )}

      <ul className="overflow-y-auto border-b border-slate-200 max-h-60 shrink-0">
        {tours === null && !error && (
          <li className="px-3 py-2 text-slate-500 italic">Loading tours…</li>
        )}
        {tours?.map((t) => {
          const active = t.id === selectedTourId
          return (
            <li key={t.id} className="group flex">
              <button
                type="button"
                onClick={() => void loadTour(t.id)}
                className={`flex-1 text-left px-3 py-2 hover:bg-slate-100 border-b border-slate-100 ${
                  active ? 'bg-indigo-50' : ''
                }`}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-medium text-slate-800 truncate">{t.name}</span>
                  <span className="text-xs text-slate-500 tabular-nums shrink-0">
                    {t.stops_actual}
                  </span>
                </div>
                <div className="text-xs text-slate-500 mt-0.5 truncate">
                  {t.theme} · {t.estimated_minutes} min · {t.distance_km.toFixed(1)} km
                </div>
              </button>
              <button
                type="button"
                onClick={() => void handleDeleteTour(t.id)}
                title="Delete tour"
                className="px-2 text-rose-500 opacity-0 group-hover:opacity-100 hover:bg-rose-50"
                disabled={busy}
              >
                ✕
              </button>
            </li>
          )
        })}
      </ul>

      {tour && (
        <div className="flex-1 min-h-0 flex flex-col overflow-y-auto">
          <TourMetadataForm tour={tour} busy={busy} onSave={handleSaveMeta} />

          <div className="px-3 py-2 border-y border-slate-200 bg-slate-50 sticky top-0 z-10">
            <div className="text-xs font-semibold uppercase tracking-wide text-slate-600 mb-1">
              Waypoints ({stops?.length ?? 0})
            </div>
            <div className="flex gap-2 flex-wrap">
              <ToggleButton
                active={addStopMode === 'free'}
                onClick={() =>
                  onAddStopModeChange(addStopMode === 'free' ? 'none' : 'free')
                }
                title="Click anywhere on the map to drop a free waypoint"
              >
                + Free waypoint
              </ToggleButton>
              <ToggleButton
                active={addStopMode === 'poi'}
                onClick={() =>
                  onAddStopModeChange(addStopMode === 'poi' ? 'none' : 'poi')
                }
                title="Click an existing POI marker to insert it as a waypoint"
              >
                + POI as stop
              </ToggleButton>
            </div>
            {addStopMode === 'free' && (
              <p className="text-[11px] text-amber-700 mt-1">
                Click on the map to drop a waypoint at that spot.
              </p>
            )}
            {addStopMode === 'poi' && (
              <p className="text-[11px] text-amber-700 mt-1">
                Click any POI marker to add it to this tour.
              </p>
            )}
          </div>

          <TourModePreview
            pois={pois}
            filter={tourModeFilter}
            onChange={onTourModeFilterChange}
          />

          <RouteSection
            tour={tour}
            stopCount={stops?.length ?? 0}
            legs={legs}
            computing={computing}
            busy={busy}
            onCompute={handleComputeRoute}
            onRecomputeLeg={handleRecomputeLeg}
            selectedLegOrder={selectedLegOrder}
            onLegSelect={onLegSelect}
            lastCompute={lastCompute}
          />

          <ol className="text-xs">
            {stops?.map((s, idx) => {
              const overridden = s.override_lat != null || s.override_lng != null
              const isFree = !s.poi_id
              return (
                <li
                  key={s.stop_id}
                  onClick={() => onFocusStop?.(s.stop_id)}
                  className="px-3 py-1.5 border-b border-slate-100 flex items-baseline gap-2 cursor-pointer hover:bg-slate-50"
                  title="Click to center the map on this waypoint"
                >
                  <span className="font-mono text-slate-400 w-6 shrink-0 tabular-nums">
                    {s.stop_order}
                  </span>
                  <div className="flex-1 min-w-0">
                    <div className="text-slate-800 truncate">
                      {s.effective_name ?? '(unnamed)'}
                      {isFree && (
                        <span className="ml-1 text-[10px] text-indigo-700 align-middle">
                          [free]
                        </span>
                      )}
                    </div>
                    <div className="text-[10px] text-slate-400 font-mono truncate">
                      {s.effective_lat?.toFixed(6) ?? '—'}, {s.effective_lng?.toFixed(6) ?? '—'}
                      {overridden && !isFree && (
                        <span className="ml-1 text-amber-600" title="Per-tour coord override">
                          (override)
                        </span>
                      )}
                    </div>
                  </div>
                  <div className="flex items-center gap-0.5 text-slate-500">
                    <button
                      type="button"
                      onClick={(e) => { e.stopPropagation(); moveStop(s.stop_id, -1) }}
                      disabled={idx === 0 || busy}
                      title="Move up"
                      className="px-1.5 py-0.5 rounded hover:bg-slate-100 disabled:opacity-30"
                    >
                      ↑
                    </button>
                    <button
                      type="button"
                      onClick={(e) => { e.stopPropagation(); moveStop(s.stop_id, 1) }}
                      disabled={idx === (stops?.length ?? 0) - 1 || busy}
                      title="Move down"
                      className="px-1.5 py-0.5 rounded hover:bg-slate-100 disabled:opacity-30"
                    >
                      ↓
                    </button>
                    <button
                      type="button"
                      onClick={(e) => { e.stopPropagation(); void handleDeleteStop(s.stop_id) }}
                      disabled={busy}
                      title="Delete waypoint"
                      className="px-1.5 py-0.5 rounded text-rose-500 hover:bg-rose-50 disabled:opacity-30"
                    >
                      🗑
                    </button>
                  </div>
                </li>
              )
            })}
          </ol>
        </div>
      )}
    </div>
  )
}

// ─── Route section (S183) ──────────────────────────────────────────────────
//
// Compute Route button + per-leg list. Computes walking polylines for every
// consecutive pair of stops via the on-device router (cache-proxy bundle),
// and persists them to salem_tour_legs. The operator can then recompute a
// single leg if a waypoint moved.
interface RouteSectionProps {
  tour: TourSummary
  stopCount: number
  legs: TourLeg[] | null
  computing: boolean
  busy: boolean
  onCompute: (force?: boolean) => void | Promise<void>
  onRecomputeLeg: (legOrder: number, force?: boolean) => void | Promise<void>
  selectedLegOrder: number | null
  onLegSelect?: (legOrder: number) => void
  /**
   * S190 — most recent compute-route response. Used to show suspicious-leg
   * call-outs ("⚠ leg 3: 45 m straight, 412 m routed, ratio 9.2") so the
   * operator can fix bad legs without grepping the proxy log.
   */
  lastCompute: ComputeRouteResponse | null
}

// S214 — Tour-mode preview panel. Mirrors the device's S186 narration gate:
// is_tour_poi=true is the always-narrate baseline; histLandmark and civic
// are opt-in classes the user can toggle in tour mode. Each row shows the
// app-wide count + a checkbox controlling whether that class renders on
// the admin map. The total at the bottom is the union (no double-counting).
interface TourModePreviewProps {
  pois: PoiRow[] | null
  filter: { tourPois: boolean; histLandmark: boolean; civic: boolean }
  onChange: (next: { tourPois: boolean; histLandmark: boolean; civic: boolean }) => void
}

function TourModePreview({ pois, filter, onChange }: TourModePreviewProps) {
  const counts = useMemo(() => {
    const live = (pois ?? []).filter((p) => !p.deleted_at)
    const tourPoiSet = new Set<string>()
    const histLandmarkSet = new Set<string>()
    const civicSet = new Set<string>()
    for (const p of live) {
      if (p.is_tour_poi) tourPoiSet.add(p.id)
      if (isMassgisHistorical(p)) histLandmarkSet.add(p.id)
      if (p.is_civic_poi) civicSet.add(p.id)
    }
    const audible = new Set<string>()
    if (filter.tourPois) for (const id of tourPoiSet) audible.add(id)
    if (filter.histLandmark) for (const id of histLandmarkSet) audible.add(id)
    if (filter.civic) for (const id of civicSet) audible.add(id)
    return {
      tourPoi: tourPoiSet.size,
      histLandmark: histLandmarkSet.size,
      civic: civicSet.size,
      audible: audible.size,
    }
  }, [pois, filter])

  return (
    <div className="px-3 py-2 border-b border-slate-200 bg-fuchsia-50/40">
      <div className="text-xs font-semibold uppercase tracking-wide text-slate-600 mb-1.5">
        Tour-mode preview
      </div>
      <p className="text-[11px] text-slate-600 mb-2 leading-tight">
        POIs that will narrate during this tour. Mirrors the device's tour-mode
        gate: <code className="text-[10px]">is_tour_poi</code> always narrates;
        Hist Landmark + Civic are opt-in.
      </p>
      <ul className="space-y-1">
        <li>
          <label className="flex items-center gap-2 text-xs text-slate-800 cursor-pointer">
            <input
              type="checkbox"
              checked={filter.tourPois}
              onChange={(e) => onChange({ ...filter, tourPois: e.target.checked })}
              className="w-3.5 h-3.5 accent-fuchsia-600"
            />
            <span className="flex-1">Tour POIs <span className="text-[10px] text-slate-500">(is_tour_poi)</span></span>
            <span className="tabular-nums text-slate-600">{counts.tourPoi}</span>
          </label>
        </li>
        <li>
          <label className="flex items-center gap-2 text-xs text-slate-800 cursor-pointer">
            <input
              type="checkbox"
              checked={filter.histLandmark}
              onChange={(e) => onChange({ ...filter, histLandmark: e.target.checked })}
              className="w-3.5 h-3.5 accent-fuchsia-600"
            />
            <span className="flex-1">Historical Landmark <span className="text-[10px] text-slate-500">(MassGIS MHC)</span></span>
            <span className="tabular-nums text-slate-600">{counts.histLandmark}</span>
          </label>
        </li>
        <li>
          <label className="flex items-center gap-2 text-xs text-slate-800 cursor-pointer">
            <input
              type="checkbox"
              checked={filter.civic}
              onChange={(e) => onChange({ ...filter, civic: e.target.checked })}
              className="w-3.5 h-3.5 accent-fuchsia-600"
            />
            <span className="flex-1">Civic <span className="text-[10px] text-slate-500">(is_civic_poi)</span></span>
            <span className="tabular-nums text-slate-600">{counts.civic}</span>
          </label>
        </li>
      </ul>
      <div className="flex items-center justify-between mt-2 pt-1.5 border-t border-fuchsia-200/60 text-xs">
        <span className="font-medium text-slate-700">Will narrate (union)</span>
        <span className="font-semibold tabular-nums text-fuchsia-700">{counts.audible}</span>
      </div>
    </div>
  )
}

function RouteSection({
  tour,
  stopCount,
  legs,
  computing,
  busy,
  onCompute,
  onRecomputeLeg,
  selectedLegOrder,
  onLegSelect,
  lastCompute,
}: RouteSectionProps) {
  const totalDistance = useMemo(
    () => (legs ?? []).reduce((s, l) => s + l.distance_m, 0),
    [legs],
  )
  const totalDuration = useMemo(
    () => (legs ?? []).reduce((s, l) => s + l.duration_s, 0),
    [legs],
  )
  const expectedLegs = Math.max(0, stopCount - 1)
  const haveLegs = (legs?.length ?? 0) > 0
  const stale = haveLegs && (legs?.length ?? 0) !== expectedLegs

  return (
    <details className="border-y border-slate-200" open>
      <summary className="px-3 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 cursor-pointer bg-slate-50 flex items-center justify-between gap-2">
        <span>Walking route</span>
        <span className="text-[10px] font-normal text-slate-500 normal-case">
          {haveLegs
            ? `${legs!.length} legs · ${(totalDistance / 1000).toFixed(2)} km · ${Math.round(
                totalDuration / 60,
              )} min`
            : 'not computed'}
        </span>
      </summary>
      <div className="px-3 py-2 space-y-2 text-xs">
        <div className="flex items-center gap-2 flex-wrap">
          <button
            type="button"
            onClick={() => void onCompute(false)}
            disabled={computing || busy || stopCount < 2}
            className="text-xs px-3 py-1 rounded bg-emerald-600 text-white hover:bg-emerald-500 disabled:opacity-40"
          >
            {computing ? 'Computing…' : haveLegs ? 'Recompute route' : 'Compute walking route'}
          </button>
          {haveLegs && (
            <button
              type="button"
              onClick={() => {
                if (window.confirm('Force-recompute every leg, including any with manual edits? This will discard hand-edited polylines.')) {
                  void onCompute(true)
                }
              }}
              disabled={computing || busy}
              className="text-xs px-2 py-1 rounded bg-rose-100 text-rose-700 border border-rose-300 hover:bg-rose-200 disabled:opacity-40"
              title="Force overwrite legs that have manual edits"
            >
              Force all
            </button>
          )}
          {stopCount < 2 && (
            <span className="text-amber-700">Add at least 2 waypoints to route.</span>
          )}
          {stale && (
            <span className="text-amber-700">
              Legs out of date — {legs!.length} legs vs {expectedLegs} expected
            </span>
          )}
        </div>

        {/* S190 — suspicious-leg banner. Surfaces legs whose routed
            distance is implausibly long compared to the straight-line
            distance, so the operator can spot bad legs at a glance. */}
        {lastCompute && (lastCompute.suspicious_count ?? 0) > 0 && (
          <div className="border border-amber-300 bg-amber-50 rounded p-2 space-y-1">
            <div className="font-semibold text-amber-800">
              ⚠ {lastCompute.suspicious_count} suspicious leg
              {(lastCompute.suspicious_count ?? 0) === 1 ? '' : 's'}
            </div>
            <ul className="space-y-0.5">
              {lastCompute.legs
                .filter((l) => l.diagnostics?.suspicious)
                .map((l) => (
                  <li key={l.leg_order} className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => onLegSelect?.(l.leg_order)}
                      className="text-amber-900 hover:underline font-mono"
                      title="Highlight this leg on the map"
                    >
                      leg {l.leg_order}
                    </button>
                    <span className="text-amber-900 truncate flex-1">
                      {l.from_label?.replace(/\s*\([^)]+\)\s*$/, '') ?? '?'} →{' '}
                      {l.to_label?.replace(/\s*\([^)]+\)\s*$/, '') ?? '?'}
                    </span>
                    <span className="font-mono text-amber-700 tabular-nums">
                      {l.diagnostics?.straight_m ?? '?'}m straight ·{' '}
                      {l.diagnostics?.routed_m ?? l.distance_m}m routed ·
                      {' '}
                      <strong>{l.diagnostics?.detour_ratio ?? '?'}×</strong>
                    </span>
                  </li>
                ))}
            </ul>
            <div className="text-amber-700 italic text-[11px]">
              Suspicious = routed distance &gt; 1.6× straight-line, OR routed &gt; 800 m for stops &lt;200 m apart.
              Click a leg to select it on the map; use the Recompute button on the leg row to re-route.
            </div>
          </div>
        )}

        {/* Skipped legs (no route, missing coords) */}
        {lastCompute && lastCompute.skipped.length > 0 && (
          <div className="border border-rose-300 bg-rose-50 rounded p-2 space-y-1">
            <div className="font-semibold text-rose-800">
              {lastCompute.skipped.length} leg
              {lastCompute.skipped.length === 1 ? '' : 's'} skipped
            </div>
            <ul className="text-rose-900 space-y-0.5 font-mono text-[11px]">
              {lastCompute.skipped.map((s) => (
                <li key={s.leg_order}>
                  leg {s.leg_order}: {s.reason}
                  {s.from && s.to ? ` — ${s.from.replace(/\s*\([^)]+\)\s*$/, '')} → ${s.to.replace(/\s*\([^)]+\)\s*$/, '')}` : ''}
                  {typeof s.straight_m === 'number' ? ` (${s.straight_m} m straight)` : ''}
                </li>
              ))}
            </ul>
          </div>
        )}

        {haveLegs && (
          <ol className="border border-slate-200 rounded divide-y divide-slate-100 bg-white">
            {legs!.map((leg) => {
              const edited = leg.manual_edits != null
              const isSelected = selectedLegOrder === leg.leg_order
              return (
                <li
                  key={leg.leg_order}
                  onClick={() => onLegSelect?.(leg.leg_order)}
                  className={`px-2 py-1 flex items-baseline gap-2 cursor-pointer ${
                    isSelected ? 'bg-rose-100 hover:bg-rose-100' : 'hover:bg-slate-50'
                  }`}
                  title="Click to highlight this leg on the map"
                >
                  <span
                    className={`font-mono w-6 shrink-0 tabular-nums ${
                      isSelected ? 'text-rose-700 font-semibold' : 'text-slate-400'
                    }`}
                  >
                    {leg.leg_order}
                  </span>
                  <div className="flex-1 min-w-0">
                    <div
                      className={`tabular-nums ${
                        isSelected ? 'text-rose-800' : 'text-slate-700'
                      }`}
                    >
                      {leg.distance_m.toFixed(0)} m · {Math.round(leg.duration_s)} s
                      {edited && (
                        <span className="ml-1 text-amber-600" title="Hand-edited polyline">
                          (edited)
                        </span>
                      )}
                    </div>
                    <div className="text-[10px] text-slate-400 font-mono truncate">
                      {leg.polyline_json.length} pts
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.stopPropagation()
                      if (edited) {
                        if (!window.confirm('This leg has manual edits. Recomputing will discard them. Continue?')) return
                        void onRecomputeLeg(leg.leg_order, true)
                      } else {
                        void onRecomputeLeg(leg.leg_order, false)
                      }
                    }}
                    disabled={busy || computing}
                    title="Recompute this leg"
                    className="px-1.5 py-0.5 rounded text-slate-500 hover:bg-slate-100 disabled:opacity-30"
                  >
                    ↻
                  </button>
                </li>
              )
            })}
          </ol>
        )}

        {haveLegs && tour.id && (
          <div className="text-[10px] text-slate-400 font-mono">
            router: {legs![0]?.router_version ?? '—'}
          </div>
        )}
      </div>
    </details>
  )
}

// ─── Metadata form ──────────────────────────────────────────────────────────

interface TourMetadataFormProps {
  tour: TourSummary
  busy: boolean
  onSave: (patch: Partial<TourSummary>) => void | Promise<void>
}

function TourMetadataForm({ tour, busy, onSave }: TourMetadataFormProps) {
  const [name, setName] = useState(tour.name)
  const [theme, setTheme] = useState(tour.theme)
  const [description, setDescription] = useState(tour.description)
  const [minutes, setMinutes] = useState(String(tour.estimated_minutes))
  const [km, setKm] = useState(String(tour.distance_km))
  const [difficulty, setDifficulty] = useState(tour.difficulty ?? 'moderate')
  const [seasonal, setSeasonal] = useState(Boolean(tour.seasonal))
  const [isHistoricalTour, setIsHistoricalTour] = useState(Boolean(tour.is_historical_tour))
  const [sortOrder, setSortOrder] = useState(String(tour.sort_order ?? 0))
  const lastIdRef = useRef<string | null>(null)

  useEffect(() => {
    if (lastIdRef.current === tour.id) return
    lastIdRef.current = tour.id
    setName(tour.name)
    setTheme(tour.theme)
    setDescription(tour.description)
    setMinutes(String(tour.estimated_minutes))
    setKm(String(tour.distance_km))
    setDifficulty(tour.difficulty ?? 'moderate')
    setSeasonal(Boolean(tour.seasonal))
    setIsHistoricalTour(Boolean(tour.is_historical_tour))
    setSortOrder(String(tour.sort_order ?? 0))
  }, [tour])

  const dirty = useMemo(() => {
    return (
      name !== tour.name ||
      theme !== tour.theme ||
      description !== tour.description ||
      minutes !== String(tour.estimated_minutes) ||
      km !== String(tour.distance_km) ||
      difficulty !== (tour.difficulty ?? 'moderate') ||
      seasonal !== Boolean(tour.seasonal) ||
      isHistoricalTour !== Boolean(tour.is_historical_tour) ||
      sortOrder !== String(tour.sort_order ?? 0)
    )
  }, [name, theme, description, minutes, km, difficulty, seasonal, isHistoricalTour, sortOrder, tour])

  const submit = () => {
    const patch: Partial<TourSummary> = {}
    if (name !== tour.name) patch.name = name
    if (theme !== tour.theme) patch.theme = theme
    if (description !== tour.description) patch.description = description
    const m = parseInt(minutes, 10)
    if (Number.isFinite(m) && m !== tour.estimated_minutes) patch.estimated_minutes = m
    const d = parseFloat(km)
    if (Number.isFinite(d) && d !== tour.distance_km) patch.distance_km = d
    if (difficulty !== (tour.difficulty ?? 'moderate')) patch.difficulty = difficulty
    if (seasonal !== Boolean(tour.seasonal)) patch.seasonal = seasonal
    if (isHistoricalTour !== Boolean(tour.is_historical_tour)) patch.is_historical_tour = isHistoricalTour
    const so = parseInt(sortOrder, 10)
    if (Number.isFinite(so) && so !== (tour.sort_order ?? 0)) patch.sort_order = so
    void onSave(patch)
  }

  return (
    <details className="border-b border-slate-200" open>
      <summary className="px-3 py-2 text-xs font-semibold uppercase tracking-wide text-slate-600 cursor-pointer bg-slate-50">
        Tour metadata
      </summary>
      <div className="px-3 py-2 space-y-2 text-xs">
        <Field label="ID" value={tour.id} mono readOnly />
        <Field label="Name">
          <input
            className="w-full border border-slate-300 rounded px-2 py-1"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </Field>
        <Field label="Theme">
          <input
            className="w-full border border-slate-300 rounded px-2 py-1"
            value={theme}
            onChange={(e) => setTheme(e.target.value)}
          />
        </Field>
        <Field label="Description">
          <textarea
            className="w-full border border-slate-300 rounded px-2 py-1 text-xs"
            rows={3}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </Field>
        <div className="grid grid-cols-2 gap-2">
          <Field label="Minutes">
            <input
              type="number"
              className="w-full border border-slate-300 rounded px-2 py-1"
              value={minutes}
              onChange={(e) => setMinutes(e.target.value)}
            />
          </Field>
          <Field label="Distance (km)">
            <input
              type="number"
              step="0.1"
              className="w-full border border-slate-300 rounded px-2 py-1"
              value={km}
              onChange={(e) => setKm(e.target.value)}
            />
          </Field>
          <Field label="Difficulty">
            <select
              className="w-full border border-slate-300 rounded px-2 py-1 bg-white"
              value={difficulty}
              onChange={(e) => setDifficulty(e.target.value)}
            >
              <option value="easy">easy</option>
              <option value="moderate">moderate</option>
              <option value="hard">hard</option>
            </select>
          </Field>
          <Field label="Sort order">
            <input
              type="number"
              className="w-full border border-slate-300 rounded px-2 py-1"
              value={sortOrder}
              onChange={(e) => setSortOrder(e.target.value)}
            />
          </Field>
        </div>
        <label className="flex items-center gap-2 text-xs text-slate-700">
          <input
            type="checkbox"
            checked={seasonal}
            onChange={(e) => setSeasonal(e.target.checked)}
          />
          Seasonal
        </label>
        <label className="flex items-center gap-2 text-xs text-slate-700">
          <input
            type="checkbox"
            checked={isHistoricalTour}
            onChange={(e) => setIsHistoricalTour(e.target.checked)}
          />
          Historical tour
          <span className="text-slate-500 font-normal">
            — narration uses <code>historical_narration</code> (silent if missing)
          </span>
        </label>
        <div className="flex justify-end pt-1">
          <button
            type="button"
            onClick={submit}
            disabled={busy || !dirty}
            className="text-xs px-3 py-1 rounded bg-indigo-600 text-white hover:bg-indigo-500 disabled:opacity-40"
          >
            {busy ? 'Saving…' : 'Save metadata'}
          </button>
        </div>
      </div>
    </details>
  )
}

// ─── Create-tour form ───────────────────────────────────────────────────────

interface CreateTourInput {
  id: string
  name: string
  theme: string
  description: string
  estimated_minutes: number
  distance_km: number
  difficulty: string
  seasonal: boolean
}

interface CreateTourFormProps {
  busy: boolean
  onCancel: () => void
  onSubmit: (input: CreateTourInput) => void | Promise<void>
}

function CreateTourForm({ busy, onCancel, onSubmit }: CreateTourFormProps) {
  const [id, setId] = useState('tour_')
  const [name, setName] = useState('')
  const [theme, setTheme] = useState('GENERAL')
  const [description, setDescription] = useState('')
  const [minutes, setMinutes] = useState('30')
  const [km, setKm] = useState('1.5')
  const [difficulty, setDifficulty] = useState('moderate')
  const [seasonal, setSeasonal] = useState(false)

  const submit = () => {
    if (!id.trim() || !name.trim()) return
    void onSubmit({
      id: id.trim(),
      name: name.trim(),
      theme: theme.trim() || 'GENERAL',
      description,
      estimated_minutes: parseInt(minutes, 10) || 0,
      distance_km: parseFloat(km) || 0,
      difficulty,
      seasonal,
    })
  }

  return (
    <div className="border-b border-slate-200 bg-emerald-50 px-3 py-2 space-y-2 text-xs">
      <div className="font-semibold text-slate-700">Create new tour</div>
      <Field label="ID (slug)">
        <input
          className="w-full border border-slate-300 rounded px-2 py-1 font-mono"
          value={id}
          onChange={(e) => setId(e.target.value)}
          placeholder="tour_my_new_tour"
        />
      </Field>
      <Field label="Name">
        <input
          className="w-full border border-slate-300 rounded px-2 py-1"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
      </Field>
      <Field label="Theme">
        <input
          className="w-full border border-slate-300 rounded px-2 py-1"
          value={theme}
          onChange={(e) => setTheme(e.target.value)}
        />
      </Field>
      <Field label="Description">
        <textarea
          className="w-full border border-slate-300 rounded px-2 py-1"
          rows={2}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
        />
      </Field>
      <div className="grid grid-cols-2 gap-2">
        <Field label="Minutes">
          <input
            type="number"
            className="w-full border border-slate-300 rounded px-2 py-1"
            value={minutes}
            onChange={(e) => setMinutes(e.target.value)}
          />
        </Field>
        <Field label="Distance (km)">
          <input
            type="number"
            step="0.1"
            className="w-full border border-slate-300 rounded px-2 py-1"
            value={km}
            onChange={(e) => setKm(e.target.value)}
          />
        </Field>
        <Field label="Difficulty">
          <select
            className="w-full border border-slate-300 rounded px-2 py-1 bg-white"
            value={difficulty}
            onChange={(e) => setDifficulty(e.target.value)}
          >
            <option value="easy">easy</option>
            <option value="moderate">moderate</option>
            <option value="hard">hard</option>
          </select>
        </Field>
        <label className="flex items-end gap-2 text-xs text-slate-700">
          <input
            type="checkbox"
            checked={seasonal}
            onChange={(e) => setSeasonal(e.target.checked)}
          />
          Seasonal
        </label>
      </div>
      <div className="flex justify-end gap-2 pt-1">
        <button
          type="button"
          onClick={onCancel}
          className="text-xs px-3 py-1 rounded bg-slate-200 hover:bg-slate-300"
          disabled={busy}
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={submit}
          className="text-xs px-3 py-1 rounded bg-emerald-600 text-white hover:bg-emerald-500 disabled:opacity-50"
          disabled={busy || !id.trim() || !name.trim()}
        >
          {busy ? 'Creating…' : 'Create'}
        </button>
      </div>
    </div>
  )
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function Field({
  label,
  children,
  value,
  mono,
  readOnly,
}: {
  label: string
  children?: React.ReactNode
  value?: string
  mono?: boolean
  readOnly?: boolean
}) {
  return (
    <div>
      <div className="text-[10px] uppercase tracking-wide text-slate-500 mb-0.5">{label}</div>
      {children ?? (
        <div
          className={`px-2 py-1 bg-slate-100 border border-slate-200 rounded text-slate-700 truncate ${
            mono ? 'font-mono text-[10px]' : ''
          }`}
        >
          {value}
          {readOnly ? '' : ''}
        </div>
      )}
    </div>
  )
}

function ToggleButton({
  active,
  onClick,
  title,
  children,
}: {
  active: boolean
  onClick: () => void
  title?: string
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      title={title}
      className={`text-xs px-2 py-1 rounded border ${
        active
          ? 'bg-amber-500 text-white border-amber-600'
          : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
      }`}
    >
      {children}
    </button>
  )
}
