import type { Category } from '@/lib/types'

export const CATEGORIES: Category[] = [
  {
    id: 'FOOD_DRINK',
    label: 'Food & Drink',
    color: '#BF360C',
    tagMatches: [
      { key: 'amenity', values: ['restaurant', 'fast_food', 'cafe', 'bar', 'pub', 'ice_cream'] },
      { key: 'shop', values: ['bakery', 'alcohol', 'deli', 'pastry', 'confectionery', 'wine', 'butcher', 'seafood'] },
      { key: 'craft', values: ['brewery', 'winery', 'distillery'] },
    ],
    subtypes: [
      { label: 'Restaurants', tags: { amenity: 'restaurant' } },
      { label: 'Fast Food', tags: { amenity: 'fast_food' } },
      { label: 'Cafes', tags: { amenity: 'cafe' } },
      { label: 'Bars', tags: { amenity: 'bar' } },
      { label: 'Pubs', tags: { amenity: 'pub' } },
      { label: 'Ice Cream', tags: { amenity: 'ice_cream' } },
      { label: 'Bakeries', tags: { shop: 'bakery' } },
      { label: 'Pastry Shops', tags: { shop: 'pastry' } },
      { label: 'Candy Stores', tags: { shop: 'confectionery' } },
      { label: 'Liquor Stores', tags: { shop: 'alcohol' } },
      { label: 'Wine Shops', tags: { shop: 'wine' } },
      { label: 'Delis', tags: { shop: 'deli' } },
      { label: 'Butcher Shops', tags: { shop: 'butcher' } },
      { label: 'Seafood Markets', tags: { shop: 'seafood' } },
      { label: 'Breweries', tags: { craft: 'brewery' } },
      { label: 'Wineries', tags: { craft: 'winery' } },
      { label: 'Distilleries', tags: { craft: 'distillery' } },
    ],
  },
  {
    id: 'FUEL_CHARGING',
    label: 'Fuel & Charging',
    color: '#E65100',
    tagMatches: [
      { key: 'amenity', values: ['fuel', 'charging_station'] },
    ],
    subtypes: [
      { label: 'Gas Stations', tags: { amenity: 'fuel' } },
      { label: 'Charging Stations', tags: { amenity: 'charging_station' } },
    ],
  },
  {
    id: 'TRANSIT',
    label: 'Transit',
    color: '#0277BD',
    tagMatches: [
      { key: 'public_transport', values: ['station'] },
      { key: 'railway', values: ['station'] },
      { key: 'amenity', values: ['bus_station', 'bicycle_rental', 'ferry_terminal', 'taxi'] },
      { key: 'aeroway', values: ['aerodrome'] },
    ],
    subtypes: [
      { label: 'Train Stations', tags: { railway: 'station' } },
      { label: 'Bus Stations', tags: { amenity: 'bus_station' } },
      { label: 'Airports', tags: { aeroway: 'aerodrome' } },
      { label: 'Bike Rentals', tags: { amenity: 'bicycle_rental' } },
      { label: 'Ferry Terminals', tags: { amenity: 'ferry_terminal' } },
      { label: 'Taxi Stands', tags: { amenity: 'taxi' } },
    ],
  },
  {
    id: 'CIVIC',
    label: 'Civic & Gov',
    color: '#1A237E',
    tagMatches: [
      { key: 'amenity', values: ['townhall', 'courthouse', 'post_office', 'post_box', 'community_centre', 'social_facility', 'recycling'] },
      { key: 'office', values: ['government', 'diplomatic'] },
    ],
    subtypes: [
      { label: 'Town Halls', tags: { amenity: 'townhall' } },
      { label: 'Courthouses', tags: { amenity: 'courthouse' } },
      { label: 'Post Offices', tags: { amenity: 'post_office' } },
      { label: 'Post Boxes', tags: { amenity: 'post_box' } },
      { label: 'Gov Offices', tags: { office: 'government' } },
      { label: 'Community Centres', tags: { amenity: 'community_centre' } },
      { label: 'Social Services', tags: { amenity: 'social_facility' } },
      { label: 'Recycling', tags: { amenity: 'recycling' } },
      { label: 'Embassies', tags: { office: 'diplomatic' } },
    ],
  },
  {
    id: 'PARKS_REC',
    label: 'Parks & Rec',
    color: '#2E7D32',
    tagMatches: [
      { key: 'leisure', values: ['park', 'nature_reserve', 'playground', 'pitch', 'swimming_pool', 'garden', 'dog_park', 'track', 'recreation_ground', 'beach_resort', 'slipway', 'skatepark'] },
      { key: 'tourism', values: ['picnic_site'] },
      { key: 'amenity', values: ['drinking_water', 'toilets', 'shelter', 'fountain'] },
    ],
    subtypes: [
      { label: 'Parks', tags: { leisure: 'park' } },
      { label: 'Nature Reserves', tags: { leisure: 'nature_reserve' } },
      { label: 'Playgrounds', tags: { leisure: 'playground' } },
      { label: 'Sports Fields', tags: { leisure: 'pitch' } },
      { label: 'Tracks', tags: { leisure: 'track' } },
      { label: 'Rec Grounds', tags: { leisure: 'recreation_ground' } },
      { label: 'Pools', tags: { leisure: 'swimming_pool' } },
      { label: 'Dog Parks', tags: { leisure: 'dog_park' } },
      { label: 'Gardens', tags: { leisure: 'garden' } },
      { label: 'Boat Ramps', tags: { leisure: 'slipway' } },
      { label: 'Skateparks', tags: { leisure: 'skatepark' } },
      { label: 'Picnic Sites', tags: { tourism: 'picnic_site' } },
      { label: 'Shelters', tags: { amenity: 'shelter' } },
      { label: 'Fountains', tags: { amenity: 'fountain' } },
      { label: 'Drinking Water', tags: { amenity: 'drinking_water' } },
      { label: 'Restrooms', tags: { amenity: 'toilets' } },
      { label: 'Beaches', tags: { leisure: 'beach_resort' } },
    ],
  },
  {
    id: 'SHOPPING',
    label: 'Shopping',
    color: '#F57F17',
    tagMatches: [
      { key: 'shop', values: ['supermarket', 'convenience', 'mall', 'department_store', 'clothes', 'hairdresser', 'beauty', 'massage', 'gift', 'laundry', 'variety_store', 'mobile_phone', 'dry_cleaning', 'books', 'furniture', 'jewelry', 'optician', 'florist', 'chemist', 'storage_rental', 'shoes', 'tobacco', 'hardware', 'pet', 'electronics', 'bicycle', 'garden_centre', 'tattoo', 'barber', 'second_hand', 'e-cigarette', 'cannabis'] },
    ],
    subtypes: [
      { label: 'Supermarkets', tags: { shop: 'supermarket' } },
      { label: 'Convenience Stores', tags: { shop: 'convenience' } },
      { label: 'Malls', tags: { shop: 'mall' } },
      { label: 'Department Stores', tags: { shop: 'department_store' } },
      { label: 'Clothing', tags: { shop: 'clothes' } },
      { label: 'Shoe Stores', tags: { shop: 'shoes' } },
      { label: 'Jewelry', tags: { shop: 'jewelry' } },
      { label: 'Hair Salons', tags: { shop: 'hairdresser' } },
      { label: 'Barber Shops', tags: { shop: 'barber' } },
      { label: 'Beauty & Spa', tags: { shop: 'beauty' } },
      { label: 'Massage', tags: { shop: 'massage' } },
      { label: 'Tattoo Shops', tags: { shop: 'tattoo' } },
      { label: 'Bookstores', tags: { shop: 'books' } },
      { label: 'Gift Shops', tags: { shop: 'gift' } },
      { label: 'Florists', tags: { shop: 'florist' } },
      { label: 'Furniture', tags: { shop: 'furniture' } },
      { label: 'Hardware Stores', tags: { shop: 'hardware' } },
      { label: 'Phone Stores', tags: { shop: 'mobile_phone' } },
      { label: 'Opticians', tags: { shop: 'optician' } },
      { label: 'Drug Stores', tags: { shop: 'chemist' } },
      { label: 'Laundromats', tags: { shop: 'laundry' } },
      { label: 'Dry Cleaners', tags: { shop: 'dry_cleaning' } },
      { label: 'Variety Stores', tags: { shop: 'variety_store' } },
      { label: 'Tobacco Shops', tags: { shop: 'tobacco' } },
      { label: 'Vape Shops', tags: { shop: 'e-cigarette' } },
      { label: 'Cannabis', tags: { shop: 'cannabis' } },
      { label: 'Thrift Stores', tags: { shop: 'second_hand' } },
      { label: 'Storage Rentals', tags: { shop: 'storage_rental' } },
      { label: 'Pet Stores', tags: { shop: 'pet' } },
      { label: 'Electronics', tags: { shop: 'electronics' } },
      { label: 'Bicycle Shops', tags: { shop: 'bicycle' } },
      { label: 'Garden Centers', tags: { shop: 'garden_centre' } },
    ],
  },
  {
    id: 'HEALTHCARE',
    label: 'Healthcare',
    color: '#D32F2F',
    tagMatches: [
      { key: 'amenity', values: ['hospital', 'pharmacy', 'clinic', 'dentist', 'doctors', 'veterinary', 'nursing_home'] },
    ],
    subtypes: [
      { label: 'Hospitals', tags: { amenity: 'hospital' } },
      { label: 'Pharmacies', tags: { amenity: 'pharmacy' } },
      { label: 'Clinics', tags: { amenity: 'clinic' } },
      { label: 'Dentists', tags: { amenity: 'dentist' } },
      { label: 'Doctors', tags: { amenity: 'doctors' } },
      { label: 'Veterinary', tags: { amenity: 'veterinary' } },
      { label: 'Nursing Homes', tags: { amenity: 'nursing_home' } },
    ],
  },
  {
    id: 'EDUCATION',
    label: 'Education',
    color: '#5D4037',
    tagMatches: [
      { key: 'amenity', values: ['school', 'library', 'college', 'university', 'childcare', 'kindergarten'] },
    ],
    subtypes: [
      { label: 'Schools', tags: { amenity: 'school' } },
      { label: 'Libraries', tags: { amenity: 'library' } },
      { label: 'Colleges', tags: { amenity: 'college' } },
      { label: 'Universities', tags: { amenity: 'university' } },
      { label: 'Childcare', tags: { amenity: 'childcare' } },
      { label: 'Kindergartens', tags: { amenity: 'kindergarten' } },
    ],
  },
  {
    id: 'LODGING',
    label: 'Lodging',
    color: '#7B1FA2',
    tagMatches: [
      { key: 'tourism', values: ['hotel', 'motel', 'hostel', 'camp_site', 'guest_house', 'caravan_site'] },
    ],
    subtypes: [
      { label: 'Hotels', tags: { tourism: 'hotel' } },
      { label: 'Motels', tags: { tourism: 'motel' } },
      { label: 'Hostels', tags: { tourism: 'hostel' } },
      { label: 'Campgrounds', tags: { tourism: 'camp_site' } },
      { label: 'Guest Houses', tags: { tourism: 'guest_house' } },
      { label: 'RV Parks', tags: { tourism: 'caravan_site' } },
    ],
  },
  {
    id: 'PARKING',
    label: 'Parking',
    color: '#455A64',
    tagMatches: [
      { key: 'amenity', values: ['parking'] },
    ],
    subtypes: null,
  },
  {
    id: 'FINANCE',
    label: 'Finance',
    color: '#00695C',
    tagMatches: [
      { key: 'amenity', values: ['bank', 'atm'] },
    ],
    subtypes: [
      { label: 'Banks', tags: { amenity: 'bank' } },
      { label: 'ATMs', tags: { amenity: 'atm' } },
    ],
  },
  {
    id: 'WORSHIP',
    label: 'Places of Worship',
    color: '#4E342E',
    tagMatches: [
      { key: 'amenity', values: ['place_of_worship'] },
    ],
    subtypes: null,
  },
  {
    id: 'TOURISM_HISTORY',
    label: 'Tourism & History',
    color: '#FF6F00',
    tagMatches: [
      { key: 'tourism', values: ['museum', 'attraction', 'viewpoint', 'artwork', 'gallery', 'information', 'zoo', 'aquarium', 'theme_park'] },
      { key: 'historic', values: ['memorial', 'monument', 'cemetery', 'building', 'ruins', 'maritime'] },
    ],
    subtypes: [
      { label: 'Museums', tags: { tourism: 'museum' } },
      { label: 'Attractions', tags: { tourism: 'attraction' } },
      { label: 'Viewpoints', tags: { tourism: 'viewpoint' } },
      { label: 'Memorials', tags: { historic: 'memorial' } },
      { label: 'Monuments', tags: { historic: 'monument' } },
      { label: 'Public Art', tags: { tourism: 'artwork' } },
      { label: 'Galleries', tags: { tourism: 'gallery' } },
      { label: 'Info Points', tags: { tourism: 'information' } },
      { label: 'Cemeteries', tags: { historic: 'cemetery' } },
      { label: 'Historic Bldgs', tags: { historic: 'building' } },
      { label: 'Ruins', tags: { historic: 'ruins' } },
      { label: 'Maritime', tags: { historic: 'maritime' } },
      { label: 'Zoos', tags: { tourism: 'zoo' } },
      { label: 'Aquariums', tags: { tourism: 'aquarium' } },
      { label: 'Theme Parks', tags: { tourism: 'theme_park' } },
    ],
  },
  {
    id: 'EMERGENCY',
    label: 'Emergency Svc',
    color: '#B71C1C',
    tagMatches: [
      { key: 'amenity', values: ['police', 'fire_station'] },
    ],
    subtypes: [
      { label: 'Police', tags: { amenity: 'police' } },
      { label: 'Fire Stations', tags: { amenity: 'fire_station' } },
    ],
  },
  {
    id: 'AUTO_SERVICES',
    label: 'Auto Services',
    color: '#37474F',
    tagMatches: [
      { key: 'shop', values: ['car_repair', 'tyres', 'car', 'car_parts'] },
      { key: 'amenity', values: ['car_wash', 'car_rental'] },
    ],
    subtypes: [
      { label: 'Repair Shops', tags: { shop: 'car_repair' } },
      { label: 'Car Washes', tags: { amenity: 'car_wash' } },
      { label: 'Rentals', tags: { amenity: 'car_rental' } },
      { label: 'Tire Shops', tags: { shop: 'tyres' } },
      { label: 'Dealerships', tags: { shop: 'car' } },
      { label: 'Parts Stores', tags: { shop: 'car_parts' } },
    ],
  },
  {
    id: 'ENTERTAINMENT',
    label: 'Entertainment',
    color: '#00838F',
    tagMatches: [
      { key: 'leisure', values: ['fitness_centre', 'sports_centre', 'golf_course', 'marina', 'stadium', 'disc_golf_course', 'dance', 'amusement_arcade', 'ice_rink', 'bowling_alley', 'water_park', 'miniature_golf', 'escape_game'] },
      { key: 'amenity', values: ['theatre', 'cinema', 'nightclub', 'events_venue', 'arts_centre', 'studio'] },
    ],
    subtypes: [
      { label: 'Fitness', tags: { leisure: 'fitness_centre' } },
      { label: 'Sports Centres', tags: { leisure: 'sports_centre' } },
      { label: 'Golf Courses', tags: { leisure: 'golf_course' } },
      { label: 'Disc Golf', tags: { leisure: 'disc_golf_course' } },
      { label: 'Marinas', tags: { leisure: 'marina' } },
      { label: 'Stadiums', tags: { leisure: 'stadium' } },
      { label: 'Theatres', tags: { amenity: 'theatre' } },
      { label: 'Cinemas', tags: { amenity: 'cinema' } },
      { label: 'Nightclubs', tags: { amenity: 'nightclub' } },
      { label: 'Event Venues', tags: { amenity: 'events_venue' } },
      { label: 'Arts Centres', tags: { amenity: 'arts_centre' } },
      { label: 'Studios', tags: { amenity: 'studio' } },
      { label: 'Dance Studios', tags: { leisure: 'dance' } },
      { label: 'Arcades', tags: { leisure: 'amusement_arcade' } },
      { label: 'Ice Rinks', tags: { leisure: 'ice_rink' } },
      { label: 'Bowling', tags: { leisure: 'bowling_alley' } },
      { label: 'Water Parks', tags: { leisure: 'water_park' } },
      { label: 'Mini Golf', tags: { leisure: 'miniature_golf' } },
      { label: 'Escape Rooms', tags: { leisure: 'escape_game' } },
    ],
  },
  {
    id: 'OFFICES',
    label: 'Offices & Services',
    color: '#546E7A',
    tagMatches: [
      { key: 'office', values: ['company', 'estate_agent', 'lawyer', 'insurance', 'tax_advisor'] },
    ],
    subtypes: [
      { label: 'Companies', tags: { office: 'company' } },
      { label: 'Real Estate', tags: { office: 'estate_agent' } },
      { label: 'Law Offices', tags: { office: 'lawyer' } },
      { label: 'Insurance', tags: { office: 'insurance' } },
      { label: 'Tax Advisors', tags: { office: 'tax_advisor' } },
    ],
  },
]

