# Plan: TigerLine + MassGIS Foundation + Historical Time-Slider (Phases 9Y + 9Z)

## Context

**Why this is happening now.** TigerLine (sibling project, `~/Research/TigerLine/`) has published a vision brief (`docs/tigerline-salem-tour-reimagined.md`, 937 lines, 2026-04-21) proposing that LMA adopt TIGER/Line 2025 + MassGIS as the ground-truth geometric substrate for the Salem tour. The operator has committed V1 scope: **everything in the brief + a "go back in time on the map" feature sourced from the 5.2 GB of public-domain historical maps SalemIntelligence (`~/Development/SalemIntelligence/data/historical_maps/`) has already catalogued**.

**What this replaces.** The current Salem map is osmdroid + OpenStreetMap/Esri tiles + straight-line (or pre-baked OSRM) walking directions + point-only POIs with circular geofences. The operator's mandate: **"I want OSM maps and things using it to go away before V1."** All of that becomes TigerLine/MassGIS-native — authoritative building polygons, MHC historic inventory records, a real pedestrian graph (trails + wharves + park paths, not just roads-minus-cars), and period-accurate historical overlays the user can scrub through time.

**Non-goals.** No deadline. Operator's words: *"I commit to providing the best product when it is done, not a date."* The Sept 2026 Play Store target listed in STATE.md is not a hard deadline for this work; the "best foundation" takes precedence. This plan treats quality as the priority and time as the variable.

**What is already done.** The three TigerLine reference docs are already in-repo:
- `docs/tigerline-salem-features.md` (per-layer inventory)
- `docs/tigerline-location-capabilities.md` (capability catalog)
- `docs/tigerline-salem-tour-reimagined.md` (vision brief — the prompt for this plan)

---

## Key architectural decisions (up front — operator should confirm before execution)

### D1. Renderer: MapLibre Native Android (replaces osmdroid)

**Why.** MapLibre Native is the open-source successor to Mapbox's pre-v10 Android SDK, natively consumes MBTiles + MVT (vector tiles), supports both raster and vector layers in the same view, renders arbitrary GeoJSON polygons and lines, is actively maintained, and gives us the cartographic freedom called out in §1.2 of the brief ("Cartoon Cartography"). It's the single industry-standard open renderer for custom-styled maps in 2026.

**Alternatives considered:**
- **Stay on osmdroid + feed it MBTiles.** osmdroid can accept MBTiles as an archive source, but it's a raster-tile-only pipeline. We lose the vector/style freedom we need for Salem-specific cartography. Rejected.
- **Google Maps SDK.** Play Services dependency, online-only baseline, not V1_OFFLINE_ONLY compatible. Rejected.
- **Custom Canvas + MBTiles reader.** Months of work reinventing rendering. Rejected.

**Dependency:** `org.maplibre.gl:android-sdk` (current stable ≥ 11.x).

### D2. Offline delivery shape

V1 is fully offline per STATE.md + `project_v1_offline_only.md`. **No live PG queries from the app**. Every TigerLine/MassGIS capability the app needs must be pre-computed and bundled. Three bundled assets land in the APK:

| Bundle | Format | Contents | Rough size |
|---|---|---|---|
| **Spatial DB** | Room-extended SQLite with JSON polygons in TEXT columns (no SpatiaLite dependency — keep the single `salem_content.db` model) | POIs joined to building_footprint (GeoJSON), mhc_id, canonical_address, mhc_narrative, year_built, style, nr_status, local_historic_district | 12–25 MB |
| **Pedestrian routing graph** | Binary blob — adjacency list of TIGER EDGES + MassGIS trails + OSM footways, pre-topologized, pure-Kotlin Dijkstra on the device | Graph + node lookup by lat/lng | 10–20 MB |
| **Base MBTiles** | MBTiles (SQLite) — vector tiles from TigerLine + MassGIS; zoom 13–19 | Buildings, roads, water, parks, historic districts | 80–150 MB |
| **Historical MBTiles** | MBTiles (SQLite) — per-year raster overlays from georectified LoC / USGS scans | One MBTiles per historical year we support (1813, 1853, 1890, 1906, 1950, etc.) | 30–80 MB each; pick 4–6 years for V1 |

**Total bundled growth:** +150–350 MB on top of today's 739 MB debug APK. AAB split + compression + APK-size audit (already a carry-forward) will bring this down. At Play Store, we'll likely use **Android App Bundle asset packs** to deliver historical MBTiles as install-time or on-demand packs, keeping the base APK reasonable.

