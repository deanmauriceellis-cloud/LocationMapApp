-- dedup-burying-point.sql — Session 125 (2026-04-14)
--
-- the_burying_point (BCS-linked, from salem_intelligence_bcs) and
-- charter_street_cemetery (OSM-sourced) are the same physical location
-- at the same gravel path. During the overnight Heritage Trail test
-- they narrated back-to-back with identical content because the
-- sync-historical-notes-from-intel.js pass mirror-copied the OSM note
-- into the BCS row. That note was NULLed at 01:56 mid-night.
--
-- Keep the OSM row (charter_street_cemetery) — it carries the authored
-- historical_note. Soft-delete the BCS duplicate with the dedup-loser
-- flag so it joins S123's pre-Play-Store hard-delete batch.
--
-- Idempotent: running again is a no-op because deleted_at IS NOT NULL
-- excludes the row from the WHERE clause.

BEGIN;

-- Verify no tour_stops reference (should be 0 rows; if not, abort manually).
SELECT tour_id, poi_id FROM salem_tour_stops WHERE poi_id = 'the_burying_point';

UPDATE salem_pois
   SET deleted_at = NOW(),
       data_source = COALESCE(data_source, '') || ' | dedup-2026-04-14-loser-burying-point',
       updated_at = NOW()
 WHERE id = 'the_burying_point'
   AND deleted_at IS NULL;

-- Summary
SELECT id, name, category, deleted_at, data_source
  FROM salem_pois
 WHERE id IN ('the_burying_point', 'charter_street_cemetery');

COMMIT;
