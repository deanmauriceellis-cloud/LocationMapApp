import { useState } from 'react'
import { getWeatherIcon } from '@/config/weatherIcons'
import type { WeatherData, WeatherAlert } from '@/lib/types'

type Tab = 'current' | 'hourly' | 'daily'

interface Props {
  open: boolean
  weather: WeatherData | null
  loading: boolean
  radarOn: boolean
  radarAnimating: boolean
  metarsVisible: boolean
  onToggleRadar: () => void
  onToggleRadarAnimate: () => void
  onToggleMetars: () => void
  onClose: () => void
}

export function WeatherPanel({
  open, weather, loading,
  radarOn, radarAnimating, metarsVisible,
  onToggleRadar, onToggleRadarAnimate, onToggleMetars,
  onClose,
}: Props) {
  const [tab, setTab] = useState<Tab>('current')
  const [alertExpanded, setAlertExpanded] = useState<string | null>(null)

  if (!open) return null

  const loc = weather?.location
  const cur = weather?.current
  const alerts = weather?.alerts || []

  return (
    <div className="absolute top-12 bottom-8 left-0 z-[1001] w-[360px] max-w-[85vw] bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-700 flex flex-col shadow-xl transition-transform duration-200">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200 dark:border-gray-700">
        <div>
          <div className="text-sm font-semibold text-gray-800 dark:text-gray-100">
            {loc ? `${loc.city}, ${loc.state}` : 'Weather'}
          </div>
          {loc?.station && (
            <div className="text-[10px] text-gray-400 dark:text-gray-500">
              Station: {loc.station}
            </div>
          )}
        </div>
        <button
          onClick={onClose}
          className="w-7 h-7 flex items-center justify-center rounded-lg text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800"
        >
          <svg viewBox="0 0 24 24" className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M18 6 6 18M6 6l12 12" />
          </svg>
        </button>
      </div>

      {/* Alert banner */}
      {alerts.length > 0 && (
        <div className="border-b border-red-300 dark:border-red-800">
          {alerts.map((alert) => (
            <AlertBanner
              key={alert.id}
              alert={alert}
              expanded={alertExpanded === alert.id}
              onToggle={() => setAlertExpanded(alertExpanded === alert.id ? null : alert.id)}
            />
          ))}
        </div>
      )}

      {/* Loading */}
      {loading && !weather && (
        <div className="flex-1 flex items-center justify-center text-sm text-gray-500 dark:text-gray-400">
          Loading weather...
        </div>
      )}

      {/* Content */}
      {weather && (
        <>
          {/* Tab bar */}
          <div className="flex border-b border-gray-200 dark:border-gray-700">
            {(['current', 'hourly', 'daily'] as Tab[]).map((t) => (
              <button
                key={t}
                onClick={() => setTab(t)}
                className={`flex-1 py-2 text-xs font-medium capitalize transition-colors ${
                  tab === t
                    ? 'text-teal-600 dark:text-teal-400 border-b-2 border-teal-500'
                    : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
                }`}
              >
                {t}
              </button>
            ))}
          </div>

          {/* Tab content */}
          <div className="flex-1 overflow-y-auto">
            {tab === 'current' && cur && <CurrentTab current={cur} />}
            {tab === 'current' && !cur && (
              <div className="px-4 py-8 text-center text-sm text-gray-500">No current data</div>
            )}
            {tab === 'hourly' && <HourlyTab hours={weather.hourly} />}
            {tab === 'daily' && <DailyTab days={weather.daily} />}
          </div>

          {/* Layer controls */}
          <div className="border-t border-gray-200 dark:border-gray-700 px-4 py-2 flex flex-col gap-1.5">
            <div className="text-[10px] text-gray-400 dark:text-gray-500 uppercase tracking-wide">Layers</div>
            <div className="flex gap-2">
              <LayerToggle label="Radar" active={radarOn} onClick={onToggleRadar} />
              <LayerToggle label="Animate" active={radarAnimating} onClick={onToggleRadarAnimate} disabled={!radarOn} />
              <LayerToggle label="METAR" active={metarsVisible} onClick={onToggleMetars} />
            </div>
          </div>
        </>
      )}
    </div>
  )
}

function AlertBanner({ alert, expanded, onToggle }: { alert: WeatherAlert; expanded: boolean; onToggle: () => void }) {
  const severity = alert.severity?.toLowerCase()
  const bg = severity === 'extreme' || severity === 'severe'
    ? 'bg-red-600 dark:bg-red-800 text-white'
    : 'bg-orange-500 dark:bg-orange-700 text-white'

  return (
    <div>
      <button onClick={onToggle} className={`w-full px-4 py-2 text-left text-xs font-medium flex items-center gap-2 ${bg}`}>
        <svg viewBox="0 0 24 24" className="w-4 h-4 flex-shrink-0" fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
          <line x1="12" y1="9" x2="12" y2="13" />
          <line x1="12" y1="17" x2="12.01" y2="17" />
        </svg>
        <span className="flex-1">{alert.event}</span>
        <svg viewBox="0 0 24 24" className={`w-3 h-3 transition-transform ${expanded ? 'rotate-180' : ''}`} fill="none" stroke="currentColor" strokeWidth="2">
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>
      {expanded && (
        <div className="px-4 py-2 text-xs bg-red-50 dark:bg-red-900/30 text-gray-800 dark:text-gray-200 space-y-1 max-h-48 overflow-y-auto">
          <div className="font-medium">{alert.headline}</div>
          {alert.areaDesc && <div className="text-gray-500 dark:text-gray-400">Area: {alert.areaDesc}</div>}
          {alert.instruction && <div className="mt-1">{alert.instruction}</div>}
          {alert.description && <div className="mt-1 text-gray-600 dark:text-gray-300 whitespace-pre-line">{alert.description}</div>}
        </div>
      )}
    </div>
  )
}

function CurrentTab({ current }: { current: NonNullable<WeatherData['current']> }) {
  return (
    <div className="p-4">
      {/* Main display */}
      <div className="flex items-center gap-4 mb-4">
        <div className="text-gray-700 dark:text-gray-200">
          {getWeatherIcon(current.iconCode, current.isDaytime, 48)}
        </div>
        <div>
          <div className="text-3xl font-light text-gray-800 dark:text-gray-100">
            {current.temperature != null ? `${current.temperature}°F` : '--'}
          </div>
          <div className="text-sm text-gray-500 dark:text-gray-400">{current.description}</div>
        </div>
      </div>

      {/* Detail grid */}
      <div className="grid grid-cols-2 gap-3">
        <DetailRow label="Humidity" value={current.humidity != null ? `${current.humidity}%` : '--'} />
        <DetailRow label="Wind" value={current.windSpeed != null ? `${current.windDirection || ''} ${current.windSpeed} mph` : '--'} />
        <DetailRow label="Visibility" value={current.visibility != null ? `${current.visibility} mi` : '--'} />
        <DetailRow label="Barometer" value={current.barometer != null ? `${current.barometer} inHg` : '--'} />
        <DetailRow label="Dewpoint" value={current.dewpoint != null ? `${current.dewpoint}°F` : '--'} />
        <DetailRow
          label={current.heatIndex != null ? 'Heat Index' : 'Wind Chill'}
          value={
            current.heatIndex != null ? `${current.heatIndex}°F`
            : current.windChill != null ? `${current.windChill}°F`
            : '--'
          }
        />
      </div>
    </div>
  )
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="bg-gray-50 dark:bg-gray-800 rounded-lg px-3 py-2">
      <div className="text-[10px] text-gray-400 dark:text-gray-500 uppercase">{label}</div>
      <div className="text-sm font-medium text-gray-700 dark:text-gray-200">{value}</div>
    </div>
  )
}

function HourlyTab({ hours }: { hours: WeatherData['hourly'] }) {
  if (hours.length === 0) return <div className="px-4 py-8 text-center text-sm text-gray-500">No hourly data</div>

  return (
    <div className="divide-y divide-gray-100 dark:divide-gray-800">
      {hours.map((h, i) => {
        const d = new Date(h.time)
        const time = d.toLocaleTimeString([], { hour: 'numeric', hour12: true })
        const day = d.toLocaleDateString([], { weekday: 'short' })
        return (
          <div key={i} className="flex items-center px-4 py-2 gap-3 text-sm">
            <div className="w-14 text-gray-500 dark:text-gray-400 text-xs">
              <div>{time}</div>
              <div className="text-[10px] text-gray-400 dark:text-gray-500">{day}</div>
            </div>
            <div className="w-6 text-gray-600 dark:text-gray-300">
              {getWeatherIcon(h.iconCode, h.isDaytime, 20)}
            </div>
            <div className="w-10 font-medium text-gray-800 dark:text-gray-100">{h.temperature}°</div>
            <div className="flex-1 text-xs text-gray-500 dark:text-gray-400 truncate">{h.shortForecast}</div>
            {h.precipProbability > 0 && (
              <div className="text-xs text-blue-500">{h.precipProbability}%</div>
            )}
            <div className="text-xs text-gray-400 dark:text-gray-500 w-16 text-right">{h.windSpeed}</div>
          </div>
        )
      })}
    </div>
  )
}

function DailyTab({ days }: { days: WeatherData['daily'] }) {
  if (days.length === 0) return <div className="px-4 py-8 text-center text-sm text-gray-500">No forecast data</div>

  return (
    <div className="divide-y divide-gray-100 dark:divide-gray-800">
      {days.map((d, i) => (
        <div key={i} className="px-4 py-3">
          <div className="flex items-center gap-3 mb-1">
            <div className="w-6 text-gray-600 dark:text-gray-300">
              {getWeatherIcon(d.iconCode, d.isDaytime, 20)}
            </div>
            <div className="font-medium text-sm text-gray-800 dark:text-gray-100 flex-1">{d.name}</div>
            <div className="text-sm font-medium text-gray-800 dark:text-gray-100">{d.temperature}°</div>
          </div>
          <div className="text-xs text-gray-500 dark:text-gray-400 ml-9">{d.shortForecast}</div>
          <div className="flex gap-3 ml-9 mt-1 text-[10px] text-gray-400 dark:text-gray-500">
            {d.precipProbability > 0 && <span className="text-blue-500">{d.precipProbability}% precip</span>}
            <span>Wind: {d.windSpeed}</span>
          </div>
        </div>
      ))}
    </div>
  )
}

function LayerToggle({ label, active, onClick, disabled }: { label: string; active: boolean; onClick: () => void; disabled?: boolean }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`flex-1 px-2 py-1.5 text-xs font-medium rounded-lg transition-colors ${
        disabled
          ? 'text-gray-300 dark:text-gray-600 cursor-not-allowed'
          : active
          ? 'bg-teal-100 dark:bg-teal-900/50 text-teal-700 dark:text-teal-300'
          : 'text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-800'
      }`}
    >
      {label}
    </button>
  )
}
