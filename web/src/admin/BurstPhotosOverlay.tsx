// BurstPhotosOverlay — S229 (cluster + bulk-review modal added S231)
//
// Drop-in overlay for the AdminMap that pins every GPS-burst photo from
// /mnt/sdb-images/LMASalemPictures/ on the map. Photos at the same (lat, lon)
// are collapsed into ONE cluster marker (1962-photo session has 1373 unique
// coords / 638 duplicates). Click a cluster → modal with a thumbnail strip of
// every photo at that coord, each with:
//   - green-check Save → removes the row from this session's review queue
//                        (no disk change; just hides it locally so you can
//                        focus on the undecided photos)
//   - red trash Delete → real disk delete (with one-click confirm)
//
// Backend: cache-proxy/lib/admin-burst-photos.js. Thumbnails come from the
// new /thumb endpoint (sharp, 320-wide, disk-cached under .thumbs/).
//
// Usage (inside AdminMap's <MapContainer>):
//   {showBurstPhotos && <BurstPhotosOverlay />}
import { useCallback, useEffect, useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import { CircleMarker, useMap } from 'react-leaflet'

interface BurstPhoto {
  id: string
  session: string
  name: string
  ts: number
  lat: number
  lon: number
}

interface ListResponse {
  sessions: Array<{ session: string; count: number }>
  photos: BurstPhoto[]
  root?: string
}

interface PhotoCluster {
  key: string        // "lat,lon" (5dp filename precision — already collapses sub-meter neighbours)
  lat: number
  lon: number
  photos: BurstPhoto[]
}

function clusterKey(lat: number, lon: number): string {
  return `${lat.toFixed(5)},${lon.toFixed(5)}`
}

// Disable every Leaflet interaction while a modal is open, then restore.
// Belt-and-suspenders alongside the body-portal: even with the modal lifted
// out of the Leaflet DOM subtree, native wheel listeners on .leaflet-container
// can fire on the underlying area uncovered by the modal panel itself.
function useFreezeMapWhile(active: boolean) {
  const map = useMap()
  useEffect(() => {
    if (!active) return
    const interactions = [
      map.dragging,
      map.touchZoom,
      map.doubleClickZoom,
      map.scrollWheelZoom,
      map.boxZoom,
      map.keyboard,
    ]
    const prev = interactions.map(i => i?.enabled() ?? false)
    interactions.forEach(i => i?.disable())
    return () => {
      interactions.forEach((i, idx) => { if (prev[idx]) i?.enable() })
    }
  }, [active, map])
}

export function BurstPhotosOverlay() {
  const [photos, setPhotos] = useState<BurstPhoto[]>([])
  const [activeKey, setActiveKey] = useState<string | null>(null)
  // Per-session "saved" set — photos the operator hit the green check on.
  // No persistence; a page reload starts the queue fresh.
  const [savedIds, setSavedIds] = useState<Set<string>>(new Set())

  const refetch = useCallback(() => {
    fetch('/api/admin/burst-photos', { credentials: 'same-origin' })
      .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((body: ListResponse) => setPhotos(body.photos ?? []))
      .catch(() => setPhotos([]))
  }, [])

  useEffect(() => {
    refetch()
  }, [refetch])

  const clusters: PhotoCluster[] = useMemo(() => {
    const map = new Map<string, PhotoCluster>()
    for (const p of photos) {
      const key = clusterKey(p.lat, p.lon)
      const existing = map.get(key)
      if (existing) {
        existing.photos.push(p)
      } else {
        map.set(key, { key, lat: p.lat, lon: p.lon, photos: [p] })
      }
    }
    for (const c of map.values()) c.photos.sort((a, b) => a.ts - b.ts)
    return Array.from(map.values())
  }, [photos])

  const activeCluster = activeKey
    ? clusters.find(c => c.key === activeKey) ?? null
    : null

  useFreezeMapWhile(activeCluster !== null)

  // Esc closes modal.
  useEffect(() => {
    if (!activeKey) return
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setActiveKey(null)
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [activeKey])

  const handleDelete = useCallback((id: string) => {
    fetch(`/api/admin/burst-photos/${id}`, {
      method: 'DELETE',
      credentials: 'same-origin',
    })
      .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then(() => {
        setPhotos(prev => prev.filter(p => p.id !== id))
        setSavedIds(prev => {
          if (!prev.has(id)) return prev
          const next = new Set(prev)
          next.delete(id)
          return next
        })
      })
      .catch((e: unknown) => alert(`Delete failed: ${(e as Error).message ?? e}`))
  }, [])

  // Toggle: clicking Save on an already-saved photo unmarks it (in case of
  // mis-click). No persistence — saved state is per-page-load.
  const handleToggleSave = useCallback((id: string) => {
    setSavedIds(prev => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }, [])

  return (
    <>
      {clusters.map(c => {
        const n = c.photos.length
        // radius scales gently with cluster size: 1→4px, 2→5, 5→6, 20→8, 100→10
        const radius = Math.min(10, 4 + Math.log10(n) * 3)
        return (
          <CircleMarker
            key={c.key}
            center={[c.lat, c.lon]}
            radius={radius}
            pathOptions={{
              color: '#7f1d1d',
              weight: 1,
              fillColor: '#ef4444',
              fillOpacity: 0.85,
            }}
            eventHandlers={{ click: () => setActiveKey(c.key) }}
          />
        )
      })}
      {activeCluster && (
        <BurstPhotoClusterModal
          cluster={activeCluster}
          savedIds={savedIds}
          onToggleSave={handleToggleSave}
          onDelete={handleDelete}
          onClose={() => setActiveKey(null)}
        />
      )}
    </>
  )
}

interface BurstPhotoClusterModalProps {
  cluster: PhotoCluster
  savedIds: Set<string>
  onToggleSave: (id: string) => void
  onDelete: (id: string) => void
  onClose: () => void
}

function BurstPhotoClusterModal({
  cluster,
  savedIds,
  onToggleSave,
  onDelete,
  onClose,
}: BurstPhotoClusterModalProps) {
  // Show every surviving photo (saved + undecided). Deleted ones are removed
  // from cluster.photos by the parent when the DELETE response returns.
  const total = cluster.photos.length
  const savedCount = cluster.photos.filter(p => savedIds.has(p.id)).length
  const undecided = total - savedCount

  // If everything's been triaged (saved or deleted), auto-close.
  useEffect(() => {
    if (cluster.photos.length === 0) onClose()
  }, [cluster.photos.length, onClose])

  // Stop wheel/touch from ever bubbling up to the Leaflet map. The portal
  // already lifts us out of the .leaflet-container DOM subtree, but capturing
  // here defends against any future repositioning too.
  const swallow = (e: React.SyntheticEvent) => e.stopPropagation()

  return createPortal(
    <div
      className="fixed inset-0 z-[2000] bg-black/80 flex items-center justify-center p-6"
      onClick={onClose}
      onWheel={swallow}
      onTouchMove={swallow}
      onContextMenu={swallow}
    >
      <div
        className="relative bg-white rounded-lg shadow-2xl w-[95vw] max-w-5xl max-h-[95vh] flex flex-col"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center gap-3 px-4 py-2 border-b border-slate-200">
          <div className="flex-1 min-w-0">
            <div className="text-sm font-medium text-slate-900">
              {total} photo{total === 1 ? '' : 's'} at {cluster.lat.toFixed(5)}, {cluster.lon.toFixed(5)}
            </div>
            <div className="text-xs text-slate-500">
              {undecided} to review · {savedCount} kept
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-full w-7 h-7 flex items-center justify-center bg-slate-200 hover:bg-slate-300 text-slate-700 text-lg leading-none"
            title="Close (Esc)"
          >
            ×
          </button>
        </div>

        <div className="flex-1 min-h-0 overflow-y-auto p-3 bg-slate-50">
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-3">
            {cluster.photos.map(p => (
              <ClusterPhotoCard
                key={p.id}
                photo={p}
                saved={savedIds.has(p.id)}
                onToggleSave={() => onToggleSave(p.id)}
                onDelete={() => onDelete(p.id)}
              />
            ))}
          </div>
        </div>
      </div>
    </div>,
    document.body,
  )
}

interface ClusterPhotoCardProps {
  photo: BurstPhoto
  saved: boolean
  onToggleSave: () => void
  onDelete: () => void
}

function ClusterPhotoCard({ photo, saved, onToggleSave, onDelete }: ClusterPhotoCardProps) {
  const [zoomed, setZoomed] = useState(false)
  const thumbUrl = `/api/admin/burst-photos/${photo.id}/thumb`
  const fullUrl  = `/api/admin/burst-photos/${photo.id}/image`
  const tsLabel  = new Date(photo.ts).toISOString().slice(11, 19) + 'Z'

  return (
    <>
      <div
        className={
          'rounded shadow-sm overflow-hidden flex flex-col border-2 transition-colors ' +
          (saved ? 'border-emerald-500 bg-emerald-50' : 'border-slate-200 bg-white')
        }
      >
        <button
          type="button"
          onClick={() => setZoomed(true)}
          className="relative block bg-slate-900 aspect-square overflow-hidden"
          title="Click to zoom"
        >
          <img
            src={thumbUrl}
            alt={photo.name}
            loading="lazy"
            className="w-full h-full object-cover hover:opacity-90 transition-opacity"
          />
          {saved && (
            <div className="absolute top-1 right-1 bg-emerald-600 text-white text-[11px] font-semibold px-1.5 py-0.5 rounded shadow">
              ✓ Saved
            </div>
          )}
        </button>
        <div className="px-2 py-1 text-[11px] text-slate-600 font-mono truncate" title={photo.name}>
          {tsLabel}
        </div>
        <div className="flex border-t border-slate-200">
          <button
            type="button"
            onClick={onToggleSave}
            className={
              'flex-1 py-1.5 text-sm flex items-center justify-center gap-1 font-medium border-r border-slate-200 ' +
              (saved
                ? 'bg-emerald-600 hover:bg-emerald-700 text-white'
                : 'bg-emerald-50 hover:bg-emerald-100 text-emerald-800')
            }
            title={saved ? 'Click to unsave' : 'Mark this photo as saved (kept)'}
          >
            <span aria-hidden>✓</span> {saved ? 'Saved' : 'Save'}
          </button>
          <button
            type="button"
            onClick={onDelete}
            className="flex-1 py-1.5 text-sm flex items-center justify-center gap-1 bg-rose-50 hover:bg-rose-100 text-rose-800 font-medium"
            title="Move to .deleted/ (recoverable until you rm -rf .deleted/)"
          >
            <span aria-hidden>🗑</span> Delete
          </button>
        </div>
      </div>

      {zoomed && createPortal(
        <div
          className="fixed inset-0 z-[2100] bg-black/90 flex items-center justify-center p-4"
          onClick={() => setZoomed(false)}
          onWheel={(e) => e.stopPropagation()}
          onTouchMove={(e) => e.stopPropagation()}
        >
          <img
            src={fullUrl}
            alt={photo.name}
            className="max-w-[95vw] max-h-[95vh] object-contain"
            onClick={e => e.stopPropagation()}
          />
          <button
            type="button"
            onClick={() => setZoomed(false)}
            className="absolute top-3 right-3 rounded-full w-9 h-9 flex items-center justify-center bg-white/90 hover:bg-white text-slate-800 text-xl leading-none"
            title="Close (Esc)"
          >
            ×
          </button>
        </div>,
        document.body,
      )}
    </>
  )
}
