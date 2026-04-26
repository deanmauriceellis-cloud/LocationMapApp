# SalemTourMapRouting — TigerLine handoff to LocationMapApp Salem Tour

**Source project:** TigerLine (`~/Research/TigerLine`)
**Consumer:** LocationMapApp Salem Tour
**Database:** local PostgreSQL, database `tiger`, schemas `tiger` (functions) + `salem` (graph)
**SRID:** 4269 (NAD83). For Salem MA the offset from WGS84 (4326) is sub-millimetre — **safe to treat as interchangeable** for tour-overlay purposes; reproject only if your client toolchain requires it.
**Status:** Phase 5 + Phase 6 complete. Production-ready for Salem-area walking routes, with the MassGIS biketrails supplement merged into the routing graph.

---

## What this gives you

A point-to-point and ordered multi-waypoint **walking router** over the Salem-area street network, returning a single GeoJSON LineString plus distance and duration. You pass lat/lng pairs in, you get a polyline back. No external services, no client-side routing logic — one SQL call.

Coverage: **Essex County, MA** (FIPS 25/009) — Salem proper plus all neighbouring towns. **85,067 edges** (84,568 TIGER + 499 MassGIS biketrails segments), of which **50,835 are flagged walkable**; **60,436 graph nodes**, of which **40,593 are walkable**. Supplement edges carry the synthetic `mtfcc='PEDPT'` for traceability.

---

## Connection details

```
host:     localhost
database: tiger
schema:   tiger     (functions live here — exposed contract)
schema:   salem     (underlying graph — read access only; do not mutate)
user:     <whatever your LMA pg user is — peer auth on the dev box for witchdoctor>
```

The functions are in the `tiger` schema. You do not need to query `salem.*` directly to get a route; you can if you want to do custom analysis.

---

## Public functions (the contract)

All three live in the `tiger` schema and are SQL-callable. Inputs are plain `double precision` lat/lng (or geometry array for the multi variant). Outputs are `(geojson text, distance_m double precision, duration_s double precision)`.

Walking-speed assumption: **1.4 m/s** (≈ 5.0 km/h, ≈ 3.1 mph) — average adult pace. `duration_s = distance_m / 1.4`. If you need to display a different pace, recompute on the client from `distance_m` rather than re-running the route.

### 1. `tiger.nearest_walkable_node(lat, lng) → BIGINT`

KNN snap helper. Returns the node ID of the nearest graph vertex that participates in at least one walkable edge. Sub-millisecond.

