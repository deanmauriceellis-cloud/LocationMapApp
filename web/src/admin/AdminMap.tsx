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
import type { TourStop, TourSummary } from './tourTypes'

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

// Tile provider choices (S160). Salem-Custom / Mapnik / Esri-WorldImagery come
// out of tools/tile-bake/dist/salem_tiles.sqlite via cache-proxy's
// /admin/tiles/:provider/:z/:x/:y endpoint — matches what the phone app sees.
// OSM is kept as a fallback for zooming out past the bake coverage.
interface TileProvider {
  id: string
  label: string
  url: string
  attribution: string
  maxNativeZoom: number
}

const TILE_PROVIDERS: TileProvider[] = [
  {
    id: 'Salem-Custom',
    label: 'Witchy (Salem-Custom)',
    url: '/api/admin/tiles/Salem-Custom/{z}/{x}/{y}',
    attribution: 'Bundled Witchy tiles — OSM + MassGIS | planetiler + tippecanoe + MapLibre-GL-Native',
    maxNativeZoom: 19,
  },
  {
    id: 'Mapnik',
    label: 'OSM Mapnik (bundled)',
    url: '/api/admin/tiles/Mapnik/{z}/{x}/{y}',
    attribution: 'Bundled OSM Mapnik tiles &copy; OpenStreetMap contributors',
    maxNativeZoom: 19,
  },
  {
    id: 'Esri-WorldImagery',
    label: 'Esri Satellite (bundled)',
    url: '/api/admin/tiles/Esri-WorldImagery/{z}/{x}/{y}',
    attribution: 'Bundled Esri World Imagery — Tiles &copy; Esri',
    maxNativeZoom: 19,
  },
  {
    id: 'OSM',
    label: 'OSM online (zoom out)',
    url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    maxNativeZoom: 19,
  },
]

const TILE_PROVIDER_STORAGE_KEY = 'lma-admin-tile-provider'

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
  /** When set, only POIs with matching category render on the map. */
  categoryFilter?: string | null
  onClearCategoryFilter?: () => void
  /** Tour-mode props (S174). When activeTour is set, the tour-stop layer
   *  renders numbered draggable waypoints over the basemap. */
  activeTour?: TourSummary | null
  tourStops?: TourStop[] | null
  onStopMoved?: (stopId: number, lat: number, lng: number) => void
  /** Add-stop mode: 'free' = next map click creates a free waypoint,
   *  'poi' = next POI marker click adds that POI as a stop. */
  addStopMode?: 'none' | 'free' | 'poi'
  onMapClickAddFree?: (lat: number, lng: number) => void
  onPickPoiForStop?: (poi: PoiRow) => void
}

export interface PendingStopMove {
  stop: TourStop
  label: string
  from: { lat: number; lng: number }
  to: { lat: number; lng: number }
  marker: L.Marker
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

// ─── Parcel click hit-test (S163, invisible) ───────────────────────────────
//
// Any POI with a populated `building_footprint_geojson` is hit-testable.
// Hidden POIs (`default_visible=false`) from the MassGIS MHC import have
// footprints but no visible marker — this lets the operator click "on a
// parcel" (via the raster Witchy tile rendering) and open the hidden POI's
// editor. Visible POIs also pick up polygon-click as a second way to select.
//
// Nothing is rendered on the map. Uses the existing `pois` array already
// loaded by AdminLayout, so no additional fetch.

interface FootprintIndexEntry {
  poi: PoiRow
  minLat: number
  maxLat: number
  minLng: number
  maxLng: number
  area: number // bbox area — used to pick the tightest match when nested
  polygons: Array<Array<Array<[number, number]>>> // [poly][ring][vertex] = [lng, lat]
}

function pointInRing(lng: number, lat: number, ring: Array<[number, number]>): boolean {
  // Classic ray-casting even-odd rule.
  let inside = false
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i]
    const [xj, yj] = ring[j]
    const intersect = yi > lat !== yj > lat && lng < ((xj - xi) * (lat - yi)) / (yj - yi) + xi
    if (intersect) inside = !inside
  }
  return inside
}

