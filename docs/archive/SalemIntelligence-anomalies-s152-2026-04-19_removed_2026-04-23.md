# SalemIntelligence Anomaly Report — LMA Session 152

**Date:** 2026-04-19
**Filed by:** LocationMapApp Session 152 during SI KB absorption
**SI state at time of report:** API `:8089` up, 21,417 entities, 1,560 BCS, 317 HBD, 9,574 short_narrations, 219/219 historical_notes, 3 active verified_facts (Heritage Trail, Town Pump, Black Mary Widow)
**LMA state at time of report:** 1,830 active POIs, 1,401 intel-linked

This file collects anomalies observed during LMA's re-sync from SI. Each item is scoped so SI can act on it (confirm, fix, or reject). No LMA-side unilateral changes were made based on these findings — they are SI-side questions.

---

## ANOM-001 — GP-rooftop phantom coord still live for 9 LMA-linked entities

**Severity:** HIGH — they place unrelated POIs on a single rooftop in downtown Salem.

**Observed:** Nine entities in `/api/intel/poi-export` currently return the exact phantom coord `(42.5227091, -70.8950943)` (30 Church St rooftop, a known phantom-pivot registered in `salemint/geocode_guards.py::KNOWN_PHANTOM_PIVOTS`).

**SI note:** Session 021 report said "10/10 original residuals clean" — that was about LMA's original report list (8 cleared S017 + Winer Bros S019 + Silsbee's S021). These 9 are a separate leak, likely written before the guard landed and never re-geocoded.

**The 9 entities:**

| entity_id | display_name | street_address | primary_category |
|---|---|---|---|
| 266a3604-4180-43be-8a34-2fed8d390d6c | Ben Rekemeyer Photography | "Salem, MA" (no street) | tour_operator |
| a7dd9710-b85b-4844-b9a1-59493b135ce7 | BraidsbyShitems | (none) | SHOPPING (LMA category; SI BCS may differ) |
| ac70b06e-f6d7-4394-bef6-04c75935590c | CAR WASH ADVERTISING, INC | (none) | AUTO_SERVICES |
| ab21e303-e6ed-4ef1-b761-cca0ab4b2782 | Haunted Neighborhood | (none) | ENTERTAINMENT |
| 966ba860-dc58-44a3-b9cc-300923d0cc27 | Monsters of Salem | (none) | CIVIC |
| 6abc8fcf-0777-41f2-b1b9-507715ce4b68 | MoveMore Performance Health | (none) | HEALTHCARE |
| 2e63b84b-f523-41be-8703-177560a99e2d | Red Antler Apothecary | 30 Church St, Salem, MA 01970 | shop_retail |
| aae256dd-f373-434b-a4c0-a718503cf190 | The Warlock House | "Salem, MA" (no street) | hotel_lodging |
| 5a923eca-fff4-4538-bcf0-e88514d39e6d | Tours for Touring Tourists | (none) | tour_operator |

**Legitimate exception:** Red Antler Apothecary's address IS 30 Church St — the coord may be correct for that one entity (and the rooftop becomes the phantom only when OTHER addresses resolve there). SI should verify, probably by querying the actual building's polygon centroid.

**Ask of SI:**
1. Re-run `safe_set_entity_coord` against the 8 non-30-Church-St entities with NULL-ing the coord if the address can't be resolved to a non-phantom location.
2. Confirm Red Antler Apothecary's coord is correct (30 Church St address actually lives there).
3. Consider a one-time retroactive scan: `UPDATE entities SET latitude=NULL, longitude=NULL WHERE (latitude, longitude) IN (KNOWN_PHANTOM_PIVOTS) AND street_address NOT MATCHING the phantom address.`

---

---

## ANOM-002 — Heritage Trail `verified_fact` correction only propagated to `short_narration`; long/description/historical_note still confabulate a "2020 Red Line revision"

**Severity:** HIGH — this is exactly the kind of fabrication the verified_facts layer was built to prevent, and the correction is incomplete.

**Entity:** `8cf46ca3-7668-42b2-ab38-6177a97e0113` — Salem Heritage Trail
**LMA id:** `salem_heritage_trail`
**Verified_fact seed:** `00368eca-81a9-4cf1-8225-492ae694cf99` (S019) — "The Salem Heritage Trail is a yellow painted line on the sidewalk"

**What landed in LMA after SI re-sync (content from `/api/intel/entity/{id}/narration`):**

- `short_narration` (CLEAN, correction landed):
  > "Follow the Salem Heritage Trail, a yellow painted line on the sidewalk, to discover connections between Salem's earliest settlers, the Witch Trials, and its diverse history."

- `long_narration` (CONFABULATION — still live):
  > "The Salem Heritage Trail is a yellow painted line on the sidewalk... In 2020, the trail underwent a significant revision, removing references to its original moniker, the 'Red Line,' which carried offensive connotations. This change reflects a commitment to inclusivity and a more nuanced understanding of Salem's history..."

- `description` / `medium_narration` (CONFABULATION):
  > "Recently revised to remove references to its original, offensive 'Red Line' history, the trail now promotes inclusivity and a more complete understanding of Salem's past."

- `historical_note` (CONFABULATION):
  > "In 2020, the trail underwent revisions to remove references to its original, problematic 'Red Line' history, furthering its commitment to inclusivity."

**Why we call it confabulation:** There is no historical record of the Salem Heritage Trail being previously named "Red Line" or undergoing a 2020 revision to remove "offensive connotations." The LLM appears to be inventing a revision narrative to justify the verified_fact ("yellow"), rather than simply stating the fact. SI S020 reported this exact hallucination ("underwent revisions to remove references to its original, problematic 'Red Line' history") was gone in the short_narration — but it survived in the long/medium/historical_note tiers.

**Probable mechanism:** The verified_facts layer may be applied per prompt-template, and short_narration's template has `verified_facts` injected but medium / long / historical_note templates do not (or inject in a weaker position). Alternatively, the LLM sees the verified_fact as a constraint ("must say yellow") and invents a world-consistent reason ("because it was revised from red").

**Ask of SI:**
1. Confirm whether `verified_facts` are injected into all four narration templates (short/medium/long + historical_note).
2. If yes, consider strengthening the directive: "do not invent a historical origin story for the current state unless one exists in the KB."
3. Consider a post-synthesis contradiction scan targeted at verified_fact patterns — e.g., "Red Line" as a proper noun relating to Heritage Trail should trigger a warning. (This matches your S021 TOP PRIORITY #5 — post-synthesis contradiction scan.)
4. Regenerate all 4 tiers for this entity after the fix.

**LMA mitigation (pending operator direction):** Short narration is safe to ship. Long / description / historical_note need either operator-authored replacements or nulling to prevent shipping the confabulation.

---

## ANOM-003 — `historical_note` coverage for LMA intel-linked POIs is thin (33 of 1,401)

**Severity:** LOW — informational / product coverage question, not a fabrication.

**Observed:** Of LMA's 1,401 intel-linked POIs, only 33 receive a historical_note from `/api/intel/entity/{id}/historical_note`. SI's total historical_note count is 219/219 tour candidates. The intersection is narrow because LMA's POI set and SI's tour-candidate set overlap on a small subset.

**Ask of SI:** Is this expected? Historical Mode in LMA falls back to short_narration + a narration-note requirement in `NarrationGeofenceManager.isHistoricalQualified` when historical_note is null, so this is not a hard blocker. But if SI's tour-candidate definition could be widened to include more LMA-linked entities (historic buildings, civic entities, worship sites), Historical Mode coverage in LMA would improve.

Not blocking. Consider at leisure.

---

## ANOM-004 — 3 404s and 2 thin-skips on `/api/intel/entity/{id}/narration` (out of 1,401)

**Severity:** INFO — expected per SI's OMEN-012 §9 ("failure > fabrication") posture, but documenting for completeness.

**Observed:** Out of 1,401 LMA intel-linked entities, 3 returned 404 on `/api/intel/entity/{id}/narration` (narration not yet generated) and 2 were skipped because SI returned empty content. 1,398/1,401 received narrations. 1,399 rows were written to PG (the delta includes BCS-field updates even for the 1 entity that fell through narration but had BCS content to write).

This matches SI's stated behavior — it refuses to narrate when KB support is thin. LMA-side fallback: short_description from BCS is used if narration is empty, which is working.

Not actionable on SI side unless SI's long-narration ceiling (~22%) lifts via upstream enrichment.

---

_(More anomalies appended below as discovered in subsequent sync steps.)_
