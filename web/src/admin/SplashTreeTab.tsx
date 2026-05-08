// SplashTreeTab — S231
//
// Authoring UI for the splash-screen announcement decision tree. Three panes:
//
//   ┌─ Bucket list ─┬─ Bucket editor ──┬─ Live test ──┐
//   │ DANVERS  100  │ id, label, prio  │ lat / lon    │
//   │ NEAR      90  │ trigger fields   │ speed / hdg  │
//   │ APPRO     80  │ variant pool     │ → which      │
//   │ MID       70  │   add/remove/    │   bucket     │
//   │ FAR       60  │   edit text      │ → rendered   │
//   │ ──────────── │                  │   variants   │
//   │ UNKNOWN    0  │                  │              │
//   └───────────────┴───────────────────┴──────────────┘
//
// Backed by /admin/splash/tree (GET/PUT) + /admin/splash/test (POST). Save
// button writes the whole tree at once with server-side validation.

import { useCallback, useEffect, useMemo, useState } from 'react'

// ── Types (mirror cache-proxy/lib/splash-tree-types.js) ─────────────────────

const MOVEMENT_VALUES = ['STATIONARY','APPROACHING','DEPARTING','LATERAL','UNKNOWN'] as const
const PLACE_KIND_VALUES = ['IN_CITY','NEAR_CITY','IN_TOWN_ADJACENT_TO_SALEM','IN_COUNTY','OFFGRID'] as const
type Movement  = typeof MOVEMENT_VALUES[number]
type PlaceKind = typeof PLACE_KIND_VALUES[number]

interface Trigger {
  distance_min_mi?: number
  distance_max_mi?: number
  movement?: Movement[]
  place_kind?: PlaceKind[]
  town_name?: string
  no_gps?: boolean
}

interface Variant {
  id: string
  text: string
  notes?: string
  weight?: number
}

interface Bucket {
  id: string
  label: string
  priority: number
  trigger: Trigger
  variants: Variant[]
}

interface SplashTree {
  schema_version: 1
  updated_at: string
  buckets: Bucket[]
  fallback: Bucket
}

interface TestResult {
  context: Record<string, unknown> | null
  bucket_id: string | null
  bucket_label: string | null
  eligible_variants: Array<{ id: string; template: string; rendered: string; weight: number }>
}

// ── Component ───────────────────────────────────────────────────────────────

