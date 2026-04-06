#!/usr/bin/env python3
"""
Merge and deduplicate three Salem POI data sources into a unified dataset.

Sources:
  1. destination-salem.json   — 284 business listings from salem.org
  2. haunted-happenings-venues.json — 162 venues from hauntedhappenings.org
  3. osm-salem-pois.json      — 1,945 POIs from OpenStreetMap

Output:
  merged-salem-pois.json — Deduplicated, categorized, filtered POI dataset.

Usage:
  python3 merge_pois.py
"""

import json
import math
import re
import os
import html
from collections import Counter, defaultdict
from typing import Optional

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))

# Input files
DEST_SALEM_FILE = os.path.join(SCRIPT_DIR, "destination-salem.json")
HAUNTED_HAPPENINGS_FILE = os.path.join(SCRIPT_DIR, "haunted-happenings-venues.json")
OSM_FILE = os.path.join(SCRIPT_DIR, "osm-salem-pois.json")

# Output file
OUTPUT_FILE = os.path.join(SCRIPT_DIR, "merged-salem-pois.json")

# Salem bounding box
SALEM_LAT_MIN = 42.50
SALEM_LAT_MAX = 42.55
SALEM_LNG_MIN = -70.93
SALEM_LNG_MAX = -70.85

# Deduplication threshold in meters
DEDUP_DISTANCE_M = 50

# Infrastructure OSM amenity types to filter out (but keep parking lots)
INFRASTRUCTURE_FILTER = {
    "parking_space", "bench", "waste_basket", "bicycle_parking",
    "post_box", "shelter", "recycling", "drinking_water",
    "vending_machine", "telephone", "clock", "letter_box",
    "fire_hydrant", "atm",
}

# Leisure types to filter
INFRASTRUCTURE_LEISURE = {
    "picnic_table", "outdoor_seating", "swimming_pool",
}

# ─── Destination Salem sub-category mapping ───
# Parent categories: 4=Things to Do, 10=Eat & Drink, 15=Shopping, 20=Stay
# Sub-categories determined by inspecting sample listings:
DS_SUBCAT_MAP = {
    5:   ("attraction", None),            # Attractions / Activities
    6:   ("tour", None),                  # Tours (ghost, walking, etc.)
    7:   ("psychic", None),               # Psychic / Spiritual services
    8:   ("historic_site", None),         # Historic sites & landmarks
    11:  ("restaurant", "dessert"),        # Desserts / Ice cream / Sweets
    12:  ("cafe", None),                  # Coffee / Tea
    13:  ("restaurant", None),            # Restaurants / Bars
    14:  ("brewery", None),               # Breweries
    16:  ("shopping", None),              # General retail
    17:  ("shopping", "gothic"),           # Gothic / Specialty shops
    18:  ("witch_shop", None),            # Witch / Occult shops
    21:  ("hotel", None),                 # Hotels
    23:  ("lodging", "bed_and_breakfast"), # B&Bs / Inns
    24:  ("lodging", "campground"),        # Campgrounds
    25:  ("hotel", "nearby"),             # Nearby hotels
    250: ("tour", "boat"),                # Boat tours / Water activities
    733: ("cannabis_dispensary", None),    # Cannabis dispensaries
    741: ("event_venue", None),           # Events / Venues
}

# Parent category fallback (for listings with no sub_categories)
DS_PARENT_MAP = {
    "4":  ("attraction", None),
    "10": ("restaurant", None),
    "15": ("shopping", None),
    "20": ("hotel", None),
}

# ─── OSM tag to category mapping ───
OSM_AMENITY_MAP = {
    "restaurant": ("restaurant", None),
    "cafe": ("cafe", None),
    "fast_food": ("restaurant", "fast_food"),
    "bar": ("bar", None),
    "pub": ("bar", "pub"),
    "ice_cream": ("restaurant", "dessert"),
    "pharmacy": ("pharmacy", None),
    "hospital": ("hospital", None),
    "doctors": ("medical", "doctor"),
    "dentist": ("medical", "dentist"),
    "clinic": ("medical", "clinic"),
    "bank": ("bank", None),
    "library": ("library", None),
    "school": ("school", None),
    "university": ("university", None),
    "place_of_worship": ("place_of_worship", None),
    "post_office": ("post_office", None),
    "cinema": ("entertainment", "cinema"),
    "theatre": ("entertainment", "theatre"),
    "police": ("government", "police"),
    "fire_station": ("government", "fire_station"),
    "townhall": ("government", "city_hall"),
    "courthouse": ("government", "courthouse"),
    "community_centre": ("community_center", None),
    "social_centre": ("community_center", None),
    "social_facility": ("community_center", None),
    "childcare": ("school", "childcare"),
    "parking": ("parking", None),
    "fuel": ("gas_station", None),
    "ferry_terminal": ("transportation", "ferry"),
    "marketplace": ("shopping", "market"),
    "money_transfer": ("financial_services", None),
}

