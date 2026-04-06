#!/usr/bin/env python3
"""
Generate narration content SQL for Wave 2 (85) and Wave 3 (109) POIs.

Strategy:
- POIs WITH descriptions: adapt description into ~50-80 word narration (20-30 sec TTS)
- POIs WITHOUT descriptions: craft ~25-40 word narration (10 sec TTS) from name/category

Outputs SQL UPDATE statements to narration_content.sql
"""

import json
import os
import re
import textwrap

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
INPUT_FILE = os.path.join(SCRIPT_DIR, "narration-priority-pois.json")
OUTPUT_FILE = os.path.join(SCRIPT_DIR, "..", "..", "salem-content", "narration_content.sql")

WAVE_MAP = {
    "witch_museum": 1, "witch_shop": 1, "psychic": 1, "ghost_tour": 1,
    "haunted_attraction": 1, "museum": 1, "historic_site": 1, "public_art": 1,
    "cemetery": 1, "tour": 1, "visitor_info": 1,
    "attraction": 2, "park": 2, "place_of_worship": 2, "lodging": 2,
    "hotel": 2, "brewery": 2, "bar": 2,
    "restaurant": 3, "cafe": 3, "community_center": 3, "government": 3, "library": 3,
}

# ═══════════════════════════════════════════════════════════════════
# Manual narrations for POIs without descriptions
# These are crafted from name, category, and Salem context
# ═══════════════════════════════════════════════════════════════════

MANUAL_NARRATIONS = {}

def m(name, narration):
    """Register a manual narration for a POI without a description."""
    MANUAL_NARRATIONS[name] = narration

# --- Wave 2: Attractions without descriptions ---
m("Bell", "You're near Bell, a local Salem venue. Part of the fabric of this historic waterfront city.")
m("Derby Waterfront District", "You're entering the Derby Waterfront District, Salem's historic harbor front. This area was the heart of Salem's global maritime trade in the eighteenth century, when the city was one of the wealthiest ports in America.")

# --- Wave 2: Parks ---
m("Armory Park", "You're passing Armory Park, a neighborhood green space in Salem named for the nearby historic armory building.")
m("David J. Beattie Park", "You're near David J. Beattie Park, a quiet neighborhood park in Salem.")
m("Gonyea Park", "You're passing Gonyea Park, one of Salem's smaller neighborhood green spaces.")
m("Hatches Wharf", "You're at Hatches Wharf, a waterfront park along Derby Street with views of Salem Harbor.")
m("High Street Park", "You're near High Street Park, a neighborhood green space in Salem's residential area.")
m("Irzyk Park", "You're passing Irzyk Park, named after a Salem veteran. A small community park.")
m("Lafayette Park", "You're near Lafayette Park on Lafayette Street, one of Salem's downtown green spaces.")
m("Mary Jane Lee Park", "You're passing Mary Jane Lee Park, a community park in Salem's Point neighborhood.")
m("Palmer Cove Park", "You're near Palmer Cove Park, a waterfront park on Salem's eastern shore with ball fields and harbor views.")
m("Peabody Street Park", "You're passing Peabody Street Park, a small neighborhood park in Salem's Point area.")
m("Riley Plaza", "You're at Riley Plaza, a public square in downtown Salem near the commuter rail station.")
m("Swiniuch Park", "You're passing Swiniuch Park, a small community green space in Salem.")

# --- Wave 2: Places of worship ---
m("Immaculate Conception Church", "You're near Immaculate Conception Church on Hawthorne Boulevard, a Roman Catholic parish that has served Salem's community for generations.")
m("Ministerios Casa De Oración", "You're near Ministerios Casa De Oración, a Spanish-language church serving Salem's Latino community.")
m("Saint Nicholas Orthodox Church", "You're near Saint Nicholas Orthodox Church on Forrester Street, reflecting the diverse immigrant communities that have called Salem home.")
m("St. John The Baptist Ukrainian Church", "You're near St. John The Baptist Ukrainian Church, one of Salem's heritage congregations reflecting the city's immigrant history.")
m("Wesley United Methodist Church", "You're near Wesley United Methodist Church on North Street, a congregation with deep roots in Salem's religious history.")

