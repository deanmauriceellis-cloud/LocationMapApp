import { useMemo } from 'react'
import { CircleMarker, Tooltip, useMap } from 'react-leaflet'
import { classifyPoi, resolveCategory, UNCATEGORIZED_COLOR } from '@/config/categories'
import type { POI, FindResult } from '@/lib/types'

const LABEL_ZOOM = 16

interface Props {
  pois: POI[]
  filterResults?: FindResult[] | null
  onPoiClick?: (poi: POI) => void
}

export function PoiMarkerLayer({ pois, filterResults, onPoiClick }: Props) {
  const map = useMap()
  const zoom = map.getZoom()
  const showLabels = zoom >= LABEL_ZOOM

  // In filter mode, show only filtered results with forced labels
  const isFilterMode = filterResults != null && filterResults.length > 0

  const classified = useMemo(() =>
    pois.map(poi => ({
      poi,
      category: classifyPoi(poi.tags || {}),
    })),
    [pois]
  )

  // Build a set of filter result keys for fast lookup
  const filterKeys = useMemo(() => {
    if (!isFilterMode) return null
    const keys = new Set<string>()
    for (const r of filterResults!) {
      keys.add(`${r.type}-${r.id}`)
    }
    return keys
  }, [filterResults, isFilterMode])

  if (isFilterMode) {
    // Render only filtered results with forced labels
    return (
      <>
        {filterResults!.map((r) => {
          const cat = resolveCategory(r.category)
          const color = cat?.color || UNCATEGORIZED_COLOR
          return (
            <CircleMarker
              key={`${r.type}-${r.id}`}
              center={[r.lat, r.lon]}
              radius={6}
              pathOptions={{ color, fillColor: color, fillOpacity: 0.85, weight: 1 }}
              eventHandlers={onPoiClick ? {
                click: () => {
                  // Construct a POI-like object from FindResult
                  onPoiClick({ osm_type: r.type, osm_id: r.id, lat: r.lat, lon: r.lon, tags: r.tags, type: r.type, id: r.id })
                }
              } : undefined}
            >
              {r.name && (
                <Tooltip permanent direction="right" offset={[8, 0]} className="poi-label">
                  {r.name}
                </Tooltip>
              )}
            </CircleMarker>
          )
        })}
      </>
    )
  }

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
            eventHandlers={onPoiClick ? {
              click: () => onPoiClick(poi)
            } : undefined}
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
