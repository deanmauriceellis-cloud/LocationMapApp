# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — must stay under 200 lines.

**Last updated:** 2026-04-27 — Session 191 closed. Tour-flag bulk-fix + admin-tool polish + a hidden-POI narration bug **diagnosed but not yet patched**. Bulk-flipped 77 curated `HISTORICAL_BUILDINGS` rows to `is_tour_poi=true` so the curated bucket is now 118/118 (MassGIS bucket stays 0/487 per S186); added `hist_curated_not_tour` lint check. Cleared 2 stale `is_civic_poi=true` flags on rows whose category had been re-set to `HISTORICAL_BUILDINGS` (Witch House at Salem, Howard Street Cemetery — leftover from S186 bulk civic flag); added `civic_flag_mismatch` lint check (`is_civic_poi=true AND category<>'CIVIC'`). Two new synthetic admin tree categories `SOURCE:destination_salem` / `SOURCE:haunted_happenings` cross-cut real categories and prepend at the top of `PoiTree`. Per-operator data cleanup: soft-deleted 67 POIs whose provenance was solely `destination_salem` and/or `haunted_happenings` (no OSM token); stripped those tokens from 63 OSM-bearing survivors via `regexp_replace`. Asset DB shrank 9.2 MB → 9.0 MB. Two full publish chains + APK reinstalls on Lenovo HNY0CY0W during the session. Fixed RHF reset race in `PoiEditDialog.tsx` (`useMemo(defaultValues, [poi.id])` instead of `[poi]`) so unsaved checkbox toggles survive a marker drag. **NEW carry-forward — primary S192 priority**: hidden POIs are still narrating. `app-salem/.../SalemPoiDao.kt:43` `findNarrated()` query gates on `is_narrated=1` only, ignoring `default_visible`; 1,155 hidden-but-narrated POIs (489 hist-bldg, 197 OFFICES, 147 HEALTHCARE, 133 CIVIC, 82 SHOPPING, 52 AUTO_SERVICES) load into `NarrationGeofenceManager.points` and fire ENTRY events in explore mode. Live evidence: 6 of last 10 narrations on device were `default_visible=false`. Fix proposed (`AND default_visible=1`, with a `OR is_tour_poi=1` carve-out for the 55 tour POIs that are intentionally hidden) but operator session-ended before applying. Files NEW: `docs/session-logs/session-191-2026-04-27.md`. Files MODIFIED: `cache-proxy/lib/admin-lint.js`, `web/src/admin/{PoiEditDialog.tsx, PoiTree.tsx}`, `app-salem/src/main/assets/salem_content.db`, `salem-content/salem_content.db`. Full detail in `docs/session-logs/session-191-2026-04-27.md`.

