// MassEditTab — S241
//
// Mass POI editing via spreadsheet round-trip:
//   1. Export → download .xlsx (frozen header, autofilter, hidden id column)
//   2. Edit in LibreOffice Calc
//   3. Upload → diff against live PG, render per-cell approve/reject grid
//   4. Apply → POST approved cells; server runs in a single PG transaction
//
// Companion backend: cache-proxy/lib/admin-mass-edit.js
import { useCallback, useMemo, useState } from 'react'

interface CellChange {
  column: string
  kind: 'text' | 'numeric' | 'boolean' | 'date' | 'jsonb' | 'soft_delete' | 'restore'
  old: string
  new: string | null
  warning?: string
  error?: string
}

interface PoiChangeset {
  poi_id: string
  poi_name: string
  poi_category: string
  stale: boolean
  soft_deleted: boolean
  pg_updated_at: string
  changes: CellChange[]
}

interface ImportResponse {
  exported_at: string | null
  imported_at: string
  poi_count_changed: number
  cell_count_changed: number
  skipped: { missingId: number; notFound: number; noChanges: number }
  changeset: PoiChangeset[]
}

interface ApplyResponse {
  applied_pois: number
  applied_cells: number
  soft_deleted?: number
  restored?: number
  results: Array<{ poi_id: string; poi_name: string; applied_cells: number }>
}

type Phase = 'idle' | 'uploading' | 'review' | 'applying' | 'done'

function approvalKey(poiId: string, column: string): string {
  return `${poiId}||${column}`
}

