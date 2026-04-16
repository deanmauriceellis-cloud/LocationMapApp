#!/usr/bin/env node
/*
 * distill-npc-sd-prompts-via-oracle.js — Phase 9X.6 Pass A.2
 *
 * For each figure already cached by extract-npc-appearance-via-oracle.js,
 * send the long appearance narrative back to Oracle and ask it to distill
 * the content into a 40-60 word Stable Diffusion prompt tail. Writes
 * the distilled prompt into the same cache file at figures[id].sd_prompt.
 *
 * Oracle /api/oracle/ask is used with reset:true so each distill call
 * starts fresh and sees only the inline context passed in the question.
 *
 * Usage:
 *   node distill-npc-sd-prompts-via-oracle.js
 *   node distill-npc-sd-prompts-via-oracle.js --force
 *   node distill-npc-sd-prompts-via-oracle.js --only=tituba,rebecca_nurse
 */

const fs = require('fs');
const path = require('path');

const ORACLE_URL = process.env.SALEM_ORACLE_URL || 'http://localhost:8088';
const OUT_DIR = path.resolve(__dirname, '../out');
const CACHE_PATH = path.join(OUT_DIR, 'npc-appearance-cache.json');

const args = process.argv.slice(2);
const FORCE = args.includes('--force');
const ONLY = (() => {
  const arg = args.find(a => a.startsWith('--only='));
  if (!arg) return null;
  return new Set(arg.slice('--only='.length).split(',').map(s => s.trim()).filter(Boolean));
})();

function loadCache() {
  if (!fs.existsSync(CACHE_PATH)) {
    console.error(`[fatal] cache not found: ${CACHE_PATH}. Run extract-npc-appearance-via-oracle.js first.`);
    process.exit(1);
  }
  return JSON.parse(fs.readFileSync(CACHE_PATH, 'utf8'));
}

function saveCache(cache) {
  fs.writeFileSync(CACHE_PATH, JSON.stringify(cache, null, 2));
}

function buildDistillQuestion(fig) {
  const narrative = (fig.answer && fig.answer.text) || '';
  const ageStr = fig.age_in_1692 && fig.age_in_1692 > 0 ? `age ${fig.age_in_1692} in 1692` : `born c. ${fig.born_year || '?'}`;
  return [
    `Below is a detailed appearance reconstruction for ${fig.name} (${ageStr}, role: ${fig.role}). Your task: distill this into a Stable Diffusion prompt tail of 50 to 80 words for a head-and-shoulders pencil-sketch PORTRAIT. Describe ONLY the person's visible attributes. Use comma-separated descriptor phrases suitable for text-to-image generation, NOT narrative prose. Do NOT write complete sentences. Do NOT say "the record notes" or "likely" or "we can assume". Do NOT include personality, motivation, emotions-as-state, or historical context.`,
    '',
    `REQUIRED CATEGORIES (include ALL that appear in the reconstruction):`,
    `1. Gender, apparent age`,
    `2. Build visible at bust level (broad-shouldered, thin, frail)`,
    `3. Hair: color, style, and head covering (periwig type, coif style, skullcap, kerchief, bare)`,
    `4. Face: shape, expression, skin texture, notable features`,
    `5. ROLE-IDENTIFYING VESTMENTS — this is critical. Include EVERY specific garment named in the reconstruction that identifies their station: Geneva bands, Geneva gown, clerical collar, judicial justaucorps, ministerial black, periwig style, cravat, falling collar, waistcoat, bodice, stays, lace collar, apron, shift. Name the specific garment, not just "formal clothing". Fabrics and colors matter: black wool broadcloth, white linen, silk cravat, undyed kersey.`,
    `6. WORN ACCESSORIES that identify role: Geneva bands (paired white linen tabs — THE defining marker of Puritan clergy), silver buckles, leather belt, spectacles, clay pipe, lace cuffs, modest jewelry`,
    '',
    `EXPLICITLY ALLOWED (include these when present): Geneva bands, Geneva gown, clerical collar, skullcap, periwig, justaucorps, cravat, falling band collar, waistcoat, chain of office, apron, stays, bodice, petticoat top, lace cuffs, spectacles, clay pipe, buckled shoes, leather gloves, badge of office`,
    '',
    `STRICTLY FORBIDDEN: scene/environment (dungeon, prison, courtroom, church, pulpit, fireplace, background, landscape, setting), lighting (dim, dark, harsh, candlelight, shadows), full-body pose (standing, sitting, kneeling, walking), actions (praying, weeping, confessing, examining, holding, clutching, carrying), furniture/props (book, bible, staff, cane, stool, chair, bed, cross, lectern, gavel, table), camera/art-direction (close-up, wide shot, angle), abstract/mood (haunting, ominous, eerie, mysterious, tragic), environment/situation words of any kind`,
    '',
    `Output ONLY the comma-separated tail with no preamble, no quotation marks, no leading "A portrait of", no trailing period.`,
    '',
    `APPEARANCE RECONSTRUCTION:`,
    narrative,
    '',
    `Distilled SD prompt tail (40-60 words, comma-separated descriptors only):`,
  ].join('\n');
}

