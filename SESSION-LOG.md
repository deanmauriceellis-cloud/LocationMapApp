# LocationMapApp — Session Log

> **Rolling window — last 10 sessions only.** On every session end, the oldest session is moved to `SESSION-LOG-ARCHIVE.md`. This file currently holds Sessions 161-170. Everything older lives in the archive (which itself ends with the original v1.5.0–v1.5.50 archive at the bottom).
>
> **Per-session live conversation logs** (the canonical, append-only record with full reasoning, decisions, file diffs, build results) live in `docs/session-logs/session-NNN-YYYY-MM-DD.md`. The entries in this file are 2-3 sentence summaries — pointers to the live logs, not replacements.

## Session 170: 2026-04-24 — Map sprite pipeline (rotation-only baseline shipped, walking deferred to V2)

Long off-repo sprite-system exploration: built a working SDXL→TripoSR→headless-render→animated-WebP pipeline that produces 7 cartoon characters (witch, owl, black-cat, katrina-kitty, skeleton, mouse, rat) as 16-angle rotation frames packed at **992 KB total** for the eventual map-overlay system. Three approaches to walking animation all failed (AI 2-pose generation lost identity, AnimateDiff is for motion-not-rotation with no rotate-LoRA, procedural Blender mesh deformation crumples AI meshes) — operator chose to leave it as is and defer skeletal rigging to V2. Side deliverable: `MASTER_SESSION_REFERENCE.md` at repo root, a topic-indexed lookup so future sessions can find prior work without loading every log. Repo footprint: only the live log + this entry + MSR + CLAUDE.md key-reference table update; all sprite work lives under `~/AI-Studio/`.

Full session detail: `docs/session-logs/session-170-2026-04-24.md`. Commit: _pending_.

---

## Session 169: 2026-04-24 — Walk-mode dwell rewritten as TTS-gated (CPA removed)

