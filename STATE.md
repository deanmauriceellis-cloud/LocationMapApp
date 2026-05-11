# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — must stay under 200 lines.

**Last updated:** 2026-05-10 — **Session 241 (mass-edit POI workflow live — end-to-end .xlsx/.ods round-trip in production use).** New admin tool tab under Tooling → 📊 Mass Edit. Export every POI to .xlsx (frozen header, per-column autofilter, hidden id UUID column A, in-cell dropdown pick-lists for 24 enum-bound columns) → edit in LibreOffice Calc → upload .xlsx or .ods → per-cell diff against live PG → per-cell approve/reject (or one-button Accept all) → apply in a single PG transaction via the existing `buildUpdateClause` whitelist. **Bit Bar field-validation: PASS** (S240 default_visible/is_narrated decoupling holds on real device). **Backend:** new `cache-proxy/lib/admin-mass-edit.js` (~720 LOC) with 3 routes — export-spreadsheet, import-spreadsheet (multer multipart), apply-mass-edit. JSZip post-write patcher injects frozen-pane XML + `<dataValidations>` block (24 list bindings). Mirrors canonical soft-delete + restore SQL from admin-pois.js for the `is_deleted` writable "intent" column. Conflict guard rejects with 400 when one POI gets both soft-delete + restore intents in the same batch. **Frontend:** new `web/src/admin/MassEditTab.tsx` (~450 LOC) — sticky review header with Accept all / Reject all / Cancel / Apply; per-POI cards with 🗑 / ↩ chips for soft-delete / restore; rose badge + strikethrough for soft-deleted rows; amber badge for stale rows (`pg_updated_at > exported_at`). **Three operator-driven scope expansions** during the session: (S241.1) `is_deleted` writable for bulk dupe cleanup; (S241.2) per-cell pick-lists for category/subcategory/booleans/all enum columns (`allowBlank=1 showErrorMessage=0` so operator can type their own value to override); plus column-reorder (category|subcategory|is_deleted|name as first four visible cols, created_at/updated_at pushed to end) and soft-deleted-row inclusion in the export (87 currently flagged). **Nasty JS bug caught during dev:** `String.replace` `$1`-as-backreference interpretation broke the subcategory range `$B$171` mid-XML — fixed by switching to function-form replacement. **New deps:** `xlsx@0.18.5`, `multer@2.1.1`, `jszip@3.10.1` (all cache-proxy). **Operator's first field-use:** 2070 → **2042 POIs** (28-row delta — dupe cleanup + recategorizations + soft-deletes). Full publish chain + 374 MB debug APK + Lenovo HNY0CY0W uninstall+install — clean deploy. **Eight verification PASS gates** (column-order regression, no-op round-trip, soft-delete one row, restore one row, mixed batch transaction, conflict guard, already-deleted rejection, multi-line + em-dash + smart-quote paste round-trip byte-identical). **Carry to S242:** operator on-device validation that the 28-row mass-edit landed; SheetJS prototype-pollution audit (acceptable: admin surface is Basic-Auth on localhost only); optional cascading subcategory pick-list; on-device verification of the new APK + S240/S239/S238/S237/S236 carry-forwards still apply. **Previous (S240/S239/S238/S237/S236) context still applies below.**

**Recent context — for per-session detail see `SESSION-LOG.md`:** S240 decoupled map marker rendering from narration — `default_visible` is now an honest controller (new `renderable` snapshot in `PoiCache` parallel to `narrated`; 51 POIs newly map-renderable including Bit Bar). S239 fixed three tilt-mode rendering bugs (St. Peter's flat duplicate, upright drift, magnify FAB icon scaling) all traced to HW display-list staleness — shipped Option 2 (per-Marker tilt-suppression flag + MapListener + animator updateListener). S238 shipped Phase 3 wedge fill (`topY` 1.0→0.6) + cull loosen + diagnostic strip. S237 shipped two-pass tilt rendering (strip-then-pass-2). S236 attempted +10mi-with-buildings raster bake → Lenovo crash → rolled back to S234 tiles.

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

### Recent session outcomes — see `SESSION-LOG.md` (last 10) and `SESSION-LOG-ARCHIVE.md` (older) for detail

(Per-session detail lives in `SESSION-LOG.md` and `docs/session-logs/`. The "Last updated" para at the top covers this session's headlines.)

### S242+ next-session docket

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

**Sessions completed:** 241. **Internal ship target: 2026-08-01** (operator-confirmed S201). Salem 400+ peak attendance October 2026 (~1M visitors).

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
