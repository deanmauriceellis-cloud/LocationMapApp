// (C) Dean Maurice Ellis, 2026 - Module VehicleDetailPanel.tsx
import { getRouteColor, routeTypeLabel, vehicleStatusLabel } from '@/config/transit'
import type { MbtaVehicle, MbtaPrediction } from '@/lib/types'

interface Props {
  vehicle: MbtaVehicle
  predictions: MbtaPrediction[]
  following: boolean
  onToggleFollow: () => void
  onFlyTo: (lat: number, lon: number) => void
  onClose: () => void
}

function formatArrival(time: string | null): string {
  if (!time) return '—'
  const dt = new Date(time)
  const diff = Math.round((dt.getTime() - Date.now()) / 60_000)
  if (diff <= 0) return 'Now'
  if (diff < 60) return `${diff} min`
  return dt.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
}

export function VehicleDetailPanel({ vehicle: v, predictions, following, onToggleFollow, onFlyTo, onClose }: Props) {
  const color = getRouteColor(v.routeId, v.routeType)
  const statusText = `${vehicleStatusLabel(v.status)} ${v.stopName}`
  const typeText = routeTypeLabel(v.routeType)
  const age = v.updatedAt ? Math.round((Date.now() - new Date(v.updatedAt).getTime()) / 1000) : null

  return (
    <div className="absolute top-12 bottom-8 left-0 z-[1001] w-[360px] max-w-[85vw] bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-700 flex flex-col shadow-xl">
      {/* Color bar + header */}
      <div className="h-1.5 shrink-0" style={{ backgroundColor: color }} />
      <div className="flex items-start gap-2 p-4 border-b border-gray-200 dark:border-gray-700">
        <div className="flex-1 min-w-0">
          <h2 className="font-semibold text-base text-gray-900 dark:text-gray-100">
            {v.routeName || v.routeId}
          </h2>
          <div className="text-xs mt-0.5" style={{ color }}>
            {v.headsign || typeText}
          </div>
        </div>
        <button
          onClick={onClose}
          className="w-8 h-8 shrink-0 flex items-center justify-center text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800"
        >
          <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M18 6 6 18M6 6l12 12" />
          </svg>
        </button>
      </div>

      {/* Info rows */}
      <div className="flex-1 overflow-y-auto">
        <div className="p-4 space-y-3">
          <InfoRow label="Vehicle" value={v.label} />
          <InfoRow label="Status" value={statusText} />
          <InfoRow label="Type" value={typeText} />
          {v.speed != null && <InfoRow label="Speed" value={`${Math.round(v.speed * 2.237)} mph`} />}
          {age != null && (
            <InfoRow label="Last Update" value={age < 60 ? `${age}s ago` : `${Math.round(age / 60)}m ago`} />
          )}
        </div>

        {/* Next stops along the route */}
        {predictions.length > 0 && (
          <div className="px-4 pb-3">
            <div className="text-[10px] font-medium text-gray-400 dark:text-gray-500 uppercase tracking-wider mb-2">
              Next Stops
            </div>
            <div className="space-y-1.5">
              {predictions.slice(0, 5).map((p, i) => (
                <div key={p.id} className="flex items-center gap-2 text-sm">
                  <div className="w-5 h-5 rounded-full shrink-0 flex items-center justify-center text-[9px] font-bold text-white"
                    style={{ backgroundColor: p.routeColor || color }}
                  >
                    {i + 1}
                  </div>
                  <span className="flex-1 text-gray-700 dark:text-gray-300 truncate">
                    {p.stopName || p.headsign || p.routeId}
                  </span>
                  <span className="text-xs font-mono text-teal-600 dark:text-teal-400 shrink-0">
                    {formatArrival(p.arrivalTime || p.departureTime)}
                  </span>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Action buttons */}
        <div className="p-4 pt-0 grid grid-cols-2 gap-2">
          <button
            onClick={onToggleFollow}
            className={`flex items-center justify-center gap-1.5 px-3 py-2 text-xs font-medium rounded-lg transition-colors ${
              following
                ? 'bg-teal-600 text-white hover:bg-teal-700'
                : 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700'
            }`}
          >
            <svg viewBox="0 0 24 24" className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
            {following ? 'Following' : 'Follow'}
          </button>
          <button
            onClick={() => onFlyTo(v.lat, v.lon)}
            className="flex items-center justify-center gap-1.5 px-3 py-2 text-xs font-medium rounded-lg bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
          >
            <svg viewBox="0 0 24 24" className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
              <path d="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
            </svg>
            Map
          </button>
        </div>
      </div>
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-[10px] font-medium text-gray-400 dark:text-gray-500 uppercase tracking-wider">{label}</div>
      <div className="text-sm text-gray-800 dark:text-gray-200 mt-0.5">{value}</div>
    </div>
  )
}
