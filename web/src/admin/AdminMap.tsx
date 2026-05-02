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
import type { PoiRow, PoiSelection, CategoryRow } from './PoiTree'
import {
  categoryFilterMatches,
  makeCategoryLabelLookup,
  isMassgisHistorical,
  HIST_BUILDINGS_FILTER,
  HIST_LANDMARK_FILTER,
} from './PoiTree'
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
  if (!category) return DEFAULT_COLOR
  // S190 — synthetic split keys like HISTORICAL_BUILDINGS:mhc share the
  // base category's color.
  const base = category.split(':')[0]
  return CATEGORY_COLORS[base] || CATEGORY_COLORS[category] || DEFAULT_COLOR
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

// Witchy bundle is the ONLY basemap (S188 operator rule). Mapnik/Esri/OSM
// removed from the picker. Server-side overzoom (cache-proxy/lib/admin-tiles.js)
// fills missing zoom levels by resizing whatever Witchy tile we DO have for
// the requested area.
const TILE_PROVIDERS: TileProvider[] = [
  {
    id: 'Salem-Custom',
    label: 'Witchy (Salem-Custom)',
    url: '/api/admin/tiles/Salem-Custom/{z}/{x}/{y}',
    attribution: 'Bundled Witchy tiles — OSM + MassGIS | planetiler + tippecanoe + MapLibre-GL-Native',
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
  /** Override the FlyToSelected zoom floor (default 17). Lint nav passes 20. */
  selectedPoiMinZoom?: number
  onPoiSelect: (selection: PoiSelection) => void
  onPoiMoved: (id: string, lat: number, lng: number) => void
  /** When set, only POIs with matching category render on the map. */
  categoryFilter?: string | null
  onClearCategoryFilter?: () => void
  /**
   * S190 — canonical categories from salem_poi_categories. Drives the
   * legend + filter banner labels so they match the tree and dropdown
   * (DB is single source of truth).
   */
  categories?: CategoryRow[]
  /** When set (S187 lint nav), only POIs whose id is in the set render. Used
   *  by the Lint tab "Show on Map" action so the operator sees only the POIs
   *  flagged by the current check. Independent of categoryFilter (intersected
   *  if both are set). */
  lintIdFilter?: Set<string> | null
  /** Optional label shown in the floating filter banner. */
  lintIdFilterLabel?: string | null
  onClearLintIdFilter?: () => void
  /** S221 — search-driven id filter. Mirrors the PoiTree search box: when
   *  the operator types in the tree's search, the map narrows to the matching
   *  set so what they see in the tree is what they see on the map. */
  searchIdFilter?: Set<string> | null
  /** Label for the floating filter banner (raw search term). */
  searchIdFilterLabel?: string | null
  /** Tour-mode props (S174). When activeTour is set, the tour-stop layer
   *  renders numbered draggable waypoints over the basemap. */
  activeTour?: TourSummary | null
  /** S214 — When activeTour is set, narrow visible POIs to the tour-mode
   *  narration set (matches device S186 gate logic): is_tour_poi=true is the
   *  always-narrate baseline; histLandmark + civic are opt-in classes. */
  tourModeFilter?: { tourPois: boolean; histLandmark: boolean; civic: boolean } | null
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
  /** S188 — geocode-candidate preview. When set, the map renders a temp
   *  marker at the candidate lat/lng plus a line from the source POI, and
   *  shows a floating panel with accept / ignore / cancel buttons. */
  geocodePreview?: {
    sourcePoi: PoiRow
    candidate: {
      lat: number
      lng: number
      rating: number
      normalized_address: string
      distance_m: number
    }
    /** Every Tiger candidate across the cluster — focal + dupes. The "Show
     *  all candidates" toggle in the panel renders all of these on the map
     *  with dashed lines back to their source POI. */
    allCandidates?: Array<{
      source_poi_id: string
      source_poi_name: string
      source_lat: number
      source_lng: number
      source_address: string | null
      lat: number
      lng: number
      rating: number
      normalized_address: string
      distance_m: number
    }>
  } | null
  onGeocodePreviewAccept?: () => void | Promise<void>
  onGeocodePreviewIgnore?: () => void | Promise<void>
  onGeocodePreviewCancel?: () => void
  /** Validate stored coords as correct without moving the POI. */
  onGeocodePreviewValidateStored?: () => void | Promise<void>
  /** Open the source POI's editor focused on the address field. */
  onGeocodePreviewEditAddress?: () => void
  /** S218 — proposal review mode. When set, the editor dialog is closed,
   *  the map flies to the proposal at z20, and the fuchsia "?" pin becomes
   *  draggable so the operator can fine-tune before clicking Accept. The
   *  small floating panel with Accept/Cancel lives in AdminLayout. */
  proposalReview?: {
    poi: PoiRow
    /** Effective drag position (overrides poi.lat_proposed/lng_proposed when set). */
    dragLat: number | null
    dragLng: number | null
    /** Bumped when the parent wants to (re-)fly to the proposal at z20. */
    flyNonce: number
  } | null
  onProposalDrag?: (lat: number, lng: number) => void
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
  /** Minimum zoom floor when flying to the selection. Default 17. Lint nav
   *  overrides to 20 (3 closer) so the operator lands tight on the marker. */
  minZoom?: number
}

function FlyToSelected({ selectedPoi, minZoom = 17 }: FlyToProps) {
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
    const targetZoom = Math.max(map.getZoom(), minZoom)
    map.flyTo([lat, lng], targetZoom, { duration: 0.6 })
  }, [selectedPoi, map, minZoom])
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
  /** S214 — when present, the dialog shows the TigerLine edge the move will
   *  snap to. Tour-stop drags populate this; POI drags leave it null. */
  snap?: { fullname: string | null; snap_m: number; edge_id: number } | null
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
  snap,
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
        {snap && (
          <div className="mt-2 bg-fuchsia-50 border border-fuchsia-200 rounded p-2 text-xs text-fuchsia-900">
            <div className="font-medium">Will snap to TigerLine edge</div>
            <div className="font-mono mt-1">
              {snap.fullname || '(unnamed edge)'}{' '}
              <span className="text-fuchsia-700">· Δ {snap.snap_m.toFixed(1)} m</span>
            </div>
            <div className="text-fuchsia-700/80 text-[10px] mt-0.5">
              edge_id {snap.edge_id}
            </div>
          </div>
        )}
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

