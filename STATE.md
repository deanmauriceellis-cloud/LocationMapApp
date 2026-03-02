# LocationMapApp v1.5 — Project State

## Last Updated: 2026-03-02 Session 38 (Geofence Phase 3B — Additional Database Builders)

## Architecture
- **Android app** (Kotlin, Hilt DI, OkHttp, osmdroid) targeting API 34
- **Cache proxy** (Node.js/Express on port 3000) — transparent caching layer between app and external APIs
- **PostgreSQL** (`locationmapapp` DB) — permanent storage for POIs and aircraft sightings
- **Split**: App → Cache Proxy → External APIs (Overpass, NWS, Aviation Weather, MBTA, OpenSky, Windy Webcams)

## What's Working
- Map display with osmdroid (OpenStreetMap tiles)
- GPS location tracking with manual override (long-press)
- Custom zoom slider (right edge)
- POI search via Overpass — 16 categories with submenu refinement
  - Food & Drink, Fuel & Charging, Transit, Civic & Gov, Parks & Rec, Shopping,
    Healthcare, Education, Lodging, Parking, Finance, Places of Worship,
    Tourism & History, Emergency Svc, Auto Services, Entertainment
  - Categories with subtypes show AlertDialog with multi-choice checkboxes
  - All categories driven from central config (PoiCategories.kt)
- POI markers as small colored category dots (5dp), unified marker tracking per layer
  - **Zoom ≥ 18**: dots switch to labeled icons showing category type above and business name below
- Layer-aware POI LiveData — `Pair<String, List>` ensures categories don't overwrite each other
- Unified POI pipeline — all categories use searchPoisAt() via Overpass
- All layers default ON on fresh install (POIs, MBTA, radar, METAR, webcams) — except aircraft (OFF)
- **Weather dialog** (v1.5.34) — rich graphical weather display via NWS API composite endpoint
  - Toolbar Weather icon shows current conditions; red border when alerts exist
  - Auto-fetches on first GPS fix and every 30 minutes
  - Dialog sections: current conditions (temp, wind, humidity, dewpoint, visibility, barometer),
    expandable severity-colored alerts, 48-hour hourly forecast scroll strip, 7-day daily outlook
  - Proxy `/weather?lat=&lon=` merges 5 NWS API calls with per-section TTLs (points=24h, current=5min, forecast=30min, alerts=5min)
  - 22 weather condition vector icons (day/night variants), `WeatherIconHelper.kt` mapping
  - Replaced old Alerts submenu (4 stubs + 1 useless global alerts + METAR = 8 items → 1 Weather button)
  - METAR toggle + frequency moved to Radar menu
- METAR stations with rich text markers — temp (°F), wind arrow+speed, sky/wx, flight-category color
  - Bbox passthrough to Aviation Weather API, cached per-bbox
  - Deferred load — waits for GPS fix so map has a valid bounding box
  - Tap shows human-readable decoded METAR (compass wind, decoded sky/wx, flight category explained)
  - Handles HTTP 204 (no content) gracefully
- NWS NEXRAD radar tiles (Iowa State Mesonet — replaced RainViewer)
- MBTA live vehicles: buses, commuter rail, subway (with auto-refresh)
  - Directional arrows on vehicle markers showing bearing/heading
- **MBTA train station markers** (v1.5.17) — ~270 subway + commuter rail stations on map
  - Station building icon (26dp, tinted per line); multi-line stations use neutral dark gray
  - Tap station → **arrival board dialog**: real-time predictions with route dots, headsign, "Now/X min/H:MM" times
  - Auto-refreshes every 30s while dialog is open
  - Tap a train row → **trip schedule dialog**: full timetable with stop names, times, track numbers (CR)
  - `routeColor()` / `routeAbbrev()` helpers extract duplicated color logic
  - Toggle in Transit menu ("Train Stations"), defaults ON, persisted with `PREF_MBTA_STATIONS`
  - Fetches via 2 MBTA API calls (subway routes + CR route_type=2), merges by stop ID
  - No interference with vehicle/aircraft follow mode
- **MBTA bus stop markers** (v1.5.19) — ~7,900 bus stops, viewport-filtered client-side
  - Fetched via proxy `/mbta/bus-stops` (24h cache TTL), held in memory (~500KB)
  - Only shown at zoom >= 15; 300ms debounced viewport filter on scroll/zoom
  - Bus stop sign icon (20dp, teal tint), tap → reuses arrival board dialog
  - Toggle in Transit menu ("Bus Stops"), defaults OFF (opt-in), persisted with `PREF_MBTA_BUS_STOPS`
- **Vehicle detail dialog** (v1.5.19) — replaces direct tap-to-follow for all vehicles
  - Tapping bus/train/subway shows info dialog: route, vehicle ID, status, speed, staleness
  - Three buttons: Follow (teal), View Route (trip schedule), Arrivals (arrival board)
  - Route color bar under header; buttons dimmed when trip/stop info unavailable
