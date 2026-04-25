-- S174 — make salem_tour_stops editable for the web admin tour editor.
--
-- Changes:
--   * Add stop_id BIGSERIAL surrogate PK (replaces composite tour_id+poi_id PK
--     so we can have stable IDs and allow the same POI to appear twice).
--   * Add nullable lat/lng — per-tour waypoint override of POI coords.
--     Effective coord at read time = COALESCE(stop.lat, poi.lat).
--   * Add nullable name — label override for free-floating waypoints later.
--   * Make poi_id NULL-able — same forward-compat for free waypoints.
--
-- Existing 5 tours / 87 stops are preserved as-is (lat/lng NULL = fall through
-- to POI coords, so client behavior is unchanged until someone drags a stop).

BEGIN;

ALTER TABLE salem_tour_stops DROP CONSTRAINT IF EXISTS salem_tour_stops_pkey;

ALTER TABLE salem_tour_stops
  ADD COLUMN IF NOT EXISTS stop_id BIGSERIAL,
  ADD COLUMN IF NOT EXISTS lat DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS lng DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS name TEXT;

ALTER TABLE salem_tour_stops ALTER COLUMN poi_id DROP NOT NULL;

ALTER TABLE salem_tour_stops ADD PRIMARY KEY (stop_id);

CREATE INDEX IF NOT EXISTS salem_tour_stops_tour_order_idx
  ON salem_tour_stops (tour_id, stop_order);

COMMIT;
