#!/usr/bin/env python3
"""
bake-salem-routing.py — Export TigerLine's salem.edges + salem.edges_vertices_pgr
into a Salem-area SQLite bundle the Salem app and web router both consume.

Produces app-salem/src/main/assets/routing/salem-routing-graph.sqlite.

Schema (consumer contract, see SCHEMA.md):
    nodes(id INTEGER PK, lat REAL, lng REAL, walkable INTEGER)
    nodes_rtree (R-tree spatial index over nodes for KNN snap)
    edges(id INTEGER PK, source INTEGER, target INTEGER,
          length_m REAL, walk_cost REAL, mtfcc TEXT, fullname TEXT,
          geom_polyline TEXT)  -- "lat,lng;lat,lng;..." flat string
    meta(key TEXT PK, value TEXT)
        version, built_at, bbox, source_db, source_tables, walking_pace_mps,
        edge_count, node_count, walkable_node_count

Bbox: 3-mile buffer around the Salem POI envelope.
    POI envelope (from salem_pois): 42.490-42.563 lat, -70.938 to -70.835 lng
    + 3-mile buffer (~0.044 lat, ~0.059 lng @ 42.5)
    => routing bbox: 42.446-42.607 lat, -70.997 to -70.776 lng

Usage:
    python3 bake-salem-routing.py [--out PATH] [--bbox MIN_LAT MIN_LNG MAX_LAT MAX_LNG]
"""

import argparse
import math
import os
import sqlite3
import sys
import time
from datetime import datetime, timezone

import psycopg2

# Salem POI envelope + 3-mile buffer. Frozen as a literal so bakes are
# reproducible. Change here only if the operator expands tour coverage.
DEFAULT_BBOX = (42.446, -70.997, 42.607, -70.776)  # min_lat, min_lng, max_lat, max_lng

WALKING_PACE_MPS = 1.4  # Matches TigerLine's tiger.route_walking contract.

DEFAULT_OUT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "..", "app-salem", "src", "main", "assets", "routing",
    "salem-routing-graph.sqlite",
)

SCHEMA_VERSION = 1

# S179: edge-splitting parameters. TIGER edges run uninterrupted from
# intersection to intersection, leaving 100-300m gaps between graph
# vertices on long blocks. Mid-block POIs (e.g. Phillips House at 34
# Chestnut St) snap to the nearest cross-street vertex, which is far
# enough off that the WalkingDirections tail-approach exceeds its 60m
# safety cap and the polyline visibly stops short of the destination.
# Splitting long edges every ~SPLIT_TARGET_M metres densifies the vertex
# grid so mid-block destinations land within snap range.
SPLIT_MIN_M = 60.0       # only split edges longer than this
SPLIT_TARGET_M = 40.0    # nominal sub-segment length after splitting
SPLIT_NODE_ID_BASE = 20_000_000_000   # synthetic node IDs use this offset
SPLIT_EDGE_ID_BASE = 5_000_000_000    # synthetic edge IDs use this offset


def log(msg):
    print(f"[bake] {msg}", flush=True)


def haversine_m(p1, p2):
    """Great-circle distance in meters between (lat, lng) tuples."""
    lat1, lng1 = p1
    lat2, lng2 = p2
    R = 6_371_000.0
    dlat = math.radians(lat2 - lat1)
    dlng = math.radians(lng2 - lng1)
    a = (math.sin(dlat / 2) ** 2
         + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2))
         * math.sin(dlng / 2) ** 2)
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def parse_polyline(s):
    """Parse 'lat,lng;lat,lng;...' to [(lat, lng), ...]."""
    if not s:
        return []
    out = []
    for pair in s.split(";"):
        if not pair:
            continue
        lat_str, lng_str = pair.split(",")
        out.append((float(lat_str), float(lng_str)))
    return out


def encode_polyline(pts):
    return ";".join(f"{p[0]:.6f},{p[1]:.6f}" for p in pts)


