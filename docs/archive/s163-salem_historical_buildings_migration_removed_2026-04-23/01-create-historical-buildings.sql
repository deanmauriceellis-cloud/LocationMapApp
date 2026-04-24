-- Historical Buildings table
-- Separate from salem_pois. Sourced from MassGIS MHC inventory (NHL + NRIND + LHD, named records).
-- Coexists with POIs at shared centroids; no FK relation.
-- Additive, idempotent — safe to re-run.
--
-- Narration is Android TTS at runtime: narration_text is the composed script.
-- Records without year_built get NULL narration_text and are silent.
--
-- Usage: DATABASE_URL=postgres://user:pass@localhost/locationmapapp \
--        psql "$DATABASE_URL" -f 01-create-historical-buildings.sql

CREATE TABLE IF NOT EXISTS salem_historical_buildings (
  id                TEXT PRIMARY KEY,                          -- hb_salem_<seq>

  -- Identity
  name              TEXT NOT NULL,                             -- raw MHC name, e.g. "Dix, Benjamin House"
  display_name      TEXT NOT NULL,                             -- normalized, e.g. "Benjamin Dix House"
  builder           TEXT,                                      -- parsed person name, NULL for institutional
  is_institutional  BOOLEAN NOT NULL DEFAULT FALSE,

  -- Facts
  year_built        INTEGER,                                   -- from L3 assessor join; NULL if no match
  address           TEXT,
  designation       TEXT NOT NULL,                             -- raw MHC code: 'NHL', 'LHD NRDIS', etc.

  -- Geometry
  centroid_lat      DOUBLE PRECISION NOT NULL,
  centroid_lon      DOUBLE PRECISION NOT NULL,
  footprint_geojson TEXT NOT NULL,                             -- serialized MultiPolygon for admin map

  -- Narration (Android TTS at runtime)
  narration_text    TEXT,                                      -- composed script; NULL when year_built IS NULL

  -- Provenance
  data_source       TEXT NOT NULL DEFAULT 'massgis_mhc',
  year_source       TEXT,                                      -- 'l3_assessor' | 'macris' | 'manual'
  confidence        REAL NOT NULL DEFAULT 0.9,
  verified_date     TIMESTAMPTZ,

  -- Editorial workflow (matches salem_witch_trials_newspapers pattern)
  admin_dirty       BOOLEAN NOT NULL DEFAULT FALSE,
  admin_dirty_at    TIMESTAMPTZ,

  -- Lifecycle
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at        TIMESTAMPTZ
);

-- Active-record index (soft-delete aware)
CREATE INDEX IF NOT EXISTS idx_shb_active
  ON salem_historical_buildings (id) WHERE deleted_at IS NULL;

-- Spatial bbox lookup for admin parcel-click (table is small, btrees on lat/lon are sufficient)
CREATE INDEX IF NOT EXISTS idx_shb_centroid
  ON salem_historical_buildings (centroid_lat, centroid_lon) WHERE deleted_at IS NULL;

-- Designation filter (lets admin tool filter by NHL / NRIND / LHD etc.)
CREATE INDEX IF NOT EXISTS idx_shb_designation
  ON salem_historical_buildings (designation) WHERE deleted_at IS NULL;

-- Editorial workflow
CREATE INDEX IF NOT EXISTS idx_shb_admin_dirty
  ON salem_historical_buildings (admin_dirty) WHERE admin_dirty = TRUE;

-- Narratable records (has TTS text, active)
CREATE INDEX IF NOT EXISTS idx_shb_narratable
  ON salem_historical_buildings (id)
  WHERE deleted_at IS NULL AND narration_text IS NOT NULL;

-- Convention: updated_at is set explicitly by callers in UPDATE statements
-- (matches other salem_* tables — no trigger-based auto-touch).
