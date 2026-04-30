# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — must stay under 200 lines.

**Last updated:** 2026-04-29 — Session 200 closed. Five distinct shipping arcs end-to-end. (1) **S198/S199 carry-forward #4 cleared** — `bundle-witch-trials-newspapers-into-db.js` no longer hard-codes a stale `ROOM_IDENTITY_V9` or writes `room_master_table`; align-asset-schema-to-room is the only stamper. (2) **Commercial-prose legal cleaning.** Per attorney guidance: 1439 commercial tier-0 POIs across 13 categories had description / short_narration / long_narration / historical_note / historical_narration / custom_description nulled in PG (CSV backup at `docs/archive/commercial-prose-wipe-2026-04-29/`). Stripped POI sheet upgraded with sub-category line, tap-to-call (`ACTION_DIAL`, zero permissions), prominent Visit Website (`ACTION_VIEW`), no hours. PoiEditDialog tier-aware field gate hides prose for tier-0 commercials (amber notice explains the tier-bump path). New `commercial_tier0_has_prose` lint check (severity error, count=0 post-wipe). 103 commercial-hero leak files (3.2 MB) pruned. SERVICES added to BUSINESSES bucket in AudioControl + verify + prune (was the 1-cat misalignment vs the 13-set the operator confirmed). Architecture preserves the merchant_tier monetization path — operator hand-authors demo POIs to showcase paid-tier display. (3) **MASSGIS landmark surfacing.** Witch Trials → Historic Sites of Salem fallback chain extended with `historicalNarration` first-priority — unlocked pre-1860 content for ~375 MASSGIS rows. PoiDetailSheet (regular marker tap) renders `historical_narration` in a new 4th body section (`bodyHistorical`); serializer + parser updated to round-trip the field through bundle args; auto-narration during sheet read still gated by `isHistoricalNarrationActive()` (preserves S192 strict pre-1860 rule); user can tap to manually trigger TTS. (4) **PoiDetailSheet field-naming alignment** — "Overview" / "About" / "The Story" relabeled to match admin column names ("Short narration", "Description", "Long narration"). New "Historical narration" section. Hours action chip removed entirely (also fixes `Hours · null` leak). (5) **384 Essex initial McIntire narration** authored from operator's first-hand knowledge — operator previously lived in the Sprague House. Memory `user_personal_context_beverly.md` updated to note operator's authority on personally-resided Salem properties. 528-char pre-1860 strict draft attributing the c. 1806 commission to Samuel McIntire late in his career. Discovered en route: SalemIntelligence's `mode_c_historic_building_details` is empty for most MASSGIS rows (architect/style/year never extracted); operator declined a MACRIS-detail-page scraper in favor of hand-authored content for canonical attributions. Final publish chain ran end-to-end after operator-confirmed admin edits (publish-pois 2219 → tours 2 → tour-legs 33 → bundle WT-* → align v15). APK on Lenovo HNY0CY0W. Operator session-end: "session end and we continue next session." Full detail in `docs/session-logs/session-200-2026-04-29.md`.

**Previous (S199):** Three big shipped end-to-end. (1) **Salem Witch Trials content rewritten under attorney-driven word caps** — Oracle newspapers ≤250 words (avg 213, was 466), Witch Trial History tiles ≤150 (avg 166, was 369–419), People-of-Salem bios ≤150 (avg 135, was 2486 with the long fictional `narrative.*` concatenation). All three Salem Oracle generators (`Salem/cmd/{gennewspaper,gentiles,genbiographies}/`) got tightened prompts + dropped the duplicative "first POINTS repeats the lede" rule + a new `normalizeASCII()` helper. 267 generations on the RTX 3090 (gemma3:27b), 0 failures, 45 min. (2) **Import pipeline restructured** — bio import now reads `tts_full_text` from `data/json/biographies/` (was concatenating 7 fictional `narrative.*` sections out of `npcs/`). New JSON-direct importers replace the SalemIntelligence middleman. Incidental `publish-witch-trials-to-sqlite.js` Date-→-ISO `s()` wrapper extended to newspaper + article inserts. (3) **`is_civic_poi` drift across 179 POIs fixed** — operator field-discovered M&M Contractors narrating during a tour walk despite category=SERVICES; root cause flag never auto-cleared on category change. UPDATE 179 → 0 drift; bidirectional auto-sync wired in backend `buildUpdateClause()` + frontend useEffect. Full detail in `docs/session-logs/session-199-2026-04-29.md`.

