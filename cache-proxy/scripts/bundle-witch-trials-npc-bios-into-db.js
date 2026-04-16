#!/usr/bin/env node
/*
 * bundle-witch-trials-npc-bios-into-db.js — Phase 9X.5
 *
 * Reads the 49 active Salem Witch Trials NPC bios from PG and bakes them
 * into the bundled salem_content.db. Schema already exists in the asset
 * DB from the S129 v7 bake; we simply populate rows with INSERT OR REPLACE.
 *
 * Room schema is still v8 (newspapers added headline + headline_summary
 * in S130). The npc_bios table structure hasn't changed from v7, so no
 * identity-hash bump is needed for this session — we leave the existing
 * hash in place.
 *
 * Usage: node bundle-witch-trials-npc-bios-into-db.js
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

const DB = path.resolve(__dirname, '../../app-salem/src/main/assets/salem_content.db');
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

(async () => {
  const { rows } = await pool.query(`
    SELECT id, name, display_name, tier, role, faction,
           born_year, died_year, age_in_1692, historical_outcome,
           bio, related_npc_ids, related_event_ids, related_newspaper_dates,
           portrait_asset, data_source, confidence, verified_date, generator_model
    FROM salem_witch_trials_npc_bios
    WHERE deleted_at IS NULL
    ORDER BY tier ASC, name ASC
  `);

  if (rows.length !== 49) {
    console.warn(`WARN: expected 49 active npc bios, got ${rows.length}`);
  }

  const db = new Database(DB);
  db.pragma('journal_mode = DELETE');

  const insert = db.prepare(`
    INSERT OR REPLACE INTO salem_witch_trials_npc_bios
      (id, name, display_name, tier, role, faction,
       born_year, died_year, age_in_1692, historical_outcome,
       bio, related_npc_ids, related_event_ids, related_newspaper_dates,
       portrait_asset, data_source, confidence, verified_date, generator_model)
    VALUES (@id, @name, @display_name, @tier, @role, @faction,
            @born_year, @died_year, @age_in_1692, @historical_outcome,
            @bio, @related_npc_ids, @related_event_ids, @related_newspaper_dates,
            @portrait_asset, @data_source, @confidence, @verified_date, @generator_model)
  `);
  const tx = db.transaction((bios) => {
    for (const b of bios) insert.run(b);
  });
  tx(rows.map((r) => ({
    id: r.id,
    name: r.name,
    display_name: r.display_name,
    tier: r.tier,
    role: r.role,
    faction: r.faction,
    born_year: r.born_year,
    died_year: r.died_year,
    age_in_1692: r.age_in_1692,
    historical_outcome: r.historical_outcome,
    bio: r.bio,
    related_npc_ids: JSON.stringify(r.related_npc_ids || []),
    related_event_ids: JSON.stringify(r.related_event_ids || []),
    related_newspaper_dates: JSON.stringify(r.related_newspaper_dates || []),
    portrait_asset: r.portrait_asset,
    data_source: r.data_source,
    confidence: r.confidence,
    verified_date: r.verified_date ? r.verified_date.toISOString() : null,
    generator_model: r.generator_model,
  })));
  const count = db.prepare('SELECT COUNT(*) AS c FROM salem_witch_trials_npc_bios').get().c;
  db.exec('VACUUM');
  db.close();
  console.log(`Bundled ${rows.length} bios into ${DB}. Post-insert COUNT=${count}.`);
  await pool.end();
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });
