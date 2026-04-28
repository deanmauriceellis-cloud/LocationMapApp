# S192 — Historical Narration: Status & Progress
**As of 2026-04-27 — full population run in progress: 23/565 POIs processed, 19 historical narrations written to DB.**

---

## Pipeline checkpoints

| Step | Status |
|---|---|
| PG column `historical_narration TEXT` added | ✓ Done |
| Generator `cache-proxy/scripts/generate-historical-narrations.js` written | ✓ Done |
| 33 narrations validated across 3 review rounds (operator approved all) | ✓ Done |
| `intel_entity_id` backfilled (53 → 565 linked) | ✓ Done |
| Room schema v11 → v12 with `historical_narration` column | ✓ Done |
| `publish-salem-pois.js` updated to include the column | ✓ Done |
| Admin UI `PoiEditDialog` Historical Narration textarea (HISTORICAL_BUILDINGS only) | ✓ Done |
| Admin backend `UPDATABLE_FIELDS` accepts `historical_narration` | ✓ Done |
| Lint check `hist_pre1860_no_historical_narration` registered | ✓ Done |
| Full population run on 565 eligible POIs | ⏳ 23/565 |
| `align-asset-schema-to-room.js` rebake bundled DB | ⏸ After full run |
| Full publish chain (POIs → tours → tour-legs → align) | ⏸ After full run |
| APK rebuild + Lenovo install | ⏸ After full run |

---

## Validators / safety rules in effect

- **Hard year cutoff** — no mention of any year ≥ 1860
- **Era vocabulary** — Massachusetts Bay Colony / Province / Commonwealth based on entity year
- **Pre-1776 / pre-1780 anachronism bans** — no "United States" / "Commonwealth of Massachusetts" before those dates
- **Witch-trial year guard** — entity year must be in [1670, 1710] OR description must literally name the trials
- **Commemorative pre-filter** — statues / plaques / memorials / monuments / cenotaphs auto-skip
- **Modern-content bans** — museum operations, NRHP designations, modern persons, modern businesses
- **Meta-gap rule** — never narrate what we do not know; omit silently
- **Sentence-level rescue** — drop violating sentences + orphan-pronoun followers
- **Multi-source GROUND_TRUTH** — SI dump + SI historical_note + local Salem JSON (buildings/biographies/facts) for trial-era entities

---

## Narrations written so far (19 of 565)

Sorted by entity year ascending.

### Peabody Essex Museum (1626, 202 words)

> The structure before you stands as one of the oldest continuously maintained establishments within the Massachusetts Bay Colony. In 1799, captains and mariners returned from voyages to China, India, and lands beyond, and formed the East India Marine Society. They sought to share the wonders they had witnessed, and so began a collection of artifacts gathered from across the globe. The Society’s holdings were later combined with those of the Essex Institute, enriching the breadth of its scope. Within these walls, one finds echoes of Salem’s prominence as a port of trade, a gateway connecting the colony to the wider world. The building itself holds a history spanning well over two centuries. It is home to Morse Auditorium, with seating arranged to accommodate all who might gather there. Within its galleries, one might encounter works by artists from the islands of Jamaica, the Dominican Republic, Cuba, and Martinique, each piece a testament to the vibrant cultures of those distant lands. Notes regarding these works were penned by careful observers, seeking to understand the stories they tell. The collection also includes works that once graced the walls of a gallery in New York, brought here to continue their journey of observation and contemplation.

---

### Salem (1626, 244 words)

> Salem, situated within the bounds of Essex County in the Massachusetts Bay Colony, came into being circa 1626. Records detail the existence of maps pertaining to this settlement, though many are not formally held within the collections of the Library of Congress. These cartographic depictions, including printed examples, photocopies, and facsimiles, likely illustrate landholdings and perhaps routes for those traveling to and from the colonial settlement. The history of Salem is interwoven with that of prominent families. The lineage of the Peabody family, and also that of the Webster family, can be traced back to this locale. Furthermore, the town’s early narrative is linked to the events surrounding the Bury St. Edmunds Witch Trial, a matter of some discussion in relation to Salem’s own, later, trials. The town’s character is also reflected in the contributions of men such as Benjamin D. Hill and Winfield S. Nevins, who aided in the creation of a guidebook to the area. The Orpheus Club lent its voice to the community, composing an anniversary hymn, with the musical arrangement attributed to E. Peter Oyer. Beverly, a neighboring town on the north shore of Massachusetts Bay, is noted as being within the scope of this guidebook. Maps of Salem also show the proximity of Wamsutta Mills, though this establishment lies within the town of New Bedford. The author of these records frequently wrote of Salem and its history, lending credence to the importance of this settlement within the colonial landscape.

---

