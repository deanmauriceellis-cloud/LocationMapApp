#!/usr/bin/env python3
"""
Generate SQL INSERT statements for the narration_points table
from the Wave 1 priority POI data (307 POIs across 3 waves).

Input:  tools/salem-data/narration-priority-pois.json
Output: Appends to salem-content/salem_content.sql
"""

import json
import re
import time
from pathlib import Path

# ── Paths ──────────────────────────────────────────────────────────────────
SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent.parent
INPUT_FILE = SCRIPT_DIR / "narration-priority-pois.json"
OUTPUT_FILE = PROJECT_ROOT / "salem-content" / "salem_content.sql"

# ── Wave assignment by category ────────────────────────────────────────────
WAVE_MAP = {
    # Wave 1
    "witch_museum": 1, "witch_shop": 1, "psychic": 1,
    "ghost_tour": 1, "haunted_attraction": 1, "museum": 1,
    "historic_site": 1, "public_art": 1, "cemetery": 1,
    "tour": 1, "visitor_info": 1,
    # Wave 2
    "attraction": 2, "park": 2, "place_of_worship": 2,
    "lodging": 2, "hotel": 2, "brewery": 2, "bar": 2,
    # Wave 3
    "restaurant": 3, "cafe": 3,
    "community_center": 3, "government": 3, "library": 3,
}

# ── Priority assignment by category ───────────────────────────────────────
PRIORITY_MAP = {
    "witch_museum": 1, "witch_shop": 1, "psychic": 1,
    "ghost_tour": 2, "haunted_attraction": 2, "museum": 2, "historic_site": 2,
    "public_art": 2, "cemetery": 2, "tour": 2, "visitor_info": 2,
    "attraction": 3, "park": 3, "place_of_worship": 3,
    "lodging": 3, "hotel": 3, "brewery": 3, "bar": 3,
    "restaurant": 4, "cafe": 4,
    "community_center": 4, "government": 4, "library": 4,
}

# ── Geofence radius by category ──────────────────────────────────────────
GEOFENCE_RADIUS_MAP = {
    "museum": 30, "witch_museum": 30, "historic_site": 30,
    "witch_shop": 25, "psychic": 25, "restaurant": 25, "bar": 25,
    "cafe": 25, "brewery": 25,
    "public_art": 20,
    "park": 50, "cemetery": 50,
    "tour": 35, "ghost_tour": 35,
    # Everything else defaults to 40
}
DEFAULT_GEOFENCE_RADIUS = 40

# ── Source field mapping ──────────────────────────────────────────────────
SOURCE_MAP = {
    "destination_salem": "destination_salem",
    "haunted_happenings": "haunted_happenings",
    "osm": "openstreetmap",
}


def make_id(name: str) -> str:
    """Create a slug ID from the POI name: lowercase, special chars to underscore, max 60."""
    slug = name.lower()
    slug = re.sub(r"[^a-z0-9]+", "_", slug)
    slug = slug.strip("_")
    return slug[:60]


def sql_str(value) -> str:
    """Return a SQL-safe string literal, or NULL if empty/None."""
    if value is None:
        return "NULL"
    s = str(value).strip()
    if not s:
        return "NULL"
    # Replace newlines/carriage returns with spaces to keep INSERT on one line
    s = s.replace("\r\n", " ").replace("\r", " ").replace("\n", " ")
    # Collapse multiple spaces
    s = re.sub(r"  +", " ", s)
    # Escape single quotes by doubling them
    s = s.replace("'", "''")
    return f"'{s}'"


def sql_real(value) -> str:
    """Return a SQL real literal."""
    if value is None:
        return "NULL"
    return str(float(value))


def build_confidence(poi: dict) -> float:
    """Derive confidence from source data."""
    if poi.get("multi_source"):
        return 1.0
    source = poi.get("source", "")
    if source == "destination_salem":
        return 0.95
    if source == "haunted_happenings":
        return 0.9
    if source == "osm":
        return 0.85
    return 0.8


def build_data_source(poi: dict) -> str:
    """Build data_source string from confirmed_sources."""
    sources = poi.get("confirmed_sources", [])
    if not sources:
        raw = poi.get("source", "manual_curated")
        return SOURCE_MAP.get(raw, raw)
    mapped = [SOURCE_MAP.get(s, s) for s in sources]
    if len(mapped) == 1:
        return mapped[0]
    return "+".join(sorted(mapped))


def generate_insert(poi: dict, now_ms: int) -> str:
    """Generate a single INSERT OR REPLACE statement for a POI."""
    cat = poi["category"]
    poi_id = make_id(poi["name"])
    wave = WAVE_MAP.get(cat, 1)
    priority = PRIORITY_MAP.get(cat, 3)
    geofence_radius = GEOFENCE_RADIUS_MAP.get(cat, DEFAULT_GEOFENCE_RADIUS)
    confidence = build_confidence(poi)
    data_source = build_data_source(poi)

    columns = (
        "id, name, lat, lng, address, type, "
        "short_narration, long_narration, description, "
        "image_asset, voice_clip_asset, "
        "geofence_radius_m, geofence_shape, corridor_points, "
        "priority, wave, "
        "related_figure_ids, related_fact_ids, related_source_ids, "
        "action_buttons, phone, website, hours, "
        "merchant_tier, ad_priority, "
        "custom_icon_asset, custom_voice_asset, custom_description, "
        "data_source, confidence, verified_date, "
        "created_at, updated_at, stale_after"
    )

    values = ", ".join([
        sql_str(poi_id),                          # id
        sql_str(poi["name"]),                      # name
        sql_real(poi["lat"]),                      # lat
        sql_real(poi["lng"]),                      # lng
        sql_str(poi.get("address")),               # address
        sql_str(cat),                              # type
        "NULL",                                    # short_narration
        "NULL",                                    # long_narration
        sql_str(poi.get("description")),           # description
        "NULL",                                    # image_asset
        "NULL",                                    # voice_clip_asset
        str(geofence_radius),                      # geofence_radius_m
        "'circle'",                                # geofence_shape
        "NULL",                                    # corridor_points
        str(priority),                             # priority
        str(wave),                                 # wave
        "NULL",                                    # related_figure_ids
        "NULL",                                    # related_fact_ids
        "NULL",                                    # related_source_ids
        "NULL",                                    # action_buttons
        sql_str(poi.get("phone")),                 # phone
        sql_str(poi.get("website")),               # website
        sql_str(poi.get("hours")),                 # hours
        "NULL",                                    # merchant_tier
        "0",                                       # ad_priority
        "NULL",                                    # custom_icon_asset
        "NULL",                                    # custom_voice_asset
        "NULL",                                    # custom_description
        sql_str(data_source),                      # data_source
        str(confidence),                           # confidence
        "'2026-04-06'",                            # verified_date
        str(now_ms),                               # created_at
        str(now_ms),                               # updated_at
        "0",                                       # stale_after
    ])

    return f"INSERT OR REPLACE INTO narration_points ({columns}) VALUES ({values});"


