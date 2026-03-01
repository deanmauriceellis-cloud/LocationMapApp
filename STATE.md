# LocationMapApp v1.5 — Project State

## Last Updated: 2026-02-28 Session 17 (Debug HTTP Server — Embedded in App)

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
- Weather alerts (NWS active alerts)
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
- Aircraft follow mode: tap airplane → map tracks it globally via icao24 query
  - Dedicated icao24 refresh loop (not limited to bbox — tracks anywhere in the world)
  - Banner shows callsign, altitude, speed, heading, vertical rate, SPI flag
  - POI prefetch at aircraft position each refresh cycle
  - 3-strike failure tolerance on position queries (handles 429 rate limits)
  - Auto-stops with toast when aircraft disappears after 3 consecutive failures
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
- Cached POI coverage display: proxy `/pois/bbox` endpoint returns POIs within visible map area
  - Loads on startup, refreshes on scroll/zoom (500ms debounce), refreshes after follow prefetch
- Adaptive POI search radius — proxy stores per-grid-cell hints, app fetches/reports, 20km fuzzy matching
- Individual POI cache (poi-cache.json) — deduped by OSM type+id, with first/last seen timestamps
- POI database (PostgreSQL) — permanent storage with JSONB tags, category indexing, upsert import
- Cache proxy with disk persistence (cache-data.json, radius-hints.json, poi-cache.json, 365-day Overpass TTL)
- **Overpass request queue** (v1.5.16): proxy serializes upstream Overpass requests, 10s minimum gap
  - Cache hits return instantly; cache misses queued and processed one at a time
  - Prevents Overpass 429/504 storms from parallel requests
  - Queue depth visible in `/cache/stats` → `overpassQueue`
  - `X-POI-New` / `X-POI-Known` response headers report new vs existing POIs per request
- **Startup POI fix** (v1.5.16): no per-category Overpass queries on launch, just loads cached bbox
- **Error radius immunity** (v1.5.16): 504/429 errors no longer shrink radius hints (transient, not density)
- Debug logging + TCP log streamer
- **Embedded debug HTTP server** (v1.5.18) — programmatic app control via `adb forward` + `curl`
  - Port 8085, raw `ServerSocket` on `Dispatchers.IO`, minimal HTTP/1.0 parser, JSON via Gson
  - 19 endpoints: state, logs, map control, marker listing/search/tap/nearest, screenshot, livedata, prefs, toggle, search, refresh, follow, stop-follow, perf, overlays
  - `DebugHttpServer.kt` (singleton accept loop) + `DebugEndpoints.kt` (all handlers)
  - `runOnMain` helper for UI-thread access via `suspendCancellableCoroutine`
  - Marker tap via synthetic MotionEvent at projected screen position
  - Lifecycle-aware: endpoints registered in `onResume`, nulled in `onPause`
  - Usage: `adb forward tcp:8085 tcp:8085 && curl localhost:8085/state | jq .`

## External API Routing
| API | App Endpoint | Proxy Route | TTL |
|-----|-------------|-------------|-----|
| Overpass POI | `http://10.0.0.4:3000/overpass` | POST /overpass | 365 days |
| NWS Alerts | `http://10.0.0.4:3000/nws-alerts` | GET /nws-alerts | 1 hour |
| METAR | `http://10.0.0.4:3000/metar?bbox=...` | GET /metar?bbox=s,w,n,e | 1 hour (per bbox) |
| Radius Hints | `http://10.0.0.4:3000/radius-hint` | GET+POST /radius-hint | persistent |
| POI Cache | `http://10.0.0.4:3000/pois/...` | GET /pois/stats, /pois/export, /pois/bbox, /poi/:type/:id | persistent |
| Aircraft | `http://10.0.0.4:3000/aircraft?...` | GET /aircraft?bbox=s,w,n,e or ?icao24=hex | 15 seconds |
| Webcams | `http://10.0.0.4:3000/webcams?...` | GET /webcams?s=&w=&n=&e=&categories= | 10 minutes |
| DB POI Query | `http://10.0.0.4:3000/db/pois/...` | GET /db/pois/search, /nearby, /stats, /categories, /coverage | live |
| DB POI Lookup | `http://10.0.0.4:3000/db/poi/...` | GET /db/poi/:type/:id | live |
| MBTA Vehicles | direct (api-v3.mbta.com) | not proxied | — |
| MBTA Stations | direct (api-v3.mbta.com/stops) | not proxied | — |
| MBTA Predictions | direct (api-v3.mbta.com/predictions) | not proxied | — |
| MBTA Schedules | direct (api-v3.mbta.com/schedules) | not proxied | — |
| Radar tiles | direct (mesonet.agron.iastate.edu) | not proxied | — |

