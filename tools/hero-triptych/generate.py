#!/usr/bin/env python3
"""
Triptych hero generator — 10 sample POIs for Salem tour.

Each POI gets a 3-panel hero bar (800x200 WebP total, panels 160/320/320 @ 200h):
  Panel 1 — Katrina's cat-POV of the business (what a smart cat would notice)
  Panel 2 — painterly exterior with a small cat somewhere
  Panel 3 — a cartoon monster "working there" with a small cat in frame

Style: painterly cartoon storybook (matches welcome-cards + find-tiles + splash).
Engine: Forge + DreamShaperXL Turbo, 8-step, Karras.
"""

import base64
import io
import sys
import time
from pathlib import Path

import requests
from PIL import Image

SCRIPT_DIR = Path(__file__).parent
OUT_DIR = SCRIPT_DIR / "output"
PANELS_DIR = OUT_DIR / "panels"
TRIP_DIR = OUT_DIR / "triptychs"
for d in (OUT_DIR, PANELS_DIR, TRIP_DIR):
    d.mkdir(parents=True, exist_ok=True)

FORGE = "http://127.0.0.1:7860"
CHECKPOINT = "shared/DreamShaperXL_Turbo_v2_1.safetensors [4496b36d48]"

STYLE = (
    "painterly cartoon storybook illustration, cozy Salem colonial New England "
    "aesthetic, warm amber candlelight, dark teal and plum ambient background, "
    "muted vintage palette, subtle gold accents, soft brush strokes, cinematic "
    "composition, signs and signboards appear blank or show only abstract symbols, "
    "no legible text anywhere, no readable letters, no readable words, no typography"
)

NEG = (
    "photorealistic, photograph, realistic, scary, gore, blood, horror, "
    "demon, nudity, naked, sexual, violence, weapon, skull face, "
    "low quality, deformed, ugly, noisy, grainy, oversaturated, "
    "text, watermark, signature, logo, title, caption, words, letters, "
    "readable text, business name, store name, lettering, typography, "
    "sign with text, sign with words, storefront sign with lettering, "
    "gold letters, painted letters, menu board, chalkboard with words, "
    "labels, street signs, address numbers, "
    "multiple cats fighting"
)

KATRINA = (
    "a single long-haired grey and white cat with big round yellow-green eyes, "
    "pink nose, white face blaze, white chest, white paws, fluffy fur, tuxedo "
    "markings, curious intelligent expression"
)

