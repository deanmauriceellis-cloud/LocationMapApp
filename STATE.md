# LocationMapApp v1.5 — Project State

## Last Updated: 2026-03-04 Session 62 (Proxy Heap Reduction — 643MB → 208MB)

## Architecture
- **Android app** (Kotlin, Hilt DI, OkHttp, osmdroid) targeting API 34
- **Cache proxy** (Node.js/Express on port 3000) — transparent caching layer between app and external APIs
- **PostgreSQL** (`locationmapapp` DB) — permanent storage for POIs and aircraft sightings
- **Split**: App → Cache Proxy → External APIs (Overpass, NWS, Aviation Weather, MBTA, OpenSky, Windy Webcams)

## What's Working
- Map display (osmdroid), GPS tracking with manual override (long-press), custom zoom slider
- **17 POI categories, 153 subtypes** with submenu refinement (central config in `PoiCategories.kt`)
  - 5dp colored dots; zoom ≥ 16: labeled icons with type + name
  - Layer-aware LiveData `Pair<String, List>`, viewport-only display via `/pois/bbox`
  - All layers default ON except aircraft (OFF)
- **Weather dialog** (v1.5.34): current conditions, 48-hour hourly, 7-day outlook, expandable alerts
  - Proxy `/weather?lat=&lon=` (5 NWS calls), 22 vector icons, auto-fetch every 30min
  - Toolbar icon shows current conditions; red border when alerts active
- **METAR** — rich text markers (temp, wind, sky), bbox passthrough, deferred load, human-readable tap info
- **NWS NEXRAD radar** tiles (Iowa State Mesonet) + animated radar (7-frame 35-min loop), default 35% opacity
- **Dark mode** (v1.5.44): toolbar moon icon toggles between MAPNIK and CartoDB Dark Matter tiles, persisted
- **Share POI** (v1.5.44): 5th action button in POI detail dialog, shares name/address/phone/hours + Google Maps link
- **Favorites** (v1.5.44): star icon in POI detail dialog, SharedPreferences+JSON storage, dedicated Favorites cell in Find dialog
- **Smart fuzzy search** (v1.5.51): pg_trgm similarity + keyword→category hints in Find dialog search bar
  - Typo-tolerant: "Starbcks" finds Starbucks, "Dunkin Donts" finds Dunkin' Donuts
  - ~110 keyword→category mappings: "historic" → Tourism & History, "gas" → Fuel & Charging, "tattoo" → Shopping, etc.
  - **Cuisine-aware search** (v1.5.57): "pizza" matches name OR `cuisine=pizza` tag (~12.8k cuisine-tagged POIs)
  - Combined queries: "food italian" → Food & Drink category + fuzzy "italian" name + cuisine match
  - Distance expansion: 50km → 100km → 100mi/160,934m (stops at ≥50 results)
  - **Results sorted by distance** (v1.5.56): nearest first, regardless of fuzzy match score
  - Rich result rows: bold name, detail line (cuisine/brand), category label in category color
  - Header hint bar: count + category + "refine to narrow" in title bar next to "Find" (200 result limit)
- **MBTA transit** — live vehicles (buses, CR, subway) with directional arrows + staleness detection
  - Commuter rail: next-stop ETA badge on labeled markers (batch predictions API)
  - ~270 train stations: tap → arrival board (30s auto-refresh) → trip schedule
  - ~7,900 bus stops: viewport-filtered, zoom ≥ 15, tap → arrival board
  - Vehicle detail dialog: info + Follow/View Route/Arrivals buttons
- **Aircraft tracking** (OpenSky) — rotated airplane icons, callsign, altitude-colored, SPI emergency
  - 18 state vector fields, configurable refresh (30s–5min), zoom ≥ 10 guard
  - Dedicated **Air** menu (toggle, frequency, auto-follow)
