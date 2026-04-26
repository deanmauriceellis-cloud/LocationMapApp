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


def log(msg):
    print(f"[bake] {msg}", flush=True)


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

    source_summary = (
        f"salem.edges intersect bbox @ {datetime.now(timezone.utc).date().isoformat()} "
        f"(TigerLine source — see SalemTourMapRouting.md)"
    )

    log("writing sqlite…")
    meta = write_sqlite(args.out, edges, nodes, bbox, source_summary)
    log(f"  wrote {os.path.getsize(args.out):,} bytes")

    validate(args.out)
    log(f"meta: {meta}")
    log(f"done in {time.time() - t0:.1f}s")


if __name__ == "__main__":
    sys.exit(main())
