// AdminMap — Phase 9P.B Step 9P.9
//
// Center-pane Leaflet map for the Salem POI admin tool. Renders all 1,720
// POIs across the three kinds (tour, business, narration) as a single
// clustered layer with kind-specific colors. Click a marker → emits a
// selection back to AdminLayout (which will open the edit dialog in 9P.10);
// drag a marker → confirm modal → POSTs to /admin/salem/pois/:kind/:id/move.
// Externally driven pan/zoom: when the parent mutates `selectedPoi`, the map
// flies to its position. The tree click is the primary driver of this.
//
// ─── Why imperative cluster layer instead of react-leaflet-markercluster ────
// react-leaflet-markercluster v3+ is built for react-leaflet v4. We're on
// react-leaflet v5 which introduced internal API changes the wrapper hasn't
// caught up to. The official escape hatch is to grab the leaflet map via
// useMap() and drive the cluster group imperatively. That's also strictly
// fewer dependencies (3 high-severity audit warnings already in the tree —
// not adding a fourth wrapper just for an effect we can write in 30 lines).
//
// ─── Selection model ───────────────────────────────────────────────────────
// Two way: tree-click sets `selectedPoi` in AdminLayout, AdminMap reacts by
// flying to it; map-click on a marker fires `onPoiSelect` which AdminLayout
// also routes into `selectedPoi`. The selected marker is highlighted with a
// gold ring so the user can find it after a fly-to.
//
// ─── Drag-to-move flow ─────────────────────────────────────────────────────
// 1. User starts dragging a marker (each marker has dragging.enable())
// 2. dragend fires; AdminMap captures (kind, id, oldLat/Lng, newLat/Lng)
// 3. A confirm modal renders over the map showing distance + before/after
// 4. Confirm: POST /admin/salem/pois/:kind/:id/move; on success the parent's
//    `onPoiMoved` callback updates the shared byKind so the marker stays put
//    Cancel: marker is reverted to its original position via marker.setLatLng

import { useEffect, useMemo, useRef, useState, useCallback } from 'react'
import { MapContainer, TileLayer, useMap } from 'react-leaflet'
import L from 'leaflet'
import 'leaflet.markercluster'
import type { PoiKind, PoiRow, PoiSelection } from './PoiTree'

// ─── Constants ───────────────────────────────────────────────────────────────

// Salem center — Old Town Hall area, ~equidistant from PEM, Common, Witch
// House. Initial zoom 14 fits the downtown POI footprint comfortably.
const SALEM_CENTER: [number, number] = [42.5197, -70.8967]
const INITIAL_ZOOM = 14

// Kind → color. These also serve as the cluster legend.
const KIND_COLORS: Record<PoiKind, string> = {
  tour: '#dc2626',       // red-600 — historical/curated, fewest, most prominent
  business: '#2563eb',   // blue-600 — merchants
  narration: '#059669',  // emerald-600 — ambient narration points
}

const KIND_LABELS: Record<PoiKind, string> = {
  tour: 'Tour',
  business: 'Business',
  narration: 'Narration',
}

// Tile sources mirror the main app's MapView so the admin operator sees
// roughly the same basemap they're used to.
const TILE_URL = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
const TILE_ATTR = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'

// ─── Marker icon factory ─────────────────────────────────────────────────────
// L.divIcon with an inline SVG dot — fast, no asset loading, easy to recolor.
// Selected markers get a gold halo. We cache by (kind, selected) to avoid
// allocating a fresh DivIcon for every marker.

const iconCache = new Map<string, L.DivIcon>()

function poiIcon(kind: PoiKind, selected: boolean): L.DivIcon {
  const key = `${kind}|${selected ? 'sel' : 'norm'}`
  let icon = iconCache.get(key)
  if (icon) return icon
  const fill = KIND_COLORS[kind]
  const size = selected ? 18 : 12
  const stroke = selected ? '#facc15' : '#ffffff'
  const strokeW = selected ? 3 : 1.5
  const html = `
    <svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" xmlns="http://www.w3.org/2000/svg">
      <circle cx="${size / 2}" cy="${size / 2}" r="${size / 2 - strokeW / 2}"
              fill="${fill}" stroke="${stroke}" stroke-width="${strokeW}"/>
    </svg>`
  icon = L.divIcon({
    html,
    className: 'admin-poi-marker', // unset background/border via global CSS reset below
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  })
  iconCache.set(key, icon)
  return icon
}

// ─── Types ───────────────────────────────────────────────────────────────────

