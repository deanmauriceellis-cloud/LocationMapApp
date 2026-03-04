# LocationMapApp — Session Log

> Sessions prior to v1.5.51 archived in `SESSION-LOG-ARCHIVE.md`.

## Session: 2026-03-04e (3-Phase Code Decomposition — server.js + ViewModels + MenuPrefs)

### Context
Monolithic files had grown to unsustainable sizes: `server.js` (3,925 lines), `MainViewModel.kt` (958 lines), `AppBarMenuManager.kt` (879 lines). Pure structural refactoring — zero behavior changes, all endpoints/menus/UI work identically after.

### Phase 1: server.js → 156-line bootstrap + 18 modules in lib/
- Created `cache-proxy/lib/` directory with 18 route/utility modules
- Each module exports `function(app, deps)` receiving shared state
- Key modules: overpass.js (300 lines), db-pois.js (680 lines), auth.js (340 lines), tfr.js (330 lines), chat.js (240 lines), weather.js (220 lines)
- auth.js registered first (exports requireAuth used by comments.js + chat.js)
- Proxy restarted and verified between batches

### Phase 2: MainViewModel.kt → 215 lines + 6 domain ViewModels
- **SocialViewModel** (200 lines): auth, comments, chat — AuthRepository, CommentRepository, ChatRepository
- **TransitViewModel** (165 lines): MBTA trains, subway, buses, stations, bus stops — MbtaRepository
- **AircraftViewModel** (79 lines): aircraft tracking, flight history — AircraftRepository
- **FindViewModel** (86 lines): Find counts, nearby, search, POI website — FindRepository
- **WeatherViewModel** (108 lines): weather, METAR, webcams, radar refresh — WeatherRepository, WebcamRepository
- **GeofenceViewModel** (286 lines): TFR, cameras, schools, flood, crossings, databases, GeofenceEngine — TfrRepository, GeofenceRepository, GeofenceDatabaseRepository
- MainViewModel retained: Location + POI viewport (215 lines, 2 dependencies)
- Each extraction: update ViewModel refs in consumer files + MainActivity observers + DebugEndpoints constructor
- 6 successful incremental builds

### Phase 3: AppBarMenuManager.kt → 812 lines + MenuPrefs.kt (74 lines)
- Removed unused MainViewModel constructor parameter
- Extracted 35 preference key constants to MenuPrefs.kt object
- Updated 65 references across 7 consuming files
- Removed unused ContextCompat import, unused AppBarMenuManager import in MainActivityFind.kt

### Files Created (26)
- `cache-proxy/lib/*.js` — 18 route modules
- `app/.../ui/SocialViewModel.kt` — auth, comments, chat
- `app/.../ui/TransitViewModel.kt` — MBTA transit
- `app/.../ui/AircraftViewModel.kt` — aircraft tracking
- `app/.../ui/FindViewModel.kt` — Find dialog queries
- `app/.../ui/WeatherViewModel.kt` — weather, METAR, webcams
- `app/.../ui/GeofenceViewModel.kt` — geofence system
- `app/.../ui/menu/MenuPrefs.kt` — preference key constants
- `REFACTORING-REPORT.txt` — detailed refactoring report

### Files Modified (15+)
- `cache-proxy/server.js` — 3,925 → 156 lines (bootstrap only)
- `app/.../ui/MainViewModel.kt` — 958 → 215 lines (Location + POI only)
- `app/.../ui/menu/AppBarMenuManager.kt` — 879 → 812 lines (companion removed)
- `app/.../ui/MainActivity.kt` — 7 ViewModel properties, updated observers + DebugEndpoints
- `app/.../ui/MainActivityTransit.kt` — viewModel → transitViewModel
- `app/.../ui/MainActivityAircraft.kt` — viewModel → aircraftViewModel
- `app/.../ui/MainActivityFind.kt` — viewModel → findViewModel
- `app/.../ui/MainActivityWeather.kt` — viewModel → weatherViewModel
- `app/.../ui/MainActivityGeofences.kt` — viewModel → geofenceViewModel
- `app/.../ui/MainActivityDebug.kt` — multiple ViewModel refs
- `app/.../ui/MainActivityRadar.kt` — weatherViewModel + MenuPrefs
- `app/.../ui/MainActivityPopulate.kt` — MenuPrefs
- `app/.../ui/MainActivitySocial.kt` — socialViewModel
- `app/.../util/DebugEndpoints.kt` — 6 ViewModel constructor params

### Commits (3)
- `6e8fa58` Decompose server.js (3,925 → 156 lines) into 18 modules in lib/
- `6762cd5` Decompose MainViewModel.kt (958 → 215 lines) into 6 domain-specific ViewModels
- `494c112` Refactor AppBarMenuManager.kt: extract MenuPrefs.kt, remove unused viewModel param

---

## Session: 2026-03-04d (COMMERCIALIZATION.md v2.0 — Lawyer-Ready Enhancement)

