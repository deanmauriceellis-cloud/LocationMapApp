#!/usr/bin/env python3
"""
Generate SalemBusinessesExpanded.kt from merged-salem-pois.json.

Reads 848 merged POIs and produces a Kotlin seed data file matching
the existing SalemBusinesses.kt pattern with OutputBusiness + Provenance.
"""

import json
import re
import os
from collections import Counter
from datetime import datetime

INPUT_FILE = os.path.join(os.path.dirname(__file__), "merged-salem-pois.json")
OUTPUT_FILE = os.path.join(
    os.path.dirname(__file__),
    "../../salem-content/src/main/kotlin/com/example/salemcontent/data/SalemBusinessesExpanded.kt"
)

# Category mapping from merged POI categories to businessType values
CATEGORY_MAP = {
    "restaurant": "restaurant",
    "cafe": "cafe",
    "bar": "bar",
    "pub": "bar",
    "brewery": "bar",
    "witch_shop": "shop_occult",
    "psychic": "shop_psychic",
    "ghost_tour": "tour_ghost",
    "haunted_attraction": "attraction_haunted",
    "historic_site": "historic",
    "historic_house": "historic",
    "museum": "museum",
    "witch_museum": "museum",
    "shopping": "shop_retail",
    "gift_shop": "shop_retail",
    "lodging": "lodging",
    "hotel": "lodging",
    "attraction": "attraction",
    "event_venue": "venue",
    "park": "park",
    "tour": "tour",
    "cemetery": "historic",
    "public_art": "attraction",
    "recreation": "attraction",
    "library": "public",
    "place_of_worship": "public",
    "visitor_info": "public",
    "community_center": "public",
    "government": "public",
    "school": "public",
    "university": "public",
    "hospital": "medical",
    "medical": "medical",
    "pharmacy": "medical",
    "bank": "services",
    "financial_services": "services",
    "services": "services",
    "parking": "services",
    "gas_station": "services",
    "post_office": "services",
    "cannabis_dispensary": "shop_retail",
    "office": "services",
    "organization": "other",
    "uncategorized": "other",
}

# Source to dataSource mapping
SOURCE_MAP = {
    "destination_salem": "api_sync",
    "osm": "overpass_import",
    "haunted_happenings": "api_sync",
}

# Confidence by source
CONFIDENCE_MAP = {
    "destination_salem": 0.85,
    "osm": 0.75,
    "haunted_happenings": 0.70,
}

VERIFIED_DATE = "2026-04-05"


def slugify(name: str) -> str:
    """Convert a business name to a Kotlin-safe slug ID."""
    s = name.lower()
    # Replace common punctuation with nothing or hyphens
    s = s.replace("'", "").replace("'", "").replace("'", "")
    s = s.replace("&", "and")
    s = s.replace("/", "-")
    s = s.replace("@", "at")
    # Replace non-alphanumeric with hyphens
    s = re.sub(r'[^a-z0-9]+', '-', s)
    # Collapse multiple hyphens
    s = re.sub(r'-+', '-', s)
    # Strip leading/trailing hyphens
    s = s.strip('-')
    # Convert hyphens to underscores for Kotlin ID convention
    s = s.replace('-', '_')
    return s


def escape_kotlin_string(s: str) -> str:
    """Escape a string for use inside Kotlin double quotes."""
    if not s:
        return s
    s = s.replace("\\", "\\\\")
    s = s.replace('"', '\\"')
    s = s.replace("\n", "\\n")
    s = s.replace("\r", "")
    s = s.replace("\t", "\\t")
    s = s.replace("$", "\\$")
    return s


def format_hours_dict(hours_dict: dict) -> str:
    """Convert an hours dict with timestamps into a readable string."""
    days = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]
    parts = []
    for day in days:
        close_key = f"close_{day}"
        if hours_dict.get(close_key, False):
            parts.append(f"{day.capitalize()}: Closed")
        else:
            open_ts = hours_dict.get(f"{day}_open")
            close_ts = hours_dict.get(f"{day}_close")
            if open_ts and close_ts:
                open_time = datetime.fromtimestamp(open_ts).strftime("%I:%M %p").lstrip("0")
                close_time = datetime.fromtimestamp(close_ts).strftime("%I:%M %p").lstrip("0")
                parts.append(f"{day[:3].capitalize()} {open_time}-{close_time}")
    return "; ".join(parts) if parts else ""


def get_safe_string(poi: dict, field: str) -> str | None:
    """Get a string field from a POI, handling dict hours and empty values."""
    val = poi.get(field)
    if val is None:
        return None
    if isinstance(val, dict):
        if field == "hours":
            result = format_hours_dict(val)
            return result if result else None
        return None
    if isinstance(val, str):
        val = val.strip()
        return val if val else None
    return str(val)


def map_business_type(category: str) -> str:
    """Map a POI category to a businessType."""
    return CATEGORY_MAP.get(category, "other")


