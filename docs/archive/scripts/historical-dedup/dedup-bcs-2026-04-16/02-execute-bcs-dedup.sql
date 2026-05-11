-- Session 135 BCS-prioritized dedup execution
--
-- Prerequisites:
--   * 01-build-bcs-dedup-plan.sql has been run (creates bcs_dedup_plan table)
--   * Tour stop FKs verified and repointed in Step 0
--
-- What this script does:
--   0. Repoint tour stop FKs from loser_id to survivor_id
--   1. Merge scalar fields loser->survivor where survivor's field is NULL/empty
--   2. Merge JSONB arrays loser->survivor where survivor's array is empty/null
--   3. Merge wave (non-null loser wave fills null survivor wave)
--   4. Soft-delete losers (deleted_at = NOW(), data_source append marker)
--   5. Hard-delete hawthorne_hotel_lodging (user-requested)
--
-- Single transaction. ROLLBACK if anything looks wrong.

BEGIN;

-- ── Step 0: Repoint tour stop FKs ──────────────────────────────────────────
-- Must happen BEFORE soft-deleting losers.

UPDATE salem_tour_stops ts
SET poi_id = d.survivor_id
FROM bcs_dedup_plan d
WHERE ts.poi_id = d.loser_id;

-- Verify no orphaned tour stops remain
SELECT ts.tour_id, ts.poi_id, ts.stop_order
FROM salem_tour_stops ts
LEFT JOIN salem_pois p ON ts.poi_id = p.id AND p.deleted_at IS NULL
WHERE p.id IS NULL;
-- (should return 0 rows)

-- ── Step 1: Merge scalar fields (survivor wins; loser fills gaps) ──────────

UPDATE salem_pois s
SET
  short_narration    = COALESCE(NULLIF(s.short_narration, ''),   NULLIF(l.short_narration, '')),
  long_narration     = COALESCE(NULLIF(s.long_narration, ''),    NULLIF(l.long_narration, '')),
  address            = COALESCE(NULLIF(s.address, ''),           NULLIF(l.address, '')),
  description        = COALESCE(NULLIF(s.description, ''),       NULLIF(l.description, '')),
  short_description  = COALESCE(NULLIF(s.short_description, ''), NULLIF(l.short_description, '')),
  custom_description = COALESCE(NULLIF(s.custom_description, ''),NULLIF(l.custom_description, '')),
  origin_story       = COALESCE(NULLIF(s.origin_story, ''),      NULLIF(l.origin_story, '')),
  historical_period  = COALESCE(NULLIF(s.historical_period, ''), NULLIF(l.historical_period, '')),
  historical_note    = COALESCE(NULLIF(s.historical_note, ''),   NULLIF(l.historical_note, '')),
  admission_info     = COALESCE(NULLIF(s.admission_info, ''),    NULLIF(l.admission_info, '')),
  phone              = COALESCE(NULLIF(s.phone, ''),             NULLIF(l.phone, '')),
  email              = COALESCE(NULLIF(s.email, ''),             NULLIF(l.email, '')),
  website            = COALESCE(NULLIF(s.website, ''),           NULLIF(l.website, '')),
  hours_text         = COALESCE(NULLIF(s.hours_text, ''),        NULLIF(l.hours_text, '')),
  hours              = COALESCE(s.hours,                         l.hours),
  menu_url           = COALESCE(NULLIF(s.menu_url, ''),          NULLIF(l.menu_url, '')),
  reservations_url   = COALESCE(NULLIF(s.reservations_url, ''),  NULLIF(l.reservations_url, '')),
  order_url          = COALESCE(NULLIF(s.order_url, ''),         NULLIF(l.order_url, '')),
  image_asset        = COALESCE(NULLIF(s.image_asset, ''),       NULLIF(l.image_asset, '')),
  custom_icon_asset  = COALESCE(NULLIF(s.custom_icon_asset, ''), NULLIF(l.custom_icon_asset, '')),
  voice_clip_asset   = COALESCE(NULLIF(s.voice_clip_asset, ''),  NULLIF(l.voice_clip_asset, '')),
  custom_voice_asset = COALESCE(NULLIF(s.custom_voice_asset, ''),NULLIF(l.custom_voice_asset, '')),
  cuisine_type       = COALESCE(NULLIF(s.cuisine_type, ''),      NULLIF(l.cuisine_type, '')),
  price_range        = COALESCE(NULLIF(s.price_range, ''),       NULLIF(l.price_range, '')),
  rating             = COALESCE(s.rating,                        l.rating),
  intel_entity_id    = COALESCE(NULLIF(s.intel_entity_id, ''),   NULLIF(l.intel_entity_id, '')),
  district           = COALESCE(NULLIF(s.district, ''),          NULLIF(l.district, '')),
  year_established   = COALESCE(s.year_established,              l.year_established),
  wave               = COALESCE(s.wave,                          l.wave),
  is_narrated        = s.is_narrated OR l.is_narrated,
  is_tour_poi        = s.is_tour_poi OR l.is_tour_poi
FROM bcs_dedup_plan d
JOIN salem_pois l ON l.id = d.loser_id
WHERE s.id = d.survivor_id;

-- ── Step 2: Merge JSONB arrays where survivor is empty/null ────────────────

UPDATE salem_pois s
SET
  secondary_categories = CASE WHEN jsonb_array_length(COALESCE(s.secondary_categories, '[]'::jsonb)) = 0
                              THEN l.secondary_categories ELSE s.secondary_categories END,
  specialties          = CASE WHEN jsonb_array_length(COALESCE(s.specialties, '[]'::jsonb)) = 0
                              THEN l.specialties ELSE s.specialties END,
  owners               = CASE WHEN jsonb_array_length(COALESCE(s.owners, '[]'::jsonb)) = 0
                              THEN l.owners ELSE s.owners END,
  related_figure_ids   = CASE WHEN jsonb_array_length(COALESCE(s.related_figure_ids, '[]'::jsonb)) = 0
                              THEN l.related_figure_ids ELSE s.related_figure_ids END,
  related_fact_ids     = CASE WHEN jsonb_array_length(COALESCE(s.related_fact_ids, '[]'::jsonb)) = 0
                              THEN l.related_fact_ids ELSE s.related_fact_ids END,
  related_source_ids   = CASE WHEN jsonb_array_length(COALESCE(s.related_source_ids, '[]'::jsonb)) = 0
                              THEN l.related_source_ids ELSE s.related_source_ids END,
  source_categories    = CASE WHEN jsonb_array_length(COALESCE(s.source_categories, '[]'::jsonb)) = 0
                              THEN l.source_categories ELSE s.source_categories END,
  tags                 = CASE WHEN jsonb_array_length(COALESCE(s.tags, '[]'::jsonb)) = 0
                              THEN l.tags ELSE s.tags END,
  action_buttons       = CASE WHEN jsonb_array_length(COALESCE(s.action_buttons, '[]'::jsonb)) = 0
                              THEN l.action_buttons ELSE s.action_buttons END,
  amenities            = CASE WHEN s.amenities IS NULL OR s.amenities = '{}'::jsonb
                              THEN l.amenities ELSE s.amenities END
FROM bcs_dedup_plan d
JOIN salem_pois l ON l.id = d.loser_id
WHERE s.id = d.survivor_id;

-- ── Step 3: Soft-delete losers ─────────────────────────────────────────────

UPDATE salem_pois
SET deleted_at = NOW(),
    data_source = data_source || '|bcs-dedup-2026-04-16-loser'
WHERE id IN (SELECT loser_id FROM bcs_dedup_plan)
  AND deleted_at IS NULL;

-- ── Step 4: Hard-delete hawthorne_hotel_lodging ────────────────────────────
-- User explicitly requested deletion. No FK refs. Wrong category (SHOPPING).

DELETE FROM salem_pois WHERE id = 'hawthorne_hotel_lodging';

-- ── Verification ───────────────────────────────────────────────────────────

SELECT 'losers_soft_deleted' AS metric, COUNT(*) AS value
  FROM salem_pois WHERE data_source LIKE '%bcs-dedup-2026-04-16-loser%'
UNION ALL
SELECT 'hawthorne_hotel_lodging_gone', COUNT(*)
  FROM salem_pois WHERE id = 'hawthorne_hotel_lodging'
UNION ALL
SELECT 'orphaned_tour_stops', COUNT(*)
  FROM salem_tour_stops ts
  LEFT JOIN salem_pois p ON ts.poi_id = p.id AND p.deleted_at IS NULL
  WHERE p.id IS NULL
UNION ALL
SELECT 'total_active_pois', COUNT(*)
  FROM salem_pois WHERE status = 'open' AND deleted_at IS NULL
UNION ALL
SELECT 'tour_stops_repointed', COUNT(*)
  FROM salem_tour_stops ts
  JOIN bcs_dedup_plan d ON ts.poi_id = d.survivor_id;

COMMIT;