**Previous (S190):** Substantive admin-tool feature session driven by parallel V1-launch-review work. Built strict POI Category/Subcategory dropdowns sourced from `salem_poi_categories` / `salem_poi_subcategories` with inline `+ Add new` panels (new `cache-proxy/lib/admin-categories.js` for taxonomy CRUD). Split `HISTORICAL_BUILDINGS` tree into synthetic "Historic Buildings" (curated, 126) vs "POI Hist. Landmark" (MassGIS MHC, 487). Reworked subcategory rendering — every canonical subcategory shows under its parent (including empty `(0)`) plus a `(no subcategory)` bucket for unassigned POIs. Renamed `OFFICES` DB label `'Offices & Services'` → `'Professional Services'` and refactored `categoryLabel` so tree, dropdown, legend, and filter banner all read from one source (DB row label). New top-level **Geocodes** tab promoting the lint-tab's address-geocode deep scan. Tour-leg routing diagnostics added to `cache-proxy/lib/admin-tours.js` (per-leg `detour_ratio`, `point_count`, `suspicious` flag, snap diagnostics on skip via new `salemRouteDiag` helper) — surfaced as amber "⚠ N suspicious legs" + skipped-leg banners in `TourTree`. Diagnosed `tour_Density` failures: Charter Cemetery had 7 OSM pedestrian features whose endpoints sat at the EXACT same lat/lng but got different vertex IDs (3 different node IDs at `42.5206924, -70.8918647`), so paths physically meeting at junctions were routing-disconnected. Fix in `tools/routing-bake/bake-salem-routing.py` — two passes BEFORE the existing S179 edge-split: `merge_coincident_nodes(tol=3m)` fuses near-coincident vertices (lowest-id wins, drops self-loops, dedups parallel edges keeping shortest); `bridge_isolated_components(tol=60m, max_island=30)` adds one synthetic `mtfcc=SYNTH_BRIDGE` walkable edge per small island to the closest main-component node. Connected components 49→46→14. `tour_Density` 4 legs/3 skipped → 9 legs/1 skipped. No Kotlin changes — `Router.kt` treats `mtfcc` as opaque metadata. Saved memory `feedback_routing_build_engineering_allowed.md` scoping the older "tour routing is content not engineering" rule to runtime engine only — build-pipeline graph-data fixes are explicitly allowed when the admin tool can't compute legs. Full publish chain ran (publish-salem-pois → publish-tours → publish-tour-legs → align-asset-schema-to-room) and APK installed on Lenovo HNY0CY0W via uninstall+install. `verifyBundledAssets` flagged 9 commercial-hero leaks (POIs with `merchant_tier=0` carrying hero images) — pre-existing, bypassed `-x verifyBundledAssets` for dev build only. **NEW carry-forward**: clean those 9 leaks before any signed AAB upload (see TOP PRIORITY 1b below). Files NEW: `cache-proxy/lib/admin-categories.js`, `web/src/admin/GeocodesTab.tsx`, `docs/session-logs/session-190-2026-04-27.md`. Files MODIFIED: cache-proxy/{server.js, lib/{salem-router.js, admin-tours.js}}, tools/routing-bake/bake-salem-routing.py, web/src/admin/{AdminLayout.tsx, AdminMap.tsx, PoiEditDialog.tsx, PoiTree.tsx, TourTree.tsx, tourTypes.ts}, app-salem/src/main/assets/{routing/salem-routing-graph.sqlite, salem_content.db}, salem-content/salem_content.db. Full detail in `docs/session-logs/session-190-2026-04-27.md`.

**Previous (S189):** Diagnostic + planning session, no app or admin code changed. Synthesized a 10-section total launch review and built `docs/V1-LAUNCH-REVIEW-2026-04-27.odt` (13 KB) as an annotatable intake document. Operator's annotated copy drives S190+ planning. Critical findings flagged: Form TX copyright 23 days out and unconfirmed; 2026-04-20 lawyer meeting outcome not in any session log; privacy policy `[TBD]` placeholders unresolved; Play Console developer account not started; 365 dedup losers still soft-deleted in PG awaiting hard-delete pre-AAB; V1 feature-gating eyes-on smoke test from S180 still owed; single-medium upload-keystore backup.

