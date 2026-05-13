# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — must stay under 200 lines.

**Last updated:** 2026-05-13 — **Session 256 (V1 health audit Wave 3 shipped — Install-time Asset Pack for salem_tiles.sqlite + first androidTest + Lenovo field-validate green).** New `:app-salem-tiles-pack` Gradle module with `com.android.asset-pack` plugin and `install-time` delivery; `salem_tiles.sqlite` (262 MB) git-moved out of `:app-salem`'s base assets. Zero code changes to `OfflineTileManager.kt` — install-time pack assets merge into the app's `AssetManager` namespace at runtime, so `context.assets.openFd("salem_tiles.sqlite")` keeps working. Confirmed via Lenovo logcat (PID 3527): `Extracting salem_tiles.sqlite (asset=274313216)` → `Archive verified: Salem-Custom=715841`. Build verified across `:app-salem-tiles-pack:assemble`, `:app-salem:assembleDebug`, `:app-salem:bundleRelease`, `bundletool build-apks` (universal + split + device-targeted), and `bundletool install-apks --device-id=HNY0CY0W`. **AAB sizes: base APK 55.2 MB compressed (was 374 MB); asset pack 261.6 MB; total compressed download 125 MB.** Base is well under Play's 200 MB ceiling; pack sits in the 2 GB asset-pack ceiling. **Cliff 1 — CLOSED at build level + field-verified on Lenovo TB305FU.** All three V1 ship-cliffs are now resolved — AAB is upload-eligible pending operator-side legal items. New `app-salem/src/androidTest/.../SalemContentDatabaseMigrationTest.kt` (first androidTest in the repo, 2/2 pass on Lenovo via `:app-salem:connectedDebugAndroidTest`) — asserts asset's `room_master_table.identity_hash` matches v19 hash + minimum row counts on all 12 Room-managed tables. Wave 3 closed in 1 session vs plan estimate of 3-4 sessions. Full detail: `docs/session-logs/session-256-2026-05-12.md`.

**V1 ship-cliff status post-S256:**
- Cliff 1 (AAB 316 MB vs Play Store 200 MB compressed ceiling) — **CLOSED** (S256 Wave 3, install-time Asset Pack)
- Cliff 2 (no `largeHeap` + 60° tilt OOM risk) — **CLOSED** (S255 Wave 1)
- Cliff 3 (`allowBackup=true` paid-offline contradiction) — **CLOSED** (S255 Wave 1)

**Workflow change (S256, applies to all future builds with asset packs):** standalone APKs installed via `adb install` will NOT contain `salem_tiles.sqlite` (install-time pack assets ship only through the AAB → Play / bundletool pipeline). For local field-testing use:
```
bundletool build-apks --bundle=app-salem-release.aab --output=salem.apks --connected-device --device-id=HNY0CY0W
bundletool install-apks --apks=salem.apks --device-id=HNY0CY0W
```
The `adb uninstall && adb install` pattern (per `feedback_adb_install_after_db_rebake.md`) is preserved — the uninstall step still applies; the install step swaps to `bundletool install-apks`. bundletool jar at `~/.local/bin/bundletool.jar` (1.17.2).

**Recent context — for per-session detail see `SESSION-LOG.md`:** S254 was a pure-planning V1 health audit (5 parallel agents, 23-decision ledger, 5-wave plan written at `docs/plans/v1-health-audit-2026-05-12.md`). S253 shipped tilt-mode lateral wedge fill + `forceFlatDraw` default flip + content refresh. S252 shipped TigerBase CONUS basemap pipeline Z3-Z12 (post-V1, lawyer-gated). S251 closed MBTA-missing-on-Lenovo bug + diagnosed `ufw allow 4300/tcp` firewall flap. S250 closed Lenovo SuperAdmin actually-reaching-network bug (UI-surface gates + Android API 28+ CLEARTEXT permit). S249 shipped Lenovo SuperAdmin toolbar toggle + `:core` manifest-leak fix (release APK now bit-clean — zero INTERNET/ACCESS_NETWORK_STATE). S248 shipped web SuperAdmin tab. S247/S246 swept Destructive AI Gurus, LLC across 340 files / About dialog. S245 backfilled the LLC entity into privacy policy + STATE.

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

### S257+ next-session docket (post-S256 — Waves 1+2+3 closed; AAB upload-eligible)

