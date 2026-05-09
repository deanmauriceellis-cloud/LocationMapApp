# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — must stay under 200 lines.

**Last updated:** 2026-05-09 — **Session 235 (crash-recovery + vector tile pipeline +10mi (buildings + OMT) on disk; raster re-bake deferred to S236).** Pre-crash work landed cleanly on disk: `salem-buildings-plus10mi.geojsonl` (128 MB / 360,662 features), `buildings.mbtiles` (36.8 MB, 6,482 vector tiles z14-16, tippecanoe), `salem-vector.mbtiles` (75 MB, 11,525 OMT tiles z0-16, Planetiler) — the vector input layer that S236's raster re-bake will rasterize into a fresh +10mi `salem-custom.mbtiles` with building footprints baked in for the matrix-tilt 3D wedges. Parallel raster bake crashed mid-run at ~28% of z15 (z14 fully done at 575/575); `salem-custom.mbtiles` is still S234's 266 MB at 00:27. `worker-output/` discarded; new bake artifacts gitignored. Folded long-untracked operator content into the repo: `DrK/` (4 narration drafts), `qcis/` (QGIS project), four S192 review .odts. Operator chose to defer the ~9-min bake rerun to S236. **Carry-forward to S236:** `cd tools/tile-bake && node bake-parallel.js` against the new vector sources; merge into `app-salem/src/main/assets/salem_tiles.sqlite`; rebuild `:app-salem:assembleDebug`; field-walk the 374 MB APK. **Previous (S234) context still applies below.**

— **Session 234 (tile bake +10mi everywhere q=60 via parallel infra + PoiCache singleton + heading-up fast path + V1 call-site gates).** Five distinct shippings. **Tile coverage:** full +10 mi ring at every zoom (z14-z19) at WebP q=60. 715,841 tiles baked in 9 min via new 8-worker parallel infrastructure (`tools/tile-bake/render-worker.js` + `bake-parallel.js`, hash-split `(bx*100003+by)%NUM_WORKERS`). Bundle 58.9 → 261.7 MB; debug APK 161 → 374 MB. **PoiCache** Hilt @Singleton — entire 2,076-row catalog loaded ONCE at startup (572 ms background) into immutable snapshot with 8 pre-built indexes. Refactored: SalemContentRepository (16 POI read methods), FindViewModel, WitchTrialsRepository, SalemMainActivity.loadHauntConfigsAsync — all route through cache. **Zero Room queries during runtime** after the one-shot load. **Narration marker diff cache + Mutex** — filter toggles (Show All POIs, Layers, tour-mode) become near-instant no-ops; mutex serializes concurrent callers (fixed a startup race that double-attached 1,130 markers). **Heading-up fast path** — `lastProcessedMoveBearing` early-return at the top of `applyHeadingUpRotation` eliminates ~100 Hz sensor allocation churn + log spam. **V1 call-site gates** on `onScroll`/`onZoom` listeners so scheduleAircraft/BusStop/Webcam/GeofenceReload aren't called even to hit their internal early-returns. Also: MarkerIconHelper LRU 2000 → 4096; comprehensive TiltContainer timing instrumentation (burst + sampled-steady + per-MOVE + overlay count) + debug overlay-suppression long-press toggle on 3D FAB. Three new feedback memories (`feedback_diagnose_before_changing.md`, `feedback_map_easy_pois_are_the_lever.md`, `feedback_v1_gate_at_call_site.md`) — operator caught a load-bearing edit based on assumed device state, then redirected scope from basemap pipeline to POI lifecycle, then required V1 gating at call sites. Operator verdict: "much better, good but needs more work." **Carry-forward to S235:** residual cold-start tile-decode spike (one Choreographer "Slow dispatch took 1134ms"); occasional 30-43ms steady-state frame spikes at z19+tilt+600 overlays; instance-level `narrationPointsCache` field redundant with PoiCache (safe to delete); field walk on the 374 MB APK; AAB / Asset-Pack ship story for the bigger bundle.

