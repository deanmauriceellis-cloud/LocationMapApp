#!/usr/bin/env node
/*
 * bundle-witch-trials-newspapers-into-db.js — Phase 9X.4
 *
 * Reads the 202 active Salem 1692-era newspaper articles from PG
 * (salem_witch_trials_newspapers, consolidated from salem_newspapers_1692
 * in S130) and bakes them directly into the bundled salem_content.db.
 *
 * Same rationale as bundle-witch-trials-into-db.js (S129): baking rows
 * into the asset DB at build-time is more reliable than the assets/JSON →
 * Room hydrate path, which was observed to silently drop inserts on a
 * prebuilt asset DB with a retrofitted room_master_table identity hash.
 *
 * Usage: node bundle-witch-trials-newspapers-into-db.js
 */
const { Pool } = require('pg');
const Database = require('better-sqlite3');
const path = require('path');
const fs = require('fs');

if (!process.env.DATABASE_URL) {
  try {
    const envContent = fs.readFileSync(path.resolve(__dirname, '../.env'), 'utf8');
    for (const line of envContent.split('\n')) {
      const t = line.trim();
      if (!t || t.startsWith('#')) continue;
      const eq = t.indexOf('=');
      if (eq === -1) continue;
      const k = t.slice(0, eq).trim();
      let v = t.slice(eq + 1).trim();
      if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
      if (!process.env[k]) process.env[k] = v;
    }
  } catch (_) {}
}

// S150 fix: bake into the CANONICAL source DB (salem-content/), then copy to
// assets. Previously only wrote to assets, which `publish-salem-pois.js` would
// silently clobber on its next run — the exact failure mode seen in the S149
// field-test log where `salem_witch_trials_newspapers` was missing from the
// shipped APK.
const SRC_DB = path.resolve(__dirname, '../../salem-content/salem_content.db');
const ASSETS_DB = path.resolve(__dirname, '../../app-salem/src/main/assets/salem_content.db');
const DB = SRC_DB;
// S159 Phase 9Y.2b — Room schema v8 → v9 identity hash (9 MassGIS/MHC columns added)
const ROOM_IDENTITY_V9 = '4ec9ae3528d8f55529cd6875c7b0adef';
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

(async () => {
  const { rows } = await pool.query(`
    SELECT id, date, day_of_week, long_date, crisis_phase, summary, lede,
           tts_full_text, events_referenced,
           event_count, fact_count, primary_source_count,
           data_source, confidence, verified_date, generator_model,
           headline, headline_summary
    FROM salem_witch_trials_newspapers
    WHERE deleted_at IS NULL
    ORDER BY date ASC
  `);

  if (rows.length !== 202) {
    console.warn(`WARN: expected 202 active newspapers, got ${rows.length}`);
  }

  const db = new Database(DB);
  db.pragma('journal_mode = DELETE');

  // ── S150 fix: create the table if missing (the `publish-salem-pois.js`
  // publish loop rebuilds salem_pois but relies on other tables being present;
  // if the source DB was regenerated from scratch the newspaper table is gone,
  // producing the SQLITE_ERROR: no such table seen in the S149 field log). ──
  db.exec(`
    CREATE TABLE IF NOT EXISTS salem_witch_trials_newspapers (
      id TEXT NOT NULL PRIMARY KEY,
      date TEXT NOT NULL,
      day_of_week TEXT,
      long_date TEXT,
      crisis_phase INTEGER NOT NULL,
      summary TEXT,
      lede TEXT,
      tts_full_text TEXT NOT NULL,
      events_referenced TEXT NOT NULL,
      event_count INTEGER NOT NULL,
      fact_count INTEGER NOT NULL,
      primary_source_count INTEGER NOT NULL,
      data_source TEXT NOT NULL,
      confidence REAL NOT NULL,
      verified_date TEXT,
      generator_model TEXT,
      headline TEXT,
      headline_summary TEXT
    )
  `);

  // ── Schema patch: ensure the two new columns exist (idempotent for
  // pre-existing DBs created before S130). ──
  const cols = db.prepare("PRAGMA table_info(salem_witch_trials_newspapers)").all().map(r => r.name);
  if (!cols.includes('headline')) {
    db.exec("ALTER TABLE salem_witch_trials_newspapers ADD COLUMN headline TEXT");
    console.log('Schema: added headline column');
  }
  if (!cols.includes('headline_summary')) {
    db.exec("ALTER TABLE salem_witch_trials_newspapers ADD COLUMN headline_summary TEXT");
    console.log('Schema: added headline_summary column');
  }

  // ── Update room_master_table identity hash to v9 ──
  db.prepare("INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)").run(ROOM_IDENTITY_V9);
  console.log(`Schema: room_master_table identity_hash set to ${ROOM_IDENTITY_V9}`);
  const insert = db.prepare(`
    INSERT OR REPLACE INTO salem_witch_trials_newspapers
      (id, date, day_of_week, long_date, crisis_phase, summary, lede,
       tts_full_text, events_referenced,
       event_count, fact_count, primary_source_count,
       data_source, confidence, verified_date, generator_model,
       headline, headline_summary)
    VALUES (@id, @date, @day_of_week, @long_date, @crisis_phase, @summary, @lede,
            @tts_full_text, @events_referenced,
            @event_count, @fact_count, @primary_source_count,
            @data_source, @confidence, @verified_date, @generator_model,
            @headline, @headline_summary)
  `);
  const tx = db.transaction((papers) => {
    for (const p of papers) insert.run(p);
  });
  tx(rows.map((r) => ({
    id: r.id,
    date: r.date,
    day_of_week: r.day_of_week,
    long_date: r.long_date,
    crisis_phase: r.crisis_phase,
    summary: r.summary,
    lede: r.lede,
    tts_full_text: r.tts_full_text,
    events_referenced: JSON.stringify(r.events_referenced || []),
    event_count: r.event_count || 0,
    fact_count: r.fact_count || 0,
    primary_source_count: r.primary_source_count || 0,
    data_source: r.data_source,
    confidence: r.confidence,
    verified_date: r.verified_date ? r.verified_date.toISOString() : null,
    generator_model: r.generator_model,
    headline: r.headline,
    headline_summary: r.headline_summary,
  })));
  const count = db.prepare('SELECT COUNT(*) AS c FROM salem_witch_trials_newspapers').get().c;
  db.exec('VACUUM');
  db.close();
  // S150: mirror the source DB to the APK assets so the next build picks up
  // the newspaper table without requiring a separate publish-salem-pois run.
  fs.copyFileSync(SRC_DB, ASSETS_DB);
  console.log(`Bundled ${rows.length} newspapers into ${SRC_DB}. Post-insert COUNT=${count}.`);
  console.log(`Mirrored → ${ASSETS_DB}`);
  await pool.end();
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });
