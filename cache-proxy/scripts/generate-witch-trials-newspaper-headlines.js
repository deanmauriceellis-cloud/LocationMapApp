#!/usr/bin/env node
/*
 * generate-witch-trials-newspaper-headlines.js — Phase 9X.4 S130
 *
 * For every row in salem_witch_trials_newspapers missing `headline`, call
 * Ollama salem-village:latest to produce:
 *   { headline: "5-8 WORD ALL-CAPS 1692 TABLOID", event_line: "one sentence." }
 * and store them. Idempotent — re-runs only fill nulls.
 *
 * Usage: node generate-witch-trials-newspaper-headlines.js
 */
const { Pool } = require('pg');
const path = require('path');
const fs = require('fs');

if (!process.env.DATABASE_URL) {
  try {
    const envContent = fs.readFileSync(path.resolve(__dirname, '../.env'), 'utf8');
    for (const line of envContent.split('\n')) {
      const t = line.trim();
      if (!t || t.startsWith('#')) continue;
      const eq = t.indexOf('=');
      if (eq === -1) continue;
      const k = t.slice(0, eq).trim();
      let v = t.slice(eq + 1).trim();
      if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
      if (!process.env[k]) process.env[k] = v;
    }
  } catch (_) {}
}

const OLLAMA = 'http://localhost:11434/api/generate';
const MODEL = 'salem-village:latest';
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

function buildPrompt(summary) {
  return `You are a sensational 1692 broadsheet editor. Given this news summary, produce JSON only (no preamble, no markdown):
{"headline": "<5-8 WORDS ALL CAPS, OLD-SCHOOL NEWSPAPER TABLOID STYLE>", "event_line": "<one crisp sentence stating the single most important event>"}

News summary: ${summary}`;
}

function parseResponse(text) {
  // Strip ```json fences if present
  let s = text.trim();
  s = s.replace(/^```(?:json)?\s*/i, '').replace(/\s*```\s*$/i, '');
  const firstBrace = s.indexOf('{');
  const lastBrace = s.lastIndexOf('}');
  if (firstBrace === -1 || lastBrace === -1) return null;
  try {
    const obj = JSON.parse(s.slice(firstBrace, lastBrace + 1));
    if (!obj.headline || !obj.event_line) return null;
    let h = String(obj.headline).trim();
    // Force uppercase, trim trailing punctuation-only tails
    h = h.toUpperCase();
    const e = String(obj.event_line).trim();
    return { headline: h, event_line: e };
  } catch (_) { return null; }
}

async function callOllama(prompt) {
  const res = await fetch(OLLAMA, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      model: MODEL,
      prompt,
      stream: false,
      options: { temperature: 0.7, num_predict: 200 }
    })
  });
  if (!res.ok) throw new Error(`Ollama ${res.status}`);
  const j = await res.json();
  return j.response || '';
}

(async () => {
  const { rows } = await pool.query(`
    SELECT id, date, summary, lede, tts_full_text
    FROM salem_witch_trials_newspapers
    WHERE deleted_at IS NULL AND (headline IS NULL OR headline_summary IS NULL)
    ORDER BY date ASC
  `);
  console.log(`Generating headlines for ${rows.length} newspapers…`);
  let done = 0, failed = 0;
  const started = Date.now();
  for (const r of rows) {
    const basis = (r.summary && r.summary.length > 60) ? r.summary
                  : (r.lede || r.tts_full_text || '').slice(0, 900);
    const prompt = buildPrompt(basis);
    let attempt = 0, result = null;
    while (attempt < 2 && !result) {
      attempt++;
      try {
        const raw = await callOllama(prompt);
        result = parseResponse(raw);
        if (!result) throw new Error(`parse-fail: ${raw.slice(0,120)}`);
      } catch (e) {
        if (attempt >= 2) {
          console.warn(`[${r.date}] FAILED attempt ${attempt}: ${e.message}`);
        }
      }
    }
    if (!result) {
      failed++;
      continue;
    }
    await pool.query(
      `UPDATE salem_witch_trials_newspapers
         SET headline = $1, headline_summary = $2, updated_at = now()
       WHERE id = $3`,
      [result.headline, result.event_line, r.id]
    );
    done++;
    const elapsed = (Date.now() - started) / 1000;
    const avg = elapsed / done;
    const eta = Math.round(avg * (rows.length - done));
    if (done % 5 === 0 || done <= 3) {
      console.log(`[${r.date}] ${done}/${rows.length} (avg ${avg.toFixed(1)}s, eta ${eta}s): ${result.headline}`);
    }
  }
  console.log(`\nDone: ${done} ok, ${failed} failed, total ${rows.length}. Elapsed ${((Date.now()-started)/1000).toFixed(1)}s.`);
  await pool.end();
})().catch((e) => { console.error('FAILED:', e); process.exit(1); });