OSM_SHOP_MAP = {
    "gift": ("shopping", "gift"),
    "convenience": ("shopping", "convenience"),
    "clothes": ("shopping", "clothing"),
    "books": ("shopping", "books"),
    "jewelry": ("shopping", "jewelry"),
    "supermarket": ("shopping", "grocery"),
    "bakery": ("restaurant", "bakery"),
    "confectionery": ("restaurant", "dessert"),
    "alcohol": ("shopping", "liquor"),
    "tattoo": ("services", "tattoo"),
    "hairdresser": ("services", "hair_salon"),
    "beauty": ("services", "beauty"),
    "car_repair": ("services", "auto_repair"),
    "car": ("services", "auto_dealer"),
    "car_parts": ("services", "auto_parts"),
    "massage": ("services", "massage"),
    "laundry": ("services", "laundry"),
    "dry_cleaning": ("services", "dry_cleaning"),
    "mobile_phone": ("shopping", "electronics"),
    "electronics": ("shopping", "electronics"),
    "cannabis": ("cannabis_dispensary", None),
    "esoteric": ("witch_shop", None),
    "occult_goods": ("witch_shop", None),
    "psychic": ("psychic", None),
    "florist": ("shopping", "florist"),
    "art": ("shopping", "art"),
    "craft": ("shopping", "crafts"),
    "tea": ("cafe", "tea"),
    "cheese": ("shopping", "specialty_food"),
    "seafood": ("shopping", "specialty_food"),
    "chocolate": ("shopping", "specialty_food"),
    "pastry": ("restaurant", "bakery"),
    "herbalist": ("witch_shop", None),
    "mall": ("shopping", "mall"),
    "department_store": ("shopping", "department_store"),
    "second_hand": ("shopping", "thrift"),
    "variety_store": ("shopping", "variety"),
    "pet": ("shopping", "pet"),
    "pet_grooming": ("services", "pet"),
    "furniture": ("shopping", "furniture"),
    "optician": ("services", "optician"),
    "charity": ("shopping", "charity"),
    "copyshop": ("services", "printing"),
    "bicycle": ("shopping", "bicycle"),
    "paint": ("shopping", "hardware"),
    "doityourself": ("shopping", "hardware"),
    "funeral_directors": ("services", "funeral"),
    "ticket": ("services", "tickets"),
    "tobacco": ("shopping", "tobacco"),
    "video_games": ("shopping", "games"),
    "travel_agency": ("services", "travel"),
    "musical_instrument": ("shopping", "music"),
    "music": ("shopping", "music"),
    "sports": ("shopping", "sports"),
    "e-cigarette": ("shopping", "tobacco"),
    "pawnbroker": ("shopping", "pawn"),
    "model": ("shopping", "hobby"),
    "tailor": ("services", "tailor"),
    "repair": ("services", "repair"),
    "carpet": ("shopping", "home"),
    "doors": ("services", "home_improvement"),
    "fabric": ("shopping", "crafts"),
    "fashion_accessories": ("shopping", "accessories"),
    "fishing": ("shopping", "outdoor"),
    "storage_rental": ("services", "storage"),
    "vacuum_cleaner": ("services", "appliance"),
    "motorcycle_repair": ("services", "auto_repair"),
}

OSM_TOURISM_MAP = {
    "museum": ("museum", None),
    "hotel": ("hotel", None),
    "guest_house": ("lodging", "guest_house"),
    "attraction": ("attraction", None),
    "artwork": ("public_art", None),
    "gallery": ("gallery", None),
    "information": ("visitor_info", None),
    "picnic_site": ("park", "picnic"),
    "caravan_site": ("lodging", "campground"),
}

OSM_HISTORIC_MAP = {
    "memorial": ("historic_site", "memorial"),
    "monument": ("historic_site", "monument"),
    "cemetery": ("cemetery", None),
    "maritime": ("historic_site", "maritime"),
    "manor": ("historic_site", "manor"),
    "ship": ("historic_site", "ship"),
    "district": ("historic_site", "district"),
    "yes": ("historic_site", None),
}

OSM_LEISURE_MAP = {
    "park": ("park", None),
    "garden": ("park", "garden"),
    "playground": ("park", "playground"),
    "nature_reserve": ("park", "nature"),
    "pitch": ("recreation", "sports_field"),
    "sports_centre": ("recreation", "sports_center"),
    "fitness_centre": ("recreation", "fitness"),
    "golf_course": ("recreation", "golf"),
    "slipway": ("recreation", "marina"),
    "marina": ("recreation", "marina"),
}

OSM_OFFICE_MAP = {
    "lawyer": ("services", "legal"),
    "financial": ("financial_services", None),
    "association": ("organization", None),
    "guide": ("tour", None),
    "harbour_master": ("government", "harbor"),
    "yes": ("office", None),
}

