# LocationMapApp v1.5 — Intellectual Property Register

**Author:** Dean Maurice Ellis
**Copyright:** (c) 2026 Dean Maurice Ellis. All rights reserved.
**Date:** March 3, 2026
**Status:** Proprietary — not licensed for redistribution

---

## 1. Adaptive Radius Cap-Retry Algorithm

**Key Files:** `PlacesRepository.kt`, `server.js`

**Description:** A self-tuning search radius algorithm for POI discovery via the Overpass API. The system begins with a proxy-hinted radius and dynamically adjusts based on result density. When too many results are returned (API overload), the radius is halved and retried. When too few results are returned, the radius expands. A minimum radius floor (100m) prevents infinite shrinking, while adaptive 20km fuzzy matching ensures coverage in sparse areas.

**Novelty:** Existing geocoding APIs use fixed radii. This system creates a feedback loop where the search area self-calibrates to local POI density without prior knowledge of the area, avoiding both API overload and under-discovery.

**Patent Claim Angle:** A method for dynamically adjusting geospatial search radii based on real-time result density feedback, comprising cap-retry halving on overload, proxy-hinted initial radius estimation, and minimum radius floor constraints.

---

## 2. Probe-Calibrate-Spiral Scanner

**Key Files:** `MainActivity.kt`

**Description:** A multi-phase area scanner for systematic POI discovery. Phase 1 (Probe) sends a single query to estimate local POI density. Phase 2 (Calibrate) uses the probe results to compute optimal cell size for the area. Phase 3 (Spiral) executes a recursive 3x3 grid subdivision pattern, scanning outward in a spiral from the user's location with 30-second pacing to respect API rate limits. The scanner preserves state for pause/resume across app lifecycle events.

**Novelty:** Conventional area-scanning approaches use fixed grids. This system adapts grid density to local conditions before scanning begins, then uses spiral ordering to prioritize nearby areas while maintaining systematic coverage.

**Patent Claim Angle:** A method for area-adaptive geospatial scanning comprising density probing, grid calibration based on probe results, and spiral-ordered recursive subdivision with rate-limited execution and state preservation.

---

## 3. 10km Probe Expanding Spiral

**Key Files:** `MainActivity.kt`

**Description:** A large-scale discovery algorithm that uses an expanding spiral pattern of 10km-wide probe points for efficient low-density area exploration. The system estimates fill radius from initial probe results to determine how far each discovery point's results extend, minimizing redundant queries in sparsely populated regions.

**Novelty:** Unlike uniform grid scanning, this approach uses wide probes specifically calibrated for low-density areas, with fill radius estimation to avoid redundant queries where POIs are sparse.

**Patent Claim Angle:** A method for large-scale geospatial discovery using expanding spiral probes with fill radius estimation for adaptive coverage of low-density regions.

---

## 4. Idle Auto-Populate with Density Guard

**Key Files:** `MainActivity.kt`

**Description:** An intelligent background population system that detects 10 minutes of GPS stationarity (user not moving) and automatically triggers the full Probe-Calibrate-Spiral scanner with extended 45-second delays. A density guard checks whether 100+ POIs already exist within 10km before starting, preventing redundant scanning of already-populated areas. Any UI interaction immediately resets the idle timer and halts scanning. Scanner state is preserved for resume after interruption.

**Novelty:** The combination of GPS stationarity detection, density-aware gate, and interruptible scanning with state preservation creates an autonomous discovery system that operates only when beneficial and never when the user is actively engaged.

**Patent Claim Angle:** A method for autonomous geospatial data population comprising GPS stationarity detection, local density evaluation as a pre-scan gate, interruptible execution with state preservation, and adaptive delay timing.

---

## 5. JTS R-Tree Geofence Engine

**Key Files:** `GeofenceEngine.kt`

**Description:** A high-performance on-device geofence evaluation engine using JTS Topology Suite's R-tree spatial index. The engine supports 5 live zone types (TFR, military, school, exclusion camera, DJI no-fly) and evaluates device position against 220,000+ polygon boundaries in real time. Zone entry/exit events trigger configurable alerts with zone-specific metadata.

**Novelty:** Mobile geofence systems typically use simple circle-based proximity checks with limited zone counts. This engine handles complex polygon geometries at scale using spatial indexing, enabling real-time evaluation against hundreds of thousands of zones on a mobile device.

**Patent Claim Angle:** A mobile geofence system using R-tree spatial indexing for real-time device position evaluation against large-scale polygon zone databases with configurable multi-type alert triggers.

---

## 6. Multi-Source Downloadable Geofence Databases

**Key Files:** `GeofenceDatabaseRepository.kt`, `cache-proxy/geofence-databases/build-military.js`, `build-excam.js`, `build-nces.js`, `build-dji-nofly.js`

