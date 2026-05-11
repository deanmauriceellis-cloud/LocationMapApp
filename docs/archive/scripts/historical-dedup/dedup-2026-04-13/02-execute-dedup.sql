-- Session 123 dedup execute — Phase 9U cleanup
--
-- Prerequisites:
--   * Run 01-build-dedup-plan.sql first (creates `dedup_plan` table)
--   * Pre-flight: verified ZERO FK references from referencing tables to any loser_id
--     (salem_events_calendar, salem_historical_facts, salem_historical_figures,
--      salem_primary_sources, salem_timeline_events, salem_tour_stops)
--
-- What this script does:
--   1. Merges scalar fields loser→survivor where survivor's field is NULL/empty
--   2. Merges JSONB arrays loser→survivor where survivor's array is empty/null
--   3. Soft-deletes losers (deleted_at = NOW(), data_source append marker)
--
-- Wrapped in a single transaction. Roll back with ROLLBACK if anything looks wrong.

BEGIN;

-- ── Step 1: Merge scalar fields (survivor wins; loser fills gaps) ────────────
-- Uses COALESCE so NULL/empty survivor fields get loser's value.
-- For TEXT we treat empty string as "missing" via NULLIF.

UPDATE salem_pois s
SET
  short_narration   = COALESCE(NULLIF(s.short_narration, ''),   NULLIF(l.short_narration, '')),
  long_narration    = COALESCE(NULLIF(s.long_narration, ''),    NULLIF(l.long_narration, '')),
  address           = COALESCE(NULLIF(s.address, ''),           NULLIF(l.address, '')),
  description       = COALESCE(NULLIF(s.description, ''),       NULLIF(l.description, '')),
  short_description = COALESCE(NULLIF(s.short_description, ''), NULLIF(l.short_description, '')),
  custom_description= COALESCE(NULLIF(s.custom_description, ''),NULLIF(l.custom_description, '')),
  origin_story      = COALESCE(NULLIF(s.origin_story, ''),      NULLIF(l.origin_story, '')),
  historical_period = COALESCE(NULLIF(s.historical_period, ''), NULLIF(l.historical_period, '')),
  historical_note   = COALESCE(NULLIF(s.historical_note, ''),   NULLIF(l.historical_note, '')),
  admission_info    = COALESCE(NULLIF(s.admission_info, ''),    NULLIF(l.admission_info, '')),
  phone             = COALESCE(NULLIF(s.phone, ''),             NULLIF(l.phone, '')),
  email             = COALESCE(NULLIF(s.email, ''),             NULLIF(l.email, '')),
  website           = COALESCE(NULLIF(s.website, ''),           NULLIF(l.website, '')),
  hours_text        = COALESCE(NULLIF(s.hours_text, ''),        NULLIF(l.hours_text, '')),
  hours             = COALESCE(s.hours,                         l.hours),
  menu_url          = COALESCE(NULLIF(s.menu_url, ''),          NULLIF(l.menu_url, '')),
  reservations_url  = COALESCE(NULLIF(s.reservations_url, ''),  NULLIF(l.reservations_url, '')),
  order_url         = COALESCE(NULLIF(s.order_url, ''),         NULLIF(l.order_url, '')),
  image_asset       = COALESCE(NULLIF(s.image_asset, ''),       NULLIF(l.image_asset, '')),
  custom_icon_asset = COALESCE(NULLIF(s.custom_icon_asset, ''), NULLIF(l.custom_icon_asset, '')),
  voice_clip_asset  = COALESCE(NULLIF(s.voice_clip_asset, ''),  NULLIF(l.voice_clip_asset, '')),
  custom_voice_asset= COALESCE(NULLIF(s.custom_voice_asset, ''),NULLIF(l.custom_voice_asset, '')),
  cuisine_type      = COALESCE(NULLIF(s.cuisine_type, ''),      NULLIF(l.cuisine_type, '')),
  price_range       = COALESCE(NULLIF(s.price_range, ''),       NULLIF(l.price_range, '')),
  rating            = COALESCE(s.rating,                        l.rating),
  intel_entity_id   = COALESCE(NULLIF(s.intel_entity_id, ''),   NULLIF(l.intel_entity_id, '')),
  district          = COALESCE(NULLIF(s.district, ''),          NULLIF(l.district, '')),
  year_established  = COALESCE(s.year_established,              l.year_established),
  is_narrated       = s.is_narrated OR l.is_narrated,
  is_tour_poi       = s.is_tour_poi OR l.is_tour_poi
FROM dedup_plan d
JOIN salem_pois l ON l.id = d.loser_id
WHERE s.id = d.survivor_id;

-- ── Step 2: Merge JSONB arrays where survivor is empty/null ──────────────────
-- An empty array `[]` is equivalent to "no data" — fill from loser if loser has data.

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
FROM dedup_plan d
JOIN salem_pois l ON l.id = d.loser_id
WHERE s.id = d.survivor_id;

-- ── Step 3: Soft-delete losers ───────────────────────────────────────────────
-- data_source gets an append marker so we know this row is awaiting hard-delete.

UPDATE salem_pois
SET deleted_at = NOW(),
    data_source = data_source || '|dedup-2026-04-13-loser'
WHERE id IN (SELECT loser_id FROM dedup_plan)
  AND deleted_at IS NULL;

-- ── Verification ─────────────────────────────────────────────────────────────

SELECT 'losers_soft_deleted'  AS metric, COUNT(*) AS value
  FROM salem_pois WHERE data_source LIKE '%dedup-2026-04-13-loser%'
UNION ALL
SELECT 'survivors_with_narration_now',
       COUNT(*) FROM salem_pois s JOIN dedup_plan d ON s.id = d.survivor_id
       WHERE (s.short_narration IS NOT NULL OR s.long_narration IS NOT NULL)
UNION ALL
SELECT 'active_pois_remaining',
       COUNT(*) FROM salem_pois WHERE deleted_at IS NULL;

-- COMMIT or ROLLBACK manually after reviewing output.
