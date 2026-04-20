# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — it should stay under 200 lines.

**Last updated:** 2026-04-19 — Session 151 (counsel packet PDFs produced for 2026-04-20 counsel engagement meeting: two-tier NDA-gated disclosure — Tier 1 pre-NDA 46-page PDF at `docs/counsel-packet/Katrina-Counsel-Packet-Tier1-Pre-NDA.pdf` hands to counsel Monday; Tier 2 post-NDA 133-page PDF at `docs/counsel-packet/Katrina-Counsel-Packet-Tier2-Post-NDA.pdf` holds until NDA executes; operator prep memo 7 pages at `docs/counsel-packet/Operator-Prep-Memo.pdf`; S150 field-test verification deferred one more session)

---

## TOP PRIORITY — Next Session (S152)

**Operator-directed starting point:**

0. **POST-COUNSEL-MEETING FOLLOW-UP.** The 2026-04-20 counsel engagement meeting happened between S151 and S152. Open S152 by asking the operator:
   - Did counsel sign the mutual NDA (or execute an engagement letter with confidentiality)?
     - If YES: email Tier 2 PDF to counsel that same day. Confirm receipt. Log it in the live conversation log.
     - If NO: hold Tier 2. Reassess in a later session if/when the NDA signs.
   - What did counsel commit to? Engagement scope, fee structure, retainer amount, next-checkpoint date.
   - Which of the 12 open decisions on the A7 Decision Checklist got signed?
   - Any homework counsel assigned (TESS search, state-of-incorporation memo, Form TX prep, etc.)?
   - C-corp paperwork status.

1. **FIELD TEST THE S150 FIXES.** Deferred from S151. Pull `adb pull /sdcard/Android/data/com.destructiveaigurus.katrinasmysticvisitorsguide/files/logs/` and verify the 7 shipped fixes:
   - **Detail flows to `long_narration`:** set Audio Detail to DEEP before walking; log should show `NARR-PLAY: DIRECT PLAY: … detail=DEEP bodyLen=<large>` and `NARR-STATE → Speaking(… type=LONG_NARRATION …)`.
   - **Phillip's house narrates:** Historic New England's Phillips House and Hale Farm should now enter the queue (no longer `SKIP (AudioControl group muted)`). Category in log should read `HISTORICAL_BUILDINGS`.
   - **GPS trail follows live movement:** magenta polyline should grow smoothly at driving and walking speeds.
   - **GPS polling adaptive:** `GPS interval →` should show 2500 during motion/narration, 30000 while parked, 10000 above 20 mph. Total fix count over a 60-90 min walk should be ~100-200, not ~500.
   - **Bbox default ON:** first fix from Beverly should log `bypassBboxClamp = true … setupMap() … bypassBbox=true` and NOT show `snapping to Samantha statue`.
   - **Newspaper panel works:** tapping Witch Trials → Newspaper should load without `HistNewspaper: no such table` errors.
   - **NARR-GATE gate-leak diagnosis:** every POI enqueue logs category/group/enabled/flags. If Grace Episcopal / Golden Dawn Contracting narrate again, the log will reveal whether isPoiSpeechEnabled returned true unexpectedly.

2. **Anything the operator reports from the walk** — any remaining UX surprises, narration timing off, POIs missing, etc.

3. **File Form TX copyright registration within 30 days of 2026-04-20.** $65, unrelated to C-corp formation. Per operator memo §2.1: the 3-month statutory-damages window depends on filing before or within 3 months of first publication.

4. **Carry-forwards from S149 / S150 / S151:**
   - **Webex demo 2026-04-20** — happened between S151 and S152. Confirm outcome.
   - **APK size pre-Play-Store audit** — debug APK 782 MB, `poi-icons/` 544 MB dominant. Prune/compress before first release AAB.
   - **SalemIntelligence phantom-coord bug report** at `docs/SalemIntelligence-report-phantom-samantha-coords-2026-04-17.md` — still needs to be forwarded to SI.
   - **Play Store closed-testing tester recruitment** — per operator memo §2.4: need 20 testers for 14 consecutive days before production release. Start recruiting now.

