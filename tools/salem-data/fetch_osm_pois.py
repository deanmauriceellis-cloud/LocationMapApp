#!/usr/bin/env python3
"""
Fetch ALL POI data from OpenStreetMap for Salem, MA via the Overpass API.

Bounding box: 42.50,-70.93,42.54,-70.87 (central Salem)
Tag types: amenity, shop, tourism, historic, leisure, office, craft

Output: osm-salem-pois.json in the same directory as this script.

License: Data is under the Open Database License (ODbL).
         Attribution required: (c) OpenStreetMap contributors
         https://www.openstreetmap.org/copyright
"""

import json
import os
import sys
import urllib.request
import urllib.parse
from datetime import datetime, timezone

OVERPASS_URL = "https://overpass-api.de/api/interpreter"

# Central Salem bounding box
BBOX = "42.50,-70.93,42.54,-70.87"

# All tag types to query
TAG_TYPES = ["amenity", "shop", "tourism", "historic", "leisure", "office", "craft"]

def build_query():
    """Build Overpass QL query for all tag types."""
    # We query nodes, ways, and relations for each tag type.
    # Using union to combine all results.
    tag_queries = []
    for tag in TAG_TYPES:
        tag_queries.append(f'  node["{tag}"]({BBOX});')
        tag_queries.append(f'  way["{tag}"]({BBOX});')
        tag_queries.append(f'  relation["{tag}"]({BBOX});')

    union_body = "\n".join(tag_queries)

    query = f"""[out:json][timeout:120];
(
{union_body}
);
out center;
"""
    return query


def extract_poi(element):
    """Extract a clean POI record from an Overpass element."""
    tags = element.get("tags", {})

    # Get coordinates: nodes have lat/lon directly; ways/relations use center
    lat = element.get("lat") or (element.get("center", {}).get("lat"))
    lng = element.get("lon") or (element.get("center", {}).get("lon"))

    if lat is None or lng is None:
        return None

    # Determine the primary category tag(s)
    categories = {}
    for tag_type in TAG_TYPES:
        if tag_type in tags:
            categories[tag_type] = tags[tag_type]

    # Build address from addr:* tags
    address_parts = {}
    for key, value in tags.items():
        if key.startswith("addr:"):
            address_parts[key.replace("addr:", "")] = value

    poi = {
        "osm_id": element.get("id"),
        "osm_type": element.get("type"),
        "name": tags.get("name"),
        "lat": lat,
        "lng": lng,
        "categories": categories,
        "address": address_parts if address_parts else None,
        "phone": tags.get("phone") or tags.get("contact:phone"),
        "website": tags.get("website") or tags.get("contact:website") or tags.get("url"),
        "opening_hours": tags.get("opening_hours"),
        "cuisine": tags.get("cuisine"),
        "description": tags.get("description"),
        "wikipedia": tags.get("wikipedia"),
        "wikidata": tags.get("wikidata"),
        "all_tags": tags,
    }

    return poi


def summarize(pois):
    """Generate a summary of the POI dataset."""
    total = len(pois)
    named = sum(1 for p in pois if p.get("name"))

    # Breakdown by main tag type
    by_type = {}
    for tag_type in TAG_TYPES:
        count = sum(1 for p in pois if tag_type in p.get("categories", {}))
        if count > 0:
            by_type[tag_type] = count

    # Count POIs with contact info
    with_phone = sum(1 for p in pois if p.get("phone"))
    with_website = sum(1 for p in pois if p.get("website"))
    with_hours = sum(1 for p in pois if p.get("opening_hours"))
    with_address = sum(1 for p in pois if p.get("address"))

    # Top amenity subtypes
    amenity_subtypes = {}
    for p in pois:
        if "amenity" in p.get("categories", {}):
            subtype = p["categories"]["amenity"]
            amenity_subtypes[subtype] = amenity_subtypes.get(subtype, 0) + 1
    top_amenities = dict(sorted(amenity_subtypes.items(), key=lambda x: -x[1])[:15])

    # Top shop subtypes
    shop_subtypes = {}
    for p in pois:
        if "shop" in p.get("categories", {}):
            subtype = p["categories"]["shop"]
            shop_subtypes[subtype] = shop_subtypes.get(subtype, 0) + 1
    top_shops = dict(sorted(shop_subtypes.items(), key=lambda x: -x[1])[:15])

    # Top tourism subtypes
    tourism_subtypes = {}
    for p in pois:
        if "tourism" in p.get("categories", {}):
            subtype = p["categories"]["tourism"]
            tourism_subtypes[subtype] = tourism_subtypes.get(subtype, 0) + 1

    # Top historic subtypes
    historic_subtypes = {}
    for p in pois:
        if "historic" in p.get("categories", {}):
            subtype = p["categories"]["historic"]
            historic_subtypes[subtype] = historic_subtypes.get(subtype, 0) + 1

    summary = {
        "total_pois": total,
        "named_pois": named,
        "unnamed_pois": total - named,
        "by_main_tag_type": by_type,
        "with_phone": with_phone,
        "with_website": with_website,
        "with_opening_hours": with_hours,
        "with_address": with_address,
        "top_amenity_subtypes": top_amenities,
        "top_shop_subtypes": top_shops,
        "tourism_subtypes": tourism_subtypes,
        "historic_subtypes": historic_subtypes,
    }

    return summary


