import { useState, useEffect } from 'react'
import { formatDistance } from '@/lib/distance'
import { resolveCategory, UNCATEGORIZED_COLOR } from '@/config/categories'
import type { FindResult, WebsiteInfo } from '@/lib/types'

interface Props {
  result: FindResult
  onClose: () => void
  onFlyTo: (lat: number, lon: number) => void
  onFetchWebsite: (type: string, id: number | string, name: string, lat: number, lon: number) => Promise<WebsiteInfo | null>
}

export function PoiDetailPanel({ result, onClose, onFlyTo, onFetchWebsite }: Props) {
  const [website, setWebsite] = useState<WebsiteInfo | null>(null)
  const [websiteLoading, setWebsiteLoading] = useState(false)
  const [websiteLoaded, setWebsiteLoaded] = useState(false)

  const cat = resolveCategory(result.category)
  const color = cat?.color || UNCATEGORIZED_COLOR

  const tags = result.tags || {}
  const address = tags['addr:street']
    ? `${tags['addr:housenumber'] || ''} ${tags['addr:street']}${tags['addr:city'] ? ', ' + tags['addr:city'] : ''}`.trim()
    : tags['addr:full'] || ''
  const phone = tags.phone || tags['contact:phone'] || ''
  const hours = tags.opening_hours || ''
  const cuisine = tags.cuisine?.replace(/;/g, ', ') || ''
  const typeLabel = [tags.amenity, tags.shop, tags.tourism, tags.leisure, tags.office, tags.craft, tags.historic].filter(Boolean).join(', ')

  // Auto-resolve website from tags first, then allow manual resolution
  const tagWebsite = tags.website || tags['contact:website'] || tags.url || ''

  const handleLoadWebsite = async () => {
    setWebsiteLoading(true)
    const info = await onFetchWebsite(result.type, result.id, result.name, result.lat, result.lon)
    setWebsite(info)
    setWebsiteLoaded(true)
    setWebsiteLoading(false)
  }

  // Reset state when result changes
  useEffect(() => {
    setWebsite(null)
    setWebsiteLoaded(false)
    setWebsiteLoading(false)
  }, [result.type, result.id])

  const handleDirections = () => {
    window.open(`https://www.google.com/maps/dir/?api=1&destination=${result.lat},${result.lon}`, '_blank')
  }

  const handleShare = async () => {
    const text = `${result.name} — ${cat?.label || 'Place'}`
    if (navigator.share) {
      try { await navigator.share({ title: result.name, text, url: window.location.href }) } catch {}
    } else {
      await navigator.clipboard.writeText(text)
    }
  }

  const resolvedUrl = tagWebsite || website?.url || ''
  const resolvedPhone = phone || website?.phone || ''
  const resolvedAddress = address || website?.address || ''
  const resolvedHours = hours || website?.hours || ''

  return (
    <div className="absolute top-12 bottom-8 left-0 z-[1001] w-[360px] max-w-[85vw] bg-white dark:bg-gray-900 border-r border-gray-200 dark:border-gray-700 flex flex-col shadow-xl">
      {/* Color bar + header */}
      <div className="h-1.5 shrink-0" style={{ backgroundColor: color }} />
      <div className="flex items-start gap-2 p-4 border-b border-gray-200 dark:border-gray-700">
        <div className="flex-1 min-w-0">
          <h2 className="font-semibold text-base text-gray-900 dark:text-gray-100 truncate">
            {result.name || 'Unnamed'}
          </h2>
          <div className="text-xs mt-0.5" style={{ color }}>
            {cat?.label || 'Other'}
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
          <InfoRow label="Distance" value={formatDistance(result.distance_m)} />
          {typeLabel && <InfoRow label="Type" value={typeLabel} />}
          {cuisine && <InfoRow label="Cuisine" value={cuisine} />}
          {resolvedAddress && <InfoRow label="Address" value={resolvedAddress} />}
          {resolvedPhone && (
            <InfoRow label="Phone">
              <a href={`tel:${resolvedPhone}`} className="text-teal-600 dark:text-teal-400 hover:underline">
                {resolvedPhone}
              </a>
            </InfoRow>
          )}
          {resolvedHours && <InfoRow label="Hours" value={resolvedHours} />}

          {/* Website */}
          {resolvedUrl ? (
            <InfoRow label="Website">
              <a
                href={resolvedUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="text-teal-600 dark:text-teal-400 hover:underline truncate block"
              >
                {resolvedUrl.replace(/^https?:\/\/(www\.)?/, '').replace(/\/$/, '').slice(0, 40)}
              </a>
            </InfoRow>
          ) : !websiteLoaded ? (
            <div>
              <button
                onClick={handleLoadWebsite}
                disabled={websiteLoading}
                className="text-sm text-teal-600 dark:text-teal-400 hover:underline disabled:opacity-50"
              >
                {websiteLoading ? 'Looking up...' : 'Find Website'}
              </button>
            </div>
          ) : null}
        </div>

        {/* Action buttons */}
        <div className="p-4 pt-0 grid grid-cols-2 gap-2">
          <ActionButton onClick={handleDirections} label="Directions" icon="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7" />
          {resolvedPhone && (
            <ActionButton onClick={() => window.open(`tel:${resolvedPhone}`)} label="Call" icon="M3 5a2 2 0 012-2h3.28a1 1 0 01.948.684l1.498 4.493a1 1 0 01-.502 1.21l-2.257 1.13a11.042 11.042 0 005.516 5.516l1.13-2.257a1 1 0 011.21-.502l4.493 1.498a1 1 0 01.684.949V19a2 2 0 01-2 2h-1C9.716 21 3 14.284 3 6V5z" />
          )}
          <ActionButton onClick={() => onFlyTo(result.lat, result.lon)} label="Map" icon="M17.657 16.657L13.414 20.9a1.998 1.998 0 01-2.827 0l-4.244-4.243a8 8 0 1111.314 0z M15 11a3 3 0 11-6 0 3 3 0 016 0z" />
          <ActionButton onClick={handleShare} label="Share" icon="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
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

function ActionButton({ onClick, label, icon }: { onClick: () => void; label: string; icon: string }) {
  return (
    <button
      onClick={onClick}
      className="flex items-center justify-center gap-1.5 px-3 py-2 text-xs font-medium rounded-lg bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700 transition-colors"
    >
      <svg viewBox="0 0 24 24" className="w-4 h-4" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
        <path d={icon} />
      </svg>
      {label}
    </button>
  )
}