function Legend({
  categoryCounts,
  labelLookup,
}: {
  categoryCounts: [string, number][]
  labelLookup: (cat: string) => string
}) {
  return (
    <div className="absolute top-2 right-2 z-[100] bg-white/95 border border-slate-300 rounded shadow px-3 py-2 text-xs max-h-80 overflow-y-auto">
      <div className="font-semibold text-slate-700 mb-1">Legend</div>
      {categoryCounts.map(([cat, count]) => (
        <div key={cat} className="flex items-center gap-2">
          <span
            className="inline-block w-3 h-3 rounded-full border border-white flex-shrink-0"
            style={{ background: categoryColor(cat) }}
          />
          <span className="text-slate-700 truncate">{labelLookup(cat)}</span>
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

// ─── Proposal preview (S218) ────────────────────────────────────────────────
// When the selected POI has lat_proposed/lng_proposed set (typically from
// the PoiEditDialog "Validate via TigerLine" button), drop a fuchsia "?"
// pin at the proposed location + dashed line back to the live coords so
// the operator can eyeball the move before clicking Accept proposed
// coordinates. Lighter than the cluster-aware GeocodePreviewLayer below
// because there's no candidate list — just one stored point + one proposal.

interface ProposalPreviewLayerProps {
  poi: PoiRow
  /** S218 — when in proposal-review mode, the pin is draggable + uses the
   *  caller-supplied dragLat/dragLng instead of poi.lat_proposed so the dashed
   *  line and pin track each drag without round-tripping the DB. */
  draggable?: boolean
  dragLat?: number | null
  dragLng?: number | null
  onDrag?: (lat: number, lng: number) => void
}

function ProposalPreviewLayer({
  poi, draggable = false, dragLat = null, dragLng = null, onDrag,
}: ProposalPreviewLayerProps) {
  const map = useMap()
  const layerRef = useRef<L.LayerGroup | null>(null)

  // Effective proposed coords: dragLat/dragLng override the stored proposal
  // when the operator is mid-drag in review mode.
  const propLat = dragLat != null ? dragLat : poi.lat_proposed
  const propLng = dragLng != null ? dragLng : poi.lng_proposed

  useEffect(() => {
    if (layerRef.current) {
      map.removeLayer(layerRef.current)
      layerRef.current = null
    }
    if (propLat == null || propLng == null) return
    if (poi.lat == null || poi.lng == null) return

    const group = L.layerGroup()

    L.polyline(
      [[poi.lat as number, poi.lng as number],
       [propLat as number, propLng as number]],
      { color: '#a21caf', weight: 3, dashArray: '6 6', opacity: 0.85 },
    ).addTo(group)

    const proposalIcon = L.divIcon({
      className: 'proposal-marker',
      html: `<div style="background:#a21caf;color:#fff;border:3px solid #fdf4ff;
                         border-radius:50%;width:30px;height:30px;line-height:24px;
                         text-align:center;font-weight:700;font-size:16px;
                         box-shadow:0 2px 6px rgba(0,0,0,0.4);
                         cursor:${draggable ? 'grab' : 'default'};">?</div>`,
      iconSize: [30, 30], iconAnchor: [15, 15],
    })
    const driftLine = poi.location_drift_m != null
      ? `${Number(poi.location_drift_m).toFixed(1)} m from current`
      : ''
    const ratingLine = poi.location_geocoder_rating != null
      ? `Tiger rating ${poi.location_geocoder_rating}`
      : ''
    const tip = [
      draggable ? 'Drag to fine-tune' : 'Proposed location',
      poi.location_source ?? null, ratingLine, driftLine,
    ].filter(Boolean).join('<br>')
    const m = L.marker(
      [propLat as number, propLng as number],
      { icon: proposalIcon, zIndexOffset: 1100, draggable, autoPan: true },
    ).bindTooltip(tip, { direction: 'top', offset: [0, -15] })
    if (draggable && onDrag) {
      m.on('dragend', (ev) => {
        const ll = (ev.target as L.Marker).getLatLng()
        onDrag(ll.lat, ll.lng)
      })
    }
    m.addTo(group)

    group.addTo(map)
    layerRef.current = group

    return () => {
      if (layerRef.current) {
        map.removeLayer(layerRef.current)
        layerRef.current = null
      }
    }
  }, [
    map, draggable, onDrag,
    poi.lat, poi.lng, propLat, propLng,
    poi.location_drift_m, poi.location_geocoder_rating, poi.location_source,
  ])

  return null
}

// S218 — Fly the map to the active proposal at zoom 20 when proposal-review
// mode opens. Triggered by `nonce` so the operator can re-fly with the same
// coords (e.g. after an inadvertent pan) without remounting the layer.
interface FlyToProposalProps {
  lat: number | null
  lng: number | null
  nonce: number
}
function FlyToProposal({ lat, lng, nonce }: FlyToProposalProps) {
  const map = useMap()
  const lastNonceRef = useRef<number>(-1)
  useEffect(() => {
    if (lat == null || lng == null) return
    if (nonce === lastNonceRef.current) return
    lastNonceRef.current = nonce
    map.flyTo([lat, lng], 20, { duration: 0.6 })
  }, [lat, lng, nonce, map])
  return null
}

// ─── Geocode candidate preview (S188) ───────────────────────────────────────

interface GeocodePreviewLayerProps {
  sourcePoi: PoiRow
  candidate: { lat: number; lng: number; rating: number; normalized_address: string; distance_m: number }
  /** When provided AND showAll is true, render every candidate from every
   *  source POI in the cluster, color-coded by rating. */
  allCandidates?: Array<{
    source_poi_id: string
    source_poi_name: string
    source_lat: number
    source_lng: number
    source_address: string | null
    lat: number
    lng: number
    rating: number
    normalized_address: string
    distance_m: number
  }>
  showAll: boolean
  /** Re-fit map bounds nonce — bumping triggers a fresh fitBounds. */
  fitNonce: number
}

function ratingColor(rating: number): string {
  if (rating <= 5) return '#059669'   // emerald-600
  if (rating <= 30) return '#65a30d'  // lime-600
  if (rating <= 70) return '#d97706'  // amber-600
  return '#a21caf'                    // fuchsia-700 (city-centroid / weak)
}

function GeocodePreviewLayer({
  sourcePoi, candidate, allCandidates, showAll, fitNonce,
}: GeocodePreviewLayerProps) {
  const map = useMap()
  const layerRef = useRef<L.LayerGroup | null>(null)
  const lastFitNonceRef = useRef<number>(-1)

  useEffect(() => {
    if (layerRef.current) {
      map.removeLayer(layerRef.current)
      layerRef.current = null
    }
    const group = L.layerGroup()
    const fitPoints: Array<[number, number]> = []

    const sources = new Map<string, { lat: number; lng: number; name: string }>()
    sources.set(sourcePoi.id, {
      lat: sourcePoi.lat as number,
      lng: sourcePoi.lng as number,
      name: sourcePoi.name,
    })

    if (showAll && allCandidates && allCandidates.length > 0) {
      // Multi-mode: every Tiger candidate across the cluster, colored by rating.
      for (const a of allCandidates) {
        if (!sources.has(a.source_poi_id)) {
          sources.set(a.source_poi_id, {
            lat: a.source_lat, lng: a.source_lng, name: a.source_poi_name,
          })
        }
        const c = ratingColor(a.rating)
        L.polyline(
          [[a.source_lat, a.source_lng], [a.lat, a.lng]],
          { color: c, weight: 2, dashArray: '4 5', opacity: 0.7 },
        ).addTo(group)
        const icon = L.divIcon({
          className: 'geocode-candidate-marker',
          html: `<div style="background:${c};color:#fff;border:2px solid #fff;
                             border-radius:50%;width:22px;height:22px;line-height:18px;
                             text-align:center;font-weight:700;font-size:11px;
                             box-shadow:0 1px 4px rgba(0,0,0,0.4);">r${a.rating}</div>`,
          iconSize: [22, 22], iconAnchor: [11, 11],
        })
        L.marker([a.lat, a.lng], { icon, zIndexOffset: 800 })
          .bindTooltip(
            `<strong>${a.source_poi_name}</strong> · r${a.rating}<br>` +
            `${a.normalized_address}<br>` +
            `${a.distance_m.toLocaleString()} m from stored`,
            { direction: 'top', offset: [0, -12] },
          )
          .addTo(group)
        fitPoints.push([a.lat, a.lng])
      }
    } else {
      // Single-candidate mode (default).
      const candidateIcon = L.divIcon({
        className: 'geocode-candidate-marker',
        html: `<div style="background:#a21caf;color:#fff;border:3px solid #fdf4ff;
                           border-radius:50%;width:30px;height:30px;line-height:24px;
                           text-align:center;font-weight:700;font-size:16px;
                           box-shadow:0 2px 6px rgba(0,0,0,0.4);">?</div>`,
        iconSize: [30, 30], iconAnchor: [15, 15],
      })
      L.marker([candidate.lat, candidate.lng], { icon: candidateIcon, zIndexOffset: 1000 })
        .bindTooltip(
          `Tiger candidate: ${candidate.normalized_address}<br>` +
          `r${candidate.rating} · ${candidate.distance_m} m from stored`,
          { direction: 'top', offset: [0, -15] },
        )
        .addTo(group)
      L.polyline(
        [[sourcePoi.lat as number, sourcePoi.lng as number], [candidate.lat, candidate.lng]],
        { color: '#a21caf', weight: 3, dashArray: '6 6', opacity: 0.85 },
      ).addTo(group)
      fitPoints.push([candidate.lat, candidate.lng])
    }

    // Source POI dots — one per unique source.
    for (const [, s] of sources) {
      L.circleMarker([s.lat, s.lng], {
        radius: 8, color: '#6d28d9', weight: 3, fillColor: '#ddd6fe', fillOpacity: 0.9,
      }).bindTooltip(`${s.name} (stored)`, { direction: 'top', offset: [0, -10] })
        .addTo(group)
      fitPoints.push([s.lat, s.lng])
    }

    group.addTo(map)
    layerRef.current = group

    // Fit when nonce bumps (cancel previous fit, do a new one).
    if (fitNonce !== lastFitNonceRef.current && fitPoints.length > 0) {
      lastFitNonceRef.current = fitNonce
      const bounds = L.latLngBounds(fitPoints)
      map.fitBounds(bounds, { padding: [80, 80], maxZoom: 19 })
    }

    return () => {
      if (layerRef.current) {
        map.removeLayer(layerRef.current)
        layerRef.current = null
      }
    }
  }, [map, sourcePoi.id, sourcePoi.lat, sourcePoi.lng, sourcePoi.name, candidate.lat, candidate.lng, candidate.rating, candidate.normalized_address, candidate.distance_m, showAll, allCandidates, fitNonce])

  return null
}

interface GeocodePreviewPanelProps {
  sourcePoi: PoiRow
  candidate: { lat: number; lng: number; rating: number; normalized_address: string; distance_m: number }
  hasAllCandidates: boolean
  showAll: boolean
  onToggleShowAll: () => void
  focusMap: boolean
  onToggleFocusMap: () => void
  onRefit: () => void
  onAccept?: () => void | Promise<void>
  onIgnore?: () => void | Promise<void>
  onCancel?: () => void
  onValidateStored?: () => void | Promise<void>
  onEditAddress?: () => void
}

// ─── Conflict analyzer ───────────────────────────────────────────────────────
// Looks at the stored address + stored coords + Tiger candidate and produces
// a plain-English diagnosis: which side of the conflict is suspect, what the
// specific failure mode is, and what the operator most likely wants to do.
//
// The four useful failure modes in practice:
//   1. Stored address is empty or vague (no street number) — geocoder cannot
//      do any better than city-level. Fix the address.
//   2. Tiger's rating >= 90 — same outcome (city-centroid fallback) but caused
//      by the geocoder rather than the input. Hide this candidate.
//   3. Strong street-level match within ~50 m — the geocoder confirms the
//      stored coords. Validate without moving.
//   4. Strong street-level match >100 m away — real conflict, needs a human.

interface ConflictAnalysis {
  severity: 'good' | 'warn' | 'bad'
  headline: string
  conflicts: { label: string; detail: string }[]
  recommendation: { label: string; rationale: string; action: 'move' | 'validate' | 'hide' | 'edit' | 'investigate' }
  storedAddress: string | null
  candidateAddress: string
  ratingHint: string
}

function analyzeGeocodeConflict(
  storedLat: number, storedLng: number, storedAddress: string | null,
  candLat: number, candLng: number, candRating: number,
  candNormalizedAddress: string, distanceM: number,
): ConflictAnalysis {
  const ratingHint =
    candRating <= 5  ? `Exact street-level match (r${candRating})` :
    candRating <= 30 ? `Strong street match (r${candRating})` :
    candRating <= 70 ? `Approximate match (r${candRating})` :
    candRating <= 89 ? `Weak match (r${candRating})` :
    `City-centroid fallback (r${candRating}) — Tiger could NOT match street-level`

  const trimmed = (storedAddress ?? '').trim()
  const hasDigit = /\d/.test(trimmed)
  const looksCityOnly = trimmed.length > 0 && trimmed.length < 18 && !hasDigit
  const conflicts: { label: string; detail: string }[] = []

  // Address-side issues
  if (trimmed.length === 0) {
    conflicts.push({
      label: 'Stored address is empty',
      detail: 'POI has no address in the database, so Tiger had nothing to match against and returned the city center.',
    })
  } else if (!hasDigit) {
    conflicts.push({
      label: 'Stored address has no street number',
      detail: `Stored address "${trimmed}" lacks a house number. Tiger needs a street number to land on a building; without one it falls back to the city centroid.`,
    })
  } else if (looksCityOnly) {
    conflicts.push({
      label: 'Stored address is too vague',
      detail: `"${trimmed}" looks like a city/state, not a street address.`,
    })
  }

  // Geocoder-side issues
  if (candRating >= 90) {
    conflicts.push({
      label: 'Tiger fell back to city centroid',
      detail: `Rating ${candRating} means Tiger could not match the address at street level and returned Salem's geographic center. The candidate location is meaningless for placing this POI.`,
    })
  }

  // Address vs normalized address
  if (trimmed.length > 0 && candNormalizedAddress) {
    const norm = candNormalizedAddress.toLowerCase().replace(/[^a-z0-9]/g, '')
    const stored = trimmed.toLowerCase().replace(/[^a-z0-9]/g, '')
    if (candRating <= 70 && norm !== stored && !norm.includes(stored) && !stored.includes(norm)) {
      conflicts.push({
        label: 'Tiger normalized your address differently',
        detail: `Stored: "${trimmed}" → Tiger parsed: "${candNormalizedAddress}". Confirm Tiger picked the right street.`,
      })
    }
  }

  // Distance vs match strength
  if (candRating <= 30 && distanceM > 200) {
    conflicts.push({
      label: 'Stored coords disagree with the address',
      detail: `Tiger is confident in the address (r${candRating}), and it points ${distanceM.toLocaleString()} m away from the stored coords. Either the stored coords are wrong, the address is stale, or there are two locations with similar names.`,
    })
  } else if (candRating <= 30 && distanceM <= 50) {
    conflicts.push({
      label: 'Stored coords agree with the address',
      detail: `Tiger's geocode lands ${distanceM} m from the stored coords — essentially the same place. Stored coords appear correct.`,
    })
  }

  // Recommendation
  let recommendation: ConflictAnalysis['recommendation']
  let severity: ConflictAnalysis['severity'] = 'warn'

  if (trimmed.length === 0 || !hasDigit) {
    severity = 'bad'
    recommendation = {
      label: 'Edit the POI address',
      rationale: 'The stored address is too vague to geocode. Fix the address first, then re-run Geocodes. Hide this candidate so it stops appearing.',
      action: 'edit',
    }
  } else if (candRating >= 90) {
    severity = 'bad'
    recommendation = {
      label: 'Hide this candidate',
      rationale: 'City-centroid fallback. Moving the POI here puts it on top of every other unresolved POI. The address itself looks fine — Tiger may need MA street data tuning, or this is a building Tiger doesn\'t know about.',
      action: 'hide',
    }
  } else if (candRating <= 30 && distanceM <= 50) {
    severity = 'good'
    recommendation = {
      label: 'Validate stored location',
      rationale: 'Tiger confirms the stored coords. Mark them verified without moving the POI.',
      action: 'validate',
    }
  } else if (candRating <= 30 && distanceM <= 200) {
    severity = 'warn'
    recommendation = {
      label: 'Move POI to Tiger\'s match',
      rationale: 'Strong address match within 200 m of stored coords. Likely the correct fix for a small placement drift.',
      action: 'move',
    }
  } else if (candRating <= 30) {
    severity = 'bad'
    recommendation = {
      label: 'Investigate before moving',
      rationale: `Tiger is confident in the address but it\'s ${distanceM.toLocaleString()} m from the stored coords. One of them is wrong. Don\'t auto-accept — open the editor and check.`,
      action: 'investigate',
    }
  } else {
    severity = 'warn'
    recommendation = {
      label: 'Eyeball the map before deciding',
      rationale: `Match strength is ${ratingHint.toLowerCase()}. Look at the dashed line on the map to see if the candidate is on the right block.`,
      action: 'investigate',
    }
  }

  const headline =
    severity === 'good' ? 'Stored coords look correct' :
    severity === 'warn' ? 'Some uncertainty — review needed' :
    'Conflict detected — do NOT auto-accept'

  return {
    severity, headline, conflicts, recommendation,
    storedAddress: trimmed.length > 0 ? trimmed : null,
    candidateAddress: candNormalizedAddress,
    ratingHint,
  }
}

function GeocodePreviewPanel({
  sourcePoi, candidate,
  hasAllCandidates, showAll, onToggleShowAll,
  focusMap, onToggleFocusMap, onRefit,
  onAccept, onIgnore, onCancel,
  onValidateStored, onEditAddress,
}: GeocodePreviewPanelProps) {
  // ESC closes the panel — operator was getting stuck in it.
  useEffect(() => {
    if (!onCancel) return
    const handler = (e: KeyboardEvent) => { if (e.key === 'Escape') onCancel() }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [onCancel])
  const storedAddrRaw = (sourcePoi as { address?: unknown }).address
  const storedAddr = typeof storedAddrRaw === 'string' ? storedAddrRaw : null

  const analysis = analyzeGeocodeConflict(
    sourcePoi.lat as number, sourcePoi.lng as number, storedAddr,
    candidate.lat, candidate.lng, candidate.rating,
    candidate.normalized_address, candidate.distance_m,
  )

  const sevBg =
    analysis.severity === 'good' ? 'bg-emerald-50 border-emerald-300 text-emerald-900' :
    analysis.severity === 'warn' ? 'bg-amber-50 border-amber-300 text-amber-900' :
    'bg-rose-50 border-rose-300 text-rose-900'

  // Action enablement
  const canMove = candidate.rating < 90 // never propose city-centroid as a move target
  const recAction = analysis.recommendation.action

  return (
    <div className="absolute bottom-4 right-4 z-[600]
                    bg-white rounded-lg shadow-xl border border-fuchsia-300
                    px-4 py-3 w-[520px] max-w-[95vw] max-h-[80vh] overflow-y-auto text-sm">
      <div className="flex items-start justify-between gap-2 mb-1">
        <div className="text-xs uppercase tracking-wide text-fuchsia-700 font-semibold">
          Geocode conflict review · {sourcePoi.name}
        </div>
        <button
          type="button"
          onClick={onCancel}
          className="text-slate-400 hover:text-slate-700 text-xl leading-none flex-none -mt-0.5"
          title="Close (Esc)"
          aria-label="Close"
        >
          ×
        </button>
      </div>

      {/* Map view controls */}
      <div className="mb-2 px-2 py-1.5 bg-violet-50 border border-violet-200 rounded flex items-center gap-2 flex-wrap text-[11px]">
        <span className="text-violet-700 font-semibold uppercase tracking-wide">Map view:</span>
        <button
          type="button"
          onClick={onToggleFocusMap}
          className={`px-2 py-0.5 rounded text-[11px] ${
            focusMap
              ? 'bg-violet-600 text-white hover:bg-violet-700'
              : 'bg-white text-violet-700 border border-violet-300 hover:bg-violet-100'
          }`}
          title="Hide every other POI on the map; only the source POI(s) and the geocode candidate(s) stay visible"
        >
          {focusMap ? '✓ Focused' : 'Focus on these coords'}
        </button>
        {hasAllCandidates && (
          <button
            type="button"
            onClick={onToggleShowAll}
            className={`px-2 py-0.5 rounded text-[11px] ${
              showAll
                ? 'bg-fuchsia-600 text-white hover:bg-fuchsia-700'
                : 'bg-white text-fuchsia-700 border border-fuchsia-300 hover:bg-fuchsia-100'
            }`}
            title="Show every Tiger candidate from the cluster on the map at once"
          >
            {showAll ? '✓ Showing all candidates' : 'Show all candidates'}
          </button>
        )}
        <button
          type="button"
          onClick={onRefit}
          className="px-2 py-0.5 rounded text-[11px] bg-white text-slate-700 border border-slate-300 hover:bg-slate-100"
          title="Re-fit the map to the current geocode markers"
        >
          ⤢ Re-fit
        </button>
      </div>

      {/* Diagnosis banner */}
      <div className={`mt-1 px-2 py-1.5 text-xs rounded border ${sevBg}`}>
        <div className="font-semibold">
          {analysis.severity === 'good' ? '✓ ' : analysis.severity === 'warn' ? '⚠ ' : '✗ '}
          {analysis.headline}
        </div>
        <div className="mt-0.5 text-[11px] opacity-90">
          <strong>Recommendation:</strong> {analysis.recommendation.label}.{' '}
          {analysis.recommendation.rationale}
        </div>
      </div>

      {/* Side-by-side details */}
      <div className="mt-2 grid grid-cols-2 gap-2 text-[11px]">
        <div className="border border-violet-300 rounded p-2 bg-violet-50/40">
          <div className="text-[10px] uppercase tracking-wide text-violet-700 font-semibold mb-1">
            Stored (current)
          </div>
          <div className="text-slate-500">Address</div>
          <div className="text-slate-800 break-words mb-1">
            {analysis.storedAddress || <em className="text-rose-700">none</em>}
          </div>
          <div className="text-slate-500">Coords</div>
          <div className="text-slate-800 tabular-nums">
            {(sourcePoi.lat as number).toFixed(5)}, {(sourcePoi.lng as number).toFixed(5)}
          </div>
        </div>
        <div className="border border-fuchsia-300 rounded p-2 bg-fuchsia-50/40">
          <div className="text-[10px] uppercase tracking-wide text-fuchsia-700 font-semibold mb-1 flex items-center gap-1">
            <span>Tiger geocoder</span>
            <span className={`text-[9px] uppercase tracking-wide px-1 py-0.5 rounded ${
              candidate.rating <= 5 ? 'bg-emerald-100 text-emerald-700' :
              candidate.rating <= 30 ? 'bg-lime-100 text-lime-700' :
              candidate.rating <= 70 ? 'bg-amber-100 text-amber-700' :
              'bg-rose-100 text-rose-700'
            }`}>r{candidate.rating}</span>
          </div>
          <div className="text-slate-500">Normalized address</div>
          <div className="text-slate-800 break-words mb-1">{candidate.normalized_address}</div>
          <div className="text-slate-500">Coords</div>
          <div className="text-slate-800 tabular-nums">
            {candidate.lat.toFixed(5)}, {candidate.lng.toFixed(5)}
          </div>
          <div className="text-slate-500 mt-1">Distance from stored</div>
          <div className="text-slate-800 tabular-nums">{candidate.distance_m.toLocaleString()} m</div>
          <div className="text-slate-500 mt-1">Match strength</div>
          <div className="text-slate-800">{analysis.ratingHint}</div>
        </div>
      </div>

      {/* Conflict bullets */}
      {analysis.conflicts.length > 0 && (
        <div className="mt-2 px-2 py-1.5 bg-slate-50 border border-slate-200 rounded">
          <div className="text-[10px] uppercase tracking-wide text-slate-600 font-semibold mb-1">
            What's conflicting ({analysis.conflicts.length})
          </div>
          <ul className="text-[11px] text-slate-700 space-y-1">
            {analysis.conflicts.map((c, i) => (
              <li key={i}>
                <strong>{c.label}.</strong> {c.detail}
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Options */}
      <div className="mt-3 flex flex-col gap-1.5">
        <button
          type="button"
          disabled={!canMove}
          onClick={() => void onAccept?.()}
          className={`w-full px-3 py-2 text-xs rounded text-left disabled:opacity-40 disabled:cursor-not-allowed text-white ${
            recAction === 'move' ? 'bg-emerald-600 hover:bg-emerald-700 ring-2 ring-emerald-300' : 'bg-emerald-600 hover:bg-emerald-700'
          }`}
          title={canMove ? 'Move the POI to this candidate' : 'Disabled: city-centroid candidate is not a real location'}
        >
          ✓ <strong>Move POI here</strong>
          {recAction === 'move' && <span className="ml-1 text-[10px] uppercase tracking-wide bg-white/25 px-1 rounded">recommended</span>}
          <div className="text-[10px] opacity-90 mt-0.5">
            Update <em>{sourcePoi.name}</em> to{' '}
            <span className="tabular-nums">{candidate.lat.toFixed(5)}, {candidate.lng.toFixed(5)}</span>
            {' '}({candidate.distance_m.toLocaleString()} m shift). Sets location_status='verified' implicitly via /move.
            {!canMove && ' — disabled because the candidate is a city-centroid fallback'}
          </div>
        </button>

        {onValidateStored && (
          <button
            type="button"
            onClick={() => void onValidateStored()}
            className={`w-full px-3 py-2 text-xs rounded text-left text-white ${
              recAction === 'validate' ? 'bg-emerald-700 hover:bg-emerald-800 ring-2 ring-emerald-400' : 'bg-emerald-700 hover:bg-emerald-800'
            }`}
            title="Mark the stored coordinates as verified without moving the POI"
          >
            ✓ <strong>Validate stored coords</strong>
            {recAction === 'validate' && <span className="ml-1 text-[10px] uppercase tracking-wide bg-white/25 px-1 rounded">recommended</span>}
            <div className="text-[10px] opacity-90 mt-0.5">
              Keep <em>{sourcePoi.name}</em> at{' '}
              <span className="tabular-nums">{(sourcePoi.lat as number).toFixed(5)}, {(sourcePoi.lng as number).toFixed(5)}</span>
              {' '}and stamp location_status='verified' / location_verified_at=now.
            </div>
          </button>
        )}

        <button
          type="button"
          onClick={() => void onIgnore?.()}
          className={`w-full px-3 py-2 text-xs rounded text-white text-left ${
            recAction === 'hide' ? 'bg-rose-600 hover:bg-rose-700 ring-2 ring-rose-300' : 'bg-rose-600 hover:bg-rose-700'
          }`}
          title="Add this candidate to the geocode blacklist for this POI"
        >
          ✗ <strong>Hide this candidate</strong>
          {recAction === 'hide' && <span className="ml-1 text-[10px] uppercase tracking-wide bg-white/25 px-1 rounded">recommended</span>}
          <div className="text-[10px] opacity-90 mt-0.5">
            Mark <span className="tabular-nums">{candidate.lat.toFixed(5)}, {candidate.lng.toFixed(5)}</span>
            {' '}as wrong for <em>{sourcePoi.name}</em>. Adds a row to <code>salem_geocode_blacklist</code>;
            this exact lat/lng stops appearing in future Geocodes lookups for this POI.
          </div>
        </button>

        {onEditAddress && (
          <button
            type="button"
            onClick={() => onEditAddress()}
            className={`w-full px-3 py-2 text-xs rounded text-left text-white ${
              recAction === 'edit' ? 'bg-slate-700 hover:bg-slate-800 ring-2 ring-slate-400' : 'bg-slate-700 hover:bg-slate-800'
            }`}
            title="Open the POI editor focused on the address field"
          >
            ✎ <strong>Edit POI address</strong>
            {recAction === 'edit' && <span className="ml-1 text-[10px] uppercase tracking-wide bg-white/25 px-1 rounded">recommended</span>}
            <div className="text-[10px] opacity-90 mt-0.5">
              Open <em>{sourcePoi.name}</em> in the POI editor. Use this when the stored address
              is wrong/incomplete and the geocoder needs better input before it can help.
            </div>
          </button>
        )}

        <button
          type="button"
          onClick={onCancel}
          className="w-full px-3 py-2 text-xs rounded bg-slate-200 hover:bg-slate-300 text-slate-700 text-left"
        >
          ↩ <strong>Cancel</strong>
          <div className="text-[10px] opacity-80 mt-0.5">
            Close this preview without changing anything. The POI, the candidate, and the
            blacklist all stay as they are.
          </div>
        </button>
      </div>
    </div>
  )
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
  selectedPoiMinZoom,
  onPoiSelect,
  onPoiMoved,
  categoryFilter,
  onClearCategoryFilter,
  categories,
  lintIdFilter,
  lintIdFilterLabel,
  onClearLintIdFilter,
  searchIdFilter,
  searchIdFilterLabel,
  activeTour,
  tourModeFilter,
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
  geocodePreview,
  onGeocodePreviewAccept,
  onGeocodePreviewIgnore,
  onGeocodePreviewCancel,
  onGeocodePreviewValidateStored,
  onGeocodePreviewEditAddress,
  proposalReview,
  onProposalDrag,
}: AdminMapProps) {
  // S188 — geocode-preview view mode. `focusMap` hides every POI marker
  // outside the cluster; `showAll` renders every Tiger candidate at once.
  // `fitNonce` is bumped to trigger a fresh fitBounds (toggle Re-fit).
  const [showAllCandidates, setShowAllCandidates] = useState(false)
  const [focusMap, setFocusMap] = useState(true)
  const [fitNonce, setFitNonce] = useState(0)
  // Reset to defaults when a new preview opens (different sourcePoi).
  const previewKey = geocodePreview
    ? `${geocodePreview.sourcePoi.id}|${geocodePreview.candidate.lat.toFixed(5)},${geocodePreview.candidate.lng.toFixed(5)}`
    : null
  const lastPreviewKeyRef = useRef<string | null>(null)
  useEffect(() => {
    if (previewKey !== lastPreviewKeyRef.current) {
      lastPreviewKeyRef.current = previewKey
      if (previewKey) {
        setShowAllCandidates(false)
        setFocusMap(true)
        setFitNonce(n => n + 1)
      }
    }
  }, [previewKey])

  const filteredPois = useMemo(() => {
    if (!pois) return pois
    let out = pois
    if (categoryFilter) {
      // S190 — categoryFilter may be a real DB category or a synthetic
      // "<CATEGORY>:<tag>" key (currently HISTORICAL_BUILDINGS:mhc /
      // HISTORICAL_BUILDINGS:non_mhc). Delegate to the shared predicate so
      // both buckets render the right subset on the map.
      out = out.filter((p) => categoryFilterMatches(p, categoryFilter))
    }
    if (lintIdFilter && lintIdFilter.size > 0) {
      out = out.filter((p) => lintIdFilter.has(p.id))
    }
    // S221 — search-driven filter mirrors the PoiTree search box.
    if (searchIdFilter) {
      out = out.filter((p) => searchIdFilter.has(p.id))
    }
    // S214 — Tour-mode preview. When a tour is active and at least one
    // tour-mode class is enabled, narrow markers to the union of those
    // classes. Mirrors the device's S186 narration gate so the operator
    // sees exactly which POIs will narrate during this tour. Tour stops
    // still render via TourStopLayer regardless of this filter.
    if (activeTour && tourModeFilter) {
      const { tourPois, histLandmark, civic } = tourModeFilter
      if (tourPois || histLandmark || civic) {
        out = out.filter((p) => {
          if (tourPois && p.is_tour_poi) return true
          if (histLandmark && isMassgisHistorical(p)) return true
          if (civic && p.is_civic_poi) return true
          return false
        })
      } else {
        // All three off → show no POIs (operator explicitly turned everything off).
        out = []
      }
    }
    // When the geocode preview is open AND focusMap is on, hide every POI
    // marker EXCEPT the source POI (single mode) or the source POI + every
    // dupe contributing a candidate (show-all mode).
    if (geocodePreview && focusMap) {
      const keep = new Set<string>([geocodePreview.sourcePoi.id])
      if (showAllCandidates && geocodePreview.allCandidates) {
        for (const a of geocodePreview.allCandidates) keep.add(a.source_poi_id)
      }
      out = out.filter(p => keep.has(p.id))
    }
    return out
  }, [pois, categoryFilter, lintIdFilter, searchIdFilter, activeTour, tourModeFilter, geocodePreview, focusMap, showAllCandidates])
  const [pending, setPending] = useState<PendingMove | null>(null)
  const [moveBusy, setMoveBusy] = useState(false)
  const [moveError, setMoveError] = useState<string | null>(null)

  // Tour-mode (S174) — drag-to-save state for waypoints.
  const [pendingStop, setPendingStop] = useState<PendingStopMove | null>(null)
  const [stopBusy, setStopBusy] = useState(false)
  const [stopError, setStopError] = useState<string | null>(null)

  // S214 — snap-edge preview for the pending drag. Fetched off the
  // admin/salem/snap-edge endpoint when the operator drops a marker; shown
  // inside MoveConfirm so they see exactly which TigerLine edge the waypoint
  // will end up bound to before committing.
  const [pendingSnap, setPendingSnap] = useState<{
    fullname: string | null
    snap_m: number
    edge_id: number
    snap_lat: number
    snap_lng: number
  } | null>(null)
  useEffect(() => {
    if (!pendingStop) { setPendingSnap(null); return }
    let cancelled = false
    void (async () => {
      try {
        const res = await fetch(
          `/api/admin/salem/snap-edge?lat=${pendingStop.to.lat}&lng=${pendingStop.to.lng}`,
          { credentials: 'same-origin' },
        )
        if (cancelled) return
        if (!res.ok) { setPendingSnap(null); return }
        const j = await res.json()
        setPendingSnap({
          fullname: j.fullname ?? null,
          snap_m: typeof j.snap_m === 'number' ? j.snap_m : 0,
          edge_id: typeof j.edge_id === 'number' ? j.edge_id : 0,
          snap_lat: j.snap_lat,
          snap_lng: j.snap_lng,
        })
      } catch {
        if (!cancelled) setPendingSnap(null)
      }
    })()
    return () => { cancelled = true }
  }, [pendingStop])

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
      let body: { lat?: number; lng?: number } | null = null
      try { body = await res.json() } catch { /* not json */ }
      if (!res.ok) {
        const errMsg = body && (body as { error?: string }).error
          ? `${res.status} ${(body as { error?: string }).error}`
          : `${res.status} ${res.statusText}`
        throw new Error(errMsg)
      }
      // S214 — server may have snapped the coords. Reposition the marker to
      // the actually-stored location so it visually lands on the road instead
      // of the operator's drop point. Fall back to drop coords if response
      // didn't include lat/lng (older server, defensive).
      const finalLat = typeof body?.lat === 'number' ? body.lat : pendingStop.to.lat
      const finalLng = typeof body?.lng === 'number' ? body.lng : pendingStop.to.lng
      pendingStop.marker.setLatLng([finalLat, finalLng])
      onStopMoved(pendingStop.stop.stop_id, finalLat, finalLng)
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

  // S190 — label lookup driven by salem_poi_categories so the legend
  // and filter banner stay in sync with the tree + the editor dropdown.
  const labelLookup = useMemo(
    () => makeCategoryLabelLookup(categories ?? []),
    [categories],
  )

  const categoryCounts = useMemo(() => {
    if (!pois) return []
    const counts = new Map<string, number>()
    for (const p of pois) {
      if (p.deleted_at) continue
      // S190 — mirror the PoiTree split for HISTORICAL_BUILDINGS so the
      // legend shows "Historic Buildings" and "POI Hist. Landmark"
      // separately. Underlying DB rows are unchanged.
      let cat = (p.category as string) || '(uncategorized)'
      if (cat === 'HISTORICAL_BUILDINGS') {
        cat = isMassgisHistorical(p) ? HIST_LANDMARK_FILTER : HIST_BUILDINGS_FILTER
      }
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
        <FlyToSelected selectedPoi={selectedPoi} minZoom={selectedPoiMinZoom} />
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
        {geocodePreview && (
          <GeocodePreviewLayer
            sourcePoi={geocodePreview.sourcePoi}
            candidate={geocodePreview.candidate}
            allCandidates={geocodePreview.allCandidates}
            showAll={showAllCandidates}
            fitNonce={fitNonce}
          />
        )}
        {/* S218 — proposal review mode: draggable pin + auto-fly to z20 */}
        {!geocodePreview && proposalReview && (
          <>
            <ProposalPreviewLayer
              poi={proposalReview.poi}
              draggable
              dragLat={proposalReview.dragLat}
              dragLng={proposalReview.dragLng}
              onDrag={onProposalDrag}
            />
            <FlyToProposal
              lat={proposalReview.dragLat ?? proposalReview.poi.lat_proposed ?? null}
              lng={proposalReview.dragLng ?? proposalReview.poi.lng_proposed ?? null}
              nonce={proposalReview.flyNonce}
            />
          </>
        )}
        {/* Read-only proposal preview (selected POI has a proposal but
            we're not in review mode) */}
        {!geocodePreview
          && !proposalReview
          && selectedPoi
          && selectedPoi.poi.lat_proposed != null
          && selectedPoi.poi.lng_proposed != null && (
            <ProposalPreviewLayer poi={selectedPoi.poi} />
          )}
      </MapContainer>
      {TILE_PROVIDERS.length > 1 && (
        <TileProviderPicker
          providerId={tileProviderId}
          onChange={setTileProviderId}
        />
      )}
      <Legend categoryCounts={categoryCounts} labelLookup={labelLookup} />
      {categoryFilter && (
        <div className="absolute top-2 left-1/2 -translate-x-1/2 z-[500]
                        bg-indigo-600 text-white text-sm px-3 py-1 rounded-full shadow
                        flex items-center gap-2">
          <span>Filtering: <strong>{labelLookup(categoryFilter)}</strong></span>
          <button
            type="button"
            onClick={onClearCategoryFilter}
            className="ml-1 text-white/80 hover:text-white underline text-xs"
          >
            clear
          </button>
        </div>
      )}
      {lintIdFilter && lintIdFilter.size > 0 && (
        <div className="absolute top-12 left-1/2 -translate-x-1/2 z-[500]
                        bg-amber-600 text-white text-sm px-3 py-1 rounded-full shadow
                        flex items-center gap-2">
          <span>
            Lint filter: <strong>{lintIdFilterLabel || `${lintIdFilter.size} POIs`}</strong>
          </span>
          <button
            type="button"
            onClick={onClearLintIdFilter}
            className="ml-1 text-white/80 hover:text-white underline text-xs"
          >
            clear
          </button>
        </div>
      )}
      {searchIdFilter && (
        <div className="absolute top-2 right-2 z-[500]
                        bg-sky-700 text-white text-xs px-3 py-1 rounded-full shadow">
          Search: <strong>“{searchIdFilterLabel}”</strong>
          <span className="ml-1 text-white/80 tabular-nums">
            ({searchIdFilter.size.toLocaleString()})
          </span>
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
      {geocodePreview && (
        <GeocodePreviewPanel
          sourcePoi={geocodePreview.sourcePoi}
          candidate={geocodePreview.candidate}
          hasAllCandidates={(geocodePreview.allCandidates?.length ?? 0) > 0}
          showAll={showAllCandidates}
          onToggleShowAll={() => {
            setShowAllCandidates(v => !v)
            setFitNonce(n => n + 1)
          }}
          focusMap={focusMap}
          onToggleFocusMap={() => setFocusMap(v => !v)}
          onRefit={() => setFitNonce(n => n + 1)}
          onAccept={onGeocodePreviewAccept}
          onIgnore={onGeocodePreviewIgnore}
          onCancel={onGeocodePreviewCancel}
          onValidateStored={onGeocodePreviewValidateStored}
          onEditAddress={onGeocodePreviewEditAddress}
        />
      )}
      {pendingStop && (
        <MoveConfirm
          title="Confirm waypoint move"
          subjectName={pendingStop.label}
          subjectId={`stop #${pendingStop.stop.stop_id} · order ${pendingStop.stop.stop_order}`}
          from={pendingStop.from}
          to={pendingStop.to}
          snap={
            // Free waypoints (no poi_id) get edge-snapped on the server.
            // POI-based stops are not snapped, so we hide the snap panel.
            pendingStop.stop.poi_id == null && pendingSnap ? pendingSnap : null
          }
          busy={stopBusy}
          error={stopError}
          onConfirm={handleStopConfirm}
          onCancel={handleStopCancel}
        />
      )}
    </div>
  )
}
