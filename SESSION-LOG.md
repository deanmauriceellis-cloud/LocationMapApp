# LocationMapApp — Session Log

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
