#!/usr/bin/env python3
"""
Extract Salem-area park / recreation polygons from massgis.openspace into the
WickedMap PolygonLibrary GeoJSON FeatureCollection. Filter:
prim_purp IN ('R','B') — Recreation, or both Recreation+Conservation.

Mirrors extract-cemeteries-from-massgis.py — appends kind='park' features to
the existing polygons.json (preserving water + cemetery + others). Re-running
drops only the existing 'park' features and adds them fresh.

Usage:
    python3 extract-parks-from-massgis.py
"""
import json
import os
import sys
import subprocess

DB_NAME = "tiger"  # massgis schema lives in the tiger DB on this operator's setup

BBOX_MIN_LON = -71.00
BBOX_MIN_LAT = 42.45
BBOX_MAX_LON = -70.70
BBOX_MAX_LAT = 42.62

OUT_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "..",
    "app-salem", "src", "main", "assets", "wickedmap", "polygons.json"
)

SQL = """
SELECT
    gid,
    COALESCE(NULLIF(site_name, ''), 'Unnamed Park') AS name,
    COALESCE(prim_purp, '') AS prim_purp,
    ST_AsGeoJSON(
        ST_SimplifyPreserveTopology(geom, 0.00003),
        6
    ) AS geometry_json
FROM massgis.openspace
WHERE geom && ST_Transform(
        ST_MakeEnvelope({min_lon}, {min_lat}, {max_lon}, {max_lat}, 4326),
        ST_SRID(geom)
    )
  AND ST_IsValid(geom)
  AND prim_purp IN ('R','B')
  -- Exclude cemeteries / burying grounds (covered by extract-cemeteries-…)
  AND site_name NOT ILIKE '%cemetery%'
  AND site_name NOT ILIKE '%burying%'
  AND site_name NOT ILIKE '%burial%'
  AND site_name NOT ILIKE '%graveyard%'
ORDER BY gid;
""".format(
    min_lon=BBOX_MIN_LON, min_lat=BBOX_MIN_LAT,
    max_lon=BBOX_MAX_LON, max_lat=BBOX_MAX_LAT,
)


def run():
    cmd = ["psql", "-d", DB_NAME, "-tA", "-F", "\t", "-c", SQL]
    print("Querying massgis.openspace for parks...", file=sys.stderr)
    proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
    if proc.returncode != 0:
        print("psql failed:", proc.stderr, file=sys.stderr)
        sys.exit(1)

    new_features = []
    skipped = 0
    for line in proc.stdout.strip().splitlines():
        parts = line.split("\t")
        if len(parts) < 4:
            skipped += 1
            continue
        gid, name, prim_purp, geom_json = parts
        try:
            geometry = json.loads(geom_json)
        except json.JSONDecodeError:
            skipped += 1
            continue
        new_features.append({
            "type": "Feature",
            "geometry": geometry,
            "properties": {
                "kind": "park",
                "subkind": "recreation" if prim_purp == "R" else "rec_conservation",
                "name": name,
                "source": "massgis.openspace",
                "massgis_gid": int(gid),
            },
        })

    if os.path.exists(OUT_PATH):
        with open(OUT_PATH) as f:
            fc = json.load(f)
    else:
        fc = {"type": "FeatureCollection", "metadata": {}, "features": []}

    fc.setdefault("features", [])
    before = len(fc["features"])
    fc["features"] = [
        f for f in fc["features"]
        if f.get("properties", {}).get("kind") != "park"
    ]
    pruned = before - len(fc["features"])
    fc["features"].extend(new_features)

    fc.setdefault("metadata", {})
    fc["metadata"]["park_count"] = len(new_features)
    fc["metadata"]["park_source"] = "massgis.openspace WHERE prim_purp IN ('R','B') minus cemeteries"

    with open(OUT_PATH, "w") as f:
        json.dump(fc, f, separators=(",", ":"))

    size_kb = os.path.getsize(OUT_PATH) / 1024
    print(
        f"Wrote {len(new_features)} parks (replaced {pruned}) → "
        f"{OUT_PATH} ({size_kb:.1f} KB total). skipped={skipped}",
        file=sys.stderr,
    )


if __name__ == "__main__":
    run()