**Post-S150 key facts:**
- **Detail level now flows to ambient walk** — `NarrationGeofenceManager.getNarrationForPass()` reads `AudioControl.detailLevel()`. BRIEF → null (skip body), STANDARD → `shortNarration`, DEEP → `longNarration ?: shortNarration`. Historical Mode still preferred when `historical_note` populated. New helper `hasAnyNarrationText()` powers the mode-independent no-narrative gate. `pickVariantText()` in `NarrationManager` remains the TourPoi path (unchanged).
- **speakTaggedNarration vs speakTaggedHint split** — `NarrationManager.speakTaggedNarration()` is new, emits `SegmentType.LONG_NARRATION` with shared `inferKindFromTag` helper. `TourViewModel.speakTaggedHint` + `speakTaggedNarration` both exposed. DIRECT PLAY + playNextNarration paths in `SalemMainActivityNarration` now use hint for "You are at X" and narration for the body. All 4 `newspaper_1692` callsites automatically promoted to proper LONG_NARRATION.
- **23 POIs recategorized** (PG + Room) with `data_source … |category-fix-s150-2026-04-18`: 12 HISTORICAL_BUILDINGS (Historic New England's Phillips House, Hale Farm, Ames Memorial Hall, Charter Street / Abbott Street / Greenlawn Cemeteries, Witch Trials Memorial, Salem Maritime NHP + Central Wharf, Colonial Hall, Historic Salem Inc., John Cabot House); 1 CIVIC (Salem City Hall); 10 WORSHIP (First Church in Salem, Grace Episcopal, Remix, 3× St. Peter's, Tabernacle, First Baptist, First United Methodist, Saint Vasilios, Salvation Army). Museums (Salem Witch Museum et al.) intentionally left as ENTERTAINMENT pending a broader MEANINGFUL-vs-BUSINESSES group decision.
- **GPS trail freeze fix** — speed-based escape hatch in `stationaryFrozen`: `update.speedMps > 0.5` bypasses the 25m radius check. Accelerometer-based significant-motion sensor doesn't fire in a stable car, so before this fix the trail only updated every 25m. Now trail updates on every fix during any real motion.
- **GPS polling adaptive** — `val desiredInterval = when { speed > 20mph → 10s; speedMps > 0.5 || narrating → 2.5s; else → 30s }`. Previously `narrationActive=true` was hardcoded, making the 60s battery-saver branch unreachable. S149 field log: 494 fixes in 90 min, mostly parked. Expected drop to ~100-200 fixes in the S151 walk.
- **Bbox override default flipped** — `MenuPrefs.PREF_GPS_BBOX_OVERRIDE_DEFAULT = true`. `SalemMainActivity.setupMap()` reads with this default. Fresh install from Beverly shows real GPS, no Samantha clamp. Emulator / demo can toggle off via Journey menu.
- **Newspaper table** — `bundle-witch-trials-newspapers-into-db.js` now `CREATE TABLE IF NOT EXISTS` before insert, writes to source DB (`salem-content/`), then mirrors to assets. Operator's S149 field log showed 14+ `no such table: salem_witch_trials_newspapers` errors when opening the Witch Trials newspaper panel — that's fixed.
- **NARR-GATE instrumentation** — `enqueueNarration` now logs `NARR-GATE: <poi> category=<X> group=<Y> enabled=<bool> jumpToFront=<bool> (M=<bool> A=<bool> B=<bool>)` on every call. S149 log showed Grace Episcopal Church (ENTERTAINMENT at that time) and Golden Dawn Contracting (OFFICES) passed the S149 AudioControl gate despite no toggle events and the correct category in the DB. No code bypass. Added log will reveal whether `isBusinessesEnabled` returns true unexpectedly or `groupForCategory` resolves to a different group.
- **POI inventory:** **1,830 active POIs** (S149 reported 1,837; post-cleanup actual is 1,830 — 7 Samantha-cluster soft-deletes from S149 now reflected in publish-salem-pois row count). 202 witch-trials newspapers now bundled in assets DB.

