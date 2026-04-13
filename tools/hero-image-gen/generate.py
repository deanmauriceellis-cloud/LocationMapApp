#!/usr/bin/env python3
"""Batch hero image generator — Forge API + live QC viewer.

Reads hero-prompts.json (from export-hero-prompts.py), sends each prompt to
Forge's /sdapi/v1/txt2img API, saves output as WebP, and serves a live QC
web viewer for operator quality control.

Usage:
    1. Start Forge:  ~/AI-Studio/forge-start.sh
    2. Export prompts: ~/Development/SalemIntelligence/.venv/bin/python3 export-hero-prompts.py
    3. Run generator: python3 generate.py [--resume] [--port 4400]

    Open http://localhost:4400 in browser for live QC viewer.

Options:
    --resume     Skip entities that already have images in output/full/
    --port N     QC viewer port (default 4400)
    --limit N    Generate only first N images (for testing)
    --forge URL  Forge API base URL (default http://localhost:7860)
"""

import argparse
import base64
import io
import json
import os
import sys
import threading
import time
from datetime import datetime
from http.server import HTTPServer, SimpleHTTPRequestHandler
from pathlib import Path

import requests
from PIL import Image

SCRIPT_DIR = Path(__file__).parent
OUTPUT_FULL = SCRIPT_DIR / "output" / "full"
OUTPUT_APP = SCRIPT_DIR / "output" / "app"
MANIFEST_PATH = SCRIPT_DIR / "hero-prompts.json"
STATUS_PATH = SCRIPT_DIR / "output" / "status.json"
QC_HTML_PATH = SCRIPT_DIR / "qc-viewer.html"

# Style — scary menacing but humorous cartoon, interior, objects hate you
STYLE_PREFIX = (
    "scary menacing but humorous cartoon, INTERIOR VIEW INSIDE the room, "
)
STYLE_SUFFIX = (
    ", every single object in the room is alive and has its own angry opinion about you, "
    "objects glare at you with suspicion and hatred, grumpy hostile furniture, "
    "annoyed bottles, disgusted shelves, furious appliances, judgmental walls, "
    "cartoon illustration, thick black outlines, cel shaded, 2D animated, "
    "bold Halloween colors, NOT realistic NOT a photograph NOT exterior"
)

# DreamShaper 8 (SD 1.5) — naturally cartoon, upscale after
FORGE_SETTINGS = {
    "width": 768,
    "height": 192,
    "steps": 30,
    "cfg_scale": 7.5,
    "sampler_name": "DPM++ 2M Karras",
    "negative_prompt": (
        "photorealistic, photograph, photo, realistic, real life, DSLR, "
        "hyperrealistic, stock photo, RAW photo, film grain, "
        "natural lighting, depth of field, bokeh, analog, cinematic, "
        "3d render, CGI, unreal engine, "
        "exterior view, outside, building exterior, storefront, facade, street, sidewalk, outdoor, "
        "books, bookshelves, library, leather-bound books, stacks of books, "
        "blurry, low quality, deformed, disfigured, "
        "watermark, text, logo, signature, readable words, letters"
    ),
}

# App output dimensions
APP_WIDTH = 800
APP_HEIGHT = 200


def load_manifest() -> list[dict]:
    if not MANIFEST_PATH.exists():
        print(f"ERROR: {MANIFEST_PATH} not found. Run export-hero-prompts.py first.",
              file=sys.stderr)
        sys.exit(1)
    return json.loads(MANIFEST_PATH.read_text())


def update_status(status: dict):
    STATUS_PATH.write_text(json.dumps(status, indent=2, ensure_ascii=False))


def extract_products(prompt: str) -> str:
    """Mine the LLM prompt for product/object nouns — the useful visual content."""
    import re

    # Kill exterior/atmospheric sentences and phrases — keep product details
    JUNK_SENTENCES = [
        r'(?i)[^.]*\b(storefront|shopfront|facade|exterior|building sits|building with)\b[^.]*\.\s*',
        r'(?i)[^.]*\b(cobblestone|sidewalk|roofline|from the street|from outside)\b[^.]*\.\s*',
        r'(?i)[^.]*\b(gas lamp|lamp post|street lamp|iron lamp)\b[^.]*\.\s*',
        r'(?i)[^.]*\b(jack-o.lantern|carved pumpkin|scattered pumpkin)\b[^.]*\.\s*',
        r'(?i)[^.]*\b(iron fence|wrought.iron|wooden sign|sign above)\b[^.]*\.\s*',
        r'(?i)[^.]*\b(ground fog|fog rolls|fog creeps|fog curls|wisp of fog)\b[^.]*\.\s*',
        r'(?i)[^.]*\b(maple leaves|autumn leaves|fallen leaves|crimson leaves)\b[^.]*\.\s*',
        r'(?i)[^.]*\b(tall ships?|harbor|masts|wharf)\b[^.]*\.\s*',
        r'(?i)[^.]*\b(the entire scene|the overall|color palette)\b[^.]*\.\s*',
        r'(?i)[^.]*\b(In the distance)\b[^.]*\.\s*',
        r'(?i)Outside,\s*[^.]*\.\s*',
    ]
    JUNK_PHRASES = [
        r'(?i)stylized halloween illustration[,.]?\s*',
        r'(?i)horizontal banner composition[,.]?\s*',
        r'(?i)through (the |a )?\w*\s*window[^,.]*[,.]\s*',
        r'(?i)\bat (dusk|twilight|sunset|dawn)\b[,.]?\s*',
        r'(?i)on a (misty |quiet |cobblestone )?\w+ street[,.]?\s*',
        r'(?i)in Salem, Massachusetts[,.]?\s*',
        r'(?i)the blurred lights of Salem[^,.]*[,.]\s*',
        r'(?i)the October \w+[,.]?\s*',
    ]

    result = prompt
    for pattern in JUNK_SENTENCES:
        result = re.sub(pattern, ' ', result)
    for pattern in JUNK_PHRASES:
        result = re.sub(pattern, '', result)

    result = re.sub(r'\s{2,}', ' ', result).strip(' ,.')
    return result


# Interior scene descriptions by category AND subcategory
# Subcategories checked first (more specific), then category fallback
SUBCATEGORY_SCENES = {
    # FOOD_DRINK subcategories
    "cafes": "cozy cafe with espresso machine steaming, rows of coffee mugs, pastry display case, coffee beans in glass jars, chalkboard menu",
    "bars": "dark moody bar with rows of liquor bottles glowing on backlit shelves, beer taps, cocktail shaker, whiskey glasses, bar stools",
    "bakeries": "bakery with golden bread loaves, frosted cupcakes, layer cakes, rolling pins, flour-dusted counter, pastry bags, baking trays",
    # SHOPPING subcategories
    "bookstores": "bookstore with towering bookshelves, stacks of books everywhere, reading nooks, old maps, leather armchairs",
    "antiques": "antique shop with vintage furniture, tarnished silver, grandfather clocks, porcelain dolls, brass candlesticks, old paintings",
    "beauty_spa": "spa with massage oils, hot stones, facial masks, manicure stations, nail polish bottles, fluffy towels, candles",
    "souvenirs": "souvenir shop with Salem witch magnets, snow globes, t-shirts on racks, postcards, keychains, mugs with witch logos",
    "cannabis": "cannabis dispensary with glass jars of colorful buds, edible packages, vape pens, rolling papers, scales, display cases",
    # ENTERTAINMENT subcategories
    "tour_operators": "tour office with Salem maps on walls, brochures, witch tour posters, compass, lanterns, walking sticks, costume hats",
    "fitness": "gym interior with dumbbells, weight benches, treadmills, punching bags, yoga mats, kettlebells, wall mirrors",
    # TOURISM_HISTORY subcategories
    "museums": "museum gallery with display cases of artifacts, spotlights, velvet ropes, historical dioramas, old paintings",
    "attractions": "tourist attraction with interactive exhibits, memorabilia displays, souvenir counter, ticket booth, colorful posters",
    "galleries": "art gallery with paintings on walls in ornate frames, sculptures on pedestals, spotlight beams, polished wood floors",
}

