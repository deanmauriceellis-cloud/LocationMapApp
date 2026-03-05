# LocationMapApp — Changelog

> Releases prior to v1.5.51 archived in `CHANGELOG-ARCHIVE.md`.

## [1.5.65] — 2026-03-05

### Added
- **Web app Phase 4** — Aircraft tracking + MBTA transit layers with real-time data
  - **Aircraft markers**: DivIcon with rotated airplane SVG, altitude-colored (ground=gray, <5kft=green, 5-20kft=blue, >20kft=purple), callsign labels
  - **Aircraft detail panel**: altitude/speed/heading/squawk info, follow button (centers map on aircraft each 15s refresh), sighting history from DB
  - **Flight trail**: altitude-colored polyline segments from flight path history
  - **Transit vehicle markers**: route-colored CircleMarkers for trains, subway, and buses with labels at zoom >= 16
  - **Station markers**: rail station dots at zoom >= 12, bus stop dots at zoom >= 15 (max 200 per viewport, bbox-filtered with sampling)
  - **Arrival/departure board**: click station → dark-themed panel with route-colored prediction rows, DEP/ARR labels, "service ended" message when no predictions
  - **Vehicle detail panel**: route-colored header, status, next 5 stops along route with arrival times (trip-based predictions)
  - **Vehicle follow mode**: click Follow → map centers on vehicle position each refresh cycle, selected vehicle gets teal border ring + permanent detail label
  - **Layers dropdown**: toolbar button with 4 toggle switches (Aircraft/Trains/Subway/Buses) and count badges
  - **Server-side POI clustering**: >1000 POIs triggers SQL grid aggregation (~77 translucent cluster circles vs 28k individual markers)
  - **Detail panel mutual exclusion**: POI/Aircraft/Vehicle/ArrivalBoard — only one shows at a time
  - **Auto-refresh**: aircraft 15s, trains/subway 15s, buses 30s, predictions 30s
  - **Status bar**: per-layer counts (aircraft/trains/subway/buses) alongside POI count

### Proxy Endpoints Added (5)
- `GET /mbta/vehicles?route_type=X` — flat vehicle list with tripId, 15s cache
- `GET /mbta/stations?route_type=X` — flat station list, 1h cache
- `GET /mbta/predictions?stop=X&stop_name=X` — station predictions (resolves all platforms by name), 30s cache
- `GET /mbta/trip-predictions?trip=X` — next 5 stops for a vehicle's trip, 30s cache
- `GET /mbta/bus-stops/bbox?s=&w=&n=&e=` — bus stops in viewport, max 200 with sampling

### Proxy Changes
- `cache-proxy/lib/pois.js` — `/pois/bbox` now returns server-side clusters when >1000 POIs (SQL grid aggregation with MODE() dominant category)
- `cache-proxy/lib/mbta.js` — JSON:API flattening helpers, 5 new endpoints, prediction null-time filtering + time-based sorting

### Files Created (12)
- `web/src/config/aircraft.ts` — altitude colors, unit converters, SVG icon factory
- `web/src/config/transit.ts` — MBTA route colors, status/type labels
- `web/src/hooks/useAircraft.ts` — aircraft state, auto-refresh, history, follow
- `web/src/hooks/useTransit.ts` — trains/subway/buses/stations/busStops, follow, trip predictions
- `web/src/components/Map/AircraftMarkerLayer.tsx` — rotated airplane DivIcons
- `web/src/components/Map/FlightTrailLayer.tsx` — altitude-colored polylines
- `web/src/components/Map/TransitMarkerLayer.tsx` — route-colored CircleMarkers + station/bus stop dots
- `web/src/components/Aircraft/AircraftDetailPanel.tsx` — aircraft info + follow
- `web/src/components/Transit/VehicleDetailPanel.tsx` — vehicle info + next stops
- `web/src/components/Transit/ArrivalBoardPanel.tsx` — station departures & arrivals board
- `web/src/components/Layout/LayersDropdown.tsx` — layer toggle dropdown