**Recommended S257 opener: Wave 4 (Spanish i18n, ~2-3 sessions) OR content authoring depending on operator priority. Full plan + remaining waves + decision ledger in `docs/plans/v1-health-audit-2026-05-12.md`.**

- **Wave 4 i18n (~2-3 sessions, V1 scope):** externalize 29 hardcoded Toast strings + any survey-found user-facing literals → `app-salem/src/main/res/values/strings.xml`; build `values-es/strings.xml` (Spanish UI); locale switcher in Settings + locale-aware TTS engine selection (`TextToSpeech.setLanguage(Locale.forLanguageTag("es-ES"))` with fallback in `NarrationManager.kt:107`); narration stays English V1; smoke-test on Lenovo with `adb shell setprop persist.sys.locale es-ES`.
- **Wave 5 (no V1 code):** write `docs/plans/post-v1-architecture-decomposition.md` — SalemMainActivity 18,623-LOC monolith ViewModel-extraction plan + Manager/Repository consolidation rules + estimated 3–4 week post-V1 refactor scope.

**Wave 3 leftovers / next steps:**
- **Operator field-walk validation of the asset-pack build on Lenovo** — full Salem walk to confirm tile rendering at z16-z19, tilt 30/45/60°, MBTA SuperAdmin path still exercises cleanly through cache-proxy passthrough. The asset pack was end-to-end verified via OfflineTileManager logs + 2/2 androidTest pass, but a real walk hasn't happened yet on this APK.
- **Optional Wave 3 polish:** swap `OfflineTileManager` to point osmdroid directly at the asset pack's `AssetPackManager.getPackLocation("salem_tiles_pack").assetsPath()` — skips the ~1.8s first-launch copy to externalFilesDir + saves 262 MB external storage. Requires `com.google.android.play:asset-delivery` dependency. Deferred since the current copy-based path works.
- **AAB signing for upload** — `bundleRelease` is currently signing with the debug keystore (bundletool default when run via CLI). Operator-side: confirm `~/.gradle/gradle.properties` keystore properties resolve when signing for actual Play Console upload (SHIP-CHECKLIST.md item).

**Wave 2 leftovers (still open):**

