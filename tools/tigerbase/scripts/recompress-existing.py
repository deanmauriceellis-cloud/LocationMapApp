#!/usr/bin/env python3
"""recompress-existing.py — re-encode WebP tiles in-place with the current
per-layer LAYER_ENCODE settings from bake-tigerbase.py.

Use after tuning compression so previously-baked zooms shrink without re-rendering.
Walks <root>/<layer>/{z}/{x}/{y}.webp, decodes each tile, re-saves with the new options.
Skips tiles whose new size would be larger than the existing (defensive).
"""
from __future__ import annotations

import argparse
import multiprocessing as mp
import os
import sys
import time
from pathlib import Path

from PIL import Image

# Import LAYER_ENCODE from the renderer so we stay in sync.
_HERE = Path(__file__).parent
sys.path.insert(0, str(_HERE))
from importlib.util import spec_from_file_location, module_from_spec
_spec = spec_from_file_location("bake_tigerbase", _HERE / "bake-tigerbase.py")
_mod = module_from_spec(_spec)
_spec.loader.exec_module(_mod)
LAYER_ENCODE = _mod.LAYER_ENCODE
KNOWN_LAYERS = _mod.ALL_LAYERS


def _recompress_one(args):
    layer, p_str = args
    p = Path(p_str)
    try:
        old_size = p.stat().st_size
        img = Image.open(p).convert("RGBA")
        encode = LAYER_ENCODE.get(layer, {"quality": 70, "method": 6})
        tmp = p.with_suffix(".webp.new")
        img.save(tmp, "WEBP", **encode)
        new_size = tmp.stat().st_size
        if new_size < old_size:
            os.replace(tmp, p)
            return (1, old_size - new_size)
        else:
            tmp.unlink()
            return (0, 0)
    except Exception as e:
        return (-1, 0)


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--root", required=True, help="Tile-tree root (parent of <layer>/{z}/{x}/{y}.webp)")
    p.add_argument("--layer", choices=("all",) + tuple(KNOWN_LAYERS), default="all")
    p.add_argument("--workers", type=int, default=max(1, min(8, (os.cpu_count() or 4) - 2)))
    args = p.parse_args()

    root = Path(args.root)
    layers = list(KNOWN_LAYERS) if args.layer == "all" else [args.layer]

    grand_files = 0
    grand_saved = 0
    grand_replaced = 0
    grand_failed = 0
    t0 = time.time()

    for layer in layers:
        ldir = root / layer
        if not ldir.exists():
            print(f"[recompress] {layer}: no dir, skip")
            continue
        # Collect all webp paths
        paths = []
        for z_dir in ldir.iterdir():
            if not z_dir.is_dir() or not z_dir.name.isdigit():
                continue
            for x_dir in z_dir.iterdir():
                if not x_dir.is_dir() or not x_dir.name.isdigit():
                    continue
                for p in x_dir.glob("*.webp"):
                    paths.append((layer, str(p)))
        if not paths:
            print(f"[recompress] {layer}: 0 tiles")
            continue
        before_bytes = sum(Path(p).stat().st_size for _, p in paths)
        print(f"[recompress] {layer}: {len(paths)} tiles, {before_bytes / 1024 / 1024:.1f} MB before")

        ctx = mp.get_context("fork")
        with ctx.Pool(args.workers) as pool:
            results = pool.imap_unordered(_recompress_one, paths, chunksize=max(1, len(paths) // (args.workers * 8)))
            replaced = 0; saved = 0; failed = 0
            for status, delta in results:
                if status == 1:
                    replaced += 1; saved += delta
                elif status == -1:
                    failed += 1
            print(f"[recompress] {layer}: replaced {replaced}/{len(paths)} tiles, "
                  f"saved {saved / 1024 / 1024:.1f} MB ({100*saved/max(1,before_bytes):.1f}%) "
                  f"failed={failed}")
            grand_files += len(paths)
            grand_saved += saved
            grand_replaced += replaced
            grand_failed += failed

    print(f"[recompress] TOTAL: {grand_replaced}/{grand_files} replaced, "
          f"saved {grand_saved / 1024 / 1024:.1f} MB total, "
          f"failed={grand_failed}, "
          f"wall={time.time() - t0:.1f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
