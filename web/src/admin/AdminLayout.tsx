// AdminLayout — Phase 9P.B Steps 9P.7-9P.10
//
// Shell for the LocationMapApp Salem POI admin tool. Three-pane layout:
//   ┌─────────────────────────────────────────────────────────────────────┐
//   │ Header  [Highlight Duplicates] [Publish]  Oracle: —   [Logout]      │
//   ├──────────────────────┬──────────────────────────────────────────────┤
//   │ POI tree (9P.8 ✓)    │ Map view (9P.9 ✓)                            │
//   │ react-arborist       │ Leaflet + cluster                            │
//   │                      │  + edit dialog modal (9P.10 ✓)               │
//   └──────────────────────┴──────────────────────────────────────────────┘
//
// Step status:
//   9P.7  ✓ shell (this file)
//   9P.8  ✓ POI tree (PoiTree.tsx, wired into the left pane)
//   9P.9  ✓ Admin map view (AdminMap.tsx, wired into the center pane)
//   9P.10 ✓ POI edit dialog (PoiEditDialog.tsx; opens from marker click)
//   9P.10a — Linked Historical Content tab (currently a stub in the dialog)
//   9P.10b — Salem Oracle integration (the "Oracle: —" pill goes live + a
//            "Generate with Salem Oracle" button appears in the edit dialog)
//   9P.11 — Highlight Duplicates wiring
//   9P.13 — Publish wiring
//
// ─── Selection vs. edit-open semantics (9P.10) ─────────────────────────────
// Tree click: SELECTS the POI → AdminMap flies to it + gold-rings the marker.
//             Does NOT open the edit dialog (operator may just be browsing).
// Marker click: SELECTS *and* OPENS the edit dialog (per master plan §1296).
// This is implemented via two callbacks: handleTreeSelect (just sets the
// selection) and handleMapSelect (sets the selection + flips editOpen=true).
//
// Data model (9P.9):
//   PoiTree owns the initial fetch of all 1,720 POIs (kind=tour|business|
//   narration). It calls back via onDataLoaded once at mount, AdminLayout
//   stores the dataset in `byKind`, and passes it to AdminMap so the map
//   does NOT re-fetch (one Basic Auth prompt, one network round-trip).
//   After a successful drag-to-move, AdminMap calls onPoiMoved → AdminLayout
//   patches the lat/lng on the matching row in `byKind`, then PoiTree
//   re-renders against the same shared snapshot via the externalByKind prop.
//
// Selection model (9P.9):
//   Clicking a POI in either the tree OR a marker on the map sets
//   `selectedPoi` here. PoiTree has no selected-row prop today (its visual
//   highlight is internal to react-arborist), but AdminMap consumes
//   `selectedPoi` to fly to the row and gold-ring the marker.
//
// Auth model: HTTP Basic Auth via the browser's native dialog. The
// cache-proxy admin endpoints (Phase 9P.3 middleware) return 401 with
// WWW-Authenticate: Basic. The first admin API call from this page (the
// tree fetch in 9P.8) triggers the prompt; subsequent requests reuse the
// browser's cached credentials. There is no in-page login form by design.

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { PoiTree, type PoiKind, type PoiRow, type PoiSelection } from './PoiTree'
import { AdminMap } from './AdminMap'
import { PoiEditDialog } from './PoiEditDialog'
import { ORACLE_BASE, getStatus, type OracleStatus } from './oracleClient'

// 9P.10b: how often the header pill re-checks the Oracle. The check itself
// is sub-millisecond on the Salem side and 5s timeout client-side, so this
// is cheap; we re-poll because the operator may bounce the testapp between
// admin actions and the pill should reflect that within ~30s without a
// manual page reload.
const ORACLE_POLL_MS = 30_000

/** UI state for the header Oracle pill. */
type OraclePillState =
  | { kind: 'loading' }
  | { kind: 'ready'; status: OracleStatus & { available: true } }
  | { kind: 'unavailable'; reason: string }

