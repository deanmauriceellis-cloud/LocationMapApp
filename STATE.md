# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — must stay under 200 lines.

**Last updated:** 2026-05-17 — **Session 275.** Katrina's Collection **Phase 2 + Phase 3 SHIPPED end-to-end** in a single session: 107 paired ghost portraits (A normal + B fourth-wall-break smirk, 2% runtime swap), 8 frame overlay PNGs, Room v23, augmented CollectionSheet with new "Badges" tab + witch-hat row stamps replaced by ghost-portrait tiles. **Asset pipeline:** paper-napkin attribute table (`cache-proxy/data/ghost-personas-seed.json`, 7 columns mirrored to operator's `~/Documents/MiscAttributes.ods`) → 4 generator scripts → 4 SD passes (Pass 1 txt2img+txt2img drifted identity; Pass 2 full img2img drifted backgrounds; **Pass 3 face-only inpaint** with elliptical mask + dedicated face-focused B prompt + `({expression}:1.5)` weighting + denoise 0.75 landed). DreamShaperXL Turbo, 768×768, 214 portraits in 669s + 8 frame overlays via chroma-key + per-emblem composite. Packed to ~4.6 MB total app payload (`pack-ghost-portraits.py` 768 PNG → 384 webp q85 alpha-aware). **PG + Room schema:** added `ghost_asset_a/b/ghost_frame TEXT` columns on `salem_pois` (idempotent ALTER) and denormalized into `collection_entry`; Room **v22 → v23**, identity hash `4db15f763c9dbd5a529d24b128cecada`. `populate-ghost-personas.py` UPDATEs 107 HIST_BLDG rows from manifest; frame_slug derived via SHA1(slug) mod 8 (distribution 9–18 per frame). Publish chain ordering quirk noted: `publish-poi-collection.js` must run AFTER `align-asset-schema-to-room.js` on first-run schema bumps (re-run after align is the bootstrap workaround). Resulted in 797 collection_entry rows, 234 with ghost data (107 ghosts × ~2.18 collection appearances each). **Runtime:** new `GhostResolver.kt` with LRU cache + 2% B-swap roll + graceful-null missing-asset handling. **UI iteration arc:** first try replaced the list view with grid — operator caught: *"this should augment what we had, not overwrite it"* — reverted via `git checkout` and added tabs (List | Badges) with sliding amber indicator + two stacked RecyclerViews in ConstraintLayout. One layout collapse bug along the way (`layout_constraintDimensionRatio="1:1"` doesn't work under LinearLayout parent; rewrote tile XML as ConstraintLayout root). Blank-tile filter: badge grid HIST_BLDG-only (39 of 72 in Dr. K's Tour). **Operator UI iteration:** witch-hat row stamp → 64dp ghost-portrait tile (same rules as badges), uncollected dimmed to 50% alpha on top of greyscale, **frame only appears when caught** (the catch reward). 5 build/install cycles in the closing UI loop; both Pixel 8 + Lenovo confirmed `SalemContentDatabase opened: schema_v=23 tables=13 (...collection_entry)` with zero destructive migrations. New `~/AI-Studio/ghost-batch-v1/inspector.html` self-contained HTML inspector with A/B + color/greyscale + frame on/off toggles for visual QA. New memory `project_katrinas_collection_ghost_asset_pairing.md` captures full pairing spec + asset locations + frame model + view-state rules + category scope (HIST_BLDG V1; commercial categories stubbed for post-V1). **Operator close:** *"we have done wonderful in this. Commit it and make sure all the documents reflect this."* SI2 (~1 week) will redo all personas with deterministic per-building seeding; the pairing convention + filename pattern + 2% swap rule survive that re-bake. Phase 4 (operator field smoke) carry-forward: walk a Salem POI to verify greyscale → color transition + frame appearance after first narration trigger, confirm 2% B-swap visibly fires in the wild. 73 days to 2026-08-01 ship; **3 days to Form TX deadline 2026-05-20**. Full detail in `docs/session-logs/session-275-2026-05-16.md`.

### Prior — S274 (2026-05-16)

"POI Passport" rebranded → **"Katrina's Collection"**. Phase 1 mechanical rename across ~50 files: PG tables, cache-proxy lib + scripts, Room content DB **v21 → v22** (`PoiPassport` → `CollectionEntry`; new identity hash `07f63ead9c5c3c05d87dcb6d54f64ba8`), Room user DB **v3 → v4** with `MIGRATION_3_4` preserving paid-user history, Android UI + web admin + resources. 107 HIST_BLDG POIs confirmed ghost-eligible. Full detail in `docs/session-logs/session-274-2026-05-16.md`.

