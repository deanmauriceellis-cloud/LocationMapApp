#!/usr/bin/env python3
"""
S278 Katrina's Collection — SI clue-miner for badge-ghost prompts.

For each HIST_BLDG POI linked to SalemIntelligence via intel_entity_id, call SI's
/building/{id}/details + /entity/{id}/context and distill the surfaced clues
(builder, original_owner, notable_residents, notable_events, other_relations)
into ONE prompt-friendly profession phrase per POI.

Plagiarism rule: SI is clue-source, not content. Output prompt phrases are
common-noun keyword phrases ("1690s witch-trial magistrate", "1840s brooding
author"), NOT lifted SI prose. Names are dropped from the prompt by default;
the prompt feeds a stylized cartoon SD pipeline. See [[feedback_si_is_reference_only]].

Outputs:
  cache-proxy/data/ghost-persona-overrides.json
      { poi_id: { "profession": "...", "_si_clue": "...", "_confidence": 0.7 }, ... }
      Consumed by generate-ghost-portraits.py (next step) to override the random
      profession roll. POIs not in this file fall back to the random pool roll.

  cache-proxy/data/ghost-persona-overrides.csv
      Same data, operator-scannable: poi_id, name, year, profession, _si_clue, confidence

POIs with no usable SI clue (unlinked, low-confidence, or empty SI return) are
OMITTED from the overrides file — they correctly fall back to the existing
random-roll pool, which is what we shipped at S275.

Re-runnable. Hits SI once per linked POI (89 calls × 2 endpoints = 178 calls).
"""
import csv
import json
import os
import re
import sys
import time
from pathlib import Path
from typing import Optional

import psycopg2
import requests

REPO_ROOT = Path(__file__).resolve().parents[2]
ENV_FILE = REPO_ROOT / "cache-proxy" / ".env"
OUT_JSON = REPO_ROOT / "cache-proxy" / "data" / "ghost-persona-overrides.json"
OUT_CSV = REPO_ROOT / "cache-proxy" / "data" / "ghost-persona-overrides.csv"
SI_BASE = "http://localhost:8089"


def load_database_url() -> str:
    if "DATABASE_URL" in os.environ:
        return os.environ["DATABASE_URL"]
    for line in ENV_FILE.read_text().splitlines():
        m = re.match(r"^\s*DATABASE_URL\s*=\s*(.+)\s*$", line)
        if m:
            return m.group(1).strip().strip('"').strip("'")
    sys.exit("DATABASE_URL not set and not found in cache-proxy/.env")


