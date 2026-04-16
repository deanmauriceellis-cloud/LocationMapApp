/*
 * LocationMapApp v1.5 — Phase 9P.6 (Session 101)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * ════════════════════════════════════════════════════════════════════════
 * MIRROR OF: app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/menu/PoiCategories.kt
 * ════════════════════════════════════════════════════════════════════════
 *
 * Hand-port of the 20-category POI taxonomy used by the Salem Android module.
 * Used by the Salem POI Admin Tool (Phase 9P.B) for the left-side tree pane,
 * category-aware edit dialog, and per-mode visibility schema (9P.4a).
 *
 * Why a separate file from web/src/config/categories.ts:
 *   - categories.ts is the 17-category taxonomy used by the GENERIC public
 *     web app for OSM POI classification. Shape uses `tagMatches: { key, values }[]`.
 *   - poiCategories.ts (this file) mirrors the Salem Android module's exact
 *     taxonomy: 17 standard + 3 Salem-specific categories, with `tags: string[]`
 *     ("amenity=restaurant" form), plus per-mode visibility flags.
 *   - The two files will probably be unified in a future refactor (TODO Phase 9C
 *     or later), but until then this file is the source of truth for the admin
 *     tool's category model.
 *
 * Cross-reference: any change to PoiCategories.kt MUST be mirrored here, and
 * vice-versa. The two files are intentionally line-aligned where possible.
 *
 * Per-mode visibility (Phase 9P.4a):
 *   - `defaultEnabled` — visibility default in FREE-ROAM mode (the default app)
 *   - `historicTourDefault` — visibility default in HISTORIC TOUR mode (Phase 9R)
 *
 * Two-prefKey naming convention:
 *   - free-roam pref key  = `<prefKey>` (existing)            — `freeRoamPrefKey()`
 *   - tour-mode pref key  = `<prefKey>_tour`                  — `tourPrefKey()`
 */

export interface PoiSubtype {
  label: string;
  tags: string[]; // ["amenity=restaurant", "shop=bakery", ...]
}

export interface PoiCategory {
  id: string;
  label: string;
  prefKey: string;
  tags: string[]; // flat list of "key=value" strings
  subtypes: PoiSubtype[] | null;
  color: string; // hex e.g. "#BF360C"
  defaultEnabled: boolean;
  historicTourDefault: boolean;
}

/** Phase 9P.4a: alias for clarity — same as `prefKey`. Names the free-roam SharedPreferences key. */
export function freeRoamPrefKey(cat: PoiCategory): string {
  return cat.prefKey;
}

/** Phase 9P.4a: derived tour-mode SharedPreferences key — appends `_tour` suffix. */
export function tourPrefKey(cat: PoiCategory): string {
  return `${cat.prefKey}_tour`;
}

// ─── Layer IDs ───────────────────────────────────────────────────────────────
// Mirror of PoiLayerId in app-salem (and core for the standard ones).
export const PoiLayerId = {
  // 17 standard
  FOOD_DRINK: 'FOOD_DRINK',
  FUEL_CHARGING: 'FUEL_CHARGING',
  TRANSIT: 'TRANSIT',
  CIVIC: 'CIVIC',
  PARKS_REC: 'PARKS_REC',
  SHOPPING: 'SHOPPING',
  HEALTHCARE: 'HEALTHCARE',
  EDUCATION: 'EDUCATION',
  LODGING: 'LODGING',
  PARKING: 'PARKING',
  FINANCE: 'FINANCE',
  WORSHIP: 'WORSHIP',
  HISTORICAL_BUILDINGS: 'HISTORICAL_BUILDINGS',
  EMERGENCY: 'EMERGENCY',
  AUTO_SERVICES: 'AUTO_SERVICES',
  ENTERTAINMENT: 'ENTERTAINMENT',
  OFFICES: 'OFFICES',
  // 3 Salem-specific (S134: was 5; HAUNTED_ATTRACTION→ENTERTAINMENT, HISTORIC_HOUSE→HISTORICAL_BUILDINGS, GHOST_TOUR→TOUR_COMPANIES)
  WITCH_SHOP: 'WITCH_SHOP',
  PSYCHIC: 'PSYCHIC',
  TOUR_COMPANIES: 'TOUR_COMPANIES',
} as const;