### D3. TigerLine's delivery contract

The operator said *"If we need TigerLine to provide API's, etc. we can do that."* I will ask TigerLine (via OMEN) for four concrete deliverables:

1. **Salem SpatiaLite / SQLite bundle** (brief §9.1a) — pre-joined tables (buildings ⋈ MHC ⋈ address_points ⋈ parcels ⋈ historic_districts) clipped to Salem + 5-mile buffer. We ingest this into Room at publish time.
2. **Salem pedestrian routing graph** (brief §9.1b) — binary adjacency graph with node+edge arrays. We ship it as-is.
3. **Salem base MBTiles** (brief §9.1c, "Phase 10 cartography") — vector MBTiles styled to our spec (we can iterate on style even after the initial cut). If TigerLine can't produce this, we do it ourselves with `tippecanoe` against their PostGIS export.
4. **Historical MBTiles** — TigerLine or LMA can do this; the georectification skill lives wherever the GeoTIFF tooling is. SI has the raw scans. **Proposed split:** USGS GeoPDFs (already georeferenced) → LMA uses `gdal_translate` + `gdal2tiles` directly, no help needed. LoC JP2s (not georeferenced) → ask TigerLine or SI to handle GCP registration, since both already work with geospatial data at scale.

### D4. Sequencing: parallel flag, not big bang

**Don't rip osmdroid out first.** Add MapLibre Native alongside osmdroid gated by a `FeatureFlags.MAPLIBRE_RENDERER` compile-time boolean. Port one overlay at a time. Run both renderers in parallel on the Lenovo for a few weeks. Flip the flag when MapLibre renders everything osmdroid does today, plus the new polygon + historical layers. Only then delete the osmdroid code paths + Gradle deps.

This protects against regressions on the 1,830 POIs + 1,770 narrations + 5 tours already running.

### D5. Phase numbering: 9Y (Foundation) + 9Z (Historical)

The current master plan has Phase 9X (Witch Trials, complete) → Phase 9Q (Salem Domain Content Bridge) → Phase 9R (Historic Tour Mode) → Phase 10 (Play Store prep). Phase 9Y/9Z don't exist — free slots.

**Insert the TigerLine work as:**
- **Phase 9Y — TigerLine/MassGIS Foundation.** Comes after Phase 9X (done), before 9Q / 9R / 10. 9Q and 9R become *consumers* of the 9Y substrate rather than parallel tracks.
- **Phase 9Z — Historical Maps Time-Slider.** Comes after 9Y since it depends on the MapLibre renderer + MBTiles pipeline. Can run in parallel with 9Q/9R content work once 9Y is done.

---

## Phase 9Y — TigerLine/MassGIS Foundation

### 9Y.1  TigerLine ingest pipeline (backend, cache-proxy-side)

**Files touched:** new `cache-proxy/scripts/ingest-tigerline/` directory:
- `import-tigerline-dump.js` — consume the TigerLine Salem SpatiaLite bundle, load into PG schemas `tiger.*` and `massgis.*`
- `import-mhc-inventory.js` — MHC Historic Inventory → `massgis.mhc_inventory` table
- `import-address-points.js` — MassGIS ADDRESS_POINTS → `massgis.address_points`
- `import-building-footprints.js` — MassGIS STRUCTURES → `massgis.structures_poly`
- `import-historic-districts.js` — Local Historic District polygons → `massgis.local_historic_dist`
- `import-cemetery-polygons.js` — TIGER AREALM + MassGIS MHC_BURIALGROUNDS
- `import-pedestrian-graph.js` — unified trail + footway + road edges → `routing.pedestrian_edges`

**Pattern matches existing scripts:** `cache-proxy/scripts/import-bcs-pois.js`, `import-witch-trials-npc-bios.js`, etc.

**Requires from TigerLine:** the SpatiaLite / PG dump bundle + schema documentation.

### 9Y.2  Schema evolution — POI model