def fetch_linked_hist_bldg() -> list[dict]:
    """Pull ALL HIST_BLDG POIs. SI calls skip for rows without intel_entity_id;
    the LMA-prose seam still applies to every row."""
    with psycopg2.connect(load_database_url()) as conn, conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, name, intel_entity_id, year_established, historical_period,
                   COALESCE(description, ''), COALESCE(short_narration, '')
              FROM salem_pois
             WHERE category = 'HISTORICAL_BUILDINGS'
               AND deleted_at IS NULL
             ORDER BY id
            """
        )
        return [
            {
                "poi_id": r[0],
                "name": r[1],
                "entity_id": r[2],
                "year": r[3],
                "period": r[4],
                "description": r[5],
                "short_narration": r[6],
            }
            for r in cur.fetchall()
        ]


def si_get(path: str) -> Optional[dict]:
    """GET with a short retry, return parsed JSON or None on miss/error."""
    url = f"{SI_BASE}{path}"
    for attempt in range(2):
        try:
            r = requests.get(url, timeout=10)
            if r.status_code == 200:
                return r.json()
            if r.status_code == 404:
                return None
        except Exception:
            time.sleep(0.5)
    return None


# Witch-trials magistrate seam: SI surfaces "presided over the Salem witch trials" /
# "examined the accused" / "signed the death warrant" — all of these → magistrate ghost.
TRIALS_HINTS = (
    "witch trial",
    "witch-trial",
    "preside",
    "magistrate",
    "examiner",
    "examined",
    "death warrant",
    "court of oyer",
)

# Era buckets — coarse, drives the wardrobe period in the prompt.
def era_phrase(year: Optional[int]) -> str:
    if year is None:
        return ""
    if year < 1700:
        return "1690s puritan colonial"
    if year < 1780:
        return "early 18th-century colonial"
    if year < 1830:
        return "Federal-era"
    if year < 1870:
        return "antebellum Victorian"
    if year < 1910:
        return "late-Victorian"
    return "early-20th-century"


# Two-pass LMA lexicon. NAME-pass is dispositive — if a building is literally named
# "X Cemetery" or "X Tavern", that wins regardless of what tangentially appears in the
# description. DESC-pass is a softer pull for buildings whose names are generic
# ("Joshua Ward House") but whose narrations name a specific historical figure.

# NAME-pass: matched against poi.name only. High precedence.
LMA_NAME_ARCHETYPES: list[tuple[tuple[str, ...], str]] = [
    (("cemetery", "burial ground", "burying point", "burying ground", "graveyard"), "weathered sexton (gravedigger)"),
    (("tavern", " inn", "ale house", "alehouse"), "tavern-keeper"),
    (("shoe shop", "cordwainer"),                "cordwainer (shoemaker)"),
    (("custom house", "customs"),                "customs officer with ledger"),
    (("powder house", "powderhouse"),            "powder-house keeper"),
    (("apothecary",),                            "apothecary with mortar and pestle"),
    (("schoolhouse",),                           "schoolmaster with cane"),
    (("meetinghouse", "first church", "second church"), "puritan minister"),
    (("almshouse", "poorhouse"),                 "weary almshouse-keeper"),
    (("armory", "muster"),                       "militia officer with musket"),
    (("light station", "lighthouse"),            "lighthouse keeper"),
    (("fort ",),                                 "fort gunner with cannon"),
    (("witch trials memorial", "proctor's ledge", "proctors ledge"), "wronged accused witch (gallows-bound)"),
    (("witch house",),                           "witch-trial magistrate"),
    (("jail",),                                  "weary jailer with iron keys"),
    (("wharf", "shipyard"),                      "merchant sea-captain"),
    (("samantha statue", "bewitched"),           "Hollywood TV witch in cocktail dress"),
    (("seven gables",),                          "brooding novelist with quill"),
    (("scarlet letter",),                        "brooding novelist with quill"),
    (("hawthorne",),                             "brooding novelist with quill"),
    (("bowditch",),                              "navigator with sextant and almanac"),
    (("mcintire",),                              "master woodcarver"),
    (("conant",),                                "puritan founder in cloak"),
    (("charlotte forten",),                      "schoolteacher abolitionist"),
    (("ww ii", "world war"),                     "stoic stone-watcher"),
    (("washington arch",),                       "stoic stone-watcher"),
    (("statue", "monument"),                     "stoic stone-watcher"),  # generic memorial
]

# DESC-pass: matched against description + narrations. Named-figure-only — used when
# NAME-pass came up empty. Generic trades are NOT here because they cause false hits
# (descriptions frequently mention adjacent buildings or eras).
LMA_DESC_ARCHETYPES: list[tuple[tuple[str, ...], str]] = [
    (("nathaniel hawthorne", "scarlet letter"),  "brooding novelist with quill"),
    (("nathaniel bowditch",),                    "navigator with sextant and almanac"),
    (("samuel mcintire", "mcintire"),            "master woodcarver"),
    (("bridget bishop",),                        "wronged accused witch (gallows-bound)"),
    (("rebecca nurse",),                         "wronged accused witch (gallows-bound)"),
    (("sarah good",),                            "wronged accused witch (gallows-bound)"),
    (("sarah wildes",),                          "wronged accused witch (gallows-bound)"),
    (("susannah martin",),                       "wronged accused witch (gallows-bound)"),
    (("mary easty", "mary eastey"),              "wronged accused witch (gallows-bound)"),
    (("john proctor", "elizabeth proctor"),      "wronged accused witch (gallows-bound)"),
    (("george burroughs",),                      "accused puritan minister (gallows-bound)"),
    (("john hathorne",),                         "witch-trial magistrate"),
    (("jonathan corwin",),                       "witch-trial magistrate"),
    (("samuel parris",),                         "puritan minister"),
    (("john endecott", "john endicott"),         "stern puritan governor"),
    (("frederick douglass",),                    "abolitionist orator"),
    (("alice parker",),                          "wronged accused witch (gallows-bound)"),
    (("friendship of salem", "tall ship"),       "merchant sea-captain"),  # the 1797 replica ship POI
]


def lma_prose_match(poi: dict) -> Optional[tuple[str, str, int]]:
    """Pattern-match LMA prose. Returns (profession, clue, tier_rank) or None.

    tier_rank: 1 = NAME-pass (high confidence, dispositive). 2 = DESC-pass (named figure).
    """
    name_lc = (poi.get("name") or "").lower()
    for keywords, profession in LMA_NAME_ARCHETYPES:
        for kw in keywords:
            if kw in name_lc:
                return profession, f"lma_name='{kw}'", 1

    desc_lc = " ".join(
        [poi.get("description") or "", poi.get("short_narration") or "", poi.get("period") or ""]
    ).lower()
    for keywords, profession in LMA_DESC_ARCHETYPES:
        for kw in keywords:
            if kw in desc_lc:
                return profession, f"lma_desc='{kw}'", 2
    return None


def distill_profession(
    poi: dict, building: Optional[dict], context: Optional[dict]
) -> tuple[Optional[str], Optional[str], float]:
    """Return (profession_phrase, raw_clue, confidence) or (None, None, 0) if no clue.

    Source priority (per feedback_si_is_reference_only):
      1. LMA prose match (LMA = source of truth)
      2. SI building.original_use (high-signal structured field)
      3. SI relations / context (free-text hints)
      4. (museum primary-category fallback REMOVED — too generic, made 20+ badges samey)

    Profession phrase is a common-noun keyword string ready to drop into the SD
    prompt's `{profession}` slot. No names. No lifted SI prose.
    """
    era = era_phrase(poi.get("year"))
    confidence = 0.0

    # Tier 1 — LMA prose (source of truth). Two-pass: name first (dispositive), then desc.
    lma_hit = lma_prose_match(poi)
    if lma_hit:
        prof, clue, tier_rank = lma_hit
        # Era-prefix when it makes sense; some phrases (like Hollywood TV witch) shouldn't take a 1690s prefix.
        skip_era = any(s in prof for s in ("Hollywood", "stone-watcher"))
        conf = 0.85 if tier_rank == 1 else 0.7
        return (prof if skip_era else f"{era} {prof}".strip(), clue, conf)

    # Tier 2 — SI building.original_use (structured, high-signal).
    if building:
        confidence = float(building.get("confidence") or 0)
        notable_residents = building.get("notable_residents") or []
        ownership_chain = building.get("ownership_chain") or []
        original_use = (building.get("original_use") or "").lower()

        # Trial-related residents / owners.
        for entry in notable_residents + ownership_chain:
            blob = json.dumps(entry).lower()
            if any(h in blob for h in TRIALS_HINTS):
                return (f"{era} witch-trial magistrate".strip(), blob[:200], max(confidence, 0.7))

        if "tavern" in original_use or "inn" in original_use:
            return (f"{era} tavern-keeper".strip(), f"original_use={original_use}", max(confidence, 0.6))
        if "shop" in original_use or "store" in original_use:
            return (f"{era} shopkeeper".strip(), f"original_use={original_use}", max(confidence, 0.6))
        if "school" in original_use:
            return (f"{era} schoolmaster".strip(), f"original_use={original_use}", max(confidence, 0.6))
        if "church" in original_use or "meeting" in original_use:
            return (f"{era} puritan minister".strip(), f"original_use={original_use}", max(confidence, 0.6))
        if "wharf" in original_use or "warehouse" in original_use or "customs" in original_use:
            return (f"{era} merchant sea-captain".strip(), f"original_use={original_use}", max(confidence, 0.6))

    # Tier 3 — SI relations free-text.
    if context:
        relations = context.get("other_relations") or []
        for rel in relations:
            desc = (rel.get("description") or "").lower()
            if any(h in desc for h in TRIALS_HINTS):
                return (f"{era} witch-trial magistrate".strip(), desc[:200], max(confidence, 0.6))
            if "author" in desc or "wrote" in desc or "novelist" in desc:
                return (f"{era} brooding novelist with quill".strip(), desc[:200], max(confidence, 0.55))
            if "captain" in desc or "shipmaster" in desc or "maritime" in desc:
                return (f"{era} merchant sea-captain".strip(), desc[:200], max(confidence, 0.55))
            if "minister" in desc or "reverend" in desc or "pastor" in desc:
                return (f"{era} puritan minister".strip(), desc[:200], max(confidence, 0.55))

    return (None, None, 0.0)


def main() -> None:
    pois = fetch_linked_hist_bldg()
    print(f"[pg] {len(pois)} linked HIST_BLDG POIs to probe", flush=True)

    overrides: dict[str, dict] = {}
    csv_rows = []
    skipped = []

    for i, poi in enumerate(pois, 1):
        building = None
        context = None
        if poi.get("entity_id"):
            building = si_get(f"/api/intel/building/{poi['entity_id']}/details")
            context = si_get(f"/api/intel/entity/{poi['entity_id']}/context")
        profession, clue, conf = distill_profession(poi, building, context)
        if profession:
            overrides[poi["poi_id"]] = {
                "profession": profession,
                "_si_clue": clue,
                "_confidence": round(conf, 2),
                "_name": poi["name"],
                "_year": poi["year"],
            }
            csv_rows.append([poi["poi_id"], poi["name"], poi["year"] or "", profession, conf, clue or ""])
            print(f"  [{i:3d}/{len(pois)}] {poi['poi_id']:40s}  →  {profession}", flush=True)
        else:
            skipped.append(poi["poi_id"])
            print(f"  [{i:3d}/{len(pois)}] {poi['poi_id']:40s}  →  (fallback to random pool)", flush=True)

    OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    OUT_JSON.write_text(json.dumps(overrides, indent=2, sort_keys=True))
    with OUT_CSV.open("w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["poi_id", "name", "year", "profession", "confidence", "_si_clue"])
        w.writerows(csv_rows)

    print(
        f"\n[out] {len(overrides)} overrides → {OUT_JSON.relative_to(REPO_ROOT)}"
        f" / {OUT_CSV.relative_to(REPO_ROOT)}"
    )
    print(f"[out] {len(skipped)} POIs fall back to random pool")


if __name__ == "__main__":
    main()
