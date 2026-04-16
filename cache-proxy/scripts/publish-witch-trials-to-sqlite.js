#!/usr/bin/env node
/**
 * publish-witch-trials-to-sqlite.js — S134
 * Exports witch trials tables from PG into the bundled Room SQLite.
 */
const { Pool } = require('pg');
const path = require('path');
const fs = require('fs');
let Database;
try { Database = require('better-sqlite3'); } catch (_) {
  console.error('Error: better-sqlite3 not installed'); process.exit(1);
}

const envPath = path.resolve(__dirname, '../.env');
try {
  const envContent = fs.readFileSync(envPath, 'utf8');
  for (const line of envContent.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const eqIdx = trimmed.indexOf('=');
    if (eqIdx === -1) continue;
    const key = trimmed.slice(0, eqIdx).trim();
    let val = trimmed.slice(eqIdx + 1).trim();
    if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'")))
      val = val.slice(1, -1);
    if (!process.env[key]) process.env[key] = val;
  }
} catch (_) {}

const SQLITE_PATH = path.resolve(__dirname, '../../app-salem/src/main/assets/salem_content.db');
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

async function main() {
  const db = new Database(SQLITE_PATH);

  // --- Create tables if missing (publish-salem-pois.js rebuilds the DB from scratch) ---
  // Schema must match Room @Entity definitions EXACTLY: no DEFAULT clauses,
  // correct NOT NULL. Room validates on open and crashes on any mismatch.
  db.exec(`
    CREATE TABLE IF NOT EXISTS salem_witch_trials_newspapers (
      id TEXT NOT NULL PRIMARY KEY,
      date TEXT NOT NULL,
      day_of_week TEXT,
      long_date TEXT,
      crisis_phase INTEGER NOT NULL,
      summary TEXT,
      lede TEXT,
      body_points TEXT NOT NULL,
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
    );
    CREATE TABLE IF NOT EXISTS salem_witch_trials_npc_bios (
      id TEXT NOT NULL PRIMARY KEY,
      name TEXT NOT NULL,
      display_name TEXT,
      tier INTEGER NOT NULL,
      role TEXT NOT NULL,
      faction TEXT,
      born_year INTEGER,
      died_year INTEGER,
      age_in_1692 INTEGER,
      historical_outcome TEXT,
      bio TEXT NOT NULL,
      related_npc_ids TEXT NOT NULL,
      related_event_ids TEXT NOT NULL,
      related_newspaper_dates TEXT NOT NULL,
      portrait_asset TEXT,
      data_source TEXT NOT NULL,
      confidence REAL NOT NULL,
      verified_date TEXT,
      generator_model TEXT
    );
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
    );
  `);

  // --- Newspapers ---
  const { rows: papers } = await pool.query('SELECT * FROM salem_witch_trials_newspapers ORDER BY date');
  console.log(`PG newspapers: ${papers.length}`);
  db.exec('DELETE FROM salem_witch_trials_newspapers');
  const insNews = db.prepare(`INSERT INTO salem_witch_trials_newspapers
    (id,date,day_of_week,long_date,crisis_phase,summary,lede,body_points,tts_full_text,
     events_referenced,event_count,fact_count,primary_source_count,data_source,confidence,
     verified_date,generator_model,headline,headline_summary)
    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`);
  const insertPapers = db.transaction((rows) => {
    for (const r of rows) {
      insNews.run(r.id, r.date, r.day_of_week, r.long_date, r.crisis_phase,
        r.summary, r.lede,
        typeof r.body_points === 'object' ? JSON.stringify(r.body_points) : r.body_points,
        r.tts_full_text,
        typeof r.events_referenced === 'object' ? JSON.stringify(r.events_referenced) : r.events_referenced,
        r.event_count, r.fact_count, r.primary_source_count,
        r.data_source, r.confidence, r.verified_date, r.generator_model,
        r.headline, r.headline_summary);
    }
  });
  insertPapers(papers);
  console.log(`SQLite newspapers: ${db.prepare('SELECT COUNT(*) as c FROM salem_witch_trials_newspapers').get().c}`);

  // --- NPC Bios ---
  const { rows: bios } = await pool.query('SELECT * FROM salem_witch_trials_npc_bios ORDER BY name');
  console.log(`PG bios: ${bios.length}`);
  db.exec('DELETE FROM salem_witch_trials_npc_bios');
  const insBio = db.prepare(`INSERT INTO salem_witch_trials_npc_bios
    (id,name,display_name,tier,role,faction,born_year,died_year,age_in_1692,historical_outcome,
     bio,related_npc_ids,related_event_ids,related_newspaper_dates,portrait_asset,
     data_source,confidence,verified_date,generator_model)
    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`);
  // Safely convert any value to a SQLite-bindable type
  const s = v => {
    if (v == null) return null;
    if (typeof v === 'number' || typeof v === 'string' || typeof v === 'bigint') return v;
    if (v instanceof Date) return v.toISOString();
    if (typeof v === 'boolean') return v ? 1 : 0;
    if (Buffer.isBuffer(v)) return v;
    return JSON.stringify(v);
  };
  const insertBios = db.transaction((rows) => {
    for (const r of rows) {
      insBio.run(s(r.id), s(r.name), s(r.display_name), s(r.tier), s(r.role), s(r.faction),
        s(r.born_year), s(r.died_year), s(r.age_in_1692), s(r.historical_outcome), s(r.bio),
        s(r.related_npc_ids) || '[]',
        s(r.related_event_ids) || '[]',
        s(r.related_newspaper_dates) || '[]',
        s(r.portrait_asset),
        s(r.data_source), s(r.confidence), s(r.verified_date), s(r.generator_model));
    }
  });
  insertBios(bios);
  console.log(`SQLite bios: ${db.prepare('SELECT COUNT(*) as c FROM salem_witch_trials_npc_bios').get().c}`);

  // --- Articles ---
  const { rows: articles } = await pool.query('SELECT * FROM salem_witch_trials_articles ORDER BY tile_order');
  console.log(`PG articles: ${articles.length}`);
  db.exec('DELETE FROM salem_witch_trials_articles');
  const insArt = db.prepare(`INSERT INTO salem_witch_trials_articles
    (id,tile_order,tile_kind,title,period_label,teaser,body,
     related_npc_ids,related_event_ids,related_newspaper_dates,
     data_source,confidence,verified_date,generator_model)
    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)`);
  const insertArts = db.transaction((rows) => {
    for (const r of rows) {
      insArt.run(r.id, r.tile_order, r.tile_kind, r.title, r.period_label, r.teaser, r.body,
        s(r.related_npc_ids) || '[]',
        s(r.related_event_ids) || '[]',
        s(r.related_newspaper_dates) || '[]',
        r.data_source, r.confidence, r.verified_date, r.generator_model);
    }
  });
  insertArts(articles);
  console.log(`SQLite articles: ${db.prepare('SELECT COUNT(*) as c FROM salem_witch_trials_articles').get().c}`);

  db.close();
  await pool.end();
  console.log('DONE');
}

main().catch(e => { console.error(e); process.exit(1); });
