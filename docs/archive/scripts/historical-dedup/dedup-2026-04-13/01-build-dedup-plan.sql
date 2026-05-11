-- Session 123 dedup plan — Phase 9U cleanup
--
-- Identifies true-duplicate POI groups (same normalized name, within ~50m)
-- and picks a survivor + losers per group.
--
-- Survivor selection priority:
--   1. Has narration (short or long non-empty) over no narration
--   2. Longer combined narration length
--   3. Newer `last_synced_from_intel` timestamp (if intel-linked)
--   4. Has intel_entity_id (linked) over not-linked
--   5. Shorter id length (canonical-looking)
--   6. Lexically smaller id (deterministic tie-break)
--
-- Outputs `dedup_plan` table with one row per duplicate POI:
--   group_key | survivor_id | loser_id | survivor_score | loser_score

DROP TABLE IF EXISTS dedup_plan;

CREATE TABLE dedup_plan AS
WITH groups AS (
  SELECT
    -- Normalize: lowercase, strip leading "Historic|The|Old|New",
    -- strip leading "<Word> Street -" and trailing "- <Word> Street" disambiguation,
    -- drop non-alphanumeric.
    regexp_replace(
      lower(
        regexp_replace(
          regexp_replace(
            regexp_replace(name, '^(historic|the|old|new)\s+', '', 'i'),
            '^[A-Za-z]+\s+Street\s*[-–—]\s*', '', 'i'
          ),
          '\s*[-–—]\s*[A-Za-z]+\s+Street\s*$', '', 'i'
        )
      ),
      '[^a-z0-9]', '', 'g'
    ) AS norm_key,
    id, name, lat, lng, short_narration, long_narration,
    intel_entity_id, data_source
  FROM salem_pois
  WHERE deleted_at IS NULL
),
group_stats AS (
  -- True dup = same norm_key + max pairwise distance ≤ 50m
  SELECT norm_key, COUNT(*) AS n,
         GREATEST((MAX(lat) - MIN(lat)) * 111000.0,
                  (MAX(lng) - MIN(lng)) * 82000.0) AS max_dist_m
  FROM groups GROUP BY norm_key
),
true_dups AS (
  SELECT g.*, gs.n
  FROM groups g
  JOIN group_stats gs USING (norm_key)
  WHERE gs.n > 1 AND gs.max_dist_m <= 50.0
),
ranked AS (
  SELECT *,
    -- Composite score, higher = better survivor
    ((CASE WHEN COALESCE(short_narration, '') <> '' OR COALESCE(long_narration, '') <> '' THEN 1000000 ELSE 0 END)
     + COALESCE(LENGTH(short_narration), 0)
     + COALESCE(LENGTH(long_narration), 0)
     + (CASE WHEN intel_entity_id IS NOT NULL THEN 500 ELSE 0 END)
     - LENGTH(id)  -- shorter id wins tie
    ) AS score
  FROM true_dups
),
picks AS (
  SELECT norm_key, id AS survivor_id, score AS survivor_score,
         ROW_NUMBER() OVER (PARTITION BY norm_key ORDER BY score DESC, id ASC) AS rn
  FROM ranked
),
survivors AS (
  SELECT norm_key, survivor_id, survivor_score FROM picks WHERE rn = 1
)
SELECT
  s.norm_key                                    AS group_key,
  s.survivor_id,
  s.survivor_score,
  r.id                                          AS loser_id,
  r.score                                       AS loser_score,
  r.name                                        AS loser_name,
  r.short_narration IS NOT NULL                 AS loser_has_short,
  r.long_narration  IS NOT NULL                 AS loser_has_long,
  r.intel_entity_id IS NOT NULL                 AS loser_has_intel
FROM survivors s
JOIN ranked r USING (norm_key)
WHERE r.id <> s.survivor_id
ORDER BY s.norm_key, r.id;

-- Summary
SELECT
  'GROUPS'  AS metric, COUNT(DISTINCT group_key) AS value FROM dedup_plan
UNION ALL
SELECT 'LOSERS_TO_SOFT_DELETE', COUNT(*) FROM dedup_plan
UNION ALL
SELECT 'LOSERS_WITH_NARRATION', COUNT(*) FROM dedup_plan WHERE loser_has_short OR loser_has_long
UNION ALL
SELECT 'LOSERS_WITH_INTEL_LINK', COUNT(*) FROM dedup_plan WHERE loser_has_intel;
