# LocationMapApp — Session Log

## Session: 2026-02-28q (Debug HTTP Server — Embedded in App)

### Context
Testing the app required guessing pixel coordinates for `adb shell input tap`. We needed a way to programmatically interrogate and control the running app from the terminal. Solution: an embedded HTTP server accessed via `adb forward tcp:8085 tcp:8085` + `curl`.

### Changes Made

#### New file: `DebugHttpServer.kt`
- Singleton `object` with `ServerSocket` accept loop on `Dispatchers.IO`
- Port 8085, minimal HTTP/1.0 parser (method, path, query params)
- Routes to `DebugEndpoints`, always `Connection: close`
- URL-decoded query parameter parsing
- `@Volatile var endpoints` — set/cleared by Activity lifecycle

#### New file: `DebugEndpoints.kt`
- Holds `MainActivity` + `MainViewModel` references
- `EndpointResult` data class for responses (status, contentType, body, bodyBytes for PNG)
- `runOnMain` helper: `suspendCancellableCoroutine` + `Handler(Looper.getMainLooper())`
- 19 endpoint handlers:
  - `/` — endpoint listing
  - `/state` — map center, zoom, bounds, marker counts, follow state
  - `/logs` — DebugLogger entries with tail/filter/level params
  - `/logs/clear` — clear buffer
  - `/map` — read or set map position (animates via controller)
  - `/markers` — list markers by type with lat/lon/title/snippet
  - `/markers/tap` — trigger click via `debugTapMarker()` (synthetic MotionEvent)
  - `/markers/nearest` — Haversine distance sort from a point
  - `/markers/search` — text search on title/snippet
  - `/screenshot` — `@Suppress("DEPRECATION")` drawing cache → PNG bytes
  - `/livedata` — all ViewModel LiveData current values
  - `/prefs` — dump SharedPreferences
  - `/toggle` — toggle pref + fire layer handler (reuses handleDebugIntent pattern)
  - `/search` — trigger POI search at lat/lon
  - `/refresh` — force refresh any layer
  - `/follow` — follow aircraft by icao or vehicle by type+index
  - `/stop-follow` — stop following
  - `/perf` — Runtime memory, thread count, uptime
  - `/overlays` — map overlay list with types and counts

#### Modified: `MainActivity.kt`
- Added imports: `DebugEndpoints`, `DebugHttpServer`
- `onCreate()`: `DebugHttpServer.start()` after `TcpLogStreamer.start()`
- `onResume()`: `DebugHttpServer.endpoints = DebugEndpoints(this, viewModel)`
- `onPause()`: `DebugHttpServer.endpoints = null`
- New `internal` accessor methods:
  - `debugMapView()` — returns binding.mapView
  - `debugState()` — snapshot map of center, zoom, bounds, all marker counts, follow IDs, overlay count
  - `debugMarkers(type)` — serializable list of marker info (type/index/lat/lon/title/snippet)
  - `debugRawMarkers(type)` — raw Marker objects for tap
  - `debugTapMarker(marker)` — synthetic MotionEvent at projected screen position → `onSingleTapConfirmed`
  - `debugTogglePref(pref, value)` — sets pref + invokes menuEventListenerImpl handler
  - `debugRefreshLayer(layer)` — dispatches to ViewModel fetch or local load method
  - `debugFollowAircraft(icao)` — finds marker or starts icao24 tracking
  - `debugFollowVehicleByIndex(type, index)` — taps marker at index
  - `debugStopFollow()` — delegates to `stopFollowing()`

### Build Issues & Fixes
1. `onMarkerClickDefault` is `protected` in osmdroid `Marker` — switched to synthetic `MotionEvent` + `onSingleTapConfirmed`
2. Drawing cache deprecation warnings — suppressed with `@Suppress("DEPRECATION")`
3. `runOnMain` catch block was re-calling `block()` — fixed to `resumeWithException(e)`

### Version
- v1.5.18

---

## Session: 2026-02-28p (MBTA Train Station Markers with Arrivals & Schedules)

### Context
The app shows live MBTA vehicle positions but had no station markers. User wants to see all subway (~123) and commuter rail (~150) stations on the map, tap a station to see arriving trains with destinations, and tap a train to see its full schedule. MBTA v3 API supports all of this via `/stops`, `/predictions`, `/schedules`.

### Changes Made

#### Data classes (`Models.kt`)
- Added `MbtaStop(id, name, lat, lon, routeIds)` with `toGeoPoint()`
- Added `MbtaPrediction(id, routeId, routeName, tripId, headsign, arrivalTime, departureTime, directionId, status, vehicleId)`
- Added `MbtaTripScheduleEntry(stopId, stopName, stopSequence, arrivalTime, departureTime, platformCode)`

#### Station icon (`ic_train_station.xml`)
- 24dp vector drawable — building shape with canopy, door, two windows, clock accent at top
- Tinted at runtime per transit line color

#### MBTA API methods (`MbtaRepository.kt`)
- Extracted shared `executeGet(url, label)` helper from existing vehicle fetch logic
- `fetchStations()` — 2 API calls: subway routes (Red,Orange,Blue,Green-B/C/D/E,Mattapan) filtered by location_type=1 + CR route_type=2; merges by stop ID to combine routeIds for multi-line stations
- `fetchPredictions(stopId)` — real-time arrivals from `/predictions?filter[stop]=X&include=trip,route&sort=departure_time`; headsign from included trip, routeName from included route
- `fetchTripSchedule(tripId)` — full timetable from `/schedules?filter[trip]=X&include=stop&sort=stop_sequence`; stopName and platformCode from included stops

#### MarkerIconHelper changes
- Added `"train_station"` to CATEGORY_MAP with dark gray default
- Added `stationIcon(context, tintColor)` — 26dp tinted station icon

#### ViewModel (`MainViewModel.kt`)
- `_mbtaStations` / `mbtaStations` LiveData for station list
- `fetchMbtaStations()` — launches coroutine to call repository
- `clearMbtaStations()` — empties LiveData
- `fetchPredictionsDirectly(stopId)` — suspend, returns directly for dialog use
- `fetchTripScheduleDirectly(tripId)` — suspend, returns directly for dialog use

#### Menu wiring
- `MenuEventListener.kt` — added `onMbtaStationsToggled(enabled: Boolean)`
- `menu_transit.xml` — added checkable "Train Stations" item before national alerts
- `AppBarMenuManager.kt` — added `PREF_MBTA_STATIONS` constant (default ON), toggle handler, syncCheckStates

