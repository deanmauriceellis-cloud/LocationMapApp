# LocationMapApp — Session Log

> **Rolling window — last 10 sessions only.** On every session end, the oldest session is moved to `SESSION-LOG-ARCHIVE.md`. This file currently holds Sessions 111-120. Everything older lives in the archive (which itself ends with the original v1.5.0–v1.5.50 archive at the bottom).
>
> **Per-session live conversation logs** (the canonical, append-only record with full reasoning, decisions, file diffs, build results) live in `docs/session-logs/session-NNN-YYYY-MM-DD.md`. The entries in this file are 2-3 sentence summaries — pointers to the live logs, not replacements.

## Session 120: 2026-04-13 — Phase 9U consumer migration (NarrationPoint→SalemPoi), admin tree rework, parking lot triage

Consumer migration across 13 Android files — entire narration pipeline now queries unified `salem_pois` table via SalemPoi entity. PoiHeroResolver upgraded to use S119 hero images. Admin PoiTree gets 3-level tree (category→subcategory→POI), "Visible only" filter, human-readable labels. AdminMap renders hidden POIs at 35% opacity. Default visibility fixed for 210 POIs. 29 S119 parking lot items triaged into master plan backlog.

Full session detail: `docs/session-logs/session-120-2026-04-13.md`.

---

## Session 119: 2026-04-12 — Hero image generation pipeline — 1,295 cartoon Halloween hero images

Built complete hero image generation pipeline: SalemIntelligence export → Forge API batch generation (DreamShaper 8) → live QC web viewer → DB population. 1,295 images generated, 1,013 POIs updated with image_asset paths, bundled DB republished. 29 parking lot items captured covering app modes, UX overhaul, search, onboarding, and hero image refinement roadmap.

Full session detail: `docs/session-logs/session-119-2026-04-12.md`. Commit: `67874e8`.

---

## Session 118: 2026-04-12 — Phase 9U BCS import (976→2190 POIs), SalemPoi Room entity, walk sim overhaul

Imported 1,766 BCS POIs from SalemIntelligence — 404 existing enriched, 1,214 new inserted, total 2,190. Built PG→SQLite publish pipeline, new unified SalemPoi Room entity + DAO (version 5). Walk sim fixes: 3-then-30s narration pacing, dwell rate-limiting, direct-play bypass, skip button, icon refresh. Tour-aware walk sim: Walk button follows selected tour route.

Full session detail: `docs/session-logs/session-118-2026-04-12.md`. Commits: `5213be7`, `a4ac369`.

---

## Session 117: 2026-04-12 — Phase 9U Session 117: unified salem_pois table — three-table merge complete

Merged `salem_narration_points` (817), `salem_businesses` (861), and `salem_tour_pois` (45) into a single `salem_pois` table (71 columns, 976 rows, 14 categories). Steps 9U.1-9U.6 shipped: unified DDL, migration script, FK repointing, admin backend + frontend rewritten for single-table queries, old tables renamed to `_legacy`. `PoiKind` type removed entirely from frontend. Zero TypeScript errors.

Full session detail: `docs/session-logs/session-117-2026-04-12.md`.

---

## Session 116: 2026-04-12 — Phase 9U planning: Unified POI table + SalemIntelligence import

Planning session — no code changes. Three strategic decisions: (1) V1 commercial release is fully offline, no hosted LLM/KB, SalemIntelligence is build-time data source only. (2) All 19 BCS categories map to existing 22 LMA categories with no new top-level types (4 new subcategories). (3) Merge `salem_narration_points` (817), `salem_businesses` (861), and `salem_tour_pois` (45) into a single `salem_pois` table — analysis confirmed 817/817 narration points match businesses by name+coordinates, they're the same entities stored twice. Added Phase 9U to the master plan (4 sessions S117-S120, 17 steps) covering schema migration, BCS import (~900 new entities → ~2,600 total), admin tool adaptation, publish loop, and Room migration. Heading-up rotation smoothness deferred to S120.

Full session detail: `docs/session-logs/session-116-2026-04-12.md`.

---

## Session 115: 2026-04-10 — Android debug + tour polish: 18 fixes, heading-up smoothness deferred

Android debug pass on the Lenovo TB305FU surfaced a cascade of related issues and shipped 18 fixes across 7 modified files + 2 new classes (`DeviceOrientationTracker`, `MotionTracker`). Highlights: GPS prune 1h→5min; near-zone escape hatch for POIs inside the behind-user filter; dwell expansion ladder (20→35→60→100m, cap 6) for stopping at the Witch House; new heading-up `N↑` FAB with hybrid heading source (GPS bearing → rotation vector sensor → stale GPS); walk sim auto-dwell at each POI (15m trigger, 3s "look at POI" pause via new `setMovementBearing` setter); `android:configChanges` on the Salem activity to survive rotation without destroying the walk sim coroutine — which **also kills the S110 lifecycle churn** that was on the carry-forward list for 6+ sessions; magnetic declination correction via `GeomagneticField` for true-north rotation; stationary GPS freeze via `TYPE_SIGNIFICANT_MOTION`; adaptive smoothing with static-mode detection and SNAP-on-rotation-complete; compass accuracy toasts. Heading-up rotation smoothness is not yet solved — root cause identified as 100 Hz sensor delivery + main-thread saturation from per-sample log writes, plan documented for S116.

Full session detail (sensor inventory findings via `dumpsys`, 5 test/iteration cycles, three separate walk sim behavior iterations, three separate rotation-responsiveness iterations, and the final discovery that the Lenovo fires sensor updates at 100 Hz via SENSOR_DELAY_UI): `docs/session-logs/session-115-2026-04-10.md`.

---

## Session 114: 2026-04-10 — OMEN NOTEs cleared + POI taxonomy foundation (Step 1 of 4-step arc)

Cleared the outstanding OMEN NOTE backlog (L013 VoiceAuditionActivity deleted, L014 Privacy Policy drafted for OMEN-008 Salem stream pending review, L015 confirmed no-op for the 9th consecutive session) and then pivoted mid-session from a planned hero-image regen into an architecture conversation that surfaced seven parallel POI taxonomies across the codebase. Shipped Step 1 of a new 1→4 taxonomy-alignment arc: two PG lookup tables (`salem_poi_categories` 22 rows, `salem_poi_subcategories` 175 rows) seeded from `web/src/config/poiCategories.ts` via a new idempotent Node script, new nullable `category`/`subcategory` columns on `salem_businesses`, three FK constraints enforced (narration_points.subcategory, businesses.category, businesses.subcategory), and a multi-session plan doc (`docs/poi-taxonomy-plan.md`) with explicit S115/S116 handoff notes and hard scope decisions (`salem_tour_pois` excluded — tour-chapter themes, different concept; no table merge per operator direction).

Full session detail (architecture conversation, seven-taxonomy mapping, two bugs caught and fixed during seed-script testing, decisions locked in, deferred items, hero regen still blocked on SalemIntelligence): `docs/session-logs/session-114-2026-04-10.md`. Commit: `e32a4b7`.

---

## Session 113: 2026-04-09 — POI Detail Sheet: full-screen dialog with hero + descriptions + TTS read-through

Built a new POI detail window that opens on tap of a dock icon, map marker, or narration banner — hero image strip (20% of screen, fitXY stretch from hash-pinned `poi-icons/` pool or red "ASSIGN HERO" placeholder), overview row, Visit Website through in-app WebView, short/about/story narrative sections, and render-time-synthesized action buttons. Three iterations in one session: initial BottomSheetDialogFragment build, full-screen DialogFragment conversion + TTS trim (drops action button narration, reads only name/address/phone/website-ack + descriptions), then highlight-ring tap-interception fix (polygons moved to overlay index 0 with null titles and non-consuming click listeners) plus narration banner click handlers plus verbose click-path logging. End-of-session hero regen pass deferred — operator escalated it into a new sibling product at `~/Development/SalemIntelligence` (knowledgebase + LLM for per-business research-driven prompts) that takes a day to build.

Full session detail (3 iterations, 2 operator bug triage rounds, debug log reconstruction, S112 highlight-ring tap-interception diagnosis): `docs/session-logs/session-113-2026-04-09.md`. Commit: `2826145`.

---

## Session 112: 2026-04-09 — Major narration overhaul: tiered queue, density cascade, ahead-only filter, glowing highlight ring, walk-sim 3x removal, fresh-start reset, POI button tier filter, home-stops-walksim

Operator field test surfaced three distinct narration bugs (APPROACH-cooldown, walk-sim 3x starvation, sequencing) plus the GPS journey line backgrounding issue (diagnosed not fixed). Shipped 5 commits ending with a 3-level density cascade (40m/30m/20m discard tiers) + bearing-based ahead-only filter that picks the closest in-front POI within the highest non-empty tier, plus a glowing pulsing highlight ring on the active POI and a debug overlay showing the discard radii as outline circles around the walking icon. Operator confirmed "working much better. Success!" on the final walk.

Full session detail (all 5 commits, 7 design questions, debug log analysis, tier classifier, bearing math): `docs/session-logs/session-112-2026-04-09.md`. Commits: `7ca0602`, `95f25f2`, `dded0e6`, `1f63113`, `8a94a06`.

---

## Session 111: 2026-04-09 — Protocol optimization (compress STATE.md, archive S001-S101, rewrite session protocols, thin OMEN report template)

Compressed STATE.md from 784 → 112 lines (snapshot-only), archived sessions S001-S101 to SESSION-LOG-ARCHIVE.md (rolling-window protocol now active), and rewrote CLAUDE.md session-start protocol for parallel + head-only reads and session-end protocol for write-once-in-live-log with a new ~80-line thin OMEN report template. Operator-queued from S110 wrap-up; net per-session read cost drops ~70% at session start. **First session using the new protocols** — this entry itself is the new thin format.

Full session detail (decisions, file diffs, line-count tables, verification): `docs/session-logs/session-111-2026-04-09.md`. Per the new protocol, the SESSION-LOG.md entry is a 2-3 sentence pointer; the live log is the canonical record.

---
<!-- END OF ROLLING WINDOW — Sessions 109 and earlier are in SESSION-LOG-ARCHIVE.md -->
