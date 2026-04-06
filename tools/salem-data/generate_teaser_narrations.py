#!/usr/bin/env python3
"""
Generate ultra-short "teaser" narrations for ALL 814 Salem POIs.

Target: ~15-25 words per narration (~10 seconds TTS at 0.9x speed).
Style: Information-dense, no filler. Every word earns its place.
Purpose: Free tier ambient narration. Pay to unlock full narrations.

Rules:
  - NO "You're near", "right here in Salem", "serving the community"
  - Every narration MUST say WHAT the place IS (type) + NAME + best detail
  - Minimum 8 words. Target 15-20.
  - No mid-sentence cutoffs. Clean, complete thoughts.
  - Descriptions mined for the ONE best fact.
  - No-description POIs get type-specific functional narrations.

Process:
  1. Promote current short_narration → long_narration (where long IS NULL)
  2. Replace short_narration with ultra-short teaser
  3. Output SQL files for both operations
"""

import sqlite3
import re
import os

DB_PATH = os.path.join(os.path.dirname(__file__), '..', '..', 'app-salem', 'src', 'main', 'assets', 'salem_content.db')
OUT_DIR = os.path.join(os.path.dirname(__file__), '..', '..', 'salem-content')


# ============================================================
# DESCRIPTION MINING
# ============================================================

def clean_text(s):
    """Normalize whitespace, strip."""
    if not s:
        return None
    s = re.sub(r'\s+', ' ', s).strip()
    return s if s else None

def extract_street(address):
    """Pull street name from address like '231 Essex Street, Salem, MA'."""
    if not address:
        return None
    m = re.match(r'[\d;/\-\s]*(.+?)(?:,\s*(?:Salem|Peabody|Danvers|Beverly|MA)|$)', address, re.I)
    if m:
        street = m.group(1).strip().rstrip(',')
        if len(street) > 3 and 'salem' not in street.lower() and 'ma' != street.lower():
            return street
    return None

def extract_best_sentence(desc, name, max_words=22):
    """Extract the single best sentence from a description. No cutoffs."""
    if not desc:
        return None
    desc = clean_text(desc)
    if not desc:
        return None

    # Strip the business name from the start
    desc_clean = desc
    for prefix in [name, f'The {name}', f'{name} is', f'The {name} is',
                   f'{name} -', f'{name} –', f'{name} —']:
        if desc_clean.lower().startswith(prefix.lower()):
            rest = desc_clean[len(prefix):].lstrip(' ,.-–—:')
            if rest and len(rest) > 10:
                desc_clean = rest[0].upper() + rest[1:]
                break

    # Split into sentences
    sentences = re.split(r'(?<=[.!?])\s+', desc_clean)
    if not sentences:
        return None

    # Score each sentence for interestingness
    scored = []
    for s in sentences:
        s = s.strip().rstrip('.')
        words = s.split()
        if len(words) < 3 or len(words) > max_words:
            # If too long, try to take first clause
            if len(words) > max_words:
                # Split at comma, semicolon, or dash
                parts = re.split(r'[,;–—]', s)
                first_part = parts[0].strip()
                first_words = first_part.split()
                if 3 <= len(first_words) <= max_words:
                    s = first_part
                    words = first_words
                else:
                    continue
            else:
                continue

        score = 0
        s_lower = s.lower()

        # High-value signals
        if re.search(r'\b\d{4}\b', s):  # Contains a year
            score += 3
        if re.search(r"(world'?s?\s+only|only\s+\w+\s+in|oldest|first\s|largest|original)", s_lower):
            score += 5
        if re.search(r"(award|voted|#\d|top.rated|highest.rated|best\s|famous)", s_lower):
            score += 4
        if re.search(r"(handmade|handcrafted|artisan|local|homemade|small.batch|organic)", s_lower):
            score += 2
        if re.search(r"(since\s+\d{4}|founded|established|built\s+in|dating)", s_lower):
            score += 3
        if re.search(r"(haunted|ghost|witch|spell|tarot|psychic|occult|magick)", s_lower):
            score += 2  # Salem-relevant
        if re.search(r"(free parking|open\s+\d|daily\s+\d|hours|admission|tickets)", s_lower):
            score += 1  # Practical info is useful
        if 'click here' in s_lower or 'visit our website' in s_lower or 'http' in s_lower:
            score -= 10  # Web junk
        if re.search(r'(call\s*:?\s*\d{3}|email\s|@\w+\.\w+|\.com|\.org|phone|learn more)', s_lower):
            score -= 10  # Contact info / CTA junk
        if re.search(r'^(to learn more|for more info|visit us|contact|check out our)', s_lower):
            score -= 10  # CTA opener
        if len(words) >= 8:
            score += 1  # Prefer meatier sentences

        scored.append((score, s))

    if not scored:
        return None

    # Return highest-scored sentence
    scored.sort(key=lambda x: -x[0])
    return scored[0][1]


