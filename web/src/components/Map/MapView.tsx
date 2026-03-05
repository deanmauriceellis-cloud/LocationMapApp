import { useEffect, useRef, useCallback } from 'react'
import { MapContainer, TileLayer, useMap, useMapEvents } from 'react-leaflet'
import type { Map as LeafletMap } from 'leaflet'
import { PoiMarkerLayer } from './PoiMarkerLayer'
import { RadarLayer } from './RadarLayer'
import { MetarMarkerLayer } from './MetarMarkerLayer'
import { AircraftMarkerLayer } from './AircraftMarkerLayer'
import { FlightTrailLayer } from './FlightTrailLayer'
import { TransitMarkerLayer } from './TransitMarkerLayer'
import { MapControls } from './MapControls'
import type { POI, BboxParams, FindResult, MetarStation, AircraftState, FlightPathPoint, MbtaVehicle, MbtaStop } from '@/lib/types'
import type { PoiCluster } from '@/hooks/usePois'

const LIGHT_TILES = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png'
const DARK_TILES = 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png'
const LIGHT_ATTR = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
const DARK_ATTR = '&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a> &copy; <a href="https://carto.com/">CARTO</a>'

interface Props {
  center: [number, number]
  dark: boolean
  pois: POI[]
  clusters?: PoiCluster[] | null
  onBoundsChange: (bbox: BboxParams) => void
  onLocate: () => void
  mapRef: React.MutableRefObject<LeafletMap | null>
  filterResults?: FindResult[] | null
  onPoiClick?: (poi: POI) => void
  metars?: MetarStation[]
  metarsVisible?: boolean
  radarOn?: boolean
  radarAnimating?: boolean
  // Aircraft
  aircraft?: AircraftState[]
  aircraftVisible?: boolean
  flightPath?: FlightPathPoint[]
  onAircraftClick?: (ac: AircraftState) => void
  // Transit
  trains?: MbtaVehicle[]
  subway?: MbtaVehicle[]
  buses?: MbtaVehicle[]
  stations?: MbtaStop[]
  busStops?: MbtaStop[]
  trainsVisible?: boolean
  subwayVisible?: boolean
  busesVisible?: boolean
  selectedVehicleId?: string | null
  onVehicleClick?: (v: MbtaVehicle) => void
  onStopClick?: (s: MbtaStop) => void
  onLongPress?: (lat: number, lon: number) => void
  hasHome?: boolean
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

function LongPressHandler({ onLongPress }: { onLongPress: (lat: number, lon: number) => void }) {
  const map = useMap()
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const cancel = useCallback(() => {
    if (timerRef.current) { clearTimeout(timerRef.current); timerRef.current = null }
  }, [])

  useMapEvents({
    mousedown: (e) => {
      cancel()
      timerRef.current = setTimeout(() => {
        timerRef.current = null
        onLongPress(e.latlng.lat, e.latlng.lng)
      }, 700)
    },
    mouseup: cancel,
    dragstart: cancel,
    zoomstart: cancel,
  })

  // Prevent native context menu on map (mobile long-press)
  useEffect(() => {
    const container = map.getContainer()
    const prevent = (e: Event) => e.preventDefault()
    container.addEventListener('contextmenu', prevent)
    return () => container.removeEventListener('contextmenu', prevent)
  }, [map])

  return null
}

function MapRefSetter({ mapRef }: { mapRef: React.MutableRefObject<LeafletMap | null> }) {
  const map = useMap()
  useEffect(() => { mapRef.current = map }, [map, mapRef])
  return null
}

export function MapView({
  center, dark, pois, clusters, onBoundsChange, onLocate, mapRef, filterResults, onPoiClick,
  metars, metarsVisible, radarOn, radarAnimating,
  aircraft, aircraftVisible, flightPath, onAircraftClick,
  trains, subway, buses, stations, busStops, trainsVisible, subwayVisible, busesVisible, selectedVehicleId, onVehicleClick, onStopClick,
  onLongPress, hasHome,
}: Props) {
  return (
    <MapContainer
      center={center}
      zoom={14}
      className="h-full w-full"
      zoomControl={false}
    >
      <MapRefSetter mapRef={mapRef} />
      {onLongPress && <LongPressHandler onLongPress={onLongPress} />}
      <TileLayer
        key={dark ? 'dark' : 'light'}
        url={dark ? DARK_TILES : LIGHT_TILES}
        attribution={dark ? DARK_ATTR : LIGHT_ATTR}
        maxZoom={19}
      />
      <BoundsWatcher onBoundsChange={onBoundsChange} />
      <RadarLayer visible={radarOn || false} animating={radarAnimating || false} />
      <PoiMarkerLayer pois={pois} clusters={clusters} filterResults={filterResults} onPoiClick={onPoiClick} />
      <MetarMarkerLayer metars={metars || []} visible={metarsVisible || false} />
      <AircraftMarkerLayer aircraft={aircraft || []} visible={aircraftVisible || false} onAircraftClick={onAircraftClick} />
      <FlightTrailLayer path={flightPath || []} visible={aircraftVisible || false} />
      <TransitMarkerLayer
        trains={trains || []} subway={subway || []} buses={buses || []} stations={stations || []} busStops={busStops || []}
        trainsVisible={trainsVisible || false} subwayVisible={subwayVisible || false} busesVisible={busesVisible || false}
        selectedVehicleId={selectedVehicleId}
        onVehicleClick={onVehicleClick} onStopClick={onStopClick}
      />
      <MapControls onLocate={onLocate} hasHome={hasHome} />
    </MapContainer>
  )
}