export function MassEditTab() {
  const [phase, setPhase] = useState<Phase>('idle')
  const [error, setError] = useState<string | null>(null)
  const [importResp, setImportResp] = useState<ImportResponse | null>(null)
  const [applyResp, setApplyResp] = useState<ApplyResponse | null>(null)
  const [approvals, setApprovals] = useState<Set<string>>(new Set())
  const [expandedPois, setExpandedPois] = useState<Set<string>>(new Set())
  const [confirmOpen, setConfirmOpen] = useState(false)

  // ─── Export ─────────────────────────────────────────────────────────────
  const handleExport = useCallback(() => {
    // Browser handles the download — Basic Auth cookie/header is sent
    // automatically for same-origin /api/admin/* requests in this session.
    window.location.href = '/api/admin/salem/pois/export-spreadsheet'
  }, [])

  // ─── Upload ─────────────────────────────────────────────────────────────
  const handleUpload = useCallback((file: File) => {
    setPhase('uploading')
    setError(null)
    setImportResp(null)
    setApplyResp(null)

    const fd = new FormData()
    fd.append('file', file)

    fetch('/api/admin/salem/pois/import-spreadsheet', {
      method: 'POST',
      credentials: 'same-origin',
      body: fd,
    })
      .then(async r => {
        const body = await r.json()
        if (!r.ok) throw new Error(body.error || `HTTP ${r.status}`)
        return body as ImportResponse
      })
      .then(body => {
        setImportResp(body)
        // Default approvals: every changed cell that has no error AND no warning.
        // Cells with warnings (e.g. lat/lng outside Salem bbox) stay unchecked
        // until the operator explicitly opts in.
        const initial = new Set<string>()
        for (const p of body.changeset) {
          for (const c of p.changes) {
            if (c.error || c.warning) continue
            // soft-delete / restore intents require explicit opt-in even when
            // the row is clean — high-impact actions don't auto-approve.
            if (c.kind === 'soft_delete' || c.kind === 'restore') continue
            // Skip auto-approval on soft-deleted POIs unless the change is a
            // restore (operator has to manually opt in to those anyway).
            if (p.soft_deleted) continue
            initial.add(approvalKey(p.poi_id, c.column))
          }
        }
        setApprovals(initial)
        // Auto-expand the first 5 POIs so the operator sees something immediately.
        setExpandedPois(new Set(body.changeset.slice(0, 5).map(p => p.poi_id)))
        setPhase('review')
      })
      .catch(e => {
        setError(String((e as Error).message ?? e))
        setPhase('idle')
      })
  }, [])

  // ─── Apply ──────────────────────────────────────────────────────────────
  // Two-step: clicking the Apply button opens a confirm dialog; only the
  // confirm button in that dialog actually POSTs. Mass-edit can land 500+
  // edits in a single transaction, so a single click was too easy to misfire.
  const handleRequestApply = useCallback(() => {
    if (!importResp) return
    if (approvals.size === 0) {
      alert('No cells approved. Tick at least one row before Apply.')
      return
    }
    setConfirmOpen(true)
  }, [importResp, approvals])

  const handleConfirmApply = useCallback(() => {
    if (!importResp) return
    setConfirmOpen(false)
    const payload: Array<{ poi_id: string; column: string; new: string | null }> = []
    for (const p of importResp.changeset) {
      for (const c of p.changes) {
        if (approvals.has(approvalKey(p.poi_id, c.column))) {
          payload.push({ poi_id: p.poi_id, column: c.column, new: c.new })
        }
      }
    }

    setPhase('applying')
    setError(null)
    // S245 — Mass-Edit Tab is an admin-driven path (operator picks each
    // approval explicitly), so it always passes ?force=true. The
    // no_overwrite lock targets AI / unattended automation, not the admin UI.
    fetch('/api/admin/salem/pois/apply-mass-edit?force=true', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ approvals: payload }),
    })
      .then(async r => {
        const body = await r.json()
        if (!r.ok) throw new Error(body.error || `HTTP ${r.status}`)
        return body as ApplyResponse
      })
      .then(body => {
        setApplyResp(body)
        setPhase('done')
      })
      .catch(e => {
        setError(String((e as Error).message ?? e))
        setPhase('review')
      })
  }, [importResp, approvals])

  const handleCancelReview = useCallback(() => {
    setImportResp(null)
    setApprovals(new Set())
    setExpandedPois(new Set())
    setError(null)
    setPhase('idle')
  }, [])

  const handleStartOver = useCallback(() => {
    setImportResp(null)
    setApplyResp(null)
    setApprovals(new Set())
    setExpandedPois(new Set())
    setError(null)
    setPhase('idle')
  }, [])

  // Tick every non-errored cell across the whole batch — including
  // soft-delete / restore intents and warning rows. Use when operator
  // has eyeballed the diff and wants to commit the entire batch.
  const handleAcceptAll = useCallback(() => {
    if (!importResp) return
    const next = new Set<string>()
    for (const p of importResp.changeset) {
      for (const c of p.changes) {
        if (c.error) continue
        next.add(approvalKey(p.poi_id, c.column))
      }
    }
    setApprovals(next)
  }, [importResp])

  const handleRejectAll = useCallback(() => {
    setApprovals(new Set())
  }, [])

  const toggleApproval = useCallback((poiId: string, column: string) => {
    const k = approvalKey(poiId, column)
    setApprovals(prev => {
      const next = new Set(prev)
      if (next.has(k)) next.delete(k); else next.add(k)
      return next
    })
  }, [])

  const togglePoi = useCallback((poiId: string) => {
    setExpandedPois(prev => {
      const next = new Set(prev)
      if (next.has(poiId)) next.delete(poiId); else next.add(poiId)
      return next
    })
  }, [])

  const approveAllInPoi = useCallback((poi: PoiChangeset) => {
    setApprovals(prev => {
      const next = new Set(prev)
      for (const c of poi.changes) {
        if (!c.error) next.add(approvalKey(poi.poi_id, c.column))
      }
      return next
    })
  }, [])

  const rejectAllInPoi = useCallback((poi: PoiChangeset) => {
    setApprovals(prev => {
      const next = new Set(prev)
      for (const c of poi.changes) next.delete(approvalKey(poi.poi_id, c.column))
      return next
    })
  }, [])

  const approvedCount = approvals.size
  const errorCount = useMemo(() => {
    if (!importResp) return 0
    let n = 0
    for (const p of importResp.changeset) for (const c of p.changes) if (c.error) n++
    return n
  }, [importResp])
  const warningCount = useMemo(() => {
    if (!importResp) return 0
    let n = 0
    for (const p of importResp.changeset) for (const c of p.changes) if (c.warning) n++
    return n
  }, [importResp])

  // ─── Render ─────────────────────────────────────────────────────────────
  return (
    <div className="h-full flex flex-col bg-slate-50">
      <div className="px-6 py-4 border-b border-slate-200 bg-white">
        <h2 className="text-lg font-semibold text-slate-800">Mass Edit POIs</h2>
        <p className="text-xs text-slate-500 mt-0.5">
          Export every POI to a spreadsheet, mass-edit in LibreOffice Calc, then upload
          for per-cell review against the live database.
        </p>
      </div>

      <div className="flex-1 overflow-auto px-6 py-4 space-y-4">
        {/* ── Step 1: Export ───────────────────────────────────────────── */}
        <section className="bg-white border border-slate-200 rounded p-4">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={handleExport}
              className="px-4 py-2 rounded bg-emerald-700 hover:bg-emerald-600 text-white text-sm font-medium"
            >
              ⬇ Download .xlsx (all POIs)
            </button>
            <div className="text-xs text-slate-600">
              Opens in LibreOffice Calc. Header row is frozen and every column has a
              filter dropdown. <span className="text-rose-700 font-medium">Do not modify
              column&nbsp;A</span> (hidden POI&nbsp;ID). Save as .xlsx or .ods, then
              upload below.
            </div>
          </div>
        </section>

        {/* ── Step 2: Upload ───────────────────────────────────────────── */}
        {phase === 'idle' || phase === 'uploading' ? (
          <section className="bg-white border border-slate-200 rounded p-4">
            <label className="block">
              <div className="text-sm font-medium text-slate-700 mb-2">
                Upload edited spreadsheet
              </div>
              <input
                type="file"
                accept=".xlsx,.ods"
                disabled={phase === 'uploading'}
                onChange={e => {
                  const f = e.target.files?.[0]
                  if (f) handleUpload(f)
                  e.target.value = ''
                }}
                className="block w-full text-sm text-slate-700 file:mr-3 file:py-1.5 file:px-3 file:rounded file:border-0 file:text-sm file:bg-slate-200 file:text-slate-800 hover:file:bg-slate-300"
              />
            </label>
            {phase === 'uploading' && (
              <div className="mt-3 text-sm text-slate-600">
                Parsing spreadsheet and diffing against live POI rows…
              </div>
            )}
            {error && (
              <div className="mt-3 text-sm text-rose-700 bg-rose-50 border border-rose-200 rounded px-3 py-2">
                {error}
              </div>
            )}
          </section>
        ) : null}

        {/* ── Step 3: Review ───────────────────────────────────────────── */}
        {phase === 'review' && importResp && (
          <>
            <section className="bg-white border border-slate-200 rounded p-4 sticky top-0 z-10">
              <div className="flex items-center gap-3 flex-wrap">
                <div className="text-sm text-slate-700">
                  <span className="font-semibold">{importResp.poi_count_changed}</span> POIs &middot;{' '}
                  <span className="font-semibold">{importResp.cell_count_changed}</span> cells changed
                </div>
                <div className="text-sm text-emerald-700">
                  <span className="font-semibold">{approvedCount}</span> approved
                </div>
                {warningCount > 0 && (
                  <div className="text-sm text-amber-700">
                    <span className="font-semibold">{warningCount}</span> warnings
                  </div>
                )}
                {errorCount > 0 && (
                  <div className="text-sm text-rose-700">
                    <span className="font-semibold">{errorCount}</span> parse errors
                  </div>
                )}
                <div className="text-xs text-slate-500">
                  Skipped: {importResp.skipped.missingId} missing-id,{' '}
                  {importResp.skipped.notFound} not-found,{' '}
                  {importResp.skipped.noChanges} unchanged
                </div>
                <div className="flex-1" />
                <button
                  type="button"
                  onClick={handleAcceptAll}
                  className="px-3 py-1.5 text-sm rounded bg-emerald-100 hover:bg-emerald-200 text-emerald-800 border border-emerald-300 font-medium"
                  title="Tick every non-errored cell in the entire batch (including soft-delete / restore)"
                >
                  ✓ Accept all changes
                </button>
                <button
                  type="button"
                  onClick={handleRejectAll}
                  disabled={approvedCount === 0}
                  className="px-3 py-1.5 text-sm rounded bg-slate-200 hover:bg-slate-300 text-slate-800 disabled:opacity-50"
                  title="Untick every cell across the batch"
                >
                  Reject all
                </button>
                <button
                  type="button"
                  onClick={handleCancelReview}
                  className="px-3 py-1.5 text-sm rounded bg-slate-200 hover:bg-slate-300 text-slate-800"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleRequestApply}
                  disabled={approvedCount === 0}
                  className="px-4 py-1.5 text-sm rounded bg-indigo-700 hover:bg-indigo-600 text-white font-medium disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  Apply {approvedCount} approved…
                </button>
              </div>
              {error && (
                <div className="mt-3 text-sm text-rose-700 bg-rose-50 border border-rose-200 rounded px-3 py-2">
                  {error}
                </div>
              )}
            </section>

            {importResp.changeset.length === 0 ? (
              <div className="bg-white border border-slate-200 rounded p-6 text-center text-sm text-slate-500">
                No changes detected. The spreadsheet matches the live database.
              </div>
            ) : (
              <div className="space-y-2">
                {importResp.changeset.map(poi => (
                  <PoiChangeCard
                    key={poi.poi_id}
                    poi={poi}
                    expanded={expandedPois.has(poi.poi_id)}
                    approvals={approvals}
                    onToggleExpand={() => togglePoi(poi.poi_id)}
                    onToggleCell={(col) => toggleApproval(poi.poi_id, col)}
                    onApproveAll={() => approveAllInPoi(poi)}
                    onRejectAll={() => rejectAllInPoi(poi)}
                  />
                ))}
              </div>
            )}
          </>
        )}

        {/* ── Step 4: Done ─────────────────────────────────────────────── */}
        {phase === 'applying' && (
          <section className="bg-white border border-slate-200 rounded p-4 text-sm text-slate-600">
            Applying approved cells…
          </section>
        )}

        {/* ── Confirm Apply modal ──────────────────────────────────────── */}
        {confirmOpen && importResp && (() => {
          // Build a per-POI tally of approved cells + flag any soft-delete /
          // restore / warning overrides that landed in the approval set.
          let affectedPois = 0
          let softDeletes = 0
          let restores = 0
          let warningOverrides = 0
          for (const p of importResp.changeset) {
            let approvedHere = 0
            for (const c of p.changes) {
              if (!approvals.has(approvalKey(p.poi_id, c.column))) continue
              approvedHere++
              if (c.kind === 'soft_delete') softDeletes++
              else if (c.kind === 'restore') restores++
              if (c.warning) warningOverrides++
            }
            if (approvedHere > 0) affectedPois++
          }
          return (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 px-4">
              <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-5">
                <h3 className="text-base font-semibold text-slate-800">Apply mass edit?</h3>
                <div className="mt-3 text-sm text-slate-700 space-y-1.5">
                  <div>
                    <span className="font-semibold">{approvedCount}</span> cell
                    {approvedCount === 1 ? '' : 's'} across{' '}
                    <span className="font-semibold">{affectedPois}</span> POI
                    {affectedPois === 1 ? '' : 's'} will be written to PG in a single transaction.
                  </div>
                  {softDeletes > 0 && (
                    <div className="text-rose-700">
                      ⚠ {softDeletes} POI{softDeletes === 1 ? '' : 's'} will be soft-deleted.
                    </div>
                  )}
                  {restores > 0 && (
                    <div className="text-emerald-700">
                      ↩ {restores} POI{restores === 1 ? '' : 's'} will be restored from soft-delete.
                    </div>
                  )}
                  {warningOverrides > 0 && (
                    <div className="text-amber-700">
                      ⚠ {warningOverrides} cell{warningOverrides === 1 ? '' : 's'} ticked despite
                      validation warnings.
                    </div>
                  )}
                  <div className="text-xs text-slate-500 pt-2">
                    Changes are reversible via the Audit tab, but a publish-chain run
                    afterwards will bake them into the Android asset DB.
                  </div>
                </div>
                <div className="mt-5 flex items-center justify-end gap-2">
                  <button
                    type="button"
                    onClick={() => setConfirmOpen(false)}
                    className="px-3 py-1.5 text-sm rounded bg-slate-200 hover:bg-slate-300 text-slate-800"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={handleConfirmApply}
                    className="px-4 py-1.5 text-sm rounded bg-indigo-700 hover:bg-indigo-600 text-white font-medium"
                  >
                    Apply {approvedCount}
                  </button>
                </div>
              </div>
            </div>
          )
        })()}

        {phase === 'done' && applyResp && (
          <section className="bg-emerald-50 border border-emerald-200 rounded p-4">
            <div className="text-sm text-emerald-800 font-medium">
              ✓ Applied {applyResp.applied_cells} cell{applyResp.applied_cells === 1 ? '' : 's'} across {applyResp.applied_pois} POI{applyResp.applied_pois === 1 ? '' : 's'}
              {applyResp.soft_deleted ? `, soft-deleted ${applyResp.soft_deleted}` : ''}
              {applyResp.restored ? `, restored ${applyResp.restored}` : ''}.
            </div>
            <div className="text-xs text-emerald-700 mt-1">
              Run the publish chain to push these changes to the Android asset DB:
              {' '}<code className="bg-emerald-100 px-1.5 py-0.5 rounded">node cache-proxy/scripts/publish-salem-pois.js</code>
              {' '}then the rest of the chain (or hit the POIs tab's Publish button).
            </div>
            <button
              type="button"
              onClick={handleStartOver}
              className="mt-3 px-3 py-1.5 text-sm rounded bg-emerald-700 hover:bg-emerald-600 text-white"
            >
              Start over
            </button>
          </section>
        )}
      </div>
    </div>
  )
}

