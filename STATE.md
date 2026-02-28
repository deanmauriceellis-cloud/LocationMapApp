# LocationMapApp v1.5 — Project State

## Last Updated: 2026-02-28 Session 8 (Viewport-only POI markers with eviction, LRU icon cache)

## Architecture
- **Android app** (Kotlin, Hilt DI, OkHttp, osmdroid) targeting API 34
- **Cache proxy** (Node.js/Express on port 3000) — transparent caching layer between app and external APIs
- **PostgreSQL** (`locationmapapp` DB) — permanent storage for POIs and aircraft sightings
- **Split**: App → Cache Proxy → External APIs (Overpass, NWS, Aviation Weather, MBTA, OpenSky)

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
- All layers default ON on fresh install (POIs, MBTA, radar, METAR)
- Weather alerts (NWS active alerts)
- METAR stations with rich text markers — temp (°F), wind arrow+speed, sky/wx, flight-category color
  - Bbox passthrough to Aviation Weather API, cached per-bbox
  - Deferred load — waits for GPS fix so map has a valid bounding box
  - Tap shows human-readable decoded METAR (compass wind, decoded sky/wx, flight category explained)
  - Handles HTTP 204 (no content) gracefully
- NWS NEXRAD radar tiles (Iowa State Mesonet — replaced RainViewer)
- MBTA live vehicles: buses, commuter rail, subway (with auto-refresh)
  - Directional arrows on vehicle markers showing bearing/heading
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
  - FAB speed dial toggle + GPS Alerts menu toggle and frequency slider
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
- Vehicle follow mode: tap a bus/train → map tracks it, banner shows status
- POI prefetch along followed vehicle/aircraft routes
- Cached POI coverage display: proxy `/pois/bbox` endpoint returns POIs within visible map area
  - Loads on startup, refreshes on scroll/zoom (500ms debounce), refreshes after follow prefetch
- Adaptive POI search radius — proxy stores per-grid-cell hints, app fetches/reports
- Individual POI cache (poi-cache.json) — deduped by OSM type+id, with first/last seen timestamps
- POI database (PostgreSQL) — permanent storage with JSONB tags, category indexing, upsert import
- Cache proxy with disk persistence (cache-data.json, radius-hints.json, poi-cache.json, 365-day Overpass TTL)
- Debug logging + TCP log streamer

## External API Routing
| API | App Endpoint | Proxy Route | TTL |
|-----|-------------|-------------|-----|
| Overpass POI | `http://10.0.0.4:3000/overpass` | POST /overpass | 365 days |
| NWS Alerts | `http://10.0.0.4:3000/nws-alerts` | GET /nws-alerts | 1 hour |
| METAR | `http://10.0.0.4:3000/metar?bbox=...` | GET /metar?bbox=s,w,n,e | 1 hour (per bbox) |
| Radius Hints | `http://10.0.0.4:3000/radius-hint` | GET+POST /radius-hint | persistent |
| POI Cache | `http://10.0.0.4:3000/pois/...` | GET /pois/stats, /pois/export, /pois/bbox, /poi/:type/:id | persistent |
| Aircraft | `http://10.0.0.4:3000/aircraft?...` | GET /aircraft?bbox=s,w,n,e or ?icao24=hex | 15 seconds |
| DB POI Query | `http://10.0.0.4:3000/db/pois/...` | GET /db/pois/search, /nearby, /stats, /categories, /coverage | live |
| DB POI Lookup | `http://10.0.0.4:3000/db/poi/...` | GET /db/poi/:type/:id | live |
| MBTA | direct (api-v3.mbta.com) | not proxied | — |
| Radar tiles | direct (mesonet.agron.iastate.edu) | not proxied | — |

## Map Interaction Model
- **Single tap**: no action
- **Long press (~2s)**: enter manual mode, center map, search POIs at location
- **Scroll/pan**: displays cached POIs for visible area via proxy `/pois/bbox`
- **Tap vehicle marker**: follow mode (map tracks vehicle, banner shows status/speed)
- **Tap aircraft marker**: follow mode (map tracks globally via icao24, banner shows flight info)
- **Tap follow banner**: stop following

## Key Files
- `app/src/main/java/.../ui/MainActivity.kt` — main map activity, all overlays
- `app/src/main/java/.../ui/MainViewModel.kt` — LiveData, data fetching
- `app/src/main/java/.../ui/MarkerIconHelper.kt` — icon/dot rendering with cache
- `app/src/main/java/.../ui/menu/PoiCategories.kt` — central config for all 16 POI categories
- `app/src/main/java/.../data/repository/PlacesRepository.kt` — Overpass POI search
- `app/src/main/java/.../data/repository/WeatherRepository.kt` — NWS + METAR
- `app/src/main/java/.../data/repository/AircraftRepository.kt` — OpenSky aircraft
- `app/src/main/java/.../data/repository/MbtaRepository.kt` — MBTA vehicles
- `cache-proxy/server.js` — Express caching proxy
- `cache-proxy/cache-data.json` — persistent cache (gitignored)
- `cache-proxy/radius-hints.json` — adaptive radius hints per grid cell (gitignored)
- `cache-proxy/poi-cache.json` — individual POI cache, deduped by type+id (gitignored)
- `cache-proxy/schema.sql` — PostgreSQL schema for permanent POI storage
- `cache-proxy/import-pois.js` — standalone script to import POIs from proxy into PostgreSQL

## PostgreSQL Database
- Database: `locationmapapp`, user: `witchdoctor`
- **`pois` table**: Composite PK `(osm_type, osm_id)`, JSONB tags (GIN index), promoted name/category columns
  - Indexes: category, name (partial), tags (GIN), lat+lon (compound)
  - 22,494 POIs as of 2026-02-28 (synced via `import-pois.js`)
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
- Client credentials flow: `OPENSKY_CLIENT_ID` + `OPENSKY_CLIENT_SECRET` env vars
- Token endpoint: `auth.opensky-network.org/.../token` (30-min expiry, auto-refresh with 5-min buffer)
- Authenticated: 4,000 req/day (vs 100 anonymous)

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
- 10.0.0.4 proxy IP hardcoded (works on local network only)
- OpenSky state vector: category field (index 17) not always present — guarded with size check

## Next Steps
- Test viewport-only POI eviction on emulator — verify no OOM after extended run
- Test vehicle follow mode across full bus routes
- Monitor cache growth and hit rates over time
- Evaluate proxy → remote deployment for non-local testing
- Automate periodic POI imports (cron or proxy hook)
- Add aircraft query endpoints to /db/* API (search by callsign, flight path analysis)
