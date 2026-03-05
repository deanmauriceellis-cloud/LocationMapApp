// (C) Dean Maurice Ellis, 2026 - Module FlightTrailLayer.tsx
import { Polyline } from 'react-leaflet'
import { getAltitudeColor } from '@/config/aircraft'
import type { FlightPathPoint } from '@/lib/types'

interface Props {
  path: FlightPathPoint[]
  visible: boolean
}

export function FlightTrailLayer({ path, visible }: Props) {
  if (!visible || path.length === 0) return null

  return (
    <>
      {path.map((pt, i) => {
        const color = getAltitudeColor(pt.altitude, false)
        const positions: [number, number][] = [
          [pt.firstLat, pt.firstLon],
          [pt.lastLat, pt.lastLon],
        ]
        return (
          <Polyline
            key={i}
            positions={positions}
            pathOptions={{
              color,
              weight: 2.5,
              opacity: 0.7,
              dashArray: '6 4',
            }}
          />
        )
      })}
    </>
  )
}
