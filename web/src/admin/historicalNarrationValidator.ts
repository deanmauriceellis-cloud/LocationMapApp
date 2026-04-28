// TS mirror of cache-proxy/lib/historical-narration-validator.js. Used by
// the Salem Oracle variant picker to filter historic candidates that hit
// critical patterns before showing them to the operator.
//
// Keep this file in sync with the JS version. If you change a pattern in
// one, update the other.

export type ValidatorSeverity = 'critical' | 'soft'

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
]

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
]

const TAG_ECHO_PATTERNS = [
  /\bfor its contributions to (?:architecture|health|medicine|engineering|social history)/i,
  /\bcontributions to architecture[, ].*?(?:health|medicine|social history)/i,
]

const STREET_ADDR_RE =
  /\b\d+(?:[-–]\d+)?\s+[A-Z][\w'.-]*(?:\s+[A-Z][\w'.-]*)*\s+(?:Street|St\.?|Avenue|Ave\.?|Road|Rd\.?|Lane|Ln\.?|Place|Pl\.?|Court|Ct\.?|Square|Sq\.?|Wharf|Way)\b/g

const YEAR_RE = /\b(1[89]\d{2}|20\d{2})\b/g

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
]

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
]

function checkPatterns(text: string, patterns: RegExp[]): string[] {
  const hits: string[] = []
  for (const re of patterns) {
    const m = text.match(re)
    if (m) hits.push(m[0])
  }
  return hits
}

function checkAddressList(text: string): string[] | null {
  const matches = text.match(STREET_ADDR_RE) || []
  return matches.length >= 3 ? [...matches] : null
}

function checkSubjectPresent(text: string, name: string): boolean {
  const tokens = name
    .split(/[\s\-(),@.]+/)
    .filter(
      (t) =>
        t.length > 3 &&
        !/^(House|Hall|Building|Street|the|and|at)$/i.test(t),
    )
  if (tokens.length === 0) return true
  return tokens.some((tok) =>
    new RegExp(
      `\\b${tok.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}\\b`,
      'i',
    ).test(text),
  )
}

export interface NarrationValidationResult {
  critical: string[]
  soft: string[]
  wordCount: number
}

/**
 * Score a historical_narration candidate for quality issues.
 *
 * - `critical` reasons mean the variant should be hidden / disabled.
 * - `soft` reasons mean the variant is mediocre but salvageable; surface
 *   them as warnings without blocking selection.
 */
export function validateHistoricalNarration(
  text: string,
  name: string,
): NarrationValidationResult {
  const critical: string[] = []
  const soft: string[] = []

  if (!text || text.trim().length === 0) {
    return { critical: ['empty'], soft: [], wordCount: 0 }
  }

  const wordCount = text.trim().split(/\s+/).length

  if (!/^[A-Z"(\[]/.test(text.trim())) {
    critical.push(`leading-garbage: "${text.trim().slice(0, 30)}…"`)
  }

  if (!checkSubjectPresent(text, name)) {
    critical.push('subject-absent')
  }

  for (const m of checkPatterns(text, SELF_REF_PATTERNS)) {
    critical.push(`self-ref: "${m}"`)
  }
  for (const m of checkPatterns(text, MODERN_LEAK_PATTERNS)) {
    critical.push(`modern-leak: "${m}"`)
  }
  for (const m of checkPatterns(text, TAG_ECHO_PATTERNS)) {
    critical.push(`tag-echo: "${m}"`)
  }

  const years = (text.match(YEAR_RE) || []).map((s) => Number(s))
  const violatingYear = years.find((y) => y >= 1860 && y <= 2100)
  if (violatingYear) critical.push(`year-violation: ${violatingYear}`)

  const addrs = checkAddressList(text)
  if (addrs) {
    critical.push(
      `address-list (${addrs.length}): ${addrs.slice(0, 3).join(' / ')}`,
    )
  }

  for (const m of checkPatterns(text, HEDGING_PATTERNS)) {
    soft.push(`hedging: "${m}"`)
  }
  for (const m of checkPatterns(text, FILLER_PATTERNS)) {
    soft.push(`filler: "${m}"`)
  }

  return { critical, soft, wordCount }
}