**Previous (S188):** Polish pass on S187's Geocode-candidates modal + a Witchy-only basemap with server-side auto-overzoom. The Geocodes modal now shows every duplicate's address/coords, geocodes each duplicate's address (cluster-wide best-match selection), and offers a per-candidate **Show on Map** that opens an in-map preview (purple POI dot + fuchsia "?" candidate marker + dashed line) with a smart `analyzeGeocodeConflict()` diagnoser that labels each case (good / warn / bad) and recommends one of five action buttons (Move POI / Validate stored / Hide candidate / Edit address / Cancel). New `salem_geocode_blacklist` PG table backs the **Hide this candidate** action — flagged candidates stop appearing in future Geocodes lookups for that POI. The preview panel can be escaped with `×` or `Esc`, has a Focus toggle that hides every other POI marker, a Show-all-candidates toggle that renders every Tiger hit across the cluster color-coded by rating, and a Re-fit button. Mapnik / Esri / OSM removed from the basemap picker (Witchy is the only basemap); `cache-proxy/lib/admin-tiles.js` gains `sharp`-backed auto-overzoom that walks UP up to 8 ancestor levels (crop+resize) or DOWN up to 2 child levels (stitch) when a (z,x,y) isn't baked, with a 500-entry LRU and `X-Tile-Source` headers for devtools introspection. Modal-hang regression fixed — soft-deleted POIs filtered out of dupe clusters (operator: "soft deleted are not concerns, just footnotes"), per-call Tiger timeout 15→5 s, 20 s wall-clock budget across the whole geocode-candidates call. Cache-proxy restarted on PID 68413 with everything live. Files NEW: none. Files MODIFIED: `cache-proxy/lib/{admin-lint.js, admin-tiles.js}`, `cache-proxy/package.json` (+sharp), `web/src/admin/{GeocodeCandidatesModal.tsx, AdminMap.tsx, AdminLayout.tsx}`. Full detail in `docs/session-logs/session-188-2026-04-26.md`.

S187 standing (still valid): Admin Lint tab is the 4th view, 15 instant checks + on-demand address-geocode deep scan, Tiger MA fully loaded (~322 MB), `salem_lint_suppressions` for false-positive flagging. None of S188 changed the lint check set or counts.

S186 standing (still valid): Tour Mode narration gate + mode-dependent Layers checkboxes (POIs Civic / POIs Hist. Landmark) + `is_civic_poi` Room v11 + 1-hour dedup persists across walk-sim restarts + `is_tour_poi=true` force-visible-and-force-audible. None of S187 touched the Android side. Detail in `docs/session-logs/session-186-2026-04-26.md`.

S184 standing (still valid): Admin walking-route UX polish (anchor + clip + click-to-highlight + click-to-recenter + cancel for + Free / + POI modes). The same anchor + clip algorithm now runs in the app's tour overlay too (S185).

S183 standing (still valid): Per-leg tour walking-route admin tool (backend + UI). PG table `salem_tour_legs` + 3 cache-proxy endpoints. App-side consumption now landed (S185).

S180 standing (still valid): V1 Play Store gating chores DONE end-to-end. First signed AAB at `app-salem/build/outputs/bundle/release/app-salem-release.aab` (78 MB) — superseded by S185 rebuild but signing config + version code (10000) unchanged. Manifest network surface stripped, V1 feature gates R8-stripped, Room migrations locked, signingConfigs wired, upload keystore generated + OMEN-registered. S179: tour routing unified to live `Router.route()` against bundled walking graph; bake-time edge splitting (47,704 edges / 44,223 walkable nodes). S178: `:routing-jvm` extraction + 60m approach cap + surgical OSM ingest. S175: on-device Salem router. S174: web admin Tours tab CRUD. S172: animations live in SalemMainActivity. S171: custom WickedMap engine (replaces osmdroid; migration in progress, 338 osmdroid call sites still extant).

---

## TOP PRIORITY — Next Session (S192)

0. **Read operator's annotated `docs/V1-LAUNCH-REVIEW-2026-04-27.odt` first.** This intake document defines next-steps priorities and supersedes the engineering-only ranking below. Items 1–12 below remain valid as a backlog but should be re-ordered against the operator's annotated launch-review action plan (Week 1 / 2 / 3–4 / Month 2+ in §9 of the .odt). Critical operator-side blockers from the review are also tracked here as items 13–15 because they're hard-deadline-driven.

0a. **HIDDEN-POI NARRATION FIX (S191 carry-forward).** `app-salem/.../content/dao/SalemPoiDao.kt:43` — `findNarrated()` ignores `default_visible`. 1,155 hidden-but-narrated POIs are loaded into `NarrationGeofenceManager.points` and fire ENTRY events in explore mode (Layers gate only applies in tour mode per S186). Operator confirmed the bug live: 6 of last 10 narrations were `default_visible=false`. Proposed query: `WHERE is_narrated = 1 AND (default_visible = 1 OR is_tour_poi = 1)` — the OR carves out the 55 intentionally-hidden tour POIs. Republish chain + APK reinstall after the change.

