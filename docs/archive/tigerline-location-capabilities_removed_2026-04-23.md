<!--
PUBLISHED SNAPSHOT — NOT THE CANONICAL SOURCE.

Canonical file:  ~/Research/TigerLine/LOCATION_CAPABILITIES.md
Source repo:     github.com/deanmauriceellis-cloud/TigerLine
Source commit:   c5a319d9d315ac5153edd72fc77bf90d895af9b9
Published to LMA: 2026-04-21 (TigerLine Session 001)

Companion to docs/tigerline-salem-features.md. That doc tells you WHAT
is in TigerLine (per layer). This doc tells you WHAT YOU CAN DO with it
(per use case). Both together are input for LMA's Phase 7 consumer
contract + API design — decide which of the 10 capability categories
LMA wants to consume via pre-exported packages vs live SQL queries.

Do not edit this copy directly — edit in TigerLine and re-publish.
-->

# TigerLine — Location-Centered Capabilities

> **Purpose.** Given a point (longitude, latitude) or a focus polygon (a city, neighborhood, census tract, bounding box), what can TigerLine's PostGIS + pgRouting + `postgis_tiger_geocoder` stack actually answer? This document is city-agnostic — examples use a placeholder `:lon`, `:lat`, or `:extent` that you substitute for any U.S. location. A Salem-specific instantiation is in the final section.
>
> **Prerequisites.** Phase 2 PostGIS import complete (populates `tiger.*` tables). Phase 3 pgRouting topology built (for routing queries). Curated POI layer optional (for tour-specific use cases).

---

## How to read this document

Every capability is presented as:

1. **The question** in plain English (what a user/app/analysis is actually asking)
2. **The SQL shape** — a runnable query template
3. **What you get back** (columns + example row)
4. **Consumer use case** (who asks this question and why)
5. **Cost/latency note** where it matters

