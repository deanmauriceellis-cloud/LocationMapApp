#!/usr/bin/env python3
"""Export hero image prompts from SalemIntelligence DB to a JSON manifest.

Run with the SalemIntelligence venv:
    ~/Development/SalemIntelligence/.venv/bin/python3 export-hero-prompts.py

Reads DATABASE_URL from ~/Development/SalemIntelligence/.env
Outputs: hero-prompts.json (consumed by generate.py)
"""

import json
import os
import sys
from pathlib import Path

# Load DATABASE_URL from SI .env
SI_ROOT = Path.home() / "Development" / "SalemIntelligence"
env_file = SI_ROOT / ".env"
if not env_file.exists():
    print(f"ERROR: {env_file} not found", file=sys.stderr)
    sys.exit(1)

for line in env_file.read_text().splitlines():
    line = line.strip()
    if line and not line.startswith("#") and "=" in line:
        key, _, val = line.partition("=")
        os.environ.setdefault(key.strip(), val.strip())

db_url = os.environ.get("DATABASE_URL")
if not db_url:
    print("ERROR: DATABASE_URL not found in .env", file=sys.stderr)
    sys.exit(1)

from sqlalchemy import create_engine, text

engine = create_engine(db_url)

QUERY = text("""
    SELECT
        e.id::text AS entity_id,
        e.name,
        e.entity_type,
        e.address,
        e.latitude,
        e.longitude,
        e.hero_image_prompt,
        e.hero_image_prompt_sha,
        b.primary_category,
        b.display_name
    FROM entities e
    LEFT JOIN business_current_state b ON b.entity_id = e.id
    WHERE e.hero_image_prompt IS NOT NULL
    ORDER BY
        CASE e.entity_type
            WHEN 'business' THEN 0
            WHEN 'attraction' THEN 1
            WHEN 'civic_entity' THEN 2
            WHEN 'place' THEN 3
            WHEN 'historic_building' THEN 4
            ELSE 5
        END,
        e.name
""")

print("Querying SalemIntelligence DB for hero prompts...")

with engine.connect() as conn:
    rows = conn.execute(QUERY).fetchall()

manifest = []
for row in rows:
    try:
        prompt_data = json.loads(row.hero_image_prompt)
    except (json.JSONDecodeError, TypeError):
        continue

    hero_prompt = prompt_data.get("hero_prompt")
    if not hero_prompt:
        continue

    manifest.append({
        "entity_id": row.entity_id,
        "name": row.display_name or row.name,
        "entity_type": row.entity_type,
        "category": row.primary_category,
        "address": row.address,
        "lat": row.latitude,
        "lng": row.longitude,
        "hero_prompt": hero_prompt,
        "vibe_keywords": prompt_data.get("vibe_keywords"),
        "confidence": prompt_data.get("confidence", 0),
        "prompt_sha": row.hero_image_prompt_sha,
    })

output_path = Path(__file__).parent / "hero-prompts.json"
output_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False))

print(f"Exported {len(manifest)} hero prompts to {output_path}")

# Summary by type
from collections import Counter
type_counts = Counter(e["entity_type"] for e in manifest)
for t, c in type_counts.most_common():
    print(f"  {t}: {c}")
