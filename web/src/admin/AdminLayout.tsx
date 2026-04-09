// AdminLayout — Phase 9P.B Step 9P.7 (shell), 9P.8 (POI tree wired in)
//
// Shell for the LocationMapApp Salem POI admin tool. Three-pane layout:
//   ┌─────────────────────────────────────────────────────────────────────┐
//   │ Header  [Highlight Duplicates] [Publish]  Oracle: —   [Logout]      │
//   ├──────────────────────┬──────────────────────────────────────────────┤
//   │ POI tree (9P.8 ✓)    │ Map view (9P.9)                              │
//   │ react-arborist       │ Leaflet                                      │
//   │                      │                                              │
//   └──────────────────────┴──────────────────────────────────────────────┘
//
// Step status:
//   9P.7  ✓ shell (this file)
//   9P.8  ✓ POI tree (PoiTree.tsx, wired into the left pane)
//   9P.9  — Admin map view (center pane)
//   9P.10 — POI edit dialog (modal triggered from tree/map)
//   9P.10b — Salem Oracle integration (the "Oracle: —" pill goes live + a
//            "Generate with Salem Oracle" button appears in the edit dialog)
//   9P.11 — Highlight Duplicates wiring
//   9P.13 — Publish wiring
//
// Auth model: HTTP Basic Auth via the browser's native dialog. The
// cache-proxy admin endpoints (Phase 9P.3 middleware) return 401 with
// WWW-Authenticate: Basic. The first admin API call from this page (the
// tree fetch in 9P.8) triggers the prompt; subsequent requests reuse the
// browser's cached credentials. There is no in-page login form by design.

import { useCallback } from 'react'
import { PoiTree, type PoiSelection } from './PoiTree'

export function AdminLayout() {
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
    // 9P.8: emits selection only — log for now. The map view (9P.9) will
    // pan/zoom to selection.poi, and the edit dialog (9P.10) will open with
    // selection.poi as its initial state.
    console.log('[admin] POI selected:', selection.kind, selection.poi.id, selection.poi.name)
  }, [])

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
            <PoiTree onSelect={handlePoiSelect} />
          </div>
        </aside>

        {/* Center pane — map view (9P.9) */}
        <main className="flex-1 relative bg-slate-200 min-h-0">
          {/* TODO 9P.9 — replace with <AdminMapView /> (Leaflet) */}
          <div className="absolute inset-0 flex items-center justify-center text-slate-500">
            <div className="text-center max-w-md px-4">
              <p className="text-base italic">Admin map view lands in Step 9P.9.</p>
              <p className="mt-2 text-xs">
                Will render all selected POIs as markers, support click-to-edit,
                and host the duplicates highlight overlay (9P.11).
              </p>
            </div>
          </div>
        </main>
      </div>
    </div>
  )
}
