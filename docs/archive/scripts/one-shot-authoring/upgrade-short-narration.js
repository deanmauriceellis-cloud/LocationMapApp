#!/usr/bin/env node
/*
 * upgrade-short-narration.js — Session 125 (2026-04-14)
 *
 * Improves salem_pois.short_narration for historical-category POIs
 * whose short is either:
 *   a) NULL (POI passed the has_announce_narration flag only because
 *      `description` is set — Historical Mode narrates, but falls
 *      through a longer chain to find text), OR
 *   b) a stub from generate-narration-from-intel.js commit f4626bb
 *      ("X is a historic landmark in Salem, located at Y.").
 *
 * Source priority (first non-empty wins):
 *   1. SI short_narration        (canonical, tight)
 *   2. SI medium_narration       (first sentence, ≤250 chars)
 *   3. description                (first sentence, ≤250 chars)
 *   4. skip
 *
 * Scope: TOURISM_HISTORY, WORSHIP, PARKS_REC, ENTERTAINMENT(year_established).
 * Matches the Historical Mode categorical-qualified set so the upgrade
 * lands where the tour-guide voice actually uses it.
 *
 * historical_note is NOT touched — already handled by
 * backfill-historical-note-from-intel.js.
 *
 * Usage:
 *   node scripts/upgrade-short-narration.js
 *   node scripts/upgrade-short-narration.js --dry-run
 *   node scripts/upgrade-short-narration.js --limit=N
 */

const { Pool } = require('pg');
const path = require('path');
const http = require('http');
const fs = require('fs');

const DRY_RUN = process.argv.includes('--dry-run');
const LIMIT = (() => {
  const a = process.argv.find((x) => x.startsWith('--limit='));
  return a ? parseInt(a.split('=')[1], 10) : null;
})();
const INTEL_BASE = process.env.INTEL_BASE || 'http://localhost:8089';

const ALWAYS = ['TOURISM_HISTORY', 'WORSHIP', 'PARKS_REC'];
const CONDITIONAL_ENTERTAINMENT = 'ENTERTAINMENT';

// Matches the stubs generate-narration-from-intel.js wrote in f4626bb.
// Using the distinctive phrase that only our stub produces; won't collide
// with hand-authored prose.
const STUB_MARKERS = [
  '%historic landmark in Salem%',
  '%is a shop in Salem%',
  '%is a restaurant in Salem%',
  '%is a park in Salem%',
  '%is a school in Salem%',
  '%is a place of worship in Salem%',
  '%is a healthcare provider in Salem%',
  '%is a civic business in Salem%',
  '%is a business in Salem%',
  '%is a lodging in Salem%',
  '%is a witch shop in Salem%',
  '%is a haunted attraction in Salem%',
  '%is a financial services office in Salem%',
  '%is a ghost tour in Salem%',
  '%is a psychic reader in Salem%',
  '%is a office in Salem%',
  '%is a entertainment venue in Salem%',
  '%is a auto services business in Salem%',
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

function httpGetJson(url, timeoutMs = 15000) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const req = http.get(
      { hostname: u.hostname, port: u.port, path: u.pathname + (u.search || ''), timeout: timeoutMs },
      (res) => {
        let buf = '';
        res.on('data', (c) => (buf += c));
        res.on('end', () => {
          if (res.statusCode === 200) {
            try { resolve(JSON.parse(buf)); } catch (_) { resolve(null); }
          } else {
            resolve(null);
          }
        });
      }
    );
    req.on('error', reject);
    req.on('timeout', () => req.destroy(new Error('timeout')));
  });
}

/**
 * Return the first N characters ending on a sentence boundary if possible.
 * Falls back to a clean word boundary; falls back to a hard cut.
 */
function firstSentence(text, maxLen = 250) {
  if (!text) return null;
  const t = text.trim();
  if (t.length <= maxLen) return t;

  // Look for ". " within the window — prefer the latest one that fits.
  const window = t.slice(0, maxLen + 1);
  const lastPeriod = Math.max(
    window.lastIndexOf('. '),
    window.lastIndexOf('? '),
    window.lastIndexOf('! ')
  );
  if (lastPeriod > 40) {
    return t.slice(0, lastPeriod + 1).trim();
  }
  // Fall back to word boundary
  const lastSpace = window.lastIndexOf(' ');
  if (lastSpace > 40) {
    return t.slice(0, lastSpace).trim() + '…';
  }
  return t.slice(0, maxLen).trim() + '…';
}

