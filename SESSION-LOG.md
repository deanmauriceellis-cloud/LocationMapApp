# LocationMapApp — Session Log

> Sessions prior to v1.5.35 archived in `SESSION-LOG-ARCHIVE.md`.

## Session: 2026-03-03c (v1.5.50 — Find Dialog Overhaul)

### Context
Find dialog had three issues: bottom rows clipped (5-row main grid, 8+ row subtype grids overflow screen), 200km distance cap preventing results for rare POIs (zoos, theme parks), and missing useful subtypes. Wrapped grids in ScrollView, removed distance cap, and added 16 new subtypes.

### Changes Made

#### 1. ScrollView Wrapping (MainActivity.kt)
- **Main category grid** (`showFindCategoryGrid`): wrapped `grid` in `ScrollView` with `layoutParams = LayoutParams(MATCH_PARENT, 0, 1f)`; search bar stays fixed above
- **Subtype grid** (`showFindSubtypeGrid`): same ScrollView wrapping pattern
- **Dialog height**: changed from `WRAP_CONTENT` to `(dm.heightPixels * 0.85).toInt()` for both grids
- **Cell height cap**: added `minOf(dp(120), ...)` to prevent oversized cells on small categories

#### 2. Unlimited Distance (server.js)
- Changed scope array from `[50, 200]` to `[50, 200, 1000, 0]`
- When `tryKm === 0`: skips bbox WHERE clause entirely, queries full database ordered by distance
- Verified: zoo search from Beverly MA returned results at 694–1413km (previously 0 results)

#### 3. New Subtypes (PoiCategories.kt + server.js)
- Added `'craft'` to `POI_CATEGORY_KEYS` array in server.js
- 16 new subtypes added to PoiCategories.kt across 6 categories (tags + subtypes lists)
- Total subtypes: 122 → 138

### Test Results
- Build passes, APK installed successfully
- Find main grid: all 18 cells visible, scrolls to Entertainment/Offices row
- Shopping: all 26 subtypes visible with scroll (Pet Stores 6, Electronics 2, Bicycle Shops 3, Garden Centers 9)
- Entertainment: all 18 subtypes visible with scroll (Water Parks, Mini Golf 1, Escape Rooms 1)
- Count badges displaying correctly on all cells
- `/db/pois/find` zoo search: 5 results returned at 694–1413km with `scope_m: 0` (global fallback)

### Files Modified
- `app/.../ui/MainActivity.kt` — ScrollView wrapping + cell height cap (~20 lines changed)
- `app/.../ui/menu/PoiCategories.kt` — 16 new subtypes + tags (~50 lines added)
- `cache-proxy/server.js` — `craft` key + unlimited distance find (~40 lines changed)

---

## Session: 2026-03-03b (v1.5.49 — Automated POI DB Import)

### Context
POI imports from proxy's in-memory cache into PostgreSQL required manually running `node import-pois.js`. Automated this on a 15-minute cycle with delta approach — only imports POIs updated since last successful run. Follows existing `setInterval` timer patterns (aircraft sighting purge, login lockout cleanup).

### Changes Made

#### Automated Delta Import (server.js)
- **State variables**: `lastDbImportTime`, `dbImportRunning` mutex, `dbImportStats` tracking
- **Helper functions**: `derivePoiCategory()`, `extractPoiCoords()` — inlined from `import-pois.js`
- **`runPoiDbImport(manual)`**: filters `poiCache` for entries with `lastSeen >= lastDbImportTime`, upserts in batches of 500, per-batch transactions, timestamp-only-on-success
- **Timer**: `setTimeout` 30s (initial full import), `setInterval` 15min (delta imports), guarded by `if (pgPool)`
- **Endpoints**: `POST /db/import` (manual trigger), `GET /db/import/status` (read-only status)
- **Stats**: `dbImport` object in `/cache/stats` response
- **Startup banner**: shows auto-import enabled/disabled state

### Test Results
- Full import: 178,395 POIs in 217s (~3.6 min)
- Delta import: 1,996 POIs in 9s (only new arrivals)
- `pendingDelta` drops to 0 after import; `totalRuns` counter increments correctly
- DB verified: `SELECT count(*) FROM pois` = 180,059