# ─── Salem-specific keyword matching for category refinement ───
# These keywords in POI names trigger reclassification to Salem-specific categories

WITCH_SHOP_KEYWORDS = [
    "witchery", "witchcraft", "hex ", "hex:", "occult",
    "cauldron", "botanica", "wicca", "pagan", "spell", "crow haven",
    "broomstick", "broom co", "magick", "magika", "enchant",
    "apothecary", "haus witch", "hauswich", "hauswitch",
    "sea wych", "black veil", "ossuary",
    "witch village", "witch & fairy", "witch way",
    "good witch", "witch pix", "witch dr",
    "lost library",  # at the Witch Village
]

PSYCHIC_KEYWORDS = [
    "psychic", "tarot", "palm read", "fortune tell", "clairvoyant",
    "medium", "divination", "aura photo", "aura read", "crystal ball",
    "spiritual read", "celestial navigation", "angelique renard",
    "seance", "séance", "past life regression",
]

GHOST_TOUR_KEYWORDS = [
    "ghost tour", "ghost walk", "haunted tour", "haunted walk",
    "witch walk", "witch tour", "walking tour", "witch city walk",
    "specter", "apparition", "vampire", "paranormal",
    "ghost hunt", "ghostly walk", "dark tour",
]

HAUNTED_ATTRACTION_KEYWORDS = [
    "haunted house", "haunted mansion", "haunt attraction",
    "blackcraft haunt", "witch mansion haunted", "gallows hill museum",
    "haunted witch village", "haunted village",
    "chambers of terror", "fright", "nightmare gallery",
]

WITCH_MUSEUM_KEYWORDS = [
    "witch museum", "witch dungeon", "witch history",
    "witch house", "witch trial", "salem witch museum",
    "witch board museum", "1692",
]


def clean_html(text: str) -> str:
    """Strip HTML tags and decode entities."""
    if not text:
        return ""
    text = html.unescape(text)
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text


def haversine_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """Return distance in meters between two lat/lng points."""
    R = 6_371_000  # Earth radius in meters
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlam = math.radians(lng2 - lng1)
    a = math.sin(dphi / 2) ** 2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlam / 2) ** 2
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


def normalize_name(name: str) -> str:
    """Normalize a name for fuzzy matching."""
    s = name.lower().strip()
    s = html.unescape(s)
    s = re.sub(r"<[^>]+>", "", s)
    # Common entity artifacts
    s = s.replace("&#038;", "&").replace("&#8211;", "-").replace("&#8217;", "'")
    # Normalize special characters to ASCII equivalents
    s = s.replace("æ", "ae").replace("ö", "o").replace("ü", "u").replace("é", "e")
    # Replace punctuation that acts as word separators with spaces
    s = re.sub(r"[/|:–—\-]", " ", s)
    # Normalize quotes
    s = re.sub(r"['`\"\u2018\u2019\u201c\u201d]", "", s)
    # Strip common prefixes
    s = re.sub(r"^the\s+", "", s)
    # Remove all non-alphanumeric except spaces
    s = re.sub(r"[^a-z0-9 ]", " ", s).strip()
    # Collapse whitespace
    s = re.sub(r"\s+", " ", s)
    return s


def in_salem(lat: Optional[float], lng: Optional[float]) -> bool:
    """Check if coordinates fall within Salem proper."""
    if lat is None or lng is None:
        return False
    return SALEM_LAT_MIN <= lat <= SALEM_LAT_MAX and SALEM_LNG_MIN <= lng <= SALEM_LNG_MAX