def get_cuisine_type(poi: dict) -> str | None:
    """Extract cuisine type for restaurants."""
    category = poi.get("category", "")
    if category not in ("restaurant", "cafe", "brewery"):
        return None
    subcategory = poi.get("subcategory")
    if subcategory and isinstance(subcategory, str) and subcategory.strip():
        return subcategory.strip()
    # Try to infer from source_categories
    source_cats = poi.get("source_categories", [])
    for sc in source_cats:
        if "cuisine" in sc.lower():
            parts = sc.split("=")
            if len(parts) > 1:
                return parts[1]
    return None


def build_tags_json(poi: dict) -> str:
    """Build a JSON array string of tags from source_categories."""
    source_cats = poi.get("source_categories", [])
    if not source_cats:
        return "[]"
    # Clean up tags - remove prefixes like ds_, osm_, hh_
    clean_tags = []
    for tag in source_cats:
        # Keep original for traceability
        clean_tags.append(tag)
    return json.dumps(clean_tags)


def get_confidence(poi: dict) -> float:
    """Calculate confidence based on source and multi-source status."""
    if poi.get("multi_source", False):
        return 0.90
    source = poi.get("source", "osm")
    return CONFIDENCE_MAP.get(source, 0.70)


def get_data_source(poi: dict) -> str:
    """Map source to dataSource string."""
    source = poi.get("source", "osm")
    return SOURCE_MAP.get(source, "overpass_import")


def generate_output_business_block(poi: dict, slug: str) -> str:
    """Generate a single OutputBusiness(...) constructor call."""
    name = escape_kotlin_string(poi["name"])
    lat = poi["lat"]
    lng = poi["lng"]
    address = escape_kotlin_string(get_safe_string(poi, "address") or "Salem, MA")
    btype = map_business_type(poi.get("category", "uncategorized"))
    cuisine = get_cuisine_type(poi)
    hours = get_safe_string(poi, "hours")
    phone = get_safe_string(poi, "phone")
    website = get_safe_string(poi, "website")
    description = get_safe_string(poi, "description")
    tags = build_tags_json(poi)
    confidence = get_confidence(poi)
    data_source = get_data_source(poi)

    lines = []
    lines.append(f'        OutputBusiness(')
    lines.append(f'            id = "{slug}", name = "{name}",')
    lines.append(f'            lat = {lat}, lng = {lng},')
    lines.append(f'            address = "{address}",')
    lines.append(f'            businessType = "{btype}",')
    if cuisine:
        lines.append(f'            cuisineType = "{escape_kotlin_string(cuisine)}",')
    if hours:
        lines.append(f'            hours = "{escape_kotlin_string(hours)}",')
    if phone:
        lines.append(f'            phone = "{escape_kotlin_string(phone)}",')
    if website:
        lines.append(f'            website = "{escape_kotlin_string(website)}",')
    if description:
        lines.append(f'            description = "{escape_kotlin_string(description)}",')
    lines.append(f'            tags = """{tags}""",')
    lines.append(f'            provenance = Provenance("{data_source}", {confidence}f, "{VERIFIED_DATE}", now, now,')
    lines.append(f'                now + 180L * 24 * 60 * 60 * 1000)')
    lines.append(f'        )')

    return "\n".join(lines)