— **Session 233 (matrix-tilt 3D promoted to V1: wedge fill + dedicated 3D FAB + perspective touch fix).** S232's debug-only prototype hardened and shipped. Symmetric upward MapView extension (`height += 5.0 × containerH`, `topMargin = -extra/2`, `clipChildren=false`) fills the upper-corner wedges with real distant Salem tiles through the SAME single perspective transform — no seams, no `setMapCenterOffset` (that broke osmdroid's tile-fetch viewport, leaving overlays rendering through dark gaps). Touch events manually perspective-transformed via `Matrix.mapPoints` + multi-pointer `MotionEvent.obtain(...)` — Android's `MotionEvent.transform(Matrix)` mangles perspective matrices (silently drops/mis-applies the bottom row → inverted y direction). FAB x1..x5 magnify pivots correctly on the operator marker because the symmetric layout puts MapView's geometric center at TiltContainer center = operator's GPS-rendered position. New "3D" FAB above GPS (`btnTilt3d` in `activity_main.xml`) replaces S232's long-press on the "+" zoom button; cycles **0° → 30° → 36° → 42° → 48° → 0°** (operator-tuned ladder, persisted via `MenuPrefs.PREF_TILT_3D_DEG`). `BuildDefaults.TILT_3D_ENABLED` flipped from `BuildConfig.DEBUG` to `true` — ships in release/AAB. Verbose logging added across `TiltContainer` + zoom buttons + FAB + slider for the next reproducer of an ANR operator hit during FAB x5 cycling at high zoom. **Carry-forward to S234**: tile-bake bbox expansion 10 miles outside Salem's bbox for ALL zoom layers — residual darkness at higher tilt + zoom is a real bundled-coverage gap, not a tilt-engine bug. **Previous (S232) context still applies below.**

— **Session 231 (Splash decision tree end-to-end + Rapid Recon time-driven + sidebar reorg + auto-fire camera).** Ten distinct shippings in one long session. **Splash decision tree, full stack:** TigerLine 2025 polygon asset baked at `app-salem/src/main/assets/us_places_v1.sqlite` (102 top-100 cities + 102 thirty-mile buffer rings + 11 Salem-adjacent towns + 3,235 US counties = 3,450 polygons / 2.09 MB, gzipped WKB blobs); cache-proxy schema + GET/PUT/POST `/admin/splash/*` endpoints with PostGIS `ST_Contains` for live preview accuracy; web-admin three-pane editor (`SplashTreeTab.tsx`) under new Tooling sidebar group; bake script + Gradle `publishSplashTree` task chained into `publishSalemContent`; runtime Kotlin in new `splash/` package — `LocationContext` + `PlaceResolver` (own WKB ring-tracing PIP since Android stock SQLite ships without rtree) + `SplashEngine` + `SplashActivity` hook; authoring doc `docs/SPLASH-ANNOUNCEMENTS-V1.md` with 32 numbered TODO stubs. Verified live on Lenovo HNY0CY0W: home GPS in Beverly resolves to "You're close to discovering Salem — about 2.7 miles away in Beverly, MA." **Web admin sidebar reorg:** 8-button header strip → `AdminSidebar.tsx` left rail with Content / Quality / Tooling groups, localStorage-persisted collapse + per-group state. **Rapid Recon (GpsBurstCameraManager):** time-driven 1 Hz tick instead of GPS-fix-driven (flaky/coalesced GPS no longer starves the camera); true-north `GPSImgDirection` via `GeomagneticField`; null-safe filename + EXIF; rebrand. **Photos overlay** (`BurstPhotosOverlay.tsx`): cluster pins; thumbnail grid modal; per-card green-✓ Save / red-🗑 Delete; soft-delete to `<session>/.deleted/`; sharp thumbnail endpoint (disk-cached); map non-reactive while modal open via `createPortal` + `useFreezeMapWhile`. **Salem trip recovery:** pulled 1962 photos / 5.1 GB / 33.3 min; cadence histogram confirms 1 s tick. **Auto-fire toolbar recon camera:** `EXTRA_AUTO_FIRE` skips preview + shutter, single-tap shutter via 300 ms autofocus settle. **FAB zoom-step regression:** `BuildDefaults.FAB_ZOOM_STEP` 2.0 → 1.0 in both build types. **Vite proxy:** `localhost:4300` (LAN-IP-shift-proof). **Owed for next session:** operator authors splash announcement variants (32 stubs in the new editor); field validation of the S229 + S230 field-edit update + create loops; GPS-burst round-2 field walk; alignment review of the 1962-photo session via the new cluster modal; auto-fire camera field-test; S227 upright-skeleton bridge field-walk. **Room still at v19 identity `745afa3eb4ce04bd7873671ea297b6e0`, no schema bump this session.** **Operator-stated hard pause from S214 still in effect: Salem Oracle (:8088) + SalemIntelligence (:8089) undergoing major reformulation to attach full per-claim provenance.** **Cross-repo Salem commit owed (still).** Per-session detail in `docs/session-logs/session-{220..231}-*.md` and `SESSION-LOG.md`.

