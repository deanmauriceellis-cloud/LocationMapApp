// oracleClient — Phase 9P.B Step 9P.10b, re-targeted S125 (2026-04-14)
//
// The admin tool's editorial-AI bridge. Historically called Salem Oracle on
// `:8088`; now retargeted to SalemIntelligence on `:8089` per operator
// direction (S125): "SalemIntelligence has all the KBs from Salem Oracle
// so it should be used instead". Oracle and SI cannot both run on the
// workstation at once (shared GPU), so SI is the default.
//
// This file keeps the Oracle* type names + public function names unchanged
// so every consumer (AdminLayout, PoiEditDialog) continues to compile
// without edits. Internally it maps SI's endpoints to the Oracle shapes
// the admin tool already understands.
//
// Endpoint mapping:
//   getStatus()  → GET  /api/intel/status          (SI)   vs  /api/oracle/status
//   ask(req)     → POST /api/intel/chat            (SI)   vs  /api/oracle/ask
//   listPois()   → GET  /api/intel/poi-export      (SI)   vs  /api/oracle/pois
//
// ─── Known feature gaps vs Oracle ─────────────────────────────────────────
// The following Oracle features have no SI equivalent today. They degrade
// silently — the adapter drops unsupported params or returns empty/sensible
// defaults. Full list (surfaced as an OMEN cross-project note, S125):
//
//   1. Conversation history. Oracle kept a 6-turn rolling history shared
//      across all callers for deictic prompts ("make that shorter").
//      SI /chat is stateless.
//
//   2. `current_poi_id` context pinning. Oracle would bias retrieval
//      against the LMA POI currently being edited so "rewrite this"
//      resolved against the right subject. SI exposes per-entity context
//      via /api/intel/entity/{id}/context but not as a chat companion.
//
//   3. `current_newspaper_date` pinning. Oracle used this to anchor
//      answers inside the 1692 game timeline. SI /chat has no timeline.
//
//   4. `reset` flag. Moot since SI is stateless, but preserved in the
//      adapter interface so callers don't need to change.
//
//   5. Rich `primary_sources` (verbatim + attribution + modern_gloss +
//      doc_type + relevance + score). SI returns a simpler
//      `ChatCitationResponse` that this adapter flattens into
//      OraclePrimarySource with best-effort mapping.
//
//   6. Separate 1692 POI catalog (/pois returned 63 Salem-Village 1692
//      POIs). SI's /poi-export returns 1,597 modern Salem entities —
//      different corpus. The adapter still maps but the output is not
//      interchangeable with the old Oracle catalog.
//
// ─── Base URL ────────────────────────────────────────────────────────────
// Default: `http://localhost:8089`. Override via `VITE_SALEM_INTELLIGENCE_URL`
// (preferred) or the legacy `VITE_SALEM_ORACLE_URL` env var in `.env.local`.
//
// ─── Latency / timeout ────────────────────────────────────────────────────
// SI /chat calls gemma3:27b on the workstation's RTX 3090. Typical 5-15s;
// keep the 120s default to leave headroom for cold-cache turns. Status
// and catalog calls are sub-millisecond; use 5s.

// ─── Base URL resolution ──────────────────────────────────────────────────

const DEFAULT_INTEL_BASE = 'http://localhost:8089'

function resolveBase(): string {
  const fromIntel = (import.meta.env?.VITE_SALEM_INTELLIGENCE_URL as string | undefined)?.trim()
  if (fromIntel) return fromIntel.replace(/\/$/, '')
  // Legacy compat — operator may still have the Oracle env var set.
  const fromOracle = (import.meta.env?.VITE_SALEM_ORACLE_URL as string | undefined)?.trim()
  if (fromOracle) return fromOracle.replace(/\/$/, '')
  return DEFAULT_INTEL_BASE
}

/** Kept as `ORACLE_BASE` so existing imports in AdminLayout don't change. */
export const ORACLE_BASE = resolveBase()

// ─── Response shapes (Oracle-compatible public surface) ───────────────────

/** Successful /status response. */
export interface OracleStatusOk {
  available: true
  fact_count: number
  primary_source_count: number
  history_len: number
  game_date: string
  newspaper_count: number
  first_newspaper: string
  last_newspaper: string
  poi_count: number
}

