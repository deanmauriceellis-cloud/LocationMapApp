#!/usr/bin/env python3
"""
Generate narration_points INSERTs + narration content for all salem_businesses
that don't already have narration_points entries.

Every POI in Salem gets a short, inviting narration. Style:
- Restaurants: "If you're hungry for [cuisine], [name] is right here."
- Shops: Invitation to browse/explore
- Historic: Historical context
- Services: Community-oriented
- All: Use description if available for richer content

Output:
  1. wave4_narration_inserts.sql — INSERT OR REPLACE INTO narration_points
  2. wave4_narration_content.sql — UPDATE statements with short_narration
"""

import sqlite3
import re
import os
import unicodedata

DB_PATH = os.path.join(os.path.dirname(__file__), '..', '..', 'app-salem', 'src', 'main', 'assets', 'salem_content.db')

# Map business_type → narration_point type
TYPE_MAP = {
    'shop_retail': 'shop',
    'restaurant': 'restaurant',
    'services': 'services',
    'attraction': 'attraction',
    'venue': 'venue',
    'other': 'other',
    'public': 'public',
    'park': 'park',
    'historic': 'historic_site',
    'cafe': 'cafe',
    'shop_occult': 'witch_shop',
    'lodging': 'lodging',
    'museum': 'museum',
    'tour': 'tour',
    'medical': 'medical',
    'bar': 'bar',
    'shop_psychic': 'psychic',
    'attraction_haunted': 'haunted_attraction',
    'tour_ghost': 'ghost_tour',
}

# Cuisine display names
CUISINE_NAMES = {
    'seafood': 'seafood',
    'italian': 'Italian',
    'chinese': 'Chinese',
    'japanese': 'Japanese',
    'mexican': 'Mexican',
    'thai': 'Thai',
    'indian': 'Indian',
    'pizza': 'pizza',
    'american': 'American comfort food',
    'fast_food': 'fast food',
    'bakery': 'baked goods and pastries',
    'coffee': 'coffee',
    'ice_cream': 'ice cream',
    'dessert': 'dessert and sweet treats',
    'sandwich': 'sandwiches',
    'sushi': 'sushi',
    'korean': 'Korean',
    'vietnamese': 'Vietnamese',
    'greek': 'Greek',
    'mediterranean': 'Mediterranean',
    'middle_eastern': 'Middle Eastern',
    'caribbean': 'Caribbean',
    'german': 'German',
    'french': 'French',
    'british': 'British pub fare',
    'breakfast': 'breakfast',
    'brunch': 'brunch',
    'diner': 'classic diner fare',
    'bbq': 'barbecue',
    'wings': 'wings',
    'burger': 'burgers',
    'steak': 'steak',
    'soup': 'soup',
    'vegan': 'vegan',
    'vegetarian': 'vegetarian',
    'gluten_free': 'gluten-free options',
    'gastropub': 'gastropub fare',
    'tapas': 'tapas',
    'ramen': 'ramen',
    'pho': 'pho',
    'deli': 'deli sandwiches',
    'roast_beef': 'roast beef',
    'donuts': 'donuts',
    'juice': 'fresh juices',
    'smoothie': 'smoothies',
    'tea': 'tea',
    'crepe': 'crêpes',
    'chocolate': 'chocolate',
    'fudge': 'fudge',
    'candy': 'candy and sweets',
    'popcorn': 'gourmet popcorn',
    'moroccan': 'Moroccan',
    'turkish': 'Turkish',
    'peruvian': 'Peruvian',
    'colombian': 'Colombian',
    'brazilian': 'Brazilian',
    'ethiopian': 'Ethiopian',
    'lebanese': 'Lebanese',
    'dominican': 'Dominican',
    'salvadoran': 'Salvadoran',
}


def make_id(name):
    """Convert name to kebab-case ID."""
    s = name.lower().strip()
    s = unicodedata.normalize('NFKD', s)
    s = s.encode('ascii', 'ignore').decode('ascii')
    s = re.sub(r"[''`]", '', s)
    s = re.sub(r'[^a-z0-9]+', '_', s)
    s = s.strip('_')
    return s


