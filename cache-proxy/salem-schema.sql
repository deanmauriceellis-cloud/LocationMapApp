/*
 * WickedSalemWitchCityTour — Salem Content Schema
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Extends the LocationMapApp PostgreSQL database with Salem-specific
 * content tables. Backward compatible — no changes to existing tables.
 *
 * Usage: psql -U postgres -d locationmapapp -f salem-schema.sql
 */

-- ════════════════════════════════════════════════════════════════════
-- Provenance extension for existing pois table (backward compatible)
-- ════════════════════════════════════════════════════════════════════

ALTER TABLE pois ADD COLUMN IF NOT EXISTS data_source TEXT DEFAULT 'overpass_import';
ALTER TABLE pois ADD COLUMN IF NOT EXISTS confidence REAL DEFAULT 0.8;
ALTER TABLE pois ADD COLUMN IF NOT EXISTS verified_date TIMESTAMPTZ;
ALTER TABLE pois ADD COLUMN IF NOT EXISTS stale_after TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_pois_data_source ON pois (data_source);
CREATE INDEX IF NOT EXISTS idx_pois_stale_after ON pois (stale_after) WHERE stale_after IS NOT NULL;

-- ════════════════════════════════════════════════════════════════════
-- Legacy POI tables — REMOVED in Phase 9U (Session 126, 2026-04-15)
-- ════════════════════════════════════════════════════════════════════
-- The three pre-unification tables — salem_tour_pois, salem_businesses,
-- salem_narration_points — were merged into salem_pois in Phase 9U
-- (Session 117, see below) and dropped from PG. Their CREATE statements
-- and indexes have been removed from this file. The old importer
-- scripts (cache-proxy/scripts/import-narration-points.js,
-- import-tour-pois-and-businesses.js, migrate-to-unified-pois.js) are
-- preserved in git history; do not re-run them.
-- ════════════════════════════════════════════════════════════════════

-- ════════════════════════════════════════════════════════════════════
-- Historical Figures
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_historical_figures (
  id                     TEXT PRIMARY KEY,
  name                   TEXT NOT NULL,
  first_name             TEXT NOT NULL,
  surname                TEXT NOT NULL,
  born                   TEXT,
  died                   TEXT,
  age_in_1692            INTEGER,
  role                   TEXT NOT NULL,
  faction                TEXT,
  short_bio              TEXT NOT NULL,
  full_bio               TEXT,
  narration_script       TEXT,
  appearance_description TEXT,
  role_in_crisis         TEXT,
  historical_outcome     TEXT,
  key_quotes             JSONB DEFAULT '[]',
  family_connections     JSONB DEFAULT '{}',
  primary_poi_id         TEXT REFERENCES salem_pois(id),
  -- Provenance & Staleness
  data_source            TEXT NOT NULL DEFAULT 'salem_project',
  confidence             REAL NOT NULL DEFAULT 1.0,
  verified_date          TIMESTAMPTZ,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stale_after            TIMESTAMPTZ
);

-- ════════════════════════════════════════════════════════════════════
-- Historical Facts
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_historical_facts (
  id               TEXT PRIMARY KEY,
  title            TEXT NOT NULL,
  description      TEXT NOT NULL,
  date             TEXT,
  date_precision   TEXT,
  category         TEXT,
  subcategory      TEXT,
  poi_id           TEXT REFERENCES salem_pois(id),
  figure_id        TEXT REFERENCES salem_historical_figures(id),
  source_citation  TEXT,
  narration_script TEXT,
  confidentiality  TEXT DEFAULT 'public',
  tags             JSONB DEFAULT '[]',
  -- Provenance & Staleness
  data_source      TEXT NOT NULL DEFAULT 'salem_project',
  confidence       REAL NOT NULL DEFAULT 1.0,
  verified_date    TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stale_after      TIMESTAMPTZ
);

-- ════════════════════════════════════════════════════════════════════
-- Timeline Events
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_timeline_events (
  id               TEXT PRIMARY KEY,
  name             TEXT NOT NULL,
  date             TEXT NOT NULL,
  crisis_phase     TEXT,
  description      TEXT NOT NULL,
  poi_id           TEXT REFERENCES salem_pois(id),
  figures_involved JSONB DEFAULT '[]',
  narration_script TEXT,
  is_anchor        BOOLEAN DEFAULT FALSE,
  -- Provenance & Staleness
  data_source      TEXT NOT NULL DEFAULT 'salem_project',
  confidence       REAL NOT NULL DEFAULT 1.0,
  verified_date    TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stale_after      TIMESTAMPTZ
);

-- ════════════════════════════════════════════════════════════════════
-- Primary Sources
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_primary_sources (
  id               TEXT PRIMARY KEY,
  title            TEXT NOT NULL,
  source_type      TEXT NOT NULL,
  author           TEXT,
  date             TEXT,
  full_text        TEXT,
  excerpt          TEXT,
  figure_id        TEXT REFERENCES salem_historical_figures(id),
  poi_id           TEXT REFERENCES salem_pois(id),
  narration_script TEXT,
  citation         TEXT,
  -- Provenance & Staleness
  data_source      TEXT NOT NULL DEFAULT 'salem_project',
  confidence       REAL NOT NULL DEFAULT 1.0,
  verified_date    TIMESTAMPTZ,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stale_after      TIMESTAMPTZ
);

-- ════════════════════════════════════════════════════════════════════
-- Tours
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_tours (
  id                TEXT PRIMARY KEY,
  name              TEXT NOT NULL,
  theme             TEXT NOT NULL,
  description       TEXT NOT NULL,
  estimated_minutes INTEGER NOT NULL,
  distance_km       REAL NOT NULL,
  stop_count        INTEGER NOT NULL,
  difficulty        TEXT DEFAULT 'moderate',
  seasonal          BOOLEAN DEFAULT FALSE,
  icon_asset        TEXT,
  sort_order        INTEGER DEFAULT 0,
  -- Provenance & Staleness
  data_source       TEXT NOT NULL DEFAULT 'manual_curated',
  confidence        REAL NOT NULL DEFAULT 1.0,
  verified_date     TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stale_after       TIMESTAMPTZ
);

-- ════════════════════════════════════════════════════════════════════
-- Tour Stops (join table)
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_tour_stops (
  tour_id                 TEXT NOT NULL REFERENCES salem_tours(id),
  poi_id                  TEXT NOT NULL REFERENCES salem_pois(id),
  stop_order              INTEGER NOT NULL,
  transition_narration    TEXT,
  walking_minutes_from_prev INTEGER,
  distance_m_from_prev    INTEGER,
  -- Provenance & Staleness
  data_source             TEXT NOT NULL DEFAULT 'manual_curated',
  confidence              REAL NOT NULL DEFAULT 1.0,
  created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stale_after             TIMESTAMPTZ,
  PRIMARY KEY (tour_id, poi_id)
);

-- ════════════════════════════════════════════════════════════════════
-- Events Calendar
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_events_calendar (
  id                 TEXT PRIMARY KEY,
  name               TEXT NOT NULL,
  venue_poi_id       TEXT REFERENCES salem_pois(id),
  event_type         TEXT NOT NULL,
  description        TEXT,
  start_date         TEXT,
  end_date           TEXT,
  hours              TEXT,
  admission          TEXT,
  website            TEXT,
  recurring          BOOLEAN DEFAULT FALSE,
  recurrence_pattern TEXT,
  seasonal_month     INTEGER,
  -- Provenance & Staleness
  data_source        TEXT NOT NULL DEFAULT 'manual_curated',
  confidence         REAL NOT NULL DEFAULT 1.0,
  verified_date      TIMESTAMPTZ,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stale_after        TIMESTAMPTZ
);

-- ════════════════════════════════════════════════════════════════════
-- Content Sync Tracking — tracks what the app has synced from the server
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_sync_state (
  table_name   TEXT PRIMARY KEY,
  last_sync_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  row_count    INTEGER DEFAULT 0,
  checksum     TEXT
);

-- ════════════════════════════════════════════════════════════════════
-- POI Taxonomy (Phase 9P.B+, Session 114)
-- ════════════════════════════════════════════════════════════════════
--
-- Two lookup tables mirroring the canonical POI taxonomy defined in
--   app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/menu/PoiCategories.kt
--   web/src/config/poiCategories.ts              (line-aligned mirror)
--
-- 22 categories x ~176 subcategories (exact count comes from the seed
-- script `cache-proxy/scripts/sync-poi-taxonomy.js` — rerun that script
-- whenever poiCategories.ts changes).
--
-- Subcategory PK format: `{CATEGORY_ID}__{slug}` — e.g. `FOOD_DRINK__restaurants`.
-- This lets POI tables FK directly to the subcategory row without needing
-- a separate category_id column on the POI side.
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_poi_categories (
  id                     TEXT PRIMARY KEY,            -- e.g. 'FOOD_DRINK' — matches PoiLayerId
  label                  TEXT NOT NULL,               -- e.g. 'Food & Drink'
  pref_key               TEXT NOT NULL,               -- e.g. 'poi_food_drink_on'
  tags                   JSONB NOT NULL DEFAULT '[]', -- flat OSM tag list, key=value form
  color                  TEXT NOT NULL,               -- e.g. '#BF360C'
  default_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
  historic_tour_default  BOOLEAN NOT NULL DEFAULT FALSE,
  display_order          INTEGER NOT NULL,            -- 1..N from POI_CATEGORIES array order
  source                 TEXT NOT NULL DEFAULT 'poiCategories.ts',
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS salem_poi_subcategories (
  id                     TEXT PRIMARY KEY,            -- e.g. 'FOOD_DRINK__restaurants'
  category_id            TEXT NOT NULL REFERENCES salem_poi_categories(id) ON DELETE RESTRICT,
  label                  TEXT NOT NULL,               -- e.g. 'Restaurants'
  slug                   TEXT NOT NULL,               -- e.g. 'restaurants' (unique within category)
  tags                   JSONB NOT NULL DEFAULT '[]', -- per-subtype OSM tag list
  display_order          INTEGER NOT NULL,            -- 1..N from subtypes array order
  source                 TEXT NOT NULL DEFAULT 'poiCategories.ts',
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (category_id, slug)
);

CREATE INDEX IF NOT EXISTS idx_salem_poi_subcat_category ON salem_poi_subcategories (category_id);

-- ════════════════════════════════════════════════════════════════════
-- Unified Salem POIs — Phase 9U (Session 117)
--
-- Single canonical table for every Salem POI — restaurant, witch shop,
-- dentist, park, historic house. Merged from the three pre-unification
-- tables (salem_tour_pois 45 + salem_businesses 861 + salem_narration_points
-- 817) which were dropped in S126. Superset schema + SalemIntelligence
-- BCS enrichment columns from the S118 import.
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_pois (
  -- ── Core identity ───────────────────────────────────────────────
  id                     TEXT PRIMARY KEY,
  name                   TEXT NOT NULL,
  lat                    DOUBLE PRECISION NOT NULL,
  lng                    DOUBLE PRECISION NOT NULL,
  address                TEXT,
  status                 TEXT DEFAULT 'open',           -- open / temporarily_closed / seasonal / unknown

  -- ── Taxonomy (FK-enforced) ──────────────────────────────────────
  category               TEXT REFERENCES salem_poi_categories(id),
  subcategory            TEXT REFERENCES salem_poi_subcategories(id),

  -- ── Narration layer (null for non-narrated POIs) ────────────────
  short_narration        TEXT,
  long_narration         TEXT,
  geofence_radius_m      INTEGER DEFAULT 40,
  geofence_shape         TEXT DEFAULT 'circle',         -- 'circle' | 'corridor'
  corridor_points        TEXT,                           -- serialized polyline for corridor shape
  priority               INTEGER DEFAULT 3,
  wave                   INTEGER,
  voice_clip_asset       TEXT,
  custom_voice_asset     TEXT,

  -- ── Business layer (null for parks/public art/etc) ──────────────
  cuisine_type           TEXT,
  price_range            TEXT,
  rating                 REAL,
  merchant_tier          INTEGER DEFAULT 0,              -- 0=none, 1=basic, 2=premium, 3=featured
  ad_priority            INTEGER DEFAULT 0,

  -- ── Historical/tour layer (null for non-historic POIs) ──────────
  historical_period      TEXT,
  historical_note        TEXT,
  admission_info         TEXT,
  requires_transportation BOOLEAN DEFAULT false,
  wheelchair_accessible  BOOLEAN DEFAULT true,
  seasonal               BOOLEAN DEFAULT false,

  -- ── Contact/hours ───────────────────────────────────────────────
  phone                  TEXT,
  email                  TEXT,
  website                TEXT,
  hours                  JSONB,                          -- structured JSON (upgrade from text)
  hours_text             TEXT,                            -- preserve legacy freeform hours strings
  menu_url               TEXT,
  reservations_url       TEXT,
  order_url              TEXT,

  -- ── Content ─────────────────────────────────────────────────────
  description            TEXT,
  short_description      TEXT,
  custom_description     TEXT,
  origin_story           TEXT,
  image_asset            TEXT,
  custom_icon_asset      TEXT,
  action_buttons         JSONB DEFAULT '[]',

  -- ── SalemIntelligence enrichment ────────────────────────────────
  intel_entity_id        TEXT,                            -- BCS entity_id FK for v2 online features
  secondary_categories   JSONB DEFAULT '[]',
  specialties            JSONB DEFAULT '[]',
  owners                 JSONB DEFAULT '[]',
  year_established       INTEGER,
  amenities              JSONB DEFAULT '{}',
  district               TEXT,

  -- ── Relations ───────────────────────────────────────────────────
  related_figure_ids     JSONB DEFAULT '[]',
  related_fact_ids       JSONB DEFAULT '[]',
  related_source_ids     JSONB DEFAULT '[]',
  source_id              TEXT,
  source_categories      JSONB DEFAULT '[]',
  tags                   JSONB DEFAULT '[]',

  -- ── Provenance ──────────────────────────────────────────────────
  data_source            TEXT NOT NULL DEFAULT 'unified_migration',
  confidence             REAL NOT NULL DEFAULT 0.8,
  verified_date          TIMESTAMPTZ,
  stale_after            TIMESTAMPTZ,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at             TIMESTAMPTZ,                    -- soft delete (NULL = active)

  -- ── Flags ───────────────────────────────────────────────────────
  is_tour_poi            BOOLEAN DEFAULT false,          -- the 45 curated 1692 stops
  is_narrated            BOOLEAN DEFAULT false,          -- has narration content
  default_visible        BOOLEAN DEFAULT true,           -- per-POI visibility override

  -- ── Legacy audit (dropped after Phase 9U verification) ──────────
  legacy_narration_category TEXT,
  legacy_business_type   TEXT,
  legacy_tour_category   TEXT
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_spois_category        ON salem_pois (category);
CREATE INDEX IF NOT EXISTS idx_spois_lat_lng         ON salem_pois (lat, lng);
CREATE INDEX IF NOT EXISTS idx_spois_data_source     ON salem_pois (data_source);
CREATE INDEX IF NOT EXISTS idx_spois_active          ON salem_pois (id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_spois_merchant_tier   ON salem_pois (merchant_tier) WHERE merchant_tier > 0;
CREATE INDEX IF NOT EXISTS idx_spois_stale           ON salem_pois (stale_after) WHERE stale_after IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_spois_wave            ON salem_pois (wave) WHERE wave IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_spois_intel_entity    ON salem_pois (intel_entity_id) WHERE intel_entity_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_spois_tour            ON salem_pois (id) WHERE is_tour_poi = true;
CREATE INDEX IF NOT EXISTS idx_spois_district        ON salem_pois (district) WHERE district IS NOT NULL;
