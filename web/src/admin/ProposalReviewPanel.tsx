/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * S218 — Proposal Review Panel
 *
 * Small floating panel that appears bottom-right of the map after the
 * operator clicks "Validate via TigerLine" in PoiEditDialog. Shows the
 * POI name + address + Tiger rating + drift, and offers Accept / Cancel
 * actions plus a hint that the fuchsia "?" pin is draggable to fine-tune.
 *
 * Stays out of the way of the map (max-w-sm, bottom-4 right-4) so the
 * operator can still pan/zoom around the proposed location.
 */
import type { PoiRow } from './PoiTree'

interface ProposalReviewPanelProps {
  poi: PoiRow
  /** Effective pin position. dragLat/dragLng track the operator's mid-drag
   *  state; they fall back to the stored proposal when null. */
  dragLat: number | null
  dragLng: number | null
  acceptBusy: boolean
  cancelBusy: boolean
  errorMessage: string | null
  onAccept: () => void
  onCancel: () => void
  onRecenter: () => void
}

// S218 — drift thresholds (meters) for the in-panel sanity warning. Salem
// proper is ~5 km radius; anything more than 1 km of drift is suspicious,
// > 5 km is almost certainly a wrong-town match (Tiger rating-18 fuzzy hits
// caught operator at 33 km mid-S218).
const DRIFT_AMBER_M = 1000
const DRIFT_ROSE_M = 5000

function haversineMeters(
  lat1: number, lng1: number, lat2: number, lng2: number,
): number {
  const toRad = (d: number) => d * Math.PI / 180
  const R = 6371000
  const dLat = toRad(lat2 - lat1)
  const dLng = toRad(lng2 - lng1)
  const a = Math.sin(dLat / 2) ** 2
    + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2
  return R * 2 * Math.asin(Math.sqrt(a))
}

