#!/usr/bin/env node
/*
 * ingest-regen-log.js — S196
 *
 * Parses generate-historical-narrations.js stderr/stdout log and inserts
 * one row per POI processed into salem_narration_regen_log. Powers the
 * `narration_regen_failed` lint check.
 *
 * Usage:
 *   node scripts/ingest-regen-log.js /tmp/s196-full-regen.log
 *   node scripts/ingest-regen-log.js /tmp/s196-full-regen.log --replace
 *      (--replace deletes prior rows for these POIs first)
 */
'use strict';

const fs = require('fs');
const path = require('path');
const { Pool } = require('pg');

if (!process.env.DATABASE_URL) {
  try {
    const env = fs.readFileSync(path.resolve(__dirname, '../.env'), 'utf8');
    for (const line of env.split('\n')) {
      const s = line.trim();
      if (!s || s.startsWith('#')) continue;
      const eq = s.indexOf('=');
      if (eq < 0) continue;
      const k = s.slice(0, eq).trim();
      const v = s.slice(eq + 1).trim().replace(/^["']|["']$/g, '');
      if (!process.env[k]) process.env[k] = v;
    }
  } catch (_) {}
}

const LOG_PATH = process.argv[2];
if (!LOG_PATH || !fs.existsSync(LOG_PATH)) {
  console.error('usage: ingest-regen-log.js <log-path> [--replace]');
  process.exit(1);
}
const REPLACE = process.argv.includes('--replace');

// Match lines like:
//   [3/561] crowninshield_bentley_house (Crowninshield-Bentley House) ... ok (words=103, witch_trial=false, prompt_sha=e1e972c)
//   [4/561] charter_street_historic_district (Charter Street Historic District) ... reject (words=57<60 (after stripping ...))
//   [10/561] world_war_ii_memorial (World War II Memorial) ... reject (empty)
//   [5/10] bewitched_sculpture_samantha_statue (...) ... skip-commemorative-excluded (commemorative subject excluded: ...)
//
// Reject lines contain nested parens, so we capture status as a word and
// then take everything after as the detail (may or may not be wrapped).
// The poi name parenthetical is the FIRST '(...)' on the line — anchored
// after the id token. Use a non-greedy [^()]* for that segment.
const LINE_RE = /^\[(\d+)\/\d+\]\s+(\S+)\s+\(([^()]+)\)\s+\.\.\.\s+([\w-]+)(?:\s+(.+))?$/;

async function main() {
  const text = fs.readFileSync(LOG_PATH, 'utf8');
  const lines = text.split('\n');
  const records = [];
  for (const ln of lines) {
    const m = LINE_RE.exec(ln);
    if (!m) continue;
    const [, _idx, poi_id, name, status, detailRaw] = m;
    // Strip outer parens if the detail is parenthesized (`reject (words=57<60 (...))`).
    let detail = (detailRaw || '').trim();
    if (detail.startsWith('(') && detail.endsWith(')')) {
      detail = detail.slice(1, -1);
    }
    const wordsMatch = detail.match(/words=(\d+)/);
    const attemptsMatch = detail.match(/attempt[s ]?(\d+)|(\d+) attempts?/i);
    records.push({
      poi_id,
      name,
      status,
      reason: detail || null,
      words: wordsMatch ? parseInt(wordsMatch[1], 10) : null,
      attempts: attemptsMatch ? parseInt(attemptsMatch[1] || attemptsMatch[2], 10) : null,
    });
  }
  if (!records.length) {
    console.error('no records parsed from', LOG_PATH);
    process.exit(2);
  }

  // Run-start timestamp = file mtime (close enough — regen runs minutes-to-hours)
  const fileStat = fs.statSync(LOG_PATH);
  const runStartedAt = fileStat.mtime.toISOString();

  const pool = new Pool({ connectionString: process.env.DATABASE_URL });
  if (REPLACE) {
    const ids = [...new Set(records.map((r) => r.poi_id))];
    await pool.query('DELETE FROM salem_narration_regen_log WHERE poi_id = ANY($1)', [ids]);
    console.error(`deleted prior rows for ${ids.length} POIs`);
  }

  let inserted = 0;
  for (const r of records) {
    await pool.query(
      `INSERT INTO salem_narration_regen_log
        (poi_id, poi_name, status, reason, attempts, words, run_started_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7)`,
      [r.poi_id, r.name, r.status, r.reason, r.attempts, r.words, runStartedAt],
    );
    inserted++;
  }
  await pool.end();

  // Summary
  const tally = records.reduce((m, r) => { m[r.status] = (m[r.status] || 0) + 1; return m; }, {});
  console.error(`inserted ${inserted} rows. tally:`, JSON.stringify(tally, null, 2));
}

main().catch((e) => { console.error('FATAL:', e); process.exit(1); });
