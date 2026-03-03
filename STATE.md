# LocationMapApp v1.5 — Project State

## Last Updated: 2026-03-03 Session 47 (POI Marker Click-Through, Device-Bonded Auth, Chat Testing)

## Architecture
- **Android app** (Kotlin, Hilt DI, OkHttp, osmdroid) targeting API 34
- **Cache proxy** (Node.js/Express on port 3000) — transparent caching layer between app and external APIs
- **PostgreSQL** (`locationmapapp` DB) — permanent storage for POIs and aircraft sightings
- **Split**: App → Cache Proxy → External APIs (Overpass, NWS, Aviation Weather, MBTA, OpenSky, Windy Webcams)

## What's Working
- Map display (osmdroid), GPS tracking with manual override (long-press), custom zoom slider
- **17 POI categories** with submenu refinement (central config in `PoiCategories.kt`)
  - 5dp colored dots; zoom ≥ 18: labeled icons with type + name
  - Layer-aware LiveData `Pair<String, List>`, viewport-only display via `/pois/bbox`
  - All layers default ON except aircraft (OFF)
- **Weather dialog** (v1.5.34): current conditions, 48-hour hourly, 7-day outlook, expandable alerts
  - Proxy `/weather?lat=&lon=` (5 NWS calls), 22 vector icons, auto-fetch every 30min
  - Toolbar icon shows current conditions; red border when alerts active
- **METAR** — rich text markers (temp, wind, sky), bbox passthrough, deferred load, human-readable tap info
- **NWS NEXRAD radar** tiles (Iowa State Mesonet) + animated radar (7-frame 35-min loop)
- **Dark mode** (v1.5.44): toolbar moon icon toggles between MAPNIK and CartoDB Dark Matter tiles, persisted
- **Share POI** (v1.5.44): 5th action button in POI detail dialog, shares name/address/phone/hours + Google Maps link
- **Favorites** (v1.5.44): star icon in POI detail dialog, SharedPreferences+JSON storage, dedicated Favorites cell in Find dialog
- **Text search** (v1.5.44): search bar in Find dialog, debounced name search via `/db/pois/search`, color dot + distance results
- **MBTA transit** — live vehicles (buses, CR, subway) with directional arrows + staleness detection
  - Commuter rail: next-stop ETA badge on labeled markers (batch predictions API)
  - ~270 train stations: tap → arrival board (30s auto-refresh) → trip schedule
  - ~7,900 bus stops: viewport-filtered, zoom ≥ 15, tap → arrival board
  - Vehicle detail dialog: info + Follow/View Route/Arrivals buttons
- **Aircraft tracking** (OpenSky) — rotated airplane icons, callsign, altitude-colored, SPI emergency
  - 18 state vector fields, configurable refresh (30s–5min), zoom ≥ 10 guard
  - Dedicated **Air** menu (toggle, frequency, auto-follow)
- **Slim toolbar + status line + grid dropdown** (v1.5.40): replaced 2×5 icon grid with compact 3-icon bar
  - **Toolbar** (40dp): Weather icon (left) | spacer | Dark Mode toggle | Alerts icon + Grid menu button (right)
  - **Status line** (24dp): priority-based info bar — GPS coords+weather when idle, follow/scan/alert info when active
    - `StatusLineManager.kt`: 7 priority levels (GPS_IDLE → GEOFENCE_ALERT), set/clear/updateIdle API
    - All banner functions migrated from dynamic TextView creation to StatusLineManager
    - Geofence alerts show zone-type-colored background on status line
  - **Grid dropdown**: PopupWindow with 8 labeled buttons (icon+text) in 2×4 grid
    - Row 1: Transit, Webcams, Aircraft, Radar | Row 2: POI, Utility, Find, Go To
  - `setupSlimToolbar()` + `showGridDropdown()` in AppBarMenuManager; `fitsSystemWindows` on AppBarLayout
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
  - **Skipped for now**: COPPA age gate, email encryption, content moderation, OAuth, monthly partitioning
