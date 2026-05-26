#!/usr/bin/env python3
"""
S299 FULL bespoke-hero run — all 124 (107 HIST_BLDG + 17 WORSHIP).

Locked recipe:
  - subject  : narration-mined clue prompt (render_hero_pilot.subject_clause),
               with S299 tuning (subordinate setting clue + per-POI kind overrides)
  - mood     : h3 haunted-Halloween woodcut (render_hero_haunted.STYLE_H3 / NEG_H3)
  - format   : 2.25:1 master, 1152x512 WebP, centerCrop-ready safe band
  - QC       : easyocr text-reject with a 6-attempt seed-spread retry (kills the
               occasional gibberish caption); accept best-effort on the 6th.

Reads bespoke_full.json; writes out_full/hero_<id>.webp. Resumable: skips a POI
whose output already exists unless --force. Writes out_full/manifest.json
(id/name/category/year/prompt/retries) for the gallery.
"""
import base64, io, json, sys, time
from pathlib import Path
import numpy as np
import requests
from PIL import Image
import render_hero_pilot as P
import render_hero_haunted as H

FORGE = "http://127.0.0.1:7860"
CHECKPOINT = "shared/DreamShaperXL_Turbo_v2_1.safetensors [4496b36d48]"
HERE = Path(__file__).parent
OUT = HERE / "out_full"
OUT.mkdir(parents=True, exist_ok=True)
MW, MH = 1152, 512
CFG = 4.0
FORCE = "--force" in sys.argv

_OCR = None
def ocr():
    global _OCR
    if _OCR is None:
        print("loading easyocr (CUDA)...", flush=True)
        import easyocr
        _OCR = easyocr.Reader(["en"], gpu=True, verbose=False)
    return _OCR

def has_text(img, min_conf=0.30, min_chars=3):
    res = ocr().readtext(np.array(img.convert("RGB")))
    hits = [(t, c) for (_b, t, c) in res if c >= min_conf and len(t.strip()) >= min_chars]
    return (len(hits) > 0), hits

# Subject-specific negative guards (S299): memorials/statues/fountains/sites kept
# drifting to churches/houses. Block large buildings on those subjects.
NO_BUILDING_NEG = ", church, steeple, chapel, large building, house, mansion, cathedral, tower"
def neg_for(prompt):
    p = prompt.lower()
    if any(k in p for k in ("stone memorial", "bronze statue", "stone fountain",
                            "memorial arch", "historic site of", "militia memorial",
                            "single monument")):
        return H.NEG_H3 + NO_BUILDING_NEG
    return H.NEG_H3

def gen(prompt, seed):
    payload = {"prompt": f"{prompt}, {H.STYLE_H3}", "negative_prompt": neg_for(prompt), "steps": 9,
               "cfg_scale": CFG, "sampler_name": "DPM++ SDE", "seed": seed,
               "width": MW, "height": MH,
               "override_settings": {"sd_model_checkpoint": CHECKPOINT}}
    r = requests.post(f"{FORGE}/sdapi/v1/txt2img", json=payload, timeout=600)
    r.raise_for_status()
    b64 = r.json()["images"][0]
    return Image.open(io.BytesIO(base64.b64decode(b64.split(",", 1)[-1]))).convert("RGB")

def main():
    pois = json.load(open(HERE / "bespoke_full.json"))
    # optional subset: --ids a,b,c (forces re-render of just those)
    only_ids = None
    for a in sys.argv:
        if a.startswith("--ids="):
            only_ids = set(a.split("=", 1)[1].split(","))
    if only_ids:
        pois = [p for p in pois if p["id"] in only_ids]
        globals()["FORCE"] = True
    manifest, t0, done = [], time.time(), 0
    for i, poi in enumerate(pois, 1):
        pid = poi["id"]
        outp = OUT / f"hero_{pid}.webp"
        prompt = P.subject_clause(poi)
        base_seed = P.seed_for(pid)
        if outp.exists() and not FORCE:
            manifest.append({**{k: poi.get(k) for k in ("id", "name", "category", "year")},
                             "prompt": prompt, "retries": None, "skipped": True})
            continue
        img, retries, clean = None, 0, False
        for attempt in range(6):
            seed = base_seed + attempt * 97
            img = gen(prompt, seed)
            txt, hits = has_text(img)
            if not txt:
                clean = True
                break
            retries = attempt + 1
            if attempt == 0:
                print(f"  [{pid}] OCR hit {hits[:2]} -> retry", flush=True)
        img.save(outp, quality=92)
        done += 1
        manifest.append({**{k: poi.get(k) for k in ("id", "name", "category", "year")},
                         "prompt": prompt, "retries": retries, "clean": clean})
        print(f"[{i}/{len(pois)}] {pid}  retries={retries} clean={clean}  "
              f"({time.time()-t0:.0f}s)", flush=True)
    if not only_ids:  # don't clobber the full manifest on a subset re-render
        json.dump(manifest, open(OUT / "manifest.json", "w"), indent=2)
    print(f"DONE rendered={done} total={len(pois)} in {time.time()-t0:.0f}s -> {OUT}", flush=True)

if __name__ == "__main__":
    main()
