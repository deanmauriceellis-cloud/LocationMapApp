#!/usr/bin/env python3
"""
Scrape ALL business listings from the Destination Salem REST API.

Endpoints:
  - /listings      — categorized listings
  - /search        — paginated search (50/page)
  - /parking       — parking locations
  - /restrooms     — restroom locations

Strategy: listings endpoint + alphabetical a-z searches + keyword searches,
then deduplicate by ID.
"""

import json
import os
import time
import string
import sys
from collections import defaultdict
from datetime import datetime, timezone

import requests

BASE = "https://www.salem.org/wp-json/dest-salem-app/v1"
DELAY = 0.5  # seconds between requests
TIMEOUT = 30

KEYWORDS = [
    "witch", "psychic", "tarot", "ghost", "haunted", "museum",
    "restaurant", "bar", "cafe", "hotel", "shop", "tour",
    "halloween", "occult", "gallery", "theater", "park",
]

HEADERS = {
    "User-Agent": "LocationMapApp-DataCollector/1.0 (research; salem walking tour app)",
    "Accept": "application/json",
}

# ── helpers ──────────────────────────────────────────────────────────────

def get_json(url, params=None, label=""):
    """GET with retry, delay, and error handling."""
    for attempt in range(3):
        try:
            resp = requests.get(url, params=params, headers=HEADERS, timeout=TIMEOUT)
            resp.raise_for_status()
            time.sleep(DELAY)
            return resp.json()
        except requests.exceptions.HTTPError as e:
            if resp.status_code == 404:
                print(f"  [404] {label or url} — skipping")
                time.sleep(DELAY)
                return None
            print(f"  [HTTP {resp.status_code}] {label or url} — attempt {attempt+1}/3")
        except requests.exceptions.RequestException as e:
            print(f"  [ERR] {label or url} — {e} — attempt {attempt+1}/3")
        time.sleep(DELAY * 2)
    print(f"  [FAIL] {label or url} — giving up after 3 attempts")
    return None


def extract_id(item):
    """Get a stable unique ID from a listing item."""
    # Try various ID fields
    for key in ("id", "ID", "post_id", "listing_id"):
        if key in item and item[key]:
            return str(item[key])
    # Fall back to title-based key
    title = item.get("title") or item.get("name") or ""
    return f"title:{title}"


def normalize_listing(item, source="unknown"):
    """Ensure each listing has a consistent shape and source tag."""
    item["_scrape_source"] = source
    return item


# ── 1. Listings endpoint ────────────────────────────────────────────────

def fetch_listings(all_items):
    """Fetch the /listings endpoint which returns categorized data."""
    print("\n=== Fetching /listings endpoint ===")
    data = get_json(f"{BASE}/listings", label="/listings")
    if data is None:
        print("  No data from /listings")
        return

    count_before = len(all_items)

    if isinstance(data, dict):
        # Could be {category_name: [items...], ...} or {data: [...]}
        for key, value in data.items():
            if isinstance(value, list):
                print(f"  Category '{key}': {len(value)} items")
                for item in value:
                    if isinstance(item, dict):
                        lid = extract_id(item)
                        item["_category_from_listings"] = key
                        normalize_listing(item, source="listings")
                        if lid not in all_items:
                            all_items[lid] = item
            elif isinstance(value, dict):
                # Nested: {category: {subcategory: [items...]}}
                for subkey, subvalue in value.items():
                    if isinstance(subvalue, list):
                        print(f"  Category '{key}/{subkey}': {len(subvalue)} items")
                        for item in subvalue:
                            if isinstance(item, dict):
                                lid = extract_id(item)
                                item["_category_from_listings"] = f"{key}/{subkey}"
                                normalize_listing(item, source="listings")
                                if lid not in all_items:
                                    all_items[lid] = item
    elif isinstance(data, list):
        print(f"  Flat list: {len(data)} items")
        for item in data:
            if isinstance(item, dict):
                lid = extract_id(item)
                normalize_listing(item, source="listings")
                if lid not in all_items:
                    all_items[lid] = item

    print(f"  Added {len(all_items) - count_before} new items from /listings")


# ── 2. Search endpoint ──────────────────────────────────────────────────