# ============================================================
# NAME-BASED INFERENCE
# ============================================================

def infer_cuisine(name, cuisine_type):
    """Infer cuisine from explicit type or name patterns."""
    if cuisine_type:
        display = {
            'seafood': 'Seafood', 'italian': 'Italian', 'chinese': 'Chinese',
            'japanese': 'Japanese', 'mexican': 'Mexican', 'thai': 'Thai',
            'indian': 'Indian', 'pizza': 'Pizza', 'american': 'American',
            'fast_food': 'Fast food', 'bakery': 'Bakery', 'coffee': 'Coffee',
            'ice_cream': 'Ice cream', 'dessert': 'Desserts', 'sandwich': 'Sandwiches',
            'sushi': 'Sushi', 'korean': 'Korean', 'vietnamese': 'Vietnamese',
            'greek': 'Greek', 'mediterranean': 'Mediterranean',
            'middle_eastern': 'Middle Eastern', 'caribbean': 'Caribbean',
            'french': 'French', 'british': 'Pub fare', 'breakfast': 'Breakfast',
            'brunch': 'Brunch', 'diner': 'Diner', 'bbq': 'Barbecue',
            'burger': 'Burgers', 'wings': 'Wings',
        }
        return display.get(cuisine_type, cuisine_type.replace('_', ' ').title())

    nl = name.lower()
    patterns = [
        ('Pizza', ['pizza', 'pizzeria']),
        ('Sushi', ['sushi']),
        ('Japanese', ['koto', 'ramen', 'teriyaki', 'hibachi', 'tempura']),
        ('Mexican', ['taco', 'burrito', 'poblano', 'taqueria', 'cantina', 'barrio']),
        ('Chinese', ['wok', 'mei lee', 'hunan', 'szechuan']),
        ('Thai', ['thai', 'pad thai']),
        ('Indian', ['tandoor', 'masala', 'curry house']),
        ('Italian', ['trattoria', 'ristorante', 'osteria', 'piatti', 'verona', 'bella verona']),
        ('Mediterranean', ['mediterranean', 'falafel', 'kebab', 'adriatic', 'settler']),
        ('French', ['bernadette', 'bistro', 'brasserie', 'patisserie', 'crêpe']),
        ('Seafood', ['seafood', 'lobster', 'clam', 'oyster', 'fish co', 'turner']),
        ('Ice cream', ['ice cream', 'gelato', 'holy cow', 'melt ice', 'frozen']),
        ('Coffee and donuts', ['dunkin']),
        ('Coffee', ['coffee', 'espresso', 'roasters']),
        ('Burgers', ['burger', 'five guys']),
        ('Barbecue', ['bbq', 'barbecue', 'smokehouse']),
        ('Roast beef', ['roast beef', 'bill & bob']),
        ('Deli', ['deli']),
        ('Bakery', ['bake', 'bakery', 'pastry', 'sweet something', 'cookie']),
        ('Pub fare', ['pub', 'tavern', 'ale house', 'tap room']),
        ('Farm-to-table', ['farm', 'maitland']),
        ('Breakfast', ['waffle', 'pancake', 'breakfast']),
        ('Wings', ['wing zone', 'buffalo']),
    ]
    for cuisine, kws in patterns:
        for kw in kws:
            if kw in nl:
                return cuisine
    return None

