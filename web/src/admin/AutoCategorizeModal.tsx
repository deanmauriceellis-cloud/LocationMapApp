/**
 * S205 — AutoCategorizeModal
 *
 * Modal that drives `/admin/salem/auto-categorize` (dry-run + apply) and
 * `/admin/salem/apply-categorization` (operator-confirmed batch). Surfaced
 * from the Lint tab's left-aside header.
 */

import { useCallback, useEffect, useMemo, useState } from 'react'

interface Proposal {
  poi_id: string
  poi_name: string
  lat: number | null
  lng: number | null
  current_category: string
  current_subcategory: string | null
  proposed_category: string
  proposed_subcategory: string
  confidence: number
  match_kind: 'intel_id' | 'fuzzy' | string
  source: string
  auto_apply_eligible: boolean
}

interface AutoCategorizeResponse {
  generated_at: string
  total_proposals: number
  auto_applied: number
  auto_eligible_count: number
  review_count: number
  review: Proposal[]
  dry_run: boolean
}

interface Props {
  onClose: () => void
  onApplied: () => void
}

export function AutoCategorizeModal({ onClose, onApplied }: Props) {
  const [phase, setPhase] = useState<'loading' | 'preview' | 'applying' | 'error'>('loading')
  const [resp, setResp] = useState<AutoCategorizeResponse | null>(null)
  const [err, setErr] = useState<string | null>(null)
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [appliedSummary, setAppliedSummary] = useState<{ auto: number, manual: number } | null>(null)

  // Initial dry-run
  const fetchDryRun = useCallback(async () => {
    setPhase('loading')
    setErr(null)
    try {
      const r = await fetch('/api/admin/salem/auto-categorize', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ dry_run: true }),
      })
      if (!r.ok) throw new Error(`auto-categorize dry-run → ${r.status}`)
      const body = (await r.json()) as AutoCategorizeResponse
      setResp(body)
      // Default-select review rows with confidence ≥ 0.6 so the operator's first click is the safer batch.
      const initial = new Set<string>()
      for (const p of body.review) if (p.confidence >= 0.6) initial.add(p.poi_id)
      setSelected(initial)
      setPhase('preview')
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e))
      setPhase('error')
    }
  }, [])

  useEffect(() => { void fetchDryRun() }, [fetchDryRun])

  // Apply auto-eligible (no dry_run) — server applies confidence ≥ 0.9 same-cat proposals
  const applyAutoEligible = useCallback(async () => {
    setPhase('applying')
    try {
      const r = await fetch('/api/admin/salem/auto-categorize', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ dry_run: false }),
      })
      if (!r.ok) throw new Error(`auto-categorize apply → ${r.status}`)
      const body = (await r.json()) as AutoCategorizeResponse
      setResp(body)
      setAppliedSummary({ auto: body.auto_applied, manual: 0 })
      // Reset selected to remaining review default (≥0.6 confidence)
      const initial = new Set<string>()
      for (const p of body.review) if (p.confidence >= 0.6) initial.add(p.poi_id)
      setSelected(initial)
      setPhase('preview')
      onApplied()
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e))
      setPhase('error')
    }
  }, [onApplied])

  // Apply selected review rows
  const applySelected = useCallback(async () => {
    if (!resp || selected.size === 0) return
    const changes = resp.review
      .filter(p => selected.has(p.poi_id))
      .map(p => ({
        poi_id: p.poi_id,
        proposed_category: p.proposed_category,
        proposed_subcategory: p.proposed_subcategory,
      }))
    setPhase('applying')
    try {
      const r = await fetch('/api/admin/salem/apply-categorization', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ changes }),
      })
      if (!r.ok) throw new Error(`apply-categorization → ${r.status}`)
      const body = (await r.json()) as { applied: number, requested: number }
      setAppliedSummary({ auto: appliedSummary?.auto ?? 0, manual: (appliedSummary?.manual ?? 0) + body.applied })
      onApplied()
      // Refresh dry-run to show new state
      await fetchDryRun()
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e))
      setPhase('error')
    }
  }, [resp, selected, appliedSummary, onApplied, fetchDryRun])

  const toggleRow = useCallback((id: string) => {
    setSelected(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }, [])

  const selectAllInTable = useCallback(() => {
    if (!resp) return
    setSelected(new Set(resp.review.map(p => p.poi_id)))
  }, [resp])

  const clearSelection = useCallback(() => setSelected(new Set()), [])

  // Group review rows by transition (current → proposed) for header table
  const transitions = useMemo(() => {
    if (!resp) return []
    const m = new Map<string, number>()
    for (const p of resp.review) {
      const k = `${p.current_category} → ${p.proposed_category}`
      m.set(k, (m.get(k) ?? 0) + 1)
    }
    return [...m.entries()].sort((a, b) => b[1] - a[1])
  }, [resp])

  return (
    <div className="fixed inset-0 z-50 bg-black/40 flex items-center justify-center p-4">
      <div className="bg-white rounded-lg shadow-xl w-[1100px] max-w-full max-h-[90vh] flex flex-col">
        <header className="px-5 py-3 border-b border-slate-200 flex items-baseline justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-800">Auto-categorize commercial POIs</h2>
            <p className="text-xs text-slate-500 mt-0.5">
              Source: SalemIntelligence (port 8089) primary_category + name overrides. Excludes
              HISTORICAL_BUILDINGS, CIVIC, and tour POIs.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="px-3 py-1 text-sm rounded bg-slate-200 hover:bg-slate-300"
          >
            Close
          </button>
        </header>

        <div className="flex-1 min-h-0 overflow-y-auto px-5 py-4">
          {phase === 'loading' && (
            <div className="text-slate-600 text-sm">Building proposals from SalemIntelligence…</div>
          )}
          {phase === 'error' && (
            <div className="text-rose-700 bg-rose-50 border border-rose-200 rounded p-3 text-sm">
              {err}
              <button type="button" onClick={() => void fetchDryRun()} className="ml-3 underline">retry</button>
            </div>
          )}
          {(phase === 'preview' || phase === 'applying') && resp && (
            <>
              {/* Summary cards */}
              <div className="grid grid-cols-4 gap-3 mb-4">
                <SummaryCard label="Total proposals" value={resp.total_proposals} />
                <SummaryCard label="Auto-eligible (≥0.9, same cat)" value={resp.auto_eligible_count} accent="emerald" />
                <SummaryCard label="Review needed" value={resp.review_count} accent="amber" />
                <SummaryCard label="Already applied this session" value={(appliedSummary?.auto ?? 0) + (appliedSummary?.manual ?? 0)} accent="indigo" />
              </div>

              {/* Auto-apply CTA */}
              {resp.auto_eligible_count > 0 && (
                <div className="mb-4 p-3 bg-emerald-50 border border-emerald-200 rounded flex items-center justify-between">
                  <div className="text-sm text-emerald-900">
                    <strong>{resp.auto_eligible_count}</strong> proposals are high-confidence and don't change any
                    category — they only fill the missing subcategory. Safe to apply in one click.
                  </div>
                  <button
                    type="button"
                    onClick={() => void applyAutoEligible()}
                    disabled={phase === 'applying'}
                    className="px-4 py-2 text-sm rounded bg-emerald-600 hover:bg-emerald-700 text-white disabled:opacity-50"
                  >
                    {phase === 'applying' ? 'Applying…' : `Apply auto-eligible (${resp.auto_eligible_count})`}
                  </button>
                </div>
              )}

              {/* Transition heatmap */}
              {transitions.length > 0 && (
                <div className="mb-4">
                  <h3 className="text-xs font-semibold text-slate-700 uppercase tracking-wide mb-2">
                    Review transitions (current → proposed)
                  </h3>
                  <div className="flex flex-wrap gap-2 text-xs">
                    {transitions.slice(0, 16).map(([k, n]) => (
                      <span key={k} className="px-2 py-1 bg-slate-100 border border-slate-200 rounded text-slate-700 tabular-nums">
                        <strong>{n}</strong>&nbsp;{k}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {/* Review table */}
              <div className="flex items-center justify-between mb-2">
                <h3 className="text-sm font-semibold text-slate-700">
                  Review ({resp.review.length}) — selected {selected.size}
                </h3>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={selectAllInTable}
                    className="px-2 py-0.5 text-xs rounded bg-slate-200 hover:bg-slate-300"
                  >Select all</button>
                  <button
                    type="button"
                    onClick={clearSelection}
                    className="px-2 py-0.5 text-xs rounded bg-slate-200 hover:bg-slate-300"
                  >Clear</button>
                  <button
                    type="button"
                    onClick={() => void applySelected()}
                    disabled={phase === 'applying' || selected.size === 0}
                    className="px-3 py-0.5 text-xs rounded bg-indigo-600 hover:bg-indigo-700 text-white disabled:opacity-50"
                  >Apply selected ({selected.size})</button>
                </div>
              </div>

              <div className="border border-slate-200 rounded overflow-hidden">
                <table className="w-full text-xs">
                  <thead className="bg-slate-100 text-slate-700">
                    <tr>
                      <th className="w-8 px-2 py-1.5"></th>
                      <th className="px-2 py-1.5 text-left">POI</th>
                      <th className="px-2 py-1.5 text-left">Current</th>
                      <th className="px-2 py-1.5 text-left">Proposed</th>
                      <th className="px-2 py-1.5 text-right tabular-nums">Conf</th>
                      <th className="px-2 py-1.5 text-left">Source</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {resp.review.map(p => {
                      const isSel = selected.has(p.poi_id)
                      const catChange = p.current_category !== p.proposed_category
                      return (
                        <tr key={p.poi_id} className={isSel ? 'bg-indigo-50' : 'hover:bg-slate-50'}>
                          <td className="px-2 py-1">
                            <input
                              type="checkbox"
                              checked={isSel}
                              onChange={() => toggleRow(p.poi_id)}
                            />
                          </td>
                          <td className="px-2 py-1 max-w-[280px] truncate" title={p.poi_name}>{p.poi_name}</td>
                          <td className={`px-2 py-1 ${catChange ? 'text-rose-700' : 'text-slate-600'}`}>
                            {p.current_category}{p.current_subcategory ? <span className="text-slate-400">/{p.current_subcategory.replace(p.current_category + '__', '')}</span> : ''}
                          </td>
                          <td className="px-2 py-1 text-emerald-700">
                            {p.proposed_category}<span className="text-slate-400">/{p.proposed_subcategory.replace(p.proposed_category + '__', '')}</span>
                          </td>
                          <td className="px-2 py-1 text-right tabular-nums">
                            <span className={`px-1.5 py-0.5 rounded ${
                              p.confidence >= 0.9 ? 'bg-emerald-100 text-emerald-800' :
                              p.confidence >= 0.7 ? 'bg-amber-100 text-amber-800' :
                              'bg-slate-100 text-slate-700'
                            }`}>
                              {p.confidence.toFixed(2)}
                            </span>
                          </td>
                          <td className="px-2 py-1 text-slate-500 max-w-[260px] truncate" title={p.source}>{p.source}</td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}

function SummaryCard({ label, value, accent }: { label: string, value: number, accent?: 'emerald' | 'amber' | 'indigo' }) {
  const cls =
    accent === 'emerald' ? 'bg-emerald-50 text-emerald-900 border-emerald-200' :
    accent === 'amber'   ? 'bg-amber-50 text-amber-900 border-amber-200' :
    accent === 'indigo'  ? 'bg-indigo-50 text-indigo-900 border-indigo-200' :
                            'bg-slate-50 text-slate-800 border-slate-200'
  return (
    <div className={`border rounded p-3 ${cls}`}>
      <div className="text-xs uppercase tracking-wide opacity-70">{label}</div>
      <div className="text-xl font-semibold tabular-nums mt-0.5">{value.toLocaleString()}</div>
    </div>
  )
}
