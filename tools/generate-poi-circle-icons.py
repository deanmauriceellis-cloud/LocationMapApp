#!/usr/bin/env python3
"""
Generate small circular POI map icons using Stable Diffusion (Forge API).

Each POI subtype gets a unique icon: a centered object/symbol rendered as a
small round button suitable for map markers (~48dp on screen).

Outputs 512x512 PNGs (will be scaled to 48dp at runtime).
Uses DreamShaper XL Turbo for fast generation.

Usage:
    python3 generate-poi-circle-icons.py [--output-dir DIR] [--category CATEGORY]
"""

import json
import os
import sys
import time
import base64
import argparse
from io import BytesIO

try:
    import requests
    from PIL import Image, ImageDraw
except ImportError:
    print("pip install requests Pillow")
    sys.exit(1)

FORGE_URL = "http://localhost:7862"
OUTPUT_DIR = os.path.expanduser(
    "~/Development/LocationMapApp_v1.5/app-salem/src/main/assets/poi-circle-icons"
)

# Base prompt template for circular icon generation
# Every icon carries the Salem witch tour aesthetic: gothic, spooky, purple/green palette
ICON_PROMPT_PREFIX = (
    "mobile app icon of {desc}, single centered object, circular shape, "
    "dark gothic witchcraft aesthetic, Salem witch trials theme, "
    "deep purple and black background with green magical glow, "
    "spooky Halloween atmosphere, occult mystical feeling, "
    "glowing eldritch edges, ornate dark fantasy style, "
    "witch app game icon, highly detailed miniature, no text, masterpiece"
)
NEGATIVE_PROMPT = (
    "text, words, letters, numbers, watermark, signature, blurry, "
    "photograph, photorealistic, multiple objects, busy cluttered background, "
    "cropped, cut off, deformed, ugly, low quality, abstract, minimalist, "
    "bright cheerful colors, pastel, cute kawaii, flat color, white background"
)

# ─── POI Subtypes with icon descriptions ─────────────────────────────────
# Format: (category_dir, filename, icon_description)

ICON_DEFS = [
    # ── Food & Drink ──
    ("food_drink", "restaurant", "plate with fork and knife, steaming food"),
    ("food_drink", "fast_food", "hamburger and french fries"),
    ("food_drink", "cafe", "coffee cup with steam, latte art"),
    ("food_drink", "bar", "cocktail glass with cherry"),
    ("food_drink", "pub", "beer mug with foam"),
    ("food_drink", "ice_cream", "ice cream cone with two scoops"),
    ("food_drink", "bakery", "fresh baked bread loaf"),
    ("food_drink", "pastry", "cupcake with frosting"),
    ("food_drink", "candy", "wrapped candy and lollipop"),
    ("food_drink", "liquor", "wine bottle and glass"),
    ("food_drink", "wine", "wine glass with red wine"),
    ("food_drink", "deli", "sandwich with layers"),
    ("food_drink", "butcher", "meat cleaver and steak"),
    ("food_drink", "seafood", "fish and shrimp"),
    ("food_drink", "marketplace", "market stall with produce"),
    ("food_drink", "brewery", "beer barrel and hops"),
    ("food_drink", "winery", "grape vine and wine barrel"),
    ("food_drink", "distillery", "copper still and bottle"),

    # ── Fuel & Charging ──
    ("fuel_charging", "gas_station", "gas pump nozzle"),
    ("fuel_charging", "charging_station", "electric plug with lightning bolt"),

    # ── Transit ──
    ("transit", "train_station", "train locomotive front view"),
    ("transit", "bus_station", "city bus front view"),
    ("transit", "airport", "airplane taking off"),
    ("transit", "bike_rental", "bicycle"),
    ("transit", "ferry", "ferry boat on water"),
    ("transit", "taxi", "taxi cab with checkered stripe"),

    # ── Civic & Gov ──
    ("civic", "town_hall", "government building with columns and dome"),
    ("civic", "courthouse", "scales of justice"),
    ("civic", "post_office", "mailbox and envelope"),
    ("civic", "gov_office", "eagle seal government emblem"),
    ("civic", "community_centre", "people gathering in circle"),
    ("civic", "recycling", "green recycling arrows symbol"),

    # ── Parks & Rec ──
    ("parks_rec", "park", "green tree in a park"),
    ("parks_rec", "nature_reserve", "forest with wildlife deer"),
    ("parks_rec", "playground", "swing set and slide"),
    ("parks_rec", "sports_field", "soccer ball on grass"),
    ("parks_rec", "pool", "swimming pool with diving board"),
    ("parks_rec", "dog_park", "happy dog silhouette"),
    ("parks_rec", "garden", "flowering garden with roses"),
    ("parks_rec", "boat_ramp", "boat on water ramp"),
    ("parks_rec", "fountain", "ornamental water fountain"),
    ("parks_rec", "restroom", "restroom sign symbols"),
    ("parks_rec", "beach", "beach umbrella and waves"),

    # ── Shopping ──
    ("shopping", "supermarket", "shopping cart full of groceries"),
    ("shopping", "convenience", "small shop storefront"),
    ("shopping", "mall", "shopping mall building"),
    ("shopping", "clothing", "dress on hanger"),
    ("shopping", "shoes", "pair of shoes"),
    ("shopping", "jewelry", "diamond ring sparkling"),
    ("shopping", "hair_salon", "scissors and comb"),
    ("shopping", "barber", "barber pole red white blue"),
    ("shopping", "beauty_spa", "lotus flower and candle"),
    ("shopping", "tattoo", "tattoo machine and ink"),
    ("shopping", "bookstore", "stack of books"),
    ("shopping", "gift_shop", "wrapped gift box with bow"),
    ("shopping", "florist", "bouquet of flowers"),
    ("shopping", "hardware", "hammer and wrench tools"),
    ("shopping", "phone_store", "smartphone device"),
    ("shopping", "pet_store", "paw print"),
    ("shopping", "electronics", "circuit board and chip"),
    ("shopping", "bicycle_shop", "bicycle wheel and gear"),
    ("shopping", "thrift", "recycled clothing hanger with heart"),
    ("shopping", "cannabis", "cannabis leaf"),

    # ── Healthcare ──
    ("healthcare", "hospital", "red cross medical symbol"),
    ("healthcare", "pharmacy", "pharmacy mortar and pestle"),
    ("healthcare", "clinic", "stethoscope"),
    ("healthcare", "dentist", "tooth with sparkle"),
    ("healthcare", "doctor", "medical caduceus symbol"),
    ("healthcare", "veterinary", "paw print with heart"),
    ("healthcare", "nursing_home", "elderly person with care heart"),

    # ── Education ──
    ("education", "school", "school building with bell"),
    ("education", "library", "open book with bookmark"),
    ("education", "college", "graduation cap"),
    ("education", "university", "university building with columns"),
    ("education", "childcare", "baby rattle and blocks"),

    # ── Lodging ──
    ("lodging", "hotel", "hotel building with star"),
    ("lodging", "motel", "motel neon sign"),
    ("lodging", "hostel", "bunk beds"),
    ("lodging", "campground", "tent under stars"),
    ("lodging", "guest_house", "cozy house with welcome mat"),
    ("lodging", "rv_park", "recreational vehicle camper"),

    # ── Parking ──
    ("parking", "parking", "letter P parking sign"),

    # ── Finance ──
    ("finance", "bank", "bank building with dollar sign"),
    ("finance", "atm", "ATM machine"),

    # ── Worship ──
    ("worship", "place_of_worship", "church steeple with cross"),

    # ── Tourism & History ──
    ("tourism_history", "museum", "classical museum building with pillars"),
    ("tourism_history", "attraction", "star burst attraction marker"),
    ("tourism_history", "viewpoint", "binoculars overlooking landscape"),
    ("tourism_history", "memorial", "stone memorial wreath"),
    ("tourism_history", "monument", "tall stone monument obelisk"),
    ("tourism_history", "public_art", "sculpture on pedestal"),
    ("tourism_history", "gallery", "framed painting on wall"),
    ("tourism_history", "info_point", "information letter i in circle"),
    ("tourism_history", "cemetery", "tombstone with cross"),
    ("tourism_history", "historic_building", "old colonial building"),
    ("tourism_history", "ruins", "ancient stone ruins"),
    ("tourism_history", "maritime", "ship anchor and rope"),
    ("tourism_history", "zoo", "elephant and giraffe"),
    ("tourism_history", "aquarium", "tropical fish in tank"),
    ("tourism_history", "theme_park", "roller coaster"),

    # ── Emergency ──
    ("emergency", "police", "police badge and shield"),
    ("emergency", "fire_station", "fire helmet and axe"),

    # ── Auto Services ──
    ("auto_services", "repair_shop", "car with wrench"),
    ("auto_services", "car_wash", "car with water spray"),
    ("auto_services", "car_rental", "car key with tag"),
    ("auto_services", "tire_shop", "tire and wheel"),
    ("auto_services", "dealership", "car with price tag"),

    # ── Entertainment ──
    ("entertainment", "fitness", "dumbbell weights"),
    ("entertainment", "sports_centre", "basketball and hoop"),
    ("entertainment", "golf", "golf ball on tee"),
    ("entertainment", "marina", "sailboat on water"),
    ("entertainment", "stadium", "stadium with floodlights"),
    ("entertainment", "theatre", "comedy tragedy masks"),
    ("entertainment", "cinema", "movie film reel and popcorn"),
    ("entertainment", "nightclub", "disco ball with lights"),
    ("entertainment", "event_venue", "stage with spotlight"),
    ("entertainment", "arts_centre", "paint palette and brush"),
    ("entertainment", "arcade", "retro arcade joystick"),
    ("entertainment", "ice_rink", "ice skate"),
    ("entertainment", "bowling", "bowling pin and ball"),
    ("entertainment", "mini_golf", "mini golf windmill"),
    ("entertainment", "escape_room", "locked padlock with key"),

    # ── Offices ──
    ("offices", "company", "office building"),
    ("offices", "real_estate", "house with for sale sign"),
    ("offices", "law_office", "gavel and law book"),
    ("offices", "insurance", "shield with checkmark"),
    ("offices", "tax_advisor", "calculator and money"),

    # ══ Salem-Specific ══

    # ── Witch & Occult Shops ──
    ("witch_shop", "witchcraft_shop", "bubbling cauldron with green smoke"),
    ("witch_shop", "occult_supplies", "pentagram and candles"),
    ("witch_shop", "metaphysical", "third eye and aura"),
    ("witch_shop", "crystal_shop", "glowing crystal cluster amethyst"),
    ("witch_shop", "herb_shop", "mortar and pestle with herbs and potion bottles"),

    # ── Psychic & Tarot ──
    ("psychic", "tarot", "tarot card with moon and star"),
    ("psychic", "psychic_reading", "crystal ball with swirling mist"),
    ("psychic", "palm_reading", "open palm with glowing lines"),
    ("psychic", "seance", "flickering candles in dark circle"),
    ("psychic", "spiritual_healer", "hands with healing light aura"),

    # ── Ghost Tours ──
    ("ghost_tour", "walking_tour", "ghostly lantern floating in fog"),
    ("ghost_tour", "haunted_tour", "ghost figure in old doorway"),
    ("ghost_tour", "night_tour", "full moon with bats"),
    ("ghost_tour", "historical_tour", "old scroll map with skull"),

    # ── Haunted Attractions ──
    ("haunted_attraction", "haunted_house", "spooky haunted mansion"),
    ("haunted_attraction", "scare_attraction", "screaming face in darkness"),
    ("haunted_attraction", "wax_museum", "wax figure candle melting"),
    ("haunted_attraction", "escape_horror", "bloody handprint on glass"),

    # ── Historic Houses ──
    ("historic_house", "colonial_house", "colonial era wooden house 1600s"),
    ("historic_house", "witch_trial_house", "dark puritan house with accusation scroll"),
    ("historic_house", "maritime_house", "sea captain mansion with ship weathervane"),
    ("historic_house", "literary_house", "house with quill pen and inkwell"),
    ("historic_house", "museum_house", "stately house with museum plaque"),
]


def generate_icon(prompt_desc: str, output_path: str) -> bool:
    """Generate a single circular icon via Forge txt2img API."""
    full_prompt = ICON_PROMPT_PREFIX.format(desc=prompt_desc)

    payload = {
        "data": [
            0,          # task_id
            full_prompt,
            NEGATIVE_PROMPT,
            [],         # prompt styles
            20,         # steps
            "DPM++ 2M SDE",  # sampler
            1,          # batch count
            1,          # batch size
            7.0,        # CFG scale
            -1,         # seed (-1 = random)
            512,        # width
            512,        # height
        ]
    }

    # Use the simpler approach — direct API
    try:
        # Forge A1111-compatible API (might work if --api flag was used)
        api_payload = {
            "prompt": full_prompt,
            "negative_prompt": NEGATIVE_PROMPT,
            "steps": 20,
            "sampler_name": "DPM++ 2M SDE",
            "cfg_scale": 7.0,
            "width": 512,
            "height": 512,
            "seed": -1,
            "batch_size": 1,
            "n_iter": 1,
        }
        resp = requests.post(f"{FORGE_URL}/sdapi/v1/txt2img", json=api_payload, timeout=120)
        if resp.status_code == 200:
            data = resp.json()
            img_b64 = data["images"][0]
            img = Image.open(BytesIO(base64.b64decode(img_b64)))
            # Apply circular mask
            img = apply_circle_mask(img)
            os.makedirs(os.path.dirname(output_path), exist_ok=True)
            img.save(output_path, "PNG")
            return True
        else:
            print(f"  API error {resp.status_code}: {resp.text[:200]}")
            return False
    except Exception as e:
        print(f"  Error: {e}")
        return False


def apply_circle_mask(img: Image.Image) -> Image.Image:
    """Crop image to a circle with transparent background."""
    size = img.size[0]
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((4, 4, size - 4, size - 4), fill=255)
    result = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    result.paste(img.convert("RGBA"), (0, 0), mask)

    # Draw thin border ring
    overlay = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)
    draw.ellipse((2, 2, size - 2, size - 2), outline=(255, 255, 255, 180), width=3)
    result = Image.alpha_composite(result, overlay)

    return result


def main():
    parser = argparse.ArgumentParser(description="Generate POI circle icons")
    parser.add_argument("--output-dir", default=OUTPUT_DIR)
    parser.add_argument("--category", help="Only generate for this category dir")
    parser.add_argument("--dry-run", action="store_true", help="Just print prompts")
    args = parser.parse_args()

    # Filter if category specified
    defs = ICON_DEFS
    if args.category:
        defs = [d for d in defs if d[0] == args.category]
        if not defs:
            print(f"No icons defined for category: {args.category}")
            sys.exit(1)

    print(f"Generating {len(defs)} circular POI icons")
    print(f"Output: {args.output_dir}")
    print(f"Model: DreamShaper XL Turbo (512x512)")
    print()

    # Check Forge is reachable
    if not args.dry_run:
        try:
            r = requests.get(f"{FORGE_URL}/info", timeout=5)
            print("Forge connected OK")
        except Exception as e:
            print(f"Cannot reach Forge at {FORGE_URL}: {e}")
            sys.exit(1)

    success = 0
    failed = 0
    for cat_dir, filename, desc in defs:
        output_path = os.path.join(args.output_dir, cat_dir, f"{filename}.png")

        if os.path.exists(output_path):
            print(f"  SKIP (exists): {cat_dir}/{filename}.png")
            success += 1
            continue

        if args.dry_run:
            print(f"  {cat_dir}/{filename}.png — \"{desc}\"")
            continue

        print(f"  Generating: {cat_dir}/{filename}.png — \"{desc}\"")
        if generate_icon(desc, output_path):
            success += 1
            print(f"    OK")
        else:
            failed += 1
            print(f"    FAILED")

    print(f"\nDone: {success} success, {failed} failed, {len(defs)} total")


if __name__ == "__main__":
    main()