export function AdminLayout() {
  // Shared POI dataset — populated by PoiTree's onDataLoaded callback after
  // the initial fetch, then passed down to PoiTree (as externalByKind, so
  // both panes always see identical data) and to AdminMap.
  const [byKind, setByKind] = useState<Record<PoiKind, PoiRow[]> | null>(null)
  const [selectedPoi, setSelectedPoi] = useState<PoiSelection | null>(null)
  // 9P.10: edit dialog visibility. Marker click opens it; tree click does not.
  const [editOpen, setEditOpen] = useState(false)

  // 9P.10b: live Oracle health pill in the header. We poll once at mount and
  // every ORACLE_POLL_MS thereafter, in addition to letting the edit dialog
  // re-trigger a poll on demand if the operator wants to retry after starting
  // the testapp. mountedRef guards against `setState` after unmount in the
  // 5s status timeout race.
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

  const handleDataLoaded = useCallback((data: Record<PoiKind, PoiRow[]>) => {
    setByKind(data)
  }, [])

  const handleHighlightDuplicates = useCallback(() => {
    // TODO 9P.11 — fetch /api/admin/salem/pois/duplicates?radius=15 and draw
    // red rings on the map view.
    console.log('[admin] Highlight Duplicates clicked — wiring lands in 9P.11')
  }, [])

  const handlePublish = useCallback(() => {
    // TODO 9P.13 — POST /api/admin/publish to regenerate the bundled
    // salem-content/salem_content.db from PG so the next APK build picks
    // up admin edits. This is the "publish loop" half of the user's
    // "update both the database and where they are sourced" requirement.
    console.log('[admin] Publish clicked — wiring lands in 9P.13')
  }, [])

  // Tree click — selects only (drives map fly-to + ring), does NOT open
  // the edit dialog. The operator may just be browsing the tree.
  const handleTreeSelect = useCallback((selection: PoiSelection) => {
    setSelectedPoi(selection)
  }, [])

  // Marker click — selects AND opens the edit dialog (master plan §1296).
  const handleMapSelect = useCallback((selection: PoiSelection) => {
    setSelectedPoi(selection)
    setEditOpen(true)
  }, [])

  // 9P.10: when the dialog saves successfully, patch the matching row in the
  // shared byKind snapshot so the tree (via externalByKind) and the map (via
  // its byKind prop) reflect the new field values without re-fetching.
  const handlePoiSaved = useCallback(
    (kind: PoiKind, updated: PoiRow) => {
      setByKind((prev) => {
        if (!prev) return prev
        const next = { ...prev }
        next[kind] = prev[kind].map((row) =>
          row.id === updated.id ? { ...row, ...updated } : row,
        )
        return next
      })
      setSelectedPoi((prev) => {
        if (!prev || prev.kind !== kind || prev.poi.id !== updated.id) return prev
        return { kind, poi: { ...prev.poi, ...updated } }
      })
    },
    [],
  )

  // 9P.10: when the dialog soft-deletes, mark the row as deleted in the
  // shared snapshot. PoiTree filters deleted rows by default (toggle reveals);
  // AdminMap excludes deleted rows from the map entirely.
  const handlePoiDeleted = useCallback(
    (kind: PoiKind, id: string, deletedAt: string) => {
      setByKind((prev) => {
        if (!prev) return prev
        const next = { ...prev }
        next[kind] = prev[kind].map((row) =>
          row.id === id ? { ...row, deleted_at: deletedAt } : row,
        )
        return next
      })
      // Drop the selection — the deleted POI shouldn't keep the gold ring.
      setSelectedPoi((prev) =>
        prev && prev.kind === kind && prev.poi.id === id ? null : prev,
      )
    },
    [],
  )

  const handleEditClose = useCallback(() => {
    setEditOpen(false)
  }, [])

  // Observed category / business_type values from the loaded dataset, used
  // to populate the edit dialog's <datalist> autocomplete. Computed once
  // per byKind change. The tree currently has 7 categories for tour, 19
  // business_types, and 29 narration categories.
  const knownCategories = useMemo(() => {
    if (!selectedPoi || !byKind) return []
    const rows = byKind[selectedPoi.kind] ?? []
    const set = new Set<string>()
    for (const row of rows) {
      const v =
        selectedPoi.kind === 'business'
          ? (row.business_type as string | undefined)
          : (row.category as string | undefined)
      if (v) set.add(v)
    }
    return [...set].sort((a, b) => a.localeCompare(b))
  }, [byKind, selectedPoi])

  const handlePoiMoved = useCallback(
    (kind: PoiKind, id: string, lat: number, lng: number) => {
      // Patch the matching row's lat/lng in the shared snapshot. Both the
      // tree (via externalByKind) and the map (via byKind) re-render off
      // this — the tree's display doesn't show coords, but its emitted
      // PoiSelection rides the new values forward to the edit dialog.
      setByKind((prev) => {
        if (!prev) return prev
        const next = { ...prev }
        next[kind] = prev[kind].map((row) =>
          row.id === id ? { ...row, lat, lng } : row,
        )
        return next
      })
      // If the moved POI is currently selected, update the selection too so
      // the gold-ringed marker doesn't lag behind the new position.
      setSelectedPoi((prev) => {
        if (!prev || prev.kind !== kind || prev.poi.id !== id) return prev
        return { kind, poi: { ...prev.poi, lat, lng } }
      })
    },
    [],
  )

  const handleLogout = useCallback(() => {
    // Browser-cached HTTP Basic Auth has no formal logout. The widely-used
    // workaround is to fire an XMLHttpRequest at a URL the server will reject
    // with 401, supplying obviously-bad credentials in the URL itself. The
    // browser caches the *new* (wrong) credentials for the origin, replacing
    // the good ones. The next admin API call therefore re-prompts.
    //
    // We point at /api/admin/salem/pois because we know it requires auth and
    // is harmless to hit. The hardcoded "logout:wrongpassword" credentials
    // are intentional; they exist purely to overwrite the cache.
    try {
      const xhr = new XMLHttpRequest()
      xhr.open('GET', '/api/admin/salem/pois', false, 'logout', 'wrongpassword')
      xhr.send()
    } catch {
      // Some browsers throw on the deprecated sync XHR or on the auth header
      // shape — that's fine, the credential overwrite still happens in most.
    }
    // Force a reload so the next admin request re-prompts cleanly.
    window.location.reload()
  }, [])

  return (
    <div className="h-screen w-screen flex flex-col bg-slate-100 text-slate-900">
      {/* Header */}
      <header className="flex items-center gap-3 px-4 h-12 bg-slate-800 text-slate-100 shadow">
        <h1 className="text-base font-semibold tracking-wide mr-4">
          LocationMapApp Admin
          <span className="ml-2 text-xs font-normal text-slate-400">/ Salem POIs</span>
        </h1>

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

        {/* Spacer */}
        <div className="flex-1" />

        {/* Oracle status pill (9P.10b — live polled) */}
        <OraclePill state={oraclePill} onRefresh={refreshOracleStatus} />

        <button
          type="button"
          onClick={handleLogout}
          className="px-3 py-1 text-sm rounded bg-slate-700 hover:bg-slate-600 transition-colors"
        >
          Logout
        </button>
      </header>

      {/* Body: tree + map */}
      <div className="flex-1 flex min-h-0">
        {/* Left pane — POI tree (9P.8) */}
        <aside className="w-80 border-r border-slate-300 bg-white flex flex-col min-h-0">
          <div className="px-3 py-2 border-b border-slate-200 text-xs font-semibold uppercase tracking-wide text-slate-600">
            POIs
          </div>
          <div className="flex-1 min-h-0">
            <PoiTree
              onSelect={handleTreeSelect}
              onDataLoaded={handleDataLoaded}
              externalByKind={byKind}
            />
          </div>
        </aside>

        {/* Center pane — map view (9P.9) */}
        <main className="flex-1 relative bg-slate-200 min-h-0">
          <AdminMap
            byKind={byKind}
            selectedPoi={selectedPoi}
            onPoiSelect={handleMapSelect}
            onPoiMoved={handlePoiMoved}
          />
        </main>
      </div>

      {/* Edit dialog (9P.10) — opens on marker click */}
      <PoiEditDialog
        open={editOpen}
        poi={selectedPoi?.poi ?? null}
        kind={selectedPoi?.kind ?? null}
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
//
// Three visual states:
//   loading      grey  "Oracle: …"        — first poll in flight
//   ready        green "Oracle: ready"    — LLM up, corpora loaded
//   unavailable  rose  "Oracle: down"     — connection refused / LLM error
//
// Click anywhere on the pill to force a re-poll without waiting for the
// 30s tick. Tooltip explains how to start the testapp when the pill is rose.

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

  // unavailable
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