### Charter Street Historic District (1630, 146 words)

> Charter Street, as it lies within the colonial settlement, first took shape in the early years of the Massachusetts Bay Colony, circa 1630. The way itself, a thoroughfare of some length, is situated near other established paths and holdings. The surrounding land saw the construction of homes for those establishing themselves in this new world. Nearby stand the Ward House, and the Bott House, each a testament to the building traditions of the era. Further along, the Beckett House also rose, becoming a notable structure. The Burying Point, a ground for the solemn duty of interment, also came to be established nearby. The very fabric of the settlement, and Charter Street within it, was shaped by the needs of a growing community. The Salem Historical Commission, though not yet formed in this earlier time, reflects the continuing care for the structures and stories of this place.

---

### Charter Street Cemetery (1637, 135 words)

> Charter Street Cemetery has been a place set apart for the departed residents of Salem for many years, its origins stretching back to the earliest days of the Massachusetts Bay Colony. The land itself holds the remains of those who shaped the early colony. The city undertook work to stabilize the pathways and repair the iron fencing, ensuring the grounds remain a respectful place of remembrance. This work was undertaken with the collaboration of the Massachusetts Historical Commission, who lent their expertise to the endeavor. The cemetery lies near other notable places within Salem Town, though the distances are not of particular note to the telling of its history. The stones themselves, crafted from slate and marked with symbols of mortality, stand as silent witnesses to the lives lived and lost within the colonial settlement.

---

### Salem Common (1637, 210 words)

> Salem Common, a public space of some seven acres within the bounds of the Massachusetts Bay Colony, holds a place as the oldest such park within this settlement. From its beginnings circa 1637, it served as a broad clearing – wider than the village greens of smaller settlements – intended for the common use of the people. Paths meandered through the grass, and trees offered shade, all surrounded by the buildings of a growing town. Throughout the years, the Common has witnessed much of the colony’s life. It was a place for gathering, for the muster of men, and, sadly, a site for public humiliation during the difficult days of 1692. Accounts tell of those accused during the trials brought forth in this very space, a somber chapter in the colony’s history. In the year 1859, the likeness of the Common was captured by the lenses of both Mr. Peabody & Mr. Tilton, and Mr. Charles G. The land itself is observable from the outside, a visual record of the settlement’s life unfolding within its boundaries. One can imagine the scents of open air, grass, and earth mingling with the smells of livestock and woodsmoke drifting from nearby homes, the sounds of harbor activity and cart traffic filling the air.

---

### Yin Yu Tang (1644, 124 words)

> Yin Yu Tang is a dwelling of considerable age, its origins tracing back to the Qing Dynasty, circa 1644, in the distant lands of southeastern China. For more than two centuries, this house served as the home of the Huang family, sheltering eight generations within its walls. Within these rooms, the family experienced the full cycle of life, witnessing births, marriages, and the passing of elders. It was a place of shared meals and familial bonds, a center for a lineage that endured for over two hundred years. The house itself was constructed with careful consideration for its surroundings, oriented according to the principles of feng shui, and positioned to benefit from a nearby stream, believed to bring prosperity to those who dwelt within.

---

### The Witch House at Salem (1650, 311 words)

> The Witch House at Salem stands as a testament to the colonial settlement’s early years. Constructed of wood, the house presents a solid, imposing face to Essex Street. Within its walls resided Magistrate Jonathan Corwin, born in Salem in 1640. He came of a family well-established in the colony, his father a successful merchant and his wife descended from the Winthrop lineage. Before the unsettling events of 1692, Corwin served as a magistrate in the Essex County courts, known for his competent administration. However, it is the year 1692 which most firmly binds this house to the annals of the Massachusetts Bay Colony. During that time, the Great Hall, with its plastered walls, ornate carved mantelpiece, and furnishings of turkey-work chairs and imported ceramics, served as a space reflecting wealth and authority. More significantly, the house contained an Examination Parlor, where pre-trial examinations of the accused were conducted. A heavy table stood at the center, serving as the magistrate’s bench, with chairs positioned behind for Corwin and his colleague, John Hathorne. Here, within these walls, Sarah Good, Tituba, and Bridget Bishop were subjected to questioning. Corwin, alongside Hathorne, presided over these examinations, signing warrants for arrest and commitment. He accepted spectral testimony as evidence, aligning with the legal understandings of the time. The magistrate’s private study, filled with English statute books, court records, and correspondence, provided a space for deliberation. The house, with its wide pine plank floors swept with sand, hand-hewn summer beams, and fieldstone chimney, bore witness to a dark chapter in the colony’s history. The air within carried the scent of ink and beeswax candles, a testament to the magistrate’s work and the household’s formality. Even after the dissolution of the Court of Oyer and Terminer, Jonathan Corwin continued in public office until his death in 1718, never offering a public apology for his role in the trials.

---

### Broad Street Cemetery (1655, 142 words)

> Broad Street Cemetery began as Lawes Hill Burial Place back in 1655, just a few decades after the town itself was founded. Imagine this space, then, as a wild hill overlooking the harbor, slowly filling with the lives of early Salem families. By 1732, they’d begun enclosing it with fences, marking it as a sacred space. Look closely at the stones—many are slate and marble, carved by the first generation of Massachusetts stonecutters. They tell stories in the shapes they chose, the symbols they etched. Veterans of the Revolutionary and Civil Wars rest here, alongside merchants, craftsmen, and those who contributed to the prosperity of Salem. The City of Salem still cares for this ground, continuing a tradition that stretches back nearly four centuries. It is a place of remembrance, and a testament to the enduring spirit of the Massachusetts Bay Colony.

---

### Gedney House (1665, 120 words)

> Gedney House stands in Salem, a dwelling of considerable age, constructed around the year 1665. It was built by Eleazor Gedney, who became the first owner of this home. Eleazor Gedney was brother to Bartholomew Gedney, a magistrate who presided over the examinations during the unsettling trials concerning witchcraft that gripped the colony in 1692. The Gedney family were amongst the earliest settlers of this colonial settlement, having arrived in the year 1636. This house, therefore, represents a direct connection to those who first established Salem. Though the house stands as it was built, the small windows speak to the concerns of the time, intended to ward off both the cold of the Massachusetts winters and, perhaps, less tangible threats.

---

### Daniels House Historic Site (1667, 97 words)

> The Daniels House stands as a testament to the earliest days of the colonial settlement. The structure shares its location with the Daniels House Inn and Silsbee’s, suggesting a continuity of hospitality over the years. The Daniels House, alongside the Inn, has served as a dwelling for many years. The structure itself is of note. The Daniels House and Inn share the same address, and together they form a landmark within the town. The house stands as a reminder of the architectural styles of its time, a testament to the building traditions brought to this new land.

---

### Hathaway House (1668, 86 words)

> Hathaway House stands within the colonial settlement, a dwelling first raised circa 1668. Its form reflects the building practices of that early era. The structure is known to have been inventoried as a property of note. Constructed in 1755, the building exhibits features of the Federal style, a manner of building becoming increasingly favored in the colony during that period. The House stands near other dwellings, and is located on Essex Street. It is a multiple-family dwelling house, and is built in a Federal architectural style.

---

### Narbonne House (1675, 219 words)

> Narbonne House stood within the bounds of Salem Town as early as 1675. It was then the property of Thomas Ives, a butcher who supplied provisions to the inhabitants of the colonial settlement. Constructed in a modest but sound manner, the dwelling exemplifies the First Period style common to the region. The structure, with its lean-to addition and clapboard siding, reflected the means of a man of the middle class. Its location near Essex Street was deliberate, for that thoroughfare fronted the harbor and facilitated trade. The house witnessed tumultuous times. It stood as a silent observer during the dreadful year of 1692, when accusations of witchcraft gripped the colony. In the year 1780, the house passed into the possession of the Andrews family. It was Sarah Narbonne, a long-time resident, who lent her name to the dwelling. She remained a fixture in Salem life for many years, and the house became known by her name. The wide pine plank floors within, swept clean with sand, and the hand-hewn summer beams overhead, spoke to the simple life lived within. A fieldstone chimney, cold and rough, rose from the hearth, and board-and-batten doors secured the rooms with wooden lift-latches. The air would have carried the scent of oak woodsmoke, tallow candles, and the cooking of salt pork and cornmeal porridge.

---

### The Witch House (1675, 222 words)

> The Witch House stands within the colonial settlement of Salem, Massachusetts, upon Essex Street. In the year 1692, during the unsettling period of witchcraft accusations, Magistrate Corwin, alongside John Hathorne, presided over numerous examinations of the accused. Within its walls, specifically a room known as the Examination Parlor, preliminary hearings were conducted. Sarah Good underwent examination here on the first of March, 1692, and Tituba, too, was questioned within these rooms, her confessions recorded by both Ezekiel Cheever and Jonathan Corwin himself. Bridget Bishop faced examination here on April 19th, and George Burroughs was subjected to private questioning on May 9th. The house itself reflects the authority of its owner. Wide pine plank floors, swept with sand, lie beneath hand-hewn summer beams. Fieldstone chimneys rise from the structure, and board-and-batten doors secure the rooms. The Great Hall, the largest room within the dwelling, was furnished with plastered walls, an ornate carved mantelpiece, and imported upholstery, displaying wealth and status. Corwin maintained a private study, filled with English statute books, court records, and correspondence. Sheriff George Corwin, the magistrate’s nephew and grandson of Governor Winthrop, executed property seizures during this time, further solidifying the family’s influence within the colony. Jonathan Corwin continued to serve in public office until his death in 1718, never offering a public apology for his role in the trials.

