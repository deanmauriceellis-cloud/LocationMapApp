#!/usr/bin/env node
/*
 * sync-narrations-from-intel.js — Session 121
 *
 * Pulls narrative text from SalemIntelligence (:8089) into the unified
 * salem_pois table in PostgreSQL. Two data sources per entity:
 *
 *   1. GET /api/intel/entity/{id}/narration  — LLM-generated narrations
 *      short_narration  → salem_pois.short_narration  (geofence walk-by, 20-50 words)
 *      long_narration   → salem_pois.long_narration   (detail view, 200-500 words)
 *      medium_narration → salem_pois.description       (POI card, 60-120 words)
 *      entity_narration → salem_pois.long_narration    (fallback if long_narration is null)
 *
 *   2. GET /api/intel/poi-export — BCS extracted descriptions
 *      short_description → salem_pois.short_description
 *      long_description  → salem_pois.description (fallback if medium_narration is null)
 *      origin_story      → salem_pois.origin_story
 *
 * OVERWRITE mode: always replaces existing values. Designed for repeated refresh.
 * Sets is_narrated = true when at least short_narration or long_narration is populated.
 *
 * Usage:
 *   node scripts/sync-narrations-from-intel.js                # live run
 *   node scripts/sync-narrations-from-intel.js --dry-run      # log only, no PG writes
 *   node scripts/sync-narrations-from-intel.js --concurrency 5  # limit parallel requests
 *
 * Requires: DATABASE_URL in environment or .env
 */

const { Pool } = require('pg');
const path = require('path');
const http = require('http');

const DRY_RUN = process.argv.includes('--dry-run');
const concurrencyIdx = process.argv.indexOf('--concurrency');
const CONCURRENCY = concurrencyIdx !== -1 ? parseInt(process.argv[concurrencyIdx + 1], 10) : 10;
const INTEL_BASE = process.env.INTEL_BASE || 'http://localhost:8089';

if (!process.env.DATABASE_URL) {
  // Parse .env manually (no dotenv dependency)
  const fs = require('fs');
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
      if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
        val = val.slice(1, -1);
      }
      if (!process.env[key]) process.env[key] = val;
    }
  } catch (_) {}
}
if (!process.env.DATABASE_URL) {
  console.error('Error: DATABASE_URL environment variable is required');
  process.exit(1);
}

const pool = new Pool({ connectionString: process.env.DATABASE_URL });

// ── HTTP helper (no external deps) ──────────────────────────────────────────

function httpGet(url) {
  return new Promise((resolve, reject) => {
    http.get(url, (res) => {
      let body = '';
      res.on('data', (chunk) => body += chunk);
      res.on('end', () => {
        if (res.statusCode === 200) {
          try { resolve(JSON.parse(body)); } catch (e) { reject(new Error(`JSON parse error: ${e.message}`)); }
        } else if (res.statusCode === 404) {
          resolve(null); // no narration generated yet
        } else {
          reject(new Error(`HTTP ${res.statusCode}: ${body.slice(0, 200)}`));
        }
      });
      res.on('error', reject);
    }).on('error', reject);
  });
}

// ── Concurrency limiter ─────────────────────────────────────────────────────

async function mapConcurrent(items, concurrency, fn) {
  const results = [];
  let idx = 0;
  async function worker() {
    while (idx < items.length) {
      const i = idx++;
      results[i] = await fn(items[i], i);
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, worker));
  return results;
}

// ── Main ────────────────────────────────────────────────────────────────────

async function main() {
  console.log(`\n=== sync-narrations-from-intel.js ===`);
  console.log(`Mode: ${DRY_RUN ? 'DRY RUN' : 'LIVE'} | Concurrency: ${CONCURRENCY}`);
  console.log(`SalemIntelligence: ${INTEL_BASE}\n`);

  // Step 1: Load BCS export as a lookup by entity_id
  console.log('Step 1: Fetching BCS export from SalemIntelligence...');
  const bcsExport = await httpGet(`${INTEL_BASE}/api/intel/poi-export?min_confidence=0`);
  if (!bcsExport || !bcsExport.pois) {
    console.error('Failed to fetch BCS export. Is SalemIntelligence running?');
    process.exit(1);
  }
  const bcsLookup = new Map();
  for (const poi of bcsExport.pois) {
    bcsLookup.set(poi.entity_id, poi);
  }
  console.log(`  BCS export: ${bcsExport.count} POIs loaded into lookup\n`);

  // Step 2: Get all linked POIs from PG
  console.log('Step 2: Loading linked POIs from PostgreSQL...');
  const { rows: linkedPois } = await pool.query(`
    SELECT id, intel_entity_id, name
    FROM salem_pois
    WHERE intel_entity_id IS NOT NULL AND deleted_at IS NULL
    ORDER BY name
  `);
  console.log(`  ${linkedPois.length} POIs with intel_entity_id\n`);

  // Step 3: Fetch narrations from SalemIntelligence (concurrent)
  console.log(`Step 3: Fetching narrations (concurrency=${CONCURRENCY})...`);
  const stats = {
    narrationFetched: 0,
    narration404: 0,
    narrationError: 0,
    bcsMatched: 0,
    bcsMissed: 0,
    updated: 0,
    skippedNoContent: 0,
  };

  const updates = await mapConcurrent(linkedPois, CONCURRENCY, async (poi, i) => {
    const entityId = poi.intel_entity_id;

    // Fetch narration
    let narration = null;
    try {
      narration = await httpGet(`${INTEL_BASE}/api/intel/entity/${entityId}/narration`);
      if (narration) stats.narrationFetched++;
      else stats.narration404++;
    } catch (err) {
      stats.narrationError++;
      if (i < 5) console.warn(`  Warning: narration fetch failed for ${poi.name}: ${err.message}`);
    }

    // Look up BCS data
    const bcs = bcsLookup.get(entityId);
    if (bcs) stats.bcsMatched++;
    else stats.bcsMissed++;

    // Build update fields
    const fields = {};

    // From narration endpoint (LLM-generated)
    if (narration) {
      if (narration.short_narration) fields.short_narration = narration.short_narration;
      if (narration.long_narration) fields.long_narration = narration.long_narration;
      if (narration.medium_narration) fields.description = narration.medium_narration;
      // entity_narration fills long_narration if the dedicated long was null
      if (narration.entity_narration && !fields.long_narration) fields.long_narration = narration.entity_narration;
    }

    // From BCS export (extracted descriptions)
    if (bcs) {
      if (bcs.short_description) fields.short_description = bcs.short_description;
      if (bcs.origin_story) fields.origin_story = bcs.origin_story;
      // BCS long_description fills description if narration medium_narration was null
      if (bcs.long_description && !fields.description) fields.description = bcs.long_description;
    }

    // Determine is_narrated: true if we have usable narration for geofence triggers
    if (fields.short_narration || fields.long_narration) {
      fields.is_narrated = true;
    }

    if (Object.keys(fields).length === 0) {
      stats.skippedNoContent++;
      return null;
    }

    // Progress logging every 100
    if ((i + 1) % 100 === 0) {
      console.log(`  ... ${i + 1}/${linkedPois.length} processed`);
    }

    return { poiId: poi.id, name: poi.name, fields };
  });

  // Step 4: Apply updates to PG
  const validUpdates = updates.filter(Boolean);
  console.log(`\nStep 4: Applying ${validUpdates.length} updates to PostgreSQL...`);

  if (!DRY_RUN) {
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      for (const update of validUpdates) {
        const setClauses = [];
        const values = [];
        let paramIdx = 1;

        for (const [col, val] of Object.entries(update.fields)) {
          setClauses.push(`${col} = $${paramIdx}`);
          values.push(val);
          paramIdx++;
        }
        setClauses.push(`updated_at = NOW()`);
        values.push(update.poiId);

        await client.query(
          `UPDATE salem_pois SET ${setClauses.join(', ')} WHERE id = $${paramIdx}`,
          values
        );
        stats.updated++;
      }
      await client.query('COMMIT');
      console.log(`  Committed ${stats.updated} updates in single transaction.`);
    } catch (err) {
      await client.query('ROLLBACK');
      console.error(`  ROLLBACK: ${err.message}`);
      throw err;
    } finally {
      client.release();
    }
  } else {
    console.log(`  [DRY RUN] Would update ${validUpdates.length} POIs`);
    // Show first 5 as samples
    for (const u of validUpdates.slice(0, 5)) {
      const fieldList = Object.keys(u.fields).join(', ');
      console.log(`    ${u.name}: ${fieldList}`);
    }
    if (validUpdates.length > 5) console.log(`    ... and ${validUpdates.length - 5} more`);
  }

  // Summary
  console.log('\n=== Summary ===');
  console.log(`  POIs processed:       ${linkedPois.length}`);
  console.log(`  Narrations fetched:   ${stats.narrationFetched}`);
  console.log(`  Narrations 404:       ${stats.narration404}`);
  console.log(`  Narration errors:     ${stats.narrationError}`);
  console.log(`  BCS matches:          ${stats.bcsMatched}`);
  console.log(`  BCS misses:           ${stats.bcsMissed}`);
  console.log(`  POIs updated:         ${DRY_RUN ? `${validUpdates.length} (dry run)` : stats.updated}`);
  console.log(`  Skipped (no content): ${stats.skippedNoContent}`);
  console.log('');

  await pool.end();
}

main().catch((err) => {
  console.error('Fatal:', err);
  process.exit(1);
});