**Previous (S198):** Two operator-driven fixes. (1) Witch Trials admin tab regression — `cache-proxy/lib/admin-witch-trials.js` destructured `pool` from `deps`, but the shared deps object exposes the connection as `pgPool` (every other admin module does so), so every list/get/put route was 500'ing. One-line alias fix. (2) `body_points` retired from `salem_witch_trials_newspapers` so the in-app Oracle Newspaper page renders the same article that the Speak button + ambient narration narrates (`tts_full_text`). Pre-S198: visual rendered `body_points` (LLM rewrite, ~1370 chars / 6 paragraphs); audio narrated `tts_full_text` (continuous prose, ~2740 chars). Operator: "I want what I hear and what I see to be identical." Room v14 → 15, identity_hash `3e927300be7b2a8971fa6afb4aa5af78`; PG column dropped on both `salem_witch_trials_newspapers` (live) and orphan `salem_newspapers_1692`; full publish chain ran; build clean (~11s); Lenovo HNY0CY0W uninstall + install. Full detail in `docs/session-logs/session-198-2026-04-29.md`.

**Previous (S197):** Two targeted Android narration fixes. (1) S192 punctuation chunker in `NarrationManager.kt` extended with five trip-up handlers — closing-quote sentence boundary so quoted dialogue splits cleanly, em-/en-dash mid-sentence beat (200ms), single-char ellipsis `…` recognized as both terminator and pause (300ms), `ABBREVIATIONS` expanded ~24 entries (Ave/Blvd/Rd/Ln/Esq/PhD/MD/Prof/etc.), `MIN_SUB_CHARS` 60 → 40 so commas in long sentences get an earlier explicit beat. (2) Witch-Trials Speak button bypassed AudioControl gate — added `userInitiated: Boolean = false` to `NarrationSegment`, threaded through `speakTagged*`. `enqueue()` skips group/oracle gates when `userInitiated=true`. `TourViewModel.speakSheetSection()` passes true; auto-triggered ambient still respects toggles. Two commits (`fb410f7`, `215b86d`), two Lenovo reinstalls. Full detail in `docs/session-logs/session-197-2026-04-29.md`.

**Previous (S196):** Three big wins. (1) **Commercial-hero leak fixed** — re-categorized 4 civic non-profits (Historic Salem Inc., Destination Salem ×2, Salem Arts Association) from BUSINESS categories to CIVIC; pruned 19 commercial hero files / 11 unique POIs. Signed-AAB blocker cleared (verify-bundled-assets: 0 leaks). (2) **Historical narration overhaul + full regen.** Generator (`generate-historical-narrations.js`) gained 100–250 word HARD CAP, STRICT_RULE 13 anti-repetition / closure-flourish ban, STRICT_RULE 14 no meta-narration, STRICT_RULE 15 name anchor (caught a Fairbanks-House hallucination on Witch House retry), bare-token / ALL-CAPS preamble strip, era-flexible commemorative tribute path. Operator policy mid-session: ALL Salem-connected commemoratives narrate (was: pre-1860 only) — Bewitched/Samantha now narrates the 1970 filming + Salem pop-culture connection in modern voice; Lydia Pinkham narrates her 1819 birth + 1922 Salem clinic. Memory `feedback_commemoratives_are_historical_tributes.md` updated. Full regen ran 6 hours: 344 ok / 199 reject / 18 skip across 561 eligible POIs. Bundled DB rebuilt + APK installed on Lenovo. 199 rejects surfaced in lint as `narration_regen_failed`. (3) **Admin audit log + revert UI** — operator-requested, end-to-end. PG row-level trigger on 9 admin tables (`salem_audit_log` + `salem_audit_trigger_fn`), captures BEFORE+AFTER + actor/source from `SET LOCAL "app.actor"`. Cache-proxy `lib/admin-audit.js` with list/stats/per-entity/single-row endpoints + revert (applies inverse op, marks original reverted, writes new audit row). Web admin **Audit tab** (`AuditTab.tsx`) with stats strip, filter bar, color-coded action timeline, expand-to-diff, per-row Revert + reason prompt, 15s auto-refresh. E2e revert test verified. CAVEAT: 561 regen audit rows + admin-UI write rows have NULL actor/source — code paths not yet instrumented (next session: multi-admin auth + actor-from-session wiring). 3 commits (`7c7b116`, `f5a9654`, `d961dc9`), 1 Lenovo reinstall. Full detail in `docs/session-logs/session-196-2026-04-28.md`.

