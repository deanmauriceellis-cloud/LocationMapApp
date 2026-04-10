# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — it should stay under 200 lines.

**Last updated:** 2026-04-09 — Session 113 (POI Detail Sheet — full-screen dialog with hero + descriptions + tagged TTS read-through; hero regen deferred pending new SalemIntelligence sibling project)

---

## TOP PRIORITY — Next Session (S114)

**Admin tool hero image override chain.** Operator said in S113 Q2: next session priority, above the old 9P.11/9Q/9R plan. Concrete scope:

1. **PG column:** add `hero_image_asset TEXT` to `salem_narration_points`, `salem_tour_pois`, and `salem_businesses`. Default NULL.
2. **Admin tool UI:** file-upload + preview + assign widget in `web/src/admin/PoiEditDialog.tsx`. Two layers — merchant paid asset (top priority) and admin-assigned asset (fallback to hash-pinned pool, then to red placeholder).
3. **Publish loop (mini):** a dedicated sync path that copies admin-assigned hero assets from PG-referenced storage into `app-salem/src/main/assets/poi-icons/_heroes/{poi_id}.png` at build time (or pre-build script). Room DB stays on the hash-pinned fallback until Phase 9P.C lands the full publish pipeline.
4. **Android side:** `PoiHeroResolver.resolve()` now checks the new `heroImageAsset` column FIRST, then falls back to the existing category-folder hash-pinned pick, then to red. (Currently only the latter two.)
5. **Red worklist query:** admin tool gets a "POIs missing hero" filter backed by the new column so operator can walk the list and assign.

**SalemIntelligence dependency:** the S113 plan to regenerate the 1,416-image character pool with a refined SD prompt is DEFERRED. Operator is building `~/Development/SalemIntelligence` as a separate product — a knowledgebase + LLM that generates per-business descriptions AND hero prompts. The regen pass is blocked until SalemIntelligence ships (~1 day of operator work). Until then, the hash-pinned pool + red placeholder is the correct intermediate state.

**After admin hero chain:** background research task to fill missing POI fields (phone/website/hours/booking URL). Operator S113: "run a background task locally to research all the new (and old POI's) that don't have advanced information."

**Phase 9P.B Step 9P.11 Highlight Duplicates** — still stated but DEMOTED per S113 operator direction "refinement loop supersedes." Carried S107 → S108 → S109 → S110 → S111 → S112 → S113 (7 sessions). Concrete scope still the same: wire `web/src/admin/AdminLayout.tsx:69-73` stub to `GET /api/admin/salem/pois/duplicates?radius=15`, red rings on AdminMap, click → side panel.

**Skip 9P.10a** (Linked Historical Content tab) until Phase 9Q has run.

---

## Phase Status

| Phase | Status | Notes |
|---|---|---|
| 1-9 + 9A+ + 9T (8/9) | COMPLETE | Core dev, offline foundation, ambient narration |
| **9P.A** Backend Foundation | **COMPLETE** (S98-S101) | Schema, importer, admin auth, write endpoints, duplicates, per-mode visibility |
| **9P.B** Admin UI | **6/8 done** | 9P.6 taxonomy, 9P.7 shell, 9P.8 tree, 9P.9 map, 9P.10 edit dialog, 9P.10b Salem Oracle. Pending: 9P.11 (demoted), 9P.13 publish wiring. 9P.10a blocked on 9Q. |
| **9P.B+** Hero override chain | **NEXT (S114)** | PG `hero_image_asset` column, admin upload/assign UI, mini publish loop for hero assets, PoiHeroResolver priority check. Supersedes 9P.11 per S113 operator direction. |
| **9P.C** Publish loop | not started | 9P.12-9P.14 |
| **9Q** Salem Domain Content Bridge | not started | building→POI translation, 425 buildings, 202 newspapers |
| **9R** Historic Tour Mode | not started | opt-in chapter-based 1692 tour |
| **10** Production readiness | DEFERRED behind 9P+9Q+9R | Firebase, photos, DB hardening, emulator verification |
| **11** Branding, ASO, Play Store | target 2026-09-01 | Salem 400+ launch window |
| **Cross-project** SalemIntelligence | not started (operator building) | New sibling project at `~/Development/SalemIntelligence` — knowledgebase + LLM for per-business descriptions and hero SD prompts. Blocks the hero asset regen pass until it ships. |

**Sessions completed:** 113 (last entries in `SESSION-LOG.md`). Salem 400+ quadricentennial is 2026 — app must be in Play Store by Sept to capture October's 1M+ visitors.

---

## Carry-forward Items (NOT blocking the hero override chain)

**S113 POI Detail Sheet recap:** S113 shipped a full-screen `DialogFragment` that opens on dock tap, map marker tap, OR narration banner tap. Hero strip (20% of screen, `fitXY` stretch, deterministic hash-pinned from `poi-icons/{category}/` pool or red `ASSIGN HERO` placeholder), overview row, Visit Website through existing in-app WebView, short/about/story sections from `shortNarration`/`description`/`longNarration`, synthesized action list (Call/Directions/Hours — not narrated). Tagged TTS read-through via new `speakTaggedHint` + `cancelSegmentsWithTag` on NarrationManager — reads only `name+type+address+phone+website-ack` + descriptions, drops stale segments at open AND close via prefix match, doesn't touch ambient queue. Three iterations in one session: initial Bottom sheet build → full-screen conversion + TTS trim → S112 highlight-ring tap-interception fix (polygons moved to overlay index 0 with null titles and non-consuming click listeners) + narration banner click wiring + verbose click logging at every entry point. 1 commit: `2826145`.

**Still pending (carry into S114+):**
- **`DWELL_MS = 3_000L` dead code** in `NarrationGeofenceManager` — declared, never wired. Single jittery fixes can fire false-positive narration in dense areas. ~10 lines to fix with a per-POI fix-count gate.
- **GPS journey line backgrounding bug** — `viewModel.currentLocation.observe(this)` is bound to the activity's STARTED lifecycle, so the recorder stops receiving fixes when the activity goes to onPause/onStop (screen off, app backgrounded). Diagnosed in S112; fix options proposed: (a) singleton recorder rewire ~30 lines, (b) foreground service ~150 lines. Not chosen, not implemented.
- **18:09-18:15 lifecycle churn** (S110 known issue) — 10 activity recreates with `changingConfig=true` in 6 minutes. Trigger still unknown.
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

1. **NOTE-L014 / OMEN-008 — Privacy Policy drafting** (BLOCKING RadioIntelligence Salem ingest). Async writing work; can be drafted between admin-tool sessions.
2. **NOTE-L015 — `~/Development/SalemCommercial/` cutover never executed** on this workstation. Surface in every session report until OMEN executes or retracts. **Seven sessions running.**
3. **NOTE-L013 — debug `VoiceAuditionActivity.kt` cleanup** still pending (low priority).
4. **OMEN-002 history rotation** — operator action only: `ALTER USER witchdoctor WITH PASSWORD '<new>';` + regenerate `OPENSKY_CLIENT_SECRET` via portal + replace test `ADMIN_PASS=salem-tour-9P3-test` in `cache-proxy/.env` with a real strong value before relying on admin auth in any non-test context.
5. **OMEN-004 — first real Kotlin unit test** (Phase 1 deadline 2026-04-30, ~3 weeks).
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
