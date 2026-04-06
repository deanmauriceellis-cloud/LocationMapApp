#!/usr/bin/env python3
"""
Scrape all venue listings from the Haunted Happenings REST API.
Paginates through all pages and saves results to JSON.
"""

import html
import json
import time
import sys
from datetime import datetime, timezone

import requests

API_BASE = "https://www.hauntedhappenings.org/wp-json/tribe/events/v1/venues"
PER_PAGE = 50
DELAY = 0.5
OUTPUT_FILE = "/home/witchdoctor/Development/LocationMapApp_v1.5/tools/salem-data/haunted-happenings-venues.json"


def fetch_all_venues():
    all_venues = []
    page = 1

    while True:
        url = f"{API_BASE}?per_page={PER_PAGE}&page={page}"
        print(f"Fetching page {page}: {url}")

        try:
            resp = requests.get(url, timeout=30, headers={
                "User-Agent": "LocationMapApp-DataPipeline/1.0"
            })
            resp.raise_for_status()
        except requests.exceptions.HTTPError as e:
            if resp.status_code == 404:
                print(f"  Page {page} returned 404 - no more pages.")
                break
            raise

        data = resp.json()

        # The API returns venues in a 'venues' key
        venues = data.get("venues", [])
        total_found = data.get("total", 0)
        total_pages = data.get("total_pages", 0)

        print(f"  Got {len(venues)} venues (total: {total_found}, total_pages: {total_pages})")

        for v in venues:
            venue_record = {
                "id": v.get("id"),
                "venue": html.unescape(v.get("venue", "") or ""),
                "address": html.unescape(v.get("address", "") or ""),
                "city": v.get("city"),
                "state": v.get("state") or v.get("province"),
                "zip": v.get("zip"),
                "country": v.get("country"),
                "phone": v.get("phone"),
                "url": v.get("url"),
                "website": v.get("website"),
                "lat": None,
                "lng": None,
                "stateprovince": v.get("stateprovince"),
            }

            # Coordinates can be in different places depending on API version
            if v.get("geo_lat") and v.get("geo_lng"):
                venue_record["lat"] = float(v["geo_lat"]) if v["geo_lat"] else None
                venue_record["lng"] = float(v["geo_lng"]) if v["geo_lng"] else None
            elif v.get("geolocation", {}).get("latitude"):
                geo = v["geolocation"]
                venue_record["lat"] = float(geo["latitude"]) if geo.get("latitude") else None
                venue_record["lng"] = float(geo["longitude"]) if geo.get("longitude") else None

            all_venues.append(venue_record)

        if page >= total_pages:
            print(f"  Reached last page ({total_pages}).")
            break

        page += 1
        time.sleep(DELAY)

    return all_venues


def main():
    print(f"Starting Haunted Happenings venue scrape at {datetime.now(timezone.utc).isoformat()}")
    print("=" * 60)

    venues = fetch_all_venues()

    # Deduplicate by ID (just in case)
    seen_ids = set()
    unique_venues = []
    for v in venues:
        if v["id"] not in seen_ids:
            seen_ids.add(v["id"])
            unique_venues.append(v)

    venues = unique_venues

    # Compute stats
    total = len(venues)
    with_coords = sum(1 for v in venues if v["lat"] is not None and v["lng"] is not None)
    without_coords = total - with_coords

    # Sample venue names
    sample_names = [v["venue"] for v in venues[:10]]

    # Build output
    output = {
        "metadata": {
            "source": "https://www.hauntedhappenings.org/wp-json/tribe/events/v1/venues",
            "scraped_at": datetime.now(timezone.utc).isoformat(),
            "total_venues": total,
            "venues_with_coordinates": with_coords,
            "venues_without_coordinates": without_coords,
        },
        "venues": venues,
    }

    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2, ensure_ascii=False)

    print()
    print("=" * 60)
    print(f"SUMMARY")
    print(f"  Total venues:              {total}")
    print(f"  With coordinates:          {with_coords}")
    print(f"  Without coordinates:       {without_coords}")
    print(f"  Output file:               {OUTPUT_FILE}")
    print()
    print(f"  Sample venue names:")
    for name in sample_names:
        print(f"    - {name}")
    print()

    # Show coordinate coverage breakdown by city
    cities = {}
    for v in venues:
        city = v.get("city") or "(no city)"
        if city not in cities:
            cities[city] = {"total": 0, "with_coords": 0}
        cities[city]["total"] += 1
        if v["lat"] is not None:
            cities[city]["with_coords"] += 1

    print("  Venues by city:")
    for city, counts in sorted(cities.items(), key=lambda x: -x[1]["total"]):
        print(f"    {city}: {counts['total']} total, {counts['with_coords']} with coords")

    print()
    print("Done.")


if __name__ == "__main__":
    main()
