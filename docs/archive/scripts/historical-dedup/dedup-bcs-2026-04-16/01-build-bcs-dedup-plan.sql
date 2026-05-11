-- Session 135 BCS-prioritized dedup plan
--
-- Finds all active POIs where a BCS entry shares a name with a non-BCS entry.
-- BCS entry = survivor, non-BCS entry = loser.
--
-- Exclusions:
--   * Already-soft-deleted entries (data_source LIKE '%loser%')
--   * Multi-location chains where distance > 500m:
--     CVS Pharmacy, Dunkin', Subway, Starbucks Coffee Company,
--     Salem Five Bank, Burba Dental Partners
--   * BCS-vs-BCS duplicates (handled separately)

DROP TABLE IF EXISTS bcs_dedup_plan;

CREATE TABLE bcs_dedup_plan AS
WITH pairs AS (
  SELECT
    b.id AS survivor_id,
    b.name AS survivor_name,
    b.category AS survivor_cat,
    b.data_source AS survivor_source,
    o.id AS loser_id,
    o.name AS loser_name,
    o.category AS loser_cat,
    o.data_source AS loser_source,
    -- Haversine distance in meters
    ROUND(CAST(
      6371000 * ACOS(
        LEAST(1.0, COS(RADIANS(b.lat)) * COS(RADIANS(o.lat)) * COS(RADIANS(o.lng - b.lng))
        + SIN(RADIANS(b.lat)) * SIN(RADIANS(o.lat)))
      ) AS numeric), 0) AS dist_m
  FROM salem_pois b
  JOIN salem_pois o ON LOWER(TRIM(b.name)) = LOWER(TRIM(o.name)) AND b.id != o.id
  WHERE b.data_source = 'salem_intelligence_bcs'
    AND o.data_source != 'salem_intelligence_bcs'
    AND o.data_source NOT LIKE '%loser%'
    AND b.status = 'open' AND o.status = 'open'
    AND b.deleted_at IS NULL AND o.deleted_at IS NULL
)
SELECT * FROM pairs
WHERE NOT (
  -- Skip multi-location chains where distance suggests different physical stores
  LOWER(survivor_name) IN (
    'cvs pharmacy', 'dunkin''', 'subway', 'starbucks coffee company',
    'salem five bank', 'burba dental partners'
  )
  AND dist_m > 500
);

-- Show the plan
SELECT survivor_id, survivor_name, survivor_cat,
       loser_id, loser_cat, loser_source, dist_m
FROM bcs_dedup_plan
ORDER BY survivor_name, loser_id;

SELECT COUNT(*) AS total_pairs FROM bcs_dedup_plan;
