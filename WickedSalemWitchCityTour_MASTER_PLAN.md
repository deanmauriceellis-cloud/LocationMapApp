# WickedSalemWitchCityTour — Master Plan

**App Name:** WickedSalemWitchCityTour (product / store name: *Katrina's Mystic Visitor's Guide*)
**Package:** `com.destructiveaigurus.katrinasmysticvisitorsguide`
**V1 commercial posture (locked S138):** $19.99 flat paid, fully offline, no ads, no LLM, IARC Teen.
**Platform:** Android (API 26+, Kotlin, osmdroid)
**Base:** LocationMapApp v1.5 multi-module platform
**Knowledge Base:** ~/Development/Salem (Salem Witch Trials 1692 simulation project) + SalemIntelligence :8089

> **Scope note (2026-04-23):** completed-phase detail (Phases 1-9X), V2-deferred tier infrastructure (Phases 9B/9C/9D), and the original tiered business model are all preserved in `docs/archive/WickedSalemWitchCityTour_MASTER_PLAN_removed_2026-04-23.md`. This file focuses on phases that are in flight, planned, or still provide design reference for pre-launch and post-launch work.

---

## Table of Contents

### Completed Phases (index only — full detail in the archive)
- [Completed Phases Index](#completed-phases-index)

### In Flight / Planned (V1)
1. [Vision](#1-vision)
2. [Architecture Overview](#2-architecture-overview)
3. [Phase 9Q — Salem Domain Content Bridge](#phase-9q--salem-domain-content-bridge) — deferred, consumes 9Y data
4. [Phase 9Y — MassGIS + TigerLine Overlay Integration](#phase-9y--massgis--tigerline-overlay-integration-added-s156-re-scoped-s157) — **IN PROGRESS (S159 shipped 9Y.2b)**
5. [Phase 9Z — Historical Maps Time-Slider](#phase-9z--historical-maps-time-slider-added-s156-re-scoped-s157) — planned (osmdroid TilesOverlay)
6. [Phase 9R — Historic Tour Mode (App-Side)](#phase-9r--historic-tour-mode-app-side) — deferred, consumes 9Y + 9Z
7. [Phase 10 — Production Readiness & Offline Infrastructure](#phase-10--production-readiness--offline-infrastructure)
8. [Phase 11 — Branding, ASO & Play Store Launch](#phase-11--branding-aso--play-store-launch)

### Marketing & Business Development (NO CODE)
9. [Phase 12 — Social Media & Digital Presence](#phase-12--social-media--digital-presence)
10. [Phase 13 — Fieldwork & Content Photography](#phase-13--fieldwork--content-photography)
11. [Phase 14 — Community Engagement & Salem Partnerships](#phase-14--community-engagement--salem-partnerships)

### Growth Features (V1 launch window)
12. [Phase 15 — In-App Virality & Gamification](#phase-15--in-app-virality--gamification)
13. [Phase 16 — iOS & Web Expansion (PWA)](#phase-16--ios--web-expansion-pwa)

### Backlog
14. [Backlog — S119 Parking Lot Triage](#backlog--s119-parking-lot-triage-2026-04-13)

### Reference Sections
15. [Competitive Landscape](#competitive-landscape)
16. [Content Organization Strategy](#content-organization-strategy)
17. [Tour Definitions](#tour-definitions)
18. [Salem POI Master List](#salem-poi-master-list)
19. [Data Sources Reference](#data-sources-reference)
20. [Verification Checkpoints](#verification-checkpoints)
21. [V1 Business Model](#v1-business-model)
22. [Technical Foundation — Audit Recommendations](#technical-foundation--audit-recommendations)
23. [Social Media Content Calendar](#social-media-content-calendar)
24. [Community Engagement Contacts](#community-engagement-contacts)
25. [Fieldwork Planning Guide](#fieldwork-planning-guide)

### Post-Launch / V2
26. [Phase 17 — Merchant Network & Advertising Platform](#phase-17--merchant-network--advertising-platform)
27. [Phase 18 — Custom Narration & Audio Upgrade](#phase-18--custom-narration--audio-upgrade)
28. [Phase 19 — Salem Village LLM Integration](#phase-19--salem-village-llm-integration)
29. [Phase 20 — Additional Revenue Features](#phase-20--additional-revenue-features)

### Critical Timeline
> **Salem 400+ quadricentennial is 2026 — the city's 400th anniversary.** Every organization in Salem is mobilized. This is a once-in-a-generation marketing window. Original launch target was September 1, 2026 to capture October's 1M+ visitors; per operator direction 2026-04-21 this is now aspirational, **quality over schedule.** Missing October 2026 means shipping into October 2027 instead — still valuable, just a longer runway.

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

## Completed Phases Index

Every phase below is shipped and operating. One-line summary only — full step-by-step specs for each live in `docs/archive/WickedSalemWitchCityTour_MASTER_PLAN_removed_2026-04-23.md` (Part 1). The code itself, session logs, and `git log -p` are the authoritative record of how each subsystem actually works today.

| Phase | Summary | Archive anchor |
|---|---|---|
| **Phase 1 — Core Module Extraction** | Split shared data layer (`:core`) out of LocationMapApp so `:app` and `:app-salem` both consume it. | Part 1 → "Phase 1 — Core Module Extraction" |
| **Phase 2 — Salem App Shell (`:app-salem`)** | New Gradle module with Salem-branded UI, map centered on downtown Salem, operational core features. | Part 1 → "Phase 2 — Salem App Shell" |
| **Phase 3 — Salem Content Database** | Room schema for Salem content (tour_pois, historical_figures, historical_facts, timeline_events, primary_sources, tours, tour_stops, events_calendar, salem_businesses). | Part 1 → "Phase 3 — Salem Content Database" |
| **Phase 4 — Content Pipeline** | JVM `salem-content` module reads `~/Development/Salem/data/json/*` → emits bundled `salem_content.db`. | Part 1 → "Phase 4 — Content Pipeline" |
| **Phase 5 — Enhanced Salem POI Catalog** | Curated POIs (historical, cultural, occult, food, lodging) + data-provenance and staleness infra. | Part 1 → "Phase 5 — Enhanced Salem POI Catalog" |
| **Phase 6 — Tour Engine** | Tour data model, tour engine, tour selection UI, active-tour HUD, progress persistence, custom builder, time-filtered tours. | Part 1 → "Phase 6 — Tour Engine" |
| **Phase 7 — GPS Geofence Triggers & Narration** | Tour geofence manager, Android TTS, narration trigger flow, controls, ambient mode. | Part 1 → "Phase 7 — GPS Geofence Triggers & Narration" |
| **Phase 8 — Walking Directions** | OSRM routing integration, route display, "get me there," route optimization for custom tours. | Part 1 → "Phase 8 — Walking Directions" |
| **Phase 9 — Haunted Happenings & Events** | Events calendar DB + UI + seasonal awareness. | Part 1 → "Phase 9 — Haunted Happenings & Events Integration" |
| **Phase 9A — Splash & Satellite Map Entry** | WitchKitty splash, USGS satellite tile source, dramatic zoom animation, tile-source picker. | Part 1 → "Phase 9A — Splash Screen & Satellite Map Entry" |
| **Phase 9A+ — Tour Hardening & Offline Foundation** | Bundled offline tiles, bundled POI data, pre-computed walking route geometry, Frida GPS walk simulator, offline TTS verification. | Part 1 → "Phase 9A+ — Tour Hardening & Offline Foundation" |
| **Phase 9T — Salem Walking Tour Restructure** | Geographic boundary + narration point catalog + narration dialog UI + enhanced content queue + street corridor geofences + 80-120 narration points + suggested walking loops. | Part 1 → "Phase 9T — Salem Walking Tour Restructure" |
| **Phase 9P.A / 9P.B / 9P.C — POI Admin Tool** | `salem_narration_points` PG table, importer, HTTP Basic Auth, admin POI CRUD endpoints, duplicates detection, three-table PG migration, React admin UI with react-arborist POI tree, Leaflet admin map, tabbed edit dialog, Salem Oracle "Generate with AI" integration, publish loop to `salem_content.db`. | Part 1 → "Phase 9P — POI Admin Tool" |
| **Phase 9U — Unified POI Table & SalemIntelligence Import** | `salem_pois` unified table, three-table merge, FK repoint, admin-tool rework, BCS import (1,724 entities), category assignment, visibility defaults, publish loop, legacy-table drop, heading-up rotation smoothness. | Part 1 → "Phase 9U — Unified POI Table & SalemIntelligence Import" |
| **Phase 9X — The Salem Witch Trials Feature** | Dedicated 1692 experience: NPCs, articles, newspapers, Witch Trials entry point, trial-era narration surface, v7→v8 Room bump (headline + headline_summary). | Part 1 → "Phase 9X — The Salem Witch Trials Feature" |

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
- [x] Indexes: `(building_id)`, `(poi_id)`, `(link_type)`

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

## Phase 9Y — MassGIS + TigerLine Overlay Integration (Added S156, re-scoped S157)

> **⚠️ S157 AMENDMENT (2026-04-22).** The original S156 scope was to retire osmdroid, adopt MapLibre Native, and bundle TigerLine-produced vector + Sanborn raster tile packs as the base-map substrate. **Operator reversed that decision on 2026-04-22.** OSM + osmdroid **stay** as V1's base map; MassGIS and TigerLine data are consumed as **overlays only** (osmdroid `Polygon` / `Polyline` / `Marker`). Sub-steps 9Y.5 (MapSurface abstraction) / 9Y.6 (MBTiles consumer) / 9Y.7 (per-overlay port) / 9Y.8 (osmdroid ripout) are **STRUCK**. The data-side sub-steps — ingest, schema, enrichment, routing graph reader, polygon geofence runtime — remain valid and active. Project memory `project_osm_stays_as_basemap.md` codifies the reversal.

**Goal (re-scoped):** Enrich Salem POIs with MassGIS / TigerLine data — building-footprint polygons, MHC Historic Inventory attributes, L3 parcel/assessor linkage, historic-district membership, cemetery polygons — and render selected overlays on top of the existing osmdroid + OSM base map. Pedestrian routing graph from TIGER EDGES + OSM footways still ships bundled.

**Target:** 6 sub-steps (trimmed from 10), ~6-10 sessions. **Status:** IN PROGRESS (S157 L3 ingest). **Priority:** precedes 9Q / 9R / 10. **Plan:** `docs/tigerline-integration-plan.md` (historical — see S157 amendment banner). **Discovery:** `docs/tigerline-integration-discovery.md`.

### Sub-step sequence (post-S157)

| Step | Scope | Status | Blocks on |
|---|---|---|---|
| **9Y.0** | Step 0 discovery pass (data audit, year slate, scope deltas) | DONE (S156) | — |
| **9Y.1** | MassGIS / TigerLine local-PG ingest. L3 Parcels + L3 Assess for Essex County landed S157 (`massgis.l3_parcels_essex` 366k polygons / `massgis.l3_assess_essex` 429k rows) via `cache-proxy/scripts/ingest-l3-parcels-essex.py`. Other layers (structures, mhc_inventory, landuse2005, tiger.edges, tiger.arealm) already present in local `tiger` PG DB from TigerLine's import. | PARTIAL (S157) | Optional: TigerLine re-runs MassGIS collector statewide / Salem-slice SpatiaLite for faster-than-JOIN access |
| **9Y.2a** | PG schema extension — add 9 nullable columns to `salem_pois` (building_footprint_geojson, mhc_*, canonical_address_point_id, local_historic_district, parcel_owner_class) | DONE (S156) | — |
| **9Y.2b** | Kotlin SalemPoi + Room v8→v9 bump + identity-hash cascade (verify-bundled-assets + bundle scripts + publish-salem-pois) + rebake + Lenovo uninstall+install verify | DONE (S159) | — |
| **9Y.3** | Cross-join enrichment script (`cache-proxy/scripts/enrich-pois-from-massgis.js`) — for each of ~459 historic/civic/parks/worship/education POIs, resolve against: `massgis.structures` (building footprint) · `massgis.mhc_inventory` (year_built / style / NR status) · `massgis.l3_parcels_essex` + `massgis.l3_assess_essex` (LOC_ID / owner class / assessor year / use_code) · `massgis.landuse2005` where lu05_desc='Cemetery' · `tiger.arealm` K2582 (cemetery fallback). Populate the 9 Phase 9Y.2a columns. Preview subset first. | **NEXT** (unblocked S159) | — |
| **9Y.4** | Binary pedestrian graph reader + DijkstraRouter in Kotlin. Test against synthetic graph first, real data later. | PARALLEL-READY | TigerLine delivers the binary graph for real use |
| **9Y.5** | ~~MapLibre Native Gradle add + MapSurface abstraction + renderer flag~~ — **STRUCK S157** | CANCELLED | — |
| **9Y.6** | ~~MBTiles base vector tileset consumer~~ — **STRUCK S157** | CANCELLED | — |
| **9Y.7** | ~~Per-overlay port (12+ overlays) from osmdroid to MapLibre~~ — **STRUCK S157** | CANCELLED | — |
| **9Y.8** | ~~Osmdroid ripout~~ — **STRUCK S157** | CANCELLED | — |
| **9Y.9** | Polygon geofence runtime — `NarrationGeofenceManager.checkPosition()` gains `pointInPolygon()` branch when `geofence_shape == "polygon"`. Circle fallback preserved for POIs without building footprints. | PARALLEL-READY (synthetic test data) | — |
| **9Y.Overlay** | osmdroid overlay layer for MassGIS data — building footprints (on narrated historic POIs), cemetery polygons (from `landuse2005`), MHC historic-district boundaries, optional parcel-boundary toggle. Uses osmdroid `Polygon` / `Polyline` — no new renderer. Replaces the spirit of the struck 9Y.6/9Y.7. | READY (after 9Y.3 populates the data) | 9Y.3 |
| **9Y.10** | ~~Admin tool polygon editor~~ — OUT of V1 per operator S156. Admin tool stays point-only. Polygons flow one-way from MassGIS → PG → Room. | REMOVED | — |

### Out of scope (V2 follow-ons)

- Live SQL against `tiger.*` / `massgis.*` from the app — requires backend connectivity; V1 is offline-only. Enrichment happens at publish time.
- Multi-city generalization (brief §9.3) — Salem-specific only for V1.
- MapLibre renderer adoption — deferred indefinitely. osmdroid's overlay API covers V1 needs.
- Admin polygon editor (see 9Y.10 above).

### Verification (end-to-end)

Salem Heritage Trail on the Lenovo, airplane mode, start-to-finish. Narrated historic POIs highlight their building polygon overlay. Narration pulls from MHC year_built / style. Cemetery polygons visible when zoomed in. `verify-bundled-assets.js` wired into Gradle preBuild with v9 identity hash.

### OMEN coordination

S156 filed `~/Development/OMEN/reports/locationmapapp/S156-tigerline-asks-2026-04-21.md`. **Needs S157 amendment** before OMEN actions it — items about TigerLine tile ownership / MapLibre / base-map generation are now obsolete. Items about MassGIS ingest completion, L3 parcel delivery in Salem-slice bundle form, MHC inventory completeness, and historical-maps georeferencing (now for osmdroid TilesOverlay) remain relevant.

---

## Phase 9Z — Historical Maps Time-Slider (Added S156, re-scoped S157)

> **⚠️ S157 AMENDMENT (2026-04-22).** Delivery mechanism changed from MapLibre-rendered MBTiles to **osmdroid `TilesOverlay` with XYZ-tiled Sanborn rasters**, consistent with the S157 decision to keep osmdroid as the V1 base map. Year slate unchanged. Georectification ownership (TigerLine vs SI vs self) still open.

**Goal:** Add a "go back in time on the map" feature backed by the SalemIntelligence public-domain historical maps corpus (509 items at `~/Development/SalemIntelligence/data/historical_maps/`). User drags a time-slider to scrub between Salem as it looked in **1890, 1906, 1950, 1957** (four Sanborn Fire Insurance atlases, 300 sheets total) and today. Narration is time-aware: POIs suppress when the slider is on a year before they existed.

**Target:** 4 sub-steps, ~5-8 sessions (simpler than the MapLibre plan). **Status:** PLANNED. **Priority:** after 9Y.2b/9Y.3. **Plan:** `docs/tigerline-integration-plan.md` (see S157 amendment banner).

### Sub-step sequence (post-S157)

| Step | Scope | Status | Blocks on |
|---|---|---|---|
| **9Z.1** | Georectification + XYZ-tile generation — OWNED by TigerLine, SI, or self (open). Per-year XYZ tile pyramids (z13–19) for 1890, 1906, 1950, 1957 from LoC IIIF bbox metadata. LMA consumes as on-disk tile archive or sqlite MBTiles via osmdroid's `MBTilesFileArchive`. | BLOCKED | Ownership + delivery |
| **9Z.2** | Client-side `TimeSliderView.kt` — bottom-sheet slider with year tick marks (1890, 1906, 1950, 1957, today). Drives a `HistoricalTilesOverlay` wrapping osmdroid's `TilesOverlay`. Crossfades between years via overlay alpha. | READY | — |
| **9Z.3** | Ghost-street / lost-building overlay — MHC records with "no longer extant" flag render as sepia osmdroid `Polygon` overlays at their historical-year setting. | V2-slip if scope pressure | 9Y.3 (MHC data landed) |
| **9Z.4** | Narration integration — `year_built` / `year_demolished` fields gate narration triggers by slider state. Modern-expansion POIs suppress at 1890; demolished POIs narrate at their original year. | READY | 9Y.3 |

### V1 year slate (finalized S156)

1890, 1906, 1950, 1957 — four Sanborn Fire Insurance atlases with consecutive sheet numbering, downtown Salem coverage at 1:600 building-detail scale, LoC IIIF metadata providing bbox georef without manual GCP work.

**Dropped from slider (too sparse):** 1626, 1813, 1853 (single-map years) — embed as static narrative overlays instead.

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

## Backlog — S119 Parking Lot Triage (2026-04-13)

> Items captured during S119 (hero image generation session). Full descriptions in `docs/session-logs/session-119-2026-04-12.md` → "Parking Lot" section. Categorized below by target phase.

### Bugs (Phase 10 — Production Readiness)

| ID | Item | Priority |
|----|------|----------|
| P7 | Narration geofence boundary enforcement — triggers outside downtown bbox | HIGH |
| P8 | Orientation change destroys state (classic lifecycle, needs ViewModel/SavedState) | HIGH |
| P13 | Tour resume / session persistence — cold-start recovery | HIGH |

### Core UX (Phase 10–11)

| ID | Item | Priority |
|----|------|----------|
| P1 | Four app modes (Main Tours / Generated Tours / Explore Salem / Salem 1692) | HIGH |
| P2 | Overlapping POI spiderfy (critical with 2,190 POIs) | HIGH |
| P5 | Intelligent POI visibility & narration control (group toggles, smart defaults) | HIGH |
| P9 | User map rotation / bearing control (two-finger twist, compass) | MEDIUM |
| P10 | Onboarding / first-run experience (interest picker, GPS permission flow) | HIGH |
| P11 | Search UI (2,190 POIs need a search bar — SalemPoiDao.search() ready) | HIGH |
| P12 | POI detail view redesign (hero banner, narration tiers, hours/phone/website) | HIGH |
| P14 | Favorites / "Want to Visit" list | MEDIUM |
| P15 | Battery awareness (GPS duty cycling, low-power mode) | MEDIUM |
| P17 | "Walk Me There" point-to-point navigation (OSRM infrastructure exists) | MEDIUM |
| P21 | Wire hero images into app UI (imageAsset field populated but not displayed) | HIGH |

### Branding & UX Polish (Phase 11)

| ID | Item | Priority |
|----|------|----------|
| P3 | Complete menu/navigation overhaul (Salem-branded, 4-mode structure) | HIGH |
| P4 | Top-layer UI contextualization (cloud/home/utility buttons for tourism) | MEDIUM |
| P6 | Top ribbon as contextual information bar (district, progress, nearby count) | MEDIUM |
| P19 | Night mode / dark map theme (October evening use case) | MEDIUM |

### Social & Growth (Phase 12)

| ID | Item | Priority |
|----|------|----------|
| P16 | POI sharing (hero image + name + description → share sheet) | MEDIUM |

### Content (Phase 9Q–9R)

| ID | Item | Priority |
|----|------|----------|
| P18 | Events / "Happening Now" (744 event pages in SalemIntelligence) | MEDIUM |
| P22 | Salem 1692 newspaper illustrations (203 pen-and-ink sketches) | LOW |

### Hero Image Refinement (Cross-project with SalemIntelligence)

| ID | Item | Description |
|----|------|-------------|
| P20 | Hero image refinement pass (re-run prompts for ~20% exterior-biased) |
| P24 | Hero image prompt architecture overhaul (interior/exterior classification) |
| P25 | SalemIntelligence API feedback loop for hero images |
| P26 | Entity-bonded hero images via SalemIntelligence (product-specific, not generic) |
| P27 | Category/subcategory drives image tone and authority |
| P28 | Every hero image must be visually unique (composition/color variety) |
| P29 | Image-to-context quality review (QC tool, flag/regenerate loop) |

### Infrastructure

| ID | Item | Priority |
|----|------|----------|
| P23 | Dynamic content update pipeline (2-4 week Google Play cycle) | MEDIUM |

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

## V1 Business Model

**Pricing posture (locked S138, 2026-04-16):** V1 ships as a **$19.99 flat paid** download on the Google Play Store. No free tier, no tiered upgrades, no subscription, no in-app purchases, no ads.

### V1 product decisions

| Decision | Value |
|---|---|
| Price | **$19.99 flat, one-time** (paid-up-front on Play Store) |
| Tiers | None. Every buyer gets the full product. |
| In-app purchases | None. |
| Ads | None. |
| LLM features | None. All narration is pre-generated at build time. |
| Network dependency | None at runtime. App is fully offline after install. |
| Content updates | Delivered via standard Play Store APK updates (2-4 week cycle). |
| Target rating | IARC Teen (PG-13). Age gate not needed — Play Store handles discovery by rating. |

### Why flat / offline / no-LLM for V1

- **Operational simplicity.** No billing integration, no subscription renewals, no churn analytics, no geofenced ad server, no merchant portal, no loyalty ledger, no LLM cost accounting. Zero ongoing infrastructure burden beyond the Play Store APK itself.
- **Trust.** A tourist in Salem with flaky cellular coverage gets the full product immediately. Nothing fails because a server was slow, a token expired, or an ad SDK crashed.
- **Privacy.** No network calls = no data collection, no ad-targeting, no third-party SDKs. The V1 Privacy Policy becomes trivially short because the app simply does not transmit user data.
- **Content quality is the moat.** The 1,830-POI Salem corpus + pre-generated narration + bundled heroes + bundled historic maps is the product. Every engineering hour goes into content, not monetization plumbing.
- **Salem 400+ alignment.** A $19.99 tourist companion is a credible commercial product at the quadricentennial without requiring Salem-400+-scale infrastructure.

### What revenue looks like

Revenue = `unit sales × $19.99 × (1 − Play Store cut)`. Play Store takes 15% on the first $1M/yr per developer, 30% above that. Break-even math is therefore `(infra + content-gen GPU + art + legal) / (19.99 × 0.85)`. This is a product we can sell to one customer at a time and still improve — there is no unit economics cliff where a marginal user costs more than they pay.

### V2 roadmap (not V1)

Tiered pricing, ads, merchant network, LLM-powered conversations, loyalty program, and the full advertising-platform design are all deferred to V2. The original tiered-pricing material is preserved verbatim in `docs/archive/WickedSalemWitchCityTour_MASTER_PLAN_removed_2026-04-23.md` (Part 3), and the V2 code phases (9B Feature Tier Matrix, 9C User Settings, 9D Contextual Alerts) are preserved in Part 2 of the same archive. When V2 planning starts, those sections are the starting material — do not rebuild from memory.

### Distribution & fulfillment

- **Channel:** Google Play Store, paid-up-front app listing.
- **Platform:** Android only for V1. PWA and iOS are Phase 16 territory and remain a V2 consideration (see Phase 16 below).
- **Geography:** Worldwide availability, but content is Salem-MA-specific. The app is useful primarily when the user is physically in or planning a trip to Salem; secondary use case is armchair-historian / pre-trip reading.
- **Refunds:** Standard Play Store refund window. Content is audited to IARC-Teen / PG-13 standards before ship so refund exposure from rating surprise is low.

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

---

*Document created: 2026-04-03*
*Major re-eval: 2026-04-04 (Session 76 — added Phases 10-16 expanded, social media, community engagement, fieldwork, competitive landscape, iOS/PWA strategy).*
*Split: 2026-04-23 (Session 160 — completed phase detail, V2-deferred tier infrastructure, and pre-S138 tiered business model moved to `docs/archive/WickedSalemWitchCityTour_MASTER_PLAN_removed_2026-04-23.md`; V1 Business Model section rewritten to reflect $19.99 flat paid / fully offline posture).*
*Project: LocationMapApp v1.5 → WickedSalemWitchCityTour (Katrina's Mystic Visitor's Guide)*
*Author: Dean Maurice Ellis*