**PG `salem_pois`:** add columns (all nullable; backward-compatible):
- `building_footprint_geojson TEXT` — MassGIS structure polygon as GeoJSON
- `mhc_id TEXT` — MHC record ID (`SAL.1`, `SAL.214`, etc.)
- `mhc_year_built INTEGER`
- `mhc_style TEXT` (First Period, Georgian, Federal, Greek Revival, etc.)
- `mhc_nr_status TEXT` (NR Listed / Eligible / Contributing / null)
- `mhc_narrative TEXT` (MACRIS surveyor description)
- `canonical_address_point_id TEXT` — MassGIS address_points PK
- `local_historic_district TEXT`
- `parcel_owner_class TEXT`

**Repurpose vestigial columns** (identified by Explore agent as never-read):
- `geofence_shape` → becomes meaningful: `"circle"` | `"polygon"` | `"district"`
- `corridor_points` → becomes polygon-vertices-as-GeoJSON when `geofence_shape = "polygon"`

**Room side:** `SalemPoi.kt` data class gains the same fields. `publish-salem-pois.js` is updated to carry them through. Room version bumps from 8 → 9; migration is additive-only so `fallbackToDestructiveMigration` is still safe (but we schedule replacing that with a real migration as part of Phase 10 hardening — existing carry-forward).

**Files:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/content/model/SalemPoi.kt`, `.../content/db/SalemContentDatabase.kt`, `cache-proxy/scripts/publish-salem-pois.js`.

### 9Y.3  Cross-join pipeline (PG → enriched POIs)

**New script:** `cache-proxy/scripts/enrich-pois-from-tigerline.js`. For each POI in `salem_pois`:
1. Resolve `address` (or `lat`/`lng`) to nearest `massgis.address_points` → populate `canonical_address_point_id`.
2. `ST_Contains` check against `massgis.structures_poly` → populate `building_footprint_geojson`.
3. `ST_Intersects` check against `massgis.mhc_inventory` → populate `mhc_id` + year + style + nr_status + narrative.
4. `ST_Within` check against `massgis.local_historic_dist` → populate `local_historic_district`.
5. Log gaps (POIs that don't match any building / any MHC record) for curator review.

This is the §6.1 join in the brief, run once at publish time rather than live.

### 9Y.4  Pedestrian routing graph + Kotlin Dijkstra

**Backend:** ingest TIGER EDGES + MassGIS TRAILS + DCRTRAILS + LDTRAILS + BIKETRAILS + OSM footways into `routing.pedestrian_edges`. Run pgRouting to build topology. Export to a binary adjacency format — roughly:

```
nodes.bin:    count | (u32 lat_scaled, u32 lng_scaled) * count
edges.bin:    count | (u32 src, u32 dst, u16 length_cm, u8 edge_class) * count
```

10-20 MB for Salem extent. Lands at `app-salem/src/main/assets/routing/salem_pedestrian.bin`.

**Client:** `WalkingDirections.kt` gains a new `BinaryPedestrianGraph` backend. The existing `getBundledTourRoute(tourId)` pre-computed routes still work; the new `getRoute(from, to)` path uses the binary graph instead of straight-line.

**Files:**
- new: `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/tour/routing/BinaryPedestrianGraph.kt`
- new: `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/tour/routing/DijkstraRouter.kt`
- modified: `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/tour/WalkingDirections.kt`

### 9Y.5  MapLibre Native integration (in parallel with osmdroid)

**Gradle:** add `org.maplibre.gl:android-sdk:11.x` to `app-salem/build.gradle`.

**Feature flag:** `FeatureFlags.MAPLIBRE_RENDERER` (new, default `false` during development, `true` before V1 ship).

**Layout:** `activity_main.xml` gets a `FrameLayout` that hosts either `org.osmdroid.views.MapView` or `org.maplibre.android.maps.MapView` based on the flag.

**Abstraction:** new interface `com.example.wickedsalemwitchcitytour.ui.map.MapSurface` with methods: `setTileSource`, `addMarker`, `addPolyline`, `addPolygon`, `addOverlay`, `setZoom`, `animateTo`, etc. Two implementations: `OsmdroidMapSurface`, `MapLibreMapSurface`.

**SalemMainActivity** swaps direct `binding.mapView.xxx` calls for `mapSurface.xxx`. All 12+ overlays go through `MapSurface`.

**Files:**
- new: `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/map/MapSurface.kt`
- new: `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/map/OsmdroidMapSurface.kt`
- new: `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/map/MapLibreMapSurface.kt`
- modified: `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt` (the 1,000+ line god-activity)
- modified: `app-salem/src/main/res/layout/activity_main.xml`
- modified: `core/src/main/java/com/example/locationmapapp/data/model/Models.kt` (replace `org.osmdroid.util.GeoPoint` imports with a neutral `LmaGeoPoint(lat: Double, lng: Double)` in `core/`)

### 9Y.6  MBTiles base tileset

**Source:** TigerLine delivers a Salem-specific MBTiles file (vector, MVT format, zoom 13–19). Style JSON authored separately (can iterate on style post-ingest).

**Client:** `TileSourceManager.kt` rewrites its offline-source logic: instead of pointing osmdroid at `salem_tiles.sqlite` (Esri satellite archive), it points MapLibre at `salem_base.mbtiles` via `mbtiles://` URI. Satellite tiles remain available as a *second* MapLibre raster layer (so the user can still toggle satellite view).

