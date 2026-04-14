#!/usr/bin/env node
/*
 * generate-narration-from-intel.js — Session 125 (2026-04-14)
 *
 * Fills salem_pois.short_narration for every POI currently flagged
 * has_announce_narration = FALSE. Three paths, in priority order:
 *
 *   1. GET  /api/intel/entity/{uuid}/narration   (cached, fast)
 *         → if short_narration present: use it.
 *   2. POST /api/intel/generate/narration        (synchronous LLM call, slow)
 *         → if 200 with short_narration: use it.
 *   3. Local stub: "{name} is a {category_label} in Salem{, at {address}}."
 *         → covers POIs SI doesn't know about (34 unlinked + any SI-404).
 *
 * SI's narration is the canonical source when available — better prose
 * than any template. The stub is a deliberate fallback so the narration
 * engine always has SOMETHING to speak at a geofence trigger; the
 * operator can hand-author better text later via the admin tool.
 *
 * Usage:
 *   node scripts/generate-narration-from-intel.js
 *   node scripts/generate-narration-from-intel.js --dry-run
 *   node scripts/generate-narration-from-intel.js --only-linked   # skip stubs for the 34 unlinked
 *   node scripts/generate-narration-from-intel.js --no-generate   # GET-only, no LLM synthesis
 *   node scripts/generate-narration-from-intel.js --limit=10      # test run
 */

const { Pool } = require('pg');
const path = require('path');
const http = require('http');
const fs = require('fs');

const DRY_RUN = process.argv.includes('--dry-run');
const ONLY_LINKED = process.argv.includes('--only-linked');
const NO_GENERATE = process.argv.includes('--no-generate');
const LIMIT = (() => {
  const arg = process.argv.find((a) => a.startsWith('--limit='));
  return arg ? parseInt(arg.split('=')[1], 10) : null;
})();
const INTEL_BASE = process.env.INTEL_BASE || 'http://localhost:8089';

if (!process.env.DATABASE_URL) {
  try {
    const envPath = path.resolve(__dirname, '../.env');
    const content = fs.readFileSync(envPath, 'utf8');
    for (const line of content.split('\n')) {
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

function httpRequestJson(method, url, body = null, timeoutMs = 180000) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const opts = {
      method,
      hostname: u.hostname,
      port: u.port,
      path: u.pathname + (u.search || ''),
      headers: { 'Accept': 'application/json' },
      timeout: timeoutMs,
    };
    if (body !== null) {
      const payload = JSON.stringify(body);
      opts.headers['Content-Type'] = 'application/json';
      opts.headers['Content-Length'] = Buffer.byteLength(payload);
    }
    const req = http.request(opts, (res) => {
      let buf = '';
      res.on('data', (c) => (buf += c));
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try {
            resolve({ status: res.statusCode, json: JSON.parse(buf) });
          } catch (e) {
            resolve({ status: res.statusCode, json: null, raw: buf });
          }
        } else {
          resolve({ status: res.statusCode, json: null, raw: buf });
        }
      });
    });
    req.on('error', reject);
    req.on('timeout', function () {
      req.destroy(new Error('timeout'));
    });
    if (body !== null) req.write(JSON.stringify(body));
    req.end();
  });
}

function firstNonBlank(...vals) {
  for (const v of vals) {
    if (typeof v === 'string' && v.trim().length > 10) return v.trim();
  }
  return null;
}

/** Human-readable label for a category code, for the stub fallback. */
const CATEGORY_LABELS = {
  SHOPPING: 'shop',
  FOOD_DRINK: 'restaurant',
  OFFICES: 'office',
  ENTERTAINMENT: 'entertainment venue',
  EDUCATION: 'school',
  HEALTHCARE: 'healthcare provider',
  CIVIC: 'civic business',
  AUTO_SERVICES: 'auto services business',
  LODGING: 'lodging',
  TOURISM_HISTORY: 'historic landmark',
  WITCH_SHOP: 'witch shop',
  HAUNTED_ATTRACTION: 'haunted attraction',
  FINANCE: 'financial services office',
  WORSHIP: 'place of worship',
  GHOST_TOUR: 'ghost tour',
  PARKS_REC: 'park',
  PSYCHIC: 'psychic reader',
};

function stubNarration(row) {
  const label = CATEGORY_LABELS[row.category] || 'business';
  const address = (row.address || '').trim();
  const district = (row.district || '').trim();
  if (address.length > 3) {
    return `${row.name} is a ${label} in Salem, located at ${address}.`;
  }
  if (district.length > 1) {
    return `${row.name} is a ${label} in Salem's ${district}.`;
  }
  return `${row.name} is a ${label} in Salem.`;
}

