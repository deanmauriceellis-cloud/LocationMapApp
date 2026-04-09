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
-- Salem Tour POIs — curated tour-worthy locations with narration
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_tour_pois (
  id                     TEXT PRIMARY KEY,
  name                   TEXT NOT NULL,
  lat                    DOUBLE PRECISION NOT NULL,
  lng                    DOUBLE PRECISION NOT NULL,
  address                TEXT NOT NULL,
  category               TEXT NOT NULL,
  subcategories          JSONB DEFAULT '[]',
  short_narration        TEXT,
  long_narration         TEXT,
  description            TEXT,
  historical_period      TEXT,
  admission_info         TEXT,
  hours                  TEXT,
  phone                  TEXT,
  website                TEXT,
  image_asset            TEXT,
  geofence_radius_m      INTEGER DEFAULT 50,
  requires_transportation BOOLEAN DEFAULT FALSE,
  wheelchair_accessible  BOOLEAN DEFAULT TRUE,
  seasonal               BOOLEAN DEFAULT FALSE,
  priority               INTEGER DEFAULT 3,
  -- Provenance & Staleness
  data_source            TEXT NOT NULL DEFAULT 'manual_curated',
  confidence             REAL NOT NULL DEFAULT 1.0,
  verified_date          TIMESTAMPTZ,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stale_after            TIMESTAMPTZ,
  deleted_at             TIMESTAMPTZ                        -- soft delete (NULL = active), Phase 9P.4
);

-- Phase 9P.4: backfill deleted_at on existing deployments
ALTER TABLE salem_tour_pois ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_salem_pois_category ON salem_tour_pois (category);
CREATE INDEX IF NOT EXISTS idx_salem_pois_lat_lng ON salem_tour_pois (lat, lng);
CREATE INDEX IF NOT EXISTS idx_salem_pois_data_source ON salem_tour_pois (data_source);
CREATE INDEX IF NOT EXISTS idx_salem_pois_stale ON salem_tour_pois (stale_after) WHERE stale_after IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_salem_pois_active ON salem_tour_pois (id) WHERE deleted_at IS NULL;

-- ════════════════════════════════════════════════════════════════════
-- Salem Businesses — restaurants, bars, shops, lodging, attractions
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_businesses (
  id                TEXT PRIMARY KEY,
  name              TEXT NOT NULL,
  lat               DOUBLE PRECISION NOT NULL,
  lng               DOUBLE PRECISION NOT NULL,
  address           TEXT NOT NULL,
  business_type     TEXT NOT NULL,
  cuisine_type      TEXT,
  price_range       TEXT,
  hours             TEXT,
  phone             TEXT,
  website           TEXT,
  description       TEXT,
  historical_note   TEXT,
  tags              JSONB DEFAULT '[]',
  rating            REAL,
  image_asset       TEXT,
  -- Provenance & Staleness
  data_source       TEXT NOT NULL DEFAULT 'manual_curated',
  confidence        REAL NOT NULL DEFAULT 1.0,
  verified_date     TIMESTAMPTZ,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stale_after       TIMESTAMPTZ,
  deleted_at        TIMESTAMPTZ                              -- soft delete (NULL = active), Phase 9P.4
);

-- Phase 9P.4: backfill deleted_at on existing deployments
ALTER TABLE salem_businesses ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_salem_biz_type ON salem_businesses (business_type);
CREATE INDEX IF NOT EXISTS idx_salem_biz_lat_lng ON salem_businesses (lat, lng);
CREATE INDEX IF NOT EXISTS idx_salem_biz_data_source ON salem_businesses (data_source);
CREATE INDEX IF NOT EXISTS idx_salem_biz_stale ON salem_businesses (stale_after) WHERE stale_after IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_salem_biz_active ON salem_businesses (id) WHERE deleted_at IS NULL;

