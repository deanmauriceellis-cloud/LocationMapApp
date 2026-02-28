-- POI permanent storage schema for LocationMapApp
-- Usage: psql -U postgres -d locationmapapp -f schema.sql

CREATE TABLE IF NOT EXISTS pois (
  osm_type   TEXT NOT NULL,
  osm_id     BIGINT NOT NULL,
  lat        DOUBLE PRECISION,
  lon        DOUBLE PRECISION,
  name       TEXT,
  category   TEXT,
  tags       JSONB NOT NULL DEFAULT '{}',
  first_seen TIMESTAMPTZ NOT NULL,
  last_seen  TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (osm_type, osm_id)
);

CREATE INDEX IF NOT EXISTS idx_pois_category ON pois (category);
CREATE INDEX IF NOT EXISTS idx_pois_name ON pois (name) WHERE name IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_pois_tags ON pois USING GIN (tags);