---

### John Ward House (1684, 159 words)

> John Ward House stands as a testament to the earliest years of the Massachusetts Bay Colony. Constructed between 1684 and 1723, it offers a rare glimpse into the domestic architecture of the period, a prime example of what is known as First Period building. John Ward, a man of means engaged in trade, was the builder and first owner of this dwelling. The house, located in Salem Town along Essex Street, reflects the simple yet solid construction favored by those establishing lives in the new world. Its steeply pitched roof and central chimney speak to the practicalities of a northern climate, while the minimal ornamentation reflects the restrained tastes of the time. The structure, built of wood, would have stood as a clear sign of prosperity within the colonial settlement. Though time has passed, the house remains a tangible link to the lives of those who first settled this land, a silent witness to the unfolding of colonial history.

---

### Alice Parker Home Site (1692, 112 words)

> Alice Parker Home Site stands as a testament to the early days of the Province of Massachusetts Bay. Constructed circa 1692, the house represents a dwelling raised in a time of considerable change for the colony. The site continued as a residence through the years, witnessing the unfolding of life within the Province. It remained a single family dwelling house, and over time, it became known as a place of significance within the community. The Parker name endured, marking the family’s long association with the property. The house stands as a quiet reminder of those who first settled this land, and of the lives lived within the colony during a formative era.

---

### Blue Anchor Tavern Site (1692, 144 words)

> The site of the Blue Anchor Tavern stands within the Province of Massachusetts Bay, its origins reaching back to approximately the year 1692. Over the years, the building witnessed a succession of occupants and purposes. By the late 18th century, it came into the possession of the Derby family, a name well known in the maritime trade of Salem. It was during their tenure that significant alterations were made to the building’s structure. In 1799, the Derby family constructed a new front onto the original building, expanding its footprint and altering its appearance. The building’s architecture reflects the styles of its time. The original structure, dating to the late 17th century, exhibits characteristics of First Period construction, while the addition of 1799 demonstrates the emerging Federal style. The building is constructed of wood, a common material for structures of this age within the Province.

---

### First Church Meetinghouse 1692 Site (1692, 130 words)

> The First Church Meetinghouse stands as a testament to the early religious life of this colony. Constructed circa 1692, it arose during the period of the Province charter, a time when the foundations of governance were being firmly laid. The Meeting House served not merely as a place of worship, but as the very heart of communal life. It was within these walls that the faithful gathered, and where matters of both spiritual and civic import were often discussed. The building’s architecture, though simple in its early form, reflects the prevailing styles of the era. It is a structure built to endure, and it has witnessed the unfolding of years. The authenticity of the original construction has been a matter of inquiry, with scholars examining its every timber and stone.

---

### Howard Street Cemetery (1692, 73 words)

> Howard Street Cemetery lies within the Province of Massachusetts Bay, a burial ground first established circa 1692. Nearby lie other ancient grounds, each holding stories of those who came before. The cemetery is situated in close proximity to other burial places, a testament to the enduring need for such spaces within a settled community. The land itself bears witness to the passage of time, a quiet observer of the colony’s growth and change.

---

### Judge Hathorne's Home Site (1692, 182 words)

> postaje
The structure known as the Jonathan Corwin House stood completed by the year 1692. It was the residence of Judge Jonathan Corwin, a magistrate within the Province of Massachusetts Bay, and a figure central to the trials concerning witchcraft that troubled this colony during that year. The house, then and for more than forty years thereafter, remained within the Corwin family. Judge Corwin did employ the house as a place for examinations related to the accusations of witchcraft. It was here that he, and others appointed to administer justice, questioned both the accused and those who brought forth claims of spectral affliction. The house witnessed the proceedings involving numerous individuals caught within the web of these trials. In the year 1692, the colony was gripped by fear and suspicion, and the Corwin House served as a focal point for the investigations into alleged witchcraft. The structure stands as one of the few remaining buildings directly connected to this dark chapter in the history of the Province. About 1856, a single-story apothecary was added to the east side front of the house.

---

## What runs next

1. Full run continues for ~9 hours, writing one narration every 30-90s.
2. As each completes, lint check `hist_pre1860_no_historical_narration` decrements (started at 50).
3. When done, the full publish chain bakes the asset DB with all narrations + new Room schema.
4. APK rebuild + Lenovo install.
5. Future: build the "Historical Tour" mode UI that surfaces `historical_narration` instead of `short_narration`.