1b. **PRE-AAB — clean the 9 commercial-hero leaks** (S190 surfaced). Either NULL out `image_asset` on the 9 POIs flagged by `verifyBundledAssets`, or move them to `merchant_tier=1`. The check was bypassed `-x verifyBundledAssets` for the S190 dev-build to Lenovo, but it's a hard gate for any signed AAB upload. List captured in S190 build log: `real_pirates_salem`, `salem_arts_association_3`, `schooner_fame_of_salem_2`, `historic_salem_inc`, `stepping_stone_inn`, plus 4 UUID-named hero/ entries (3 ENTERTAINMENT, 1 LODGING).

13. **Form TX copyright filing — hard deadline 2026-05-20 (23 days from 2026-04-27).** $65, ~10 minutes online. File as Dean Maurice Ellis individually if entity not yet formed; assign later. Missing this loses statutory damages eligibility for any infringement during the launch window.

14. **Play Console developer account application — multi-week ID-verification lead time.** $25 + government-issued ID. Independent of legal entity setup. Must start ASAP to not gate the Sept 1 ship target.

15. **Privacy policy `[TBD]` placeholders + 2026-04-20 lawyer meeting outcome.** Four placeholders (operating entity, contact email, mailing address, jurisdiction) blocking Play Console submission. Counsel meeting was 2026-04-20 (now 7 days past) — outcome not in any session log. Operator must confirm.

---

1. **Use the Lint tab + S188 map preview to drive content cleanup.** Same backlog as S188's intended TOP PRIORITY, now with sharper tooling. In rough order: backfill `year_established` on 500+ Historical Buildings (Lint → Open Editor); author narration for the 30 silent Civic POIs and the 8 museum tour-flags; hard-delete the 365 dedup losers pre-AAB; run the address-geocode deep scan and walk through results using the new map preview (Show on Map → Move POI / Validate / Hide / Edit). The Geocodes modal now also feeds the `salem_geocode_blacklist` table — every "Hide this candidate" decision persists across sessions.

2. **Onboarding-to-nearest-point on tour start.** Operator request from S185: when user picks a tour and isn't on the polyline, route them to the nearest point on it before walk mode begins. Code path: `TourViewModel.startTour()` → fetch `tour_legs` → flatten polyline → find nearest segment to user's GPS → call `walkingDirections.getRoute(userLoc, nearestPoint)` → publish that as the active directions session. When user reaches it, switch to "tour-walking" mode (no live directions; user just follows the static polyline + POI geofence narration).

3. **GPS-OBS heartbeat investigation.** Pre-existing Lenovo TB305FU quirk surfaced during S185 walk-sim debug: system GPS is delivering fixes (visible at `LocationManagerService` level) but the app's tour observer reports them as stale (`HEARTBEAT STALE — last fix 33s ago, narration reach-out suppressed`). Same family as the broken-motion-sensor issue noted in `feedback_lenovo_motion_sensor_broken.md`. Not introduced by S185, but compounds the "narration didn't fire" experience when walk-sim isn't active. Investigate why fresh fixes aren't reaching `lastFixAtMs` on this device.

4. **Tier 2 — admin → build pipeline auto-bake** (S180 carry-forward, escalated by S185). Per `feedback_admin_changes_propagate_to_builds.md`. Wrap `publish-tours.js` + `publish-tour-legs.js` + `align-asset-schema-to-room.js` as a `preBuild`-dependent Gradle task with a "stale bake" warning when last-bake-mtime < last-admin-edit-mtime in PG. ~30-min job. Now critical since the publish chain has 3 scripts and getting any of them out of order leaves a destructive-migration footgun.

