#!/usr/bin/env python3
"""
Download map tiles for downtown Salem and package as an osmdroid offline archive.

Creates a SQLite database compatible with osmdroid's DatabaseFileArchive.
The archive is placed in osmdroid's base path and auto-discovered by
MapTileFileArchiveProvider — no custom tile provider code needed.

Schema: CREATE TABLE tiles (key INTEGER PRIMARY KEY, provider TEXT, tile BLOB)
Key encoding: matches osmdroid MapTileIndex.getTileIndex(zoom, x, y)

Usage:
    python3 download_salem_tiles.py [output_path]

Output defaults to: app-salem/src/main/assets/salem_tiles.sqlite
"""

import math
import os
import sqlite3
import sys
import time
import urllib.request

# ── Bounding boxes: tiered coverage ─────────────────────────────────────
# DOWNTOWN: the Salem Essentials walking tour area (Salem Common to House of
#   Seven Gables harbor). Pixel-perfect zoom 19 is reserved for this tight box.
# FULL_SALEM: the full Salem, MA city footprint — Salem Willows peninsula
#   (north), Forest River Park (south), Peabody border (west), harbor +
#   Winter Island (east). Zoom 16-18 span this box so panning to any corner
#   of the city shows tiles instead of gray.
BBOX_DOWNTOWN = {
    "north": 42.530,
    "south": 42.508,
    "west": -70.905,
    "east": -70.876,
}
BBOX_FULL_SALEM = {
    "north": 42.545,
    "south": 42.475,
    "west": -70.958,
    "east": -70.835,
}

# ── Tile sources ────────────────────────────────────────────────────────
# Provider names MUST match the osmdroid ITileSource.name() values exactly.
# osmdroid's DatabaseFileArchive queries: WHERE key = ? AND provider = ?
# Tiered zoom/bbox pairs: low zooms cover the whole city; zoom 19 stays tight
# on downtown to keep bundle size reasonable.
SOURCES = {
    "Esri-WorldImagery": {
        # ArcGIS uses z/y/x (row/column), not z/x/y
        "url": "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
        "zoom_bboxes": [
            (16, BBOX_FULL_SALEM),
            (17, BBOX_FULL_SALEM),
            (18, BBOX_DOWNTOWN),
            (19, BBOX_DOWNTOWN),
        ],
    },
    "Mapnik": {
        "url": "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
        "zoom_bboxes": [
            (16, BBOX_FULL_SALEM),
            (17, BBOX_FULL_SALEM),
            (18, BBOX_DOWNTOWN),
        ],
    },
}

USER_AGENT = "WickedSalemWitchCityTour/1.0 (offline tile builder; contact: deanmauriceellis@gmail.com)"
DELAY_SECONDS = 0.15  # Be polite to tile servers


def lat_lon_to_tile(lat, lon, zoom):
    """Convert lat/lon to Web Mercator tile x,y at given zoom level."""
    n = 2 ** zoom
    x = int(math.floor((lon + 180.0) / 360.0 * n))
    lat_rad = math.radians(lat)
    y = int(
        math.floor(
            (1.0 - math.log(math.tan(lat_rad) + 1.0 / math.cos(lat_rad)) / math.pi)
            / 2.0
            * n
        )
    )
    return x, y


def osmdroid_tile_index(zoom, x, y):
    """
    Compute osmdroid MapTileIndex key.
    Matches the Java implementation:
        (((long) zoom) << (zoom + zoom)) | (((long) x) << zoom) | (long) y
    """
    return (zoom << (zoom + zoom)) | (x << zoom) | y


def get_tile_ranges(bbox, zoom):
    """Get tile x,y ranges for a bounding box at a zoom level."""
    x_min, y_min = lat_lon_to_tile(bbox["north"], bbox["west"], zoom)
    x_max, y_max = lat_lon_to_tile(bbox["south"], bbox["east"], zoom)
    # Ensure min <= max (y_min is north = numerically smaller in Web Mercator)
    if x_min > x_max:
        x_min, x_max = x_max, x_min
    if y_min > y_max:
        y_min, y_max = y_max, y_min
    return x_min, x_max, y_min, y_max