### Prior — S273 (2026-05-16)

3-bug TTS chain fixed + field-verified on Lenovo + Pixel 8: master-mute walk-sim stall (`emitSyntheticComplete` Handler-posted Idle pulse), business gate (`category=null` plumbed through 7 POI call sites), Show-All override scope fix (visibility-only, audio toggles always honored). Full detail in `docs/session-logs/session-273-2026-05-16.md`.

### V1 ship-cliffs (all closed)

Cliff 1 (Play 200 MB ceiling) closed S256 via install-time Asset Pack; Cliffs 2 (`largeHeap` + 60° tilt OOM) + 3 (`allowBackup=true`) closed S255. Build/install workflow with asset packs lives in `CLAUDE.md` Key Paths block + `feedback_adb_install_after_db_rebake.md`.

---

## TOP PRIORITY — V1 launch triage (operator-confirmed 2026-04-29; revised S203 close 2026-04-30)

> **Internal ship target: 2026-08-01** (77 days as of 2026-05-16). One month tighter than the prior Sept 1 anchor. Salem 400+ peak attendance is October 2026.

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

S245+ aged backlog (Rendering / Content / Tools — 26-55 sessions stale by S271) moved to `docs/archive/STATE_carryforwards_removed_2026-05-16.md`.


- Operator field-validation: S221 detour / S220 subtopic content / S217 BusinessLabel + FAB override; spurious `is_tour_poi=true` lint cleanup; borderline-historical commercial opt-in audit.
- Tier 3 disk reclaim ~4.4 GB (poi-icons / hero-triptych / tile-bake / l3-essex / unused-style archives) + `.gitignore` audit (overnight-runs, poi-cache.json, cache-data.json, web/dist, tsconfig.tsbuildinfo, docs/bake-tests).
- S216 follow-ups: John Ward House tour-leg fix, HISTORICAL_LANDMARKS icon+hero regen, description rebuild post-historical_note merge, dequeue 40m staleness vs `geofence_radius_m`.
- S215 suspicious-leg graph: Dr. K 13/14, WD1 3/6/10/11.
- PoiDetailSheet/Find cleanup (S224): clearXXX/stopXXX no-ops.
- S206: speed-aware queue cap (>15 mph), DensityTour 0-stop fallback decision; Operator-driven Salem cross-repo commit (`genbiographies/main.go` + 3 hand-edited bios).

Carry-forwards 70+ sessions stale (Rebuild-AAB-since-S180, GPS-OBS heartbeat, re-author 5 deleted Kotlin tours, McIntire content drain) moved to `docs/archive/STATE_carryforwards_removed_2026-05-16.md` (S271).

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
- **2026-05-17 (S275):** Katrina's Collection Phase 2 + Phase 3 SHIPPED — 107 paired ghost portraits (A normal + B fourth-wall-break smirk, 2% runtime swap), 8 frame overlay PNGs, Room **v22 → v23** (identity `4db15f763c9dbd5a529d24b128cecada`). New `ghost_asset_a/b/ghost_frame` columns on `salem_pois` + denormalized into `collection_entry`. New `GhostResolver.kt` runtime, `CollectionSheet` augmented with tabs (List | Badges) + witch-hat row stamps replaced by ghost-portrait tiles. ~4.6 MB asset payload (4.2 MB portraits + 0.36 MB frames). New memory: `project_katrinas_collection_ghost_asset_pairing.md`. 5 generator scripts (`generate-ghost-portraits.py`, `generate-frame-overlays.py`, `pack-ghost-portraits.py`, `populate-ghost-personas.py`, `build-ghost-gallery.py`).
- **2026-05-16 (S270):** Recon photo triage tool Layer 1 (bulk-cull) shipped on top of S229/S231 burst-photos infra. New `web/src/admin/ReconTriageTab.tsx` admin tab; `cache-proxy/lib/admin-burst-photos.js` extended with persistent `.kept.json` per-session keep-flag, atomic `commit-cull` (move-everything-unkept to `.deleted/`), `adb devices` lister, and SSE-streaming `adb pull` + python-organizer endpoint. Zero new dependencies. End-to-end smoke verified.
- **2026-05-15 (S269):** POI Passport architecture matured to **pool ∩ proximity** model. Global passport (`default_salem_walking`) defines the "pool" via filter SQL (categories + flags + min broadcast radius + year range); per-tour passports are walk-derived snapshots of `pool ∩ polyline-proximity`, edited via the new Tour-editor walk dialog. Recompute is destructive (operator-confirmed: "wipes everything; manual edits persist until next wipe"). Tour metadata is now fully derived: `recomputeTourMetadata` rebuilds `stop_count + distance_km + estimated_minutes` from `salem_tour_legs + salem_tour_stops` on every mutation + on PATCH (operator-supplied values get overwritten). New "stamps" UX: Android tour cards + web admin tour list both show passport-bound POI count instead of legacy `stop_count` (free polyline waypoints). New `auto_bake` column on `salem_passport_filters` separates filter-driven (auto-bake, global pool only) from operator-curated (manual, per-tour) rows. Five Lenovo bundletool installs in one session.
- **2026-05-04 (S224):** Session-start protocol amended lean → lean+state — `STATE.md` + most-recent live log mandatory. `enqueueNarration` cancel-stamp scoped to direct-play (fixed walk-sim queue stall from S146 Idle-guard collision).
- **Older pivots (S157-S185, 2026-04-23 → 04-26):** V1 manifest stripped of network permissions; tours polyline-only with PG source of truth; asset schema Room-canonical via `align-asset-schema-to-room.js`; MassGIS L3 / Witchy basemap / WickedMap prototype / on-device TIGER router / wharf-walkway osm_id allowlist. Detail in `SESSION-LOG-ARCHIVE.md`.

