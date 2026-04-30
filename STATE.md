# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — must stay under 200 lines.

**Last updated:** 2026-04-30 — **Session 203 (V1 backlog drive-through — full Phase A cleanup, V1/V1.0.1/V2 triage, six V1 items shipped; Phase D blocked on Lenovo).** Operator framed the session as "drive through all the backlog with us developing the code; lawyer items in process." Lenovo HNY0CY0W went offline mid-session, blocking Phase D + 7 device-dependent V1 items. Shipped: (A1) heroes/ vs hero/ audit confirmed both LIVE — no delete; (A2) S202's seven testing-window flips converted from operator-side decision into a `BuildConfig.RECON_DEFAULTS` switch — debug builds get recon posture (GPS rec, breadcrumb, heading-up, zoom 19, FAB ±2, magnify x2, real-GPS-outside-Salem), release/AAB gets fresh-install retail. Single source of truth at `app-salem/.../ui/BuildDefaults.kt`; toggle by build type, no separate code paths to maintain; both compiles clean. (A3) bulk-suppressed 199 `narration_regen_failed` lint flags. (A4) FK-verified zero refs across 8 tables, then hard-DELETEd 365 dedup losers in transaction (2778 → 2413 POIs). (A5) newspaper bundle script gets pre-insert truncate so legacy id=date PK collisions can't survive. (A6) `civic_flag_mismatch` lint severity bumped warn→error (S199 auto-sync makes any drift a real bug; current PG drift: 0). (B1) all 31 carry-forwards triaged into V1 / V1.0.1 / V2 (operator confirmed). (Phase C) #28 poi-icons 544 MB blocker is STALE memory — actual `poi-icons/` is 3.4 MB, total assets 80 MB, current AAB 78 MB, well under Play Store's 150 MB cap; no-op. #23 verify-bundled-assets gained Witchy-only provider check (`Salem-Custom`-only). #24 first real Kotlin unit test ships — `NarrationGeofenceManagerTourModeTest` (5 tests, all green) on the `setTourMode` contract; mockito-core + `unitTests.returnDefaultValues=true` infrastructure added; OMEN-004 phase-1 deadline (2026-08-30) closed. #12 fixed `salem_common_2`/`_3` to centroid 42.5224,-70.8910; bumped `OUTLIER_KM` 3→5 km to cover legitimate Salem-Peabody border POIs; suppressed Rebecca Nurse Homestead (Danvers) individually; lint goes 75→0. #10 onboarding-to-nearest-point added in `TourViewModel.startTour` — reads `tour_legs`, finds nearest polyline point to user, routes if gap > 50 m. #27 burial-grounds tribute hallucination audit: 6 POIs had their `historical_narration` NULLed (Ames Memorial Hall invented donor; 4 Greenlawn outbuildings with meta-gap or "garage prior to 1780" hallucinations or post-1860 dates; Prescott Memorial Cemetery raw "156m/164m/187m..." metric leak). PG-side mutations need the full publish chain run before next AAB build. **Session 202 (prior):** Recon Camera as V1 feature (in-app CameraX + GPS+compass EXIF) + 7 testing-window default flips for the wife's morning Salem walk. Operator-pivoted scope; the S201-planned 1-hour V1/V1.0.1/V2 backlog triage rolls to S203. Two requests delivered: (1) Recon Camera as a V1 feature — slim-toolbar button next to Home, in-app CameraX `ReconCaptureActivity` (no system handoff) with explicit X-close + back-button cancel + 84dp shutter; `KatrinaCameraManager` writes full GPS+compass EXIF (lat/lon/alt/speed/track/img-direction/timestamp + Make/Model/Software + UserComment) using raw FusedLocationClient.lastLocation (bypasses MainViewModel's Samantha-clamp) + ROTATION_VECTOR azimuth; photos publish to MediaStore `Pictures/WickedSalem-Recon/`. CameraX 1.3.4 + ExifInterface 1.3.7 added. (2) GPS auto-tracking verified already wired (FusedLocationProvider + animateTo line 3067). Five testing-window defaults flipped: PREF_GPS_BBOX_OVERRIDE_DEFAULT true, PREF_RECORD_GPS true, isGpsTrackVisible true, isHeadingUpMode true, default zoom 18→19, FAB +/- step ±1→±2, magnify FAB level x1→x2. Footgun discovered: `AppBarMenuManager.prefDefault()` had hardcoded fallbacks separate from `MenuPrefs.PREF_*_DEFAULT` constants — flipping a default needs both sites or the menu UI silently lies (memory `feedback_appbar_pref_default_override.md`). **Session 201 (prior):** Operator-confirmed internal ship target 2026-08-01. COMMERCIALIZATION.md / IP.md / CURRENT_TESTING.md archived; thin V1 replacements. **Session 200 (prior):** Five shipping arcs (commercial-prose legal cleaning, MASSGIS landmark surfacing, 384 Essex McIntire stub, etc.). Detail in `docs/session-logs/session-{200,201,202}-*.md`.

**Previous (S199):** Three big shipped end-to-end. (1) **Salem Witch Trials content rewritten under attorney-driven word caps** — Oracle newspapers ≤250 words (avg 213, was 466), Witch Trial History tiles ≤150 (avg 166, was 369–419), People-of-Salem bios ≤150 (avg 135, was 2486 with the long fictional `narrative.*` concatenation). All three Salem Oracle generators (`Salem/cmd/{gennewspaper,gentiles,genbiographies}/`) got tightened prompts + dropped the duplicative "first POINTS repeats the lede" rule + a new `normalizeASCII()` helper. 267 generations on the RTX 3090 (gemma3:27b), 0 failures, 45 min. (2) **Import pipeline restructured** — bio import now reads `tts_full_text` from `data/json/biographies/` (was concatenating 7 fictional `narrative.*` sections out of `npcs/`). New JSON-direct importers replace the SalemIntelligence middleman. Incidental `publish-witch-trials-to-sqlite.js` Date-→-ISO `s()` wrapper extended to newspaper + article inserts. (3) **`is_civic_poi` drift across 179 POIs fixed** — operator field-discovered M&M Contractors narrating during a tour walk despite category=SERVICES; root cause flag never auto-cleared on category change. UPDATE 179 → 0 drift; bidirectional auto-sync wired in backend `buildUpdateClause()` + frontend useEffect. Full detail in `docs/session-logs/session-199-2026-04-29.md`.

**Previous (S198):** Two operator-driven fixes. (1) Witch Trials admin tab regression — `cache-proxy/lib/admin-witch-trials.js` destructured `pool` from `deps`, but the shared deps object exposes the connection as `pgPool` (every other admin module does so), so every list/get/put route was 500'ing. One-line alias fix. (2) `body_points` retired from `salem_witch_trials_newspapers` so the in-app Oracle Newspaper page renders the same article that the Speak button + ambient narration narrates (`tts_full_text`). Pre-S198: visual rendered `body_points` (LLM rewrite, ~1370 chars / 6 paragraphs); audio narrated `tts_full_text` (continuous prose, ~2740 chars). Operator: "I want what I hear and what I see to be identical." Room v14 → 15, identity_hash `3e927300be7b2a8971fa6afb4aa5af78`; PG column dropped on both `salem_witch_trials_newspapers` (live) and orphan `salem_newspapers_1692`; full publish chain ran; build clean (~11s); Lenovo HNY0CY0W uninstall + install. Full detail in `docs/session-logs/session-198-2026-04-29.md`.

**Previous (S197):** Two targeted Android narration fixes. (1) S192 punctuation chunker in `NarrationManager.kt` extended with five trip-up handlers — closing-quote sentence boundary so quoted dialogue splits cleanly, em-/en-dash mid-sentence beat (200ms), single-char ellipsis `…` recognized as both terminator and pause (300ms), `ABBREVIATIONS` expanded ~24 entries (Ave/Blvd/Rd/Ln/Esq/PhD/MD/Prof/etc.), `MIN_SUB_CHARS` 60 → 40 so commas in long sentences get an earlier explicit beat. (2) Witch-Trials Speak button bypassed AudioControl gate — added `userInitiated: Boolean = false` to `NarrationSegment`, threaded through `speakTagged*`. `enqueue()` skips group/oracle gates when `userInitiated=true`. `TourViewModel.speakSheetSection()` passes true; auto-triggered ambient still respects toggles. Two commits (`fb410f7`, `215b86d`), two Lenovo reinstalls. Full detail in `docs/session-logs/session-197-2026-04-29.md`.

**Previous (S196):** Three big wins. (1) **Commercial-hero leak fixed** — re-categorized 4 civic non-profits (Historic Salem Inc., Destination Salem ×2, Salem Arts Association) from BUSINESS categories to CIVIC; pruned 19 commercial hero files / 11 unique POIs. Signed-AAB blocker cleared (verify-bundled-assets: 0 leaks). (2) **Historical narration overhaul + full regen.** Generator (`generate-historical-narrations.js`) gained 100–250 word HARD CAP, STRICT_RULE 13 anti-repetition / closure-flourish ban, STRICT_RULE 14 no meta-narration, STRICT_RULE 15 name anchor (caught a Fairbanks-House hallucination on Witch House retry), bare-token / ALL-CAPS preamble strip, era-flexible commemorative tribute path. Operator policy mid-session: ALL Salem-connected commemoratives narrate (was: pre-1860 only) — Bewitched/Samantha now narrates the 1970 filming + Salem pop-culture connection in modern voice; Lydia Pinkham narrates her 1819 birth + 1922 Salem clinic. Memory `feedback_commemoratives_are_historical_tributes.md` updated. Full regen ran 6 hours: 344 ok / 199 reject / 18 skip across 561 eligible POIs. Bundled DB rebuilt + APK installed on Lenovo. 199 rejects surfaced in lint as `narration_regen_failed`. (3) **Admin audit log + revert UI** — operator-requested, end-to-end. PG row-level trigger on 9 admin tables (`salem_audit_log` + `salem_audit_trigger_fn`), captures BEFORE+AFTER + actor/source from `SET LOCAL "app.actor"`. Cache-proxy `lib/admin-audit.js` with list/stats/per-entity/single-row endpoints + revert (applies inverse op, marks original reverted, writes new audit row). Web admin **Audit tab** (`AuditTab.tsx`) with stats strip, filter bar, color-coded action timeline, expand-to-diff, per-row Revert + reason prompt, 15s auto-refresh. E2e revert test verified. CAVEAT: 561 regen audit rows + admin-UI write rows have NULL actor/source — code paths not yet instrumented (next session: multi-admin auth + actor-from-session wiring). 3 commits (`7c7b116`, `f5a9654`, `d961dc9`), 1 Lenovo reinstall. Full detail in `docs/session-logs/session-196-2026-04-28.md`.

> Older "Previous" entries (S180 / S183 / S184 / S186 / S187 / S188 / S189 trimmed S201; S195 trimmed S202) — moved out to keep STATE.md under the 200-line cap. Detail lives in `SESSION-LOG.md` + `SESSION-LOG-ARCHIVE.md` + per-session live logs at `docs/session-logs/session-NNN-*.md`.

---

## TOP PRIORITY — V1 launch triage (operator-confirmed 2026-04-29; revised S203 close 2026-04-30)

> **Internal ship target: 2026-08-01** (93 days). One month tighter than the prior Sept 1 anchor in CLAUDE.md / strategic docs. Salem 400+ peak attendance is October 2026.

### Operator-side legal / business (in flight)

- **Form TX copyright** — counsel handling. Hard deadline **2026-05-20** (20 days). Confirm with lawyer this week that filing happens.
- **2026-04-20 lawyer meeting** — happened, outcome captured by operator. **Operator owes me four privacy-policy fields** (operating entity name+type, jurisdiction, contact email, mailing address) so I can patch `docs/PRIVACY-POLICY-V1.md`.
- **Play Console developer account** — operator starts this week. Multi-week ID-verification clock starts immediately.
- **Upload keystore second-medium backup** — covered (operator confirmed multiple secure methods).
- **TTS quality monitoring** — operator does end-to-end Lenovo listen-tests routinely as part of QC.

### Field test (carry-forward to next session)

- Operator's wife walked Salem morning of 2026-04-30 with the recon-camera build on Lenovo HNY0CY0W; Lenovo is currently offline so Phase D smoke + device-dependent V1 verification is queued for next session.

### S204 next-session actions

1. **Run the publish chain** to push S203 PG mutations into the bundled DB before the next AAB:
   ```
   publish-salem-pois.js → publish-tours.js → publish-tour-legs.js
   → bundle-witch-trials-newspapers-into-db.js → align-asset-schema-to-room.js
   ```
   Mutations to propagate: -365 dedup-loser hard-deletes, `salem_common_2/3` coord fix, 6 burial-grounds `historical_narration` NULLed.
2. **Rebuild signed AAB with S185–S203 work + 30-min Lenovo smoke** against the V1 gating checklist (Visit Website ACTION_VIEW handoff, Find dialog Reviews/Comments hidden, Find Directions visible, toolbar gating, webcam "View Live" hidden, recon camera in DEBUG only — verify the BuildDefaults switch ships retail posture in release).
3. **Tier-2 auto-bake** — wrap the 5-script publish chain as a `preBuild`-dependent Gradle task with stale-bake warning. ~45 min. Removes a class of release-day footgun.
4. **Device-dependent V1 verification** (queued from S203 — Lenovo was offline):
   - #3 Eyes-on shorter Witch Trials content during real Lenovo walk (S199 / S200 verify).
   - #11 GPS-OBS heartbeat investigation on Lenovo TB305FU.
   - #17 Walker-dwell field fixes verify.
   - #20 GPS + V1-offline regression check on next drive.
   - #21 Find type-search smoke ("dentist", "lawyer", "gym", "coffee").
   - #25 Phase 9T.9 walk simulator end-to-end verification.
   - #29 S150 outdoor-walk validation Fixes 1+3.
