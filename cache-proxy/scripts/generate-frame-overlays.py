#!/usr/bin/env python3
"""
S275 Katrina's Collection — generate the 8 frame overlay PNGs.

Each frame is a rectangular decorative border with a baked-in top-center emblem
and a transparent interior, designed to layer over a ghost portrait in the badge tile.

Pipeline per frame:
  1. SD generate at 768x768 on a bright green background (DreamShaperXL Turbo)
  2. Chroma-key green pixels → alpha=0 (handles whatever SD did with the background)
  3. Force the inner 70% (centered rectangle) to alpha=0 — guarantees a portrait
     cutout regardless of whether SD produced one
  4. Save as RGBA PNG to ~/AI-Studio/ghost-frames-v1/

Reads frame slug + emblem hints from cache-proxy/data/ghost-personas-seed.json.
"""
import argparse
import base64
import io
import json
import sys
import time
from pathlib import Path

import requests
from PIL import Image, ImageDraw, ImageFilter

REPO_ROOT = Path(__file__).resolve().parents[2]
SEED_FILE = REPO_ROOT / "cache-proxy" / "data" / "ghost-personas-seed.json"

FORGE = "http://localhost:7860"
CHECKPOINT = "shared/DreamShaperXL_Turbo_v2_1.safetensors [4496b36d48]"

# Per-frame prompts. Slug must match the seed JSON `frames[*].slug`.
FRAME_PROMPTS: dict[str, str] = {
    "wrought_iron_raven":
        "ornate decorative gothic wrought-iron picture frame border, "
        "twisted black metal ornamental edges, "
        "single raven perched at top center of the frame, "
        "Halloween, Tim Burton aesthetic",
    "braided_rope_anchor":
        "ornate decorative thick braided maritime rope picture frame border, "
        "weathered tan rope ornamental edges, "
        "single small ship anchor at top center of the frame, "
        "nautical, Salem harbor",
    "autumn_vines_pumpkin":
        "ornate decorative autumn vines and oak leaves picture frame border, "
        "russet and orange leafy ornamental edges, "
        "single small orange pumpkin at top center of the frame, "
        "fall harvest, Halloween",
    "gothic_wood_skull":
        "ornate decorative carved dark gothic wood picture frame border, "
        "intricately carved black walnut ornamental edges, "
        "single small ivory skull at top center of the frame, "
        "Halloween, gothic",
    "brass_rivets_compass":
        "ornate decorative tarnished brass picture frame border with rivets, "
        "antique brass ornamental edges with rivets, "
        "single small compass rose at top center of the frame, "
        "vintage nautical, Salem",
    "leather_witchhat":
        "ornate decorative stitched colonial brown leather picture frame border, "
        "hand-stitched aged brown leather ornamental edges, "
        "single small black pointed witch hat at top center of the frame, "
        "colonial Salem, Halloween",
    "bone_moon":
        "ornate decorative ivory bone-carved picture frame border, "
        "carved aged bone ornamental edges, "
        "single small silver crescent moon at top center of the frame, "
        "witchy, occult, PG-13",
    "gravestone_jackolantern":
        "ornate decorative weathered carved stone gravestone picture frame border, "
        "moss-flecked aged grey stone ornamental edges, "
        "single small glowing orange jack-o'-lantern at top center of the frame, "
        "Halloween, Salem cemetery",
}

COMMON_TAIL = (
    ", single ornamental frame object on a bright pure chroma green background, "
    "centered composition, square aspect ratio, "
    "flat empty bright green interior visible through the frame opening, "
    "no portrait, no person, no face, no scenery, no landscape, "
    "PG-13, vector-clean shape, high contrast edges"
)