**Description:** A system for building, distributing, and importing pre-compiled geofence databases from multiple authoritative sources. Four build scripts transform raw data from ArcGIS (military bases), government registries (schools/NCES), DJI no-fly zones, and exclusion camera databases into optimized SQLite format for on-device import. Users can download and install zone databases independently.

**Novelty:** Geofence databases are typically hardcoded or require server-side evaluation. This system enables offline geofence evaluation by transforming diverse authoritative sources into a standardized downloadable format with on-device spatial indexing.

**Patent Claim Angle:** A method for creating distributable geofence databases from heterogeneous authoritative geospatial sources, comprising source-specific ETL pipelines, standardized SQLite output format, and on-device spatial index construction.

---

## 7. Overpass Fair-Queue with Rate Limiting

**Key Files:** `server.js`

**Description:** A serialized request queue for upstream Overpass API calls that ensures fair access across multiple clients. The system enforces a 10-second minimum gap between upstream requests, uses round-robin scheduling with a 5-request cap per client, and provides a centralized queuing point that prevents API abuse while maximizing throughput for all connected devices.

**Novelty:** Most Overpass API clients manage rate limiting independently, leading to conflicts when multiple devices share an API key. This centralized fair-queue with per-client caps and round-robin scheduling provides coordinated access control.

**Patent Claim Angle:** A proxy-based request queuing system for rate-limited APIs comprising serialized upstream dispatch, per-client request caps, round-robin fair scheduling, and configurable minimum inter-request delays.

---

## 8. Smart Fuzzy Search with Keyword Hints

**Key Files:** `server.js`

**Description:** A multi-strategy search system combining PostgreSQL pg_trgm trigram similarity matching (threshold >0.2) with ILIKE substring search and ~80 keyword-to-category mappings. The system uses composite scoring to rank results, provides category hints in search responses (e.g., "Fuel & Charging" for "gas station"), and implements progressive distance expansion (50km -> 100km -> 160km) until a threshold of 50 results is reached, with a 200-result cap.

**Novelty:** The combination of trigram fuzzy matching, keyword-to-category semantic mapping, composite scoring, progressive distance expansion, and result-count-driven search termination creates a search experience that handles both exact and fuzzy queries with intelligent scope management.

**Patent Claim Angle:** A geospatial search system combining trigram similarity matching, keyword-to-category semantic mappings, composite relevance scoring, and progressive distance expansion driven by result count thresholds.

---

## 9. Filter and Map Mode

**Key Files:** `MainActivity.kt`

**Description:** An exclusive map visualization mode triggered from Find dialog results. When activated, the system clears all non-POI layers (transit, aircraft, webcams, METAR, geofences, radar), stops background data refresh jobs, and displays only the filtered POI subset with force-labeled markers at any zoom level. The view centers on the result centroid at zoom 15. A status line indicator enables tap-to-exit, and the mode auto-exits when the Find dialog reopens. Radar state is saved and restored on exit.

**Novelty:** Map applications typically overlay search results on existing map content. This mode creates a clean, dedicated view of search results by temporarily suspending all other map layers and background operations, providing focused visualization with full state restoration on exit.

**Patent Claim Angle:** A method for exclusive map visualization of filtered results comprising layer suspension, background job halting, forced marker labeling, centroid-centered display, and complete state restoration on mode exit.

---

## 10. Aircraft Auto-Follow with Rotation

**Key Files:** `MainActivity.kt`

**Description:** An automatic aircraft tracking system that follows individual aircraft using globally-tracked ICAO24 identifiers. The system maintains flight path trails, implements 3-strike tolerance for temporary signal loss (aircraft disappearing from ADS-B coverage), and supports 20-minute auto-rotation to cycle through tracked aircraft.

**Novelty:** Aircraft tracking apps typically require manual selection and lose track on signal gaps. This system provides autonomous follow with signal loss tolerance and automatic rotation through multiple tracked aircraft.

**Patent Claim Angle:** A method for autonomous aircraft tracking comprising persistent identifier tracking across data refresh cycles, configurable signal loss tolerance, flight path trail accumulation, and timed rotation across multiple tracked targets.

---

## 11. Priority-Based Status Line System

**Key Files:** `StatusLineManager.kt`

**Description:** A UI status communication system with 8 priority levels that manages competing status messages from multiple subsystems (GPS, weather, scanning, errors, social, filter mode). Higher-priority messages preempt lower-priority ones, with automatic message expiry and restoration of the next-highest-priority pending message. The status line also supports tap-to-action callbacks for interactive status items.

**Novelty:** Mobile apps typically use simple status indicators or toast messages. This priority-queue-based system provides structured, preemptive status management across multiple concurrent subsystems with interactive callbacks.

**Patent Claim Angle:** A priority-based status line management system for mobile applications comprising multi-level message preemption, automatic expiry with next-priority restoration, and tap-to-action callback registration.

