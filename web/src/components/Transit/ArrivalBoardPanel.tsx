// (C) Dean Maurice Ellis, 2026 - Module ArrivalBoardPanel.tsx
import type { MbtaPrediction } from '@/lib/types'

interface Props {
  stopName: string
  predictions: MbtaPrediction[]
  loading: boolean
  onClose: () => void
}

function formatTime(time: string | null): string {
  if (!time) return '—'
  const dt = new Date(time)
  const diff = Math.round((dt.getTime() - Date.now()) / 60_000)
  if (diff <= 0) return 'Now'
  if (diff < 60) return `${diff} min`
  return dt.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
}

/** Determine if this prediction is a departure (no arrival) or arrival */
function timeLabel(p: { arrivalTime: string | null; departureTime: string | null }): { text: string; tag: 'arr' | 'dep' } {
  if (p.arrivalTime && p.departureTime) {
    // Both present — show arrival (typical mid-route stop)
    return { text: formatTime(p.arrivalTime), tag: 'arr' }
  }
  if (p.departureTime && !p.arrivalTime) {
    // Departure only — origin/hub station
    return { text: formatTime(p.departureTime), tag: 'dep' }
  }
  if (p.arrivalTime && !p.departureTime) {
    // Arrival only — terminus
    return { text: formatTime(p.arrivalTime), tag: 'arr' }
  }
  return { text: '—', tag: 'arr' }
}

export function ArrivalBoardPanel({ stopName, predictions, loading, onClose }: Props) {
  return (
    <div className="absolute top-12 bottom-8 left-0 z-[1001] w-[360px] max-w-[85vw] bg-gray-900 border-r border-gray-700 flex flex-col shadow-xl text-white">
      {/* Header */}
      <div className="flex items-start gap-2 p-4 border-b border-gray-700">
        <div className="flex-1 min-w-0">
          <h2 className="font-semibold text-base text-gray-100 truncate">
            {stopName}
          </h2>
          <div className="text-xs text-gray-400 mt-0.5">
            Departures &amp; Arrivals {loading && '(updating...)'}
          </div>
        </div>
        <button
          onClick={onClose}
          className="w-8 h-8 shrink-0 flex items-center justify-center text-gray-400 hover:text-gray-300 rounded-lg hover:bg-gray-800"
        >
          <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M18 6 6 18M6 6l12 12" />
          </svg>
        </button>
      </div>

      {/* Predictions list */}
      <div className="flex-1 overflow-y-auto">
        {predictions.length === 0 && !loading && (
          <div className="p-4">
            <div className="text-sm text-gray-400">No upcoming departures or arrivals</div>
            <div className="text-xs text-gray-500 mt-2">
              Service may have ended for the night. Check back during operating hours.
            </div>
          </div>
        )}
        {predictions.map(p => (
          <div
            key={p.id}
            className="flex items-center gap-3 px-4 py-2.5 border-b border-gray-800 hover:bg-gray-800/50"
          >
            {/* Route color dot */}
            <div
              className="w-3 h-3 rounded-full shrink-0"
              style={{ backgroundColor: p.routeColor || '#6B7280' }}
            />
            {/* Route + headsign */}
            <div className="flex-1 min-w-0">
              <div className="text-sm font-medium text-gray-200 truncate">
                {p.routeId}
                <span className="text-gray-400 font-normal ml-2">{p.headsign}</span>
              </div>
              {p.status && (
                <div className="text-[10px] text-gray-500 mt-0.5">{p.status}</div>
              )}
            </div>
            {/* Time + arr/dep labels */}
            <div className="text-right shrink-0">
              {p.arrivalTime && p.departureTime ? (
                <>
                  <div className="text-sm font-mono text-amber-400">
                    {formatTime(p.arrivalTime)}
                    <span className="text-[9px] text-gray-500 ml-1">ARR</span>
                  </div>
                  <div className="text-sm font-mono text-sky-400">
                    {formatTime(p.departureTime)}
                    <span className="text-[9px] text-sky-500 ml-1">DEP</span>
                  </div>
                </>
              ) : (
                <>
                  <div className="text-sm font-mono text-amber-400">
                    {timeLabel(p).text}
                  </div>
                  <div className={`text-[9px] font-medium ${timeLabel(p).tag === 'dep' ? 'text-sky-400' : 'text-gray-500'}`}>
                    {timeLabel(p).tag === 'dep' ? 'DEP' : 'ARR'}
                  </div>
                </>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
