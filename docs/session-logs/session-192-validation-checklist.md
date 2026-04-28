# S192 — Operator Validation Checklist

Each item below is a **specific decision** that needs your sign-off before
we run the generator on the full POI set. For each: read the question,
look at the current behavior, pick one of the options (replace [ ] with [X]),
and add notes if you're picking "Other" or want a tweak.

---

## SECTION A — Per-narration accuracy fact-check

**Goal: confirm each generated narration is factually accurate and stylistically acceptable.**

For each narration, read the full text in `session-192-narrations-review.md` (or
.odt). Then mark Approve / Reject / Needs-Edit.

---

### A1. The Witch House at Salem (1650, RICH, 308 words)

**Key claims to verify:**
- Built circa 1650
- Jonathan Corwin was a magistrate of the colony
- Examinations of the accused were held within the house
- Timber-framed structure

**Your verdict:**
- [ ] Approve as-is
- [ ] Reject — facts are wrong (explain below)
- [ ] Needs edit — text is mostly right but has issues (explain below)

Notes: ____________________________________________________________

---

### A2. Gedney House (1665, RICH, 317 words)

**Key claims to verify:**
- Built circa 1665 by Eleazor Gedney
- Bartholomew Gedney was Eleazor's brother and a witch-trial magistrate
- John Gedney (forebear) was an early Salem settler
- Some examinations held within the house

**Your verdict:**
- [ ] Approve as-is
- [ ] Reject — facts are wrong (explain below)
- [ ] Needs edit — text is mostly right but has issues (explain below)

Notes: ____________________________________________________________

---

### A3. Narbonne House (1675, RICH, 280 words)

**Key claims to verify:**
- First proprietor was Thomas Ives
- Examinations held within its walls
- Mentions Corwin and Hathorne as magistrates
- Mentions Abigail Williams and Ann Putnam as accusers

⚠ **Concern:** Were Corwin/Hathorne/Williams/Putnam actually documented at
Narbonne House specifically, or did the model pull these names from
generic 1692-trial KB context and attribute them to this house?

**Your verdict:**
- [ ] Approve as-is — claims are accurate
- [ ] Reject — those names should not be attached to Narbonne House
- [ ] Needs edit — keep the house facts, drop the trial-name attributions
- [ ] I need to check the source records first

Notes: ____________________________________________________________

---

### A4. John Ward House (1684, RICH, 206 words)

**Key claims to verify:**
- Built circa 1684
- First Period style
- John Ward as owner

⚠ Generic — Dow restoration sentence was stripped. Output is thin but accurate.

**Your verdict:**
- [ ] Approve as-is — thin but acceptable
- [ ] Reject — needs to be hand-authored later, leave column NULL
- [ ] Needs edit (explain below)

Notes: ____________________________________________________________

---

### A5. Peirce-Nichols House (1782, RICH, 251 words)

**Key claims to verify:**
- Built 1782 by Samuel McIntire (architect AND builder)
- Original owner Jerathmiel Peirce, merchant/privateer
- Peirce partnered with a Mr. Waite

✓ Witch-trial hallucination from prior sweep is GONE.

**Your verdict:**
- [ ] Approve as-is
- [ ] Reject — facts are wrong (explain below)
- [ ] Needs edit (explain below)

Notes: ____________________________________________________________

---

### A6. Joshua Ward House (1784, RICH, 298 words)

**Key claims to verify:**
- Built 1784, one of Salem's earliest brick dwellings
- Samuel McIntire did the construction and interior woodwork
- George Washington stayed there in 1789 during his Salem visit

**Your verdict:**
- [ ] Approve as-is
- [ ] Reject — facts are wrong (explain below)
- [ ] Needs edit (explain below)

Notes: ____________________________________________________________

---

### A7. Ropes Mansion (1727, RICH, 112 words)

**Key claims to verify:**
- Built between 1727 and 1729
- Built for the Ropes family
- Four generations of Ropes lived there
- Georgian architecture

⚠ Thin output — only 112 words, KB has limited pre-1860 material on this one.

**Your verdict:**
- [ ] Approve as-is — thin but accurate
- [ ] Reject — needs to be hand-authored later, leave column NULL
- [ ] Needs edit (explain below)

Notes: ____________________________________________________________

---

### A8. Gardner-Pingree House (1800, STANDARD, 77 words)

**Key claims to verify:**
- Built circa 1800
- Domestic architecture on Essex Street

⚠ Very thin — almost no specific facts. Did NOT mention McIntire (who actually
designed it) or the 1830 Joseph White murder (the Gardner-Pingree's most
famous historical event, and very pre-1860).