- **Slim toolbar + status line + grid dropdown** (v1.5.40): replaced 2×5 icon grid with compact toolbar bar
  - **Toolbar** (40dp): Weather | Home | spacer | Dark Mode | Alerts | Grid | About (v1.5.55)
  - **Status line** (24dp): priority-based info bar — GPS coords+weather when idle, follow/scan/alert info when active
    - `StatusLineManager.kt`: 8 priority levels (GPS_IDLE → GEOFENCE_ALERT), set/clear/updateIdle API
    - All banner functions migrated from dynamic TextView creation to StatusLineManager
    - Geofence alerts show zone-type-colored background on status line
  - **Grid dropdown**: PopupWindow with 8 labeled buttons (icon+text) in 2×4 grid
    - Row 1: Transit, Webcams, Aircraft, Radar | Row 2: POI, Utility, Find, Go To
  - `setupSlimToolbar()` + `showGridDropdown()` in AppBarMenuManager; `fitsSystemWindows` on AppBarLayout
  - **Home icon** (v1.5.55): house icon centers map on GPS at zoom 18 with 800ms animation
  - **About icon** (v1.5.55): info circle shows version/copyright/contact dialog (DestructiveAIGurus.com)
  - Weather icon dynamically updates to show current conditions; red border when alerts active
  - Alerts icon dynamically colored by severity: gray (none), blue (INFO), yellow (WARNING), red (CRITICAL), pulsing red (EMERGENCY)
  - Debug `/state` includes `statusLine` field with text + priority name
- **Go to Location** (v1.5.32): Photon geocoder autocomplete via proxy `/geocode`, US-only, 500ms debounce
- **Aircraft follow mode**: global icao24 tracking, 3-strike tolerance, flight path trail (altitude-colored polyline, DB history + live, 1000-point cap)
- **Auto-follow aircraft (POI Builder)**: ≥10k ft, 20-min rotation, smart switching (altitude/POI/US bounds)
- **Webcam layer** (Windy API): 18 categories, preview + live player, 0.5° min bbox, 10-min cache
- **Social layer** (v1.5.45) — auth, POI comments, real-time chat
  - **Auth**: device-bonded registration (register once, no login/logout), Argon2id hashing, JWT access tokens (15min) + refresh tokens (365d)
    - Family-based refresh token rotation with reuse revocation
    - `AuthRepository.kt`: auto-refresh expired tokens, SharedPreferences storage
    - Profile dialog: avatar initial, display name, role badge (no logout — device-bonded)
    - Register-only dialog from grid dropdown "Social" button (no login toggle)
  - **POI Comments**: star ratings (1-5), upvote/downvote, threaded (parent_id), soft delete
    - `CommentRepository.kt`: fetch/post/vote/delete with auth
    - Comments section in POI detail dialog: author, relative time, rating stars, vote arrows
    - "Add Comment" sub-dialog with star rating selector
  - **Chat**: Socket.IO real-time messaging, REST room list/create/history
    - `ChatRepository.kt`: Socket.IO connect/disconnect, room join/leave, send/receive
    - Auto-creates "Global" room on proxy startup
    - Room list dialog: name, member count, description, tap to enter
    - Chat room dialog: message bubbles (own=blue, others=gray), author name, send bar, fitsSystemWindows
    - Socket.IO connect-before-join: suspendCancellableCoroutine waits for actual connection
    - Create room dialog: name + optional description
  - **Grid dropdown**: expanded to 3×4 (12 buttons) — Row 3: Social, Chat, Profile, Legend
  - **Proxy deps**: argon2, jsonwebtoken, socket.io; `http.createServer` + Socket.IO attach
  - **Android dep**: `io.socket:socket.io-client:2.1.0`
  - **8 DB tables**: users, auth_lookup, refresh_tokens, poi_comments, comment_votes, chat_rooms, room_memberships, messages
  - **Security hardening** (v1.5.47): server-side sanitization + validation + rate limiting + login lockout
    - `sanitizeText(str, maxLen)` / `sanitizeMultiline(str, maxLen)`: strip HTML, control chars, enforce caps
    - Rate limiting: auth 10/15min (IP), comments 10/1min (user), rooms 5/1hr (user), Socket.IO 30/60s (socket)
    - Login lockout: 5 fails → 15min IP lock, auto-cleanup 30min interval
    - Validation: displayName 2-50 chars unicode whitelist, password max 128, email format, osmType/roomType enums, rating integer
    - JSON body limit 16kb; `/auth/debug/users` requires auth + owner/support role
    - Client-side: `InputFilter.LengthFilter` on all social EditTexts, min-length checks, email format, comment char counter
    - Proxy dep: express-rate-limit ^7.5.1
  - **Skipped for now**: COPPA age gate, email encryption, content moderation, OAuth, monthly partitioning
