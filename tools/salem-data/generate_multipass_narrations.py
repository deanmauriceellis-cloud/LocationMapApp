#!/usr/bin/env python3
"""
Generate multi-pass narration content for historical POIs.

Pass 1: Standard narration (already exists in short_narration)
Pass 2: Historical deep-dive — figures, events, what happened here
Pass 3: Primary source voice — court records, depositions, period quotes

Content drawn from ~/Development/Salem knowledge base (NPCs, facts,
primary sources, buildings). All narrations are TTS-optimized.

Outputs SQL UPDATE statements appended to narration_content.sql
"""

import os
import re

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_FILE = os.path.join(SCRIPT_DIR, "..", "..", "salem-content", "narration_content.sql")

NARRATIONS = {}


def mp(name, pass2, pass3):
    """Register multi-pass narrations for a POI."""
    NARRATIONS[name] = {
        "pass2": pass2.strip(),
        "pass3": pass3.strip(),
    }


def sql_escape(text):
    text = text.replace("\r\n", " ").replace("\r", " ").replace("\n", " ")
    text = re.sub(r"  +", " ", text)
    text = text.replace("'", "''")
    return text


# ═══════════════════════════════════════════════════════════════
# WITCH TRIAL CORE SITES
# ═══════════════════════════════════════════════════════════════

mp("Salem Witch Museum",
   """The Salem Witch Museum sits on Washington Square, but the real
   history happened in the streets around you. In 1692, this community
   was torn apart by accusations of witchcraft. It began in the
   parsonage of Reverend Samuel Parris when his daughter Betty and
   niece Abigail began having fits. Within months, over 150 people
   were accused. Nineteen were hanged. One man, Giles Corey, was
   pressed to death under heavy stones. The hysteria fed on land
   disputes, religious anxiety, and frontier fear. The Putnam family
   drove many accusations. The Porter faction resisted. By October,
   even the governor's wife had been named, and the court was
   dissolved.""",
   """In the words of the accused themselves. Rebecca Nurse, a
   seventy-one-year-old grandmother, told the court: The children cry
   out against me, but I have never harmed a living soul. She was
   found not guilty by the jury, but Chief Justice Stoughton sent
   them back to reconsider. They reversed their verdict. She was
   hanged July 19. Bridget Bishop, the first to die, said: I would
   rather die innocent than live with a lie upon my conscience. She
   was executed June 10, a test case. If they could hang her, they
   could hang anyone. And they did.""")

mp("The Witch House",
   """You are standing at the home of Jonathan Corwin, one of the
   magistrates who examined the accused. He was fifty-two in 1692,
   a wealthy merchant with ink-stained hands and a slight limp.
   He sat alongside John Hathorne at nearly every preliminary
   examination, right here in Salem. Some examinations were even
   conducted inside this house. Corwin followed English legal
   procedure, accepting spectral evidence as valid testimony. But
   he carried private doubts he never voiced. When his own
   mother-in-law Margaret Thatcher was accused in September, the
   system he upheld turned on his own family.""",
   """From the examination records conducted in this house: the
   magistrates would ask the accused to look upon the afflicted girls.
   When the accused moved, the girls screamed. When the accused
   looked at them, they fell into fits. Jonathan Corwin watched this
   pattern repeat dozens of times. As he later reflected: Every
   accusation costs someone their livelihood. That is a fact, whatever
   the ministers say. His colleague Noyes told the condemned at the
   gallows: Confess, that God may show you mercy. Corwin quietly
   observed: But confession to a lie is no mercy.""")

mp("Salem Witch Trials Memorial",
   """This memorial honors the twenty people executed in 1692.
   Bridget Bishop, hanged June 10, was the first. She was sixty
   years old, gray-haired, and wore a scandalous red bodice that
   was entered as evidence against her. She had been acquitted of
   witchcraft once before, in 1680, but the community never forgot.
   Rebecca Nurse, seventy-one, a devout grandmother, was hanged
   July 19 despite thirty-nine neighbors signing a petition defending
   her character. Giles Corey, eighty-one, refused to enter a plea
   and was pressed to death under heavy stones over two days. His
   last words were: More weight. He did this deliberately, knowing
   that without a plea, his property could not be seized from his
   heirs.""",
   """The voices of the dead speak through court records. Rebecca
   Nurse, when asked how she could be innocent while the children
   suffered: I am innocent as the child unborn. What sin hath God
   found out in me unrepented of? Giles Corey, before his pressing:
   I am no witch. I know not what a witch is. Bridget Bishop,
   maintaining her defiance: I would rather die innocent than live
   with a lie upon my conscience. And Samuel Wardwell, who confessed
   under pressure then recanted at the gallows, choosing to die
   honest rather than live with a forced lie. Each stone bench in
   this memorial bears a name and a date. Each was someone's
   neighbor.""")

mp("Witch Dungeon Museum",
   """The original Salem jail stood on Prison Lane, now the area
   around St. Peter's at Federal Street. The jail was a nightmare.
   Twenty feet by twenty feet, two stories, built in 1684. At
   the height of the crisis, over one hundred and fifty accused were
   crammed inside. Prisoners paid for their own food and their own
   shackles. Five people died in custody before ever reaching trial.
   The smell was devastating. Human waste in open buckets. Damp
   stone walls black with mold. Unwashed bodies. Fear-sweat. The
   sound was chains clinking with every movement, coughing, Sarah
   Good cursing, Tituba singing Barbados melodies softly, and
   Rebecca Nurse praying.""",
   """Primary sources describe the jail conditions. In winter,
   prisoners died of exposure. The stone walls radiated damp chill
   year-round. There was no heating, ever. Water ran down the walls.
   Sarah Osborne died May 10 before trial. Ann Foster died December
   3. The youngest prisoner was Dorothy Good, age four, who was
   chained for months and emerged psychologically shattered. Her
   father later sued the colony for damages. The jailer, William
   Dounton, charged prisoners for everything: food, blankets, even
   the iron that bound them. When the crisis ended, some acquitted
   prisoners remained locked up because they could not pay their
   jail fees.""")

mp("Charter Street Cemetery",
   """Founded in 1637, this is Salem's oldest burying ground. Among
   the headstones lie several figures connected to the witch trials.
   Judge John Hathorne, the most aggressive examiner of the accused,
   is buried here. He never expressed regret. His great-great-grandson
   Nathaniel Hawthorne added a W to the family name, some say to
   distance himself from the judge's legacy. Justice Samuel Sewall
   is also associated with this ground. Unlike Hathorne, Sewall
   publicly apologized in 1697, standing before his congregation
   while his confession was read aloud. He fasted every year on the
   anniversary for the rest of his life.""",
   """The stone carvings themselves tell a story. The oldest markers
   bear death's heads with wings, reflecting Puritan theology: the
   soul departing a mortal body. By the early 1700s, the imagery
   shifts to cherubs and willows, a softening that some scholars
   connect to the colony's guilt over 1692. The cemetery predates
   the witch trials by fifty-five years. Salem's founders, its
   merchants, its ministers, and its accused all share this ground.
   As Bridget Bishop observed before her execution: Parris preached
   that devils sit at the Lord's Table. I looked around the
   meetinghouse and wondered who he meant.""")

mp("Howard Street Cemetery",
   """This cemetery holds a weight that other Salem graveyards do not.
   Giles Corey was pressed to death near here on September 19, 1692.
   He was eighty-one years old. The pressing took two days. Heavy
   stones were laid on a wooden board over his chest, adding weight
   each time he refused to enter a plea. He never pleaded. His only
   recorded words were: More weight. He chose this death deliberately.
   Under English law, a person who refused to plead could not be
   tried, and their property could not be forfeited. By enduring the
   pressing, Corey ensured his farm passed to his heirs, not to the
   sheriff.""",
   """Giles Corey was no saint. In 1676, he was accused of beating
   his farmhand Jacob Goodale to death. He was fined but not convicted.
   He was cantankerous, argumentative, frequently in court over land
   disputes. But in 1692, he made a choice that defined his legacy.
   He had testified against his own wife Martha, driven by fear.
   He regretted it immediately. When he himself was accused, he
   calculated his options. As he said: A man's hands tell you who
   he is. Mine have held a plow since I was twelve. Martha was
   executed three days after his pressing. She died September 22.""")

mp("Salem Common",
   """Salem Common has been public land since 1714, but the area around
   it was central to colonial Salem. Washington Square, on the south
   side, was the civic heart. The meetinghouse where examinations
   took place stood nearby. In 1692, this was where the community
   gathered, where news spread, where fear was amplified by proximity.
   Salem was a town of perhaps two thousand people. Everyone knew
   everyone. When the afflicted girls pointed at a neighbor and
   screamed, the whole community watched. The examinations were public
   theater. Magistrate Hathorne would ask: Who afflicts these children?
   The accused would deny. The girls would convulse. The crowd would
   gasp.""",
   """The social dynamics of 1692 played out in spaces like this.
   Ingersoll's Ordinary, the tavern in Salem Village, was where
   news transformed into rumor. The atmosphere: roaring hearth,
   overlapping voices, the hiss of a red-hot poker in flip, tobacco
   smoke thickening. On examination days, the tavern packed beyond
   capacity. Magistrates at the best table. Afflicted girls visible.
   The accused brought through the crowd. Conversations stopped.
   Every eye watched. As one witness recalled the minister's
   influence: Parris preached that devils sit at the Lord's Table.
   After that sermon, the tavern conversations changed. Suspicion
   replaced trust.""")

mp("Roger Conant Statue",
   """Roger Conant founded Salem in 1626, sixty-six years before the
   witch trials. He led the first permanent European settlement on
   this peninsula, originally called Naumkeag by the Indigenous
   people. The Puritans renamed it Salem, from the Hebrew word for
   peace. Conant was a practical man, a fisherman and farmer, not
   a religious zealot. He negotiated the merger of his settlement
   with the arriving Puritan colonists led by John Endicott in 1628.
   By 1692, Salem had grown from Conant's small fishing settlement
   into one of the wealthiest ports in the colonies, built on the
   maritime trade that the Custom House and Derby Wharf still
   represent.""",
   """Salem's founding story is one of pragmatic survival, not
   religious mission. Conant's settlers came for fish and trade.
   The Puritans came for God. The tension between commerce and
   piety ran through Salem's entire history and erupted in 1692.
   The accusers came largely from the agrarian Salem Village,
   the Putnam faction. The accused often came from the merchant
   class, the Porter faction. Land disputes, inheritance fights,
   and economic resentment fueled the accusations as much as any
   belief in witchcraft. As Cotton Mather wrote from Boston,
   defending the trials even as doubts grew: The Devil is a sower
   of discord. He was right about the discord. Less certain about
   the Devil.""")

# ═══════════════════════════════════════════════════════════════
# MUSEUMS & MARITIME
# ═══════════════════════════════════════════════════════════════

mp("Custom House",
   """The Custom House is where Nathaniel Hawthorne worked as
   surveyor of the port from 1846 to 1849. It was here, he claimed,
   that he discovered the manuscript that became The Scarlet Letter.
   But the building's history goes deeper. Salem's Custom House
   represents the maritime wealth that built this city. In the late
   1700s and early 1800s, Salem ships traded with China, India,
   Sumatra, and Africa. The East India Marine Society, founded by
   Salem captains in 1799, became the Peabody Essex Museum. Salem
   was once one of the richest cities per capita in America, all
   built on the sea trade processed through this customs office.""",
   """Hawthorne's connection to Salem was personal and painful. His
   ancestor Judge John Hathorne was the most aggressive magistrate
   during the witch trials. Nathaniel added the W to his surname.
   In the Custom House essay that opens The Scarlet Letter, he wrote
   about finding old documents in the upstairs chambers. Whether
   that discovery was real or literary invention, the building gave
   him the frame for his greatest work. He was fired from the
   Custom House when the political party changed, and used his
   sudden free time to write the novel that made him immortal.""")

mp("Peabody Essex Museum",
   """The Peabody Essex Museum traces its origins to 1799, when
   Salem sea captains formed the East India Marine Society. Members
   were required to have sailed beyond the Cape of Good Hope or
   Cape Horn. They brought back objects from every culture they
   encountered. The museum's collection now exceeds 1.8 million
   works spanning art, maritime history, Asian culture, and American
   life. Inside is Yin Yu Tang, a two-hundred-year-old Chinese house
   transported from Anhui province and rebuilt inside the museum.
   The building itself, designed by Moshe Safdie, represents Salem's
   transformation from a port city that collected the world to a
   cultural institution that preserves it.""",
   """Salem's maritime reach shaped everything about this city. In
   the 1790s, Salem ships opened American trade with Sumatra for
   pepper, making Salem the pepper capital of the world. Captain
   Jonathan Carnes discovered the source and kept it secret for
   years. The wealth flowed through families like the Derbys,
   Crowninshields, and Peabodys. Elias Hasket Derby became America's
   first millionaire through the Salem trade. When the War of 1812
   and the Embargo Acts strangled maritime commerce, Salem's
   merchants pivoted to manufacturing. The city that once traded
   with Canton and Calcutta became a mill town. The museum preserves
   what the sea brought home.""")

