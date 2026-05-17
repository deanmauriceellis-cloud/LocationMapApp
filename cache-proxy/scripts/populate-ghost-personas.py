#!/usr/bin/env python3
"""
S275 Katrina's Collection — populate salem_pois ghost_asset_a/b/ghost_frame columns
from the SD batch manifest.

Steps:
  1. Load ~/AI-Studio/ghost-batch-v1/manifest.json (107 rows, one per HIST_BLDG POI)
  2. Load frame slug list from cache-proxy/data/ghost-personas-seed.json
  3. For each row: derive frame_slug from SHA1(slug) % len(frames) (matches inspector)
  4. Enrich manifest in place (adds frame_slug field for canonical reference)
  5. UPDATE salem_pois SET ghost_asset_a, ghost_asset_b, ghost_frame WHERE id = poi_id

Asset paths follow image_asset convention: full asset-relative path with extension.
  ghost_asset_a = ghosts/ghost_<poi_id>_a.webp
  ghost_asset_b = ghosts/ghost_<poi_id>_b.webp
  ghost_frame   = frames/frame_<slug>.webp

Re-runnable. Same hash + same manifest = same result.
"""
import hashlib
import json
import os
import re
import sys
from pathlib import Path

import psycopg2

REPO_ROOT = Path(__file__).resolve().parents[2]
SEED_FILE = REPO_ROOT / "cache-proxy" / "data" / "ghost-personas-seed.json"
ENV_FILE = REPO_ROOT / "cache-proxy" / ".env"
MANIFEST = Path.home() / "AI-Studio" / "ghost-batch-v1" / "manifest.json"


def load_database_url() -> str:
    if "DATABASE_URL" in os.environ:
        return os.environ["DATABASE_URL"]
    for line in ENV_FILE.read_text().splitlines():
        m = re.match(r"^\s*DATABASE_URL\s*=\s*(.+)\s*$", line)
        if m:
            return m.group(1).strip().strip('"').strip("'")
    sys.exit("DATABASE_URL not set and not found in cache-proxy/.env")


def derive_frame_slug(slug: str, frame_slugs: list[str]) -> str:
    h = int(hashlib.sha1(slug.encode()).hexdigest()[:8], 16)
    return frame_slugs[h % len(frame_slugs)]


def main() -> None:
    if not MANIFEST.exists():
        sys.exit(f"No manifest at {MANIFEST} — run generate-ghost-portraits.py --from-pg first")

    manifest = json.loads(MANIFEST.read_text())
    seed = json.loads(SEED_FILE.read_text())
    frame_slugs = [f["slug"] for f in seed["frames"]]

    # Enrich + build update rows.
    rows = []
    for row in manifest:
        poi_id = row.get("poi_id")
        slug = row["slug"]
        if not poi_id:
            continue   # validation/test entries from --count mode have no poi_id
        frame_slug = row.get("frame_slug") or (row.get("frame") or {}).get("slug")
        if not frame_slug:
            frame_slug = derive_frame_slug(slug, frame_slugs)
            row["frame_slug"] = frame_slug   # write back into manifest
        rows.append(
            {
                "poi_id": poi_id,
                "ghost_asset_a": f"ghosts/{slug}_a.webp",
                "ghost_asset_b": f"ghosts/{slug}_b.webp",
                "ghost_frame":   f"frames/frame_{frame_slug}.webp",
            }
        )

    print(f"[manifest] {len(rows)} ghost rows to populate")

    # Persist the enriched manifest so frame_slug is canonical for future runs.
    MANIFEST.write_text(json.dumps(manifest, indent=2))
    print(f"[manifest] frame_slug enriched in place: {MANIFEST}")

    with psycopg2.connect(load_database_url()) as conn, conn.cursor() as cur:
        updated = 0
        not_found = []
        for r in rows:
            cur.execute(
                """
                UPDATE salem_pois
                   SET ghost_asset_a = %s,
                       ghost_asset_b = %s,
                       ghost_frame   = %s,
                       updated_at    = NOW()
                 WHERE id = %s
                """,
                (r["ghost_asset_a"], r["ghost_asset_b"], r["ghost_frame"], r["poi_id"]),
            )
            if cur.rowcount == 0:
                not_found.append(r["poi_id"])
            else:
                updated += cur.rowcount
        conn.commit()

    print(f"[pg] updated {updated} salem_pois rows with ghost asset paths")
    if not_found:
        print(f"[pg] WARNING — {len(not_found)} poi_ids in manifest not found in salem_pois:")
        for p in not_found:
            print(f"    {p}")

    # Quick frame distribution report.
    from collections import Counter
    dist = Counter(r["ghost_frame"].rsplit("/", 1)[1].replace("frame_", "").replace(".webp", "")
                   for r in rows)
    print("\n[frames] distribution across 107 ghosts:")
    for frame, count in sorted(dist.items(), key=lambda kv: -kv[1]):
        print(f"    {count:3d}  {frame}")


if __name__ == "__main__":
    main()
