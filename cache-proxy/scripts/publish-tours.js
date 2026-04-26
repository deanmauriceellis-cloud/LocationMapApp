#!/usr/bin/env node
/*
 * publish-tours.js — Phase 9R.0 Heritage Trail
 *
 * Exports PG salem_tours + salem_tour_stops into the bundled Room
 * salem_content.db (tables `tours` and `tour_stops`). Parallel to
 * publish-salem-pois.js but scoped to the tour tables.
 *
 * Full replace: clears both Room tables then re-inserts from PG in sort order.
 *
 * Usage:
 *   node scripts/publish-tours.js
 *   node scripts/publish-tours.js --dry-run
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

async function main() {
  console.log(`\n=== Publish Tours (PG → SQLite) ===`);
  console.log(`Mode:   ${DRY_RUN ? 'DRY RUN' : 'LIVE'}`);
  console.log(`SQLite: ${SQLITE_PATH}`);
  console.log(`Assets: ${ASSETS_PATH}\n`);

  const client = await pool.connect();
  let tours, stops;
  try {
    const toursRes = await client.query(`
      SELECT id, name, theme, description, estimated_minutes, distance_km,
             stop_count, difficulty, seasonal, icon_asset, sort_order,
             data_source, confidence,
             to_char(verified_date, 'YYYY-MM-DD') AS verified_date
      FROM salem_tours
      ORDER BY sort_order, name
    `);
    tours = toursRes.rows;
    // S185: skip rows whose poi_id is NULL (free waypoints — internal
    // authoring data for the admin walking-route tool, not user-facing
    // stops). Tours are polyline-only at runtime; narration is driven by
    // POI geofences independently of tour_stops.
    const stopsRes = await client.query(`
      SELECT tour_id, poi_id, stop_order, transition_narration,
             walking_minutes_from_prev, distance_m_from_prev,
             data_source, confidence
      FROM salem_tour_stops
      WHERE poi_id IS NOT NULL
      ORDER BY tour_id, stop_order
    `);
    stops = stopsRes.rows;
    console.log(`PG: ${tours.length} tours, ${stops.length} tour_stops`);
    for (const t of tours) {
      const n = stops.filter((s) => s.tour_id === t.id).length;
      console.log(`  ${t.id.padEnd(30)} ${t.theme.padEnd(24)} ${n} stops`);
    }
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

  // Clear existing — full replace semantics
  const toursDel = db.prepare('DELETE FROM tours').run();
  const stopsDel = db.prepare('DELETE FROM tour_stops').run();
  console.log(`Cleared ${toursDel.changes} tours + ${stopsDel.changes} tour_stops from SQLite`);

  const insertTour = db.prepare(`
    INSERT INTO tours (
      id, name, theme, description, estimated_minutes, distance_km,
      stop_count, difficulty, seasonal, icon_asset, sort_order,
      data_source, confidence, verified_date, created_at, updated_at, stale_after
    ) VALUES (
      @id, @name, @theme, @description, @estimated_minutes, @distance_km,
      @stop_count, @difficulty, @seasonal, @icon_asset, @sort_order,
      @data_source, @confidence, @verified_date, 0, 0, 0
    )
  `);
  const insertStop = db.prepare(`
    INSERT INTO tour_stops (
      tour_id, poi_id, stop_order, transition_narration,
      walking_minutes_from_prev, distance_m_from_prev,
      data_source, confidence, created_at, updated_at, stale_after
    ) VALUES (
      @tour_id, @poi_id, @stop_order, @transition_narration,
      @walking_minutes_from_prev, @distance_m_from_prev,
      @data_source, @confidence, 0, 0, 0
    )
  `);

  const insertAll = db.transaction(() => {
    for (const t of tours) {
      insertTour.run({
        id: t.id,
        name: t.name,
        theme: t.theme,
        description: t.description,
        estimated_minutes: t.estimated_minutes,
        distance_km: t.distance_km,
        stop_count: t.stop_count,
        difficulty: t.difficulty || 'moderate',
        seasonal: t.seasonal ? 1 : 0,
        icon_asset: t.icon_asset || null,
        sort_order: t.sort_order || 0,
        data_source: t.data_source || 'manual_curated',
        confidence: t.confidence != null ? t.confidence : 1.0,
        verified_date: t.verified_date || null,
      });
    }
    for (const s of stops) {
      insertStop.run({
        tour_id: s.tour_id,
        poi_id: s.poi_id,
        stop_order: s.stop_order,
        transition_narration: s.transition_narration || null,
        walking_minutes_from_prev: s.walking_minutes_from_prev != null ? s.walking_minutes_from_prev : null,
        distance_m_from_prev: s.distance_m_from_prev != null ? s.distance_m_from_prev : null,
        data_source: s.data_source || 'manual_curated',
        confidence: s.confidence != null ? s.confidence : 1.0,
      });
    }
  });
  insertAll();

  const tourCount = db.prepare('SELECT COUNT(*) as c FROM tours').get().c;
  const stopCount = db.prepare('SELECT COUNT(*) as c FROM tour_stops').get().c;
  console.log(`\nSQLite verification: ${tourCount} tours, ${stopCount} tour_stops`);

  db.close();

  fs.copyFileSync(SQLITE_PATH, ASSETS_PATH);
  const size = fs.statSync(ASSETS_PATH).size;
  console.log(`Copied to assets (${(size / 1024 / 1024).toFixed(1)} MB)`);
  console.log('\nPUBLISH TOURS COMPLETE');

  await pool.end();
}

main().catch((e) => {
  console.error('Publish failed:', e.message);
  process.exit(1);
});
