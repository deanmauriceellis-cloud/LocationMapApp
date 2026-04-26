-- ingest-osm-pedestrian-into-salem-edges.sql — S178 (2026-04-25, surgical rewrite 2026-04-26)
--
-- Surgically integrate a hand-picked allowlist of OSM pedestrian polylines
-- into the salem.edges + salem.edges_vertices_pgr routing graph alongside
-- TIGER. Only the features required for tour POIs that TIGER doesn't reach.
--
-- An earlier broad-sweep ingest (2,071 OSM features) produced 608 connected
-- components because most OSM endpoints didn't sit close enough to a TIGER
-- node to hook up — they formed isolated islands. Operator response: "I am
-- trying to get away from OSM, just use what we must have." See memory
-- feedback_v1_no_external_contact.md and feedback_internal_db_single_source.md
-- for the architectural posture; this surgical ingest is a stopgap until a
-- proper internal pedestrian dataset is available.
--
-- Source: osm.salem_pedestrian_raw, populated from
-- tools/tile-bake/data/sources/massachusetts.osm.pbf (build-time, no runtime
-- network call). Each OSM feature is hand-verified to have at least one
-- endpoint within ~30m of a TIGER walkable node so the resulting edge is
-- reachable from the road network.
--
-- ID space: TIGER node IDs go up to ~10⁹. New OSM-derived vertex IDs are
-- offset by 10_000_000_000 (10 billion). Edge gids use 10_000_000 + osm_fid.

\set ON_ERROR_STOP on

BEGIN;

-- 0. Idempotent re-runs: clear any prior OSMP rows + their orphaned vertices.
DELETE FROM salem.edges WHERE mtfcc = 'OSMP';
DELETE FROM salem.edges_vertices_pgr v
WHERE v.id >= 10000000000
  AND NOT EXISTS (SELECT 1 FROM salem.edges e WHERE e.source = v.id OR e.target = v.id);

-- 1. The allowlist. Each row is one OSM polyline that the routing graph needs.
-- Curated 2026-04-26 to cover the three downtown tour POIs that TIGER
-- doesn't reach (Derby Wharf Light Station, Salem Maritime NHP visitor
-- area, House of the Seven Gables). Add new entries here only with a clear
-- reason and verified TIGER hookup.
DROP TABLE IF EXISTS osm.salem_allowlist;
CREATE TABLE osm.salem_allowlist (
  osm_id     TEXT PRIMARY KEY,
  reason     TEXT NOT NULL,
  fullname   TEXT
);
INSERT INTO osm.salem_allowlist (osm_id, reason, fullname) VALUES
  ('190138297',  'Derby Wharf — covers Derby Wharf Light Station + Salem Maritime NHP', 'Derby Wharf'),
  ('1028206775', 'Seven Gables waterfront path — covers House of the Seven Gables',     'Seven Gables Path'),
  ('1028206777', 'Seven Gables waterfront path (parallel) — covers House of the Seven Gables', 'Seven Gables Path'),
  -- Charter Street Cemetery interior paths (S178 — covers charter_street_cemetery + witch_trials_memorial + the_burying_point)
  -- One endpoint of 136214484 hooks to TIGER at 3.4m (Charter St entrance); the rest cluster within the cemetery.
  ('759628022',  'Charter Cemetery interior path — covers Charter Street Cemetery / Witch Trials Memorial', 'Charter Cemetery Path'),
  ('1258072451', 'Charter Cemetery interior path',                                              'Charter Cemetery Path'),
  ('136214484',  'Charter Cemetery entrance walk — TIGER hookup at Charter St',                 'Charter Cemetery Walk'),
  ('759628025',  'Charter Cemetery interior path',                                              'Charter Cemetery Path'),
  ('759628024',  'Charter Cemetery interior path',                                              'Charter Cemetery Path'),
  ('1258072452', 'Charter Cemetery interior path',                                              'Charter Cemetery Path'),
  ('759628029',  'Charter Cemetery interior path',                                              'Charter Cemetery Path'),
  -- Salem Willows park footway — covers salem_willows POI; both endpoints hook to TIGER nodes (5.8m + 13.9m)
  ('1080185559', 'Salem Willows footway — covers Salem Willows POI',                            'Salem Willows Path');

-- 2. Pull the picked features, reproject to 4269, drop self-loops.
DROP TABLE IF EXISTS osm.salem_picked;
CREATE TABLE osm.salem_picked AS
SELECT
  r.ogc_fid AS osm_fid,
  r.osm_id,
  COALESCE(a.fullname, '') AS fullname,
  ST_LineMerge(ST_Transform(r.geom, 4269))::geometry(LineString, 4269) AS geom
FROM osm.salem_pedestrian_raw r
JOIN osm.salem_allowlist a ON a.osm_id = r.osm_id
WHERE ST_GeometryType(ST_LineMerge(ST_Transform(r.geom, 4269))) = 'ST_LineString';

DELETE FROM osm.salem_picked
WHERE ST_StartPoint(geom) = ST_EndPoint(geom)
   OR ST_Length(geom::geography) < 0.5;

\echo Picked features:
SELECT osm_id, fullname, ROUND(ST_Length(geom::geography)::numeric, 1) AS len_m FROM osm.salem_picked;

