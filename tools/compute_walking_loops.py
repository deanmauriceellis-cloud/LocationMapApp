#!/usr/bin/env python3
"""
Compute OSRM walking routes for the 3 Salem walking loops.
Converts raw waypoints into street-following route geometry.

Usage: python3 tools/compute_walking_loops.py
"""

import json
import time
import urllib.request

OSRM_BASE = "https://router.project-osrm.org/route/v1/foot"
RATE_LIMIT_S = 1.1
OUTPUT_DIR = "app-salem/src/main/assets/tours"

LOOPS = [
    {
        "id": "loop_quick",
        "name": "Quick Salem Stroll",
        "description": "The essential Salem experience in 30 minutes. Walk the pedestrian mall, visit Charter Street Cemetery, and loop back along the waterfront.",
        "estimatedMinutes": 30,
        "waypoints": [
            (42.5216, -70.8869),  # NPS Visitor Center
            (42.5220, -70.8895),  # Essex St
            (42.5218, -70.8935),  # Essex pedestrian mall
            (42.5212, -70.8905),  # Essex at Central
            (42.5205, -70.8905),  # Charter St
            (42.5195, -70.8898),  # Charter Street Cemetery
            (42.5188, -70.8890),  # Derby St
            (42.5195, -70.8920),  # Derby St west
            (42.5210, -70.8880),  # Hawthorne Blvd
            (42.5216, -70.8869),  # Return
        ]
    },
    {
        "id": "loop_standard",
        "name": "Salem Explorer Walk",
        "description": "A thorough downtown tour covering witch sites, maritime history, beautiful architecture, and Salem Common. Pass 50+ narrated locations.",
        "estimatedMinutes": 60,
        "waypoints": [
            (42.5216, -70.8869),
            (42.5220, -70.8895),
            (42.5225, -70.8967),  # Witch House area
            (42.5240, -70.8970),  # Washington St
            (42.5250, -70.8975),  # Witch Museum
            (42.5240, -70.8880),  # Hawthorne Blvd
            (42.5230, -70.8935),  # Chestnut St
            (42.5220, -70.8895),  # Federal St
            (42.5215, -70.8912),  # Charter St
            (42.5195, -70.8898),  # Cemetery
            (42.5188, -70.8890),  # Derby St
            (42.5175, -70.8830),  # Seven Gables
            (42.5195, -70.8850),  # Back up
            (42.5216, -70.8869),  # Return
        ]
    },
    {
        "id": "loop_grand",
        "name": "Grand Salem Experience",
        "description": "The complete downtown Salem experience. Every major street, every historic district, the waterfront, and the Common. Pass 80+ narrated locations.",
        "estimatedMinutes": 90,
        "waypoints": [
            (42.5216, -70.8869),
            (42.5220, -70.8895),
            (42.5225, -70.8967),
            (42.5240, -70.8980),  # Bridge St area
            (42.5255, -70.8965),  # Church St
            (42.5250, -70.8975),  # Witch Museum
            (42.5240, -70.8880),  # Common
            (42.5230, -70.8935),  # Chestnut St
            (42.5240, -70.8910),  # Federal St north
            (42.5220, -70.8895),  # Federal at Essex
            (42.5230, -70.8930),  # Liberty St
            (42.5215, -70.8912),  # Charter St
            (42.5195, -70.8898),  # Cemetery
            (42.5188, -70.8890),  # Derby St west
            (42.5175, -70.8830),  # Seven Gables
            (42.5170, -70.8815),  # Turner St
            (42.5180, -70.8855),  # Derby Wharf
            (42.5195, -70.8920),  # Congress St
            (42.5210, -70.8880),  # Hawthorne Blvd
            (42.5216, -70.8869),  # Return
        ]
    }
]


def fetch_route(from_lat, from_lng, to_lat, to_lng):
    url = (f"{OSRM_BASE}/{from_lng},{from_lat};{to_lng},{to_lat}"
           f"?overview=full&geometries=geojson&steps=true")
    req = urllib.request.Request(url, headers={"User-Agent": "WickedSalemWitchCityTour/1.0"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode())

    if data.get("code") != "Ok" or not data.get("routes"):
        return None, 0, 0

    route = data["routes"][0]
    coords = [(c[1], c[0]) for c in route["geometry"]["coordinates"]]  # [lng,lat] → (lat,lng)
    return coords, route["distance"], route["duration"]


def compute_loop(loop):
    print(f"\n=== {loop['name']} ({len(loop['waypoints'])} waypoints) ===")
    all_coords = []
    total_distance = 0
    total_duration = 0

    for i in range(len(loop["waypoints"]) - 1):
        lat1, lng1 = loop["waypoints"][i]
        lat2, lng2 = loop["waypoints"][i + 1]

        print(f"  Segment {i+1}: ({lat1:.4f},{lng1:.4f}) → ({lat2:.4f},{lng2:.4f})  ", end="")
        coords, dist, dur = fetch_route(lat1, lng1, lat2, lng2)
        if coords:
            all_coords.extend(coords if i == 0 else coords[1:])  # skip duplicate start
            total_distance += dist
            total_duration += dur
            print(f"{dist:.0f}m, {dur:.0f}s, {len(coords)} pts")
        else:
            print("FAILED — using straight line")
            all_coords.append((lat2, lng2))
        time.sleep(RATE_LIMIT_S)

    print(f"  Total: {total_distance:.0f}m ({total_distance/1000:.1f}km), {total_duration/60:.0f}min, {len(all_coords)} route points")

    # Build tour JSON
    tour_json = {
        "tour": {
            "id": loop["id"],
            "name": loop["name"],
            "description": loop["description"],
            "estimatedMinutes": loop["estimatedMinutes"],
            "distanceKm": round(total_distance / 1000, 1),
            "pointCount": len(all_coords),
            "difficulty": "easy" if loop["estimatedMinutes"] <= 30 else
                          "moderate" if loop["estimatedMinutes"] <= 60 else "challenging"
        },
        "route": [{"lat": c[0], "lng": c[1]} for c in all_coords],
        "waypoints": [{"lat": w[0], "lng": w[1]} for w in loop["waypoints"]]
    }

    outfile = f"{OUTPUT_DIR}/{loop['id']}.json"
    with open(outfile, "w") as f:
        json.dump(tour_json, f, indent=2)
    print(f"  → {outfile}")
    return tour_json


if __name__ == "__main__":
    for loop in LOOPS:
        compute_loop(loop)
    print("\nDone! Walking loop routes computed and saved.")
