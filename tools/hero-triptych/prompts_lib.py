"""
Per-POI triptych prompt generator.

Given a POI record (id, name, category, subcategory, ...), returns three prompt
strings — one per panel:
  - cat_scene  — Katrina cat-POV of the business interior
  - exterior   — painterly exterior with a small cat somewhere
  - monster    — a friendly cartoon monster "working there" with a small cat

Resolution priority:
  1. FAMOUS_OVERRIDES — exact-phrase match on landmark POIs (tribute sites).
  2. KEYWORD_SCENES — substring match on common business-type keywords.
  3. SUBCATEGORY_SCENES — salem_pois.subcategory lookup.
  4. CATEGORY_DEFAULTS — single per-category fallback.
"""

from __future__ import annotations
import zlib


def _stable_idx(key: str, n: int) -> int:
    return zlib.crc32(key.encode("utf-8")) % n


KATRINA = (
    "a single long-haired grey and white cat with big round yellow-green eyes, "
    "pink nose, white face blaze, white chest, white paws, fluffy fur, tuxedo "
    "markings, curious intelligent expression"
)


STYLE = (
    "painterly cartoon storybook illustration, cozy Salem colonial New England "
    "aesthetic, warm amber candlelight, dark teal and plum ambient background, "
    "muted vintage palette, subtle gold accents, soft brush strokes, cinematic "
    "composition. "
    "CRITICAL: every wall, window, awning, door, banner, and storefront surface "
    "is COMPLETELY BLANK with NO painted writing, NO business names, NO shop "
    "names, NO signs of any kind, NO text, NO letters, NO words, NO typography, "
    "NO lettering, NO menu boards, NO scribbles, NO gibberish writing; all "
    "sign-shaped surfaces are solid plain color or pure decorative ornament only"
)

NEG = (
    "photorealistic, photograph, realistic, scary, gore, blood, horror, "
    "demon, nudity, naked, sexual, violence, weapon, skull face, "
    "low quality, deformed, ugly, noisy, grainy, oversaturated, "
    "text, watermark, signature, logo, title, caption, words, letters, "
    "readable text, business name, store name, shop name, lettering, typography, "
    "sign with text, sign with words, storefront sign, storefront signage, "
    "shop sign, hanging sign with text, gold letters, painted letters, "
    "painted business name, painted shop name, painted lettering on wall, "
    "menu board, chalkboard with words, awning with text, awning with writing, "
    "marquee with text, neon sign with text, "
    "gibberish text, garbled letters, fake writing, fake text, illegible writing, "
    "scribbled text, random letters, blurred text, decorative lettering, "
    "labels, street signs, address numbers, house numbers, "
    "multiple cats fighting"
)


def _kat(scene: str) -> str:
    return f"{KATRINA} in the foreground observing {scene}, low cat's-eye perspective"


def _entry(cat_scene_body: str, exterior: str, monster: str) -> dict[str, str]:
    return {
        "cat_scene": _kat(cat_scene_body),
        "exterior": exterior,
        "monster": monster,
    }