def classify_salem_specific(name: str, current_cat: str, current_subcat: Optional[str]) -> tuple:
    """
    Reclassify a POI to a Salem-specific category if its name matches keywords.
    Returns (category, subcategory) — may return originals if no match.

    Uses a conservative approach: "witch city X" where X is a common business type
    (mall, hibachi, auto, ink, comedy, consignment, wicks) is NOT reclassified.
    Only places that are genuinely about witchcraft/occult/Salem witch history qualify.
    """
    name_lower = name.lower()

    # Exclusion patterns: businesses that happen to have "witch" in the name
    # but are not actually witch/occult themed
    NON_WITCH_PATTERNS = [
        "witch city hibachi", "witch city mall", "witch city auto",
        "witch city ink", "witch city comedy", "witch city consignment",
        "witch city wicks", "witch city tattoo", "witch city pizza",
        "witch city sports", "witch city fitness", "witch city laundry",
        "witchcraft heights",  # neighborhood name, not witchcraft
        "witchside tavern",    # restaurant
    ]
    for excl in NON_WITCH_PATTERNS:
        if excl in name_lower:
            return (current_cat, current_subcat)

    # Check witch museum first (more specific than witch_shop)
    for kw in WITCH_MUSEUM_KEYWORDS:
        if kw in name_lower:
            # Only reclassify if it's a museum/attraction/tour, not a random business
            if current_cat in ("museum", "attraction", "historic_site", "tour"):
                return ("witch_museum", current_subcat)

    # Check haunted attraction
    for kw in HAUNTED_ATTRACTION_KEYWORDS:
        if kw in name_lower:
            if current_cat in ("museum", "attraction", "tour", "event_venue",
                               "entertainment", "uncategorized"):
                return ("haunted_attraction", current_subcat)

    # Check ghost tours
    for kw in GHOST_TOUR_KEYWORDS:
        if kw in name_lower:
            if current_cat in ("tour", "attraction", "event_venue", "uncategorized"):
                return ("ghost_tour", current_subcat)

    # Check psychic
    for kw in PSYCHIC_KEYWORDS:
        if kw in name_lower:
            if current_cat in ("psychic", "witch_shop", "shopping", "services",
                               "attraction", "event_venue", "uncategorized"):
                return ("psychic", current_subcat)

    # Check witch shops — only for shops/services, not restaurants etc.
    for kw in WITCH_SHOP_KEYWORDS:
        if kw in name_lower:
            if current_cat in ("shopping", "witch_shop", "services",
                               "event_venue", "uncategorized"):
                return ("witch_shop", current_subcat)

    return (current_cat, current_subcat)


# ─── Source normalizers ───

def normalize_destination_salem(data: dict) -> list:
    """Normalize Destination Salem listings to common format."""
    records = []

    # Process business listings
    for listing in data.get("listings", []):
        title = listing.get("title")
        if not title:
            continue

        title = clean_html(title)
        addr_obj = listing.get("fields", {}).get("address", {})
        lat = addr_obj.get("lat")
        lng = addr_obj.get("lng")

        if lat is None or lng is None:
            continue

        # Build address string
        parts = []
        sn = addr_obj.get("street_number", "")
        st = addr_obj.get("street_name_short", "") or addr_obj.get("street_name", "")
        if sn and st:
            parts.append(f"{sn} {st}")
        elif st:
            parts.append(st)
        city = addr_obj.get("city", "")
        state_short = addr_obj.get("state_short", "")
        post_code = addr_obj.get("post_code", "")
        if city:
            parts.append(city)
        if state_short:
            parts.append(state_short)
        if post_code:
            parts.append(post_code)
        address = ", ".join(parts) if parts else addr_obj.get("address", "")

        # Category from sub_categories
        sub_cats = listing.get("sub_categories", [])
        category = "uncategorized"
        subcategory = None
        source_categories = []

        if sub_cats:
            # Use first sub_category for primary classification
            for sc_id in sub_cats:
                if sc_id in DS_SUBCAT_MAP:
                    cat, subcat = DS_SUBCAT_MAP[sc_id]
                    if category == "uncategorized":
                        category = cat
                        subcategory = subcat
                    source_categories.append(f"ds_subcat_{sc_id}")
        else:
            # Fallback to parent category
            cat_from = listing.get("_category_from_listings", "")
            parent_id = cat_from.split("/")[0] if cat_from else ""
            if parent_id in DS_PARENT_MAP:
                category, subcategory = DS_PARENT_MAP[parent_id]
                source_categories.append(f"ds_parent_{parent_id}")

        fields = listing.get("fields", {})

        records.append({
            "name": title,
            "lat": float(lat),
            "lng": float(lng),
            "address": address,
            "phone": fields.get("phone", "") or "",
            "website": fields.get("website", "") or "",
            "hours": fields.get("hours", "") or "",
            "category": category,
            "subcategory": subcategory,
            "description": clean_html(listing.get("description", "") or ""),
            "source": "destination_salem",
            "source_id": str(listing.get("id", "")),
            "source_categories": source_categories,
            "has_coordinates": True,
        })

    # Process parking entries
    for parking in data.get("parking", []):
        title = parking.get("title", "")
        if not title:
            continue
        addr_obj = parking.get("fields", {}).get("address", {})
        lat = addr_obj.get("lat")
        lng = addr_obj.get("lng")
        if lat is None or lng is None:
            continue

        address = addr_obj.get("address", "")
        desc = parking.get("fields", {}).get("description", "")

        records.append({
            "name": clean_html(title),
            "lat": float(lat),
            "lng": float(lng),
            "address": address,
            "phone": "",
            "website": "",
            "hours": "",
            "category": "parking",
            "subcategory": "lot",
            "description": clean_html(desc) if desc else "",
            "source": "destination_salem",
            "source_id": str(parking.get("id", "")),
            "source_categories": ["ds_parking"],
            "has_coordinates": True,
        })

    # Process restrooms
    for restroom in data.get("restrooms", []):
        title = restroom.get("title", "")
        if not title:
            continue
        addr_obj = restroom.get("fields", {}).get("address", {})
        lat = addr_obj.get("lat")
        lng = addr_obj.get("lng")
        if lat is None or lng is None:
            continue

        address = addr_obj.get("address", "")

        records.append({
            "name": clean_html(title),
            "lat": float(lat),
            "lng": float(lng),
            "address": address,
            "phone": "",
            "website": "",
            "hours": "",
            "category": "restroom",
            "subcategory": None,
            "description": "",
            "source": "destination_salem",
            "source_id": str(restroom.get("id", "")),
            "source_categories": ["ds_restroom"],
            "has_coordinates": True,
        })

    return records