## Map Interaction Model
- **Single tap**: no action
- **Long press (~2s)**: enter manual mode, center map, search POIs at location
- **Scroll/pan**: displays cached POIs for visible area via proxy `/pois/bbox`
- **Tap vehicle marker**: follow mode (map tracks vehicle, banner shows status/speed)
- **Tap station marker**: arrival board dialog (real-time predictions), tap train → trip schedule dialog
- **Tap aircraft marker**: follow mode (map tracks globally via icao24, banner shows flight info)
- **Tap follow/populate banner**: stop following or stop populate scan
- **Utility → Populate POIs**: systematic grid scanner spirals from map center

## Key Files
- `app/src/main/java/.../ui/MainActivity.kt` — main map activity, all overlays
- `app/src/main/java/.../ui/MainViewModel.kt` — LiveData, data fetching
- `app/src/main/java/.../ui/MarkerIconHelper.kt` — icon/dot rendering with cache
- `app/src/main/java/.../ui/menu/PoiCategories.kt` — central config for all 16 POI categories
- `app/src/main/java/.../data/repository/PlacesRepository.kt` — Overpass POI search
- `app/src/main/java/.../data/repository/WeatherRepository.kt` — NWS + METAR
- `app/src/main/java/.../data/repository/AircraftRepository.kt` — OpenSky aircraft
- `app/src/main/java/.../data/repository/MbtaRepository.kt` — MBTA vehicles
- `app/src/main/java/.../data/repository/WebcamRepository.kt` — Windy webcams
- `cache-proxy/server.js` — Express caching proxy
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
  - 70,808 POIs as of 2026-02-28 (synced via `import-pois.js`)
- **`aircraft_sightings` table**: Serial PK, tracks each continuous observation as a separate row
  - Columns: icao24, callsign, origin_country, first/last seen, first/last lat/lon/altitude/heading, velocity, vertical_rate, squawk, on_ground
  - 5-minute gap between observations = new sighting row (enables flight history analysis)
  - Indexes: icao24, callsign, first_seen, last_seen, last_lat+lon
  - 28,690 sightings / 8,337 unique aircraft as of 2026-02-28
  - Real-time: proxy writes to DB on every aircraft API response (cache hits and misses)
- **DB query API** (`/db/*` prefix): 6 endpoints for searching, filtering, analytics
  - `GET /db/pois/search` — name search (ILIKE), category filter, bbox, tag queries, distance sort
  - `GET /db/pois/nearby` — Haversine distance sort with bbox pre-filter
  - `GET /db/poi/:type/:id` — single POI with timestamps
  - `GET /db/pois/stats` — totals, named count, top categories, bounds, time range
  - `GET /db/pois/categories` — category breakdown with key/value split
  - `GET /db/pois/coverage` — geographic grid with configurable resolution
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

Marker types: `poi`, `stations`, `trains`, `subway`, `buses`, `aircraft`, `webcams`, `metar`, `gps`

## Next Steps
- **Test debug HTTP server** — `adb forward tcp:8085 tcp:8085`, verify all endpoints
- **Test station markers** — toggle on, verify ~270 stations appear, tap one, verify arrival board, tap train row, verify schedule
- **Test aircraft layer** with rate limiter — enable aircraft, verify throttling works, no 429 storms
- **Test webcam "View Live"** — tap webcam → preview → View Live → verify WebView player loads
- Monitor cache growth and hit rates over time
- Evaluate proxy → remote deployment for non-local testing
- Automate periodic POI imports (cron or proxy hook)
- Add aircraft query endpoints to /db/* API (search by callsign, flight path analysis)
