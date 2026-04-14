#!/usr/bin/env node
/*
 * import-legacy-tours.js — Session 125 (2026-04-14)
 *
 * Re-hydrates the 4 pre-Heritage-Trail tours into PG so they show up in
 * the Android app's tour list again:
 *
 *   tour_essentials   — Walking Through Salem (14 stops)
 *   tour_explorer     — Salem Explorer (20 stops)
 *   tour_grand        — Grand Salem Tour (26 stops)
 *   tour_witch_trials — Salem Witch Trials Walking Tour (19 stops)
 *
 * These tours were defined in salem-content/SalemTours.kt and bundled in
 * the APK as assets/tours/*.json. Phase 9U (Session 118) switched the
 * Room `tours` + `tour_stops` tables to be published from PG via
 * publish-tours.js — but nobody ever imported the 4 legacy tours INTO
 * PG, so each publish wiped them out of Room. The JSON assets (with
 * their ~2,400 interpolated route points for the walk simulator) stayed
 * bundled because the runtime reads them directly from assets, not from
 * the Room DB.
 *
 * Source of truth: the asset JSONs (they have every field we need and
 * they match what the Kotlin SalemTours.kt would produce).
 *
 * POI ID resolution per stop:
 *   1. Literal poiId match in salem_pois (not soft-deleted).
 *   2. Explicit alias (see LEGACY_ID_ALIASES below).
 *   3. Fuzzy match: name normalized + coord within 100 m.
 *   4. Unresolved → reported, stop skipped.
 *
 * Usage:
 *   node scripts/import-legacy-tours.js
 *   node scripts/import-legacy-tours.js --dry-run
 */

const { Pool } = require('pg');
const path = require('path');
const fs = require('fs');

const DRY_RUN = process.argv.includes('--dry-run');
const ASSETS_DIR = path.resolve(__dirname, '../../app-salem/src/main/assets/tours');
const LEGACY_TOUR_FILES = [
  'tour_essentials.json',
  'tour_explorer.json',
  'tour_grand.json',
  'tour_witch_trials.json',
];

if (!process.env.DATABASE_URL) {
  try {
    const envPath = path.resolve(__dirname, '../.env');
    for (const line of fs.readFileSync(envPath, 'utf8').split('\n')) {
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

// Explicit legacy → current ID aliases. Covers renames from the Phase 9U
// unification + subsequent SI-sync adjustments. Only the aliases we've
// confirmed manually — everything else falls through to fuzzy match.
const LEGACY_ID_ALIASES = {
  nps_visitor_center: 'national_park_service_visitor_center',
  witch_house: 'the_witch_house',
  house_seven_gables: 'the_house_of_the_seven_gables',
  witch_trials_memorial: 'salem_witch_trials_memorial',
  salem_maritime_nhp: 'salem_maritime_national_historical_park',
  // Heritage Trail tour uses these longer forms natively — the legacy
  // tours used shorter aliases. Map them explicitly so the Phase 9R
  // stops and the legacy-tour stops point at the same canonical POI.
  bewitched_sculpture_samantha_statue: 'bewitched_sculpture_samantha_statue',
  charter_street_cemetery: 'charter_street_cemetery',
};

function normalizeName(s) {
  if (!s) return '';
  return s
    .toLowerCase()
    .normalize('NFKD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\b(the|of|and|a|at|in|on|house|site|home)\b/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function haversineM(a, b) {
  if (a.lat == null || a.lng == null || b.lat == null || b.lng == null) return Infinity;
  const R = 6_371_000;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const la1 = toRad(a.lat), la2 = toRad(b.lat);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(la1) * Math.cos(la2) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

async function loadPoiIndex() {
  const q = await pool.query(
    `SELECT id, name, lat, lng
       FROM salem_pois
      WHERE deleted_at IS NULL`
  );
  const byId = new Map();
  const list = [];
  for (const r of q.rows) {
    byId.set(r.id, r);
    list.push({
      id: r.id,
      name: r.name,
      normalizedName: normalizeName(r.name),
      lat: parseFloat(r.lat),
      lng: parseFloat(r.lng),
    });
  }
  return { byId, list };
}

function resolvePoiId(stop, poiIndex) {
  // 1. Direct
  if (poiIndex.byId.has(stop.poiId)) return { id: stop.poiId, via: 'direct' };

  // 2. Alias
  const alias = LEGACY_ID_ALIASES[stop.poiId];
  if (alias && poiIndex.byId.has(alias)) return { id: alias, via: `alias:${stop.poiId}→${alias}` };

  // 3. Fuzzy: normalized name + coord within 100m
  const target = {
    name: normalizeName(stop.name),
    lat: stop.lat,
    lng: stop.lng,
  };
  let best = null;
  for (const candidate of poiIndex.list) {
    const nameMatch = target.name && candidate.normalizedName === target.name;
    const nameContains = target.name && candidate.normalizedName.includes(target.name);
    const dist = haversineM(target, candidate);
    let score = 0;
    if (nameMatch) score += 100;
    else if (nameContains) score += 50;
    if (dist <= 50) score += 50;
    else if (dist <= 150) score += 30;
    else if (dist > 300) continue;
    if (score < 80) continue;
    if (!best || score > best.score || (score === best.score && dist < best.dist)) {
      best = { id: candidate.id, score, dist, candidate };
    }
  }
  if (best) {
    return {
      id: best.id,
      via: `fuzzy:score=${best.score},dist=${best.dist.toFixed(0)}m,match=${best.candidate.name}`,
    };
  }
  return null;
}

async function main() {
  console.log(`=== Re-import legacy tours into PG ===`);
  console.log(`Mode: ${DRY_RUN ? 'DRY RUN' : 'LIVE'}`);
  console.log();

  const poiIndex = await loadPoiIndex();
  console.log(`Loaded ${poiIndex.list.length} live salem_pois for resolution`);
  console.log();

  let tourCount = 0, stopCount = 0, skippedStops = 0;
  const unresolved = [];

  for (const fname of LEGACY_TOUR_FILES) {
    const fpath = path.join(ASSETS_DIR, fname);
    if (!fs.existsSync(fpath)) {
      console.warn(`  [!] ${fname} not found — skipped`);
      continue;
    }
    const data = JSON.parse(fs.readFileSync(fpath, 'utf8'));
    const tour = data.tour;
    const stops = data.stops || [];

    console.log(`── ${tour.id} — ${tour.name} (${stops.length} stops)`);

    // Upsert tour
    if (!DRY_RUN) {
      await pool.query(
        `INSERT INTO salem_tours
           (id, name, theme, description, estimated_minutes, distance_km, stop_count, difficulty, sort_order)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
         ON CONFLICT (id) DO UPDATE SET
           name = EXCLUDED.name,
           theme = EXCLUDED.theme,
           description = EXCLUDED.description,
           estimated_minutes = EXCLUDED.estimated_minutes,
           distance_km = EXCLUDED.distance_km,
           stop_count = EXCLUDED.stop_count,
           difficulty = EXCLUDED.difficulty,
           sort_order = EXCLUDED.sort_order,
           updated_at = NOW()`,
        [
          tour.id,
          tour.name,
          tour.theme || 'tour',
          tour.description || '',
          tour.estimatedMinutes || 60,
          tour.distanceKm || 1.0,
          stops.length,
          tour.difficulty || 'moderate',
          tour.sortOrder || 10,
        ]
      );
    }
    tourCount++;

    for (const stop of stops) {
      const resolved = resolvePoiId(stop, poiIndex);
      if (!resolved) {
        skippedStops++;
        unresolved.push({ tour: tour.id, stop: stop.poiId, name: stop.name });
        console.log(`    [SKIP] order=${stop.order} ${stop.poiId} (${stop.name}) — no match`);
        continue;
      }
      if (resolved.via !== 'direct') {
        console.log(`    [ORD ${stop.order}] ${stop.poiId} → ${resolved.id} (${resolved.via})`);
      }
      if (!DRY_RUN) {
        await pool.query(
          `INSERT INTO salem_tour_stops
             (tour_id, poi_id, stop_order, transition_narration, walking_minutes_from_prev, distance_m_from_prev)
           VALUES ($1, $2, $3, $4, $5, $6)
           ON CONFLICT (tour_id, poi_id) DO UPDATE SET
             stop_order = EXCLUDED.stop_order,
             transition_narration = EXCLUDED.transition_narration,
             walking_minutes_from_prev = EXCLUDED.walking_minutes_from_prev,
             distance_m_from_prev = EXCLUDED.distance_m_from_prev,
             updated_at = NOW()`,
          [
            tour.id,
            resolved.id,
            stop.order || stop.stopOrder,
            stop.transitionNarration || null,
            stop.walkingMinutesFromPrev ?? null,
            stop.distanceMFromPrev ?? null,
          ]
        );
      }
      stopCount++;
    }
    console.log();
  }

  console.log(`== Summary ==`);
  console.log(`  Tours imported:   ${tourCount}`);
  console.log(`  Stops imported:   ${stopCount}${DRY_RUN ? ' (dry run — 0 actual writes)' : ''}`);
  console.log(`  Stops skipped (unresolved): ${skippedStops}`);
  if (unresolved.length > 0) {
    console.log();
    console.log(`  Unresolved stops — needs manual attention:`);
    for (const u of unresolved) {
      console.log(`    [${u.tour}] ${u.stop} — ${u.name}`);
    }
  }

  console.log();
  console.log(`Next:`);
  console.log(`  node scripts/publish-tours.js`);
  console.log(`  ./gradlew :app-salem:assembleDebug`);

  await pool.end();
}

main().catch((e) => {
  console.error('FAILED:', e.message);
  process.exit(1);
});
