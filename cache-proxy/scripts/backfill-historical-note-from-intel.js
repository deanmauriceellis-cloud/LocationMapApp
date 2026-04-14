#!/usr/bin/env node
/*
 * backfill-historical-note-from-intel.js — Session 125 (2026-04-14)
 *
 * Populates salem_pois.historical_note from SalemIntelligence's
 * medium/long/entity narration, BUT **only** for categories that are
 * unambiguously historical:
 *
 *     TOURISM_HISTORY                       — landmarks, houses, cemeteries, memorials
 *     PARKS_REC                             — Salem Common, wharves, historic parks
 *     WORSHIP                               — historic churches
 *     ENTERTAINMENT (year_established set)  — Hamilton Hall 1805, Ropes Mansion,
 *                                             Old Town Hall, historic meeting halls
 *
 * Why not every category: S125 commit 33dd451 removed the blanket
 * medium_narration fallback from sync-historical-notes-from-intel.js
 * because it was piping modern-business prose (Spellbound Tours,
 * Vampfangs, etc.) into historical_note and leaking them into
 * Historical Mode. The categories above are categorically historical —
 * SI's medium_narration for them is appropriate tour-guide content.
 *
 * The ENTERTAINMENT gate uses `year_established IS NOT NULL` — matches
 * the NarrationGeofenceManager.isCategoricallyHistorical() rule exactly,
 * so modern Halloween-museum / tour-company ENTERTAINMENT rows stay out.
 *
 * Source priority per POI (first non-empty wins):
 *     1. dedicated /api/intel/entity/{id}/historical_note endpoint
 *        (future SI endpoint; probably 404 today)
 *     2. /api/intel/entity/{id}/narration  → historical_note field if any
 *     3. /api/intel/entity/{id}/narration  → medium_narration
 *     4. /api/intel/entity/{id}/narration  → long_narration
 *     5. /api/intel/entity/{id}/narration  → entity_narration
 *
 * Only writes historical_note; short_narration is untouched (already
 * populated by generate-narration-from-intel.js commit f4626bb).
 *
 * Usage:
 *   node scripts/backfill-historical-note-from-intel.js
 *   node scripts/backfill-historical-note-from-intel.js --dry-run
 *   node scripts/backfill-historical-note-from-intel.js --force      # overwrite existing notes
 *   node scripts/backfill-historical-note-from-intel.js --limit=N
 */

const { Pool } = require('pg');
const path = require('path');
const http = require('http');
const fs = require('fs');

const DRY_RUN = process.argv.includes('--dry-run');
const FORCE = process.argv.includes('--force');
const LIMIT = (() => {
  const a = process.argv.find((x) => x.startsWith('--limit='));
  return a ? parseInt(a.split('=')[1], 10) : null;
})();
const INTEL_BASE = process.env.INTEL_BASE || 'http://localhost:8089';

const ALWAYS_ELIGIBLE = ['TOURISM_HISTORY', 'PARKS_REC', 'WORSHIP'];
// ENTERTAINMENT is only eligible when year_established is populated —
// mirrors NarrationGeofenceManager.isCategoricallyHistorical so modern
// Halloween museums / tour companies stay out of Historical Mode.
const CONDITIONAL_ENTERTAINMENT = 'ENTERTAINMENT';

if (!process.env.DATABASE_URL) {
  try {
    const envPath = path.resolve(__dirname, '../.env');
    const content = fs.readFileSync(envPath, 'utf8');
    for (const line of content.split('\n')) {
      const s = line.trim();
      if (!s || s.startsWith('#')) continue;
      const eq = s.indexOf('=');
      if (eq < 0) continue;
      const k = s.slice(0, eq).trim();
      let v = s.slice(eq + 1).trim();
      if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
        v = v.slice(1, -1);
      }
      if (!process.env[k]) process.env[k] = v;
    }
  } catch (_) {}
}
if (!process.env.DATABASE_URL) {
  console.error('Error: DATABASE_URL is required');
  process.exit(1);
}

const pool = new Pool({ connectionString: process.env.DATABASE_URL });

function httpGetJson(url, timeoutMs = 15000) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const req = http.get(
      { hostname: u.hostname, port: u.port, path: u.pathname + (u.search || ''), timeout: timeoutMs },
      (res) => {
        let buf = '';
        res.on('data', (c) => (buf += c));
        res.on('end', () => {
          if (res.statusCode === 200) {
            try { resolve(JSON.parse(buf)); } catch (_) { resolve(null); }
          } else {
            resolve(null);
          }
        });
      }
    );
    req.on('error', reject);
    req.on('timeout', () => req.destroy(new Error('timeout')));
  });
}

function firstNonBlank(...vals) {
  for (const v of vals) {
    if (typeof v === 'string' && v.trim().length > 20) return v.trim();
  }
  return null;
}

async function fetchHistoricalNote(uuid) {
  // 1. Dedicated endpoint (probably 404)
  try {
    const r = await httpGetJson(`${INTEL_BASE}/api/intel/entity/${uuid}/historical_note`);
    const hit = firstNonBlank(r?.historical_note, r?.text, r?.note);
    if (hit) return { text: hit, source: 'si_historical_endpoint' };
  } catch (_) {}

  // 2-5. Narration endpoint
  const narr = await httpGetJson(`${INTEL_BASE}/api/intel/entity/${uuid}/narration`);
  if (!narr) return null;

  const hit = firstNonBlank(narr.historical_note, narr.tour_guide_narration, narr.heritage_note);
  if (hit) return { text: hit, source: 'si_narration_historical_field' };

  const med = firstNonBlank(narr.medium_narration);
  if (med) return { text: med, source: 'si_medium_narration' };

  const lng = firstNonBlank(narr.long_narration);
  if (lng) return { text: lng, source: 'si_long_narration' };

  const ent = firstNonBlank(narr.entity_narration);
  if (ent) return { text: ent, source: 'si_entity_narration' };

  return null;
}

async function main() {
  console.log(`=== Backfill historical_note for historical categories ===`);
  console.log(`INTEL_BASE: ${INTEL_BASE}`);
  console.log(`Mode: ${DRY_RUN ? 'DRY RUN' : 'LIVE'}  force=${FORCE}  limit=${LIMIT ?? '∞'}`);
  console.log(`Categories: ${ALWAYS_ELIGIBLE.join(', ')} + ${CONDITIONAL_ENTERTAINMENT} (year_established set)`);
  console.log();

  const q = await pool.query(
    `SELECT id, name, category, intel_entity_id, historical_note, year_established
       FROM salem_pois
      WHERE deleted_at IS NULL
        AND intel_entity_id IS NOT NULL
        AND (
              category = ANY($1::text[])
              OR (category = $2 AND year_established IS NOT NULL)
            )
        ${FORCE ? '' : "AND (historical_note IS NULL OR btrim(historical_note) = '')"}
      ORDER BY
        CASE category
          WHEN 'TOURISM_HISTORY' THEN 0
          WHEN 'WORSHIP'         THEN 1
          WHEN 'PARKS_REC'       THEN 2
          WHEN 'ENTERTAINMENT'   THEN 3
          ELSE 4
        END,
        name
      ${LIMIT ? `LIMIT ${LIMIT}` : ''}`,
    [ALWAYS_ELIGIBLE, CONDITIONAL_ENTERTAINMENT]
  );
  const rows = q.rows;
  console.log(`POIs to process: ${rows.length}`);
  console.log();

  const stats = { filled: 0, unknown: 0, nothing: 0, updated: 0 };
  const perSource = {};

  let i = 0;
  for (const row of rows) {
    i++;
    const hit = await fetchHistoricalNote(row.intel_entity_id);

    if (!hit) {
      stats.nothing++;
      if (rows.length <= 80) {
        console.log(`  [${i}/${rows.length}] ${row.id} ← (no SI content)`);
      }
      continue;
    }

    perSource[hit.source] = (perSource[hit.source] || 0) + 1;
    stats.filled++;

    if (!DRY_RUN) {
      await pool.query(
        `UPDATE salem_pois
            SET historical_note = $1,
                updated_at = NOW()
          WHERE id = $2`,
        [hit.text, row.id]
      );
      stats.updated++;
    }

    if (rows.length <= 80 || i % 20 === 0) {
      console.log(
        `  [${i}/${rows.length}] [${row.category}] ${row.id} ← ${hit.source} (${hit.text.length} chars)`
      );
    }
  }

  console.log();
  console.log(`== Summary ==`);
  console.log(`  Processed:        ${rows.length}`);
  console.log(`  Filled from SI:   ${stats.filled}`);
  console.log(`  No SI content:    ${stats.nothing}`);
  console.log(`  Rows updated:     ${stats.updated}${DRY_RUN ? ' (dry run — 0 actual writes)' : ''}`);
  console.log(`  Sources used:`);
  for (const [s, n] of Object.entries(perSource).sort((a, b) => b[1] - a[1])) {
    console.log(`    ${s.padEnd(34)} ${n}`);
  }

  await pool.end();
}

main().catch((e) => {
  console.error('FAILED:', e.message);
  process.exit(1);
});
