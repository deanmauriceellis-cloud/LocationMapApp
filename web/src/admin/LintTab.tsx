// LintTab — S187
//
// Data-quality lint view for the Salem admin tool. Calls
// GET /api/admin/salem/lint, renders a left-side tree of category → check →
// count, and a right pane that shows offending entities with a plain-English
// message, a fix hint, and one-click jump to the existing POI editor / map.
//
// Address ↔ geocode mismatch is on-demand (slow tiger.geocode pass). The tree
// node has a [Run Verification] button that POSTs to .../address-geocode/run
// and then polls .../address-geocode/status until done.

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { PoiRow } from './PoiTree'

// ─── Types matching cache-proxy/lib/admin-lint.js ───────────────────────────

export interface LintItem {
  entity_type: 'poi' | 'tour'
  entity_id: string
  entity_label: string
  message: string
  fix_hint: string
  lat?: number
  lng?: number
  // Address-geocode mismatch extras
  distance_m?: number | null
  rating?: number | null
  suggested_lat?: number
  suggested_lng?: number
  // Cleanup check extra
  soft_deleted?: boolean
}

export interface LintCheck {
  id: string
  label: string
  category: string
  severity: 'info' | 'warn' | 'error'
  count: number
  items: LintItem[]
  error?: string
}

interface OnDemandCheck {
  id: string
  label: string
  category: string
  severity: 'info' | 'warn' | 'error'
  state: 'idle' | 'running' | 'done' | 'error'
  count: number
}

interface LintResponse {
  generated_at: string
  checks: LintCheck[]
  on_demand: OnDemandCheck[]
}

interface AddrGeocodeStatus {
  state: 'idle' | 'running' | 'done' | 'error'
  progress: number
  total: number
  started_at: string | null
  finished_at: string | null
  error: string | null
  items: LintItem[] | null
}

// ─── Props ──────────────────────────────────────────────────────────────────

export interface LintTabProps {
  /** Called when operator clicks "Open Editor" on a POI item. */
  onOpenPoi: (poiId: string) => void
  /**
   * Called when operator clicks "Show on Map". The map filters down to only
   * the POIs flagged by the current check; the clicked POI is selected and
   * panned to. Pass the full id list of the check + a short label for the
   * floating banner.
   */
  onShowPoiOnMap: (poiId: string, checkPoiIds: string[], checkLabel: string) => void
  /** Called when operator clicks an item belonging to a tour. */
  onOpenTour: (tourId: string) => void
  /** Called when operator clicks "Geocodes" — opens the geocode-candidates modal. */
  onOpenGeocodes: (poiId: string) => void
}

interface SuppressionRow {
  poi_id: string
  check_id: string
  reason: string | null
  suppressed_at: string
  poi_name: string | null
}

// ─── Severity styling ───────────────────────────────────────────────────────

function severityStyles(sev: LintCheck['severity']) {
  switch (sev) {
    case 'error': return { dot: 'bg-rose-600',   text: 'text-rose-700',   ring: 'ring-rose-200' }
    case 'warn':  return { dot: 'bg-amber-500',  text: 'text-amber-700',  ring: 'ring-amber-200' }
    default:      return { dot: 'bg-sky-500',    text: 'text-sky-700',    ring: 'ring-sky-200' }
  }
}

// ─── Component ──────────────────────────────────────────────────────────────

