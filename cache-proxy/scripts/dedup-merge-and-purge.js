#!/usr/bin/env node
/*
 * S216 — Merge content from soft-deleted POIs into their live spatial
 * cluster-mate, then hard-delete all soft-deleted rows.
 *
 * Operator rule (S216):
 *   - Soft-deleted POIs (deleted_at IS NOT NULL) are slated to die.
 *   - Before deletion, any RICHER content on a dead POI moves to its live
 *     cluster-mate so it isn't lost.
 *   - The LIVE POI's lat/lng (and lat_proposed/lng_proposed) are NEVER
 *     replaced — operator-curated location is authoritative.
 *   - After merge, all dead rows are HARD-DELETED from the database.
 *
 * Cluster definition: a dead POI matches a live POI when BOTH conditions
 *   hold:
 *     1. Normalised name match (lowercase, strip every non-alphanumeric).
 *        "John Ward House" == "john ward house" == "John Ward House.".
 *     2. Distance ≤ RADIUS_M (default 300 m).
 *
 *   Spatial-only matching is NOT enough: in downtown Salem, distinct
 *   businesses share storefronts at the same coordinates ("Bewitched
 *   Historical Tours" and "Halloween Museum of Salem" are 0 m apart but
 *   are NOT a dedup pair). Name match without spatial cap is also wrong —
 *   chain stores ("7-Eleven") have the same name across town. Both
 *   together is the right signal.
 *
 *   In the current data: 23 dead POIs have a name-match somewhere live;
 *   13 of those are within 300 m and merge cleanly; the other 10 are
 *   chain-store / generic-name false positives that stay orphan.
 *   Override via --radius.
 *
 * Orphan policy: dead POIs with no cluster-mate inside RADIUS_M have
 *   nowhere to merge their content. Per operator ("dead = die") they
 *   are still hard-deleted; their content is dropped on the floor with
 *   a per-row log line so the operator can audit.
 *
 * Field merge rules (only applied when DEAD has content):
 *   - TEXT fields:    fill if live is null/blank; otherwise take whichever
 *                     is LONGER (dead wins ties).
 *   - NUMERIC fields: fill if live is null; otherwise leave live alone.
 *   - BOOLEAN flags:  OR — promote a positive feature flag (is_tour_poi,
 *                     is_narrated, …) but never demote.
 *   - JSONB arrays:   union — concat live + dead, dedup by JSON.stringify.
 *   - JSONB objects:  shallow merge with live keys winning conflicts.
 *
 * NEVER merged: lat, lng, lat_proposed, lng_proposed, id, status,
 *   data_source, confidence, deleted_at, created_at, updated_at,
 *   admin_dirty, admin_dirty_at, location_status, location_truth_of_record,
 *   location_verified_at, category, subcategory, corridor_points.
 *
 * Usage:
 *   node cache-proxy/scripts/dedup-merge-and-purge.js                # dry-run
 *   node cache-proxy/scripts/dedup-merge-and-purge.js --apply        # commit
 *   node cache-proxy/scripts/dedup-merge-and-purge.js --radius=50    # override
 *
 * Wraps the whole pass in a single transaction; --apply commits, dry-run
 * rolls back so operator can preview the report without side effects.
 */

const path = require('path');
const fs = require('fs');
const { Pool } = require('pg');

// Match the rest of cache-proxy/scripts: parse cache-proxy/.env by hand so
// we don't add a runtime dependency on dotenv (cache-proxy/server.js relies
// on the shell environment too).
(function loadEnv() {
  const envPath = path.resolve(__dirname, '..', '.env');
  if (!fs.existsSync(envPath)) return;
  const raw = fs.readFileSync(envPath, 'utf8');
  for (const line of raw.split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/i);
    if (!m) continue;
    if (process.env[m[1]] !== undefined) continue;
    let v = m[2];
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) {
      v = v.slice(1, -1);
    }
    process.env[m[1]] = v;
  }
})();

const args = new Set(process.argv.slice(2));
const APPLY = args.has('--apply');
const radiusArg = process.argv.slice(2).find(a => a.startsWith('--radius='));
const RADIUS_M = radiusArg ? parseInt(radiusArg.split('=')[1], 10) : 300;