def split_long_edges(edges, nodes):
    """Densify the graph by inserting walkable nodes mid-edge on any edge
    longer than SPLIT_MIN_M. Returns (new_edges, new_nodes) where
    new_nodes augments the original nodes list with synthetic split
    vertices (IDs above SPLIT_NODE_ID_BASE). Pre-existing short edges
    pass through untouched.

    Conventions:
      - Split nodes are always walkable=1 (they sit on a walkable parent).
      - Sub-edge walk_cost == sub-edge length_m (1:1, matches parent for
        any S1400/local-road edge in the current graph).
      - Sub-edge mtfcc/fullname inherit from the parent — the road has
        the same name and class along its length.
      - Sub-edge geom_polyline is the slice of the parent polyline up to
        and including the split point, so the rendered route still
        follows the parent road's curve.
    """
    next_node_id = SPLIT_NODE_ID_BASE
    next_edge_id = SPLIT_EDGE_ID_BASE
    new_edges = []
    new_nodes = []  # appended to original nodes
    split_count = 0
    sub_count = 0

    for edge in edges:
        gid, src, tgt, length_m, walk_cost, mtfcc, fullname, polyline_str = edge
        pts = parse_polyline(polyline_str)
        if length_m <= SPLIT_MIN_M or len(pts) < 2:
            new_edges.append(edge)
            continue

        # Cumulative distance along the polyline.
        cum = [0.0]
        for i in range(1, len(pts)):
            cum.append(cum[-1] + haversine_m(pts[i - 1], pts[i]))
        total = cum[-1]
        if total <= SPLIT_MIN_M:
            new_edges.append(edge)
            continue

        n_pieces = max(2, round(total / SPLIT_TARGET_M))
        # n_pieces - 1 split points, evenly spaced by parent length.
        targets = [total * i / n_pieces for i in range(1, n_pieces)]

        # Resolve each target distance to a split point on the polyline.
        # Each entry: (segment_index_i, point_after_split).
        splits = []
        ti = 0
        for tl in targets:
            while ti < len(cum) - 1 and cum[ti + 1] < tl:
                ti += 1
            seg_len = cum[ti + 1] - cum[ti]
            if seg_len < 1e-6:
                pt = pts[ti]
            else:
                t = (tl - cum[ti]) / seg_len
                pt = (
                    pts[ti][0] + (pts[ti + 1][0] - pts[ti][0]) * t,
                    pts[ti][1] + (pts[ti + 1][1] - pts[ti][1]) * t,
                )
            splits.append((ti, pt))

        # Walk the parent polyline, emitting one sub-edge per split.
        prev_node = src
        prev_pt = pts[0]
        cursor_seg = 0  # last segment we fully consumed
        for (seg_i, split_pt) in splits:
            new_node_id = next_node_id
            next_node_id += 1
            new_nodes.append((new_node_id, split_pt[0], split_pt[1], 1))

            sub_pts = [prev_pt]
            ci = cursor_seg
            while ci < seg_i:
                sub_pts.append(pts[ci + 1])
                ci += 1
            sub_pts.append(split_pt)

            sub_len = sum(haversine_m(sub_pts[k], sub_pts[k + 1]) for k in range(len(sub_pts) - 1))
            new_edges.append((
                next_edge_id, prev_node, new_node_id,
                sub_len, sub_len,
                mtfcc, fullname, encode_polyline(sub_pts),
            ))
            next_edge_id += 1
            sub_count += 1

            prev_node = new_node_id
            prev_pt = split_pt
            cursor_seg = seg_i

        # Tail piece: from the last split point through any remaining
        # geometry vertices to the parent's target node.
        sub_pts = [prev_pt]
        ci = cursor_seg
        while ci < len(pts) - 1:
            sub_pts.append(pts[ci + 1])
            ci += 1
        sub_len = sum(haversine_m(sub_pts[k], sub_pts[k + 1]) for k in range(len(sub_pts) - 1))
        new_edges.append((
            next_edge_id, prev_node, tgt,
            sub_len, sub_len,
            mtfcc, fullname, encode_polyline(sub_pts),
        ))
        next_edge_id += 1
        sub_count += 1
        split_count += 1

    log(f"  split {split_count} long edges into {sub_count} sub-edges, "
        f"+{len(new_nodes)} synthetic nodes")
    return new_edges, nodes + new_nodes


