# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — must stay under 200 lines.

**Last updated:** 2026-04-30 — **Session 205 (Category/subcategory lint pass + SI-driven auto-categorization — recovered + closed after a mid-session crash).** Operator picked S205 carry-forward #4. Inventory caught a silent resolver bug — `PoiHeroResolver.kt` filtered icon files by `${subcategory}_` prefix, but DB stores `food_drink__cafes` while files are `cafes_*`, so per-subcategory hero icons never fired and every POI hash-picked across the full category folder; fix strips `CATEGORY__` namespace. 4 new Taxonomy lint checks added (`subcat_namespace_mismatch` 2, `subcat_unknown_token` 24 — all `ENTERTAINMENT__tour_operators` → likely belong in TOUR_COMPANIES, `commercial_missing_subcategory` 1188 raw / 500 cap, `category_keyword_mismatch` 10). Phase 3 shipped SI-driven auto-categorize: 974/1188 (82%) of the missing-subcategory backlog have `intel_entity_id` for direct lookup, remaining 214 fuzzy-match (200m + name-token); mapping engine has 20-primary SI→LMA map, refiner functions for ambiguous primaries, name-override layer that runs BEFORE SI mapping, confidence tiers (≥0.9 same-cat → auto-apply, never silently changes category). Dry-run: 918 proposals / 706 auto-eligible / 212 review. Top review transitions: 68× SHOPPING→OFFICES, 26× ENTERTAINMENT→TOUR_COMPANIES, 12× SHOPPING→FOOD_DRINK. Web admin gained `AutoCategorizeModal.tsx` with summary cards + transition heatmap + color-coded review table + "Apply auto-eligible (706)" CTA. Vite HMR config fixed for LAN access (`web/vite.config.ts` removed explicit `hmr.host: localhost`). Claude crashed mid-session before close protocol; recovery confirmed work was functionally complete (no half-applied state) and ran the close. **Session 204 (prior):** Operator framed: "let's continue with the Lenovo items." All 7 device-dependent V1 carry-forwards from S203 closed in one pass. **Real bug found + shipped (#11 GPS-OBS heartbeat):** adaptive GPS interval picker sat downstream of the stationary-freeze early-return; on Lenovo TB305FU (`TYPE_SIGNIFICANT_MOTION` permanently broken per memory), the freeze stayed true forever during dwells, picker never ran, subscription stayed stuck at 60s — fixes arrived every ~27s right at the 30s stale threshold, toggling stale constantly. Lifted picker upstream; stale threshold 30s → 45s; verified on Lenovo: 30s/min 15s subscription, ~14s fix cadence, sustained `HEARTBEAT ok`, zero STALE transitions over 3 min. **#3 Witch Trials content audit** found 11/49 NPC bios with S192 meta-gap violations ("Little is known...", "vanished from the historical record"); patched `Salem/cmd/genbiographies/main.go` prompt with new `== ANTI-META-GAP RULE (CRITICAL) ==` + concrete VERDICT replacement examples; 2 LLM regen passes (cleared 8/11) + hand-edits on the 3 stragglers; final audit clean across 13 forbidden patterns. Re-bundled to APK + reinstalled. **Other items:** #17 walker-dwell S169 code intact (operator-skipped Splash launchMode fix); #29 S150 fixes 1+3 code correct; #21 Find type-search engine + bundled DB matches confirmed; #25 walk-sim e2e basic machinery verified (FAB tap → tour auto-pick → 225-pt baked polyline → 1.4 m/s sustained, TTS-gated dwell armed) — full POI loop deferred to operator field walk; #20 V1-offline static posture verified (zero network perms / `V1_OFFLINE_ONLY` const / BuildDefaults release retail). Five-script publish chain ran clean before any device work to push S203 PG mutations into bundled DB. **Cross-repo carry-forward:** Salem repo prompt edits + 3 hand-edited bio JSONs need committing in that repo. **Session 203 (prior):** V1 backlog drive-through — Phase A cleanup (heroes/hero audit, BuildDefaults debug/release switch, bulk-suppress 199 lint flags, hard-DELETE 365 dedup losers, newspaper PK defense, civic-mismatch strict), Phase B V1/V1.0.1/V2 triage, Phase C V1 execution (Witchy-only verify, first JVM unit test, outlier coords, onboarding-to-nearest-point, 6 burial-grounds NULLed). Phase D blocked on Lenovo offline. **Session 202 (prior):** Recon Camera (in-app CameraX + GPS+compass EXIF) + 7 testing-window default flips for wife's morning Salem walk. **Session 201 (prior):** Operator-confirmed internal ship target 2026-08-01. COMMERCIALIZATION.md / IP.md / CURRENT_TESTING.md archived; thin V1 replacements. **Session 200 (prior):** Five shipping arcs (commercial-prose legal cleaning, MASSGIS landmark surfacing, 384 Essex McIntire stub, etc.). Detail in `docs/session-logs/session-{200,201,202,203,204,205}-*.md`.

