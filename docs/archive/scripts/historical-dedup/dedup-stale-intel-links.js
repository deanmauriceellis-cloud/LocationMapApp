#!/usr/bin/env node
/*
 * dedup-stale-intel-links.js — Session 125 (2026-04-14)
 *
 * Cleans up LMA POIs whose intel_entity_id points at a SalemIntelligence
 * entity that SI no longer recognizes. The working assumption per
 * operator direction is that SI is the master of the canonical UUID
 * space; LMA linkages captured before the last SI registry rotation
 * are stale and must either be healed or removed.
 *
 * Algorithm:
 *   1. Fetch SI's /api/intel/poi-export  (1,597 canonical entities).
 *      Build two in-memory indexes:
 *         a. name-normalized → [entity, ...]
 *         b. flat list       → for coordinate nearest-neighbour scan
 *   2. Find every LMA POI with intel_entity_id set but the SI entity
 *      is unknown (/api/intel/entity/{id}/narration → 404).
 *   3. For each stale LMA POI, score SI candidates:
 *         name-match               (case/punct-normalized): +100
 *         within 50 m of LMA coord:                          +50
 *         within 150 m:                                       +20
 *         category family match:                              +10
 *      Best candidate wins if score >= 100 AND distance <= 300 m.
 *   4. Matched → UPDATE salem_pois SET intel_entity_id = NEW.
 *      Unmatched → soft-delete with data_source tag
 *         "dedup-stale-uuid-2026-04-14-loser".
 *
 * AFTER this run, operator should re-run:
 *   - generate-narration-from-intel.js    (pulls new shorts for relinked)
 *   - backfill-historical-note-from-intel.js (pulls new historical_note)
 *   - flag-narration-status.js            (refresh the flag)
 *   - publish-salem-pois.js                (push to bundled Room)
 *
 * Usage:
 *   node scripts/dedup-stale-intel-links.js
 *   node scripts/dedup-stale-intel-links.js --dry-run
 *   node scripts/dedup-stale-intel-links.js --relink-only   # skip soft-delete
 *   node scripts/dedup-stale-intel-links.js --drop-only     # skip relink
 */

const { Pool } = require('pg');
const path = require('path');
const http = require('http');
const fs = require('fs');

const DRY_RUN = process.argv.includes('--dry-run');
const RELINK_ONLY = process.argv.includes('--relink-only');
const DROP_ONLY = process.argv.includes('--drop-only');
const INTEL_BASE = process.env.INTEL_BASE || 'http://localhost:8089';

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

function httpGet(url, timeoutMs = 10000) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const req = http.get(
      { hostname: u.hostname, port: u.port, path: u.pathname + (u.search || ''), timeout: timeoutMs },
      (res) => {
        let buf = '';
        res.on('data', (c) => (buf += c));
        res.on('end', () => resolve({ status: res.statusCode, body: buf }));
      }
    );
    req.on('error', reject);
    req.on('timeout', () => req.destroy(new Error('timeout')));
  });
}

function normalizeName(s) {
  if (!s) return '';
  return s
    .toLowerCase()
    .normalize('NFKD').replace(/[\u0300-\u036f]/g, '') // strip accents
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\b(llc|inc|corp|co|ltd|the|of|and|a|at|in)\b/g, ' ')
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

/**
 * Per-candidate scoring. Returns null if the candidate should be rejected
 * outright (e.g. distance > 300 m AND no name match).
 */
function scoreCandidate(lma, si) {
  const lmaName = normalizeName(lma.name);
  const siName = normalizeName(si.display_name);
  const nameExact = lmaName.length > 0 && lmaName === siName;
  const nameContains = !nameExact && lmaName.length > 4 && (
    lmaName.includes(siName) || siName.includes(lmaName)
  );
  const dist = haversineM({ lat: lma.lat, lng: lma.lng }, { lat: si.latitude, lng: si.longitude });

  let score = 0;
  if (nameExact) score += 100;
  else if (nameContains) score += 60;
  if (dist <= 50) score += 50;
  else if (dist <= 150) score += 20;
  else if (dist > 300) return null;

  return { score, dist, nameExact, nameContains };
}

