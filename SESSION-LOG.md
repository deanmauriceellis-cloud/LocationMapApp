# LocationMapApp — Session Log

> **Rolling window — last 10 sessions only.** On every session end, the oldest session is moved to `SESSION-LOG-ARCHIVE.md`. This file currently holds Sessions 177-186. Everything older lives in the archive (which itself ends with the original v1.5.0–v1.5.50 archive at the bottom).
>
> **Per-session live conversation logs** (the canonical, append-only record with full reasoning, decisions, file diffs, build results) live in `docs/session-logs/session-NNN-YYYY-MM-DD.md`. The entries in this file are 2-3 sentence summaries — pointers to the live logs, not replacements.

## Session 186: 2026-04-26 — Tour Mode narration gate + mode-dependent Layers checkboxes (POIs Civic / POIs Hist. Landmark)

Replaced the missing Tour Mode narration contract. Pre-S186 a tour silently narrated everything in proximity (no gate); operator wanted "during a tour, only TOUR POIs narrate by default; Layers checkboxes opt-in additional classes." Built: new `is_civic_poi` column on `salem_pois` (Room v10 → v11, identity_hash `55b35822253369a8af9f91e8bdbf8656`), bulk-flagged 157 CIVIC POIs; bulk-flagged 164 more rows as `is_tour_poi=true` (WORSHIP/PARKS_REC/EDUCATION + 8 HIST_BLDG museums) so 216 POIs are now always-narrate-during-tour; new "POIs Civic" checkbox in the Layers menu beside the existing "POIs Hist. Landmark"; `NarrationGeofenceManager.setTourMode(active, allowHistLandmarks, allowCivic)` short-circuits `isHistoricalQualified` when active. Operator reframed twice mid-session: (1) Layers checkboxes have separate explore vs tour state (PREF_POI_HIST_LANDMARK explore default ON, PREF_POI_HIST_LANDMARK_TOUR default OFF, same for civic) — explore = "Explore Salem" permissive default, tour = restrictive default, each remembers independently; (2) `is_tour_poi=true` is the force-visible AND force-audible override — Layers checkboxes only add/remove the *additional* class, never silence or hide the tour-flagged baseline. Post-AAB field test surfaced two bugs: (a) marker filter wasn't honoring is_tour_poi short-circuit so HIST_BLDG-categorized tour POIs (e.g. First Church Meetinghouse 1692 Site) disappeared when Hist Landmark checkbox went OFF — fixed; (b) walk-sim start was unconditionally calling `narrationGeofenceManager.resetSession()` which wiped the 1-hour dedup state, so the same POI re-fired on every walk-sim restart — operator rule is "only cold start clears dedup," removed the call. Web admin: `is_civic_poi` added to PoiEditDialog Operational checkboxes + cache-proxy admin-pois.js whitelist + poiAdminFields.ts. New canonical publish chain: `publish-salem-pois.js` (now writes is_civic_poi) → `publish-tours.js` → `publish-tour-legs.js` → `align-asset-schema-to-room.js` (last word — auto-discovers latest schema JSON, stamps identity_hash + user_version). Carry-forward: `publish-tour-legs.js` still has a hardcoded v10 hash (overwritten by align script but a footgun); ENTERTAINMENT (207) + most LODGING (24/33) lack `year_established` so the Hist Landmark override only narratable set is currently 79 POIs until operator backfills year data; 8 newly tour-flagged museums have no narration text yet. S185 carry-forwards still apply (onboarding-to-nearest-point, GPS-OBS heartbeat, Tier 2 publish-chain auto-bake, dedup-loser hard-delete, re-author 5 deleted Kotlin tours). Operator close: "good enough, session end and we continue next session with refinement."

Full session detail: `docs/session-logs/session-186-2026-04-26.md`. Commit: `<pending>`.

---

## Session 185: 2026-04-26 — Land S183/S184 walking-route work in the AAB; flush latent asset-DB schema landmines

