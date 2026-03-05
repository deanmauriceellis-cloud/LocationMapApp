// (C) Dean Maurice Ellis, 2026 - Module AircraftDetailPanel.tsx
import {
  getAltitudeColor,
  headingToCompass,
  msToMph,
  metersToFeet,
  vertRateToFpm,
  isEmergencySquawk,
} from '@/config/aircraft'
import type { AircraftState, AircraftHistory } from '@/lib/types'

interface Props {
  aircraft: AircraftState
  history: AircraftHistory | null
  historyLoading: boolean
  following: boolean
  onToggleFollow: () => void
  onFlyTo: (lat: number, lon: number) => void
  onClose: () => void
}

export function AircraftDetailPanel({
  aircraft: ac,
  history,
  historyLoading,
  following,
  onToggleFollow,
  onFlyTo,
  onClose,
}: Props) {
  const color = getAltitudeColor(ac.baroAlt, ac.onGround)
  const callsign = ac.callsign || ac.icao24
  const emergencySquawk = isEmergencySquawk(ac.squawk)

  return (
    <div className="absolute top-12 bottom-8 left-0 z-[1001] w-[360px] max-w-[85vw] bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-700 flex flex-col shadow-xl">
      {/* Color bar + header */}
      <div className="h-1.5 shrink-0" style={{ backgroundColor: color }} />
      <div className="flex items-start gap-2 p-4 border-b border-gray-200 dark:border-gray-700">
        <div className="flex-1 min-w-0">
          <h2 className="font-semibold text-base text-gray-900 dark:text-gray-100 font-mono">
            {callsign}
          </h2>
          <div className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
            {ac.icao24.toUpperCase()} &middot; {ac.originCountry}
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
          <InfoRow label="Status" value={ac.onGround ? 'On Ground' : 'Airborne'} />
          <InfoRow label="Altitude" value={metersToFeet(ac.baroAlt)} />
          {ac.geoAlt != null && <InfoRow label="Geo Altitude" value={metersToFeet(ac.geoAlt)} />}
          <InfoRow label="Speed" value={msToMph(ac.velocity)} />
          <InfoRow
            label="Heading"
            value={ac.track != null ? `${Math.round(ac.track)}° ${headingToCompass(ac.track)}` : '—'}
          />
          <InfoRow label="Vertical Rate" value={vertRateToFpm(ac.vertRate)} />
          {ac.squawk && (
            <InfoRow label="Squawk">
              <span className={emergencySquawk ? 'text-red-600 dark:text-red-400 font-bold' : ''}>
                {ac.squawk}
                {ac.squawk === '7500' && ' (Hijack)'}
                {ac.squawk === '7600' && ' (Radio Failure)'}
                {ac.squawk === '7700' && ' (Emergency)'}
              </span>
            </InfoRow>
          )}
          {ac.spi && (
            <InfoRow label="SPI">
              <span className="text-red-600 dark:text-red-400 font-semibold">Active</span>
            </InfoRow>
          )}

          {/* History section */}
          {historyLoading && (
            <div className="text-xs text-gray-400">Loading history...</div>
          )}
          {history && (
            <>
              <div className="border-t border-gray-200 dark:border-gray-700 pt-3 mt-3">
                <div className="text-[10px] font-medium text-gray-400 dark:text-gray-500 uppercase tracking-wider mb-1">
                  Sighting History
                </div>
                <div className="text-sm text-gray-800 dark:text-gray-200">
                  {history.totalSightings} sighting{history.totalSightings !== 1 ? 's' : ''}
                </div>
                {history.callsigns.length > 0 && (
                  <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                    Callsigns: {history.callsigns.join(', ')}
                  </div>
                )}
                <div className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                  First: {new Date(history.firstSeen).toLocaleDateString()}
                  {' — Last: '}
                  {new Date(history.lastSeen).toLocaleDateString()}
                </div>
              </div>
            </>
          )}
        </div>

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
            onClick={() => onFlyTo(ac.lat, ac.lon)}
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

function InfoRow({ label, value, children }: { label: string; value?: string; children?: React.ReactNode }) {
  return (
    <div>
      <div className="text-[10px] font-medium text-gray-400 dark:text-gray-500 uppercase tracking-wider">{label}</div>
      <div className="text-sm text-gray-800 dark:text-gray-200 mt-0.5">
        {children || value}
      </div>
    </div>
  )
}
