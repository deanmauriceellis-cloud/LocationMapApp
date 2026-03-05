// (C) Dean Maurice Ellis, 2026 - Module useTransit.ts
import { useState, useRef, useCallback } from 'react'
import { apiFetch } from '@/config/api'
import type { MbtaVehicle, MbtaStop, MbtaPrediction } from '@/lib/types'

const RAIL_REFRESH = 15_000  // 15s for trains/subway
const BUS_REFRESH = 30_000   // 30s for buses
const PRED_REFRESH = 30_000  // 30s for predictions

export function useTransit() {
  const [trains, setTrains] = useState<MbtaVehicle[]>([])
  const [subway, setSubway] = useState<MbtaVehicle[]>([])
  const [buses, setBuses] = useState<MbtaVehicle[]>([])
  const [stations, setStations] = useState<MbtaStop[]>([])
  const [stationsLoaded, setStationsLoaded] = useState(false)

  const [trainsVisible, setTrainsVisible] = useState(false)
  const [subwayVisible, setSubwayVisible] = useState(false)
  const [busesVisible, setBusesVisible] = useState(false)

  const [selectedVehicle, setSelectedVehicle] = useState<MbtaVehicle | null>(null)
  const [selectedStop, setSelectedStop] = useState<MbtaStop | null>(null)
  const [predictions, setPredictions] = useState<MbtaPrediction[]>([])
  const [predictionsLoading, setPredictionsLoading] = useState(false)

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

  const fetchPredictions = useCallback(async (stopId: string) => {
    setPredictionsLoading(true)
    try {
      const data = await apiFetch<MbtaPrediction[]>(`/mbta/predictions?stop=${stopId}`)
      setPredictions(Array.isArray(data) ? data : [])
    } catch (e) {
      console.error('Predictions fetch error:', e)
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
      }
      return !prev
    })
  }, [fetchVehicles])

  const selectVehicle = useCallback((v: MbtaVehicle) => {
    setSelectedVehicle(v)
    setSelectedStop(null)
    setPredictions([])
  }, [])

  const selectStop = useCallback((stop: MbtaStop) => {
    setSelectedStop(stop)
    setSelectedVehicle(null)
    fetchPredictions(stop.id)
    // Auto-refresh predictions
    if (predTimer.current) clearInterval(predTimer.current)
    predTimer.current = setInterval(() => fetchPredictions(stop.id), PRED_REFRESH)
  }, [fetchPredictions])

  const clearSelection = useCallback(() => {
    setSelectedVehicle(null)
    setSelectedStop(null)
    setPredictions([])
    if (predTimer.current) { clearInterval(predTimer.current); predTimer.current = null }
  }, [])

  return {
    trains, subway, buses, stations,
    trainsVisible, subwayVisible, busesVisible,
    selectedVehicle, selectedStop, predictions, predictionsLoading,
    toggleTrains, toggleSubway, toggleBuses,
    selectVehicle, selectStop, clearSelection,
  }
}