- Aircraft tracking (OpenSky Network) — live airplane positions
  - Rotated airplane icon pointing to heading, callsign label, vertical rate indicator (↑↓—)
  - SPI emergency flag: thick red circle around marker, warning in tap info
  - Altitude-colored markers: green (<5k ft), blue (5–20k ft), purple (>20k ft), gray (ground)
  - All 18 state vector fields parsed (added: timePosition, lastContact, spi, positionSource)
  - Tap shows: altitude, speed, heading, vertical rate, squawk, origin, category, source (ADS-B/MLAT), position age
  - Auto-refresh at configurable interval (30s–5min, default 60s)
  - Zoom guard: only fetches at zoom ≥ 10 to avoid massive queries
  - Reloads on scroll/zoom with 1s debounce
  - Deferred restore like METAR — waits for GPS fix
  - FAB speed dial toggle + dedicated **Air** top-level menu (toggle, frequency slider, auto-follow)
- **Two-row icon toolbar** (v1.5.35): 10 icon-only buttons in 2 rows of 5, long-press shows tooltip
  - Row 1: Weather, Transit, CAMs, Air, Radar
  - Row 2: POI, Utility, Find, Go to Location, Alerts
  - Programmatic ImageView creation (no more menu inflation); `setupTwoRowToolbar()` in AppBarMenuManager
  - Weather icon dynamically updates to show current conditions; red border when alerts active
  - Alerts icon dynamically colored by severity: gray (none), blue (INFO), yellow (WARNING), red (CRITICAL), pulsing red (EMERGENCY)
- **Go to Location** (v1.5.32): geocode autocomplete dialog — type-ahead suggestions as you type
  - **Photon geocoder** (Komoot OSM) via proxy `/geocode` — prefix matching, US-only (bbox filter)
  - Auto-suggest: `TextWatcher` with 500ms debounce, fires at >= 3 characters
  - Replaced `android.location.Geocoder` (no prefix matching) with Photon (proper autocomplete)
  - Results cached 24h at proxy; `GeocodeSuggestion` data class in Models.kt
  - On result tap: MANUAL mode, animateTo, triggerFullSearch, bbox refresh, scheduleSilentFill
- Aircraft follow mode: tap airplane → map tracks it globally via icao24 query
  - Dedicated icao24 refresh loop (not limited to bbox — tracks anywhere in the world)
  - Banner shows callsign, altitude, speed, heading, vertical rate, SPI flag
  - POI prefetch at aircraft position each refresh cycle
  - 3-strike failure tolerance on position queries (handles 429 rate limits)
  - Auto-stops with toast when aircraft disappears after 3 consecutive failures
  - **Flight path trail** (v1.5.24): altitude-colored polyline drawn on map during follow
    - Loads DB sighting history on follow start (2 points per sighting), grows live each 60s refresh
    - Colors: gray (ground), green (<5k ft), blue (5–20k ft), purple (>20k ft)
    - Skips >30min gaps (separate flights); caps at 1000 points; z-ordered under markers
    - Clears on stop-follow; debug state reports trail point/segment counts
- **Auto-follow aircraft (POI Builder)**: Utility menu toggle for passive POI cache building
  - Picks random aircraft ≥ 10,000 ft from wide bbox (~zoom 8, 6°×8° span), follows via aircraft follow mode
  - Rotates every 20 minutes; prioritizes westbound aircraft (stays over land)
  - Smart switching triggers:
    - **Below 10,000 ft** → picks any new aircraft
    - **0 POIs returned** (over water) → picks furthest-west aircraft
    - **Outside US bounds** (>49°N, <25°N, >-66°W, <-125°W) → picks aircraft closest to US interior center
  - Banner shows "Auto-following ✈" prefix; restores on app restart
- **Webcam layer** (Windy Webcams API) — live camera markers on map
  - 18 webcam categories (matches Windy API v3): traffic, city, village, beach, coast, port, lake, river, mountain, forest, landscape, indoor, airport, building, square, observatory, meteo, sportArea
  - Camera icon markers, tap opens 90% fullscreen dark dialog with preview image, info, and "View Live" button
  - "View Live" loads Windy player in in-app WebView (no browser fork)
  - Viewport reload on scroll AND zoom (500ms debounce, same pattern as POIs)
  - Minimum 0.5° bbox span enforced (Windy API returns 0 for small viewports)
  - Proxy `/webcams` endpoint with 10-min cache TTL, includes `playerUrl` and `detailUrl` fields
  - Deferred restore on app restart, defaults ON with "traffic" pre-selected
- **Automatic cap detection & retry-to-fit** (v1.5.15): all POI searches self-correct in dense areas
  - Overpass `out center 500` cap — when hit, `searchPois()` halves radius and retries in-place
  - Keeps halving until results fit or 100m floor reached — no subdivision queue needed
  - Radius hint halved on cap feedback so future searches at that location start small
  - **20km fuzzy radius hints**: nearest known hint within 20km used as starting radius
    - Eliminates retry chains in known dense metro areas (e.g., Boston downtown → all of metro area)
  - Tested: downtown Boston settles at 250m (5 attempts first time, 1 attempt after hint saved)