**Background items (not blocking S150):**
- **37-item parking lot walkthrough** — Clusters C/D/E/F/G/H/I items wait. Operator will walk through the rest post-Monday.
- **Long-run device soak (8-12 h)** — operator-run at own pace. Confirms S141 GPS-OBS backoff.
- **OMEN-004 — first real Kotlin unit test** — deadline 2026-08-30 (~4.5 months out). Small scope.
- **NOTE-L014 Privacy Policy** — V1-minimal artifact shipped S145 at `docs/PRIVACY-POLICY-V1.md` (Posture A). Full OMEN-008-compliant draft at `docs/PRIVACY-POLICY.md` remains pending OMEN review (32 sessions); decoupled from V1 ship because V1 doesn't collect RadioIntelligence Salem data.
- **NOTE-L015 `~/Development/SalemCommercial/` cutover** — **parked post-V1 per operator direction S145** (reversal of initial "abide" direction). Filesystem check: cutover never executed on this workstation; `~/Development/Salem/` still canonical. Nothing to do LMA-side until the physical move happens; decision is post-V1-ship.
- **NOTE-L018 PG-13 content rule** — pending OMEN acceptance + upstream relay.
- **NOTE-L019 restrooms_zombie.png regen** — LOW content-art item, no deadline.
- **SalemIntelligence bug report** — `docs/SalemIntelligence-report-phantom-samantha-coords-2026-04-17.md` drafted S144 for operator to forward to SI. 10 BCS POIs with phantom coords 0.2 m off Samantha statue (geocoding fallback bug).

**S144-S150 facts still current (compressed):**
- **Counsel packet shipped S151** — `docs/counsel-packet/` holds Tier 1 pre-NDA PDF (46 pages, for counsel Monday), Tier 2 post-NDA PDF (133 pages, held pending NDA), Operator prep memo PDF (7 pages). Source markdown + reproducible build scripts also in that dir. Old lawyer packet `docs/lawyer-packet/10-legal-walkthrough.md` + `11-pricing-and-age-gate.md` remain as historical source.
- **V1 Privacy Policy V1-minimal Posture A** at `docs/PRIVACY-POLICY-V1.md` (4 TBDs for Monday counsel). Full OMEN-008 draft at `docs/PRIVACY-POLICY.md` held for future RI Salem activation.
- **Hosting decision:** DestructiveAIGurus.com subpages `/katrinas-mystic-guide/{privacy,support,terms}`. Entity: C-corp (2026-04-20 counsel meeting); interim fallback sole prop Dean Maurice Ellis.
- **#45 Universal Audio Control shipped (S145)** — `audio/AudioControl.kt` + `audio/NarrationHistory.kt` with 4 group toggles (Oracle / Meaningful / Ambient / Businesses) + Detail radio (Brief/Standard/Deep). S150 split `speakTaggedNarration` from `speakTaggedHint` so body segments emit `SegmentType.LONG_NARRATION`. Category→group mapping: MEANINGFUL = HISTORICAL_BUILDINGS+CIVIC+WITCH_SHOP+WORSHIP; AMBIENT = PARKS_REC+EDUCATION; BUSINESSES = FOOD_DRINK+SHOPPING+LODGING+HEALTHCARE+ENTERTAINMENT+AUTO_SERVICES+OFFICES+TOUR_COMPANIES+PSYCHIC+FINANCE+FUEL_CHARGING+TRANSIT+PARKING+EMERGENCY. Unknown → MEANINGFUL.
- **Content-art & UX shipped S144-S146:** 12 Katrina splash variants + library-tea app icon + welcome dialog restructure (Take-a-Tour/Witch Trials/Explore cards) + Find menu rework (4 online tiles hidden, 16 painterly tiles, Salem-voice labels) + V1 toolbar/grid trimming (Home | Grid | About; 2×4 grid rows; Social/Chat/Profile removed) + TTS chunker honoring 40+ abbreviations.
- **Samantha clamp (S144)** — `SalemBounds.kt`; GPS outside bbox snaps to Samantha Statue. Raw still logs to GPS-OBS. S150 `PREF_GPS_BBOX_OVERRIDE_DEFAULT = true` so fresh install from outside Salem bypasses clamp.
- **Cold-start tier-first narration (S144)** + S150 detail-aware `getNarrationForPass` (DEEP→long_narration).
- **23 POIs recategorized S150** (12 HISTORICAL_BUILDINGS + 1 CIVIC + 10 WORSHIP) tagged `|category-fix-s150-2026-04-18`. Museums (Salem Witch Museum et al.) intentionally left ENTERTAINMENT pending broader MEANINGFUL-vs-BUSINESSES group decision.
- **GPS fixes S150:** speed-based stationary-unfreeze (>0.5 m/s bypasses MotionTracker); adaptive polling ladder (10s driving / 2.5s walking-or-narrating / 30s idle). Newspaper table now baked into assets DB via `bundle-witch-trials-newspapers-into-db.js`.
- **Parking-lot #35 = HARD PRE-V1 RELEASE BLOCKER** — all assets encrypted + ProGuard/R8 obfuscation before first Play Store submission.

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