- **Cap detection & retry-to-fit**: halves radius on 500-element cap, 20km fuzzy hints, MIN_RADIUS 100m
- **Populate POIs v2** (grid scanner): probe-calibrate-spiral with recursive 3×3 subdivision, 10km initial probe, narrative banner
- **10km Probe Populate** (v1.5.42): expanding spiral of 10km wide probes for low-density POI discovery
  - Crosshairs pan (no zoom), 30s countdown, status line with last probe count + recommended fill radius
  - `estimateFillRadius()`: targets ~200 POIs per search area, scales by density
  - Fill Probe Populate: stub for future implementation
- **Vehicle follow**: tap → track, staleness detection (>2 min), POI prefetch along route
- **Find dialog** (v1.5.26, overhauled v1.5.50): category grid → subtype grid → distance-sorted results, long-press filter mode
  - Counts scoped to 10km radius around map center; auto-fit cell heights (36dp min, 120dp max cap)
  - ScrollView wrapping on both grids — large categories (26 Shopping, 18 Entertainment) scroll instead of clipping
  - Dialog height 85% of screen; search bar fixed above scrollable grid
  - Unlimited distance on `/db/pois/find`: scope expands 50km → 200km → 1000km → global (no bbox)
  - Smart fuzzy search bar (v1.5.51): pg_trgm fuzzy + keyword hints, 1000ms debounce, rich 3-line result rows
  - Favorites cell (v1.5.44): gold star, first in grid, shows count badge, tap for sorted favorites list
  - **Filter and Map** (v1.5.53, improved v1.5.56): teal button at top of search results → exclusive map view
    - Clears all other layers (transit, aircraft, webcams, METAR, geofences, radar), stops background jobs
    - Force-labels all result markers at any zoom; keeps current position, adaptive zoom (18→out until results visible)
    - Status line "Showing N label — tap to clear" with FIND_FILTER priority; tap exits and restores layers
    - Scroll/zoom handlers guarded — no layer reloads while active
- **POI Detail Dialog** (v1.5.27): info rows, website (3-tier waterfall), action buttons (Directions/Call/Reviews/Map/Share)
  - Tap any POI marker on map → opens detail dialog directly (v1.5.46)
  - Star icon in header (v1.5.44): tap to add/remove from favorites, filled/outline toggle
- **Legend dialog** (v1.5.25): 7 sections, Utility menu, driven from `PoiCategories.ALL`
- Transit zoom guard (zoom ≤ 10 hides markers), POI display (zoom ≥ 10 + max 5000 markers), adaptive radius hints
- **Idle auto-populate** (v1.5.33): 10-min GPS stationarity → full scanner, 45s delays, GPS-centered
  - **POI density guard** (v1.5.48): checks `/db/pois/counts` 10km radius before starting; skips if ≥100 POIs nearby
  - Touch-to-stop: any map tap cancels idle populate, resets 10-min idle timer
  - Any UI activity (grid dropdown, dialogs, toolbar buttons) also resets idle timer
  - State preservation: stopped idle scanner resumes from last ring/point (not from scratch)
  - State cleared on: long-press, GPS move >100m, goToLocation, manual populate start
- **Overpass queue**: serialized upstream, 10s min gap, per-client fair queue, covering cache, content hash delta, **retry with 3-endpoint rotation + exponential backoff** (proxy + app-side)
  - **Queue cancel** (`POST /overpass/cancel`): flushes all queued requests for a client; called on stop follow, stop populate, GPS move
- **Proxy heap reduction** (v1.5.60): eliminated poiCache Map (268k entries, ~90MB), LRU cap on main cache (7,214 → 2,000 entries)
  - All POI endpoints (`/pois/*`, `/poi/:type/:id`) query PostgreSQL directly; `collectPoisInRadius()` uses PostgreSQL
  - Import buffer: lightweight array drains every 15min; `MAX_CACHE_ENTRIES` env var (default 2000)
  - Heap: 643MB → 208MB; `poi-cache.json` eliminated; `cache-data.json` 320MB → 57MB