---

## 12. Silent Fill

**Key Files:** `MainActivity.kt`

**Description:** A lightweight, non-intrusive POI population method that executes a single Overpass search on specific triggers: app startup, state restoration, and long-press events. The fill operates with a 3-4 second delay to avoid interfering with user interactions, populating nearby POIs without the overhead of a full scanner cycle.

**Novelty:** Unlike full scanning algorithms, Silent Fill provides just-enough POI coverage for immediate use with minimal latency and zero user interaction, complementing the heavier scanner algorithms for ongoing discovery.

**Patent Claim Angle:** A method for lightweight geospatial data population triggered by application lifecycle events, comprising delayed single-query execution timed to avoid user interaction interference.

---

## 13. Device-Bonded Authentication

**Key Files:** `AuthRepository.kt`, `server.js`

**Description:** An authentication system that bonds user accounts to device identity without traditional login/logout flows. Registration generates a device-specific credential using Argon2id password hashing, with JWT access tokens and 365-day refresh tokens for persistent sessions. The system eliminates password management overhead while maintaining cryptographic security through device binding.

**Novelty:** Most mobile auth systems use email/password or OAuth flows. Device bonding eliminates the login/logout UX entirely while maintaining security through hardware-bound credentials and industry-standard token management.

**Patent Claim Angle:** A device-bonded authentication method comprising hardware identity-based registration without explicit login flows, Argon2id credential hashing, and long-lived refresh token management for persistent mobile sessions.

---

## 14. Bbox Snapping for Cache Optimization

**Key Files:** `server.js`

**Description:** A coordinate rounding system that snaps bounding box queries to fixed grid intervals (METAR/webcams at 0.01 degrees, aircraft at 0.1 degrees) to maximize cache hit rates. By rounding viewport coordinates to predictable values, multiple slightly-different viewport positions map to the same cache key, dramatically reducing upstream API calls.

**Novelty:** Geospatial API proxies typically cache exact bounding box queries, resulting in poor cache hit rates as users pan the map. Bbox snapping trades minimal precision for dramatically improved cache efficiency by quantizing the coordinate space.

**Patent Claim Angle:** A method for improving geospatial API cache efficiency by quantizing bounding box coordinates to configurable grid intervals, comprising per-data-source snap granularity and deterministic cache key generation from rounded coordinates.

---

## Legal Protection Roadmap

### Immediate Actions (This Week)

1. **Copyright headers** — Added to all 131+ source files (Kotlin, XML, JavaScript, Shell, SQL)
2. **Repository visibility** — Ensure GitHub repository is set to private (`gh repo edit --visibility private`)
3. **License posture** — No open-source license file; "All Rights Reserved" via copyright headers

### Week 1

4. **US Copyright Registration** — File with US Copyright Office
   - Form: TX (literary work — software source code)
   - Fee: $65 (single author, single work)
   - Submit: representative source code deposit (first 25 + last 25 pages)
   - Protection: statutory damages + attorney fees eligibility upon registration

### Month 1

5. **Provisional Patent Applications** — File with USPTO (micro-entity rate: $320 each)
   - **Priority 1:** Adaptive Radius Cap-Retry Algorithm (#1)
   - **Priority 2:** Probe-Calibrate-Spiral Scanner (#2)
   - **Priority 3:** Overpass Fair-Queue (#7)
   - **Priority 4:** JTS R-Tree Geofence Engine (#5)
   - Each provisional provides 12 months to file a full utility patent

### Month 2-3

6. **IP Attorney Consultation** — Engage patent attorney for:
   - Review provisional applications
   - Advise on utility patent conversion strategy
   - Evaluate remaining 10 innovations for patentability
   - Trade secret vs. patent strategy for server-side algorithms

### Ongoing

7. **Trade Secret Protection** — Server-side algorithms (proxy code, queue logic, fuzzy search, bbox snapping) may be better protected as trade secrets than patents since they never ship to users
   - Document confidential parameters separately from public code
   - Restrict proxy source access to authorized personnel only
   - Maintain access logs for all code repositories

8. **NDA Requirement** — Require signed NDA before sharing any source code with collaborators, contractors, or potential investors

9. **Trademark** — Consider USPTO trademark registration for "LocationMapApp"
   - Filing fee: $250-350 (TEAS Plus / TEAS Standard)
   - Class: IC 009 (mobile application software)

### Timeline Summary

| When | Action | Cost |
|------|--------|------|
| Now | Copyright headers + private repo | $0 |
| Week 1 | US Copyright registration | $65 |
| Month 1 | 4 provisional patents | $1,280 |
| Month 2-3 | IP attorney engagement | $2,000-5,000 |
| Month 6 | Trademark filing | $250-350 |
| Month 12 | Utility patent conversions | $5,000-15,000/ea |