// ─── 20 categories ───────────────────────────────────────────────────────────

export const POI_CATEGORIES: PoiCategory[] = [
  // 1 — Food & Drink
  {
    id: PoiLayerId.FOOD_DRINK,
    label: 'Food & Drink',
    prefKey: 'poi_food_drink_on',
    tags: [
      'amenity=restaurant', 'amenity=fast_food', 'amenity=cafe',
      'amenity=bar', 'amenity=pub', 'amenity=ice_cream',
      'shop=bakery', 'shop=alcohol', 'shop=deli',
      'shop=pastry', 'shop=confectionery', 'amenity=marketplace',
      'craft=brewery', 'craft=winery', 'craft=distillery',
      'shop=wine', 'shop=butcher', 'shop=seafood',
    ],
    subtypes: [
      { label: 'Restaurants',     tags: ['amenity=restaurant'] },
      { label: 'Fast Food',       tags: ['amenity=fast_food'] },
      { label: 'Cafes',           tags: ['amenity=cafe'] },
      { label: 'Bars',            tags: ['amenity=bar'] },
      { label: 'Pubs',            tags: ['amenity=pub'] },
      { label: 'Ice Cream',       tags: ['amenity=ice_cream'] },
      { label: 'Bakeries',        tags: ['shop=bakery'] },
      { label: 'Pastry Shops',    tags: ['shop=pastry'] },
      { label: 'Candy Stores',    tags: ['shop=confectionery'] },
      { label: 'Liquor Stores',   tags: ['shop=alcohol'] },
      { label: 'Wine Shops',      tags: ['shop=wine'] },
      { label: 'Delis',           tags: ['shop=deli'] },
      { label: 'Butcher Shops',   tags: ['shop=butcher'] },
      { label: 'Seafood Markets', tags: ['shop=seafood'] },
      { label: 'Marketplaces',    tags: ['amenity=marketplace'] },
      { label: 'Breweries',       tags: ['craft=brewery'] },
      { label: 'Wineries',        tags: ['craft=winery'] },
      { label: 'Distilleries',    tags: ['craft=distillery'] },
    ],
    color: '#BF360C',
    defaultEnabled: false,
    historicTourDefault: false,
  },

  // 2 — Fuel & Charging
  {
    id: PoiLayerId.FUEL_CHARGING,
    label: 'Fuel & Charging',
    prefKey: 'poi_fuel_charging_on',
    tags: ['amenity=fuel', 'amenity=charging_station'],
    subtypes: [
      { label: 'Gas Stations',      tags: ['amenity=fuel'] },
      { label: 'Charging Stations', tags: ['amenity=charging_station'] },
    ],
    color: '#E65100',
    defaultEnabled: false,
    historicTourDefault: false,
  },

  // 3 — Transit
  {
    id: PoiLayerId.TRANSIT,
    label: 'Transit',
    prefKey: 'poi_transit_on',
    tags: [
      'public_transport=station', 'railway=station', 'amenity=bus_station',
      'amenity=bicycle_rental', 'amenity=ferry_terminal',
      'aeroway=aerodrome', 'amenity=taxi',
    ],
    subtypes: [
      { label: 'Train Stations',   tags: ['public_transport=station', 'railway=station'] },
      { label: 'Bus Stations',     tags: ['amenity=bus_station'] },
      { label: 'Airports',         tags: ['aeroway=aerodrome'] },
      { label: 'Bike Rentals',     tags: ['amenity=bicycle_rental'] },
      { label: 'Ferry Terminals',  tags: ['amenity=ferry_terminal'] },
      { label: 'Taxi Stands',      tags: ['amenity=taxi'] },
    ],
    color: '#0277BD',
    defaultEnabled: false,
    historicTourDefault: false,
  },

  // 4 — Civic & Gov (free-roam ON, historic-tour ON: 1692 courthouses, town halls central to witch trials)
  {
    id: PoiLayerId.CIVIC,
    label: 'Civic & Gov',
    prefKey: 'poi_civic_on',
    tags: [
      'amenity=townhall', 'amenity=courthouse', 'amenity=post_office', 'office=government',
      'amenity=community_centre', 'amenity=social_facility', 'amenity=post_box',
      'amenity=recycling', 'office=diplomatic',
    ],
    subtypes: [
      { label: 'Town Halls',        tags: ['amenity=townhall'] },
      { label: 'Courthouses',       tags: ['amenity=courthouse'] },
      { label: 'Post Offices',      tags: ['amenity=post_office'] },
      { label: 'Post Boxes',        tags: ['amenity=post_box'] },
      { label: 'Gov Offices',       tags: ['office=government'] },
      { label: 'Community Centres', tags: ['amenity=community_centre'] },
      { label: 'Social Services',   tags: ['amenity=social_facility'] },
      { label: 'Recycling',         tags: ['amenity=recycling'] },
      { label: 'Embassies',         tags: ['office=diplomatic'] },
    ],
    color: '#1A237E',
    defaultEnabled: true,
    historicTourDefault: true,
  },

  // 5 — Parks & Rec
  {
    id: PoiLayerId.PARKS_REC,
    label: 'Parks & Rec',
    prefKey: 'poi_parks_rec_on',
    tags: [
      'leisure=park', 'leisure=nature_reserve', 'leisure=playground',
      'leisure=pitch', 'leisure=swimming_pool',
      'leisure=garden', 'tourism=picnic_site',
      'amenity=drinking_water', 'amenity=toilets',
      'amenity=shelter', 'amenity=fountain', 'leisure=dog_park',
      'leisure=track', 'leisure=recreation_ground',
      'leisure=beach_resort', 'leisure=slipway', 'leisure=skatepark',
    ],
    subtypes: [
      { label: 'Parks',           tags: ['leisure=park'] },
      { label: 'Nature Reserves', tags: ['leisure=nature_reserve'] },
      { label: 'Playgrounds',     tags: ['leisure=playground'] },
      { label: 'Sports Fields',   tags: ['leisure=pitch'] },
      { label: 'Tracks',          tags: ['leisure=track'] },
      { label: 'Rec Grounds',     tags: ['leisure=recreation_ground'] },
      { label: 'Pools',           tags: ['leisure=swimming_pool'] },
      { label: 'Dog Parks',       tags: ['leisure=dog_park'] },
      { label: 'Gardens',         tags: ['leisure=garden'] },
      { label: 'Boat Ramps',      tags: ['leisure=slipway'] },
      { label: 'Skateparks',      tags: ['leisure=skatepark'] },
      { label: 'Picnic Sites',    tags: ['tourism=picnic_site'] },
      { label: 'Shelters',        tags: ['amenity=shelter'] },
      { label: 'Fountains',       tags: ['amenity=fountain'] },
      { label: 'Drinking Water',  tags: ['amenity=drinking_water'] },
      { label: 'Restrooms',       tags: ['amenity=toilets'] },
      { label: 'Beaches',         tags: ['leisure=beach_resort'] },
    ],
    color: '#2E7D32',
    defaultEnabled: false,
    historicTourDefault: false,
  },

  // 6 — Shopping
  {
    id: PoiLayerId.SHOPPING,
    label: 'Shopping',
    prefKey: 'poi_shopping_on',
    tags: [
      'shop=supermarket', 'shop=convenience', 'shop=mall',
      'shop=department_store', 'shop=clothes',
      'shop=hairdresser', 'shop=beauty', 'shop=massage',
      'shop=gift', 'shop=laundry', 'shop=variety_store',
      'shop=mobile_phone', 'shop=dry_cleaning', 'shop=books',
      'shop=furniture', 'shop=jewelry', 'shop=optician',
      'shop=florist', 'shop=chemist', 'shop=storage_rental',
      'shop=shoes', 'shop=tobacco', 'shop=hardware',
      'shop=pet', 'shop=electronics', 'shop=bicycle', 'shop=garden_centre',
      'shop=tattoo', 'shop=barber', 'shop=second_hand',
      'shop=e-cigarette', 'shop=cannabis',
    ],
    subtypes: [
      { label: 'Supermarkets',       tags: ['shop=supermarket'] },
      { label: 'Convenience Stores', tags: ['shop=convenience'] },
      { label: 'Malls',              tags: ['shop=mall'] },
      { label: 'Department Stores',  tags: ['shop=department_store'] },
      { label: 'Clothing',           tags: ['shop=clothes'] },
      { label: 'Shoe Stores',        tags: ['shop=shoes'] },
      { label: 'Jewelry',            tags: ['shop=jewelry'] },
      { label: 'Hair Salons',        tags: ['shop=hairdresser'] },
      { label: 'Barber Shops',       tags: ['shop=barber'] },
      { label: 'Beauty & Spa',       tags: ['shop=beauty'] },
      { label: 'Massage',            tags: ['shop=massage'] },
      { label: 'Tattoo Shops',       tags: ['shop=tattoo'] },
      { label: 'Bookstores',         tags: ['shop=books'] },
      { label: 'Gift Shops',         tags: ['shop=gift'] },
      { label: 'Florists',           tags: ['shop=florist'] },
      { label: 'Furniture',          tags: ['shop=furniture'] },
      { label: 'Hardware Stores',    tags: ['shop=hardware'] },
      { label: 'Phone Stores',       tags: ['shop=mobile_phone'] },
      { label: 'Opticians',          tags: ['shop=optician'] },
      { label: 'Drug Stores',        tags: ['shop=chemist'] },
      { label: 'Laundromats',        tags: ['shop=laundry'] },
      { label: 'Dry Cleaners',       tags: ['shop=dry_cleaning'] },
      { label: 'Variety Stores',     tags: ['shop=variety_store'] },
      { label: 'Tobacco Shops',      tags: ['shop=tobacco'] },
      { label: 'Vape Shops',         tags: ['shop=e-cigarette'] },
      { label: 'Cannabis',           tags: ['shop=cannabis'] },
      { label: 'Thrift Stores',      tags: ['shop=second_hand'] },
      { label: 'Storage Rentals',    tags: ['shop=storage_rental'] },
      { label: 'Pet Stores',         tags: ['shop=pet'] },
      { label: 'Electronics',        tags: ['shop=electronics'] },
      { label: 'Bicycle Shops',      tags: ['shop=bicycle'] },
      { label: 'Garden Centers',     tags: ['shop=garden_centre'] },
    ],
    color: '#F57F17',
    defaultEnabled: false,
    historicTourDefault: false,
  },

  // 7 — Healthcare
  {
    id: PoiLayerId.HEALTHCARE,
    label: 'Healthcare',
    prefKey: 'poi_healthcare_on',
    tags: [
      'amenity=hospital', 'amenity=pharmacy', 'amenity=clinic',
      'amenity=dentist', 'amenity=doctors', 'amenity=veterinary',
      'amenity=nursing_home',
    ],
    subtypes: [
      { label: 'Hospitals',     tags: ['amenity=hospital'] },
      { label: 'Pharmacies',    tags: ['amenity=pharmacy'] },
      { label: 'Clinics',       tags: ['amenity=clinic'] },
      { label: 'Dentists',      tags: ['amenity=dentist'] },
      { label: 'Doctors',       tags: ['amenity=doctors'] },
      { label: 'Veterinary',    tags: ['amenity=veterinary'] },
      { label: 'Nursing Homes', tags: ['amenity=nursing_home'] },
    ],
    color: '#D32F2F',
    defaultEnabled: false,
    historicTourDefault: false,
  },

  // 8 — Education
  {
    id: PoiLayerId.EDUCATION,
    label: 'Education',
    prefKey: 'poi_education_on',
    tags: [
      'amenity=school', 'amenity=library', 'amenity=college', 'amenity=university',
      'amenity=childcare', 'amenity=kindergarten',
    ],
    subtypes: [
      { label: 'Schools',       tags: ['amenity=school'] },
      { label: 'Libraries',     tags: ['amenity=library'] },
      { label: 'Colleges',      tags: ['amenity=college'] },
      { label: 'Universities',  tags: ['amenity=university'] },
      { label: 'Childcare',     tags: ['amenity=childcare'] },
      { label: 'Kindergartens', tags: ['amenity=kindergarten'] },
    ],
    color: '#5D4037',
    defaultEnabled: false,
    historicTourDefault: false,
  },

  // 9 — Lodging
  {
    id: PoiLayerId.LODGING,
    label: 'Lodging',
    prefKey: 'poi_lodging_on',
    tags: [
      'tourism=hotel', 'tourism=motel', 'tourism=hostel',
      'tourism=camp_site', 'tourism=guest_house', 'tourism=caravan_site',
    ],
    subtypes: [
      { label: 'Hotels',       tags: ['tourism=hotel'] },
      { label: 'Motels',       tags: ['tourism=motel'] },
      { label: 'Hostels',      tags: ['tourism=hostel'] },
      { label: 'Campgrounds',  tags: ['tourism=camp_site'] },
      { label: 'Guest Houses', tags: ['tourism=guest_house'] },
      { label: 'RV Parks',     tags: ['tourism=caravan_site'] },
    ],
    color: '#7B1FA2',
    defaultEnabled: false,
    historicTourDefault: false,
  },

  // 10 — Parking
  {
    id: PoiLayerId.PARKING,
    label: 'Parking',
    prefKey: 'poi_parking_on',
    tags: ['amenity=parking'],
    subtypes: null,
    color: '#455A64',
    defaultEnabled: false,
    historicTourDefault: false,
  },

  // 11 — Finance
  {
    id: PoiLayerId.FINANCE,
    label: 'Finance',
    prefKey: 'poi_finance_on',
    tags: ['amenity=bank', 'amenity=atm'],
    subtypes: [
      { label: 'Banks', tags: ['amenity=bank'] },
      { label: 'ATMs',  tags: ['amenity=atm'] },
    ],
    color: '#00695C',
    defaultEnabled: false,
    historicTourDefault: false,
  },

  // 12 — Places of Worship
  // OFF in free-roam (clutter), ON in historic tour (1692 churches central to narrative —
  // Salem Village Church, First Church of Salem, etc.)
  {
    id: PoiLayerId.WORSHIP,
    label: 'Places of Worship',
    prefKey: 'poi_worship_on',
    tags: ['amenity=place_of_worship'],
    subtypes: null,
    color: '#4E342E',
    defaultEnabled: false,
    historicTourDefault: true,
  },

  // 13 — Historic Sites (default ON — primary layer for Salem tour app)
  // S134: replaced TOURISM_HISTORY. Genuine historic buildings, home sites, cemeteries,
  // memorials, forts, wharves, districts. Modern attractions/tours → ENTERTAINMENT/TOUR_COMPANIES.
  {
    id: PoiLayerId.HISTORICAL_BUILDINGS,
    label: 'Historic Sites',
    prefKey: 'poi_historic_sites_on',
    tags: [
      'historic=building', 'historic=house', 'historic=memorial',
      'historic=monument', 'historic=cemetery', 'historic=maritime',
      'historic=ruins',
    ],
    subtypes: [
      { label: 'Historic Buildings', tags: ['historic=building'] },
      { label: 'Cemeteries',         tags: ['historic=cemetery'] },
      { label: 'Memorials',          tags: ['historic=memorial'] },
      { label: 'Monuments',          tags: ['historic=monument'] },
      { label: 'Maritime',           tags: ['historic=maritime'] },
      { label: 'Historic Districts', tags: ['historic=district'] },
      { label: 'Home Sites',         tags: ['historic=site'] },
      { label: 'Ruins',              tags: ['historic=ruins'] },
    ],
    color: '#8D6E63',
    defaultEnabled: true,
    historicTourDefault: true,
  },

  // 14 — Emergency Svc
  {
    id: PoiLayerId.EMERGENCY,
    label: 'Emergency Svc',
    prefKey: 'poi_emergency_on',
    tags: ['amenity=police', 'amenity=fire_station'],
    subtypes: [
      { label: 'Police',        tags: ['amenity=police'] },
      { label: 'Fire Stations', tags: ['amenity=fire_station'] },
    ],
    color: '#B71C1C',
    defaultEnabled: false,
    historicTourDefault: false,
  },

  // 15 — Auto Services
  {
    id: PoiLayerId.AUTO_SERVICES,
    label: 'Auto Services',
    prefKey: 'poi_auto_services_on',
    tags: [
      'shop=car_repair', 'amenity=car_wash', 'amenity=car_rental', 'shop=tyres',
      'shop=car', 'shop=car_parts',
    ],
    subtypes: [
      { label: 'Repair Shops', tags: ['shop=car_repair'] },
      { label: 'Car Washes',   tags: ['amenity=car_wash'] },
      { label: 'Rentals',      tags: ['amenity=car_rental'] },
      { label: 'Tire Shops',   tags: ['shop=tyres'] },
      { label: 'Dealerships',  tags: ['shop=car'] },
      { label: 'Parts Stores', tags: ['shop=car_parts'] },
    ],
    color: '#37474F',
    defaultEnabled: false,
    historicTourDefault: false,
  },

  // 16 — Entertainment (default ON — haunted houses, theatres, event venues)
  {
    id: PoiLayerId.ENTERTAINMENT,
    label: 'Entertainment',
    prefKey: 'poi_entertainment_on',
    tags: [
      'leisure=fitness_centre', 'leisure=sports_centre', 'leisure=golf_course',
      'leisure=marina', 'leisure=stadium', 'leisure=disc_golf_course',
      'amenity=theatre', 'amenity=cinema', 'amenity=nightclub',
      'amenity=events_venue', 'amenity=arts_centre',
      'amenity=studio', 'leisure=dance', 'leisure=amusement_arcade',
      'leisure=ice_rink', 'leisure=bowling_alley',
      'leisure=water_park', 'leisure=miniature_golf', 'leisure=escape_game',
    ],
    subtypes: [
      { label: 'Fitness',        tags: ['leisure=fitness_centre'] },
      { label: 'Sports Centres', tags: ['leisure=sports_centre'] },
      { label: 'Golf Courses',   tags: ['leisure=golf_course'] },
      { label: 'Disc Golf',      tags: ['leisure=disc_golf_course'] },
      { label: 'Marinas',        tags: ['leisure=marina'] },
      { label: 'Stadiums',       tags: ['leisure=stadium'] },
      { label: 'Theatres',       tags: ['amenity=theatre'] },
      { label: 'Cinemas',        tags: ['amenity=cinema'] },
      { label: 'Nightclubs',     tags: ['amenity=nightclub'] },
      { label: 'Event Venues',   tags: ['amenity=events_venue'] },
      { label: 'Arts Centres',   tags: ['amenity=arts_centre'] },
      { label: 'Studios',        tags: ['amenity=studio'] },
      { label: 'Dance Studios',  tags: ['leisure=dance'] },
      { label: 'Arcades',        tags: ['leisure=amusement_arcade'] },
      { label: 'Ice Rinks',      tags: ['leisure=ice_rink'] },
      { label: 'Bowling',        tags: ['leisure=bowling_alley'] },
      { label: 'Water Parks',    tags: ['leisure=water_park'] },
      { label: 'Mini Golf',      tags: ['leisure=miniature_golf'] },
      { label: 'Escape Rooms',   tags: ['leisure=escape_game'] },
    ],
    color: '#00838F',
    defaultEnabled: true,
    historicTourDefault: false,
  },

  // 17 — Offices & Services
  {
    id: PoiLayerId.OFFICES,
    label: 'Offices & Services',
    prefKey: 'poi_offices_on',
    tags: [
      'office=company', 'office=estate_agent', 'office=lawyer',
      'office=insurance', 'office=tax_advisor',
    ],
    subtypes: [
      { label: 'Companies',    tags: ['office=company'] },
      { label: 'Real Estate',  tags: ['office=estate_agent'] },
      { label: 'Law Offices',  tags: ['office=lawyer'] },
      { label: 'Insurance',    tags: ['office=insurance'] },
      { label: 'Tax Advisors', tags: ['office=tax_advisor'] },
    ],
    color: '#546E7A',
    defaultEnabled: false,
    historicTourDefault: false,
  },

  // ═══════════════════════════════════════════════════════════════
  // Salem-Specific Categories (18-20)
  // S134: was 18-22. HAUNTED_ATTRACTION→ENTERTAINMENT, HISTORIC_HOUSE→HISTORICAL_BUILDINGS,
  // GHOST_TOUR→TOUR_COMPANIES. TOURISM_HISTORY split into HISTORICAL_BUILDINGS+others.
  // ═══════════════════════════════════════════════════════════════

  // 18 — Witch & Occult Shops (modern Salem witch shops fit the historic atmosphere)
  {
    id: PoiLayerId.WITCH_SHOP,
    label: 'Witch & Occult Shops',
    prefKey: 'poi_witch_shop_on',
    tags: ['shop=esoteric', 'shop=occult', 'shop=metaphysical'],
    subtypes: [
      { label: 'Witchcraft Shops', tags: ['shop=esoteric'] },
      { label: 'Occult Supplies',  tags: ['shop=occult'] },
      { label: 'Metaphysical',     tags: ['shop=metaphysical'] },
      { label: 'Crystal Shops',    tags: ['shop=crystal'] },
      { label: 'Herb Shops',       tags: ['shop=herbs'] },
    ],
    color: '#6A1B9A',
    defaultEnabled: true,
    historicTourDefault: true,
  },

  // 19 — Psychic & Tarot (modern flavor distracts from historic narrative)
  {
    id: PoiLayerId.PSYCHIC,
    label: 'Psychic & Tarot',
    prefKey: 'poi_psychic_on',
    tags: ['shop=psychic', 'amenity=psychic'],
    subtypes: [
      { label: 'Tarot Readings',    tags: ['shop=tarot'] },
      { label: 'Psychic Readings',  tags: ['shop=psychic'] },
      { label: 'Palm Readings',     tags: ['shop=palmistry'] },
      { label: 'Séances',           tags: ['amenity=seance'] },
      { label: 'Spiritual Healers', tags: ['amenity=spiritual'] },
    ],
    color: '#AB47BC',
    defaultEnabled: true,
    historicTourDefault: false,
  },

  // 20 — Tour Companies
  // S134: absorbed GHOST_TOUR + walking tour companies from old TOURISM_HISTORY
  {
    id: PoiLayerId.TOUR_COMPANIES,
    label: 'Tour Companies',
    prefKey: 'poi_tour_companies_on',
    tags: ['tourism=walking_tour', 'tourism=ghost_tour', 'tourism=ghost_walk', 'tourism=historical_tour'],
    subtypes: [
      { label: 'Walking Tours', tags: ['tourism=walking_tour'] },
      { label: 'Ghost Tours',   tags: ['tourism=ghost_tour'] },
      { label: 'Food Tours',    tags: ['tourism=food_tour'] },
      { label: 'Trolley Tours', tags: ['tourism=trolley_tour'] },
    ],
    color: '#FF6F00',
    defaultEnabled: true,
    historicTourDefault: true,
  },
];

// ─── Lookup helpers ──────────────────────────────────────────────────────────

const _categoryById = new Map(POI_CATEGORIES.map((c) => [c.id, c]));

/** Look up a category by its layer ID, or undefined. */
export function findPoiCategory(id: string): PoiCategory | undefined {
  return _categoryById.get(id);
}

/**
 * Build a set of OSM tag values (e.g. "restaurant", "museum") for the categories
 * enabled in the given mode. Mirror of `enabledTagValues(prefs)` in PoiCategories.kt
 * but driven by an explicit visibility map rather than SharedPreferences.
 */
export function enabledTagValues(
  visibility: Record<string, boolean>,
  mode: 'freeroam' | 'tour' = 'freeroam',
): Set<string> {
  const out = new Set<string>();
  for (const cat of POI_CATEGORIES) {
    const key = mode === 'tour' ? tourPrefKey(cat) : freeRoamPrefKey(cat);
    const def = mode === 'tour' ? cat.historicTourDefault : cat.defaultEnabled;
    const enabled = key in visibility ? visibility[key] : def;
    if (enabled) {
      for (const tag of cat.tags) {
        const eq = tag.indexOf('=');
        if (eq !== -1) out.add(tag.slice(eq + 1));
      }
    }
  }
  return out;
}