### Files Modified (8)
- `web/src/lib/types.ts` — AircraftState, AircraftHistory, MbtaVehicle (with tripId), MbtaStop, MbtaPrediction (with stopName/stopSequence)
- `web/src/hooks/usePois.ts` — server-side cluster support (PoiCluster type)
- `web/src/App.tsx` — aircraft/transit hooks, layers dropdown, detail mutual exclusion, follow effects, bus stops fetch
- `web/src/components/Map/MapView.tsx` — aircraft/transit/cluster layer integration
- `web/src/components/Map/PoiMarkerLayer.tsx` — cluster rendering mode (translucent circles with count labels)
- `web/src/components/Layout/Toolbar.tsx` — Layers button with active count badge
- `web/src/components/Layout/StatusBar.tsx` — per-layer vehicle counts
- `web/src/index.css` — aircraft-label, transit-label, cluster-label CSS (dark mode variants)

### Bug Fixes
- DivIcon transit markers not rendering → replaced with CircleMarker approach (same as POIs/METARs)
- MBTA stations returning 0 results (location_type=1 filter incorrect) → removed filter
- Page unresponsive with 28k POIs → server-side SQL grid clustering
- Bus stop predictions failing (stop_name resolver only checked rail stations cache) → added fetchPredictionsById for bus stops
- Selected vehicle permanent label not showing → force remount via key change on selection
- Prediction null-time entries sorting first → filtered out + sorted by earliest available time

## [1.5.63] — 2026-03-05

### Added
- **Web app Phase 3** — Weather overlay, METAR markers, radar, alert notifications
  - **Weather panel**: slide-in 360px panel with Current/Hourly/Daily tabs, expandable alert banners
  - **Current tab**: large temp + SVG weather icon, description, 2×3 detail grid (humidity, wind, visibility, barometer, dewpoint, windchill/heat index)
  - **Hourly tab**: scrollable 48-hour forecast — time, icon, temp, precip%, wind
  - **Daily tab**: 7-day forecast cards — name, icon, temp, precip%, wind, short forecast
  - **Alert banners**: red/orange expandable sections with severity-aware colors, headline, area, instruction, description
  - **Weather icons**: 15 SVG icon variants (sun, moon, clouds, rain, thunderstorm, snow, fog, wind) with day/night variants
  - **METAR markers**: flight-category colored circles (VFR green, MVFR blue, IFR red, LIFR magenta), monospace labels at zoom >= 10
  - **Radar overlay**: RainViewer API tiles at 35% opacity, direct from CDN (no proxy needed)
  - **Animated radar**: 7-frame loop at 800ms/frame using RainViewer historical frames, smooth opacity cycling via Leaflet API
  - **Layer controls**: Radar/Animate/METAR toggle buttons at bottom of weather panel
  - **Toolbar weather button**: dynamic SVG icon showing current conditions, red dot when alerts active
  - **Status bar alert banner**: red bar with alert event name + count, click opens weather panel
  - **Mutual exclusion**: Weather and Find panels share position, opening one closes the other
  - **Auto-refresh**: weather data refreshes every 5 minutes while panel is open
  - **METAR bounds fetch**: fetches METARs on map move when layer enabled

### Files Created (5)
- `web/src/hooks/useWeather.ts` — weather/METAR fetch, radar/metar toggles, 5-min auto-refresh
- `web/src/config/weatherIcons.ts` — NWS icon code → SVG mapping (~25 codes, day/night)
- `web/src/components/Weather/WeatherPanel.tsx` — slide-in panel: tabs, alerts, layer controls
- `web/src/components/Map/RadarLayer.tsx` — RainViewer radar tiles + 7-frame animation via Leaflet API
- `web/src/components/Map/MetarMarkerLayer.tsx` — flight-category colored CircleMarkers + monospace labels

### Files Modified (6)
- `web/src/lib/types.ts` — WeatherData, WeatherCurrent/Hourly/Daily, WeatherAlert, WeatherLocation, MetarStation
- `web/src/App.tsx` — weather state, mutual exclusion, METAR bounds fetch, alert click, stable callback refs
- `web/src/components/Layout/Toolbar.tsx` — weather button with dynamic icon + alert dot indicator
- `web/src/components/Layout/StatusBar.tsx` — red alert banner with event name + click handler
- `web/src/components/Map/MapView.tsx` — RadarLayer + MetarMarkerLayer integration
- `web/src/index.css` — `.metar-label` styles (monospace, dark mode)

### Bug Fixes
- Fixed `handleBoundsChange` infinite re-render loop caused by `wx` object dependency — use stable individual function refs
- Switched from Iowa State Mesonet animated tiles (404 errors) to RainViewer API (provides frame manifest)

## [1.5.62] — 2026-03-04

### Added
- **Web app Phase 2** — Find dialog, fuzzy search, and POI detail panel
  - **Find panel**: slide-in 360px panel with search bar, 4-column category grid with count badges (10km radius), subtype drill-down, results list
  - **Fuzzy search**: 1s debounced search via `/db/pois/search`, min 2 chars, AbortController cancellation
  - **Category browse**: tap category → subtype grid (or direct results if no subtypes), "Browse All" option
  - **Results list**: distance (ft/mi) + category color dot + bold name + detail line (cuisine/brand) + category label
  - **Filter and Map**: button at top of results, shows only matching POIs with forced labels, teal status bar "click to clear"
  - **POI detail panel**: category color bar, info rows (distance, type, cuisine, address, phone, hours), async website resolution
  - **Action buttons**: Directions (Google Maps), Call (tel: link), Map (fly to zoom 18), Share (Web Share API)
  - **POI marker click**: tap any marker on map → opens detail panel with haversine distance
  - **Toolbar Find button**: magnifying glass icon with teal active highlight
  - **Category utilities**: `resolveCategory()` handles both ID and tag-string formats, count aggregation from tag→category

### Files Created (5)
- `web/src/lib/distance.ts` — `haversineM()` + `formatDistance()` (ft/mi)
- `web/src/hooks/useFind.ts` — search, findByCategory, loadCounts, fetchWebsite, fetchPoiDetail
- `web/src/components/Find/FindPanel.tsx` — search + category grid + subtype grid + results + Filter and Map
- `web/src/components/Find/ResultsList.tsx` — shared result rows with distance + color dot + category
- `web/src/components/Find/PoiDetailPanel.tsx` — detail panel with info rows + website + action buttons

### Files Modified (7)
- `web/src/lib/types.ts` — added FindResult, WebsiteInfo, PoiDetailResponse interfaces; id types widened to `string | number`
- `web/src/config/categories.ts` — added `resolveCategory()`, `getCategoryByTag()`, `getCategoryTags()`, `getSubtypeTags()`
- `web/src/components/Layout/Toolbar.tsx` — Find button with active highlight
- `web/src/components/Layout/StatusBar.tsx` — filter mode: teal bar with count + label + click to clear
- `web/src/components/Map/PoiMarkerLayer.tsx` — click handlers + filter mode (forced labels, filtered markers only)
- `web/src/components/Map/MapView.tsx` — passes through filterResults + onPoiClick props
- `web/src/App.tsx` — full orchestration: findOpen, selectedResult, filterResults state; Find/Detail mutual exclusion

## [1.5.61] — 2026-03-04

