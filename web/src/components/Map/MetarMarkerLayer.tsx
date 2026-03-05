import { CircleMarker, Tooltip, useMap } from 'react-leaflet'
import type { MetarStation } from '@/lib/types'

const METAR_LABEL_ZOOM = 10

const FLT_CAT_COLORS: Record<string, string> = {
  VFR: '#2E7D32',
  MVFR: '#1565C0',
  IFR: '#C62828',
  LIFR: '#AD1457',
}

interface Props {
  metars: MetarStation[]
  visible: boolean
}

export function MetarMarkerLayer({ metars, visible }: Props) {
  const map = useMap()
  const zoom = map.getZoom()
  const showLabels = zoom >= METAR_LABEL_ZOOM

  if (!visible || metars.length === 0) return null

  return (
    <>
      {metars.map((m) => {
        const color = FLT_CAT_COLORS[m.fltCat] || '#9E9E9E'
        const tempDisplay = m.temp != null ? `${Math.round(m.temp)}°C` : ''
        return (
          <CircleMarker
            key={m.icaoId}
            center={[m.lat, m.lon]}
            radius={showLabels ? 5 : 3}
            pathOptions={{
              color,
              fillColor: color,
              fillOpacity: 0.9,
              weight: 1,
            }}
          >
            {showLabels && (
              <Tooltip
                permanent
                direction="right"
                offset={[6, 0]}
                className="metar-label"
              >
                {m.icaoId} {tempDisplay}
              </Tooltip>
            )}
          </CircleMarker>
        )
      })}
    </>
  )
}