CATEGORY_SCENES = {
    "restaurant": "restaurant kitchen with pots and pans and plates of food",
    "cafe": "cafe with espresso machine and pastries and coffee mugs",
    "bar": "bar with liquor bottles and beer taps and cocktail glasses",
    "shop_occult": "witchcraft shop with potions and crystals and spell books and candles",
    "shop_retail": "retail shop with products on shelves",
    "spa_beauty": "beauty salon with styling chairs and beauty products",
    "service_health": "medical office with exam table and medical instruments",
    "service_professional": "professional office with desk and equipment",
    "fitness": "gym with weights and exercise equipment",
    "education": "classroom with desks and school supplies",
    "religious": "church interior with pews and stained glass",
    "TOURISM_HISTORY": "museum with display cases and artifacts",
    "ENTERTAINMENT": "entertainment venue with stage and seats",
    "LODGING": "hotel lobby with reception desk and luggage",
    "SHOPPING": "shop interior with merchandise on shelves",
    "FOOD_DRINK": "dining room with tables and plates of food",
    "HEALTHCARE": "medical clinic with exam equipment and medicine",
    "CIVIC": "government office with service counter and forms",
    "OFFICES": "office with desks and computer monitors",
    "AUTO_SERVICES": "auto garage with a big cartoon car on a lift, engine parts, tires, wrenches, oil drums",
    "FINANCE": "bank with teller windows and vault",
    "WORSHIP": "church with pews and stained glass windows",
    "WITCH_SHOP": "witchcraft shop with potions and crystals and tarot and cauldrons",
    "PSYCHIC": "psychic reading room with crystal ball and tarot cards and candles",
    "HAUNTED_ATTRACTION": "haunted house with cobwebs and monsters and fog",
    "GHOST_TOUR": "ghost tour room with lanterns and costumes and props",
    "PARKS_REC": "park with benches and paths and trees",
    "EDUCATION": "classroom with desks and chalkboard and supplies",
}

