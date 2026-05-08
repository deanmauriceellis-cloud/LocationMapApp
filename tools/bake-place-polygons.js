#!/usr/bin/env node
/*
 * S231 — Bake nationwide place-resolution polygon asset for the splash screen
 * decision tree. Output: app-salem/src/main/assets/us_places_v1.sqlite
 *
 * Layered hint resolution (queried in order at runtime):
 *   [1] CITY      Top-100 US cities (raw simplified polygons)        → "in <city>"
 *   [2] BUFFER    Top-100 cities + 30 mi buffer rings                 → "near <city>"
 *   [3] TOWN_LOC  Salem-adjacent MA towns (10 entries)                → "in <town>, MA"
 *   [4] COUNTY    All ~3,235 US counties (aggressively simplified)    → "in <county>, <state>"
 *   [5] (none)    Outside everything (ocean / outside US) → distance-only template
 *
 * Source: TigerLine 2025 shapefiles ingested into PG schema tiger.s231_*
 *   (See /tmp/lma-tiger-ingest/ + the shp2pgsql ingest run in S231 setup.)
 *
 * V1 offline rule: this is a build-time process. No network at runtime.
 *
 * Schema written to the SQLite asset:
 *   places(id PK, layer, name, admin_name, lat_centroid, lon_centroid,
 *          minx, miny, maxx, maxy, pop)
 *   place_geom(id PK → places.id, wkb_simplified BLOB)
 *   places_rtree (R-tree spatial index on bbox)
 *   meta(key, value) — schema_version, baked_at, layer counts
 *
 * The runtime resolver uses places_rtree to bbox-prefilter ~3,400 → ~5
 * candidates per (lat, lon) lookup, then runs point-in-polygon against the
 * simplified WKB. Sub-millisecond warm.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module bake-place-polygons.js';
void MODULE_ID;

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const { Client } = require('pg');
const Database = require('better-sqlite3');

// ──────────────────────────────────────────────────────────────────────────
// Layer config
// ──────────────────────────────────────────────────────────────────────────

// Top-100 US cities by 2023 Census estimated population (incorporated cities
// + a handful of CDPs). Hardcoded so the bake has zero network dependency
// and the choice is explicit / auditable. (name, state_postal, statefp).
//
// Aliases: a couple of cities have different name spellings between TigerLine
// PLACE.NAME and common usage — we list the TigerLine-canonical NAME so the
// JOIN finds the row directly.
const TOP_100_CITIES = [
  ['New York',           'NY', '36'],
  ['Los Angeles',        'CA', '06'],
  ['Chicago',            'IL', '17'],
  ['Houston',            'TX', '48'],
  ['Phoenix',            'AZ', '04'],
  ['Philadelphia',       'PA', '42'],
  ['San Antonio',        'TX', '48'],
  ['San Diego',          'CA', '06'],
  ['Dallas',             'TX', '48'],
  ['Jacksonville',       'FL', '12'],
  ['Austin',             'TX', '48'],
  ['Fort Worth',         'TX', '48'],
  ['San Jose',           'CA', '06'],
  ['Columbus',           'OH', '39'],
  ['Charlotte',          'NC', '37'],
  ['Indianapolis city (balance)', 'IN', '18'],
  ['San Francisco',      'CA', '06'],
  ['Seattle',            'WA', '53'],
  ['Denver',             'CO', '08'],
  ['Washington',         'DC', '11'],
  ['Nashville-Davidson metropolitan government (balance)', 'TN', '47'],
  ['Oklahoma City',      'OK', '40'],
  ['El Paso',            'TX', '48'],
  ['Boston',             'MA', '25'],
  ['Portland',           'OR', '41'],
  ['Las Vegas',          'NV', '32'],
  ['Detroit',            'MI', '26'],
  ['Memphis',            'TN', '47'],
  ['Louisville/Jefferson County metro government (balance)', 'KY', '21'],
  ['Baltimore',          'MD', '24'],
  ['Milwaukee',          'WI', '55'],
  ['Albuquerque',        'NM', '35'],
  ['Tucson',             'AZ', '04'],
  ['Fresno',             'CA', '06'],
  ['Sacramento',         'CA', '06'],
  ['Mesa',               'AZ', '04'],
  ['Kansas City',        'MO', '29'],
  ['Atlanta',            'GA', '13'],
  ['Omaha',              'NE', '31'],
  ['Colorado Springs',   'CO', '08'],
  ['Raleigh',            'NC', '37'],
  ['Long Beach',         'CA', '06'],
  ['Virginia Beach',     'VA', '51'],
  ['Miami',              'FL', '12'],
  ['Oakland',            'CA', '06'],
  ['Minneapolis',        'MN', '27'],
  ['Tulsa',              'OK', '40'],
  ['Bakersfield',        'CA', '06'],
  ['Wichita',            'KS', '20'],
  ['Arlington',          'TX', '48'],
  ['Aurora',             'CO', '08'],
  ['Tampa',              'FL', '12'],
  ['New Orleans',        'LA', '22'],
  ['Cleveland',          'OH', '39'],
  ['Urban Honolulu',     'HI', '15'], // Urban Honolulu CDP — closest to "Honolulu" common usage
  ['Anaheim',            'CA', '06'],
  ['Lexington-Fayette',  'KY', '21'],
  ['Stockton',           'CA', '06'],
  ['Corpus Christi',     'TX', '48'],
  ['Henderson',          'NV', '32'],
  ['Riverside',          'CA', '06'],
  ['Newark',             'NJ', '34'],
  ['St. Paul',           'MN', '27'],
  ['Santa Ana',          'CA', '06'],
  ['Cincinnati',         'OH', '39'],
  ['Irvine',             'CA', '06'],
  ['Orlando',            'FL', '12'],
  ['Pittsburgh',         'PA', '42'],
  ['Greensboro',         'NC', '37'],
  ['Durham',             'NC', '37'],
  ['Lincoln',            'NE', '31'],
  ['Jersey City',        'NJ', '34'],
  ['Plano',              'TX', '48'],
  ['Anchorage',          'AK', '02'],
  ['North Las Vegas',    'NV', '32'],
  ['St. Louis',          'MO', '29'],
  ['Madison',            'WI', '55'],
  ['Chandler',           'AZ', '04'],
  ['Gilbert',            'AZ', '04'],
  ['Reno',               'NV', '32'],
  ['Buffalo',            'NY', '36'],
  ['Chula Vista',        'CA', '06'],
  ['Fort Wayne',         'IN', '18'],
  ['Lubbock',            'TX', '48'],
  ['Toledo',             'OH', '39'],
  ['St. Petersburg',     'FL', '12'],
  ['Laredo',             'TX', '48'],
  ['Irving',             'TX', '48'],
  ['Norfolk',            'VA', '51'],
  ['Chesapeake',         'VA', '51'],
  ['Glendale',           'AZ', '04'],
  ['Garland',            'TX', '48'],
  ['Scottsdale',         'AZ', '04'],
  ['Winston-Salem',      'NC', '37'],
  ['Boise City',         'ID', '16'],
  ['Richmond',           'VA', '51'],
  ['Spokane',            'WA', '53'],
  ['Hialeah',            'FL', '12'],
  ['Frisco',             'TX', '48'],
  ['Baton Rouge',        'LA', '22'],
  ['Tacoma',             'WA', '53'],
  ['Modesto',            'CA', '06'],
];

// Salem-adjacent towns — Salem itself + every town that ST_Touches Salem
// (the 6 direct geometric neighbors) + a small set of second-ring tourist
// arteries operator-confirmed in S231. (name, statefp) — all MA so statefp='25'.
//
// "Salem" is in the layer so a user inside Salem resolves to "in Salem, MA"
// rather than falling through to Boston's 30-mile buffer ring.
const SALEM_ADJACENT = [
  // Salem proper
  ['Salem',                  '25'],
  // Direct geometric neighbors (ST_Touches Salem) — confirmed via PG
  ['Beverly',                '25'],
  ['Danvers',                '25'],
  ['Lynn',                   '25'],
  ['Marblehead',             '25'],
  ['Peabody',                '25'],
  ['Swampscott',             '25'],
  // Second-ring tourist-arterial neighbors
  ['Manchester-by-the-Sea',  '25'],
  ['Wenham',                 '25'],
  ['Topsfield',              '25'],
  ['Saugus',                 '25'],
];

// Simplification tolerances (degrees, in EPSG:4269/NAD83 working frame).
// 1° ≈ 111 km. Looser = smaller. We're not rendering — just point-in-polygon.
const TOL_CITY     = 0.0005;  // ~55 m — keep city outlines crisp
const TOL_BUFFER   = 0.001;   // ~110 m — buffer rings can be loose
const TOL_TOWN     = 0.0005;  // ~55 m — Salem neighborhood needs decent edges
const TOL_COUNTY   = 0.03;    // ~3.3 km — counties are huge, "in this county?" tolerates lots of wobble

// 30 mile buffer ring around top-100 cities.
const BUFFER_MILES = 30;
const BUFFER_METERS = Math.round(BUFFER_MILES * 1609.344);

// Layer codes (single byte) stored in places.layer for fast filter.
const LAYER_CITY    = 1;
const LAYER_BUFFER  = 2;
const LAYER_TOWN    = 3;
const LAYER_COUNTY  = 4;

const ASSET_PATH = path.resolve(__dirname, '..', 'app-salem', 'src', 'main', 'assets', 'us_places_v1.sqlite');

// ──────────────────────────────────────────────────────────────────────────

async function main() {
  const t0 = Date.now();
  // Unix-socket peer auth as the OS user (per memory reference_credentials).
  const pg = new Client({ database: 'tiger', host: '/var/run/postgresql' });
  await pg.connect();

  // Fresh asset.
  if (fs.existsSync(ASSET_PATH)) fs.unlinkSync(ASSET_PATH);
  const db = new Database(ASSET_PATH);
  db.pragma('journal_mode = OFF');
  db.pragma('synchronous = OFF');
  db.exec(`
    CREATE TABLE places (
      id            INTEGER PRIMARY KEY,
      layer         INTEGER NOT NULL,           -- LAYER_*
      name          TEXT    NOT NULL,           -- e.g. "Detroit"
      admin_name    TEXT,                       -- state postal, e.g. "MI"
      lat_centroid  REAL    NOT NULL,
      lon_centroid  REAL    NOT NULL,
      minx          REAL    NOT NULL,
      miny          REAL    NOT NULL,
      maxx          REAL    NOT NULL,
      maxy          REAL    NOT NULL,
      pop           INTEGER
    );
    CREATE TABLE place_geom (
      id            INTEGER PRIMARY KEY,
      wkb_gz        BLOB    NOT NULL            -- gzip(ST_AsBinary(simplified geom, EPSG:4269))
    );
    -- Android stock SQLite ships WITHOUT the rtree extension, so the runtime
    -- uses a regular B-tree index on the bbox columns already stored on
    -- places. ~3,400 rows; index brings the scan to sub-millisecond.
    CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT);
  `);

  const insPlace = db.prepare(
    'INSERT INTO places (id, layer, name, admin_name, lat_centroid, lon_centroid, minx, miny, maxx, maxy, pop) ' +
    'VALUES (@id, @layer, @name, @admin_name, @lat_centroid, @lon_centroid, @minx, @miny, @maxx, @maxy, @pop)'
  );
  const insGeom  = db.prepare('INSERT INTO place_geom (id, wkb_gz) VALUES (?, ?)');
  // (R-Tree dropped — Android SQLite lacks it. Bbox columns live on `places`.)

  const counts = { city: 0, buffer: 0, town: 0, county: 0 };
  let nextId = 1;
  let totalGeomBytes = 0;

  function emitRow(row) {
    const id = nextId++;
    insPlace.run({ id, ...row });
    // gzip level 9 — bake is offline so a few extra ms per row is free.
    const gz = zlib.gzipSync(row.wkb, { level: 9 });
    insGeom.run(id, gz);
    totalGeomBytes += gz.length;
  }

  // ─── Layer 1: Top-100 cities (raw simplified) ────────────────────────────
  console.log('=== Layer 1/4: top-100 cities ===');
  for (const [name, postal, statefp] of TOP_100_CITIES) {
    const r = await pg.query({
      text: `
        SELECT
          name,
          ST_AsBinary(ST_SimplifyPreserveTopology(geom, $3)) AS wkb,
          ST_Y(ST_PointOnSurface(geom)) AS clat,
          ST_X(ST_PointOnSurface(geom)) AS clon,
          ST_XMin(geom) AS minx, ST_YMin(geom) AS miny,
          ST_XMax(geom) AS maxx, ST_YMax(geom) AS maxy
        FROM tiger.s231_us_place
        WHERE statefp = $2 AND name = $1
        LIMIT 1
      `,
      values: [name, statefp, TOL_CITY],
    });
    if (r.rows.length === 0) {
      console.warn(`  WARN city not found: ${name}, ${postal}`);
      continue;
    }
    const row = r.rows[0];
    emitRow({
      layer: LAYER_CITY,
      name, admin_name: postal,
      lat_centroid: row.clat, lon_centroid: row.clon,
      minx: row.minx, miny: row.miny, maxx: row.maxx, maxy: row.maxy,
      pop: null,
      wkb: row.wkb,
    });
    counts.city++;
  }
  console.log(`  → ${counts.city} cities`);

  // ─── Layer 2: 30-mile buffer rings around the same cities ───────────────
  console.log(`=== Layer 2/4: ${BUFFER_MILES}-mile buffer rings ===`);
  for (const [name, postal, statefp] of TOP_100_CITIES) {
    const r = await pg.query({
      text: `
        WITH src AS (SELECT geom FROM tiger.s231_us_place WHERE statefp = $2 AND name = $1 LIMIT 1)
        SELECT
          ST_AsBinary(
            ST_SimplifyPreserveTopology(
              ST_Transform(ST_Buffer(ST_Transform(geom, 5070)::geometry, $3), 4269),
              $4
            )
          ) AS wkb,
          ST_Y(ST_PointOnSurface(geom)) AS clat,
          ST_X(ST_PointOnSurface(geom)) AS clon,
          ST_XMin(ST_Transform(ST_Buffer(ST_Transform(geom, 5070)::geometry, $3), 4269)) AS minx,
          ST_YMin(ST_Transform(ST_Buffer(ST_Transform(geom, 5070)::geometry, $3), 4269)) AS miny,
          ST_XMax(ST_Transform(ST_Buffer(ST_Transform(geom, 5070)::geometry, $3), 4269)) AS maxx,
          ST_YMax(ST_Transform(ST_Buffer(ST_Transform(geom, 5070)::geometry, $3), 4269)) AS maxy
        FROM src
      `,
      values: [name, statefp, BUFFER_METERS, TOL_BUFFER],
    });
    if (r.rows.length === 0) continue;
    const row = r.rows[0];
    emitRow({
      layer: LAYER_BUFFER,
      name, admin_name: postal,
      lat_centroid: row.clat, lon_centroid: row.clon,
      minx: row.minx, miny: row.miny, maxx: row.maxx, maxy: row.maxy,
      pop: null,
      wkb: row.wkb,
    });
    counts.buffer++;
  }
  console.log(`  → ${counts.buffer} buffer rings`);

  // ─── Layer 3: Salem-adjacent towns ──────────────────────────────────────
  console.log('=== Layer 3/4: Salem-adjacent towns ===');
  for (const [name, statefp] of SALEM_ADJACENT) {
    const r = await pg.query({
      text: `
        SELECT
          ST_AsBinary(ST_SimplifyPreserveTopology(geom, $3)) AS wkb,
          ST_Y(ST_PointOnSurface(geom)) AS clat,
          ST_X(ST_PointOnSurface(geom)) AS clon,
          ST_XMin(geom) AS minx, ST_YMin(geom) AS miny,
          ST_XMax(geom) AS maxx, ST_YMax(geom) AS maxy
        FROM tiger.s231_ma_cousub
        WHERE statefp = $2 AND name = $1
        LIMIT 1
      `,
      values: [name, statefp, TOL_TOWN],
    });
    if (r.rows.length === 0) {
      console.warn(`  WARN town not found: ${name}, statefp=${statefp}`);
      continue;
    }
    const row = r.rows[0];
    emitRow({
      layer: LAYER_TOWN,
      name, admin_name: 'MA',
      lat_centroid: row.clat, lon_centroid: row.clon,
      minx: row.minx, miny: row.miny, maxx: row.maxx, maxy: row.maxy,
      pop: null,
      wkb: row.wkb,
    });
    counts.town++;
  }
  console.log(`  → ${counts.town} towns`);

  // ─── Layer 4: All US counties ───────────────────────────────────────────
  console.log('=== Layer 4/4: US counties (aggressive simplify) ===');
  // Stream cursor — ~3,235 rows isn't huge but no need to load all at once.
  const cur = await pg.query({
    text: `
      SELECT
        c.name AS cname,
        s.stusps AS state_postal,
        ST_AsBinary(ST_SimplifyPreserveTopology(c.geom, $1)) AS wkb,
        ST_Y(ST_PointOnSurface(c.geom)) AS clat,
        ST_X(ST_PointOnSurface(c.geom)) AS clon,
        ST_XMin(c.geom) AS minx, ST_YMin(c.geom) AS miny,
        ST_XMax(c.geom) AS maxx, ST_YMax(c.geom) AS maxy
      FROM tiger.s231_us_county c
      JOIN tiger.state s ON s.statefp = c.statefp
    `,
    values: [TOL_COUNTY],
  });
  const tx = db.transaction((rows) => {
    for (const row of rows) {
      emitRow({
        layer: LAYER_COUNTY,
        name: row.cname, admin_name: row.state_postal,
        lat_centroid: row.clat, lon_centroid: row.clon,
        minx: row.minx, miny: row.miny, maxx: row.maxx, maxy: row.maxy,
        pop: null,
        wkb: row.wkb,
      });
      counts.county++;
    }
  });
  tx(cur.rows);
  console.log(`  → ${counts.county} counties`);

  // ─── Indexes + meta ─────────────────────────────────────────────────────
  db.exec('CREATE INDEX places_layer_idx ON places(layer);');
  db.exec('CREATE INDEX places_name_idx  ON places(layer, name);');
  // Composite bbox index — runtime queries lookups of the form
  //   WHERE minx <= ? AND maxx >= ? AND miny <= ? AND maxy >= ?
  // SQLite's planner picks the leading column; (maxx, minx) is the more
  // selective pair for typical North American queries (eastern hemisphere
  // would prefer the inverse but we don't ship there).
  db.exec('CREATE INDEX places_bbox_idx ON places(maxx, minx, maxy, miny);');
  const setMeta = db.prepare('INSERT INTO meta(key,value) VALUES (?,?)');
  setMeta.run('schema_version', '1');
  setMeta.run('baked_at', new Date().toISOString());
  setMeta.run('buffer_miles', String(BUFFER_MILES));
  setMeta.run('count_city',   String(counts.city));
  setMeta.run('count_buffer', String(counts.buffer));
  setMeta.run('count_town',   String(counts.town));
  setMeta.run('count_county', String(counts.county));
  setMeta.run('source', 'TigerLine 2025: PLACE + COUNTY + MA COUSUB');

  db.close();
  await pg.end();

  const stat = fs.statSync(ASSET_PATH);
  const mb = stat.size / (1024 * 1024);
  const dt = ((Date.now() - t0) / 1000).toFixed(1);
  console.log(`\nBAKE COMPLETE in ${dt}s`);
  console.log(`  asset: ${ASSET_PATH}`);
  console.log(`  size:  ${mb.toFixed(2)} MB (raw geom bytes: ${(totalGeomBytes/1024).toFixed(0)} KB)`);
  console.log(`  rows:  ${counts.city} city + ${counts.buffer} buffer + ${counts.town} town + ${counts.county} county = ${counts.city + counts.buffer + counts.town + counts.county}`);
  if (mb > 3.0) {
    console.log(`  ⚠ over the 3 MB threshold — consider increasing TOL_COUNTY`);
  }
}

main().catch(err => { console.error(err); process.exit(1); });
