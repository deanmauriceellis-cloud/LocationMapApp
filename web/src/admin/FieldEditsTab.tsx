// FieldEditsTab — S229 Part C
//
// Inbox for the on-device field-edit JSONL data pulled by
// tools/pull-field-edits.py. Each card shows an atomic edit (one tap →
// one save on the Lenovo) with current vs proposed side-by-side, plus
// any free-text note and attached recon photos. Apply routes through
// the existing /admin/salem/pois/:id endpoints; Reject is a sidecar
// dismiss so the same edit stops surfacing on subsequent fetches.
//
// The web admin is the gate — no edit hits salem_pois until the operator
// clicks Apply on this tab.
import { useCallback, useEffect, useMemo, useState } from 'react'

interface FieldEdit {
  schema: number
  ts: number
  session_ts: string
  device_model?: string
  poi_id: string
  poi_name: string
  current_lat?: number
  current_lng?: number
  current_category?: string
  current_subcategory?: string
  proposed_lat?: number
  proposed_lng?: number
  proposed_category?: string
  proposed_subcategory?: string
  note?: string
  photo_filenames?: string[]
}

interface InboxItem {
  editKey: string
  sessionDir: string
  sessionTs: string
  lineIndex: number
  status: 'pending' | 'applied' | 'dismissed'
  applied: { ts: string; result: unknown } | null
  dismissed: { ts: string; reason: string | null } | null
  edit: FieldEdit
}

interface InboxSession {
  sessionDir: string
  sessionTs: string
  count: number
  items: InboxItem[]
}

interface ListResponse {
  root: string
  totalPending: number
  sessions: InboxSession[]
}

type StatusFilter = 'pending' | 'applied' | 'dismissed' | 'all'

function fmtSession(ts: string): string {
  const m = /^(\d{4})(\d{2})(\d{2})-(\d{2})(\d{2})(\d{2})$/.exec(ts)
  if (!m) return ts
  const [, y, mo, d, h, mi] = m
  return `${y}-${mo}-${d} ${h}:${mi}`
}

function fmtCoord(v?: number): string {
  return v == null ? '—' : v.toFixed(5)
}

function distanceMeters(a: { lat?: number; lng?: number }, b: { lat?: number; lng?: number }): number | null {
  if (a.lat == null || a.lng == null || b.lat == null || b.lng == null) return null
  const R = 6_371_000
  const toRad = (x: number) => (x * Math.PI) / 180
  const dLat = toRad(b.lat - a.lat)
  const dLng = toRad(b.lng - a.lng)
  const lat1 = toRad(a.lat)
  const lat2 = toRad(b.lat)
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)))
}

