# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — must stay under 200 lines.

**Last updated:** 2026-05-11 — **Session 247 (About-dialog product-name fix: "WickedSalemApp" → "Katrina's Mystic Visitors Guide" — operator field-validated; S246 LLC About-dialog field-validation PASS, closing S246 carry #1).** Tight micro-session. Operator opened the About dialog on Lenovo HNY0CY0W and confirmed S246's LLC propagation rendered correctly (copyright line `Destructive AI Gurus, LLC`, email `contact@destructiveaigurus.com`) — closing S246 carry #1. Then caught a separate issue in the same dialog: title and version-line strings still said "WickedSalemApp" (internal Kotlin class name), not the user-facing product. Two-line edit to `SalemMainActivityHelpers.kt`: line 21 body `WickedSalemApp v1.5.55` → `Katrina's Mystic Visitors Guide v1.5.55`, line 34 title `About WickedSalemApp` → `About Katrina's Mystic Visitors Guide`. Now matches `strings.xml` `app_name = "Katrina's Mystic Visitors Guide - Salem"`, AndroidManifest `android:label="@string/app_name"`, and the splash screen. Explore agent did a full-repo branding sweep — verified clean across the rest of the app + `web/src/` admin UI. All other `WickedSalemApp` hits are internal (class declarations, file-header comments, MODULE_ID diagnostic constants, log tags) and were intentionally out of scope. `:app-salem:assembleDebug` SUCCESSFUL in 7 s; `adb uninstall && adb install` to Lenovo HNY0CY0W; operator field-validated PASS. **S246 (entity propagation: Destructive AI Gurus, LLC swept across 340 source files / docs / config — APK rebuilt at 357 MB and installed on Lenovo HNY0CY0W) state below still applies.** Single commit (`9ac8342`). Followed the S245 entity backfill (privacy policy + STATE + gitignore) with the wider propagation pass called out in the S245 carry. Plan-mode approved 5-pass batch: copyright headers across 614+ source files (`.kt` / `.xml` / `.ts` / `.tsx` / `.js` / `.sh` / `.sql` / `.py`); Kotlin + JS MODULE_ID diagnostic constants; **in-app About dialog at `SalemMainActivityHelpers.kt`** (user-facing — now says `Copyright (c) 2026 Destructive AI Gurus, LLC` and email canonicalized to `contact@destructiveaigurus.com`); `IP.md` Author/Copyright split + Form TX line now explicitly requires the LLC as claimant; `COMMERCIALIZATION.md` operator-side legal table refreshed (entity DONE with filing facts; Play Console registration target = LLC); `docs/lawyer-packet/10-legal-walkthrough.md` S246 supersession block at section 3; `docs/PRIVACY-POLICY.md` (V2 future-state) `[OPERATING ENTITY TBD]` placeholder backfilled; `web/package.json` + `cache-proxy/package.json` got `"author": "Destructive AI Gurus, LLC"` + `"license": "UNLICENSED"`. Intentionally untouched: `docs/archive/`, `docs/session-logs/`, `SESSION-LOG.md`, the 10-file counsel packet (operator-confirmed frozen), `tools/` vendored sources, `applicationId`, `namespace`, git author identity. Build clean in 47 s; debug APK 357 MB. **S245 (authoring-lock UX revision + entity backfill) state below still applies.** Two commits (`79b2291`, `2e54b88`); 5 files changed; long-pending operator-side legal carry-forward (4 privacy-policy placeholders) cleared by the Certificate of Organization filed at MA SOC 2026-05-11 11:21 AM (Filing # 202626416630). The `no_overwrite` lock now blocks AI/automation only — admin paths (PoiEditDialog + MassEditTab) hardcode `?force=true`, the S244 confirm-prompt-on-409 flow is gone. Lock panel reads at a glance: red border / bg / switch when on; clear white panel with a visible OFF affordance when off. Two new memories: `feedback_no_overwrite_targets_automation.md`, `project_operating_entity_dag_llc.md`. **S244 (DWELL fix + `no_overwrite` initial ship) state below still applies.** Operator pivoted off the S244-carry docket headline (DWELL fix) into a new authoring-protection feature midway. **DWELL fix (commit `9dd0d85`, 3 files):** new public `NarrationManager.isTtsReady` accessor (existing private `ttsReady` field instrumented at every site by S243 Phase 2) + `TourViewModel.isTtsReady()` pass-through + DWELL entry guard at `SalemMainActivity.kt:2166` so walk-sim emits a W-level skip and falls through to the next step instead of pinning 180s when no TTS engine is available (Bluestacks; any TTS init failure). Existing dwell-loop body unchanged; 180s safety cap retained as backstop. Compiled clean, installed on Lenovo HNY0CY0W. **`no_overwrite` authoring lock (commit `42e3675`, 6 files):** idempotent `ALTER TABLE salem_pois ADD COLUMN IF NOT EXISTS no_overwrite BOOLEAN NOT NULL DEFAULT FALSE` at cache-proxy startup; hard-refuse with HTTP 409 + `code: NO_OVERWRITE_LOCKED` + `locked_fields: [...]` on `PUT /admin/salem/pois/:id` AND `POST /admin/salem/pois/apply-mass-edit` when any of 9 protected fields would change on a flagged row (short_narration, long_narration, historical_narration, narration_subtopics, description, short_description, custom_description, origin_story, year_established). `?force=true` bypasses; toggling the flag itself is always free; mass-edit pre-scans the batch before opening the transaction so a single locked row blocks the whole apply (unless force). Frontend: amber "Authoring Lock" panel pinned to the top of the Flags tab in `PoiEditDialog` + lock indicator on flagged rows in `PoiTree` + save-flow 409 catch with confirm-and-retry-with-`?force=true`. New module exports: `NO_OVERWRITE_PROTECTED_FIELDS` from admin-pois.js. End-to-end verified via curl: flag on → narration change refused 409 with locked-field name → same change with `?force=true` succeeded → reverted to clean state. **Bonus diagnostic:** "Lye-Tapley Shoe Shop" appearing under "POI Hist. Landmark" in the admin tree is S190 synthetic split on `data_source LIKE '%massgis%'` — not a bad category; operator flipped `data_source = 'KHalecki'` to move it into "Historic Buildings". **Re-baked bundled content** (2,035 live POIs, 73 tour_legs, identity_hash `745afa3eb4ce04bd7873671ea297b6e0`) + 357 MB debug APK + Lenovo HNY0CY0W uninstall+install — carrying both fixes. **Carry to S245 documented below.** **Previous (S243/S242/S241/…) context still applies below.** Operator pivoted off the S243 docket to Bluestacks deployment, hit a single-bug trio (walk-sim hung + no TTS + tours broken — Bluestacks lacks Google TTS, NarrationMgr defers correctly but currentNarration still sets, walk-sim DWELL gates on `currentNarration != null` and deadlocks 180s) plus a "strange green polygons" symptom traced to `RollingGrassOverlay.kt:37`'s vivid green blade paint (likely x86-vs-ARM Skia stroke difference on Bluestacks). Direct quote: *"I am tired of walking on land mines… until we are ready to make this production, I want everything available to us to debug."* **Revived dormant `TcpLogStreamer`** (`app-salem/.../util/TcpLogStreamer.kt`: HOST=10.0.0.194, PORT=4301, BuildConfig.DEBUG-gated, wired into `WickedSalemApp.onCreate`) + built `/tmp/salem-collector.py` (multi-client Python TCP collector tagging source IPs; `/tmp/salem-stream.log`) so Lenovo HNY0CY0W and Bluestacks (10.0.0.204) stream concurrently. **Five parallel Explore agents** audited rendering / narration+TTS+walk-sim / data+Room+cache / GPS+sensors / UI+lifecycle and surfaced 50+ silent-state file:line citations; synthesized into 4-phase plan at `~/.claude/plans/validated-squishing-hinton.md`, plan-mode-approved. **Phase 1 (commit `867c0db`, 16 files):** TCP streamer revive + `MapDebugDumper.kt` on-demand snapshot + 10s auto-dump scheduler + `dumpNow(reason)` for walk-sim start/stop events + 1Hz frame summary in WickedAnimationOverlay + per-frame cull counts (duty/intensity/viewport) in AnimatedWaterOverlay / RollingGrassOverlay / FireflyOverlay + SpriteOverlay bitmap-bytes logging (catches S236 pressure) + TileArchive 5s cache-summary (hits/decoded/null/missing) + PolygonLibrary malformed-ring + unknown-geometry counters + TiltContainer applyMapExtension pre/post mv.height (catches S238 layout race). **Phase 2 (commit `b25ca3f`, 2 files):** NarrationManager ttsReady mutations logged at every site (onInit success/failure/shutdown — silent shutdown was a real hole), pause/resume guard refusals, enqueue pass-through path; NarrationGeofenceManager isHistoricalQualified 8-branch decision tree (per-(POI,allow) 30s throttle — naive logging would flood 2000-POI loops), cooldown skip reasons with remaining ms, findNearestUnnarrated reach-out accounting (considered/alreadyNarrated/gateRejected/outOfRadius). **Phase 3 (commit `5385404`, 6 files):** Room callback on SalemContentDatabase — `onOpen` logs schema_v + table list; `onDestructiveMigration` fires E-level alarm (S180-style silent fallback was real); PoiHeroResolver per-tier (tier0 triptych / tier1 imageAsset / tier2 category-folder / RedPlaceholder); PoiCache expanded build summary + per-category breakdown; GPS-OBS fix lines now tagged `(walk-sim)`/`(manual)`; MotionTracker 2-min "armed but never fired" diagnostic (Lenovo TB305FU broken-sensor pattern); DeviceOrientationTracker sustained-static-mode warning at 60s threshold (Bluestacks compass-static at 300.7°). **Phase 4 (commit `f0c7711`, 3 files):** SalemMainActivity.onCreate single startup-posture echo (BuildConfig.DEBUG / RECON_DEFAULTS / V1_OFFLINE_ONLY + 7 BuildDefaults flags + DEFAULT_ZOOM + TILT_3D_ENABLED — catches S203 mismatches with one grep); onConfigurationChanged pending-restore echo; dbImportLauncher / csvImportLauncher result logging (including cancelled returns); MainViewModel V1_OFFLINE_ONLY early returns now log "BLOCKED by V1_OFFLINE_ONLY" with call args; new `core/util/VerboseTrace.kt` helper for grep-friendly gate-decision logs. **Bluestacks aids hoisted out of V1_OFFLINE_ONLY strip** (visible in BuildConfig.DEBUG): "Open TTS Settings (Debug)" (`Intent("com.android.settings.TTS_SETTINGS")` → ACCESSIBILITY_SETTINGS fallback) and "Dump Map Overlays (Debug)". **Release-clean verified:** release dex carries 5 pre-existing un-gated tag strings; debug dex carries 66 — R8 stripped ~92% of new additions. **Memory writes:** new `feedback_verbose_debug_until_production.md` (operator's "until V1 ships" stance, BuildConfig.DEBUG gate pattern, throttle-high-frequency rule, R8-strip-verified) + `MEMORY.md` index update. **Aggregate: 27 files / +822 −52 across 4 commits.** Lenovo HNY0CY0W carries post-Phase-4 build. **Carry to S244 documented below.** **Previous (S242/S241/S240/…) context still applies below.**

**Recent context — for per-session detail see `SESSION-LOG.md`:** S242 deleted `:app` + `:salem-content` (~26K LOC), archived 32 stale scripts, retargeted publish chain to bundle asset directly, dropped Room schema v10–v18 JSONs, shipped 6 Tier 2 Android fixes + `/overpass` ENABLE_OVERPASS gate; Lenovo carrying post-S242 374 MB build. S241 shipped Mass-Edit POI workflow (.xlsx/.ods round-trip, per-cell approve/reject, soft-delete via spreadsheet, in-cell pick-lists for 24 enum-bound columns; operator's first field-use dropped POI count 2070 → 2042). S240 decoupled map marker rendering from narration — `default_visible` is now an honest controller (new `renderable` snapshot in `PoiCache` parallel to `narrated`; 51 POIs newly map-renderable including Bit Bar). S239 fixed three tilt-mode rendering bugs (St. Peter's flat duplicate, upright drift, magnify FAB icon scaling) all traced to HW display-list staleness. S238 shipped Phase 3 wedge fill (`topY` 1.0→0.6) + cull loosen + diagnostic strip. S237 shipped two-pass tilt rendering.

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

### S245+ next-session docket

- **RollingGrassOverlay green-polygon fix (S243 surfaced; S244 untouched).** Vivid green (60, 210, 80) grass-blade paint at `RollingGrassOverlay.kt:37` renders as "large patches" on Bluestacks — likely x86 Skia stroke-rendering difference vs ARM. Field-walk Bluestacks with the new `RollingGrass` 1Hz frame summary + `WaterAnchors`/`Firefly`/`WickedAnimSummary` to confirm what's actually drawing before choosing fix (stroke width tune vs feature-flag overlays off on emulators vs path simplification).
- **TTS-settings deep link reliability** — operator hit "settings UI stripped in Bluestacks." Verify `Settings.ACTION_ACCESSIBILITY_SETTINGS` fallback works when `com.android.settings.TTS_SETTINGS` doesn't, and that English voice data installs correctly from there.
- **Field-validate S243 verbose stream on Lenovo** — confirm `Posture` startup line, `MapDump auto` 10s scheduler, `WickedAnimSummary` 1Hz heartbeat, `GPS-OBS (walk-sim)` tagging, `NARR-GATE` decision logging all flow correctly to the TCP collector during a real walk.
- **`:routing-jvm:test` parity decision (S242 surfaced, untouched in S243).** 4 of 10 tests fail on the current bundle at `app-salem/src/main/assets/routing/salem-routing-graph.sqlite`. Pre-existing on HEAD~1; bundle re-baked since S178 captured the parity fixtures. Two options: re-bake fixtures from current bundle (fast, matches "bundle is canonical" framing) or investigate drift root cause (slower, could surface a real regression).
- **Field-validate Phase 5 fixes on Lenovo HNY0CY0W during operator's next Salem walk** (S242 ship, S243 carry): GPS-OBS heartbeat clean shutdown in onDestroy, tilt-mode first-frame layout fix (`mv.requestLayout()` post-extension), marker/sprite cull behaviour at tilt entry/exit (constants extracted), magnify FAB scaling under tilt (`onMapStateChanged` consolidation). Confirm GPX/record-GPS menu items are gone from the toolbar utility menu.
- **Operator on-device validation of the S241 mass-edit batch.** Launch the new APK on Lenovo, spot-check that the 28-row delta (recategorizations + soft-deletes) reflects correctly in Find / Layers / marker rendering.
- **SheetJS prototype-pollution audit (S241 carry).** `xlsx@0.18.5` from npm registry has known CVEs; acceptable here because admin surface is Basic-Auth + localhost-only. Optional swap to cdn.sheetjs.com tarball or maintained fork.
- **Optional polish — cascading subcategory pick-list.** Currently all 170 subcategories appear regardless of parent category; operator picks the right pairing manually. Could add Excel/LibreOffice `INDIRECT` cascading later if it gets annoying.
- **Lint review of the 51-POI newly-renderable set (S240 carry).** `default_visible=true AND NOT is_narrated AND NOT is_tour_poi AND NOT is_civic_poi AND NOT is_historical_property`. Some may be dedup leftovers or coord outliers; flip per-POI `default_visible=false` where appropriate. The mass-edit tab now makes this batch-edit fast.
- **Sweet Boba regression check (S240 carry).** Should still render AND still narrate template-synthesized text on geofence entry.
- **Layers menu category gap (S240 deferred follow-up).** FOOD_DRINK / SHOPPING / etc. toggles don't currently gate the narration overlay (only `histLandmark` + `civic` prefs are read in `loadNarrationPointMarkers`). Wire so the per-category Layers checkboxes actually filter the map.
- **Operator-mentioned tilt-mode odds and ends** (deferred at S239 session close — operator: "some other odds and ends but we will work on that next session").
- **Hero `hero/<uuid>` cleanup.** ~12 MB AAB pre-ship reclaim. 385 of 386 files are dead at runtime (94 shadowed by `heroes/<slug>` priority-0, 291 orphan). The single live reference (`hb_3530de8ed522` → `hero/b2542725-...webp`) can be migrated to `heroes/<slug>.webp` to retire the entire `hero/` dir + drop the `image_asset` column in a Room bump. See S239 live log "Curiosity detour: hero asset compression audit" for the reconciliation table.
- **Field-walk validation of S238 perspective fix in motion.** Static screenshot at NPS Visitor Center confirmed full screen fill at z19/z20 + tilt=48°. Real walk through Salem at multiple tilt steps (30/36/42/48°) is still owed — the "fills the screen" claim needs to hold up while POI markers move, headings rotate, and tiles re-fetch. (S239 didn't field-walk; just static-tested option-2.)
- **First-frame layout race in `applyMapExtension`.** S238 diagnostic surfaced `mv.size=684` (unextended) on the first dispatchDraw immediately after entering tilt; layout self-corrects on the next frame. Cosmetically a one-frame "tiles snap into place" beat at tilt entry. Fix: `mv.requestLayout()` after `setLayoutParams` in `applyMapExtension`.
- **Sprite path field-walk** — Phase 3 cull mirror in `SpriteOverlay.drawBillboarded` shipped but no haunt POIs encountered during S238's adb-driven walk. Validate at the bridge POI when the operator passes one.
- **Clean-reproduction test of +10mi-with-buildings asset** (S236 carry). Rebuild with only the 848 MB live `salem_tiles.sqlite` in `app-salem/src/main/assets/` (no .bak alongside — the polluted APK from S236 contained both, undermining the bitmap-pressure diagnosis). Install to Lenovo, launch, observe. If crashes: bitmap pressure confirmed → drop `BuildDefaults.TILT_3D_ENABLED` to `BuildConfig.DEBUG` for retail, OR shrink bake (q=40 / palette / z14-z15 building drop), OR halve bitmap memory via `Bitmap.Config.RGB_565` for tile decoder. If launches clean: the rollback was unnecessary; ship the +10mi-with-buildings bundle.
- **Residual cold-start tile-decode spike** — one Choreographer "Slow dispatch took 1134ms" on first tilt entry remains. Steady-state frames mostly 7ms but occasional 30-43ms spikes at z19+tilt+603 overlays. POI work confirmed eliminated; remaining cost looks like tile decode + GC. Worth canvas-level breakdown if drag chunkiness is still felt in field testing.
- **Field walk on the 374 MB APK** — does the q=60 visual quality hold up at z17/z18 in motion? Disk + RAM behavior on the Lenovo? Drag feel at z19 + tilt + walking-sim?
- **AAB / Play Store ship implications** of the 374 MB APK / 261.7 MB asset — verify Asset Pack / dynamic-delivery story before retail submission. Operator was clear "maps are cheap" but ship has its own constraints.
- **Possible perf follow-ups (only if needed):** overlay culling to perspective trapezoid; batched custom Overlay (replace 600 Markers with one draw call). Both candidates if drag still chunky after field validation. Operator's principle: POIs are the lever, basemap pipeline off-limits.
- **Author splash variants** in the new Splash Tree editor (Tooling → Splash Tree). 32 TODO stubs in `docs/SPLASH-ANNOUNCEMENTS-V1.md`. Save → next `:app-salem:assembleDebug` ships.
- **Field-edit field validation** — UPDATE loop (multi-POI move + category + photo attach → pull → Apply/Reject) + CREATE loop (long-press empty → name → pull → pick category → Create). Optional: delete / split / duplicate edit kinds; dedicated drawables for the field-edit + GPS-burst toolbar icons.
- **Rapid Recon fresh-walk validation** of the 1 Hz tick + true-north heading.
- **POI/path-alignment review** of the 1962-photo Salem-trip session at `/mnt/sdb-images/LMASalemPictures/session-20260507-160007-1962photos/` using the new cluster modal (and the 485+278-photo earlier sessions).
- **Auto-fire camera field-test** — confirm 300 ms `AUTO_FIRE_SETTLE_MS` feels right.
- **S227 Haunt** upright-skeleton field-walk near the bridge POI; optional polish (audio component, per-character motion profiles, tour-only gating).

**S224 Find-menu refinement docket** still queued. Now also relevant whenever the operator decides to give the new categories created in S225+ their own Find-menu tiles.

- **Find category grid review.** 16 visible tiles (4 cols × 4 rows + Favorites cell). `FIND_HIDE_IN_V1` currently excludes `FUEL_CHARGING`, `TRANSIT`, `PARKING`, `EMERGENCY` — revisit. `FIND_SALEM_LABEL` map (e.g. "Taverns & Cafés", "Apothecaries & Clinics") and `FIND_TILE_ASSET` map (`find_tile_<id>.jpg` in `res/drawable-nodpi/`) define the labels + artwork — walk tile-by-tile.
- **Subcategory grid review.** `showFindSubtypeGrid` at `SalemMainActivityFind.kt:606` — drills from each top-level category into `PoiCategories.ALL` subcategories. Verify naming, ordering, and which subcategories surface for each parent.
- **Hero picture review.** Per-tile illustration assets in `res/drawable-nodpi/find_tile_<id>.jpg` plus per-POI hero artwork resolved via `PoiHeroResolver`. Operator wants to review which hero shows for which category/subcategory and whether HISTORICAL_LANDMARKS still aliases to the `historic_house` set (S216 carry).
- **POI detail click-through review.** Two parallel surfaces: `showPoiDetailDialog` (Find dialog flow) and `PoiDetailSheet` (map flow). Walk through what each shows, in what order, and whether the post-S224-cleanup layout (no Reviews, no Comments, no in-app website) is coherent.

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

**Sessions completed:** 247. **Internal ship target: 2026-08-01** (operator-confirmed S201). Salem 400+ peak attendance October 2026 (~1M visitors).

---

## POI Inventory (refreshed S180 from live PG)

- **PG `salem_pois`:** 2,042 live + 87 soft-deleted (S241 mass-edit dropped live count by 28 — dupe cleanup + recategorizations + soft-deletes via the new spreadsheet round-trip; S216 dedup history: 220 dead hard-deleted + 43 live cluster losers hard-deleted + 1 Gardner-Pingree dup, all merged before delete; further trim through S221+). 484 reclassified `HISTORICAL_BUILDINGS` → `HISTORICAL_LANDMARKS` (curated buildings now 105). `historical_note` column dropped S216; content lifted to `description` (143 POIs touched, 0 gaps).
- **Room DB:** at `app-salem/src/main/assets/salem_content.db`, **v19 schema** (S227), identity_hash `745afa3eb4ce04bd7873671ea297b6e0`. Witch Trials intact (npc_bios 49 / articles 16 / newspapers 202). 4 tours / 73 legs (S224). v11 (S186) `is_civic_poi`. v12 (S192) `historical_narration`. v13 (S193) `is_historical_tour`. v14 (S195) `is_historical_property`. v15 (S198) dropped `body_points` from WitchTrialsNewspaper. v16 (S216) dropped `historical_note` from SalemPoi. v17 (S219) added `narration_subtopics` JSONB to SalemPoi. v18 (S226) added 6 haunt columns (`haunt_sprite_id` + 4 tuning ints + `haunt_enabled`). v19 (S227) added `haunt_duration_s` REAL.
- **APK:** **374 MB debug** (S234 — bumped 161 → 374 MB by the +10mi q=60 tile expansion to 261.7 MB embedded `salem_tiles.sqlite`). `poi-icons/` at 544 MB is the pre-Play-Store audit target. AAB Asset Pack / dynamic-delivery story for the bigger bundle owed to S235+.
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