# Standalone emblem prompts — generated separately and composited at top-center of each
# frame because SD won't reliably place named small objects at a specific frame position.
EMBLEM_PROMPTS: dict[str, str] = {
    "wrought_iron_raven":
        "single black raven bird, side profile silhouette, simple iconic shape, "
        "bright pure chroma green background, no other objects, no scenery, "
        "single subject, centered, PG-13 cartoon icon",
    "braided_rope_anchor":
        "single ship anchor, dark iron metal, simple iconic shape, "
        "bright pure chroma green background, no other objects, no scenery, "
        "single subject, centered, nautical icon",
    "autumn_vines_pumpkin":
        "single orange Halloween pumpkin, simple iconic shape, "
        "bright pure chroma green background, no other objects, no scenery, "
        "single subject, centered, cartoon icon",
    "gothic_wood_skull":
        "single ivory human skull, simple iconic frontal view, friendly cartoon style, "
        "bright pure chroma green background, no other objects, no scenery, "
        "single subject, centered, PG-13 cartoon icon",
    "brass_rivets_compass":
        "single antique brass compass rose, simple iconic shape, "
        "bright pure chroma green background, no other objects, no scenery, "
        "single subject, centered, nautical vintage icon",
    "leather_witchhat":
        "single black pointed witch hat, simple iconic side profile, "
        "bright pure chroma green background, no other objects, no scenery, "
        "single subject, centered, Halloween cartoon icon",
    "bone_moon":
        "single silver crescent moon, simple iconic shape, "
        "bright pure chroma green background, no other objects, no scenery, "
        "single subject, centered, witchy icon",
    "gravestone_jackolantern":
        "single glowing orange jack-o'-lantern with carved face, simple iconic frontal view, "
        "bright pure chroma green background, no other objects, no scenery, "
        "single subject, centered, Halloween cartoon icon",
}

NEG = (
    "person, face, portrait, character, human, ghost, animal head, "
    "scenery, landscape, environment, multiple objects, photo, photograph, "
    "text, watermark, signature, logo, low quality, blurry, "
    "photorealistic, cluttered, gore, scary, "
    "interior scene, background scene, painting inside"
)

# Chroma-key target: pure SD green tends to be around (40-180, 180-255, 40-180).
CHROMA_R_MAX = 180
CHROMA_G_MIN = 130
CHROMA_B_MAX = 180
CHROMA_GREEN_DOMINANCE = 30   # G must exceed both R and B by at least this much


def wait_for_forge(timeout_s: int = 60) -> None:
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        try:
            r = requests.get(f"{FORGE}/sdapi/v1/sd-models", timeout=3)
            if r.status_code == 200:
                return
        except Exception:
            pass
        time.sleep(2)
    sys.exit("Forge did not come up within timeout.")


def select_checkpoint() -> None:
    requests.post(
        f"{FORGE}/sdapi/v1/options",
        json={"sd_model_checkpoint": CHECKPOINT},
        timeout=600,
    ).raise_for_status()


def generate(prompt: str, seed_val: int, out_path: Path, *, size: int) -> float:
    payload = {
        "prompt": prompt,
        "negative_prompt": NEG,
        "steps": 8,
        "cfg_scale": 2.5,
        "sampler_name": "DPM++ SDE",
        "scheduler": "Karras",
        "width": size,
        "height": size,
        "seed": seed_val,
    }
    t0 = time.time()
    r = requests.post(f"{FORGE}/sdapi/v1/txt2img", json=payload, timeout=600)
    r.raise_for_status()
    img_b64 = r.json()["images"][0]
    out_path.write_bytes(base64.b64decode(img_b64))
    return time.time() - t0


def chroma_key_only(src: Path, dst: Path, *, dominance: int = 15) -> None:
    """Convert SD-on-green output to RGBA with green pixels alpha=0. Looser threshold
    than chroma_key_to_alpha — catches anti-aliased halo pixels on emblem cut-outs."""
    img = Image.open(src).convert("RGBA")
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, _ = px[x, y]
            if (
                g >= 100
                and r <= 200
                and b <= 200
                and g - r >= dominance
                and g - b >= dominance
            ):
                px[x, y] = (0, 0, 0, 0)
    img.save(dst, format="PNG")


