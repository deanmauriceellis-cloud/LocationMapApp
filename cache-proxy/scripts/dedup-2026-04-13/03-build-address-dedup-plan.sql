-- Session 123 — Address-based dedup plan (Phase 9U cleanup, second pass)
--
-- Strategy:
--   1. Normalize each row's `address` to a canonical `<number> <street>` form
--      (strip city/state/zip, normalize street-type abbreviations).
--   2. Group rows that share both:
--        a) the same canonical address
--        b) at least one ≥4-char alphanumeric name token
--      The token-overlap check guards multi-tenant addresses (e.g. 32 Derby Sq
--      has many distinct businesses — we don't want to collapse those).
--   3. Pick survivor per group via the same scoring as the name-pass:
--        narration > intel link > shorter id.
--
-- Output: `address_dedup_plan` with one row per duplicate POI:
--   address_key | survivor_id | loser_id | shared_tokens | loser_name | …

DROP TABLE IF EXISTS address_dedup_plan;

CREATE TABLE address_dedup_plan AS
WITH normed AS (
  SELECT
    id, name, lat, lng, address, category,
    short_narration, long_narration, intel_entity_id,
    -- ── Address normalization ────────────────────────────────────────────
    -- 1) Take everything before the first comma (drops ", Salem, MA, 01970").
    -- 2) Lowercase.
    -- 3) Strip trailing street-type word ("street", "st", "avenue", etc.).
    -- 4) Collapse whitespace + trim.
    -- 5) Keep only if it starts with a digit (street number) AND has a name token.
    regexp_replace(
      regexp_replace(
        regexp_replace(
          lower(split_part(COALESCE(address, ''), ',', 1)),
          '\s+(street|st\.?|avenue|ave\.?|road|rd\.?|boulevard|blvd\.?|court|ct\.?|drive|dr\.?|lane|ln\.?|place|pl\.?|square|sq\.?|highway|hwy\.?|way|terrace|ter\.?|circle|cir\.?|parkway|pkwy\.?|wharf|alley)\s*$',
          '', 'g'
        ),
        '\s+', ' ', 'g'
      ),
      '^\s+|\s+$', '', 'g'
    ) AS address_key,
    -- ── Name token set: alphanumeric runs of ≥4 chars, lowercased ────────
    (SELECT array_agg(DISTINCT t)
     FROM regexp_split_to_table(lower(name), '[^a-z0-9]+') t
     WHERE LENGTH(t) >= 4) AS name_tokens
  FROM salem_pois
  WHERE deleted_at IS NULL
),
valid AS (
  -- Only consider rows where the address_key looks like "<digit>… <text>".
  SELECT * FROM normed
  WHERE address_key ~ '^[0-9]'
    AND address_key ~ '[a-z]'
    AND name_tokens IS NOT NULL
    AND array_length(name_tokens, 1) >= 1
),
-- Stoplist: tokens that don't count as "shared brand" evidence.
-- Includes street-type words, common geographic and generic business tokens.
-- The street NAME (e.g. "canal", "derby") is excluded per-pair via address_key tokens.
stoplist AS (
  SELECT unnest(ARRAY[
    'salem', 'peabody', 'beverly', 'marblehead', 'massachusetts',
    'north', 'south', 'east', 'west',
    'street', 'avenue', 'road', 'boulevard', 'court', 'drive', 'lane',
    'place', 'square', 'highway', 'wharf',
    'shop', 'store', 'sales', 'group', 'services', 'center', 'company',
    'auto', 'salon', 'corp', 'llc', 'inc',
    'office', 'associates', 'partners', 'agency',
    'this', 'that', 'with', 'from'
  ]) AS t
),
-- Pairs of POIs that share an address AND at least one non-stopword name token
-- AND are within 50m. Pair-based to handle clusters where some rows are >50m apart.
pairs AS (
  SELECT a.id AS id_a, b.id AS id_b,
         a.address_key,
         (SELECT array_agg(t) FROM unnest(a.name_tokens) t WHERE t = ANY(b.name_tokens)) AS shared_tokens
  FROM valid a
  JOIN valid b
    ON a.address_key = b.address_key
   AND a.id < b.id
   AND ABS(a.lat - b.lat) < 0.00045         -- ~50m
   AND ABS(a.lng - b.lng) < 0.0006          -- ~50m
   -- If both have a category, they must match (sub-tenants in different
   -- POI types like GHOST_TOUR vs ENTERTAINMENT are NOT duplicates).
   AND (a.category IS NULL OR b.category IS NULL OR a.category = b.category)
  WHERE EXISTS (
    SELECT 1
    FROM unnest(a.name_tokens) t
    WHERE t = ANY(b.name_tokens)
      AND t NOT IN (SELECT t FROM stoplist)
      -- exclude any token that appears in the address_key (e.g. street name)
      AND a.address_key !~ ('(^|[^a-z])' || t || '([^a-z]|$)')
  )
  -- AND: reject pairs where BOTH sides have ≥2 unique non-stopword non-shared tokens.
  -- Catches "different professionals at same brand/address" (e.g. two TurboTax agents,
  -- two lawyers in same office building).
  AND (
    (SELECT COUNT(*) FROM unnest(a.name_tokens) t
     WHERE t <> ALL(b.name_tokens)
       AND t NOT IN (SELECT t FROM stoplist)
       AND a.address_key !~ ('(^|[^a-z])' || t || '([^a-z]|$)')) < 2
    OR
    (SELECT COUNT(*) FROM unnest(b.name_tokens) t
     WHERE t <> ALL(a.name_tokens)
       AND t NOT IN (SELECT t FROM stoplist)
       AND a.address_key !~ ('(^|[^a-z])' || t || '([^a-z]|$)')) < 2
  )
),
-- Cluster connected pairs into groups via union-find emulation
-- using min-id as group representative across iterations.
edges AS (
  SELECT id_a AS u, id_b AS v FROM pairs
  UNION
  SELECT id_b, id_a FROM pairs
),
-- Iterate to find min reachable id for each row (transitive closure cap at 5 hops, plenty for these small clusters)
hop1 AS (SELECT u AS id, LEAST(u, MIN(v)) AS rep FROM edges GROUP BY u),
hop2 AS (
  SELECT h.id, LEAST(h.rep, COALESCE(MIN(h2.rep), h.rep)) AS rep
  FROM hop1 h LEFT JOIN edges e ON e.u = h.id LEFT JOIN hop1 h2 ON h2.id = e.v
  GROUP BY h.id, h.rep
),
hop3 AS (
  SELECT h.id, LEAST(h.rep, COALESCE(MIN(h2.rep), h.rep)) AS rep
  FROM hop2 h LEFT JOIN edges e ON e.u = h.id LEFT JOIN hop2 h2 ON h2.id = e.v
  GROUP BY h.id, h.rep
),
clusters AS (
  SELECT id, rep AS group_rep FROM hop3
),
ranked AS (
  SELECT v.*, c.group_rep,
    ((CASE WHEN COALESCE(v.short_narration, '') <> '' OR COALESCE(v.long_narration, '') <> '' THEN 1000000 ELSE 0 END)
     + COALESCE(LENGTH(v.short_narration), 0)
     + COALESCE(LENGTH(v.long_narration), 0)
     + (CASE WHEN v.intel_entity_id IS NOT NULL THEN 500 ELSE 0 END)
     - LENGTH(v.id)
    ) AS score
  FROM valid v
  JOIN clusters c USING (id)
),
picks AS (
  SELECT group_rep, id AS survivor_id, score AS survivor_score, address_key,
         ROW_NUMBER() OVER (PARTITION BY group_rep ORDER BY score DESC, id ASC) AS rn
  FROM ranked
),
survivors AS (
  SELECT group_rep, survivor_id, survivor_score, address_key FROM picks WHERE rn = 1
)
SELECT
  s.address_key,
  s.group_rep,
  s.survivor_id,
  s.survivor_score,
  r.id                                          AS loser_id,
  r.score                                       AS loser_score,
  r.name                                        AS loser_name,
  r.short_narration IS NOT NULL                 AS loser_has_short,
  r.long_narration  IS NOT NULL                 AS loser_has_long,
  r.intel_entity_id IS NOT NULL                 AS loser_has_intel
FROM survivors s
JOIN ranked r ON r.group_rep = s.group_rep
JOIN salem_pois ls ON ls.id = s.survivor_id   -- survivor's full row for intel_entity_id
WHERE r.id <> s.survivor_id
  -- Operator rule: a row with a UNIQUE BCS (intel_entity_id) must stay.
  -- Only collapse a loser if its intel_entity_id is NULL or same as the survivor's.
  AND (r.intel_entity_id IS NULL
       OR (ls.intel_entity_id IS NOT NULL AND r.intel_entity_id = ls.intel_entity_id))
ORDER BY s.address_key, r.id;

-- Summary
SELECT 'ADDRESS_GROUPS' AS metric, COUNT(DISTINCT group_rep) AS value FROM address_dedup_plan
UNION ALL
SELECT 'LOSERS_TO_SOFT_DELETE', COUNT(*) FROM address_dedup_plan
UNION ALL
SELECT 'LOSERS_WITH_NARRATION', COUNT(*) FROM address_dedup_plan WHERE loser_has_short OR loser_has_long
UNION ALL
SELECT 'LOSERS_WITH_INTEL_LINK', COUNT(*) FROM address_dedup_plan WHERE loser_has_intel;
