# Oracle Newspaper Digest — 16 History Tiles

Generate 16 "Oracle Newspaper Digests" for the History panel's 4x4 tile grid.
Each digest is a monthly (or thematic) summary of the Salem Witch Trials,
written in the voice of "The Oracle" — a colonial-era Salem Village newspaper.

## Output format per tile

Each tile needs these fields:

| Field | Description |
|---|---|
| `id` | Snake_case identifier (see table below) |
| `tile_order` | 1-16 |
| `tile_kind` | `intro`, `month`, `fallout`, `closing`, or `epilogue` |
| `period_label` | Short label shown on the tile (e.g. "March 1692") |
| `title` | 4-8 word newspaper headline, ALL CAPS style |
| `teaser` | 1-2 sentence hook shown on the tile card (~80 chars) |
| `body` | Full Oracle Newspaper digest — 300-500 words, narrative prose, written as a newspaper retrospective of that month's events. Include key names, dates, locations. |
| `related_npc_ids` | JSON array of NPC IDs referenced (from the 49-figure corpus) |
| `related_event_ids` | JSON array of event IDs referenced |
| `related_newspaper_dates` | JSON array of ISO dates (YYYY-MM-DD) of Oracle newspapers covering this period |

## The 16 tiles

### Tile 1 — Intro: Before the Storm
- **id:** `intro_pre_1692`
- **tile_kind:** `intro`
- **period_label:** "Pre-1692"
- **Content guidance:** Salem Village tensions. Rev. Samuel Parris's arrival (1689) and salary disputes. The Putnam-Porter factional split. Border disputes with Salem Town. Frontier war anxiety (King William's War). The Bayless/Lawson predecessor ministers. Set the powder keg — a village primed for crisis.

### Tile 2 — January 1692
- **id:** `month_1692_01`
- **tile_kind:** `month`
- **period_label:** "Jan 1692"
- **Content guidance:** Betty Parris (9) and Abigail Williams (11) begin strange fits in the Parris parsonage. Attempted folk remedies. Mary Sibley instructs Tituba and John Indian to bake a "witch cake." The fits spread to Ann Putnam Jr. and Elizabeth Hubbard. Village whispers and fear. The doctor (William Griggs) declares "the Evil Hand."

### Tile 3 — February 1692
- **id:** `month_1692_02`
- **tile_kind:** `month`
- **period_label:** "Feb 1692"
- **Content guidance:** Under pressure, the afflicted girls name three women: Tituba, Sarah Good, Sarah Osborne. Arrest warrants issued Feb 29. The three "usual suspects" — a slave, a beggar, a church-absent widow. The first examinations before magistrates Hathorne and Corwin at Ingersoll's Tavern. Tituba's dramatic confession — she describes the Devil's book and other witches. Good and Osborne deny everything. Salem's witchcraft crisis is now public.

### Tile 4 — March 1692
- **id:** `month_1692_03`
- **tile_kind:** `month`
- **period_label:** "Mar 1692"
- **Content guidance:** Accusations escalate to "respectable" targets. Martha Corey — a full church member — accused March 12. Rebecca Nurse — pious 71-year-old matriarch — accused March 23. This crosses a social line. Deodat Lawson visits and witnesses the fits firsthand. Examinations move to the Salem Town meetinghouse to accommodate crowds. Ann Putnam Sr. (the mother) begins accusing. The crisis is no longer confined to marginal figures.

