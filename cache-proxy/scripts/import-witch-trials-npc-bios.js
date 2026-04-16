#!/usr/bin/env node
/*
 * import-witch-trials-npc-bios.js — Phase 9X.5
 *
 * Reads Tier-1 + Tier-2 NPC JSON files from
 *   ~/Development/Salem/data/json/npcs/
 * and upserts the 49 key figures into salem_witch_trials_npc_bios.
 *
 * Bio is assembled from the narrative.* subfields (life_before_1692,
 * role_in_crisis, emotional_arc, how_they_see_world, appearance) with
 * section headers. A "role_type" (judge/accuser/accused/clergy/official/other)
 * is derived from the JSON role+faction strings and stored in the
 * data_source JSON payload (we don't have a dedicated column for it;
 * the UI will re-derive it client-side from role as well).
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
const pool = new Pool({ connectionString: process.env.DATABASE_URL });

function parseYear(raw) {
  if (raw === null || raw === undefined) return null;
  const s = String(raw).trim();
  if (!s) return null;
  const m = s.match(/(\d{4})/);
  return m ? parseInt(m[1], 10) : null;
}

function assembleBio(npc) {
  const n = npc.narrative || {};
  const sections = [
    ['Life Before 1692', n.life_before_1692],
    ['Role in the Crisis', n.role_in_crisis],
    ['Emotional Arc', n.emotional_arc],
    ['How They Saw the World', n.how_they_see_world],
    ['Inner Landscape', n.emotional_landscape],
    ['Sermon Influence', n.sermon_influence],
    ['Appearance', n.appearance],
  ];
  const parts = [];
  for (const [label, text] of sections) {
    const t = (text || '').trim();
    if (!t) continue;
    parts.push(`## ${label}\n\n${t}`);
  }
  if (!parts.length) {
    const fallback = (npc.role || '').trim() || npc.name;
    return `## Role\n\n${fallback}`;
  }
  return parts.join('\n\n');
}

(async () => {
  const files = fs.readdirSync(NPCS_DIR).filter(f => f.endsWith('.json'));
  const keyFigures = [];
  for (const f of files) {
    let npc;
    try {
      npc = JSON.parse(fs.readFileSync(path.join(NPCS_DIR, f), 'utf8'));
    } catch (e) {
      continue;
    }
    if (npc.tier === 1 || npc.tier === 2) keyFigures.push(npc);
  }

  console.log(`Found ${keyFigures.length} Tier-1+Tier-2 figures in ${NPCS_DIR}`);
  if (keyFigures.length === 0) {
    console.error('FAILED: zero key figures located — aborting');
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
  for (const npc of keyFigures) {
    try {
      const bio = assembleBio(npc);
      const born = parseYear(npc.born_year);
      const died = parseYear(npc.died_year);
      const age1692 = (typeof npc.age === 'number') ? npc.age : null;
      await pool.query(upsert, [
        npc.id,
        npc.name,
        npc.display_name || null,
        npc.tier,
        (npc.role || '').trim() || 'figure',
        (npc.faction || '').trim() || null,
        born,
        died,
        age1692,
        (npc.historical_outcome || '').trim() || null,
        bio,
        'salem_corpus',
        0.9,
        'salem-corpus-npc-json',
      ]);
      ok++;
    } catch (e) {
      fail++;
      console.error(`  FAIL [${npc.id}]:`, e.message);
    }
  }

  const { rows: [{ count }] } = await pool.query(
    "SELECT COUNT(*)::int AS count FROM salem_witch_trials_npc_bios WHERE deleted_at IS NULL"
  );
  console.log(`Upserted ${ok}/${keyFigures.length} (${fail} failures). Table now has ${count} active rows.`);
  await pool.end();
})().catch((e) => { console.error('FATAL:', e); process.exit(1); });