### Added
- **Web application** (Phase 1) — React 19 + TypeScript + Vite + Leaflet frontend consuming existing proxy API
  - Interactive map with OpenStreetMap (light) and CartoDB Dark Matter (dark) tile layers
  - POI markers from `/pois/bbox` with all 17 category colors matching Android app
  - Zoom >= 16 shows POI name labels (same threshold as Android)
  - Dark mode toggle persisted to localStorage
  - Browser geolocation with Boston fallback
  - Debounced (300ms) viewport-based POI loading
  - Status bar: coordinates + viewport POI count + total DB count
  - Toolbar: app name + dark mode toggle
  - Zoom/locate controls
  - Full POI category classification system (`classifyPoi()`) ported from `PoiCategories.kt`
  - TypeScript strict mode, zero compilation errors
  - Vite production build: 368KB JS (112KB gzipped)
- **CORS middleware** on cache proxy — `cors({ origin: true, credentials: true })` enables web app cross-origin requests
  - New dependency: `cors ^2.8.5`

### Files Created (20 web app files)
- `web/package.json`, `web/tsconfig.json`, `web/vite.config.ts`, `web/tailwind.config.ts`, `web/postcss.config.js`
- `web/index.html`, `web/.env.development`, `web/public/favicon.svg`, `web/public/manifest.json`
- `web/src/main.tsx`, `web/src/App.tsx`, `web/src/index.css`, `web/src/vite-env.d.ts`
- `web/src/config/api.ts`, `web/src/config/categories.ts`, `web/src/lib/types.ts`
- `web/src/hooks/useGeolocation.ts`, `web/src/hooks/usePois.ts`, `web/src/hooks/useDarkMode.ts`
- `web/src/components/Map/MapView.tsx`, `web/src/components/Map/PoiMarkerLayer.tsx`, `web/src/components/Map/MapControls.tsx`
- `web/src/components/Layout/Toolbar.tsx`, `web/src/components/Layout/StatusBar.tsx`

### Files Modified (2 proxy files)
- `cache-proxy/server.js` — added `cors` require + middleware
- `cache-proxy/package.json` — added `cors ^2.8.5` dependency

## [1.5.60] — 2026-03-04

### Changed
- **Proxy heap reduction** (643MB → 208MB) — eliminated `poiCache` in-memory Map (268k entries, ~90MB) and capped main Overpass cache with LRU eviction (7,214 → 2,000 entries)
  - All POI endpoints (`/pois/stats`, `/pois/export`, `/pois/bbox`, `/poi/:type/:id`) now query PostgreSQL directly instead of in-memory Map
  - `collectPoisInRadius()` (scan cell CELL hits) now queries PostgreSQL instead of iterating poiCache
  - `poi-cache.json` (90MB disk file) eliminated — no longer generated or loaded
  - `cache-data.json` shrunk from 320MB → 57MB via LRU eviction
  - New import buffer: Overpass responses buffered in lightweight array, drained every 15min by DB import
  - LRU cache cap: `MAX_CACHE_ENTRIES` env var (default 2000), oldest entries evicted on insert and on startup load
  - `/cache/stats` shows `maxCacheEntries`, `importBufferPending` (replaces `pois` count)
  - `/db/import/status` shows `pendingDelta` from buffer (replaces poiCache iteration)
  - Zero app-side changes — all response formats preserved

### Files Modified (7 proxy files)
- `cache-proxy/lib/cache.js` — removed poiCache + added import buffer + LRU eviction
- `cache-proxy/lib/overpass.js` — wired bufferOverpassElements, await async collectPoisInRadius
- `cache-proxy/lib/scan-cells.js` — PostgreSQL collectPoisInRadius
- `cache-proxy/lib/pois.js` — all 4 POI endpoints → async PostgreSQL queries
- `cache-proxy/lib/import.js` — drainImportBuffer + dedupe instead of poiCache delta
- `cache-proxy/lib/admin.js` — importBuffer + MAX_CACHE_ENTRIES stats, removed poiCache refs
- `cache-proxy/server.js` — updated deps wiring

## [1.5.59] — 2026-03-04