---

## Phase Status (V1 lens)

Phases 1-9 + 9A+ + 9P.A/B + 9T + 9U + 9X: **COMPLETE**. Phase 10 (production readiness): first signed AAB built S180, asset-pack reorg S256, ship-cliffs 1/2/3 closed. Phase 11 (ASO/Play Store): operator-led, post-AAB-upload. **9Y/9Z/9Q/9R deferred** (V1.0.1+, no V1 ship dependency). Cross-project TigerLine stalled 2026-04-21; SalemIntelligence PAUSED S214.

**Sessions completed:** 275. **Internal ship target: 2026-08-01.**

---

## POI Inventory (refreshed S180 from live PG)

- **PG `salem_pois`:** 2,039 live (S271 refresh — net +2 from S270's 2,037). 87 soft-deleted (per `feedback_dedup_is_manual.md`, soft-deletes are operator-managed via Lint tab; `WHERE deleted_at IS NULL` filter in `publish-salem-pois.js:206` keeps them out of the asset DB). 484 reclassified `HISTORICAL_BUILDINGS` → `HISTORICAL_LANDMARKS` (curated buildings now 105). `historical_note` column dropped S216; content lifted to `description`.
- **Room DB:** `app-salem/src/main/assets/salem_content.db`, **v23** (S275), identity_hash `4db15f763c9dbd5a529d24b128cecada`. **v23 added `ghost_asset_a/b/ghost_frame` columns on `salem_pois` + denormalized into `collection_entry` for Katrina's Collection Phase 2.** Witch Trials intact (npc_bios 49 / articles 16 / newspapers 202). 4 tours / 73 legs (S224). **5 collections / 797 baked entries (S269; rebranded S274 from "5 passports")** — 1 global Whole-Salem pool (447 POIs filtered via categories + historical_narration + min_geofence_radius_m=10) + 4 walk-derived per-tour collections (49 / 72 / 188 / 41) authored as `pool ∩ proximity`. **UserDataDatabase v3 → v4 (S274)** renames `passport_visit` → `poi_visit` via hand-written `MIGRATION_3_4` (strict, no destructive fallback per S180 lockdown; preserves paid-user history). **PG schema** rebranded S274: `salem_passport_filters` → `salem_collections`, `salem_passport_pois` → `salem_collection_entries`, FK column `filter_id` → `collection_id`. **v21 added 7 hot-DAO indices on SalemPoi (S271):** `is_narrated`, `is_tour_poi`, `is_civic_poi`, `is_historical_property`, `category`, `district`, `subcategory`. **v22 renamed `PoiPassport` → `CollectionEntry` (table `poi_passport` → `collection_entry`, columns `passport_id/_name` → `collection_id/_name`) for the Katrina's Collection rebrand.** v11-v22 column-history archived in session logs.
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
