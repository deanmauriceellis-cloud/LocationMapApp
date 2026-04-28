#!/usr/bin/env node
/*
 * backfill-intel-entity-id.js — Session 192 (2026-04-27)
 *
 * Populates salem_pois.intel_entity_id for HISTORICAL_BUILDINGS rows that
 * are not yet linked to a SalemIntelligence entity. Uses SI's
 * /api/intel/geocode-sync as the canonical source, filtered to
 * entity_type='historic_building' (~6,570 entities).
 *
 * Match algorithm (per LMA POI):
 *   1. Find SI candidates within MATCH_RADIUS_M (default 30m) by haversine.
 *   2. Score each candidate:
 *        - normalized name exact match:                         +100
 *        - normalized name token-jaccard >= 0.7:                +60
 *        - normalized name token-jaccard >= 0.5:                +30
 *        - within 15m:                                          +30
 *        - within 30m:                                          +15
 *   3. Best candidate wins if score >= 60 AND distance <= 30m.
 *   4. UPDATE salem_pois SET intel_entity_id = match.entity_id.
 *
 * Name normalization: lowercase, strip "the ", "house", "mansion",
 * "site", "historic", "national", "park", non-alphanumeric.
 *
 * Usage:
 *   node scripts/backfill-intel-entity-id.js
 *   node scripts/backfill-intel-entity-id.js --dry-run
 *   node scripts/backfill-intel-entity-id.js --radius=50
 *   node scripts/backfill-intel-entity-id.js --min-score=80
 *   node scripts/backfill-intel-entity-id.js --verbose
 *   node scripts/backfill-intel-entity-id.js --limit=20         # test run
 */

'use strict';

const { Pool } = require('pg');
const path = require('path');
const http = require('http');
const fs = require('fs');

const DRY_RUN = process.argv.includes('--dry-run');
const VERBOSE = process.argv.includes('--verbose');
const RADIUS_M = (() => {
  const a = process.argv.find((s) => s.startsWith('--radius='));
  return a ? parseFloat(a.split('=')[1]) : 30;
})();
const MIN_SCORE = (() => {
  const a = process.argv.find((s) => s.startsWith('--min-score='));
  return a ? parseFloat(a.split('=')[1]) : 60;
})();
const LIMIT = (() => {
  const a = process.argv.find((s) => s.startsWith('--limit='));
  return a ? parseInt(a.split('=')[1], 10) : null;
})();
const INTEL_BASE = process.env.INTEL_BASE || 'http://localhost:8089';

// env loader
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
      if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
      if (!process.env[k]) process.env[k] = v;
    }
  } catch (_) {}
}
if (!process.env.DATABASE_URL) { console.error('DATABASE_URL required'); process.exit(1); }

const pool = new Pool({ connectionString: process.env.DATABASE_URL });

// http
function httpJson(method, url, timeoutMs = 60000) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const req = http.request({
      method, hostname: u.hostname, port: u.port,
      path: u.pathname + (u.search || ''),
      headers: { Accept: 'application/json' }, timeout: timeoutMs,
    }, (res) => {
      let data = '';
      res.on('data', (c) => (data += c));
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try { resolve(JSON.parse(data)); } catch (e) { reject(e); }
        } else reject(new Error(`HTTP ${res.statusCode}`));
      });
    });
    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('timeout')); });
    req.end();
  });
}

// ── normalization ───────────────────────────────────────────────────────────

const STOPWORDS = new Set([
  'the', 'a', 'an', 'and', 'of', 'at', 'in', 'on',
  'house', 'mansion', 'site', 'historic', 'national',
  'park', 'street', 'estate', 'building', 'museum',
  'home', 'residence', 'property',
]);

