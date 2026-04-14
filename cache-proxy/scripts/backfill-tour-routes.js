#!/usr/bin/env node
/*
 * backfill-tour-routes.js — Session 125 (2026-04-14)
 *
 * The 4 legacy tours imported in commit 3d3125e carry only their stop
 * lat/lng coordinates — no routeToNext polyline geometry. Walk-sim's
 * TourRouteLoader.loadAllRoutePoints falls back to raw stop coords, so
 * the walker is drawn straight-line between stops, cutting through
 * buildings. Heritage Trail was hand-built with OSRM geometry
 * (build-heritage-trail-route.js); this script backfills the same
 * shape for the legacy set.
 *
 * For each tour:
 *   1. Load assets/tours/{tourId}.json.
 *   2. For each consecutive (stop_k, stop_k+1), hit public OSRM foot
 *      routing to get the walking polyline (~10-200 coordinate pairs).
 *   3. Stamp routeToNext / routeDistanceM / routeDurationS on stop_k.
 *      The last stop has empty routeToNext (no successor).
 *   4. Write the JSON back in-place.
 *
 * Rate limit: 250 ms between OSRM requests. 4 tours × ~20 legs each ×
 * 250 ms ≈ 20 seconds total.
 *
 * Usage:
 *   node scripts/backfill-tour-routes.js
 *   node scripts/backfill-tour-routes.js --dry-run
 *   node scripts/backfill-tour-routes.js --tour tour_grand
 */

const fs = require('fs');
const path = require('path');

const DRY_RUN = process.argv.includes('--dry-run');
const TOUR_ARG = (() => {
  const idx = process.argv.indexOf('--tour');
  return idx >= 0 ? process.argv[idx + 1] : null;
})();

const DEFAULT_TOURS = [
  'tour_essentials',
  'tour_explorer',
  'tour_grand',
  'tour_witch_trials',
];
const TOURS = TOUR_ARG ? [TOUR_ARG] : DEFAULT_TOURS;

const ASSETS_DIR = path.resolve(__dirname, '../../app-salem/src/main/assets/tours');

const OSRM_BASE = 'https://routing.openstreetmap.de/routed-foot/route/v1/foot';
const OSRM_SLEEP_MS = 250;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function osrmLeg(from, to) {
  const coords = `${from.lng},${from.lat};${to.lng},${to.lat}`;
  const url = `${OSRM_BASE}/${coords}?overview=full&geometries=geojson&steps=false`;
  const res = await fetch(url, { headers: { 'User-Agent': 'LocationMapApp/1.5' } });
  if (!res.ok) {
    throw new Error(`OSRM HTTP ${res.status} for ${coords}`);
  }
  const j = await res.json();
  if (j.code !== 'Ok' || !j.routes?.length) {
    throw new Error(`OSRM error code=${j.code} for ${coords}`);
  }
  const r = j.routes[0];
  return {
    distance: r.distance,
    duration: r.duration,
    // r.geometry.coordinates are [lng, lat] — our JSON wants [lat, lng]
    points: r.geometry.coordinates.map(([lng, lat]) => [lat, lng]),
  };
}

async function processTour(tourId) {
  const fpath = path.join(ASSETS_DIR, `${tourId}.json`);
  if (!fs.existsSync(fpath)) {
    console.warn(`  [!] ${tourId}.json not found — skipped`);
    return;
  }
  const data = JSON.parse(fs.readFileSync(fpath, 'utf8'));
  const stops = data.stops || [];
  if (stops.length < 2) {
    console.warn(`  [!] ${tourId} has ${stops.length} stops — nothing to route`);
    return;
  }

  console.log(`── ${tourId} — ${data.tour?.name || '(unknown)'} (${stops.length} stops, ${stops.length - 1} legs)`);
  let totalDistance = 0;
  let totalDuration = 0;
  let totalPoints = 0;

  for (let k = 0; k < stops.length - 1; k++) {
    const from = stops[k];
    const to = stops[k + 1];
    // Already has routeToNext? Skip.
    if (Array.isArray(from.routeToNext) && from.routeToNext.length > 0) {
      console.log(`    leg ${k + 1}/${stops.length - 1}: already has ${from.routeToNext.length} pts — skipping`);
      continue;
    }
    try {
      const leg = await osrmLeg(from, to);
      totalDistance += leg.distance;
      totalDuration += leg.duration;
      totalPoints += leg.points.length;
      if (!DRY_RUN) {
        from.routeToNext = leg.points;
        from.routeDistanceM = Math.round(leg.distance);
        from.routeDurationS = Math.round(leg.duration);
        if (from.walkingMinutesFromPrev == null && k > 0) {
          // Leave legacy walkingMinutesFromPrev fields alone.
        }
      }
      console.log(
        `    leg ${k + 1}/${stops.length - 1}: ${from.name?.slice(0, 28).padEnd(28) || from.poiId.padEnd(28)} → ${to.name?.slice(0, 28).padEnd(28) || to.poiId.padEnd(28)} ` +
          `${(leg.distance / 1609.344).toFixed(2)}mi  ${Math.round(leg.duration / 60)}min  ${leg.points.length}pts`
      );
    } catch (e) {
      console.error(`    leg ${k + 1}/${stops.length - 1}: ${e.message}`);
    }
    await sleep(OSRM_SLEEP_MS);
  }
  // Ensure the last stop has an empty routeToNext (idempotency marker).
  const last = stops[stops.length - 1];
  if (!DRY_RUN && !Array.isArray(last.routeToNext)) {
    last.routeToNext = [];
    last.routeDistanceM = 0;
    last.routeDurationS = 0;
  }

  console.log(
    `  Total: ${(totalDistance / 1609.344).toFixed(2)} mi,  ${Math.round(totalDuration / 60)} min,  ${totalPoints} polyline points`
  );

  if (!DRY_RUN) {
    // Update tour-level summary fields if they exist.
    if (data.tour) {
      data.tour.distanceKm = parseFloat((totalDistance / 1000).toFixed(2));
      // Leave estimatedMinutes alone — the authored value is intentional
      // (accounts for per-stop dwell time, not just walking time).
    }
    fs.writeFileSync(fpath, JSON.stringify(data, null, 2));
    console.log(`  Wrote ${fpath}`);
  }
  console.log();
}

async function main() {
  console.log(`=== Backfill OSRM walking routes ===`);
  console.log(`Mode: ${DRY_RUN ? 'DRY RUN' : 'LIVE'}`);
  console.log(`Tours: ${TOURS.join(', ')}`);
  console.log();

  for (const tourId of TOURS) {
    try {
      await processTour(tourId);
    } catch (e) {
      console.error(`${tourId} failed: ${e.message}`);
    }
  }

  console.log(`== Done ==`);
  console.log(`Next:`);
  console.log(`  ./gradlew :app-salem:assembleDebug`);
  console.log(`  adb install -r app-salem/build/outputs/apk/debug/app-salem-debug.apk`);
}

main().catch((e) => {
  console.error('FAILED:', e.message);
  process.exit(1);
});
