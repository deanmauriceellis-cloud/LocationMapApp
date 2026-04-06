#!/tmp/forge-gen/bin/python
"""
WickedSalemWitchCityTour — POI Icon Generator
Generates 155 unique witch-themed circular icons for all POI subtypes
using local Stable Diffusion Forge Gradio API (DreamShaperXL Turbo).

Style: Fun, family-friendly, slightly spooky Salem witch aesthetic.

Usage:
  1. Start Forge: bash ~/AI-Studio/forge-start.sh
  2. Run: /tmp/forge-gen/bin/python tools/generate-poi-icons.py
  3. Icons saved to: tools/poi-icons/

Generates 3 variants per icon (pick the best). ~465 images total.
"""

import json
import os
import shutil
import subprocess
import sys
import time
import random
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from gradio_client import Client

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------
FORGE_URL = "http://127.0.0.1:7860"
OUTPUT_DIR = Path(__file__).parent / "poi-icons"

CHECKPOINT = "DreamShaperXL_Turbo_v2_1 [4496b36d48]"

SAMPLER = "DPM++ SDE"
SCHEDULE_TYPE = "Karras"
STEPS = 6          # Turbo needs only 6-8
CFG_SCALE = 2.0    # Turbo sweet spot
WIDTH = 512
HEIGHT = 512
PREVIEW_SCALE = 2  # Show each image at 2x in viewer

# Style applied to every prompt
# Variant templates — 8 flavors, all OBJECT-FOCUSED with character as background menace.
# Business items fill the frame. Character is a small angry threatening figure lurking behind.
# {activity} = dense business object nouns. {object_desc} = standalone scary object scene.
VARIANTS = [
    ("evil",     "{object_desc}, prominent filling the entire frame, ABSOLUTE EVIL dark sinister atmosphere, blood red lighting, shadows crawling, cursed objects with dark aura, a furious witch lurking in the dark background glaring with hatred, game item icon, sticker design, dark horror art style, black and blood red background, stylized 2D illustration, menacing and terrifying, no text no words no letters, digital art, high quality"),
    ("cute",     "{object_desc}, prominent filling the entire frame, ADORABLE BUT EVIL cute kawaii style with tiny fangs and evil grins on the objects, pastel purple and pink with dark undertones, a cute angry little witch in the background shaking her fist, game item icon, sticker design, cute horror chibi style, dark purple gradient background, stylized 2D illustration, adorable yet sinister, no text no words no letters, digital art, high quality"),
    ("devil",    "{object_desc}, prominent filling the entire frame, HELLISH INFERNAL style, objects burning with hellfire, lava cracks, brimstone smoke, pentagram floor, a devil with horns and pitchfork lurking menacingly in the fiery background, game item icon, sticker design, hellfire art style, deep red and black background, stylized 2D illustration, infernal and threatening, no text no words no letters, digital art, high quality"),
    ("psycho",   "{object_desc}, prominent filling the entire frame, PSYCHOTIC UNHINGED style, objects distorted and melting, crazy angles, neon splatter paint, chaotic energy, a deranged warlock with twitching eye cackling madly in the background, game item icon, sticker design, psychedelic horror style, neon purple and toxic green background, stylized 2D illustration, insane and chaotic, no text no words no letters, digital art, high quality"),
    ("undead",   "{object_desc}, prominent filling the entire frame, UNDEAD RISEN FROM THE GRAVE style, objects decayed and ancient with moss and cracks, ghostly glow, tombstones, a skeletal figure in tattered robes reaching from the shadows in the background, game item icon, sticker design, gothic undead style, dark green and grey background, stylized 2D illustration, haunting and decrepit, no text no words no letters, digital art, high quality"),
    ("demon",    "{object_desc}, prominent filling the entire frame, DEMONIC POSSESSION style, objects floating and vibrating with supernatural energy, glowing sigils, black smoke tendrils, a massive horned demon face emerging from darkness in the background, game item icon, sticker design, demonic dark fantasy style, deep black and purple background, stylized 2D illustration, overwhelming dark power, no text no words no letters, digital art, high quality"),
    ("zombie",   "{object_desc}, prominent filling the entire frame, ZOMBIE APOCALYPSE style, objects smeared with grime and decay, broken glass, boarded windows, a shambling zombie employee with torn apron reaching toward the viewer from behind the counter in background, game item icon, sticker design, zombie horror style, sickly green and brown background, stylized 2D illustration, post-apocalyptic decay, no text no words no letters, digital art, high quality"),
    ("witchcraft", "{object_desc}, prominent filling the entire frame, WITCHCRAFT AND SORCERY style, objects enchanted with glowing purple magical energy, floating runes, spell circles, bubbling potions, a powerful warlock casting a spell with crackling energy in the shadowy background, game item icon, sticker design, dark sorcery style, deep purple and midnight blue background, stylized 2D illustration, mystical and dangerous, no text no words no letters, digital art, high quality"),
]

NEGATIVE_PROMPT = (
    "photorealistic, realistic, photograph, 3d render, "
    "blurry, low quality, ugly, deformed, "
    "watermark, text, logo, words, letters, numbers"
)