function pointInPolygons(lng: number, lat: number, polys: FootprintIndexEntry['polygons']): boolean {
  for (const poly of polys) {
    if (poly.length === 0) continue
    const outer = poly[0]
    if (!pointInRing(lng, lat, outer)) continue
    // Subtract holes.
    let inHole = false
    for (let h = 1; h < poly.length; h++) {
      if (pointInRing(lng, lat, poly[h])) {
        inHole = true
        break
      }
    }
    if (!inHole) return true
  }
  return false
}

function buildFootprintIndex(pois: PoiRow[]): FootprintIndexEntry[] {
  const index: FootprintIndexEntry[] = []
  for (const poi of pois) {
    if (poi.deleted_at) continue
    const raw = poi.building_footprint_geojson
    if (typeof raw !== 'string' || !raw) continue
    let geom: { type: string; coordinates: unknown }
    try {
      geom = JSON.parse(raw) as { type: string; coordinates: unknown }
    } catch {
      continue
    }
    let polygons: FootprintIndexEntry['polygons']
    if (geom.type === 'Polygon') {
      polygons = [geom.coordinates as Array<Array<[number, number]>>]
    } else if (geom.type === 'MultiPolygon') {
      polygons = geom.coordinates as Array<Array<Array<[number, number]>>>
    } else {
      continue
    }
    let minLat = Infinity
    let maxLat = -Infinity
    let minLng = Infinity
    let maxLng = -Infinity
    for (const poly of polygons) {
      for (const ring of poly) {
        for (const [lng, lat] of ring) {
          if (lat < minLat) minLat = lat
          if (lat > maxLat) maxLat = lat
          if (lng < minLng) minLng = lng
          if (lng > maxLng) maxLng = lng
        }
      }
    }
    if (!Number.isFinite(minLat)) continue
    index.push({
      poi,
      minLat,
      maxLat,
      minLng,
      maxLng,
      area: (maxLat - minLat) * (maxLng - minLng),
      polygons,
    })
  }
  return index
}

// Render hidden POIs (default_visible === false) that carry a footprint as
// subtle polygon outlines on the map. Visible POIs keep their normal marker.
// Clicking a polygon opens the standard PoiEditDialog via onPoiSelect.

interface HiddenFootprintsProps {
  pois: PoiRow[]
  onPoiSelect: (selection: PoiSelection) => void
}

function HiddenPoiFootprints({ pois, onPoiSelect }: HiddenFootprintsProps) {
  const map = useMap()
  useEffect(() => {
    const group = L.featureGroup()
    for (const poi of pois) {
      if (poi.deleted_at) continue
      if (poi.default_visible !== false) continue
      const raw = poi.building_footprint_geojson
      if (typeof raw !== 'string' || !raw) continue
      let geom: GeoJSON.GeoJsonObject
      try {
        geom = JSON.parse(raw)
      } catch {
        continue
      }
      const layer = L.geoJSON(geom, {
        style: {
          color: '#8D6E63',
          weight: 1,
          fillColor: '#8D6E63',
          fillOpacity: 0.08,
          opacity: 0.7,
        },
      })
      layer.bindTooltip(String(poi.name ?? poi.id), { sticky: true })
      layer.on('click', () => {
        onPoiSelect({ poi })
      })
      layer.on('mouseover', () => {
        layer.setStyle({ weight: 2, fillOpacity: 0.25 })
      })
      layer.on('mouseout', () => {
        layer.setStyle({ weight: 1, fillOpacity: 0.08 })
      })
      group.addLayer(layer)
    }
    group.addTo(map)
    return () => {
      map.removeLayer(group)
    }
  }, [map, pois, onPoiSelect])
  return null
}

interface HitTestProps {
  pois: PoiRow[]
  onPoiSelect: (selection: PoiSelection) => void
}