def fetch_search(term, all_items):
    """Search with pagination. Returns count of new items found."""
    new_count = 0
    page = 1
    max_pages = 50  # safety limit

    while page <= max_pages:
        label = f"/search?search={term}&page={page}"
        data = get_json(f"{BASE}/search", params={"search": term, "page": page}, label=label)

        if data is None:
            break

        # Handle different response shapes
        items = []
        if isinstance(data, list):
            items = data
        elif isinstance(data, dict):
            # Could be {results: [...]} or {data: [...]} or {listings: [...]}
            for key in ("results", "data", "listings", "items", "posts"):
                if key in data and isinstance(data[key], list):
                    items = data[key]
                    break
            if not items and "id" in data:
                # Single result wrapped in dict
                items = [data]

        if not items:
            break

        for item in items:
            if isinstance(item, dict):
                lid = extract_id(item)
                normalize_listing(item, source=f"search:{term}")
                if lid not in all_items:
                    all_items[lid] = item
                    new_count += 1

        # If fewer than 50 results, no more pages
        if len(items) < 50:
            break

        page += 1

    return new_count


def fetch_all_searches(all_items):
    """Run alphabetical + keyword searches."""
    print("\n=== Running alphabetical searches (a-z) ===")
    for letter in string.ascii_lowercase:
        new = fetch_search(letter, all_items)
        sys.stdout.write(f"  '{letter}': +{new} new (total: {len(all_items)})\n")
        sys.stdout.flush()

    print(f"\n=== Running keyword searches ({len(KEYWORDS)} terms) ===")
    for kw in KEYWORDS:
        new = fetch_search(kw, all_items)
        sys.stdout.write(f"  '{kw}': +{new} new (total: {len(all_items)})\n")
        sys.stdout.flush()


# ── 3. Parking endpoint ─────────────────────────────────────────────────

def fetch_parking():
    """Fetch /parking endpoint."""
    print("\n=== Fetching /parking endpoint ===")
    data = get_json(f"{BASE}/parking", label="/parking")
    if data is None:
        print("  No data from /parking")
        return []

    items = []
    if isinstance(data, list):
        items = data
    elif isinstance(data, dict):
        for key in ("results", "data", "listings", "items", "parking"):
            if key in data and isinstance(data[key], list):
                items = data[key]
                break
        if not items:
            items = [data] if "id" in data or "title" in data or "name" in data else []
            if not items:
                # Treat all values that are lists as potential item lists
                for v in data.values():
                    if isinstance(v, list) and v and isinstance(v[0], dict):
                        items.extend(v)

    print(f"  Found {len(items)} parking locations")
    for item in items:
        if isinstance(item, dict):
            normalize_listing(item, source="parking")
    return items


# ── 4. Restrooms endpoint ───────────────────────────────────────────────

def fetch_restrooms():
    """Fetch /restrooms endpoint."""
    print("\n=== Fetching /restrooms endpoint ===")
    data = get_json(f"{BASE}/restrooms", label="/restrooms")
    if data is None:
        print("  No data from /restrooms")
        return []

    items = []
    if isinstance(data, list):
        items = data
    elif isinstance(data, dict):
        for key in ("results", "data", "listings", "items", "restrooms"):
            if key in data and isinstance(data[key], list):
                items = data[key]
                break
        if not items:
            items = [data] if "id" in data or "title" in data or "name" in data else []
            if not items:
                for v in data.values():
                    if isinstance(v, list) and v and isinstance(v[0], dict):
                        items.extend(v)

    print(f"  Found {len(items)} restroom locations")
    for item in items:
        if isinstance(item, dict):
            normalize_listing(item, source="restrooms")
    return items


# ── 5. Summary ───────────────────────────────────────────────────────────

def has_coordinates(item):
    """Check if a listing item has lat/lng coordinates."""
    if not isinstance(item, dict):
        return False
    # Direct lat/lng fields
    for lat_key in ("lat", "latitude", "geo_lat", "Lat"):
        val = item.get(lat_key)
        if val is not None and val != "" and val != 0:
            try:
                float(val)
                return True
            except (ValueError, TypeError):
                pass
    # Nested location/geo/coordinates/map objects
    for container_key in ("location", "geo", "coordinates", "map"):
        container = item.get(container_key)
        if isinstance(container, dict):
            for lat_key in ("lat", "latitude"):
                val = container.get(lat_key)
                if val is not None and val != "" and val != 0:
                    try:
                        float(val)
                        return True
                    except (ValueError, TypeError):
                        pass
    return False


