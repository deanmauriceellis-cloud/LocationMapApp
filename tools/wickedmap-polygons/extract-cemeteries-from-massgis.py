#!/usr/bin/env python3
"""
Extract Salem-area cemetery polygons from massgis.openspace into the same
GeoJSON FeatureCollection used by the WickedMap PolygonLibrary. Filter:
prim_purp = 'H' (Historical/Cultural in MassGIS OpenSpace) AND site_name
matches a cemetery/burial-ground pattern.

This script *appends* to the existing polygons.json (preserving water and
any other previously-extracted kinds). Re-running drops only the existing
'cemetery' features and adds them fresh.

Usage:
    python3 extract-cemeteries-from-massgis.py
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
    COALESCE(NULLIF(site_name, ''), 'Unnamed Burial Ground') AS name,
    COALESCE(prim_purp, '') AS prim_purp,
    -- geom is in SRID 4269 (NAD83 lat/lon degrees), so tolerance is in
    -- degrees. 0.00003 ≈ 3.3m at Salem latitude — preserves shape detail
    -- without bloating the asset.
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
  AND prim_purp = 'H'
  AND (
    site_name ILIKE '%cemetery%'
    OR site_name ILIKE '%burying%'
    OR site_name ILIKE '%burial%'
    OR site_name ILIKE '%graveyard%'
    OR site_name ILIKE '%memorial park%'
  )
ORDER BY gid;
""".format(
    min_lon=BBOX_MIN_LON, min_lat=BBOX_MIN_LAT,
    max_lon=BBOX_MAX_LON, max_lat=BBOX_MAX_LAT,
)


def run():
    cmd = ["psql", "-d", DB_NAME, "-tA", "-F", "\t", "-c", SQL]
    print("Querying massgis.openspace...", file=sys.stderr)
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
                "kind": "cemetery",
                "subkind": "historic",
                "name": name,
                "source": "massgis.openspace",
                "massgis_gid": int(gid),
            },
        })

    # Load existing FC, drop old cemetery features, append new.
    if os.path.exists(OUT_PATH):
        with open(OUT_PATH) as f:
            fc = json.load(f)
    else:
        fc = {"type": "FeatureCollection", "metadata": {}, "features": []}

    fc.setdefault("features", [])
    before = len(fc["features"])
    fc["features"] = [
        f for f in fc["features"]
        if f.get("properties", {}).get("kind") != "cemetery"
    ]
    pruned = before - len(fc["features"])
    fc["features"].extend(new_features)

    fc.setdefault("metadata", {})
    fc["metadata"]["cemetery_count"] = len(new_features)
    fc["metadata"]["cemetery_source"] = "massgis.openspace WHERE prim_purp='H' AND name~cemetery"

    with open(OUT_PATH, "w") as f:
        json.dump(fc, f, separators=(",", ":"))

    size_kb = os.path.getsize(OUT_PATH) / 1024
    print(
        f"Wrote {len(new_features)} cemeteries (replaced {pruned}) → "
        f"{OUT_PATH} ({size_kb:.1f} KB total). skipped={skipped}",
        file=sys.stderr,
    )


if __name__ == "__main__":
    run()
