#!/usr/bin/env python3
"""Pack an XYZ tile directory ({z}/{x}/{y}.png) into MBTiles (TMS y).

Used to take gdal2tiles.py --xyz output (which leaves a directory tree) and
fold it into an MBTiles container so merge-historical-into-bundle.js can
ingest it. The merge script flips TMS->XYZ at insert time, so we write TMS
y here to match standard MBTiles spec.

Usage: xyz_to_mbtiles.py <tiles_dir> <out.mbtiles> [--ext png|webp|jpg]
"""
import argparse
import os
import sqlite3
import sys


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("tiles_dir")
    ap.add_argument("out_mbtiles")
    ap.add_argument("--ext", default="png")
    args = ap.parse_args()

    if not os.path.isdir(args.tiles_dir):
        sys.exit(f"missing dir: {args.tiles_dir}")
    if os.path.exists(args.out_mbtiles):
        os.remove(args.out_mbtiles)

    conn = sqlite3.connect(args.out_mbtiles)
    cur = conn.cursor()
    cur.executescript(
        """
        CREATE TABLE metadata (name TEXT, value TEXT);
        CREATE TABLE tiles (zoom_level INTEGER, tile_column INTEGER,
                            tile_row INTEGER, tile_data BLOB);
        CREATE UNIQUE INDEX tile_index
            ON tiles (zoom_level, tile_column, tile_row);
        """
    )

    n = 0
    bytes_total = 0
    zoom_counts = {}
    for z_name in sorted(os.listdir(args.tiles_dir), key=lambda s: int(s) if s.isdigit() else -1):
        z_dir = os.path.join(args.tiles_dir, z_name)
        if not z_name.isdigit() or not os.path.isdir(z_dir):
            continue
        z = int(z_name)
        max_y = (1 << z) - 1
        for x_name in os.listdir(z_dir):
            x_dir = os.path.join(z_dir, x_name)
            if not x_name.isdigit() or not os.path.isdir(x_dir):
                continue
            x = int(x_name)
            for fname in os.listdir(x_dir):
                if not fname.endswith("." + args.ext):
                    continue
                y_xyz = int(fname.split(".")[0])
                y_tms = max_y - y_xyz
                with open(os.path.join(x_dir, fname), "rb") as f:
                    data = f.read()
                cur.execute(
                    "INSERT INTO tiles VALUES (?, ?, ?, ?)",
                    (z, x, y_tms, data),
                )
                n += 1
                bytes_total += len(data)
                zoom_counts[z] = zoom_counts.get(z, 0) + 1

    for k, v in [
        ("name", os.path.basename(args.out_mbtiles)),
        ("type", "overlay"),
        ("version", "1"),
        ("description", "Historical map tiles"),
        ("format", args.ext),
    ]:
        cur.execute("INSERT INTO metadata VALUES (?, ?)", (k, v))

    conn.commit()
    conn.close()

    print(f"wrote {n} tiles ({bytes_total / 1024 / 1024:.1f} MB) to {args.out_mbtiles}")
    for z in sorted(zoom_counts):
        print(f"  z{z}: {zoom_counts[z]} tiles")


if __name__ == "__main__":
    main()