def download_tile(url):
    """Download a single tile, return raw bytes."""
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read()


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_dir = os.path.dirname(script_dir)
    default_output = os.path.join(
        project_dir, "app-salem", "src", "main", "assets", "salem_tiles.sqlite"
    )
    output_path = sys.argv[1] if len(sys.argv) > 1 else default_output

    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    print(f"Salem Offline Tile Downloader (tiered coverage)")
    print(f"  Downtown bbox : {BBOX_DOWNTOWN}")
    print(f"  Full Salem    : {BBOX_FULL_SALEM}")
    print(f"Output: {output_path}")
    print()

    # ── Calculate tile counts per source ────────────────────────────────
    # Each source has its own zoom/bbox pairs: low zooms cover the whole city,
    # zoom 19 stays tight on downtown.
    total_tiles = 0
    source_zoom_details = {}
    for source_name, source_config in SOURCES.items():
        pairs = source_config["zoom_bboxes"]
        details = []
        source_tiles = 0
        print(f"  {source_name}:")
        for zoom, bbox in pairs:
            x_min, x_max, y_min, y_max = get_tile_ranges(bbox, zoom)
            nx = x_max - x_min + 1
            ny = y_max - y_min + 1
            count = nx * ny
            source_tiles += count
            details.append((zoom, x_min, x_max, y_min, y_max, nx, ny, count))
            scope = "full-salem" if bbox is BBOX_FULL_SALEM else "downtown"
            print(f"    Zoom {zoom:2d} ({scope:10s}): {nx}×{ny} = {count:5d} tiles")
        total_tiles += source_tiles
        source_zoom_details[source_name] = details
        print(f"    Subtotal: {source_tiles} tiles")
        print()

    est_low = total_tiles * 15 // 1024
    est_high = total_tiles * 35 // 1024
    print(f"  Total: {total_tiles} tiles")
    print(f"  Estimated size: {est_low:,} KB – {est_high:,} KB ({est_low // 1024} – {est_high // 1024} MB)")
    est_time = total_tiles * DELAY_SECONDS
    print(f"  Estimated time: {est_time:.0f} seconds ({est_time / 60:.1f} minutes)")
    print()

    # ── Open or create SQLite database ────────────────────────────────────
    # If the database already exists, only missing tiles will be downloaded.
    fresh = not os.path.exists(output_path)
    db = sqlite3.connect(output_path)
    db.execute("PRAGMA journal_mode=WAL")
    # Composite PK: same tile coords can exist for different providers (Esri + Mapnik).
    # Matches osmdroid's SqlTileWriter schema: PRIMARY KEY (key, provider).
    db.execute(
        "CREATE TABLE IF NOT EXISTS tiles (key INTEGER, provider TEXT, tile BLOB, PRIMARY KEY (key, provider))"
    )
    if not fresh:
        existing = db.execute("SELECT COUNT(*) FROM tiles").fetchone()[0]
        print(f"  Existing database with {existing} tiles — will skip already-downloaded tiles\n")

    # ── Download tiles ──────────────────────────────────────────────────
    downloaded = 0
    skipped = 0
    errors = 0
    total_bytes = 0
    start_time = time.time()

    for source_name, source_config in SOURCES.items():
        zooms = [z for z, _ in source_config["zoom_bboxes"]]
        print(f"── {source_name} (zoom {min(zooms)}-{max(zooms)}) ──")
        source_start = time.time()
        source_count = 0

        for zoom, x_min, x_max, y_min, y_max, nx, ny, count in source_zoom_details[source_name]:
            for x in range(x_min, x_max + 1):
                for y in range(y_min, y_max + 1):
                    key = osmdroid_tile_index(zoom, x, y)

                    # Skip tiles already in the database
                    existing_row = db.execute(
                        "SELECT 1 FROM tiles WHERE key = ? AND provider = ?",
                        (key, source_name),
                    ).fetchone()
                    if existing_row:
                        skipped += 1
                        continue

                    url = source_config["url"].format(z=zoom, x=x, y=y)
                    try:
                        tile_data = download_tile(url)
                        db.execute(
                            "INSERT OR REPLACE INTO tiles (key, provider, tile) VALUES (?, ?, ?)",
                            (key, source_name, tile_data),
                        )
                        downloaded += 1
                        source_count += 1
                        total_bytes += len(tile_data)

                        if downloaded % 100 == 0:
                            db.commit()
                            elapsed = time.time() - start_time
                            rate = downloaded / elapsed if elapsed > 0 else 0
                            remaining = (total_tiles - downloaded - skipped) / rate if rate > 0 else 0
                            print(
                                f"  {downloaded} downloaded, {skipped} skipped | "
                                f"{total_bytes / 1024 / 1024:.1f} MB | "
                                f"{rate:.1f} tiles/s | "
                                f"~{remaining:.0f}s remaining"
                            )
                    except Exception as e:
                        errors += 1
                        print(f"  ERROR z{zoom}/{x}/{y}: {e}")

                    time.sleep(DELAY_SECONDS)

        source_elapsed = time.time() - source_start
        print(f"  {source_name}: {source_count} tiles in {source_elapsed:.1f}s\n")

    # ── Finalize ────────────────────────────────────────────────────────
    db.commit()
    db.execute("PRAGMA journal_mode=DELETE")  # Remove WAL for portability
    db.execute("VACUUM")
    db.close()

    elapsed = time.time() - start_time
    file_size = os.path.getsize(output_path)
    print(f"═══════════════════════════════════════════")
    print(f"Done in {elapsed:.1f}s")
    print(f"Downloaded: {downloaded} tiles, skipped: {skipped}, errors: {errors}")
    print(f"New data:   {total_bytes / 1024 / 1024:.1f} MB (raw tiles)")
    print(f"Database:   {file_size / 1024 / 1024:.1f} MB ({output_path})")


if __name__ == "__main__":
    main()
