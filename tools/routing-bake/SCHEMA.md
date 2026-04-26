# salem-routing-graph.sqlite — bundle schema

This is the **consumer contract** for the on-device Salem routing graph. The Android APK
(via `core/.../routing/SalemRoutingGraph.kt`) and the cache-proxy web router both consume
this file. Both must continue to work after every re-bake.

The bundle is produced by `bake-salem-routing.py` from TigerLine's `salem.edges` +
`salem.edges_vertices_pgr` tables, clipped to a Salem-area bbox (3-mile buffer around the
POI envelope). See `SalemTourMapRouting.md` for the upstream TigerLine contract.

## Versioning

`meta.schema_version` is a small integer. Consumers must:
- Read `schema_version` at load time.
- Refuse to load if the version is unknown (log + fall back to no-routing UI).
- Always prefer the highest-known version.

Current version: **1**.

## Tables

### `nodes`

Every graph vertex inside the bbox.

| Column     | Type    | Notes                                                |
|------------|---------|------------------------------------------------------|
| `id`       | INTEGER | Primary key. Matches TigerLine's `salem.edges_vertices_pgr.id`. Stable across rebakes for nodes that survive both vintages. |
| `lat`      | REAL    | WGS84-equivalent (NAD83 SRID 4269 — sub-mm offset, treat as WGS84). |
| `lng`      | REAL    | Same.                                                |
| `walkable` | INTEGER | 0 or 1. Mirrors `walkable_node` from upstream.       |

### `nodes_rtree`

SQLite virtual R-tree over **walkable nodes only** (the only thing the router snaps to).

| Column   | Type | Notes              |
|----------|------|--------------------|
| `id`     | INT  | FK → `nodes.id`.   |
| `min_lat`, `max_lat` | REAL | Bbox is degenerate per node — `min_lat = max_lat = nodes.lat`. |
| `min_lng`, `max_lng` | REAL | Same with `lng`.  |

KNN snap query pattern (must match TigerLine's `tiger.nearest_walkable_node()`):

```sql
SELECT n.id, n.lat, n.lng,
       (n.lat - :lat) * (n.lat - :lat) + (n.lng - :lng) * (n.lng - :lng) AS d2
FROM nodes_rtree r
JOIN nodes n ON n.id = r.id
WHERE r.min_lat BETWEEN :lat - 0.005 AND :lat + 0.005
  AND r.min_lng BETWEEN :lng - 0.007 AND :lng + 0.007
ORDER BY d2 ASC
LIMIT 1;
```

(0.005 lat ≈ 555m; 0.007 lng ≈ 575m at 42.5°. Expand the window if the first query returns 0.)

**Critical:** distance must be **planar in SRID-4269 degrees** (raw squared diff of lat
and lng), NOT haversine or any geographic-accurate metric. TigerLine's upstream function
uses PostGIS's `<->` operator on point geometries, which is also planar in degrees. At 42.5°
latitude this slightly over-weights longitude differences, so the geometrically-nearest
node in metres may not be the first hit — but matching this quirk is what guarantees the
bundled and live routers return identical routes for identical inputs.

### `edges`

Every **walkable** edge whose geometry intersects the bbox. The bake filters
upstream `salem.edges.walkable = TRUE`, so consumers do not need to
re-filter — every row in this table is safe to traverse on foot. Non-walkable
edges (interstates, ramps) are dropped because they are not part of the
pedestrian graph.

| Column          | Type    | Notes                                                                |
|-----------------|---------|----------------------------------------------------------------------|
| `id`            | INTEGER | Primary key. Matches TigerLine's `salem.edges.gid`.                  |
| `source`        | INTEGER | FK → `nodes.id`. Never null.                                         |
| `target`        | INTEGER | FK → `nodes.id`. Never null.                                         |
| `length_m`      | REAL    | Geographic length in metres. Matches upstream.                       |
| `walk_cost`     | REAL    | Seconds at 1.4 m/s walking pace (`length_m / 1.4`). Use this in Dijkstra. |
| `mtfcc`         | TEXT    | TIGER feature class (`S1400` local road, `PEDPT` MassGIS supplement, etc.). Nullable for safety; non-null in practice. |
| `fullname`      | TEXT    | Street/path name. Empty string when upstream is null. Used by turn-by-turn synthesis to detect street changes. |
| `geom_polyline` | TEXT    | Flat polyline: `"lat,lng;lat,lng;..."` with 6-decimal precision (~0.1m). Always at least two points. Always single LINESTRING (multi-linestrings flattened to longest part during bake). |

Indexes: `idx_edges_source(source)`, `idx_edges_target(target)`. Fetching all
edges incident to a node is `O(log N)` via either index — Dijkstra adjacency is
the union of both directions because the graph is undirected for pedestrians.

### `meta`

Free-form key/value bundle metadata. Keys present today (consumers may rely on these):

| Key                    | Example value                          |
|------------------------|----------------------------------------|
| `schema_version`       | `1`                                    |
| `built_at`             | `2026-04-25T19:42:11+00:00` (UTC ISO8601) |
| `bbox`                 | `42.446000,-70.997000,42.607000,-70.776000` (min_lat,min_lng,max_lat,max_lng) |
| `source_db`            | `tiger`                                |
| `source_tables`        | `salem.edges, salem.edges_vertices_pgr` |
| `source_summary`       | Human-readable provenance string       |
| `walking_pace_mps`     | `1.4`                                  |
| `edge_count`           | `<int>`                                |
| `node_count`           | `<int>`                                |
| `walkable_node_count`  | `<int>`                                |

Never read pace from app code — pull `walking_pace_mps` from `meta` at startup so
a future change is data-only.

## Consumer expectations

- The graph is **undirected** for pedestrians. When iterating from a node, Dijkstra must
  consider both `WHERE source = :n` and `WHERE target = :n` and traverse the edge in the
  appropriate direction.
- `walk_cost` is authoritative; do not recompute from `length_m / pace` per edge — the
  bake already did it once and the router benefits from one-source-of-truth.
- All coordinates are degrees, decimal, 6-decimal precision. Don't apply integer scaling.
- The bundle is read-only at runtime. Open with SQLite read-only flags on Android
  (`OPEN_READONLY`) and via `better-sqlite3({readonly: true})` on Node.

## Rebake checklist

1. Run `python3 tools/routing-bake/bake-salem-routing.py`. Bake completes in seconds.
2. Verify the file is checked into APK assets (`app-salem/src/main/assets/routing/`) — bundles are intentionally tracked, not generated at build time, so the APK is reproducible from a clean checkout.
3. Run the router parity tests against the new bundle (Phase 2c). Distances must remain within ±5% of TigerLine's reference routes.
4. Bump the SESSION-LOG with the bake date and `meta.source_summary`.
