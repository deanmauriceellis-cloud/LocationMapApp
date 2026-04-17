# LocationMapApp — Session Log

> **Rolling window — last 10 sessions only.** On every session end, the oldest session is moved to `SESSION-LOG-ARCHIVE.md`. This file currently holds Sessions 130-139. Everything older lives in the archive (which itself ends with the original v1.5.0–v1.5.50 archive at the bottom).
>
> **Per-session live conversation logs** (the canonical, append-only record with full reasoning, decisions, file diffs, build results) live in `docs/session-logs/session-NNN-YYYY-MM-DD.md`. The entries in this file are 2-3 sentence summaries — pointers to the live logs, not replacements.

## Session 139: 2026-04-16 — Retroactive close-out of S138 paperwork (no code work)

Operator opened the session with *"I thought I did a session end last session — if that was not captured, make it happen."* Evidence confirmed S138's five code commits were on master and pushed, but the session-end paperwork (live-log close block, SESSION-LOG entry, STATE refresh, OMEN report) had been skipped. S139 executed the retroactive close in a single commit (`675cc58`): appended a clearly-labelled close block to the S138 live log with honest narrative of what S138 actually shipped vs. the planned TOP PRIORITY, prepended the S138 entry to SESSION-LOG.md while rolling S128 to the archive, refreshed STATE.md (TOP PRIORITY rewritten for S139+, V1-posture key facts added, sessions count 137 → 138), and wrote the thin-template OMEN report at `~/Development/OMEN/reports/locationmapapp/session-138-2026-04-16.md`. Operator then ended S139; Phase 9X Step 3 (Oracle tile import + device-verify) rolls to S140.

Full session detail: `docs/session-logs/session-139-2026-04-16.md`. Commits: `675cc58` (retroactive close of S138) + the S139 close-out commit. No code changes. Phase 9X Step 3 still pending.

---

## Session 138: 2026-04-16 — 37-item parking lot + V1 commercial posture locked + PG-13 standing content rule

Session pivoted off the planned Oracle tile import / device verify (rolled forward to S139+) into operator-driven strategy work: triaged a 37-item "master review" prep list into `docs/parking-lot-S138-master-review.md` (status / complexity / dependencies per item, 16 clusters, 6 proposed new phases); resolved four decisions via dialog — V1 is **$19.99 flat paid, offline-only, no ads, no LLM, no tiers** (tiered/ads/LLM deferred to V2), operator's William Woodbury house (1676) in Beverly saved as PRIVATE user memory, GPS FAB #34 target confirmed, age-gate posture finalized (accept likely IARC Teen rating). Operator then clarified **PG-13 / Teen content rule** as a standing rule across all channels (narration, text, TTS/sox audio, SD prompts, portraits, external-generator briefs to Oracle / SalemIntelligence / Forge / ComfyUI / Bark / Piper); wrote `feedback_pg13_content_rule.md` memory, indexed at top of MEMORY.md, applied immediately to `docs/oracle-tile-brief.md`. Filed out-of-cycle OMEN notification (`S138-urgent-pg13-content-rule-2026-04-16.md`) asking OMEN to note-ack NOTE-L018 and relay the PG-13 constraint to upstream Salem Oracle / SalemIntelligence / GeoInbox projects.

Full session detail: `docs/session-logs/session-138-2026-04-16.md`. Commits: `b3b16ff`, `cd3393e`, `59b5034`, `c6c3a64`, `746163e`. **Note:** Session 138's end-of-session close-out was executed retroactively at the start of Session 139 (2026-04-16 late-evening) — operator noticed the paperwork had not landed though the commits were already pushed.

---

## Session 137: 2026-04-16 — HTML/WebView "The Oracle" newspaper renderer + tile brief

Shipped Step 2 of the newspaper UI enhancement: replaced the native-TextView `showWitchTrialsNewspaperDetailDialog()` with a full HTML/WebView newspaper page — "The Oracle" masthead with double-rule gold border, info row with dateline + crisis phase, ALL-CAPS headline, italic deck, flowing justified paragraphs with first-letter drop cap, gold dotted-underline NPC cross-links handled via `shouldOverrideUrlLoading` custom-scheme interception (npc:// and newspaper://). Added `renderHtmlWithLinks()` (HTML equivalent of Spannable cross-linker) and `buildNewspaperHtml()` (CSS-styled page builder). Wrote `docs/oracle-tile-brief.md` — complete content brief for all 16 History panel tiles (intro + 12 months + fallout + closing + epilogue) for Salem Oracle to generate as Oracle Newspaper Digests.

Full session detail: `docs/session-logs/session-137-2026-04-16.md`. Commit: `1d4e778`.

---

## Session 136: 2026-04-16 — BCS dedup finalization + device verification + newspaper dock mode

BCS re-import from fresh SI export (1,560 entities): 1,375 enriched, 89 orphans soft-deleted, all 7 BCS-vs-BCS dedup groups resolved. Walk-sim speed reverted to 1.4 m/s. Device verification confirmed newspaper dispatches fire during tour mode (S132 gate fix working). Built narration dock newspaper mode — "THE ORACLE" masthead + date on bottom sheet during newspaper playback, tap opens detail dialog. Next: HTML/WebView newspaper renderer (step 2) + SI-generated month tiles (step 3).

Full session detail: `docs/session-logs/session-136-2026-04-16.md`. Commit: `5aa5032`.

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

---
<!-- END OF ROLLING WINDOW — Sessions 129 and earlier are in SESSION-LOG-ARCHIVE.md -->