def main():
    # Load input
    with open(INPUT_FILE) as f:
        data = json.load(f)

    pois = data["pois"]
    assert len(pois) == 307, f"Expected 307 POIs, got {len(pois)}"

    now_ms = int(time.time() * 1000)

    # Track duplicates
    seen_ids = {}
    duplicate_count = 0

    # Build SQL lines
    create_table = """
-- ============================================================================
-- Narration Points (307 POIs — Wave 1: 113, Wave 2: 85, Wave 3: 109)
-- Generated by tools/salem-data/generate_narration_sql.py
-- ============================================================================

CREATE TABLE IF NOT EXISTS narration_points (
  id TEXT NOT NULL PRIMARY KEY,
  name TEXT NOT NULL,
  lat REAL NOT NULL,
  lng REAL NOT NULL,
  address TEXT,
  type TEXT NOT NULL,
  short_narration TEXT,
  long_narration TEXT,
  description TEXT,
  image_asset TEXT,
  voice_clip_asset TEXT,
  geofence_radius_m INTEGER NOT NULL DEFAULT 40,
  geofence_shape TEXT NOT NULL DEFAULT 'circle',
  corridor_points TEXT,
  priority INTEGER NOT NULL DEFAULT 3,
  wave INTEGER NOT NULL DEFAULT 1,
  related_figure_ids TEXT,
  related_fact_ids TEXT,
  related_source_ids TEXT,
  action_buttons TEXT,
  phone TEXT,
  website TEXT,
  hours TEXT,
  merchant_tier TEXT,
  ad_priority INTEGER NOT NULL DEFAULT 0,
  custom_icon_asset TEXT,
  custom_voice_asset TEXT,
  custom_description TEXT,
  data_source TEXT NOT NULL DEFAULT 'manual_curated',
  confidence REAL NOT NULL DEFAULT 1.0,
  verified_date TEXT,
  created_at INTEGER NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL DEFAULT 0,
  stale_after INTEGER NOT NULL DEFAULT 0
);
"""

    sql_lines = [create_table]

    # Sort POIs by wave, then priority, then name for organized output
    def sort_key(poi):
        cat = poi["category"]
        return (WAVE_MAP.get(cat, 1), PRIORITY_MAP.get(cat, 3), poi["name"])

    sorted_pois = sorted(pois, key=sort_key)

    current_wave = 0
    wave_counts = {1: 0, 2: 0, 3: 0}
    category_counts = {}

    for poi in sorted_pois:
        cat = poi["category"]
        wave = WAVE_MAP.get(cat, 1)

        # Wave header
        if wave != current_wave:
            current_wave = wave
            sql_lines.append(f"\n-- Wave {wave}")
            sql_lines.append(f"-- {'=' * 70}")

        # Handle duplicate IDs (append source_id suffix)
        poi_id = make_id(poi["name"])
        if poi_id in seen_ids:
            duplicate_count += 1
            # Append source_id to disambiguate
            src_id = poi.get("source_id", str(duplicate_count))
            poi_id_suffixed = f"{poi_id}_{src_id}"[:60]
            poi["_override_id"] = poi_id_suffixed
            seen_ids[poi_id_suffixed] = True
        else:
            seen_ids[poi_id] = True

        insert = generate_insert(poi, now_ms)

        # If we had to override the ID, patch it in
        if "_override_id" in poi:
            original_id = make_id(poi["name"])
            insert = insert.replace(
                f"'{original_id}'",
                f"'{poi['_override_id']}'",
                1  # only replace the first occurrence (the id field)
            )

        sql_lines.append(insert)
        wave_counts[wave] += 1
        category_counts[cat] = category_counts.get(cat, 0) + 1

    sql_output = "\n".join(sql_lines) + "\n"

    # Append to existing SQL file
    with open(OUTPUT_FILE, "a") as f:
        f.write(sql_output)

    # Print summary
    print("=" * 70)
    print("narration_points SQL generation complete")
    print("=" * 70)
    print(f"Output file: {OUTPUT_FILE}")
    print(f"Total POIs:  {len(sorted_pois)}")
    print(f"  Wave 1:    {wave_counts[1]} (core Salem attractions)")
    print(f"  Wave 2:    {wave_counts[2]} (secondary attractions)")
    print(f"  Wave 3:    {wave_counts[3]} (dining & services)")
    print(f"Duplicate IDs resolved: {duplicate_count}")
    print()
    print("Category breakdown:")
    for cat, count in sorted(category_counts.items(), key=lambda x: (-WAVE_MAP.get(x[0], 1) * -1, -x[1])):
        w = WAVE_MAP.get(cat, 1)
        p = PRIORITY_MAP.get(cat, 3)
        r = GEOFENCE_RADIUS_MAP.get(cat, DEFAULT_GEOFENCE_RADIUS)
        print(f"  {cat:25s}  wave={w}  priority={p}  radius={r:2d}m  count={count}")
    print()
    print(f"CREATE TABLE + {len(sorted_pois)} INSERT statements appended to salem_content.sql")


if __name__ == "__main__":
    main()
