# Archived Content — Master Plan Phase 10 & Future Phases (Pre-Session 76)

> Archived 2026-04-04, Session 76. These sections were replaced with expanded, research-backed phases.

## Phase 10 — Polish, Branding & Play Store (ORIGINAL)

**Goal:** Final polish, app icon, store listing, tiered pricing release (see Business Model section).

### Step 10.1: App icon & branding
- [ ] Design app icon (Salem themed — consider: witch silhouette, historic building outline, crescent moon, vintage map element)
- [ ] Splash screen with Salem imagery
- [ ] Consistent typography and color scheme throughout
- [ ] About screen with credits, historical source citations

### Step 10.2: Offline mode
- [ ] Pre-cache osmdroid map tiles for Salem area (zoom 12-18)
  - ~42.50 to 42.54 lat, -70.91 to -70.87 lng
- [ ] All content in local Room DB (already offline)
- [ ] Walking directions: cache recent routes, fallback to straight-line distance
- [ ] Indicate online/offline status in status bar

### Step 10.3: Performance optimization
- [ ] Lazy-load narration scripts (don't load all at startup)
- [ ] Marker clustering for dense POI areas (Essex Street)
- [ ] Background GPS: use foreground service with notification
- [ ] Battery optimization: reduce GPS frequency when user is stationary

### Step 10.4: Accessibility
- [ ] Content descriptions on all map markers
- [ ] TalkBack compatibility
- [ ] High-contrast mode
- [ ] Wheelchair accessibility flags on POIs
- [ ] Large text support

### Step 10.5: Google Play Store
- [ ] Set up Google Play Developer account (if not already)
- [ ] Configure tiered pricing via Google Play billing (Free + IAP / subscriptions)
- [ ] Store listing
- [ ] Privacy policy (required for paid apps)
- [ ] Generate signed APK/AAB

### Step 10.6: Verify (full regression)
- [ ] Complete tour walkthrough (simulated GPS): Witch Trial Trail start-to-finish
- [ ] All narration triggers correctly
- [ ] Walking directions display and update
- [ ] Business search finds restaurants, bars, shops
- [ ] Events calendar shows current events
- [ ] MBTA transit works (Salem Station trains)
- [ ] Weather displays for Salem
- [ ] Offline mode works (airplane mode after initial load)
- [ ] App installs from signed APK
- [ ] Git commit: "v1.0.0 — WickedSalemWitchCityTour release candidate"

---

## Future Phases (Post-Launch) — ORIGINAL

### Phase 11 — Merchant Network & Advertising Platform
- [ ] Build merchant admin portal (web-based)
- [ ] Merchant self-service POI creation/editing
- [ ] Geofenced ad delivery system (proximity-triggered cards)
- [ ] Loyalty program engine (check-ins, discount code generation)
- [ ] Analytics dashboard (impressions, foot traffic, redemptions)
- [ ] North Shore merchant data expansion (500+ businesses)
- [ ] Sponsored tour stop system
- [ ] Business-to-app payment integration (Stripe)

### Phase 12 — Salem Village LLM Integration
- [ ] `/salem/chat` API endpoint — conversational interface to Salem LLM
- [ ] Character selection (50 historical figures available)
- [ ] Context-aware conversations (figure knows their history, location, relationships)
- [ ] Token metering for $49.99/mo subscription
- [ ] Conversation persistence (chat history)
- [ ] Voice input/output integration with TTS system
- [ ] Safety guardrails (historical accuracy, no harmful content)
- [ ] Rate limiting and abuse prevention

### Phase 13 — Additional Revenue Features
- [ ] In-app merchandise (Salem-branded items, print-on-demand)
- [ ] Tour booking integration (partner ghost tours, museum tickets)
- [ ] Photo/selfie spots with AR historical overlays
- [ ] Social sharing ("I completed the Witch Trial Trail!")
- [ ] Seasonal content packs (October Haunted Happenings premium content)
