#!/usr/bin/env python3
"""
S299 — woodcut category 'emblem' scenes (PoiHeroResolver Tier-2 fallback).

Non-bespoke POIs (CIVIC / commercial / landmarks, ~1,900) resolve their hero +
56dp icon from poi-icons/<folder>/ (hash-picked). Today those are off-style
photoreal crystals (or missing → red). This regenerates each mapped folder with
2 generic h3-mood woodcut Salem scenes so every POI on the map reads on-style.

Format: 2.25:1 (1152x512), same as heroes — centerCrops cleanly into both the
20% hero banner and the 56dp overview icon. Replaces the folder's old webps.
Writes to app-salem/src/main/assets/poi-icons/<folder>/<folder>_{1,2}.webp.

Adds NEW folders: services, apartment_building, landmarks (+ parking/fuel refresh)
— resolver mapping updated separately in PoiHeroResolver.kt.
"""
import base64, io, sys, time
from pathlib import Path
import numpy as np, requests
from PIL import Image
import render_hero_haunted as H
import render_hero_full as F   # reuse OCR + gen plumbing patterns

FORGE = "http://127.0.0.1:7860"
CHECKPOINT = "shared/DreamShaperXL_Turbo_v2_1.safetensors [4496b36d48]"
ICONS = Path("/home/witchdoctor/Development/LocationMapApp_v1.5/app-salem/src/main/assets/poi-icons")
MW, MH = 1152, 512

# folder -> generic establishing scene (period Salem, no modern anachronisms)
SCENES = {
    "shopping":      "a row of small colonial Salem shopfronts with hanging trade signs, cobblestone street",
    "offices":       "a stately brick Federal office building with rows of windows on a Salem street",
    "food_drink":    "a colonial Salem tavern with a hanging ale sign and warm glowing windows",
    "healthcare":    "an old Salem apothecary shop, mortar-and-pestle sign, bottles in the window",
    "entertainment": "an old Salem theater playhouse facade with a marquee at night",
    "civic":         "Salem town hall, stately Federal civic building with a white cupola",
    "parks_rec":     "a moonlit Salem park green with gnarled bare trees, a path and an iron fence",
    "services":      "a colonial Salem tradesman workshop storefront with a tools sign",
    "auto_services": "an old Salem carriage house with a wagon wheel and a hanging lantern",
    "education":     "an old Salem brick schoolhouse with a bell cupola",
    "ghost_tour":    "a hooded lantern-bearing guide leading a ghost tour down a foggy Salem lane",
    "witch_shop":    "a witchcraft shop storefront, witch-hat sign, candles and crystals in the window",
    "psychic":       "a fortune-teller parlor storefront with a crystal ball and an all-seeing-eye sign",
    "worship":       "a white New England clapboard church with a tall steeple",
    "lodging":       "a colonial Salem inn with a hanging sign, a lantern and warm windows",
    "finance":       "an old Salem bank with a columned granite facade",
    "parking":       "a Salem livery carriage yard with a signpost and a stone wall",
    "fuel_charging": "a lantern-lit Salem waystation post by the roadside",
    "apartment_building": "a tall Salem brick row tenement building with many windows",
    "landmarks":     "a carved stone monument and engraved granite marker on a moonlit Salem green",
}

def gen(prompt, seed):
    payload = {"prompt": f"{prompt}, {H.STYLE_H3}", "negative_prompt": H.NEG_H3, "steps": 9,
               "cfg_scale": 4.0, "sampler_name": "DPM++ SDE", "seed": seed,
               "width": MW, "height": MH,
               "override_settings": {"sd_model_checkpoint": CHECKPOINT}}
    r = requests.post(f"{FORGE}/sdapi/v1/txt2img", json=payload, timeout=600)
    r.raise_for_status()
    return Image.open(io.BytesIO(base64.b64decode(r.json()["images"][0].split(",", 1)[-1]))).convert("RGB")

def main():
    only = set(sys.argv[1:])
    t0 = time.time()
    for folder, scene in SCENES.items():
        if only and folder not in only:
            continue
        d = ICONS / folder
        d.mkdir(parents=True, exist_ok=True)
        # clear old (photoreal) webps in this folder
        for old in d.glob("*.webp"):
            old.unlink()
        for n in range(1, 3):  # 2 variants
            seed = (abs(hash(folder)) % 900000) + n * 9173
            for attempt in range(6):  # OCR reject
                img = gen(scene, seed + attempt * 97)
                txt, _ = F.has_text(img)
                if not txt:
                    break
            img.save(d / f"{folder}_{n}.webp", quality=90)
        print(f"  {folder}: 2 scenes ({time.time()-t0:.0f}s)", flush=True)
    print(f"DONE {len(SCENES if not only else only)} folders in {time.time()-t0:.0f}s", flush=True)

if __name__ == "__main__":
    main()