### Tile 5 — April 1692
- **id:** `month_1692_04`
- **tile_kind:** `month`
- **period_label:** "Apr 1692"
- **Content guidance:** Accusations multiply — dozens named. Elizabeth Proctor and John Proctor accused. Sarah Cloyce (Rebecca Nurse's sister) accused after storming out of church. Little Dorcas Good (age 4-5) accused and jailed. Examinations now a public spectacle. Spectral evidence becomes the primary weapon — accusers claim to see the specter of the accused tormenting them. Deputy Governor Danforth sits in on examinations. Over 20 people in jail by month's end.

### Tile 6 — May 1692
- **id:** `month_1692_05`
- **tile_kind:** `month`
- **period_label:** "May 1692"
- **Content guidance:** Governor Sir William Phips arrives with the new Massachusetts charter on May 14. George Burroughs — a former Salem Village minister now in Maine — accused and arrested. Phips establishes the Court of Oyer and Terminer on May 27 to try the witchcraft cases. Lt. Gov. William Stoughton appointed chief justice. The legal machinery is now in place. More accusations: Mary Easty (third Towne sister), Susannah Martin, Bridget Bishop.

### Tile 7 — June 1692
- **id:** `month_1692_06`
- **tile_kind:** `month`
- **period_label:** "Jun 1692"
- **Content guidance:** The Court of Oyer and Terminer convenes June 2. Bridget Bishop is the first tried, convicted, and sentenced to death. Hanged on Gallows Hill June 10 — the first execution. Cotton Mather writes his letter cautioning against spectral evidence but ultimately supporting the court. A brief pause after Bishop's execution while the court considers its methods. Rebecca Nurse initially acquitted but the court sends the jury back — they reverse to guilty. Nathaniel Saltonstall resigns from the court in protest.

### Tile 8 — July 1692
- **id:** `month_1692_07`
- **tile_kind:** `month`
- **period_label:** "Jul 1692"
- **Content guidance:** Five hanged on July 19: Rebecca Nurse, Susannah Martin, Elizabeth Howe, Sarah Good, Sarah Wildes. Sarah Good's defiant last words to Rev. Noyes: "I am no more a witch than you are a wizard, and if you take away my life God will give you blood to drink." The emotional impact of hanging a 71-year-old church grandmother (Nurse). Confessors are spared execution — creating a perverse incentive to confess. Accusations spread beyond Salem into Andover and other towns.

### Tile 9 — August 1692
- **id:** `month_1692_08`
- **tile_kind:** `month`
- **period_label:** "Aug 1692"
- **Content guidance:** Five more hanged on August 19: George Jacobs Sr., Martha Carrier, George Burroughs, John Willard, John Proctor. Burroughs recites the Lord's Prayer perfectly on the gallows (witches supposedly cannot) — crowd nearly riots, but Cotton Mather intervenes. John Proctor's letter from prison pleading for a change of venue. The Andover touch test — dozens accused in a single day. Over 100 people now in jail. The crisis reaches its numerical peak.

### Tile 10 — September 1692
- **id:** `month_1692_09`
- **tile_kind:** `month`
- **period_label:** "Sep 1692"
- **Content guidance:** Giles Corey pressed to death September 19 — refused to enter a plea, "More weight." Eight more hanged September 22 (the last executions): Martha Corey, Mary Eastey, Ann Pudeator, Alice Parker, Mary Parker, Wilmot Redd, Margaret Scott, Samuel Wardwell. Mary Eastey's petition from prison — one of the most eloquent documents of the trials. Increase Mather presents "Cases of Conscience" to the ministers on October 3 — arguing spectral evidence is unreliable. The tide turns.

### Tile 11 — October 1692
- **id:** `month_1692_10`
- **tile_kind:** `month`
- **period_label:** "Oct 1692"
- **Content guidance:** Governor Phips dissolves the Court of Oyer and Terminer on October 29. Increase Mather's "Cases of Conscience" gains support — "It were better that Ten Suspected Witches should escape, than that one Innocent Person should be Condemned." Thomas Brattle's letter circulates, criticizing the proceedings. Public opinion shifts. Accusations against prominent figures (Lady Phips, the governor's wife, is named) accelerate the shutdown. No more executions after September 22.

### Tile 12 — November 1692
- **id:** `month_1692_11`
- **tile_kind:** `month`
- **period_label:** "Nov 1692"
- **Content guidance:** The Superior Court of Judicature established to replace the disbanded Court of Oyer and Terminer. Stoughton remains chief justice. Spectral evidence now barred. Grand juries begin reviewing remaining cases under the new evidentiary standard. Most indictments are dismissed. Prisoners remain in jail — many cannot afford their jail fees (prisoners had to pay for their own food and lodging). The machinery of justice grinds slowly toward release.

### Tile 13 — December 1692
- **id:** `month_1692_12`
- **tile_kind:** `month`
- **period_label:** "Dec 1692"
- **Content guidance:** Remaining trials under the new court — almost all acquitted. Stoughton signs death warrants for the few convicted, but Governor Phips reprives them all. Stoughton storms off the bench in fury. Prisoners still languish — those acquitted or never tried still cannot leave until jail fees are paid. Cotton Mather writes "Wonders of the Invisible World" defending the trials. The intellectual battle between the Mathers — Increase vs. Cotton — plays out in print.

### Tile 14 — Fallout: 1693
- **id:** `fallout_1693`
- **tile_kind:** `fallout`
- **period_label:** "1693"
- **Content guidance:** Governor Phips issues a general release in May 1693 — the last prisoners freed (those who could finally pay their fees or had them paid). Tituba remains in jail longest — Samuel Parris refuses to pay her fees; eventually sold to cover them. Sarah Osborne died in jail (never tried). Several others died awaiting trial. The Salem Village church begins fracturing — factions for and against Parris. The economic and social fallout: shattered families, ruined reputations, destroyed livelihoods.

### Tile 15 — Closing: The Reckoning
- **id:** `closing_reckoning`
- **tile_kind:** `closing`
- **period_label:** "Legacy"
- **Content guidance:** Synthesis of the trials' lasting impact. Samuel Sewall's public apology (1697) — the only judge to repent. Ann Putnam Jr.'s apology (1706). Massachusetts General Court declares a day of fasting (1697). Reversal of attainders (1711) and compensation to victims' families (totaling about 600 pounds). Parris forced out of Salem Village (1697). The trials as a cautionary tale — spectral evidence, mass hysteria, the danger of unchecked judicial power. The phrase "witch hunt" enters the language.

### Tile 16 — Epilogue: After the Darkness
- **id:** `epilogue_outcomes`
- **tile_kind:** `epilogue`
- **period_label:** "Aftermath"
- **Content guidance:** What happened to the key figures after the trials. The accusers: Ann Putnam Jr. never married, gave her apology, died at 37. The judges: Stoughton became governor, never apologized. Samuel Sewall lived with guilt for decades. The survivors: families rebuilt (or didn't). Tituba's fate. The Nurse family's long fight for Rebecca's name. Salem Village eventually becomes Danvers. Salem Town evolves — from shame to tourism to the modern "Witch City" identity. The 1992 Tercentenary memorial. The story never ends.

---

## Notes for Oracle

- Write each `body` as a retrospective newspaper digest — "The Oracle looks back on the events of [month]..."
- Use the same voice and tone as the existing 202 Oracle newspapers
- Cross-reference specific NPC names that appear in our 49-figure corpus
- Include specific dates where known
- Keep the `teaser` punchy — it's shown on a small tile card in a 4x4 grid
- The `title` should be an ALL-CAPS newspaper headline style (like the existing Oracle headlines)