- **Scan cell coverage** (v1.5.59): ~1.1km grid cells track when areas were last scanned
  - Decision flow: exact cache → covering cache → cache-only merge → **scan cell FRESH → serve from PostgreSQL** → upstream Overpass
  - `X-Cache: CELL` + `X-Coverage: FRESH/STALE/EMPTY` headers on all `/overpass` responses
  - Configurable: `SCAN_FRESHNESS_MS` (default 24h), `MIN_COVERAGE_POIS` (default 10)
  - Persisted to `scan-cells.json`; debug endpoint `GET /scan-cells`
  - Idle/manual populate: FRESH cells **skipped entirely** (no search, no countdown, no subdivision)
  - Silent fill: FRESH cells show brief "Coverage fresh" banner (1.5s vs 3s)
- **Bbox snapping**: METAR/webcams 0.01°, aircraft 0.1° for cache hit rate
- **Silent fill** (v1.5.28): single Overpass search on startup/restore/long-press (3-4s delay)
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
- **Database Import & Export** (v1.5.39) — import custom zone databases, export installed databases
  - **Import SQLite .db**: SAF file picker → schema validation (db_meta + zones tables, required columns, zone count) → install to geofence_databases
  - **Import CSV**: SAF file picker → config dialog (name, zone type, default radius) → parsed with column aliases → converted to SQLite with full schema + bbox indexes
  - **Export**: installed databases shareable via Android share intent (FileProvider)
  - **Duplicate handling**: detects existing database ID, shows overwrite confirmation dialog
  - **Local-only databases**: catalog merges locally-imported databases not in remote catalog; works offline
  - **Database Manager UI**: "IMPORT .DB" / "IMPORT CSV" buttons at top; "EXPORT" button on installed cards
- **MODULE_ID** (v1.5.55): searchable copyright+module constant in every source file (131 files)
  - Kotlin: `private const val MODULE_ID` after imports; JS: `const MODULE_ID`; Shell: `MODULE_ID=`; SQL: `-- MODULE_ID:`; XML: `Module:` in copyright comment
- **Startup**: loads cached bbox only (no per-category Overpass queries); 504/429 don't shrink radius hints
- **Debug HTTP server** (v1.5.18): port 8085, 24 endpoints, `adb forward` + `curl`
  - `DebugHttpServer.kt` (singleton) + `DebugEndpoints.kt`; lifecycle-aware, double-start guard
  - `relatedObject` on all markers; reflection-based marker tap; `runOnMain` helper
  - Test suite: `test-app.sh` (30+ tests), `overnight-test.sh`, `morning-transit-test.sh`

## External API Routing
| API | App Endpoint | Proxy Route | TTL |
|-----|-------------|-------------|-----|
| Overpass POI | `http://10.0.0.4:3000/overpass` | POST /overpass | 365 days |
| NWS Alerts | `http://10.0.0.4:3000/nws-alerts` | GET /nws-alerts | 1 hour |
| NWS Weather | `http://10.0.0.4:3000/weather?lat=&lon=` | GET /weather (composite) | 5min–24h per section |
| METAR | `http://10.0.0.4:3000/metar?bbox=...` | GET /metar?bbox=s,w,n,e | 1 hour (per bbox) |
| Radius Hints | `http://10.0.0.4:3000/radius-hint` | GET+POST /radius-hint | persistent |
| POI Query | `http://10.0.0.4:3000/pois/...` | GET /pois/stats, /pois/export, /pois/bbox, /poi/:type/:id | live (PostgreSQL) |
| Aircraft | `http://10.0.0.4:3000/aircraft?...` | GET /aircraft?bbox=s,w,n,e or ?icao24=hex | 15 seconds |
| Webcams | `http://10.0.0.4:3000/webcams?...` | GET /webcams?s=&w=&n=&e=&categories= | 10 minutes |
| DB Import | `http://10.0.0.4:3000/db/import` | POST /db/import (manual), GET /db/import/status | live |
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
| Auth | `http://10.0.0.4:3000/auth/*` | POST /auth/register, /login, /refresh, /logout; GET /auth/me | — |
| Comments | `http://10.0.0.4:3000/comments/*` | GET /comments/:type/:id; POST /comments, /comments/:id/vote; DELETE /comments/:id | live |
| Chat Rooms | `http://10.0.0.4:3000/chat/*` | GET /chat/rooms, /chat/rooms/:id/messages; POST /chat/rooms | live |
| Chat Socket.IO | `ws://10.0.0.4:3000` | join_room, leave_room, send_message, typing; new_message, user_typing | real-time |
| MBTA Bus Stops | `http://10.0.0.4:3000/mbta/bus-stops` | GET /mbta/bus-stops | 24 hours |
| MBTA Vehicles | direct (api-v3.mbta.com) | not proxied | — |
| MBTA Stations | direct (api-v3.mbta.com/stops) | not proxied | — |
| MBTA Predictions | direct (api-v3.mbta.com/predictions) | not proxied | — |
| MBTA Schedules | direct (api-v3.mbta.com/schedules) | not proxied | — |
| Radar tiles | direct (mesonet.agron.iastate.edu) | not proxied | — |