def sql_escape(text):
    """Escape single quotes for SQL."""
    if text is None:
        return None
    return text.replace("'", "''")


def infer_cuisine_from_name(name):
    """Try to infer cuisine from the business name."""
    n = name.lower()
    patterns = {
        'pizza': 'pizza',
        'roast beef': 'roast_beef',
        'taco': 'mexican',
        'burrito': 'mexican',
        'sushi': 'sushi',
        'ramen': 'ramen',
        'pho': 'pho',
        'thai': 'thai',
        'wok': 'chinese',
        'dumpling': 'chinese',
        'noodle': 'chinese',
        'kebab': 'middle_eastern',
        'falafel': 'middle_eastern',
        'gyro': 'greek',
        'burger': 'burger',
        'donut': 'donuts',
        'doughnut': 'donuts',
        'bagel': 'bakery',
        'bakery': 'bakery',
        'ice cream': 'ice_cream',
        'gelato': 'ice_cream',
        'coffee': 'coffee',
        'café': 'coffee',
        'cafe': 'coffee',
        'tea ': 'tea',
        'juice': 'juice',
        'smoothie': 'smoothie',
        'bbq': 'bbq',
        'barbecue': 'bbq',
        'wing': 'wings',
        'steak': 'steak',
        'seafood': 'seafood',
        'lobster': 'seafood',
        'clam': 'seafood',
        'oyster': 'seafood',
        'fish': 'seafood',
        'deli': 'deli',
        'sub ': 'sandwich',
        'sandwich': 'sandwich',
        'crepe': 'crepe',
        'chocolate': 'chocolate',
        'fudge': 'fudge',
        'candy': 'candy',
        'popcorn': 'popcorn',
        'diner': 'diner',
        'ihop': 'breakfast',
        'dunkin': 'coffee',
        'starbucks': 'coffee',
        "mcdonald": 'fast_food',
        'burger king': 'fast_food',
        "wendy": 'fast_food',
        'taco bell': 'fast_food',
        "papa john": 'pizza',
        'domino': 'pizza',
    }
    for pattern, cuisine in patterns.items():
        if pattern in n:
            return cuisine
    return None


def condense_description(desc, max_words=40):
    """Condense a description to a TTS-friendly snippet."""
    if not desc:
        return None
    # Remove HTML tags
    desc = re.sub(r'<[^>]+>', ' ', desc)
    # Remove URLs
    desc = re.sub(r'https?://\S+', '', desc)
    # Remove email addresses
    desc = re.sub(r'\S+@\S+\.\S+', '', desc)
    # Clean whitespace
    desc = re.sub(r'\s+', ' ', desc).strip()
    # Take first N words, try to end at a sentence boundary
    words = desc.split()
    if len(words) <= max_words:
        return desc
    # Find sentence boundary within range
    truncated = ' '.join(words[:max_words])
    last_period = truncated.rfind('.')
    last_excl = truncated.rfind('!')
    boundary = max(last_period, last_excl)
    if boundary > len(truncated) // 3:
        return truncated[:boundary + 1]
    return truncated + '.'


def generate_restaurant_narration(name, cuisine_type, description):
    """Generate narration for a restaurant."""
    cuisine = cuisine_type
    if not cuisine:
        cuisine = infer_cuisine_from_name(name)

    cuisine_display = CUISINE_NAMES.get(cuisine, cuisine) if cuisine else None

    if description:
        snippet = condense_description(description, 35)
        if snippet:
            return f"You're near {name}. {snippet}"

    if cuisine_display:
        if cuisine in ('fast_food',):
            return f"You're near {name}, a quick bite right here in Salem."
        elif cuisine in ('coffee',):
            return f"Need a pick-me-up? {name} serves fresh coffee right here in Salem."
        elif cuisine in ('ice_cream', 'dessert'):
            return f"Got a sweet tooth? {name} is right here with {cuisine_display}."
        elif cuisine in ('bakery',):
            return f"The smell of fresh {cuisine_display} awaits you at {name}."
        elif cuisine in ('breakfast', 'brunch'):
            return f"If you're starting your day hungry, {name} serves {cuisine_display} right here in Salem."
        else:
            return f"If you're hungry for {cuisine_display}, {name} is right here in Salem."
    else:
        return f"Looking for a bite to eat? {name} is right here in Salem."