POIS = [
    {
        "slug": "witch_house",
        "name": "The Witch House (Jonathan Corwin House)",
        "cat_scene": (
            f"{KATRINA} in the foreground observing the dim ancient Puritan kitchen "
            "of the Witch House, diamond-paned windows with candlelight, a warm glowing "
            "hearth, an iron cooking pot, dust motes drifting in amber light, a perfect "
            "cozy kitchen corner for a cat to nap, low cat's-eye perspective"
        ),
        "exterior": (
            "exterior view of the Witch House in Salem, a 17th-century black-timbered "
            "Puritan house with steep gables and diamond-pane windows, autumn twilight, "
            "a tiny tuxedo cat sitting on the cold stone doorstep"
        ),
        "monster": (
            "interior of the Witch House kitchen, a friendly cartoon witch in a tall "
            "pointy hat stirring a bubbling cauldron over a hearth fire, wooden beams "
            "overhead, a small grey tabby cat perched on the rafters watching"
        ),
    },
    {
        "slug": "rockafellas",
        "name": "Rockafellas Restaurant",
        "cat_scene": (
            f"{KATRINA} in the foreground observing a cozy upscale restaurant dining "
            "room, candlelit tables with white tablecloths, plates of sizzling seafood, "
            "a warm tuna dish, brass railings to jump onto, warm pools of candlelight, "
            "low cat's-eye perspective"
        ),
        "exterior": (
            "exterior of a historic red brick Salem restaurant facade "
            "with large bright windows glowing warm amber, a brass doorway lantern, "
            "cobblestone sidewalk, a small tortoiseshell cat perched on the windowsill"
        ),
        "monster": (
            "interior of an elegant restaurant, a friendly cartoon vampire in a "
            "butler's tuxedo serving a plate of shrimp at a candlelit table, a small "
            "black cat peeking out from under a nearby chair"
        ),
    },
    {
        "slug": "first_church_1692",
        "name": "First Church Meetinghouse (1692 Site)",
        "cat_scene": (
            f"{KATRINA} in the foreground observing a quiet Puritan meetinghouse "
            "interior, straight-back wooden pews in long rows, a tall wooden pulpit, "
            "dust motes drifting in a shaft of morning sunlight from tall windows, "
            "a warm wooden pew perfect to curl up on, low cat's-eye perspective"
        ),
        "exterior": (
            "exterior of an austere white wooden New England Puritan meetinghouse "
            "with a simple colonial silhouette against a deep plum dusk sky, a small "
            "orange cat sitting beside a stone marker"
        ),
        "monster": (
            "interior of a wooden Puritan meetinghouse, a kindly translucent cartoon "
            "ghost preacher in colonial Puritan black clothes standing at a tall "
            "wooden pulpit with an open leather book showing only abstract illuminated "
            "designs (no legible text), a small striped cat sitting attentively in the first pew"
        ),
    },
    {
        "slug": "old_burying_point",
        "name": "Old Burying Point / Charter Street Cemetery",
        "cat_scene": (
            f"{KATRINA} in the foreground observing leaning weathered slate headstones "
            "in a twilight cemetery, soft autumn leaves covering cool grass, low stone "
            "walls with mossy warm spots, small creatures rustling in the leaves, "
            "low cat's-eye perspective"
        ),
        "exterior": (
            "exterior of Charter Street Burying Point cemetery, ancient tilted slate "
            "gravestones scattered in autumn fog, wrought iron gate, a small grey cat "
            "perched quietly on top of an old headstone"
        ),
        "monster": (
            "a cemetery path between old headstones, a friendly cartoon groundskeeper "
            "skeleton in a flannel shirt and overalls raking autumn leaves, a small "
            "tabby cat batting at the leaf pile"
        ),
    },
    {
        "slug": "salem_common",
        "name": "Salem Common",
        "cat_scene": (
            f"{KATRINA} in the foreground observing a Salem park lawn at golden hour, "
            "iron lampposts casting warm pools of light, tall oaks with crackly autumn "
            "leaves to pounce, a picnic basket, warm sunny patches on the grass, "
            "low cat's-eye perspective"
        ),
        "exterior": (
            "exterior of Salem Common park at dusk, black iron fence, tall autumn "
            "trees with gold leaves, a wrought-iron lamppost, a small black cat sitting "
            "in the middle of the grass path"
        ),
        "monster": (
            "a Salem park lawn in autumn, a friendly cartoon werewolf park ranger in "
            "a green uniform hat raking autumn leaves, a small white cat riding on top "
            "of the leaf pile"
        ),
    },
    {
        "slug": "hex_witchery",
        "name": "HEX: Old World Witchery",
        "cat_scene": (
            f"{KATRINA} in the foreground observing a dim velvet-draped witchery shop, "
            "shelves of glowing potion bottles and crystal points, a crystal ball on a "
            "silk cloth, a bowl of dried catnip, cozy velvet chairs, warm candle pools "
            "of light, low cat's-eye perspective"
        ),
        "exterior": (
            "exterior of a Salem witch shop, a black timber facade with a swinging iron "
            "pentacle-shaped medallion over the door (abstract symbol only, no text), "
            "warm candle glow in small windows, a tiny black cat sitting under the medallion"
        ),
        "monster": (
            "interior of a cozy witch shop, a cheerful cartoon green-skinned witch in "
            "a velvet dress arranging glowing potion bottles behind a wooden counter, "
            "a small black cat asleep on a stack of ancient leather-bound tomes with "
            "blank covers"
        ),
    },
    {
        "slug": "peabody_essex",
        "name": "Peabody Essex Museum",
        "cat_scene": (
            f"{KATRINA} in the foreground observing a grand museum gallery with polished "
            "wood floors, gleaming brass railings just the right height to walk along, "
            "display cases reflecting soft spotlights, velvet benches warm from sunlight, "
            "low cat's-eye perspective"
        ),
        "exterior": (
            "exterior of the Peabody Essex Museum, a grand stone facade with tall "
            "columns and warm museum lobby light spilling out onto cobblestones, a "
            "small grey cat sitting on the front steps"
        ),
        "monster": (
            "interior of a museum gallery, a friendly cartoon mummy curator in khaki "
            "tweed holding a blank wooden clipboard and gesturing at an ancient artifact "
            "in a glass case, a small sandy-colored cat investigating a display pedestal, "
            "no labels or placards with text"
        ),
    },
    {
        "slug": "hawthorne_hotel",
        "name": "Hawthorne Hotel",
        "cat_scene": (
            f"{KATRINA} in the foreground observing a grand hotel lobby with a crackling "
            "fireplace, overstuffed velvet armchairs, a polished brass luggage rack, "
            "oriental rug patterns perfect for kneading, a concierge desk with treats, "
            "low cat's-eye perspective"
        ),
        "exterior": (
            "exterior of a grand historic brick hotel, green awnings over the front "
            "entrance, warm glow from a row of brass hanging lanterns (no signs, no "
            "lettering), a small black cat sitting on a red brick step by the front doors"
        ),
        "monster": (
            "interior of an elegant hotel lobby, a polite cartoon Frankenstein's-monster-"
            "style bellhop in a red uniform holding a brass room key behind the front "
            "desk, a small calico cat curled up on the oriental lobby rug"
        ),
    },
    {
        "slug": "reds_sandwich",
        "name": "Red's Sandwich Shop",
        "cat_scene": (
            f"{KATRINA} in the foreground observing a classic chrome diner interior, "
            "red vinyl stools at a counter, a griddle with sizzling bacon, stacks of "
            "plates, a tall milkshake, cozy warm booth cushions, low cat's-eye perspective"
        ),
        "exterior": (
            "exterior of a classic red-and-white colonial diner storefront in Salem, "
            "big diner windows glowing warm, a blue awning over the entrance (no text, "
            "no lettering), a small ginger cat waiting at the front door"
        ),
        "monster": (
            "interior of a classic chrome diner, a cheerful cartoon goblin diner cook "
            "in a white paper hat and apron flipping pancakes on a sizzling grill, a "
            "small orange tabby cat peeking out from behind the counter"
        ),
    },
    {
        "slug": "bunghole_liquors",
        "name": "Bunghole Liquors",
        "cat_scene": (
            f"{KATRINA} in the foreground observing a tall-shelved liquor shop, rows "
            "of glowing colorful bottles, cardboard boxes perfect to nap in, a warm "
            "counter with a register, wooden barrels just the right height for climbing, "
            "low cat's-eye perspective"
        ),
        "exterior": (
            "exterior of a quirky red brick Salem liquor storefront, a carved wooden "
            "whiskey barrel emblem hung over the door (no text, no lettering), warm "
            "amber light in the window, a small black cat sitting on an empty wine crate "
            "out front"
        ),
        "monster": (
            "interior of a cozy liquor store, a cartoon gruff but friendly pirate "
            "shopkeeper with an eye patch stocking shelves of whiskey bottles, a small "
            "black-and-white cat perched on top of a wooden wine barrel"
        ),
    },
]