### Files Modified
- `cache-proxy/server.js` — 185 lines added (import logic, endpoints, stats, banner)

---

## Session: 2026-03-03 (v1.5.48 — Startup/Behavior Tuning)

### Context
Running on BlueStacks (Washington DC) and physical Android device. Three quality-of-life tunings for a better default experience: zoomed-in startup, less opaque radar, and smarter idle populate.

### Changes Made

#### 1. Default Zoom 14 → 18
- **First GPS fix** (`MainActivity.kt` ~line 926): `setZoom(18.0)`
- **Long-press** (~line 577): threshold `< 18.0`, zoom to 18
- **goToLocation** (~line 5833): threshold `< 18.0`, zoom to 18
- **Populate scanning marker** (~line 7607): threshold `< 18.0`, zoom to 18

#### 2. Radar Transparency 70% → 35%
- `radarAlphaPercent` default changed from 70 to 35 (`MainActivity.kt` line 71)
- Applies to both static and animated radar (shared variable)
- User can still adjust via menu slider

#### 3. Idle Auto-Populate POI Density Guard
- **`MainViewModel.fetchNearbyPoiCount()`**: new suspend function, delegates to `FindRepository.fetchCounts()`, returns total POI count or -1 on error
- **`MainActivity.kt` idle trigger** (~line 892): wraps `startIdlePopulate()` in a `lifecycleScope.launch` coroutine that first queries `/db/pois/counts?lat=&lon=&radius=10000`
  - If total ≥ 100: logs skip reason, does not start scanner
  - If total < 100 or query fails (-1): proceeds as normal

### Files Modified
- `app/src/main/java/com/example/locationmapapp/ui/MainActivity.kt` — zoom, radar alpha, idle guard
- `app/src/main/java/com/example/locationmapapp/ui/MainViewModel.kt` — `fetchNearbyPoiCount()`

---

## Session: 2026-03-02 (v1.5.40 — Slim Toolbar + Status Line + Grid Dropdown)

### Context
The two-row icon toolbar (10 white icons in 2×5 grid, v1.5.35) was functional but cryptic — icons had no labels and the redesign had eliminated the status line that showed live tracking info. Banners were dynamic TextViews added/removed from the CoordinatorLayout. User wanted the status area restored and icons converted to a labeled button grid for better discoverability.

### Changes Made

#### New Layout: Slim Toolbar → Status Line → Grid Dropdown → Map
- **Slim toolbar** (40dp): Weather icon (left) | spacer | Alerts icon + Grid menu button (right)
- **Status line** (24dp): persistent priority-based info bar below toolbar
  - Idle: GPS coordinates + speed + weather (e.g., "42.5557, -70.8730 • 61°F")
  - Active: highest-priority state wins — follow info, scan progress, or geofence alerts
  - 7 priority levels: GPS_IDLE(0) → SILENT_FILL(1) → IDLE_POPULATE(2) → POPULATE(3) → AIRCRAFT_FOLLOW(4) → VEHICLE_FOLLOW(5) → GEOFENCE_ALERT(6)
  - Geofence alerts show zone-type-colored background; tap to dismiss + show zone detail
  - Tap on follow/scan entries to stop the active operation
