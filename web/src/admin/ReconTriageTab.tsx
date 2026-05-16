// ReconTriageTab — S270
//
// Layer 1 of the operator's photo-triage workflow. The on-device GPS-burst
// camera (GpsBurstCameraManager.kt) drops 1 Hz JPEGs into
// /sdcard/Pictures/WickedSalemRecon/. tools/pull-and-organize-burst-photos.py
// (or the in-tab "Pull from device" button) ferries them to
// /mnt/sdb-images/LMASalemPictures/session-*/. This tab gives the operator a
// flat thumbnail grid across one session at a time:
//
//   - Click a tile → toggle Keep (gold border + ★). Persists to
//     <session>/.kept.json via /admin/burst-photos/sessions/:s/kept.
//   - Double-click a tile → full-size lightbox + EXIF panel + keyboard nav.
//   - Footer "X kept / Y total" + Commit Cull button → confirm modal →
//     /admin/burst-photos/sessions/:s/commit-cull moves every unkept photo
//     to <session>/.deleted/. Survivors stay; .kept.json resets.
//
// Layer 2 (geo cluster pin view + per-cluster modal) is the existing
// BurstPhotosOverlay mounted inside AdminMap.

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { toastSuccess } from '../lib/toast'

interface BurstPhoto {
  id: string
  session: string
  name: string
  ts: number
  lat: number
  lon: number
}

interface SessionMeta {
  session: string
  count: number
  bbox: [number, number, number, number] | null
  firstTs: number | null
  lastTs: number | null
}

interface ListResponse {
  sessions: SessionMeta[]
  photos: BurstPhoto[]
  root?: string
}

interface DeviceRow {
  serial: string
  state: string
}

interface PullEvent {
  stage: 'pull' | 'organize' | 'done' | 'error'
  line?: string
  summary?: unknown
}

const API = '/api/admin/burst-photos'