def fetch_edges(pg_cur, bbox):
    """Return list of (gid, source, target, length_m, walk_cost, mtfcc, fullname, polyline_str).
    polyline_str is "lat,lng;lat,lng;..." with 6-decimal precision (~0.1m at this latitude).
    Edges are clipped by bbox intersection on the_geom.
    Multi-linestring edges are flattened via ST_LineMerge; if that returns a MultiLineString
    (unmergeable junction), we keep only the longest sub-line.
    """
    min_lat, min_lng, max_lat, max_lng = bbox
    sql = """
        WITH bbox AS (
            SELECT ST_MakeEnvelope(%s, %s, %s, %s, 4269) AS g
        )
        SELECT
            e.gid,
            e.source,
            e.target,
            e.length_m,
            e.walk_cost,
            e.mtfcc,
            COALESCE(e.fullname, '') AS fullname,
            ST_AsText(
                CASE WHEN GeometryType(ST_LineMerge(e.the_geom)) = 'LINESTRING'
                     THEN ST_LineMerge(e.the_geom)
                     ELSE (
                        SELECT geom FROM (
                            SELECT (ST_Dump(ST_LineMerge(e.the_geom))).geom AS geom
                        ) parts ORDER BY ST_Length(geom) DESC LIMIT 1
                     )
                END
            ) AS line_wkt
        FROM salem.edges e, bbox
        WHERE e.the_geom && bbox.g
          AND e.source IS NOT NULL
          AND e.target IS NOT NULL
          AND e.walkable = TRUE;
    """
    pg_cur.execute(sql, (min_lng, min_lat, max_lng, max_lat))
    out = []
    for row in pg_cur.fetchall():
        gid, src, tgt, length_m, walk_cost, mtfcc, fullname, wkt = row
        out.append((gid, src, tgt, length_m, walk_cost, mtfcc, fullname, wkt_to_polyline(wkt)))
    return out


def wkt_to_polyline(wkt):
    """Convert 'LINESTRING(lng lat, lng lat, ...)' to 'lat,lng;lat,lng;...'.
    Returns empty string for null/empty geometries."""
    if not wkt:
        return ""
    inner = wkt[wkt.index("(") + 1: wkt.rindex(")")]
    pts = []
    for pair in inner.split(","):
        lng_str, lat_str = pair.strip().split()
        pts.append(f"{float(lat_str):.6f},{float(lng_str):.6f}")
    return ";".join(pts)


def fetch_nodes(pg_cur, node_ids):
    """Return list of (id, lat, lng, walkable_int) for the given node ids."""
    if not node_ids:
        return []
    pg_cur.execute(
        """
        SELECT id, ST_Y(the_geom), ST_X(the_geom), COALESCE(walkable_node, false)
        FROM salem.edges_vertices_pgr
        WHERE id = ANY(%s);
        """,
        (list(node_ids),),
    )
    return [(nid, lat, lng, 1 if walkable else 0) for (nid, lat, lng, walkable) in pg_cur.fetchall()]


