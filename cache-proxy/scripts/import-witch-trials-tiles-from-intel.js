#!/usr/bin/env node
/*
 * import-witch-trials-tiles-from-intel.js — Phase 9X Step 3 (S140)
 *
 * Pulls the 16 Salem Witch Trials "History tile" articles from SalemIntelligence
 * (which itself ingested them from Salem Oracle S054) and loads them into PG
 * `salem_witch_trials_articles`. Downstream bundle-witch-trials-into-db.js and
 * publish-witch-trials-to-sqlite.js bake the table into the bundled Room DB.
 *
 * Source: GET :8089/api/intel/salem-1692/tiles  (index, 16 entries)
 *         GET :8089/api/intel/salem-1692/tile?id={id}  (full record per tile)
 *
 * Strategy: REPLACE. PG already contains 16 older S128-era tiles under a
 * different id convention (pre_1692, jan_1692, ...). The new Oracle corpus
 * uses (intro_pre_1692, month_1692_01..12, fallout_1693, closing_reckoning,
 * epilogue_outcomes). No Kotlin code hardcodes the old ids; the UI queries
 * ORDER BY tile_order, so a full-set replace is safe.
 *
 * Safe to re-run: TRUNCATEs + reinserts in a single transaction.
 */

const { Pool } = require('pg');
const path = require('path');
const http = require('http');
const fs = require('fs');

const INTEL_BASE = process.env.INTEL_BASE || 'http://localhost:8089';

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

const pool = new Pool({ connectionString: process.env.DATABASE_URL });

function httpGetJson(url) {
  return new Promise((resolve, reject) => {
    http
      .get(url, { timeout: 20000 }, (res) => {
        let body = '';
        res.on('data', (c) => (body += c));
        res.on('end', () => {
          if (res.statusCode === 200) {
            try { resolve(JSON.parse(body)); } catch (e) { reject(e); }
          } else {
            reject(new Error(`HTTP ${res.statusCode}: ${body.slice(0, 200)}`));
          }
        });
      })
      .on('error', reject);
  });
}

async function main() {
  console.log(`Fetching ${INTEL_BASE}/api/intel/salem-1692/tiles ...`);
  const index = await httpGetJson(`${INTEL_BASE}/api/intel/salem-1692/tiles`);
  const entries = index.entries || [];
  console.log(`  Index: ${entries.length} tiles`);
  if (entries.length !== 16) {
    throw new Error(`Expected 16 tiles, got ${entries.length}`);
  }

  // Fetch full records sequentially (16 calls, small)
  const tiles = [];
  for (const e of entries) {
    const full = await httpGetJson(
      `${INTEL_BASE}/api/intel/salem-1692/tile?id=${encodeURIComponent(e.id)}`
    );
    if (!full || !full.id || !full.body) {
      throw new Error(`Tile ${e.id} missing id/body: ${JSON.stringify(full).slice(0, 120)}`);
    }
    tiles.push(full);
  }
  console.log(`  Fetched ${tiles.length} full tile records`);

  // Validate tile_order is 1..16 contiguous
  const orders = tiles.map(t => t.tile_order).sort((a, b) => a - b);
  for (let i = 0; i < 16; i++) {
    if (orders[i] !== i + 1) {
      throw new Error(`tile_order not contiguous 1..16: got ${orders.join(',')}`);
    }
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    const preCount = await client.query(
      'SELECT COUNT(*)::int AS c FROM salem_witch_trials_articles'
    );
    console.log(`  Pre-truncate row count: ${preCount.rows[0].c}`);

    await client.query('TRUNCATE TABLE salem_witch_trials_articles');

    const insertSql = `
      INSERT INTO salem_witch_trials_articles
        (id, tile_order, tile_kind, title, period_label, teaser, body,
         related_npc_ids, related_event_ids, related_newspaper_dates,
         data_source, confidence, verified_date, generator_model)
      VALUES
        ($1, $2, $3, $4, $5, $6, $7,
         $8::jsonb, $9::jsonb, $10::jsonb,
         'salem_oracle', 0.7, NULL, $11)
    `;

    for (const t of tiles) {
      await client.query(insertSql, [
        t.id,
        t.tile_order,
        t.tile_kind,
        t.title,
        t.period_label || null,
        t.teaser,
        t.body,
        JSON.stringify(t.related_npc_ids || []),
        JSON.stringify(t.related_event_ids || []),
        JSON.stringify(t.related_newspaper_dates || []),
        t.model || null,
      ]);
    }

    await client.query('COMMIT');

    const postCount = await client.query(
      'SELECT COUNT(*)::int AS c FROM salem_witch_trials_articles'
    );
    console.log(`Inserted ${tiles.length} tiles. Post-insert COUNT=${postCount.rows[0].c}`);

    const sample = await client.query(`
      SELECT id, tile_order, title, LENGTH(body) AS body_len
      FROM salem_witch_trials_articles
      ORDER BY tile_order
    `);
    for (const r of sample.rows) {
      console.log(`  ${String(r.tile_order).padStart(2)}  ${r.id.padEnd(22)}  body=${String(r.body_len).padStart(5)}  ${r.title}`);
    }
  } catch (e) {
    await client.query('ROLLBACK').catch(() => {});
    throw e;
  } finally {
    client.release();
  }
  await pool.end();
}

main().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });
