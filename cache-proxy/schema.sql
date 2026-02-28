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
CREATE INDEX IF NOT EXISTS idx_pois_lat_lon ON pois (lat, lon);

-- Aircraft sightings — each continuous observation is a separate row
CREATE TABLE IF NOT EXISTS aircraft_sightings (
  id              SERIAL PRIMARY KEY,
  icao24          TEXT NOT NULL,
  callsign        TEXT,
  origin_country  TEXT,
  first_seen      TIMESTAMPTZ NOT NULL,
  last_seen       TIMESTAMPTZ NOT NULL,
  first_lat       DOUBLE PRECISION,
  first_lon       DOUBLE PRECISION,
  first_altitude  DOUBLE PRECISION,   -- meters (baro)
  first_heading   DOUBLE PRECISION,
  last_lat        DOUBLE PRECISION,
  last_lon        DOUBLE PRECISION,
  last_altitude   DOUBLE PRECISION,
  last_heading    DOUBLE PRECISION,
  last_velocity   DOUBLE PRECISION,   -- m/s
  last_vertical_rate DOUBLE PRECISION,
  squawk          TEXT,
  on_ground       BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_aircraft_icao24 ON aircraft_sightings (icao24);
CREATE INDEX IF NOT EXISTS idx_aircraft_callsign ON aircraft_sightings (callsign) WHERE callsign IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_aircraft_first_seen ON aircraft_sightings (first_seen);
CREATE INDEX IF NOT EXISTS idx_aircraft_last_seen ON aircraft_sightings (last_seen);
CREATE INDEX IF NOT EXISTS idx_aircraft_last_lat_lon ON aircraft_sightings (last_lat, last_lon);