**Your verdict:**
- [ ] Approve as-is — thin but accurate
- [ ] Reject — should be hand-authored, this needs the McIntire + White-murder content
- [ ] Try a re-prompt with explicit "mention McIntire and Joseph White murder if grounded"

Notes: ____________________________________________________________

---

### A9. Hamilton Hall (1805, STANDARD, 68 words)

**Key claims to verify:**
- Built circa 1805
- Federal style, designed by a Salem architect
- Named in honor of Alexander Hamilton (Founding Father / Federalist)
- Used as a social hall

**Your verdict:**
- [ ] Approve as-is
- [ ] Reject — facts are wrong (explain below)
- [ ] Needs edit (explain below)

Notes: ____________________________________________________________

---

### A10. Custom House (1815, STANDARD, 120 words)

**Key claims to verify:**
- Built circa 1815
- Department of the Treasury jurisdiction
- Setting and subject of Hawthorne's *Scarlet Letter* introductory essay
- Hawthorne's "Main Street" sketch (Snow Image collection) referenced this building

**Your verdict:**
- [ ] Approve as-is
- [ ] Reject — facts are wrong (explain below)
- [ ] Needs edit (explain below)

Notes: ____________________________________________________________

---

## SECTION B — Behavior & rule decisions

---

### B1. Commemorative pre-filter

**Question: should the generator skip POIs whose name matches the
commemorative pattern, regardless of LMA year?**

**Current pattern:** `statue | monument | plaque | marker | memorial |
cenotaph | obelisk | tablet | bust`

**Why this exists:** statues etc. are post-1860 artifacts even when
honoring an earlier subject (e.g., Roger Conant Statue is 1913 even
though Conant settled Salem in 1626).

**Currently catches:**
- Roger Conant Statue ✓
- Salem Witch Trials Plaque ✓
- Proctor's Ledge Memorial ✓

**Your decision:**
- [ ] Keep as-is
- [ ] Add patterns: __________ (e.g., "Site of", "Statue of")
- [ ] Remove patterns: __________
- [ ] Disable this filter (let model handle it via rule #5)

Notes: ____________________________________________________________

---

### B2. Witch-trial year guard

**Question: when should the generator set witch_trial=true (which
triggers richer trial-focused content)?**

**Current rule:** witch_trial=true if **(a)** entity year is in [1670, 1710],
OR **(b)** the entity's `mode_a_entity.description` literally contains
"witch trial" or "Salem witch trials".

**Why this exists:** without the guard, the Peirce-Nichols House (1782)
hallucinated a witch-trial paragraph because KB relations linked it to
trial-era figures via surname matches. The guard prevented that.

**Your decision on the year window:**
- [ ] Keep [1670, 1710] as-is
- [ ] Tighten to [1685, 1700] (only houses that existed during the actual trials)
- [ ] Tighten to [1675, 1700]
- [ ] Widen to [1660, 1715]
- [ ] Other: __________

Notes: ____________________________________________________________

---

### B3. Sentence-level rescue (instead of full reject)

**Question: when a banned-phrase violation appears in one sentence,
should we keep the rest of the narration?**

**Current behavior:** strip the violating sentence + any following
sentences that begin with a pronoun ("He undertook…", "His work…"). If
the remaining text still meets the word floor, accept it.

**Example:** John Ward House had the sentence "*In the early years of the
twentieth century, an antiquarian turned his attention to the house*"
plus a following sentence "*His work ensured...*". Both stripped. The 4
preceding pre-1860 paragraphs were kept.

**Your decision:**
- [ ] Keep sentence-rescue as-is
- [ ] Stricter — any violation rejects the whole narration (return EMPTY)
- [ ] Looser — also try to clean phrases mid-sentence (e.g., delete just
  "twentieth-century antiquarian" without dropping the whole sentence)

Notes: ____________________________________________________________

---

### B4. Word-floor lock-down

**Question: should the generator accept short, sparse-grounding
narrations (e.g., 60-150 words) or insist on rich output?**

**Current behavior:** floor at **60 words for both buckets**. Prompt still
asks for 250-500 words in RICH if grounded. Sparse-grounding POIs
produce 60-150 words instead of EMPTY.

**Examples:**
- Ropes Mansion at 112 words — ACCEPTED (was previously REJECT)
- Hamilton Hall at 68 words — ACCEPTED
- Gardner-Pingree at 77 words — ACCEPTED

**Your decision:**
- [ ] Keep floor at 60 / both buckets
- [ ] Raise RICH floor back to 100 (sparse RICH POIs go EMPTY)
- [ ] Raise RICH floor back to 200 (originally proposed)
- [ ] Lower floor further to 40

Notes: ____________________________________________________________

---

### B5. Era-vocabulary rules

**Question: are these temporal-framing rules correct for the
narrations?**

**Current rules:**
- 1626-1691 → "Massachusetts Bay Colony" / "the colony" / "colonial settlement"
- 1692-1775 → "Province of Massachusetts Bay" / "the colony"
- 1776-1779 → "the colony" or "newly-declared United States"
- 1780-1859 → "Commonwealth of Massachusetts" / "United States"
- Pre-1776 → ban "United States" / "America" / "American"
- Pre-1780 → ban "Commonwealth of Massachusetts"
- 1626-1660 → may reference "Old Planters of Salem" if grounded

**Your decision:**
- [ ] All rules correct as-is
- [ ] One or more rules are wrong (specify below)

Notes: ____________________________________________________________

---

### B6. Old Planters list

**Question: is this list of Old Planters correct and complete?**

**Current list (Conant's 1626 founding group):**
Roger Conant, John Balch, Peter Palfrey, Thomas Gardner, William Trask,
John Woodbury, John Tilly, Walter Knight, William Allen, Anthony Dike

**Your decision:**
- [ ] List is correct and complete
- [ ] Add names: __________
- [ ] Remove names: __________
- [ ] Drop the Old Planters mention entirely

Notes: ____________________________________________________________

---

### B7. Voice / persona

**Question: the prompt tells the LLM to write "*as if delivered by a
careful 1859 historian*". Is this the right voice?**

**Effect:** this is why the narrations sound formal / archaic ("the year
of our Lord 1692", "stood as a silent observer", "in these troubled
times"). It locks the model into pre-1860 vocabulary naturally.

**Your decision:**
- [ ] Keep "1859 historian" voice
- [ ] Switch to "modern historian writing only about pre-1860 facts"
  (more readable, but risks contemporary phrasing leaks)
- [ ] Switch to "neutral past-tense narrator" (drops the period flavor)
- [ ] Other: __________

Notes: ____________________________________________________________

---

## SECTION C — Coverage & next steps

---

### C1. intel_entity_id backfill

**Status now:** 53 of 597 HISTORICAL_BUILDINGS have `intel_entity_id`
populated. The other 544 are skipped by the generator (no SI link → no
grounding source).

**Plan:** match LMA POIs to SI entities via SI's `geocode-sync` endpoint
(6,570 historic_building entries). Match criteria: coordinate distance
< 30m AND fuzzy name match (token overlap or Levenshtein).

**Your decision:**
- [ ] Proceed with backfill as described
- [ ] Tighten match criteria (e.g., 15m + stricter name match)
- [ ] Loosen match criteria (e.g., 50m)
- [ ] Skip backfill — only run on the 53 already linked
- [ ] I want to spot-check the bridge logic on 5 samples first

Notes: ____________________________________________________________

---

### C2. Full-run kickoff

**Question: after backfill (~400-500 eligible POIs), kick off the full
generator run as a background agent?**

**Estimated runtime:** ~30-60 sec per POI × 500 = ~5-8 hours. Background
agent is appropriate.

**Your decision:**
- [ ] Yes, kick off background agent after backfill
- [ ] Run in foreground in batches of 50 so I can spot-check
- [ ] Hold — I want to review more samples first before committing

Notes: ____________________________________________________________

---

### C3. Post-run pipeline order

**Plan:**
1. Room v11 → v12 schema bump (add `historicalNarration` column)
2. Update `publish-salem-pois.js` to include the column
3. Admin UI Historical Narration field in PoiEditDialog
4. Lint check `hist_pre1860_no_historical_narration` (coverage gaps)
5. APK rebuild + Lenovo install + verify

**Your decision:**
- [ ] Order is correct
- [ ] Re-order to: __________
- [ ] Skip step __________
- [ ] Add step: __________

Notes: ____________________________________________________________

---

## SECTION D — Final approval gate

**Question: are we approved to proceed?**

- [ ] APPROVED — proceed with backfill + full run + post-run pipeline
- [ ] APPROVED with the changes noted above (apply changes, then re-test on the same 13 POIs, then proceed)
- [ ] NOT APPROVED — significant rework needed (explain below)

Notes / overall thoughts: ____________________________________________________________

________________________________________________________________________

________________________________________________________________________
