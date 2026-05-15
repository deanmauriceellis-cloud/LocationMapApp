-- S268 — POI Passport: operator-tunable filters that generate per-user lists
-- of POIs the user accumulates "stamps" against by hearing the narration.
--
-- Replaces the four leftover stops-based tour UIs (HUD banner, Active Tour
-- dialog, completion-stats body, numbered polyline pins). User's actual
-- visit log lives in user_data.db (Room v3) and is POI-keyed only — these
-- PG tables only hold the canonical lists, not per-user state.
--
-- Apply:
--   psql "$DATABASE_URL" -f docs/archive/scripts/migrations-2026-05/2026-05-15-poi-passport.sql
--
-- Idempotent. Safe to re-run.

CREATE TABLE IF NOT EXISTS salem_passport_filters (
  id                            TEXT PRIMARY KEY,
  name                          TEXT NOT NULL,
  tour_id                       TEXT REFERENCES salem_tours(id) ON DELETE CASCADE,
  categories                    TEXT[] NOT NULL DEFAULT '{}',
  require_historical_narration  BOOLEAN NOT NULL DEFAULT TRUE,
  min_geofence_radius_m         INTEGER NOT NULL DEFAULT 0,
  min_year_established          INTEGER,
  max_year_established          INTEGER,
  sort_order                    INTEGER NOT NULL DEFAULT 0,
  created_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at                    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_salem_passport_filters_tour
  ON salem_passport_filters (tour_id) WHERE tour_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS salem_passport_pois (
  filter_id      TEXT NOT NULL REFERENCES salem_passport_filters(id) ON DELETE CASCADE,
  poi_id         TEXT NOT NULL REFERENCES salem_pois(id) ON DELETE CASCADE,
  display_order  INTEGER NOT NULL,
  PRIMARY KEY (filter_id, poi_id)
);

CREATE INDEX IF NOT EXISTS idx_salem_passport_pois_filter
  ON salem_passport_pois (filter_id, display_order);
CREATE INDEX IF NOT EXISTS idx_salem_passport_pois_poi
  ON salem_passport_pois (poi_id);