def wait_for_forge():
    for _ in range(60):
        try:
            if requests.get(f"{FORGE}/sdapi/v1/sd-models", timeout=3).status_code == 200:
                return
        except Exception:
            pass
        time.sleep(1)
    print("Forge not reachable", file=sys.stderr)
    sys.exit(2)


def set_checkpoint():
    r = requests.post(
        f"{FORGE}/sdapi/v1/options",
        json={"sd_model_checkpoint": CHECKPOINT},
        timeout=600,
    )
    r.raise_for_status()


def gen_panel(prompt, seed, width, height):
    payload = {
        "prompt": f"{prompt}, {STYLE}",
        "negative_prompt": NEG,
        "steps": 8,
        "cfg_scale": 2.5,
        "sampler_name": "DPM++ SDE",
        "scheduler": "Karras",
        "width": width,
        "height": height,
        "seed": seed,
    }
    r = requests.post(f"{FORGE}/sdapi/v1/txt2img", json=payload, timeout=600)
    r.raise_for_status()
    img_b64 = r.json()["images"][0]
    return Image.open(io.BytesIO(base64.b64decode(img_b64)))


def fit_crop(img, target_w, target_h):
    src_w, src_h = img.size
    src_aspect = src_w / src_h
    tgt_aspect = target_w / target_h
    if src_aspect > tgt_aspect:
        new_w = int(src_h * tgt_aspect)
        left = (src_w - new_w) // 2
        img = img.crop((left, 0, left + new_w, src_h))
    elif src_aspect < tgt_aspect:
        new_h = int(src_w / tgt_aspect)
        top = (src_h - new_h) // 2
        img = img.crop((0, top, src_w, top + new_h))
    return img.resize((target_w, target_h), Image.LANCZOS)


def composite(p1, p2, p3):
    out = Image.new("RGB", (800, 200), (10, 10, 20))
    out.paste(fit_crop(p1, 160, 200), (0, 0))
    out.paste(fit_crop(p2, 320, 200), (160, 0))
    out.paste(fit_crop(p3, 320, 200), (480, 0))
    return out


def build_gallery(entries):
    parts = ["""<!doctype html><html><head><meta charset=utf-8>
<title>Katrina Triptych Heroes — Sample Set</title>
<style>
  body{margin:0;padding:24px;background:#111;color:#eee;font-family:system-ui,sans-serif;max-width:1200px;margin-left:auto;margin-right:auto}
  h1{margin:0 0 4px 0}
  .sub{color:#888;margin-bottom:24px;font-size:14px;line-height:1.5}
  .poi{background:#1c1c20;border:1px solid #2a2a30;border-radius:8px;margin-bottom:24px;overflow:hidden}
  .poi h2{margin:0;padding:12px 16px;background:#2a2a30;color:#e6b84c;font-size:15px;letter-spacing:.02em}
  .heroblock{padding:16px;background:#141418}
  .hero{display:block;width:100%;max-width:1152px;height:auto;cursor:zoom-in;image-rendering:auto;border:1px solid #333}
  .hero.actual{width:800px;max-width:100%;image-rendering:pixelated}
  .heroactualwrap{margin-top:12px;display:flex;flex-direction:column;align-items:flex-start;gap:6px}
  .actuallabel{color:#888;font-size:11px;font-family:monospace}
  .legend{display:grid;grid-template-columns:160fr 320fr 320fr;gap:0;font-size:11px;color:#aaa}
  .legend > div{padding:10px 12px;border-right:1px solid #2a2a30;border-top:1px solid #2a2a30;line-height:1.4}
  .legend > div:last-child{border-right:none}
  .legend b{color:#e6b84c;display:block;margin-bottom:4px}
  .panelrow{display:flex;gap:8px;padding:12px 16px;background:#1c1c20;border-top:1px solid #2a2a30}
  .panelrow img{display:block;height:128px;cursor:zoom-in;border:1px solid #333}
  dialog{border:none;padding:0;background:transparent;max-width:95vw;max-height:95vh}
  dialog::backdrop{background:rgba(0,0,0,.92)}
  dialog img{max-width:95vw;max-height:95vh;display:block}
  .notes{padding:8px 16px;background:#141418;color:#888;font-size:11px;font-style:italic;border-top:1px solid #2a2a30}
</style></head><body>
<h1>Katrina Triptych Heroes — Sample Set</h1>
<div class=sub>10 sample POIs · 800×200 WebP triptych (panels 160/320/320) · DreamShaperXL Turbo + painterly storybook style.<br>
The large image is the triptych scaled up so you can see composition; the <b>actual 800×200</b> version is shown below it at native size.
Click anything to zoom.</div>
"""]
    for i, e in enumerate(entries, start=1):
        trip = e["filename"]
        p1, p2, p3 = e["panel1"], e["panel2"], e["panel3"]
        name = e["name"]
        prompts = e["prompts"]
        cs = prompts[0].replace("<", "&lt;").replace(">", "&gt;")
        ex = prompts[1].replace("<", "&lt;").replace(">", "&gt;")
        mo = prompts[2].replace("<", "&lt;").replace(">", "&gt;")
        parts.append(f"""<div class=poi>
  <h2>{i}. {name}</h2>
  <div class=heroblock>
    <img class=hero src="triptychs/{trip}" loading=lazy onclick="document.getElementById('d{i}').showModal()">
    <div class=heroactualwrap>
      <div class=actuallabel>↓ actual 800×200 size (what the app would render):</div>
      <img class="hero actual" src="triptychs/{trip}">
    </div>
  </div>
  <div class=legend>
    <div><b>Panel 1 · Katrina's cat-POV (160×200)</b>{cs}</div>
    <div><b>Panel 2 · Exterior + small cat (320×200)</b>{ex}</div>
    <div><b>Panel 3 · Monster at work + small cat (320×200)</b>{mo}</div>
  </div>
  <div class=panelrow>
    <img src="panels/{p1}" loading=lazy onclick="document.getElementById('d{i}a').showModal()" title="Panel 1 native">
    <img src="panels/{p2}" loading=lazy onclick="document.getElementById('d{i}b').showModal()" title="Panel 2 native">
    <img src="panels/{p3}" loading=lazy onclick="document.getElementById('d{i}c').showModal()" title="Panel 3 native">
  </div>
  <div class=notes>Seeds — P1: {e["seeds"][0]} · P2: {e["seeds"][1]} · P3: {e["seeds"][2]} · triptych file: {trip}</div>
</div>
<dialog id=d{i} onclick="this.close()"><img src="triptychs/{trip}" style="image-rendering:pixelated;width:95vw;height:auto"></dialog>
<dialog id=d{i}a onclick="this.close()"><img src="panels/{p1}"></dialog>
<dialog id=d{i}b onclick="this.close()"><img src="panels/{p2}"></dialog>
<dialog id=d{i}c onclick="this.close()"><img src="panels/{p3}"></dialog>
""")
    parts.append("</body></html>\n")
    (OUT_DIR / "gallery.html").write_text("".join(parts))


