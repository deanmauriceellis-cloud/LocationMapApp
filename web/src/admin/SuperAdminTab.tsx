// SuperAdminTab — S248
//
// Post-V1 service console. The Android app ships V1 fully offline, so the
// parked external-service code paths (weather / MBTA / aircraft / METAR /
// webcams / TFRs) have no consumer in V1. This tab gives the operator one
// place to exercise each service through the existing cache-proxy routes
// — proves the upstream still responds, the API key still works, and the
// response shape hasn't drifted before counsel approves a service for
// V1.0.1+ and we wire it back into the Android ViewModels.
//
// All requests go through the existing Vite /api proxy
// (web/vite.config.ts:39 — strips /api and forwards to localhost:4300).
// The metadata block (which services to surface, what the env-key
// indicators say) comes from /api/admin/super-admin/health, defined in
// cache-proxy/lib/admin-super.js.

import { useCallback, useEffect, useMemo, useState } from 'react'

// ─── Types matching cache-proxy/lib/admin-super.js ──────────────────────────

interface ServiceMeta {
  id: string
  label: string
  icon: string
  upstream: string
  envKey: string | null
  envKeyPresent: boolean | null
  envKey2: string | null
  envKey2Present: boolean | null
  proxyPath: string
  defaultQuery: Record<string, string | number>
  notes: string
}

interface HealthResponse {
  generatedAt: string
  services: ServiceMeta[]
}

type CardStatus = 'idle' | 'loading' | 'ok' | 'err'

interface CardState {
  status: CardStatus
  durationMs: number | null
  bytes: number | null
  rawJson: string | null
  errMsg: string | null
  // Editable copy of defaultQuery so operator can tweak per-call.
  query: Record<string, string>
  expanded: boolean
}

