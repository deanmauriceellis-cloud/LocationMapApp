# LocationMapApp — Session Log

> **Rolling window — last 10 sessions only.** On every session end, the oldest session is moved to `SESSION-LOG-ARCHIVE.md`. This file currently holds Sessions 172-181. Everything older lives in the archive (which itself ends with the original v1.5.0–v1.5.50 archive at the bottom).
>
> **Per-session live conversation logs** (the canonical, append-only record with full reasoning, decisions, file diffs, build results) live in `docs/session-logs/session-NNN-YYYY-MM-DD.md`. The entries in this file are 2-3 sentence summaries — pointers to the live logs, not replacements.

## Session 182: 2026-04-26 — S181 OSM-policy reversal RETRACTED; tour-rendering declared content-not-engineering

Operator opened with "I don't like your solution from the last session" — pushed back on S181's OSM-merge plan after testing P2P "tap POI → directions" between two Heritage Trail stops and getting a clean route, which the multi-stop tour render of consecutive stops can't reproduce. Traced both code paths: `Router.routeMulti` is literally per-leg `route()` calls + `concat()`, byte-for-byte equivalent to N P2P calls. The actual divergence is in the input — tour legs feed POI centroids (often inside/behind a building polygon, KNN snap can land on the wrong side), P2P feeds the user's GPS (already on a sidewalk). Same algorithm, different start node, different shortest path, one crosses the building and one doesn't. S181's proposed broad OSM pedestrian merge wouldn't have fixed it. Reverted: memory `feedback_no_osm_use_local_geo.md` restored to S178 surgical-only constraint, MEMORY.md index reverted, `docs/plans/S182-osm-pedestrian-routing-merge.md` archived to `docs/archive/S182-osm-pedestrian-routing-merge_archived_2026-04-26-misdiagnosis.md` with banner, CLAUDE.md pinned next-steps block reframed. Feasibility-checked an address-anchored alternative — `salem_pois.address` has 97.5% coverage, TIGER `lfromadd/ltoadd/rfromadd/rtoadd` interpolation works (sample "237 Essex St" geocoded to 12 m offset on Essex St centerline) — but ≈half the Heritage Trail stops have addresses that fall through (intersection / square-only / no-number / TIGER coverage gap), so the resolver would need a layered fallback. Operator rejected the whole engineering path: "I need to do that tour by hand, I don't want to muck more with the mapping, wasting too much time on that." Saved a new HARD RULE memory `feedback_tour_routing_is_content_not_engineering.md` to prevent this loop in future sessions. Cache-proxy (4300) and Vite admin (4302) brought up at session end for the operator's curation work. **No code changes shipped this session.**

Full session detail: `docs/session-logs/session-182-2026-04-26.md`. Commit: `<S182-close-sha>`.

---

## Session 181: 2026-04-26 — Routing-data OSM policy reversal + S182 plan; one-medium keystore backup

Operator picked S180 carry-forward #1 (off-machine keystore backup) and #6 (eyes-on smoke test). Backup: created staging dir + sudo script that SHA-256-verifies before/after copy and chowns USB destination back to user; one-medium copy landed on `/media/witchdoctor/writable`, second copy on a different medium still owed before first Play Console upload. Smoke test surfaced one issue (Salem Heritage Trail polyline visually grazing historic-building polygons in downtown), which became a five-step diagnostic rabbit hole: logcat confirmed router output was correct (47,704-edge bundle, 21-turn `routeMulti` route, no fallbacks); PostGIS against `massgis.buildingfp` found zero downtown overlaps; against the actual rendered `salem-historic-buildings.geojson` set found 14 walkable edges that cut historic buildings (worst: St Peter St → Gardner Block 57m, Salem Green cluster grazing 3 polygons). Operator reframed: "why isn't it following the sidewalk?" — exposed that downtown `salem.edges` is ~92% TIGER street centerlines and only ~8% pedestrian, with `osm.salem_pedestrian_edges_resolved` (1,030 downtown footway/path/pedestrian/steps edges, ~120km Salem-area total) sitting unused. After full Witchy-tile-bake source review (~90% OSM-rendered, ~10% MassGIS, with documented S158 carve-out), operator reversed the prior surgical-only OSM constraint: "if we require OSM to do this right, then let's change our direction and allow OSM for our routing needs." Memory `feedback_no_osm_use_local_geo.md` amended (S178 surgical-allowlist superseded for pedestrian network; broad pedestrian-network ingest permitted under topological-merge constraints with `pgr_strongComponents ≤ 5` hard gate). Plan written at `docs/plans/S182-osm-pedestrian-routing-merge.md` — 4 phases / 2-3 sessions / risk register / rollback. **No code changes shipped this session.** Smoke test interrupted before items 2-N (POI detail Visit Website ACTION_VIEW handoff, Find dialog Reviews/Comments hidden, Find Directions on-device router, toolbar gating, webcam dialog gating) — carry-forward.