def generate_cafe_narration(name, cuisine_type, description):
    """Generate narration for a cafe."""
    if description:
        snippet = condense_description(description, 35)
        if snippet:
            return f"You're near {name}. {snippet}"
    return f"Need a pick-me-up? {name} offers coffee, drinks, and a place to sit right here in Salem."


def generate_bar_narration(name, description):
    """Generate narration for a bar."""
    if description:
        snippet = condense_description(description, 35)
        if snippet:
            return f"You're near {name}. {snippet}"
    return f"Looking to raise a glass? {name} is right here in Salem."


def generate_shop_narration(name, description):
    """Generate narration for a retail shop."""
    if description:
        snippet = condense_description(description, 40)
        if snippet:
            return f"You're near {name}. {snippet}"
    # Try to infer shop type from name
    n = name.lower()
    if any(w in n for w in ('liquor', 'wine', 'spirits', 'beer')):
        return f"You're near {name}, a place to pick up spirits of the liquid variety here in Salem."
    if any(w in n for w in ('dispensary', 'cannabis')):
        return f"You're near {name}, an adult-use cannabis dispensary here in Salem."
    if any(w in n for w in ('boutique', 'fashion', 'clothing', 'apparel')):
        return f"Browse the latest styles at {name}, right here in Salem."
    if any(w in n for w in ('book', 'comic')):
        return f"Bookworms, take note. {name} is right here in Salem."
    if any(w in n for w in ('antique', 'vintage', 'thrift')):
        return f"You're near {name}. Treasure hunters, this one's for you."
    if any(w in n for w in ('jewelry', 'jewel', 'gem')):
        return f"You're near {name}, offering jewelry and fine pieces here in Salem."
    if any(w in n for w in ('art', 'gallery', 'studio')):
        return f"You're near {name}, showcasing art and creativity in Salem."
    if any(w in n for w in ('pet', 'dog', 'cat')):
        return f"You're near {name}, for the animal lovers in your group."
    if any(w in n for w in ('flower', 'floral', 'plant')):
        return f"You're near {name}, bringing color and life to Salem."
    if any(w in n for w in ('crystal', 'mineral', 'stone')):
        return f"You're near {name}. Step in and discover crystals and minerals from around the world."
    return f"You're near {name}. Step in and see what Salem has to offer."


def generate_witch_shop_narration(name, description):
    """Generate narration for a witch/occult shop."""
    if description:
        snippet = condense_description(description, 40)
        if snippet:
            return f"You're near {name}. {snippet}"
    return f"Step into {name} and explore Salem's mystical side. Candles, crystals, herbs, and enchantments await."


def generate_psychic_narration(name, description):
    """Generate narration for a psychic."""
    if description:
        snippet = condense_description(description, 35)
        if snippet:
            return f"You're near {name}. {snippet}"
    return f"Seek guidance at {name}. Salem's spiritual practitioners are ready to reveal what lies ahead."


def generate_historic_narration(name, description, historical_note):
    """Generate narration for a historic site."""
    if historical_note:
        snippet = condense_description(historical_note, 40)
        if snippet:
            return f"You're near {name}. {snippet}"
    if description:
        snippet = condense_description(description, 40)
        if snippet:
            return f"You're near {name}. {snippet}"
    n = name.lower()
    if 'cemetery' in n or 'burial' in n:
        return f"You're near {name}, a resting place steeped in Salem's history. Walk quietly among the stones."
    if 'beach' in n:
        return f"You're near {name}, where Salem meets the sea."
    if 'house' in n or 'mansion' in n:
        return f"You're near {name}, a piece of Salem's architectural heritage."
    return f"You're near {name}, a place that carries the weight of Salem's long history."


def generate_park_narration(name, description):
    """Generate narration for a park."""
    if description:
        snippet = condense_description(description, 35)
        if snippet:
            return f"You're near {name}. {snippet}"
    n = name.lower()
    if 'playground' in n:
        return f"You're near {name}, a spot for the younger members of your group to burn off some energy."
    if 'garden' in n:
        return f"You're near {name}, a green oasis in the heart of Salem."
    return f"You're near {name}. Take a moment to breathe. Salem's got plenty of green space to enjoy."


def generate_museum_narration(name, description):
    """Generate narration for a museum."""
    if description:
        snippet = condense_description(description, 40)
        if snippet:
            return f"You're near {name}. {snippet}"
    return f"You're near {name}. Step inside and discover another layer of Salem's story."


def generate_attraction_narration(name, description):
    """Generate narration for an attraction."""
    if description:
        snippet = condense_description(description, 35)
        if snippet:
            return f"You're near {name}. {snippet}"
    return f"You're near {name}, one of Salem's points of interest."


def generate_haunted_narration(name, description):
    """Generate narration for a haunted attraction."""
    if description:
        snippet = condense_description(description, 35)
        if snippet:
            return f"You're near {name}. {snippet}"
    return f"Dare to enter {name}? One of Salem's haunted attractions awaits the brave."


def generate_ghost_tour_narration(name, description):
    """Generate narration for a ghost tour."""
    if description:
        snippet = condense_description(description, 35)
        if snippet:
            return f"You're near {name}. {snippet}"
    return f"The spirits await at {name}. A ghostly journey through Salem's dark past."


def generate_tour_narration(name, description):
    """Generate narration for a tour."""
    if description:
        snippet = condense_description(description, 35)
        if snippet:
            return f"You're near {name}. {snippet}"
    return f"You're near {name}. Join a guided exploration of Salem's history and hidden corners."


def generate_lodging_narration(name, description):
    """Generate narration for lodging."""
    if description:
        snippet = condense_description(description, 35)
        if snippet:
            return f"You're near {name}. {snippet}"
    n = name.lower()
    if 'inn' in n:
        return f"You're near {name}, a cozy inn in the heart of Salem."
    if 'hotel' in n:
        return f"You're near {name}, offering accommodations right here in Salem."
    return f"Looking for a place to stay? {name} is right here in Salem."


def generate_venue_narration(name, description):
    """Generate narration for a venue."""
    if description:
        snippet = condense_description(description, 40)
        if snippet:
            return f"You're near {name}. {snippet}"
    n = name.lower()
    if 'theater' in n or 'theatre' in n or 'cinema' in n:
        return f"You're near {name}, Salem's spot for shows and entertainment."
    if 'wharf' in n or 'pier' in n or 'dock' in n:
        return f"You're near {name}, where Salem meets the water."
    if 'hall' in n:
        return f"You're near {name}, a gathering place in the heart of Salem."
    if 'church' in n or 'chapel' in n:
        return f"You're near {name}, a spiritual landmark in Salem."
    if 'square' in n or 'fountain' in n:
        return f"You're near {name}, a landmark in downtown Salem."
    if 'cemetery' in n:
        return f"You're near {name}. Walk quietly among the stones."
    return f"You're near {name}, a notable spot in Salem."


def generate_services_narration(name, description):
    """Generate narration for a service business."""
    if description:
        snippet = condense_description(description, 30)
        if snippet:
            return f"You're near {name}. {snippet}"
    n = name.lower()
    if any(w in n for w in ('salon', 'barber', 'hair', 'spa', 'nail', 'beauty')):
        return f"You're near {name}. Need a little self-care while in Salem? They've got you."
    if any(w in n for w in ('tattoo', 'piercing', 'ink')):
        return f"You're near {name}. Looking to take home a permanent souvenir from Salem?"
    if any(w in n for w in ('laundry', 'cleaners', 'dry clean')):
        return f"You're near {name}, practical services for visitors and locals alike."
    if any(w in n for w in ('bank', 'credit union', 'financial')):
        return f"You're near {name}, part of Salem's financial services."
    if any(w in n for w in ('gym', 'fitness', 'yoga', 'crossfit')):
        return f"You're near {name}. Need to work off those Salem treats? Drop in."
    if any(w in n for w in ('school', 'academy', 'education', 'college')):
        return f"You're near {name}, part of Salem's educational community."
    if any(w in n for w in ('church', 'temple', 'mosque', 'synagogue')):
        return f"You're near {name}, a place of worship in Salem."
    if any(w in n for w in ('post office', 'usps', 'fedex', 'ups')):
        return f"You're near {name}. Need to send a postcard from Salem?"
    return f"You're near {name}, serving the Salem community."