- **Populate POIs v2** (grid scanner, v1.5.16): Utility menu for systematic cache building
  - **Three-phase approach**: probe center → calibrate grid → spiral with recursive subdivision
  - Phase 1: probes map center to discover settled radius (retries up to 3× on transient errors)
  - Phase 2: calculates grid step from settled radius (not hardcoded 3000m)
  - Phase 3: spirals outward; each cell has retry-to-fit; dense cells trigger recursive 3×3 subdivision
  - **Recursive 3×3 subdivision**: when a cell settles at smaller radius than grid, fills 8 surrounding points
    - Recurses further if fill points also settle smaller; stops at MIN_RADIUS (100m)
    - Dense pockets get fine coverage; sparse areas stay at 1 search per cell
  - **Narrative banner**: two-line status showing grid radius, current action, POI counts (new vs known)
    - "Searching cell 3/8 at 1500m…", "Dense area! 1500m→750m — filling 8 gaps", "Fill 3/8: 45 POIs (8 new)"
  - Orange crosshair marker shows current scan position
  - Guards: refuses while vehicle/aircraft follow is active; tap banner to stop
  - Stops on user interaction: long-press, vehicle tap, aircraft tap
  - Never auto-restarts on app launch — pref cleared in `onStart()`
  - Webcam reloads suppressed during scanning; POI markers hidden at zoom ≤ 8
  - Proxy `X-Cache` header (HIT/MISS) on `/overpass` responses
  - Proxy cache key includes radius to prevent cache collisions on cap-retry
- Vehicle follow mode: tap a bus/train → map tracks it, banner shows status
  - **Staleness detection**: banner and tap snippet show "STALE (Xm ago)" when vehicle GPS update is >2 min old
- POI prefetch along followed vehicle/aircraft routes
- **Find dialog** (v1.5.26) — POI discovery feature on toolbar
  - Category grid (4×4) → subtype grid → distance-sorted results list → tap to navigate
  - Long-press category/subtype → map filter mode with "Showing: X ✕" banner
  - DB-backed: `/db/pois/counts` (10-min cache) + `/db/pois/find` (Haversine-sorted, 50→200km auto-expand)
  - `FindRepository.kt` with client-side counts cache; `FindResult`/`FindCounts`/`FindResponse` models
  - "All POIs On" button in POI menu for quick recovery after filtering
- **POI Detail Dialog** (v1.5.27) — rich info view when tapping Find results
  - Header: category color dot + compact GPS distance + POI name + close
  - Info rows: Distance, Type (with cuisine/brand detail), Address, Phone (tappable → dialer), Hours
  - "Load Website" button → full-screen in-app WebView (deferred init avoids ANR)
  - Proxy `/pois/website` endpoint: 3-tier waterfall (OSM tags → Wikidata P856 → DuckDuckGo)
  - Resolved URLs cached permanently in DB as `_resolved_website` JSONB tag
  - `duck-duck-scrape` npm dependency for Tier 3 search
  - 4 action buttons: Directions (Google Maps), Call (dialer), Reviews (Yelp in WebView), Map (zoom 18)
  - External intents use `FLAG_ACTIVITY_NO_HISTORY` — auto-killed on return to app
  - Full-screen WebView: back/close bar, pinch-to-zoom, `onRenderProcessGone` crash handler
- **Legend dialog** (v1.5.25) — accessible via Utility → Map Legend
  - 7 sections: GPS, 16 POI categories, METAR/radar, transit vehicles, transit stops, aircraft, webcams
  - POI section driven from `PoiCategories.ALL` — stays in sync automatically