Full session detail: `docs/session-logs/session-181-2026-04-26.md`. Commit: `cf863d9`.

---

## Session 180: 2026-04-26 — V1 Play Store gating chores + first signed AAB on Lenovo

Tier 1 of the S180 ship-readiness review shipped end-to-end. **P1** stripped the manifest network surface (INTERNET / ACCESS_NETWORK_STATE / cleartextTraffic gone, WickedMap LAUNCHER intent removed, VoiceTest narrowed to exported=false). **P2** hard-gated 10 V1-disabled UI sites that bypassed the OkHttp `OfflineModeInterceptor` via WebView, osmdroid online tile sources, and external Intents — all behind `if (!FeatureFlags.V1_OFFLINE_ONLY)` so R8 + const dead-code-eliminates them from the V1 release binary; Find dialog Directions button permanently switched to on-device `walkTo()` instead of Google Maps URL. **P3** revised V1 website behaviour per operator: Visit Website button stays visible, click hands off to external browser via ACTION_VIEW (no in-app WebView); defensive gates on every social dialog body. **P4** locked `UserDataDatabase` migrations — removed `fallbackToDestructiveMigration` so paying users no longer lose POI encounter history on every update; schema v2 is the v1.0.0 floor, future bumps need real Migration objects. **P5** versionCode 1 → 10000, minifyEnabled+shrinkResources, new conservative `proguard-rules.pro` (with -dontwarn for sqlite-jdbc/slf4j leaking from `:routing-jvm`), `signingConfigs.release` block reading from `~/.gradle/gradle.properties`, and `verifyBundledAssets` Gradle Exec task wired to preBuild (catches the silent rebake-wipe class of bug). Generated upload keystore at `~/keys/wickedsalem-upload.jks` (PKCS12, RSA-2048, 10000-day validity, 32-char random pw); registered in OMEN credential audit (committed + pushed to OMEN remote). First signed AAB built (78 MB) + signed release APK (87 MB) installed on Lenovo TB305FU; SalemMainActivity at 60fps with no AndroidRuntime errors. Two new memories saved: `feedback_admin_changes_propagate_to_builds.md` + `feedback_v1_feature_scope_explicit.md`. Operator-side carry-forward: keystore off-machine backup before first Play Console upload, Form TX copyright (24 days to 2026-05-20), Play Developer Account verification, public Privacy Policy hosting.

Full session detail: `docs/session-logs/session-180-2026-04-26.md`. Commit: `04c99e3`.

---

## Session 179: 2026-04-26 — Tour routing unified to live engine + bake-time edge splitting

Two shipped pieces. **P1:** runtime tour polyline now computed via the same `Router.route()` against the bundled Salem walking graph that powers point-to-point "Get directions" — pre-baked `tour_legs` / `routeToNext` from S178 P6 are no longer consulted, gold tour line replaced with green + dark-green-border + intersection markers identical to point-to-point, `WalkingDirections.getBundledTourRoute()` removed. **P2:** operator field-tested directions to Phillips House (34 Chestnut St) drew a polyline that didn't enter Chestnut St; root-caused to TIGER's intersection-only vertex layout (95m gap to nearest walkable vertex, 108m tail approach blocked by 60m safety cap). Added bake-time edge splitting in `bake-salem-routing.py` — long edges >60m split into ~40m sub-segments with synthetic mid-edge walkable nodes; graph went from 16,237 edges / 12,756 nodes to 47,704 / 44,223 (asset 5 → 11.2 MB, bake 0.8s). Phillips House snap 95m → 30m, operator confirmed "working very well" on Lenovo. Carry-forward: option 2 (runtime mid-edge projection) for the residual ~30m, plus walk-sim + DebugEndpoints `TourRouteLoader` cleanup, then full retirement of `TourLegBaker` / `tour_legs` / `routeToNext`.

