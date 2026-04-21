# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — it should stay under 200 lines.

**Last updated:** 2026-04-20 — Session 155 (Thin investigation session, no code changes. Operator asked to stop all services; resource check revealed no LMA services running, only a 1.9 GiB stale Gradle daemon from S154; ollama holding 20 GiB VRAM at 96% utilization; no kernel OOM events in 7 days. Operator's "almost OOM" traced to WWWatcher on a different workstation — not on this box. Session closed with nothing killed, nothing changed. All S154 carry-forwards unchanged.)

---

## TOP PRIORITY — Next Session (S155)

**Operator-directed starting point:**

0. **COUNSEL RESPONSE WATCH (async).** Counsel 2026-04-20 meeting was initial-talk only. Counsel to send cost estimate + retainer letter. When the response lands:
   - If engagement letter with confidentiality arrives → email Tier 2 PDF (`docs/counsel-packet/Katrina-Counsel-Packet-Tier2-Post-NDA.pdf`).
   - Reassess the 12 A7 decisions + homework assignments (TESS trademark, state of incorp, Form TX prep, entity type).
   - Until counsel responds, this is parked — no session-open blocker.

1. **File Form TX copyright registration.** $65, unrelated to C-corp. Per operator memo §2.1: 3-month statutory-damages window. **Hard deadline 2026-05-20.** Unblocked by counsel — operator can file independently.

2. **REAL OUTDOOR FIELD WALK — now unblocked by S154 GPS fix.** Previously unexercised S150 fixes + validate the derived-speed escape hatch at walking pace:
   - **Fix 1:** toggle Audio Detail = DEEP; confirm `detail=DEEP bodyLen=<large>` in log.
   - **Fix 3:** real GPS motion — STATIONARY escape should now fire via derived-speed (`derived-speed escape — X.XXmps` log line), not the 25m distance hatch.
   - **Fix 2 definitive:** Phillips House / Hale Farm on-foot.
   - **Fix 7 leak probe:** with Businesses OFF, see if any stripped POI narrates. If so, NARR-GATE line diagnoses which gate leaked.
   - Checklist at `docs/field-walk-s153-checklist.md` still valid.

3. **Smoke-test S154 APK on Lenovo** (subsumes S153 smoke test since S154 APK carries the same fixes):
   - Witch Trials → People panel + History 4×4 tiles + 1691-11-22 newspaper tap → no SQLiteException.
   - Tap a FOOD_DRINK/SHOPPING POI → stripped full-screen hero + name/address/URL/phone/hours; no narration text; no action buttons; no "Visit Website" button.
   - Tap Pedrick Store House / a church / a park → per-POI hero + full narration. Unchanged.
   - Different restaurants in sequence → different hash-pinned painterly variants (hash-pinning works).
   - Audio Control → Businesses ON → walk past stripped POI → hear only "You are near [Name], at [Address]." No body.

4. **Wire `verify-bundled-assets.js` into the build.** Still manual. Gradle preBuild task + CI hook so neither the S149-class asset clobber nor the S154-class commercial-hero leak can silently ship. Now also includes the S154 commercial-hero leak check (fails if any commercial unlicensed POI has a per-POI hero file).

5. **Task #9 — Find detail WebView buttons.** Scope-adjacent from S154. `SalemMainActivityFind.kt` `showPoiDetailDialog` has "Load Website" + "Reviews" buttons that open embedded WebView — violates both V1_OFFLINE_ONLY (WebView bypasses OkHttp interceptor) and the S154 strip policy. Gate for stripped commercial categories: hide buttons, render URL as inert text.

6. **Carry-forwards from S149-S154:**
   - **APK size pre-Play-Store audit** — debug 820 → 739 MB after S154 hero prune. `poi-icons/` at 544 MB is next dominant target (downsize to 256×256 / WebP q=75).
   - **Play Store closed-testing tester recruitment** — 20 testers for 14 consecutive days.
   - **1692-victim-tribute POI burial-ground audit** — hand-grep 20 canonical victim names against tribute-POI narration (now affects 100% of user-visible narration since commercial was stripped).
   - **SI anomaly reports (ANOM-001 / ANOM-002)** filed via OMEN in S153; SI response pending.
   - **Backup cleanup:** `/tmp/commercial-heroes-backup-S154-2026-04-20.tar.gz` (76 MB) — delete in 2-3 weeks once confident nothing needs restoring.

**Post-S154 key facts:**
- **V1 content-strip policy shipped.** `PoiContentPolicy.shouldStripContent(poi)` gates at every render surface (bottom sheet, geofence TTS, tour detail, ambient hint). Strip condition = category ∈ BUSINESSES group AND `merchant_tier == 0`. Non-licensed commercial POIs render name+address+category-graphic only; merchant unlock via `merchant_tier ≥ 1`. Render-time gate only — no data destruction; SI narrations stay in Room DB for future monetization.
- **Category line** — KEEP (always render): HISTORICAL_BUILDINGS, CIVIC, WORSHIP, PARKS_REC, EDUCATION. STRIP (unless licensed): FOOD_DRINK, SHOPPING, LODGING, HEALTHCARE, ENTERTAINMENT, AUTO_SERVICES, OFFICES, TOUR_COMPANIES, PSYCHIC, FINANCE, FUEL_CHARGING, TRANSIT, PARKING, EMERGENCY, WITCH_SHOP. ~1,832 strip / 459 keep across 2,291 total rows.
- **Hero architecture** — `PoiHeroResolver` gained `forceCategoryFallback: Boolean` flag + subcategory-prefix filtering. Stripped sheet uses Tier 2 hash-pinned pick from `poi-icons/{category}/`. 2,301 per-POI WebPs for 1,832 commercial POIs pruned from APK (73 MB on disk, 81 MB APK). Historic POI heroes preserved. Backup at `/tmp/commercial-heroes-backup-S154-2026-04-20.tar.gz`.
- **GPS cursor freeze fix** — `MotionTracker.isStationary()` stays true forever on Lenovo TB305FU (TYPE_SIGNIFICANT_MOTION never fires). Added derived-speed escape hatch: `distance/time` between consecutive fixes; unfreeze if ≥ 0.3 m/s. New `lastGpsPointMs` timestamp field. Log signature: `STATIONARY: derived-speed escape — X.XXmps (Ym / Ts), unfreezing`. Operator confirmed "working now."
- **SI handoff document** at `docs/si-handoff-s154-content-strip-2026-04-20.md` — details what LMA moved/removed + implications for SI generation priorities (kept categories are highest priority; commercial POI narration bundles dormant; ANOM-001/002 reconciliation now affects 100% of user-visible content). Operator to forward to SI team.
- **Narration repeat-behavior** (operator clarification): one-per-POI per app session is desired; fresh launch re-narrates is expected. No code change.
- **S152 SalemIntelligence KB absorption still current** — 1,769 narrated POIs (1,399 narrations + 33 historical_notes synced). Heritage Trail confabulation cleared across 3 surfaces.
- **S150 fixes status** — Fix 2 ✅ / Fix 7 ✅ / Fix 1 ⚠️ (DEEP toggle still needed) / Fix 3 🚫 (real GPS motion still needed, now unblocked by S154 GPS fix). Real outdoor walk unblocked.

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
- **BCS dedup fully resolved** (from S136). Current inventory tracked in POI Inventory section below.
- **Walk-sim dwell cap** now 180s (was 60s); waived while TTS speaking.
- **Room `@Insert` silent-drop** from S129 still latent.

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

**Sessions completed:** 154. Salem 400+ quadricentennial is 2026 — app must be in Play Store by Sept to capture October's 1M+ visitors.

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
5. **Cross-project: SalemIntelligence** — Phase 1 KB + Phase 2 regen caught up S020/S021 with verified_facts layer battle-tested. LMA absorbed S152 (1,399 narrations + 33 historical_notes). **Anomaly relay FILED to OMEN in S153** at `~/Development/OMEN/reports/locationmapapp/S153-si-anomalies-relay-2026-04-20.md` — covers ANOM-001 (9 GP-rooftop phantom-coord leaks still live in SI export), ANOM-002 (Heritage Trail verified_fact only propagated to short_narration tier; long/medium/historical_note still carried 2020-Red-Line-revision confabulation), plus LOW/INFO items. Older S144 report superseded by ANOM-001, asked OMEN to close it.
6. **NOTE-L018 PG-13 standing content rule** — ACCEPTED by OMEN S023 (2026-04-19). Upstream relays written to Salem / SI / GeoInbox. No LMA action.
7. **NOTE-L019 restrooms_zombie.png regen** (LOW) — no deadline, no blocker.
8. **9-dot menu witchy backgrounds + strong foreground titles** — PARKED S146. Design direction: witchy illustrated backgrounds with readable titles for the 8 grid-menu items. Reusable content-art pipeline from find-tile batch pattern. Detail in S146 log.
9. **Cross-project: Oracle + SalemIntelligence burial-grounds-tribute hallucinations** — PARKED S146. Upstream AI places 1692 victims as interred at Witch Trials Memorial / Charter Street / Old Burying Point (they are NOT buried there). Pre-Play-Store audit needed: grep 20 canonical victim names against tribute-POI narration, excise burial claims. OMEN to relay upstream for Oracle + SI fix.
10. **APK size pre-Play-Store blocker** — S153 debug APK ~820 MB (Room DB grew to 9.2 MB with restored npc_bios + articles tables, still dwarfed by `poi-icons/` 544 MB). Audit required: which category folders are live, downsize to 256×256 or WebP q=75. Must land before first Play Store AAB upload.
11. **Post-counsel-meeting follow-up** — counsel meeting happened 2026-04-20; outcomes NOT captured before workstation crash. Re-ask at S154 start: NDA / engagement / A7 decisions / homework / entity status / Webex demo outcome. File Form TX by **2026-05-20** (statutory-damages window). Start recruiting 20 Play Store closed-testing testers (14-day rule).
12. **S150 fixes 1/2/3/7 — walk-sim validated Fix 2 and Fix 7; Fix 1 (needs DEEP toggle) and Fix 3 (needs real GPS motion) still pending a real outdoor walk.** Checklist at `docs/field-walk-s153-checklist.md`.
13. **Smoke-test patched S153 APK on Lenovo** — installed but not smoke-tested before the computer crash. Verify Witch Trials → People + History + 1691-11-22 newspaper detail all load without SQLiteException.
14. **Wire `verify-bundled-assets.js` into the build** — currently manual. Gradle preBuild task + CI hook so the asset-clobber class of bug can't silently slip into another AAB.

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

- **Current PG:** **1,830 active POIs** in `salem_pois` (**1,769 narrated** post-S152 SI re-sync, up from 1,483 at S150). 1,122 historical_notes. 1,832 commercial POIs (categories in BUSINESSES group) render as stripped; 459 historic/civic/parks/worship/education render in full.
- **Room DB:** 9.2 MB at `app-salem/src/main/assets/salem_content.db`. Witch Trials tables intact (npc_bios 49 / articles 16 / newspapers 202).
- **APK size:** 739 MB debug (post-S154 hero prune; 820 → 739). `poi-icons/` at 544 MB is the remaining dominant target for the pre-Play-Store audit.
- **Hero assets (post-S154 prune):** `heroes/` 18 MB / 395 entries + `hero/` 13 MB / 436 entries (historic + civic + parks + worship + education only). Commercial heroes pruned; backup at `/tmp/commercial-heroes-backup-S154-2026-04-20.tar.gz` (76 MB, 2,307 files).
- **Inventory PDF tool:** `tools/generate-poi-inventory-pdf.py`
- **Assets manifest + pre-build verifier:** `app-salem/src/main/assets/ASSETS-MANIFEST.md` + `cache-proxy/scripts/verify-bundled-assets.js` (S153; S154 added commercial-hero leak check).
- **Commercial-hero prune script:** `cache-proxy/scripts/prune-commercial-heroes.js` (S154, `--dry-run` flag).

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
