# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — it should stay under 200 lines.

**Last updated:** 2026-04-10 — Session 115 (Android debug + tour polish — 18 fixes shipped, heading-up rotation smoothness not yet solved, root cause identified for S116)

---

## TOP PRIORITY — Next Session (S116)

**Finish the heading-up rotation smoothness work from S115.** All other S115 fixes shipped and validated. The one remaining problem: heading-up rotation feels unsmooth and latent when the user physically turns the tablet.

**Root cause identified in S115:** the Lenovo TB305FU's MTK HAL delivers `TYPE_ROTATION_VECTOR` at **~100 Hz** via `SENSOR_DELAY_UI` (not the documented 60ms / 16 Hz). My apply-loop runs on the main thread and does — per sample — sensor math, `GeomagneticField` lookup, static-mode detection, two DEBUG log writes (ring buffer + logcat + file sink), source selection, EMA blend, hysteresis check, and `setMapOrientation` + internal invalidate. **At 100 Hz on the main thread, it saturates the UI thread** and the HAL starts coalescing sensor samples, which drains the very data stream we need for smooth tracking.

**S116 plan (ordered, try in sequence):**

1. **Cut log chatter at the hot path.** Remove `ORIENT-RAW` DEBUG trace. Remove `HEADING-UP: skip hysteresis` DEBUG trace. Only log at INFO on real state transitions. This alone drops ~200 log writes per second from the main thread and may solve it.
2. **Rate limit the apply path to ~33ms (30 Hz).** Rate-limit ONLY the apply, not the tracker's receive path — static mode detection needs all 100 samples.
3. **Move sensor processing to a background `HandlerThread`.** `registerListener(handler)` can point at a background looper. Only the `setMapOrientation` hop goes back to the main thread.
4. **Switch static-mode detection from sample count to wall-clock.** 3 samples at 10ms = 30ms is way too fast. Use "no movement for 100-200ms" instead.
5. **Optional: request `SENSOR_DELAY_GAME` (20ms)** explicitly to get a known rate across devices.

**DO NOT remove in the next pass:** the SNAP, hybrid source, declination correction, configChanges fix, MotionTracker stationary freeze, dwell expansion, walk sim auto-dwell — all of those are correct. Only the apply-loop hot path needs rework.

After heading-up is smooth, return to the **POI taxonomy arc Step 2 of 4** (backfill + admin worklist) — still the TOP PRIORITY from a roadmap perspective. See `docs/poi-taxonomy-plan.md` for scope. Then S117: publish loop + Room migration + `PoiHeroResolver` rewrite.

**After the Android debug pass, the taxonomy arc resumes — Step 2 of 4 (backfill + admin worklist).** Concrete scope from `docs/poi-taxonomy-plan.md`:

1. **Normalization migration for `salem_narration_points.category`** — 814 rows, 29 lowercase legacy values (`shop` 119, `restaurant` 104, `services` 101, `attraction` 69, `venue` 62, `other` 60, etc.) need to be mapped to PoiLayerId form (`SHOPPING`, `FOOD_DRINK`, etc.). Straightforward mappings (`restaurant`/`cafe`/`bar`/`brewery` → `FOOD_DRINK`) are easy; the hard cases are the generic buckets (`services`, `venue`, `other`) which need per-row name/tag inspection. Dry-run to CSV first, then execute.
2. **Heuristic subcategory assignment** from POI name/tags/description → `subcategory` FK to `salem_poi_subcategories`. High-confidence assignments go through; ambiguous rows stay NULL and surface in the admin worklist.
3. **Same backfill for `salem_businesses`** — use `business_type` + `cuisine_type` + `name` + `tags` to seed `category` and `subcategory`.
4. **"Needs Subcategory" admin worklist** in `web/src/admin/AdminLayout.tsx` + `PoiTree.tsx`. Filter backed by `WHERE subcategory IS NULL`.
5. **Subcategory dropdown in `PoiEditDialog.tsx`** — reads from `salem_poi_subcategories` via new endpoint `GET /api/admin/salem/poi-subcategories?category_id=XXX`.
6. **Add the deferred FK constraint** on `salem_narration_points.category` once the normalization is complete. This is S115's acceptance checkpoint — if the FK fails to add, backfill is not done.

