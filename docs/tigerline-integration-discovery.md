# TigerLine + MassGIS Integration — Step 0 Discovery Report

**Authored:** 2026-04-21 (LMA Session 156)
**Scope:** Pre-implementation audit of what the TigerLine project and SalemIntelligence's historical-maps corpus actually contain today. Drives sub-step ordering for Phase 9Y + 9Z.
**Companion plan:** `~/.claude/plans/foamy-swimming-teacup.md` (operator-approved 2026-04-21).

---

## TL;DR (three bullets)

- **TigerLine is stalled mid-Phase-2.** ~80% of TIGER import landed (edges 65.7M, addr 35.5M, areawater 2.3M, pointlm 1.1M), but `county`, `tract`, `place`, `cousub`, `faces`, `state` all sit at 0 rows despite the import log marking them complete. MassGIS ingest has not started. Their export scripts for LMA-facing deliverables (7a bundle, 7b routing graph, 7c MBTiles) do not exist yet.
- **Historical maps are ready to go.** SI's `data/historical_maps/` has 509 public-domain items, zero download failures. Best V1 slate: **1890, 1906, 1950, 1957 Sanborn atlases** (300 sheets total, consecutive numbering, mosaic-able per year, LoC IIIF carries bbox metadata — no manual GCP work needed for these four years). Operator already told SI at their S025 close that the time-slider is a committed V1 LMA feature.
- **Parallel tracks.** LMA doesn't need to wait. Room schema additions (9Y.2), MapSurface abstraction (9Y.5), pedestrian graph reader (9Y.4), and MapLibre Native Gradle add all proceed without any upstream data. We land a test renderer on the Lenovo while TigerLine unblocks Phase 2.

---

## 1. TigerLine project state (as of 2026-04-21)

**Location:** `~/Research/TigerLine/`
**Session count:** 1 completed (2026-04-20).
**Repository:** github.com/deanmauriceellis-cloud/TigerLine (private).
**Stack:** PostgreSQL 16.13 + PostGIS 3.6.2 + postgis_topology 3.6.2 + postgis_tiger_geocoder 3.6.2. Database name `tiger`, schema `tiger`, SRID 4269 (NAD83).

### Phase status

| Phase | Status | Notes |
|---|---|---|
| **Phase 1 — Download TIGER/Line 2025** | ✅ COMPLETE | 49 GB across 33,209 zip files (99.96% coverage). 12-file gap documented and accepted (zero impact on Essex County MA). |
| **Phase 2 — PostGIS import** | ⚠️ STALLED | Import process restarted 2026-04-21 02:57 under `setsid + nohup + disown`; died/stalled ~18:42. Process not running now. |
| **Phase 3 — pgRouting topology** | ⬜ NOT STARTED | Blocked by Phase 2 |
| **Phase 4 — Salem slice** | ⬜ NOT STARTED | Blocked by Phase 2 |
| **Phase 5 — Walking router** | ⬜ NOT STARTED | Blocked by Phase 3 |
| **Phase 6 — OSM pedestrian supplement** | ⬜ NOT STARTED | — |
| **Phase 7a — Salem SpatiaLite bundle** | ⬜ NOT STARTED | Export script does not exist (`export_salem_spatialite.sh`) |
| **Phase 7b — Binary routing graph** | ⬜ NOT STARTED | Export script does not exist (`export_kotlin_graph.sh`) |
| **Phase 7c — Salem MBTiles** | ⬜ NOT STARTED | Export script does not exist (`build_salem_tiles.sh`) |
| **Phase 7d — Live SQL API** | ✅ PARTIAL | SQL queries work against the 4 ingested tables today; useful only for LMA backend, NOT for V1 offline client |
| **MassGIS ingest** | ⬜ MANIFEST ONLY | 198 layers discovered, manifest at `scripts/manifests/massgis_layers.tsv`. `download_massgis.sh` + `import_massgis.sh` authored but not run. |

### TIGER row counts (live PG query, TigerLine's box)

| Table | Expected | Actual | Status |
|---|---:|---:|---|
| `tiger.edges` | 65.7M | **65,715,578** | ✅ |
| `tiger.addr` | 35M+ | **35,563,062** | ✅ |
| `tiger.areawater` | 2M+ | **2,254,755** | ✅ |
| `tiger.pointlm` | 1M+ | **1,088,082** | ✅ |
| `tiger.faces` | 1M+ | **0** | ❌ |
| `tiger.tract` | 74k | **0** | ❌ |
| `tiger.county` | 3.2k | **0** | ❌ |
| `tiger.place` | 200k+ | **0** | ❌ |
| `tiger.cousub` | 40k+ | **0** | ❌ |
| `tiger.state` | 56 | **0** | ❌ |

