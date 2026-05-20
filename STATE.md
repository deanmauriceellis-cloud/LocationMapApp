# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — must stay under 200 lines.

**Last updated:** 2026-05-19 — **Session 283.** Chatbot V1 scoped end-to-end (on-device, rule-based, POI facts only, distance-sorted). Operator declared scope as `"Where is the Phillips House?"` / `"Where can I find pizza?"` / `"What are pizza places close to me?"`. Recommended SQLite FTS5 over hand-rolled inverted index. Locked 7 design decisions across 3 AskUserQuestion rounds: dedicated full-screen Find screen via drawer menu / FTS5 + Levenshtein-name fuzzy fallback / locations + short blurb inline AND direct-answer for high-confidence name queries / all 2,039 active POIs indexed (name + category + subcategory + description + short_description) / GPS-unavailable falls back to alphabetical sort / synonyms deferred to post-v1 / drawer entry near top. Wrote `docs/plans/Chatbot_V1.md` (~150 lines) capturing locked scope + 7 open items for operator pondering (Phase 0 recall spike as gate, FTS bake hook placement, Room v23→v24 rotation discipline, Hawthorne disambiguation edge case for direct-answer threshold, default radius, drawer placement, empty-input state) + 6 implementation phases + 5 risks + explicit out-of-scope list. **Phase 0 is the recommended first action when implementation kicks off:** 5-min PG query against ~15 tourist-staple keywords against the proposed FTS columns to validate the synonyms-deferral assumption before any code. Plan parked for operator pondering. Net code-side change this session: zero — discussion + plan doc only. No PG / Room / Android / web / cache-proxy / publish chain / device installs touched. Full detail in `docs/session-logs/session-283-2026-05-19.md`.

### Prior — S282 (2026-05-18)

LongWalkTour walk-sim full-route HB coverage on Lenovo (2,619 steps / 59 TTS dwells / 130 GPS samples / `tools/s281_hb_coverage_v2.py`): **48 HBs in range, 46 announced, 2 missed.** Diagnosed both: **(1)** `14 Mall Street (Scarlet Letter House)` — content-authoring gate (PG row had `is_narrated=0` despite 781 chars `historical_narration` + 812 `long_narration`; the DAO `WHERE (is_narrated = 1 OR ...)` excluded it from the narration pool entirely). **Fixed S282** — PG `UPDATE salem_pois SET is_narrated=true WHERE id='scarlet_letter_house'`, full 5-script publish chain re-ran clean (Narrated 1987→1988, identity_hash `4db15f...` unchanged), debug AAB rebuilt in 16 s, bundletool install on Pixel 8 + Lenovo. Commit `9a4e23a`. **(2)** `Paul Revere's Bell` — real runtime miss, root cause not provable from S282's log (filter lacked NARR-GEO / NARR-GATE). Both Bell and Armory Park are `is_narrated=1`, ~15 m apart; Armory fired ENTRY at step ~1024 but Bell never appears in any `NARR-QUEUE` line. Stream filter for the next walk extended to `WALK-SIM|NarrationMgr|NARR-QUEUE|NARR-STATE|NARR-GEO|NARR-GATE` to distinguish scanner-never-fired-ENTRY from enqueue-silently-SKIPped. Confirmed the dwell mechanism (`SalemMainActivity.kt:2284-2316`) is NOT the cause. Latent fragility flagged (not actioned): `events.collectLatest` at `SalemMainActivityNarration.kt:307` is safe today because `enqueueNarration` is synchronous, but a future change making it suspending would silently drop concurrent ENTRYs.

### Prior — S281 (2026-05-18)

Publish chain (5/5) + bundletool install on Pixel 8 + Lenovo — S279's architectural changes (3 tours / Dr. K's-default / no Whole-Salem global pool / no TOUR_0001) brought to-device. Asset DB rebuilt to Room v23 (`4db15f...`), 2,039 POIs / 3 tours / 59 legs / 309 collection entries. HISTORICAL_BUILDINGS coverage spot-check on LongWalkTour walk-sim (1887 steps / Lenovo) attempted — logcat ring buffer was only 256 KiB so 78% of the walk was unrecoverable; captured 22% window (steps 1540–1880 / 36 GPS samples) showed 7 in range / 7 announced / 0 missed. Bumped Lenovo logcat buffer 256 KiB → 16 MiB to unblock S282's full-route capture. `tools/s281_hb_coverage_v2.py` preserved.