def write_sqlite(out_path, edges, nodes, bbox, source_summary):
    """Build the bundle. Replaces any existing file at out_path."""
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    if os.path.exists(out_path):
        os.remove(out_path)

    conn = sqlite3.connect(out_path)
    cur = conn.cursor()

    cur.executescript("""
        PRAGMA journal_mode = OFF;
        PRAGMA synchronous = OFF;
        PRAGMA page_size = 4096;

        CREATE TABLE nodes (
            id INTEGER PRIMARY KEY,
            lat REAL NOT NULL,
            lng REAL NOT NULL,
            walkable INTEGER NOT NULL DEFAULT 0
        );

        CREATE VIRTUAL TABLE nodes_rtree USING rtree(
            id,
            min_lat, max_lat,
            min_lng, max_lng
        );

        CREATE TABLE edges (
            id INTEGER PRIMARY KEY,
            source INTEGER NOT NULL,
            target INTEGER NOT NULL,
            length_m REAL NOT NULL,
            walk_cost REAL NOT NULL,
            mtfcc TEXT,
            fullname TEXT,
            geom_polyline TEXT NOT NULL
        );

        CREATE INDEX idx_edges_source ON edges(source);
        CREATE INDEX idx_edges_target ON edges(target);

        CREATE TABLE meta (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        );
    """)

    cur.executemany(
        "INSERT INTO nodes(id, lat, lng, walkable) VALUES (?, ?, ?, ?)",
        nodes,
    )
    # Populate the R-tree only with walkable nodes — KNN snap target is
    # always a walkable vertex per the SalemRouter contract.
    walkable_rows = [(nid, lat, lat, lng, lng) for (nid, lat, lng, w) in nodes if w == 1]
    cur.executemany(
        "INSERT INTO nodes_rtree(id, min_lat, max_lat, min_lng, max_lng) VALUES (?, ?, ?, ?, ?)",
        walkable_rows,
    )

    cur.executemany(
        """INSERT INTO edges(id, source, target, length_m, walk_cost, mtfcc, fullname, geom_polyline)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
        edges,
    )

    walkable_count = sum(1 for n in nodes if n[3] == 1)
    meta = {
        "schema_version": str(SCHEMA_VERSION),
        "built_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "bbox": ",".join(f"{v:.6f}" for v in bbox),
        "source_db": "tiger",
        "source_tables": "salem.edges, salem.edges_vertices_pgr",
        "source_summary": source_summary,
        "walking_pace_mps": str(WALKING_PACE_MPS),
        "edge_count": str(len(edges)),
        "node_count": str(len(nodes)),
        "walkable_node_count": str(walkable_count),
    }
    cur.executemany("INSERT INTO meta(key, value) VALUES (?, ?)", list(meta.items()))

    conn.commit()
    cur.execute("VACUUM")
    conn.close()
    return meta


def validate(out_path):
    """Sanity checks: no orphan edges, R-tree populated, every walkable
    edge resolves to known nodes."""
    conn = sqlite3.connect(out_path)
    cur = conn.cursor()
    cur.execute("""
        SELECT COUNT(*) FROM edges e
        WHERE NOT EXISTS (SELECT 1 FROM nodes n WHERE n.id = e.source)
           OR NOT EXISTS (SELECT 1 FROM nodes n WHERE n.id = e.target)
    """)
    orphans = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM nodes_rtree")
    rtree_count = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM nodes WHERE walkable = 1")
    walkable_count = cur.fetchone()[0]
    conn.close()
    if orphans != 0:
        raise RuntimeError(f"Validation failed: {orphans} orphan edges (source/target node missing)")
    if rtree_count != walkable_count:
        raise RuntimeError(f"Validation failed: rtree has {rtree_count} rows, walkable nodes = {walkable_count}")
    log(f"validate ok: {orphans} orphans, rtree={rtree_count}, walkable_nodes={walkable_count}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=DEFAULT_OUT, help="SQLite output path")
    ap.add_argument("--bbox", nargs=4, type=float, metavar=("MIN_LAT", "MIN_LNG", "MAX_LAT", "MAX_LNG"),
                    default=None, help="Override default Salem bbox")
    args = ap.parse_args()
    bbox = tuple(args.bbox) if args.bbox else DEFAULT_BBOX

    log(f"bbox = {bbox}")
    log(f"out  = {args.out}")

    t0 = time.time()
    pg = psycopg2.connect(dbname="tiger")
    pg_cur = pg.cursor()

    log("fetching edges…")
    edges = fetch_edges(pg_cur, bbox)
    log(f"  {len(edges)} edges")

    node_ids = set()
    for e in edges:
        node_ids.add(e[1])
        node_ids.add(e[2])
    log(f"fetching {len(node_ids)} nodes…")
    nodes = fetch_nodes(pg_cur, node_ids)
    log(f"  {len(nodes)} nodes")

    pg.close()

    log(f"densifying long edges (split >{SPLIT_MIN_M:.0f}m, target {SPLIT_TARGET_M:.0f}m)…")
    edges, nodes = split_long_edges(edges, nodes)
    log(f"  post-split: {len(edges)} edges, {len(nodes)} nodes")

    source_summary = (
        f"salem.edges intersect bbox @ {datetime.now(timezone.utc).date().isoformat()} "
        f"(TigerLine source — see SalemTourMapRouting.md); "
        f"S179 edge-split min={SPLIT_MIN_M:.0f}m target={SPLIT_TARGET_M:.0f}m"
    )

    log("writing sqlite…")
    meta = write_sqlite(args.out, edges, nodes, bbox, source_summary)
    log(f"  wrote {os.path.getsize(args.out):,} bytes")

    validate(args.out)
    log(f"meta: {meta}")
    log(f"done in {time.time() - t0:.1f}s")


if __name__ == "__main__":
    sys.exit(main())