# KEYWORD_SCENES — first match on poi.name substring (case-insensitive) wins.
# Order matters: longer/more-specific keywords first. Text-inviting features
# scrubbed (no awnings, marquees, hanging signs, painted storefronts).
KEYWORD_SCENES: list[tuple[str, dict[str, str]]] = [
    # ── TOURS (checked first so "Candlelit Ghostly Walking Tours" matches
    # "walking tour" before it hits "candle" further down) ───────────────────
    ("ghost tour", _entry(
        "a cozy Salem ghost-tour outfitter office, abstract maps on walls, brass lanterns on hooks, folded brochures, costume top hats on pegs, walking sticks",
        "exterior of a Salem ghost-tour company office, a dark brick storefront with a brass lantern hanging, a small black cat on the step",
        "interior of a Salem ghost-tour office, a friendly cartoon ghost tour leader in a Victorian coat gesturing at a rack of walking sticks and brass lanterns, a small black cat on the counter",
    )),
    ("walking tour", _entry(
        "a cozy Salem tour office interior, abstract maps on walls, brass lanterns, folded brochures, walking sticks, a brass bell",
        "exterior of a Salem tour company office, a brick storefront with a hanging brass lantern, a small orange cat on the step",
        "interior of a Salem tour office, a cheerful cartoon witch tour guide holding a brass lantern and pointing at a big rolled parchment map with abstract coastlines, a small calico cat on the map",
    )),
    ("ghostly", _entry(
        "a cozy Salem ghost-tour outfitter office, abstract maps on walls, brass lanterns on hooks, folded brochures, costume top hats on pegs, walking sticks",
        "exterior of a Salem ghost-tour company office, a dark brick storefront with a brass lantern hanging, a small black cat on the step",
        "interior of a Salem ghost-tour office, a friendly cartoon ghost tour leader in a Victorian coat gesturing at a rack of walking sticks and brass lanterns, a small black cat on the counter",
    )),
    ("haunted", _entry(
        "a cozy Salem haunted attraction lobby interior, flickering lanterns, carved pumpkins, cobwebs in corners, antique wooden chairs, old tapestries",
        "exterior of a Salem haunted attraction, a dark wooden facade with a heavy iron-studded door, glowing pumpkins on the step, a small black cat on a wooden crate",
        "interior of a Salem haunted attraction, a friendly cartoon ghost host in a long Victorian coat welcoming visitors with a brass lantern, a small black cat peering from behind a curtain",
    )),

    # ── FOOD ─────────────────────────────────────────────────────────────────
    ("ice cream", _entry(
        "a cozy Salem ice cream parlor interior, tubs of colorful ice cream behind glass, waffle cones stacked, sprinkles, chrome scoops, red vinyl stools at a chrome counter, pastel pendant lights",
        "exterior of a classic Salem ice cream parlor with pastel cornice trim and big front windows showing colorful tubs, a hand-cranked ice cream barrel outside, a small white cat sitting by the door",
        "interior of a Salem ice cream parlor, a friendly cartoon Frankenstein-style scooper in a pastel apron scooping ice cream into a waffle cone, a small white cat watching eagerly from a chrome counter",
    )),
    ("pizza", _entry(
        "a Salem pizzeria interior, wood-fired oven glowing with flames, dough being tossed, pizza peels leaned against a counter, a tomato-sauce-stained wooden counter, hanging garlic braids, warm candlelit tables",
        "exterior of a classic Salem pizzeria with red brick walls, a warm glowing front window showing a pizza oven, an iron lantern by the door, a small orange cat on a wooden crate out front",
        "interior of a Salem pizzeria, a cheerful cartoon goblin pizza cook in a white apron sliding a steaming pizza from a wood-fired oven with a long peel, a small orange cat on a flour-dusted counter",
    )),
    ("sushi", _entry(
        "a Salem sushi bar interior, bamboo mats, chopsticks in holders, a glass case of sushi rolls and sashimi, a warm wooden counter, soft pendant lights, small decorative bonsai",
        "exterior of a Salem sushi restaurant with a dark wood storefront, a simple arched doorway, paper lanterns glowing softly, a small calico cat sitting on the doorstep",
        "interior of a Salem sushi bar, a friendly cartoon mummy sushi chef in a white headband slicing a sashimi roll with a long knife at a bamboo counter, a small grey cat watching eagerly from a barrel",
    )),
    ("bakery", _entry(
        "a cozy Salem bakery interior, trays of frosted pastries in a glass case, golden loaves of bread cooling on wooden racks, rolling pins and bowls of flour, a dusting of powdered sugar in the air",
        "exterior of a Salem bakery, a brick facade with tall front windows full of bread loaves and cakes, warm amber glow from inside, a small grey cat on a flour-dusted crate by the door",
        "interior of a Salem bakery, a cheerful cartoon Frankenstein-style baker in a white apron sliding a tray of frosted pastries onto a counter, a small grey cat watching from a flour-dusted stool",
    )),
    ("donut", _entry(
        "a Salem donut shop interior, a display case of frosted donuts with rainbow sprinkles, coffee pots steaming, red vinyl stools at a counter, warm pendant lights",
        "exterior of a Salem donut shop with a hot pink entry overhang over big windows showing a donut case, a small orange cat on a red-painted stool by the door",
        "interior of a Salem donut shop, a cheerful cartoon goblin donut maker in a white paper hat glazing a rack of donuts, a small orange cat on a red vinyl stool",
    )),
    ("bagel", _entry(
        "a Salem bagel shop interior, baskets of shiny bagels, a deli counter with cream cheese and lox, a bread slicer, chrome toasters, warm overhead lights",
        "exterior of a Salem bagel shop, a narrow brick storefront with a cornice trim, a big front window full of bagel baskets, a small tabby cat on a barrel by the door",
        "interior of a Salem bagel shop, a cheerful cartoon mummy bagel maker in an apron topping a bagel with cream cheese at a deli counter, a small tabby cat on a stool",
    )),
    ("tavern", _entry(
        "a Salem tavern interior, dark wooden beams, pewter mugs of ale, candlelit tables, a crackling hearth, a long wooden bar, cozy benches perfect for a cat to nap",
        "exterior of a Salem tavern, a heavy timbered colonial facade with leaded glass windows glowing warm amber, an iron hanging lantern, a small black cat on the step",
        "interior of a Salem tavern, a cartoon gruff-but-friendly pirate tavernkeeper with an eye patch wiping a pewter mug at a wooden bar, a small black cat perched on a barrel",
    )),
    ("pub", _entry(
        "a cozy Salem pub interior, dark wood walls, rows of pint glasses hanging above the bar, warm pendant lamps, leather benches, dart board on the wall",
        "exterior of a Salem pub with weathered clapboard walls, small front windows glowing amber, a hanging iron lantern, a small tabby cat on the step",
        "interior of a Salem pub, a friendly cartoon werewolf bartender in a vest pulling a pint at a wooden bar, a small tabby cat on a leather stool",
    )),
    ("brew", _entry(
        "a Salem brewery interior, copper brewing tanks glowing, wooden barrels stacked, pint glasses in racks, hops and grain sacks, warm pendant lights, exposed brick walls",
        "exterior of a Salem brewery in a converted red brick warehouse, tall arched windows, industrial rollup door, warm glow from inside, a small grey cat on a wooden barrel",
        "interior of a Salem brewery, a friendly cartoon Frankenstein-style brewer in a leather apron stirring a copper tank with a long paddle, a small grey cat on a wooden barrel",
    )),
    ("wine bar", _entry(
        "a Salem wine bar interior, dark wood, velvet chairs, a backlit display of wine bottles, a marble counter, candles, cheese board on a table",
        "exterior of a Salem wine bar, a narrow brick storefront with a small iron-railed entrance, warm candle glow in the window, a small tortoiseshell cat on the step",
        "interior of a Salem wine bar, a friendly cartoon vampire sommelier in a dark waistcoat tipping a bottle to pour a glass, a small black cat on a velvet stool",
    )),
    ("cocktail", _entry(
        "a Salem cocktail lounge interior, a marble bar lined with crystal glasses, shelves of spirits glowing, velvet booths, low amber lighting, a bowl of garnish cherries",
        "exterior of a Salem cocktail lounge, a polished black storefront with gilded trim, a single iron lantern glowing, a small black cat on the doorstep",
        "interior of a Salem cocktail lounge, a friendly cartoon vampire mixologist in a vest shaking a silver cocktail shaker with flair, a small black cat perched on a bar stool",
    )),
    ("cafe", _entry(
        "a Salem cafe interior, espresso machine steaming, rows of ceramic coffee mugs, a pastry display case, a cozy leather armchair for a cat",
        "exterior of a Salem cafe with a brick facade and a small cornice trim, big windows glowing amber, potted plants by the door, a small calico cat on a wooden chair out front",
        "interior of a Salem cafe, a friendly cartoon mummy barista in a knit beanie pulling an espresso shot at a steaming machine, a small calico cat asleep on a bag of coffee beans",
    )),
    ("coffee", _entry(
        "a Salem coffee shop interior, espresso machine steaming, rows of ceramic mugs, a pastry case, cozy leather armchairs, warm amber pendant lights, wooden shelves of coffee bags",
        "exterior of a Salem coffee shop, a brick facade with a small cornice trim, big windows glowing amber, potted plants by the door, a small calico cat on a chair out front",
        "interior of a Salem coffee shop, a friendly cartoon mummy barista pulling an espresso shot at a chrome machine, a small calico cat asleep on a burlap coffee bag",
    )),
    ("chinese", _entry(
        "a Salem Chinese restaurant interior, steaming woks, dumplings on a bamboo steamer, red paper lanterns, teapots, chopsticks, a lazy Susan on a round table",
        "exterior of a Salem Chinese restaurant with red painted trim, a pair of paper lanterns glowing, big windows, a small orange cat on a wooden bench out front",
        "interior of a Salem Chinese restaurant, a cheerful cartoon goblin wok chef in a white jacket tossing noodles in a flaming wok, a small orange cat on a bamboo stool",
    )),
    ("thai", _entry(
        "a Salem Thai restaurant interior, curry pots simmering, pad thai noodles in woks, Thai basil in bowls, coconut milk cans, a small Buddha statue, carved wooden screens",
        "exterior of a Salem Thai restaurant, a narrow storefront with teak-wood trim, a small banana tree in a pot by the door, a small tabby cat on the steps",
        "interior of a Salem Thai restaurant, a friendly cartoon mummy Thai chef in a red apron stirring a curry pot, a small tabby cat perched on a bamboo shelf",
    )),
    ("mexican", _entry(
        "a Salem Mexican restaurant interior, sizzling fajitas on a plate, bowls of salsa and guacamole, painted talavera tiles, woven blankets on chair backs",
        "exterior of a Salem Mexican restaurant with bright decorative trim, a small terracotta overhang, strings of pepper-shaped lights, a small ginger cat on the step",
        "interior of a Salem Mexican restaurant, a cheerful cartoon goblin taqueria cook in a white apron flipping a tortilla on a comal, a small ginger cat on a hand-painted stool",
    )),
    ("indian", _entry(
        "a Salem Indian restaurant interior, a tandoori oven glowing, curry pots, naan bread, ornate brass plates, spice jars lined on a shelf, hanging silk fabric, incense",
        "exterior of a Salem Indian restaurant, a narrow storefront with ornate brass trim, a hanging brass lantern glowing, a small tabby cat on the step",
        "interior of a Salem Indian restaurant, a friendly cartoon mummy tandoori chef in a red apron pulling naan from the tandoor oven, a small tabby cat on a woven stool",
    )),
    ("italian", _entry(
        "a Salem Italian restaurant interior, fresh pasta hanging to dry, bowls of tomato sauce, a wood-fired oven glowing, hanging garlic braids, candlelit tables with checkered cloths",
        "exterior of a Salem Italian restaurant, red brick walls, a green-and-white striped cornice trim, warm front windows showing pasta displays, a small tortoiseshell cat on the step",
        "interior of a Salem Italian restaurant, a cheerful cartoon goblin nonno chef in a white apron twirling fresh pasta onto a plate, a small tortoiseshell cat on a flour-dusted stool",
    )),
    ("seafood", _entry(
        "a Salem seafood restaurant interior, a raw bar of oysters on crushed ice, fishing nets on the walls, brass portholes, lobster tanks, candle-lit wooden tables",
        "exterior of a Salem seafood restaurant, a weathered grey clapboard facade, rope-wrapped pilings by the door, an iron anchor by the entrance, a small black cat on a lobster trap",
        "interior of a Salem seafood restaurant, a friendly cartoon pirate cook in an apron cracking a lobster at a wooden counter, a small black cat waiting hopefully under the counter",
    )),
    ("fish", _entry(
        "a Salem seafood shop interior, ice-packed fish on display, lobster tanks bubbling, cod fillets on butcher paper, a scale, fishing nets on walls",
        "exterior of a Salem seafood shop, a weathered grey clapboard storefront, a blue-painted door, a lobster trap by the step, a small tabby cat on the trap",
        "interior of a Salem seafood shop, a friendly cartoon sea-captain fishmonger with a yellow rubber apron weighing a cod on brass scales, a small tabby cat watching the scale",
    )),
    ("burger", _entry(
        "a Salem burger joint interior, a griddle with sizzling patties, towers of sesame buns, red ketchup bottles, red vinyl booths, chrome napkin dispensers, warm neon glow",
        "exterior of a Salem burger joint with a red-and-white painted facade, a big front window with a glowing grill visible, a small ginger cat on a wooden bench out front",
        "interior of a Salem burger joint, a cheerful cartoon goblin cook in a paper hat flipping a sizzling patty with a spatula, a small ginger cat on a red vinyl stool",
    )),
    ("bbq", _entry(
        "a Salem barbecue joint interior, a smoker glowing, racks of ribs hanging, cornbread in baskets, mason jars of sauce, worn wooden tables, neon glow",
        "exterior of a Salem barbecue joint, a weathered wood-plank facade, smoke curling from a chimney, an iron door lantern, a small orange cat on a wooden crate",
        "interior of a Salem barbecue joint, a friendly cartoon werewolf pitmaster in an apron pulling ribs off a smoker rack, a small orange cat on a whiskey barrel",
    )),
    ("deli", _entry(
        "a Salem deli interior, a glass case of meats and cheeses, a bread slicer, stacks of rye bread, jars of pickles, small wooden stools at a counter",
        "exterior of a Salem deli, a narrow brick storefront with a cornice trim, a big front window showing the deli case, a small tabby cat on a wooden stool",
        "interior of a Salem deli, a friendly cartoon mummy deli worker in a white cap building a tall sandwich on a cutting board, a small tabby cat on a nearby stool",
    )),
    ("sandwich", _entry(
        "a Salem sandwich shop interior, a glass case of meats and cheeses, a bread slicer, red vinyl stools at a counter, warm pendant lights, open shelves with chips",
        "exterior of a Salem sandwich shop with a classic brick storefront and a cornice trim, a small ginger cat waiting at the front door",
        "interior of a Salem sandwich shop, a friendly cartoon goblin sandwich maker in a white apron stacking meat on a bread slicer, a small ginger cat watching from the counter",
    )),
    ("diner", _entry(
        "a classic Salem diner interior, red vinyl stools at a chrome counter, a griddle with sizzling bacon, stacks of ceramic plates, a tall milkshake, cozy warm booth cushions",
        "exterior of a classic Salem diner with chrome trim, big curved windows glowing, a blue-painted doorway, a small ginger cat waiting at the front door",
        "interior of a classic Salem diner, a cheerful cartoon goblin cook in a paper hat flipping pancakes on a sizzling grill, a small orange tabby peeking from behind the counter",
    )),
    ("wings", _entry(
        "a Salem wings joint interior, fryers bubbling with wings, jars of hot sauce, beer taps, TV screens, red vinyl booths",
        "exterior of a Salem wings joint, a sports-bar brick storefront with a cornice trim, big windows with glowing screens inside, a small orange cat on a barrel",
        "interior of a Salem wings joint, a cheerful cartoon werewolf cook in an apron tossing wings in a sauce bowl, a small orange cat on a stool",
    )),
    ("juice", _entry(
        "a Salem juice bar interior, stacks of fresh fruit in baskets, blenders whirring, mason jars of colorful juice, green potted plants, bright white tile counter",
        "exterior of a Salem juice bar, a bright brick storefront with fruit decals, a small calico cat on a chair out front",
        "interior of a Salem juice bar, a cheerful cartoon witch juicer in a bright apron pouring a green smoothie into a mason jar, a small calico cat watching from a fruit basket",
    )),
    ("smoothie", _entry(
        "a Salem smoothie shop interior, stacked baskets of fresh fruit, blenders whirring, tall jars of colorful smoothies, bright counters",
        "exterior of a Salem smoothie shop, a bright brick storefront, a small tabby cat on a wooden crate",
        "interior of a Salem smoothie shop, a cheerful cartoon witch blender operator pouring a smoothie into a tall glass, a small tabby cat on the counter",
    )),

    # ── RETAIL SPECIFICS ─────────────────────────────────────────────────────
    ("bookstore", _entry(
        "a cozy Salem bookshop interior, towering shelves of leather-bound books, a reading nook with a leather armchair, a brass reading lamp, warm amber glow",
        "exterior of a Salem bookshop, a brick storefront with a bay window full of stacked books, warm amber glow, a small tabby cat in the window",
        "interior of a Salem bookshop, a friendly cartoon mummy bookseller in a cardigan reaching for a leather-bound book on a high shelf, a small tabby cat asleep on a reading chair",
    )),
    ("book", _entry(
        "a cozy Salem bookshop interior, towering shelves of leather-bound books, a reading nook with a leather armchair, a brass reading lamp, warm amber glow",
        "exterior of a Salem bookshop, a brick storefront with a bay window full of stacked books, warm amber glow, a small tabby cat in the window",
        "interior of a Salem bookshop, a friendly cartoon mummy bookseller in a cardigan reaching for a leather-bound book on a high shelf, a small tabby cat asleep on a reading chair",
    )),
    ("flower", _entry(
        "a Salem flower shop interior, bouquets of colorful flowers in vases, ribbons, flowerpots, a watering can, warm skylight glow, green potted plants hanging from above",
        "exterior of a Salem flower shop, a brick storefront with big windows full of bouquets, a small tricolor cat peeking out from between flower pots on the step",
        "interior of a Salem flower shop, a cheerful cartoon witch florist in an apron tying a bouquet with a ribbon, a small calico cat batting at a loose ribbon",
    )),
    ("jewelry", _entry(
        "a Salem jewelry shop interior, glass display cases of rings and necklaces sparkling, velvet trays, a magnifying loupe, soft spotlights, polished wood",
        "exterior of a Salem jewelry shop, a narrow gilded brick storefront with a single display window glowing softly, a small black cat on a velvet cushion in the window",
        "interior of a Salem jewelry shop, a cartoon vampire jeweler in a waistcoat holding up a gemstone with tweezers, a small black cat on the velvet counter",
    )),
    ("antique", _entry(
        "a cozy Salem antique shop interior, vintage furniture, tarnished silver, grandfather clocks, old paintings with blank canvases, brass candlesticks, warm musty air",
        "exterior of a Salem antique shop, a weathered wooden storefront, a hanging iron lantern, a wooden barrel by the door with antique wares, a small grey cat on the barrel",
        "interior of a Salem antique shop, a friendly cartoon ghost antique dealer in Victorian clothes polishing a brass candlestick, a small grey cat on a dusty velvet chair",
    )),
    ("candle", _entry(
        "a cozy Salem candle shop interior, shelves of candles in brass holders, small flames flickering, glass jars, cozy velvet chairs, warm amber glow",
        "exterior of a Salem candle shop, a narrow brick storefront with a bay window full of glowing candles, a small tabby cat in the window",
        "interior of a Salem candle shop, a cheerful cartoon witch candle-maker pouring wax into a brass mold, a small tabby cat watching from a shelf of candles",
    )),
    ("tattoo", _entry(
        "a Salem tattoo parlor interior, a leather chair, tattoo machines on a tray, ink bottles in every color, flash art on the walls, warm lamp",
        "exterior of a Salem tattoo parlor, a black-painted brick storefront with decorative ironwork, a small black cat in the window",
        "interior of a Salem tattoo parlor, a friendly cartoon vampire tattoo artist in a leather apron holding a tattoo machine, a small black cat asleep on the leather chair",
    )),
    ("thrift", _entry(
        "a cozy Salem thrift shop interior, racks of vintage clothes, stacks of used books, old records in crates, quirky lamps, retro furniture",
        "exterior of a Salem thrift shop, a weathered clapboard storefront, clothes on a rack by the window, a small tabby cat on a wooden crate",
        "interior of a Salem thrift shop, a friendly cartoon ghost thrift worker in a vintage dress sorting through a box of records, a small tabby cat on a stack of old books",
    )),
    ("pawn", _entry(
        "a Salem pawn shop interior, glass cases of watches and jewelry, guitars hanging on the wall, electronics on shelves, a bell at the counter",
        "exterior of a Salem pawn shop, a dark painted brick storefront with barred windows showing watches and instruments, a small black cat on a wooden stool",
        "interior of a Salem pawn shop, a cartoon gruff-but-friendly vampire pawnbroker in a waistcoat examining a pocket watch with a loupe, a small black cat on a guitar case",
    )),
    ("vape", _entry(
        "a Salem vape shop interior, glass display cases of vaping devices, jars of flavored liquids, cloud puffs, cozy lounge chairs",
        "exterior of a Salem vape shop, a narrow black brick storefront with softly glowing purple accent lights, a small black cat on the step",
        "interior of a Salem vape shop, a friendly cartoon ghost shopkeeper in a hoodie arranging vape devices in a display case, a small black cat on a lounge chair",
    )),
    ("smoke", _entry(
        "a Salem smoke shop interior, glass pipes, tobacco tins, rolling papers, a wooden counter, cozy lounge seats",
        "exterior of a Salem smoke shop, a narrow brick storefront with barred windows, a small grey cat on a stool out front",
        "interior of a Salem smoke shop, a cartoon gruff-but-friendly vampire shopkeeper arranging glass pipes in a display case, a small grey cat on the counter",
    )),
    ("wine", _entry(
        "a Salem wine shop interior, wooden racks of wine bottles, a tasting counter, barrel decorations, cork displays, warm pendant lights",
        "exterior of a Salem wine shop, a narrow brick storefront with a single display window of wine bottles, a small tortoiseshell cat on a wooden crate",
        "interior of a Salem wine shop, a friendly cartoon vampire sommelier in a waistcoat holding up a wine bottle, a small tortoiseshell cat on a wooden wine barrel",
    )),
    ("liquor", _entry(
        "a cozy Salem liquor store interior, tall shelves of bottles glowing, wooden barrels, a warm counter with a register, cardboard boxes for a cat to nap in",
        "exterior of a cozy Salem liquor store, a red brick storefront with barred windows, a carved wooden whiskey barrel emblem over the door, a small black cat on a wine crate",
        "interior of a cozy Salem liquor store, a cartoon gruff-but-friendly pirate shopkeeper with an eye patch stocking whiskey bottles on a shelf, a small black-and-white cat on a wooden wine barrel",
    )),
    ("cheese", _entry(
        "a Salem cheese shop interior, wheels of cheese on wooden boards, a cheese display case, loaves of bread, cheese knives, burlap sacks",
        "exterior of a Salem cheese shop, a narrow brick storefront with a barrel by the door, a small tabby cat on the barrel",
        "interior of a Salem cheese shop, a friendly cartoon mummy cheesemonger in an apron slicing a wedge of cheese on a wooden board, a small tabby cat watching from a crate",
    )),
    ("furniture", _entry(
        "a Salem furniture showroom interior, sofas, tables, lamps, bedroom sets, decorative pillows, a warm rug perfect for a cat to knead",
        "exterior of a Salem furniture showroom, a wide brick storefront with big windows showing sofas and tables, a small calico cat on a wooden bench out front",
        "interior of a Salem furniture showroom, a friendly cartoon mummy salesperson in a vest gesturing at a plush sofa, a small calico cat curled up on the sofa",
    )),
    ("hardware", _entry(
        "a Salem hardware store interior, bins of nails and screws, power tools on a wall, paint cans stacked, lumber, tool belts, a brass bell at the counter",
        "exterior of a Salem hardware store, a wide brick storefront with a big front window showing tools, a wooden ladder by the door, a small tabby cat on the ladder step",
        "interior of a Salem hardware store, a friendly cartoon Frankenstein-style hardware clerk in a tool belt demonstrating a power drill, a small tabby cat on a paint-can stack",
    )),
    ("tailor", _entry(
        "a Salem tailor shop interior, sewing machines, fabric bolts on shelves, measuring tapes, dress forms, thread spools, scissors on a cutting table",
        "exterior of a Salem tailor shop, a narrow brick storefront with a display window showing a dress form, a small tabby cat on the step",
        "interior of a Salem tailor shop, a friendly cartoon mummy tailor in a vest measuring fabric at a cutting table with a long tape, a small tabby cat on a stool",
    )),
    ("print", _entry(
        "a Salem print shop interior, a vintage printing press, stacks of paper, ink rollers, cutting boards, trays of metal type",
        "exterior of a Salem print shop, a brick storefront with a big window showing a press, a small grey cat on a wooden crate out front",
        "interior of a Salem print shop, a friendly cartoon ghost printer in a leather apron feeding paper into a vintage press, a small grey cat on a paper stack",
    )),
    ("toy", _entry(
        "a cozy Salem toy shop interior, shelves of wooden toys, stuffed animals, a rocking horse, a dollhouse, colorful building blocks",
        "exterior of a Salem toy shop, a cheery brick storefront with a bay window full of toys, a small calico cat on a toy box out front",
        "interior of a Salem toy shop, a cheerful cartoon witch toymaker in an apron painting a wooden toy, a small calico cat batting at a yarn ball",
    )),
    ("gift", _entry(
        "a Salem gift shop interior, shelves of wrapped boxes, ribbons, seasonal decorations, warm pendant lights",
        "exterior of a Salem gift shop, a brick storefront with a small cornice trim, a bay window full of gift boxes, a small tabby cat on a wooden crate",
        "interior of a Salem gift shop, a cheerful cartoon witch shopkeeper in an apron tying a ribbon on a gift box, a small tabby cat batting at loose ribbon",
    )),
    ("souvenir", _entry(
        "a Salem souvenir shop interior, shelves of witch magnets, snow globes, shirts on racks, postcards, mugs with witch outlines",
        "exterior of a Salem souvenir shop, a painted witch-themed brick storefront with abstract imagery, a small black cat in the window",
        "interior of a Salem souvenir shop, a cheerful cartoon witch shopkeeper stocking shelves of witch-themed merchandise, a small black cat on a witch-hat display",
    )),
    ("boutique", _entry(
        "a cozy Salem boutique interior, dress racks, a velvet settee, a full-length mirror, hat boxes, a display case of accessories",
        "exterior of a Salem boutique, a narrow brick storefront with a display window showing a dress on a mannequin, a small calico cat on a stool out front",
        "interior of a Salem boutique, a friendly cartoon mummy boutique owner in a flowy shawl arranging dresses on a rack, a small calico cat napping in a hat box",
    )),
    ("record", _entry(
        "a Salem record shop interior, crates of vinyl records, a turntable spinning, abstract posters on the walls, a warm counter",
        "exterior of a Salem record shop, a narrow brick storefront with a big window full of album covers, a small orange cat on a milk crate",
        "interior of a Salem record shop, a friendly cartoon werewolf record-store clerk in a vintage shirt flipping through a crate of vinyl, a small orange cat on a turntable box",
    )),
    ("pet", _entry(
        "a Salem pet shop interior, pet toys, food bowls, leashes, squeaky toys, fish tanks glowing, pet beds, hamster cages",
        "exterior of a Salem pet shop, a brick storefront with a big window showing colorful fish tanks and toys, a small tabby cat on the step",
        "interior of a Salem pet shop, a cheerful cartoon witch pet-shop keeper in an apron holding a fish net over a glowing aquarium, a small tabby cat watching the fish",
    )),
    ("vet", _entry(
        "a Salem veterinary clinic interior, an exam table, a stethoscope, medicine bottles, abstract paw-print charts on the wall, soft lighting",
        "exterior of a Salem veterinary clinic, a clean brick storefront with a big window, a small tabby cat on a wooden bench out front",
        "interior of a Salem veterinary clinic, a friendly cartoon mummy veterinarian in a white coat examining a cartoon puppy with a stethoscope, a small tabby cat watching from a shelf",
    )),

    # ── HEALTH ───────────────────────────────────────────────────────────────
    ("dental", _entry(
        "a cozy Salem dental office interior, a cushioned chair, a tool tray with mirrors, model teeth on the counter, soft lamp glow, x-ray light box",
        "exterior of a Salem dental office, a clean brick facade with a big window, potted plants by the door, a small tabby cat on a wooden bench",
        "interior of a Salem dental office, a kindly cartoon vampire dentist in a white coat holding a mirror-on-a-stick, a small orange cat peeking from under the chair",
    )),
    ("dentist", _entry(
        "a cozy Salem dental office interior, a cushioned chair, a tool tray with mirrors, model teeth on the counter, soft lamp glow, x-ray light box",
        "exterior of a Salem dental office, a clean brick facade with a big window, potted plants by the door, a small tabby cat on a wooden bench",
        "interior of a Salem dental office, a kindly cartoon vampire dentist in a white coat holding a mirror-on-a-stick, a small orange cat peeking from under the chair",
    )),
    ("pharmacy", _entry(
        "a cozy Salem pharmacy interior, shelves of medicine bottles, a wooden counter, a brass mortar and pestle, a scale, warm lamp",
        "exterior of a Salem pharmacy, a brick storefront with a big front window showing shelves of bottles, a small calico cat on the step",
        "interior of a Salem pharmacy, a friendly cartoon mummy pharmacist in a white coat filling a bottle from a large jar, a small calico cat on the counter",
    )),
    ("optical", _entry(
        "a cozy Salem optical shop interior, rows of eyeglass frames on display, polished wood, an eye chart with abstract symbols, soft lamp",
        "exterior of a Salem optical shop, a narrow brick storefront with a big window showing glasses frames, a small grey cat on the step",
        "interior of a Salem optical shop, a friendly cartoon ghost optician in a waistcoat holding up a pair of spectacles, a small grey cat on a frame display",
    )),
    ("optometrist", _entry(
        "a cozy Salem optical shop interior, rows of eyeglass frames on display, polished wood, an eye chart with abstract symbols, soft lamp",
        "exterior of a Salem optometrist, a narrow brick storefront with a big window showing glasses frames, a small grey cat on the step",
        "interior of a Salem optometrist's office, a friendly cartoon ghost optometrist in a white coat examining an eye chart, a small grey cat on the chair",
    )),
    ("chiropract", _entry(
        "a Salem chiropractic office interior, an adjustment table, a spine model, abstract anatomical posters, a warm lamp",
        "exterior of a Salem chiropractic office, a clean brick storefront, potted plants, a small calico cat on a bench out front",
        "interior of a Salem chiropractic office, a friendly cartoon Frankenstein-style chiropractor in a white coat holding a spine model, a small calico cat on the adjustment table",
    )),
    ("massage", _entry(
        "a cozy Salem massage room interior, a massage table with soft towels, essential oil bottles, hot stones on a warmer, candles flickering",
        "exterior of a Salem massage studio, a narrow quiet brick storefront with velvet drapes in the window, a small grey cat on a chair out front",
        "interior of a Salem massage room, a friendly cartoon ghost masseuse in a robe lighting candles around a massage table, a small grey cat curled on a soft towel",
    )),
    ("acupunct", _entry(
        "a Salem acupuncture clinic interior, thin needles in trays, a treatment bed, herbal medicine jars, abstract meridian charts",
        "exterior of a Salem acupuncture clinic, a quiet brick storefront with bamboo-accented trim, a small tabby cat on the step",
        "interior of a Salem acupuncture clinic, a friendly cartoon witch acupuncturist in a kimono holding a tray of needles, a small tabby cat curled on the treatment bed",
    )),
    ("physical therapy", _entry(
        "a Salem physical therapy clinic interior, exercise balls, resistance bands, a treadmill, parallel bars, a massage table",
        "exterior of a Salem PT clinic, a clean brick storefront with big windows, a small tabby cat on a wooden bench out front",
        "interior of a Salem PT clinic, a friendly cartoon Frankenstein-style therapist in a polo shirt demonstrating an exercise with resistance bands, a small tabby cat on a balance ball",
    )),
    ("therapy", _entry(
        "a cozy Salem therapy office interior, a comfortable couch, an armchair, soft lighting, a tissue box, a bookshelf, a small plant",
        "exterior of a Salem therapy office, a quiet brick storefront with a single small window, a small calico cat on a bench out front",
        "interior of a Salem therapy office, a kindly cartoon ghost therapist in a cardigan sitting in an armchair with a notebook, a small calico cat curled on the couch",
    )),
    ("clinic", _entry(
        "a cozy Salem clinic waiting room interior, soft lighting, leafy plants, upholstered chairs, a reception counter, warm rugs",
        "exterior of a Salem clinic, a clean brick storefront with big windows, potted plants by the entrance, a small tabby cat on the steps",
        "interior of a Salem clinic, a friendly cartoon mummy doctor in a white coat holding a wooden stethoscope, a small tabby cat on the exam table",
    )),
    ("medical", _entry(
        "a cozy Salem medical office interior, an exam table, a stethoscope, cabinets of supplies, a scale, soft lighting",
        "exterior of a Salem medical office, a clean brick storefront, potted plants, a small tabby cat on the step",
        "interior of a Salem medical office, a friendly cartoon mummy doctor in a white coat holding a wooden stethoscope, a small tabby cat on the exam table",
    )),

    # ── BEAUTY ───────────────────────────────────────────────────────────────
    ("barber", _entry(
        "a classic Salem barber shop interior, red-leather barber chairs, a striped barber pole, scissors and combs on a counter, warm pendant lights, big mirrors",
        "exterior of a Salem barber shop, a narrow brick storefront with a spinning striped barber pole, a small black cat on a wooden bench out front",
        "interior of a Salem barber shop, a friendly cartoon Frankenstein-style barber in a white smock snipping with scissors, a small black cat on a leather seat",
    )),
    ("nail", _entry(
        "a cozy Salem nail salon interior, hundreds of colorful nail polish bottles on shelves, manicure stations, UV lamps, soft pink lighting",
        "exterior of a Salem nail salon, a bright brick storefront with a small cornice trim, a small calico cat on a chair out front",
        "interior of a Salem nail salon, a cheerful cartoon witch manicurist in a pink smock painting nails at a small table, a small white cat asleep on a chair",
    )),
    ("salon", _entry(
        "a cozy Salem hair salon interior, barber chairs, mirrors, hair dryers, shelves of colorful bottles, warm pendant lights",
        "exterior of a Salem salon, a brick storefront with a big front window, a small calico cat on a bench out front",
        "interior of a Salem salon, a cheerful cartoon werewolf stylist in a smock combing out a customer's hair, a small calico cat on a barber chair",
    )),
    ("hair", _entry(
        "a cozy Salem hair studio interior, barber chairs, mirrors, hair dryers, shelves of bottles, warm pendant lights",
        "exterior of a Salem hair studio, a brick storefront with a big front window, a small calico cat on a bench out front",
        "interior of a Salem hair studio, a cheerful cartoon werewolf stylist in a smock using a curling iron, a small calico cat on a barber chair",
    )),
    ("spa", _entry(
        "a cozy Salem spa interior, a massage table, hot stones, essential oil bottles, candles, soft towels, a zen fountain, warm amber light",
        "exterior of a Salem spa, a quiet brick storefront with velvet drapes and candle glow, a small grey cat on a chair out front",
        "interior of a Salem spa, a friendly cartoon ghost masseuse in a robe lighting candles around a massage table, a small grey cat curled on a soft towel",
    )),
    ("wax", _entry(
        "a Salem waxing studio interior, a treatment bed with paper cover, jars of warm wax, soft lighting, white-tiled walls",
        "exterior of a Salem waxing studio, a pastel-brick storefront, a small tabby cat on the step",
        "interior of a Salem waxing studio, a cheerful cartoon witch esthetician in a pastel smock warming a jar of wax, a small tabby cat on a stool",
    )),
    ("lash", _entry(
        "a Salem lash studio interior, a treatment bed, trays of delicate lashes, magnifying lights, soft pink lighting",
        "exterior of a Salem lash studio, a pastel-brick storefront with a velvet drape in the window, a small calico cat on a chair out front",
        "interior of a Salem lash studio, a cheerful cartoon witch lash artist in a pastel smock applying lashes to a customer under a magnifier, a small calico cat on the pillow",
    )),
    ("beauty", _entry(
        "a cozy Salem beauty studio interior, a styling chair, mirrors, shelves of bottles, a makeup counter, warm pendant lights",
        "exterior of a Salem beauty studio, a pastel-brick storefront with a small overhang, a small calico cat on a chair out front",
        "interior of a Salem beauty studio, a cheerful cartoon witch beautician in a smock applying makeup, a small calico cat on a styling chair",
    )),

    # ── SERVICES / CONSTRUCTION ──────────────────────────────────────────────
    ("construction", _entry(
        "a cozy Salem construction company office interior, blueprints rolled on a drafting table, hard hats on hooks, a model building, lumber samples, a warm desk lamp",
        "exterior of a small Salem construction company office, a weathered clapboard facade with a small truck parked outside and a ladder leaned by the door, a small tabby cat on the truck bed",
        "interior of a Salem construction office, a friendly cartoon Frankenstein-style builder in a hard hat unrolling a blueprint on a drafting table, a small tabby cat on a stack of 2x4s",
    )),
    ("builder", _entry(
        "a cozy Salem builder's office interior, blueprints rolled on a drafting table, hard hats, model houses, lumber samples, a warm desk lamp",
        "exterior of a small Salem builder's office with clapboard walls, a truck and ladder outside, a small tabby cat on a wooden crate",
        "interior of a Salem builder's office, a friendly cartoon Frankenstein-style builder in a flannel shirt examining a blueprint, a small tabby cat on a toolbox",
    )),
    ("woodwork", _entry(
        "a Salem woodworking shop interior, stacks of fine lumber, a table saw, chisels on a pegboard, hand-carved furniture in progress, wood shavings on the floor",
        "exterior of a Salem woodworking shop, a weathered barn-board facade with a sliding door partly open showing lumber, a small tabby cat on a wood stack out front",
        "interior of a Salem woodworking shop, a friendly cartoon Frankenstein-style woodworker in an apron planing a board at a workbench, a small tabby cat on a lumber stack",
    )),
    ("carpent", _entry(
        "a Salem carpentry shop interior, a workbench with tools, stacks of lumber, a wood lathe, drawers of nails, warm lamp",
        "exterior of a Salem carpentry shop, a barn-board workshop, a ladder and sawhorses outside, a small tabby cat on a sawhorse",
        "interior of a Salem carpentry shop, a friendly cartoon Frankenstein-style carpenter in overalls hammering a board at a workbench, a small tabby cat on a stack of planks",
    )),
    ("plumb", _entry(
        "a Salem plumbing workshop interior, pipes, wrenches on pegboard, faucets, toilet fixtures, copper tubing, pipe cutters",
        "exterior of a small Salem plumbing company office, a weathered brick storefront with a truck outside and pipes leaned on a wall, a small tabby cat on the truck step",
        "interior of a Salem plumbing workshop, a friendly cartoon goblin plumber in overalls holding a giant wrench at a sink fixture, a small tabby cat on a toolbox",
    )),
    ("electric", _entry(
        "a Salem electrician workshop interior, wire spools, circuit breakers, multimeters, outlet boxes, cable strippers, a warm workbench lamp",
        "exterior of a small Salem electrical company office, a brick storefront with a truck outside and a spool of cable by the door, a small tabby cat on a wire spool",
        "interior of a Salem electrical workshop, a friendly cartoon Frankenstein-style electrician in overalls testing a circuit with a multimeter, a small tabby cat on a wire spool",
    )),
    ("hvac", _entry(
        "a Salem HVAC workshop interior, air conditioning units, ductwork, thermostats, refrigerant tanks, copper tubing, a workbench",
        "exterior of a Salem HVAC company, a warehouse facade with AC units stacked outside, a truck, a small tabby cat on a unit",
        "interior of a Salem HVAC workshop, a friendly cartoon Frankenstein-style HVAC tech in overalls holding a wrench next to an air conditioner, a small tabby cat on a duct",
    )),
    ("roofing", _entry(
        "a Salem roofing company workshop interior, stacks of shingles, tar buckets, nail guns, ladders, flashing material",
        "exterior of a small Salem roofing company, a weathered clapboard office with a truck loaded with ladders, a small tabby cat on the truck bed",
        "interior of a Salem roofing workshop, a friendly cartoon Frankenstein-style roofer in overalls holding a nail gun next to a stack of shingles, a small tabby cat on a ladder rung",
    )),
    ("landscap", _entry(
        "a Salem landscaping shed interior, lawnmowers, rakes, shovels, flower pots, bags of mulch, hedge trimmers, a potted plant sprout",
        "exterior of a Salem landscaping company, a small wooden shed with a truck and trailer, lawn equipment outside, a small tabby cat on a wheelbarrow",
        "interior of a Salem landscaping shed, a friendly cartoon goblin landscaper in overalls holding hedge trimmers, a small tabby cat in a flower pot",
    )),
    ("handyman", _entry(
        "a Salem handyman workshop interior, power drills, a hammer, a toolbox, paint cans, tape measure, nails, warm workbench lamp",
        "exterior of a Salem handyman company, a modest clapboard office with a truck and ladder outside, a small tabby cat on the truck step",
        "interior of a Salem handyman workshop, a friendly cartoon Frankenstein-style handyman in overalls holding a drill and tape measure, a small tabby cat on a toolbox",
    )),
    ("welding", _entry(
        "a Salem welding shop interior, welding torches, metal sheets, an anvil, grinding wheels, sparks flying, a leather apron hanging",
        "exterior of a Salem welding shop, a metal-clad industrial building with a roll-up door, a small black cat on a metal barrel",
        "interior of a Salem welding shop, a friendly cartoon Frankenstein-style welder in a leather apron and mask welding a steel beam with sparks, a small black cat behind a safety barrier",
    )),
    ("paint", _entry(
        "a Salem painting company office interior, paint cans stacked in every color, a workbench with brushes, drop cloths, ladders leaned in a corner",
        "exterior of a Salem painting company, a modest workshop with a paint-spattered truck outside and ladders on a rack, a small ginger cat on a paint-can stack",
        "interior of a Salem paint shop, a friendly cartoon ghost painter in white coveralls holding a paint roller next to a wall of swatches, a small ginger cat on a paint bucket",
    )),
    ("design", _entry(
        "a Salem interior design studio interior, a drafting table with fabric swatches and tile samples, a model room, shelves of design books, warm task lamps",
        "exterior of a small Salem design studio, a narrow brick storefront with a bay window showing a model room, a small calico cat on a stool out front",
        "interior of a Salem design studio, a friendly cartoon witch designer in stylish glasses holding fabric swatches, a small calico cat on a rolled carpet sample",
    )),
    ("architect", _entry(
        "a Salem architecture studio interior, a drafting table with rolled blueprints, a model building, T-squares on a wall, warm task lamps",
        "exterior of a Salem architecture studio, a narrow brick storefront with a big window showing a drafting table, a small grey cat on a wooden stool out front",
        "interior of a Salem architecture studio, a friendly cartoon mummy architect in a waistcoat hunched over a blueprint at a drafting table, a small grey cat on a model building",
    )),
    ("cleaning", _entry(
        "a Salem cleaning supply room interior, mops, brooms, spray bottles, buckets of soapy water, vacuum cleaners, feather dusters, a warm lamp",
        "exterior of a small Salem cleaning company, a brick storefront with a company truck outside, brooms leaning on a wall, a small grey cat on a mop bucket",
        "interior of a Salem cleaning supply room, a friendly cartoon ghost cleaner in a uniform holding a feather duster and spray bottle, a small grey cat on a mop bucket",
    )),
    ("laundr", _entry(
        "a cozy Salem laundromat interior, rows of washing machines spinning, dryers tumbling, folded clothes on a counter, detergent bottles, warm fluorescent light",
        "exterior of a Salem laundromat, a clean brick storefront with big windows showing the washers, a small tabby cat on a wooden bench out front",
        "interior of a Salem laundromat, a friendly cartoon Frankenstein-style attendant folding clothes at a counter, a small tabby cat in an empty laundry basket",
    )),
    ("cleaners", _entry(
        "a cozy Salem dry cleaner interior, racks of hanging clothes in plastic wrap, pressing machines, a steam iron, hangers, warm overhead lights",
        "exterior of a Salem dry cleaner, a brick storefront with a big window showing garment racks, a small grey cat on the step",
        "interior of a Salem dry cleaner, a friendly cartoon mummy dry cleaner pressing a shirt with a steaming iron, a small grey cat on a laundry counter",
    )),
    ("storage", _entry(
        "a Salem storage facility interior, a hallway of metal roll-up doors, padlocks, moving boxes stacked, hand trucks, overhead lighting",
        "exterior of a Salem storage facility, rows of orange roll-up doors under metal canopies, a small tabby cat on a hand truck",
        "interior of a Salem storage facility hallway, a friendly cartoon ghost storage manager in a uniform unlocking a roll-up door, a small tabby cat on a moving box",
    )),
    ("moving", _entry(
        "a Salem moving company office interior, stacks of cardboard boxes, furniture dollies, packing tape rolls, bubble wrap, moving blankets",
        "exterior of a Salem moving company, a warehouse with a big truck and a ramp extended, a small tabby cat on a packing crate",
        "interior of a Salem moving company, a friendly cartoon Frankenstein-style mover in overalls loading a dolly with boxes, a small tabby cat on top of a stack of boxes",
    )),
    ("funeral", _entry(
        "a quiet Salem funeral parlor interior, ornate caskets, flower arrangements in tall vases, candelabras, velvet curtains, prayer books",
        "exterior of a Salem funeral home, a dignified brick colonial facade with tall columns and heavy wooden doors, a small black cat on the steps",
        "interior of a Salem funeral parlor, a kindly cartoon ghost funeral director in a black suit gesturing softly at a row of candles, a small black cat on a velvet cushion",
    )),
    ("pest", _entry(
        "a Salem pest control workshop interior, spray tanks, traps, nets, chemical bottles, flashlights, protective gear on hooks",
        "exterior of a small Salem pest control company, a brick storefront with a truck outside and equipment racked on the wall, a small tabby cat on the truck step",
        "interior of a Salem pest control office, a friendly cartoon ghost pest control tech in a white suit holding a spray wand, a small tabby cat on a storage bin",
    )),
    ("tow", _entry(
        "a Salem tow yard office interior, chains, a winch, jumper cables, traffic cones, safety vests, a CB radio on a desk",
        "exterior of a Salem tow company, a tow truck with a ramp extended in a gravel yard, a small black cat on the truck hood",
        "interior of a Salem tow yard, a friendly cartoon goblin tow driver in a reflective vest holding a tow chain, a small black cat on a traffic cone",
    )),
    ("frame", _entry(
        "a Salem frame shop interior, stacks of picture frames, mat board, rolls of paper, a corner vise, a workbench with a miter saw",
        "exterior of a Salem frame shop, a narrow brick storefront with a display of frames, a small tabby cat on the step",
        "interior of a Salem frame shop, a friendly cartoon mummy framer in an apron cutting mat board at a workbench, a small tabby cat on a roll of paper",
    )),

    # ── PROFESSIONAL / OFFICES ───────────────────────────────────────────────
    ("law", _entry(
        "a Salem law office interior, leather-bound law books, a mahogany desk, brass scales of justice, framed diplomas, a green banker's lamp",
        "exterior of a Salem law office, a dignified brick colonial facade with brass trim, tall windows, a small grey cat on the granite step",
        "interior of a Salem law office, a friendly cartoon mummy lawyer in a tweed vest holding a rolled scroll at a mahogany desk, a small grey cat on a leather armchair",
    )),
    ("attorney", _entry(
        "a Salem law office interior, leather-bound law books, a mahogany desk, brass scales of justice, framed diplomas, a green banker's lamp",
        "exterior of a Salem law office, a dignified brick colonial facade with brass trim, tall windows, a small grey cat on the granite step",
        "interior of a Salem law office, a friendly cartoon mummy attorney in a tweed vest holding a rolled scroll at a mahogany desk, a small grey cat on a leather armchair",
    )),
    ("account", _entry(
        "a Salem accounting office interior, a calculator, stacks of tax forms, filing cabinets, green desk lamp, ledger books",
        "exterior of a Salem accounting office, a narrow brick storefront with tall windows and a brass door, a small grey cat on the stoop",
        "interior of a Salem accounting office, a kindly cartoon ghost accountant in spectacles tapping beads on an abacus at a mahogany desk, a small grey cat on a pile of ledgers",
    )),
    ("cpa", _entry(
        "a Salem CPA office interior, a calculator, ledger books, filing cabinets, a green desk lamp, bowls of paper clips",
        "exterior of a Salem CPA office, a narrow brick storefront with tall windows, a small grey cat on the step",
        "interior of a Salem CPA office, a kindly cartoon ghost accountant in spectacles tapping an abacus, a small grey cat on a pile of papers",
    )),
    ("tax", _entry(
        "a Salem tax office interior, a calculator, stacks of forms, filing cabinets, a computer, a paper shredder, green desk lamp",
        "exterior of a Salem tax office, a narrow brick storefront with frosted windows, a small grey cat on the step",
        "interior of a Salem tax office, a kindly cartoon mummy tax preparer in a cardigan going over a form with a calculator, a small grey cat on a filing cabinet",
    )),
    ("insurance", _entry(
        "a cozy Salem insurance office interior, a mahogany desk, filing cabinets, a green desk lamp, leather chairs, stacks of policy documents",
        "exterior of a Salem insurance office, a dignified brick storefront with tall windows, a small grey cat on the stoop",
        "interior of a Salem insurance office, a kindly cartoon ghost insurance agent in a waistcoat tapping an abacus at a desk, a small grey cat on a pile of documents",
    )),
    ("real estate", _entry(
        "a Salem real estate office interior, house models on shelves, a desk with contracts, a key rack, an abstract listing wall",
        "exterior of a Salem real estate office, a brick storefront with a big front window showing house models, a small calico cat on the step",
        "interior of a Salem real estate office, a friendly cartoon witch realtor in a blazer holding up a small wooden house model, a small calico cat on a stack of contracts",
    )),
    ("realty", _entry(
        "a Salem real estate office interior, house models on shelves, a desk with contracts, a key rack, an abstract listing wall",
        "exterior of a Salem realty office, a brick storefront with a big window, a small calico cat on the step",
        "interior of a Salem realty office, a friendly cartoon witch realtor in a blazer holding a small wooden house model, a small calico cat on a key rack",
    )),
    ("photograph", _entry(
        "a Salem photography studio interior, cameras on tripods, studio lights, plain cloth backdrops, lens collection, abstract hanging prints",
        "exterior of a Salem photography studio, a narrow brick storefront with a big front window showing a camera on a tripod, a small tabby cat on a stool out front",
        "interior of a Salem photography studio, a friendly cartoon vampire photographer in a beret holding a large camera on a tripod, a small tabby cat on a backdrop roll",
    )),
    ("photo", _entry(
        "a Salem photo studio interior, cameras on tripods, studio lights, plain backdrops, lens collection, abstract hanging prints",
        "exterior of a Salem photo studio, a narrow brick storefront with a big front window showing a camera on a tripod, a small tabby cat on a stool out front",
        "interior of a Salem photo studio, a friendly cartoon vampire photographer in a beret holding a large camera, a small tabby cat on a backdrop roll",
    )),
    ("marketing", _entry(
        "a Salem marketing agency interior, a conference table with a whiteboard of abstract diagrams, laptops, coffee cups, abstract posters on the walls",
        "exterior of a Salem marketing office, a modern brick storefront with tall glass windows, a small calico cat on a bench out front",
        "interior of a Salem marketing agency, a cheerful cartoon witch marketer in a blazer gesturing at a whiteboard with abstract charts, a small calico cat on a laptop",
    )),

    # ── FITNESS / DANCE / MUSIC / ART ────────────────────────────────────────
    ("gym", _entry(
        "a Salem gym interior, dumbbells in racks, weight benches, treadmills, punching bags, yoga mats, kettlebells, big wall mirrors",
        "exterior of a Salem gym, a wide industrial brick storefront with big windows showing gym equipment, a small tabby cat on a wooden bench out front",
        "interior of a Salem gym, a friendly cartoon werewolf trainer in athletic wear holding a kettlebell, a small tabby cat on a stack of yoga mats",
    )),
    ("fitness", _entry(
        "a Salem fitness studio interior, dumbbells, treadmills, yoga mats, kettlebells, big mirrors",
        "exterior of a Salem fitness studio, a wide modern brick storefront with big windows, a small tabby cat on a bench out front",
        "interior of a Salem fitness studio, a friendly cartoon werewolf trainer in athletic wear demonstrating a squat, a small tabby cat on a stack of yoga mats",
    )),
    ("yoga", _entry(
        "a Salem yoga studio interior, rows of yoga mats, incense, meditation cushions, potted plants, soft lighting, wall mirrors",
        "exterior of a Salem yoga studio, a narrow brick storefront with a big window showing mats and plants, a small calico cat on a meditation cushion in the window",
        "interior of a Salem yoga studio, a cheerful cartoon ghost yoga teacher in loose clothes demonstrating a pose on a mat, a small calico cat asleep on a cushion",
    )),
    ("martial art", _entry(
        "a Salem martial arts dojo interior, punching bags, training mats, wooden dummies, a weapons rack, a belt display",
        "exterior of a Salem martial arts dojo, a brick storefront with paper lanterns and a big window showing training mats, a small tabby cat on the step",
        "interior of a Salem martial arts dojo, a friendly cartoon werewolf sensei in a gi demonstrating a stance, a small tabby cat on a mat",
    )),
    ("karate", _entry(
        "a Salem karate dojo interior, training mats, a wooden dummy, punching bags, a belt display, paper lanterns",
        "exterior of a Salem karate dojo, a brick storefront with a simple doorway and paper lanterns, a small tabby cat on the step",
        "interior of a Salem karate dojo, a friendly cartoon werewolf sensei in a gi demonstrating a kata, a small tabby cat on a rolled mat",
    )),
    ("crossfit", _entry(
        "a Salem crossfit gym interior, kettlebells, barbells, pull-up bars, plyo boxes, climbing ropes, chalk bowls",
        "exterior of a Salem crossfit gym, a warehouse-style brick facade with roll-up doors, a small tabby cat on a wooden crate",
        "interior of a Salem crossfit gym, a friendly cartoon werewolf coach in athletic wear swinging a kettlebell, a small tabby cat on a chalk box",
    )),
    ("dance", _entry(
        "a Salem dance studio interior, a polished wood floor, wall mirrors, a ballet barre, dance shoes, speakers, practice mats",
        "exterior of a Salem dance studio, a narrow brick storefront with a big window showing the dance floor, a small calico cat on a chair out front",
        "interior of a Salem dance studio, a cheerful cartoon ghost dance teacher in a leotard demonstrating a ballet pose at a barre, a small calico cat on a dance bag",
    )),
    ("music", _entry(
        "a Salem music studio interior, guitars on stands, drums, microphones, amplifiers, sheet music on stands, soundproofing panels",
        "exterior of a Salem music studio, a narrow brick storefront with instruments visible in the window, a small orange cat on a guitar amp out front",
        "interior of a Salem music studio, a friendly cartoon werewolf musician in a band tee strumming a guitar, a small orange cat on an amp",
    )),
    ("art", _entry(
        "a Salem art studio interior, easels with blank canvases, paint palettes, tubes of paint, brushes in mason jars, a skylight flooding warm light",
        "exterior of a Salem art studio, a narrow brick storefront with a bay window showing an easel, a small tabby cat on a paint-speckled stool out front",
        "interior of a Salem art studio, a friendly cartoon witch artist in a paint-smeared apron painting a canvas at an easel, a small tabby cat on a stack of canvases",
    )),
    ("gallery", _entry(
        "a Salem art gallery interior, framed abstract paintings on white walls, sculptures on pedestals, spotlight beams, polished wood floors",
        "exterior of a Salem art gallery, a white brick storefront with a bay window displaying an abstract framed painting, a small white cat on the step",
        "interior of a Salem art gallery, a friendly cartoon mummy gallery curator in tweed pointing at an abstract painting on a white wall, a small white cat on a pedestal",
    )),

    # ── LODGING ──────────────────────────────────────────────────────────────
    ("hotel", _entry(
        "a grand Salem hotel lobby interior, a crackling fireplace, overstuffed velvet armchairs, a polished brass luggage rack, an oriental rug",
        "exterior of a grand Salem hotel, a historic brick building with green ivy trim and a row of brass hanging lanterns, a small black cat on a red brick step",
        "interior of a grand Salem hotel lobby, a polite cartoon Frankenstein's-monster-style bellhop in a red uniform holding a brass room key behind the front desk, a small calico cat on the oriental rug",
    )),
    ("inn", _entry(
        "a cozy Salem inn lobby interior, a small reception desk with a brass bell, a fireplace, a rocking chair, velvet armchairs, a warm hanging lantern",
        "exterior of a cozy Salem colonial inn at dusk, a warm iron lantern over the porch, clapboard walls, a hanging brass bell, a small black cat on the porch step",
        "interior of a cozy Salem inn lobby, a friendly cartoon vampire innkeeper at a reception desk holding a brass bell, a small tabby cat asleep on a velvet armchair",
    )),
    ("motel", _entry(
        "a modest Salem motel lobby interior, a reception counter with a brass bell, a wooden key rack, a small TV, a warm lamp",
        "exterior of a modest Salem motel, a long single-story building with rows of doors, a parking lot, a small orange cat on a wooden bench out front",
        "interior of a Salem motel lobby, a friendly cartoon ghost clerk at a reception counter holding a brass key, a small orange cat on the counter",
    )),
    ("hostel", _entry(
        "a cozy Salem hostel common room, bunks, shared couches, a small kitchenette, backpacks on hooks, string lights, a warm rug",
        "exterior of a Salem hostel, a clapboard facade with a welcoming porch, a small tabby cat on a backpack out front",
        "interior of a Salem hostel common room, a cheerful cartoon witch hostel manager at a reception desk pinning a note on a cork board, a small tabby cat on a bunk",
    )),
    ("b&b", _entry(
        "a cozy Salem bed-and-breakfast lounge interior, a fireplace, overstuffed chairs, a breakfast table with muffins and coffee, bookshelves",
        "exterior of a cozy Salem bed-and-breakfast, a classic colonial house with a wraparound porch, hanging flower baskets, a small tabby cat on the porch",
        "interior of a cozy Salem bed-and-breakfast, a friendly cartoon witch innkeeper in an apron pouring coffee at a breakfast table, a small tabby cat on a chair cushion",
    )),
    ("bed and breakfast", _entry(
        "a cozy Salem bed-and-breakfast lounge interior, a fireplace, overstuffed chairs, a breakfast table with muffins and coffee, bookshelves",
        "exterior of a cozy Salem bed-and-breakfast, a classic colonial house with a wraparound porch, hanging flower baskets, a small tabby cat on the porch",
        "interior of a cozy Salem bed-and-breakfast, a friendly cartoon witch innkeeper in an apron pouring coffee at a breakfast table, a small tabby cat on a chair cushion",
    )),

    # ── AUTO ─────────────────────────────────────────────────────────────────
    ("autobody", _entry(
        "a Salem auto body shop interior, a car being spray-painted, spray guns, dented panels, paint cans stacked, welding torches",
        "exterior of a Salem auto body shop, a warehouse-style building with a big roll-up door, a small tabby cat on an oil drum",
        "interior of a Salem auto body shop, a friendly cartoon Frankenstein-style body tech in a coverall spray-painting a car door, a small tabby cat watching from a stack of tires",
    )),
    ("auto body", _entry(
        "a Salem auto body shop interior, a car being spray-painted, spray guns, dented panels, paint cans stacked, welding torches",
        "exterior of a Salem auto body shop, a warehouse-style building with a big roll-up door, a small tabby cat on an oil drum",
        "interior of a Salem auto body shop, a friendly cartoon Frankenstein-style body tech in a coverall spray-painting a car door, a small tabby cat on tires",
    )),
    ("collision", _entry(
        "a Salem collision repair shop interior, a car with dents and scratches, body hammers, a welding torch, a paint booth in the back",
        "exterior of a Salem collision shop, a warehouse-style building with a roll-up door, a small black cat on an oil drum",
        "interior of a Salem collision shop, a friendly cartoon Frankenstein-style body tech straightening a dent with a body hammer, a small black cat on a paint can",
    )),
    ("tire", _entry(
        "a Salem tire shop interior, stacked tires, tire irons, lug nuts, a car jack, air compressors, wheel rims, a warm overhead light",
        "exterior of a Salem tire shop, a warehouse-style building with a roll-up door and stacks of tires outside, a small grey cat on a tire stack",
        "interior of a Salem tire shop, a friendly cartoon Frankenstein-style tire tech rolling a fresh tire across the bay, a small grey cat on a stack of rims",
    )),
    ("muffler", _entry(
        "a Salem muffler shop interior, exhaust pipes, catalytic converters, welding equipment, a car on a lift, oil cans",
        "exterior of a Salem muffler shop, a warehouse-style building with a roll-up door, a small tabby cat on an oil drum",
        "interior of a Salem muffler shop, a friendly cartoon goblin muffler tech in overalls welding an exhaust pipe, a small tabby cat watching from a distance",
    )),
    ("transmission", _entry(
        "a Salem transmission shop interior, a transmission on a workbench, tools, gear oil, a car on a lift",
        "exterior of a Salem transmission shop, a warehouse with a roll-up door, a small tabby cat on an oil drum",
        "interior of a Salem transmission shop, a friendly cartoon goblin transmission tech in overalls with a wrench over a transmission, a small tabby cat on a tool chest",
    )),
    ("mechanic", _entry(
        "a Salem auto garage interior, a cartoon car on a hydraulic lift with its hood open, wrenches on pegboard, oil cans, stacked tires, a warm shop lamp",
        "exterior of a classic Salem auto garage with large roll-up doors, red brick sides, a weathered service bay, a small black-and-white cat on a barrel out front",
        "interior of a Salem auto garage, a cartoon goblin mechanic in grease-stained overalls holding a wrench under a lifted car, a small black-and-white cat on a stack of tires",
    )),
    ("motor", _entry(
        "a Salem motor shop interior, engines on workbenches, transmissions, car parts on shelves, wrenches, oil cans",
        "exterior of a Salem motor shop, a warehouse with a roll-up door, a small tabby cat on an oil drum",
        "interior of a Salem motor shop, a friendly cartoon Frankenstein-style mechanic in a coverall working on an engine at a workbench, a small tabby cat on a toolbox",
    )),
    ("garage", _entry(
        "a Salem mechanic garage interior, a cartoon car on a lift with hood popped open, engine parts scattered, oil drums, wrenches on pegboard, tires",
        "exterior of a classic Salem auto garage with large roll-up doors, red brick sides, a weathered service bay, a small black-and-white cat on a barrel out front",
        "interior of a Salem mechanic garage, a cartoon goblin mechanic in grease-stained overalls with a wrench, a cartoon car on a hydraulic lift, a small black-and-white cat on tires",
    )),
    ("auto", _entry(
        "a Salem auto shop interior, a cartoon car on a lift, wrenches on pegboard, oil cans, stacked tires, a warm shop lamp",
        "exterior of a classic Salem auto shop with large roll-up doors, red brick sides, a weathered service bay, a small black-and-white cat on a barrel out front",
        "interior of a Salem auto shop, a cartoon goblin mechanic in grease-stained overalls holding a wrench under a lifted car, a small black-and-white cat on a stack of tires",
    )),

    # ── EDUCATION / CHILDCARE ────────────────────────────────────────────────
    ("elementary", _entry(
        "a Salem elementary school classroom interior, small wooden desks in rows, a chalkboard with abstract diagrams, stacks of textbooks, a brass bell, colorful construction paper",
        "exterior of a Salem elementary school, a traditional brick colonial schoolhouse with a small bell tower, tall windows, a small calico cat on the granite steps",
        "interior of a Salem elementary classroom, a friendly cartoon werewolf teacher in spectacles pointing at an abstract diagram on a chalkboard, a small calico cat on the teacher's desk",
    )),
    ("school", _entry(
        "a Salem schoolroom interior, small wooden desks in rows, a chalkboard with abstract diagrams, stacks of textbooks, a brass bell",
        "exterior of a Salem schoolhouse, a traditional brick or clapboard facade with a small bell tower, tall windows, a small calico cat on the steps",
        "interior of a Salem classroom, a friendly cartoon werewolf teacher in spectacles pointing at a chalkboard with abstract diagrams, a small calico cat on the desk",
    )),
    ("academy", _entry(
        "a Salem academy classroom interior, wooden desks, a chalkboard with abstract diagrams, stacks of books, a brass bell, an antique globe",
        "exterior of a Salem academy, a dignified brick colonial building with a small bell tower, a small tabby cat on the stone step",
        "interior of a Salem academy classroom, a friendly cartoon witch teacher in a robe reading from a book, a small tabby cat on the windowsill",
    )),
    ("daycare", _entry(
        "a cozy Salem daycare interior, colorful toys on the floor, tiny chairs, abstract finger paintings on walls, building blocks, stuffed animals",
        "exterior of a cozy Salem daycare, a classic clapboard house with a small playground, a wrought iron door knocker, a small calico cat on a fence post",
        "interior of a cozy Salem daycare, a cheerful cartoon witch daycare teacher in an apron reading a picture book to tiny stuffed animals, a small calico cat on a bean bag",
    )),
    ("preschool", _entry(
        "a cozy Salem preschool interior, colorful toys, tiny chairs, abstract finger paintings, blocks, stuffed animals",
        "exterior of a cozy Salem preschool, a classic clapboard facade with a small playground, a small calico cat on a fence post",
        "interior of a cozy Salem preschool, a cheerful cartoon witch teacher reading a picture book, a small calico cat on a bean bag",
    )),
    ("tutor", _entry(
        "a cozy Salem tutoring room interior, a whiteboard with abstract diagrams, textbooks, a desk with worksheets, flash cards",
        "exterior of a Salem tutoring center, a brick storefront with a big window showing a tidy study space, a small tabby cat on a bench out front",
        "interior of a Salem tutoring room, a friendly cartoon witch tutor in glasses pointing at a whiteboard with abstract diagrams, a small tabby cat on a stack of books",
    )),
    ("library", _entry(
        "a cozy Salem library interior, towering shelves of leather-bound books, reading nooks with green banker's lamps, a rolling ladder",
        "exterior of a Salem library, a dignified brick building with tall arched windows and columns, a small tabby cat on the stone step",
        "interior of a Salem library, a friendly cartoon mummy librarian in a cardigan pushing a cart of books, a small tabby cat asleep on a reading chair",
    )),

    # ── WORSHIP ──────────────────────────────────────────────────────────────
    ("church", _entry(
        "a quiet Salem church interior, straight-back wooden pews in long rows, a tall wooden pulpit, shafts of morning light from tall windows, dust motes in amber light",
        "exterior of a white wooden New England church with a steeple, a simple colonial silhouette against a deep plum sky, a small orange cat on the granite step",
        "interior of a Salem church, a kindly translucent cartoon ghost preacher in Puritan black clothes at a tall pulpit with an open leather book showing abstract illuminated designs, a small striped cat in the first pew",
    )),
    ("meetinghouse", _entry(
        "a quiet Salem Puritan meetinghouse interior, straight-back pews, a tall pulpit, shafts of morning light from tall windows",
        "exterior of an austere white wooden Puritan meetinghouse with a simple colonial silhouette and small steeple, a small orange cat on the step",
        "interior of a Puritan meetinghouse, a kindly translucent cartoon ghost preacher at a tall pulpit, a small striped cat in the first pew",
    )),
    ("temple", _entry(
        "a quiet Salem temple interior, carved wooden columns, stained glass with abstract patterns, soft pendant lights, cushioned benches, candle alcoves",
        "exterior of a Salem temple, a dignified facade with a small dome, a small orange cat on the step",
        "interior of a Salem temple, a kindly cartoon ghost rabbi in a tallit gesturing gently at a scroll, a small orange cat on a cushion",
    )),
    ("chapel", _entry(
        "a quiet Salem chapel interior, rows of small wooden pews, a small pulpit, candles, stained glass with abstract patterns",
        "exterior of a small Salem chapel, a white clapboard facade with a tiny bell tower, a small calico cat on the stone step",
        "interior of a Salem chapel, a kindly cartoon ghost minister at a small pulpit, a small calico cat on a wooden pew",
    )),

    # ── TOURS ────────────────────────────────────────────────────────────────
    ("ghost tour", _entry(
        "a cozy Salem ghost-tour outfitter office, abstract maps on walls, brass lanterns on hooks, folded brochures, costume top hats on pegs, walking sticks",
        "exterior of a Salem ghost-tour company office, a dark brick storefront with a brass lantern hanging, a small black cat on the step",
        "interior of a Salem ghost-tour office, a friendly cartoon ghost tour leader in a Victorian coat gesturing at a rack of walking sticks and brass lanterns, a small black cat on the counter",
    )),
    ("walking tour", _entry(
        "a cozy Salem tour office interior, abstract maps on walls, brass lanterns, folded brochures, walking sticks, a brass bell",
        "exterior of a Salem tour company office, a brick storefront with a hanging brass lantern, a small orange cat on the step",
        "interior of a Salem tour office, a cheerful cartoon witch tour guide holding a brass lantern and pointing at a big rolled parchment map with abstract coastlines, a small calico cat on the map",
    )),
    ("tour", _entry(
        "a cozy Salem tour company office, abstract maps on walls, brass lanterns on hooks, folded brochures, walking sticks, costume hats on pegs",
        "exterior of a Salem tour company office, a brick storefront with a brass lantern hanging, a small orange cat on the step",
        "interior of a Salem tour office, a cheerful cartoon witch tour guide holding a brass lantern up and pointing at a rolled parchment map with abstract coastlines, a small calico cat on the map",
    )),

    # ── SALEM SPECIALTY ──────────────────────────────────────────────────────
    ("witch", _entry(
        "a dim velvet-draped Salem witch shop interior, shelves of glowing potion bottles and crystal points, a crystal ball on silk cloth, a bowl of dried catnip, cozy velvet chairs",
        "exterior of a Salem witch shop, a black timber facade with a swinging iron pentacle-shaped medallion over the door, warm candle glow in small windows, a tiny black cat under the medallion",
        "interior of a Salem witch shop, a cheerful cartoon green-skinned witch in a velvet dress arranging glowing potion bottles behind a wooden counter, a small black cat asleep on a stack of tomes",
    )),
    ("occult", _entry(
        "a dim Salem occult shop interior, shelves of herbs in jars, crystal points, tarot cards, a brass mortar and pestle, velvet cloths",
        "exterior of a Salem occult shop, a dark brick storefront with a pentacle-shaped medallion, warm candle glow in the windows, a small black cat on the step",
        "interior of a Salem occult shop, a friendly cartoon crone witch in a shawl grinding dried herbs in a brass mortar, a small grey cat on a crystal-bottle shelf",
    )),
    ("psychic", _entry(
        "a dim Salem psychic parlor interior, velvet chairs, a polished crystal orb on silk, tarot cards fanned, incense smoke curling, soft purple lamplight",
        "exterior of a Salem psychic parlor, a small brick storefront with velvet drapes in the windows and a small purple door, a crescent-moon lamp, a small calico cat on the step",
        "interior of a Salem psychic parlor, a cartoon velvet-cloaked vampire seer gesturing at a glowing crystal orb on silk, a small black cat on a velvet armchair across the table",
    )),
    ("tarot", _entry(
        "a dim Salem tarot room interior, velvet chairs around a small round table, a polished crystal orb, tarot cards fanned on silk, incense smoke",
        "exterior of a Salem tarot parlor, a narrow brick storefront with velvet drapes in the window, a small calico cat on the step",
        "interior of a Salem tarot room, a kindly cartoon gypsy witch in a silk headscarf laying out tarot cards with abstract symbols on a small round table, a small calico cat watching the cards",
    )),
    ("spirit", _entry(
        "a dim Salem spiritual-goods shop interior, shelves of crystals, incense, candles, charms, a crystal ball on a silk cloth",
        "exterior of a Salem spiritual-goods shop, a dark timber brick storefront with a crescent-moon hanging, a small black cat on the step",
        "interior of a Salem spiritual-goods shop, a cheerful cartoon witch shopkeeper in a velvet dress arranging crystals behind a counter, a small black cat on a pile of velvet cloths",
    )),
    ("cannabis", _entry(
        "a Salem cannabis dispensary interior, glass jars of colorful buds, edible packages, vape pens, rolling papers, scales, warm pendant lights",
        "exterior of a Salem cannabis dispensary, a clean modern brick storefront with a big frosted window, a small tabby cat on a bench out front",
        "interior of a Salem cannabis dispensary, a friendly cartoon ghost budtender in a hoodie behind a display case of jars, a small tabby cat on a counter",
    )),
    ("dispensary", _entry(
        "a Salem dispensary interior, glass jars of buds, edible packages, vape pens, scales, warm pendant lights",
        "exterior of a Salem dispensary, a clean modern brick storefront with a big frosted window, a small tabby cat on a bench out front",
        "interior of a Salem dispensary, a friendly cartoon ghost budtender in a hoodie behind a display case of jars, a small tabby cat on a counter",
    )),

    # ── ENTERTAINMENT ────────────────────────────────────────────────────────
    ("theatre", _entry(
        "a Salem theatre lobby interior, plush red seats visible through an archway, velvet curtains, gold trim, soft spotlights, a popcorn stand",
        "exterior of a classic Salem theatre with a grand arched doorway canopy, red doors, warm amber lights overhead, a small white cat on the curb",
        "interior of a Salem theatre lobby, a friendly cartoon vampire ticket-taker in a red usher uniform holding out a plain paper ticket, a small black cat under a brass rope stanchion",
    )),
    ("theater", _entry(
        "a Salem theater lobby interior, plush red seats through an archway, velvet curtains, gold trim, soft spotlights, a popcorn stand",
        "exterior of a classic Salem theater with a grand arched doorway canopy, red doors, warm amber lights, a small white cat on the curb",
        "interior of a Salem theater lobby, a friendly cartoon vampire ticket-taker in a red usher uniform holding a plain paper ticket, a small black cat under a rope stanchion",
    )),
    ("cinema", _entry(
        "a Salem cinema lobby interior, a popcorn machine, candy counter, velvet ropes, soft spotlights, a ticket booth",
        "exterior of a Salem cinema with a grand arched doorway canopy and abstract decorative trim, a small white cat on the curb",
        "interior of a Salem cinema lobby, a friendly cartoon vampire usher holding a flashlight gesturing toward the theater doors, a small white cat on a velvet seat",
    )),
    ("bowl", _entry(
        "a Salem bowling alley interior, wooden lanes, racks of bowling balls, pin-setters, neon glow, red vinyl seats",
        "exterior of a Salem bowling alley, a long low brick building with an arched doorway canopy, a small orange cat on the curb",
        "interior of a Salem bowling alley, a friendly cartoon werewolf bowling attendant in a vest handing out shoes, a small orange cat on a scoring table",
    )),
    ("arcade", _entry(
        "a Salem arcade interior, rows of arcade cabinets glowing with abstract screens, pinball machines, a change booth, neon lights",
        "exterior of a Salem arcade, a brick storefront with abstract neon trim and big glowing windows, a small orange cat on a bench out front",
        "interior of a Salem arcade, a cheerful cartoon goblin arcade attendant in a striped shirt holding a roll of tokens, a small orange cat on top of a pinball machine",
    )),
    ("museum", _entry(
        "a grand Salem museum gallery interior, polished wood floors, gleaming brass railings, display cases reflecting soft spotlights, velvet benches warm from sunlight",
        "exterior of a grand Salem museum with a stone facade, tall columns, warm lobby light spilling onto cobblestones, a small grey cat on the front steps",
        "interior of a Salem museum gallery, a friendly cartoon mummy curator in khaki tweed holding a blank wooden clipboard and gesturing at an artifact in a glass case, a small sandy cat investigating a pedestal",
    )),

    # ── FINANCE ──────────────────────────────────────────────────────────────
    ("bank", _entry(
        "a cozy Salem bank interior, a brass-railed teller counter, gold coins on a velvet tray, leather ledger books, polished wood paneling, a green desk lamp",
        "exterior of a small Salem bank with stone steps, tall columns, heavy wooden doors, a brass lamp by the door, a small grey cat on the steps",
        "interior of a Salem bank, a friendly cartoon vampire banker in a dark waistcoat counting gold coins behind a brass-railed counter, a small black cat on a leather ledger",
    )),
    ("credit union", _entry(
        "a cozy Salem credit union lobby interior, a teller counter, leather armchairs, a warm ceiling fan, a small potted plant",
        "exterior of a Salem credit union, a clean brick storefront with a glass front door, a small grey cat on the step",
        "interior of a Salem credit union, a friendly cartoon mummy teller in a cardigan handing out a small cloth bag, a small grey cat on a leather armchair",
    )),

    # ── PARKS / OUTDOOR ──────────────────────────────────────────────────────
    ("playground", _entry(
        "a Salem playground at golden hour, swings and a slide, wood chips, an abstract painted hopscotch, tall trees overhead",
        "exterior of a Salem playground, a wrought-iron fence around swings, a wooden climbing structure, a small calico cat on a swing seat",
        "a Salem playground in autumn, a friendly cartoon witch park caretaker gently pushing an empty swing, a small calico cat on the slide",
    )),
    ("garden", _entry(
        "a tranquil Salem garden interior, flowering shrubs, a stone bench, a small pond with lily pads, a trellis of vines, a warm sunny spot",
        "exterior of a Salem garden, a wrought-iron gate, a path through flowering hedges, a small tabby cat on the gate post",
        "a Salem garden in autumn, a friendly cartoon goblin gardener in overalls watering a flower bed with a copper watering can, a small tabby cat in a flower pot",
    )),
    ("park", _entry(
        "a Salem park at golden hour, iron lampposts casting warm pools of light, tall autumn oaks with crackly leaves, a picnic basket tipped on a bench, warm sunny patches on the grass",
        "exterior of a Salem park at dusk, a black iron fence, tall autumn trees with gold leaves, a wrought-iron lamppost glowing, a small black cat on the grass path",
        "a Salem park lawn in autumn, a friendly cartoon werewolf park ranger in a green uniform hat raking autumn leaves, a small white cat riding on top of the leaf pile",
    )),
    ("cemetery", _entry(
        "a Salem cemetery at twilight, leaning weathered slate headstones with abstract grooves, soft autumn leaves on cool grass, low stone walls with mossy warm spots",
        "exterior of a Salem cemetery, ancient tilted slate gravestones in autumn fog, a wrought iron gate, a small grey cat on top of an old headstone",
        "a Salem cemetery path between old headstones, a friendly cartoon groundskeeper skeleton in flannel and overalls raking autumn leaves, a small tabby cat batting at the leaf pile",
    )),
    ("burial", _entry(
        "a Salem cemetery at twilight, leaning weathered slate headstones with abstract grooves, soft autumn leaves on cool grass, low stone walls",
        "exterior of a Salem burial ground, ancient tilted slate gravestones in autumn fog, a wrought iron gate, a small grey cat on top of a headstone",
        "a Salem cemetery path between old headstones, a friendly cartoon groundskeeper skeleton raking autumn leaves, a small tabby cat batting at the leaf pile",
    )),
    ("common", _entry(
        "a Salem town common at golden hour, wide grassy lawn, iron lampposts glowing, tall autumn oaks, park benches, a gazebo in the distance",
        "exterior of a Salem common park, a black iron fence, tall autumn trees with gold leaves, a small black cat on the grass path",
        "a Salem common lawn in autumn, a friendly cartoon werewolf park ranger raking autumn leaves, a small white cat on top of the leaf pile",
    )),
    ("wharf", _entry(
        "a Salem wharf at dusk, weathered wooden planks, coiled ropes, fishing nets, tall ship masts in silhouette, a brass lantern glowing on a piling",
        "exterior view of a Salem wharf with tall ship masts silhouetted against a plum sky, a brass lantern on a piling, a small black cat on a lobster trap",
        "a Salem wharf at twilight, a friendly cartoon ghost sea-captain in a long coat standing beside a coil of rope, a small black cat on a piling",
    )),
    ("harbor", _entry(
        "a Salem harbor at dusk, weathered wooden planks, coiled ropes, masts in silhouette, a lantern on a piling, a small fishing boat tied up",
        "exterior view of Salem harbor with tall ship masts against a plum sky, a pier, a small black cat on a lobster trap",
        "a Salem harbor pier at twilight, a friendly cartoon ghost sea-captain in a long coat gesturing at a coiled rope, a small black cat on a piling",
    )),
    ("beach", _entry(
        "a Salem beach at golden hour, soft sand, gentle waves, driftwood logs, seashells scattered, a warm sandy patch",
        "exterior view of a Salem beach, a wooden boardwalk through dune grass leading to the sand, a small tabby cat on the boardwalk",
        "a Salem beach at sunset, a friendly cartoon mermaid-style siren sitting on driftwood combing her hair, a small tabby cat at her feet",
    )),

    # ── CIVIC / GOVERNMENT ───────────────────────────────────────────────────
    ("city hall", _entry(
        "a Salem city hall interior, a grand marble lobby with brass railings, polished wood doors, tall pillars, a reception counter",
        "exterior of a Salem city hall, a dignified brick colonial building with tall columns and granite steps, a small grey cat on the step",
        "interior of a Salem city hall, a friendly cartoon witch clerk at a brass-railed counter stamping a parchment, a small grey cat on a marble floor",
    )),
    ("town hall", _entry(
        "a Salem town hall interior, a grand lobby with wooden doors, brass railings, a reception counter, an abstract bulletin board",
        "exterior of a Salem town hall, a brick colonial building with a small bell tower and tall windows, a small grey cat on the step",
        "interior of a Salem town hall, a friendly cartoon witch clerk at a reception counter stamping a parchment, a small grey cat on a bench",
    )),
    ("post office", _entry(
        "a Salem post office interior, rows of brass mailboxes, a clerk counter, scales, stacks of plain packages, a brass bell",
        "exterior of a Salem post office, a dignified brick building with tall columns, a small grey cat on the step",
        "interior of a Salem post office, a friendly cartoon mummy postal clerk in a vest weighing a package on a brass scale, a small grey cat on a mailbox counter",
    )),
    ("fire", _entry(
        "a Salem fire station interior, a red fire truck, coiled hoses, turnout gear on hooks, a polished brass pole, helmets",
        "exterior of a Salem fire station, a brick building with big red bay doors open showing a fire truck, a small orange cat on the truck bumper",
        "interior of a Salem fire station, a friendly cartoon werewolf firefighter in a red uniform polishing the fire truck, a small orange cat in the driver's seat",
    )),
    ("police", _entry(
        "a Salem police station interior, a reception counter, an abstract cork bulletin board, wooden benches, filing cabinets",
        "exterior of a Salem police station, a brick colonial building with blue lamp globes by the entrance, a small grey cat on the stone step",
        "interior of a Salem police station, a friendly cartoon mummy officer in uniform at a reception counter holding a plain ticket, a small grey cat on a filing cabinet",
    )),
]