**Files:**
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/TileSourceManager.kt` (rewrite)
- `app-salem/src/main/assets/salem_base.mbtiles` (new binary asset)
- `app-salem/src/main/assets/salem_style.json` (new MapLibre style)

### 9Y.7  Overlay port — one layer at a time

Port each overlay from osmdroid to MapLibre. Verify visually on Lenovo before moving to next. Order (easiest → hardest):

1. **Base map + zoom controls** — smoke test the renderer
2. **POI markers** (`refreshPoiMarkerIcons`, `refreshNarrationIcons`) — MapLibre SymbolLayer with icon expressions
3. **GPS journey polyline** (`gpsTrackOverlay`) — MapLibre LineLayer with a GeoJSONSource updated live
4. **Tour route polyline** (`tourRoutePolyline` from `SalemMainActivityTour.kt`) — another LineLayer
5. **Numbered tour stops** — LightweightSymbolLayer with number annotations
6. **Location ball** (`SalemLocationBallOverlay`) — Custom annotation or CircleLayer
7. **Geofence debug rings** (`userTriggerRing20/30/40`) — CircleLayer
8. **TFR / camera / school / flood polygons** — FillLayer + LineLayer
9. **Radar tile animation** (`radarTileOverlay`) — MapLibre RasterLayer with frame cycling
10. **Building footprints** (new) — FillLayer with historic-period color expression
11. **Historic district overlays** (new) — FillLayer with district-specific tint
12. **MHC-tagged buildings** (new) — SymbolLayer with MHC ID, tap opens info card

**Files:** the per-feature activity extensions already exist (`SalemMainActivityNarration.kt`, `SalemMainActivityTour.kt`, etc. — 8 extension files). Each gets its overlay-port in a dedicated sub-step.

### 9Y.8  Osmdroid ripout

Once 9Y.7 is complete and the flag has run at `MAPLIBRE_RENDERER = true` on the Lenovo through a full field walk without regressions:

- Delete `SalemLocationBallOverlay.kt`, `TileSourceManager.kt` osmdroid branches, `OsmdroidMapSurface.kt`
- Remove `osmdroid`, `osmdroid-mapsforge`, `osmbonuspack` from all three `build.gradle` files (app-salem, app, core)
- Replace remaining `org.osmdroid.util.GeoPoint` imports in `core/` `Models.kt` with `LmaGeoPoint`
- Delete `OSRMRoadManager` usage from `WalkingDirections.kt`
- Delete `salem_tiles.sqlite` from `app-salem/src/main/assets/` (replaced by `salem_base.mbtiles`)

The ripout is a single commit/PR at the end of Phase 9Y.

### 9Y.9  Polygon geofencing

**Upgrade `NarrationGeofenceManager.checkPosition()`:** for each POI where `geofence_shape == "polygon"` (populated in 9Y.3 for buildings with footprints), do point-in-polygon against `corridor_points` GeoJSON. Fallback to circle for POIs without a building footprint (streets, open-air memorials, statues).

**Performance:** 1,830 POIs × point-in-polygon on every GPS update is fine — polygons are small, we can bbox-prefilter. Spatial index is not needed at this scale.

**Files:** `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/narration/NarrationGeofenceManager.kt` + helpers.

### 9Y.10  Admin tool polygon editor

Add a MapLibre GL JS (web) polygon-draw editor to `web/src/admin/PoiEditDialog.tsx` — `Location` tab grows a "Building footprint" sub-section with:
- Read-only display of MassGIS-sourced polygon (if present)
- "Override" toggle to draw a custom polygon
- Leaflet.Draw or Mapbox-GL-Draw for the edit surface
- GeoJSON validation on save

**Files:** `web/src/admin/PoiEditDialog.tsx` + new `web/src/admin/PolygonEditor.tsx`.

---

## Phase 9Z — Historical Maps Time-Slider

### 9Z.1  Georectification

**USGS GeoPDFs** (198 maps, already georeferenced): `gdal_translate` → GeoTIFF → `gdal2tiles.py --profile=mercator --zoom=13-17` → MBTiles. Fully scriptable.

**LoC Maps + Sanborn JPEG-2000** (311 sheets, NOT georeferenced): need Ground Control Points. Two options:
- **Ask TigerLine or SI** to do the GCP work at their end (batch, they have the tooling).
- **Do it ourselves** via QGIS's georeferencer tool (manual, ~10–20 min per map, tedious but possible).

Proposed: ask TigerLine first; fall back to SI; last resort we do it. For V1 we target 4–6 period-accurate years: **1813, 1853, 1890, 1906, 1950**. Sanborn sheets per year get mosaicked before tiling.

**Scripts:** new `cache-proxy/scripts/historical-maps/` directory:
- `mosaic-sanborn-by-year.js`
- `register-loc-jp2.sh` (GDAL + GCP file input)
- `tile-historical-maps.sh` (gdal2tiles orchestration)
- `bundle-historical-mbtiles.js` (copy per-year MBTiles into app-salem assets)

### 9Z.2  Client-side time-slider UI

**New component:** `TimeSliderView.kt` — bottom-sheet slider with year tick marks (1626, 1813, 1853, 1890, 1906, 1950, "today"). Dragging updates a state flow.

**Map layer switching:** `MapLibreMapSurface` gets a `setHistoricalYear(year: Int?)` method. When set, it activates the matching MBTiles raster layer under the base vector layer. `null` = modern map only.

**Crossfade:** between consecutive years, crossfade the raster layers (MapLibre supports layer opacity animation natively).

**Files:**
- new: `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/timeslider/TimeSliderView.kt`
- modified: `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt`
- modified: `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/map/MapLibreMapSurface.kt`

### 9Z.3  Ghost-street / lost-building overlay

Per brief §7.2 — MHC records have addresses for buildings no longer extant. Render those as low-opacity sepia overlays that appear at their historical-year setting. Not a V1 blocker but lands naturally once the MBTiles + polygon data are in place.

### 9Z.4  Narration integration with time-slider

When the user is on an older year and walks past a modern POI that didn't exist then (e.g., Peabody Essex 2003 expansion at year=1890), suppress the narration. Conversely, POIs with "demolished" flags narrate only in the year they existed.

**Schema impact:** add `year_built`, `year_demolished` to POI model — most already come from MHC data, a few need curator tagging.

---

## Deliverables to request from TigerLine (via OMEN)

1. **Salem SpatiaLite bundle** — cross-joined POI-ready table (buildings ⋈ MHC ⋈ address_points ⋈ parcels ⋈ historic_districts), Salem + 5mi buffer, latest vintage.
2. **Salem pedestrian routing binary graph** — TIGER EDGES + MassGIS trails + OSM footways, pre-topologized, adjacency-list binary.
3. **Salem base MBTiles** — MVT format, zoom 13–19, Salem extent, styled to a provisional Salem Tour spec we'll co-author.
4. **Historical MBTiles** (4–6 years: 1813, 1853, 1890, 1906, 1950) — if TigerLine has capacity. Otherwise we do USGS ourselves and negotiate LoC/Sanborn split with SI.
5. **Schema documentation** — PostgreSQL schemas + any conventions (projection, snapping tolerances, address normalization rules).
6. **Provenance freshness contract** — quarterly refresh cadence for MassGIS layers per brief §9.5.

Relay via `~/Development/OMEN/reports/locationmapapp/S157-tigerline-asks-*.md` at session end.

## Deliverables from SalemIntelligence (via OMEN)

1. **Manifest confirmation** — 509 historical maps (already catalogued at `~/Development/SalemIntelligence/data/historical_maps/manifest.json`). No data movement needed; LMA reads from SI's path during build.
2. **Optional: JP2 georectification** — if SI has bandwidth to produce `.gcp` / `.tfw` files for the 311 LoC JP2 sheets, it saves LMA 50+ hours of QGIS work.

---

## Critical files that this plan touches

**Kotlin (app-salem):**
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/SalemMainActivity.kt` — core map-host activity (rewrite of overlay wiring via MapSurface abstraction)
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/TileSourceManager.kt` — rewrite for MBTiles
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/map/*.kt` — **new directory** for MapSurface abstraction
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/timeslider/TimeSliderView.kt` — **new** (9Z)
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/content/model/SalemPoi.kt` — new fields
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/content/db/SalemContentDatabase.kt` — version bump 8 → 9
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/narration/NarrationGeofenceManager.kt` — polygon geofence support
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/tour/WalkingDirections.kt` — new binary-graph backend
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/tour/routing/*.kt` — **new** directory
- `app-salem/src/main/res/layout/activity_main.xml` — MapView swap

