# LocationMapApp — Changelog

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
