// Shared validator for salem_pois.historical_narration text quality.
// Used by:
//   - cache-proxy/lib/admin-lint.js (lint check: needs_refinement)
//   - cache-proxy/scripts/score-historical-narrations.js (QC report)
//   - cache-proxy/scripts/generate-historical-narrations.js (pre-write gate)
//   - Salem Oracle picker (variant filtering — future)
//
// Two severity buckets:
//   - CRITICAL_PATTERNS: clear breaks. POI gets flagged for refinement.
//   - SOFT_PATTERNS: rambling/formulaic. Not surfaced as a lint flag yet
//     (operator wants those treated as draft-quality, not broken).

// --- CRITICAL ---

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
  /\bcarried (?:in|within) (?:the\s+)?records I\b/i,
  /\bnot fully carried\b/i,
  /\brecords I possess\b/i,
  /\bistellectual\b/i,
];

const MODERN_LEAK_PATTERNS = [
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

const TAG_ECHO_PATTERNS = [
  /\bfor its contributions to (?:architecture|health|medicine|engineering|social history)/i,
  /\bcontributions to architecture[, ].*?(?:health|medicine|social history)/i,
];

// 3+ street addresses anywhere in the narration (district-graph leak).
const STREET_ADDR_RE =
  /\b\d+(?:[-–]\d+)?\s+[A-Z][\w'.-]*(?:\s+[A-Z][\w'.-]*)*\s+(?:Street|St\.?|Avenue|Ave\.?|Road|Rd\.?|Lane|Ln\.?|Place|Pl\.?|Court|Ct\.?|Square|Sq\.?|Wharf|Way)\b/g;

const YEAR_RE = /\b(1[89]\d{2}|20\d{2})\b/g;

// --- SOFT (filler, hedging) ---

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

function checkPatterns(text, patterns) {
  const hits = [];
  for (const re of patterns) {
    const m = text.match(re);
    if (m) hits.push(m[0]);
  }
  return hits;
}

function checkAddressList(text) {
  const matches = text.match(STREET_ADDR_RE) || [];
  return matches.length >= 3 ? matches : null;
}

function checkSubjectPresent(text, name) {
  const tokens = name
    .split(/[\s\-(),@.]+/)
    .filter(
      (t) =>
        t.length > 3 && !/^(House|Hall|Building|Street|the|and|at)$/i.test(t)
    );
  if (tokens.length === 0) return true;
  return tokens.some((tok) =>
    new RegExp(`\\b${tok.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`, 'i').test(
      text
    )
  );
}

/**
 * Score a single historical_narration string.
 *
 * @param {string} text - the narration
 * @param {string} name - the POI name (used for subject-presence check)
 * @returns {{ critical: string[], soft: string[], wordCount: number }}
 *   critical: reason codes this row should be flagged for refinement
 *   soft: rambling/formulaic hits (informational, not a flag)
 *   wordCount: total word count
 */
function validateNarration(text, name) {
  const critical = [];
  const soft = [];

  if (!text || text.trim().length === 0) {
    return { critical: ['empty'], soft: [], wordCount: 0 };
  }

  const wordCount = text.trim().split(/\s+/).length;

  // Critical: leading-garbage / non-uppercase start
  if (!/^[A-Z"(\[]/.test(text.trim())) {
    critical.push(`leading-garbage: "${text.trim().slice(0, 30)}…"`);
  }

  // Critical: subject absent
  if (!checkSubjectPresent(text, name)) {
    critical.push(`subject-absent: POI name not found`);
  }

  // Critical: self-reference
  for (const m of checkPatterns(text, SELF_REF_PATTERNS)) {
    critical.push(`self-ref: "${m}"`);
  }
  // Critical: modern leak
  for (const m of checkPatterns(text, MODERN_LEAK_PATTERNS)) {
    critical.push(`modern-leak: "${m}"`);
  }
  // Critical: tag echo
  for (const m of checkPatterns(text, TAG_ECHO_PATTERNS)) {
    critical.push(`tag-echo: "${m}"`);
  }
  // Critical: year >= 1860
  const years = (text.match(YEAR_RE) || []).map(Number);
  const violatingYear = years.find((y) => y >= 1860 && y <= 2100);
  if (violatingYear) critical.push(`year-violation: ${violatingYear}`);
  // Critical: address-list (district-graph leak)
  const addrs = checkAddressList(text);
  if (addrs) {
    critical.push(
      `address-list (${addrs.length}): ${addrs.slice(0, 3).join(' / ')}`
    );
  }

  // Soft: hedging
  for (const m of checkPatterns(text, HEDGING_PATTERNS)) {
    soft.push(`hedging: "${m}"`);
  }
  // Soft: filler
  for (const m of checkPatterns(text, FILLER_PATTERNS)) {
    soft.push(`filler: "${m}"`);
  }

  return { critical, soft, wordCount };
}

module.exports = {
  validateNarration,
  CRITICAL: {
    SELF_REF_PATTERNS,
    MODERN_LEAK_PATTERNS,
    TAG_ECHO_PATTERNS,
    STREET_ADDR_RE,
    YEAR_RE,
  },
  SOFT: {
    HEDGING_PATTERNS,
    FILLER_PATTERNS,
  },
};