if (!Number.isFinite(RADIUS_M) || RADIUS_M <= 0 || RADIUS_M > 1000) {
  console.error(`--radius=${RADIUS_M} is invalid (must be 1..1000)`);
  process.exit(2);
}

const TEXT_FIELDS = [
  'address', 'short_narration', 'long_narration', 'historical_period',
  'historical_note', 'historical_narration', 'admission_info', 'phone',
  'email', 'website', 'hours_text', 'menu_url', 'reservations_url',
  'order_url', 'description', 'short_description', 'custom_description',
  'origin_story', 'image_asset', 'custom_icon_asset', 'intel_entity_id',
  'source_id', 'voice_clip_asset', 'custom_voice_asset', 'cuisine_type',
  'price_range', 'building_footprint_geojson', 'mhc_id', 'mhc_style',
  'mhc_nr_status', 'mhc_narrative', 'canonical_address_point_id',
  'location_source', 'district', 'legacy_narration_category',
  'legacy_business_type', 'legacy_tour_category',
];

const NUMERIC_FIELDS = [
  'mhc_year_built', 'year_established', 'geofence_radius_m', 'priority',
  'wave', 'merchant_tier', 'ad_priority', 'rating', 'location_drift_m',
  'location_geocoder_rating',
];

// Promote-only positive flags. wheelchair_accessible / seasonal /
// requires_transportation / location_truth_of_record / default_visible
// are deliberately omitted — their semantics aren't pure-positive and an
// OR can flip them in unintended ways.
const BOOL_FLAG_FIELDS = [
  'is_tour_poi', 'is_narrated', 'has_announce_narration',
  'is_historical_property', 'is_witch_trial_site', 'is_free_admission',
  'is_indoor', 'is_family_friendly', 'is_civic_poi',
];

const JSONB_ARRAY_FIELDS = [
  'secondary_categories', 'specialties', 'owners', 'related_figure_ids',
  'related_fact_ids', 'related_source_ids', 'source_categories', 'tags',
  'action_buttons',
];

const JSONB_OBJECT_FIELDS = ['hours', 'amenities'];

function isBlank(v) {
  return v == null || (typeof v === 'string' && v.trim() === '');
}

function pickLonger(liveVal, deadVal) {
  // Returns [usedDead: bool, mergedVal]
  if (isBlank(deadVal)) return [false, liveVal];
  if (isBlank(liveVal)) return [true, deadVal];
  return String(deadVal).length > String(liveVal).length
    ? [true, deadVal]
    : [false, liveVal];
}

function unionArrays(liveArr, deadArr) {
  const live = Array.isArray(liveArr) ? liveArr : [];
  const dead = Array.isArray(deadArr) ? deadArr : [];
  if (dead.length === 0) return [false, live];
  const seen = new Set(live.map(v => JSON.stringify(v)));
  const merged = [...live];
  let added = 0;
  for (const v of dead) {
    const key = JSON.stringify(v);
    if (!seen.has(key)) {
      seen.add(key);
      merged.push(v);
      added++;
    }
  }
  return [added > 0, merged];
}

function mergeObjects(liveObj, deadObj) {
  const live = liveObj && typeof liveObj === 'object' ? liveObj : {};
  const dead = deadObj && typeof deadObj === 'object' ? deadObj : {};
  const out = { ...dead, ...live }; // live keys win on conflict
  let changed = false;
  for (const k of Object.keys(dead)) {
    if (!(k in live)) changed = true;
  }
  return [changed, out];
}