5. **PRE-AAB — hard-delete dedup loser rows. Count corrected by S187 lint: 365** (S185 estimated 110). Lint tab → Cleanup → "Soft-deleted dedup losers (pre-AAB cleanup)" or run `DELETE FROM salem_pois WHERE data_source ILIKE '%dedup%loser%' OR data_source ILIKE '%address-dedup%loser%';` (verify zero FK refs first). Surfaced again in S185: 5 "Ledger" rows in PG, 3 narrated, 2 unnarrated dedup leftovers — likely contributing to confused geofence registration.

6. **Re-author the 5 deleted Kotlin tours in PG** (S185 carry-forward). Operator wants them back as polyline-only tours via the web admin (path-only model — POIs govern narration). Each tour: insert `salem_tours` row, drop free waypoints onto the route in admin map, click Compute Route, eyeball-verify, re-run publish chain.

7. **Continue eyes-on smoke test on Lenovo** (S180/S181/S182 carry-forward). Remaining items: POI detail Visit Website ACTION_VIEW external browser handoff, Find dialog Reviews/Comments hidden in V1, Find dialog Directions on-device router visible, toolbar gating (no Weather/Transit/CAMs/Aircraft/Radar buttons), webcam dialog "View Live" hidden. Worth interleaving since the next AAB rebuilds anyway.

8. **OPERATOR-SIDE — second-medium copy of upload keystore.** One USB copy exists at `/media/witchdoctor/writable/wickedsalem-upload-key-backup-2026-04-26/`. Recommend encrypted cloud or a second USB stored elsewhere before first Play Console upload. Single-medium backups fail. Run `sudo bash ~/keys/backup-staging/copy-to-usb.sh` against a different mounted medium.

9. **Tier 3 — Content-data fixes for outlier POIs** (S178 carry-forward). Two POIs have wrong coords causing >60m snap gaps even with S179's densified graph: `salem_common_2` at (42.5203, -70.8816) is 600m from actual Salem Common (~42.5232, -70.8908); `salem_willows` at (42.535, -70.86945) is mid-parking-lot. SQL UPDATE + re-bake + rebuild AAB.

10. **OPERATOR-SIDE async** (no Claude action): Form TX copyright (lawyer handling, hard deadline 2026-05-20), Play Developer Account verification (multi-week lead time, will tackle in a later session), Privacy Policy public hosting (waiting on lawyer approval).

11. **DEFERRED from S179** (lower priority): Option 2 runtime mid-edge projection in `:routing-jvm` Router; walk-sim + DebugEndpoints `TourRouteLoader` cleanup → full retirement of S178 P6 dead data; water-aware approach segments (post-V1). **Do not propose** as part of routing-quality work — see content-not-engineering rule.

12. **DirectionsSession JVM tests** (still deferred). Convenience-not-correctness.

---

## TOP PRIORITY — Carry-forward (still owed from earlier sessions)

- **Water animation visual tuning** (S172) — operator's verdict was "not the best." Infrastructure complete (world-anchored 14185-anchor lat/lon grid, ANR-safe), but `~` curved-stroke shape doesn't sell as wave crests. Three directions documented in `docs/session-logs/session-172-2026-04-25.md`: different shape (irregular dash bundles / elliptical foam / parallel-to-coast angle), pre-rendered animated drawable (frame-anim PNG/WebP via AI Studio pipeline), or density bump (110m → 70m grid).

- **osmdroid → WickedMapView migration** (S171/S172 carry). 338 osmdroid call sites still extant in `SalemMainActivity`. Stage 1 = layout swap + simple camera/lifecycle calls; Stage 2+ = per-feature overlay migration starting with POIs. Operator accepts temporary regressions in radar/aircraft/METAR/transit during migration.

- **Field-test S169 fixes** (S168/S169/S171): walk-confirm walker dwells on every narrating POI (not just CPA winners) and exits cleanly; one-line manifest fix `android:launchMode="singleInstance"` on SplashActivity to stop launcher tap jumping past Katrina intro; flip `pickNextFromQueue` explore mode to tier-first / closest-tiebreaker (currently closest-first; spec is "historical then closest" per S169).

