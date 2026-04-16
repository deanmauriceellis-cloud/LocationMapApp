# LocationMapApp — Session Log

> **Rolling window — last 10 sessions only.** On every session end, the oldest session is moved to `SESSION-LOG-ARCHIVE.md`. This file currently holds Sessions 124-134. Everything older lives in the archive (which itself ends with the original v1.5.0–v1.5.50 archive at the bottom).
>
> **Per-session live conversation logs** (the canonical, append-only record with full reasoning, decisions, file diffs, build results) live in `docs/session-logs/session-NNN-YYYY-MM-DD.md`. The entries in this file are 2-3 sentence summaries — pointers to the live logs, not replacements.

## Session 136: 2026-04-16 — BCS dedup finalization + device verification + newspaper dock mode

BCS re-import from fresh SI export (1,560 entities): 1,375 enriched, 89 orphans soft-deleted, all 7 BCS-vs-BCS dedup groups resolved. Walk-sim speed reverted to 1.4 m/s. Device verification confirmed newspaper dispatches fire during tour mode (S132 gate fix working). Built narration dock newspaper mode — "THE ORACLE" masthead + date on bottom sheet during newspaper playback, tap opens detail dialog. Next: HTML/WebView newspaper renderer (step 2) + SI-generated month tiles (step 3).

Full session detail: `docs/session-logs/session-136-2026-04-16.md`. Commit: `pending`.

---

## Session 135: 2026-04-16 — BCS dedup + ATTRACTION removal + SI sync + newspaper overhaul

Major data quality and narration overhaul. BCS-prioritized POI dedup (39 soft-deleted, 12 tour stops repointed). Removed ATTRACTION tier entirely (PAID/HISTORIC/REST). Fixed Vampfangs historic false-positive (year ≤1860 threshold). Full SalemIntelligence re-sync with corrected geocoords (1,281 enriched, 100 new, 1,928 total). Regenerated Heritage Trail OSRM route (was cutting through buildings). Newspaper dispatch overhaul: Room DB source with dateline+headline+body format, yield-to-POI, 3s delay, fixed S132 tour-active gate that was blocking newspapers during tours. Walk-sim tuned: 3.0 m/s, 2.5s GPS, 15s POI dwell. Publish pipeline hardened (Room-compatible DDL, no DEFAULTs).

Full session detail: `docs/session-logs/session-135-2026-04-16.md`. Commit: `d186ad6`.

---

## Session 134: 2026-04-16 — POI category reclassification + Historic Sites of Salem feature

Shipped two major deliverables. (1) POI category reclassification: split `TOURISM_HISTORY` into `HISTORICAL_BUILDINGS` (117 POIs) and `TOUR_COMPANIES` (17 POIs), deleted `HAUNTED_ATTRACTION` and `GHOST_TOUR` categories, backfilled `year_established` for 77 POIs from historical notes. 22→20 categories, 12+ code files updated across Kotlin + TypeScript. (2) "Historic Sites of Salem" — new 4th tile on Witch Trials menu with era filter chips (ALL / 1692 & Before / Colonial / Federal), scrollable site list, and detail dialog with NPC cross-linking + TTS. Fixed bundled SQLite publish pipeline — new `publish-witch-trials-to-sqlite.js` populates all 3 witch trials tables. Device verification deferred to S135.

Full session detail: `docs/session-logs/session-134-2026-04-16.md`. Commit: `25dbb49`.

---

## Session 133: 2026-04-16 — Phase 9X.7 cross-linking + Today-in-1692 card + admin integration

Shipped Phase 9X.7 end-to-end: `EntityLinkRenderer.kt` auto-detects 49 NPC names (1,110+ mentions across all bios) as gold underlined ClickableSpans in article, bio, and newspaper detail dialogs — tapping navigates to the bio detail with self-linking excluded. "Today in 1692" gold-bordered card at top of Witch Trials menu matches the current calendar month-day to the 202-newspaper corpus (±3 day window). Admin tool integration: cache-proxy REST endpoints for all 3 witch trials tables + React `WitchTrialsPanel` with tabbed article/bio/newspaper browsers and inline editing, accessed via POIs/Witch Trials toggle in the admin header.

Full session detail: `docs/session-logs/session-133-2026-04-16.md`. Commit: `2669ea6`.

---

