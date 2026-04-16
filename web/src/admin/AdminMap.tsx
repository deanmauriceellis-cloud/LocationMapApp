// AdminMap — Phase 9U (unified salem_pois table)
//
// Center-pane Leaflet map for the Salem POI admin tool. Renders all POIs from
// the unified salem_pois table as a single clustered layer with category-based
// colors. Click a marker → emits a selection; drag a marker → confirm modal →
// POSTs to /admin/salem/pois/:id/move.

import { useEffect, useMemo, useRef, useState, useCallback } from 'react'
import { MapContainer, TileLayer, useMap } from 'react-leaflet'
import L from 'leaflet'
import 'leaflet.markercluster'
import type { PoiRow, PoiSelection } from './PoiTree'

// ─── Constants ───────────────────────────────────────────────────────────────

const SALEM_CENTER: [number, number] = [42.5197, -70.8967]
const INITIAL_ZOOM = 14

// Category → color. The top categories get distinct colors; everything else
// falls back to a neutral grey. This replaces the old kind-based coloring.
const CATEGORY_COLORS: Record<string, string> = {
  FOOD_DRINK:          '#BF360C',
  SHOPPING:            '#2563eb',
  HISTORICAL_BUILDINGS: '#8D6E63',
  ENTERTAINMENT:       '#7c3aed',
  PARKS_REC:           '#059669',
  WITCH_SHOP:          '#9333ea',
  CIVIC:               '#64748b',
  LODGING:             '#0891b2',
  PSYCHIC:             '#a855f7',
  TOUR_COMPANIES:      '#FF6F00',
  HEALTHCARE:          '#0d9488',
  WORSHIP:             '#78716c',
  EDUCATION:           '#ca8a04',
}
const DEFAULT_COLOR = '#94a3b8' // slate-400

function categoryColor(category: string | undefined): string {
  return (category && CATEGORY_COLORS[category]) || DEFAULT_COLOR
}

const TILE_URL = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
const TILE_ATTR = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'

// ─── Marker icon factory ─────────────────────────────────────────────────────

const iconCache = new Map<string, L.DivIcon>()

function poiIcon(color: string, selected: boolean, hidden: boolean = false): L.DivIcon {
  const key = `${color}|${selected ? 'sel' : 'norm'}|${hidden ? 'hid' : 'vis'}`
  let icon = iconCache.get(key)
  if (icon) return icon
  const size = selected ? 18 : 12
  const stroke = selected ? '#facc15' : '#ffffff'
  const strokeW = selected ? 3 : 1.5
  const opacity = hidden ? 0.35 : 1
  const html = `
    <svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" xmlns="http://www.w3.org/2000/svg" style="opacity:${opacity}">
      <circle cx="${size / 2}" cy="${size / 2}" r="${size / 2 - strokeW / 2}"
              fill="${color}" stroke="${stroke}" stroke-width="${strokeW}"/>
    </svg>`
  icon = L.divIcon({
    html,
    className: 'admin-poi-marker',
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  })
  iconCache.set(key, icon)
  return icon
}

// ─── Types ───────────────────────────────────────────────────────────────────

export interface PendingMove {
  poi: PoiRow
  from: { lat: number; lng: number }
  to: { lat: number; lng: number }
  marker: L.Marker
}

interface AdminMapProps {
  pois: PoiRow[] | null
  selectedPoi: PoiSelection | null
  onPoiSelect: (selection: PoiSelection) => void
  onPoiMoved: (id: string, lat: number, lng: number) => void
}

// ─── Marker layer ────────────────────────────────────────────────────────────

interface MarkerLayerProps {
  pois: PoiRow[]
  selectedKey: string | null
  onPoiSelect: (selection: PoiSelection) => void
  onDragEnd: (move: PendingMove) => void
}