**Kotlin (core):**
- `core/src/main/java/com/example/locationmapapp/data/model/Models.kt` — `GeoPoint` → `LmaGeoPoint`
- `core/src/main/java/com/example/locationmapapp/util/FeatureFlags.kt` — `MAPLIBRE_RENDERER` flag

**Gradle:**
- `app-salem/build.gradle`, `app/build.gradle`, `core/build.gradle` — osmdroid removal + MapLibre addition

**Bundled assets:**
- `app-salem/src/main/assets/salem_content.db` — schema v9
- `app-salem/src/main/assets/salem_base.mbtiles` — **new** base tileset
- `app-salem/src/main/assets/salem_style.json` — **new** MapLibre style
- `app-salem/src/main/assets/routing/salem_pedestrian.bin` — **new** routing graph
- `app-salem/src/main/assets/historical/{1813,1853,1890,1906,1950}.mbtiles` — **new** (9Z)
- `app-salem/src/main/assets/salem_tiles.sqlite` — **delete at 9Y.8**

**Cache-proxy (backend):**
- `cache-proxy/scripts/ingest-tigerline/*.js` — **new directory**
- `cache-proxy/scripts/historical-maps/*` — **new directory**
- `cache-proxy/scripts/enrich-pois-from-tigerline.js` — **new**
- `cache-proxy/scripts/publish-salem-pois.js` — update to carry new columns
- `cache-proxy/scripts/verify-bundled-assets.js` — extend manifest (already a carry-forward to wire into Gradle)