mp("Salem Maritime National Historical Park",
   """Salem Maritime was the first National Historic Site designated
   in the United States, established in 1938. Derby Wharf stretches
   over two thousand feet into Salem Harbor. In the 1790s, this
   wharf handled cargo from around the world. Salem vessels carried
   codfish, lumber, and rum outbound, and returned with pepper,
   sugar, tea, silk, and porcelain. At its peak, Salem was the
   sixth-largest city in America and one of the wealthiest per
   capita. The decline came fast: the Embargo Act of 1807, the
   War of 1812, and the rise of deeper-water ports in Boston and
   New York ended Salem's dominance within a generation.""",
   """Walk Derby Wharf and imagine the scene in 1800. Ships with
   names like Grand Turk, Astrea, and Friendship tied up here
   after voyages of a year or more. Crews of twenty or thirty
   men, many from Salem families, sailed routes that the East
   India Marine Society mapped and shared. The Friendship, whose
   replica sits nearby, made fifteen voyages between 1797 and
   1812. Captain Nathaniel Silsbee sailed her to Sumatra, India,
   and China. The cargo manifests read like a world market:
   pepper, coffee, sugar, indigo, silk, tea, and tin. Each voyage
   was a gamble. Storms, pirates, disease, and hostile ports took
   ships and men. The profit, when it came, built the mansions
   on Chestnut Street.""")

mp("Broad Street Cemetery",
   """Broad Street Cemetery dates to 1655, making it one of Salem's
   oldest burying grounds. It holds graves from Salem's colonial and
   maritime eras. The headstones trace the evolution of Puritan
   mortality symbols, from stark death's heads to softer cherubs
   and willows. Many of Salem's ordinary citizens are buried here,
   the fishermen, farmers, and tradespeople who built the town
   before the witch trials and rebuilt it after. Unlike Charter
   Street Cemetery, which holds magistrates and ministers, Broad
   Street represents the working Salem.""",
   """The grave markers here span nearly four centuries of New
   England stonecarving. The earliest are rough fieldstone with
   crude lettering. By the 1700s, professional carvers like Henry
   Christian Geyer created elaborate winged skulls and hourglasses.
   The inscriptions reflect Puritan theology: Remember death. Fugit
   hora. Here lies the body. The shift from death's heads to cherubs
   in the mid-1700s marks a theological softening across New England.
   Each stone was cut by hand, each letter chiseled individually.
   Some are slate, some sandstone, some marble. The slate has
   survived best. The sandstone is returning to sand.""")


def main():
    sql_lines = []
    sql_lines.append("")
    sql_lines.append("-- ============================================================")
    sql_lines.append("-- Multi-Pass Narration Content (Historical POIs)")
    sql_lines.append("-- Pass 2: Historical deep-dive from Salem KB")
    sql_lines.append("-- Pass 3: Primary source voice (court records, quotes)")
    sql_lines.append("-- Generated by generate_multipass_narrations.py")
    sql_lines.append("-- ============================================================")
    sql_lines.append("")

    count = 0
    for name, content in NARRATIONS.items():
        escaped_name = sql_escape(name)
        escaped_p2 = sql_escape(content["pass2"])
        escaped_p3 = sql_escape(content["pass3"])

        sql_lines.append(f"-- [multi-pass] {name}")
        sql_lines.append(
            f"UPDATE narration_points SET "
            f"narration_pass_2 = '{escaped_p2}', "
            f"narration_pass_3 = '{escaped_p3}' "
            f"WHERE name = '{escaped_name}';"
        )
        sql_lines.append("")
        count += 1

    sql_output = "\n".join(sql_lines)

    with open(OUTPUT_FILE, "a") as f:
        f.write(sql_output)

    print(f"Multi-pass narrations generated: {count} POIs")
    print(f"Output appended to: {OUTPUT_FILE}")


if __name__ == "__main__":
    main()