**Then S116: Phase 9P.C publish loop + Room schema migration + `PoiHeroResolver` rewrite.** See `docs/poi-taxonomy-plan.md` for full scope.

**S115 shipped (Android debug + tour polish — 18 fixes):**
- **GPS hygiene:** `PRUNE_INTERVAL_MS` 1h → 5min in `GpsTrackRecorder.kt`
- **Narration:** `isPoiAhead` near-zone escape hatch (≤15m bypasses behind-user filter); dwell expansion ladder (20→35→60→100m, cap 6) for "standing at Witch House" case
- **Heading-up FAB (`N↑`):** new button in `activity_main.xml`, pref-backed, with map rotation via `setMapOrientation`
- **Walk sim auto-dwell:** 15m trigger, 3s "face POI" pause (new `setMovementBearing` cross-file setter + `bearingToPoi` helper)
- **Lifecycle fix:** `android:configChanges` on Salem activity + `onConfigurationChanged` override. Survives rotation without destroying the activity. **Also kills the S110 lifecycle churn** that was on the carry-forward list for 6+ sessions.
- **Hybrid heading source:** new `DeviceOrientationTracker` class (~190 lines) listening to `TYPE_ROTATION_VECTOR` with display-rotation remap, GeomagneticField declination correction (true north), static-mode detection, accuracy toasts on LOW/UNRELIABLE
- **Stationary freeze:** new `MotionTracker` class (~180 lines) using `TYPE_SIGNIFICANT_MOTION` to freeze GPS jitter (polyline/rings/marker/bearing) when tablet is physically still; 25m escape hatch for real drift
- **Sensor inventory log:** runs once at `onCreate`, enumerates 8 heading-relevant sensors as `✓`/`✗` with vendor
- **Verbose logging:** `HEADING-UP`, `BEARING`, `NARR-DWELL`, `WALK-SIM`, `ORIENT-RAW`, `SENSORS`, `DEVICE-ORIENT`, `MOTION`, `STATIONARY` tags for post-mortem grep

**Lenovo TB305FU sensor inventory confirmed:** rotation vector (MTK, 9-axis fused), game rotation vector, geomagnetic rotation vector, accelerometer, step detector, step counter, significant motion — all present. Raw gyro and magnetometer not exposed as discrete sensors but HAL uses them internally (`gyro-rate=200Hz` in dumpsys).

**What's NOT yet working (S116 priority):**
- Heading-up rotation smoothness during physical tablet rotation. Root cause: 100 Hz sensor + main-thread saturation from log writes + per-sample apply. Plan documented in TOP PRIORITY section above.

**S114 shipped (foundation — still the roadmap priority after heading-up):**
- `salem_poi_categories` (22 rows) + `salem_poi_subcategories` (175 rows) PG lookup tables, seeded from `poiCategories.ts` via `cache-proxy/scripts/sync-poi-taxonomy.js` (idempotent)
- New nullable `category`/`subcategory` columns on `salem_businesses`
- 3 FK constraints: `fk_salem_narration_subcategory`, `fk_salem_business_category`, `fk_salem_business_subcategory`
- `docs/poi-taxonomy-plan.md` — multi-session plan doc with explicit scope decisions and S115/S116 handoff notes

**Scope decisions locked in S114:**
- `PoiCategories.kt` is THE canonical POI taxonomy; everything else must FK or mirror it.
- `salem_businesses` and `salem_narration_points` stay as separate tables — operator direction "two distinct things." They share the category/subcategory spine only.
- `salem_tour_pois` is OUT of the taxonomy arc entirely — its `category` column holds tour-chapter themes (`witch_trials`, `maritime`, `literary`, etc.), a different concept. Tour POIs stay on their own path.
- FK-to-lookup-tables over CHECK constraints — more flexible, also serves as the admin tool's subcategory dropdown source.
- Hero image regeneration **stays deferred behind SalemIntelligence.** The data plumbing arc proceeds independently.