def main():
    with open(INPUT_FILE) as f:
        data = json.load(f)

    pois = data["pois"]
    print(f"Loaded {len(pois)} POIs from merged data")

    # Group by businessType for organized output
    by_type: dict[str, list[tuple[str, dict]]] = {}
    seen_slugs: dict[str, int] = {}

    for poi in pois:
        btype = map_business_type(poi.get("category", "uncategorized"))
        slug = slugify(poi["name"])

        # Deduplicate slugs
        if slug in seen_slugs:
            seen_slugs[slug] += 1
            slug = f"{slug}_{seen_slugs[slug]}"
        else:
            seen_slugs[slug] = 0

        if btype not in by_type:
            by_type[btype] = []
        by_type[btype].append((slug, poi))

    # Type labels for section headers
    type_labels = {
        "restaurant": "Restaurants",
        "cafe": "Cafes & Coffee",
        "bar": "Bars & Breweries",
        "shop_occult": "Witch & Occult Shops",
        "shop_psychic": "Psychic & Spiritual Services",
        "shop_retail": "Shopping & Retail",
        "tour_ghost": "Ghost Tours",
        "tour": "Tours & Experiences",
        "attraction_haunted": "Haunted Attractions",
        "attraction": "Attractions",
        "historic": "Historic Sites",
        "museum": "Museums",
        "lodging": "Lodging",
        "venue": "Event Venues",
        "park": "Parks & Open Space",
        "public": "Public Services",
        "medical": "Medical",
        "services": "Services",
        "other": "Other",
    }

    # Build Kotlin file
    kt_lines = []
    kt_lines.append('/*')
    kt_lines.append(' * WickedSalemWitchCityTour v1.0')
    kt_lines.append(' * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.')
    kt_lines.append(' *')
    kt_lines.append(' * Auto-generated from merged-salem-pois.json by generate_businesses_expanded.py')
    kt_lines.append(f' * Generated: {datetime.now().strftime("%Y-%m-%d %H:%M:%S")}')
    kt_lines.append(f' * Source POIs: {len(pois)}')
    kt_lines.append(' *')
    kt_lines.append(' * Data attribution:')
    kt_lines.append(' *   - Destination Salem (salem.org)')
    kt_lines.append(' *   - Haunted Happenings (hauntedhappenings.org)')
    kt_lines.append(' *   - OpenStreetMap contributors (ODbL 1.0)')
    kt_lines.append(' */')
    kt_lines.append('')
    kt_lines.append('package com.example.salemcontent.data')
    kt_lines.append('')
    kt_lines.append('import com.example.salemcontent.pipeline.OutputBusiness')
    kt_lines.append('import com.example.salemcontent.pipeline.Provenance')
    kt_lines.append('')
    kt_lines.append('/**')
    kt_lines.append(f' * Expanded Salem business directory — {len(pois)} POIs from merged data sources.')
    kt_lines.append(' * Auto-generated. Do not edit manually; re-run generate_businesses_expanded.py.')
    kt_lines.append(' *')
    kt_lines.append(' * Sources: Destination Salem, Haunted Happenings, OpenStreetMap')
    kt_lines.append(' * OpenStreetMap data (c) OpenStreetMap contributors, ODbL 1.0')
    kt_lines.append(' */')
    kt_lines.append('object SalemBusinessesExpanded {')
    kt_lines.append('')
    kt_lines.append('    private val now = System.currentTimeMillis()')
    kt_lines.append('')

    # Build the all() function - list all category lists
    all_list_names = []

    # Sort types: tourist-facing first, services last
    type_order = [
        "shop_occult", "shop_psychic", "museum", "historic", "attraction",
        "attraction_haunted", "tour_ghost", "tour", "restaurant", "cafe", "bar",
        "shop_retail", "lodging", "venue", "park", "public", "medical", "services", "other"
    ]
    ordered_types = [t for t in type_order if t in by_type]
    # Add any types not in our order
    for t in by_type:
        if t not in ordered_types:
            ordered_types.append(t)

    for btype in ordered_types:
        entries = by_type[btype]
        list_name = btype.replace("_", " ").title().replace(" ", "")
        # Make first char lowercase for Kotlin convention
        list_name = list_name[0].lower() + list_name[1:] if list_name else list_name
        all_list_names.append(list_name)

    kt_lines.append(f'    fun all(): List<OutputBusiness> = {" + ".join(all_list_names)}')
    kt_lines.append('')

    # Stats tracking
    stats_by_type = Counter()
    total_phone = 0
    total_website = 0
    total_hours = 0

    # Generate each section
    for btype in ordered_types:
        entries = by_type[btype]
        label = type_labels.get(btype, btype.replace("_", " ").title())
        list_name = btype.replace("_", " ").title().replace(" ", "")
        list_name = list_name[0].lower() + list_name[1:] if list_name else list_name

        stats_by_type[btype] = len(entries)

        kt_lines.append(f'    // {"=" * 67}')
        kt_lines.append(f'    // {label} ({len(entries)})')
        kt_lines.append(f'    // {"=" * 67}')
        kt_lines.append('')
        kt_lines.append(f'    val {list_name} = listOf(')

        for i, (slug, poi) in enumerate(entries):
            block = generate_output_business_block(poi, slug)
            # Track stats
            if get_safe_string(poi, "phone"):
                total_phone += 1
            if get_safe_string(poi, "website"):
                total_website += 1
            if get_safe_string(poi, "hours"):
                total_hours += 1

            if i < len(entries) - 1:
                block += ","
            kt_lines.append(block)

        kt_lines.append('    )')
        kt_lines.append('')

    kt_lines.append('}')
    kt_lines.append('')

    # Write output
    output_path = os.path.abspath(OUTPUT_FILE)
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    content = "\n".join(kt_lines)
    with open(output_path, "w") as f:
        f.write(content)

    file_size = os.path.getsize(output_path)

    # Print summary
    print()
    print("=" * 60)
    print("  SalemBusinessesExpanded.kt Generation Summary")
    print("=" * 60)
    print()
    print(f"  Total businesses generated: {len(pois)}")
    print()
    print("  Breakdown by businessType:")
    for btype in ordered_types:
        count = stats_by_type[btype]
        label = type_labels.get(btype, btype)
        print(f"    {btype:<22} {count:>4}  ({label})")
    print()
    print(f"  Have phone:   {total_phone:>4} / {len(pois)}")
    print(f"  Have website: {total_website:>4} / {len(pois)}")
    print(f"  Have hours:   {total_hours:>4} / {len(pois)}")
    print()
    print(f"  Output file: {output_path}")
    print(f"  File size:   {file_size:,} bytes ({file_size / 1024:.1f} KB)")
    print()
    print("=" * 60)


if __name__ == "__main__":
    main()