## Map Interaction Model
- **Tap POI marker**: opens POI detail dialog directly (info, comments, actions)
- **Single tap on map** (empty area): stops all following (vehicle/aircraft) and all population tasks (populate, 10km probe, idle populate, silent fill)
- **Long press (~2s)**: enter manual mode, center map (auto-zoom to 18 if <18), search POIs at location, fetch weather + alerts
- **Scroll/pan**: displays cached POIs for visible area via proxy `/pois/bbox`
- **Tap vehicle marker**: vehicle detail dialog (route, status, speed) with Follow / View Route / Arrivals buttons
- **Tap station marker**: arrival board dialog (real-time predictions), tap train → trip schedule dialog
- **Tap bus stop marker**: arrival board dialog (real-time bus predictions)
- **Tap aircraft marker**: follow mode (map tracks globally via icao24, banner shows flight info)
- **Tap follow/populate banner**: stop following or stop populate scan
- **Tap find filter banner**: exit filter mode, restore normal POI display
- **Tap filter-and-map status line**: exit exclusive filter view, restore all layers
- **Find toolbar icon**: search bar + favorites cell + category grid → subtype grid → distance-sorted results → tap to open POI detail dialog
- **Find results "Filter and Map" button**: enter exclusive map view showing only those results with forced labels
- **Go to Location toolbar icon**: geocoder dialog → type address → pick result → map navigates + POI search
- **POI detail dialog**: info rows + star (favorite) + Load Website button + action buttons (Directions, Call, Reviews, Map, Share)
- **Find long-press**: filter map to show only that category's POIs
- **Utility → Populate POIs**: systematic grid scanner spirals from map center

## Key Files

### MainActivity Decomposition (13 extension-function files)
- `app/src/main/java/.../ui/MainActivity.kt` — main map activity, lifecycle, setup, observers (1,996 lines)
- `app/src/main/java/.../ui/MainActivityFind.kt` — Find dialog, fuzzy search, Filter and Map (2,161 lines)
- `app/src/main/java/.../ui/MainActivityGeofences.kt` — geofence overlays, zone loading, alerts (1,011 lines)
- `app/src/main/java/.../ui/MainActivityTransit.kt` — MBTA vehicles, stations, bus stops (977 lines)
- `app/src/main/java/.../ui/MainActivitySocial.kt` — auth, comments, chat UI (860 lines)
- `app/src/main/java/.../ui/MainActivityPopulate.kt` — POI populate scanner, idle populate (782 lines)
- `app/src/main/java/.../ui/MainActivityWeather.kt` — weather dialog, alerts dialog (607 lines)
- `app/src/main/java/.../ui/MainActivityAircraft.kt` — aircraft overlays, follow mode, flight trail (534 lines)
- `app/src/main/java/.../ui/MainActivityDebug.kt` — debug HTTP server accessor methods (332 lines)
- `app/src/main/java/.../ui/MainActivityDialogs.kt` — POI detail, vehicle detail dialogs (269 lines)
- `app/src/main/java/.../ui/MainActivityRadar.kt` — radar tile overlay, animation (211 lines)
- `app/src/main/java/.../ui/MainActivityMetar.kt` — METAR markers, info display (208 lines)
- `app/src/main/java/.../ui/MainActivityHelpers.kt` — small utility functions (39 lines)

### ViewModel Decomposition (7 ViewModels)
- `app/src/main/java/.../ui/MainViewModel.kt` — Location + POI viewport only (215 lines)
- `app/src/main/java/.../ui/SocialViewModel.kt` — auth, comments, chat (200 lines)
- `app/src/main/java/.../ui/TransitViewModel.kt` — MBTA trains, subway, buses, stations (165 lines)
- `app/src/main/java/.../ui/GeofenceViewModel.kt` — TFR, cameras, schools, flood, crossings, databases (286 lines)
- `app/src/main/java/.../ui/WeatherViewModel.kt` — weather, METAR, webcams, radar (108 lines)
- `app/src/main/java/.../ui/FindViewModel.kt` — Find dialog DB queries, text search (86 lines)
- `app/src/main/java/.../ui/AircraftViewModel.kt` — aircraft tracking, flight history (79 lines)

### Menu & UI
- `app/src/main/java/.../ui/menu/AppBarMenuManager.kt` — toolbar menus, grid dropdown (812 lines)
- `app/src/main/java/.../ui/menu/MenuPrefs.kt` — preference key constants (74 lines)
- `app/src/main/java/.../ui/menu/PoiCategories.kt` — central config for all 17 POI categories (153 subtypes)
- `app/src/main/java/.../ui/menu/MenuEventListener.kt` — menu event interface
- `app/src/main/java/.../ui/MarkerIconHelper.kt` — icon/dot rendering with cache
- `app/src/main/java/.../ui/WeatherIconHelper.kt` — NWS icon code → drawable mapping
- `app/src/main/java/.../ui/StatusLineManager.kt` — priority-based toolbar status line manager

### Repositories
- `app/src/main/java/.../data/repository/PlacesRepository.kt` — Overpass POI search
- `app/src/main/java/.../data/repository/WeatherRepository.kt` — NWS + METAR
- `app/src/main/java/.../data/repository/AircraftRepository.kt` — OpenSky aircraft
- `app/src/main/java/.../data/repository/MbtaRepository.kt` — MBTA vehicles
- `app/src/main/java/.../data/repository/FindRepository.kt` — Find dialog DB queries + text search
- `app/src/main/java/.../data/repository/WebcamRepository.kt` — Windy webcams
- `app/src/main/java/.../data/repository/TfrRepository.kt` — FAA TFR fetch via proxy
- `app/src/main/java/.../data/repository/GeofenceRepository.kt` — speed cameras, schools, flood zones, crossings
- `app/src/main/java/.../data/repository/GeofenceDatabaseRepository.kt` — downloadable geofence DB catalog/download/import/export
- `app/src/main/java/.../data/repository/AuthRepository.kt` — JWT auth, token storage, auto-refresh
- `app/src/main/java/.../data/repository/CommentRepository.kt` — POI comments CRUD
- `app/src/main/java/.../data/repository/ChatRepository.kt` — Socket.IO chat + REST rooms/messages

### Utilities
- `app/src/main/java/.../util/GeofenceEngine.kt` — JTS R-tree spatial index + multi-zone geofence alerting
- `app/src/main/java/.../util/FavoritesManager.kt` — SharedPreferences+JSON favorites CRUD
- `app/src/main/java/.../util/DebugHttpServer.kt` — embedded HTTP server (port 8085)
- `app/src/main/java/.../util/DebugEndpoints.kt` — debug endpoint handlers (takes 6 ViewModel params)