**Previous (S195):** Layers checkboxes (POIs Hist. Landmark / POIs Civic) repurposed from tour-mode-only widening into true global overrides gating eligibility in BOTH explore and tour modes. Phase A backfilled `is_historical_property=true` on 358 active HIST_BLDG; Phase B: Room v13 → v14, DAO `findNarrated*` widened, NarrationGeofenceManager unified eligibility gate. Per-POI Quality flags panel landed in PoiEditDialog. 123 POIs flipped to `is_civic_poi=true` (WORSHIP/EDUCATION/PARKS_REC). Heading-up rotation re-enabled GPS-only. 45-min drive test produced 1689 GPS points → ~3-4m systematic NW bias in Lenovo GPS chip (multipath, not basemap). Final `22b5e08`.

**Previous (S189):** Diagnostic + planning session, no app or admin code changed. Synthesized a 10-section total launch review and built `docs/V1-LAUNCH-REVIEW-2026-04-27.odt` (13 KB) as an annotatable intake document. Operator's annotated copy drives S190+ planning. Critical findings flagged: Form TX copyright 23 days out and unconfirmed; 2026-04-20 lawyer meeting outcome not in any session log; privacy policy `[TBD]` placeholders unresolved; Play Console developer account not started; 365 dedup losers still soft-deleted in PG awaiting hard-delete pre-AAB; V1 feature-gating eyes-on smoke test from S180 still owed; single-medium upload-keystore backup.

**Previous (S188):** Polish pass on S187's Geocode-candidates modal + a Witchy-only basemap with server-side auto-overzoom. The Geocodes modal now shows every duplicate's address/coords, geocodes each duplicate's address (cluster-wide best-match selection), and offers a per-candidate **Show on Map** that opens an in-map preview (purple POI dot + fuchsia "?" candidate marker + dashed line) with a smart `analyzeGeocodeConflict()` diagnoser that labels each case (good / warn / bad) and recommends one of five action buttons (Move POI / Validate stored / Hide candidate / Edit address / Cancel). New `salem_geocode_blacklist` PG table backs the **Hide this candidate** action — flagged candidates stop appearing in future Geocodes lookups for that POI. The preview panel can be escaped with `×` or `Esc`, has a Focus toggle that hides every other POI marker, a Show-all-candidates toggle that renders every Tiger hit across the cluster color-coded by rating, and a Re-fit button. Mapnik / Esri / OSM removed from the basemap picker (Witchy is the only basemap); `cache-proxy/lib/admin-tiles.js` gains `sharp`-backed auto-overzoom that walks UP up to 8 ancestor levels (crop+resize) or DOWN up to 2 child levels (stitch) when a (z,x,y) isn't baked, with a 500-entry LRU and `X-Tile-Source` headers for devtools introspection. Modal-hang regression fixed — soft-deleted POIs filtered out of dupe clusters (operator: "soft deleted are not concerns, just footnotes"), per-call Tiger timeout 15→5 s, 20 s wall-clock budget across the whole geocode-candidates call. Cache-proxy restarted on PID 68413 with everything live. Files NEW: none. Files MODIFIED: `cache-proxy/lib/{admin-lint.js, admin-tiles.js}`, `cache-proxy/package.json` (+sharp), `web/src/admin/{GeocodeCandidatesModal.tsx, AdminMap.tsx, AdminLayout.tsx}`. Full detail in `docs/session-logs/session-188-2026-04-26.md`.

S187 standing (still valid): Admin Lint tab is the 4th view, 15 instant checks + on-demand address-geocode deep scan, Tiger MA fully loaded (~322 MB), `salem_lint_suppressions` for false-positive flagging. None of S188 changed the lint check set or counts.

S186 standing (still valid): Tour Mode narration gate + mode-dependent Layers checkboxes (POIs Civic / POIs Hist. Landmark) + `is_civic_poi` Room v11 + 1-hour dedup persists across walk-sim restarts + `is_tour_poi=true` force-visible-and-force-audible. None of S187 touched the Android side. Detail in `docs/session-logs/session-186-2026-04-26.md`.

S184 standing (still valid): Admin walking-route UX polish (anchor + clip + click-to-highlight + click-to-recenter + cancel for + Free / + POI modes). The same anchor + clip algorithm now runs in the app's tour overlay too (S185).

S183 standing (still valid): Per-leg tour walking-route admin tool (backend + UI). PG table `salem_tour_legs` + 3 cache-proxy endpoints. App-side consumption now landed (S185).