def main():
    query = build_query()
    print("=== Overpass QL Query ===")
    print(query)
    print("=========================")
    print()

    # Send query to Overpass API
    print(f"Sending request to {OVERPASS_URL} ...")
    data = urllib.parse.urlencode({"data": query}).encode("utf-8")
    req = urllib.request.Request(OVERPASS_URL, data=data)
    req.add_header("User-Agent", "LocationMapApp-POI-Fetcher/1.0 (Salem tour app)")

    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            raw = response.read()
            result = json.loads(raw)
    except Exception as e:
        print(f"ERROR: Failed to fetch data from Overpass API: {e}", file=sys.stderr)
        sys.exit(1)

    elements = result.get("elements", [])
    print(f"Received {len(elements)} elements from Overpass API.")
    print()

    # Deduplicate: some elements may appear in multiple tag queries
    seen_ids = set()
    unique_elements = []
    for el in elements:
        key = (el.get("type"), el.get("id"))
        if key not in seen_ids:
            seen_ids.add(key)
            unique_elements.append(el)

    print(f"After deduplication: {len(unique_elements)} unique elements.")

    # Extract POIs
    pois = []
    skipped = 0
    for el in unique_elements:
        poi = extract_poi(el)
        if poi:
            pois.append(poi)
        else:
            skipped += 1

    print(f"Extracted {len(pois)} POIs ({skipped} skipped due to missing coordinates).")
    print()

    # Generate summary
    summary = summarize(pois)

    # Build output document
    output = {
        "metadata": {
            "source": "OpenStreetMap via Overpass API",
            "endpoint": OVERPASS_URL,
            "query_bbox": BBOX,
            "query_tags": TAG_TYPES,
            "fetched_at": datetime.now(timezone.utc).isoformat(),
            "license": "Open Database License (ODbL) 1.0",
            "attribution": "(c) OpenStreetMap contributors",
            "attribution_url": "https://www.openstreetmap.org/copyright",
            "license_note": (
                "This data is made available under the Open Database License: "
                "http://opendatacommons.org/licenses/odbl/1.0/. "
                "Any rights in individual contents of the database are licensed under the "
                "Database Contents License: http://opendatacommons.org/licenses/dbcl/1.0/. "
                "You must attribute: (c) OpenStreetMap contributors."
            ),
        },
        "summary": summary,
        "pois": pois,
    }

    # Write to file
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_path = os.path.join(script_dir, "osm-salem-pois.json")

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2, ensure_ascii=False)

    print(f"Saved to: {output_path}")
    print(f"File size: {os.path.getsize(output_path) / 1024:.1f} KB")
    print()

    # Print summary
    print("=== SUMMARY ===")
    print(f"Total POIs:          {summary['total_pois']}")
    print(f"Named POIs:          {summary['named_pois']}")
    print(f"Unnamed POIs:        {summary['unnamed_pois']}")
    print()
    print("By main tag type:")
    for tag_type, count in sorted(summary["by_main_tag_type"].items(), key=lambda x: -x[1]):
        print(f"  {tag_type:12s}  {count:4d}")
    print()
    print(f"With phone:          {summary['with_phone']}")
    print(f"With website:        {summary['with_website']}")
    print(f"With opening hours:  {summary['with_opening_hours']}")
    print(f"With address:        {summary['with_address']}")
    print()

    if summary.get("top_amenity_subtypes"):
        print("Top amenity subtypes:")
        for subtype, count in summary["top_amenity_subtypes"].items():
            print(f"  {subtype:25s}  {count:3d}")
        print()

    if summary.get("top_shop_subtypes"):
        print("Top shop subtypes:")
        for subtype, count in summary["top_shop_subtypes"].items():
            print(f"  {subtype:25s}  {count:3d}")
        print()

    if summary.get("tourism_subtypes"):
        print("Tourism subtypes:")
        for subtype, count in sorted(summary["tourism_subtypes"].items(), key=lambda x: -x[1]):
            print(f"  {subtype:25s}  {count:3d}")
        print()

    if summary.get("historic_subtypes"):
        print("Historic subtypes:")
        for subtype, count in sorted(summary["historic_subtypes"].items(), key=lambda x: -x[1]):
            print(f"  {subtype:25s}  {count:3d}")
        print()

    print("License: ODbL — (c) OpenStreetMap contributors")
    print("Done.")


if __name__ == "__main__":
    main()
