// oracleClient — Phase 9P.B Step 9P.10b
//
// Typed client for the Salem Oracle API. The Oracle is a dev-side LLM-backed
// service exposed by Salem's `cmd/testapp` on the operator's workstation. It
// composes, revises, summarizes, and fact-checks editorial content for the
// LocationMapApp admin tool against Salem's full historical corpus
// (3,891 facts, 4,950 primary source chunks, 63 POIs, 202 newspaper articles).
//
// Reference contract: ~/Development/Salem/docs/oracle-api.md (492 lines,
// owned by Salem). DO NOT modify Salem's data through this client — the
// corpus is read-only from the API surface; the only "write" verb is `ask`,
// which mutates the Oracle's in-memory conversation history.
//
// ─── Architecture decision ────────────────────────────────────────────────
// The Oracle serves a permissive CORS policy (`Access-Control-Allow-Origin: *`,
// methods `GET, POST, OPTIONS`), so the admin tool's browser code calls it
// directly at `http://localhost:8088/api/oracle/*`. We do NOT route through
// the cache-proxy or the Vite dev proxy — the Oracle is a sibling dev service,
// not part of LocationMapApp's backend. Per master plan §1341.
//
// ─── Base URL ────────────────────────────────────────────────────────────
// Default: `http://localhost:8088`. Override at runtime by setting
// `VITE_SALEM_ORACLE_URL` in `.env.local` (Vite-style env var) before
// starting the dev server. Example:
//   echo 'VITE_SALEM_ORACLE_URL=http://10.0.0.229:8088' > web/.env.local
// Useful when the operator runs the admin tool from a LAN host while the
// Salem testapp runs on the dev box.
//
// ─── Latency / timeout ────────────────────────────────────────────────────
// The `ask` endpoint is bottlenecked by Ollama running gemma3:27b on the
// workstation's RTX 3090. Typical latency is 5-15s per call. Per the contract,
// clients should plan a minimum 60s timeout. We default to 120s to leave
// headroom for cold-cache slow turns. Status / catalog endpoints are
// sub-millisecond and use a much shorter timeout.

// ─── Base URL resolution ──────────────────────────────────────────────────

const DEFAULT_ORACLE_BASE = 'http://localhost:8088'

function resolveBase(): string {
  // Vite injects import.meta.env.VITE_* at build time.
  const fromEnv = (import.meta.env?.VITE_SALEM_ORACLE_URL as string | undefined)?.trim()
  if (fromEnv) return fromEnv.replace(/\/$/, '')
  return DEFAULT_ORACLE_BASE
}

export const ORACLE_BASE = resolveBase()

// ─── Response shapes ──────────────────────────────────────────────────────

/** Successful /api/oracle/status response. */
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

/** Degraded /api/oracle/status response — LLM down but catalogs may still load. */
export interface OracleStatusDown {
  available: false
  reason: string
  game_date?: string
  newspaper_count?: number
  poi_count?: number
}

export type OracleStatus = OracleStatusOk | OracleStatusDown

/** One verbatim primary source citation in an Oracle ask response. */
export interface OraclePrimarySource {
  verbatim_text: string
  attribution: string
  modern_gloss?: string
  doc_type?: string
  relevance?: string
  score?: number
}

/** Successful /api/oracle/ask response. */
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

/** Error response from /api/oracle/ask (HTTP 200 with `error` key). */
export interface OracleAskErr {
  error: string
}

export type OracleAskResponse = OracleAskOk | OracleAskErr

/** Request body for /api/oracle/ask. */
export interface OracleAskRequest {
  question: string
  current_poi_id?: string
  current_newspaper_date?: string
  reset?: boolean
}

/** Lightweight POI entry from /api/oracle/pois (catalog list). */
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

// ─── Errors ───────────────────────────────────────────────────────────────

/**
 * Thrown when the network call itself fails (Oracle down, timeout, CORS,
 * DNS, etc). Distinguishes from a successful HTTP response that contains an
 * `error` key — those come back as the response shape.
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
      throw new OracleNetworkError(`Oracle request timed out after ${timeoutMs}ms`, e)
    }
    throw new OracleNetworkError(
      `Oracle request failed: ${e instanceof Error ? e.message : String(e)}`,
      e,
    )
  } finally {
    clearTimeout(timer)
  }
}

// ─── Public API ───────────────────────────────────────────────────────────

/**
 * Health check. Call once at admin-tool startup; show "Oracle: ready" or
 * "Oracle: unavailable" in the AdminLayout header. Sub-millisecond on the
 * server side; we use a 5s timeout so the pill flips to "unavailable" fast
 * when the testapp isn't running.
 *
 * Throws `OracleNetworkError` on connection failure (caller renders that as
 * "unavailable" with the Salem startup hint). On success, the response is
 * either `available: true` (LLM up) or `available: false` (catalogs loaded
 * but LLM down — the pill should still render unavailable in this case).
 */
export async function getStatus(): Promise<OracleStatus> {
  const res = await fetchWithTimeout(
    `${ORACLE_BASE}/api/oracle/status`,
    { method: 'GET' },
    5_000,
  )
  if (!res.ok) {
    throw new OracleNetworkError(`Oracle /status returned ${res.status}`)
  }
  return (await res.json()) as OracleStatus
}

/**
 * Main composition endpoint. The Oracle accepts a free-form question and
 * returns prose composed against the full Salem corpus. Optional context
 * pinning via `current_poi_id` (the LocationMapApp POI being edited) lets
 * deictic prompts ("rewrite this", "make him sound less judgmental") resolve
 * against the current edit subject.
 *
 * The Oracle keeps a 6-turn rolling conversation history shared across all
 * callers (single-developer dev surface). Pass `reset: true` on the first
 * call of a new editorial session to start fresh; omit it on follow-ups to
 * iterate ("make that two sentences shorter").
 *
 * Latency is 5-15s typical, bounded by Ollama on the operator's GPU. Default
 * timeout is 120s to leave headroom; the spec recommends 60s minimum.
 */
export async function ask(
  req: OracleAskRequest,
  timeoutMs = 120_000,
): Promise<OracleAskResponse> {
  const res = await fetchWithTimeout(
    `${ORACLE_BASE}/api/oracle/ask`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(req),
    },
    timeoutMs,
  )
  if (!res.ok) {
    throw new OracleNetworkError(`Oracle /ask returned ${res.status}`)
  }
  return (await res.json()) as OracleAskResponse
}

/**
 * Catalog list — Salem's 63 POIs (29 buildings + 34 landmarks). Used by
 * future "browse Salem catalog" features in the admin tool. Not strictly
 * required for the 9P.10b minimum but exported for downstream use.
 */
export async function listPois(): Promise<OraclePoiList> {
  const res = await fetchWithTimeout(
    `${ORACLE_BASE}/api/oracle/pois`,
    { method: 'GET' },
    5_000,
  )
  if (!res.ok) {
    throw new OracleNetworkError(`Oracle /pois returned ${res.status}`)
  }
  return (await res.json()) as OraclePoiList
}

/**
 * Type guard: did `ask` return a successful answer or an error envelope?
 */
export function isAskOk(r: OracleAskResponse): r is OracleAskOk {
  return (r as OracleAskErr).error === undefined
}
