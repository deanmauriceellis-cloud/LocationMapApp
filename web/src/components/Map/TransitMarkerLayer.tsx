// (C) Dean Maurice Ellis, 2026 - Module TransitMarkerLayer.tsx
import { useMemo, useState } from 'react'
import { CircleMarker, Tooltip, useMap, useMapEvents } from 'react-leaflet'
import { getRouteColor, vehicleStatusLabel } from '@/config/transit'
import type { MbtaVehicle, MbtaStop } from '@/lib/types'

const LABEL_ZOOM = 16
const STATION_ZOOM = 12
const BUS_STOP_ZOOM = 15

interface Props {
  trains: MbtaVehicle[]
  subway: MbtaVehicle[]
  buses: MbtaVehicle[]
  stations: MbtaStop[]
  busStops: MbtaStop[]
  trainsVisible: boolean
  subwayVisible: boolean
  busesVisible: boolean
  selectedVehicleId?: string | null
  onVehicleClick?: (v: MbtaVehicle) => void
  onStopClick?: (s: MbtaStop) => void
}

export function TransitMarkerLayer({
  trains, subway, buses, stations, busStops,
  trainsVisible, subwayVisible, busesVisible,
  selectedVehicleId,
  onVehicleClick, onStopClick,
}: Props) {
  const map = useMap()
  const [zoom, setZoom] = useState(map.getZoom())
  useMapEvents({ zoomend: () => setZoom(map.getZoom()) })
  const showLabels = zoom >= LABEL_ZOOM
  const showStations = zoom >= STATION_ZOOM && (trainsVisible || subwayVisible)
  const showBusStops = zoom >= BUS_STOP_ZOOM && busesVisible

  // Combine all visible vehicles
  const allVehicles = useMemo(() => {
    const result: MbtaVehicle[] = []
    if (trainsVisible) result.push(...trains)
    if (subwayVisible) result.push(...subway)
    if (busesVisible) result.push(...buses)
    return result
  }, [trains, subway, buses, trainsVisible, subwayVisible, busesVisible])

  if (allVehicles.length === 0 && !showStations && !showBusStops) return null

  return (
    <>
      {/* Station dots */}
      {showStations && stations.map(s => (
        <CircleMarker
          key={`stn-${s.id}`}
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

      {/* Bus stop dots */}
      {showBusStops && busStops.map(s => (
        <CircleMarker
          key={`bus-${s.id}`}
          center={[s.lat, s.lon]}
          radius={6}
          pathOptions={{
            color: '#4F46E5',
            fillColor: '#A5B4FC',
            fillOpacity: 0.85,
            weight: 2,
          }}
          eventHandlers={onStopClick ? {
            click: () => onStopClick(s),
          } : undefined}
        >
          {showLabels ? (
            <Tooltip permanent direction="right" offset={[6, 0]} className="poi-label">
              {s.name}
            </Tooltip>
          ) : (
            <Tooltip direction="top" offset={[0, -6]} className="poi-label">
              {s.name}
            </Tooltip>
          )}
        </CircleMarker>
      ))}

      {/* Vehicle markers — CircleMarker with route color */}
      {allVehicles.map(v => {
        const color = getRouteColor(v.routeId, v.routeType)
        const isBus = v.routeType === 3
        const age = v.updatedAt ? Date.now() - new Date(v.updatedAt).getTime() : 0
        const stale = age > 120_000
        const isSelected = v.id === selectedVehicleId
        const label = `${v.routeId} ${v.headsign}`.trim()
        const detailLabel = `${label} — ${vehicleStatusLabel(v.status)} ${v.stopName}`

        return (
          <CircleMarker
            key={`veh-${v.id}-${isSelected ? 's' : 'u'}`}
            center={[v.lat, v.lon]}
            radius={isSelected ? 10 : isBus ? 6 : 8}
            pathOptions={{
              color: isSelected ? '#0d9488' : '#fff',
              fillColor: color,
              fillOpacity: stale ? 0.4 : 0.9,
              weight: isSelected ? 3 : 2,
            }}
            eventHandlers={onVehicleClick ? {
              click: () => onVehicleClick(v),
            } : undefined}
          >
            {/* Selected vehicle always shows permanent detail label */}
            {isSelected && (
              <Tooltip permanent direction="right" offset={[10, 0]} className="poi-label">
                {detailLabel}
              </Tooltip>
            )}
            {/* Non-selected: permanent label at zoom 16+, hover tooltip otherwise */}
            {!isSelected && showLabels && (
              <Tooltip permanent direction="right" offset={[8, 0]} className="poi-label">
                {label}
              </Tooltip>
            )}
            {!isSelected && !showLabels && (
              <Tooltip direction="top" offset={[0, -8]} className="poi-label">
                {detailLabel}
              </Tooltip>
            )}
          </CircleMarker>
        )
      })}
    </>
  )
}
