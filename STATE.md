# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — must stay under 200 lines.

**Last updated:** 2026-05-24 — **Session 293.** OMEN-directive sweep (S293 opener) → **OMEN-025 (LMA purchase-validation + content-integrity) Phase 1 kickoff.** Read all OMEN directives; OMEN-025 (issued OMEN-S057) is the headline: a mandatory, one-time, first-run online validation that **retains nothing** (Play Integrity + signed content manifest; content encryption → Phase 2). **Does NOT break the offline tour promise** — it's a one-time licensing handshake in front of an app that still runs fully offline; only `INTERNET` returns to the release manifest for that one call. Wrote an operator-approved, OMEN-reviewed (NOTE-L023) **8-session Phase-1 plan** (`docs/plans/OMEN-025-anti-piracy-phase1.md`) and **shipped Sessions 1–2**: Layer-2 signed content manifest (`sign-content-manifest.js` + `verify-bundled-assets.js` extension + RSA-2048 key `~/keys/content-manifest.pem` + committed `res/raw/content_manifest_pubkey.der` + `content-manifest.json`/`.sig`; all sig/tamper/CI tests green) and `core` activation foundations (`DeviceKeyManager` hardware-keystore EC P-256, `ActivationReceipt`, `ActivationStore`; deps + proguard; `:core` compiles). **Binding decisions:** cert pinning DROPPED (plain HTTPS + host guard); Worker is **LMA-owned on `*.workers.dev`, not DAG.com**; OMEN-004 unit tests required. **Zero Android-runtime/Room/PG impact** (build-pipeline + new `core` code + committed assets only). **Next = Session 3** (`ContentManifestVerifier` + JVM tests). Live gate (S6–S7) blocked on Play Console ← operator P.O. Box / D-U-N-S.

### Prior — S292 (2026-05-23) — pruned to one line at S293 close

Security / credential audit: scanned full codebase + all 574 commits — **CLEAN**, nothing exposed/to-delete; secrets correctly externalized. Hardening (`95ac85f`): added keystore/key/google-services/env-production patterns to `.gitignore`. Zero Android/Room/publish-chain/app/PG impact. Detail in `docs/session-logs/session-292-2026-05-23.md`.

### Older prior-session blocks pruned (S285+S286+S287 condensed 2026-05-22 at S288 close; S288+S289 condensed 2026-05-23 at S292 close; S290 Historian-role + S291 web-admin-followups condensed 2026-05-24 at S293 close) for the 200-line cap; see `SESSION-LOG.md` for S284–S293 + `docs/session-logs/` for full detail. (S289: 1692 raster recovered from all-transparent bake, `TileSourceManager` minZoom bug fixed, per-map zoom-envelope pivot, 1692 re-baked z11-z14 / 91 tiles. S288: 1692 Upham georef precision passes + reusable tooling `mask_to_frame.sh`/`gcp_picker.html`/`points_to_gdal_translate.sh`/`build_cutline.py`/`webp_compress_historical.py` + Pixel 8 GPS demo-prep.)

### V1 ship-cliffs (all closed)

Cliff 1 (Play 200 MB ceiling) closed S256 via install-time Asset Pack; Cliffs 2 (`largeHeap` + 60° tilt OOM) + 3 (`allowBackup=true`) closed S255. Build/install workflow with asset packs lives in `CLAUDE.md` Key Paths block + `feedback_adb_install_after_db_rebake.md`.

---

## TOP PRIORITY — V1 launch triage (operator-confirmed 2026-04-29; revised S203 close 2026-04-30)

> **Internal ship target: 2026-08-01** (73 days as of 2026-05-20). One month tighter than the prior Sept 1 anchor. Salem 400+ peak attendance is October 2026.

### Operator-side legal / business (in flight)