**Critical issue:** `import_log` marks these layers as complete but row counts are 0. This is either a failed transaction that got "marked" spuriously, or an import-log reporting bug. Needs TigerLine-side debugging before Phase 3 can start.

### What LMA can consume from TigerLine TODAY

- **Nothing offline-ready.** Their offline deliverables (7a, 7b, 7c) don't exist.
- **Live SQL (7d)** works for backend tasks — we could do reverse-geocoding of addresses during content curation, but V1 ships fully offline per STATE.md + `feedback/project_v1_offline_only.md`. Not V1-useful.

### What TigerLine owes LMA

Per the vision brief Appendix B timeline (which predates the Phase 2 stall):
1. Phase 7a **Salem SpatiaLite bundle** — buildings, polygons, landmarks, pre-joined. Unknown new ETA.
2. Phase 7b **Salem pedestrian routing binary graph** — TIGER EDGES + MassGIS trails + OSM footways. Unknown new ETA.
3. Phase 7c **Salem MBTiles** — vector tiles for the custom renderer. Per operator direction, TigerLine owns tile generation entirely (both base + historical).
4. **MassGIS ingest** — priority per operator direction. 26 Salem-critical layers (MHC historic inventory, building footprints, address points, historic districts, etc.) need to land in TigerLine's `massgis.*` schema before any of 7a/7b/7c can happen.

### OMEN coordination touchpoints

- `~/Development/OMEN/notes/tigerline.md` — standing notes (T001–T008). T001/T002 closed.
- `~/Development/OMEN/reports/tigerline/session-001-2026-04-20.md` — TigerLine's S001 report.
- No cross-project notes filed yet from TigerLine to LMA. This plan generates the first formal request at session-close.

---

## 2. SalemIntelligence historical maps inventory

**Location:** `~/Development/SalemIntelligence/data/historical_maps/` (4.84 GB, 509 items).
**Manifest:** `manifest.json` (381 KB, all items catalogued, all `license: "public_domain"`, 0 download failures).
**Note:** STATE.md's "503/509" reference is stale — current manifest shows 100% success.

### Distribution by source

| Source | Items | Total size | Format | Georef status |
|---|---:|---:|---|---|
| `loc_maps/` | 11 | 108 MB | JP2 | ❌ No embedded coords; LoC IIIF carries bbox metadata |
| `loc_sanborn/` | 300 | 1.85 GB | JP2 | ❌ No embedded coords; LoC IIIF carries bbox per sheet |
| `usgs_historical/` | 198 | 3.0 GB | PDF (GeoPDF) | ✅ Embedded WGS84 coords per USGS standard |

### Year distribution — highlights

Full histogram in appendix. Dense years:

| Year | LoC maps | Sanborn sheets | USGS | Total | Notes |
|---|---:|---:|---:|---:|---|
| **1890** | 1 | **43** | 0 | 44 | First Sanborn atlas; Salem industrial peak |
| **1906** | 0 | **80** | 0 | 80 | Expanded coverage; pre-WWI commercial |
| 1943–1949 | 0 | 0 | 43 | 43 | WWII-era USGS only |
| **1950** | 0 | **83** | 3 | 86 | Post-WWII Sanborn; highest coverage |
| **1957** | 0 | **94** | 0 | 94 | Final Sanborn; finest detail |

Sparse-but-historic years (for static narrative, not slider): 1626 (1 item), 1813 (1), 1853 (1). Cannot support slider rendering at these years.

### V1 time-slider recommendation

**Four years:** 1890, 1906, 1950, 1957 — all Sanborn atlases.

**Rationale:**
- All four are 100% consecutive-numbered (1 to N), mosaic-able without gaps
- All four cover downtown Salem at Sanborn's 1:600 building-detail scale
- LoC IIIF metadata provides geographic bounding boxes per sheet — **no manual GCP work needed**
- Bracket Salem's commercial arc: industrial → pre-WWI → post-WWII → pre-suburbia
- Public domain verified (all Sanborn pre-1977 = PD)

**Optional fifth:** Blend USGS 1893 Boston quad (34 sheets) for regional geographic context at earlier-era tour stops. USGS PDFs are already georeferenced, minimal additional work.

**Drop:** 1626, 1813, 1853 — too sparse for the slider; embed them in static narrative surfaces instead.

### Who does the georectification + tiling?

Three candidates per the operator's approved plan:

