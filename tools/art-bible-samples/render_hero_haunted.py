#!/usr/bin/env python3
"""
S299 HAUNTED variant pass. Operator feedback on the first pilot: too lifelike /
period-illustration; missing the Salem Witch-City haunted-Halloween flavor.

This reuses the narration clue-miner (subject fidelity stays) but swaps the
daytime/overcast mood for a spooky, stylized, moonlit-Halloween treatment and
pushes the woodcut to be bolder/more graphic (less photoreal). Renders the SAME
POIs at the SAME seeds as render_hero_pilot.py so the two sets compare directly.

Two intensities per POI so the operator can pick how far to go:
  _h1 = haunted (moonlit dusk, fog, crows, teal moonglow, bolder ink)
  _h2 = max spooky (night, heavy stylization, stronger spectral teal, exaggerated)
"""
import base64, io, json, time
from pathlib import Path
import requests
from PIL import Image
import render_hero_pilot as P  # reuse clue miner + subject_clause

FORGE = "http://127.0.0.1:7860"
CHECKPOINT = "shared/DreamShaperXL_Turbo_v2_1.safetensors [4496b36d48]"
HERE = Path(__file__).parent
OUT = HERE / "out_haunted"
OUT.mkdir(parents=True, exist_ok=True)
MW, MH = 1152, 512

# Bolder, more graphic woodcut + spooky atmosphere. Less rendering, more ink.
STYLE_H1 = ("bold stylized ink-comic woodcut, thick heavy black ink outlines, flat graphic "
            "cel shading, high contrast, 1692 Salem witch-trial broadsheet woodcut, spooky "
            "storybook illustration, eerie haunted Halloween atmosphere, moonlit dusk, low "
            "ground fog and mist, gnarled twisted bare trees, circling crows and ravens, "
            "long dramatic shadows, muted period palette parchment grey charcoal with "
            "ghost-teal cyan moonlight glow, 2D illustration, subject centered in clear "
            "central band, establishing exterior view")
STYLE_H2 = ("bold exaggerated ink-comic woodcut, very thick black ink linework, flat graphic "
            "cel shading, stark high contrast, 1692 Salem witch-city broadsheet woodcut, "
            "creepy spooky Halloween storybook illustration, ominous haunted mood, dark "
            "moonlit night, full moon, swirling fog and mist, twisted clawing bare branches, "
            "flock of black crows, jack-o-lantern warm glow accents, eerie ghost-teal cyan "
            "spectral light, dramatic silhouettes, stylized not realistic, 2D illustration, "
            "subject centered in clear central band")
NEG = ("photorealistic, photograph, realistic, lifelike, 3d render, soft airbrush, gradient, "
       "painterly, daytime, bright sunny, cheerful, mundane, calm, text, typography, letters, "
       "words, caption, label, signature, watermark, frame, border, modern objects, cars, "
       "people, crowd, bright saturated colors, neon, purple dominant, gore, blood, nude, "
       "deformed, low quality, off-center, cropped subject")

# h3 = tuned middle (operator pick S299): h1's legible LIT building + more of h2's drama/teal.
# Building stays bright cream against a dark spectral-teal moonlit sky; teal is accent, not wash.
STYLE_H3 = ("bold stylized ink-comic woodcut, thick black ink outlines, flat graphic cel "
            "shading, high contrast, 1692 Salem witch-city broadsheet woodcut, spooky "
            "Halloween storybook illustration, haunted ominous mood, moonlit night, full "
            "moon, drifting fog and mist, gnarled twisted bare trees in dark silhouette, "
            "circling crows and bats, long dramatic shadows, the building brightly moonlit "
            "with light cream walls and warm glowing candlelit windows standing out against "
            "the dark sky, muted period palette parchment cream charcoal black ink with "
            "ghost-teal cyan moonlight accents in the sky, mostly muted, stylized not "
            "realistic, 2D illustration, subject centered in clear central band")
NEG_H3 = NEG + ", dark building, black silhouette building, teal building, building in shadow"

# (tag, style, cfg, negative)
VARIANTS = [("h1", STYLE_H1, 3.5, NEG), ("h2", STYLE_H2, 4.5, NEG), ("h3", STYLE_H3, 4.0, NEG_H3)]
PILOT_IDS = {"the_witch_house_at_salem", "charter_street_cemetery",
             "old_town_hall", "john_ward_house"}

def gen(prompt, style, cfg, neg, seed):
    payload = {"prompt": f"{prompt}, {style}", "negative_prompt": neg, "steps": 9,
               "cfg_scale": cfg, "sampler_name": "DPM++ SDE", "seed": seed,
               "width": MW, "height": MH,
               "override_settings": {"sd_model_checkpoint": CHECKPOINT}}
    r = requests.post(f"{FORGE}/sdapi/v1/txt2img", json=payload, timeout=600)
    r.raise_for_status()
    b64 = r.json()["images"][0]
    return Image.open(io.BytesIO(base64.b64decode(b64.split(",", 1)[-1]))).convert("RGB")

def main():
    import sys
    only = set(sys.argv[1:])  # optional tag filter, e.g. `python render_hero_haunted.py h3`
    pois = [p for p in json.load(open(HERE / "pilot_pois.json")) if p["id"] in PILOT_IDS]
    t0 = time.time()
    for poi in pois:
        # subject clause minus the per-POI lighting tail (haunted style owns the mood)
        subj = P.subject_clause(poi)
        seed = P.seed_for(poi["id"])
        for tag, style, cfg, neg in VARIANTS:
            if only and tag not in only:
                continue
            print(f"[{poi['id']}] {tag} cfg={cfg}", flush=True)
            img = gen(subj, style, cfg, neg, seed)
            img.save(OUT / f"hero_{poi['id']}_{tag}.webp", quality=92)
    print(f"DONE in {time.time()-t0:.0f}s -> {OUT}", flush=True)

if __name__ == "__main__":
    main()
