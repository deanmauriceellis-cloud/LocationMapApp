#!/usr/bin/env node
/*
 * sync-historical-notes-from-intel.js — Phase 9R.0 Heritage Trail
 *
 * Populates salem_pois.historical_note for the 10 Heritage Trail anchor POIs
 * (and optionally any other POI with intel_entity_id). Historical notes use a
 * tour-guide voice and are the narration source when the app is in
 * Historical Mode.
 *
 * Source priority (first non-empty wins):
 *   1. GET /api/intel/entity/{id}/historical_note         ← preferred (future SI endpoint)
 *   2. GET /api/intel/entity/{id}/narration               → historical_note field if present
 *   3. GET /api/intel/entity/{id}/dump                    → generated_narrations.historical_note
 *                                                        OR mode_c_historic_building_details.tour_note
 *
 * S125 (2026-04-14): removed the medium_narration fallback. Overnight test
 * 2026-04-14 showed it was writing business-description prose into
 * historical_note for ~1,100 POIs, which then leaked modern shops, tour
 * companies, witch museums, etc. into Historical Mode (Spellbound Tours,
 * Vampfangs, Salem Ghosts Tours, World of Wizardry narrating as 1692
 * history). Without a dedicated tour-guide-voice pass from SI, the note
 * is left NULL — which is correct: the POI then falls back to its short
 * narration (or is filtered out by the narration-note requirement in
 * NarrationGeofenceManager.isHistoricalQualified).
 *
 * Usage:
 *   node scripts/sync-historical-notes-from-intel.js
 *   node scripts/sync-historical-notes-from-intel.js --anchors-only     # only Heritage Trail 10
 *   node scripts/sync-historical-notes-from-intel.js --dry-run
 *
 * Requires: DATABASE_URL in env or cache-proxy/.env
 */

const { Pool } = require('pg');
const path = require('path');
const http = require('http');
const fs = require('fs');

const DRY_RUN = process.argv.includes('--dry-run');
const ANCHORS_ONLY = process.argv.includes('--anchors-only');
const INTEL_BASE = process.env.INTEL_BASE || 'http://localhost:8089';

const HERITAGE_TRAIL_ANCHOR_POI_IDS = [
  'national_park_service_visitor_center',
  'salem_jail_site',
  'salem_common',
  'salem_maritime_national_historical_park',
  'custom_house',
  'old_town_hall',
  'salem_witch_trials_memorial',
  'bewitched_sculpture_samantha_statue',
  'the_witch_house',
  'chestnut_street',
];

if (!process.env.DATABASE_URL) {
  try {
    const envPath = path.resolve(__dirname, '../.env');
    const envContent = fs.readFileSync(envPath, 'utf8');
    for (const line of envContent.split('\n')) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const eq = trimmed.indexOf('=');
      if (eq === -1) continue;
      const k = trimmed.slice(0, eq).trim();
      let v = trimmed.slice(eq + 1).trim();
      if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
        v = v.slice(1, -1);
      }
      if (!process.env[k]) process.env[k] = v;
    }
  } catch (_) {}
}
if (!process.env.DATABASE_URL) {
  console.error('Error: DATABASE_URL is required (env or cache-proxy/.env)');
  process.exit(1);
}

const pool = new Pool({ connectionString: process.env.DATABASE_URL });

function httpGetJson(url) {
  return new Promise((resolve, reject) => {
    http
      .get(url, { timeout: 10000 }, (res) => {
        let body = '';
        res.on('data', (c) => (body += c));
        res.on('end', () => {
          if (res.statusCode === 200) {
            try {
              resolve(JSON.parse(body));
            } catch (e) {
              reject(new Error(`Parse error: ${e.message}`));
            }
          } else if (res.statusCode === 404) {
            resolve(null);
          } else {
            reject(new Error(`HTTP ${res.statusCode}: ${body.slice(0, 200)}`));
          }
        });
      })
      .on('error', reject)
      .on('timeout', function () {
        this.destroy(new Error('timeout'));
      });
  });
}

function firstNonEmpty(...values) {
  for (const v of values) {
    if (typeof v === 'string' && v.trim().length > 10) return v.trim();
  }
  return null;
}

/**
 * Try every known SI shape to find a tour-guide-voice historical note.
 * Returns { text, source } or null.
 */
