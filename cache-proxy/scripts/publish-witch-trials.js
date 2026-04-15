#!/usr/bin/env node
/*
 * publish-witch-trials.js — Phase 9X.3
 *
 * Reads the 16 active Salem Witch Trials history articles from PG
 * (salem_witch_trials_articles, deleted_at IS NULL) and writes them to
 * a JSON asset bundled in the APK. The Android WitchTrialsRepository
 * hydrates Room from this asset on first run.
 *
 * Output: app-salem/src/main/assets/witch_trials/articles.json
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

const OUT = path.resolve(
  __dirname,
  '../../app-salem/src/main/assets/witch_trials/articles.json'
);
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

  const out = {
    version: 1,
    count: rows.length,
    generated_at: new Date().toISOString(),
    articles: rows.map((r) => ({
      id: r.id,
      tileOrder: r.tile_order,
      tileKind: r.tile_kind,
      title: r.title,
      periodLabel: r.period_label,
      teaser: r.teaser,
      body: r.body,
      relatedNpcIds: JSON.stringify(r.related_npc_ids || []),
      relatedEventIds: JSON.stringify(r.related_event_ids || []),
      relatedNewspaperDates: JSON.stringify(r.related_newspaper_dates || []),
      dataSource: r.data_source,
      confidence: r.confidence,
      verifiedDate: r.verified_date ? r.verified_date.toISOString() : null,
      generatorModel: r.generator_model,
    })),
  };

  fs.writeFileSync(OUT, JSON.stringify(out, null, 2));
  const size = fs.statSync(OUT).size;
  console.log(
    `Wrote ${rows.length} articles to ${OUT} (${(size / 1024).toFixed(1)} KB)`
  );
  await pool.end();
})().catch((e) => { console.error('FAILED:', e.message); process.exit(1); });