def generate_medical_narration(name, description):
    """Generate narration for medical services."""
    if description:
        snippet = condense_description(description, 30)
        if snippet:
            return f"You're near {name}. {snippet}"
    n = name.lower()
    if 'hospital' in n:
        return f"You're near {name}. Salem's healthcare is close at hand if you need it."
    if any(w in n for w in ('pharmacy', 'cvs', 'walgreens', 'rite aid')):
        return f"You're near {name}. Need something from the pharmacy? They're right here."
    if any(w in n for w in ('dental', 'dentist')):
        return f"You're near {name}, dental care in Salem."
    return f"You're near {name}, providing healthcare services in Salem."


def generate_public_narration(name, description):
    """Generate narration for a public space."""
    if description:
        snippet = condense_description(description, 35)
        if snippet:
            return f"You're near {name}. {snippet}"
    n = name.lower()
    if any(w in n for w in ('library', 'public library')):
        return f"You're near {name}. Free Wi-Fi, quiet space, and a wealth of Salem history inside."
    if any(w in n for w in ('fire', 'police', 'station')):
        return f"You're near {name}, keeping Salem safe."
    if any(w in n for w in ('school', 'elementary', 'middle', 'high school')):
        return f"You're near {name}, part of Salem's educational community."
    if any(w in n for w in ('post office',)):
        return f"You're near {name}. Need to send a postcard home from Salem?"
    if any(w in n for w in ('city hall', 'town hall', 'government')):
        return f"You're near {name}, the seat of Salem's local government."
    if any(w in n for w in ('parking', 'garage', 'lot')):
        return f"You're near {name}. Good to know where parking is in Salem."
    return f"You're near {name}, part of Salem's public infrastructure."


def generate_other_narration(name, description):
    """Generate narration for uncategorized businesses."""
    if description:
        snippet = condense_description(description, 35)
        if snippet:
            return f"You're near {name}. {snippet}"
    return f"You're near {name}, part of Salem's vibrant community."


def generate_narration(biz_type, name, cuisine_type, description, historical_note):
    """Route to the appropriate narration generator."""
    generators = {
        'restaurant': lambda: generate_restaurant_narration(name, cuisine_type, description),
        'cafe': lambda: generate_cafe_narration(name, cuisine_type, description),
        'bar': lambda: generate_bar_narration(name, description),
        'shop_retail': lambda: generate_shop_narration(name, description),
        'shop_occult': lambda: generate_witch_shop_narration(name, description),
        'shop_psychic': lambda: generate_psychic_narration(name, description),
        'historic': lambda: generate_historic_narration(name, description, historical_note),
        'park': lambda: generate_park_narration(name, description),
        'museum': lambda: generate_museum_narration(name, description),
        'attraction': lambda: generate_attraction_narration(name, description),
        'attraction_haunted': lambda: generate_haunted_narration(name, description),
        'tour_ghost': lambda: generate_ghost_tour_narration(name, description),
        'tour': lambda: generate_tour_narration(name, description),
        'lodging': lambda: generate_lodging_narration(name, description),
        'venue': lambda: generate_venue_narration(name, description),
        'services': lambda: generate_services_narration(name, description),
        'medical': lambda: generate_medical_narration(name, description),
        'public': lambda: generate_public_narration(name, description),
        'other': lambda: generate_other_narration(name, description),
    }
    gen = generators.get(biz_type, lambda: generate_other_narration(name, description))
    return gen()