### Older prior-session blocks pruned 2026-05-19 (S283) for the 200-line cap; see `SESSION-LOG.md` for S274–S280 + `docs/session-logs/` for full detail.

### V1 ship-cliffs (all closed)

Cliff 1 (Play 200 MB ceiling) closed S256 via install-time Asset Pack; Cliffs 2 (`largeHeap` + 60° tilt OOM) + 3 (`allowBackup=true`) closed S255. Build/install workflow with asset packs lives in `CLAUDE.md` Key Paths block + `feedback_adb_install_after_db_rebake.md`.

---

## TOP PRIORITY — V1 launch triage (operator-confirmed 2026-04-29; revised S203 close 2026-04-30)

> **Internal ship target: 2026-08-01** (72 days as of 2026-05-19). One month tighter than the prior Sept 1 anchor. Salem 400+ peak attendance is October 2026.

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

### S284 opener (parked at S283 close)

**Chatbot V1 plan parked for operator pondering.** `docs/plans/Chatbot_V1.md` is the authoritative scope doc — all 13 locked decisions + 7 open items + 6-phase implementation roadmap live there. **Phase 0 recall spike** is the recommended first action when operator gives the go-ahead: 5-min PG query over ~15 tourist-staple keywords (pizza, coffee, beer, ATM, restroom, parking, ice cream, bathroom, sushi, witch trials, ...) against `name + category + subcategory + description + short_description` columns to validate the synonyms-deferral assumption. The 7 open items include the Hawthorne disambiguation edge case (uniqueness rule for direct-answer triggering), FTS bake hook placement (fold into `align-asset-schema-to-room.js` vs. new standalone script), and a few smaller calls. Wait for operator to finish pondering before kickoff.

