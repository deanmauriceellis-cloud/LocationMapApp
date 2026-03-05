import { useRef, useCallback, useState, useEffect } from 'react'
import type { Map as LeafletMap } from 'leaflet'
import { MapView } from '@/components/Map/MapView'
import { Toolbar } from '@/components/Layout/Toolbar'
import { StatusBar } from '@/components/Layout/StatusBar'
import { LayersDropdown } from '@/components/Layout/LayersDropdown'
import { FindPanel } from '@/components/Find/FindPanel'
import { PoiDetailPanel } from '@/components/Find/PoiDetailPanel'
import { WeatherPanel } from '@/components/Weather/WeatherPanel'
import { AircraftDetailPanel } from '@/components/Aircraft/AircraftDetailPanel'
import { VehicleDetailPanel } from '@/components/Transit/VehicleDetailPanel'
import { ArrivalBoardPanel } from '@/components/Transit/ArrivalBoardPanel'
import { AuthDialog } from '@/components/Social/AuthDialog'
import { ProfileDropdown } from '@/components/Social/ProfileDropdown'
import { ChatPanel } from '@/components/Social/ChatPanel'
import { useGeolocation } from '@/hooks/useGeolocation'
import { usePois } from '@/hooks/usePois'
import { useDarkMode } from '@/hooks/useDarkMode'
import { useFind } from '@/hooks/useFind'
import { useWeather } from '@/hooks/useWeather'
import { useAircraft } from '@/hooks/useAircraft'
import { useTransit } from '@/hooks/useTransit'
import { useAuth } from '@/hooks/useAuth'
import { useComments } from '@/hooks/useComments'
import { useChat } from '@/hooks/useChat'
import { classifyPoi } from '@/config/categories'
import { haversineM } from '@/lib/distance'
import type { BboxParams, POI, FindResult, AircraftState, MbtaVehicle, MbtaStop } from '@/lib/types'

// Which detail panel is showing (mutual exclusion)
type DetailView = 'none' | 'poi' | 'aircraft' | 'vehicle' | 'arrivals'

export default function App() {
  const geo = useGeolocation()
  const { pois, clusters, loading, totalCount, fetchPois } = usePois()
  const { dark, toggle: toggleDark } = useDarkMode()
  const find = useFind()
  const wx = useWeather()
  const ac = useAircraft()
  const tr = useTransit()
  const auth = useAuth()
  const comments = useComments()
  const chat = useChat()
  const mapRef = useRef<LeafletMap | null>(null)
  const [mapCenter, setMapCenter] = useState<[number, number] | null>(null)
  const lastBbox = useRef<BboxParams | null>(null)

  const [findOpen, setFindOpen] = useState(false)
  const [weatherOpen, setWeatherOpen] = useState(false)
  const [layersOpen, setLayersOpen] = useState(false)
  const [chatOpen, setChatOpen] = useState(false)
  const [authDialogOpen, setAuthDialogOpen] = useState(false)
  const [profileOpen, setProfileOpen] = useState(false)
  const [detailView, setDetailView] = useState<DetailView>('none')
  const [selectedResult, setSelectedResult] = useState<FindResult | null>(null)
  const [filterResults, setFilterResults] = useState<FindResult[] | null>(null)
  const [filterLabel, setFilterLabel] = useState<string | null>(null)
  const [arrivalStopName, setArrivalStopName] = useState('')

  const center: [number, number] = mapCenter || [geo.lat, geo.lon]

  const metarsVisibleRef = useRef(wx.metarsVisible)
  metarsVisibleRef.current = wx.metarsVisible
  const acVisibleRef = useRef(ac.visible)
  acVisibleRef.current = ac.visible
  const busesVisibleRef = useRef(tr.busesVisible)
  busesVisibleRef.current = tr.busesVisible
  const fetchBusStopsRef = useRef(tr.fetchBusStops)
  fetchBusStopsRef.current = tr.fetchBusStops

  const handleBoundsChange = useCallback((bbox: BboxParams) => {
    fetchPois(bbox)
    lastBbox.current = bbox
    const lat = (bbox.s + bbox.n) / 2
    const lon = (bbox.w + bbox.e) / 2
    setMapCenter([lat, lon])
    if (metarsVisibleRef.current) wx.fetchMetars(bbox)
    if (acVisibleRef.current) ac.fetchAircraft(bbox)
    if (busesVisibleRef.current) fetchBusStopsRef.current(bbox)
  }, [fetchPois, wx.fetchMetars, ac.fetchAircraft])

  // Auto-refresh aircraft when visible
  useEffect(() => {
    if (ac.visible && lastBbox.current) {
      ac.fetchAircraft(lastBbox.current)
      ac.startAutoRefresh(lastBbox.current)
    } else {
      ac.stopAutoRefresh()
    }
    return () => ac.stopAutoRefresh()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ac.visible])

  // Fetch bus stops when bus layer is toggled on
  useEffect(() => {
    if (tr.busesVisible && lastBbox.current) {
      fetchBusStopsRef.current(lastBbox.current)
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tr.busesVisible])

  // Follow selected aircraft: fly to its position on each data refresh
  useEffect(() => {
    if (ac.following && ac.selectedAircraft && mapRef.current) {
      const sel = ac.aircraft.find(a => a.icao24 === ac.selectedAircraft!.icao24)
      if (sel) {
        mapRef.current.setView([sel.lat, sel.lon])
      }
    }
  }, [ac.following, ac.selectedAircraft, ac.aircraft])

  // Follow selected vehicle: fly to its position on each data refresh
  useEffect(() => {
    if (tr.followingVehicleId && tr.selectedVehicle && mapRef.current) {
      mapRef.current.setView([tr.selectedVehicle.lat, tr.selectedVehicle.lon])
    }
  }, [tr.followingVehicleId, tr.selectedVehicle])

  // Connect/disconnect chat socket when chat panel opens/closes
  useEffect(() => {
    if (chatOpen && auth.isLoggedIn) {
      chat.connect()
      chat.loadRooms()
    } else {
      chat.disconnect()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [chatOpen, auth.isLoggedIn])

  // Close auth dialog when user logs in successfully
  useEffect(() => {
    if (auth.isLoggedIn && authDialogOpen) {
      setAuthDialogOpen(false)
    }
  }, [auth.isLoggedIn, authDialogOpen])

  const handleLocate = useCallback(() => {
    geo.locate()
    if (mapRef.current && geo.lat && geo.lon) {
      mapRef.current.setView([geo.lat, geo.lon], 14)
    }
  }, [geo])

  const handleToggleFind = useCallback(() => {
    setFindOpen(prev => {
      if (!prev) {
        setDetailView('none')
        setSelectedResult(null)
        ac.clearSelection()
        tr.clearSelection()
        comments.clearComments()
        setWeatherOpen(false)
        setChatOpen(false)
        wx.stopAutoRefresh()
      }
      return !prev
    })
  }, [wx.stopAutoRefresh, ac.clearSelection, tr.clearSelection, comments.clearComments])

  const handleToggleWeather = useCallback(() => {
    setWeatherOpen(prev => {
      if (!prev) {
        setFindOpen(false)
        setChatOpen(false)
        const c = mapCenter || [geo.lat, geo.lon]
        wx.fetchWeather(c[0], c[1])
        wx.startAutoRefresh(c[0], c[1])
      } else {
        wx.stopAutoRefresh()
      }
      return !prev
    })
  }, [mapCenter, geo.lat, geo.lon, wx.fetchWeather, wx.startAutoRefresh, wx.stopAutoRefresh])

  const handleToggleLayers = useCallback(() => {
    setLayersOpen(prev => !prev)
  }, [])

  const handleToggleChat = useCallback(() => {
    setChatOpen(prev => {
      if (!prev) {
        setFindOpen(false)
        setWeatherOpen(false)
        wx.stopAutoRefresh()
      }
      return !prev
    })
  }, [wx.stopAutoRefresh])

  const handleToggleProfile = useCallback(() => {
    setProfileOpen(prev => !prev)
  }, [])

  const handleLoginRequired = useCallback(() => {
    setAuthDialogOpen(true)
  }, [])

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
    setDetailView('poi')
    setFindOpen(false)
    ac.clearSelection()
    tr.clearSelection()
    comments.loadComments(result.type, result.id)
    if (mapRef.current) {
      mapRef.current.setView([result.lat, result.lon], 18)
    }
  }, [ac.clearSelection, tr.clearSelection, comments.loadComments])

  const handleFilterAndMap = useCallback((results: FindResult[], label: string) => {
    setFilterResults(results)
    setFilterLabel(label)
    setFindOpen(false)
    setSelectedResult(null)
    setDetailView('none')
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
    const osmType = poi.osm_type || poi.type
    const osmId = poi.osm_id || poi.id
    const result: FindResult = {
      type: osmType,
      id: osmId,
      lat: poi.lat,
      lon: poi.lon,
      name: poi.tags?.name || '',
      category: cat?.id || '',
      distance_m: dist,
      tags: poi.tags || {},
    }
    setSelectedResult(result)
    setDetailView('poi')
    setFindOpen(false)
    ac.clearSelection()
    tr.clearSelection()
    comments.loadComments(osmType, osmId)
  }, [center, ac.clearSelection, tr.clearSelection, comments.loadComments])

  const handleAircraftClick = useCallback((aircraft: AircraftState) => {
    ac.selectAircraft(aircraft)
    setDetailView('aircraft')
    setSelectedResult(null)
    tr.clearSelection()
    setFindOpen(false)
  }, [ac.selectAircraft, tr.clearSelection])

  const handleVehicleClick = useCallback((v: MbtaVehicle) => {
    tr.selectVehicle(v)
    setDetailView('vehicle')
    setSelectedResult(null)
    ac.clearSelection()
    setFindOpen(false)
  }, [tr.selectVehicle, ac.clearSelection])

  const handleStopClick = useCallback((s: MbtaStop) => {
    tr.selectStop(s)
    setArrivalStopName(s.name)
    setDetailView('arrivals')
    setSelectedResult(null)
    ac.clearSelection()
    setFindOpen(false)
  }, [tr.selectStop, ac.clearSelection])

  const handleCloseDetail = useCallback(() => {
    setDetailView('none')
    setSelectedResult(null)
    ac.clearSelection()
    tr.clearSelection()
    comments.clearComments()
  }, [ac.clearSelection, tr.clearSelection, comments.clearComments])

  const handleFlyTo = useCallback((lat: number, lon: number) => {
    if (mapRef.current) {
      mapRef.current.setView([lat, lon], 18)
    }
  }, [])

  // Layer counts
  const activeLayerCount =
    (ac.visible ? 1 : 0) +
    (tr.trainsVisible ? 1 : 0) +
    (tr.subwayVisible ? 1 : 0) +
    (tr.busesVisible ? 1 : 0)

  const layers = [
    { label: 'Aircraft', active: ac.visible, count: ac.aircraft.length, onToggle: ac.toggleVisible },
    { label: 'Trains', active: tr.trainsVisible, count: tr.trains.length, onToggle: tr.toggleTrains },
    { label: 'Subway', active: tr.subwayVisible, count: tr.subway.length, onToggle: tr.toggleSubway },
    { label: 'Buses', active: tr.busesVisible, count: tr.buses.length, onToggle: tr.toggleBuses },
  ]

  return (
    <div className={`h-screen w-screen relative ${dark ? 'dark' : ''}`}>
      <Toolbar
        dark={dark}
        onToggleDark={toggleDark}
        findOpen={findOpen}
        onToggleFind={handleToggleFind}
        weatherOpen={weatherOpen}
        onToggleWeather={handleToggleWeather}
        layersOpen={layersOpen}
        onToggleLayers={handleToggleLayers}
        activeLayerCount={activeLayerCount}
        alertCount={wx.weather?.alerts?.length}
        weatherIconCode={wx.weather?.current?.iconCode}
        weatherIsDaytime={wx.weather?.current?.isDaytime}
        chatOpen={chatOpen}
        onToggleChat={handleToggleChat}
        profileOpen={profileOpen}
        onToggleProfile={handleToggleProfile}
        userInitial={auth.user?.displayName?.charAt(0)?.toUpperCase() || null}
      />

      <LayersDropdown open={layersOpen} layers={layers} onClose={() => setLayersOpen(false)} />

      <div className="absolute inset-0 top-12 bottom-8">
        <MapView
          center={center}
          dark={dark}
          pois={pois}
          clusters={clusters}
          onBoundsChange={handleBoundsChange}
          onLocate={handleLocate}
          mapRef={mapRef}
          filterResults={filterResults}
          onPoiClick={handlePoiClick}
          metars={wx.metars}
          metarsVisible={wx.metarsVisible}
          radarOn={wx.radarOn}
          radarAnimating={wx.radarAnimating}
          aircraft={ac.aircraft}
          aircraftVisible={ac.visible}
          flightPath={ac.history?.path}
          onAircraftClick={handleAircraftClick}
          trains={tr.trains}
          subway={tr.subway}
          buses={tr.buses}
          stations={tr.stations}
          busStops={tr.busStops}
          trainsVisible={tr.trainsVisible}
          subwayVisible={tr.subwayVisible}
          busesVisible={tr.busesVisible}
          selectedVehicleId={tr.selectedVehicle?.id}
          onVehicleClick={handleVehicleClick}
          onStopClick={handleStopClick}
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

      {/* Chat panel */}
      <ChatPanel
        open={chatOpen}
        user={auth.user}
        chat={chat}
        onLoginRequired={handleLoginRequired}
        onClose={handleToggleChat}
      />

      {/* Detail panels — mutually exclusive */}
      {detailView === 'poi' && selectedResult && (
        <PoiDetailPanel
          result={selectedResult}
          onClose={handleCloseDetail}
          onFlyTo={handleFlyTo}
          onFetchWebsite={find.fetchWebsite}
          comments={comments.comments}
          commentTotal={comments.total}
          commentsLoading={comments.loading}
          userId={auth.user?.id ?? null}
          userRole={auth.user?.role ?? null}
          isLoggedIn={auth.isLoggedIn}
          onPostComment={comments.postComment}
          onVoteComment={comments.voteOnComment}
          onDeleteComment={comments.deleteComment}
          onLoginRequired={handleLoginRequired}
        />
      )}

      {detailView === 'aircraft' && ac.selectedAircraft && (
        <AircraftDetailPanel
          aircraft={ac.selectedAircraft}
          history={ac.history}
          historyLoading={ac.historyLoading}
          following={ac.following}
          onToggleFollow={ac.toggleFollow}
          onFlyTo={handleFlyTo}
          onClose={handleCloseDetail}
        />
      )}

      {detailView === 'vehicle' && tr.selectedVehicle && (
        <VehicleDetailPanel
          vehicle={tr.selectedVehicle}
          predictions={tr.predictions}
          following={tr.followingVehicleId === tr.selectedVehicle.id}
          onToggleFollow={tr.toggleFollow}
          onFlyTo={handleFlyTo}
          onClose={handleCloseDetail}
        />
      )}

      {detailView === 'arrivals' && (tr.selectedStop || arrivalStopName) && (
        <ArrivalBoardPanel
          stopName={tr.selectedStop?.name || arrivalStopName}
          predictions={tr.predictions}
          loading={tr.predictionsLoading}
          onClose={handleCloseDetail}
        />
      )}

      <StatusBar
        lat={center[0]}
        lon={center[1]}
        poiCount={clusters ? clusters.reduce((s, c) => s + c.count, 0) : pois.length}
        totalCount={totalCount}
        loading={loading}
        filterLabel={filterLabel}
        filterCount={filterResults?.length}
        onClearFilter={handleClearFilter}
        alertEvent={wx.weather?.alerts?.[0]?.event}
        alertCount={wx.weather?.alerts?.length}
        onAlertClick={handleAlertClick}
        aircraftCount={ac.visible ? ac.aircraft.length : undefined}
        trainCount={tr.trainsVisible ? tr.trains.length : undefined}
        subwayCount={tr.subwayVisible ? tr.subway.length : undefined}
        busCount={tr.busesVisible ? tr.buses.length : undefined}
      />

      {/* Auth dialog (modal overlay) */}
      <AuthDialog
        open={authDialogOpen}
        onClose={() => setAuthDialogOpen(false)}
        onRegister={auth.register}
        onLogin={auth.login}
        loading={auth.loading}
        error={auth.error}
        onClearError={auth.clearError}
      />

      {/* Profile dropdown */}
      <ProfileDropdown
        open={profileOpen}
        user={auth.user}
        onClose={() => setProfileOpen(false)}
        onLogout={auth.logout}
        onSignIn={handleLoginRequired}
      />
    </div>
  )
}
