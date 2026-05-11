-- author-historical-pois-batch2.sql — overnight content boost
-- Hand-authored tour-guide-voice historical_note for 12 high-value historical
-- POIs along the Heritage Trail whose SI-generated content hasn't landed yet.
-- Each is ~600-1200 chars (30-70 s TTS). SalemIntelligence's dedicated
-- historical_note pass will overwrite these when it catches up (the sync
-- script is source-aware — real SI content replaces hand-authored fallbacks).

BEGIN;

-- Washington Arch (on Salem Common, erected 1805)
UPDATE salem_pois
SET historical_note = E'Look up at Washington Arch. This gateway into Salem Common was erected in 1805 to honor George Washington, who had visited the town in 1789. Salem gave Washington a hero''s welcome that day — he stayed at the Joshua Ward House just a few blocks from here. The arch you''re looking at isn''t the original; the 1805 wooden one rotted and was rebuilt in sturdier form. Walk through it and you''re stepping onto a nine-acre green that has been the town''s gathering place since the 1630s. Militia drilled here. Cattle grazed here. Every Fourth of July for more than two centuries, Salem has come here to celebrate independence. Pause a moment on the other side and look back — this is the front door of Salem''s civic memory.',
    updated_at = NOW()
WHERE id = 'washington_arch';

-- Andrew Safford House (1819, Washington Square East)
UPDATE salem_pois
SET historical_note = E'You''re standing in front of the Andrew Safford House, finished in 1819. When it was built, people called it the most expensive private house in New England — a staggering thirty-six thousand dollars, which was enough to buy fifty ordinary houses at the time. Safford was a prosperous sea captain and shipping merchant, and he wanted the world to know it. Look at the scale: deep red brick, tall Corinthian columns, a side porch, a cupola on the roof for watching ships come in. It''s almost ostentatious — and it was meant to be. In 1920 the Essex Institute, a historical society, acquired the house, and today it stands as one of the crown jewels of the Peabody Essex Museum''s historic properties. An extravagant monument to the money America''s first millionaires spent in Salem.',
    updated_at = NOW()
WHERE id = 'andrew_safford_house';

-- The First Muster (monument to the 1637 first militia)
UPDATE salem_pois
SET historical_note = E'Stop here a moment. You''re standing on the ground where the American citizen-soldier tradition began. On April 30, 1637, Salem mustered the first militia companies in the English North American colonies — not one regiment, but three: the North, South, and East. Every able-bodied man was required to report with his own musket, to drill, and to defend the town. That date — April 30, 1637 — is why the modern U.S. National Guard traces its founding to Salem Common. This small monument marks the occasion. Three hundred eighty-some years later, every time a Guardsman is deployed, the line runs back through every muster to this field. America''s militia began here.',
    updated_at = NOW()
WHERE id = 'the_first_muster';

-- Gardner-Pingree House (1804, Essex Street, McIntire masterpiece)
UPDATE salem_pois
SET historical_note = E'Look up at those delicate carvings around the door and windows — that''s the signature of Samuel McIntire, a Salem woodcarver who became one of the most important architects in early America. This house, built in 1804 for the merchant John Gardner, is widely considered McIntire''s finest surviving work. Every ornament is hand-carved: the cornice, the fanlight, the garland of flowers above the door. In the early 1800s the house passed to the Pingree family, who lived here for generations. Its most notorious moment came in 1830, when Captain Joseph White was murdered in his bed inside these walls — a crime that shocked all of New England and inspired Nathaniel Hawthorne''s story The House of the Seven Gables. Today it''s a museum, preserved almost exactly as the Pingrees left it.',
    updated_at = NOW()
WHERE id = 'gardner_pingree_house';

-- Hamilton Hall (1805, Chestnut Street — Samuel McIntire)
UPDATE salem_pois
SET historical_note = E'This is Hamilton Hall, built in 1805 and designed by Samuel McIntire — the same architect whose fingerprints are on half the Federal-era houses in this neighborhood. It''s named for Alexander Hamilton, who had died the year before in his duel with Aaron Burr; Salem''s Federalist merchants built this ballroom as a tribute. For two hundred years it has hosted weddings, debates, lectures, and dances. During the War of 1812, impressed British sailors were held here as prisoners. John Quincy Adams lectured from its stage. Lafayette danced here on his triumphal return tour in 1824. Walk inside if you can — the ballroom still has its original sprung maple floor, set on wooden springs so it flexed under dancers'' feet.',
    updated_at = NOW()
WHERE id = 'hamilton_hall';

-- Peirce-Nichols House (1782, Federal Street)
UPDATE salem_pois
SET historical_note = E'The Peirce-Nichols House, built in 1782, is one of the first commissions Samuel McIntire ever took on — he was barely twenty-five. The original front is pure Georgian: heavy, symmetrical, a little severe. But twenty years later, when Jerathmeel Peirce''s daughter married, the family asked McIntire back to transform the interior and the rear facade in the lighter, more graceful Federal style that was just coming into fashion. You can see both styles in one building — the old formal Georgian outside, the graceful Federal in the parlors and the garden wing. It''s a remarkable document of how American architecture changed in the decades after the Revolution. The Peirce family lived here for over a century, and much of the original furniture remains.',
    updated_at = NOW()
WHERE id = 'peirce_nichols_house';

