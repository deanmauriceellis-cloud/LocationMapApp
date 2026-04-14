#!/usr/bin/env node
/*
 * publish-1692-newspapers.js — Phase 9R.0
 *
 * Writes the 1692 newspaper corpus from PG (salem_newspapers_1692) to a
 * JSON asset bundled in the APK. The Android HistoricalHeadlineQueue reads
 * this asset on first poll — no Room migration needed.
 *
 * Output: app-salem/src/main/assets/salem_1692_newspapers.json
 */
const { Pool } = require('pg');
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

const OUT = path.resolve(__dirname, '../../app-salem/src/main/assets/salem_1692_newspapers.json');
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

(async () => {
  const { rows } = await pool.query(`
    SELECT id, date, day_of_week, long_date, crisis_phase, lede, summary, tts_full_text
    FROM salem_newspapers_1692
    ORDER BY date ASC, id ASC
  `);
  const out = {
    version: 1,
    count: rows.length,
    generatedAt: new Date().toISOString(),
    newspapers: rows.map((r) => ({
      id: r.id,
      date: r.date,
      dayOfWeek: r.day_of_week,
      longDate: r.long_date,
      crisisPhase: r.crisis_phase,
      lede: r.lede,
      summary: r.summary,
      ttsFullText: r.tts_full_text,
    })),
  };
  fs.writeFileSync(OUT, JSON.stringify(out, null, 2));
  const size = fs.statSync(OUT).size;
  console.log(`Wrote ${rows.length} newspapers to ${OUT} (${(size / 1024).toFixed(1)} KB)`);
  await pool.end();
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });
