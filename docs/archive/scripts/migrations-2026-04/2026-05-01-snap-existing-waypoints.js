/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * S214 — one-shot backfill: bind every existing free tour waypoint
 *        (salem_tour_stops with poi_id IS NULL) to its nearest TigerLine edge.
 *
 * What it does
 *   For each free waypoint with a non-null lat/lng, calls the salem-router
 *   bundle's nearestWalkableEdge() to compute (edge_id, edge_fraction,
 *   snap_lat, snap_lng) and writes those four columns. The waypoint's stored
 *   lat/lng is overwritten with the snapped position so the map immediately
 *   reflects the binding.
 *
 *   POI-based stops (poi_id IS NOT NULL) are left untouched — their canonical
 *   coords live on salem_pois and the runtime computes effective_lat/lng via
 *   COALESCE.
 *
 *   Idempotent: re-running on an already-snapped row produces the same result
 *   (the snap point is on the edge, so re-snapping returns 0 m delta).
 *
 * Output
 *   Per-row "before → after" diff with snap distance + bound street name,
 *   followed by a summary: total scanned, snapped, unchanged, errors.
 *
 * Usage
 *   cd cache-proxy && node scripts/2026-05-01-snap-existing-waypoints.js [--dry]
 *     --dry  Just report — don't write.
 */
const path = require('path');
const fs = require('fs');
const { Pool } = require('pg');

// Hand-roll .env loader — cache-proxy doesn't depend on dotenv.
(function loadEnv() {
  const envPath = path.join(__dirname, '..', '.env');
  if (!fs.existsSync(envPath)) return;
  for (const line of fs.readFileSync(envPath, 'utf8').split('\n')) {
    const m = line.match(/^([A-Z][A-Z0-9_]*)=(.*)$/);
    if (m && process.env[m[1]] === undefined) {
      let v = m[2];
      if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
        v = v.slice(1, -1);
      }
      process.env[m[1]] = v;
    }
  }
})();

const dryRun = process.argv.includes('--dry');

// Load the routing bundle the same way salem-router does, but in a stripped
// "no Express" mode so we can call _snapEdge directly.
const fakeApp = { get: () => {}, post: () => {} };
const routerModule = require('../lib/salem-router')(fakeApp, {});
const snapEdge = routerModule._snapEdge;
const bundle = routerModule._bundle();
if (!bundle) {
  console.error('ERROR: routing bundle failed to load — check cache-proxy logs');
  process.exit(1);
}

if (!process.env.DATABASE_URL) {
  console.error('ERROR: DATABASE_URL not set (source cache-proxy/.env first)');
  process.exit(1);
}
const pgPool = new Pool({ connectionString: process.env.DATABASE_URL });

async function main() {
  const { rows } = await pgPool.query(
    `SELECT stop_id, tour_id, stop_order, lat, lng, name, edge_id, edge_fraction
       FROM salem_tour_stops
      WHERE poi_id IS NULL
        AND lat IS NOT NULL
        AND lng IS NOT NULL
      ORDER BY tour_id, stop_order`
  );
  console.log(`Scanning ${rows.length} free waypoints (dryRun=${dryRun})...`);
  let snapped = 0, unchanged = 0, errors = 0, alreadyBound = 0;
  for (const r of rows) {
    const snap = snapEdge(r.lat, r.lng);
    if (!snap) {
      console.warn(`  [stop_id=${r.stop_id}] tour=${r.tour_id} order=${r.stop_order} → NO snap found near (${r.lat}, ${r.lng})`);
      errors++;
      continue;
    }
    const wasBound = r.edge_id != null && r.edge_fraction != null;
    const sameEdge = wasBound && Number(r.edge_id) === Number(snap.edge_id);
    const dLat = Math.abs(r.lat - snap.snap_lat);
    const dLng = Math.abs(r.lng - snap.snap_lng);
    const moved = dLat > 1e-9 || dLng > 1e-9;
    const label = `${r.tour_id} #${r.stop_order} stop_id=${r.stop_id}${r.name ? ` "${r.name}"` : ''}`;
    if (sameEdge && !moved) {
      console.log(`  ✓ ${label} already bound to edge_id=${snap.edge_id} (${snap.fullname || '(unnamed)'})`);
      alreadyBound++;
      continue;
    }
    console.log(
      `  → ${label}: ` +
      (wasBound ? `was edge_id=${r.edge_id} ` : 'unbound ') +
      `→ edge_id=${snap.edge_id} (${snap.fullname || '(unnamed)'}) ` +
      `frac=${snap.fraction.toFixed(3)} snap=${snap.snap_m}m`
    );
    if (!dryRun) {
      await pgPool.query(
        `UPDATE salem_tour_stops
            SET lat = $1, lng = $2, edge_id = $3, edge_fraction = $4, updated_at = NOW()
          WHERE stop_id = $5`,
        [snap.snap_lat, snap.snap_lng, snap.edge_id, snap.fraction, r.stop_id]
      );
    }
    snapped++;
  }
  console.log('---');
  console.log(`Total: ${rows.length} | Snapped/updated: ${snapped} | Already bound: ${alreadyBound} | No edge found: ${errors}`);
  if (dryRun) console.log('DRY RUN — no rows written.');
  await pgPool.end();
}

main().catch((e) => {
  console.error('ERROR:', e);
  pgPool.end().catch(() => {});
  process.exit(1);
});
