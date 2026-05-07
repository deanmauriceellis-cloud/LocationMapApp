// BurstPhotosOverlay — S229
//
// Drop-in overlay for the AdminMap that pins every GPS-burst photo from
// /mnt/sdb-images/LMASalemPictures/ on the map. Click a pin → modal with
// the image + parsed EXIF + Delete (confirm-before-deleting). Used by the
// operator to spot-check POI / path alignment by overlaying photo positions
// on top of the existing POI layer.
//
// Backend: cache-proxy/lib/admin-burst-photos.js.
//
// Usage (inside AdminMap's <MapContainer>):
//   {showBurstPhotos && <BurstPhotosOverlay />}
//
// The overlay owns its own fetch + state + modal. Toggling `enabled` off
// (by un-rendering it) cancels everything via React's normal teardown.
import { useCallback, useEffect, useState } from 'react'
import { CircleMarker } from 'react-leaflet'

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

interface ExifResponse {
  id: string
  session: string
  name: string
  size_bytes: number
  mtime: string
  exif: Record<string, unknown>
}

export function BurstPhotosOverlay() {
  const [photos, setPhotos] = useState<BurstPhoto[]>([])
  const [activeId, setActiveId] = useState<string | null>(null)
  const [exif, setExif] = useState<ExifResponse | null>(null)
  const [exifLoading, setExifLoading] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const refetch = useCallback(() => {
    fetch('/api/admin/burst-photos', { credentials: 'same-origin' })
      .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((body: ListResponse) => setPhotos(body.photos ?? []))
      .catch(() => setPhotos([]))
  }, [])

  useEffect(() => {
    refetch()
  }, [refetch])

  // Fetch EXIF when a pin is clicked.
  useEffect(() => {
    if (!activeId) {
      setExif(null)
      return
    }
    setExifLoading(true)
    fetch(`/api/admin/burst-photos/${activeId}/exif`, { credentials: 'same-origin' })
      .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then((body: ExifResponse) => setExif(body))
      .catch(() => setExif(null))
      .finally(() => setExifLoading(false))
  }, [activeId])

  // Esc closes modal.
  useEffect(() => {
    if (!activeId) return
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setActiveId(null)
        setConfirmDelete(false)
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [activeId])

  const handleDelete = useCallback(() => {
    if (!activeId) return
    fetch(`/api/admin/burst-photos/${activeId}`, {
      method: 'DELETE',
      credentials: 'same-origin',
    })
      .then(r => r.ok ? r.json() : Promise.reject(new Error(`HTTP ${r.status}`)))
      .then(() => {
        setActiveId(null)
        setConfirmDelete(false)
        refetch()
      })
      .catch((e: unknown) => alert(`Delete failed: ${(e as Error).message ?? e}`))
  }, [activeId, refetch])

  return (
    <>
      {photos.map(p => (
        <CircleMarker
          key={p.id}
          center={[p.lat, p.lon]}
          radius={5}
          pathOptions={{
            color: '#dc2626',
            weight: 1,
            fillColor: '#ef4444',
            fillOpacity: 0.85,
          }}
          eventHandlers={{
            click: () => {
              setActiveId(p.id)
              setConfirmDelete(false)
            },
          }}
        />
      ))}
      {activeId && (
        <BurstPhotoModal
          id={activeId}
          exif={exif}
          exifLoading={exifLoading}
          confirmDelete={confirmDelete}
          onRequestDelete={() => setConfirmDelete(true)}
          onCancelDelete={() => setConfirmDelete(false)}
          onConfirmDelete={handleDelete}
          onClose={() => {
            setActiveId(null)
            setConfirmDelete(false)
          }}
        />
      )}
    </>
  )
}

interface BurstPhotoModalProps {
  id: string
  exif: ExifResponse | null
  exifLoading: boolean
  confirmDelete: boolean
  onRequestDelete: () => void
  onCancelDelete: () => void
  onConfirmDelete: () => void
  onClose: () => void
}

function BurstPhotoModal({
  id,
  exif,
  exifLoading,
  confirmDelete,
  onRequestDelete,
  onCancelDelete,
  onConfirmDelete,
  onClose,
}: BurstPhotoModalProps) {
  const imageUrl = `/api/admin/burst-photos/${id}/image`

  return (
    <div
      className="fixed inset-0 z-[2000] bg-black/80 flex items-center justify-center p-6"
      onClick={onClose}
    >
      <div
        className="relative bg-white rounded-lg shadow-2xl max-w-[95vw] max-h-[95vh] flex flex-col"
        onClick={e => e.stopPropagation()}
      >
        <div className="flex items-center gap-3 px-4 py-2 border-b border-slate-200">
          <div className="flex-1 min-w-0">
            <div className="text-sm font-medium text-slate-900 truncate" title={exif?.name ?? ''}>
              {exif?.name ?? '…'}
            </div>
            <div className="text-xs text-slate-500 truncate" title={exif?.session ?? ''}>
              {exif?.session ?? ''}
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

        <div className="flex-1 min-h-0 overflow-auto bg-slate-900 flex items-center justify-center">
          <img
            src={imageUrl}
            alt={exif?.name ?? ''}
            className="max-w-full max-h-[70vh] object-contain"
          />
        </div>

        <div className="border-t border-slate-200 bg-slate-50 px-4 py-3 max-h-[28vh] overflow-y-auto">
          <div className="flex items-start gap-3">
            <div className="flex-1 min-w-0">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-600 mb-1">
                EXIF
              </div>
              {exifLoading ? (
                <div className="text-xs text-slate-500">Loading…</div>
              ) : exif ? (
                <ExifTable exif={exif} />
              ) : (
                <div className="text-xs text-rose-700">EXIF unavailable</div>
              )}
            </div>
            <div className="flex flex-col gap-2 shrink-0">
              {!confirmDelete ? (
                <button
                  type="button"
                  onClick={onRequestDelete}
                  className="px-3 py-1.5 text-sm rounded bg-rose-600 hover:bg-rose-700 text-white"
                >
                  Delete
                </button>
              ) : (
                <div className="flex flex-col gap-2 p-2 rounded border border-rose-300 bg-rose-50">
                  <div className="text-xs text-rose-900 font-medium">
                    Delete this photo?
                  </div>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={onConfirmDelete}
                      className="flex-1 px-2 py-1 text-xs rounded bg-rose-600 hover:bg-rose-700 text-white"
                    >
                      Yes, delete
                    </button>
                    <button
                      type="button"
                      onClick={onCancelDelete}
                      className="flex-1 px-2 py-1 text-xs rounded bg-slate-200 hover:bg-slate-300 text-slate-800"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}

function ExifTable({ exif }: { exif: ExifResponse }) {
  const e = exif.exif
  const rows: [string, string][] = []
  const push = (label: string, val: unknown) => {
    if (val == null || val === '') return
    let s: string
    if (val instanceof Date) s = val.toISOString()
    else if (typeof val === 'object') s = JSON.stringify(val)
    else s = String(val)
    rows.push([label, s])
  }
  push('When', e.DateTimeOriginal ?? e.ModifyDate)
  push('Lat',  e.latitude ?? e.GPSLatitude)
  push('Lon',  e.longitude ?? e.GPSLongitude)
  push('Alt (m)',     e.GPSAltitude)
  push('Speed (m/s)', e.GPSSpeed)
  push('Track (°)',   e.GPSTrack)
  push('Heading (°)', e.GPSImgDirection)
  push('Camera',  [e.Make, e.Model].filter(Boolean).join(' '))
  push('Software', e.Software)
  push('Flash',   e.Flash)
  push('ISO',     e.ISO)
  push('Exposure (s)', e.ExposureTime)
  push('Aperture',     e.FNumber ? `f/${e.FNumber}` : null)
  push('Image',   e.ExifImageWidth && e.ExifImageHeight ? `${e.ExifImageWidth}×${e.ExifImageHeight}` : null)
  push('Size',    `${(exif.size_bytes / 1024).toFixed(0)} KB`)
  push('UserComment', typeof e.userComment === 'string' ? e.userComment : null)

  return (
    <table className="text-xs w-full">
      <tbody>
        {rows.map(([k, v]) => (
          <tr key={k} className="border-b border-slate-200/60 last:border-0">
            <td className="py-0.5 pr-3 text-slate-500 font-medium align-top w-28">{k}</td>
            <td className="py-0.5 text-slate-800 break-all">{v}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
