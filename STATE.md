# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — must stay under 200 lines.

**Last updated:** 2026-05-15 — **Session 269.** S4 of the POI Passport plan landed (4 of 5 plan-slice sessions complete) PLUS a major architecture revamp through 5 Lenovo bundletool installs in one session. **S4 destructive shipped**: full `TourEngine.kt` / `TourModels.kt` / `TourViewModel.kt` rewrite — stops-progress shed gone (`currentStopIndex`, `completedStops`, `skippedStops`, advance/skip/add/remove/reorder), 4 dead UIs deleted (Active Tour dialog → tight `showTourInProgressDialog`; bottom HUD bar `updateTourHud`+`removeTourHud`; numbered polyline pins in `drawTourRoute`; per-stop detail dialog `showTourStopDetail`), `currentLegEndForDetourAnchor()` helper for the detour rejoin path landing BEFORE `currentStopIndex` deletion, suspend `endTour()` builds passport-aware `TourSummary`, completion auto-fires via `passportVisitDao.countHeardAmong(tourPassportPoiIds) == passportPoiIds.size` (one-shot per session), `showTourCompletionDialog` rewritten with `passportHeard / passportTotal` stats + "Open Passport" CTA + "Tour Complete!" vs "Tour Ended" title flip on partial. **S4 architecture revamp** (operator-driven, 5 Lenovo installs): (a) **walk-derived per-tour passports** — new `WalkPassportDialog.tsx` full-screen modal (Leaflet map with Witchy basemap, radius slider 10–300 m with ft display, scrollable checkbox list ordered by walk-encounter index, click-toggle map markers, hover tooltips, auto-fit bounds); new backend endpoints `GET /admin/salem/tours/:tourId/passport-walk-candidates` (walks `salem_tour_legs.polyline_json`, computes min point-to-polyline distance via equirectangular approximation) + `POST /admin/salem/tours/:tourId/walk-derived-passport` (UPSERTs synthetic salem_passport_filters row + replaces salem_passport_pois); new `auto_bake BOOLEAN` column on `salem_passport_filters` with idempotent ALTER + bake-script preserves manual lists when false. (b) **`recomputeTourMetadata` helper** — replaces stale `syncStopCount`, computes `stop_count + distance_km + estimated_minutes` from `salem_tour_stops + salem_tour_legs` on every leg/stop mutation + on PATCH (caught real bug: tour_DrKs_001 was showing stale 5.85 km vs real 2.81 km). Empty-PATCH bodies now valid as a "force refresh" signal. (c) **`pool ∩ proximity` model** — operator clarified the global passport = "the pool" (categories + flags), per-tour passport = (pool ∩ polyline-proximity); rewrote `WalkPassportDialog` with explicit "Compute Candidates" button (wipe-confirmation, slider becomes parameter for the next compute click only — `Recompute wipes everything; manual edits persist until next wipe`), pool banner showing filter source + match count, orphan warning for saved POIs no longer in pool. (d) **PassportSheet routing** — `show(fragmentManager, passportId?)` pins the sheet to the right passport from tour-completion CTA, tour-in-progress dialog, AND toolbar witch-hat during active tour; context-aware overflow ⋮ menu adds "End tour" item when `tourState` is Active/Paused/Detour. (e) **"Stamps not stops"** — operator: *"we care about the stamps/POI, not the stops"* — Android tour cards now show "49 / 72 / 188 / 41 stamps" (passport-bound POI count via `PoiPassportDao.listPassports()`) instead of meaningless `stop_count` (free polyline waypoints); web admin TourTree left list + TourMetadataForm Walk-derived Passport panel both surface `passport_poi_count` via new `LEFT JOIN LATERAL` on auto_bake=false filters in GET endpoints + PATCH response. **Final state**: 797 rows in baked `poi_passport` (447 global Whole-Salem pool + 49+72+188+41 per-tour passports). Five bundletool installs at 19:39 / 20:17 / 20:26 / 21:19 / 21:28. **S5** (operator field walk + PassportSheet empty-state polish + Pixel 8 portrait toolbar fit) still owed. V1 ship-cliff status unchanged. ~76 days to 2026-08-01 ship; 4 days to Form TX deadline 2026-05-20. Full detail in `docs/session-logs/session-269-2026-05-15.md`.

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

### S270 opener: operator field walk + S5 polish

