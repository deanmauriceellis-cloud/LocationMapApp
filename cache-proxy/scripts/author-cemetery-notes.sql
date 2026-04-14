-- author-cemetery-notes.sql — Phase 9R.0
-- Hand-authored tour-guide-voice historical_note for the three most
-- historically significant cemeteries in downtown Salem. These fill the
-- gap while SalemIntelligence's dedicated historical_note pass catches up.
-- When SI generates its own content for these entities, the sync script
-- (sync-historical-notes-from-intel.js) will overwrite these with SI's
-- canonical version.

BEGIN;

-- Charter Street Cemetery — "The Burying Point" — oldest burying ground in
-- Salem (est. 1637), adjacent to the Witch Trials Memorial. Named dead here
-- include Judge John Hathorne (one of the witch-trial judges, whose great-
-- great-grandson Nathaniel added the "w" to distance himself from him),
-- Richard More (Mayflower passenger), Governor Simon Bradstreet, and
-- Bartholomew Gedney. Drives the emotional pivot of the trail.
UPDATE salem_pois
SET historical_note = E'Take a moment here. This is the oldest burying ground in Salem — the Charter Street Cemetery, also called the Old Burying Point — and it has been accepting the city''s dead since 1637. Walk the rows and read the slate: you''re looking at three centuries of Salem condensed into stone. Governor Simon Bradstreet is buried here. So is Richard More, who came over on the Mayflower as a child of eight. And so is Judge John Hathorne, one of the magistrates who signed the death warrants for the accused in 1692. Hathorne never apologized. A few paces away, his great-great-grandson Nathaniel Hawthorne would later add a ''w'' to the family name to put distance between himself and what the judge did. The memorial you see adjoining the cemetery is for those the judge condemned — twenty victims of the 1692 trials, their names carved into stone benches that stop cold mid-sentence, just as their lives did.',
    updated_at = NOW()
WHERE id = 'charter_street_cemetery';

-- Howard Street Cemetery — site of Giles Corey's pressing death, 1692.
-- One of the most visceral moments of the witch trials. The 81-year-old
-- farmer refused to plead, and was pressed with stones over two days until
-- he died — reportedly saying "more weight" as his final words.
UPDATE salem_pois
SET historical_note = E'You''re standing at the Howard Street Cemetery. Look toward the edge of the field — somewhere near here, on September 19, 1692, an 81-year-old farmer named Giles Corey was laid on his back in the dirt, a board placed across his chest, and stones piled on top. The court called it peine forte et dure — hard and forceful punishment. They did it because Corey refused to enter a plea. Plead guilty or innocent and the court seizes your estate; stay silent and your land passes to your sons. Corey stayed silent. He endured two days of stones. When the sheriff asked him if he would plead, he is said to have answered, "more weight." That was his last sentence. He died in the field. No other person in American history has ever been killed this way under English law. His wife Martha, also accused, was hanged three days later.',
    updated_at = NOW()
WHERE id = 'howard_street_cemetery';

-- Broad Street Cemetery — colonial-era burying ground (est. 1655),
-- Revolutionary War veterans, early Salem merchants.
UPDATE salem_pois
SET historical_note = E'This is the Broad Street Cemetery, consecrated in 1655 — nearly forty years before the Witch Trials. Many of the first English families of Salem are buried beneath your feet: Turners, Pickmans, Endicotts. Revolutionary War veterans rest here too, alongside ship captains from Salem''s golden age of maritime trade. Read the weathered slate: the winged skulls and ''death''s heads'' carved on the oldest stones are a Puritan iconography, a reminder from a culture that took death seriously and saw it everywhere. By the early 1800s the style shifts — willows and urns, cherub faces, hopeful language about resurrection. The cemetery tells the story of Salem''s spiritual journey from Puritan dread to Federalist grace, all in one walk.',
    updated_at = NOW()
WHERE id = 'broad_street_cemetery';

-- The Burying Point (intel-linked duplicate of Charter Street Cemetery)
-- Mirror the Charter Street content so the anchor POI near the Witch
-- Trials Memorial has the same rich narration when triggered.
UPDATE salem_pois
SET historical_note = (SELECT historical_note FROM salem_pois WHERE id = 'charter_street_cemetery'),
    updated_at = NOW()
WHERE id = 'the_burying_point';

SELECT id, LENGTH(historical_note) AS hn_chars FROM salem_pois
WHERE id IN ('charter_street_cemetery','howard_street_cemetery','broad_street_cemetery','the_burying_point')
ORDER BY id;

COMMIT;
