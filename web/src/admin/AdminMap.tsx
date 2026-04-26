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
import type { TourLeg, TourStop, TourSummary } from './tourTypes'

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

const ZOOM_LABEL_THRESHOLD = 17

function humanizeCategory(category: string | undefined): string {
  if (!category) return ''
  return category
    .replace(/_/g, ' ')
    .split(' ')
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(' ')
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * Mirrors `MarkerIconHelper.labeledDot` on Android: humanized category label
 * above the dot, name label below. Used at zoom ≥ ZOOM_LABEL_THRESHOLD when
 * the operator is close enough to the map for labels to be readable without
 * crowding the view.
 */
function poiIconLabeled(
  color: string,
  selected: boolean,
  hidden: boolean,
  category: string | undefined,
  name: string | undefined
): L.DivIcon {
  const cat = humanizeCategory(category)
  const nm = name && name.trim() ? name : ''
  const key = `${color}|${selected ? 'sel' : 'norm'}|${hidden ? 'hid' : 'vis'}|L|${cat}|${nm}`
  const cached = iconCache.get(key)
  if (cached) return cached

  const dotSize = selected ? 18 : 12
  const stroke = selected ? '#facc15' : '#ffffff'
  const strokeW = selected ? 3 : 1.5
  const opacity = hidden ? 0.35 : 1

  const dotSvg = `
    <svg width="${dotSize}" height="${dotSize}" viewBox="0 0 ${dotSize} ${dotSize}" xmlns="http://www.w3.org/2000/svg" style="opacity:${opacity}">
      <circle cx="${dotSize / 2}" cy="${dotSize / 2}" r="${dotSize / 2 - strokeW / 2}"
              fill="${color}" stroke="${stroke}" stroke-width="${strokeW}"/>
    </svg>`

  const catHtml = cat
    ? `<div class="admin-poi-label admin-poi-label-cat" style="color:${color}">${escapeHtml(cat)}</div>`
    : ''
  const nameHtml = nm
    ? `<div class="admin-poi-label admin-poi-label-name">${escapeHtml(nm)}</div>`
    : ''

  const html = `
    <div class="admin-poi-labeled">
      ${catHtml}
      <div class="admin-poi-labeled-dot">${dotSvg}</div>
      ${nameHtml}
    </div>`

  // iconAnchor centers on the dot, not the bounding box. Width is auto via
  // CSS (max-content); height is dot + ~15px label above + ~15px below per
  // label that's present.
  const labelH = 15
  const totalH = (cat ? labelH + 2 : 0) + dotSize + (nm ? labelH + 2 : 0)
  const yAnchor = (cat ? labelH + 2 : 0) + dotSize / 2

  const icon = L.divIcon({
    html,
    className: 'admin-poi-marker admin-poi-marker-labeled',
    iconSize: [120, totalH],
    iconAnchor: [60, yAnchor],
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
  /** Precomputed walking legs (S183). When set, rendered as a green polyline
   *  overlay underneath the waypoints. */
  tourLegs?: TourLeg[] | null
  /** When set, the matching leg renders in red instead of green and a click
   *  on any leg polyline reports the leg_order via onLegSelect. */
  selectedLegOrder?: number | null
  onLegSelect?: (legOrder: number) => void
  /** When set (with a fresh nonce on each request), the map flies to the
   *  matching stop's coords. The nonce lets repeat-clicks re-fire. */
  focusedStopId?: number | null
  focusedStopNonce?: number
  onStopMoved?: (stopId: number, lat: number, lng: number) => void
  /** Add-stop mode: 'free' = next map click creates a free waypoint,
   *  'poi' = next POI marker click adds that POI as a stop. */
  addStopMode?: 'none' | 'free' | 'poi'
  /** Exit add-stop mode (banner close button + Esc key). */
  onCancelAddStopMode?: () => void
  onMapClickAddFree?: (lat: number, lng: number) => void
  onPickPoiForStop?: (poi: PoiRow) => void
  /** S177 P5 — when set, draw a walking route from current map center to
   *  this POI using /api/salem/route. */
  directionsTarget?: PoiRow | null
  onClearDirections?: () => void
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
  const markersByKeyRef = useRef<
    Map<
      string,
      {
        marker: L.Marker
        color: string
        hidden: boolean
        category: string | undefined
        name: string | undefined
      }
    >
  >(new Map())
  const labelsActiveRef = useRef<boolean>(map.getZoom() >= ZOOM_LABEL_THRESHOLD)

  const iconFor = (
    color: string,
    selected: boolean,
    hidden: boolean,
    category: string | undefined,
    name: string | undefined,
    labeled: boolean
  ): L.DivIcon => {
    return labeled
      ? poiIconLabeled(color, selected, hidden, category, name)
      : poiIcon(color, selected, hidden)
  }

  useEffect(() => {
    if (clusterGroupRef.current) {
      map.removeLayer(clusterGroupRef.current)
      clusterGroupRef.current = null
      markersByKeyRef.current.clear()
    }

    const cluster = L.markerClusterGroup({
      showCoverageOnHover: false,
      spiderfyOnMaxZoom: true,
      // S185: matches ZOOM_LABEL_THRESHOLD so labels appear the moment
      // markers de-cluster — no zoom-17 limbo where some markers are
      // labeled and others are still hidden inside a cluster bubble.
      disableClusteringAtZoom: ZOOM_LABEL_THRESHOLD,
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
        icon: iconFor(
          color,
          isSelected,
          isHidden,
          poi.category as string | undefined,
          poi.name as string | undefined,
          labelsActiveRef.current
        ),
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
      markersByKeyRef.current.set(key, {
        marker,
        color,
        hidden: isHidden,
        category: poi.category as string | undefined,
        name: poi.name as string | undefined,
      })
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
    const labeled = labelsActiveRef.current
    const prev = previousSelectedKeyRef.current
    if (prev && prev !== selectedKey) {
      const entry = markersByKeyRef.current.get(prev)
      if (entry) {
        entry.marker.setIcon(
          iconFor(entry.color, false, entry.hidden, entry.category, entry.name, labeled)
        )
      }
    }
    if (selectedKey) {
      const entry = markersByKeyRef.current.get(selectedKey)
      if (entry) {
        entry.marker.setIcon(
          iconFor(entry.color, true, entry.hidden, entry.category, entry.name, labeled)
        )
      }
    }
    previousSelectedKeyRef.current = selectedKey
  }, [selectedKey])

  // S185: swap to labeled icons (humanized category above the dot, name below
  // — same layout as Android's MarkerIconHelper.labeledDot) once we cross
  // ZOOM_LABEL_THRESHOLD. Below the threshold, plain dot to avoid label
  // crowding when many POIs are visible. Listening on zoomend so we only
  // touch markers when the threshold is actually crossed.
  useEffect(() => {
    const apply = () => {
      const shouldLabel = map.getZoom() >= ZOOM_LABEL_THRESHOLD
      if (shouldLabel === labelsActiveRef.current) return
      labelsActiveRef.current = shouldLabel
      const selKey = previousSelectedKeyRef.current
      for (const [key, entry] of markersByKeyRef.current.entries()) {
        const isSel = key === selKey
        entry.marker.setIcon(
          iconFor(entry.color, isSel, entry.hidden, entry.category, entry.name, shouldLabel)
        )
      }
    }
    map.on('zoomend', apply)
    apply()
    return () => {
      map.off('zoomend', apply)
    }
  }, [map])

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

// Pans the map to a tour stop when its row is clicked in the side panel.
// `nonce` must change on every click (even re-clicks of the same stop) so the
// effect re-fires; the stops array provides the coords by id.
interface FlyToStopProps {
  stopId: number | null
  nonce: number
  stops: TourStop[] | null
}

function FlyToStop({ stopId, nonce, stops }: FlyToStopProps) {
  const map = useMap()
  useEffect(() => {
    if (stopId == null || !stops) return
    const target = stops.find((s) => s.stop_id === stopId)
    if (!target) return
    const lat = target.effective_lat
    const lng = target.effective_lng
    if (typeof lat !== 'number' || typeof lng !== 'number') return
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) return
    const targetZoom = Math.max(map.getZoom(), 18)
    map.flyTo([lat, lng], targetZoom, { duration: 0.5 })
    // nonce intentionally listed so re-clicks re-fire the effect.
  }, [stopId, nonce, stops, map])
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

// ─── Tour legs layer (S183) ────────────────────────────────────────────────
//
// Renders the precomputed walking polyline for a tour as a green outline +
// fill (matching the in-app directions style). Renders BELOW the numbered
// stop markers and ABOVE the basemap. Each leg is a separate Leaflet
// polyline (we keep them separate so future hand-vertex editing can target
// one leg at a time).
interface TourLegsLayerProps {
  legs: TourLeg[]
  /** Stops are used to anchor each leg's polyline to the from/to marker coords
   *  so the rendered route visually touches the numbered waypoint dots (no
   *  road-snap gap). */
  stops: TourStop[] | null
  /** Highlight a single leg in red when its row is selected in the side panel.
   *  null = no leg highlighted. */
  selectedLegOrder: number | null
  /** Click anywhere on a leg polyline to select it (mirrors clicking the
   *  leg row in the side panel). */
  onLegSelect: (legOrder: number) => void
}

const SELECTED_LEG_PANE = 'tour-selected-leg-pane'

function TourLegsLayer({ legs, stops, selectedLegOrder, onLegSelect }: TourLegsLayerProps) {
  const map = useMap()

  useEffect(() => {
    // Custom pane for the selected leg so it stacks ABOVE TourStopLayer's
    // dashed inter-stop polyline (overlayPane = 400) but BELOW the numbered
    // waypoint markers (markerPane = 600). Created here (not in a separate
    // effect) to guarantee the pane exists before any polyline references it.
    let pane = map.getPane(SELECTED_LEG_PANE)
    if (!pane) {
      pane = map.createPane(SELECTED_LEG_PANE)
      pane.style.pointerEvents = 'auto'
    }
    // Re-assert z-index every render in case Leaflet recreated the pane.
    // 450 sits between overlayPane (400) and shadowPane (500), so the
    // selected leg renders above the green legs + dashed connector but
    // below shadows / waypoint markers.
    pane.style.zIndex = '450'

    const group = L.layerGroup()
    // Build a stop_id → [lat,lng] lookup so we can anchor each leg's polyline
    // at the marker positions, matching Find→Directions visual style.
    const stopById = new Map<number, [number, number]>()
    for (const s of stops ?? []) {
      if (typeof s.effective_lat === 'number' && typeof s.effective_lng === 'number') {
        stopById.set(s.stop_id, [s.effective_lat, s.effective_lng])
      }
    }

    // Build anchored polylines for every leg first; defer drawing the selected
    // one so it lands on top of all the green legs. The selected leg also
    // renders in a higher-z pane so it sits above the dashed inter-stop line.
    // Squared-degree distance — fine for tiny intra-tour comparisons (sub-km).
    const sq = (ax: number, ay: number, bx: number, by: number) => {
      const dx = ax - bx
      const dy = ay - by
      return dx * dx + dy * dy
    }
    // Index of the closest polyline point to `target`.
    const closestIdx = (poly: L.LatLngTuple[], target: [number, number]) => {
      let best = 0
      let bestD = Infinity
      for (let i = 0; i < poly.length; i++) {
        const p = poly[i]
        const d = sq(p[0] as number, p[1] as number, target[0], target[1])
        if (d < bestD) {
          bestD = d
          best = i
        }
      }
      return best
    }

    const drawLeg = (leg: TourLeg, selected: boolean) => {
      const routed = leg.polyline_json as L.LatLngTuple[] | unknown
      if (!Array.isArray(routed) || routed.length < 2) return
      const from = stopById.get(leg.from_stop_id)
      const to = stopById.get(leg.to_stop_id)
      // Clip the routed polyline to the segment closest to from/to markers
      // before anchoring. Without clipping, an appended to_marker that sits
      // BEFORE the polyline's last routed point reads as an overshoot dogleg
      // (router snapped past the waypoint). Same for from_marker undershoots.
      let startIdx = 0
      let endIdx = routed.length - 1
      if (from) startIdx = closestIdx(routed, from)
      if (to) endIdx = closestIdx(routed, to)
      // Degenerate (e.g. very short legs where both anchors map to one point):
      // fall back to the full routed geometry.
      if (startIdx > endIdx) {
        startIdx = 0
        endIdx = routed.length - 1
      }
      const pts: L.LatLngTuple[] = []
      if (from) pts.push(from)
      for (let i = startIdx; i <= endIdx; i++) pts.push(routed[i])
      if (to) pts.push(to)
      if (pts.length < 2) return

      const outlineColor = selected ? '#7f1d1d' : '#0f5132'
      const fillColor = selected ? '#ef4444' : '#22C55E'
      const outlineWeight = selected ? 12 : 8
      const fillWeight = selected ? 7 : 5
      const baseOpts: L.PolylineOptions = {
        interactive: true,
        bubblingMouseEvents: false,
      }
      if (selected) baseOpts.pane = SELECTED_LEG_PANE
      L.polyline(pts, {
        ...baseOpts,
        color: outlineColor,
        weight: outlineWeight,
        opacity: selected ? 0.85 : 0.45,
      })
        .on('click', () => onLegSelect(leg.leg_order))
        .addTo(group)
      L.polyline(pts, {
        ...baseOpts,
        color: fillColor,
        weight: fillWeight,
        opacity: 1.0,
      })
        .on('click', () => onLegSelect(leg.leg_order))
        .addTo(group)
    }

    let selectedLeg: TourLeg | null = null
    for (const leg of legs) {
      if (selectedLegOrder === leg.leg_order) {
        selectedLeg = leg
        continue
      }
      drawLeg(leg, false)
    }
    if (selectedLeg) drawLeg(selectedLeg, true)
    group.addTo(map)
    return () => {
      map.removeLayer(group)
    }
  }, [legs, stops, selectedLegOrder, onLegSelect, map])
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

// ─── Directions layer (S177 P5) ────────────────────────────────────────────
//
// Draws a walking route from the current map center to a target POI using
// /api/salem/route (the cache-proxy router from P4). Re-fetches whenever the
// target or selected source changes; origin is captured at fetch time so the
// route doesn't re-flow as the operator pans afterwards.

interface RouteResponse {
  source: 'bundle' | 'live'
  distance_m: number
  duration_s: number
  pace_mps: number
  geometry: Array<[number, number]> // [lat, lng]
  edges: Array<{ edge_id: number; fullname: string; mtfcc: string | null; length_m: number }>
}

interface DirectionsLayerProps {
  target: PoiRow
  source: 'bundle' | 'live'
  onResult: (r: RouteResponse | null, err: string | null) => void
}

function DirectionsLayer({ target, source, onResult }: DirectionsLayerProps) {
  const map = useMap()
  const polylineRef = useRef<L.Polyline | null>(null)
  const originMarkerRef = useRef<L.CircleMarker | null>(null)
  const fitDoneRef = useRef<string | null>(null) // key per (target.id, source) to fit only once

  useEffect(() => {
    let cancelled = false
    // Capture origin at fetch time so panning after doesn't re-route.
    const c = map.getCenter()
    const fromLat = c.lat
    const fromLng = c.lng
    const toLat = target.lat as number
    const toLng = target.lng as number

    const url =
      `/api/salem/route?from_lat=${fromLat}&from_lng=${fromLng}` +
      `&to_lat=${toLat}&to_lng=${toLng}&source=${source}`

    onResult(null, null) // clear panel while fetching

    fetch(url, { credentials: 'same-origin' })
      .then(async (res) => {
        const body = (await res.json()) as RouteResponse | { error?: string }
        if (!res.ok) {
          const msg = (body as { error?: string }).error || `${res.status} ${res.statusText}`
          throw new Error(msg)
        }
        return body as RouteResponse
      })
      .then((r) => {
        if (cancelled) return
        // Clear any prior overlay.
        if (polylineRef.current) { map.removeLayer(polylineRef.current); polylineRef.current = null }
        if (originMarkerRef.current) { map.removeLayer(originMarkerRef.current); originMarkerRef.current = null }

        if (!r.geometry.length) {
          onResult(r, null)
          return
        }
        // Polyline (matches Android #22C55E, 5px, with a wider darker outline for legibility).
        const pts = r.geometry as L.LatLngExpression[]
        const outline = L.polyline(pts, { color: '#0f5132', weight: 8, opacity: 0.45, interactive: false })
        const line = L.polyline(pts, { color: '#22C55E', weight: 5, opacity: 0.95, interactive: false })
        outline.addTo(map)
        line.addTo(map)
        // Pair the outline with the line by overwriting the ref to a layer group.
        polylineRef.current = L.layerGroup([outline, line]).addTo(map) as unknown as L.Polyline

        // Origin marker — small green-bordered dot at the captured map center.
        originMarkerRef.current = L.circleMarker([fromLat, fromLng], {
          radius: 6, color: '#0f5132', weight: 2, fillColor: '#22C55E', fillOpacity: 1,
        }).addTo(map)

        // Fit bounds on the first render for this (target, source) only — re-fitting
        // on every update fights the operator if they pan in to inspect.
        const fitKey = `${target.id}|${source}`
        if (fitDoneRef.current !== fitKey) {
          fitDoneRef.current = fitKey
          const bounds = L.latLngBounds(pts).extend([fromLat, fromLng])
          map.fitBounds(bounds, { padding: [60, 60], maxZoom: 18 })
        }
        onResult(r, null)
      })
      .catch((e) => {
        if (cancelled) return
        onResult(null, e instanceof Error ? e.message : String(e))
      })

    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target.id, target.lat, target.lng, source, map])

  // Final cleanup when the layer unmounts (target cleared).
  useEffect(() => {
    return () => {
      if (polylineRef.current) { map.removeLayer(polylineRef.current); polylineRef.current = null }
      if (originMarkerRef.current) { map.removeLayer(originMarkerRef.current); originMarkerRef.current = null }
    }
  }, [map])

  return null
}

interface DirectionsPanelProps {
  target: PoiRow
  source: 'bundle' | 'live'
  onSourceChange: (s: 'bundle' | 'live') => void
  result: RouteResponse | null
  error: string | null
  onClose: () => void
}

function DirectionsPanel({
  target, source, onSourceChange, result, error, onClose,
}: DirectionsPanelProps) {
  function fmtDistance(m: number): string {
    if (m < 1000) return `${m.toFixed(0)} m`
    return `${(m / 1000).toFixed(2)} km`
  }
  function fmtDuration(s: number): string {
    const mins = Math.round(s / 60)
    if (mins < 60) return `${mins} min`
    const h = Math.floor(mins / 60); const r = mins % 60
    return r === 0 ? `${h} h` : `${h} h ${r} min`
  }

  return (
    <div className="absolute top-2 right-2 z-[600] bg-white border border-emerald-300 rounded shadow-lg
                    px-3 py-2 text-xs min-w-[220px] max-w-[320px]">
      <div className="flex items-start justify-between gap-2">
        <div className="font-semibold text-emerald-900">
          Directions to <span className="text-slate-900">{target.name}</span>
        </div>
        <button
          type="button"
          onClick={onClose}
          className="text-slate-500 hover:text-slate-900 leading-none -mt-0.5"
          aria-label="Clear directions"
          title="Clear"
        >
          ×
        </button>
      </div>
      <div className="mt-1 text-slate-600 text-[11px]">
        Origin: current map center at request time
      </div>
      {error && (
        <div className="mt-2 text-rose-700 bg-rose-50 px-2 py-1 rounded">
          {error}
        </div>
      )}
      {!error && !result && (
        <div className="mt-2 text-slate-500 italic">Computing route…</div>
      )}
      {result && (
        <div className="mt-2 space-y-1">
          <div>
            <span className="font-mono text-emerald-700">{fmtDistance(result.distance_m)}</span>
            <span className="text-slate-500"> · </span>
            <span className="font-mono">{fmtDuration(result.duration_s)}</span>
            <span className="text-slate-500"> walking @ {result.pace_mps} m/s</span>
          </div>
          <div className="text-[11px] text-slate-500">
            {result.geometry.length} pts · {result.edges.length} edges
          </div>
        </div>
      )}
      <div className="mt-2 flex items-center gap-1 text-[11px]">
        <span className="text-slate-500">Source:</span>
        <button
          type="button"
          onClick={() => onSourceChange('bundle')}
          className={`px-2 py-0.5 rounded ${
            source === 'bundle'
              ? 'bg-emerald-600 text-white'
              : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
          }`}
          title="Bundled SQLite (same data the APK ships)"
        >
          bundle
        </button>
        <button
          type="button"
          onClick={() => onSourceChange('live')}
          className={`px-2 py-0.5 rounded ${
            source === 'live'
              ? 'bg-emerald-600 text-white'
              : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
          }`}
          title="TigerLine live tiger.route_walking() — verification path"
        >
          live
        </button>
      </div>
    </div>
  )
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
  tourLegs,
  selectedLegOrder = null,
  onLegSelect,
  focusedStopId = null,
  focusedStopNonce = 0,
  onStopMoved,
  addStopMode = 'none',
  onCancelAddStopMode,
  onMapClickAddFree,
  onPickPoiForStop,
  directionsTarget,
  onClearDirections,
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

  // S177 P5 — Directions overlay state.
  const [directionsSource, setDirectionsSource] = useState<'bundle' | 'live'>('bundle')
  const [directionsResult, setDirectionsResult] = useState<RouteResponse | null>(null)
  const [directionsError, setDirectionsError] = useState<string | null>(null)
  const handleDirectionsResult = useCallback(
    (r: RouteResponse | null, err: string | null) => {
      setDirectionsResult(r)
      setDirectionsError(err)
    },
    [],
  )
  // Reset transient state when the target POI changes (avoid showing stale
  // distance from the previous target while the new one is fetching).
  useEffect(() => {
    setDirectionsResult(null)
    setDirectionsError(null)
  }, [directionsTarget?.id])

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

  // Esc cancels add-stop mode (free/POI). Listener is global so it works
  // whether the map or the side panel has focus. Declared before the early
  // return below so hook order stays stable across renders.
  useEffect(() => {
    if (addStopMode === 'none' || !onCancelAddStopMode) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancelAddStopMode()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [addStopMode, onCancelAddStopMode])

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
                        bg-amber-500 text-white text-xs pl-3 pr-1 py-1 rounded-full shadow
                        flex items-center gap-2">
          <span>
            {addStopMode === 'free'
              ? 'Click the map to drop a free waypoint'
              : 'Click any POI marker to add it as a stop'}
          </span>
          <span className="text-amber-100 text-[10px]">(Esc to cancel)</span>
          <button
            type="button"
            onClick={onCancelAddStopMode}
            title="Cancel add-stop mode"
            className="rounded-full w-5 h-5 leading-none flex items-center justify-center
                       bg-amber-700 hover:bg-amber-800 text-white text-sm"
          >
            ×
          </button>
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
        {tourLegs && tourLegs.length > 0 && (
          <TourLegsLayer
            legs={tourLegs}
            stops={tourStops ?? null}
            selectedLegOrder={selectedLegOrder}
            onLegSelect={onLegSelect ?? (() => {})}
          />
        )}
        {tourStops && tourStops.length > 0 && (
          <TourStopLayer stops={tourStops} onDragEnd={handleStopDragEnd} />
        )}
        <FitTourBounds stops={tourStops ?? null} tourId={activeTour?.id ?? null} />
        <FlyToStop
          stopId={focusedStopId}
          nonce={focusedStopNonce}
          stops={tourStops ?? null}
        />
        {directionsTarget && Number.isFinite(directionsTarget.lat) && Number.isFinite(directionsTarget.lng) && (
          <DirectionsLayer
            target={directionsTarget}
            source={directionsSource}
            onResult={handleDirectionsResult}
          />
        )}
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
      {directionsTarget && (
        <DirectionsPanel
          target={directionsTarget}
          source={directionsSource}
          onSourceChange={setDirectionsSource}
          result={directionsResult}
          error={directionsError}
          onClose={() => { onClearDirections?.() }}
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
