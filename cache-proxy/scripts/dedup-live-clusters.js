#!/usr/bin/env node
/*
 * S216 — Collapse live POI duplicate clusters.
 *
 * Operator rule (S216):
 *   BCS wins. For every cluster of LIVE POIs that share a normalised name
 *   and sit within RADIUS_M of each other, the keeper is whichever row is
 *   sourced from BCS (salem_intelligence_bcs) — or, if multiple BCS rows,
 *   the most content-rich one. Failing BCS, prefer manual_curated, then
 *   is_tour_poi=true, then most content, then earliest created_at. Every
 *   non-keeper in the cluster has its richer fields merged into the
 *   keeper, then is HARD-DELETED.
 *
 *   The keeper's lat/lng / lat_proposed / lng_proposed are NEVER touched —
 *   operator hand-curates location, that is sacred. Future admin coord
 *   edits go against the BCS row by default after this runs.
 *
 * Cluster definition: same normalised name (lowercase, alnum-only) AND
 *   distance ≤ RADIUS_M (default 200 m). Generous radius because BCS and
 *   MHC ingestion pipelines geocode independently and routinely disagree
 *   by 50–150 m on the same physical building. Pure spatial proximity is
 *   NOT used (would merge unrelated businesses sharing a storefront).
 *
 * Usage:
 *   node cache-proxy/scripts/dedup-live-clusters.js                # dry-run
 *   node cache-proxy/scripts/dedup-live-clusters.js --apply        # commit
 *   node cache-proxy/scripts/dedup-live-clusters.js --radius=300   # override
 */

const path = require('path');
const fs = require('fs');
const { Pool } = require('pg');

(function loadEnv() {
  const envPath = path.resolve(__dirname, '..', '.env');
  if (!fs.existsSync(envPath)) return;
  const raw = fs.readFileSync(envPath, 'utf8');
  for (const line of raw.split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*?)\s*$/i);
    if (!m) continue;
    if (process.env[m[1]] !== undefined) continue;
    let v = m[2];
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
    process.env[m[1]] = v;
  }
})();

const args = new Set(process.argv.slice(2));
const APPLY = args.has('--apply');
const radiusArg = process.argv.slice(2).find(a => a.startsWith('--radius='));
const RADIUS_M = radiusArg ? parseInt(radiusArg.split('=')[1], 10) : 200;
if (!Number.isFinite(RADIUS_M) || RADIUS_M <= 0 || RADIUS_M > 1000) {
  console.error(`--radius=${RADIUS_M} invalid (1..1000)`); process.exit(2);
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

function isBlank(v) { return v == null || (typeof v === 'string' && v.trim() === ''); }
function pickLonger(liveVal, deadVal) {
  if (isBlank(deadVal)) return [false, liveVal];
  if (isBlank(liveVal)) return [true, deadVal];
  return String(deadVal).length > String(liveVal).length ? [true, deadVal] : [false, liveVal];
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
    if (!seen.has(key)) { seen.add(key); merged.push(v); added++; }
  }
  return [added > 0, merged];
}
function mergeObjects(liveObj, deadObj) {
  const live = liveObj && typeof liveObj === 'object' ? liveObj : {};
  const dead = deadObj && typeof deadObj === 'object' ? deadObj : {};
  const out = { ...dead, ...live };
  let changed = false;
  for (const k of Object.keys(dead)) if (!(k in live)) changed = true;
  return [changed, out];
}

function richness(p) {
  let total = 0;
  for (const f of TEXT_FIELDS) total += String(p[f] || '').length;
  return total;
}
// Source priority — higher number wins. Drives keeper selection.
function sourceRank(ds) {
  const s = String(ds || '').toLowerCase();
  if (s.includes('salem_intelligence_bcs')) return 100;
  if (s.includes('manual_curated'))         return 90;
  if (s.includes('overpass_import') || s.includes('openstreetmap')) return 50;
  if (s.includes('api_sync'))               return 40;
  if (s.includes('massgis_mhc'))            return 20;
  if (s.includes('destination_salem'))      return 30;
  return 10;
}
function pickKeeper(rows) {
  // Highest sourceRank, tie-break by is_tour_poi, then richness, then earliest created_at.
  return [...rows].sort((a, b) => {
    const sr = sourceRank(b.data_source) - sourceRank(a.data_source);
    if (sr !== 0) return sr;
    if (!!b.is_tour_poi !== !!a.is_tour_poi) return (b.is_tour_poi ? 1 : 0) - (a.is_tour_poi ? 1 : 0);
    const rr = richness(b) - richness(a);
    if (rr !== 0) return rr;
    return new Date(a.created_at) - new Date(b.created_at);
  })[0];
}

