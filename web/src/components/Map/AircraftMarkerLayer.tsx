// (C) Dean Maurice Ellis, 2026 - Module AircraftMarkerLayer.tsx
import { useMemo } from 'react'
import { Marker } from 'react-leaflet'
import L from 'leaflet'
import { getAltitudeColor, aircraftIconHtml } from '@/config/aircraft'
import type { AircraftState } from '@/lib/types'

interface Props {
  aircraft: AircraftState[]
  visible: boolean
  onAircraftClick?: (ac: AircraftState) => void
}

export function AircraftMarkerLayer({ aircraft, visible, onAircraftClick }: Props) {
  const markers = useMemo(() => {
    if (!visible) return []
    return aircraft.map(ac => {
      const color = getAltitudeColor(ac.baroAlt, ac.onGround)
      const label = ac.callsign || ac.icao24
      const html = aircraftIconHtml(ac.track, color, label, ac.spi)
      const icon = L.divIcon({
        html,
        className: '',
        iconSize: [16, 16],
        iconAnchor: [8, 8],
      })
      return { ac, icon }
    })
  }, [aircraft, visible])

  if (!visible || markers.length === 0) return null

  return (
    <>
      {markers.map(({ ac, icon }) => (
        <Marker
          key={ac.icao24}
          position={[ac.lat, ac.lon]}
          icon={icon}
          eventHandlers={onAircraftClick ? {
            click: () => onAircraftClick(ac),
          } : undefined}
        />
      ))}
    </>
  )
}