export function LintTab({ onOpenPoi, onShowPoiOnMap, onOpenTour, onOpenGeocodes }: LintTabProps) {
  const [data, setData] = useState<LintResponse | null>(null)
  const [loading, setLoading] = useState(false)
  const [err, setErr] = useState<string | null>(null)
  const [selectedCheckId, setSelectedCheckId] = useState<string | null>(null)
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set())
  const [addrStatus, setAddrStatus] = useState<AddrGeocodeStatus | null>(null)
  const pollRef = useRef<number | null>(null)
  // S187 — false-positive suppression. When non-null, the right pane shows
  // suppressed entries for the selected check instead of active ones.
  const [showingSuppressed, setShowingSuppressed] = useState(false)
  const [suppressionsByCheck, setSuppressionsByCheck] = useState<Record<string, SuppressionRow[]>>({})

  // ── Fetch lint results ────────────────────────────────────────────────────
  const fetchLint = useCallback(async () => {
    setLoading(true)
    setErr(null)
    try {
      const res = await fetch('/api/admin/salem/lint', { credentials: 'same-origin' })
      if (!res.ok) throw new Error(`GET /admin/salem/lint → ${res.status} ${res.statusText}`)
      const body = (await res.json()) as LintResponse
      setData(body)
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [])

  // ── Suppression management ────────────────────────────────────────────────
  const fetchSuppressions = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/salem/lint/suppressions', { credentials: 'same-origin' })
      if (!res.ok) return
      const body = (await res.json()) as { suppressions: SuppressionRow[] }
      const grouped: Record<string, SuppressionRow[]> = {}
      for (const s of body.suppressions) {
        ;(grouped[s.check_id] ||= []).push(s)
      }
      setSuppressionsByCheck(grouped)
    } catch (e) {
      console.error('[LintTab] fetchSuppressions error:', e)
    }
  }, [])

  const suppressItem = useCallback(async (poiId: string, checkId: string) => {
    const reason = window.prompt(
      `Suppress this POI from the "${checkId}" lint check?\n\n` +
      `Reason (e.g. "Salem Lighthouse — no street address by design"):`,
      '',
    )
    if (reason === null) return
    try {
      const res = await fetch(`/api/admin/salem/lint/poi/${encodeURIComponent(poiId)}/suppress`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ check_id: checkId, reason: reason.trim() || null }),
      })
      if (!res.ok) {
        const body = await res.json().catch(() => ({}))
        throw new Error(body.error || `${res.status}`)
      }
      await fetchLint()
      await fetchSuppressions()
    } catch (e) {
      window.alert(`Suppress failed: ${e instanceof Error ? e.message : String(e)}`)
    }
  }, [fetchLint, fetchSuppressions])

  const unsuppressItem = useCallback(async (poiId: string, checkId: string) => {
    try {
      const res = await fetch(
        `/api/admin/salem/lint/poi/${encodeURIComponent(poiId)}/suppress/${encodeURIComponent(checkId)}`,
        { method: 'DELETE', credentials: 'same-origin' },
      )
      if (!res.ok) throw new Error(`${res.status}`)
      await fetchLint()
      await fetchSuppressions()
    } catch (e) {
      window.alert(`Unsuppress failed: ${e instanceof Error ? e.message : String(e)}`)
    }
  }, [fetchLint, fetchSuppressions])

  useEffect(() => {
    void fetchLint()
    void fetchSuppressions()
  }, [fetchLint, fetchSuppressions])

  // ── Address-geocode poll ──────────────────────────────────────────────────
  const pollAddrStatus = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/salem/lint/address-geocode/status', {
        credentials: 'same-origin',
      })
      if (!res.ok) throw new Error(`status → ${res.status}`)
      const body = (await res.json()) as AddrGeocodeStatus
      setAddrStatus(body)
      if (body.state !== 'running') {
        if (pollRef.current) {
          window.clearInterval(pollRef.current)
          pollRef.current = null
        }
      }
    } catch (e) {
      console.error('[LintTab] addr-status poll error:', e)
    }
  }, [])

  useEffect(() => {
    void pollAddrStatus() // one-shot on mount in case a previous run finished
    return () => {
      if (pollRef.current) {
        window.clearInterval(pollRef.current)
        pollRef.current = null
      }
    }
  }, [pollAddrStatus])

  const startAddrGeocode = useCallback(async () => {
    try {
      const res = await fetch('/api/admin/salem/lint/address-geocode/run', {
        method: 'POST',
        credentials: 'same-origin',
      })
      if (!res.ok) throw new Error(`run → ${res.status}`)
      // Begin polling every 2s
      if (!pollRef.current) {
        pollRef.current = window.setInterval(() => void pollAddrStatus(), 2000)
      }
      void pollAddrStatus()
    } catch (e) {
      window.alert(`Failed to start address-geocode pass: ${e instanceof Error ? e.message : String(e)}`)
    }
  }, [pollAddrStatus])

  // ── Tree groups (category → checks) ───────────────────────────────────────
  const groups = useMemo(() => {
    if (!data) return []
    const byCat = new Map<string, (LintCheck | OnDemandCheck)[]>()
    for (const chk of data.checks) {
      const list = byCat.get(chk.category) ?? []
      list.push(chk)
      byCat.set(chk.category, list)
    }
    for (const od of data.on_demand) {
      const list = byCat.get(od.category) ?? []
      list.push(od)
      byCat.set(od.category, list)
    }
    return [...byCat.entries()].map(([cat, checks]) => ({
      category: cat,
      checks,
      total: checks.reduce((s, c) => s + (c.count || 0), 0),
    })).sort((a, b) => b.total - a.total)
  }, [data])

  const selectedCheck = useMemo(() => {
    if (!data || !selectedCheckId) return null
    if (selectedCheckId === 'address_geocode_mismatch') {
      // Synthesize from addrStatus if available
      const od = data.on_demand.find(o => o.id === 'address_geocode_mismatch')
      const items = addrStatus?.items ?? []
      return {
        id: 'address_geocode_mismatch',
        label: od?.label ?? 'Address ↔ geocode mismatch',
        category: 'Geography',
        severity: 'info' as const,
        count: items.length,
        items,
      } as LintCheck
    }
    return data.checks.find(c => c.id === selectedCheckId) ?? null
  }, [data, selectedCheckId, addrStatus])

  // ── Toggle collapse ───────────────────────────────────────────────────────
  const toggleCollapse = useCallback((cat: string) => {
    setCollapsed(prev => {
      const next = new Set(prev)
      if (next.has(cat)) next.delete(cat)
      else next.add(cat)
      return next
    })
  }, [])

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div className="flex h-full min-h-0 w-full">
      {/* Left tree */}
      <aside className="w-96 border-r border-slate-300 bg-white flex flex-col min-h-0">
        <div className="px-3 py-2 border-b border-slate-200 flex items-center justify-between">
          <span className="text-xs font-semibold uppercase tracking-wide text-slate-600">Lint Issues</span>
          <button
            type="button"
            onClick={() => void fetchLint()}
            disabled={loading}
            className="text-xs px-2 py-0.5 rounded bg-slate-200 hover:bg-slate-300 text-slate-700 disabled:opacity-50"
            title="Re-run all instant checks"
          >
            {loading ? 'Loading…' : 'Refresh'}
          </button>
        </div>
        <div className="flex-1 overflow-y-auto py-1 text-sm">
          {err && (
            <div className="px-3 py-2 text-rose-700 bg-rose-50 border-b border-rose-200 text-xs">
              {err}
            </div>
          )}
          {!data && !err && (
            <div className="px-3 py-2 text-slate-500 text-xs">Loading lint results…</div>
          )}
          {data && groups.length === 0 && (
            <div className="px-3 py-2 text-emerald-700 text-xs">All clean.</div>
          )}
          {data && groups.map(group => {
            const isCollapsed = collapsed.has(group.category)
            return (
              <div key={group.category} className="mb-1">
                <button
                  type="button"
                  onClick={() => toggleCollapse(group.category)}
                  className="w-full px-3 py-1.5 flex items-center justify-between hover:bg-slate-100 text-left"
                >
                  <span className="font-semibold text-slate-700 text-sm">
                    <span className="inline-block w-3 text-slate-400">{isCollapsed ? '▸' : '▾'}</span>{' '}
                    {group.category}
                  </span>
                  <span className="text-xs text-slate-500 tabular-nums">{group.total}</span>
                </button>
                {!isCollapsed && (
                  <ul className="ml-1">
                    {group.checks.map(chk => {
                      const isOnDemand = 'state' in chk
                      const isSelected = selectedCheckId === chk.id
                      const sev = severityStyles(chk.severity)
                      return (
                        <li key={chk.id}>
                          <button
                            type="button"
                            onClick={() => setSelectedCheckId(chk.id)}
                            className={`w-full pl-7 pr-3 py-1.5 text-left flex items-center justify-between gap-2 hover:bg-slate-50 ${
                              isSelected ? 'bg-indigo-50 ring-1 ring-inset ring-indigo-200' : ''
                            }`}
                          >
                            <span className="flex items-center gap-2 min-w-0">
                              <span className={`inline-block w-2 h-2 rounded-full ${sev.dot} flex-none`} />
                              <span className="text-xs text-slate-700 truncate">{chk.label}</span>
                            </span>
                            <span className="text-xs tabular-nums text-slate-500 flex-none">
                              {isOnDemand && chk.state === 'idle' && '—'}
                              {isOnDemand && chk.state === 'running' && '…'}
                              {!isOnDemand && (chk as LintCheck).error
                                ? <span className="text-rose-600" title={(chk as LintCheck).error}>err</span>
                                : chk.count}
                            </span>
                          </button>
                        </li>
                      )
                    })}
                  </ul>
                )}
              </div>
            )
          })}
          {data && (
            <div className="px-3 pt-3 pb-2 mt-2 border-t border-slate-200 text-[10px] text-slate-400">
              Generated {new Date(data.generated_at).toLocaleString()}
            </div>
          )}
        </div>
      </aside>

      {/* Right pane */}
      <main className="flex-1 min-h-0 flex flex-col bg-slate-50">
        {!selectedCheck && (
          <div className="flex-1 flex items-center justify-center text-slate-500 text-sm p-8 text-center">
            Pick a check on the left to drill in. Each item shows a plain-English description of
            the issue and a one-click jump to the editor or the map.
          </div>
        )}
        {selectedCheck && (
          <CheckDetailPane
            check={selectedCheck}
            addrStatus={selectedCheck.id === 'address_geocode_mismatch' ? addrStatus : null}
            onRunAddrGeocode={startAddrGeocode}
            onOpenPoi={onOpenPoi}
            onShowPoiOnMap={onShowPoiOnMap}
            onOpenTour={onOpenTour}
            onOpenGeocodes={onOpenGeocodes}
            onSuppress={suppressItem}
            onUnsuppress={unsuppressItem}
            suppressions={suppressionsByCheck[selectedCheck.id] ?? []}
            showingSuppressed={showingSuppressed}
            onToggleSuppressed={() => setShowingSuppressed(s => !s)}
          />
        )}
      </main>
    </div>
  )
}

// ─── Right-pane detail ──────────────────────────────────────────────────────

interface CheckDetailPaneProps {
  check: LintCheck
  addrStatus: AddrGeocodeStatus | null
  onRunAddrGeocode: () => void
  onOpenPoi: (poiId: string) => void
  onShowPoiOnMap: (poiId: string, checkPoiIds: string[], checkLabel: string) => void
  onOpenTour: (tourId: string) => void
  onOpenGeocodes: (poiId: string) => void
  onSuppress: (poiId: string, checkId: string) => void
  onUnsuppress: (poiId: string, checkId: string) => void
  suppressions: SuppressionRow[]
  showingSuppressed: boolean
  onToggleSuppressed: () => void
}

function CheckDetailPane({
  check, addrStatus, onRunAddrGeocode,
  onOpenPoi, onShowPoiOnMap, onOpenTour, onOpenGeocodes,
  onSuppress, onUnsuppress, suppressions, showingSuppressed, onToggleSuppressed,
}: CheckDetailPaneProps) {
  const sev = severityStyles(check.severity)
  const isAddrCheck = check.id === 'address_geocode_mismatch'

  return (
    <>
      <header className="px-5 py-3 border-b border-slate-200 bg-white">
        <div className="flex items-baseline justify-between gap-3">
          <div className="min-w-0">
            <div className="flex items-center gap-2">
              <span className={`inline-block w-2.5 h-2.5 rounded-full ${sev.dot}`} />
              <h2 className="text-base font-semibold text-slate-800">{check.label}</h2>
            </div>
            <p className="text-xs text-slate-500 mt-0.5">{check.category} · {check.severity}</p>
          </div>
          <div className="text-right flex items-center gap-3">
            <span className="text-2xl font-semibold tabular-nums text-slate-700">
              {showingSuppressed ? suppressions.length : check.count}
            </span>
            {suppressions.length > 0 && (
              <button
                type="button"
                onClick={onToggleSuppressed}
                className={`px-3 py-1 text-xs rounded ${
                  showingSuppressed
                    ? 'bg-slate-700 hover:bg-slate-800 text-white'
                    : 'bg-slate-200 hover:bg-slate-300 text-slate-700'
                }`}
                title="Toggle between active issues and suppressed false-positives"
              >
                {showingSuppressed ? `Active (${check.count})` : `Suppressed (${suppressions.length})`}
              </button>
            )}
            {isAddrCheck && addrStatus?.state !== 'running' && (
              <button
                type="button"
                onClick={onRunAddrGeocode}
                className="px-3 py-1 text-xs rounded bg-indigo-600 hover:bg-indigo-700 text-white"
              >
                {addrStatus?.state === 'done' ? 'Re-run' : 'Run Verification'}
              </button>
            )}
          </div>
        </div>
        {check.error && (
          <div className="mt-2 text-xs text-rose-700 bg-rose-50 border border-rose-200 rounded px-2 py-1">
            Check error: {check.error}
          </div>
        )}
        {isAddrCheck && addrStatus && (
          <AddrStatusBanner status={addrStatus} />
        )}
      </header>

      <div className="flex-1 min-h-0 overflow-y-auto">
        {!showingSuppressed && check.count === 0 && !isAddrCheck && (
          <div className="p-5 text-emerald-700 text-sm">No issues found in this check.</div>
        )}
        {showingSuppressed && (
          <ul className="divide-y divide-slate-200">
            {suppressions.map(s => (
              <li key={s.poi_id} className="px-5 py-3 hover:bg-white">
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <div className="text-sm font-medium text-slate-800 truncate">
                      {s.poi_name || s.poi_id}
                      <span className="ml-2 text-[10px] uppercase tracking-wide text-slate-500 bg-slate-100 px-1.5 py-0.5 rounded">
                        suppressed
                      </span>
                    </div>
                    <div className="text-xs text-slate-600 mt-1 italic">
                      {s.reason || <span className="text-slate-400">no reason given</span>}
                    </div>
                    <div className="text-[10px] text-slate-400 mt-1">
                      suppressed {new Date(s.suppressed_at).toLocaleString()}
                    </div>
                  </div>
                  <button
                    type="button"
                    onClick={() => onUnsuppress(s.poi_id, check.id)}
                    className="px-2 py-1 text-[11px] rounded bg-emerald-600 hover:bg-emerald-700 text-white whitespace-nowrap flex-none"
                    title="Remove suppression — this POI will appear in the lint check again"
                  >
                    Unsuppress
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
        {isAddrCheck && !showingSuppressed && (!addrStatus || addrStatus.state === 'idle') && (
          <div className="p-5 text-slate-600 text-sm">
            This is the deep address-verification check. It runs <code>tiger.geocode()</code> on
            every POI with an address (~1700 rows × ~250 ms = several minutes). Click
            <span className="font-semibold"> Run Verification</span> above to start; you can leave
            this tab and the pass will keep going in the background.
          </div>
        )}
        {!showingSuppressed && (
        <ul className="divide-y divide-slate-200">
          {check.items.map(item => (
            <li key={`${item.entity_type}:${item.entity_id}`} className="px-5 py-3 hover:bg-white">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <div className="text-sm font-medium text-slate-800 truncate">
                    {item.entity_label}
                    {item.soft_deleted && (
                      <span className="ml-2 text-[10px] uppercase tracking-wide text-rose-600 bg-rose-50 px-1.5 py-0.5 rounded">
                        soft-deleted
                      </span>
                    )}
                  </div>
                  <div className="text-xs text-slate-600 mt-1">{item.message}</div>
                  <div className="text-xs text-slate-500 mt-1 italic">→ {item.fix_hint}</div>
                  {(item.lat != null && item.lng != null) && (
                    <div className="text-[10px] text-slate-400 mt-1 tabular-nums">
                      {item.lat.toFixed(5)}, {item.lng.toFixed(5)}
                      {item.suggested_lat != null && item.suggested_lng != null && (
                        <> · suggested: {item.suggested_lat.toFixed(5)}, {item.suggested_lng.toFixed(5)}</>
                      )}
                    </div>
                  )}
                </div>
                <div className="flex flex-col gap-1 flex-none">
                  {item.entity_type === 'poi' ? (
                    <>
                      <button
                        type="button"
                        onClick={() => onOpenPoi(item.entity_id)}
                        className="px-2 py-1 text-[11px] rounded bg-indigo-600 hover:bg-indigo-700 text-white whitespace-nowrap"
                      >
                        Open Editor
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          const ids = check.items
                            .filter(i => i.entity_type === 'poi')
                            .map(i => i.entity_id)
                          onShowPoiOnMap(item.entity_id, ids, check.label)
                        }}
                        className="px-2 py-1 text-[11px] rounded bg-slate-200 hover:bg-slate-300 text-slate-700 whitespace-nowrap"
                      >
                        Show on Map
                      </button>
                      <button
                        type="button"
                        onClick={() => onOpenGeocodes(item.entity_id)}
                        className="px-2 py-1 text-[11px] rounded bg-amber-100 hover:bg-amber-200 text-amber-800 whitespace-nowrap"
                        title="View tiger.geocode() candidates and override or validate the location"
                      >
                        Geocodes
                      </button>
                      <button
                        type="button"
                        onClick={() => onSuppress(item.entity_id, check.id)}
                        className="px-2 py-1 text-[11px] rounded bg-rose-100 hover:bg-rose-200 text-rose-700 whitespace-nowrap"
                        title="Mark this POI as a known false-positive — it will stop appearing in this check"
                      >
                        Suppress
                      </button>
                    </>
                  ) : (
                    <button
                      type="button"
                      onClick={() => onOpenTour(item.entity_id)}
                      className="px-2 py-1 text-[11px] rounded bg-indigo-600 hover:bg-indigo-700 text-white whitespace-nowrap"
                    >
                      Open Tour
                    </button>
                  )}
                </div>
              </div>
            </li>
          ))}
        </ul>
        )}
      </div>
    </>
  )
}

function AddrStatusBanner({ status }: { status: AddrGeocodeStatus }) {
  if (status.state === 'idle') return null
  if (status.state === 'running') {
    const pct = status.total > 0 ? Math.round((status.progress / status.total) * 100) : 0
    return (
      <div className="mt-2 text-xs text-slate-600">
        Running: {status.progress.toLocaleString()} / {status.total.toLocaleString()} ({pct}%) — started{' '}
        {status.started_at ? new Date(status.started_at).toLocaleTimeString() : '—'}.
        <div className="mt-1 h-1.5 w-full bg-slate-200 rounded overflow-hidden">
          <div className="h-full bg-indigo-500 transition-all" style={{ width: `${pct}%` }} />
        </div>
      </div>
    )
  }
  if (status.state === 'done') {
    return (
      <div className="mt-2 text-xs text-emerald-700">
        Done. Scanned {status.total.toLocaleString()} POIs · finished{' '}
        {status.finished_at ? new Date(status.finished_at).toLocaleTimeString() : '—'}.
      </div>
    )
  }
  return (
    <div className="mt-2 text-xs text-rose-700 bg-rose-50 border border-rose-200 rounded px-2 py-1">
      Error: {status.error}
    </div>
  )
}

// silence unused-PoiRow-import warning when type isn't otherwise used at runtime
export type _LintTabPoiRow = PoiRow
