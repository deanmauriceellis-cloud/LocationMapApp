# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — must stay under 200 lines.

**Last updated:** 2026-05-13 — **Session 264 (GPS-flakiness root-cause fix shipped + on-road validation green).** Operator drove the freshly-installed S263 debug build and returned "GPS is hung up, was very flakey... when I restart it will work again". Pivoted from S264's planned Phase A portrait smoke test to a full GPS root-cause investigation. Pulled `dumpsys location` (GNSS chip healthy — 407 fixes / 7.4% failure / 4.4 m mean accuracy / CN0 45.5 dB-Hz; but `gps provider: ProviderRequest[OFF], mStarted=false changed +10m39s ago`), 22 MB app debug log, 1592-row journey DB, and 1056 recon-camera bursts. Diagnosed 4 root causes: (1) **60-sec default GPS rate** hardcoded in `MainViewModel.kt:140` from the S126 battery-conservative era — too slow for high-zoom tracking; (2) **walk-sim race** — `setManualLocation()` called `stopLocationPolling()` on every walk-sim tick, killing tour-engine's fresh fast Flow within 14 ms (operator's mis-tapped Walk button was enough to strand the app); (3) **zombie callback leak** in `LocationManager.kt` — `awaitClose { removeLocationUpdates }` could fire before the `requestLocationUpdates` Task finished registering, leaving ghost callbacks alive forever (trip log evidence: two concurrent `onLocationResult #234`/`#57` counter series fighting); (4) **`stopWalkSim()` never restored `_locationMode = GPS`** — after walk-sim stops the cascade left the app in MANUAL with dead subscription. Patched ~40 LOC across 3 files: `MainViewModel.kt` (default `1_000L / 500L`, removed `stopLocationPolling` from `setManualLocation`, new `endManualMode()`, idempotent `onPermissionGranted`), `SalemMainActivity.kt` (all 3 `stopWalkSim*` variants call `viewModel.endManualMode()`), `LocationManager.kt` (`registerCompleted` / `cancelArrivedBeforeRegister` flags clean up late-registered zombies). First build failed with `@Volatile`-on-local-var; dropped to plain `var` (both flags touched only on Main looper). Built debug AAB (28 s green), uninstalled previous, bundletool installed on Pixel 8. Operator took the patched build on a drive. **Five health checks all green:** single `onLocationResult` counter `#1 → #1202` monotonic (was two concurrent series on broken trip); ZERO Flow-cancellation events (was 30+); subscription registered exactly once for the entire 23-min drive; 9× `restartLocationUpdates` all took the in-place `updateRequestParams` happy path (no fresh-launch fallback); journey-table density 1202 fixes / 23 min ≈ 52/min ≈ **1 Hz sustained** (was 4-20 fixes/min on broken trip; max gap 14 s during between-tour 30-s slots, by design — broken trip had a 47.5-min total dropout). Operator close: *"I took the app for a drive, much better, check the logs"*. Phase A portrait smoke test rolls to S265 opener. V1 ship-cliff status unchanged (all 3 closed). ~80 days to 2026-08-01 ship; 7 days to Form TX deadline 2026-05-20. **Preceding sessions (S263 Pixel 8 onboarding + portrait plan; S262 align-asset-schema atomic rebuild; S261 plan-write only; S260 full-confidence audit; S259 health sweep + Dr. K POI drafts) detail in SESSION-LOG.md.**

### V1 ship-cliff status (all closed)

- Cliff 1 (AAB > 200 MB Play ceiling) — **CLOSED S256** (install-time Asset Pack: base 55.2 MB / pack 261.6 MB / total compressed download 125 MB; well under both ceilings)
- Cliff 2 (no `largeHeap` + 60° tilt OOM risk) — **CLOSED S255**
- Cliff 3 (`allowBackup=true` vs paid-offline) — **CLOSED S255**

**Build/install workflow with asset packs:** standalone `adb install` does NOT include `salem_tiles.sqlite`. Use bundletool:
```
bundletool build-apks --bundle=<aab> --connected-device --device-id=<serial>
bundletool install-apks --apks=<apks> --device-id=<serial>
```
The `adb uninstall && adb install` pattern (per `feedback_adb_install_after_db_rebake.md`) is preserved — uninstall step still applies; install step swaps to `bundletool install-apks`. bundletool jar at `~/.local/bin/bundletool.jar` (1.17.2).

---

## TOP PRIORITY — V1 launch triage (operator-confirmed 2026-04-29; revised S203 close 2026-04-30)

> **Internal ship target: 2026-08-01** (89 days). One month tighter than the prior Sept 1 anchor in CLAUDE.md / strategic docs. Salem 400+ peak attendance is October 2026.

### Operator-side legal / business (in flight)

- **Form TX copyright** — counsel handling. Hard deadline **2026-05-20** (9 days from 2026-05-11). Confirm with lawyer this week that the filing names **Destructive AI Gurus, LLC** (not Dean Ellis personally) as claimant.
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

### S265+ next-session docket (post-S264 — GPS fix shipped, portrait plan still parked)

**S264 closed:** GPS-flakiness root-cause fix shipped + on-road validated. 4 root causes (60-sec default rate / walk-sim race / zombie callback leak / stopWalkSim doesn't restore GPS mode) patched across `MainViewModel.kt`, `SalemMainActivity.kt`, `LocationManager.kt` (~40 LOC, no new deps). On-road drive: 1 Hz sustained, single counter `#1 → #1202`, zero Flow cancellations, single registration for the whole 23-min drive. Pivoted from planned Phase A portrait smoke test — that rolls to S265.

**S265 opener: Phase A from the portrait plan** — operator-driven Pixel 8 visual smoke test. Walk every UI surface (every menu, dialog, sheet, FAB cluster, screen state) in BOTH landscape and portrait on the Pixel 8 (auto-rotate first needs enabling per S263 diagnosis). Screenshot into `docs/pixel8-portrait-survey/<surface>-<orient>.png`. Output: `docs/pixel8-portrait-survey/MATRIX.md` annotating each surface as `OK / minor / major / blocker` per orientation. Drives Phase B-D scope.

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

- **Wire Android-side SuperAdmin-gated TigerBase basemap source (S252 TASK #15).** Post-V1, lawyer-gated. Data exists at `10.0.0.229:4300/tigerbase/<layer>/{z}/{x}/{y}?scope=conus` (5 layers Z3-Z12 baked, web preview verified). Android-side wiring deferred — new osmdroid TileSource (or 5 stacked overlays) gated on `SuperAdminMode.allowNetwork == true` AND outside-Salem geofence (so Witchy stays the default inside Salem).
- **TigerBase optional polish:** pack CONUS Z3-Z12 to PMTiles (~1 min each layer, ~33% disk reclaim, range-request friendly); recompress old Z3-Z10 tiles with `method=6` for ~5-10% size win; decide whether to ship Z13/Z14 (Z13 ~12 hr bake, Z14 ~50 hr — Z12 is the practical detail wall for TigerLine source).
- **Radar tiles still bypass the cache-proxy.** `SalemMainActivityRadar.kt:52,127` calls `mesonet.agron.iastate.edu` direct. Acceptable since IEM is canonical NEXRAD source so cache-proxy would just pass-through, but worth documenting (or moving behind proxy for purity).
- **Persist the `ufw allow 4300/tcp` + `4301/tcp` rules on dev-box** — operator opened interactively at S251 close. Verify via `sudo ufw status numbered`; should survive reboot.
- **WeatherRepository defense-in-depth (optional)** — bump `connectTimeout` 15s → 45s + retry-on-cold-connect for cleaner UX on transient wifi/ARP hiccups.
- **PREF_MBTA_BUS_STOPS still defaults FALSE** — flip in Transit submenu if you want ~7,900 bus-stop markers; sticky thereafter via S251 `kickDeferredSuperAdminFetches()`.
- **Auto-start the TCP collector** at boot via systemd unit so operator doesn't re-launch `python3 /tmp/salem-collector.py` after every dev-box reboot.
- **Operator field-validation of parked services on Lenovo via SuperAdmin** (Aircraft / Radar / Webcams / METAR — same hook path as the MBTA one we just verified).
- **WEB SuperAdmin tab field-validation** — refresh http://localhost:4302/admin → Super Admin → Test each of the 8 cards.
- **Optional SuperAdmin icon swap** — currently `@drawable/ic_debug`; one-line change at `toolbar_two_row.xml:117`.

### S245+ aged backlog (rendering / content / tools — referenced when in scope)

- **Rendering:** RollingGrassOverlay green-patches on Bluestacks; sprite field-walk near bridge POI; field-validate S238 perspective at z19/z20 + tilt 30/36/42/48°; first-frame layout race in `applyMapExtension` (post-extension `mv.requestLayout()`); cold-start tile-decode spike (Choreographer 1134ms); optional overlay culling / batched Overlay perf candidates.
- **Content:** lint review of 51-POI newly-renderable set; Sweet Boba regression; Layers menu category gap (FOOD_DRINK/SHOPPING toggles don't gate narration overlay); tilt-mode odds-and-ends (S239); splash variants (32 TODO in `docs/SPLASH-ANNOUNCEMENTS-V1.md`); POI/path alignment review of 1962-photo session; Find menu tile + hero review.
- **Tools:** TTS-settings deep-link reliability; SheetJS CVE audit; cascading subcategory pick-list; field-edit UPDATE/CREATE loops; Rapid Recon fresh-walk 1 Hz tick + true-north; auto-fire camera field-test.

### Other carry-forwards (lower priority — pull when in scope)

- **Operator field-validation** of S221 detour / S220 subtopic content / S217 BusinessLabel + FAB override; S220 lint cleanup for spurious `is_tour_poi=true`; borderline-historical commercial opt-in audit.
- **Tier 3 disk reclaim** (~4.4 GB): `tools/poi-icons/`, `tools/hero-triptych/output-full/`, `tools/tile-bake/data/sources/`, `cache-proxy/out/l3-essex/`, `docs/archive/poi-icons-unused-styles_*/`. `.gitignore` audit: `overnight-runs/`, `cache-proxy/poi-cache.json`, `cache-proxy/cache-data.json`, `web/dist`, `web/tsconfig.tsbuildinfo`, `docs/bake-tests/`.
- **S216 follow-ups:** John Ward House tour-leg fix; HISTORICAL_LANDMARKS icon+hero regen; description rebuild post-historical_note-merge; dequeue 40m staleness vs `geofence_radius_m`.
- **S215 suspicious-leg graph investigation:** Dr. K 13/14, WD1 3/6/10/11.
- **PoiDetailSheet/Find cleanup (S224):** `enterFilterAndMapMode/exitFilterAndMapMode` clearXXX/stopXXX no-ops; CLAUDE.md "Pinned" block references stale Room v11 (actual v19).
- **Speed-aware queue cap (S206):** at >15 mph replace queued items or switch to SHORT tier — pick + ship.
- **DensityTour 0-stop decision (S206):** auto-fall-back to Explore or refuse to start.
- **Cross-repo Salem commit (S204→S206):** `Salem/cmd/genbiographies/main.go` + 3 hand-edited bio JSONs — operator-driven.
- **Rebuild signed AAB + 30-min Lenovo smoke** (first signed AAB since S180; V1 gating checklist).
- **Operator field-walks (S204):** GPS-OBS heartbeat + Witch Trials bios + full TTS-gated dwell; **drive regression in airplane mode** (#20).
- **Operator content:** re-author 5 polyline tours via web admin (S185); McIntire content drain (S200).

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

### Marketing (operator-led, before Aug 1)

Channel: Salem Chamber of Commerce + local-first. Asset packet (1-page sell sheet, feature graphic, 3-5 hero screenshots, 2-para press blurb) drafted in a content-only session — operator provides photos + final approval, I draft text + screenshot frames.

---

## Older backlog (S159–S200, TRIAGED S203)

**V1 (open):** McIntire content hand-author (S200); re-author 5 deleted Kotlin tours in PG (S185, operator); GPS + V1-offline drive regression (S161, operator). **V1.0.1:** multi-admin auth + actor-from-session (S196); PoiEditDialog inline history panel (S196); `year_established` backfill on 500+ HIST_BLDG rows (S192); DirectionsSession JVM tests; POI location verifier batch-run (S162); Witchy tile-bake bbox extension for Beverly (S159); water animation tuning (S172). **V2:** driving mode (S195, V1 walking-only); routing-jvm Option 2 + walk-sim cleanup (S179); osmdroid → WickedMapView migration; MHC hidden-POI companion importer (S163).

---

## Architectural pivots & rule changes (recent → older)

- **2026-05-10 (S241):** Mass-Edit POI workflow live. `cache-proxy/lib/admin-mass-edit.js` exposes 3 routes (export/import/apply) under `/admin/salem/pois/*-spreadsheet`; new admin Tooling tab `MassEditTab.tsx` drives end-to-end round-trip. `is_deleted` promoted to a writable "intent" column with bespoke soft-delete / restore apply path mirroring the canonical SQL in admin-pois.js DELETE + POST /restore. 24 enum-bound columns get in-cell pick-list dropdowns via OOXML `<dataValidations>` XML injection (post-write JSZip patcher — SheetJS community writer doesn't emit them natively). New runtime deps in cache-proxy: `xlsx@0.18.5`, `multer@2.1.1`, `jszip@3.10.1`.
- **2026-05-01 (S216):** Bundle of 4 changes — `HISTORICAL_LANDMARKS` split off from `HISTORICAL_BUILDINGS` (MASSGIS-only rows reclassified, gated by existing Layers toggle, default off; curated buildings always narrate in tour mode); `historical_note` column dropped (Room v15→v16, content consolidated into `description`); auto-bake publish chain wired into Gradle preBuild (S180/S185 carry-forward done; escape hatch `-PskipPublishChain`); walk-sim onboarding owns the directions session via `TourViewModel.startTour(skipOnboarding=false)` flag (drift-reroute loop can no longer paint green polyline over per-leg overlays).
- **2026-05-04 (S224):** **Session-start protocol amended lean → lean+state.** STATE.md and the most recent live log are now mandatory at session start (in addition to auto-loaded CLAUDE.md + MEMORY.md). Older logs / SESSION-LOG.md / OMEN files stay on-demand. Memory `feedback_session_protocol.md` rewritten.
- **2026-05-04 (S224):** **`enqueueNarration` cancel-stamp scoped to direct-play.** Walk-sim queue stall (Lappin Park → Rockafellas) was caused by unconditional `narrationCancelForPoiAtMs` bump in `enqueueNarration` colliding with the S146 cancel-for-POI Idle-suppression guard within 500ms of a previously-playing POI's natural Idle. Stamp + newspaper-cancel now only fire on the direct-play branch.
- **Older pivots (S180–S185, 2026-04-23 → 04-26):** V1 manifest stripped of network permissions + R8-stripped feature gates (`feedback_v1_no_external_contact.md`); tours are polyline-only with PG as sole source of truth; asset schema must be Room-canonical via `align-asset-schema-to-room.js`; `feedback_tour_routing_is_content_not_engineering.md`; OSM policy restored to S178 surgical-only allowlist. Full text in `SESSION-LOG-ARCHIVE.md`.
- **Earlier pivots (S157/S158/S171/S175/S178, 2026-04-23 → 04-25):** MassGIS L3 ingest, Witchy basemap, WickedMap engine prototype, on-device TIGER router, wharf-walkway osm_id allowlist. Detail in archive.

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

**Sessions completed:** 264. **Internal ship target: 2026-08-01** (operator-confirmed S201). Salem 400+ peak attendance October 2026 (~1M visitors).

---

## POI Inventory (refreshed S180 from live PG)

- **PG `salem_pois`:** 2,037 live (S253 refresh — net −5 from S241's 2,042) + 87 soft-deleted (S241 mass-edit dropped live count by 28 — dupe cleanup + recategorizations + soft-deletes via the new spreadsheet round-trip; S216 dedup history: 220 dead hard-deleted + 43 live cluster losers hard-deleted + 1 Gardner-Pingree dup, all merged before delete; further trim through S221+). 484 reclassified `HISTORICAL_BUILDINGS` → `HISTORICAL_LANDMARKS` (curated buildings now 105). `historical_note` column dropped S216; content lifted to `description` (143 POIs touched, 0 gaps).
- **Room DB:** at `app-salem/src/main/assets/salem_content.db`, **v19 schema** (S227), identity_hash `745afa3eb4ce04bd7873671ea297b6e0`. Witch Trials intact (npc_bios 49 / articles 16 / newspapers 202). 4 tours / 73 legs (S224). v11 (S186) `is_civic_poi`. v12 (S192) `historical_narration`. v13 (S193) `is_historical_tour`. v14 (S195) `is_historical_property`. v15 (S198) dropped `body_points` from WitchTrialsNewspaper. v16 (S216) dropped `historical_note` from SalemPoi. v17 (S219) added `narration_subtopics` JSONB to SalemPoi. v18 (S226) added 6 haunt columns (`haunt_sprite_id` + 4 tuning ints + `haunt_enabled`). v19 (S227) added `haunt_duration_s` REAL.
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

1. **NOTE-L014 / OMEN-008 — Privacy Policy** — V1-minimal Posture A shipped S145 at `docs/PRIVACY-POLICY-V1.md`. Full OMEN-008 draft pending OMEN review.
2. **NOTE-L015 — SalemCommercial cutover** — PARKED POST-V1 per operator S145.
3. **Cross-project: SalemIntelligence anomaly relay** — `~/Development/OMEN/reports/locationmapapp/S153-si-anomalies-relay-2026-04-20.md` (ANOM-001, ANOM-002).
4. **NOTE-L019 restrooms_zombie.png regen** (LOW) — no deadline.
5. **9-dot menu witchy backgrounds** — PARKED S146.

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
| Older STATE.md content | `docs/archive/STATE_removed_2026-04-09.md` (and `_removed_2026-04-21.md`) |
