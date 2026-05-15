#!/usr/bin/env node
/*
 * publish-poi-passport.js — S268
 *
 * Bakes the canonical POI Passport lists for each filter row in
 * salem_passport_filters. Writes results to:
 *   - PG salem_passport_pois (always, used by web admin for preview/diff)
 *   - SQLite poi_passport table in salem_content.db (only if the table
 *     exists in the current Room schema — activates automatically once
 *     Session 2 lands Room v20)
 *
 * Slots into the canonical publish chain:
 *   publish-salem-pois.js → publish-tours.js → publish-tour-legs.js →
 *   publish-poi-passport.js → align-asset-schema-to-room.js
 *
 * Filter SQL semantics are owned by cache-proxy/lib/admin-passport.js
 * (buildFilterQuery). This script imports that helper so admin "preview"
 * and bake stay in lock-step.
 *
 * Usage:
 *   node cache-proxy/scripts/publish-poi-passport.js
 *   node cache-proxy/scripts/publish-poi-passport.js --dry-run
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

const { buildFilterQuery } = require('../lib/admin-passport');

const DRY_RUN = process.argv.includes('--dry-run');
const ASSETS_PATH = path.resolve(__dirname, '../../app-salem/src/main/assets/salem_content.db');
const SQLITE_PATH = ASSETS_PATH;

// Read current Room identity_hash + version from the latest schema JSON
// (matches publish-tour-legs.js:51-70 pattern). Don't hardcode.
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

async function fetchFilters(client) {
  const { rows } = await client.query(
    `SELECT id, name, tour_id, categories, require_historical_narration,
            min_geofence_radius_m, min_year_established, max_year_established,
            sort_order
       FROM salem_passport_filters
   ORDER BY sort_order ASC, name ASC`,
  );
  return rows;
}

async function matchPois(client, filter) {
  const { whereSql, joinSql, args } = buildFilterQuery(filter);
  const sql = `
    SELECT p.id, p.name, p.category, p.lat, p.lng
      FROM salem_pois p
      ${joinSql}
     WHERE ${whereSql}
  ORDER BY p.name ASC
  `;
  const { rows } = await client.query(sql, args);
  return rows;
}

async function bakeToPg(client, filter, pois) {
  await client.query(`DELETE FROM salem_passport_pois WHERE filter_id = $1`, [filter.id]);
  if (!pois.length) return 0;
  // Bulk insert. Build VALUES list with three params per row.
  const rowsSql = [];
  const args = [];
  let i = 1;
  let order = 0;
  for (const p of pois) {
    rowsSql.push(`($${i++}, $${i++}, $${i++})`);
    args.push(filter.id, p.id, order++);
  }
  await client.query(
    `INSERT INTO salem_passport_pois (filter_id, poi_id, display_order)
     VALUES ${rowsSql.join(', ')}`,
    args,
  );
  return pois.length;
}

function sqliteHasTable(db, name) {
  const row = db
    .prepare("SELECT name FROM sqlite_master WHERE type='table' AND name=?")
    .get(name);
  return !!row;
}

function bakeToSqlite(db, filtersWithPois) {
  // Schema mirrors what the Room v20 PoiPassport entity will generate. Until
  // Session 2 bumps Room and writes the entity, the table won't exist in the
  // baked asset — we gracefully skip the SQLite write in that case.
  if (!sqliteHasTable(db, 'poi_passport')) {
    return {
      skipped: true,
      reason: 'poi_passport table not present in salem_content.db (Room v19 — Session 2 adds it)',
    };
  }

  const del = db.prepare('DELETE FROM poi_passport').run();
  const insert = db.prepare(`
    INSERT INTO poi_passport (
      passport_id, passport_name, tour_id, poi_id, display_order,
      poi_name, poi_lat, poi_lng, poi_category
    ) VALUES (
      @passport_id, @passport_name, @tour_id, @poi_id, @display_order,
      @poi_name, @poi_lat, @poi_lng, @poi_category
    )
  `);

  let inserted = 0;
  const txn = db.transaction(() => {
    for (const { filter, pois } of filtersWithPois) {
      let order = 0;
      for (const p of pois) {
        insert.run({
          passport_id: filter.id,
          passport_name: filter.name,
          tour_id: filter.tour_id ?? null,
          poi_id: p.id,
          display_order: order++,
          poi_name: p.name,
          poi_lat: p.lat,
          poi_lng: p.lng,
          poi_category: p.category,
        });
        inserted += 1;
      }
    }
  });
  txn();
  return { skipped: false, cleared: del.changes, inserted };
}

async function main() {
  console.log(`\n=== Publish POI Passport (PG → SQLite) ===`);
  console.log(`Mode:   ${DRY_RUN ? 'DRY RUN' : 'LIVE'}`);
  console.log(`SQLite: ${SQLITE_PATH}\n`);

  const client = await pool.connect();
  let filters;
  const filtersWithPois = [];
  try {
    filters = await fetchFilters(client);
    console.log(`PG: ${filters.length} passport filter(s)`);
    if (!filters.length) {
      console.log('No filters to bake. Done.');
      await pool.end();
      return;
    }

    for (const f of filters) {
      const pois = await matchPois(client, f);
      filtersWithPois.push({ filter: f, pois });
      console.log(
        `  ${f.id.padEnd(30)} ${pois.length.toString().padStart(4)} POIs` +
        (f.tour_id ? `  (tour: ${f.tour_id})` : '  (global)'),
      );
    }

    if (DRY_RUN) {
      console.log('\nDRY RUN — no writes.');
      await pool.end();
      return;
    }

    // Write to PG (always)
    await client.query('BEGIN');
    let pgTotal = 0;
    for (const { filter, pois } of filtersWithPois) {
      const n = await bakeToPg(client, filter, pois);
      pgTotal += n;
    }
    await client.query('COMMIT');
    console.log(`\nPG salem_passport_pois: ${pgTotal} rows written across ${filters.length} filter(s)`);
  } catch (err) {
    try { await client.query('ROLLBACK'); } catch (_) {}
    throw err;
  } finally {
    client.release();
  }

  // Write to SQLite (only if the table exists in the current Room schema)
  if (!fs.existsSync(SQLITE_PATH)) {
    console.warn(`\nWARNING: SQLite file not found at ${SQLITE_PATH} — skipping asset bake`);
  } else {
    const db = new Database(SQLITE_PATH);
    try {
      const result = bakeToSqlite(db, filtersWithPois);
      if (result.skipped) {
        console.log(`\nSQLite poi_passport: SKIPPED — ${result.reason}`);
        console.log('(This is expected during S268 Session 1. Session 2 bumps Room v20 and activates this path.)');
      } else {
        console.log(`\nSQLite poi_passport: cleared ${result.cleared}, inserted ${result.inserted}`);
        // Stamp the Room identity_hash + user_version from the latest schema JSON.
        // Matches publish-tour-legs.js convention (keeps script in sync with
        // @Database(version) bumps).
        db.prepare('UPDATE room_master_table SET identity_hash = ? WHERE id = 42').run(ROOM_IDENTITY.hash);
        db.pragma(`user_version = ${ROOM_IDENTITY.version}`);
        console.log(`Stamped Room identity_hash → ${ROOM_IDENTITY.hash} (v${ROOM_IDENTITY.version})`);
      }
    } finally {
      db.close();
    }
  }

  console.log('\nPUBLISH POI PASSPORT COMPLETE');
  await pool.end();
}

main().catch((e) => {
  console.error('Publish failed:', e.message);
  process.exit(1);
});