### Added
- **Scan cell coverage tracking** — proxy divides the world into ~1.1km grid cells (2 decimal places of lat/lon), tracks when each was last successfully scanned via Overpass and how many POIs it contains
  - `cache-proxy/lib/scan-cells.js` — new module with persistence to `scan-cells.json`
  - Decision flow: exact cache → covering cache → cache-only merge → **scan cell FRESH → serve from poiCache** → upstream
  - `X-Cache: CELL` header when served from scan cell coverage (instant ~10ms response)
  - `X-Coverage: FRESH/STALE/EMPTY` header on all `/overpass` responses
  - Config: `SCAN_FRESHNESS_MS` (default 24h), `MIN_COVERAGE_POIS` (default 10)
  - Debug: `GET /scan-cells` dumps all cells; `GET /scan-cells?lat=X&lon=Y` checks specific cell
  - Admin: scan cell count in `/cache/stats`, cleared by `/cache/clear`
- **Overpass queue cancel** — `POST /overpass/cancel` flushes all queued requests for a client ID
  - Called automatically on: stop following, stop populate, stop idle populate, stop probe 10km
  - Prevents wasted upstream Overpass calls when user navigates away

### Changed
- **Idle populate skips FRESH cells** — cells with fresh coverage are skipped entirely (no search, no countdown, no subdivision); scanner advances instantly to next cell
- **Manual populate skips FRESH cells** — same behavior; FRESH cells produce no upstream Overpass call
- **Silent fill FRESH banner** — shows brief "Coverage fresh" (1.5s) instead of full "Filling POIs" (3s) when coverage is fresh
- **PopulateSearchResult** — new `coverageStatus` field passes proxy coverage state to app

## [1.5.58] — 2026-03-04

### Added
- **Overpass retry + endpoint rotation** — proxy retries up to 4 attempts across 3 endpoints (overpass-api.de, lz4, z) with 15s/30s/60s exponential backoff; detects HTML errors, 429s, 5xx, dispatcher errors
- **App-side Overpass retry** — `PlacesRepository.executeOverpassWithRetry()` retries 3 times with 5s/10s delays on HTTP errors, HTML error bodies, and network exceptions
- **Single tap on map stops everything** — tapping empty map area stops vehicle/aircraft following and all population tasks (populate, 10km probe, idle populate, silent fill)

### Changed
- **Label zoom threshold lowered to 16** — POI names, train/subway/bus details (route, speed, destination, status) now visible from zoom 16+ (was 18+)

## [Refactoring] — 2026-03-04

### Changed
- **server.js decomposed** (3,925 → 156 lines) — 18 route modules extracted to `cache-proxy/lib/`
- **MainViewModel.kt decomposed** (958 → 215 lines) — 6 domain-specific ViewModels: Social, Transit, Aircraft, Find, Weather, Geofence
- **AppBarMenuManager.kt refactored** (879 → 812 lines) — 35 preference constants extracted to `MenuPrefs.kt`, unused viewModel param removed
- **DebugEndpoints.kt** updated to accept 6 ViewModel constructor params
- **REFACTORING-REPORT.txt** — detailed report of all decomposition work

### Notes
- Pure structural refactoring — zero behavior changes
- All endpoints, LiveData, menus, and UI flows work identically
- Prior: MainActivity.kt was decomposed (9,577 → 1,996 lines + 13 extension-function files)

## [1.5.57] — 2026-03-04

### Added
- **15 new POI subtypes** across 5 categories (138 → 153 total)
  - Food & Drink: Wine Shops, Butcher Shops, Seafood Markets
  - Transit: Airports, Taxi Stands
  - Parks & Rec: Boat Ramps (1,324 in DB), Skateparks
  - Shopping: Barber Shops, Massage, Tattoo Shops, Thrift Stores, Vape Shops, Cannabis
  - Entertainment: Disc Golf
- **Cuisine-aware fuzzy search** — "pizza", "sushi", "burger", "bbq", etc. now match `tags->>'cuisine'` in addition to name fuzzy match; unlocks ~12,800 cuisine-tagged POIs
  - 30+ cuisine keywords with alias mapping (bbq → barbecue, burgers → burger, etc.)
