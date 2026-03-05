// (C) Dean Maurice Ellis, 2026 - Module TransitMarkerLayer.tsx
import { useMemo } from 'react'
import { Marker, CircleMarker, Tooltip, useMap } from 'react-leaflet'
import L from 'leaflet'
import { getRouteColor, trainIconHtml, busIconHtml } from '@/config/transit'
import type { MbtaVehicle, MbtaStop } from '@/lib/types'

const LABEL_ZOOM = 16
const STATION_ZOOM = 12

interface Props {
  trains: MbtaVehicle[]
  subway: MbtaVehicle[]
  buses: MbtaVehicle[]
  stations: MbtaStop[]
  trainsVisible: boolean
  subwayVisible: boolean
  busesVisible: boolean
  onVehicleClick?: (v: MbtaVehicle) => void
  onStopClick?: (s: MbtaStop) => void
}

export function TransitMarkerLayer({
  trains, subway, buses, stations,
  trainsVisible, subwayVisible, busesVisible,
  onVehicleClick, onStopClick,
}: Props) {
  const map = useMap()
  const zoom = map.getZoom()
  const showLabels = zoom >= LABEL_ZOOM
  const showStations = zoom >= STATION_ZOOM && (trainsVisible || subwayVisible)

  // Combine all visible vehicles
  const allVehicles = useMemo(() => {
    const result: MbtaVehicle[] = []
    if (trainsVisible) result.push(...trains)
    if (subwayVisible) result.push(...subway)
    if (busesVisible) result.push(...buses)
    return result
  }, [trains, subway, buses, trainsVisible, subwayVisible, busesVisible])

  const vehicleMarkers = useMemo(() => {
    return allVehicles.map(v => {
      const color = getRouteColor(v.routeId, v.routeType)
      const label = showLabels ? `${v.routeId} ${v.headsign}`.trim() : ''
      const isBus = v.routeType === 3
      const html = isBus
        ? busIconHtml(color, v.bearing, label)
        : trainIconHtml(color, v.bearing, label)

      // Staleness check: dim if > 2min old
      const age = v.updatedAt ? Date.now() - new Date(v.updatedAt).getTime() : 0
      const stale = age > 120_000

      const sz = isBus ? 18 : 20
      const icon = L.divIcon({
        html: stale ? `<div style="opacity:0.5">${html}</div>` : html,
        className: '',
        iconSize: [sz, sz],
        iconAnchor: [sz / 2, sz / 2],
      })
      return { v, icon }
    })
  }, [allVehicles, showLabels])

  if (allVehicles.length === 0 && !showStations) return null

  return (
    <>
      {/* Station dots */}
      {showStations && stations.map(s => (
        <CircleMarker
          key={s.id}
          center={[s.lat, s.lon]}
          radius={6}
          pathOptions={{
            color: '#374151',
            fillColor: '#F3F4F6',
            fillOpacity: 0.95,
            weight: 2,
          }}
          eventHandlers={onStopClick ? {
            click: () => onStopClick(s),
          } : undefined}
        >
          <Tooltip direction="top" offset={[0, -6]} className="poi-label">
            {s.name}
          </Tooltip>
        </CircleMarker>
      ))}

      {/* Vehicle markers */}
      {vehicleMarkers.map(({ v, icon }) => (
        <Marker
          key={v.id}
          position={[v.lat, v.lon]}
          icon={icon}
          eventHandlers={onVehicleClick ? {
            click: () => onVehicleClick(v),
          } : undefined}
        />
      ))}
    </>
  )
}