async function main() {
  const cs = process.env.DATABASE_URL;
  if (!cs) {
    console.error('DATABASE_URL not set in cache-proxy/.env');
    process.exit(2);
  }
  const pool = new Pool({ connectionString: cs });

  console.log('=== Dedup merge + purge ===');
  console.log(`Mode:   ${APPLY ? 'APPLY (will commit)' : 'DRY-RUN (will rollback)'}`);
  console.log(`Radius: ${RADIUS_M} m\n`);

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // 1. Snapshot counts.
    const before = await client.query(
      `SELECT count(*) FILTER (WHERE deleted_at IS NULL) AS live,
              count(*) FILTER (WHERE deleted_at IS NOT NULL) AS dead
         FROM salem_pois`
    );
    console.log(`Before: ${before.rows[0].live} live, ${before.rows[0].dead} dead\n`);

    // 2. Pair each dead POI with the closest LIVE POI that ALSO has a
    //    matching normalised name. Two-criteria match (name + distance)
    //    keeps shared-storefront false positives out of the merge set.
    const pairsRes = await client.query(
      `WITH norm AS (
         SELECT id, name, lat, lng, deleted_at,
                lower(regexp_replace(name, '[^a-zA-Z0-9]+', '', 'g')) AS nname
         FROM salem_pois
       ),
       pair AS (
         SELECT DISTINCT ON (d.id)
           d.id AS dead_id,
           d.name AS dead_name,
           l.id AS live_id,
           l.name AS live_name,
           sqrt(
             power((d.lat - l.lat) * 111320.0, 2) +
             power((d.lng - l.lng) * 111320.0 * cos(radians(d.lat)), 2)
           )::numeric(8,2) AS dist_m
         FROM norm d
         JOIN norm l
           ON l.deleted_at IS NULL
          AND d.nname = l.nname
          AND d.id <> l.id
         WHERE d.deleted_at IS NOT NULL
         ORDER BY d.id,
           sqrt(power((d.lat - l.lat) * 111320.0, 2) + power((d.lng - l.lng) * 111320.0 * cos(radians(d.lat)), 2))
       )
       SELECT * FROM pair`
    );

    const pairsByDeadId = new Map();
    for (const r of pairsRes.rows) {
      pairsByDeadId.set(r.dead_id, r);
    }

    // 3. Pull every dead POI row.
    const allDeadRes = await client.query(
      `SELECT * FROM salem_pois WHERE deleted_at IS NOT NULL ORDER BY name, id`
    );
    const deadRows = allDeadRes.rows;

    let mergeCount = 0;
    let orphanCount = 0;
    let fieldsCopied = 0;
    const orphanLog = [];
    const mergeLog = [];

    // 4. For each dead row: maybe merge, then delete.
    for (const dead of deadRows) {
      const pair = pairsByDeadId.get(dead.id);
      const within = pair && parseFloat(pair.dist_m) <= RADIUS_M;

      if (!within) {
        orphanCount++;
        orphanLog.push({
          id: dead.id,
          name: dead.name,
          nearest_live_id: pair ? pair.live_id : null,
          nearest_live_name: pair ? pair.live_name : null,
          nearest_dist_m: pair ? parseFloat(pair.dist_m) : null,
        });
        await client.query(`DELETE FROM salem_pois WHERE id = $1`, [dead.id]);
        continue;
      }

      // Fetch the live keeper.
      const liveRes = await client.query(
        `SELECT * FROM salem_pois WHERE id = $1`,
        [pair.live_id]
      );
      const live = liveRes.rows[0];

      const updates = []; // { field, fromLive, fromDead, kind }
      const setExprs = [];
      const setVals = [];
      let setIdx = 1;

      // TEXT — pick longer / fill blank.
      for (const f of TEXT_FIELDS) {
        const [usedDead, val] = pickLonger(live[f], dead[f]);
        if (usedDead && val !== live[f]) {
          updates.push({ field: f, kind: 'text',
            liveLen: live[f] ? String(live[f]).length : 0,
            deadLen: dead[f] ? String(dead[f]).length : 0 });
          setExprs.push(`${f} = $${setIdx++}`);
          setVals.push(val);
        }
      }
      // NUMERIC — fill nulls only.
      for (const f of NUMERIC_FIELDS) {
        if ((live[f] == null) && dead[f] != null) {
          updates.push({ field: f, kind: 'numeric',
            liveVal: live[f], deadVal: dead[f] });
          setExprs.push(`${f} = $${setIdx++}`);
          setVals.push(dead[f]);
        }
      }
      // BOOLEAN flags — OR (promote only).
      for (const f of BOOL_FLAG_FIELDS) {
        const live_b = !!live[f];
        const dead_b = !!dead[f];
        if (!live_b && dead_b) {
          updates.push({ field: f, kind: 'bool', from: live_b, to: true });
          setExprs.push(`${f} = $${setIdx++}`);
          setVals.push(true);
        }
      }
      // JSONB ARRAY — union.
      for (const f of JSONB_ARRAY_FIELDS) {
        const [changed, merged] = unionArrays(live[f], dead[f]);
        if (changed) {
          updates.push({ field: f, kind: 'jsonb_array',
            liveLen: (live[f] || []).length, mergedLen: merged.length });
          setExprs.push(`${f} = $${setIdx++}::jsonb`);
          setVals.push(JSON.stringify(merged));
        }
      }
      // JSONB OBJECT — merge.
      for (const f of JSONB_OBJECT_FIELDS) {
        const [changed, merged] = mergeObjects(live[f], dead[f]);
        if (changed) {
          updates.push({ field: f, kind: 'jsonb_obj',
            liveKeys: Object.keys(live[f] || {}).length,
            mergedKeys: Object.keys(merged).length });
          setExprs.push(`${f} = $${setIdx++}::jsonb`);
          setVals.push(JSON.stringify(merged));
        }
      }

      if (setExprs.length > 0) {
        setExprs.push(`updated_at = NOW()`);
        setExprs.push(`admin_dirty = TRUE`);
        setExprs.push(`admin_dirty_at = NOW()`);
        const sql = `UPDATE salem_pois SET ${setExprs.join(', ')} WHERE id = $${setIdx}`;
        setVals.push(live.id);
        await client.query(sql, setVals);
      }

      await client.query(`DELETE FROM salem_pois WHERE id = $1`, [dead.id]);

      mergeCount++;
      fieldsCopied += updates.length;
      mergeLog.push({
        dead_id: dead.id,
        dead_name: dead.name,
        live_id: live.id,
        live_name: live.name,
        dist_m: parseFloat(pair.dist_m),
        fields: updates,
      });
    }

    // 5. Snapshot after.
    const after = await client.query(
      `SELECT count(*) FILTER (WHERE deleted_at IS NULL) AS live,
              count(*) FILTER (WHERE deleted_at IS NOT NULL) AS dead
         FROM salem_pois`
    );

    console.log(`Processed ${deadRows.length} dead POIs:`);
    console.log(`  Merged into live cluster-mate: ${mergeCount}`);
    console.log(`    Total fields copied:         ${fieldsCopied}`);
    console.log(`  Orphan (deleted, no merge):    ${orphanCount}`);
    console.log(`After:  ${after.rows[0].live} live, ${after.rows[0].dead} dead\n`);

    if (mergeLog.length > 0) {
      console.log('--- MERGES (sample, first 20) ---');
      for (const m of mergeLog.slice(0, 20)) {
        const summary = m.fields.length === 0
          ? '(no field changes — dead had no richer content)'
          : m.fields.map(f => `${f.field}/${f.kind}`).join(', ');
        console.log(`  ${m.dead_id} → ${m.live_id} @ ${m.dist_m.toFixed(1)}m`);
        console.log(`    "${m.dead_name}" → "${m.live_name}"`);
        console.log(`    fields: ${summary}`);
      }
      if (mergeLog.length > 20) console.log(`  ... and ${mergeLog.length - 20} more`);
      console.log('');
    }

    if (orphanLog.length > 0) {
      console.log('--- ORPHANS (no live cluster-mate within radius) ---');
      for (const o of orphanLog.slice(0, 30)) {
        const ctx = o.nearest_dist_m != null
          ? `closest live ${o.nearest_live_id} "${o.nearest_live_name}" @ ${o.nearest_dist_m.toFixed(1)}m`
          : 'no live POIs at all';
        console.log(`  ${o.id} "${o.name}" — ${ctx}`);
      }
      if (orphanLog.length > 30) console.log(`  ... and ${orphanLog.length - 30} more`);
      console.log('');
    }

    if (APPLY) {
      await client.query('COMMIT');
      console.log('COMMITTED.');
    } else {
      await client.query('ROLLBACK');
      console.log('DRY-RUN — rolled back. Pass --apply to commit.');
    }
  } catch (err) {
    await client.query('ROLLBACK').catch(() => {});
    console.error('ERROR:', err);
    process.exit(1);
  } finally {
    client.release();
    await pool.end();
  }
}

main().catch(e => { console.error(e); process.exit(1); });
