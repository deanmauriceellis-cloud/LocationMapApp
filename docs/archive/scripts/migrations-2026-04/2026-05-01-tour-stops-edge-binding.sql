-- S214 — bind tour waypoints to TigerLine edges at placement.
--
-- New columns persist the snap result returned by salem-router's
-- nearestWalkableEdge() so the admin tool, the routing pipeline, and any
-- future device-side renderer all agree on which routable edge a free
-- waypoint sits on.
--
--   edge_id        — TigerLine edge id (matches edges.id in the routing
--                    bundle SQLite). Nullable: POI-based stops don't need
--                    a binding (POI lat/lng is authoritative for them) and
--                    legacy free waypoints stay null until backfilled.
--   edge_fraction  — position along the edge as a fraction 0..1 of total
--                    polyline length. Lets the routing layer eventually
--                    split the edge for true mid-block routing (Phase 2).
--
-- No data is mutated. Backfill of existing free waypoints lives in
-- cache-proxy/scripts/2026-05-01-snap-existing-waypoints.js.

BEGIN;

ALTER TABLE salem_tour_stops
  ADD COLUMN IF NOT EXISTS edge_id BIGINT,
  ADD COLUMN IF NOT EXISTS edge_fraction REAL;

COMMENT ON COLUMN salem_tour_stops.edge_id IS
  'S214: TigerLine edge id this free waypoint snaps to. Null for POI-based stops or unsnapped legacy rows.';
COMMENT ON COLUMN salem_tour_stops.edge_fraction IS
  'S214: Fraction (0..1) along the bound edge polyline. Position = edge.start + fraction * (edge.end - edge.start) along cumulative segment lengths.';

COMMIT;
