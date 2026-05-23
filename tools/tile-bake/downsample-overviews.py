#!/usr/bin/env python3
"""Downsample Salem-Custom tiles from z14 to lower zooms (z13, z12, z11).

For each z-1 tile (x', y'), stitch the 4 z tiles at (2x', 2y'), (2x'+1, 2y'),
(2x', 2y'+1), (2x'+1, 2y'+1) into a 512x512 canvas, then downsample to 256x256
with bilinear filtering. Encode as WebP q=70 method=6 (same as parent z14 settings
per feedback_webp_lossy_beats_lossless_for_basemap.md) and insert as a new row.

Key encoding matches osmdroid SqliteArchive format:
    key = (((z << z) + x) << z) + y

Usage: downsample-overviews.py [DB_PATH] [--target-zoom MIN_Z]
  Default DB: app-salem-tiles-pack/src/main/assets/salem_tiles.sqlite
  Default target: z11 (downsamples z14 → z13 → z12 → z11)
"""
import argparse
import io
import sqlite3
import sys
import time
from PIL import Image

PROVIDER = 'Salem-Custom'

def key(z, x, y):
    return (((z << z) + x) << z) + y

def decode_key(k, z):
    mask = (1 << z) - 1
    y = k & mask
    x = (k >> z) - (z << z)
    return x, y

def downsample_one_zoom(con, z_src, z_dst):
    """Generate all z_dst tiles by stitching+downsampling z_src tiles."""
    cur = con.cursor()
    # Read all source keys (z_src tiles)
    rows = cur.execute(
        "SELECT key, tile FROM tiles WHERE provider=?", (PROVIDER,)
    ).fetchall()
    # Filter to z_src by key range. The osmdroid encoding is unique per z so
    # we can identify z from the key by trying each z.
    src_tiles = {}   # (x, y) → bytes
    for k, blob in rows:
        # The key uniquely identifies a (z, x, y). To find the z that this key
        # was encoded with, try z_src first (we know the source zoom).
        x, y = decode_key(k, z_src)
        if 0 <= x < (1 << z_src) and 0 <= y < (1 << z_src):
            recon = (((z_src << z_src) + x) << z_src) + y
            if recon == k:
                src_tiles[(x, y)] = blob

    print(f"  z={z_src}: {len(src_tiles)} source tiles found")

    # Group by destination (x', y') = (x//2, y//2)
    dst_groups = {}
    for (x, y) in src_tiles:
        dst = (x // 2, y // 2)
        dst_groups.setdefault(dst, set()).add((x, y))

    print(f"  z={z_dst}: {len(dst_groups)} destination tiles to generate")

    insert = con.cursor()
    written = 0
    skipped_existing = 0
    t0 = time.time()
    for (dst_x, dst_y), src_set in dst_groups.items():
        dst_key = key(z_dst, dst_x, dst_y)
        # Skip if already present (idempotent rerun)
        if insert.execute(
            "SELECT 1 FROM tiles WHERE key=? AND provider=? LIMIT 1",
            (dst_key, PROVIDER)
        ).fetchone():
            skipped_existing += 1
            continue

        # Stitch 2x2 quadrants. Missing quadrants stay transparent.
        canvas = Image.new('RGBA', (512, 512), (0, 0, 0, 0))
        for dx in (0, 1):
            for dy in (0, 1):
                src = (dst_x * 2 + dx, dst_y * 2 + dy)
                if src in src_tiles:
                    img = Image.open(io.BytesIO(src_tiles[src])).convert('RGBA')
                    if img.size != (256, 256):
                        img = img.resize((256, 256), Image.BILINEAR)
                    canvas.paste(img, (dx * 256, dy * 256))
        # Downsample to 256x256
        out = canvas.resize((256, 256), Image.BILINEAR)
        # Encode WebP q=70 method=6 (same as basemap's existing setting)
        buf = io.BytesIO()
        out.save(buf, format='WEBP', quality=70, method=6)
        insert.execute(
            "INSERT INTO tiles (key, provider, tile) VALUES (?, ?, ?)",
            (dst_key, PROVIDER, buf.getvalue())
        )
        written += 1
        if written % 50 == 0:
            print(f"    ... wrote {written}/{len(dst_groups) - skipped_existing} z{z_dst} tiles ({time.time()-t0:.1f}s)")

    con.commit()
    print(f"  z={z_dst}: wrote {written} tiles ({skipped_existing} already present, skipped) in {time.time()-t0:.1f}s")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('db', nargs='?',
                    default='/home/witchdoctor/Development/LocationMapApp_v1.5/app-salem-tiles-pack/src/main/assets/salem_tiles.sqlite')
    ap.add_argument('--target-zoom', type=int, default=11,
                    help='Lowest zoom to generate (default: 11). Will downsample z14 → z13 → ... → target.')
    ap.add_argument('--source-zoom', type=int, default=14,
                    help='Source zoom to downsample from (default: 14).')
    args = ap.parse_args()

    print(f"DB: {args.db}")
    print(f"Downsampling Salem-Custom from z={args.source_zoom} down to z={args.target_zoom}")

    con = sqlite3.connect(args.db)
    # Each zoom step downsamples from the prior one (so z13 uses z14, z12 uses z13, etc.)
    for z_src in range(args.source_zoom, args.target_zoom, -1):
        z_dst = z_src - 1
        print(f"\n=== Pass: z={z_src} → z={z_dst} ===")
        downsample_one_zoom(con, z_src, z_dst)

    # Final stats
    print(f"\n=== Final ===")
    for z in range(args.target_zoom, args.source_zoom + 1):
        # Need to count tiles whose key decodes to this z. Cheaper: SELECT all
        # keys, decode each. Or just SELECT count for z keys (rough).
        rows = con.execute(
            "SELECT key FROM tiles WHERE provider=?", (PROVIDER,)
        ).fetchall()
        n = 0
        for (k,) in rows:
            x, y = decode_key(k, z)
            if 0 <= x < (1 << z) and 0 <= y < (1 << z):
                recon = (((z << z) + x) << z) + y
                if recon == k:
                    n += 1
        print(f"  z{z}: {n} tiles")

    sz = con.execute(
        "SELECT page_count * page_size FROM pragma_page_count(), pragma_page_size()"
    ).fetchone()
    print(f"\nDB size: {sz[0]/1024/1024:.1f} MB")
    con.execute("VACUUM")
    sz = con.execute(
        "SELECT page_count * page_size FROM pragma_page_count(), pragma_page_size()"
    ).fetchone()
    print(f"After VACUUM: {sz[0]/1024/1024:.1f} MB")
    con.close()


if __name__ == '__main__':
    main()
