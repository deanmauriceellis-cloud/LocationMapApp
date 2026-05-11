-- S183 — salem_tour_legs (per-leg precomputed walking polylines).
--
-- One row per consecutive (stop_order n → n+1) pair on a tour. Authored
-- via the admin "Compute Route" tool, baked into salem_content.db so the
-- APK never has to route at runtime. Hand-editable per leg.
--
-- Apply:
--   psql "$DATABASE_URL" -f cache-proxy/scripts/2026-04-26-tour-legs.sql
--
-- Idempotent. Safe to re-run.

CREATE TABLE IF NOT EXISTS salem_tour_legs (
  tour_id        TEXT    NOT NULL REFERENCES salem_tours(id) ON DELETE CASCADE,
  leg_order      INTEGER NOT NULL,
  from_stop_id   BIGINT  NOT NULL REFERENCES salem_tour_stops(stop_id) ON DELETE CASCADE,
  to_stop_id     BIGINT  NOT NULL REFERENCES salem_tour_stops(stop_id) ON DELETE CASCADE,
  polyline_json  JSONB   NOT NULL,
  distance_m     REAL    NOT NULL,
  duration_s     REAL    NOT NULL,
  router_version TEXT,
  manual_edits   JSONB,
  computed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (tour_id, leg_order)
);

CREATE INDEX IF NOT EXISTS salem_tour_legs_from_idx
  ON salem_tour_legs (from_stop_id);
CREATE INDEX IF NOT EXISTS salem_tour_legs_to_idx
  ON salem_tour_legs (to_stop_id);
