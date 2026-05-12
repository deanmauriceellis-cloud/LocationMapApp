#!/usr/bin/env python3
"""pack-pmtiles.py — pack a per-layer WebP tile tree into a single PMTiles archive.

Reads:  <in_root>/{z}/{x}/{y}.webp
Writes: <out_path>.pmtiles  (single file, HTTP range-request servable)

PMTiles eliminates per-file filesystem block overhead. At CONUS scale (~10M files across 5
layers), loose-tree slack would be ~40 GB; PMTiles packs them tight.

Pipeline contract: touches only tools/tigerbase/. Each invocation handles one layer.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

from pmtiles.tile import Compression, TileType, zxy_to_tileid
from pmtiles.writer import Writer


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--in", dest="in_root", required=True,
                   help="Per-layer tile tree root (contains {z}/{x}/{y}.webp)")
    p.add_argument("--out", required=True, help="Output .pmtiles path")
    p.add_argument("--name", required=True, help="Archive name written to metadata")
    p.add_argument("--bbox", required=True, help="minx,miny,maxx,maxy")
    p.add_argument("--minzoom", type=int, required=True)
    p.add_argument("--maxzoom", type=int, required=True)
    p.add_argument("--attribution", default="© U.S. Census Bureau TIGER/Line 2023 (public domain)")
    args = p.parse_args()

    in_root = Path(args.in_root)
    if not in_root.is_dir():
        print(f"missing input dir: {in_root}", file=sys.stderr)
        return 2

    minx, miny, maxx, maxy = (float(x) for x in args.bbox.split(","))
    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)

    t0 = time.time()
    tile_count = 0
    bytes_total = 0

    with open(out_path, "wb") as f:
        w = Writer(f)
        # Iterate in canonical PMTiles tileid order (writer needs increasing tileid for compact directories)
        tile_paths: list[tuple[int, Path]] = []
        for zdir in in_root.iterdir():
            if not zdir.is_dir() or not zdir.name.isdigit():
                continue
            z = int(zdir.name)
            for xdir in zdir.iterdir():
                if not xdir.is_dir() or not xdir.name.isdigit():
                    continue
                x = int(xdir.name)
                for ytile in xdir.glob("*.webp"):
                    y = int(ytile.stem)
                    tile_paths.append((zxy_to_tileid(z, x, y), ytile))

        tile_paths.sort(key=lambda t: t[0])
        for tileid, path in tile_paths:
            data = path.read_bytes()
            w.write_tile(tileid, data)
            tile_count += 1
            bytes_total += len(data)

        header = {
            "tile_type": TileType.WEBP,
            "tile_compression": Compression.NONE,
            "min_zoom": args.minzoom,
            "max_zoom": args.maxzoom,
            "min_lon_e7": int(minx * 1e7),
            "min_lat_e7": int(miny * 1e7),
            "max_lon_e7": int(maxx * 1e7),
            "max_lat_e7": int(maxy * 1e7),
            "center_zoom": (args.minzoom + args.maxzoom) // 2,
            "center_lon_e7": int(((minx + maxx) / 2) * 1e7),
            "center_lat_e7": int(((miny + maxy) / 2) * 1e7),
        }
        metadata = {
            "name": args.name,
            "description": "TigerBase tile layer baked from TIGER/Line shapefiles",
            "attribution": args.attribution,
            "type": "overlay",
            "version": "1",
            "format": "webp",
        }
        w.finalize(header, metadata)

    size_mb = out_path.stat().st_size / 1024 / 1024
    print(f"[pmtiles] {out_path.name}: {tile_count} tiles, "
          f"{bytes_total / 1024 / 1024:.2f} MB raw, "
          f"{size_mb:.2f} MB archive, "
          f"{time.time() - t0:.1f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