def main():
    db = sqlite3.connect(DB_PATH)
    db.row_factory = sqlite3.Row

    # Get all businesses without narration_points (distinct by name, take first occurrence)
    rows = db.execute("""
        SELECT b.name, b.business_type, b.cuisine_type, b.description,
               b.lat, b.lng, b.address, b.phone, b.website, b.hours,
               b.historical_note, b.data_source, b.confidence, b.id
        FROM salem_businesses b
        LEFT JOIN narration_points np ON b.name = np.name
        WHERE np.name IS NULL
        GROUP BY b.name
        ORDER BY b.business_type, b.name
    """).fetchall()

    print(f"Found {len(rows)} businesses without narration_points")

    out_dir = os.path.dirname(__file__)
    insert_path = os.path.join(out_dir, '..', '..', 'salem-content', 'wave4_narration_inserts.sql')
    content_path = os.path.join(out_dir, '..', '..', 'salem-content', 'wave4_narration_content.sql')

    seen_ids = set()
    timestamp = 1775448992042  # 2026-04-06

    with open(insert_path, 'w') as f_ins, open(content_path, 'w') as f_con:
        f_ins.write("-- ============================================================\n")
        f_ins.write("-- Wave 4: Narration points for ALL remaining salem_businesses\n")
        f_ins.write(f"-- {len(rows)} POIs\n")
        f_ins.write("-- Generated by generate_all_poi_narrations.py\n")
        f_ins.write("-- ============================================================\n\n")

        f_con.write("-- ============================================================\n")
        f_con.write("-- Wave 4 Narration Content — All remaining Salem POIs\n")
        f_con.write(f"-- {len(rows)} narrations\n")
        f_con.write("-- Generated by generate_all_poi_narrations.py\n")
        f_con.write("-- ============================================================\n\n")
        f_con.write("BEGIN;\n\n")

        current_type = None
        count = 0

        for row in rows:
            name = row['name']
            biz_type = row['business_type']
            cuisine = row['cuisine_type']
            desc = row['description']
            hist = row['historical_note']
            lat = row['lat']
            lng = row['lng']
            addr = row['address'] or ''
            phone = row['phone']
            website = row['website']
            hours = row['hours']
            data_source = row['data_source'] or 'scraped'
            confidence = row['confidence'] or 0.85

            # Generate ID
            poi_id = make_id(name)
            if poi_id in seen_ids:
                poi_id = f"{poi_id}_{make_id(biz_type)}"
            if poi_id in seen_ids:
                poi_id = f"{poi_id}_{count}"
            seen_ids.add(poi_id)

            np_type = TYPE_MAP.get(biz_type, 'other')

            # Generate narration
            narration = generate_narration(biz_type, name, cuisine, desc, hist)

            # Type header
            if biz_type != current_type:
                current_type = biz_type
                f_ins.write(f"\n-- [{biz_type}] ==============================\n")
                f_con.write(f"\n-- [{biz_type}] ==============================\n")

            # INSERT statement
            f_ins.write(f"-- {name}\n")
            f_ins.write(
                f"INSERT OR REPLACE INTO narration_points "
                f"(id, name, lat, lng, address, type, short_narration, long_narration, "
                f"description, geofence_radius_m, geofence_shape, priority, wave, "
                f"phone, website, hours, "
                f"data_source, confidence, verified_date, created_at, updated_at, stale_after) "
                f"VALUES ('{sql_escape(poi_id)}', '{sql_escape(name)}', {lat}, {lng}, "
                f"'{sql_escape(addr)}', '{np_type}', NULL, NULL, "
                f"'{sql_escape(desc)}', 30, 'circle', 4, 4, "
                f"{repr_or_null(phone)}, {repr_or_null(website)}, {repr_or_null(hours)}, "
                f"'{sql_escape(data_source)}', {confidence}, '2026-04-06', "
                f"{timestamp}, {timestamp}, 0);\n"
            )

            # UPDATE statement for narration content
            f_con.write(f"-- [{np_type}] {name}\n")
            f_con.write(
                f"UPDATE narration_points SET short_narration = '{sql_escape(narration)}' "
                f"WHERE name = '{sql_escape(name)}';\n\n"
            )

            count += 1

        f_con.write("\nCOMMIT;\n")

    print(f"Generated {count} narration_points")
    print(f"  INSERT file: {os.path.abspath(insert_path)}")
    print(f"  Content file: {os.path.abspath(content_path)}")


def repr_or_null(val):
    """Return SQL-safe representation or NULL."""
    if val is None or val == '':
        return 'NULL'
    return f"'{sql_escape(val)}'"


if __name__ == '__main__':
    main()