- **Grid dropdown**: PopupWindow triggered by grid button
  - 8 labeled buttons (icon + text) in 2×4 grid
  - Row 1: Transit, Webcams, Aircraft, Radar | Row 2: POI, Utility, Find, Go To
  - Dark semi-transparent background (#E8212121), ripple feedback, auto-dismiss
- Net height: 72dp → 64dp = 8dp saved

#### New Files
- `app/.../ui/StatusLineManager.kt` — priority-based status line manager
- `app/.../res/drawable/ic_grid_menu.xml` — Material 3×3 grid icon
- `app/.../res/layout/grid_dropdown_panel.xml` — PopupWindow content

#### Modified Files
- `toolbar_two_row.xml` — replaced 2×5 grid with slim icon row + status line TextView
- `activity_main.xml` — added `fitsSystemWindows="true"` to AppBarLayout
- `AppBarMenuManager.kt` — removed `setupTwoRowToolbar()`, added `setupSlimToolbar()` + `showGridDropdown()`
- `MainActivity.kt` — replaced `followBanner: TextView?` with `statusLineManager: StatusLineManager`; migrated 6 banner functions to StatusLineManager set/clear calls

---

## Session: 2026-03-02f (Geofence Phase 4 — Database Import & Export — v1.5.39)

### Changes Made
- **Import SQLite .db**: SAF file picker → schema validation → install with duplicate detection
- **Import CSV**: SAF file picker → config dialog (name, zone type, radius) → parsed with column aliases → converted to SQLite
- **Export**: installed databases shareable via FileProvider + Android share intent
- **Local-only databases**: catalog merges locally-imported DBs not in remote catalog; works offline

### Files Changed
- `GeofenceDatabaseRepository.kt` — 6 new methods (validate, import SQLite, import CSV, parse CSV, get file, get local-only DBs)
- `MainViewModel.kt` — import result LiveData, import/export methods, catalog offline fallback
- `MainActivity.kt` — SAF launchers, CSV config dialog, overwrite confirmation, export
- `AndroidManifest.xml` + `file_paths.xml` — FileProvider setup

---

## Session: 2026-03-02e (Geofence Phase 2 — Additional Zone Types — v1.5.36)

### Changes Made
- 4 new zone types (speed cameras, school zones, flood zones, railroad crossings)
- `ZoneType` enum, `GeofenceRepository.kt` (4 fetch methods + `generateCircleShape()`)
- 4 proxy endpoints: `/cameras`, `/schools`, `/flood-zones`, `/crossings`
- Per-type LiveData, `rebuildGeofenceIndex()`, zone-type-aware UI
- Zoom guards: cameras ≥ 10, others ≥ 12

### Files Changed
- Models.kt, GeofenceEngine.kt, server.js, GeofenceRepository.kt (new), AppModule.kt, MainViewModel.kt, MenuEventListener.kt, AppBarMenuManager.kt, menu_alerts.xml, MainActivity.kt, DebugEndpoints.kt

---

## Session: 2026-03-02d (Geofence Alert System — TFR Phase 1 — v1.5.35)

### Changes Made
- JTS R-tree spatial engine (`GeofenceEngine.kt`), FAA TFR scraping via proxy `/tfrs`
- Semi-transparent red TFR polygon overlays, detail dialog on tap
- Entry/proximity/exit detection with cooldowns
- Severity-colored Alerts toolbar icon, alert banner
- Two-row toolbar layout (10 icons in 2×5 grid)
- GPS integration: user position + followed aircraft

### New Files (6)
- TfrRepository.kt, GeofenceEngine.kt, toolbar_two_row.xml, ic_alerts.xml, ic_tfr_zone.xml, menu_alerts.xml

### Files Modified (10)
- build.gradle, Models.kt, MainViewModel.kt, MainActivity.kt, AppBarMenuManager.kt, MenuEventListener.kt, AppModule.kt, DebugEndpoints.kt, activity_main.xml, server.js + package.json

---

## Session: 2026-03-02c (Weather Feature Overhaul — v1.5.34)

### Changes Made
- Proxy `/weather` composite endpoint (5 NWS API calls, per-section TTLs)
- Weather dialog: current conditions, 48-hour hourly strip, 7-day outlook, expandable alerts
- 22 weather condition vector icons, `WeatherIconHelper.kt`
- Dynamic toolbar icon with red border when alerts active
- Auto-fetch on GPS fix + every 30 minutes
- Deleted old Alerts submenu; METAR moved to Radar menu

---

## Session: 2026-03-02b (Idle Auto-Populate + Delta Cache — v1.5.33)

### Changes Made
- Idle auto-populate: 60s GPS stationarity → full scanner, 45s delays, GPS-centered
- X-Client-ID header on all Overpass requests (per-device UUID)
- Proxy delta cache: covering cache check, content hash skip, per-client fair queue (5-cap, round-robin)

---

## Session: 2026-03-02a (Geocode Autocomplete + Tooltips — v1.5.32)

### Changes Made
- Photon geocoder via proxy `/geocode` — prefix matching, US-only, 24h cache
- Auto-suggest TextWatcher with 500ms debounce at >= 3 chars
- Replaced `android.location.Geocoder` (no prefix matching)
- Toolbar tooltips on all 9 items
