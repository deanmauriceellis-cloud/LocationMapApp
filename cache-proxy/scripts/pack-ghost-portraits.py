#!/usr/bin/env python3
"""
S275 Katrina's Collection — downscale + pack ghost portraits for app delivery.

Source dir (768x768 PNG) -> Output dir (384x384 webp q85). Source-of-truth stays in
the input dir untouched; output is what ships in the app asset pack.

Why webp q85: ~30-50 KB per image vs ~700 KB PNG source. 214 images -> ~10 MB total
instead of ~150 MB. Still crisp at the ~125dp tile size at xxxhdpi.

Usage:
  python3 cache-proxy/scripts/pack-ghost-portraits.py
  python3 cache-proxy/scripts/pack-ghost-portraits.py --size 256 --quality 80
  python3 cache-proxy/scripts/pack-ghost-portraits.py --input <src> --output <dst>
"""
import argparse
import shutil
import sys
import time
from pathlib import Path

from PIL import Image


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "--input",
        type=Path,
        default=Path.home() / "AI-Studio" / "ghost-batch-v1",
        help="source dir of 768x768 PNGs (also reads manifest.json)",
    )
    ap.add_argument(
        "--output",
        type=Path,
        default=Path.home() / "AI-Studio" / "ghost-batch-v1-app",
        help="destination dir for the packed webp + manifest copy",
    )
    ap.add_argument("--size", type=int, default=384, help="target edge px (square)")
    ap.add_argument("--quality", type=int, default=85, help="webp quality 0-100")
    ap.add_argument(
        "--pattern",
        type=str,
        default="ghost_*.png",
        help="glob pattern in input dir (e.g. 'frame_*.png' for frames)",
    )
    args = ap.parse_args()

    if not args.input.is_dir():
        sys.exit(f"Input dir does not exist: {args.input}")

    args.output.mkdir(parents=True, exist_ok=True)

    pngs = sorted(args.input.glob(args.pattern))
    if not pngs:
        sys.exit(f"No '{args.pattern}' files in {args.input}")

    src_bytes = 0
    dst_bytes = 0
    t0 = time.time()
    for src in pngs:
        dst = args.output / f"{src.stem}.webp"
        with Image.open(src) as im:
            # Frames have an alpha channel that must be preserved; portraits don't.
            mode = "RGBA" if "A" in im.getbands() else "RGB"
            im = im.convert(mode)
            if im.size != (args.size, args.size):
                im = im.resize((args.size, args.size), Image.LANCZOS)
            save_kwargs = {"format": "WEBP", "quality": args.quality, "method": 6}
            if mode == "RGBA":
                save_kwargs["lossless"] = False   # lossy alpha keeps it tiny
            im.save(dst, **save_kwargs)
        src_bytes += src.stat().st_size
        dst_bytes += dst.stat().st_size

    # Copy manifest alongside so the packed dir is self-contained
    manifest_src = args.input / "manifest.json"
    if manifest_src.exists():
        shutil.copy2(manifest_src, args.output / "manifest.json")

    elapsed = time.time() - t0
    n = len(pngs)
    print(f"Packed {n} images in {elapsed:.1f}s")
    print(f"  src: {src_bytes / 1024 / 1024:7.1f} MB  ({src_bytes // n // 1024} KB/img avg)")
    print(f"  dst: {dst_bytes / 1024 / 1024:7.1f} MB  ({dst_bytes // n // 1024} KB/img avg)")
    print(f"  reduction: {(1 - dst_bytes / src_bytes) * 100:.1f}%")
    print(f"Output: {args.output}")


if __name__ == "__main__":
    main()
