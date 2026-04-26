# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — must stay under 200 lines.

**Last updated:** 2026-04-26 — Session 181 closed. Two paperwork-only outcomes: (a) one-medium off-machine backup of upload signing key landed on `/media/witchdoctor/writable/wickedsalem-upload-key-backup-2026-04-26/` via SHA-256-verifying sudo script; second-medium copy still owed before first Play Console upload. (b) **OSM policy reversed for routing-graph use** — operator approved broad pedestrian-network OSM ingest into `salem.edges` after eyes-on smoke test surfaced the streets-only routing producing visually wrong-looking polylines that grazed historic-building polygons in downtown Salem. Investigation: route was *functionally correct* (router output was right; visual artifact was misregistration of TIGER street centerlines vs. OSM-rendered Witchy basemap), but exposed that downtown `salem.edges` is ~92% TIGER street centerlines and ~8% pedestrian, with 1,030 OSM downtown footway/path edges sitting unused. Memory `feedback_no_osm_use_local_geo.md` amended; S178 surgical-allowlist superseded for pedestrian network ingest. Plan written at `docs/plans/S182-osm-pedestrian-routing-merge.md` — 4 phases, 2-3 sessions, hard `pgr_strongComponents ≤ 5` connectivity gate, parity test against existing tour legs. **No code changes shipped this session.** Smoke test interrupted after first item. Full detail in `docs/session-logs/session-181-2026-04-26.md`.

S180 standing (still valid): V1 Play Store gating chores DONE end-to-end. First signed AAB at `app-salem/build/outputs/bundle/release/app-salem-release.aab` (78 MB). Manifest network surface stripped, V1 feature gates R8-stripped from binary, Room migrations locked, signingConfigs wired, upload keystore generated + OMEN-registered. S179: tour routing unified to live `Router.route()` against bundled walking graph; bake-time edge splitting (47,704 edges / 44,223 walkable nodes; asset 11.2 MB). S178: `:routing-jvm` extraction + 60m approach cap + surgical OSM ingest. S175: on-device Salem router. S174: web admin Tours tab CRUD. S172: animations live in SalemMainActivity. S171: custom WickedMap engine (replaces osmdroid; migration in progress, 338 osmdroid call sites still extant).

---

## TOP PRIORITY — Next Session (S182)

1. **EXECUTE the OSM pedestrian merge plan** at `docs/plans/S182-osm-pedestrian-routing-merge.md`. P1 = node resolution at scale. P2 = edge ingest with `pgr_strongComponents ≤ 5` hard gate (catches the S178 608-component failure mode). P3 = re-bake routing bundle + parity test against existing tour legs. P4 = rebuild AAB + Lenovo eyes-on. **Three open questions for operator at S182 start:** tour parity tolerance (currently ±10% per-leg), fold in admin→build auto-bake from S180 carry-forward, OSM PBF refresh cadence (currently 2026-04-22 vintage).

2. **Continue eyes-on smoke test on Lenovo** (S180 carry-forward, S181 partial). Only tour routing was tested. Remaining items: POI detail Visit Website ACTION_VIEW external browser handoff, Find dialog Reviews/Comments hidden in V1, Find dialog Directions on-device router visible, toolbar gating (no Weather/Transit/CAMs/Aircraft/Radar buttons), webcam dialog "View Live" hidden. Worth interleaving with S182 since the OSM merge will rebuild the AAB anyway.

3. **OPERATOR-SIDE — second-medium copy of upload keystore.** One USB copy exists. Recommend encrypted cloud or a second USB stored elsewhere before first Play Console upload. Single-medium backups fail.

4. **Tier 2 — admin → build pipeline auto-bake** (S180 carry-forward). Per `feedback_admin_changes_propagate_to_builds.md`. Add a Gradle task that re-runs `salem-content` JVM bake from PG `salem_pois` → `salem_content.db` before `assembleRelease`/`bundleRelease`. Surface a "stale bake" warning when last-bake-mtime < last-admin-edit-mtime in PG. Could fold into S182 P3 since both involve re-baking.

5. **Tier 3 — Content-data fixes for outlier POIs** (S178 carry-forward). Two POIs have wrong coords causing >60m snap gaps even with S179's densified graph: `salem_common_2` at (42.5203, -70.8816) is 600m from actual Salem Common (~42.5232, -70.8908); `salem_willows` at (42.535, -70.86945) is mid-parking-lot. SQL UPDATE + re-bake + rebuild AAB. Heritage Trail tour also references 6 POIs not in curated tour-POI set.

6. **PRE-AAB — hard-delete dedup loser rows.** S123 soft-deleted 110 duplicate POIs (86 name-pass + 24 address-pass) marked in `data_source`. Before first Play Store upload: `DELETE FROM salem_pois WHERE data_source LIKE '%dedup-2026-04-13-loser%' OR data_source LIKE '%address-dedup-2026-04-13-loser%';` (verify zero FK refs first).

7. **OPERATOR-SIDE async** (no Claude action): Form TX copyright (lawyer handling, hard deadline 2026-05-20), Play Developer Account verification (multi-week lead time, will tackle in a later session), Privacy Policy public hosting (waiting on lawyer approval).

8. **DEFERRED from S179** (lower priority): Option 2 runtime mid-edge projection in `:routing-jvm` Router; walk-sim + DebugEndpoints `TourRouteLoader` cleanup → full retirement of S178 P6 dead data; water-aware approach segments (post-V1).

9. **DirectionsSession JVM tests** (still deferred). Convenience-not-correctness.

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

- **2026-04-26 (S181):** OSM allowed for routing graph (broad pedestrian-network ingest), build-time only. `feedback_no_osm_use_local_geo.md` amended. Plan at `docs/plans/S182-osm-pedestrian-routing-merge.md`.
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

**Sessions completed:** 181. Salem 400+ target 2026-09-01 aspirational.

---

## POI Inventory (refreshed S180 from live PG)

- **PG `salem_pois`:** 2,778 total. 1,443 visible / 1,335 hidden. 2,354 narrated. 2,639 with short_narration > 20 chars; 1,865 with long_narration > 50 chars. 503 with `building_footprint_geojson`. 1,649 with non-blank `website` URL (used by V1 Visit Website ACTION_VIEW). 0 with `lat_proposed`/`location_status` (verifier still blocked on TIGER MA ingest).
- **Room DB:** 9.2 MB at `app-salem/src/main/assets/salem_content.db`, v9 schema, identity_hash `4ec9ae3528d8f55529cd6875c7b0adef`. Witch Trials intact (npc_bios 49 / articles 16 / newspapers 202).
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