---

## TOP PRIORITY — V1 launch triage (operator-confirmed 2026-04-29; revised S203 close 2026-04-30)

> **Internal ship target: 2026-08-01** (89 days). One month tighter than the prior Sept 1 anchor in CLAUDE.md / strategic docs. Salem 400+ peak attendance is October 2026.

### Operator-side legal / business (in flight)

- **Form TX copyright** — counsel handling. Hard deadline **2026-05-20** (16 days). Confirm with lawyer this week that filing happens.
- **2026-04-20 lawyer meeting** — happened, outcome captured by operator. **Operator owes me four privacy-policy fields** (operating entity name+type, jurisdiction, contact email, mailing address) so I can patch `docs/PRIVACY-POLICY-V1.md`.
- **Play Console developer account** — operator starts this week. Multi-week ID-verification clock starts immediately.
- **Upload keystore second-medium backup** — covered (operator confirmed multiple secure methods).
- **TTS quality monitoring** — operator does end-to-end Lenovo listen-tests routinely as part of QC.

### Field test (carry-forward to next session)

- Operator drove ~50 min with wife on 2026-04-30 evening on the S206 first-build APK (pre-fixes). Logs reconstructed the missing-POI experience as queue-starvation + tour-mode default-OFF + `is_tour_poi` half-honored. Lenovo now carries the post-fix S206 APK; next drive should validate (a) Lappin Park / Curtis Park / Carlton School render in tour mode regardless of layer toggles, (b) starting a tour with explore-checked layer boxes no longer silently silences markers + narration. Operator's wife's next Salem walk also still owes #25 / #29 Fix 1 / #3 full POI-narration loop validation from S204.

### Hard gate (S214 close, 2026-05-01)

**Salem Oracle (:8088) and SalemIntelligence (:8089) are paused for major provenance reformulation.** Until the operator signals completion, do NOT propose: new admin-tool integrations against `oracleClient.ts` / port 8088, new consumers of `/api/intel/*` at :8089, narration regeneration sourced from these services, hero-prompt regeneration from these services, POI description backfills from these services. Manual / curated content (operator-authored, MassGIS MHC, TigerLine-derived, hand-edited Wikipedia with operator citations) is unaffected. Existing `salem_pois` / `salem_tour_stops` data sourced from these services is grandfathered for now but may need re-attribution before AAB upload — the operator will direct this when the reformulation lands.

### Recent session outcomes — full detail in `SESSION-LOG.md` and live logs

