# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — it should stay under 200 lines.

**Last updated:** 2026-04-17 — Session 144 (Samantha-statue out-of-bbox clamp + cold-start tier-first narration + 7 Monday Must-Haves shipped in one session; all device-verified; 1 session-end commit)

---

## TOP PRIORITY — Next Session (S145)

**Operator-directed starting point:** **finish the remaining 5 Monday Must-Haves** before the 2026-04-20 lawyer C-corp meeting. 7 of 13 shipped in S144. Remaining:

- **#10** Legal walkthrough write-up — consolidate COPPA / IARC Teen / Privacy / ads posture / data-collection for counsel (writing, no code).
- **#11** $19.99 + age-gate counsel-ready write-up — document the S138 posture decision (flat paid → no COPPA via Google Play, no tiers in V1) (writing).
- **#27** Rebuild top hero view (M code — structural).
- **#45** Universal audio control at top menu (M code — touches NarrationMgr, Oracle reader, Witch Trials reader, splash voiceover).
- **#44** Screaming-cat splash voiceover (½ session content-art, pairs with existing Katrina splash work).

**Background items (not blocking S145):**
- **37-item parking lot walkthrough** — Clusters C/D/E/F/G/H/I items wait. Operator will walk through the rest post-Monday.
- **Long-run device soak (8-12 h)** — operator-run at own pace. Confirms S141 GPS-OBS backoff.
- **OMEN-004 — first real Kotlin unit test** — deadline 2026-08-30 (~4.5 months out). Small scope.
- **NOTE-L014 Privacy Policy** — drafted S114, pending OMEN review (31 sessions).
- **NOTE-L015 `~/Development/SalemCommercial/` cutover** — 18 sessions pending operator decision.
- **NOTE-L018 PG-13 content rule** — pending OMEN acceptance + upstream relay.
- **NOTE-L019 restrooms_zombie.png regen** — LOW content-art item, no deadline.
- **SalemIntelligence bug report** — `docs/SalemIntelligence-report-phantom-samantha-coords-2026-04-17.md` drafted S144 for operator to forward to SI. 10 BCS POIs with phantom coords 0.2 m off Samantha statue (geocoding fallback bug).

