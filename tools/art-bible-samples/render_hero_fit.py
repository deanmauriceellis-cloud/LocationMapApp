#!/usr/bin/env python3
"""
S298 hero-fit validation. Operator spec: hero = ~20% of top screen as a banner,
must fit BOTH portrait and landscape. Full-screen dialog => hero is full
screen-width x 0.20*screen-height. Device boxes:
  Pixel8  portrait 1080x480 (2.25:1) | landscape 2400x216 (11:1)
  Lenovo  portrait  800x268 (3:1)    | landscape 1340x160 (8.4:1)
Single image can't FILL both without crop -> use centerCrop + safe-band master.
Render woodcut masters (teal dialed back) composed subject-centered, then
simulate the real centerCrop for each device/orientation so operator sees fit.
"""
import base64, io, time
from pathlib import Path
import requests
from PIL import Image

FORGE = "http://127.0.0.1:7860"
CHECKPOINT = "shared/DreamShaperXL_Turbo_v2_1.safetensors [4496b36d48]"
OUT = Path(__file__).parent / "out_fit"
OUT.mkdir(parents=True, exist_ok=True)

STYLE = ("ink-comic woodcut illustration, heavy black ink outlines, flat cel shading, "
         "1692 Salem broadsheet woodcut style, muted period palette parchment cream "
         "weathered grey earthy brown charcoal, subtle ghost-teal cyan accent only in small "
         "highlights, mostly muted, 2D illustration, hand-printed broadsheet texture, "
         "centered symmetrical composition, subject centered, clear central band")
NEG = ("photorealistic, photograph, 3d render, soft airbrush, gradient, painterly, text, "
       "typography, letters, watermark, modern objects, cars, bright saturated colors, "
       "teal dominant, neon, purple, gore, blood, nude, deformed, low quality, off-center")

# master aspect = 2.25:1 (matches Pixel portrait box exactly; widest-used orientation)
MW, MH = 1152, 512

def gen(prompt, w, h, steps=8, cfg=2.5, seed=88):
    payload = {"prompt": f"{prompt}, {STYLE}", "negative_prompt": NEG, "steps": steps,
               "cfg_scale": cfg, "sampler_name": "DPM++ SDE", "seed": seed, "width": w,
               "height": h, "override_settings": {"sd_model_checkpoint": CHECKPOINT}}
    r = requests.post(f"{FORGE}/sdapi/v1/txt2img", json=payload, timeout=600)
    r.raise_for_status()
    b64 = r.json()["images"][0]
    return Image.open(io.BytesIO(base64.b64decode(b64.split(",", 1)[-1]))).convert("RGB")

def center_crop_cover(img, cw, ch):
    """Replicates Android ImageView.ScaleType.CENTER_CROP."""
    scale = max(cw / img.width, ch / img.height)
    nw, nh = round(img.width * scale), round(img.height * scale)
    r = img.resize((nw, nh), Image.LANCZOS)
    left, top = (nw - cw) // 2, (nh - ch) // 2
    return r.crop((left, top, left + cw, top + ch))

BOXES = {  # device_orientation -> (w,h) of the 20%-height hero container
    "pixel_portrait":  (1080, 480),
    "pixel_landscape": (2400, 216),
    "lenovo_portrait": (800, 268),
    "lenovo_landscape": (1340, 160),
}

SUBJECTS = {
    "witch_house": "the Salem Witch House, dark gabled colonial 1670s timber house, diamond-pane windows, bare autumn trees, centered",
    "burying_point": "the Old Burying Point cemetery in Salem, leaning colonial slate gravestones, bare trees, low fog, centered wide view",
}

def main():
    t0 = time.time()
    for name, prompt in SUBJECTS.items():
        print(f"master: {name} {MW}x{MH}", flush=True)
        m = gen(prompt, MW, MH)
        m.save(OUT / f"{name}_master_{MW}x{MH}.webp", quality=92)
        for box, (cw, ch) in BOXES.items():
            crop = center_crop_cover(m, cw, ch)
            crop.save(OUT / f"{name}_{box}_{cw}x{ch}.webp", quality=92)
    print(f"DONE {time.time()-t0:.0f}s -> {OUT}", flush=True)

if __name__ == "__main__":
    main()
