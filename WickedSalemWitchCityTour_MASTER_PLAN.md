# WickedSalemWitchCityTour — Master Plan

**App Name:** WickedSalemWitchCityTour
**Package:** `com.example.wickedsalemwitchcitytour`
**Price:** Tiered (Free / $4.99 / $9.99 / $49.99-mo subscription)
**Platform:** Android (API 26+, Kotlin, osmdroid)
**Base:** LocationMapApp v1.5 multi-module platform
**Knowledge Base:** ~/Development/Salem (Salem Witch Trials 1692 simulation project)

---

## Table of Contents

1. [Vision](#1-vision)
2. [Architecture Overview](#2-architecture-overview)
3. [Phase 1 — Core Module Extraction](#phase-1--core-module-extraction)
4. [Phase 2 — Salem App Shell](#phase-2--salem-app-shell-app-salem)
5. [Phase 3 — Salem Content Database](#phase-3--salem-content-database)
6. [Phase 4 — Content Pipeline (Salem Project Import)](#phase-4--content-pipeline-salem-project-import)
7. [Phase 5 — Enhanced Salem POI Catalog](#phase-5--enhanced-salem-poi-catalog)
8. [Phase 6 — Tour Engine](#phase-6--tour-engine)
9. [Phase 7 — GPS Geofence Triggers & Narration](#phase-7--gps-geofence-triggers--narration)
10. [Phase 8 — Walking Directions](#phase-8--walking-directions)
11. [Phase 9 — Haunted Happenings & Events](#phase-9--haunted-happenings--events-integration)
12. [Phase 10 — Polish, Branding & Play Store](#phase-10--polish-branding--play-store)
13. [Content Organization Strategy](#content-organization-strategy)
14. [Tour Definitions](#tour-definitions)
15. [Salem POI Master List](#salem-poi-master-list)
16. [Data Sources Reference](#data-sources-reference)
17. [Verification Checkpoints](#verification-checkpoints)
18. [Business Model & Monetization](#business-model--monetization)
19. [Technical Foundation — Audit Recommendations](#technical-foundation--audit-recommendations)
20. [Future Phases (Post-Launch)](#future-phases-post-launch)

---

## 1. Vision

A tourist arrives in Salem, MA. They open WickedSalemWitchCityTour on their Android phone. The map centers on Salem with rich, categorized POI markers — every museum, restaurant, bar, B&B, occult shop, memorial, park, and historical site. They pick a themed walking tour (Witch Trials, Maritime, Hawthorne Literary, etc.) or just explore freely. As they walk, the app detects their GPS proximity to points of interest and delivers historically accurate narration via text-to-speech — short summaries on approach, deeper stories when they stop, verbatim court records and primary source quotes on demand. Walking directions guide them from stop to stop. The app knows about Haunted Happenings events, Peabody Essex Museum exhibits, seasonal shows, and every business that serves Salem's tourists.

All of this builds on top of LocationMapApp's proven infrastructure: real-time MBTA transit (trains from Boston, local buses), weather, geofencing, POI search, social features, and the existing database of thousands of local businesses.

**Target audience:** Salem tourists (season: April 1 - mid November, peak: October)
**Geographic scope:** Downtown Salem (walkable core ~1 sq mile), plus Danvers (original Salem Village) sites flagged as requiring transportation

---

## 2. Architecture Overview

### Multi-Module Monorepo

```
LocationMapApp_v1.5/
├── core/                    # Shared library (models, repos, location, geofence, utils)
├── app/                     # LocationMapApp (generic, unchanged for users)
├── app-salem/               # WickedSalemWitchCityTour (this app)
├── salem-content/           # Content pipeline tool (build-time only, not shipped)
├── cache-proxy/             # Existing Node.js proxy server
├── web/                     # Existing React web app
└── server/                  # Existing backend
```

**Dependency chain:**
```
app-salem → core ← app
```

Both `:app` and `:app-salem` depend on `:core`. They share all data layer code (models, repositories, location, geofencing) but have independent UI, branding, and app-specific features.

### Offline-First + API Sync Architecture

```
┌──────────────────────────────────────────────────────────────┐
│ WickedSalemWitchCityTour (Android)                           │
│                                                              │
│  ┌─────────────────┐    ┌──────────────────────────────────┐ │
│  │ Room SQLite DB   │◄──│ Bundled salem_content.db (asset) │ │
│  │ (salem_content)  │    │ 9 tables, ~841 records           │ │
│  │                  │    └──────────────────────────────────┘ │
│  │ Works 100%       │                                        │
│  │ offline          │◄── /salem/sync (when online)           │
│  └─────────────────┘                                        │
│           │                                                  │
│  ┌────────▼────────┐    ┌──────────────────────────────────┐ │
│  │ Core repos       │───►│ cache-proxy (existing APIs)      │ │
│  │ (Overpass, MBTA, │    │ /pois, /weather, /transit, etc.  │ │
│  │  Weather, etc.)  │    │ POIs, transit, weather = online  │ │
│  └─────────────────┘    └──────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────▼──────────┐
                    │ cache-proxy (4300)  │
                    │ Node.js + PostgreSQL│
                    │                    │
                    │ /salem/* endpoints  │ ← NEW: Salem content API
                    │ /pois/*            │ ← Existing: generic POIs
                    │ /weather, /transit │ ← Existing: weather, MBTA
                    └────────────────────┘
```

**Design principles:**
- **Offline-first**: App ships with bundled Room DB; all Salem content (POIs, figures, facts, sources, tours) works without internet
- **Online-enhanced**: When connected, syncs fresh content via `/salem/sync` endpoint; core features (live transit, weather radar, Overpass POIs) require network
- **Dual database**: Local Room SQLite (bundled asset) + remote PostgreSQL (cache-proxy APIs). Local is the truth for Salem content; remote for generic POIs
- **Backward compatible**: All new endpoints under `/salem/*` prefix. Existing `pois`, `auth`, `chat`, `comments` endpoints untouched. No schema changes to existing tables except optional provenance columns on `pois` (ALTER ADD, nullable, defaults)
- **Cloud-ready**: Backend designed for eventual cloud deployment. Salem API endpoints are stateless and horizontally scalable

### Data Provenance & Staleness

Every entity in both local and remote databases carries provenance and staleness metadata:

| Field | Type | Purpose |
|-------|------|---------|
| `data_source` | String | Origin: `manual_curated`, `salem_project`, `overpass_import`, `api_sync`, `user_report` |
| `confidence` | Float | 0.0–1.0 trust score (curated=1.0, overpass=0.8, user_report=0.5) |
| `verified_date` | String/Timestamp | ISO date of last human/automated verification |
| `created_at` | Long/Timestamp | Record creation time |
| `updated_at` | Long/Timestamp | Last modification time |
| `stale_after` | Long/Timestamp | TTL — when this record should be re-verified (0 = never stale) |

**Staleness strategy:**
- Historical content (figures, facts, timeline, sources): `stale_after = 0` (never stale — history doesn't change)
- Businesses: `stale_after = 180 days` (hours, prices, and closures change)
- Events: `stale_after = event end date` (naturally expire)
- Tour POIs: varies (museums=365d, seasonal sites=90d, permanent landmarks=0)

---

## Phase 1 — Core Module Extraction

**Goal:** Extract shared code from `:app` into `:core` library module. No user-facing changes.

### Step 1.1: Create core module structure
- [x] Create `core/build.gradle` (Android library plugin)
  - Dependencies: OkHttp 4.12.0, Gson 2.10.1, Coroutines 1.7.3, Hilt 2.51, JTS 1.19.0, Socket.IO 2.1.0, Room 2.6.1, GMS Location 21.1.0
- [x] Create `core/src/main/AndroidManifest.xml` (minimal library manifest)
- [x] Update `settings.gradle`: add `include ':core'`
- [x] Update `app/build.gradle`: add `implementation project(':core')`

### Step 1.2: Move data models
- [x] Move `app/.../data/model/Models.kt` → `core/.../data/model/Models.kt`
  - All 50+ data classes (PlaceResult, MbtaVehicle, AircraftState, WeatherAlert, GeofenceAlert, AuthUser, ChatMessage, etc.)
- [x] Move `app/.../core/AppException.kt` → `core/.../core/AppException.kt`

### Step 1.3: Move repositories
- [x] Move all 12 repository files from `app/.../data/repository/` → `core/.../data/repository/`:
  - PlacesRepository.kt (537 lines — POI search, Overpass, OSM)
  - MbtaRepository.kt (561 lines — MBTA API v3, vehicles, stops)
  - AircraftRepository.kt (160 lines — OpenSky)
  - WeatherRepository.kt (261 lines — NWS, alerts, forecast)
  - WebcamRepository.kt (84 lines — Windy webcams)
  - TfrRepository.kt (103 lines — FAA TFR)
  - FindRepository.kt (256 lines — search, reverse geocode)
  - GeofenceRepository.kt (295 lines — speed cameras, school zones)
  - GeofenceDatabaseRepository.kt (578 lines — Room DB)
  - AuthRepository.kt (244 lines — device-bonded auth)
  - ChatRepository.kt (243 lines — Socket.IO)
  - CommentRepository.kt (144 lines — POI comments)

### Step 1.4: Move location & geofencing
- [x] Move `app/.../data/location/LocationManager.kt` → `core/.../data/location/LocationManager.kt`
- [x] Move `app/.../util/GeofenceEngine.kt` → `core/.../util/GeofenceEngine.kt`
  - JTS STRtree spatial indexing, point-in-polygon, proximity alerts

### Step 1.5: Move utilities & DI
- [x] Move `app/.../util/DebugLogger.kt` → `core/.../util/DebugLogger.kt`
- [x] Move `app/.../util/FavoritesManager.kt` → `core/.../util/FavoritesManager.kt`
- [x] Move `app/.../di/AppModule.kt` → `core/.../di/CoreModule.kt` (rename)
- [x] Move `app/.../ui/menu/MenuPrefs.kt` → `core/.../ui/menu/MenuPrefs.kt`
- [x] Move `app/.../ui/menu/MenuEventListener.kt` → `core/.../ui/menu/MenuEventListener.kt`
- N/A `PoiLayerId` — not a standalone file (defined within PoiCategories.kt)

### Step 1.6: Update imports in `:app`
- [x] Update all import statements in remaining `:app` files
- [x] Verify no circular dependencies

### Step 1.7: Verify
- [x] `./gradlew :core:assembleDebug` builds successfully
- [x] `./gradlew :app:assembleDebug` builds successfully
- [x] Run on Pixel_8a_API_34 emulator — all features work identically
- [x] Git commit: "Phase 1: Extract :core shared library module"

**What stays in `:app` (NOT moved):**
- All `MainActivity*.kt` files (UI-specific)
- All `*ViewModel.kt` files (AndroidX lifecycle)
- `AppBarMenuManager.kt` (Android Toolbar API)
- `MarkerIconHelper.kt`, `WeatherIconHelper.kt` (bitmap rendering)
- `StatusLineManager.kt` (Android TextView)
- `DebugEndpoints.kt` (~35K lines, app state coupling)
- `TcpLogStreamer.kt`, `DebugHttpServer.kt` (app-specific debug)
- All `res/` resources

---

## Phase 2 — Salem App Shell (`:app-salem`)

**Goal:** Create a buildable Salem app that shows a map centered on Salem with all core features working.

### Step 2.1: Create app-salem module
- [x] Create `app-salem/build.gradle` (`.gradle` not `.kts`)
  - `com.android.application` plugin
  - `implementation(project(":core"))`
  - Dependencies: osmdroid 6.1.18, osm-bonus-pack 6.9.0, Material Design, AndroidX
  - `applicationId = "com.example.wickedsalemwitchcitytour"`
  - `versionName = "1.0.0"`
- [x] Update `settings.gradle`: add `include ':app-salem'`
- [x] Create `app-salem/src/main/AndroidManifest.xml`
  - Permissions: INTERNET, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, FOREGROUND_SERVICE

### Step 2.2: Copy & adapt UI from `:app`
- [x] Copy `MainActivity.kt` → `SalemMainActivity.kt` (adapted, map centered on Salem)
  - Center map on Salem: lat=42.521, lng=-70.887, zoom=15
- [x] Copy all extension files (Find, Weather, Transit, Geofences, Social, Dialogs, Helpers, Aircraft, Debug, Metar, Populate, Radar — 12 total)
- [x] Copy ViewModels (7 files)
- [x] Copy `MarkerIconHelper.kt`, `StatusLineManager.kt`, `WeatherIconHelper.kt`
- [x] Copy menu system (`AppBarMenuManager.kt`, `PoiCategories.kt`)
- [x] Copy `WickedSalemApp.kt` application class, `DebugLogActivity.kt`, debug utils
- [x] Package: `com.example.wickedsalemwitchcitytour`, 31 Kotlin files total

### Step 2.3: Salem branding
- [x] Create `res/values/colors.xml` — deep purple (#2D1B4E), antique gold (#C9A84C), dark charcoal
- [x] Create `res/values/strings.xml` — app name "Wicked Salem"
- [x] Create `res/values/themes.xml` — `Theme.WickedSalem` (day + night)
- [x] Create app icon (placeholder: gold "W" on purple background)
- [ ] Create splash screen layout (deferred to Phase 10)

### Step 2.4: Verify
- [x] `./gradlew :app-salem:assembleDebug` builds (9.6MB APK)
- [ ] Install on emulator — map shows Salem
- [ ] Core features work: weather, transit, POI search, geofences
- [ ] Git commit: "Phase 2-4: Salem app shell, content database, content pipeline"

---

## Phase 3 — Salem Content Database

**Goal:** Design and implement the Room database that holds all Salem-specific content.

### Step 3.1: Room database schema
- [x] Create `SalemContentDatabase.kt` — Room database class
- [x] Create entity classes (in `app-salem/.../content/model/`):

```
tour_pois          — Tour-worthy points of interest with narration
salem_businesses   — Enhanced business listings (restaurants, bars, B&Bs, shops)
historical_figures — People of the Salem Witch Trials
historical_facts   — Facts from the Salem knowledge base
timeline_events    — Chronological events of 1692
primary_sources    — Court records, petitions, letters, diary entries
tours              — Pre-defined tour route definitions
tour_stops         — Tour-to-POI join table with ordering
figure_poi_links   — Which historical figures connect to which POIs
poi_categories     — Category/tag taxonomy
events_calendar    — Haunted Happenings, museum exhibits, shows, seasonal events
```

### Step 3.2: Entity definitions

**tour_pois** (core tour stops — ~50-100 curated locations):
```
id, name, lat, lng, address, category, subcategories (JSON array),
short_narration (TTS, 15-30 sec), long_narration (TTS, 60-120 sec),
description (text display), historical_period, admission_info,
hours, phone, website, image_asset, geofence_radius_m,
requires_transportation (boolean), wheelchair_accessible (boolean),
seasonal (boolean), priority (1-5)
```

**salem_businesses** (comprehensive business directory — hundreds):
```
id, name, lat, lng, address, business_type (restaurant|bar|cafe|lodging|
shop_occult|shop_retail|shop_gift|attraction|event_venue|service),
cuisine_type, price_range, hours, phone, website, description,
historical_note (many Salem businesses have historical connections),
tags (JSON array), rating, image_asset
```

**historical_figures** (from Salem project — ~50 key people):
```
id, name, first_name, surname, born, died, age_in_1692, role,
faction, short_bio (2-3 sentences), full_bio (complete narrative),
narration_script (TTS-optimized), appearance_description,
role_in_crisis, historical_outcome, key_quotes (JSON array),
family_connections (JSON), primary_poi_id
```

**historical_facts** (from Salem project — ~500 selected):
```
id, title, description, date, date_precision, category, subcategory,
poi_id, figure_id, source_citation, narration_script,
confidentiality (public|semi_private), tags (JSON array)
```

**timeline_events** (40 anchor + ~40 minor — ~80 total):
```
id, name, date, crisis_phase, description, poi_id,
figures_involved (JSON array), narration_script, is_anchor (boolean)
```

**primary_sources** (selected ~200 key excerpts):
```
id, title, source_type (examination|petition|letter|diary|sermon|court_record),
author, date, full_text, excerpt (key passage), figure_id, poi_id,
narration_script, citation
```

**tours** (8-10 pre-defined routes):
```
id, name, theme, description, estimated_minutes, distance_km,
stop_count, difficulty, seasonal (boolean), icon_asset, sort_order
```

**tour_stops** (tour-to-POI ordering):
```
tour_id, poi_id, stop_order, transition_narration,
walking_minutes_from_prev, distance_m_from_prev
```

**events_calendar** (Haunted Happenings, shows, exhibits):
```
id, name, venue_poi_id, event_type (haunted_tour|museum_exhibit|show|
festival|market|parade|special_event), description, start_date,
end_date, hours, admission, website, recurring (boolean),
recurrence_pattern, seasonal_month (10 for October events)
```

### Step 3.3: DAO interfaces
- [x] `TourPoiDao` — findByCategory, findNearby(lat, lng, radiusM), findById, search(query)
- [x] `SalemBusinessDao` — findByType, findNearby, search, findByTag
- [x] `HistoricalFigureDao` — findAll, findById, findByPoi, findByRole, search
- [x] `HistoricalFactDao` — findByPoi, findByFigure, findByCategory, findByDate, search
- [x] `TimelineEventDao` — findAll (ordered by date), findByPoi, findByPhase, findAnchorEvents
- [x] `PrimarySourceDao` — findByFigure, findByPoi, findByType, search
- [x] `TourDao` — findAll, findById, findBySeason
- [x] `TourStopDao` — findByTour (ordered), findByPoi, findTourPoisByTour (JOIN)
- [x] `EventsCalendarDao` — findUpcoming, findByMonth, findByVenue, findActive

### Step 3.4: Repository layer
- [x] Create `SalemContentRepository.kt` — unified access to all Salem content
  - Tour management (list tours, get stops, track progress)
  - POI queries (by category, proximity, search)
  - Business directory queries
  - Historical content queries (figures, facts, sources, timeline)
  - Event calendar queries
  - Bulk insert methods for content pipeline
- [x] Hilt injection via `SalemModule.kt`

### Step 3.5: Verify
- [x] All DAOs compile and can be injected
- [x] `./gradlew :app-salem:assembleDebug` builds clean (0 warnings)
- [x] Git commit: "Phase 2-4: Salem app shell, content database, content pipeline"

---

## Phase 4 — Content Pipeline (Salem Project Import)

**Goal:** Build tooling to transform ~/Development/Salem JSON into the Salem content database.

### Step 4.1: Create salem-content module
- [x] Create `salem-content/build.gradle.kts` (JVM-only, no Android)
  - Dependencies: Gson, kotlinx-coroutines (for file I/O)
- [x] Update `settings.gradle`: add `include ':salem-content'`

### Step 4.2: JSON readers
- [x] `BuildingReader.kt` — parse `data/json/buildings/_all_buildings.json`
  - Extract: id, name, type, zone, rooms, atmosphere, descriptions
- [x] `NpcReader.kt` — parse `data/json/npcs/_all_npcs.json`
  - Extract: id, name, born/died, role, faction, narrative, personality, relationships, role_in_crisis, key quotes
  - Filter: Tier 1 (28) + Tier 2 (20) only
- [x] `FactReader.kt` — parse `data/json/facts/_all_facts.json`
  - Extract: id, title, description, date, category, npcs_involved, location, source
  - Filter: public/semi_private confidentiality, tourist-relevant categories
  - Target: ~500 facts
- [x] `EventReader.kt` — parse `data/json/events/_all_events.json`
  - Extract: all 40 anchor events + select minor events
- [x] `PrimarySourceReader.kt` — parse `data/json/primary_sources/_all_primary_sources.json`
  - Extract: key examination transcripts, petitions, letters, diary entries
  - Target: ~200 most impactful excerpts
- [x] `CoordinateReader.kt` — parse `client/src/data/buildingCoordinates.ts`
  - Extract: building id, name, x, y grid coordinates, zone

### Step 4.3: Coordinate mapping
- [x] `CoordinateMapper.kt` — grid (2000x2000) → GPS lat/lng
  - Anchor points for triangulation:
    - Meetinghouse (grid 1000,1200) → GPS ~42.5630, -70.9510 (Danvers)
    - Nurse Homestead (grid 1100,1260) → GPS 42.5630, -70.9380 (Danvers)
  - Grid scale: 1 unit ≈ 26 feet ≈ 7.9 meters
  - These map 1692 VILLAGE locations (modern Danvers)
  - Modern SALEM tourist sites have direct GPS (manually curated, not grid-derived)

### Step 4.4: Narration script generator
- [x] `NarrationGenerator.kt`
  - Convert historical descriptions to TTS-optimized text:
    - Shorter sentences (max ~20 words)
    - Pronunciation hints for 1692 names (Parris, Hathorne, Corey, Easty)
    - Natural speech patterns, not academic prose
    - Appropriate pauses (commas, periods)
  - Generate two versions per POI:
    - **Short narration** (15-30 sec / ~50-100 words): approach summary
    - **Long narration** (60-120 sec / ~200-400 words): full at-location story
  - Generate per-figure narration scripts from bios
  - Embed key primary source quotes inline

### Step 4.5: Content assembly & output
- [x] `ContentPipeline.kt` — orchestrate the full pipeline:
  1. Read all JSON sources
  2. Filter to tourist-relevant content
  3. Map coordinates
  4. Generate narration scripts
  5. Output as SQL insert statements or pre-built Room DB
- [x] `ContentValidator.kt` — verify:
  - All tour POIs have valid GPS coordinates
  - All narration scripts are under TTS length limits
  - All figure-POI links reference valid entities
  - No orphaned content
- [x] Generate `salem_content.db` → copy to `app-salem/src/main/assets/`
  - Room `createFromAsset("salem_content.db")` with identity hash `1ab2eea2c8c64126e88af7a9ce8ba38f`
  - 1.7MB SQLite DB with 841 records

### Step 4.6: Verify
- [x] Pipeline runs end-to-end without errors
- [x] Output database loads in the Salem app (createFromAsset + fallbackToDestructiveMigration)
- [ ] Content is queryable and displays correctly (Phase 6 — UI needed)
- [x] Git commit: (included in Phase 5 commit)

---

## Phase 5 — Enhanced Salem POI Catalog

**Goal:** Build a comprehensive, rich directory of every tourist-relevant entity in Salem, far beyond the base LocationMapApp POI database.

### Step 5.1: Historical & cultural POIs (curated, ships in DB)

**Witch Trials Sites (8/10 curated):**
- [x] Witch Trials Memorial (24 Liberty St) — with full narration
- [x] Salem Witch Museum (19 1/2 Washington Sq N) — hours, admission, narration
- [x] Witch House / Jonathan Corwin House (310 1/2 Essex St)
- [x] Proctor's Ledge Memorial (7 Pope St) — confirmed execution site
- [x] Charter Street Cemetery / Old Burying Point (51 Charter St)
- [x] Salem Jail site (Federal & St. Peter's) — marked location
- [x] Court House site (70 Washington St) — marked location
- [x] Judge Hathorne's home site (118 Washington St) — marked location
- [x] Sheriff Corwin's home site (148 Washington St) — marked location
- [x] Rebecca Nurse Homestead (149 Pine St, Danvers) — flag: requires transport

**Maritime & National Historic (3/5 curated):**
- [x] Salem Maritime National Historical Park (160 Derby St) — FREE
- [x] Custom House (within SMNHP) — Hawthorne worked here
- [x] Derby Wharf — 1/2 mile, Friendship of Salem replica
- [x] Derby Wharf Light Station — lighthouse at end of wharf, 1871
- [x] Narbonne House (71 Essex St, within SMNHP) — c. 1675, oldest surviving house

**Museums & Cultural (5/6 curated):**
- [x] Peabody Essex Museum (161 Essex St) — exhibits, hours, admission
- [x] House of the Seven Gables (115 Derby St) — campus with multiple buildings
- [x] Pioneer Village / Salem 1630 (98 West Ave) — living history
- [x] Witch Dungeon Museum
- [x] ~~Salem Wax Museum~~ — permanently closed, removed from catalog
- [x] New England Pirate Museum

**Literary (3/5 curated):**
- [x] Hawthorne's Birthplace (on Seven Gables campus)
- [x] "Castle Dismal" (10½ Herbert St) — Manning family / Hawthorne boyhood home
- [x] 14 Mall Street — where he wrote The Scarlet Letter
- [x] Hawthorne statue
- [x] Hawthorne Hotel (18 Washington Square West)

**Parks & Landmarks (6/6 curated):**
- [x] Salem Common (31 Washington Square) — 9 acres
- [x] Winter Island Park (50 Winter Island Rd) — Fort Pickering, lighthouse
- [x] Salem Willows Park — beaches, carousel
- [x] Roger Conant Statue (Brown St & Washington Sq)
- [x] McIntire Historic District — Federal-style mansions, Samuel McIntire
- [x] Chestnut Street — grand residential boulevard
- [x] Ropes Mansion — Hocus Pocus filming location

**Visitor Services (4/5 curated):**
- [x] NPS Regional Visitor Center (2 New Liberty St) — Heritage Trail start
- [x] Salem MBTA Station (252 Bridge St)
- [x] Salem Ferry Terminal (10 Blaney St) — seasonal
- [x] Museum Place Garage (1 New Liberty St) — $1.25/hr, EV charging
- [x] South Harbor Garage (10 Congress St) — waterfront parking, EV charging

### Step 5.2: Witch & occult shops (5 curated)
- [x] Catalog witch/occult/metaphysical shops on Essex Street and surrounds
  - Crow Haven Corner, Hex, Omen, Artemisia Botanicals, Coven's Cottage
  - Each with: name, address, GPS, description, specialty tags, provenance

### Step 5.3: Restaurants, bars, cafes (13 curated)
- [x] Catalog tourist-area dining establishments:
  - Restaurants (7): Turner's, Sea Level, Finz, Flying Saucer, Rockafellas, Ledger, Opus
  - Bars (3): Mercy Tavern, Notch Brewing, Bit Bar
  - Cafes (3): Gulu-Gulu, Jaho Coffee, Brew Box
  - Each with: name, address, GPS, cuisine type, price range, description, historical notes, tags, provenance
- [x] Key areas covered: Pickering Wharf, Essex Street, Derby Street, downtown

### Step 5.4: Lodging (5 curated)
- [x] Catalog tourist lodging:
  - Hotels: Hawthorne Hotel, Salem Waterfront Hotel
  - Inns: Salem Inn, Coach House Inn
  - B&Bs: Morning Glory
  - Each with: address, GPS, price range, phone, website, historical notes, provenance

### Step 5.5: Leverage LocationMapApp's existing POI database
- [ ] For Salem app: set default search center to Salem (deferred — UI integration in Phase 6+)
- [ ] Auto-populate on first launch: restaurants, cafes, shops, parking, fuel, transit in Salem area
- [ ] Salem-enhanced POIs OVERLAY on top of generic Overpass POIs
  - If a Salem business matches an Overpass POI → merge (Salem data takes priority)
  - If no match → Salem business appears as its own marker with richer detail

### Step 5.6: Data provenance & staleness infrastructure
- [x] Added provenance fields to all 9 Room entities (data_source, confidence, verified_date, created_at, updated_at, stale_after)
- [x] Added staleness-aware DAO queries (findStale, findBySource, markUpdated, setStaleAfter)
- [x] Updated SalemContentRepository with provenance methods
- [x] Room DB version 2, fallbackToDestructiveMigration
- [x] Updated content pipeline output models with Provenance data class
- [x] Created PostgreSQL schema (salem-schema.sql) — 9 Salem tables + provenance on existing pois
- [x] Created Node.js Salem API endpoints (lib/salem.js) — CRUD + /salem/sync + /salem/stats
- [x] All backward compatible — no changes to existing LocationMapApp endpoints

### Step 5.7: Verify
- [x] Pipeline generates 37 POIs + 23 businesses + 3 tours (60 stops) with provenance (0 errors, 0 warnings)
- [x] `salem_content.db` created (1.7MB, 841 records, Room identity hash matched)
- [x] `./gradlew :app-salem:assembleDebug` builds successfully
- [ ] Emulator verification (this session)
- [ ] Git commit: "Phase 5: Salem POI catalog + provenance + staleness + API"

---

## Phase 6 — Tour Engine

**Goal:** Implement the guided walking tour system.

### Step 6.1: Tour data model
- [ ] Create `TourModels.kt`:
  - `TourTheme` enum: WITCH_TRIALS, MARITIME, LITERARY, ARCHITECTURE, PARKS, FOOD_DRINK, COMPLETE, OCTOBER_SPECIAL, HERITAGE_TRAIL, CUSTOM
  - `TourProgress` data class: tourId, currentStopIndex, completedStops, startTime, totalDistanceWalked
  - `ActiveTour` data class: tour, stops (ordered), progress, isActive

### Step 6.2: Tour engine
- [ ] Create `TourEngine.kt`:
  - `startTour(tourId)` — load tour + stops, begin GPS tracking
  - `advanceToNextStop()` — mark current complete, update progress
  - `skipStop()` — skip without completing
  - `reorderStops(newOrder)` — user rearranges
  - `addStop(poiId)` — insert POI from another theme
  - `removeStop(poiId)` — remove from active tour
  - `pauseTour()` / `resumeTour()` — persist progress
  - `endTour()` — summary stats
  - Emits `Flow<TourState>` for UI observation

### Step 6.3: Tour selection UI
- [ ] Create tour selection screen (RecyclerView or Compose list):
  - Tour card: icon, name, description, stop count, estimated time, distance
  - Category filter chips
  - "Build Your Own" option
- [ ] Tour detail screen:
  - Route preview on map with numbered markers
  - Stop list with descriptions and distances
  - "Start Tour" button
  - Estimated time, total distance

### Step 6.4: Active tour HUD
- [ ] Map overlay showing:
  - Route polyline (connecting all stops)
  - Numbered stop markers (completed = green, current = blue, upcoming = gray)
  - Current stop name + distance to next
  - Progress bar (stops completed / total)
  - Tour controls: Next, Skip, Pause, End
- [ ] Bottom sheet with current stop details

### Step 6.5: Tour progress persistence
- [ ] Save to SharedPreferences:
  - Active tour ID, current stop index, completed stops list
  - Restore on app relaunch
  - "Resume Tour?" prompt on next open

### Step 6.6: Custom tour builder
- [ ] "Build Your Own" flow:
  - Browse all POIs by category
  - Add/remove stops
  - Auto-optimize route (nearest-neighbor TSP)
  - Show estimated time and distance
  - Save as personal tour

### Step 6.7: Time-filtered tours
- [ ] "I have X minutes" filter:
  - 30 min → top 4-5 must-see sites
  - 60 min → essential witch trials + 1-2 maritime
  - 90 min → full themed tour
  - 3+ hours → complete Salem
- [ ] Algorithm: select highest-priority POIs that fit within time budget, optimize route

### Step 6.8: Verify
- [ ] Can select and start any pre-defined tour
- [ ] Route displays correctly on map
- [ ] Progress tracks through stops
- [ ] Can skip, reorder, add stops
- [ ] Progress persists across app restarts
- [ ] Custom tour builder generates valid routes
- [ ] Git commit: "Tour engine — guided walking tours with customization"

---

## Phase 7 — GPS Geofence Triggers & Narration

**Goal:** Automatic GPS-triggered narration as the tourist walks through Salem.

### Step 7.1: Tour geofence manager
- [ ] Create `TourGeofenceManager.kt`:
  - On tour start: create geofence zones for all tour stops (50m radius circles)
  - Convert tour POIs to `TfrZone` format for core `GeofenceEngine`
  - Load into `GeofenceEngine.loadZones()`
  - On each GPS update: `GeofenceEngine.checkPosition()` → detect proximity/entry
  - On POI entry: emit event → trigger narration + UI update

### Step 7.2: Android TTS integration
- [ ] Create `NarrationManager.kt`:
  - Initialize `TextToSpeech` engine with `Locale.US`
  - Speech rate: 0.9x (slightly slower for historical content)
  - Methods:
    - `speakShortNarration(poiId)` — approach summary (15-30 sec)
    - `speakLongNarration(poiId)` — full story (60-120 sec)
    - `speakQuote(text)` — primary source quote
    - `speakTransition(fromPoiId, toPoiId)` — walking transition
    - `pause()`, `resume()`, `stop()`, `skip()`
  - Queue management: queue segments, play in order
  - Emit `Flow<NarrationState>` (IDLE, SPEAKING, PAUSED, segment info)

### Step 7.3: Narration trigger flow
```
GPS update → GeofenceEngine.checkPosition()
  → PROXIMITY alert (approaching, ~50m out)
    → Vibrate briefly
    → Show notification: "Approaching: Witch Trials Memorial"
    → If auto-narration ON: speakShortNarration()
  → ENTRY alert (arrived, within ~20m)
    → Show rich POI card
    → If auto-narration ON: speakLongNarration()
    → "Hear More" button → speakQuote() with primary sources
  → EXIT alert (leaving)
    → If on active tour: speakTransition() to next stop
```

### Step 7.4: Narration controls
- [ ] On-screen controls:
  - Play/Pause button
  - Skip to next segment
  - Stop narration
  - Speed control (0.75x, 1.0x, 1.25x)
  - Volume control
- [ ] Notification controls (MediaStyle notification):
  - Play/Pause, Skip, Stop
  - Current POI name as notification title
  - Narration text snippet as notification body
- [ ] Respect phone ringer mode (silent = no narration, vibrate only)

### Step 7.5: Ambient mode (no active tour)
- [ ] Even without an active tour, enable "Ambient Narration":
  - Monitor GPS passively
  - When user walks near ANY Salem POI (100m): subtle notification
  - "Did you know? You're near the Witch House — the only building still standing with direct ties to the 1692 trials."
  - User can tap to hear full narration
  - Toggle on/off in settings

### Step 7.6: Verify
- [ ] Simulate GPS walk along Witch Trial Trail
- [ ] Geofence triggers fire at correct distances
- [ ] Short narration plays on approach
- [ ] Long narration plays on arrival
- [ ] Controls (pause, resume, skip) work
- [ ] Notification shows with media controls
- [ ] Ambient mode triggers for non-tour POIs
- [ ] Git commit: "GPS geofence triggers and TTS narration system"

---

## Phase 8 — Walking Directions

**Goal:** Turn-by-turn walking directions from user's location to any POI.

### Step 8.1: OSRM routing integration
- [ ] Create `WalkingDirections.kt`:
  - Call OSRM public API: `router.project-osrm.org/route/v1/walking/{coords}`
  - Parse GeoJSON response → list of route segments
  - Extract: total distance, estimated time, step-by-step instructions
  - Cache responses (same route = same result within an hour)

### Step 8.2: Route display on map
- [ ] Draw walking route as polyline on osmdroid map
  - Color: tour theme color or branded gold
  - Width: 6px with border
  - Dashed for future segments, solid for current
- [ ] Turn markers at key intersections
- [ ] Distance remaining overlay

### Step 8.3: "Get me there" feature
- [ ] Any POI detail card: "Walk Here" button
  - Calculate route from current GPS to selected POI
  - Show route on map + estimated walking time
  - Optional: TTS turn-by-turn ("Turn left on Essex Street in 100 feet")

### Step 8.4: Tour route display
- [ ] When on active tour: show route from current location → next stop
- [ ] Preview: show entire tour route as connected polyline
- [ ] Completed segments shown in faded color

### Step 8.5: Route optimization for custom tours
- [ ] Nearest-neighbor heuristic:
  1. Start from current GPS location
  2. Find nearest unvisited stop
  3. Go there, repeat until all visited
- [ ] Display total distance and estimated walking time
- [ ] Allow manual reordering (drag-and-drop stop list)

### Step 8.6: Verify
- [ ] Walking directions display correctly on map
- [ ] Estimated times are reasonable (~5 km/h walking)
- [ ] "Walk Here" works from any POI card
- [ ] Tour route preview shows all stops connected
- [ ] Route optimization produces reasonable order
- [ ] Git commit: "Walking directions — OSRM integration"

---

## Phase 9 — Haunted Happenings & Events Integration

**Goal:** Comprehensive coverage of Salem's events, shows, exhibits, and seasonal offerings.

### Step 9.1: Events calendar database
- [ ] Populate `events_calendar` table with:

**Haunted Happenings (October):**
- [ ] Grand Parade (early October)
- [ ] Artisan marketplace (every weekend)
- [ ] Haunted houses: all major commercial haunted attractions
- [ ] Ghost tours: all operators with schedules and prices
- [ ] Psychic fairs, seances, special events
- [ ] Costume contests, pumpkin decorating
- [ ] Extended museum hours during October

**Year-round events:**
- [ ] Salem Film Fest (March)
- [ ] Salem's So Sweet (February)
- [ ] Salem Arts Festival (June)
- [ ] Heritage Days (August)

**Museum exhibits:**
- [ ] Peabody Essex Museum — current and upcoming exhibits
- [ ] Salem Witch Museum — permanent + special exhibits
- [ ] House of Seven Gables — programs, tours, events
- [ ] Pioneer Village — seasonal programming

**Event venues:**
- [ ] Witch House — event schedule
- [ ] Old Town Hall
- [ ] Hawthorne Hotel events
- [ ] Hamilton Hall
- [ ] Peabody Essex Museum event space

### Step 9.2: Events UI
- [ ] Events tab/section:
  - "What's Happening Today" — filtered by current date
  - "This Week" — upcoming events
  - "This Month" — monthly calendar view
  - Category filter: haunted, museum, food, music, family, outdoor
- [ ] Event detail card:
  - Name, venue (linked to POI), description
  - Date/time, admission, "Walk Here" button
  - Share button

### Step 9.3: Seasonal awareness
- [ ] October mode:
  - Special October tour theme unlocked
  - Haunted Happenings banner/callout
  - Extended hours reflected in POI data
  - Halloween-themed map overlays (optional)
- [ ] "On this date in 1692..." feature:
  - Cross-reference current date with timeline events
  - Show what was happening in Salem on this date 334 years ago
  - Daily historical notification (optional)

### Step 9.4: Verify
- [ ] Events display with correct dates and venues
- [ ] "Today" filter shows only current events
- [ ] Tapping venue navigates to POI on map
- [ ] October content appears in October, hidden otherwise
- [ ] "On this date" feature shows accurate historical correlation
- [ ] Git commit: "Events calendar — Haunted Happenings, exhibits, seasonal content"

---

## Phase 10 — Polish, Branding & Play Store

**Goal:** Final polish, app icon, store listing, tiered pricing release (see Business Model section).

### Step 10.1: App icon & branding
- [ ] Design app icon (Salem themed — consider: witch silhouette, historic building outline, crescent moon, vintage map element)
- [ ] Splash screen with Salem imagery
- [ ] Consistent typography and color scheme throughout
- [ ] About screen with credits, historical source citations

### Step 10.2: Offline mode
- [ ] Pre-cache osmdroid map tiles for Salem area (zoom 12-18)
  - ~42.50 to 42.54 lat, -70.91 to -70.87 lng
- [ ] All content in local Room DB (already offline)
- [ ] Walking directions: cache recent routes, fallback to straight-line distance
- [ ] Indicate online/offline status in status bar

### Step 10.3: Performance optimization
- [ ] Lazy-load narration scripts (don't load all at startup)
- [ ] Marker clustering for dense POI areas (Essex Street)
- [ ] Background GPS: use foreground service with notification
- [ ] Battery optimization: reduce GPS frequency when user is stationary

### Step 10.4: Accessibility
- [ ] Content descriptions on all map markers
- [ ] TalkBack compatibility
- [ ] High-contrast mode
- [ ] Wheelchair accessibility flags on POIs
- [ ] Large text support

### Step 10.5: Google Play Store
- [ ] Set up Google Play Developer account (if not already)
- [ ] Configure tiered pricing via Google Play billing (Free + IAP / subscriptions)
- [ ] Store listing:
  - Title: "Wicked Salem Witch City Tour"
  - Short description: "GPS-guided walking tours of Salem, MA with historical narration"
  - Full description: features, tour themes, historical accuracy claims, content sources
  - Screenshots (5-8): map view, tour selection, narration, POI detail, walking directions
  - Feature graphic
  - Category: Travel & Local
  - Content rating: Everyone
- [ ] Privacy policy (required for paid apps)
- [ ] Generate signed APK/AAB

### Step 10.6: Verify (full regression)
- [ ] Complete tour walkthrough (simulated GPS): Witch Trial Trail start-to-finish
- [ ] All narration triggers correctly
- [ ] Walking directions display and update
- [ ] Business search finds restaurants, bars, shops
- [ ] Events calendar shows current events
- [ ] MBTA transit works (Salem Station trains)
- [ ] Weather displays for Salem
- [ ] Offline mode works (airplane mode after initial load)
- [ ] App installs from signed APK
- [ ] Git commit: "v1.0.0 — WickedSalemWitchCityTour release candidate"

---

## Content Organization Strategy

The content is large. Here's how it's organized:

### Hierarchy

```
Content Database (Room)
├── Tour POIs (~50-100)          ← Curated stops with narration scripts
│   ├── Short narration          ← 15-30 sec TTS (approach trigger)
│   ├── Long narration           ← 60-120 sec TTS (at-location trigger)
│   └── Deep-dive content        ← Primary sources, quotes, extended history
├── Salem Businesses (~200-500)  ← Every restaurant, bar, shop, B&B, service
├── Historical Figures (~50)     ← From Salem project, Tier 1 + Tier 2
│   ├── Short bio               ← 2-3 sentences
│   ├── Full bio                 ← Complete narrative
│   └── Narration script         ← TTS-optimized biography
├── Historical Facts (~500)      ← Selected from Salem project's 3,891
├── Timeline Events (~80)        ← Anchor + key minor events
├── Primary Sources (~200)       ← Court records, petitions, letters
├── Tours (~10)                  ← Pre-defined route definitions
│   └── Tour Stops              ← Ordered POI references per tour
└── Events Calendar (~50-100)    ← Haunted Happenings, exhibits, shows
```

### Category Taxonomy

```
HISTORICAL
├── witch_trials        — 1692 sites, memorials, trial locations
├── maritime            — wharves, ships, Custom House, merchant era
├── literary            — Hawthorne, House of Seven Gables
├── architecture        — McIntire, Federal, Colonial
└── landmarks           — statues, monuments, historic markers

ATTRACTIONS
├── museum              — PEM, Witch Museum, Seven Gables
├── event_venue         — theaters, halls, outdoor event spaces
├── park                — Salem Common, Winter Island, Willows
└── cemetery            — Charter Street, Broad Street, Harmony Grove

BUSINESS
├── restaurant          — full service dining
├── bar_pub             — bars, pubs, breweries
├── cafe_bakery         — coffee shops, bakeries
├── lodging             — hotels, B&Bs, inns
├── shop_occult         — witch shops, metaphysical, tarot
├── shop_retail         — general retail, gifts, souvenirs
└── service             — visitor centers, parking, transit

EVENTS
├── haunted_tour        — ghost tours, haunted houses
├── museum_exhibit      — PEM exhibits, special shows
├── festival            — Haunted Happenings, Arts Festival
├── show                — performances, reenactments
└── seasonal            — October-only events
```

### Content Sourcing

| Content Type | Primary Source | Secondary Source |
|-------------|---------------|-----------------|
| Witch Trials history | ~/Development/Salem project JSON | Salem Witch Museum, NPS |
| Historical figures | ~/Development/Salem NPC profiles | Primary source documents |
| Primary source quotes | ~/Development/Salem primary_sources | UVA Salem Witchcraft Papers |
| Maritime history | Manual research + narration writing | NPS Salem Maritime NHS |
| Literary (Hawthorne) | Manual research + narration writing | Seven Gables, PEM |
| Business listings | Overpass/OSM (base) + manual curation | Google Places, Yelp |
| Events calendar | Manual entry from salemma.gov, salem.org | Haunted Happenings website |
| Walking routes | Heritage Trail official route + custom | Salem Heritage Trail org |

---

## Tour Definitions

### Tour 1: Witch Trial Trail
**Theme:** witch_trials | **Stops:** 12-14 | **Time:** 90 min | **Distance:** ~2.5 km

1. NPS Visitor Center (start, orientation)
2. Salem Witch Museum (overview of 1692)
3. Roger Conant Statue (Salem's founding)
4. Salem Common (militia training ground, community center)
5. Witch House / Corwin House (Judge Corwin's home — only surviving 1692 structure)
6. Court House site (70 Washington St — trial location marker)
7. Judge Hathorne's home site (118 Washington St)
8. Sheriff Corwin's home site (148 Washington St)
9. Witch Trials Memorial (20 executed honored here)
10. Charter Street Cemetery (oldest burial ground)
11. Salem Jail site (Federal & St. Peter's — where accused were held)
12. Proctor's Ledge Memorial (confirmed execution site)
13. *Optional:* Rebecca Nurse Homestead (Danvers — requires transport)

### Tour 2: Maritime Heritage
**Theme:** maritime | **Stops:** 8-10 | **Time:** 75 min | **Distance:** ~2 km

1. NPS Visitor Center (orientation)
2. Custom House (Hawthorne worked here, maritime commerce hub)
3. Derby Wharf (1/2 mile wharf, Friendship replica)
4. Derby Wharf Light Station
5. Narbonne House (c.1675, maritime neighborhood)
6. House of the Seven Gables (1668, Turner family — sea captains)
7. Pickering Wharf (modern marina, shops)
8. Peabody Essex Museum (East India Marine Society collection)
9. McIntire District (wealth from maritime trade)

### Tour 3: Hawthorne's Salem
**Theme:** literary | **Stops:** 7 | **Time:** 60 min | **Distance:** ~1.5 km

1. Hawthorne's Birthplace (on Seven Gables campus)
2. House of the Seven Gables (inspiration for the novel)
3. Custom House (wrote Scarlet Letter intro here)
4. 14 Mall Street (where he actually wrote The Scarlet Letter)
5. "Castle Dismal" (10 1/2 Herbert St — boyhood home)
6. Hawthorne Hotel (named in his honor)
7. Charter Street Cemetery (Hathorne grave — the judge ancestor)

### Tour 4: Architecture Walk
**Theme:** architecture | **Stops:** 8-10 | **Time:** 70 min | **Distance:** ~2 km

1. Witch House (1675 — First Period colonial)
2. House of the Seven Gables (1668 — oldest timber-frame mansion)
3. Custom House (1819 — Federal style)
4. East India Marine Hall/PEM (1825)
5. Chestnut Street (grand Federal mansions)
6. Ropes Mansion (1727 — Colonial Georgian)
7. Hamilton Hall (1805 — Samuel McIntire designed)
8. McIntire Historic District walking loop
9. Pioneer Village (1630 construction methods)

### Tour 5: Heritage Trail (Official City Route)
**Theme:** heritage | **Stops:** 14 | **Time:** 90 min | **Distance:** ~2.4 km (1.5 mi)

Follow the red line painted on Salem's sidewalks:
1. Lappin Park → 2. Witch House → 3. Salem Witch Museum → 4. Derby Square
5. House of Seven Gables → 6. Charter Street Cemetery → 7. Charlotte Forten Park
8. Salem Maritime NHS → 9. Hawthorne Statue → 10. Salem Arts Association
11. Salem Common → 12. Witch Dungeon Museum → 13. East India Marine Hall/PEM
14. Roger Conant Statue

### Tour 6: Food & Drink Trail
**Theme:** food_drink | **Stops:** 10-15 | **Time:** Self-paced

Curated restaurant/bar crawl through Salem's dining scene with historical context for each building/location. Updated seasonally.

### Tour 7: Complete Salem
**Theme:** complete | **Stops:** 20-24 | **Time:** 3+ hours | **Distance:** ~4 km

Best-of from all themes: top witch trials sites + maritime highlights + literary landmarks + architecture gems + parks.

### Tour 8: October Special (Haunted Happenings)
**Theme:** october | **Stops:** 10-14 | **Time:** 90 min | **Seasonal:** October only

Spooky history + Halloween events + haunted tour starting points + October-only attractions. Active only during Haunted Happenings season.

---

## Salem POI Master List

### GPS Coordinates — Key Locations

| # | POI | Lat | Lng | Address | Category |
|---|-----|-----|-----|---------|----------|
| 1 | Witch Trials Memorial | 42.5205 | -70.8862 | 24 Liberty St | witch_trials |
| 2 | Salem Witch Museum | 42.5228 | -70.8888 | 19 1/2 Washington Sq N | witch_trials, museum |
| 3 | Witch House (Corwin House) | 42.5215 | -70.8930 | 310 1/2 Essex St | witch_trials, architecture |
| 4 | House of Seven Gables | 42.5183 | -70.8833 | 115 Derby St | literary, architecture |
| 5 | Peabody Essex Museum | 42.5222 | -70.8878 | 161 Essex St | museum, maritime |
| 6 | Charter Street Cemetery | 42.5205 | -70.8860 | 51 Charter St | witch_trials, cemetery |
| 7 | Salem Maritime NHS | 42.5190 | -70.8840 | 160 Derby St | maritime |
| 8 | Custom House | 42.5190 | -70.8840 | 160 Derby St | maritime, literary |
| 9 | Derby Wharf | 42.5180 | -70.8830 | Derby St | maritime, park |
| 10 | Proctor's Ledge Memorial | 42.5175 | -70.8980 | 7 Pope St | witch_trials |
| 11 | Salem Common | 42.5240 | -70.8890 | 31 Washington Sq | park |
| 12 | Pioneer Village | 42.5100 | -70.8820 | 98 West Ave | architecture |
| 13 | Winter Island Park | 42.5310 | -70.8680 | 50 Winter Island Rd | park |
| 14 | Salem Willows Park | 42.5310 | -70.8740 | Fort Ave | park |
| 15 | Rebecca Nurse Homestead | 42.5630 | -70.9380 | 149 Pine St, Danvers | witch_trials |
| 16 | Roger Conant Statue | 42.5228 | -70.8890 | Brown St & Washington Sq | landmark |
| 17 | NPS Visitor Center | 42.5222 | -70.8875 | 2 New Liberty St | visitor_services |
| 18 | Salem MBTA Station | 42.5245 | -70.8950 | 252 Bridge St | transit |
| 19 | Salem Ferry Terminal | 42.5170 | -70.8830 | 10 Blaney St | transit |
| 20 | McIntire Historic District | 42.5200 | -70.8920 | Chestnut/Federal Sts | architecture |
| 21 | Hawthorne Hotel | 42.5235 | -70.8895 | 18 Washington Sq W | literary, lodging |
| 22 | Pickering Wharf | 42.5185 | -70.8845 | Pickering Wharf | food_drink, shopping |
| 23 | Court House site | 42.5215 | -70.8890 | 70 Washington St | witch_trials |
| 24 | Salem Jail site | 42.5210 | -70.8870 | Federal & St. Peter's | witch_trials |
| 25 | Derby Wharf Light | 42.5165 | -70.8790 | End of Derby Wharf | maritime |
| 26 | Hawthorne Birthplace | 42.5183 | -70.8833 | 115 Derby St (campus) | literary |
| 27 | Hawthorne Statue | 42.5195 | -70.8850 | Hawthorne Blvd | literary |
| 28 | Ropes Mansion | 42.5210 | -70.8905 | 318 Essex St | architecture |
| 29 | Old Town Hall | 42.5210 | -70.8880 | Derby Square | architecture, landmark |
| 30 | Lappin Park | 42.5215 | -70.8885 | Essex & Washington | landmark |

**Geographic center of tourist activity:** 42.521, -70.887

---

## Data Sources Reference

### From ~/Development/Salem Project

| Source File | Records | Lines | Content |
|-------------|---------|-------|---------|
| `data/json/npcs/_all_npcs.json` | 2,174 NPCs | 174,103 | Character profiles, bios, quotes, relationships |
| `data/json/facts/_all_facts.json` | 3,891 facts | 167,596 | Historical facts by person, place, date |
| `data/json/buildings/_all_buildings.json` | 424 buildings | 66,030 | Architecture, rooms, atmosphere, sensory detail |
| `data/json/primary_sources/_all_primary_sources.json` | 4,950 chunks | 138,846 | Court records, diaries, sermons, petitions |
| `data/json/events/_all_events.json` | 40 anchors | 1,596 | Major crisis events with dates and descriptions |
| `data/json/relationships/_all_relationships.json` | 10,033 | 313,677 | Social connections, family, political alliances |
| `client/src/data/buildingCoordinates.ts` | 30 buildings | ~200 | Grid coordinates for 1692 village layout |

**Total available content:** ~29,800 data files, ~1.1M lines of JSON

### Scholarly Sources (via Salem Project)

- UVA Salem Witchcraft Papers (140 case files)
- Upham *Salem Witchcraft* (1867)
- Boyer & Nissenbaum *Salem Possessed*
- Mary Beth Norton *In the Devil's Snare*
- Marilynne Roach *Six Women of Salem* + *Day-by-Day Chronicle*
- Cotton Mather *Wonders of the Invisible World*
- Sewall *Diary* (211K words)
- Calef *More Wonders*
- Hale *Modest Enquiry*
- Parris Sermons (7 texts, UVA digitized)
- 15+ additional secondary sources

---

## Verification Checkpoints

### After Phase 1 (Core Extraction):
- `./gradlew :core:assembleDebug` — core library builds
- `./gradlew :app:assembleDebug` — generic app unchanged
- Emulator: all existing features work identically

### After Phase 2 (Salem Shell):
- `./gradlew :app-salem:assembleDebug` — Salem app builds
- Map centered on Salem, core features operational

### After Phase 5 (POI Catalog):
- All POIs display on map at correct GPS positions
- Category filtering works across all types
- Business search returns restaurants, shops, lodging

### After Phase 6 (Tour Engine):
- Select tour → route on map with numbered stops
- Tour progress tracks through stops
- Custom tour builder generates valid routes

### After Phase 7 (Narration):
- GPS proximity triggers narration automatically
- Short narration on approach, long on arrival
- Controls work: pause, resume, skip, speed

### After Phase 8 (Walking Directions):
- Route polyline displays on map
- "Walk Here" from any POI card
- Estimated times reasonable for walking speed

### End-to-End Acceptance Test:
- Simulate walking Witch Trial Trail: Salem Station → 12 stops → finish
- Narration triggers at every stop
- Walking directions guide between stops
- Can browse restaurants mid-tour
- MBTA transit shows Salem trains
- Weather works for Salem
- Offline mode functional (airplane mode after load)
- Events calendar shows current events

---

## Business Model & Monetization

### Tiered Pricing

| Tier | Price | Features |
|------|-------|----------|
| **Free** | $0 | Limited tour ability (1 tour preview), Google Ads throughout, NO transit, NO weather, basic POI map |
| **Explorer** | $4.99 (one-time) | Moderate tour ability (2-3 tours), all North Shore POIs, reduced ads, local business directory |
| **Premium** | $9.99 (one-time) | Full Salem POIs, ALL tours, transit info, detailed historical + POI content, all walking directions, no ads |
| **Salem Village LLM** | $49.99/month | All Premium features + conversational AI with Salem Village NPCs via Salem LLM. Talk to historical figures. |

### Revenue Streams

1. **Google Ads (Free tier)** — Standard AdMob banner/interstitial ads. Primary conversion funnel to paid tiers.
2. **Salem business advertising** — Local businesses pay for featured placement. Geofenced proximity ads push content to users near the business.
3. **Loyalty/discount program** — Visit X highlighted partner shops → app generates discount code (vendor-specific %). Drives foot traffic for merchants, engagement for users.
4. **Merchant partnerships** — Paid POI placements, featured listings, sponsored tour stops, "Recommended by Wicked Salem" badges.
5. **Subscription revenue (LLM tier)** — Monthly recurring for AI-powered conversations with Salem Village historical figures.

### Feature Gating Architecture

```
Free tier:       core map + limited POIs + 1 tour preview + ads
Explorer tier:   unlock via Google Play IAP → expands POI database + 2-3 tours
Premium tier:    unlock via Google Play IAP → full content + transit + weather + all tours
LLM tier:        Google Play subscription → all Premium + /salem/chat endpoint
```

Implementation: `FeatureGate.kt` in `:app-salem` checks tier via Google Play Billing Library. Core engine (`core`) is tier-agnostic — gating is app-level only.

### Advertising & Marketing Platform

- **Geofenced business ads**: When user is within X meters of a paying merchant, show contextual card (not intrusive popup). Respect user preferences.
- **Sponsored tour stops**: Merchants can sponsor a stop on a tour route. "This stop brought to you by [Business]."
- **Loyalty program**: `LoyaltyTracker` tracks check-ins at partner locations. After X visits, generate redeemable discount code. Codes tied to merchant ID + user ID.
- **Analytics dashboard** (future): Merchant portal showing foot traffic, ad impressions, redemption rates.
- **Business content section needed**: Full review of business plan profitability, merchant acquisition strategy, pricing for ad placements.

### North Shore Merchant Expansion

Coverage must extend beyond downtown Salem to include:
- Marblehead, Beverly, Danvers, Peabody, Swampscott
- All restaurants, bars, cafes, shops, lodging, attractions in the region
- Overpass/OSM as base → manual curation for quality
- Business categories: dining, shopping, entertainment, services, lodging, attractions
- Each merchant record needs: hours, phone, website, description, GPS, tags, historical notes (where applicable)
- Target: 500+ North Shore businesses in Explorer tier, 200+ Salem-specific in Premium tier

---

## Technical Foundation — Audit Recommendations

### Priority 1: Database Hardening (Do Before Phase 6 Code)

**1a. Add @Index to all Room entities**
All findNearby(), findByCategory(), and FK-based queries currently do full table scans. Add indexes:
- `TourPoi`: lat, lng, category, data_source, priority
- `SalemBusiness`: lat, lng, business_type, data_source
- `HistoricalFigure`: primary_poi_id, role, data_source
- `HistoricalFact`: poi_id, figure_id, category, data_source
- `TimelineEvent`: crisis_phase, poi_id, date, data_source
- `PrimarySource`: figure_id, poi_id, source_type, data_source
- `EventsCalendar`: venue_poi_id, seasonal_month, start_date, data_source
- `TourStop`: tour_id, poi_id (explicit indexes beyond composite PK)

**1b. Add @ForeignKey constraints**
All FK relationships are implicit. Declare with CASCADE DELETE:
- `TourStop.tour_id` → `Tour.id`
- `TourStop.poi_id` → `TourPoi.id`
- `HistoricalFact.poi_id` → `TourPoi.id`
- `HistoricalFact.figure_id` → `HistoricalFigure.id`
- `HistoricalFigure.primary_poi_id` → `TourPoi.id`
- `EventsCalendar.venue_poi_id` → `TourPoi.id`

**1c. Replace fallbackToDestructiveMigration**
Use explicit `Migration(2, 3)` classes. Set `exportSchema = true` for migration validation.

**1d. Add @Delete/@Update to all DAOs**
Currently only INSERT and SELECT exist. Add full CRUD.

**1e. Add @Transaction on batch operations**
Multi-entity inserts must be atomic.

### Priority 2: JSON Content Packages

**Design:** All POI/content data must be deliverable as versioned JSON bundles:
- Content packages are the **source of truth**, Room DB is the **cache**
- App checks for newer packages on startup (network available → hit `/salem/content/version`)
- On Android version upgrade, content refresh is part of the upgrade process
- Stale local data overwritten by fresh packages
- Same pattern as LocationMapApp — portable, versionable, independently updatable

**Implementation:**
- Versioned JSON bundles in `assets/content/v{N}/` for offline baseline
- Server endpoint `/salem/content/check?version=N` returns update availability
- Server endpoint `/salem/content/download?since=N` returns delta JSON
- `ContentSyncManager.kt` in `:app-salem` handles download → parse → Room insert/update
- Package format: `{ version: N, pois: [...], businesses: [...], tours: [...], ... }`

### Priority 3: FTS5 Search (Replace LIKE '%query%')

All 6 DAOs with search methods use `LIKE '%' || :query || '%'` which:
- Prevents index usage (leading wildcard)
- Does full table scan on every keystroke
- Is unreliable for JSON tag matching

**Fix:** Implement SQLite FTS5 virtual tables:
- `tour_pois_fts` → name, description, short_narration
- `salem_businesses_fts` → name, description, tags
- `historical_figures_fts` → name, short_bio
- `historical_facts_fts` → title, description
- `primary_sources_fts` → title, excerpt
- Trigram tokenizer for fuzzy matching (same approach as LocationMapApp's pg_trgm)

### Priority 4: API Key Security

Currently hardcoded in source:
- MBTA API key in MbtaRepository.kt
- Windy Webcams API key in server.js

**Fix:**
- Move to `local.properties` (gitignored) → injected via BuildConfig at build time
- Server-side keys → environment variables (already partially done for DATABASE_URL)
- Add `secrets-gradle-plugin` for Android key management
- Never commit keys to version control

### Priority 5: Socket.IO Replacement Evaluation

Current: Socket.IO v2.1.0 (2021, outdated, security concerns)

**Options:**
- **OkHttp WebSocket** — Already in dependency tree (OkHttp 4.12.0). Native WebSocket support, no additional dependency. Simpler API. **Recommended.**
- **Ktor WebSocket** — Kotlin-native, coroutine-based. Heavier dependency.
- **Socket.IO v4.x** — Upgrade in place. More features than needed.

**Recommendation:** Migrate to OkHttp WebSocket. Eliminates Socket.IO dependency entirely. OkHttp is already in `:core`.

### Priority 6: Core API Backward Compatibility

**Principle:** Core engine (`:core`) must serve LocationMapApp, WickedSalem, and future apps. Salem-specific features NEVER go in `:core`.

**Rules:**
- All core API changes must be **additive** (new methods/classes, not breaking changes)
- Test core changes against both `:app` and `:app-salem` builds
- Salem-specific: `:app-salem` only (tour engine, narration, Salem content DB)
- Shared infrastructure: `:core` (geofencing, location, repositories, networking)
- Feature gating: `:app-salem` level, not `:core` level

### Priority 7: Network Security

- Remove `android:usesCleartextTraffic="true"` from AndroidManifest.xml
- Add `network_security_config.xml` allowing cleartext only for local dev (10.0.0.x)
- Enable `minifyEnabled true` + `shrinkResources true` for release builds
- Add `POST_NOTIFICATIONS` permission for Android 13+ proximity alerts
- Add ProGuard/R8 rules for Room, Gson, Retrofit, OkHttp

---

## Future Phases (Post-Launch)

### Phase 11 — Merchant Network & Advertising Platform
- [ ] Build merchant admin portal (web-based)
- [ ] Merchant self-service POI creation/editing
- [ ] Geofenced ad delivery system (proximity-triggered cards)
- [ ] Loyalty program engine (check-ins, discount code generation)
- [ ] Analytics dashboard (impressions, foot traffic, redemptions)
- [ ] North Shore merchant data expansion (500+ businesses)
- [ ] Sponsored tour stop system
- [ ] Business-to-app payment integration (Stripe)

### Phase 12 — Salem Village LLM Integration
- [ ] `/salem/chat` API endpoint — conversational interface to Salem LLM
- [ ] Character selection (50 historical figures available)
- [ ] Context-aware conversations (figure knows their history, location, relationships)
- [ ] Token metering for $49.99/mo subscription
- [ ] Conversation persistence (chat history)
- [ ] Voice input/output integration with TTS system
- [ ] Safety guardrails (historical accuracy, no harmful content)
- [ ] Rate limiting and abuse prevention

### Phase 13 — Additional Revenue Features
- [ ] In-app merchandise (Salem-branded items, print-on-demand)
- [ ] Tour booking integration (partner ghost tours, museum tickets)
- [ ] Photo/selfie spots with AR historical overlays
- [ ] Social sharing ("I completed the Witch Trial Trail!")
- [ ] Seasonal content packs (October Haunted Happenings premium content)

---

*Document created: 2026-04-03*
*Last updated: 2026-04-03 (Session 73: Business model, technical foundation, audit recommendations)*
*Project: LocationMapApp v1.5 → WickedSalemWitchCityTour*
*Author: Dean Maurice Ellis*