def infer_shop_specialty(name, desc):
    """Infer what a shop sells from name/description."""
    combined = (name + ' ' + (desc or '')).lower()
    patterns = [
        ('Records, vinyl, and music', ['record exchange', 'record', 'vinyl', 'music shop']),
        ('Books and reading', ['book', 'comic']),
        ('Antiques and vintage', ['antique', 'vintage', 'collectible']),
        ('Art gallery', ['gallery', 'fine art']),
        ('Jewelry', ['jewel', 'gem ', 'silver', 'gold ']),
        ('Candy and chocolate', ['candy', 'chocolate', 'fudge', 'confection']),
        ('Ice cream and sweets', ['ice cream', 'gelato', 'sweet']),
        ('Clothing and fashion', ['boutique', 'apparel', 'clothing', 'fashion', 'dress']),
        ('Gifts and souvenirs', ['gift', 'souvenir', 'novelty']),
        ('Toys and games', ['toy', 'game', 'puzzle']),
        ('Pet supplies', ['pet', 'dog ', 'cat ']),
        ('Yarn and craft supplies', ['craft', 'yarn', 'stitch', 'knit', 'quilt']),
        ('Crystals and minerals', ['crystal', 'mineral']),
        ('Candles', ['candle', 'wax', 'wick']),
        ('Home decor', ['home decor', 'furnish', 'interior']),
        ('Liquor store', ['liquor', 'wine ', 'spirits', 'package store']),
        ('Cannabis dispensary', ['dispensary', 'cannabis', 'marijuana']),
        ('Tattoo studio', ['tattoo', 'ink ', 'piercing']),
        ('Beauty and skincare', ['salon', 'beauty', 'cosmetic', 'skincare']),
        ('Thrift store', ['thrift', 'goodwill', 'consignment', 'secondhand']),
        ('Grocery store', ['grocery', 'market', 'stop & shop', 'target', 'shaw']),
        ('Convenience store', ['convenience', '7-eleven', '7 eleven', 'quick pick']),
        ('Bridal', ['bridal', 'wedding']),
        ('Photography', ['photo', 'camera']),
        ('Handcrafted brooms', ['broom']),
        ('Hats and costumes', ['hat', 'costume', 'steampunk', 'bespoke']),
        ('Hardware', ['hardware', 'ace ', 'home depot', 'lowes']),
        ('Pharmacy', ['pharmacy', 'cvs', 'walgreen', 'rite aid']),
        ('Flowers', ['flower', 'floral', 'florist']),
        ('Tobacco', ['smoke shop', 'tobacco', 'cigar', 'vape']),
        ('Eyewear', ['optical', 'eyewear', 'glasses', 'lens']),
    ]
    for specialty, kws in patterns:
        for kw in kws:
            if kw in combined:
                return specialty
    return None

def infer_service_type(name):
    """Infer the type of service business from name."""
    nl = name.lower()
    patterns = [
        ('Parking', ['parking', 'garage', ' lot']),
        ('Barbershop', ['barber']),
        ('Hair salon', ['salon', 'hair ']),
        ('Tattoo studio', ['tattoo', 'ink']),
        ('Dry cleaning', ['cleaners', 'laundry', 'dry clean']),
        ('Auto repair', ['auto', 'car ', 'tire', 'mechanic', 'propeller', 'muffler', 'transmission']),
        ('Auto parts', ['auto parts', 'autozone', 'advance auto', 'napa', "o'reilly"]),
        ('Banking', ['bank', 'credit union', 'atm', 'trust']),
        ('Insurance', ['insurance', 'allstate', 'state farm']),
        ('Real estate', ['real estate', 'realty', 'century 21', 'coldwell']),
        ('Fitness center', ['fitness', 'gym', 'yoga', 'crossfit', 'planet fitness']),
        ('Public transit', ['mbta', 'train', 'bus', 'ferry', 'transit', 'commuter']),
        ('Marine services', ['propeller', 'marine', 'boat', 'charter']),
        ('Self storage', ['storage', 'u-haul']),
        ('Dental care', ['dental', 'dentist', 'orthodont']),
        ('Veterinary', ['vet', 'animal hospital', 'creature']),
        ('Legal services', ['law ', 'attorney', 'legal']),
        ('Accounting', ['account', 'tax', 'cpa']),
        ('Printing', ['print', 'copy', 'fedex', 'ups store']),
        ('Rideshare', ['skipper', 'rideshare', 'uber', 'lyft']),
        ('Daycare', ['daycare', 'child care', 'preschool']),
        ('Tutoring', ['tutor', 'kumon', 'learning center']),
        ('Funeral services', ['funeral', 'mortuary', 'memorial']),
        ('Moving services', ['moving', 'movers']),
        ('Photography', ['photo', 'portrait']),
        ('Nail salon', ['nail']),
        ('Spa', ['spa', 'massage', 'wellness']),
    ]
    for stype, kws in patterns:
        for kw in kws:
            if kw in nl:
                return stype
    return None

def infer_venue_type(name, desc):
    """Infer venue type from name."""
    combined = (name + ' ' + (desc or '')).lower()
    patterns = [
        ('Theater and live entertainment', ['theater', 'theatre', 'cinema', 'film', 'stage']),
        ('Church', ['church', 'parish', 'chapel', 'congregation', 'tabernacle']),
        ('Wharf and waterfront', ['wharf', 'pier', 'dock', 'marina', 'landing']),
        ('Event hall', ['hall ', 'ballroom', 'banquet']),
        ('Music venue', ['music', 'concert', 'stage', 'lounge']),
        ('Sports facility', ['stadium', 'arena', 'field', 'rink', 'court']),
        ('Historic building', ['historic', 'old town', 'custom house']),
        ('School', ['school', 'academy', 'university', 'college']),
        ('Brewery taproom', ['brewery', 'taproom', 'brewing']),
    ]
    for vtype, kws in patterns:
        for kw in kws:
            if kw in combined:
                return vtype
    return None