## Session 132: 2026-04-15/16 — Phase 9X.6 pencil-sketch portraits for 49 figures + bug fixes

Shipped Phase 9X.6: Oracle-extracted period-accurate appearance descriptions (role-aware vestments — Geneva bands for clergy, judicial justaucorps, per-station dress codes) distilled into SD prompt tails, then rendered through 4 checkpoints × 2 prompt versions (392 total portraits). Operator selected RealVisXL V5.0 v2 (role-aware). 49 grayscale JPGs (2.5 MB) bundled in APK, wired as 160dp hero portrait in bio detail and 48dp circular thumbnails in People browser, all decoded async via `Dispatchers.IO` + `LruCache(60)`. Bug fixes: ambient HINT narrations now suppressed during active tours (`runSilenceFill` tour-state gate); portrait bitmap ANR eliminated. AI Studio expanded with 3 new checkpoints (RealVisXL, Juggernaut XL, Flux.1 dev nf4) and `--api` flag.

Full session detail: `docs/session-logs/session-132-2026-04-15.md`. Commit: `9b81ce4`.

---

## Session 131: 2026-04-15 — Phase 9X.5 People of Salem 1692 panel + TTS chunking for long bios

Shipped Phase 9X.5 end-to-end: imported 49 Tier-1/2 historical figures from `~/Development/Salem/data/json/npcs/` into `salem_witch_trials_npc_bios` (bios assembled by concatenating narrative sections — life_before_1692, role_in_crisis, emotional_arc, etc. — into a `## Header`-delimited body, 10K–21K chars each), baked into the asset DB, and built the `WitchTrialsPeopleBrowserDialog` + `WitchTrialsBioDetailDialog` pair. Browser sorts by role bucket → tier → name, filter chips (All / Judges / Accusers / Accused / Clergy / Officials / Others) colour-coded per STATE.md (scarlet / gold / gray / purple / silver / slate); detail dialog renders gold `## ` subheadings, dates + age-in-1692, historical outcome, and Speak TTS. Root-caused and fixed the long-bio TTS error by adding `chunkForTts()` — splits on paragraph then sentence boundaries at a 3500-char cap; the 14K Stoughton bio now enqueues as 5 segments under the `witchtrials_bio_` tag prefix, and Stop still drains all chunks via `cancelSegmentsWithTag`.

Full session detail: `docs/session-logs/session-131-2026-04-15.md`. Commit: `f312b5e`.

---

## Session 130: 2026-04-15 — Phase 9X.4 Oracle Newspaper panel + tabloid-headline UX overhaul

Shipped Phase 9X.4 end-to-end: `WitchTrialsNewspaperBrowserDialog` (full-screen chronological list of all 202 Salem 1692-era dispatches 1691-11-01 → 1693-05-09, horizontal crisis-phase filter chips) + `WitchTrialsNewspaperDetailDialog` (phase eyebrow / date header / day-of-week / italic summary / Speak pill with `witchtrials_newspaper` TTS tag / bullet body-points list). Consolidated the 202-row `salem_newspapers_1692` corpus into the canonical `salem_witch_trials_newspapers` table, then bumped Room v7 → v8 with two additive columns (`headline`, `headline_summary`), batch-generated all 202 AI tabloid headlines via Ollama `salem-village:latest` (10.9 min, 0 failures; e.g. "FIVE WITCHES SWING FROM PROCTOR'S LEDGE!"), and rebuilt the list row into a 2-line layout: big gold serif "Mmm D, YYYY: ALL-CAPS HEADLINE!" + single-sentence event summary. Bonus: Witch Trials is now also reachable from the 9-dot tour menu (row 4, alongside Tours/Events) via new `onWitchTrialsRequested` event + `ic_witch_trials.xml`.

Full session detail: `docs/session-logs/session-130-2026-04-15.md`. Commit: `d8df2ab`.

---

## Session 129: 2026-04-15 — Phase 9X.3 Salem Witch Trials History 4×4 tile UI + detail dialog shipped