Full session detail: `docs/session-logs/session-179-2026-04-26.md`. Commit: `e86de97`.

---

## Session 178: 2026-04-25/26 — P6 tour-leg pre-bake + :routing-jvm extraction + surgical OSM pedestrian ingest

S175's last carry-forward shipped: tour-leg polylines pre-baked from the same TigerLine bundle the runtime Router uses (P6). New `:routing-jvm` module extracted from `:core` so both Android and the JVM `:salem-content` pipeline consume one routing engine; `salem-content` grew a `TourLegBaker` that writes the new `tour_legs` table AND regenerates the runtime `assets/tours/*.json` files (replacing S125's OSRM-baked content). Live "Get directions" routes got first/last-approach segments capped at 60m (over-water fix); for pier/peninsular destinations TIGER doesn't reach, surgical OSM ingest of 11 hand-picked osm_ids (Derby Wharf, Seven Gables waterfront, Charter Cemetery interior paths, Salem Willows) with verified per-feature TIGER hookup — Derby Wharf Light snap dropped 349.3m → 5.3m, Charter Cemetery 83.8m → 5.5m. Two new memories: `feedback_v1_no_external_contact.md` (Android app makes ZERO outside contact except GPS — V1 mandate), `feedback_internal_db_single_source.md` (internal DB is canonical; web admin may validate against PG, runtime app makes no validation calls); `feedback_no_osm_use_local_geo.md` got an addendum that allows surgical-allowlist OSM ingest when nothing internal covers a feature, and forbids broad bbox imports (the first attempt produced 608 disconnected components and broke 57 tour legs before rollback).

Full session detail: `docs/session-logs/session-178-2026-04-25.md`. Commit: `a7d7b76`.

---

## Session 177: 2026-04-25 — Web walking router + Leaflet Directions UI