function MarkerLayer({
  pois,
  selectedKey,
  onPoiSelect,
  onDragEnd,
}: MarkerLayerProps) {
  const map = useMap()
  const clusterGroupRef = useRef<L.MarkerClusterGroup | null>(null)
  const markersByKeyRef = useRef<Map<string, { marker: L.Marker; color: string; hidden: boolean }>>(new Map())

  useEffect(() => {
    if (clusterGroupRef.current) {
      map.removeLayer(clusterGroupRef.current)
      clusterGroupRef.current = null
      markersByKeyRef.current.clear()
    }

    const cluster = L.markerClusterGroup({
      showCoverageOnHover: false,
      spiderfyOnMaxZoom: true,
      disableClusteringAtZoom: 18,
      maxClusterRadius: 50,
    }) as L.MarkerClusterGroup

    const allMarkers: L.Marker[] = []

    for (const poi of pois) {
      if (poi.deleted_at) continue
      if (typeof poi.lat !== 'number' || typeof poi.lng !== 'number') continue
      if (!Number.isFinite(poi.lat) || !Number.isFinite(poi.lng)) continue

      const key = poi.id
      const isSelected = key === selectedKey
      const color = categoryColor(poi.category as string)
      const isHidden = poi.default_visible === false

      const marker = L.marker([poi.lat, poi.lng], {
        icon: poiIcon(color, isSelected, isHidden),
        draggable: true,
        autoPan: true,
        title: `${poi.category || '—'}: ${poi.name}`,
      })

      marker.on('click', () => {
        onPoiSelect({ poi })
      })

      let dragOrigin = { lat: poi.lat, lng: poi.lng }
      marker.on('dragstart', () => {
        const ll = marker.getLatLng()
        dragOrigin = { lat: ll.lat, lng: ll.lng }
      })
      marker.on('dragend', () => {
        const ll = marker.getLatLng()
        if (ll.lat === dragOrigin.lat && ll.lng === dragOrigin.lng) return
        onDragEnd({
          poi,
          from: dragOrigin,
          to: { lat: ll.lat, lng: ll.lng },
          marker,
        })
      })

      allMarkers.push(marker)
      markersByKeyRef.current.set(key, { marker, color, hidden: isHidden })
    }

    cluster.addLayers(allMarkers)
    map.addLayer(cluster)
    clusterGroupRef.current = cluster

    return () => {
      if (clusterGroupRef.current) {
        map.removeLayer(clusterGroupRef.current)
        clusterGroupRef.current = null
        markersByKeyRef.current.clear()
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pois, map])

  const previousSelectedKeyRef = useRef<string | null>(null)
  useEffect(() => {
    const prev = previousSelectedKeyRef.current
    if (prev && prev !== selectedKey) {
      const entry = markersByKeyRef.current.get(prev)
      if (entry) entry.marker.setIcon(poiIcon(entry.color, false, entry.hidden))
    }
    if (selectedKey) {
      const entry = markersByKeyRef.current.get(selectedKey)
      if (entry) entry.marker.setIcon(poiIcon(entry.color, true, entry.hidden))
    }
    previousSelectedKeyRef.current = selectedKey
  }, [selectedKey])

  return null
}

// ─── Fly-to controller ──────────────────────────────────────────────────────

interface FlyToProps {
  selectedPoi: PoiSelection | null
}

function FlyToSelected({ selectedPoi }: FlyToProps) {
  const map = useMap()
  const lastIdRef = useRef<string | null>(null)
  useEffect(() => {
    if (!selectedPoi) return
    const key = selectedPoi.poi.id
    if (key === lastIdRef.current) return
    lastIdRef.current = key
    const { lat, lng } = selectedPoi.poi
    if (typeof lat !== 'number' || typeof lng !== 'number') return
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) return
    const targetZoom = Math.max(map.getZoom(), 17)
    map.flyTo([lat, lng], targetZoom, { duration: 0.6 })
  }, [selectedPoi, map])
  return null
}

// ─── Move-confirm modal ─────────────────────────────────────────────────────

function metersBetween(a: { lat: number; lng: number }, b: { lat: number; lng: number }): number {
  const R = 6371000
  const toRad = (d: number) => (d * Math.PI) / 180
  const dLat = toRad(b.lat - a.lat)
  const dLng = toRad(b.lng - a.lng)
  const x =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2
  return R * 2 * Math.asin(Math.sqrt(x))
}

interface MoveConfirmProps {
  pending: PendingMove
  onConfirm: () => void
  onCancel: () => void
  busy: boolean
  error: string | null
}

function MoveConfirm({ pending, onConfirm, onCancel, busy, error }: MoveConfirmProps) {
  const distance = metersBetween(pending.from, pending.to)
  return (
    <div className="absolute inset-0 z-[500] flex items-center justify-center bg-black/30">
      <div className="bg-white rounded shadow-lg w-96 max-w-[90vw] p-4 text-sm text-slate-800">
        <h2 className="text-base font-semibold mb-2">Confirm POI move</h2>
        <p className="text-xs text-slate-600 mb-3">
          <span className="font-medium text-slate-800">{pending.poi.name}</span>{' '}
          (<span className="font-mono text-[10px]">{pending.poi.id}</span>)
        </p>
        <div className="bg-slate-50 border border-slate-200 rounded p-2 text-xs font-mono">
          <div className="flex justify-between">
            <span className="text-slate-500">From:</span>
            <span>
              {pending.from.lat.toFixed(6)}, {pending.from.lng.toFixed(6)}
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">To:</span>
            <span>
              {pending.to.lat.toFixed(6)}, {pending.to.lng.toFixed(6)}
            </span>
          </div>
          <div className="flex justify-between mt-1 pt-1 border-t border-slate-200">
            <span className="text-slate-500">Distance:</span>
            <span>{distance.toFixed(1)} m</span>
          </div>
        </div>
        {error && (
          <div className="mt-3 p-2 text-xs text-rose-700 bg-rose-50 border border-rose-200 rounded font-mono whitespace-pre-wrap break-words">
            {error}
          </div>
        )}
        <div className="mt-4 flex justify-end gap-2">
          <button
            type="button"
            onClick={onCancel}
            disabled={busy}
            className="px-3 py-1 text-sm rounded bg-slate-200 hover:bg-slate-300 disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={busy}
            className="px-3 py-1 text-sm rounded bg-emerald-600 text-white hover:bg-emerald-500 disabled:opacity-50"
          >
            {busy ? 'Saving…' : 'Confirm move'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Legend ──────────────────────────────────────────────────────────────────

function Legend({ categoryCounts }: { categoryCounts: [string, number][] }) {
  return (
    <div className="absolute top-2 right-2 z-[100] bg-white/95 border border-slate-300 rounded shadow px-3 py-2 text-xs max-h-80 overflow-y-auto">
      <div className="font-semibold text-slate-700 mb-1">Legend</div>
      {categoryCounts.map(([cat, count]) => (
        <div key={cat} className="flex items-center gap-2">
          <span
            className="inline-block w-3 h-3 rounded-full border border-white flex-shrink-0"
            style={{ background: categoryColor(cat) }}
          />
          <span className="text-slate-700 truncate">{cat.replace(/_/g, ' ')}</span>
          <span className="ml-auto tabular-nums text-slate-500">{count}</span>
        </div>
      ))}
    </div>
  )
}

// ─── Top-level component ────────────────────────────────────────────────────

export function AdminMap({
  pois,
  selectedPoi,
  onPoiSelect,
  onPoiMoved,
}: AdminMapProps) {
  const [pending, setPending] = useState<PendingMove | null>(null)
  const [moveBusy, setMoveBusy] = useState(false)
  const [moveError, setMoveError] = useState<string | null>(null)

  const selectedKey = selectedPoi?.poi.id ?? null

  const categoryCounts = useMemo(() => {
    if (!pois) return []
    const counts = new Map<string, number>()
    for (const p of pois) {
      if (p.deleted_at) continue
      const cat = (p.category as string) || '(uncategorized)'
      counts.set(cat, (counts.get(cat) || 0) + 1)
    }
    return [...counts.entries()].sort((a, b) => b[1] - a[1])
  }, [pois])

  const handleDragEnd = useCallback((move: PendingMove) => {
    setPending(move)
    setMoveError(null)
  }, [])

  const handleCancel = useCallback(() => {
    if (pending) {
      pending.marker.setLatLng([pending.from.lat, pending.from.lng])
    }
    setPending(null)
    setMoveError(null)
  }, [pending])

  const handleConfirm = useCallback(async () => {
    if (!pending) return
    setMoveBusy(true)
    setMoveError(null)
    try {
      const res = await fetch(
        `/api/admin/salem/pois/${encodeURIComponent(pending.poi.id)}/move`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          credentials: 'same-origin',
          body: JSON.stringify({ lat: pending.to.lat, lng: pending.to.lng }),
        },
      )
      if (!res.ok) {
        let msg = `${res.status} ${res.statusText}`
        try {
          const body = await res.json()
          if (body?.error) msg = `${res.status} ${body.error}`
        } catch {
          // body wasn't JSON
        }
        throw new Error(msg)
      }
      onPoiMoved(pending.poi.id, pending.to.lat, pending.to.lng)
      setPending(null)
    } catch (e) {
      pending.marker.setLatLng([pending.from.lat, pending.from.lng])
      setMoveError(e instanceof Error ? e.message : String(e))
    } finally {
      setMoveBusy(false)
    }
  }, [pending, onPoiMoved])

  if (!pois) {
    return (
      <div className="absolute inset-0 flex items-center justify-center text-slate-500">
        <p className="text-sm italic">Loading POI map…</p>
      </div>
    )
  }

  return (
    <div className="absolute inset-0">
      <MapContainer
        center={SALEM_CENTER}
        zoom={INITIAL_ZOOM}
        maxZoom={22}
        className="h-full w-full"
        zoomControl={true}
      >
        <TileLayer
          url={TILE_URL}
          attribution={TILE_ATTR}
          maxZoom={22}
          maxNativeZoom={19}
        />
        <MarkerLayer
          pois={pois}
          selectedKey={selectedKey}
          onPoiSelect={onPoiSelect}
          onDragEnd={handleDragEnd}
        />
        <FlyToSelected selectedPoi={selectedPoi} />
      </MapContainer>
      <Legend categoryCounts={categoryCounts} />
      {pending && (
        <MoveConfirm
          pending={pending}
          busy={moveBusy}
          error={moveError}
          onConfirm={handleConfirm}
          onCancel={handleCancel}
        />
      )}
    </div>
  )
}
