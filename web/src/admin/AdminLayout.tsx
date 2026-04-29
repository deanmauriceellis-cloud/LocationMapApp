// AdminLayout — Phase 9U (unified salem_pois table)
//
// Shell for the LocationMapApp Salem POI admin tool. Three-pane layout:
//   ┌─────────────────────────────────────────────────────────────────────┐
//   │ Header  [Highlight Duplicates] [Publish]  Oracle: —   [Logout]      │
//   ├──────────────────────┬──────────────────────────────────────────────┤
//   │ POI tree (PoiTree)   │ Map view (AdminMap)                          │
//   │ react-arborist       │ Leaflet + cluster                            │
//   │                      │  + edit dialog modal (PoiEditDialog)         │
//   └──────────────────────┴──────────────────────────────────────────────┘

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { PoiTree, type PoiRow, type PoiSelection, type CategorySelection } from './PoiTree'
import { AdminMap } from './AdminMap'
import { PoiEditDialog, type CategoryRow, type SubcategoryRow } from './PoiEditDialog'
import { WitchTrialsPanel } from './WitchTrialsPanel'
import { TourTree, type AddStopMode } from './TourTree'
import type { TourLeg, TourStop, TourSummary } from './tourTypes'
import { ORACLE_BASE, getStatus, type OracleStatus } from './oracleClient'
import { LintTab } from './LintTab'
import { GeocodesTab } from './GeocodesTab'
import { GeocodeCandidatesModal } from './GeocodeCandidatesModal'
import { AuditTab } from './AuditTab'

type AdminView = 'pois' | 'tours' | 'witch-trials' | 'lint' | 'geocodes' | 'audit'

const ORACLE_POLL_MS = 30_000

type OraclePillState =
  | { kind: 'loading' }
  | { kind: 'ready'; status: OracleStatus & { available: true } }
  | { kind: 'unavailable'; reason: string }