async function fetchShortFromIntel(uuid) {
  const n = await httpGetJson(`${INTEL_BASE}/api/intel/entity/${uuid}/narration`);
  if (!n) return null;
  // SI's short_narration is already tight (~150 chars). Use if non-blank.
  if (typeof n.short_narration === 'string' && n.short_narration.trim().length > 20) {
    return { text: n.short_narration.trim(), source: 'si_short' };
  }
  if (typeof n.medium_narration === 'string' && n.medium_narration.trim().length > 20) {
    return { text: firstSentence(n.medium_narration, 250), source: 'si_medium_1s' };
  }
  if (typeof n.entity_narration === 'string' && n.entity_narration.trim().length > 20) {
    return { text: firstSentence(n.entity_narration, 250), source: 'si_entity_1s' };
  }
  return null;
}

async function main() {
  console.log(`=== Upgrade short_narration for historical categories ===`);
  console.log(`INTEL_BASE: ${INTEL_BASE}`);
  console.log(`Mode: ${DRY_RUN ? 'DRY RUN' : 'LIVE'}  limit=${LIMIT ?? '∞'}`);
  console.log();

  // Build OR clauses for the 18 stub markers
  const stubWhere = STUB_MARKERS.map((_, i) => `short_narration LIKE $${i + 3}`).join(' OR ');

  const q = await pool.query(
    `SELECT id, name, category, intel_entity_id, short_narration, description, year_established
       FROM salem_pois
      WHERE deleted_at IS NULL
        AND (
              category = ANY($1::text[])
              OR (category = $2 AND year_established IS NOT NULL)
            )
        AND (
              short_narration IS NULL
              OR btrim(short_narration) = ''
              OR ${stubWhere}
            )
      ORDER BY
        CASE category
          WHEN 'TOURISM_HISTORY' THEN 0
          WHEN 'WORSHIP'         THEN 1
          WHEN 'PARKS_REC'       THEN 2
          WHEN 'ENTERTAINMENT'   THEN 3
          ELSE 4
        END,
        name
      ${LIMIT ? `LIMIT ${LIMIT}` : ''}`,
    [ALWAYS, CONDITIONAL_ENTERTAINMENT, ...STUB_MARKERS]
  );
  const rows = q.rows;
  console.log(`POIs to upgrade: ${rows.length}`);
  console.log();

  const stats = { from_si_short: 0, from_si_medium: 0, from_si_entity: 0, from_description: 0, unchanged: 0, updated: 0 };

  let i = 0;
  for (const row of rows) {
    i++;
    let text = null;
    let source = null;

    if (row.intel_entity_id) {
      const hit = await fetchShortFromIntel(row.intel_entity_id);
      if (hit) {
        text = hit.text;
        source = hit.source === 'si_short'     ? 'from_si_short'
               : hit.source === 'si_medium_1s' ? 'from_si_medium'
               :                                 'from_si_entity';
      }
    }

    if (!text && row.description && row.description.trim().length > 20) {
      text = firstSentence(row.description, 250);
      source = 'from_description';
    }

    if (!text) {
      stats.unchanged++;
      console.log(`  [${i}/${rows.length}] [${row.category}] ${row.id} — no source, keeping existing`);
      continue;
    }

    stats[source]++;
    if (!DRY_RUN) {
      await pool.query(
        `UPDATE salem_pois SET short_narration = $1, updated_at = NOW() WHERE id = $2`,
        [text, row.id]
      );
      stats.updated++;
    }
    console.log(`  [${i}/${rows.length}] [${row.category}] ${row.id} ← ${source} (${text.length} chars)`);
  }

  console.log();
  console.log(`== Summary ==`);
  console.log(`  Processed:          ${rows.length}`);
  console.log(`  From SI short:      ${stats.from_si_short}`);
  console.log(`  From SI medium 1s:  ${stats.from_si_medium}`);
  console.log(`  From SI entity 1s:  ${stats.from_si_entity}`);
  console.log(`  From description:   ${stats.from_description}`);
  console.log(`  Unchanged (no source): ${stats.unchanged}`);
  console.log(`  Rows updated:       ${stats.updated}${DRY_RUN ? ' (dry run)' : ''}`);

  await pool.end();
}

main().catch((e) => {
  console.error('FAILED:', e.message);
  process.exit(1);
});