Examples are Postgres/PostGIS dialect, assume SRID 4269 (NAD83 — TIGER's native), and use `:lon` / `:lat` / `:extent` as substitutable parameters.

---

## The bird's-eye view — 10 capability categories

| # | Category | One-line summary |
|---|---|---|
| 1 | Where am I? | Administrative lookup: which jurisdictions / districts / regions contain this point |
| 2 | What's around me? | Proximity queries: nearest / within-radius / adjacent features |
| 3 | How do I get from A to B? | Routing via pgRouting on EDGES (driving and walking) |
| 4 | What's this address? | Geocoding: address ↔ coordinate in both directions |
| 5 | What do the demographics look like? | Census BG / tract / block joins with ACS data |
| 6 | What natural features? | Water, coastline, parks, cemeteries, landmarks |
| 7 | Spatial analysis | Isochrones, buffers, clustering, density, intersections |
| 8 | Rendering | Clip, simplify, tile, serve as GeoJSON / MVT / MBTiles |
| 9 | Tour composition | Chained queries: "walkable stops within 1 km of my hotel, sorted by tour priority" |
| 10 | Cross-project joins | TIGER ⋈ curated POIs / GTFS transit / OSM / photo EXIF / weather |

---

# Category 1 — Where am I? (Administrative lookup)

Given a point, identify every jurisdictional polygon that contains it. TIGER makes this cheap because every polygon layer shares a consistent spatial index.

## 1.1 Which state?

```sql
SELECT stusps, name
FROM tiger.state
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269));
```
**Returns:** 1 row like `MA | Massachusetts`.
**Use case:** Top-level filtering for multi-state queries, state-level metadata display.

## 1.2 Which county (or parish / independent city)?

```sql
SELECT statefp, countyfp, name, namelsad
FROM tiger.county
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269));
```
**Returns:** 1 row like `25 | 009 | Essex | Essex County`.
**Use case:** Scope a tour to a county, load county-local data, show county metadata.

## 1.3 Which town / city (COUSUB)?

```sql
SELECT statefp, countyfp, cousubfp, name, namelsad
FROM tiger.cousub
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269));
```
**Returns:** 1 row like `25 | 009 | 60340 | Salem | Salem city`.
**Use case:** "You are in Salem" banner; filter tour content to the current city.

## 1.4 Which incorporated place?

```sql
SELECT statefp, placefp, name, classfp
FROM tiger.place
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269));
```
**Returns:** 1 row (or 0 if in an unincorporated area).
**Use case:** Some states encode place differently than cousub — cross-check.

## 1.5 Which census tract / block group / block?

```sql
-- Tract
SELECT statefp, countyfp, tractce, name
FROM tiger.tract
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269));

-- Block group
SELECT statefp, countyfp, tractce, blkgrpce
FROM tiger.bg
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269));

-- Block (finest granularity)
SELECT statefp, countyfp, tractce, blockce20
FROM tiger.tabblock20
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269));
```
**Returns:** 1 row each.
**Use case:** Hooking into ACS demographic joins (Category 5). Block is the finest Census unit — useful for very-high-zoom queries and population density estimation.

## 1.6 Which ZIP code?

```sql
SELECT zcta5ce20
FROM tiger.zcta520
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269));
```
**Returns:** 1 row like `01970`.
**Use case:** Address-less ZIP display, ZIP-level external-data joins (weather, hotel availability).

## 1.7 Which metropolitan / urban classification?

```sql
-- Metropolitan statistical area (Boston-Cambridge-Newton etc.)
SELECT cbsafp, name, lsad FROM tiger.cbsa
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269));

-- Combined statistical area (larger)
SELECT csafp, name FROM tiger.csa
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269));

-- Urban area (continuously built-up)
SELECT uace20, name, uatype20 FROM tiger.uac20
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269));
```
**Returns:** 1 row each (or 0 for rural areas).
**Use case:** "You are in Greater Boston" context card; urban-vs-rural logic.

## 1.8 Which political districts?

```sql
-- Congressional district
SELECT cdfp FROM tiger.cd
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269));

-- State senate (upper) and state house (lower)
SELECT 'upper' AS chamber, sldust AS district FROM tiger.sldu
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269))
UNION ALL
SELECT 'lower', sldlst FROM tiger.sldl
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269));
```
**Returns:** 1 row each.
**Use case:** Civic engagement apps (not primary tour use, but available).

## 1.9 Which school district?

```sql
SELECT name, unsdlea FROM tiger.unsd
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269))
UNION ALL
SELECT name, elsdlea FROM tiger.elsd
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269))
UNION ALL
SELECT name, scsdlea FROM tiger.scsd
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269));
```
**Returns:** 0–3 rows (unified OR elementary+secondary).
**Use case:** Real-estate apps, family-travel filtering. Not tour-critical.

## 1.10 Everything at once (one-shot full-stack lookup)

```sql
WITH p AS (SELECT ST_SetSRID(ST_MakePoint(:lon, :lat), 4269) AS g)
SELECT
  (SELECT stusps FROM tiger.state WHERE ST_Contains(geom, p.g))                           AS state,
  (SELECT name   FROM tiger.county WHERE ST_Contains(geom, p.g))                          AS county,
  (SELECT name   FROM tiger.cousub WHERE ST_Contains(geom, p.g))                          AS city,
  (SELECT name   FROM tiger.place WHERE ST_Contains(geom, p.g))                           AS place,
  (SELECT tractce FROM tiger.tract WHERE ST_Contains(geom, p.g))                          AS tract,
  (SELECT blkgrpce FROM tiger.bg WHERE ST_Contains(geom, p.g))                            AS block_group,
  (SELECT blockce20 FROM tiger.tabblock20 WHERE ST_Contains(geom, p.g))                   AS block,
  (SELECT zcta5ce20 FROM tiger.zcta520 WHERE ST_Contains(geom, p.g))                      AS zip,
  (SELECT name FROM tiger.cbsa WHERE ST_Contains(geom, p.g))                              AS metro,
  (SELECT name FROM tiger.csa WHERE ST_Contains(geom, p.g))                               AS combined_metro
FROM p;
```
**Returns:** 1 row, wide — every administrative container of the point.
**Use case:** The "where am I?" payload any client can request. Cache-friendly. ~10ms with spatial indexes.

---

# Category 2 — What's around me? (Proximity queries)

Given a point, find nearby features. TIGER gives you everything except commercial POIs (which come from curated data).

## 2.1 Nearest road

```sql
SELECT fullname, mtfcc,
       ST_Distance(geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)::geography) AS meters
FROM tiger.roads
ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)
LIMIT 1;
```
**Returns:** `Derby St | S1400 | 12.4` (meters).
**Use case:** "You are near Derby St" display. Reverse geocoding hint.
**Note:** The `<->` operator + GIST index = sub-millisecond nearest-neighbor.

## 2.2 All roads within a radius

```sql
SELECT fullname, mtfcc,
       ST_Distance(geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)::geography) AS meters
FROM tiger.roads
WHERE ST_DWithin(
  geom::geography,
  ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)::geography,
  :radius_m
)
ORDER BY meters;
```
**Use case:** "Streets in the 200m walking bubble around my location."

## 2.3 Nearest park / cemetery / polygon landmark (AREALM)

```sql
SELECT fullname, mtfcc,
       ST_Distance(geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)::geography) AS meters
FROM tiger.arealm
WHERE statefp = :statefp
  AND mtfcc IN ('K2180','K2181','K2195')  -- parks, rec areas, cemeteries
ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)
LIMIT 5;
```
**Returns:** 5 nearest polygon landmarks by feature class. Names like `Salem Common | K2180 | 45.1`.
**Use case:** "Nearest park" / "Nearest cemetery" in a ghost-themed tour.

## 2.4 Nearest point landmark (POINTLM)

```sql
SELECT fullname, mtfcc,
       ST_Distance(geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)::geography) AS meters
FROM tiger.pointlm
WHERE statefp = :statefp
ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)
LIMIT 10;
```
**Returns:** Nearest schools, churches, hospitals, government buildings. Limited set — see Category 10 for curated POIs.
**Use case:** Emergency services locator ("nearest hospital"), landmark orientation.

## 2.5 Nearest rail station / line

```sql
-- Rail lines intersecting a buffer
SELECT fullname, mtfcc
FROM tiger.rails
WHERE ST_DWithin(
  geom::geography,
  ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)::geography,
  :radius_m
);
```
**Use case:** "How far is the nearest MBTA line?" Note: rail *stations* are usually in POINTLM (K-codes) or must come from GTFS data.

## 2.6 Nearest water feature

```sql
-- Water polygons (bays, lakes, ponds)
SELECT fullname, mtfcc,
       ST_Distance(geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)::geography) AS meters
FROM tiger.areawater
WHERE statefp = :statefp AND countyfp = :countyfp
ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)
LIMIT 5;

-- Linear water (streams, rivers)
SELECT fullname, mtfcc,
       ST_Distance(geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)::geography) AS meters
FROM tiger.linearwater
WHERE statefp = :statefp AND countyfp = :countyfp
ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)
LIMIT 5;
```
**Use case:** "Nearest harbor / river / pond." Essential for coastal/river-city tours.

## 2.7 Nearest coastline

```sql
SELECT ST_Distance(
  geom::geography,
  ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)::geography
) AS meters_to_coast
FROM tiger.coastline
ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)
LIMIT 1;
```
**Use case:** "Distance to the ocean." For Salem, useful for waterfront tour UX.

## 2.8 Everything of any kind within a radius (one-shot)

```sql
WITH p AS (SELECT ST_SetSRID(ST_MakePoint(:lon, :lat), 4269) AS g)
SELECT 'road' AS kind, fullname AS name, mtfcc,
       ST_Distance(geom::geography, p.g::geography) AS meters
FROM tiger.roads, p
WHERE ST_DWithin(geom::geography, p.g::geography, :radius_m)

UNION ALL
SELECT 'park/cemetery', fullname, mtfcc,
       ST_Distance(geom::geography, p.g::geography)
FROM tiger.arealm, p
WHERE mtfcc IN ('K2180','K2181','K2195')
  AND ST_DWithin(geom::geography, p.g::geography, :radius_m)

UNION ALL
SELECT 'pointlm', fullname, mtfcc,
       ST_Distance(geom::geography, p.g::geography)
FROM tiger.pointlm, p
WHERE ST_DWithin(geom::geography, p.g::geography, :radius_m)

UNION ALL
SELECT 'water', fullname, mtfcc,
       ST_Distance(geom::geography, p.g::geography)
FROM tiger.areawater, p
WHERE ST_DWithin(geom::geography, p.g::geography, :radius_m)

ORDER BY meters;
```
**Use case:** "Everything interesting within 500m of my current location" — a single dashboard query.

---

# Category 3 — How do I get from A to B? (Routing)

Requires **Phase 3** pgRouting topology build on `tiger.edges`. Until then these are aspirational; after Phase 3 they are real.

## 3.1 Walking directions, point-to-point

```sql
WITH
  src AS (SELECT source FROM tiger.edges
          ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:src_lon, :src_lat), 4269) LIMIT 1),
  dst AS (SELECT target FROM tiger.edges
          ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:dst_lon, :dst_lat), 4269) LIMIT 1)
SELECT seq, edge, cost, agg_cost, ST_AsGeoJSON(ST_Transform(e.geom, 4326)) AS geom
FROM pgr_dijkstra(
  'SELECT gid AS id, source, target, length_m AS cost
   FROM tiger.edges
   WHERE mtfcc IN (''S1400'',''S1500'',''S1710'',''S1720'',''S1730'',''S1740'',''S1750'',''S1780'')',
  (SELECT source FROM src),
  (SELECT target FROM dst),
  directed := false
) r
JOIN tiger.edges e ON e.gid = r.edge;
```
**Returns:** Ordered list of edges forming the shortest walking path + total cost (meters). GeoJSON geometry for each segment.
**Use case:** "Directions from my hotel to the Witch House." THE core Phase 5 deliverable.

## 3.2 Driving directions

```sql
-- Same as 3.1 but drop the pedestrian mtfcc filter and weight by drive time
SELECT ... FROM pgr_dijkstra(
  'SELECT gid AS id, source, target,
     CASE WHEN mtfcc LIKE ''S11%'' THEN length_m / 30.0  -- highways ~108 km/h
          WHEN mtfcc LIKE ''S12%'' THEN length_m / 15.0  -- primary ~54 km/h
          ELSE length_m / 10.0                            -- local ~36 km/h
     END AS cost
   FROM tiger.edges WHERE roadflg = ''Y''',
  :src, :dst, false
);
```
**Use case:** Driving routing (less tour-relevant, but possible from the same data).

## 3.3 Isochrone — everywhere reachable in N minutes on foot

```sql
WITH start AS (
  SELECT source FROM tiger.edges
  ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4269) LIMIT 1
)
SELECT
  ST_ConcaveHull(ST_Collect(e.geom), 0.1) AS reachable_polygon,
  :minutes * 60 * :walk_speed_m_per_s AS budget_m
FROM pgr_drivingDistance(
  'SELECT gid AS id, source, target, length_m AS cost
   FROM tiger.edges
   WHERE mtfcc IN (''S1400'',''S1500'',''S1710'',''S1720'',''S1730'',''S1740'',''S1750'',''S1780'')',
  (SELECT source FROM start),
  :minutes * 60 * :walk_speed_m_per_s,
  directed := false
) r
JOIN tiger.edges e ON e.gid = r.edge;
```
**Returns:** A polygon showing everywhere reachable within `:minutes` minutes at `:walk_speed_m_per_s` (default 4.5 km/h ≈ 1.25 m/s).
**Use case:** "Show me everything walkable from Salem Depot in 15 minutes." Essential for "wander" mode in the tour.

## 3.4 Many-to-many shortest paths (tour ordering)

```sql
SELECT * FROM pgr_dijkstraCostMatrix(
  'SELECT gid AS id, source, target, length_m AS cost
   FROM tiger.edges WHERE ...',
  ARRAY[:node1, :node2, :node3, :node4, ...],  -- the tour-stop nodes
  directed := false
);
```
**Returns:** N×N matrix of walking costs between all tour stops.
**Use case:** Input to TSP (traveling salesman) solver for "visit all 10 tour stops in the optimal order." MASTER_PLAN Phase 8.

## 3.5 Nearest-routable-point snapping

```sql
-- Given an arbitrary lat/lon, find the nearest walkable edge and the closest point ON that edge
SELECT
  e.gid AS edge_id,
  ST_ClosestPoint(e.geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)) AS snapped_point,
  ST_Distance(
    e.geom::geography,
    ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)::geography
  ) AS meters_to_snap
FROM tiger.edges e
WHERE e.mtfcc IN ('S1400','S1500','S1710','S1720','S1730','S1740','S1750','S1780')
ORDER BY e.geom <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)
LIMIT 1;
```
**Use case:** A user drops a pin in the middle of a block — snap it to the nearest walkable street before routing.

---

# Category 4 — What's this address? (Geocoding)

Uses the `postgis_tiger_geocoder` extension, which is already installed and whose tables our import populates.

## 4.1 Address → coordinate (forward geocoding)

```sql
SELECT g.rating, ST_X(g.geomout) AS lon, ST_Y(g.geomout) AS lat, pprint_addy(addy) AS standardized
FROM geocode('174 Derby St, Salem, MA 01970') AS g;
```
**Returns:** Coordinate + match quality rating (0 = perfect, higher = worse) + standardized address.
**Use case:** Users typing addresses in search, batch-geocoding a curated POI list.

## 4.2 Coordinate → address (reverse geocoding)

```sql
SELECT (rg).*
FROM (SELECT reverse_geocode(ST_SetSRID(ST_MakePoint(:lon, :lat), 4269), true) AS rg) x;
```
**Returns:** Intersection of nearest named street + estimated address range + nearby cross streets.
**Use case:** Photo EXIF → "this photo was taken near 174 Derby St" (GeoInbox consumer). Tap-to-identify in maps.

## 4.3 Fuzzy / partial matches

```sql
SELECT * FROM geocode('Derby St, Salem', max_results := 5);
```
**Returns:** Multiple candidate matches when input is incomplete.
**Use case:** Typeahead suggestions; recovering from typos.

## 4.4 Address-range lookup along a specific street

```sql
SELECT tlid, fullname, lfromhn, ltohn, rfromhn, rtohn, zipl, zipr, parityl, parityr
FROM tiger.addrfeat
WHERE fullname = 'DERBY ST' AND zipl = '01970';
```
**Returns:** Every ADDRFEAT segment of Derby St in Salem's ZIP, with left/right address ranges.
**Use case:** Inventory what addresses exist on a street; dense-block visualization.

---

# Category 5 — Demographics at this location

Requires joining TIGER geometry with external ACS (American Community Survey) data — a separate non-TIGER dataset. Load as `acs.*` tables alongside `tiger.*`.

## 5.1 Population at this point

```sql
SELECT acs.population, acs.median_income, acs.median_age
FROM tiger.bg b
JOIN acs.block_group acs ON acs.geoid = b.geoid
WHERE ST_Contains(b.geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269));
```
**Returns:** Demographic row for the block group containing the point.
**Use case:** "This neighborhood has ~2,400 residents, median age 38."

## 5.2 Population density in an area

```sql
SELECT SUM(acs.population)::float / (ST_Area(ST_Union(b.geom)::geography) / 1e6) AS people_per_sq_km
FROM tiger.bg b
JOIN acs.block_group acs ON acs.geoid = b.geoid
WHERE ST_Intersects(b.geom, :extent);
```
**Use case:** Thematic map coloring, "busy neighborhood" scoring.

## 5.3 Demographic heatmap for rendering

```sql
SELECT b.geom, acs.median_income
FROM tiger.bg b
JOIN acs.block_group acs ON acs.geoid = b.geoid
WHERE ST_Intersects(b.geom, :extent);
```
**Use case:** Vector tile generator input for income / age / ethnicity choropleth overlays.

---

# Category 6 — Natural and physical features

## 6.1 Which water body is this?

```sql
SELECT fullname, mtfcc
FROM tiger.areawater
WHERE ST_Contains(geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269));
```
**Use case:** User taps water on the map → label appears.

## 6.2 Named streams / rivers crossing an area

```sql
SELECT DISTINCT fullname
FROM tiger.linearwater
WHERE ST_Intersects(geom, :extent)
  AND fullname IS NOT NULL
ORDER BY fullname;
```

## 6.3 All parks + cemeteries + recreation polygons in an area

```sql
SELECT fullname, mtfcc, ST_Area(geom::geography)/4047 AS acres
FROM tiger.arealm
WHERE ST_Intersects(geom, :extent)
  AND mtfcc IN ('K2180','K2181','K2195','K2540')
ORDER BY acres DESC;
```
**Use case:** Green-space inventory for a neighborhood; cemetery-tour assembly.

## 6.4 Coastline length in an area (oceanfront extent)

```sql
SELECT ST_Length(ST_Union(ST_Intersection(c.geom, :extent))::geography) / 1000 AS coastline_km
FROM tiger.coastline c
WHERE ST_Intersects(c.geom, :extent);
```
**Use case:** Water-themed city metrics ("Salem has 8.2 km of coastline inside its city boundary").

---

# Category 7 — Spatial analysis (buffers, clusters, density, hulls)

## 7.1 Buffer a point by walking distance

```sql
-- 500m buffer as a polygon, suitable for overlay rendering
SELECT ST_Buffer(
  ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)::geography,
  :meters
)::geometry AS walk_bubble;
```

## 7.2 Nearest-N clustering

```sql
-- Find centroid of the cluster of POIs nearest to me
WITH near AS (
  SELECT name, geom
  FROM tour.poi
  ORDER BY geom <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)
  LIMIT :n
)
SELECT ST_Centroid(ST_Collect(geom))::geometry(Point,4269) AS cluster_center
FROM near;
```

## 7.3 Density per square km in an area

```sql
SELECT COUNT(*) / (ST_Area(:extent::geography)/1e6) AS pois_per_sq_km
FROM tour.poi
WHERE ST_Intersects(geom, :extent);
```

## 7.4 Concave hull around a set of points

```sql
SELECT ST_ConcaveHull(ST_Collect(geom), 0.2)
FROM tour.poi
WHERE category = 'witch-trial-site';
```
**Use case:** "Draw a polygon around all witch-trial sites" → renders as a themed overlay zone.

## 7.5 Geometry simplification for low-zoom rendering

```sql
SELECT ST_SimplifyPreserveTopology(geom, 0.0001) AS simplified
FROM tiger.cousub
WHERE statefp = '25';
```
**Use case:** Reducing vertex count for tile generation at low zooms without creating invalid polygons.

---

# Category 8 — Rendering & export

## 8.1 GeoJSON for a feature

```sql
SELECT jsonb_build_object(
  'type','Feature',
  'geometry', ST_AsGeoJSON(ST_Transform(geom, 4326))::jsonb,
  'properties', jsonb_build_object('name', fullname, 'class', mtfcc)
)
FROM tiger.arealm
WHERE fullname = 'Salem Common';
```

## 8.2 GeoJSON for a whole layer, clipped to an extent

```sql
SELECT jsonb_build_object(
  'type','FeatureCollection',
  'features', jsonb_agg(jsonb_build_object(
    'type','Feature',
    'geometry', ST_AsGeoJSON(ST_Transform(geom, 4326))::jsonb,
    'properties', jsonb_build_object('name', fullname, 'class', mtfcc)
  ))
)
FROM tiger.arealm
WHERE ST_Intersects(geom, :extent);
```

## 8.3 Mapbox Vector Tile (MVT) generation

```sql
SELECT ST_AsMVT(tile, 'roads', 4096, 'geom')
FROM (
  SELECT
    ST_AsMVTGeom(
      ST_Transform(r.geom, 3857),
      ST_TileEnvelope(:z, :x, :y),
      4096, 256, true
    ) AS geom,
    r.fullname, r.mtfcc
  FROM tiger.roads r
  WHERE r.geom && ST_Transform(ST_TileEnvelope(:z, :x, :y), 4269)
) tile;
```
**Use case:** Serving vector tiles from Postgres directly — skip tippecanoe for live rendering.

## 8.4 MBTiles export (via tippecanoe, Phase 9)

Out-of-band: export the filtered GeoJSON from 8.2 to disk, run tippecanoe to produce an `.mbtiles` file.
**Use case:** Offline map bundles for mobile apps.

## 8.5 Cartographic simplification by zoom

```sql
SELECT ST_SimplifyPreserveTopology(
  geom,
  CASE :z
    WHEN 8 THEN 0.01
    WHEN 10 THEN 0.005
    WHEN 12 THEN 0.001
    WHEN 14 THEN 0.0001
    ELSE 0
  END
)
FROM tiger.roads
WHERE ST_Intersects(geom, :extent);
```

---

# Category 9 — Tour composition (chained / narrative queries)

The queries below assume a curated POI table exists: `tour.poi(id, name, category, geom, description, ...)`.

## 9.1 "Tour stops within 500m of my hotel, sorted by tour priority"

```sql
SELECT p.name, p.category, p.description,
       ST_Distance(p.geom::geography, ST_SetSRID(ST_MakePoint(:hotel_lon, :hotel_lat), 4269)::geography) AS meters
FROM tour.poi p
WHERE ST_DWithin(
  p.geom::geography,
  ST_SetSRID(ST_MakePoint(:hotel_lon, :hotel_lat), 4269)::geography,
  500
)
ORDER BY p.priority DESC, meters ASC;
```

## 9.2 "Tour stops along the walking route from A to B"

```sql
-- Buffer the route geometry, then find POIs inside the buffer
WITH route AS (
  SELECT ST_Union(e.geom) AS path_geom
  FROM pgr_dijkstra(...) r
  JOIN tiger.edges e ON e.gid = r.edge
)
SELECT p.name, p.category
FROM tour.poi p, route
WHERE ST_DWithin(p.geom::geography, route.path_geom::geography, :buffer_m);
```
**Use case:** "On my walk from the hotel to the Witch House, here are 4 stops you'd pass."

## 9.3 "Tour stops reachable in 15 minutes on foot"

```sql
WITH reachable AS (
  -- From Category 3.3 isochrone query
  SELECT ... AS reachable_polygon
)
SELECT p.*
FROM tour.poi p, reachable
WHERE ST_Contains(reachable.reachable_polygon, p.geom);
```

## 9.4 "Optimal order to visit all selected stops"

```sql
-- Many-to-many cost matrix from pgRouting (Category 3.4) → feed to TSP solver
-- Then render the optimized path
```

## 9.5 "Tour stops by theme, clustered by neighborhood"

```sql
SELECT t.name AS neighborhood, COUNT(p.id) AS stops, array_agg(p.name) AS stop_names
FROM tour.poi p
JOIN tiger.tract t ON ST_Contains(t.geom, p.geom)
WHERE p.category = :theme
  AND t.statefp = :statefp
GROUP BY t.name
ORDER BY stops DESC;
```

---

# Category 10 — Cross-project / cross-dataset joins

The real power of TigerLine comes from **joining TIGER base geometry with other datasets**. Below, `xxx.*` refers to external schemas populated by other projects.

## 10.1 ⋈ Curated POI layer (LMA salem-content)

```sql
SELECT p.name AS poi, r.fullname AS on_street, p.description
FROM lma.poi p
JOIN tiger.edges r ON ST_DWithin(p.geom, r.geom, 0.0001)
WHERE p.city = 'Salem';
```
**Use case:** Every LMA-curated POI tagged with its street → better UI labels, better geocoding fallback.

## 10.2 ⋈ GTFS transit feed

```sql
SELECT s.name AS stop, s.geom
FROM gtfs.stops s
WHERE ST_DWithin(
  s.geom::geography,
  ST_SetSRID(ST_MakePoint(:lon, :lat), 4269)::geography,
  200
)
ORDER BY s.geom <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4269);
```
**Use case:** "Nearest MBTA bus stop + route IDs." Transit is always a separate GTFS import; TIGER's RAILS doesn't cover bus/subway/streetcar reliably.

## 10.3 ⋈ OpenStreetMap pedestrian paths

```sql
-- Combine TIGER edges + OSM footways for a richer pedestrian graph
WITH pedestrian_edges AS (
  SELECT gid, source, target, length_m, 'tiger' AS src FROM tiger.edges
  WHERE mtfcc IN ('S1400','S1500','S1710','S1720','S1730','S1740','S1750','S1780')
  UNION ALL
  SELECT gid + 100000000, source, target, length_m, 'osm' FROM osm.footways
)
SELECT * FROM pgr_dijkstra('SELECT gid AS id, ... FROM pedestrian_edges', :src, :dst);
```
**Use case:** Phase 6 deliverable — combines TIGER's road-level routing with OSM's sidewalks/paths/steps.

## 10.4 ⋈ Photo EXIF data (GeoInbox)

```sql
SELECT photo.id, photo.taken_at,
       (reverse_geocode(photo.geom)).addy AS approximate_address,
       nearest.fullname AS nearest_street
FROM geoinbox.photos photo
CROSS JOIN LATERAL (
  SELECT fullname FROM tiger.edges
  ORDER BY geom <-> photo.geom LIMIT 1
) nearest
WHERE photo.city_fips = '25-009';
```
**Use case:** GeoInbox consumer — reverse-geocoding tourist photos against TIGER addresses.

## 10.5 ⋈ Weather (hypothetical external schema)

```sql
SELECT w.temp_f, w.conditions
FROM weather.current w
JOIN tiger.zcta520 z ON w.zip = z.zcta5ce20
WHERE ST_Contains(z.geom, ST_SetSRID(ST_MakePoint(:lon, :lat), 4269));
```

## 10.6 ⋈ Historical maps (hypothetical georeferenced overlays)

```sql
SELECT historic.name, historic.year, historic.geom
FROM history.maps_salem historic
WHERE ST_Intersects(historic.geom, :extent);
```
**Use case:** Overlay 1692 / 1790 / 1850 Salem maps on modern TIGER basemap — differentiator for witch-trial-era tourism.

---

# Consumption patterns

The above categories map to three deployment modes, all already planned:

| Mode | How it works | When to use |
|---|---|---|
| **Live SQL** | Consumer (LMA, GeoInbox) connects to the `tiger` Postgres directly | Rich ad-hoc queries, admin dashboards, server-side logic. Requires network access to the DB. |
| **Per-city package** | Phase 9 exports SpatiaLite + binary routing graph + MBTiles for one city, bundled with the mobile app | Offline mobile, guaranteed latency, no dependency on a DB server. This is LMA's Phase 7 contract. |
| **Tile server** | Vector tile generation from 8.3 served over HTTP | Live web-map rendering where data freshness matters more than offline support. Not in current MASTER_PLAN — would be a Phase 13+ addition. |

---

# Salem — worked examples

Everything above is city-agnostic. Below are the same queries with Salem values plugged in.

**Anchor:** Salem Common center — approximately `(-70.894, 42.522)`.

## "Where am I standing?"

```sql
WITH p AS (SELECT ST_SetSRID(ST_MakePoint(-70.894, 42.522), 4269) AS g)
SELECT
  (SELECT stusps FROM tiger.state WHERE ST_Contains(geom, p.g))                           AS state,   -- MA
  (SELECT name   FROM tiger.county WHERE ST_Contains(geom, p.g))                          AS county,  -- Essex
  (SELECT name   FROM tiger.cousub WHERE ST_Contains(geom, p.g))                          AS city,    -- Salem
  (SELECT zcta5ce20 FROM tiger.zcta520 WHERE ST_Contains(geom, p.g))                      AS zip,     -- 01970
  (SELECT name FROM tiger.cbsa WHERE ST_Contains(geom, p.g))                              AS metro    -- Boston-Cambridge-Newton, MA-NH
FROM p;
```

## "What's within 300m of Salem Common?"

```sql
SELECT fullname, mtfcc,
  ST_Distance(geom::geography, ST_SetSRID(ST_MakePoint(-70.894, 42.522), 4269)::geography) AS m
FROM tiger.arealm
WHERE statefp = '25'
  AND ST_DWithin(
    geom::geography,
    ST_SetSRID(ST_MakePoint(-70.894, 42.522), 4269)::geography,
    300
  )
ORDER BY m;
-- Expected: Salem Common (0m, you're in it), Old Burying Point (~200m S),
-- Howard Street Cemetery (~250m W), nearby churches from POINTLM.
```

## "Walking isochrone — 10 minutes from Salem Depot"

```sql
-- Salem Depot ~(-70.895, 42.525), after Phase 3 topology build:
WITH start AS (
  SELECT source FROM tiger.edges
  ORDER BY geom <-> ST_SetSRID(ST_MakePoint(-70.895, 42.525), 4269) LIMIT 1
)
SELECT ST_ConcaveHull(ST_Collect(e.geom), 0.1) AS reachable_in_10min
FROM pgr_drivingDistance(
  'SELECT gid AS id, source, target, length_m AS cost
   FROM tiger.edges
   WHERE mtfcc IN (''S1400'',''S1500'',''S1710'',''S1720'',''S1730'',''S1740'',''S1750'',''S1780'')',
  (SELECT source FROM start),
  10 * 60 * 1.25,   -- 10 min * 60 s * 1.25 m/s ≈ 750m
  directed := false
) r
JOIN tiger.edges e ON e.gid = r.edge;
```

## "Optimal tour of the top 10 witch-trial sites"

```sql
-- Step 1: Get the 10 site nodes (nearest routable nodes to each curated POI)
-- Step 2: pgr_dijkstraCostMatrix to get all pairwise walking distances
-- Step 3: Feed to an external TSP solver (or pgr_TSP if available)
-- Step 4: Return the ordered list + total walking time
```

---

# Quick reference — which category for which question?

| You are asking... | Go to |
|---|---|
| "Which city/county/ZIP is this?" | Category 1 |
| "What's nearby?" | Category 2 |
| "How long to walk there?" | Category 3 |
| "Where is this address on the map?" | Category 4 |
| "Who lives here?" | Category 5 |
| "Is there a park / water / landmark?" | Category 6 |
| "What's within 500m?" | Category 2 or 7 |
| "Can I see this on a map?" | Category 8 |
| "Can I build a tour from here?" | Category 9 |
| "Can I combine this with our other data?" | Category 10 |

---

_Generated 2026-04-21 (TigerLine Session 001) as a capability reference. City-agnostic; Phase 2 import of the national TIGER catalog in flight at time of writing. Salem worked examples assume post-import state._
