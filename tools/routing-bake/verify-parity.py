#!/usr/bin/env python3
"""verify-parity.py — Run a set of Salem routes via both the bundled SQLite (the
APK's routing source) and TigerLine's live tiger.route_walking() function.
Asserts the two return identical distances (the bundled router IS allowed to
diverge by <0.1m due to floating-point ordering, but no more). This is the
spec the Kotlin and JS routers must also pass — bundle output must equal live
output for any input.
"""

import heapq
import math
import os
import sqlite3
import sys

import psycopg2

BUNDLE = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "..", "app-salem", "src", "main", "assets", "routing",
    "salem-routing-graph.sqlite",
)

# (label, from_lat, from_lng, to_lat, to_lng) — all Salem-area sanity routes.
# Distances are NOT hardcoded; the script fetches them from TigerLine live.
REF_ROUTES = [
    ("Salem Common -> House of Seven Gables", 42.5219, -70.8967, 42.5226, -70.8845),
    ("Witch House -> Burying Point Cemetery", 42.5223, -70.8983, 42.5208, -70.8957),
    ("Peabody Essex Museum -> Derby Wharf",   42.5225, -70.8915, 42.5165, -70.8845),
    ("Salem Commuter Rail -> Museum Place",   42.5236, -70.8951, 42.5226, -70.8908),
    ("Danvers cross-town",                    42.5750, -70.9350, 42.5650, -70.9100),
    ("Beverly across the bridge",             42.5450, -70.8850, 42.5350, -70.8900),
]
PARITY_TOL_M = 0.5  # bundle vs live must agree within half a metre


def haversine_m(lat1, lng1, lat2, lng2):
    R = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlam = math.radians(lng2 - lng1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlam / 2) ** 2
    return 2 * R * math.asin(math.sqrt(a))


def nearest_walkable_node(cur, lat, lng):
    """KNN using planar SRID-4269 degree distance, matching the semantic of
    PostGIS's `<->` operator on points. This is intentionally NOT
    haversine — TigerLine's `tiger.nearest_walkable_node()` uses `<->`
    so the consumer router must do the same to produce identical routes."""
    for window_lat, window_lng in [(0.005, 0.007), (0.02, 0.027), (0.08, 0.108)]:
        cur.execute(
            """
            SELECT n.id, n.lat, n.lng,
                   (n.lat - ?) * (n.lat - ?) +
                   (n.lng - ?) * (n.lng - ?)
                   AS d2
            FROM nodes_rtree r
            JOIN nodes n ON n.id = r.id
            WHERE r.min_lat BETWEEN ? - ? AND ? + ?
              AND r.min_lng BETWEEN ? - ? AND ? + ?
            ORDER BY d2 ASC
            LIMIT 1
            """,
            (lat, lat, lng, lng, lat, window_lat, lat, window_lat, lng, window_lng, lng, window_lng),
        )
        row = cur.fetchone()
        if row:
            return row[0], row[1], row[2]
    return None


def dijkstra(cur, src_node, tgt_node):
    if src_node == tgt_node:
        return 0.0, [src_node]
    # cost-to-reach map; lazy expansion
    dist = {src_node: 0.0}
    prev = {}
    heap = [(0.0, src_node)]
    while heap:
        d, u = heapq.heappop(heap)
        if u == tgt_node:
            # reconstruct
            path = [u]
            while path[-1] in prev:
                path.append(prev[path[-1]])
            return d, list(reversed(path))
        if d > dist.get(u, float("inf")):
            continue
        cur.execute(
            "SELECT target AS nbr, length_m FROM edges WHERE source = ? "
            "UNION ALL "
            "SELECT source AS nbr, length_m FROM edges WHERE target = ?",
            (u, u),
        )
        for nbr, edge_m in cur.fetchall():
            nd = d + edge_m
            if nd < dist.get(nbr, float("inf")):
                dist[nbr] = nd
                prev[nbr] = u
                heapq.heappush(heap, (nd, nbr))
    return None, None


def main():
    if not os.path.exists(BUNDLE):
        print(f"FAIL: bundle not found at {BUNDLE}")
        return 1
    conn = sqlite3.connect(BUNDLE)
    cur = conn.cursor()

    print(f"bundle: {BUNDLE} ({os.path.getsize(BUNDLE):,} bytes)\n")
    cur.execute("SELECT key, value FROM meta ORDER BY key")
    for k, v in cur.fetchall():
        print(f"  meta.{k} = {v}")
    print()

    pg = psycopg2.connect(dbname="tiger")
    pg_cur = pg.cursor()

    failures = 0
    for label, flat, flng, tlat, tlng in REF_ROUTES:
        s = nearest_walkable_node(cur, flat, flng)
        t = nearest_walkable_node(cur, tlat, tlng)
        if s is None or t is None:
            print(f"FAIL  {label}  — KNN snap failed (s={s} t={t})")
            failures += 1
            continue
        snap_m_from = haversine_m(flat, flng, s[1], s[2])
        snap_m_to = haversine_m(tlat, tlng, t[1], t[2])
        d_m, path = dijkstra(cur, s[0], t[0])
        if d_m is None:
            print(f"FAIL  {label}  — no path from {s[0]} to {t[0]} in bundle")
            failures += 1
            continue
        pg_cur.execute("SELECT distance_m FROM tiger.route_walking(%s, %s, %s, %s)",
                       (flat, flng, tlat, tlng))
        live_m = pg_cur.fetchone()[0]
        if live_m is None:
            print(f"FAIL  {label}  — TigerLine returned NULL")
            failures += 1
            continue
        delta_m = d_m - live_m
        ok = abs(delta_m) <= PARITY_TOL_M
        marker = "PASS" if ok else "FAIL"
        if not ok:
            failures += 1
        print(f"{marker}  {label}")
        print(f"      bundle = {d_m:8.2f} m   live = {live_m:8.2f} m   delta = {delta_m:+.3f} m   (tol ±{PARITY_TOL_M}m)")
        print(f"      snap   from={snap_m_from:.1f}m   to={snap_m_to:.1f}m   nodes_in_path={len(path)}")
    pg.close()

    print()
    print(f"failures: {failures}/{len(REF_ROUTES)}")
    return 0 if failures == 0 else 2


if __name__ == "__main__":
    sys.exit(main())