### Context
Rewrote COMMERCIALIZATION.md from a 15-section technical reference (1,019 lines) into a 27-section, 3-part lawyer-ready document (1,897 lines). Goal: hand the document to an attorney who has never seen the app and have them understand the product, business model, legal risks, and give actionable advice.

### Changes Made

#### 1. COMMERCIALIZATION.md — Complete Restructure (1,019 → 1,897 lines)

**Part A — What the Lawyer Needs to Know (4 new/enhanced sections)**
- §1 Product Description: plain-English app overview, features, tech stack, audience, dev status
- §2 Revenue Model & Freemium Design: free+ads / paid tier ($2.99–$4.99/mo), Google's cut, ad revenue estimates, legal implications
- §3 Data Flow Description: what's collected, ASCII data flow diagram, what users see about each other, what third parties receive, encryption status
- §4 Executive Summary: enhanced with Likelihood column and reading guide

**Part B — Legal Analysis (6 new sections + expanded privacy)**
- §5 Finding the Right Attorney: type needed, where to find (MA Bar, SCORE, meetups), what to bring, budget, red flags, timing
- §9.7–9.10 International Privacy: GDPR, UK GDPR, 5 other jurisdictions, phased expansion roadmap
- §11 Dependency Inventory: 22 Android + 12 Node.js libraries with license + risk (flagged: osmbonuspack LGPL, JTS EPL-2.0, duck-duck-scrape ToS risk)
- §12 Ad Network Compliance: AdMob requirements, UMP consent, COPPA+ads, ad content filtering, revenue estimates
- §13 In-App Purchase & Google Play Billing: Billing Library, 15%/30% commission, post-Epic v. Google, subscription legal requirements
- §14 Tax Considerations: sales tax (Google collects), income tax, S-Corp election, quarterly estimates, deductible expenses, CPA timing
- §15 Social Media Integration: current status (none), future options, social login legal requirements
- §17 Competitor Analysis: 8 comparable apps (Google Maps, Yelp, Flightradar24, Waze, Transit, AllTrails, RadarScope, Aloft) with legal approach

**Part C — Implementation & Execution (3 new sections + expanded checklist)**
- §24 Cost Summary: updated with Google Play dev ($25), CPA ($200–500), revenue projections vs costs, breakeven analysis (~1,500–2,500 MAU)
- §25 Risk Matrix: 14 risks scored by Probability × Impact (1–5 scale), priority actions ranked by score
- §26 Specific Questions for Attorney: 17 questions in 3 tiers (Must/Should/Can defer), serves as meeting agenda
- §27 Master Checklist: expanded from 8 → 10 phases, added Monetization Setup + Future Growth phases, ~70 items

All 15 original sections preserved (renumbered, Cloud Deployment moved to Part C as §23).

### Files Modified (4)
- `COMMERCIALIZATION.md` — complete rewrite (1,019 → 1,897 lines)
- `STATE.md` — updated Commercialization section + Next Steps
- `SESSION-LOG.md` — added this session entry
- `memory/MEMORY.md` — updated commercialization references

---

## Session: 2026-03-04c (v1.5.57 — POI Coverage Expansion + Cuisine Search)

### Context
Analyzed 211K POIs — found 1,324 boat ramps, 165 massage shops, 132 tattoo shops, 131 cannabis dispensaries, and 12,800+ cuisine-tagged restaurants not reachable through Find grid or search. Also missing airports, barber shops, skateparks from Overpass scans.

### Changes Made

#### 1. 15 New Subtypes in PoiCategories.kt (138 → 153)
- Food & Drink: +Wine Shops, +Butcher Shops, +Seafood Markets
- Transit: +Airports (`aeroway=aerodrome`), +Taxi Stands
- Parks & Rec: +Boat Ramps (`leisure=slipway`), +Skateparks
- Shopping: +Barber Shops, +Massage, +Tattoo Shops, +Thrift Stores, +Vape Shops, +Cannabis
- Entertainment: +Disc Golf

#### 2. Cuisine-Aware Fuzzy Search (server.js)
- Added CUISINE_KEYWORDS set (30+ entries) + CUISINE_ALIASES for OSM spelling variants
- Search query now adds `OR tags->>'cuisine' ILIKE '%keyword%'` when cuisine keyword detected
- "pizza" finds 1,483 pizza places, "burger" finds 1,693, "mexican" finds 1,103, etc.

#### 3. ~30 New Search Keywords (server.js)
- Shopping: tattoo, barber, thrift, second hand, vape, cannabis, dispensary, massage, spa
- Food: butcher, seafood, wine shop, bbq, burger, steak, ramen, noodle, taco, donut, bagel, chicken, wings, sandwich, japanese, korean, vietnamese, greek, french, mediterranean
- Transit: airport, taxi
- Parks: boat ramp, boat launch, skatepark, skateboard
- Entertainment: disc golf, frisbee golf

#### 4. Expanded Overpass Search (PlacesRepository.kt)
- Default keys: added `craft`, `aeroway`, `healthcare`
- Category extraction: handles new keys in both parseOverpassJson instances
- POI_CATEGORY_KEYS in server.js: added `aeroway`, `healthcare`