// ─── Per-POI card ──────────────────────────────────────────────────────────

interface PoiChangeCardProps {
  poi: PoiChangeset
  expanded: boolean
  approvals: Set<string>
  onToggleExpand: () => void
  onToggleCell: (column: string) => void
  onApproveAll: () => void
  onRejectAll: () => void
}

function PoiChangeCard({
  poi, expanded, approvals,
  onToggleExpand, onToggleCell, onApproveAll, onRejectAll,
}: PoiChangeCardProps) {
  const approvedInThisPoi = poi.changes.filter(c => approvals.has(`${poi.poi_id}||${c.column}`)).length

  return (
    <div className={`bg-white border rounded ${
      poi.soft_deleted ? 'border-rose-300' : poi.stale ? 'border-amber-300' : 'border-slate-200'
    }`}>
      <button
        type="button"
        onClick={onToggleExpand}
        className="w-full px-4 py-2.5 flex items-center gap-3 text-left hover:bg-slate-50"
      >
        <span className="text-slate-400 text-xs w-3">{expanded ? '▾' : '▸'}</span>
        <span className={`font-medium ${poi.soft_deleted ? 'text-rose-700 line-through' : 'text-slate-800'}`}>
          {poi.poi_name}
        </span>
        <span className="text-xs text-slate-500">{poi.poi_category}</span>
        {poi.soft_deleted && (
          <span className="text-xs bg-rose-100 text-rose-800 px-2 py-0.5 rounded border border-rose-300">
            soft-deleted (restore before editing)
          </span>
        )}
        {poi.stale && (
          <span className="text-xs bg-amber-100 text-amber-800 px-2 py-0.5 rounded border border-amber-300">
            edited since export
          </span>
        )}
        <span className="flex-1" />
        <span className="text-xs text-slate-600">
          {approvedInThisPoi}/{poi.changes.length} approved
        </span>
      </button>

      {expanded && (
        <div className="border-t border-slate-200">
          <div className="px-4 py-2 flex items-center gap-2 bg-slate-50 border-b border-slate-200">
            <button
              type="button"
              onClick={onApproveAll}
              className="text-xs px-2 py-0.5 rounded bg-emerald-100 hover:bg-emerald-200 text-emerald-800 border border-emerald-300"
            >
              Approve all
            </button>
            <button
              type="button"
              onClick={onRejectAll}
              className="text-xs px-2 py-0.5 rounded bg-slate-200 hover:bg-slate-300 text-slate-700"
            >
              Reject all
            </button>
            <span className="flex-1" />
            <span className="text-[11px] text-slate-500 font-mono">
              {poi.poi_id} · pg_updated_at {poi.pg_updated_at?.slice(0, 19)}
            </span>
          </div>

          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-xs text-slate-500 uppercase tracking-wide">
              <tr>
                <th className="text-left px-4 py-1.5 w-10">Apply</th>
                <th className="text-left px-2 py-1.5">Column</th>
                <th className="text-left px-2 py-1.5">Current (PG)</th>
                <th className="text-left px-2 py-1.5">Proposed</th>
                <th className="text-left px-2 py-1.5">Notes</th>
              </tr>
            </thead>
            <tbody>
              {poi.changes.map(c => {
                const k = `${poi.poi_id}||${c.column}`
                const checked = approvals.has(k)
                return (
                  <tr key={c.column} className="border-t border-slate-100">
                    <td className="px-4 py-1.5">
                      <input
                        type="checkbox"
                        checked={checked}
                        disabled={!!c.error}
                        onChange={() => onToggleCell(c.column)}
                      />
                    </td>
                    <td className="px-2 py-1.5 font-mono text-xs text-slate-700">{c.column}</td>
                    <td className="px-2 py-1.5 text-slate-600 max-w-md truncate" title={c.old}>
                      {c.old === '' ? <span className="text-slate-400 italic">(empty)</span> : c.old}
                    </td>
                    <td className="px-2 py-1.5 text-slate-900 max-w-md truncate" title={c.new ?? ''}>
                      {c.kind === 'soft_delete' ? (
                        <span className="inline-flex items-center gap-1 text-xs bg-rose-100 text-rose-800 px-2 py-0.5 rounded border border-rose-300 font-medium">
                          🗑 Soft-delete row
                        </span>
                      ) : c.kind === 'restore' ? (
                        <span className="inline-flex items-center gap-1 text-xs bg-emerald-100 text-emerald-800 px-2 py-0.5 rounded border border-emerald-300 font-medium">
                          ↩ Restore row
                        </span>
                      ) : c.new === null
                        ? <span className="text-rose-600 italic">(clear to NULL)</span>
                        : c.new === ''
                          ? <span className="text-slate-400 italic">(empty)</span>
                          : c.new}
                    </td>
                    <td className="px-2 py-1.5 text-xs">
                      {c.error && <span className="text-rose-700">⚠ {c.error}</span>}
                      {c.warning && <span className="text-amber-700">⚠ {c.warning}</span>}
                      {!c.error && !c.warning && <span className="text-slate-400">{c.kind}</span>}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
