// (C) Dean Maurice Ellis, 2026 - Module VehicleDetailPanel.tsx
import { getRouteColor, routeTypeLabel, vehicleStatusLabel } from '@/config/transit'
import type { MbtaVehicle } from '@/lib/types'

interface Props {
  vehicle: MbtaVehicle
  onArrivals: (stopName: string) => void
  onFlyTo: (lat: number, lon: number) => void
  onClose: () => void
}

export function VehicleDetailPanel({ vehicle: v, onArrivals, onFlyTo, onClose }: Props) {
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

        {/* Action buttons */}
        <div className="p-4 pt-0 grid grid-cols-2 gap-2">
          {v.stopName && (
            <button
              onClick={() => onArrivals(v.stopName)}
              className="flex items-center justify-center gap-1.5 px-3 py-2 text-xs font-medium rounded-lg bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
            >
              <svg viewBox="0 0 24 24" className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
                <line x1="16" y1="2" x2="16" y2="6" />
                <line x1="8" y1="2" x2="8" y2="6" />
                <line x1="3" y1="10" x2="21" y2="10" />
              </svg>
              Arrivals
            </button>
          )}
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