### Cache Proxy (decomposed into 19 modules)
- `cache-proxy/server.js` — Express bootstrap, middleware, module loader (164 lines)
- `cache-proxy/lib/config.js` — environment vars, constants
- `cache-proxy/lib/cache.js` — file-based cache engine with LRU eviction + import buffer
- `cache-proxy/lib/opensky.js` — OpenSky OAuth2 token management
- `cache-proxy/lib/scan-cells.js` — scan cell coverage tracking (~1.1km grid, persistence, debug endpoint)
- `cache-proxy/lib/overpass.js` — POST /overpass (queue, cache, scan cells, content hash, cancel)
- `cache-proxy/lib/db-pois.js` — 8 /db/pois/* routes + /pois/website
- `cache-proxy/lib/db-aircraft.js` — 4 /db/aircraft/* routes
- `cache-proxy/lib/aircraft.js` — /aircraft endpoint
- `cache-proxy/lib/weather.js` — /weather composite NWS
- `cache-proxy/lib/tfr.js` — /tfrs FAA scraping
- `cache-proxy/lib/zones.js` — /cameras, /schools, /flood-zones, /crossings
- `cache-proxy/lib/auth.js` — 5 /auth/* routes + rate limiting
- `cache-proxy/lib/comments.js` — 4 /comments/* routes
- `cache-proxy/lib/chat.js` — 3 /chat/* routes + Socket.IO
- `cache-proxy/lib/pois.js` — POI endpoints (PostgreSQL-backed: stats, export, bbox, single lookup)
- `cache-proxy/lib/import.js`, `metar.js`, `webcams.js`, `mbta.js`, `geocode.js`, `geofences.js`, `admin.js`, `proxy-get.js`
- `cache-proxy/geofence-databases/catalog.json` — geofence database catalog (4 databases)
- `cache-proxy/geofence-databases/build-military.js` — military base polygon builder (ArcGIS NTAD)
- `cache-proxy/geofence-databases/build-excam.js` — speed/red-light camera builder (WzSabre XZ/NDJSON)
- `cache-proxy/geofence-databases/build-nces.js` — US public school builder (NCES ArcGIS)
- `cache-proxy/geofence-databases/build-dji-nofly.js` — DJI no-fly zone builder (GitHub CSV)
- `cache-proxy/cache-data.json` — persistent Overpass cache, LRU-capped at 2000 entries (gitignored)
- `cache-proxy/radius-hints.json` — adaptive radius hints per grid cell (gitignored)
- `cache-proxy/scan-cells.json` — scan cell coverage timestamps + POI counts (gitignored)
- `cache-proxy/schema.sql` — PostgreSQL schema for permanent POI storage
- `app/src/main/java/.../util/DebugHttpServer.kt` — embedded HTTP server (port 8085)
- `app/src/main/java/.../util/DebugEndpoints.kt` — all debug endpoint handlers
- `cache-proxy/import-pois.js` — standalone script to import POIs from proxy into PostgreSQL

## PostgreSQL Database
- Database: `locationmapapp`, user: `witchdoctor`
- **`pois` table**: Composite PK `(osm_type, osm_id)`, JSONB tags (GIN index), promoted name/category columns
  - Indexes: category, name (partial), tags (GIN), lat+lon (compound), **name trigram (GIN pg_trgm)** for fuzzy search
  - 268,291 POIs as of 2026-03-04 (auto-imported, ~12.8k with cuisine tags)
- **`aircraft_sightings` table**: Serial PK, tracks each continuous observation as a separate row
  - Columns: icao24, callsign, origin_country, first/last seen, first/last lat/lon/altitude/heading, velocity, vertical_rate, squawk, on_ground
  - 5-minute gap between observations = new sighting row (enables flight history analysis)
  - Indexes: icao24, callsign, first_seen, last_seen, last_lat+lon
  - 501+ sightings as of 2026-03-01, 195 unique aircraft (accumulates in real-time)
  - Real-time: proxy writes to DB on every aircraft API response (cache hits and misses)
- **Automated POI import** (v1.5.49, updated v1.5.60): buffer-drain upsert every 15min
  - Overpass responses buffered in lightweight import buffer; drained and deduped on each import cycle
  - Batches of 500, per-batch transactions, mutex against overlap
  - `POST /db/import` (manual trigger), `GET /db/import/status` (read-only status)
  - Stats in `/cache/stats` → `dbImport` object
- **DB query API** (`/db/*` prefix): 14 endpoints — 8 POI + 4 aircraft + 2 import
  - `GET /db/pois/search` — smart fuzzy search (pg_trgm similarity + ILIKE), keyword→category hints, **cuisine-aware** (tags->>'cuisine'), distance expansion, composite scoring
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
- **Social tables** (v1.5.45): `users`, `auth_lookup`, `refresh_tokens`, `poi_comments`, `comment_votes`, `chat_rooms`, `room_memberships`, `messages`
  - All granted to `witchdoctor` user; DDL runs as `sudo -u postgres`
- Import: automated every 15min when `DATABASE_URL` set (manual: `node import-pois.js` or `POST /db/import`)
- Proxy startup: `DATABASE_URL=... JWT_SECRET=... OPENSKY_CLIENT_ID=... OPENSKY_CLIENT_SECRET=... node server.js`
  - Without DATABASE_URL: proxy starts normally, `/db/*` and social endpoints return 503, auto-import disabled
  - Without JWT_SECRET: random secret generated (tokens don't survive restarts)
  - Without OPENSKY_*: aircraft requests use anonymous access (100 req/day)

## GPS Centering
- Auto-centers on **first** GPS fix only (`initialCenterDone` flag) at **zoom 18**; follow mode still pans

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

## POI Marker Memory Management
- Viewport-only display: `replaceAllPoiMarkers()` on bbox refresh, ~100-400 markers (was 22k)
- Category toggles control *searching*, not *display* — display always from `/pois/bbox`
- LRU icon cache capped at 500 entries

## Known Issues
- MBTA API key hardcoded in MbtaRepository.kt (should be in BuildConfig/secrets)
- Windy Webcams API key hardcoded in server.js (free tier)
- 10.0.0.4 proxy IP hardcoded (works on local network only)
- OpenSky state vector: category field (index 17) not always present — guarded with size check
- **Overpass intermittent 504s** (observed 2026-03-04): FIXED — proxy now retries with 3-endpoint rotation + exponential backoff; app PlacesRepository retries up to 3 times with 5s/10s delays

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

### Full Suite Results (2026-03-01): 103 PASS, 0 FAIL
- Overnight 5.5hrs + Morning 1hr — no leaks, no failures, memory 9-27MB steady
- See `overnight-runs/` for detailed reports

## Completed Plans
- **Geofence Alert System** (v1.5.35–v1.5.39): Phases 1-4 done. See `PLAN-ARCHIVE.md`.
- **Social Layer** Phases A-C (v1.5.45–v1.5.47): Auth + Comments + Chat done. Phase D (mod tools) not started. See `GOVERNANCE.md`.
- **Code Decomposition** (2026-03-04): 3-phase refactoring complete. See `REFACTORING-REPORT.txt`.
  - Phase 1: server.js (3,925 → 156 lines + 18 modules in lib/)
  - Phase 2: MainViewModel.kt (958 → 215 lines + 6 domain ViewModels)
  - Phase 3: AppBarMenuManager.kt (879 → 812 lines + MenuPrefs.kt)
  - Prior: MainActivity.kt (9,577 → 1,996 lines + 13 extension-function files)

## Commercialization
- **`COMMERCIALIZATION.md`** v2.0 — lawyer-ready edition (1,897 lines, 27 sections, created 2026-03-04)
  - **Part A** (§1–4): Product description, freemium revenue model (free+ads / paid tier), data flow diagram, executive risk summary with probability scoring
  - **Part B** (§5–17): Finding an attorney, business entity (Wyoming LLC), insurance, IP protection, user content liability (Section 230, DMCA, Take It Down Act), privacy (CCPA + 21 states + GDPR/UK/international roadmap), API licensing (**OpenSky BLOCKING**), dependency inventory (22 Android + 12 Node.js libs), ad network compliance (AdMob), Play Billing, tax considerations, social media integration, safety disclaimers, competitor analysis (8 comparable apps)
  - **Part C** (§18–27): Content moderation, legal documents, Play Store requirements, account management, APK protection, cloud deployment, cost summary ($4,803–$11,480 Year 1), risk matrix (14 risks scored by probability×impact), 17 prioritized attorney questions, master checklist (10 phases, ~70 action items)

## Next Steps
- **Commercialization blockers**: Find attorney (see §5), OpenSky commercial license, LLC formation, insurance, attorney review of ToS/Privacy Policy
- **Monetization**: AdMob integration, Google Play Billing for subscriptions, freemium tier gating
- Social: Phase D (room management), content moderation system (reporting, flagging, moderation queue)
- Cloud migration: move proxy + PostgreSQL to cloud, replace hardcoded 10.0.0.4 with domain
- Find dialog: pagination, recent searches, search history
- Privacy: user data export endpoint, account deletion/anonymization, age gate
- Google Play: R8 obfuscation, Data Safety section, prominent location disclosure