export function ReconTriageTab() {
  const [sessions, setSessions] = useState<SessionMeta[]>([])
  const [allPhotos, setAllPhotos] = useState<BurstPhoto[]>([])
  const [activeSession, setActiveSession] = useState<string | null>(null)
  const [kept, setKept] = useState<Set<string>>(new Set())
  const [lightbox, setLightbox] = useState<number | null>(null)
  const [committing, setCommitting] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [pullOpen, setPullOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const refetchList = useCallback(async () => {
    try {
      const r = await fetch(API, { credentials: 'same-origin' })
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      const body = (await r.json()) as ListResponse
      setSessions(body.sessions ?? [])
      setAllPhotos(body.photos ?? [])
      setError(null)
    } catch (e) {
      setError((e as Error).message ?? String(e))
    }
  }, [])

  useEffect(() => {
    void refetchList()
  }, [refetchList])

  // Default to the largest session on first load.
  useEffect(() => {
    if (activeSession || sessions.length === 0) return
    const biggest = sessions.reduce((a, b) => (b.count > a.count ? b : a))
    setActiveSession(biggest.session)
  }, [sessions, activeSession])

  // Refetch kept set when session changes. S271: AbortController upgrade so
  // the in-flight request actually cancels on rapid session switches (was a
  // cancelled-flag pattern that let stale fetches complete then no-op).
  useEffect(() => {
    if (!activeSession) { setKept(new Set()); return }
    const ac = new AbortController()
    fetch(`${API}/sessions/${encodeURIComponent(activeSession)}/kept`, {
      credentials: 'same-origin',
      signal: ac.signal,
    })
      .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((body: { kept: string[] }) => setKept(new Set(body.kept ?? [])))
      .catch(e => {
        if ((e as Error)?.name === 'AbortError') return
        setError((e as Error).message ?? String(e))
      })
    return () => ac.abort()
  }, [activeSession])

  const photosForSession = useMemo(
    () => allPhotos.filter(p => p.session === activeSession).sort((a, b) => a.ts - b.ts),
    [allPhotos, activeSession],
  )

  const toggleKeep = useCallback(async (id: string) => {
    const next = new Set(kept)
    const willKeep = !next.has(id)
    if (willKeep) next.add(id); else next.delete(id)
    setKept(next) // optimistic
    if (!activeSession) return
    try {
      const r = await fetch(`${API}/sessions/${encodeURIComponent(activeSession)}/kept`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ id, kept: willKeep }),
      })
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
    } catch (e) {
      // Roll back on failure.
      setKept(prev => {
        const rb = new Set(prev)
        if (willKeep) rb.delete(id); else rb.add(id)
        return rb
      })
      setError((e as Error).message ?? String(e))
    }
  }, [kept, activeSession])

  const commitCull = useCallback(async () => {
    if (!activeSession) return
    setCommitting(true)
    try {
      const r = await fetch(`${API}/sessions/${encodeURIComponent(activeSession)}/commit-cull`, {
        method: 'POST',
        credentials: 'same-origin',
      })
      if (!r.ok) throw new Error(`HTTP ${r.status}`)
      const body = await r.json() as { kept: number; culled: number; errors: unknown[] }
      setKept(new Set())
      setConfirmOpen(false)
      await refetchList()
      toastSuccess(`Cull done: ${body.kept} kept, ${body.culled} culled${body.errors?.length ? ` (${body.errors.length} errors)` : ''}`)
    } catch (e) {
      setError((e as Error).message ?? String(e))
    } finally {
      setCommitting(false)
    }
  }, [activeSession, refetchList])

  const total = photosForSession.length
  const keptCount = photosForSession.filter(p => kept.has(p.id)).length

  return (
    <div className="flex-1 flex flex-col min-h-0 bg-slate-100">
      {/* ── Toolbar ─────────────────────────────────────────────────── */}
      <div className="flex items-center gap-3 px-4 py-2 bg-white border-b border-slate-300 shadow-sm">
        <div className="flex items-center gap-2">
          <label className="text-xs uppercase tracking-wider text-slate-500">Session</label>
          <select
            value={activeSession ?? ''}
            onChange={e => setActiveSession(e.target.value || null)}
            className="border border-slate-300 rounded px-2 py-1 text-sm bg-white"
          >
            {sessions.length === 0 && <option value="">(no sessions)</option>}
            {sessions.map(s => (
              <option key={s.session} value={s.session}>
                {s.session} — {s.count} photo{s.count === 1 ? '' : 's'}
              </option>
            ))}
          </select>
          <button
            type="button"
            onClick={() => void refetchList()}
            className="text-xs text-slate-600 hover:text-indigo-700 underline"
            title="Re-scan /mnt/sdb-images/LMASalemPictures"
          >
            Refresh
          </button>
        </div>

        <div className="flex-1" />

        <button
          type="button"
          onClick={() => setPullOpen(true)}
          className="px-3 py-1.5 text-sm bg-slate-700 hover:bg-slate-800 text-white rounded font-medium"
          title="adb pull WickedSalemRecon → organize into a new session"
        >
          📲 Pull from device
        </button>
      </div>

      {error && (
        <div className="px-4 py-1.5 bg-rose-50 border-b border-rose-200 text-xs text-rose-800 flex items-center gap-2">
          <span>⚠ {error}</span>
          <button onClick={() => setError(null)} className="ml-auto underline">dismiss</button>
        </div>
      )}

      {/* ── Header strip ────────────────────────────────────────────── */}
      <div className="flex items-center gap-3 px-4 py-2 bg-slate-50 border-b border-slate-200">
        <div className="text-sm">
          <span className="font-semibold text-amber-700">{keptCount}</span>
          <span className="text-slate-500"> kept · </span>
          <span className="text-slate-700">{total - keptCount}</span>
          <span className="text-slate-500"> to cull · </span>
          <span className="text-slate-500">{total} total</span>
        </div>
        <div className="flex-1" />
        <button
          type="button"
          onClick={() => setKept(new Set())}
          disabled={keptCount === 0 || committing}
          className="text-xs text-slate-600 hover:text-rose-700 underline disabled:opacity-30 disabled:no-underline"
        >
          Clear keep marks
        </button>
        <button
          type="button"
          onClick={() => setConfirmOpen(true)}
          disabled={total === 0 || committing}
          className={
            'px-3 py-1.5 text-sm rounded font-medium ' +
            (total === 0 || committing
              ? 'bg-slate-200 text-slate-400 cursor-not-allowed'
              : 'bg-rose-600 hover:bg-rose-700 text-white')
          }
        >
          🗑 Commit cull ({total - keptCount})
        </button>
      </div>

      {/* ── Grid ────────────────────────────────────────────────────── */}
      <div className="flex-1 overflow-y-auto p-3">
        {total === 0 ? (
          <div className="h-full flex items-center justify-center text-slate-400 text-sm">
            {activeSession ? 'No photos in this session.' : 'Pick a session to begin triage.'}
          </div>
        ) : (
          <div className="grid grid-cols-3 sm:grid-cols-4 md:grid-cols-6 lg:grid-cols-8 xl:grid-cols-10 gap-2">
            {photosForSession.map((p, i) => (
              <ThumbTile
                key={p.id}
                photo={p}
                kept={kept.has(p.id)}
                onToggle={() => void toggleKeep(p.id)}
                onOpen={() => setLightbox(i)}
              />
            ))}
          </div>
        )}
      </div>

      {/* ── Lightbox ────────────────────────────────────────────────── */}
      {lightbox !== null && (
        <Lightbox
          photos={photosForSession}
          index={lightbox}
          kept={kept}
          onIndexChange={setLightbox}
          onToggleKeep={(id) => void toggleKeep(id)}
          onClose={() => setLightbox(null)}
        />
      )}

      {/* ── Confirm cull ────────────────────────────────────────────── */}
      {confirmOpen && createPortal(
        <div className="fixed inset-0 z-[2000] bg-black/70 flex items-center justify-center p-6" onClick={() => !committing && setConfirmOpen(false)}>
          <div className="bg-white rounded-lg shadow-2xl max-w-md w-full p-5" onClick={e => e.stopPropagation()}>
            <h3 className="text-lg font-semibold text-slate-900 mb-2">Commit cull?</h3>
            <p className="text-sm text-slate-700 mb-4">
              This will move <strong>{total - keptCount}</strong> unkept photo{total - keptCount === 1 ? '' : 's'} to <code className="text-xs">{activeSession}/.deleted/</code>.
              The <strong>{keptCount}</strong> kept photo{keptCount === 1 ? '' : 's'} will stay. The kept-flag list resets.
            </p>
            <p className="text-xs text-slate-500 mb-4">
              Recoverable until you manually <code>rm -rf .deleted/</code>.
            </p>
            <div className="flex justify-end gap-2">
              <button
                type="button"
                disabled={committing}
                onClick={() => setConfirmOpen(false)}
                className="px-3 py-1.5 text-sm border border-slate-300 hover:bg-slate-50 rounded"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={committing}
                onClick={() => void commitCull()}
                className="px-3 py-1.5 text-sm bg-rose-600 hover:bg-rose-700 text-white rounded font-medium disabled:opacity-50"
              >
                {committing ? 'Culling…' : `Cull ${total - keptCount}`}
              </button>
            </div>
          </div>
        </div>,
        document.body,
      )}

      {/* ── Pull modal ──────────────────────────────────────────────── */}
      {pullOpen && (
        <PullModal onClose={() => { setPullOpen(false); void refetchList() }} />
      )}
    </div>
  )
}

