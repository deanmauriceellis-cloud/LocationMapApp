# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — must stay under 200 lines.

**Last updated:** 2026-04-26 — Session 186 closed. **Tour Mode now has a real narration gate** (was missing pre-S186 — tours silently narrated everything in proximity). New rule: during a tour, only POIs flagged `is_tour_poi=true` narrate by default; two Layers checkboxes (existing **POIs Hist. Landmark**, new **POIs Civic**) opt-in additional classes. New `is_civic_poi` column on `salem_pois` (Room v10 → v11, identity_hash `55b35822253369a8af9f91e8bdbf8656`), bulk-true for 157 CIVIC POIs. Bulk-flagged 164 more rows as `is_tour_poi=true` (WORSHIP/PARKS_REC/EDUCATION + 8 HIST_BLDG museums) so 216 POIs are always-narrate-during-tour. Layers checkboxes have **separate explore vs tour state** — explore defaults BOTH ON ("Explore Salem" permissive default, civic + hist landmark visible + audible), tour defaults BOTH OFF (restrictive — only is_tour_poi narrates until user opts back in mid-tour). Each mode remembers its own pref independently. **`is_tour_poi=true` is the force-visible AND force-audible override** — Layers checkboxes only add/remove the *additional* class, never silence or hide the tour-flagged baseline. **1-hour dedup now persists across walk-sim restarts** within a process (only cold start clears it) — was being wiped by `narrationGeofenceManager.resetSession()` on every walk-sim start, fixed. Files: `app-salem/.../{content/model/SalemPoi.kt (+isCivicPoi), content/db/SalemContentDatabase.kt (v11), tour/NarrationGeofenceManager.kt (+setTourMode/isTourEligible/TOUR_HIST_LANDMARK_CATEGORIES), tour/TourEngine.kt (Tour Mode wiring + Historical Mode auto-enable removed), ui/SalemMainActivity.kt (refreshHistoricalModeForActiveTour rewritten + isTourActive + Layers map filter w/ is_tour_poi short-circuit + walk-sim resetSession removed), ui/SalemMainActivityTour.kt (observeTourState fires refreshHistoricalModeForActiveTour on every transition), ui/menu/AppBarMenuManager.kt (POIs Civic checkbox + active-mode pref helpers)}`, `core/.../{ui/menu/MenuPrefs.kt (4 new const + helpers), ui/menu/MenuEventListener.kt (+isTourActive)}`, `app-salem/schemas/...11.json (NEW)`, `app-salem/src/main/assets/salem_content.db` (regenerated), `cache-proxy/scripts/{publish-salem-pois.js (+is_civic_poi col), verify-bundled-assets.js (v11 hash)}`, `cache-proxy/lib/admin-pois.js (+is_civic_poi whitelist)`, `web/src/admin/{poiAdminFields.ts (+is_civic_poi), PoiEditDialog.tsx (+Civic POI checkbox)}`. Full detail in `docs/session-logs/session-186-2026-04-26.md`.

S184 standing (still valid): Admin walking-route UX polish (anchor + clip + click-to-highlight + click-to-recenter + cancel for + Free / + POI modes). The same anchor + clip algorithm now runs in the app's tour overlay too (S185).

S183 standing (still valid): Per-leg tour walking-route admin tool (backend + UI). PG table `salem_tour_legs` + 3 cache-proxy endpoints. App-side consumption now landed (S185).

S180 standing (still valid): V1 Play Store gating chores DONE end-to-end. First signed AAB at `app-salem/build/outputs/bundle/release/app-salem-release.aab` (78 MB) — superseded by S185 rebuild but signing config + version code (10000) unchanged. Manifest network surface stripped, V1 feature gates R8-stripped, Room migrations locked, signingConfigs wired, upload keystore generated + OMEN-registered. S179: tour routing unified to live `Router.route()` against bundled walking graph; bake-time edge splitting (47,704 edges / 44,223 walkable nodes). S178: `:routing-jvm` extraction + 60m approach cap + surgical OSM ingest. S175: on-device Salem router. S174: web admin Tours tab CRUD. S172: animations live in SalemMainActivity. S171: custom WickedMap engine (replaces osmdroid; migration in progress, 338 osmdroid call sites still extant).

---

## TOP PRIORITY — Next Session (S187)

1. **Continue refining the Android app + web admin tool.** Operator's stated direction at S186 close: "good enough, session end and we continue next session with refinement." Likely Layers menu UX polish, year_established backfill on ENTERTAINMENT/LODGING (so the Hist Landmark override has more than 79 narratable POIs in tour mode), and museum narration text authoring for the 8 newly tour-flagged HIST_BLDG museums (currently silent during tours).

2. **Onboarding-to-nearest-point on tour start.** Operator request from S185: when user picks a tour and isn't on the polyline, route them to the nearest point on it before walk mode begins. Code path: `TourViewModel.startTour()` → fetch `tour_legs` → flatten polyline → find nearest segment to user's GPS → call `walkingDirections.getRoute(userLoc, nearestPoint)` → publish that as the active directions session. When user reaches it, switch to "tour-walking" mode (no live directions; user just follows the static polyline + POI geofence narration).

3. **GPS-OBS heartbeat investigation.** Pre-existing Lenovo TB305FU quirk surfaced during S185 walk-sim debug: system GPS is delivering fixes (visible at `LocationManagerService` level) but the app's tour observer reports them as stale (`HEARTBEAT STALE — last fix 33s ago, narration reach-out suppressed`). Same family as the broken-motion-sensor issue noted in `feedback_lenovo_motion_sensor_broken.md`. Not introduced by S185, but compounds the "narration didn't fire" experience when walk-sim isn't active. Investigate why fresh fixes aren't reaching `lastFixAtMs` on this device.

4. **Tier 2 — admin → build pipeline auto-bake** (S180 carry-forward, escalated by S185). Per `feedback_admin_changes_propagate_to_builds.md`. Wrap `publish-tours.js` + `publish-tour-legs.js` + `align-asset-schema-to-room.js` as a `preBuild`-dependent Gradle task with a "stale bake" warning when last-bake-mtime < last-admin-edit-mtime in PG. ~30-min job. Now critical since the publish chain has 3 scripts and getting any of them out of order leaves a destructive-migration footgun.

5. **PRE-AAB — hard-delete dedup loser rows.** S123 soft-deleted 110 duplicate POIs (86 name-pass + 24 address-pass) marked in `data_source`. Before first Play Store upload: `DELETE FROM salem_pois WHERE data_source LIKE '%dedup-2026-04-13-loser%' OR data_source LIKE '%address-dedup-2026-04-13-loser%';` (verify zero FK refs first). Surfaced again in S185: 5 "Ledger" rows in PG, 3 narrated, 2 unnarrated dedup leftovers — likely contributing to confused geofence registration.

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

**Sessions completed:** 185. Salem 400+ target 2026-09-01 aspirational.

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