**Admin tool hero override chain (S113 TOP PRIORITY) — DEMOTED.** The taxonomy arc supersedes it. Once S116 lands (publish loop + `PoiHeroResolver` rewrite), the "admin overrides hero per POI" work becomes a simple additional column on `salem_narration_points`/`salem_businesses` that the new resolver checks first. It is no longer a separate TOP PRIORITY item — it's downstream of the taxonomy arc.

**Phase 9P.B Step 9P.11 Highlight Duplicates** — still demoted, still the same concrete scope. 8 sessions carried.

---

## Phase Status

| Phase | Status | Notes |
|---|---|---|
| 1-9 + 9A+ + 9T (8/9) | COMPLETE | Core dev, offline foundation, ambient narration |
| **9P.A** Backend Foundation | **COMPLETE** (S98-S101) | Schema, importer, admin auth, write endpoints, duplicates, per-mode visibility |
| **9P.B** Admin UI | **6/8 done** | 9P.6 taxonomy, 9P.7 shell, 9P.8 tree, 9P.9 map, 9P.10 edit dialog, 9P.10b Salem Oracle. Pending: 9P.11 (demoted), 9P.13 publish wiring. 9P.10a blocked on 9Q. |
| **9P.B+** POI taxonomy alignment (1→4 arc) | **Step 1/4 COMPLETE (S114)** | S114: lookup tables + seed script + plan doc. S115: backfill + admin worklist. S116: publish loop + Room migration + PoiHeroResolver rewrite. Hero regen still deferred behind SalemIntelligence. |
| **9P.C** Publish loop | not started | Folds into 9P.B+ Step 3 (S116). |
| **9Q** Salem Domain Content Bridge | not started | building→POI translation, 425 buildings, 202 newspapers |
| **9R** Historic Tour Mode | not started | opt-in chapter-based 1692 tour |
| **10** Production readiness | DEFERRED behind 9P+9Q+9R | Firebase, photos, DB hardening, emulator verification |
| **11** Branding, ASO, Play Store | target 2026-09-01 | Salem 400+ launch window |
| **Cross-project** SalemIntelligence | not started (operator building) | New sibling project at `~/Development/SalemIntelligence` — knowledgebase + LLM for per-business descriptions and hero SD prompts. Blocks the hero asset regen pass until it ships. |

**Sessions completed:** 114 (last entries in `SESSION-LOG.md`). Salem 400+ quadricentennial is 2026 — app must be in Play Store by Sept to capture October's 1M+ visitors.

---

## Carry-forward Items (NOT blocking the taxonomy arc)

