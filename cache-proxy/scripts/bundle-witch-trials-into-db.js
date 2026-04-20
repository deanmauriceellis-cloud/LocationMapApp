#!/usr/bin/env node
/*
 * bundle-witch-trials-into-db.js — Phase 9X.3
 *
 * Reads the 16 active Salem Witch Trials history articles from PG and
 * inserts them directly into the bundled salem_content.db. Bundling into
 * the asset DB means the app has the data on first launch with no Room
 * hydration step — simpler and more reliable than the assets/JSON → Room
 * path (which was observed to silently drop inserts on a prebuilt asset
 * DB with a retrofitted room_master_table identity hash).
 *
 * Usage: node bundle-witch-trials-into-db.js
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

// S153 fix: bake into the CANONICAL source DB, then mirror to assets. Previously
// this script wrote only to assets, which `publish-salem-pois.js` silently
// clobbers on its next run. Same bug/fix pattern as the S150 newspaper fix.
const SRC_DB = path.resolve(__dirname, '../../salem-content/salem_content.db');
const ASSETS_DB = path.resolve(__dirname, '../../app-salem/src/main/assets/salem_content.db');
const DB = SRC_DB;
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

(async () => {
  const { rows } = await pool.query(`
    SELECT id, tile_order, tile_kind, title, period_label, teaser, body,
           related_npc_ids, related_event_ids, related_newspaper_dates,
           data_source, confidence, verified_date, generator_model
    FROM salem_witch_trials_articles
    WHERE deleted_at IS NULL
    ORDER BY tile_order ASC
  `);

  if (rows.length !== 16) {
    console.warn(`WARN: expected 16 active articles, got ${rows.length}`);
  }

  const db = new Database(DB);
  db.pragma('journal_mode = DELETE');

  // S153 fix: create the table if missing. Schema must match Room @Entity
  // exactly. Mirrors publish-witch-trials-to-sqlite.js.
  db.exec(`
    CREATE TABLE IF NOT EXISTS salem_witch_trials_articles (
      id TEXT NOT NULL PRIMARY KEY,
      tile_order INTEGER NOT NULL,
      tile_kind TEXT NOT NULL,
      title TEXT NOT NULL,
      period_label TEXT,
      teaser TEXT NOT NULL,
      body TEXT NOT NULL,
      related_npc_ids TEXT NOT NULL,
      related_event_ids TEXT NOT NULL,
      related_newspaper_dates TEXT NOT NULL,
      data_source TEXT NOT NULL,
      confidence REAL NOT NULL,
      verified_date TEXT,
      generator_model TEXT
    )
  `);

  // Full-set replace: clear existing rows so id-convention changes don't
  // leave orphans (e.g. S140 migration from S128 snake_case ids to the
  // Oracle-native intro_pre_1692 / month_1692_NN convention).
  const preBundle = db.prepare('SELECT COUNT(*) AS c FROM salem_witch_trials_articles').get().c;
  db.exec('DELETE FROM salem_witch_trials_articles');
  const insert = db.prepare(`
    INSERT OR REPLACE INTO salem_witch_trials_articles
      (id, tile_order, tile_kind, title, period_label, teaser, body,
       related_npc_ids, related_event_ids, related_newspaper_dates,
       data_source, confidence, verified_date, generator_model)
    VALUES (@id, @tile_order, @tile_kind, @title, @period_label, @teaser, @body,
            @related_npc_ids, @related_event_ids, @related_newspaper_dates,
            @data_source, @confidence, @verified_date, @generator_model)
  `);
  const tx = db.transaction((articles) => {
    for (const a of articles) insert.run(a);
  });
  tx(rows.map((r) => ({
    id: r.id,
    tile_order: r.tile_order,
    tile_kind: r.tile_kind,
    title: r.title,
    period_label: r.period_label,
    teaser: r.teaser,
    body: r.body,
    related_npc_ids: JSON.stringify(r.related_npc_ids || []),
    related_event_ids: JSON.stringify(r.related_event_ids || []),
    related_newspaper_dates: JSON.stringify(r.related_newspaper_dates || []),
    data_source: r.data_source,
    confidence: r.confidence,
    verified_date: r.verified_date ? r.verified_date.toISOString() : null,
    generator_model: r.generator_model,
  })));
  const count = db.prepare('SELECT COUNT(*) AS c FROM salem_witch_trials_articles').get().c;
  db.exec('VACUUM');
  db.close();
  // S153: mirror source → assets so the APK build picks up the table.
  fs.copyFileSync(SRC_DB, ASSETS_DB);
  console.log(`Cleared ${preBundle} pre-existing rows. Bundled ${rows.length} articles into ${SRC_DB}. Post-insert COUNT=${count}.`);
  console.log(`Mirrored → ${ASSETS_DB}`);
  await pool.end();
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });
