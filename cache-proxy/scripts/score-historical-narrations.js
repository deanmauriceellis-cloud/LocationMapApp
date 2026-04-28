#!/usr/bin/env node
// Score every existing salem_pois.historical_narration against tightened
// quality rules. NO WRITES. Produces a markdown report with pass/reject
// status, reason codes, and the narration text for operator review.
//
// New rules (S194):
// - Forbid hedging / "Records indicate" / "It is noted" / "thought to have"
//   etc. Narration must speak authoritatively.
// - Forbid architecture-as-virtue filler ("stands as a testament", "speaks to",
//   "fabric of the town", "enduring spirit", "those who first raised its walls",
//   "noted as simply").
// - Forbid SI tag/category echo ("for its contributions to architecture, health,
//   medicine, and social history").
// - Forbid model self-reference ("my memory", "I am unable", "I cannot",
//   "the prompt").
// - Forbid address-list district-graph leak: 3+ street addresses in close
//   proximity.
// - Plus all original validator rules (still applied for completeness).

const { Pool } = require('pg');
const fs = require('fs');
const path = require('path');

// Load DATABASE_URL from cache-proxy/.env if not already set
if (!process.env.DATABASE_URL) {
  const envFile = path.join(__dirname, '..', '.env');
  if (fs.existsSync(envFile)) {
    for (const line of fs.readFileSync(envFile, 'utf8').split(/\r?\n/)) {
      const m = line.match(/^([A-Z_][A-Z0-9_]*)=(.*)$/);
      if (m && !process.env[m[1]]) {
        process.env[m[1]] = m[2].replace(/^["']|["']$/g, '');
      }
    }
  }
}

const pool = new Pool({ connectionString: process.env.DATABASE_URL });

// --- HEDGING / UNCERTAINTY (operator S194 rule) ---
const HEDGING_PATTERNS = [
  /\brecords?\s+indicate\b/i,
  /\bit is noted\b/i,
  /\bnoted as\b/i,
  /\bnoted to be\b/i,
  /\bit is recorded\b/i,
  /\bthough\s+(?:the\s+)?precise\b/i,
  /\bthought to have\b/i,
  /\bbelieved to have\b/i,
  /\bis said to have\b/i,
  /\bdetails remain\b/i,
  /\bparticulars are\b/i,
  /\bappears? to have\b/i,
  /\bmay have been\b/i,
  /\bis understood to be\b/i,
  /\bunderstood to be\b/i,
  /\bwithin my memory\b/i,
  /\bcarried within\b/i,
  /\blikely\b/i,
  /\bperhaps\b/i,
  /\bpossibly\b/i,
  /\bsuggesting\b/i,
];

// --- FILLER / FORMULA (architecture-as-virtue) ---
const FILLER_PATTERNS = [
  /\bstands? as a testament\b/i,
  /\bspeaks? to the\s+\w+/i,
  /\bof some note\b/i,
  /\bof some age\b/i,
  /\bfabric of the town\b/i,
  /\benduring spirit\b/i,
  /\barchitectural sensibilities\b/i,
  /\bbuilding traditions of an earlier age\b/i,
  /\bthose who first raised its walls\b/i,
  /\bebb and flow of family life\b/i,
  /\bsilent observer\b/i,
  /\bnoted as simply\b/i,
  /\barchitectural trends of its time\b/i,
  /\barchitecture of its time\b/i,
  /\bplace of respite\b/i,
  /\btestament to (?:the\s+)?(?:architecture|building|design|growth)/i,
  // Newly-added formulaic patterns surfacing across the corpus:
  /\barchitecture (?:reflects|defines)\b/i,
  /\breflects the building traditions\b/i,
  /\bbuilding traditions of (?:the period|the era|that period|that era|the time|colonial)\b/i,
  /\bin proximity to other (?:buildings|structures|dwellings|houses)\b/i,
  /\bshadow of those proceedings\b/i,
  /\bevolving landscape\b/i,
  /\bdwelling known as\b/i,
  /\bdwelling of some age\b/i,
  /\bgrowth of the settlement\b/i,
  /\bmarking a point in\b/i,
  /\bstructure of (?:some|note)\b/i,
  /\bthe house, like many\b/i,
  /\barchitectural style of the period\b/i,
  /\barchitecture of (?:its|the) (?:period|era|time)\b/i,
  /\b(?:reflects|reflecting) the (?:prosperity|order|spirit|values|principles)\b/i,
  /\bcraft(?:ed|smanship) of (?:its|the) time\b/i,
  /\benduring (?:legacy|presence)\b/i,
  /\bquiet (?:dignity|grace|presence)\b/i,
  /\bsense of (?:place|history|continuity)\b/i,
];

// --- SI TAG / CATEGORY ECHO ---
const TAG_ECHO_PATTERNS = [
  /\bfor its contributions to (?:architecture|health|medicine|engineering|social history)/i,
  /\bcontributions to architecture[, ].*?(?:health|medicine|social history)/i,
];

// --- MODEL SELF-REFERENCE ---
const SELF_REF_PATTERNS = [
  /\bI cannot\b/i,
  /\bI am unable\b/i,
  /\bI am programmed\b/i,
  /\bI apologize\b/i,
  /\bas an AI\b/i,
  /\blanguage model\b/i,
  /\bthe prompt\b/i,
  /\bthe request\b/i,
  /\bmy memory\b/i,
  /\bistellectual\b/i,
];

// --- ORIGINAL VALIDATOR RULES (year, period bans) ---
const YEAR_RE = /\b(1[89]\d{2}|20\d{2})\b/g;
const ORIGINAL_BANNED = [
  /\bnational register\b/i,
  /\bnational historic landmark\b/i,
  /\bhistoric district\b/i,
  /\btoday[, ]/i,
  /\bcurrently\b/i,
  /\bpresently\b/i,
  /\bvisitors? (?:may|can)\b/i,
  /\badmission\b/i,
  /\boperated as a museum\b/i,
  /\bnow (?:a|the|operates|stands|serves|functions|houses)\b/i,
  /\bin the\s+\d+(?:st|nd|rd|th)\s+century\b/i,
  /\bnineteenth century\b/i,
  /\btwentieth century\b/i,
  /\btwenty-first century\b/i,
];

// --- ADDRESS-LIST DISTRICT-GRAPH LEAK ---
// 3+ street addresses anywhere in narration (the form that bypassed
// S192's "X meters from" regex).
const STREET_ADDR_RE =
  /\b\d+(?:[-–]\d+)?\s+[A-Z][\w'.-]*(?:\s+[A-Z][\w'.-]*)*\s+(?:Street|St\.?|Avenue|Ave\.?|Road|Rd\.?|Lane|Ln\.?|Place|Pl\.?|Court|Ct\.?|Square|Sq\.?|Wharf|Way)\b/g;

function checkAddressList(text) {
  const matches = text.match(STREET_ADDR_RE) || [];
  return matches.length >= 3 ? matches : null;
}

function scoreOne(name, text) {
  const reasons = [];
  if (!text || text.trim().length === 0) {
    return { status: 'empty', reasons: ['no text'] };
  }

  // Leading-garbage check (anchor failure / non-uppercase start)
  if (!/^[A-Z"(\[]/.test(text.trim())) {
    reasons.push(`leading-garbage: "${text.trim().slice(0, 30)}…"`);
  }

  // POI-name presence (anchor verification — answer must reference subject)
  // Accept any token of the name (>3 chars) appearing in narration.
  const nameTokens = name
    .split(/[\s\-(),@.]+/)
    .filter((t) => t.length > 3 && !/^(House|Hall|Building|Street|the|and|at)$/i.test(t));
  const nameAppears = nameTokens.some((tok) =>
    new RegExp(`\\b${tok.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'i').test(text)
  );
  if (!nameAppears && nameTokens.length > 0) {
    reasons.push(`subject-absent: name tokens [${nameTokens.join(', ')}] not in narration`);
  }

  // Hedging
  for (const re of HEDGING_PATTERNS) {
    const m = text.match(re);
    if (m) reasons.push(`hedging: "${m[0]}"`);
  }
  // Filler
  for (const re of FILLER_PATTERNS) {
    const m = text.match(re);
    if (m) reasons.push(`filler: "${m[0]}"`);
  }
  // Tag echo
  for (const re of TAG_ECHO_PATTERNS) {
    const m = text.match(re);
    if (m) reasons.push(`tag-echo: "${m[0]}"`);
  }
  // Self reference
  for (const re of SELF_REF_PATTERNS) {
    const m = text.match(re);
    if (m) reasons.push(`self-ref: "${m[0]}"`);
  }
  // Original banned
  for (const re of ORIGINAL_BANNED) {
    const m = text.match(re);
    if (m) reasons.push(`modern-leak: "${m[0]}"`);
  }
  // Year >= 1860
  const years = (text.match(YEAR_RE) || []).map(Number);
  const violatingYear = years.find((y) => y >= 1860 && y <= 2100);
  if (violatingYear) reasons.push(`year-violation: ${violatingYear}`);

  // Address list
  const addrs = checkAddressList(text);
  if (addrs) reasons.push(`address-list (${addrs.length}): ${addrs.slice(0, 3).join(' / ')}${addrs.length > 3 ? '…' : ''}`);

  // Word count floor
  const wordCount = text.trim().split(/\s+/).length;
  if (wordCount < 60) reasons.push(`under-floor: ${wordCount} words < 60`);

  return {
    status: reasons.length === 0 ? 'pass' : 'reject',
    reasons,
    wordCount,
  };
}

async function main() {
  const { rows } = await pool.query(`
    SELECT id, name, year_established, intel_entity_id, default_visible, is_tour_poi,
           length(historical_narration) AS chars,
           historical_narration
    FROM salem_pois
    WHERE deleted_at IS NULL
      AND historical_narration IS NOT NULL
      AND length(trim(historical_narration)) > 0
    ORDER BY name
  `);

  console.error(`Scoring ${rows.length} narrations...`);

  let pass = 0;
  let reject = 0;
  const reasonCounts = new Map();
  const out = [];
  out.push(`# Historical Narration QC — S194 scoring pass`);
  out.push(``);
  out.push(`Scored at: ${new Date().toISOString()}`);
  out.push(`Total scored: ${rows.length}`);
  out.push(``);
  out.push(`---`);
  out.push(``);

  const passed = [];
  const rejected = [];

  for (const row of rows) {
    const r = scoreOne(row.name, row.historical_narration);
    if (r.status === 'pass') {
      pass++;
      passed.push({ row, r });
    } else {
      reject++;
      rejected.push({ row, r });
      for (const reason of r.reasons) {
        const key = reason.split(':')[0];
        reasonCounts.set(key, (reasonCounts.get(key) || 0) + 1);
      }
    }
  }

  // Summary
  out.push(`## Summary`);
  out.push(``);
  out.push(`- **Pass: ${pass} / ${rows.length}** (${((pass / rows.length) * 100).toFixed(1)}%)`);
  out.push(`- **Reject: ${reject} / ${rows.length}** (${((reject / rows.length) * 100).toFixed(1)}%)`);
  out.push(``);
  out.push(`### Reject reasons (count of rules fired across rejected rows)`);
  out.push(``);
  for (const [k, v] of [...reasonCounts.entries()].sort((a, b) => b[1] - a[1])) {
    out.push(`- \`${k}\` — ${v}`);
  }
  out.push(``);
  out.push(`---`);
  out.push(``);

  // Passed (the ones we'd keep)
  out.push(`## ✅ Passed (${passed.length})`);
  out.push(``);
  out.push(`These survive the S194 rule set and would be kept on a re-bake.`);
  out.push(``);
  for (const { row, r } of passed) {
    out.push(`### ${row.name} — \`${row.id}\``);
    out.push(`- year_established: ${row.year_established ?? 'null'} · intel_entity_id: ${row.intel_entity_id ?? 'null'} · visible: ${row.default_visible} · tour: ${row.is_tour_poi} · words: ${r.wordCount}`);
    out.push(``);
    out.push(`> ${row.historical_narration.replace(/\n+/g, '\n> ')}`);
    out.push(``);
  }
  out.push(`---`);
  out.push(``);

  // Rejected
  out.push(`## ❌ Rejected (${rejected.length})`);
  out.push(``);
  out.push(`Each row shows reason codes followed by the narration text.`);
  out.push(``);
  for (const { row, r } of rejected) {
    out.push(`### ${row.name} — \`${row.id}\``);
    out.push(`- year_established: ${row.year_established ?? 'null'} · words: ${r.wordCount}`);
    out.push(`- **reasons:** ${r.reasons.map((x) => `\`${x}\``).join(' · ')}`);
    out.push(``);
    out.push(`> ${row.historical_narration.replace(/\n+/g, '\n> ')}`);
    out.push(``);
  }

  const outPath = path.join(
    __dirname,
    '..',
    '..',
    'docs',
    'session-logs',
    `session-194-narration-quality-scoring.md`
  );
  fs.writeFileSync(outPath, out.join('\n'));
  console.error(`Wrote ${outPath}`);
  console.error(`Pass: ${pass}  Reject: ${reject}`);

  await pool.end();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
