# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — must stay under 200 lines.

**Last updated:** 2026-05-03 — **Session 222 (utility — restart web-admin services post-reboot).** Brought cache-proxy (4300) + web Vite (4302) back up after a host reboot. Cache-proxy `npm start` crashed at `admin-lint.js:1026` because `cache-proxy/server.js` reads `process.env.DATABASE_URL` directly without `dotenv` — `npm start` doesn't source `cache-proxy/.env`. Worked around by sourcing the env into the spawning shell (`(set -a; source .env; set +a; node server.js …)`); permanent fix queued for S223 (add `dotenv` dep + `require('dotenv').config()` at top of server.js, OR wrap `npm start` in an env-sourcing shim). Services left running across the session boundary at operator request. SalemIntelligence (8089) + Salem Oracle (8088) intentionally NOT started — provenance-pause still in effect. **Session 221 (prior — tour detour feature + web admin fuzzy search + 2 GB stale-file recovery).** Three substantive deliveries plus a host-system AIDE diagnosis. (1) **Tour detour feature** end-to-end across 11 files (8 modified, 3 new): subtopic adjacency cards (`source_kind='adjacent_poi'`) get an inline "▸ Open <linked POI>" link → fly + open POI Detail Sheet → orange "Take a detour and walk there?" button → orange `#FF8C00` out-route polyline + persistent fuchsia floating banner pinned top-of-map → tap banner → AlertDialog (Return / Stop / Cancel), Return → second chooser (Nearest point / Back to last stop, per operator's "let user choose each time" answer). New `TourState.Detour` variant + 4 SharedPreferences keys (`detour_poi_id`, `detour_poi_name`, `detour_prev_hist_landmark_tour`, `detour_prev_civic_tour`) persist the detour across cold launches; `restoreIfSaved()` re-emits Detour so the banner reappears on relaunch. Narration gate fully relaxed during detour (so any eligible POI speaks while walking to the detour POI), Layer-toggle prefs snapshotted + restored on Return. New `SubtopicRenderer` callbacks (`onOpenLinkedPoi`, `resolveLinkedPoiName`) shared by the narration banner + POI Detail Sheet (both async-prefetch adjacency POI names so the link label resolves to real names). New file `SalemMainActivityDetour.kt` (~190 LOC) holds banner wiring, dialog flow, polyline overlays, and the `openPoiDetailFromSubtopic` gateway. `./gradlew :app-salem:assembleDebug` green, APK on Lenovo HNY0CY0W, no Room schema bump. Plan vetted via Plan-mode + AskUserQuestion before code. (2) **Web admin fuzzy search + map auto-filter** — `PoiTree.tsx` search now multi-token AND across `name + id + address + 3 narration fields + category/subcategory labels + slug-form of category/subcategory IDs`, so "bed" hits both "Morning Glory Bed & Breakfast" (name) and any future POI in `LODGING__bed_and_breakfast` subcat. AdminLayout mirrors the search via new `onSearchChange`, builds an id Set via shared `poiMatchesQuery`, passes as `searchIdFilter` to AdminMap. Floating sky pill top-right of map shows `Search: "<term>" (N)`. (3) **2 GB stale-file cleanup** — Tier 1 (build artifacts) + Tier 2 (untracked stale runtime caches & 2026-03-01 overnight-runs + S092-era bake-test logs); repo size 15 G → 13 G. Tier 3 (~4.4 GB in tools/ + cache-proxy/out/l3-essex/ + docs/archive/poi-icons-unused-styles_*) flagged for operator review, NOT touched. (4) Host-side AIDE process force-killed + timer disabled; 9.7 Gi memory recovered. **Session 220 (prior — subtopic UI on narration banner + auto-gen pipeline).** Finished the runtime UI surface coverage: extracted `app-salem/.../ui/SubtopicRenderer.kt` (top-level `Subtopic` data class + `parseSubtopics` + `renderSubtopics` + `hideSubtopics`) and wired it into the narration bottom sheet (`narration_bottom_sheet.xml` + `showNarrationSheet()` in `SalemMainActivityNarration.kt`); `showNewspaperSheet()` calls `hideSubtopics()` so witch-trials headline mode stays clean. Same chip-strip + collapsible body card UX as the POI Detail Sheet — refactored `PoiDetailSheet.bindSubtopics` to use the shared util. Built `cache-proxy/scripts/auto-gen-narration-subtopics.js`: synthesizes subtopic candidates from local sources only (provenance-pause-respecting), figure detection across the 49 NPC bios in `salem_witch_trials_npc_bios` (multi-variant whole-word matching, ≥8 char variants only) + adjacency to the nearest eligible POI ≤200 m. Operator-driven eligibility fence baked mid-session: only `category IN (HISTORICAL_LANDMARKS, HISTORICAL_BUILDINGS, CIVIC, WORSHIP)` OR `is_historical_property/is_civic_poi/is_witch_trial_site=true`. Commercial categories excluded as both source and target. `is_tour_poi` deliberately NOT in the fence — data-quality pollution discovered (e.g. `young_world_academy`/`young_world_preschool_and_daycare` flagged true). Dupe suppression via `looksLikeDupe(name1, name2)` token-overlap heuristic (suppresses `yin_yu_tang ↔ yin_yu_tang_house_peabody_essex_museum` style cross-links). `source_kind`/`source_ref` tagged on every emitted card so admin SubtopicEditor distinguishes auto-gen from hand-authored. Live run: 652 POIs eligible, 497 updated (12 with figure cards + 485 adjacency-only). Publish chain re-ran (no Room schema bump — column existed from S219); identity_hash unchanged at `d44caaec…`. Lenovo HNY0CY0W visual sign-off: "works well — needs to be refined a lot more but very useful." **Session 219 (prior):** Storytelling-with-subtopics framework end-to-end — `salem_pois.narration_subtopics` JSONB column (shape `[{header, body, source_kind?, source_ref?}]`), publish chain extended, Room v16→v17 with `SalemPoi.narrationSubtopics`. Web admin `SubtopicEditor.tsx` + `PoiDetailSheet` chip+body rendering. Worked example: 5 hand-authored Old Burying Point subtopics. **S213-S218 (prior):** TTS pre-normalization (S213), TigerLine waypoint binding + tour-mode POI preview (S214), edge-point Dijkstra threading mid-block waypoints (S215), 10-thread consolidation incl. FAB walk onboarding + auto-bake gradle preBuild + S189 hard-purge dedup + HISTORICAL_LANDMARKS split + Room v15→v16 (S216), category-aware FAB narration + Show-All-POIs gate-override (S217), per-POI Validate via TigerLine/Google workflow + `docs/TIGERLINE.md` (S218). **Operator-stated hard pause from S214 still in effect: Salem Oracle (:8088) + SalemIntelligence (:8089) are undergoing major reformulation to attach full per-claim provenance; without source attribution V1 content cannot ship legally — do not propose work consuming them or backfilling content from them until the operator signals the pause is lifted (memory `project_oracle_intelligence_paused_provenance.md`).** **Cross-repo Salem commit owed (still).** Detail in `docs/session-logs/session-{208..221}-*.md` and `SESSION-LOG.md`.

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

