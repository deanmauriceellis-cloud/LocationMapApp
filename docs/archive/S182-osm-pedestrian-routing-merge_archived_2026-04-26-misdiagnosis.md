# S182+ Plan — OSM Pedestrian Network Merge into salem.edges

> **ARCHIVED 2026-04-26 (S182 open) — DO NOT EXECUTE.** This plan was based on a misdiagnosis. The S181 screenshot complaint (Heritage tour polyline crossing buildings) was attributed to the routing graph, but per-leg Find→Directions on the same graph routes cleanly between the same POIs. The fault is in the tour-rendering path (`routeMulti` / Heritage tour polyline builder), not the graph and not the data sources. The S181 OSM-policy reversal in `feedback_no_osm_use_local_geo.md` has been retracted. Kept here only as a record of the (wrong) direction taken.

**Status:** drafted S181 (2026-04-26), pending operator approval at S182 start.
**Scope:** add 1,882 OSM footway/path/pedestrian/steps edges to the runtime routing graph so the router prefers actual pedestrian infrastructure (sidewalks, alleys, park paths, stairs) over TIGER street centerlines where available.
**Estimated cost:** 2-3 sessions if topology merge goes cleanly; +1 session if connectivity tuning is needed.

---

## Why this is happening

S181 smoke test of the V1 release AAB on Lenovo surfaced a routing artifact: green polyline for the Salem Heritage Trail visually grazed historic-building polygons in downtown Salem (Salem Green / Visitor Center area). Investigation:

- The router was doing its job correctly — outputting the shortest path on the bundled `salem.edges` graph.
- The 14 walkable edges that cut historic-building polygons across all of Salem were a **rendering artifact**, not a routing bug. The user walking the route would walk on the actual sidewalk by the actual building, not into a wall.
- BUT — the underlying issue is that `salem.edges` is 99% TIGER street centerlines. In downtown Salem, only ~8% of walkable edges are pedestrian-tagged. The router has no choice but to put walking routes on car streets.
- `osm.salem_pedestrian_edges_resolved` has 1,882 ready-to-merge pedestrian edges (~120 km of footway/path/pedestrian/steps in the Salem bbox), populated by the S178 ingest pipeline.

Operator decision (S181): "if we require OSM to do this right, then let's change our direction and allow OSM for our routing needs." Memory `feedback_no_osm_use_local_geo.md` amended to permit broader pedestrian-network OSM merge (build-time only; runtime stays offline).

---

## Goals

1. Merge `osm.salem_pedestrian_edges_resolved` into `salem.edges` such that pgRouting can route across the combined graph end-to-end.
2. Existing routing quality must not regress — every Salem Heritage Trail / Witch Trial Sites / Salem Essentials tour leg that currently routes must still route.
3. Routing quality must measurably improve in downtown Salem — for a defined set of test pairs, the router should choose pedestrian infrastructure when shorter/equal in walking time.
4. The runtime app (`app-salem`) needs **zero code changes**. The whole improvement is in the bundled routing graph.

## Non-goals

- Do not add OSM `service` / `residential` / `secondary` / `primary` road categories. They duplicate TIGER S1100/S1200/S1400 and create graph noise without quality gain.
- Do not address the visual misregistration of route polyline vs. building polygons. That's a separate cosmetic ticket. (And mostly disappears as a side effect when routes prefer footways over street centerlines.)
- Do not change the basemap render. Witchy basemap stays as-is.
- Do not address the 14 known building-cutting edges via per-edge geometry edits. That's a different (and lower-value) approach we're consciously not taking.

---

## Approach — topological merge, not bulk import

The S178 first attempt at a broad merge used `pgr_createTopology` style bulk ingest. It pulled in 2,071 OSM features and produced **608 disconnected graph components**, breaking 57 tour legs that previously routed cleanly. Don't repeat that. Specifically:

- `pgr_createTopology` on a mixed dataset creates a fresh node anywhere two edges share a coordinate. OSM endpoints rarely share exact coordinates with TIGER endpoints (~3-15m offset is typical), so most OSM edges end up on islands disconnected from the TIGER backbone.
- The right pattern is **node resolution before edge insertion**: for each OSM endpoint, look up the nearest existing `salem.edges_vertices_pgr` node within a tolerance. If found, reuse that node id. If not found, create a new node — but only then.

The S178 pipeline already implements node resolution for the surgical 3-osm_id allowlist. The S182+ work scales that up to the full 1,882-edge resolved set.

### Phase breakdown

#### Phase 1 — node resolution at scale (1 session)

- Scale the S178 `salem_node_resolution` / `salem_picked_resolution` pattern to the full `salem.salem_pedestrian_edges_resolved` set.
- For each OSM edge endpoint, find nearest TIGER node within 5m (configurable) and reuse, else create new node.
- Output: a `osm.salem_pedestrian_resolved_to_salem_nodes` table mapping OSM endpoints → final node ids in `salem.edges_vertices_pgr` (existing or new).
- Validate: count distribution of (reused vs. new) nodes. Expect heavy reuse — if >50% are new, tolerance is too tight or the OSM data doesn't actually align with TIGER.

#### Phase 2 — edge ingest with proper source/target (1 session)

- Insert 1,882 rows into `salem.edges` with:
  - `mtfcc='OSMP'` (consistent with S178 surgical rows)
  - `walkable=TRUE`
  - `walk_cost = length_m / pedestrian_pace`
  - `source` / `target` from Phase 1 mapping
  - `length_m` from OSM (already computed in resolved table)
  - `the_geom` cast from OSM `LineString` to TIGER's `MultiLineString(4269)` form
  - `fullname` from OSM `name` (often null for footways — that's fine)
  - Origin tracking: tag with `tlid` set to a sentinel value (e.g., negative osm_fid) so OSMP rows are findable later
- After ingest, run `pgr_strongComponents` on the full graph. **Acceptance gate: ≤ 5 components** covering the Salem bbox (one giant component + small islands for genuinely-isolated paths). If components ≥ 50, abort and tighten Phase 1 tolerance / investigate.

#### Phase 3 — re-bake routing bundle + parity test (1 session)

- Re-run `tools/routing-bake/bake-salem-routing.py`. Bundle should grow from 47,704 to roughly 49,500 edges.
- Add a parity test or extend `routing-jvm/src/test/kotlin/com/example/locationmapapp/core/routing/RouterParityTest.kt`:
  - For each existing Salem tour, compute the multi-stop route on the new bundle. Must succeed (no null returns, no missing legs).
  - Capture before/after distance per tour. Acceptable bounds: ±10% per leg. >10% shorter = good (footway shortcut found). >10% longer = investigate (was the prior route on a now-removed edge?).
- Acceptance: all existing tour legs route, no leg distance increases by >10%.

#### Phase 4 — rebuild AAB + Lenovo smoke test (1 session, can fold into Phase 3)

- `./gradlew :app-salem:bundleRelease` and `:app-salem:assembleRelease`.
- Eyes-on smoke test on Lenovo (Salem Heritage Trail tour, point-to-point Find→Directions in downtown).
- Visual confirm: routes prefer pedestrian infrastructure where it exists (e.g., walking through Salem Common via footways instead of around it on Washington Street).
- Memory + STATE.md updates. Session end.

---

## Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Node resolution tolerance too loose → wrong edges joined | Med | High (silently bad routes) | Start tight (3m), widen only with manual review of unmatched-endpoint count |
| Node resolution tolerance too tight → too many disconnected components | Med | Medium (Phase 2 acceptance gate catches it) | Phase 2 hard-gates on `pgr_strongComponents` ≤ 5 |
| OSM data has stair edges that TIGER walkable cost model doesn't reflect (e.g., 50 vertical-foot staircase costed as flat 10m walk) | High | Low (router picks it but route is "harder than expected") | Document; defer step-cost-multiplier as V2 |
| Tour leg parity test reveals an existing tour now routes through a footway that closes seasonally / privately (e.g., a shop alley) | Low | Medium | If detected, blacklist that osm_id and re-merge |
| OSM data licensing — ODbL share-alike — affects bundled distribution | Low | Already addressed | Witchy bake already ships under "© OpenMapTiles © OpenStreetMap contributors" attribution per `tools/tile-bake/README.md`. Routing bundle is derived data; same attribution applies and goes in the app's About screen if not already there |
| OSM coverage in Salem is uneven (e.g., Derby St has footways but Bridge St doesn't) → some routes prefer footways, others don't | High | Low (still better than zero footway coverage) | Document; over time, contribute back missing footways to OSM upstream |

---

## Validation gates (must pass before each phase exits)

- **Phase 1 exit:** > 50% of OSM endpoints resolve to existing TIGER nodes. < 5% resolve ambiguously (multiple TIGER nodes within tolerance — should pick the closest deterministically).
- **Phase 2 exit:** `pgr_strongComponents` over `salem.edges WHERE walkable=TRUE` returns ≤ 5 components within Salem bbox. Each component ≥ 100 edges.
- **Phase 3 exit:** All existing tour legs (Salem Heritage Trail 10 stops + Witch Trial Sites + Salem Essentials) route end-to-end. No leg increases distance by >10%.
- **Phase 4 exit:** Lenovo smoke test: Heritage Trail tour route renders + walks correctly. At least one downtown leg visibly chooses a footway/path that the prior bundle didn't.

---

## Rollback plan

The OSMP rows are tagged for traceability. A single `DELETE FROM salem.edges WHERE mtfcc='OSMP';` reverts to TIGER-only routing. The ingest pipeline is idempotent — re-running it from a cleaned table reproduces the merge.

---

## Open questions for operator at S182 start

1. **Tour parity tolerance** — is ±10% per-leg distance the right gate, or should it be tighter? Looser?
2. **Routing-bundle bake automation** — should Phase 3 also wire the bake into the Gradle build (per the existing carry-forward "admin → build pipeline auto-bake" item from S180)? Could combine the two.
3. **OSM PBF refresh cadence** — current PBF is 2026-04-22 (Apr 23 download). Do we lock to that vintage for V1 ship, or refresh before bundleRelease? The Witchy basemap is on the same vintage; refreshing one means refreshing both.

---

## Cross-references

- Memory: `feedback_no_osm_use_local_geo.md` (S181 amendment recording the policy reversal)
- Memory: `feedback_v1_no_external_contact.md` (runtime stays offline — unchanged)
- Memory: `project_witchy_tile_bake.md` (the OSM tile-bake precedent)
- S178 live log: `docs/session-logs/session-178-2026-04-25.md` (the surgical 3-osm_id ingest)
- S181 live log: `docs/session-logs/session-181-2026-04-26.md` (the policy reversal + this plan)
- Code: `cache-proxy/scripts/ingest-osm-pedestrian-into-salem-edges.sql` (S178 surgical pattern to scale up)
- Code: `tools/routing-bake/bake-salem-routing.py` (Phase 3 re-bake target)
- Code: `routing-jvm/src/test/kotlin/com/example/locationmapapp/core/routing/RouterParityTest.kt` (Phase 3 parity-test extension target)