**S282 carry-forward (Paul Revere's Bell miss) still standing:** next walk-sim should be captured against the extended filter `WALK-SIM|NarrationMgr|NARR-QUEUE|NARR-STATE|NARR-GEO|NARR-GATE` (`grep -v WickedAnim`). Re-run `tools/s281_hb_coverage_v2.py` and grep the captured `NARR-GEO` lines around step ~1024-1140 for the Bell's `ENTRY` (or absence) — distinguishes scanner-never-saw-it from enqueue-silently-skipped. Lenovo logcat buffer is still 16 MiB (persists until reboot).

### S271 opener: Recon Layer 2 + Passport S5 still owed

S270 shipped Recon Layer 1 (bulk-cull photo triage tool). **S271 carries forward two parallel tracks:**

**Recon Layer 2** — Extend `web/src/admin/BurstPhotosOverlay.tsx` (already 80% built since S229/S231) so the geo cluster pins read the same `.kept.json` keep-flag state (kept = amber pins, unkept = slate); add POI-proximity filter chips (nearest-3 POIs within 50 m via PG lookup); add "Attach to POI" / "Drop" / "Defer" decisions for the curated set; possibly create new `salem_recon_photos` PG table (`id, source_filename, captured_at, lat, lng, heading_true, speed_kmh, poi_id NULLABLE, decision, created_at`) for the post-triage curated photo set that feeds downstream POI hero/gallery work.

**Passport S5** (carried forward from S269, untouched in S270):

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

### Other carry-forwards (lower priority — pull when in scope)

S245+ aged backlog (Rendering / Content / Tools — 26-55 sessions stale by S271) and 70+ sessions stale (Rebuild-AAB-since-S180, GPS-OBS heartbeat, re-author 5 deleted Kotlin tours, McIntire content drain) both moved to `docs/archive/STATE_carryforwards_removed_2026-05-16.md` (S271).

- Operator field-validation: S221 detour / S220 subtopic content / S217 BusinessLabel + FAB override; spurious `is_tour_poi=true` lint cleanup; borderline-historical commercial opt-in audit.
- Tier 3 disk reclaim ~4.4 GB (poi-icons / hero-triptych / tile-bake / l3-essex / unused-style archives) + `.gitignore` audit (overnight-runs, poi-cache.json, cache-data.json, web/dist, tsconfig.tsbuildinfo, docs/bake-tests).
- S216 follow-ups: John Ward House tour-leg fix, HISTORICAL_LANDMARKS icon+hero regen, description rebuild post-historical_note merge, dequeue 40m staleness vs `geofence_radius_m`. S215 suspicious-leg graph: Dr. K 13/14, WD1 3/6/10/11. PoiDetailSheet/Find cleanup (S224): clearXXX/stopXXX no-ops. S206: speed-aware queue cap (>15 mph), DensityTour 0-stop fallback decision; Operator-driven Salem cross-repo commit (`genbiographies/main.go` + 3 hand-edited bios).

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

**V1.0.1:** multi-admin auth + actor-from-session (S196); PoiEditDialog inline history panel (S196); `year_established` backfill on 500+ HIST_BLDG rows (S192); DirectionsSession JVM tests; POI location verifier batch-run (S162); Witchy tile-bake bbox extension for Beverly (S159); water animation tuning (S172). **V2:** driving mode (S195, V1 walking-only); routing-jvm Option 2 + walk-sim cleanup (S179); MHC hidden-POI companion importer (S163).

V1-open content carry-forwards 70+ sessions stale moved to `docs/archive/STATE_carryforwards_removed_2026-05-16.md` (S271); osmdroid → WickedMapView migration archived alongside (de-facto abandoned for V1).

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

**Sessions completed:** 283. **Internal ship target: 2026-08-01.**

---

## POI Inventory (refreshed S180 from live PG)

- **PG `salem_pois`:** 2,039 live (S271 refresh — net +2 from S270's 2,037). 87 soft-deleted (per `feedback_dedup_is_manual.md`, soft-deletes are operator-managed via Lint tab; `WHERE deleted_at IS NULL` filter in `publish-salem-pois.js:206` keeps them out of the asset DB). 484 reclassified `HISTORICAL_BUILDINGS` → `HISTORICAL_LANDMARKS` (curated buildings now 105). `historical_note` column dropped S216; content lifted to `description`.
- **Room DB:** `app-salem/src/main/assets/salem_content.db`, **v23** (S275), identity_hash `4db15f763c9dbd5a529d24b128cecada`. **v23 added `ghost_asset_a/b/ghost_frame` columns on `salem_pois` + denormalized into `collection_entry` for Katrina's Collection Phase 2.** Witch Trials intact (npc_bios 49 / articles 16 / newspapers 202). **PG end-state at S279 close: 3 tours (Density / Dr. K's / LongWalk) / 3 collections / 309 collection_entries** — Whole-Salem global pool deprecated (`default_salem_walking` + 447 entries deleted S279), TOUR_0001 (`tour_WD1` + 15 stops / 14 legs / 41-entry collection) deleted S279. All collections are direct per-tour walk-derived snapshots with operator-curated `auto_bake=false`. `tour_drks_001_walk_passport` is the default no-tour-active view (sort_order=0). **On-device asset DB matches PG as of 2026-05-18 S281 close** (publish chain (5/5) + debug AAB + bundletool install on Pixel 8 + Lenovo). Older counts (S278 close): 4 tours / 73 legs (S224) / 5 collections / 797 baked entries. **UserDataDatabase v3 → v4 (S274)** renames `passport_visit` → `poi_visit` via hand-written `MIGRATION_3_4` (strict, no destructive fallback per S180 lockdown; preserves paid-user history). **PG schema** rebranded S274: `salem_passport_filters` → `salem_collections`, `salem_passport_pois` → `salem_collection_entries`, FK column `filter_id` → `collection_id`. **v21 added 7 hot-DAO indices on SalemPoi (S271):** `is_narrated`, `is_tour_poi`, `is_civic_poi`, `is_historical_property`, `category`, `district`, `subcategory`. **v22 renamed `PoiPassport` → `CollectionEntry` (table `poi_passport` → `collection_entry`, columns `passport_id/_name` → `collection_id/_name`) for the Katrina's Collection rebrand.** v11-v22 column-history archived in session logs.
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
