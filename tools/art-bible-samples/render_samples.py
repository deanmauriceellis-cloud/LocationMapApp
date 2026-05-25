#!/usr/bin/env python3
"""
S298 art-bible validation renders.
House style: ink-comic woodcut. Palette: muted period + ghost-teal accent.
Renders the SAME subject (Salem Witch House) in three hero aspects, plus a POI
icon and a re-toned ghost portrait, so the operator can judge the locked
direction (Decisions 1-3) and the open hero-format question.
"""
import base64, io, time
from pathlib import Path
import requests
from PIL import Image

FORGE = "http://127.0.0.1:7860"
CHECKPOINT = "shared/DreamShaperXL_Turbo_v2_1.safetensors [4496b36d48]"
OUT = Path(__file__).parent / "out"
OUT.mkdir(parents=True, exist_ok=True)

# Art-bible scaffold (docs/plans/graphics-art-bible.md §5)
STYLE = ("ink-comic woodcut illustration, heavy black ink outlines, flat cel shading, "
         "1692 Salem broadsheet woodcut style, muted period palette of parchment cream "
         "weathered grey earthy brown and charcoal, single spectral ghost-teal cyan accent glow, "
         "2D illustration, hand-printed broadsheet texture")
NEG = ("photorealistic, photograph, 3d render, soft airbrush, gradient shading, painterly, "
       "text, typography, letters, signature, watermark, modern objects, cars, "
       "bright saturated colors, purple dominant, neon, gore, blood, nude, deformed, low quality")

SEED = 77

def gen(prompt, w, h, steps=8, cfg=2.5):
    payload = {
        "prompt": f"{prompt}, {STYLE}",
        "negative_prompt": NEG,
        "steps": steps, "cfg_scale": cfg, "sampler_name": "DPM++ SDE",
        "seed": SEED, "width": w, "height": h,
        "override_settings": {"sd_model_checkpoint": CHECKPOINT},
        "override_settings_restore_afterwards": False,
    }
    r = requests.post(f"{FORGE}/sdapi/v1/txt2img", json=payload, timeout=600)
    r.raise_for_status()
    b64 = r.json()["images"][0]
    return Image.open(io.BytesIO(base64.b64decode(b64.split(",", 1)[-1]))).convert("RGB")

WITCH_HOUSE = "the Salem Witch House, a dark gabled colonial 1670s timber house with diamond-pane windows, bare autumn trees, dusk"

def main():
    t0 = time.time()

    # --- HERO ASPECT A: square 1:1 (768x768) ---
    print("A: square hero 768x768 ...", flush=True)
    a = gen(f"establishing scene of {WITCH_HOUSE}, faint ghost-teal glow in the windows", 768, 768)
    a.save(OUT / "hero_A_square_768.webp", quality=92)

    # --- HERO ASPECT B: single wide banner ~4:1 (832x216 -> resize 800x200) ---
    print("B: wide banner hero 832x216 ...", flush=True)
    b = gen(f"wide panoramic establishing scene of {WITCH_HOUSE}, faint ghost-teal glow", 832, 216)
    b.resize((800, 200), Image.LANCZOS).save(OUT / "hero_B_banner_800x200.webp", quality=92)

    # --- HERO ASPECT C: triptych 800x200 (3 panels stitched) ---
    print("C: triptych hero (3 panels) ...", flush=True)
    panels = [
        gen(f"exterior of {WITCH_HOUSE}", 384, 384),
        gen("interior of a 1690s colonial room, hearth, hanging herbs, pewter, candlelight, ghost-teal glow", 384, 384),
        gen("a gaunt 1692 Salem witch-trial magistrate in a black coat and tall hat, ghost-teal eyes", 384, 384),
    ]
    panel_w = [160, 320, 320]  # canonical triptych split
    canvas = Image.new("RGB", (800, 200))
    x = 0
    for p, pw in zip(panels, panel_w):
        # center-crop each square panel to the target panel aspect, then resize
        ph = 200
        target_ar = pw / ph
        src_ar = p.width / p.height
        if src_ar > target_ar:
            nw = int(p.height * target_ar); left = (p.width - nw) // 2
            p = p.crop((left, 0, left + nw, p.height))
        else:
            nh = int(p.width / target_ar); top = (p.height - nh) // 2
            p = p.crop((0, top, p.width, top + nh))
        canvas.paste(p.resize((pw, ph), Image.LANCZOS), (x, 0)); x += pw
    canvas.save(OUT / "hero_C_triptych_800x200.webp", quality=92)

    # --- POI ICON: woodcut emblem (crystal shop) at icon scale ---
    print("D: POI icon (crystal shop) 512 ...", flush=True)
    d = gen("a single centered cluster of magic crystals, app icon emblem, circular composition, "
            "parchment ground, ghost-teal glow", 512, 512)
    d.save(OUT / "icon_D_crystal_512.webp", quality=92)

    # --- GHOST PORTRAIT: re-toned to teal accent ---
    print("E: ghost portrait (teal-toned) 512 ...", flush=True)
    e = gen("a translucent ghostly 1692 Salem witch-trial magistrate, tall black hat, gaunt face, "
            "spectral ghost-teal glow, in front of a colonial meeting house, autumn", 512, 512)
    e.save(OUT / "ghost_E_magistrate_512.webp", quality=92)

    print(f"DONE in {time.time()-t0:.0f}s -> {OUT}", flush=True)

if __name__ == "__main__":
    main()