The S183/S184 operator-curated `tour_WD1` is now actually in the AAB on the Lenovo. New Room `TourLeg` entity (v9 → v10), new `cache-proxy/scripts/publish-tour-legs.js` (PG `salem_tour_legs` → asset's `tour_legs`), `TourViewModel.computeTourPolyline()` rewritten to read baked legs and assemble them with the same anchor + clip rendering policy as the admin's `TourLegsLayer.drawLeg`. Also rewired walk-sim's route loader (was falling back to the bundled "Downtown Salem" route because tour_WD1 has no JSON in `assets/tours/` and no POI-bound stops). Operator clarified the V1 model mid-session: tours are polyline-only paths users follow; POIs govern their own narration via geofence proximity, fully independent of any tour. `TourEngine.startTour` no longer errors on stop-less tours, and Historical Mode is auto-skipped when the active tour has zero user-facing stops (was building an empty whitelist that silenced every POI on the route, which is what hid Ames Memorial Hall during the operator's first walk-sim test). Two latent asset-DB landmines flushed during the install loop: (1) tables had `DEFAULT` clauses from `salem-content/create_db.sql` while Room codegen has none → `TableInfo.equals` mismatch → `fallbackToDestructiveMigration` wiped the asset on every fresh install; (2) `PRAGMA user_version` lagged the `@Database(version=N)` value → upgrade migration ran → fallback destructive. New `cache-proxy/scripts/align-asset-schema-to-room.js` is the canonical bridge — rewrites every Room-managed table using the exact `createSql` from `app-salem/schemas/<DB>/<v>.json` and stamps both `identity_hash` and `user_version`. The 5 historical Kotlin-curated tours (Essentials/Explorer/Grand/Witch Trials/Heritage Trail) were dropped from the asset; operator will re-author them in PG via admin when wanted. Admin polish at session end: POI markers at zoom ≥17 now show humanized category above + name below (mirrors Android `MarkerIconHelper.labeledDot`), cluster threshold lowered to 17 to match. Carry-forward (S186): tour-start onboarding to nearest point on polyline; investigate Lenovo's GPS-OBS heartbeat (system delivers fixes but app's tour observer reports them stale); hard-delete dedup losers; Tier 2 admin → build pipeline auto-bake (now critical with 3 publish scripts).

Full session detail: `docs/session-logs/session-185-2026-04-26.md`. Commit: `3c68102`.

---

## Session 184: 2026-04-26 — Admin walking-route UX polish: marker-anchored polylines + click-to-highlight + click-to-recenter

Continuation of S183's authoring pipeline — pure web admin UX, zero backend or bake changes. Diagnosed the operator's "leg 1 → 2 missing" report as a marker-vs-polyline gap (the on-device router snaps each waypoint to the nearest pedestrian-graph node, leaving 1.1–25.1 m visible gaps at the dots), and shipped: (1) `TourLegsLayer` now anchors each leg's polyline to its from/to marker coords AND clips the routed geometry to the polyline-points closest to each marker, so road-snap gaps disappear AND overshoot/corner-and-back doglegs (e.g. leg 4 ending 16 m past waypoint 5) get trimmed; (2) click a leg row in the side panel → that leg highlights red on the map (custom Leaflet pane at z-index 450 so it sits above `overlayPane`'s dashed inter-stop connector but below `markerPane`'s numbered waypoint dots); click anywhere on a leg polyline also selects the matching row; (3) click a waypoint row → map flies to the stop (zoom ≥18, repeat-clicks re-fire via a nonce); (4) cancel for "+ Free waypoint" / "+ POI as stop" modes — × button on the amber banner, Esc key handler, "(Esc to cancel)" hint. One Rules-of-Hooks bug (Esc `useEffect` declared after the early-return guard) caused a white-screen on refresh; fixed by moving the effect above the guard. Surfaced two routing-graph oddities — legs 10 and 12 of `tour_WD1` were 2082 m / 1850 m for waypoints 80–108 m apart because stops 10–13 sat on/inside Salem Common where the pedestrian graph has no edges; per `feedback_tour_routing_is_content_not_engineering.md`, operator dragged those waypoints onto streets and the legs went sane. Files: `web/src/admin/AdminMap.tsx`, `web/src/admin/AdminLayout.tsx`, `web/src/admin/TourTree.tsx`. TypeScript clean throughout. App-side bake / `TourViewModel` switch / Lenovo smoke-test items still queued.

Full session detail: `docs/session-logs/session-184-2026-04-26.md`. Commit: `32ad045`.

---

## Session 183: 2026-04-26 — Tour walking-route content tool: per-leg compute + admin overlay