# ---------------------------------------------------------------------------
# All 155 POI subtypes — each has an activity (for character variants) and
# an object_desc (for the object-only variant).
# Format: (category_id, subtype_slug, activity_desc, object_desc)
#   activity_desc: "doing X with Y" — inserted after character description
#   object_desc:   standalone scary object scene, no character
# ---------------------------------------------------------------------------
POI_ICONS = [
    # ── Food & Drink (18) ──
    # Format: (category, slug, activity_with_objects, object_scene)
    ("food_drink", "restaurants", "restaurant kitchen with stove pots pans plates of food prep counter cutting board knives serving dishes", "restaurant table with steaming plates of food wine glasses napkins candles silverware in spooky dining room"),
    ("food_drink", "fast_food", "fast food counter with burger grill deep fryer french fries soda fountain menu board cash register", "a giant cheeseburger with fries and soda cup and ketchup packets on a fast food tray"),
    ("food_drink", "cafes", "coffee shop counter with espresso machine coffee grinder cups saucers pastry display coffee beans bags", "a steaming coffee cup with latte art and coffee beans and pastries on a cafe table"),
    ("food_drink", "bars", "bar counter with cocktail shaker bottles of liquor beer taps shot glasses ice bucket garnishes", "cocktail glasses and liquor bottles and beer taps and shot glasses lined up on a bar counter"),
    ("food_drink", "pubs", "pub bar with beer taps pint glasses wooden bar stools dartboard ale barrels pub menu", "foaming pint glasses of beer and ale barrels and pub snacks on a wooden bar"),
    ("food_drink", "ice_cream", "ice cream shop with scoops waffle cones toppings sprinkles sundae cups freezer display", "ice cream cones with colorful scoops and sundae cups and toppings and waffle cones"),
    ("food_drink", "bakeries", "bakery with bread loaves cupcakes cake decorating oven trays flour bags rolling pin piping bags", "fresh bread loaves cupcakes wedding cake donuts pastries on bakery display shelves"),
    ("food_drink", "pastry_shops", "pastry shop with croissants eclairs tarts display case piping bag powdered sugar baking sheets", "croissants eclairs tarts danish pastries and cream puffs on a pastry shop display"),
    ("food_drink", "candy_stores", "candy shop with jars of candy lollipops gummy bears chocolate bars candy canes display shelves", "jars of colorful candy lollipops gummy bears chocolate bars and candy canes on shelves"),
    ("food_drink", "liquor_stores", "liquor store with bottles of whiskey vodka rum wine shelves price tags paper bags register", "shelves of liquor bottles whiskey vodka rum wine with price tags in a liquor store"),
    ("food_drink", "wine_shops", "wine shop with wine bottles wine rack corkscrew wine glasses tasting counter barrel", "wine bottles in a wooden wine rack with glasses corkscrew and tasting notes"),
    ("food_drink", "delis", "deli counter with sliced meats cheeses bread rolls deli slicer scale wrapped sandwiches pickles", "a deli sandwich with sliced meats cheeses lettuce on a cutting board with deli slicer behind"),
    ("food_drink", "butcher_shops", "butcher shop with meat hooks hanging beef pork cuts butcher block cleaver knives meat grinder scale", "a butcher block with cleaver knives hanging meat cuts on hooks and meat grinder and scale"),
    ("food_drink", "seafood_markets", "seafood market with fish on ice lobster crab shrimp oysters fish counter scale nets", "fresh fish on ice with lobster crab shrimp oysters and fishing nets at a seafood counter"),
    ("food_drink", "marketplaces", "market stall with crates of produce fruits vegetables price signs scale canvas awning baskets", "market stalls with crates of fresh fruits vegetables flowers and hanging produce"),
    ("food_drink", "breweries", "brewery with copper brewing tanks fermentation vats hops grain sacks beer kegs bottling line", "copper brewing tanks and beer kegs and hops and grain sacks in a brewery"),
    ("food_drink", "wineries", "winery with wine barrels grape press vineyard crates wine bottles fermenting vats corking machine", "wine barrels and grape press and vineyard crates and bottles in a stone winery cellar"),
    ("food_drink", "distilleries", "distillery with copper pot still coiled tubes glass bottles aging barrels grain sacks thermometer", "a copper pot still with coiled tubes and glass bottles and aging barrels in a distillery"),

    # ── Fuel & Charging (2) ──
    ("fuel_charging", "gas_stations", "gas station with fuel pumps gas nozzle price sign convenience store oil cans windshield squeegee", "gas pumps with fuel nozzles and price signs at a gas station with oil cans"),
    ("fuel_charging", "charging_stations", "EV charging station with charging cable plug electric car battery indicator parking bollard", "an electric vehicle charging station with cable plug and battery indicator screen"),

    # ── Transit (6) ──
    ("transit", "train_stations", "train station platform with train tracks locomotive departure board tickets turnstile benches", "a steam locomotive at a train platform with departure board and ticket booth"),
    ("transit", "bus_stations", "bus station with city bus route sign bus stop shelter bench schedule timetable", "a city bus at a bus stop with route sign schedule board and waiting shelter"),
    ("transit", "airports", "airport terminal with airplane runway control tower departure board luggage carousel check-in desk", "an airplane on a runway with control tower and airport terminal building"),
    ("transit", "bike_rentals", "bike rental station with bicycles in a rack helmets lock chain rental kiosk pump", "bicycles lined up in a rental rack with helmets and a rental kiosk"),
    ("transit", "ferry_terminals", "ferry terminal with ferry boat dock ramp water waves ticket booth life preservers", "a ferry boat at a dock with ramp and ticket booth and life preservers"),
    ("transit", "taxi_stands", "taxi stand with yellow taxi cab roof light meter fare card taxi sign waiting area", "a yellow taxi cab with roof light and meter at a taxi stand"),

    # ── Civic & Gov (9) ──
    ("civic", "town_halls", "town hall with podium microphone gavel council chambers flags columns clock tower", "a colonial town hall building with columns clock tower and flags"),
    ("civic", "courthouses", "courthouse with judge bench gavel scales of justice law books witness stand jury box", "a judge bench with gavel scales of justice and law books in a courtroom"),
    ("civic", "post_offices", "post office counter with mail packages stamps scale postbox sorting bins envelopes", "a post office counter with packages stamps scale and mail sorting bins"),
    ("civic", "post_boxes", "blue post box mail collection box with mail slot pull handle collection times schedule", "a blue USPS post box on a street corner with mail slot"),
    ("civic", "gov_offices", "government office with desk paperwork filing cabinets rubber stamps official seal flags", "a government office desk with paperwork filing cabinets and official seal"),
    ("civic", "community_centres", "community center with folding chairs stage bulletin board activity sign-ups meeting room", "a community center building with bulletin board and activity signs and welcome banner"),
    ("civic", "social_services", "social services office with intake desk pamphlets forms waiting chairs resource board", "a social services desk with pamphlets forms and resource information board"),
    ("civic", "recycling", "recycling center with sorting bins green blue bins cardboard bales crushing machine", "recycling bins green blue containers and cardboard bales at a recycling center"),
    ("civic", "embassies", "embassy with national flags security gate diplomatic seal reception desk metal detector", "an embassy building with national flags security gate and diplomatic seal"),

    # ── Parks & Rec (17) ──
    ("parks_rec", "parks", "park with trees benches walking path playground fountain grass picnic area lamp posts", "a park with trees benches walking path fountain and lamp posts"),
    ("parks_rec", "nature_reserves", "nature reserve with forest trail wildlife signs binoculars bird feeders hiking path map board", "a forest trail with wildlife signs and hiking path in a nature reserve"),
    ("parks_rec", "playgrounds", "playground with swings slide monkey bars sandbox seesaw jungle gym rubber ground", "a playground with swings slide monkey bars and sandbox"),
    ("parks_rec", "sports_fields", "sports field with soccer goal net lines on grass scoreboard bleachers corner flags", "a soccer field with goal net scoreboard and bleachers"),
    ("parks_rec", "tracks", "running track with lanes hurdles starting blocks stopwatch finish line bleachers", "a running track with lanes hurdles and starting blocks"),
    ("parks_rec", "rec_grounds", "recreation grounds with open grass field picnic tables pavilion bbq grills trash cans", "recreation grounds with picnic tables pavilion and bbq grills"),
    ("parks_rec", "pools", "swimming pool with diving board lane ropes lifeguard chair pool ladder towels lounge chairs", "a swimming pool with diving board lane ropes and lifeguard chair"),
    ("parks_rec", "dog_parks", "dog park with fence gate water bowl tennis balls agility ramps fire hydrant waste bags", "a fenced dog park with agility equipment water bowls and dogs playing"),
    ("parks_rec", "gardens", "garden with flower beds watering can wheelbarrow trowel gloves seed packets trellis", "a garden with flower beds blooming flowers trellis and garden tools"),
    ("parks_rec", "boat_ramps", "boat ramp with boat trailer dock cleats water ramp concrete slope parking", "a boat ramp with trailer going into water and dock with cleats"),
    ("parks_rec", "skateparks", "skatepark with halfpipe ramps rails grind box skateboard helmet knee pads", "a skatepark with halfpipe ramps rails and grind boxes"),
    ("parks_rec", "picnic_sites", "picnic site with picnic table checkered cloth basket plates cups cooler grill", "a picnic table with checkered cloth basket and plates and a grill"),
    ("parks_rec", "shelters", "park shelter with roof wooden posts picnic tables fire pit rain cover benches", "a park shelter with wooden roof posts picnic tables and fire pit"),
    ("parks_rec", "fountains", "decorative fountain with water jets stone basin coins tiered bowls spray", "a decorative stone fountain with water jets and tiered bowls"),
    ("parks_rec", "drinking_water", "drinking water fountain with push button spout basin accessible height", "a drinking water fountain with push button and spout"),
    ("parks_rec", "restrooms", "public restroom building with door signs male female accessible hand wash station", "a public restroom building with door signs and accessible symbols"),
    ("parks_rec", "beaches", "beach with sand waves umbrella beach chairs towels surfboard cooler shells seagulls", "a beach with sand umbrellas beach chairs surfboard and ocean waves"),

    # ── Shopping (31) ──
    ("shopping", "supermarkets", "supermarket with shopping cart aisles shelves produce section checkout counter bags", "a shopping cart in supermarket aisle with shelves of groceries and produce"),
    ("shopping", "convenience_stores", "convenience store with snack shelves refrigerator drinks counter register lottery neon open sign", "a small convenience store with snack shelves drink fridge and neon open sign"),
    ("shopping", "malls", "shopping mall with escalator storefronts directory fountain food court shopping bags", "a shopping mall interior with escalator storefronts and fountain"),
    ("shopping", "department_stores", "department store with display mannequins clothing racks perfume counter gift wrap elevator", "department store with mannequins clothing racks and perfume counter displays"),
    ("shopping", "clothing", "clothing store with racks of clothes hangers dress forms fitting room mirror price tags", "clothing racks with hanging outfits dress forms and a fitting room mirror"),
    ("shopping", "shoe_stores", "shoe store with shoe display shelves shoe boxes footwear try-on bench mirror shoehorn", "shoe display shelves with boxes of shoes and a try-on bench and mirror"),
    ("shopping", "jewelry", "jewelry store with display cases rings necklaces bracelets diamonds velvet trays magnifier", "jewelry display cases with rings necklaces diamonds on velvet trays"),
    ("shopping", "hair_salons", "hair salon with salon chair mirror scissors comb blow dryer hair products cape shampoo sink", "a salon chair with mirror scissors comb blow dryer and hair products"),
    ("shopping", "barber_shops", "barber shop with barber chair striped pole straight razor clippers hot towel shaving cream mirror", "a barber chair with striped pole straight razor clippers and hot towel"),
    ("shopping", "beauty_spa", "beauty spa with massage table face mask cucumbers hot stones candles oils towels robes", "spa massage table with face mask products hot stones candles and oils"),
    ("shopping", "massage", "massage parlor with massage table oil bottles hot stones towels relaxation music candles", "a massage table with oil bottles hot stones and towels in a treatment room"),
    ("shopping", "tattoo_shops", "tattoo shop with tattoo machine ink bottles needle flash art designs on walls tattoo chair", "a tattoo machine with ink bottles and flash art designs covering the walls"),
    ("shopping", "bookstores", "bookstore with bookshelves stacks of books reading nook armchair bestseller display register", "bookshelves packed with books and a reading nook with armchair and lamp"),
    ("shopping", "gift_shops", "gift shop with souvenir shelves greeting cards wrapping paper ribbon bows gift bags display", "gift shop shelves with souvenirs greeting cards wrapped presents and gift bags"),
    ("shopping", "florists", "florist shop with flower buckets bouquets vases ribbon wrapping paper cooler display roses", "bouquets of flowers in vases and buckets with roses and wrapping ribbon"),
    ("shopping", "furniture", "furniture store with sofas chairs tables lamps rugs bedroom sets dining display showroom", "a furniture showroom with sofa armchair table lamp and rug display"),
    ("shopping", "hardware_stores", "hardware store with tool wall hammers wrenches screwdrivers nails paint cans lumber aisle", "a hardware store wall of tools with hammers wrenches paint cans and lumber"),
    ("shopping", "phone_stores", "phone store with smartphones tablets display cases chargers accessories screen protectors", "smartphones and tablets on display stands with accessories and chargers"),
    ("shopping", "opticians", "optician shop with eyeglasses display wall frames lens machine eye chart fitting table", "eyeglasses on a display wall with frames eye chart and lens equipment"),
    ("shopping", "drug_stores", "drug store with pharmacy counter pill bottles prescription bags OTC medicine shelves vitamins", "pharmacy counter with pill bottles prescriptions and medicine shelves"),
    ("shopping", "laundromats", "laundromat with washing machines dryers laundry baskets detergent folding table coins", "washing machines and dryers in a laundromat with laundry baskets and detergent"),
    ("shopping", "dry_cleaners", "dry cleaners with garment bags on conveyor rack pressing iron steamer hangers counter ticket", "garment bags on a conveyor rack with pressing iron and hangers at dry cleaners"),
    ("shopping", "variety_stores", "variety store with cheap goods bins toys household items cluttered aisles everything a dollar", "cluttered variety store shelves with assorted cheap goods toys and household items"),
    ("shopping", "tobacco_shops", "tobacco shop with cigars in humidor pipes tobacco tins rolling papers lighters display", "cigars in a humidor with pipes tobacco tins and lighters on display"),
    ("shopping", "vape_shops", "vape shop with vape devices e-liquid bottles display case coils mods tank counter", "vape devices and e-liquid bottles on a display case with mods and tanks"),
    ("shopping", "cannabis", "cannabis dispensary with glass jars of flower edibles display pre-rolls vape pens menu board", "glass jars of cannabis flower and edibles display and pre-rolls on dispensary shelves"),
    ("shopping", "thrift_stores", "thrift store with clothing racks vintage items used furniture books toys donation bins", "thrift store racks of vintage clothing and used furniture and bins of items"),
    ("shopping", "storage_rentals", "storage facility with roll-up doors padlocks dollies moving boxes unit numbers hallway", "storage unit hallway with roll-up doors padlocks and stacked moving boxes"),
    ("shopping", "pet_stores", "pet store with aquariums birdcages dog toys leashes food bags collars small animal cages", "pet store with aquariums birdcages and shelves of pet food toys and leashes"),
    ("shopping", "electronics", "electronics store with TVs computers phones headphones cables display tables chargers", "electronics store display with TVs computers phones and headphones on shelves"),
    ("shopping", "bicycle_shops", "bicycle shop with bikes on wall rack wheels tires tubes helmets repair stand pump tools", "bicycles hanging on a wall rack with wheels helmets and repair tools"),
    ("shopping", "garden_centers", "garden center with potted plants flowers seeds soil bags pots watering cans greenhouse", "potted plants and flowers and soil bags in a garden center greenhouse"),

    # ── Healthcare (7) ──
    ("healthcare", "hospitals", "hospital with gurney IV drip monitors stethoscope scrubs wheelchair medical chart curtain", "a hospital room with gurney IV drip monitors and medical equipment"),
    ("healthcare", "pharmacies", "pharmacy counter with prescription bottles pill organizer mortar pestle medicine shelves Rx sign", "pharmacy shelves with prescription bottles medicine boxes and Rx sign"),
    ("healthcare", "clinics", "medical clinic with exam table stethoscope blood pressure cuff otoscope exam light charts", "a medical clinic exam room with table stethoscope and blood pressure cuff"),
    ("healthcare", "dentists", "dentist office with dental chair drill mirror tools tray bib rinse cup x-ray light", "a dentist chair with drill mirror tools tray and overhead light"),
    ("healthcare", "doctors", "doctor office with desk stethoscope prescription pad anatomy poster blood pressure cuff scale", "a doctor office with stethoscope prescription pad anatomy poster and desk"),
    ("healthcare", "veterinary", "veterinary clinic with exam table pet carrier stethoscope vaccines pet treats scale cone", "a veterinary exam table with pet carrier stethoscope and medical supplies"),
    ("healthcare", "nursing_homes", "nursing home with hospital bed wheelchair walker call button bedside table TV flowers", "a nursing home room with hospital bed wheelchair walker and bedside table"),

    # ── Education (6) ──
    ("education", "schools", "school classroom with desks chalkboard textbooks pencils backpacks globe apple teacher desk", "a school classroom with desks chalkboard textbooks globe and teacher desk"),
    ("education", "libraries", "library with tall bookshelves reading tables card catalog desk lamp quiet signs book cart", "tall library bookshelves with reading tables desk lamp and book cart"),
    ("education", "colleges", "college campus with lecture hall podium projector graduation caps textbooks campus quad", "a college lecture hall with podium projector screen and graduation caps"),
    ("education", "universities", "university with grand hall columns library stacks lab equipment lecture podium banners", "a grand university building with columns banners and academic crest"),
    ("education", "childcare", "childcare center with cribs toys building blocks play mat sippy cups high chairs", "a childcare room with cribs toys building blocks play mat and high chairs"),
    ("education", "kindergartens", "kindergarten with tiny desks crayons finger paint alphabet chart cubbies reading rug", "a kindergarten room with tiny desks crayons alphabet chart and cubbies"),

    # ── Lodging (6) ──
    ("lodging", "hotels", "hotel lobby with front desk room keys luggage cart bellhop stand elevator chandelier", "a grand hotel lobby with front desk room keys luggage cart and chandelier"),
    ("lodging", "motels", "motel with room doors parking lot ice machine vending neon vacancy sign room numbers", "a motel exterior with room doors neon vacancy sign and parking lot"),
    ("lodging", "hostels", "hostel with bunk beds lockers shared bathroom backpacks common room bulletin board", "hostel bunk beds with lockers backpacks and a common room bulletin board"),
    ("lodging", "campgrounds", "campground with tent campfire ring picnic table cooler sleeping bags lantern firewood", "a campsite with tent campfire ring picnic table and lantern"),
    ("lodging", "guest_houses", "guest house with cozy bedroom quilted bed nightstand welcome basket keys porch", "a cozy guest house with porch welcome sign quilted bedroom and keys"),
    ("lodging", "rv_parks", "RV park with motorhome hookups power pedestal water hose picnic table awning campfire", "an RV parked at a campsite with hookups awning and picnic table"),

    # ── Parking (1 — category-level) ──
    ("parking", "parking", "parking lot with parking meter spaces painted lines ticket machine gate arm signs P sign", "a parking lot with meter painted spaces P sign and ticket machine"),

    # ── Finance (2) ──
    ("finance", "banks", "bank interior with teller window vault door safe deposit boxes counter pen chain forms", "a bank vault door with safe deposit boxes and stacks of gold coins"),
    ("finance", "atms", "ATM machine with card slot screen keypad cash dispenser receipt slot bank logo", "an ATM machine with card slot screen keypad and cash being dispensed"),

    # ── Worship (1 — category-level) ──
    ("worship", "places_of_worship", "church interior with pews altar stained glass windows candles pulpit organ hymnals", "a church with steeple stained glass windows and bell tower under moonlit sky"),

    # ── Tourism & History (15) ──
    ("tourism_history", "museums", "museum gallery with display cases artifacts paintings roped exhibits information plaques", "museum display cases with artifacts paintings and information plaques"),
    ("tourism_history", "attractions", "tourist attraction with ticket booth turnstile souvenir stand entrance arch tour guide sign", "a tourist attraction entrance with ticket booth turnstile and welcome arch"),
    ("tourism_history", "viewpoints", "scenic viewpoint with telescope railing information panel binoculars panoramic view bench", "a scenic viewpoint telescope with railing and panoramic view"),
    ("tourism_history", "memorials", "memorial site with stone monument wreaths plaques flowers candles names engraved fence", "a stone memorial with wreaths engraved names plaques and flowers"),
    ("tourism_history", "monuments", "stone monument with pedestal plaque historical inscription railing dedication date", "a stone monument on pedestal with historical plaque and inscription"),
    ("tourism_history", "public_art", "public art installation with sculpture mural art piece on pedestal artist plaque", "a public art sculpture on a pedestal with artist plaque"),
    ("tourism_history", "galleries", "art gallery with framed paintings sculpture pedestals track lighting white walls bench", "an art gallery with framed paintings on white walls and track lighting"),
    ("tourism_history", "info_points", "information point with brochure rack maps visitor guide desk welcome sign directions", "an information stand with brochure rack maps and visitor guide materials"),
    ("tourism_history", "cemeteries", "cemetery with headstones graves iron fence gate flowers stone angels paths", "a cemetery with headstones iron fence gate stone angels and paths"),
    ("tourism_history", "historic_bldgs", "historic colonial building with period furniture old doors windows plaques wooden beams", "a historic colonial Salem building with period details and historical plaque"),
    ("tourism_history", "ruins", "stone ruins with crumbling walls archways overgrown ivy broken columns foundation", "crumbling stone ruins with archways overgrown ivy and broken columns"),
    ("tourism_history", "maritime", "maritime museum with ship wheel anchor rope nets nautical charts compass sextant model ships", "a ship wheel anchor rope nets and nautical instruments at a maritime display"),
    ("tourism_history", "zoos", "zoo with animal enclosures viewing glass feeding time zookeeper tools info signs paths", "zoo animal enclosures with viewing glass info signs and walking paths"),
    ("tourism_history", "aquariums", "aquarium with large fish tanks coral reef seahorses jellyfish tunnel viewing glass blue light", "a large aquarium tank with fish coral reef jellyfish and blue lighting"),
    ("tourism_history", "theme_parks", "theme park with roller coaster ferris wheel carousel cotton candy game booths rides", "a theme park with roller coaster ferris wheel carousel and game booths"),

    # ── Emergency Svc (2) ──
    ("emergency", "police", "police station with badge handcuffs radio patrol car desk booking window wanted board", "a police badge with handcuffs radio and patrol car at a police station"),
    ("emergency", "fire_stations", "fire station with fire truck ladder hose hydrant helmet boots turnout gear pole bay door", "a fire truck with ladder hose hydrant and firefighter gear at the station"),

    # ── Auto Services (6) ──
    ("auto_services", "repair_shops", "auto repair shop with car on lift wrench socket set oil drain toolbox diagnostic scanner", "a car on a lift with wrench set oil drain pan and toolbox in a repair shop"),
    ("auto_services", "car_washes", "car wash with soap brushes water spray conveyor dryer wax foam cannon vacuum hose", "a car going through a car wash with soap brushes water spray and foam"),
    ("auto_services", "rentals", "car rental counter with car keys rental agreement fleet of cars key board rate sign", "car rental counter with key board fleet of cars and rate sign"),
    ("auto_services", "tire_shops", "tire shop with tires stacked wheel balancer lug wrench jack lift alignment machine", "tires stacked with wheel balancer lug wrench and alignment machine"),
    ("auto_services", "dealerships", "car dealership showroom with shiny new cars price stickers sales desk balloons flags", "a car dealership showroom with shiny new cars and price stickers"),
    ("auto_services", "parts_stores", "auto parts store with shelves of oil filters spark plugs belts batteries brake pads wiper blades", "auto parts shelves with oil filters spark plugs batteries and brake pads"),

    # ── Entertainment (19) ──
    ("entertainment", "fitness", "gym with dumbbells barbells weight rack treadmill bench press mirror rubber floor", "dumbbells barbells weight rack and treadmill in a gym with mirror"),
    ("entertainment", "sports_centres", "sports center with basketball court scoreboard bleachers equipment room nets goals", "a basketball court with scoreboard bleachers and sports equipment"),
    ("entertainment", "golf_courses", "golf course with golf clubs bag tee green flag pin hole fairway cart sand trap", "a golf green with flag pin clubs bag and cart on a fairway"),
    ("entertainment", "disc_golf", "disc golf course with disc basket tee pad disc bag fairway course map sign", "a disc golf basket with discs tee pad and course map sign"),
    ("entertainment", "marinas", "marina with sailboats dock cleats fuel pump boat slips harbormaster shack buoys", "a marina with sailboats at dock slips with buoys and harbormaster shack"),
    ("entertainment", "stadiums", "stadium with field seats floodlights jumbotron scoreboard hot dog vendor pennants", "a stadium with field floodlights jumbotron and scoreboard"),
    ("entertainment", "theatres", "theatre with stage curtains spotlight seats orchestra pit props masks playbill", "a theatre stage with red curtains spotlight and comedy tragedy masks"),
    ("entertainment", "cinemas", "cinema with movie screen projector popcorn bucket soda reclining seats ticket stub", "a cinema screen with projector beam popcorn bucket and movie seats"),
    ("entertainment", "nightclubs", "nightclub with DJ booth turntables disco ball dance floor laser lights speakers bar", "a nightclub DJ booth with turntables disco ball and laser lights"),
    ("entertainment", "event_venues", "event venue with stage podium microphone speakers lighting rig banquet tables chairs", "an event stage with podium microphone speakers and lighting rig"),
    ("entertainment", "arts_centres", "arts center with painting easels pottery wheels sculpture tools kiln gallery space", "painting easels pottery wheels and sculpture tools in an art studio"),
    ("entertainment", "studios", "recording studio with microphone mixing board headphones sound booth monitors speakers", "a recording studio mixing board with microphone headphones and monitors"),
    ("entertainment", "dance_studios", "dance studio with ballet barre mirror wood floor pointe shoes tutu music speaker", "a dance studio with ballet barre wall mirror and wood floor"),
    ("entertainment", "arcades", "arcade with arcade cabinets claw machine pinball skeeball prize counter token machine", "arcade cabinets with claw machine pinball and prize counter"),
    ("entertainment", "ice_rinks", "ice rink with ice skates zamboni boards goal nets rental counter hockey sticks", "an ice rink with skates zamboni and goal nets"),
    ("entertainment", "bowling", "bowling alley with lanes pins ball return bowling ball shoes scoring screen", "a bowling lane with pins ball return and bowling shoes"),
    ("entertainment", "water_parks", "water park with water slides lazy river wave pool splash pad lifeguard tower tubes", "water slides and lazy river and wave pool at a water park"),
    ("entertainment", "mini_golf", "mini golf course with windmill obstacle castle bridge putter golf ball scorecard pencil", "a mini golf course with windmill obstacle castle bridge and putter"),
    ("entertainment", "escape_rooms", "escape room with locked door padlocks clue papers puzzle boxes clock timer key hidden", "a locked escape room door with padlocks clue papers and puzzle boxes"),

    # ── Offices & Services (5) ──
    ("offices", "companies", "office with desk computer monitor keyboard phone filing cabinet whiteboard coffee mug", "an office desk with computer monitor keyboard phone and filing cabinet"),
    ("offices", "real_estate", "real estate office with house listings photos for-sale signs keys lockbox contracts MLS book", "a house with for-sale sign keys lockbox and listing photos"),
    ("offices", "law_offices", "law office with legal books desk gavel scales of justice diploma frames briefcase contracts", "law books gavel scales of justice and legal documents on a mahogany desk"),
    ("offices", "insurance", "insurance office with policy documents umbrella shield logo filing cabinets calculator claims", "insurance policy documents with umbrella shield logo and filing cabinets"),
    ("offices", "tax_advisors", "tax office with calculator tax forms receipts filing cabinets ledger books pen desk lamp", "calculator tax forms receipts and ledger books on a tax preparer desk"),

    # ── Salem: Witch & Occult Shops (5) ──
    ("witch_shop", "witchcraft_shops", "witch shop with crystal balls tarot decks candles herbs spell books cauldron pentagram altar wands", "crystal balls tarot decks spell candles herbs and cauldron on a witch shop altar"),
    ("witch_shop", "occult_supplies", "occult supply shop with ritual daggers chalices incense burners black candles grimoires sigils", "ritual daggers chalices incense burners and grimoires on occult shop shelves"),
    ("witch_shop", "metaphysical", "metaphysical shop with crystals amethyst quartz singing bowls sage bundles essential oils chakra chart", "crystals amethyst clusters singing bowls and sage bundles in a metaphysical shop"),
    ("witch_shop", "crystal_shops", "crystal shop with gemstone display cases amethyst geodes rose quartz towers selenite wands fluorite", "gemstone display cases with amethyst geodes rose quartz towers and crystal wands"),
    ("witch_shop", "herb_shops", "herbal apothecary with dried herb bundles glass jars mortar pestle tincture bottles botanical prints", "dried herb bundles in glass jars with mortar pestle and tincture bottles"),

    # ── Salem: Psychic & Tarot (5) ──
    ("psychic", "tarot_readings", "tarot reading parlor with tarot cards spread on velvet cloth crystal ball candles incense dark curtains", "tarot cards spread on velvet cloth with crystal ball and candles"),
    ("psychic", "psychic_readings", "psychic reading room with crystal ball palmistry chart spirit board candles velvet drapes third eye", "a crystal ball on a draped table with palmistry chart and spirit board"),
    ("psychic", "palm_readings", "palm reading table with palmistry hand model chart magnifying glass candles curtains cushions", "a palmistry hand model with chart and magnifying glass on a draped table"),
    ("psychic", "seances", "seance room with round table candelabra spirit board ouija planchette dark room heavy curtains", "a seance table with candelabra spirit board ouija and planchette in candlelight"),
    ("psychic", "spiritual_healers", "spiritual healing room with crystals on body chakra stones reiki table incense singing bowl aura", "chakra healing stones and reiki crystals arranged with singing bowl and incense"),

    # ── Salem: Ghost Tours (4) ──
    ("ghost_tour", "walking_tours", "ghost tour group with lantern guide on cobblestone street old buildings fog night cemetery gate", "a lantern on cobblestone street with fog and old Salem buildings at night"),
    ("ghost_tour", "haunted_tours", "haunted tour with flashlight group outside creepy building tombstones full moon bare trees ghosts", "a creepy building exterior at night with tombstones full moon and bare trees"),
    ("ghost_tour", "night_tours", "night tour with torch-lit path through cemetery iron gates gaslight lanterns colonial buildings fog", "a torch-lit cemetery path with iron gates and colonial buildings in fog"),
    ("ghost_tour", "historical_tours", "historical walking tour with colonial guide lantern old courthouse stocks pillory Salem streets", "colonial Salem streets with old courthouse stocks pillory and gaslight lanterns"),

    # ── Salem: Haunted Attractions (4) ──
    ("haunted_attraction", "haunted_houses", "haunted house with boarded windows creaky door cobwebs fog machine strobe lights bloody handprints", "a haunted house facade with boarded windows cobwebs fog and creaky door"),
    ("haunted_attraction", "scare_attractions", "scare attraction entrance with ticket booth warning signs strobe lights fog chains haunted maze", "a scare attraction entrance with warning signs chains fog and strobe lights"),
    ("haunted_attraction", "wax_museums", "wax museum with lifelike figures in period costume glass display cases spotlights velvet ropes", "wax museum figures in colonial costume behind glass with spotlights"),
    ("haunted_attraction", "escape_horror", "horror escape room with bloody walls chains padlocks cage timer skull props surgical tools", "a horror escape room with chains padlocks cage and skull props"),

    # ── Salem: Historic Houses (5) ──
    ("historic_house", "colonial_houses", "colonial house with clapboard siding central chimney small windows wooden shutters picket fence plaque", "a colonial Salem house with clapboard siding central chimney and historical plaque"),
    ("historic_house", "witch_trial_houses", "witch trial era house with dark windows period furniture spinning wheel hearth iron pots candles", "a witch trial era house interior with spinning wheel hearth and iron pots"),
    ("historic_house", "maritime_houses", "sea captain house with widow walk cupola ship painting nautical instruments rope anchors telescope", "a sea captain house with widows walk cupola and nautical instruments"),
    ("historic_house", "literary_houses", "literary house with writing desk quill ink well bookshelf manuscript candle window seat portrait", "a literary house writing desk with quill ink well bookshelf and manuscript"),
    ("historic_house", "museum_houses", "house museum with period rooms roped off furniture display cases docent desk brochures guest book", "a house museum period room with roped furniture display cases and brochures"),
]

