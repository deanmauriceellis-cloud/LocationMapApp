# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — it should stay under 200 lines.

**Last updated:** 2026-04-24 — Session 163 closed. Imported the MassGIS MHC inventory as hidden POIs inside `salem_pois` (NOT a separate table). `tools/historical-buildings/import-to-salem-pois.py` reads `tools/tile-bake/salem-historic-buildings.geojson`, filters to NHL/NRIND/LHD + named (503 records), spatial-joins `tiger.massgis.l3_parcels_essex` (FEE-only) + `l3_assess_essex` for `mhc_year_built`, and does a 15m + name-fuzz dedup against existing `salem_pois` → **enrich** matching rows (populate `building_footprint_geojson` + `mhc_year_built` + `mhc_nr_status` + `mhc_narrative`) OR **insert** new hidden POI (`id = hb_<sha256>`, `category = HISTORICAL_BUILDINGS`, `default_visible=false`, `has_announce_narration=true`, `geofence_radius_m=20`, composed `short_narration`). Result: 487 new hidden POIs + 16 enrichments → `salem_pois` now 2,303 active (1,160 hidden, 503 with footprint). Admin map renders hidden POIs as subtle clickable polygon outlines that open the existing `PoiEditDialog`; POI-tree category clicks filter the map with a toggle-off pill. Chat gated behind `ENABLE_CHAT=true` env flag (post-V1). SI research request filed at `~/Development/SalemIntelligence/docs/lma-mhc-hidden-poi-research-request.md` (Tier A/B/C enrichment ask, non-urgent, iterative). Mid-session burned building a parallel `salem_historical_buildings` table + Room v10 + admin tab; operator corrected the architecture ("just a POI that doesn't present as a POI") and the whole parallel subsystem was torn down — Room back at v9 / identity_hash `4ec9ae3528d8f55529cd6875c7b0adef`. New memory `feedback_leverage_existing_assets.md` pins the rule: don't build parallel systems when existing schema can absorb the change.

---

## TOP PRIORITY — Next Session (S164)

**S157 pivot (2026-04-22) still in force:** OSM + osmdroid stay as V1 base map. MassGIS + TigerLine become **overlays only**. MapLibre port, osmdroid ripout, TigerLine tile bundling are CANCELLED. See `project_osm_stays_as_basemap.md` memory.

**Discipline carry-forward (S163):** Every task starts by locating the existing surface that handles 80% of the concern; only build new when the existing thing genuinely cannot absorb the change. `feedback_leverage_existing_assets.md` memory is load-bearing. The session-163 "parallel table → hidden POI" detour cost about half the session; do not repeat.

0a. **POI location verifier — UNBLOCK by checking TigerLine MA ingest status (S162 carry-forward, still owed).** S162 shipped the schema (`lat_proposed`/`lng_proposed`/`location_status`/`location_truth_of_record`/`location_drift_m`/`location_geocoder_rating`/`location_verified_at` + 3 indexes), backend `accept-proposed-location` endpoint, admin filter+panel, and Android layers-popup POI-source group — all additive, all committed. The verifier itself is blocked: `tiger.addr/addrfeat/edges/featnames` are empty for `statefp='25'`. **Ask TigerLine project (or `~/Development/OMEN/notes/tigerline.md`) whether MA tables are populated yet.** If yes → write the verifier, run it against 1,761 active addressed POIs, inspect drift, add `lat_proposed`/`lng_proposed` to `SalemPoi` Room entity (would bump v9→v10), override `onPoiLocationLayerChanged` in `SalemMainActivity` to render proposed markers. L3 assessor path is **already available and already used** for the MHC year_built join (S163), so an L3-centroid fallback for rows with a matched parcel is viable in parallel.