# Name-keyword → product scene (overrides category when name is more specific)
NAME_SCENES = {
    "pet": "pet shop with cartoon dogs and cats, pet toys, food bowls, leashes, squeaky toys, fish tanks, pet beds",
    "vet": "veterinary clinic with cartoon dogs and cats on exam tables, stethoscope, medicine bottles, paw print charts",
    "animal": "animal shelter with cartoon dogs cats rabbits in kennels, pet carriers, feeding bowls, adoption posters",
    "auto part": "auto parts store with car batteries, oil filters, spark plugs, brake rotors, windshield wipers, tool boxes",
    "autozone": "auto parts store with car batteries, oil filters, spark plugs, brake rotors, windshield wipers, tool boxes",
    "o'reilly": "auto parts store with car batteries, oil filters, spark plugs, brake rotors, tool boxes, shop manuals",
    "napa": "auto parts store with car batteries, oil filters, spark plugs, brake pads, belts, hoses, tool boxes",
    "tire": "tire shop with stacked tires, tire irons, lug nuts, car jacks, air compressors, wheel rims",
    "pizza": "pizzeria with bubbling pizzas, wood-fired oven with flames, pizza peels, dough balls, tomato sauce, cheese",
    "sushi": "sushi bar with sushi rolls, sashimi, chopsticks, soy sauce, wasabi, bamboo mats, sharp knives",
    "nail": "nail salon with hundreds of colorful nail polish bottles, manicure stations, nail files, UV lamps",
    "hair": "hair salon with barber chairs, scissors, combs, hair dryers, mirrors, shelves of hair products",
    "dental": "dental office with dental chair, examination light, toothbrushes, model teeth, dental tools, x-rays",
    "flower": "flower shop with bouquets of colorful flowers, vases, ribbons, flower pots, watering cans, pruning shears",
    "bakery": "bakery with fresh bread loaves, cupcakes with frosting, wedding cakes, rolling pins, flour-dusted counter",
    "gym": "gym with dumbbells, treadmills, weight benches, punching bags, yoga mats, kettlebells, mirrors",
    "laundry": "laundromat with rows of washing machines and dryers, folded clothes, detergent bottles, laundry baskets",
    "bank": "bank interior with vault door, stacks of coins, teller windows, brass fixtures, safe deposit boxes",
    "pharmacy": "pharmacy with medicine bottles on shelves, prescription counter, pill bottles, mortar and pestle",
    "tattoo": "tattoo parlor with tattoo machines, ink bottles in every color, flash art on walls, leather chair",
    "liquor": "liquor store with shelves of whiskey bourbon wine bottles, beer coolers, shot glasses, barrel decorations",
    "donut": "donut shop with trays of frosted donuts with sprinkles, glazed donuts, display case, coffee pots",
    "ice cream": "ice cream parlor with tubs of colorful ice cream, waffle cones, sprinkles, sundae glasses, scoops",
    "convenience": "convenience store with snack aisles, soda coolers, candy bars, lottery tickets, slushie machine",
    "7-eleven": "convenience store with snack aisles, soda bottles, candy bars, chip bags, slushie machine glowing neon",
    "insurance": "insurance office with desk, stacks of policy documents, filing cabinets, brass nameplate, desk lamp",
    "law": "law office with leather-bound law books, mahogany desk, brass scales of justice, framed diplomas",
    "real estate": "real estate office with property listings on wall, house models, desk with contracts, key rack",
    "yoga": "yoga studio with yoga mats, incense, meditation cushions, plants, soft lighting, wall mirrors",
    "martial art": "martial arts dojo with punching bags, training mats, wooden dummies, weapons rack, belt display",
    "daycare": "daycare with colorful toys, tiny chairs, finger paintings on walls, building blocks, stuffed animals",
    "clean": "cleaning supply room with mops, brooms, spray bottles, buckets of soapy water, vacuum cleaners, rubber gloves, feather dusters",
    "dry clean": "dry cleaner with racks of hanging clothes in plastic wrap, pressing machines, hangers, steam iron",
    "auto repair": "auto repair garage with a big cartoon car on a hydraulic lift, hood open showing engine, wrenches and ratchets, oil cans, tires stacked",
    "auto body": "auto body shop with a cartoon car being spray painted, spray guns, dented panels, paint cans everywhere",
    "collision": "collision repair shop with a cartoon car with dents and scratches, body hammers, welding torch, paint booth",
    "garage": "mechanic garage with a big cartoon car on a lift, hood popped open, engine parts scattered, oil drums, wrenches on pegboard, tires",
    "motor": "auto mechanic shop with cartoon cars and engines on workbenches, transmissions, car parts on shelves, wrenches",
    "exhaust": "muffler shop with exhaust pipes, catalytic converters, welding equipment, car on lift",
    "plumb": "plumbing workshop with pipes, wrenches, faucets, toilet fixtures, copper tubing, pipe cutters",
    "electric": "electrician workshop with wire spools, circuit breakers, multimeters, outlet boxes, cable strippers",
    "hvac": "HVAC workshop with air conditioning units, ductwork, thermostats, refrigerant tanks, copper tubing",
    "construct": "construction office with blueprints, hard hats, tool belts, lumber samples, level, measuring tape",
    "handyman": "workshop with power drill, hammer, nails, screwdrivers, paint cans, tape measure, toolbox",
    "roofing": "roofing workshop with shingles, tar buckets, nail guns, ladders, flashing material",
    "landscap": "landscaping shed with lawnmowers, rakes, shovels, flower pots, bags of mulch, hedge trimmers",
    "storage": "storage facility hallway with rows of metal roll-up doors, padlocks, moving boxes, hand trucks",
    "funeral": "funeral parlor with ornate caskets, flower arrangements, candelabras, velvet curtains, prayer books",
    "barber": "barber shop with barber chairs, hot towels, straight razors, shaving cream, striped barber pole, mirrors",
    "cpa": "accountant office with calculator, stacks of tax forms, filing cabinets, green desk lamp, ledger books",
    "account": "accounting office with calculator, spreadsheets, filing cabinets, desk lamp, stacks of receipts",
    "tax": "tax office with calculator, tax forms, filing cabinets, computer screen with numbers, paper shredder",
    "therapy": "therapy office with comfortable couch, armchair, soft lighting, plants, tissue box, bookshelf",
    "physical therapy": "physical therapy clinic with exercise balls, resistance bands, treadmill, parallel bars, massage table",
    "chiropract": "chiropractic office with adjustment table, spine model, x-ray lightbox, anatomical posters",
    "massage": "massage room with massage table, essential oil bottles, hot stones, candles, soft towels, zen fountain",
    "acupunct": "acupuncture clinic with thin needles, treatment bed, herbal medicine jars, anatomical meridian charts",
    "photo": "photography studio with cameras on tripods, studio lights, backdrops, lens collection, printed photos",
    "tow": "tow yard with tow truck, chains, winch, jumper cables, traffic cones, safety vests",
    "pest": "pest control van interior with spray tanks, traps, nets, chemical bottles, flashlights, protective gear",
    "moving": "moving company with stacked cardboard boxes, furniture dollies, packing tape, bubble wrap, blankets",
    "tutor": "tutoring room with whiteboard, textbooks, desk with worksheets, pencils, student chairs, flash cards",
    "dance": "dance studio with wooden floor, wall mirrors, ballet barre, dance shoes, speakers, practice mats",
    "music": "music studio with guitars, drums, microphones, amplifiers, sheet music, soundproofing panels",
    "paint": "painting studio with easels, paint palettes, brushes, canvases, tubes of paint, art supplies",
    "welding": "welding shop with welding torches, metal sheets, anvil, grinding wheels, sparks, protective masks",
    "optical": "optical shop with rows of eyeglass frames on display, eye chart on wall, lens grinding equipment",
    "jewelry": "jewelry store with glass display cases of rings necklaces bracelets, velvet trays, magnifying loupe",
    "furniture": "furniture showroom with sofas, tables, lamps, rugs, bedroom sets, decorative pillows",
    "hardware": "hardware store with bins of nails and screws, power tools, paint cans, lumber, tool belts",
    "print": "print shop with printing press, stacks of paper, ink rollers, design samples on walls, cutting boards",
    "tailor": "tailor shop with sewing machines, fabric bolts, measuring tapes, dress forms, thread spools, scissors",
    "thrift": "thrift store with racks of vintage clothes, old records, used books, quirky lamps, retro furniture",
    "pawn": "pawn shop with glass cases of watches jewelry electronics, guitars on wall, vintage collectibles",
    "smoke": "smoke shop with glass pipes, hookahs, tobacco tins, rolling papers, incense, display cases",
    "wine": "wine shop with wooden racks of wine bottles, barrel decorations, cork displays, tasting glasses",
    "brew": "brewery with copper brewing tanks, beer taps, wooden barrels, pint glasses, hops and grain sacks",
    "fish": "seafood market with fish on ice, lobster tanks, shrimp, clam shells, fishing nets on walls",
    "meat": "butcher shop with hanging cuts of meat, butcher block, cleavers, sausage links, meat hooks, scale",
    "chinese": "Chinese restaurant with steaming woks, dumplings, noodle bowls, red lanterns, chopsticks, tea pots",
    "mexican": "Mexican restaurant with sizzling fajitas, tacos, salsa bowls, sombreros on wall, piñata, margarita glasses",
    "thai": "Thai restaurant with curry pots, pad thai noodles, wok flames, thai basil, coconut milk, Buddha statue",
    "indian": "Indian restaurant with tandoori oven, curry pots, naan bread, spice jars, ornate brass plates",
}


