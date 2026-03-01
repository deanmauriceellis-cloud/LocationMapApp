# LocationMapApp — Changelog

## [1.5.19] — 2026-03-01

### Added
- **MBTA bus stop markers** — ~7,900 bus stops fetched once, viewport-filtered client-side
  - `fetchBusStops()` + `parseBusStops()` in MbtaRepository (route_type=3, page limit 10,000)
  - Zoom guard: only shown at zoom >= 15 to prevent marker flood
  - 300ms debounced reload on scroll/zoom, instant viewport filtering from in-memory list
  - Bus stop sign icon (`ic_bus_stop.xml`, 20dp) with teal tint
  - `busStopIcon()` method in MarkerIconHelper
  - Tap any bus stop → reuses existing arrival board dialog with real-time bus predictions
  - Transit menu: "Bus Stops" checkable toggle, defaults OFF (opt-in)
  - `PREF_MBTA_BUS_STOPS` preference, `onMbtaBusStopsToggled()` callback
  - `mbtaBusStops` LiveData in MainViewModel with fetch/clear
  - Restored from persisted pref in `onStart()`
- **Vehicle detail dialog** — replaces tap-to-follow for all MBTA vehicle types
  - Tapping any bus, train, or subway marker now shows an info dialog first
  - Shows: vehicle type/number, route name, status/current stop, speed, last updated + staleness
  - Color bar under header matches route line color (teal for buses, standard colors for rail/subway)
  - Three action buttons:
    - **Follow** (teal) — starts existing follow mode
    - **View Route** (gray) — opens trip schedule dialog via synthetic MbtaPrediction
    - **Arrivals** (blue) — opens arrival board dialog via synthetic MbtaStop
  - Buttons dimmed when trip/stop info unavailable
  - 85% width, centered, dark theme, wrap-content height
- **Debug endpoint updates** — `busStops` + `busStopsTotal` marker counts in `/state`, `bus_stops` type in `/markers` and `/refresh`

## [1.5.18] — 2026-03-01

### Fixed
- **Debug HTTP server won't start on Activity recreation** — `ServerSocket` bind failed silently on double-start
  - Added `Job` tracking with early-return guard in `start()` when already running
  - Added `stop()` method to cancel coroutine and close socket
  - `onDestroy()` now calls `DebugHttpServer.stop()` to release port 8085
  - Bind exceptions always logged via `Log.e` (was only logged when coroutine `isActive`)

### Database
- Re-imported 6,631 POIs from proxy cache into PostgreSQL (tables were empty after reset)

## [1.5.18] — 2026-02-28

### Added
- **Embedded debug HTTP server** — programmatic app control via `adb forward` + `curl`
  - `DebugHttpServer.kt` — singleton `ServerSocket` accept loop on `Dispatchers.IO`, port 8085
  - `DebugEndpoints.kt` — all endpoint handlers with `runOnMain` helper for UI-thread access
  - Minimal HTTP/1.0 parser, JSON responses via Gson, `Connection: close`
  - Lifecycle-aware: endpoints registered in `onResume`, nulled in `onPause`
- **19 debug endpoints**:
  - `GET /` — list all endpoints
  - `GET /state` — map center, zoom, viewport bounds, marker counts, follow state
  - `GET /logs?tail=N&filter=X&level=E` — debug log entries with filtering
  - `GET /logs/clear` — clear log buffer
  - `GET /map?lat=X&lon=Y&zoom=Z` — set/read map position (animates)
  - `GET /markers?type=X&limit=N` — list markers by type with position/title/snippet
  - `GET /markers/tap?type=X&index=N` — trigger marker click handler via synthetic MotionEvent
  - `GET /markers/nearest?lat=X&lon=Y&type=X` — find nearest marker(s) by distance
  - `GET /markers/search?q=X&type=X` — search markers by title/snippet text
  - `GET /screenshot` — returns PNG of root view
  - `GET /livedata` — current values of all ViewModel LiveData
  - `GET /prefs` — dump SharedPreferences
  - `GET /toggle?pref=X&value=true|false` — toggle any layer preference + fire handler
  - `GET /search?lat=X&lon=Y` — trigger POI search at a point
  - `GET /refresh?layer=X` — force refresh a layer (trains, subway, buses, stations, aircraft, metar, webcams, pois, radar)
  - `GET /follow?type=aircraft&icao=X` — follow an aircraft by ICAO24
  - `GET /follow?type=trains&index=N` — follow a vehicle by marker index
  - `GET /stop-follow` — stop following any vehicle or aircraft
  - `GET /perf` — performance stats (memory, threads, uptime)
  - `GET /overlays` — list all map overlays with types and counts