- **POI location verifier** (S162) — UNBLOCK by checking TigerLine MA ingest status. Schema (`lat_proposed`/`lng_proposed`/`location_status` etc.) + 3 indexes shipped. Verifier itself blocked: `tiger.addr/addrfeat/edges/featnames` were empty for `statefp='25'` in S162. Re-check; if populated, write verifier, run against 1,761 active addressed POIs.

- **S163 MHC hidden POIs companion importer** — wait for SalemIntelligence enrichment delivery, then write `tools/historical-buildings/import-si-enrichment.py` mirroring `import-to-salem-pois.py`.

- **GPS + V1-offline regression check on next drive** (S161) — pull Lenovo debug log; expected zero "POI failed" / "Fill failed" toasts, zero `findItem(...) null` warnings, fix cadence ~1-2.5s while moving (no 20-30s gaps).

- **Find type-search smoke test** (S165/S166) — install + smoke-test "dentist" / "lawyer" / "gym" / "coffee" before further Find work.

- **Witchy tile bake bbox extension OR no-coverage UX hint** (S159) — operator's home in Beverly (42.5567, -70.8717) is north of S158 bake bbox max lat 42.545. Either extend the bake or render a "pan south to Salem" hint when viewport has no tile coverage.

- **`verify-bundled-assets.js` provider list update** (S167 reversed S158). Tiles back in APK assets at `app-salem/src/main/assets/salem_tiles.sqlite` (Witchy-only, Salem city bbox). Script's check is valid again; needs updated provider list.

---

## Architectural pivots & rule changes (recent → older)

- **2026-04-26 (S185):** **Tours are polyline-only paths; POIs govern their own narration.** Operator clarification during walk-sim debug: a tour is just a line for users to follow; POI geofences fire narration based on user proximity, independent of any tour. Tour stops in PG with NULL `poi_id` are internal authoring waypoints (used by admin to compute legs) — they're filtered out of the asset's `tour_stops` table. `TourEngine.startTour` no longer errors on empty stops. Historical Mode is auto-skipped when the active tour has zero user-facing stops (was previously building an empty whitelist that silenced every POI). **PG is the only source of truth for tours/stops/legs**; the 5 historical Kotlin-curated tours are dropped from the asset and will be re-authored via admin when wanted.
- **2026-04-26 (S185):** **Asset schema must be Room-canonical, not legacy SQL.** Two latent landmines were flushed: (1) asset's `tours`/`salem_pois`/etc. had `DEFAULT` clauses from `salem-content/create_db.sql`, but Room codegen schema has none → `TableInfo.equals` mismatch → `fallbackToDestructiveMigration` wiped the asset on every install; (2) `PRAGMA user_version` lagged the `@Database(version=N)` value → upgrade migration ran → fallback destructive. New `cache-proxy/scripts/align-asset-schema-to-room.js` is the canonical bridge: it rewrites every Room-managed table using the exact `createSql` from `app-salem/schemas/<DB>/<v>.json` and stamps both `identity_hash` and `user_version`. Must run last in any publish chain.
- **2026-04-26 (S184):** Admin walking-route rendering uses **anchor + clip** for marker continuity. Each leg's polyline is anchored to its from/to waypoint markers AND clipped to the polyline-points closest to those markers, eliminating both road-snap gaps and overshoot doglegs. The selected leg renders into a custom Leaflet pane (z 450) so it stacks above the green legs and the dashed connector but below the numbered waypoint markers. Pure rendering policy — no engine change.
- **2026-04-26 (S183):** Tour walking polylines become **authored content**, not runtime computation. New PG table `salem_tour_legs` stores per-leg polylines computed at admin time via the same on-device router; runtime APK reads baked legs directly (S184 follow-up). Satisfies S182's content-not-engineering rule by moving the work entirely to authoring time.
- **2026-04-26 (S182):** Tour route fidelity declared content-authoring, not engineering. New HARD RULE `feedback_tour_routing_is_content_not_engineering.md` — operator hand-curates tour stops; routing graph, geocoder, anchor schema, and Router changes are parked for V1.
- **2026-04-26 (S182):** S181 OSM-policy reversal RETRACTED. Diagnosis was wrong (`Router.routeMulti` is per-leg P2P + concat; visual divergence is from POI-centroid input, not from a graph deficiency). Plan archived to `docs/archive/`. `feedback_no_osm_use_local_geo.md` restored to S178 surgical-only constraint.
- **2026-04-26 (S180):** V1 manifest stripped of network permissions; V1 feature gates R8-stripped from binary. App network-incapable at OS level. Rule: `feedback_v1_no_external_contact.md` (zero outside contact except GPS).
- **2026-04-25 (S178):** Surgical 3-osm_id allowlist for wharf walkways added to `salem.edges` (Derby Wharf, Pickering Wharf, Seven Gables). Superseded by S181 broader policy.
- **2026-04-25 (S175):** On-device Salem router shipped (TIGER bake → APK + Directions UI). End of OSRM/online-tile dependency for routing.
- **2026-04-24 (S171):** osmdroid replacement (custom WickedMap SurfaceView engine) shipped as parallel system. S157 OSM-stays decision reversed. Migration of `SalemMainActivity` is in-progress carry-forward.
- **2026-04-23 (S158):** Witchy basemap added — third bundled basemap built from OSM (via Planetiler) + MassGIS structures + L3 parcels + custom MapLibre style. ~90% OSM-rendered, ~10% MassGIS. Documented S181.
- **2026-04-23 (S157):** MassGIS L3 Parcels + Assess for Essex County ingested. `massgis.l3_parcels_essex` = 366,884 polygons, `massgis.l3_assess_essex` = 429,803 rows.

