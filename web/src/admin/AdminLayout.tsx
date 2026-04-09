// AdminLayout — Phase 9P.B Step 9P.7 (shell), 9P.8 (POI tree), 9P.9 (map)
//
// Shell for the LocationMapApp Salem POI admin tool. Three-pane layout:
//   ┌─────────────────────────────────────────────────────────────────────┐
//   │ Header  [Highlight Duplicates] [Publish]  Oracle: —   [Logout]      │
//   ├──────────────────────┬──────────────────────────────────────────────┤
//   │ POI tree (9P.8 ✓)    │ Map view (9P.9 ✓)                            │
//   │ react-arborist       │ Leaflet + cluster                            │
//   │                      │                                              │
//   └──────────────────────┴──────────────────────────────────────────────┘
//
// Step status:
//   9P.7  ✓ shell (this file)
//   9P.8  ✓ POI tree (PoiTree.tsx, wired into the left pane)
//   9P.9  ✓ Admin map view (AdminMap.tsx, wired into the center pane)
//   9P.10 — POI edit dialog (modal triggered from tree/map)
//   9P.10b — Salem Oracle integration (the "Oracle: —" pill goes live + a
//            "Generate with Salem Oracle" button appears in the edit dialog)
//   9P.11 — Highlight Duplicates wiring
//   9P.13 — Publish wiring
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

import { useCallback, useState } from 'react'
import { PoiTree, type PoiKind, type PoiRow, type PoiSelection } from './PoiTree'
import { AdminMap } from './AdminMap'

export function AdminLayout() {
  // Shared POI dataset — populated by PoiTree's onDataLoaded callback after
  // the initial fetch, then passed down to PoiTree (as externalByKind, so
  // both panes always see identical data) and to AdminMap.
  const [byKind, setByKind] = useState<Record<PoiKind, PoiRow[]> | null>(null)
  const [selectedPoi, setSelectedPoi] = useState<PoiSelection | null>(null)

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

  const handlePoiSelect = useCallback((selection: PoiSelection) => {
    // 9P.9: lift the selection so AdminMap can fly to it. The edit dialog
    // (9P.10) will read this same state to know which POI to populate.
    setSelectedPoi(selection)
  }, [])

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

        {/* Oracle status pill (placeholder — wired up in 9P.10b) */}
        <div
          className="px-2 py-1 text-xs rounded-full bg-slate-700 text-slate-300"
          title="Salem Oracle status — wired up in Phase 9P.10b"
        >
          Oracle: —
        </div>

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
              onSelect={handlePoiSelect}
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
            onPoiSelect={handlePoiSelect}
            onPoiMoved={handlePoiMoved}
          />
        </main>
      </div>
    </div>
  )
}