**Post-S144 key facts:**
- **Samantha clamp shipped** — GPS fixes outside the Salem bbox (42.475–42.545 N × -70.958 to -70.835 W) snap to the Bewitched / Samantha Statue (42.5213319, -70.8958518). New `SalemBounds.kt` helper + clamp at all 4 MainViewModel GPS emission sites. Raw GPS still logs to GPS-OBS for diagnostics; only the `_currentLocation` emission gets replaced. Transition logged once per inside/outside flip.
- **Cold-start tier-first narration shipped** — explore-mode narration queue prefers HISTORIC/CIVIC/EDUCATION tier while the user is within 40 m of their initial narration anchor. After crossing the threshold, reverts to S125's closest-first retail-block behavior.
- **Katrina mascot rollout** — 12 Salem/Halloween-themed splash variants (library-tea, pumpkin patch, moonlit rooftop, cozy bookshop, autumn leaves, jack-o-lantern, crystal orb, lighthouse, fireside, starry bat sky, forest ranger, witchy library tea) at `res/drawable-nodpi/splash_katrina_01..12.jpg` (800×1000 Q80, ~125 KB avg, 1.5 MB total). `SplashActivity.KATRINA_SPLASHES.random()` picks one per launch. Library-tea (#12) is the `splash_katrina_12` fallback referenced by `themes.xml` windowSplashScreenAnimatedIcon and `activity_splash.xml` src.
- **App icon — library-tea scene** — center-cropped 1024×1024 from `vibe-12-witchy-library-tea`. Mipmap PNGs at all 5 densities for `ic_launcher.png`, `ic_launcher_round.png`, `ic_launcher_foreground.png`. Adaptive icon XMLs updated to reference bitmap foreground; old vector "W" logo deleted. Background vector kept as deep-purple #2D1B4E.
- **Welcome dialog restructured (#47 + #47b)** — three cards ordered Take-a-Tour (hero, gold border, 84dp icon) → Witch Trials (64dp icon) → Explore (64dp icon). Icons on the left, Katrina avatar medallion + Creepster title at the top for splash continuity. Three pose-specific Katrina icons: `welcome_card_tour` (green velvet colonial coat + brass candle), `welcome_card_trials` (open spellbook + quill), `welcome_card_explore` (teal bowtie + parchment map). All built as local `drawable-nodpi/welcome_card_*.jpg` at 320×320 Q85 (~25 KB each).
- **Find menu reinforced (#49)** — 4 online/empty tiles hidden (Fuel & Charging, Transit, Parking, Emergency); 15 of 16 remaining tiles relabeled in Salem voice via local override map (Food & Drink → Taverns & Cafés, Healthcare → Apothecaries & Clinics, Worship → Churches & Meetinghouses, etc.); 16 painterly cartoon tile illustrations generated in Forge (`batch-find-tiles.py`) and wired as layered backgrounds with 0x99 black tint + white bold label + shadow for legibility. `FIND_SALEM_LABEL` + `FIND_TILE_ASSET` + `FIND_HIDE_IN_V1` are local to `SalemMainActivityFind.kt` so `PoiCategories.kt` mirror contract to `web/src/config/poiCategories.ts` stays untouched.
- **TTS tuning (#50)** — replaced naive `[.!?]` chunker regex in `WitchTrialsMenuDialog.chunkForTts` with a character-walker `splitIntoSentences()` that honors 40+ abbreviations (Mr/Mrs/Dr/Rev/Inc/Ltd/a.m./p.m./Jan/Feb/etc/Mass/Ave/…), single-letter initials, and next-char-lowercase continuation. Only splits at real sentence ends. Short text (< 3500 chars) still speaks as one utterance with natural Android TTS prosody.
- **Top toolbar + grid dropdown (#13 + #48)** — V1 toolbar: Home | [spacer] | Grid | About (Weather, Alerts, MapLayers all hidden). V1 grid: 2 rows of 4 — Row A: POI / Find / Go To / **Journey** (renamed Utility) / Row B: Tours / Events / Witch Trials / Legend. Social/Chat/Profile removed (auth needs online backend). Journey popup slimmed — only user-facing items remain (Record GPS, Build Story, Email GPX, GPS Mode, Narrator Mode, Legend); dev items stripped.
- **Parking-lot #35 escalated to HARD PRE-V1 RELEASE BLOCKER** per operator direction — all assets encrypted + ProGuard/R8 obfuscation before first Play Store submission. Tracked as task #6.

**S141 facts still current:**
- **V1 offline-mode enforcement shipped** — `FeatureFlags.V1_OFFLINE_ONLY = true` (compile-time const in `:core`). Three-layer enforcement: ViewModel gates (early-return) → OkHttp `OfflineModeInterceptor` (hard backstop in all 13 client sites) → offline-only tile sources (empty URL so osmdroid downloader refuses). V2 resumes by flipping one boolean.
- **Find feature fully offline** — `FindViewModel` rewritten from cache-proxy `FindRepository` to `SalemPoiDao` (Room). Proximity search, text search, category counts, website lookup all served from the bundled 1,837-POI `salem_content.db`. Public API preserved; `SalemMainActivityFind` unchanged. Session-level `hashCode → SalemPoi` cache for `fetchPoiWebsiteDirectly` with name+coord fallback.
- **Walking directions offline** — `WalkingDirections.getRoute()` returns straight-line, `getMultiStopRoute()` returns multi-segment straight-line, `getBundledTourRoute(tourId)` loads the pre-computed OSRM polyline from `assets/tours/{tourId}.json` (already generated for all 5 tours by S125's `backfill-tour-routes.js`). `TourViewModel.getFullTourRoute` prefers the bundled polyline for the active tour. `WalkingRoute.road` is nullable.
- **Log tuning shipped:** GPS-OBS stale-heartbeat backoff (30s → 5m → 15m by stale age; S140 showed 1,208 W-lines over 12h — projected to drop to ~15). `NarrationMgr.intentionallyStopping` flag distinguishes cancel vs real TTS error (spurious `E TTS ERROR` gone from cancel paths). `WALK_SIM_DWELL_MAX_MS` 60 → 180s and waives cap while TTS still actively speaking (Oracle tiles of 2-3 min no longer get cut off).
- **Device-verified** on Lenovo HNY0CY0W: zero outbound-network W-lines at cold boot, `TileSourceManager: buildSource(SATELLITE) v1Offline=true`, tour restored at stop 2/10, 1,837 POIs loaded from Room, no crashes, no ANRs.
- **Phase 9X COMPLETE** (from S140) — 14 actual sessions (S127-S140) vs 8 originally planned.
- **V1 commercial posture locked** (S138): $19.99 flat paid, fully offline, no ads, no LLM, no tiers. IARC Teen (PG-13). S141 brought the code into full compliance with the offline-only half.
- **PG-13 standing content rule** in effect. Rule memory: `feedback_pg13_content_rule.md`.
- **Device-verify preference:** Lenovo TB305FU (HNY0CY0W) over any emulator. Memory: `feedback_lenovo_over_emulator.md`.
- **NOTE-L018 (PG-13)** still pending OMEN acceptance + upstream relay.
- **BCS dedup fully resolved** (from S136). **PG: 1,837 active POIs** (1,483 narrated).
- **Walk-sim dwell cap** now 180s (was 60s); waived while TTS speaking.
- **Room `@Insert` silent-drop** from S129 still latent.
- **NVMe advisory** (2026-04-16) — LIFTED 2026-04-17. Normal commit/push cadence resumes.

**Salem 400+ launch deadline 2026-09-01 still tracks.** 4.5 months runway.

---

## Phase Status

| Phase | Status | Notes |
|---|---|---|
| 1-9 + 9A+ + 9T (8/9) | COMPLETE | Core dev, offline foundation, ambient narration |
| **9P.A** Backend Foundation | **COMPLETE** (S98-S101) | Schema, importer, admin auth, write endpoints, duplicates, per-mode visibility |
| **9P.B** Admin UI | **6/8 done** | 9P.6-9P.10b complete. Pending: 9P.11 (demoted), 9P.13 (folded into 9U). 9P.10a blocked on 9Q. |
| **9U** Unified POI Table | **DONE (S125-S126)** | Dedup, narration resync, NarrationPoint+SalemBusiness entity removal, TourPoi rerouted to salem_pois, legacy PG schema dropped, inventory PDF tool migrated. |
| **9X** Salem Witch Trials Feature | **COMPLETE (S127-S140, 14 sessions)** | S127 foundation. S128 history-article LLM gen. S129 History 4×4 tile UI. S130 Oracle Newspaper panel + 202 AI headlines. S131 People panel + TTS chunker. S132 pencil-sketch portraits + bug fixes. S133 cross-linking (1,110+ NPC name auto-links) + Today-in-1692 card + admin integration. S134 Historic Sites feature + category reclass. S135-S136 BCS dedup + newspaper dock mode. S137 HTML/WebView newspaper renderer + Oracle tile brief. S138 V1 posture locked + PG-13 standing rule + parking lot. S139 retroactive paperwork. S140 Oracle tile import + bundled Room bake + Lenovo device-verify. |
| **9Q** Salem Domain Content Bridge | not started — queued behind 9X | building→POI translation, 425 buildings, 202 newspapers. Simplified by 9U (no `poi_kind` column). |
| **9R** Historic Tour Mode | not started — queued behind 9X | opt-in chapter-based 1692 tour |
| **10** Production readiness | DEFERRED behind 9X+9Q+9R | Firebase, photos, DB hardening, emulator verification |
| **11** Branding, ASO, Play Store | target 2026-09-01 | Salem 400+ launch window |
| **Cross-project** SalemIntelligence | **Phase 1 KB LIVE** at :8089 | 1,724 BCS POIs, 116K entities, 238 buildings, 5.67M relations. Phase 2 (narration gen) pending operator gate. |

**Sessions completed:** 144. Salem 400+ quadricentennial is 2026 — app must be in Play Store by Sept to capture October's 1M+ visitors.

---

## Carry-forward Items (NOT blocking Phase 9U)

**Content-art regen queue (post-S143):**
- **Splash illustration (#43)** — replace `splash_witchkitty.png` with a photo/stylized render of Katrina. Monday must-have per S143 operator direction.
- **Splash voiceover (#44)** — replace `splash_voiceover.wav` with a screaming-cat sound. Monday must-have per S143 operator direction. GPU-caution rule applies.
- **Location-aware art/sound initiative** — broader operator theme; no formal scope yet. Candidate for new phase entry post-launch.

**Still pending (carry into S120+):**
- **Heading-up rotation smoothness** — root cause identified in S115 (100 Hz sensor + main-thread saturation). Plan: cut log chatter, rate-limit apply, move sensor processing to background HandlerThread, switch static detection to wall-clock. **Scheduled for S120 Step 9U.17.**
- **`DWELL_MS = 3_000L` dead code** in `NarrationGeofenceManager` — declared, never wired.
- **GPS journey line backgrounding bug** — diagnosed S112; fix options proposed but not implemented.
- **DB wipe across APK reinstalls** — `fallbackToDestructiveMigration`. Replace with real Room migrations before Play Store.
- **walkSimMode gap-timing differences** — revisit if walk-sim should be 100% indistinguishable from real GPS.
- **Bearing filter cone is 90°** — operator may want to tighten.
- **Admin tool POI creation** — currently only edits existing POIs; Phase 9U BCS import adds 900+ new POIs which partially addresses this.
- **POI encounter review screen** — future in-app debug menu feature.
- **Pre-existing GPS log redundancy** — 4 lines from 4 layers per fix.
- **Narration sheet has no `setPeekHeight`** — 168dp workaround handles common case.
- **PRE-PRODUCTION HARD-DELETE: dedup loser rows** — S123 soft-deleted **110 duplicate POIs** across two passes: 86 from name-based pass (`data_source LIKE '%dedup-2026-04-13-loser%'`) + 24 from address-based pass (`data_source LIKE '%address-dedup-2026-04-13-loser%'`). Before Play Store APK build, hard-delete these so they don't ship in the bundled Room DB. Plan: `DELETE FROM salem_pois WHERE data_source LIKE '%dedup-2026-04-13-loser%' OR data_source LIKE '%address-dedup-2026-04-13-loser%';` (verify zero FK refs first). Dedup scripts: `cache-proxy/scripts/dedup-2026-04-13/`. Operator rule applied: rows with unique `intel_entity_id` (BCS) are kept regardless of address collision.

---

## OMEN Open Items

1. **NOTE-L014 / OMEN-008 — Privacy Policy** — **DRAFTED S114 at `docs/PRIVACY-POLICY.md`**. Pending OMEN review. Ball is in OMEN's court (31 sessions).
2. **NOTE-L015 — `~/Development/SalemCommercial/` cutover never executed.** 18 sessions running. Needs OMEN to execute or retract.
3. **OMEN-002 history rotation** — operator action only.
4. **OMEN-004 — first real Kotlin unit test** — **deadline moved to 2026-08-30** (operator direction S142). Previously 2026-04-30. Surfaced to OMEN in S142 report for amendment or acknowledgment.
5. **Phase 9T.9 walk simulator end-to-end verification** still TODO.
6. **Cross-project: SalemIntelligence** — Phase 1 KB live. Phase 2 (narration gen) pending operator gate. Hero regen deferred behind Phase 2. **S144 new bug report:** `docs/SalemIntelligence-report-phantom-samantha-coords-2026-04-17.md` — 10 BCS POIs have phantom coords 0.2 m off the Samantha statue (geocoding fallback anchoring unresolvable POIs to a famous landmark). Operator to forward to SI.
7. **NOTE-L018 — PG-13 standing content rule** proposed to OMEN at S138 via out-of-cycle notification. Pending OMEN acceptance + relay to upstream Salem Oracle / SalemIntelligence / GeoInbox.
8. **NOTE-L019 — `restrooms_zombie.png` regen** (LOW) — one themed POI icon rsync-skipped during the Session 020 NVMe clone. No deadline, no blocker. Regen during a content-art session.
9. **NVMe advisory** — LIFTED 2026-04-17 (OMEN S021). Normal commit/push cadence resumes.
7. ~~**Cross-project: stale intel_entity_id UUIDs in salem_pois**~~ — **CLOSED S125 2026-04-14 (commit `870733b`).** Self-resolved on LMA's side via `cache-proxy/scripts/dedup-stale-intel-links.js`. Pulled SI's `/api/intel/poi-export` (1597 canonical entities), probed every LMA linkage, fuzzy-matched stale UUIDs by name + coord + `/context` validity, then re-linked survivors and soft-deleted the rest. Outcome: 12 canonicals re-linked (chezcasa, lafayette_hotel, rockafellas_restaurant, the_village + 8 others), 211 soft-deleted with `data_source LIKE '%dedup-stale-uuid-2026-04-14-loser%'` (pending pre-Play-Store hard-delete), 0 stale linkages remaining. Bonus: fixed a `generate-narration-from-intel.js` bug that was aborting on GET 404 instead of falling through to POST `/generate` — any future rerun now correctly synthesizes narrations for SI-known-but-uncached entities. No OMEN action required.

---

## Salem Oracle & SalemIntelligence Integration

**Two distinct services now available:**
- **Salem Oracle** at `:8088` — 1692 historical corpus (63 POIs, NPCs, trial events, newspapers). Used by admin tool's "Generate with AI" feature (Phase 9P.10b).
- **SalemIntelligence** at `:8089` — modern business KB (1,724 BCS entities, 116K total entities, 238 historic buildings, 5.67M relations). Build-time data source for Phase 9U POI import. Integration guide: `~/Development/SalemIntelligence/docs/lma-integration-guide.md`.

---

## Expanded vision (updated S123)

- **Phase 9U** — Unified POI Table + BCS Import. 1 session left (S124). Legacy cleanup + heading-up fix + admin historical_note field. **NEXT.**
- **Phase 9P.C** — Publish loop. Operational (publish-salem-pois.js shipped S118).
- **Phase 9Q** — Salem Domain Content Bridge. 2-3 sessions. Simplified by 9U unified table.
- **Phase 9R** — Historic Tour Mode. 4-6 sessions.

**Total runway:** ~9-12 sessions for 9U(cleanup)+9Q+9R. Launch deadline Sept 1, 2026.
- **S119 parking lot** — 29 items triaged into master plan backlog. See `WickedSalemWitchCityTour_MASTER_PLAN.md` → "Backlog — S119 Parking Lot Triage".

---

## POI Inventory

- **Current PG:** **1,837 active POIs** in `salem_pois` (1,483 narrated). S136 BCS re-import + orphan cleanup (93 soft-deleted) + LMA-side dedup (4 soft-deleted). All BCS-vs-BCS dupes resolved.
- **Room DB:** published from PG, in sync (1,837 rows).
- **Inventory PDF tool:** `tools/generate-poi-inventory-pdf.py`

---

## Pointers to detail

| What | Where |
|---|---|
| Recent session summaries (rolling window) | `SESSION-LOG.md` |
| Older session summaries | `SESSION-LOG-ARCHIVE.md` |
| Live conversation logs | `docs/session-logs/session-NNN-YYYY-MM-DD.md` |
| Full build plan | `WickedSalemWitchCityTour_MASTER_PLAN.md` |
| Phase 9U detail | `WickedSalemWitchCityTour_MASTER_PLAN.md` Phase 9U section |
| SalemIntelligence integration | `~/Development/SalemIntelligence/docs/lma-integration-guide.md` |
| Architecture, tech stack | `CLAUDE.md` and the codebase itself |
| Legal/compliance | `GOVERNANCE.md` |
| Patentable innovations | `IP.md` |
| Monetization strategy | `COMMERCIALIZATION.md` |
| UX/architecture decisions | `DESIGN-REVIEW.md` |
| Test checklist | `CURRENT_TESTING.md` |
| Older STATE.md content | `docs/archive/STATE_removed_2026-04-09.md` |
