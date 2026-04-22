# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — it should stay under 200 lines.

**Last updated:** 2026-04-21 — Session 156 (SI content refresh + TigerLine foundation plan adopted + Step 0 discovery shipped. Refreshed all 1,400 POI narrations from SI's rewrite. Operator committed V1 to TigerLine + MassGIS as the foundational mapping substrate with a historical-maps time-slider; OSM/osmdroid to be retired pre-V1. Phase 9Y + 9Z added. Learned `adb install -r` corrupts Room WAL recovery after asset-DB rebake — use uninstall+install.)

---

## TOP PRIORITY — Next Session (S157)

**Operator-directed starting point (LMA is now on a TigerLine + MassGIS foundation track; most prior carry-forwards deferred):**

1. **Phase 9Y foundation — parallel-track continuation.** PG schema extended S156 with 9 nullable columns (building_footprint_geojson, mhc_*, canonical_address_point_id, local_historic_district, parcel_owner_class). Next Kotlin + Room v8→v9 bump needs a careful identity-hash cascade:
   - Edit `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/content/model/SalemPoi.kt` — add 9 matching nullable fields
   - Edit `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/content/db/SalemContentDatabase.kt` — `version = 8` → `version = 9`
   - Build debug APK; extract new identity_hash from `build/generated/ksp/debug/java/.../SalemContentDatabase_Impl.java`
   - Update `cache-proxy/scripts/verify-bundled-assets.js` (v8 const → v9 const)
   - Update `cache-proxy/scripts/bundle-witch-trials-newspapers-into-db.js` (v8 const → v9 const)
   - Update `cache-proxy/scripts/publish-salem-pois.js` — add 9 columns to CREATE_TABLE_SQL + SELECT + INSERT
   - Rebake, verify on Lenovo (uninstall+install per `feedback_adb_install_after_db_rebake.md`)

2. **Phase 9Y MapSurface abstraction.** `FeatureFlags.MAPLIBRE_RENDERER` flag, `MapSurface` interface in new `ui/map/` package, `OsmdroidMapSurface` wraps current calls, empty `MapLibreMapSurface` skeleton, Gradle add `org.maplibre.gl:android-sdk:11.x`. No user-facing change. Sets scaffolding for per-overlay port in later sessions.

3. **Phase 9Y polygon geofence runtime.** Extend `NarrationGeofenceManager.checkPosition()` with `pointInPolygon()` path when `geofence_shape == "polygon"`. Test with synthetic data until real MassGIS polygons arrive from TigerLine.

4. **COUNSEL RESPONSE WATCH (async, unchanged from S155).** When retainer letter arrives, email Tier 2 PDF + reassess 12 A7 decisions. Parked.

5. **Form TX copyright registration ($65) — Hard deadline 2026-05-20, 29 days.** Unblocked by counsel. Operator-owed.

6. **Task #9 — Find WebView buttons** (carry-forward from S154). Still owed. Scope: `SalemMainActivityFind.kt` `showPoiDetailDialog` has "Load Website" + "Reviews" buttons that open WebView (violates V1_OFFLINE_ONLY). Gate for stripped commercial categories.

7. **Wire `verify-bundled-assets.js` into Gradle preBuild.** Still manual; needed before V1 ship. Now must also handle v9 identity hash after 9Y.2b lands.

8. **APK-size pre-Play-Store audit.** `poi-icons/` 544 MB is next target (downsize to 256×256 / WebP q=75). TigerLine tile bundles will add ~80-150 MB on top; APK budget needs to stay manageable.

**Deferred from S155 carry-forward (reprioritized by Phase 9Y foundation shift):**
- Real outdoor field walk (S150 fixes 1/3 validation) — valuable but not blocking Phase 9Y work
- Smoke-test S154 APK on Lenovo — covered by S156's successful launch (ambient narration 1770 points loaded cleanly)
- Play Store closed-testing tester recruitment (20 × 14d) — hold until after 9Y lands
- 1692-victim-tribute burial-ground audit — still an eventual must-have; no schedule pressure
- SI ANOM-001 / ANOM-002 response — awaiting upstream
- Backup cleanup (`/tmp/commercial-heroes-backup-S154-2026-04-20.tar.gz`) — 2-3 wk window, not urgent

**TigerLine + SalemIntelligence asks filed via OMEN:** `~/Development/OMEN/reports/locationmapapp/S156-tigerline-asks-2026-04-21.md`. Contains 7 asks to TigerLine (Phase 2 unblock, MassGIS priority, SpatiaLite bundle, routing graph, tile ownership confirmation, projection conventions, schema docs) and 4 to SalemIntelligence (historical-tile ownership decision, V1 Sanborn year slate 1890/1906/1950/1957, manifest stability, stale "503/509" correction).

---

### Post-S156 key facts

- **Phase 9Y (TigerLine+MassGIS Foundation) and Phase 9Z (Historical Maps Time-Slider)** added to master plan. Phase 9Y precedes Phase 9Q / 9R / 10 — they become consumers of the new substrate.
- **PG `salem_pois` extended** with 9 nullable Phase 9Y columns at S156 close. Backward-compatible; existing code unchanged.
- **Renderer decision:** MapLibre Native Android replaces osmdroid. Parallel flag rollout (both renderers coexist behind `MAPLIBRE_RENDERER` flag) to de-risk the port of 12+ overlay types. osmdroid ripout at end of Phase 9Y.7.
- **Time-slider year slate:** 1890, 1906, 1950, 1957 Sanborn atlases (300 sheets total; LoC IIIF metadata provides bbox — no manual GCP work). 1626/1813/1853 embed as static narrative overlays.
- **TigerLine state** (2026-04-21): Phase 2 import stalled at ~80%; `county/tract/place/cousub/state/faces` at 0 rows despite import_log marking complete. Export scripts for 7a/7b/7c don't exist. MassGIS ingest not started. LMA blocked on these but can progress in parallel on schema / abstraction / runtime scaffolding.
- **New feedback memory** `feedback_adb_install_after_db_rebake.md` captures: after `publish-salem-pois.js` rebake, deploy via `adb uninstall && adb install`, NEVER `install -r` — SIGKILL during replace corrupts Room WAL recovery and produces false "no such table" crashes.
- **SI refresh absorbed S156 morning.** 1,400 POI narrations OVERWRITE-updated from SI rewrite + 34 historical_notes + 2 coord drift fixes. Room DB rebaked, 1,830 POIs / 1,770 narrated, bundled at 9.26 MB (up from 9.23 MB).

### Previously current (unchanged unless noted below)

- **V1 content-strip policy shipped (S154).** `PoiContentPolicy.shouldStripContent(poi)` gates every render surface. Strip = BUSINESSES group AND `merchant_tier == 0`. ~1,832 strip / 459 keep.
- **Category line (S154).** KEEP: HISTORICAL_BUILDINGS, CIVIC, WORSHIP, PARKS_REC, EDUCATION. STRIP (unless licensed): FOOD_DRINK, SHOPPING, LODGING, HEALTHCARE, ENTERTAINMENT, AUTO_SERVICES, OFFICES, TOUR_COMPANIES, PSYCHIC, FINANCE, FUEL_CHARGING, TRANSIT, PARKING, EMERGENCY, WITCH_SHOP.
- **Hero architecture (S154).** `PoiHeroResolver.forceCategoryFallback` + subcategory-prefix filtering. Commercial hero backup at `/tmp/commercial-heroes-backup-S154-2026-04-20.tar.gz`.
- **GPS cursor freeze fix (S154).** Derived-speed escape hatch in `MotionTracker`; operator-confirmed working.
- **V1 commercial posture (S138):** $19.99 flat paid, fully offline, no ads, no LLM, IARC Teen.
- **PG-13 standing content rule** in effect.
- **Device-verify preference:** Lenovo TB305FU (HNY0CY0W) over any emulator.
- **V1 offline-mode enforcement shipped (S141).** `FeatureFlags.V1_OFFLINE_ONLY = true` + three-layer enforcement (ViewModel gates, OkHttp interceptor, offline-only tile sources).
- **Find feature offline (S141).** Rewritten to `SalemPoiDao` (Room).
- **Walking directions offline (S141).** Straight-line + bundled OSRM polylines.
- **Salem 400+ launch deadline 2026-09-01.** Per operator statement this session: date is aspirational, not hard — quality over schedule.

**Background / deferred items (unchanged):**
- **37-item parking lot walkthrough** — Clusters C/D/E/F/G/H/I items wait.
- **Long-run device soak (8-12 h)** — operator-run at own pace.
- **OMEN-004 — first real Kotlin unit test** — deadline 2026-08-30.
- **NOTE-L014 Privacy Policy** — V1-minimal shipped S145; full OMEN-008-compliant draft pending OMEN review.
- **NOTE-L015 `~/Development/SalemCommercial/` cutover** — parked post-V1 per operator S145.
- **NOTE-L018 PG-13 content rule** — pending OMEN acceptance + upstream relay.
- **NOTE-L019 restrooms_zombie.png regen** — LOW content-art item, no deadline.
- **SalemIntelligence bug report** — `docs/SalemIntelligence-report-phantom-samantha-coords-2026-04-17.md` drafted S144 for operator to forward to SI. 10 BCS POIs with phantom coords 0.2 m off Samantha statue (geocoding fallback bug).

**Older S141-S150 fact blocks archived to `docs/archive/STATE_removed_2026-04-21.md`** — the live narrative is now the Phase 9Y foundation track. Most of those facts are covered in the "Previously current" block above; the archive preserves the session-level granularity for reference.

---

## Phase Status

| Phase | Status | Notes |
|---|---|---|
| 1-9 + 9A+ + 9T (8/9) | COMPLETE | Core dev, offline foundation, ambient narration |
| **9P.A / 9P.B / 9U / 9X** | COMPLETE | Admin UI, unified POI table, Witch Trials feature |
| **9Y** TigerLine+MassGIS Foundation | **STARTED (S156)** | NEW. Plan + Step 0 discovery shipped. PG schema extended with 9 Phase 9Y columns. Next: Kotlin Room v9 bump, MapSurface abstraction, polygon geofence runtime. Blocked on TigerLine Phase 2 + MassGIS ingest for real data. |
| **9Z** Historical Maps Time-Slider | **PLANNED (S156)** | NEW. V1 year slate: 1890, 1906, 1950, 1957 Sanborn. Blocked on TigerLine or SI tile generation decision. |
| **9Q** Salem Domain Content Bridge | DEFERRED — now consumes 9Y substrate | building→POI translation, 425 buildings, 202 newspapers. Simplified further by 9Y's MHC join. |
| **9R** Historic Tour Mode | DEFERRED — now consumes 9Y substrate | opt-in chapter-based 1692 tour. Feeds off Phase 9Z time-slider. |
| **10** Production readiness | DEFERRED behind 9Y+9Z+9Q+9R | Firebase, photos, DB hardening, emulator verification. Offline-tile step (10.3) subsumed by 9Y.6. |
| **11** Branding, ASO, Play Store | Quality over schedule per operator 2026-04-21 | Salem 400+ Oct 2026 is aspirational, not hard. |
| **Cross-project** TigerLine | **PHASE 2 STALLED (2026-04-21)** | 65.7M edges + 35.5M addrs imported; county/tract/place/cousub/state/faces at 0 rows. MassGIS ingest not started. LMA blocked on their Phase 7a/7b/7c deliverables. |
| **Cross-project** SalemIntelligence | **Phase 1 KB LIVE** at :8089 | S156 SI-rewrite absorbed (1,400 POIs refreshed). 1,830 POIs / 1,770 narrated. |

**Sessions completed:** 156. Salem 400+ target 2026-09-01 now aspirational — operator has committed to quality over schedule for the Phase 9Y foundation.

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
