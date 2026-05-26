#!/usr/bin/env python3
"""
S299 bespoke-hero PILOT generator (art-bible v0.2 §4.1 + §5).

Reads a JSON manifest of real POIs (pilot_pois.json), builds a per-POI woodcut
prompt grounded in name/category/year, renders the locked hero master
(2.25:1, 1152x512, subject in a centered safe band). For 2 representative POIs
it also writes the real centerCrop simulations (Pixel portrait + landscape) so
the fitXY->centerCrop fit can be re-confirmed against real subjects.

This is intended to grow into the full 124-POI run: swap the manifest for the
full bespoke list and (optionally) drop the crop-simulation step.

Style/palette per docs/plans/graphics-art-bible.md:
  - ink-comic woodcut, heavy ink lines, flat cel shade, 1692 broadsheet
  - muted period palette; ghost-teal accent ONLY on cemeteries (spectral fog),
    mortal/daytime buildings + churches stay muted (bible §2/§5).
"""
import base64, hashlib, io, json, time
from pathlib import Path
import requests
from PIL import Image

FORGE = "http://127.0.0.1:7860"
CHECKPOINT = "shared/DreamShaperXL_Turbo_v2_1.safetensors [4496b36d48]"
HERE = Path(__file__).parent
MANIFEST = HERE / "pilot_pois.json"
OUT = HERE / "out_pilot"
OUT.mkdir(parents=True, exist_ok=True)

MW, MH = 1152, 512  # 2.25:1 master (matches Pixel portrait box; widest used)

# Shared woodcut base (bible §5). NB: the "hand-printed broadsheet texture" cue is
# DROPPED here -- on the S299 pilot it induced gibberish caption text (Saint Vasilios).
# Woodcut feel survives via the ink-outline + cel-shade + "broadsheet woodcut style" cues.
STYLE = ("ink-comic woodcut illustration, heavy black ink outlines, flat cel shading, "
         "1692 Salem broadsheet woodcut style, muted period palette parchment cream "
         "weathered grey earthy brown charcoal, 2D illustration, "
         "centered composition, subject centered in clear central band, "
         "establishing exterior view")
NEG = ("photorealistic, photograph, 3d render, soft airbrush, gradient, painterly, text, "
       "typography, letters, words, caption, label, title, signature, watermark, frame, "
       "border, modern objects, cars, people, crowd, bright saturated colors, neon, "
       "purple dominant, teal dominant, gore, blood, nude, deformed, low quality, "
       "off-center, cropped subject")

TEAL = "faint ghost-teal cyan spectral haze, mostly muted"
SPECTRAL = "faint ghost-teal cyan glow in the windows, eerie, mostly muted"
MUTED = "overcast muted daylight, no saturated color"

def is_cemetery(name):
    n = name.lower()
    return any(k in n for k in ("cemetery", "burying", "burial", "graveyard"))

# Architectural style clues mined from description/narration (bible §4.1: txt2img,
# narration-driven). First match per group wins; multiple groups can stack.
FIRST_PERIOD = ("First Period New England architecture, steep pointed gable roof, "
                "second-story jetty overhang, dark weathered clapboard, small "
                "diamond-pane casement windows, massive central chimney")
STYLE_CLUES = [   # substring in blurb -> visual injection
    ("first period",   FIRST_PERIOD),
    ("17th-century",   FIRST_PERIOD),
    ("17th century",   FIRST_PERIOD),
    ("federal-style",  "Federal-style architecture, symmetrical brick facade, white cupola"),
    ("federal style",  "Federal-style architecture, symmetrical brick facade, white cupola"),
    ("greek revival",  "Greek Revival architecture, granite columns, pediment"),
    ("colonnade",      "columned colonnade, pediment"),
    ("georgian",       "Georgian brick mansion, symmetrical"),
    ("mansion",        "large stately mansion"),
]
MATERIAL_CLUES = [
    ("brick",     "brick facade"),
    ("granite",   "granite stone facade"),
    ("clapboard", "clapboard siding"),
    ("gable",     "gabled roof"),
]
# Setting clues are SUBORDINATE (S299 fix): "far background" so the building stays
# the foreground subject (the first pilot's Custom House receded into a townscape).
SETTING_CLUES = [
    ("maritime",  "distant Salem harbor and tall-ship masts far in the background"),
    ("port",      "distant Salem harbor and tall-ship masts far in the background"),
    ("wharf",     "distant Salem harbor and tall-ship masts far in the background"),
    ("custom house","distant Salem harbor and tall-ship masts far in the background"),
]
# Per-POI building-kind overrides (S299): churches whose denomination isn't in the
# name, or other subjects the narration doesn't describe well.
POI_KIND_OVERRIDE = {
    "saint_vasilios_church": "Greek Orthodox church, Byzantine dome, orthodox cross",
    "st_john_the_baptist_ukrainian_church": "Ukrainian Orthodox church, Byzantine onion domes, orthodox cross",
}
# Worship denomination from NAME (bible §4.1 worship bespoke; names carry the cue).
DENOM_CLUES = [
    ("orthodox",  "Eastern Orthodox church, Byzantine onion dome, orthodox cross"),
    ("episcopal", "Gothic Revival stone church, pointed-arch windows, square bell tower"),
    ("grace",     "Gothic Revival stone church, pointed-arch windows, square bell tower"),
    ("methodist", "white New England clapboard church, tall slender steeple"),
    ("baptist",   "brick church, steeple, arched windows"),
    ("catholic",  "Catholic church, tall spire, rose window"),
    ("immaculate","Catholic church, tall spire, rose window"),
]
SPECTRAL_TEXT = ("witch", "trial", "haunt", "ghost", "spirit", "spectral")