# SUBCATEGORY SCENES
SUBCATEGORY_SCENES: dict[str, dict[str, str]] = {
    "FOOD_DRINK__cafes": _entry(
        "a Salem cafe interior, espresso machine steaming, rows of ceramic mugs, a pastry case, cozy leather armchair, warm amber light",
        "exterior of a Salem cafe with a brick facade and cornice trim, big windows glowing amber, a small calico cat on a chair out front",
        "interior of a Salem cafe, a friendly cartoon mummy barista in a knit beanie pulling an espresso shot, a small calico cat on a coffee-bean bag",
    ),
    "FOOD_DRINK__bars": _entry(
        "a Salem bar interior, rows of liquor bottles glowing on backlit shelves, beer taps, a cocktail shaker, whiskey glasses, leather stools",
        "exterior of a Salem bar, a dark brick storefront with a single iron lantern glowing, a small black cat on the step",
        "interior of a Salem bar, a friendly cartoon vampire bartender in a waistcoat shaking a cocktail, a small black cat on a barrel",
    ),
    "SHOPPING__bookstores": _entry(
        "a cozy Salem bookshop interior, towering shelves of leather-bound books, a reading nook with a leather armchair, a brass reading lamp, amber glow",
        "exterior of a Salem bookshop, a brick storefront with a bay window full of stacked books, a small tabby cat in the window",
        "interior of a Salem bookshop, a friendly cartoon mummy bookseller in a cardigan reaching for a leather-bound book on a high shelf, a small tabby cat asleep on a reading chair",
    ),
    "SHOPPING__antiques": _entry(
        "a cozy Salem antique shop interior, vintage furniture, tarnished silver, grandfather clocks, old paintings with blank canvases, brass candlesticks",
        "exterior of a Salem antique shop, a weathered wooden storefront, an iron hanging lantern, a wooden barrel by the door with antique wares, a small grey cat on the barrel",
        "interior of a Salem antique shop, a friendly cartoon ghost antique dealer in Victorian clothes polishing a brass candlestick, a small grey cat on a velvet chair",
    ),
    "SHOPPING__beauty_spa": _entry(
        "a cozy Salem spa interior, a massage table with soft towels, essential oil bottles, hot stones, candles flickering, pastel tile",
        "exterior of a Salem spa, a quiet brick storefront with velvet drapes and candle glow in the window, a small grey cat on a chair out front",
        "interior of a Salem spa, a friendly cartoon ghost masseuse in a robe lighting candles around a massage table, a small grey cat on a towel",
    ),
    "SHOPPING__laundromats": _entry(
        "a cozy Salem laundromat interior, rows of washing machines, dryers tumbling, folded clothes, detergent bottles, fluorescent light",
        "exterior of a Salem laundromat, a clean brick storefront with big windows showing washers, a small tabby cat on a bench out front",
        "interior of a Salem laundromat, a friendly cartoon Frankenstein-style attendant folding clothes, a small tabby cat in a laundry basket",
    ),
    "SHOPPING__storage_rentals": _entry(
        "a Salem storage facility interior, a hallway of metal roll-up doors, padlocks, moving boxes stacked, hand trucks",
        "exterior of a Salem storage facility, rows of orange roll-up doors, a small tabby cat on a hand truck",
        "interior of a Salem storage facility hallway, a friendly cartoon ghost storage manager unlocking a roll-up door, a small tabby cat on a moving box",
    ),
    "SHOPPING__pet_stores": _entry(
        "a Salem pet shop interior, pet toys, food bowls, leashes, fish tanks glowing, pet beds, hamster cages",
        "exterior of a Salem pet shop, a brick storefront with a big window showing fish tanks, a small tabby cat on the step",
        "interior of a Salem pet shop, a cheerful cartoon witch keeper in an apron holding a fish net over an aquarium, a small tabby cat watching",
    ),
    "ENTERTAINMENT__tour_operators": _entry(
        "a Salem tour company office, abstract maps on walls, brass lanterns, folded brochures, walking sticks, costume hats",
        "exterior of a Salem tour company office, a brick storefront with a brass lantern hanging, a small orange cat on the step",
        "interior of a Salem tour office, a cheerful cartoon witch tour guide holding a brass lantern pointing at a rolled parchment map, a small calico cat on the map",
    ),
    "ENTERTAINMENT__fitness": _entry(
        "a Salem fitness studio interior, dumbbells, treadmills, yoga mats, kettlebells, big wall mirrors",
        "exterior of a Salem fitness studio, a wide modern brick storefront with big windows showing equipment, a small tabby cat on a bench out front",
        "interior of a Salem fitness studio, a friendly cartoon werewolf trainer in athletic wear holding a kettlebell, a small tabby cat on a yoga mat stack",
    ),
    "HISTORICAL_BUILDINGS__museums": _entry(
        "a grand Salem museum gallery interior, polished wood floors, brass railings, display cases with artifacts, spotlights, velvet benches",
        "exterior of a grand Salem museum, a stone facade with tall columns, warm lobby light, a small grey cat on the steps",
        "interior of a Salem museum gallery, a friendly cartoon mummy curator in tweed holding a blank clipboard gesturing at an artifact in a glass case, a small sandy cat on a pedestal",
    ),
}


