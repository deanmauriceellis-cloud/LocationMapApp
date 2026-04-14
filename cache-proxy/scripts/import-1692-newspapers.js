#!/usr/bin/env node
/*
 * import-1692-newspapers.js — Phase 9R.0
 *
 * Pulls SalemIntelligence's curated 1692 newspaper corpus (~203 dated
 * articles with pre-written tts_full_text) from /api/intel/salem-1692/export
 * and loads it into a new PG table `salem_newspapers_1692`. Downstream,
 * publish-1692-newspapers.js mirrors the table into Room for offline
 * consumption by HistoricalHeadlineQueue.
 *
 * Safe to re-run: TRUNCATEs + reinserts.
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
  console.log(`Fetching ${INTEL_BASE}/api/intel/salem-1692/export ...`);
  const payload = await httpGetJson(`${INTEL_BASE}/api/intel/salem-1692/export?limit=500`);
  const newspapers = payload.newspapers || [];
  console.log(`  Got ${newspapers.length} newspapers (export_version=${payload.export_version})`);

  const client = await pool.connect();
  try {
    await client.query(`
      CREATE TABLE IF NOT EXISTS salem_newspapers_1692 (
        id SERIAL PRIMARY KEY,
        date TEXT NOT NULL,
        day_of_week TEXT,
        long_date TEXT,
        crisis_phase TEXT,
        lede TEXT,
        summary TEXT,
        tts_full_text TEXT NOT NULL,
        body_points JSONB,
        sources_cited JSONB,
        events_referenced JSONB,
        imported_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
      );
      CREATE INDEX IF NOT EXISTS salem_newspapers_1692_date_idx
        ON salem_newspapers_1692 (date);
    `);
    await client.query('BEGIN');
    await client.query('TRUNCATE TABLE salem_newspapers_1692 RESTART IDENTITY');
    let inserted = 0;
    for (const n of newspapers) {
      if (!n.tts_full_text || !n.date) continue;
      await client.query(
        `INSERT INTO salem_newspapers_1692
           (date, day_of_week, long_date, crisis_phase, lede, summary, tts_full_text,
            body_points, sources_cited, events_referenced)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8::jsonb,$9::jsonb,$10::jsonb)`,
        [
          n.date, n.day_of_week || null, n.long_date || null,
          n.crisis_phase != null ? String(n.crisis_phase) : null,
          n.lede || null, n.summary || null, n.tts_full_text,
          JSON.stringify(n.body_points || []),
          JSON.stringify(n.sources_cited || []),
          JSON.stringify(n.events_referenced || [])
        ]
      );
      inserted++;
    }
    await client.query('COMMIT');
    console.log(`Inserted ${inserted} newspapers into salem_newspapers_1692`);
  } catch (e) {
    await client.query('ROLLBACK').catch(() => {});
    throw e;
  } finally {
    client.release();
  }
  await pool.end();
}

main().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });
