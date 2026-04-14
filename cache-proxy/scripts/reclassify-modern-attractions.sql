-- reclassify-modern-attractions.sql — Session 124 Phase 9R.0
-- Moves clearly-modern tourist attractions out of TOURISM_HISTORY so they
-- stop announcing during Historical Mode. TOURISM_HISTORY stays reserved
-- for landmarks, historic houses, cemeteries, wharves, monuments, and
-- museums with pre-1950 heritage significance.
--
-- These were likely miscategorized at BCS import time because their
-- descriptions mention "history" / "Salem" / "witch" but the venues
-- themselves are modern attractions (post-1970 theme museums,
-- modern-day tours, art studios, paddle companies, haunted shows).
--
-- Safe to re-run: uses an explicit WHERE id IN clause.

BEGIN;

-- Haunted/horror themed modern venues → HAUNTED_ATTRACTION
UPDATE salem_pois SET category = 'HAUNTED_ATTRACTION', historical_note = NULL, updated_at = NOW()
WHERE id IN (
    'halloween_museum',
    'halloween_museum_of_salem',
    'count_orloks_nightmare_gallery_monster_museum',
    'international_monster_museum',
    'haunted_neighborhood_2',
    'gallows_hill_salem'
);

-- Modern tours/cruises/studios → ENTERTAINMENT (not categorically historical
-- but a valid existing category; the Historical Mode filter only admits
-- ENTERTAINMENT items that have year_established, which these don't, so
-- they'll be silenced and hidden during Heritage Trail).
UPDATE salem_pois SET category = 'ENTERTAINMENT', historical_note = NULL, updated_at = NOW()
WHERE id IN (
    'black_cat_tours',
    'bewitched_historical_tours',
    'bewitched_tours',
    'forest_lore_tour',
    'lighthouse_harbor_tours',
    'alijen_charters',
    'coast_to_coast_paddle',
    'coast_to_coast_paddle_2',
    'mahi_harbor_cruises_private_events',
    'gallows_hill_artist_studios',
    'herb_mackeys_metal_sculpture_yard',
    'miramar_print_lab',
    'oleseas_art_studio',
    'joes_fresh_fish_prints',
    'craft_witch',
    'palmers_cove_yacht_club',
    'lady_of_salem_maritime_public_art_celebration'
);

-- Modern info center → CIVIC
UPDATE salem_pois SET category = 'CIVIC', historical_note = NULL, updated_at = NOW()
WHERE id = 'destination_salem_visitor_information_center';

-- Modern parks/beaches → PARKS_REC (still listed as historic-tour-default in
-- category table, but without year_established the filter excludes them)
UPDATE salem_pois SET category = 'PARKS_REC', historical_note = NULL, updated_at = NOW()
WHERE id IN ('forest_river_park','dead_horse_beach');

-- Verify
SELECT COUNT(*) AS reclassified FROM salem_pois WHERE category = 'ATTRACTION' AND id IN (
    'halloween_museum','halloween_museum_of_salem','count_orloks_nightmare_gallery_monster_museum',
    'international_monster_museum','haunted_neighborhood_2','gallows_hill_salem',
    'black_cat_tours','bewitched_historical_tours','bewitched_tours','forest_lore_tour',
    'lighthouse_harbor_tours','alijen_charters','coast_to_coast_paddle','coast_to_coast_paddle_2',
    'mahi_harbor_cruises_private_events','gallows_hill_artist_studios','herb_mackeys_metal_sculpture_yard',
    'miramar_print_lab','oleseas_art_studio','joes_fresh_fish_prints','craft_witch',
    'destination_salem_visitor_information_center','palmers_cove_yacht_club',
    'lady_of_salem_maritime_public_art_celebration','forest_river_park','dead_horse_beach'
);

COMMIT;