export function ProposalReviewPanel({
  poi, dragLat, dragLng,
  acceptBusy, cancelBusy, errorMessage,
  onAccept, onCancel, onRecenter,
}: ProposalReviewPanelProps) {
  const propLat = dragLat ?? (poi.lat_proposed ?? null)
  const propLng = dragLng ?? (poi.lng_proposed ?? null)
  const drifted = (dragLat != null && dragLat !== poi.lat_proposed)
    || (dragLng != null && dragLng !== poi.lng_proposed)
  const fmt = (n: number | null | undefined): string =>
    n == null ? '—' : Number(n).toFixed(7)
  const busy = acceptBusy || cancelBusy
  // Effective drift: distance from current to the pin's actual position
  // (drag-aware). Falls back to the stored location_drift_m on the
  // ProposalReview entry frame before any drag.
  const effectiveDriftM = (propLat != null && propLng != null)
    ? haversineMeters(poi.lat, poi.lng, propLat, propLng)
    : (poi.location_drift_m != null ? Number(poi.location_drift_m) : 0)
  const driftWarning: 'rose' | 'amber' | null =
    effectiveDriftM >= DRIFT_ROSE_M ? 'rose'
      : effectiveDriftM >= DRIFT_AMBER_M ? 'amber'
        : null

  return (
    <div className="absolute bottom-4 right-4 z-[600]
                    w-[22rem] max-w-[90vw]
                    bg-white rounded-lg shadow-2xl border border-fuchsia-300
                    p-3 text-xs text-slate-800">
      <div className="flex items-center justify-between mb-2">
        <div className="text-[11px] uppercase tracking-wide text-fuchsia-700 font-semibold">
          Validate location
        </div>
        <div className="flex items-center gap-3">
          {/* S218 — cross-reference: open Google Maps in a new tab so the
              operator can compare what Google places at this address vs.
              what Tiger proposed. Pure client-side window.open — no
              server-side request to Google. */}
          {typeof poi.address === 'string' && poi.address.length > 0 && (
            <a
              href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(poi.address)}`}
              target="_blank"
              rel="noopener noreferrer"
              className="text-[10px] text-fuchsia-700 hover:underline"
              title="Open this address in Google Maps (new tab) to cross-check Tiger's proposal"
            >
              Ask Google ↗
            </a>
          )}
          <button
            type="button"
            onClick={onRecenter}
            disabled={busy}
            className="text-[10px] text-fuchsia-700 hover:underline disabled:opacity-50"
            title="Re-center the map on the proposed location at zoom 20"
          >
            Re-center ↻
          </button>
        </div>
      </div>

      <div className="font-medium text-sm text-slate-900 truncate" title={poi.name}>
        {poi.name}
      </div>
      {typeof poi.address === 'string' && poi.address.length > 0 && (
        <div className="text-[11px] text-slate-500 truncate" title={poi.address}>
          {poi.address}
        </div>
      )}

      <div className="mt-2 grid grid-cols-2 gap-x-3 gap-y-1 font-mono text-[11px]">
        <div>
          <div className="text-slate-400">Current</div>
          <div>{fmt(poi.lat)}</div>
          <div>{fmt(poi.lng)}</div>
        </div>
        <div>
          <div className="text-slate-400">
            Proposed{drifted ? ' (dragged)' : ''}
          </div>
          <div className="text-fuchsia-700">{fmt(propLat)}</div>
          <div className="text-fuchsia-700">{fmt(propLng)}</div>
        </div>
      </div>

      <div className="mt-2 text-[11px] text-slate-600 font-sans">
        {poi.location_source && (
          <span
            className={[
              'mr-3 px-1.5 py-0.5 rounded text-[10px] uppercase tracking-wide font-semibold',
              poi.location_source === 'google'
                ? 'bg-sky-100 text-sky-800'
                : 'bg-fuchsia-100 text-fuchsia-800',
            ].join(' ')}
            title="Source of the proposed coordinates"
          >
            {poi.location_source === 'google' ? 'Google' :
             poi.location_source === 'tigerline_street_fallback' ? 'Tiger (street fallback)' :
             poi.location_source === 'tigerline' ? 'Tiger' : poi.location_source}
          </span>
        )}
        {poi.location_geocoder_rating != null && (
          <span title="Tiger geocoder rating: 0=exact match, 100=city centroid">
            rating <strong>{poi.location_geocoder_rating}</strong>
          </span>
        )}
        <span
          className={[
            'ml-3',
            driftWarning === 'rose' ? 'text-rose-700 font-semibold'
              : driftWarning === 'amber' ? 'text-amber-700 font-semibold'
                : '',
          ].join(' ')}
          title="Distance from current stored coords to the pin"
        >
          drift <strong>
            {effectiveDriftM >= 1000
              ? `${(effectiveDriftM / 1000).toFixed(1)} km`
              : `${effectiveDriftM.toFixed(1)} m`}
          </strong>
        </span>
      </div>

      {driftWarning === 'rose' && (
        <div className="mt-2 px-2 py-1.5 text-[11px] rounded bg-rose-50 border border-rose-200 text-rose-800">
          <strong>⚠ {(effectiveDriftM / 1000).toFixed(1)} km drift</strong> — Tiger
          likely matched a street with the same name in another town. Verify
          the address before accepting.
        </div>
      )}
      {driftWarning === 'amber' && (
        <div className="mt-2 px-2 py-1.5 text-[11px] rounded bg-amber-50 border border-amber-200 text-amber-800">
          {(effectiveDriftM / 1000).toFixed(1)} km drift is unusual for a Salem POI — double-check before accepting.
        </div>
      )}

      <p className="mt-2 text-[10px] text-slate-500 italic">
        Drag the fuchsia <span className="text-fuchsia-700">?</span> pin to fine-tune,
        then Accept to commit (writes to the POI's lat/lng).
      </p>

      {errorMessage && (
        <p className="mt-2 text-[11px] text-rose-700">{errorMessage}</p>
      )}

      <div className="mt-3 flex gap-2">
        <button
          type="button"
          onClick={onAccept}
          disabled={busy}
          className={[
            'flex-1 px-3 py-1.5 text-xs font-medium rounded',
            busy
              ? 'bg-slate-100 text-slate-400 cursor-not-allowed'
              : 'bg-emerald-600 text-white hover:bg-emerald-700',
          ].join(' ')}
        >
          {acceptBusy ? 'Accepting…' : 'Accept'}
        </button>
        <button
          type="button"
          onClick={onCancel}
          disabled={busy}
          className={[
            'flex-1 px-3 py-1.5 text-xs font-medium rounded border',
            busy
              ? 'bg-slate-100 text-slate-400 border-slate-200 cursor-not-allowed'
              : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50',
          ].join(' ')}
        >
          {cancelBusy ? 'Cancelling…' : 'Cancel'}
        </button>
      </div>
    </div>
  )
}