- **~30 new search keywords** — tattoo, barber, thrift, vape, cannabis, dispensary, massage, spa, boat ramp, skatepark, disc golf, airport, taxi, butcher, seafood, wine shop, bbq, burger, steak, ramen, noodle, taco, donut, bagel, chicken, wings, sandwich, japanese, korean, vietnamese, greek, french, mediterranean

### Changed
- **Overpass default search keys** expanded: `craft`, `aeroway`, `healthcare` added — future scans pick up airports, breweries (broad scans), urgent care
- **POI category extraction** handles `craft`, `aeroway`, `healthcare` keys in both app and proxy import

## [1.5.56] — 2026-03-04

### Changed
- **Fuzzy search results sorted by distance** — search results now sort purely by distance (nearest first) instead of relevance score first; "Brooks Law" at 14 mi no longer appears before "Laws Point" at 13 mi
- **"Filter and Map" button moved to top** — teal button now appears at the top of search results (was at bottom, buried under 200+ results and invisible without scrolling)
- **Filter and Map keeps current position** — no longer teleports to results centroid; stays at current map position
- **Filter and Map adaptive zoom** — starts at zoom 18 and steps back one level at a time until at least one result is visible in the viewport (was fixed at zoom 15 centered on centroid)

### Fixed
- **Filter and Map centroid teleport** — entering filter mode no longer moves the map to a random location; respects user's current position

## [1.5.55] — 2026-03-03

### Added
- **MODULE_ID constants** — searchable `(C) Dean Maurice Ellis, 2026 - Module <filename>` in every source file (131 files: 33 Kotlin, 6 JS, 6 shell, 1 SQL, 85 XML)
- **Home toolbar icon** — house icon (left of spacer) centers map on current GPS location at zoom 18 with 800ms animation; shows toast "No GPS fix yet" if unavailable
- **About toolbar icon** — info circle icon (far right) shows AlertDialog with version (v1.5.55), copyright, website (DestructiveAIGurus.com), email (Questions@DestructiveAIGurus.com), proprietary notice

### Changed
- **Toolbar layout** — now 7 icons: Weather | Home | spacer | DarkMode | Alerts | Grid | About

## [1.5.54] — 2026-03-03

### Added
- **Copyright headers** on 131 source files + `IP.md` intellectual property register

## [1.5.53] — 2026-03-03

### Added
- **Filter and Map mode** — teal button in Find results enters exclusive map view
  - Clears all other layers, stops background jobs, force-labels all result markers
  - Status line "Showing N label — tap to clear" with FIND_FILTER priority
  - Scroll/zoom handlers guarded; radar state saved/restored; auto-exits on Find reopen
- **`FIND_FILTER` priority level** — new StatusLineManager priority (level 3)

## [1.5.52] — 2026-03-03

### Fixed
- **Search results not showing** — layout weight bugs fixed for gridScroll/searchScroll visibility

### Changed
- **Header hint bar** — count + category shown in title bar next to "Find"
- **Search result limit** — 50 → 200; distance expansion radii tuned to 50km → 100km → 100mi

## [1.5.51] — 2026-03-03

### Added
- **Smart fuzzy search** — pg_trgm similarity + ~80 keyword→category mappings in Find dialog
  - Typo-tolerant, distance expansion (50km → 100km → 100mi), rich 3-line result rows
  - `SearchResponse` model with `categoryHint` and `scopeM` fields

### Changed
- **`/db/pois/search` endpoint** — rewritten with fuzzy matching, keyword hints, composite scoring

### Database (DDL — run as sudo -u postgres)
- `CREATE EXTENSION IF NOT EXISTS pg_trgm`
- `CREATE INDEX idx_pois_name_trgm ON pois USING GIN (name gin_trgm_ops)`
