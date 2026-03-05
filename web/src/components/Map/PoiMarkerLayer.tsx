import { useMemo } from 'react'
import { CircleMarker, Tooltip, useMap } from 'react-leaflet'
import { classifyPoi, resolveCategory, UNCATEGORIZED_COLOR } from '@/config/categories'
import type { POI, FindResult } from '@/lib/types'
import type { PoiCluster } from '@/hooks/usePois'

const LABEL_ZOOM = 16

/** Map dominant tag string to a color */
function clusterColor(tag: string): string {
  const cat = resolveCategory(tag)
  return cat?.color || UNCATEGORIZED_COLOR
}

/** Cluster circle radius based on count */
function clusterRadius(count: number): number {
  if (count <= 5) return 10
  if (count <= 20) return 14
  if (count <= 50) return 18
  if (count <= 100) return 22
  return 26
}

interface Props {
  pois: POI[]
  clusters?: PoiCluster[] | null
  filterResults?: FindResult[] | null
  onPoiClick?: (poi: POI) => void
}

export function PoiMarkerLayer({ pois, clusters, filterResults, onPoiClick }: Props) {
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

  // Server-side clusters — lightweight rendering, max ~400 circles
  if (clusters && clusters.length > 0) {
    return (
      <>
        {clusters.map((c, i) => {
          const r = clusterRadius(c.count)
          const color = clusterColor(c.tag)
          return (
            <CircleMarker
              key={i}
              center={[c.lat, c.lon]}
              radius={r}
              pathOptions={{
                color: color,
                fillColor: color,
                fillOpacity: 0.2,
                weight: 1,
                opacity: 0.4,
              }}
              interactive={false}
            >
              <Tooltip permanent direction="center" className="cluster-label">
                {c.count}
              </Tooltip>
            </CircleMarker>
          )
        })}
      </>
    )
  }

  // Normal mode — individual interactive markers
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
