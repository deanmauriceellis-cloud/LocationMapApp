"""
Reads the Salem JSON corpus at ~/Development/Salem/data/json/ and buckets
events + facts for each of the 16 Witch Trials History articles.

Public surface:
- ARTICLES: list of 16 dicts (tile_order, tile_kind, period_label, title,
  date_range, related_event_ids, related_fact_ids).
- load_corpus(salem_root) -> dict {events, facts, primary_sources}
- bucket_articles(corpus) -> list of 16 enriched article dicts ready for
  prompt rendering. Each dict gains 'events_for_period' and 'facts_for_period'.

Notes on the corpus:
- Events are dated (game_date 1692-01-01 .. 1693-12-XX) and are anchor-numbered.
  We have 40 events total, 12 in March 1692 (peak), zero in Nov + Dec 1692.
- Facts (the aggregate `_all_facts.json`) carry a `date` + `date_precision`
  field; ~1,743 of 3,855 are dated. We bucket by date for monthly articles
  and fall back to NPC-overlap for the intro / closing / epilogue tiles.
  Nov 1692 has 6 dated facts; Dec 1692 has 2 — enough to anchor the
  "calm between executions" tiles without going hollow.
- Primary sources are referenced by id from facts (`source` field) and live in
  ~/Development/Salem/data/json/primary_sources/. Loaded but not auto-bucketed
  here — the prompt template asks Oracle to cite them via its own retrieval.
"""

import json
import os
from pathlib import Path

SALEM_ROOT = Path(os.path.expanduser("~/Development/Salem/data/json"))

# Soft cap on facts per article so the prompt stays under Oracle's context budget.
FACTS_PER_ARTICLE_CAP = 25

# The 16 articles. tile_order matches the tile grid position in the History panel.
ARTICLES = [
    {
        "tile_order": 1, "tile_kind": "intro",
        "period_label": "Before 1692 — The Village in Crisis",
        "title": "The Village Before the Storm",
        "date_range": ("1689-01-01", "1691-12-31"),
        "framing": "Set the stage. Salem Village's split from Salem Town, the Putnam–Porter feud, the controversial appointment of Reverend Samuel Parris, the long-running tax revolt over his salary and firewood, the Indian War to the north pushing refugees south, the loss of the Massachusetts Bay charter. By the end of 1691 the village was already in a low-grade civil war. The afflictions had not yet started.",
    },
    {
        "tile_order": 2, "tile_kind": "month", "period_label": "January 1692",
        "title": "January 1692 — The First Signs",
        "date_range": ("1692-01-01", "1692-01-31"),
        "framing": "Winter in Salem Village. The Parris parsonage, Betty Parris (9) and Abigail Williams (11) begin showing strange behaviors — fits, contortions, screaming. Tituba and John Indian, enslaved in the parsonage. The kitchen-circle fortune-telling experiments with the Venus glass.",
    },
    {
        "tile_order": 3, "tile_kind": "month", "period_label": "February 1692",
        "title": "February 1692 — The Doctor's Verdict",
        "date_range": ("1692-02-01", "1692-02-29"),
        "framing": "Doctor William Griggs is summoned and renders the diagnosis: 'the evil hand'. Tituba bakes the witch cake at Mary Sibley's instruction, fed to a dog to identify the witch. Ann Putnam Jr (12) joins the afflicted. The accusations turn outward.",
    },
    {
        "tile_order": 4, "tile_kind": "month", "period_label": "March 1692",
        "title": "March 1692 — The First Warrants",
        "date_range": ("1692-03-01", "1692-03-31"),
        "framing": "Warrants issued for Sarah Good, Sarah Osborne, and Tituba (Feb 29 / Mar 1). Examinations at Ingersoll's Ordinary and the Salem Village Meeting House. Tituba's confession — yellow bird, the Devil's book, the man in black — and her naming of others. The crisis becomes a public spectacle. By month's end Martha Corey and Rebecca Nurse have been accused.",
    },
    {
        "tile_order": 5, "tile_kind": "month", "period_label": "April 1692",
        "title": "April 1692 — The Net Widens",
        "date_range": ("1692-04-01", "1692-04-30"),
        "framing": "Examinations move to Salem Town. Deputy Governor Thomas Danforth presides. Mary Warren (Proctor servant) flips and unflips. The accused now include Bridget Bishop, Giles Corey, Mary Easty, Sarah Cloyce. Spectral evidence dominates.",
    },
    {
        "tile_order": 6, "tile_kind": "month", "period_label": "May 1692",
        "title": "May 1692 — The Court Convenes",
        "date_range": ("1692-05-01", "1692-05-31"),
        "framing": "Governor Phips arrives from England with the new charter. The Court of Oyer and Terminer is established under Chief Justice William Stoughton. Cotton Mather sends his cautionary letter ('Return of Several Ministers') warning against over-reliance on spectral evidence — the warning is largely ignored.",
    },
    {
        "tile_order": 7, "tile_kind": "month", "period_label": "June 1692",
        "title": "June 1692 — The First Hanging",
        "date_range": ("1692-06-01", "1692-06-30"),
        "framing": "Bridget Bishop tried June 2, hanged June 10 at Proctor's Ledge. Nathaniel Saltonstall resigns from the court in protest. Five more women — Sarah Good, Sarah Wilds, Elizabeth Howe, Susannah Martin, Rebecca Nurse — convicted at the end of the month.",
    },
    {
        "tile_order": 8, "tile_kind": "month", "period_label": "July 1692",
        "title": "July 1692 — Five Hang at Proctor's Ledge",
        "date_range": ("1692-07-01", "1692-07-31"),
        "framing": "July 19: Sarah Good, Sarah Wilds, Elizabeth Howe, Susannah Martin, Rebecca Nurse hanged together. Sarah Good's curse on Reverend Nicholas Noyes ('I am no more a witch than you are a wizard, and if you take away my life God will give you blood to drink').",
    },
    {
        "tile_order": 9, "tile_kind": "month", "period_label": "August 1692",
        "title": "August 1692 — Reverend Burroughs Hangs",
        "date_range": ("1692-08-01", "1692-08-31"),
        "framing": "August 19: George Burroughs (the former Salem Village minister, accused as the ringleader of the witches), John Proctor, John Willard, George Jacobs Sr, Martha Carrier hanged. Burroughs recites the Lord's Prayer perfectly on the scaffold — supposedly impossible for a witch — but Cotton Mather convinces the crowd to proceed.",
    },
    {
        "tile_order": 10, "tile_kind": "month", "period_label": "September 1692",
        "title": "September 1692 — Pressing and Eight More",
        "date_range": ("1692-09-01", "1692-09-30"),
        "framing": "September 19: Giles Corey is pressed to death with stones for refusing to enter a plea — 'more weight'. September 22: Martha Corey, Mary Easty, Alice Parker, Ann Pudeator, Margaret Scott, Wilmott Redd, Samuel Wardwell, Mary Parker hanged. The last public executions of the trials.",
    },
    {
        "tile_order": 11, "tile_kind": "month", "period_label": "October 1692",
        "title": "October 1692 — The Tide Turns",
        "date_range": ("1692-10-01", "1692-10-31"),
        "framing": "Increase Mather's 'Cases of Conscience Concerning Evil Spirits' published — argues against spectral evidence ('it were better that ten suspected witches should escape than one innocent person be condemned'). Governor Phips dissolves the Court of Oyer and Terminer at month's end. His own wife had been accused.",
    },
    {
        "tile_order": 12, "tile_kind": "month", "period_label": "November 1692",
        "title": "November 1692 — A Strange Calm",
        "date_range": ("1692-11-01", "1692-11-30"),
        "framing": "No executions, no new sessions. The Court of Oyer and Terminer is dissolved; the Superior Court of Judicature is being constituted to replace it. Roughly 150 people remain in jail awaiting trial. Some begin to be released on bond. The afflictions in the village quiet down — though they have not stopped entirely. The village holds its breath.",
        "is_quiet_month": True,
    },
    {
        "tile_order": 13, "tile_kind": "month", "period_label": "December 1692",
        "title": "December 1692 — Winter in the Jails",
        "date_range": ("1692-12-01", "1692-12-31"),
        "framing": "Cold winter. The accused who remain in jail — many in Boston Prison, Salem Jail, Ipswich — face brutal conditions. Sarah Good's infant daughter Mercy has already died in jail; others will follow. Governor Phips issues the order constituting the Superior Court of Judicature. Trials will resume in January under new rules: spectral evidence is no longer admissible.",
        "is_quiet_month": True,
    },
    {
        "tile_order": 14, "tile_kind": "fallout", "period_label": "1693 — The Trials End",
        "title": "1693 — The Court Without Spectral Evidence",
        "date_range": ("1693-01-01", "1693-12-31"),
        "framing": "January–May 1693: the Superior Court of Judicature tries the remaining cases without spectral evidence. Of 56 indictments, only 3 result in conviction — and Phips reprieves them. May 1693: Phips issues a general jailhouse delivery, releasing all remaining accused. The trials are effectively over. Twenty have been executed. Five more have died in jail. Sentences are vacated piecemeal over the next twenty years; full restitution will not arrive until centuries later.",
    },
    {
        "tile_order": 15, "tile_kind": "closing", "period_label": "The Aftermath (1694–1711)",
        "title": "The Aftermath — Public Apologies and the First Restitutions",
        "date_range": ("1694-01-01", "1711-12-31"),
        "framing": "1697: Samuel Sewall, one of the trial judges, stands in Old South Church and publicly apologizes — the only judge to do so. The same year, twelve trial jurors sign a public confession of error. 1702: the Massachusetts General Court declares the trials unlawful. 1706: Ann Putnam Jr (the most prolific child accuser) issues a public apology in Salem Village Church. 1711: the Massachusetts legislature reverses the attainders of most of the convicted and grants £600 in restitution to families. Some names remain unrestored for centuries.",
    },
    {
        "tile_order": 16, "tile_kind": "epilogue", "period_label": "The Long Memory",
        "title": "The Long Memory — How Salem Carries 1692",
        "date_range": ("1712-01-01", "2026-12-31"),
        "framing": "Three centuries of memory. Hawthorne's gothic shame (his great-great-grandfather John Hathorne was the unrepentant chief examiner). Arthur Miller's 'The Crucible' as McCarthy-era allegory. The Salem Witch Trials Memorial dedicated 1992 (300th anniversary). The 2017 dedication of Proctor's Ledge as the actual execution site — closing a 325-year geographic mystery. Salem today: a city that lives inside the trauma it inflicted, and the global tourist economy that has grown around it. The 2026 quadricentennial.",
    },
]