- **`hero/` dir migration audit (deferred from Wave 2 #13).** S239 audit said "385 dead at runtime" but PG has **949 live `salem_pois.image_asset LIKE 'hero/%'` references**. Audit which are actually rendered (shadowed by `heroes/<slug>` priority-0 vs really used by PoiHeroResolver tier logic), migrate active ones to `heroes/<slug>.webp`, then drop `hero/`. ~13.4 MB potential reclaim.
- **Bump cache-proxy on operator dev-box** to load the S255 `/mbta/upstream/*` route — restarted manually mid-S255 (PID 1976458); operator should add a systemd / pm2 unit so it survives reboots.
- **Field-validate Wave 1+2 build on Lenovo through SuperAdmin toggle.** S255 walk-sim ran clean but operator never toggled SuperAdmin so the new `/mbta/upstream/*` passthrough wasn't exercised on-device. Toggle and watch `tail -F /tmp/salem-stream.log` for `MbtaRepo` HTTP logs hitting the cache-proxy.

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

**Closed this session (S256):** S254 Wave 3 — Install-time Asset Pack for `salem_tiles.sqlite` (`:app-salem-tiles-pack` module, settings.gradle include, app-salem assetPacks wiring, verify-bundled-assets.js path bump, ASSETS-MANIFEST.md doc); first androidTest in the repo (`SalemContentDatabaseMigrationTest`, 2/2 pass on Lenovo, closes Wave 3 decision #15); Lenovo field-validate via bundletool install-apks; Cliff 1 (AAB > 200 MB ceiling) — all three V1 ship-cliffs now closed.

**Closed S255:** S254 Wave 1 (largeHeap, allowBackup, gradle heap, 4 × scope.cancel, log gating, manual-publish-chain memory) + Wave 2 (dead-table drop, MBTA key removal + `/mbta/upstream/*` cache-proxy passthrough, lint baseline, `:routing-jvm:test` 10/10 was 4/10, HeroAssetLoader inSampleSize, SHIP-CHECKLIST.md, .github/workflows/ci.yml); S252 hardcoded MBTA key + S251/S252 api-v3 direct-leak; S253 manual-publish-chain memory + field-validate-higher-tilts.

### S245+ docket (older carry-forwards — many still relevant, some may be obsolete)

**Rendering / animation:**
- RollingGrassOverlay vivid-green-patches on Bluestacks (`RollingGrassOverlay.kt:37` — x86 Skia stroke difference).
- Sprite path field-walk near bridge POI (haunt skeleton).
- Field-walk validation of S238 perspective fix at z19/z20 + tilt 30/36/42/48° in motion.
- First-frame layout race in `applyMapExtension` (one-frame snap on tilt entry; fix: `mv.requestLayout()` post-extension).
- Residual cold-start tile-decode spike (one Choreographer 1134ms on first tilt entry; canvas-level breakdown if drag chunky).
- Optional perf candidates (only if needed): overlay culling to perspective trapezoid; batched custom Overlay replacing 600 Markers.

**Content / authoring:**
- Lint review of 51-POI newly-renderable set (`default_visible=true AND NOT is_narrated AND NOT is_tour_poi AND NOT is_civic_poi AND NOT is_historical_property`).
- Sweet Boba regression check (still render + narrate template-synthesized text).
- Layers menu category gap — FOOD_DRINK / SHOPPING toggles don't gate narration overlay (only `histLandmark` + `civic` prefs read in `loadNarrationPointMarkers`).
- Operator-mentioned tilt-mode odds and ends (deferred S239).
- Author splash variants in Splash Tree editor (32 TODO stubs in `docs/SPLASH-ANNOUNCEMENTS-V1.md`).
- POI/path-alignment review of the 1962-photo Salem-trip session (`/mnt/sdb-images/LMASalemPictures/session-20260507-160007-1962photos/`).
- Find menu tile review (16 visible cells + Favorites; `FIND_HIDE_IN_V1` excludes FUEL_CHARGING/TRANSIT/PARKING/EMERGENCY); subcategory grid drill-down (`showFindSubtypeGrid` at `SalemMainActivityFind.kt:606`); hero picture review (`res/drawable-nodpi/find_tile_<id>.jpg` + `PoiHeroResolver`); POI detail click-through review (`showPoiDetailDialog` + `PoiDetailSheet` post-S224-cleanup coherence).

**Tools / process:**
- TTS-settings deep-link reliability (`Settings.ACTION_ACCESSIBILITY_SETTINGS` fallback when `com.android.settings.TTS_SETTINGS` missing).
- SheetJS prototype-pollution audit (`xlsx@0.18.5` known CVEs; admin is Basic-Auth + localhost-only so acceptable).
- Cascading subcategory pick-list (Excel `INDIRECT` if operator finds the flat 170-subcat list annoying).
- Field-edit field-validate UPDATE/CREATE loops; Rapid Recon fresh-walk 1 Hz tick + true-north validation; Auto-fire camera field-test (300 ms `AUTO_FIRE_SETTLE_MS`).

### Other carry-forwards (lower priority)

- **Operator field-validation** of S221 detour, S220 subtopic content, S217 BusinessLabel + FAB override; **S220 lint cleanup** for spurious `is_tour_poi=true` rows; **borderline-historical commercial opt-in audit** (Hawthorne Hotel etc.).
- **Tier 3 disk reclaim** (~4.4 GB): `tools/poi-icons/`, `tools/hero-triptych/output-full/`, `tools/tile-bake/data/sources/`, `cache-proxy/out/l3-essex/`, `docs/archive/poi-icons-unused-styles_*/`. **`.gitignore` audit**: `overnight-runs/`, `cache-proxy/poi-cache.json`, `cache-proxy/cache-data.json`, `web/dist`, `web/tsconfig.tsbuildinfo`, `docs/bake-tests/`.
- **John Ward House tour-leg fix**, **HISTORICAL_LANDMARKS icon+hero regen**, **`description` content rebuild post-`historical_note`-merge**, **Dequeue 40 m staleness drop align with `geofence_radius_m`** (all S216).
- **S215 suspicious-leg graph investigation** — Dr. K 13/14, WD1 3/6/10/11.
- **PoiDetailSheet/Find further cleanup** (S224): `enterFilterAndMapMode`/`exitFilterAndMapMode` clearXXX/stopXXX no-ops still in source. **CLAUDE.md "Pinned" block cleanup** — references Room v11 / S186-S188 state; actual schema is v17.

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

**Sessions completed:** 256. **Internal ship target: 2026-08-01** (operator-confirmed S201). Salem 400+ peak attendance October 2026 (~1M visitors).

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