### Files Modified (3)
- `app/.../ui/menu/PoiCategories.kt` — 15 new subtypes, new tags
- `cache-proxy/server.js` — cuisine search, 30+ keywords, CATEGORY_LABEL_TAGS, POI_CATEGORY_KEYS
- `app/.../data/repository/PlacesRepository.kt` — 3 new Overpass keys, category extraction

### Testing Needed
- [ ] Proxy restart + verify cuisine search ("pizza", "bbq", "sushi" near Boston)
- [ ] App reinstall + verify new subtypes appear in Find grid
- [ ] Verify "Boat Ramps" shows 1,324 results
- [ ] Verify "Filter and Map" works with new subtypes
- [ ] Trigger a scan to verify airports/barbers/skateparks get imported

---

## Session: 2026-03-04b (Commercialization Roadmap)

### Context
User requested a comprehensive "pathway to making this an application I can sell" — covering cloud deployment, legal structure, IP protection, user content liability, privacy compliance, content moderation, and Google Play requirements.

### Changes Made

#### 1. COMMERCIALIZATION.md (new document, ~1,019 lines)
- Researched cloud hosting (Railway, DigitalOcean, Neon, Cloudflare R2), pricing at 100/1K/10K user scales
- Legal structure: Wyoming LLC recommended ($100 formation, $60/yr)
- Insurance: Tech E&O + Cyber Liability ($1,300–$3,000/yr) — non-optional given safety data
- IP protection: copyright ($130), provisional patents ($60 each micro-entity), trademark ($350), trade secrets ($0), R8 obfuscation ($0)
- User content liability: Section 230 protections, DMCA safe harbor ($6 agent registration), Take It Down Act (May 2026 deadline)
- Privacy: CCPA/CPRA, 21+ state laws treating GPS as sensitive PI, COPPA age gate (13+), data retention schedule
- Third-party APIs: **OpenSky requires commercial license (BLOCKING)**, OSM ODbL share-alike on POI database, MBTA license review needed
- Content moderation: OpenAI Moderation API (free) + Perspective API (free), tiered auto-block/queue/approve system
- Safety data disclaimers for TFR, geofences, weather, flood zones
- Google Play: Data Safety section, prominent location disclosure, R8 release builds
- Master checklist: 8 phases, ~50 action items
- Year 1 estimated cost: $4,578–$10,955

### Files Created (1)
- `COMMERCIALIZATION.md` — full commercialization roadmap

### Files Modified (3)
- `STATE.md` — added Commercialization section + updated Next Steps
- `SESSION-LOG.md` — added this session entry
- `memory/MEMORY.md` — added commercialization reference

---

## Session: 2026-03-04 (v1.5.56 — Search Distance Sort + Filter and Map UX)

### Context
User noticed fuzzy search results were sorted by relevance (fuzzy match score) instead of distance, and the "Filter and Map" button was buried at the bottom of 200+ results. Also, tapping "Filter and Map" teleported to results centroid instead of keeping current position.

### Changes Made

#### 1. Fuzzy Search Distance Sort (server.js)
- Changed `ORDER BY (score) DESC, distance ASC` to `ORDER BY distance ASC`
- All search results now sorted nearest-first regardless of fuzzy match quality

#### 2. "Filter and Map" Button Moved to Top (MainActivity.kt)
- Moved teal button from bottom of `searchResultsList` to top (before result rows)
- Removed duplicate button from bottom of results

#### 3. Filter and Map — Keep Current Position + Adaptive Zoom (MainActivity.kt)
- Removed centroid calculation and `setCenter()` call
- Starts at zoom 18, steps back one level until at least one result is visible (min zoom 3)

### Files Modified (2)
- `cache-proxy/server.js` — ORDER BY change (1 line)
- `app/.../ui/MainActivity.kt` — button move, centroid removal, adaptive zoom (~30 lines)

---

## Session: 2026-03-03g (v1.5.55 — Module IDs + Home + About)

### Changes Made
- MODULE_ID constants in 131 source files
- Home toolbar icon: GPS center at zoom 18
- About toolbar icon: version/copyright/contact dialog

---

## Session: 2026-03-03f (v1.5.53 — Filter and Map Mode)

### Changes Made
- Teal "Filter and Map" button in Find results → exclusive map view
- enterFilterAndMapMode/exitFilterAndMapMode with scroll/zoom guards
- FIND_FILTER status line priority, radar save/restore, auto-exit on Find reopen

---

## Session: 2026-03-03e (v1.5.52 — Fuzzy Search Testing & Fixes)

### Changes Made
- Fixed gridScroll/searchScroll layout weight bugs (search results invisible)
- Header hint bar moved to title, search limit 50→200, distance expansion tuning

---

## Session: 2026-03-03d (v1.5.51 — Smart Fuzzy Search)

### Changes Made
- pg_trgm extension + GIN trigram index on pois.name
- ~80 keyword→category mappings, composite scoring, distance expansion
- SearchResponse model, rewritten /db/pois/search endpoint
- Rich 3-line result rows, 1000ms debounce, keyword hint chips