export function FieldEditsTab() {
  const [data, setData] = useState<ListResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [syncing, setSyncing] = useState(false)
  const [busyKey, setBusyKey] = useState<string | null>(null)
  const [filter, setFilter] = useState<StatusFilter>('pending')

  const refetch = useCallback(() => {
    setLoading(true)
    fetch('/api/admin/field-edits', { credentials: 'same-origin' })
      .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((body: ListResponse) => {
        setData(body)
        setError(null)
      })
      .catch((e: unknown) => setError(String((e as Error).message ?? e)))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => { refetch() }, [refetch])

  const handleSync = useCallback(() => {
    setSyncing(true)
    fetch('/api/admin/field-edits/sync', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
    })
      .then(r => r.json())
      .then((body: { ok: boolean; stdout: string; stderr: string }) => {
        if (!body.ok) alert(`Sync exited non-zero:\n\n${body.stderr || body.stdout}`)
        refetch()
      })
      .catch((e: unknown) => alert(`Sync failed: ${(e as Error).message ?? e}`))
      .finally(() => setSyncing(false))
  }, [refetch])

  const handleApply = useCallback((editKey: string) => {
    setBusyKey(editKey)
    fetch(`/api/admin/field-edits/${editKey}/apply`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
    })
      .then(async r => {
        const body = await r.json()
        if (!r.ok) throw new Error(body.error || `HTTP ${r.status}`)
        return body
      })
      .then(() => refetch())
      .catch((e: unknown) => alert(`Apply failed: ${(e as Error).message ?? e}`))
      .finally(() => setBusyKey(null))
  }, [refetch])

  const handleReject = useCallback((editKey: string) => {
    setBusyKey(editKey)
    fetch(`/api/admin/field-edits/${editKey}/reject`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({}),
    })
      .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then(() => refetch())
      .catch((e: unknown) => alert(`Reject failed: ${(e as Error).message ?? e}`))
      .finally(() => setBusyKey(null))
  }, [refetch])

  const flatItems = useMemo(() => {
    if (!data) return []
    const all: InboxItem[] = []
    for (const s of data.sessions) all.push(...s.items)
    return all
      .filter(i => filter === 'all' ? true : i.status === filter)
      .sort((a, b) => b.edit.ts - a.edit.ts)
  }, [data, filter])

  const counts = useMemo(() => {
    const out = { pending: 0, applied: 0, dismissed: 0, all: 0 }
    if (!data) return out
    for (const s of data.sessions) for (const i of s.items) {
      out.all++
      out[i.status]++
    }
    return out
  }, [data])

  return (
    <div className="flex flex-col h-full min-h-0 bg-slate-100">
      <div className="px-4 py-2 border-b border-slate-300 bg-white flex items-center gap-3">
        <span className="text-sm font-semibold text-slate-700">Field Edits</span>
        <span className="text-xs text-slate-500">
          Source: <code className="text-slate-700">{data?.root ?? '/mnt/sdb-images/LMASalemFieldEdits'}</code>
        </span>
        <div className="flex-1" />
        <button
          type="button"
          onClick={handleSync}
          disabled={syncing}
          className="px-3 py-1 text-sm rounded bg-indigo-600 hover:bg-indigo-500 text-white transition-colors disabled:opacity-50"
          title="adb pull /sdcard/Documents/WickedSalemFieldEdits/ from the connected Lenovo"
        >
          {syncing ? 'Syncing…' : 'Sync from device'}
        </button>
        <button
          type="button"
          onClick={refetch}
          disabled={loading}
          className="px-2 py-1 text-sm rounded bg-slate-200 hover:bg-slate-300 disabled:opacity-50"
          title="Re-read disk"
        >
          ↻
        </button>
      </div>

      {/* Filter chips */}
      <div className="px-4 py-2 border-b border-slate-200 bg-white flex items-center gap-2 text-xs">
        {(['pending', 'applied', 'dismissed', 'all'] as StatusFilter[]).map(f => (
          <button
            key={f}
            type="button"
            onClick={() => setFilter(f)}
            className={`px-2 py-0.5 rounded ${
              filter === f
                ? 'bg-indigo-600 text-white'
                : 'bg-slate-200 text-slate-700 hover:bg-slate-300'
            }`}
          >
            {f} ({counts[f]})
          </button>
        ))}
      </div>

      {error && (
        <div className="mx-4 mt-3 p-2 text-sm rounded bg-rose-50 text-rose-800 border border-rose-200">
          {error}
        </div>
      )}

      {/* Inbox */}
      <div className="flex-1 min-h-0 overflow-y-auto px-4 py-3 space-y-3">
        {loading && <div className="text-sm text-slate-500">Loading…</div>}
        {!loading && flatItems.length === 0 && (
          <div className="text-sm text-slate-500">
            No {filter === 'all' ? '' : filter + ' '}edits.
            {filter === 'pending' && ' Run a field walk on the Lenovo and click Sync from device.'}
          </div>
        )}
        {flatItems.map(item => (
          <EditCard
            key={item.editKey}
            item={item}
            busy={busyKey === item.editKey}
            onApply={() => handleApply(item.editKey)}
            onReject={() => handleReject(item.editKey)}
          />
        ))}
      </div>
    </div>
  )
}

interface EditCardProps {
  item: InboxItem
  busy: boolean
  onApply: () => void
  onReject: () => void
}