- **`debugState()`** internal method on MainActivity — returns snapshot of marker counts, map state, follow state
- **`debugMarkers(type)`** — returns serializable marker info for any marker type
- **`debugRawMarkers(type)`** — returns raw Marker objects for tap simulation
- **`debugTapMarker(marker)`** — triggers click via synthetic MotionEvent at projected screen position
- **`debugTogglePref(pref, value)`** — toggles pref and fires the corresponding layer handler
- **`debugRefreshLayer(layer)`** — force refreshes any data layer
- **`debugFollowAircraft(icao)`** / **`debugFollowVehicleByIndex(type, index)`** — programmatic follow
- **`debugStopFollow()`** — stop following

## [1.5.17] — 2026-02-28

### Added
- **MBTA train station markers** — ~270 subway + commuter rail stations displayed on map
  - Station building icon (`ic_train_station.xml`, 26dp), tinted per transit line color
  - Multi-line stations (e.g., Park Street = Red+Green) get neutral dark gray tint
  - Single-line stations get their line color (red, orange, blue, green, purple for CR)
- **Arrival board dialog** — tap any station to see real-time arrivals
  - 90% fullscreen dark dialog (same pattern as webcam preview)
  - Header with station name, subtitle with lines served
  - Column headers: Line | Destination | Arrives
  - Each row: colored route dot, abbreviation (RL/OL/GL-B/CR), headsign, arrival time
  - Arrival time format: "Now", "X min", or "H:MM AM/PM"
  - Auto-refreshes every 30s while dialog is open
  - Empty state: "No upcoming arrivals"
- **Trip schedule dialog** — tap a train row in the arrival board
  - Back button + close button header
  - Route color bar under header
  - Full timetable: colored dot, stop name, 12h time, track number (commuter rail only)
- **MbtaStop, MbtaPrediction, MbtaTripScheduleEntry** data classes in Models.kt
- **3 new API methods** in MbtaRepository.kt:
  - `fetchStations()` — 2 API calls (subway routes + CR route_type=2), merges by stop ID
  - `fetchPredictions(stopId)` — real-time arrivals from `/predictions` endpoint
  - `fetchTripSchedule(tripId)` — full timetable from `/schedules` endpoint
  - Shared `executeGet()` helper extracted from existing fetch logic
- **`stationIcon()`** method in MarkerIconHelper + `"train_station"` in CATEGORY_MAP
- **`routeColor()` / `routeAbbrev()`** helpers in MainActivity — centralized MBTA color logic
- **Transit menu** — "Train Stations" checkable toggle (defaults ON)
- **`PREF_MBTA_STATIONS`** preference constant, wired through AppBarMenuManager
- **`onMbtaStationsToggled()`** in MenuEventListener interface
- **`mbtaStations` LiveData** in MainViewModel with `fetchMbtaStations()`, `clearMbtaStations()`
- **`fetchPredictionsDirectly()` / `fetchTripScheduleDirectly()`** suspend functions for dialog use
- **`onStart()` restore** — stations restored from persisted toggle state like other MBTA layers

## [1.5.16] — 2026-02-28

### Changed
- **Populate POIs v2 — probe-calibrate-subdivide**
  - Phase 1: probes map center to discover correct radius (up to 3 retry attempts on transient errors)
  - Phase 2: calculates grid step from probe's settled radius (was hardcoded 3000m)
  - Phase 3: spirals outward using calibrated grid; each cell has retry-to-fit
  - Beverly downtown: probe settles at 1500m, grid auto-calibrates to ~2.4km spacing
- **Recursive 3×3 subdivision** — when a cell settles at smaller radius than grid, fills 8 surrounding points
  - Recurses if fill points settle even smaller (e.g., 1500m→750m→375m, depth 0→1→2)
  - Stops at MIN_RADIUS (100m); sparse areas unaffected (1 search per cell)
