import { useRef, useCallback, useState } from 'react'
import type { Map as LeafletMap } from 'leaflet'
import { MapView } from '@/components/Map/MapView'
import { Toolbar } from '@/components/Layout/Toolbar'
import { StatusBar } from '@/components/Layout/StatusBar'
import { useGeolocation } from '@/hooks/useGeolocation'
import { usePois } from '@/hooks/usePois'
import { useDarkMode } from '@/hooks/useDarkMode'
import type { BboxParams } from '@/lib/types'

export default function App() {
  const geo = useGeolocation()
  const { pois, loading, totalCount, fetchPois } = usePois()
  const { dark, toggle: toggleDark } = useDarkMode()
  const mapRef = useRef<LeafletMap | null>(null)
  const [mapCenter, setMapCenter] = useState<[number, number] | null>(null)

  const center: [number, number] = mapCenter || [geo.lat, geo.lon]

  const handleBoundsChange = useCallback((bbox: BboxParams) => {
    fetchPois(bbox)
    const lat = (bbox.s + bbox.n) / 2
    const lon = (bbox.w + bbox.e) / 2
    setMapCenter([lat, lon])
  }, [fetchPois])

  const handleLocate = useCallback(() => {
    geo.locate()
    if (mapRef.current && geo.lat && geo.lon) {
      mapRef.current.setView([geo.lat, geo.lon], 14)
    }
  }, [geo])

  return (
    <div className={`h-screen w-screen relative ${dark ? 'dark' : ''}`}>
      <Toolbar dark={dark} onToggleDark={toggleDark} />
      <div className="absolute inset-0 top-12 bottom-8">
        <MapView
          center={center}
          dark={dark}
          pois={pois}
          onBoundsChange={handleBoundsChange}
          onLocate={handleLocate}
          mapRef={mapRef}
        />
      </div>
      <StatusBar
        lat={center[0]}
        lon={center[1]}
        poiCount={pois.length}
        totalCount={totalCount}
        loading={loading}
      />
    </div>
  )
}
