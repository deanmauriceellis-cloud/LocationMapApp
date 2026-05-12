// LocationMapApp v1.5
// Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
//
// TigerBaseTab — S252
//
// Operator-only preview of the CONUS TigerBase basemap baked by tools/tigerbase/.
// Stacks the 5 transparent tile layers (water, boundaries, roads, places, labels)
// served by cache-proxy `/tigerbase/:layer/:z/:x/:y`.
//
// Not wired to the Android app. This is the web preview path; the Android client
// is a separate post-V1 task gated behind SuperAdminMode.

import { useEffect, useMemo, useState } from 'react'
import { MapContainer, TileLayer, useMap } from 'react-leaflet'

const LAYERS = ['water', 'boundaries', 'roads', 'places', 'labels'] as const
type LayerName = typeof LAYERS[number]

interface ManifestScope {
  scope: string
  layers: Record<string, { minZoom: number; maxZoom: number; bbox: number[] } | { error: string }>
}

interface Manifest {
  tigerbase_out: string
  scopes: ManifestScope[]
}

function CenterOnScope({ bbox }: { bbox?: number[] }) {
  const map = useMap()
  useEffect(() => {
    if (!bbox || bbox.length !== 4) return
    map.fitBounds([[bbox[1], bbox[0]], [bbox[3], bbox[2]]])
  }, [bbox, map])
  return null
}

export default function TigerBaseTab() {
  const [manifest, setManifest] = useState<Manifest | null>(null)
  const [manifestErr, setManifestErr] = useState<string | null>(null)
  const [selectedScope, setSelectedScope] = useState<string>('conus')
  const [active, setActive] = useState<Record<LayerName, boolean>>({
    water: true, boundaries: true, roads: true, places: true, labels: true,
  })

  // Force tile-layer cache busts when scope changes
  const [scopeBust, setScopeBust] = useState(0)

  useEffect(() => {
    fetch('/api/tigerbase/manifest')
      .then(r => r.ok ? r.json() : Promise.reject(`${r.status}`))
      .then((m: Manifest) => {
        setManifest(m)
        if (m.scopes.length > 0 && !m.scopes.find(s => s.scope === selectedScope)) {
          setSelectedScope(m.scopes[0].scope)
        }
      })
      .catch(e => setManifestErr(String(e)))
  }, [])

  const scope = manifest?.scopes.find(s => s.scope === selectedScope)
  const bbox = useMemo(() => {
    if (!scope) return undefined
    const first = Object.values(scope.layers).find((l: any) => l?.bbox) as any
    return first?.bbox
  }, [scope])
  const zoomRange = useMemo(() => {
    if (!scope) return { min: 3, max: 14 }
    const vals = Object.values(scope.layers).filter((l: any) => l?.minZoom !== undefined) as any[]
    if (!vals.length) return { min: 3, max: 14 }
    return {
      min: Math.min(...vals.map(v => v.minZoom)),
      max: Math.max(...vals.map(v => v.maxZoom)),
    }
  }, [scope])

  if (manifestErr) {
    return (
      <div className="flex-1 min-h-0 p-6 text-sm text-rose-700">
        <p>Failed to load tigerbase manifest: <code>{manifestErr}</code></p>
        <p className="mt-2 text-slate-600">
          Make sure cache-proxy is running and a bake exists at <code>tools/tigerbase/out/</code>.
        </p>
      </div>
    )
  }
  if (!manifest || !scope) {
    return <div className="flex-1 min-h-0 p-6 text-sm text-slate-600">Loading tigerbase manifest…</div>
  }

  const initialCenter: [number, number] = bbox
    ? [(bbox[1] + bbox[3]) / 2, (bbox[0] + bbox[2]) / 2]
    : [39.5, -98]
  const initialZoom = Math.min(Math.max(zoomRange.min, 4), 6)

  return (
    <div className="flex-1 min-h-0 flex">
      {/* Left rail — scope picker + layer toggles */}
      <aside className="w-72 border-r border-slate-300 bg-white px-4 py-4 text-sm overflow-y-auto">
        <h2 className="text-base font-semibold text-slate-800 mb-2">TigerBase</h2>
        <p className="text-xs text-slate-600 mb-4">
          Preview of TIGER/Line raster basemap baked at <code className="text-[10px]">tools/tigerbase/out/</code>.
          Post-V1, server-served only.
        </p>

        <div className="mb-4">
          <label className="block text-xs uppercase tracking-wide text-slate-500 mb-1">Scope</label>
          <select
            value={selectedScope}
            onChange={e => { setSelectedScope(e.target.value); setScopeBust(b => b + 1) }}
            className="w-full border border-slate-300 rounded px-2 py-1 text-sm bg-white"
          >
            {manifest.scopes.map(s => (
              <option key={s.scope} value={s.scope}>{s.scope}</option>
            ))}
          </select>
          <div className="mt-1 text-[11px] text-slate-500">
            Zoom range: {zoomRange.min}–{zoomRange.max}
          </div>
        </div>

        <div className="mb-2 text-xs uppercase tracking-wide text-slate-500">Layers</div>
        <ul className="space-y-1">
          {LAYERS.map(layer => {
            const l = scope.layers[layer]
            const ok = l && !('error' in l)
            return (
              <li key={layer}>
                <label className={`flex items-center gap-2 ${ok ? '' : 'opacity-50'}`}>
                  <input
                    type="checkbox"
                    checked={active[layer] && !!ok}
                    disabled={!ok}
                    onChange={e => setActive(a => ({ ...a, [layer]: e.target.checked }))}
                  />
                  <span className="font-mono text-xs">{layer}</span>
                  {ok ? (
                    <span className="text-[10px] text-slate-500 ml-auto">
                      z{(l as any).minZoom}-{(l as any).maxZoom}
                    </span>
                  ) : (
                    <span className="text-[10px] text-rose-600 ml-auto">missing</span>
                  )}
                </label>
              </li>
            )
          })}
        </ul>

        <hr className="my-4 border-slate-200" />
        <div className="text-[11px] text-slate-500">
          Tiles served by cache-proxy via{' '}
          <code>/tigerbase/&lt;layer&gt;/&#123;z&#125;/&#123;x&#125;/&#123;y&#125;</code>.
          Layered architecture: each layer is a separate transparent WebP pyramid,
          composited by Leaflet here. Adjust visibility above.
        </div>
      </aside>

      {/* Map */}
      <div className="flex-1 min-h-0 relative">
        <MapContainer
          center={initialCenter}
          zoom={initialZoom}
          minZoom={zoomRange.min}
          maxZoom={zoomRange.max}
          className="w-full h-full"
          worldCopyJump={false}
        >
          <CenterOnScope bbox={bbox} key={`fit-${scopeBust}`} />
          {LAYERS.map(layer => {
            const l = scope.layers[layer]
            if (!l || 'error' in l) return null
            if (!active[layer]) return null
            return (
              <TileLayer
                key={`${selectedScope}-${layer}-${scopeBust}`}
                url={`/api/tigerbase/${layer}/{z}/{x}/{y}?scope=${encodeURIComponent(selectedScope)}`}
                tileSize={256}
                minZoom={(l as any).minZoom}
                maxZoom={(l as any).maxZoom}
                opacity={1}
                noWrap={true}
                attribution="© U.S. Census Bureau TIGER/Line"
                zIndex={LAYERS.indexOf(layer) + 1}
              />
            )
          })}
        </MapContainer>
      </div>
    </div>
  )
}