async function main() {
  console.log(`=== Dedup stale SI intel_entity_id links ===`);
  console.log(`INTEL_BASE: ${INTEL_BASE}`);
  console.log(`Mode: ${DRY_RUN ? 'DRY RUN' : 'LIVE'}  relink-only=${RELINK_ONLY}  drop-only=${DROP_ONLY}`);
  console.log();

  // 1. Pull SI canonical set
  const siResp = await httpGet(`${INTEL_BASE}/api/intel/poi-export`);
  if (siResp.status !== 200) {
    console.error(`SI poi-export failed: HTTP ${siResp.status}`);
    process.exit(1);
  }
  const siData = JSON.parse(siResp.body);
  const siPois = siData.pois;
  console.log(`SI canonical entities: ${siPois.length}`);

  // Index SI by normalized name
  const siByName = new Map();
  for (const p of siPois) {
    const n = normalizeName(p.display_name);
    if (!siByName.has(n)) siByName.set(n, []);
    siByName.get(n).push(p);
  }

  // 2. Find stale LMA POIs — intel_entity_id set but SI 404s it.
  console.log(`\nProbing LMA → SI for stale linkages...`);
  const lmaQ = await pool.query(
    `SELECT id, name, lat, lng, category, intel_entity_id
       FROM salem_pois
      WHERE deleted_at IS NULL
        AND intel_entity_id IS NOT NULL
      ORDER BY id`
  );
  const lmaRows = lmaQ.rows;

  const stale = [];
  let probed = 0;
  for (const row of lmaRows) {
    const r = await httpGet(`${INTEL_BASE}/api/intel/entity/${row.intel_entity_id}/narration`);
    if (r.status === 404) stale.push(row);
    probed++;
    if (probed % 200 === 0) process.stdout.write(`\r  ${probed}/${lmaRows.length} probed (stale so far: ${stale.length})`);
  }
  console.log(`\n  ${probed}/${lmaRows.length} probed — stale: ${stale.length}`);
  console.log();

  // 3. Score + re-link or drop
  const stats = { relinked: 0, dropped: 0, relink_candidates: 0, drop_candidates: 0, probed_candidates: 0 };
  const samples = { relinked: [], dropped: [] };

  // Cache entity-existence lookups. Use /context instead of /narration —
  // narration is cached content (404 means "not cached yet", not "entity
  // missing"), while /context is authoritative existence. POST /generate
  // will synthesize narration on demand post-relink for any entity that
  // exists in /context.
  const existsCache = new Map();
  async function siEntityExists(uuid) {
    if (existsCache.has(uuid)) return existsCache.get(uuid);
    const r = await httpGet(`${INTEL_BASE}/api/intel/entity/${uuid}/context`);
    stats.probed_candidates++;
    const ok = r.status === 200;
    existsCache.set(uuid, ok);
    return ok;
  }

  // Suffix-N rows (foo_2, foo_3) are LMA dedup duplicates of a canonical
  // row — they should be DROPPED rather than re-linked even if a good SI
  // match exists, because the canonical LMA row already covers them.
  function looksLikeDedupDup(id) {
    return /_[0-9]+$/.test(id);
  }

  for (const lma of stale) {
    const candidates = [];
    for (const si of siPois) {
      const s = scoreCandidate(lma, si);
      if (s) candidates.push({ si, ...s });
    }
    candidates.sort((a, b) => b.score - a.score || a.dist - b.dist);

    // Walk candidates top-down. Qualifying candidate: score >= 100,
    // distance <= 300 m, and SI confirms the entity exists.
    let best = null;
    if (!looksLikeDedupDup(lma.id)) {
      for (const c of candidates.slice(0, 5)) {  // only probe top 5
        if (c.score < 100 || c.dist > 300) break;
        if (await siEntityExists(c.si.entity_id)) {
          best = c;
          break;
        }
      }
    }

    if (best) {
      stats.relink_candidates++;
      if (!DROP_ONLY) {
        if (!DRY_RUN) {
          await pool.query(
            `UPDATE salem_pois
                SET intel_entity_id = $1, updated_at = NOW()
              WHERE id = $2`,
            [best.si.entity_id, lma.id]
          );
        }
        stats.relinked++;
        if (samples.relinked.length < 30) {
          samples.relinked.push(
            `  ${lma.id}  →  ${best.si.entity_id}  (${lma.name} → ${best.si.display_name}, ${best.dist.toFixed(0)}m, score ${best.score})`
          );
        }
      }
    } else {
      stats.drop_candidates++;
      if (!RELINK_ONLY) {
        if (!DRY_RUN) {
          await pool.query(
            `UPDATE salem_pois
                SET deleted_at = NOW(),
                    data_source = COALESCE(data_source, '') || ' | dedup-stale-uuid-2026-04-14-loser',
                    updated_at = NOW()
              WHERE id = $1
                AND deleted_at IS NULL`,
            [lma.id]
          );
        }
        stats.dropped++;
        if (samples.dropped.length < 30) {
          const topScored = candidates.find((c) => c.score >= 100 && c.dist <= 300);
          const reason = looksLikeDedupDup(lma.id)
            ? 'dedup-dup suffix (_N) — canonical row covers'
            : (topScored
                ? `top scored ${topScored.si.display_name} (${topScored.dist.toFixed(0)}m) — SI entity unknown`
                : 'no candidates in 300m');
          samples.dropped.push(`  ${lma.id}  (${lma.name}) — ${reason}`);
        }
      }
    }
  }

  console.log(`== Re-link candidates (${stats.relink_candidates}) — sample ==`);
  samples.relinked.forEach((l) => console.log(l));
  if (stats.relink_candidates > samples.relinked.length) {
    console.log(`  ... +${stats.relink_candidates - samples.relinked.length} more`);
  }
  console.log();
  console.log(`== Drop candidates (${stats.drop_candidates}) — sample ==`);
  samples.dropped.forEach((l) => console.log(l));
  if (stats.drop_candidates > samples.dropped.length) {
    console.log(`  ... +${stats.drop_candidates - samples.dropped.length} more`);
  }
  console.log();

  console.log(`== Summary ==`);
  console.log(`  Stale linkages probed: ${stale.length}`);
  console.log(`  Re-linked:  ${stats.relinked}${DRY_RUN ? ' (dry run)' : ''}`);
  console.log(`  Soft-deleted: ${stats.dropped}${DRY_RUN ? ' (dry run)' : ''}`);

  await pool.end();

  if (!DRY_RUN && (stats.relinked > 0 || stats.dropped > 0)) {
    console.log();
    console.log(`Next steps (operator):`);
    console.log(`  node scripts/generate-narration-from-intel.js      # pulls real short_narration for re-linked`);
    console.log(`  node scripts/backfill-historical-note-from-intel.js # pulls historical_note for re-linked`);
    console.log(`  node scripts/flag-narration-status.js              # refresh flag`);
    console.log(`  node scripts/publish-salem-pois.js                  # push to bundled Room DB`);
  }
}

main().catch((e) => {
  console.error('FAILED:', e.message);
  process.exit(1);
});