-- ════════════════════════════════════════════════════════════════════
-- Salem Narration Points — ambient narration POIs (Phase 9P, Session 98)
-- Imported from tools/salem-data/narration-priority-pois.json and the
-- bundled narration_points table in salem-content/salem_content.sql.
-- PostgreSQL becomes the canonical source of truth going forward; the
-- JSON files retire to historical-artifact status after the migration.
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS salem_narration_points (
  id                 TEXT PRIMARY KEY,
  name               TEXT NOT NULL,
  lat                DOUBLE PRECISION NOT NULL,
  lng                DOUBLE PRECISION NOT NULL,
  address            TEXT,
  -- Bundled SQL uses 'type' for category; we use 'category' to match PoiCategories.kt
  -- and CMS conventions. Importer maps bundled.type → category.
  category           TEXT NOT NULL,
  subcategory        TEXT,
  -- Narration content
  -- Tier system (geofence trigger duration):
  --   short_narration = walk-by trigger (~50-100 words, 15-30s TTS) — also doubles as Pass 1
  --   long_narration  = linger trigger  (~200-400 words, 60-120s TTS)
  -- Multipass system (visit-count rotation, daily no-repeat — see project_multipass_narration memory):
  --   Pass 1 = short_narration (basic intro, no separate column)
  --   Pass 2 = narration_pass_2 (historical deep-dive)
  --   Pass 3 = narration_pass_3 (primary source quotes)
  -- Column names match the bundled DB / Room entity (NarrationPoint.kt) so the
  -- 9P.C publish loop is a 1:1 dump-and-load.
  short_narration    TEXT,
  long_narration     TEXT,
  description        TEXT,
  narration_pass_2   TEXT,
  narration_pass_3   TEXT,
  -- Geofence + tour metadata
  geofence_radius_m  INTEGER NOT NULL DEFAULT 40,
  geofence_shape     TEXT NOT NULL DEFAULT 'circle',  -- 'circle' | 'corridor'
  corridor_points    TEXT,                              -- serialized polyline for corridor shape
  priority           INTEGER NOT NULL DEFAULT 3,
  wave               INTEGER,
  -- Contact metadata
  phone              TEXT,
  website            TEXT,
  hours              TEXT,
  -- Media assets (default — merchant overrides below take precedence at runtime)
  image_asset        TEXT,
  voice_clip_asset   TEXT,
  -- Cross-references to historical content (populated by Phase 9Q building→POI bridge backfill)
  related_figure_ids JSONB DEFAULT '[]',
  related_fact_ids   JSONB DEFAULT '[]',
  related_source_ids JSONB DEFAULT '[]',
  -- Action button JSON config (Visit / Directions / More Info / etc.)
  action_buttons     JSONB DEFAULT '[]',
  -- Merchant advertising fields (foundation for Phase 17; populated/edited later)
  merchant_tier      INTEGER NOT NULL DEFAULT 0,       -- 0=none, 1=basic, 2=premium, 3=featured
  ad_priority        INTEGER NOT NULL DEFAULT 0,       -- queue priority boost for paying merchants
  custom_icon_asset  TEXT,                              -- merchant-supplied icon override
  custom_voice_asset TEXT,                              -- merchant-supplied voice clip override
  custom_description TEXT,                              -- merchant-supplied description override
  -- Source metadata (where this POI came from)
  source_id          TEXT,
  source_categories  JSONB DEFAULT '[]',
  tags               JSONB DEFAULT '[]',
  -- Provenance & Staleness (matches existing salem_* table pattern)
  data_source        TEXT NOT NULL DEFAULT 'overpass_import',
  confidence         REAL NOT NULL DEFAULT 0.8,
  verified_date      TIMESTAMPTZ,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  stale_after        TIMESTAMPTZ,
  deleted_at         TIMESTAMPTZ                        -- soft delete (NULL = active)
);

CREATE INDEX IF NOT EXISTS idx_salem_narration_category ON salem_narration_points (category);
CREATE INDEX IF NOT EXISTS idx_salem_narration_lat_lng ON salem_narration_points (lat, lng);
CREATE INDEX IF NOT EXISTS idx_salem_narration_data_source ON salem_narration_points (data_source);
CREATE INDEX IF NOT EXISTS idx_salem_narration_wave ON salem_narration_points (wave) WHERE wave IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_salem_narration_active ON salem_narration_points (id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_salem_narration_stale ON salem_narration_points (stale_after) WHERE stale_after IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_salem_narration_merchant_tier ON salem_narration_points (merchant_tier) WHERE merchant_tier > 0;

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
  primary_poi_id         TEXT REFERENCES salem_tour_pois(id),
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
  poi_id           TEXT REFERENCES salem_tour_pois(id),
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
  poi_id           TEXT REFERENCES salem_tour_pois(id),
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
  poi_id           TEXT REFERENCES salem_tour_pois(id),
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
  poi_id                  TEXT NOT NULL REFERENCES salem_tour_pois(id),
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
  venue_poi_id       TEXT REFERENCES salem_tour_pois(id),
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