const INITIAL_CARD_STATE: CardState = {
  status: 'idle',
  durationMs: null,
  bytes: null,
  rawJson: null,
  errMsg: null,
  query: {},
  expanded: false,
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function buildQueryString(q: Record<string, string>): string {
  const params = new URLSearchParams()
  for (const [k, v] of Object.entries(q)) {
    if (v !== '' && v != null) params.append(k, v)
  }
  const s = params.toString()
  return s ? `?${s}` : ''
}

function formatBytes(n: number | null): string {
  if (n == null) return '—'
  if (n < 1024) return `${n} B`
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`
  return `${(n / 1024 / 1024).toFixed(2)} MB`
}

function formatDuration(ms: number | null): string {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(2)} s`
}

function statusBadge(status: CardStatus) {
  switch (status) {
    case 'ok':      return { text: 'ok',      cls: 'bg-emerald-100 text-emerald-800 ring-emerald-300' }
    case 'err':     return { text: 'error',   cls: 'bg-rose-100 text-rose-800 ring-rose-300' }
    case 'loading': return { text: 'loading', cls: 'bg-amber-100 text-amber-800 ring-amber-300' }
    default:        return { text: 'idle',    cls: 'bg-slate-100 text-slate-700 ring-slate-300' }
  }
}

function envKeyChip(svc: ServiceMeta): { text: string; cls: string } {
  if (svc.envKey == null) {
    return { text: 'no key required', cls: 'bg-slate-100 text-slate-600 ring-slate-300' }
  }
  // Some services need 2 keys (OpenSky); "present" only if ALL set.
  const present = svc.envKeyPresent && (svc.envKey2 == null || svc.envKey2Present)
  if (present) {
    return { text: `key: ${svc.envKey} ✓`, cls: 'bg-emerald-100 text-emerald-800 ring-emerald-300' }
  }
  return { text: `key: ${svc.envKey} ✗ missing`, cls: 'bg-rose-100 text-rose-800 ring-rose-300' }
}

// ─── Component ──────────────────────────────────────────────────────────────

export function SuperAdminTab() {
  const [health, setHealth] = useState<HealthResponse | null>(null)
  const [healthLoading, setHealthLoading] = useState(false)
  const [healthErr, setHealthErr] = useState<string | null>(null)
  const [cards, setCards] = useState<Record<string, CardState>>({})

  const loadHealth = useCallback(async () => {
    setHealthLoading(true)
    setHealthErr(null)
    try {
      const res = await fetch('/api/admin/super-admin/health', { credentials: 'same-origin' })
      if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
      const data = (await res.json()) as HealthResponse
      setHealth(data)
      // Seed per-card state from defaultQuery only on first load (preserve
      // operator edits across re-fetches).
      setCards((prev) => {
        const next = { ...prev }
        for (const svc of data.services) {
          if (!next[svc.id]) {
            next[svc.id] = {
              ...INITIAL_CARD_STATE,
              query: Object.fromEntries(
                Object.entries(svc.defaultQuery).map(([k, v]) => [k, String(v)]),
              ),
            }
          }
        }
        return next
      })
    } catch (e) {
      setHealthErr(e instanceof Error ? e.message : String(e))
    } finally {
      setHealthLoading(false)
    }
  }, [])

  useEffect(() => { void loadHealth() }, [loadHealth])

  const updateCard = useCallback((id: string, patch: Partial<CardState>) => {
    setCards((prev) => ({ ...prev, [id]: { ...prev[id], ...patch } }))
  }, [])

  const updateQuery = useCallback((id: string, key: string, value: string) => {
    setCards((prev) => ({
      ...prev,
      [id]: { ...prev[id], query: { ...prev[id].query, [key]: value } },
    }))
  }, [])

  const testService = useCallback(async (svc: ServiceMeta) => {
    const card = cards[svc.id] ?? INITIAL_CARD_STATE
    updateCard(svc.id, { status: 'loading', errMsg: null })
    const url = `/api${svc.proxyPath}${buildQueryString(card.query)}`
    const t0 = performance.now()
    try {
      const res = await fetch(url, { credentials: 'same-origin' })
      const text = await res.text()
      const durationMs = Math.round(performance.now() - t0)
      const bytes = new Blob([text]).size
      let pretty = text
      try {
        pretty = JSON.stringify(JSON.parse(text), null, 2)
      } catch {
        // not JSON — leave raw
      }
      if (!res.ok) {
        updateCard(svc.id, {
          status: 'err',
          durationMs,
          bytes,
          rawJson: pretty,
          errMsg: `${res.status} ${res.statusText}`,
          expanded: true,
        })
        return
      }
      updateCard(svc.id, {
        status: 'ok',
        durationMs,
        bytes,
        rawJson: pretty,
        errMsg: null,
      })
    } catch (e) {
      const durationMs = Math.round(performance.now() - t0)
      updateCard(svc.id, {
        status: 'err',
        durationMs,
        bytes: null,
        rawJson: null,
        errMsg: e instanceof Error ? e.message : String(e),
      })
    }
  }, [cards, updateCard])

  const services = health?.services ?? []
  const okCount = useMemo(
    () => services.filter((s) => cards[s.id]?.status === 'ok').length,
    [services, cards],
  )
  const errCount = useMemo(
    () => services.filter((s) => cards[s.id]?.status === 'err').length,
    [services, cards],
  )

  return (
    <div className="flex h-full flex-col bg-slate-50">
      {/* Header */}
      <header className="flex items-center justify-between px-5 py-3 border-b border-slate-200 bg-white">
        <div className="flex items-center gap-3">
          <h2 className="text-base font-semibold text-slate-800">Super Admin · Post-V1 service console</h2>
          {health && (
            <span className="text-xs text-slate-500">
              {services.length} services · {okCount} ok · {errCount} err
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          {health?.generatedAt && (
            <span className="text-xs text-slate-400">
              health: {new Date(health.generatedAt).toLocaleTimeString()}
            </span>
          )}
          <button
            type="button"
            onClick={() => void loadHealth()}
            disabled={healthLoading}
            className="rounded-md bg-slate-700 px-3 py-1.5 text-xs font-medium text-white hover:bg-slate-800 disabled:opacity-50"
          >
            {healthLoading ? 'Refreshing…' : 'Refresh health'}
          </button>
        </div>
      </header>

      {/* Body */}
      <main className="flex-1 overflow-y-auto px-5 py-4">
        {healthErr && (
          <div className="mb-4 rounded-md border border-rose-300 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            Health probe failed: {healthErr}
          </div>
        )}

        {!health && !healthErr && (
          <div className="text-sm text-slate-500">Loading service registry…</div>
        )}

        <div className="mb-4 rounded-md border border-sky-200 bg-sky-50 px-3 py-2 text-xs text-sky-900">
          <strong>About this tab.</strong> The Android V1 app ships fully offline — none of these
          services are reachable from the phone. The cache-proxy still wraps them so the operator
          can verify upstream health and prepare V1.0.1+ unlocks. Calls go from the browser
          through the Vite <code>/api</code> proxy to <code>cache-proxy</code> on
          localhost:4300, then out to the upstream. Edit the defaults inline before pressing Test.
        </div>

        <div className="space-y-3">
          {services.map((svc) => {
            const card = cards[svc.id] ?? INITIAL_CARD_STATE
            const sb = statusBadge(card.status)
            const ek = envKeyChip(svc)
            return (
              <section
                key={svc.id}
                className="rounded-lg border border-slate-200 bg-white shadow-sm"
              >
                {/* Card header */}
                <div className="flex items-center justify-between gap-3 px-4 py-3 border-b border-slate-100">
                  <div className="flex items-center gap-3 min-w-0">
                    <span className="text-2xl leading-none" aria-hidden="true">{svc.icon}</span>
                    <div className="min-w-0">
                      <div className="text-sm font-semibold text-slate-800 truncate">
                        {svc.label}
                      </div>
                      <div className="text-xs text-slate-500 truncate">
                        <code className="text-slate-700">GET /api{svc.proxyPath}</code>
                        <span className="mx-1">·</span>
                        upstream: <span className="text-slate-700">{svc.upstream}</span>
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 flex-shrink-0">
                    <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ring-1 ${ek.cls}`}>
                      {ek.text}
                    </span>
                    <span className={`rounded-full px-2 py-0.5 text-[11px] font-medium ring-1 ${sb.cls}`}>
                      {sb.text}
                    </span>
                  </div>
                </div>

                {/* Notes */}
                <div className="px-4 pt-2 text-xs text-slate-500 italic">{svc.notes}</div>

                {/* Query inputs */}
                <div className="px-4 pt-2 pb-3 grid grid-cols-2 gap-2">
                  {Object.entries(card.query).map(([k, v]) => (
                    <label key={k} className="flex flex-col text-xs">
                      <span className="text-slate-600 font-medium mb-0.5">{k}</span>
                      <input
                        type="text"
                        value={v}
                        onChange={(e) => updateQuery(svc.id, k, e.target.value)}
                        className="rounded border border-slate-300 px-2 py-1 text-xs font-mono text-slate-800 focus:border-sky-500 focus:outline-none"
                      />
                    </label>
                  ))}
                  {Object.keys(card.query).length === 0 && (
                    <span className="col-span-2 text-xs text-slate-400 italic">(no query params)</span>
                  )}
                </div>

                {/* Action row */}
                <div className="flex items-center gap-3 px-4 py-2 border-t border-slate-100 bg-slate-50">
                  <button
                    type="button"
                    onClick={() => void testService(svc)}
                    disabled={card.status === 'loading'}
                    className="rounded-md bg-sky-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-sky-700 disabled:opacity-50"
                  >
                    {card.status === 'loading' ? 'Testing…' : 'Test'}
                  </button>
                  <span className="text-xs text-slate-600">
                    {formatDuration(card.durationMs)} · {formatBytes(card.bytes)}
                  </span>
                  {card.errMsg && (
                    <span className="text-xs text-rose-700 truncate flex-1">
                      {card.errMsg}
                    </span>
                  )}
                  {card.rawJson && (
                    <button
                      type="button"
                      onClick={() => updateCard(svc.id, { expanded: !card.expanded })}
                      className="ml-auto text-xs text-slate-600 hover:text-slate-900"
                    >
                      {card.expanded ? '▾ Hide response' : '▸ Show response'}
                    </button>
                  )}
                </div>

                {/* Raw response */}
                {card.expanded && card.rawJson && (
                  <pre className="m-0 max-h-96 overflow-auto bg-slate-900 px-4 py-3 text-[11px] leading-snug text-slate-100">
                    {card.rawJson}
                  </pre>
                )}
              </section>
            )
          })}
        </div>
      </main>
    </div>
  )
}
