#!/usr/bin/env python3
"""S286 Phase 3 — compress Historical-YYYY tile rows from PNG → WebP in-place.

Per feedback_webp_lossy_beats_lossless_for_basemap.md, quality=70 method=6 is
the proven sweet spot for all basemap tile types. We preserve the alpha channel
(historical tiles have transparent paper backgrounds) by encoding as RGBA WebP.

Only touches rows where provider LIKE 'Historical-%'. The modern Salem-Custom
basemap is already shipping in its own format and is left untouched.

Run from anywhere — paths are absolute.
"""
import io
import multiprocessing as mp
import os
import sqlite3
import sys
import time
from PIL import Image

DB = "/home/witchdoctor/Development/LocationMapApp_v1.5/tools/tile-bake/dist/salem_tiles.sqlite"
QUALITY = 70
METHOD = 6
PROC_COUNT = max(1, (os.cpu_count() or 4) - 2)


def encode_one(args):
    key, provider, png_blob = args
    img = Image.open(io.BytesIO(png_blob))
    # Preserve alpha — historical tiles have transparent paper outside the
    # map's geographic extent.
    if img.mode != "RGBA":
        img = img.convert("RGBA")
    buf = io.BytesIO()
    img.save(buf, format="WEBP", quality=QUALITY, method=METHOD)
    return key, provider, buf.getvalue(), len(png_blob), buf.tell()


def main():
    if not os.path.exists(DB):
        sys.exit(f"missing {DB}")

    backup = DB + ".preWebP"
    if not os.path.exists(backup):
        print(f"Backing up to {backup} ...")
        import shutil
        shutil.copy(DB, backup)
    else:
        print(f"Backup already exists at {backup}; reusing.")

    src = sqlite3.connect(DB)
    cur = src.cursor()
    counts = cur.execute(
        "SELECT provider, COUNT(*) FROM tiles WHERE provider LIKE 'Historical-%' GROUP BY provider"
    ).fetchall()
    print("Before:")
    for prov, n in counts:
        print(f"  {prov}: {n} tiles")
    total = sum(n for _, n in counts)
    if total == 0:
        sys.exit("No Historical-* rows; nothing to do.")
    print(f"Total to convert: {total}")

    cur.execute(
        "SELECT key, provider, tile FROM tiles WHERE provider LIKE 'Historical-%'"
    )
    rows = cur.fetchall()
    src.close()

    print(f"Encoding with {PROC_COUNT} workers (quality={QUALITY}, method={METHOD})…")
    t0 = time.time()
    src_bytes = 0
    dst_bytes = 0
    done = 0
    last_log = 0

    dst = sqlite3.connect(DB)
    dst.execute("PRAGMA journal_mode = WAL")
    update = dst.cursor()

    with mp.Pool(PROC_COUNT) as pool:
        for key, provider, webp_blob, src_n, dst_n in pool.imap_unordered(encode_one, rows, chunksize=64):
            update.execute(
                "UPDATE tiles SET tile = ? WHERE key = ? AND provider = ?",
                (webp_blob, key, provider),
            )
            src_bytes += src_n
            dst_bytes += dst_n
            done += 1
            if done - last_log >= 2000:
                elapsed = time.time() - t0
                rate = done / elapsed
                eta = (total - done) / rate if rate else 0
                pct_save = 100.0 * (1 - dst_bytes / src_bytes)
                print(
                    f"  {done}/{total} ({100*done/total:.1f}%) — "
                    f"{rate:.0f} tiles/s — eta {eta:.0f}s — "
                    f"size {src_bytes/1024/1024:.0f}MB → {dst_bytes/1024/1024:.0f}MB "
                    f"(-{pct_save:.1f}%)"
                )
                last_log = done

    dst.commit()
    print("VACUUM …")
    dst.execute("PRAGMA journal_mode = DELETE")
    dst.execute("VACUUM")
    dst.close()

    elapsed = time.time() - t0
    pct_save = 100.0 * (1 - dst_bytes / src_bytes) if src_bytes else 0
    print(
        f"\nDone in {elapsed:.0f}s — {done} tiles — "
        f"{src_bytes/1024/1024:.1f} MB → {dst_bytes/1024/1024:.1f} MB "
        f"({pct_save:.1f}% smaller)"
    )
    final_size = os.path.getsize(DB)
    print(f"salem_tiles.sqlite final size: {final_size/1024/1024:.1f} MB")


if __name__ == "__main__":
    main()