- **Narrative populate banner** — two-line status with real-time diagnostics
  - Line 1: ring, cells, total POIs (new count), grid radius
  - Line 2: narrative action ("Searching cell 3/8 at 1500m", "Fill 5/8: 45 POIs (8 new) at 750m", "Dense area! 1500m→750m — filling 8 gaps")
- **Overpass request queue** — proxy serializes upstream requests with 10s minimum gap
  - Cache hits return instantly; misses queued one-at-a-time
  - Re-checks cache before processing (earlier queued request may have populated it)
  - Eliminates 429/504 storms from parallel requests at startup or populate
- **Startup POI optimization** — removed 16 parallel per-category Overpass queries on launch
  - Now loads cached POIs via single `/pois/bbox` call (display-driven, not search-driven)
- **Error radius immunity** — proxy no longer shrinks radius hints on 504/429 errors
  - Transient API errors (timeouts, rate limits) are not density signals
  - Prevents hint poisoning that caused cascading failures
- **searchPoisForPopulate retry-to-fit** — populate searches now retry in-place on cap, same as regular searches
  - Returns settled radius in `PopulateSearchResult` for grid calibration
- **Proxy `X-POI-New` / `X-POI-Known` headers** — Overpass responses report new vs existing POI counts
  - App accumulates and displays in populate banner

### Removed
- **Old populate subdivision logic** — caller-side 2×2 mini-grid, sub-cell loops, capped counter
  - Replaced by recursive 3×3 subdivision driven by settled radius comparison
- **Per-category startup Overpass queries** — 16 parallel searches replaced by single bbox load

## [1.5.15] — 2026-02-28

### Changed
- **Overpass result cap raised to 500** — `out center 200` → `out center 500`, reduces cap frequency by ~60%
- **Auto-retry on cap** — `searchPois()` halves radius and retries in-place when Overpass returns 500 elements
  - Keeps halving until results fit or 100m floor reached; no external subdivision queue
  - Radius hint saved at settled value so future searches start small
  - Downtown Boston: settles at 250m (5 attempts first time, instant on subsequent)
- **20km fuzzy radius hints** — nearest known hint within 20km used as starting radius (was 1 mile)
  - A single capped search in downtown Boston seeds the entire metro area
  - Eliminates retry chains for nearby searches in known dense areas
- **Proxy `adjustRadiusHint()` capped flag** — when `capped=true`, halves radius (×0.5) instead of confirming
- **MIN_RADIUS lowered to 100m** (was 500m) — app, proxy, and subdivision floor all aligned
- **`postRadiusFeedback()` sends `capped` flag** — proxy can distinguish cap from normal feedback

### Removed
- **Subdivision queue** — CapEvent model, SharedFlow, ViewModel subdivision queue, recursive subdivideCell()
  - Replaced by simpler in-place retry loop inside `searchPois()` itself
  - No UI observers needed (capDetected, subdivisionComplete removed from ViewModel)
  - No cancellation calls needed in longPressHelper/startPopulatePois

### Database
- POI database synced: 70,808 POIs (was 22,494)

## [1.5.14] — 2026-02-28

### Changed
- **Populate scanner: 30s fixed pacing** — replaces adaptive delay (200ms/4s/10s) to avoid Overpass 429s
  - Failed cells are retried in-place instead of advancing
  - Auto-stops after 5 consecutive errors (unchanged)
- **Populate scanner: cap detection and cell subdivision** — prevents silent POI loss in dense areas
  - Overpass `out center 200` silently truncates dense results; now detected via raw element count
  - When raw elements >= 200: halves radius, subdivides cell into mini-grid (2x2 at 1500m), searches each sub-cell
  - Logs warning with raw vs parsed counts; toasts "Dense area — subdividing to Xm radius"
  - Sub-cells that are still capped are logged but accepted (one level of subdivision)
- **Populate scanner: zoom-14 view** — map zooms to scan point at zoom 14 during populate
  - Keeps viewport small so bbox POI query returns fewer markers
  - Old POIs fall off naturally via viewport-only eviction, reducing GC pressure
