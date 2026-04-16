#!/usr/bin/env python3
"""
generate-npc-portraits.py — Phase 9X.6 Pass B

Pencil-sketch portrait generation for the 49 Tier-1/2 figures in
salem_witch_trials_npc_bios. Reads the SD prompt tails already distilled
by Oracle (Pass A.2) from
  cache-proxy/out/npc-appearance-cache.json
and submits each to Forge's /sdapi/v1/txt2img endpoint.

Saves grayscale JPGs (q80) to:
  app-salem/src/main/assets/portraits/{id}.jpg

Writes status after each generation to:
  /tmp/s132-portrait-status.json

The live gallery at /tmp/s132-gallery/ serves on :4311 and polls that
status file every 2 seconds to fill in cards as portraits complete.

Usage:
  python3 generate-npc-portraits.py                      # run all missing
  python3 generate-npc-portraits.py --force              # regenerate all
  python3 generate-npc-portraits.py --only=tituba,stoughton_...
  python3 generate-npc-portraits.py --model=RealVisXL_V5.0_fp16.safetensors
  python3 generate-npc-portraits.py --size=1024          # render bigger, downscale to 512
  python3 generate-npc-portraits.py --seed=12345 --only=tituba  # deterministic re-render

The script never deletes existing output files on its own — re-runs are
opt-in via --force or --only.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import io
import json
import os
import signal
import sys
import time
from pathlib import Path

try:
    import requests
except ImportError:
    print("[fatal] pip3 install requests Pillow", file=sys.stderr)
    sys.exit(1)
try:
    from PIL import Image
except ImportError:
    print("[fatal] pip3 install Pillow", file=sys.stderr)
    sys.exit(1)


# --- paths -----------------------------------------------------------------

REPO = Path(__file__).resolve().parents[2]
CACHE_PATH = REPO / "cache-proxy" / "out" / "npc-appearance-cache.json"
PRODUCTION_OUTPUT_DIR = REPO / "app-salem" / "src" / "main" / "assets" / "portraits"
GALLERY_DIR = Path("/tmp/s132-gallery")
PRODUCTION_STATUS_PATH = GALLERY_DIR / "status.json"

# When --label=<tag> is set, portraits and status go here instead so multiple
# model runs can be compared side-by-side in the gallery's /compare.html tabs.
# Otherwise the script writes to the production location (bundled into APK).
def label_output_dir(label: str) -> Path:
    return GALLERY_DIR / "compare" / label / "portraits"
def label_status_path(label: str) -> Path:
    return GALLERY_DIR / "compare" / label / "status.json"

# Variety subset for quick cross-model comparison: covers male/female, young/old,
# judge/accuser/accused/clergy, light/dark skin, and social class extremes.
TEST_SUBSET_IDS = [
    "william_stoughton",   # male, 61, JUDGE, periwig + justaucorps (top of society)
    "tituba",              # woman of color, indigenous/Caribbean, ACCUSED, domestic servant
    "ann_putnam_jr",       # girl, 13, ACCUSER, coif + dark bodice (afflicted-girl archetype)
    "rebecca_nurse",       # elderly woman, 71, ACCUSED, frail goodwife (iconic victim)
    "dorcas_good",         # child, 4, ACCUSED, rags (destitute)
]

FORGE_URL = os.environ.get("FORGE_URL", "http://localhost:7860")

# --- prompt template -------------------------------------------------------

TEMPLATE_HEAD = (
    "pencil sketch portrait, graphite on paper, head and shoulders, "
    "historical illustration, 1692 colonial Massachusetts Bay Colony, "
    "plain neutral paper background, period-accurate dress, fine "
    "crosshatching, detailed line work, black and white, monochrome "
    "graphite shading, no color, consistent illustration style, portrait "
    "centered, bust composition, "
)

NEGATIVE = (
    "color, colored, painted, watercolor, oil painting, photograph, "
    "photorealistic, modern clothing, modern hairstyle, sunglasses, watch, "
    "phone, anime, cartoon, 3d render, cgi, text, watermark, signature, "
    "caption, frame, border, full body, landscape background, scenery, "
    "dungeon, chains, prison, courtroom, multiple people, group, crowd, "
    "blurry, low quality, distorted face, asymmetric eyes, extra fingers, "
    "bad anatomy, cropped, out of frame, jewelry, makeup, lipstick"
)

# --- role colors (match the Kotlin browser) --------------------------------

ROLE_COLORS = {
    "JUDGE":    {"hex": "#c73b3b", "name": "scarlet"},
    "ACCUSER":  {"hex": "#c9a84c", "name": "gold"},
    "ACCUSED":  {"hex": "#9a9a9a", "name": "gray"},
    "CLERGY":   {"hex": "#8866aa", "name": "purple"},
    "OFFICIAL": {"hex": "#b0b0c0", "name": "silver"},
    "OTHER":    {"hex": "#708090", "name": "slate"},
}


def role_type_of(role: str, faction: str | None) -> str:
    """Mirror of Kotlin roleTypeOf() heuristic."""
    s = (role or "").lower() + " " + (faction or "").lower()
    if any(w in s for w in ["judge", "examiner", "justice", "magistrate"]):
        return "JUDGE"
    if any(w in s for w in ["minister", "reverend", "clergy"]) or s.startswith("rev_"):
        return "CLERGY"
    if any(w in s for w in ["afflicted", "accuser", "complaint filer", "parish clerk"]) or "putnam/accuser" in s:
        return "ACCUSER"
    if any(w in s for w in ["accused", "hanged", "executed", "pressed", "prison", "confessor", "recanter", "victim"]):
        return "ACCUSED"
    if any(w in s for w in ["governor", "constable", "physician", "deputy", "militia", "merchant", "intellectual"]):
        return "OFFICIAL"
    return "OTHER"


# --- status file -----------------------------------------------------------

def load_status(status_path: Path) -> dict:
    if status_path.exists():
        try:
            return json.loads(status_path.read_text())
        except Exception:
            pass
    return {"figures": {}, "started_at": None, "updated_at": None, "total": 0, "done": 0, "failed": 0, "skipped": 0}


def write_status(status: dict, status_path: Path) -> None:
    status["updated_at"] = time.strftime("%Y-%m-%d %H:%M:%S")
    status_path.parent.mkdir(parents=True, exist_ok=True)
    status_path.write_text(json.dumps(status, indent=2))


# --- Forge txt2img ---------------------------------------------------------

def forge_options() -> dict:
    r = requests.get(f"{FORGE_URL}/sdapi/v1/options", timeout=10)
    r.raise_for_status()
    return r.json()


def forge_set_model(checkpoint: str) -> None:
    print(f"[forge] switching checkpoint → {checkpoint}")
    r = requests.post(
        f"{FORGE_URL}/sdapi/v1/options",
        json={"sd_model_checkpoint": checkpoint},
        timeout=180,
    )
    r.raise_for_status()


def forge_txt2img(prompt: str, negative: str, seed: int, steps: int, cfg: float,
                   sampler: str, scheduler: str, width: int, height: int) -> Image.Image:
    payload = {
        "prompt": prompt,
        "negative_prompt": negative,
        "seed": seed,
        "steps": steps,
        "cfg_scale": cfg,
        "sampler_name": sampler,
        "scheduler": scheduler,
        "width": width,
        "height": height,
        "n_iter": 1,
        "batch_size": 1,
        "restore_faces": False,
        "tiling": False,
        "send_images": True,
        "save_images": False,
    }
    r = requests.post(f"{FORGE_URL}/sdapi/v1/txt2img", json=payload, timeout=600)
    r.raise_for_status()
    d = r.json()
    images = d.get("images") or []
    if not images:
        raise RuntimeError(f"Forge returned no images: {d.get('info','?')[:200]}")
    png_b64 = images[0]
    if "," in png_b64:
        png_b64 = png_b64.split(",", 1)[1]
    return Image.open(io.BytesIO(base64.b64decode(png_b64)))


def save_portrait(img: Image.Image, dest: Path, target_size: int = 512, jpg_quality: int = 82) -> int:
    """Convert to grayscale, resize to target_size², save as JPG. Returns bytes written."""
    if img.size != (target_size, target_size):
        img = img.resize((target_size, target_size), Image.LANCZOS)
    img = img.convert("L")  # grayscale
    dest.parent.mkdir(parents=True, exist_ok=True)
    img.save(dest, "JPEG", quality=jpg_quality, optimize=True, progressive=True)
    return dest.stat().st_size


# --- gallery -------------------------------------------------------------

def refresh_gallery_symlinks(output_dir: Path, gallery_portraits: Path) -> None:
    """Mirror output_dir/*.jpg into gallery_portraits/ for the HTTP server."""
    gallery_portraits.mkdir(parents=True, exist_ok=True)
    for f in output_dir.glob("*.jpg"):
        link = gallery_portraits / f.name
        if link.is_symlink() or link.exists():
            continue
        try:
            link.symlink_to(f)
        except Exception:
            pass


# --- main ----------------------------------------------------------------

def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--force", action="store_true", help="regenerate even if output file exists")
    p.add_argument("--only", type=str, default=None, help="comma-separated npc_ids to regenerate")
    p.add_argument("--test-subset", action="store_true",
                   help="restrict to the 5-figure variety set (Stoughton/Tituba/Ann/Rebecca/Dorcas). "
                        "Intended for cross-model comparison in the /compare gallery.")
    p.add_argument("--label", type=str, default=None,
                   help="When set, write portraits + status to /tmp/s132-gallery/compare/<label>/ "
                        "instead of the production APK location. Use per-model labels like "
                        "'realvisxl', 'juggernaut', 'dreamshaperxl-turbo', 'flux-dev'.")
    p.add_argument("--model", type=str, default=None, help="Forge checkpoint filename to switch to")
    p.add_argument("--seed", type=int, default=42,
                   help="base seed — combined with figure id hash so each figure is unique "
                        "but deterministic across runs. Use 0 for fully random.")
    p.add_argument("--steps", type=int, default=30)
    p.add_argument("--cfg", type=float, default=6.5)
    p.add_argument("--sampler", type=str, default="DPM++ 2M")
    p.add_argument("--scheduler", type=str, default="Karras")
    p.add_argument("--size", type=int, default=1024, help="generation resolution (downscaled to 512 for output)")
    p.add_argument("--target-size", type=int, default=512, help="saved image pixel size")
    p.add_argument("--jpg-quality", type=int, default=82)
    args = p.parse_args()

    # Resolve output dir + status path based on --label
    if args.label:
        output_dir = label_output_dir(args.label)
        status_path = label_status_path(args.label)
        gallery_portraits = output_dir  # compare.html reads images from compare/<label>/portraits/ directly
        run_scope = f"compare:{args.label}"
    else:
        output_dir = PRODUCTION_OUTPUT_DIR
        status_path = PRODUCTION_STATUS_PATH
        gallery_portraits = GALLERY_DIR / "portraits"
        run_scope = "production"

    # Seed gallery symlinks now so compare-tab refresh sees anything already on disk
    refresh_gallery_symlinks(output_dir, gallery_portraits)

    # Selection
    if args.test_subset:
        only = set(TEST_SUBSET_IDS)
    elif args.only:
        only = set(s.strip() for s in args.only.split(","))
    else:
        only = None

    if not CACHE_PATH.exists():
        print(f"[fatal] cache not found: {CACHE_PATH}", file=sys.stderr)
        return 1
    cache = json.loads(CACHE_PATH.read_text())
    figures = cache.get("figures", {})
    if not figures:
        print("[fatal] no figures in cache", file=sys.stderr)
        return 1

    # Probe Forge
    try:
        opts = forge_options()
        current_ckpt = opts.get("sd_model_checkpoint", "?")
        print(f"[forge] reachable at {FORGE_URL}; current checkpoint: {current_ckpt}")
    except Exception as e:
        print(f"[fatal] Forge /sdapi/v1/options unreachable: {e}", file=sys.stderr)
        return 1

    if args.model and args.model != current_ckpt:
        try:
            forge_set_model(args.model)
        except Exception as e:
            print(f"[fatal] model switch failed: {e}", file=sys.stderr)
            return 1

    # Figure out which figures to run
    all_ids = sorted(figures.keys())
    targets: list[str] = []
    for fid in all_ids:
        if only and fid not in only:
            continue
        dest = output_dir / f"{fid}.jpg"
        if dest.exists() and not args.force:
            continue
        fig = figures[fid]
        if not fig.get("sd_prompt", {}).get("tail"):
            print(f"[warn] {fid} has no sd_prompt.tail — skipping", file=sys.stderr)
            continue
        targets.append(fid)

    if not targets:
        print("[pass-b] nothing to do (all portraits exist — use --force to regenerate)")
        return 0

    print(f"[pass-b] {len(targets)} portraits to generate (of {len(figures)} figures)")

    status = load_status(status_path)
    status["started_at"] = time.strftime("%Y-%m-%d %H:%M:%S")
    status["total"] = len(targets)
    status["done"] = 0
    status["failed"] = 0
    status["skipped"] = 0
    status["generation"] = {
        "model": args.model or current_ckpt,
        "steps": args.steps,
        "cfg": args.cfg,
        "sampler": args.sampler,
        "scheduler": args.scheduler,
        "size": args.size,
        "target_size": args.target_size,
        "jpg_quality": args.jpg_quality,
        "template_head": TEMPLATE_HEAD,
        "negative": NEGATIVE,
    }
    # Preload every figure into status so gallery shows queued cards immediately
    for fid, fig in figures.items():
        role = role_type_of(fig.get("role"), fig.get("faction"))
        existing = status["figures"].get(fid, {})
        status["figures"][fid] = {
            **existing,
            "id": fid,
            "name": fig.get("name"),
            "role": role,
            "role_color": ROLE_COLORS[role]["hex"],
            "role_label": (fig.get("role") or "").split("—")[0].strip(),
            "tier": fig.get("tier"),
            "born_year": fig.get("born_year"),
            "died_year": fig.get("died_year"),
            "image_rel": f"portraits/{fid}.jpg" if (output_dir / f"{fid}.jpg").exists() else None,
            "state": existing.get("state") or ("done" if (output_dir / f"{fid}.jpg").exists() else "queued"),
        }
    # Mark targets as queued freshly
    for fid in targets:
        status["figures"][fid]["state"] = "queued"
        status["figures"][fid]["error"] = None
    write_status(status, status_path)
    refresh_gallery_symlinks(output_dir, gallery_portraits)

    # Install a clean interrupt handler so Ctrl-C or kill leaves a consistent status
    def on_sig(_s, _f):
        status["interrupted"] = True
        write_status(status, status_path)
        print("\n[pass-b] interrupted — partial results kept", file=sys.stderr)
        sys.exit(130)
    signal.signal(signal.SIGINT, on_sig)
    signal.signal(signal.SIGTERM, on_sig)

    # Go
    t_total = time.time()
    for i, fid in enumerate(targets, 1):
        fig = figures[fid]
        role = role_type_of(fig.get("role"), fig.get("faction"))
        tail = fig["sd_prompt"]["tail"]
        prompt = TEMPLATE_HEAD + tail
        if args.seed == 0:
            seed = int(time.time_ns() & 0x7FFFFFFF)
        else:
            fid_hash = int.from_bytes(hashlib.md5(fid.encode()).digest()[:4], "little")
            seed = (args.seed ^ fid_hash) & 0x7FFFFFFF

        status["figures"][fid]["state"] = "generating"
        status["figures"][fid]["started_at"] = time.strftime("%H:%M:%S")
        status["figures"][fid]["prompt"] = prompt
        status["figures"][fid]["seed"] = seed
        write_status(status, status_path)

        label = f"[{i}/{len(targets)}] {fig.get('name')} ({fid})"
        print(f"{label} ... seed={seed} ", end="", flush=True)
        t0 = time.time()
        try:
            img = forge_txt2img(
                prompt=prompt,
                negative=NEGATIVE,
                seed=seed,
                steps=args.steps,
                cfg=args.cfg,
                sampler=args.sampler,
                scheduler=args.scheduler,
                width=args.size,
                height=args.size,
            )
            dest = output_dir / f"{fid}.jpg"
            bytes_written = save_portrait(img, dest, target_size=args.target_size, jpg_quality=args.jpg_quality)
            refresh_gallery_symlinks(output_dir, gallery_portraits)
            elapsed = time.time() - t0
            status["done"] += 1
            status["figures"][fid]["state"] = "done"
            status["figures"][fid]["finished_at"] = time.strftime("%H:%M:%S")
            status["figures"][fid]["elapsed_ms"] = int(elapsed * 1000)
            status["figures"][fid]["image_rel"] = f"portraits/{fid}.jpg"
            status["figures"][fid]["bytes"] = bytes_written
            write_status(status, status_path)
            print(f"ok ({elapsed:.1f}s, {bytes_written/1024:.1f} KB)")
        except Exception as e:
            status["failed"] += 1
            status["figures"][fid]["state"] = "failed"
            status["figures"][fid]["error"] = str(e)[:500]
            write_status(status, status_path)
            print(f"FAIL: {e}")

    status["finished_at"] = time.strftime("%Y-%m-%d %H:%M:%S")
    write_status(status, status_path)
    total_elapsed = time.time() - t_total
    print(f"\n[pass-b] done in {total_elapsed:.0f}s — "
          f"{status['done']} ok, {status['failed']} failed, {status['skipped']} skipped")
    return 0 if status["failed"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
