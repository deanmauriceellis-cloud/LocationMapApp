#!/usr/bin/env python3
"""
Full triptych campaign — generates heroes for every POI in salem_pois.

Reads the bundled Room DB, iterates active POIs, generates a triptych per POI
using prompts_lib.build_prompts(). Resume-friendly: skips any POI with an
existing output file. Writes category-grouped HTML gallery incrementally.

Usage:
    python3 generate_full.py                        # all 1,837 active POIs
    python3 generate_full.py --limit 20             # test run
    python3 generate_full.py --narrated-only        # only POIs with short_narration
    python3 generate_full.py --categories WITCH_SHOP,WORSHIP
"""

import argparse
import base64
import io
import sqlite3
import sys
import time
import zlib
from pathlib import Path

import numpy as np
import requests
from PIL import Image

from prompts_lib import build_prompts, STYLE, NEG

# easyocr — lazy-init; only loaded on first OCR call. Uses CUDA if available.
_OCR_READER = None


def get_ocr_reader():
    global _OCR_READER
    if _OCR_READER is None:
        print("Loading easyocr model (CUDA)...", flush=True)
        import easyocr
        _OCR_READER = easyocr.Reader(["en"], gpu=True, verbose=False)
        print("easyocr ready.", flush=True)
    return _OCR_READER


def panel_has_text(img: Image.Image, min_conf: float = 0.20, min_chars: int = 2) -> tuple[bool, list]:
    """
    Return (has_text, detected_list). Detected text is filtered by confidence +
    minimum character length so we don't trip on stray 1-pixel scratches.
    """
    arr = np.array(img.convert("RGB"))
    reader = get_ocr_reader()
    results = reader.readtext(arr)
    hits = [(t, c) for (_bb, t, c) in results if c >= min_conf and len(t.strip()) >= min_chars]
    return (len(hits) > 0), hits

SCRIPT_DIR = Path(__file__).parent
OUT_DIR = SCRIPT_DIR / "output-full"
PANELS_DIR = OUT_DIR / "panels"
TRIP_DIR = OUT_DIR / "triptychs"
FAILED_DIR = OUT_DIR / "failed"

for d in (OUT_DIR, PANELS_DIR, TRIP_DIR, FAILED_DIR):
    d.mkdir(parents=True, exist_ok=True)

FORGE = "http://127.0.0.1:7860"
CHECKPOINT = "shared/DreamShaperXL_Turbo_v2_1.safetensors [4496b36d48]"

DB_PATH = (
    Path.home()
    / "Development/LocationMapApp_v1.5/app-salem/src/main/assets/salem_content.db"
)

GALLERY_REBUILD_EVERY = 25  # POIs — how often to refresh gallery.html


def poi_seed(poi_id: str, panel_idx: int) -> int:
    return (zlib.crc32(poi_id.encode()) * 10 + panel_idx) % 2_000_000_000 + 10_000


def load_pois(narrated_only: bool = False) -> list[dict]:
    if not DB_PATH.exists():
        print(f"ERROR: DB not found at {DB_PATH}", file=sys.stderr)
        sys.exit(1)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    q = """
        SELECT id, name, category, subcategory, historical_period,
               year_established, short_narration, data_source
        FROM salem_pois
        WHERE (data_source NOT LIKE '%dedup%' OR data_source IS NULL)
    """
    if narrated_only:
        q += " AND short_narration IS NOT NULL AND short_narration != ''"
    q += " ORDER BY category, name"
    rows = conn.execute(q).fetchall()
    conn.close()
    return [dict(r) for r in rows]


def wait_for_forge():
    for _ in range(60):
        try:
            if requests.get(f"{FORGE}/sdapi/v1/sd-models", timeout=3).status_code == 200:
                return
        except Exception:
            pass
        time.sleep(1)
    print("Forge not reachable", file=sys.stderr)
    sys.exit(2)


def set_checkpoint():
    requests.post(
        f"{FORGE}/sdapi/v1/options",
        json={"sd_model_checkpoint": CHECKPOINT},
        timeout=600,
    ).raise_for_status()


def gen_panel(prompt, seed, width, height):
    payload = {
        "prompt": f"{prompt}, {STYLE}",
        "negative_prompt": NEG,
        "steps": 8,
        "cfg_scale": 2.5,
        "sampler_name": "DPM++ SDE",
        "scheduler": "Karras",
        "width": width,
        "height": height,
        "seed": seed,
    }
    r = requests.post(f"{FORGE}/sdapi/v1/txt2img", json=payload, timeout=600)
    r.raise_for_status()
    return Image.open(io.BytesIO(base64.b64decode(r.json()["images"][0])))


def fit_crop(img, target_w, target_h):
    src_w, src_h = img.size
    if src_w / src_h > target_w / target_h:
        new_w = int(src_h * target_w / target_h)
        left = (src_w - new_w) // 2
        img = img.crop((left, 0, left + new_w, src_h))
    else:
        new_h = int(src_w * target_h / target_w)
        top = (src_h - new_h) // 2
        img = img.crop((0, top, src_w, top + new_h))
    return img.resize((target_w, target_h), Image.LANCZOS)


def bottom_crop(img, target_w, target_h):
    """
    Bottom-bias crop — keep only the bottom portion of the image.
    Used for panel 2 (exterior): SDXL Turbo compulsively paints signage onto
    the facade/awning/overhead-sign area. By physically cropping to the bottom
    ~45% of the frame (where the door + step + cat sit), the sign zone never
    makes it into the final composite.
    """
    src_w, src_h = img.size
    # Keep the bottom 55% of the source height, then center-crop horizontally
    # to the target aspect.
    keep_h = int(src_h * 0.55)
    top = src_h - keep_h
    keep_w = int(keep_h * target_w / target_h)
    if keep_w > src_w:
        keep_w = src_w
        keep_h = int(keep_w * target_h / target_w)
        top = src_h - keep_h
    left = (src_w - keep_w) // 2
    img = img.crop((left, top, left + keep_w, src_h))
    return img.resize((target_w, target_h), Image.LANCZOS)


def composite(p1, p2, p3):
    # S146: Now that OCR retry guarantees text-free panel 2, revert to center
    # crop so full-facade composition is preserved. bottom_crop() is retained
    # in the module as a fallback but no longer used.
    out = Image.new("RGB", (800, 200), (10, 10, 20))
    out.paste(fit_crop(p1, 160, 200), (0, 0))
    out.paste(fit_crop(p2, 320, 200), (160, 0))
    out.paste(fit_crop(p3, 320, 200), (480, 0))
    return out


