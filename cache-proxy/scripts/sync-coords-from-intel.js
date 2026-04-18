#!/usr/bin/env node
/*
 * sync-coords-from-intel.js — S149 (2026-04-18)
 *
 * Pulls corrected lat/lng for every intel-linked POI from SalemIntelligence
 * (/api/intel/poi-export) and updates salem_pois in PG where the coords have
 * drifted by more than a trivial amount.
 *
 * Motivation: S144 surfaced a phantom-coord bug where 10 BCS POIs anchored
 * at the exact Samantha Statue coordinate (42.5213319, -70.8958518) because
 * SI's geocoding fallback was returning the famous-landmark coord when it
 * couldn't resolve a street address. SI has since fixed those rows; this
 * script brings LMA's PG in sync so the fix propagates into the bundled
 * Room DB on the next publish-salem-pois run.
 *
 * Match key: intel_entity_id ↔ SI entity_id.
 * Update threshold: MIN_DELTA_M (haversine distance in metres). Anything below
 * the threshold is treated as a cosmetic rounding change and skipped to keep
 * the data_source annotations clean.
 *
 * Usage:
 *   node scripts/sync-coords-from-intel.js             # live run
 *   node scripts/sync-coords-from-intel.js --dry-run   # report only
 *
 * Requires: DATABASE_URL in env or cache-proxy/.env, SalemIntelligence at :8089.
 */

const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');

const DRY_RUN = process.argv.includes('--dry-run');
const SI_URL = process.env.SI_URL || 'http://127.0.0.1:8089/api/intel/poi-export';
const MIN_DELTA_M = 1.0;  // ignore sub-metre rounding differences

// Load DATABASE_URL from cache-proxy/.env if not already set.
if (!process.env.DATABASE_URL) {
  const envPath = path.resolve(__dirname, '..', '.env');
  if (fs.existsSync(envPath)) {
    for (const line of fs.readFileSync(envPath, 'utf8').split('\n')) {
      const m = line.match(/^\s*DATABASE_URL\s*=\s*(.+?)\s*$/);
      if (m) { process.env.DATABASE_URL = m[1].replace(/^["']|["']$/g, ''); break; }
    }
  }
}

function haversineM(lat1, lng1, lat2, lng2) {
  const R = 6371000;
  const toRad = d => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.asin(Math.min(1, Math.sqrt(a)));
}

async function fetchSiPois() {
  const res = await fetch(SI_URL);
  if (!res.ok) throw new Error(`SI export HTTP ${res.status}`);
  const body = await res.json();
  return body.pois || [];
}

(async () => {
  console.log(`mode: ${DRY_RUN ? 'DRY-RUN' : 'LIVE'}`);
  console.log(`SI URL: ${SI_URL}`);

  const si = await fetchSiPois();
  const byEntity = new Map(si.map(p => [p.entity_id, p]));
  console.log(`SI rows: ${si.length} (with entity_id)`);

  const pool = new Pool({ connectionString: process.env.DATABASE_URL });
  const lma = await pool.query(`
    SELECT id, intel_entity_id, name, category, lat, lng, data_source
    FROM salem_pois
    WHERE deleted_at IS NULL AND intel_entity_id IS NOT NULL
  `);
  console.log(`LMA rows (intel-linked, not deleted): ${lma.rows.length}`);

  const changes = [];
  const missingFromSi = [];

  for (const row of lma.rows) {
    const s = byEntity.get(row.intel_entity_id);
    if (!s) { missingFromSi.push(row); continue; }
    if (s.latitude == null || s.longitude == null) continue;
    const d = haversineM(row.lat, row.lng, s.latitude, s.longitude);
    if (d < MIN_DELTA_M) continue;
    changes.push({ row, si: s, deltaM: d });
  }

  console.log(`\nplanned updates: ${changes.length}`);
  console.log(`linked-to-SI but missing from current export: ${missingFromSi.length}`);

  // Show the largest moves + the Samantha cluster specifically.
  const sorted = [...changes].sort((a, b) => b.deltaM - a.deltaM);
  console.log(`\ntop 20 largest shifts (delta ≥ 500 m typically means phantom-coord fix):`);
  console.log(`  ${'name'.padEnd(40)} ${'category'.padEnd(22)} ${'delta_m'.padStart(10)}`);
  for (const c of sorted.slice(0, 20)) {
    console.log(`  ${(c.row.name || '').slice(0, 40).padEnd(40)} ` +
                `${(c.row.category || '').padEnd(22)} ` +
                `${c.deltaM.toFixed(1).padStart(10)}`);
  }

  if (DRY_RUN) {
    console.log('\n--dry-run — no writes');
    await pool.end();
    return;
  }

  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const annotate = `intel-coord-sync-2026-04-18`;
    for (const c of changes) {
      const newDs = c.row.data_source
        ? `${c.row.data_source};${annotate}`
        : annotate;
      await client.query(
        `UPDATE salem_pois
         SET lat = $1, lng = $2, data_source = $3, updated_at = NOW()
         WHERE id = $4`,
        [c.si.latitude, c.si.longitude, newDs, c.row.id]
      );
    }
    await client.query('COMMIT');
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  } finally {
    client.release();
  }

  console.log(`\nwrote ${changes.length} coord updates`);
  await pool.end();
})();