// ─── ThumbTile ──────────────────────────────────────────────────────────
interface ThumbTileProps {
  photo: BurstPhoto
  kept: boolean
  onToggle: () => void
  onOpen: () => void
}

function ThumbTile({ photo, kept, onToggle, onOpen }: ThumbTileProps) {
  const ref = useRef<HTMLDivElement>(null)
  const [visible, setVisible] = useState(false)

  // IntersectionObserver-based lazy load. Once visible, stay loaded so
  // scrolling back doesn't refetch.
  useEffect(() => {
    if (visible || !ref.current) return
    const io = new IntersectionObserver(entries => {
      for (const e of entries) {
        if (e.isIntersecting) { setVisible(true); io.disconnect(); break }
      }
    }, { rootMargin: '400px' })
    io.observe(ref.current)
    return () => io.disconnect()
  }, [visible])

  const tsLabel = new Date(photo.ts).toISOString().slice(11, 19) + 'Z'
  const noGps = !Number.isFinite(photo.lat) || !Number.isFinite(photo.lon)

  return (
    <div
      ref={ref}
      className={
        'relative aspect-square rounded overflow-hidden border-2 cursor-pointer group transition-all ' +
        (kept
          ? 'border-amber-500 ring-2 ring-amber-300 shadow-md'
          : 'border-slate-300 hover:border-indigo-400')
      }
      onClick={onToggle}
      onDoubleClick={onOpen}
      title={`${photo.name}\n${tsLabel}${noGps ? '' : ` · ${photo.lat.toFixed(5)}, ${photo.lon.toFixed(5)}`}\nClick: toggle keep · Double-click: zoom`}
    >
      {visible ? (
        <img
          src={`${API}/${photo.id}/thumb`}
          alt={photo.name}
          className="absolute inset-0 w-full h-full object-cover"
          loading="lazy"
        />
      ) : (
        <div className="absolute inset-0 bg-slate-200 animate-pulse" />
      )}
      {kept && (
        <div className="absolute top-1 right-1 bg-amber-500 text-white text-xs font-bold w-6 h-6 rounded-full flex items-center justify-center shadow">
          ★
        </div>
      )}
      <div className="absolute bottom-0 inset-x-0 bg-gradient-to-t from-black/70 to-transparent text-white text-[10px] font-mono px-1.5 py-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
        {tsLabel}
      </div>
    </div>
  )
}

