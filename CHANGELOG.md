# LocationMapApp — Changelog

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
