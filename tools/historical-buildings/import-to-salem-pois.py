#!/usr/bin/env python3
"""Import MassGIS MHC building footprints into salem_pois as hidden POIs.

For each of the 503 in-scope MHC records (NHL/NRIND/LHD, named):
  1. Compute footprint centroid, parse name, join L3 assessor for year_built.
  2. Look for an existing salem_pois row within ~15m whose name fuzzy-matches
     the MHC name. If found → ENRICH that row's mhc_* + building_footprint_geojson
     + mhc_nr_status. No duplicate created.
  3. Otherwise → INSERT a new hidden POI (default_visible=false,
     has_announce_narration=true, category=HISTORICAL_BUILDINGS,
     short_narration=composed text, geofence_radius_m=20, is_narrated=true).

The admin can later "grow" a hidden POI by editing it in the existing POI admin:
flip default_visible=true, flesh out narration, set merchant tier, etc.

Idempotent. `admin_dirty` POIs are never clobbered (neither enrichment nor
re-insert overwrites their fields).

Usage:
  cd cache-proxy && set -a && source .env && set +a && cd ..
  python3 tools/historical-buildings/import-to-salem-pois.py [--dry-run]
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
from pathlib import Path

import psycopg2
import psycopg2.extras
from shapely.geometry import shape

REPO = Path(__file__).resolve().parents[2]
GEOJSON_PATH = REPO / "tools" / "tile-bake" / "salem-historic-buildings.geojson"

INDIVIDUAL_TOKENS = {"NHL", "NRIND", "LHD"}
ENRICH_RADIUS_M = 15.0  # ~0.00014° of latitude
OUTBUILDING_MARKERS = (
    "Carriage House", "Carriage Houses", "Summer House", "Counting House",
    "Double House", "Triple House", "Row House", "Privy", "Barn",
    "Warehouse", "Store", "Shop", "Office", "Studio",
    "Cabinetmaker Shop", "Photography Studio",
)
PERSON_NAME_RE = re.compile(
    r"^(?P<surname>[A-Z][A-Za-z\.\-']+),\s+(?P<given>[A-Z][A-Za-z\.\-' ]+?)\s+(?P<suffix>House|Houses|Cottage|Residence|Homestead|Estate|Farm|Place)\s*$",
    re.IGNORECASE,
)
PERSON_TOKEN_RE = re.compile(r"^(?P<surname>[A-Z][A-Za-z\.\-']+),\s+(?P<given>[A-Z][A-Za-z\.\-' ]+)$")


def has_individual_designation(designation: str) -> bool:
    return bool(set(designation.split()) & INDIVIDUAL_TOKENS)


def _invert_person_token(tok: str) -> tuple[str, bool]:
    m = PERSON_TOKEN_RE.match(tok.strip())
    if m:
        return f"{m.group('given').strip()} {m.group('surname').strip()}", True
    return tok.strip(), False


def parse_name(name: str) -> tuple[str, str | None, bool]:
    """(display_name, builder_or_None, is_institutional)"""
    lower = name.lower()
    for marker in OUTBUILDING_MARKERS:
        if lower.endswith(marker.lower()):
            body = name[: -len(marker)].strip().rstrip(",")
            m_token = PERSON_TOKEN_RE.match(body)
            if m_token:
                return f"{m_token.group('given').strip()} {m_token.group('surname').strip()} {marker}", None, True
            return name, None, True

    m = PERSON_NAME_RE.match(name)
    if m:
        given = m.group("given").strip()
        surname = m.group("surname").strip()
        suffix = m.group("suffix").strip()
        return f"{given} {surname} {suffix.capitalize()}", f"{given} {surname}", False

    if " - " in name:
        parts = [s.strip() for s in name.split(" - ")]
        tail = ""
        while len(parts) > 1 and "," not in parts[-1]:
            tail = (parts.pop() + " " + tail).strip()
        if parts:
            last = parts[-1]
            tail_words = []
            tokens = last.split()
            descriptors = {
                "house", "houses", "cottage", "residence", "homestead", "estate",
                "farm", "place", "store", "shop", "warehouse", "building", "block",
                "double", "triple", "carriage", "summer", "winter", "row", "frame",
                "brick", "stone", "wood", "studio", "barn", "privy", "office",
            }
            while tokens and tokens[-1].rstrip(",.").lower() in descriptors:
                tail_words.insert(0, tokens.pop())
            parts[-1] = " ".join(tokens).strip().rstrip(",")
            if tail_words:
                tail = (" ".join(tail_words) + " " + tail).strip()
        inverted = [_invert_person_token(tok)[0] for tok in parts if tok]
        joined = " - ".join(inverted)
        if tail:
            joined = f"{joined} {tail}".strip()
        return joined, None, True

    return re.sub(r"\s*-\s*", " - ", name).strip(), None, True


def compose_narration(display_name: str, builder: str | None, is_institutional: bool, year: int | None) -> str | None:
    if year is None:
        return None
    if is_institutional or builder is None:
        return f"This is the {display_name}, built in {year}."
    return f"This building was built in {year} by {builder}."


def stable_id(address: str | None, name: str) -> str:
    key = f"{(address or '').strip().lower()}|{name.strip().lower()}"
    h = hashlib.sha256(key.encode("utf-8")).hexdigest()[:12]
    return f"hb_{h}"


def normalize_for_match(s: str | None) -> str:
    if not s:
        return ""
    # Strip punctuation, collapse whitespace, lowercase.
    s = re.sub(r"[,\.]", " ", s.lower())
    s = re.sub(r"\s+", " ", s).strip()
    return s


def lookup_year_built(tiger_cur, lon: float, lat: float) -> tuple[int | None, str | None]:
    tiger_cur.execute(
        """
        SELECT loc_id
          FROM massgis.l3_parcels_essex
         WHERE geom && ST_SetSRID(ST_MakePoint(%s, %s), 4269)
           AND ST_Contains(geom, ST_SetSRID(ST_MakePoint(%s, %s), 4269))
           AND poly_type = 'FEE'
         ORDER BY shape_area ASC
         LIMIT 1
        """,
        (lon, lat, lon, lat),
    )
    row = tiger_cur.fetchone()
    if not row or not row[0]:
        return None, None
    tiger_cur.execute(
        """
        SELECT (raw->>'YEAR_BUILT')::int
          FROM massgis.l3_assess_essex
         WHERE loc_id = %s
           AND raw ? 'YEAR_BUILT'
           AND raw->>'YEAR_BUILT' ~ '^[0-9]+$'
         ORDER BY (raw->>'FY')::int DESC NULLS LAST
         LIMIT 1
        """,
        (row[0],),
    )
    yr = tiger_cur.fetchone()
    if not yr or yr[0] is None:
        return None, None
    year = int(yr[0])
    if year < 1600 or year > 2026:
        return None, None
    return year, "l3_assessor"


def find_enrichment_candidate(lma_cur, lat: float, lon: float, name: str, display_name: str) -> str | None:
    """Return the id of an existing salem_pois row that should be enriched
    (rather than duplicated) for this MHC record. None if no good match."""
    lat_box = ENRICH_RADIUS_M / 111_000.0  # ≈ degrees per meter
    lon_box = lat_box / 0.76  # rough cos(42°N)
    lma_cur.execute(
        """
        SELECT id, name, lat, lng, mhc_id
          FROM salem_pois
         WHERE deleted_at IS NULL
           AND lat BETWEEN %s AND %s
           AND lng BETWEEN %s AND %s
           AND id NOT LIKE 'hb_%%'       -- don't enrich a previous-run hidden POI with itself
        """,
        (lat - lat_box, lat + lat_box, lon - lon_box, lon + lon_box),
    )
    candidates = lma_cur.fetchall()
    if not candidates:
        return None

    norm_display = normalize_for_match(display_name)
    norm_raw = normalize_for_match(name)
    display_tokens = set(norm_display.split()) - {"house", "houses", "cottage", "building", "the", "of", "and"}

    # Preferred: strong name overlap.
    for row in candidates:
        cid, cname = row[0], row[1]
        norm_c = normalize_for_match(cname)
        if not norm_c:
            continue
        if norm_c == norm_display or norm_c == norm_raw:
            return cid
        # Token-overlap heuristic — at least 2 distinctive tokens in common.
        c_tokens = set(norm_c.split()) - {"house", "houses", "cottage", "building", "the", "of", "and"}
        overlap = display_tokens & c_tokens
        if len(overlap) >= 2:
            return cid
        # Substring either direction, if the shorter side is ≥ 6 chars (avoids "st" matches)
        if len(norm_c) >= 6 and norm_c in norm_display:
            return cid
        if len(norm_display) >= 6 and norm_display in norm_c:
            return cid

    return None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true", help="Plan only, no writes")
    ap.add_argument("--limit", type=int, default=None)
    args = ap.parse_args()

    print(f"[read] {GEOJSON_PATH}")
    with GEOJSON_PATH.open() as f:
        fc = json.load(f)
    feats = fc.get("features", [])
    in_scope = [
        f for f in feats
        if (f.get("properties") or {}).get("name")
        and has_individual_designation((f.get("properties") or {}).get("designation") or "")
    ]
    print(f"[filter] {len(in_scope)} in-scope (NHL/NRIND/LHD + named)")
    if args.limit:
        in_scope = in_scope[: args.limit]
        print(f"[filter] --limit: {len(in_scope)}")

    tiger = psycopg2.connect(dbname="tiger", user=os.environ.get("USER", "witchdoctor"))
    tiger_cur = tiger.cursor()

    dburl = os.environ.get("DATABASE_URL")
    if not dburl:
        print("ERROR: DATABASE_URL not set. `source cache-proxy/.env` first.", file=sys.stderr)
        return 1
    lma = psycopg2.connect(dburl)
    lma_cur = lma.cursor()

    stats = {
        "in_scope": len(in_scope),
        "enrich_hits": 0,
        "new_inserts": 0,
        "skipped_admin_dirty": 0,
        "upsert_updates": 0,   # re-run: id already exists, refresh
        "no_year": 0,
    }

    for f in in_scope:
        p = f["properties"]
        name = p["name"]
        designation = p.get("designation") or ""
        address = p.get("address")

        display_name, builder, is_institutional = parse_name(name)
        geom_obj = shape(f["geometry"])
        c = geom_obj.centroid
        lon, lat = c.x, c.y
        footprint_geojson = json.dumps(f["geometry"], separators=(",", ":"))

        year, year_source = lookup_year_built(tiger_cur, lon, lat)
        if year is None:
            stats["no_year"] += 1
        narration = compose_narration(display_name, builder, is_institutional, year)

        # Decision: enrich an existing POI, or create a new hidden one?
        existing_id = find_enrichment_candidate(lma_cur, lat, lon, name, display_name)

        if existing_id is not None:
            # ENRICH path
            if args.dry_run:
                stats["enrich_hits"] += 1
                continue
            lma_cur.execute(
                """
                UPDATE salem_pois
                   SET building_footprint_geojson = COALESCE(building_footprint_geojson, %s),
                       mhc_year_built             = COALESCE(mhc_year_built, %s),
                       mhc_nr_status              = COALESCE(mhc_nr_status, %s),
                       mhc_narrative              = COALESCE(mhc_narrative, %s),
                       updated_at                 = NOW()
                 WHERE id = %s
                   AND deleted_at IS NULL
                """,
                (footprint_geojson, year, designation, narration, existing_id),
            )
            stats["enrich_hits"] += 1
            continue

        # INSERT path: new hidden POI
        new_id = stable_id(address, name)

        if args.dry_run:
            stats["new_inserts"] += 1
            continue

        # Preserve admin_dirty rows on re-run.
        lma_cur.execute(
            "SELECT admin_dirty FROM salem_pois WHERE id = %s",
            (new_id,),
        )
        row = lma_cur.fetchone()
        if row and row[0]:
            stats["skipped_admin_dirty"] += 1
            continue
        is_rerun = row is not None
        if is_rerun:
            stats["upsert_updates"] += 1
        else:
            stats["new_inserts"] += 1

        lma_cur.execute(
            """
            INSERT INTO salem_pois (
                id, name, lat, lng, address, category, subcategory,
                short_narration,
                geofence_radius_m, geofence_shape,
                priority,
                merchant_tier, ad_priority,
                historical_period,
                requires_transportation, wheelchair_accessible, seasonal,
                data_source, confidence,
                is_tour_poi, is_narrated, default_visible,
                has_announce_narration,
                building_footprint_geojson,
                mhc_year_built, mhc_nr_status, mhc_narrative,
                admin_dirty,
                created_at, updated_at
            ) VALUES (
                %s, %s, %s, %s, %s, 'HISTORICAL_BUILDINGS', NULL,
                %s,
                20, 'circle',
                4,
                0, 0,
                'mhc_inventory',
                FALSE, TRUE, FALSE,
                'massgis_mhc', 0.9,
                FALSE, TRUE, FALSE,
                TRUE,
                %s,
                %s, %s, %s,
                FALSE,
                NOW(), NOW()
            )
            ON CONFLICT (id) DO UPDATE SET
                name                       = EXCLUDED.name,
                lat                        = EXCLUDED.lat,
                lng                        = EXCLUDED.lng,
                address                    = EXCLUDED.address,
                short_narration            = EXCLUDED.short_narration,
                building_footprint_geojson = EXCLUDED.building_footprint_geojson,
                mhc_year_built             = EXCLUDED.mhc_year_built,
                mhc_nr_status              = EXCLUDED.mhc_nr_status,
                mhc_narrative              = EXCLUDED.mhc_narrative,
                updated_at                 = NOW()
              WHERE salem_pois.admin_dirty = FALSE
            """,
            (
                new_id, display_name, lat, lon, address,
                narration,
                footprint_geojson,
                year, designation, narration,
            ),
        )

    if not args.dry_run:
        lma.commit()
    lma_cur.close()
    lma.close()
    tiger_cur.close()
    tiger.close()

    print()
    print("Import summary")
    print(f"  in-scope:               {stats['in_scope']}")
    print(f"  enriched existing POIs: {stats['enrich_hits']}")
    print(f"  new hidden POIs added:  {stats['new_inserts']}")
    print(f"  re-run refreshes:       {stats['upsert_updates']}")
    print(f"  skipped admin_dirty:    {stats['skipped_admin_dirty']}")
    print(f"  no year_built:          {stats['no_year']}")
    if args.dry_run:
        print("  (dry run — no writes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
