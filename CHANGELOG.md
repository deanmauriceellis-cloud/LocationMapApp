# LocationMapApp — Changelog

> Releases prior to v1.5.51 archived in `CHANGELOG-ARCHIVE.md`.

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