**Still pending (carry into S116+):**
- **Heading-up rotation smoothness** — root cause identified in S115 (100 Hz sensor + main-thread saturation). Plan: cut log chatter, rate-limit apply, move sensor processing to background HandlerThread, switch static detection to wall-clock. See TOP PRIORITY.
- **`DWELL_MS = 3_000L` dead code** in `NarrationGeofenceManager` — declared, never wired. Single jittery fixes can fire false-positive narration in dense areas. ~10 lines to fix with a per-POI fix-count gate.
- **GPS journey line backgrounding bug** — `viewModel.currentLocation.observe(this)` is bound to the activity's STARTED lifecycle, so the recorder stops receiving fixes when the activity goes to onPause/onStop (screen off, app backgrounded). Diagnosed in S112; fix options proposed: (a) singleton recorder rewire ~30 lines, (b) foreground service ~150 lines. Not chosen, not implemented.
- ~~**18:09-18:15 lifecycle churn** (S110 known issue)~~ — **FIXED in S115 via `android:configChanges`** on the Salem activity. Rotation no longer destroys the activity, and with it the mysterious 10-recreates-in-6-min churn should stop.
- **DB wipe across APK reinstalls** — `fallbackToDestructiveMigration` on `UserDataDatabase` wipes user_data.db on schema upgrade. Pre-existing S110 design choice; should be replaced with real Room migrations before Play Store.
- **walkSimMode flag still has gap-timing differences** — `gapMs = if (walkSimRunning) 500L else 2000L` and `silenceMs = if (walkSimRunning) 500L else 5000L` in `SalemMainActivityNarration.kt`. S112 removed the 3× radius inflation but left these gap timings alone. Revisit if walk-sim should be 100% indistinguishable from real GPS.
- **Bearing filter cone is 90°** (front half-circle) — operator may want to tighten to 60° or 45° if "ahead" is too loose. `AHEAD_CONE_HALF_ANGLE_DEG` constant in `SalemMainActivityNarration.kt`.
- **Admin tool POI creation** — operator noted in S112 that to mark merchants as paid customers (`adPriority > 0`) the admin tool needs a way to create new POIs. Currently it only edits existing ones. Phase 9P.B follow-on.
- **POI encounter review screen** — current review path is `adb pull` + `sqlite3` or grep the persistent log. A future session can add an in-app review screen, probably in the operator's debug menu.
- **Pre-existing GPS log redundancy** — every fix logs 4 lines from 4 layers (`LocationManager → ViewModel → GPS-OBS → SalemMainActivity`). Pre-existing duplication, not blocking.
- **Narration sheet has no `setPeekHeight`** — the deeper architectural reason buttons get covered. The S110 168dp marginBottom workaround handles common case but not a fully expanded sheet.

**Field-test observation (S110, no code action required):** cache-proxy at `10.0.0.229:4300` was unreachable from the tablet during the field test. The new circuit breaker handles this gracefully (fast-fails, single-line WARN log per failure, no app crash). Run `bash bin/restart-proxy.sh` before the next field test if POI prefetch matters.

---

## OMEN Open Items

1. **NOTE-L014 / OMEN-008 — Privacy Policy** — **DRAFTED S114 at `docs/PRIVACY-POLICY.md`**. Pending OMEN review before Salem radio-environment ingest goes live. No longer blocking from LocationMapApp's side — the ball is in OMEN's court.
2. **NOTE-L015 — `~/Development/SalemCommercial/` cutover never executed** on this workstation. Surfaced again in S115. **Ten sessions running.** LocationMapApp has no unilateral remediation path that would not break valid references — needs OMEN to execute or retract.
3. **NOTE-L013 — debug `VoiceAuditionActivity.kt` cleanup** — **CLEARED S114**. Untracked file deleted, empty parent dir removed. `VoiceTestActivity.kt` stays — it is actively wired to the Salem debug FAB.
4. **OMEN-002 history rotation** — operator action only: `ALTER USER witchdoctor WITH PASSWORD '<new>';` + regenerate `OPENSKY_CLIENT_SECRET` via portal + replace test `ADMIN_PASS=salem-tour-9P3-test` in `cache-proxy/.env` with a real strong value before relying on admin auth in any non-test context.
5. **OMEN-004 — first real Kotlin unit test** (Phase 1 deadline 2026-04-30, 20 days out).
6. **Phase 9T.9 walk simulator end-to-end verification** still TODO.
7. **Cross-project dependency: SalemIntelligence** — new sibling project at `~/Development/SalemIntelligence` (operator building). Knowledgebase + LLM that generates per-business descriptions and custom SD prompts for POI and hero imagery. Blocks the LocationMapApp hero asset regen pass until it ships. Flag for OMEN's Shared Engine Registry.

---

## Salem Oracle Integration (Phase 9P.10b reference)

