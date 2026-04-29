#!/usr/bin/env node
/*
 * generate-historical-narrations.js — Session 192 (2026-04-27)
 *
 * Fills salem_pois.historical_narration for HISTORICAL_BUILDINGS whose
 * effective build year is < 1860. Drives SalemIntelligence's chat endpoint
 * with a hardened pre-1860 prompt, with a GROUND_TRUTH block extracted from
 * the entity dump so the LLM never has to re-derive structured facts the KB
 * already has.
 *
 * Eligibility:
 *   category = 'HISTORICAL_BUILDINGS'
 *   AND deleted_at IS NULL
 *   AND intel_entity_id IS NOT NULL
 *   AND effective_year < 1860, where:
 *     effective_year = mode_c.built_year
 *                   ?? mode_c.construction_start_year
 *                   ?? lma.year_established
 *   (POIs with no resolvable pre-1860 year are skipped — written as empty.)
 *
 * Pipeline per POI:
 *   1. GET  /api/intel/entity/{intel_entity_id}/dump  → ground truth
 *   2. Build GROUND_TRUTH block (built_year, architect, builder,
 *      original_owner, architectural_styles, sources, pre-1860 description
 *      sentences only).
 *   3. POST /api/intel/chat with strict pre-1860 prompt. Length target =
 *      LONG (150-250 words) if witch-trial signal else SHORT (60-100 words).
 *   4. Validate: year regex >= 1860 / banned phrases / ASCII floor /
 *      word floor. One retry on failure, then EMPTY.
 *   5. UPDATE salem_pois SET historical_narration = ...
 *
 * Witch-trial signal: any relation describing a 1690-1700 event OR any
 * entity name token matching /witch.*trial|corwin|hathorne|parris|proctor/i.
 *
 * Usage:
 *   node scripts/generate-historical-narrations.js
 *   node scripts/generate-historical-narrations.js --dry-run
 *   node scripts/generate-historical-narrations.js --limit=3
 *   node scripts/generate-historical-narrations.js --ids=poi_id_1,poi_id_2
 *   node scripts/generate-historical-narrations.js --force
 *      (default: skip POIs that already have historical_narration populated)
 */

'use strict';

const { Pool } = require('pg');
const path = require('path');
const http = require('http');
const fs = require('fs');

// ── arg parsing ─────────────────────────────────────────────────────────────

const DRY_RUN = process.argv.includes('--dry-run');
const FORCE = process.argv.includes('--force');
const VERBOSE = process.argv.includes('--verbose');
const SHOW_REJECTED = process.argv.includes('--show-rejected');
const LIMIT = (() => {
  const arg = process.argv.find((a) => a.startsWith('--limit='));
  return arg ? parseInt(arg.split('=')[1], 10) : null;
})();
const ONLY_IDS = (() => {
  const arg = process.argv.find((a) => a.startsWith('--ids='));
  return arg ? arg.split('=')[1].split(',').map((s) => s.trim()).filter(Boolean) : null;
})();
const INTEL_BASE = process.env.INTEL_BASE || 'http://localhost:8089';
const PRE1860_CUTOFF = 1860;
const SALEM_JSON = process.env.SALEM_JSON || '/home/witchdoctor/Development/Salem/data/json';

// LMA POI id → local Salem building id. Operator-maintained (hand-curated for
// the most-documented POIs; the rest fall back to fuzzy match in
// findLocalBuildingId()).
const LOCAL_BUILDING_MAP = {
  the_witch_house_at_salem: 'corwin_house',
  the_witch_house: 'corwin_house',
  witch_house: 'corwin_house',
  gedney_house: 'gedney_house',
  narbonne_house: 'narbonne_house',
  john_ward_house: 'ward_house',
  ropes_mansion: 'ropes_mansion',
  derby_house: 'derby_house',
  joshua_ward_house: 'joshua_ward_house',
  peirce_nichols_house: 'peirce_nichols_house',
  crowninshield_bentley_house: 'crowninshield_bentley_house',
  custom_house: 'custom_house',
  hamilton_hall: 'hamilton_hall',
  old_town_hall: 'old_town_hall',
  scarlet_letter_house: 'mall_street_house',
};

// ── env loader (cribbed from generate-narration-from-intel.js) ──────────────

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

// ── http helper ─────────────────────────────────────────────────────────────

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
    let payload = null;
    if (body !== null) {
      payload = JSON.stringify(body);
      opts.headers['Content-Type'] = 'application/json';
      opts.headers['Content-Length'] = Buffer.byteLength(payload);
    }
    const req = http.request(opts, (res) => {
      let data = '';
      res.on('data', (c) => (data += c));
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try { resolve(JSON.parse(data)); } catch (e) { reject(new Error('Bad JSON: ' + e.message)); }
        } else {
          reject(new Error(`HTTP ${res.statusCode}: ${data.slice(0, 200)}`));
        }
      });
    });
    req.on('error', reject);
    req.on('timeout', () => { req.destroy(); reject(new Error('timeout')); });
    if (payload) req.write(payload);
    req.end();
  });
}

// ── local Salem JSON enrichment ────────────────────────────────────────────

let _allBuildings = null;
function loadAllBuildings() {
  if (_allBuildings) return _allBuildings;
  try {
    const p = path.join(SALEM_JSON, 'buildings/_all_buildings.json');
    _allBuildings = JSON.parse(fs.readFileSync(p, 'utf8'));
  } catch (_) { _allBuildings = []; }
  return _allBuildings;
}

function findLocalBuildingId(lmaPoiId, lmaName) {
  if (LOCAL_BUILDING_MAP[lmaPoiId]) return LOCAL_BUILDING_MAP[lmaPoiId];
  const buildings = loadAllBuildings();
  const norm = (s) => (s || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
  const lmaNorm = norm(lmaName);
  for (const b of buildings) {
    if (norm(b.name) === lmaNorm) return b.id;
    if (norm(b.id) === lmaNorm) return b.id;
  }
  return null;
}

function readLocalBuilding(localId) {
  if (!localId) return null;
  try {
    return JSON.parse(fs.readFileSync(path.join(SALEM_JSON, `buildings/${localId}.json`), 'utf8'));
  } catch (_) { return null; }
}

function extractBuildingFacts(b) {
  const lines = [];
  if (b.address) lines.push(`ADDRESS: ${b.address}`);
  if (b.construction) lines.push(`CONSTRUCTION: ${b.construction}`);
  if (b.dimensions) lines.push(`DIMENSIONS: ${b.dimensions}`);
  if (b.zone) lines.push(`LOCATION: ${b.zone}`);
  if (b.type) lines.push(`TYPE: ${b.type}`);
  if (Array.isArray(b.rooms) && b.rooms.length) {
    lines.push('ROOMS:');
    for (const r of b.rooms) {
      lines.push(`  - ${r.name}: ${r.description || ''}`);
      if (Array.isArray(r.furnishings) && r.furnishings.length) {
        lines.push(`      furnishings: ${r.furnishings.slice(0, 5).join('; ')}`);
      }
    }
  }
  const atm = b.atmosphere || {};
  if (atm.mood) lines.push(`MOOD: ${atm.mood}`);
  if (Array.isArray(atm.textures) && atm.textures.length) {
    lines.push(`TEXTURES: ${atm.textures.slice(0, 4).join('; ')}`);
  }
  if (Array.isArray(atm.smells) && atm.smells.length) {
    lines.push(`SMELLS: ${atm.smells.slice(0, 3).join('; ')}`);
  }
  if (Array.isArray(atm.sounds) && atm.sounds.length) {
    lines.push(`SOUNDS: ${atm.sounds.slice(0, 3).join('; ')}`);
  }
  return lines.join('\n');
}

// Pull NPC ids hinted by the building file (rooms/atmosphere often name people).
function extractNPCsFromBuilding(b) {
  const found = new Set();
  const text = JSON.stringify(b).toLowerCase();
  const candidates = [
    'jonathan_corwin', 'john_hathorne', 'george_corwin',
    'elizabeth_hathorne_porter', 'samuel_sewall', 'cotton_mather',
    'tituba', 'bridget_bishop', 'rebecca_nurse', 'giles_corey',
    'samuel_parris', 'abigail_williams', 'ann_putnam', 'thomas_putnam',
    'edward_putnam', 'martha_corey', 'john_proctor', 'elizabeth_proctor',
    'susannah_martin', 'sarah_good', 'sarah_osborne', 'george_burroughs',
    'increase_mather', 'william_phips', 'william_stoughton',
    'eleazor_gedney', 'bartholomew_gedney', 'john_gedney',
    'thomas_ives', 'john_ward',
    'richard_derby', 'elias_hasket_derby',
    'samuel_mcintire', 'jerathmiel_peirce',
    'william_bentley', 'nathaniel_hawthorne', 'andrew_safford',
    'charles_bulfinch', 'joshua_ward',
  ];
  for (const c of candidates) if (text.includes(c.replace(/_/g, ' '))) found.add(c);
  for (const c of candidates) if (text.includes(c)) found.add(c);
  return [...found];
}

function readBiography(npcId) {
  try {
    return JSON.parse(fs.readFileSync(path.join(SALEM_JSON, `biographies/${npcId}.json`), 'utf8'));
  } catch (_) { return null; }
}

// Sentence-filter to pre-1860, drop modern-language sentences. Used on
// long-form prose like biographies and historical_notes.
function filterPre1860Prose(text) {
  if (!text) return '';
  const sentences = text.split(/(?<=[.!?])\s+/).filter(Boolean);
  const ok = [];
  for (const s of sentences) {
    if (/\b(18[6-9]\d|19\d{2}|20\d{2})\b/.test(s)) continue;
    if (/\b(today|currently|now|modern|recently|memorial|exhibits|historical interpretation|museum operates|today's)\b/i.test(s)) continue;
    if (/\bremains a (?:physical link|reminder)\b/i.test(s)) continue;
    ok.push(s);
  }
  return ok.join(' ');
}

function extractBioFacts(bio) {
  const lines = [];
  if (bio.name) lines.push(`SUBJECT: ${bio.name}`);
  if (bio.born) lines.push(`BORN: ${bio.born.date || '?'}${bio.born.location ? ' in ' + bio.born.location : ''}`);
  if (bio.died) {
    const dy = parseInt((bio.died.date || '').match(/\d{4}/)?.[0] || '0', 10);
    if (dy && dy < PRE1860_CUTOFF) {
      lines.push(`DIED: ${bio.died.date}${bio.died.location ? ' in ' + bio.died.location : ''}`);
    }
  }
  if (bio.role_summary) lines.push(`ROLE: ${filterPre1860Prose(bio.role_summary)}`);
  if (bio.life_summary) lines.push(`LIFE: ${filterPre1860Prose(bio.life_summary)}`);
  return lines.filter((l) => !/^\s*(ROLE|LIFE):\s*$/.test(l)).join('\n');
}

// Find facts that mention a list of NPCs and have a pre-1860 date. Cap to
// avoid blowing up the prompt.
let _factsCache = null;
function loadFactsIndex() {
  if (_factsCache) return _factsCache;
  _factsCache = [];
  try {
    const dir = path.join(SALEM_JSON, 'facts');
    for (const f of fs.readdirSync(dir)) {
      if (!f.endsWith('.json')) continue;
      try {
        const d = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
        const yr = parseInt((d.date || '').match(/^\d{4}/)?.[0] || '0', 10);
        if (yr && yr < PRE1860_CUTOFF) _factsCache.push(d);
      } catch (_) {}
    }
  } catch (_) {}
  return _factsCache;
}

function findRelevantFacts(npcs, lmaName, max = 8) {
  if (!npcs.length) return [];
  const facts = loadFactsIndex();
  const npcSet = new Set(npcs);
  const nameNeedle = (lmaName || '').toLowerCase();
  const matched = [];
  for (const f of facts) {
    const involved = f.npcs_involved || [];
    const overlap = involved.some((n) => npcSet.has(n));
    if (!overlap) continue;
    const blob = `${f.title || ''} ${f.description || ''} ${f.location || ''}`.toLowerCase();
    // prefer facts that also reference this POI
    const buildingHit = nameNeedle && blob.includes(nameNeedle);
    matched.push({ fact: f, score: (buildingHit ? 10 : 0) + (involved.length === 1 ? 1 : 0) });
  }
  matched.sort((a, b) => b.score - a.score);
  return matched.slice(0, max).map((m) => m.fact);
}

async function fetchHistoricalNote(entityId) {
  try {
    const r = await httpRequestJson('GET', `${INTEL_BASE}/api/intel/entity/${entityId}/historical_note`, null, 15000);
    return r.historical_note || null;
  } catch (_) { return null; }
}

// ── ground-truth extraction ─────────────────────────────────────────────────

const WITCH_TRIAL_PATTERN = /witch.*trial|corwin|hathorne|parris|proctor|samuel sewall|cotton mather|salem village 1692|tituba|bridget bishop|rebecca nurse|giles corey/i;

// Names that indicate a commemorative artifact (statue/monument/plaque/etc.)
// rather than a building. ALL such POIs route to the tribute prompt path
// (S196 era-flexible), narrating the SUBJECT being commemorated. The subject
// may be pre-1860 (Roger Conant Statue, Witch Trials Memorial) or post-1860
// (Bewitched/Samantha Statue, WWII Memorial); the tribute prompt picks the
// voice based on subject era and the validator runs in tribute-relaxed mode.
// See feedback_commemoratives_are_historical_tributes.md.
const COMMEMORATIVE_NAME_PATTERN = /\b(statue|monument|plaque|marker|memorial|cenotaph|obelisk|tablet|bust|arch)\b/i;

function extractEffectiveYear(dump) {
  const mc = dump.mode_c_historic_building_details || {};
  if (typeof mc.built_year === 'number') return mc.built_year;
  if (typeof mc.construction_start_year === 'number') return mc.construction_start_year;
  return null;
}

function detectWitchTrialSignal(dump, effectiveYear) {
  // Year guard: a building only carries a credible witch-trial signal if it
  // existed at the time. Houses built post-1710 cannot have hosted 1692-era
  // examinations regardless of what the KB says about surname matches.
  if (!effectiveYear || effectiveYear < 1670 || effectiveYear > 1710) {
    // Allow direct-description override only — entity description must
    // literally name the trials. KB relations alone are not enough.
    const desc = ((dump.mode_a_entity || {}).description || '').toLowerCase();
    if (/\bwitch.*trial|salem witch trials\b/.test(desc)) return true;
    return false;
  }
  const name = (dump.entity_name || '').toLowerCase();
  if (WITCH_TRIAL_PATTERN.test(name)) return true;
  const desc = ((dump.mode_a_entity || {}).description || '').toLowerCase();
  if (WITCH_TRIAL_PATTERN.test(desc)) return true;
  for (const r of (dump.relations || [])) {
    const blob = `${r.relation_type || ''} ${r.to_entity_name || ''} ${r.description || ''}`;
    if (WITCH_TRIAL_PATTERN.test(blob)) return true;
    // event year heuristic — relation description containing a 1690-1700 year
    if (/\b169[0-9]\b|\b1700\b/.test(r.description || '')) return true;
  }
  return false;
}

function buildGroundTruthBlock(dump) {
  const lines = [];
  const mc = dump.mode_c_historic_building_details || {};
  const ma = dump.mode_a_entity || {};
  lines.push(`ENTITY NAME: ${dump.entity_name || ma.name || '(unknown)'}`);
  if (mc.built_year) lines.push(`BUILT YEAR: ${mc.built_year}`);
  else if (mc.construction_start_year) lines.push(`CONSTRUCTION STARTED: ${mc.construction_start_year}`);
  if (mc.construction_end_year && mc.construction_end_year !== mc.built_year) {
    lines.push(`CONSTRUCTION ENDED: ${mc.construction_end_year}`);
  }
  if (mc.architect) lines.push(`ARCHITECT: ${mc.architect}`);
  if (mc.builder) lines.push(`BUILDER: ${mc.builder}`);
  if (mc.original_owner) lines.push(`ORIGINAL OWNER: ${mc.original_owner}`);
  if (Array.isArray(mc.architectural_styles) && mc.architectural_styles.length) {
    lines.push(`ARCHITECTURAL STYLES: ${mc.architectural_styles.join(', ')}`);
  }

  // pre-1860 description sentences
  const desc = ma.description || '';
  if (desc) {
    const sentences = desc.split(/(?<=[.!?])\s+/);
    const safe = sentences.filter((s) => {
      if (/\b(18[6-9]\d|19\d{2}|20\d{2})\b/.test(s)) return false;
      if (/\b(today|currently|now |modern|recently|contemporary|present-day|tourists|operates|operated by|open to the public|admission|guided tour)\b/i.test(s)) return false;
      return true;
    });
    if (safe.length) lines.push(`PRE-1860 DESCRIPTION SENTENCES (verbatim from KB):\n  - ${safe.join('\n  - ')}`);
  }

  // pre-1860 relations: any relation whose description references a year < 1860 OR is not modern-tagged
  const preRelations = [];
  for (const r of (dump.relations || [])) {
    const d = r.description || '';
    if (/\b(18[6-9]\d|19\d{2}|20\d{2})\b/.test(d)) continue;
    // skip pure modern neighbor_of relations (district graph)
    if (r.relation_type === 'neighbor_of') continue;
    if (!d) continue;
    preRelations.push(`${r.relation_type}: ${d}`);
    if (preRelations.length >= 12) break;
  }
  if (preRelations.length) {
    lines.push(`PRE-1860 RELATIONS:\n  - ${preRelations.join('\n  - ')}`);
  }

  // sources
  const sources = (dump.sources || []).map((s) => s.url || s.collector).filter(Boolean);
  if (sources.length) lines.push(`SOURCES: ${sources.slice(0, 5).join(', ')}`);

  return lines.join('\n');
}

// ── prompt builders ─────────────────────────────────────────────────────────

const STRICT_RULES = [
  'STRICT RULES (violation of any rule means return only EMPTY):',
  '1. Use ONLY facts present in GROUND_TRUTH or facts retrieved from the SalemIntelligence knowledge base. Do NOT invent.',
  '2. Do NOT mention any year on or after 1860.',
  '3. Do NOT use any of these words/phrases: today, currently, now, modern, recently, contemporary, present-day, last century, 19th century late, 20th century, 21st century, visitors, tourists, museum, operates, operated by, open to the public, guided tour, admission, gift shop, designated, registered, listed, national register, national historic landmark, historic district, restoration, preserved, commemorate, commemorating, recognized, memorial plaque, today\'s.',
  '4. Do NOT describe any work, restoration, care, repair, attention, or modification done on the building AFTER 1859. Do NOT euphemize post-1859 work as "later care", "the passage of years", "in the years following", "an antiquarian later", "subsequent attention", etc. — simply omit it. Pre-1860 modifications and additions ARE in scope and may be mentioned.',
  '4b. Do NOT mention modern business names, restaurants, shops, salons, marketing firms, printing companies, real-estate agencies, or any commercial entity established after 1859. Do NOT list neighboring businesses or distances from them. Do NOT name people who are alive in your training knowledge — only name pre-1860 historical figures with documented connections to this site. If KB context includes such modern names, IGNORE them.',
  '5. If the subject is a commemorative artifact (statue / monument / plaque / memorial / marker / cenotaph / obelisk / tablet / bust), DO NOT describe the artifact itself. Narrate the pre-1860 SUBJECT being commemorated (the figure, event, or place) as if the artifact did not exist. If the commemorated subject is post-1860 or unknown, return EMPTY.',
  '6. Write in past tense. The narration should read as if delivered by a careful 1859 historian.',
  '7. Cover where grounded: full name, year built (or approximate decade), original owner/builder/architect, notable pre-1860 events or occupants, documented pre-1860 architectural features.',
  '8. Output English prose only. No bullet points, no headings, no markdown.',
  '9. If GROUND_TRUTH and KB context together do not yield enough material to meet the minimum word count below, return only the literal string EMPTY.',
  '10. If GROUND_TRUTH or retrieved knowledge-base context contains any banned phrase or post-1860 fact, OMIT that fact entirely. Do not transcribe it. Do not rephrase it ("location within X historic district" must simply not appear). The narration must be silent on modern designations even when the KB knows them.',
  '11. SAY WHAT YOU KNOW. NEVER narrate what you do NOT know. Do NOT include sentences that announce missing or unknown information. Forbidden phrasings include: "records do not detail …", "details remain scarce", "particulars are not within my recollection", "specific accounts are not (recorded|available)", "the names of … are not known", "though detailed accounts are not at hand", "lie beyond my recollection", "though I do not carry accounts", "while specific … remain elusive/obscured/unrecorded". If a fact is not grounded, simply OMIT that aspect — do not write a sentence acknowledging the gap. Silence is preferred to filler.',
  '12. ALLOWED — architectural classifications. You MAY use architectural style names such as "First Period", "Federal", "Federal style", "Georgian", "Georgian Revival", "Greek Revival", "Italianate", "Colonial Revival", "Adam style", "Federalist style", "Second Empire", "Gothic Revival", "Queen Anne", etc. even though some of these terms were coined by historians AFTER 1860. They describe pre-1860 architecture and are scholarly classifications, not modern designations. Use them confidently when grounded.',
  '13. NO REPETITION. Each sentence must add a NEW grounded fact. Do not restate the same fact in different words across sentences. Do not name the same architectural style twice. Do not echo a fact already given in the GROUND_TRUTH preamble unless adding new detail. Forbidden closure flourishes (final-paragraph filler that adds no fact): "remains a testament to …", "stands as a testament to …", "stands as a witness to …", "echoes of generations still linger", "lives lived within its walls", "the imprint of their daily lives", "speaks to a time of …", "the house itself speaks to …", "a tranquil escape", "a fitting backdrop", "a captivating journey", "offers a glimpse into …". Strike any sentence that uses such phrasing — there is no flourish quota. End on the last grounded fact, even if abrupt.',
  '14. NO META-NARRATION. Do not address the reader. Do not say "you are looking at", "you can see", "imagine yourself", "picture this", "as you walk past". The narration is third-person past-tense history.',
  '15. NAME ANCHOR. The first sentence MUST refer to the subject by its actual name (the name given at the top of this prompt). Do NOT substitute the name of a different building, person, or place — even if the knowledge base mentions one. If you cannot ground the named subject, return EMPTY.',
];

const GROUND_TRUTH_LINE_FILTERS = [
  /historic district/i,
  /national register/i,
  /national historic landmark/i,
  /\b(18[6-9]\d|19\d{2}|20\d{2})\b/,
  /\b(museum|tourists|visitors|admission|gift shop|guided tour)\b/i,
  /\d+\s*meter(s)?\b/i,   // SI district-graph distance leaks
  /\d+\s*feet\b/i,
  // modern present-tense person/role descriptions
  /\b(?:attends? to|manages?|operates?|is responsible for|oversees?|works? at)\b/i,
  // modern business/operator markers in relation descriptions
  /\b(?:salem trolley|haunted happenings|destination salem|llc|inc\.)\b/i,
  /\b(?:floral|printing|services|salon|realty|pizza|sushi|bistro|cafe|bakery|coffee|catering|foods|barbecue|bbq|brewing|brewery)\b/i,
];

function stripGroundTruth(block) {
  return block.split('\n').filter((line) => {
    for (const re of GROUND_TRUTH_LINE_FILTERS) if (re.test(line)) return false;
    return true;
  }).join('\n');
}

// The "Old Planters of Salem" — Conant's 1626 founding group from Cape Ann.
// Strictly the 1626 founders, not second-generation Salem/Beverly settlers.
const OLD_PLANTERS = [
  'Roger Conant', 'John Balch', 'Peter Palfrey', 'Thomas Gardner',
  'William Trask', 'John Woodbury', 'John Tilly', 'Walter Knight',
  'William Allen', 'Anthony Dike',
];

function buildTemporalFraming(year) {
  const lines = [`TEMPORAL FRAMING (the entity is dated ${year}; use vocabulary appropriate to that era):`];
  if (year >= 1626 && year <= 1691) {
    lines.push('- This entity precedes the Province charter (1692). Refer to the polity as "the Massachusetts Bay Colony", "the colony", or "the colonial settlement".');
    lines.push('- DO NOT use "Province of Massachusetts" or "Commonwealth of Massachusetts" — neither existed yet.');
  } else if (year >= 1692 && year <= 1775) {
    lines.push('- This entity falls under the Province charter (1692-1776). Refer to the polity as "the Province of Massachusetts Bay" or "the colony".');
    lines.push('- DO NOT use "Commonwealth of Massachusetts" — that designation did not exist until 1780.');
  } else if (year >= 1776 && year < 1780) {
    lines.push('- This entity is during the American Revolution but pre-Commonwealth. You may use "the colony" or "the newly-declared United States".');
    lines.push('- DO NOT use "Commonwealth of Massachusetts" — that designation did not exist until 1780.');
  } else if (year >= 1780) {
    lines.push('- This entity post-dates the Commonwealth charter (1780). You may use "the Commonwealth of Massachusetts" and "the United States".');
  }
  if (year < 1776) {
    lines.push('- DO NOT use "the United States", "America", or "American" — these were not in use before 1776 in this region.');
  }
  if (year >= 1626 && year <= 1660) {
    lines.push(`- OLD PLANTERS: If GROUND_TRUTH or KB context indicates the original owner or builder was among the Old Planters of Salem (Conant's 1626 founding group: ${OLD_PLANTERS.join(', ')}), mention this connection explicitly. If no such grounding exists, do NOT speculate.`);
  }
  return lines.join('\n');
}

function buildPrompt(name, year, lengthBucket, groundTruthBlock, witchTrial) {
  // S196 — single length band, hard cap 250 words. The previous RICH bucket
  // (up to 500w) produced padded, repetitive output that played 60–90s+ on
  // the device. Operator target: 100–250 words = ~45–110 seconds at TTS
  // rate 0.9x, fitting comfortably in a 2-minute walking-pace narration.
  // The lengthBucket arg is retained for call-site compatibility; both
  // values share the same rule below.
  const lengthRule = [
    'LENGTH: 100 to 250 words. HARD CAP 250. One or two short paragraphs.',
    '- Aim for ~150 words when grounding supports it.',
    '- If grounding only supports the basics, 100 words is fine. Brevity is correct when the facts are thin.',
    '- If grounding cannot support 60 words of strictly pre-1860, fact-grounded content WITHOUT padding or repetition, return only the literal string EMPTY.',
    '- DO NOT pad to reach a word target. Stop on the last grounded fact.',
    '- DO NOT exceed 250 words under any circumstance. Output longer than 250 words will be rejected.',
  ].join('\n');
  let focus = witchTrial
    ? 'CONTENT FOCUS: This entity has a documented connection to the 1692 Salem witch trials. Cover the trial connection in detail where grounded — accusations, examinations held here, named magistrates, named accused, named accusers, outcomes — staying within the pre-1860 cutoff.'
    : 'CONTENT FOCUS: Cover the original construction, original owner/builder/architect, notable pre-1860 occupants, notable pre-1860 events at this site, and documented pre-1860 architectural features.';
  // Anti-hallucination: entities built well after 1692 cannot have hosted /
  // predated the witch trials. Forbid that claim explicitly. Skipped when
  // year is null — the validator's banned-witch-trial-language patterns
  // and the GROUND_TRUTH itself are the gates.
  if (year && year > 1700) {
    focus += ` HARD FACT: This entity dates to ${year}, which is ${year - 1692} years AFTER the 1692 Salem witch trials. Do NOT claim it predates, witnessed, or was associated with the witch trials. The trials had concluded long before this entity existed.`;
  }
  // S193 — null year case (LMA year_established and SI mode_c.built_year both
  // unset). Tell gemma the entity is pre-1860 but we don't have an exact year;
  // forbid inventing one. The validator still strips any sentence that mentions
  // a post-1859 year, so a fabricated "1875" would get caught.
  const yearHeader = year
    ? `Built circa ${year}.`
    : 'Construction year is not in our records, but the entity is pre-1860. Do NOT invent a specific year — write only what is grounded in the GROUND_TRUTH below. If grounding does not support a year, say "by the early colonial period" / "during the seventeenth century" / "in the late eighteenth century" only when the grounded relations support it.';
  return [
    `You will write a strict pre-1860 historical narration for ${name} in Salem, Massachusetts. ${yearHeader}`,
    '',
    'GROUND_TRUTH (these are operator-verified facts; the narration must use them and must not contradict them):',
    groundTruthBlock,
    '',
    buildTemporalFraming(year || 1800),
    '',
    focus,
    '',
    STRICT_RULES.join('\n'),
    lengthRule,
    '',
    'Begin narration:',
  ].join('\n');
}

// S196 — commemorative tribute prompt (era-flexible). The subject of the
// narration is the figure / event / place being commemorated, NOT the
// artifact itself. The subject may be any era:
//
//   - Pre-1860 subject (Roger Conant, witch trials, Hawthorne) → narrate
//     in 1859-historian voice, past tense, pre-1860 vocabulary.
//   - Post-1860 subject (Bewitched filming, WWII servicemembers, Lydia
//     Pinkham) → narrate in present-day Salem voice, may use modern
//     vocabulary and post-1860 dates.
//
// The LLM picks the voice based on the inferred subject era. The validator
// runs in tribute-relaxed mode: skips year-cutoff strip and pre-1860 word
// bans (so "1970", "filming", "today" are allowed when narrating a modern
// commemorated subject). Anti-padding, anti-repetition, name-anchor, and
// 100-250w cap still apply.
function buildCommemorativeTributePrompt(name, groundTruthBlock) {
  const lengthRule = [
    'LENGTH: 100 to 250 words. HARD CAP 250. One or two short paragraphs.',
    '- Aim for ~150 words.',
    '- DO NOT exceed 250 words. Output longer than 250 words will be rejected.',
    '- DO NOT pad. Stop on the last grounded fact about the subject.',
  ].join('\n');
  // Lighter rules for tribute mode: drop pre-1860-specific bans (rules 2,
  // 3, 4, 4b, 6, 12 are pre-1860-historian voice constraints). Keep the
  // anti-padding / anti-repetition / no-meta-gap / name-anchor rules,
  // which are quality concerns that apply across eras.
  const TRIBUTE_RULES = [
    'TRIBUTE STRICT RULES (violation means return only EMPTY):',
    'T1. Use ONLY facts present in GROUND_TRUTH or facts retrievable from the SalemIntelligence knowledge base. Do NOT invent.',
    'T2. Output English prose only. No bullet points, no headings, no markdown headers, no preamble.',
    'T3. NO PADDING / NO REPETITION (mirrors STRICT_RULE 13). Each sentence must add a NEW grounded fact. Do not restate the same fact in different words. Forbidden closure flourishes: "remains a testament to …", "stands as a witness to …", "echoes still linger", "lives lived within these walls", "the imprint of their daily lives", "speaks to a time of …", "a fitting backdrop", "a captivating journey", "offers a glimpse into …". Strike any sentence that uses such phrasing — there is no flourish quota. End on the last grounded fact, even if abrupt.',
    'T4. NO META-NARRATION (mirrors STRICT_RULE 14). Do not address the reader. Do not say "you are looking at", "you can see", "imagine yourself", "picture this", "as you walk past". Third-person narration only.',
    'T5. NO META-GAP. SAY WHAT YOU KNOW. Do NOT include sentences that announce missing or unknown information. If a fact is not grounded, simply OMIT that aspect — do not write a sentence acknowledging the gap.',
    'T6. NO COMMERCIAL / MEMOIRESQUE PROMOTION. Do not mention modern business names that aren\'t directly part of the subject\'s story. No LLC/Inc. legal suffixes. No restaurant / shop / brewery names unless central to the commemorated subject (e.g. a brewery whose founder is the commemorated figure is OK).',
    'T7. NAME ANCHOR. The first sentence MUST refer to the subject by name (the commemorated figure / event / place — NOT the artifact name unless the artifact name and the subject are the same).',
    'T8. DO NOT describe the artifact itself in detail. The bronze, the plaque, the dedication year, the artist, the inscription wording — these are at most one passing sentence; the focus is the SUBJECT being commemorated. The narration should read as a story about the historical subject, with the commemorative artifact being the framing context.',
  ];
  return [
    `The Salem POI "${name}" is a commemorative artifact (statue, monument, plaque, memorial, marker, or arch). Your task is to narrate the SUBJECT being commemorated — the historical figure, event, or place that this artifact honors. The artifact itself is the framing context; the SUBJECT is the story.`,
    '',
    'TRIBUTE-MODE INSTRUCTIONS:',
    '1. From the artifact name and the GROUND_TRUTH below, identify the commemorated subject. Examples:',
    '   - "Roger Conant Statue" → Roger Conant the 1626 Salem founder',
    '   - "Salem Witch Trials Memorial" → the 1692 trials',
    '   - "Hawthorne Statue" → Nathaniel Hawthorne the author (1804–1864)',
    '   - "Bewitched Sculpture / Samantha Statue" → the 1970 filming of Bewitched in Salem and the show\'s connection to the city\'s pop-culture identity',
    '   - "World War II Memorial" → Salem servicemembers who fought in WWII',
    '   - "Washington Arch" → George Washington (1732–1799)',
    '   - "Viking Statue" → Leif Erikson and the Norse exploration of New England',
    '2. PICK THE VOICE BASED ON THE SUBJECT\'S ERA:',
    '   - If the subject is pre-1860 (founder, witch trials, colonial figure, Revolutionary-era figure, antebellum author/event), narrate in past tense as a careful 1859 historian. Use period-appropriate vocabulary: "the colony", "the Province of Massachusetts Bay", "the Commonwealth", etc. as the year permits.',
    '   - If the subject is post-1860 (Civil War, WWII, 20th-century cultural moment, modern-era figure, TV show, modern industry), narrate as a present-day Salem narrator. Past tense. You MAY use post-1860 dates ("1970", "2005"), modern vocabulary ("filming", "television", "veterans"), and reference the subject in its actual era.',
    '3. If the subject is genuinely unknown / cannot be inferred from name + KB grounding, return EMPTY.',
    '4. Salem connection is the storytelling hook — what does the commemorated subject have to do with Salem? Surface that connection prominently.',
    '5. DO NOT describe the artifact in detail. The story is about the SUBJECT.',
    '',
    'GROUND_TRUTH (operator-verified facts; use what fits the subject\'s story):',
    groundTruthBlock,
    '',
    TRIBUTE_RULES.join('\n'),
    lengthRule,
    '',
    'Begin subject narration:',
  ].join('\n');
}

// ── validator ───────────────────────────────────────────────────────────────

// Word-boundary regex patterns. Each must match a violation in the lowercased
// text. Use \b boundaries so substring false-positives (e.g. "snow image"
// matching "now ") don't fire.
const BANNED_PATTERNS = [
  /\btoday\b/, /\btoday's\b/,
  /\bcurrently\b/, /\bpresently\b/,
  /\bnow\b/,                                // word-boundary fixes "snow" / "known"
  /\bmodern\b/, /\brecently\b/, /\bcontemporary\b/,
  /\bpresent-?day\b/,
  /\blast century\b/, /\bin the last (?:century|years)\b/,
  /\b19th century\b/, /\b20th century\b/, /\b21st century\b/,
  /\bnineteenth century late\b/, /\btwentieth century\b/,
  /\btwenty-?first century\b/,
  /\blate nineteenth century\b/, /\bend of the nineteenth\b/,
  /\blate 19th\b/,
  /\bvisitors?\b/, /\btourists?\b/,
  /\bmuseum\b/,
  /\boperates\b/, /\boperated by\b/,
  /\bopen to the public\b/, /\bguided tour\b/,
  /\badmission\b/, /\bgift shop\b/,
  /\bdesignated\b/, /\bregistered\b/, /\blisted\b/,
  /\bnational register\b/, /\bnational historic landmark\b/,
  /\bhistoric district\b/,
  /\brestoration\b/, /\bpreserv(?:e|es|ed|ing|ation)\b/,
  /\bcommemorat/, /\brecognized\b/,
  /\brecent (?:times|years|decades|memory|past|inquiry)\b/,
  /\bin (?:more |most |these )?recent\b/,
  /\bPEM\b/,                                // Peabody Essex Museum acronym (modern)
  /\bmemorial plaque\b/,
  /\b1900s\b/,
  // modern-continuity paraphrases
  /\bstill owned\b/, /\bstill stands?\b/, /\bstill standing\b/,
  /\bcontinues to\b/, /\bcontinuing the\b/, /\bcontinuing its\b/,
  /\bremains in use\b/,
  /\battended to (?:the house|it)\b/,
  /\bcontinued standing\b/, /\bits legacy\b/,
  /\bmaintain (?:its?|the)\b/,
  /\benduring presence\b/, /\blong been\b/, /\bensuring its\b/,
  // post-1860 corporate / publishing references that smuggle in via the KB
  /\bdetroit publishing\b/, /\bpublishing co\.?\b/, /\bpublishing company\b/,
  // SI district-graph distance leaks (model also tries "paces" as evasion)
  /\d+\s*meters?\b/, /\bmeters? (?:from|away)\b/,
  /\d+\s*feet\b/, /\bfeet (?:from|away)\b/,
  /\bpaces (?:from|away|distant)\b/,
  /\d+\s*paces\b/,
  // spelled-out number + paces / meters / feet (e.g. "one hundred and fourteen paces", "three meters")
  /\b(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|thousand)(?:[\s-]+(?:and\s+)?(?:one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety|hundred|thousand))*\s+(?:paces|meters?|feet)\b/,
  // multi-number list (3+ comma-separated numbers) — catches enumerated distances
  /\d+(?:\s*,\s*\d+){2,}/,
  // modern business / continuity
  /\bto this day\b/, /\bto the present\b/, /\bto our time\b/, /\bto our day\b/,
  // modern business suffixes (catches lists like "X Floral, Y Printing, Z Services")
  // (lowercased — validator lowercases the text before testing)
  /\b(?:llc|inc\.|incorporated|corp\.|corporation|l\.?l\.?c\.?|ltd\.?)\b/,
  /\b(?:floral|printing|marketing|services|salon|realty|realtors|pizza|sushi|bistro|café|cafe|bakery|coffee|catering|foods|barbecue|bbq|brewing|brewery|rockafella)\b/,
  // modern Salem-area tour/operator names
  /\bsalem trolley\b/, /\bsalem on foot\b/, /\bhaunted happenings\b/, /\bdestination salem\b/,
  // modern designations / phrases
  /\bhistoric streets\b/, /\bhistoric neighborhood\b/, /\battend to the affairs\b/,
  // META-GAP phrases (operator S192: never narrate what we don't know)
  /\brecords do not\b/, /\bno records detail\b/, /\bno records describe\b/,
  /\bdetails (?:are|remain) (?:scarce|elusive|obscured|unrecorded|not (?:fully )?recorded|not (?:readily |fully )?(?:known|available|at hand|recalled))\b/,
  /\bdetails .{0,40}? do not come to mind\b/,
  /\bparticulars (?:are|remain) (?:not (?:within|fully|readily)|scarce|elusive|obscured|unrecorded|less readily recalled)\b/,
  /\bspecific (?:accounts|details) (?:are|remain) (?:scarce|elusive|obscured|not (?:fully |readily )?(?:known|recorded|recalled|available|at hand))\b/,
  /\baccounts (?:are|remain) (?:scarce|elusive|obscured|unrecorded|not (?:fully |readily )?(?:known|recorded|available))\b/,
  /\baccounts do not\b/, /\bbeyond .{0,40}? (?:its|his|her|their) (?:municipal|original)\b/,
  /\bthe names of .{1,40}? are not (?:known|recorded|recalled|available)\b/,
  /\bnot (?:fully |readily |yet )?(?:known|recorded|recalled|available|at hand|detailed) (?:to|in|within|by|here)\b/,
  /\bnot (?:fully |readily )?within (?:my|our) (?:recollection|knowledge)\b/,
  /\bnot (?:fully |readily )?at hand\b/,
  /\blie(?:s)? beyond (?:my|our|the) (?:present |scope of |recollection|knowledge|memory)/,
  /\bobscured (?:to|by) (?:me|us|present (?:inquiry|recollection)|the (?:passage of time|records))/,
  /\bare not (?:fully |readily )?recalled (?:to|by) (?:me|us)/,
  /\bi do not (?:carry|possess|have)\b/,                             // (lowercase, since validator lowercases text)
  /\bdo not (?:come to mind|reach me|reach us)\b/,
  // Catch-all: "are/is/remain not (fully|readily|well)? (detailed|known|recorded|recalled|available|at hand)"
  /\b(?:are|is|remain(?:s)?) not (?:fully |readily |well )?(?:detailed|known|recorded|recalled|available|at hand)\b/,
  /\b(?:detailed|known|recorded|recalled|available|at hand) (?:to|in|within|by) (?:me|us|present)\b/,
  /\bare lost to (?:time|history|us)\b/,
  /\bremain(?:s)? (?:obscured|elusive|fragmented|unrecorded|scarce|shadowed)/,
  // Inverse: phrases meaning "the records don't have this" via roundabout grammar
  /\bin (?:the |these |available |extant )?records at hand\b/,
  /\bavailable in (?:these |the )?records\b/,
  /\bin (?:the |these )?records (?:available|to me|to us|of the time)\b/,
  /\bnot specifically detailed\b/,
  /\bto (?:my|our) (?:recollection|knowledge|memory)\b/,
  /\bin (?:my|our) (?:recollection|knowledge|memory)\b/,
  /\b(?:though|while) the .{0,30}? remain(?:s)? (?:obscured|scarce|elusive|unrecorded)/,
];

const PRE_1776_PATTERNS = [/\bunited states\b/, /\bamerica\b/, /\bamerican\b/];
const PRE_1780_PATTERNS = [/\bcommonwealth of massachusetts\b/];

// Per-sentence validity. Returns the offending pattern name(s) or null.
function sentenceViolations(sentence, effectiveYear) {
  const lower = sentence.toLowerCase();
  const hits = [];
  // year violations
  const yearHits = lower.match(/\b(18[6-9]\d|19\d{2}|20\d{2})\b/g);
  if (yearHits) hits.push(`year:${[...new Set(yearHits)].join(',')}`);
  // banned patterns
  for (const re of BANNED_PATTERNS) {
    if (re.test(lower)) { hits.push(re.source.replace(/\\b/g, '').replace(/[\\\(\)\[\]\?]/g, '')); break; }
  }
  if (effectiveYear && effectiveYear < 1776) {
    for (const re of PRE_1776_PATTERNS) {
      if (re.test(lower)) { hits.push('pre-1776:' + re.source.replace(/\\b/g, '')); break; }
    }
  }
  if (effectiveYear && effectiveYear < 1780) {
    for (const re of PRE_1780_PATTERNS) {
      if (re.test(lower)) { hits.push('pre-1780:' + re.source.replace(/\\b/g, '')); break; }
    }
  }
  return hits.length ? hits.join(',') : null;
}

// Common abbreviations that end with a period but do NOT terminate a sentence.
const ABBREVIATIONS = /\b(?:Mr|Mrs|Ms|Dr|St|Jr|Sr|Co|Inc|Ltd|Capt|Rev|Hon|Gov|Pres|Maj|Gen|Lt|Sgt|Mt|Ft|U\.S|U\.K|i\.e|e\.g|etc|vol|no|pp)\.\s*$/;

function splitSentences(text) {
  // Sentence split that respects common abbreviations: only split AFTER .!? when
  // (a) followed by whitespace AND a capital letter, AND (b) the chunk before
  // doesn't end with a known abbreviation. This avoids breaking up
  // "Detroit Publishing Co. captured..." and "Mr. Hawthorne wrote..."
  const raw = text.split(/(?<=[.!?])\s+/);
  const out = [];
  for (const piece of raw) {
    if (out.length && ABBREVIATIONS.test(out[out.length - 1])) {
      out[out.length - 1] = out[out.length - 1] + ' ' + piece;
    } else if (out.length && /^[a-z]/.test(piece)) {
      // chunk starts with lowercase — almost certainly a continuation
      out[out.length - 1] = out[out.length - 1] + ' ' + piece;
    } else {
      out.push(piece);
    }
  }
  return out.filter(Boolean);
}

const PRONOUN_ANCHOR = /^\s*(?:He|She|His|Her|Hers|Him|Their|They|Them|It|Its|This|That|These|Those)\b/;

function validate(text, lengthBucket, effectiveYear, poiName) {
  const failures = [];
  const trimmed = (text || '').trim();
  if (!trimmed) return { ok: false, reason: 'empty' };
  if (trimmed === 'EMPTY') return { ok: false, reason: 'model-said-empty' };

  // ASCII floor (catch CJK gemma flakes) — applied first to whole text
  let asciiCount = 0;
  for (let i = 0; i < trimmed.length; i++) {
    if (trimmed.charCodeAt(i) < 128) asciiCount++;
  }
  const asciiRatio = asciiCount / trimmed.length;
  if (asciiRatio < 0.95) return { ok: false, reason: `ascii_ratio=${asciiRatio.toFixed(2)}` };

  // S196 — name anchor. The first ~240 chars must contain a recognizable
  // token from the POI name. Catches LLM hallucinations where a different
  // entity name slips in (e.g. "Fairbanks House" leaking into a Witch House
  // narration). For tribute mode the anchor token is also expected — since
  // the commemorated subject's name typically matches a token from the POI
  // name (e.g. "Hawthorne Statue" → "hawthorne"). Stop-words are ignored.
  if (poiName) {
    const STOP = new Set(['the','of','and','a','an','at','in','on','to','from','for','salem','house','hall','site','park','square','street','company','salem,','massachusetts','ma','usa','statue','monument','plaque','marker','memorial','cenotaph','obelisk','tablet','bust','arch','sculpture']);
    const tokens = poiName.toLowerCase()
      .replace(/[^a-z0-9 ]+/g, ' ')
      .split(/\s+/)
      .filter((t) => t.length >= 3 && !STOP.has(t));
    if (tokens.length) {
      const head = trimmed.slice(0, 240).toLowerCase();
      const hit = tokens.some((t) => head.includes(t));
      if (!hit) {
        return {
          ok: false,
          reason: `name-anchor-miss: first 240 chars contain none of [${tokens.join(',')}]`,
          rejected_text: trimmed.slice(0, 200),
        };
      }
    }
  }

  // S196 — tribute mode (any-era subject) skips the per-sentence year-cutoff
  // and pre-1860 banned-phrase strip. The tribute prompt's TRIBUTE_RULES
  // handle quality concerns; the validator's residual job is name-anchor +
  // ASCII floor + word-count cap + EMPTY check + (light) sentence cleanup.
  // For tribute mode we skip the strip phase entirely and let the prompt
  // bear the quality load.
  const isTribute = lengthBucket === 'TRIBUTE';
  if (isTribute) {
    const cleanText = trimmed;
    let words = cleanText.split(/\s+/).length;
    const MIN_WORDS = 60;
    const MAX_WORDS = 250;
    if (words < MIN_WORDS) {
      return { ok: false, reason: `tribute-words=${words}<${MIN_WORDS}`, words, cleanText };
    }
    let truncatedSentences = 0;
    let outText = cleanText;
    if (words > MAX_WORDS) {
      const tribKept = splitSentences(cleanText);
      const truncated = [];
      let runningWords = 0;
      for (const s of tribKept) {
        const sw = s.trim().split(/\s+/).length;
        if (runningWords + sw > MAX_WORDS) break;
        truncated.push(s);
        runningWords += sw;
      }
      if (truncated.length === 0) {
        return { ok: false, reason: `tribute-over-cap-unrecoverable`, words, cleanText };
      }
      truncatedSentences = tribKept.length - truncated.length;
      outText = truncated.join(' ').trim();
      words = outText.split(/\s+/).length;
    }
    return {
      ok: true,
      words,
      cleanText: outText,
      stripped: 0,
      truncatedSentences: truncatedSentences || null,
      droppedReasons: null,
      tribute: true,
    };
  }

  // Sentence-level scan: drop violating sentences AND following pronoun-anchored
  // sentences (orphan-pronoun rescue — "He undertook..." dangles if its
  // antecedent sentence was stripped).
  const sentences = splitSentences(trimmed);
  const kept = [];
  const droppedReasons = [];
  let dropNext = false;
  for (const s of sentences) {
    const v = sentenceViolations(s, effectiveYear);
    if (v) {
      droppedReasons.push(v);
      dropNext = true;
      continue;
    }
    if (dropNext && PRONOUN_ANCHOR.test(s)) {
      droppedReasons.push('orphan-pronoun');
      // dropNext stays true — chain through any further pronoun sentences
      continue;
    }
    dropNext = false;
    kept.push(s);
  }
  let cleanText = kept.join(' ').trim();
  let words = cleanText ? cleanText.split(/\s+/).length : 0;
  // Operator decision (S192): if grounding is sparse, accept short output.
  // Floor is 60 words. S196 — added 250-word ceiling per operator (target
  // 100-250w, fits ~2 min walking-pace TTS). Over-cap output is truncated
  // at the last sentence boundary that keeps us ≤ 250 words; if truncation
  // can't get us under, we reject so a retry can try harder.
  const MIN_WORDS = 60;
  const MAX_WORDS = 250;

  if (!cleanText) {
    return { ok: false, reason: `all-sentences-violated: ${droppedReasons.slice(0,3).join(' / ')}`, words: 0 };
  }
  if (words < MIN_WORDS) {
    return {
      ok: false,
      reason: `words=${words}<${MIN_WORDS} (after stripping ${sentences.length - kept.length} violating sentences: ${droppedReasons.slice(0,3).join(' / ')})`,
      words,
      cleanText,
    };
  }
  let truncatedSentences = 0;
  if (words > MAX_WORDS) {
    // Walk kept[] from the start, accumulating sentences until adding the
    // next would exceed MAX_WORDS. Output is truncated at the last full
    // sentence boundary ≤ MAX_WORDS. If even one sentence exceeds the cap
    // (very long single sentence), we reject so the retry path can re-prompt
    // with a tighter bias.
    const truncated = [];
    let runningWords = 0;
    for (const s of kept) {
      const sw = s.trim().split(/\s+/).length;
      if (runningWords + sw > MAX_WORDS) break;
      truncated.push(s);
      runningWords += sw;
    }
    if (truncated.length === 0) {
      return {
        ok: false,
        reason: `over-cap-unrecoverable: first sentence already > ${MAX_WORDS} words`,
        words,
        cleanText,
      };
    }
    truncatedSentences = kept.length - truncated.length;
    cleanText = truncated.join(' ').trim();
    words = cleanText.split(/\s+/).length;
  }
  return {
    ok: true,
    words,
    cleanText,
    stripped: sentences.length - kept.length,
    truncatedSentences: truncatedSentences || null,
    droppedReasons: droppedReasons.length ? droppedReasons : null,
  };
}

// ── per-POI generation ──────────────────────────────────────────────────────

async function generateForPoi(poi) {
  const result = {
    poi_id: poi.id,
    name: poi.name,
    intel_entity_id: poi.intel_entity_id,
    effective_year: null,
    length_bucket: null,
    narration: null,
    status: 'unknown',
    note: null,
    attempts: 0,
  };

  let dump;
  try {
    dump = await httpRequestJson('GET', `${INTEL_BASE}/api/intel/entity/${poi.intel_entity_id}/dump`, null, 30000);
  } catch (e) {
    result.status = 'skip-no-dump';
    result.note = e.message;
    return result;
  }

  // S196 — commemorative routing (era-flexible). If the POI's name matches
  // the commemorative pattern (statue/monument/plaque/memorial/...), it
  // gets the tribute prompt path: narrate the SUBJECT, not the artifact.
  // The subject may be any era — pre-1860 subjects get historian voice,
  // post-1860 subjects get present-day Salem voice. No exclusions: every
  // Salem-connected commemorative narrates per operator policy.
  const lmaName = poi.name || '';
  const siName = dump.entity_name || '';
  const isCommemorative = COMMEMORATIVE_NAME_PATTERN.test(lmaName) || COMMEMORATIVE_NAME_PATTERN.test(siName);
  if (isCommemorative) {
    result.is_commemorative = true;
  }

  const bcsYear = extractEffectiveYear(dump);
  const effYear = bcsYear || poi.year_established;
  result.effective_year = effYear;

  // S193: only HARD-SKIP when year is known and ≥ 1860 AND not a commemorative.
  // Commemoratives skip this guard — their year_established is the artifact's
  // installation year (post-1860 by definition), but the SUBJECT being
  // commemorated may well be pre-1860. The tribute prompt + STRICT_RULES gate
  // the actual output.
  if (!isCommemorative && effYear && effYear >= PRE1860_CUTOFF) {
    result.status = 'skip-not-pre1860';
    result.note = `effective_year=${effYear}`;
    return result;
  }

  const witchTrial = detectWitchTrialSignal(dump, effYear || 1700);
  // S196 — single 100-250w bucket; argument retained for compatibility but
  // both labels share the same length rule now.
  const lengthBucket = (effYear && effYear < 1800) ? 'RICH' : 'STANDARD';
  result.length_bucket = isCommemorative ? 'TRIBUTE' : lengthBucket;

  // Build enriched GROUND_TRUTH from every available source.
  const blocks = [];
  blocks.push(stripGroundTruth(buildGroundTruthBlock(dump)));

  // SI's existing historical_note (sentence-filter pre-1860)
  const siNote = await fetchHistoricalNote(poi.intel_entity_id);
  const siNoteFiltered = filterPre1860Prose(siNote);
  if (siNoteFiltered && siNoteFiltered.length > 30) {
    blocks.push('SALEM_INTELLIGENCE HISTORICAL NOTES (pre-1860 sentences):\n' + siNoteFiltered);
  }

  // Local Salem JSON enrichment
  const localId = findLocalBuildingId(poi.id, poi.name);
  if (localId) {
    const local = readLocalBuilding(localId);
    if (local) {
      blocks.push('LOCAL BUILDING DATA:\n' + extractBuildingFacts(local));
      const npcs = extractNPCsFromBuilding(local);
      // biographies (sentence-filtered)
      const bioBlocks = [];
      for (const npc of npcs.slice(0, 4)) {
        const bio = readBiography(npc);
        if (bio) bioBlocks.push(`BIOGRAPHY (${npc}):\n${extractBioFacts(bio)}`);
      }
      if (bioBlocks.length) blocks.push(bioBlocks.join('\n\n'));
      // pre-1860 dated facts mentioning these figures and this site
      const facts = findRelevantFacts(npcs, poi.name);
      if (facts.length) {
        const factLines = facts.map((f) => `  - ${f.date || '?'} | ${f.title || ''}: ${(f.description || '').slice(0, 220)}`);
        blocks.push('PRE-1860 DOCUMENTED FACTS:\n' + factLines.join('\n'));
      }
      result.local_id = localId;
      result.npcs_found = npcs.length;
      result.facts_found = facts.length;
    }
  }

  const groundTruth = blocks.filter(Boolean).join('\n\n');
  const prompt = isCommemorative
    ? buildCommemorativeTributePrompt(poi.name, groundTruth)
    : buildPrompt(poi.name, effYear, lengthBucket, groundTruth, witchTrial);

  const MAX_ATTEMPTS = 3;
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    result.attempts = attempt;
    let resp;
    try {
      resp = await httpRequestJson('POST', `${INTEL_BASE}/api/intel/chat`, { query: prompt, top_k: 15 }, 90000);
    } catch (e) {
      result.status = 'fail-chat';
      result.note = `attempt ${attempt}: ${e.message}`;
      if (attempt === MAX_ATTEMPTS) return result;
      continue;
    }
    // strip leading non-ASCII glyphs, gemma3 special tokens, markdown headers
    let ans = (resp.answer || '').replace(/<\|?(?:unused\d+|im_(?:start|end)|begin|end)[^>]*\|?>/g, '');
    ans = ans.replace(/^[^\x00-\x7F\s]+/, '');
    // strip leading markdown headers like "## Your answer" or "**Narration:**"
    ans = ans.replace(/^\s*#+\s+[^\n]*\n+/g, '');
    ans = ans.replace(/^\s*\*\*[^*\n]+\*\*\s*:?\s*\n+/g, '');
    // anchor to first occurrence of the entity name within the first 250 chars
    // (gemma3 sometimes leaks a meta-preamble like "elegantly crafted...rules."
    // or "Certainly, here is the narration:" before the actual content)
    const nameKey = (poi.name || '').toLowerCase();
    if (nameKey.length >= 4) {
      const idx = ans.toLowerCase().indexOf(nameKey);
      if (idx > 0 && idx < 250) ans = ans.slice(idx);
    }
    // First-person preamble strip: if the answer starts with "I " or any
    // first-person opener, drop the leading sentence.
    ans = ans.replace(/^\s*I\s+(?:find|believe|note|see|will|shall|am|have|do)[^.!?]*[.!?]\s*/, '');
    // Other meta-openers
    ans = ans.replace(/^\s*(?:Certainly[^.!?]*[.!?]|Here(?:'s| is)[^.!?]*[.!?:]|Below is[^.!?]*[.!?:]|Okay[^.!?]*[.!?]|Sure[^.!?]*[.!?])\s*/i, '');
    // S196 — bare-token preambles (e.g. "userRouter:", "PRODUCTS OF THE PAST:")
    // — strip a leading short identifier line that ends with a colon.
    ans = ans.replace(/^\s*[A-Za-z][\w\s]{0,40}:\s*\n+/, '');
    // ALL-CAPS section headers like "PRODUCTS OF THE PAST:" with surrounding blanks
    ans = ans.replace(/^\s*[A-Z][A-Z\s]{2,40}:\s*\n+/, '');
    ans = ans.trim();
    const v = validate(ans, result.length_bucket, effYear, poi.name);
    if (v.ok) {
      result.status = 'ok';
      result.narration = v.cleanText || ans;
      const stripNote = v.stripped ? `, stripped=${v.stripped}sentences (${(v.droppedReasons || []).slice(0,2).join('|')})` : '';
      result.note = `words=${v.words}, witch_trial=${witchTrial}, prompt_sha=${resp.prompt_sha?.slice(0,8) || '?'}${stripNote}`;
      return result;
    }
    if (VERBOSE) console.error(`  [attempt ${attempt}] reject: ${v.reason} | first 80: ${ans.slice(0,80)}`);
    if (attempt === MAX_ATTEMPTS) {
      result.status = 'reject';
      result.note = v.reason;
      result.rejected_text = ans;
      return result;
    }
  }
  return result;
}

// ── main ────────────────────────────────────────────────────────────────────

async function main() {
  console.error(`generate-historical-narrations: dry_run=${DRY_RUN} force=${FORCE} limit=${LIMIT ?? 'none'} ids=${ONLY_IDS ? ONLY_IDS.length : 'all'}`);

  // S196 — eligibility: HIST_BLDG (the legacy set) OR commemorative-named
  // POIs in any category. Commemoratives are routed to the tribute prompt
  // path inside generateForPoi(), narrating the pre-1860 subject rather
  // than the post-1860 artifact.
  const COMMEMORATIVE_SQL = `name ~* '\\m(statue|monument|plaque|marker|memorial|cenotaph|obelisk|tablet|bust|arch)\\M'`;
  let where = `(category = 'HISTORICAL_BUILDINGS' OR ${COMMEMORATIVE_SQL}) AND deleted_at IS NULL AND intel_entity_id IS NOT NULL`;
  if (!FORCE) where += ` AND (historical_narration IS NULL OR historical_narration = '')`;
  if (ONLY_IDS) where += ` AND id = ANY($1)`;

  let sql = `SELECT id, name, lat, lng, year_established, intel_entity_id, data_source FROM salem_pois WHERE ${where} ORDER BY year_established NULLS LAST, name`;
  if (LIMIT) sql += ` LIMIT ${LIMIT}`;
  const params = ONLY_IDS ? [ONLY_IDS] : [];

  const { rows } = await pool.query(sql, params);
  console.error(`Eligible POIs: ${rows.length}`);

  let stats = { ok: 0, reject: 0, skip_not_pre1860: 0, skip_no_dump: 0, fail_chat: 0 };
  const samples = [];

  for (let i = 0; i < rows.length; i++) {
    const poi = rows[i];
    process.stderr.write(`\n[${i + 1}/${rows.length}] ${poi.id} (${poi.name}) ... `);
    const result = await generateForPoi(poi);
    process.stderr.write(`${result.status}`);
    if (result.note) process.stderr.write(` (${result.note})`);

    const key = result.status.replace(/-/g, '_');
    stats[key] = (stats[key] || 0) + 1;

    if (result.status === 'ok' && !DRY_RUN) {
      await pool.query(
        `UPDATE salem_pois SET historical_narration = $1, admin_dirty = true, admin_dirty_at = NOW(), updated_at = NOW() WHERE id = $2`,
        [result.narration, poi.id]
      );
    }
    if (DRY_RUN) samples.push(result);
  }

  console.error('\n\n=== STATS ===');
  console.error(JSON.stringify(stats, null, 2));

  if (DRY_RUN || samples.length) {
    console.error('\n=== SAMPLES ===');
    for (const s of samples) {
      console.error('\n---', s.poi_id, '|', s.name, '| year=', s.effective_year, '| bucket=', s.length_bucket, '| status=', s.status, '---');
      if (s.narration) console.error(s.narration);
      else if (s.rejected_text && SHOW_REJECTED) {
        console.error('[REJECTED — reason:', s.note, ']');
        console.error(s.rejected_text);
      }
      else if (s.note) console.error('[no narration]', s.note);
    }
  }

  await pool.end();
}

main().catch((e) => { console.error('FATAL:', e); process.exit(1); });