# ============================================================
# SQL HELPERS
# ============================================================

def escape_sql(s):
    if s is None:
        return 'NULL'
    return "'" + s.replace("'", "''") + "'"


# ============================================================
# TEASER GENERATORS BY TYPE
# ============================================================

def generate_teaser(name, ntype, address, cuisine_type, desc, hist_note, existing_short, has_long):
    """Generate an ultra-short teaser narration. 15-25 words, complete thoughts only."""

    street = extract_street(address)
    loc = f" on {street}" if street else ""
    detail = extract_best_sentence(desc, name)
    hist = clean_text(hist_note)

    # For Wave 1 POIs with existing long narrations, distill existing short
    if has_long and existing_short:
        first = _first_complete_sentence(existing_short, 25)
        if first and len(first.split()) >= 8:
            return first

    # Dispatch by type
    generators = {
        'witch_museum': _gen_witch_museum,
        'museum': _gen_museum,
        'historic_site': _gen_historic,
        'witch_shop': _gen_witch_shop,
        'psychic': _gen_psychic,
        'ghost_tour': _gen_ghost_tour,
        'haunted_attraction': _gen_haunted,
        'restaurant': _gen_restaurant,
        'cafe': _gen_cafe,
        'bar': _gen_bar,
        'brewery': _gen_bar,
        'tour': _gen_tour,
        'attraction': _gen_attraction,
        'venue': _gen_venue,
        'shop': _gen_shop,
        'park': _gen_park,
        'lodging': _gen_lodging,
        'hotel': _gen_lodging,
        'medical': _gen_medical,
        'public': _gen_public,
        'government': _gen_public,
        'library': _gen_public,
        'visitor_info': _gen_public,
        'place_of_worship': _gen_worship,
        'cemetery': _gen_cemetery,
        'public_art': _gen_public_art,
        'community_center': _gen_community,
        'services': _gen_services,
        'other': _gen_other,
    }

    gen = generators.get(ntype, _gen_fallback)
    teaser = gen(name, loc, street, detail, hist, desc, cuisine_type)

    # Enforce minimum word count — pad with useful context if too short
    teaser = _ensure_minimum(teaser, name, ntype, loc, street)
    # Enforce maximum — clean truncation at sentence/clause boundary
    teaser = _enforce_maximum(teaser, 25)
    return teaser


# Type-specific padding for short narrations
_TYPE_PADDING = {
    'restaurant': 'Local restaurant in walking distance',
    'cafe': 'Stop in for a warm drink on your walk',
    'bar': 'Drinks and atmosphere',
    'brewery': 'Local craft brewery',
    'shop': 'Browse inside while exploring Salem',
    'witch_shop': 'Witchcraft supplies, crystals, and readings',
    'psychic': 'Psychic readings available',
    'museum': 'Exhibits and displays worth exploring',
    'witch_museum': 'Witch trial history and exhibits',
    'historic_site': 'Historic landmark from Salem\'s past',
    'tour': 'Guided tours of Salem available',
    'ghost_tour': 'Ghost and haunted history tour',
    'haunted_attraction': 'Haunted attraction with scares and thrills',
    'attraction': 'Local Salem attraction worth a stop',
    'venue': 'Events and entertainment venue',
    'park': 'Green space, a good rest stop on your walk',
    'lodging': 'Overnight accommodations in Salem',
    'hotel': 'Hotel stay in Salem',
    'medical': 'Healthcare services available',
    'public': 'Public facility',
    'government': 'Government office',
    'cemetery': 'Historic burial ground',
    'public_art': 'Public art worth a look',
    'place_of_worship': 'House of worship, visitors welcome',
    'community_center': 'Community gathering space',
    'services': 'Local business serving Salem visitors and residents',
    'other': 'Local Salem establishment',
    'visitor_info': 'Tourist information and maps',
    'library': 'Public library, free wifi and restrooms',
}

# Service-type specific padding
_SERVICE_PADDING = {
    'Parking': 'Downtown parking for your Salem visit',
    'Barbershop': 'Walk-in haircuts available',
    'Hair salon': 'Hair and beauty services',
    'Tattoo studio': 'Custom tattoo work',
    'Dry cleaning': 'Laundry and dry cleaning services',
    'Auto repair': 'Vehicle repair and maintenance',
    'Auto parts': 'Car parts and supplies',
    'Banking': 'ATM and banking services available',
    'Insurance': 'Insurance services',
    'Real estate': 'Salem real estate office',
    'Fitness center': 'Gym and fitness',
    'Public transit': 'Public transportation connections',
    'Marine services': 'Boat and marine services',
    'Self storage': 'Storage units available',
    'Dental care': 'Dental office',
    'Veterinary': 'Animal care and veterinary services',
    'Legal services': 'Legal services office',
    'Printing': 'Printing and shipping services',
    'Rideshare': 'Local ride service for getting around Salem',
    'Nail salon': 'Nail care and manicures',
    'Spa': 'Relaxation and spa treatments',
    'Photography': 'Professional photography services',
    'Funeral services': 'Funeral and memorial services',
}

