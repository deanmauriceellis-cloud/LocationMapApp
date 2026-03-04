# LocationMapApp — Changelog

> Releases prior to v1.5.51 archived in `CHANGELOG-ARCHIVE.md`.

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
