// PassportTab — S268
//
// Operator-tunable filters that generate per-user POI Passports.
// Replaces the four stops-based tour UIs (HUD banner, Active Tour dialog,
// completion-stats body, numbered polyline pins) with a stamps-on-walk model.
//
// Left:  passport filter list (with poi_count badge + tour name).
// Right: form for the selected passport (or a new-passport form).
//        Includes "Preview matched POIs" button that runs the filter SQL
//        without saving — shows count + sample list.
//
// Filter SQL semantics live in cache-proxy/lib/admin-passport.js
// (buildFilterQuery). Bake script: cache-proxy/scripts/publish-poi-passport.js.

import { useCallback, useEffect, useMemo, useState } from 'react'

interface PassportFilter {
  id: string
  name: string
  tour_id: string | null
  tour_name: string | null
  categories: string[]
  require_historical_narration: boolean
  min_geofence_radius_m: number
  min_year_established: number | null
  max_year_established: number | null
  sort_order: number
  created_at: string
  updated_at: string
  poi_count: number
}

interface CategoryRow {
  id: string
  label: string
  color: string
  display_order: number
}

interface TourSummary {
  id: string
  name: string
}

interface PreviewSamplePoi {
  id: string
  name: string
  category: string
  lat: number
  lng: number
  geofence_radius_m: number | null
  year_established: number | null
  has_historical_narration: boolean
}

interface PreviewResult {
  count: number
  sample: PreviewSamplePoi[]
}

const ENDPOINT = '/api/admin/salem/passports'
const VALID_ID_RE = /^[a-z0-9_]+$/