#### MainActivity — markers, dialogs, glue
- `stationMarkers` list with `addStationMarker()` / `clearStationMarkers()`
- `routeColor(routeId)` — centralized MBTA line color mapping (Red→#C62828, Orange→#E65100, Blue→#1565C0, Green→#2E7D32, CR→#6A1B9A, Silver→#546E7A)
- `routeAbbrev(routeId)` — short labels for arrival board (RL, OL, BL, GL-B, CR, M, SL)
- Multi-line stations (>1 routeId) get neutral dark gray tint; single-line get line color
- Observer on `mbtaStations` LiveData: clear + rebuild markers
- `onStart()` restore block: fetches stations if pref ON and markers empty
- Station tap does NOT interfere with vehicle/aircraft follow mode
- **Arrival board dialog** (`showArrivalBoardDialog`): 90% fullscreen dark, header + subtitle, column headers, prediction rows with colored dot + abbreviation + headsign + arrival time, 30s auto-refresh, tap row → trip schedule
- **Trip schedule dialog** (`showTripScheduleDialog`): back+close header, route color bar, stop list with dot + name + time + track number
- `formatArrivalTime()` — "Now" (≤0 min), "X min" (<60), "H:MM AM/PM" (else)
- `formatScheduleTime()` — 12h AM/PM format
- `onMbtaStationsToggled()` in menuEventListenerImpl

### Files Changed (9 files, 1 new)
1. `app/src/main/res/drawable/ic_train_station.xml` — **NEW** station icon
2. `app/.../data/model/Models.kt` — 3 new data classes
3. `app/.../data/repository/MbtaRepository.kt` — 3 API methods + parsers + shared executeGet
4. `app/.../ui/MarkerIconHelper.kt` — train_station category + stationIcon method
5. `app/.../ui/MainViewModel.kt` — stations LiveData + 3 functions
6. `app/.../ui/menu/MenuEventListener.kt` — onMbtaStationsToggled interface method
7. `app/src/main/res/menu/menu_transit.xml` — Train Stations checkable item
8. `app/.../ui/menu/AppBarMenuManager.kt` — PREF_MBTA_STATIONS + toggle + sync
9. `app/.../ui/MainActivity.kt` — station markers, arrival/schedule dialogs, menu handler

### Build
- `assembleDebug` passes cleanly (only pre-existing deprecation warning)

### Version
- v1.5.17

---

## Session: 2026-02-28o (Populate v2: Probe-Calibrate-Subdivide)

### Context
Testing populate utility on Beverly, MA downtown revealed multiple issues: 16 parallel Overpass queries on startup caused 504 storms, 504 errors poisoned radius hints (shrinking to 100m), populate grid was hardcoded at 3000m spacing regardless of actual density, and dense pockets within the grid left gaps.

### Changes Made

#### Populate v2: Three-phase approach (`MainActivity.kt`)
- Phase 1 (Probe): searches center first to discover settled radius, 3 retry attempts on errors
- Phase 2 (Calibrate): grid step calculated from settled radius, not hardcoded 3000m
- Phase 3 (Spiral): starts ring 1, each cell searches with retry-to-fit
- Removed old caller-side subdivision logic (2x2 mini-grid, sub-cell loops)

#### Recursive 3×3 subdivision (`MainActivity.kt`)
- New `searchCellSubdivisions()` function: when a cell settles smaller than grid radius, searches 8 fill points
- Recurses if fill points settle even smaller (tested: depth 0→1→2, 1500m→750m→375m)
- Tracks fill progress: "Fill 3/8 at 750m (depth 1)"
- Fixed bug: probe was unnecessarily subdividing (compared vs DEFAULT_RADIUS instead of grid radius)

#### searchPoisForPopulate retry-to-fit (`PlacesRepository.kt`)
- Added same cap-detection retry loop as `searchPois()` — halves radius on cap, retries in-place
- Returns settled radius and accumulated new/known POI counts in `PopulateSearchResult`
- `PopulateSearchResult` model extended with `poiNew` and `poiKnown` fields

#### Overpass request queue (`server.js`)
- All upstream Overpass cache misses serialized through a FIFO queue
- 10-second minimum interval between upstream requests (OVERPASS_MIN_INTERVAL_MS)
- Re-checks cache before processing (earlier queued request may have populated the same key)
- Queue depth exposed in `/cache/stats` → `overpassQueue`

#### Error radius immunity (`server.js`)
- `adjustRadiusHint()` now returns early on errors without changing the radius
- 504/429 timeouts are transient infrastructure problems, not density signals
- Prevents hint poisoning cascade (was: error→shrink→100m→all future searches use 100m)

#### Startup POI optimization (`MainActivity.kt`)
- Removed: loop over 16 `PoiCategories.ALL` firing `searchPoisAt()` for each enabled category
- Replaced with: single deferred `loadCachedPoisForVisibleArea()` (bbox display)
- Startup went from ~16 parallel Overpass queries to 0

#### Narrative populate banner (`MainActivity.kt`)
- Two-line banner with real-time diagnostics
- Shows: ring, cells, POIs (new count), grid radius, current action narrative
- Actions: "Probing center…", "Searching cell 3/8 at 1500m…", "Dense area! 1500m→750m — filling 8 gaps", "Fill 3/8: 45 POIs (8 new) at 750m"

#### Proxy POI count headers (`server.js`)
- `cacheIndividualPois()` now returns `{ added, updated }` counts
- Overpass responses include `X-POI-New` and `X-POI-Known` headers
- App reads headers in `searchPoisForPopulate` and accumulates in PopulateStats

### Testing
- Beverly, MA downtown: probe settled at 1500m, grid calibrated correctly
- Recursive subdivision fired at depths 0→1→2 (1500m→750m→375m) in dense pockets
- 1,741 POIs found in ~20 searches, zero 504 errors with throttle active
- Clean startup: only METAR + webcam cache hits, zero Overpass queries

### Memory note
- **NEVER attempt sudo or postgres-owned database commands** — must tell user to run manually

## Session: 2026-02-28n (Auto Cap Detection & Retry-to-Fit)

### Context
The Overpass API `out center 200` silently truncates results in dense areas. Only the populate scanner detected this. Regular `searchPois()` discarded the raw count, so GPS startup, long-press, category toggles, and follow-mode prefetches all silently lost POIs in dense areas. The 1-mile fuzzy radius hint range was too small — a search 2km from a known dense area would start at the default 3000m and retry through the full chain.

### Changes Made

#### Phase 1: Subdivision queue (later replaced)
- Added `CapEvent` model, `SharedFlow<CapEvent>` in PlacesRepository, recursive `subdivideCell()` in MainViewModel
- This was over-engineered — replaced in Phase 2

#### Phase 2: Retry-to-fit (`PlacesRepository.kt`)
- `searchPois()` now retries in-place when capped: halves radius, re-queries same center
- Loop continues until results fit under 500-element cap or 100m floor reached
- `postRadiusFeedback()` sends `capped: Boolean` to proxy for aggressive hint shrinking
- Removed: CapEvent model, SharedFlow, subdivision queue, ViewModel cap collection, Activity observers

#### Overpass cap raised to 500 (`PlacesRepository.kt`)
- `out center 200` → `out center 500` — reduces cap frequency significantly
- `OVERPASS_RESULT_LIMIT` constant updated to 500

#### MIN_RADIUS lowered to 100m (`PlacesRepository.kt`, `MainViewModel.kt`, `server.js`)
- Was 500m → now 100m in app and proxy
- Subdivision floor also 100m

#### 20km fuzzy radius hints (`PlacesRepository.kt`, `server.js`)
- Fuzzy hint search range expanded from 1 mile (~0.01449°) to 20km (~0.1798°)
- Proxy logs distance in km instead of miles
- Effect: one capped search in downtown Boston seeds hints for entire metro area

#### Proxy capped radius halving (`server.js`)
- `adjustRadiusHint(lat, lon, resultCount, error, capped)` — new 5th parameter
- When `capped=true`: halves radius (×0.5) instead of confirming
- POST `/radius-hint` reads `capped` from request body

#### Database sync
- Imported 70,808 POIs from proxy cache into PostgreSQL (was 22,494)

### Testing
- Downtown Boston (42.358, -71.058): 500 cap at all radii down to 375m, clears at 250m (290 elements)
- Beverly, MA (42.558, -70.880): 6/9 grid cells clear at 3000m, 3 cap (Salem/Danvers density)
- 20km fuzzy: Back Bay (2km) → 188m, Cambridge (5km) → 188m, Quincy (13km) → 188m, Plymouth (60km) → 3000m default

### Files Modified
- `app/.../data/model/Models.kt` — CapEvent added then removed (clean)
- `app/.../data/repository/PlacesRepository.kt` — retry loop, postRadiusFeedback capped flag, 20km fuzzy, MIN_RADIUS=100, OVERPASS_RESULT_LIMIT=500
- `app/.../ui/MainViewModel.kt` — subdivision queue added then removed (clean)
- `app/.../ui/MainActivity.kt` — cap observers added then removed (clean)
- `cache-proxy/server.js` — adjustRadiusHint capped param, 20km fuzzy, MIN_RADIUS=100

### Version
v1.5.15 — committed as two commits (cap detection + retry, then raised cap/min radius)

---

## Session: 2026-02-28m (Populate POIs — Hardening)

### Context
Populate scanner from v1.5.13 had several issues discovered during testing:
- Overpass 429s from aggressive adaptive delay (200ms/4s)
- Webcam reload spam triggered by crosshair animation moving the map
- POIs not appearing on map during scan (no bbox refresh after search)
- Overpass `out center 200` silently truncating dense areas (cap detection checked post-filter count)
- Cap retries hitting same cached response (proxy cache key didn't include radius)
- GC pressure from rendering thousands of POI markers at wide zoom

### Changes Made

#### Populate scanner pacing (`MainActivity.kt`)
- Replaced adaptive delay (200ms hit / 4s miss / 10s error) with **30s fixed pacing**
- Failed cells **retry in-place** instead of advancing to next cell
- Added `loadCachedPoisForVisibleArea()` after each successful search so POIs appear immediately

#### Populate scanner cap detection (`PlacesRepository.kt`, `Models.kt`, `MainActivity.kt`)
- `parseOverpassJson` now returns `Pair<List<PlaceResult>, Int>` — results + raw element count
- Cap detection checks **raw element count >= 200** (not post-filter named count)
- `PopulateSearchResult` gains `radiusM` and `capped` fields
- `searchPoisForPopulate()` accepts optional `radiusOverride` parameter
- When capped: subdivides cell into **mini-grid** (2x2 at half radius) instead of retrying center point
- Sub-grid step recomputed from halved radius: `0.8 * 2 * subRadius / 111320`

#### Populate banner improvements (`MainActivity.kt`)
- Shows success/fail/capped counts: `✓ok ⚠fail ✂capped`
- Per-second countdown timer: `Next: 25s`
- Shows `(retry 1500m)` during sub-cell searches

#### Populate UX changes (`MainActivity.kt`, `AppBarMenuManager.kt`, `menu_utility.xml`)
- Menu item no longer checkable — title changes to "⌖ Populate POIs (active)" when running
- Pref cleared in `onStart()` — never auto-restarts on app launch
- Stops on user interaction: long-press on map, vehicle marker tap, aircraft marker tap
- Map zooms to **zoom 14** at each scan point — smaller viewport = fewer bbox POIs = less GC pressure

#### Scroll event suppression (`MainActivity.kt`)
- Webcam reloads suppressed during populate (crosshair animation was triggering every 2-3s)
- Bbox POI reloads kept active so user sees new POIs appear

#### POI zoom guard (`MainActivity.kt`)
- `loadCachedPoisForVisibleArea()` skips loading at zoom ≤ 8 and clears existing markers
- Prevents overwhelming map with thousands of POI dots at wide zoom

#### Proxy cache key fix (`cache-proxy/server.js`)
- Overpass cache key now includes radius: `overpass:lat:lon:rRADIUS:tags`
- Prevents cap-retry with smaller radius from returning same cached 200-element response

## Session: 2026-02-28l (Populate POIs — Grid Scanner)

### Context
POI coverage was built passively (manual long-press, auto-follow aircraft). No way to systematically fill a geographic area. Implemented a "Populate" utility that takes the current map center and spirals outward through a square grid, searching every cell for POIs.

### Changes Made

#### Proxy: X-Cache header (`cache-proxy/server.js`)
- Added `X-Cache: HIT` on cache hit path, neighbor cache hit path
- Added `X-Cache: MISS` on upstream response
- Enables app-side adaptive delay (fast on cache hits, slow on upstream calls)

#### Data model (`Models.kt`)
- New `PopulateSearchResult(results, cacheHit, gridKey)` data class

#### Repository (`PlacesRepository.kt`)
- New `searchPoisForPopulate()` — reuses existing Overpass query building/parsing, reads X-Cache header
- Returns `PopulateSearchResult` with cache status for adaptive timing

#### ViewModel (`MainViewModel.kt`)
- New `populateSearchAt()` — direct suspend function, not LiveData-based

#### Scanning marker (`ic_crosshair.xml`)
- New 24dp VectorDrawable: orange crosshair with center dot and cross lines

#### Menu + wiring
- `menu_utility.xml` — checkable "Populate POIs" item after divider, before Debug Log
- `MenuEventListener.kt` — `onPopulatePoisToggled(enabled: Boolean)` callback
- `AppBarMenuManager.kt` — `PREF_POPULATE_POIS` constant (defaults OFF), toggle wiring, sync
- `MarkerIconHelper.kt` — "crosshair" category entry with orange tint

#### MainActivity — Core populate logic
- State: `populateJob`, `scanningMarker`
- `startPopulatePois()` — guards against active follow, computes step from latitude, launches spiral coroutine
- `stopPopulatePois()` — cancels job, removes marker, hides banner, resets pref, triggers bbox refresh
- `generateRingPoints()` — square spiral perimeter for ring N (8N points, ring 0 = center)
- Adaptive delay: 200ms on cache HIT, 4000ms on MISS, 10000ms on error
- Auto-stop after 5 consecutive errors
- Progress banner reuses `followBanner` — ring, cells, POIs, hit rate, errors; tap to stop

### Status
- **BUILD SUCCESSFUL** — compiles clean
- Version: v1.5.13

### Files Created
- `app/src/main/res/drawable/ic_crosshair.xml`

### Files Changed
- `cache-proxy/server.js` — X-Cache header on /overpass
- `app/.../data/model/Models.kt` — PopulateSearchResult
- `app/.../data/repository/PlacesRepository.kt` — searchPoisForPopulate()
- `app/.../ui/MainViewModel.kt` — populateSearchAt()
- `app/.../ui/MarkerIconHelper.kt` — crosshair category
- `app/.../ui/menu/MenuEventListener.kt` — onPopulatePoisToggled()
- `app/.../ui/menu/AppBarMenuManager.kt` — PREF_POPULATE_POIS, wiring
- `app/src/main/res/menu/menu_utility.xml` — Populate POIs menu item
- `app/.../ui/MainActivity.kt` — populate logic (183 lines added)

---

## Session: 2026-02-28k (Aircraft "Air" Menu, Vehicle Staleness Detection)

### Context
Aircraft controls were buried in the Alerts menu alongside weather/METAR items. Auto-follow was in Utility. Needed a dedicated top-level menu. Also discovered MBTA trains can report stale GPS positions (hours old) while still appearing in the API — needed staleness detection in the follow banner and tap info.

### Changes Made

#### Dedicated "Air" Top-Level Menu (`menu_aircraft.xml`, `menu_main_toolbar.xml`, `AppBarMenuManager.kt`)
- New `menu_aircraft.xml` with: Aircraft Tracking toggle, Update Frequency slider, Auto-Follow (POI Builder)
- New 7th toolbar button "Air" between CAMs and Radar
- `showAircraftMenu()` method in AppBarMenuManager with full toggle/slider/sync logic
- Removed aircraft items from `menu_gps_alerts.xml` (was alongside weather/METAR)
- Removed Auto-Follow Aircraft from `menu_utility.xml` (moved to Air menu)
- Toolbar now: `Alerts | Transit | CAMs | Air | Radar | POI | Utility`

#### Vehicle Staleness Detection (`MainActivity.kt`)
- New `vehicleStalenessTag(isoTimestamp)` — parses ISO-8601 timestamp, returns "" if fresh (≤2 min) or " — STALE (Xm ago)" / " — STALE (Xh Ym ago)"
- Follow banner shows staleness on first line: "Following Train 1704 — Newburyport Line — STALE (5h 12m ago)"
- Tap snippet (`buildTrainSnippet`) also shows staleness after the update timestamp
- Discovered: MBTA API returns ghost vehicles with hours-old GPS data (e.g., train 1704 on shuttle-replaced weekend service)

### MBTA Investigation
- User reported missing trains near Beverly, MA
- Root cause: Newburyport/Rockport line under weekend shuttle bus replacement (MBTA alert active Feb 28–Mar 1)
- MBTA `/vehicles` API only reports vehicles actively broadcasting GPS — schedule shows service but no real-time vehicles
- Train 1704 was a stale ghost entry (GPS frozen at 11:12 AM, API still reporting at 4 PM with 45 mph speed)

### Status
- **BUILD SUCCESSFUL** — compiles clean
- **Installed on emulator** — Air menu verified working, 7 toolbar buttons visible
- **Not yet committed**

### Files Created
- `app/src/main/res/menu/menu_aircraft.xml`

### Files Changed
- `app/src/main/res/menu/menu_main_toolbar.xml` — added Air button
- `app/src/main/res/menu/menu_gps_alerts.xml` — removed aircraft items
- `app/src/main/res/menu/menu_utility.xml` — removed auto-follow aircraft item
- `app/.../ui/menu/AppBarMenuManager.kt` — showAircraftMenu(), removed aircraft from GPS Alerts and Utility handlers
- `app/.../ui/MainActivity.kt` — vehicleStalenessTag(), staleness in follow banner and tap snippet

---

## Session: 2026-02-28j (OpenSky Rate Limiter, Webcam Enhancements, Testing Fixes)

### Context
OpenSky API was being hammered with requests — 5 independent app-side request paths (periodic refresh, scroll debounce, followed aircraft, auto-follow wide bbox, POI prefetch) with zero backoff at either proxy or app level. 429 responses cascaded into more requests. Also tested webcam layer for the first time, found and fixed several issues.

### Changes Made

#### OpenSky Rate Limiter (`cache-proxy/server.js`)
- **Proxy-level rate limiter** — single throttle point for all aircraft requests
- Rolling 24h request counter, 90% safety margin (3,600 of 4,000 authenticated limit)
- Minimum interval between upstream requests (~24s authenticated, ~960s anonymous)
- **Exponential backoff** on 429 responses: 10s → 20s → 40s → 80s → 160s → 300s cap
- **Stale cache fallback**: when throttled, returns expired cached data (app doesn't see errors)
- `Retry-After` header when no cache available
- Backoff resets on successful response
- `openskyCanRequest()`, `openskyRecordRequest()`, `openskyRecord429()`, `openskyRecordSuccess()`
- Rate state exposed in `/cache/stats` → `opensky` object

#### Webcam Live Player (`MainActivity.kt`, `Models.kt`, `WebcamRepository.kt`, `server.js`)
- Proxy now fetches `include=images,location,categories,player,urls` from Windy API
- Response includes `playerUrl` (Windy embed player) and `detailUrl` (Windy webcam page)
- `Webcam` data class: added `playerUrl`, `detailUrl` fields
- `WebcamRepository`: parses new fields with null safety
- **Preview dialog redesigned**: 90% fullscreen dark panel with title + X close button
- **"View Live" button**: tapping swaps preview image for in-app WebView loading Windy player
- WebView: JavaScript enabled, DOM storage, no user gesture required for media
- WebView destroyed on dialog dismiss to free resources

#### Webcam Bbox Minimum (`MainActivity.kt`)
- Windy API returns 0 results for small bboxes (discovered during testing)
- `loadWebcamsForVisibleArea()` enforces minimum 0.5° span in both lat and lon
- Ensures webcams appear even at high zoom levels

#### Webcam Zoom Reload (`MainActivity.kt`)
- `scheduleWebcamReload()` was only called from `onScroll`, not `onZoom`
- Added call in `onZoom` handler — webcams now reload on zoom changes too

#### Webcam Categories (`AppBarMenuManager.kt`)
- Updated to match actual Windy API v3 categories (was 18 with 5 dead ones)
- **Added**: coast, port, river, village, square, observatory, sportArea
- **Removed** (don't exist in Windy API): outdoor, harbor, animals, island, golf, resort, sportsite
- `sportArea` label formatted as "Sport Area" via camelCase splitting regex

#### Aircraft + Auto-Follow Default OFF (`MainActivity.kt`, `AppBarMenuManager.kt`)
- Aircraft display defaults **OFF** on fresh install (was ON)
- Auto-follow aircraft already defaulted OFF
- New `prefDefault(prefKey)` helper in AppBarMenuManager — returns correct default per pref key
- `syncCheckStates()` and `toggleBinary()` use `prefDefault()` instead of hardcoded `true`

#### Logging Enhancement (`cache-proxy/server.js`)
- `log()` function accepts optional `extra` parameter for contextual suffixes
- Rate limiter events logged with context: `[stale (backoff 19s)]`, `[throttled (min interval)]`, `[upstream 429]`

### Test Results
- **Rate limiter**: verified — only 2 upstream requests in 60s when app was hammering (was 10-20+)
- **Exponential backoff**: confirmed escalation 10s → 20s → 40s → 80s on consecutive 429s
- **Webcam markers**: appear on map after bbox fix (4-9 visible in Massachusetts)
- **Webcam preview dialog**: 90% fullscreen, image loads, "View Live" button present
- **Webcam live player**: WebView loads Windy player in-app (no Chrome fork)
- **Vehicle follow POI prefetch**: confirmed working via logcat (57K chars of POI data returned)

### Status
- **BUILD SUCCESSFUL** — compiles clean
- **Committed & pushed**: `43e7ff6` on `master`
- **Proxy running** with rate limiter + OAuth2

### Files Changed
- `cache-proxy/server.js` — rate limiter, log enhancement, webcam player+urls fields, rate stats in /cache/stats
- `app/.../data/model/Models.kt` — Webcam: added playerUrl, detailUrl
- `app/.../data/repository/WebcamRepository.kt` — parse playerUrl, detailUrl
- `app/.../ui/MainActivity.kt` — 90% webcam dialog, WebView live player, bbox minimum, zoom reload, aircraft default OFF
- `app/.../ui/menu/AppBarMenuManager.kt` — updated webcam categories, prefDefault(), aircraft default OFF

---

## Session: 2026-02-28i (Webcam Layer — Windy Webcams API Integration)

### Context
Adding a webcam layer to the map using the Windy Webcams API (free tier). The CAMs menu button was already stubbed — wired it to real functionality with multi-select webcam categories and camera preview on tap.

### Changes Made

#### Proxy Route (`cache-proxy/server.js`)
- `GET /webcams?s=&w=&n=&e=&categories=` — proxies Windy Webcams API v3
- API key: `x-windy-api-key` header (free tier)
- Upstream URL: `api.windy.com/webcams/api/v3/webcams?bbox=...&category=...&limit=50&include=images,location,categories`
- 10-minute TTL cache (matches image URL expiry on free tier)
- Response transformed to simplified JSON array: `[{ id, title, lat, lon, categories, previewUrl, thumbnailUrl, status, lastUpdated }]`
- Startup log updated to include `/webcams` route

#### Data Model (`Models.kt`)
- New `Webcam` data class: id (Long), title, lat, lon, categories (List<String>), previewUrl, thumbnailUrl, status, lastUpdated, `toGeoPoint()`

#### Repository (`WebcamRepository.kt` — new file)
- `@Singleton` with `@Inject constructor()`, OkHttp client (15s/30s timeouts)
- `fetchWebcams(south, west, north, east, categories)` — hits proxy `/webcams`, parses JSON array

#### DI + ViewModel (`AppModule.kt`, `MainViewModel.kt`)
- `provideWebcamRepository()` added to AppModule
- `WebcamRepository` injected into MainViewModel
- `_webcams` / `webcams` LiveData, `loadWebcams()`, `clearWebcams()`

#### Menu System (`menu_cams.xml`, `MenuEventListener.kt`, `AppBarMenuManager.kt`)
- `menu_cams.xml` replaced: "Webcams" checkable toggle + "Camera Types..." action
- `MenuEventListener`: replaced `onTrafficCamsToggled` + `onCamsMoreRequested` with `onWebcamToggled(enabled)` + `onWebcamCategoriesChanged(categories: Set<String>)`
- `AppBarMenuManager.showCamsMenu()` rewired for new menu items
- `showWebcamCategoryDialog()` — AlertDialog with all 18 Windy categories as multi-select checkboxes
  - "traffic" pre-selected by default
  - "Select All / Deselect All" neutral button
  - Stored as `StringSet` pref `"webcam_categories"`
- Pref keys: `PREF_WEBCAMS_ON`, `PREF_WEBCAM_CATEGORIES` (replaces old `PREF_TRAFFIC_CAMS`)

#### MainActivity — Markers & Tap Dialog
- `webcamMarkers` list, `webcamReloadJob`, `pendingWebcamRestore` state variables
- Observer: clears + adds markers on LiveData update
- `addWebcamMarker()`: camera icon (20dp, existing "camera" category mapping), tap opens preview dialog
- `showWebcamPreviewDialog()`: AlertDialog with ImageView + info text, async OkHttp image download in coroutine
- `loadWebcamsForVisibleArea()`: gets bbox from map, reads active categories from prefs
- `scheduleWebcamReload()`: 500ms debounce on scroll (same pattern as POI bbox)
- Deferred restore in `onStart()` via `pendingWebcamRestore` — fires after GPS fix + 2s
- Toggle off: cancels pending loads, clears LiveData + markers
- Category change: reloads if webcams enabled, clears if empty category set

### Status
- **BUILD SUCCESSFUL** — compiles clean (only pre-existing deprecation warning)
- **APK installed on emulator** — ready for testing
- **Proxy restarted** with webcams route — verified: returns real webcam data for Massachusetts bbox
- **Committed & pushed**: `4cce176` on `master` → `github.com/deanmauriceellis-cloud/LocationMapApp`
- **GitHub auth configured**: `gh auth login` + `gh auth setup-git` (credential helper persists)
- **Not yet tested on device** — needs manual testing of markers, scroll reload, tap dialog, category dialog

### Files Created
- `app/.../data/repository/WebcamRepository.kt`

### Files Changed
- `cache-proxy/server.js` — `/webcams` route, startup log
- `app/.../data/model/Models.kt` — `Webcam` data class
- `app/.../di/AppModule.kt` — `provideWebcamRepository()`
- `app/.../ui/MainViewModel.kt` — webcam LiveData + methods
- `app/src/main/res/menu/menu_cams.xml` — replaced with Webcams toggle + Camera Types
- `app/.../ui/menu/MenuEventListener.kt` — new webcam callbacks
- `app/.../ui/menu/AppBarMenuManager.kt` — showCamsMenu(), showWebcamCategoryDialog(), new pref keys
- `app/.../ui/MainActivity.kt` — webcam markers, observer, scroll reload, tap dialog, deferred restore

---

## Session: 2026-02-28h (Viewport-Only POI Markers with Eviction, LRU Icon Cache)

### Context
Emulator OOM after ~3 hours with all layers active + 22K POIs. POI Marker objects accumulated across all 16 category layers + the `all_cached` layer and were never evicted. The proxy already has a `GET /pois/bbox` endpoint that returns POIs within the visible bounding box, and `loadCachedPoisForVisibleArea()` fires on every scroll/zoom with 500ms debounce. Used this as the recovery mechanism — evict everything off-screen and let the bbox fetch re-materialize markers when the user scrolls back.

### Changes Made

#### Viewport-Only POI Display (`MainActivity.kt`, `MainViewModel.kt`)
- **Places observer refactored**: two-path handler based on layerId
  - `layerId == "bbox"` (from viewport bbox fetch): calls `replaceAllPoiMarkers()` — clears ALL POI markers from every layer, adds only visible results under single `"bbox"` key
  - Any other layerId (from `searchPoisAt`): skips marker creation, schedules bbox refresh after 1s delay so newly cached data appears
- **New `replaceAllPoiMarkers(places)`**: clears `poiMarkers` map entirely, removes all POI markers from overlays, adds only viewport results
- **New `clearAllPoiMarkers()`**: helper to remove all POI markers from all layers at once
- **`onPoiLayerToggled()` simplified**: toggle-off no longer calls `clearPoiMarkers(layerId)` — markers are viewport-driven, category toggles only control searching
- **Renamed layerId**: `"all_cached"` → `"bbox"` in `loadCachedPoisForBbox()` for clarity

#### LRU Icon Cache (`MarkerIconHelper.kt`)
- Converted `cache` from `HashMap<String, BitmapDrawable>` to access-order `LinkedHashMap` with `removeEldestEntry()` override
- Capped at 500 entries — evicts least-recently-used when exceeded
- Prevents `labeledDot()` cache from growing unbounded with unique POI names (was 22K+ entries)

### Memory Impact
| Metric | Before | After |
|--------|--------|-------|
| POI Marker objects | ~22,000 (all categories accumulated) | ~100-400 (viewport only) |
| Icon cache entries | unbounded (22K+ labeled dots) | capped at 500 (LRU) |
| Estimated POI RAM | ~50-100 MB | ~1-2 MB |

### Build Environment Note
- Gradle requires Java 21 (`gradle/gradle-daemon-jvm.properties`)
- System Java 17 is NOT sufficient
- Must use JBR (JetBrains Runtime) 21.0.9 bundled with Android Studio:
  `JAVA_HOME=/home/witchdoctor/AndroidStudio/android-studio/jbr ./gradlew assembleDebug`

### Status
- **BUILD SUCCESSFUL** — compiles clean with 2 warnings (deprecated `setBuiltInZoomControls`, always-true condition)
- **Not yet tested on emulator** — needs extended run to verify OOM fix

### Files Changed
- `app/.../ui/MainActivity.kt` — observer refactored, `replaceAllPoiMarkers()`, `clearAllPoiMarkers()`, simplified `onPoiLayerToggled()`
- `app/.../ui/MainViewModel.kt` — renamed `"all_cached"` → `"bbox"`
- `app/.../ui/MarkerIconHelper.kt` — LRU cache cap at 500 entries

---

## Session: 2026-02-28g (PostgreSQL Query API, Aircraft Sightings DB, OpenSky OAuth2, Smart Auto-Follow)

### Context
POI cache had grown to 8,198 POIs (7,797 in PostgreSQL). The `pg` dependency was installed but unused — all endpoints used in-memory JSON. Added DB-backed query endpoints, real-time aircraft sighting tracking, OpenSky OAuth2 authentication, and smarter auto-follow logic.

### Changes Made

#### PostgreSQL POI Query API (`cache-proxy/server.js`, `cache-proxy/schema.sql`)
- Added `pg` Pool init with `DATABASE_URL` env var (max 5 connections, 5s timeout)
- `requirePg` middleware: `/db/*` routes return 503 if no DATABASE_URL
- Added compound index `idx_pois_lat_lon ON pois (lat, lon)` for bbox queries
- **6 new `/db/*` endpoints** (all parameterized SQL, Haversine distance):
  - `GET /db/pois/search` — combined filtered search (q, category, category_like, bbox, lat/lon, radius, tag, tag_value, limit, offset)
  - `GET /db/pois/nearby` — nearby POIs sorted by distance with bbox pre-filter
  - `GET /db/poi/:type/:id` — single POI lookup with first_seen/last_seen
  - `GET /db/pois/stats` — 5 parallel queries: total, named, top categories, bounds, time range
  - `GET /db/pois/categories` — GROUP BY with key/value split
  - `GET /db/pois/coverage` — rounded lat/lon grid with configurable resolution
- Response format matches Overpass JSON (`{ count, elements: [{ type, id, lat, lon, tags }] }`)

#### Aircraft Sightings Database (`cache-proxy/server.js`, `cache-proxy/schema.sql`)
- New `aircraft_sightings` table: serial PK, icao24, callsign, origin_country, first/last seen+lat+lon+altitude+heading, velocity, vertical_rate, squawk, on_ground
- Each continuous observation = separate row; 5-min gap = new sighting (enables flight history)
- In-memory `activeSightings` map tracks which DB row to update
- `trackAircraftSightings()` called on every aircraft response (cache hits AND misses)
- Stale sighting purge every 10 minutes
- Indexes: icao24, callsign, first_seen, last_seen, last_lat+lon
- Results after ~8 hours: 28,690 sightings, 8,337 unique aircraft, 9,342 unique callsigns

#### OpenSky OAuth2 Authentication (`cache-proxy/server.js`)
- Replaced basic auth with OAuth2 client credentials flow
- Token endpoint: `auth.opensky-network.org/.../openid-connect/token`
- `getOpenskyToken()`: caches token, auto-refreshes 5 min before expiry (30-min tokens)
- `OPENSKY_CLIENT_ID` + `OPENSKY_CLIENT_SECRET` env vars
- Graceful degradation: no credentials = anonymous (100 req/day)
- Authenticated: 4,000 req/day

#### Smart Auto-Follow Improvements (`MainActivity.kt`)
- **Wider search bbox**: 1.5°×2° → 6°×8° (covers most of the northeast/CONUS)
- **Lower altitude floor**: 20,000 ft → 10,000 ft (more candidates at night)
- **Altitude switch**: below 10,000 ft → picks any new aircraft
- **Over water switch**: 0 POIs → `pickFurthestWestAircraft()` (most inland candidate)
- **US boundary check**: lat >49°, <25°, lon >-66°, <-125° → `pickInteriorAircraft()` (closest to geographic center of US ~39°N, -98°W)
- `pickAndFollowRandomAircraft(westboundOnly)` parameter for forced westbound selection
- `selectAndFollow(candidates, westboundOnly)` enforces westbound-only when flag set

### Test Results
- All 6 `/db/*` endpoints tested with curl — search, nearby, stats, categories, coverage, single lookup all working
- Existing endpoints (`/pois/bbox`, `/pois/stats`, `/cache/stats`) unchanged
- OpenSky OAuth2: token refresh working, HTTP 200 on authenticated requests
- Aircraft DB: INSERT + UPDATE paths verified, positions updating in real-time
- Auto-follow ran for ~8 hours: POI cache grew from 8,198 → 22,494; aircraft sightings collected 28,690 rows
- Emulator OOM after ~3 hours with all layers active (memory pressure kill, not crash)

### Files Changed
- `cache-proxy/server.js` — PG pool, 6 `/db/*` endpoints, aircraft sighting tracker, OpenSky OAuth2, startup log
- `cache-proxy/schema.sql` — lat/lon compound index, aircraft_sightings table + indexes
- `app/.../ui/MainActivity.kt` — smart auto-follow (altitude check, furthest-west, interior US, wider bbox, lower altitude floor)

---

## Session: 2026-02-28f (Auto-Follow Aircraft POI Builder, Labeled POI Markers)

### Context
User wants to passively build the POI cache by automatically following random high-altitude aircraft. Also wants POI markers to show category type and business name at high zoom levels.

### Changes Made

#### Auto-Follow Aircraft — POI Builder (`menu_utility.xml`, `AppBarMenuManager.kt`, `MenuEventListener.kt`, `MainActivity.kt`)
- New checkable Utility menu item "Auto-Follow Aircraft (POI Builder)"
- `PREF_AUTO_FOLLOW_AIRCRAFT` constant (defaults false/off)
- `onAutoFollowAircraftToggled()` callback wired through MenuEventListener
- `startAutoFollowAircraft()` — ensures aircraft layer on, picks immediately, starts 20-min rotation job
- `stopAutoFollowAircraft()` — cancels job, stops follow, toasts
- `pickAndFollowRandomAircraft()` — computes zoom-11-equivalent bbox centered on map (1.5° × 2°) without changing user's zoom, queries aircraft, filters ≥ 20,000 ft altitude
- `selectAndFollow()` — prioritizes westbound aircraft (track 180–360°) since this is New England east coast, excludes currently followed icao24 for variety
- `filterHighAltitude()` — filters by `baroAltitude * 3.28084 >= 20000`
- Banner prefix: "Auto-following ✈" when auto-follow active, "Following ✈" for manual
- Edge cases:
  - Aircraft lost from feed → if auto-follow active, immediately picks replacement
  - No-POI zone: after 2 consecutive empty POI prefetches, switches to new aircraft
  - Aircraft layer toggled off (menu or FAB) → cancels auto-follow, clears pref
  - `onStart()` restore: deferred 5s after GPS fix so aircraft data has loaded
- **3-strike failure tolerance**: `followedAircraftFailCount` tracks consecutive null responses from icao24 query; only declares "lost" after 3 failures (handles HTTP 429 rate limits)

#### Labeled POI Markers at Zoom 18+ (`MarkerIconHelper.kt`, `MainActivity.kt`)
- New `MarkerIconHelper.labeledDot()` — composite icon: category label → dot → name label
  - Category humanized ("fast_food" → "Fast Food"), bold, colored to match category
  - Name in dark gray below dot
  - White pill backgrounds for readability, cached by color|type|name
- `addPoiMarker()` checks `zoomLevelDouble >= 18.0` — uses `labeledDot` or `dot`
- `PlaceResult` stored on `marker.relatedObject` for icon refresh without re-query
- `refreshPoiMarkerIcons()` swaps all POI marker icons when crossing zoom threshold
- `poiLabelsShowing` flag tracked in `onZoom` handler — triggers refresh on threshold crossing

### Test Results
- Auto-follow: toggled on, queried wide bbox (20 aircraft), filtered to 1 at 35,000 ft (FIN16), followed correctly
- Pref persists across restart, auto-restores with deferred timing
- POI labels: verified at zoom 18 — "Nature Reserve" / "Sarah Doublet Forest", "Park" / "Bumblebee Park", "Place Of Worship" / "Abundant Life Assembly Church"
- Labels disappear when zooming below 18 (back to dots)
- OpenSky 429 rate limit observed — 3-strike tolerance prevents premature aircraft loss

## Session: 2026-02-28e (Enhanced Aircraft Markers, Aircraft Follow, POI Coverage Display)

### Context
Aircraft markers were basic (small icon with arrow). Needed: rotated airplane pointing to heading, callsign labels, vertical rate indicators, SPI emergency rings, aircraft follow mode, and cached POI coverage display for database building.

### Changes Made

#### Enhanced Aircraft Markers (`MarkerIconHelper.kt`)
- New `aircraftMarker()` method replaces `withArrow()` for aircraft
- Airplane icon **rotated to heading** — the plane itself points where it's flying
- **Callsign text label** above icon with white pill background
- **Vertical rate indicator**: ↑ climbing, ↓ descending, — level (next to callsign)
- **SPI emergency ring**: thick red circle around marker when Special Purpose Indicator active

#### New OpenSky Fields (`Models.kt`, `AircraftRepository.kt`)
- Added `timePosition`, `lastContact`, `spi`, `positionSource` to `AircraftState`
- Parses all 18 state vector fields (indices 3, 4, 15, 16 added)
- Tap info shows: position source (ADS-B/MLAT/ASTERIX/FLARM), data age, SPI warning

#### Aircraft Follow Mode (`MainActivity.kt`, `MainViewModel.kt`)
- Tap aircraft marker to follow — map centers, dark banner shows flight info
- **Global tracking via icao24 query** — not limited to visible bbox
  - Proxy: `/aircraft?icao24=hex` route (no bbox needed, queries OpenSky globally)
  - Dedicated `followedAircraftRefreshJob` polls at aircraft refresh interval
  - `followedAircraft` LiveData in ViewModel for icao24 query results
- Banner: callsign, altitude, speed, heading, vertical rate, SPI flag
- Tap banner to stop; auto-stops when aircraft disappears from feed
- Starting vehicle follow cancels aircraft follow and vice versa
- Toggling aircraft layer off cancels follow

#### POI Prefetch on Aircraft Follow
- Each aircraft follow refresh fires `searchPoisAt()` at the aircraft's position
- Same pattern as existing MBTA vehicle follow POI prefetch
- Fills proxy cache + poi-cache.json as the plane flies over new territory

#### Cached POI Coverage Display
- **Proxy**: new `GET /pois/bbox?s=...&w=...&n=...&e=...` endpoint
  - Returns all cached POIs within bounding box from poi-cache.json
  - Server-side filtering — app only receives visible subset
- **App**: `loadCachedPoisForVisibleArea()` calls bbox endpoint
  - Fires on startup (deferred after GPS fix)
  - Fires on scroll/zoom (500ms debounce)
  - Fires 3s after each follow prefetch (aircraft or vehicle)
- Replaces old per-grid-cell cache-only Overpass queries
- No in-memory cache of all POIs — proxy handles filtering

#### PostgreSQL Import
- 7797 POIs imported (up from 1334)
- DB user `witchdoctor` created with password auth

### Status
- **Builds clean** — BUILD SUCCESSFUL
- **Tested on emulator** — aircraft follow working, POI prefetch populating cache along flight paths
- **7797 POIs in PostgreSQL** after import

### Files Created
- None (all changes to existing files)

### Files Changed
- `app/.../data/model/Models.kt` — 4 new AircraftState fields
- `app/.../data/repository/AircraftRepository.kt` — parse new fields + fetchAircraftByIcao()
- `app/.../data/repository/PlacesRepository.kt` — fetchCachedPoisInBbox()
- `app/.../ui/MarkerIconHelper.kt` — aircraftMarker() method
- `app/.../ui/MainActivity.kt` — aircraft follow mode, cached POI bbox display, scroll handler
- `app/.../ui/MainViewModel.kt` — followedAircraft LiveData, loadCachedPoisForBbox()
- `cache-proxy/server.js` — /aircraft?icao24= support, /pois/bbox endpoint

---

## Session: 2026-02-28d (OpenSky Aircraft Tracking, GPS Center Fix)

### Context
Adding live aircraft positions to the map using the OpenSky Network API. Aircraft displayed as airplane markers with directional arrows showing heading, color-coded by altitude. Also fixed a long-standing issue where GPS updates constantly re-centered the map.

### Changes Made

#### Aircraft Tracking — Full Stack
- **Proxy** (`cache-proxy/server.js`): Added `GET /aircraft?bbox=s,w,n,e` route
  - Upstream: `opensky-network.org/api/states/all?lamin=...`
  - 15-second TTL cache per bbox
- **Model** (`Models.kt`): `AircraftState` data class — icao24, callsign, origin, lat/lon, altitude (baro+geo), velocity, track, vertical rate, squawk, category
- **Repository** (`AircraftRepository.kt`): new `@Singleton`, parses OpenSky state vectors (mixed-type JSON arrays)
  - Guards index 17 (category) with `s.size() > 17` — not always present
  - Filters null lat/lon entries
- **ViewModel** (`MainViewModel.kt`): `_aircraft`/`aircraft` LiveData, `loadAircraft()`, `clearAircraft()`
  - Injected `AircraftRepository` via Hilt
- **Menu** (`menu_gps_alerts.xml`): Aircraft Tracking toggle + frequency slider
  - `MenuEventListener.kt`: `onAircraftDisplayToggled()`, `onAircraftFrequencyChanged()`
  - `AppBarMenuManager.kt`: `PREF_AIRCRAFT_DISPLAY`, `PREF_AIRCRAFT_FREQ`, slider range 30–300s
- **Drawable** (`ic_aircraft.xml`): 24dp airplane silhouette vector icon
- **MarkerIconHelper.kt**: Added `aircraft` category entry
- **MainActivity.kt**: Full integration
  - `aircraftMarkers` list, `aircraftRefreshJob`, `aircraftRefreshIntervalSec` (default 60s)
  - `addAircraftMarker()`: altitude-colored (green/blue/purple/gray), `withArrow()` for heading
  - `buildAircraftSnippet()`: altitude ft, speed kt+mph, heading, vertical rate fpm, squawk, origin, category name
  - `startAircraftRefresh()`/`stopAircraftRefresh()`: coroutine loop at configurable interval
  - `loadAircraftForVisibleArea()`: zoom ≥ 10 guard
  - `scheduleAircraftReload()`: 1s debounced reload on scroll/zoom (via MapListener)
  - `pendingAircraftRestore`: deferred load after GPS fix + 1.5s settle
  - `toggleAircraftFromFab()`: FAB quick-toggle mirrors menu logic
  - Menu callbacks: `onAircraftDisplayToggled`, `onAircraftFrequencyChanged`

#### GPS Center Fix
- Map was re-centering on every GPS update (~60s), preventing the user from panning away
- Added `initialCenterDone` flag — map only auto-centers on the **first** GPS fix
- Subsequent GPS updates still move the GPS marker but don't pan the map

#### Defaults
- Aircraft tracking defaults **ON** (all layers default ON)
- Frequency slider: 30s–5min range, default 60s

### Bug Fixes During Testing
- **IndexOutOfBoundsException**: OpenSky state vectors sometimes have 17 elements (indices 0–16), category at index 17 missing. Guarded with `s.size() > 17`.
- **Stuck bbox**: Aircraft refresh loop wasn't picking up user's zoom/scroll changes. Fixed by adding `scheduleAircraftReload()` in MapListener's `onScroll`/`onZoom`.

### Status
- **Builds clean** — BUILD SUCCESSFUL
- **Tested on emulator** — 26 aircraft visible near Boston at zoom 10-11
- **Next**: enhance markers to show rotated airplane icon with callsign + altitude text labels

### Files Created
- `app/.../data/repository/AircraftRepository.kt`
- `app/src/main/res/drawable/ic_aircraft.xml`

### Files Changed
- `cache-proxy/server.js` — /aircraft route
- `app/.../data/model/Models.kt` — AircraftState
- `app/.../ui/MainViewModel.kt` — aircraft LiveData + loadAircraft
- `app/src/main/res/menu/menu_gps_alerts.xml` — aircraft menu items
- `app/.../ui/menu/MenuEventListener.kt` — aircraft callbacks
- `app/.../ui/menu/AppBarMenuManager.kt` — aircraft prefs + handling
- `app/.../ui/MarkerIconHelper.kt` — aircraft category
- `app/.../ui/MainActivity.kt` — aircraft markers, refresh, restore, FAB, GPS center fix

---

## Session: 2026-02-28c (METAR deferred load, human-readable snippets, vehicle direction arrows)

### Context
METAR stations were not appearing on startup because `loadMetarsForVisibleArea()` fired during `onStart()` before the map had a valid bounding box (returned `0,0,0,0`). METAR tap info was compact/abbreviated. MBTA vehicle markers had no indication of travel direction.

### Changes Made

#### METAR Deferred Load
- Added `pendingMetarRestore` flag alongside existing `pendingPoiRestore`
- `onStart()` now defers METAR load instead of calling `loadMetarsForVisibleArea()` immediately
- METAR fires after GPS fix + `postDelayed(1500ms)` to let `animateTo()` animation settle
- Verified: bbox now correctly reflects Beverly area (42.55,-71.01) instead of 0,0,0,0

#### METAR HTTP 204 Handling
- `WeatherRepository.fetchMetars()`: changed `response.body!!.string()` to `response.body?.string().orEmpty()`
- Returns empty list when body is blank instead of crashing on `JsonParser.parseString("")`

#### Human-Readable METAR Tap Info
- Rewrote `buildMetarSnippet()` in MainActivity
- Wind: compass direction ("Southwest") instead of degrees (200°)
- Sky: decoded ("Scattered clouds") instead of abbreviation ("SCT")
- Weather phenomena: expanded via `decodeWx()` helper ("Light Rain" not "-RA")
- Flight category: explained ("VFR (Visual Flight Rules)")
- Observation time: formatted to local time ("9:53 PM")
- Added `degreesToCompass()` and `decodeWx()` helper methods
- Raw METAR kept at bottom for reference

#### Vehicle Direction Arrows
- Added `MarkerIconHelper.withArrow()` method
- Composites a small triangular arrow above the base vehicle icon, rotated to bearing
- Arrow is 8dp, same color as the vehicle icon
- Applied to all three vehicle types: trains (30dp), subway (26dp), buses (22dp)
- Arrow not cached by bearing to avoid excessive cache entries — cached per (resId, size, color, bearing)

### Status
- **Builds clean** — BUILD SUCCESSFUL
- **Tested** — METAR loads correctly after GPS fix, KBVY shows for Beverly area

### Files Changed
- `app/.../data/repository/WeatherRepository.kt` — HTTP 204 handling
- `app/.../ui/MainActivity.kt` — deferred METAR, human-readable snippet, arrow calls
- `app/.../ui/MarkerIconHelper.kt` — `withArrow()` method

---

## Session: 2026-02-28b (Defaults ON, Layer-aware LiveData, METAR Overhaul)

### Context
After expanding to 16 POI categories, all layers defaulted to OFF on fresh install. POI markers overwrote each other because all 16 categories shared a single LiveData. METAR was failing with HTTP 400 (API now requires bbox). METAR markers showed only a small icon with no weather data visible on the map.

### Changes Made

#### All Layers Default ON
- POI categories: `getBoolean(prefKey, false)` → `true` in MainActivity restore + AppBarMenuManager toggle/sync
- MBTA trains, subway, buses: restore defaults changed to `true`
- Radar and METAR: restore defaults changed to `true`
- Fresh install now shows everything immediately

#### Layer-Aware POI LiveData
- `_places` changed from `MutableLiveData<List<PlaceResult>>` to `MutableLiveData<Pair<String, List<PlaceResult>>>`
- `searchPoisAt()` now takes `layerId` parameter, emits `layerId to results`
- `searchPoisFromCache()` emits with `"cache"` layerId
- Observer destructures pair: `{ (layerId, places) -> }` — only clears/replaces that specific layer
- Removed `activePoiLayerId` variable (no longer needed)
- Fixes: all 16 categories now coexist on map simultaneously

#### METAR Bbox Passthrough
- Proxy: replaced static `proxyGet('/metar', ...)` with custom route accepting `?bbox=lat0,lon0,lat1,lon1`
- Proxy caches per-bbox key with 1h TTL
- App: `fetchMetars()` now takes `(south, west, north, east)` bounds
- ViewModel: `loadAllUsMetars()` → `loadMetars(south, west, north, east)`
- MainActivity: `loadMetarsForVisibleArea()` helper gets map bounding box

#### Rich METAR Station Markers
- `MetarStation` model: added `name`, `windGustKt`, `slpMb`, `skyCover`, `wxString`
- Parser: fixed `fltCat` field name (was `fltcat`), handles `visib` as string (`"10+"`), parses all new fields
- Map marker: text-based bitmap with temp (°F), wind arrow+speed, sky/wx — color-coded border by flight category
- Tap snippet: full METAR details (temp °F/°C, dewpoint, wind/gusts, vis, sky, wx, altimeter, SLP, raw METAR)
- `windDirToArrow()` helper converts degrees to unicode arrows

### Status
- **Builds clean** — BUILD SUCCESSFUL
- **Tested** — all layers load on fresh install, METAR stations display with weather data

### Files Changed
- `app/.../data/model/Models.kt` — MetarStation expanded
- `app/.../data/repository/WeatherRepository.kt` — bbox param, new field parsing
- `app/.../ui/MainActivity.kt` — defaults ON, layer-aware observer, METAR station icons, loadMetarsForVisibleArea()
- `app/.../ui/MainViewModel.kt` — Pair LiveData, loadMetars(bbox)
- `app/.../ui/menu/AppBarMenuManager.kt` — toggle/sync defaults to true
- `cache-proxy/server.js` — METAR bbox route

---

## Session: 2026-02-27b (16 POI Categories with Submenu Refinement)

### Context
App had 6 POI toggles (Restaurants, Gas, Transit, Civic, Parks, Earthquakes). Expanded to 16 useful categories, dropped Earthquakes entirely. Categories with natural subtypes get an AlertDialog submenu for refinement.

### Changes Made

#### New: `PoiCategories.kt` — Central Category Config
- `PoiCategory` data class: id, label, prefKey, tags, subtypes, color
- `PoiSubtype` data class: label, tags (for submenu checkboxes)
- `PoiCategories.ALL` — single source of truth for all 16 categories
- Menu, toggles, restore, queries, and marker colors all driven from this list

#### `MenuEventListener.kt` — PoiLayerId Expanded
- 6 constants → 16: `FOOD_DRINK`, `FUEL_CHARGING`, `TRANSIT`, `CIVIC`, `PARKS_REC`, `SHOPPING`, `HEALTHCARE`, `EDUCATION`, `LODGING`, `PARKING`, `FINANCE`, `WORSHIP`, `TOURISM_HISTORY`, `EMERGENCY`, `AUTO_SERVICES`, `ENTERTAINMENT`
- Old IDs removed: `RESTAURANTS`, `GAS_STATIONS`, `EARTHQUAKES`, `TRANSIT_ACCESS`, `PARKS`

#### `menu_poi.xml` — 16 Menu Items
- Categories with subtypes show `▸` suffix (e.g., "Food & Drink ▸")

#### `AppBarMenuManager.kt` — Data-Driven POI Menu
- Old 6 `PREF_POI_*` constants removed (now driven by `PoiCategory.prefKey`)
- `showPoiMenu()` rewritten: `menuIdToCategory` lookup map, iterates `PoiCategories.ALL`
- Simple categories toggle directly; subtype categories open `showPoiSubtypeDialog()`
- `showPoiSubtypeDialog()`: AlertDialog with multi-choice checkboxes, stores selections as `StringSet` pref
- `getActiveTags(categoryId)`: returns Overpass tags filtered by selected subtypes

#### `MarkerIconHelper.kt` — ~80 Category Mappings
- Expanded from 20 to ~80 entries covering all subtypes across all 16 categories
- Each subtype maps to its parent category's color
- Earthquake entry removed

#### `PlacesRepository.kt` — Expanded Category Extraction, Earthquake Code Removed
- `parseOverpassJson()` category chain: `amenity → shop → tourism → leisure → historic → office → "place"`
- `searchGasStations()` removed (use `searchPois()` with `amenity=fuel`)
- `fetchEarthquakes()` and `parseEarthquakeJson()` removed entirely

#### `MainViewModel.kt` — Removed Gas/Earthquake LiveData
- `gasStations` LiveData and `loadGasStations()` removed
- `earthquakes` LiveData and `loadEarthquakesForMap()` removed
- `searchPoisAt()` is the unified entry point for all POI searches

#### `MainActivity.kt` — Unified Marker Tracking
- 3 separate marker lists → `poiMarkers: MutableMap<String, MutableList<Marker>>`
- `activePoiLayerId` tracks which layer owns current places observer result
- `clearPoiMarkers(layerId)` and `addPoiMarker(layerId, place)` — per-layer ops
- `onPoiLayerToggled()` rewritten: lookup category, get active tags, unified search
- FAB speed dial: removed Earthquakes and Gas Stations buttons
- `onStart()` restore: iterates all 16 categories from `PoiCategories.ALL`
- Deferred restore: fires searches for all enabled categories with subtype filtering

### Status
- **Builds clean** (`./gradlew assembleDebug` — BUILD SUCCESSFUL)
- **Not yet committed** — changes are unstaged

### Files Changed
- `app/src/main/java/.../ui/menu/PoiCategories.kt` (new)
- `app/src/main/java/.../ui/menu/MenuEventListener.kt`
- `app/src/main/java/.../ui/menu/AppBarMenuManager.kt`
- `app/src/main/res/menu/menu_poi.xml`
- `app/src/main/java/.../ui/MarkerIconHelper.kt`
- `app/src/main/java/.../data/repository/PlacesRepository.kt`
- `app/src/main/java/.../ui/MainViewModel.kt`
- `app/src/main/java/.../ui/MainActivity.kt`

---

## Session: 2026-02-28 (POI Database — PostgreSQL)

### Context
The proxy's individual POI cache (`poi-cache.json`) had grown to 1334 unique POIs with rich Overpass data (280 unique tag keys, avg 7.3 tags/POI). Needed permanent storage for querying, analytics, and eventual API endpoints.

### Changes Made

#### PostgreSQL Schema (`cache-proxy/schema.sql`)
- `pois` table with composite PK `(osm_type, osm_id)` — globally unique OSM identifiers
- Promoted columns: `name` (from `tags.name`), `category` (derived: first match of amenity/shop/tourism/leisure/historic/office → `"key=value"`)
- `tags` JSONB column preserves all OSM tag keys; GIN index for flexible queries
- `first_seen`/`last_seen` TIMESTAMPTZ for discovery tracking
- Indexes: `category`, `name` (partial WHERE NOT NULL), `tags` (GIN)

#### Import Script (`cache-proxy/import-pois.js`)
- Standalone Node.js script, no dependency on proxy server code
- Fetches `http://localhost:3000/pois/export`, parses all POIs
- Extracts lat/lon (top-level for nodes, `center` for ways)
- Derives category from first matching tag key (amenity > shop > tourism > leisure > historic > office)
- Batch UPSERT in single transaction: `INSERT ... ON CONFLICT DO UPDATE` (preserves original `first_seen`)
- Connection via `DATABASE_URL` environment variable
- Prints summary: upserted count, total in database

#### Dependencies (`cache-proxy/package.json`)
- Added `pg` ^8.13.0 (node-postgres)

### Results
- Schema applied, all indexes created
- 1334 POIs imported successfully
- Top categories: parking (167), restaurant (94), bench (89), pitch (65), school (58)
- Re-import verified idempotent: count stays at 1334, no duplicates

### Files Changed
- `cache-proxy/schema.sql` (new)
- `cache-proxy/import-pois.js` (new)
- `cache-proxy/package.json`
- `cache-proxy/package-lock.json`

---

## Session: 2026-02-27 (Adaptive POI Radius)

### Context
POI search used a hardcoded 3000m radius. Dense metros (Boston downtown) triggered 429/504 errors from Overpass; rural areas returned zero results.

### Changes Made

#### Adaptive Radius — Proxy (`cache-proxy/server.js`)
- Added `radiusHints` Map with separate disk persistence (`radius-hints.json`)
- Grid key: 3dp lat/lon (~111m cells), same grid as POI cache
- `GET /radius-hint?lat=X&lon=Y` → returns `{ radius }` (default 3000)
- `POST /radius-hint` with `{ lat, lon, resultCount, error }` → applies adaptation rules:
  - Error → shrink 30% (× 0.7)
  - 0–4 results → grow 30% (× 1.3)
  - 5+ results → confirm (no change, refresh timestamp)
- Bounds: min 500m, max 15000m
- `GET /radius-hints` → admin dump of all hints
- `/cache/stats` now includes `radiusHints` count
- `/cache/clear` now also clears hints + deletes `radius-hints.json`

#### Adaptive Radius — App (`PlacesRepository.kt`)
- Replaced `RADIUS_M = 3000` with companion constants: `DEFAULT_RADIUS_M`, `MIN_RADIUS_M`, `MAX_RADIUS_M`, `MIN_USEFUL_POI_COUNT`
- Added `radiusHintCache` (ConcurrentHashMap, session-level)
- `fetchRadiusHint()`: GET from proxy, cache locally in `radiusHintCache`
- `postRadiusFeedback()`: POST result count / error to proxy, update local cache with response
- `buildOverpassQuery()` now takes `radiusM` parameter
- `searchPois()`: fetch hint → build query → execute → post feedback
- `searchPoisCacheOnly()`: uses local hint cache (no network call for hint)
- No changes to MainViewModel or MainActivity — adaptation is transparent

### Files Changed
- `cache-proxy/server.js`
- `app/src/main/java/.../data/repository/PlacesRepository.kt`

---

## Session: 2026-02-27 (Initial Commit Session)

### Context
First documented session. App was already functional with map, GPS, POI search, MBTA transit, weather overlays. This session added caching infrastructure, radar fix, vehicle tracking, and UI improvements.

### Changes Made

#### Cache Proxy (NEW)
- Created `cache-proxy/` — Node.js Express server on port 3000
- Routes: POST /overpass, GET /earthquakes, GET /nws-alerts, GET /metar
- Admin: GET /cache/stats, POST /cache/clear
- In-memory cache with TTL eviction + disk persistence (cache-data.json)
- Overpass cache key: lat/lon rounded to 3 decimals (~111m) + sorted tag filters
- X-Cache-Only header support for cache-only requests (no upstream on miss)
- Overpass TTL set to 365 days; earthquakes 2h; NWS alerts 1h; METAR 1h
- Tested: first request 400ms (MISS) → cached request 3.6ms (HIT)

#### Radar Fix
- RainViewer tiles returning 403 Forbidden on all requests
- Switched to NWS NEXRAD composite via Iowa State Mesonet
- URL: `mesonet.agron.iastate.edu/cache/tile.py/1.0.0/nexrad-n0q-900913/`
- No API key, no timestamp fetch needed, standard XYZ tiles

#### Vehicle Follow Mode (NEW)
- Tap any bus/train/subway marker → map zooms to it (zoom 16), dark banner appears
- Banner shows: vehicle type, label, route, status, speed
- On each refresh cycle (30-60s): map re-centers on vehicle, banner updates
- Tap banner to stop following
- Toggling layer off stops following
- POI prefetch fires at vehicle position on each update (fills cache along route)

#### MBTA JsonNull Fix
- Vehicles with null stop/trip relationships crashed parser (~30 warnings per refresh)
- Fixed: `getAsJsonObject("data")` → `get("data")?.takeIf { !it.isJsonNull }?.asJsonObject`
- All vehicles now parse cleanly

#### POI Marker Redesign
- Changed from 26dp vector icons to 5dp colored dots
- Semi-transparent circle with opaque center point
- Category colors preserved (orange=gas, red=restaurant, green=park, etc.)
- Much cleaner at any zoom level

#### Map Interaction Changes
- Single tap: disabled (was setting manual location)
- Long press: enters manual mode, centers map, triggers POI search
- Scroll/pan: displays cached POIs only (X-Cache-Only header, no upstream calls)

#### Android URL Routing
- PlacesRepository: Overpass + earthquakes → proxy at 10.0.0.4:3000
- WeatherRepository: NWS alerts + METAR → proxy at 10.0.0.4:3000
- AndroidManifest: usesCleartextTraffic was already true