- **S232 (2026-05-08):** Curiosity-driven debug-only matrix-tilt 3D prototype. `TiltContainer` wraps the MapView, concats a `setPolyToPoly` perspective matrix, inverse-transforms touch events. Long-press the "+" zoom button cycled 0/30/45/60°. Wider-MapView two-pass attempt to fill wedges was reverted (seam at horizon). ~18 `Marker(binding.mapView)` constructions sed-replaced with pass-through `BillboardMarker`. Pixel 7a recommended as the budget rooted Android target. Live log: `docs/session-logs/session-232-2026-05-08.md`.
- **S230 (2026-05-06):** Field-edit Android feature gained a "create new POI" entry kind via long-press on empty map. JSONL schema bumped 1 → 2 with a `kind: "update" | "create"` discriminator. Web admin gate requires operator-supplied category at apply time; INSERTs with `data_source='admin_create_field'`. Owed: real-walk field validation. Live log: `docs/session-logs/session-230-2026-05-06.md`.
- **S229 (2026-05-06):** Three-part shipping. Burst-photo admin overlay (`BurstPhotosOverlay.tsx` + `/admin/burst-photos`); POI-tree `SOURCE:*` cleanup; Android field-edit feature A+B+C end-to-end (toolbar toggle → JSONL → `tools/pull-field-edits.py` → web-admin Field Edits inbox with Apply/Reject). Operator field-tested through a Starbird subcategory edit. Live log: `docs/session-logs/session-229-2026-05-06.md`.
- **S228 (2026-05-06):** GPS-burst camera shipped (headless CameraX, debug-only toolbar button) + `tools/pull-and-organize-burst-photos.py` organizer with 120 s gap split rule. Round-1 captured the 485-photo Beverly→Salem loop; round-2 tuning dropped throttle 3 s → 2 s (replaced by S231's 1 s time-driven tick). Live log: `docs/session-logs/session-228-2026-05-06.md`.
- **S227 (2026-05-05 → 06):** Haunt refinement — Room v18 → v19 with `haunt_duration_s`, sprite overlay rewrite (alpha/scale decoupling, depth bob, frame-11 upright facing, elliptical orbit, scale clamp), osmdroid heading-up rotation counter-fix in `SpriteOverlay.draw`. Owed: bridge-POI upright field-walk. Live log: `docs/session-logs/session-227-2026-05-05.md`.

### S231 outcome — Splash decision tree + Rapid Recon + sidebar reorg + auto-fire camera (2026-05-07 → 08)

Detailed shipment list lives in `docs/session-logs/session-231-2026-05-07.md` and the SESSION-LOG.md entry. Headlines: TigerLine 2025 polygon asset + cache-proxy admin endpoints + web-admin Splash Tree editor + Kotlin runtime resolver (verified live, Beverly fix → 2.7-mile Salem proximity message); Rapid Recon time-driven 1 Hz; web-admin sidebar reorg; photos cluster overlay; auto-fire toolbar recon camera; FAB zoom step 1.0; Vite proxy localhost:4300.

### S233 outcome — Matrix-tilt 3D promoted to V1 (wedge fill + 3D FAB + perspective touch fix) (2026-05-08)

S232's debug-only prototype hardened and shipped. Detailed shipment list in `docs/session-logs/session-233-2026-05-08.md`. Headlines: symmetric upward MapView extension fills wedges with real distant tiles through one perspective transform; manual `Matrix.mapPoints` touch transform (Android's `MotionEvent.transform(Matrix)` mangles perspective matrices — inverted-y); new "3D" FAB (`btnTilt3d`) cycles 0/30/36/42/48° ladder persisted via `MenuPrefs.PREF_TILT_3D_DEG`; `BuildDefaults.TILT_3D_ENABLED` flipped to `true` (ships in AAB); verbose logging across TiltContainer + zoom buttons + FAB for the FAB-x5-at-high-zoom ANR.

### S234 outcome — Tile bake +10mi everywhere q=60 + PoiCache + heading-up fast path + V1 call-site gates (2026-05-08 → 09)

