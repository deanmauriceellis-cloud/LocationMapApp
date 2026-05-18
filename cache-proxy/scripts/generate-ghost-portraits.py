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
OVERRIDES_FILE = REPO_ROOT / "cache-proxy" / "data" / "ghost-persona-overrides.json"
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
    # Anti-living-person — pass 5: the model defaults "woman" subject to "pretty alive
    # woman in costume". Punish hard.
    "solid opaque body, opaque skin, living person, alive, real human, flesh, "
    "physical body, mortal, healthy complexion, normal skin, ruddy cheeks, rosy cheeks, "
    "beautiful woman, pretty girl, attractive face, smiling model, "
    "healthy young woman, photogenic, beauty shot, fashion portrait, cosplay, "
    "lifelike eyes, bright eyes, sparkling eyes, glossy lips, makeup, "
    # Wrong era — pass 3 produced a Halloween-bedsheet ghost in a suburban yard. Punish.
    "modern clothing, modern setting, contemporary, jeans, t-shirt, "
    "suburban houses, modern street, sneakers, hoodie, baseball cap, "
    "neon, plastic, christmas, easter, generic halloween costume, "
    "Casper the friendly ghost, white bedsheet ghost, "
    # Realism push-back.
    "photorealistic, photograph, realistic, photo, real life, hyperrealism, "
    "detailed pores, real skin texture, 3D render, octane render, raytraced, "
    "watercolor, oil painting, "
    # PG-13 floor.
    "gore, blood, demon, satanic, nude, naked, suggestive, sexual, "
    # Subject hygiene.
    "multiple figures, multiple ghosts, group, crowd, "
    "full body, lower body, legs, hands deformed, extra limbs, extra fingers, "
    "crossed eyes, asymmetric eyes, mutated, "
    # Tech.
    "low quality, noisy, grainy, oversaturated, blurry, "
    "text, watermark, signature, logo"
)

# S278 pass 4 — operator: "Make them spookier! They are ghosts from 300+ years ago,
# they should look like they are from 1600-1700's. The backgrounds should be more
# haunted too!" Three reinforced axes: (a) period wardrobe HIGH-WEIGHT and global
# (so pool-rolled professions like "Town Drunk" still get puritan dress), (b) spookier
# — gaunt, sunken, centuries-dead, uncanny, (c) specific 1690s Salem haunted scenery
# replaces generic Halloween night.
PROMPT_TEMPLATE = (
    # Ghost-ness first — pass 5: stronger spectral language, multiple synonyms so the
    # signal survives all the way through the period+wardrobe+background description.
    "(wraith:1.5), (specter:1.5), (revenant haunting:1.4), "
    "(translucent ghost spirit:1.6), (ethereal apparition from the 1690s:1.45), "
    "(centuries-dead three-hundred-year-old spirit:1.4), "
    "(transparent body fading into smoke wisps at the bottom:1.35), "
    "(gaunt sunken hollow-cheeked corpse-face:1.45), "
    "(corpse-grey ashen skin:1.4), (hollow black eye-sockets staring out:1.4), "
    "(deathly pallor:1.3), "
    # Period wardrobe — global, weighted, so pool-rolled professions inherit it.
    "(1690s puritan colonial wardrobe:1.4), "
    "tattered linen ruff collar and weather-worn wool tunic, "
    "lace coif cap or buckled wide-brim hat or starched white collar, "
    "weathered colonial cloth fraying at the edges, "
    # Cartoon style.
    "(2D cel-shaded cartoon illustration:1.4), (thick black outlines:1.2), "
    "(flat saturated colors:1.15), "
    # The subject.
    "spectral cartoon portrait of the lingering spirit of a {profession}, "
    "Salem Massachusetts 1692 witch-trials ghost, "
    "{hair} hair drifting weightlessly, ({oddity}:1.25), "
    "{prop}, "
    "{expression}, "
    # Composition.
    "head-and-shoulders apparition, single subject, centered composition, "
    "body dissolving into curling spirit-smoke at the shoulders, no legs visible, "
    # Haunted 1690s Salem background — specific and weighted so the model commits.
    "(haunted 1690s Salem village background:1.3), "
    "leaning gambrel-roofed colonial saltbox houses with dark casement windows, "
    "crooked weathered slate gravestones half-tilted in the dirt, "
    "gnarled bare oak trees with twisted branches, "
    "low fog drifting over cobblestone lane, oil-lamp flicker behind warped panes, "
    "dead leaves swirling, twisted iron weathervane, no modern objects, "
    # Style spine.
    "Coraline meets Hocus Pocus meets Over the Garden Wall aesthetic, "
    "muted autumnal palette of cold-grey ink-black plum amber bone and sickly witch-green, "
    "uncanny moonlit dusk, "
    "PG-13, painterly cartoon, hand-drawn 2D animation"
)

# B is face-only inpaint. S278 pass 6 — operator: "B images too extreme, need subtler".
# Goals: (a) the B should read as the SAME ghost as A with a micro-expression change,
# not as a different character. (b) The fourth-wall break is a hint, not a stunt.
# Dropped "exaggerated", "stretched mouth", "oversized". Lowered expression weight
# 1.5 → 1.15. Pool-side change: extreme grins removed (see ghost-personas-seed.json).
PROMPT_TEMPLATE_B = (
    "(translucent ghost face:1.3), (spectral pale skin:1.2), "
    "(2D cel-shaded cartoon:1.35), (thick black outlines:1.2), "
    "({expression}:1.15), "
    "subtle micro-expression, same face as before, "
    "Coraline meets Hocus Pocus aesthetic, PG-13"
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
        "prop": rng.choice(seed["witchy_props"]),
        "frame": rng.choice(seed["frames"]),
    }


def build_prompt(persona: dict, variant: str) -> str:
    if variant == "b":
        return PROMPT_TEMPLATE_B.format(expression=persona["expression_b"])
    return PROMPT_TEMPLATE.format(
        profession=persona["profession"],
        hair=persona["hair"],
        oddity=persona["oddity"],
        prop=persona.get("prop") or "wisps of spirit-smoke curling at the shoulders",
        expression=persona["expression_a"],
    )


def generate_txt2img(prompt: str, seed_val: int, out_path: Path, *, width: int, height: int) -> tuple[float, str]:
    """Returns (elapsed_s, base64_png) — caller writes the file and may reuse the b64."""
    payload = {
        "prompt": prompt,
        "negative_prompt": NEG,
        "steps": 8,
        "cfg_scale": 4.5,
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
        "cfg_scale": 4.5,
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
        default=0.45,
        help="face-inpaint denoise for B (lower = subtler expression change, higher = more obvious). "
             "S278: lowered default 0.75 → 0.45 per operator 'B too extreme'.",
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
            # S278: re-roll expression_b from the current pool using the SAME per-POI RNG.
            # This catches updates to the expressions_alt pool (e.g., when extreme entries
            # are removed). All other persona attributes stay fixed to maintain A↔B
            # continuity (same character, only mouth/eye micro-expression changes).
            poi_seed_key = row.get("poi_id") or row["slug"]
            poi_rng = random.Random(poi_seed_key)
            # Burn the same five rolls roll_persona did when A was generated, so we
            # land on the same point in the RNG stream that roll_persona would for
            # expression_b. Order in roll_persona: profession, expression_a, expression_b,
            # oddity, hair, prop, frame. We re-roll expression_b only.
            poi_rng.choice(seed["professions"])     # burn profession roll
            poi_rng.choice(seed["expressions"])     # burn expression_a roll
            new_expression_b = poi_rng.choice(seed["expressions_alt"])  # the one we want
            persona = {
                "profession": row["profession"],
                "expression_a": row["expression_a"],
                "expression_b": new_expression_b,
                "oddity": row["oddity"],
                "hair": row["hair"],
                "prop": row.get("prop"),
                "frame": row.get("frame"),
                "frame_shape": row.get("frame_shape"),
                "frame_metal": row.get("frame_metal"),
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
        print(f"[regen-b] {len(targets)} targets from existing manifest (re-rolled expression_b from current pool)", flush=True)
    elif args.from_pg:
        pois = fetch_hist_bldg_pois()
        if args.limit:
            pois = pois[: args.limit]
        # S278: per-POI profession overrides mined from SI clues + LMA prose.
        # Hair/expression/oddity/frame still pool-rolled deterministically per poi_id.
        # POIs absent from the overrides file get the full random-pool roll (S275 behavior).
        overrides: dict[str, dict] = {}
        if OVERRIDES_FILE.exists():
            overrides = json.loads(OVERRIDES_FILE.read_text())
            print(f"[overrides] loaded {len(overrides)} profession overrides from {OVERRIDES_FILE.name}", flush=True)
        else:
            print(f"[overrides] {OVERRIDES_FILE.name} not found — full random-pool fallback", flush=True)
        print(f"[pg] {len(pois)} HISTORICAL_BUILDINGS POIs", flush=True)
        overridden = 0
        targets = []
        for poi in pois:
            poi_rng = random.Random(poi["poi_id"])
            persona = roll_persona(seed, poi_rng)
            ov = overrides.get(poi["poi_id"])
            if ov and ov.get("profession"):
                persona["profession"] = ov["profession"]
                persona["_profession_source"] = ov.get("_si_clue") or "override"
                overridden += 1
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
        print(f"[overrides] {overridden}/{len(pois)} POIs got profession override; {len(pois) - overridden} use pool roll", flush=True)
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