- **Populate banner: countdown timer + success/fail counts**
  - Shows `✓ok ⚠fail ✂capped Next: 25s` with per-second countdown during 30s wait
  - Shows `(retry 1500m)` during sub-cell searches
- **Populate menu: activation state instead of checkbox**
  - Title changes to "⌖ Populate POIs (active)" when running; no checkable attribute
- **Populate: never auto-restarts** — pref cleared in `onStart()`, user must manually activate
- **Populate stops on user interaction** — long-press, vehicle tap, aircraft tap all cancel scanner
- **Webcam reloads suppressed during populate** — prevents scroll-triggered webcam spam from crosshair animation
- **POI markers hidden at zoom ≤ 8** — bbox query skipped and markers cleared to avoid overwhelming the map
- **Proxy Overpass cache key includes radius** — `overpass:lat:lon:rRADIUS:tags` prevents cache collisions between different-radius queries for the same point
- **`parseOverpassJson` returns raw element count** — `Pair<List<PlaceResult>, Int>` enables cap detection against pre-filter count

### Fixed
- **Populate POIs not appearing on map** — `loadCachedPoisForVisibleArea()` now called after each successful search
- **Webcam reload spam during populate** — crosshair animation triggered scroll listener every 2-3s
- **Overpass 429s during populate** — adaptive delay too aggressive; now 30s fixed pacing
- **Cap detection false negatives** — was checking post-filter named count (195) vs limit (200); now checks raw element count (200)
- **Cap retry returning cached data** — proxy cache key didn't include radius, so smaller-radius retries returned same cached 200-element response

## [1.5.13] — 2026-02-28

### Added
- **Populate POIs** — systematic grid scanner in Utility menu
  - Spirals outward from map center through rings 0→15 ((2R+1)² cells per ring)
  - Step size: 80% of diameter (4800m at default 3000m radius) for ~20% overlap
  - Adaptive delay based on proxy `X-Cache` header: 200ms cache hit, 4s cache miss, 10s on error
  - Orange crosshair scanning marker shows current search position
  - Progress banner: ring, cells searched, POIs found, cache hit rate, error count
  - Tap banner to stop; auto-stops after 5 consecutive errors
  - Guards: refuses to start while vehicle/aircraft follow is active
  - Re-run efficiency: cached cells fly through at 200ms each
  - Coverage: Ring 5 = ~46km², Ring 10 = ~96km², Ring 15 = ~144km²
- **Proxy `X-Cache` header** — `/overpass` responses now include `X-Cache: HIT` or `X-Cache: MISS`
  - Exact cache hit, neighbor cache hit, and upstream response paths all tagged
- `PopulateSearchResult` data class — wraps search results with cache status and grid key
- `searchPoisForPopulate()` in PlacesRepository — reads X-Cache header for populate scanner
- `populateSearchAt()` suspend function in MainViewModel — direct call, not LiveData
- `ic_crosshair.xml` — 24dp orange crosshair VectorDrawable
- "crosshair" entry in MarkerIconHelper CATEGORY_MAP

## [1.5.12] — 2026-02-28

### Added
- **"Air" top-level menu** — dedicated toolbar button for all aircraft controls
  - Aircraft Tracking toggle, Update Frequency slider (30–300s), Auto-Follow (POI Builder)
  - Toolbar now: Alerts | Transit | CAMs | **Air** | Radar | POI | Utility
- **Vehicle staleness detection** — MBTA follow banner and tap snippet show age of GPS data
  - Fresh (≤2 min): no indicator
  - Stale (>2 min): " — STALE (Xm ago)" or " — STALE (Xh Ym ago)"
  - Catches ghost vehicles with frozen GPS positions still reported by MBTA API

### Changed
- Aircraft controls moved out of Alerts menu into dedicated Air menu
- Auto-Follow Aircraft (POI Builder) moved from Utility menu to Air menu

## [1.5.11] — 2026-02-28

### Added
- **OpenSky rate limiter** — proxy-level throttling to stay within API daily quota
  - Rolling 24h request counter with 90% safety margin (3,600 of 4,000 authenticated)
  - Minimum interval between upstream requests (~24s authenticated)
  - Exponential backoff on 429 responses: 10s → 20s → 40s → ... → 300s cap
  - Stale cache fallback: returns expired cached data when throttled (transparent to app)
  - Rate stats exposed in `/cache/stats` → `opensky` object