async function fetchHistoricalNote(entityId) {
  // 1. Dedicated endpoint (preferred, may 404 today)
  try {
    const r = await httpGetJson(`${INTEL_BASE}/api/intel/entity/${entityId}/historical_note`);
    const hit = firstNonEmpty(r?.historical_note, r?.text, r?.note);
    if (hit) return { text: hit, source: 'si_historical_note_endpoint' };
  } catch (_) {}

  // 2. Narration endpoint with a historical_note field
  let narration = null;
  try {
    narration = await httpGetJson(`${INTEL_BASE}/api/intel/entity/${entityId}/narration`);
    const hit = firstNonEmpty(
      narration?.historical_note,
      narration?.tour_guide_narration,
      narration?.heritage_note
    );
    if (hit) return { text: hit, source: 'si_narration_endpoint_historical_field' };
  } catch (_) {}

  // 3. Dump endpoint (nested)
  try {
    const d = await httpGetJson(`${INTEL_BASE}/api/intel/entity/${entityId}/dump`);
    const hit = firstNonEmpty(
      d?.generated_narrations?.historical_note,
      d?.generated_narrations?.tour_guide_narration,
      d?.mode_c_historic_building_details?.tour_note,
      d?.mode_c_historic_building_details?.historical_note,
      d?.historical_note
    );
    if (hit) return { text: hit, source: 'si_dump_endpoint' };
  } catch (_) {}

  // S125: No fallback. If SI returns no dedicated historical_note, leave the
  // column NULL. Historical Mode's categorical + note filter will silence
  // the POI rather than pollute the immersive track with modern business
  // prose.
  return null;
}

async function main() {
  console.log(`INTEL_BASE=${INTEL_BASE}  DRY_RUN=${DRY_RUN}  ANCHORS_ONLY=${ANCHORS_ONLY}`);

  let rows;
  if (ANCHORS_ONLY) {
    const q = await pool.query(
      `SELECT id, name, intel_entity_id FROM salem_pois WHERE id = ANY($1::text[]) ORDER BY id`,
      [HERITAGE_TRAIL_ANCHOR_POI_IDS]
    );
    rows = q.rows;
  } else {
    // All linked POIs (expands the historical_note backfill across Salem for
    // any future Historical Mode session, not just the Heritage Trail)
    const q = await pool.query(
      `SELECT id, name, intel_entity_id FROM salem_pois
       WHERE intel_entity_id IS NOT NULL
         AND deleted_at IS NULL
       ORDER BY id`
    );
    rows = q.rows;
  }

  console.log(`Processing ${rows.length} POIs...`);

  let fetched = 0;
  let skippedNoLink = 0;
  let skippedNoContent = 0;
  let updated = 0;
  const perSource = {};

  for (const row of rows) {
    if (!row.intel_entity_id) {
      skippedNoLink++;
      if (ANCHORS_ONLY) console.log(`  [${row.id}] ${row.name}: no intel_entity_id — cannot pull from SI`);
      continue;
    }

    const hit = await fetchHistoricalNote(row.intel_entity_id);
    if (!hit) {
      skippedNoContent++;
      if (ANCHORS_ONLY) console.log(`  [${row.id}] ${row.name}: SI returned no content for any source`);
      continue;
    }

    fetched++;
    perSource[hit.source] = (perSource[hit.source] || 0) + 1;

    if (!DRY_RUN) {
      await pool.query(
        `UPDATE salem_pois SET historical_note = $1, updated_at = NOW() WHERE id = $2`,
        [hit.text, row.id]
      );
      updated++;
    }
    if (ANCHORS_ONLY) {
      console.log(`  [${row.id}] ${row.name} ← ${hit.source} (${hit.text.length} chars)`);
    }
  }

  console.log('\nSummary:');
  console.log(`  Fetched:            ${fetched}`);
  console.log(`  Updated in PG:      ${updated}${DRY_RUN ? ' (dry run — no writes)' : ''}`);
  console.log(`  No intel_entity_id: ${skippedNoLink}`);
  console.log(`  No SI content:      ${skippedNoContent}`);
  console.log(`  Sources used:`);
  for (const [src, n] of Object.entries(perSource)) {
    console.log(`    ${src.padEnd(48)} ${n}`);
  }

  await pool.end();
}

main().catch((e) => {
  console.error('FAILED:', e.message);
  process.exit(1);
});