def composite_emblem(frame_path: Path, emblem_path: Path, out_path: Path,
                      *, emblem_target_px: int = 150, top_inset_px: int = 24) -> None:
    """Composite emblem (RGBA, transparent bg) at top-center of frame (RGBA)."""
    frame = Image.open(frame_path).convert("RGBA")
    emblem = Image.open(emblem_path).convert("RGBA")
    # Trim transparent border on the emblem so size-target maps to the visible subject.
    bbox = emblem.getbbox()
    if bbox:
        emblem = emblem.crop(bbox)
    # Resize emblem to a uniform visual weight across frames.
    ew, eh = emblem.size
    scale = emblem_target_px / max(ew, eh)
    emblem = emblem.resize((int(ew * scale), int(eh * scale)), Image.LANCZOS)
    # Position: horizontally centered, top edge of emblem at top_inset_px from frame top.
    fw, fh = frame.size
    ew2, eh2 = emblem.size
    pos = ((fw - ew2) // 2, top_inset_px)
    frame.alpha_composite(emblem, dest=pos)
    frame.save(out_path, format="PNG")


def chroma_key_to_alpha(src: Path, dst: Path, *, force_interior_cutout: bool = True) -> None:
    """Convert SD output -> RGBA. Green pixels become transparent; optionally also force
    a centered rectangle cutout so the portrait below shows through."""
    img = Image.open(src).convert("RGBA")
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, _ = px[x, y]
            if (
                g >= CHROMA_G_MIN
                and r <= CHROMA_R_MAX
                and b <= CHROMA_B_MAX
                and g - r >= CHROMA_GREEN_DOMINANCE
                and g - b >= CHROMA_GREEN_DOMINANCE
            ):
                px[x, y] = (0, 0, 0, 0)

    if force_interior_cutout:
        # Hard-guarantee a centered rectangular cutout so the portrait shows through,
        # even if SD ignored the "transparent interior" hint and drew an opaque inside.
        # Inner rect = 70% of each dimension, centered. Soft edges via mask blur.
        cutout = Image.new("L", (w, h), 0)
        d = ImageDraw.Draw(cutout)
        pad_x = int(w * 0.15)
        pad_y = int(h * 0.18)   # slightly more vertical pad so the top-center emblem survives
        d.rectangle((pad_x, pad_y, w - pad_x, h - pad_y), fill=255)
        cutout = cutout.filter(ImageFilter.GaussianBlur(radius=w * 0.02))
        # Multiply existing alpha by inverted cutout (cutout=255 -> alpha=0)
        a = img.split()[3]
        a_arr = list(a.getdata())
        c_arr = list(cutout.getdata())
        new_a = [int(av * (1 - cv / 255)) for av, cv in zip(a_arr, c_arr)]
        a_new = Image.new("L", (w, h))
        a_new.putdata(new_a)
        img.putalpha(a_new)

    img.save(dst, format="PNG")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "--outdir",
        type=Path,
        default=Path.home() / "AI-Studio" / "ghost-frames-v1",
    )
    ap.add_argument("--size", type=int, default=768)
    ap.add_argument("--master-seed", type=int, default=1692, help="lock the SD seeds for reproducibility")
    ap.add_argument(
        "--skip-interior-cutout",
        action="store_true",
        help="don't force the centered alpha rectangle (trust SD to do it)",
    )
    ap.add_argument(
        "--emblems-only",
        action="store_true",
        help="skip frame generation, only generate the 8 emblems + composite onto existing frames",
    )
    ap.add_argument(
        "--frames-only",
        type=str,
        default=None,
        help="comma-separated slug list to regen ONLY those frames (and re-composite their emblems)",
    )
    args = ap.parse_args()

    args.outdir.mkdir(parents=True, exist_ok=True)
    seed = json.loads(SEED_FILE.read_text())
    frame_slugs = [f["slug"] for f in seed["frames"]]

    missing = [s for s in frame_slugs if s not in FRAME_PROMPTS]
    if missing:
        sys.exit(f"FRAME_PROMPTS missing slugs: {missing}")

    print(f"[forge] connecting to {FORGE} ...", flush=True)
    wait_for_forge()
    print(f"[forge] selecting checkpoint: {CHECKPOINT}", flush=True)
    select_checkpoint()

    raw_dir = args.outdir / "raw_green"
    raw_dir.mkdir(exist_ok=True)
    emblem_dir = args.outdir / "emblems"
    emblem_dir.mkdir(exist_ok=True)
    emblem_raw_dir = emblem_dir / "raw_green"
    emblem_raw_dir.mkdir(exist_ok=True)
    frames_noemblem_dir = args.outdir / "no_emblem"
    frames_noemblem_dir.mkdir(exist_ok=True)

    frames_to_regen = (
        [s.strip() for s in args.frames_only.split(",")]
        if args.frames_only else
        ([] if args.emblems_only else frame_slugs)
    )

    # PHASE 1: regenerate frame borders (if any)
    for i, slug in enumerate(frames_to_regen, start=1):
        prompt = FRAME_PROMPTS[slug] + COMMON_TAIL
        sd_seed = args.master_seed + 100 + frame_slugs.index(slug)   # +100 to avoid emblem-seed collision
        raw_path = raw_dir / f"frame_{slug}_raw.png"
        noemblem_path = frames_noemblem_dir / f"frame_{slug}.png"
        print(f"\n[frame {i}/{len(frames_to_regen)}] {slug} seed={sd_seed}", flush=True)
        elapsed = generate(prompt, sd_seed, raw_path, size=args.size)
        print(f"  generated ({elapsed:.1f}s) -> {raw_path.name}", flush=True)
        chroma_key_to_alpha(raw_path, noemblem_path, force_interior_cutout=not args.skip_interior_cutout)
        print(f"  alpha + cutout -> no_emblem/{noemblem_path.name}", flush=True)

    # PHASE 2: generate emblems for every slug we'll composite (default: all 8)
    emblem_slugs = frames_to_regen if (frames_to_regen and not args.emblems_only) else frame_slugs
    for i, slug in enumerate(emblem_slugs, start=1):
        prompt = EMBLEM_PROMPTS[slug]
        sd_seed = args.master_seed + 200 + frame_slugs.index(slug)
        raw_path = emblem_raw_dir / f"emblem_{slug}_raw.png"
        emblem_path = emblem_dir / f"emblem_{slug}.png"
        print(f"\n[emblem {i}/{len(emblem_slugs)}] {slug} seed={sd_seed}", flush=True)
        # Emblems can be smaller — 384 keeps detail crisp, faster than 768.
        elapsed = generate(prompt, sd_seed, raw_path, size=384)
        print(f"  generated ({elapsed:.1f}s) -> emblems/raw_green/{raw_path.name}", flush=True)
        chroma_key_only(raw_path, emblem_path)
        print(f"  alpha -> emblems/{emblem_path.name}", flush=True)

    # PHASE 3: composite each emblem onto its frame
    for slug in emblem_slugs:
        noemblem_path = frames_noemblem_dir / f"frame_{slug}.png"
        emblem_path = emblem_dir / f"emblem_{slug}.png"
        final_path = args.outdir / f"frame_{slug}.png"
        if not noemblem_path.exists():
            # Fall back to existing frame_<slug>.png from a prior run if no_emblem version not produced this run
            noemblem_path = args.outdir / f"frame_{slug}.png"
        composite_emblem(noemblem_path, emblem_path, final_path)
        print(f"  composite -> {final_path.name}", flush=True)

    print(f"\nDone. Final frames in {args.outdir}/frame_*.png")


if __name__ == "__main__":
    main()