// ─── Lightbox ───────────────────────────────────────────────────────────
interface LightboxProps {
  photos: BurstPhoto[]
  index: number
  kept: Set<string>
  onIndexChange: (i: number) => void
  onToggleKeep: (id: string) => void
  onClose: () => void
}

interface ExifResponse {
  exif?: Record<string, unknown>
  size_bytes?: number
}

function Lightbox({ photos, index, kept, onIndexChange, onToggleKeep, onClose }: LightboxProps) {
  const photo = photos[index]
  const [exif, setExif] = useState<ExifResponse | null>(null)

  useEffect(() => {
    let cancelled = false
    setExif(null)
    fetch(`${API}/${photo.id}/exif`, { credentials: 'same-origin' })
      .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((body: ExifResponse) => { if (!cancelled) setExif(body) })
      .catch(() => { /* exif is best-effort */ })
    return () => { cancelled = true }
  }, [photo.id])

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
      else if (e.key === 'ArrowRight') onIndexChange(Math.min(index + 1, photos.length - 1))
      else if (e.key === 'ArrowLeft') onIndexChange(Math.max(index - 1, 0))
      else if (e.key === 'k' || e.key === 'K' || e.key === ' ') {
        e.preventDefault()
        onToggleKeep(photo.id)
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [index, photo.id, photos.length, onIndexChange, onToggleKeep, onClose])

  const isKept = kept.has(photo.id)
  const tsIso = new Date(photo.ts).toISOString()
  const exifEntries: Array<[string, string]> = []
  if (exif?.exif) {
    const e = exif.exif as Record<string, unknown>
    const fmt = (v: unknown) => v == null ? '—' : String(v)
    if (e.GPSLatitude != null)        exifEntries.push(['GPS lat',     fmt(e.GPSLatitude)])
    if (e.GPSLongitude != null)       exifEntries.push(['GPS lon',     fmt(e.GPSLongitude)])
    if (e.GPSAltitude != null)        exifEntries.push(['Altitude',    `${fmt(e.GPSAltitude)} m`])
    if (e.GPSSpeed != null)           exifEntries.push(['Speed',       `${fmt(e.GPSSpeed)} ${fmt(e.GPSSpeedRef ?? '')}`])
    if (e.GPSImgDirection != null)    exifEntries.push(['Heading',     `${fmt(e.GPSImgDirection)}° ${fmt(e.GPSImgDirectionRef ?? '')}`])
    if (e.GPSTrack != null)           exifEntries.push(['Track',       `${fmt(e.GPSTrack)}°`])
    if (e.GPSProcessingMethod != null) exifEntries.push(['Fix method', fmt(e.GPSProcessingMethod)])
    if (e.Make != null)               exifEntries.push(['Camera',      `${fmt(e.Make)} ${fmt(e.Model ?? '')}`])
  }

  return createPortal(
    <div
      className="fixed inset-0 z-[2100] bg-black/90 flex flex-col"
      onClick={onClose}
      onWheel={e => e.stopPropagation()}
    >
      <div
        className="flex-1 flex items-center justify-center p-6 min-h-0"
        onClick={e => e.stopPropagation()}
      >
        <img
          src={`${API}/${photo.id}/image`}
          alt={photo.name}
          className="max-w-[95vw] max-h-[80vh] object-contain"
        />
      </div>

      <div
        className="bg-slate-900 text-slate-100 px-6 py-3 border-t border-slate-700 flex items-center gap-4"
        onClick={e => e.stopPropagation()}
      >
        <div className="text-xs flex-1 min-w-0">
          <div className="font-mono truncate">{photo.name}</div>
          <div className="text-slate-400">{tsIso}</div>
          {exifEntries.length > 0 && (
            <div className="mt-1 grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-x-4 gap-y-0.5">
              {exifEntries.map(([k, v]) => (
                <div key={k} className="truncate"><span className="text-slate-500">{k}:</span> {v}</div>
              ))}
            </div>
          )}
        </div>
        <div className="text-xs text-slate-400 whitespace-nowrap">{index + 1} / {photos.length}</div>
        <button
          type="button"
          onClick={() => onIndexChange(Math.max(index - 1, 0))}
          disabled={index === 0}
          className="px-2 py-1 bg-slate-800 hover:bg-slate-700 rounded disabled:opacity-30"
          title="Previous (←)"
        >
          ←
        </button>
        <button
          type="button"
          onClick={() => onIndexChange(Math.min(index + 1, photos.length - 1))}
          disabled={index === photos.length - 1}
          className="px-2 py-1 bg-slate-800 hover:bg-slate-700 rounded disabled:opacity-30"
          title="Next (→)"
        >
          →
        </button>
        <button
          type="button"
          onClick={() => onToggleKeep(photo.id)}
          className={
            'px-3 py-1.5 rounded font-medium text-sm ' +
            (isKept
              ? 'bg-amber-500 hover:bg-amber-600 text-white'
              : 'bg-slate-700 hover:bg-slate-600 text-slate-100')
          }
          title="Toggle keep (K or Space)"
        >
          {isKept ? '★ Kept' : '☆ Keep'}
        </button>
        <button
          type="button"
          onClick={onClose}
          className="px-2 py-1 bg-slate-800 hover:bg-slate-700 rounded"
          title="Close (Esc)"
        >
          ×
        </button>
      </div>
    </div>,
    document.body,
  )
}

