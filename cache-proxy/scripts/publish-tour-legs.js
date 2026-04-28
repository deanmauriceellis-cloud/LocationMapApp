#!/usr/bin/env node
/*
 * publish-tour-legs.js — S185
 *
 * Exports PG salem_tour_legs into the bundled Room salem_content.db
 * (table `tour_legs`). Sibling of publish-tours.js / publish-salem-pois.js.
 *
 * The S183/S184 admin tool (web/src/admin/AdminMap.tsx + cache-proxy/lib/admin-tours.js)
 * authors per-leg walking polylines into PG. This script lands those rows in
 * the asset DB so the runtime tour player draws them directly instead of
 * re-routing on device.
 *
 * Schema mapping PG → SQLite:
 *   PG salem_tour_legs (tour_id, leg_order, from_stop_id, to_stop_id,
 *                       polyline_json JSONB, distance_m, duration_s, ...)
 *   join salem_tour_stops on stop_id → produces stop_order + poi_id
 *   →
 *   SQLite tour_legs (tour_id, from_stop_order, to_stop_order, from_poi_id,
 *                     to_poi_id, distance_m, duration_s, edge_count,
 *                     geometry "lat,lng;lat,lng;...", + provenance)
 *
 * polyline_json (JSONB array of [lat,lng] pairs) is encoded as the same
 * "lat,lng;lat,lng;..." string the JVM ContentPipeline.writeSql emits, so
 * the Room TourLeg entity reads one consistent format regardless of source.
 *
 * Full replace: clears tour_legs then re-inserts from PG ordered by
 * (tour_id, leg_order).
 *
 * Usage:
 *   node scripts/publish-tour-legs.js
 *   node scripts/publish-tour-legs.js --dry-run
 */

const { Pool } = require('pg');
const path = require('path');
const fs = require('fs');

let Database;
try {
  Database = require('better-sqlite3');
} catch (_) {
  console.error('Error: better-sqlite3 not installed. Run: cd cache-proxy && npm install better-sqlite3');
  process.exit(1);
}

const DRY_RUN = process.argv.includes('--dry-run');
const SQLITE_PATH = path.resolve(__dirname, '../../salem-content/salem_content.db');
const ASSETS_PATH = path.resolve(__dirname, '../../app-salem/src/main/assets/salem_content.db');

// S193: read the current Room identity_hash + version from the latest schema
// JSON instead of hardcoding. The hardcoded v10 value caused S192/S193 to
// silently restamp v10 on top of v12/v13 aligned DBs, requiring a follow-up
// align-asset-schema run to recover. This script is no longer order-sensitive
// for the hash — the next align run will overwrite anyway, but the value
// stamped here is now correct for the schema actually compiled into the APK.
const SCHEMAS_DIR = path.resolve(
  __dirname,
  '../../app-salem/schemas/com.example.wickedsalemwitchcitytour.content.db.SalemContentDatabase'
);
function readLatestRoomIdentity() {
  const files = fs.readdirSync(SCHEMAS_DIR).filter((f) => /^\d+\.json$/.test(f));
  if (!files.length) {
    throw new Error(`No schema JSON in ${SCHEMAS_DIR}. Build with exportSchema=true first.`);
  }
  files.sort((a, b) => parseInt(b) - parseInt(a));
  const file = files[0];
  const data = JSON.parse(fs.readFileSync(path.join(SCHEMAS_DIR, file), 'utf8'));
  return { version: parseInt(file), hash: data.database.identityHash };
}
const ROOM_IDENTITY = readLatestRoomIdentity();

