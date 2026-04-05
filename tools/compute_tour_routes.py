#!/usr/bin/env python3
"""
Fetch OSRM walking routes for each tour stop→next pair and inject
route geometry into the tour JSON. Includes loop segment (last→first).

Usage: python3 tools/compute_tour_routes.py
"""

import json
import time
import urllib.request
import sys
import os

TOUR_JSON = "app-salem/src/main/assets/tours/tour_witch_trials.json"
OSRM_BASE = "https://router.project-osrm.org/route/v1/foot"
RATE_LIMIT_S = 1.1  # Be polite to public OSRM

def fetch_route(from_lat, from_lng, to_lat, to_lng):
    """Fetch walking route from OSRM. Returns (coords_list, distance_m, duration_s)."""
    # OSRM uses lng,lat order
    url = (f"{OSRM_BASE}/{from_lng},{from_lat};{to_lng},{to_lat}"
           f"?overview=full&geometries=geojson&steps=true")
    req = urllib.request.Request(url, headers={"User-Agent": "WickedSalemWitchCityTour/1.0"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode())

    if data.get("code") != "Ok" or not data.get("routes"):
        return None, 0, 0

    route = data["routes"][0]
    coords = route["geometry"]["coordinates"]  # [[lng, lat], ...]
    # Convert to [lat, lng] for our JSON
    lat_lng_coords = [[round(c[1], 6), round(c[0], 6)] for c in coords]
    distance_m = route["distance"]
    duration_s = route["duration"]
    return lat_lng_coords, distance_m, duration_s


def main():
    with open(TOUR_JSON) as f:
        tour_data = json.load(f)

    stops = tour_data["stops"]
    n = len(stops)
    total_distance = 0
    total_duration = 0

    print(f"Computing OSRM walking routes for {n} stops + loop-back...")
    print()

    for i in range(n):
        next_i = (i + 1) % n  # Loop: last stop connects back to first
        stop_from = stops[i]
        stop_to = stops[next_i]

        label = f"Stop {stop_from['order']} → {stop_to['order']}"
        if next_i == 0:
            label += " (loop back)"

        print(f"  {label}: {stop_from['name'][:30]} → {stop_to['name'][:30]}...", end=" ", flush=True)

        coords, dist_m, dur_s = fetch_route(
            stop_from["lat"], stop_from["lng"],
            stop_to["lat"], stop_to["lng"]
        )

        if coords is None:
            print("FAILED")
            continue

        # Store route geometry on the FROM stop (route to next)
        stop_from["routeToNext"] = coords
        stop_from["routeDistanceM"] = round(dist_m)
        stop_from["routeDurationS"] = round(dur_s)

        # Also update the pre-existing distance/walking fields on the TO stop
        if next_i != 0:
            stop_to["distanceMFromPrev"] = round(dist_m)
            stop_to["walkingMinutesFromPrev"] = round(dur_s / 60)

        total_distance += dist_m
        total_duration += dur_s
        pts = len(coords)
        print(f"{dist_m:.0f}m, {dur_s/60:.1f}min, {pts} points")

        time.sleep(RATE_LIMIT_S)

    # Update tour metadata
    tour_data["tour"]["distanceKm"] = round(total_distance / 1000, 1)
    tour_data["tour"]["estimatedMinutes"] = round(total_duration / 60)
    tour_data["tour"]["isLoop"] = True

    print()
    print(f"Total route: {total_distance/1000:.1f} km, {total_duration/60:.0f} min")
    print(f"Route points: {sum(len(s.get('routeToNext', [])) for s in stops)}")

    # Write back
    with open(TOUR_JSON, "w") as f:
        json.dump(tour_data, f, indent=2, ensure_ascii=False)

    print(f"Written to {TOUR_JSON}")


if __name__ == "__main__":
    main()
