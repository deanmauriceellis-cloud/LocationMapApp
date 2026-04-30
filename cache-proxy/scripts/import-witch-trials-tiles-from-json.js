#!/usr/bin/env node
/*
 * import-witch-trials-tiles-from-json.js — S199
 *
 * Reads the 16 Witch Trial History tile JSONs produced by gentiles
 *   ~/Development/Salem/data/json/oracle_tiles/<id>.json
 * and upserts them into PG `salem_witch_trials_articles`.
 *
 * Replaces import-witch-trials-tiles-from-intel.js (which read from
 * SalemIntelligence at :8089). Source of truth is now the on-disk JSON
 * regenerated directly by Salem Oracle.
 *
 * Idempotent. Safe to re-run.
 *
 * Usage: node import-witch-trials-tiles-from-json.js
 */

const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');
const os = require('os');

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

const TILES_DIR = path.join(os.homedir(), 'Development/Salem/data/json/oracle_tiles');
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

function loadJson(p) {
  try { return JSON.parse(fs.readFileSync(p, 'utf8')); } catch (_) { return null; }
}

(async () => {
  const files = fs.readdirSync(TILES_DIR)
    .filter(f => f.endsWith('.json') && !f.startsWith('_'));

  console.log(`Found ${files.length} tile JSONs in ${TILES_DIR}`);
  if (files.length === 0) {
    console.error('FAILED: zero tile JSONs — aborting');
    process.exit(1);
  }

  const tiles = [];
  for (const f of files) {
    const t = loadJson(path.join(TILES_DIR, f));
    if (!t || !t.id || !t.body) {
      console.error(`  SKIP [${f}]: missing id/body`);
      continue;
    }
    tiles.push(t);
  }
  tiles.sort((a, b) => (a.tile_order || 0) - (b.tile_order || 0));

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // REPLACE strategy: clear active rows, reinsert. Same as the prior
    // intel-based importer — operator hand-edits land in PG, not in the
    // generator JSONs, so a destructive replace is acceptable per the
    // current authoring flow (regenerate everything, then re-edit in admin).
    await client.query("DELETE FROM salem_witch_trials_articles");

    const insert = `
      INSERT INTO salem_witch_trials_articles
        (id, tile_order, tile_kind, title, period_label, teaser, body,
         related_npc_ids, related_event_ids, related_newspaper_dates,
         data_source, confidence, verified_date, generator_model)
      VALUES
        ($1, $2, $3, $4, $5, $6, $7,
         $8::jsonb, $9::jsonb, $10::jsonb,
         $11, $12, NOW(), $13)
    `;

    let ok = 0, fail = 0;
    for (const t of tiles) {
      try {
        await client.query(insert, [
          t.id,
          t.tile_order || 0,
          t.tile_kind || 'month',
          t.title || '',
          t.period_label || null,
          t.teaser || '',
          t.body,
          JSON.stringify(t.related_npc_ids || []),
          JSON.stringify(t.related_event_ids || []),
          JSON.stringify(t.related_newspaper_dates || []),
          'salem_oracle',
          0.9,
          t.model || 'salem-village (gemma3:27b)',
        ]);
        ok++;
      } catch (e) {
        fail++;
        console.error(`  FAIL [${t.id}]:`, e.message);
      }
    }

    await client.query('COMMIT');

    const { rows: [{ count }] } = await client.query(
      "SELECT COUNT(*)::int AS count FROM salem_witch_trials_articles WHERE deleted_at IS NULL"
    );
    console.log(`Inserted ${ok}/${tiles.length} tiles (${fail} failures). Table now has ${count} active rows.`);
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  } finally {
    client.release();
    await pool.end();
  }
})().catch((e) => { console.error('FATAL:', e); process.exit(1); });