# --- Wave 2: Lodging ---
m("Fidelia Bridges Guest House", "You're near the Fidelia Bridges Guest House on Essex Street, a Salem bed and breakfast named after the renowned nineteenth-century nature painter who lived in the city.")

# --- Wave 2: Hotels ---
m("Lafeyette Hotel", "You're near the Lafayette Hotel, a lodging option in Salem's downtown area.")

# --- Wave 2: Bars ---
m("All Souls Lounge", "You're near All Souls Lounge, a cocktail bar in Salem with a moody atmosphere perfect for the city's witchy reputation.")
m("Regatta Pub", "You're near Regatta Pub on Derby Street at Pickering Wharf, a casual waterfront pub.")
m("The Roof", "You're near The Roof on Essex Street, a rooftop bar at the Hotel Salem with views over downtown.")
m("VFW", "You're passing the VFW on Derby Street, the local Veterans of Foreign Wars post. A no-frills spot where locals gather.")

# --- Wave 3: Restaurants without descriptions ---
m("Antique Table", "You're near Antique Table on Congress Street, a Salem dining spot.")
m("Back Alley Bacon", "You're near Back Alley Bacon on Liberty Street, a Salem eatery with a name that promises indulgence.")
m("Bambolina", "You're near Bambolina on Derby Street, an Italian-inspired restaurant on Salem's waterfront.")
m("Barrio Tacos", "You're near Barrio Tacos, serving Mexican street food-inspired tacos here in Salem.")
m("Bella Verona", "You're near Bella Verona on Essex Street, an Italian restaurant in the heart of Salem's pedestrian mall.")
m("Blue Fez Moroccan Cuisine", "You're near Blue Fez, bringing Moroccan cuisine to Salem. A taste of North Africa on the North Shore.")
m("Boston Burger Company", "You're near Boston Burger Company on Washington Street, known for creative over-the-top burger combinations.")
m("Caramel", "You're near Caramel on Essex Street, a patisserie and sweets shop in Salem's pedestrian district.")
m("Casa Tequila", "You're near Casa Tequila on Derby Street, a Mexican restaurant on Salem's waterfront.")
m("Crave", "You're near Crave, a local Salem dining spot.")
m("Dotty and Ray's", "You're near Dotty and Ray's on North Street, a neighborhood restaurant in Salem.")
m("Dunkin'", "You're near Dunkin' on Washington Street, a reliable stop for coffee and donuts on your Salem walk.")
m("Engine House Pizza", "You're near Engine House Pizza, a local pizzeria here in Salem.")
m("Finz", "You're near Finz, a popular seafood spot on Salem's waterfront.")
m("Fountain Place", "You're near Fountain Place on Essex Street in Salem's pedestrian district.")
m("Ginger Cajun Seafood & Bar", "You're near Ginger Cajun Seafood and Bar, bringing Louisiana flavors to Salem.")
m("Good Night, Fatty", "You're near Good Night, Fatty at Washington Square, a late-night food spot in Salem.")
m("Howling Wolf Taqueria", "You're near Howling Wolf Taqueria on Lafayette Street, serving tacos and Mexican favorites with a Salem twist.")
m("Koto Grill & Sushi", "You're near Koto Grill and Sushi, offering Japanese cuisine in downtown Salem.")
m("La Delicia", "You're near La Delicia on Congress Street, a Latin American restaurant in Salem.")
m("Ledger", "You're near Ledger, a restaurant housed in one of Salem's historic bank buildings.")
m("Life Alive Urban Oasis & Organic Cafe", "You're near Life Alive on Essex Street, offering organic and plant-based meals for health-conscious visitors.")
m("Lil' Devil's Deli", "You're near Lil' Devil's Deli on Congress Street, a sandwich shop with a devilish Salem flair.")
m("Maitland Farm", "You're near Maitland Farm on Derby Street, a farm-to-table restaurant on Salem's waterfront.")
m("Maria's Sweet Somethings", "You're near Maria's Sweet Somethings on Front Street, a bakery and sweet shop in Salem.")
m("Maria\u2019s Sweet Somethings", "You're near Maria's Sweet Somethings on Front Street, a bakery and sweet shop in Salem.")
m("Melt", "You're near Melt, a local Salem eatery.")
m("Mercy Tavern", "You're near Mercy Tavern on Derby Street, a pub named in the spirit of Salem's history. Mercy was a common name among accused witches in 1692.")
m("Ministry Of Donuts", "You're near Ministry Of Donuts, serving creative donuts here in Salem.")
m("Nat's", "You're near Nat's, a local dining spot in Salem.")
m("O'Neill's", "You're near O'Neill's, an Irish pub in Salem's downtown.")
m("Passage to India", "You're near Passage to India on Washington Street, bringing Indian cuisine to downtown Salem.")
m("Piccolo piatti", "You're near Piccolo Piatti on Webb Street. The name means small plates in Italian.")
m("Poblano", "You're near Poblano on Lafayette Street, a Mexican restaurant in Salem.")
m("Rayadea's", "You're near Rayadea's on Lafayette Street, a local Salem restaurant.")
m("Rockefellas", "You're near Rockafellas on Essex Street, a longtime Salem favorite for dining and nightlife.")
m("Roseadella's", "You're near Roseadella's, a local café and bakery in Salem.")
m("O'Neill's", "You're near O'Neill's, an Irish pub in Salem's downtown.")
m("Rayadea's", "You're near Rayadea's on Lafayette Street, a local Salem restaurant.")
m("Salem Tipico", "You're near Salem Tipico on Congress Street, offering Latin American flavors in Salem.")
m("Salem's Retreat", "You're near Salem's Retreat, a local dining establishment.")
m("Super Slice Pizza", "You're near Super Slice Pizza, serving quick slices in Salem.")
m("Tavern on the Green", "You're near Tavern on the Green, a dining spot in Salem.")
m("The Juicery", "You're near The Juicery, offering fresh juices and healthy options in Salem.")
m("Tipsy Cowboy", "You're near Tipsy Cowboy, a Western-themed bar and restaurant in Salem.")
m("Ugly Mug Diner", "You're near Ugly Mug Diner, a casual breakfast and lunch spot in Salem.")
m("Wendy's", "You're near Wendy's on Lafayette Street, a fast food option on the edge of Salem's downtown.")
m("WitchSide Tavern", "You're near WitchSide Tavern on Derby Street, a pub on Salem's waterfront strip.")
m("Ziggy & Sons Donuts", "You're near Ziggy and Sons Donuts, a donut shop in Salem.")