- **Webcam live player** — "View Live" button in webcam preview dialog
  - Loads Windy embed player in in-app WebView (no browser fork)
  - JavaScript, DOM storage, and auto-play enabled; WebView destroyed on dismiss
- **Webcam player/detail URLs** — proxy now fetches `player,urls` from Windy API
  - Response includes `playerUrl` (embed player) and `detailUrl` (Windy page)

### Changed
- **Webcam preview dialog** redesigned as 90% fullscreen dark panel with X close button
- **Webcam bbox minimum** — enforces 0.5° span to work around Windy API returning 0 for small viewports
- **Webcam reload on zoom** — `scheduleWebcamReload()` now fires on zoom events (was only scroll)
- **Webcam categories** updated to match actual Windy API v3
  - Added: coast, port, river, village, square, observatory, sportArea
  - Removed (dead): outdoor, harbor, animals, island, golf, resort, sportsite
- **Aircraft display defaults OFF** on fresh install (was ON); auto-follow already defaulted OFF
- **`prefDefault()` helper** in AppBarMenuManager for per-pref-key defaults

### Fixed
- **Webcam markers not appearing** at high zoom — Windy API returns 0 for small bboxes, now enforces minimum span
- **Webcam not reloading on zoom** — only `onScroll` called `scheduleWebcamReload()`, now `onZoom` does too
- **OpenSky 429 storm** — app had 5 independent request paths with no backoff; rate limiter blocks all at proxy

## [1.5.10] — 2026-02-28

### Added
- **Webcam layer** — live camera markers using Windy Webcams API (free tier)
  - Camera icon markers on map at webcam positions
  - Tap marker to view 400×224 preview image in dialog with title, categories, status
  - **18 webcam categories** selectable via multi-select dialog: traffic, city, beach, indoor, outdoor, landscape, mountain, lake, harbor, airport, building, meteo, forest, animals, island, golf, resort, sportsite
  - "Traffic" pre-selected by default; Select All / Deselect All button
  - Viewport reload on scroll/zoom (500ms debounce)
  - Deferred restore on app restart (waits for GPS fix like other layers)
  - Defaults ON with "traffic" category
- **Proxy route** `GET /webcams?s=&w=&n=&e=&categories=` — 10-minute cache TTL
  - Upstream: Windy Webcams API v3 (`api.windy.com/webcams/api/v3/webcams`)
  - Response simplified to `[{ id, title, lat, lon, categories, previewUrl, thumbnailUrl, status, lastUpdated }]`
- **WebcamRepository** — new `@Singleton` repository for webcam data fetching

### Changed
- CAMs menu rewritten: "Highway Traffic Cams" stub → functional "Webcams" toggle + "Camera Types..." dialog
- `MenuEventListener`: `onTrafficCamsToggled`/`onCamsMoreRequested` → `onWebcamToggled`/`onWebcamCategoriesChanged`

## [1.5.9] — 2026-02-28

### Changed
- **Viewport-only POI markers** — markers are evicted on every scroll/zoom and replaced with only what's visible
  - `replaceAllPoiMarkers()` clears ALL POI markers from all layers, adds only bbox viewport results
  - Non-bbox search results (user-initiated, category restores) go to proxy cache; next bbox refresh displays them
  - Category toggles now control *searching* (Overpass queries), not *display* — display is always driven by `/pois/bbox`
  - Estimated marker count: ~100-400 (viewport) vs ~22,000 (previous accumulated)
  - Estimated POI RAM: ~1-2 MB vs ~50-100 MB
- **LRU icon cache** — `MarkerIconHelper` cache converted from unbounded `HashMap` to access-order `LinkedHashMap` capped at 500 entries
- Renamed `"all_cached"` layerId to `"bbox"` in ViewModel for clarity

## [1.5.8] — 2026-02-28

