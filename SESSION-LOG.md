# LocationMapApp — Session Log

> **Rolling window — last 10 sessions only.** On every session end, the oldest session is moved to `SESSION-LOG-ARCHIVE.md`. This file currently holds Sessions 118-127. Everything older lives in the archive (which itself ends with the original v1.5.0–v1.5.50 archive at the bottom).
>
> **Per-session live conversation logs** (the canonical, append-only record with full reasoning, decisions, file diffs, build results) live in `docs/session-logs/session-NNN-YYYY-MM-DD.md`. The entries in this file are 2-3 sentence summaries — pointers to the live logs, not replacements.

## Session 127: 2026-04-15 — Phase 9X.1 Salem Witch Trials feature foundation: schema + splash + 3-panel skeleton

Operator pivoted ahead of Phase 9Q/9R into a new top-level "The Salem Witch Trials" feature (hero+2-below welcome dialog → 3-panel sub-menu: History 4×4 tile grid + Oracle Newspaper browser + People of Salem 1692 portraits/bios). Plan locked via 13 dialog-box Q&A (8 sessions S127-S134, full scope, OMEN-004 deliberately slipped). Phase 1 shipped foundation: 3 PG tables (`salem_witch_trials_articles/npc_bios/newspapers` mirroring `salem_pois` admin_dirty + soft-delete + provenance pattern), 3 Room entities + DAOs + Hilt providers (DB v6 → v7), `WitchTrialsRepository` + `WitchTrialsViewModel`, `MenuPrefs.PREF_NARRATOR_MODE_ENABLED` + utility menu toggle, redesigned welcome dialog (gold-bordered crystal-ball hero card + horizontal Explore/Tour row), `WitchTrialsMenuDialog` with 3 panel cards + "coming in Phase N" placeholders, asset directory `app-salem/src/main/assets/witch_trials/` with empty stubs. Verified visually on Lenovo HNY0CY0W (uninstall+install due to DB version bump, granted location, force-stop+launcher restart, screenshots confirm all 3 navigation paths).

Full session detail: `docs/session-logs/session-126-2026-04-15.md` (Phase 9X.1 section). Commit: `ebc9e30`. Master plan: new Phase 9X section. Plan file: `~/.claude/plans/rosy-shimmying-stream.md`.

---

## Session 126: 2026-04-15 — Phase 9U closeout + GPS-off-means-off + walk-from-long-press + tour-broken hotfix

Cleaned up Phase 9U: deleted NarrationPoint + SalemBusiness Room entities, rerouted `TourPoi` to project from `salem_pois` (no longer `@Entity`), dropped 23 dead repository methods, bumped DB version 5→6, removed legacy CREATE statements from `cache-proxy/salem-schema.sql`, archived 3 one-shot migration scripts to `docs/archive/`. Fixed inventory PDF tool to query `salem_pois` instead of dropped tables. Walk button now starts the tour from the long-press location (snap to nearest interpolated step, back off 15 for clean APPROACH→ENTRY arc). GPS toggle FAB + `setManualLocation` now actually cancel the GMS provider so the hardware stops polling — closes the "heartbeat brings me back home" leak. Tour-selection-broken regression caused by DB version bump (Room destructive migration wiped tour rows) — fixed by uninstall+reinstall, operator accepts destructive-restart for the test app.

Full session detail: `docs/session-logs/session-126-2026-04-15.md`. Commits: `5335ff3` (S125 carry-over) → `bf2c026` (Phase 9U cleanup) → `fddd673` (PDF tool) → `cf09e59` (STATE) → `a997a6a` (walk from long-press) → `1b499a3` (GPS off) → `ebc9e30` (Phase 9X.1 — see S127 entry).

---

## Session 125: 2026-04-14 — Overnight-test fixes + narration coverage to 100% + legacy-tour restoration + admin SI bridge

