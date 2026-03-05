import { useRef, useCallback, useState } from 'react'
import type { Map as LeafletMap } from 'leaflet'
import { MapView } from '@/components/Map/MapView'
import { Toolbar } from '@/components/Layout/Toolbar'
import { StatusBar } from '@/components/Layout/StatusBar'
import { FindPanel } from '@/components/Find/FindPanel'
import { PoiDetailPanel } from '@/components/Find/PoiDetailPanel'
import { WeatherPanel } from '@/components/Weather/WeatherPanel'
import { useGeolocation } from '@/hooks/useGeolocation'
import { usePois } from '@/hooks/usePois'
import { useDarkMode } from '@/hooks/useDarkMode'
import { useFind } from '@/hooks/useFind'
import { useWeather } from '@/hooks/useWeather'
import { classifyPoi } from '@/config/categories'
import { haversineM } from '@/lib/distance'
import type { BboxParams, POI, FindResult } from '@/lib/types'

export default function App() {
  const geo = useGeolocation()
  const { pois, loading, totalCount, fetchPois } = usePois()
  const { dark, toggle: toggleDark } = useDarkMode()
  const find = useFind()
  const wx = useWeather()
  const mapRef = useRef<LeafletMap | null>(null)
  const [mapCenter, setMapCenter] = useState<[number, number] | null>(null)
  const lastBbox = useRef<BboxParams | null>(null)

  const [findOpen, setFindOpen] = useState(false)
  const [weatherOpen, setWeatherOpen] = useState(false)
  const [selectedResult, setSelectedResult] = useState<FindResult | null>(null)
  const [filterResults, setFilterResults] = useState<FindResult[] | null>(null)
  const [filterLabel, setFilterLabel] = useState<string | null>(null)

  const center: [number, number] = mapCenter || [geo.lat, geo.lon]

  const metarsVisibleRef = useRef(wx.metarsVisible)
  metarsVisibleRef.current = wx.metarsVisible

  const handleBoundsChange = useCallback((bbox: BboxParams) => {
    fetchPois(bbox)
    lastBbox.current = bbox
    const lat = (bbox.s + bbox.n) / 2
    const lon = (bbox.w + bbox.e) / 2
    setMapCenter([lat, lon])
    if (metarsVisibleRef.current) wx.fetchMetars(bbox)
  }, [fetchPois, wx.fetchMetars])

  const handleLocate = useCallback(() => {
    geo.locate()
    if (mapRef.current && geo.lat && geo.lon) {
      mapRef.current.setView([geo.lat, geo.lon], 14)
    }
  }, [geo])

  const handleToggleFind = useCallback(() => {
    setFindOpen(prev => {
      if (!prev) {
        setSelectedResult(null)
        setWeatherOpen(false) // mutual exclusion
        wx.stopAutoRefresh()
      }
      return !prev
    })
  }, [wx.stopAutoRefresh])

  const handleToggleWeather = useCallback(() => {
    setWeatherOpen(prev => {
      if (!prev) {
        setFindOpen(false) // mutual exclusion
        // Fetch weather for current center
        const c = mapCenter || [geo.lat, geo.lon]
        wx.fetchWeather(c[0], c[1])
        wx.startAutoRefresh(c[0], c[1])
      } else {
        wx.stopAutoRefresh()
      }
      return !prev
    })
  }, [mapCenter, geo.lat, geo.lon, wx.fetchWeather, wx.startAutoRefresh, wx.stopAutoRefresh])

  const handleAlertClick = useCallback(() => {
    if (!weatherOpen) {
      setFindOpen(false)
      setWeatherOpen(true)
      const c = mapCenter || [geo.lat, geo.lon]
      wx.fetchWeather(c[0], c[1])
      wx.startAutoRefresh(c[0], c[1])
    }
  }, [weatherOpen, mapCenter, geo.lat, geo.lon, wx.fetchWeather, wx.startAutoRefresh])

  const handleToggleMetars = useCallback(() => {
    wx.toggleMetars()
    // If enabling metars and we have a bbox, fetch immediately
    if (!metarsVisibleRef.current && lastBbox.current) {
      wx.fetchMetars(lastBbox.current)
    }
  }, [wx.toggleMetars, wx.fetchMetars])

  const handleSearch = useCallback((query: string) => {
    find.search(query, center[0], center[1])
  }, [find, center])

  const handleLoadCounts = useCallback(() => {
    find.loadCounts(center[0], center[1])
  }, [find, center])

  const handleFindByCategory = useCallback(async (categories: string[]) => {
    return find.findByCategory(center[0], center[1], categories)
  }, [find, center])

  const handleSelectResult = useCallback((result: FindResult) => {
    setSelectedResult(result)
    setFindOpen(false)
    if (mapRef.current) {
      mapRef.current.setView([result.lat, result.lon], 18)
    }
  }, [])

  const handleFilterAndMap = useCallback((results: FindResult[], label: string) => {
    setFilterResults(results)
    setFilterLabel(label)
    setFindOpen(false)
    setSelectedResult(null)
    // Zoom to fit all results
    if (mapRef.current && results.length > 0) {
      const L = (window as any).L
      if (L) {
        const bounds = L.latLngBounds(results.map((r: FindResult) => [r.lat, r.lon]))
        mapRef.current.fitBounds(bounds, { padding: [40, 40], maxZoom: 18 })
      }
    }
  }, [])

  const handleClearFilter = useCallback(() => {
    setFilterResults(null)
    setFilterLabel(null)
  }, [])

  const handlePoiClick = useCallback((poi: POI) => {
    const cat = classifyPoi(poi.tags || {})
    const dist = haversineM(center[0], center[1], poi.lat, poi.lon)
    const result: FindResult = {
      type: poi.osm_type || poi.type,
      id: poi.osm_id || poi.id,
      lat: poi.lat,
      lon: poi.lon,
      name: poi.tags?.name || '',
      category: cat?.id || '',
      distance_m: dist,
      tags: poi.tags || {},
    }
    setSelectedResult(result)
    setFindOpen(false)
  }, [center])

  const handleCloseDetail = useCallback(() => {
    setSelectedResult(null)
  }, [])

  const handleFlyTo = useCallback((lat: number, lon: number) => {
    if (mapRef.current) {
      mapRef.current.setView([lat, lon], 18)
    }
  }, [])

  return (
    <div className={`h-screen w-screen relative ${dark ? 'dark' : ''}`}>
      <Toolbar
        dark={dark}
        onToggleDark={toggleDark}
        findOpen={findOpen}
        onToggleFind={handleToggleFind}
        weatherOpen={weatherOpen}
        onToggleWeather={handleToggleWeather}
        alertCount={wx.weather?.alerts?.length}
        weatherIconCode={wx.weather?.current?.iconCode}
        weatherIsDaytime={wx.weather?.current?.isDaytime}
      />
      <div className="absolute inset-0 top-12 bottom-8">
        <MapView
          center={center}
          dark={dark}
          pois={pois}
          onBoundsChange={handleBoundsChange}
          onLocate={handleLocate}
          mapRef={mapRef}
          filterResults={filterResults}
          onPoiClick={handlePoiClick}
          metars={wx.metars}
          metarsVisible={wx.metarsVisible}
          radarOn={wx.radarOn}
          radarAnimating={wx.radarAnimating}
        />
      </div>

      {/* Find panel */}
      <FindPanel
        open={findOpen}
        lat={center[0]}
        lon={center[1]}
        searchResults={find.searchResults}
        searchLoading={find.searchLoading}
        searchHint={find.searchHint}
        categoryCounts={find.categoryCounts}
        countsTotal={find.countsTotal}
        onSearch={handleSearch}
        onClearSearch={find.clearSearch}
        onLoadCounts={handleLoadCounts}
        onFindByCategory={handleFindByCategory}
        onSelectResult={handleSelectResult}
        onFilterAndMap={handleFilterAndMap}
        onClose={handleToggleFind}
      />

      {/* Weather panel */}
      <WeatherPanel
        open={weatherOpen}
        weather={wx.weather}
        loading={wx.weatherLoading}
        radarOn={wx.radarOn}
        radarAnimating={wx.radarAnimating}
        metarsVisible={wx.metarsVisible}
        onToggleRadar={wx.toggleRadar}
        onToggleRadarAnimate={wx.toggleRadarAnimate}
        onToggleMetars={handleToggleMetars}
        onClose={handleToggleWeather}
      />

      {/* POI detail panel */}
      {selectedResult && (
        <PoiDetailPanel
          result={selectedResult}
          onClose={handleCloseDetail}
          onFlyTo={handleFlyTo}
          onFetchWebsite={find.fetchWebsite}
        />
      )}

      <StatusBar
        lat={center[0]}
        lon={center[1]}
        poiCount={pois.length}
        totalCount={totalCount}
        loading={loading}
        filterLabel={filterLabel}
        filterCount={filterResults?.length}
        onClearFilter={handleClearFilter}
        alertEvent={wx.weather?.alerts?.[0]?.event}
        alertCount={wx.weather?.alerts?.length}
        onAlertClick={handleAlertClick}
      />
    </div>
  )
}