-- Derby House (1762, Derby Wharf — oldest brick house in Salem)
UPDATE salem_pois
SET historical_note = E'This is the Derby House, built in 1762 — the oldest surviving brick house in Salem. Elias Hasket Derby, who lived here, made his fortune in the post-Revolution China trade and became one of the first millionaires in America. The house is small by later standards, but in 1762 it was a statement: brick was imported and expensive, and most Salem houses at the time were modest clapboard. Stand back and look — the Derby family''s wharves, where the global wealth came in, were just fifty yards from the front door. A ship could tie up, and the family could walk down and inspect the cargo of silks, tea, pepper, and porcelain before it was sold. The house now sits within the Salem Maritime National Historic Site.',
    updated_at = NOW()
WHERE id = 'derby_house';

-- Hawthorne Statue (near Custom House)
UPDATE salem_pois
SET historical_note = E'This bronze figure looking thoughtful and slightly downcast is Nathaniel Hawthorne, Salem''s most famous native son, cast in 1925 for the centennial of his birth. Hawthorne was born a few blocks from here in 1804 and spent much of his adult life haunted by his own family''s past — his great-great-grandfather John Hathorne was one of the judges who sent twenty people to their deaths in 1692. Nathaniel added a ''w'' to the family name to put distance between himself and the judge. He worked just behind you at the Custom House as a surveyor of the port, a tedious job that he transformed into the famous opening chapter of The Scarlet Letter. The sculptor is Bela Pratt, and the pose is meant to suggest a man listening — perhaps for ghosts.',
    updated_at = NOW()
WHERE id = 'hawthorne_statue';

-- Pedrick Store House (1770, Derby Wharf)
UPDATE salem_pois
SET historical_note = E'This small gambrel-roofed building is the Pedrick Store House, built around 1770 — a rare surviving colonial warehouse from Salem''s golden age of sail. Ships coming back from Canton or Calcutta would unload cargo directly onto wharves like Derby''s, and warehouses like this one stored the goods before they were sold or shipped inland. Pepper, silks, porcelain, tea, sugar, rum — the cargoes that made Salem fortunes passed through buildings exactly like this. It was painstakingly moved here from nearby Marblehead in 2009 to be preserved as part of the Salem Maritime National Historic Site. Peek inside if you can and picture it full of barrels and crates, smelling of salt and spice.',
    updated_at = NOW()
WHERE id = 'pedrick_store_house';

-- Derby Wharf Light Station (1871 lighthouse at the end of Derby Wharf)
UPDATE salem_pois
SET historical_note = E'Walk out to the tip of Derby Wharf and you''ll reach the Derby Wharf Light Station, built in 1871. The wharf itself is older than the lighthouse by almost a century — Elias Hasket Derby''s ships used this very spit of land to offload cargo from Canton, Calcutta, and the West Indies. By the 1870s Salem''s maritime glory was fading, but the harbor still needed a navigation beacon, so the federal government put this squat square tower at the wharf''s end. It stood fifteen feet tall with a fixed red light. Decommissioned in 1977, then automated and relit in the 1980s. Today it''s the most photographed working lighthouse in Essex County, and the view from here across Salem Harbor is the same view every returning captain saw for three hundred years.',
    updated_at = NOW()
WHERE id = 'derby_wharf_light_station';

-- Crowninshield-Bentley House (1727)
UPDATE salem_pois
SET historical_note = E'The Crowninshield-Bentley House was built in 1727 for John Crowninshield, of the mercantile dynasty whose ships helped launch Salem''s golden age. In the 1790s Reverend William Bentley moved in as a boarder — and Bentley is who you really want to know about. He was a Unitarian minister, a scholar of seventeen languages, a diarist who recorded Salem''s daily life for three decades, and an abolitionist. His diaries — covering 1784 to 1819 — are one of the most important primary sources for early American history. He died in this house in 1819, exactly while Salem''s maritime wealth was cresting. The house was moved to the Peabody Essex Museum campus in 1959 to preserve it; the wood floors inside still creak with the weight of Bentley''s books.',
    updated_at = NOW()
WHERE id = 'crowninshield_bentley_house';

-- Hathaway House (the House of the Seven Gables — 1668, one of oldest)
UPDATE salem_pois
SET historical_note = E'Welcome to one of the oldest surviving wooden houses in the United States. Built around 1668 for the sea captain John Turner, this dark, weathered house was the inspiration for Nathaniel Hawthorne''s 1851 novel The House of the Seven Gables. Hawthorne''s cousin Susanna Ingersoll owned it when he was young, and he visited often. When you look at the building you''re looking at almost three and a half centuries of Salem sailors, ship owners, and ghosts. The distinctive gables — seven of them — were added and removed over generations as fashions changed; by the time Hawthorne wrote the novel many had been torn down. In 1908 the philanthropist Caroline Emmerton bought the house, restored the seven gables, and opened it as a settlement house and museum. It''s still operating that mission today — tours, a historic garden, and programs for immigrant children, just as Emmerton intended.',
    updated_at = NOW()
WHERE id = 'hathaway_house';

-- Report
SELECT id, LENGTH(historical_note) AS hn
FROM salem_pois
WHERE id IN ('washington_arch','andrew_safford_house','the_first_muster','gardner_pingree_house',
             'hamilton_hall','peirce_nichols_house','derby_house','hawthorne_statue',
             'pedrick_store_house','derby_wharf_light_station','crowninshield_bentley_house','hathaway_house')
ORDER BY id;

COMMIT;
