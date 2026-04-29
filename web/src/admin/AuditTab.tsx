// AuditTab — S196
//
// Tabular view of salem_audit_log with filters + per-row revert. Backed by
// the PG row-level trigger that captures every INSERT/UPDATE/DELETE on the
// admin-managed tables (salem_pois, salem_tours, salem_tour_legs, salem_tour_stops,
// salem_poi_categories, salem_poi_subcategories, salem_witch_trials_*,
// salem_geocode_blacklist).
//
// Rows show: time, table, entity, action, changed fields, actor, source.
// Click a row to expand the diff (old → new per field). Per-row Revert
// button POSTs to /admin/salem/audit/:id/revert which re-applies old_values
// and writes a NEW audit row marking the original as reverted.

import { useCallback, useEffect, useMemo, useState } from 'react'

type AuditAction = 'INSERT' | 'UPDATE' | 'DELETE'

type AuditRow = {
  id: string
  recorded_at: string
  table_name: string
  entity_id: string
  action: AuditAction
  changed_fields: string[]
  actor: string | null
  source: string | null
  reverted: boolean
  reverted_at: string | null
  reverted_by: string | null
  revert_audit_id: string | null
}

type AuditDetail = AuditRow & {
  old_values: Record<string, unknown> | null
  new_values: Record<string, unknown> | null
}

type AuditStats = {
  summary: {
    total: number
    last_24h: number
    last_7d: number
    reverted_count: number
    entities_24h: number
    uninstrumented_count: number
  }
  by_table: { table_name: string; n: number }[]
  top_fields_7d: { field: string; n: number }[]
}

type Filters = {
  table: string
  entity: string
  actor: string
  source: string
  action: '' | AuditAction
  field: string
  since: string
  until: string
  reverted: '' | 'true' | 'false'
}

const EMPTY_FILTERS: Filters = {
  table: '', entity: '', actor: '', source: '',
  action: '', field: '', since: '', until: '', reverted: '',
}

const AUDITED_TABLES = [
  'salem_pois',
  'salem_tours',
  'salem_tour_legs',
  'salem_tour_stops',
  'salem_poi_categories',
  'salem_poi_subcategories',
  'salem_witch_trials_articles',
  'salem_witch_trials_newspapers',
  'salem_witch_trials_npc_bios',
  'salem_geocode_blacklist',
]

const PAGE_SIZE = 100