- Operator drove ~50 min with wife on 2026-04-30 evening on the S206 first-build APK (pre-fixes). Logs reconstructed the missing-POI experience as queue-starvation + tour-mode default-OFF + `is_tour_poi` half-honored. Lenovo now carries the post-fix S206 APK; next drive should validate (a) Lappin Park / Curtis Park / Carlton School render in tour mode regardless of layer toggles, (b) starting a tour with explore-checked layer boxes no longer silently silences markers + narration. Operator's wife's next Salem walk also still owes #25 / #29 Fix 1 / #3 full POI-narration loop validation from S204.

### Hard gate (S214 close, 2026-05-01)

**Salem Oracle (:8088) and SalemIntelligence (:8089) are paused for major provenance reformulation.** Until the operator signals completion, do NOT propose: new admin-tool integrations against `oracleClient.ts` / port 8088, new consumers of `/api/intel/*` at :8089, narration regeneration sourced from these services, hero-prompt regeneration from these services, POI description backfills from these services. Manual / curated content (operator-authored, MassGIS MHC, TigerLine-derived, hand-edited Wikipedia with operator citations) is unaffected. Existing `salem_pois` / `salem_tour_stops` data sourced from these services is grandfathered for now but may need re-attribution before AAB upload — the operator will direct this when the reformulation lands.

### S223 next-session actions