The Salem sibling project exposes a dev-side LLM-backed API at `http://localhost:8088` that the admin tool calls to compose, revise, summarize, or expand POI descriptions. Reference: `~/Development/Salem/docs/oracle-api.md` (492 lines, owned by Salem).

- **Key endpoints:** `/api/oracle/status` (health), `/api/oracle/ask` (main composition with `current_poi_id` context pinning, 5-15s latency), `/api/oracle/pois`, `/api/oracle/poi?id=`, `/api/oracle/newspapers`. Salem corpus is read-only from this API.
- **Backed by:** `gemma3:27b` on the operator's RTX 3090.
- **Critical conceptual point:** Salem's 63 historical POIs (`salem_poi_*` IDs) are DISTINCT from LocationMapApp's 1,720 POIs — same geography, different datasets. The Oracle's `current_poi_id` field accepts any string for context pinning; pass the LocationMapApp POI id directly.

---

## Three-phase expanded vision (recap from S97 planning)

- **Phase 9P** — POI Admin Tool. ~4-5 sessions. Operator-facing CMS for POI metadata. Active.
- **Phase 9Q** — Salem Domain Content Bridge. ~2-3 sessions. Builds the `building_id → poi_id` translation layer. Imports 425 buildings + the new 202-article newspaper corpus (Salem session 044, ~13.5 hours TTS-ready content via `tts_full_text` field). Backfills historical FKs by graph traversal.
- **Phase 9R** — Historic Tour Mode (App-Side). ~4-6 sessions. Opt-in tour mode that suppresses ambient POI narration, plays curated 1692 historical content as user walks. Hybrid model: user picks a chapter, chapter is a curated route through 4-8 POIs in chronological story order. 7 chapters drafted: Strange Behavior in the Parris Household → First Accusations → Examinations → Court of Oyer and Terminer → Bridget Bishop & First Execution → Summer of Death → Aftermath.

**Total runway for full vision:** 9-12 sessions across 9P + 9Q + 9R. Salem 400+ launch deadline is September 1, 2026 — ship 9P first, then assess 9Q+9R against runway.

**Cross-project dependency (Salem):** Phase 9Q consumes the Salem Oracle newspaper corpus (NEW in Salem session 044). Schema at `~/Development/Salem/data/json/newspapers/README.md`. Each article has `events_referenced` JSONB field — the load-bearing join key for the bridge chain `newspaper → event → building → POI`.

---

## POI Inventory

- **PG canonical:** 45 tour POIs + 861 businesses + 814 narration points = **1,720 active POIs** (S101 migration). Bundled `salem-content/salem_content.db` is now a downstream artifact and will need Phase 9P.C publish loop to regenerate.
- **Inventory PDF tool:** `tools/generate-poi-inventory-pdf.py` (Python + reportlab). Output: `docs/poi-inventory-2026-04-08.pdf` (gitignored, 1,163 pages, 2.5 MB). Re-run: `tools/.poi-pdf-venv/bin/python tools/generate-poi-inventory-pdf.py`.

---

## Pointers to detail

| What | Where |
|---|---|
| Recent session summaries (S103-S112, rolling window) | `SESSION-LOG.md` |
| Older session summaries (S001-S102, plus pre-v1.5.51) | `SESSION-LOG-ARCHIVE.md` |
| Live conversation logs (per-session) | `docs/session-logs/session-NNN-YYYY-MM-DD.md` |
| Full build plan | `WickedSalemWitchCityTour_MASTER_PLAN.md` |
| Architecture, tech stack, file inventory | `CLAUDE.md` and the codebase itself |
| Legal/compliance | `GOVERNANCE.md` |
| Patentable innovations | `IP.md` |
| Monetization strategy | `COMMERCIALIZATION.md` |
| UX/architecture decisions | `DESIGN-REVIEW.md` |
| Test checklist | `CURRENT_TESTING.md` |
| Older STATE.md content (Architecture / What's Working / Build Env / etc.) | `docs/archive/STATE_removed_2026-04-09.md` |
