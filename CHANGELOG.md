# LocationMapApp — Changelog

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