async function fetchJson<T>(input: RequestInfo, init?: RequestInit): Promise<T> {
  const res = await fetch(input, { credentials: 'same-origin', ...(init ?? {}) })
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText}`
    try {
      const body = await res.json()
      if (body?.error) msg = `${res.status} ${body.error}`
    } catch { /* not json */ }
    throw new Error(msg)
  }
  return (await res.json()) as T
}

const EMPTY_DRAFT: Draft = {
  id: '',
  name: '',
  tour_id: null,
  categories: [],
  require_historical_narration: true,
  min_geofence_radius_m: 0,
  min_year_established: '',
  max_year_established: '',
  sort_order: 0,
}

interface Draft {
  id: string
  name: string
  tour_id: string | null
  categories: string[]
  require_historical_narration: boolean
  min_geofence_radius_m: number
  min_year_established: number | ''
  max_year_established: number | ''
  sort_order: number
}

function filterToDraft(f: PassportFilter): Draft {
  return {
    id: f.id,
    name: f.name,
    tour_id: f.tour_id,
    categories: f.categories ?? [],
    require_historical_narration: f.require_historical_narration,
    min_geofence_radius_m: f.min_geofence_radius_m,
    min_year_established: f.min_year_established ?? '',
    max_year_established: f.max_year_established ?? '',
    sort_order: f.sort_order,
  }
}

function draftToBody(d: Draft, includeId: boolean) {
  return {
    ...(includeId ? { id: d.id } : {}),
    name: d.name,
    tour_id: d.tour_id || null,
    categories: d.categories,
    require_historical_narration: d.require_historical_narration,
    min_geofence_radius_m: d.min_geofence_radius_m,
    min_year_established: d.min_year_established === '' ? null : d.min_year_established,
    max_year_established: d.max_year_established === '' ? null : d.max_year_established,
    sort_order: d.sort_order,
  }
}

export function PassportTab() {
  const [passports, setPassports] = useState<PassportFilter[] | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const [categories, setCategories] = useState<CategoryRow[]>([])
  const [tours, setTours] = useState<TourSummary[]>([])
  const [draft, setDraft] = useState<Draft>(EMPTY_DRAFT)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [preview, setPreview] = useState<PreviewResult | null>(null)
  const [previewBusy, setPreviewBusy] = useState(false)

  const loadPassports = useCallback(async () => {
    setError(null)
    try {
      const body = await fetchJson<{ count: number; passports: PassportFilter[] }>(ENDPOINT)
      setPassports(body.passports)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }, [])

  const loadSidecars = useCallback(async () => {
    try {
      const [catBody, tourBody] = await Promise.all([
        fetchJson<{ categories: CategoryRow[] }>('/api/admin/salem/categories'),
        fetchJson<{ tours: TourSummary[] }>('/api/admin/salem/tours'),
      ])
      setCategories(catBody.categories)
      setTours(tourBody.tours)
    } catch (e) {
      console.warn('[PassportTab] sidecar fetch failed:', e)
    }
  }, [])

  useEffect(() => {
    void loadPassports()
    void loadSidecars()
  }, [loadPassports, loadSidecars])

  // When selectedId changes, hydrate the draft from the matching passport.
  useEffect(() => {
    if (creating) return
    if (selectedId == null || !passports) return
    const found = passports.find(p => p.id === selectedId)
    if (found) {
      setDraft(filterToDraft(found))
      setPreview(null)
    }
  }, [selectedId, passports, creating])

  const handleNew = useCallback(() => {
    setCreating(true)
    setSelectedId(null)
    setDraft(EMPTY_DRAFT)
    setPreview(null)
    setError(null)
  }, [])

  const handleSelect = useCallback((id: string) => {
    setCreating(false)
    setSelectedId(id)
    setError(null)
  }, [])

  const handleToggleCategory = useCallback((catId: string) => {
    setDraft(prev => {
      const has = prev.categories.includes(catId)
      return {
        ...prev,
        categories: has ? prev.categories.filter(c => c !== catId) : [...prev.categories, catId],
      }
    })
  }, [])

  const validate = useCallback((d: Draft, requireId: boolean): string | null => {
    if (requireId) {
      if (!d.id.trim()) return 'id is required'
      if (!VALID_ID_RE.test(d.id)) return 'id must contain only lowercase letters, digits, and underscores'
    }
    if (!d.name.trim()) return 'name is required'
    if (d.min_geofence_radius_m < 0) return 'min_geofence_radius_m must be ≥ 0'
    if (d.min_year_established !== '' && d.max_year_established !== '' &&
        d.min_year_established > d.max_year_established) {
      return 'min_year_established cannot exceed max_year_established'
    }
    return null
  }, [])

  const handleSave = useCallback(async () => {
    const err = validate(draft, creating)
    if (err) {
      setError(err)
      return
    }
    setBusy(true)
    setError(null)
    try {
      if (creating) {
        await fetchJson(ENDPOINT, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(draftToBody(draft, true)),
        })
        await loadPassports()
        setCreating(false)
        setSelectedId(draft.id)
      } else if (selectedId) {
        await fetchJson(`${ENDPOINT}/${encodeURIComponent(selectedId)}`, {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(draftToBody(draft, false)),
        })
        await loadPassports()
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }, [creating, draft, selectedId, validate, loadPassports])

  const handleDelete = useCallback(async () => {
    if (!selectedId) return
    if (!window.confirm(`Delete passport "${draft.name}" (${selectedId})?\n\nThis also deletes all rows in salem_passport_pois for this filter. Cannot be undone.`)) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      await fetchJson(`${ENDPOINT}/${encodeURIComponent(selectedId)}`, { method: 'DELETE' })
      await loadPassports()
      setSelectedId(null)
      setDraft(EMPTY_DRAFT)
      setPreview(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }, [selectedId, draft.name, loadPassports])

  const handlePreview = useCallback(async () => {
    setPreviewBusy(true)
    setError(null)
    try {
      const body = await fetchJson<PreviewResult>(`${ENDPOINT}/preview`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(draftToBody(draft, false)),
      })
      setPreview(body)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setPreviewBusy(false)
    }
  }, [draft])

  const handleCancelCreate = useCallback(() => {
    setCreating(false)
    setDraft(EMPTY_DRAFT)
    setPreview(null)
    setError(null)
  }, [])

  const editing = creating || selectedId != null
  const sortedCategories = useMemo(
    () => [...categories].sort((a, b) => a.display_order - b.display_order),
    [categories],
  )

  return (
    <div className="flex-1 flex min-h-0">
      {/* Left: passport list */}
      <aside className="w-80 border-r border-slate-300 bg-white flex flex-col min-h-0">
        <div className="px-3 py-2 border-b border-slate-200 flex items-center gap-2">
          <span className="text-xs font-semibold uppercase tracking-wide text-slate-600 flex-1">
            Passports
          </span>
          <button
            type="button"
            onClick={handleNew}
            className="text-xs px-2 py-1 rounded bg-emerald-600 hover:bg-emerald-500 text-white"
          >
            + New
          </button>
        </div>

        <div className="flex-1 overflow-y-auto">
          {passports == null ? (
            <div className="p-3 text-sm text-slate-500">Loading…</div>
          ) : passports.length === 0 ? (
            <div className="p-3 text-sm text-slate-500">
              No passports yet. Click <strong>+ New</strong> to author your first filter.
            </div>
          ) : (
            <ul className="divide-y divide-slate-100">
              {passports.map(p => (
                <li key={p.id}>
                  <button
                    type="button"
                    onClick={() => handleSelect(p.id)}
                    className={
                      'w-full text-left px-3 py-2 hover:bg-slate-100 ' +
                      (selectedId === p.id && !creating
                        ? 'bg-indigo-50 border-l-4 border-indigo-500'
                        : 'border-l-4 border-transparent')
                    }
                  >
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-medium text-slate-900 flex-1 truncate">
                        {p.name}
                      </span>
                      <span className="text-xs px-1.5 py-0.5 rounded bg-slate-200 text-slate-700">
                        {p.poi_count}
                      </span>
                    </div>
                    <div className="text-xs text-slate-500 mt-0.5 truncate">
                      {p.tour_name ? `Tour: ${p.tour_name}` : 'Global'} · {p.id}
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </aside>

      {/* Right: editor */}
      <main className="flex-1 overflow-y-auto bg-slate-50 min-h-0">
        {!editing ? (
          <div className="p-8 text-sm text-slate-500">
            <p>Select a passport on the left or click <strong>+ New</strong> to author one.</p>
            <p className="mt-3 text-xs">
              POI Passports replace the old stops-based tour UI. Each filter row generates
              a list of POIs the user can stamp by hearing the narration. Bake with:
            </p>
            <pre className="mt-2 p-2 bg-slate-900 text-slate-100 text-xs rounded">
              node cache-proxy/scripts/publish-poi-passport.js
            </pre>
          </div>
        ) : (
          <div className="max-w-3xl mx-auto p-6 space-y-6">
            <header className="flex items-center gap-3">
              <h2 className="text-lg font-semibold text-slate-900 flex-1">
                {creating ? 'New Passport' : draft.name || selectedId}
              </h2>
              <button
                type="button"
                onClick={handlePreview}
                disabled={previewBusy}
                className="px-3 py-1.5 text-sm rounded bg-slate-700 hover:bg-slate-600 text-white disabled:opacity-50"
                title="Run the filter SQL without saving — shows count + sample of matched POIs"
              >
                {previewBusy ? 'Previewing…' : 'Preview matched POIs'}
              </button>
              <button
                type="button"
                onClick={handleSave}
                disabled={busy}
                className="px-3 py-1.5 text-sm rounded bg-emerald-600 hover:bg-emerald-500 text-white disabled:opacity-50"
              >
                {busy ? 'Saving…' : creating ? 'Create' : 'Save changes'}
              </button>
              {creating ? (
                <button
                  type="button"
                  onClick={handleCancelCreate}
                  disabled={busy}
                  className="px-3 py-1.5 text-sm rounded bg-slate-300 hover:bg-slate-200 text-slate-800"
                >
                  Cancel
                </button>
              ) : (
                <button
                  type="button"
                  onClick={handleDelete}
                  disabled={busy}
                  className="px-3 py-1.5 text-sm rounded bg-rose-600 hover:bg-rose-500 text-white disabled:opacity-50"
                >
                  Delete
                </button>
              )}
            </header>

            {error && (
              <div className="p-3 rounded bg-rose-100 border border-rose-300 text-rose-900 text-sm">
                {error}
              </div>
            )}

            <section className="bg-white border border-slate-200 rounded p-4 space-y-3">
              <h3 className="text-xs uppercase tracking-wide text-slate-500">Identity</h3>
              <div className="grid grid-cols-3 gap-3 items-center">
                <label className="text-sm text-slate-700">ID</label>
                <input
                  type="text"
                  className="col-span-2 px-2 py-1 border border-slate-300 rounded font-mono text-sm disabled:bg-slate-100 disabled:text-slate-500"
                  value={draft.id}
                  onChange={e => setDraft(prev => ({ ...prev, id: e.target.value }))}
                  disabled={!creating}
                  placeholder="default_salem_walking"
                />
                <label className="text-sm text-slate-700">Name</label>
                <input
                  type="text"
                  className="col-span-2 px-2 py-1 border border-slate-300 rounded text-sm"
                  value={draft.name}
                  onChange={e => setDraft(prev => ({ ...prev, name: e.target.value }))}
                  placeholder="Default Salem Walking"
                />
                <label className="text-sm text-slate-700">Tour</label>
                <select
                  className="col-span-2 px-2 py-1 border border-slate-300 rounded text-sm"
                  value={draft.tour_id ?? ''}
                  onChange={e => setDraft(prev => ({ ...prev, tour_id: e.target.value || null }))}
                >
                  <option value="">— Global (no tour) —</option>
                  {tours.map(t => (
                    <option key={t.id} value={t.id}>{t.name} ({t.id})</option>
                  ))}
                </select>
                <label className="text-sm text-slate-700">Sort order</label>
                <input
                  type="number"
                  className="col-span-2 px-2 py-1 border border-slate-300 rounded text-sm"
                  value={draft.sort_order}
                  onChange={e => setDraft(prev => ({ ...prev, sort_order: parseInt(e.target.value, 10) || 0 }))}
                />
              </div>
            </section>

            <section className="bg-white border border-slate-200 rounded p-4 space-y-3">
              <h3 className="text-xs uppercase tracking-wide text-slate-500">Categories</h3>
              <p className="text-xs text-slate-500">
                Select one or more POI categories. Empty = no category restriction.
              </p>
              <div className="flex flex-wrap gap-2">
                {sortedCategories.map(c => {
                  const on = draft.categories.includes(c.id)
                  return (
                    <button
                      key={c.id}
                      type="button"
                      onClick={() => handleToggleCategory(c.id)}
                      className={
                        'px-2 py-1 text-xs rounded border transition-colors ' +
                        (on
                          ? 'text-white border-transparent'
                          : 'text-slate-700 bg-white border-slate-300 hover:bg-slate-100')
                      }
                      style={on ? { backgroundColor: c.color } : undefined}
                    >
                      {c.label}
                    </button>
                  )
                })}
              </div>
            </section>

            <section className="bg-white border border-slate-200 rounded p-4 space-y-3">
              <h3 className="text-xs uppercase tracking-wide text-slate-500">Filter parameters</h3>
              <label className="flex items-center gap-2 text-sm text-slate-700">
                <input
                  type="checkbox"
                  checked={draft.require_historical_narration}
                  onChange={e => setDraft(prev => ({ ...prev, require_historical_narration: e.target.checked }))}
                />
                Require <code>historical_narration</code> (only pre-1860-narrated POIs count)
              </label>
              <div className="grid grid-cols-3 gap-3 items-center">
                <label className="text-sm text-slate-700">Min geofence radius (m)</label>
                <input
                  type="number"
                  min={0}
                  className="col-span-2 px-2 py-1 border border-slate-300 rounded text-sm"
                  value={draft.min_geofence_radius_m}
                  onChange={e => setDraft(prev => ({ ...prev, min_geofence_radius_m: parseInt(e.target.value, 10) || 0 }))}
                />
                <label className="text-sm text-slate-700">Year built — min</label>
                <input
                  type="number"
                  className="col-span-2 px-2 py-1 border border-slate-300 rounded text-sm"
                  value={draft.min_year_established}
                  placeholder="(no floor)"
                  onChange={e => {
                    const v = e.target.value
                    setDraft(prev => ({ ...prev, min_year_established: v === '' ? '' : (parseInt(v, 10) || 0) }))
                  }}
                />
                <label className="text-sm text-slate-700">Year built — max</label>
                <input
                  type="number"
                  className="col-span-2 px-2 py-1 border border-slate-300 rounded text-sm"
                  value={draft.max_year_established}
                  placeholder="(no ceiling)"
                  onChange={e => {
                    const v = e.target.value
                    setDraft(prev => ({ ...prev, max_year_established: v === '' ? '' : (parseInt(v, 10) || 0) }))
                  }}
                />
              </div>
              <p className="text-xs text-slate-500">
                Note: POIs with NULL <code>year_established</code> are excluded by any year-range filter.
                Currently ~387 of 445 historical POIs are missing this field (carry-forward from S192).
              </p>
            </section>

            {preview && (
              <section className="bg-white border border-slate-200 rounded p-4 space-y-3">
                <h3 className="text-xs uppercase tracking-wide text-slate-500">
                  Preview — {preview.count} POI{preview.count === 1 ? '' : 's'} matched
                  {preview.sample.length < preview.count && ` (showing first ${preview.sample.length})`}
                </h3>
                {preview.count === 0 ? (
                  <p className="text-sm text-slate-500">
                    No POIs match this filter. Loosen the criteria or check the category list.
                  </p>
                ) : (
                  <ul className="text-xs font-mono space-y-0.5 max-h-72 overflow-y-auto">
                    {preview.sample.map(s => (
                      <li key={s.id} className="text-slate-700 flex items-baseline gap-2">
                        <span className="text-slate-400 w-8 shrink-0">{s.year_established ?? '—'}</span>
                        <span className="text-slate-500 w-44 shrink-0 truncate">{s.category}</span>
                        <span className="flex-1 truncate">{s.name}</span>
                        <span className="text-slate-400 w-10 text-right shrink-0">{s.geofence_radius_m ?? '—'}m</span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            )}

            {!creating && (
              <section className="bg-white border border-slate-200 rounded p-4 space-y-2">
                <h3 className="text-xs uppercase tracking-wide text-slate-500">Bake</h3>
                <p className="text-xs text-slate-600">
                  After saving, re-run the publish chain so the asset DB matches the new filter:
                </p>
                <pre className="p-2 bg-slate-900 text-slate-100 text-xs rounded overflow-x-auto">
{`node cache-proxy/scripts/publish-poi-passport.js`}
                </pre>
                <p className="text-xs text-slate-500">
                  Currently baked: <strong>{passports?.find(p => p.id === selectedId)?.poi_count ?? 0}</strong> POIs
                  in <code>salem_passport_pois</code>. Re-run the bake whenever the filter or
                  upstream <code>salem_pois</code> changes.
                </p>
              </section>
            )}
          </div>
        )}
      </main>
    </div>
  )
}