def normalize_haunted_happenings(data: dict) -> list:
    """Normalize Haunted Happenings venue listings."""
    records = []

    for venue in data.get("venues", []):
        name = venue.get("venue", "")
        if not name:
            continue

        name = clean_html(name)
        lat = venue.get("lat")
        lng = venue.get("lng")

        if lat is None or lng is None:
            continue

        # Build address
        parts = []
        addr = venue.get("address", "")
        if addr:
            parts.append(addr)
        city = venue.get("city", "")
        state = venue.get("state", "") or venue.get("stateprovince", "")
        zipcode = venue.get("zip", "")
        if city:
            parts.append(city)
        if state:
            parts.append(state)
        if zipcode:
            parts.append(str(zipcode))
        address = ", ".join(parts)

        records.append({
            "name": name,
            "lat": float(lat),
            "lng": float(lng),
            "address": address,
            "phone": venue.get("phone", "") or "",
            "website": venue.get("website", "") or "",
            "hours": "",
            "category": "event_venue",
            "subcategory": None,
            "description": "",
            "source": "haunted_happenings",
            "source_id": str(venue.get("id", "")),
            "source_categories": ["hh_venue"],
            "has_coordinates": True,
        })

    return records


def normalize_osm(data: dict) -> list:
    """Normalize OpenStreetMap POIs."""
    records = []

    for poi in data.get("pois", []):
        name = poi.get("name")
        if not name:
            continue

        lat = poi.get("lat")
        lng = poi.get("lng")
        if lat is None or lng is None:
            continue

        categories = poi.get("categories", {})

        # Determine if this is infrastructure to filter
        amenity = categories.get("amenity", "")
        leisure = categories.get("leisure", "")
        if amenity in INFRASTRUCTURE_FILTER:
            continue
        if leisure in INFRASTRUCTURE_LEISURE:
            continue

        # Build address
        addr_obj = poi.get("address", {}) or {}
        parts = []
        hn = addr_obj.get("housenumber", "")
        st = addr_obj.get("street", "")
        if hn and st:
            parts.append(f"{hn} {st}")
        elif st:
            parts.append(st)
        city = addr_obj.get("city", "")
        state = addr_obj.get("state", "")
        postcode = addr_obj.get("postcode", "")
        if city:
            parts.append(city)
        if state:
            parts.append(state)
        if postcode:
            parts.append(postcode)
        address = ", ".join(parts)

        # Determine category — try each tag type in priority order
        category = "uncategorized"
        subcategory = None
        source_categories = []

        # Collect all category tags for source_categories
        for tag_key, tag_val in categories.items():
            source_categories.append(f"osm_{tag_key}={tag_val}")

        # Tourism takes priority (museums, attractions, hotels)
        tourism = categories.get("tourism", "")
        historic = categories.get("historic", "")

        if tourism and tourism in OSM_TOURISM_MAP:
            category, subcategory = OSM_TOURISM_MAP[tourism]
        elif historic and historic in OSM_HISTORIC_MAP:
            category, subcategory = OSM_HISTORIC_MAP[historic]
        elif amenity and amenity in OSM_AMENITY_MAP:
            category, subcategory = OSM_AMENITY_MAP[amenity]

        shop = categories.get("shop", "")
        if shop and shop in OSM_SHOP_MAP:
            # Shop mapping — may override if more specific
            shop_cat, shop_sub = OSM_SHOP_MAP[shop]
            if category == "uncategorized":
                category, subcategory = shop_cat, shop_sub

        if categories.get("leisure", "") and category == "uncategorized":
            leis = categories["leisure"]
            if leis in OSM_LEISURE_MAP:
                category, subcategory = OSM_LEISURE_MAP[leis]

        if categories.get("office", "") and category == "uncategorized":
            off = categories["office"]
            if off in OSM_OFFICE_MAP:
                category, subcategory = OSM_OFFICE_MAP[off]

        if categories.get("craft", "") and category == "uncategorized":
            category = "services"
            subcategory = categories["craft"]

        all_tags = poi.get("all_tags", {})

        records.append({
            "name": name,
            "lat": float(lat),
            "lng": float(lng),
            "address": address,
            "phone": (poi.get("phone", "") or "").replace("+1 ", "").replace("+1-", ""),
            "website": poi.get("website", "") or "",
            "hours": poi.get("opening_hours", "") or "",
            "category": category,
            "subcategory": subcategory,
            "description": clean_html(poi.get("description", "") or all_tags.get("description", "") or ""),
            "source": "osm",
            "source_id": str(poi.get("osm_id", "")),
            "source_categories": source_categories,
            "has_coordinates": True,
        })

    return records


# ─── Deduplication ───

def _stem_word(word: str) -> str:
    """Crude stemming for common business name variants."""
    # Normalize common word endings for matching
    STEM_MAP = {
        "brewery": "brew", "brewing": "brew", "brewed": "brew",
        "bakery": "bake", "bakers": "bake", "baking": "bake",
        "theater": "theatre", "theatre": "theatre",
        "museum": "museum", "museums": "museum",
        "restaurant": "restaurant", "restaurants": "restaurant",
        "gallery": "gallery", "galleries": "gallery",
        "company": "company", "companies": "company",
    }
    if word in STEM_MAP:
        return STEM_MAP[word]
    # Strip trailing 's' for plurals (but not short words)
    if len(word) > 4 and word.endswith("s") and not word.endswith("ss"):
        return word[:-1]
    return word


def names_match(name1: str, name2: str) -> bool:
    """Check if two POI names are fuzzy matches."""
    n1 = normalize_name(name1)
    n2 = normalize_name(name2)

    if not n1 or not n2:
        return False

    # Exact match after normalization
    if n1 == n2:
        return True

    # Strip common business suffixes for comparison
    def strip_suffixes(s):
        s = re.sub(r"\b(llc|inc|co|corp|ltd)\b", "", s).strip()
        s = re.sub(r"\s+", " ", s)
        return s

    n1s = strip_suffixes(n1)
    n2s = strip_suffixes(n2)
    if n1s == n2s:
        return True

    # Stemmed comparison
    n1_stemmed = " ".join(_stem_word(w) for w in n1s.split())
    n2_stemmed = " ".join(_stem_word(w) for w in n2s.split())
    if n1_stemmed == n2_stemmed:
        return True

    # One contains the other (for cases like "A&J King" vs "A&J King Artisan Bakers")
    # Only if the shorter name is at least 4 chars to avoid false positives
    shorter, longer = sorted([n1s, n2s], key=len)
    if len(shorter) >= 4:
        if shorter in longer:
            # The shorter must be at least 33% the length of the longer
            if len(shorter) / len(longer) >= 0.33:
                return True

    # Also try with spaces removed for compound word differences
    # e.g., "couchdog" vs "couch dog"
    n1_compact = n1s.replace(" ", "")
    n2_compact = n2s.replace(" ", "")
    if len(n1_compact) >= 5 and len(n2_compact) >= 5:
        if n1_compact == n2_compact:
            return True
        shorter_c, longer_c = sorted([n1_compact, n2_compact], key=len)
        if shorter_c in longer_c and len(shorter_c) / len(longer_c) >= 0.5:
            return True

    # Stemmed compact comparison
    n1_stem_compact = n1_stemmed.replace(" ", "")
    n2_stem_compact = n2_stemmed.replace(" ", "")
    if len(n1_stem_compact) >= 5 and len(n2_stem_compact) >= 5:
        shorter_sc, longer_sc = sorted([n1_stem_compact, n2_stem_compact], key=len)
        if shorter_sc in longer_sc and len(shorter_sc) / len(longer_sc) >= 0.5:
            return True

    # Check word overlap — if enough words match
    words1_sig = {_stem_word(w) for w in n1s.split() if len(w) >= 3}
    words2_sig = {_stem_word(w) for w in n2s.split() if len(w) >= 3}
    if len(words1_sig) >= 2 and len(words2_sig) >= 2:
        intersection = words1_sig & words2_sig
        # Use the smaller set as denominator — if most words in the smaller name
        # appear in the larger name, it's a match
        smaller = min(len(words1_sig), len(words2_sig))
        if len(intersection) >= 2 and len(intersection) / smaller >= 0.65:
            return True

    return False


