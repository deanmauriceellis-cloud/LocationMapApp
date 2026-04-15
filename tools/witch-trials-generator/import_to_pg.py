"""
Loads output/article-NN.json checkpoints into salem_witch_trials_articles.

Idempotent: ON CONFLICT (id) DO UPDATE so re-runs replace the row in place
(without flipping admin_dirty). Stamps the data_source / confidence /
generator_model / generator_prompt_hash provenance fields per the Phase 9X.2
spec and leaves verified_date NULL for human review.

Requires DATABASE_URL in env.

Usage:
  python import_to_pg.py                  # load all output/article-*.json
  python import_to_pg.py --only 1,4,12    # load specific tile orders
  python import_to_pg.py --dry-run        # show SQL, don't execute
"""

import argparse
import json
import os
import sys
from pathlib import Path

import psycopg2
import psycopg2.extras
from dotenv import load_dotenv

THIS_DIR = Path(__file__).resolve().parent
OUTPUT_DIR = THIS_DIR / "output"

# DATABASE_URL canonically lives in cache-proxy/.env. Load that first; if the
# operator has it in the shell env already, that wins (load_dotenv default).
CACHE_PROXY_ENV = THIS_DIR.parent.parent / "cache-proxy" / ".env"
if CACHE_PROXY_ENV.exists():
    load_dotenv(CACHE_PROXY_ENV)

DATA_SOURCE = "ollama_direct_salem_village"
CONFIDENCE = 0.7

# tile_order → stable string id used as PRIMARY KEY in salem_witch_trials_articles.
TILE_ID = {
    1: "pre_1692",
    2: "jan_1692",
    3: "feb_1692",
    4: "mar_1692",
    5: "apr_1692",
    6: "may_1692",
    7: "jun_1692",
    8: "jul_1692",
    9: "aug_1692",
    10: "sep_1692",
    11: "oct_1692",
    12: "nov_1692",
    13: "dec_1692",
    14: "fallout_1693",
    15: "closing",
    16: "epilogue",
}


UPSERT_SQL = """
INSERT INTO salem_witch_trials_articles (
    id, tile_order, tile_kind, title, period_label, teaser, body,
    related_event_ids, related_newspaper_dates, related_npc_ids,
    data_source, confidence, verified_date, generator_model, generator_prompt_hash,
    admin_dirty, admin_dirty_at, updated_at
) VALUES (
    %(id)s, %(tile_order)s, %(tile_kind)s, %(title)s, %(period_label)s,
    %(teaser)s, %(body)s,
    %(related_event_ids)s::jsonb, '[]'::jsonb, '[]'::jsonb,
    %(data_source)s, %(confidence)s, NULL, %(generator_model)s, %(generator_prompt_hash)s,
    FALSE, NULL, NOW()
)
ON CONFLICT (id) DO UPDATE SET
    tile_order = EXCLUDED.tile_order,
    tile_kind = EXCLUDED.tile_kind,
    title = EXCLUDED.title,
    period_label = EXCLUDED.period_label,
    teaser = EXCLUDED.teaser,
    body = EXCLUDED.body,
    related_event_ids = EXCLUDED.related_event_ids,
    data_source = EXCLUDED.data_source,
    confidence = EXCLUDED.confidence,
    generator_model = EXCLUDED.generator_model,
    generator_prompt_hash = EXCLUDED.generator_prompt_hash,
    updated_at = NOW()
WHERE NOT salem_witch_trials_articles.admin_dirty;
"""


def parse_only(spec):
    if not spec:
        return None
    return {int(p.strip()) for p in spec.split(",") if p.strip()}


def load_articles(only=None):
    articles = []
    for p in sorted(OUTPUT_DIR.glob("article-*.json")):
        rec = json.loads(p.read_text())
        if only is not None and rec["tile_order"] not in only:
            continue
        articles.append(rec)
    return articles


def to_pg_row(rec):
    tile_order = rec["tile_order"]
    if tile_order not in TILE_ID:
        raise ValueError(f"no PK mapping for tile_order={tile_order}")
    return {
        "id":                    TILE_ID[tile_order],
        "tile_order":            tile_order,
        "tile_kind":             rec["tile_kind"],
        "title":                 rec["title"],
        "period_label":          rec["period_label"],
        "teaser":                rec["teaser"],
        "body":                  rec["body"],
        "related_event_ids":     json.dumps(rec.get("related_event_ids", [])),
        "data_source":           DATA_SOURCE,
        "confidence":            CONFIDENCE,
        "generator_model":       rec.get("generator_model"),
        "generator_prompt_hash": rec.get("generator_prompt_hash"),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", help="Comma-separated tile_orders to import.")
    parser.add_argument("--dry-run", action="store_true", help="Print payloads without executing.")
    args = parser.parse_args()

    db_url = os.environ.get("DATABASE_URL")
    if not db_url and not args.dry_run:
        sys.exit("FATAL: DATABASE_URL not set in env (and not --dry-run)")

    only = parse_only(args.only)
    articles = load_articles(only=only)
    print(f"[plan] loading {len(articles)} article(s) from {OUTPUT_DIR}")
    if not articles:
        sys.exit("no article-*.json files found in output/")

    rows = [to_pg_row(a) for a in articles]

    if args.dry_run:
        for r in rows:
            print(f"\n--- tile {r['tile_order']:2d} ({r['tile_kind']}) id={r['id']!r} title={r['title']!r}")
            print(f"    teaser: {r['teaser'][:120]}{'...' if len(r['teaser']) > 120 else ''}")
            print(f"    body:   {len(r['body'])} chars")
            print(f"    provenance: data_source={r['data_source']} confidence={r['confidence']} model={r['generator_model']} hash={r['generator_prompt_hash'][:12] if r['generator_prompt_hash'] else None}")
        print("\n[dry-run] no DB writes performed")
        return

    conn = psycopg2.connect(db_url)
    conn.autocommit = False
    try:
        with conn.cursor() as cur:
            skipped_dirty = 0
            for r in rows:
                # Detect admin_dirty rows so we can report what we skipped (the
                # WHERE clause in UPSERT_SQL silently skips them).
                cur.execute("SELECT admin_dirty FROM salem_witch_trials_articles WHERE id = %s", (r["id"],))
                existing = cur.fetchone()
                if existing and existing[0]:
                    print(f"[skip] tile {r['tile_order']:2d} id={r['id']!r} — admin_dirty=TRUE (preserve operator edits)")
                    skipped_dirty += 1
                    continue
                cur.execute(UPSERT_SQL, r)
                print(f"[ok]   tile {r['tile_order']:2d} id={r['id']!r} title={r['title']!r}")
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()

    print(f"\n[done] inserted/updated {len(rows) - skipped_dirty}, skipped {skipped_dirty} dirty")


if __name__ == "__main__":
    main()