**Web (admin tool):**
- `web/src/admin/PoiEditDialog.tsx` — polygon editor
- `web/src/admin/PolygonEditor.tsx` — **new**

**Documentation:**
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — insert Phase 9Y + 9Z
- `STATE.md` — TOP PRIORITY change
- `docs/tigerline-integration-status.md` — **new** running status doc

---

## Verification strategy

### Per-sub-step (unit)

- **Ingest scripts** — dry-run with `--dry-run` flag, compare PG row counts before/after
- **Enrichment script** — sample 20 POIs post-run, verify MHC records attach where expected
- **Routing graph** — encode a known 200m Salem walk (Essex St), decode via DijkstraRouter, verify path length matches ground truth ±5%
- **POI schema** — Room version bump smoke test on Lenovo: install fresh APK, verify DB opens, 1,830 POIs still load

### Per-phase (integration)

- **9Y.5–9Y.7** (MapLibre port): run both renderers side-by-side via `MAPLIBRE_RENDERER` flag. Visual diff at identical zoom/center on the Lenovo. Each overlay port gets a before/after screenshot committed to `docs/phase-9y-port/`.
- **9Y.8** (ripout): single commit, full regression on Lenovo — splash, tours, narration, Find, Witch Trials panel, newspapers, photos all work.
- **9Y.9** (polygon geofence): real-outdoor walk against 10 buildings with known footprints. Compare trigger timing to circle baseline.
- **9Z.2** (time-slider): scrub through all 6 years, verify raster layers load, crossfade smooth, no memory leaks over 5 min of scrubbing.

### End-to-end (acceptance)

- The "Three-Hour Salem Walking Tour" (brief §8.1) on the Lenovo, airplane mode, start-to-finish. Every stop highlights its building polygon, narrates from MHC data, routes over real pedestrian paths.
- Toggle time-slider to 1890, re-walk. Sanborn Fire Insurance maps render correctly. POIs that didn't exist yet suppress narration.
- APK passes `verify-bundled-assets.js` (which is wired into Gradle preBuild by then).

---

## Out of scope for this plan (but likely follow-ons)

