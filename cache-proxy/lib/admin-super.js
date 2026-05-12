/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * SuperAdmin endpoints (S248).
 *
 * Operator-only console support for the parked V1+ external services
 * (weather / MBTA / aircraft / METAR / webcams / TFRs / NEXRAD radar).
 * V1 Android ships fully offline so these routes have no consumer; the
 * SuperAdmin tab in the web admin uses this metadata block to decide
 * which services to surface and whether their API keys are present.
 *
 * Routes (gated by /admin Basic Auth via app.use('/admin', ...) in server.js):
 *   GET /admin/super-admin/health
 *        Returns { generatedAt, services: [...] } — pure metadata,
 *        no upstream calls. Drives the env-var indicator + per-card
 *        config in SuperAdminTab.tsx.
 */
const MODULE_ID = '(C) Destructive AI Gurus, LLC, 2026 - Module admin-super.js';

// Service registry — the single source of truth for what the SuperAdmin
// tab can exercise. id matches the React-side card key. envKey is the
// .env variable read by the corresponding lib/<svc>.js module; null
// means no key required (NWS / METAR / TFR are unauthenticated).
// proxyPath is the cache-proxy route the React tab calls via the Vite
// /api proxy (e.g. /api/weather → cache-proxy /weather).
const SERVICES = [
  {
    id: 'weather',
    label: 'Weather',
    icon: '🌦',
    upstream: 'api.weather.gov (NWS)',
    envKey: null,
    proxyPath: '/weather',
    defaultQuery: { lat: 42.5197, lon: -70.8967 },
    notes: 'No API key required. Returns current + hourly + daily + alerts.',
  },
  {
    id: 'metar',
    label: 'METAR',
    icon: '✈',
    upstream: 'aviationweather.gov',
    envKey: null,
    proxyPath: '/metar',
    defaultQuery: { bbox: '42.50,-70.92,42.55,-70.86' },
    notes: 'No API key required. Returns aviation surface obs in bbox.',
  },
  {
    id: 'aircraft',
    label: 'Aircraft (live)',
    icon: '🛩',
    upstream: 'opensky-network.org',
    envKey: 'OPENSKY_CLIENT_ID',
    envKey2: 'OPENSKY_CLIENT_SECRET',
    proxyPath: '/aircraft',
    defaultQuery: { bbox: '42.50,-70.92,42.55,-70.86' },
    notes: 'OAuth2 (anonymous fallback heavily rate-limited). 15s cache TTL.',
  },
  {
    id: 'db-aircraft',
    label: 'Aircraft (DB history)',
    icon: '📜',
    upstream: 'local PG aircraft_sightings',
    envKey: 'DATABASE_URL',
    proxyPath: '/db/aircraft/recent',
    defaultQuery: { limit: 20 },
    notes: 'Reads sightings table; populated by /aircraft writes.',
  },
  {
    id: 'mbta-vehicles',
    label: 'MBTA Vehicles',
    icon: '🚌',
    upstream: 'api-v3.mbta.com',
    envKey: 'MBTA_API_KEY',
    proxyPath: '/mbta/vehicles',
    defaultQuery: { route_type: 3 },
    notes: 'route_type 0=LightRail 1=HeavyRail 2=CommuterRail 3=Bus.',
  },
  {
    id: 'mbta-stations',
    label: 'MBTA Stations',
    icon: '🚉',
    upstream: 'api-v3.mbta.com',
    envKey: 'MBTA_API_KEY',
    proxyPath: '/mbta/stations',
    defaultQuery: { route_type: '0,1,2' },
    notes: '1h cache TTL. Comma-list of route_types accepted.',
  },
  {
    id: 'webcams',
    label: 'Webcams',
    icon: '📷',
    upstream: 'api.windy.com',
    envKey: 'WINDY_API_KEY',
    proxyPath: '/webcams',
    defaultQuery: {
      s: 42.50, w: -70.92, n: 42.55, e: -70.86, categories: 'traffic',
    },
    notes: '10min cache TTL. categories=traffic|landscape|city|etc.',
  },
  {
    id: 'tfrs',
    label: 'TFRs (FAA)',
    icon: '🚫',
    upstream: 'tfr.faa.gov',
    envKey: null,
    proxyPath: '/tfrs',
    defaultQuery: { bbox: '42.50,-70.92,42.55,-70.86' },
    notes: 'No API key. HTML/AIXM scrape. Often empty for Salem — that is OK.',
  },
];

module.exports = function (app /*, deps */) {
  app.get('/admin/super-admin/health', (req, res) => {
    const services = SERVICES.map((svc) => {
      const envKeyPresent = svc.envKey ? !!process.env[svc.envKey] : null;
      const envKey2Present = svc.envKey2 ? !!process.env[svc.envKey2] : null;
      return {
        id: svc.id,
        label: svc.label,
        icon: svc.icon,
        upstream: svc.upstream,
        envKey: svc.envKey,
        envKeyPresent,
        envKey2: svc.envKey2 || null,
        envKey2Present,
        proxyPath: svc.proxyPath,
        defaultQuery: svc.defaultQuery,
        notes: svc.notes,
      };
    });
    res.json({ generatedAt: new Date().toISOString(), services });
  });
};