def rewrite_prompt(prompt: str, entity_type: str, category: str) -> str:
    """Build a clean interior prompt from scratch using category + extracted products."""

    # True outdoor entities — keep original prompt
    # Only skip for places that are actually outdoor (parks, cemeteries, plazas)
    # NOT businesses miscategorized as "place"
    ename = getattr(sys.modules[__name__], '_current_entity_name', '').lower()
    outdoor_keywords = ["park", "cemetery", "memorial", "common", "green", "plaza",
                        "wharf", "pier", "garden", "field", "beach", "pond"]
    is_outdoor = (
        (category or "").upper() in {"PARKS_REC"}
        or (entity_type == "place" and any(kw in ename for kw in outdoor_keywords))
    )
    if is_outdoor:
        import re
        prompt = re.sub(r'(?i)stylized halloween illustration[,.]?\s*', '', prompt)
        prompt = re.sub(r'(?i)horizontal banner composition[,.]?\s*', '', prompt)
        return prompt.strip()

    # Get the entity name
    ename = getattr(sys.modules[__name__], '_current_entity_name', '').lower()

    # 0. Fix miscategorized entities — name keywords override bad categories
    CATEGORY_CORRECTIONS = {
        # Auto-related
        "auto": "AUTO_SERVICES", "tire": "AUTO_SERVICES", "muffler": "AUTO_SERVICES",
        "collision": "AUTO_SERVICES", "autozone": "AUTO_SERVICES", "o'reilly": "AUTO_SERVICES",
        "napa": "AUTO_SERVICES", "exhaust": "AUTO_SERVICES", "mechanic": "AUTO_SERVICES",
        "garage": "AUTO_SERVICES", "motor": "AUTO_SERVICES", "transmission": "AUTO_SERVICES",
        # Pet/Vet
        "pet": "service_health", "vet": "service_health", "animal": "service_health",
        # Food — restaurants
        "pizza": "restaurant", "sushi": "restaurant", "grill": "restaurant",
        "diner": "restaurant", "bistro": "restaurant", "steakhouse": "restaurant",
        "taqueria": "restaurant", "ramen": "restaurant", "pho": "restaurant",
        "chinese": "restaurant", "mexican": "restaurant", "thai": "restaurant",
        "indian": "restaurant", "italian": "restaurant", "seafood": "restaurant",
        "burger": "restaurant", "wings": "restaurant", "bbq": "restaurant",
        "tavern": "restaurant", "dining": "restaurant", "kitchen": "restaurant",
        # Food — cafe
        "bakery": "cafe", "donut": "cafe", "bagel": "cafe", "coffee": "cafe",
        "ice cream": "cafe", "frozen yogurt": "cafe", "juice": "cafe", "smoothie": "cafe",
        # Food — bar
        "brew": "bar", "taproom": "bar", "pub": "bar", "lounge": "bar",
        "wine bar": "bar", "cocktail": "bar",
        # Beauty
        "salon": "spa_beauty", "barber": "spa_beauty", "nail": "spa_beauty",
        "spa": "spa_beauty", "hair": "spa_beauty", "cuts": "spa_beauty",
        "beauty": "spa_beauty", "wax": "spa_beauty", "lash": "spa_beauty",
        # Health
        "dental": "service_health", "chiropract": "service_health",
        "pharmacy": "service_health", "optical": "service_health",
        "therapy": "service_health", "clinic": "service_health",
        "doctor": "service_health", "medical": "service_health",
        # Retail specific
        "flower": "shop_retail", "florist": "shop_retail",
        "jewelry": "shop_retail", "furniture": "shop_retail",
        "hardware": "shop_retail", "paint": "shop_retail",
        "thrift": "shop_retail", "consignment": "shop_retail",
        "market": "shop_retail", "grocery": "shop_retail",
        "convenience": "shop_retail", "7-eleven": "shop_retail",
        "smoke": "shop_retail", "vape": "shop_retail",
        "wine": "shop_retail", "liquor": "shop_retail",
        "cheese": "shop_retail", "tackle": "shop_retail",
        "sherwin": "shop_retail",
        # Occult
        "psychic": "PSYCHIC", "tarot": "PSYCHIC", "palm read": "PSYCHIC",
        "witch": "shop_occult", "occult": "shop_occult", "craft": "shop_occult",
        # Other
        "bank": "FINANCE", "credit union": "FINANCE", "insurance": "FINANCE",
        "laundry": "shop_retail", "dry clean": "shop_retail", "cleaners": "shop_retail",
        "storage": "shop_retail",
        "tattoo": "shop_retail", "ink": "shop_retail",
        "gym": "fitness", "yoga": "fitness", "martial art": "fitness", "crossfit": "fitness",
        "church": "WORSHIP", "temple": "WORSHIP", "mosque": "WORSHIP",
        "school": "EDUCATION", "academy": "EDUCATION", "daycare": "EDUCATION", "tutor": "EDUCATION",
        "funeral": "service_professional", "mortuary": "service_professional",
        "photo": "shop_retail", "gallery": "gallery_art", "art": "gallery_art",
        "hotel": "LODGING", "inn": "LODGING", "motel": "LODGING", "hostel": "LODGING",
        "museum": "TOURISM_HISTORY", "historic": "TOURISM_HISTORY",
        "tour": "GHOST_TOUR",
        "book": "shop_bookstore", "library": "shop_bookstore",
    }
    corrected_cat = category or ""
    for name_kw, correct_cat in CATEGORY_CORRECTIONS.items():
        if name_kw in ename:
            corrected_cat = correct_cat
            break

    # 1. Find the best scene description — name match beats category match
    scene = None
    for name_kw, name_scene in NAME_SCENES.items():
        if name_kw in ename:
            scene = name_scene
            break

    if not scene:
        cat_key = (corrected_cat or "").lower()
        scene = CATEGORY_SCENES.get(cat_key,
                CATEGORY_SCENES.get(cat_key.upper(),
                "shop interior with shelves of products and merchandise"))

    # 2. Extract product nouns from the LLM prompt (the good stuff)
    products = extract_products(prompt)

    # 3. Clean vibe keywords (remove outdoor/exterior words), then combine
    vibe = getattr(sys.modules[__name__], '_current_vibe_keywords', '')
    if vibe:
        OUTDOOR_VIBES = {"foggy", "misty", "dusk", "twilight", "dawn", "sunset",
                         "cobblestone", "street-corner", "canal-street", "wharf",
                         "harbor", "autumn", "colonial", "brick", "clapboard",
                         "weathered", "industrial", "residential"}
        clean_vibes = [v.strip() for v in vibe.split(",")
                       if v.strip().lower() not in OUTDOOR_VIBES]
        if clean_vibes:
            return f"interior of {scene} filled with {', '.join(clean_vibes)}"
    return f"interior of {scene}"


_current_entity_name = ""
_current_vibe_keywords = ""


def generate_image(forge_url: str, prompt: str, entity_type: str = "",
                   category: str = "", name: str = "") -> Image.Image | None:
    """Send a prompt to Forge txt2img and return a PIL Image."""
    global _current_entity_name
    _current_entity_name = name
    prompt = rewrite_prompt(prompt, entity_type, category)
    styled_prompt = STYLE_PREFIX + prompt + STYLE_SUFFIX
    payload = {
        "prompt": styled_prompt,
        "negative_prompt": FORGE_SETTINGS["negative_prompt"],
        "width": FORGE_SETTINGS["width"],
        "height": FORGE_SETTINGS["height"],
        "steps": FORGE_SETTINGS["steps"],
        "cfg_scale": FORGE_SETTINGS["cfg_scale"],
        "sampler_name": FORGE_SETTINGS["sampler_name"],
        "batch_size": 1,
        "n_iter": 1,
        "save_images": False,
        "send_images": True,
    }

    try:
        resp = requests.post(
            f"{forge_url}/sdapi/v1/txt2img",
            json=payload,
            timeout=120,
        )
        resp.raise_for_status()
        data = resp.json()
        img_b64 = data["images"][0]
        img_bytes = base64.b64decode(img_b64)
        return Image.open(io.BytesIO(img_bytes))
    except requests.exceptions.ConnectionError:
        print("ERROR: Cannot connect to Forge. Is it running?", file=sys.stderr)
        return None
    except Exception as e:
        print(f"ERROR generating image: {e}", file=sys.stderr)
        return None


