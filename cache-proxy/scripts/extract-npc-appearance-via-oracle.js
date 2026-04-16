#!/usr/bin/env node
/*
 * extract-npc-appearance-via-oracle.js — Phase 9X.6 Pass A
 *
 * For each of the 49 Tier-1/2 figures in salem_witch_trials_npc_bios,
 * query Salem Oracle (:8088 /api/oracle/ask) for period-accurate
 * appearance details. Cache answers + primary_sources to
 *   cache-proxy/out/npc-appearance-cache.json
 *
 * Idempotent. Re-runs skip figures already cached unless --force is passed.
 *
 * Usage:
 *   node extract-npc-appearance-via-oracle.js           # fill gaps
 *   node extract-npc-appearance-via-oracle.js --force   # re-extract all
 *   node extract-npc-appearance-via-oracle.js --only=william_stoughton,tituba
 */

const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');

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
  if (!fs.existsSync(CACHE_PATH)) return { figures: {} };
  try {
    return JSON.parse(fs.readFileSync(CACHE_PATH, 'utf8'));
  } catch (e) {
    console.warn(`[warn] cache unreadable (${e.message}) — starting fresh`);
    return { figures: {} };
  }
}

function saveCache(cache) {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.writeFileSync(CACHE_PATH, JSON.stringify(cache, null, 2));
}

function buildQuestion(bio) {
  const age = bio.age_in_1692 != null && bio.age_in_1692 > 0
    ? `age ${bio.age_in_1692} in 1692`
    : `born c. ${bio.born_year || '?'}`;

  const roleHints = buildRoleHints(bio);

  return [
    `You are advising a historical portrait artist who must render ${bio.name} (${age}) as they would have appeared in Salem Village or Salem Town, 1692.`,
    `Role: ${bio.role}.`,
    bio.faction ? `Faction: ${bio.faction}.` : '',
    '',
    `Reconstruct their appearance in exhaustive visual detail from everything the historical record tells us, supplemented by what we know about dress and presentation norms for their exact station, occupation, age, gender, and faction in 1692 Massachusetts Bay Colony. If no direct physical description survives, reconstruct from contextual evidence and period norms — but be specific, not generic.`,
    '',
    `Cover ALL of the following, in this order:`,
    `1. GENDER and apparent AGE — how old do they look, not just their birth year.`,
    `2. BUILD — height, frame, weight, posture. Labor-worn or sedentary? How does their occupation show in their body?`,
    `3. FACE — shape, skin tone and texture, expression, notable features (scars, moles, lines). What emotion or bearing does their habitual expression convey?`,
    `4. HAIR — color, length, style. Head covering: for women, what kind of coif or cap (close-fitting linen coif, draped hood, kerchief)? For men, natural hair or periwig? If periwig, what style and length?`,
    `5. ROLE-IDENTIFYING VESTMENTS AND DRESS — this is critical. ${roleHints} Be specific about every garment layer visible from head to waist: undergarments showing at collar/cuff, middle layers, outer garments. Name the specific garment types (justaucorps, doublet, waistcoat, bodice, stays, petticoat, shift, gown, cassock, Geneva gown, bands, falling collar, etc). Specify fabrics (wool broadcloth, linen, silk, serge, kersey, fustian) and colors (black, dark brown, indigo, undyed, etc). What distinguishes THIS person's dress from a generic colonist? What marks their social rank, occupation, and faction visually?`,
    `6. ROLE-IDENTIFYING ACCESSORIES AND WORN OBJECTS — items on their person that signal who they are: Geneva bands (paired white linen tabs) for ministers, judicial chain or medal of office, apron for a goodwife, leather gloves, belt or girdle, spectacles if elderly and literate, clay pipe if documented. Include ONLY items worn on or carried on the person, not furniture or settings.`,
    `7. HANDS — what do their hands reveal? Calloused from labor, ink-stained from writing, soft from privilege? How are they positioned (folded, gripping, resting)?`,
    `8. BEARING AND POSTURE — how do they carry themselves? What does their posture communicate about their character? Upright and commanding, bent and frail, fidgeting and anxious?`,
    '',
    `Ground every detail in what the record says or what 1692 Massachusetts Bay Colony norms mandate for a person of this exact station. Do not use modern clothing vocabulary. Do not describe settings or backgrounds — ONLY the person themselves.`,
  ].filter(Boolean).join('\n');
}

