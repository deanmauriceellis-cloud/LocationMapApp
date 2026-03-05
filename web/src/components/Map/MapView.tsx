import { useEffect } from 'react'
import { MapContainer, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import type { Map as LeafletMap } from 'leaflet'
import { PoiMarkerLayer } from './PoiMarkerLayer'
import { MapControls } from './MapControls'
import type { POI, BboxParams, FindResult } from '@/lib/types'

const LIGHT_TILES = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
const DARK_TILES = 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png'
const LIGHT_ATTR = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
const DARK_ATTR = '&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a> &copy; <a href="https://carto.com/">CARTO</a>'

interface Props {
  center: [number, number]
  dark: boolean
  pois: POI[]
  onBoundsChange: (bbox: BboxParams) => void
  onLocate: () => void
  mapRef: React.MutableRefObject<LeafletMap | null>
  filterResults?: FindResult[] | null
  onPoiClick?: (poi: POI) => void
}

function BoundsWatcher({ onBoundsChange }: { onBoundsChange: (bbox: BboxParams) => void }) {
  const map = useMap()

  useEffect(() => {
    const b = map.getBounds()
    onBoundsChange({ s: b.getSouth(), w: b.getWest(), n: b.getNorth(), e: b.getEast() })
  }, [map, onBoundsChange])

  useMapEvents({
    moveend: () => {
      const b = map.getBounds()
      onBoundsChange({ s: b.getSouth(), w: b.getWest(), n: b.getNorth(), e: b.getEast() })
    },
  })

  return null
}

function MapRefSetter({ mapRef }: { mapRef: React.MutableRefObject<LeafletMap | null> }) {
  const map = useMap()
  useEffect(() => { mapRef.current = map }, [map, mapRef])
  return null
}

export function MapView({ center, dark, pois, onBoundsChange, onLocate, mapRef, filterResults, onPoiClick }: Props) {
  return (
    <MapContainer
      center={center}
      zoom={14}
      className="h-full w-full"
      zoomControl={false}
    >
      <MapRefSetter mapRef={mapRef} />
      <TileLayer
        key={dark ? 'dark' : 'light'}
        url={dark ? DARK_TILES : LIGHT_TILES}
        attribution={dark ? DARK_ATTR : LIGHT_ATTR}
        maxZoom={19}
      />
      <BoundsWatcher onBoundsChange={onBoundsChange} />
      <PoiMarkerLayer pois={pois} filterResults={filterResults} onPoiClick={onPoiClick} />
      <MapControls onLocate={onLocate} />
    </MapContainer>
  )
}