Shipped Phase 9X.3 end-to-end: `WitchTrialsHistoryDialog` (full-screen 4×4 GridLayout of tile cards with tile-kind iconography, period label, bold title, teaser), `WitchTrialsTileDetailDialog` (hero header, italic teaser, pill "▶ Speak" button bound to `tourViewModel.speakSheetSection(tag="witchtrials_article", …)` with auto-cancel-on-dismiss and narrator-mode auto-play). Two publish paths shipped: `publish-witch-trials.js` (JSON asset fallback) + `bundle-witch-trials-into-db.js` (primary — uses `better-sqlite3` to pre-populate the bundled Room DB directly), the latter needed because a retrofitted asset DB silently dropped Room `@Insert` writes on a journal-mode flip. Had to bake the three Phase 9X tables + v7 identity hash (`0545a31e8fb7ce05e575755b61532d46`) into the asset DB to close the original install-crash (`SQLiteException: no such table: salem_witch_trials_articles`). Verified on Lenovo HNY0CY0W with fresh install: welcome → hero → sub-menu → grid → tile #2 → Speak TTS → close → tile #16 all work clean.

Full session detail: `docs/session-logs/session-129-2026-04-15.md`. Commit: `3a7d65b`.

---

## Session 128: 2026-04-15 — Phase 9X.2 Salem Witch Trials History articles: 16/16 generated + loaded into PG

Built `tools/witch-trials-generator/` — Python pipeline that buckets the Salem corpus (`_all_events.json` + `_all_facts.json`'s 1,743 dated facts) per tile, renders Jinja prompts (intro / monthly / quiet_month for Nov+Dec / fallout / closing / epilogue), and drafts all 16 history articles via direct calls to Ollama (`salem-village:latest` = same Gemma3:27B Q4_K_M the Salem Oracle uses internally). Pivoted off Salem Oracle's `/api/oracle/ask` wrapper after the smoke test failed at its hard 30s timeout — bypassing the wrapper saved both the timeout problem and the planned GPU-swap dance with SalemIntelligence (the model can be talked to directly while SI runs). Full 16-article run took 6.8 min total (vs the 50-60 min plan estimate). All 16 imported into `salem_witch_trials_articles` via idempotent UPSERT (`admin_dirty=TRUE` rows protected) — body lengths 3,138-4,232 chars / 494-695 words, all `verified_date=NULL`, `confidence=0.7`, provenance label `data_source='ollama_direct_salem_village'`.

Full session detail: `docs/session-logs/session-128-2026-04-15.md`. Commit: pending. Master plan: Phase 9X.2 marked DONE; architecture + provenance + risks sections updated to reflect the direct-Ollama path. STATE.md TOP PRIORITY rolled to S129 (Phase 9X.3 — wire articles into the History 4×4 tile UI + detail dialog).

---

## Session 127: 2026-04-15 — Phase 9X.1 Salem Witch Trials feature foundation: schema + splash + 3-panel skeleton

Operator pivoted ahead of Phase 9Q/9R into a new top-level "The Salem Witch Trials" feature (hero+2-below welcome dialog → 3-panel sub-menu: History 4×4 tile grid + Oracle Newspaper browser + People of Salem 1692 portraits/bios). Plan locked via 13 dialog-box Q&A (8 sessions S127-S134, full scope, OMEN-004 deliberately slipped). Phase 1 shipped foundation: 3 PG tables (`salem_witch_trials_articles/npc_bios/newspapers` mirroring `salem_pois` admin_dirty + soft-delete + provenance pattern), 3 Room entities + DAOs + Hilt providers (DB v6 → v7), `WitchTrialsRepository` + `WitchTrialsViewModel`, `MenuPrefs.PREF_NARRATOR_MODE_ENABLED` + utility menu toggle, redesigned welcome dialog (gold-bordered crystal-ball hero card + horizontal Explore/Tour row), `WitchTrialsMenuDialog` with 3 panel cards + "coming in Phase N" placeholders, asset directory `app-salem/src/main/assets/witch_trials/` with empty stubs. Verified visually on Lenovo HNY0CY0W (uninstall+install due to DB version bump, granted location, force-stop+launcher restart, screenshots confirm all 3 navigation paths).

Full session detail: `docs/session-logs/session-126-2026-04-15.md` (Phase 9X.1 section). Commit: `ebc9e30`. Master plan: new Phase 9X section. Plan file: `~/.claude/plans/rosy-shimmying-stream.md`.

---

---
<!-- END OF ROLLING WINDOW — Sessions 126 and earlier are in SESSION-LOG-ARCHIVE.md -->
