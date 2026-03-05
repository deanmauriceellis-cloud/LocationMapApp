import { useMemo } from 'react'
import { CircleMarker, Tooltip, useMap } from 'react-leaflet'
import { classifyPoi, UNCATEGORIZED_COLOR } from '@/config/categories'
import type { POI } from '@/lib/types'

const LABEL_ZOOM = 16

interface Props {
  pois: POI[]
}

export function PoiMarkerLayer({ pois }: Props) {
  const map = useMap()
  const zoom = map.getZoom()
  const showLabels = zoom >= LABEL_ZOOM

  const classified = useMemo(() =>
    pois.map(poi => ({
      poi,
      category: classifyPoi(poi.tags || {}),
    })),
    [pois]
  )

  return (
    <>
      {classified.map(({ poi, category }) => {
        const color = category?.color || UNCATEGORIZED_COLOR
        const name = poi.tags?.name
        return (
          <CircleMarker
            key={`${poi.osm_type || poi.type}-${poi.osm_id || poi.id}`}
            center={[poi.lat, poi.lon]}
            radius={showLabels ? 6 : 4}
            pathOptions={{
              color,
              fillColor: color,
              fillOpacity: 0.85,
              weight: 1,
            }}
          >
            {showLabels && name && (
              <Tooltip
                permanent
                direction="right"
                offset={[8, 0]}
                className="poi-label"
              >
                {name}
              </Tooltip>
            )}
          </CircleMarker>
        )
      })}
    </>
  )
}