export interface PendingMove {
  kind: PoiKind
  poi: PoiRow
  from: { lat: number; lng: number }
  to: { lat: number; lng: number }
  /** Reference to the leaflet marker so we can revert it on cancel. */
  marker: L.Marker
}

interface AdminMapProps {
  /** Full POI dataset (loaded once by PoiTree, shared via AdminLayout). */
  byKind: Record<PoiKind, PoiRow[]> | null
  /** Currently selected POI (for fly-to + highlight). */
  selectedPoi: PoiSelection | null
  /** Fired when the user clicks a marker. */
  onPoiSelect: (selection: PoiSelection) => void
  /** Fired when a drag-to-move is confirmed and the API call succeeds. */
  onPoiMoved: (kind: PoiKind, id: string, lat: number, lng: number) => void
}

// ─── Marker layer (imperative, child of MapContainer) ────────────────────────
// This component lives inside the <MapContainer>. It uses useMap() to grab
// the leaflet map, then imperatively builds and updates the cluster group.

interface MarkerLayerProps {
  byKind: Record<PoiKind, PoiRow[]>
  selectedKey: string | null
  onPoiSelect: (selection: PoiSelection) => void
  onDragEnd: (move: PendingMove) => void
}

function MarkerLayer({
  byKind,
  selectedKey,
  onPoiSelect,
  onDragEnd,
}: MarkerLayerProps) {
  const map = useMap()
  const clusterGroupRef = useRef<L.MarkerClusterGroup | null>(null)
  // markersByKey lets us recolor a single marker on selection change without
  // rebuilding the whole cluster (rebuild = ~1,700 marker allocs + reflow).
  const markersByKeyRef = useRef<Map<string, { marker: L.Marker; kind: PoiKind }>>(new Map())

  // Build (or rebuild) the cluster group when the dataset changes.
  useEffect(() => {
    // Tear down any prior layer first
    if (clusterGroupRef.current) {
      map.removeLayer(clusterGroupRef.current)
      clusterGroupRef.current = null
      markersByKeyRef.current.clear()
    }

    // disableClusteringAtZoom: 18 → at the deepest zooms the operator wants
    // every individual POI visible (not folded into a cluster) so they can
    // pick the exact one to drag. spiderfyOnMaxZoom handles overlap below.
    const cluster = L.markerClusterGroup({
      showCoverageOnHover: false,
      spiderfyOnMaxZoom: true,
      disableClusteringAtZoom: 18,
      maxClusterRadius: 50,
    }) as L.MarkerClusterGroup

    const allMarkers: L.Marker[] = []

    for (const kind of ['tour', 'business', 'narration'] as PoiKind[]) {
      const rows = byKind[kind] ?? []
      for (const poi of rows) {
        // Skip soft-deleted from the map (we still keep them in the tree
        // behind the toggle, but the map is the operator's spatial workspace
        // and clutter from tombstones is unhelpful here)
        if (poi.deleted_at) continue
        if (typeof poi.lat !== 'number' || typeof poi.lng !== 'number') continue
        if (!Number.isFinite(poi.lat) || !Number.isFinite(poi.lng)) continue

        const key = `${kind}:${poi.id}`
        const isSelected = key === selectedKey

        const marker = L.marker([poi.lat, poi.lng], {
          icon: poiIcon(kind, isSelected),
          draggable: true,
          autoPan: true,
          title: `${KIND_LABELS[kind]}: ${poi.name}`,
        })

        marker.on('click', () => {
          onPoiSelect({ kind, poi })
        })

        // Capture the original position at dragstart so we can revert on
        // cancel without re-querying the row (which the map state may have
        // already mutated)
        let dragOrigin = { lat: poi.lat, lng: poi.lng }
        marker.on('dragstart', () => {
          const ll = marker.getLatLng()
          dragOrigin = { lat: ll.lat, lng: ll.lng }
        })
        marker.on('dragend', () => {
          const ll = marker.getLatLng()
          // No-op move (user grabbed and released without moving)
          if (ll.lat === dragOrigin.lat && ll.lng === dragOrigin.lng) return
          onDragEnd({
            kind,
            poi,
            from: dragOrigin,
            to: { lat: ll.lat, lng: ll.lng },
            marker,
          })
        })

        allMarkers.push(marker)
        markersByKeyRef.current.set(key, { marker, kind })
      }
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
    // Rebuild on dataset change. selectedKey is handled in a separate effect
    // below to avoid full rebuilds on every selection click.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [byKind, map])

  // Recolor only the (de)selected markers when selection changes.
  const previousSelectedKeyRef = useRef<string | null>(null)
  useEffect(() => {
    const prev = previousSelectedKeyRef.current
    if (prev && prev !== selectedKey) {
      const entry = markersByKeyRef.current.get(prev)
      if (entry) entry.marker.setIcon(poiIcon(entry.kind, false))
    }
    if (selectedKey) {
      const entry = markersByKeyRef.current.get(selectedKey)
      if (entry) entry.marker.setIcon(poiIcon(entry.kind, true))
    }
    previousSelectedKeyRef.current = selectedKey
  }, [selectedKey])

  return null
}

// ─── Pan/zoom controller (also a MapContainer child) ─────────────────────────
// Watches selectedPoi and flies to it when the parent updates it. Uses
// flyTo at zoom ≥17 so the operator can see the marker plainly. Skips the
// fly when the change came from a marker click (the map is already there).

interface FlyToProps {
  selectedPoi: PoiSelection | null
}

function FlyToSelected({ selectedPoi }: FlyToProps) {
  const map = useMap()
  const lastIdRef = useRef<string | null>(null)
  useEffect(() => {
    if (!selectedPoi) return
    const key = `${selectedPoi.kind}:${selectedPoi.poi.id}`
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

// ─── Move-confirm modal ──────────────────────────────────────────────────────

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
          {KIND_LABELS[pending.kind]} POI{' '}
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

// ─── Legend (top-right corner) ───────────────────────────────────────────────

function Legend({ counts }: { counts: { tour: number; business: number; narration: number } }) {
  return (
    <div className="absolute top-2 right-2 z-[100] bg-white/95 border border-slate-300 rounded shadow px-3 py-2 text-xs">
      <div className="font-semibold text-slate-700 mb-1">Legend</div>
      {(['tour', 'business', 'narration'] as PoiKind[]).map((k) => (
        <div key={k} className="flex items-center gap-2">
          <span
            className="inline-block w-3 h-3 rounded-full border border-white"
            style={{ background: KIND_COLORS[k] }}
          />
          <span className="text-slate-700">{KIND_LABELS[k]}</span>
          <span className="ml-auto tabular-nums text-slate-500">{counts[k]}</span>
        </div>
      ))}
    </div>
  )
}

// ─── Top-level component ─────────────────────────────────────────────────────

export function AdminMap({
  byKind,
  selectedPoi,
  onPoiSelect,
  onPoiMoved,
}: AdminMapProps) {
  const [pending, setPending] = useState<PendingMove | null>(null)
  const [moveBusy, setMoveBusy] = useState(false)
  const [moveError, setMoveError] = useState<string | null>(null)

  const selectedKey = selectedPoi
    ? `${selectedPoi.kind}:${selectedPoi.poi.id}`
    : null

  const counts = useMemo(() => {
    if (!byKind) return { tour: 0, business: 0, narration: 0 }
    const visible = (rows: PoiRow[]) => rows.filter((r) => !r.deleted_at).length
    return {
      tour: visible(byKind.tour),
      business: visible(byKind.business),
      narration: visible(byKind.narration),
    }
  }, [byKind])

  const handleDragEnd = useCallback((move: PendingMove) => {
    setPending(move)
    setMoveError(null)
  }, [])

  const handleCancel = useCallback(() => {
    if (pending) {
      // Snap the marker back to its origin
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
        `/api/admin/salem/pois/${encodeURIComponent(pending.kind)}/${encodeURIComponent(pending.poi.id)}/move`,
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
          // body wasn't JSON; keep status text
        }
        throw new Error(msg)
      }
      // Success: notify parent so the shared byKind reflects the new lat/lng
      onPoiMoved(pending.kind, pending.poi.id, pending.to.lat, pending.to.lng)
      setPending(null)
    } catch (e) {
      // On failure, also revert the marker so the map stays consistent with PG
      pending.marker.setLatLng([pending.from.lat, pending.from.lng])
      setMoveError(e instanceof Error ? e.message : String(e))
    } finally {
      setMoveBusy(false)
    }
  }, [pending, onPoiMoved])

  if (!byKind) {
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
        className="h-full w-full"
        zoomControl={true}
      >
        <TileLayer url={TILE_URL} attribution={TILE_ATTR} maxZoom={19} />
        <MarkerLayer
          byKind={byKind}
          selectedKey={selectedKey}
          onPoiSelect={onPoiSelect}
          onDragEnd={handleDragEnd}
        />
        <FlyToSelected selectedPoi={selectedPoi} />
      </MapContainer>
      <Legend counts={counts} />
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