# ---------------------------------------------------------------------------
# Forge API helpers
# ---------------------------------------------------------------------------

def preview_image(path, label):
    """Scale image to 2x, add label bar at bottom, open in eog (non-blocking)."""
    preview_path = path.parent / f".preview_{path.name}"
    try:
        img = Image.open(path)
        scaled = img.resize((WIDTH * PREVIEW_SCALE, HEIGHT * PREVIEW_SCALE), Image.LANCZOS)

        # Add label bar at the bottom
        bar_h = 40
        canvas = Image.new("RGB", (scaled.width, scaled.height + bar_h), (45, 27, 78))
        canvas.paste(scaled, (0, 0))
        draw = ImageDraw.Draw(canvas)
        try:
            font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 20)
        except OSError:
            font = ImageFont.load_default()
        bbox = draw.textbbox((0, 0), label, font=font)
        tx = (canvas.width - (bbox[2] - bbox[0])) // 2
        ty = scaled.height + (bar_h - (bbox[3] - bbox[1])) // 2
        draw.text((tx, ty), label, fill="white", font=font)
        canvas.save(str(preview_path))

        subprocess.Popen(["eog", str(preview_path)],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except Exception as e:
        # Fallback — just open raw image
        subprocess.Popen(["eog", str(path)],
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def connect_forge():
    """Connect to Forge via Gradio client."""
    try:
        client = Client(FORGE_URL, verbose=False)
        print(f"✓ Connected to Forge at {FORGE_URL}")
        return client
    except Exception as e:
        print(f"✗ Forge not reachable at {FORGE_URL}: {e}")
        print("  Start it with: bash ~/AI-Studio/forge-start.sh")
        return None


def generate_icon(client, prompt_text, negative, seed, output_path):
    """Generate a single icon via Forge Gradio /txt2img endpoint."""
    try:
        result = client.predict(
            None,                              # parameter_47 (task id)
            prompt_text,                       # Prompt
            negative,                          # Negative prompt
            [],                                # Styles
            1,                                 # Batch count
            1,                                 # Batch size
            CFG_SCALE,                         # CFG Scale
            3.5,                               # Distilled CFG Scale
            HEIGHT,                            # Height
            WIDTH,                             # Width
            False,                             # Hires. fix
            0.7,                               # Denoising strength
            2.0,                               # Upscale by
            "Latent",                          # Upscaler
            0,                                 # Hires steps
            0,                                 # Resize width to
            0,                                 # Resize height to
            "Use same checkpoint",             # Hires Checkpoint
            ["Use same choices"],              # Hires VAE
            "Use same sampler",                # Hires sampling method
            "Use same scheduler",              # Hires schedule type
            "",                                # Hires prompt
            "",                                # Hires negative prompt
            7.0,                               # Hires CFG Scale
            3.5,                               # Hires Distilled CFG Scale
            None,                              # Override settings
            None,                              # Script
            STEPS,                             # Sampling steps
            SAMPLER,                           # Sampling method
            SCHEDULE_TYPE,                     # Schedule type
            False,                             # Refiner
            CHECKPOINT,                        # Checkpoint
            0.8,                               # Switch at
            seed,                              # Seed
            api_name="/txt2img"
        )
        # result is a tuple: (gallery_list, info_text, html1, html2)
        # gallery_list is a list of dicts: [{"image": "/path/to/img.png", "caption": ...}, ...]
        gallery = result[0]
        if not gallery:
            print(f"  ✗ No images returned")
            return False

        img_entry = gallery[0]
        src_path = img_entry.get("image", "") if isinstance(img_entry, dict) else str(img_entry)

        if not src_path or not os.path.exists(src_path):
            print(f"  ✗ Image path not found: {src_path}")
            return False

        shutil.copy2(src_path, str(output_path))
        return True

    except Exception as e:
        print(f"  ✗ Error: {e}")
        return False


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    # Check for --dry-run flag
    dry_run = "--dry-run" in sys.argv

    if dry_run:
        print("=" * 60)
        print("DRY RUN — listing all icons to generate")
        print("=" * 60)
        for i, (cat, slug, activity, obj_desc) in enumerate(POI_ICONS, 1):
            print(f"\n[{i:3d}/{len(POI_ICONS)}] {cat}/{slug}")
            for vname, vtemplate in VARIANTS:
                prompt = vtemplate.format(object_desc=obj_desc)
                print(f"  {vname:12s}: {prompt}")
        total_images = len(POI_ICONS) * len(VARIANTS)
        print(f"\nTotal: {len(POI_ICONS)} icons × {len(VARIANTS)} variants = {total_images} images")
        return

    # Connect to Forge
    client = connect_forge()
    if not client:
        sys.exit(1)

    # Create output directory
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    total = len(POI_ICONS)
    num_variants = len(VARIANTS)
    total_images = total * num_variants
    generated = 0
    skipped = 0
    failed = 0
    start_time = time.time()

    print(f"\n{'=' * 60}")
    print(f"Generating {total} icons × {num_variants} variants = {total_images} images")
    print(f"  Variants: {', '.join(v[0] for v in VARIANTS)}")
    print(f"  Output: {OUTPUT_DIR}")
    print(f"{'=' * 60}\n")

    for i, (category, slug, activity, obj_desc) in enumerate(POI_ICONS, 1):
        # Create category subdirectory
        cat_dir = OUTPUT_DIR / category
        cat_dir.mkdir(exist_ok=True)

        for vname, vtemplate in VARIANTS:
            filename = f"{slug}_{vname}.png"
            output_path = cat_dir / filename

            # Skip if already exists
            if output_path.exists():
                skipped += 1
                continue

            # Build prompt — all variants are object-focused now
            prompt = vtemplate.format(object_desc=obj_desc)

            seed = random.randint(1, 2**32 - 1)
            elapsed = time.time() - start_time
            rate = generated / elapsed if elapsed > 0 and generated > 0 else 0
            remaining = (total_images - generated - skipped) / rate if rate > 0 else 0

            print(f"[{i:3d}/{total}] {category}/{filename}  "
                  f"({generated + skipped}/{total_images})  "
                  f"~{remaining / 60:.0f}m remaining")

            if generate_icon(client, prompt, NEGATIVE_PROMPT, seed, output_path):
                generated += 1
                preview_image(output_path, f"{category}/{slug} [{vname}]")
            else:
                failed += 1
                time.sleep(2)

    elapsed = time.time() - start_time
    print(f"\n{'=' * 60}")
    print(f"DONE in {elapsed / 60:.1f} minutes")
    print(f"  Generated: {generated}")
    print(f"  Skipped (existing): {skipped}")
    print(f"  Failed: {failed}")
    print(f"  Output: {OUTPUT_DIR}")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