/** Degraded /status response — service up but not ready. */
export interface OracleStatusDown {
  available: false
  reason: string
  game_date?: string
  newspaper_count?: number
  poi_count?: number
}

export type OracleStatus = OracleStatusOk | OracleStatusDown

/** One primary source citation in an ask response. */
export interface OraclePrimarySource {
  verbatim_text: string
  attribution: string
  modern_gloss?: string
  doc_type?: string
  relevance?: string
  score?: number
}

/** Successful /ask response. */
export interface OracleAskOk {
  question: string
  text: string
  game_date: string
  primary_sources: OraclePrimarySource[]
  fact_count: number
  primary_source_count: number
  route: string
  history_turn_count: number
  used_external_context: boolean
}

/** Error envelope (HTTP 200 with `error` key). */
export interface OracleAskErr {
  error: string
}

export type OracleAskResponse = OracleAskOk | OracleAskErr

/** Request body for /ask. */
export interface OracleAskRequest {
  question: string
  current_poi_id?: string
  current_newspaper_date?: string
  reset?: boolean
}

/** Lightweight POI entry in the catalog list. */
export interface OraclePoiSummary {
  id: string
  name: string
  kind: 'building' | 'landmark'
  type?: string
  subtype?: string
  zone?: string
  summary?: string
}

export interface OraclePoiList {
  count: number
  entries: OraclePoiSummary[]
}

// ─── Internal SI response shapes ──────────────────────────────────────────

interface IntelStatusResponse {
  available: boolean
  version?: string
  phase?: string
  llm_model?: string
  entity_count?: number
  relation_count?: number
  source_count?: number
  entities_by_type?: Record<string, number>
}

interface IntelChatCitation {
  entity_id?: string
  entity_name?: string
  snippet?: string
  source?: string
  attribution?: string
  verbatim_text?: string
  modern_gloss?: string
  doc_type?: string
  relevance?: string
  score?: number
}

interface IntelChatResponse {
  answer: string
  citations: IntelChatCitation[]
  model?: string
  prompt_sha?: string
}

interface IntelPoiExportItem {
  entity_id: string
  display_name: string
  primary_category?: string
  secondary_categories?: string[]
  district?: string
  short_description?: string
  long_description?: string
  origin_story?: string
}

interface IntelPoiExportResponse {
  count: number
  pois: IntelPoiExportItem[]
}

// ─── Errors ───────────────────────────────────────────────────────────────

/**
 * Thrown when the network call itself fails (service down, timeout, CORS,
 * DNS, etc). Kept as `OracleNetworkError` for call-site compatibility.
 */
export class OracleNetworkError extends Error {
  constructor(message: string, public cause?: unknown) {
    super(message)
    this.name = 'OracleNetworkError'
  }
}

// ─── Internal fetch helper ────────────────────────────────────────────────

async function fetchWithTimeout(
  url: string,
  init: RequestInit,
  timeoutMs: number,
): Promise<Response> {
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), timeoutMs)
  try {
    return await fetch(url, { ...init, signal: ctrl.signal })
  } catch (e) {
    if ((e as { name?: string })?.name === 'AbortError') {
      throw new OracleNetworkError(`SalemIntelligence request timed out after ${timeoutMs}ms`, e)
    }
    throw new OracleNetworkError(
      `SalemIntelligence request failed: ${e instanceof Error ? e.message : String(e)}`,
      e,
    )
  } finally {
    clearTimeout(timer)
  }
}

// ─── Public API ───────────────────────────────────────────────────────────

/**
 * Health check. Sub-millisecond; 5s timeout so the pill flips to
 * "unavailable" fast when SI isn't running.
 */