S180 standing (still valid): V1 Play Store gating chores DONE end-to-end. First signed AAB at `app-salem/build/outputs/bundle/release/app-salem-release.aab` (78 MB) — superseded by S185 rebuild but signing config + version code (10000) unchanged. Manifest network surface stripped, V1 feature gates R8-stripped, Room migrations locked, signingConfigs wired, upload keystore generated + OMEN-registered. S179: tour routing unified to live `Router.route()` against bundled walking graph; bake-time edge splitting (47,704 edges / 44,223 walkable nodes). S178: `:routing-jvm` extraction + 60m approach cap + surgical OSM ingest. S175: on-device Salem router. S174: web admin Tours tab CRUD. S172: animations live in SalemMainActivity. S171: custom WickedMap engine (replaces osmdroid; migration in progress, 338 osmdroid call sites still extant).

---

## TOP PRIORITY — Next Session (S201)

0. **Read operator's annotated `docs/V1-LAUNCH-REVIEW-2026-04-27.odt` first.** This intake document defines next-steps priorities and supersedes the engineering-only ranking below. Items 1–12 below remain valid as a backlog but should be re-ordered against the operator's annotated launch-review action plan (Week 1 / 2 / 3–4 / Month 2+ in §9 of the .odt). Critical operator-side blockers from the review are also tracked here as items 13–15.

0a. **(S196 NEW) Multi-admin auth + actor-from-session wiring.** Add `salem_admin_users` table (username, hashed password, role, created_at). Update Basic Auth to verify against env user OR table. Wire admin write endpoints + scripts to `SET LOCAL "app.actor" = req.user` and `"app.source" = '<endpoint name>'` so the audit log gets attribution prospectively.

0b. **(S196 NEW) PoiEditDialog inline history panel.** Show the latest 20 audit entries for the open POI alongside the Quality Flags panel. Per-row Revert. Uses existing `GET /admin/salem/audit/entity/salem_pois/:id`.

0c. **(S196 NEW) Hand-author the 199 rejected narrations** (lint: `narration_regen_failed`). Operator-side. Many are post-1860 hb_* houses where SI dump has too few pre-1860 facts. Walk the lint tab, suppress accepted-silent, hand-author the rest.

0d. **(S199 NEW)** Eyes-on the new shorter Witch Trials content + S200 commercial-template + S200 historical_narration display during a real walk on the Lenovo. Confirm 250/150/150 word caps feel right; confirm tier-0 commercials show only template; confirm MASSGIS rows now surface their pre-1860 narration on marker tap.

0e. **(S199 carry-forward)** Newspaper PK normalization — old `id = date` rows were a legacy from before `cmd/gennewspaper` adopted `id = "salem_oracle_" + date`. `bundle-witch-trials-newspapers-into-db.js` does INSERT OR REPLACE; if any future PG load brings back date-only ids the bundle will accumulate dupes. Pin a constraint OR add a delete-stale-ids step at top of bundle script.

0f. **(S200 NEW)** `civic_flag_mismatch` lint can flip back to STRICT now that S199 wired bidirectional auto-sync on category change — and S200 commercial cleanup proved the auto-sync holds. One-line change in `cache-proxy/lib/admin-lint.js`.