**Sessions completed:** 151. Salem 400+ quadricentennial is 2026 — app must be in Play Store by Sept to capture October's 1M+ visitors.

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

1. **NOTE-L014 / OMEN-008 — Privacy Policy** — V1-minimal Posture A shipped S145 at `docs/PRIVACY-POLICY-V1.md`. Full OMEN-008 draft at `docs/PRIVACY-POLICY.md` still pending OMEN review (32+ sessions); relevant for future RI Salem activation.
2. **NOTE-L015 — SalemCommercial cutover** — PARKED POST-V1 per operator S145. Salem still at `~/Development/Salem/`; LMA paths resolve correctly.
3. **OMEN-004 first real Kotlin unit test** — deadline moved to **2026-08-30** (OMEN S023 amendment, 2026-04-19). No action this cycle.
4. **Phase 9T.9 walk simulator end-to-end verification** still TODO.
5. **Cross-project: SalemIntelligence** — Phase 1 KB live at :8089. Phase 2 (narration gen) pending operator gate. Hero regen deferred behind Phase 2. **S144 bug report:** `docs/SalemIntelligence-report-phantom-samantha-coords-2026-04-17.md` (10 BCS POIs with phantom 0.2m-off-Samantha coords) still pending operator forward to SI.
6. **NOTE-L018 PG-13 standing content rule** — ACCEPTED by OMEN S023 (2026-04-19). Upstream relays written to Salem / SI / GeoInbox. No LMA action.
7. **NOTE-L019 restrooms_zombie.png regen** (LOW) — no deadline, no blocker.
8. **9-dot menu witchy backgrounds + strong foreground titles** — PARKED S146. Design direction: witchy illustrated backgrounds with readable titles for the 8 grid-menu items. Reusable content-art pipeline from find-tile batch pattern. Detail in S146 log.
9. **Cross-project: Oracle + SalemIntelligence burial-grounds-tribute hallucinations** — PARKED S146. Upstream AI places 1692 victims as interred at Witch Trials Memorial / Charter Street / Old Burying Point (they are NOT buried there). Pre-Play-Store audit needed: grep 20 canonical victim names against tribute-POI narration, excise burial claims. OMEN to relay upstream for Oracle + SI fix.
10. **APK size pre-Play-Store blocker (S147)** — debug APK 780 MB, dominated by `poi-icons/` 544 MB. Audit required: which category folders are live, downsize to 256×256 or WebP q=75. Must land before first Play Store AAB upload.
11. **Post-counsel-meeting follow-up (new S151)** — if counsel signs NDA Monday, deliver Tier 2 PDF same day. File Form TX within 30 days (unrelated to C-corp; preserves statutory-damages window). Start recruiting 20 Play Store closed-testing testers (14-day rule).

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
