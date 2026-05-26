#!/usr/bin/env python3
"""
S301 — woodcut church-glyph circle marker for the 17 WORSHIP POIs (art-bible
§4.1 / §4 "POI circle marker"). WORSHIP POIs carry no Katrina ghost badge, so on
the map they fall through to the category circle-icon. The old asset is the
pre-redo witch-style sigil; this replaces it with a bold woodcut church glyph
that reads at ~48dp.

Marker rule (art-bible §5): restrained BASE style (NOT the H3 haunted hero mood),
single centered emblem, simple/bold, parchment ground, no broadsheet-texture cue
(induces gibberish). OCR text-reject. Square output (loadCircleIcon scales the
asset to a square px box, then MarkerIconHelper draws it).

Generates N candidates to out_worship/; pick one and copy to:
  app-salem/src/main/assets/poi-circle-icons/worship/place_of_worship.webp
"""
import base64, io, sys, time
from pathlib import Path
import requests
from PIL import Image
try:
    import render_hero_full as F   # reuse OCR has_text()
    _HAS_OCR = True
except Exception:
    _HAS_OCR = False

def _has_text(img):
    if not _HAS_OCR:
        return False, None
    try:
        return F.has_text(img)
    except Exception:
        return False, None

FORGE = "http://127.0.0.1:7860"
CHECKPOINT = "shared/DreamShaperXL_Turbo_v2_1.safetensors [4496b36d48]"
OUT = Path(__file__).parent / "out_worship"
SIZE = 1024          # generate square; saved asset downscaled to 512
SAVE_PX = 512

# Restrained base woodcut style (art-bible §5 shared positive base + icon rules).
STYLE = ("ink-comic woodcut illustration, heavy black ink outlines, flat cel shading, "
         "1692 Salem woodcut style, muted period palette parchment cream grey earthy brown charcoal, "
         "2D illustration, single centered, one emblem, simple bold, parchment ground")
NEG = ("photorealistic, photograph, 3d render, soft airbrush, gradient, painterly, "
       "text, typography, letters, words, caption, watermark, signature, modern objects, "
       "bright saturated colors, neon, purple dominant, gore, nude, deformed, low quality, "
       "cluttered, busy background, multiple buildings, scene, landscape")
SUBJECT = ("a single church building seen head-on, tall steeple with a small cross on top, "
           "centered iconic emblem, plain background")

def gen(seed):
    payload = {"prompt": f"{SUBJECT}, {STYLE}", "negative_prompt": NEG, "steps": 9,
               "cfg_scale": 4.5, "sampler_name": "DPM++ SDE", "seed": seed,
               "width": SIZE, "height": SIZE,
               "override_settings": {"sd_model_checkpoint": CHECKPOINT}}
    r = requests.post(f"{FORGE}/sdapi/v1/txt2img", json=payload, timeout=600)
    r.raise_for_status()
    return Image.open(io.BytesIO(base64.b64decode(r.json()["images"][0].split(",", 1)[-1]))).convert("RGB")

def main():
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 4
    OUT.mkdir(parents=True, exist_ok=True)
    t0 = time.time()
    for i in range(n):
        seed = 730000 + i * 4153
        for attempt in range(6):  # OCR reject
            img = gen(seed + attempt * 97)
            txt, _ = _has_text(img)
            if not txt:
                break
        small = img.resize((SAVE_PX, SAVE_PX), Image.LANCZOS)
        small.save(OUT / f"church_{i+1}.webp", quality=90)
        print(f"  church_{i+1} seed={seed} ({time.time()-t0:.0f}s){' [ocr-rerolled]' if txt else ''}", flush=True)
    print(f"DONE {n} candidates in {time.time()-t0:.0f}s -> {OUT}", flush=True)

if __name__ == "__main__":
    main()