export async function getStatus(): Promise<OracleStatus> {
  const res = await fetchWithTimeout(
    `${ORACLE_BASE}/api/intel/status`,
    { method: 'GET' },
    5_000,
  )
  if (!res.ok) {
    throw new OracleNetworkError(`SalemIntelligence /status returned ${res.status}`)
  }
  const raw = (await res.json()) as IntelStatusResponse
  if (!raw.available) {
    return {
      available: false,
      reason: `SalemIntelligence not ready (${raw.phase || 'phase unknown'})`,
    }
  }
  const entitiesByType = raw.entities_by_type ?? {}
  const poiCount =
    (entitiesByType.attraction ?? 0) +
    (entitiesByType.business ?? 0) +
    (entitiesByType.historic_building ?? 0)
  return {
    available: true,
    // Oracle-shape fields below are filled with SI equivalents where they
    // exist, or 0 / "unknown" where they don't. Consumers only read
    // `available`, `poi_count`, and (sometimes) `fact_count` in the status
    // pill today; everything else is surface compatibility.
    fact_count: raw.source_count ?? 0,
    primary_source_count: raw.source_count ?? 0,
    history_len: 0,
    game_date: 'modern',
    newspaper_count: 0,
    first_newspaper: '',
    last_newspaper: '',
    poi_count: poiCount,
  }
}

/**
 * Main composition endpoint. Maps Oracle's `question` → SI's `query`. SI
 * is stateless, so `current_poi_id`, `current_newspaper_date`, and `reset`
 * are dropped (see "Known feature gaps" at the top of this file).
 *
 * Returns the Oracle-shape `OracleAskOk` so PoiEditDialog's rendering code
 * stays unchanged. `citations` (SI) are reshaped into `primary_sources`.
 */
export async function ask(
  req: OracleAskRequest,
  timeoutMs = 120_000,
): Promise<OracleAskResponse> {
  if (req.current_poi_id || req.current_newspaper_date || req.reset) {
    console.warn(
      '[oracleClient] SalemIntelligence /chat is stateless — ignoring',
      'current_poi_id/current_newspaper_date/reset. Surface as OMEN note',
      'S125 if the admin tool needs these restored.',
    )
  }
  const res = await fetchWithTimeout(
    `${ORACLE_BASE}/api/intel/chat`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: req.question, top_k: 10 }),
    },
    timeoutMs,
  )
  if (!res.ok) {
    if (res.status === 503) {
      return { error: 'SalemIntelligence LLM unavailable (HTTP 503)' }
    }
    throw new OracleNetworkError(`SalemIntelligence /chat returned ${res.status}`)
  }
  const raw = (await res.json()) as IntelChatResponse
  const primary_sources: OraclePrimarySource[] = (raw.citations ?? []).map((c) => ({
    verbatim_text: c.verbatim_text ?? c.snippet ?? '',
    attribution: c.attribution ?? c.source ?? c.entity_name ?? '',
    modern_gloss: c.modern_gloss,
    doc_type: c.doc_type,
    relevance: c.relevance,
    score: c.score,
  }))
  return {
    question: req.question,
    text: raw.answer,
    game_date: 'modern',
    primary_sources,
    fact_count: 0,
    primary_source_count: primary_sources.length,
    route: raw.model ?? 'intel-chat',
    history_turn_count: 0,
    used_external_context: false,
  }
}

/**
 * Catalog list. SI's /poi-export carries 1,597 modern Salem entities with
 * `entity_id` + `display_name` + `primary_category` + optional
 * `secondary_categories`. Mapped to the Oracle shape the admin tool
 * already renders.
 */
export async function listPois(): Promise<OraclePoiList> {
  const res = await fetchWithTimeout(
    `${ORACLE_BASE}/api/intel/poi-export`,
    { method: 'GET' },
    5_000,
  )
  if (!res.ok) {
    throw new OracleNetworkError(`SalemIntelligence /poi-export returned ${res.status}`)
  }
  const raw = (await res.json()) as IntelPoiExportResponse
  const entries: OraclePoiSummary[] = (raw.pois ?? []).map((p) => ({
    id: p.entity_id,
    name: p.display_name,
    // SI categories don't cleanly divide into Oracle's 'building' | 'landmark'.
    // Map historic-family → 'building', everything else → 'landmark'.
    kind:
      p.primary_category === 'museum' ||
      p.primary_category === 'historic' ||
      (p.secondary_categories ?? []).includes('historic')
        ? 'building'
        : 'landmark',
    type: p.primary_category,
    subtype: (p.secondary_categories ?? [])[0],
    zone: p.district,
    summary: p.short_description,
  }))
  return {
    count: raw.count ?? entries.length,
    entries,
  }
}

/**
 * Type guard: did `ask` return a successful answer or an error envelope?
 */
export function isAskOk(r: OracleAskResponse): r is OracleAskOk {
  return (r as OracleAskErr).error === undefined
}
