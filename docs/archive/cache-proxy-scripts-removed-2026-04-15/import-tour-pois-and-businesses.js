#!/usr/bin/env node
/*
 * LocationMapApp v1.5 — Phase 9P.A.3 (Session 101)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * One-shot importer: brings tour_pois and salem_businesses from the
 * bundled salem-content/salem_content.db into PostgreSQL.
 *
 * Why: after Phase 9P.4 the admin write endpoints exist for all three POI
 * kinds, but the PG copies of salem_tour_pois and salem_businesses are
 * empty (only narration_points was migrated in S98). That meant any admin
 * PUT/DELETE/move on a tour or business POI returned 404. This script
 * mirrors the S98 narration migration for the other two tables. After it
 * runs, PostgreSQL is canonical for all three POI kinds and the admin tool
 * is functional end-to-end.
 *
 * Source: salem-content/salem_content.db (SQLite — the most authoritative
 * current snapshot, used by the Android app and verified working).
 *
 * Type conversions:
 *   - JSON TEXT fields  → JSONB (parse + ::jsonb cast)
 *   - INTEGER 0/1       → BOOLEAN (requires_transportation, wheelchair_accessible, seasonal)
 *   - INTEGER Unix ms   → TIMESTAMPTZ (0 → NULL for stale_after)
 *   - TEXT YYYY-MM-DD   → TIMESTAMPTZ (PG parses directly)
 *
 * Idempotent: UPSERT by id. Re-running updates existing rows.
 *
 * Usage:
 *   node cache-proxy/scripts/import-tour-pois-and-businesses.js
 *
 * Reads DATABASE_URL from cache-proxy/.env automatically (via the
 * restart-proxy.sh-style env load) OR from process.env.
 */

const path = require('path');
const fs = require('fs');
const Database = require('better-sqlite3');
const { Client } = require('pg');

const PROJECT_ROOT = path.resolve(__dirname, '..', '..');
const BUNDLED_DB = path.join(PROJECT_ROOT, 'salem-content', 'salem_content.db');
const ENV_FILE = path.join(PROJECT_ROOT, 'cache-proxy', '.env');

// ── Load env vars from cache-proxy/.env if not already set ──────────────────
if (!process.env.DATABASE_URL && fs.existsSync(ENV_FILE)) {
  const lines = fs.readFileSync(ENV_FILE, 'utf8').split('\n');
  for (const line of lines) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eq = trimmed.indexOf('=');
    if (eq === -1) continue;
    const k = trimmed.slice(0, eq).trim();
    const v = trimmed.slice(eq + 1).trim();
    if (!process.env[k]) process.env[k] = v;
  }
}

// ── Type conversion helpers ─────────────────────────────────────────────────

function toTimestamp(epochMs) {
  if (epochMs === null || epochMs === undefined || epochMs === 0) return null;
  return new Date(Number(epochMs)).toISOString();
}

function toBoolean(v) {
  if (v === null || v === undefined) return null;
  return v === 1 || v === true || v === '1' || v === 'true';
}

function toJsonb(textValue, fallback) {
  if (textValue === null || textValue === undefined) return JSON.stringify(fallback);
  try {
    const parsed = JSON.parse(textValue);
    return JSON.stringify(parsed);
  } catch (_) {
    return JSON.stringify(fallback);
  }
}

// ── Tour POIs migration ─────────────────────────────────────────────────────

async function migrateTourPois(srcDb, pg) {
  console.log('\n=== Migrating tour_pois ===');
  const rows = srcDb.prepare('SELECT * FROM tour_pois ORDER BY id').all();
  console.log(`  source rows: ${rows.length}`);

  // Use UPSERT (ON CONFLICT DO UPDATE) instead of TRUNCATE + INSERT.
  // Six tables reference salem_tour_pois via FK (the historical FK chain
  // backfilled in Phase 9Q): salem_historical_figures, salem_historical_facts,
  // salem_timeline_events, salem_primary_sources, salem_tour_stops,
  // salem_events_calendar. Even though those references are currently NULL,
  // PG refuses to TRUNCATE a referenced table without CASCADE.
  // UPSERT avoids the issue entirely and is fully idempotent.
  await pg.query('BEGIN');
  try {
    const insertSql = `
      INSERT INTO salem_tour_pois (
        id, name, lat, lng, address, category, subcategories,
        short_narration, long_narration, description, historical_period,
        admission_info, hours, phone, website, image_asset,
        geofence_radius_m, requires_transportation, wheelchair_accessible,
        seasonal, priority,
        data_source, confidence, verified_date,
        created_at, updated_at, stale_after,
        deleted_at
      ) VALUES (
        $1, $2, $3, $4, $5, $6, $7::jsonb,
        $8, $9, $10, $11,
        $12, $13, $14, $15, $16,
        $17, $18, $19,
        $20, $21,
        $22, $23, $24,
        $25, $26, $27,
        NULL
      )
      ON CONFLICT (id) DO UPDATE SET
        name = EXCLUDED.name, lat = EXCLUDED.lat, lng = EXCLUDED.lng,
        address = EXCLUDED.address, category = EXCLUDED.category,
        subcategories = EXCLUDED.subcategories,
        short_narration = EXCLUDED.short_narration, long_narration = EXCLUDED.long_narration,
        description = EXCLUDED.description, historical_period = EXCLUDED.historical_period,
        admission_info = EXCLUDED.admission_info, hours = EXCLUDED.hours,
        phone = EXCLUDED.phone, website = EXCLUDED.website, image_asset = EXCLUDED.image_asset,
        geofence_radius_m = EXCLUDED.geofence_radius_m,
        requires_transportation = EXCLUDED.requires_transportation,
        wheelchair_accessible = EXCLUDED.wheelchair_accessible,
        seasonal = EXCLUDED.seasonal, priority = EXCLUDED.priority,
        data_source = EXCLUDED.data_source, confidence = EXCLUDED.confidence,
        verified_date = EXCLUDED.verified_date,
        created_at = EXCLUDED.created_at, updated_at = EXCLUDED.updated_at,
        stale_after = EXCLUDED.stale_after
    `;

    let inserted = 0;
    for (const r of rows) {
      await pg.query(insertSql, [
        r.id, r.name, r.lat, r.lng, r.address, r.category,
        toJsonb(r.subcategories, []),
        r.short_narration, r.long_narration, r.description, r.historical_period,
        r.admission_info, r.hours, r.phone, r.website, r.image_asset,
        r.geofence_radius_m, toBoolean(r.requires_transportation),
        toBoolean(r.wheelchair_accessible), toBoolean(r.seasonal), r.priority,
        r.data_source, r.confidence, r.verified_date,
        toTimestamp(r.created_at), toTimestamp(r.updated_at), toTimestamp(r.stale_after),
      ]);
      inserted++;
    }

    await pg.query('COMMIT');
    console.log(`  ✓ inserted ${inserted} rows`);

    const { rows: verifyRows } = await pg.query('SELECT COUNT(*)::int AS cnt FROM salem_tour_pois');
    const verifyCount = verifyRows[0].cnt;
    if (verifyCount !== rows.length) {
      throw new Error(`Verification failed: source has ${rows.length}, target has ${verifyCount}`);
    }
    console.log(`  ✓ verified target count = ${verifyCount}`);

    // Sample row spot-check
    const sample = await pg.query(
      'SELECT id, name, category, jsonb_array_length(subcategories) AS subcat_count, '
      + 'wheelchair_accessible, priority, updated_at '
      + "FROM salem_tour_pois WHERE id = 'witch_trials_memorial'"
    );
    if (sample.rows.length) {
      console.log(`  ✓ sample: ${JSON.stringify(sample.rows[0])}`);
    }
  } catch (err) {
    await pg.query('ROLLBACK').catch(() => {});
    throw err;
  }
}

// ── Salem Businesses migration ──────────────────────────────────────────────

