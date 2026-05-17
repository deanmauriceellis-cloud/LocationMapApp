#!/usr/bin/env node
/*
 * publish-poi-collection.js — S268
 *
 * Bakes the canonical Katrina's Collection lists for each filter row in
 * salem_collections. Writes results to:
 *   - PG salem_collection_entries (always, used by web admin for preview/diff)
 *   - SQLite collection_entry table in salem_content.db (only if the table
 *     exists in the current Room schema — activates automatically once
 *     Session 2 lands Room v20)
 *
 * Slots into the canonical publish chain:
 *   publish-salem-pois.js → publish-tours.js → publish-tour-legs.js →
 *   publish-poi-collection.js → align-asset-schema-to-room.js
 *
 * Filter SQL semantics are owned by cache-proxy/lib/admin-collection.js
 * (buildFilterQuery). This script imports that helper so admin "preview"
 * and bake stay in lock-step.
 *
 * Usage:
 *   node cache-proxy/scripts/publish-poi-collection.js
 *   node cache-proxy/scripts/publish-poi-collection.js --dry-run
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

const { buildFilterQuery } = require('../lib/admin-collection');

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
  // S269 — auto_bake gates whether the bake regenerates this filter's
  // salem_collection_entries rows from filter SQL (TRUE) or preserves whatever
  // the operator wrote there via the walk-derived dialog (FALSE). The
  // column is added by an idempotent ALTER in admin-collection.js module
  // init, so older PG snapshots that haven't been touched by the proxy
  // yet may not have it. COALESCE keeps the script backwards-compatible.
  const { rows } = await client.query(
    `SELECT id, name, tour_id, categories, require_historical_narration,
            min_geofence_radius_m, min_year_established, max_year_established,
            sort_order,
            COALESCE(auto_bake, TRUE) AS auto_bake
       FROM salem_collections
   ORDER BY sort_order ASC, name ASC`,
  );
  return rows;
}

async function loadExistingPois(client, filterId) {
  const { rows } = await client.query(
    `SELECT p.id, p.name, p.category, p.lat, p.lng,
            p.ghost_asset_a, p.ghost_asset_b, p.ghost_frame
       FROM salem_collection_entries pp
       JOIN salem_pois p ON p.id = pp.poi_id
      WHERE pp.collection_id = $1
   ORDER BY pp.display_order ASC`,
    [filterId],
  );
  return rows;
}

async function matchPois(client, filter) {
  const { whereSql, joinSql, args } = buildFilterQuery(filter);
  const sql = `
    SELECT p.id, p.name, p.category, p.lat, p.lng,
           p.ghost_asset_a, p.ghost_asset_b, p.ghost_frame
      FROM salem_pois p
      ${joinSql}
     WHERE ${whereSql}
  ORDER BY p.name ASC
  `;
  const { rows } = await client.query(sql, args);
  return rows;
}

async function bakeToPg(client, filter, pois) {
  await client.query(`DELETE FROM salem_collection_entries WHERE collection_id = $1`, [filter.id]);
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
    `INSERT INTO salem_collection_entries (collection_id, poi_id, display_order)
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
  // Schema mirrors what the Room v20 CollectionEntry entity will generate. Until
  // Session 2 bumps Room and writes the entity, the table won't exist in the
  // baked asset — we gracefully skip the SQLite write in that case.
  if (!sqliteHasTable(db, 'collection_entry')) {
    return {
      skipped: true,
      reason: 'collection_entry table not present in salem_content.db (Room v19 — Session 2 adds it)',
    };
  }

  const del = db.prepare('DELETE FROM collection_entry').run();
  const insert = db.prepare(`
    INSERT INTO collection_entry (
      collection_id, collection_name, tour_id, poi_id, display_order,
      poi_name, poi_lat, poi_lng, poi_category,
      ghost_asset_a, ghost_asset_b, ghost_frame
    ) VALUES (
      @collection_id, @collection_name, @tour_id, @poi_id, @display_order,
      @poi_name, @poi_lat, @poi_lng, @poi_category,
      @ghost_asset_a, @ghost_asset_b, @ghost_frame
    )
  `);

  let inserted = 0;
  const txn = db.transaction(() => {
    for (const { filter, pois } of filtersWithPois) {
      let order = 0;
      for (const p of pois) {
        insert.run({
          collection_id: filter.id,
          collection_name: filter.name,
          tour_id: filter.tour_id ?? null,
          poi_id: p.id,
          display_order: order++,
          poi_name: p.name,
          poi_lat: p.lat,
          poi_lng: p.lng,
          poi_category: p.category,
          // S275 denormalization: ghost paths live on salem_pois; copy onto each
          // collection_entry row so CollectionSheet renders from one table.
          ghost_asset_a: p.ghost_asset_a ?? null,
          ghost_asset_b: p.ghost_asset_b ?? null,
          ghost_frame:   p.ghost_frame   ?? null,
        });
        inserted += 1;
      }
    }
  });
  txn();
  return { skipped: false, cleared: del.changes, inserted };
}

async function main() {
  console.log(`\n=== Publish Katrina's Collection (PG → SQLite) ===`);
  console.log(`Mode:   ${DRY_RUN ? 'DRY RUN' : 'LIVE'}`);
  console.log(`SQLite: ${SQLITE_PATH}\n`);

  const client = await pool.connect();
  let filters;
  const filtersWithPois = [];
  try {
    filters = await fetchFilters(client);
    console.log(`PG: ${filters.length} collection filter(s)`);
    if (!filters.length) {
      console.log('No filters to bake. Done.');
      await pool.end();
      return;
    }

    for (const f of filters) {
      // S269 — auto_bake=false filters carry an operator-authored POI list
      // (walk-derived dialog). Read salem_collection_entries directly instead of
      // re-running the filter SQL, then skip the bakeToPg step for them so
      // the existing rows are preserved.
      let pois;
      let source;
      if (f.auto_bake === false) {
        pois = await loadExistingPois(client, f.id);
        source = 'manual';
      } else {
        pois = await matchPois(client, f);
        source = 'filter';
      }
      filtersWithPois.push({ filter: f, pois, source });
      console.log(
        `  ${f.id.padEnd(30)} ${pois.length.toString().padStart(4)} POIs` +
        `  [${source}]` +
        (f.tour_id ? `  (tour: ${f.tour_id})` : '  (global)'),
      );
    }

    if (DRY_RUN) {
      console.log('\nDRY RUN — no writes.');
      await pool.end();
      return;
    }

    // Write to PG (only for auto_bake=true filters; manual filters keep
    // whatever was authored via the walk-derived dialog).
    await client.query('BEGIN');
    let pgTotal = 0;
    for (const { filter, pois, source } of filtersWithPois) {
      if (source === 'manual') continue;
      const n = await bakeToPg(client, filter, pois);
      pgTotal += n;
    }
    await client.query('COMMIT');
    console.log(`\nPG salem_collection_entries: ${pgTotal} rows written across auto-bake filters (manual filters preserved)`);
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
        console.log(`\nSQLite collection_entry: SKIPPED — ${result.reason}`);
        console.log('(This is expected during S268 Session 1. Session 2 bumps Room v20 and activates this path.)');
      } else {
        console.log(`\nSQLite collection_entry: cleared ${result.cleared}, inserted ${result.inserted}`);
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

  console.log('\nPUBLISH POI COLLECTION COMPLETE');
  await pool.end();
}

main().catch((e) => {
  console.error('Publish failed:', e.message);
  process.exit(1);
});
