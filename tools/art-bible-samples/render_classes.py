#!/usr/bin/env python3
"""S298 art-bible per-class validation. Woodcut + muted-period + ghost-teal.
One sample per remaining asset class so the whole direction is visually proven."""
import base64, io, time
from pathlib import Path
import requests
from PIL import Image

FORGE = "http://127.0.0.1:7860"
CHECKPOINT = "shared/DreamShaperXL_Turbo_v2_1.safetensors [4496b36d48]"
OUT = Path(__file__).parent / "out_classes"
OUT.mkdir(parents=True, exist_ok=True)

STYLE = ("ink-comic woodcut illustration, heavy black ink outlines, flat cel shading, "
         "1692 Salem broadsheet woodcut style, muted period palette parchment cream grey "
         "earthy brown charcoal, 2D illustration, hand-printed broadsheet texture")
NEG = ("photorealistic, photograph, 3d render, soft airbrush, gradient, painterly, text, "
       "typography, letters, watermark, modern, bright saturated, neon, gore, nude, deformed, low quality")

def gen(prompt, w, h, seed, steps=8, cfg=2.5):
    payload = {"prompt": f"{prompt}, {STYLE}", "negative_prompt": NEG, "steps": steps,
               "cfg_scale": cfg, "sampler_name": "DPM++ SDE", "seed": seed, "width": w,
               "height": h, "override_settings": {"sd_model_checkpoint": CHECKPOINT}}
    r = requests.post(f"{FORGE}/sdapi/v1/txt2img", json=payload, timeout=600)
    r.raise_for_status()
    return Image.open(io.BytesIO(base64.b64decode(r.json()["images"][0].split(",",1)[-1]))).convert("RGB")

JOBS = [
    # (filename, prompt, w, h, seed)
    ("icon_food_drink_512", "a single centered steaming tavern tankard and a plate of food, simple bold emblem, "
        "parchment background, small ghost-teal accent glow", 512, 512, 101),
    ("icon_parks_rec_512", "a single centered grand oak tree, simple bold emblem, parchment background, "
        "small ghost-teal accent", 512, 512, 102),
    ("marker_witch_shop_512", "a single bold witch hat and potion bottle, very simple high-contrast glyph, "
        "centered in a circular medallion, minimal detail for tiny icon, ghost-teal glow", 512, 512, 103),
    ("splash_katrina_768", "a grey and white longhair tuxedo cat mascot with luminous ghost-teal eyes, sitting "
        "proud, a tiny witch hat, spectral ghost-teal magic wisps, Salem night, charming friendly mascot", 768, 768, 104),
    ("portrait_accused_640", "bust portrait of a 1690s Salem Puritan woman, plain coif and collar, solemn dignified "
        "expression, period accurate, neutral parchment background, woodcut portrait", 512, 640, 105),
    ("sprite_blackcat_512", "a small black cat in profile side view, simple bold silhouette, single character on a "
        "plain flat background, full body, ghost-teal eye, game sprite", 512, 512, 106),
    ("frame_gothic_640", "an ornate empty rectangular gothic woodcut border frame, wrought iron and raven motifs, "
        "hollow empty center, decorative corners, ghost-teal filigree accents, on flat grey background", 512, 640, 107),
]

def main():
    t0 = time.time()
    marker_full = None
    for name, prompt, w, h, seed in JOBS:
        print(f"... {name}", flush=True)
        img = gen(prompt, w, h, seed)
        img.save(OUT / f"{name}.webp", quality=92)
        if name.startswith("marker_"):
            marker_full = img
    # legibility test: downscale marker to real dock sizes
    if marker_full is not None:
        for px in (96, 48):
            marker_full.resize((px, px), Image.LANCZOS).save(OUT / f"marker_witch_shop_{px}.webp", quality=92)
    print(f"DONE {time.time()-t0:.0f}s -> {OUT}", flush=True)

if __name__ == "__main__":
    main()
