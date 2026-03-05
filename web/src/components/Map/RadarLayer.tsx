import { useEffect, useRef } from 'react'
import { useMap } from 'react-leaflet'
import L from 'leaflet'

const RAINVIEWER_API = 'https://api.rainviewer.com/public/weather-maps.json'
const ANIMATION_MS = 800
// color=2 (universal blue), smooth=1, snow=1
const TILE_OPTS = '/256/{z}/{x}/{y}/2/1_1.png'

interface RainViewerFrame {
  time: number
  path: string
}

interface Props {
  visible: boolean
  animating: boolean
}

export function RadarLayer({ visible, animating }: Props) {
  const map = useMap()
  const staticRef = useRef<L.TileLayer | null>(null)
  const animLayersRef = useRef<L.TileLayer[]>([])
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const frameRef = useRef(0)
  const framesRef = useRef<RainViewerFrame[]>([])
  const hostRef = useRef('')

  // Fetch available frames from RainViewer
  useEffect(() => {
    if (!visible) return
    let cancelled = false
    fetch(RAINVIEWER_API)
      .then(r => r.json())
      .then(data => {
        if (cancelled) return
        hostRef.current = data.host || 'https://tilecache.rainviewer.com'
        framesRef.current = data.radar?.past || []
      })
      .catch(e => console.error('RainViewer API error:', e))
    return () => { cancelled = true }
  }, [visible])

  // Static radar layer (latest frame)
  useEffect(() => {
    if (!visible || animating) return
    const frames = framesRef.current
    const host = hostRef.current
    if (!host || frames.length === 0) {
      // Frames not loaded yet — retry after a short delay
      const retry = setTimeout(() => {
        if (!framesRef.current.length || !hostRef.current) return
        const latest = framesRef.current[framesRef.current.length - 1]
        const url = hostRef.current + latest.path + TILE_OPTS
        const layer = L.tileLayer(url, { opacity: 0.35, maxZoom: 18, zIndex: 100 })
        layer.addTo(map)
        staticRef.current = layer
      }, 500)
      return () => {
        clearTimeout(retry)
        if (staticRef.current) {
          map.removeLayer(staticRef.current)
          staticRef.current = null
        }
      }
    }
    const latest = frames[frames.length - 1]
    const url = host + latest.path + TILE_OPTS
    const layer = L.tileLayer(url, { opacity: 0.35, maxZoom: 18, zIndex: 100 })
    layer.addTo(map)
    staticRef.current = layer
    return () => {
      map.removeLayer(layer)
      staticRef.current = null
    }
  }, [visible, animating, map])

  // Animated radar layers
  useEffect(() => {
    if (!visible || !animating) return

    const host = hostRef.current
    const frames = framesRef.current
    if (!host || frames.length === 0) {
      // Frames not loaded yet — retry after a short delay
      const retry = setTimeout(() => startAnimation(), 500)
      return () => { clearTimeout(retry) }
    }

    const cleanup = startAnimation()
    return cleanup

    function startAnimation() {
      const host = hostRef.current
      const frames = framesRef.current
      if (!host || frames.length === 0) return () => {}

      // Use last 7 frames (or all if fewer)
      const useFrames = frames.slice(-7)
      const layers: L.TileLayer[] = []

      for (const frame of useFrames) {
        const url = host + frame.path + TILE_OPTS
        const layer = L.tileLayer(url, { opacity: 0, maxZoom: 18, zIndex: 100 })
        layer.addTo(map)
        layers.push(layer)
      }
      animLayersRef.current = layers

      // Show first frame
      frameRef.current = 0
      layers[0].setOpacity(0.35)

      // Cycle frames
      const timer = setInterval(() => {
        const prev = frameRef.current
        const next = (prev + 1) % layers.length
        layers[prev].setOpacity(0)
        layers[next].setOpacity(0.35)
        frameRef.current = next
      }, ANIMATION_MS)
      timerRef.current = timer

      return () => {
        clearInterval(timer)
        timerRef.current = null
        layers.forEach(l => map.removeLayer(l))
        animLayersRef.current = []
      }
    }
  }, [visible, animating, map])

  return null
}