def main():
    print(f"Forge at {FORGE} — waiting...", flush=True)
    wait_for_forge()
    print("  up. Setting checkpoint...", flush=True)
    set_checkpoint()

    entries = []
    total = len(POIS)
    t_start = time.time()
    for i, poi in enumerate(POIS, start=1):
        slug = poi["slug"]
        seeds = [7000 + i * 10, 7000 + i * 10 + 1, 7000 + i * 10 + 2]
        print(f"[{i}/{total}] {poi['name']}  seeds={seeds}", flush=True)

        t0 = time.time()
        p1 = gen_panel(poi["cat_scene"], seeds[0], 832, 1216)
        p2 = gen_panel(poi["exterior"], seeds[1], 1216, 832)
        p3 = gen_panel(poi["monster"], seeds[2], 1216, 832)

        p1_name = f"{slug}_p1_katrina.webp"
        p2_name = f"{slug}_p2_exterior.webp"
        p3_name = f"{slug}_p3_monster.webp"
        p1.save(PANELS_DIR / p1_name, "WEBP", quality=88)
        p2.save(PANELS_DIR / p2_name, "WEBP", quality=88)
        p3.save(PANELS_DIR / p3_name, "WEBP", quality=88)

        trip = composite(p1, p2, p3)
        trip_name = f"{slug}.webp"
        trip.save(TRIP_DIR / trip_name, "WEBP", quality=85)

        kb = (TRIP_DIR / trip_name).stat().st_size / 1024
        print(f"  done in {time.time()-t0:.1f}s · triptych {kb:.0f} KB", flush=True)

        entries.append({
            "slug": slug,
            "name": poi["name"],
            "filename": trip_name,
            "panel1": p1_name, "panel2": p2_name, "panel3": p3_name,
            "seeds": seeds,
            "prompts": [poi["cat_scene"], poi["exterior"], poi["monster"]],
        })

        build_gallery(entries)

    elapsed = time.time() - t_start
    gallery = OUT_DIR / "gallery.html"
    print(f"\nAll done in {elapsed/60:.1f} min.", flush=True)
    print(f"Gallery: file://{gallery.resolve()}", flush=True)


if __name__ == "__main__":
    main()