- **Transit zoom guard** (v1.5.25) — all transit markers hidden at zoom ≤ 10, restored on zoom in
- Cached POI coverage display: proxy `/pois/bbox` endpoint returns POIs within visible map area
  - Loads on startup, refreshes on scroll/zoom (500ms debounce), refreshes after follow prefetch
  - Also triggered explicitly after long-press location change (programmatic moves don't fire onScroll)
- Adaptive POI search radius — proxy stores per-grid-cell hints, app fetches/reports, 20km fuzzy matching
- Individual POI cache (poi-cache.json) — deduped by OSM type+id, with first/last seen timestamps
- POI database (PostgreSQL) — permanent storage with JSONB tags, category indexing, upsert import
- Cache proxy with disk persistence (cache-data.json, radius-hints.json, poi-cache.json, 365-day Overpass TTL)
- **Idle auto-populate** (v1.5.33) — full probe-calibrate-spiral scanner starts after 60s GPS stationarity
  - GPS observer jitter branch (<100m) checks idle time; significant-move branch resets timer + cancels scan
  - `startIdlePopulate(center)` / `stopIdlePopulate()`; `idlePopulateJob` + `lastSignificantMoveTime` state vars
  - 45s inter-cell delay (gentler than manual 30s); "Idle scan:" banner prefix
  - Guards: no manual populate, not following, speed ≤20mph; always uses GPS position (never map center)
  - Cancels on: GPS >100m move, long-press, vehicle/aircraft tap, manual populate, banner tap, Go to Location
  - Debug state: `idlePopulate` (boolean), `idleTimeSec`, `populate` (manual scanner)
- **Overpass request queue** (v1.5.16): proxy serializes upstream Overpass requests, 10s minimum gap
  - Cache hits return instantly; cache misses queued and processed one at a time
  - Prevents Overpass 429/504 storms from parallel requests
  - Queue depth visible in `/cache/stats` → `overpassQueue`
  - `X-POI-New` / `X-POI-Known` response headers report new vs existing POIs per request
  - **Per-client fair queuing** (v1.5.33): round-robin across `X-Client-ID` headers, 5-request per-client cap
  - **Covering cache check** (v1.5.33): before Overpass upstream, checks if larger-radius cached result covers area
  - **Content hash delta** (v1.5.33): MD5 of sorted element IDs skips `cacheIndividualPois()` when data unchanged
- **Bbox snapping for cache hit rate** (v1.5.23): coordinates rounded to grid for reuse across small scrolls
  - METAR: 0.01° (~1km), webcams: 0.01° (~1km), aircraft: 0.1° (~11km)
  - South/west snap down, north/east snap up to fully contain original viewport
- **Silent background POI fill** (v1.5.28): automatic single Overpass search on startup/restore/long-press
  - Fires at center position after delay (3-4s), uses adaptive radius with cap-retry
  - Cancels on: new long-press, vehicle/aircraft tap, full populate scanner start
  - `scheduleSilentFill()` with tracked Runnable prevents double-fire
  - Debug banner toggle in Utility menu (default ON): shows fill progress, tap to cancel
  - `silentFill` boolean in debug `/state` endpoint
- **Geofence Alert System** (v1.5.35-36) — multi-zone spatial alerting with 5 zone types
  - **GeofenceEngine** (`util/GeofenceEngine.kt`): JTS R-tree spatial index, point-in-polygon, proximity detection
    - `loadZones()` builds JTS polygons, inserts into STRtree; `checkPosition()` returns `List<GeofenceAlert>`
    - Entry detection (CRITICAL for TFR, WARNING for others), proximity within 5nm + bearing ±60°, exit (INFO)
    - Cooldowns: 5min proximity, 10min entry; configurable proximity threshold
    - `ZoneType` enum: TFR, SPEED_CAMERA, SCHOOL_ZONE, FLOOD_ZONE, RAILROAD_CROSSING
    - Severity mapping per zone type; school-zone time filter (weekdays 7-9 AM, 2-4 PM)
    - `getZoneCountByType()` for per-type counts in debug
  - **5 zone types** with map overlays (all default OFF except TFR):
    - **TFR** (v1.5.35): red fill/outline, FAA TFR data via `tfr.faa.gov` scraping
    - **Speed cameras** (v1.5.36): orange fill/outline, Overpass `highway=speed_camera`, 200m alert radius
    - **School zones** (v1.5.36): amber fill/outline, Overpass `amenity=school`, polygon or 300m circle
    - **Flood zones** (v1.5.36): blue fill/outline (darker for A/V codes), FEMA NFHL ArcGIS Layer 28
    - **Railroad crossings** (v1.5.36): dark fill/yellow outline, Overpass `railway=level_crossing|crossing`, 100m radius
  - **Zone-type-aware UI**: detail dialog adapts color bar + metadata by zone type; alert banner color per type
  - **Proxy endpoints**: `/tfrs`, `/cameras`, `/schools`, `/flood-zones`, `/crossings` (all bbox-based)
  - **GeofenceRepository** (`data/repository/GeofenceRepository.kt`): consolidated fetch for 4 non-TFR zone types
    - `generateCircleShape()` companion helper for point → polygon conversion
  - **Viewport loading**: `scheduleGeofenceReload()` loads all enabled zone types with zoom guards
    - Cameras: zoom ≥ 10; Schools/Flood/Crossings: zoom ≥ 12
  - **GPS integration**: user GPS checks geofence (no altitude); followed aircraft checks with baroAltitude
  - **Alerts menu**: TFR Overlay (ON), Speed Camera/School Zone/Flood Zone/Railroad Crossing toggles (OFF), Alert Sound, Alert Distance
  - Debug: `/geofences`, `/geofences/alerts` endpoints; `geofences` field in `/state` with per-type counts
- **Downloadable Geofence Databases** (v1.5.37-38) — offline SQLite databases for pre-built zone data
  - **Database Manager dialog**: Alerts menu → "Zone Databases", shows catalog with download/delete per database
  - **`GeofenceDatabaseRepository.kt`**: catalog fetch, file download with progress callback, SQLite zone loading
  - **Proxy endpoints**: `GET /geofences/catalog` (enriched with file sizes), `GET /geofences/database/:id/download`
  - **4 databases** (220,657 zones total):
    - `military-bases.db`: 1,944 US military installation polygon boundaries (MILITARY_BASE, severity 2)
    - `excam-cameras.db`: 109,500 worldwide speed/red-light cameras (SPEED_CAMERA, severity 1, 200m radius)
    - `nces-schools.db`: 101,390 US K-12 public schools (SCHOOL_ZONE, severity 1, 300m radius)
    - `dji-nofly.db`: 7,823 DJI drone no-fly zones (NO_FLY_ZONE, severity 2, variable radius)
  - **Build scripts**: `build-military.js` (ArcGIS polygon), `build-excam.js` (XZ/NDJSON), `build-nces.js` (ArcGIS point), `build-dji-nofly.js` (CSV)
  - Auto-updates `catalog.json` with actual zone counts and file sizes after each build
  - `lzma-native` npm dependency for XZ decompression (ExCam cameras)
- **Startup POI fix** (v1.5.16): no per-category Overpass queries on launch, just loads cached bbox
- **Error radius immunity** (v1.5.16): 504/429 errors no longer shrink radius hints (transient, not density)
- Debug logging (TcpLogStreamer disabled — superseded by debug HTTP server `/logs`)
- **Embedded debug HTTP server** (v1.5.18) — programmatic app control via `adb forward` + `curl`
  - Port 8085, raw `ServerSocket` on `Dispatchers.IO`, minimal HTTP/1.0 parser, JSON via Gson
  - 24 endpoints: state, logs, map control, marker listing/search/tap/nearest, vehicles, stations, bus-stops, screenshot, livedata, prefs, toggle, search, refresh, follow, stop-follow, perf, overlays, geofences, geofences/alerts
  - `DebugHttpServer.kt` (singleton accept loop) + `DebugEndpoints.kt` (all handlers)
  - `runOnMain` helper for UI-thread access via `suspendCancellableCoroutine`
  - Marker tap invokes custom `OnMarkerClickListener` via reflection (v1.5.21)
  - `relatedObject` stored on all markers for rich data access (v1.5.21)
  - Automated test suite: `./test-app.sh` (30+ tests, curl + jq)
  - **Overnight test harness**: `./overnight-test.sh` (~1,850 lines), `./morning-transit-test.sh`, `./run-full-test-suite.sh`
  - Lifecycle-aware: endpoints registered in `onResume`, nulled in `onPause`
  - **Double-start guard**: `start()` returns early if job already active; `stop()` cancels job + closes socket
  - **`onDestroy()` cleanup**: MainActivity calls `DebugHttpServer.stop()` to release port on Activity recreation
  - Bind exceptions always logged (no more silent swallowing behind `isActive` check)
  - Usage: `adb forward tcp:8085 tcp:8085 && curl localhost:8085/state | jq .`

## External API Routing
| API | App Endpoint | Proxy Route | TTL |
|-----|-------------|-------------|-----|
| Overpass POI | `http://10.0.0.4:3000/overpass` | POST /overpass | 365 days |
| NWS Alerts | `http://10.0.0.4:3000/nws-alerts` | GET /nws-alerts | 1 hour |
| NWS Weather | `http://10.0.0.4:3000/weather?lat=&lon=` | GET /weather (composite) | 5min–24h per section |
| METAR | `http://10.0.0.4:3000/metar?bbox=...` | GET /metar?bbox=s,w,n,e | 1 hour (per bbox) |
| Radius Hints | `http://10.0.0.4:3000/radius-hint` | GET+POST /radius-hint | persistent |
| POI Cache | `http://10.0.0.4:3000/pois/...` | GET /pois/stats, /pois/export, /pois/bbox, /poi/:type/:id | persistent |
| Aircraft | `http://10.0.0.4:3000/aircraft?...` | GET /aircraft?bbox=s,w,n,e or ?icao24=hex | 15 seconds |
| Webcams | `http://10.0.0.4:3000/webcams?...` | GET /webcams?s=&w=&n=&e=&categories= | 10 minutes |
| DB POI Query | `http://10.0.0.4:3000/db/pois/...` | GET /db/pois/search, /nearby, /stats, /categories, /coverage | live |
| DB POI Find | `http://10.0.0.4:3000/db/pois/...` | GET /db/pois/counts (10-min cache), /db/pois/find | 10 min / live |
| POI Website | `http://10.0.0.4:3000/pois/website` | GET /pois/website?osm_type=&osm_id=&name=&lat=&lon= | permanent (DB) |
| DB POI Lookup | `http://10.0.0.4:3000/db/poi/...` | GET /db/poi/:type/:id | live |
| DB Aircraft | `http://10.0.0.4:3000/db/aircraft/...` | GET /db/aircraft/search, /recent, /stats, /:icao24 | live |
| FAA TFRs | `http://10.0.0.4:3000/tfrs?bbox=...` | GET /tfrs?bbox=s,w,n,e | 5min list / 10min detail |
| Speed Cameras | `http://10.0.0.4:3000/cameras?bbox=...` | GET /cameras?bbox=s,w,n,e | 24 hours |
| Schools | `http://10.0.0.4:3000/schools?bbox=...` | GET /schools?bbox=s,w,n,e | 24 hours |
| Flood Zones | `http://10.0.0.4:3000/flood-zones?bbox=...` | GET /flood-zones?bbox=s,w,n,e | 30 days |
| Railroad Crossings | `http://10.0.0.4:3000/crossings?bbox=...` | GET /crossings?bbox=s,w,n,e | 7 days |
| Geofence Catalog | `http://10.0.0.4:3000/geofences/catalog` | GET /geofences/catalog | live (from catalog.json) |
| Geofence DB Download | `http://10.0.0.4:3000/geofences/database/:id/download` | GET /geofences/database/:id/download | static file |
| Geocode | `http://10.0.0.4:3000/geocode?q=...` | GET /geocode?q=&limit= | 24 hours |
| MBTA Bus Stops | `http://10.0.0.4:3000/mbta/bus-stops` | GET /mbta/bus-stops | 24 hours |
| MBTA Vehicles | direct (api-v3.mbta.com) | not proxied | — |
| MBTA Stations | direct (api-v3.mbta.com/stops) | not proxied | — |
| MBTA Predictions | direct (api-v3.mbta.com/predictions) | not proxied | — |
| MBTA Schedules | direct (api-v3.mbta.com/schedules) | not proxied | — |
| Radar tiles | direct (mesonet.agron.iastate.edu) | not proxied | — |

## Map Interaction Model
- **Single tap**: no action
- **Long press (~2s)**: enter manual mode, center map (auto-zoom to 14 if <14), search POIs at location
- **Scroll/pan**: displays cached POIs for visible area via proxy `/pois/bbox`
- **Tap vehicle marker**: vehicle detail dialog (route, status, speed) with Follow / View Route / Arrivals buttons
- **Tap station marker**: arrival board dialog (real-time predictions), tap train → trip schedule dialog
- **Tap bus stop marker**: arrival board dialog (real-time bus predictions)
- **Tap aircraft marker**: follow mode (map tracks globally via icao24, banner shows flight info)
- **Tap follow/populate banner**: stop following or stop populate scan
- **Tap find filter banner**: exit filter mode, restore normal POI display
- **Find toolbar icon**: category grid → subtype grid → distance-sorted results → tap to open POI detail dialog
- **Go to Location toolbar icon**: geocoder dialog → type address → pick result → map navigates + POI search
- **POI detail dialog**: info rows + Load Website button + action buttons (Directions, Call, Reviews, Map)
- **Find long-press**: filter map to show only that category's POIs
- **Utility → Populate POIs**: systematic grid scanner spirals from map center

## Key Files
- `app/src/main/java/.../ui/MainActivity.kt` — main map activity, all overlays
- `app/src/main/java/.../ui/MainViewModel.kt` — LiveData, data fetching
- `app/src/main/java/.../ui/MarkerIconHelper.kt` — icon/dot rendering with cache
- `app/src/main/java/.../ui/WeatherIconHelper.kt` — NWS icon code → drawable mapping
- `app/src/main/java/.../ui/menu/PoiCategories.kt` — central config for all 16 POI categories
- `app/src/main/java/.../data/repository/PlacesRepository.kt` — Overpass POI search (injects `@ApplicationContext` for X-Client-ID)
- `app/src/main/java/.../data/repository/WeatherRepository.kt` — NWS + METAR
- `app/src/main/java/.../data/repository/AircraftRepository.kt` — OpenSky aircraft
- `app/src/main/java/.../data/repository/MbtaRepository.kt` — MBTA vehicles
- `app/src/main/java/.../data/repository/FindRepository.kt` — Find dialog DB queries
- `app/src/main/java/.../data/repository/WebcamRepository.kt` — Windy webcams
- `app/src/main/java/.../data/repository/TfrRepository.kt` — FAA TFR fetch via proxy
- `app/src/main/java/.../data/repository/GeofenceRepository.kt` — speed cameras, schools, flood zones, crossings fetch
- `app/src/main/java/.../data/repository/GeofenceDatabaseRepository.kt` — downloadable geofence DB catalog, download, SQLite loading
- `app/src/main/java/.../util/GeofenceEngine.kt` — JTS R-tree spatial index + multi-zone geofence alerting
- `cache-proxy/server.js` — Express caching proxy
- `cache-proxy/geofence-databases/catalog.json` — geofence database catalog (4 databases)
- `cache-proxy/geofence-databases/build-military.js` — military base polygon builder (ArcGIS NTAD)
- `cache-proxy/geofence-databases/build-excam.js` — speed/red-light camera builder (WzSabre XZ/NDJSON)
- `cache-proxy/geofence-databases/build-nces.js` — US public school builder (NCES ArcGIS)
- `cache-proxy/geofence-databases/build-dji-nofly.js` — DJI no-fly zone builder (GitHub CSV)
- `cache-proxy/cache-data.json` — persistent cache (gitignored)
- `cache-proxy/radius-hints.json` — adaptive radius hints per grid cell (gitignored)
- `cache-proxy/poi-cache.json` — individual POI cache, deduped by type+id (gitignored)
- `cache-proxy/schema.sql` — PostgreSQL schema for permanent POI storage
- `app/src/main/java/.../util/DebugHttpServer.kt` — embedded HTTP server (port 8085)
- `app/src/main/java/.../util/DebugEndpoints.kt` — all debug endpoint handlers
- `cache-proxy/import-pois.js` — standalone script to import POIs from proxy into PostgreSQL

## PostgreSQL Database
- Database: `locationmapapp`, user: `witchdoctor`
- **`pois` table**: Composite PK `(osm_type, osm_id)`, JSONB tags (GIN index), promoted name/category columns
  - Indexes: category, name (partial), tags (GIN), lat+lon (compound)
  - 39,266 POIs as of 2026-03-01 (re-imported after Hollywood/LA scanning)
- **`aircraft_sightings` table**: Serial PK, tracks each continuous observation as a separate row
  - Columns: icao24, callsign, origin_country, first/last seen, first/last lat/lon/altitude/heading, velocity, vertical_rate, squawk, on_ground
  - 5-minute gap between observations = new sighting row (enables flight history analysis)
  - Indexes: icao24, callsign, first_seen, last_seen, last_lat+lon
  - 501+ sightings as of 2026-03-01, 195 unique aircraft (accumulates in real-time)
  - Real-time: proxy writes to DB on every aircraft API response (cache hits and misses)
- **DB query API** (`/db/*` prefix): 12 endpoints — 8 POI + 4 aircraft
  - `GET /db/pois/search` — name search (ILIKE), category filter, bbox, tag queries, distance sort
  - `GET /db/pois/nearby` — Haversine distance sort with bbox pre-filter
  - `GET /db/poi/:type/:id` — single POI with timestamps
  - `GET /db/pois/stats` — totals, named count, top categories, bounds, time range
  - `GET /db/pois/categories` — category breakdown with key/value split
  - `GET /db/pois/coverage` — geographic grid with configurable resolution
  - `GET /db/pois/counts` — category counts with 10-min server cache (Find dialog)
  - `GET /db/pois/find` — distance-sorted POIs by category, Haversine sort, auto-expand (Find dialog)
  - `GET /db/aircraft/search` — filter by callsign, icao24, country, bbox, time range
  - `GET /db/aircraft/stats` — totals, unique aircraft, top countries/callsigns, altitude distribution
  - `GET /db/aircraft/recent` — most recently seen aircraft, deduplicated by icao24
  - `GET /db/aircraft/:icao24` — full sighting history + flight path for one aircraft
- Import: `DATABASE_URL=postgres://witchdoctor:fuckers123@localhost/locationmapapp node import-pois.js`
- Proxy startup: `DATABASE_URL=... OPENSKY_CLIENT_ID=... OPENSKY_CLIENT_SECRET=... node server.js`
  - Without DATABASE_URL: proxy starts normally, `/db/*` returns 503
  - Without OPENSKY_*: aircraft requests use anonymous access (100 req/day)

## GPS Centering
- Map only auto-centers on **first** GPS fix (`initialCenterDone` flag)
- Subsequent GPS updates move the GPS marker but don't pan the map
- Vehicle follow mode still pans to tracked vehicle

## OpenSky OAuth2
- Registered account: `DeanMauriceEllis`
- Client ID: `deanmauriceellis-api-client`
- Client credentials flow: `OPENSKY_CLIENT_ID` + `OPENSKY_CLIENT_SECRET` env vars
- Token endpoint: `auth.opensky-network.org/.../token` (30-min expiry, auto-refresh with 5-min buffer)
- Authenticated: 4,000 req/day (vs 100 anonymous)

## OpenSky Rate Limiter (v1.5.11)
- **Proxy-level** — all aircraft requests throttled at the single proxy choke point
- Daily quota: 90% of limit (3,600 of 4,000 authenticated, 90 of 100 anonymous)
- Minimum interval: `86400000 / effective_limit` ms between upstream requests (~24s authenticated)
- **Exponential backoff** on 429: 10s → 20s → 40s → 80s → 160s → 300s cap
- **Stale cache fallback**: returns expired cached data when throttled (app doesn't see errors)
- `Retry-After` header sent when no cache available
- Rate stats exposed in `/cache/stats` → `opensky` object (requestsLast24h, remaining, backoff state)
- Resets on successful response after 429 series

## Build Environment
- **Java**: JBR (JetBrains Runtime) 21.0.9 bundled with Android Studio
  - Path: `/home/witchdoctor/AndroidStudio/android-studio/jbr`
  - Build command: `JAVA_HOME=/home/witchdoctor/AndroidStudio/android-studio/jbr ./gradlew assembleDebug`
  - System Java 17 is NOT sufficient — Gradle daemon requires Java 21 (`gradle/gradle-daemon-jvm.properties`)

## POI Marker Memory Management (v1.5.9)
- **Viewport-only display**: POI markers are evicted on every scroll/zoom and replaced with only what the proxy returns for the visible bbox
- `places` observer: `"bbox"` layerId → `replaceAllPoiMarkers()` clears ALL POI markers, adds only viewport results
- Non-bbox results (user searches, category restores) go to proxy cache silently; next bbox refresh picks them up
- Category toggles control *searching* (Overpass queries), not *display* — display is always driven by `/pois/bbox`
- `MarkerIconHelper` icon cache: LRU LinkedHashMap capped at 500 entries (was unbounded HashMap)
- Estimated POI marker count: ~100-400 (viewport) vs ~22,000 (previous accumulated)

## Known Issues
- MBTA API key hardcoded in MbtaRepository.kt (should be in BuildConfig/secrets)
- Windy Webcams API key hardcoded in server.js (free tier)
- 10.0.0.4 proxy IP hardcoded (works on local network only)
- OpenSky state vector: category field (index 17) not always present — guarded with size check
- ~~**test-app.sh ANSI grep**~~ — fixed in v1.5.23, strips ANSI before counting

## Debug HTTP Server (v1.5.18)
| Endpoint | Description |
|---|---|
| `GET /` | List all endpoints |
| `GET /state` | Map center, zoom, bounds, marker counts, follow state |
| `GET /logs?tail=N&filter=X&level=E` | Debug log entries (filtered, tailed) |
| `GET /logs/clear` | Clear log buffer |
| `GET /map?lat=X&lon=Y&zoom=Z` | Set/read map position (animates) |
| `GET /markers?type=X&limit=N` | List markers by type |
| `GET /markers/tap?type=X&index=N` | Trigger marker click handler |
| `GET /markers/nearest?lat=X&lon=Y&type=X` | Find nearest marker(s) |
| `GET /markers/search?q=X&type=X` | Search markers by title/snippet |
| `GET /vehicles?type=X&limit=N&index=N` | Raw vehicle data (headsign, tripId, stopName, etc.) |
| `GET /stations?limit=N&q=X` | Raw station data with name search |
| `GET /bus-stops?limit=N&q=X` | All cached bus stops with name search |
| `GET /screenshot` | PNG of root view |
| `GET /livedata` | All ViewModel LiveData values |
| `GET /prefs` | Dump SharedPreferences |
| `GET /toggle?pref=X&value=true` | Toggle layer pref + fire handler |
| `GET /search?lat=X&lon=Y` | Trigger POI search at point |
| `GET /refresh?layer=X` | Force refresh a layer |
| `GET /follow?type=aircraft&icao=X` | Follow aircraft/vehicle |
| `GET /stop-follow` | Stop following |
| `GET /perf` | Memory, threads, uptime |
| `GET /overlays` | List all map overlays |
| `GET /geofences` | Loaded geofence zones, active zones, proximity threshold |
| `GET /geofences/alerts` | Active geofence alerts with severity/type/distance |

Marker types: `poi`, `stations`, `trains`, `subway`, `buses`, `aircraft`, `webcams`, `metar`, `gps`

## Automated Testing

### Test Scripts (project root)
| Script | Lines | Purpose |
|--------|-------|---------|
| `test-app.sh` | ~650 | Quick endpoint tests (30+ checks, ~1 min) |
| `overnight-test.sh` | ~1,850 | Full overnight harness (6-8 hrs unattended) |
| `morning-transit-test.sh` | ~500 | Deep transit validation (active service hours) |
| `run-full-test-suite.sh` | ~100 | Master chain: overnight → morning |

### Running Tests
```bash
adb forward tcp:8085 tcp:8085        # Required first
./test-app.sh                        # Quick smoke test
./overnight-test.sh --duration 30    # 30-min trial run
./overnight-test.sh                  # Full 8-hour overnight run
./run-full-test-suite.sh --quick     # 30-min overnight + 30-min morning
./run-full-test-suite.sh             # Full suite (8hr + 1hr)
```

### Test Output
```
overnight-runs/YYYY-MM-DD_HHMM/
  events.log          — timestamped PASS/FAIL/WARN/INFO stream
  time-series.csv     — 30-column CSV (memory, markers, cache, OpenSky, errors)
  report.md           — final summary with recommendations
  baseline/           — initial state snapshots + init-timing.json
  snapshots/          — periodic JSON state every 5 min
  screenshots/        — PNG captures every 15 min
  logs/               — error snapshots + final log dump
```

### Full Suite Results (2026-03-01, overnight 2:19AM + morning 7:50AM)

**Overnight (5.5 hrs): 67 PASS, 0 FAIL, 4 WARN**
- Memory: 38MB baseline → 12-20MB steady, peak 62MB (GC spike), **no leak**
- Cache: 184→265 entries, 28% session hit rate
- OpenSky: 183 requests, 3,400 remaining
- 27 screenshots, 69 CSV rows, 64 snapshots
- Transit came online at 5:25 AM — detected automatically (buses 0→171, trains 0→9, subway 0→32)
- Init timing: stations=257/7.3s, bus_stops=6904/5.4s, metar=1/5.2s, webcams=50/6.4s, pois=844/3.1s
- Warnings: bus headsign/stopName 25% (early AM), aircraft altitude null (on ground), METAR 0 overnight

**Morning (1 hr): 36 PASS, 0 FAIL, 2 WARN**
- Vehicle quality: buses 240-270 (80% headsign, 100% tripId), CR 11-16 (100% all), subway 69-77 (100% all)
- Follow endurance: 6/6 checks active over 60s
- API reliability: 45/45 requests succeeded (100%)
- Bus stops: Downtown=46, Cambridge=139, Seaport=78
- Warnings: bus routeName 80% **(fixed v1.5.23)**, bus stop search 'Mass Ave' = 0 **(fixed v1.5.23 — fuzzy search)**

**Monitor (14 snapshots, 30-min intervals, 2:22AM → 8:53AM)**
- Transit ramp: 52 buses (2AM) → 100 (5:23) → 198 (6:23) → 273 (8:53)
- Subway: 0 (3AM) → 9 (5:23) → 54 (6:23) → 75 (8:53)
- POI markers: 3,136 → 4,828 (peak) — cache building during map moves
- Memory: 9-27MB range, no trend upward
- 0 test failures across entire run

## Active Plan: Geofence Alert System (5 Phases)
**See `PLAN.md` for full details.** Current status:
- Phase 1: Core Engine + TFRs — **DONE** (v1.5.35)
- Phase 2: Additional Zone Types — **DONE** (v1.5.36)
- Phase 3A: Downloadable Database Infrastructure — **DONE** (v1.5.37)
- Phase 3B: Additional Database Builders — **DONE** (v1.5.38)
- Phase 4: User-Created Databases + Distribution — **NEXT**
- Phase 5: Advanced Sources

## Other Next Steps
- Monitor cache growth and hit rates over time
- Evaluate proxy → remote deployment for non-local testing
- Automate periodic POI imports (cron or proxy hook)
- Find dialog enhancements: pagination (load more), search within results, recent/favorites