0g. **(S200 NEW)** Operator hand-authors McIntire content for canonical attributions (Hamilton Hall, Gardner-Pingree, Joshua Ward already have grounded coverage; Hawkes + Peirce-Nichols already cite McIntire correctly; 384 Essex stub authored S200 from operator's first-hand knowledge). Other personally-known properties: operator dictates facts → publish chain. SalemIntelligence's `mode_c_historic_building_details` is empty for most MASSGIS rows; operator declined a MACRIS scraper, so any remaining architectural attributions need either operator first-hand knowledge or hand-authored research.

0h. **(S192 carry-forward) Backfill `year_established` on the 500+ HIST_BLDG rows missing it.** Pull from SI + MHC. Reduces the lint cap-hit so other content gaps surface.

0i. **(S195 NEW, still open) Driving mode considered explicitly.** S195 drive-test surfaced walking-tuned geofences (40m + serial TTS) are brittle at 10 m/s. V1 stance is a walking-only disclaimer. Driving mode is V2.

13. **Form TX copyright filing — hard deadline 2026-05-20 (23 days from 2026-04-27).** $65, ~10 minutes online. File as Dean Maurice Ellis individually if entity not yet formed; assign later. Missing this loses statutory damages eligibility for any infringement during the launch window.

14. **Play Console developer account application — multi-week ID-verification lead time.** $25 + government-issued ID. Independent of legal entity setup. Must start ASAP to not gate the Sept 1 ship target.

15. **Privacy policy `[TBD]` placeholders + 2026-04-20 lawyer meeting outcome.** Four placeholders (operating entity, contact email, mailing address, jurisdiction) blocking Play Console submission. Counsel meeting was 2026-04-20 (now 7 days past) — outcome not in any session log. Operator must confirm.

---

1. **Use the Lint tab + S188 map preview to drive content cleanup.** Same backlog as S188's intended TOP PRIORITY, now with sharper tooling. In rough order: backfill `year_established` on 500+ Historical Buildings (Lint → Open Editor); author narration for the 30 silent Civic POIs and the 8 museum tour-flags; hard-delete the 365 dedup losers pre-AAB; run the address-geocode deep scan and walk through results using the new map preview (Show on Map → Move POI / Validate / Hide / Edit). The Geocodes modal now also feeds the `salem_geocode_blacklist` table — every "Hide this candidate" decision persists across sessions.

2. **Onboarding-to-nearest-point on tour start.** Operator request from S185: when user picks a tour and isn't on the polyline, route them to the nearest point on it before walk mode begins. Code path: `TourViewModel.startTour()` → fetch `tour_legs` → flatten polyline → find nearest segment to user's GPS → call `walkingDirections.getRoute(userLoc, nearestPoint)` → publish that as the active directions session. When user reaches it, switch to "tour-walking" mode (no live directions; user just follows the static polyline + POI geofence narration).

3. **GPS-OBS heartbeat investigation.** Pre-existing Lenovo TB305FU quirk surfaced during S185 walk-sim debug: system GPS is delivering fixes (visible at `LocationManagerService` level) but the app's tour observer reports them as stale (`HEARTBEAT STALE — last fix 33s ago, narration reach-out suppressed`). Same family as the broken-motion-sensor issue noted in `feedback_lenovo_motion_sensor_broken.md`. Not introduced by S185, but compounds the "narration didn't fire" experience when walk-sim isn't active. Investigate why fresh fixes aren't reaching `lastFixAtMs` on this device.

4. **Tier 2 — admin → build pipeline auto-bake** (S180 carry-forward, escalated by S185). Per `feedback_admin_changes_propagate_to_builds.md`. Wrap `publish-tours.js` + `publish-tour-legs.js` + `align-asset-schema-to-room.js` as a `preBuild`-dependent Gradle task with a "stale bake" warning when last-bake-mtime < last-admin-edit-mtime in PG. ~30-min job. Now critical since the publish chain has 3 scripts and getting any of them out of order leaves a destructive-migration footgun.

5. **PRE-AAB — hard-delete dedup loser rows. Count corrected by S187 lint: 365** (S185 estimated 110). Lint tab → Cleanup → "Soft-deleted dedup losers (pre-AAB cleanup)" or run `DELETE FROM salem_pois WHERE data_source ILIKE '%dedup%loser%' OR data_source ILIKE '%address-dedup%loser%';` (verify zero FK refs first). Surfaced again in S185: 5 "Ledger" rows in PG, 3 narrated, 2 unnarrated dedup leftovers — likely contributing to confused geofence registration.

6. **Re-author the 5 deleted Kotlin tours in PG** (S185 carry-forward). Operator wants them back as polyline-only tours via the web admin (path-only model — POIs govern narration). Each tour: insert `salem_tours` row, drop free waypoints onto the route in admin map, click Compute Route, eyeball-verify, re-run publish chain.

7. **Continue eyes-on smoke test on Lenovo** (S180/S181/S182 carry-forward). Remaining items: POI detail Visit Website ACTION_VIEW external browser handoff, Find dialog Reviews/Comments hidden in V1, Find dialog Directions on-device router visible, toolbar gating (no Weather/Transit/CAMs/Aircraft/Radar buttons), webcam dialog "View Live" hidden. Worth interleaving since the next AAB rebuilds anyway.

8. **OPERATOR-SIDE — second-medium copy of upload keystore.** One USB copy exists at `/media/witchdoctor/writable/wickedsalem-upload-key-backup-2026-04-26/`. Recommend encrypted cloud or a second USB stored elsewhere before first Play Console upload. Single-medium backups fail. Run `sudo bash ~/keys/backup-staging/copy-to-usb.sh` against a different mounted medium.

9. **Tier 3 — Content-data fixes for outlier POIs** (S178 carry-forward). Two POIs have wrong coords causing >60m snap gaps even with S179's densified graph: `salem_common_2` at (42.5203, -70.8816) is 600m from actual Salem Common (~42.5232, -70.8908); `salem_willows` at (42.535, -70.86945) is mid-parking-lot. SQL UPDATE + re-bake + rebuild AAB.

10. **OPERATOR-SIDE async** (no Claude action): Form TX copyright (lawyer handling, hard deadline 2026-05-20), Play Developer Account verification (multi-week lead time, will tackle in a later session), Privacy Policy public hosting (waiting on lawyer approval).

11. **DEFERRED from S179** (lower priority): Option 2 runtime mid-edge projection in `:routing-jvm` Router; walk-sim + DebugEndpoints `TourRouteLoader` cleanup → full retirement of S178 P6 dead data; water-aware approach segments (post-V1). **Do not propose** as part of routing-quality work — see content-not-engineering rule.

12. **DirectionsSession JVM tests** (still deferred). Convenience-not-correctness.

---

## TOP PRIORITY — Carry-forward (still owed from earlier sessions)

- **Water animation visual tuning** (S172) — operator's verdict was "not the best." Infrastructure complete (world-anchored 14185-anchor lat/lon grid, ANR-safe), but `~` curved-stroke shape doesn't sell as wave crests. Three directions documented in `docs/session-logs/session-172-2026-04-25.md`: different shape (irregular dash bundles / elliptical foam / parallel-to-coast angle), pre-rendered animated drawable (frame-anim PNG/WebP via AI Studio pipeline), or density bump (110m → 70m grid).

- **osmdroid → WickedMapView migration** (S171/S172 carry). 338 osmdroid call sites still extant in `SalemMainActivity`. Stage 1 = layout swap + simple camera/lifecycle calls; Stage 2+ = per-feature overlay migration starting with POIs. Operator accepts temporary regressions in radar/aircraft/METAR/transit during migration.

- **Field-test S169 fixes** (S168/S169/S171): walk-confirm walker dwells on every narrating POI (not just CPA winners) and exits cleanly; one-line manifest fix `android:launchMode="singleInstance"` on SplashActivity to stop launcher tap jumping past Katrina intro; flip `pickNextFromQueue` explore mode to tier-first / closest-tiebreaker (currently closest-first; spec is "historical then closest" per S169).

- **POI location verifier** (S162) — UNBLOCK by checking TigerLine MA ingest status. Schema (`lat_proposed`/`lng_proposed`/`location_status` etc.) + 3 indexes shipped. Verifier itself blocked: `tiger.addr/addrfeat/edges/featnames` were empty for `statefp='25'` in S162. Re-check; if populated, write verifier, run against 1,761 active addressed POIs.

- **S163 MHC hidden POIs companion importer** — wait for SalemIntelligence enrichment delivery, then write `tools/historical-buildings/import-si-enrichment.py` mirroring `import-to-salem-pois.py`.

- **GPS + V1-offline regression check on next drive** (S161) — pull Lenovo debug log; expected zero "POI failed" / "Fill failed" toasts, zero `findItem(...) null` warnings, fix cadence ~1-2.5s while moving (no 20-30s gaps).

- **Find type-search smoke test** (S165/S166) — install + smoke-test "dentist" / "lawyer" / "gym" / "coffee" before further Find work.

- **Witchy tile bake bbox extension OR no-coverage UX hint** (S159) — operator's home in Beverly (42.5567, -70.8717) is north of S158 bake bbox max lat 42.545. Either extend the bake or render a "pan south to Salem" hint when viewport has no tile coverage.

- **`verify-bundled-assets.js` provider list update** (S167 reversed S158). Tiles back in APK assets at `app-salem/src/main/assets/salem_tiles.sqlite` (Witchy-only, Salem city bbox). Script's check is valid again; needs updated provider list.

---

## Architectural pivots & rule changes (recent → older)

- **2026-04-26 (S185):** **Tours are polyline-only paths; POIs govern their own narration.** Operator clarification during walk-sim debug: a tour is just a line for users to follow; POI geofences fire narration based on user proximity, independent of any tour. Tour stops in PG with NULL `poi_id` are internal authoring waypoints (used by admin to compute legs) — they're filtered out of the asset's `tour_stops` table. `TourEngine.startTour` no longer errors on empty stops. Historical Mode is auto-skipped when the active tour has zero user-facing stops (was previously building an empty whitelist that silenced every POI). **PG is the only source of truth for tours/stops/legs**; the 5 historical Kotlin-curated tours are dropped from the asset and will be re-authored via admin when wanted.
- **2026-04-26 (S185):** **Asset schema must be Room-canonical, not legacy SQL.** Two latent landmines were flushed: (1) asset's `tours`/`salem_pois`/etc. had `DEFAULT` clauses from `salem-content/create_db.sql`, but Room codegen schema has none → `TableInfo.equals` mismatch → `fallbackToDestructiveMigration` wiped the asset on every install; (2) `PRAGMA user_version` lagged the `@Database(version=N)` value → upgrade migration ran → fallback destructive. New `cache-proxy/scripts/align-asset-schema-to-room.js` is the canonical bridge: it rewrites every Room-managed table using the exact `createSql` from `app-salem/schemas/<DB>/<v>.json` and stamps both `identity_hash` and `user_version`. Must run last in any publish chain.
- **2026-04-26 (S184):** Admin walking-route rendering uses **anchor + clip** for marker continuity. Each leg's polyline is anchored to its from/to waypoint markers AND clipped to the polyline-points closest to those markers, eliminating both road-snap gaps and overshoot doglegs. The selected leg renders into a custom Leaflet pane (z 450) so it stacks above the green legs and the dashed connector but below the numbered waypoint markers. Pure rendering policy — no engine change.
- **2026-04-26 (S183):** Tour walking polylines become **authored content**, not runtime computation. New PG table `salem_tour_legs` stores per-leg polylines computed at admin time via the same on-device router; runtime APK reads baked legs directly (S184 follow-up). Satisfies S182's content-not-engineering rule by moving the work entirely to authoring time.
- **2026-04-26 (S182):** Tour route fidelity declared content-authoring, not engineering. New HARD RULE `feedback_tour_routing_is_content_not_engineering.md` — operator hand-curates tour stops; routing graph, geocoder, anchor schema, and Router changes are parked for V1.
- **2026-04-26 (S182):** S181 OSM-policy reversal RETRACTED. Diagnosis was wrong (`Router.routeMulti` is per-leg P2P + concat; visual divergence is from POI-centroid input, not from a graph deficiency). Plan archived to `docs/archive/`. `feedback_no_osm_use_local_geo.md` restored to S178 surgical-only constraint.
- **2026-04-26 (S180):** V1 manifest stripped of network permissions; V1 feature gates R8-stripped from binary. App network-incapable at OS level. Rule: `feedback_v1_no_external_contact.md` (zero outside contact except GPS).
- **2026-04-25 (S178):** Surgical 3-osm_id allowlist for wharf walkways added to `salem.edges` (Derby Wharf, Pickering Wharf, Seven Gables). Superseded by S181 broader policy.
- **2026-04-25 (S175):** On-device Salem router shipped (TIGER bake → APK + Directions UI). End of OSRM/online-tile dependency for routing.
- **2026-04-24 (S171):** osmdroid replacement (custom WickedMap SurfaceView engine) shipped as parallel system. S157 OSM-stays decision reversed. Migration of `SalemMainActivity` is in-progress carry-forward.
- **2026-04-23 (S158):** Witchy basemap added — third bundled basemap built from OSM (via Planetiler) + MassGIS structures + L3 parcels + custom MapLibre style. ~90% OSM-rendered, ~10% MassGIS. Documented S181.
- **2026-04-23 (S157):** MassGIS L3 Parcels + Assess for Essex County ingested. `massgis.l3_parcels_essex` = 366,884 polygons, `massgis.l3_assess_essex` = 429,803 rows.

---

## Phase Status

| Phase | Status | Notes |
|---|---|---|
| 1-9 + 9A+ + 9T (8/9) | COMPLETE | Core dev, offline foundation, ambient narration |
| **9P.A / 9P.B / 9U / 9X** | COMPLETE | Admin UI, unified POI table, Witch Trials feature |
| **9Y** MassGIS+TigerLine Overlay Integration | PARTIAL | L3 parcels + assess ingested (S157). Schema extended (S156). v9 cascade (S159). Outstanding: 9Y.3 enrichment script, 9Y.9 polygon geofence runtime, 9Y.Overlay osmdroid dynamic overlays. |
| **9Z** Historical Maps Time-Slider | PLANNED | V1 year slate: 1890, 1906, 1950, 1957 Sanborn. |
| **9Q** Salem Domain Content Bridge | DEFERRED — consumes 9Y data | building→POI translation. |
| **9R** Historic Tour Mode | DEFERRED — consumes 9Y data | opt-in chapter-based 1692 tour. |
| **10** Production readiness | First signed AAB built (S180); commercial chores remain | Firebase, photos, DB hardening, emulator verification. |
| **11** Branding, ASO, Play Store | Quality over schedule per operator | Salem 400+ Oct 2026 aspirational, not hard. |
| **Cross-project** TigerLine | Phase 2 stalled (2026-04-21) | LMA no longer blocked on tile delivery. |
| **Cross-project** SalemIntelligence | Phase 1 KB live at :8089 | 1,830 POIs / 1,770 narrated. |

**Sessions completed:** 195. Salem 400+ target 2026-09-01 aspirational.

---

## POI Inventory (refreshed S180 from live PG)

- **PG `salem_pois`:** 2,778 total. 1,443 visible / 1,335 hidden. 2,354 narrated. 2,639 with short_narration > 20 chars; 1,865 with long_narration > 50 chars. 503 with `building_footprint_geojson`. 1,649 with non-blank `website` URL (used by V1 Visit Website ACTION_VIEW). 0 with `lat_proposed`/`location_status` (verifier still blocked on TIGER MA ingest).
- **Room DB:** 9.3 MB at `app-salem/src/main/assets/salem_content.db`, **v14 schema** (S195), identity_hash `5a42a013703bb605f5ad4f065309ee8a`. Witch Trials intact (npc_bios 49 / articles 16 / newspapers 202). v11 (S186) `is_civic_poi`. v12 (S192) `historical_narration` (358 populated post-S195 cleanup). v13 (S193) `is_historical_tour`. v14 (S195) `is_historical_property` (358 set, 265 `is_civic_poi`). `publish-tour-legs.js` reads latest schema JSON.
- **APK:** ~739 MB debug; `poi-icons/` at 544 MB is the pre-Play-Store audit target.
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

1. **NOTE-L014 / OMEN-008 — Privacy Policy** — V1-minimal Posture A shipped S145 at `docs/PRIVACY-POLICY-V1.md`. Full OMEN-008 draft pending OMEN review (32+ sessions).
2. **NOTE-L015 — SalemCommercial cutover** — PARKED POST-V1 per operator S145.
3. **OMEN-004 first real Kotlin unit test** — deadline 2026-08-30.
4. **Phase 9T.9 walk simulator end-to-end verification** — still TODO.
5. **Cross-project: SalemIntelligence anomaly relay** at `~/Development/OMEN/reports/locationmapapp/S153-si-anomalies-relay-2026-04-20.md` — covers ANOM-001 (9 GP-rooftop phantom-coord leaks), ANOM-002 (Heritage Trail verified_fact propagation gap).
6. **NOTE-L019 restrooms_zombie.png regen** (LOW) — no deadline.
7. **9-dot menu witchy backgrounds** — PARKED S146.
8. **Burial-grounds tribute hallucinations** (Oracle + SalemIntelligence) — PARKED S146. Pre-Play-Store audit needed.
9. **APK size pre-Play-Store blocker** — `poi-icons/` 544 MB. Audit + downsize to 256×256 or WebP q=75.
10. **S150 fixes 1/3 outdoor walk validation** — Fix 1 (DEEP toggle) + Fix 3 (real GPS motion) still pending. Checklist at `docs/field-walk-s153-checklist.md`.

---

## Pointers to detail

| What | Where |
|---|---|
| **Topic-indexed lookup of prior work** | **`MASTER_SESSION_REFERENCE.md`** |
| Recent session summaries (rolling window) | `SESSION-LOG.md` |
| Older session summaries | `SESSION-LOG-ARCHIVE.md` |
| Live conversation logs | `docs/session-logs/session-NNN-YYYY-MM-DD.md` |
| Active multi-phase plans | `docs/plans/` |
| SalemIntelligence integration | `~/Development/SalemIntelligence/docs/lma-*.md` |
| Architecture, tech stack | `CLAUDE.md` and the codebase itself |
| Legal/compliance | `GOVERNANCE.md` |
| Patentable innovations | `IP.md` |
| Monetization strategy | `COMMERCIALIZATION.md` |
| UX/architecture decisions | `DESIGN-REVIEW.md` |
| Test checklist | `CURRENT_TESTING.md` |
| Older STATE.md content | `docs/archive/STATE_removed_2026-04-09.md` (and `_removed_2026-04-21.md`) |