def save_images(img: Image.Image, entity_id: str) -> tuple[Path, Path]:
    """Save full-res and app-res WebP images."""
    full_path = OUTPUT_FULL / f"{entity_id}.webp"
    app_path = OUTPUT_APP / f"{entity_id}.webp"

    img.save(full_path, "WEBP", quality=85)

    app_img = img.resize((APP_WIDTH, APP_HEIGHT), Image.LANCZOS)
    app_img.save(app_path, "WEBP", quality=80)

    return full_path, app_path


class QCHandler(SimpleHTTPRequestHandler):
    """HTTP handler that serves the QC viewer, status JSON, and generated images."""

    def do_GET(self):
        # Strip query string for routing
        path = self.path.split("?")[0]

        if path == "/" or path == "/index.html":
            self.send_response(200)
            self.send_header("Content-Type", "text/html")
            self.end_headers()
            self.wfile.write(QC_HTML_PATH.read_bytes())
        elif path == "/status.json":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            if STATUS_PATH.exists():
                self.wfile.write(STATUS_PATH.read_bytes())
            else:
                self.wfile.write(b'{}')
        elif path.startswith("/images/"):
            filename = path[len("/images/"):]
            img_path = OUTPUT_FULL / filename
            if img_path.exists():
                self.send_response(200)
                self.send_header("Content-Type", "image/webp")
                self.send_header("Cache-Control", "no-cache")
                self.end_headers()
                self.wfile.write(img_path.read_bytes())
            else:
                self.send_error(404)
        else:
            self.send_error(404)

    def log_message(self, format, *args):
        pass  # Suppress HTTP logs