- **Cap detection & retry-to-fit**: halves radius on 500-element cap, 20km fuzzy hints, MIN_RADIUS 100m
- **Populate POIs v2** (grid scanner): probe-calibrate-spiral with recursive 3×3 subdivision, 10km initial probe, narrative banner
- **10km Probe Populate** (v1.5.42): expanding spiral of 10km wide probes for low-density POI discovery
  - Crosshairs pan (no zoom), 30s countdown, status line with last probe count + recommended fill radius
  - `estimateFillRadius()`: targets ~200 POIs per search area, scales by density
  - Fill Probe Populate: stub for future implementation
- **Vehicle follow**: tap → track, staleness detection (>2 min), POI prefetch along route
- **Find dialog** (v1.5.26): category grid → subtype grid → distance-sorted results, long-press filter mode
  - Counts scoped to 10km radius around map center; auto-fit cell heights for all screen sizes
  - Text search bar (v1.5.44): debounced name search above category grid, 500ms delay, min 2 chars
  - Favorites cell (v1.5.44): gold star, first in grid, shows count badge, tap for sorted favorites list
- **POI Detail Dialog** (v1.5.27): info rows, website (3-tier waterfall), action buttons (Directions/Call/Reviews/Map/Share)
  - Tap any POI marker on map → opens detail dialog directly (v1.5.46)
  - Star icon in header (v1.5.44): tap to add/remove from favorites, filled/outline toggle
- **Legend dialog** (v1.5.25): 7 sections, Utility menu, driven from `PoiCategories.ALL`
- Transit zoom guard (zoom ≤ 10 hides markers), POI display (zoom ≥ 10 + max 5000 markers), adaptive radius hints
- **Idle auto-populate** (v1.5.33): 10-min GPS stationarity → full scanner, 45s delays, GPS-centered
  - Touch-to-stop: any map tap cancels idle populate, resets 10-min idle timer
  - Any UI activity (grid dropdown, dialogs, toolbar buttons) also resets idle timer
  - State preservation: stopped idle scanner resumes from last ring/point (not from scratch)
  - State cleared on: long-press, GPS move >100m, goToLocation, manual populate start
- **Overpass queue**: serialized upstream, 10s min gap, per-client fair queue, covering cache, content hash delta
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
- **Long press (~2s)**: enter manual mode, center map (auto-zoom to 14 if <14), search POIs at location, fetch weather + alerts
- **Scroll/pan**: displays cached POIs for visible area via proxy `/pois/bbox`
- **Tap vehicle marker**: vehicle detail dialog (route, status, speed) with Follow / View Route / Arrivals buttons
- **Tap station marker**: arrival board dialog (real-time predictions), tap train → trip schedule dialog
- **Tap bus stop marker**: arrival board dialog (real-time bus predictions)
- **Tap aircraft marker**: follow mode (map tracks globally via icao24, banner shows flight info)
- **Tap follow/populate banner**: stop following or stop populate scan
- **Tap find filter banner**: exit filter mode, restore normal POI display
- **Find toolbar icon**: search bar + favorites cell + category grid → subtype grid → distance-sorted results → tap to open POI detail dialog
- **Go to Location toolbar icon**: geocoder dialog → type address → pick result → map navigates + POI search
- **POI detail dialog**: info rows + star (favorite) + Load Website button + action buttons (Directions, Call, Reviews, Map, Share)
- **Find long-press**: filter map to show only that category's POIs
- **Utility → Populate POIs**: systematic grid scanner spirals from map center