# CATEGORY DEFAULTS
CATEGORY_DEFAULTS: dict[str, dict[str, str]] = {
    "HISTORICAL_BUILDINGS": _entry(
        "an ancient timbered interior of a historic Salem house, wide worn floorboards, a stone hearth with glowing embers, dust motes drifting in amber candlelight, carved wooden beams",
        "exterior of a 17th-century black-timbered Puritan house with steep gables and diamond-pane windows, autumn twilight, a small tuxedo cat on the stone doorstep",
        "interior of a historic Salem house, a kindly cartoon ghost Colonial-era resident in period clothing dusting a wooden mantel with a feather duster, a small calico cat asleep on a rug",
    ),
    "WORSHIP": _entry(
        "a quiet colonial Puritan meetinghouse interior, straight-back wooden pews, a tall pulpit, shafts of morning sunlight, dust motes",
        "exterior of an austere white wooden New England church with a simple colonial silhouette and small steeple, a small orange cat on the granite step",
        "interior of a wooden Puritan meetinghouse, a kindly cartoon ghost preacher at a tall pulpit with a plain leather book, a small striped cat in the first pew",
    ),
    "CIVIC": _entry(
        "a Salem civic-office interior with a heavy oak counter, brass filing cabinets, tall windows, stacks of rolled paperwork, an old brass oil lamp",
        "exterior of a red brick colonial Salem civic building with tall windows and granite steps, brass door lantern, a small grey cat on the step",
        "interior of a colonial Salem civic office, a friendly cartoon goblin clerk in a small tri-corner hat and apron stamping a parchment with a brass seal, a small tabby cat on the counter",
    ),
    "WITCH_SHOP": _entry(
        "a dim velvet-draped Salem witch shop interior, shelves of glowing potion bottles and crystal points, a crystal ball on silk cloth, a bowl of dried catnip, cozy velvet chairs",
        "exterior of a Salem witch shop, a black timber facade with a swinging iron pentacle-shaped medallion, warm candle glow, a tiny black cat under the medallion",
        "interior of a Salem witch shop, a cheerful cartoon green-skinned witch arranging glowing potion bottles behind a wooden counter, a small black cat asleep on a stack of tomes",
    ),
    "PSYCHIC": _entry(
        "a dim Salem psychic parlor interior, velvet chairs, a polished crystal orb on silk, tarot cards fanned, incense smoke, soft purple lamplight",
        "exterior of a Salem psychic parlor, a small brick storefront with velvet drapes in the window, a crescent-moon lamp, a small calico cat on the step",
        "interior of a Salem psychic parlor, a cartoon velvet-cloaked vampire seer gesturing at a glowing crystal orb, a small black cat on a velvet armchair",
    ),
    "PARKS_REC": _entry(
        "a Salem park at golden hour, iron lampposts casting warm pools of light, tall autumn oaks with crackly leaves, a picnic basket, warm sunny grass",
        "exterior of a Salem park at dusk, a black iron fence, tall autumn trees with gold leaves, a wrought-iron lamppost, a small black cat on the grass path",
        "a Salem park lawn in autumn, a friendly cartoon werewolf park ranger in a green uniform hat raking autumn leaves, a small white cat on the leaf pile",
    ),
    "FOOD_DRINK": _entry(
        "a cozy Salem eatery interior, candlelit tables, plates of warm food, sizzling pans, brass railings, warm pools of candlelight",
        "exterior of a historic red brick Salem eatery facade with large bright windows glowing warm amber, a brass doorway lantern, a small tortoiseshell cat on the windowsill",
        "interior of a Salem restaurant, a friendly cartoon vampire in a butler's tuxedo serving a plate at a candlelit table, a small black cat under a chair",
    ),
    "SHOPPING": _entry(
        "a quaint Salem retail shop interior, shelves of interesting products, a warm counter lamp, a cozy rug behind the register, cardboard boxes for a cat to nap in",
        "exterior of a Salem storefront with red brick or white clapboard walls, cozy window displays, a warm hanging lantern, a small grey cat on a wooden barrel",
        "interior of a Salem shop, a cheerful cartoon Frankenstein-style shopkeeper in a tidy apron stocking a wooden shelf, a small tabby cat on the counter",
    ),
    "LODGING": _entry(
        "a grand Salem hotel lobby with a crackling fireplace, overstuffed velvet armchairs, a polished brass luggage rack, an oriental rug",
        "exterior of a grand historic brick Salem hotel with green ivy trim over the entrance and brass hanging lanterns, a small black cat on a brick step",
        "interior of a grand Salem hotel lobby, a polite cartoon Frankenstein's-monster-style bellhop in a red uniform holding a brass room key behind the desk, a small calico cat on the rug",
    ),
    "ENTERTAINMENT": _entry(
        "a classic Salem theatre foyer with plush red seats, velvet curtains, gold accents, soft spotlights, a warm carpet aisle",
        "exterior of a classic Salem theatre or venue facade with a grand arched doorway canopy, red doors, warm amber lights, a small white cat under the canopy",
        "interior of a Salem theatre lobby, a friendly cartoon vampire ticket-taker in a red usher uniform holding out a plain paper ticket, a small black cat under a brass rope stanchion",
    ),
    "HEALTHCARE": _entry(
        "a cozy Salem clinic waiting room, soft lighting, leafy plants, upholstered chairs, a reception counter, warm rugs",
        "exterior of a small historic Salem clinic building, a brick facade with a small overhang, tidy window boxes, a small tabby cat on the step",
        "interior of a cozy clinic, a friendly cartoon mummy doctor in a knee-length white coat holding a wooden stethoscope, a small tabby cat on the exam table",
    ),
    "OFFICES": _entry(
        "a quiet Salem professional office, a mahogany desk, brass oil lamp, leather armchair, tall shelves of ledgers, warm lamplight",
        "exterior of a historic Salem professional office building, a red brick facade with tall windows and a brass doorway, a small grey cat on the stoop",
        "interior of a Salem office, a kindly cartoon ghost accountant in spectacles tapping an abacus at a mahogany desk, a small grey cat on a pile of ledgers",
    ),
    "EDUCATION": _entry(
        "a warm colonial Salem classroom, small wooden desks in rows, a chalkboard with abstract diagrams, stacks of textbooks, a brass bell",
        "exterior of a colonial Salem schoolhouse with a simple steeple, white clapboards, tall windows, a small ginger cat on the path",
        "interior of a colonial Salem classroom, a friendly cartoon werewolf teacher in spectacles pointing at a chalkboard with abstract diagrams, a small calico cat on the desk",
    ),
    "AUTO_SERVICES": _entry(
        "a Salem auto shop interior, a cartoon car on a hydraulic lift with hood open, wrenches on pegboard, oil cans, stacked tires, a warm shop lamp",
        "exterior of a classic Salem auto-service garage with large roll-up doors, red brick sides, a weathered service bay, an oil drum, a small black-and-white cat on the drum",
        "interior of a Salem auto garage, a cartoon goblin mechanic in grease-stained overalls holding a wrench under a lifted car, a small black-and-white cat on a stack of tires",
    ),
    "TOUR_COMPANIES": _entry(
        "a cozy Salem tour outfitter office, abstract maps on walls, brass lanterns, folded brochures, walking sticks, costume hats",
        "exterior of a Salem tour-company storefront with a hanging brass lantern and a folded map in the window, a small orange cat on the step",
        "interior of a Salem tour office, a cheerful cartoon witch tour guide holding a brass lantern pointing at a rolled parchment map, a small calico cat on the map",
    ),
    "FINANCE": _entry(
        "a cozy Salem bank interior, a brass-railed teller counter, gold coins on a velvet tray, leather ledger books, polished wood paneling, green desk lamp",
        "exterior of a small Salem bank building with stone steps, tall columns, heavy wooden doors, a brass lamp, a small grey cat on the steps",
        "interior of a Salem bank, a friendly cartoon vampire banker in a dark waistcoat counting gold coins behind a brass-railed counter, a small black cat on a leather ledger",
    ),
}