export function AdminLayout() {
  // View toggle (POIs vs Witch Trials)
  const [view, setView] = useState<AdminView>('pois')

  // Category filter — click a category node in PoiTree to hide all POIs not
  // in that category. Click the same category again to clear.
  const [mapCategoryFilter, setMapCategoryFilter] = useState<string | null>(null)
  const handleCategorySelect = useCallback((sel: CategorySelection) => {
    setMapCategoryFilter(sel.category)
  }, [])

  // Shared POI dataset — populated by PoiTree's onDataLoaded callback
  const [pois, setPois] = useState<PoiRow[] | null>(null)
  const [selectedPoi, setSelectedPoi] = useState<PoiSelection | null>(null)
  const [editOpen, setEditOpen] = useState(false)
  // S187 — when the operator navigates from the Lint tab, zoom in 3 closer
  // than the default POI fly-to floor (17 → 20). Cleared on next non-lint
  // selection so map clicks etc. resume normal behavior.
  const [poiFlyToMinZoom, setPoiFlyToMinZoom] = useState<number | undefined>(undefined)
  // S187 — restrict the POI map to only the entities flagged by the current
  // lint check. Independent of categoryFilter (intersected if both set).
  const [lintIdFilter, setLintIdFilter] = useState<Set<string> | null>(null)
  const [lintIdFilterLabel, setLintIdFilterLabel] = useState<string | null>(null)
  const clearLintIdFilter = useCallback(() => {
    setLintIdFilter(null)
    setLintIdFilterLabel(null)
  }, [])
  // S187 — Geocodes modal state.
  const [geocodeModalPoiId, setGeocodeModalPoiId] = useState<string | null>(null)
  const handleLintOpenGeocodes = useCallback((poiId: string) => {
    setGeocodeModalPoiId(poiId)
  }, [])
  const handleGeocodeModalClose = useCallback(() => {
    setGeocodeModalPoiId(null)
  }, [])
  const handleGeocodeChanged = useCallback(() => {
    // Refresh the cached pois list so any open POI editor sees fresh coords.
    // Cheap brute-force: re-fetch the whole list.
    void fetch('/api/admin/salem/pois?include_deleted=true&limit=5000', {
      credentials: 'same-origin',
    })
      .then(r => r.json())
      .then(body => setPois(body.pois ?? null))
      .catch(() => {})
  }, [])

  // S188 — geocode-candidate map preview state. When set, AdminMap renders
  // the source POI + a temp marker at the candidate lat/lng + a line, plus
  // a floating panel for accept / ignore / cancel.
  const [geocodePreview, setGeocodePreview] = useState<{
    sourcePoi: PoiRow
    candidate: {
      lat: number
      lng: number
      rating: number
      normalized_address: string
      distance_m: number
    }
    allCandidates?: Array<{
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
    }>
  } | null>(null)
  const handleGeocodePreviewAccept = useCallback(async () => {
    if (!geocodePreview) return
    const { sourcePoi, candidate } = geocodePreview
    const ok = window.confirm(
      `Move "${sourcePoi.name}"\n` +
      `  from (${sourcePoi.lat.toFixed(5)}, ${sourcePoi.lng.toFixed(5)})\n` +
      `  to   (${candidate.lat.toFixed(5)}, ${candidate.lng.toFixed(5)}) — ${candidate.distance_m} m\n\n` +
      `Tiger normalized: ${candidate.normalized_address}`,
    )
    if (!ok) return
    try {
      const res = await fetch(`/api/admin/salem/pois/${encodeURIComponent(sourcePoi.id)}/move`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ lat: candidate.lat, lng: candidate.lng }),
      })
      if (!res.ok) {
        const body = await res.json().catch(() => ({}))
        throw new Error(body.error || `${res.status} ${res.statusText}`)
      }
      handleGeocodeChanged()
      setGeocodePreview(null)
    } catch (e) {
      window.alert(`Move failed: ${e instanceof Error ? e.message : String(e)}`)
    }
  }, [geocodePreview, handleGeocodeChanged])
  const handleGeocodePreviewIgnore = useCallback(async () => {
    if (!geocodePreview) return
    const { sourcePoi, candidate } = geocodePreview
    const reason = window.prompt(
      `Ignore this geocode candidate for "${sourcePoi.name}"?\n` +
      `(${candidate.lat.toFixed(5)}, ${candidate.lng.toFixed(5)}) — ${candidate.normalized_address}\n\n` +
      `Optional reason (Cancel to abort):`,
      '',
    )
    if (reason === null) return
    try {
      const res = await fetch(
        `/api/admin/salem/lint/poi/${encodeURIComponent(sourcePoi.id)}/blacklist-candidate`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'same-origin',
          body: JSON.stringify({ lat: candidate.lat, lng: candidate.lng, reason: reason || null }),
        },
      )
      if (!res.ok) {
        const body = await res.json().catch(() => ({}))
        throw new Error(body.error || `${res.status} ${res.statusText}`)
      }
      setGeocodePreview(null)
    } catch (e) {
      window.alert(`Ignore failed: ${e instanceof Error ? e.message : String(e)}`)
    }
  }, [geocodePreview])
  const handleGeocodePreviewCancel = useCallback(() => {
    setGeocodePreview(null)
  }, [])
  const handleGeocodePreviewValidateStored = useCallback(async () => {
    if (!geocodePreview) return
    const { sourcePoi } = geocodePreview
    const ok = window.confirm(
      `Mark "${sourcePoi.name}" as VERIFIED at its current coordinates ` +
      `(${(sourcePoi.lat as number).toFixed(5)}, ${(sourcePoi.lng as number).toFixed(5)})?\n\n` +
      `Sets location_status='verified' and stamps location_verified_at to now. Does NOT move the POI.`,
    )
    if (!ok) return
    try {
      const res = await fetch(
        `/api/admin/salem/lint/poi/${encodeURIComponent(sourcePoi.id)}/mark-verified`,
        { method: 'POST', credentials: 'same-origin' },
      )
      if (!res.ok) {
        const body = await res.json().catch(() => ({}))
        throw new Error(body.error || `${res.status} ${res.statusText}`)
      }
      handleGeocodeChanged()
      setGeocodePreview(null)
    } catch (e) {
      window.alert(`Validate failed: ${e instanceof Error ? e.message : String(e)}`)
    }
  }, [geocodePreview, handleGeocodeChanged])
  const handleGeocodePreviewEditAddress = useCallback(() => {
    if (!geocodePreview) return
    const { sourcePoi } = geocodePreview
    setGeocodePreview(null)
    setSelectedPoi({ poi: sourcePoi })
    setEditOpen(true)
  }, [geocodePreview])
  // S177 P5 — Directions overlay target. When set, AdminMap fetches a route
  // from the current map center to this POI and renders a polyline.
  const [directionsTarget, setDirectionsTarget] = useState<PoiRow | null>(null)
  const handleShowDirections = useCallback((poi: PoiRow) => {
    setDirectionsTarget(poi)
  }, [])
  const handleClearDirections = useCallback(() => {
    setDirectionsTarget(null)
  }, [])

  // Tour-mode state (S174)
  const [activeTour, setActiveTour] = useState<TourSummary | null>(null)
  const [tourStops, setTourStops] = useState<TourStop[] | null>(null)
  // S183 — precomputed walking legs for the active tour, lifted from TourTree
  // so AdminMap can render them as a polyline overlay.
  const [tourLegs, setTourLegs] = useState<TourLeg[] | null>(null)
  // S184 — selected leg in the side panel highlights red on the map; clicking
  // a leg polyline on the map selects the matching row.
  const [selectedLegOrder, setSelectedLegOrder] = useState<number | null>(null)
  // S184 — clicking a waypoint row pans the map to that stop. The nonce bumps
  // on every click (even repeat clicks of the same stop) so FlyToStop re-fires.
  const [focusedStopId, setFocusedStopId] = useState<number | null>(null)
  const [focusedStopNonce, setFocusedStopNonce] = useState(0)
  const [tourRefreshKey, setTourRefreshKey] = useState(0)
  const [addStopMode, setAddStopMode] = useState<AddStopMode>('none')

  const handleTourSelect = useCallback((tour: TourSummary, stops: TourStop[]) => {
    setActiveTour(tour)
    setTourStops(stops)
    // Reset leg/waypoint selection when switching tours; the new tour's
    // legs will arrive shortly via onLegsChange.
    setSelectedLegOrder(null)
    setFocusedStopId(null)
    // Don't clear legs here — TourTree will fetch fresh legs for the new tour
    // and push them up via onLegsChange.
  }, [])

  const handleLegsChange = useCallback((legs: TourLeg[] | null) => {
    setTourLegs(legs)
  }, [])

  const handleLegSelect = useCallback((legOrder: number) => {
    setSelectedLegOrder((prev) => (prev === legOrder ? null : legOrder))
  }, [])

  const handleFocusStop = useCallback((stopId: number) => {
    setFocusedStopId(stopId)
    setFocusedStopNonce((n) => n + 1)
  }, [])

  const handleStopMoved = useCallback((stopId: number, lat: number, lng: number) => {
    setTourStops((prev) => {
      if (!prev) return prev
      return prev.map((s) =>
        s.stop_id === stopId
          ? {
              ...s,
              override_lat: lat,
              override_lng: lng,
              effective_lat: lat,
              effective_lng: lng,
            }
          : s,
      )
    })
    setTourRefreshKey((k) => k + 1)
  }, [])

  // Add a free waypoint at the clicked map coords. Resets the add mode after
  // a successful drop so the operator clicks again deliberately.
  const handleAddFreeStop = useCallback(
    async (lat: number, lng: number) => {
      if (!activeTour) return
      try {
        const res = await fetch(
          `/api/admin/salem/tours/${encodeURIComponent(activeTour.id)}/stops`,
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify({ lat, lng }),
          },
        )
        if (!res.ok) {
          let msg = `${res.status} ${res.statusText}`
          try {
            const body = await res.json()
            if (body?.error) msg = `${res.status} ${body.error}`
          } catch { /* not json */ }
          throw new Error(msg)
        }
        setAddStopMode('none')
        setTourRefreshKey((k) => k + 1)
      } catch (e) {
        window.alert(`Could not add waypoint: ${e instanceof Error ? e.message : String(e)}`)
      }
    },
    [activeTour],
  )

  const handlePickPoiForStop = useCallback(
    async (poi: PoiRow) => {
      if (!activeTour) return
      try {
        const res = await fetch(
          `/api/admin/salem/tours/${encodeURIComponent(activeTour.id)}/stops`,
          {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify({ poi_id: poi.id }),
          },
        )
        if (!res.ok) {
          let msg = `${res.status} ${res.statusText}`
          try {
            const body = await res.json()
            if (body?.error) msg = `${res.status} ${body.error}`
          } catch { /* not json */ }
          throw new Error(msg)
        }
        setAddStopMode('none')
        setTourRefreshKey((k) => k + 1)
      } catch (e) {
        window.alert(`Could not add POI as stop: ${e instanceof Error ? e.message : String(e)}`)
      }
    },
    [activeTour],
  )

  // Oracle health pill
  const [oraclePill, setOraclePill] = useState<OraclePillState>({ kind: 'loading' })
  const mountedRef = useRef(true)

  const refreshOracleStatus = useCallback(async () => {
    try {
      const status = await getStatus()
      if (!mountedRef.current) return
      if (status.available) {
        setOraclePill({ kind: 'ready', status })
      } else {
        setOraclePill({
          kind: 'unavailable',
          reason: status.reason || 'Oracle reported unavailable',
        })
      }
    } catch (e) {
      if (!mountedRef.current) return
      setOraclePill({
        kind: 'unavailable',
        reason: e instanceof Error ? e.message : String(e),
      })
    }
  }, [])

  useEffect(() => {
    mountedRef.current = true
    void refreshOracleStatus()
    const id = window.setInterval(() => void refreshOracleStatus(), ORACLE_POLL_MS)
    return () => {
      mountedRef.current = false
      window.clearInterval(id)
    }
  }, [refreshOracleStatus])

  const handleDataLoaded = useCallback((data: PoiRow[]) => {
    setPois(data)
  }, [])

  const handleHighlightDuplicates = useCallback(() => {
    console.log('[admin] Highlight Duplicates clicked — wiring lands in 9P.11')
  }, [])

  const handlePublish = useCallback(() => {
    console.log('[admin] Publish clicked — wiring lands in 9P.13')
  }, [])

  const handleTreeSelect = useCallback((selection: PoiSelection) => {
    setPoiFlyToMinZoom(undefined)
    setSelectedPoi(selection)
  }, [])

  const handleMapSelect = useCallback((selection: PoiSelection) => {
    // Map clicks should not auto-clear an active lint filter — operator may
    // be inspecting markers within the filtered set. Only zoom-floor resets.
    setPoiFlyToMinZoom(undefined)
    setSelectedPoi(selection)
    setEditOpen(true)
  }, [])

  const handlePoiSaved = useCallback(
    (updated: PoiRow) => {
      setPois((prev) => {
        if (!prev) return prev
        return prev.map((row) =>
          row.id === updated.id ? { ...row, ...updated } : row,
        )
      })
      setSelectedPoi((prev) => {
        if (!prev || prev.poi.id !== updated.id) return prev
        return { poi: { ...prev.poi, ...updated } }
      })
    },
    [],
  )

  const handlePoiDeleted = useCallback(
    (id: string, deletedAt: string) => {
      setPois((prev) => {
        if (!prev) return prev
        return prev.map((row) =>
          row.id === id ? { ...row, deleted_at: deletedAt } : row,
        )
      })
      setSelectedPoi((prev) =>
        prev && prev.poi.id === id ? null : prev,
      )
    },
    [],
  )

  const handleEditClose = useCallback(() => {
    setEditOpen(false)
  }, [])

  // ── Lint tab handlers (S187) ──────────────────────────────────────────────
  // Lint items only carry an entity_id. We resolve to the full PoiRow either
  // from the cached `pois` list (if the operator has already loaded the POI
  // view) or by fetching /admin/salem/pois/:id on demand.
  const resolvePoi = useCallback(async (poiId: string): Promise<PoiRow | null> => {
    if (pois) {
      const hit = pois.find(p => p.id === poiId)
      if (hit) return hit
    }
    try {
      const res = await fetch(`/api/admin/salem/pois/${encodeURIComponent(poiId)}`, {
        credentials: 'same-origin',
      })
      if (!res.ok) return null
      const body = await res.json()
      const row = body.poi ?? body
      return {
        ...row,
        lat: typeof row.lat === 'string' ? parseFloat(row.lat) : row.lat,
        lng: typeof row.lng === 'string' ? parseFloat(row.lng) : row.lng,
      } as PoiRow
    } catch (e) {
      console.error('[AdminLayout] resolvePoi failed:', e)
      return null
    }
  }, [pois])

  const handleLintOpenPoi = useCallback(async (poiId: string) => {
    const poi = await resolvePoi(poiId)
    if (!poi) {
      window.alert(`Could not load POI ${poiId} — it may have been hard-deleted.`)
      return
    }
    setPoiFlyToMinZoom(20)
    setSelectedPoi({ poi })
    setEditOpen(true)
  }, [resolvePoi])

  const handleLintShowPoiOnMap = useCallback(async (
    poiId: string,
    checkPoiIds: string[],
    checkLabel: string,
  ) => {
    const poi = await resolvePoi(poiId)
    if (!poi) {
      window.alert(`Could not load POI ${poiId} — it may have been hard-deleted.`)
      return
    }
    setLintIdFilter(new Set(checkPoiIds))
    setLintIdFilterLabel(`${checkLabel} · ${checkPoiIds.length}`)
    setPoiFlyToMinZoom(20)
    setSelectedPoi({ poi })
    setView('pois')
  }, [resolvePoi])

  const handleLintOpenTour = useCallback((tourId: string) => {
    setView('tours')
    // Tour selection wiring is owned by TourTree; operator picks the tour
    // from the side tree. We surface the id so they know which one.
    console.log(`[lint] Open tour requested: ${tourId} — pick from the tour tree.`)
  }, [])

  const knownCategories = useMemo(() => {
    if (!pois) return []
    const set = new Set<string>()
    for (const row of pois) {
      if (row.category) set.add(row.category as string)
    }
    return [...set].sort((a, b) => a.localeCompare(b))
  }, [pois])

  // S190 — canonical taxonomy from salem_poi_categories /
  // salem_poi_subcategories. Drives the PoiEditDialog Category and
  // Subcategory <select> dropdowns and the inline "+ Add new" forms.
  // Refetched after a successful POST so the new entry is selectable.
  const [categories, setCategories] = useState<CategoryRow[]>([])
  const [subcategories, setSubcategories] = useState<SubcategoryRow[]>([])

  const refetchTaxonomy = useCallback(async () => {
    try {
      const [catRes, subRes] = await Promise.all([
        fetch('/api/admin/salem/categories', { credentials: 'same-origin' }),
        fetch('/api/admin/salem/subcategories', { credentials: 'same-origin' }),
      ])
      if (catRes.ok) {
        const body = await catRes.json()
        setCategories((body.categories || []) as CategoryRow[])
      }
      if (subRes.ok) {
        const body = await subRes.json()
        setSubcategories((body.subcategories || []) as SubcategoryRow[])
      }
    } catch (e) {
      console.warn('[admin] taxonomy fetch failed:', e)
    }
  }, [])

  useEffect(() => {
    void refetchTaxonomy()
  }, [refetchTaxonomy])

  const handlePoiMoved = useCallback(
    (id: string, lat: number, lng: number) => {
      setPois((prev) => {
        if (!prev) return prev
        return prev.map((row) =>
          row.id === id ? { ...row, lat, lng } : row,
        )
      })
      setSelectedPoi((prev) => {
        if (!prev || prev.poi.id !== id) return prev
        return { poi: { ...prev.poi, lat, lng } }
      })
    },
    [],
  )

  const handleLogout = useCallback(() => {
    try {
      const xhr = new XMLHttpRequest()
      xhr.open('GET', '/api/admin/salem/pois', false, 'logout', 'wrongpassword')
      xhr.send()
    } catch {
      // credential overwrite attempt
    }
    window.location.reload()
  }, [])

  return (
    <div className="h-screen w-screen flex flex-col bg-slate-100 text-slate-900">
      {/* Header */}
      <header className="flex items-center gap-3 px-4 h-12 bg-slate-800 text-slate-100 shadow">
        <h1 className="text-base font-semibold tracking-wide mr-2">
          LocationMapApp Admin
        </h1>

        {/* View toggle */}
        <div className="flex rounded overflow-hidden border border-slate-600 mr-3">
          <button
            type="button"
            onClick={() => setView('pois')}
            className={`px-3 py-1 text-sm transition-colors ${
              view === 'pois' ? 'bg-indigo-600 text-white' : 'bg-slate-700 text-slate-300 hover:bg-slate-600'
            }`}
          >
            POIs
          </button>
          <button
            type="button"
            onClick={() => setView('tours')}
            className={`px-3 py-1 text-sm transition-colors ${
              view === 'tours' ? 'bg-indigo-600 text-white' : 'bg-slate-700 text-slate-300 hover:bg-slate-600'
            }`}
          >
            Tours
          </button>
          <button
            type="button"
            onClick={() => setView('witch-trials')}
            className={`px-3 py-1 text-sm transition-colors ${
              view === 'witch-trials' ? 'bg-indigo-600 text-white' : 'bg-slate-700 text-slate-300 hover:bg-slate-600'
            }`}
          >
            Witch Trials
          </button>
          <button
            type="button"
            onClick={() => setView('lint')}
            className={`px-3 py-1 text-sm transition-colors ${
              view === 'lint' ? 'bg-indigo-600 text-white' : 'bg-slate-700 text-slate-300 hover:bg-slate-600'
            }`}
            title="Data-quality lint scan"
          >
            Lint
          </button>
          <button
            type="button"
            onClick={() => setView('geocodes')}
            className={`px-3 py-1 text-sm transition-colors ${
              view === 'geocodes' ? 'bg-indigo-600 text-white' : 'bg-slate-700 text-slate-300 hover:bg-slate-600'
            }`}
            title="Address ↔ stored coords deep verification"
          >
            Geocodes
          </button>
          <button
            type="button"
            onClick={() => setView('audit')}
            className={`px-3 py-1 text-sm transition-colors ${
              view === 'audit' ? 'bg-indigo-600 text-white' : 'bg-slate-700 text-slate-300 hover:bg-slate-600'
            }`}
            title="Change audit log — every admin/automation edit, with revert"
          >
            Audit
          </button>
        </div>

        {view === 'pois' && (
          <>
            <button
              type="button"
              onClick={handleHighlightDuplicates}
              className="px-3 py-1 text-sm rounded bg-slate-700 hover:bg-slate-600 transition-colors"
            >
              Highlight Duplicates
            </button>
            <button
              type="button"
              onClick={handlePublish}
              className="px-3 py-1 text-sm rounded bg-emerald-700 hover:bg-emerald-600 transition-colors"
            >
              Publish
            </button>
          </>
        )}

        <div className="flex-1" />

        <OraclePill state={oraclePill} onRefresh={refreshOracleStatus} />

        <button
          type="button"
          onClick={handleLogout}
          className="px-3 py-1 text-sm rounded bg-slate-700 hover:bg-slate-600 transition-colors"
        >
          Logout
        </button>
      </header>

      {/* Body */}
      {view === 'witch-trials' ? (
        <div className="flex-1 min-h-0">
          <WitchTrialsPanel />
        </div>
      ) : view === 'lint' ? (
        <div className="flex-1 min-h-0">
          <LintTab
            onOpenPoi={handleLintOpenPoi}
            onShowPoiOnMap={handleLintShowPoiOnMap}
            onOpenTour={handleLintOpenTour}
            onOpenGeocodes={handleLintOpenGeocodes}
          />
        </div>
      ) : view === 'geocodes' ? (
        <div className="flex-1 min-h-0">
          <GeocodesTab
            onOpenPoi={handleLintOpenPoi}
            onShowPoiOnMap={handleLintShowPoiOnMap}
            onOpenGeocodes={handleLintOpenGeocodes}
          />
        </div>
      ) : view === 'audit' ? (
        <div className="flex-1 min-h-0">
          <AuditTab />
        </div>
      ) : view === 'tours' ? (
        <div className="flex-1 flex min-h-0">
          <aside className="w-80 border-r border-slate-300 bg-white flex flex-col min-h-0">
            <div className="px-3 py-2 border-b border-slate-200 text-xs font-semibold uppercase tracking-wide text-slate-600">
              Tours
            </div>
            <div className="flex-1 min-h-0">
              <TourTree
                selectedTourId={activeTour?.id ?? null}
                onTourSelect={handleTourSelect}
                refreshKey={tourRefreshKey}
                addStopMode={addStopMode}
                onAddStopModeChange={setAddStopMode}
                onLegsChange={handleLegsChange}
                selectedLegOrder={selectedLegOrder}
                onLegSelect={handleLegSelect}
                onFocusStop={handleFocusStop}
              />
            </div>
          </aside>

          <main className="flex-1 relative bg-slate-200 min-h-0">
            <AdminMap
              pois={pois}
              selectedPoi={selectedPoi}
              selectedPoiMinZoom={poiFlyToMinZoom}
              onPoiSelect={handleMapSelect}
              onPoiMoved={handlePoiMoved}
              categoryFilter={mapCategoryFilter}
              onClearCategoryFilter={() => setMapCategoryFilter(null)}
              categories={categories}
              activeTour={activeTour}
              tourStops={tourStops}
              tourLegs={tourLegs}
              selectedLegOrder={selectedLegOrder}
              onLegSelect={handleLegSelect}
              focusedStopId={focusedStopId}
              focusedStopNonce={focusedStopNonce}
              onStopMoved={handleStopMoved}
              addStopMode={addStopMode}
              onCancelAddStopMode={() => setAddStopMode('none')}
              onMapClickAddFree={handleAddFreeStop}
              onPickPoiForStop={handlePickPoiForStop}
              directionsTarget={directionsTarget}
              onClearDirections={handleClearDirections}
            />
          </main>
        </div>
      ) : (
        <>
          {/* POI view: tree + map */}
          <div className="flex-1 flex min-h-0">
            <aside className="w-80 border-r border-slate-300 bg-white flex flex-col min-h-0">
              <div className="px-3 py-2 border-b border-slate-200 text-xs font-semibold uppercase tracking-wide text-slate-600">
                POIs
              </div>
              <div className="flex-1 min-h-0">
                <PoiTree
                  onSelect={handleTreeSelect}
                  onCategorySelect={handleCategorySelect}
                  onDataLoaded={handleDataLoaded}
                  externalPois={pois}
                  categories={categories}
                  subcategories={subcategories}
                />
              </div>
            </aside>

            <main className="flex-1 relative bg-slate-200 min-h-0">
              <AdminMap
                pois={pois}
                selectedPoi={selectedPoi}
                selectedPoiMinZoom={poiFlyToMinZoom}
                onPoiSelect={handleMapSelect}
                onPoiMoved={handlePoiMoved}
                categoryFilter={mapCategoryFilter}
                onClearCategoryFilter={() => setMapCategoryFilter(null)}
                categories={categories}
                lintIdFilter={lintIdFilter}
                lintIdFilterLabel={lintIdFilterLabel}
                onClearLintIdFilter={clearLintIdFilter}
                directionsTarget={directionsTarget}
                onClearDirections={handleClearDirections}
                geocodePreview={geocodePreview}
                onGeocodePreviewAccept={handleGeocodePreviewAccept}
                onGeocodePreviewIgnore={handleGeocodePreviewIgnore}
                onGeocodePreviewCancel={handleGeocodePreviewCancel}
                onGeocodePreviewValidateStored={handleGeocodePreviewValidateStored}
                onGeocodePreviewEditAddress={handleGeocodePreviewEditAddress}
              />
            </main>
          </div>
        </>
      )}

      {/* Geocodes modal (Lint tab) */}
      <GeocodeCandidatesModal
        open={geocodeModalPoiId != null}
        poiId={geocodeModalPoiId}
        onClose={handleGeocodeModalClose}
        onChanged={handleGeocodeChanged}
        onOpenSibling={(id) => {
          setGeocodeModalPoiId(null)
          void handleLintOpenPoi(id)
        }}
        onShowSiblingOnMap={async (id) => {
          setGeocodeModalPoiId(null)
          const poi = await resolvePoi(id)
          if (!poi) {
            window.alert(`Could not load POI ${id} — it may have been hard-deleted.`)
            return
          }
          setPoiFlyToMinZoom(20)
          setSelectedPoi({ poi })
          setView('pois')
        }}
        onPreviewCandidate={async (sourcePoiId, candidate, allCandidates) => {
          setGeocodeModalPoiId(null)
          const sourcePoi = await resolvePoi(sourcePoiId)
          if (!sourcePoi) {
            window.alert(`Could not load POI ${sourcePoiId}.`)
            return
          }
          setSelectedPoi({ poi: sourcePoi })
          setPoiFlyToMinZoom(19)
          setView('pois')
          setGeocodePreview({ sourcePoi, candidate, allCandidates })
        }}
        onShowClusterOnMap={async (focalId, dupeIds, label) => {
          setGeocodeModalPoiId(null)
          const all = [focalId, ...dupeIds]
          const focal = await resolvePoi(focalId)
          if (!focal) {
            window.alert(`Could not load focal POI ${focalId}.`)
            return
          }
          setLintIdFilter(new Set(all))
          setLintIdFilterLabel(`${label} · ${all.length}`)
          setPoiFlyToMinZoom(20)
          setSelectedPoi({ poi: focal })
          setView('pois')
        }}
      />

      {/* Edit dialog (POI view only) */}
      <PoiEditDialog
        open={editOpen}
        poi={selectedPoi?.poi ?? null}
        knownCategories={knownCategories}
        categories={categories}
        subcategories={subcategories}
        onTaxonomyChanged={refetchTaxonomy}
        oracleAvailable={oraclePill.kind === 'ready'}
        onOracleRefresh={refreshOracleStatus}
        onSaved={handlePoiSaved}
        onDeleted={handlePoiDeleted}
        onClose={handleEditClose}
        onShowDirections={handleShowDirections}
      />
    </div>
  )
}

// ─── Oracle status pill ─────────────────────────────────────────────────────

interface OraclePillProps {
  state: OraclePillState
  onRefresh: () => void | Promise<void>
}

function OraclePill({ state, onRefresh }: OraclePillProps) {
  const handleClick = () => {
    void onRefresh()
  }

  if (state.kind === 'loading') {
    return (
      <button
        type="button"
        onClick={handleClick}
        className="px-2 py-1 text-xs rounded-full bg-slate-700 text-slate-300 hover:bg-slate-600"
        title="Checking Salem Oracle status…"
      >
        Oracle: …
      </button>
    )
  }

  if (state.kind === 'ready') {
    const s = state.status
    const tooltip =
      `Salem Oracle ready at ${ORACLE_BASE} — gemma3:27b backed by the full Salem corpus\n` +
      `${s.fact_count.toLocaleString()} facts · ` +
      `${s.primary_source_count.toLocaleString()} primary sources · ` +
      `${s.poi_count} POIs · ${s.newspaper_count} newspapers\n` +
      `History: ${s.history_len} turns · click to re-check`
    return (
      <button
        type="button"
        onClick={handleClick}
        className="px-2 py-1 text-xs rounded-full bg-emerald-700 text-emerald-50 hover:bg-emerald-600"
        title={tooltip}
      >
        Oracle: ready
      </button>
    )
  }

  const tooltip =
    `Oracle is unavailable: ${state.reason}\n\n` +
    `Start the Salem testapp:\n` +
    `  bash ~/Development/Salem/scripts/start-testapp.sh\n\n` +
    `Then click this pill to re-check.`
  return (
    <button
      type="button"
      onClick={handleClick}
      className="px-2 py-1 text-xs rounded-full bg-rose-700 text-rose-50 hover:bg-rose-600"
      title={tooltip}
    >
      Oracle: down
    </button>
  )
}
