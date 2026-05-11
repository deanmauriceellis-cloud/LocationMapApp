-- Adds proposed-location columns to salem_pois for the TigerLine/MassGIS verification workflow.
-- Additive only: existing lat/lng remain the live coords until a reviewer accepts a proposal.
-- Idempotent — safe to re-run.
--
-- location_status enum (enforced in app code, not DB):
--   unverified   — never run through the verifier
--   no_address   — POI has no usable address; can't geocode
--   no_match     — geocoder returned no result
--   needs_review — geocoded successfully but drift > threshold
--   verified     — geocoded, drift within threshold, current coords trusted
--   accepted     — reviewer accepted the proposed coords (overwrote lat/lng)
--
-- location_source enum: 'tigerline' | 'massgis_site_addr' | 'osm_addr' | 'manual'
-- location_truth_of_record: when true, verifier skips this POI permanently.

ALTER TABLE salem_pois
  ADD COLUMN IF NOT EXISTS lat_proposed             double precision,
  ADD COLUMN IF NOT EXISTS lng_proposed             double precision,
  ADD COLUMN IF NOT EXISTS location_source          text,
  ADD COLUMN IF NOT EXISTS location_status          text NOT NULL DEFAULT 'unverified',
  ADD COLUMN IF NOT EXISTS location_truth_of_record boolean NOT NULL DEFAULT false,
  ADD COLUMN IF NOT EXISTS location_drift_m         real,
  ADD COLUMN IF NOT EXISTS location_geocoder_rating integer,
  ADD COLUMN IF NOT EXISTS location_verified_at     timestamp with time zone;

CREATE INDEX IF NOT EXISTS idx_spois_location_status
  ON salem_pois (location_status) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_spois_location_truth
  ON salem_pois (id) WHERE location_truth_of_record = true;

CREATE INDEX IF NOT EXISTS idx_spois_needs_review
  ON salem_pois (id) WHERE location_status = 'needs_review' AND deleted_at IS NULL;

-- Backfill: rows with no usable address can't be geocoded, mark them up front.
UPDATE salem_pois
SET location_status = 'no_address'
WHERE (address IS NULL OR address = '')
  AND deleted_at IS NULL
  AND location_status = 'unverified';