1. **TigerLine** — owns all tile generation per operator direction (§D3 of plan). If they take the Sanborn atlases, we consume MBTiles directly.
2. **SalemIntelligence** — has the catalog. Operator said at S025 close: "go back in time" is committed for LMA V1. SI's own docs draft an `/api/intel/historical-maps` endpoint. They could generate tiles before serving them.
3. **LMA** — fallback if neither upstream takes it. `gdal2tiles.py` + GDAL is straightforward for USGS PDFs. Sanborn mosaicking is non-trivial but doable.

**Proposed ownership (pending OMEN coordination):**
- TigerLine owns base vector MBTiles (modern Salem). **Confirmed by operator 2026-04-21.**
- SalemIntelligence OR TigerLine owns historical raster MBTiles. SI has the catalog; TigerLine has the tooling. Either works. OMEN should broker.
- LMA consumes whichever comes and integrates into the time-slider UI. Zero tile generation on our side.

---

## 3. LMA-side implications

### Data dependencies

| Work | Depends on | Status |
|---|---|---|
| MassGIS-driven POI enrichment | TigerLine MassGIS ingest | BLOCKED — MassGIS not started on TigerLine's box |
| Offline SpatiaLite bundle ingest | TigerLine Phase 7a export | BLOCKED — script doesn't exist |
| Binary pedestrian routing graph consumer | TigerLine Phase 7b export | BLOCKED — script doesn't exist |
| Base vector MBTiles consumer | TigerLine Phase 7c export | BLOCKED — script doesn't exist |
| Historical MBTiles consumer | TigerLine OR SI pipeline | BLOCKED — ownership unresolved |

### What LMA CAN do in parallel (no upstream data required)

Sub-step | Readiness | Notes
---|---|---
**9Y.2 — Room schema v8 → v9** | ✅ READY | Additive-only columns (`building_footprint_geojson`, `mhc_id`, `mhc_year_built`, `mhc_style`, `mhc_nr_status`, `mhc_narrative`, `canonical_address_point_id`, `local_historic_district`, `parcel_owner_class`). Nulls fine until data arrives. Repurpose `geofence_shape` + `corridor_points`.
**9Y.5 — MapSurface abstraction + MapLibre Gradle add** | ✅ READY | `MapSurface` interface, `OsmdroidMapSurface` wrapper, empty `MapLibreMapSurface` skeleton. Flag-gated. No user-facing change.
**9Y.4 — Binary pedestrian graph reader** | ⚠️ PARTIAL | Can write the reader + Dijkstra runner against a synthetic test graph. Real data lands later.
**9Y.7.1 — MapLibre base-map smoke test** | ✅ READY | With a placeholder MBTiles (trivial 1-tile pink-square), verify MapLibre renders on the Lenovo. Sanity check before the real tileset arrives.
**9Y.9 — Polygon geofence runtime** | ✅ READY | Add `pointInPolygon()` to `NarrationGeofenceManager`, run against synthetic building footprints seeded in a test POI. Activates when real polygons arrive.
**Task #9 (S154 carry-forward) — Find WebView button strip** | ✅ READY | Unrelated to TigerLine but still owed from S154.
**Form TX copyright filing** | ✅ READY | Operator-owed; hard deadline 2026-05-20.

### What LMA should NOT start yet

- Enrichment scripts (`enrich-pois-from-tigerline.js`) — no data to query
- Publish-salem-pois column expansion — wait until enrichment data exists
- Actual osmdroid ripout (9Y.8) — only after 9Y.5–9Y.7 ports are verified

---

## 4. Decisions surfaced by discovery (operator input wanted)

1. **TigerLine Phase 2 unblock priority.** TigerLine's stall is the critical-path blocker. Options:
   - **(a)** File OMEN note asking TigerLine to prioritize Phase 2 debug + MassGIS start. Recommended.
   - **(b)** LMA does its own MassGIS download + ingest to a separate PG schema (duplicate work, two sources of truth, bad).
   - **(c)** Wait silently. Bad — we'd be surprised in July.

2. **Historical MBTiles ownership.** TigerLine vs SalemIntelligence vs LMA. Per §2 above, TigerLine per operator's tile-generation policy, but SI has the catalog. Needs a one-line OMEN decision.

3. **LMA parallel-track scope.** Given the 5+ week wait for TigerLine deliverables, how much of 9Y.2 / 9Y.4 / 9Y.5 / 9Y.9 do we pull forward?
   - **Aggressive:** all four in parallel. Lands a functional MapLibre renderer on Lenovo before TigerLine data arrives.
   - **Conservative:** 9Y.2 schema only; defer renderer until data is real.
   - **Recommended: aggressive.** Operator said "provide the best product when it is done, not a date" — the parallel work reduces total elapsed time and surfaces renderer issues early.