- **Form TX copyright** — counsel handling. Deadline **deferred 2026-05-17 (S276) from 2026-05-20 → 2026-07-01** (operator direction). Confirm with lawyer that the filing names **Destructive AI Gurus, LLC** (not Dean Ellis personally) as claimant.
- **Operating entity — RESOLVED 2026-05-11 (S245).** Destructive AI Gurus, LLC formed as MA LLC, Filing # 202626416630, William Francis Galvin signed. Principal office 111 Essex Street, Beverly, MA 01915. Contact email contact@destructiveaigurus.com. All 4 privacy-policy fields backfilled in `docs/PRIVACY-POLICY-V1.md` v1.1. Certificate PDF kept locally at `LegalDocuments/` (gitignored).
- **Play Console developer account** — operator owed; multi-week ID-verification clock starts immediately. Register the account under **Destructive AI Gurus, LLC**, not personal identity.
- **Operating agreement** — none filed yet (MA doesn't require for single-member LLC, but counsel may recommend a written OA before AAB upload for veil protection). Operator-side, not a code-side blocker.
- **Upload keystore second-medium backup** — covered (operator confirmed multiple secure methods).
- **TTS quality monitoring** — operator does end-to-end Lenovo listen-tests routinely as part of QC.

### Field test (carry-forward to next session)

- Operator drove ~50 min with wife on 2026-04-30 evening on the S206 first-build APK (pre-fixes). Logs reconstructed the missing-POI experience as queue-starvation + tour-mode default-OFF + `is_tour_poi` half-honored. Lenovo now carries the post-fix S206 APK; next drive should validate (a) Lappin Park / Curtis Park / Carlton School render in tour mode regardless of layer toggles, (b) starting a tour with explore-checked layer boxes no longer silently silences markers + narration. Operator's wife's next Salem walk also still owes #25 / #29 Fix 1 / #3 full POI-narration loop validation from S204.

### Hard gate (S214 close, 2026-05-01)

**Salem Oracle (:8088) and SalemIntelligence (:8089) are paused for major provenance reformulation.** Until the operator signals completion, do NOT propose: new admin-tool integrations against `oracleClient.ts` / port 8088, new consumers of `/api/intel/*` at :8089, narration regeneration sourced from these services, hero-prompt regeneration from these services, POI description backfills from these services. Manual / curated content (operator-authored, MassGIS MHC, TigerLine-derived, hand-edited Wikipedia with operator citations) is unaffected. Existing `salem_pois` / `salem_tour_stops` data sourced from these services is grandfathered for now but may need re-attribution before AAB upload — the operator will direct this when the reformulation lands.

### Recent session outcomes — see `SESSION-LOG.md` (last 10) and `SESSION-LOG-ARCHIVE.md` (older) for detail

(Per-session detail lives in `SESSION-LOG.md` and `docs/session-logs/`. The "Last updated" para at the top covers this session's headlines.)

### S294 opener (parked at S293 close)

**TOP: resume OMEN-025 Phase 1 at Session 3.** Follow the stepped plan `docs/plans/OMEN-025-anti-piracy-phase1.md` (embedded in the S293 live log). Sessions 1–2 done. **Session 3 (NEXT, cleared, unblocked):** Android `ContentManifestVerifier` — public-key load (`res/raw/content_manifest_pubkey.der`) → verify `.sig` → tiered `verifyCheap`/`verifyFull` (D4) → JVM unit tests (sig-valid/invalid/byte-flip/stale; OMEN-004 NOTE-L023 #3) → benchmark on Pixel 8 + Lenovo. Then S4 (Worker, LMA repo `*.workers.dev` — start with the endpoint-contract doc for OMEN), S5 (network path), S6–S7 (⏸ Play Console: Play Integrity + gate UI + offline re-scope + rollout), S8 (disclosures + cred inventory).

**OWED to OMEN (per-item NOTE-L023 response in next report):** confirm `~/keys/content-manifest.pem` generated (for cred inventory #5); confirm the `*.workers.dev` decision so OMEN **withdraws NOTE-DA005**; ack #1 (pin dropped) / #3 (unit tests) / #4 (residual recorded).

**Carry (S290/S291): historian browser visual smoke** — log in at http://10.0.0.229:4302/admin with the historian credential; confirm no left sidebar, compact `HistorianPoiDialog`, non-draggable markers, save attributes `actor='Historian'`/`data_source='Historian'`, and "+ New POI" creates with `data_source='Historian'`.

**1692 / historical-maps docket (untouched since S289 — resumes after OMEN-025 reaches a pausing point, or per operator direction):**

**1692 alignment iteration.** S289 final bake: 6 GCPs, no cutline, z11-z14 envelope, 91 tiles, on Lenovo. Owed: visual validation walk through all 4 zoom levels in Salem Village + judgement on whether 6-GCP TPS is "good enough for Upham" or whether more free picks are warranted. Operator can add more GCPs via the picker — free-pick mode is wired in (click Upham → click Leaflet → auto-numbered #100+).

**Per-layer zoom clamps in `HistoricalMapsBottomSheet`.** Architectural follow-up (~30 min). Add `minZoom`/`maxZoom` to each `TileSpec`. `applyHistoricalOverlaySelection` sets `mapView.minZoomLevel/maxZoomLevel` per selection. Off → revert to global z11–z21. Higher value once 1700/1851/1911 are aligned.

**1700 Phillips precision pass.** Same workflow as 1692: mask_to_frame → picker (new anchor list — downtown Salem features: Witch House / First Church / Pickering Wharf / Ward House / Old Jail) → TPS warp → bake at z13-z17 envelope.

**1911 Walker precision pass.** Same workflow. Target z14-z18 envelope. Wider coverage than the others (includes Beverly proper).

**Basemap blank-at-low-zoom — 3 options.** (a) Widen Salem-Custom bake bbox from "+10mi" to ~30mi (covers North Shore + Cape Ann); planetiler re-run, ~30+ min. (b) Wire TigerBase as fallback at z3-z12 (per `project_tigerbase_pipeline.md` baked + web admin preview shipped, Android wiring still pending — task #15 from S252). (c) Live with it — z11 is diagnostic regional context, not display.

**GPS "follow until manual pan" — still owed (carried from S288).** `userPannedSinceFix` flag wired to MapView touch listener (S288 demo workaround was "tell user not to pan"). Dead-zone tightening + initial-center-on-user landed at S288 close.

**1692 cutline-mask post-process (deferred).** gdal's inverse-TPS pixel-space cutline transform keeps self-intersecting; alternative is rasterizing the cutline against the warped TIF directly via PIL/shapely (bypasses gdalwarp entirely). Sidesteps the validity issue and gives a clean Upham-shaped clip. Not blocking — current bake renders fine without it for the operator's "preserve full area" preference.

**Buildings-Outline top-layer — CANCELED** (operator: *"the buildings are good the way they are"*). Removed from docket.

**Plan original Phase 4 partly done** — `verify-bundled-assets.js` regex extended in S286 P1 + S287 (now covers 1692). Still owed: asset bake hook in `CLAUDE.md` publish chain documentation, `ASSETS-MANIFEST.md` Historical Maps section.

**Plan original Phase 5** — operator field-walk around Salem Common switching through all 4 maps still owed. Validates alignment + on-the-ground UX.

**WickedMap clamps** — `MapCamera.minZoom=16.0` and Salem bbox clamp affect operators entering WickedMap from a zoomed-out or out-of-bbox production view. Acceptable for V1 scope; consider widening if it bites.

**Chatbot V1 plan still parked.** `docs/plans/Chatbot_V1.md` — Phase 0 recall spike is the first action when operator gives the go-ahead.

**S282 carry-forward (Paul Revere's Bell miss) still standing:** next walk-sim captured against extended filter `WALK-SIM|NarrationMgr|NARR-QUEUE|NARR-STATE|NARR-GEO|NARR-GATE` (`grep -v WickedAnim`). Re-run `tools/s281_hb_coverage_v2.py` and grep captured `NARR-GEO` lines around step ~1024-1140 for the Bell's `ENTRY` (or absence) — distinguishes scanner-never-saw-it from enqueue-silently-skipped. Lenovo logcat buffer is still 16 MiB.

**S284 device-state note:** S284's `sheet_collection.xml` fix + S285 plan are NOT on field devices (Pixel 8 + Lenovo still carry S282 build). Both cosmetic-only / planning; not blocking the next walk-sim.

**Node 20 → Node 24 GitHub Actions deprecation** lands 2026-06-02 (~12 days). Cheap one-line workflow bump (`.github/workflows/ci.yml`) when in scope.

### Older opener carry-forwards (S266–S271)

Recon Layer 2 + Passport S5 + S267/S268 latency/migration backlog + S266 portrait validation are now in `docs/archive/STATE_openers_removed_2026-05-21.md`. Pull from there if any of those threads re-enter scope.

**Phase A portrait smoke test (still owed from S264/S265):** operator-driven Pixel 8 visual smoke test — walk every UI surface (every menu, dialog, sheet, FAB cluster, screen state) in BOTH landscape and portrait on the Pixel 8 (auto-rotate first needs enabling per S263 diagnosis). Screenshot into `docs/pixel8-portrait-survey/<surface>-<orient>.png`. Output: `docs/pixel8-portrait-survey/MATRIX.md` annotating each surface as `OK / minor / major / blocker` per orientation. Drives Phase B-D scope.

**Carry-forward note (Lenovo install recovery):** if `adb uninstall` returns `DELETE_FAILED_INTERNAL_ERROR` again on field-test devices, sequence is `am force-stop` → `pm clear` → retry `uninstall` BEFORE running `bundletool install-apks` — preserves the WAL-safety rule from `feedback_adb_install_after_db_rebake.md`.

**Optional S265 follow-ups on S264 GPS work:**
- Investigate the 5-task-restart cycle observed in the original trip's 19:36-19:45 window (no longer happening on the new build per single-PID drive log — but underlying cause not nailed; may have been a downstream symptom of the same race).
- Watch for the `Cancel-before-register detected` warn-log on field walks — if it ever fires, confirm the zombie cleanup path works as designed and isn't itself producing follow-on issues.

**Remaining docket (held behind portrait work):** Wave 4 (Spanish i18n, ~2-3 sessions) / content authoring docket (66 short narrations, 221 missing subtopics, 14 oversized historicals, ~5-10 pre-1860 landmark backfills) / admin UX polish (Mass Edit re-export-stale button, Field Edits sync error mapping, Mass Edit success toast) / Wave 5 post-V1 arch decomp doc. These resume after the portrait plan reaches Phase F or is otherwise paused.

**Original V1 wave plan + decision ledger remains in `docs/plans/v1-health-audit-2026-05-12.md`.**

- **Wave 4 i18n (~2-3 sessions, V1 scope):** externalize 29 hardcoded Toast strings + any survey-found user-facing literals → `app-salem/src/main/res/values/strings.xml`; build `values-es/strings.xml` (Spanish UI); locale switcher in Settings + locale-aware TTS engine selection (`TextToSpeech.setLanguage(Locale.forLanguageTag("es-ES"))` with fallback in `NarrationManager.kt:107`); narration stays English V1; smoke-test on Lenovo with `adb shell setprop persist.sys.locale es-ES`.
- **Wave 5 (no V1 code):** write `docs/plans/post-v1-architecture-decomposition.md` — SalemMainActivity 18,623-LOC monolith ViewModel-extraction plan + Manager/Repository consolidation rules + estimated 3–4 week post-V1 refactor scope.

**Wave 3 leftovers:** operator field-walk validation of asset-pack build on Lenovo (z16-z19 + tilt 30/45/60° + MBTA SuperAdmin path through cache-proxy — verified via OfflineTileManager logs + 2/2 androidTest but no real walk yet); optional `OfflineTileManager` swap to `AssetPackManager.getPackLocation` direct (skips ~1.8s copy + saves 262 MB external — needs `play:asset-delivery` dep); AAB signing-for-upload verification (`~/.gradle/gradle.properties` keystore props vs CLI debug-fallback per SHIP-CHECKLIST.md).

**Wave 2 leftovers:** `hero/` dir migration audit (S239 said "385 dead" but PG has 949 live `image_asset LIKE 'hero/%'`; per `feedback_hero_revisit_later.md` this is parked for full revisit later — don't flag as ship blocker); bump cache-proxy on dev-box for the S255 `/mbta/upstream/*` route via systemd/pm2; field-validate Wave 1+2 build on Lenovo through SuperAdmin toggle (S255 walk-sim ran clean but SuperAdmin not exercised on-device).

**Carry from prior sessions (still open):**

- TigerBase Android wiring (S252 TASK #15) — SuperAdmin+outside-Salem-gated; data live at `10.0.0.229:4300/tigerbase/<layer>/{z}/{x}/{y}?scope=conus` (5 layers Z3-Z12 baked); osmdroid TileSource or 5 stacked overlays. Optional polish: PMTiles pack (~33% reclaim), Z3-Z10 recompress `method=6` (~5-10%), Z13/Z14 decision.
- Radar tiles bypass cache-proxy (`SalemMainActivityRadar.kt:52,127` → `mesonet.agron.iastate.edu` direct; acceptable but document).
- Persist `ufw allow 4300/tcp` + `4301/tcp` on dev-box; auto-start TCP collector via systemd; `WeatherRepository` connectTimeout bump 15s → 45s + cold-connect retry (optional); `PREF_MBTA_BUS_STOPS` defaults FALSE (Transit submenu to enable).
- **Operator field-validation of parked services on Lenovo via SuperAdmin** (Aircraft / Radar / Webcams / METAR — same hook path as the MBTA one we just verified).
- **WEB SuperAdmin tab field-validation** — refresh http://localhost:4302/admin → Super Admin → Test each of the 8 cards.
- **Optional SuperAdmin icon swap** — currently `@drawable/ic_debug`; one-line change at `toolbar_two_row.xml:117`.

### Other carry-forwards (lower priority — pull when in scope)

S245+ aged backlog (Rendering / Content / Tools — 26-55 sessions stale by S271) and 70+ sessions stale (Rebuild-AAB-since-S180, GPS-OBS heartbeat, re-author 5 deleted Kotlin tours, McIntire content drain) both moved to `docs/archive/STATE_carryforwards_removed_2026-05-16.md` (S271).

- Operator field-validation backlog (S221 detour / S220 subtopic / S217 BusinessLabel + FAB override; `is_tour_poi=true` lint cleanup; borderline-historical commercial opt-in audit); Tier 3 disk reclaim ~4.4 GB + `.gitignore` audit; S216 follow-ups (John Ward House tour-leg fix, HIST_LANDMARKS icon+hero regen, description rebuild, dequeue 40m staleness); S215 suspicious-leg graph (Dr. K 13/14, WD1 3/6/10/11); PoiDetailSheet/Find clearXXX/stopXXX no-ops; S206 speed-aware queue cap, DensityTour 0-stop fallback.

### S205+ — V1 feature additions (priority order, KEPT for V1)

1. **Halloween / October seasonal layer** — date-checked content overlay (different POI markers, ghost-tour POIs unlocked October 1, photo-spot pins, October-only event narration). Zero network. Reuse existing geofence + narration paths. Scope tight, ~1 week.
2. **Historical-map FAB picker, 4 maps for V1** (1851 McIntyre / 1874 Hopkins / 1906 Sanborn / 1911 Walker) — **SHIPPED end-to-end on Lenovo in S286** (Phases 1-3+3.5+3.6, commits `b8c02fb` / `bdcd1f7` / `72b4ee0`). All 4 maps WebP-compressed (93%, salem_tiles.sqlite 262→444 MB net). Picker in WickedMap prototype; camera-passthrough from production map. **Remaining**: precision GCP pass for 1874/1906/1911 (S286 used coarse 4-corner, ±50-100m), publish-chain docs, ASSETS-MANIFEST update, field walk. Plan at `~/.claude/plans/groovy-launching-popcorn.md` (S285).
3. **Tour-completion certificate (PDF on-device)** — end-of-tour souvenir with route + triggered POIs + date + Salem-themed border. Zero network. ~2-3 days.
4. **Salem Heritage Trail polyline tour — YELLOW, not red** (the trail was repainted yellow). Hand-trace via admin or import official KML if available. ~1 day. See memory `reference_salem_heritage_trail_yellow.md`.

### V1 features deferred / killed (S201)

Bark hero voice clips SKIPPED (Android TTS only); Hotel B2B partnerships post-launch; Pre-launch beta small (2-3 friendly eyes); iOS port TBD.

### Marketing (operator-led, before Aug 1)

Channel: Salem Chamber of Commerce + local-first. Asset packet (1-page sell sheet, feature graphic, 3-5 hero screenshots, 2-para press blurb) drafted in a content-only session — operator provides photos + final approval, I draft text + screenshot frames.

---

## Older backlog (S159–S200, TRIAGED S203)

**V1.0.1:** multi-admin auth + actor-from-session (S196); PoiEditDialog inline history panel (S196); `year_established` backfill on 500+ HIST_BLDG rows (S192); DirectionsSession JVM tests; POI location verifier batch-run (S162); Witchy tile-bake bbox extension for Beverly (S159); water animation tuning (S172). **V2:** driving mode (S195, V1 walking-only); routing-jvm Option 2 + walk-sim cleanup (S179); MHC hidden-POI companion importer (S163). V1-open content carry-forwards 70+ sessions stale moved to `docs/archive/STATE_carryforwards_removed_2026-05-16.md` (S271); osmdroid → WickedMapView migration archived alongside.

---

## Architectural pivots & rule changes (recent → older)

- **2026-05-10 (S241):** Mass-Edit POI workflow live (`admin-mass-edit.js`, `MassEditTab.tsx`, export/import/apply round-trip; `is_deleted` writable; 24 enum dropdowns via OOXML dataValidations injection; deps `xlsx`, `multer`, `jszip`).
- **2026-05-01 (S216):** `HISTORICAL_LANDMARKS` split from `HISTORICAL_BUILDINGS` (Layers-toggle gated); `historical_note` column dropped (Room v15→v16, lifted into `description`); auto-bake publish chain wired into Gradle preBuild (`-PskipPublishChain` escape); walk-sim onboarding owns directions session.
- **2026-05-17 (S278):** Katrina's Collection **v2 ghost batch SHIPPED end-to-end** on Pixel 8 + Lenovo. New hard-rule `feedback_si_is_reference_only.md` — SI is clue-lookup only, never content source; LMA `salem_pois` is the source of truth. `mine-si-clues-for-ghosts.py` injects 74/107 SI/LMA-clued profession overrides into `generate-ghost-portraits.py` (pool-roll fallback for the other 33). 5-pass prompt overhaul to cartoon/witchy/spooky/1690s-period/haunted-Salem-backgrounds; CFG 2.5→4.5; new `witchy_props` pool (30 entries). B-image subtlety: extreme `expressions_alt` entries replaced with micro-expressions, denoise 0.75→0.45, `--regen-b` re-rolls from current pool. Full batch 858 s, regen-B 298 s. Pack PNG→webp (95% reduction, 9 MB final asset). `CollectionSheet.kt:buildRowsForCollection()` filters to ghost-eligible only — List + Badges both show 107. No schema changes. Room v23 / identity_hash `4db15f...` unchanged. New scripts: `mine-si-clues-for-ghosts.py`, `build-ghost-review-gallery.py`. Parametrized `populate-ghost-personas.py --manifest`.
- **2026-05-17 (S275):** Katrina's Collection Phase 2 + Phase 3 SHIPPED — 107 paired ghost portraits (A normal + B fourth-wall-break smirk, 2% runtime swap), 8 frame overlay PNGs, Room **v22 → v23** (identity `4db15f763c9dbd5a529d24b128cecada`). New `ghost_asset_a/b/ghost_frame` columns on `salem_pois` + denormalized into `collection_entry`. New `GhostResolver.kt` runtime, `CollectionSheet` augmented with tabs (List | Badges) + witch-hat row stamps replaced by ghost-portrait tiles. ~4.6 MB asset payload (4.2 MB portraits + 0.36 MB frames). New memory: `project_katrinas_collection_ghost_asset_pairing.md`. 5 generator scripts (`generate-ghost-portraits.py`, `generate-frame-overlays.py`, `pack-ghost-portraits.py`, `populate-ghost-personas.py`, `build-ghost-gallery.py`).
- **2026-05-16 (S270):** Recon photo triage tool Layer 1 (bulk-cull) shipped on top of S229/S231 burst-photos infra. New `web/src/admin/ReconTriageTab.tsx` admin tab; `cache-proxy/lib/admin-burst-photos.js` extended with persistent `.kept.json` per-session keep-flag, atomic `commit-cull` (move-everything-unkept to `.deleted/`), `adb devices` lister, and SSE-streaming `adb pull` + python-organizer endpoint. Zero new dependencies. End-to-end smoke verified.
- **2026-05-15 (S269):** POI Passport architecture matured to **pool ∩ proximity** model. Global passport (`default_salem_walking`) defines the "pool" via filter SQL (categories + flags + min broadcast radius + year range); per-tour passports are walk-derived snapshots of `pool ∩ polyline-proximity`, edited via the new Tour-editor walk dialog. Recompute is destructive (operator-confirmed: "wipes everything; manual edits persist until next wipe"). Tour metadata is now fully derived: `recomputeTourMetadata` rebuilds `stop_count + distance_km + estimated_minutes` from `salem_tour_legs + salem_tour_stops` on every mutation + on PATCH (operator-supplied values get overwritten). New "stamps" UX: Android tour cards + web admin tour list both show passport-bound POI count instead of legacy `stop_count` (free polyline waypoints). New `auto_bake` column on `salem_passport_filters` separates filter-driven (auto-bake, global pool only) from operator-curated (manual, per-tour) rows. Five Lenovo bundletool installs in one session.
- **2026-05-04 (S224):** Session-start protocol amended lean → lean+state — `STATE.md` + most-recent live log mandatory. `enqueueNarration` cancel-stamp scoped to direct-play (fixed walk-sim queue stall from S146 Idle-guard collision).
- **Older pivots (S157-S185, 2026-04-23 → 04-26):** V1 manifest stripped of network permissions; tours polyline-only with PG source of truth; asset schema Room-canonical via `align-asset-schema-to-room.js`; MassGIS L3 / Witchy basemap / WickedMap prototype / on-device TIGER router / wharf-walkway osm_id allowlist. Detail in `SESSION-LOG-ARCHIVE.md`.

---

## Phase Status (V1 lens)

Phases 1-9 + 9A+ + 9P.A/B + 9T + 9U + 9X: **COMPLETE**. Phase 10 (production readiness): first signed AAB built S180, asset-pack reorg S256, ship-cliffs 1/2/3 closed. Phase 11 (ASO/Play Store): operator-led, post-AAB-upload. **9Y/9Z/9Q/9R deferred** (V1.0.1+, no V1 ship dependency). Cross-project TigerLine stalled 2026-04-21; SalemIntelligence PAUSED S214.

**Sessions completed:** 292. **Internal ship target: 2026-08-01.**

---

## POI Inventory (refreshed S180 from live PG)

- **PG `salem_pois`:** 2,039 live (S271 refresh — net +2 from S270's 2,037). 87 soft-deleted (per `feedback_dedup_is_manual.md`, soft-deletes are operator-managed via Lint tab; `WHERE deleted_at IS NULL` filter in `publish-salem-pois.js:206` keeps them out of the asset DB). 484 reclassified `HISTORICAL_BUILDINGS` → `HISTORICAL_LANDMARKS` (curated buildings now 105). `historical_note` column dropped S216; content lifted to `description`.
- **Room DB:** `app-salem/src/main/assets/salem_content.db`, **v23** (S275), identity_hash `4db15f763c9dbd5a529d24b128cecada`. **v23 added `ghost_asset_a/b/ghost_frame` columns on `salem_pois` + denormalized into `collection_entry` for Katrina's Collection Phase 2.** Witch Trials intact (npc_bios 49 / articles 16 / newspapers 202). **PG end-state at S279 close: 3 tours (Density / Dr. K's / LongWalk) / 3 collections / 309 collection_entries** — Whole-Salem global pool deprecated (`default_salem_walking` + 447 entries deleted S279), TOUR_0001 (`tour_WD1` + 15 stops / 14 legs / 41-entry collection) deleted S279. All collections are direct per-tour walk-derived snapshots with operator-curated `auto_bake=false`. `tour_drks_001_walk_passport` is the default no-tour-active view (sort_order=0). **On-device asset DB matches PG as of 2026-05-18 S281 close** (publish chain (5/5) + debug AAB + bundletool install on Pixel 8 + Lenovo). Older counts (S278 close): 4 tours / 73 legs (S224) / 5 collections / 797 baked entries. **UserDataDatabase v3 → v4 (S274)** renames `passport_visit` → `poi_visit` via hand-written `MIGRATION_3_4` (strict, no destructive fallback per S180 lockdown; preserves paid-user history). **PG schema** rebranded S274: `salem_passport_filters` → `salem_collections`, `salem_passport_pois` → `salem_collection_entries`, FK column `filter_id` → `collection_id`. v11-v22 column-history archived in session logs.
- **APK / AAB:** S256 reorganized into base + asset pack. **Debug APK 95 MB** (was 374 MB pre-S256), **Release AAB 129 MB total** (`base-master.apk` 55.2 MB compressed + `salem_tiles_pack-master.apk` 261.6 MB compressed). `bundletool get-size total` = 125 MB compressed download. Base APK well under Play Store's 200 MB compressed ceiling; asset pack sits in the 2 GB pack ceiling. AAB is upload-eligible. `poi-icons/` at 544 MB is still the next pre-Play-Store size target.
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

1. NOTE-L014 / OMEN-008 — Privacy Policy V1-minimal Posture A shipped S145 (`docs/PRIVACY-POLICY-V1.md`); full OMEN-008 draft pending. NOTE-L015 SalemCommercial cutover PARKED POST-V1.
2. SalemIntelligence anomaly relay — `~/Development/OMEN/reports/locationmapapp/S153-si-anomalies-relay-2026-04-20.md` (ANOM-001, ANOM-002). NOTE-L019 restrooms_zombie.png regen LOW.

---

## Pointers to detail

- **Topic-indexed lookup of prior work:** `MASTER_SESSION_REFERENCE.md`
- **Sessions:** `SESSION-LOG.md` (rolling 10) / `SESSION-LOG-ARCHIVE.md` (older) / `docs/session-logs/session-NNN-*.md` (live)
- **Plans:** `docs/plans/` (active) / `docs/archive/` (older + removed STATE content)
- **Architecture / tech stack:** `CLAUDE.md` + codebase. **Legal:** `GOVERNANCE.md`. **IP:** `IP.md`. **Commercial:** `COMMERCIALIZATION.md`. **UX/arch decisions:** `DESIGN-REVIEW.md`.