Different path from S182's "hand-curate stop coords" framing. Operator created a fresh `tour_WD1` (15 free waypoints) and asked for a one-click admin tool to convert waypoints into per-leg walking polylines, persist them, and render the whole path as a saved overlay. New PG table `salem_tour_legs` (per-leg `polyline_json` JSONB + distance/duration + `router_version` + nullable `manual_edits` JSONB), 3 new admin endpoints under `/admin/salem/tours/:tour_id` (`GET /legs`, `POST /compute-route` with manual-edits preservation unless `force:true`, `POST /legs/:leg_order/recompute`). `cache-proxy/server.js` captures the salem-router module into `deps.salemRoute`/`deps.salemBundle` so admin-tours calls the same in-process bundle Find→Directions uses (44,223 nodes / 47,704 edges). Web admin: new "Walking route" panel above the Waypoints list (Compute / Recompute, Force-all, per-leg list with distance + ↻ recompute, stale indicator), and a green outlined polyline overlay on the Leaflet map matching the in-app `DirectionsLayer` style. CLI verification: `tour_WD1` computed cleanly to 14 legs / 6815.5 m / 4868.2 s, no skipped legs, router_version `2026-04-26T04:47:09+00:00`. TypeScript clean (`tsc --noEmit` exit 0); Vite HMR fired through TourTree → AdminLayout → AdminMap. Salem-content bake to include `salem_tour_legs` and the app-side switch (`TourViewModel.computeTourPolyline()` reads baked legs first, falls back to runtime `routeMulti`) are deferred to follow-up sessions — that's what closes the loop into the AAB. S182's content-not-engineering rule still holds: route work is now entirely authoring-time (admin one-click), zero runtime engine change in the APK.

Full session detail: `docs/session-logs/session-183-2026-04-26.md`. Commit: `d943595`.

---

## Session 182: 2026-04-26 — S181 OSM-policy reversal RETRACTED; tour-rendering declared content-not-engineering

Operator opened with "I don't like your solution from the last session" — pushed back on S181's OSM-merge plan after testing P2P "tap POI → directions" between two Heritage Trail stops and getting a clean route, which the multi-stop tour render of consecutive stops can't reproduce. Traced both code paths: `Router.routeMulti` is literally per-leg `route()` calls + `concat()`, byte-for-byte equivalent to N P2P calls. The actual divergence is in the input — tour legs feed POI centroids (often inside/behind a building polygon, KNN snap can land on the wrong side), P2P feeds the user's GPS (already on a sidewalk). Same algorithm, different start node, different shortest path, one crosses the building and one doesn't. S181's proposed broad OSM pedestrian merge wouldn't have fixed it. Reverted: memory `feedback_no_osm_use_local_geo.md` restored to S178 surgical-only constraint, MEMORY.md index reverted, `docs/plans/S182-osm-pedestrian-routing-merge.md` archived to `docs/archive/S182-osm-pedestrian-routing-merge_archived_2026-04-26-misdiagnosis.md` with banner, CLAUDE.md pinned next-steps block reframed. Feasibility-checked an address-anchored alternative — `salem_pois.address` has 97.5% coverage, TIGER `lfromadd/ltoadd/rfromadd/rtoadd` interpolation works (sample "237 Essex St" geocoded to 12 m offset on Essex St centerline) — but ≈half the Heritage Trail stops have addresses that fall through (intersection / square-only / no-number / TIGER coverage gap), so the resolver would need a layered fallback. Operator rejected the whole engineering path: "I need to do that tour by hand, I don't want to muck more with the mapping, wasting too much time on that." Saved a new HARD RULE memory `feedback_tour_routing_is_content_not_engineering.md` to prevent this loop in future sessions. Cache-proxy (4300) and Vite admin (4302) brought up at session end for the operator's curation work. **No code changes shipped this session.**

Full session detail: `docs/session-logs/session-182-2026-04-26.md`. Commit: `9efc931`.

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

<!-- END OF ROLLING WINDOW — Sessions 175 and earlier are in SESSION-LOG-ARCHIVE.md -->
<!-- S175 rolled to archive 2026-04-26 by the session-end protocol (S185) -->
<!-- S174 rolled to archive 2026-04-26 by the session-end protocol (S184) -->
<!-- S173 rolled to archive 2026-04-26 by the session-end protocol (S183) -->
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