function buildRoleHints(bio) {
  const role = (bio.role || '').toLowerCase();
  const faction = (bio.faction || '').toLowerCase();
  const s = role + ' ' + faction;

  if (/minister|reverend|clergy|pastor/.test(s)) {
    return 'This person is CLERGY in 1692 Puritan Massachusetts. Describe their specific clerical vestments in detail: the black Geneva gown (a long, loose academic robe), the white Geneva bands (paired rectangular linen tabs hanging from the collar — the single most identifying marker of a Puritan minister), the white linen shirt visible at collar and cuffs, the black skullcap or bare head, black breeches and stockings, plain black shoes with modest buckles. Ministers dressed distinctly from laypeople — their vestments were their identity.';
  }
  if (/judge|examiner|justice|magistrate|chief/.test(s)) {
    return 'This person is a JUDGE or MAGISTRATE — colonial legal authority at the highest level. Describe their specific judicial dress: formal black justaucorps (a long knee-length coat with large cuffs), matching waistcoat underneath, silk or linen cravat at the throat, fine linen shirt with ruffled cuffs showing, dark breeches, silk stockings, polished buckled shoes, and a formal periwig (full-bottomed or campaign style). They dress to project institutional power — every garment signals rank.';
  }
  if (/governor|constable|deputy|sheriff|marshal/.test(s)) {
    return 'This person is a COLONIAL OFFICIAL with civil authority. Describe their dress as formal but practical: a dark broadcloth coat, waistcoat, decent cravat or falling band collar, leather belt or baldric if a constable/marshal, possibly a staff of office or badge. They dress well but not as elaborately as a magistrate — functional authority, not courtroom grandeur.';
  }
  if (/physician|doctor/.test(s)) {
    return 'This person is a PHYSICIAN. In 1692 Massachusetts, physicians dressed as educated gentlemen: dark coat, waistcoat, decent periwig or well-kept natural hair, clean linen, possibly spectacles. Their hands and bearing should suggest education and close examination of patients.';
  }
  if (/merchant|wealthy|prosperous/.test(s) || /english faction|porter/.test(s)) {
    return 'This person is of the PROSPEROUS class (merchant, landowner, or allied with the wealthier Porter/English faction). Their clothing should show means: better-quality wool or even some silk, finer linen, a more fashionable cut, possibly some modest ornamentation like silver buttons or a decorative buckle. They dress well but within Puritan modesty norms — wealth shows in fabric quality and fit, not flashiness.';
  }
  if (/accused|hanged|executed|pressed|prison|confessor|recanter/.test(s)) {
    return 'This person is ACCUSED of witchcraft. Describe how they would have appeared BEFORE their arrest — in their normal station-appropriate dress, not prison clothing. Their social class and occupation determine their dress. If they are poor (like Sarah Good), their clothing should reflect poverty (worn, patched, undyed). If they are a respectable goodwife (like Rebecca Nurse), their clothing should reflect modest prosperity.';
  }
  if (/afflicted|accuser/.test(s)) {
    return 'This person is one of the ACCUSERS (afflicted girls/witnesses). Most were young women or girls from farming families. Their dress should reflect their age and modest rural station: linen coif or cap, plain bodice and petticoat, apron, simple linen collar, serviceable wool or linen fabrics in muted colors. Young girls wore simpler, less structured versions of adult women\'s clothing.';
  }
  return 'Describe clothing appropriate to their specific social rank, occupation, gender, and age in 1692 Massachusetts Bay Colony. Be specific about garment types, fabrics, and what distinguishes their dress from other colonists.';
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
    text: d.text || '',
    primary_source_count: d.primary_source_count || 0,
    fact_count: d.fact_count || 0,
    primary_sources: (d.primary_sources || []).slice(0, 6).map(s => ({
      verbatim_text: (s.verbatim_text || '').slice(0, 800),
      attribution: s.attribution || '',
      doc_type: s.doc_type || '',
      relevance: s.relevance || '',
    })),
    route: d.route || '',
    elapsed_ms: Date.now() - t0,
  };
}

async function oracleHealth() {
  try {
    const resp = await fetch(`${ORACLE_URL}/`, { signal: AbortSignal.timeout(3000) });
    return resp.ok || resp.status === 200;
  } catch (_) {
    return false;
  }
}

async function main() {
  console.log(`[pass-a] Oracle appearance extraction — ${ORACLE_URL}`);
  if (!(await oracleHealth())) {
    console.error('[fatal] Oracle not reachable. Is :8088 up?');
    process.exit(1);
  }

  const pool = new Pool({ connectionString: process.env.DATABASE_URL });
  const { rows: bios } = await pool.query(`
    SELECT id, name, tier, role, faction, born_year, died_year, age_in_1692, historical_outcome
    FROM salem_witch_trials_npc_bios
    WHERE deleted_at IS NULL
    ORDER BY tier ASC, name ASC
  `);
  await pool.end();

  const cache = loadCache();
  cache.meta = cache.meta || {};
  cache.meta.last_run = new Date().toISOString();
  cache.meta.oracle_url = ORACLE_URL;
  cache.figures = cache.figures || {};

  const targets = bios.filter(b => !ONLY || ONLY.has(b.id));
  console.log(`[pass-a] ${bios.length} figures total; ${targets.length} selected for this run`);
  if (ONLY) console.log(`[pass-a] --only filter: ${[...ONLY].join(', ')}`);

  let done = 0, skipped = 0, failed = 0;
  const failures = [];

  for (const bio of targets) {
    const cached = cache.figures[bio.id];
    if (cached && !FORCE && cached.answer && cached.answer.text) {
      skipped++;
      continue;
    }

    const n = done + skipped + failed + 1;
    process.stdout.write(`[${n}/${targets.length}] ${bio.name} (${bio.id}) ... `);
    try {
      const question = buildQuestion(bio);
      const answer = await askOracle(question);
      cache.figures[bio.id] = {
        id: bio.id,
        name: bio.name,
        tier: bio.tier,
        role: bio.role,
        faction: bio.faction,
        born_year: bio.born_year,
        died_year: bio.died_year,
        age_in_1692: bio.age_in_1692,
        historical_outcome: bio.historical_outcome,
        question,
        answer,
        extracted_at: new Date().toISOString(),
      };
      saveCache(cache);
      done++;
      console.log(`ok (${(answer.elapsed_ms / 1000).toFixed(1)}s, ${answer.primary_source_count} sources, ${answer.text.length} chars)`);
    } catch (e) {
      failed++;
      failures.push({ id: bio.id, name: bio.name, error: e.message });
      console.log(`FAIL: ${e.message}`);
    }
  }

  console.log(`\n[pass-a] done: ${done} extracted, ${skipped} skipped (cached), ${failed} failed`);
  console.log(`[pass-a] cache written to ${CACHE_PATH}`);
  if (failures.length) {
    console.log('[pass-a] failures:');
    for (const f of failures) console.log(`  - ${f.id}: ${f.error}`);
    process.exit(1);
  }
}

main().catch(e => {
  console.error('[fatal]', e);
  process.exit(1);
});
