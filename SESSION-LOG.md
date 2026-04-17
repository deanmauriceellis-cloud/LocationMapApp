# LocationMapApp — Session Log

> **Rolling window — last 10 sessions only.** On every session end, the oldest session is moved to `SESSION-LOG-ARCHIVE.md`. This file currently holds Sessions 133-142. Everything older lives in the archive (which itself ends with the original v1.5.0–v1.5.50 archive at the bottom).
>
> **Per-session live conversation logs** (the canonical, append-only record with full reasoning, decisions, file diffs, build results) live in `docs/session-logs/session-NNN-YYYY-MM-DD.md`. The entries in this file are 2-3 sentence summaries — pointers to the live logs, not replacements.

## Session 142: 2026-04-17 — V1 UI hide for seven online-only features

Operator directed picking up S141's deferred UI hide pass, deferring the 8-12h soak to their own schedule, and moving OMEN-004's deadline from 2026-04-30 to 2026-08-30. Explore subagent's survey found all 7 entry points (radar, weather, transit, aircraft, webcams, TFR, online tile switcher) concentrated in a single file — `app-salem/.../ui/menu/AppBarMenuManager.kt`. Shipped six surgical edits behind `FeatureFlags.V1_OFFLINE_ONLY`: hid the dead-code top-bar XML items defensively, hid the slim-toolbar weather + tile-source icons, hid grid-dropdown row 1 (Transit/Webcams/Aircraft/Radar), added early-return guards to all four `show*Menu()` paths, removed `menu_tfr_overlay` from the alerts popup, and stopped `computeActiveLayerCount()` from counting the 8 online-only layer prefs so the grid badge can't reflect stale state. Device-verified on Lenovo HNY0CY0W: `uiautomator dump` shows weather + tile-source icons absent from the toolbar icon row (View.GONE removes them from layout entirely), grid dropdown renders 11 cells across 3 rows (rows 2-4) with row 1 gone, alerts popup has Speed Camera / School Zone / Flood / Crossing / Alert Sound / Alert Distance / Zone Databases but no TFR. No FATAL, no ANR, no AndroidRuntime exceptions across the splash → map → grid → alerts cycle. Combined with S141's data-layer enforcement, V1 now has three-layer offline coverage — OkHttp interceptor (hard backstop) + ViewModel gates (clean logs) + UI hide (no visible surface).

Full session detail: `docs/session-logs/session-142-2026-04-17.md`. Commits: `8b4c210` (UI hide) + the S142 close-out commit.

---

## Session 141: 2026-04-17 — V1 offline-mode enforcement + log tuning

Operator opened the session with a 12-hour Lenovo logcat dump (the S140 device build still running) and asked for a tuning pass. Log review found no crashes / ANRs / OOMs but five real issues: ~720 cache-proxy circuit-breaker W-lines, 1,208 GPS-OBS stale-heartbeat W-lines, 118 `TransitViewModel` main-thread blocks averaging 1.1s (the MBTA fleet StateFlow recompose), 3 spurious `TTS ERROR` E-lines (cancel-vs-real-error mislogging), and 2 walk-sim `DWELL CAP` hits. Operator directed **disable all outbound network features for V1** (including MBTA, so the 1.1s recompose goes away too), keep the three log-tuning fixes, and for V1 walking directions use pre-computed polylines. S141 shipped seven commits: `FeatureFlags.V1_OFFLINE_ONLY` + `OfflineModeInterceptor` installed in all 13 OkHttpClient sites, ViewModel early-return gates on Transit / Weather / Geofence / Aircraft / Main, `FindViewModel` rewritten to `SalemPoiDao` (A1 — offline Room search over 1,837 bundled POIs), `WalkingDirections` V1 offline path using the already-bundled `assets/tours/{tourId}.json` OSRM polylines from S125, `TileSourceManager` forced to empty-URL offline mode (DARK hidden in V1), GPS-OBS backoff cadence (30s → 5m → 15m by stale age), `NarrationMgr.intentionallyStopping` flag to distinguish cancel vs real TTS error, walk-sim dwell cap 60 → 180s that also waives cap while TTS still speaking. Device-verified on Lenovo HNY0CY0W cold boot: zero outbound-network W-lines, tile source reports `v1Offline=true`, GPS-OBS banner shows new backoff message, tour restored at stop 2/10, 1,837 POIs loaded from Room, no crashes. V2 = flip one boolean in `FeatureFlags`.

Full session detail: `docs/session-logs/session-141-2026-04-17.md`. Commits: `e50966b` (interceptor) + `e5208af` (VM gates) + `0f98b69` (FindVM→Room) + `d68440c` (WalkingDirections offline) + `d10c9d8` (tiles + log tuning) + `7a2e886` (MainVM gates) + `9d412c0` (log finalized) + the S141 close-out commit.

---

## Session 140: 2026-04-16/17 — Phase 9X Step 3 complete; Oracle tiles imported, bundled, device-verified on Lenovo; Phase 9X now COMPLETE

Shipped the twice-deferred Phase 9X Step 3. Discovered Salem Oracle had already generated and SalemIntelligence had already ingested 16 PG-13-compliant Oracle Newspaper Digest tiles (`:8089/api/intel/salem-1692/tiles`); PG-13 spot-checked the two highest-risk months (Aug 1692 Burroughs/Proctor and Sep 1692 Corey pressing) — both passed the IARC Teen bar. Wrote `cache-proxy/scripts/import-witch-trials-tiles-from-intel.js` to pull all 16 from SI into PG `salem_witch_trials_articles` under the new Oracle id convention (`intro_pre_1692` / `month_1692_01..12` / `fallout_1693` / `closing_reckoning` / `epilogue_outcomes`, replacing the stale S128 ids). Patched `bundle-witch-trials-into-db.js` with a DELETE-before-INSERT-OR-REPLACE step to avoid id-convention-change orphans in the bundled SQLite. Re-baked `salem_content.db`, refreshed fallback `articles.json`, built `:app-salem:assembleDebug` (31s, clean). Operator chose Lenovo TB305FU over the emulator for device-verify; unloaded Ollama's gemma3:27b first to free GPU, installed on HNY0CY0W, confirmed `WitchTrialsRepo: existing=16 / articles already hydrated` and `showTileDetail id=month_1692_07 order=8` + `id=month_1692_09 order=10`. HTML/WebView renderer, S133 NPC auto-links, TTS Speak button all working on fresh Oracle content. New feedback memory `feedback_lenovo_over_emulator.md`. **Phase 9X now COMPLETE** (14 actual sessions S127-S140 vs 8 originally planned).

Full session detail: `docs/session-logs/session-140-2026-04-16.md`. Commits: `9374380` (importer) + `c588658` (bundle fix + refreshed assets) + the S140 close-out commit.

---

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

---
<!-- END OF ROLLING WINDOW — Sessions 132 and earlier are in SESSION-LOG-ARCHIVE.md -->