---

## Phase Status

| Phase | Status | Notes |
|---|---|---|
| 1-9 + 9A+ + 9T (8/9) | COMPLETE | Core dev, offline foundation, ambient narration |
| **9P.A / 9P.B / 9U / 9X** | COMPLETE | Admin UI, unified POI table, Witch Trials feature |
| **9Y** MassGIS+TigerLine Overlay Integration | PARTIAL | L3 parcels + assess ingested (S157). Schema extended (S156). v9 cascade (S159). Outstanding: 9Y.3 enrichment script, 9Y.9 polygon geofence runtime, 9Y.Overlay osmdroid dynamic overlays. |
| **9Z** Historical Maps Time-Slider | PLANNED | V1 year slate: 1890, 1906, 1950, 1957 Sanborn. |
| **9Q** Salem Domain Content Bridge | DEFERRED — consumes 9Y data | building→POI translation. |
| **9R** Historic Tour Mode | DEFERRED — consumes 9Y data | opt-in chapter-based 1692 tour. |
| **10** Production readiness | First signed AAB built (S180); commercial chores remain | Firebase, photos, DB hardening, emulator verification. |
| **11** Branding, ASO, Play Store | Quality over schedule per operator | Salem 400+ Oct 2026 aspirational, not hard. |
| **Cross-project** TigerLine | Phase 2 stalled (2026-04-21) | LMA no longer blocked on tile delivery. |
| **Cross-project** SalemIntelligence | Phase 1 KB live at :8089 | 1,830 POIs / 1,770 narrated. |

**Sessions completed:** 189. Salem 400+ target 2026-09-01 aspirational.

---

## POI Inventory (refreshed S180 from live PG)

