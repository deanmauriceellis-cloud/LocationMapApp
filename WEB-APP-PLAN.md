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

## Phase 4: Aircraft + Transit — DONE (v1.5.65)
- Aircraft markers from `/aircraft` (DivIcon with rotated airplane SVG, altitude-colored, callsign labels)
- Aircraft detail panel (altitude/speed/heading/squawk, follow button, sighting history)
- Flight path trail (altitude-colored polyline segments from DB history)
- MBTA transit: live vehicle markers (trains/subway/buses) as route-colored CircleMarkers
- Station dots at zoom >= 12 (rail), bus stop dots at zoom >= 15 (max 200 per viewport, bbox-filtered)
- Click station → arrival/departure board with DEP/ARR labels, service-ended message when no predictions
- Click vehicle → detail panel with next 5 stops along route (trip-based predictions)
- Vehicle follow mode: map centers on vehicle position each refresh cycle
- Selected vehicle highlighting: teal border ring + permanent detail label
- Layers dropdown: 4 toggle switches (Aircraft/Trains/Subway/Buses) with count badges
- Server-side POI clustering: >1000 POIs → SQL grid aggregation (~77 clusters vs 28k markers)
- Detail panel mutual exclusion: POI/Aircraft/Vehicle/ArrivalBoard (only one shows at a time)
- Auto-refresh: aircraft 15s, trains/subway 15s, buses 30s, predictions 30s
- Status bar: per-layer counts (aircraft/trains/subway/buses) alongside POI count
- Proxy endpoints: `/mbta/vehicles`, `/mbta/stations`, `/mbta/predictions`, `/mbta/trip-predictions`, `/mbta/bus-stops/bbox`
- 12 new files + 6 modified + 2 proxy files modified

## Phase 5: Auth + Social — DONE (v1.5.66)
- Auth system: register/login modal, JWT tokens in localStorage, auto-refresh with 2-min buffer, singleton de-duplication, 401 retry
- `authFetch<T>()` wrapper: Bearer header injection, proactive refresh, auto-retry
- Profile dropdown: avatar initial (teal), display name, role badge, sign-out
- POI comments: star ratings (1-5, interactive), upvote/downvote with color feedback, delete for owner/staff, relative time
- CommentsSection embedded below POI detail action buttons, auto-loads on POI open
- Real-time chat: Socket.IO with JWT auth, room list, create room, typing indicator
- Chat panel: room list → chat room views, own messages teal/right, others gray/left
- Toolbar: Chat (speech bubble) + Profile (user icon/initial) buttons
- Panel mutual exclusion: Chat/Find/Weather share left panel slot
- 9 new files + 5 modified, 499KB / 147KB gzip

## Phase 6: Favorites + URL Routing — DONE (v1.5.68)
- Favorites: star in POI detail header (amber filled/outline), localStorage persistence (`lma_favorites`), toggle on/off
- Favorites cell in Find panel category grid (first position, gold amber, count badge), tap to browse sorted by distance
- URL routing: `?lat=&lon=&z=&poi=` search params, debounced replaceState on map move
- POI deep linking: `?poi=way/123` auto-fetches and opens detail panel on load
- Share button copies full URL (with poi param) to clipboard
- 2 new files + 5 modified, 506KB / 149KB gzip

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