Detailed shipment list in `docs/session-logs/session-234-2026-05-08.md`. Headlines: full +10mi ring at every zoom (z14-z19) WebP q=60 — 715,841 tiles in 9 min via new 8-worker parallel-bake infra (`tools/tile-bake/render-worker.js` + `bake-parallel.js`, hash-split `(bx*100003+by) % NUM_WORKERS`), bundle 58.9 → 261.7 MB, debug APK 161 → 374 MB; **PoiCache Hilt @Singleton** at `app-salem/src/main/java/.../content/PoiCache.kt` loads 2,076 POIs once in 572 ms, builds 8 pre-built indexes (id/category/subcategory/district/visible/narrated/tour/districts), routed through SalemContentRepository's 16 POI read methods + FindViewModel + WitchTrialsRepository + SalemMainActivity.loadHauntConfigsAsync — **zero Room queries during runtime after the load**; **narration marker diff cache + Mutex** makes filter toggles near-instant no-ops (fixed startup race that double-attached 1,130 markers); **heading-up sensor-rate fast path** via `lastProcessedMoveBearing` early-return eliminates ~100 Hz allocation churn + log spam; **V1 call-site gates** on scroll/zoom listener hot paths so V1-disabled subsystems aren't even invoked; MarkerIconHelper LRU 2000 → 4096; comprehensive TiltContainer timing instrumentation (burst + sampled-steady + per-MOVE + overlay count) + debug overlay-suppression long-press toggle on 3D FAB. Three new feedback memories: `feedback_diagnose_before_changing.md`, `feedback_map_easy_pois_are_the_lever.md`, `feedback_v1_gate_at_call_site.md`.

### S235+ next-session docket

- **Residual cold-start tile-decode spike** — one Choreographer "Slow dispatch took 1134ms" on first tilt entry remains. Steady-state frames mostly 7ms but occasional 30-43ms spikes at z19+tilt+603 overlays. POI work confirmed eliminated; remaining cost looks like tile decode + GC. Worth canvas-level breakdown if drag chunkiness is still felt in field testing.
- **`narrationPointsCache` field cleanup** — instance-level cache added to SalemMainActivity earlier in S234 is now redundant with PoiCache (its first read goes through repository → PoiCache anyway). Safe to delete next session, ~5-line removal.
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

- **Operator field-validation of S221 detour feature** on Lenovo HNY0CY0W (Plan steps 2–12). Polish anticipated post-field-test: distance/time banner labels, out-and-back preview, rejoin-fade timing.
- **Refine subtopic content quality** (S220 carry): figure-card body trimming, adjacency teasers, distance phrasing.
- **`is_tour_poi=true` Lint cleanup** (S220 carry): spurious `young_world_*` rows. Add a Lint check flagging `is_tour_poi=true` AND `category IN (EDUCATION, OFFICES, HEALTHCARE, AUTO_SERVICES, FINANCE, APARTMENT_BUILDING, FOOD_DRINK, LODGING)`.
- **Borderline-historical commercial opt-in audit** (S220 carry): Hawthorne Hotel etc. — operator-side flip `is_historical_property=true`.
- **Tier 3 disk reclaim** (~4.4 GB): `tools/poi-icons/`, `tools/hero-triptych/output-full/`, `tools/tile-bake/data/sources/`, `cache-proxy/out/l3-essex/`, `docs/archive/poi-icons-unused-styles_*/`.
- **`.gitignore` audit**: `overnight-runs/`, `cache-proxy/poi-cache.json`, `cache-proxy/cache-data.json`, `web/dist`, `web/tsconfig.tsbuildinfo`, `docs/bake-tests/`.
- **Field listen-test of S217 BusinessLabel fallbacks** + TTS pronunciation of "cafe" / "centre".
- **Field validation of S217 FAB override** — every nearby POI announces with FAB ON.
- **John Ward House tour-leg fix** (S216): operator-side re-author or relocate.
- **HISTORICAL_LANDMARKS icon + hero regeneration** (S216).
- **Content rebuild for `description` post-`historical_note`-merge** (S216).
- **Dequeue 40 m staleness drop** (S216) — align with `geofence_radius_m`?
- **S215 suspicious-leg graph investigation** — Dr. K 13/14, WD1 3/6/10/11.
- **PoiDetailSheet/Find further cleanup** (S224 carry): `enterFilterAndMapMode`/`exitFilterAndMapMode` clearXXX/stopXXX no-ops still in source.
- **CLAUDE.md "Pinned" block cleanup** — references Room v11 / S186-S188 state; actual schema is v17.

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