def build_gallery(pois: list[dict], done: set[str]):
    """Build a category-grouped HTML gallery of every completed triptych."""
    by_cat: dict[str, list[dict]] = {}
    for p in pois:
        if p["id"] in done:
            by_cat.setdefault(p.get("category") or "UNKNOWN", []).append(p)

    cats_sorted = sorted(by_cat.keys(), key=lambda c: -len(by_cat[c]))

    parts = [
        """<!doctype html><html><head><meta charset=utf-8>
<title>Katrina Triptych Heroes — Full Campaign</title>
<style>
  body{margin:0;padding:20px;background:#111;color:#eee;font-family:system-ui,sans-serif;max-width:1400px;margin-left:auto;margin-right:auto}
  h1{margin:0 0 4px 0}
  .sub{color:#888;margin-bottom:20px;font-size:13px}
  .toc{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:24px;padding:12px;background:#1c1c20;border-radius:6px}
  .toc a{color:#e6b84c;text-decoration:none;font-size:12px;padding:4px 8px;background:#2a2a30;border-radius:4px}
  .toc a:hover{background:#3a3a40}
  .cat{margin-bottom:32px}
  .cat h2{margin:0 0 10px 0;padding:8px 12px;background:#2a2a30;color:#e6b84c;border-radius:4px;font-size:14px;letter-spacing:.05em}
  .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(400px,1fr));gap:8px}
  .card{background:#1c1c20;border:1px solid #2a2a30;border-radius:4px;overflow:hidden;font-size:11px}
  .card img{display:block;width:100%;height:auto;cursor:zoom-in}
  .card .name{padding:4px 8px;color:#ccc;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
  dialog{border:none;padding:0;background:transparent;max-width:95vw;max-height:95vh}
  dialog::backdrop{background:rgba(0,0,0,.92)}
  dialog img{max-width:95vw;max-height:95vh;display:block;image-rendering:pixelated}
</style></head><body>
<h1>Katrina Triptych Heroes — Full Campaign</h1>
"""
    ]
    parts.append(f'<div class=sub>{sum(len(v) for v in by_cat.values())} POIs rendered · click a triptych to zoom</div>')
    parts.append('<div class=toc>')
    for c in cats_sorted:
        parts.append(f'<a href="#cat-{c}">{c} ({len(by_cat[c])})</a>')
    parts.append('</div>')

    dlg_id = 0
    for cat in cats_sorted:
        parts.append(f'<div class=cat id="cat-{cat}"><h2>{cat} — {len(by_cat[cat])}</h2><div class=grid>')
        for p in sorted(by_cat[cat], key=lambda x: x["name"]):
            dlg_id += 1
            trip = f"triptychs/{p['id']}.webp"
            name_esc = p['name'].replace('<', '&lt;').replace('>', '&gt;')
            parts.append(
                f'<div class=card>'
                f'<img src="{trip}" loading=lazy onclick="document.getElementById(\'d{dlg_id}\').showModal()">'
                f'<div class=name title="{name_esc}">{name_esc}</div>'
                f'</div>'
                f'<dialog id=d{dlg_id} onclick="this.close()"><img src="{trip}"></dialog>'
            )
        parts.append('</div></div>')

    parts.append("</body></html>\n")
    (OUT_DIR / "gallery.html").write_text("".join(parts))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, help="Limit to first N POIs")
    ap.add_argument("--narrated-only", action="store_true")
    ap.add_argument("--categories", type=str, help="Comma-separated category filter")
    ap.add_argument("--sample-per-category", type=int,
                    help="Instead of all POIs, pick up to N per category for a preview batch")
    args = ap.parse_args()

    print(f"Forge at {FORGE}...", flush=True)
    wait_for_forge()
    set_checkpoint()
    print("Forge ready.", flush=True)

    pois = load_pois(narrated_only=args.narrated_only)
    if args.categories:
        allowed = {c.strip().upper() for c in args.categories.split(",")}
        pois = [p for p in pois if (p.get("category") or "").upper() in allowed]

    if args.sample_per_category:
        per_cat: dict[str, list[dict]] = {}
        for p in pois:
            per_cat.setdefault(p.get("category") or "UNKNOWN", []).append(p)
        sampled = []
        for cat, entries in per_cat.items():
            # Deterministic sample: take the first N in alphabetical order
            for e in sorted(entries, key=lambda x: (x.get("name") or ""))[: args.sample_per_category]:
                sampled.append(e)
        pois = sampled

    if args.limit:
        pois = pois[: args.limit]

    total = len(pois)
    done = {f.stem for f in TRIP_DIR.glob("*.webp")}
    todo = [p for p in pois if p["id"] not in done]
    print(f"POIs: {total} total · {len(done)} already done · {len(todo)} remaining.", flush=True)
    print(f"Est. runtime at 14s/POI: {len(todo) * 14 / 60:.0f} min.", flush=True)

    t_start = time.time()
    generated = 0
    failed = 0

    for i, poi in enumerate(todo, start=1):
        poi_id = poi["id"]

        try:
            cat_scene, exterior, monster = build_prompts(poi)
            seeds = [poi_seed(poi_id, 0), poi_seed(poi_id, 1), poi_seed(poi_id, 2)]

            t0 = time.time()
            p1 = gen_panel(cat_scene, seeds[0], 832, 1216)

            # S146: Panel 2 uses OCR retry loop. SDXL Turbo is strongly biased
            # to paint gibberish text onto any facade / storefront surface.
            # Solution: generate, OCR-scan the image, if text detected retry
            # with next seed. Max 5 retries. On the 6th "still has text" result
            # we accept the last attempt rather than block the campaign.
            p2 = None
            p2_retries = 0
            for attempt in range(6):
                seed_try = seeds[1] + attempt * 97  # spread seeds widely
                candidate = gen_panel(exterior, seed_try, 1216, 832)
                has_text, hits = panel_has_text(candidate)
                if not has_text:
                    p2 = candidate
                    seeds[1] = seed_try
                    break
                p2 = candidate
                seeds[1] = seed_try
                p2_retries += 1
                hit_sample = ", ".join(f"{t!r}({c:.2f})" for t, c in hits[:3])
                print(f"    [P2 retry {attempt + 1}/6] OCR hit: {hit_sample}", flush=True)

            p3 = gen_panel(monster, seeds[2], 1216, 832)

            p1.save(PANELS_DIR / f"{poi_id}_p1_katrina.webp", "WEBP", quality=82)
            p2.save(PANELS_DIR / f"{poi_id}_p2_exterior.webp", "WEBP", quality=82)
            p3.save(PANELS_DIR / f"{poi_id}_p3_monster.webp", "WEBP", quality=82)

            trip = composite(p1, p2, p3)
            trip.save(TRIP_DIR / f"{poi_id}.webp", "WEBP", quality=85)

            generated += 1
            done.add(poi_id)
            kb = (TRIP_DIR / f"{poi_id}.webp").stat().st_size / 1024
            elapsed_total = time.time() - t_start
            rate = generated / elapsed_total if elapsed_total > 0 else 0
            remaining = len(todo) - generated
            eta_min = remaining / rate / 60 if rate > 0 else 0
            name_short = (poi['name'] or '')[:42]
            cat_short = (poi.get('category') or '')[:12]
            retry_tag = f" P2r={p2_retries}" if p2_retries > 0 else ""
            print(
                f"[{i}/{len(todo)}] {name_short:<42} ({cat_short:<12}) "
                f"{time.time() - t0:.1f}s {kb:.0f}KB{retry_tag}  "
                f"rate={rate * 60:.1f}/min eta={eta_min:.0f}min",
                flush=True,
            )

            if generated % GALLERY_REBUILD_EVERY == 0:
                build_gallery(pois, done)

            # S146 auto-abort per operator Q4: after 50 attempts, if failure
            # rate exceeds 10% kill the run so the overnight budget isn't
            # wasted on a broken pipeline.
            attempted = generated + failed
            if attempted >= 50 and (failed / attempted) > 0.10:
                print(
                    f"\n══ AUTO-ABORT ══  attempted={attempted} failed={failed} "
                    f"rate={failed / attempted:.1%} > 10%. Stopping campaign.",
                    flush=True,
                )
                build_gallery(pois, done)
                sys.exit(3)

        except KeyboardInterrupt:
            print("\nInterrupted. Partial progress saved. Re-run with same args to resume.", flush=True)
            build_gallery(pois, done)
            sys.exit(130)
        except Exception as e:
            failed += 1
            try:
                with open(FAILED_DIR / f"{poi_id}.txt", "w") as fp:
                    fp.write(f"{poi_id}\n{poi['name']}\n{e!r}\n")
            except Exception:
                pass
            print(f"[{i}/{len(todo)}] {poi['name'][:42]} FAILED: {e!r}", flush=True)

    build_gallery(pois, done)

    elapsed = time.time() - t_start
    print(f"\n════════════════════════════════════════════════════════════", flush=True)
    print(f"Campaign complete.", flush=True)
    print(f"  generated this run: {generated}", flush=True)
    print(f"  failed:             {failed}", flush=True)
    print(f"  total done:         {len(done)} / {total}", flush=True)
    print(f"  time:               {elapsed/60:.1f} min", flush=True)
    print(f"  gallery:            file://{(OUT_DIR / 'gallery.html').resolve()}", flush=True)


if __name__ == "__main__":
    main()
