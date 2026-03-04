/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module weather.js';

module.exports = function(app, deps) {
  const { cacheGet, cacheSet, stats, log } = deps;

  const NWS_UA = { 'User-Agent': 'LocationMapApp/1.5 contact@example.com', 'Accept': 'application/geo+json' };
  const NWS_TTL_POINTS   = 24 * 60 * 60 * 1000;  // 24h
  const NWS_TTL_CURRENT  =  5 * 60 * 1000;        // 5min
  const NWS_TTL_FORECAST = 30 * 60 * 1000;        // 30min
  const NWS_TTL_ALERTS   =  5 * 60 * 1000;        // 5min

  function extractNwsIconCode(iconUrl) {
    if (!iconUrl) return 'unknown';
    const m = iconUrl.match(/\/icons\/land\/(?:day|night)\/([^,?/]+)/);
    return m ? m[1] : 'unknown';
  }

  function isDaytimeFromIcon(iconUrl) {
    if (!iconUrl) return true;
    return iconUrl.includes('/day/');
  }

  function celsiusToF(c) { return c != null ? Math.round(c * 9 / 5 + 32) : null; }
  function kmhToMph(k) { return k != null ? Math.round(k * 0.621371) : null; }
  function paToInHg(p) { return p != null ? +(p / 3386.39).toFixed(2) : null; }
  function metersToMiles(m) { return m != null ? +(m / 1609.344).toFixed(1) : null; }

  function parseWindSpeed(s) {
    return s || '';
  }

  function extractPrecipProb(period) {
    if (period.probabilityOfPrecipitation && period.probabilityOfPrecipitation.value != null) {
      return period.probabilityOfPrecipitation.value;
    }
    return 0;
  }

  function degToCompass(deg) {
    if (deg == null) return '';
    const dirs = ['N','NNE','NE','ENE','E','ESE','SE','SSE','S','SSW','SW','WSW','W','WNW','NW','NNW'];
    return dirs[Math.round(deg / 22.5) % 16];
  }

  app.get('/weather', async (req, res) => {
    const lat = parseFloat(req.query.lat);
    const lon = parseFloat(req.query.lon);
    if (isNaN(lat) || isNaN(lon)) return res.status(400).json({ error: 'Must specify lat and lon' });

    // Snap to 2 decimal places for cache keys (~1km resolution)
    const sLat = lat.toFixed(2);
    const sLon = lon.toFixed(2);
    const pointKey = `weather:point:${sLat},${sLon}`;

    try {
      // Step 1: Get grid coordinates + station (24h cache)
      let pointData = cacheGet(pointKey, NWS_TTL_POINTS);
      if (!pointData) {
        const pointResp = await fetch(`https://api.weather.gov/points/${sLat},${sLon}`, { headers: NWS_UA });
        if (!pointResp.ok) {
          const text = await pointResp.text();
          console.error(`[Weather] /points failed: ${pointResp.status} ${text.substring(0, 200)}`);
          return res.status(502).json({ error: 'NWS points lookup failed', status: pointResp.status });
        }
        const pointJson = await pointResp.json();
        const props = pointJson.properties;
        pointData = {
          data: {
            gridId: props.gridId,
            gridX: props.gridX,
            gridY: props.gridY,
            city: props.relativeLocation?.properties?.city || '',
            state: props.relativeLocation?.properties?.state || '',
            station: props.observationStations // URL to list
          }
        };
        cacheSet(pointKey, JSON.stringify(pointData.data));
        pointData = { data: JSON.stringify(pointData.data) };
      }
      stats.hits++; // count the composite request
      const grid = JSON.parse(pointData.data);

      // Resolve nearest station (from the station list URL, also cached with points)
      const stationKey = `weather:station:${grid.gridId}:${grid.gridX},${grid.gridY}`;
      let stationId;
      const cachedStation = cacheGet(stationKey, NWS_TTL_POINTS);
      if (cachedStation) {
        stationId = cachedStation.data;
      } else if (grid.station) {
        const stResp = await fetch(grid.station, { headers: NWS_UA });
        if (stResp.ok) {
          const stJson = await stResp.json();
          stationId = stJson.features?.[0]?.properties?.stationIdentifier || 'UNKNOWN';
        } else {
          stationId = 'UNKNOWN';
        }
        cacheSet(stationKey, stationId);
      } else {
        stationId = 'UNKNOWN';
      }

      // Step 2-5: Fetch remaining data in parallel with per-section TTLs
      const currentKey  = `weather:current:${stationId}`;
      const hourlyKey   = `weather:hourly:${grid.gridId}:${grid.gridX},${grid.gridY}`;
      const dailyKey    = `weather:daily:${grid.gridId}:${grid.gridX},${grid.gridY}`;
      const alertKey    = `weather:alerts:${sLat},${sLon}`;

      const [currentData, hourlyData, dailyData, alertData] = await Promise.all([
        // Current conditions (5min)
        (async () => {
          const cached = cacheGet(currentKey, NWS_TTL_CURRENT);
          if (cached) return JSON.parse(cached.data);
          try {
            const r = await fetch(`https://api.weather.gov/stations/${stationId}/observations/latest`, { headers: NWS_UA });
            if (!r.ok) return null;
            const j = await r.json();
            const p = j.properties;
            const result = {
              temperature: celsiusToF(p.temperature?.value),
              temperatureUnit: 'F',
              humidity: p.relativeHumidity?.value != null ? Math.round(p.relativeHumidity.value) : null,
              windSpeed: kmhToMph(p.windSpeed?.value),
              windDirection: p.windDirection?.value != null ? degToCompass(p.windDirection.value) : null,
              windChill: celsiusToF(p.windChill?.value),
              heatIndex: celsiusToF(p.heatIndex?.value),
              dewpoint: celsiusToF(p.dewpoint?.value),
              description: p.textDescription || '',
              iconCode: extractNwsIconCode(p.icon),
              isDaytime: isDaytimeFromIcon(p.icon),
              visibility: metersToMiles(p.visibility?.value),
              barometer: paToInHg(p.barometricPressure?.value)
            };
            cacheSet(currentKey, JSON.stringify(result));
            return result;
          } catch (e) { console.error('[Weather] current conditions error:', e.message); return null; }
        })(),
        // Hourly forecast (30min)
        (async () => {
          const cached = cacheGet(hourlyKey, NWS_TTL_FORECAST);
          if (cached) return JSON.parse(cached.data);
          try {
            const r = await fetch(`https://api.weather.gov/gridpoints/${grid.gridId}/${grid.gridX},${grid.gridY}/forecast/hourly`, { headers: NWS_UA });
            if (!r.ok) return [];
            const j = await r.json();
            const periods = (j.properties?.periods || []).slice(0, 48).map(p => ({
              time: p.startTime,
              temperature: p.temperature,
              windSpeed: parseWindSpeed(p.windSpeed),
              windDirection: p.windDirection || '',
              precipProbability: extractPrecipProb(p),
              shortForecast: p.shortForecast || '',
              iconCode: extractNwsIconCode(p.icon),
              isDaytime: p.isDaytime
            }));
            cacheSet(hourlyKey, JSON.stringify(periods));
            return periods;
          } catch (e) { console.error('[Weather] hourly forecast error:', e.message); return []; }
        })(),
        // Daily forecast (30min)
        (async () => {
          const cached = cacheGet(dailyKey, NWS_TTL_FORECAST);
          if (cached) return JSON.parse(cached.data);
          try {
            const r = await fetch(`https://api.weather.gov/gridpoints/${grid.gridId}/${grid.gridX},${grid.gridY}/forecast`, { headers: NWS_UA });
            if (!r.ok) return [];
            const j = await r.json();
            const periods = (j.properties?.periods || []).map(p => ({
              name: p.name,
              isDaytime: p.isDaytime,
              temperature: p.temperature,
              windSpeed: parseWindSpeed(p.windSpeed),
              shortForecast: p.shortForecast || '',
              detailedForecast: p.detailedForecast || '',
              iconCode: extractNwsIconCode(p.icon),
              precipProbability: extractPrecipProb(p)
            }));
            cacheSet(dailyKey, JSON.stringify(periods));
            return periods;
          } catch (e) { console.error('[Weather] daily forecast error:', e.message); return []; }
        })(),
        // Location-specific alerts (5min)
        (async () => {
          const cached = cacheGet(alertKey, NWS_TTL_ALERTS);
          if (cached) return JSON.parse(cached.data);
          try {
            const r = await fetch(`https://api.weather.gov/alerts/active?point=${sLat},${sLon}`, { headers: NWS_UA });
            if (!r.ok) return [];
            const j = await r.json();
            const alerts = (j.features || []).map(f => {
              const p = f.properties;
              return {
                id: p.id || '',
                event: p.event || '',
                severity: p.severity || '',
                urgency: p.urgency || '',
                headline: p.headline || '',
                description: p.description || '',
                instruction: p.instruction || '',
                effective: p.effective || '',
                expires: p.expires || '',
                areaDesc: p.areaDesc || ''
              };
            });
            cacheSet(alertKey, JSON.stringify(alerts));
            return alerts;
          } catch (e) { console.error('[Weather] alerts error:', e.message); return []; }
        })()
      ]);

      const result = {
        location: { city: grid.city, state: grid.state, station: stationId },
        current: currentData,
        hourly: hourlyData,
        daily: dailyData,
        alerts: alertData,
        fetchedAt: new Date().toISOString()
      };

      log('/weather', false, 0, `${stationId} ${grid.city},${grid.state} alerts=${(alertData||[]).length}`);
      res.json(result);
    } catch (err) {
      console.error('[Weather] composite error:', err.message);
      res.status(502).json({ error: 'Weather fetch failed', detail: err.message });
    }
  });
};