## Key Files
- `app/src/main/java/.../ui/MainActivity.kt` — main map activity, all overlays
- `app/src/main/java/.../ui/MainViewModel.kt` — LiveData, data fetching
- `app/src/main/java/.../ui/MarkerIconHelper.kt` — icon/dot rendering with cache
- `app/src/main/java/.../ui/WeatherIconHelper.kt` — NWS icon code → drawable mapping
- `app/src/main/java/.../ui/StatusLineManager.kt` — priority-based toolbar status line manager
- `app/src/main/java/.../ui/menu/PoiCategories.kt` — central config for all 16 POI categories
- `app/src/main/java/.../data/repository/PlacesRepository.kt` — Overpass POI search (injects `@ApplicationContext` for X-Client-ID)
- `app/src/main/java/.../data/repository/WeatherRepository.kt` — NWS + METAR
- `app/src/main/java/.../data/repository/AircraftRepository.kt` — OpenSky aircraft
- `app/src/main/java/.../data/repository/MbtaRepository.kt` — MBTA vehicles
- `app/src/main/java/.../data/repository/FindRepository.kt` — Find dialog DB queries + text search
- `app/src/main/java/.../util/FavoritesManager.kt` — SharedPreferences+JSON favorites CRUD
- `app/src/main/java/.../data/repository/WebcamRepository.kt` — Windy webcams
- `app/src/main/java/.../data/repository/TfrRepository.kt` — FAA TFR fetch via proxy
- `app/src/main/java/.../data/repository/GeofenceRepository.kt` — speed cameras, schools, flood zones, crossings fetch
- `app/src/main/java/.../data/repository/GeofenceDatabaseRepository.kt` — downloadable geofence DB catalog, download, SQLite loading, import/export, CSV parsing
- `app/src/main/java/.../util/GeofenceEngine.kt` — JTS R-tree spatial index + multi-zone geofence alerting
- `app/src/main/java/.../data/repository/AuthRepository.kt` — JWT auth, token storage, auto-refresh
- `app/src/main/java/.../data/repository/CommentRepository.kt` — POI comments CRUD
- `app/src/main/java/.../data/repository/ChatRepository.kt` — Socket.IO chat + REST rooms/messages
- `cache-proxy/server.js` — Express caching proxy + auth + comments + chat (Socket.IO)
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
  - 89,622 POIs as of 2026-03-02 (re-imported after multi-region scanning)
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
- **Social tables** (v1.5.45): `users`, `auth_lookup`, `refresh_tokens`, `poi_comments`, `comment_votes`, `chat_rooms`, `room_memberships`, `messages`
  - All granted to `witchdoctor` user; DDL runs as `sudo -u postgres`
- Import: `DATABASE_URL=postgres://witchdoctor:fuckers123@localhost/locationmapapp node import-pois.js`
- Proxy startup: `DATABASE_URL=... JWT_SECRET=... OPENSKY_CLIENT_ID=... OPENSKY_CLIENT_SECRET=... node server.js`
  - Without DATABASE_URL: proxy starts normally, `/db/*` and social endpoints return 503
  - Without JWT_SECRET: random secret generated (tokens don't survive restarts)
  - Without OPENSKY_*: aircraft requests use anonymous access (100 req/day)

## GPS Centering
- Auto-centers on **first** GPS fix only (`initialCenterDone` flag); follow mode still pans

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

## Completed Plan: Geofence Alert System
Roadmap completed across v1.5.35–v1.5.39. See `PLAN-ARCHIVE.md` for full details.
- Phase 1: Core Engine + TFRs (v1.5.35)
- Phase 2: Additional Zone Types (v1.5.36)
- Phase 3A: Downloadable Database Infrastructure (v1.5.37)
- Phase 3B: Additional Database Builders (v1.5.38)
- Phase 4: Database Import & Export (v1.5.39)
- Phase 5: Advanced Sources — deferred (sufficient coverage with current sources)

## Active Plan: Social Layer
See `SOCIAL-PLAN.md` for full plan. Current status:
- **Phase A (Auth)**: DONE — register/login, JWT, profile, 3×4 grid
- **Phase B (Comments)**: DONE — POI comments, ratings, votes
- **Phase C (Chat)**: DONE — real-time Socket.IO chat, rooms
- **Phase D (Room Management)**: NOT STARTED — mod tools, bans, room settings
- **Governance checkpoints**: NOT STARTED — COPPA, encryption, moderation (see GOVERNANCE.md)
- **DDL done** — 8 tables created, testing in progress (see CURRENT_TESTING.md)
- **Auth model**: device-bonded (register once, no login/logout, 365d refresh tokens)

## Other Next Steps
- Monitor cache growth and hit rates over time
- Evaluate proxy → remote deployment for non-local testing
- Automate periodic POI imports (cron or proxy hook)
- Find dialog enhancements: pagination (load more), search within results, recent searches