- **Cache-proxy `.env` autoload fix.** Add `dotenv` to `cache-proxy/package.json` + `require('dotenv').config()` at top of `server.js` (above the `process.env.DATABASE_URL` read), OR replace the `npm start` script with an env-sourcing shim. Surfaced S222 — every reboot currently kills cache-proxy at `admin-lint.js:1026` until the env is manually sourced.
- **Operator field-validation of S221 detour feature on Lenovo HNY0CY0W.** Plan steps 2–12: walk-sim a tour with adjacency cards (e.g. The Witch House → Nathaniel Bowditch House), confirm Open link → fly + sheet → orange "Take a detour" button → orange route + fuchsia banner → narration gate relaxed (non-tour POI speaks during detour) → kill app + relaunch → banner persists → Return tap → Nearest/Last-stop chooser → fuchsia rejoin polyline → gate reapplied. Then re-detour → "Stop, continue later" → confirm relaunch offers resume.
- **Detour feature polish post-field-test** (S221 carry, anticipated): distance/time labels on the banner ("On detour to X · 240m · 3 min"), out-and-back distance preview on the "Take a detour?" confirm step, rejoin-polyline fade after a few seconds, Layer-toggle re-application timing question (immediate vs. on rejoin-arrival).
- **Refine subtopic content quality** (S220 carry). Operator: "works well — needs to be refined a lot more but very useful." Levers: figure-card body trimming (currently 800 chars — tune for storytelling cadence), adjacency teasers (currently first 1–2 sentences of nearby's `short_narration` — may want a curated link sentence instead), distance phrasing thresholds.
- **`is_tour_poi=true` Lint-tab cleanup** (S220 carry). Spurious-flagged rows: `young_world_academy`, `young_world_preschool_and_daycare` (both `category=EDUCATION`). Add a Lint check flagging `is_tour_poi=true` AND `category IN (EDUCATION, OFFICES, HEALTHCARE, AUTO_SERVICES, FINANCE, APARTMENT_BUILDING, FOOD_DRINK, LODGING)`. Operator confirms / un-flags one-by-one. Same UX as dedup-loser cleanup. This pollution affects the S186 force-visible/force-audible override during tours AND the new S221 detour gate (the detour button hides on the current-stop POI, so polluted tour-flagged daycares could surface the button from neighbouring stops).
- **Borderline-historical commercial opt-in audit** (S220 carry). Famous Salem buildings categorized commercially get fenced out of auto-gen — e.g. Hawthorne Hotel (1925, `category=LODGING`, no historical flags). Operator-side: flip `is_historical_property=true` on the rows the operator wants in the auto-gen pool, then re-run with `--overwrite` flag.
- **Tier 3 disk reclaim** (~4.4 GB still on disk after S221 quick-win). `tools/poi-icons/` (1.9 GB), `tools/hero-triptych/output-full/` (526 MB), `tools/tile-bake/data/sources/` (1.7 GB), `cache-proxy/out/l3-essex/` (920 MB), `docs/archive/poi-icons-unused-styles_archived_2026-04-24/` (471 MB). Operator decision before any of these go.
- **`.gitignore` audit** (S221 follow-up offered, deferred) — make `overnight-runs/`, `cache-proxy/poi-cache.json`, `cache-proxy/cache-data.json`, `web/dist`, `web/tsconfig.tsbuildinfo`, `docs/bake-tests/` ignored so they stop re-accumulating.
- **Field listen-test of S217 BusinessLabel fallbacks** — vague generics likely to want refinement: SERVICES → "service", FINANCE → "financial business", HEALTHCARE → "healthcare provider", ENTERTAINMENT → "venue". TTS pronunciation of "cafe" and "centre" (British spelling) may need overrides.
- **Field validation of S217 FAB override** — walk a tour with FAB ON; every nearby POI should announce regardless of tour gate / Layers / Audio Control toggles. NARR-GATE log shows `override=true` per enqueue.
- **John Ward House (and similar) tour-leg fix** — operator-side (S216 carry). Leg passes 55–73 m from POI's BCS coords, outside the 40 m narration trigger. Re-author the leg or move the POI in admin.
- **HISTORICAL_LANDMARKS icon + hero regeneration** (S216 carry) — currently aliased to `historic_house` set; operator plans distinct landmark imagery.
- **Content rebuild for `description` post-`historical_note`-merge** (S216 carry) — 133 POIs had `historical_note` appended with `\n\n`; commercial-production content pass will rewrite cleanly.
- **Dequeue 40 m staleness drop** (S216 carry) — confirm the 40 m rule matches the operator's intent or whether it should align with each POI's own `geofence_radius_m`.
- **S215 suspicious-leg graph investigation** — Dr. K legs 13/14 + WD1 legs 3/6/10/11 "short straight, long routed" — pre-existing routing-bundle oddity.
- **CLAUDE.md "Pinned for next session start" cleanup** (carry-forward) — block still references Room v11 / S186-S188 state; actual schema is v16.

### Open backlog (older items, still pending)

- **Speed-aware queue cap** (S206 carry) — at >15 mph, replace queued narration items rather than appending; alternative: switch to SHORT tier at high speed. Pick + ship.
- **DensityTour 0-stop design question** (S206) — auto-fall-back to Explore at tour start, or refuse to start a 0-stop tour. Decide + ship.
- **Cross-repo Salem commit owed** (S204→S205→S206) — `Salem/cmd/genbiographies/main.go` + 3 hand-edited bio JSONs need committing in the Salem repo. Operator-driven.
- **Rebuild signed AAB + 30-min Lenovo smoke** — first signed AAB since S180. V1 gating checklist (Visit Website handoff, Find Reviews/Comments hidden, toolbar gating, webcam "View Live" hidden, recon camera DEBUG-only — verify BuildDefaults retail posture in release).
- **Operator field-walk validation** (S204) — GPS-OBS heartbeat + Witch Trials bios + full TTS-gated dwell on real POI traversal during wife's Salem walks.
- **Drive regression in airplane mode** (#20) — validate offline posture under real GPS load.
- **#9 Re-author 5 polyline tours via web admin** (S185, operator-driven).
- **#6 McIntire content drain** (S200, operator hand-author).

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

Open items only (✅ S203/S204 entries trimmed S216 — detail in archive): **V1.0.1 (S172)** water animation visual tuning; **V2 (S171/S172)** osmdroid → WickedMapView migration (parallel system shipped); **V1.0.1 (S162)** POI location verifier batch-run (TIGER MA loaded post-S188); **V2 (S163)** MHC hidden-POI companion importer; **V1 (S161)** GPS + V1-offline drive regression check (operator-only); **V1.0.1 (S159)** Witchy tile-bake bbox extension for operator's Beverly home.

---

## Architectural pivots & rule changes (recent → older)

- **2026-05-01 (S216):** **`HISTORICAL_LANDMARKS` split off from `HISTORICAL_BUILDINGS`.** MASSGIS-only (`data_source = 'massgis_mhc'`) POIs reclassify to a new `HISTORICAL_LANDMARKS` category that's gated by the existing "POIs Hist. Landmark" Layers toggle (default off). Curated `HISTORICAL_BUILDINGS` (BCS / OSM / manual / api_sync) always narrate in tour mode — no Layers gate. `NarrationGeofenceManager.TOUR_HIST_LANDMARK_CATEGORIES` swapped from `BUILDINGS, ENT, LODGING` to `LANDMARKS, ENT, LODGING`.
- **2026-05-01 (S216):** **`historical_note` column dropped (Room v15 → v16).** Pre-existing rule: `historical_narration` is the strict pre-1860 audio script. `historical_note` was a duplicate-purpose blurb field used for display + search + legacy fallbacks; consolidated into `description` (no gaps), then column dropped end-to-end across PG, Kotlin (5 files), cache-proxy + web admin (5 files), Room schema bump, KSP regen.
- **2026-05-01 (S216):** **Auto-bake publish chain into Gradle preBuild** (S180/S185 carry-forward done) — every `:app-salem` build now runs `publishSalemContent` (4 scripts) + `verifyBundledAssets` so admin-tool edits in PG reach the device automatically. Escape hatch `-PskipPublishChain`.
- **2026-05-01 (S216):** **Walk-sim onboarding owns the directions session** — `TourViewModel.startTour(skipOnboarding: Boolean = false)` flag; both walk-sim entrypoints opt out of the parallel `onboardToNearestPolylinePoint` path so the drift-reroute loop can't paint a green directions polyline on top of the per-leg overlays.
- **2026-04-26 (S185):** **Tours are polyline-only paths; POIs govern their own narration.** Operator clarification during walk-sim debug: a tour is just a line for users to follow; POI geofences fire narration based on user proximity, independent of any tour. Tour stops in PG with NULL `poi_id` are internal authoring waypoints (used by admin to compute legs) — they're filtered out of the asset's `tour_stops` table. `TourEngine.startTour` no longer errors on empty stops. Historical Mode is auto-skipped when the active tour has zero user-facing stops (was previously building an empty whitelist that silenced every POI). **PG is the only source of truth for tours/stops/legs**; the 5 historical Kotlin-curated tours are dropped from the asset and will be re-authored via admin when wanted.
- **2026-04-26 (S185):** **Asset schema must be Room-canonical, not legacy SQL.** Two latent landmines were flushed: (1) asset's `tours`/`salem_pois`/etc. had `DEFAULT` clauses from `salem-content/create_db.sql`, but Room codegen schema has none → `TableInfo.equals` mismatch → `fallbackToDestructiveMigration` wiped the asset on every install; (2) `PRAGMA user_version` lagged the `@Database(version=N)` value → upgrade migration ran → fallback destructive. New `cache-proxy/scripts/align-asset-schema-to-room.js` is the canonical bridge: it rewrites every Room-managed table using the exact `createSql` from `app-salem/schemas/<DB>/<v>.json` and stamps both `identity_hash` and `user_version`. Must run last in any publish chain.
- **2026-04-26 (S184):** Admin walking-route rendering uses **anchor + clip** for marker continuity. Each leg's polyline is anchored to its from/to waypoint markers AND clipped to the polyline-points closest to those markers, eliminating both road-snap gaps and overshoot doglegs. The selected leg renders into a custom Leaflet pane (z 450) so it stacks above the green legs and the dashed connector but below the numbered waypoint markers. Pure rendering policy — no engine change.
- **2026-04-26 (S183):** Tour walking polylines become **authored content**, not runtime computation. New PG table `salem_tour_legs` stores per-leg polylines computed at admin time via the same on-device router; runtime APK reads baked legs directly (S184 follow-up). Satisfies S182's content-not-engineering rule by moving the work entirely to authoring time.
- **2026-04-26 (S182):** Tour route fidelity declared content-authoring, not engineering. New HARD RULE `feedback_tour_routing_is_content_not_engineering.md` — operator hand-curates tour stops; routing graph, geocoder, anchor schema, and Router changes are parked for V1.
- **2026-04-26 (S182):** S181 OSM-policy reversal RETRACTED. Diagnosis was wrong (`Router.routeMulti` is per-leg P2P + concat; visual divergence is from POI-centroid input, not from a graph deficiency). Plan archived to `docs/archive/`. `feedback_no_osm_use_local_geo.md` restored to S178 surgical-only constraint.
- **2026-04-26 (S180):** V1 manifest stripped of network permissions; V1 feature gates R8-stripped from binary. App network-incapable at OS level. Rule: `feedback_v1_no_external_contact.md` (zero outside contact except GPS).
- **2026-04-23 to 04-25 (S157/S158/S171/S175/S178):** MassGIS L3 ingest, Witchy basemap, custom WickedMap engine (parallel system), on-device router shipped (TIGER bake + Directions UI), surgical wharf-walkway osm_id allowlist. Detail in archive.

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

**Sessions completed:** 216. **Internal ship target: 2026-08-01** (operator-confirmed S201). Salem 400+ peak attendance October 2026 (~1M visitors).

---

## POI Inventory (refreshed S180 from live PG)

- **PG `salem_pois`:** 2,149 live (S216 dedup: 220 dead hard-deleted + 43 live cluster losers hard-deleted + 1 Gardner-Pingree dup, all merged before delete). 484 reclassified `HISTORICAL_BUILDINGS` → `HISTORICAL_LANDMARKS` (curated buildings now 105). `historical_note` column dropped S216; content lifted to `description` (143 POIs touched, 0 gaps).
- **Room DB:** at `app-salem/src/main/assets/salem_content.db`, **v16 schema** (S216), identity_hash `2b9838099e6df9b9a616f538c6e99a0c`. Witch Trials intact (npc_bios 49 / articles 16 / newspapers 202). v11 (S186) `is_civic_poi`. v12 (S192) `historical_narration`. v13 (S193) `is_historical_tour`. v14 (S195) `is_historical_property`. v15 (S198) dropped `body_points` from WitchTrialsNewspaper. v16 (S216) dropped `historical_note` from SalemPoi (content consolidated into `description`; `historical_narration` is now the canonical pre-1860 narration field).
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