### Added
- **PostgreSQL query API** — 6 new `/db/*` endpoints for searching the permanent POI database
  - `/db/pois/search` — name search (ILIKE), category filter, bbox, JSONB tag queries, Haversine distance sort
  - `/db/pois/nearby` — nearby POIs by distance with bbox pre-filter (default 1km/50 results)
  - `/db/poi/:type/:id` — single POI lookup with first_seen/last_seen timestamps
  - `/db/pois/stats` — analytics: totals, named count, top 20 categories, geographic bounds, time range
  - `/db/pois/categories` — full category breakdown with key/value split and counts
  - `/db/pois/coverage` — geographic grid coverage with configurable resolution
  - Graceful degradation: without DATABASE_URL, `/db/*` returns 503; proxy runs normally
- **Aircraft sightings database** — real-time tracking of every aircraft seen
  - New `aircraft_sightings` table: each continuous observation is a separate row
  - Tracks first/last position, altitude, heading, velocity, vertical rate, squawk
  - 5-minute gap between observations = new sighting row (full flight history)
  - Writes on every aircraft API response (cache hits and misses)
  - In-memory active sighting map with 10-minute stale purge
- **OpenSky OAuth2 authentication** — client credentials flow for 4,000 req/day (was 100 anonymous)
  - Auto-refreshing Bearer tokens (30-min expiry, 5-min buffer)
  - `OPENSKY_CLIENT_ID` + `OPENSKY_CLIENT_SECRET` env vars
- **Smart auto-follow** — multiple triggers for switching aircraft during POI building
  - Below 10,000 ft altitude → switch to any new aircraft
  - Zero POIs returned (over water) → switch to furthest-west aircraft
  - Outside US continental bounds → switch to aircraft closest to US interior center (~39°N, -98°W)
- **Compound lat/lon index** on pois table for faster bbox queries

### Changed
- Auto-follow search bbox widened from ~1.5° to ~6°×8° (covers most of the northeast)
- Auto-follow altitude floor lowered from 20,000 ft to 10,000 ft (more candidates at night)
- Aircraft tracking on cache hits: all aircraft in cached responses now tracked in DB too

## [1.5.7] — 2026-02-28

### Added
- **Auto-follow aircraft (POI Builder)** — Utility menu toggle that passively builds POI cache
  - Picks random aircraft at 20,000+ ft, follows it via existing aircraft follow mode
  - Rotates to a new aircraft every 20 minutes
  - Uses wide bbox (~zoom 11 equivalent) to find candidates without changing user's view
  - Prioritizes westbound aircraft (heading 180–360°) to stay over land in New England
  - Auto-picks replacement when followed aircraft enters no-POI zone (2 consecutive empty checks)
  - 3-strike failure tolerance on position queries (handles HTTP 429 rate limits gracefully)
  - Banner shows "Auto-following" prefix to distinguish from manual follow
  - Cancels if aircraft layer turned off (menu or FAB); restores on app restart
- **POI labels at high zoom** — at zoom ≥ 18, POI markers show type and name
  - Category label above dot (e.g. "Cafe", "Park") in bold category color
  - Business/place name below dot in dark gray
  - Both labels on white pill backgrounds for readability
  - Dynamic icon swap when crossing zoom-18 threshold (no data re-fetch needed)
  - New `MarkerIconHelper.labeledDot()` renders composite icon with cached results

### Changed
- Aircraft follow mode tolerates up to 3 consecutive null responses before declaring aircraft lost
- `stopFollowing()` resets failure and empty-POI counters

## [1.5.6] — 2026-02-28

### Added
- **Enhanced aircraft markers** — airplane icon rotated to heading with callsign label and vertical rate indicator
  - ↑ climbing, ↓ descending, — level flight shown next to callsign
  - **SPI emergency flag**: thick red circle around marker, warning in tap info
- **Aircraft follow mode** — tap airplane marker to track it globally
  - Dedicated icao24 query tracks aircraft anywhere (not limited to visible map area)
  - Dark banner shows callsign, altitude, speed, heading, vertical rate
  - Auto-stops with toast when aircraft disappears from feed
  - POI prefetch at aircraft position each refresh cycle
- **New OpenSky fields**: position source (ADS-B/MLAT), position data age, SPI, last contact
- **Cached POI coverage display** — shows all cached POIs within the visible map area
  - Proxy `GET /pois/bbox?s=&w=&n=&e=` endpoint for server-side bbox filtering
  - Loads on startup, refreshes on scroll/zoom (500ms debounce)
  - Refreshes after each follow prefetch so new POIs appear immediately
