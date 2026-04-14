#!/usr/bin/env node
/*
 * flag-narration-status.js — Session 125 (2026-04-14)
 *
 * Populates salem_pois.has_announce_narration to reflect whether the POI
 * has any text the runtime narration engine would actually speak.
 *
 * A POI has "announce narration" when ANY of:
 *   - historical_note is non-blank (used in Historical Mode)
 *   - short_narration is non-blank (default ambient voice)
 *   - description is non-blank (final fallback)
 *
 * Runtime equivalence: this is the same null/blank check
 * SalemMainActivityNarration.enqueueNarration performs in its "SKIP (no
 * narrative)" gate. Storing it in PG means admin tools, reports, and any
 * future bulk-generation pipeline can query without repeating the predicate.
 *
 * Idempotent — safe to re-run after content edits. Prints a breakdown
 * report to stdout and (optionally) to docs/poi-narration-status-DATE.md.
 *
 * Usage:
 *   node scripts/flag-narration-status.js
 *   node scripts/flag-narration-status.js --dry-run        # compute only, no writes
 *   node scripts/flag-narration-status.js --report=off     # skip writing .md report
 */

const { Pool } = require('pg');
const path = require('path');
const fs = require('fs');

const DRY_RUN = process.argv.includes('--dry-run');
const REPORT_OFF = process.argv.includes('--report=off');

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

function isNonBlank(col) {
  return `(${col} IS NOT NULL AND btrim(${col}) <> '')`;
}

const PREDICATE = [
  isNonBlank('historical_note'),
  isNonBlank('short_narration'),
  isNonBlank('description'),
].join(' OR ');

async function main() {
  console.log(`=== Flag POI narration status ===`);
  console.log(`Mode: ${DRY_RUN ? 'DRY RUN (no writes)' : 'LIVE'}`);
  console.log();

  // Snapshot before
  const beforeQ = await pool.query(
    `SELECT
       COUNT(*) FILTER (WHERE deleted_at IS NULL) AS total_active,
       COUNT(*) FILTER (WHERE deleted_at IS NULL AND has_announce_narration = TRUE) AS flagged_true_before,
       COUNT(*) FILTER (WHERE deleted_at IS NULL AND has_announce_narration = FALSE) AS flagged_false_before
     FROM salem_pois`
  );
  const before = beforeQ.rows[0];

  // Compute what SHOULD be true under the predicate
  const expectedQ = await pool.query(
    `SELECT
       COUNT(*) FILTER (WHERE deleted_at IS NULL AND (${PREDICATE})) AS should_true,
       COUNT(*) FILTER (WHERE deleted_at IS NULL AND NOT (${PREDICATE})) AS should_false
     FROM salem_pois`
  );
  const expected = expectedQ.rows[0];

  console.log(`Active POIs:          ${before.total_active}`);
  console.log(`Flagged TRUE (before): ${before.flagged_true_before}`);
  console.log(`Flagged FALSE (before): ${before.flagged_false_before}`);
  console.log(`Should be TRUE:       ${expected.should_true}`);
  console.log(`Should be FALSE:      ${expected.should_false}`);
  console.log();

  if (!DRY_RUN) {
    const upd = await pool.query(
      `UPDATE salem_pois
          SET has_announce_narration = (${PREDICATE})
        WHERE deleted_at IS NULL
          AND has_announce_narration <> (${PREDICATE})`
    );
    console.log(`Updated rows: ${upd.rowCount}`);
  } else {
    console.log(`DRY RUN — no UPDATE issued`);
  }
  console.log();

  // Breakdown by category: count of silent (no narration) vs narrated
  const catQ = await pool.query(
    `SELECT
        category,
        COUNT(*) FILTER (WHERE deleted_at IS NULL) AS total,
        COUNT(*) FILTER (WHERE deleted_at IS NULL AND (${PREDICATE})) AS with_narr,
        COUNT(*) FILTER (WHERE deleted_at IS NULL AND NOT (${PREDICATE})) AS silent
     FROM salem_pois
     GROUP BY category
     ORDER BY silent DESC, category ASC`
  );

  // Sample silent POIs (up to 20, largest categories first)
  const sampleQ = await pool.query(
    `SELECT id, name, category, district
       FROM salem_pois
      WHERE deleted_at IS NULL
        AND NOT (${PREDICATE})
      ORDER BY category ASC, name ASC
      LIMIT 50`
  );

  console.log(`-- Breakdown by category --`);
  console.log(
    `${'category'.padEnd(28)} ${'total'.padStart(6)} ${'with narr'.padStart(10)} ${'silent'.padStart(7)}`
  );
  for (const r of catQ.rows) {
    console.log(
      `${(r.category || '(null)').padEnd(28)} ${String(r.total).padStart(6)} ${String(r.with_narr).padStart(10)} ${String(r.silent).padStart(7)}`
    );
  }
  console.log();

  console.log(`-- Sample silent POIs (up to 50) --`);
  for (const r of sampleQ.rows) {
    console.log(`  [${r.id}] ${r.name} — ${r.category}${r.district ? ` (${r.district})` : ''}`);
  }
  console.log();

  const totalSilent = catQ.rows.reduce((n, r) => n + Number(r.silent), 0);
  const totalWith = catQ.rows.reduce((n, r) => n + Number(r.with_narr), 0);
  const totalActive = Number(before.total_active);
  const pct = totalActive > 0 ? ((totalSilent / totalActive) * 100).toFixed(1) : '0.0';
  console.log(`== Summary ==`);
  console.log(`  Silent / Active: ${totalSilent} / ${totalActive} (${pct}%)`);
  console.log(`  Narrated:        ${totalWith}`);

  if (!REPORT_OFF) {
    const date = new Date().toISOString().slice(0, 10);
    const reportPath = path.resolve(__dirname, `../../docs/poi-narration-status-${date}.md`);
    const lines = [];
    lines.push(`# POI Narration Status — ${date}`);
    lines.push('');
    lines.push(`**Generated by:** \`cache-proxy/scripts/flag-narration-status.js\``);
    lines.push('');
    lines.push(`**Column:** \`salem_pois.has_announce_narration\` (BOOLEAN, set per ${DRY_RUN ? 'DRY RUN — not persisted' : 'LIVE update'})`);
    lines.push('');
    lines.push(`**Predicate:** non-blank in \`historical_note\` OR \`short_narration\` OR \`description\`.`);
    lines.push('');
    lines.push(`## Summary`);
    lines.push(`- Active POIs: **${totalActive}**`);
    lines.push(`- Narrated: **${totalWith}**`);
    lines.push(`- Silent (no announce text): **${totalSilent}** (${pct}%)`);
    lines.push('');
    lines.push(`## Breakdown by Category`);
    lines.push('');
    lines.push(`| Category | Total | With Narration | Silent |`);
    lines.push(`|---|---:|---:|---:|`);
    for (const r of catQ.rows) {
      lines.push(`| ${r.category || '(null)'} | ${r.total} | ${r.with_narr} | ${r.silent} |`);
    }
    lines.push('');
    lines.push(`## Sample Silent POIs (up to 50)`);
    lines.push('');
    for (const r of sampleQ.rows) {
      lines.push(`- \`${r.id}\` — ${r.name} — ${r.category}${r.district ? ` (${r.district})` : ''}`);
    }
    lines.push('');
    fs.writeFileSync(reportPath, lines.join('\n'));
    console.log(`Report written: ${reportPath}`);
  }

  await pool.end();
}

main().catch((e) => {
  console.error('FAILED:', e.message);
  process.exit(1);
});