async function migrateSalemBusinesses(srcDb, pg) {
  console.log('\n=== Migrating salem_businesses ===');
  const rows = srcDb.prepare('SELECT * FROM salem_businesses ORDER BY id').all();
  console.log(`  source rows: ${rows.length}`);

  // No FK constraints reference salem_businesses, but using UPSERT here too
  // for consistency and idempotency.
  await pg.query('BEGIN');
  try {
    const insertSql = `
      INSERT INTO salem_businesses (
        id, name, lat, lng, address, business_type, cuisine_type,
        price_range, hours, phone, website, description, historical_note,
        tags, rating, image_asset,
        data_source, confidence, verified_date,
        created_at, updated_at, stale_after,
        deleted_at
      ) VALUES (
        $1, $2, $3, $4, $5, $6, $7,
        $8, $9, $10, $11, $12, $13,
        $14::jsonb, $15, $16,
        $17, $18, $19,
        $20, $21, $22,
        NULL
      )
      ON CONFLICT (id) DO UPDATE SET
        name = EXCLUDED.name, lat = EXCLUDED.lat, lng = EXCLUDED.lng,
        address = EXCLUDED.address, business_type = EXCLUDED.business_type,
        cuisine_type = EXCLUDED.cuisine_type, price_range = EXCLUDED.price_range,
        hours = EXCLUDED.hours, phone = EXCLUDED.phone, website = EXCLUDED.website,
        description = EXCLUDED.description, historical_note = EXCLUDED.historical_note,
        tags = EXCLUDED.tags, rating = EXCLUDED.rating, image_asset = EXCLUDED.image_asset,
        data_source = EXCLUDED.data_source, confidence = EXCLUDED.confidence,
        verified_date = EXCLUDED.verified_date,
        created_at = EXCLUDED.created_at, updated_at = EXCLUDED.updated_at,
        stale_after = EXCLUDED.stale_after
    `;

    let inserted = 0;
    for (const r of rows) {
      await pg.query(insertSql, [
        r.id, r.name, r.lat, r.lng, r.address, r.business_type, r.cuisine_type,
        r.price_range, r.hours, r.phone, r.website, r.description, r.historical_note,
        toJsonb(r.tags, []), r.rating, r.image_asset,
        r.data_source, r.confidence, r.verified_date,
        toTimestamp(r.created_at), toTimestamp(r.updated_at), toTimestamp(r.stale_after),
      ]);
      inserted++;
    }

    await pg.query('COMMIT');
    console.log(`  ✓ inserted ${inserted} rows`);

    const { rows: verifyRows } = await pg.query('SELECT COUNT(*)::int AS cnt FROM salem_businesses');
    const verifyCount = verifyRows[0].cnt;
    if (verifyCount !== rows.length) {
      throw new Error(`Verification failed: source has ${rows.length}, target has ${verifyCount}`);
    }
    console.log(`  ✓ verified target count = ${verifyCount}`);

    // Distinct business_type breakdown
    const types = await pg.query(
      'SELECT business_type, COUNT(*)::int AS cnt FROM salem_businesses '
      + 'GROUP BY business_type ORDER BY cnt DESC LIMIT 10'
    );
    console.log(`  ✓ top business_types:`);
    for (const t of types.rows) {
      console.log(`      ${t.business_type}: ${t.cnt}`);
    }

    // Sample row spot-check
    const sample = await pg.query(
      'SELECT id, name, business_type, jsonb_array_length(tags) AS tag_count, '
      + 'data_source, confidence '
      + "FROM salem_businesses WHERE id = 'hex'"
    );
    if (sample.rows.length) {
      console.log(`  ✓ sample: ${JSON.stringify(sample.rows[0])}`);
    }
  } catch (err) {
    await pg.query('ROLLBACK').catch(() => {});
    throw err;
  }
}

// ── Main ────────────────────────────────────────────────────────────────────

async function main() {
  if (!process.env.DATABASE_URL) {
    console.error('ERROR: DATABASE_URL not set (looked in env and cache-proxy/.env)');
    process.exit(1);
  }
  if (!fs.existsSync(BUNDLED_DB)) {
    console.error(`ERROR: bundled DB not found at ${BUNDLED_DB}`);
    process.exit(1);
  }

  console.log(`Source: ${BUNDLED_DB}`);
  console.log(`Target: ${process.env.DATABASE_URL.replace(/:[^:@]+@/, ':***@')}`);

  const srcDb = new Database(BUNDLED_DB, { readonly: true });
  const pg = new Client({ connectionString: process.env.DATABASE_URL });
  await pg.connect();

  try {
    await migrateTourPois(srcDb, pg);
    await migrateSalemBusinesses(srcDb, pg);

    // Cross-check totals
    const totals = await pg.query(`
      SELECT 'tour' AS kind, COUNT(*)::int AS cnt FROM salem_tour_pois WHERE deleted_at IS NULL
      UNION ALL
      SELECT 'business', COUNT(*)::int FROM salem_businesses WHERE deleted_at IS NULL
      UNION ALL
      SELECT 'narration', COUNT(*)::int FROM salem_narration_points WHERE deleted_at IS NULL
    `);
    console.log('\n=== Final PG state (active rows) ===');
    let total = 0;
    for (const r of totals.rows) {
      console.log(`  ${r.kind}: ${r.cnt}`);
      total += r.cnt;
    }
    console.log(`  TOTAL: ${total}`);
  } finally {
    await pg.end();
    srcDb.close();
  }
}

main().catch(err => {
  console.error('\nFATAL:', err.message);
  console.error(err.stack);
  process.exit(1);
});