function EditCard({ item, busy, onApply, onReject }: EditCardProps) {
  const e = item.edit
  const moveDist = distanceMeters(
    { lat: e.current_lat, lng: e.current_lng },
    { lat: e.proposed_lat, lng: e.proposed_lng },
  )
  const hasMove = e.proposed_lat != null && e.proposed_lng != null
  const hasCat = e.proposed_category != null && e.proposed_category !== e.current_category
  const hasSub = e.proposed_subcategory != null

  const statusBadge = item.status === 'pending'
    ? <span className="px-2 py-0.5 text-[10px] rounded bg-amber-100 text-amber-900 uppercase tracking-wide">Pending</span>
    : item.status === 'applied'
      ? <span className="px-2 py-0.5 text-[10px] rounded bg-emerald-100 text-emerald-900 uppercase tracking-wide">Applied</span>
      : <span className="px-2 py-0.5 text-[10px] rounded bg-slate-200 text-slate-700 uppercase tracking-wide">Dismissed</span>

  return (
    <div className="bg-white rounded shadow-sm border border-slate-200 p-3">
      <div className="flex items-start gap-2 mb-2">
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <div className="text-sm font-semibold text-slate-900 truncate">{e.poi_name}</div>
            {statusBadge}
          </div>
          <div className="text-xs text-slate-500 truncate">
            <code>{e.poi_id}</code> · {fmtSession(e.session_ts)}
            {e.device_model ? ` · ${e.device_model}` : ''}
          </div>
        </div>
        {item.status === 'pending' && (
          <div className="flex gap-2 shrink-0">
            <button
              type="button"
              onClick={onApply}
              disabled={busy}
              className="px-3 py-1 text-xs rounded bg-emerald-600 hover:bg-emerald-700 text-white disabled:opacity-50"
            >
              Apply
            </button>
            <button
              type="button"
              onClick={onReject}
              disabled={busy}
              className="px-3 py-1 text-xs rounded bg-slate-200 hover:bg-slate-300 text-slate-800 disabled:opacity-50"
            >
              Reject
            </button>
          </div>
        )}
      </div>

      {/* Diff grid */}
      <div className="grid grid-cols-[88px_1fr_1fr] gap-x-2 gap-y-1 text-xs">
        <div className="text-slate-500 font-medium">Field</div>
        <div className="text-slate-500 font-medium">Current</div>
        <div className="text-slate-500 font-medium">Proposed</div>

        <div className="text-slate-700">Lat / Lng</div>
        <div className="text-slate-800 font-mono">
          {fmtCoord(e.current_lat)}, {fmtCoord(e.current_lng)}
        </div>
        <div className={hasMove ? 'text-blue-700 font-mono font-medium' : 'text-slate-400 font-mono'}>
          {hasMove
            ? `${fmtCoord(e.proposed_lat)}, ${fmtCoord(e.proposed_lng)}${moveDist != null ? ` (Δ ${moveDist.toFixed(1)} m)` : ''}`
            : '(unchanged)'}
        </div>

        <div className="text-slate-700">Category</div>
        <div className="text-slate-800">{e.current_category ?? '—'}</div>
        <div className={hasCat ? 'text-blue-700 font-medium' : 'text-slate-400'}>
          {hasCat ? e.proposed_category : '(unchanged)'}
        </div>

        <div className="text-slate-700">Subcategory</div>
        <div className="text-slate-800">{e.current_subcategory ?? '—'}</div>
        <div className={hasSub ? 'text-blue-700 font-medium' : 'text-slate-400'}>
          {hasSub ? e.proposed_subcategory : '(unchanged)'}
        </div>
      </div>

      {e.note && (
        <div className="mt-3 p-2 rounded bg-amber-50 border border-amber-200">
          <div className="text-[10px] uppercase tracking-wide text-amber-900 font-semibold mb-0.5">Note</div>
          <div className="text-xs text-amber-950 whitespace-pre-wrap">{e.note}</div>
        </div>
      )}

      {e.photo_filenames && e.photo_filenames.length > 0 && (
        <div className="mt-3">
          <div className="text-[10px] uppercase tracking-wide text-slate-600 font-semibold mb-1">
            Photos ({e.photo_filenames.length})
          </div>
          <div className="flex flex-wrap gap-2">
            {e.photo_filenames.map(fn => (
              <a
                key={fn}
                href={`/api/admin/field-edits/photo/${item.sessionDir}/${fn}`}
                target="_blank"
                rel="noreferrer"
                title={fn}
                className="block"
              >
                <img
                  src={`/api/admin/field-edits/photo/${item.sessionDir}/${fn}`}
                  alt={fn}
                  className="w-24 h-24 object-cover rounded border border-slate-300 hover:border-indigo-500 transition-colors"
                />
              </a>
            ))}
          </div>
        </div>
      )}

      {item.status === 'applied' && item.applied && (
        <div className="mt-3 text-[11px] text-emerald-800 bg-emerald-50 rounded p-2">
          Applied {new Date(item.applied.ts).toLocaleString()}.
          <pre className="mt-1 whitespace-pre-wrap text-[10px] text-emerald-900">
            {JSON.stringify(item.applied.result, null, 2)}
          </pre>
        </div>
      )}
      {item.status === 'dismissed' && item.dismissed && (
        <div className="mt-3 text-[11px] text-slate-700 bg-slate-50 rounded p-2">
          Dismissed {new Date(item.dismissed.ts).toLocaleString()}
          {item.dismissed.reason ? ` — ${item.dismissed.reason}` : ''}.
        </div>
      )}
    </div>
  )
}