if (!process.env.DATABASE_URL) {
  try {
    const envPath = path.resolve(__dirname, '../.env');
    const envContent = fs.readFileSync(envPath, 'utf8');
    for (const line of envContent.split('\n')) {
      const t = line.trim();
      if (!t || t.startsWith('#')) continue;
      const eq = t.indexOf('=');
      if (eq === -1) continue;
      const k = t.slice(0, eq).trim();
      let v = t.slice(eq + 1).trim();
      if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
        v = v.slice(1, -1);
      }
      if (!process.env[k]) process.env[k] = v;
    }
  } catch (_) {}
}
if (!process.env.DATABASE_URL) {
  console.error('Error: DATABASE_URL is required');
  process.exit(1);
}

const pool = new Pool({ connectionString: process.env.DATABASE_URL });

function encodeGeometry(polylineJson) {
  if (!Array.isArray(polylineJson)) return '';
  const parts = [];
  for (const pt of polylineJson) {
    if (!Array.isArray(pt) || pt.length < 2) continue;
    parts.push(`${pt[0]},${pt[1]}`);
  }
  return parts.join(';');
}

async function main() {
  console.log(`\n=== Publish Tour Legs (PG → SQLite) ===`);
  console.log(`Mode:   ${DRY_RUN ? 'DRY RUN' : 'LIVE'}`);
  console.log(`SQLite: ${SQLITE_PATH}`);
  console.log(`Assets: ${ASSETS_PATH}\n`);

  const client = await pool.connect();
  let legs;
  try {
    // Join salem_tour_legs with salem_tour_stops twice (from/to) to convert
    // BIGINT stop_id surrogate keys into the (stop_order, poi_id) pair the
    // app's Room schema wants.
    // Resolve each waypoint's effective lat/lng. Free waypoints (poi_id NULL)
    // carry their own lat/lng on the stop row; POI-anchored waypoints inherit
    // from salem_pois. COALESCE handles either authoring style.
    const res = await client.query(`
      SELECT l.tour_id,
             l.leg_order,
             fs.stop_order AS from_stop_order,
             ts.stop_order AS to_stop_order,
             fs.poi_id     AS from_poi_id,
             ts.poi_id     AS to_poi_id,
             COALESCE(fs.lat, fp.lat) AS from_lat,
             COALESCE(fs.lng, fp.lng) AS from_lng,
             COALESCE(ts.lat, tp.lat) AS to_lat,
             COALESCE(ts.lng, tp.lng) AS to_lng,
             l.polyline_json,
             l.distance_m,
             l.duration_s,
             l.router_version
        FROM salem_tour_legs l
        JOIN salem_tour_stops fs ON fs.stop_id = l.from_stop_id
        JOIN salem_tour_stops ts ON ts.stop_id = l.to_stop_id
   LEFT JOIN salem_pois fp ON fp.id = fs.poi_id
   LEFT JOIN salem_pois tp ON tp.id = ts.poi_id
       ORDER BY l.tour_id, l.leg_order
    `);
    legs = res.rows;
    console.log(`PG: ${legs.length} tour_legs`);
    const byTour = new Map();
    for (const l of legs) byTour.set(l.tour_id, (byTour.get(l.tour_id) || 0) + 1);
    for (const [tid, n] of byTour) console.log(`  ${tid.padEnd(30)} ${n} legs`);
  } finally {
    client.release();
  }

  if (DRY_RUN) {
    console.log('\nDRY RUN — no writes.');
    await pool.end();
    return;
  }

  if (!fs.existsSync(SQLITE_PATH)) {
    console.error(`SQLite file not found: ${SQLITE_PATH}`);
    process.exit(1);
  }
  const db = new Database(SQLITE_PATH);

  // Create tour_legs if it doesn't already exist. Schema matches the JVM
  // ContentPipeline.writeSql() INSERT shape and the Room TourLeg @Entity.
  // Composite PK (tour_id, from_stop_order) since each leg starts from a
  // unique stop within a tour.
  db.exec(`
    -- Schema matches Room's generated CREATE TABLE for the TourLeg entity at v10
    -- (see app-salem/schemas/.../10.json). Room validates column types, nullability,
    -- and PK on open; deviating from this exact shape would crash the app at
    -- startup. DEFAULT clauses intentionally omitted — Room doesn't emit them.
    CREATE TABLE IF NOT EXISTS tour_legs (
      tour_id           TEXT    NOT NULL,
      from_stop_order   INTEGER NOT NULL,
      to_stop_order     INTEGER NOT NULL,
      from_poi_id       TEXT,
      to_poi_id         TEXT,
      from_lat          REAL,
      from_lng          REAL,
      to_lat            REAL,
      to_lng            REAL,
      distance_m        REAL    NOT NULL,
      duration_s        REAL    NOT NULL,
      edge_count        INTEGER NOT NULL,
      geometry          TEXT    NOT NULL,
      data_source       TEXT    NOT NULL,
      confidence        REAL    NOT NULL,
      created_at        INTEGER NOT NULL,
      updated_at        INTEGER NOT NULL,
      stale_after       INTEGER NOT NULL,
      PRIMARY KEY (tour_id, from_stop_order)
    );
  `);

  const legsDel = db.prepare('DELETE FROM tour_legs').run();
  console.log(`Cleared ${legsDel.changes} tour_legs from SQLite`);

  const insertLeg = db.prepare(`
    INSERT INTO tour_legs (
      tour_id, from_stop_order, to_stop_order, from_poi_id, to_poi_id,
      from_lat, from_lng, to_lat, to_lng,
      distance_m, duration_s, edge_count, geometry,
      data_source, confidence, created_at, updated_at, stale_after
    ) VALUES (
      @tour_id, @from_stop_order, @to_stop_order, @from_poi_id, @to_poi_id,
      @from_lat, @from_lng, @to_lat, @to_lng,
      @distance_m, @duration_s, @edge_count, @geometry,
      @data_source, @confidence, 0, 0, 0
    )
  `);

  const insertAll = db.transaction(() => {
    for (const l of legs) {
      const geometry = encodeGeometry(l.polyline_json);
      const edgeCount = Array.isArray(l.polyline_json) ? Math.max(0, l.polyline_json.length - 1) : 0;
      insertLeg.run({
        tour_id: l.tour_id,
        from_stop_order: l.from_stop_order,
        to_stop_order: l.to_stop_order,
        from_poi_id: l.from_poi_id,
        to_poi_id: l.to_poi_id,
        from_lat: l.from_lat,
        from_lng: l.from_lng,
        to_lat: l.to_lat,
        to_lng: l.to_lng,
        distance_m: l.distance_m,
        duration_s: l.duration_s,
        edge_count: edgeCount,
        geometry,
        data_source: l.router_version ? `pg_admin_${l.router_version}` : 'pg_admin_curated',
        confidence: 1.0,
      });
    }
  });
  insertAll();

  const legCount = db.prepare('SELECT COUNT(*) as c FROM tour_legs').get().c;
  console.log(`\nSQLite verification: ${legCount} tour_legs`);

  // Stamp the current Room identity_hash + user_version (read from the latest
  // schema JSON at startup). Keeps this script in sync with @Database(version)
  // bumps — no more drift like the S192/S193 v10 hardcode.
  db.prepare('UPDATE room_master_table SET identity_hash = ? WHERE id = 42').run(ROOM_IDENTITY.hash);
  db.pragma(`user_version = ${ROOM_IDENTITY.version}`);
  console.log(`Stamped Room identity_hash → ${ROOM_IDENTITY.hash} (v${ROOM_IDENTITY.version})`);

  db.close();

  fs.copyFileSync(SQLITE_PATH, ASSETS_PATH);
  const size = fs.statSync(ASSETS_PATH).size;
  console.log(`Copied to assets (${(size / 1024 / 1024).toFixed(1)} MB)`);
  console.log('\nPUBLISH TOUR LEGS COMPLETE');

  await pool.end();
}

main().catch((e) => {
  console.error('Publish failed:', e.message);
  process.exit(1);
});