Single-day megasession (23 commits). Shipped all 11 overnight-test P0/P1/P2/P3/P4 fixes (newspaper heartbeat, dual-walk mutex, Historical Mode TourEngine routing, narratedAt persistence, wake-lock, etc.). Generated narrations for 422 silent POIs via SalemIntelligence (194 cached + 228 stubs, later upgraded by the SI-unknown GET-vs-POST bug fix). Swept 216 stale SI UUIDs: 12 re-linked, 211 soft-deleted. Re-imported 4 legacy tours (essentials / explorer / grand / witch_trials) into PG with fresh OSRM walking polylines so walk-sim follows streets. Heading-up map rotation disabled pending rework. Admin editorial-AI client retargeted from Salem Oracle `:8088` to SalemIntelligence `:8089` (adapter preserves types). Walk-sim random-start now anchors to a random tour stop so narration fires immediately. `has_announce_narration` + `admin_dirty` flags added to salem_pois. OMEN NOTE-L016 filed (Oracle→SI gaps); OMEN Open Item #7 closed (stale-UUID sweep self-resolved).

Full session detail: `docs/session-logs/session-125-2026-04-14.md`. Commit: `4be98ce` (HEAD).

---

## Session 124: 2026-04-14 — Heritage Trail Phase 9R.0 + 1692 newspaper corpus + overnight endurance walk

Shipped Phase 9R.0: Heritage Trail bundled asset (1692 yellow-line route + 11 stops), 1692 newspaper corpus (202 dispatches), `HistoricalHeadlineQueue` with SharedPreferences pointer, 2:1 POI/newspaper silence-fill interleave, AU female voice for newspapers, tag-based narration cancellation. 1,143 POIs now have `historical_note` (up from ~180). Ran a 7.5-hour overnight Heritage Trail endurance walk on the TB305FU tablet — produced `session-124-overnight-issues.md` cataloguing 11 prioritized bugs that Session 125 then consumed.

Full session detail: `docs/session-logs/session-124-2026-04-13.md` + `docs/session-logs/session-124-overnight-issues.md`. Commit: `12fa5a8`.

---

## Session 123: 2026-04-13 — POI dedup (110 soft-deleted), narration resync (+719 narrated), Phase 9R Historical Tour Mode spec

Two-pass POI dedup: name-based (86 losers, prefix-stripping) + address-based (24 losers, with stoplist + same-category guard + min-unique-tokens guard + BCS-uniqueness preservation). Re-ran SalemIntelligence narration sync (1,227 PG updates, +567 short / +719 is_narrated). Wrote Phase 9R Historical Tour Mode requirements spec (`docs/salem-intelligence-historical-tour-request.md`) plus cross-project coordination notes (NOTE-SI006, NOTE-S008) and direct doc copies to SI/Salem trees. Admin map zoom expanded to z22. Net POI count 2,190 → 2,080; 110 losers tagged for pre-Play-Store hard-delete.

Full session detail: `docs/session-logs/session-123-2026-04-13.md`.

---

## Session 122: 2026-04-13 — AudioCraft (AudioGen) installation for Salem sound effects

Installed AudioCraft 1.4.0a2 with AudioGen medium model at `~/AI-Studio/audiocraft/` for generating custom sound effects (geofence triggers, atmospheric audio, notifications). Resolved Python 3.12 / CUDA 13.0 / torchaudio 2.11 compatibility chain. Built Gradio web UI with Salem preset categories and CLI generation script. Created `~/AI-Studio/USAGE.md` service reference. No changes to LocationMapApp codebase — tooling session only.

Full session detail: `docs/session-logs/session-122-2026-04-13.md`.

---

## Session 121: 2026-04-13 — SalemIntelligence narration sync, drop multipass, tap-to-speak detail sheet, icon + dwell fixes

Built `sync-narrations-from-intel.js` and pulled narrations from SalemIntelligence into 1,211 POIs. Dropped multipass narration (pass_2/pass_3 columns) — promoted 717 entries to long_narration, simplified to 2-tier model. POI detail sheet sections now tap-to-speak with TTS interrupt. Fixed missing circle icons for all 19 SalemPoi categories, zoom-out marker sticky size bug, and tightened dwell expansion to 20m→35m→50m max (was 100m). Exported 572 unlinked POIs + handoff doc for SalemIntelligence.

Full session detail: `docs/session-logs/session-121-2026-04-13.md`.

---

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

---
<!-- END OF ROLLING WINDOW — Sessions 117 and earlier are in SESSION-LOG-ARCHIVE.md -->