### Architectural housekeeping (S201) — done

`COMMERCIALIZATION.md` / `IP.md` replaced with V1-truthful stubs (pre-pivot originals archived 2026-04-29). `CURRENT_TESTING.md` archived. Ship anchor updated Sept 1 → Aug 1.

---

## Backlog from S185–S200 (TRIAGED S203, ✅ items pruned)

Open items only: **V1.0.1 (S196)** multi-admin auth + actor-from-session wiring; **V1.0.1 (S196)** PoiEditDialog inline history panel; **V1 (S200)** operator hand-authors McIntire content; **V1.0.1 (S192)** backfill `year_established` on 500+ HIST_BLDG rows; **V2 (S195)** driving mode (V1 walking-only); **V1 (S185)** re-author 5 deleted Kotlin tours in PG (operator-driven); **V2 (S179)** routing-jvm Option 2 + walk-sim cleanup; **V1.0.1 (open)** DirectionsSession JVM tests.

---

### Older carry-forwards (S159–S172) — TRIAGED S203

Open items: V1.0.1 water animation tuning (S172); V2 osmdroid → WickedMapView migration; V1.0.1 POI location verifier batch-run (S162); V2 MHC hidden-POI companion importer (S163); V1 GPS + V1-offline drive regression (S161, operator); V1.0.1 Witchy tile-bake bbox extension for operator's Beverly home (S159).

---

## Architectural pivots & rule changes (recent → older)

- **2026-05-01 (S216):** **`HISTORICAL_LANDMARKS` split off from `HISTORICAL_BUILDINGS`.** MASSGIS-only (`data_source = 'massgis_mhc'`) POIs reclassify to a new `HISTORICAL_LANDMARKS` category that's gated by the existing "POIs Hist. Landmark" Layers toggle (default off). Curated `HISTORICAL_BUILDINGS` (BCS / OSM / manual / api_sync) always narrate in tour mode — no Layers gate. `NarrationGeofenceManager.TOUR_HIST_LANDMARK_CATEGORIES` swapped from `BUILDINGS, ENT, LODGING` to `LANDMARKS, ENT, LODGING`.
- **2026-05-01 (S216):** **`historical_note` column dropped (Room v15 → v16).** Pre-existing rule: `historical_narration` is the strict pre-1860 audio script. `historical_note` was a duplicate-purpose blurb field used for display + search + legacy fallbacks; consolidated into `description` (no gaps), then column dropped end-to-end across PG, Kotlin (5 files), cache-proxy + web admin (5 files), Room schema bump, KSP regen.
- **2026-05-01 (S216):** **Auto-bake publish chain into Gradle preBuild** (S180/S185 carry-forward done) — every `:app-salem` build now runs `publishSalemContent` (4 scripts) + `verifyBundledAssets` so admin-tool edits in PG reach the device automatically. Escape hatch `-PskipPublishChain`.
- **2026-05-01 (S216):** **Walk-sim onboarding owns the directions session** — `TourViewModel.startTour(skipOnboarding: Boolean = false)` flag; both walk-sim entrypoints opt out of the parallel `onboardToNearestPolylinePoint` path so the drift-reroute loop can't paint a green directions polyline on top of the per-leg overlays.
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

**Sessions completed:** 235. **Internal ship target: 2026-08-01** (operator-confirmed S201). Salem 400+ peak attendance October 2026 (~1M visitors).

---

## POI Inventory (refreshed S180 from live PG)

- **PG `salem_pois`:** 2,081 live (S216 dedup: 220 dead hard-deleted + 43 live cluster losers hard-deleted + 1 Gardner-Pingree dup, all merged before delete; further trim through S221+). 484 reclassified `HISTORICAL_BUILDINGS` → `HISTORICAL_LANDMARKS` (curated buildings now 105). `historical_note` column dropped S216; content lifted to `description` (143 POIs touched, 0 gaps).
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