> Older "Previous" entries (S180 / S183-S189 trimmed S201; S195 trimmed S202; S196-S199 trimmed S204) — moved out to keep STATE.md under the 200-line cap. Detail lives in `SESSION-LOG.md` + `SESSION-LOG-ARCHIVE.md` + per-session live logs at `docs/session-logs/session-NNN-*.md`.

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

- Operator's wife walked Salem morning of 2026-04-30 with the S202/S203 recon-camera build. S204 APK now on Lenovo with the GPS-OBS fix + 11 cleaned bios — next walk will exercise the full POI-narration loop (deferred from #25 / #29 Fix 1 / #3 device verification, which were Beverly-desk-blocked).

### S206 next-session actions

1. **Apply S205 auto-categorize batch (operator-driven)** — open Lint tab → Auto-categorize modal → "Apply auto-eligible (706)", then walk the 212-row review table. Top review transitions: 68× SHOPPING→OFFICES, 26× ENTERTAINMENT→TOUR_COMPANIES (the previously-flagged `tour_operators` rows), 12× SHOPPING→FOOD_DRINK, 12× SHOPPING→AUTO_SERVICES, 9× SHOPPING→WITCH_SHOP, 5× FINANCE→OFFICES.
2. **Re-run publish chain after the batch** — `publish-salem-pois.js` → `publish-tours.js` → `publish-tour-legs.js` → `align-asset-schema-to-room.js` → `assembleDebug` — so Lenovo build picks up corrected categories + the resolver fix (per-subcategory hero icons fire only after a fresh deploy).
3. **Cross-repo Salem commit owed (S204 carry-forward, still owed)** — `Salem/cmd/genbiographies/main.go` (anti-meta-gap prompt rule) + `Salem/data/json/biographies/{abigail_williams,elizabeth_hubbard,john_indian}.json` (3 hand-edited bios) need committing in the Salem repo. Operator-driven since that repo is outside the LMA tree.
4. **Rebuild signed AAB + 30-min Lenovo smoke** — first signed AAB since S180 baseline. Use the V1 gating checklist (Visit Website ACTION_VIEW handoff, Find dialog Reviews/Comments hidden, Find Directions visible, toolbar gating, webcam "View Live" hidden, recon camera DEBUG-only — verify the BuildDefaults switch ships retail posture in release).
5. **Tier-2 auto-bake** (carry-forward from S203/S204) — wrap the 5-script publish chain as a `preBuild`-dependent Gradle task with stale-bake warning. ~45 min. Removes a class of release-day footgun.
6. **Operator field-walk validation** (S204 carry-forward) — wife's next Salem walk on the S204 APK validates: (a) GPS-OBS heartbeat steady (post-fix), (b) Witch Trials bios narrate without meta-gap phrasing, (c) full TTS-gated dwell engages on real POI traversal (the part #25 walk-sim couldn't exercise from Beverly).
7. **Drive regression** — operator drives Salem with airplane mode ON to validate offline posture under real GPS load (#20 carry-forward).
8. **#9 Re-author 5 polyline tours via web admin** (S185 carry-forward, operator-driven).
9. **#6 McIntire content drain** (S200 carry-forward, operator hand-author).

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
- ✅ **V1 (S199)** Eyes-on the shorter Witch Trials content — word counts verified vs caps; 11/49 bios surfaced S192 meta-gap violations → patched generator + 2 regen passes + 3 hand-edits → all clean. **S204.** Field-walk validation of audio playback rolls to operator's next Salem walk.
- ✅ **V1 (S199)** Newspaper PK normalization — pre-insert truncate added to `bundle-witch-trials-newspapers-into-db.js`. **S203.**
- ✅ **V1 (S200)** Tighten `civic_flag_mismatch` lint back to STRICT — severity warn→error. **S203.**
- **V1 (S200)** Operator hand-authors McIntire content for personally-known properties (operator-driven drain).
- **V1.0.1 (S192)** Backfill `year_established` on 500+ HIST_BLDG rows.
- **V2 (S195)** Driving mode — V1 is walking-only disclaimer.
- **V1 (S185)** Re-author 5 deleted Kotlin tours in PG as polyline-only via web admin (operator-driven).
- ✅ **V1 (S185)** Onboarding-to-nearest-point on tour start — `TourViewModel.onboardToNearestPolylinePoint`, threshold 50 m. **S203.**
- ✅ **V1 (S185)** GPS-OBS heartbeat investigation on Lenovo TB305FU — root cause: adaptive picker downstream of stationary-freeze + stale threshold too tight. Picker lifted upstream; threshold 30s → 45s. Lenovo-verified. **S204.**
- ✅ **V1 (S178)** Outlier coord fixes — `salem_common_2`/`_3` to centroid; `salem_willows` already correct; `OUTLIER_KM` 3→5 km; Rebecca Nurse suppressed. **S203.**
- **V2 (S179)** routing-jvm Option 2; walk-sim cleanup. Per content-not-engineering rule.
- **V1.0.1 (open)** DirectionsSession JVM tests.

---

### Older carry-forwards (S159–S172) — TRIAGED S203

- **V1.0.1 (S172)** Water animation visual tuning — `~` stroke doesn't sell.
- **V2 (S171/S172)** osmdroid → WickedMapView migration — 338 call sites; massive risk; parallel system already shipped.
- ✅ **V1 (S168/S169/S171)** Field-test fixes — walker dwell + `pickNextFromQueue` ordering verified intact in code (TTS-gated dwell, walkSim PACING bypass, walkSim behind-user skip). SplashActivity launchMode operator-skipped: "splash is operating fine." **S204.**
- **V1.0.1 (S162)** POI location verifier — TIGER MA loaded post-S188; verifier scaffolding exists, batch-run for V1.0.1.
- **V2 (S163)** MHC hidden-POI companion importer — awaits SalemIntelligence enrichment.
- **V1 (S161)** GPS + V1-offline regression check on next drive — static posture verified S204 (zero network perms / `V1_OFFLINE_ONLY` const / BuildDefaults release retail / OS-level network-incapable). Drive-test (airplane mode + GPS lock + zero network egress) operator-only on next Salem drive.
- ✅ **V1 (S165/S166)** Find type-search smoke — engine TYPE_SYNONYMS + bundled DB matches both verified (dentist→18, lawyer→33, gym→26, coffee→43). UI eyeballing operator-driven on next launch. **S204.**
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

**Sessions completed:** 205. **Internal ship target: 2026-08-01** (operator-confirmed S201). Salem 400+ peak attendance October 2026 (~1M visitors).

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
4. ✅ **Phase 9T.9 walk simulator end-to-end verification** — basic machinery verified S204 on Lenovo (FAB tap → tour auto-pick → 225-pt baked polyline → 1.4 m/s sustained, TTS-gated dwell armed, NARR-DWELL anchor advance). Full POI-narration loop deferred to operator field walk because Beverly desk-test triggered OUT OF SALEM fallback. Phase 9R Historic Tour Mode chapter sequencer remains DEFERRED per phase status.
5. **Cross-project: SalemIntelligence anomaly relay** at `~/Development/OMEN/reports/locationmapapp/S153-si-anomalies-relay-2026-04-20.md` — ANOM-001, ANOM-002.
6. **NOTE-L019 restrooms_zombie.png regen** (LOW) — no deadline.
7. **9-dot menu witchy backgrounds** — PARKED S146.
8. ✅ **Burial-grounds tribute hallucinations** — S203 audit: 6 POIs with hallucinated `historical_narration` NULLed (Ames Memorial Hall, 4 Greenlawn outbuildings, Prescott Memorial Cemetery). 4,311 chars of dubious prose removed; lint suppressions added. Cemeteries proper kept their narrations.
9. ~~**APK size pre-Play-Store blocker**~~ — REMOVED S203. STALE memory: actual `poi-icons/` is 3.4 MB, total assets 80 MB, current AAB 78 MB (under Play Store's 150 MB cap). No-op.
10. ✅ **S150 fixes 1/3 outdoor walk validation** — code correct S204 (DEEP install-default + 3 stationary-freeze escape hatches present). Behavioral validation rolls into operator's next field walk + S204 GPS-OBS fix indirectly strengthens Fix 3 path. **S204.**

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