const categoryMap = new Map(CATEGORIES.map(c => [c.id, c]))

export function classifyPoi(tags: Record<string, string>): Category | null {
  for (const cat of CATEGORIES) {
    for (const tm of cat.tagMatches) {
      const val = tags[tm.key]
      if (val && tm.values.includes(val)) {
        return cat
      }
    }
  }
  return null
}

export function getCategoryById(id: string): Category | undefined {
  return categoryMap.get(id)
}

export const UNCATEGORIZED_COLOR = '#9E9E9E'

/** Resolve a category from either an ID ("FOOD_DRINK") or a tag string ("amenity=restaurant") */
export function resolveCategory(cat: string): Category | null {
  return categoryMap.get(cat) || getCategoryByTag(cat)
}

/** Look up category by a "key=value" tag string, e.g. "amenity=restaurant" */
export function getCategoryByTag(tag: string): Category | null {
  const [key, value] = tag.split('=', 2)
  if (!key || !value) return null
  for (const cat of CATEGORIES) {
    for (const tm of cat.tagMatches) {
      if (tm.key === key && tm.values.includes(value)) return cat
    }
  }
  return null
}

/** Get all "key=value" tag strings for a category */
export function getCategoryTags(categoryId: string): string[] {
  const cat = categoryMap.get(categoryId)
  if (!cat) return []
  const tags: string[] = []
  for (const tm of cat.tagMatches) {
    for (const v of tm.values) tags.push(`${tm.key}=${v}`)
  }
  return tags
}

/** Get "key=value" tag strings for a specific subtype */
export function getSubtypeTags(subtype: { tags: Record<string, string> }): string[] {
  return Object.entries(subtype.tags).map(([k, v]) => `${k}=${v}`)
}