def merge_records(primary: dict, secondary: dict) -> dict:
    """
    Merge two records. Primary is preferred for most fields.
    OSM coordinates are preferred when available (more precise).
    """
    merged = dict(primary)

    # Prefer OSM coordinates (community-maintained, often more precise)
    if secondary["source"] == "osm" and primary["source"] != "osm":
        merged["lat"] = secondary["lat"]
        merged["lng"] = secondary["lng"]
    elif primary["source"] == "osm" and secondary["source"] != "osm":
        pass  # keep primary OSM coords

    # Prefer destination_salem for business info
    ds = primary if primary["source"] == "destination_salem" else (
        secondary if secondary["source"] == "destination_salem" else None
    )
    if ds:
        for field in ("phone", "website", "hours", "description", "address"):
            if ds.get(field) and not merged.get(field):
                merged[field] = ds[field]
            elif ds.get(field) and ds["source"] == "destination_salem":
                merged[field] = ds[field]

    # Fill in any empty fields from secondary
    for field in ("phone", "website", "hours", "description", "address"):
        if not merged.get(field) and secondary.get(field):
            merged[field] = secondary[field]

    # Merge source_categories
    all_cats = list(merged.get("source_categories", []))
    for sc in secondary.get("source_categories", []):
        if sc not in all_cats:
            all_cats.append(sc)
    merged["source_categories"] = all_cats

    return merged


def deduplicate(records: list) -> list:
    """
    Deduplicate records using fuzzy name matching + geographic proximity.
    Returns merged, deduplicated list.
    """
    # Source priority: destination_salem > haunted_happenings > osm
    SOURCE_PRIORITY = {"destination_salem": 0, "haunted_happenings": 1, "osm": 2}

    # Sort by source priority so destination_salem records are processed first
    records.sort(key=lambda r: SOURCE_PRIORITY.get(r["source"], 99))

    merged_pois = []
    # Track which sources confirmed each POI
    poi_sources = []  # parallel list of sets

    for record in records:
        matched_idx = None

        for i, existing in enumerate(merged_pois):
            # Check geographic proximity first (fast filter)
            dist = haversine_m(record["lat"], record["lng"],
                               existing["lat"], existing["lng"])

            # For exact name matches (after normalization), use a wider radius (200m)
            # because the same business often has different coordinates across sources
            exact_name = normalize_name(record["name"]) == normalize_name(existing["name"])
            threshold = 200 if exact_name else DEDUP_DISTANCE_M

            if dist > threshold:
                continue

            # Check name similarity
            if exact_name or names_match(record["name"], existing["name"]):
                matched_idx = i
                break

        if matched_idx is not None:
            # Merge with existing
            merged_pois[matched_idx] = merge_records(merged_pois[matched_idx], record)
            poi_sources[matched_idx].add(record["source"])
        else:
            merged_pois.append(dict(record))
            poi_sources.append({record["source"]})

    # Add source confirmation info to each record
    for poi, sources in zip(merged_pois, poi_sources):
        poi["confirmed_sources"] = sorted(sources)
        poi["multi_source"] = len(sources) > 1

    return merged_pois


# ─── Main pipeline ───

def main():
    print("=" * 70)
    print("  Salem POI Merge & Deduplication Pipeline")
    print("=" * 70)

    # 1. Read all three sources
    print("\n[1] Reading input files...")

    with open(DEST_SALEM_FILE) as f:
        ds_data = json.load(f)
    print(f"    Destination Salem:    {len(ds_data.get('listings', []))} listings"
          f" + {len(ds_data.get('parking', []))} parking"
          f" + {len(ds_data.get('restrooms', []))} restrooms")

    with open(HAUNTED_HAPPENINGS_FILE) as f:
        hh_data = json.load(f)
    print(f"    Haunted Happenings:   {len(hh_data.get('venues', []))} venues")

    with open(OSM_FILE) as f:
        osm_data = json.load(f)
    print(f"    OpenStreetMap:        {len(osm_data.get('pois', []))} POIs")

    # 2. Normalize each source
    print("\n[2] Normalizing records to common format...")

    ds_records = normalize_destination_salem(ds_data)
    print(f"    Destination Salem:    {len(ds_records)} normalized records")

    hh_records = normalize_haunted_happenings(hh_data)
    print(f"    Haunted Happenings:   {len(hh_records)} normalized records")

    osm_records = normalize_osm(osm_data)
    print(f"    OpenStreetMap:        {len(osm_records)} normalized records")

    all_records = ds_records + hh_records + osm_records
    print(f"    Total pre-filter:     {len(all_records)} records")

    # 3. Filter
    print("\n[3] Filtering records...")

    before_count = len(all_records)

    # Remove records with no name
    all_records = [r for r in all_records if r.get("name")]
    no_name = before_count - len(all_records)

    # Remove records with no coordinates
    before = len(all_records)
    all_records = [r for r in all_records if r.get("lat") and r.get("lng")]
    no_coords = before - len(all_records)

    # Remove records outside Salem proper
    before = len(all_records)
    all_records = [r for r in all_records if in_salem(r["lat"], r["lng"])]
    outside = before - len(all_records)

    print(f"    Removed {no_name} with no name")
    print(f"    Removed {no_coords} with no coordinates")
    print(f"    Removed {outside} outside Salem bounds "
          f"({SALEM_LAT_MIN}-{SALEM_LAT_MAX}, {SALEM_LNG_MIN}-{SALEM_LNG_MAX})")
    print(f"    Remaining: {len(all_records)} records")

    # 4. Apply Salem-specific category refinement
    print("\n[4] Applying Salem-specific category refinement...")

    reclassified = 0
    for record in all_records:
        old_cat = record["category"]
        new_cat, new_sub = classify_salem_specific(
            record["name"], record["category"], record["subcategory"]
        )
        if new_cat != old_cat:
            record["category"] = new_cat
            if new_sub is not None:
                record["subcategory"] = new_sub
            reclassified += 1

    print(f"    Reclassified {reclassified} records to Salem-specific categories")

    # 5. Deduplicate
    print("\n[5] Deduplicating ({} m threshold)...".format(DEDUP_DISTANCE_M))

    merged = deduplicate(all_records)
    dupes_removed = len(all_records) - len(merged)

    print(f"    Removed {dupes_removed} duplicates")
    print(f"    Final count: {len(merged)} unique POIs")

    # 6. Sort by category then name
    merged.sort(key=lambda r: (r["category"], r["name"]))

    # 7. Write output
    print(f"\n[6] Writing output to {OUTPUT_FILE}...")

    output = {
        "metadata": {
            "generated_by": "merge_pois.py",
            "sources": [
                {
                    "name": "destination_salem",
                    "file": "destination-salem.json",
                    "description": "Business listings from salem.org",
                    "input_records": len(ds_records),
                },
                {
                    "name": "haunted_happenings",
                    "file": "haunted-happenings-venues.json",
                    "description": "Event venues from hauntedhappenings.org",
                    "input_records": len(hh_records),
                },
                {
                    "name": "osm",
                    "file": "osm-salem-pois.json",
                    "description": "POIs from OpenStreetMap",
                    "input_records": len(osm_records),
                    "license": "Open Database License (ODbL) 1.0",
                    "attribution": "(c) OpenStreetMap contributors",
                },
            ],
            "dedup_threshold_meters": DEDUP_DISTANCE_M,
            "salem_bounds": {
                "lat_min": SALEM_LAT_MIN,
                "lat_max": SALEM_LAT_MAX,
                "lng_min": SALEM_LNG_MIN,
                "lng_max": SALEM_LNG_MAX,
            },
            "total_pois": len(merged),
        },
        "pois": merged,
    }

    with open(OUTPUT_FILE, "w") as f:
        json.dump(output, f, indent=2, ensure_ascii=False)

    file_size = os.path.getsize(OUTPUT_FILE)
    print(f"    Written {len(merged)} POIs ({file_size / 1024:.1f} KB)")

    # ─── Summary report ───
    print("\n" + "=" * 70)
    print("  MERGE SUMMARY")
    print("=" * 70)

    print(f"\n  Total merged POIs: {len(merged)}")

    # Category breakdown
    cat_counter = Counter(r["category"] for r in merged)
    print(f"\n  Top 20 categories:")
    for cat, count in cat_counter.most_common(20):
        print(f"    {cat:30s} {count:4d}")

    if len(cat_counter) > 20:
        remaining = sum(c for _, c in cat_counter.most_common()[20:])
        print(f"    {'... other categories':30s} {remaining:4d}")

    # Multi-source confirmed
    multi = [r for r in merged if r.get("multi_source")]
    print(f"\n  Multi-source confirmed: {len(multi)} POIs")
    for m in multi[:30]:
        print(f"    {m['name']:45s} <- {', '.join(m['confirmed_sources'])}")
    if len(multi) > 30:
        print(f"    ... and {len(multi) - 30} more")

    # Salem-specific categories
    SALEM_CATS = {"witch_shop", "psychic", "ghost_tour", "haunted_attraction", "witch_museum"}
    salem_specific = [r for r in merged if r["category"] in SALEM_CATS]
    print(f"\n  Salem-specific POIs: {len(salem_specific)}")
    salem_cat_counter = Counter(r["category"] for r in salem_specific)
    for cat, count in salem_cat_counter.most_common():
        print(f"    {cat:25s} {count:3d}")

    print(f"\n  All Salem-specific POIs:")
    for cat in sorted(SALEM_CATS):
        pois_in_cat = [r for r in merged if r["category"] == cat]
        if pois_in_cat:
            print(f"\n    [{cat}]")
            for p in sorted(pois_in_cat, key=lambda x: x["name"]):
                sources = ", ".join(p.get("confirmed_sources", [p["source"]]))
                print(f"      {p['name']:50s} ({sources})")

    # Source breakdown
    print(f"\n  Records by primary source:")
    source_counter = Counter(r["source"] for r in merged)
    for src, count in source_counter.most_common():
        print(f"    {src:25s} {count:4d}")

    print("\n" + "=" * 70)
    print("  Done.")
    print("=" * 70)


if __name__ == "__main__":
    main()