-- 3. Resolve each endpoint to a node ID. For each feature's start and end:
--   a) If a TIGER walkable node is within 30m, use that node id (this is
--      what hooks the wharf into Derby Street's network).
--   b) Otherwise allocate a new OSM-offset node id at 10_000_000_000+osm_fid*2
--      (start) or +osm_fid*2+1 (end). This is the lighthouse-end case for
--      Derby Wharf — that endpoint is the route's destination, intentionally
--      disconnected from the road network.
DROP TABLE IF EXISTS osm.salem_picked_endpoints;
CREATE TABLE osm.salem_picked_endpoints AS
SELECT osm_fid, 'start'::text AS which, ST_StartPoint(geom) AS pt FROM osm.salem_picked
UNION ALL
SELECT osm_fid, 'end',   ST_EndPoint(geom)   FROM osm.salem_picked;

DROP TABLE IF EXISTS osm.salem_picked_resolution;
CREATE TABLE osm.salem_picked_resolution AS
SELECT
  ep.osm_fid,
  ep.which,
  ep.pt,
  n.tiger_node_id,
  CASE
    WHEN n.tiger_node_id IS NOT NULL THEN n.tiger_node_id
    WHEN ep.which = 'start' THEN 10000000000::bigint + ep.osm_fid * 2
    ELSE                         10000000000::bigint + ep.osm_fid * 2 + 1
  END AS final_node_id
FROM osm.salem_picked_endpoints ep
LEFT JOIN LATERAL (
  -- 30m tolerance — generous because OSM and TIGER coordinate systems can
  -- disagree by 10-25m at the same physical intersection. Verified per-
  -- feature in the allowlist comments.
  SELECT v.id AS tiger_node_id
  FROM salem.edges_vertices_pgr v
  WHERE v.walkable_node = TRUE
    AND v.the_geom && ST_Expand(ep.pt, 0.0004)
    AND ST_Distance(v.the_geom::geography, ep.pt::geography) <= 30
  ORDER BY v.the_geom <-> ep.pt
  LIMIT 1
) n ON TRUE;

\echo Endpoint resolution (T = matched TIGER node, * = new OSM-offset node):
SELECT
  r.osm_id,
  res.which,
  res.tiger_node_id IS NOT NULL AS hooked_to_tiger,
  res.final_node_id
FROM osm.salem_picked_resolution res
JOIN osm.salem_picked p ON p.osm_fid = res.osm_fid
JOIN osm.salem_pedestrian_raw r ON r.ogc_fid = p.osm_fid
ORDER BY r.osm_id, res.which;

-- 4. Insert any new OSM-offset nodes.
INSERT INTO salem.edges_vertices_pgr (id, the_geom, walkable_node)
SELECT DISTINCT ON (final_node_id) final_node_id, pt, TRUE
FROM osm.salem_picked_resolution
WHERE tiger_node_id IS NULL
ON CONFLICT (id) DO NOTHING;

-- 5. Insert the OSM edges into salem.edges with mtfcc='OSMP'.
INSERT INTO salem.edges (
  gid, statefp, countyfp, tlid, mtfcc, fullname,
  the_geom, source, target, length_m, walk_cost, walkable, reverse_cost
)
SELECT
  10000000 + p.osm_fid AS gid,
  '25', '009',
  CAST(p.osm_id AS bigint),
  'OSMP',
  p.fullname,
  ST_Multi(p.geom)::geometry(MultiLineString, 4269),
  s.final_node_id,
  e.final_node_id,
  ST_Length(p.geom::geography),
  ST_Length(p.geom::geography),
  TRUE,
  ST_Length(p.geom::geography)
FROM osm.salem_picked p
JOIN osm.salem_picked_resolution s ON s.osm_fid = p.osm_fid AND s.which = 'start'
JOIN osm.salem_picked_resolution e ON e.osm_fid = p.osm_fid AND e.which = 'end'
ON CONFLICT (gid) DO NOTHING;

\echo Final routing graph:
SELECT
  (SELECT COUNT(*) FROM salem.edges) AS total_edges,
  (SELECT COUNT(*) FROM salem.edges WHERE mtfcc = 'OSMP') AS osmp_edges,
  (SELECT COUNT(*) FROM salem.edges_vertices_pgr) AS total_vertices,
  (SELECT COUNT(*) FROM salem.edges_vertices_pgr WHERE id >= 10000000000) AS osm_offset_vertices;

\echo Connectivity test (tour_essentials A=NPS Visitor B=Peabody Essex):
SELECT COUNT(*) AS hops, ROUND(SUM(cost)::numeric,1) AS total_m
FROM pgr_dijkstra(
  'SELECT gid AS id, source, target, walk_cost AS cost FROM salem.edges WHERE walkable = TRUE',
  tiger.nearest_walkable_node(42.5216, -70.8869),
  tiger.nearest_walkable_node(42.5215405, -70.892209),
  directed := false
);

\echo Lighthouse snap test (Derby Wharf Light, 42.5165717, -70.8835477):
SELECT
  tiger.nearest_walkable_node(42.5165717, -70.8835477) AS snap_node_id,
  ROUND((SELECT ST_Distance(v.the_geom::geography, ST_SetSRID(ST_MakePoint(-70.8835477, 42.5165717), 4269)::geography)
   FROM salem.edges_vertices_pgr v
   WHERE v.id = tiger.nearest_walkable_node(42.5165717, -70.8835477))::numeric, 1) AS snap_distance_m;

\echo Lighthouse routability test (NPS Visitor → Lighthouse):
SELECT COUNT(*) AS hops, ROUND(SUM(cost)::numeric,1) AS total_m
FROM pgr_dijkstra(
  'SELECT gid AS id, source, target, walk_cost AS cost FROM salem.edges WHERE walkable = TRUE',
  tiger.nearest_walkable_node(42.5216, -70.8869),
  tiger.nearest_walkable_node(42.5165717, -70.8835477),
  directed := false
);

COMMIT;