Operator field test surfaced two bugs in the walk FAB: the walker would freeze for 15 s on POIs that never announced, and would stride straight through full 60 s narrations without pausing. Three field-log iterations traced both to the CPA (closest-point-of-approach) trigger model — too wide on one end (LOOK AT firing on POIs at 45-52 m outside any geofence), too narrow on the other (a closer POI's CPA on the same step "consumed" the LOOK AT slot, leaving the farther narrating POI unmatched). Replaced with TTS-gated dwell: walker pauses at whatever step it's on whenever a POI is speaking or queued, resumes when TTS goes idle and queue empties; newspapers stay ambient. Pacing cooldown also bypassed in walk-sim (the dwells themselves are the pacing). Net delta -154 lines on `SalemMainActivity.kt`. Real GPS strategy unchanged.

Full session detail: `docs/session-logs/session-169-2026-04-24.md`. Commit: `53a2b7b`.

---

## Session 168: 2026-04-24 — Install-default sweep + one-narration-per-visit + zoom 14-20 lock + toolbar rework

Install-default sweep across the tourist UX so a first-launch user lands on a sensible default stack: POIs Hist. Landmark off, Record GPS off, Use Real GPS Outside Salem off, GPS-track FAB off, Oracle off, detail level Deep. Narration engine rebuilt to fire exactly one clip per POI visit (priority: stripped-commercial → historical/body text → BRIEF hint) instead of the prior intro-hint + body double. Fixed a first-POI silence bug uncovered by that collapse — `NarrationManager.enqueue()` was dropping segments during the TTS bind race; now defers them and `onInit()` flushes on ready. Map zoom locked to 14-20 (tiles only cover z16-19 + overzoom) with cinematic rewritten to z14 → z16 → z18. Toolbar cluster (First/Prev/Next/Jump) and narration hero banner deleted — single speaker icon on the right now handles tap=cancel/replay and long-press=audio popup; Home moved to the left. Splash-skip bug diagnosed (task-affinity collision brings SalemMainActivity to front instead of SplashActivity) — fix proposal recorded, not applied this session.

Full session detail: `docs/session-logs/session-168-2026-04-24.md`. Commit: `d289efc`.

---

## Session 167: 2026-04-24 — APK packaging audit: poi-icons + tile archive shrink + single-basemap build

Pre-Play-Store packaging pass plus a stack of quality-of-life fixes. Shipped: (1) Walking Tours hang root-caused to a missing `PRAGMA user_version = 9` in the asset DB triggering `fallbackToDestructiveMigration` on first launch — stamped in both source and asset DBs. (2) Walk simulator smoothness fix — `LocationMode.MANUAL` now bypasses the stationary gate (Lenovo `TYPE_SIGNIFICANT_MOTION` never fires). (3) New `POIs Hist. Landmark` toggle in the layers popup (filters 487 `massgis_mhc` POIs). (4) Dead Current/Proposed/Both POI-location selector removed. (5) `poi-icons/` trimmed to witchcraft-style-only + WebP (544 MB → 3.4 MB, 7 other styles archived with README at `docs/archive/poi-icons-unused-styles_archived_2026-04-24/`). (6) `poi-circle-icons/` converted to 128 WebP (36 MB → 1.2 MB). (7) `salem_tiles.sqlite` trimmed to Witchy-only + Salem city bbox + z16-19 + re-encoded WebP q=78 (207 MB → 30 MB, pipeline at `tools/tile-bake/trim-to-salem.py`). (8) Tile archive bundled in APK assets with first-launch auto-extract via `OfflineTileManager` + `noCompress 'sqlite'`. (9) `TileSourceManager` fully collapsed to single-basemap (Esri/Mapnik/Dark source builders, IDs, and `buildDarkTileSource`/`onDarkModeToggled` delegations all deleted). Layers button in toolbar preserved as the home for future toggles. **APK trajectory: 706 MB → 50 MB → 79 MB (bundled tiles).** Well under Play Store 150 MB base-module limit — no asset pack needed.

Full session detail: `docs/session-logs/session-167-2026-04-24.md`. Commit: `8d209b8`.

---

## Session 166: 2026-04-24 — FuzzySearchEngine type/category synonym expansion

Recovering from killed S165; committed all S165 work alongside S166. Fixed two gaps that made type-searches like "dentist" find nothing: the per-token `when { }` scoring was exclusive (non-name fields never accumulated once any name tier fired), and there was no synonym coverage between "dentist" and "dental." Added `TYPE_SYNONYMS` map (35 entries: dentist→dental, lawyer→law/attorney, gym→fitness, coffee→cafe, doctor→medical/clinic, etc.), token expansion before per-token scoring, and non-name fields (subcat, cat, desc, hist, addr) now always accumulate independently.

Full session detail: `docs/session-logs/session-166-2026-04-24.md`. Commit: `578a3fa`.

---

## Session 165: 2026-04-24 — Fuzzy Find engine + admin classification flags + FindPanel category fix (killed session)

Session killed before session-end protocol ran; work committed and closed via S166. Shipped: (1) `FuzzySearchEngine.kt` (new) — multi-tier in-memory scorer (10k/5k/3k/600/400/250/120) with subcat/cuisine/desc/hist/addr tiers; `FindViewModel` preloads all POIs on `init {}`, 250ms debounce, tap→animateTo geocoord zoom=18, long-press→detail dialog. (2) `PoiEditDialog.tsx` — new "Flags" tab with toggle-switch UI for 7 operational flags + 5 classification flags (`is_historical_property`, `is_witch_trial_site`, `is_free_admission`, `is_indoor`, `is_family_friendly`). (3) `PoiTree.tsx` — `CategorySelectCtx` context fix for react-arborist category clicks, new flag fields in `PoiRow`. (4) `FindPanel.tsx` — category click skips subtype tree, directly filters map. (5) `admin-pois.js` — 5 new classification flags in `UPDATABLE_FIELDS`.

Full session detail: `docs/session-logs/session-165-2026-04-24.md`. Commit: see S166.

---

## Session 164: 2026-04-24 — Wrong-project detour (LAN recon → RadioLogger)

No LMA work. Operator asked about LAN threat recon tooling; scripts were built here by mistake, then moved to `~/Research/RadioLogger/tools/`. No LMA code, schema, or docs changed. No commit.

Full session detail: `docs/session-logs/session-164-2026-04-24.md`. Commit: none.

---

## Session 163: 2026-04-23 — MHC hidden POIs with clickable footprints on admin map + category filter; parallel-system detour torn down

Shipped the MassGIS MHC inventory as hidden POIs in `salem_pois` — 487 new rows + 16 enrichments via `tools/historical-buildings/import-to-salem-pois.py` (spatial-join L3 assessor for year_built, nearest-+-name-fuzz enrichment, stable `hb_<sha256>` ids, idempotent). Admin map now renders hidden POIs as subtle clickable polygons (outline only, hover-highlight) wired to the existing `PoiEditDialog`; POI-tree category clicks filter the map to that category with a toggle-off and a blue "Filtering: X" pill. Filed research request to SalemIntelligence at `~/Development/SalemIntelligence/docs/lma-mhc-hidden-poi-research-request.md`. Chat gated post-V1 via `ENABLE_CHAT=true` flag. Mid-session detour: first built a parallel `salem_historical_buildings` table + Room v10 entity + admin tab + separate audio toggle; operator corrected the architecture ("just a POI that doesn't present as a POI") and everything was torn down and rebuilt as hidden POIs — Room back to v9 identity_hash `4ec9ae3528d8f55529cd6875c7b0adef`, migration archived. Four new memories shipped, including `feedback_leverage_existing_assets.md` (don't build parallel systems when existing infrastructure can absorb the change).

Full session detail: `docs/session-logs/session-163-2026-04-23.md`. Commit: `76eec90`.

---

## Session 162: 2026-04-23 — POI location verify scaffolding (DB + admin UI + Android layers); verifier blocked on TigerLine MA ingest

Operator asked for a workflow to verify all POI lat/lng against TigerLine/MassGIS, with TigerLine as ground truth and per-POI "truth of record" override. Probed the data first and found `tiger.addr/addrfeat/edges/featnames` empty for `statefp='25'` (TigerLine MA ingest still in progress per OMEN tigerline session-002) and the `massgis.l3_parcels_essex` polygons loaded but without the joined assessor table — so neither source has usable address-to-coordinate data on this machine right now. Pivoted to ship the additive scaffolding without waiting: (1) PG migration `cache-proxy/scripts/poi-verify-2026-04-23/01-add-location-verify-columns.sql` adds 7 nullable cols + 3 partial indexes to `salem_pois` (lat_proposed/lng_proposed/location_status/location_truth_of_record/location_source/location_drift_m/location_geocoder_rating/location_verified_at) and backfills 60 rows to `no_address`; (2) cache-proxy `admin-pois.js` whitelists `location_truth_of_record`+`location_status` for PUT and adds `POST /admin/salem/pois/:id/accept-proposed-location` (copies lat_proposed→lat, clears proposal, stamps admin_dirty); (3) admin web — `PoiTree.tsx` gets a Location filter `<select>` (any/needs review/no address/no match/unverified/verified/accepted) and a typed `LocationStatus`; `PoiEditDialog.tsx` gets a `LocationVerifyPanel` rendered at the bottom of the existing Location tab — color-coded status badge, current vs proposed coords side-by-side, drift/source/rating/verified_at, "Truth of record" checkbox, "Accept proposed coordinates" button (disabled when no proposal); `tsc --noEmit` clean; (4) Android — new `MenuPrefs.PREF_POI_LOCATION_LAYER`, new `PoiLocationLayerManager` (current/proposed/both), new `MenuEventListener.onPoiLocationLayerChanged` default-method, and `AppBarMenuManager.showTileSourcePopup()` extended to render two radio groups (basemap + POI source) in the same popup with a divider; `compileDebugKotlin` clean. The TigerLine verifier query itself is **deferred** — when MA tables fill (or MassGIS L3 assess loads), it's ~30 lines of SQL to populate the columns we shipped and the rest of the workflow lights up without further schema/UI work. Room migration to expose `lat_proposed`/`lng_proposed` to the Android entity is also deferred until there's data worth migrating for.

Full session detail: `docs/session-logs/session-162-2026-04-23.md`. Commit: `7396c7d`.

---

## Session 161: 2026-04-23 — Car-tour debug-log triage + GPS/V1-offline fixes + repo cleanup

Reviewed 645 KB of car-tour debug log and shipped three rounds of fixes. (1) V1 offline toasts — `searchPoisAt()`, `startSilentFill()`, and `AppBarMenuManager.syncCheckStates` all bypassed the V1_OFFLINE_ONLY gate, producing 7 "POI failed" toasts, 14 "Fill failed" banners, and 4 menu-findItem warnings during the 3-hour drive. All three gated at the caller. (2) GPS "spotty" — root cause was the interval picker thrashing between 10s/2.5s/30s on every fix (19 times in 15 min of driving), each flip doing `locationJob.cancel()` → `removeLocationUpdates` → re-register. Added `LocationManager.updateRequestParams()` that re-calls `requestLocationUpdates` on the live callback (GMS merges, no teardown); `MainViewModel.restartLocationUpdates()` now prefers that path. Interval picker reduced to 2 states (2.5s moving/narrating, 30s idle) with 10s dwell debounce. (3) Confirmed Witchy/Satellite tile alignment — side-by-side blends at 4 landmarks (z=16-18) show building footprints, streets, and piers trace exactly over Esri imagery; drift is not at the bake. (4) Repo cleanup — ~20 stale root + docs/ files archived to `docs/archive/` (CHANGELOG, SOCIAL-PLAN, WEB-APP-PLAN, MASTER_PLAN_ARCHIVE, PLAN-ARCHIVE, REFACTORING-REPORT, all 5 tigerline-* briefs now that TigerLine+MassGIS are in DB, completed SalemIntelligence + si-handoff briefs, etc.); 6 shell scripts moved to `scripts/`; parked master-plan placeholder deleted and pre-park snapshot archived; CLAUDE.md updated accordingly. Deployed twice to Lenovo TB305FU; needs next-drive verification — expected signal is `updateRequestParams — interval Xms → Yms (no teardown)` replacing the old `Flow cancelled — removing location updates after N updates received`.

Full session detail: `docs/session-logs/session-161-2026-04-23.md`. Commit: `2621c05`.

---

<!-- END OF ROLLING WINDOW — Sessions 160 and earlier are in SESSION-LOG-ARCHIVE.md -->
<!-- S160 rolled to archive 2026-04-24 by the session-end protocol (S170) -->
<!-- S159 rolled to archive 2026-04-24 by the session-end protocol (S169) -->
<!-- S158 rolled to archive 2026-04-24 by the session-end protocol (S168) -->
<!-- S157 rolled to archive 2026-04-24 by the session-end protocol (S167) -->
<!-- S156 rolled to archive 2026-04-24 by the session-end protocol (S166) -->
<!-- S155 rolled to archive 2026-04-24 by the session-end protocol (S166) -->
<!-- S154 rolled to archive 2026-04-24 by the session-end protocol (S164) -->
<!-- S153 rolled to archive 2026-04-24 by the session-end protocol (S163) -->
<!-- S152 rolled to archive 2026-04-23 by the session-end protocol (S162) -->
<!-- S151 rolled to archive 2026-04-23 by the session-end protocol (S161) -->
<!-- S150 rolled to archive 2026-04-23 by the session-end protocol (S160) -->
<!-- S149 rolled to archive 2026-04-23 by the session-end protocol (S159) -->
<!-- S148 rolled to archive 2026-04-23 by the session-end protocol (S158) -->