function ParcelHitTest({ pois, onPoiSelect }: HitTestProps) {
  const map = useMap()
  const index = useMemo(() => buildFootprintIndex(pois), [pois])

  useEffect(() => {
    const handleClick = (e: L.LeafletMouseEvent) => {
      if (index.length === 0) return
      const { lat, lng } = e.latlng
      let best: FootprintIndexEntry | null = null
      for (const entry of index) {
        if (lat < entry.minLat || lat > entry.maxLat) continue
        if (lng < entry.minLng || lng > entry.maxLng) continue
        if (!pointInPolygons(lng, lat, entry.polygons)) continue
        if (!best || entry.area < best.area) best = entry
      }
      if (best) {
        onPoiSelect({ poi: best.poi })
      }
    }
    map.on('click', handleClick)
    return () => {
      map.off('click', handleClick)
    }
  }, [map, index, onPoiSelect])

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
  title: string
  subjectName: string
  subjectId: string
  from: { lat: number; lng: number }
  to: { lat: number; lng: number }
  onConfirm: () => void
  onCancel: () => void
  busy: boolean
  error: string | null
}

function MoveConfirm({
  title,
  subjectName,
  subjectId,
  from,
  to,
  onConfirm,
  onCancel,
  busy,
  error,
}: MoveConfirmProps) {
  const distance = metersBetween(from, to)
  return (
    <div className="absolute inset-0 z-[500] flex items-center justify-center bg-black/30">
      <div className="bg-white rounded shadow-lg w-96 max-w-[90vw] p-4 text-sm text-slate-800">
        <h2 className="text-base font-semibold mb-2">{title}</h2>
        <p className="text-xs text-slate-600 mb-3">
          <span className="font-medium text-slate-800">{subjectName}</span>{' '}
          (<span className="font-mono text-[10px]">{subjectId}</span>)
        </p>
        <div className="bg-slate-50 border border-slate-200 rounded p-2 text-xs font-mono">
          <div className="flex justify-between">
            <span className="text-slate-500">From:</span>
            <span>
              {from.lat.toFixed(6)}, {from.lng.toFixed(6)}
            </span>
          </div>
          <div className="flex justify-between">
            <span className="text-slate-500">To:</span>
            <span>
              {to.lat.toFixed(6)}, {to.lng.toFixed(6)}
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

function TileProviderPicker({
  providerId,
  onChange,
}: {
  providerId: string
  onChange: (id: string) => void
}) {
  return (
    <div className="absolute top-3 right-3 z-[1000] bg-white/95 backdrop-blur rounded-md shadow-md border border-slate-300 px-2 py-1.5 text-xs text-slate-700 flex items-center gap-2">
      <label htmlFor="admin-tile-provider" className="font-medium text-slate-600">
        Base map:
      </label>
      <select
        id="admin-tile-provider"
        className="text-xs border border-slate-300 rounded px-1.5 py-0.5 bg-white focus:outline-none focus:ring-1 focus:ring-indigo-500"
        value={providerId}
        onChange={(e) => onChange(e.target.value)}
      >
        {TILE_PROVIDERS.map(p => (
          <option key={p.id} value={p.id}>{p.label}</option>
        ))}
      </select>
    </div>
  )
}

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

// ─── Map-click listener for "+ Free waypoint" mode (S174) ──────────────────

interface MapClickAddProps {
  active: boolean
  onClick: (lat: number, lng: number) => void
}

function MapClickAddListener({ active, onClick }: MapClickAddProps) {
  const map = useMap()
  useEffect(() => {
    if (!active) return
    const handler = (e: L.LeafletMouseEvent) => {
      onClick(e.latlng.lat, e.latlng.lng)
    }
    map.on('click', handler)
    return () => {
      map.off('click', handler)
    }
  }, [map, active, onClick])
  return null
}

// ─── Tour-stop layer (S174) ─────────────────────────────────────────────────
//
// Renders the active tour's waypoints as numbered, draggable markers connected
// by a translucent polyline in tour-walk order. dragend → emits a pending stop
// move; the parent shows MoveConfirm and POSTs the PATCH.

const numberedIconCache = new Map<string, L.DivIcon>()

function numberedStopIcon(order: number, overridden: boolean): L.DivIcon {
  const key = `${order}|${overridden ? 'ov' : 'poi'}`
  let icon = numberedIconCache.get(key)
  if (icon) return icon
  // Amber for per-tour overrides, indigo for fallback-to-POI.
  const fill = overridden ? '#d97706' : '#4f46e5'
  const html = `
    <div style="
      width:24px;height:24px;border-radius:50%;
      background:${fill};color:#fff;
      border:2px solid #ffffff;box-shadow:0 0 0 1px rgba(0,0,0,0.4);
      display:flex;align-items:center;justify-content:center;
      font-family:system-ui,-apple-system,sans-serif;
      font-size:11px;font-weight:700;line-height:1;
    ">${order}</div>`
  icon = L.divIcon({
    html,
    className: 'admin-tour-stop-marker',
    iconSize: [24, 24],
    iconAnchor: [12, 12],
  })
  numberedIconCache.set(key, icon)
  return icon
}

interface TourStopLayerProps {
  stops: TourStop[]
  onDragEnd: (move: PendingStopMove) => void
}

function TourStopLayer({ stops, onDragEnd }: TourStopLayerProps) {
  const map = useMap()
  useEffect(() => {
    const group = L.layerGroup()
    const latlngs: [number, number][] = []
    for (const s of stops) {
      if (typeof s.effective_lat !== 'number' || typeof s.effective_lng !== 'number') continue
      latlngs.push([s.effective_lat, s.effective_lng])
    }

    if (latlngs.length >= 2) {
      L.polyline(latlngs, {
        color: '#4f46e5',
        weight: 3,
        opacity: 0.55,
        dashArray: '4 6',
      }).addTo(group)
    }

    for (const stop of stops) {
      const lat = stop.effective_lat
      const lng = stop.effective_lng
      if (typeof lat !== 'number' || typeof lng !== 'number') continue
      const overridden = stop.override_lat != null || stop.override_lng != null
      const label = stop.effective_name ?? `Stop ${stop.stop_order}`
      const marker = L.marker([lat, lng], {
        icon: numberedStopIcon(stop.stop_order, overridden),
        draggable: true,
        autoPan: true,
        title: `${stop.stop_order}. ${label}`,
        zIndexOffset: 1000,
      })
      let dragOrigin = { lat, lng }
      marker.on('dragstart', () => {
        const ll = marker.getLatLng()
        dragOrigin = { lat: ll.lat, lng: ll.lng }
      })
      marker.on('dragend', () => {
        const ll = marker.getLatLng()
        if (ll.lat === dragOrigin.lat && ll.lng === dragOrigin.lng) return
        onDragEnd({
          stop,
          label,
          from: dragOrigin,
          to: { lat: ll.lat, lng: ll.lng },
          marker,
        })
      })
      marker.addTo(group)
    }

    group.addTo(map)
    return () => {
      map.removeLayer(group)
    }
  }, [stops, map, onDragEnd])
  return null
}

interface FitTourBoundsProps {
  stops: TourStop[] | null
  /** Bumps when a different tour is selected so we re-fit. */
  tourId: string | null
}

function FitTourBounds({ stops, tourId }: FitTourBoundsProps) {
  const map = useMap()
  const lastTourIdRef = useRef<string | null>(null)
  useEffect(() => {
    if (!tourId || !stops || tourId === lastTourIdRef.current) return
    const pts: L.LatLngTuple[] = []
    for (const s of stops) {
      if (typeof s.effective_lat === 'number' && typeof s.effective_lng === 'number') {
        pts.push([s.effective_lat, s.effective_lng])
      }
    }
    if (pts.length === 0) return
    const bounds = L.latLngBounds(pts)
    map.fitBounds(bounds, { padding: [40, 40], maxZoom: 18 })
    lastTourIdRef.current = tourId
  }, [stops, tourId, map])
  return null
}

// ─── Top-level component ────────────────────────────────────────────────────

export function AdminMap({
  pois,
  selectedPoi,
  onPoiSelect,
  onPoiMoved,
  categoryFilter,
  onClearCategoryFilter,
  activeTour,
  tourStops,
  onStopMoved,
  addStopMode = 'none',
  onMapClickAddFree,
  onPickPoiForStop,
}: AdminMapProps) {
  const filteredPois = useMemo(() => {
    if (!pois) return pois
    if (!categoryFilter) return pois
    return pois.filter((p) => (p.category as string | undefined) === categoryFilter)
  }, [pois, categoryFilter])
  const [pending, setPending] = useState<PendingMove | null>(null)
  const [moveBusy, setMoveBusy] = useState(false)
  const [moveError, setMoveError] = useState<string | null>(null)

  // Tour-mode (S174) — drag-to-save state for waypoints.
  const [pendingStop, setPendingStop] = useState<PendingStopMove | null>(null)
  const [stopBusy, setStopBusy] = useState(false)
  const [stopError, setStopError] = useState<string | null>(null)

  const handleStopDragEnd = useCallback((move: PendingStopMove) => {
    setPendingStop(move)
    setStopError(null)
  }, [])

  // In POI-pick mode, marker clicks insert the POI as a tour stop instead of
  // opening the edit dialog.
  const effectiveOnPoiSelect = useCallback(
    (sel: PoiSelection) => {
      if (addStopMode === 'poi' && onPickPoiForStop) {
        onPickPoiForStop(sel.poi)
        return
      }
      onPoiSelect(sel)
    },
    [addStopMode, onPickPoiForStop, onPoiSelect],
  )

  const handleMapClickAddFree = useCallback(
    (lat: number, lng: number) => {
      if (onMapClickAddFree) onMapClickAddFree(lat, lng)
    },
    [onMapClickAddFree],
  )

  const handleStopCancel = useCallback(() => {
    if (pendingStop) {
      pendingStop.marker.setLatLng([pendingStop.from.lat, pendingStop.from.lng])
    }
    setPendingStop(null)
    setStopError(null)
  }, [pendingStop])

  const handleStopConfirm = useCallback(async () => {
    if (!pendingStop || !activeTour || !onStopMoved) return
    setStopBusy(true)
    setStopError(null)
    try {
      const url =
        `/api/admin/salem/tours/${encodeURIComponent(activeTour.id)}` +
        `/stops/${pendingStop.stop.stop_id}`
      const res = await fetch(url, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        credentials: 'same-origin',
        body: JSON.stringify({ lat: pendingStop.to.lat, lng: pendingStop.to.lng }),
      })
      if (!res.ok) {
        let msg = `${res.status} ${res.statusText}`
        try {
          const body = await res.json()
          if (body?.error) msg = `${res.status} ${body.error}`
        } catch { /* body wasn't JSON */ }
        throw new Error(msg)
      }
      onStopMoved(pendingStop.stop.stop_id, pendingStop.to.lat, pendingStop.to.lng)
      setPendingStop(null)
    } catch (e) {
      pendingStop.marker.setLatLng([pendingStop.from.lat, pendingStop.from.lng])
      setStopError(e instanceof Error ? e.message : String(e))
    } finally {
      setStopBusy(false)
    }
  }, [pendingStop, activeTour, onStopMoved])

  const [tileProviderId, setTileProviderIdState] = useState<string>(() => {
    try {
      const stored = localStorage.getItem(TILE_PROVIDER_STORAGE_KEY)
      if (stored && TILE_PROVIDERS.some(p => p.id === stored)) return stored
    } catch { /* ignore */ }
    return 'Salem-Custom'
  })
  const setTileProviderId = useCallback((id: string) => {
    setTileProviderIdState(id)
    try { localStorage.setItem(TILE_PROVIDER_STORAGE_KEY, id) } catch { /* ignore */ }
  }, [])
  const activeProvider = useMemo(
    () => TILE_PROVIDERS.find(p => p.id === tileProviderId) ?? TILE_PROVIDERS[0],
    [tileProviderId],
  )

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

  if (!pois && !tourStops) {
    return (
      <div className="absolute inset-0 flex items-center justify-center text-slate-500">
        <p className="text-sm italic">Loading POI map…</p>
      </div>
    )
  }

  return (
    <div className={`absolute inset-0 ${addStopMode !== 'none' ? 'cursor-crosshair' : ''}`}>
      {addStopMode !== 'none' && (
        <div className="absolute top-2 left-1/2 -translate-x-1/2 z-[600]
                        bg-amber-500 text-white text-xs px-3 py-1 rounded-full shadow">
          {addStopMode === 'free'
            ? 'Click the map to drop a free waypoint'
            : 'Click any POI marker to add it as a stop'}
        </div>
      )}
      <MapContainer
        center={SALEM_CENTER}
        zoom={INITIAL_ZOOM}
        maxZoom={22}
        className="h-full w-full"
        zoomControl={true}
      >
        <TileLayer
          key={activeProvider.id}
          url={activeProvider.url}
          attribution={activeProvider.attribution}
          maxZoom={22}
          maxNativeZoom={activeProvider.maxNativeZoom}
        />
        <HiddenPoiFootprints pois={filteredPois ?? []} onPoiSelect={effectiveOnPoiSelect} />
        <MarkerLayer
          pois={filteredPois ?? []}
          selectedKey={selectedKey}
          onPoiSelect={effectiveOnPoiSelect}
          onDragEnd={handleDragEnd}
        />
        {addStopMode !== 'free' && (
          <ParcelHitTest pois={filteredPois ?? []} onPoiSelect={effectiveOnPoiSelect} />
        )}
        <MapClickAddListener
          active={addStopMode === 'free'}
          onClick={handleMapClickAddFree}
        />
        <FlyToSelected selectedPoi={selectedPoi} />
        {tourStops && tourStops.length > 0 && (
          <TourStopLayer stops={tourStops} onDragEnd={handleStopDragEnd} />
        )}
        <FitTourBounds stops={tourStops ?? null} tourId={activeTour?.id ?? null} />
      </MapContainer>
      <TileProviderPicker
        providerId={tileProviderId}
        onChange={setTileProviderId}
      />
      <Legend categoryCounts={categoryCounts} />
      {categoryFilter && (
        <div className="absolute top-2 left-1/2 -translate-x-1/2 z-[500]
                        bg-indigo-600 text-white text-sm px-3 py-1 rounded-full shadow
                        flex items-center gap-2">
          <span>Filtering: <strong>{categoryFilter}</strong></span>
          <button
            type="button"
            onClick={onClearCategoryFilter}
            className="ml-1 text-white/80 hover:text-white underline text-xs"
          >
            clear
          </button>
        </div>
      )}
      {pending && (
        <MoveConfirm
          title="Confirm POI move"
          subjectName={pending.poi.name}
          subjectId={pending.poi.id}
          from={pending.from}
          to={pending.to}
          busy={moveBusy}
          error={moveError}
          onConfirm={handleConfirm}
          onCancel={handleCancel}
        />
      )}
      {pendingStop && (
        <MoveConfirm
          title="Confirm waypoint move"
          subjectName={pendingStop.label}
          subjectId={`stop #${pendingStop.stop.stop_id} · order ${pendingStop.stop.stop_order}`}
          from={pendingStop.from}
          to={pendingStop.to}
          busy={stopBusy}
          error={stopError}
          onConfirm={handleStopConfirm}
          onCancel={handleStopCancel}
        />
      )}
    </div>
  )
}