def build_summary(listings, parking, restrooms):
    """Build a summary of what we collected."""
    summary = {
        "scrape_date": datetime.now(timezone.utc).isoformat(),
        "total_listings": len(listings),
        "total_parking": len(parking),
        "total_restrooms": len(restrooms),
        "total_all": len(listings) + len(parking) + len(restrooms),
    }

    # Count listings with coordinates
    coords_count = sum(1 for item in listings if has_coordinates(item))
    summary["listings_with_coordinates"] = coords_count
    summary["listings_without_coordinates"] = len(listings) - coords_count

    # Also check parking/restrooms for coordinates
    parking_coords = sum(1 for item in parking if has_coordinates(item))
    restroom_coords = sum(1 for item in restrooms if has_coordinates(item))
    summary["parking_with_coordinates"] = parking_coords
    summary["restrooms_with_coordinates"] = restroom_coords

    # Category breakdown
    categories = defaultdict(int)
    for item in listings:
        cat = item.get("_category_from_listings") or item.get("category") or item.get("type") or "uncategorized"
        if isinstance(cat, list):
            for c in cat:
                if isinstance(c, dict):
                    categories[c.get("name", "unknown")] += 1
                else:
                    categories[str(c)] += 1
        else:
            categories[str(cat)] += 1

    summary["category_breakdown"] = dict(sorted(categories.items(), key=lambda x: -x[1]))

    return summary


# ── main ─────────────────────────────────────────────────────────────────

def main():
    start_time = time.time()
    print("=" * 60)
    print("Destination Salem API Scraper")
    print(f"Started: {datetime.now(timezone.utc).isoformat()}")
    print("=" * 60)

    all_items = {}  # id -> item dict

    # 1. Listings endpoint
    fetch_listings(all_items)

    # 2. Search: alphabetical + keywords
    fetch_all_searches(all_items)

    # 3. Parking
    parking = fetch_parking()

    # 4. Restrooms
    restrooms = fetch_restrooms()

    # Convert to list
    listings = list(all_items.values())

    # Build summary
    summary = build_summary(listings, parking, restrooms)

    elapsed = time.time() - start_time
    summary["scrape_duration_seconds"] = round(elapsed, 1)

    # Assemble output
    output = {
        "meta": {
            "source": "Destination Salem REST API (salem.org)",
            "base_url": BASE,
            "scrape_date": summary["scrape_date"],
            "scraper": "scrape_destination_salem.py",
            "note": "Scraped for WickedSalemWitchCityTour POI enrichment",
        },
        "summary": summary,
        "listings": listings,
        "parking": parking,
        "restrooms": restrooms,
    }

    # Save
    out_path = "/home/witchdoctor/Development/LocationMapApp_v1.5/tools/salem-data/destination-salem.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2, ensure_ascii=False, default=str)

    file_size = os.path.getsize(out_path)

    print("\n" + "=" * 60)
    print("SUMMARY")
    print("=" * 60)
    print(f"Total listings (deduplicated): {summary['total_listings']}")
    print(f"  With coordinates:            {summary['listings_with_coordinates']}")
    print(f"  Without coordinates:         {summary['listings_without_coordinates']}")
    print(f"Parking locations:             {summary['total_parking']} ({summary.get('parking_with_coordinates', '?')} with coords)")
    print(f"Restroom locations:            {summary['total_restrooms']} ({summary.get('restrooms_with_coordinates', '?')} with coords)")
    print(f"Grand total:                   {summary['total_all']}")
    print(f"Duration:                      {summary['scrape_duration_seconds']}s")
    print(f"\nCategory breakdown:")
    for cat, count in summary["category_breakdown"].items():
        print(f"  {cat}: {count}")
    print(f"\nSaved to: {out_path}")
    print(f"File size: {file_size / 1024:.1f} KB")
    print("=" * 60)


if __name__ == "__main__":
    main()