function fmtTime(iso: string): string {
  const d = new Date(iso)
  return d.toLocaleString('en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

function fmtRelTime(iso: string): string {
  const d = new Date(iso).getTime()
  const now = Date.now()
  const sec = Math.floor((now - d) / 1000)
  if (sec < 60) return `${sec}s ago`
  if (sec < 3600) return `${Math.floor(sec / 60)}m ago`
  if (sec < 86400) return `${Math.floor(sec / 3600)}h ago`
  return `${Math.floor(sec / 86400)}d ago`
}

function jsonPreview(v: unknown, max = 80): string {
  if (v === null || v === undefined) return '∅'
  if (typeof v === 'string') {
    if (v.length <= max) return JSON.stringify(v)
    return JSON.stringify(v.slice(0, max - 1)) + '…'
  }
  const s = JSON.stringify(v)
  return s.length > max ? s.slice(0, max - 1) + '…' : s
}

function actionColor(a: AuditAction): string {
  switch (a) {
    case 'INSERT': return 'bg-emerald-700/40 text-emerald-300 border-emerald-700/60'
    case 'UPDATE': return 'bg-sky-700/40 text-sky-300 border-sky-700/60'
    case 'DELETE': return 'bg-rose-700/40 text-rose-300 border-rose-700/60'
  }
}

export function AuditTab() {
  const [stats, setStats] = useState<AuditStats | null>(null)
  const [rows, setRows] = useState<AuditRow[]>([])
  const [total, setTotal] = useState(0)
  const [offset, setOffset] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [filters, setFilters] = useState<Filters>(EMPTY_FILTERS)
  const [pendingFilters, setPendingFilters] = useState<Filters>(EMPTY_FILTERS)
  const [expandedId, setExpandedId] = useState<string | null>(null)
  const [expandedDetail, setExpandedDetail] = useState<AuditDetail | null>(null)
  const [expandedLoading, setExpandedLoading] = useState(false)
  const [revertingId, setRevertingId] = useState<string | null>(null)

  const queryString = useMemo(() => {
    const sp = new URLSearchParams()
    for (const [k, v] of Object.entries(filters)) {
      if (v) sp.set(k, v)
    }
    sp.set('limit', String(PAGE_SIZE))
    sp.set('offset', String(offset))
    return sp.toString()
  }, [filters, offset])

  const fetchStats = useCallback(async () => {
    try {
      const r = await fetch('/api/admin/salem/audit/stats', { credentials: 'same-origin' })
      if (!r.ok) return
      const d = await r.json() as AuditStats
      setStats(d)
    } catch (_) { /* swallow */ }
  }, [])

  const fetchRows = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const r = await fetch(`/api/admin/salem/audit?${queryString}`, { credentials: 'same-origin' })
      if (!r.ok) {
        setError(`load failed: ${r.status}`)
        setRows([])
        return
      }
      const d = await r.json() as { rows: AuditRow[]; total: number }
      setRows(d.rows)
      setTotal(d.total)
    } catch (e) {
      setError(`load failed: ${(e as Error).message}`)
    } finally {
      setLoading(false)
    }
  }, [queryString])

  useEffect(() => { fetchStats() }, [fetchStats])
  useEffect(() => { fetchRows() }, [fetchRows])

  // Auto-refresh every 15s while the tab is mounted (audit log is high-velocity
  // when admin or automation is writing). Cheap query; ~100 rows JSON.
  useEffect(() => {
    const t = window.setInterval(() => {
      fetchRows()
      fetchStats()
    }, 15_000)
    return () => window.clearInterval(t)
  }, [fetchRows, fetchStats])

  const applyFilters = useCallback(() => {
    setOffset(0)
    setFilters(pendingFilters)
  }, [pendingFilters])

  const clearFilters = useCallback(() => {
    setPendingFilters(EMPTY_FILTERS)
    setFilters(EMPTY_FILTERS)
    setOffset(0)
  }, [])

  const expandRow = useCallback(async (row: AuditRow) => {
    if (expandedId === row.id) {
      setExpandedId(null)
      setExpandedDetail(null)
      return
    }
    setExpandedId(row.id)
    setExpandedDetail(null)
    setExpandedLoading(true)
    try {
      const r = await fetch(`/api/admin/salem/audit/${row.id}`, { credentials: 'same-origin' })
      if (r.ok) setExpandedDetail(await r.json() as AuditDetail)
    } finally {
      setExpandedLoading(false)
    }
  }, [expandedId])

  const revertRow = useCallback(async (row: AuditRow) => {
    if (row.reverted) return
    const reason = window.prompt(
      `Revert this change?\n\nTable: ${row.table_name}\nEntity: ${row.entity_id}\nAction: ${row.action}\nFields: ${row.changed_fields.join(', ')}\n\nOptional reason:`,
      '',
    )
    if (reason === null) return // cancelled
    setRevertingId(row.id)
    try {
      const r = await fetch(`/api/admin/salem/audit/${row.id}/revert`, {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason }),
      })
      const d = await r.json()
      if (!r.ok) {
        window.alert(`Revert failed: ${d.error || r.status}\n\n${d.detail || ''}`)
      } else {
        window.alert(`Reverted. New audit id: ${d.revert_audit_id ?? '?'}`)
        fetchRows()
        fetchStats()
      }
    } catch (e) {
      window.alert(`Revert failed: ${(e as Error).message}`)
    } finally {
      setRevertingId(null)
    }
  }, [fetchRows, fetchStats])

  return (
    <div className="flex flex-col h-full bg-slate-900 text-slate-200">
      {/* Stats strip */}
      <div className="border-b border-slate-800 bg-slate-950/60 px-4 py-3 grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
        <Stat label="Total events"  value={stats?.summary.total ?? '—'} />
        <Stat label="Last 24h"      value={stats?.summary.last_24h ?? '—'} sub={stats ? `${stats.summary.entities_24h} entities` : undefined} />
        <Stat label="Last 7d"       value={stats?.summary.last_7d ?? '—'} />
        <Stat label="Reverted"      value={stats?.summary.reverted_count ?? '—'} />
        <Stat label="Uninstrumented" value={stats?.summary.uninstrumented_count ?? '—'}
              hint="Audit rows with no actor/source — automation that hasn't been wired to set context yet."/>
        <Stat label="Tables audited" value={stats?.by_table.length ?? '—'} />
      </div>

      {/* Filters */}
      <div className="border-b border-slate-800 bg-slate-900/60 px-4 py-3 flex flex-wrap gap-2 items-end">
        <FilterField label="Table" value={pendingFilters.table} onChange={(v) => setPendingFilters({ ...pendingFilters, table: v })}
          options={['', ...AUDITED_TABLES]} optionLabel={(v) => v || '— any —'} />
        <FilterText label="Entity id" value={pendingFilters.entity} onChange={(v) => setPendingFilters({ ...pendingFilters, entity: v })}
          placeholder="ropes_mansion" />
        <FilterText label="Field"  value={pendingFilters.field}  onChange={(v) => setPendingFilters({ ...pendingFilters, field: v })}
          placeholder="lat / historical_narration" />
        <FilterField label="Action" value={pendingFilters.action} onChange={(v) => setPendingFilters({ ...pendingFilters, action: v as Filters['action'] })}
          options={['', 'INSERT', 'UPDATE', 'DELETE']} optionLabel={(v) => v || '— any —'} />
        <FilterText label="Actor"  value={pendingFilters.actor}  onChange={(v) => setPendingFilters({ ...pendingFilters, actor: v })}
          placeholder="username" />
        <FilterText label="Source" value={pendingFilters.source} onChange={(v) => setPendingFilters({ ...pendingFilters, source: v })}
          placeholder="admin-ui / automation" />
        <FilterField label="Reverted" value={pendingFilters.reverted} onChange={(v) => setPendingFilters({ ...pendingFilters, reverted: v as Filters['reverted'] })}
          options={['', 'false', 'true']} optionLabel={(v) => v === 'true' ? 'reverted only' : v === 'false' ? 'not reverted' : '— any —'} />
        <div className="flex gap-2 ml-auto">
          <button type="button" onClick={applyFilters} className="px-3 py-1 text-sm rounded bg-indigo-600 hover:bg-indigo-500">Apply</button>
          <button type="button" onClick={clearFilters} className="px-3 py-1 text-sm rounded bg-slate-700 hover:bg-slate-600">Clear</button>
        </div>
      </div>

      {/* Result summary */}
      <div className="px-4 py-2 text-xs text-slate-400 flex items-center gap-3">
        {loading ? 'Loading…' : `${total.toLocaleString()} matching events — showing ${offset + 1}–${Math.min(offset + rows.length, total)}`}
        {error && <span className="text-rose-400">{error}</span>}
        <div className="flex-1" />
        <button type="button" disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - PAGE_SIZE))}
          className="px-2 py-0.5 rounded bg-slate-700 hover:bg-slate-600 disabled:opacity-30 disabled:cursor-not-allowed">Prev</button>
        <button type="button" disabled={offset + PAGE_SIZE >= total} onClick={() => setOffset(offset + PAGE_SIZE)}
          className="px-2 py-0.5 rounded bg-slate-700 hover:bg-slate-600 disabled:opacity-30 disabled:cursor-not-allowed">Next</button>
        <button type="button" onClick={() => { fetchRows(); fetchStats() }} className="px-2 py-0.5 rounded bg-slate-700 hover:bg-slate-600">Refresh</button>
      </div>

      {/* Audit table */}
      <div className="flex-1 min-h-0 overflow-auto">
        <table className="w-full text-sm">
          <thead className="sticky top-0 bg-slate-950/95 backdrop-blur z-10 border-b border-slate-800">
            <tr className="text-left">
              <th className="px-3 py-2 font-medium text-slate-400 w-44">Time</th>
              <th className="px-3 py-2 font-medium text-slate-400 w-32">Table</th>
              <th className="px-3 py-2 font-medium text-slate-400">Entity</th>
              <th className="px-3 py-2 font-medium text-slate-400 w-20">Action</th>
              <th className="px-3 py-2 font-medium text-slate-400">Changed fields</th>
              <th className="px-3 py-2 font-medium text-slate-400 w-32">Actor</th>
              <th className="px-3 py-2 font-medium text-slate-400 w-44">Source</th>
              <th className="px-3 py-2 font-medium text-slate-400 w-32">&nbsp;</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => {
              const isExpanded = expandedId === r.id
              return (
                <>
                  <tr key={r.id}
                      className={`border-b border-slate-800/60 hover:bg-slate-800/40 cursor-pointer ${isExpanded ? 'bg-slate-800/40' : ''}`}
                      onClick={() => expandRow(r)}>
                    <td className="px-3 py-1.5 align-top text-slate-300" title={fmtTime(r.recorded_at)}>
                      <div>{fmtRelTime(r.recorded_at)}</div>
                      <div className="text-[10px] text-slate-500">{fmtTime(r.recorded_at).split(', ')[1] ?? ''}</div>
                    </td>
                    <td className="px-3 py-1.5 align-top">
                      <code className="text-xs text-slate-400">{r.table_name.replace(/^salem_/, '')}</code>
                    </td>
                    <td className="px-3 py-1.5 align-top text-slate-200">
                      <code className="text-xs">{r.entity_id}</code>
                    </td>
                    <td className="px-3 py-1.5 align-top">
                      <span className={`px-1.5 py-0.5 rounded text-[10px] uppercase tracking-wide border ${actionColor(r.action)}`}>
                        {r.action}
                      </span>
                    </td>
                    <td className="px-3 py-1.5 align-top">
                      <div className="flex flex-wrap gap-1">
                        {r.changed_fields.slice(0, 5).map((f) => (
                          <code key={f} className="px-1 py-0.5 text-[10px] rounded bg-slate-800 text-slate-300">{f}</code>
                        ))}
                        {r.changed_fields.length > 5 && (
                          <span className="text-[10px] text-slate-500">+{r.changed_fields.length - 5} more</span>
                        )}
                      </div>
                    </td>
                    <td className="px-3 py-1.5 align-top">
                      {r.actor ? <span className="text-slate-300">{r.actor}</span> : <span className="text-slate-500 italic">—</span>}
                    </td>
                    <td className="px-3 py-1.5 align-top">
                      {r.source ? <code className="text-[11px] text-slate-400">{r.source}</code> : <span className="text-slate-500 italic">—</span>}
                    </td>
                    <td className="px-3 py-1.5 align-top text-right" onClick={(e) => e.stopPropagation()}>
                      {r.reverted ? (
                        <span className="text-[10px] text-amber-400 italic" title={`reverted ${r.reverted_at ? fmtRelTime(r.reverted_at) : ''} by ${r.reverted_by || '?'}`}>
                          reverted
                        </span>
                      ) : (
                        <button type="button"
                                onClick={() => revertRow(r)}
                                disabled={revertingId === r.id}
                                className="px-2 py-0.5 text-[11px] rounded bg-amber-700/60 hover:bg-amber-600 text-white disabled:opacity-50">
                          {revertingId === r.id ? '…' : 'Revert'}
                        </button>
                      )}
                    </td>
                  </tr>
                  {isExpanded && (
                    <tr className="bg-slate-950/60">
                      <td colSpan={8} className="px-4 py-3">
                        {expandedLoading && <div className="text-xs text-slate-500">Loading diff…</div>}
                        {!expandedLoading && expandedDetail && (
                          <DiffPanel detail={expandedDetail} />
                        )}
                      </td>
                    </tr>
                  )}
                </>
              )
            })}
            {!loading && !rows.length && (
              <tr><td colSpan={8} className="px-4 py-8 text-center text-slate-500">No matching events.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function Stat({ label, value, sub, hint }: { label: string; value: number | string; sub?: string; hint?: string }) {
  return (
    <div className="bg-slate-900 border border-slate-800 rounded p-2" title={hint}>
      <div className="text-[10px] uppercase tracking-wide text-slate-500">{label}</div>
      <div className="text-xl font-semibold text-slate-100">{typeof value === 'number' ? value.toLocaleString() : value}</div>
      {sub && <div className="text-[10px] text-slate-500">{sub}</div>}
    </div>
  )
}

function FilterText({ label, value, onChange, placeholder }: { label: string; value: string; onChange: (v: string) => void; placeholder?: string }) {
  return (
    <label className="flex flex-col text-[10px] uppercase tracking-wide text-slate-500">
      {label}
      <input type="text" value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder}
             className="mt-0.5 px-2 py-1 text-sm rounded bg-slate-800 border border-slate-700 text-slate-200 normal-case w-44" />
    </label>
  )
}

function FilterField({ label, value, onChange, options, optionLabel }: { label: string; value: string; onChange: (v: string) => void; options: string[]; optionLabel: (v: string) => string }) {
  return (
    <label className="flex flex-col text-[10px] uppercase tracking-wide text-slate-500">
      {label}
      <select value={value} onChange={(e) => onChange(e.target.value)}
              className="mt-0.5 px-2 py-1 text-sm rounded bg-slate-800 border border-slate-700 text-slate-200 normal-case w-44">
        {options.map((o) => <option key={o} value={o}>{optionLabel(o)}</option>)}
      </select>
    </label>
  )
}

function DiffPanel({ detail }: { detail: AuditDetail }) {
  const fields = detail.changed_fields
  if (detail.action === 'INSERT') {
    return (
      <div>
        <div className="text-xs text-emerald-400 font-medium mb-1">INSERT — initial values</div>
        <FieldGrid values={detail.new_values || {}} />
      </div>
    )
  }
  if (detail.action === 'DELETE') {
    return (
      <div>
        <div className="text-xs text-rose-400 font-medium mb-1">DELETE — captured row before deletion</div>
        <FieldGrid values={detail.old_values || {}} />
      </div>
    )
  }
  // UPDATE — show before/after for changed fields
  return (
    <div>
      <div className="text-xs text-sky-400 font-medium mb-1">UPDATE — diff</div>
      <table className="w-full text-xs">
        <thead>
          <tr className="text-left text-slate-500">
            <th className="px-2 py-1 font-medium">Field</th>
            <th className="px-2 py-1 font-medium">Before</th>
            <th className="px-2 py-1 font-medium">After</th>
          </tr>
        </thead>
        <tbody>
          {fields.map((f) => (
            <tr key={f} className="border-t border-slate-800">
              <td className="px-2 py-1 align-top"><code className="text-slate-300">{f}</code></td>
              <td className="px-2 py-1 align-top font-mono text-rose-300/80 break-all">{jsonPreview(detail.old_values?.[f], 200)}</td>
              <td className="px-2 py-1 align-top font-mono text-emerald-300/80 break-all">{jsonPreview(detail.new_values?.[f], 200)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function FieldGrid({ values }: { values: Record<string, unknown> }) {
  const keys = Object.keys(values).filter((k) => k !== 'updated_at' && k !== 'admin_dirty_at')
  return (
    <table className="w-full text-xs">
      <tbody>
        {keys.map((k) => (
          <tr key={k} className="border-t border-slate-800">
            <td className="px-2 py-0.5 align-top w-48"><code className="text-slate-300">{k}</code></td>
            <td className="px-2 py-0.5 align-top font-mono text-slate-300 break-all">{jsonPreview(values[k], 240)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