- **Multi-city generalization** (brief §9.3) — this plan is Salem-specific. Making NOLA / DC / Hollywood reusable is a post-V1 V2 item.
- **Live SQL against `tiger.*` / `massgis.*`** (brief §9.1c) — V2, when the tiered model returns and we can have backend connectivity.
- **Ghost-street tour** (brief §7.2) — 9Z.3 is a sketch; the full experience with curator-written "what WAS here" narration is V2.
- **Historic period style (Phase 10 Cartoon Cartography)** (brief §1.2) — the MapLibre style we ship for V1 is functional. The stylized "1800 Salem imagined onto modern GPS" is V2.
- **Real Room migrations** — continues to defer to Phase 10 hardening.

---

## Risks + mitigations

| Risk | Mitigation |
|---|---|
| MapLibre Native learning curve blows scope | Parallel flag + per-overlay porting = incremental risk; roll back to osmdroid if a specific overlay can't port |
| TigerLine delivery delayed | Phase 9Y.1–9Y.4 work against test PG exports TigerLine can produce early. Don't block on their full bundle. |
| APK size balloons past Play Store limits | Asset packs for historical MBTiles; poi-icons audit (already a carry-forward) |
| LoC JP2 georectification is manual hell | Try to push it upstream to TigerLine or SI; if we must do it ourselves, scope to 2 years for V1 (1890 Sanborn + 1813 LoC) and add more post-launch |
| Admin tool polygon editor scope creep | Ship a read-only display first (building footprint visible, not editable). Editor can land post-V1. |
| Room destructive-migration risk on version bump | 9Y.2 schema is additive-only; Room keeps v8 → v9 migration trivial; BUT the install-protocol feedback learned today (`feedback_adb_install_after_db_rebake.md`) applies — always uninstall+install during dev. |

---

## Operator decisions locked in (post plan-approval)

1. **Tile generation owned by TigerLine.** LMA does not produce MBTiles — base vector or historical raster. We receive pre-baked tiles from TigerLine when ready. 9Y.6 and 9Z.1 simplify to "consume + bundle."
2. **Polygon editing in admin tool = OUT of V1.** No GUI for drawing or overriding building footprints. `PoiEditDialog.tsx` stays point-only. Polygons flow one-way from MassGIS → PG → Room. **Step 9Y.10 removed from this plan.**
3. **Polygon data model + rendering + geofencing = ESSENTIAL.** 9Y.2 (schema), 9Y.7 (render polygons as MapLibre FillLayer), 9Y.9 (point-in-polygon geofencing) are non-negotiable for V1.
4. **Monolithic APK for now.** No Play Store asset packs at start. Watch APK size and the per-user data cost once we see the bundle math; revisit if numbers are prohibitive.
5. **MassGIS has priority over TIGER when ingesting.** MA-authoritative layers (MHC Historic Inventory, building footprints, address points, historic districts, cemetery polygons, coastline, MBTA, tidelands) land first. TIGER's national substrate (EDGES, AREALM, COUSUB) follows. This rewrites 9Y.1's sub-script order.
6. **Step 0 Discovery pass precedes any implementation.** Before locking in 9Y.1-through-9Y.9 ordering, we audit what TigerLine + MassGIS + SI historical_maps actually contain. Ordering may shift after we see the data.

---

## Step 0 — Discovery pass (NEW, precedes all other steps)

Before implementation begins, produce a written audit of the foundation data so we know what we're building on. Outputs land at `docs/tigerline-integration-discovery.md`.

**Inspect:**
- TigerLine's `~/Research/TigerLine/` current state — what's ingested, what dumps are queryable, what the sibling project's own master plan has promised-but-not-delivered
- MassGIS layer-by-layer availability (~198 layers) — which are live on TigerLine's workstation, which still pending
- SI's `~/Development/SalemIntelligence/data/historical_maps/manifest.json` in detail — exact years, coverage, file counts per year, georeferencing status
- Any existing TigerLine → LMA export artifacts (test bundles, sample SQL dumps)

**Report on:**
- What's ready to consume TODAY vs what we'd need TigerLine to produce
- Coverage gaps (years with no historical map, neighborhoods with no MHC records, etc.)
- Data-quality unknowns (how dense is the MHC inventory actually? does `structures_poly` cover Salem downtown? are address points snapped to building centroids or curb positions?)
- Which Phase 9Y sub-steps move up or down in the ordering based on data-readiness

**Output:** one document, ~300-500 lines, that the operator reviews before we commit to code changes.
