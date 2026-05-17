#!/usr/bin/env python3
"""
S275 Katrina's Collection — ghost portrait generator (paper-napkin validation pass).

Pulls attribute pools from cache-proxy/data/ghost-personas-seed.json, rolls N random
ghost personas, and generates a paired set of portraits per ghost:
  - A: primary portrait using a normal expression
  - B: alt portrait, SAME seed, only expression word swapped to a fourth-wall-break smirk

Pass 3 (S275): A is txt2img; B is **masked img2img (face inpaint)** with A as the init
image. A soft elliptical mask covers the face region so only the face is regenerated —
background stays bit-identical to A. Pass 2 (full img2img) drifted backgrounds more than
faces because random noise hits backgrounds harder than constrained face features.

Style spine shifted to Salem 1692 witch trials + Halloween animated cartoon (Tim Burton
meets Hotel Transylvania) per operator S275 direction. Watercolor framing dropped.

Usage:
  python3 cache-proxy/scripts/generate-ghost-portraits.py [--count 5] [--outdir <path>]

Default outdir: ~/AI-Studio/ghost-validation/
"""
import argparse
import base64
import hashlib
import io
import json
import os
import random
import re
import sys
import time
from pathlib import Path

import psycopg2
import requests
from PIL import Image, ImageDraw, ImageFilter

REPO_ROOT = Path(__file__).resolve().parents[2]
SEED_FILE = REPO_ROOT / "cache-proxy" / "data" / "ghost-personas-seed.json"
ENV_FILE = REPO_ROOT / "cache-proxy" / ".env"


def load_database_url() -> str:
    if "DATABASE_URL" in os.environ:
        return os.environ["DATABASE_URL"]
    if ENV_FILE.exists():
        for line in ENV_FILE.read_text().splitlines():
            m = re.match(r"^\s*DATABASE_URL\s*=\s*(.+)\s*$", line)
            if m:
                val = m.group(1).strip().strip('"').strip("'")
                return val
    sys.exit("DATABASE_URL not set and not found in cache-proxy/.env")


def fetch_hist_bldg_pois() -> list[dict]:
    """Pull the V1 ghost-eligible POI set (HISTORICAL_BUILDINGS, not soft-deleted)."""
    with psycopg2.connect(load_database_url()) as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, name
              FROM salem_pois
             WHERE category = 'HISTORICAL_BUILDINGS'
               AND deleted_at IS NULL
             ORDER BY id
            """
        )
        return [{"poi_id": r[0], "name": r[1]} for r in cur.fetchall()]

FORGE = "http://localhost:7860"
CHECKPOINT = "shared/DreamShaperXL_Turbo_v2_1.safetensors [4496b36d48]"

NEG = (
    "photorealistic, photograph, realistic, watercolor, "
    "modern clothing, modern setting, contemporary, "
    "horror, gore, scary, creepy, blood, demon, satanic, "
    "multiple figures, multiple ghosts, group, crowd, "
    "full body, lower body, legs, hands deformed, extra limbs, extra fingers, "
    "crossed eyes, asymmetric eyes, weird anatomy, mutated, ugly, "
    "low quality, noisy, grainy, oversaturated, blurry, "
    "text, watermark, signature, logo"
)

PROMPT_TEMPLATE = (
    "stylized Halloween animated cartoon portrait, "
    "translucent ethereal ghost of a {profession} from Salem Massachusetts 1692 witch trials era, "
    "{hair} hair, {oddity}, "
    "{expression}, "
    "head-and-shoulders portrait, single subject, centered composition, "
    "soft glowing ghostly wisps, 1690s puritan colonial dress, "
    "cel-shaded animation style, Tim Burton meets Hotel Transylvania aesthetic, "
    "dark autumnal palette of black plum amber and bone, "
    "candlelit dusk atmosphere, hint of Halloween Salem setting "
    "(autumn leaves or a crow or a small pumpkin in periphery), "
    "PG-13, soft rim light"
)

# B prompt is face-focused only — clothing/body/setting are bit-locked by the inpaint,
# so wasting tokens on them dilutes the expression signal. Weight the expression hard.
PROMPT_TEMPLATE_B = (
    "({expression}:1.5), "
    "ghost face looking directly at viewer, exaggerated facial expression, "
    "cel-shaded animation style, Tim Burton meets Hotel Transylvania aesthetic, "
    "PG-13"
)


def wait_for_forge(timeout_s: int = 300) -> None:
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        try:
            r = requests.get(f"{FORGE}/sdapi/v1/sd-models", timeout=3)
            if r.status_code == 200:
                return
        except Exception:
            pass
        print(".", end="", flush=True)
        time.sleep(2)
    print()
    sys.exit("Forge did not come up within timeout.")


def select_checkpoint() -> None:
    r = requests.post(
        f"{FORGE}/sdapi/v1/options",
        json={"sd_model_checkpoint": CHECKPOINT},
        timeout=600,
    )
    r.raise_for_status()


def roll_persona(seed: dict, rng: random.Random) -> dict:
    """Roll one ghost persona — shared attributes + paired A/B expressions + one frame overlay."""
    return {
        "profession": rng.choice(seed["professions"]),
        "expression_a": rng.choice(seed["expressions"]),
        "expression_b": rng.choice(seed["expressions_alt"]),
        "oddity": rng.choice(seed["oddities"]),
        "hair": rng.choice(seed["hair"]),
        "frame": rng.choice(seed["frames"]),
    }


def build_prompt(persona: dict, variant: str) -> str:
    if variant == "b":
        return PROMPT_TEMPLATE_B.format(expression=persona["expression_b"])
    return PROMPT_TEMPLATE.format(
        profession=persona["profession"],
        hair=persona["hair"],
        oddity=persona["oddity"],
        expression=persona["expression_a"],
    )


def generate_txt2img(prompt: str, seed_val: int, out_path: Path, *, width: int, height: int) -> tuple[float, str]:
    """Returns (elapsed_s, base64_png) — caller writes the file and may reuse the b64."""
    payload = {
        "prompt": prompt,
        "negative_prompt": NEG,
        "steps": 8,
        "cfg_scale": 2.5,
        "sampler_name": "DPM++ SDE",
        "scheduler": "Karras",
        "width": width,
        "height": height,
        "seed": seed_val,
    }
    t0 = time.time()
    r = requests.post(f"{FORGE}/sdapi/v1/txt2img", json=payload, timeout=600)
    r.raise_for_status()
    img_b64 = r.json()["images"][0]
    out_path.write_bytes(base64.b64decode(img_b64))
    return time.time() - t0, img_b64


def build_face_mask(width: int, height: int) -> str:
    """Soft elliptical mask over the face region — white = inpaint, black = preserve.

    Mask covers forehead + eyes + nose + mouth + jaw + neck so the inpaint can do open
    grins, head tilts, and full expression changes without being constrained to micro
    eye-area edits. Hair, shoulders, clothing, and background stay locked.
    Returns base64-encoded PNG.
    """
    mask = Image.new("L", (width, height), 0)
    draw = ImageDraw.Draw(mask)
    # Face+jaw ellipse: centered horizontally, weighted upper-half vertically.
    cx, cy = width // 2, int(height * 0.42)
    rx, ry = int(width * 0.32), int(height * 0.38)
    draw.ellipse((cx - rx, cy - ry, cx + rx, cy + ry), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(radius=width * 0.05))
    buf = io.BytesIO()
    mask.save(buf, format="PNG")
    return base64.b64encode(buf.getvalue()).decode()


def generate_img2img_face_inpaint(
    prompt: str,
    init_b64: str,
    mask_b64: str,
    seed_val: int,
    out_path: Path,
    *,
    width: int,
    height: int,
    denoising_strength: float = 0.55,
) -> float:
    """Generate B by inpainting only the face region of A — background stays bit-identical."""
    payload = {
        "prompt": prompt,
        "negative_prompt": NEG,
        "init_images": [init_b64],
        "mask": mask_b64,
        "mask_blur": 16,
        "inpainting_fill": 1,           # 1 = original — preserves edges and lighting
        "inpaint_full_res": True,       # refine just the mask region for sharper face
        "inpaint_full_res_padding": 32,
        "inpainting_mask_invert": 0,    # white = inpaint, black = keep
        "denoising_strength": denoising_strength,
        "steps": 14,
        "cfg_scale": 2.5,
        "sampler_name": "DPM++ SDE",
        "scheduler": "Karras",
        "width": width,
        "height": height,
        "seed": seed_val,
        "resize_mode": 0,
    }
    t0 = time.time()
    r = requests.post(f"{FORGE}/sdapi/v1/img2img", json=payload, timeout=600)
    r.raise_for_status()
    img_b64 = r.json()["images"][0]
    out_path.write_bytes(base64.b64decode(img_b64))
    return time.time() - t0


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--count", type=int, default=5, help="random validation: number of ghost pairs")
    ap.add_argument(
        "--from-pg",
        action="store_true",
        help="full-batch mode: pull the 107 HIST_BLDG POIs from PG, deterministic per-POI seeding",
    )
    ap.add_argument(
        "--outdir",
        type=Path,
        default=None,
        help="default: ~/AI-Studio/ghost-validation (random) or ~/AI-Studio/ghost-batch-v1 (--from-pg)",
    )
    ap.add_argument("--size", type=int, default=768, help="portrait safe-square edge px")
    ap.add_argument("--master-seed", type=int, default=None, help="lock the random roller (validation)")
    ap.add_argument(
        "--denoise",
        type=float,
        default=0.75,
        help="face-inpaint denoise for B (lower = subtler expression change, higher = more obvious)",
    )
    ap.add_argument(
        "--regen-b",
        action="store_true",
        help="reuse existing A PNGs in --outdir (or default outdir) and only regenerate B. "
             "Loads manifest.json for persona/seed, skips A generation entirely.",
    )
    ap.add_argument(
        "--limit",
        type=int,
        default=None,
        help="--from-pg only: cap the batch (useful for testing the wiring on a few POIs first)",
    )
    args = ap.parse_args()

    if args.outdir is None:
        args.outdir = Path.home() / "AI-Studio" / (
            "ghost-batch-v1" if (args.from_pg or args.regen_b) else "ghost-validation"
        )
    args.outdir.mkdir(parents=True, exist_ok=True)
    seed = json.loads(SEED_FILE.read_text())

    # Three modes:
    #  - validation:  roll N random personas, slug = ghost_test_<i>
    #  - --from-pg :  load 107 HIST_BLDG, seed RNG per-POI by poi_id, slug = ghost_<poi_id>
    #  - --regen-b :  rehydrate targets from existing manifest, only re-generate B images
    if args.regen_b:
        manifest_path = args.outdir / "manifest.json"
        if not manifest_path.exists():
            sys.exit(f"--regen-b requires {manifest_path} to exist")
        existing = json.loads(manifest_path.read_text())
        targets = []
        for row in existing:
            persona = {
                "profession": row["profession"],
                "expression_a": row["expression_a"],
                "expression_b": row["expression_b"],
                "oddity": row["oddity"],
                "hair": row["hair"],
                # New schema: row may have 'frame' dict; old schema had frame_shape + frame_metal
                "frame": row.get("frame"),
                "frame_shape": row.get("frame_shape"),    # legacy, kept for back-compat
                "frame_metal": row.get("frame_metal"),    # legacy, kept for back-compat
            }
            targets.append(
                {
                    "slug": row["slug"],
                    "poi_id": row.get("poi_id"),
                    "poi_name": row.get("poi_name"),
                    "sd_seed": row["sd_seed"],
                    "persona": persona,
                }
            )
        print(f"[regen-b] {len(targets)} targets from existing manifest", flush=True)
    elif args.from_pg:
        pois = fetch_hist_bldg_pois()
        if args.limit:
            pois = pois[: args.limit]
        print(f"[pg] {len(pois)} HISTORICAL_BUILDINGS POIs", flush=True)
        targets = []
        for poi in pois:
            poi_rng = random.Random(poi["poi_id"])
            persona = roll_persona(seed, poi_rng)
            targets.append(
                {
                    "slug": f"ghost_{poi['poi_id']}",
                    "poi_id": poi["poi_id"],
                    "poi_name": poi["name"],
                    # poi_id is a text slug; hash to int32 for the SD seed (still fully reproducible per-POI)
                    "sd_seed": int(hashlib.sha1(poi["poi_id"].encode()).hexdigest()[:8], 16),
                    "persona": persona,
                }
            )
    else:
        rng = random.Random(args.master_seed)
        targets = []
        for i in range(args.count):
            persona = roll_persona(seed, rng)
            targets.append(
                {
                    "slug": f"ghost_test_{i + 1:03d}",
                    "poi_id": None,
                    "poi_name": None,
                    "sd_seed": rng.randint(1, 2**31 - 1),
                    "persona": persona,
                }
            )

    if args.limit:
        targets = targets[: args.limit]
        print(f"[limit] capped at {len(targets)}", flush=True)

    # Manifest sits next to the PNGs so future-me / operator can map image -> persona.
    manifest_path = args.outdir / "manifest.json"

    print(f"[forge] connecting to {FORGE} ...", flush=True)
    wait_for_forge()
    print()
    print(f"[forge] selecting checkpoint: {CHECKPOINT}", flush=True)
    select_checkpoint()

    face_mask_b64 = build_face_mask(args.size, args.size)

    manifest = []
    n = len(targets)
    t_start = time.time()
    for i, t in enumerate(targets, start=1):
        persona = t["persona"]
        slug = t["slug"]
        sd_seed = t["sd_seed"]
        label = t["poi_name"] or persona["profession"]
        print(
            f"\n[ghost {i}/{n}] {label}  seed={sd_seed} "
            f"({persona['profession']} / {persona['hair']} hair / {persona['oddity']})",
            flush=True,
        )
        # A: txt2img from prompt — OR rehydrate from existing file in --regen-b mode
        path_a = args.outdir / f"{slug}_a.png"
        if args.regen_b:
            if not path_a.exists():
                print(f"  [A] MISSING {path_a.name} — skipping ghost", flush=True)
                continue
            img_a_b64 = base64.b64encode(path_a.read_bytes()).decode()
            print(f"  [A] reused existing {path_a.name}", flush=True)
        else:
            prompt_a = build_prompt(persona, "a")
            elapsed_a, img_a_b64 = generate_txt2img(
                prompt_a, sd_seed, path_a, width=args.size, height=args.size
            )
            print(f"  [A] expr=\"{persona['expression_a']}\"  ({elapsed_a:.1f}s) -> {path_a.name}", flush=True)

        # B: face-only inpaint of A with new expression — background stays bit-identical
        prompt_b = build_prompt(persona, "b")
        path_b = args.outdir / f"{slug}_b.png"
        elapsed_b = generate_img2img_face_inpaint(
            prompt_b,
            img_a_b64,
            face_mask_b64,
            sd_seed,
            path_b,
            width=args.size,
            height=args.size,
            denoising_strength=args.denoise,
        )
        print(
            f"  [B] expr=\"{persona['expression_b']}\"  denoise={args.denoise}  "
            f"({elapsed_b:.1f}s) -> {path_b.name}",
            flush=True,
        )

        manifest.append(
            {
                "slug": slug,
                "poi_id": t["poi_id"],
                "poi_name": t["poi_name"],
                "sd_seed": sd_seed,
                **persona,
                "frame_slug": persona["frame"]["slug"] if isinstance(persona.get("frame"), dict) else None,
                "frame_asset": persona["frame"]["asset"] if isinstance(persona.get("frame"), dict) else None,
                "assets": {"a": f"{slug}_a.png", "b": f"{slug}_b.png"},
            }
        )

    # In --regen-b mode the existing manifest is authoritative — don't clobber it,
    # the persona records didn't change, only the B PNG bytes did.
    if not args.regen_b:
        manifest_path.write_text(json.dumps(manifest, indent=2))
        print(f"Manifest: {manifest_path}")
    total = time.time() - t_start
    print(f"\nDone. {n} ghosts processed in {args.outdir}  ({total:.0f}s, {total/n:.1f}s/ghost)")


if __name__ == "__main__":
    main()
