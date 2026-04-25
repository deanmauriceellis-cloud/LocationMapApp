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
SELECT
    gid,
    COALESCE(NULLIF(fullname, ''), 'unnamed') AS name,
    COALESCE(mtfcc, '') AS mtfcc,
    awater,
    ST_AsGeoJSON(ST_SimplifyPreserveTopology(geom, 0.00005), 6) AS geometry_json
FROM tiger.areawater
WHERE geom && ST_MakeEnvelope({min_lon}, {min_lat}, {max_lon}, {max_lat}, 4269)
  AND ST_IsValid(geom)
  AND awater > 0
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

    fc = {
        "type": "FeatureCollection",
        "metadata": {
            "generator": "tools/wickedmap-polygons/extract-water-from-tiger.py",
            "source": "tiger.areawater",
            "bbox": [BBOX_MIN_LON, BBOX_MIN_LAT, BBOX_MAX_LON, BBOX_MAX_LAT],
            "feature_count": len(features),
        },
        "features": features,
    }

    os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
    with open(OUT_PATH, "w") as f:
        json.dump(fc, f, separators=(",", ":"))

    size_kb = os.path.getsize(OUT_PATH) / 1024
    print(f"Wrote {len(features)} water features to {OUT_PATH} ({size_kb:.1f} KB), {skipped} skipped",
          file=sys.stderr)


if __name__ == "__main__":
    run()