// ─── PullModal ──────────────────────────────────────────────────────────
interface PullModalProps {
  onClose: () => void
}

function PullModal({ onClose }: PullModalProps) {
  const [devices, setDevices] = useState<DeviceRow[]>([])
  const [chosen, setChosen] = useState<string | null>(null)
  const [running, setRunning] = useState(false)
  const [lines, setLines] = useState<PullEvent[]>([])
  const [done, setDone] = useState(false)

  useEffect(() => {
    fetch(`${API}/devices`, { credentials: 'same-origin' })
      .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((body: { devices: DeviceRow[] }) => {
        setDevices(body.devices ?? [])
        const ready = body.devices?.find(d => d.state === 'device')
        if (ready) setChosen(ready.serial)
      })
      .catch(() => setDevices([]))
  }, [])

  const runPull = useCallback(async () => {
    if (!chosen) return
    setRunning(true)
    setLines([])
    setDone(false)
    try {
      const r = await fetch(`${API}/pull`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ deviceSerial: chosen }),
      })
      if (!r.ok || !r.body) throw new Error(`HTTP ${r.status}`)
      const reader = r.body.getReader()
      const decoder = new TextDecoder()
      let buf = ''
      // SSE stream reader.
      // eslint-disable-next-line no-constant-condition
      while (true) {
        const { value, done: streamDone } = await reader.read()
        if (streamDone) break
        buf += decoder.decode(value, { stream: true })
        const events = buf.split('\n\n')
        buf = events.pop() ?? ''
        for (const ev of events) {
          const dataLine = ev.split('\n').find(l => l.startsWith('data:'))
          if (!dataLine) continue
          try {
            const parsed = JSON.parse(dataLine.slice(5).trim()) as PullEvent
            setLines(prev => [...prev, parsed])
            if (parsed.stage === 'done' || parsed.stage === 'error') setDone(true)
          } catch { /* ignore parse errors */ }
        }
      }
      setDone(true)
    } catch (e) {
      setLines(prev => [...prev, { stage: 'error', line: (e as Error).message ?? String(e) }])
      setDone(true)
    } finally {
      setRunning(false)
    }
  }, [chosen])

  return createPortal(
    <div className="fixed inset-0 z-[2000] bg-black/70 flex items-center justify-center p-6" onClick={() => !running && onClose()}>
      <div className="bg-white rounded-lg shadow-2xl w-[90vw] max-w-2xl max-h-[90vh] flex flex-col" onClick={e => e.stopPropagation()}>
        <div className="px-5 py-3 border-b border-slate-200 flex items-center gap-3">
          <h3 className="text-lg font-semibold text-slate-900 flex-1">Pull burst photos from device</h3>
          <button
            type="button"
            onClick={onClose}
            disabled={running}
            className="rounded-full w-7 h-7 flex items-center justify-center bg-slate-200 hover:bg-slate-300 text-slate-700 text-lg leading-none disabled:opacity-30"
          >
            ×
          </button>
        </div>

        <div className="px-5 py-3 border-b border-slate-200 flex items-center gap-3 flex-wrap">
          <label className="text-xs uppercase tracking-wider text-slate-500">Device</label>
          {devices.length === 0 ? (
            <span className="text-sm text-slate-500">No adb devices detected.</span>
          ) : (
            <select
              value={chosen ?? ''}
              onChange={e => setChosen(e.target.value || null)}
              className="border border-slate-300 rounded px-2 py-1 text-sm bg-white"
              disabled={running}
            >
              <option value="">— select —</option>
              {devices.map(d => (
                <option key={d.serial} value={d.serial} disabled={d.state !== 'device'}>
                  {d.serial} ({d.state})
                </option>
              ))}
            </select>
          )}
          <div className="flex-1" />
          <button
            type="button"
            onClick={() => void runPull()}
            disabled={!chosen || running}
            className="px-3 py-1.5 text-sm bg-indigo-600 hover:bg-indigo-700 text-white rounded font-medium disabled:bg-slate-300"
          >
            {running ? 'Pulling…' : 'Run pull + organize'}
          </button>
        </div>

        <div className="flex-1 min-h-0 overflow-y-auto bg-slate-900 text-slate-100 px-4 py-3 font-mono text-xs">
          {lines.length === 0 && !running && <div className="text-slate-500">Output will appear here.</div>}
          {lines.map((l, i) => (
            <div key={i} className={
              l.stage === 'error' ? 'text-rose-300'
              : l.stage === 'done' ? 'text-emerald-300'
              : l.stage === 'organize' ? 'text-sky-300'
              : 'text-slate-300'
            }>
              <span className="text-slate-500">[{l.stage}]</span> {l.line ?? ''}
            </div>
          ))}
        </div>

        {done && (
          <div className="px-5 py-3 border-t border-slate-200 flex justify-end">
            <button
              type="button"
              onClick={onClose}
              className="px-3 py-1.5 text-sm bg-slate-700 hover:bg-slate-800 text-white rounded font-medium"
            >
              Close (refresh sessions)
            </button>
          </div>
        )}
      </div>
    </div>,
    document.body,
  )
}