- **Proxy icao24 support** — `GET /aircraft?icao24=hex` queries OpenSky globally for a single aircraft

### Changed
- Aircraft markers now use rotated airplane icon instead of generic icon with arrow overlay
- POI display on scroll uses proxy bbox endpoint instead of per-grid-cell cache-only Overpass queries
- POI prefetch now triggers on both vehicle and aircraft follow modes

### Removed
- Old scroll-based per-grid-cell cache-only Overpass POI lookup (replaced by /pois/bbox)

## [1.5.5] — 2026-02-28

### Added
- **Aircraft tracking** (OpenSky Network) — live airplane positions on the map
  - Airplane markers with directional heading arrows (`MarkerIconHelper.withArrow`)
  - Color-coded by altitude: green (<5,000 ft), blue (5–20k ft), purple (>20k ft), gray (on ground)
  - Auto-refresh at configurable interval (30s–5min, default 60s)
  - Zoom guard: only queries at zoom ≥ 10 to avoid overwhelming the API
  - Debounced reload on scroll/zoom (1s) so markers update when panning
  - Deferred restore: waits for GPS fix like METAR layer
  - Tap marker shows: altitude, speed (kt/mph), heading, vertical rate, squawk, origin country, aircraft category
  - FAB speed dial toggle + GPS Alerts menu toggle and frequency slider
- **Proxy route** `GET /aircraft?bbox=s,w,n,e` — 15-second TTL cache, upstream OpenSky API
- **Aircraft icon** (`ic_aircraft.xml`) — 24dp airplane silhouette vector drawable

### Fixed
- **Map constantly re-centering** — GPS updates every ~60s would snap the map back to current position
  - Now only auto-centers on the first GPS fix; subsequent updates move the marker only
- **OpenSky parse crash** — state vector category field (index 17) not always present
  - Guarded with array size check to prevent IndexOutOfBoundsException

## [1.5.4] — 2026-02-28

### Added
- **Vehicle direction arrows** — small triangular arrow on top of each train, subway, and bus marker
  - Rotated to match vehicle bearing from MBTA API
  - Same color as the vehicle icon for visual consistency
- **Human-readable METAR tap info** — tapping a METAR station now shows decoded weather
  - Compass wind direction (e.g., "Southwest at 5 kt") instead of degrees
  - Sky conditions decoded (e.g., "Scattered clouds" instead of "SCT")
  - Weather phenomena expanded (e.g., "Light Rain" instead of "-RA")
  - Flight category explained (e.g., "VFR (Visual Flight Rules)")
  - Observation time in local format
  - Raw METAR preserved at bottom for reference

### Fixed
- **METAR not loading on startup** — `loadMetarsForVisibleArea()` fired before map had a valid bounding box
  - Added `pendingMetarRestore` flag, deferred until GPS fix + 1.5s animation settle
- **METAR HTTP 204 crash** — empty body from Aviation Weather API caused parse error
  - Now returns empty list gracefully when body is blank

## [1.5.3] — 2026-02-28

### Added
- **16 POI categories** replacing the original 6 (Earthquakes dropped)
  - Food & Drink, Fuel & Charging, Transit, Civic & Gov, Parks & Rec, Shopping,
    Healthcare, Education, Lodging, Parking, Finance, Places of Worship,
    Tourism & History, Emergency Svc, Auto Services, Entertainment
- **Submenu refinement** — categories with subtypes show AlertDialog with multi-choice checkboxes
  - e.g., "Food & Drink ▸" opens dialog with Restaurants, Fast Food, Cafes, Bars, Pubs, Ice Cream
  - Checking any enables the layer; unchecking all disables it
  - Subtype selections persisted as StringSet prefs
- **Central category config** (`PoiCategories.kt`) — single source of truth for all 16 categories
- **Unified marker tracking** — `poiMarkers` map keyed by layerId replaces 3 separate lists
- **Rich METAR station markers** — text labels on map showing temp (°F), wind arrow+speed, sky/wx
  - Color-coded border by flight category (VFR/MVFR/IFR/LIFR)
  - Tap for full details: temp, dewpoint, wind/gusts, visibility, sky cover, altimeter, SLP, raw METAR
  - New fields parsed: name, wind gusts, sea level pressure, sky cover, weather phenomena

