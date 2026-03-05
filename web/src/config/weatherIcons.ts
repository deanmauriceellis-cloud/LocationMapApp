import React from 'react'

type IconFn = (size?: number) => React.ReactElement

function svg(children: React.ReactNode[], size = 24): React.ReactElement {
  return React.createElement('svg', {
    viewBox: '0 0 24 24',
    width: size,
    height: size,
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.5,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
  }, ...children)
}

// Sun
const sun: IconFn = (s = 24) => svg([
  React.createElement('circle', { key: 'c', cx: 12, cy: 12, r: 4, fill: '#facc15', stroke: '#facc15' }),
  React.createElement('path', { key: 'r', d: 'M12 2v2m0 16v2M4.22 4.22l1.42 1.42m12.72 12.72l1.42 1.42M2 12h2m16 0h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42', stroke: '#facc15' }),
], s)

// Moon
const moon: IconFn = (s = 24) => svg([
  React.createElement('path', { key: 'm', d: 'M21 12.79A9 9 0 1111.21 3 7 7 0 0021 12.79z', fill: '#94a3b8', stroke: '#94a3b8' }),
], s)

// Cloud
const cloud: IconFn = (s = 24) => svg([
  React.createElement('path', { key: 'cl', d: 'M18 10h-1.26A8 8 0 109 20h9a5 5 0 000-10z', fill: '#94a3b8', stroke: '#64748b' }),
], s)

// Few clouds day (sun + small cloud)
const fewDay: IconFn = (s = 24) => svg([
  React.createElement('circle', { key: 'c', cx: 10, cy: 8, r: 3.5, fill: '#facc15', stroke: '#facc15' }),
  React.createElement('path', { key: 'r', d: 'M10 2.5v1.5m0 8v1.5M4.5 8H3m14 0h-1.5M5.4 4.4l1 1m7.2 7.2l1 1M5.4 11.6l1-1m7.2-7.2l1-1', stroke: '#facc15', strokeWidth: 1.2 }),
  React.createElement('path', { key: 'cl', d: 'M19 14h-1a6 6 0 00-11.5 1H6a3.5 3.5 0 000 7h13a3.5 3.5 0 000-7z', fill: '#cbd5e1', stroke: '#94a3b8' }),
], s)

// Few clouds night (moon + small cloud)
const fewNight: IconFn = (s = 24) => svg([
  React.createElement('path', { key: 'm', d: 'M15 4.79A6 6 0 107.21 9 4.5 4.5 0 0015 4.79z', fill: '#94a3b8', stroke: '#94a3b8', strokeWidth: 1 }),
  React.createElement('path', { key: 'cl', d: 'M19 14h-1a6 6 0 00-11.5 1H6a3.5 3.5 0 000 7h13a3.5 3.5 0 000-7z', fill: '#cbd5e1', stroke: '#64748b' }),
], s)

// Rain
const rain: IconFn = (s = 24) => svg([
  React.createElement('path', { key: 'cl', d: 'M18 8h-1.26A8 8 0 109 18h9a5 5 0 000-10z', fill: '#94a3b8', stroke: '#64748b' }),
  React.createElement('path', { key: 'r1', d: 'M8 19v3', stroke: '#3b82f6', strokeWidth: 2 }),
  React.createElement('path', { key: 'r2', d: 'M12 19v3', stroke: '#3b82f6', strokeWidth: 2 }),
  React.createElement('path', { key: 'r3', d: 'M16 19v3', stroke: '#3b82f6', strokeWidth: 2 }),
], s)

// Thunderstorm
const tsra: IconFn = (s = 24) => svg([
  React.createElement('path', { key: 'cl', d: 'M18 8h-1.26A8 8 0 109 18h9a5 5 0 000-10z', fill: '#64748b', stroke: '#475569' }),
  React.createElement('path', { key: 'b', d: 'M13 16l-2 4h4l-2 4', stroke: '#facc15', strokeWidth: 2, fill: 'none' }),
], s)

// Snow
const snow: IconFn = (s = 24) => svg([
  React.createElement('path', { key: 'cl', d: 'M18 8h-1.26A8 8 0 109 18h9a5 5 0 000-10z', fill: '#cbd5e1', stroke: '#94a3b8' }),
  React.createElement('circle', { key: 's1', cx: 8, cy: 20, r: 1, fill: '#e2e8f0', stroke: 'none' }),
  React.createElement('circle', { key: 's2', cx: 12, cy: 21, r: 1, fill: '#e2e8f0', stroke: 'none' }),
  React.createElement('circle', { key: 's3', cx: 16, cy: 20, r: 1, fill: '#e2e8f0', stroke: 'none' }),
  React.createElement('circle', { key: 's4', cx: 10, cy: 23, r: 1, fill: '#e2e8f0', stroke: 'none' }),
  React.createElement('circle', { key: 's5', cx: 14, cy: 23, r: 1, fill: '#e2e8f0', stroke: 'none' }),
], s)

// Fog
const fog: IconFn = (s = 24) => svg([
  React.createElement('path', { key: 'f1', d: 'M4 10h16', stroke: '#94a3b8', strokeWidth: 2 }),
  React.createElement('path', { key: 'f2', d: 'M4 14h16', stroke: '#94a3b8', strokeWidth: 2 }),
  React.createElement('path', { key: 'f3', d: 'M6 18h12', stroke: '#94a3b8', strokeWidth: 2 }),
  React.createElement('path', { key: 'f4', d: 'M6 6h12', stroke: '#94a3b8', strokeWidth: 2 }),
], s)

// Wind
const wind: IconFn = (s = 24) => svg([
  React.createElement('path', { key: 'w1', d: 'M9.59 4.59A2 2 0 1111 8H2', stroke: '#64748b', strokeWidth: 2 }),
  React.createElement('path', { key: 'w2', d: 'M12.59 19.41A2 2 0 1014 16H2', stroke: '#64748b', strokeWidth: 2 }),
  React.createElement('path', { key: 'w3', d: 'M17.73 7.73A2.5 2.5 0 1119.5 12H2', stroke: '#64748b', strokeWidth: 2 }),
], s)

// Icon code → render function mapping
const ICON_MAP: Record<string, { day: IconFn; night: IconFn }> = {
  skc:   { day: sun, night: moon },
  few:   { day: fewDay, night: fewNight },
  sct:   { day: fewDay, night: fewNight },
  bkn:   { day: cloud, night: cloud },
  ovc:   { day: cloud, night: cloud },
  rain:  { day: rain, night: rain },
  tsra:  { day: tsra, night: tsra },
  snow:  { day: snow, night: snow },
  fzra:  { day: rain, night: rain },
  sleet: { day: rain, night: rain },
  fog:   { day: fog, night: fog },
  haze:  { day: fog, night: fog },
  smoke: { day: fog, night: fog },
  dust:  { day: fog, night: fog },
  wind_skc:  { day: wind, night: wind },
  wind_few:  { day: wind, night: wind },
  wind_sct:  { day: wind, night: wind },
  wind_bkn:  { day: wind, night: wind },
  wind_ovc:  { day: wind, night: wind },
  hot:   { day: sun, night: sun },
  cold:  { day: snow, night: snow },
  blizzard: { day: snow, night: snow },
  tropical_storm: { day: tsra, night: tsra },
  hurricane: { day: tsra, night: tsra },
}

export function getWeatherIcon(code: string, isDaytime: boolean, size?: number): React.ReactElement {
  const entry = ICON_MAP[code]
  if (!entry) {
    // Default: sun by day, moon by night
    return isDaytime ? sun(size) : moon(size)
  }
  return isDaytime ? entry.day(size) : entry.night(size)
}
