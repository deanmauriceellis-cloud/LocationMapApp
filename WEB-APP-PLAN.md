# Web App — Development Plan

## Overview
Cross-platform web frontend (React 19 + TypeScript + Vite + Leaflet) consuming the existing cache proxy API. Located in `web/` alongside the Android app and cache proxy.

## Phase 1: Map + POI Markers + Dark Mode — DONE (v1.5.61)
- Interactive map with OSM light / CartoDB Dark Matter tiles
- POI markers from `/pois/bbox` with 17 category colors
- Labels at zoom >= 16, dark mode toggle, geolocation
- 20 files, 368KB / 112KB gzip

## Phase 2: Find + Search + POI Detail — DONE (v1.5.62)
- Find panel: search bar + 4-col category grid + count badges + subtype drill-down + results
- Fuzzy search: 1s debounce, `/db/pois/search`, distance-sorted results
- POI detail panel: info rows, website resolution, action buttons (Directions/Call/Map/Share)
- Filter and Map: exclusive marker display with forced labels, teal status bar
- Marker click → detail panel with haversine distance
- 5 new files + 7 modified, 385KB / 117KB gzip

## Phase 3: Weather Overlay — DONE (v1.5.63)
- Weather panel (slide-in, same position as Find): current conditions, 48-hour hourly, 7-day daily, expandable alert banners
- Consumes `/weather?lat=&lon=` composite endpoint, auto-refresh every 5min
- 15 SVG weather icon variants (day/night) for NWS icon codes
- Weather toolbar button: dynamic conditions icon + red dot on alerts
- METAR markers: flight-category colored (VFR/MVFR/IFR/LIFR), monospace labels at zoom >= 10
- Radar overlay: RainViewer API tiles at 35% opacity (replaced Iowa State Mesonet)
- Animated radar: 7-frame loop at 800ms via Leaflet API + RainViewer historical frames
- Layer controls: Radar/Animate/METAR toggles at bottom of weather panel
- Alert banner in status bar (red, click opens weather panel)
- 5 new files + 6 modified, 404KB / 121KB gzip

## Phase 4: Aircraft + Transit
- Aircraft markers from `/aircraft` (rotated icons, altitude-colored, callsign labels)
- Aircraft detail panel (click marker → info + follow button)
- Flight path trail (altitude-colored polyline)
- MBTA transit: live vehicle markers (buses, trains, subway)
- Station/stop tap → arrival board
- Layer toggles in toolbar or settings panel

## Phase 5: Auth + Social
- Device-bonded registration (same as Android: register once, JWT tokens)
- POI comments: star ratings, votes, in POI detail panel
- Real-time chat: Socket.IO, room list, global room
- Profile panel: display name, avatar initial, role badge
- Requires auth token storage (localStorage + refresh logic)

## Phase 6: Favorites + Offline + SEO
- Favorites: star in POI detail, localStorage persistence, dedicated view
- Recent searches / search history
- URL routing: shareable links for map position + selected POI
- SEO: meta tags, Open Graph, structured data

## Phase 7: PWA + Mobile
- Service worker for offline map tile caching
- PWA manifest: installable on mobile home screen
- Push notifications (geofence alerts, chat messages)
- Responsive design polish for mobile/tablet

## Phase 8: Monetization + Production
- AdMob web equivalent (Google AdSense or similar)
- Premium tier gating (same as Android paid tier)
- Production build pipeline: CDN deployment, environment configs
- Analytics: page views, feature usage, error tracking
- Performance: bundle splitting, lazy loading, image optimization