### Changed
- **All layers default ON** — POIs, MBTA (trains/subway/buses), radar, METAR all enabled on fresh install
- **Layer-aware POI LiveData** — `Pair<String, List<PlaceResult>>` prevents categories overwriting each other
- **METAR bbox passthrough** — app sends visible map bounds; proxy passes bbox to Aviation Weather API
  - Fixes HTTP 400 error (API now requires station IDs or bounding box)
  - Proxy caches per-bbox with 1h TTL
- **All POI categories use unified Overpass pipeline** — no more separate `loadGasStations()` path
- **MarkerIconHelper** expanded to ~80 category→color mappings (from 20)
- **PlacesRepository** category extraction now covers: amenity, shop, tourism, leisure, historic, office

### Fixed
- **POI marker overwrite bug** — all 16 categories fired into single LiveData, only last survived
- **METAR API 400 error** — Aviation Weather now requires bbox param, was missing
- **METAR `fltCat` field name** — parser used wrong case (`fltcat`), flight category was always null
- **METAR `visib` string parsing** — API returns `"10+"` as string, now handled correctly

### Removed
- **Earthquake POI code** — USGS feed, EarthquakeEvent LiveData, markers, observer
- **Gas station-specific code** — `searchGasStations()`, `gasStations` LiveData, separate markers
- **FAB buttons**: Earthquakes and Gas Stations (use POI menu instead)

## [1.5.2] — 2026-02-28

### Added
- **POI database** (PostgreSQL) — permanent storage for all cached POIs
  - Table `pois` with composite PK `(osm_type, osm_id)`, JSONB tags with GIN index
  - Promoted `name` and `category` columns for fast filtering
  - `first_seen`/`last_seen` timestamps for discovery tracking
- **Import script** (`cache-proxy/import-pois.js`) — fetches from proxy, batch upserts into PostgreSQL
  - Derives category from first matching tag (amenity/shop/tourism/leisure/historic/office)
  - Idempotent: re-running updates `last_seen` without duplicates
- **Schema file** (`cache-proxy/schema.sql`) — table + indexes, apply with `psql -f`

### Dependencies
- Added `pg` ^8.13.0 to cache-proxy

## [1.5.1] — 2026-02-27

### Added
- **Adaptive POI search radius** — search radius self-tunes per location
  - Proxy stores per-grid-cell radius hints (`radius-hints.json`, persistent)
  - App fetches hint before each search, posts feedback after
  - Error (429/504) → shrink 30%; too few results → grow 30%; healthy → confirm
  - Bounds: 500m min, 15000m max, 3000m default
  - New proxy routes: GET/POST `/radius-hint`, GET `/radius-hints`
  - `/cache/stats` and `/cache/clear` updated to include hints

## [1.5.0] — 2026-02-27

### Added
- **Cache proxy server** (`cache-proxy/`) — Node.js/Express on port 3000
  - Transparent caching for Overpass, USGS, NWS, METAR APIs
  - Disk persistence (`cache-data.json`) survives proxy restarts
  - Cache-only mode (`X-Cache-Only` header) for scroll-based POI display
  - Admin endpoints: GET /cache/stats, POST /cache/clear
- **Vehicle follow mode** — tap a bus/train/subway to track it
  - Map auto-centers on vehicle position each refresh cycle
  - Status banner (route, stop, speed) with tap-to-dismiss
  - POI prefetch along followed vehicle route (fills cache)
- **Cache-only POI display on scroll** — shows cached POIs when panning without hitting external APIs
- **Manual location mode** — long-press (2s) to override GPS and search POIs at any location

### Changed
- **Radar tiles**: RainViewer → NWS NEXRAD via Iowa State Mesonet (fixes 403 errors)
- **POI markers**: 26dp vector icons → 5dp colored dots (category-colored, minimal footprint)
- **Overpass cache TTL**: 365 days (was 30 minutes)
- **Map single tap**: disabled (was inadvertently setting manual location)

### Fixed
- **MBTA vehicle parser**: JsonNull crash on vehicles with null stop/trip relationships
  - ~30 warnings per refresh eliminated, all vehicles now parse cleanly
- **RainViewer 403**: all radar tiles were Forbidden, replaced with working NWS source