export function SplashTreeTab() {
  const [tree, setTree] = useState<SplashTree | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [dirty, setDirty] = useState(false)
  const [saving, setSaving] = useState(false)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [validationErrors, setValidationErrors] = useState<string[]>([])

  // Test pane state.
  const [tLat, setTLat] = useState('42.3601')   // Boston by default
  const [tLon, setTLon] = useState('-71.0589')
  const [tSpeed, setTSpeed] = useState('15')
  const [tBearing, setTBearing] = useState('45')
  const [testResult, setTestResult] = useState<TestResult | null>(null)
  const [testBusy, setTestBusy] = useState(false)
  const [testError, setTestError] = useState<string | null>(null)

  const refetch = useCallback(() => {
    fetch('/api/admin/splash/tree', { credentials: 'same-origin' })
      .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((body: SplashTree) => {
        setTree(body)
        setDirty(false)
        setValidationErrors([])
        setSaveError(null)
        if (!selectedId && body.buckets.length > 0) setSelectedId(body.buckets[0].id)
      })
      .catch((e: unknown) => {
        setSaveError(`Load failed: ${(e as Error).message ?? e}`)
      })
  }, [selectedId])

  useEffect(() => { refetch() }, [refetch])

  const allBuckets = useMemo<Bucket[]>(() => {
    if (!tree) return []
    return [...tree.buckets, tree.fallback]
  }, [tree])

  const selected = allBuckets.find(b => b.id === selectedId) ?? null

  const isFallback = (b: Bucket | null) => b != null && tree != null && b.id === tree.fallback.id

  const updateSelected = useCallback((mut: (b: Bucket) => Bucket) => {
    if (!tree || !selected) return
    const updated = mut(selected)
    if (isFallback(selected)) {
      setTree({ ...tree, fallback: updated })
    } else {
      setTree({
        ...tree,
        buckets: tree.buckets.map(b => b.id === selected.id ? updated : b),
      })
    }
    setDirty(true)
  }, [tree, selected])

  const handleSave = useCallback(async () => {
    if (!tree) return
    setSaving(true)
    setSaveError(null)
    setValidationErrors([])
    try {
      const res = await fetch('/api/admin/splash/tree', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(tree),
      })
      if (!res.ok) {
        const body = await res.json().catch(() => ({}))
        if (body.details && Array.isArray(body.details)) {
          setValidationErrors(body.details)
          throw new Error(body.error || `${res.status} ${res.statusText}`)
        }
        throw new Error(body.error || `${res.status} ${res.statusText}`)
      }
      const body = await res.json()
      setTree(prev => prev ? { ...prev, updated_at: body.updated_at } : prev)
      setDirty(false)
    } catch (e: unknown) {
      setSaveError((e as Error).message ?? String(e))
    } finally {
      setSaving(false)
    }
  }, [tree])

  const handleAddBucket = useCallback(() => {
    if (!tree) return
    const id = `bucket_${Date.now().toString(36)}`
    const newBucket: Bucket = {
      id,
      label: 'New bucket',
      priority: 50,
      trigger: {},
      variants: [{ id: `${id}_v1`, text: 'Welcome.' }],
    }
    setTree({ ...tree, buckets: [...tree.buckets, newBucket] })
    setSelectedId(id)
    setDirty(true)
  }, [tree])

  const handleDeleteBucket = useCallback((id: string) => {
    if (!tree) return
    if (id === tree.fallback.id) return
    if (!window.confirm(`Delete bucket "${id}"?`)) return
    setTree({ ...tree, buckets: tree.buckets.filter(b => b.id !== id) })
    if (selectedId === id) setSelectedId(tree.buckets[0]?.id ?? tree.fallback.id)
    setDirty(true)
  }, [tree, selectedId])

  const handleRunTest = useCallback(async () => {
    setTestBusy(true)
    setTestError(null)
    try {
      const body: Record<string, unknown> = {}
      if (tLat.trim()) body.lat = Number(tLat)
      if (tLon.trim()) body.lon = Number(tLon)
      if (tSpeed.trim()) body.speed_mps = Number(tSpeed)
      if (tBearing.trim()) body.bearing_deg = Number(tBearing)
      const res = await fetch('/api/admin/splash/test', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify(body),
      })
      if (!res.ok) {
        const eb = await res.json().catch(() => ({}))
        throw new Error(eb.error || `HTTP ${res.status}`)
      }
      setTestResult(await res.json() as TestResult)
    } catch (e: unknown) {
      setTestError((e as Error).message ?? String(e))
    } finally {
      setTestBusy(false)
    }
  }, [tLat, tLon, tSpeed, tBearing])

  if (!tree) {
    return (
      <div className="flex-1 flex items-center justify-center text-slate-500 text-sm">
        {saveError ? `Error: ${saveError}` : 'Loading splash tree…'}
      </div>
    )
  }

  return (
    <div className="flex-1 flex flex-col min-h-0 bg-slate-50">
      {/* Header bar */}
      <div className="flex items-center gap-3 px-4 py-2 border-b border-slate-300 bg-white">
        <h2 className="text-sm font-semibold text-slate-800">Splash Tree</h2>
        <span className="text-xs text-slate-500">
          last saved {new Date(tree.updated_at).toLocaleString()}
        </span>
        <div className="flex-1" />
        {dirty && <span className="text-xs text-amber-700">● unsaved changes</span>}
        <button
          type="button"
          onClick={handleAddBucket}
          className="px-2 py-1 text-xs rounded bg-slate-200 hover:bg-slate-300 text-slate-700"
        >
          + Bucket
        </button>
        <button
          type="button"
          onClick={handleSave}
          disabled={!dirty || saving}
          className="px-3 py-1 text-xs rounded bg-emerald-600 hover:bg-emerald-700 text-white disabled:opacity-40"
        >
          {saving ? 'Saving…' : 'Save'}
        </button>
      </div>

      {(saveError || validationErrors.length > 0) && (
        <div className="px-4 py-2 bg-rose-50 border-b border-rose-200 text-xs text-rose-900">
          {saveError && <div className="font-medium">{saveError}</div>}
          {validationErrors.length > 0 && (
            <ul className="list-disc ml-4 mt-1">
              {validationErrors.map((e, i) => <li key={i}>{e}</li>)}
            </ul>
          )}
        </div>
      )}

      {/* Three panes */}
      <div className="flex-1 flex min-h-0">
        {/* Left: bucket list */}
        <aside className="w-64 border-r border-slate-300 bg-white overflow-y-auto">
          {[...tree.buckets].sort((a, b) => b.priority - a.priority).map(b => (
            <BucketRow
              key={b.id}
              bucket={b}
              selected={selectedId === b.id}
              onSelect={() => setSelectedId(b.id)}
              onDelete={() => handleDeleteBucket(b.id)}
              isFallback={false}
            />
          ))}
          <BucketRow
            bucket={tree.fallback}
            selected={selectedId === tree.fallback.id}
            onSelect={() => setSelectedId(tree.fallback.id)}
            onDelete={() => { /* fallback can't be deleted */ }}
            isFallback={true}
          />
        </aside>

        {/* Middle: bucket editor */}
        <section className="flex-1 overflow-y-auto p-4 min-w-0">
          {selected ? (
            <BucketEditor
              bucket={selected}
              isFallback={isFallback(selected)}
              onChange={updateSelected}
            />
          ) : (
            <div className="text-sm text-slate-500">Select a bucket on the left to edit.</div>
          )}
        </section>

        {/* Right: test pane */}
        <aside className="w-80 border-l border-slate-300 bg-white overflow-y-auto p-3 text-xs">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-600 mb-2">Live Test</h3>
          <p className="text-slate-500 mb-2">Simulate a LocationContext and see which bucket fires.</p>
          <div className="grid grid-cols-2 gap-2 mb-2">
            <label className="flex flex-col">
              lat
              <input
                type="text"
                value={tLat}
                onChange={e => setTLat(e.target.value)}
                className="border border-slate-300 rounded px-2 py-1 font-mono"
              />
            </label>
            <label className="flex flex-col">
              lon
              <input
                type="text"
                value={tLon}
                onChange={e => setTLon(e.target.value)}
                className="border border-slate-300 rounded px-2 py-1 font-mono"
              />
            </label>
            <label className="flex flex-col">
              speed (m/s)
              <input
                type="text"
                value={tSpeed}
                onChange={e => setTSpeed(e.target.value)}
                className="border border-slate-300 rounded px-2 py-1 font-mono"
              />
            </label>
            <label className="flex flex-col">
              heading (°)
              <input
                type="text"
                value={tBearing}
                onChange={e => setTBearing(e.target.value)}
                className="border border-slate-300 rounded px-2 py-1 font-mono"
              />
            </label>
          </div>
          <button
            type="button"
            onClick={handleRunTest}
            disabled={testBusy}
            className="w-full mb-3 px-2 py-1.5 rounded bg-indigo-600 hover:bg-indigo-700 text-white disabled:opacity-40"
          >
            {testBusy ? 'Testing…' : 'Run'}
          </button>
          <div className="flex gap-1 mb-3 flex-wrap">
            <PresetBtn label="Salem" onClick={() => { setTLat('42.5224'); setTLon('-70.8961'); setTSpeed('0'); setTBearing('0') }} />
            <PresetBtn label="Beverly" onClick={() => { setTLat('42.5584'); setTLon('-70.8800'); setTSpeed('1.4'); setTBearing('200') }} />
            <PresetBtn label="Danvers" onClick={() => { setTLat('42.5750'); setTLon('-70.9300'); setTSpeed('0'); setTBearing('0') }} />
            <PresetBtn label="Boston→" onClick={() => { setTLat('42.3601'); setTLon('-71.0589'); setTSpeed('15'); setTBearing('40') }} />
            <PresetBtn label="LA stat." onClick={() => { setTLat('34.0522'); setTLon('-118.2437'); setTSpeed('0'); setTBearing('0') }} />
            <PresetBtn label="No GPS" onClick={() => { setTLat(''); setTLon(''); setTSpeed(''); setTBearing('') }} />
          </div>

          {testError && <div className="text-rose-700 mb-2">{testError}</div>}
          {testResult && (
            <div>
              <div className="bg-slate-50 border border-slate-200 rounded p-2 mb-2">
                <div className="font-semibold text-slate-700 mb-1">Resolved context</div>
                {testResult.context ? (
                  <table className="w-full text-[11px]">
                    <tbody>
                      {Object.entries(testResult.context)
                        .filter(([_, v]) => v != null && v !== '')
                        .map(([k, v]) => (
                          <tr key={k}>
                            <td className="text-slate-500 pr-2">{k}</td>
                            <td className="font-mono text-slate-800 break-all">{String(v)}</td>
                          </tr>
                        ))}
                    </tbody>
                  </table>
                ) : (
                  <div className="text-slate-500 italic">no GPS — fallback path</div>
                )}
              </div>
              <div className="bg-emerald-50 border border-emerald-200 rounded p-2 mb-2">
                <div className="text-emerald-900">
                  <span className="text-[11px] uppercase tracking-wide">Bucket</span>{' '}
                  <span className="font-semibold">{testResult.bucket_label || testResult.bucket_id || '—'}</span>
                </div>
              </div>
              <div className="bg-white border border-slate-200 rounded p-2">
                <div className="font-semibold text-slate-700 mb-1">
                  Eligible variants ({testResult.eligible_variants.length})
                </div>
                {testResult.eligible_variants.map(v => (
                  <div key={v.id} className="mb-2 last:mb-0">
                    <div className="text-[10px] text-slate-400 font-mono">{v.id}</div>
                    <div className="text-slate-500 italic text-[11px]">{v.template}</div>
                    <div className="text-slate-900">→ {v.rendered}</div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </aside>
      </div>
    </div>
  )
}

// ── Sub-components ──────────────────────────────────────────────────────────

function PresetBtn({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="px-1.5 py-0.5 text-[10px] rounded bg-slate-100 hover:bg-slate-200 border border-slate-200 text-slate-700"
    >
      {label}
    </button>
  )
}

function BucketRow({
  bucket, selected, onSelect, onDelete, isFallback,
}: {
  bucket: Bucket; selected: boolean; onSelect: () => void; onDelete: () => void; isFallback: boolean
}) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={
        'w-full text-left px-3 py-2 border-b border-slate-100 transition-colors ' +
        (selected ? 'bg-indigo-50 border-l-4 border-l-indigo-500' : 'hover:bg-slate-50')
      }
    >
      <div className="flex items-center gap-2">
        <span className="font-mono text-[11px] text-slate-500 w-6 text-right">
          {isFallback ? '∞' : bucket.priority}
        </span>
        <span className="text-sm font-medium text-slate-800 flex-1 truncate">{bucket.label}</span>
        {!isFallback && (
          <span
            role="button"
            tabIndex={0}
            onClick={(e) => { e.stopPropagation(); onDelete() }}
            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.stopPropagation(); onDelete() } }}
            className="text-slate-400 hover:text-rose-600 cursor-pointer"
            title="Delete bucket"
          >
            ✕
          </span>
        )}
      </div>
      <div className="text-[10px] text-slate-500 ml-8">
        {bucket.variants.length} variant{bucket.variants.length === 1 ? '' : 's'} · {bucket.id}
      </div>
    </button>
  )
}