async function fetchFromIntel(uuid) {
  // 1. Cached narration
  try {
    const r = await httpRequestJson('GET', `${INTEL_BASE}/api/intel/entity/${uuid}/narration`);
    if (r.status === 200 && r.json) {
      const text = firstNonBlank(r.json.short_narration, r.json.medium_narration, r.json.entity_narration);
      if (text) return { text, source: 'si_cached' };
    }
    if (r.status === 404) {
      // Entity unknown to SI
      return { text: null, source: null, entityUnknown: true };
    }
  } catch (_) {}

  if (NO_GENERATE) return { text: null, source: null };

  // 2. On-demand generation (LLM call, slow)
  try {
    const r = await httpRequestJson('POST', `${INTEL_BASE}/api/intel/generate/narration`, { entity_id: uuid });
    if (r.status === 200 && r.json) {
      const text = firstNonBlank(r.json.short_narration, r.json.medium_narration, r.json.entity_narration);
      if (text) return { text, source: 'si_generated' };
    }
    if (r.status === 404 || (r.raw && r.raw.includes('Entity not found'))) {
      return { text: null, source: null, entityUnknown: true };
    }
  } catch (_) {}

  return { text: null, source: null };
}

async function main() {
  console.log(`=== Generate narration for silent POIs ===`);
  console.log(`INTEL_BASE: ${INTEL_BASE}`);
  console.log(`Mode: ${DRY_RUN ? 'DRY RUN (no writes)' : 'LIVE'}`);
  console.log(`Flags: only-linked=${ONLY_LINKED} no-generate=${NO_GENERATE} limit=${LIMIT ?? '∞'}`);
  console.log();

  const filter = ONLY_LINKED ? 'AND intel_entity_id IS NOT NULL' : '';
  const limitClause = LIMIT ? `LIMIT ${LIMIT}` : '';
  const q = await pool.query(
    `SELECT id, name, category, address, district, intel_entity_id
       FROM salem_pois
      WHERE has_announce_narration = FALSE
        AND deleted_at IS NULL
        ${filter}
      ORDER BY
        CASE category
          WHEN 'TOURISM_HISTORY' THEN 0
          WHEN 'PARKS_REC' THEN 1
          WHEN 'WORSHIP' THEN 2
          ELSE 3
        END,
        id
      ${limitClause}`
  );
  const rows = q.rows;
  console.log(`Silent POIs to process: ${rows.length}`);
  console.log();

  const stats = {
    si_cached: 0,
    si_generated: 0,
    stub: 0,
    entity_unknown: 0,
    updated: 0,
    skipped: 0,
  };

  let processed = 0;
  const t0 = Date.now();
  for (const row of rows) {
    processed++;
    let text = null;
    let source = 'stub';

    if (row.intel_entity_id) {
      const r = await fetchFromIntel(row.intel_entity_id);
      if (r.text) {
        text = r.text;
        source = r.source;
      } else if (r.entityUnknown) {
        stats.entity_unknown++;
      }
    }

    if (!text) {
      text = stubNarration(row);
      source = 'stub';
    }

    stats[source] = (stats[source] || 0) + 1;

    if (!DRY_RUN) {
      try {
        await pool.query(
          `UPDATE salem_pois
              SET short_narration = $1,
                  has_announce_narration = TRUE,
                  updated_at = NOW()
            WHERE id = $2`,
          [text, row.id]
        );
        stats.updated++;
      } catch (e) {
        console.error(`  [${row.id}] UPDATE failed: ${e.message}`);
        stats.skipped++;
      }
    }

    // Progress line every 20 rows or on slow SI calls
    if (processed % 20 === 0 || source === 'si_generated') {
      const rate = processed / ((Date.now() - t0) / 1000);
      console.log(
        `[${processed}/${rows.length}] ${row.id} ← ${source} (${text.length} chars) — ${rate.toFixed(1)} POI/s`
      );
    }
  }

  const elapsed = ((Date.now() - t0) / 1000).toFixed(1);
  console.log();
  console.log(`== Summary (${elapsed}s) ==`);
  console.log(`  Processed: ${processed}`);
  console.log(`  SI cached:        ${stats.si_cached}`);
  console.log(`  SI generated:     ${stats.si_generated}`);
  console.log(`  Stub (fallback):  ${stats.stub}`);
  console.log(`  SI entity unknown: ${stats.entity_unknown}`);
  console.log(`  Rows updated in PG: ${stats.updated}${DRY_RUN ? ' (dry run — 0 actual writes)' : ''}`);
  if (stats.skipped) console.log(`  Skipped (error): ${stats.skipped}`);

  await pool.end();
}

main().catch((e) => {
  console.error('FAILED:', e.message);
  process.exit(1);
});
