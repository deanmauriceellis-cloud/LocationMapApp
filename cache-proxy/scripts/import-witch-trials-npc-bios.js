#!/usr/bin/env node
/*
 * import-witch-trials-npc-bios.js — S199 rewrite
 *
 * Source of truth for the bio text is now
 *   ~/Development/Salem/data/json/biographies/<id>.json (genbiographies output)
 * which carries a ≤150 word tts_full_text already vetted against the new
 * caps. We cross-reference
 *   ~/Development/Salem/data/json/npcs/<id>.json
 * for the metadata that biographies don't carry (role, faction, age in 1692,
 * historical_outcome, display_name).
 *
 * Cohort: Tier 1 + Tier 2 (the 49 People-of-Salem entries shipped in V1).
 *
 * Idempotent. Safe to re-run.
 *
 * Usage: node import-witch-trials-npc-bios.js
 */

const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');
const os = require('os');

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

const NPCS_DIR = path.join(os.homedir(), 'Development/Salem/data/json/npcs');
const BIOS_DIR = path.join(os.homedir(), 'Development/Salem/data/json/biographies');
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

function parseYear(raw) {
  if (raw === null || raw === undefined) return null;
  const s = String(raw).trim();
  if (!s) return null;
  const m = s.match(/(\d{4})/);
  return m ? parseInt(m[1], 10) : null;
}

function loadJson(p) {
  try { return JSON.parse(fs.readFileSync(p, 'utf8')); } catch (_) { return null; }
}

(async () => {
  // 1) Build Tier-1+2 NPC index from npcs/ for metadata.
  const npcIndex = new Map();
  for (const f of fs.readdirSync(NPCS_DIR).filter(f => f.endsWith('.json'))) {
    const n = loadJson(path.join(NPCS_DIR, f));
    if (!n) continue;
    if (n.tier === 1 || n.tier === 2) npcIndex.set(n.id, n);
  }

  // 2) Read biographies/ and pair each Tier-1+2 bio with its npc metadata.
  const bioFiles = fs.readdirSync(BIOS_DIR)
    .filter(f => f.endsWith('.json') && !f.startsWith('_'));

  const records = [];
  const missingNpc = [];
  const missingTts = [];
  for (const f of bioFiles) {
    const b = loadJson(path.join(BIOS_DIR, f));
    if (!b || (b.tier !== 1 && b.tier !== 2)) continue;
    const n = npcIndex.get(b.id);
    if (!n) { missingNpc.push(b.id); continue; }
    const tts = (b.tts_full_text || '').trim();
    if (!tts) { missingTts.push(b.id); continue; }
    records.push({ bio: b, npc: n, tts });
  }

  console.log(`Bios on disk: ${bioFiles.length} | Tier-1+2 NPCs in index: ${npcIndex.size} | Paired records: ${records.length}`);
  if (missingNpc.length) console.warn(`  ${missingNpc.length} biographies have no NPC metadata: ${missingNpc.slice(0,5).join(', ')}${missingNpc.length>5?'...':''}`);
  if (missingTts.length) console.warn(`  ${missingTts.length} biographies missing tts_full_text: ${missingTts.slice(0,5).join(', ')}${missingTts.length>5?'...':''}`);
  const missingBioIds = [...npcIndex.keys()].filter(id => !records.find(r => r.bio.id === id));
  if (missingBioIds.length) console.warn(`  ${missingBioIds.length} Tier-1+2 NPCs have no biography file yet: ${missingBioIds.slice(0,5).join(', ')}${missingBioIds.length>5?'...':''}`);

  if (records.length === 0) {
    console.error('FAILED: zero paired records — aborting');
    process.exit(1);
  }

  const upsert = `
    INSERT INTO salem_witch_trials_npc_bios
      (id, name, display_name, tier, role, faction,
       born_year, died_year, age_in_1692, historical_outcome,
       bio, data_source, confidence, verified_date, generator_model)
    VALUES
      ($1, $2, $3, $4, $5, $6,
       $7, $8, $9, $10,
       $11, $12, $13, NOW(), $14)
    ON CONFLICT (id) DO UPDATE SET
      name               = EXCLUDED.name,
      display_name       = EXCLUDED.display_name,
      tier               = EXCLUDED.tier,
      role               = EXCLUDED.role,
      faction            = EXCLUDED.faction,
      born_year          = EXCLUDED.born_year,
      died_year          = EXCLUDED.died_year,
      age_in_1692        = EXCLUDED.age_in_1692,
      historical_outcome = EXCLUDED.historical_outcome,
      bio                = EXCLUDED.bio,
      data_source        = EXCLUDED.data_source,
      confidence         = EXCLUDED.confidence,
      verified_date      = EXCLUDED.verified_date,
      generator_model    = EXCLUDED.generator_model,
      updated_at         = NOW()
  `;

  let ok = 0, fail = 0;
  for (const { bio, npc, tts } of records) {
    try {
      // born_year / died_year: prefer biographies/ born.date + died.date,
      // fall back to npcs/ born_year / died_year if biographies parse blank.
      const born = parseYear(bio?.born?.date) ?? parseYear(npc.born_year);
      const died = parseYear(bio?.died?.date) ?? parseYear(npc.died_year);
      const age1692 = (typeof npc.age === 'number') ? npc.age : null;
      const model = bio.model || 'salem-village (gemma3:27b)';
      await pool.query(upsert, [
        bio.id,
        bio.name || npc.name,
        npc.display_name || null,
        bio.tier,
        (npc.role || '').trim() || 'figure',
        (npc.faction || '').trim() || null,
        born,
        died,
        age1692,
        (npc.historical_outcome || '').trim() || null,
        tts,
        'salem_oracle_biography',
        0.9,
        model,
      ]);
      ok++;
    } catch (e) {
      fail++;
      console.error(`  FAIL [${bio.id}]:`, e.message);
    }
  }

  const { rows: [{ count }] } = await pool.query(
    "SELECT COUNT(*)::int AS count FROM salem_witch_trials_npc_bios WHERE deleted_at IS NULL"
  );
  console.log(`Upserted ${ok}/${records.length} (${fail} failures). Table now has ${count} active rows.`);
  await pool.end();
})().catch((e) => { console.error('FATAL:', e); process.exit(1); });