def load_corpus(salem_root=SALEM_ROOT):
    """Read events, facts, primary_sources, npcs from disk."""
    salem_root = Path(salem_root)

    events_path = salem_root / "events" / "_all_events.json"
    events = json.loads(events_path.read_text())

    # Prefer the aggregate _all_facts.json (carries date + date_precision).
    # Fall back to per-file scan only if the aggregate is missing.
    all_facts_path = salem_root / "facts" / "_all_facts.json"
    if all_facts_path.exists():
        facts = json.loads(all_facts_path.read_text())
    else:
        facts_dir = salem_root / "facts"
        facts = []
        for p in sorted(facts_dir.glob("*.json")):
            if p.name.startswith("_"):
                continue
            try:
                d = json.loads(p.read_text())
                if isinstance(d, dict):
                    facts.append(d)
            except json.JSONDecodeError:
                pass

    primary_sources_dir = salem_root / "primary_sources"
    primary_sources = []
    if primary_sources_dir.exists():
        for p in sorted(primary_sources_dir.glob("*.json")):
            try:
                primary_sources.append(json.loads(p.read_text()))
            except json.JSONDecodeError:
                pass

    return {
        "events": events,
        "facts": facts,
        "primary_sources": primary_sources,
    }


def _events_in_range(events, start_iso, end_iso):
    """Return events whose game_date falls in [start_iso, end_iso] inclusive."""
    out = []
    for e in events:
        d = e.get("game_date", "")
        if start_iso <= d <= end_iso:
            out.append(e)
    return sorted(out, key=lambda e: e.get("game_date", ""))


def _facts_in_range(facts, start_iso, end_iso, cap=FACTS_PER_ARTICLE_CAP):
    """Date-bucket facts whose `date` falls in [start_iso, end_iso] inclusive."""
    matching = []
    for f in facts:
        if not isinstance(f, dict):
            continue
        d = f.get("date") or ""
        if d and start_iso <= d <= end_iso:
            matching.append(f)

    def sort_key(f):
        cat = f.get("category", "")
        priority = 0 if cat in ("biographical", "key_event", "trial_event") else 1
        return (priority, f.get("date", ""), f.get("id", ""))
    matching.sort(key=sort_key)
    return matching[:cap]


def _facts_for_event_npcs(facts, events, cap=FACTS_PER_ARTICLE_CAP):
    """Fallback for tiles without dated facts: pick facts whose npcs_involved
    overlap with the period's events' npc_placements."""
    npc_ids = set()
    for e in events:
        for placement in e.get("npc_placements", []) or []:
            if placement.get("npc_id"):
                npc_ids.add(placement["npc_id"])

    if not npc_ids:
        return []

    matching = []
    for f in facts:
        if not isinstance(f, dict):
            continue
        f_npcs = set(f.get("npcs_involved", []) or [])
        if f_npcs & npc_ids:
            matching.append(f)

    def sort_key(f):
        cat = f.get("category", "")
        priority = 0 if cat in ("biographical", "key_event", "trial_event") else 1
        return (priority, f.get("id", ""))
    matching.sort(key=sort_key)
    return matching[:cap]


def bucket_articles(corpus):
    """Return ARTICLES enriched with events_for_period + facts_for_period."""
    events = corpus["events"]
    facts = corpus["facts"]

    out = []
    for art in ARTICLES:
        start, end = art["date_range"]
        period_events = _events_in_range(events, start, end)

        # Date-bucket facts for this period; fall back to NPC-overlap if the
        # date bucket comes up dry (intro / closing / epilogue tiles).
        period_facts = _facts_in_range(facts, start, end)
        if not period_facts:
            period_facts = _facts_for_event_npcs(facts, period_events)

        enriched = dict(art)
        enriched["events_for_period"] = period_events
        enriched["facts_for_period"] = period_facts
        enriched["event_count"] = len(period_events)
        enriched["fact_count"] = len(period_facts)
        out.append(enriched)

    return out


if __name__ == "__main__":
    corpus = load_corpus()
    print(f"loaded events={len(corpus['events'])} facts={len(corpus['facts'])} primary_sources={len(corpus['primary_sources'])}")
    enriched = bucket_articles(corpus)
    for art in enriched:
        print(f"  #{art['tile_order']:2d} {art['tile_kind']:8s} {art['period_label']:40s} events={art['event_count']:2d} facts={art['fact_count']:2d}")