S4 destructive + the entire architecture revamp shipped in S269. **S5** (the last plan-slice) owes:

1. **Operator field walk** on Lenovo (or Pixel 8 — both attached, the S268-build APK on Pixel 8 needs a fresh bundletool install to carry the S269 code). Smoke flow: tap witch-hat icon outside a tour → Whole-Salem (447 hollow stamps); start a tour → witch-hat now opens that tour's passport (49 / 72 / 188 / 41 hollow stamps); walk a few POIs → stamps fill + status-line shows "Tour: <name>"; full pass through a tour → "Tour Complete!" dialog auto-fires with "X / X stamps" + Open Passport CTA; tap End mid-tour → "Tour Ended" dialog with partial stats; overflow ⋮ → "End tour" item visible during active tour; passport-picker dropdown lets you switch between the 5 baked passports.
2. **PassportSheet empty-state polish** — when a filter yields 0 POIs, show better messaging than the current empty list.
3. **Pixel 8 portrait toolbar fit** check (9 icons including witch-hat).
4. **Memory + doc polish** — already-saved memory files: `feedback_passport_pool_intersect_proximity.md` + `feedback_stamps_not_stops.md`. STATE.md / SESSION-LOG.md updates done at S269 close. MASTER_SESSION_REFERENCE.md update is the last polish item.

**S269 carry-forwards still open:**

- The pre-pivot `default_salem_walking` filter holds operator-set `min_geofence_radius_m=10`. If you want to broaden/narrow, edit it in PassportTab and rerun `node cache-proxy/scripts/publish-poi-passport.js && node cache-proxy/scripts/align-asset-schema-to-room.js` then bundletool install.
- The Passports tab still exposes `min_geofence_radius_m` on per-tour passport rows where it's effectively dead (auto_bake=false skips filter SQL). Could simplify the tab UI to hide the field on tour-bound rows OR drop per-tour rows from the tab entirely (authoring lives in the Tour editor now). Deferred.
- `passport_poi_count` is computed via `LEFT JOIN LATERAL` on every GET — fast at current scale (5 passports). Future: denormalized column on salem_tours if scale demands.
- Linger and Listen (S267) field walk to validate subtopic body queueing still owed.

**S267 latency-fix carry-forwards (untouched in S269):**
- DEBUG-only ~30 min each: drop `MapDebugDumper` auto-cadence 10s → 60s; coalesce `refreshNarrationIcons` 3-bucket → single pass.
- Ships to release: `BillboardMarker` viewport-cull check before `super.draw()` (~1-2 hr); off-thread proximity scan `checkPosition()` → Default dispatcher (~2-4 hr, more invasive — defer until simpler fixes prove insufficient).

**S268 carry-forwards still open:** MIGRATION_2_3 verification (the v2→v3 upgrade path is exercised only on in-place install over the pre-S268 build — every install since S268 has been `adb uninstall && bundletool install-apks` which skips it).

**S266 carry-forwards still open:** field-validate POI edits render on both devices; validate S265 narration banner shrink on Pixel 8 portrait; then continue per-surface portrait iteration or pivot to broader Phase A survey from `docs/plans/pixel8-portrait-support.md`.

**Misc:** Lenovo `enabled=3` (DISABLED_USER) mystery — possibly Zui launcher quirk after S266 in-place-install WAL violation; `pm enable` recovers.

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

### S245+ aged backlog (rendering / content / tools — referenced when in scope)