You normally don't call this directly — `route_walking` and `route_walking_multi` call it internally — but it's exposed for cases where you want to pre-snap a POI catalogue or detect "user is too far from any street to route from" (returns `NULL` only if the entire walkable graph is empty, which won't happen).

```sql
SELECT tiger.nearest_walkable_node(42.5219, -70.8967);  -- Salem Common
```

### 2. `tiger.route_walking(from_lat, from_lng, to_lat, to_lng)` — point-to-point

Returns one row.

| Column        | Type     | Meaning                                                     |
|---------------|----------|-------------------------------------------------------------|
| `geojson`     | `text`   | GeoJSON geometry — typically `LineString`, occasionally `MultiLineString` if the merge can't dissolve a junction |
| `distance_m`  | `float8` | Route length in metres                                      |
| `duration_s`  | `float8` | Estimated walking time in seconds (`distance_m / 1.4`)      |

**Returns `(NULL, 0, 0)` when:** start and end snap to the same node, or no path exists between them.

```sql
-- Salem Common → House of Seven Gables
SELECT geojson, distance_m, duration_s
FROM tiger.route_walking(42.5219, -70.8967, 42.5226, -70.8845);
-- ≈ 960 m, ≈ 686 s (~11.4 min)
```

### 3. `tiger.route_walking_multi(points geometry[])` — ordered multi-stop

Routes pairwise between each consecutive pair of points and concatenates the segments into one polyline. Returns the same shape as `route_walking`.

Input is an **ordered** array of point geometries in SRID 4269. Build them with `ST_SetSRID(ST_MakePoint(lng, lat), 4269)`. Order matters — this is a fixed-order traversal, not a TSP solver.

**Returns `(NULL, 0, 0)` when:** array has fewer than 2 points, or any segment fails to route.

```sql
-- Salem Common → Witch House → Peabody Essex Museum
SELECT geojson, distance_m, duration_s
FROM tiger.route_walking_multi(ARRAY[
  ST_SetSRID(ST_MakePoint(-70.8967, 42.5219), 4269),
  ST_SetSRID(ST_MakePoint(-70.9000, 42.5210), 4269),
  ST_SetSRID(ST_MakePoint(-70.8915, 42.5225), 4269)
]);
-- ≈ 1269 m, ≈ 906 s (~15.1 min)
```

---

## Verified test routes (from Phase 5, 2026-04-24)

| From → To                                      | distance_m | minutes | Sanity check          |
|------------------------------------------------|------------|---------|-----------------------|
| Salem Common → House of Seven Gables           | 960        | 11.4    | matches Google Maps   |
| Witch House → Burying Point Cemetery           | 313        | 3.7     | matches Google Maps   |
| Peabody Essex Museum → Derby Wharf             | 1,226      | 14.6    | matches Google Maps   |
| Salem Commuter Rail → Museum Place             | 536        | 6.4     | matches Google Maps   |
| Danvers cross-town                             | 1,704      | 20.3    | matches Google Maps   |
| Multi: Common → Witch House → PEM              | 1,269      | 15.1    | matches Google Maps   |

---

## Underlying graph (read-only reference)

If you ever need raw edges for snapping debug or custom rendering:

### `salem.edges` — 85,067 rows

TIGER edges in Essex County (84,568) plus MassGIS biketrails supplement (499, `mtfcc='PEDPT'`), all with pgRouting columns.

| Column         | Type                          | Notes                                                          |
|----------------|-------------------------------|----------------------------------------------------------------|
| `gid`          | integer (PK)                  | edge id                                                        |
| `tlid`         | bigint                        | TIGER line id                                                  |
| `mtfcc`        | varchar(5)                    | TIGER feature class (S1400 local road, S1740 service, etc.)   |
| `fullname`     | varchar(100)                  | street name                                                    |
| `the_geom`     | `MultiLineString, 4269`       | geometry (GIST-indexed)                                        |
| `source`       | bigint                        | from-node id (FK → `edges_vertices_pgr.id`)                    |
| `target`       | bigint                        | to-node id                                                     |
| `length_m`     | double precision              | edge length, metres (geography-accurate)                       |
| `walk_cost`    | double precision              | `length_m / 1.4` — duration in seconds at walking pace         |
| `reverse_cost` | double precision              | `walk_cost` if walkable, `1e9` otherwise                       |
| `walkable`     | boolean                       | filtered by MTFCC: excludes interstates and limited-access; supplement edges are always `TRUE` |

Synthetic MTFCC values used by the supplement:
- `PEDPT` — MassGIS biketrail / shared-use path (Phase 6, 2026-04-25)

### `salem.edges_vertices_pgr` — 60,436 rows

| Column          | Type                | Notes                                           |
|-----------------|---------------------|-------------------------------------------------|
| `id`            | bigint (PK)         | node id                                         |
| `the_geom`      | `Point, 4269`       | node location (GIST-indexed)                    |
| `walkable_node` | boolean             | `TRUE` for the 40,593 nodes on walkable edges  |

---

## Performance notes

- **Single-pair route** in Salem: typically 30–150 ms. Dijkstra over 50K edges with a small node-budget cone — fast.
- **Multi-stop with N waypoints:** roughly N − 1 × single-pair. The implementation uses `LATERAL pgr_dijkstra` per segment.
- **KNN snap** (`nearest_walkable_node`): sub-millisecond. The partial GIST index on `walkable_node = TRUE` keeps the search cone tiny.
- All routing is **undirected** — pedestrians traverse every edge in both directions. The `reverse_cost` column and `directed := FALSE` flag both encode this. (Don't change either.)

---

## Pedestrian-path coverage (what's in the supplement)

As of Phase 6 (2026-04-25), the routing graph includes **499 segments / 151 km** of `massgis.biketrails` data clipped to Essex County — MassDOT-curated shared-use paths and multiuse trails (e.g., Mayor Anthony Salvo Bike Path, Jefferson at Salem Station Multiuse Path, Leslie's Retreat Park trail). Endpoints were snapped to TIGER street nodes within 5 m where possible (439 of 998 endpoint snaps connected to existing streets; 331 new vertices created for unsnapped endpoints, forming small biketrail-only components for paths that don't touch the road grid).

What the supplement gives you:
- Bike paths and shared-use multiuse trails are routable.
- The router will choose a biketrail when it's the shortest path.

What's still missing (deferred):
- **`massgis.mad_trails`** (~22 km in Salem) — has `src_data='OSM'`, deferred pending operator decision on OSM-provenance interpretation. This is the layer that would cover Salem Common internal diagonals if any are missing today.
- **Pedestrian-only paths inside parks/cemeteries** that aren't in `biketrails` (e.g., Old Burying Point cut-throughs, Pickering Wharf walkway).

If a specific tour stop turns out to lack a clean walking path, file it against TigerLine and we can revisit `mad_trails` with concrete coverage evidence.

## Known limitations (read before shipping)

1. **No OSM data.** TigerLine is intentionally TIGER + MassGIS only. If LMA needs OSM-quality pedestrian detail (sidewalks, crossings, indoor paths), that's an LMA-side concern — TigerLine won't ingest OSM.
2. **Coverage is Essex County, not just Salem city.** Routing across the county border (Beverly, Marblehead, Danvers, Peabody) works fine. Routing *out of* Essex County will fail or snap weirdly — don't pass coordinates outside MA Essex County.
3. **No turn restrictions, no one-ways, no elevation.** Walking, so this is by design.
4. **Speed is fixed at 1.4 m/s on every edge type.** Biketrails, streets, and supplement paths all traverse at the same pace. `pgr_dijkstra` minimizes `Σ length / 1.4`, which equals shortest-distance under uniform speed. If LMA ever wants biketrails preferred or stairs penalised, that's a per-`mtfcc` cost multiplier on the TigerLine side — file a request.
5. **`walkable` filter is MTFCC-based**, not granular. It excludes interstates and ramps; it does not exclude private drives, alleys, or rail-bed conversions. In practice this is fine for Salem. Supplement edges (`mtfcc='PEDPT'`) are always walkable.
6. **Supplement-only "islands."** 331 new vertices were created for biketrail endpoints that didn't snap to a street within 5 m. These form small disconnected components. A route that snaps to one of these will fail (`route_walking` returns `(NULL, 0, 0)`) — but `nearest_walkable_node` always prefers the closest of *any* walkable node, so in practice this is only a risk if a tour stop's lat/lng happens to fall within a few metres of an isolated biketrail endpoint and 50+ m from the nearest street. Effectively a non-issue for surface POIs.

---

## Suggested LMA usage pattern

Cache the GeoJSON per (tour-version, leg-index) — the underlying graph only changes when TIGER vintage rolls or Phase 6 lands, and TigerLine will signal that via OMEN if it does. For a multi-stop tour where the operator may reorder stops in the editor, prefer one `route_walking_multi` call over N `route_walking` calls — fewer round-trips and the merged geometry is cleaner at the joins.

```sql
-- Parameterised LMA-side prepared statement
PREPARE salem_tour_route (geometry[]) AS
SELECT geojson, distance_m, duration_s
FROM tiger.route_walking_multi($1);

EXECUTE salem_tour_route(ARRAY[
  ST_SetSRID(ST_MakePoint(:lng1, :lat1), 4269),
  ST_SetSRID(ST_MakePoint(:lng2, :lat2), 4269),
  ST_SetSRID(ST_MakePoint(:lng3, :lat3), 4269)
]);
```

---

## Versioning / change protocol

This is a shared-engine contract per OMEN's `standards/COMPATIBILITY.md`. The function signatures, return shape, and `salem.edges` / `salem.edges_vertices_pgr` schemas above will not change without an OMEN-coordinated cutover. If LMA needs new fields on the return (e.g., per-segment break, street names along the route, turn-by-turn), file an OMEN engine-change request — don't shim it on the consumer side.

---

_Document version 1.1 — TigerLine session 006, 2026-04-25. Functions defined in `sql/route_functions.sql`; TIGER graph built by `scripts/build_routing_topology.sh`; biketrails supplement built by `scripts/build_pedestrian_supplement.sh`._