5. **#9 Re-author 5 polyline tours via web admin** (operator-driven, can pair).
6. **#6 McIntire content drain** (operator hand-author for personally-known properties).

### S205+ — V1 feature additions (priority order, KEPT for V1)

1. **Halloween / October seasonal layer** — date-checked content overlay (different POI markers, ghost-tour POIs unlocked October 1, photo-spot pins, October-only event narration). Zero network. Reuse existing geofence + narration paths. Scope tight, ~1 week.
2. **Time-slider basemap, 1906 only** for V1 (Sanborn 1906) — full 4-year slate (1890/1906/1950/1957) deferred to V1.0.1+. ~1 week for one year.
3. **Tour-completion certificate (PDF on-device)** — end-of-tour souvenir with route + triggered POIs + date + Salem-themed border. Zero network. ~2-3 days.
4. **Salem Heritage Trail polyline tour — YELLOW, not red** (the trail was repainted yellow). Hand-trace via admin or import official KML if available. ~1 day. See memory `reference_salem_heritage_trail_yellow.md`.

### V1 features deferred / killed (S201 decisions)

- **Bark hero voice clips** — SKIPPED. Ship Android TTS only (operator does ongoing QC listen-tests).
- **Hotel B2B partnerships** — post-launch only.
- **Pre-launch beta** — small, 2–3 friendly-eyes testers (not the 5–10 program).
- **iOS port** — scope effort/timing now, decide later.

### Marketing (operator-led, content-only sessions before Aug 1)

- **Channel:** Salem Chamber of Commerce + local-first.
- **Asset packet** — drafted in a content-only session before Aug 1: 1-page sell sheet, feature graphic, 3-5 hero screenshots, 2-paragraph press blurb. Operator provides photos + final approval; I draft text + screenshot frames.

### Architectural housekeeping applied this session (S201)

- **`COMMERCIALIZATION.md`, `IP.md`** — replaced with thin V1-truthful stubs. Pre-pivot originals archived to `docs/archive/{COMMERCIALIZATION,IP}_removed_2026-04-29.md` with a "pre-pivot platform vision" banner.
- **`CURRENT_TESTING.md`** — archived (lint tab + session logs serve the testing-checklist role now); no replacement.
- **`CLAUDE.md` ship anchor** — updated from Sept 1 to Aug 1 (operator-confirmed internal target).
- **`STATE.md` ship anchor** — this section.

---

## Backlog from S185–S200 (TRIAGED S203)

S203 stamped every item below V1 / V1.0.1 / V2. ✅ marks items shipped in S203.

- **V1.0.1 (S196)** Multi-admin auth + actor-from-session wiring — `salem_admin_users` table, Basic Auth verification, `SET LOCAL "app.actor"` plumbing on admin write endpoints + scripts.
- **V1.0.1 (S196)** PoiEditDialog inline history panel — latest 20 audit entries alongside Quality Flags panel, per-row Revert.
- **V1 (S199)** Eyes-on the shorter Witch Trials content + S200 commercial template + S200 `historical_narration` display during a real Lenovo walk. **Carry-forward — Lenovo offline at S203 close.**
- ✅ **V1 (S199)** Newspaper PK normalization — pre-insert truncate added to `bundle-witch-trials-newspapers-into-db.js`. **S203.**
- ✅ **V1 (S200)** Tighten `civic_flag_mismatch` lint back to STRICT — severity warn→error. **S203.**
- **V1 (S200)** Operator hand-authors McIntire content for personally-known properties (operator-driven drain).
- **V1.0.1 (S192)** Backfill `year_established` on 500+ HIST_BLDG rows.
- **V2 (S195)** Driving mode — V1 is walking-only disclaimer.
- **V1 (S185)** Re-author 5 deleted Kotlin tours in PG as polyline-only via web admin (operator-driven).
- ✅ **V1 (S185)** Onboarding-to-nearest-point on tour start — `TourViewModel.onboardToNearestPolylinePoint`, threshold 50 m. **S203.**
- **V1 (S185)** GPS-OBS heartbeat investigation on Lenovo TB305FU. **Carry-forward — Lenovo offline.**
- ✅ **V1 (S178)** Outlier coord fixes — `salem_common_2`/`_3` to centroid; `salem_willows` already correct; `OUTLIER_KM` 3→5 km; Rebecca Nurse suppressed. **S203.**
- **V2 (S179)** routing-jvm Option 2; walk-sim cleanup. Per content-not-engineering rule.
- **V1.0.1 (open)** DirectionsSession JVM tests.