- **Rendering:** RollingGrassOverlay green-patches on Bluestacks; sprite field-walk near bridge POI; field-validate S238 perspective at z19/z20 + tilt 30/36/42/48°; first-frame layout race in `applyMapExtension` (post-extension `mv.requestLayout()`); cold-start tile-decode spike (Choreographer 1134ms); optional overlay culling / batched Overlay perf candidates.
- **Content:** lint review of 51-POI newly-renderable set; Sweet Boba regression; Layers menu category gap (FOOD_DRINK/SHOPPING toggles don't gate narration overlay); tilt-mode odds-and-ends (S239); splash variants (32 TODO in `docs/SPLASH-ANNOUNCEMENTS-V1.md`); POI/path alignment review of 1962-photo session; Find menu tile + hero review.
- **Tools:** TTS-settings deep-link reliability; SheetJS CVE audit; cascading subcategory pick-list; field-edit UPDATE/CREATE loops; Rapid Recon fresh-walk 1 Hz tick + true-north; auto-fire camera field-test.

### Other carry-forwards (lower priority — pull when in scope)

- Operator field-validation: S221 detour / S220 subtopic content / S217 BusinessLabel + FAB override; spurious `is_tour_poi=true` lint cleanup; borderline-historical commercial opt-in audit.
- Tier 3 disk reclaim ~4.4 GB (poi-icons / hero-triptych / tile-bake / l3-essex / unused-style archives) + `.gitignore` audit (overnight-runs, poi-cache.json, cache-data.json, web/dist, tsconfig.tsbuildinfo, docs/bake-tests).
- S216 follow-ups: John Ward House tour-leg fix, HISTORICAL_LANDMARKS icon+hero regen, description rebuild post-historical_note merge, dequeue 40m staleness vs `geofence_radius_m`.
- S215 suspicious-leg graph: Dr. K 13/14, WD1 3/6/10/11.
- PoiDetailSheet/Find cleanup (S224): clearXXX/stopXXX no-ops; CLAUDE.md "Pinned" Room version stale (v11 → v19).
- S206: speed-aware queue cap (>15 mph), DensityTour 0-stop fallback decision; Operator-driven Salem cross-repo commit (`genbiographies/main.go` + 3 hand-edited bios).
- Rebuild signed AAB + 30-min Lenovo smoke (first since S180); operator field-walks (S204): GPS-OBS heartbeat + Witch Trials bios + airplane-mode drive regression.
- Operator content: re-author 5 polyline tours via web admin (S185); McIntire content drain (S200).

### S205+ — V1 feature additions (priority order, KEPT for V1)

1. **Halloween / October seasonal layer** — date-checked content overlay (different POI markers, ghost-tour POIs unlocked October 1, photo-spot pins, October-only event narration). Zero network. Reuse existing geofence + narration paths. Scope tight, ~1 week.
2. **Time-slider basemap, 1906 only** for V1 (Sanborn 1906) — full 4-year slate (1890/1906/1950/1957) deferred to V1.0.1+. ~1 week for one year.
3. **Tour-completion certificate (PDF on-device)** — end-of-tour souvenir with route + triggered POIs + date + Salem-themed border. Zero network. ~2-3 days.
4. **Salem Heritage Trail polyline tour — YELLOW, not red** (the trail was repainted yellow). Hand-trace via admin or import official KML if available. ~1 day. See memory `reference_salem_heritage_trail_yellow.md`.

### V1 features deferred / killed (S201)

Bark hero voice clips SKIPPED (Android TTS only); Hotel B2B partnerships post-launch; Pre-launch beta small (2-3 friendly eyes); iOS port TBD.

### Marketing (operator-led, before Aug 1)

Channel: Salem Chamber of Commerce + local-first. Asset packet (1-page sell sheet, feature graphic, 3-5 hero screenshots, 2-para press blurb) drafted in a content-only session — operator provides photos + final approval, I draft text + screenshot frames.

---

## Older backlog (S159–S200, TRIAGED S203)

**V1 (open):** McIntire content hand-author (S200); re-author 5 deleted Kotlin tours in PG (S185, operator); GPS + V1-offline drive regression (S161, operator). **V1.0.1:** multi-admin auth + actor-from-session (S196); PoiEditDialog inline history panel (S196); `year_established` backfill on 500+ HIST_BLDG rows (S192); DirectionsSession JVM tests; POI location verifier batch-run (S162); Witchy tile-bake bbox extension for Beverly (S159); water animation tuning (S172). **V2:** driving mode (S195, V1 walking-only); routing-jvm Option 2 + walk-sim cleanup (S179); osmdroid → WickedMapView migration; MHC hidden-POI companion importer (S163).

---

## Architectural pivots & rule changes (recent → older)

- **2026-05-10 (S241):** Mass-Edit POI workflow live (`admin-mass-edit.js`, `MassEditTab.tsx`, export/import/apply round-trip; `is_deleted` writable; 24 enum dropdowns via OOXML dataValidations injection; deps `xlsx`, `multer`, `jszip`).
- **2026-05-01 (S216):** `HISTORICAL_LANDMARKS` split from `HISTORICAL_BUILDINGS` (Layers-toggle gated); `historical_note` column dropped (Room v15→v16, lifted into `description`); auto-bake publish chain wired into Gradle preBuild (`-PskipPublishChain` escape); walk-sim onboarding owns directions session.
- **2026-05-15 (S269):** POI Passport architecture matured to **pool ∩ proximity** model. Global passport (`default_salem_walking`) defines the "pool" via filter SQL (categories + flags + min broadcast radius + year range); per-tour passports are walk-derived snapshots of `pool ∩ polyline-proximity`, edited via the new Tour-editor walk dialog. Recompute is destructive (operator-confirmed: "wipes everything; manual edits persist until next wipe"). Tour metadata is now fully derived: `recomputeTourMetadata` rebuilds `stop_count + distance_km + estimated_minutes` from `salem_tour_legs + salem_tour_stops` on every mutation + on PATCH (operator-supplied values get overwritten). New "stamps" UX: Android tour cards + web admin tour list both show passport-bound POI count instead of legacy `stop_count` (free polyline waypoints). New `auto_bake` column on `salem_passport_filters` separates filter-driven (auto-bake, global pool only) from operator-curated (manual, per-tour) rows. Five Lenovo bundletool installs in one session.
- **2026-05-04 (S224):** Session-start protocol amended lean → lean+state — `STATE.md` + most-recent live log mandatory. `enqueueNarration` cancel-stamp scoped to direct-play (fixed walk-sim queue stall from S146 Idle-guard collision).
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

**Sessions completed:** 269. **Internal ship target: 2026-08-01** (operator-confirmed S201). Salem 400+ peak attendance October 2026 (~1M visitors).

---

## POI Inventory (refreshed S180 from live PG)

- **PG `salem_pois`:** 2,037 live (S253 refresh — net −5 from S241's 2,042) + 87 soft-deleted (S241 mass-edit dropped live count by 28 — dupe cleanup + recategorizations + soft-deletes via the new spreadsheet round-trip; S216 dedup history: 220 dead hard-deleted + 43 live cluster losers hard-deleted + 1 Gardner-Pingree dup, all merged before delete; further trim through S221+). 484 reclassified `HISTORICAL_BUILDINGS` → `HISTORICAL_LANDMARKS` (curated buildings now 105). `historical_note` column dropped S216; content lifted to `description` (143 POIs touched, 0 gaps).
- **Room DB:** at `app-salem/src/main/assets/salem_content.db`, **v20 schema** (S268), identity_hash `837ec05ad90541fa76a8a413a06394e0`. Witch Trials intact (npc_bios 49 / articles 16 / newspapers 202). 4 tours / 73 legs (S224). **5 passports / 797 baked POIs (S269)** — 1 global Whole-Salem pool (447 POIs filtered through categories + historical_narration + min_geofence_radius_m=10) + 4 walk-derived per-tour passports (49 / 72 / 188 / 41) authored as `pool ∩ proximity` via the Tour-editor walk dialog. v11 (S186) `is_civic_poi`. v12 (S192) `historical_narration`. v13 (S193) `is_historical_tour`. v14 (S195) `is_historical_property`. v15 (S198) dropped `body_points` from WitchTrialsNewspaper. v16 (S216) dropped `historical_note` from SalemPoi. v17 (S219) added `narration_subtopics` JSONB to SalemPoi. v18 (S226) added 6 haunt columns. v19 (S227) added `haunt_duration_s` REAL. v20 (S268) added `PoiPassport` entity (`poi_passport` table). **UserDataDatabase** also bumped v2 → v3 (S268) with `passport_visit` table + hand-written `MIGRATION_2_3` (strict, no destructive fallback per S180 lockdown). **PG schema** also gained `salem_passport_filters.auto_bake BOOLEAN` (S269 — distinguishes filter-driven from operator-curated rows for `publish-poi-passport.js`).
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

1. NOTE-L014 / OMEN-008 — Privacy Policy V1-minimal Posture A shipped S145 (`docs/PRIVACY-POLICY-V1.md`); full OMEN-008 draft pending OMEN review.
2. NOTE-L015 SalemCommercial cutover — PARKED POST-V1 (S145).
3. Cross-project SalemIntelligence anomaly relay — `~/Development/OMEN/reports/locationmapapp/S153-si-anomalies-relay-2026-04-20.md` (ANOM-001, ANOM-002).
4. NOTE-L019 restrooms_zombie.png regen (LOW); 9-dot menu witchy backgrounds PARKED S146.

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