function normalize(name) {
  return (name || '')
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function tokens(name) {
  return normalize(name).split(' ').filter((t) => t && !STOPWORDS.has(t));
}

function tokenJaccard(aTokens, bTokens) {
  if (!aTokens.length || !bTokens.length) return 0;
  const a = new Set(aTokens), b = new Set(bTokens);
  let inter = 0;
  for (const t of a) if (b.has(t)) inter++;
  const union = a.size + b.size - inter;
  return union ? inter / union : 0;
}

// haversine, meters
function distM(lat1, lng1, lat2, lng2) {
  const R = 6371000;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLng = ((lng2 - lng1) * Math.PI) / 180;
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) *
    Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

// ── main ────────────────────────────────────────────────────────────────────

async function main() {
  console.error(`backfill-intel-entity-id: dry_run=${DRY_RUN} radius=${RADIUS_M}m min_score=${MIN_SCORE} limit=${LIMIT ?? 'none'}`);

  // 1. Pull all SI POIs (will filter to historic_building + place + attraction)
  console.error('Fetching SI geocode-sync …');
  const siResp = await httpJson('GET', `${INTEL_BASE}/api/intel/geocode-sync?limit=20000`);
  const siAll = siResp.pois || [];
  // Restrict candidates to types that could plausibly be a historic building
  const SI_TYPES = new Set(['historic_building', 'place', 'attraction', 'civic_entity']);
  const siPois = siAll.filter((p) => SI_TYPES.has(p.entity_type) && typeof p.lat === 'number' && typeof p.lng === 'number');
  console.error(`SI candidates (historic/place/attraction/civic): ${siPois.length}`);

  // Pre-tokenize SI names
  for (const p of siPois) p._tokens = tokens(p.name);

  // 2. Load LMA HISTORICAL_BUILDINGS without intel_entity_id
  let sql = `SELECT id, name, lat, lng, year_established, data_source
             FROM salem_pois
             WHERE category='HISTORICAL_BUILDINGS'
               AND deleted_at IS NULL
               AND intel_entity_id IS NULL
               AND lat IS NOT NULL AND lng IS NOT NULL
             ORDER BY name`;
  if (LIMIT) sql += ` LIMIT ${LIMIT}`;
  const { rows: lmaPois } = await pool.query(sql);
  console.error(`LMA HISTORICAL_BUILDINGS unlinked: ${lmaPois.length}\n`);

  let stats = { matched: 0, unmatched: 0, ambiguous: 0 };
  const matches = [];
  const unmatchedSamples = [];

  for (let i = 0; i < lmaPois.length; i++) {
    const lma = lmaPois[i];
    const lmaTokens = tokens(lma.name);

    // Find all SI candidates within radius
    const inRange = [];
    for (const si of siPois) {
      const d = distM(lma.lat, lma.lng, si.lat, si.lng);
      if (d <= RADIUS_M) inRange.push({ si, d });
    }

    if (!inRange.length) {
      stats.unmatched++;
      if (unmatchedSamples.length < 10) unmatchedSamples.push({ id: lma.id, name: lma.name, reason: 'no SI within radius' });
      if (VERBOSE) console.error(`[${i + 1}/${lmaPois.length}] ${lma.id} (${lma.name}) — no SI within ${RADIUS_M}m`);
      continue;
    }

    // Score each candidate
    let best = null;
    for (const { si, d } of inRange) {
      const nLma = normalize(lma.name);
      const nSi = normalize(si.name);
      let score = 0;
      let reason = [];
      if (nLma === nSi) { score += 100; reason.push('exact'); }
      else {
        const j = tokenJaccard(lmaTokens, si._tokens);
        if (j >= 0.7) { score += 60; reason.push(`jaccard=${j.toFixed(2)}`); }
        else if (j >= 0.5) { score += 30; reason.push(`jaccard=${j.toFixed(2)}`); }
        else if (j > 0) { reason.push(`jaccard=${j.toFixed(2)}-low`); }
      }
      if (d <= 15) { score += 30; reason.push(`d=${d.toFixed(1)}m`); }
      else if (d <= 30) { score += 15; reason.push(`d=${d.toFixed(1)}m`); }
      else { reason.push(`d=${d.toFixed(1)}m`); }

      const cand = { si, d, score, reason: reason.join(',') };
      if (!best || cand.score > best.score || (cand.score === best.score && cand.d < best.d)) best = cand;
    }

    if (best && best.score >= MIN_SCORE) {
      // tie-break check: any other candidate within 5 points?
      const close = inRange
        .map(({ si, d }) => {
          const nLma = normalize(lma.name); const nSi = normalize(si.name);
          let s = 0;
          if (nLma === nSi) s = 100;
          else {
            const j = tokenJaccard(lmaTokens, si._tokens);
            if (j >= 0.7) s = 60; else if (j >= 0.5) s = 30;
          }
          if (d <= 15) s += 30; else if (d <= 30) s += 15;
          return { entity_id: si.entity_id, score: s };
        })
        .filter((c) => c.entity_id !== best.si.entity_id && c.score >= best.score - 5);

      if (close.length) {
        stats.ambiguous++;
        if (VERBOSE) console.error(`[${i + 1}/${lmaPois.length}] ${lma.id} ambiguous: best=${best.si.name} (${best.score}) but ${close.length} close runners-up`);
        if (unmatchedSamples.length < 20) unmatchedSamples.push({ id: lma.id, name: lma.name, reason: `ambiguous: ${close.length} close runners-up` });
        continue;
      }

      stats.matched++;
      matches.push({ lma_id: lma.id, lma_name: lma.name, si_id: best.si.entity_id, si_name: best.si.name, score: best.score, dist_m: best.d.toFixed(1), reason: best.reason });
      if (VERBOSE || matches.length <= 10) {
        console.error(`[${i + 1}/${lmaPois.length}] ${lma.id} → ${best.si.entity_id.slice(0,8)} (${best.si.name}) score=${best.score} d=${best.d.toFixed(1)}m`);
      }

      if (!DRY_RUN) {
        await pool.query(
          `UPDATE salem_pois SET intel_entity_id = $1, admin_dirty = true, admin_dirty_at = NOW(), updated_at = NOW() WHERE id = $2`,
          [best.si.entity_id, lma.id]
        );
      }
    } else {
      stats.unmatched++;
      if (unmatchedSamples.length < 20) unmatchedSamples.push({ id: lma.id, name: lma.name, reason: best ? `best score ${best.score}<${MIN_SCORE}` : 'no scored candidate' });
      if (VERBOSE) console.error(`[${i + 1}/${lmaPois.length}] ${lma.id} no match (best score=${best?.score ?? 0})`);
    }
  }

  console.error('\n=== STATS ===');
  console.error(JSON.stringify(stats, null, 2));

  console.error('\n=== SAMPLE MATCHES (first 15) ===');
  for (const m of matches.slice(0, 15)) {
    console.error(`  ${m.lma_id.padEnd(40)} → ${m.si_name.padEnd(40)} score=${m.score} d=${m.dist_m}m (${m.reason})`);
  }

  console.error('\n=== SAMPLE UNMATCHED (first 15) ===');
  for (const u of unmatchedSamples.slice(0, 15)) {
    console.error(`  ${u.id.padEnd(40)} (${u.name.slice(0,40).padEnd(40)}) — ${u.reason}`);
  }

  await pool.end();
}

main().catch((e) => { console.error('FATAL:', e); process.exit(1); });