# FAMOUS OVERRIDES — exact-phrase match on name (case-insensitive substring)
FAMOUS_OVERRIDES: dict[str, dict[str, str]] = {
    "witch house": _entry(
        "the dim ancient Puritan kitchen of the Witch House, diamond-paned windows with candlelight, a warm glowing hearth, an iron cooking pot, dust motes drifting in amber light, a perfect cozy kitchen corner for a cat to nap",
        "exterior view of the Witch House in Salem, a 17th-century black-timbered Puritan house with steep gables and diamond-pane windows, autumn twilight, a tiny tuxedo cat sitting on the cold stone doorstep",
        "interior of the Witch House kitchen, a friendly cartoon witch in a tall pointy hat stirring a bubbling cauldron over a hearth fire, wooden beams overhead, a small grey tabby cat on the rafters watching",
    ),
    "witch trials memorial": _entry(
        "a quiet stone memorial plaza, twenty low granite benches in a rectangle with abstract grooves, an old black locust tree overhead, golden autumn leaves drifting down, a reverent hush",
        "exterior of the Salem Witch Trials Memorial, a solemn granite plaza enclosed by low stone walls, a black locust tree overhead, autumn leaves, a small black cat sitting quietly on one of the benches",
        "the Salem Witch Trials Memorial plaza at twilight, a kindly translucent cartoon ghost in colonial black clothing standing gently beside a stone bench with one hand on it in tribute, a small grey cat at the foot of the bench, the bench is completely plain",
    ),
    "charter street": _entry(
        "leaning weathered slate headstones in a twilight cemetery, soft autumn leaves on cool grass, low stone walls with mossy warm spots, small creatures rustling in the leaves",
        "exterior of Charter Street Burying Point cemetery, ancient tilted slate gravestones in autumn fog, a wrought iron gate, a small grey cat on top of an old headstone",
        "a cemetery path between old headstones, a friendly cartoon groundskeeper skeleton in a flannel shirt and overalls raking autumn leaves, a small tabby cat batting at the leaf pile",
    ),
    "old burying point": _entry(
        "leaning weathered slate headstones in a twilight cemetery, soft autumn leaves on cool grass, low stone walls with mossy warm spots, small creatures rustling in the leaves",
        "exterior of Old Burying Point cemetery, ancient tilted slate gravestones in autumn fog, a wrought iron gate, a small grey cat on top of an old headstone",
        "a cemetery path between old headstones, a friendly cartoon groundskeeper skeleton in a flannel shirt and overalls raking autumn leaves, a small tabby cat batting at the leaf pile",
    ),
    "proctor's ledge": _entry(
        "a small solemn stone memorial ring on a wooded ledge, a low curved stone wall with abstract grooves, tall oak trees overhead, autumn leaves on the ground",
        "exterior of the Proctor's Ledge Memorial in Salem, a small curved granite wall on a wooded ledge, tall oaks, autumn light, a small grey cat sitting quietly at the base of the wall",
        "the Proctor's Ledge Memorial at twilight, a kindly translucent cartoon ghost in colonial clothing standing with bowed head beside the granite wall, a small black cat at the base of the wall, reverent tone",
    ),
    "bewitched statue": _entry(
        "a cozy Salem town square, a bronze statue of a cartoon witch on a broom, warm lamplight, benches, autumn leaves on cobblestone",
        "exterior of Lappin Park in Salem with the bronze Bewitched statue of a witch on a broomstick, warm lamplight, autumn leaves on cobblestone, a small orange cat at the base of the statue",
        "Lappin Park in Salem, a friendly cartoon witch taking a selfie next to the bronze Bewitched statue of a witch on a broomstick, a small tabby cat on the statue's bronze base",
    ),
    "hawthorne hotel": _entry(
        "a grand Salem hotel lobby with a crackling fireplace, overstuffed velvet armchairs, a polished brass luggage rack, an oriental rug",
        "exterior of a grand historic brick hotel modeled on the Hawthorne Hotel, green ivy trim, a row of brass hanging lanterns, a small black cat on a red brick step by the front doors",
        "interior of an elegant hotel lobby, a polite cartoon Frankenstein's-monster-style bellhop in a red uniform holding a brass room key behind the front desk, a small calico cat curled on the oriental lobby rug",
    ),
    "peabody essex": _entry(
        "a grand museum gallery with polished wood floors, gleaming brass railings, display cases reflecting soft spotlights, velvet benches warm from sunlight",
        "exterior of a grand stone museum facade with tall columns and warm museum lobby light spilling out onto cobblestones, a small grey cat sitting on the front steps",
        "interior of a museum gallery, a friendly cartoon mummy curator in khaki tweed holding a blank wooden clipboard and gesturing at an ancient artifact in a glass case, a small sandy-colored cat investigating a display pedestal",
    ),
    "salem common": _entry(
        "a Salem park lawn at golden hour, iron lampposts casting warm pools of light, tall oaks with crackly autumn leaves, a picnic basket, warm sunny patches on the grass",
        "exterior of Salem Common park at dusk, a black iron fence, tall autumn trees with gold leaves, a wrought-iron lamppost, a small black cat sitting in the middle of the grass path",
        "a Salem park lawn in autumn, a friendly cartoon werewolf park ranger in a green uniform hat raking autumn leaves, a small white cat riding on top of the leaf pile",
    ),
    "house of the seven gables": _entry(
        "a dark wood-beamed colonial mansion hall interior, a wooden staircase, wide floorboards, an iron candelabrum, a heavy wooden door, dust motes in amber light",
        "exterior of the House of the Seven Gables in Salem, a dark weathered wooden colonial mansion with seven gables against a plum dusk sky, a small black cat on the stone step",
        "interior of a dark colonial mansion hall, a friendly cartoon ghost in a long dark cloak gesturing up at a wooden staircase, a small grey cat on the bannister",
    ),
    "samantha": _entry(
        "a cozy Salem town square, a bronze statue of a cartoon witch on a broom, warm lamplight, benches, autumn leaves on cobblestone",
        "exterior of Lappin Park in Salem with the bronze Samantha Bewitched statue, warm lamplight, autumn leaves on cobblestone, a small orange cat at the base of the statue",
        "Lappin Park in Salem, a friendly cartoon witch taking a selfie next to the bronze Samantha Bewitched statue, a small tabby cat on the statue's bronze base",
    ),
}


