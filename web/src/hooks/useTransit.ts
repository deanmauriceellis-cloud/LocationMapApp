// (C) Dean Maurice Ellis, 2026 - Module useTransit.ts
import { useState, useRef, useCallback } from 'react'
import { apiFetch } from '@/config/api'
import type { BboxParams, MbtaVehicle, MbtaStop, MbtaPrediction } from '@/lib/types'

const RAIL_REFRESH = 15_000  // 15s for trains/subway
const BUS_REFRESH = 30_000   // 30s for buses
const PRED_REFRESH = 30_000  // 30s for predictions
const BUS_STOP_ZOOM = 15     // Only show bus stops at zoom >= 15

export function useTransit() {
  const [trains, setTrains] = useState<MbtaVehicle[]>([])
  const [subway, setSubway] = useState<MbtaVehicle[]>([])
  const [buses, setBuses] = useState<MbtaVehicle[]>([])
  const [stations, setStations] = useState<MbtaStop[]>([])
  const [stationsLoaded, setStationsLoaded] = useState(false)

  const [busStops, setBusStops] = useState<MbtaStop[]>([])

  const [trainsVisible, setTrainsVisible] = useState(false)
  const [subwayVisible, setSubwayVisible] = useState(false)
  const [busesVisible, setBusesVisible] = useState(false)

  const [selectedVehicle, setSelectedVehicle] = useState<MbtaVehicle | null>(null)
  const [selectedStop, setSelectedStop] = useState<MbtaStop | null>(null)
  const [predictions, setPredictions] = useState<MbtaPrediction[]>([])
  const [predictionsLoading, setPredictionsLoading] = useState(false)
  const [followingVehicleId, setFollowingVehicleId] = useState<string | null>(null)

  const trainTimer = useRef<ReturnType<typeof setInterval> | null>(null)
  const subwayTimer = useRef<ReturnType<typeof setInterval> | null>(null)
  const busTimer = useRef<ReturnType<typeof setInterval> | null>(null)
  const predTimer = useRef<ReturnType<typeof setInterval> | null>(null)

  const fetchVehicles = useCallback(async (routeType: number, setter: (v: MbtaVehicle[]) => void) => {
    try {
      const data = await apiFetch<MbtaVehicle[]>(`/mbta/vehicles?route_type=${routeType}`)
      setter(Array.isArray(data) ? data : [])
    } catch (e) {
      console.error(`Transit fetch error (type=${routeType}):`, e)
    }
  }, [])

  const fetchStations = useCallback(async () => {
    if (stationsLoaded) return
    try {
      const data = await apiFetch<MbtaStop[]>('/mbta/stations?route_type=0,1,2')
      setStations(Array.isArray(data) ? data : [])
      setStationsLoaded(true)
    } catch (e) {
      console.error('Station fetch error:', e)
    }
  }, [stationsLoaded])

  const fetchBusStops = useCallback(async (bbox: BboxParams) => {
    try {
      const data = await apiFetch<MbtaStop[]>(
        `/mbta/bus-stops/bbox?s=${bbox.s}&w=${bbox.w}&n=${bbox.n}&e=${bbox.e}`
      )
      setBusStops(Array.isArray(data) ? data : [])
    } catch (e) {
      console.error('Bus stops fetch error:', e)
    }
  }, [])

  const fetchPredictionsByName = useCallback(async (stopName: string) => {
    setPredictionsLoading(true)
    try {
      const data = await apiFetch<MbtaPrediction[]>(`/mbta/predictions?stop_name=${encodeURIComponent(stopName)}`)
      setPredictions(Array.isArray(data) ? data : [])
    } catch (e) {
      console.error('Predictions fetch error:', e)
    } finally {
      setPredictionsLoading(false)
    }
  }, [])

  const fetchPredictionsById = useCallback(async (stopId: string) => {
    setPredictionsLoading(true)
    try {
      const data = await apiFetch<MbtaPrediction[]>(`/mbta/predictions?stop=${encodeURIComponent(stopId)}`)
      setPredictions(Array.isArray(data) ? data : [])
    } catch (e) {
      console.error('Predictions fetch error:', e)
    } finally {
      setPredictionsLoading(false)
    }
  }, [])

  const fetchTripPredictions = useCallback(async (tripId: string) => {
    setPredictionsLoading(true)
    try {
      const data = await apiFetch<MbtaPrediction[]>(`/mbta/trip-predictions?trip=${encodeURIComponent(tripId)}`)
      setPredictions(Array.isArray(data) ? data : [])
    } catch (e) {
      console.error('Trip predictions fetch error:', e)
    } finally {
      setPredictionsLoading(false)
    }
  }, [])

  const toggleTrains = useCallback(() => {
    setTrainsVisible(prev => {
      if (!prev) {
        // Turning on: fetch commuter rail (type=2) + stations
        fetchVehicles(2, setTrains)
        fetchStations()
        trainTimer.current = setInterval(() => fetchVehicles(2, setTrains), RAIL_REFRESH)
      } else {
        if (trainTimer.current) { clearInterval(trainTimer.current); trainTimer.current = null }
        setTrains([])
      }
      return !prev
    })
  }, [fetchVehicles, fetchStations])

  const toggleSubway = useCallback(() => {
    setSubwayVisible(prev => {
      if (!prev) {
        // Fetch light rail (0) + heavy rail (1) combined
        const fetchBoth = async () => {
          const [light, heavy] = await Promise.all([
            apiFetch<MbtaVehicle[]>('/mbta/vehicles?route_type=0').catch(() => []),
            apiFetch<MbtaVehicle[]>('/mbta/vehicles?route_type=1').catch(() => []),
          ])
          setSubway([...(Array.isArray(light) ? light : []), ...(Array.isArray(heavy) ? heavy : [])])
        }
        fetchBoth()
        fetchStations()
        subwayTimer.current = setInterval(fetchBoth, RAIL_REFRESH)
      } else {
        if (subwayTimer.current) { clearInterval(subwayTimer.current); subwayTimer.current = null }
        setSubway([])
      }
      return !prev
    })
  }, [fetchStations])

  const toggleBuses = useCallback(() => {
    setBusesVisible(prev => {
      if (!prev) {
        fetchVehicles(3, setBuses)
        busTimer.current = setInterval(() => fetchVehicles(3, setBuses), BUS_REFRESH)
      } else {
        if (busTimer.current) { clearInterval(busTimer.current); busTimer.current = null }
        setBuses([])
        setBusStops([])
      }
      return !prev
    })
  }, [fetchVehicles])

  const selectVehicle = useCallback((v: MbtaVehicle) => {
    setSelectedVehicle(v)
    setSelectedStop(null)
    setPredictions([])
    // Fetch next stops along the vehicle's trip
    if (v.tripId) {
      fetchTripPredictions(v.tripId)
      // Auto-refresh trip predictions
      if (predTimer.current) clearInterval(predTimer.current)
      predTimer.current = setInterval(() => fetchTripPredictions(v.tripId), PRED_REFRESH)
    } else if (v.stopName) {
      fetchPredictionsByName(v.stopName)
    }
  }, [fetchTripPredictions, fetchPredictionsByName])

  const selectStop = useCallback((stop: MbtaStop) => {
    setSelectedStop(stop)
    setSelectedVehicle(null)
    // Rail stations: use stop_name to query all platforms; bus stops: use ID directly
    const isRailStation = stations.some(s => s.id === stop.id)
    if (isRailStation) {
      fetchPredictionsByName(stop.name)
      if (predTimer.current) clearInterval(predTimer.current)
      predTimer.current = setInterval(() => fetchPredictionsByName(stop.name), PRED_REFRESH)
    } else {
      fetchPredictionsById(stop.id)
      if (predTimer.current) clearInterval(predTimer.current)
      predTimer.current = setInterval(() => fetchPredictionsById(stop.id), PRED_REFRESH)
    }
  }, [stations, fetchPredictionsByName, fetchPredictionsById])

  const toggleFollow = useCallback(() => {
    setFollowingVehicleId(prev =>
      prev ? null : (selectedVehicle?.id || null)
    )
  }, [selectedVehicle])

  const clearSelection = useCallback(() => {
    setSelectedVehicle(null)
    setSelectedStop(null)
    setPredictions([])
    setFollowingVehicleId(null)
    if (predTimer.current) { clearInterval(predTimer.current); predTimer.current = null }
  }, [])

  // Update selectedVehicle with fresh data on each refresh
  const allVehicles = [...trains, ...subway, ...buses]
  if (selectedVehicle) {
    const fresh = allVehicles.find(v => v.id === selectedVehicle.id)
    if (fresh && (fresh.lat !== selectedVehicle.lat || fresh.lon !== selectedVehicle.lon)) {
      setSelectedVehicle(fresh)
    }
  }

  return {
    trains, subway, buses, stations, busStops,
    trainsVisible, subwayVisible, busesVisible,
    selectedVehicle, selectedStop, predictions, predictionsLoading,
    followingVehicleId,
    toggleTrains, toggleSubway, toggleBuses,
    selectVehicle, selectStop, clearSelection, toggleFollow,
    fetchBusStops, BUS_STOP_ZOOM,
  }
}
