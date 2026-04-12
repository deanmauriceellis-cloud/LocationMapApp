# WickedSalemWitchCityTour — Master Plan

**App Name:** WickedSalemWitchCityTour
**Package:** `com.example.wickedsalemwitchcitytour`
**Price:** Tiered (Free / $4.99 / $9.99 / $49.99-mo subscription)
**Platform:** Android (API 26+, Kotlin, osmdroid)
**Base:** LocationMapApp v1.5 multi-module platform
**Knowledge Base:** ~/Development/Salem (Salem Witch Trials 1692 simulation project)

---

## Table of Contents

### Core Development (Phases 1-9 — COMPLETE)
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

### UX Transformation (Phases 9A-9D — CODE, prioritized before Phase 10)
12. [Phase 9A — Splash Screen & Satellite Map Entry](#phase-9a--splash-screen--satellite-map-entry)
13. [Phase 9A+ — Tour Hardening & Offline Foundation](#phase-9a--tour-hardening--offline-foundation)
14. [Phase 9T — Salem Walking Tour Restructure](#phase-9t--salem-walking-tour-restructure)
15. [Phase 9P — POI Admin Tool (Developer Infrastructure)](#phase-9p--poi-admin-tool-developer-infrastructure)
16. [Phase 9U — Unified POI Table & SalemIntelligence Import](#phase-9u--unified-poi-table--salemintelligence-import) **← PRIORITY**
17. [Phase 9Q — Salem Domain Content Bridge](#phase-9q--salem-domain-content-bridge)
17. [Phase 9R — Historic Tour Mode (App-Side)](#phase-9r--historic-tour-mode-app-side)
18. [Phase 9B — Feature Tier Matrix & Gating Infrastructure](#phase-9b--feature-tier-matrix--gating-infrastructure)
16. [Phase 9C — User Settings & Alert Preferences](#phase-9c--user-settings--alert-preferences)
17. [Phase 9D — Contextual Alert System](#phase-9d--contextual-alert-system)

### Launch Readiness (Phases 10-11 — CODE)
16. [Phase 10 — Production Readiness & Offline Infrastructure](#phase-10--production-readiness--offline-infrastructure)
17. [Phase 11 — Branding, ASO & Play Store Launch](#phase-11--branding-aso--play-store-launch)

### Marketing & Business Development (Phases 12-14 — NO CODE)
18. [Phase 12 — Social Media & Digital Presence](#phase-12--social-media--digital-presence)
19. [Phase 13 — Fieldwork & Content Photography](#phase-13--fieldwork--content-photography)
20. [Phase 14 — Community Engagement & Salem Partnerships](#phase-14--community-engagement--salem-partnerships)

### Growth Features (Phases 15-16 — CODE)
21. [Phase 15 — In-App Virality & Gamification](#phase-15--in-app-virality--gamification)
22. [Phase 16 — iOS & Web Expansion (PWA)](#phase-16--ios--web-expansion-pwa)

### Future Phases (Post-Launch)
23. [Phase 17 — Merchant Network & Advertising Platform](#phase-17--merchant-network--advertising-platform)
24. [Phase 18 — Custom Narration & Audio Upgrade](#phase-18--custom-narration--audio-upgrade)
25. [Phase 19 — Salem Village LLM Integration](#phase-19--salem-village-llm-integration)
26. [Phase 20 — Additional Revenue Features](#phase-20--additional-revenue-features)

### Reference Sections
27. [Competitive Landscape](#competitive-landscape)
28. [Content Organization Strategy](#content-organization-strategy)
29. [Tour Definitions](#tour-definitions)
30. [Salem POI Master List](#salem-poi-master-list)
31. [Data Sources Reference](#data-sources-reference)
32. [Verification Checkpoints](#verification-checkpoints)
33. [Business Model & Monetization](#business-model--monetization)
34. [Technical Foundation — Audit Recommendations](#technical-foundation--audit-recommendations)
35. [Social Media Content Calendar](#social-media-content-calendar)
36. [Community Engagement Contacts](#community-engagement-contacts)
37. [Fieldwork Planning Guide](#fieldwork-planning-guide)

### Critical Timeline
> **Salem 400+ quadricentennial is 2026 — the city's 400th anniversary.** Every organization in Salem is mobilized. This is a once-in-a-generation marketing window. App MUST be in Play Store by **September 1, 2026** to capture October (1M+ visitors). Missing October 2026 means waiting until October 2027.

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
- **Tier gate:** Overpass POI auto-populate is $19.99+ tier only. Free/$4.99/$9.99 use curated Salem content only.

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
- [x] Emulator verification — confirmed working
- [ ] Git commit: "Phase 5: Salem POI catalog + provenance + staleness + API"

---

## Phase 6 — Tour Engine

**Goal:** Implement the guided walking tour system.

### Step 6.1: Tour data model
- [x] Create `TourModels.kt` (`tour/TourModels.kt`):
  - `TourTheme` enum: WITCH_TRIALS, MARITIME, LITERARY, ARCHITECTURE, PARKS, FOOD_DRINK, COMPLETE, OCTOBER_SPECIAL, HERITAGE_TRAIL, CUSTOM
  - `TourProgress` data class: tourId, currentStopIndex, completedStops, skippedStops, startTime, totalDistanceWalked, elapsedMs
  - `ActiveTour` data class: tour, stops (ordered), pois, progress — with currentStop/currentPoi/nextStop/nextPoi/remainingStops helpers
  - `TourState` sealed class: Idle, Loading, Active, Paused, Completed, Error
  - `TourSummary` data class: completion stats for end-of-tour dialog

### Step 6.2: Tour engine
- [x] Create `TourEngine.kt` (`tour/TourEngine.kt`):
  - `startTour(tourId)` — load tour + stops + POIs from SalemContentRepository, begin GPS tracking
  - `advanceToNextStop()` — mark current complete, update progress, auto-end if last
  - `skipStop()` — skip without completing
  - `reorderStops(newOrder)` — user rearranges
  - `addStop(poiId)` — insert POI from another theme (after current position)
  - `removeStop(poiId)` — remove from active tour (cannot remove current)
  - `pauseTour()` / `resumeTour()` — persist elapsed time across pauses
  - `endTour()` — summary stats, clear persisted progress
  - Emits `StateFlow<TourState>` for UI observation
  - `restoreIfSaved()` — restore from SharedPreferences on app startup
  - `onLocationUpdate(GeoPoint)` — feed GPS for distance calculations
  - Geofence-aware: `isAtCurrentStop()`, `distanceToStop()`, `isWithinGeofence()`
  - Haversine distance calculation between stops
  - `TourViewModel.kt` bridges TourEngine to SalemMainActivity UI

### Step 6.3: Tour selection UI
- [x] Tour selection dialog (full-screen, Salem-branded):
  - Tour card: name, description, stop count, estimated time, distance, difficulty chips
  - "Start Tour" gold button per card
  - Auto-redirect to active tour dialog if a tour is already running
- [ ] Category filter chips (deferred — can be added when more tours exist)
- [x] "Build Your Own" and "I Have X Minutes" buttons at bottom of tour list

### Step 6.4: Active tour HUD
- [x] Map overlay showing:
  - Route polyline (gold, connecting all stops in order)
  - Numbered stop markers (completed = green, current = blue, upcoming = gray)
  - Bottom HUD bar: current stop name + distance + Next/Skip/Info buttons
  - Tour status on StatusLineManager (TOUR priority level 4)
- [x] Active tour dialog with stop list, progress bar, and action buttons (Next/Skip/Pause/Resume/End)
- [x] Tour stop detail dialog (tap numbered marker): full POI info + "Show on Map" button
- [x] Tour completion dialog: stats summary (stops, time, distance, completion %)

### Step 6.5: Tour progress persistence
- [x] Save to SharedPreferences (tour_engine_prefs):
  - Active tour ID, current stop index, completed/skipped stops, distance, elapsed time, custom stop order
  - Restore on app relaunch via TourEngine.restoreIfSaved() (restores as Paused state)
  - TourViewModel calls restoreIfSaved() in init{}

### Step 6.6: Custom tour builder
- [x] "Build Your Own" flow (`SalemMainActivityTour.kt::showCustomTourBuilder()`):
  - Browse all POIs grouped by category with checkbox selection
  - Select All / Clear / Start Tour buttons
  - Live summary: stop count, route distance (km), estimated time
  - Auto-optimize route via nearest-neighbor TSP (`TourEngine.optimizeRoute()`)
  - Starts as custom tour with auto-generated TourStop transitions
  - Priority stars (\u2605) shown for must-see POIs

### Step 6.7: Time-filtered tours
- [x] "I Have X Minutes" dialog (`SalemMainActivityTour.kt::showTimeBudgetDialog()`):
  - 30 min → top 4-5 must-see sites
  - 60 min → essential witch trials + maritime
  - 90 min → full themed tour
  - 2 hours → extended exploration
  - 3+ hours → complete Salem experience
- [x] Algorithm (`TourEngine.buildTimeBudgetTour()`):
  - Filters out requires_transportation POIs for <2hr budgets
  - Selects highest-priority POIs that fit within time budget (~9 min/stop avg)
  - Route-optimized from user's current GPS via `optimizeRouteFromStart()`
  - Live preview: stop count + distance shown per time option
  - Tap to instantly start the generated tour

### Step 6.8: Verify
- [x] `./gradlew :app-salem:assembleDebug` builds clean (0 errors)
- [x] `./gradlew :app:assembleDebug` builds clean (core MenuEventListener backward compat)
- [ ] Emulator: can select and start any pre-defined tour
- [ ] Emulator: route displays correctly on map (polyline + numbered markers)
- [ ] Emulator: progress tracks through stops (advance/skip)
- [ ] Emulator: can add stops from active tour dialog
- [ ] Emulator: progress persists across app restarts (resume prompt)
- [ ] Emulator: custom tour builder generates valid routes
- [ ] Emulator: time-budget tour selects appropriate stops
- [ ] Git commit: "Phase 6: Tour engine — guided walking tours with customization"

---

## Phase 7 — GPS Geofence Triggers & Narration

**Goal:** Automatic GPS-triggered narration as the tourist walks through Salem.

### Step 7.1: Tour geofence manager
- [x] Create `TourGeofenceManager.kt` (`tour/TourGeofenceManager.kt`):
  - Lightweight haversine-based proximity engine (not polygon-based — tour stops are circles)
  - On tour start: `loadStops()` registers all tour stop geofences
  - On each GPS update: `checkPosition()` → APPROACH/ENTRY/EXIT detection
  - APPROACH zone: 2× geofence radius (~100m), ENTRY zone: geofence radius (~50m)
  - 60-second cooldown per stop per event type (prevents spam)
  - Emits `SharedFlow<TourGeofenceEvent>` for UI observation
  - Wired into TourEngine.onLocationUpdate() automatically

### Step 7.2: Android TTS integration
- [x] Create `NarrationManager.kt` (`tour/NarrationManager.kt`):
  - Initialize `TextToSpeech` engine with `Locale.US`
  - Speech rate: 0.9x default (slightly slower for historical content)
  - Methods:
    - `speakShortNarration(poi)` — approach summary (15-30 sec)
    - `speakLongNarration(poi)` — full story (60-120 sec)
    - `speakQuote(text, sourceName)` — primary source quote
    - `speakTransition(transitionText)` — walking transition between stops
    - `speakHint(text, poiName)` — ambient mode hint
    - `pause()`, `resume()`, `stop()`, `skip()`
  - Queue management: ArrayDeque of NarrationSegment, auto-advance on completion
  - UtteranceProgressListener → auto-play next segment
  - Emits `StateFlow<NarrationState>` (Idle, Speaking, Paused)
  - `cycleSpeed()` — cycles through 0.75x / 0.9x / 1.0x / 1.25x
  - Respects phone ringer mode (RINGER_MODE_NORMAL only)

### Step 7.3: Narration trigger flow
- [x] Wired in TourEngine.onLocationUpdate():
```
GPS update → TourGeofenceManager.checkPosition()
  → APPROACH alert (~100m out)
    → Toast: "Approaching: [POI name]"
    → If auto-narration ON: speakShortNarration()
  → ENTRY alert (within ~50m)
    → Toast: "Arrived: [POI name]"
    → Center map on POI (zoom 18, 800ms animation)
    → If auto-narration ON: speakLongNarration()
  → EXIT alert (leaving)
    → If on active tour: speakTransition() to next stop
```

### Step 7.4: Narration controls
- [x] On-screen narration bar (`SalemMainActivityTour.kt`):
  - Appears above tour HUD when narration is active
  - Shows POI name + segment type (Short Narration, Long Narration, etc.)
  - Play/Pause toggle button
  - Skip to next segment button
  - Stop narration button
  - Speed cycle button (0.75x → 0.9x → 1.0x → 1.25x, shows toast)
  - Bar auto-hides when narration finishes (NarrationState.Idle)
- [x] Respects phone ringer mode (silent/vibrate = no speech)
- [ ] MediaStyle notification controls (deferred to Phase 10 polish)

### Step 7.5: Ambient mode (no active tour)
- [x] Ambient narration in TourEngine:
  - `ambientModeEnabled` toggle (default off)
  - When no tour active + ambient on: monitors GPS passively
  - Checks proximity to ALL Salem POIs (100m radius)
  - On approach: speaks short narration hint via `speakHint()`
  - Dedup: each POI triggers only once per session (`ambientHintedPois` set)
  - `resetAmbientHints()` clears history
  - POI cache loaded at ViewModel init via `loadAmbientPois()`

### Step 7.6: Verify
- [x] `./gradlew :app-salem:assembleDebug` builds clean
- [x] `./gradlew :app:assembleDebug` builds clean
- [ ] Emulator: simulate GPS walk — geofence triggers fire at correct distances
- [ ] Emulator: short narration plays on approach
- [ ] Emulator: long narration plays on arrival
- [ ] Emulator: narration controls (pause, resume, skip, speed) work
- [ ] Emulator: ambient mode triggers for non-tour POIs
- [ ] Git commit: "Phase 7: GPS geofence triggers and TTS narration system"

---

## Phase 8 — Walking Directions

**Goal:** Turn-by-turn walking directions from user's location to any POI.

### Step 8.1: OSRM routing integration
- [x] Create `WalkingDirections.kt` (`tour/WalkingDirections.kt`):
  - Uses OSMBonusPack's `OSRMRoadManager` with `MEAN_BY_FOOT`
  - `getRoute(from, to)` — single A→B walking route
  - `getMultiStopRoute(waypoints)` — multi-stop connected route (tour preview)
  - Returns `WalkingRoute`: polyline, distance (km), duration (min), turn-by-turn instructions
  - Cache: routes cached by start+end hash for 1 hour
  - `WalkingInstruction`: text, distance, duration, location, maneuver type
  - User agent: `WickedSalemWitchCityTour/1.0`

### Step 8.2: Route display on map
- [x] Walking route drawn on osmdroid map (`SalemMainActivityDirections.kt`):
  - Bordered polyline: 10px dark gold border + 6px gold main line
  - Round caps and joins for smooth rendering
  - Turn markers at key intersections (dots at turn points, skip "continue" instructions)
  - Directions info bar: distance (km), time (min), turn count, Turns button, close button

### Step 8.3: "Get me there" feature
- [x] "Walk Here" button in tour stop detail dialog:
  - Calculates route from current GPS to selected POI
  - Shows route on map + info bar with estimated walking time
  - `walkTo(GeoPoint)` helper callable from any POI card
- [x] "Narrate" button in tour stop detail dialog:
  - Speaks long narration on demand for any stop

### Step 8.4: Tour route display
- [x] `getDirectionsToCurrentStop()` — route from GPS to active tour stop
- [x] `getFullTourRoute()` — multi-stop route preview for entire tour
- [x] `clearDirections()` — dismiss route overlay

### Step 8.5: Route optimization for custom tours
- [x] Already implemented in Phase 6.6:
  - `TourEngine.optimizeRoute()` — nearest-neighbor TSP from first POI
  - `TourEngine.optimizeRouteFromStart()` — nearest-neighbor from user GPS
  - Distance + estimated time displayed in custom tour builder
  - Reorder via `TourEngine.reorderStops()` (programmatic — drag-and-drop deferred to Phase 10)

### Step 8.6: Verify
- [x] `./gradlew :app-salem:assembleDebug` builds clean
- [x] `./gradlew :app:assembleDebug` builds clean
- [ ] Emulator: walking directions display correctly on map
- [ ] Emulator: estimated times are reasonable (~5 km/h walking)
- [ ] Emulator: "Walk Here" works from tour stop detail
- [ ] Emulator: turn-by-turn dialog shows correct instructions
- [ ] Git commit: "Phase 8: Walking directions — OSRM integration"

---

## Phase 9 — Haunted Happenings & Events Integration

**Goal:** Comprehensive coverage of Salem's events, shows, exhibits, and seasonal offerings.

### Step 9.1: Events calendar database
- [x] Populated `events_calendar` via `SalemEvents.kt` in `:salem-content` — 20 curated events:

**Haunted Happenings (October) — 7 events:**
- [x] Grand Parade (first Thursday October)
- [x] Artisan Marketplace (weekends October, Salem Common)
- [x] Costume Contest (Oct 31, Salem Common)
- [x] Pumpkin Decorating (Riley Plaza)
- [x] Thirteen Ghosts haunted house
- [x] Salem Witch Museum extended hours
- [x] Psychic Fair (daily October)

**Year-round events — 5 events:**
- [x] Salem's So Sweet (February chocolate festival)
- [x] Salem Film Fest (March documentaries)
- [x] Salem Arts Festival (June, free)
- [x] Heritage Days (August, Derby Wharf)
- [x] Salem Ferry season (May-October, Boston Harbor Cruises)

**Museum exhibits — 5 events:**
- [x] Peabody Essex Museum — permanent collection
- [x] House of Seven Gables — guided tours
- [x] Salem Witch Museum — witch trials presentation
- [x] Pioneer Village — seasonal living history
- [x] Witch Dungeon Museum — live reenactment

**Ghost tours & shows — 3 events:**
- [x] Bewitched After Dark walking tour (nightly)
- [x] Candlelight Ghost Tour (nightly Apr-Nov)
- [x] Cry Innocent — interactive witch trial (daily May-Oct)

- [x] Content pipeline updated: `SalemEvents.all()` → `ContentPipeline` → SQL output
- [x] All events linked to venue POIs where applicable

### Step 9.2: Events UI
- [x] Events dialog (`SalemMainActivityEvents.kt`):
  - "On This Date in 1692" section — cross-references timeline_events by month-day
  - "Happening Today" — active events filtered by current date
  - "Upcoming" — future events sorted by start date
  - Monthly events section
  - Category filter chips (horizontal scroll, color-coded by event type)
  - 12 event types mapped to display names + colors
- [x] Event card:
  - Name, type badge (color-coded), date range
  - Description (3-line preview), hours, admission
  - "Show venue on map" link (centers map on POI)
- [x] Events by type — filtered sub-dialog
- [x] EventsViewModel: today/upcoming/month/all events + "on this day" + type filter + October/season detection
- [x] Grid dropdown: Events button (row 4, calendar icon)
- [x] `onEventsRequested()` added to MenuEventListener (+ no-op in `:app`)

### Step 9.3: Seasonal awareness
- [x] October mode: `EventsViewModel.isOctober` flag, pumpkin emoji banner in events dialog
- [x] Tourist season detection: `isTouristSeason` (April-October)
- [x] "On this date in 1692" feature:
  - `TimelineEventDao.findByMonthDay()` — substr(date, 6, 5) match
  - Red-bordered section at top of events dialog
  - Links to related POI on map
- [ ] Halloween-themed map overlays (deferred to Phase 10)
- [ ] Daily historical notification (deferred to Phase 10)

### Step 9.4: Verify
- [x] `./gradlew :salem-content:build` builds clean
- [x] `./gradlew :app-salem:assembleDebug` builds clean
- [x] `./gradlew :app:assembleDebug` builds clean
- [ ] Emulator: events display with correct dates and venues
- [ ] Emulator: category filter chips work
- [ ] Emulator: tapping venue navigates to POI on map
- [ ] Emulator: "On this date in 1692" shows for matching dates
- [ ] Re-run content pipeline to regenerate salem_content.db with events
- [ ] Git commit: "Phase 9: Events calendar — Haunted Happenings, exhibits, seasonal content"

---

## Phase 9A — Splash Screen & Satellite Map Entry

**Goal:** Branded launch experience — animated WitchKitty splash screen transitions into USGS satellite aerial imagery with a cinematic zoom to the user's GPS position. Replace the dark mode toggle with a 3-way tile source picker.

**Target:** 1-2 sessions | **Status:** NOT STARTED | **Added:** Session 78

### Step 9A.1: Create WitchKitty Splash Screen
- [ ] Create `SplashActivity.kt` — Full-screen, Lottie animation (2.5s), "Wicked Salem" text fade-in, crossfade to SalemMainActivity
- [ ] Create `activity_splash.xml` — Centered LottieAnimationView on gradient background
- [ ] Create `splash_background.xml` — Gradient `#1A0F2E` → `#2D1B4E`
- [ ] Create/export `witchkitty_splash.json` — Lottie JSON from AI Art Studio WitchKitty assets
- [ ] Add Lottie dependency: `com.airbnb.android:lottie:6.4.0` to `app-salem/build.gradle`
- [ ] Move LAUNCHER intent-filter from SalemMainActivity to SplashActivity in AndroidManifest.xml
- [ ] Add `Theme.WickedSalem.Splash` to themes.xml (no action bar, full screen)

### Step 9A.2: USGS Satellite Tile Source
- [ ] Create `TileSourceManager.kt` — Centralized tile source manager with `TileSourceId { SATELLITE, STREET, DARK }`
- [ ] USGS URL: `https://basemap.nationalmap.gov/arcgis/rest/services/USGSImageryOnly/MapServer/tile/` (free, public domain, 15cm resolution over Salem)
- [ ] Change default tile source from MAPNIK to SATELLITE in `setupMap()`
- [ ] Delegate `buildDarkTileSource()` to `TileSourceManager.buildSource(DARK)`
- [ ] Add `PREF_TILE_SOURCE` to MenuPrefs, deprecate `PREF_DARK_MODE`

### Step 9A.3: Dramatic Zoom-In Animation
- [ ] When arriving from splash: start at zoom 4.0 (US from space), animate to 10.0 (800ms), to GPS at 15.0 (1200ms), ease to 17.0 (800ms)
- [ ] Fallback: if no GPS fix within 3s, zoom to Salem center (42.521, -70.887) at zoom 15
- [ ] Set `initialCenterDone` flag after animation to prevent GPS-center logic from fighting

### Step 9A.4: Tile Source Picker (Replaces Dark Mode Toggle)
- [ ] Create `ic_map_layers.xml` vector drawable
- [ ] Replace `toolbarDarkModeIcon` with `toolbarTileSourceIcon` in toolbar layout
- [ ] Replace dark mode click handler with PopupMenu (Satellite/Street/Dark)
- [ ] Replace `onDarkModeToggled(Boolean)` with `onTileSourceChanged(String)` in MenuEventListener

### Step 9A.5: Verify
- [ ] Launch → WitchKitty splash → crossfade → satellite map → cinematic zoom to GPS
- [ ] Layers icon shows 3 tile options, all switch correctly
- [ ] All existing features (radar, transit, POIs, tours) work on satellite tiles
- [ ] Tile preference persists across app restarts

---

## Phase 9A+ — Tour Hardening & Offline Foundation

**Goal:** Make the Salem Essentials 14-stop tour bulletproof and fully offline. This is the product foundation — every future feature builds on a proven, working tour experience. The app must run completely offline on a tablet with no internet dependency.

**Target:** 3-5 sessions | **Status:** NOT STARTED | **Added:** Session 82 | **Priority:** HIGHEST
**Test Device:** Lenovo TB305FU tablet (Android 15, ARM64, Magisk root, Frida 17.9.1, serial HNY0CY0W)

### Rationale
Phase 9B (tier gating) is premature. The tour experience IS the product. Geofence triggers, TTS narration, map overlays, and stop advancement have never been validated end-to-end on a real walking simulation. Session 81 emulator testing showed 1/14 geofences firing — the FusedLocationProvider flow is unproven. Before layering features on top, the foundation must be solid.

### Step 9A+.1: Bundle Offline Map Tiles for Salem
- [ ] Generate `.mbtiles` tile archive for downtown Salem (~1 sq mile bounding box)
- [ ] Include Esri World Imagery (satellite) tiles, zoom levels 12-19 (~60-100MB)
- [ ] Include OpenStreetMap (street) tiles, zoom levels 12-19 as fallback
- [ ] Add `MBTilesFileArchive` offline tile source to `TileSourceManager.kt`
- [ ] Add offline-first fallback: try bundled tiles first, fetch from network only if not available
- [ ] Tiles can be sideloaded to tablet storage (not APK-bundled — too large) or placed in app assets directory
- [ ] Verify: satellite map renders on tablet in airplane mode

### Step 9A+.2: Bundle Offline POI Data for Salem
- [ ] Pre-populate Room database with Overpass POI data for Salem's walkable core (~1 sq mile)
- [ ] Include restaurants, shops, museums, parks, parking, transit stops — all categories the app displays
- [ ] Add offline POI query path in PlacesRepository: check local Room DB before calling proxy
- [ ] Salem-specific POIs (37 curated + tour stops) already bundled — extend to general commercial POIs
- [ ] Verify: POI markers display on tablet in airplane mode

### Step 9A+.3: Pre-Compute Walking Route Geometry
- [ ] Fetch OSRM routes for all 13 segments of Salem Essentials tour (stop 1→2, 2→3, ... 13→14)
- [ ] Encode each route as a polyline in the tour JSON (`tour_essentials.json`)
- [ ] Add `routeGeometry` field to tour stop schema (encoded polyline string)
- [ ] Modify `WalkingDirections.kt` to check for bundled route geometry before calling OSRM
- [ ] Repeat for Explorer (20 stops) and Grand (26 stops) tours
- [ ] Verify: route polyline draws on map without internet

### Step 9A+.4: Verify Tour Stop Coordinates
- [ ] Cross-reference all 14 Salem Essentials stop coordinates against actual locations
- [ ] Stops 1, 2, 3 share latitude 42.5216 — verify this is accurate (Essex Street alignment)
- [ ] Verify geofence radii are appropriate for each stop (small memorial = 20m, large park = 80m)
- [ ] Adjust any coordinates or radii that are off
- [ ] Document verified coordinates with source (Google Maps, field measurement, etc.)

### Step 9A+.5: Build Frida GPS Walking Simulator
- [ ] Create Frida script (`frida-salem-walk.js`) with all 14 Salem Essentials waypoints plus interpolated street-following points between stops
- [ ] Hook `FusedLocationProviderClient` and `LocationManager` on the tablet to inject synthetic GPS coordinates
- [ ] Walk at realistic speed (~1.2 m/s) with GPS jitter (±2m), proper bearing, altitude, accuracy fields
- [ ] Set `isFromMockProvider(false)` — app sees "real" GPS
- [ ] Add Python control script: start, pause, speed up (5x/10x), jump to stop N, reset
- [ ] Leverage gog-agent Frida infrastructure (Frida 17.9.1, Python bridge, tablet attachment pattern at `~/Development/gog-agent/`)
- [ ] Verify: app receives continuous location updates at walking pace, geofences trigger reliably

### Step 9A+.6: End-to-End Tour Walk Test
- [ ] Deploy app to tablet, enable airplane mode
- [ ] Run Frida walking simulator through all 14 stops
- [ ] Verify each stop triggers: APPROACH → short TTS narration → ENTRY → long TTS narration → EXIT → transition narration
- [ ] Listen to all 14 TTS narrations — evaluate pacing, clarity, content accuracy
- [ ] Verify map behavior: route polyline visible, numbered markers update (gray→blue→green), HUD shows progress
- [ ] Verify tour completion: summary screen with stats, clean exit
- [ ] Document all issues found for fixing

### Step 9A+.7: Tour UX Polish
- [ ] Add continuous map follow during walking (not just on ENTRY events) — user's position stays centered
- [ ] Add "next stop" distance indicator that updates live as user walks
- [ ] Verify stop advancement is seamless: exit → transition TTS → approach next stop
- [ ] Add graceful offline indicators: suppress weather/transit/aircraft UI when offline instead of showing broken buttons
- [ ] Verify pause/resume with geofence restore works across app restart
- [ ] Verify TTS respects phone ringer mode (silent = no speech)

### Step 9A+.8: Offline TTS Verification
- [ ] Confirm Google TTS `en-US` voice data is installed on tablet (already confirmed: `com.google.android.tts` present)
- [ ] Test TTS output in airplane mode — verify no network dependency for speech synthesis
- [ ] If tablet lacks offline voice data, document how to pre-install it

### Step 9A+.9: Verify
- [ ] Salem Essentials tour runs start-to-finish on tablet in airplane mode
- [ ] All 14 geofences trigger at correct distances
- [ ] All narrations play via TTS (approach, at-stop, transition)
- [ ] Map displays satellite tiles from bundled archive
- [ ] Walking route polyline displays from pre-computed geometry
- [ ] POI markers display from bundled Room database
- [ ] No crashes, no network calls, no blank screens
- [ ] Tour pause/resume survives app restart
- [ ] Frida walking simulator is repeatable for regression testing
- [ ] Git commit: "Phase 9A+: Tour hardening — offline tiles, pre-computed routes, Frida walk simulator, UX polish"

---

## Phase 9T — Salem Walking Tour Restructure

**Goal:** Transform the tour from a linear stop-to-stop experience into an ambient content layer over downtown Salem. The city narrates itself as you walk — every historical POI, statue, street, civic building, and landmark has narration. Walking routes are suggestions, not requirements. Content triggers by proximity regardless of route.

**Target:** 5-8 sessions | **Status:** IN PROGRESS (9T.9 verification pending) | **Added:** Session 85 | **Priority:** displaced by Phase 9P (Session 96)

### Rationale
The linear tour (stop 1 → 2 → 3) forces rigid routing. Tourists don't walk that way — they wander. The restructured "Salem Walking Tour" makes the entire downtown core a narrated experience. Walk anywhere within the bounded area and the app delivers rich historical content. This is the core product differentiator vs competitors (Action Tour Guide, Salem On Your Own). The narration dialog UI (image + text + buttons) also becomes the foundation for future merchant advertising.

### Geographic Boundary
Tour content is confined to downtown Salem's walkable core:
- **Northwest:** Bridge Street
- **East:** Flint Street
- **South:** Mill Street / Harbor Street

This is approximately 0.5 square miles of the densest historical area.

### Step 9T.1: Define Geographic Boundary & Audit Existing POIs
- [ ] Define the boundary polygon as precise lat/lng coordinates (Bridge St, Flint St, Mill/Harbor St)
- [ ] Audit all 45 existing tour POIs — which fall inside the boundary?
- [ ] Audit all Overpass/OSM POIs in the bounded area — identify historical, civic, monuments, churches, cemeteries
- [ ] Identify all named streets within the boundary and their historical significance
- [ ] Produce a narration point target list: aim for 80-120+ narration points total
- [ ] Categorize by type: historical_site, street_corridor, civic_building, monument_statue, area_district, business_historical

### Step 9T.2: Narration Point Catalog Schema
- [ ] Design `NarrationPoint` data model (extends/replaces TourPoi for this use case):
  - id, name, lat, lng, type (historical_site | street_corridor | civic_building | monument_statue | area_district)
  - shortNarration (~50-100 words, approach trigger)
  - longNarration (~200-400 words, entry trigger)
  - imageAsset (optional — filename or URL for dialog image)
  - geofenceRadiusM (20-50m depending on type)
  - geofenceShape (circle for points, corridor for streets)
  - priority (1=must-see through 5=minor)
  - relatedFigureIds (link to historical_figures table)
  - relatedFactIds (link to historical_facts table)
  - relatedSourceIds (link to primary_sources table)
  - actionButtons (JSON array of optional button configs for future use)
  - dataSource, confidence, verified_date, stale_after (provenance)
- [ ] Add Room entity and DAO for NarrationPoint
- [ ] Add to salem-content pipeline (SalemNarrationPoints.kt)
- [ ] Seed initial data from existing 32 downtown tour POIs

### Step 9T.3: Narration Dialog UI
- [ ] Build `NarrationDialogFragment` — the core content display component:
  - **Top 1/3:** Image area (ImageView, optional — hide if no image, text expands)
  - **Middle:** Title, category badge, scrollable narrative text
  - **Bottom:** Action buttons row (Skip, Pause/Play, More Info) + TTS controls (speed, queue count)
- [ ] Dialog appears as a bottom sheet or overlay — map stays visible behind it
- [ ] TTS reads the narrative text simultaneously with display
- [ ] "More Info" button shows related facts, primary sources, historical figures
- [ ] Queue indicator: "3 more nearby" badge
- [ ] Dialog dismissible by swipe or tap outside
- [ ] Design for future reuse: same frame for merchant ads (image of business, description, Visit/Directions/Dismiss buttons)
- [ ] Respect quiet mode: visual only, no TTS

### Step 9T.4: Enhanced Content Queue System
- [ ] Extend NarrationManager with priority queue (not just FIFO):
  - Nearest narration point first
  - Higher priority (1) before lower (5)
  - No repeats within session (track played set)
  - Minimum gap between narrations (configurable, default 5 seconds)
  - Auto-advance: when one narration finishes, brief pause, then next
- [ ] Queue state observable: current item, queue depth, next item preview
- [ ] User controls: skip current, pause queue, clear queue, replay last
- [ ] Handle overlapping geofences: when entering multiple zones simultaneously, enqueue all by priority/distance
- [ ] De-duplication: if "Essex Street history" and "Essex Street architecture" both trigger, group or sequence them

### Step 9T.5: Street Corridor Geofences
- [ ] Add corridor geofence type: a polyline + buffer distance (e.g., 20m each side of street centerline)
- [ ] Implement proximity-to-polyline check in TourGeofenceManager (point-to-segment distance)
- [ ] Define corridors for historically significant streets within boundary:
  - Essex Street, Federal Street, Chestnut Street, Charter Street, Derby Street, Washington Street, etc.
- [ ] Each corridor has its own narration: street name origin, historical significance, notable events
- [ ] Corridor narrations fire once per walk (not every time you cross the street)

### Step 9T.6: Create Narration Content (80-120+ Points)
- [ ] Write narrations for all existing downtown POIs that lack them
- [ ] Write narrations for civic/government buildings: City Hall, courthouses, fire stations, police station, post office
- [ ] Write narrations for churches and religious buildings within boundary
- [ ] Write narrations for monuments and statues: Roger Conant, Hawthorne, Bewitched, Witch Trials Memorial
- [ ] Write narrations for street corridors (8-12 major streets)
- [ ] Write narrations for area/district entries: McIntire District, Chestnut Street row houses, Derby Wharf
- [ ] Link narration points to related historical figures (49 available), facts (500), and primary sources (200)
- [ ] Auto-generate draft narrations from Salem project data where possible, then curate
- [ ] All narrations TTS-optimized: short sentences, natural speech, no academic formatting

### Step 9T.7: Suggested Walking Loops
- [ ] Design 2-3 suggested walking loops within the boundary:
  - **Quick Loop** (~30 min, ~1.5km): Essex St pedestrian mall core, Witch House, Charter St Cemetery, waterfront return
  - **Standard Loop** (~60 min, ~3km): adds McIntire District, Chestnut St, Common, Federal St
  - **Grand Loop** (~90+ min, ~5km): covers the full bounded area comprehensively
- [ ] Compute OSRM routes for each loop
- [ ] Loops are suggestions only — content triggers regardless of route taken
- [ ] Each loop shows estimated narration points along the way ("pass 25+ narrated locations")
- [ ] Store as tour JSON files (reuse existing format with routeToNext geometry)

### Step 9T.8: Integration & Testing
- [ ] Wire NarrationDialogFragment to geofence events (replaces current toast + TTS-only flow)
- [ ] Test with walk simulator through the full bounded area
- [ ] Verify content queue handles dense areas (Essex St = 5+ points within 100m)
- [ ] Verify corridor geofences fire correctly along streets
- [ ] Verify no narration repeats within a walk session
- [ ] Verify auto-advance pacing feels natural (not overwhelming)
- [ ] Verify quiet mode (visual dialog only, no TTS)
- [ ] Test in airplane mode — all content must work offline
- [ ] Performance test: 120+ geofences checked on every GPS update must stay under 16ms

### Step 9T.9: Verify
- [ ] Walk simulator covers entire bounded area — all narration points fire
- [ ] Content queue handles overlapping zones gracefully
- [ ] Narration dialog displays correctly (image + text + buttons)
- [ ] Street corridor narrations fire when walking along streets
- [ ] Suggested loops render on map with route polylines
- [ ] No GPS drift false triggers (hysteresis: 3s inside before trigger, 25m minimum radius)
- [ ] User fatigue controls work: quiet mode, pace control, skip/pause/clear
- [ ] All content plays offline (TTS, images from bundled assets, text from Room DB)
- [ ] Git commit: "Phase 9T: Salem Walking Tour restructure — ambient content layer, narration dialog, content queue, 100+ narration points"

---

## Phase 9P — POI Admin Tool (Developer Infrastructure)

**Goal:** A web-based admin tool that runs on the operator's development machine for managing all Salem POIs — curated tour stops, businesses, and narration points. Tree on the left (category → subcategory → POI), Leaflet map in the center showing all POIs, click-to-edit dialog with tabbed attribute groups, drag-to-reposition. Foundation for fixing position errors, deduplicating overlapping icons, and (eventually) managing merchant-paid advertisements.

**Target:** 4-5 sessions | **Status:** PLANNING COMPLETE (Session 96) | **Added:** Session 96 | **Priority:** HIGHEST

### Rationale
Two real pain points motivate this work: (a) POI position errors visible on the map, (b) overlapping icons in dense areas, both inherited from the Overpass scrape feeding the 814 narration points. Editing POI data via SQL or by re-running Python pipeline scripts is unsustainable as the dataset grows. A proper admin tool also lays the groundwork for the merchant advertising business model (Phase 17) — when paying merchants need their listings updated, the tool already exists. Scoped narrowly for v1: operator-only, localnet, no merchant features, no audit log, no photo uploads, no live OTA sync.

### Architectural Decisions (Session 96 dialog)
1. **Data foundation:** Migrate `narration_points` from bundled SQL into a new PostgreSQL `salem_narration_points` table. PostgreSQL becomes the single source of truth for all editable Salem POIs going forward. JSON files in `tools/salem-data/` retire to historical artifact status.
2. **Auth:** HTTP Basic Auth via env vars (`ADMIN_USER` / `ADMIN_PASS`). Browser handles native prompt. Localnet single-admin model (operator + wife).
3. **Categories:** Hand-port `PoiCategories.kt` (520 lines, 22 categories, 153+ subtypes) into TypeScript config in `web/src/config/poiCategories.ts`. Cross-reference comments in both files. Unification deferred.
4. **Sync:** No live OTA. Edit → "Publish" rebuilds `salem-content/salem_content.sql` from PostgreSQL → next APK build ships fresh data.

### Stack additions (3 new dependencies)
- `react-arborist` — virtualized tree for ~860 POIs (installed S103 v3.4.3)
- `react-hook-form` — POI edit form state management (Step 9P.10)
- `@headlessui/react` — Tabs component for the edit dialog (Step 9P.10)
- `leaflet.markercluster` — installed S104 (v1.5.3) for the admin map cluster layer; was NOT already in the web app despite the original guess

---

### Phase 9P.A — Migration & Backend Foundation

#### Step 9P.1: Add `salem_narration_points` table to PostgreSQL
- [ ] Add `CREATE TABLE salem_narration_points` to `cache-proxy/salem-schema.sql`
- [ ] Schema mirrors `salem_businesses` provenance pattern + narration-specific fields:
  - `id TEXT PRIMARY KEY`, `name`, `lat`, `lng`, `address`, `category`, `subcategory`
  - `short_narration`, `long_narration`, `pass1_narration`, `pass2_narration`, `pass3_narration` (multipass)
  - `geofence_radius_m INTEGER DEFAULT 40`, `priority INTEGER`, `wave INTEGER`
  - `tags JSONB`, `image_asset TEXT`
  - Provenance: `data_source`, `confidence`, `verified_date`, `created_at`, `updated_at`, `stale_after`, `deleted_at` (soft delete)
- [ ] Indexes: `(category)`, `(lat, lng)`, `(data_source)`, `(deleted_at) WHERE deleted_at IS NULL`
- [ ] Run schema migration against local PostgreSQL: `psql -U postgres -d locationmapapp -f cache-proxy/salem-schema.sql`
- [ ] Verify table exists and is empty

#### Step 9P.2: Write narration points importer (one-shot)
- [ ] Create `cache-proxy/scripts/import-narration-points.js`
- [ ] Read `tools/salem-data/narration-priority-pois.json` (or `merged-salem-pois.json` if richer)
- [ ] For each POI: derive ID via the same slug rule used in `generate_narration_sql.py`, set `data_source` based on origin (`destination_salem` / `haunted_happenings` / `openstreetmap`), populate provenance fields with `confidence` matching original source quality
- [ ] If multipass narrations exist in `salem-content/salem_content.sql`, parse them out and populate the multipass columns (otherwise leave NULL — they get backfilled later)
- [ ] INSERT into `salem_narration_points` in batches of 100, transactional
- [ ] Verify final row count matches the bundled SQL count (~814)
- [ ] Spot-check 10 random rows against the source JSON for fidelity

#### Step 9P.3: HTTP Basic Auth middleware — DONE (Session 99, 2026-04-08)
- [x] Created `cache-proxy/lib/admin-auth.js` exporting `requireBasicAuth(req, res, next)` — constant-time comparison via `crypto.timingSafeEqual` with length-padding
- [x] Reads `ADMIN_USER` and `ADMIN_PASS` from env at request time, returns 401 + `WWW-Authenticate: Basic realm="LocationMapApp Admin"` on failure, 503 if env vars unset
- [x] Added `ADMIN_USER` and `ADMIN_PASS` to `cache-proxy/.env.example` with placeholders. Also created `cache-proxy/.env` (gitignored) — see OMEN-002 cleanup notes below
- [x] Mounted as `app.use('/admin', requireBasicAuth)` global middleware in `cache-proxy/server.js`
- [x] **Locked down `POST /cache/clear`** with per-route `requireBasicAuth` (the latent S96 bug)
- [x] Smoke tests: 7/7 pass (no-auth → 401, wrong creds → 401, correct creds → 200, on both `/admin/ping` and `/cache/clear`; `/cache/stats` left public as read-only telemetry)

**Bonus (OMEN-002 file-side cleanup, done same step):** moved `DATABASE_URL`, `OPENSKY_CLIENT_ID`, `OPENSKY_CLIENT_SECRET` out of `bin/restart-proxy.sh` into the new gitignored `cache-proxy/.env`. Script now sources `.env` via `set -a; source ...; set +a`. Both old credential strings confirmed absent from the tracked file. Credentials still exist in git history — rotation flagged for operator action.

#### Step 9P.4: Admin POI write endpoints — DONE (Session 99, 2026-04-08)
- [x] Created `cache-proxy/lib/admin-pois.js` (~290 lines, per-kind config + whitelisted partial updates)
- [x] Endpoints (all under `/admin/salem/pois/*`, gated by /admin Basic Auth):
  - [x] `GET /admin/salem/pois?kind=tour|business|narration&category=&s=&w=&n=&e=&q=&include_deleted=&limit=` — list with filters (bbox uses s/w/n/e per existing convention)
  - [x] `GET /admin/salem/pois/:kind/:id` — single POI (admin sees soft-deleted rows for tombstone inspection)
  - [x] `PUT /admin/salem/pois/:kind/:id` — partial update via whitelisted field sets (refuses if soft-deleted; must restore first)
  - [x] `POST /admin/salem/pois/:kind/:id/move` — lat/lng-only, returns `{from: {lat,lng}, to: {lat,lng}}` (refuses if soft-deleted)
  - [x] `DELETE /admin/salem/pois/:kind/:id` — soft delete (409 if already deleted)
  - [x] `POST /admin/salem/pois/:kind/:id/restore` — undo soft delete (409 if not deleted)
- [x] All write endpoints set `updated_at = NOW()` automatically
- [x] Validation: `lat ∈ [-90,90]`, `lng ∈ [-180,180]`, JSONB fields stringified+cast, category cannot be cleared to empty. Full taxonomy validation deferred to Phase 9P.6 (TypeScript port of `PoiCategories.kt`)
- [x] Smoke tests: 24/24 pass (all six endpoints, including 4xx paths and a full delete→restore round-trip)

**Bonus (in-scope schema migration):** added `deleted_at TIMESTAMPTZ` + `idx_..._active` partial indexes to `salem_tour_pois` and `salem_businesses` (only `salem_narration_points` had `deleted_at` going in). Updated `cache-proxy/salem-schema.sql` so the migration is reproducible. Updated `lib/salem.js` public reads (`/salem/pois`, `/salem/pois/:id`, `/salem/businesses`) to filter `deleted_at IS NULL` so soft delete is functional end-to-end (otherwise it would be theatrical — admin "deletes" a POI but the app still shows it).

#### Step 9P.4a: Per-mode category visibility schema — DONE (Session 100, 2026-04-08)
- [x] Added `historicTourDefault: Boolean = false` field to the `PoiCategory` data class in `app-salem/.../ui/menu/PoiCategories.kt`
- [x] Two-prefKey naming convention documented as helper getters on `PoiCategory`:
  - `freeRoamPrefKey: String get() = prefKey` (alias for clarity — same as the existing `prefKey`)
  - `tourPrefKey: String get() = "${prefKey}_tour"` (appends `_tour` suffix)
- [x] Free-roam defaults preserved unchanged (still drive off `defaultEnabled` exactly as before — zero behavior change for the current app)
- [x] Historic tour defaults populated for all 22 categories:
  - **ON in tour mode (6):** CIVIC, WORSHIP, TOURISM_HISTORY, WITCH_SHOP, GHOST_TOUR, HISTORIC_HOUSE
  - **OFF in tour mode (16):** everything else (food, lodging, shopping, services, modern entertainment, parks, healthcare, education, finance, parking, transit, fuel, emergency, auto services, offices, psychic, haunted attractions)
  - **Notable split:** WORSHIP is `defaultEnabled=false` (clutter in free-roam) but `historicTourDefault=true` (1692 churches like Salem Village Church and First Church of Salem are central to the witch trials narrative). PSYCHIC and HAUNTED_ATTRACTION go the opposite way: `defaultEnabled=true` (modern Salem flavor) but `historicTourDefault=false` (distract from 1692 narrative).
- [x] TypeScript config (`web/src/config/poiCategories.ts`) **deferred to Phase 9P.6** as the master plan specifies. 9P.4a is the Kotlin schema; 9P.6 hand-ports it into TypeScript with the same shape.
- [x] Compiled clean (`./gradlew :app-salem:compileDebugKotlin` BUILD SUCCESSFUL)

**Migration plan (Phase 9R rollout — to be implemented when historic tour mode ships):**
- On first launch in a build that includes historic tour mode, run a one-shot SharedPreferences migration:
  - For each `PoiCategory` in `PoiCategories.ALL`: read the existing `<prefKey>` value (defaulting to `defaultEnabled` if missing); write that same value to `<prefKey>_tour` only if `<prefKey>_tour` doesn't already exist; initialize `<prefKey>_tour` from `historicTourDefault` if neither exists. Important: the migration must only RUN ONCE — guarded by a `prefs_migrated_to_per_mode_v1` boolean.
  - The existing `<prefKey>` keys remain in use as the free-roam values; the new `<prefKey>_tour` keys are read only when historic tour mode is active.
  - Active mode is selected by a top-level `current_mode_pref` (`freeroam` | `tour`) that historic tour mode UI sets when the user opts in.
  - All POI-visibility-reading code paths (currently `enabledTagValues(prefs)` and similar) become mode-aware: `prefs.getBoolean(if (mode == TOUR) cat.tourPrefKey else cat.freeRoamPrefKey, if (mode == TOUR) cat.historicTourDefault else cat.defaultEnabled)`.
- This migration is a Phase 9R deliverable, NOT a 9P.4a deliverable. 9P.4a only ships the data shape so 9R has somewhere to land.

#### Step 9P.5: Duplicates detection endpoint — DONE (Session 100, 2026-04-08)
- [x] `GET /admin/salem/pois/duplicates?radius=15` added to `cache-proxy/lib/admin-pois.js`
- [x] Implementation: bbox-prefiltered self-join across all three POI tables (`salem_tour_pois`, `salem_businesses`, `salem_narration_points`) via a `WITH all_pois AS (...UNION ALL...)` CTE; Haversine distance in pure SQL (no PostGIS dependency); JS-side cluster grouping via union-find with path compression
- [x] Response shape: `{ radius_m, count, clusters: [{ centroid: {lat, lng}, member_count, members: [{ kind, id, name, lat, lng, distance_m_from_centroid }] }] }` — clusters sorted by member_count desc; members within each cluster sorted by distance from centroid asc
- [x] Configurable radius: default 15m, max 100m (silently capped above 100), `radius=0` and `radius=banana` both rejected with 400
- [x] Excludes soft-deleted rows (`WHERE deleted_at IS NULL` in each leg of the CTE)
- [x] Bbox prefilter (`ABS(lat-lat) <= radius/111000` AND `ABS(lng-lng) <= radius/60000`) cuts the join before Haversine, keeping latency well under 1s on the ~1700-row Salem dataset
- [x] Smoke tested against the live dataset: 7/7 pass — default radius=15m surfaces 101 clusters; the top cluster has 27 narration POIs chained together near downtown Essex Street (a known dense block where Destination Salem and OSM imports overlapped); a separate 3-POI cluster at coordinates (42.5235097, -70.8952337) shows three POIs ("Bluebikes", "St. Peter's Church", "Downtown Salem") sharing the exact same location — likely an import default that the operator should fix in the admin UI

**Phase 9P.A — Backend Foundation — COMPLETE** with 9P.5. The six foundation steps (9P.1 schema, 9P.2 importer, 9P.3 auth middleware, 9P.4 admin POI write endpoints, 9P.4a per-mode visibility schema, 9P.5 duplicates) are all done. **Phase 9P.B — Admin UI** (in `web/`) starts next session.

#### Step 9P.A.3: Migrate `tour_pois` and `salem_businesses` to PostgreSQL — DONE (Session 101, 2026-04-08)

**Why:** After Phase 9P.4 the admin write endpoints existed for all three POI kinds, but the PG copies of `salem_tour_pois` and `salem_businesses` were empty (only `narration_points` was migrated in S98). That meant any admin PUT/DELETE/move on a tour or business POI returned 404. The admin tool was functionally non-functional for two-thirds of the POIs. This step mirrors the S98 narration migration for the other two tables, completing the architectural shift the user articulated as: "update in admin tool has to update both the database and where-ever they are sourced."

**Implementation:** New `cache-proxy/scripts/import-tour-pois-and-businesses.js` (~270 lines). Uses `better-sqlite3` to read from `salem-content/salem_content.db` directly (cleaner than parsing the bundled SQL file the way S98 did) and `pg` to UPSERT into PostgreSQL.

- [x] Source: `salem-content/salem_content.db` (the bundled SQLite — verified working with the Android app, most authoritative current snapshot)
- [x] Type conversions: JSON TEXT → JSONB (`subcategories`, `tags`); INTEGER 0/1 → BOOLEAN (`requires_transportation`, `wheelchair_accessible`, `seasonal`); INTEGER Unix-millis → TIMESTAMPTZ (with `0` → NULL for `stale_after`); TEXT YYYY-MM-DD → TIMESTAMPTZ (PG handles directly)
- [x] **UPSERT, not TRUNCATE+INSERT.** Six tables reference `salem_tour_pois` via FK (`salem_historical_figures.primary_poi_id`, `salem_historical_facts.poi_id`, `salem_timeline_events.poi_id`, `salem_primary_sources.poi_id`, `salem_tour_stops.poi_id`, `salem_events_calendar.venue_poi_id`) — even though those references are currently NULL, PG refuses to TRUNCATE a referenced table without CASCADE. UPSERT (`ON CONFLICT (id) DO UPDATE SET ...`) avoids the issue and is fully idempotent.
- [x] Imported: **45 tour POIs, 861 salem_businesses** (matching source counts exactly)
- [x] PG totals after migration: tour=45, business=861, narration=814 → **1,720 active POIs**, all canonical in PG
- [x] Sample row spot-check: `witch_trials_memorial` (tour) and `hex` (business) both correct, JSONB arrays parsed cleanly
- [x] Smoke tests after restart: list (45 / 861), GET single (both), PUT update (both — were previously 404), DELETE/restore round-trip (both), duplicates endpoint now sees all 1,720 POIs and reports 550 clusters with top cluster of 54 members (up from 101 / 27 when only narration_points was in PG — confirms cross-kind duplicate detection now works as designed)

**The bundled `salem-content/salem_content.db` is now a downstream artifact** — like the bundled SQL was for narration_points after S98. It will need to be regenerated from PG when Phase 9P.C (publish loop) lands, so APK builds pick up admin edits. Until 9P.C ships, edits via the admin tool will be PG-only and will not propagate to the next APK build. Flagged as a known constraint for the operator.

**Files:**
- `cache-proxy/scripts/import-tour-pois-and-businesses.js` — NEW
- (no schema changes — `salem_tour_pois` and `salem_businesses` PG tables already existed from earlier sessions; `deleted_at` column was added in S99)

---

### Phase 9P.B — Admin UI

#### Step 9P.6: Hand-port category taxonomy to TypeScript — DONE (Session 101, 2026-04-08)
- [x] Created `web/src/config/poiCategories.ts` (~535 lines)
- [x] Ported all 22 categories + 175+ subtypes from `PoiCategories.kt`
- [x] Same shape preserved: id, label, prefKey, tags (string[]), subtypes (PoiSubtype[] | null), color (hex), defaultEnabled, **historicTourDefault** (from 9P.4a)
- [x] Per-mode helper functions: `freeRoamPrefKey(cat)`, `tourPrefKey(cat)`, plus `enabledTagValues(visibility, mode)` mirroring the Kotlin `enabledTagValues(prefs)`
- [x] `PoiLayerId` constant exported (the 22 ID strings)
- [x] `findPoiCategory(id)` lookup helper
- [x] Cross-reference comment added to BOTH files: `PoiCategories.kt` notes the mirror at `web/src/config/poiCategories.ts` and `poiCategories.ts` notes the source at `app-salem/.../ui/menu/PoiCategories.kt`. Both files include a TODO for unifying the two sources via a code generator or shared JSON config in Phase 9C or later.
- [x] **Distinct from existing `web/src/config/categories.ts`** which is the 17-category taxonomy used by the GENERIC public web app for OSM POI classification (different shape: `tagMatches: { key, values }[]`). The two files coexist; the existing `categories.ts` is untouched so the public web app behavior is unchanged. The new `poiCategories.ts` is the source of truth for the Salem admin tool's category model.
- [x] `npx tsc --noEmit` in `web/` passes clean (no type errors across the project)

#### Step 9P.7: Admin route + Basic Auth gating in web app — DONE (Session 102, 2026-04-08)
- [x] `/admin` route added via path-based dispatch in `web/src/main.tsx` (chose this over `react-router-dom` to avoid a routing dep — the public web app has no other routing needs and the admin tool is a single screen). Vite's dev server falls back to `index.html` for unknown paths so `/admin` works in `vite dev` and `vite preview`. Production hosting must mirror that fallback if `/admin` is ever exposed beyond dev.
- [x] Basic Auth gating: no in-page login form. The first admin API call from the page (the tree fetch in 9P.8) will trigger the browser's native Basic Auth dialog because the cache-proxy admin endpoints (Phase 9P.3 middleware) return 401 with `WWW-Authenticate: Basic`. Subsequent requests reuse the browser's cached credentials automatically (same-origin via the vite `/api` proxy).
- [x] `web/src/admin/AdminLayout.tsx` created (~140 lines). Three-pane layout: header bar (slate-800), left tree pane (white, 320px wide), center map pane (slate-200). Tree and map are placeholder content with TODO comments pointing at the steps that fill them in (9P.8, 9P.9, 9P.11, 9P.13). The header diagram is reproduced as a comment block at the top of the file.
- [x] Header buttons: **[Highlight Duplicates]** (slate, stub for 9P.11), **[Publish]** (emerald, stub for 9P.13), **Oracle: —** pill (right-side, stub for 9P.10b — `title` attribute documents that the pill goes live in 9P.10b), **[Logout]** (slate).
- [x] Logout uses the **XMLHttpRequest 401 trick**: synchronous XHR to `/api/admin/salem/pois` with deliberately wrong `logout:wrongpassword` credentials in the open() call. The browser caches the new (wrong) credentials for the origin, replacing the good ones; the next admin API call therefore re-prompts. After the XHR, `window.location.reload()` forces a fresh render so the prompt appears immediately. Wrapped in try/catch because some browsers throw on deprecated sync XHR. Documented inline.
- [x] `npx tsc --noEmit` in `web/` passes clean

#### Step 9P.8: POI tree (react-arborist) — DONE (Session 103, 2026-04-08)
- [x] `npm install react-arborist` in `web/` — landed at v3.4.3 (16 packages added). Pre-existing audit warnings on `vite`/`picomatch`/`socket.io-parser` are unrelated to react-arborist; deferred to a separate cleanup pass.
- [x] Create `web/src/admin/PoiTree.tsx` — ~330 lines, fully commented including data-shape recon, auth-flow rationale, and the 3-vs-4-level tree decision below.
- [x] Fetch all POIs via `/api/admin/salem/pois?kind=...&include_deleted=true&limit=2000`. The endpoint requires a `kind` query param so the tree fires **three** GETs: `tour` (45 rows) sequentially first, then `business` (861) and `narration` (814) in parallel. The sequential first call ensures the browser's Basic Auth dialog fires exactly once; subsequent fetches reuse cached credentials. Total: 1,720 active POIs (matches PG canonical count).
- [x] **Three-level grouping (kind → category → POI rows), not four.** The original spec said "category → subcategory → POI rows" but DB recon at the start of S103 showed: tour uses 7 distinct `category` values, business uses 19 distinct `business_type` values, narration uses 29 distinct `category` values — **and `salem_narration_points.subcategory` is NULL for all 814 rows.** `salem_tour_pois.subcategories` is a JSONB list of refinements (not a single column suitable for grouping). The fourth level isn't physically present in the data, so the tree collapses to three. When narration's subcategory column gets backfilled (or tour's JSONB list gets promoted), add the level back into `buildTree()` without restructuring the file.
- [x] Counts per kind and per category shown in the tree label (right-aligned, tabular-nums); also a totals strip in the toolbar (`1,720 POIs (tour 45 · biz 861 · narration 814)`) that reflects the show-deleted toggle.
- [x] Click POI row → emits `onSelect({kind, poi})` event. AdminLayout currently logs the selection to console; map pan/zoom (9P.9) and edit dialog (9P.10) will consume the event in subsequent steps.
- [x] Search bar at the top of the tree filters by name (client-side). Implemented via react-arborist's `searchTerm` + `searchMatch` props — predicate matches POI nodes by case-insensitive substring; library handles ancestor visibility automatically (matching POIs keep their kind/category nodes open).
- [x] Soft-deleted POIs hidden by default with a "Show soft-deleted POIs" checkbox. The `include_deleted=true` URL param fetches everything once at mount; the toggle then rebuilds the tree from the cached rows so flipping the checkbox is instant and never re-prompts for auth. Deleted rows render greyed-out italic with a "(deleted)" suffix.
- [x] Wired into `AdminLayout.tsx` left pane, replacing the placeholder. AdminLayout exposes `handlePoiSelect` callback (currently `console.log`).
- [x] Container sizing: react-arborist needs explicit pixel `width`/`height`. Added a small `useElementSize` hook backed by `ResizeObserver` so the tree fills the left pane and reflows on browser resize.
- [x] `cd web && npx tsc --noEmit` clean (zero output, exit 0).

#### Step 9P.9: Admin map (Leaflet + clustering + draggable markers) — DONE (Session 104)
- [x] Create `web/src/admin/AdminMap.tsx`
- [x] Reuse existing Leaflet setup from `web/src/components/Map/MapView.tsx` where possible (same react-leaflet 5.0 + leaflet 1.9 stack, same `MapContainer` + `TileLayer` pattern, same global `index.css` import for leaflet CSS)
- [x] `leaflet.markercluster` was NOT yet installed — `npm install leaflet.markercluster @types/leaflet.markercluster` added 2 packages, no new vulnerabilities introduced (3 pre-existing high-severity vulns from S103 audit are unchanged). MarkerCluster CSS imported in `web/src/index.css`.
- [x] Render all ~1,720 active POIs as clustered markers — **color by KIND not category** (tour=red `#dc2626`, business=blue `#2563eb`, narration=green `#059669`). The 22-category taxonomy applies primarily to the tree's grouping; on the map the operator needs to see "what kind of POI is this" at a glance, and there are too many categories to color-distinguish meaningfully. Per-category coloring can be revisited in 9P.11 (highlight duplicates) if needed.
- [x] Click marker → fires `onPoiSelect({kind, poi})` which lifts to AdminLayout and will open the edit dialog in 9P.10
- [x] Marker drag enabled — every marker is constructed with `draggable: true, autoPan: true`
- [x] On `dragend` → confirm modal overlay (not a Leaflet popup; renders over the map at z-index 500) showing kind, name, id, from coords, to coords, Haversine distance, [Cancel] [Confirm move]
- [x] Save → POST `/api/admin/salem/pois/:kind/:id/move` with `{lat, lng}`. On success: AdminLayout's `onPoiMoved` callback patches the row in shared `byKind` (so PoiTree's `externalByKind` rerender stays consistent), marker stays at new position. On failure or Cancel: `marker.setLatLng(origin)` reverts.
- [x] Tree selection → map pans/zooms to selected POI via `<FlyToSelected>` child component using `useMap().flyTo([lat,lng], max(currentZoom,17), {duration: 0.6})`. Selected marker gets a gold ring (`stroke: #facc15`, larger size) — recolored in a separate effect that touches just the previously- and currently-selected marker icons rather than rebuilding the cluster.
- [x] **Architectural note:** chose imperative `L.markerClusterGroup` driven via `useMap()` rather than the `react-leaflet-markercluster` wrapper. The wrapper is built for react-leaflet v4 and hasn't caught up to v5 (which we use). 30 lines of imperative code vs. another dependency on a wrapper that lags upstream. Rebuilds happen only when `byKind` changes; selection changes recolor in place.
- [x] **Data sharing:** PoiTree gained an `onDataLoaded` callback + `externalByKind` prop. AdminLayout hoists the dataset on first load and feeds both panes from one shared snapshot — single fetch, single Basic Auth prompt, drag-to-move updates flow back through the same state.
- [x] **Soft-deleted handling:** soft-deleted rows are filtered from the map (the map is the operator's spatial workspace and tombstones add clutter); PoiTree still shows them behind its existing toggle.
- [x] `disableClusteringAtZoom: 18` so the operator can drag individual POIs at deepest zoom; `maxClusterRadius: 50`; `spiderfyOnMaxZoom: true` for overlapping POIs at lower zooms.
- [x] Top-right legend overlay shows kind colors + visible counts.
- [x] `npx tsc --noEmit` clean.

**Net new files:** `web/src/admin/AdminMap.tsx` (~370 lines).
**Modified:** `web/src/admin/AdminLayout.tsx` (lifted `byKind` + `selectedPoi` state, wired AdminMap into center pane), `web/src/admin/PoiTree.tsx` (added `onDataLoaded` + `externalByKind` props), `web/src/index.css` (MarkerCluster CSS imports + `.admin-poi-marker` reset).

#### Step 9P.10: Edit dialog with tabbed attribute groups — DONE (Session 105)
- [x] `npm install react-hook-form @headlessui/react` in `web/`
- [x] Create `web/src/admin/PoiEditDialog.tsx` (~1200 lines)
- [x] Created `web/src/admin/poiAdminFields.ts` — TS mirror of `cache-proxy/lib/admin-pois.js` whitelists (TOUR_FIELDS / BUSINESS_FIELDS / NARRATION_FIELDS, JSONB_FIELDS, BOOLEAN_FIELDS, NUMERIC_FIELDS, DATE_FIELDS)
- [x] Headless UI v2 `<TabGroup>` across the top: **General** · **Location** · **Hours & Contact** · **Narration** · **Provenance** · **Linked Historical** (9P.10a stub) · **Danger Zone**
- [x] **General tab:** name, address, category (free-text + `<datalist>` of observed values), business_type (business), subcategory (narration), cuisine_type (business), historical_period (tour), description, historical_note (business), image_asset, admission_info (tour), price_range (business), rating (business), boolean toggles (tour: requires_transportation, wheelchair_accessible, seasonal), JSONB editors for `subcategories` (tour) and `tags` (business/narration)
- [x] **Location tab:** lat / lng (number inputs), geofence_radius_m, geofence_shape (narration), corridor_points (narration), priority, wave (narration). Includes a "drag the marker on the map for fine adjustments" hint.
- [x] **Hours & Contact tab:** hours, phone, website
- [x] **Narration tab:** short_narration, long_narration, pass1/2/3 (narration only — labelled "basic / historical deep-dive / primary sources"), voice_clip_asset (narration), custom_description / custom_icon_asset / custom_voice_asset (narration merchant overrides), JSONB editors for related_figure_ids / related_fact_ids / related_source_ids / action_buttons / source_categories (narration), source_id (narration), merchant_tier / ad_priority (narration). **9P.10b placeholder comment** marks where the Salem Oracle "Generate" button will land.
- [x] **Provenance tab:** data_source, confidence (number 0-1), verified_date (date picker), stale_after (date picker), read-only created_at / updated_at footer
- [x] **Linked Historical tab (9P.10a stub):** placeholder card "No links yet — see Phase 9Q (Salem Domain Content Bridge)" plus a note that editing happens in Phase 9Q.6
- [x] **Danger Zone tab:** soft delete button → inline confirm prompt → DELETE `/admin/salem/pois/:kind/:id`. Disabled when row is already soft-deleted. Surfaces server errors inline.
- [x] Save → PUT `/admin/salem/pois/:kind/:id` with **dirty-fields-only payload** via `react-hook-form`'s `formState.dirtyFields`. JSONB textareas are JSON.parsed at submit time; parse failure shows inline error and aborts save. Empty JSONB textarea → `[]`. Empty string → `null`. Numeric fields are coerced; non-numeric strings abort save with field-name error.
- [x] On save success: `onSaved(kind, updated)` patches the matching row in AdminLayout's shared `byKind` snapshot (mirrors `handlePoiMoved` pattern). Tree and map both reflect the new field values immediately, no re-fetch.
- [x] On save failure (network or 4xx): error stays in the dialog, form state preserved, operator can fix and retry. 409 (soft-deleted row) is handled — the dialog shows an amber banner if the row arrives already deleted.
- [x] Cancel → close dialog. If form has unsaved changes (`isDirty`), `window.confirm("Discard and close?")` first.
- [x] Footer save bar shows dirty-field count ("3 field(s) modified") or "No changes". Save button is disabled when not dirty.
- [x] **Open trigger:** marker click in `AdminMap` (per master plan). Tree click only selects + flies to — does NOT open the dialog. Implemented via two callbacks in AdminLayout: `handleTreeSelect` (select-only) and `handleMapSelect` (select + flip `editOpen=true`).
- [x] **Soft-delete patches the snapshot:** `onDeleted(kind, id, deletedAt)` callback marks the row deleted in `byKind`; the tree ghosts it (with show-deleted toggle off, it disappears) and the map drops it.
- [x] `npx tsc --noEmit` clean. `npm run build` succeeds (790KB bundle, no new warnings beyond pre-existing chunk-size note).

**Net new files:** `web/src/admin/PoiEditDialog.tsx` (~1200 lines), `web/src/admin/poiAdminFields.ts` (~75 lines).
**Modified:** `web/src/admin/AdminLayout.tsx` (split `handlePoiSelect` into `handleTreeSelect` / `handleMapSelect`; added `editOpen` state, `handlePoiSaved`, `handlePoiDeleted`, `handleEditClose`, `knownCategories` memo; rendered `<PoiEditDialog>` at the root of the layout).

#### Step 9P.10a: "Linked Historical Content" tab in edit dialog (added Session 97)
- [ ] Add a read-only **Linked Historical Content** tab to the edit dialog (between Narration and Provenance)
- [ ] Shows historical content tied to this POI via the building→POI bridge built in Phase 9Q:
  - Historical figures whose `primary_poi_id` resolves to this POI
  - Historical facts whose `poi_id` resolves to this POI
  - Timeline events whose `poi_id` resolves to this POI
  - Primary sources whose `poi_id` resolves to this POI
  - Newspaper articles whose `events_referenced` contains an event linked to this POI (transitive 2-hop join)
- [ ] **Until Phase 9Q is complete, this tab shows:** "No links yet — see Phase 9Q (Salem Domain Content Bridge)"
- [ ] **Editing the linkages happens in Phase 9Q.6** (Building Map admin panel), NOT in this tab — this tab is read-only forever
- [ ] Add a count badge to the tab label: "Linked Historical Content (5)" so the operator can see at a glance which POIs have historical depth

#### Step 9P.10b: Salem Oracle "Generate with AI" integration — DONE Session 106 (2026-04-09)

The Salem sibling project exposes a dev-side LLM-backed API ("the Oracle") that the admin tool can call to compose, revise, summarize, or expand POI descriptions, narrations, and historical context. This is the editorial assist surface for the description layers of the admin tool.

**Reference doc:** `~/Development/Salem/docs/oracle-api.md` (the canonical contract — ALWAYS consult this before changing the integration; cross-project, owned by Salem)

**Where the Oracle lives:**
- Salem dev workstation, port 8088 (override `SALEM_PORT`)
- Started via `~/Development/Salem/scripts/start-testapp.sh`
- Permissive CORS — admin tool can call directly from the browser
- No auth, dev-only, single shared LLM (gemma3:27b on operator's RTX 3090)
- Latency: 5-15s per `ask` call, sub-ms for catalog endpoints

**Endpoints we'll use:**
- `GET /api/oracle/status` — health check at admin tool startup; show "Oracle: ready / unavailable" indicator
- `POST /api/oracle/ask` — main composition endpoint with context pinning (`current_poi_id`, `current_newspaper_date`, `reset`)
- `GET /api/oracle/pois` — Salem's 63 catalog POIs (29 buildings + 34 landmarks); for the linked-historical-content browser
- `GET /api/oracle/poi?id=...` — fetch a Salem POI's full record (linked NPCs, events, newspaper articles)
- `GET /api/oracle/newspapers` + `GET /api/oracle/newspaper?date=...` — 202 daily newspaper articles ("The Salem Oracle")

**Important conceptual point:** Salem's POI catalog (63 historical 1692-era POIs, IDs like `salem_poi_landmark_hathorne_mansion`) is **DISTINCT** from LocationMapApp's POI tables (1,720 modern Salem POIs across `salem_tour_pois`, `salem_businesses`, `salem_narration_points`). They overlap in real-world geography (the Hathorne mansion site is in both) but are separate datasets. The admin tool edits LocationMapApp POIs; the Oracle is the editorial brain that knows the Salem corpus.

**Build tasks:**
- [x] Add `web/src/admin/oracleClient.ts` — typed client wrapping the Oracle endpoints, default base URL `http://localhost:8088` (configurable via `VITE_SALEM_ORACLE_URL` env var). Exports `getStatus`, `ask`, `listPois`, `isAskOk` plus typed response shapes (`OracleStatus`, `OracleAskOk`, `OracleAskErr`, `OraclePrimarySource`, `OraclePoiSummary`). 5s timeout on status/catalog endpoints, 120s default on `ask` (60s minimum per spec, 120s for headroom on cold-cache turns). `OracleNetworkError` distinguishes connection failures from successful HTTP responses with `error` envelopes. **S106 verified** against live Oracle: response shape matches contract exactly (9 fields in `OracleAskOk`, all primary_source fields populated, `current_poi_id` accepts arbitrary strings per master plan §1352).
- [x] Add an "Oracle: ready / unavailable" status pill in `AdminLayout.tsx` header. Polled at mount + every 30s; click forces immediate re-poll. Three visual states: loading (grey "Oracle: …"), ready (emerald "Oracle: ready" with rich tooltip showing fact/PS/POI/newspaper counts + history turn count), unavailable (rose "Oracle: down" with tooltip pointing at `bash ~/Development/Salem/scripts/start-testapp.sh`). The pill state is mirrored down to `PoiEditDialog` via the new `oracleAvailable` prop so the Generate buttons disable cleanly when the testapp is offline.
- [x] Add **"Generate with Salem Oracle"** button to the **Narration tab** of `PoiEditDialog.tsx` (Step 9P.10):
  - Banner-style launcher (`OracleLauncher` component, full-width variant) at the top of the Narration tab
  - Opens a nested Headless UI `Dialog` (z-1100, sits above the main edit dialog at z-1000)
  - Sub-dialog has: prompt textarea, "Reset Oracle conversation history" checkbox (default ON for first call, auto-flips OFF after first successful generation so iterate works without re-checking), "Generate" button
  - Calls `oracleClient.ask({ question, current_poi_id: poi.id, reset })` with the LocationMapApp POI's id directly (NOT a Salem catalog id)
  - Spinner panel shows during the call with text explaining "Asking the Oracle… typically 5-15s. The LLM is gemma3:27b on the workstation GPU; concurrent calls would queue, so this is sequential."
  - Returned `text` rendered in a slate panel with `whitespace-pre-wrap`; turn count shown as "Oracle response · turn N"
  - Up to 8 `primary_sources` rendered in a collapsed `<details>` block with attribution, score, verbatim text, and modern_gloss in amber callouts
  - "Insert into short_narration / long_narration / description" buttons (filtered through `has(field)` so tour POIs without those fields don't show buttons that would be no-ops). Each calls `setValue(field, oracleResult.text, { shouldDirty: true, shouldTouch: true })` so the existing react-hook-form dirty-tracking → PUT pipeline picks it up automatically
  - "Iterate" button passes `reset: false` regardless of checkbox state to keep the rolling history alive
- [x] Add the same launcher to the **General tab** for the `description` field (compact variant, sits inline below the description textarea)
- [x] Surface `primary_sources` citations in the UI — collapsible `<details>` with score, attribution, verbatim text, modern gloss
- [x] Capture the full Oracle response (text + primary_sources + question + timestamp + poi_id + kind + target_field + history_turn_count) in `localStorage` under key `salem-oracle-audit`. Capped at 500 entries with FIFO rotation. Logged on **insert** (when the operator accepts a generation by clicking an Insert button), not on generation, since generate-but-discard is just thinking out loud. Best-effort: localStorage quota or JSON failures are warned to console, not surfaced.
- [x] Handle Oracle unavailable: dedicated rose-colored warning panel inside the sub-dialog with the start command and a "Re-check status" button that calls AdminLayout's `onOracleRefresh`. Generate / Iterate buttons disable when `oracleAvailable=false`.

**Workflow** (from oracle-api.md's worked example, adapted for LocationMapApp):
1. Operator picks a POI to edit in the tree (Step 9P.8) → edit dialog opens (Step 9P.10)
2. Operator clicks "Generate with Salem Oracle" on the Narration tab
3. Sub-dialog opens; operator types "Rewrite this description as a single 90-word tour-guide paragraph for spoken audio" or similar
4. Oracle returns prose + primary sources in 5-15s
5. Operator reviews, optionally iterates ("make that two sentences shorter"), accepts
6. Generated text gets pasted into the field; on Save, the existing `PUT /admin/salem/pois/:kind/:id` endpoint persists it to PG
7. Eventually Phase 9P.C publish loop regenerates `salem-content/salem_content.db` so the next APK build picks up the edit

**Operator instructions surfaced in UI:**
- Tooltip on the "Oracle: ready" pill: "Salem Oracle running at http://localhost:8088 — gemma3:27b backed by the full Salem corpus"
- Tooltip on the "Generate" button: "Asks the Salem LLM to compose content. 5-15 seconds per call. Single shared conversation history — use Reset to start fresh."

**Constraints to respect:**
- Don't fire concurrent Oracle calls from the same operator session — Ollama serializes them anyway, just queue client-side
- Show a spinner of at least 5s expected duration; the operator should not assume the call hung
- Respect the 60s minimum client timeout suggested in oracle-api.md
- Salem corpus is **read-only** from this API — the admin tool never tries to write to Salem. The admin tool writes only to LocationMapApp's PG (via existing 9P.4 endpoints).
- No production deployment of the Oracle — it's dev-workstation-only. Don't add a "publish to Oracle" feature; that's not how it works.

#### Step 9P.11: Highlight duplicates toggle
- [ ] Header button "Highlight Duplicates" in `AdminLayout.tsx`
- [ ] On click → fetch `/admin/salem/pois/duplicates?radius=15`
- [ ] Draw red rings around POIs that have ≥1 neighbor within 15m
- [ ] Click red ring → opens a side panel listing the duplicate group with Compare / Pick Winner buttons (basic UX, full merge UI deferred to v1.5)
- [ ] Toggle off → rings clear

---

### Phase 9P.C — Publish Loop

#### Step 9P.12: Rebuild salem_content.sql from PostgreSQL
- [ ] Create `cache-proxy/scripts/rebuild-salem-content-sql.js`
- [ ] Reads from PostgreSQL: `salem_tour_pois`, `salem_businesses`, `salem_narration_points`, all related tables
- [ ] Generates `salem-content/salem_content.sql` matching the existing format that the salem-content Gradle module expects
- [ ] Excludes soft-deleted rows (where `deleted_at IS NOT NULL`)
- [ ] Idempotent: running twice produces identical output
- [ ] Verify Gradle build of `salem-content` module still succeeds with regenerated SQL
- [ ] Verify Room migration is not triggered (schema unchanged from app's perspective)

#### Step 9P.13: Publish button in admin UI
- [ ] Header button "Publish" in `AdminLayout.tsx`
- [ ] On click → POST `/admin/publish` → triggers `rebuild-salem-content-sql.js` server-side
- [ ] Show progress + result (success/fail, row counts, generated file size)
- [ ] Display info banner explaining the loop: "Publish regenerates salem_content.sql. Next APK build will ship the updated data."

#### Step 9P.14: End-to-end verification
- [ ] Run admin tool, edit a POI's name
- [ ] Move a marker on the map, save the new position
- [ ] Soft delete a duplicate POI
- [ ] Click Publish, verify `salem-content/salem_content.sql` is updated
- [ ] Run `./gradlew :app-salem:assembleDebug`
- [ ] Install the APK on the test tablet (HNY0CY0W)
- [ ] Verify the edited POI appears with the new name
- [ ] Verify the moved POI appears at the new location
- [ ] Verify the soft-deleted POI is gone from the app
- [ ] Walk simulator end-to-end through the bounded area — narration still works
- [ ] Git commit: "Phase 9P: POI admin tool — narration_points migration + admin UI + publish loop"

---

### Out of scope for v1 (deferred)
- Photo upload (GeoInbox integration) — Phase 9P.D or later
- Merchant accounts and self-service editing — Phase 17
- Audit log and change-tracking — Phase 17 (when payments enter the picture)
- Live OTA sync (Server → Android) — Phase 10 or later
- Multi-user roles — not needed for single-operator localnet
- Full duplicate-merge UI with metadata reconciliation — Phase 9P.D
- Categories table in PostgreSQL (vs. duplicated TypeScript) — deferred unification
- Building → POI bridge construction — Phase 9Q
- Historic Tour Mode app feature — Phase 9R

---

## Phase 9U — Unified POI Table & SalemIntelligence Import

**Goal:** Merge `salem_narration_points` (817 rows), `salem_businesses` (861 rows), and `salem_tour_pois` (45 rows) into a single canonical `salem_pois` table. Then import ~900 new entities from SalemIntelligence's BCS (Business Current State) export to bring the total to ~2,600 enriched Salem POIs. Every POI — restaurant, witch shop, dentist, park, historic house — lives in one row with one schema, one category FK, and one admin interface. Non-tourist POIs (healthcare, auto services, offices, etc.) exist in the table with `default_visible = false`, available via layer toggles.

**Motivation (Session 116 analysis):** The current three-table split is an artifact of the original import pipeline, not a meaningful data distinction. 817 out of 817 narration points have matching businesses with identical names, coordinates, phone numbers, and descriptions. They're the same entities stored twice with different column sets. SalemIntelligence adds 1,724 BCS entities (many overlapping, ~900 genuinely new) with much richer metadata (hours, amenities, owners, origin stories). Importing into the split structure would mean deciding "is this a business or a narration point?" for every new entity. A unified table eliminates that question permanently.

**Key architectural decision (Session 116):** V1 commercial release is fully offline. SalemIntelligence is a build-time data source only — we call `GET /api/intel/poi-export` once at dev time, import into PG, run through the publish pipeline into Room DB, and ship in the APK. No runtime API calls. The `intel_entity_id` column stores the SalemIntelligence FK for future v2 online features at zero cost.

**Target:** 4 sessions (S117-S120) | **Status:** PLANNING COMPLETE (Session 116) | **Added:** Session 116 | **Priority:** HIGHEST — blocks 9P.C (publish loop), 9Q (content bridge), and 9R (historic tour mode)

**Supersedes:** The Phase 9P.B+ taxonomy alignment arc (Steps 1-4 from `docs/poi-taxonomy-plan.md`). The unified table absorbs the category backfill, FK enforcement, publish loop, and Room migration that were planned as separate steps. `docs/poi-taxonomy-plan.md` remains as historical reference but is no longer the active plan.

---

### Session 117 — Schema Migration & Three-Table Merge

#### Step 9U.1: Create `salem_pois` unified table

- [ ] Add `CREATE TABLE salem_pois` to `cache-proxy/salem-schema.sql` with the superset schema:

  **Core identity:**
  - `id TEXT PRIMARY KEY`
  - `name TEXT NOT NULL`, `lat DOUBLE PRECISION NOT NULL`, `lng DOUBLE PRECISION NOT NULL`
  - `address TEXT`
  - `status TEXT DEFAULT 'open'` — open / temporarily_closed / seasonal / unknown

  **Taxonomy (FK-enforced):**
  - `category TEXT REFERENCES salem_poi_categories(id)` — NOT NULL after backfill
  - `subcategory TEXT REFERENCES salem_poi_subcategories(id)`

  **Narration layer (null for non-narrated POIs):**
  - `short_narration TEXT`, `long_narration TEXT`, `narration_pass_2 TEXT`, `narration_pass_3 TEXT`
  - `geofence_radius_m INTEGER DEFAULT 40`, `geofence_shape TEXT DEFAULT 'circle'`, `corridor_points TEXT`
  - `priority INTEGER DEFAULT 3`, `wave INTEGER`
  - `voice_clip_asset TEXT`, `custom_voice_asset TEXT`

  **Business layer (null for parks/public art/etc):**
  - `cuisine_type TEXT`, `price_range TEXT`, `rating REAL`
  - `merchant_tier INTEGER DEFAULT 0`, `ad_priority INTEGER DEFAULT 0`

  **Historical/tour layer (null for non-historic POIs):**
  - `historical_period TEXT`, `historical_note TEXT`
  - `admission_info TEXT`
  - `requires_transportation BOOLEAN DEFAULT false`
  - `wheelchair_accessible BOOLEAN DEFAULT true`
  - `seasonal BOOLEAN DEFAULT false`

  **Contact/hours:**
  - `phone TEXT`, `email TEXT`, `website TEXT`
  - `hours JSONB` — structured JSON (upgrade from text)
  - `hours_text TEXT` — preserve legacy freeform hours strings
  - `menu_url TEXT`, `reservations_url TEXT`, `order_url TEXT`

  **Content:**
  - `description TEXT`, `short_description TEXT`, `custom_description TEXT`
  - `origin_story TEXT`
  - `image_asset TEXT`, `custom_icon_asset TEXT`
  - `action_buttons JSONB DEFAULT '[]'`

  **SalemIntelligence enrichment:**
  - `intel_entity_id TEXT` — SalemIntelligence BCS entity_id (FK for v2 online features)
  - `secondary_categories JSONB DEFAULT '[]'`
  - `specialties JSONB DEFAULT '[]'`
  - `owners JSONB DEFAULT '[]'`
  - `year_established INTEGER`
  - `amenities JSONB DEFAULT '{}'`
  - `district TEXT`

  **Relations:**
  - `related_figure_ids JSONB DEFAULT '[]'`
  - `related_fact_ids JSONB DEFAULT '[]'`
  - `related_source_ids JSONB DEFAULT '[]'`
  - `source_id TEXT`, `source_categories JSONB DEFAULT '[]'`
  - `tags JSONB DEFAULT '[]'`

  **Provenance:**
  - `data_source TEXT NOT NULL DEFAULT 'unified_migration'`
  - `confidence REAL NOT NULL DEFAULT 0.8`
  - `verified_date TIMESTAMPTZ`, `stale_after TIMESTAMPTZ`
  - `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
  - `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`
  - `deleted_at TIMESTAMPTZ`

  **Flags:**
  - `is_tour_poi BOOLEAN DEFAULT false` — the 45 curated 1692 stops
  - `is_narrated BOOLEAN DEFAULT false` — has narration content
  - `default_visible BOOLEAN DEFAULT true` — overridable per-POI visibility

  **Legacy audit (dropped after verification):**
  - `legacy_narration_category TEXT` — original `salem_narration_points.category` value
  - `legacy_business_type TEXT` — original `salem_businesses.business_type` value
  - `legacy_tour_category TEXT` — original `salem_tour_pois.category` value

- [ ] Indexes: `(category)`, `(lat, lng)`, `(data_source)`, `(deleted_at) WHERE deleted_at IS NULL`, `(merchant_tier) WHERE merchant_tier > 0`, `(stale_after) WHERE stale_after IS NOT NULL`, `(wave) WHERE wave IS NOT NULL`, `(intel_entity_id) WHERE intel_entity_id IS NOT NULL`, `(is_tour_poi) WHERE is_tour_poi = true`, `(district) WHERE district IS NOT NULL`
- [ ] Run schema migration

#### Step 9U.2: Migration script — three tables into one

- [ ] Create `cache-proxy/scripts/migrate-to-unified-pois.js`
- [ ] **Phase A: Narration points (817 rows).** These have the richest data. INSERT into `salem_pois` with:
  - All narration-specific columns mapped directly
  - `is_narrated = true`
  - `legacy_narration_category` preserves the original category value
  - `category` assigned by mapping the 29 legacy values to `PoiLayerId` form (the mapping from `docs/poi-taxonomy-plan.md` Step 2)
- [ ] **Phase B: Business enrichment (817 matched + 44 new).** For each business:
  - Match to existing `salem_pois` row by `id` (IDs are identical across tables, confirmed S116)
  - If matched: UPDATE to add `cuisine_type`, `price_range`, `rating`, `historical_note`, `legacy_business_type`
  - If unmatched (the 44 unique businesses): INSERT as new rows with `is_narrated = false`
- [ ] **Phase C: Tour POIs (45 rows).** For each tour POI:
  - Match to existing `salem_pois` row by name + coordinate proximity (tour POI IDs may differ)
  - If matched: UPDATE to set `is_tour_poi = true`, add `historical_period`, `admission_info`, `requires_transportation`, `wheelchair_accessible`, `seasonal`, `legacy_tour_category`
  - If unmatched: INSERT as new row with `is_tour_poi = true`
- [ ] Transaction-safe (BEGIN/COMMIT/ROLLBACK)
- [ ] Dry-run mode (log what would happen without writing)
- [ ] Post-migration counts: expect ~862 rows (817 narration + ~45 unique from businesses/tour after dedup)

#### Step 9U.3: Repoint FK references from `salem_tour_pois` to `salem_pois`

- [ ] 5 tables currently FK to `salem_tour_pois(id)`:
  - `salem_events_calendar.venue_poi_id`
  - `salem_historical_facts.poi_id`
  - `salem_historical_figures.primary_poi_id`
  - `salem_primary_sources.poi_id`
  - `salem_timeline_events.poi_id`
  - `salem_tour_stops.poi_id`
- [ ] For each: `ALTER TABLE ... DROP CONSTRAINT ..., ADD CONSTRAINT ... REFERENCES salem_pois(id)`
- [ ] Verify all existing FK values resolve in `salem_pois`
- [ ] Verify queries against these tables still return correct results

#### Step 9U.4: Update admin tool backend (`cache-proxy/lib/admin-pois.js`)

- [ ] Replace the three-kind dispatch (`tour` / `business` / `narration`) with single-table queries against `salem_pois`
- [ ] Preserve backward-compatible URL structure during transition if needed, or simplify to:
  - `GET /admin/salem/pois` — list with filters (category, district, is_tour_poi, is_narrated, bbox, q, include_deleted)
  - `GET /admin/salem/pois/:id` — single POI
  - `PUT /admin/salem/pois/:id` — partial update (unified whitelist)
  - `POST /admin/salem/pois/:id/move` — lat/lng only
  - `DELETE /admin/salem/pois/:id` — soft delete
  - `POST /admin/salem/pois/:id/restore` — undo soft delete
  - `GET /admin/salem/pois/duplicates` — unchanged
- [ ] Update field whitelist to cover the unified schema
- [ ] Update public read endpoints (`/salem/pois`, `/salem/businesses`) to query `salem_pois` with appropriate filters
- [ ] Smoke test all endpoints

#### Step 9U.5: Update admin tool frontend

- [ ] **PoiTree.tsx:** Replace three-kind fetch with single `GET /admin/salem/pois`. Tree grouping becomes: category → subcategory → POI (the subcategory level that was missing when subcategory was all NULL — now it can be populated). Retain ability to filter by `is_tour_poi` / `is_narrated` as tree-level toggles.
- [ ] **AdminMap.tsx:** Color markers by category instead of kind (22 colors from `salem_poi_categories.color`). Or keep kind-based coloring as a toggle.
- [ ] **PoiEditDialog.tsx:** Show fields contextually based on category + flags. A restaurant shows cuisine_type, price_range, menu_url. A park shows wheelchair_accessible. A witch shop shows neither. Use category metadata to drive field visibility.
- [ ] **poiAdminFields.ts:** Replace the three per-kind whitelists with one unified whitelist + category-conditional display rules.
- [ ] Verify tree, map, edit, save, delete, move all work against the unified table

#### Step 9U.6: Rename old tables + verification

- [ ] Rename `salem_narration_points` → `salem_narration_points_legacy`
- [ ] Rename `salem_businesses` → `salem_businesses_legacy`
- [ ] Rename `salem_tour_pois` → `salem_tour_pois_legacy`
- [ ] Run full admin tool smoke test against `salem_pois`
- [ ] Verify POI counts match: unified total should equal (narration + unique businesses + unique tour pois)
- [ ] Spot-check 10 POIs: The Witch House, Hex, Finz, Pioneer Village, CVS Pharmacy, Salem Common, etc.
- [ ] Git commit: "Phase 9U Session 117: unified salem_pois table — three-table merge complete"

---

### Session 118 — SalemIntelligence BCS Import & Category Assignment

#### Step 9U.7: Pull BCS data snapshot

- [ ] Call `GET http://localhost:8089/api/intel/poi-export` (SalemIntelligence Phase 1 KB, 1,724 BCS entities)
- [ ] Save response as `tools/salem-data/bcs-export-YYYY-MM-DD.json` (versioned in repo — this is the build-time data source, not a runtime dependency)
- [ ] Document the snapshot date and BCS entity count in the file header

#### Step 9U.8: BCS → LMA category mapping table

- [ ] Create `cache-proxy/scripts/bcs-category-mapping.js` with the mapping from S116 analysis:

  **Direct mappings (no decomposition needed):**
  | BCS `primary_category` | → LMA `category` (PoiLayerId) |
  |---|---|
  | `restaurant` | `FOOD_DRINK` |
  | `cafe` | `FOOD_DRINK` |
  | `bar` | `FOOD_DRINK` |
  | `shop_occult` | `WITCH_SHOP` |
  | `museum` | `TOURISM_HISTORY` |
  | `attraction` | `TOURISM_HISTORY` |
  | `hotel_lodging` | `LODGING` |
  | `gallery_art` | `TOURISM_HISTORY` |
  | `shop_bookstore` | `SHOPPING` |
  | `shop_antiques` | `SHOPPING` |
  | `performance_venue` | `ENTERTAINMENT` |
  | `education` | `EDUCATION` |
  | `religious` | `WORSHIP` |
  | `fitness` | `ENTERTAINMENT` |
  | `service_health` | `HEALTHCARE` |
  | `spa_beauty` | `SHOPPING` |

  **Decomposition mappings (require secondary_categories/specialties/name heuristics):**
  | BCS `primary_category` | Heuristic | → LMA `category` |
  |---|---|---|
  | `service_professional` | auto/garage/tire/motor keywords | `AUTO_SERVICES` |
  | `service_professional` | bank/credit/financial/mortgage | `FINANCE` |
  | `service_professional` | law/attorney/real estate/insurance | `OFFICES` |
  | `service_professional` | health/dental/therapy | `HEALTHCARE` |
  | `service_professional` | school/tutor/childcare | `EDUCATION` |
  | `service_professional` | post office/government/court | `CIVIC` |
  | `service_professional` | fuel/gas station/energy | `FUEL_CHARGING` |
  | `service_professional` | market/deli/bakery/food | `FOOD_DRINK` |
  | `service_professional` | apartment/condo/housing | `LODGING` |
  | `service_professional` | cleaner/laundry/print/sign | `SHOPPING` |
  | `service_professional` | photo/video/studio/media | `ENTERTAINMENT` |
  | `service_professional` | taxi/limo/ferry/moving | `TRANSIT` |
  | `service_professional` | (default fallback) | `OFFICES` |
  | `shop_retail` | witch/occult/crystal/pagan keywords | `WITCH_SHOP` |
  | `shop_retail` | (all others) | `SHOPPING` |
  | `tour_operator` | ghost/haunted/vampire keywords | `GHOST_TOUR` |
  | `tour_operator` | (all others — food, sailing, walking) | `TOURISM_HISTORY` |
  | `other` | per-entity manual triage | various |

- [ ] Output: a JSON mapping `{ bcs_entity_id → { lma_category, lma_subcategory, confidence } }` for every BCS entity
- [ ] Dry-run report: count per LMA category, flag unmapped entities for manual review

#### Step 9U.9: Add new subcategories to taxonomy

- [ ] Add to `PoiCategories.kt` (Salem app) and `poiCategories.ts`:
  - TOURISM_HISTORY: `tour_operator` subtag (harbor cruises, whale watches, walking tours)
  - SHOPPING: `antiques` subtag
  - SHOPPING: `cannabis` subtag (4 dispensaries, legal in MA)
  - SHOPPING: `souvenir` subtag (32 gift/souvenir shops)
- [ ] Run `sync-poi-taxonomy.js` to update `salem_poi_categories` and `salem_poi_subcategories`
- [ ] Verify new subcategories appear in PG

#### Step 9U.10: BCS import script

- [ ] Create `cache-proxy/scripts/import-bcs-pois.js`
- [ ] Read `tools/salem-data/bcs-export-YYYY-MM-DD.json` + the category mapping from 9U.8
- [ ] **Match phase:** For each BCS entity, attempt match to existing `salem_pois` by:
  - Exact name match + coordinate proximity (< 50m)
  - Fuzzy name match (case-insensitive, strip punctuation) + coordinate proximity (< 100m)
  - Coordinate proximity only (< 20m) as fallback
- [ ] **Enrich phase:** For matched entities, UPDATE existing rows to add BCS metadata:
  - `intel_entity_id`, `phone` (if null), `website` (if null), `email`
  - `hours` (JSONB from BCS, replacing or supplementing text), `hours_text` (preserve original)
  - `short_description` (if null), `origin_story`, `secondary_categories`, `specialties`, `owners`, `year_established`, `amenities`, `district`, `status`
  - `menu_url`, `reservations_url`, `order_url`
  - Do NOT overwrite existing `description`, `short_narration`, `long_narration` — BCS descriptions are less curated
  - Update `confidence` to `max(existing, bcs)` — take the higher confidence
- [ ] **Insert phase:** For unmatched BCS entities (~900 new), INSERT as new rows with:
  - `id` = BCS `entity_id` (UUID format)
  - `category` from the mapping, `subcategory` where assignable
  - `is_narrated = false`, `is_tour_poi = false`
  - `default_visible` based on category's `defaultEnabled` flag
  - `data_source = 'salemintelligence_bcs'`
- [ ] Transaction-safe, dry-run mode
- [ ] Post-import report: matched count, enriched count, new insert count, total

#### Step 9U.11: Set default visibility per category

- [ ] After import, run an UPDATE to set `default_visible` on all rows based on their category:
  - `default_visible = true` for: FOOD_DRINK, WITCH_SHOP, PSYCHIC, GHOST_TOUR, HAUNTED_ATTRACTION, HISTORIC_HOUSE, TOURISM_HISTORY, LODGING, ENTERTAINMENT, PARKS_REC
  - `default_visible = false` for: HEALTHCARE, SHOPPING (non-tourist subcategories), OFFICES, FINANCE, AUTO_SERVICES, EDUCATION, WORSHIP, FUEL_CHARGING, TRANSIT, CIVIC, PARKING, EMERGENCY
- [ ] Per-POI overrides remain possible (a particularly notable dentist near the tour route could be set visible manually)
- [ ] Verify counts: expect ~750 visible (tourist tier), ~1,850 available on demand (utility tier)
- [ ] Git commit: "Phase 9U Session 118: SalemIntelligence BCS import — ~2,600 unified POIs with category assignment"

---

### Session 119 — Admin Tool Adaptation & Publish Loop

#### Step 9U.12: Admin tool tree rework for unified table

- [ ] Update `PoiTree.tsx` for unified `salem_pois`:
  - Single fetch: `GET /admin/salem/pois?limit=3000&include_deleted=true`
  - Tree grouping: category → subcategory → POI (the fourth level that was impossible before)
  - Category nodes show the canonical label + color from `salem_poi_categories`
  - Top-level filter toggles: "Tour POIs only", "Narrated only", "Visible by default", "All"
  - Count strip updates to reflect unified total
- [ ] Update `AdminMap.tsx`:
  - Color by category (22 colors from taxonomy) with kind as secondary indicator (icon shape or badge)
  - `default_visible = false` POIs render at reduced opacity or with a distinct marker style
  - Legend updates
- [ ] Update `PoiEditDialog.tsx`:
  - Contextual field display: category determines which field groups appear
  - FOOD_DRINK shows: cuisine_type, price_range, menu_url, hours
  - TOURISM_HISTORY shows: historical_period, admission_info, wheelchair_accessible
  - HEALTHCARE shows: phone, hours, website (prominently)
  - WITCH_SHOP shows: specialties, origin_story
  - Universal fields (name, address, lat/lng, description, category, tags, provenance) always show
  - Boolean flags section: is_tour_poi, is_narrated, default_visible — always editable
- [ ] Verify the full loop: browse tree → select POI → see on map → edit → save → tree/map update

#### Step 9U.13: Publish loop (PG → Room DB)

- [ ] Create `cache-proxy/scripts/publish-to-sqlite.js`:
  - Reads all active (non-deleted) rows from `salem_pois` + related tables
  - Generates `salem-content/salem_content.db` (SQLite, the Room DB source)
  - Maps the unified `salem_pois` schema to the Room entity schema
  - Also generates `salem-content/salem_content.sql` for the Gradle pipeline
- [ ] Wire to admin UI: "Publish" button calls `POST /admin/publish` → runs the script
- [ ] Verify the generated SQLite has correct row counts and schema

#### Step 9U.14: Room DB migration

- [ ] Update Room entity in `app-salem/` to match the unified `salem_pois` schema
- [ ] Bump Room schema version
- [ ] Write Room migration (or use `fallbackToDestructiveMigration` for pre-release — real migration before Play Store per STATE.md carry-forward)
- [ ] Update all Android DAOs that query narration points or businesses to query the unified table
- [ ] Update `NarrationGeofenceManager` — it currently queries narration points; now queries `salem_pois WHERE is_narrated = true`
- [ ] Update POI layer rendering — filter by `default_visible` and per-category user preferences
- [ ] Build + install on Lenovo TB305FU
- [ ] Walk simulator validation: narration still fires, POIs render correctly, layer toggles work
- [ ] Git commit: "Phase 9U Session 119: admin tool unified + publish loop + Room migration"

---

### Session 120 — Verification, Cleanup & Heading-Up Finish

#### Step 9U.15: End-to-end verification

- [ ] Run admin tool — browse the full tree (expect ~2,600 POIs in 22 categories)
- [ ] Edit a POI in each major category: FOOD_DRINK, WITCH_SHOP, HEALTHCARE, TOURISM_HISTORY, OFFICES
- [ ] Move a marker on the map, save, verify
- [ ] Soft delete a duplicate, verify it disappears from map
- [ ] Click Publish, verify `salem_content.db` regenerates
- [ ] Build APK, install on tablet
- [ ] Walk simulator: narration fires for narrated POIs, non-narrated utility POIs appear when layer toggled
- [ ] Toggle HEALTHCARE layer on — verify dentists/pharmacies/hospitals appear with phone numbers
- [ ] Toggle off — verify they disappear
- [ ] Spot-check BCS-enriched POIs: do they have hours, phone, amenities that weren't there before?

#### Step 9U.16: Drop legacy tables

- [ ] After verification, drop (or archive to `_legacy` suffix):
  - `salem_narration_points_legacy`
  - `salem_businesses_legacy`
  - `salem_tour_pois_legacy`
- [ ] Remove legacy code paths from cache-proxy
- [ ] Remove old import scripts that target the split tables
- [ ] Archive `docs/poi-taxonomy-plan.md` to `docs/archive/` (superseded by Phase 9U)

#### Step 9U.17: Heading-up rotation smoothness (deferred from S116)

- [ ] Complete the S115 heading-up fix plan:
  1. Cut `ORIENT-RAW` and `HEADING-UP: skip hysteresis` DEBUG traces from the hot path
  2. Rate limit apply path to ~33ms (30 Hz)
  3. Move sensor processing to background HandlerThread if still needed
  4. Switch static-mode detection from sample count to wall-clock
- [ ] Build + test on Lenovo TB305FU
- [ ] Git commit: "Phase 9U Session 120: verification complete + heading-up rotation fix"

---

### Out of scope for Phase 9U (deferred)

- Subcategory backfill for the full 2,600 POIs (category is assigned; subcategory is best-effort from BCS `secondary_categories` but many will stay NULL — admin worklist for operator curation)
- Duplicate merge UI (Phase 9P.11 highlight duplicates still pending; unified table makes dedup easier)
- Hero image regeneration (blocked on SalemIntelligence Phase 2 gate)
- Building → POI bridge construction (Phase 9Q — now simpler with unified table: `salem_building_poi_map.poi_id` references `salem_pois.id` directly, no `poi_kind` column needed)
- Live OTA sync (Phase 10)
- `hours` text → JSONB parsing for existing freeform strings (BCS imports get structured JSONB; legacy rows keep `hours_text`)

---

## Phase 9Q — Salem Domain Content Bridge

**Goal:** Build the translation layer between Salem's historical-domain ontology (`building_id`, the Salem Village/Town historical buildings) and LocationMapApp's POI ontology (modern coordinates on a map). Once the building→POI bridge exists, every Salem historical entity (figures, facts, events, primary sources, newspapers via `events_referenced`) becomes queryable by POI through graph traversal — and the schema's currently-NULL FK columns finally get populated.

**Target:** 2-3 sessions | **Status:** PLANNING | **Added:** Session 97 | **Priority:** queued behind Phase 9P

### Rationale
Investigation in Session 97 surfaced two critical facts about the LocationMapApp ↔ Salem cross-project boundary:

1. **Salem's spatial unit is `building_id`, not POI.** Salem JSON entities reference historical 1692 buildings (Putnam house, Parris parsonage, Meeting House, etc.). They have no POI field and no knowledge of LocationMapApp's existence. The translation `building_id → poi_id` must live entirely on the LocationMapApp side, in `salem-content/`.
2. **A new content stream landed in Salem session 044:** 202 daily Salem Oracle newspaper articles (Nov 1 1691 → May 9 1693, ~13.5 hours of TTS-ready content) with `tts_full_text` and `events_referenced` fields. The `events_referenced` field is the join key that lets newspapers reach POIs transitively: newspaper → event → building → POI.

The historical FK columns in `salem_historical_figures.primary_poi_id`, `salem_historical_facts.poi_id`, `salem_timeline_events.poi_id`, and `salem_primary_sources.poi_id` are all currently NULL because the bridge has never been built. Phase 9Q builds it.

### Cross-project dependency
Salem session 044 (2026-04-07) added the newspaper corpus per OMEN-S004. Schema documented at `~/Development/Salem/data/json/newspapers/README.md`. **Each newspaper article carries `events_referenced` (list of anchor/minor event IDs)** — this is the load-bearing join field for the bridge. Salem maintains the corpus; LocationMapApp consumes it via a new Reader following the existing pattern (NpcReader, FactReader, EventReader, PrimarySourceReader, NEW NewspaperReader).

### Step 9Q.1: salem_buildings table + BuildingReader
- [ ] Add `CREATE TABLE salem_buildings` to `cache-proxy/salem-schema.sql` (mirror Salem JSON building schema: id, name, building_type, historical_period, address_modern, lat_modern, lng_modern, still_exists, notes, provenance fields)
- [ ] Run schema migration against local PostgreSQL
- [ ] Create `salem-content/src/main/kotlin/.../readers/BuildingReader.kt` following the existing reader pattern
- [ ] Reader ingests all 425 building JSON files from `~/Development/Salem/data/json/buildings/`
- [ ] Add `cache-proxy/scripts/import-buildings.js` for the SQL load (the JVM pipeline writes the SQL; the JS importer runs it against PostgreSQL)
- [ ] Verify final row count (~425)

### Step 9Q.2: salem_building_poi_map join table
- [ ] Add `CREATE TABLE salem_building_poi_map` to `cache-proxy/salem-schema.sql`:
  - `building_id TEXT REFERENCES salem_buildings(id)`
  - `poi_id TEXT REFERENCES salem_pois(id)` — **simplified by Phase 9U unified table (no `poi_kind` column needed)**
  - `link_type TEXT NOT NULL` — `'exact'` | `'memorial'` | `'representative'` | `'nearby'`
  - `confidence REAL DEFAULT 1.0`
  - `notes TEXT`
  - Provenance fields
  - `PRIMARY KEY (building_id, poi_id)`
- [ ] Many-to-many by design: a single building may map to multiple POIs (e.g., a destroyed building's two surviving foundations); a single POI may represent multiple historical buildings (e.g., a museum complex)
- [ ] Indexes: `(building_id)`, `(poi_id)`, `(link_type)`

### Step 9Q.3: Draft auto-mapping + manual curation
- [ ] Write `cache-proxy/scripts/draft-building-poi-map.js`:
  - For each building in `salem_buildings`, attempt name-match against unified `salem_pois` (case-insensitive substring + trigram similarity)
  - If a building has `lat_modern`/`lng_modern`, also try geo-proximity match (POIs within 50m)
  - Output a draft CSV at `tools/salem-data/draft-building-poi-map.csv` with columns: building_id, building_name, suggested_poi_id, suggested_poi_name, distance_m, name_similarity, link_type_guess, confidence_guess
  - **Does NOT write to PostgreSQL.** Operator reviews the CSV.
- [ ] Operator manually curates the CSV (likely 2-4 hours of review for ~425 buildings, of which probably only 50-80 will have actual POI mappings)
- [ ] Curated CSV → `cache-proxy/scripts/import-building-poi-map.js` writes to `salem_building_poi_map`
- [ ] Buildings with no POI mapping get NO row in the bridge (NULL = unmapped, by absence)
- [ ] Spot-check 10 mappings against the source data for fidelity

### Step 9Q.4: salem_newspaper_articles table + NewspaperReader
- [ ] Add `CREATE TABLE salem_newspaper_articles` to schema:
  - `id TEXT PRIMARY KEY`, `publish_date DATE NOT NULL`, `headline TEXT NOT NULL`, `body TEXT`, `tts_full_text TEXT NOT NULL`
  - `events_referenced JSONB DEFAULT '[]'` — list of anchor/minor event IDs
  - `figures_referenced JSONB DEFAULT '[]'` — convenience denormalization for fast lookups
  - `audio_seconds_estimate INTEGER` — for tour pacing
  - Provenance fields
  - Index: `(publish_date)`, GIN index on `events_referenced`
- [ ] Create `salem-content/src/main/kotlin/.../readers/NewspaperReader.kt`
- [ ] Ingest all 202 newspaper JSONs from `~/Development/Salem/data/json/newspapers/`
- [ ] Verify final row count = 202, date range = Nov 1 1691 → May 9 1693

### Step 9Q.5: Backfill historical FKs by graph traversal
- [ ] Write `cache-proxy/scripts/populate-historical-fks.js`
- [ ] For each row in `salem_historical_figures`:
  - Read `building_id` from the figure's source JSON (re-ingest if needed)
  - Look up `building_id` in `salem_building_poi_map`
  - If a mapping with `link_type='exact'` or `link_type='representative'` exists, set `primary_poi_id` to that POI
  - If multiple POIs match, pick the one with highest confidence; record alternates in a separate `salem_historical_figure_aliases` table (deferred to 9Q.6 if needed)
- [ ] Same logic for `salem_historical_facts.poi_id`, `salem_timeline_events.poi_id`, `salem_primary_sources.poi_id`
- [ ] Idempotent: re-runnable as the bridge grows
- [ ] Verification: count NULLs before and after; expect 50-80% of historical entities to gain a POI link

### Step 9Q.6: Building Map admin panel
- [ ] Extend the Phase 9P admin tool with a new top-level view: **Building Map**
- [ ] Two-pane layout: list of buildings on the left (with search + filter by mapped/unmapped status), Leaflet map on the right showing POIs
- [ ] Click a building → highlights its currently-mapped POI(s) on the map (red rings) + shows building metadata in a side panel
- [ ] To map a new POI: click the building, then click a POI on the map → modal: "Link [building name] to [POI name] as [exact / memorial / representative / nearby]?"
- [ ] To unmap: click the existing mapping in the side panel, click Remove
- [ ] After every save, the admin tool re-runs `populate-historical-fks.js` automatically (or at least flags it as stale)
- [ ] Operator can iteratively grow the bridge over time without leaving the tool

### Step 9Q.7: Verification
- [ ] Pick 5 known historical events from the 1692 timeline (e.g., "examination of Tituba", "Bridget Bishop's execution")
- [ ] Query: "what POIs are linked to this event?"
- [ ] Manually verify the answers make geographic sense
- [ ] Pick 3 newspaper articles, query: "what POIs are linked to this article via events_referenced?"
- [ ] Verify the chain works
- [ ] Pick 3 POIs known to have historical depth (e.g., Witch House, Old Burying Point, Salem Common), query: "what historical content is linked to this POI?"
- [ ] Verify counts match expectations
- [ ] Update Phase 9P.10a "Linked Historical Content" tab in the admin tool — should now show non-empty results
- [ ] Git commit: "Phase 9Q: Salem domain content bridge — buildings, building→POI map, newspapers, historical FK backfill"

### Out of scope for Phase 9Q (deferred)
- Building→POI bridge UI for mass operations (bulk import from CSV is fine, mass UI editing is later)
- Confidence-weighted FK selection (use highest-confidence mapping for v1; ambiguity resolution UI deferred)
- `salem_historical_figure_aliases` table (only built if 9Q.5 reveals real ambiguity)
- Machine learning for auto-suggesting building↔POI matches (manual curation is fine for ~425 buildings)

---

## Phase 9R — Historic Tour Mode (App-Side)

**Goal:** Add an opt-in **Historic Tour Mode** to the Salem app that suppresses ambient POI narration, plays curated historical content (figures, facts, events, primary sources, newspaper articles) as the user walks, and uses tour-mode-specific category visibility settings. Implements the **hybrid model** (user picks a chapter; chapter is a curated route through 4-8 POIs in chronological order; content fires by GPS proximity but in guaranteed-narrative order because the route is curated).

**Target:** 4-6 sessions | **Status:** PLANNING | **Added:** Session 97 | **Priority:** queued behind Phase 9Q

### Rationale
The current app has only one mode: **free-roam ambient narration**, where any POI within geofence range fires its narration. This is great for casual wandering but breaks storytelling — a user could hear about the executions before they hear about the accusations. The 1692 Witch Trials story is inherently chronological and demands narrative ordering.

The hybrid tour model preserves walking agency while locking narrative coherence at the chapter level:
- User picks a chapter (e.g., "The Accusations Begin", "The Trials", "The Executions")
- Each chapter = a curated route through 4-8 POIs in chronological story order
- As user walks the chapter route, content fires by GPS proximity but in guaranteed-narrative order because the route is curated
- Newspaper articles play as long-form readings (~4 min each, one per chapter, not all 202)
- Ambient narrations from non-tour POIs are fully suppressed
- User can exit historic tour mode at any time and return to free-roam

### Pre-requisites
- Phase 9P complete (admin tool exists, narration_points in PostgreSQL)
- Phase 9Q complete (building→POI bridge populated, historical FKs backfilled, newspaper table populated)
- Per-mode category visibility schema in place (Step 9P.4a)

### Step 9R.1: Tour mode state machine
- [ ] Add `TourMode` enum to `TourViewModel` (or equivalent state holder): `FreeRoam` | `HistoricTour`
- [ ] Default: `FreeRoam`. Persisted in SharedPreferences.
- [ ] Formalize the existing implicit ambient suppression (currently `TourEngine.ambientModeEnabled` checked at line 314) into an explicit state-machine rule: ambient hints fire ONLY when `tourMode == FreeRoam` AND no active tour
- [ ] When `tourMode == HistoricTour`, ambient is fully suppressed; only the historic tour content sequencer drives narration

### Step 9R.2: Per-mode category visibility runtime
- [ ] Implement the two-prefKey lookup designed in Step 9P.4a
- [ ] `PoiCategories.isCategoryVisible(category, mode)` returns the appropriate boolean
- [ ] Settings UI: existing free-roam toggles work as before; new tour-mode toggles appear only when in tour mode (or under a "Historic Tour Settings" expandable section)
- [ ] First-launch migration: existing `poi_<category>_on` SharedPref keys become the freeroam values; tour-mode keys initialized to the restrictive defaults from `PoiCategories.kt`

### Step 9R.3: Historic tour chapter definitions
- [ ] Define 5-7 chapters covering the 1692 timeline:
  - Chapter 1: "Strange Behavior in the Parris Household" (Jan-Feb 1692)
  - Chapter 2: "The First Accusations" (late Feb-March 1692)
  - Chapter 3: "Examinations and Spectral Evidence" (March-April 1692)
  - Chapter 4: "The Court of Oyer and Terminer" (May-June 1692)
  - Chapter 5: "Bridget Bishop and the First Execution" (June 1692)
  - Chapter 6: "Summer of Death" (July-September 1692)
  - Chapter 7: "Aftermath and Reversal" (October 1692-1693)
- [ ] For each chapter: 4-8 POIs in walking order, 1 newspaper article anchor, list of associated figures/events/primary sources
- [ ] Store as JSON config in `app-salem/src/main/assets/historic-tour-chapters.json` (sourced from PostgreSQL via the publish loop)
- [ ] Each chapter has an estimated walk time (15-30 min)

### Step 9R.4: Historic tour content sequencer
- [ ] New class `HistoricTourSequencer` (in `core` or `app-salem`)
- [ ] Given an active chapter, current GPS, and walked POIs, decide what to play next
- [ ] Sequence: chapter intro narration → newspaper article (~4 min) → primary sources for the anchor event → historical figures' brief intros → next POI cue
- [ ] Pacing: between content items, brief silence (~5s) so user can absorb
- [ ] Skip controls: skip current item, skip to next POI, pause chapter
- [ ] When user reaches the chapter's last POI: chapter outro narration → "next chapter" prompt

### Step 9R.5: NarrationPlayer support for newspaper TTS
- [ ] Extend `NarrationPlayer` to handle long-form newspaper articles (`tts_full_text`, ~4 min each)
- [ ] Pause/resume at sentence boundaries
- [ ] Display the article headline + date as the narration plays
- [ ] Provide a transcript view (scrollable text) for users who prefer reading
- [ ] Accessibility: TTS speed control, font size control for transcript

### Step 9R.6: Historic tour mode UI
- [ ] Splash screen: tour mode toggle ("Free Roam" / "Historic Tour")
- [ ] Tour mode banner: persistent header indicator when in `HistoricTour` mode ("Historic Tour: Chapter 3 — The Examinations")
- [ ] Chapter picker: scrollable list of chapters, each with thumbnail + estimated time + "Start Chapter" button
- [ ] In-tour HUD: current chapter, current content item, queue depth, "Pause" + "Skip" + "Exit Tour" buttons
- [ ] Map visual change: only chapter POIs visible (others greyed/hidden), chapter route polyline drawn on map
- [ ] Exit tour confirmation modal ("Exit chapter? Your progress will be saved.")

### Step 9R.7: Walk simulator end-to-end test
- [ ] Run walk simulator in `HistoricTour` mode through a complete chapter (e.g., Chapter 5)
- [ ] Verify content sequences correctly: chapter intro → newspaper → primary sources → figures → next POI cue
- [ ] Verify ambient suppression: non-chapter POIs do NOT fire narration
- [ ] Verify mode toggle works: switching to free-roam mid-chapter pauses chapter and resumes ambient
- [ ] Verify per-mode category visibility: free-roam categories restored on mode switch, tour categories restored on switch back
- [ ] Verify map visual change works correctly
- [ ] Verify TTS handles long-form newspaper articles without choking
- [ ] Run all 7 chapters end-to-end with walk simulator over multiple sessions
- [ ] Git commit: "Phase 9R: Historic Tour Mode — chapter sequencer, newspaper TTS, per-mode visibility, hybrid model"

### Out of scope for Phase 9R (deferred)
- Pre-rendered audio for newspapers (use on-device TTS for v1; pre-rendered audio is a v1.1 quality upgrade — ~400MB at 64kbps OGG, too big for the v1 APK)
- Custom voice characters for historic tour (Bark TTS witch/warlock voices) — v1 uses standard Android TTS; character voices are Phase 18
- Branching narratives based on user choices — not in scope, the 1692 timeline is linear
- Multiplayer / shared chapter progress with friends — not in scope
- Historic tour mode for non-Salem deployments — Salem-specific feature
- Translation to other languages — Phase 11 ASO work covers internationalization

---

## Phase 9B — Feature Tier Matrix & Gating Infrastructure

**Goal:** Establish the freemium business model in code. Define what each tier unlocks, build the gating system, stub billing. No real billing yet — Phase 10 wires Google Play Billing.

**Target:** 2-3 sessions | **Status:** NOT STARTED | **Added:** Session 78

### Feature Tier Matrix

| Feature | FREE | EXPLORER ($4.99) | PREMIUM ($9.99) | LLM ($49.99/mo) |
|---------|------|-------------------|-----------------|------------------|
| Core map + basic POIs | Y | Y | Y | Y |
| Tour preview (1 tour, 3 stops) | Y | Y | Y | Y |
| Short narration (approach only) | Y | Y | Y | Y |
| Google Ads displayed | Y | Reduced | N | N |
| Transit (MBTA) | N | Y | Y | Y |
| Weather (full NWS + METAR) | N | Y | Y | Y |
| Events calendar | N | Y | Y | Y |
| All tours (full access) | N | Y | Y | Y |
| Walking directions | N | Y | Y | Y |
| Offline maps | N | Y | Y | Y |
| Full narration (long form at stops) | N | N | Y | Y |
| Historical deep content | N | N | Y | Y |
| Custom tour builder | N | N | Y | Y |
| Ambient discovery alerts | N | N | Y | Y |
| Contextual "Did you know?" alerts | N | N | Y | Y |
| Primary source quotes | N | N | Y | Y |
| Salem Village LLM chat | N | N | N | Y |

**Design decision:** FREE tier is deliberately minimal — strong upgrade incentive.

### Step 9B.1: Define Tier & Feature Enums
- [ ] Create `FeatureTier.kt` — Enum `FREE, EXPLORER, PREMIUM, SALEM_LLM` with `includes()` method
- [ ] Create `FeatureMatrix.kt` — Maps each `Feature` enum to its minimum required tier

### Step 9B.2: Create FeatureGate Singleton
- [ ] Create `FeatureGate.kt` — `@Singleton`, `StateFlow<FeatureTier>`, `isUnlocked()`, `requireOrPrompt()` (shows upgrade dialog)

### Step 9B.3: Add Tier to Data Models
- [ ] Add `tier` field to `AuthUser` (default "FREE")
- [ ] Add `min_tier` column to `Tour` and `TourPoi` Room entities (migration needed)

### Step 9B.4: Wire Gate Checks into Features
- [ ] Gate tour starts behind `Feature.FULL_TOURS`
- [ ] Gate transit/weather/walking behind respective features
- [ ] Gate long narration behind `Feature.FULL_NARRATION` (FREE gets short only)
- [ ] Gate ambient mode behind `Feature.AMBIENT_ALERTS`

### Step 9B.5: Stub Billing Manager
- [ ] Create `BillingManager.kt` — Stub `initialize()`, `launchPurchaseFlow()`, `queryPurchases()`. "Coming Soon" toast.

### Step 9B.6: First-Launch Tier Onboarding
- [ ] Create `TierOnboardingDialog.kt` — 4-tier comparison card, "Continue Free" button
- [ ] Show on first launch (flag: `onboarding_shown`)

### Step 9B.7: Debug Tier Override
- [ ] Add `/tier?set=PREMIUM` debug endpoint
- [ ] Add tier override to debug intent handler

### Step 9B.8: Verify
- [ ] App defaults to FREE — tours locked with upgrade prompt
- [ ] Debug `/tier?set=PREMIUM` unlocks everything
- [ ] First launch shows tier comparison dialog
- [ ] Tier persists across restarts

---

## Phase 9C — User Settings & Alert Preferences

**Goal:** User-facing Settings screen accessible from the toolbar. Consolidates alert type toggles, narration preferences, map settings, and notification control.

**Target:** 1-2 sessions | **Status:** NOT STARTED | **Added:** Session 78

### Step 9C.1: Create Settings Activity
- [ ] Create `SettingsActivity.kt` — `@AndroidEntryPoint`, toolbar + back button
- [ ] Create `SettingsFragment.kt` — `PreferenceFragmentCompat` (uses existing `preference-ktx` dependency)
- [ ] Create `activity_settings.xml` — Toolbar + FrameLayout container
- [ ] Register in AndroidManifest.xml

### Step 9C.2: Define Preference XML
- [ ] Create `preferences.xml` with categories:
  - Alert Types: Tour, Ambient discovery, Transit, Weather, Zone, Business (SwitchPreferences)
  - Alert Frequency: ListPreference (High/Normal/Low/Minimal)
  - Narration: Auto-narrate, Speed, Volume
  - Map: Default tile source, Default zoom
  - Notifications: Sound, Vibration, Quiet hours (with time pickers)
  - Account: Current plan, Manage subscription
- [ ] Create `arrays.xml` for ListPreference values

### Step 9C.3: Add Preference Keys
- [ ] Add all new `PREF_*` constants to `MenuPrefs.kt`

### Step 9C.4: Add Settings to Toolbar
- [ ] Create `ic_settings.xml` gear icon
- [ ] Replace About icon with Settings gear on toolbar (move About into Settings)
- [ ] Wire through `MenuEventListener.onSettingsRequested()` → launch SettingsActivity

### Step 9C.5: Wire Settings to Runtime
- [ ] NarrationManager reads speed/volume/auto-narrate from prefs + listens for changes
- [ ] TourEngine checks tour/ambient alert prefs before firing events
- [ ] SalemMainActivityGeofences checks geofence alert pref
- [ ] StatusLineManager adds quiet hours check (suppress audio in time window)

### Step 9C.6: Verify
- [ ] Settings gear on toolbar → opens categorized preference screen
- [ ] Narration speed change takes immediate effect
- [ ] Tour alert toggle suppresses/enables approach narration
- [ ] Quiet hours suppress audio but keep visual alerts
- [ ] All settings persist across restarts

---

## Phase 9D — Contextual Alert System

**Goal:** Transform limited ambient mode into a rich contextual alert engine. Fires for all nearby POIs, historical sites, businesses. Surfaces "Did you know?" facts, figure connections, primary source quotes, timeline events. Tier-gated and user-configurable.

**Target:** 2-3 sessions | **Status:** NOT STARTED | **Added:** Session 78
**Depends on:** Phase 9B (FeatureGate), Phase 9C (alert preferences)

### Step 9D.1: Create ContextualAlertManager
- [ ] Create `AlertModels.kt` — `ContextualAlertType` enum (HISTORICAL_FACT, POI_APPROACH, BUSINESS_PROMO, TIMELINE_EVENT, FIGURE_CONNECTION, PRIMARY_SOURCE) + `ContextualAlert` data class
- [ ] Create `ContextualAlertManager.kt` — `@Singleton`, 150m trigger radius, SharedFlow emission, respects FeatureGate + user settings
- [ ] Create `AlertFrequencyController.kt` — Frequency presets (High=1min, Normal=2min, Low=5min, Minimal=10min), hourly caps, session dedup

### Step 9D.2: Replace Ambient Mode
- [ ] In `TourEngine.onLocationUpdate()`: delegate to `contextualAlertManager.checkPosition()` when idle
- [ ] Deprecate old `checkAmbientProximity()` method

### Step 9D.3: Wire to UI
- [ ] Create `SalemMainActivityAlerts.kt` — Extension file for alert observation + display
- [ ] Insert `CONTEXTUAL_ALERT` priority level in StatusLineManager
- [ ] Expose alerts in TourViewModel
- [ ] Tap status line alert → detail card with full text + "Narrate" button

### Step 9D.4: Add Spatial Queries
- [ ] Add `getFactsNearby(lat, lng, radiusM)` to SalemContentRepository
- [ ] Add `getFiguresNearby(lat, lng, radiusM)` to SalemContentRepository

### Step 9D.5: Merchant Alerts (Tier-Gated)
- [ ] FREE: Business alert with ad card
- [ ] EXPLORER+: Clean info with "Recommended" badge
- [ ] PREMIUM+: Full details, loyalty, discount codes

### Step 9D.6: Verify
- [ ] Near Salem Witch Museum without tour → "Did you know?" alert fires
- [ ] Alerts respect 2-min interval + hourly cap
- [ ] Settings toggles control which alert types fire
- [ ] FREE tier sees ad cards on business alerts
- [ ] Quiet hours suppress audio, keep visual

---

## Phase 10 — Production Readiness & Offline Infrastructure

**Goal:** Make the app production-grade: crash reporting, analytics, offline map tiles, photo assets, emulator verification of Phases 6-9, performance optimization, accessibility, and security hardening. This is the engineering foundation for a paid product.

**Target:** Complete by July 2026.

### Step 10.1: Emulator verification backlog (Phases 6-9)
- [ ] Re-run content pipeline to regenerate `salem_content.db` with 20 calendar events
- [ ] Install on Salem_Tour_API34 emulator (port 5570)
- [ ] Phase 6 verification:
  - [ ] Select and start any pre-defined tour
  - [ ] Route displays correctly (polyline + numbered markers)
  - [ ] Progress tracks through stops (advance/skip)
  - [ ] Add stops from active tour dialog
  - [ ] Progress persists across app restarts (resume prompt)
  - [ ] Custom tour builder generates valid routes
  - [ ] Time-budget tour selects appropriate stops
- [ ] Phase 7 verification:
  - [ ] Simulate GPS walk — geofence triggers fire at correct distances
  - [ ] Short narration plays on approach, long on arrival
  - [ ] Narration controls (pause, resume, skip, speed) work
  - [ ] Ambient mode triggers for non-tour POIs
- [ ] Phase 8 verification:
  - [ ] Walking directions display correctly on map
  - [ ] "Walk Here" works from tour stop detail
  - [ ] Turn-by-turn dialog shows correct instructions
- [ ] Phase 9 verification:
  - [ ] Events display with correct dates and venues
  - [ ] Category filter chips work
  - [ ] Tapping venue navigates to POI on map
  - [ ] "On this date in 1692" shows for matching dates

### Step 10.1b: Pluggable MarkerIconProvider (core architecture)
- [ ] Define `MarkerIconProvider` interface in `:core` module — `dot()`, `labeledDot()`, `forCategory()`, `clusterIcon()`
- [ ] Generic `app` module implements with current colored dots / vector drawable icons (no change)
- [ ] `app-salem` implements with witch-themed circle icons from `assets/poi-circle-icons/`
- [ ] Register provider via Hilt DI — each app module injects its own implementation
- [ ] Core map rendering code calls the interface, never a concrete icon class
- [ ] Each app module brings its own visual identity without affecting others
- [ ] Merchant override: paid businesses can supply custom icon via `custom_icon_asset` field in narration_points DB

### Step 10.2: Firebase Crashlytics & Analytics
- [ ] Create Firebase project for WickedSalemWitchCityTour
- [ ] Add `google-services.json` to `app-salem/`
- [ ] Add Firebase BoM 34.11.0 to `app-salem/build.gradle`:
  - `com.google.firebase:firebase-crashlytics`
  - `com.google.firebase:firebase-analytics`
- [ ] Add Crashlytics Gradle plugin to root `build.gradle`
- [ ] Initialize in `WickedSalemApp.kt` (auto-init via ContentProvider)
- [ ] Custom keys for crash context:
  - `current_tour_id`, `tour_phase`, `user_tier`, `current_poi_id`, `location_accuracy_m`
- [ ] Non-fatal exception logging on: OSRM failures, content DB errors, API timeouts
- [ ] Custom log breadcrumbs for narration state, geofence events
- [ ] Analytics events to track:
  - `tour_started` (tour_id, tour_name, user_tier)
  - `tour_completed` (tour_id, duration_minutes, stops_visited)
  - `tour_abandoned` (tour_id, stop_index, reason)
  - `poi_viewed` (poi_id, poi_name, tour_id)
  - `narration_played` / `narration_skipped` (poi_id, duration)
  - `walking_directions_requested` (from_poi, to_poi, distance)
  - `events_calendar_opened`
- [ ] User properties: `user_tier`, `device_language`, `app_version`
- [ ] GDPR consent flow: disable analytics collection until user consents
  - Show consent dialog on first launch (not blocking — default to disabled)
  - `Firebase.analytics.setAnalyticsCollectionEnabled(userConsented)`
- [ ] Force test crash button in debug menu → verify in Firebase Console
- [ ] Google Play Data Safety section documentation prepared

### Step 10.3: Offline map tiles (Salem area)
- [ ] Use MOBAC (Mobile Atlas Creator) to generate Salem, MA tiles:
  - Bounding box: lat 42.50-42.54, lng -70.91 to -70.87
  - Zoom levels 12-15 bundled in APK (~60 MB SQLite archive)
  - Tile source: OpenStreetMap Mapnik (ODbL license)
- [ ] Export as `.sqlite` format for osmdroid
- [ ] Place archive in `app-salem/src/main/assets/osmdroid/tiles/`
- [ ] Configure `MapTileProviderArray` for offline-first:
  - Priority 1: Asset-bundled SQLite tiles (zoom 12-15)
  - Priority 2: Filesystem cache (previously downloaded tiles)
  - Priority 3: Online downloader (fallback for zoom 16-18)
- [ ] Add `CacheManager` UI for optional runtime download of zoom 16-18:
  - "Download High-Detail Maps" button in settings
  - `CacheManager.downloadAreaAsync()` with progress callback
  - Estimated additional storage: ~300 MB for zoom 16-18
- [ ] CartoDB Dark Matter tiles for dark mode: bundle same zoom range
- [ ] Attribution: "© OpenStreetMap contributors" prominently on map (not hidden)
- [ ] Online/offline status indicator in status bar
- [ ] Walking directions: cache recent OSRM routes, fallback to straight-line with haversine distance

### Step 10.4: Photo sourcing & integration (digital phase — no field trip needed)
- [ ] Download CC0/CC-BY photos from Wikimedia Commons for Tier 1 POIs (14 sites):
  - Witch Trials Memorial, Salem Witch Museum, Witch House (31 files on Commons), House of Seven Gables (51 files), Salem Maritime NHS, Custom House, Derby Wharf, Derby Wharf Light, Peabody Essex Museum, Roger Conant Statue, Charter Street Cemetery, Rebecca Nurse Homestead, Ropes Mansion, Salem Common
- [ ] Download public domain photos from NPS Media Library (npgallery.nps.gov):
  - Custom House (14 HABS photos), Derby Wharf, Narbonne House, NPS Visitor Center
- [ ] Download HABS photos from Library of Congress (loc.gov):
  - Custom House & Public Stores (14 photos + 16 measured drawings)
  - House of the Seven Gables, Hamilton Hall
- [ ] Convert all to WebP at 1920x1280 (full) + 480x320 (thumbnail)
- [ ] Name convention: `{poi_id}_hero.webp`, `{poi_id}_hero_thumb.webp`
- [ ] Place in `app-salem/src/main/assets/photos/`
- [ ] Add `PoiPhoto` Room entity:
  - Fields: id, poi_id (FK), asset_path, photo_type (hero/detail/context/approach), caption, attribution_text, license, source_url, photographer, sort_order, width_px, height_px
- [ ] Add `PoiPhotoDao` with `findByPoi()`, `findHeroByPoi()`
- [ ] Add Coil 2.7.0 dependency for image loading:
  - `implementation("io.coil-kt:coil:2.7.0")`
- [ ] Integrate hero photo into POI detail dialog
- [ ] Add "Image Credits" section in POI detail (small text below photo)
- [ ] Add "Photo Credits" screen accessible from About dialog
- [ ] Update content pipeline to populate `poi_photos` table
- [ ] Populate `imageAsset` field in `SalemPois.kt` (currently null for all POIs)
- [ ] **License tracking**: CC-BY-SA images used unmodified only, CC-NC images excluded entirely
- [ ] Target: 1 hero photo per POI for v1.0 (~7.4 MB total WebP)

### Step 10.5: Performance optimization
- [ ] Lazy-load narration scripts (don't preload all at startup)
- [ ] Marker clustering for dense POI areas (Essex Street) using osmdroid-geopackage or custom clustering
- [ ] Background GPS: foreground service with persistent notification for active tours
- [ ] Battery optimization: reduce GPS frequency when user is stationary (>30 sec same position)
- [ ] `minifyEnabled true` + `shrinkResources true` for release builds
- [ ] ProGuard/R8 rules for Room, Gson, OkHttp, osmdroid, Firebase, Coil

### Step 10.6: Accessibility
- [ ] Content descriptions on all map markers (POI name + category)
- [ ] TalkBack compatibility testing for all dialogs
- [ ] High-contrast mode option in settings
- [ ] Wheelchair accessibility flags on POIs (field-verified in Phase 13)
- [ ] Large text support (sp units throughout, no hardcoded dp for text)

### Step 10.7: Network security hardening
- [ ] Remove `android:usesCleartextTraffic="true"` from AndroidManifest.xml
- [ ] Add `network_security_config.xml` allowing cleartext only for local dev (10.0.0.x)
- [ ] Move MBTA API key to `local.properties` → BuildConfig injection
- [ ] Move Windy API key to environment variable on server
- [ ] Add `secrets-gradle-plugin` for Android key management
- [ ] Add `POST_NOTIFICATIONS` permission for Android 13+ proximity alerts

### Step 10.8: Database hardening (from audit recommendations)
- [ ] Add `@Index` to all Room entities (lat/lng, category, FK columns, data_source)
- [ ] Add `@ForeignKey` constraints with CASCADE DELETE
- [ ] Replace `fallbackToDestructiveMigration` with explicit `Migration(2, 3)` classes
- [ ] Set `exportSchema = true` for migration validation
- [ ] Add `@Delete`/`@Update` to all DAOs (currently INSERT + SELECT only)
- [ ] Add `@Transaction` on batch insert operations

### Step 10.9: Verify
- [ ] `./gradlew :app-salem:assembleDebug` builds clean
- [ ] `./gradlew :app-salem:assembleRelease` builds clean (with ProGuard)
- [ ] Offline mode works (airplane mode after initial load): map tiles display, content queries work, tours function
- [ ] Firebase Crashlytics dashboard shows test crash
- [ ] Firebase Analytics shows custom events in real-time view
- [ ] All Phase 6-9 emulator checks pass
- [ ] Git commit: "Phase 10: Production readiness — Firebase, offline tiles, photos, hardening"

---

## Phase 11 — Branding, ASO & Play Store Launch

**Goal:** Final branding polish, App Store Optimization, and Google Play Store publication. This is the launch gate.

**Target:** Complete by August 2026. App live on Play Store by September 1, 2026.

### Step 11.1: App icon & splash screen
- [ ] Design app icon (Salem themed: witch silhouette, crescent moon, vintage map element)
  - Generate adaptive icon set (foreground + background layers)
  - Replace placeholder gold "W" on purple
- [ ] Splash screen with Salem imagery (use Android 12+ SplashScreen API)
- [ ] Consistent typography and color scheme verification throughout all screens
- [ ] About screen: credits, historical source citations, photo credits link, version, contact

### Step 11.2: App Store Optimization (ASO)
- [ ] **Title** (30 chars): `Wicked Salem Witch City Tour`
- [ ] **Short description** (80 chars): `GPS-guided walking tours of Salem's 1692 Witch Trials with audio narration`
- [ ] **Full description** (4,000 chars): keyword-rich, feature-complete (see Social Media section for full text)
  - Primary keywords: Salem walking tour, Salem witch trials tour, Salem MA tour guide, GPS walking tour
  - Secondary: haunted Salem, Salem ghost tour, self-guided tour Salem, Salem history
  - Long-tail: Salem witch trials walking tour app, things to do in Salem MA
- [ ] **8 screenshots** (1080x1920 minimum):
  1. Hero: map with POI markers + "Your GPS Guide to Salem's History"
  2. Tour selection: all 8 tours with themes/durations
  3. Narration: POI detail with narration controls + witch trial text
  4. Walking directions: turn-by-turn route between POIs
  5. Historical figure: biography card (Bridget Bishop or Giles Corey)
  6. Primary source: court record excerpt with dramatic typography
  7. Geofence trigger: "Approaching: Witch Trials Memorial" notification
  8. Events: Haunted Happenings schedule view
- [ ] **30-second promo video** (screen recording + Salem footage):
  - 0-5s: Salem establishing shot + logo
  - 5-12s: Tour selection, user taps "Witch Trial Trail"
  - 12-20s: Walking POV with geofence trigger + narration
  - 20-25s: Historical facts, court records, POI map montage
  - 25-30s: "Download Free on Google Play" + feature highlights
- [ ] Seasonal screenshot rotation plan: swap screenshots 1+8 for October content
- [ ] **Localization** (store listing only for v1.0, app content later):
  - English (primary), Spanish, French, German

### Step 11.3: Google Play listing & policies
- [ ] Google Play Developer account ($25 one-time fee)
- [ ] Configure tiered pricing via Google Play Billing Library:
  - Free tier (default)
  - Explorer $4.99 (one-time IAP)
  - Premium $9.99 (one-time IAP)
  - Salem Village LLM $49.99/mo (subscription — implement when Phase 19 ready)
- [ ] `FeatureGate.kt` in `:app-salem` — check tier via Play Billing, gate features accordingly
- [ ] Category: Travel & Local
- [ ] Content rating: Everyone
- [ ] **Privacy policy** (required for paid + analytics apps):
  - Host on landing page website
  - Disclose: Firebase Crashlytics, Firebase Analytics, GPS location usage
  - GDPR compliance section for EU visitors
  - Link accessible from About screen in-app
- [ ] **Data Safety section**: complete Google Play questionnaire accurately
  - Data collected: crash logs, usage analytics, approximate location
  - Data shared with: Google (Firebase infrastructure)
  - Security: encryption in transit

### Step 11.4: In-app review prompt
- [ ] Implement Android in-app review API (`com.google.android.play.core.review`)
- [ ] Trigger AFTER user completes their first tour (experienced core value)
- [ ] Never prompt on first launch
- [ ] Rate limit: once per 30 days
- [ ] Target: 50+ reviews with 4.5+ average within first 3 months

### Step 11.5: Generate signed AAB & launch
- [ ] Generate signing key (store securely outside repo)
- [ ] Build signed AAB: `./gradlew :app-salem:bundleRelease`
- [ ] Upload to Google Play Console
- [ ] Set up staged rollout (10% → 50% → 100%)
- [ ] Monitor Firebase Crashlytics for launch-day crashes
- [ ] Git commit: "v1.0.0 — WickedSalemWitchCityTour Play Store launch"

### Step 11.6: Full regression verification
- [ ] Complete tour walkthrough (simulated GPS): Witch Trial Trail start-to-finish
- [ ] All narration triggers correctly
- [ ] Walking directions display and update
- [ ] Business search finds restaurants, bars, shops
- [ ] Events calendar shows current events
- [ ] MBTA transit works (Salem Station trains)
- [ ] Weather displays for Salem
- [ ] Offline mode works (airplane mode after initial load)
- [ ] Photos display for all POIs with digital assets
- [ ] Firebase analytics events fire correctly
- [ ] App installs from signed AAB on clean device

---

## Phase 12 — Social Media & Digital Presence

**Goal:** Build audience and brand presence BEFORE app launch. Start organic content marketing, establish social accounts, build email list, create landing page. **This phase requires NO CODE and can start immediately (April 2026).**

**Target:** Start NOW. Landing page live by July. 1,000 email subscribers by September launch.

### Step 12.1: Register social media accounts (THIS WEEK)
- [ ] Instagram: @WickedSalemApp (or @WickedSalemTour)
- [ ] TikTok: @WickedSalemApp
- [ ] Facebook Page: "Wicked Salem Witch City Tour"
- [ ] Twitter/X: @WickedSalemApp
- [ ] YouTube: "Wicked Salem Witch City Tour"
- [ ] Consistent branding across all: Salem-themed profile photo, purple/gold color scheme, consistent bio

### Step 12.2: Domain & landing page
- [ ] Register domain (priority order): wickedsalemtour.com, wickedsalem.app, wickedsalemguide.com
- [ ] Also register: wickedsalem.travel (signals category)
- [ ] Build single-page landing site (can use the existing React web app infrastructure or a simple static page):
  - Hero: app screenshots + "Your GPS Guide to Salem's History"
  - Feature highlights: 500 facts, 8 tours, GPS narration, offline mode
  - "Coming September 2026 to Google Play" → email signup
  - Tour preview section (all 8 tours with descriptions)
  - Historical content teaser (drives SEO)
  - Download links (add when live)
  - Blog section (for SEO content)
  - "For Businesses" page (merchant partnership info)
- [ ] SSL certificate (Let's Encrypt free)
- [ ] Google Analytics on website
- [ ] Google Search Console setup

### Step 12.3: Email list building
- [ ] Email service: Mailchimp free tier (up to 500 contacts) or ConvertKit
- [ ] Lead magnet: "10 Salem Witch Trial Facts Most People Don't Know" (PDF)
- [ ] Signup form on landing page (popup + inline)
- [ ] Welcome email → weekly "Fact of the Week" from 500 facts database
- [ ] Launch announcement sequence when app goes live
- [ ] Seasonal content emails (October Haunted Happenings guide)

### Step 12.4: "This Day in 1692" content series (START IMMEDIATELY)
- [ ] Create 30+ pre-designed quote graphics using the 500 facts + 80 timeline events
- [ ] Parchment/sepia aesthetic, consistent branded template
- [ ] Post daily on Instagram + TikTok + Twitter
- [ ] Schedule with Buffer, Later, or Hootsuite (free tier)
- [ ] Cross-reference timeline_events by date for accuracy

### Step 12.5: Content calendar execution

**Instagram (target: 5x/week feed, daily Stories, 3-4 Reels/week):**
- Content pillars (rotate weekly):
  1. "This Day in 1692" — daily historical fact
  2. "Then vs. Now" — historical illustration vs modern photo
  3. "Primary Source Spotlight" — court record excerpts
  4. "Meet the Accused" — mini-biography cards for 49 figures
  5. "App in Action" — screenshots, features, development updates
  6. "Salem Today" — current photos, UGC reposts
- Hashtag tiers: #SalemMA #SalemWitchTrials #WickedSalem #1692 #WitchCity + branded #WickedSalemApp
- Stories: polls, quizzes, behind-the-scenes, countdown stickers

**TikTok (target: 5-7 videos/week, daily in October):**
- "Did You Know?" historical facts (30-60 sec)
- Dramatic court record readings from 200 primary sources
- Walking tour POV previews with app on-screen
- "Salem Myth Busters" quick-hit format
- "POI Reveal" — stand at location, reveal hidden history
- October blitz: daily Haunted Happenings coverage
- Key hashtags: #WitchTok (20.7B views) #SalemWitchTrials #DarkHistory #HistoryTok

**Facebook (target: 3-4x/week):**
- Longer-form historical stories
- Event creation for launch day, Haunted Happenings
- Engage Salem community groups
- Share blog content from landing page

**YouTube (target: 2 videos/month pre-launch, 4/month at launch):**
- Full tour preview walkthroughs (10-20 min)
- Historical deep dives: "The Real Story of Giles Corey" etc.
- App demo/tutorial (3-5 min)
- "Planning Your Salem Trip" guides (high SEO value)
- October vlogs from on-the-ground in Salem

### Step 12.6: Press kit
- [ ] App description (150-word and 500-word versions)
- [ ] Fact sheet: 500 facts, 49 figures, 200 sources, 37 POIs, 8 tours
- [ ] High-res screenshots (all 8 from Play Store listing)
- [ ] App icon (512x512, 1024x1024)
- [ ] Feature graphic (1024x500)
- [ ] 30-second demo video (MP4, downloadable)
- [ ] Founder story: "Solo developer builds most comprehensive Salem walking tour app"
- [ ] 5 key differentiators vs. competitors
- [ ] Contact info + social handles
- [ ] Brand guidelines: logo usage, color palette (#2D1B4E purple, #C9A84C gold)
- [ ] Host as downloadable ZIP on website

### Step 12.7: SEO blog content (ongoing)
- [ ] "10 Salem Witch Trial Facts Most People Get Wrong"
- [ ] "The Complete Salem Witch Trials Timeline: January to October 1692"
- [ ] "Who Were the 20 People Executed in the Salem Witch Trials?" (series: 1 post per person)
- [ ] "Salem Walking Tour: The Witch Trial Trail Step by Step"
- [ ] "How to Plan a Day Trip to Salem from Boston (2026 Guide)"
- [ ] "Salem Haunted Happenings 2026: Complete Guide"
- [ ] "The Real Execution Site: How Proctor's Ledge Was Discovered"
- [ ] "Best Restaurants in Salem MA (2026)"
- [ ] "Taking the Ferry from Boston to Salem"
- [ ] E-E-A-T signals: cite scholarly sources, link to NPS/PEM, real contact info

---

## Phase 13 — Fieldwork & Content Photography

**Goal:** On-the-ground Salem field trip to photograph all POIs, verify GPS coordinates, check business listings, and assess wheelchair accessibility. **This phase requires NO CODE — it's physical fieldwork.**

**Target:** Late September 2026 (ideal timing: good light, foliage starting, all seasonal sites open, before peak October crowds).

### Step 13.1: Pre-trip digital download session (do before field trip)
- [ ] Download Wikimedia Commons CC0/CC-BY photos (Tier 1 POIs, Step 10.4)
- [ ] Download NPS + LOC public domain photos
- [ ] Identify gaps: which POIs still need field photography

### Step 13.2: Salem field trip — Day 1 (Downtown core, ~20 POIs)

**Equipment:**
- Phone with GPS enabled (Pixel 8a or equivalent), charged + backup battery
- Compact tripod (for blue hour shots)
- Clip-on polarizing filter (reduces glare on glass/stone)

**Shot list per POI (5 shots each):**
1. Hero shot: full exterior, best angle, good light, minimal clutter
2. Detail shot: plaque, signage, inscription, architectural detail
3. Context shot: street-level view showing POI in neighborhood
4. Approach shot: what user sees walking toward it (for geofence trigger moment)
5. Atmospheric shot: moody/unique angle for variety

**Day 1 itinerary:**
- [ ] 7:00 AM (golden hour): Derby Wharf, Custom House, Salem Maritime NHS (east-facing, morning light)
- [ ] 8:30 AM: House of Seven Gables, Hawthorne's Birthplace, Pickering Wharf
- [ ] 10:00 AM: Narbonne House, Derby Wharf Light Station
- [ ] 11:00 AM (flat light — good for): Charter Street Cemetery, Witch Trials Memorial, Salem Jail site
- [ ] 12:30 PM: Court House site, Judge Hathorne's home site, Sheriff Corwin's home site
- [ ] 2:00 PM: Peabody Essex Museum, NPS Visitor Center, Lappin Park
- [ ] 3:30 PM (afternoon light): Essex Street witch shops (Crow Haven Corner, Hex, Omen, Artemisia, Coven's Cottage)
- [ ] 4:30 PM: Witch House (west-facing), Hawthorne Statue, Old Town Hall
- [ ] 5:30 PM: Roger Conant Statue, Salem Common
- [ ] Blue hour (6:00 PM): Hawthorne Hotel, Salem Witch Museum (illuminated exteriors)

### Step 13.3: Salem field trip — Day 2 (Outer sites + fill-ins)
- [ ] 8:00 AM: Winter Island Park, Fort Pickering Light
- [ ] 9:30 AM: Salem Willows Park
- [ ] 10:30 AM: Salem MBTA Station, Salem Ferry Terminal
- [ ] 11:30 AM: Proctor's Ledge Memorial (7 Pope St — confirmed execution site)
- [ ] 12:30 PM: Pioneer Village (98 West Ave — seasonal, verify open)
- [ ] 2:00 PM: Chestnut Street, McIntire Historic District walking loop
- [ ] 3:00 PM: 14 Mall Street, "Castle Dismal" (10.5 Herbert St) — photograph from public way only
- [ ] 4:00 PM: Witch Dungeon Museum, New England Pirate Museum (exteriors)
- [ ] 5:00 PM: Return to any POIs that need better light
- [ ] Optional: Rebecca Nurse Homestead (149 Pine St, Danvers — 30-min drive)

### Step 13.4: Field verification tasks (during photography trip)
- [ ] Verify GPS coordinates for all 37 POIs (stand at each, record phone GPS, compare to database)
- [ ] Check wheelchair accessibility at each POI (note: curb cuts, ramps, terrain, door width)
- [ ] Verify business listings: hours posted, seasonal closures, any businesses permanently closed
- [ ] Walk the Heritage Trail red line — verify it matches Tour 5 stop order
- [ ] Note any new POIs not in database (new shops, restaurants, memorials)
- [ ] Photograph restaurant menus/hours boards for 13 curated dining listings
- [ ] Test MBTA commuter rail from Boston → Salem (verify station info accuracy)

### Step 13.5: Post-trip photo processing
- [ ] Extract EXIF GPS coordinates: `exiftool -csv -GPSLatitude -GPSLongitude *.jpg > coords.csv`
- [ ] Verify EXIF GPS matches POI database coordinates (within geofence radius)
- [ ] Convert to WebP: `cwebp -metadata exif -q 82 input.jpg -o output.webp`
- [ ] Generate thumbnails: resize to 480x320 with `cwebp -resize 480 320`
- [ ] Select best hero + 2-4 detail photos per POI
- [ ] Name: `{poi_id}_{type}.webp` (hero, detail1, detail2, context, approach)
- [ ] Update `poi_photos` table in content pipeline
- [ ] Rebuild `salem_content.db` with photo metadata
- [ ] Replace/supplement digital-only photos from Step 10.4 with field photos

### Step 13.6: Legal notes
- Photographing building exteriors from public sidewalks is legal in Massachusetts
- Private residences (Castle Dismal, 14 Mall St): photograph from public way only, do NOT enter property
- NPS sites: photography permitted outdoors; ask at visitor center about interior policies
- Private businesses (witch shops): ask permission for interiors; storefronts from sidewalk are legal
- Cemeteries: photography permitted, be respectful, no flash on headstones

---

## Phase 14 — Community Engagement & Salem Partnerships

**Goal:** Establish the app as part of Salem's tourism ecosystem through Chamber membership, Destination Salem partnership, NPS collaboration, Salem 400+ involvement, and local media outreach. **This phase requires NO CODE — it's business development and relationship building.**

**Target:** Start immediately (April 2026). Chamber membership by May. Destination Salem contact by June. Salem 400+ involvement ongoing through 2026.

### Step 14.1: Salem Chamber of Commerce membership (FIRST PRIORITY)
- [ ] Apply: salem-chamber.org/member/newmemberapp or call 978-744-0004
- [ ] Tier: Regular Member ($360/year) — unlocks:
  - Business Directory listing (print + online)
  - Website listing with hyperlink
  - Weekly e-newsletter exposure (~2,000 readers)
  - Consumer and B2B referrals (hundreds monthly)
  - Networking events: Coffee Connections, After Hours
  - Government representation
  - **Access to Haunted Happenings Grand Parade sponsorship**
- [ ] Attend first Coffee Connections networking event
- [ ] Inquire about Banner Sponsorship for Haunted Happenings 2026

### Step 14.2: Destination Salem outreach
- [ ] Contact: **Bridie O'Connell**, Advertising & Tourism Manager
  - Email: boconnell@salem.org | Phone: 978-498-4147
- [ ] Request media kit and advertising rate card
- [ ] Explore: salem.org online listing, digital advertising
- [ ] 2027 Visitor Guide advertising (2026 edition already produced)
- [ ] Position app as **complementary** to Destination Salem's logistics app (theirs = parking/events, ours = guided tours/history)
- [ ] Offer co-promotion: link to salem.org from app, they recommend app on website

### Step 14.3: NPS Salem Maritime partnership
- [ ] Contact: VECE Division (Visitor Experience & Community Engagement), 978-740-1650
- [ ] Address: 160 Derby Street, Salem, MA 01970
- [ ] Pitch: app includes NPS-accurate historical content about Salem Maritime; drives visitors to NPS sites
- [ ] Ask: recommendation at visitor center, featured app rack card
- [ ] Offer: proper NPS attribution in all maritime content, link to NPS ranger programs
- [ ] Explore: Eastern National cooperating association (operates NPS bookstores) — feature or sell app

### Step 14.4: Salem 400+ quadricentennial (2026 — BIGGEST OPPORTUNITY)
- [ ] Contact: salem400@salem.com, salem400.org
- [ ] Volunteer: salemvolunteers.org
- [ ] Follow: @400salem on Facebook and Instagram
- [ ] Submit proposal for **Old Town Hall programming** (city issued call for proposals)
- [ ] Position app as a Salem 400+ resource
- [ ] Add Salem 400+ content to app (founding history, Roger Conant, 1626 settlement)
- [ ] Attend Salem 400+ events: Food at 400+ (June 11-22), Heritage Days Parade, Sister City events
- [ ] Sponsor or volunteer at a Salem 400+ activity for visibility

### Step 14.5: Hotel concierge programs
- [ ] **Hawthorne Hotel** (hawthornehotel.com) — Salem's landmark historic hotel
  - Provide QR code display stand for front desk
  - Offer "Hotel Partner" program: guests get bonus content or small discount
- [ ] **Salem Waterfront Hotel** (Pickering Wharf) — tourist convenience location
- [ ] **Hotel Salem** — boutique downtown hotel
- [ ] Print QR code cards (100 per hotel) with app download link + partner code
- [ ] Follow up monthly to refresh cards and build relationship

### Step 14.6: Salem Cultural Council grant
- [ ] Contact: SalemCulturalCouncil@salem.com, Julie Barry (Senior Planner — Arts & Culture)
- [ ] Address: 98 Washington Street, City Hall Annex, 2nd Floor
- [ ] Grant cycle: opens September 2026, deadline October 2026, awards January 2027
- [ ] Frame app as cultural/historical education project: "GPS-guided access to Salem's 1692 history"
- [ ] Meets 1st Monday of each month, 7:30 PM — attend to introduce project
- [ ] Grant funds could offset content development, narration recording, or marketing costs

### Step 14.7: Local government engagement
- [ ] **Mayor Dominick Pangallo** — office at City Hall, 978-745-9595, 93 Washington St
- [ ] Attend City Council meetings regularly (posted at salemma.gov/AgendaCenter)
  - Introduce project during public comment period
  - Express support for tourism initiatives
- [ ] Explore appointment to a city board (Salem has ~40 boards, 200+ volunteers):
  - Salem Cultural Council, Historical Commission, or tourism-adjacent board
  - Mayor's Office publishes weekly vacancy listings
- [ ] Support Salem Main Streets initiatives (260+ downtown businesses)

### Step 14.8: Media & PR outreach
- [ ] **The Salem News** (salemnews.com) — pitch: "Local tech innovation in Salem tourism for Salem 400+"
- [ ] **Northshore Magazine** (nshoremag.com) — pitch: lifestyle angle, "new way to experience Salem"
- [ ] **Salem The Podcast** — hosts Jeffrey Lilley (Salem Uncovered Tours) + Sarah Black (Bewitched Historical Tours)
  - Pitch guest appearance to discuss app + Salem history
  - These are tour guides themselves — ideal audience
- [ ] **Streets of Salem Blog** (streetsofsalem.com) — Donna A. Seger, history professor
  - Potential content consultant / historical accuracy advisor
  - Her book "Salem's Centuries" coming 2026 from Temple University Press
- [ ] **The Thing About Salem** podcast — hosts Josh Hutchinson + Sarah Jack (witch trial descendants)
- [ ] Send press kit to all media contacts when app launches

### Step 14.9: Influencer partnerships
- [ ] Target 10-15 micro-influencers (1K-50K followers) across: travel, history, spooky/paranormal, local Salem
- [ ] Offer: free lifetime Premium access ($9.99 value) + unique referral code
- [ ] Ask: 1 Instagram Reel or TikTok video using app on location in Salem + 1 Story set
- [ ] Track referral code downloads to measure ROI
- [ ] Priority targets: @destsalem (64K), local Salem lifestyle accounts, NE travel bloggers
- [ ] 25-30 partnerships active by October peak season

### Step 14.10: Cross-promotion partnerships
- [ ] **Salem Ferry** (Boston Harbor City Cruises): QR codes on vessel/terminal, co-marketing
  - Season: mid-May through Halloween
  - Every passenger is a tourist arriving in Salem
- [ ] **Salem Trolley**: complement — trolley gives overview, app gives deep dive
  - Operates June-September, 7 days/week, $18 (free for Salem residents)
- [ ] **Salem Food Tours** (salemfoodtours.com): cross-promotion for dining content
- [ ] **Ghost tour operators**: include their schedules/booking links in app (referral revenue)
  - Salem Historical Tours, Witch City Walking Tours, Spellbound Tours
  - They are competitors AND partners — app is self-paced 24/7, they are scheduled group tours
- [ ] **TripAdvisor / Viator**: list app as a tour experience (105 Salem tours already listed)
- [ ] **MBTA**: station advertising at Salem station
- [ ] **SBDC at Salem State University**: free business assistance (marketing, financial planning)
  - Contact: 121 Loring Avenue, Suite 310, Salem, MA 01970, 978-542-6343

### Step 14.11: Offer app free to Salem residents
- [ ] "Salem Resident" promo code — free Premium tier for locals
- [ ] Builds goodwill and word-of-mouth (Salem Trolley already does free-for-residents)
- [ ] Locals become ambassadors who recommend app to visiting friends/family

---

## Phase 15 — In-App Virality & Gamification

**Goal:** Build sharing mechanics and gamification that turn every user into a marketing channel. Each completed tour, visited POI, and shared fact is organic advertising.

**Target:** v1.1 update, October 2026 (in time for peak season).

### Step 15.1: Tour completion shareable cards
- [ ] On tour completion, generate branded graphic card:
  - "I walked the Witch Trial Trail — 12 stops, 2.5 km, 90 minutes of Salem history"
  - Tour route map thumbnail, date, app branding, user display name
  - Purple/gold Salem aesthetic
- [ ] One-tap share to Instagram Stories, TikTok, Facebook, Twitter
- [ ] Pre-populate hashtags: #WickedSalemApp #WitchTrialTrail #SalemMA
- [ ] `Bitmap` generation in `SalemMainActivityTour.kt` using Canvas
- [ ] Share via Android `Intent.ACTION_SEND` with `FileProvider` URI

### Step 15.2: POI progress tracker & badges
- [ ] "I've visited X of 37 Salem POIs" with visual progress bar on home screen
- [ ] Badge system (stored in SharedPreferences, synced to analytics):

| Badge | Requirement | Rarity |
|-------|-------------|--------|
| First Steps | Complete any tour | Common |
| Witch Trial Witness | Complete Witch Trial Trail | Common |
| Maritime Explorer | Complete Maritime Heritage | Common |
| Literary Pilgrim | Complete Hawthorne's Salem | Common |
| Architecture Buff | Complete Architecture Walk | Common |
| Heritage Walker | Complete Heritage Trail | Common |
| Foodie | Complete Food & Drink Trail | Uncommon |
| Salem Master | Complete ALL tours | Rare |
| History Scholar | Read 100 historical facts | Uncommon |
| Primary Source Researcher | Read 50 court records | Uncommon |
| Salem Explorer Bronze | Visit 10 POIs | Common |
| Salem Explorer Silver | Visit 20 POIs | Uncommon |
| Salem Explorer Gold | Visit all 37 POIs | Rare |
| October Veteran | Use app during Haunted Happenings | Seasonal |
| Early Adopter | Download in first month | Limited |
| Social Butterfly | Share 5 facts to social media | Uncommon |

- [ ] Milestone shareable cards at 10, 20, 30, 37 POIs
- [ ] Badge display in user profile screen

### Step 15.3: "Share This Fact" button
- [ ] Every historical fact (500), figure bio (49), and primary source (200) gets a share button
- [ ] Generates branded graphic card with the content + app attribution
- [ ] "Shared from Wicked Salem Witch City Tour — Download the app"
- [ ] Share via Android share intent
- [ ] Track share count per item in analytics

### Step 15.4: QR code referral system
- [ ] Each user gets unique referral code (generated at registration or first launch)
- [ ] QR code display in app with "Share with a friend" prompt
- [ ] Referrer gets: unlock one premium tour free
- [ ] Referee gets: same benefit
- [ ] Track referral conversions in Firebase Analytics
- [ ] Cap: 10 referrals per user
- [ ] Print physical QR code stickers to leave at partner businesses

### Step 15.5: Push notification system
- [ ] Request permission AFTER user completes first tour (not first launch)
- [ ] Notification types:
  - Daily Historical Fact (9:00 AM, opt-in): pull from 500 facts by date
  - Tour Reminder (24hrs after last session): "Ready to explore?"
  - Geofence Welcome (entering Salem city limits): "Welcome to Salem!"
  - Event Alert (1 day before Haunted Happenings event)
  - Achievement (on badge unlock)
  - Seasonal (new content available)
- [ ] Use Firebase Cloud Messaging (FCM) for delivery
- [ ] Respect user notification preferences (settings screen with toggles per type)

### Step 15.6: Photo spot prompts
- [ ] At designated POIs, show prompt: "Take a photo at the Witch Trials Memorial"
- [ ] Provide optimal photo angle/framing suggestion
- [ ] Optional overlay: "Wicked Salem" watermark/frame
- [ ] One-tap share with location tag
- [ ] Priority POIs: Witch Trials Memorial, Roger Conant Statue, Derby Wharf, Seven Gables, Charter Street Cemetery, Proctor's Ledge

---

## Phase 16 — iOS & Web Expansion (PWA)

**Goal:** Capture iOS users (55-60% of US tourists) by enhancing the existing React web app with Salem tour functionality and deploying as a PWA. Fastest path to iOS with zero App Store friction. Validates demand before investing in native iOS.

**Target:** v1.0 PWA by November 2026 (post-Android launch, captures late-season and planning-for-next-year traffic).

### Step 16.1: Add Salem tour route to React web app
- [ ] Create `/salem` route in existing web app (`web/src/`)
- [ ] Tour selection page: list all 8 tours with descriptions, stop counts, durations
- [ ] Tour detail page: map with route polyline + numbered stop markers (Leaflet)
- [ ] Tour progress state: active tour, current stop, completed/skipped stops
- [ ] POI detail cards with photos, narration text, historical content

### Step 16.2: Port TourEngine to TypeScript
- [ ] Translate `TourEngine.kt` logic to TypeScript:
  - Tour lifecycle: start, advance, skip, pause, resume, end
  - Progress tracking: completed stops, elapsed time, distance walked
  - Route optimization: nearest-neighbor TSP (haversine math)
  - Time-budget tour builder
- [ ] Port `TourGeofenceManager` to TypeScript:
  - Haversine-based proximity checks
  - APPROACH/ENTRY/EXIT event detection
  - Cooldown tracking
- [ ] Store tour state in localStorage or IndexedDB

### Step 16.3: Web Speech API narration
- [ ] Implement narration using `window.speechSynthesis` (SpeechSynthesis API)
- [ ] Queue management: replicate NarrationManager segment queue
- [ ] Speed control: `SpeechSynthesisUtterance.rate` (0.75-1.25x)
- [ ] Event listeners: `onstart`, `onend`, `onerror`, `onpause`, `onresume`
- [ ] Note: iOS Safari requires user gesture for first speech; subsequent calls work without gesture
- [ ] Wake Lock API (`navigator.wakeLock`) to prevent screen dimming during tours

### Step 16.4: Offline support via Service Worker
- [ ] Precache Salem map tiles (Leaflet): zoom 14-19 for 1 sq mile core (~2-5 MB)
- [ ] Cache tour/POI data in IndexedDB (841 records, tiny)
- [ ] Service worker with cache-first strategy for tile URLs
- [ ] "Download Salem Map" button for manual precache trigger
- [ ] Cache API for static assets (JS, CSS, images)

### Step 16.5: PWA manifest & installation
- [ ] Create `manifest.json`: name, icons, theme color (#2D1B4E), background color
- [ ] "Add to Home Screen" prompt for iOS Safari (iOS 16.4+ supports PWA install)
- [ ] Splash screen configuration
- [ ] Standalone display mode

### Step 16.6: Payment integration (bypass Apple 30% cut)
- [ ] Stripe Checkout for tier upgrades ($4.99/$9.99)
  - Stripe fee: 2.9% + $0.30 per transaction (vs Apple's 30%)
  - On $9.99 sale: Stripe takes $0.59, Apple would take $3.00
- [ ] Payment link or embedded Stripe checkout
- [ ] Store purchase status in user account (synced to backend)

### Step 16.7: PWA limitations to document for users
- **Background GPS**: NOT supported on iOS Safari — user must keep screen on during tour
- **Push notifications**: Only for home-screen-installed PWAs on iOS 16.4+
- **Audio**: Web Speech API works but less reliable than native TTS
- **GPS accuracy**: Comparable to native when browser tab is active

### Step 16.8: Future native iOS consideration
- If PWA validates iOS demand, evaluate KMP (Kotlin Multiplatform) for native iOS:
  - Shareable: TourEngine, TourGeofenceManager, TourModels, content models (pure Kotlin)
  - Platform-specific: UI (SwiftUI), maps (MapKit), TTS (AVSpeechSynthesizer), GPS (CLLocationManager)
  - Database: SQLDelight replaces Room (cross-platform SQLite)
  - Estimated effort: 6-10 weeks for native iOS app
  - Unlocks: background GPS, App Store presence, premium native feel

---

## Competitive Landscape

### Direct Competitors

| Competitor | Platform | Price | Strengths | Weakness vs. Wicked Salem |
|-----------|----------|-------|-----------|--------------------------|
| **Action Tour Guide (Salem GPS Tour)** | iOS + Android | ~$9.99 | Established, offline audio, multi-language (EN/FR/DE/ES) | Audio-only, no live transit, no events, limited historical depth |
| **Salem On Your Own** | Web/app | Varies | 25+ years of Salem storytelling, family-run | Less historical depth, no primary sources, no geofencing |
| **VoiceMap** | iOS + Android | Per-tour | Professional narration, global platform | Generic platform, not Salem-specialized, no custom tours |
| **GPSmyCity** | iOS + Android | $3.99-7.99/tour | Huge library, offline | Shallow content, no geofence triggers |
| **Viator Self-Guided Tours** | Viator platform | $5-15/tour | Major booking platform | Platform fee, no native features |
| **Destination Salem App** | iOS + Android | Free | Official tourism org, parking/events | NOT a guided tour — complementary, not competitive |

### Our Competitive Advantages
1. **Unmatched content depth:** 500 facts, 49 figures, 200 primary sources (court records, petitions, letters)
2. **8 themed tours** + custom tour builder + time-budget tours (competitors offer 1-2 fixed tours)
3. **GPS geofence narration** that triggers automatically (not "press play at stop 3")
4. **Live MBTA transit** (trains from Boston, local buses) — no competitor offers this
5. **Events calendar** with Haunted Happenings integration
6. **Offline-first architecture** — works without internet after initial download
7. **Scholarly sources** — UVA Salem Witchcraft Papers, actual court records, not Wikipedia summaries
8. **Data provenance** — every fact tracked to source with confidence scores

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

### After Phase 10 (Production Readiness):
- Firebase Crashlytics shows test crash in dashboard
- Firebase Analytics shows custom events in real-time
- Offline map tiles display (airplane mode): zoom 12-15 for Salem area
- Photos display for all POIs with digital assets (Wikimedia/NPS/LOC)
- GDPR consent dialog appears on first launch
- ProGuard/R8 release build succeeds

### After Phase 11 (Play Store Launch):
- App installs from signed AAB on clean device
- Google Play listing looks polished (screenshots, description, video)
- In-app review prompt appears after first tour completion
- Privacy policy accessible from About screen

### End-to-End Acceptance Test:
- Simulate walking Witch Trial Trail: Salem Station → 12 stops → finish
- Narration triggers at every stop
- Walking directions guide between stops
- Can browse restaurants mid-tour
- MBTA transit shows Salem trains
- Weather works for Salem
- Offline mode functional (airplane mode after load): map + content + tours all work
- Events calendar shows current events
- Photos display for every POI
- Firebase analytics events fire for: tour_started, poi_viewed, narration_played, tour_completed
- App size under 80 MB (including offline tiles + photos)

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

### Phase 17 — Merchant Network & Advertising Platform
- [ ] Build merchant admin portal (web-based)
- [ ] Merchant self-service POI creation/editing
- [ ] Geofenced ad delivery system (proximity-triggered cards, not intrusive popups)
- [ ] Loyalty program engine (check-ins, discount code generation, tied to merchant ID + user ID)
- [ ] Analytics dashboard (impressions, foot traffic, redemptions)
- [ ] North Shore merchant data expansion (500+ businesses: Marblehead, Beverly, Danvers, Peabody, Swampscott)
- [ ] Sponsored tour stop system ("This stop brought to you by [Business]")
- [ ] Business-to-app payment integration (Stripe)
- [ ] **Pre-launch merchant pilot (can start at Phase 14):** approach 10-15 Salem businesses, offer "Featured Partner" listing for $99/year — gold badge, mention in tour narrations. No portal needed — just a spreadsheet and manual content updates. Revenue: $1,000-1,500/year from day one.

### Phase 18 — Custom Narration & Audio Upgrade
- [ ] Transition from Android TTS to pre-recorded human narration
- [ ] Options evaluation:
  - Professional voice actors (highest quality, highest cost, ~$200-500/hour)
  - Google Cloud TTS Neural2 voices (high quality, ~$10-50 for all narration scripts)
  - ElevenLabs AI voice synthesis (natural-sounding, $5-22/month for needed volume)
  - Local Salem actors/historians (authentic, supports community, moderate cost)
- [ ] Pre-generate all narration as MP3 files:
  - Short narrations (37 POIs × 15-30 sec) = ~20 minutes total
  - Long narrations (37 POIs × 60-120 sec) = ~60 minutes total
  - Transition narrations (~60 segments × 10-15 sec) = ~12 minutes total
  - Historical figure bios (49 × 30-60 sec) = ~30 minutes total
  - Primary source readings (selected 50 × 30-60 sec) = ~35 minutes total
  - **Total: ~2.5 hours of audio content**
- [ ] Bundle MP3s in app assets or stream on demand
- [ ] Replace `NarrationManager.kt` TTS calls with `MediaPlayer` audio playback
- [ ] Add `MediaStyle` notification controls for narration (deferred from Phase 7)
- [ ] Consider multi-language narration: English (primary), Spanish, French
- [ ] Estimated cost range: $50-500 depending on approach

### Phase 19 — Salem Village LLM Integration
- [ ] `/salem/chat` API endpoint — conversational interface to Salem LLM
- [ ] Character selection (50 historical figures available)
- [ ] Context-aware conversations (figure knows their history, location, relationships)
- [ ] Token metering for $49.99/mo subscription
- [ ] Conversation persistence (chat history)
- [ ] Voice input/output integration with narration system
- [ ] Safety guardrails (historical accuracy, no harmful content)
- [ ] Rate limiting and abuse prevention

### Phase 20 — Additional Revenue Features
- [ ] In-app merchandise (Salem-branded items, print-on-demand)
- [ ] Tour booking integration (partner ghost tours, museum tickets — referral revenue from Phase 14 partnerships)
- [ ] AR historical overlays at photo spots (Phase 15.6 foundation)
- [ ] Seasonal content packs (October Haunted Happenings premium content)
- [ ] Multi-language app content (Spanish, French, German, Japanese, Chinese)
  - Action Tour Guide already offers EN/FR/DE/ES — must match or exceed
- [ ] "What's Near Me" passive browse screen: scrollable list of POIs sorted by distance, with photos and one-line descriptions
- [ ] Emergency/practical info: Salem Police non-emergency, Salem Hospital, pharmacies, public restrooms, water fountains, free wifi spots
- [ ] Drag-and-drop stop reordering in custom tour builder (deferred from Phase 6)
- [ ] Halloween-themed map overlays (deferred from Phase 9)
- [ ] Daily historical notification (deferred from Phase 9)

---

## Social Media Content Calendar

### Seasonal Framework

| Period | Focus | Posting Cadence |
|--------|-------|-----------------|
| **Apr-Jun (pre-launch)** | Build audience, tease app | IG 3x/week, TikTok 3x/week, FB 2x/week |
| **Jul-Aug (hype)** | App previews, beta recruitment, countdown | IG 5x/week, TikTok 5x/week, FB 3x/week |
| **Sep (launch)** | App live, feature spotlights, download push | IG daily, TikTok daily, FB 4x/week, YT 4 videos |
| **Oct (peak)** | Haunted Happenings blitz, user content, max ads | ALL platforms daily, multiple posts/day |
| **Nov-Mar (off-season)** | Historical deep dives, trip planning content | IG 3x/week, TikTok 3x/week, FB 2x/week |

### Content Pillar Rotation (Instagram)

| Day | Pillar | Content |
|-----|--------|---------|
| Monday | "This Day in 1692" | Historical fact tied to calendar from 500 facts + 80 timeline events |
| Tuesday | "Meet the Accused" | Mini-biography card for one of 49 historical figures |
| Wednesday | "Primary Source Spotlight" | Court record excerpt — handwritten-style on dark background |
| Thursday | "App in Action" | Screenshots, features, development updates |
| Friday | "Salem Today" | Current photos, restaurant spotlights, seasonal content |
| Saturday | Reel | "Did You Know?" video, walking tour preview, myth buster |
| Sunday | Stories | Polls, quizzes, behind-the-scenes, user Q&A |

### Paid Advertising Budget

| Period | Monthly Budget | Allocation |
|--------|---------------|------------|
| Apr-Aug (pre-launch) | $0-100 | Social post boosting only |
| September (launch) | $300-500 | 50% Google Ads, 30% IG/FB, 20% TikTok |
| October (peak) | $500 | 40% Google Ads, 30% IG/FB, 30% TikTok |
| Nov-Mar (off-season) | $100-150 | Retargeting only |

**Expected ROI at $500/month peak:** ~225 installs. At 30% paid conversion ($9.99), revenue = ~$670/month. Profitable from month one.

---

## Community Engagement Contacts

| Organization | Contact | Phone/Email | Action |
|-------------|---------|-------------|--------|
| **Destination Salem** | Bridie O'Connell | boconnell@salem.org, 978-498-4147 | Advertising, listing, partnership |
| **Salem Chamber of Commerce** | General | info@salem-chamber.org, 978-744-0004 | Membership ($360/yr), Haunted Happenings |
| **Salem City Hall** | City Recorder | 978-745-9595, 93 Washington St | Council meetings, board appointments |
| **Salem Cultural Council** | Julie Barry | SalemCulturalCouncil@salem.com | Grant application (Sep 2026 cycle) |
| **Salem 400+** | General | salem400@salem.com | Volunteering, programming proposals |
| **NPS Salem Maritime** | VECE Division | 978-740-1650, 160 Derby St | Visitor center recommendation |
| **Salem Witch Museum** | Education Dept | education@salemwitchmuseum.com | Content accuracy, cross-promotion |
| **SBDC at Salem State** | General | 978-542-6343, 121 Loring Ave | Free business assistance |
| **Enterprise Center** | Laura DeToma Swanson | 978-542-7039 | Business development |
| **Salem Food Tours** | General | salemfoodtours.com | Cross-promotion, Food at 400+ |
| **Salem Main Streets** | General | salemmainstreets.org | Downtown business network |
| **The Salem News** | Editorial | salemnews.com/contact_us | Press coverage |
| **Northshore Magazine** | Editorial | nshoremag.com | Feature article |
| **Salem The Podcast** | Jeffrey Lilley + Sarah Black | Apple Podcasts, Spotify | Guest appearance |
| **Streets of Salem Blog** | Donna A. Seger | streetsofsalem.com | Content consulting |
| **North of Boston CVB** | General | northofboston.org | I-95 visitor center feature |
| **MOTT** | Grants | mass.gov/orgs/office-of-travel-and-tourism | DDC Grant (FY27 cycle, May 2027) |

---

## Fieldwork Planning Guide

### Equipment Checklist
- [ ] Phone with GPS enabled (Pixel 8a or equivalent) + backup battery
- [ ] Compact phone tripod (for blue hour/evening shots)
- [ ] Clip-on polarizing filter (reduces glare on glass, water, stone)
- [ ] Notepad for field verification notes (hours, accessibility, closures)
- [ ] Printed POI checklist with GPS coordinates for verification
- [ ] Comfortable walking shoes (2 full days, ~10 miles total)

### Photo Processing Pipeline
1. Transfer photos to development machine
2. Extract EXIF GPS: `exiftool -csv -GPSLatitude -GPSLongitude *.jpg > coords.csv`
3. Verify GPS matches POI database (within geofence radius)
4. Select best shots per POI (1 hero + 2-4 detail)
5. Convert to WebP: `cwebp -metadata exif -q 82 input.jpg -o {poi_id}_{type}.webp`
6. Generate thumbnails: `cwebp -resize 480 320 -q 75 input.jpg -o {poi_id}_{type}_thumb.webp`
7. Copy to `app-salem/src/main/assets/photos/`
8. Update content pipeline `poi_photos` table
9. Rebuild `salem_content.db`

### Image Specifications

| Spec | Full-Size | Thumbnail |
|------|-----------|-----------|
| Format | WebP (lossy) | WebP (lossy) |
| Dimensions | 1920 x 1280 (3:2) | 480 x 320 |
| Quality | 82 | 75 |
| Avg file size | 150-250 KB | 15-30 KB |
| Color space | sRGB | sRGB |

### Storage Budget

| Scenario | Photos | Total Size |
|----------|--------|------------|
| 1 hero/POI (MVP) | 37 + 37 thumbs | ~7.4 MB |
| 3 photos/POI + thumbs | 111 + 37 thumbs | ~23 MB |
| 5 photos/POI + thumbs | 185 + 37 thumbs | ~38 MB |
| Offline map tiles (zoom 12-15) | N/A | ~60 MB |
| **Total app assets (MVP)** | | **~70 MB** |

---

*Document created: 2026-04-03*
*Last updated: 2026-04-04 (Session 76: Full re-evaluation — added Phases 10-16 expanded, social media, community engagement, fieldwork, competitive landscape, iOS/PWA strategy)*
*Project: LocationMapApp v1.5 → WickedSalemWitchCityTour*
*Author: Dean Maurice Ellis*
*Archived: Pre-Session 76 Phase 10 & Future Phases → MASTER_PLAN_ARCHIVE_Phase10_FuturePhases.md*