Two final S175 carry-forwards shipped. **P4:** new `cache-proxy/lib/salem-router.js` is a JS port of `RoutingBundle.kt` + `Router.kt` reading the same `salem-routing-graph.sqlite` the APK ships — CSR adjacency, expanding-ring grid KNN with planar SRID-4269 distance, binary-heap Dijkstra, undirected pedestrian graph. Endpoints: `GET /salem/route?from_lat&from_lng&to_lat&to_lng[&source=live|bundle]`, `POST /salem/route-multi`, `GET /salem/route/meta`. `?source=live` falls through to `tiger.route_walking()` over a lazy-init unix-socket pg.Pool (peer auth) and stitches the returned MultiLineString into travel order by walking endpoints. Smoke-tested against the four `RouterParityTest.kt` reference fixtures — all four match bit-exact (Common→7G 1240.7187 m, Commuter Rail→Museum Place 509.8629 m, Witch House→Burying Point 413.7483 m, Peabody→Derby Wharf 1078 m sanity). Bundle vs live cross-check produces ±0 mm; the `DIVERGENCE` warning would fire above 5%. **P5:** new optional `onShowDirections` prop on `PoiEditDialog` adds a Directions button; AdminLayout owns the `directionsTarget` state; AdminMap renders `<DirectionsLayer>` (Polyline #22C55E w/ #0f5132 outer casing, origin CircleMarker, fits bounds once per target+source) and a `<DirectionsPanel>` (distance/duration/pace + bundle/live source toggle + close ×). TS clean, Vite production build clean, end-to-end data path verified through the Vite `/api` → cache-proxy `/salem` proxy. Visual click-flow verification deferred to operator. The S175 five-task queue (P3b/P3c/P2c/P4/P5) is now fully drained.

Full session detail: `docs/session-logs/session-177-2026-04-25.md`. Commit: `0c43131`.

---

## Session 176: 2026-04-25 — Live re-routing + MBTA-origin walk-sim + Router JVM parity tests

Three S175 carry-forwards shipped on the on-device walking router. **P3b (live re-routing):** new `DirectionsSession` class watches the location stream during an active route, fires a recompute after >25 m drift sustained for 2 consecutive fixes, and clears the route on arrival within 15 m of destination. Wired into `TourViewModel` so every GPS update flows through the session; mute flag prevents re-entry while a reroute is in flight. Verified on Lenovo TB305FU via new deterministic `/directions-session-test` endpoint (7 scripted fixes, all pass: drift detection requires two consecutive off-path fixes, single-fix tolerated, arrival fires inside 15 m). **P3c (MBTA-origin walk-sim):** when `lastGpsPoint` is >3 km from MBTA Salem station, `walkTo(POI)` now forks to `startSimulatedWalkFromMbta` — computes the bundled-router route from the station to the POI, publishes it as the active walking route (so the P3b session arms automatically), and drives a `walkSimAlongPolyline` simulator at 1.4 m/s. Lenovo verification (Boston as fake origin → 7 Gables): bundled route 1510 m / 11 turns, simulator interpolated to 1013 steps, step counter advancing cleanly at 1 Hz. In-Salem branch verified untouched. **P2c (Router JVM parity tests):** new `core/src/test/` source set with `JdbcRoutingBundleLoader` (sqlite-jdbc, JVM-only mirror of `RoutingBundleLoader`) and `RouterParityTest` (10 tests, all pass: four S175 reference routes pinned bit-exact to ±1 mm, plus degenerate same-point, KNN snap, multi-stop concat, pace→duration). Carry-forward to S177: P4 (web router in cache-proxy consuming the same SQLite bundle) and P5 (web Leaflet directions UI).

Full session detail: `docs/session-logs/session-176-2026-04-25.md`. Commit: `d2ad291`.

---

## Session 175: 2026-04-25 — On-device Salem walking router (TigerLine bake → APK + Directions UI)

Shipped a fully on-device walking router for the Salem app. New bake pipeline at `tools/routing-bake/` clips TigerLine's `salem.edges` + `salem.edges_vertices_pgr` to a 3-mile-buffer Salem bbox (16,226 walkable edges, 12,742 nodes, 4.1 MB SQLite bundle in `app-salem/src/main/assets/routing/`). New `:core` routing module: `RoutingBundle` (CSR adjacency + grid spatial index), `RoutingBundleLoader` (Android SQLite reader), `Router` (pure-Kotlin Dijkstra + planar SRID-4269 KNN matching TigerLine's `<->` semantic), `TurnByTurn` (per-edge bearing-change synthesis with imperial distances). Salem-app wiring: `SalemRouterProvider` lazy-loads the bundle on first call (~80-150ms warm-up); `WalkingDirections` swaps its V1_OFFLINE_ONLY straight-line fallback for real bundled-graph routing while keeping the existing `WalkingRoute` contract so `SalemMainActivityDirections` overlays + the turn-by-turn dialog inherit the upgrade with no UI change. `PoiDetailSheet`'s Directions action now calls `walkTo(GeoPoint)` instead of launching the external `geo:` intent. On-device verification on Lenovo TB305FU via new `/route-test` debug endpoint: 3 reference routes match TigerLine's live `tiger.route_walking()` to the millimetre (0.000m delta), 32ms cold first call, 4-6ms warm calls. Two memories locked: WickedMapView is the sole basemap (Esri+Mapnik dropped), MBTA Salem station is the simulated walk origin when GPS-Salem isn't available. Carry-forward to S176: live re-routing on path-drift, walk-sim FAB integration, web-side router in cache-proxy with `?source=live` override, web Leaflet polyline UI, and tour-leg pre-bake.

Full session detail: `docs/session-logs/session-175-2026-04-25.md`. Commit: `186a89a`.

---

## Session 174: 2026-04-25 — Web admin tour editor (full CRUD + drag-to-reposition)

Shipped a brand-new Tours tab in the web admin tool so tours can be edited end-to-end ahead of post-TigerLine-cleanup rerouting. Schema migration added `stop_id` PK, nullable `lat`/`lng` (per-tour coord override), and nullable `name`/`poi_id` to `salem_tour_stops`; effective coord at read time = `COALESCE(stop.lat, poi.lat)`. New `cache-proxy/lib/admin-tours.js` adds 7 admin endpoints — list/get tours, create/patch/delete tour, add/patch/delete/reorder stops — with auto-resync of `salem_tours.stop_count` on every stop mutation. Frontend: new `TourTree.tsx` (create form, per-row delete, metadata edit form, waypoint list with ↑/↓/🗑, "+ Free waypoint" map-click-to-add mode, "+ POI as stop" pick-marker mode), generalized `MoveConfirm` modal, new `TourStopLayer` (numbered draggable markers + dashed connecting polyline + amber-vs-indigo for override-vs-fallback), `FitTourBounds`, `MapClickAddListener`. Operator parked at session end pending TigerLine database cleanup before doing the actual rerouting.

