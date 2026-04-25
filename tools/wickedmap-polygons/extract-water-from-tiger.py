#!/usr/bin/env python3
"""
Extract Salem-area water polygons from tiger.areawater into a GeoJSON
FeatureCollection, written to the app-salem assets dir for the WickedMap
PolygonLibrary to load at app start.

Source of truth: TigerLine. Per the operator rule, do not reach for OSM /
water-polygons-split / planetiler PBFs for spatial data — local Postgres
ingests own this.

Usage:
    python3 extract-water-from-tiger.py
"""
import json
import os
import sys
import subprocess

# tiger DB uses peer auth as the OS user (memory: reference_credentials.md)
DB_NAME = "tiger"

# Salem-area bbox — generous so we cover Marblehead/Beverly/Danvers harbors too.
BBOX_MIN_LON = -71.00
BBOX_MIN_LAT = 42.45
BBOX_MAX_LON = -70.70
BBOX_MAX_LAT = 42.62

# TigerLine MTFCC → our kind/subkind taxonomy.
# H-codes from https://www2.census.gov/geo/pdfs/reference/mtfccs2024.pdf
MTFCC_TO_SUBKIND = {
    "H2030": "pond",        # Lake/Pond
    "H2040": "reservoir",   # Reservoir
    "H2041": "treatment",   # Sewage/Industrial Treatment Pond
    "H2051": "bay",         # Bay/Estuary/Gulf/Sound
    "H2053": "ocean",       # Ocean/Sea
    "H2060": "gravelpit",   # Gravel pit / quarry filled with water
    "H2081": "glacier",
    "H3010": "river",       # Stream/River
    "H3013": "braided",     # Braided stream
    "H3020": "canal",       # Canal/Ditch/Aqueduct
}

OUT_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "..",
    "app-salem", "src", "main", "assets", "wickedmap", "polygons.json"
)

# Build a query that emits each row as a JSON-encodable record. We use ST_AsGeoJSON
# on the geometry to skip parsing WKB ourselves, and we filter by bbox via the
# spatial index.
SQL = """
WITH base AS (
    SELECT
        gid,
        COALESCE(NULLIF(fullname, ''), 'unnamed') AS name,
        COALESCE(mtfcc, '') AS mtfcc,
        awater,
        -- Inward buffer 12m via Mass State Plane (SRID 26986). Keeps the
        -- WickedMap whitecap animation strictly inside what the basemap
        -- paints as water — TIGER legal-boundary polygons run a few meters
        -- onto land vs the Witchy basemap rendering, which makes whitecaps
        -- briefly clip over streets/buildings during pan.
        ST_Transform(
            ST_Buffer(ST_Transform(geom, 26986), -12),
            ST_SRID(geom)
        ) AS buffered_geom
    FROM tiger.areawater
    WHERE geom && ST_MakeEnvelope({min_lon}, {min_lat}, {max_lon}, {max_lat}, 4269)
      AND ST_IsValid(geom)
      AND awater > 0
)
SELECT
    gid,
    name,
    mtfcc,
    awater,
    ST_AsGeoJSON(ST_SimplifyPreserveTopology(buffered_geom, 0.00005), 6) AS geometry_json
FROM base
WHERE NOT ST_IsEmpty(buffered_geom)
ORDER BY awater DESC;
""".format(
    min_lon=BBOX_MIN_LON, min_lat=BBOX_MIN_LAT,
    max_lon=BBOX_MAX_LON, max_lat=BBOX_MAX_LAT,
)


def run():
    # Use psql with -t (no header) -A (unaligned) -F'\t' (tab separator) so we
    # can stream the output reliably without depending on psycopg2 being
    # installed system-wide.
    cmd = ["psql", "-d", DB_NAME, "-tA", "-F", "\t", "-c", SQL]
    print("Running:", " ".join(cmd[:4] + ["<sql>"]), file=sys.stderr)
    proc = subprocess.run(cmd, capture_output=True, text=True, check=False)
    if proc.returncode != 0:
        print("psql failed:", proc.stderr, file=sys.stderr)
        sys.exit(1)

    features = []
    skipped = 0
    for line in proc.stdout.strip().splitlines():
        parts = line.split("\t")
        if len(parts) < 5:
            skipped += 1
            continue
        gid, name, mtfcc, awater, geom_json = parts
        try:
            geometry = json.loads(geom_json)
        except json.JSONDecodeError:
            skipped += 1
            continue
        subkind = MTFCC_TO_SUBKIND.get(mtfcc, "other")
        features.append({
            "type": "Feature",
            "geometry": geometry,
            "properties": {
                "kind": "water",
                "subkind": subkind,
                "name": name,
                "mtfcc": mtfcc,
                "source": "tiger.areawater",
                "tiger_gid": int(gid),
                "area_m2": int(awater) if awater.isdigit() else None,
            },
        })

    # Append-style merge: load existing polygons.json (preserve cemetery,
    # park, etc.), drop existing 'water' kind, add fresh.
    if os.path.exists(OUT_PATH):
        with open(OUT_PATH) as f:
            fc = json.load(f)
    else:
        fc = {"type": "FeatureCollection", "metadata": {}, "features": []}

    fc.setdefault("features", [])
    before = len(fc["features"])
    fc["features"] = [
        f for f in fc["features"]
        if f.get("properties", {}).get("kind") != "water"
    ]
    pruned = before - len(fc["features"])
    fc["features"].extend(features)

    fc.setdefault("metadata", {})
    fc["metadata"]["water_count"] = len(features)
    fc["metadata"]["water_source"] = "tiger.areawater (12m inward buffer)"

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w") as f:
        json.dump(fc, f, separators=(",", ":"))

    size_kb = os.path.getsize(OUT_PATH) / 1024
    print(
        f"Wrote {len(features)} water features (replaced {pruned}) → "
        f"{OUT_PATH} ({size_kb:.1f} KB total). skipped={skipped}",
        file=sys.stderr,
    )


if __name__ == "__main__":
    run()