function cleanDistilledOutput(raw) {
  if (!raw) return '';
  let s = String(raw).trim();
  // Strip common preambles the LLM may add.
  s = s.replace(/^(here (is|'s)|here you go[:,\s]*|sure[:,\s]*|okay[:,\s]*|a portrait of|portrait of|an? image of)[:,\s]*/i, '');
  // Strip wrapping quotes.
  s = s.replace(/^["'`]+|["'`]+$/g, '');
  // Collapse whitespace.
  s = s.replace(/\s+/g, ' ').trim();
  // Strip trailing period.
  s = s.replace(/\.$/, '');
  return s;
}

async function askOracle(question) {
  const t0 = Date.now();
  const resp = await fetch(`${ORACLE_URL}/api/oracle/ask`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question, reset: true }),
    signal: AbortSignal.timeout(120000),
  });
  if (!resp.ok) throw new Error(`Oracle HTTP ${resp.status}: ${await resp.text().catch(() => '?')}`);
  const d = await resp.json();
  return {
    raw: d.text || '',
    elapsed_ms: Date.now() - t0,
  };
}

async function main() {
  console.log(`[pass-a.2] Oracle SD prompt distillation — ${ORACLE_URL}`);
  const cache = loadCache();
  const ids = Object.keys(cache.figures || {});
  if (!ids.length) {
    console.error('[fatal] no cached figures. Run Pass A first.');
    process.exit(1);
  }

  const targets = ids.filter(id => !ONLY || ONLY.has(id));
  console.log(`[pass-a.2] ${ids.length} figures cached; ${targets.length} selected for this run`);

  let done = 0, skipped = 0, failed = 0;
  const failures = [];

  for (const id of targets) {
    const fig = cache.figures[id];
    if (fig.sd_prompt && fig.sd_prompt.tail && !FORCE) {
      skipped++;
      continue;
    }

    const n = done + skipped + failed + 1;
    process.stdout.write(`[${n}/${targets.length}] ${fig.name} (${id}) ... `);
    try {
      const question = buildDistillQuestion(fig);
      const r = await askOracle(question);
      const tail = cleanDistilledOutput(r.raw);
      const wordCount = tail.split(/\s+/).filter(Boolean).length;
      fig.sd_prompt = {
        tail,
        raw: r.raw,
        word_count: wordCount,
        distilled_at: new Date().toISOString(),
        elapsed_ms: r.elapsed_ms,
      };
      saveCache(cache);
      done++;
      console.log(`ok (${(r.elapsed_ms / 1000).toFixed(1)}s, ${wordCount} words)`);
    } catch (e) {
      failed++;
      failures.push({ id, name: fig.name, error: e.message });
      console.log(`FAIL: ${e.message}`);
    }
  }

  console.log(`\n[pass-a.2] done: ${done} distilled, ${skipped} skipped (cached), ${failed} failed`);
  if (failures.length) {
    console.log('[pass-a.2] failures:');
    for (const f of failures) console.log(`  - ${f.id}: ${f.error}`);
    process.exit(1);
  }
}

main().catch(e => {
  console.error('[fatal]', e);
  process.exit(1);
});
