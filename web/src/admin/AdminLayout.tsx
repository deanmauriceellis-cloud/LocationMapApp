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
import { PoiEditDialog } from './PoiEditDialog'
import { WitchTrialsPanel } from './WitchTrialsPanel'
import { ORACLE_BASE, getStatus, type OracleStatus } from './oracleClient'

type AdminView = 'pois' | 'witch-trials'

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
    setSelectedPoi(selection)
  }, [])

  const handleMapSelect = useCallback((selection: PoiSelection) => {
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

  const knownCategories = useMemo(() => {
    if (!pois) return []
    const set = new Set<string>()
    for (const row of pois) {
      if (row.category) set.add(row.category as string)
    }
    return [...set].sort((a, b) => a.localeCompare(b))
  }, [pois])

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
            onClick={() => setView('witch-trials')}
            className={`px-3 py-1 text-sm transition-colors ${
              view === 'witch-trials' ? 'bg-indigo-600 text-white' : 'bg-slate-700 text-slate-300 hover:bg-slate-600'
            }`}
          >
            Witch Trials
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
                />
              </div>
            </aside>

            <main className="flex-1 relative bg-slate-200 min-h-0">
              <AdminMap
                pois={pois}
                selectedPoi={selectedPoi}
                onPoiSelect={handleMapSelect}
                onPoiMoved={handlePoiMoved}
                categoryFilter={mapCategoryFilter}
                onClearCategoryFilter={() => setMapCategoryFilter(null)}
              />
            </main>
          </div>
        </>
      )}

      {/* Edit dialog (POI view only) */}
      <PoiEditDialog
        open={editOpen}
        poi={selectedPoi?.poi ?? null}
        knownCategories={knownCategories}
        oracleAvailable={oraclePill.kind === 'ready'}
        onOracleRefresh={refreshOracleStatus}
        onSaved={handlePoiSaved}
        onDeleted={handlePoiDeleted}
        onClose={handleEditClose}
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