def _ensure_minimum(teaser, name, ntype, loc, street):
    """Ensure narration is at least 8 words. Pad with type context if short."""
    if not teaser:
        teaser = name
    words = teaser.split()
    if len(words) >= 8:
        return teaser

    # Try adding location if not already present
    if street and street.lower() not in teaser.lower() and loc:
        candidate = teaser.rstrip('.') + loc
        if len(candidate.split()) >= 8:
            return candidate

    # Choose padding
    padding = _TYPE_PADDING.get(ntype, 'Local Salem establishment')

    # For services, use more specific padding
    if ntype == 'services':
        stype = infer_service_type(name)
        if stype and stype in _SERVICE_PADDING:
            padding = _SERVICE_PADDING[stype]

    # Avoid redundancy: don't add padding if key words already in teaser
    teaser_lower = teaser.lower()
    padding_keywords = [w for w in padding.lower().split() if len(w) > 3]
    overlap = sum(1 for kw in padding_keywords if kw in teaser_lower)
    if overlap >= len(padding_keywords) // 2 + 1:
        # Padding is redundant — try location + generic context instead
        if loc and street and street.lower() not in teaser_lower:
            padding = "On your walk through Salem"
        else:
            padding = "In the heart of Salem"

    # Build padded narration, try location first
    if loc and street and street.lower() not in teaser_lower:
        result = teaser.rstrip('.') + loc + '. ' + padding
    else:
        result = teaser.rstrip('.') + '. ' + padding
    return result

def _enforce_maximum(teaser, max_words=25):
    """Truncate to max_words at a clean boundary (sentence end or clause)."""
    if not teaser:
        return teaser
    words = teaser.split()
    if len(words) <= max_words:
        return teaser

    # Try to find a sentence boundary within the limit
    text = ' '.join(words[:max_words + 5])  # Look a bit ahead
    sentences = re.split(r'(?<=[.!?])\s+', text)
    result = ''
    for s in sentences:
        candidate = (result + ' ' + s).strip() if result else s
        if len(candidate.split()) <= max_words:
            result = candidate
        else:
            break
    if result and len(result.split()) >= 8:
        return result.rstrip('.')

    # Fall back: truncate at last comma or clause within limit
    truncated = ' '.join(words[:max_words])
    # Find last comma or semicolon
    last_break = max(truncated.rfind(','), truncated.rfind(';'), truncated.rfind(' –'), truncated.rfind(' —'))
    if last_break > len(truncated) // 2:
        return truncated[:last_break].rstrip('.,;–— ')

    return truncated.rstrip('.,;–— ')


def _first_complete_sentence(text, max_words=25):
    """Extract first complete sentence under max_words."""
    if not text:
        return None
    text = clean_text(text)
    # Find first sentence end
    m = re.search(r'[.!?]', text)
    if m:
        sentence = text[:m.start()].strip()
        words = sentence.split()
        if 5 <= len(words) <= max_words:
            return sentence
    # No sentence boundary found, take first clause
    m = re.search(r'[,;–—]', text)
    if m and m.start() > 15:
        clause = text[:m.start()].strip()
        words = clause.split()
        if 5 <= len(words) <= max_words:
            return clause
    return None


# --- Individual type generators ---