---

### Older carry-forwards (S159–S172) — TRIAGED S203

- **V1.0.1 (S172)** Water animation visual tuning — `~` stroke doesn't sell.
- **V2 (S171/S172)** osmdroid → WickedMapView migration — 338 call sites; massive risk; parallel system already shipped.
- **V1 (S168/S169/S171)** Field-test fixes — walker dwell, SplashActivity launchMode, `pickNextFromQueue` ordering. **Carry-forward — Lenovo offline.**
- **V1.0.1 (S162)** POI location verifier — TIGER MA loaded post-S188; verifier scaffolding exists, batch-run for V1.0.1.
- **V2 (S163)** MHC hidden-POI companion importer — awaits SalemIntelligence enrichment.
- **V1 (S161)** GPS + V1-offline regression check on next drive. **Carry-forward — Lenovo offline.**
- **V1 (S165/S166)** Find type-search smoke. **Carry-forward — Lenovo offline.**
- **V1.0.1 (S159)** Witchy tile-bake bbox extension OR no-coverage UX hint — operator's Beverly home north of S158 bake.
- ✅ **V1 (S167)** `verify-bundled-assets.js` Witchy-only provider check — `Salem-Custom`-only allowlist on `salem_tiles.sqlite`. **S203.**

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

**Sessions completed:** 200 (S201 in progress). **Internal ship target: 2026-08-01** (operator-confirmed S201). Salem 400+ peak attendance October 2026 (~1M visitors).

---

## POI Inventory (refreshed S180 from live PG)

- **PG `salem_pois`:** 2,778 total. 1,443 visible / 1,335 hidden. 2,354 narrated. 2,639 with short_narration > 20 chars; 1,865 with long_narration > 50 chars. 503 with `building_footprint_geojson`. 1,649 with non-blank `website` URL (used by V1 Visit Website ACTION_VIEW). 0 with `lat_proposed`/`location_status` (verifier still blocked on TIGER MA ingest).
- **Room DB:** 9.3 MB at `app-salem/src/main/assets/salem_content.db`, **v14 schema** (S195), identity_hash `5a42a013703bb605f5ad4f065309ee8a`. Witch Trials intact (npc_bios 49 / articles 16 / newspapers 202). v11 (S186) `is_civic_poi`. v12 (S192) `historical_narration` (358 populated post-S195 cleanup). v13 (S193) `is_historical_tour`. v14 (S195) `is_historical_property` (358 set, 265 `is_civic_poi`). `publish-tour-legs.js` reads latest schema JSON.
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
3. ✅ **OMEN-004 first real Kotlin unit test** — `NarrationGeofenceManagerTourModeTest` shipped S203 (5 tests, all green). Phase 1 deadline (2026-08-30) closed. Future tests slot into the same source set.
4. **Phase 9T.9 walk simulator end-to-end verification** — carry-forward; Lenovo offline at S203 close.
5. **Cross-project: SalemIntelligence anomaly relay** at `~/Development/OMEN/reports/locationmapapp/S153-si-anomalies-relay-2026-04-20.md` — ANOM-001, ANOM-002.
6. **NOTE-L019 restrooms_zombie.png regen** (LOW) — no deadline.
7. **9-dot menu witchy backgrounds** — PARKED S146.
8. ✅ **Burial-grounds tribute hallucinations** — S203 audit: 6 POIs with hallucinated `historical_narration` NULLed (Ames Memorial Hall, 4 Greenlawn outbuildings, Prescott Memorial Cemetery). 4,311 chars of dubious prose removed; lint suppressions added. Cemeteries proper kept their narrations.
9. ~~**APK size pre-Play-Store blocker**~~ — REMOVED S203. STALE memory: actual `poi-icons/` is 3.4 MB, total assets 80 MB, current AAB 78 MB (under Play Store's 150 MB cap). No-op.
10. **S150 fixes 1/3 outdoor walk validation** — Fix 1 (DEEP toggle) + Fix 3 (real GPS motion) still pending. Carry-forward — Lenovo offline.

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