- **PG `salem_pois`:** 2,778 total. 1,443 visible / 1,335 hidden. 2,354 narrated. 2,639 with short_narration > 20 chars; 1,865 with long_narration > 50 chars. 503 with `building_footprint_geojson`. 1,649 with non-blank `website` URL (used by V1 Visit Website ACTION_VIEW). 0 with `lat_proposed`/`location_status` (verifier still blocked on TIGER MA ingest).
- **Room DB:** 9.2 MB at `app-salem/src/main/assets/salem_content.db`, **v10 schema** (S185), identity_hash `dad6c01b8e5f8fed0ae9ff6f8ef7432d`. Witch Trials intact (npc_bios 49 / articles 16 / newspapers 202). **v10 added the `tour_legs` table** for baked walking polylines from PG `salem_tour_legs`.
- **APK:** ~739 MB debug; `poi-icons/` at 544 MB is the pre-Play-Store audit target.
- **Assets manifest:** `app-salem/src/main/assets/ASSETS-MANIFEST.md` + `cache-proxy/scripts/verify-bundled-assets.js`.

---

## Standing rules + decisions

- **V1 commercial posture (S138):** $19.99 flat paid, fully offline, no ads, no LLM, IARC Teen.
- **PG-13 standing content rule** (NOTE-L018, accepted by OMEN S023).
- **Device-verify preference:** Lenovo TB305FU (HNY0CY0W) over emulator.
- **V1 offline-mode enforcement** (S141 + S180): const + R8 strip + manifest-level network-incapable at OS level.
- **adb install discipline** (S156): after `publish-salem-pois.js` rebake, deploy via `adb uninstall && adb install`, NEVER `install -r`.
- **No master plan** (S161): if multi-phase plan needed, declared explicitly in `docs/plans/`.

---

## OMEN Open Items

1. **NOTE-L014 / OMEN-008 — Privacy Policy** — V1-minimal Posture A shipped S145 at `docs/PRIVACY-POLICY-V1.md`. Full OMEN-008 draft pending OMEN review (32+ sessions).
2. **NOTE-L015 — SalemCommercial cutover** — PARKED POST-V1 per operator S145.
3. **OMEN-004 first real Kotlin unit test** — deadline 2026-08-30.
4. **Phase 9T.9 walk simulator end-to-end verification** — still TODO.
5. **Cross-project: SalemIntelligence anomaly relay** at `~/Development/OMEN/reports/locationmapapp/S153-si-anomalies-relay-2026-04-20.md` — covers ANOM-001 (9 GP-rooftop phantom-coord leaks), ANOM-002 (Heritage Trail verified_fact propagation gap).
6. **NOTE-L019 restrooms_zombie.png regen** (LOW) — no deadline.
7. **9-dot menu witchy backgrounds** — PARKED S146.
8. **Burial-grounds tribute hallucinations** (Oracle + SalemIntelligence) — PARKED S146. Pre-Play-Store audit needed.
9. **APK size pre-Play-Store blocker** — `poi-icons/` 544 MB. Audit + downsize to 256×256 or WebP q=75.
10. **S150 fixes 1/3 outdoor walk validation** — Fix 1 (DEEP toggle) + Fix 3 (real GPS motion) still pending. Checklist at `docs/field-walk-s153-checklist.md`.

---

## Pointers to detail

| What | Where |
|---|---|
| **Topic-indexed lookup of prior work** | **`MASTER_SESSION_REFERENCE.md`** |
| Recent session summaries (rolling window) | `SESSION-LOG.md` |
| Older session summaries | `SESSION-LOG-ARCHIVE.md` |
| Live conversation logs | `docs/session-logs/session-NNN-YYYY-MM-DD.md` |
| Active multi-phase plans | `docs/plans/` |
| SalemIntelligence integration | `~/Development/SalemIntelligence/docs/lma-*.md` |
| Architecture, tech stack | `CLAUDE.md` and the codebase itself |
| Legal/compliance | `GOVERNANCE.md` |
| Patentable innovations | `IP.md` |
| Monetization strategy | `COMMERCIALIZATION.md` |
| UX/architecture decisions | `DESIGN-REVIEW.md` |
| Test checklist | `CURRENT_TESTING.md` |
| Older STATE.md content | `docs/archive/STATE_removed_2026-04-09.md` (and `_removed_2026-04-21.md`) |