Full session detail: `docs/session-logs/session-174-2026-04-25.md`. Commit: `42f4698`.

---

## Session 173: 2026-04-25 — Cemetery firefly alignment investigation (no-op session, reverted)

Investigated the Quaker cemetery firefly bug — fireflies were rendering in a void next to the visible cemetery polygon, not on it. Root cause traced to a polygon-source mismatch: the Witchy basemap renders cemeteries from OSM data (via the planetiler tile bake), while our firefly polygon library queries `massgis.openspace prim_purp='H'`, which has only a 1455 m² 4-vertex sliver in the wrong place for `gid=27152` "Essex Street Cemetery". Tried two fixes — exclusion of the bad gid (operator reversed), then a B1+B2 source-merge (prefer `massgis.landuse lu37_1999=34` when area-comparable, plus a side-car JSON of hand-corrected overrides for problem cases). B1 made the OTHER ~46 cemeteries' polygons drift out of alignment with the basemap render — landuse=34 polygons disagree with OSM more than openspace does despite being the more specific MassGIS class. Operator instructed full revert. Net code change this session: zero. Quaker cemetery firefly bug remains parked; the per-feature override mechanism is a known path forward when operator wants to address it surgically.

Full session detail: `docs/session-logs/session-173-2026-04-25.md`. Commit: pending.

---

<!-- END OF ROLLING WINDOW — Sessions 172 and earlier are in SESSION-LOG-ARCHIVE.md -->
<!-- S172 rolled to archive 2026-04-26 by the session-end protocol (S182) -->
<!-- S171 rolled to archive 2026-04-26 by the session-end protocol (S181) -->
<!-- S170 rolled to archive 2026-04-26 by the session-end protocol (S180) -->
<!-- S169 rolled to archive 2026-04-26 by the session-end protocol (S179) -->
<!-- S168 rolled to archive 2026-04-26 by the session-end protocol (S178) -->
<!-- S167 rolled to archive 2026-04-25 by the session-end protocol (S177) -->
<!-- S166 rolled to archive 2026-04-25 by the session-end protocol (S176) -->
<!-- S165 rolled to archive 2026-04-25 by the session-end protocol (S175) -->
<!-- S164 rolled to archive 2026-04-25 by the session-end protocol (S174) -->
<!-- S163 rolled to archive 2026-04-25 by the session-end protocol (S173) -->
<!-- S162 rolled to archive 2026-04-25 by the session-end protocol (S172) -->
<!-- S161 rolled to archive 2026-04-25 by the session-end protocol (S171) -->
<!-- S160 rolled to archive 2026-04-24 by the session-end protocol (S170) -->
<!-- S159 rolled to archive 2026-04-24 by the session-end protocol (S169) -->
<!-- S158 rolled to archive 2026-04-24 by the session-end protocol (S168) -->
<!-- S157 rolled to archive 2026-04-24 by the session-end protocol (S167) -->
<!-- S160 rolled to archive 2026-04-24 by the session-end protocol (S170) -->
<!-- S159 rolled to archive 2026-04-24 by the session-end protocol (S169) -->
<!-- S158 rolled to archive 2026-04-24 by the session-end protocol (S168) -->
<!-- S157 rolled to archive 2026-04-24 by the session-end protocol (S167) -->
<!-- S156 rolled to archive 2026-04-24 by the session-end protocol (S166) -->
<!-- S155 rolled to archive 2026-04-24 by the session-end protocol (S166) -->
<!-- S154 rolled to archive 2026-04-24 by the session-end protocol (S164) -->
<!-- S153 rolled to archive 2026-04-24 by the session-end protocol (S163) -->
<!-- S152 rolled to archive 2026-04-23 by the session-end protocol (S162) -->
<!-- S151 rolled to archive 2026-04-23 by the session-end protocol (S161) -->
<!-- S150 rolled to archive 2026-04-23 by the session-end protocol (S160) -->
<!-- S149 rolled to archive 2026-04-23 by the session-end protocol (S159) -->
<!-- S148 rolled to archive 2026-04-23 by the session-end protocol (S158) -->