function BucketEditor({
  bucket, isFallback, onChange,
}: {
  bucket: Bucket; isFallback: boolean; onChange: (mut: (b: Bucket) => Bucket) => void
}) {
  const tr = bucket.trigger || {}

  const setLabel    = (label: string)    => onChange(b => ({ ...b, label }))
  const setId       = (id: string)       => onChange(b => ({ ...b, id }))
  const setPriority = (p: number)        => onChange(b => ({ ...b, priority: p }))
  const setTrigger  = (next: Trigger)    => onChange(b => ({ ...b, trigger: next }))
  const setVariants = (vs: Variant[])    => onChange(b => ({ ...b, variants: vs }))

  const toggleArr = <T,>(arr: T[] | undefined, val: T): T[] => {
    const cur = arr || []
    return cur.includes(val) ? cur.filter(x => x !== val) : [...cur, val]
  }

  return (
    <div className="max-w-3xl">
      <div className="bg-white border border-slate-200 rounded p-4 mb-4">
        <div className="grid grid-cols-2 gap-3 mb-3">
          <label className="flex flex-col text-xs">
            <span className="text-slate-600 mb-1">Bucket id</span>
            <input
              type="text"
              value={bucket.id}
              onChange={e => setId(e.target.value)}
              disabled={isFallback}
              className="border border-slate-300 rounded px-2 py-1 font-mono disabled:bg-slate-100"
            />
          </label>
          <label className="flex flex-col text-xs">
            <span className="text-slate-600 mb-1">Label</span>
            <input
              type="text"
              value={bucket.label}
              onChange={e => setLabel(e.target.value)}
              className="border border-slate-300 rounded px-2 py-1"
            />
          </label>
          <label className="flex flex-col text-xs">
            <span className="text-slate-600 mb-1">Priority {isFallback && '(fallback always last)'}</span>
            <input
              type="number"
              value={bucket.priority}
              onChange={e => setPriority(Number(e.target.value))}
              disabled={isFallback}
              className="border border-slate-300 rounded px-2 py-1 font-mono w-32 disabled:bg-slate-100"
            />
          </label>
        </div>

        {/* Trigger editor */}
        <div className="mt-2 border-t border-slate-200 pt-3">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-600 mb-2">Trigger</h3>
          {isFallback ? (
            <div className="text-xs text-slate-500 italic">
              Fallback bucket — always matches when no GPS context is available. Trigger is locked.
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-3 text-xs">
              <label className="flex flex-col">
                <span className="text-slate-600 mb-1">distance_min_mi (≥)</span>
                <input
                  type="number"
                  value={tr.distance_min_mi ?? ''}
                  onChange={e => setTrigger({
                    ...tr,
                    distance_min_mi: e.target.value === '' ? undefined : Number(e.target.value),
                  })}
                  className="border border-slate-300 rounded px-2 py-1 font-mono"
                />
              </label>
              <label className="flex flex-col">
                <span className="text-slate-600 mb-1">distance_max_mi (≤)</span>
                <input
                  type="number"
                  value={tr.distance_max_mi ?? ''}
                  onChange={e => setTrigger({
                    ...tr,
                    distance_max_mi: e.target.value === '' ? undefined : Number(e.target.value),
                  })}
                  className="border border-slate-300 rounded px-2 py-1 font-mono"
                />
              </label>

              <div className="flex flex-col col-span-2">
                <span className="text-slate-600 mb-1">movement (any of)</span>
                <div className="flex flex-wrap gap-1.5">
                  {MOVEMENT_VALUES.map(m => (
                    <label key={m} className="inline-flex items-center gap-1">
                      <input
                        type="checkbox"
                        checked={tr.movement?.includes(m) ?? false}
                        onChange={() => setTrigger({
                          ...tr,
                          movement: toggleArr(tr.movement, m),
                        })}
                      />
                      <span className="font-mono text-[11px]">{m}</span>
                    </label>
                  ))}
                </div>
              </div>

              <div className="flex flex-col col-span-2">
                <span className="text-slate-600 mb-1">place_kind (any of)</span>
                <div className="flex flex-wrap gap-1.5">
                  {PLACE_KIND_VALUES.map(k => (
                    <label key={k} className="inline-flex items-center gap-1">
                      <input
                        type="checkbox"
                        checked={tr.place_kind?.includes(k) ?? false}
                        onChange={() => setTrigger({
                          ...tr,
                          place_kind: toggleArr(tr.place_kind, k),
                        })}
                      />
                      <span className="font-mono text-[11px]">{k}</span>
                    </label>
                  ))}
                </div>
              </div>

              <label className="flex flex-col col-span-2">
                <span className="text-slate-600 mb-1">town_name (exact match — used for the DANVERS override)</span>
                <input
                  type="text"
                  value={tr.town_name ?? ''}
                  onChange={e => setTrigger({
                    ...tr,
                    town_name: e.target.value || undefined,
                  })}
                  className="border border-slate-300 rounded px-2 py-1"
                />
              </label>
            </div>
          )}
        </div>
      </div>

      {/* Variants */}
      <div className="bg-white border border-slate-200 rounded p-4">
        <div className="flex items-center mb-3">
          <h3 className="text-xs font-semibold uppercase tracking-wide text-slate-600 flex-1">
            Variants ({bucket.variants.length})
          </h3>
          <button
            type="button"
            onClick={() => setVariants([
              ...bucket.variants,
              { id: `${bucket.id}_v${bucket.variants.length + 1}_${Date.now().toString(36)}`, text: '' },
            ])}
            className="px-2 py-1 text-xs rounded bg-slate-200 hover:bg-slate-300 text-slate-800"
          >
            + Variant
          </button>
        </div>
        <p className="text-[11px] text-slate-500 mb-3">
          Use slot tokens in <code className="bg-slate-100 px-1 rounded">{'{curly}'}</code> form. Known
          slots: <span className="font-mono">{'{miles} {miles_int} {city} {near_city} {town} {county} {state} {state_long} {compass} {compass_short} {movement} {place}'}</span>.
        </p>
        <ul className="flex flex-col gap-3">
          {bucket.variants.map((v, idx) => (
            <li key={v.id} className="border border-slate-200 rounded p-2">
              <div className="flex items-center gap-2 mb-1">
                <span className="text-[10px] text-slate-400 font-mono flex-1 truncate">{v.id}</span>
                <button
                  type="button"
                  onClick={() => setVariants(bucket.variants.filter((_, i) => i !== idx))}
                  className="text-slate-400 hover:text-rose-600 text-xs"
                  title="Remove this variant"
                >
                  ✕
                </button>
              </div>
              <textarea
                value={v.text}
                onChange={e => setVariants(bucket.variants.map((vv, i) =>
                  i === idx ? { ...vv, text: e.target.value } : vv
                ))}
                rows={2}
                className="w-full border border-slate-300 rounded px-2 py-1 text-sm font-sans"
                placeholder='e.g. "Approaching Salem from {compass} — about {miles} to go."'
              />
              {v.notes != null && (
                <div className="text-[10px] text-slate-500 italic mt-1">{v.notes}</div>
              )}
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