def start_qc_server(port: int):
    server = HTTPServer(("0.0.0.0", port), QCHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server


def main():
    parser = argparse.ArgumentParser(description="Batch hero image generator")
    parser.add_argument("--resume", action="store_true", help="Skip already-generated images")
    parser.add_argument("--port", type=int, default=4400, help="QC viewer port")
    parser.add_argument("--limit", type=int, default=None, help="Generate only first N images")
    parser.add_argument("--forge", default="http://localhost:7860", help="Forge API URL")
    args = parser.parse_args()

    OUTPUT_FULL.mkdir(parents=True, exist_ok=True)
    OUTPUT_APP.mkdir(parents=True, exist_ok=True)

    manifest = load_manifest()
    print(f"Loaded {len(manifest)} hero prompts from manifest")

    if args.resume:
        existing = {p.stem for p in OUTPUT_FULL.glob("*.webp")}
        before = len(manifest)
        manifest = [e for e in manifest if e["entity_id"] not in existing]
        print(f"Resume mode: {before - len(manifest)} already done, {len(manifest)} remaining")

    if args.limit:
        manifest = manifest[:args.limit]
        print(f"Limit mode: processing first {len(manifest)} entries")

    # Check Forge is up
    try:
        r = requests.get(f"{args.forge}/sdapi/v1/sd-models", timeout=5)
        r.raise_for_status()
        models = r.json()
        current = [m for m in models if "DreamShaper" in m.get("title", "")]
        if current:
            print(f"Forge connected. Model: {current[0]['title']}")
        else:
            print(f"Forge connected. {len(models)} model(s) available.")
    except Exception as e:
        print(f"ERROR: Cannot reach Forge at {args.forge}: {e}", file=sys.stderr)
        print("Start Forge first: ~/AI-Studio/forge-start.sh", file=sys.stderr)
        sys.exit(1)

    # Start QC viewer
    start_qc_server(args.port)
    print(f"QC viewer: http://localhost:{args.port}")
    print(f"{'='*60}")

    total = len(manifest)
    generated = 0
    failures = 0
    start_time = time.time()
    recent_images = []

    for i, entry in enumerate(manifest):
        entity_id = entry["entity_id"]
        name = entry["name"] or "Unknown"
        category = entry.get("category") or entry.get("entity_type") or "unknown"
        prompt = entry["hero_prompt"]
        entity_type = entry.get("entity_type", "")

        # Set entity context for the rewriter, then compute the prompt
        global _current_entity_name
        _current_entity_name = name
        sys.modules[__name__]._current_vibe_keywords = entry.get("vibe_keywords", "")
        rewritten = rewrite_prompt(prompt, entity_type, category)
        full_prompt = STYLE_PREFIX + rewritten + STYLE_SUFFIX

        print(f"[{i+1}/{total}] {name} ({category})...", end=" ", flush=True)

        img = generate_image(args.forge, prompt,
                             entity_type=entry.get("entity_type", ""),
                             category=category, name=name)
        if img is None:
            failures += 1
            print("FAILED")
            continue

        full_path, app_path = save_images(img, entity_id)
        generated += 1

        full_kb = os.path.getsize(full_path) / 1024
        app_kb = os.path.getsize(app_path) / 1024

        recent_images.append({
            "entity_id": entity_id,
            "name": name,
            "category": category,
            "entity_type": entity_type,
            "prompt": full_prompt,
            "vibe_keywords": entry.get("vibe_keywords", ""),
            "filename": f"{entity_id}.webp",
            "full_kb": round(full_kb, 1),
            "app_kb": round(app_kb, 1),
        })

        # Update status AFTER image is generated — so name matches image in QC viewer
        elapsed = time.time() - start_time
        rate = generated / elapsed if elapsed > 0 and generated > 0 else 0
        eta_sec = (total - i) / rate if rate > 0 else 0
        eta_min = eta_sec / 60

        update_status({
            "current": {
                "index": i + 1,
                "entity_id": entity_id,
                "name": name,
                "category": category,
                "prompt": full_prompt,
                "vibe_keywords": entry.get("vibe_keywords"),
            },
            "progress": {
                "total": total,
                "generated": generated,
                "failures": failures,
                "percent": round((generated / total) * 100, 1) if total else 0,
                "rate_per_min": round(rate * 60, 1),
                "eta_minutes": round(eta_min, 1),
                "elapsed_minutes": round(elapsed / 60, 1),
            },
            "recent": recent_images[-20:],
            "started_at": datetime.fromtimestamp(start_time).isoformat(),
        })

        print(f"OK ({full_kb:.0f}KB full, {app_kb:.0f}KB app)")

    # Final status
    elapsed = time.time() - start_time
    final_status = {
        "current": None,
        "progress": {
            "total": total,
            "generated": generated,
            "failures": failures,
            "percent": 100.0,
            "rate_per_min": round(generated / (elapsed / 60), 1) if elapsed > 0 else 0,
            "eta_minutes": 0,
            "elapsed_minutes": round(elapsed / 60, 1),
        },
        "recent": recent_images[-20:],
        "complete": True,
        "started_at": datetime.fromtimestamp(start_time).isoformat(),
        "finished_at": datetime.now().isoformat(),
    }
    update_status(final_status)

    # Calculate totals
    full_total_mb = sum(os.path.getsize(f) for f in OUTPUT_FULL.glob("*.webp")) / (1024 * 1024)
    app_total_mb = sum(os.path.getsize(f) for f in OUTPUT_APP.glob("*.webp")) / (1024 * 1024)

    print(f"\n{'='*60}")
    print(f"HERO IMAGE GENERATION COMPLETE")
    print(f"{'='*60}")
    print(f"  Generated: {generated}/{total}")
    print(f"  Failures:  {failures}")
    print(f"  Time:      {elapsed/60:.1f} minutes")
    print(f"  Full-res:  {full_total_mb:.1f} MB ({OUTPUT_FULL})")
    print(f"  App-res:   {app_total_mb:.1f} MB ({OUTPUT_APP})")
    print(f"\nQC viewer still running at http://localhost:{args.port}")
    print("Press Ctrl+C to exit.")

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\nDone.")


if __name__ == "__main__":
    main()
