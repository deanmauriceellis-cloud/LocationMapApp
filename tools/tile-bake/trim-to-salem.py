#!/usr/bin/env python3
"""
S167 tile trim: shrink salem_tiles.sqlite to Salem city only, Witchy-only.

Input:  tools/tile-bake/dist/salem_tiles.sqlite (207 MB — Esri+Mapnik+Salem-Custom z14-19)
Output: tools/tile-bake/dist/salem_tiles_trimmed.sqlite (Salem-Custom z16-19, city-only bbox)

Operations:
  1. Drop Esri-WorldImagery (satellite) — 55 MB
  2. Drop Mapnik (street) — 29 MB
  3. Drop Salem-Custom z14/z15 — 4.2 MB (per feedback_tile_zoom_levels.md)
  4. Filter Salem-Custom z16-19 to tiles intersecting Salem city bbox
  5. Re-encode kept tiles as WebP q=78 with full optimization (tighter than current)
"""

import sqlite3
import math
import subprocess
import sys
from pathlib import Path

# Salem city limits (whole city, not just downtown tour area)
SALEM_NORTH = 42.560
SALEM_SOUTH = 42.490
SALEM_WEST  = -70.945
SALEM_EAST  = -70.830

KEEP_PROVIDER = "Salem-Custom"
KEEP_ZOOMS    = (16, 17, 18, 19)

# WebP quality (current is probably ~80; drop to 78 with method=6 for tighter packing)
WEBP_QUALITY = 78

SRC = Path(__file__).parent / "dist" / "salem_tiles.sqlite"
DST = Path(__file__).parent / "dist" / "salem_tiles_trimmed.sqlite"


def decode_key(key):
    """osmdroid key = (z << 2z) | (x << z) | y. Zoom is encoded in its own shift."""
    # Try each possible zoom to find the one that makes x,y reasonable
    for z in range(0, 25):
        shift_z = 2 * z
        shift_x = z
        zoom_part = key >> shift_z
        if zoom_part == z:
            x_y = key & ((1 << shift_z) - 1) if shift_z > 0 else 0
            x = (x_y >> shift_x) if shift_x > 0 else 0
            y = x_y & ((1 << shift_x) - 1) if shift_x > 0 else x_y
            if 0 <= x < (1 << z) and 0 <= y < (1 << z):
                return z, x, y
    return None


def tile_bounds(z, x, y):
    """Return (south, north, west, east) for a tile."""
    n = 2 ** z
    lon_w = x / n * 360.0 - 180.0
    lon_e = (x + 1) / n * 360.0 - 180.0
    lat_n = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * y / n))))
    lat_s = math.degrees(math.atan(math.sinh(math.pi * (1 - 2 * (y + 1) / n))))
    return lat_s, lat_n, lon_w, lon_e


def intersects_salem(z, x, y):
    s, n, w, e = tile_bounds(z, x, y)
    return n >= SALEM_SOUTH and s <= SALEM_NORTH and e >= SALEM_WEST and w <= SALEM_EAST


def recompress_webp(blob):
    """Re-encode a WebP blob at tighter settings via cwebp. Returns new blob."""
    try:
        result = subprocess.run(
            ["cwebp", "-q", str(WEBP_QUALITY), "-m", "6", "-quiet",
             "-o", "-", "--", "/dev/stdin"],
            input=blob,
            capture_output=True,
            check=True,
            timeout=10,
        )
        return result.stdout
    except (subprocess.CalledProcessError, FileNotFoundError, subprocess.TimeoutExpired):
        # Fallback: imagemagick
        try:
            result = subprocess.run(
                ["convert", "-", "-quality", str(WEBP_QUALITY),
                 "-define", "webp:method=6", "webp:-"],
                input=blob,
                capture_output=True,
                check=True,
                timeout=10,
            )
            return result.stdout
        except Exception as e:
            return blob  # give up, keep original


def main():
    if DST.exists():
        DST.unlink()

    src = sqlite3.connect(str(SRC))
    dst = sqlite3.connect(str(DST))

    dst.execute("CREATE TABLE tiles (key INTEGER, provider TEXT, tile BLOB, PRIMARY KEY (key, provider));")

    stats = {
        "total_read": 0,
        "dropped_provider": 0,
        "dropped_zoom": 0,
        "dropped_bbox": 0,
        "kept": 0,
        "bytes_in": 0,
        "bytes_out": 0,
        "bytes_in_kept_original": 0,
    }

    cur = src.execute("SELECT key, provider, tile FROM tiles")
    insert = "INSERT INTO tiles (key, provider, tile) VALUES (?, ?, ?)"
    batch = []
    for key, provider, tile in cur:
        stats["total_read"] += 1
        stats["bytes_in"] += len(tile)

        if provider != KEEP_PROVIDER:
            stats["dropped_provider"] += 1
            continue

        decoded = decode_key(key)
        if decoded is None:
            stats["dropped_bbox"] += 1
            continue
        z, x, y = decoded

        if z not in KEEP_ZOOMS:
            stats["dropped_zoom"] += 1
            continue

        if not intersects_salem(z, x, y):
            stats["dropped_bbox"] += 1
            continue

        stats["bytes_in_kept_original"] += len(tile)
        new_tile = recompress_webp(tile)
        stats["bytes_out"] += len(new_tile)
        stats["kept"] += 1
        batch.append((key, provider, new_tile))

        if len(batch) >= 500:
            dst.executemany(insert, batch)
            batch = []
            print(f"  ... {stats['kept']} kept, {stats['dropped_provider']} prov, {stats['dropped_zoom']} zoom, {stats['dropped_bbox']} bbox", flush=True)

    if batch:
        dst.executemany(insert, batch)

    dst.commit()
    # VACUUM to reclaim space
    dst.execute("VACUUM")
    dst.close()
    src.close()

    print("\n=== Trim summary ===")
    print(f"Total read:        {stats['total_read']:>6}")
    print(f"  dropped provider: {stats['dropped_provider']:>6}  (Esri + Mapnik)")
    print(f"  dropped zoom:     {stats['dropped_zoom']:>6}  (z14, z15)")
    print(f"  dropped bbox:     {stats['dropped_bbox']:>6}  (outside Salem city)")
    print(f"  kept:             {stats['kept']:>6}")
    print(f"Input size:   {stats['bytes_in']/1024/1024:>6.1f} MB")
    print(f"Kept src:     {stats['bytes_in_kept_original']/1024/1024:>6.1f} MB (before re-encode)")
    print(f"Kept out:     {stats['bytes_out']/1024/1024:>6.1f} MB (after WebP q={WEBP_QUALITY} m=6)")

    out_file_size = DST.stat().st_size / 1024 / 1024
    print(f"DB on disk:   {out_file_size:>6.1f} MB")
    print(f"Reduction:    {stats['bytes_in']/1024/1024 - out_file_size:>6.1f} MB "
          f"({out_file_size/(stats['bytes_in']/1024/1024)*100:.1f}% of original)")


if __name__ == "__main__":
    main()