# --- Wave 3: Cafes without descriptions ---
m("Au Gratin", "You're near Au Gratin, a café in Salem.")
m("Brew Box", "You're near Brew Box on Essex Street, a coffee spot on Salem's pedestrian mall.")
m("Honey Dew", "You're near Honey Dew, a New England coffee and donut chain.")
m("Jaho Cafe", "You're near Jaho Cafe on Derby Street, one of Salem's beloved local coffee roasters.")
m("New England Soup Factory", "You're near New England Soup Factory, offering hearty soups perfect for a cool Salem day.")
m("Odd Meter", "You're near Odd Meter on Washington Street, a café in downtown Salem.")
m("Polonus Deli", "You're near Polonus Deli on Essex Street, a Polish deli reflecting Salem's diverse immigrant community.")
m("Red Line Cafe", "You're near Red Line Cafe, a local Salem coffee spot.")
m("Roseadella's", "You're near Roseadella's, a local café in Salem.")
m("Starbucks", "You're near the Starbucks on Washington Street, if you need a familiar coffee stop during your Salem walk.")

# --- Wave 3: Community centers ---
m("Columbus Society of Salem", "You're near the Columbus Society of Salem on Commercial Street, an Italian-American social organization reflecting Salem's heritage.")
m("Espacio El Punto", "You're near Espacio El Punto, a community space serving Salem's Latino neighborhood in the Point area.")
m("Polish League of American Veterans", "You're near the Polish League of American Veterans on Daniels Street, reflecting the Polish community that has been part of Salem for generations.")
m("Salem Pantry", "You're near the Salem Pantry on Leavitt Street, a food pantry serving Salem's community.")
m("The Salvation Army North Shore Community Center & Church", "You're near the Salvation Army North Shore Community Center on North Street, serving Salem's community.")

# --- Wave 3: Government ---
m("Essex County Court Buildings", "You're near the Essex County Court Buildings, part of Salem's judicial district. Salem has been a seat of justice in Essex County since colonial times.")
m("Salem City Fire Department", "You're near the Salem City Fire Department, protecting this historic city.")
m("Salem District Court", "You're near Salem District Court on Federal Street, continuing Salem's long history as a center of law in Essex County.")
m("Salem Fire Department", "You're near the Salem Fire Department on Lafayette Street, first organized in the early 1800s to protect a city that learned hard lessons from the Great Fire of 1914.")
m("Salem Police Department", "You're near the Salem Police Department on Margin Street, keeping order in a city that welcomes over a million visitors each October.")

# --- Wave 3: Libraries ---
m("Essex Law Library", "You're near the Essex Law Library on Federal Street, a legal research library serving attorneys and the public in Essex County.")
m("Stephen Phillips Archives", "You're near the Stephen Phillips Archives on Washington Street, which holds records of Salem families, the East India maritime trade, and other local history.")


def sql_escape(text):
    """Escape text for SQL string literal."""
    text = text.replace("\r\n", " ").replace("\r", " ").replace("\n", " ")
    text = re.sub(r"  +", " ", text)
    text = text.replace("'", "''")
    return text


def trim_to_words(text, max_words):
    """Trim text to max_words, ending at a sentence boundary if possible."""
    words = text.split()
    if len(words) <= max_words:
        return text
    # Try to end at a sentence boundary
    trimmed = " ".join(words[:max_words])
    # Find last sentence-ending punctuation
    for end in [". ", "! ", "? "]:
        idx = trimmed.rfind(end)
        if idx > len(trimmed) // 2:  # Don't cut too short
            return trimmed[:idx + 1]
    # No good sentence boundary; just trim at word boundary
    return trimmed.rstrip(",;:") + "."


def generate_narration_from_description(name, category, description, address):
    """Generate a short narration from an existing description."""
    desc = description.strip()
    if not desc:
        return None

    # Clean up the description
    desc = re.sub(r'\s+', ' ', desc)

    # Determine narration length based on description richness
    word_count = len(desc.split())

    if word_count > 60:
        # Rich description → 20-30 sec narration (~50-80 words)
        # Use first ~70 words, adapted
        core = trim_to_words(desc, 70)
    else:
        # Short description → use most of it (~40-60 words)
        core = trim_to_words(desc, 55)

    # Add a narration prefix based on category
    cat_prefixes = {
        "attraction": f"You're approaching {name}.",
        "brewery": f"You're near {name}.",
        "hotel": f"You're near {name}.",
        "lodging": f"You're near {name}.",
        "park": f"You're passing {name}.",
        "place_of_worship": f"You're near {name}.",
        "bar": f"You're near {name}.",
        "restaurant": f"You're near {name}.",
        "cafe": f"You're near {name}.",
        "community_center": f"You're near {name}.",
        "government": f"You're near {name}.",
        "library": f"You're near {name}.",
    }
    prefix = cat_prefixes.get(category, f"You're near {name}.")

    # Don't repeat the name if description already starts with it
    if desc.lower().startswith(name.lower()):
        remainder = desc[len(name):].lstrip(" .,:-–—")
        # Strip orphaned copula verbs left after name removal
        for verb in ["is ", "are ", "has ", "was ", "were ", "offers "]:
            if remainder.lower().startswith(verb):
                remainder = remainder[len(verb):]
                # Capitalize first letter
                if remainder:
                    remainder = remainder[0].upper() + remainder[1:]
                break
        narration = f"{prefix} {remainder}"
    else:
        narration = f"{prefix} {desc}"

    # Trim to reasonable length
    narration = trim_to_words(narration, 80)

    return narration


def main():
    with open(INPUT_FILE) as f:
        data = json.load(f)

    pois = data["pois"]

    # Filter to Wave 2+3 only
    wave23 = [p for p in pois if WAVE_MAP.get(p["category"], 1) in (2, 3)]
    wave23.sort(key=lambda p: (WAVE_MAP.get(p["category"], 1), p["category"], p["name"]))

    sql_lines = []
    sql_lines.append("")
    sql_lines.append("-- ============================================================")
    sql_lines.append("-- Wave 2+3 Narration Content (194 POIs)")
    sql_lines.append("-- Every POI gets at least a 10-second narration")
    sql_lines.append("-- POIs with richer data get 20-30 second narrations")
    sql_lines.append("-- Generated by generate_wave23_narrations.py")
    sql_lines.append("-- ============================================================")
    sql_lines.append("")

    stats = {"manual": 0, "from_desc": 0, "wave2": 0, "wave3": 0}
    current_wave = 0

    for poi in wave23:
        name = poi["name"]
        cat = poi["category"]
        wave = WAVE_MAP.get(cat, 1)
        desc = (poi.get("description") or "").strip()
        addr = (poi.get("address") or "").strip()

        if wave != current_wave:
            current_wave = wave
            sql_lines.append(f"-- Wave {wave}")
            sql_lines.append(f"-- {'=' * 70}")

        # Normalize name for matching (curly apostrophes → straight)
        name_normalized = name.replace("\u2018", "'").replace("\u2019", "'")

        # Generate narration
        if name in MANUAL_NARRATIONS or name_normalized in MANUAL_NARRATIONS:
            narration = MANUAL_NARRATIONS.get(name) or MANUAL_NARRATIONS.get(name_normalized)
            stats["manual"] += 1
        elif desc:
            narration = generate_narration_from_description(name, cat, desc, addr)
            stats["from_desc"] += 1
        else:
            # Fallback: should not happen if MANUAL_NARRATIONS is complete
            narration = f"You're near {name}, located here in Salem."
            stats["manual"] += 1

        if wave == 2:
            stats["wave2"] += 1
        else:
            stats["wave3"] += 1

        escaped_narration = sql_escape(narration)
        escaped_name = sql_escape(name)

        sql_lines.append(f"-- [{cat}] {name}")
        sql_lines.append(
            f"UPDATE narration_points SET short_narration = '{escaped_narration}' "
            f"WHERE name = '{escaped_name}';"
        )
        sql_lines.append("")

    sql_output = "\n".join(sql_lines)

    # Append to narration_content.sql
    with open(OUTPUT_FILE, "a") as f:
        f.write(sql_output)

    print("=" * 70)
    print("Wave 2+3 narration content generation complete")
    print("=" * 70)
    print(f"Total POIs: {len(wave23)}")
    print(f"  Wave 2: {stats['wave2']}")
    print(f"  Wave 3: {stats['wave3']}")
    print(f"  From description: {stats['from_desc']}")
    print(f"  Manual/template: {stats['manual']}")
    print(f"Output appended to: {OUTPUT_FILE}")


if __name__ == "__main__":
    main()