async function main() {
  const cs = process.env.DATABASE_URL;
  if (!cs) { console.error('DATABASE_URL not set'); process.exit(2); }
  const pool = new Pool({ connectionString: cs });

  console.log('=== Live POI dedup (BCS-keeper rule) ===');
  console.log(`Mode:   ${APPLY ? 'APPLY' : 'DRY-RUN'}`);
  console.log(`Radius: ${RADIUS_M} m\n`);

  const client = await pool.connect();
  try {
    await client.query('BEGIN');

    // Build clusters: every pair of LIVE POIs that share a normalised name
    // AND sit within RADIUS_M. Then we group by transitive closure (anyone
    // pairing with anyone in the group joins it) so a 3-row cluster (A↔B,
    // B↔C, but A↔C >RADIUS_M) still collapses correctly.
    const pairs = await client.query(
      `WITH norm AS (
         SELECT id, name, lat, lng,
                lower(regexp_replace(name, '[^a-zA-Z0-9]+', '', 'g')) AS nname
         FROM salem_pois
         WHERE deleted_at IS NULL
       )
       SELECT a.id AS a_id, b.id AS b_id
         FROM norm a
         JOIN norm b
           ON a.nname = b.nname
          AND a.id < b.id
        WHERE sqrt(power((a.lat-b.lat)*111320.0,2)+power((a.lng-b.lng)*111320.0*cos(radians(a.lat)),2)) <= $1`,
      [RADIUS_M]
    );

    // Union-Find on the pair set.
    const parent = new Map();
    const find = (x) => {
      while (parent.get(x) !== x) {
        parent.set(x, parent.get(parent.get(x)));
        x = parent.get(x);
      }
      return x;
    };
    const union = (a, b) => {
      if (!parent.has(a)) parent.set(a, a);
      if (!parent.has(b)) parent.set(b, b);
      const ra = find(a), rb = find(b);
      if (ra !== rb) parent.set(ra, rb);
    };
    for (const r of pairs.rows) union(r.a_id, r.b_id);

    const clusterIds = new Set();
    for (const id of parent.keys()) clusterIds.add(id);
    if (clusterIds.size === 0) {
      console.log('No live duplicate clusters found.');
      await client.query('ROLLBACK');
      return;
    }

    const inIds = [...clusterIds];
    const rowsRes = await client.query(
      `SELECT * FROM salem_pois WHERE id = ANY($1)`,
      [inIds]
    );
    const rowsById = new Map(rowsRes.rows.map(r => [r.id, r]));

    // Group ids by root.
    const groups = new Map();
    for (const id of clusterIds) {
      const root = find(id);
      if (!groups.has(root)) groups.set(root, []);
      groups.get(root).push(rowsById.get(id));
    }

    let clustersCount = 0;
    let killCount = 0;
    let mergedFields = 0;
    const log = [];

    for (const [, members] of groups) {
      if (members.length < 2) continue;
      clustersCount++;
      const keeper = pickKeeper(members);
      const losers = members.filter(m => m.id !== keeper.id);

      const setExprs = [];
      const setVals = [];
      let setIdx = 1;
      const fieldChanges = [];

      for (const f of TEXT_FIELDS) {
        let cur = keeper[f];
        let usedFrom = null;
        for (const dead of losers) {
          const [used, val] = pickLonger(cur, dead[f]);
          if (used && val !== cur) { cur = val; usedFrom = dead.id; }
        }
        if (usedFrom) {
          setExprs.push(`${f} = $${setIdx++}`);
          setVals.push(cur);
          fieldChanges.push(`${f}/text←${usedFrom}`);
        }
      }
      for (const f of NUMERIC_FIELDS) {
        if (keeper[f] != null) continue;
        for (const dead of losers) {
          if (dead[f] != null) {
            setExprs.push(`${f} = $${setIdx++}`);
            setVals.push(dead[f]);
            fieldChanges.push(`${f}/num←${dead.id}`);
            break;
          }
        }
      }
      for (const f of BOOL_FLAG_FIELDS) {
        if (keeper[f]) continue;
        const promotedFrom = losers.find(d => !!d[f]);
        if (promotedFrom) {
          setExprs.push(`${f} = $${setIdx++}`);
          setVals.push(true);
          fieldChanges.push(`${f}/bool←${promotedFrom.id}`);
        }
      }
      for (const f of JSONB_ARRAY_FIELDS) {
        let cur = keeper[f];
        let touched = false;
        for (const dead of losers) {
          const [changed, merged] = unionArrays(cur, dead[f]);
          if (changed) { cur = merged; touched = true; }
        }
        if (touched) {
          setExprs.push(`${f} = $${setIdx++}::jsonb`);
          setVals.push(JSON.stringify(cur));
          fieldChanges.push(`${f}/arr`);
        }
      }
      for (const f of JSONB_OBJECT_FIELDS) {
        let cur = keeper[f];
        let touched = false;
        for (const dead of losers) {
          const [changed, merged] = mergeObjects(cur, dead[f]);
          if (changed) { cur = merged; touched = true; }
        }
        if (touched) {
          setExprs.push(`${f} = $${setIdx++}::jsonb`);
          setVals.push(JSON.stringify(cur));
          fieldChanges.push(`${f}/obj`);
        }
      }

      if (setExprs.length > 0) {
        setExprs.push(`updated_at = NOW()`);
        setExprs.push(`admin_dirty = TRUE`);
        setExprs.push(`admin_dirty_at = NOW()`);
        const sql = `UPDATE salem_pois SET ${setExprs.join(', ')} WHERE id = $${setIdx}`;
        setVals.push(keeper.id);
        await client.query(sql, setVals);
        mergedFields += fieldChanges.length;
      }

      // FK guard — none of the dedup-target tables had soft-deleted FKs in
      // the prior pass, but a live POI could be referenced. Repoint each
      // FK row at the keeper before deleting.
      for (const dead of losers) {
        await client.query(`UPDATE salem_events_calendar  SET venue_poi_id = $1   WHERE venue_poi_id = $2`, [keeper.id, dead.id]);
        await client.query(`UPDATE salem_historical_facts SET poi_id = $1        WHERE poi_id = $2`,        [keeper.id, dead.id]);
        await client.query(`UPDATE salem_historical_figures SET primary_poi_id = $1 WHERE primary_poi_id = $2`, [keeper.id, dead.id]);
        await client.query(`UPDATE salem_primary_sources  SET poi_id = $1        WHERE poi_id = $2`,        [keeper.id, dead.id]);
        await client.query(`UPDATE salem_timeline_events  SET poi_id = $1        WHERE poi_id = $2`,        [keeper.id, dead.id]);
        await client.query(`UPDATE salem_tour_stops       SET poi_id = $1        WHERE poi_id = $2`,        [keeper.id, dead.id]);
        await client.query(`DELETE FROM salem_pois WHERE id = $1`, [dead.id]);
        killCount++;
      }

      log.push({
        keeper: { id: keeper.id, name: keeper.name, ds: keeper.data_source, tour: keeper.is_tour_poi, lat: keeper.lat, lng: keeper.lng },
        losers: losers.map(l => ({ id: l.id, ds: l.data_source, tour: l.is_tour_poi })),
        fields: fieldChanges,
      });
    }

    console.log(`Clusters processed: ${clustersCount}`);
    console.log(`Rows hard-deleted:  ${killCount}`);
    console.log(`Field merges:       ${mergedFields}\n`);

    console.log('--- CLUSTERS ---');
    for (const c of log) {
      const kp = `${c.keeper.id} [${c.keeper.ds}${c.keeper.tour ? ' tour' : ''}]`;
      const ls = c.losers.map(l => `${l.id} [${l.ds}${l.tour ? ' tour' : ''}]`).join(' + ');
      console.log(`  KEEP ${kp}`);
      console.log(`  KILL ${ls}`);
      console.log(`  "${c.keeper.name}"`);
      if (c.fields.length > 0) {
        console.log(`  fields: ${c.fields.slice(0, 8).join(', ')}${c.fields.length > 8 ? `, +${c.fields.length - 8} more` : ''}`);
      }
      console.log('');
    }

    if (APPLY) { await client.query('COMMIT'); console.log('COMMITTED.'); }
    else { await client.query('ROLLBACK'); console.log('DRY-RUN — rolled back. Pass --apply to commit.'); }
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
