-- cleanup-polluted-historical-notes.sql — Session 125 (2026-04-14)
--
-- Background: Session 124's sync-historical-notes-from-intel.js wrote SI's
-- medium_narration into salem_pois.historical_note as a fallback for ~1,100
-- POIs when no dedicated SI historical_note existed. Many of those POIs are
-- categorically modern (SHOPPING, FOOD_DRINK, OFFICES, HEALTHCARE, etc.) or
-- are miscategorized as TOURISM_HISTORY despite being modern tour/museum
-- businesses. Result: "Spellbound Tours", "Vampfangs", "Salem Ghosts Tours",
-- "World of Wizardry" etc. narrated during the Heritage Trail walk overnight.
--
-- The Android app's isCategoricallyHistorical() gate covers most modern
-- categories, but it trusts historical_note for any row categorized as
-- TOURISM_HISTORY. This script wipes historical_note from rows where the
-- content is known-bad so they can't leak even if miscategorized.
--
-- Strategy:
--   1. NULL historical_note for every row whose category is NOT categorically
--      historical (anything except TOURISM_HISTORY + the amusement-like set
--      that also has year_established).
--   2. NULL historical_note for the specific miscategorized venues from the
--      overnight test log.
--
-- Follow-up: re-run sync-historical-notes-from-intel.js. The S125 version
-- no longer writes the fallback, so only rows with dedicated SI content
-- are repopulated. Everything else stays NULL and falls out of Historical
-- Mode narration via the isNullOrBlank check in isHistoricalQualified.

BEGIN;

-- Pass 1: blanket clear for clearly-non-historical categories. The
-- Historical Mode filter would already silence these, but NULLing the
-- column prevents accidental use elsewhere.
UPDATE salem_pois SET historical_note = NULL, updated_at = NOW()
WHERE historical_note IS NOT NULL
  AND deleted_at IS NULL
  AND category IN (
    'SHOPPING', 'FOOD_DRINK', 'OFFICES', 'HEALTHCARE',
    'AUTO_SERVICES', 'WITCH_SHOP', 'PSYCHIC_TAROT',
    'FITNESS', 'FINANCE', 'LEGAL', 'REAL_ESTATE',
    'PROFESSIONAL_SERVICES', 'BEAUTY', 'LODGING',
    'NIGHTLIFE', 'CONVENIENCE', 'PETS', 'AUTOMOTIVE',
    'HARDWARE', 'WHOLESALE', 'MANUFACTURING',
    'GAS_STATION', 'GROCERY', 'PARKING'
  );

-- Pass 2: miscategorized-as-TOURISM_HISTORY venues the overnight test
-- caught narrating modern content. These are businesses, not historic
-- landmarks — reclassify them out of TOURISM_HISTORY AND NULL the note.
UPDATE salem_pois SET
    category = 'ENTERTAINMENT',
    historical_note = NULL,
    updated_at = NOW()
WHERE id IN (
    'spellbound_tours',
    'salem_tales',
    'salem_historical_tours',
    'salem_ghosts_tours',
    '1692_before_and_after_llc',
    'world_of_wizardry'
) AND deleted_at IS NULL;

-- Arts association + museum-brand pivot to ENTERTAINMENT (modern venues,
-- not pre-1950 heritage).
UPDATE salem_pois SET
    category = 'ENTERTAINMENT',
    historical_note = NULL,
    updated_at = NOW()
WHERE id IN (
    'salem_arts_association',
    'witch_history_museum'
) AND deleted_at IS NULL;

-- Vampfangs is a retail costume/fangs shop. SHOPPING.
UPDATE salem_pois SET
    category = 'SHOPPING',
    historical_note = NULL,
    updated_at = NOW()
WHERE id = 'vampfangs'
  AND deleted_at IS NULL;

-- Summary
SELECT
    'After cleanup' AS stage,
    COUNT(*) FILTER (WHERE historical_note IS NOT NULL) AS with_note,
    COUNT(*) FILTER (WHERE historical_note IS NULL)     AS without_note,
    COUNT(*) AS total
FROM salem_pois
WHERE deleted_at IS NULL;

COMMIT;