def build_prompts(poi: dict) -> tuple[str, str, str]:
    name = (poi.get("name") or "").strip()
    name_low = name.lower()
    category = (poi.get("category") or "HISTORICAL_BUILDINGS").upper()
    subcategory = poi.get("subcategory") or ""

    cat_entry = CATEGORY_DEFAULTS.get(category) or CATEGORY_DEFAULTS["HISTORICAL_BUILDINGS"]
    result = dict(cat_entry)

    if subcategory and subcategory in SUBCATEGORY_SCENES:
        for k, v in SUBCATEGORY_SCENES[subcategory].items():
            result[k] = v

    for key, over in KEYWORD_SCENES:
        if key in name_low:
            for k, v in over.items():
                result[k] = v
            break

    for key, over in FAMOUS_OVERRIDES.items():
        if key in name_low:
            for k, v in over.items():
                result[k] = v
            break

    return result["cat_scene"], result["exterior"], result["monster"]


if __name__ == "__main__":
    test = [
        {"id": "witch_house", "name": "The Witch House", "category": "HISTORICAL_BUILDINGS"},
        {"id": "ghost_tours", "name": "Candlelit Ghostly Walking Tours", "category": "TOUR_COMPANIES"},
        {"id": "hex", "name": "HEX: Old World Witchery", "category": "WITCH_SHOP"},
        {"id": "bungh", "name": "Bunghole Liquors", "category": "SHOPPING"},
    ]
    for p in test:
        c, e, m = build_prompts(p)
        print(f"\n=== {p['name']} ({p['category']}) ===")
        print(f"  CAT:      {c[:100]}")
        print(f"  EXTERIOR: {e[:100]}")
        print(f"  MONSTER:  {m[:100]}")