def _gen_witch_museum(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    if hist:
        return f"{name}. {hist}"
    return f"{name}{loc}. Salem witch trial history brought to life through exhibits and reenactments"

def _gen_museum(name, loc, street, detail, hist, desc, cuisine):
    if hist:
        return f"{name}. {hist}"
    if detail:
        return f"{name}. {detail}"
    return f"{name}{loc}. Museum and exhibits worth exploring"

def _gen_historic(name, loc, street, detail, hist, desc, cuisine):
    if hist:
        return f"{name}. {hist}"
    if detail:
        return f"{name}. {detail}"
    return f"{name}{loc}. Historic Salem landmark"

def _gen_witch_shop(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    # Mine for offerings
    if desc:
        offerings = _find_offerings(desc, ['herbs', 'crystals', 'tarot', 'candles',
                                            'incense', 'books', 'spell kits', 'oils',
                                            'readings', 'altar tools', 'jewelry',
                                            'potions', 'wands', 'magick supplies'])
        if offerings:
            return f"{name}{loc}. {offerings}"
    return f"{name}{loc}. Witchcraft supplies, readings, and ritual tools"

def _gen_psychic(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    if desc:
        specialties = _find_offerings(desc, ['tarot', 'palm reading', 'mediumship',
                                              'aura', 'clairvoyant', 'astrology',
                                              'past life', 'rune', 'crystal ball'])
        if specialties:
            return f"{name}{loc}. {specialties}"
    return f"{name}{loc}. Psychic readings and spiritual guidance"

def _gen_ghost_tour(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    return f"{name}. Ghost tour through Salem's most haunted locations"

def _gen_haunted(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    return f"{name}{loc}. Haunted attraction with scares and thrills"

def _gen_restaurant(name, loc, street, detail, hist, desc, cuisine):
    c = infer_cuisine(name, cuisine)
    if c and detail:
        return f"{c} at {name}. {detail}"
    if c:
        return f"{c} at {name}{loc}"
    if detail:
        return f"{name}. {detail}"
    # No cuisine, no description — be direct
    return f"{name}{loc}. Restaurant and dining"

def _gen_cafe(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"Cafe. {name}. {detail}"
    return f"Cafe. {name}{loc}. Coffee, drinks, and a place to rest your feet"

def _gen_bar(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    return f"{name}{loc}. Bar and drinks"

def _gen_tour(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    return f"{name}. Guided walking tour of Salem's history and landmarks"

def _gen_attraction(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    nl = name.lower()
    if any(kw in nl for kw in ['field', 'court', 'batting cage', 'rink', 'arena']):
        return f"{name}{loc}. Sports and recreation facility"
    if any(kw in nl for kw in ['fitness', 'gym', 'ymca', 'planet fitness']):
        return f"{name}{loc}. Gym and fitness center"
    if any(kw in nl for kw in ['statue', 'sculpture', 'monument', 'memorial']):
        return f"{name}{loc}. Public monument worth a photo"
    if any(kw in nl for kw in ['festival', 'fair', 'event']):
        return f"{name}. Seasonal event and entertainment in Salem"
    if any(kw in nl for kw in ['photo', 'portrait']):
        return f"{name}{loc}. Photography and portrait sessions in Salem"
    return f"{name}{loc}. Salem attraction worth a stop"

def _gen_venue(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    vtype = infer_venue_type(name, desc)
    if vtype:
        return f"{name}{loc}. {vtype}"
    return f"{name}{loc}. Events and entertainment"

def _gen_shop(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    specialty = infer_shop_specialty(name, desc)
    if specialty:
        return f"{name}{loc}. {specialty}"
    return f"{name}{loc}. Shop and browse"

def _gen_park(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    nl = name.lower()
    if 'beach' in nl or 'cove' in nl:
        return f"{name}. Waterfront area to stretch your legs and enjoy the view"
    if 'common' in nl:
        return f"{name}. Salem's historic public green, used as common land since the sixteen thirties"
    if 'playground' in nl:
        return f"{name}. Playground and family-friendly green space"
    if 'field' in nl or 'athletic' in nl:
        return f"{name}. Athletic fields and recreation"
    if 'garden' in nl:
        return f"{name}. Garden space for a quiet moment on your walk"
    return f"{name}{loc}. Park and green space, a good spot to rest on your walk"

def _gen_lodging(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    nl = name.lower()
    if 'bed' in nl or 'b&b' in nl or 'inn' in nl:
        return f"{name}{loc}. Bed and breakfast lodging in Salem"
    if 'hotel' in nl or 'suites' in nl:
        return f"{name}{loc}. Hotel accommodations in Salem"
    return f"{name}{loc}. Lodging and overnight stays in Salem"

def _gen_medical(name, loc, street, detail, hist, desc, cuisine):
    nl = name.lower()
    if 'pharmacy' in nl or 'cvs' in nl or 'walgreen' in nl:
        return f"Pharmacy at {name}{loc}. Prescriptions and health supplies"
    if 'hospital' in nl or 'medical center' in nl:
        return f"{name}. Emergency room and medical care available nearby"
    if 'dental' in nl or 'dentist' in nl:
        return f"Dental office. {name}{loc}"
    if 'eye' in nl or 'vision' in nl or 'optical' in nl:
        return f"Eye care at {name}{loc}"
    if detail:
        return f"{name}. {detail}"
    return f"Medical services at {name}{loc}"

def _gen_public(name, loc, street, detail, hist, desc, cuisine):
    nl = name.lower()
    if detail:
        return f"{name}. {detail}"
    if 'library' in nl:
        return f"{name}{loc}. Public library, open to visitors, free wifi and restrooms"
    if 'fire' in nl and ('department' in nl or 'station' in nl):
        return f"{name}{loc}. Salem fire department, emergency services"
    if 'police' in nl:
        return f"{name}{loc}. Police department, emergency services"
    if any(kw in nl for kw in ['elementary', 'middle school', 'junior high']):
        return f"{name}{loc}. Neighborhood school in the Salem public school system"
    if 'charter' in nl and 'school' in nl:
        return f"{name}{loc}. Charter school serving the Salem community"
    if any(kw in nl for kw in ['saint', 'st.']) and 'school' in nl:
        return f"{name}{loc}. Parochial school in the Salem community"
    if any(kw in nl for kw in ['school', 'academy']):
        return f"{name}{loc}. School serving the Salem community"
    if any(kw in nl for kw in ['university', 'college']):
        return f"{name}{loc}. Higher education campus, visitors welcome on the grounds"
    if 'parking' in nl or 'garage' in nl or ' lot' in nl:
        return f"Parking at {name}{loc}. Downtown parking for your Salem visit"
    if 'post office' in nl:
        return f"U.S. Post Office at {name}{loc}. Mail and package services"
    if 'visitor' in nl or 'information' in nl or 'welcome' in nl:
        return f"{name}. Tourist information, maps, and local recommendations"
    if 'city hall' in nl or 'town hall' in nl:
        return f"{name}{loc}. Municipal government, open to the public during business hours"
    if 'court' in nl:
        return f"{name}{loc}. Salem courthouse"
    return f"{name}{loc}. Public building in the heart of Salem"

def _gen_worship(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    nl = name.lower()
    if 'church' in nl or 'parish' in nl:
        return f"{name}{loc}. Historic Salem church, visitors welcome"
    if 'temple' in nl:
        return f"{name}{loc}. Temple"
    if 'synagogue' in nl:
        return f"{name}{loc}. Synagogue"
    return f"{name}{loc}. House of worship, visitors welcome"

def _gen_cemetery(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    nl = name.lower()
    if 'charter' in nl:
        return f"{name}. Salem's oldest burial ground, established sixteen thirty-seven. Graves of witch trial judges and Mayflower descendants"
    if 'broad' in nl:
        return f"{name}. Historic cemetery with notable Salem family plots"
    if 'howard' in nl:
        return f"{name}. Nineteenth-century garden cemetery with Victorian monuments"
    return f"{name}{loc}. Historic burial ground"

def _gen_public_art(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    return f"{name}{loc}. Public art installation"

def _gen_community(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    return f"{name}{loc}. Community center and gathering space"

def _gen_services(name, loc, street, detail, hist, desc, cuisine):
    stype = infer_service_type(name)
    if detail:
        if stype:
            return f"{stype}. {name}. {detail}"
        return f"{name}. {detail}"
    if stype:
        return f"{stype}. {name}{loc}"
    return f"{name}{loc}"

def _gen_other(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    nl = name.lower()
    if 'parking' in nl or 'garage' in nl or ' lot' in nl:
        return f"Parking at {name}{loc}. Downtown parking for your Salem visit"
    if 'fuel' in nl or 'gas' in nl:
        return f"Gas station. {name}{loc}. Fuel up before heading out"
    if 'bike' in nl:
        return f"{name}{loc}. Bike rental and cycling in Salem"
    if 'field' in nl or 'arena' in nl or 'gymnasium' in nl or 'rink' in nl:
        return f"{name}{loc}. Sports and recreation facility"
    if 'tax' in nl or 'h&r' in nl:
        return f"Tax services. {name}{loc}"
    if 'insurance' in nl:
        return f"Insurance office. {name}{loc}"
    if 'realty' in nl or 'real estate' in nl:
        return f"Real estate office. {name}{loc}"
    if 'arts' in nl or 'gallery' in nl:
        return f"{name}{loc}. Arts and creative space in Salem"
    if 'catholic' in nl or 'ministry' in nl or 'ministries' in nl:
        return f"{name}{loc}. Faith-based community organization"
    if 'wealth' in nl or 'financial' in nl or 'investment' in nl:
        return f"Financial services. {name}{loc}"
    if 'bowl' in nl or 'bowling' in nl:
        return f"{name}{loc}. Bowling alley and recreation"
    if 'train' in nl or 'mbta' in nl or 'transit' in nl:
        return f"Public transit. {name}{loc}. Connections to Boston and the North Shore"
    return f"{name}{loc}. Local Salem establishment on your walking route"

def _gen_fallback(name, loc, street, detail, hist, desc, cuisine):
    if detail:
        return f"{name}. {detail}"
    return f"{name}{loc}"


# --- Helpers ---

def _find_offerings(desc, keywords):
    """Find which offerings appear in a description and return readable string."""
    if not desc:
        return None
    dl = desc.lower()
    found = [kw for kw in keywords if kw in dl]
    if len(found) >= 3:
        return found[0].capitalize() + ', ' + found[1] + ', ' + found[2] + ', and more'
    elif len(found) == 2:
        return found[0].capitalize() + ' and ' + found[1]
    elif len(found) == 1:
        return found[0].capitalize() + ' available'
    return None


# ============================================================
# MAIN
# ============================================================

def main():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row

    rows = conn.execute("""
        SELECT n.id, n.name, n.type, n.address, n.short_narration,
               n.long_narration, n.wave, n.priority,
               b.business_type, b.cuisine_type, b.description, b.historical_note
        FROM narration_points n
        LEFT JOIN salem_businesses b ON n.name = b.name
        GROUP BY n.id
        ORDER BY n.name
    """).fetchall()

    print(f"Processing {len(rows)} narration points...\n")

    promote_stmts = []
    teaser_stmts = []
    stats = {'total': 0, 'promoted': 0, 'with_desc': 0, 'word_counts': []}

    for row in rows:
        nid = row['id']
        name = row['name']
        ntype = row['type']
        address = row['address']
        existing_short = row['short_narration']
        existing_long = row['long_narration']
        cuisine = row['cuisine_type']
        desc = row['description']
        hist = row['historical_note']

        stats['total'] += 1

        # Step 1: Promote short → long where long is NULL
        if existing_short and not existing_long:
            promote_stmts.append(
                f"UPDATE narration_points SET long_narration = {escape_sql(existing_short)} WHERE id = {escape_sql(nid)};"
            )
            stats['promoted'] += 1

        # Step 2: Generate teaser
        has_long = existing_long is not None
        teaser = generate_teaser(name, ntype, address, cuisine, desc, hist,
                                  existing_short, has_long)

        if teaser:
            wc = len(teaser.split())
            stats['word_counts'].append(wc)
            if desc:
                stats['with_desc'] += 1
            teaser_stmts.append(
                f"UPDATE narration_points SET short_narration = {escape_sql(teaser)} WHERE id = {escape_sql(nid)};"
            )

    # Write promote SQL
    promote_path = os.path.join(OUT_DIR, 'promote_short_to_long.sql')
    with open(promote_path, 'w') as f:
        f.write("-- Promote current short_narration → long_narration where long is NULL\n")
        f.write(f"-- {len(promote_stmts)} POIs affected\n\n")
        f.write("BEGIN TRANSACTION;\n\n")
        for stmt in promote_stmts:
            f.write(stmt + "\n")
        f.write("\nCOMMIT;\n")

    # Write teaser SQL
    teaser_path = os.path.join(OUT_DIR, 'teaser_narrations.sql')
    with open(teaser_path, 'w') as f:
        f.write("-- Ultra-short teaser narrations (~10 sec TTS) for all POIs\n")
        f.write(f"-- {len(teaser_stmts)} POIs updated\n")
        f.write(f"-- Target: 15-25 words per narration\n\n")
        f.write("BEGIN TRANSACTION;\n\n")
        for stmt in teaser_stmts:
            f.write(stmt + "\n")
        f.write("\nCOMMIT;\n")

    # Print stats
    wc = stats['word_counts']
    avg_wc = sum(wc) / len(wc) if wc else 0
    min_wc = min(wc) if wc else 0
    max_wc = max(wc) if wc else 0
    under_8 = sum(1 for w in wc if w < 8)
    over_25 = sum(1 for w in wc if w > 25)

    print(f"--- Results ---")
    print(f"Total POIs:         {stats['total']}")
    print(f"Promoted to long:   {stats['promoted']}")
    print(f"Teasers generated:  {len(teaser_stmts)}")
    print(f"With descriptions:  {stats['with_desc']}")
    print(f"Word count — avg: {avg_wc:.1f}, min: {min_wc}, max: {max_wc}")
    print(f"Under 8 words:      {under_8}")
    print(f"Over 25 words:      {over_25}")

    # Print some samples
    print(f"\n--- Samples ---")
    import random
    sample_stmts = random.sample(teaser_stmts, min(30, len(teaser_stmts)))
    for stmt in sorted(sample_stmts):
        # Extract the narration text from the SQL
        m = re.search(r"short_narration = '(.+?)' WHERE", stmt)
        if m:
            text = m.group(1).replace("''", "'")
            wc = len(text.split())
            print(f"  [{wc:2d}w] {text}")

    print(f"\nOutput files:")
    print(f"  {promote_path}")
    print(f"  {teaser_path}")

if __name__ == '__main__':
    main()