4. **TIGER data we also genuinely need.** The vision brief leans heavily on MassGIS (MHC inventory, building footprints, address points) but we still need TIGER's `cousub` (Salem boundary) and `edges` (fallback roads where MassGIS trails are absent). Phase 2 must complete for those. Confirms D1 above.

5. **Time-slider year slate.** Plan proposed 5 years (1813, 1853, 1890, 1906, 1950). Discovery says 1813 and 1853 have only one map each — not enough for a slider. Revised recommendation: **1890, 1906, 1950, 1957** (four Sanborn atlases). Embed 1626/1813/1853 as static narrative overlays instead of slider years.

---

## 5. Proposed sub-step re-ordering (delta from approved plan)

Based on discovery:

**New Step 0.1 (this session or next) — OMEN request file.** Write one consolidated OMEN report documenting LMA's ask to TigerLine (unblock Phase 2, prioritize MassGIS, produce Phase 7a bundle, Sanborn tile ownership) and to SI (confirm SI or TigerLine takes historical tiles, produce per-year MBTiles).

**Start in parallel (no TigerLine dependency):**
- 9Y.2 (Room schema v9, additive columns)
- 9Y.5 (MapSurface abstraction + Gradle MapLibre add)
- 9Y.7.1 (MapLibre base-map smoke test on Lenovo with placeholder MBTiles)
- 9Y.9 (polygon geofence runtime with synthetic data)

**Blocked on TigerLine:**
- 9Y.1 (TigerLine ingest pipeline, cache-proxy-side)
- 9Y.3 (enrich-pois-from-tigerline.js)
- 9Y.4 (binary pedestrian graph — reader readiness OK, real graph awaits)
- 9Y.6 (base vector MBTiles consumer — placeholder tile OK, real tile awaits)
- 9Y.7.2–9Y.7.12 (per-overlay port — can proceed against placeholder)

**Blocked on TigerLine OR SI (historical tiles):**
- All of 9Z (time-slider)

**Updated sequence:**
1. **Session 157** — OMEN ask file. Start 9Y.2 + 9Y.5 in the same session.
2. **Sessions 158-160** — 9Y.7 per-overlay port, running on osmdroid today, MapLibre-ready once tiles arrive.
3. **Session ~161** (depends on TigerLine) — 9Y.1 ingest script once MassGIS schema lives on their box.
4. **Session ~163** (depends on TigerLine) — 9Y.3 enrichment + Room bake + smoke test with real polygons.
5. **Session ~165** — 9Y.8 osmdroid ripout.
6. **Session ~166+** — 9Z.1 + 9Z.2 time-slider (depends on historical-MBTiles delivery).

---

## 6. Next actions (this session, S156, remaining)

1. Commit this discovery report + the plan file + updated live log.
2. Decide on action #1 above (OMEN request to TigerLine). Draft in next session.
3. If operator approves parallel-track aggressive approach: start 9Y.2 schema work in S157.

---

## Appendix A — Full historical-maps year histogram

See SalemIntelligence manifest for complete distribution. Summary:

- 1626, 1813, 1853, 1886, 1888, 1890\*, 1892, 1893, 1903, 1906\*, 1917, 1918, 1919, 1943, 1944, 1945, 1946, 1947, 1948, 1949, 1950\*, 1951, 1952, 1953, 1954, 1955, 1956, 1957\*, 1958, 1960–1989 (sparse), 1991–2009 (sparse)
- Asterisked years are dense Sanborn atlases (≥43 sheets each)
- V1 slider targets: 1890, 1906, 1950, 1957

## Appendix B — TigerLine deliverables cheat-sheet

- **Request to TigerLine (via OMEN):** unblock Phase 2 (fix the `tract = 0 rows` bug), prioritize MassGIS priority-1 layers (26 of 198), produce Phase 7a SpatiaLite bundle for Salem + 5mi buffer, produce Phase 7b pedestrian routing graph, produce Phase 7c base vector MBTiles for Salem.
- **Request to SalemIntelligence (via OMEN):** confirm ownership of historical MBTiles generation OR relay to TigerLine. If SI takes it, produce per-year MBTiles for 1890, 1906, 1950, 1957 Sanborn atlases using LoC IIIF metadata for georectification.
- **LMA delivery format contract:** MBTiles (SQLite) for tile layers, binary adjacency format for routing graph, Room-compatible SQLite for spatial DB.
