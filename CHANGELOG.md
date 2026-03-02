# LocationMapApp — Changelog

> Releases prior to v1.5.30 archived in `CHANGELOG-ARCHIVE.md`.

## [1.5.40] — 2026-03-02

### Added
- **Slim Toolbar + Status Line + Grid Dropdown** — complete toolbar redesign
  - **Slim toolbar** (40dp): Weather icon (left), spacer, Alerts icon + Grid menu button (right)
  - **Status line** (24dp): persistent priority-based info bar below toolbar
    - Idle: GPS coordinates + speed + weather (e.g., "42.5557, -70.8730 • 61°F")
    - Active: follow info, scan progress, or geofence alerts — highest priority wins
    - 7 priority levels: GPS_IDLE (0) → SILENT_FILL (1) → IDLE_POPULATE (2) → POPULATE (3) → AIRCRAFT_FOLLOW (4) → VEHICLE_FOLLOW (5) → GEOFENCE_ALERT (6)
    - Geofence alerts show zone-type-colored background
    - Tap status line to stop follow/scan (context-sensitive click handler)
  - **Grid dropdown**: PopupWindow with 8 labeled buttons (icon + text) in 2×4 grid
    - Row 1: Transit, Webcams, Aircraft, Radar | Row 2: POI, Utility, Find, Go To
    - Dark semi-transparent background, ripple feedback, auto-dismiss on outside tap
  - **`StatusLineManager.kt`** (new): priority-based status manager with set/clear/updateIdle API
  - **`ic_grid_menu.xml`** (new): Material Design 3×3 grid icon
  - **`grid_dropdown_panel.xml`** (new): PopupWindow layout for grid dropdown

### Changed
- **`toolbar_two_row.xml`** — replaced 2×5 icon grid with slim icon row + status line
- **`activity_main.xml`** — added `fitsSystemWindows="true"` to AppBarLayout
- **`AppBarMenuManager.kt`** — `setupSlimToolbar()` + `showGridDropdown()` replace `setupTwoRowToolbar()`; removed 10 ICON_* constants
- **`MainActivity.kt`** — rewired toolbar init; migrated 6 banner functions to StatusLineManager; eliminated `followBanner` field; GPS + weather observers update idle status; `statusLine` added to `debugState()`

### Removed
- `followBanner` field and all dynamic banner `TextView` creation (6 places)
- 10 `ICON_*` constants from `AppBarMenuManager.companion`

## [1.5.39] — 2026-03-02

### Added
- **Geofence Phase 4 — Database Import & Export**
  - **Import SQLite .db files** via Android SAF file picker — validates schema, copies to geofence_databases directory
  - **Import CSV files** via SAF file picker — config dialog, column aliases, converts to SQLite
  - **Export installed databases** as .db files via Android share intent (FileProvider)
  - **Duplicate detection**: importing existing ID shows overwrite confirmation dialog
  - **Local-only database display**: merges locally-imported databases not in remote catalog; works offline

## [1.5.38] — 2026-03-02

### Added
- **Geofence Phase 3B — 3 additional downloadable databases**
  - **Speed & Red-Light Cameras** (`excam-cameras.db`): 109,500 worldwide cameras from WzSabre/ExCam
  - **US Public Schools** (`nces-schools.db`): 101,390 K-12 schools from NCES EDGE ArcGIS
  - **DJI No-Fly Zones** (`dji-nofly.db`): 7,823 drone restriction zones from DJI NFZDB
- Build scripts: `build-excam.js`, `build-nces.js`, `build-dji-nofly.js`
- Catalog now lists 4 databases totaling 220,657 zones

## [1.5.37] — 2026-03-02

### Added
- **Geofence Phase 3A — Downloadable database infrastructure**
  - `GeofenceDatabaseRepository.kt` — catalog fetch, file download with progress, SQLite loading
  - Database Manager dialog — list databases with download/delete, progress bar
  - `build-military.js` — ArcGIS NTAD military base builder (1,944 polygon zones)
  - Proxy endpoints: `GET /geofences/catalog`, `GET /geofences/database/:id/download`
  - 3 new ZoneTypes: MILITARY_BASE, NO_FLY_ZONE, NATIONAL_PARK

## [1.5.36] — 2026-03-02

### Added
- **Geofence Phase 2 — 4 additional zone types**
  - Speed/Red Light Cameras (Overpass, 200m circle, orange)
  - School Zones (Overpass, polygon or 300m circle, amber, weekday time filter)
  - Flood Zones (FEMA NFHL ArcGIS, blue)
  - Railroad Crossings (Overpass, 100m circle, dark+yellow)
- `GeofenceRepository.kt` — consolidated fetching for 4 non-TFR zone types
- 4 proxy endpoints: `/cameras`, `/schools`, `/flood-zones`, `/crossings`
- Zone-type-aware UI and zoom guards

## [1.5.35] — 2026-03-02

### Added
- **TFR Geofence Alert System** — JTS R-tree spatial engine + FAA TFR detection
  - Entry (CRITICAL), proximity (WARNING, 5nm + bearing), exit (INFO) with cooldowns
  - Semi-transparent red TFR polygon overlays, detail dialog on tap
  - Severity-colored Alerts toolbar icon (gray → blue → yellow → red → pulsing)
  - Proxy `/tfrs?bbox=` — scrapes FAA tfr.faa.gov, parses AIXM shapes
  - Two-row toolbar layout (10 icons in 2×5 grid)
  - GPS integration for user + followed aircraft

## [1.5.34] — 2026-03-02

### Added
- **Weather dialog** — rich graphical display via NWS API composite endpoint
  - Current conditions, 48-hour hourly strip, 7-day outlook, expandable alerts
  - 22 weather vector icons, `WeatherIconHelper.kt`, auto-fetch every 30min
  - Proxy `/weather?lat=&lon=` merges 5 NWS calls with per-section TTLs

### Changed
- Toolbar: Alerts → Weather (direct dialog, no submenu)
- METAR toggle moved to Radar menu

### Removed
- `menu_gps_alerts.xml`, 6 dead callbacks, dead pref constants

## [1.5.33] — 2026-03-02

### Added
- **Idle auto-populate** — 60s GPS stationarity triggers full scanner
- **X-Client-ID header** — per-device UUID on all Overpass requests
- **Delta cache** (proxy) — covering cache check, content hash skip, per-client fair queue

## [1.5.32] — 2026-03-02

### Added
- **Geocode autocomplete** — Photon geocoder via proxy `/geocode`, prefix matching, 500ms debounce
- **Toolbar tooltips** on all action views

## [1.5.31] — 2026-03-01

### Added
- **Icon toolbar** — 8 text buttons → icons; long-press tooltips
- **Go to Location** — geocoder dialog with search, tappable results, map navigation

## [1.5.30] — 2026-03-01

### Changed
- **Smart GPS** — dead zone filtering, speed-adaptive polling, 3km POI threshold