def _mine(blurb, table, limit=2):
    out, b = [], blurb.lower()
    for key, inj in table:
        if key in b and inj not in out:
            out.append(inj)
            if len(out) >= limit:
                break
    return out

def subject_clause(poi):
    name, cat, yr = poi["name"], poi["category"], poi.get("year")
    blurb = (poi.get("blurb") or "")
    period = f"built {yr}" if yr else "colonial-era"

    if is_cemetery(name):
        return (f"{name}, the {period} historic burying ground in Salem Massachusetts, "
                f"rows of leaning colonial slate gravestones, bare gnarled trees, stone wall, "
                f"{TEAL}")

    if cat == "WORSHIP":
        if poi["id"] in POI_KIND_OVERRIDE:
            kind = POI_KIND_OVERRIDE[poi["id"]]
        else:
            denom = _mine(name + " " + blurb, DENOM_CLUES, limit=1)
            kind = denom[0] if denom else "New England clapboard church, steeple, tall arched windows"
        return f"{name}, a {period} {kind} in Salem Massachusetts, {MUTED}"

    # HISTORICAL_BUILDINGS non-cemetery: a base building-kind is ALWAYS present
    # (mined style clue, else name-shape fallback); material + setting are additive.
    base = _mine(blurb, STYLE_CLUES, limit=1)
    if not base:
        n = name.lower()
        base = ["stately Federal-style civic building, brick, white cupola"] if any(
            k in n for k in ("hall", "custom")) else \
            ["gabled colonial timber house, diamond-pane windows, steep roof"]
    clues = base + _mine(blurb, MATERIAL_CLUES, limit=1) + _mine(blurb, SETTING_CLUES, limit=1)
    light = SPECTRAL if any(k in blurb.lower() for k in SPECTRAL_TEXT) else MUTED
    return f"{name}, a {period} {', '.join(clues)} in Salem Massachusetts, bare autumn trees, {light}"

def seed_for(poi_id):
    return int(hashlib.sha1(poi_id.encode()).hexdigest()[:8], 16) % 2_000_000

def gen(prompt, seed, w=MW, h=MH, steps=8, cfg=2.5):
    payload = {"prompt": f"{prompt}, {STYLE}", "negative_prompt": NEG, "steps": steps,
               "cfg_scale": cfg, "sampler_name": "DPM++ SDE", "seed": seed, "width": w,
               "height": h, "override_settings": {"sd_model_checkpoint": CHECKPOINT}}
    r = requests.post(f"{FORGE}/sdapi/v1/txt2img", json=payload, timeout=600)
    r.raise_for_status()
    b64 = r.json()["images"][0]
    return Image.open(io.BytesIO(base64.b64decode(b64.split(",", 1)[-1]))).convert("RGB")

def center_crop_cover(img, cw, ch):
    """Android ImageView.ScaleType.CENTER_CROP."""
    scale = max(cw / img.width, ch / img.height)
    nw, nh = round(img.width * scale), round(img.height * scale)
    r = img.resize((nw, nh), Image.LANCZOS)
    left, top = (nw - cw) // 2, (nh - ch) // 2
    return r.crop((left, top, left + cw, top + ch))

CROP_BOXES = {"pixel_portrait": (1080, 480), "pixel_landscape": (2400, 216)}
CROP_DEMO_IDS = {"the_witch_house_at_salem", "charter_street_cemetery"}

def main():
    pois = json.load(open(MANIFEST))
    t0 = time.time()
    for poi in pois:
        pid = poi["id"]
        prompt = subject_clause(poi)
        seed = seed_for(pid)
        print(f"[{pid}] seed={seed}\n   {prompt[:120]}...", flush=True)
        m = gen(prompt, seed)
        m.save(OUT / f"hero_{pid}.webp", quality=92)
        if pid in CROP_DEMO_IDS:
            for box, (cw, ch) in CROP_BOXES.items():
                center_crop_cover(m, cw, ch).save(OUT / f"hero_{pid}__{box}.webp", quality=92)
    print(f"DONE {len(pois)} POIs in {time.time()-t0:.0f}s -> {OUT}", flush=True)

if __name__ == "__main__":
    main()