0b. **S163 MHC hidden POIs — wait for SI enrichment, then ship the companion importer.** SI got the research request this session. When SI produces a first batch (JSON keyed by `lma_id` with Tier A fields at minimum: `historical_note` / `mhc_style` / `origin_story` / `owners`), write `tools/historical-buildings/import-si-enrichment.py` mirroring `import-to-salem-pois.py` — UPDATE `salem_pois` in place, honor `admin_dirty=TRUE` (don't clobber operator edits). No schedule — operator will surface the delivery when it lands.

0. **NEXT: verify S161 GPS + V1-offline fixes on next drive.** Pull Lenovo debug log after a representative car + walk tour. Expected signals: (a) zero "POI failed" / "Fill failed" toasts; (b) zero `AppBarMenuManager: findItem(0x7f09019a) null` warnings; (c) `updateRequestParams — interval Xms → Yms (no teardown)` on the rare genuine interval transition, **NOT** `Flow cancelled — removing location updates after N updates received`; (d) fix cadence ~1-2.5s while moving (no 20-30 s gaps). If any regress, root-cause before stacking more work.

1. **S160 tile-picker symptom — still unresolved.** Operator reported "why can't I select the new witchy looking map tiles?" at S160 close. Both servers confirmed live, tile endpoint returns HTTP 401 unauthenticated (= registered). Most likely cause: Vite HMR didn't pick up the new top-level `TILE_PROVIDERS` const + `TileProviderPicker` component; Ctrl+Shift+R the admin page. S161 did not touch this.

2. **Phase 9Y.3 — Cross-join enrichment script.** `cache-proxy/scripts/enrich-pois-from-massgis.js` — for each of ~459 historic/civic/parks/worship/education POIs, resolve against: `massgis.structures` (building footprint), `massgis.mhc_inventory` (year_built / style / NR status / narrative), `massgis.l3_parcels_essex` + `massgis.l3_assess_essex` (LOC_ID / owner class / assessor year_built / use_code), `massgis.landuse2005` (cemeteries). Populate the 9 new columns (Kotlin + publish-script ready as of S159). Preview subset first, then full run and `publish-salem-pois.js` + `bundle-witch-trials-*` to bundle.

3. **Phase 9Y.9 — Polygon geofence runtime.** Extend `NarrationGeofenceManager.checkPosition()` with `pointInPolygon()` branch when `geofence_shape == "polygon"`. Synthetic test data first, then real MassGIS footprint polygons once 9Y.3 lands. Osmdroid `Polygon` overlay renders the shape visually.

4. **osmdroid overlay rendering (9Y.Overlay).** Add a MassGIS overlay layer to the existing osmdroid map: building footprints (for narrated historic POIs), parcel boundaries (optional toggle), cemetery polygons, MHC historic district boundaries. Uses osmdroid `Polygon` / `Polyline` — no renderer change. Option B confirmed by operator in S158 (dynamic overlays, ~2-8 MB subset bundle).

5. **Witchy tile bake bbox extension OR no-coverage UX hint (from S159).** Operator home in Beverly (42.5567, -70.8717) is north of the S158 bake bbox max lat 42.545 → grey osmdroid grid when running the app from home with `bypassBboxClamp=true`. Either (a) extend Witchy + Mapnik + Esri bakes to cover Beverly, or (b) show a "pan south to Salem" hint when the viewport has no tile coverage at the current provider. Per-provider bbox audit owed — run `sqlite3 tools/tile-bake/dist/salem_tiles.sqlite` query to confirm per-provider coverage before deciding.

6. **`verify-bundled-assets.js` S158-catchup (from S159).** Script still checks `app-salem/src/main/assets/salem_tiles.sqlite` but tiles are now pushed to `/storage/emulated/0/Android/data/<pkg>/files/salem_tiles.sqlite` (moved out of APK in S158). Script now fails on every build. Redirect the check to `tools/tile-bake/dist/salem_tiles.sqlite` (dev-side) or drop the check entirely.

7. **COUNSEL RESPONSE WATCH (async, unchanged from S155).** When retainer letter arrives, email Tier 2 PDF + reassess 12 A7 decisions. Parked.

8. **Form TX copyright registration ($65) — Hard deadline 2026-05-20, 27 days.** Unblocked by counsel. Operator-owed.

9. **Find WebView buttons** (carry-forward from S154). Still owed. Scope: `SalemMainActivityFind.kt` `showPoiDetailDialog` has "Load Website" + "Reviews" buttons that open WebView (violates V1_OFFLINE_ONLY). Gate for stripped commercial categories.

10. **Wire `verify-bundled-assets.js` into Gradle preBuild.** Still manual; needed before V1 ship. (After #6 above: the catch-up fix must land first or preBuild fails every run.)

11. **APK-size pre-Play-Store audit.** `poi-icons/` 544 MB is next target (downsize to 256×256 / WebP q=75). Tile bundle pressure lifted — no TigerLine/Sanborn raster packs in V1.

**Deferred from S155/S156 carry-forward:**
- Real outdoor field walk (S150 fixes 1/3 validation) — valuable but not blocking
- Play Store closed-testing tester recruitment (20 × 14d) — hold until after 9Y lands
- 1692-victim-tribute burial-ground audit — eventual must-have; no schedule pressure
- SI ANOM-001 / ANOM-002 response — awaiting upstream
- Backup cleanup (`/tmp/commercial-heroes-backup-S154-2026-04-20.tar.gz`) — 2-3 wk window

**OMEN asks filed:** `~/Development/OMEN/reports/locationmapapp/S156-tigerline-asks-2026-04-21.md` — **needs S157 amendment** before OMEN picks it up. Items re tile-ownership, MapLibre, TigerLine base tiles are now obsolete. Items re MassGIS layer priority, L3 parcels, MHC inventory, polygon data delivery still relevant.

---

### Post-S157 key facts

- **ARCHITECTURAL PIVOT:** OSM + osmdroid STAY as V1 base map (reversal of S156). MassGIS/TigerLine are overlays only. No MapLibre, no tile bundling, no osmdroid ripout. Project memory `project_osm_stays_as_basemap.md` codifies this.
- **Phase 9Y re-scoped:** 9Y.0/9Y.1/9Y.2/9Y.3/9Y.4/9Y.9 stay (data ingest, schema, enrichment, graph reader, polygon geofence). **Struck:** 9Y.5 MapSurface abstraction, 9Y.6 MBTiles consumer, 9Y.7 per-overlay port, 9Y.8 osmdroid ripout. Admin polygon editor (9Y.10) stays removed.
- **Phase 9Z re-scoped:** still shipping the time-slider, but via osmdroid `TilesOverlay` + Sanborn rasters as XYZ overlays, not via a new renderer. Year slate unchanged: 1890, 1906, 1950, 1957.
- **MassGIS L3 Parcels + Assess for Essex County INGESTED (S157).** `massgis.l3_parcels_essex` = 366,884 polygons with geom + LOC_ID + map_par_id (262 MB). `massgis.l3_assess_essex` = 429,803 tabular rows joined by LOC_ID (450 MB). Ingest script: `cache-proxy/scripts/ingest-l3-parcels-essex.py` (idempotent, checkpoints in `cache-proxy/out/l3-essex/`). Join sample validated against 111 Essex St Beverly (year_built 1676, SFR use code 101, owner match). Unlocks `canonical_address_point_id` + `parcel_owner_class` columns.
- **TigerLine + MassGIS local PG confirmed rich.** Local `tiger` DB already has: 69 `tiger.*` tables (edges 28 GB / faces 22 GB / roads 7.8 GB / addr 5.5 GB / arealm 121 MB incl. K2582 cemeteries) + 191 `massgis.*` tables (structures 1.2 GB statewide footprints / mhc_inventory / massdot_roads 307 MB / landuse / buildingfp / boundaries / biketrails / mad_trails). **PostGIS 3.6.2** installed. This means: QGIS can render everything by connecting to db=tiger directly; no MassGIS-JSON intermediate files needed.
- **MACRIS collector gap documented.** SI's local `salemint.entities` has partial MACRIS coverage: full Salem (4,345 rows), Marblehead (2,120), Boston (729), Peabody (211) etc., but **Beverly only 45 rows (district codes BEV.A / BEV.AA / etc. — zero point-buildings)**. `tiger.massgis.mhc_inventory` has the full product; SI's `entities` does not. Ask to SI to re-run the collector for the non-Salem towns.
- **Essex County cemeteries: 145 polygons** in `massgis.landuse2005` filtered by `lu05_desc='Cemetery'`. Not as a dedicated layer. TIGER's `arealm` K2582 is a thinner alternative (~20 in Essex, naming typos, Salem downtown absent).
- **No dedicated sidewalk layer anywhere in local PG.** TIGER S1710/S1820 = 130 recreational/bike paths in Essex. MassGIS = 0 sidewalks. OSM remains the only realistic sidewalk source for Salem.
- **New feedback memory `feedback_adb_install_after_db_rebake.md`** (S156): after `publish-salem-pois.js` rebake, deploy via `adb uninstall && adb install`, NEVER `install -r` — SIGKILL during replace corrupts Room WAL recovery and produces false "no such table" crashes.
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
| **9Y** MassGIS+TigerLine Overlay Integration (re-scoped S157) | **IN PROGRESS (S159)** | L3 parcels + assess ingested (366k + 429k rows, S157). PG schema extended S156. **S159: Kotlin/Room v8→v9 cascade complete — salem_content.db v9 `4ec9ae3528d8f55529cd6875c7b0adef`, Lenovo-verified.** Next: 9Y.3 enrichment script to populate the 9 columns; 9Y.9 polygon geofence runtime; 9Y.Overlay osmdroid dynamic overlays. MapSurface/MapLibre/ripout CANCELLED. |
| **9Z** Historical Maps Time-Slider (re-scoped S157) | **PLANNED** | V1 year slate: 1890, 1906, 1950, 1957 Sanborn. Rides on osmdroid `TilesOverlay` (not MapLibre). SI or self-georeferenced rasters as XYZ tiles. |
| **9Q** Salem Domain Content Bridge | DEFERRED — now consumes 9Y overlay data | building→POI translation, 425 buildings, 202 newspapers. |
| **9R** Historic Tour Mode | DEFERRED — now consumes 9Y overlay data | opt-in chapter-based 1692 tour. Feeds off Phase 9Z time-slider. |
| **10** Production readiness | DEFERRED behind 9Y+9Z+9Q+9R | Firebase, photos, DB hardening, emulator verification. Offline-tile step (10.3) reinstated — osmdroid stays. |
| **11** Branding, ASO, Play Store | Quality over schedule per operator 2026-04-21 | Salem 400+ Oct 2026 is aspirational, not hard. |
| **Cross-project** TigerLine | **PHASE 2 STALLED (2026-04-21)** | 65.7M edges + 35.5M addrs in their PG. LMA no longer blocked on tile delivery (cancelled). Still desirable: MassGIS ingest completion, Salem slice SpatiaLite bundle for the enrichment script. |
| **Cross-project** SalemIntelligence | **Phase 1 KB LIVE** at :8089 | S156 SI-rewrite absorbed (1,400 POIs refreshed). 1,830 POIs / 1,770 narrated. |

**Sessions completed:** 159. Salem 400+ target 2026-09-01 now aspirational — operator has committed to quality over schedule.

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

## POI Inventory (post-S163)

- **PG `salem_pois`:** **2,303 active** (was 1,816 pre-S163). **1,160 hidden** (`default_visible=false`; was 673) — of which **487 are new MHC-sourced hidden POIs** (`id LIKE 'hb_%'`). **503 with `building_footprint_geojson`**. 1,143 visible. 16 existing POIs enriched in S163 with MHC fields.
- **Room DB:** 9.2 MB at `app-salem/src/main/assets/salem_content.db`. v9 schema, identity_hash `4ec9ae3528d8f55529cd6875c7b0adef`. Witch Trials tables intact (npc_bios 49 / articles 16 / newspapers 202).
- **APK size:** ~739 MB debug. `poi-icons/` at 544 MB still the pre-Play-Store audit target.
- **Assets manifest + pre-build verifier:** `app-salem/src/main/assets/ASSETS-MANIFEST.md` + `cache-proxy/scripts/verify-bundled-assets.js`. Pre-existing `salem_tiles.sqlite` path failure from S158 still unfixed.

---

## Pointers to detail

| What | Where |
|---|---|
| Recent session summaries (rolling window) | `SESSION-LOG.md` |
| Older session summaries | `SESSION-LOG-ARCHIVE.md` |
| Live conversation logs | `docs/session-logs/session-NNN-YYYY-MM-DD.md` |
| SalemIntelligence integration + MHC request | `~/Development/SalemIntelligence/docs/lma-*.md` |
| Architecture, tech stack | `CLAUDE.md` and the codebase itself |
| Legal/compliance | `GOVERNANCE.md` |
| Patentable innovations | `IP.md` |
| Monetization strategy | `COMMERCIALIZATION.md` |
| UX/architecture decisions | `DESIGN-REVIEW.md` |
| Test checklist | `CURRENT_TESTING.md` |
| Older STATE.md content | `docs/archive/STATE_removed_2026-04-09.md` |
