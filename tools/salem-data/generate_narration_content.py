#!/usr/bin/env python3
"""
Generate narration content SQL for Wave 1 POIs (113 most tourist-relevant POIs).

Wave 1 categories: witch_museum, witch_shop, psychic, ghost_tour,
    haunted_attraction, museum, historic_site, public_art, cemetery,
    tour, visitor_info

Reads narration-priority-pois.json, writes SQL UPDATE statements
to populate short_narration and long_narration fields.

POIs that already have narrations in SalemPois.kt are adapted from
those existing narrations rather than duplicated.
"""

import json
import os
import textwrap

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
INPUT_FILE = os.path.join(SCRIPT_DIR, "narration-priority-pois.json")
OUTPUT_FILE = os.path.join(
    SCRIPT_DIR, "..", "..", "salem-content", "narration_content.sql"
)

WAVE1_CATEGORIES = {
    "witch_museum",
    "witch_shop",
    "psychic",
    "ghost_tour",
    "haunted_attraction",
    "museum",
    "historic_site",
    "public_art",
    "cemetery",
    "tour",
    "visitor_info",
}

# ═══════════════════════════════════════════════════════════════════════════════
# Narration content keyed by POI name (exact match from narration-priority-pois.json)
# ═══════════════════════════════════════════════════════════════════════════════

NARRATIONS = {}


def n(name, short, long_text):
    """Register a narration."""
    NARRATIONS[name] = {
        "short_narration": textwrap.dedent(short).strip(),
        "long_narration": textwrap.dedent(long_text).strip(),
    }


# ═══════════════════════════════════════════════════════════════════
# WITCH MUSEUMS (7)
# ═══════════════════════════════════════════════════════════════════

n("1692 Before and After, LLC",
  """
  Just ahead is 1692 Before and After, one of Salem's most comprehensive
  witch trials walking tours. This two-hour experience covers the full
  arc of the 1692 hysteria. Highly rated by visitors who want real
  history, not Hollywood fiction.
  """,
  """
  1692 Before and After offers what many consider the most thorough
  walking tour of the Salem witch trials available today. Over two
  hours, guides lead you through the streets of downtown Salem,
  stopping at key sites connected to the accusations, trials, and
  executions of 1692. The tour covers the full timeline. It starts
  with the social and religious tensions that set the stage, moves
  through the initial accusations in Salem Village, and follows the
  hysteria as it spread across Essex County. You will hear about
  specific accusers and accused. Tituba, the enslaved woman whose
  confession ignited the panic. Rebecca Nurse, the elderly grandmother
  whose conviction shocked the colony. Giles Corey, pressed to death
  for refusing to enter a plea. What sets this tour apart is its
  emphasis on what came before and after the trials. The guides
  explore the political, economic, and religious forces that made
  the hysteria possible, and the long aftermath of guilt, restitution,
  and memory that followed. Reservations are recommended, especially
  during October when Salem sees over a million visitors.
  """)

n("Salem Witch Board Museum",
  """
  You are approaching the Salem Witch Board Museum, the world's only
  museum dedicated to the Ouija board. Inside, you will find the
  history, mystery, and pop culture impact of this controversial
  tool of divination.
  """,
  """
  The Salem Witch Board Museum is a one-of-a-kind experience. It is
  the only museum in the world devoted entirely to the history and
  mystery of the Ouija board. The collection traces the board's origins
  from nineteenth-century parlor game to cultural phenomenon. You will
  learn how the board was patented in 1891, how it became a mainstream
  toy sold by Parker Brothers, and how it evolved into a symbol of the
  supernatural in horror films and popular imagination. The museum
  displays include vintage boards from different eras, boards from
  around the world, and boards connected to documented cases of the
  paranormal. Some exhibits explore the darker side of Ouija history,
  including stories of obsession, alleged hauntings, and even crimes
  attributed to messages received through the board. Whether you are
  a skeptic or a believer, the museum offers a fascinating look at
  how a simple piece of wood and cardboard has captivated and terrified
  people for over a century. Located on Essex Street in the heart of
  Salem's pedestrian mall, it fits perfectly into a day exploring the
  city's supernatural heritage.
  """)

# Adapted from SalemPois.kt existing narration
n("Salem Witch Museum",
  """
  The Salem Witch Museum occupies a former church at the edge of Salem
  Common. Inside, thirteen stage sets with life-size figures tell the
  story of the 1692 witch trials through light and narration. It is
  one of Salem's most iconic landmarks.
  """,
  """
  The Salem Witch Museum is one of the most visited attractions in
  Salem. Housed in a former mid-nineteenth century church, it uses
  thirteen stage sets with life-size figures to dramatize the events
  of the 1692 witch hysteria. The main presentation takes visitors
  through the accusations, the trials, and the executions, with
  particular focus on key figures like Tituba, Bridget Bishop, and
  Rebecca Nurse. A second exhibition called Witches: Evolving
  Perceptions traces the concept of the witch from biblical times
  through modern Wicca. The building itself, a Gothic Revival structure
  originally built in 1846, adds to the atmosphere. The museum has
  been operating since 1972 and has become one of Salem's most iconic
  landmarks, visible from across Salem Common with its distinctive
  pointed turrets. Open daily ten to five, with extended hours in
  July, August, and October. Adult admission is eighteen dollars.
  Children six to fourteen are thirteen fifty. Under six is free.
  Phone number is 978-744-1692. Their phone number actually ends
  in 1692. Hard to forget.
  """)

# Adapted from SalemPois.kt existing narration
n("Salem Witch Trials Memorial",
  """
  You are approaching the Salem Witch Trials Memorial. Dedicated in
  1992 on the three hundredth anniversary of the trials. Twenty stone
  benches, each inscribed with the name of a victim, line this
  quiet park.
  """,
  """
  The Salem Witch Trials Memorial stands as a solemn tribute to the
  twenty innocent people executed during the witch hysteria of 1692.
  Designed by architect James Cutler and artist Maggie Smith, it was
  dedicated on the tercentenary of the trials in August 1992. Nobel
  Laureate Elie Wiesel delivered the dedication speech. Twenty granite
  benches jut from the stone walls, each bearing the name, method,
  and date of execution of a victim. The entrance features the
  victims' protests of innocence, their words literally cut off
  mid-sentence by the stone walls. Locust trees cast dappled shadows
  over the space, chosen for their historical association with the
  gallows. This memorial serves as a permanent reminder of what
  happens when fear, intolerance, and injustice go unchecked. The
  memorial is free and open from dawn to dusk. It sits on Liberty
  Street, adjacent to the Charter Street Cemetery. Please treat it
  with respect. No flowers, candles, or pets are permitted on the
  memorial grounds.
  """)

# Adapted from SalemPois.kt existing narration
n("The Witch House",
  """
  This is the Witch House, the only structure still standing with
  direct ties to the Salem witch trials of 1692. Judge Jonathan
  Corwin lived here and conducted preliminary examinations of
  accused witches.
  """,
  """
  The Witch House is the only remaining building in Salem with
  direct connections to the witchcraft trials of 1692. Built around
  1675, it was the home of Judge Jonathan Corwin, one of the
  magistrates who conducted preliminary examinations of accused
  witches. Several of these examinations likely took place in this
  very house. Corwin served on the Court of Oyer and Terminer that
  tried and convicted the accused. The house has been restored to
  its seventeenth-century appearance and serves as a museum of life
  in the late sixteen hundreds. Period furnishings, apothecary items,
  and architectural details give visitors a window into Puritan daily
  life. The building was nearly demolished in the 1940s but was saved
  and moved slightly from its original location to accommodate road
  widening. Open daily from mid-March through November, ten to five.
  Last entry at four forty-five. Admission is twelve dollars for
  adults, ten for seniors and students, eight for children six to
  fourteen.
  """)

# Adapted from SalemPois.kt existing narration
n("Witch Dungeon Museum",
  """
  The Witch Dungeon Museum features a live reenactment of a 1692
  witch trial, adapted from actual court transcripts. After the
  show, you can tour the recreated dungeon where accused witches
  were held.
  """,
  """
  The Witch Dungeon Museum brings the Salem witch trials to life
  through award-winning live theater. Professional actors perform
  a reenactment of the 1692 examination of Sarah Good, drawn
  directly from surviving court transcripts. After the performance,
  guides lead you through a recreation of the dungeon where accused
  witches were held in appalling conditions. The real dungeons of
  1692 were overcrowded, dark, and cold. Prisoners were chained to
  the walls and charged for their own food and shackles. Several
  accused died in custody before ever reaching trial, including
  Sarah Good's infant daughter, who perished in the jail. The museum
  is located on Lynde Street, just steps from Salem Common. Open
  daily ten to five, April through November, with extended hours in
  October. Adult admission is fourteen dollars. Children are twelve
  dollars. This is one of the more visceral witch trial experiences
  in Salem. You will hear the actual words spoken in 1692.
  """)

n("Witch History Museum",
  """
  Up ahead is the Witch History Museum. Inside, guided tours walk
  you through life-sized scenes depicting the untold stories of the
  1692 witch trials. This museum focuses on the human drama behind
  the hysteria.
  """,
  """
  The Witch History Museum offers guided tours through life-sized
  scenes that depict key moments of the Salem witch trials. Unlike
  some of Salem's more theatrical attractions, this museum aims for
  historical depth, focusing on the personal stories and social
  dynamics that drove the accusations. The scenes cover the full
  timeline from the initial afflictions in Salem Village through the
  trials and executions. Guides provide context about Puritan society,
  the role of religion, and the political tensions between Salem
  Village and Salem Town that helped fuel the crisis. The museum is
  located on Essex Street at number 197. Open daily ten to five,
  April through November, with extended hours in October. This is a
  good choice for visitors who want a more intimate, narrative-driven
  experience of the witch trials story. The guided format means you
  can ask questions and engage with the material at your own pace.
  """)


# ═══════════════════════════════════════════════════════════════════
# WITCH SHOPS (17)
# ═══════════════════════════════════════════════════════════════════

n("Artemisia Botanicals",
  """
  Artemisia Botanicals is just ahead. Step into an apothecary filled
  with over four hundred herbs, a hundred teas, essential oils, and
  handmade products for health, beauty, and magic. Tarot and tea leaf
  readings are available.
  """,
  """
  Artemisia Botanicals is one of Salem's most authentic herbal
  apothecaries. The shop carries over four hundred dried herbs, more
  than a hundred varieties of tea, essential oils, handmade soaps,
  candles, and other botanical products crafted for health, beauty,
  and magical practice. The space feels like stepping into a
  traditional herbalist's workshop. The air is thick with the scent
  of dried flowers, roots, and resins. For practitioners of modern
  witchcraft, Wicca, or herbal medicine, Artemisia is a working
  supply shop, not just a tourist attraction. Tarot and tea leaf
  readings are available from experienced readers on staff. There
  is a certain historical irony to a thriving herb shop in Salem.
  In 1692, knowledge of herbs and healing was used as evidence of
  witchcraft. Ann Pudeator, a widow and healer, was hanged in part
  because her medical skills were seen as suspicious. Today, that
  same herbal tradition is celebrated openly on the streets where
  the accused once walked. Located at 3 Hawthorne Boulevard.
  """)

n("Black Veil Shoppe for the Grim Hearted",
  """
  Black Veil Shoppe is right next to the Witch House. This is Salem's
  destination for dark fine art, gothic apparel, and home decor
  inspired by the mysteries of life, death, and nature.
  """,
  """
  Black Veil Shoppe for the Grim Hearted sits in the shadow of the
  Witch House, Salem's most historically significant building. The
  shop specializes in fine art, apparel, and home decor that draws
  inspiration from the darker side of beauty. Think Victorian mourning
  aesthetics, gothic romanticism, and the natural cycles of life and
  death. The merchandise is curated with an artist's eye. You will
  find original artwork, handcrafted jewelry, clothing that ranges
  from subtly dark to dramatically gothic, and home goods that bring
  a touch of the macabre to everyday living. The shop's location at
  304 Essex Street makes it an easy stop before or after visiting the
  Witch House next door. Salem has always attracted those drawn to
  the edges of conventional culture. The city's dark history, its
  literary connections through Hawthorne and Poe, and its vibrant
  modern witch community create a natural home for businesses like
  Black Veil that celebrate the beauty in darkness.
  """)

n("Botanica of Salem",
  """
  Botanica of Salem is just ahead at 272 Essex Street. This shop is
  dedicated to preserving the sacred knowledge of mystery traditions,
  offering occult supplies, altar tools, and magical art.
  """,
  """
  The Botanica and Hermetic Arts of Salem is dedicated to preserving
  the sacred knowledge of ancient mystery traditions. The shop offers
  a wide range of occult science supplies, including altar tools,
  magical art, books, candles, ritual cords, crystals, herbal
  preparations, incense, magical jewelry, powders, potions, and
  statuary. The term botanica has roots in Latin American spiritual
  traditions, where it refers to a shop selling herbs, candles, and
  religious items for folk magic and healing practices. In Salem,
  the Botanica bridges these traditions with Western ceremonial magic,
  Hermeticism, and modern witchcraft. For serious practitioners, this
  is a resource shop with depth. The staff is knowledgeable about
  magical traditions and can guide visitors who are new to the craft
  as well as experienced practitioners looking for specific supplies.
  Located at 272 Essex Street, the same address where the Witch House
  stands nearby, a reminder that the practice of magic in Salem has
  come a long way from 1692.
  """)

n("Crow Haven Corner",
  """
  Crow Haven Corner is Salem's oldest witch shop. Owned by Lorelei,
  known as the Famous Love Witch, this Essex Street institution has
  been serving the magical community for decades. Tarot readings
  and love spells are specialties.
  """,
  """
  Crow Haven Corner holds the distinction of being Salem's oldest
  witch shop, a title that carries real weight in a city with this
  much magical heritage. The shop is owned by Lorelei, known in the
  media as the Famous Love Witch, who has been featured on The
  Bachelorette, in the Wall Street Journal, and in the Boston Globe.
  Inside you will find candles, books, spell kits, perfumes, incense,
  crystals, and tarot cards. But the real draw for many visitors is
  the team of gifted readers. Candace, Nina, Kimberly, Dianne,
  Nickolas, and Jesse offer consultations specializing in tarot,
  mediumship, palmistry, and other forms of divination. The shop
  also supports Salem Saves Animals, a nonprofit. Proceeds from a
  dedicated room in the store go directly to animal rescue. Located
  at 125 Essex Street, Crow Haven Corner is one of those places that
  feels authentically Salem. It is not just a tourist shop. It is a
  working part of the city's living magical community.
  """)

n("Enchanted",
  """
  Enchanted is a magical gift shop on Wharf Street. Browse crystals,
  mystical gifts, and Salem souvenirs in a space that captures the
  spirit of the Witch City. Perfect for finding something special
  to take home.
  """,
  """
  Enchanted sits along Wharf Street near Pickering Wharf, one of
  Salem's most popular waterfront shopping areas. The shop offers a
  curated selection of mystical and magical gifts, crystals, jewelry,
  and Salem-themed souvenirs. Wharf Street is lined with witch shops,
  boutiques, and restaurants, making it a natural corridor for
  visitors exploring Salem's waterfront. The area around Pickering
  Wharf was once part of Salem's working waterfront, where ships
  from the East Indies and China unloaded exotic cargo that made
  Salem one of the richest ports in America. Pepper from Sumatra,
  silk from Canton, porcelain from Jingdezhen. The wharves bustled
  with sailors, merchants, and customs inspectors. Today the wharves
  have been transformed into a vibrant shopping and dining district,
  but the maritime character remains in the architecture and the
  harbor views. You can still smell the salt air and hear the
  seagulls as you browse. Enchanted is a pleasant stop while walking
  the waterfront area between Derby Street and the harbor. The shop
  captures that feeling of discovery that has defined Salem's
  waterfront for four centuries. Different cargo, same sense of
  wonder.
  """)

n("Gallows Hill Witchery\u2019s Downtown Sanctuary",
  """
  Gallows Hill Witchery's Downtown Sanctuary is just ahead on Lynde
  Street. This extension of the Gallows Hill brand offers magical
  supplies and an immersive shopping experience right in the heart
  of downtown Salem.
  """,
  """
  Gallows Hill Witchery's Downtown Sanctuary is located at 6 Lynde
  Street, near Salem Common and the cluster of witch trial attractions
  on Lynde Street. The shop is an extension of the Gallows Hill
  brand, bringing magical supplies, ritual tools, and an immersive
  atmosphere to downtown Salem. The name Gallows Hill references the
  location long believed to be the execution site of the 1692 witch
  trials. For centuries, a hill near this name was assumed to be
  where the hangings took place. In 2016, researchers confirmed that
  the actual execution site was Proctor's Ledge, a rocky outcropping
  nearby. But the name Gallows Hill remains embedded in Salem's
  geography and imagination. The shop's location on Lynde Street
  places it in good company. The Witch Dungeon Museum and other
  witch trial attractions are just steps away, making this a natural
  stop while exploring this part of Salem.
  """)

n("Haus Witch",
  """
  There is a duplicate listing here. HausWitch Home and Healing at
  144 Washington Street is just ahead. See the full entry under that
  name for the complete experience.
  """,
  """
  Haus Witch is an alternate listing for HausWitch Home and Healing,
  located at 144 Washington Street. This modern metaphysical lifestyle
  brand combines earth magic, meditation, herbalism, and interior
  design. The shop provides Salem locals and visitors with a curated
  selection of witchy and handmade products from independent makers
  across New England and beyond. HausWitch is proudly inclusive,
  welcoming all genders, sexualities, ethnicities, and abilities.
  The shop also offers aura photography readings, where a custom-built
  photo booth captures the colors of your personal energy field. Each
  session includes a fifteen-minute interpretation and a keepsake
  photo. Whether you are a practicing witch or simply curious about
  the metaphysical, HausWitch offers an approachable, stylish entry
  point into magical living. The aesthetic is modern and clean, a far
  cry from the stereotypical dark and dusty occult shop.
  """)

n("HausWitch Home + Healing",
  """
  HausWitch Home and Healing is just ahead at 144 Washington Street.
  This modern metaphysical shop blends earth magic, herbalism, and
  interior design. They also offer aura photography. Very Instagram-
  friendly.
  """,
  """
  HausWitch Home and Healing is a modern metaphysical lifestyle
  brand and shop at 144 Washington Street. Founded on the principles
  of earth magic, meditation, herbalism, and interior decorating,
  HausWitch brings magic and healing into everyday spaces. The shop
  carries a carefully curated selection of products from independent
  makers across New England and the United States. Crystals, candles,
  herbal preparations, books, altar supplies, and beautifully designed
  home goods fill the shelves. HausWitch is intentionally inclusive,
  welcoming all genders, sexualities, ethnicities, and abilities.
  It creates a supportive and safe environment for anyone exploring
  spiritual practices. One of their most popular offerings is the
  aura photography experience. Step into the custom-built photo booth
  and see your energy field captured in vivid color. Each session
  includes a fifteen-minute interpretation and a keepsake Fuji Instax
  photo. HausWitch represents a new generation of Salem's magical
  community. Stylish, accessible, and rooted in genuine practice.
  """)

n("The Cauldron Black",
  """
  The Cauldron Black is on Wharf Street. This occult shop specializes
  in handcrafted magical supplies and ritual tools for practicing
  witches and curious visitors alike.
  """,
  """
  The Cauldron Black sits at 65 Wharf Street in the Pickering Wharf
  area, Salem's waterfront shopping district. The shop specializes in
  occult goods, handcrafted magical supplies, and ritual tools. For
  practicing witches and pagans, this is a resource shop with depth.
  The inventory includes candles charged for specific intentions,
  herbal blends, incense, crystals, and ceremonial tools. For visitors
  new to magical practice, the staff can guide you through the
  basics. Wharf Street has become one of Salem's densest
  concentrations of witch shops, psychic parlors, and metaphysical
  boutiques. The area sits just steps from Derby Street and the
  waterfront, making it easy to combine a visit to The Cauldron
  Black with a walk along the harbor or a stop at the nearby
  Salem Maritime National Historical Park. The shop's name evokes
  the classic image of a witch's cauldron, an image that connects
  centuries of folklore with Salem's very real and very active modern
  magical community.
  """)

n("The Good Witch of Salem",
  """
  The Good Witch of Salem is at 2 North Street. Once a children's
  shop, it has evolved into a magical space for adults and teens,
  with crystals, jewelry, and treasures that inspire self-expression.
  """,
  """
  The Good Witch of Salem at 2 North Street started life as a
  children's shop and has evolved into a magical space for adults
  and teens. The shop offers crystals, jewelry, and carefully chosen
  treasures designed to inspire self-expression and nurture what
  the owners call your inner child magic. The name plays on a
  familiar cultural reference. In Salem, where the word witch
  carries the weight of 1692, claiming the title of Good Witch
  is both playful and deliberate. It reclaims the word, stripping
  away the fear and persecution and replacing it with something
  positive and empowering. The shop sits near the intersection of
  North Street and Essex Street, in the heart of Salem's pedestrian
  shopping district. It is a short walk from the Witch House and
  several other witch shops, making it easy to include in a walking
  tour of Salem's magical retail scene. The atmosphere is warm and
  welcoming, designed to make visitors feel comfortable regardless
  of their experience with witchcraft or spiritual practice.
  """)

n("The Lost Library: A Peculiar Gift Shop at the Witch Village",
  """
  The Lost Library is ahead at 282 Derby Street. This is not just a
  shop. It is an experience. Explore a witch's cottage, a curiosity
  corner, and Salem's largest glowing pumpkin.
  """,
  """
  The Lost Library is a peculiar gift shop at the Witch Village on
  Derby Street. The experience goes beyond browsing shelves. You will
  walk through a witch's cottage, discover a curiosity corner filled
  with oddities, and encounter Salem's largest glowing pumpkin. The
  concept is immersive shopping. You do not just buy things here.
  You explore. The Lost Library is part of the larger Witch Village
  complex at 282 Derby Street, which includes multiple attractions
  and experiences. This stretch of Derby Street, between the
  waterfront and the Charter Street Cemetery, is one of the most
  heavily trafficked tourist corridors in Salem. The Witch Village
  has been a fixture of Salem's Halloween season for decades, offering
  a cluster of witch-themed experiences in one location. The Lost
  Library adds a gentler, more whimsical dimension to the complex.
  It is family-friendly and appeals to visitors who want a touch of
  magic without the scares. Good for souvenir shopping with a sense
  of adventure.
  """)

n("The Ossuary",
  """
  The Ossuary is Salem's gothic boutique on Wharf Street. One-of-a-
  kind evening gowns, wedding attire, corsetry, and dark fashion
  that is handmade and unforgettable.
  """,
  """
  The Ossuary at 77 Wharf Street is Salem's premier gothic fashion
  boutique. The shop specializes in one-of-a-kind evening gowns,
  wedding attire, cocktail outfits, and corsetry. Everything is
  handmade or produced in small batches, making each piece a unique
  work of wearable art. The name ossuary refers to a repository for
  bones, a fitting choice for a shop that finds beauty in the dark
  and dramatic. The aesthetic draws from Victorian mourning fashion,
  gothic romance, and the dark elegance that Salem naturally inspires.
  For visitors attending Salem's many evening events, ghost tours,
  or October festivities, The Ossuary offers clothing that makes
  a statement. For those planning a gothic or alternative wedding,
  this is a destination worth the trip to Salem on its own. The
  shop's Wharf Street location places it in the heart of Pickering
  Wharf, surrounded by restaurants, galleries, and other unique
  boutiques. Follow them on Instagram at salemossuary to preview
  their latest creations.
  """)

n("The Witchery Studio Space",
  """
  The Witchery Studio Space is at 61 Wharf Street. This creative
  space combines magical retail with immersive experiences, workshops,
  and ritual theater.
  """,
  """
  The Witchery Studio Space at 61 Wharf Street is a multifaceted
  creative space that combines retail shopping with immersive
  experiences. The Witchery brand encompasses several Salem
  businesses, including the Tarot Cove reading room and the Craft
  Witch DIY studio. The Studio Space itself serves as a hub for
  these interconnected experiences. Workshops, ritual theater,
  and special events bring the space to life throughout the year.
  The Wharf Street location places it in the center of one of
  Salem's most vibrant shopping districts, steps from the harbor
  and surrounded by other witch shops, restaurants, and galleries.
  The Witchery represents Salem's entrepreneurial side. The city's
  witch identity, born from tragedy in 1692, has been transformed
  into a thriving creative economy. Shops like The Witchery are
  run by genuine practitioners who bring authenticity, artistry,
  and community to what could easily be just tourist kitsch.
  """)

n("Witch & Fairy Emporium",
  """
  Witch and Fairy Emporium is on Essex Street. Browse magical gifts,
  fairy-themed treasures, and witch-inspired souvenirs in this
  whimsical shop.
  """,
  """
  The Witch and Fairy Emporium at 178 Essex Street sits on Salem's
  pedestrian mall, the heart of the city's shopping district. The
  shop offers a blend of witch-themed and fairy-themed gifts,
  souvenirs, and magical supplies. The combination of witches and
  fairies in one shop reflects the broad spectrum of magical
  traditions that converge in Salem. While the witch trials of 1692
  give Salem its most famous association with the supernatural, the
  city's magical community draws from many traditions. Celtic fairy
  lore, modern Wicca, folk magic, and nature spirituality all find
  expression in Salem's shops and spiritual community. The Essex
  Street pedestrian mall is the main artery of downtown Salem. On
  busy days, especially in October, this stretch is packed with
  visitors. The Emporium makes a pleasant stop while walking the
  mall between the Peabody Essex Museum and the Witch House.
  """)

n("Witch City Broom Company",
  """
  Witch City Broom Company is at 246 Essex Street. They handcraft
  signature witch brooms and offer DIY broom kits. Every witch
  needs a good broom.
  """,
  """
  The Witch City Broom Company at 246 Essex Street is one of Salem's
  more distinctive shops. They specialize in handcrafted witch brooms
  and DIY broom kits, allowing you to take a piece of Salem's magic
  home in a uniquely personal way. Beyond the signature brooms, the
  shop carries handcrafted candles, stylish apparel, books,
  stationery, seasonal decor, and other gifts. Everything is
  carefully curated, with an emphasis on local and regional artisans.
  The witch's broom, or besom, has deep roots in European folk
  tradition. Besoms were used in purification rituals long before
  they became the stereotypical mode of witch transportation in
  popular culture. In modern Wiccan and pagan practice, a besom
  is used to cleanse sacred space before ritual. The Witch City
  Broom Company bridges tradition and whimsy. Whether you are a
  practitioner looking for a ritual tool or a visitor looking for
  the perfect Salem souvenir, a handcrafted witch broom is hard
  to beat.
  """)

n("Witch Dr.",
  """
  Witch Dr. is at 109 Lafayette Street. This shop offers a unique
  blend of Salem's witchy culture with a modern twist, south of the
  main tourist district.
  """,
  """
  Witch Dr. is located at 109 Lafayette Street, slightly south of
  Salem's main tourist corridor on Essex and Derby Streets. The shop
  occupies a space on the edge of the downtown district, offering
  visitors a chance to explore beyond the most heavily trafficked
  areas. Lafayette Street has its own character, with a mix of local
  businesses and residential neighborhood feel that contrasts with
  the tourist density of Essex Street. The name Witch Dr. plays on
  Salem's identity as the Witch City while suggesting healing and
  transformation. Salem has always had this duality. The city carries
  the weight of the 1692 tragedy, but it has also become a place
  where people come seeking spiritual insight, personal transformation,
  and connection to traditions of healing and magic. A walk down
  Lafayette Street to Witch Dr. takes you through a side of Salem
  that most tourists miss, the residential streets where locals
  live alongside the city's famous history.
  """)

n("Witch Way Gifts",
  """
  Witch Way Gifts is nearby. Pick up Salem souvenirs, witch-themed
  gifts, and magical keepsakes to remember your visit to the
  Witch City.
  """,
  """
  Witch Way Gifts offers a selection of Salem souvenirs, witch-themed
  merchandise, and magical keepsakes. The shop caters to visitors
  looking for fun, accessible mementos of their time in Salem.
  Salem's gift shop scene reflects the city's unique identity. Where
  else can you buy a witch hat, a historically accurate book about
  Puritan jurisprudence, and a crystal ball all within a few blocks
  of each other? The sheer density of witch-themed retail in downtown
  Salem is remarkable. Within a quarter mile of Essex Street, there
  are more witch shops, occult boutiques, and magical supply stores
  than perhaps any other place in North America. Witch Way Gifts
  adds to this ecosystem with a focus on fun, approachable merchandise
  that works for visitors of all ages and levels of interest in the
  supernatural. Whether you need a last-minute gift or a quick
  souvenir, this is a convenient stop.
  """)


# ═══════════════════════════════════════════════════════════════════
# PSYCHIC (13)
# ═══════════════════════════════════════════════════════════════════

n("Angelique Renard at Hex",
  """
  Angelique Renard reads at Hex on Essex Street. She is a Salem
  Witch, High Priestess, and gifted psychic medium specializing in
  tarot, palmistry, and spirit communication.
  """,
  """
  Angelique Renard is a Salem Witch, High Priestess of the Salem
  Coven, and a gifted psychic medium who has been offering spiritual
  guidance since childhood. She reads at Hex: Old World Witchery at
  184 Essex Street, one of Salem's most well-known occult shops.
  Angelique specializes in tarot, palmistry, and spirit mediumship,
  offering seekers insight into their past, present, and future.
  The tradition of psychic reading in Salem connects to a broader
  history. In 1692, the ability to perceive things beyond normal
  sight was viewed with suspicion and fear. The afflicted girls who
  accused their neighbors of witchcraft claimed to see spectral
  visions. Today, that same gift of perception is offered as a
  service, celebrated rather than condemned. Readings with Angelique
  can be booked through Hex at 978-666-0765 or through the website.
  Walk-ins may be available depending on the day and season. October
  is the busiest month. Plan ahead.
  """)

n("Celestial Navigation Astrology & Wellness",
  """
  Celestial Navigation is at 254 Essex Street. This quiet healing
  space offers astrology, reiki, sound baths, tarot, workshops,
  and private tours. Appointments recommended.
  """,
  """
  Celestial Navigation Astrology and Wellness at 254 Essex Street
  offers a quieter, more contemplative spiritual experience than
  many of Salem's walk-in psychic parlors. The space blends intuitive
  insight with energy work, offering astrology readings, reiki
  healing, sound baths, tarot consultations, workshops, and private
  tours. The emphasis here is on clarity and renewal. Sessions are
  by appointment, which means less waiting and a more personalized
  experience. You can book online through their website. The name
  Celestial Navigation connects beautifully to Salem's maritime
  heritage. For centuries, Salem's sea captains navigated by the
  stars, using celestial observation to guide their ships across
  oceans. Nathaniel Bowditch, Salem's most famous navigator, wrote
  The American Practical Navigator in 1802, which remains the
  global standard. Celestial Navigation the shop carries this
  tradition forward, using the positions of celestial bodies to
  guide personal journeys rather than ocean voyages.
  """)

n("Fatima\u2019s Psychic Studio",
  """
  Fatima's Psychic Studio is nearby on Essex Street. Drop in for a
  reading from one of Salem's many practicing psychics. Walk-ins
  often welcome.
  """,
  """
  Fatima's Psychic Studio is one of many reading rooms clustered
  along Essex Street in downtown Salem. Psychic readings are deeply
  woven into Salem's modern identity. While the witch trials of 1692
  were driven by fear of the supernatural, today Salem embraces the
  mystical. Dozens of psychics, mediums, and tarot readers operate
  in the city, offering everything from quick card readings to deep
  mediumship sessions. The concentration of psychic practitioners in
  Salem is remarkable. Within a few blocks of Essex Street, you can
  find tarot, palmistry, astrology, crystal ball gazing, aura
  photography, past life regression, and spirit communication. For
  visitors, a psychic reading is one of Salem's quintessential
  experiences. Whether you approach it as entertainment, spiritual
  practice, or genuine seeking, the experience is uniquely Salem.
  Walk-in readings are often available, but during busy periods,
  especially October weekends, advance booking is recommended.
  """)

n("Hex: Old World Witchery",
  """
  Hex: Old World Witchery is at 246 Essex Street. This legendary
  Salem shop is run by practicing witches who handcraft spells,
  candles, and ritual tools. Readings and seances available.
  """,
  """
  Hex: Old World Witchery is one of Salem's most established and
  well-known occult shops. Run by Christian Day, Brian Cain, and
  their circle of practicing witches, Hex is a place where magic
  is not a metaphor. The shop offers handcrafted candles, oils,
  incense, soaps, potent charms, and gris-gris bags made by genuine
  practitioners. The practitioners at Hex honor the old gods, speak
  to spirits, and conjure real change for their clients. The shop
  also hosts psychic readings, seances, and special events throughout
  the year. Hex is located at 246 Essex Street, sharing the address
  with several related businesses including the Psychic Spa, Omen
  Psychic Parlor, and the Salem Seance experience. This cluster of
  related businesses creates a kind of magical campus on Essex
  Street. For visitors looking for an authentic encounter with Salem's
  living witch community, Hex delivers. These are not actors playing
  a role. They are practitioners whose craft is their daily life.
  Phone 978-666-0765.
  """)

n("Live Spellcasting: Within the Witching Hour",
  """
  Live Spellcasting is at 282 Derby Street. Join a practicing witch
  in a live ritual and cast your own spell. This interactive
  experience is great for all ages.
  """,
  """
  Live Spellcasting: Within the Witching Hour offers one of Salem's
  most interactive spiritual experiences. At 282 Derby Street, inside
  a black box theater, a practicing witch leads you through a live
  ritual where you cast your own spell. The experience is designed
  to be immersive and participatory. You are not a passive observer.
  You become part of the ceremony. The show is appropriate for all
  ages, making it a good option for families visiting Salem who want
  a magical experience without the scares of the haunted attractions.
  Open during the summer season and October. The spellcasting
  experience is part of the Witch Village complex on Derby Street,
  which offers multiple witch-themed attractions and shops in one
  location. Whether you believe in the power of spellwork or simply
  enjoy the theater of it, the Witching Hour provides a memorable
  and unique Salem experience. Advance tickets are recommended during
  busy periods.
  """)

n("Omen: Psychic Parlor & Witchcraft Emporium",
  """
  Omen Psychic Parlor is at 184 Essex Street. Step into one of
  Salem's most atmospheric reading rooms for tarot, mediumship, and
  witchcraft consultations.
  """,
  """
  Omen: Psychic Parlor and Witchcraft Emporium occupies space at
  184 Essex Street, part of the Hex family of businesses that form
  one of Salem's most significant clusters of magical practice.
  The parlor offers psychic readings, tarot consultations, and
  mediumship sessions in an atmospheric setting designed to enhance
  the experience. The name Omen suggests messages from the
  universe, signs that reveal hidden truths. In Puritan Salem,
  omens were taken very seriously. Unusual events, strange animal
  behavior, or unsettling dreams could all be interpreted as evidence
  of witchcraft or divine displeasure. Today, the practitioners at
  Omen channel that same sensitivity to signs and symbols in a
  positive direction, helping clients find clarity, healing, and
  connection. Walk-ins may be available, but booking in advance is
  recommended, especially during Salem's busy season from September
  through November. Phone 978-666-0763.
  """)

n("Personal Rainbow Aura Photography",
  """
  Personal Rainbow Aura Photography is at HausWitch on Washington
  Street. Step into the photo booth, see your energy field in color,
  and take home a keepsake photo with interpretation.
  """,
  """
  Personal Rainbow Aura Photography is offered at HausWitch Home
  and Healing at 144 Washington Street. The custom-built aura photo
  booth captures the colors of your personal energy field using
  specialized sensors and cameras. Each session includes a fifteen-
  minute interpretation of your aura colors and a keepsake Fuji
  Instax Wide film photo. The booth accommodates two people or just
  one. Aura photography has roots in the concept of the human energy
  field, a belief shared by many spiritual traditions that the body
  radiates an electromagnetic field that can reveal information about
  emotional, physical, and spiritual states. Different colors are
  associated with different qualities. Blue might indicate calm and
  communication. Red suggests passion and energy. Purple points to
  spiritual awareness. For visitors looking for a unique Salem
  souvenir beyond the standard T-shirt and magnet, an aura photo
  is hard to beat. It is personal, memorable, and makes for a great
  conversation starter when you get home.
  """)

n("Psychic Spa",
  """
  The Psychic Spa is at 184 Essex Street. Go beyond divination into
  transformation with guided sessions of breath work, energy healing,
  and trance states.
  """,
  """
  The Psychic Spa at 184 Essex Street takes the concept of psychic
  reading beyond traditional divination into the realm of
  transformation. Here, psychic practitioners guide you through
  sessions involving breath work, energy healing, and trance states
  designed to restore balance, recall past lives, commune with
  ancestors, or raise shields of power. The approach is more
  experiential than a standard tarot reading. You recline, release,
  and receive, allowing the practitioner to work with your energy
  in a therapeutic way. The Psychic Spa is part of the Hex family
  of businesses at 184 Essex Street. The spa concept reflects a
  broader trend in Salem's spiritual community toward integrating
  traditional psychic arts with modern wellness practices. The
  result is something that appeals to visitors who might be
  interested in meditation, yoga, or energy work but have never
  considered visiting a psychic. Advance booking is recommended.
  Phone 978-666-0765.
  """)

n("Salem Center for Past Life Regression",
  """
  The Salem Center for Past Life Regression is nearby. Experienced
  therapist Susan offers regression sessions to explore past lives
  and ancestral healing. By appointment.
  """,
  """
  The Salem Center for Past Life Regression offers a deeply personal
  spiritual experience. Susan, the center's therapist, is an
  experienced Past Life Regression and Ancestral Healing practitioner
  who earned her certification from Woolger Training International
  in 2012. She has trained with world-famous master-level regression
  therapy trainers from the United Kingdom, Portugal, Brazil, and the
  United States. Her approach integrates humor with a profound
  understanding of metaphysics, psi phenomena, and holistic health.
  Past life regression involves guided relaxation and focused
  attention to access memories that practitioners believe come from
  previous incarnations. The therapeutic aim is healing and
  understanding, not entertainment. Sessions are by appointment.
  Contact pastliferegressionsalem at gmail. There is something
  fitting about past life work in Salem. A city haunted by its own
  past, where the echoes of 1692 still reverberate in every street
  and every story. Here, exploring the past is not just an academic
  exercise. It is a living practice.
  """)

n("Salem S\u00e9ance",
  """
  Salem Seance is at Hex on Essex Street. Experience an authentic
  seance where gifted psychic mediums connect with spirits and
  loved ones who have crossed over.
  """,
  """
  Salem Seance offers an authentic seance experience in the Spirit
  Parlor of Hex at 184 Essex Street. Gifted psychic mediums guide
  the session, connecting with loved ones who have crossed over and
  with spirits who inhabit Salem's layered history. The seance
  tradition has deep roots in American culture. The spiritualist
  movement of the mid-nineteenth century made spirit communication
  mainstream, and seances became fashionable parlor entertainment
  across New England. Salem, with its history of spectral evidence
  and spirit testimony, is perhaps the most fitting city in America
  for this practice. The practitioners at Hex have helped thousands
  of people reunite with souls on the other side. Whether you
  approach the experience as a believer, a skeptic, or simply
  curious, the seance offers a unique and memorable encounter with
  Salem's spiritual traditions. Sessions are held regularly.
  Advance booking through Hex at 978-666-0763 is recommended,
  especially during October. Group sizes are limited to maintain
  the intimate atmosphere.
  """)

n("Sea Wych Salem",
  """
  Sea Wych Salem is at 1 Derby Square. Salem's only shop dedicated
  to sea and water magic. Discover handcrafted ritual tools,
  mermaid-inspired items, and ocean-themed divination.
  """,
  """
  The Sea Wych Salem at 1 Derby Square is Salem's only shop dedicated
  entirely to sea and water magic. In a city defined by its maritime
  heritage, this focus makes perfect sense. The shop offers
  handcrafted magic and ritual tools, beautifully curated items for
  bath, body, mind, and soul, a drop-in sea spell jar bar, sea witch
  ball workshops, and tarot and oracle readings. The concept of the
  sea witch connects two of Salem's defining identities. The maritime
  port city that grew rich from ocean trade, and the spiritual
  community that embraces magical practice. Salem's sailors once
  feared sea witches who could summon storms and curse voyages. Today,
  The Sea Wych reclaims that archetype as a source of empowerment
  and connection to the natural world. The shop's location in Derby
  Square puts it at the crossroads of Salem's historic waterfront
  and its downtown shopping district. It is a unique find even by
  Salem's standards. Phone 508-319-1885.
  """)

n("TAROT COVE: Reading Room & Gathering Space",
  """
  Tarot Cove is at 61 Wharf Street. This divination parlor offers
  private, couples, and group tarot readings. Also hosts ritual
  theater and special events.
  """,
  """
  Tarot Cove is The Witchery's divination parlor and gathering space
  at 61 Wharf Street. The space offers private, couples, and group
  tarot readings in an intimate setting. It is a popular choice for
  wedding parties, bachelorette groups, and friends' nights out
  looking for something uniquely Salem. Beyond readings, Tarot Cove
  hosts live ritual theater, creating immersive experiences that
  blend spiritual practice with dramatic performance. The tarot
  itself has a rich history dating back to the fifteenth century.
  Originally a card game in Renaissance Italy, the tarot evolved
  into a tool of divination and self-reflection over the following
  centuries. Today, tarot reading is one of the most popular and
  accessible forms of spiritual practice. In Salem, tarot readers
  are everywhere. But Tarot Cove distinguishes itself with its
  atmosphere, its connection to The Witchery's broader community of
  practitioners, and its flexibility for groups. Advance booking
  is recommended. Phone 978-914-8858.
  """)

n("Yulia Applewood at Hex: Old World Witchery",
  """
  Yulia Applewood reads at Hex on Essex Street. A natural born
  psychic medium with over twenty years of experience, she offers
  tarot, palmistry, runes, and spirit mediumship.
  """,
  """
  Yulia Applewood is a natural born psychic medium with over twenty
  years of experience, offering readings at Hex: Old World Witchery
  at 184 Essex Street. Yulia employs a wide range of techniques
  to focus her intuition on your specific needs, including tarot,
  clairvoyance, palmistry, runes, bone throwing, dowsing, and
  crystal ball gazing. Each technique validates intuited information
  and increases accuracy. Through mediumship, Yulia connects with
  the spirits of loved ones who have passed on, providing clients
  with validating information and messages from the spirit world.
  Her approach combines compassion and clarity, making the experience
  meaningful whether you are seeking guidance on practical matters
  or hoping to connect with someone on the other side. Yulia is
  one of several readers at Hex, each with their own specialties
  and gifts. The variety of practitioners means visitors can find
  a reader whose style and abilities match what they are looking for.
  Book through Hex at 978-666-0765.
  """)


# ═══════════════════════════════════════════════════════════════════
# GHOST TOURS (4)
# ═══════════════════════════════════════════════════════════════════

n("Candlelit Ghostly Walking Tours",
  """
  Candlelit Ghostly Walking Tours meets at 288 Derby Street. Light
  your own lantern and walk Salem's haunted streets. One of the
  longest-running walking tours in the city, celebrating over
  thirty years.
  """,
  """
  The Candlelit Ghostly Walking Tour is one of Salem's oldest and
  most atmospheric ghost tours, celebrating over thirty years of
  operation. You meet in the back parking lot of the Salem Wax Museum
  at 288 Derby Street, light your own lantern, and follow your guide
  through Salem's darkened streets for forty-five to sixty minutes.
  The tour visits approximately seven documented haunted historical
  sites, including the Joshua Ward House, Charter Street Cemetery,
  and the site of Bridget Bishop's apple orchard. Each stop combines
  the real history of the location with the ghost stories that have
  accumulated over centuries. The Joshua Ward House is particularly
  notable. Built on the site of Sheriff George Corwin's home, it is
  considered one of the most haunted buildings in Salem. Corwin was
  the man who carried out the witch trial executions and the pressing
  death of Giles Corey. Tours run Monday through Thursday at six and
  seven, and Friday through Sunday starting at five, every half hour,
  with the last tour at eight. Advance ticket purchase is strongly
  encouraged. AAA, military, and group discounts available.
  """)

n("Specters & Apparitions: A Ghost Hunting Tour",
  """
  Specters and Apparitions meets at 32 Derby Square. This is a ghost
  hunting tour with actual equipment. Use EMF readers and spirit
  boxes to search for paranormal activity at Salem's haunted hot
  spots.
  """,
  """
  Specters and Apparitions offers a ghost hunting tour that goes
  beyond storytelling. Meeting at 32 Derby Square, participants
  receive state-of-the-art ghost hunting equipment and visit Salem's
  most haunted locations to search for paranormal activity. This is
  hands-on ghost hunting, not passive listening. You will use EMF
  readers, spirit boxes, and other detection tools while your guide
  explains the history and reported hauntings at each location.
  The tour is operated by Witch City Walking Tours, which has
  consistently been rated among the top cultural and historical
  tours in the United States on TripAdvisor. Salem provides
  extraordinary raw material for ghost hunting. A city where twenty
  people were executed for witchcraft, where centuries-old cemeteries
  sit in the middle of downtown, and where historical buildings
  still stand on their original foundations. Whether the equipment
  picks up anything is part of the adventure. Call or text
  781-608-6986 for reservations.
  """)

n("Vampire Ghost Adventures",
  """
  Vampire Ghost Adventures meets at the Nathaniel Hawthorne Statue
  on Hawthorne Boulevard. Your guide is a vampire. You will visit
  places and hear tales that other tours skip.
  """,
  """
  Vampire Ghost Adventures offers a walking tour unlike any other in
  Salem. Meeting at the Nathaniel Hawthorne Statue at 20 Hawthorne
  Boulevard, your guide, who is a vampire, leads you through Salem's
  haunted streets telling tales of ghosts, witches, vampires, and
  poltergeists. This is the only walking tour in Salem where you
  actually enter a building and see haunted objects. The tour
  deliberately goes to places and tells stories that other tours
  skip, offering a different perspective on Salem's supernatural
  history. New England has its own rich vampire folklore, separate
  from the European tradition. In the eighteenth and nineteenth
  centuries, communities across Rhode Island, Connecticut, and
  Vermont exhumed bodies of the recently dead, believing they were
  feeding on the living. Salem's connection to these darker
  traditions adds another layer to an already complex supernatural
  heritage. If you do not see available tickets on the website, call
  to schedule a tour. The meeting point at the Hawthorne Statue is
  easy to find, right at the intersection of Hawthorne Boulevard
  and Essex Street.
  """)

n("Witch City Walking Tours",
  """
  Witch City Walking Tours meets at 32 Derby Square. Rated number
  one cultural and historical tour in the United States by
  TripAdvisor. Multiple years of excellence awards. Call or text
  for reservations.
  """,
  """
  Witch City Walking Tours has earned a remarkable distinction.
  Rated the number one cultural and historical tour in the United
  States by TripAdvisor in both 2023 and 2024, and number two in
  the world. That is not marketing hyperbole. It is verified
  traveler reviews from thousands of visitors. Tours depart from
  32 Derby Square and cover Salem's witch trial history, haunted
  locations, and cultural landmarks. The guides are knowledgeable
  and engaging, with a gift for making history feel immediate and
  personal. The company also offers the Specters and Apparitions
  ghost hunting experience for visitors who want a more hands-on
  paranormal adventure. Derby Square, where tours meet, is a
  historic space at the heart of downtown Salem. It sits near the
  old town hall and the pedestrian mall, making it easy to find
  and convenient for combining with other Salem activities. Call
  or text 781-608-6986 for reservations. Advance booking is
  strongly recommended, especially in October.
  """)


# ═══════════════════════════════════════════════════════════════════
# HAUNTED ATTRACTIONS (5)
# ═══════════════════════════════════════════════════════════════════

n("Chambers of Terror",
  """
  Chambers of Terror is at 57 Wharf Street. Salem's scariest haunt.
  Live monsters around every corner. Nothing will touch you, but
  they will get very close.
  """,
  """
  Chambers of Terror at 57 Wharf Street claims the title of Salem's
  scariest haunted attraction, and the reviews tend to back it up.
  Live actors in elaborate costumes and makeup lurk around every
  corner, delivering scares that are intense, immediate, and
  unrelenting. The attraction maintains a strict no-touch policy.
  The monsters will not physically contact you, but they will invade
  your personal space in ways that test your nerve. Open daily in
  September and October, with after-hours Fireside Ghost Stories
  available for those who want the atmosphere without the jump scares.
  Salem's haunted attraction scene has grown into one of the most
  concentrated in New England. During October, the city transforms
  into a massive Halloween celebration, with haunted houses, ghost
  tours, costume parades, and street performers competing for
  visitors' attention. Chambers of Terror stands out by focusing on
  old-school scares delivered by live performers rather than relying
  solely on animatronics and special effects. Tickets available on
  the website or by calling 973-876-2789.
  """)

n("Count Orlok\u2019s Nightmare Gallery",
  """
  Count Orlok's Nightmare Gallery is at 217 Essex Street. Salem's
  top-rated horror museum features life-sized creatures and
  characters from over a hundred years of horror films. A must for
  genre fans.
  """,
  """
  Count Orlok's Nightmare Gallery at 217 Essex Street is Salem's
  only horror museum, and it has consistently earned top ratings
  from visitors. The gallery features life-sized creatures and
  characters spanning over a hundred years of horror cinema, created
  by Hollywood special effects artists. Expect to encounter classics
  from Universal Monsters to modern horror icons, each rendered with
  movie-quality detail and artistry. The collection is ever-expanding,
  with new additions appearing regularly. The name references Count
  Orlok, the vampire from F.W. Murnau's 1922 silent film Nosferatu,
  one of the foundational works of horror cinema. The choice reflects
  the gallery's focus on horror as an art form, not just cheap scares.
  This is a museum for genre enthusiasts who appreciate the
  craftsmanship behind the creatures. A gift shop on-site offers
  horror-themed merchandise. The gallery is located on Essex Street's
  pedestrian mall, making it easy to combine with visits to Salem's
  witch shops, psychic parlors, and other attractions. This is one
  of Salem's best rainy-day options.
  """)

n("Gallows Hill Museum/Theatre",
  """
  Gallows Hill Museum and Theatre is at 7 Lynde Street. This
  combination museum and theater brings Salem's dark history to life
  through immersive exhibits and live performance.
  """,
  """
  Gallows Hill Museum and Theatre at 7 Lynde Street combines museum
  exhibits with live theatrical performance to tell the story of
  Salem's supernatural past. The location on Lynde Street places it
  near several other witch trial attractions, creating a cluster of
  experiences that visitors can explore on foot. The name Gallows
  Hill evokes one of Salem's most potent and haunting images. For
  centuries, a hill by that name was believed to be where the accused
  witches were hanged in 1692. The actual execution site was
  confirmed in 2016 as Proctor's Ledge, a different location. But
  the power of the name endures. The museum component features
  exhibits on the witch trials and Salem's broader history of the
  supernatural. The theater element adds live performance, bringing
  an immediacy that static exhibits cannot match. The combination
  makes for an experience that is both educational and visceral.
  Check with the venue for current show times and pricing. Phone
  978-825-0222.
  """)

n("Haunted Witch Village: Live Haunt Weekends",
  """
  The Haunted Witch Village is at 278 to 282 Derby Street. Salem's
  longest-running haunted house comes to life on weekends with live
  scares and fun. Part of the Witch Village complex.
  """,
  """
  The Haunted Witch Village is Salem's longest-running haunted house,
  located at 278 to 282 Derby Street as part of the larger Witch
  Village complex. On weekends, the attraction adds live scare actors
  to its exhibits, transforming the walk-through experience into
  something more intense and unpredictable. During the week, visitors
  can explore the Witch Village at a more relaxed pace. The complex
  includes multiple attractions, shops, and experiences all centered
  on Salem's witch identity. Open daily ten to eight during the
  season. The Witch Village has been a fixture of Salem's Derby
  Street corridor for decades. Its longevity speaks to the enduring
  appeal of the witch trials story and the city's embrace of its
  supernatural heritage. The live haunt weekends are particularly
  popular during October, when Salem's visitor numbers surge past
  a million. Combination tickets with other attractions in the
  complex are available. Discounts through the Halloween Pass and
  Wicked Special promotions. Check the website for details.
  """)

n("Witch Mansion Haunted House",
  """
  Witch Mansion is at 186 Essex Street on the pedestrian mall. This
  haunted house attraction delivers scares and thrills in the heart
  of Salem's busiest shopping street.
  """,
  """
  The Witch Mansion Haunted House at 186 Essex Street occupies prime
  real estate on Salem's pedestrian mall. The haunted house delivers
  a walk-through scare experience in the middle of one of the most
  heavily trafficked tourist corridors in the city. Essex Street's
  pedestrian mall stretches from the Witch House on the west end to
  the Peabody Essex Museum area on the east, and on busy days,
  especially October weekends, the street is packed with visitors.
  The Witch Mansion capitalizes on this foot traffic with an
  accessible, visible haunted attraction that draws in passersby.
  Salem's haunted attraction scene ranges from genuinely terrifying
  experiences designed for adults to family-friendly walk-throughs
  suitable for younger visitors. The Witch Mansion sits in this
  spectrum, offering an entertaining haunted house experience in a
  convenient central location. No need to trek to the edges of
  town. The scares come to you right on the main street.
  """)


# ═══════════════════════════════════════════════════════════════════
# MUSEUMS (11)
# ═══════════════════════════════════════════════════════════════════

# Adapted from SalemPois.kt
n("Custom House",
  """
  The Custom House is where Nathaniel Hawthorne worked as surveyor
  from 1846 to 1849. It was here, he claimed, that he discovered
  the scarlet letter that inspired his famous novel.
  """,
  """
  The Custom House stands as one of the most significant buildings
  within the Salem Maritime National Historical Park. Built in 1819,
  this Federal-era building served as the center of Salem's
  international trade operations, where customs inspectors weighed,
  measured, and taxed goods arriving from around the world. Its most
  famous occupant was Nathaniel Hawthorne, who worked here as
  surveyor from 1846 to 1849. In the introduction to The Scarlet
  Letter, Hawthorne wrote that he discovered an old scarlet cloth
  letter A in the attic of this very building, along with documents
  telling the story of Hester Prynne. Whether the discovery was
  real or literary invention, it provided the spark for one of the
  greatest American novels. Today the building contains exhibits on
  the tools and work of the Custom Service, as well as Hawthorne's
  office, preserved much as it would have looked during his tenure.
  Free admission as part of the Salem Maritime National Historical
  Park. Rangers are available to answer questions.
  """)

n("Derby House",
  """
  The Derby House on Derby Street was built in 1762 as a wedding
  present. It was home to Elias Hasket Derby, who became America's
  first millionaire through international trade.
  """,
  """
  The Derby House was built in 1762 as a wedding present for Elias
  Hasket Derby and Elizabeth Crowninshield Derby. They lived here
  for the first twenty years of their marriage. Elias Hasket Derby
  would go on to become one of the most important figures in
  American maritime history and is often cited as America's first
  millionaire. His fortune was built on international trade.
  Derby's ships sailed to the East Indies, China, and ports around
  the world, bringing back spices, silk, porcelain, and other
  exotic goods that made Salem one of the richest cities in the
  young republic. The house itself is a Georgian-style residence
  that reflects the prosperity and taste of Salem's merchant elite.
  It is part of the Salem Maritime National Historical Park and
  offers visitors a window into the domestic life of Salem's golden
  age of trade. The building stands on Derby Street, which was
  named for the family. Free admission with National Park Service
  ranger-led tours available seasonally.
  """)

n("Essex Institute Museum Building",
  """
  The Essex Institute Museum Building is on the Peabody Essex Museum
  campus. This historic structure was part of the Essex Institute,
  which merged with the Peabody Museum in 1992 to form PEM.
  """,
  """
  The Essex Institute Museum Building sits on the campus of the
  Peabody Essex Museum. The Essex Institute was founded in 1848 and
  served as Essex County's historical society, collecting documents,
  artifacts, and architectural records related to the region's
  history. In 1992, the Essex Institute merged with the Peabody
  Museum of Salem to form the Peabody Essex Museum, creating one
  of the oldest and most comprehensive museums in the United States.
  The Institute's collections, including extensive archives related
  to the Salem witch trials, became part of PEM's holdings. The
  building itself is an architectural landmark, reflecting the
  nineteenth-century commitment to preserving local history. The
  area around the museum campus, centered on Essex Street, has been
  the cultural heart of Salem for centuries. The original Essex
  Institute courthouse records include some of the primary documents
  from the 1692 witch trials, making this institution's collections
  invaluable to historians and researchers studying that period.
  """)

n("Halloween Museum",
  """
  The Halloween Museum is at 131 Essex Street. Explore the history
  and culture of Halloween in America, right in the city that has
  become its unofficial capital.
  """,
  """
  The Halloween Museum at 131 Essex Street celebrates the holiday
  that has become synonymous with Salem. If any city in America
  deserves a Halloween museum, it is this one. Salem's October
  transformation is legendary. Over a million visitors descend on
  the city each October, making it the de facto Halloween capital
  of the United States. The museum explores the history and
  evolution of Halloween, from its ancient Celtic origins as Samhain,
  when the veil between the living and the dead was believed to thin,
  through its transformation into the costume-and-candy holiday we
  know today. Salem's own relationship with Halloween is relatively
  recent. The city began embracing its witch trial heritage as a
  tourist attraction in the 1970s and 1980s. By the 1990s, the
  annual Haunted Happenings festival had grown into a month-long
  celebration that now draws visitors from around the world. The
  museum sits on Essex Street's pedestrian mall, surrounded by the
  witch shops and attractions that make Salem's October experience
  unique.
  """)

n("Hawkes House",
  """
  The Hawkes House on Derby Street was designed by Samuel McIntire,
  one of America's earliest and most influential architects. It was
  commissioned by Elias Hasket Derby but never lived in by him.
  """,
  """
  The Hawkes House on Derby Street was designed by Samuel McIntire,
  the self-taught woodcarver who became one of the most important
  architects of the Federal period in American history. The house
  was commissioned by Elias Hasket Derby, America's first
  millionaire, but Derby died before it was completed. The house
  eventually passed to other owners and takes its name from a
  later family. McIntire's work defines much of Salem's most
  beautiful architecture. His signature style combined elegant
  Federal proportions with exquisite carved ornament, creating
  buildings that remain among the finest in New England. The
  Hawkes House is part of the Salem Maritime National Historical
  Park, offering visitors a chance to see McIntire's work in
  context alongside the wharves, warehouses, and other structures
  that tell the story of Salem's maritime golden age. The building
  illustrates how Salem's extraordinary wealth from international
  trade translated into extraordinary architecture.
  """)

# Adapted from SalemPois.kt
n("Narbonne House",
  """
  The Narbonne House dates to around 1675, making it one of the
  oldest houses in Salem. It sits within the Salem Maritime National
  Historical Park and shows how ordinary families lived in colonial
  Salem.
  """,
  """
  The Narbonne House, built around 1675, is one of the oldest
  surviving houses in Salem and a rare example of a modest colonial
  dwelling. Unlike the grand merchant homes that dominate Salem's
  historic landscape, this house tells the story of working-class
  life in colonial New England. It was built for butcher Thomas Ives
  and later named after Sarah Narbonne, whose grandfather Jonathan
  Andrews purchased the house in 1780. Sarah was born here and lived
  here her entire life. Multiple generations of families lived here
  over more than three centuries, each leaving architectural layers
  that archaeologists and historians have carefully documented. The
  house sits within the Salem Maritime National Historical Park and
  provides a fascinating contrast to the nearby Custom House and
  grand wharf buildings. Its survival is remarkable. While wealthy
  merchants built and rebuilt in fashionable styles, this humble
  First Period house endured virtually unchanged. Free to view the
  exterior. Interior tours available seasonally through the National
  Park Service.
  """)

n("Pedrick Store House",
  """
  The Pedrick Store House sits at the foot of Derby Wharf. Originally
  built in Marblehead in 1770 by Thomas Pedrick, who used it to
  store captured cargo from British merchant vessels during the
  Revolution.
  """,
  """
  The Pedrick Store House at 1 Derby Wharf was originally built in
  Marblehead in 1770 by Thomas Pedrick, a patriot who commissioned
  privateers to capture British merchant vessels during the American
  Revolution. Captured cargo was likely stored in this very building.
  The store house was later moved to its current location at the foot
  of Derby Wharf, where it became part of the Salem Maritime National
  Historical Park. Privateering was a legal and highly profitable
  form of warfare in the eighteenth century. Private ship owners
  received letters of marque from the government authorizing them
  to seize enemy vessels. Salem was a major center of privateering
  during both the Revolution and the War of 1812. Over 150 privateers
  sailed from Salem during the Revolution alone. The Pedrick Store
  House is a tangible connection to this era, when the line between
  merchant, patriot, and privateer was blurred by the necessities
  of war.
  """)

n("Public Stores",
  """
  The Public Stores building on Derby Street was used by U.S.
  Customs to hold cargo until merchants paid their duties. Goods
  arrived in barrels, crates, bags, and chests from around the world.
  """,
  """
  The Public Stores building on Derby Street served as the U.S.
  Customs Service warehouse, holding cargo from merchant ships until
  the importers could pay the duties owed to the government. Goods
  arrived in Salem in barrels, crates, bags, and chests from ports
  across the globe. Pepper from Sumatra, tea from China, silk from
  India, sugar from the Caribbean. Each shipment was inspected,
  weighed, and taxed before being released to the merchant. The
  customs duties collected at ports like Salem were the primary
  source of revenue for the young American government. In the early
  decades after independence, customs revenue funded nearly the
  entire federal budget. Salem's importance to this system is why
  it was chosen as the site of the first National Historic Site in
  the United States. The Public Stores building is part of the Salem
  Maritime National Historical Park. It stands near the Custom House,
  the Scale House, and Derby Wharf, forming a cluster of buildings
  that together tell the story of Salem as a center of global trade.
  """)

n("Scale House",
  """
  The Scale House on Derby Street stored the scales and weighing
  equipment used to measure cargo from incoming ships. The scales
  were carried to the wharf and assembled alongside each vessel.
  """,
  """
  The Scale House on Derby Street served a precise and essential
  function in Salem's maritime trade. It stored the scales and
  weighing equipment that customs inspectors used to measure cargo
  from incoming ships. The scales were never used inside the building
  itself. Instead, they were carted to the wharf and assembled
  alongside each vessel as it unloaded. This was careful, methodical
  work. Every barrel of pepper, chest of tea, and bolt of silk had
  to be accurately weighed so the correct duty could be assessed.
  The revenue depended on precision. The Scale House is part of the
  Salem Maritime National Historical Park, joining the Custom House,
  Public Stores, and Derby Wharf in telling the story of how
  international trade actually worked in early America. These
  buildings show the infrastructure behind the romance of tall ships
  and exotic ports. Someone had to weigh the pepper. Someone had to
  calculate the tax. These mundane but essential tasks happened right
  here.
  """)

n("St. Joseph Hall",
  """
  St. Joseph Hall at 160 Derby Street was built in 1909 as the
  headquarters of a Polish fraternal society. It marks the heart of
  Salem's Polish community in the early twentieth century.
  """,
  """
  St. Joseph Hall at 160 Derby Street was built in 1909 by the St.
  Joseph Society, a Polish fraternal organization. By the early
  twentieth century, the western end of Derby Street had become the
  heart of Salem's Polish community. Like many American port cities,
  Salem received waves of immigrants who transformed its neighborhoods
  and culture. The Polish community joined earlier waves of Irish,
  French Canadian, and other immigrant groups who came to Salem for
  work in its factories and mills after the maritime trade declined.
  St. Joseph Hall served as a community center, meeting place, and
  social hub for Polish families. Fraternal societies like the St.
  Joseph Society provided mutual aid, cultural preservation, and
  social connection for immigrant communities navigating life in a
  new country. The building is part of the Salem Maritime National
  Historical Park, recognized for its role in telling the broader
  story of Salem beyond the witch trials and maritime trade. Salem's
  immigrant history is an essential and often overlooked chapter
  of the city's story.
  """)

n("West India Goods Store",
  """
  The West India Goods Store on Derby Street was a retail shop
  selling exotic goods from around the world. In Salem, West India
  goods meant products from everywhere, not just the Caribbean.
  """,
  """
  The West India Goods Store on Derby Street represents the retail
  end of Salem's international trade empire. The term West India
  Goods Store was used in Salem as a generic name for shops selling
  imported products from around the world, not just the Caribbean.
  Captain Henry Prince built this structure in the early 1800s. A
  store like this would have sold spices, coffee, tea, sugar, rum,
  molasses, fabric, and other exotic goods brought to Salem by
  merchant ships. For Salem residents in the late eighteenth and
  early nineteenth centuries, a visit to the West India Goods Store
  was a connection to the wider world. The scents of cinnamon, pepper,
  and cloves filled the air. Bolts of silk from China sat alongside
  sugar from Barbados. The store is part of the Salem Maritime
  National Historical Park. It offers a vivid reminder of the era
  when Salem's merchants traded with ports across the globe, making
  this small New England city one of the richest in America. The
  building helps bring the abstract concept of international trade
  down to a human, sensory scale.
  """)


# ═══════════════════════════════════════════════════════════════════
# HISTORIC SITES (20)
# ═══════════════════════════════════════════════════════════════════

n("Andrew Safford House",
  """
  The Andrew Safford House is a grand Federal-era mansion near Essex
  Street. It represents the wealth and architectural ambition of
  Salem's merchant elite during the golden age of trade.
  """,
  """
  The Andrew Safford House is a Federal-era mansion that exemplifies
  the wealth and refined taste of Salem's merchant class during the
  city's maritime golden age. The house reflects the prosperity that
  international trade brought to Salem in the late eighteenth and
  early nineteenth centuries. Merchants like Safford built grand
  homes that rivaled anything in Boston or Philadelphia, filling
  them with imported furnishings, Asian porcelain, and custom
  woodwork by local craftsmen like Samuel McIntire. The house is
  located near the Peabody Essex Museum campus, which owns and
  maintains several of Salem's most significant historic houses.
  The area around Essex Street and the museum contains one of the
  finest collections of Federal-era domestic architecture in the
  United States. Walking this neighborhood, you can see how Salem's
  extraordinary wealth from trade translated into extraordinary
  architecture. The Safford House is part of this remarkable
  collection, standing as evidence of a time when Salem was one of
  the richest cities in America.
  """)

n("Benjamin Punchard, Shoreman",
  """
  The Benjamin Punchard memorial is nearby. This marker honors one
  of Salem's working waterfront men who helped build the port into
  a center of global trade.
  """,
  """
  The Benjamin Punchard memorial commemorates one of Salem's
  shoremen, the laborers who worked the wharves and waterfronts
  loading and unloading the merchant ships that made Salem wealthy.
  While the merchants, captains, and judges of Salem history are
  well documented, the workers who did the physical labor of
  international trade are often overlooked. Shoremen carried barrels
  of pepper, chests of tea, and crates of exotic goods on and off
  ships in all weather. The work was hard, poorly paid, and
  essential. Without men like Benjamin Punchard, Salem's merchant
  princes could not have built their fortunes. This memorial offers
  a reminder that Salem's maritime glory was built on the labor of
  ordinary people. The grand mansions on Chestnut Street and the
  elegant Custom House tell one part of the story. Memorials like
  this one tell the other part. The part about calloused hands,
  aching backs, and long days on the wharf.
  """)

# Bewitched Statue has description in JSON — adapted
n("Bewitched Sculpture \u2013 Samantha Statue",
  """
  The Bewitched statue is ahead on Essex Street. This six-foot bronze
  of Elizabeth Montgomery as Samantha was gifted to Salem by TV Land
  in 2005. One of the most photographed spots in town.
  """,
  """
  The Bewitched Sculpture on Essex Street is a six-foot-tall bronze
  statue of Elizabeth Montgomery as Samantha Stephens from the 1960s
  TV sitcom Bewitched. The series filmed several episodes in Salem
  for its seventh season in 1970 after a fire shut down its
  Hollywood set. In 2005, the nostalgia cable channel TV Land gifted
  this statue to Salem in honor of the show's fortieth anniversary.
  It has quickly become one of the most photographed landmarks in
  the city. Visitors line up to take selfies, mimic the iconic nose
  twitch, and pretend to cast spells alongside Samantha. The statue
  represents an interesting layer of Salem's identity. Beyond the
  real history of 1692, beyond the genuine spiritual community,
  there is Salem's pop culture connection to witchcraft. Bewitched,
  along with Hocus Pocus and other films, has helped make Salem a
  destination for visitors whose interest in witchcraft is playful
  rather than historical or spiritual. The statue sits near the
  intersection of Essex Street and Washington Street, impossible
  to miss on any walk through downtown Salem. Photo credit to
  Ben Rekemeyer.
  """)

n("Central Wharf",
  """
  Central Wharf extends into Salem Harbor from Derby Street. This
  was once a busy commercial pier in Salem's maritime heyday.
  """,
  """
  Central Wharf is one of several wharves that once lined Salem
  Harbor during the city's golden age of maritime trade. In the late
  eighteenth century, Salem had more than fifty wharves extending
  into the harbor, each serving the merchant ships that traded with
  ports across the globe. Central Wharf sits near Derby Street,
  surrounded by the other maritime structures preserved within the
  Salem Maritime National Historical Park. The wharf area offers
  waterfront views and a sense of the physical scale of Salem's
  maritime operations. At its peak, Salem was one of the richest
  cities in America, rivaling Boston and Philadelphia in
  international trade. Ships from Salem were the first American
  vessels to reach many ports in the East Indies and the Pacific.
  The city's motto, To the Farthest Ports of the Rich East,
  captures the ambition and reach of this small New England port.
  Walking the wharf area today, with the harbor stretching out
  before you, it is easy to imagine the bustle of ships, cargo,
  and commerce that once defined this waterfront.
  """)

n("Charlotte Forten Park",
  """
  Charlotte Forten Park is Salem's newest green space on Derby
  Street. It honors Charlotte Forten, an abolitionist, activist,
  and Salem State University's first African American graduate
  in 1856.
  """,
  """
  Charlotte Forten Park on Derby Street honors one of Salem's most
  remarkable residents. Charlotte Forten was an educator, writer,
  poet, abolitionist, and women's rights activist who came to Salem
  from Philadelphia seeking the equal education she could not find
  at home. She became the first African American to graduate from
  the Salem Normal School, now Salem State University, with the
  class of 1856. Forten later became the first northern African
  American teacher to go south to teach formerly enslaved people.
  Throughout her life, she faced inequality due to her race and
  gender, but used her pen to advocate for justice and equality.
  The park, roughly twenty-five thousand square feet, includes a
  plaza for programs and performances, a harbor walk around the
  South River, swing seating facing the water, built-in percussion
  features, and green space. It connects downtown Salem to the Point
  neighborhood and the historic waterfront. Charlotte Forten Park
  tells an essential story about Salem that goes beyond witch trials
  and maritime trade. It is a story about courage, education, and
  the ongoing struggle for equality.
  """)

n("Charter Street Historic District",
  """
  You are entering the Charter Street Historic District. This area
  contains some of Salem's oldest buildings and the famous Charter
  Street Cemetery, established in 1637.
  """,
  """
  The Charter Street Historic District encompasses one of the oldest
  and most historically significant neighborhoods in Salem. The area
  centers on Charter Street, which runs from the waterfront up toward
  Essex Street, passing the Charter Street Cemetery and the Witch
  Trials Memorial along the way. The cemetery, established in 1637,
  is the oldest burying ground in Salem and one of the oldest in the
  United States. Buried here are witch trial judge John Hathorne,
  Governor Simon Bradstreet, Mayflower passenger Captain Richard More,
  and many other figures from Salem's earliest centuries. The
  district's buildings span several centuries of architectural styles,
  from First Period colonial houses to Federal-era mansions. Walking
  through this area gives you a condensed timeline of Salem's
  development from a small Puritan settlement into a prosperous
  merchant city. The district is listed on the National Register of
  Historic Places and is one of the most photographed neighborhoods
  in Salem, especially during October when the ancient gravestones
  and weathered buildings take on an especially atmospheric quality.
  """)

n("Crowninshield-Bentley House",
  """
  The Crowninshield-Bentley House is a historic home near the Peabody
  Essex Museum. It represents Salem's colonial architecture and the
  connections between Salem's prominent families.
  """,
  """
  The Crowninshield-Bentley House is a historic residence near the
  Peabody Essex Museum campus. The Crowninshield family was one of
  Salem's most prominent merchant dynasties, deeply involved in the
  international trade that made the city wealthy. Elizabeth
  Crowninshield married Elias Hasket Derby, America's first
  millionaire, connecting two of Salem's most powerful families.
  Reverend William Bentley, the house's other namesake, was the
  minister of Salem's East Church and one of the most learned men
  in early America. His diary, kept from 1784 to 1819, is one of
  the most important primary sources for understanding daily life
  in Federal-era Salem. Bentley recorded everything from shipping
  news to political gossip to weather observations. The house is
  maintained by the Peabody Essex Museum and represents the
  architectural style and domestic life of Salem's colonial period.
  Its location near the museum campus makes it accessible for
  visitors exploring Salem's cultural institutions.
  """)

# Adapted from SalemPois.kt
n("Derby Wharf Light Station",
  """
  The Derby Wharf Light Station was built in 1871 to help ships
  enter Salem Harbor. This small square lighthouse sits at the end
  of the half-mile wharf. A scenic walk with panoramic harbor views.
  """,
  """
  The Derby Wharf Light Station was constructed in 1871 to better
  assist ships entering Salem Harbor. The lighthouse features a
  unique square design and stands only about twenty feet tall. The
  National Park Service gained ownership of the lighthouse in 1977,
  and today NPS rangers at the Salem Maritime National Historic Site
  are available to provide additional information about the light
  station. The interior of the lighthouse is not open to the public,
  however the exterior is accessible via a scenic walk to the end
  of Derby Wharf. The walk along the full length of the wharf is
  one of Salem's most peaceful experiences. On a clear day, you
  can see Marblehead Neck, Baker's Island, and the open Atlantic.
  The wharf itself stretches nearly half a mile into the harbor,
  built by the wealthy merchant Elias Hasket Derby in the eighteenth
  century. Walking to the lighthouse and back takes about thirty
  minutes at a leisurely pace. Bring a camera. The views of Salem
  Harbor are exceptional, especially at sunset.
  """)

n("Gardner-Pingree House",
  """
  The Gardner-Pingree House is one of Samuel McIntire's masterworks.
  This Federal-era mansion near the Peabody Essex Museum is
  considered one of the finest houses in New England.
  """,
  """
  The Gardner-Pingree House is widely regarded as one of the
  finest examples of Federal architecture in the United States.
  Designed by Samuel McIntire and built in 1804 for the merchant
  John Gardner, the house showcases McIntire's extraordinary skill
  as both architect and decorative carver. The proportions are
  elegant, the details exquisite. McIntire's signature carved
  ornaments, including garlands, urns, and sheaves of wheat, adorn
  the facade and interior. The house also carries a darker story.
  In 1830, Captain Joseph White, a retired merchant who lived here,
  was murdered in his bed. The case became a national sensation
  and was prosecuted by Daniel Webster. Nathaniel Hawthorne may
  have drawn on the crime for aspects of The House of the Seven
  Gables. The house is now owned by the Peabody Essex Museum and
  is open for tours. It stands on Essex Street near the museum
  campus, a jewel of American architecture in a city full of
  architectural treasures.
  """)

n("Hathaway House",
  """
  The Hathaway House is a historic dwelling near the Seven Gables
  campus. This colonial-era house adds to the remarkable
  concentration of period architecture on Derby Street.
  """,
  """
  The Hathaway House is a colonial-era dwelling located near the
  House of the Seven Gables campus on the Derby Street waterfront.
  The house contributes to the remarkable concentration of historic
  architecture in this area, where buildings from the seventeenth,
  eighteenth, and nineteenth centuries stand within walking distance
  of each other. Salem's waterfront district has been continuously
  occupied since the city's founding in 1626, making it one of the
  oldest areas of continuous European settlement in North America.
  The Hathaway name connects to several prominent Salem families.
  The Derby Street area, running from Pickering Wharf to the
  House of the Seven Gables, offers one of the best walking tours
  of historic architecture in New England. The mix of domestic,
  commercial, and maritime buildings tells the story of a community
  that lived, worked, and traded on this waterfront for four
  centuries.
  """)

n("Lady of Salem Maritime Public Art Celebration",
  """
  Look for the Ladies of Salem on the lampposts along Essex Street.
  These painted figureheads celebrate Salem's Golden Age of Sail.
  Over seventeen figureheads by local artists adorn the pedestrian
  mall.
  """,
  """
  The Lady of Salem Maritime Public Art Celebration features over
  seventeen painted figureheads created by local artists, displayed
  on lampposts along the Essex Street pedestrian mall from June
  through October. The project began in 2012, sponsored by the
  Salem Beautification Committee. Each figurehead is a thirty-three
  inch fiber-molded form painted and embellished by a local artist,
  embodying various aspects of Salem's Golden Age of Sail.
  Figureheads were ornamental carvings that graced the bowsprit of
  sailing ships and helped identify the vessel. During the late
  seventeen nineties, Salem was the richest city in America with
  over fifty wharves. Spices, textiles, and other riches were
  brought to Salem from Asia and the Caribbean. Locally crafted
  products were traded around the world. Salem's city motto, To
  the Farthest Ports of the Rich East, celebrates this period.
  The Ladies pay homage to this maritime glory. Each figurehead
  is paired with a school, civic organization, or business sponsor,
  making this a true public-private collaboration. Keep your eyes
  on the lampposts as you walk Essex Street.
  """)

n("Nathaniel Bowditch House",
  """
  The Nathaniel Bowditch House is at 9 North Street. Bowditch was a
  self-taught mathematician who found over eight thousand errors in
  the standard navigation manual and wrote the book that replaced it.
  """,
  """
  The Nathaniel Bowditch House at 9 North Street was the home of one
  of Salem's most remarkable residents. Nathaniel Bowditch was a
  self-taught mathematician who went to sea as a young man and
  discovered over eight thousand errors in the standard English
  navigation manual, John Hamilton Moore's Practical Navigator.
  In 1802, Bowditch published his own work, The New American
  Practical Navigator, which remains the world standard for
  celestial navigation to this day. The book is still published
  and used by the United States government. Bowditch's achievement
  is extraordinary. A man with almost no formal education, born in
  a small New England port, produced a work of mathematical and
  navigational science that surpassed everything that had come
  before. He later became president of the American Academy of
  Arts and Sciences and made important contributions to astronomy
  and mathematics. Salem has produced many remarkable figures, but
  Bowditch stands out as a genius whose work saved countless lives
  at sea and advanced human understanding of navigation.
  """)

# Adapted from SalemPois.kt
n("Roger Conant Statue",
  """
  This statue of Roger Conant, the founder of Salem in 1626, is one
  of the most photographed landmarks in the city. Visitors often
  mistake his Puritan cloak for a witch's outfit.
  """,
  """
  The Roger Conant Statue depicts the founder of Salem, who led a
  small group of settlers to establish the community in 1626, two
  years before the arrival of the larger Massachusetts Bay Colony.
  The statue, erected in 1913, shows Conant in his Puritan cloak
  and hat, standing at the edge of Salem Common near Washington
  Square. It has become one of Salem's most recognizable and most
  photographed landmarks. There is a delightful irony to this
  statue. Visitors who do not know Salem's history frequently
  mistake Conant's Puritan cloak for a witch's outfit, posing with
  the statue under the assumption that it depicts a witch or wizard.
  Roger Conant would have been appalled. As a Puritan leader, he
  would have viewed witchcraft as a mortal sin. Yet here he stands,
  permanently misidentified as a symbol of the very thing his
  community feared and persecuted. The statue is free and accessible
  at all hours, located at the corner of Brown Street and Washington
  Square. It is a perfect photo opportunity and a good starting
  point for exploring Salem Common.
  """)

# Adapted from SalemPois.kt
n("Salem Common",
  """
  Salem Common is a nine-acre park in the heart of downtown. Used as
  common grazing land since the sixteen thirties, it is surrounded
  by grand homes and anchored by the Witch Museum and Hawthorne Hotel.
  """,
  """
  Salem Common is a nine-acre public park in the heart of downtown
  Salem. Used as common grazing land since the sixteen thirties, it
  is one of the oldest public spaces in the United States. The Common
  is surrounded by grand architecture, including the Salem Witch
  Museum in its Gothic Revival former church, the Hawthorne Hotel,
  and several magnificent Federal-era mansions. The park features
  walking paths, mature trees, a bandstand, and the Roger Conant
  statue at its southern edge. Throughout the year, Salem Common
  hosts events, festivals, and community gatherings. In October,
  during Haunted Happenings, the Common becomes a central gathering
  point for the city's massive Halloween celebration. The Washington
  Arch at the north end commemorates George Washington's visit to
  Salem in 1789. The First Muster monument marks the spot where
  the first militia in America mustered in 1637. Salem Common is
  more than a park. It is a palimpsest of nearly four hundred years
  of American history, written in stone, bronze, and living trees.
  """)

n("Salem Harbor",
  """
  Salem Harbor stretches before you. This was once one of the most
  important ports in the American colonies. During the late
  eighteenth century, Salem's merchant ships traded across the globe.
  """,
  """
  Salem Harbor was one of the major international ports in the
  American colonies and the early republic. Spanning both north and
  south of Salem, the harbor served as the gateway for Salem's
  extraordinary international trade. During the late eighteenth and
  early nineteenth centuries, merchant ships from Salem sailed to
  ports across the globe, importing ceramics, furniture, decorative
  arts, spices, dyes, textiles, and other exotic goods. Salem's
  captains were among the first Americans to trade with China, Japan,
  India, and the East Indies. At its peak, the city had over fifty
  wharves extending into the harbor. Today, the harbor is best
  enjoyed from Pickering Wharf, where restaurants and shops line the
  waterfront, or from the end of Derby Wharf, where panoramic views
  stretch across the water to Marblehead and beyond. The harbor is
  known for its beautiful sunsets. If your timing is right, find a
  spot on the waterfront as the sun drops behind the city. It is one
  of Salem's most memorable views.
  """)

# Adapted from SalemPois.kt
n("Salem Maritime National Historical Park",
  """
  Salem Maritime National Historical Park is the first National
  Historic Site in the United States, established in 1938. It
  preserves Salem's rich maritime heritage from its era as one of
  America's wealthiest ports.
  """,
  """
  Salem Maritime National Historical Park was the first National
  Historic Site designated in the United States, established by
  Congress in 1938. The park preserves and interprets the maritime
  history of New England and the nation. During the late eighteenth
  century, Salem was one of the richest cities in America, its
  wealth built on international trade. Ships from Salem sailed to
  the East Indies, China, Africa, and beyond. The park includes
  twelve historic structures, a replica tall ship called the
  Friendship, and approximately ten acres of land along the
  waterfront. Key sites include the Custom House where Hawthorne
  worked, Derby Wharf, the Scale House, the West India Goods Store,
  the Derby House, and the Narbonne House. Rangers offer free tours
  and programs throughout the season. The park is free to enter,
  though some special programs may have fees. Visitor center hours
  are nine to five daily. Start your visit at the main visitor
  center on Derby Street for orientation, maps, and program
  schedules. Phone 978-740-1650.
  """)

n("The First Muster",
  """
  The First Muster monument stands on Salem Common. It marks the
  spot where the first military muster in America took place in
  1637, making Salem the birthplace of the National Guard.
  """,
  """
  The First Muster monument on Salem Common marks a significant
  moment in American military history. On this spot in 1637, the
  first militia in America was mustered, organized, and drilled.
  This makes Salem the birthplace of the American citizen-soldier
  tradition and, by extension, the National Guard. The militia was
  established just seven years after Salem's founding as a permanent
  settlement. The need for organized defense was urgent. Relations
  with some Native American groups were tense, and the Pequot War
  was underway in Connecticut. The concept of the citizen militia,
  ordinary people taking up arms in defense of their community,
  became a foundational principle of American democracy. It was
  enshrined in the Second Amendment and shaped the nation's approach
  to military service for centuries. The monument stands at the
  north end of Salem Common, near the Washington Arch. Together,
  these monuments connect Salem to some of the most important
  themes in American history, well beyond the witch trials that
  dominate the city's popular image.
  """)

n("Washington Arch",
  """
  The Washington Arch stands at the north end of Salem Common. It
  commemorates President George Washington's visit to Salem in 1789
  during his tour of the new nation.
  """,
  """
  The Washington Arch at the north end of Salem Common commemorates
  President George Washington's visit to Salem on October 29, 1789.
  Washington visited Salem as part of his tour of New England
  following his inauguration as the first President of the United
  States. The visit was a significant event for Salem, which had
  played an important role in the American Revolution. Salem's
  privateers had captured more British ships than any other American
  port during the war. Washington's tour was designed to build
  national unity and demonstrate the new federal government's
  connection to communities across the young nation. The arch
  stands near the First Muster monument, together forming a pair
  of landmarks that connect Salem to pivotal moments in American
  history. Most visitors come to Salem for the witch trials story,
  but the city's contributions to the Revolution, to early American
  commerce, and to the development of democratic institutions are
  equally remarkable.
  """)

n("World War II Memorial",
  """
  The World War II Memorial on Salem Common honors the Salem
  residents who served and sacrificed during the Second World War.
  A quiet place of reflection amid the bustle of downtown.
  """,
  """
  The World War II Memorial on Salem Common honors the men and women
  of Salem who served in the Second World War. The memorial stands
  among the collection of monuments on the Common that together tell
  the story of Salem's contributions to American military history,
  from the First Muster in 1637 through the twentieth century. Salem,
  like communities across America, sent its sons and daughters to
  serve in every theater of the war. The memorial serves as a place
  of reflection and remembrance. Salem Common's role as a gathering
  place for the community stretches back nearly four hundred years.
  It has hosted celebrations, protests, musters, and memorials.
  The World War II Memorial adds to this layered history, reminding
  visitors that Salem's story extends far beyond the witch trials.
  The memorial is free and accessible at all hours, located on the
  Common near the other military monuments.
  """)

n("Yin Yu Tang",
  """
  Yin Yu Tang is a two-hundred-year-old Chinese house that was
  transported and reassembled inside the Peabody Essex Museum. An
  entire house, moved across the Pacific. Remarkable.
  """,
  """
  Yin Yu Tang is one of the most extraordinary exhibits at the
  Peabody Essex Museum. It is a complete, two-hundred-year-old
  Chinese house from the Huizhou region of Anhui Province,
  transported piece by piece across the Pacific Ocean and
  reassembled inside the museum. The house belonged to the Huang
  family for eight generations. When the last residents moved away,
  the museum negotiated its acquisition and painstaking disassembly.
  Every beam, tile, and carved panel was cataloged, shipped to Salem,
  and rebuilt to its original specifications. Walking through Yin Yu
  Tang, you move through rooms that span two centuries of Chinese
  domestic life. Family altars, sleeping quarters, a central
  courtyard. The house tells the story of how one family lived
  through dynastic change, revolution, and modernization. Its
  presence in Salem connects to the city's centuries-old trading
  relationship with China. Salem's merchants were among the first
  Americans to trade with Chinese ports. The Canton trade brought
  porcelain, tea, silk, and other goods to Salem. Yin Yu Tang
  continues that cultural exchange in a profound and tangible way.
  Admission to PEM required.
  """)


# ═══════════════════════════════════════════════════════════════════
# PUBLIC ART (16)
# ═══════════════════════════════════════════════════════════════════

n("Cat Witch",
  """
  Look for the Cat Witch mural nearby on Harbor Street. This striking
  piece of street art blends Salem's witch identity with its famous
  love of cats. Part of the Punto Urban Art Museum.
  """,
  """
  The Cat Witch mural is located near 64 and a half Harbor Street,
  part of Salem's Punto Urban Art Museum. This open-air gallery in
  the Point neighborhood features dozens of murals by local and
  international artists, transforming the residential streets south
  of downtown into an extraordinary outdoor museum. The Cat Witch
  mural blends two of Salem's most recognizable symbols. The witch,
  of course, from the city's 1692 history and modern magical
  community. And the cat, Salem's unofficial mascot, beloved by
  locals and visitors alike. The Punto Urban Art Museum was created
  to bring art into the everyday lives of the Point neighborhood's
  residents, many of whom are from immigrant communities. The murals
  celebrate diversity, cultural heritage, and community pride. The
  project can be explored on foot, and the Punto website provides a
  map of all the murals with artist information. The Point
  neighborhood is just south of Derby Street, within easy walking
  distance of Salem's main tourist areas.
  """)

n("Communion with Us",
  """
  The Communion with Us mural is nearby. Part of Salem's Punto Urban
  Art Museum, this piece explores themes of community and connection
  in the Point neighborhood.
  """,
  """
  Communion with Us is a mural in Salem's Punto Urban Art Museum,
  the open-air gallery that has transformed the Point neighborhood
  south of downtown into an outdoor museum of contemporary art.
  The mural explores themes of community, connection, and shared
  experience. The Punto Urban Art Museum was launched to bring
  world-class art into the everyday environment of a working-class
  neighborhood. The Point, historically home to waves of immigrants
  who came to Salem for work in its factories and fishing industry,
  is a community rich in cultural diversity. The murals reflect
  this diversity, telling stories of heritage, identity, and
  belonging. For visitors, exploring the Punto murals offers a
  completely different side of Salem from the witch shops and
  museums of Essex Street. Here, art speaks to contemporary life,
  immigrant experience, and the power of creativity to transform
  public space. The murals can be found on buildings, walls, and
  fences throughout the neighborhood. Check puntourbanartmuseum.org
  for a complete map.
  """)

n("Crabs",
  """
  The Crabs sculpture is nearby. This playful public artwork adds
  a touch of maritime whimsy to Salem's streets, connecting the
  city's art scene to its ocean heritage.
  """,
  """
  The Crabs sculpture is one of several public artworks scattered
  through Salem's downtown and waterfront neighborhoods. The piece
  adds maritime whimsy to the streetscape, reminding passersby of
  Salem's deep connection to the ocean. Salem has been a fishing
  and trading port since its founding in 1626. The harbor, wharves,
  and waterfront define the city's geography and history. Public
  art like Crabs brings this maritime identity into the present,
  making it visible and playful in the urban landscape. Salem's
  public art scene has grown significantly in recent years, with
  installations ranging from the large-scale Punto Urban Art Museum
  murals in the Point neighborhood to smaller sculptures and
  installations throughout downtown. The city actively supports
  public art through beautification initiatives, artist partnerships,
  and community projects. Keep your eyes open as you walk through
  Salem. Art appears in unexpected places.
  """)

n("Equality",
  """
  The Equality artwork is nearby. This public art piece speaks to
  themes of justice and equal rights, resonating deeply in a city
  forever marked by the injustice of 1692.
  """,
  """
  The Equality artwork is a public art installation that speaks to
  themes of justice, equal rights, and human dignity. In Salem,
  these themes carry particular weight. The witch trials of 1692
  stand as one of America's most powerful cautionary tales about
  what happens when fear overrides justice and due process is
  abandoned. Twenty innocent people were executed. Over two hundred
  were accused. The trials destroyed families and traumatized a
  community. Today, Salem uses its dark history as a platform for
  advocacy. The city has officially apologized to the victims of
  the witch trials and has embraced its role as a symbol of the
  importance of civil liberties and equal protection under the law.
  Public art like Equality continues this tradition, using creative
  expression to keep these values visible in the daily life of the
  city. The piece is located in the area south of downtown, near
  other public artworks in the Point neighborhood.
  """)

n("Garden Boy",
  """
  The Garden Boy mural is nearby on Peabody Street. Part of the
  Punto Urban Art Museum, this colorful piece brings life and
  artistry to the Point neighborhood.
  """,
  """
  Garden Boy is a mural in Salem's Punto Urban Art Museum, located
  on Peabody Street in the Point neighborhood. The mural is part
  of the extraordinary collection of outdoor art that has transformed
  this residential area into one of the most vibrant open-air
  galleries in New England. The Punto Urban Art Museum features
  works by both local and international artists, painted directly
  on the buildings and walls of the neighborhood. Garden Boy adds
  color, life, and imagination to the streetscape. The Point
  neighborhood, bounded roughly by Derby Street, Congress Street,
  and Lafayette Street, has a distinct character. It is more
  residential and diverse than the tourist-heavy areas to the north.
  Walking through the Punto murals gives visitors a glimpse of
  everyday Salem, the Salem where people live, work, and raise
  families alongside the city's famous history. More information
  and a complete mural map at puntourbanartmuseum.org.
  """)

n("Goldfish",
  """
  The Goldfish artwork is nearby. This playful public art piece adds
  a splash of color and whimsy to Salem's streets.
  """,
  """
  The Goldfish artwork is one of several public art installations
  in Salem's downtown area. The piece brings color and whimsy to
  the streetscape, offering a moment of delight for pedestrians.
  Salem's investment in public art reflects the city's broader
  identity as a creative community. Beyond the witch shops and
  historic sites, Salem supports a vibrant arts scene that includes
  galleries, studios, theaters, and public installations. The
  Peabody Essex Museum anchors the city's cultural life with
  world-class exhibitions, while independent artists and galleries
  along Essex Street and in the Point neighborhood add depth and
  diversity. Public artworks like Goldfish are part of this
  ecosystem, making art accessible and present in everyday spaces.
  You do not need to buy a museum ticket to encounter art in Salem.
  It meets you on the street, on the walls of buildings, and in
  the parks and public spaces of this creative city.
  """)

n("Life is Sweet",
  """
  Life is Sweet is a public artwork nearby. This piece brings
  positive energy and color to Salem's Point neighborhood.
  """,
  """
  Life is Sweet is a public art piece located in the Point
  neighborhood area of Salem, south of the downtown tourist
  district. The artwork brings a message of positivity and
  celebration to the streetscape. Salem's public art ranges
  from the deeply historical and political to the purely joyful.
  Life is Sweet falls in the latter category, offering passersby
  a moment of simple pleasure. The Point neighborhood has been
  particularly enriched by public art in recent years, thanks
  largely to the Punto Urban Art Museum project. The area's
  transformation through art has brought national attention and
  visitor interest to a neighborhood that was previously
  overlooked by most tourists. For visitors who have spent their
  morning exploring witch museums and haunted attractions, a walk
  through the Point's public art offers a refreshing change of
  pace. Different themes, different stories, different colors.
  Still Salem, but a side most visitors never see.
  """)

n("Love Child",
  """
  The Love Child mural is on Peabody Street. Part of the Punto
  Urban Art Museum, this vibrant piece celebrates love and
  community in Salem's diverse Point neighborhood.
  """,
  """
  Love Child is a mural at 24 Peabody Street, part of Salem's Punto
  Urban Art Museum. The piece celebrates themes of love, identity,
  and community, rendered in the vibrant style that characterizes
  the Punto collection. The mural is one of dozens scattered through
  the Point neighborhood, creating an open-air gallery that can be
  explored on foot. The artists represented in the Punto collection
  come from diverse backgrounds and traditions, reflecting the
  multicultural character of the neighborhood itself. The Point has
  historically been home to immigrant communities, from Polish and
  French Canadian families in the early twentieth century to
  Dominican and other Latin American families today. The murals
  honor this heritage while creating new cultural landmarks for
  the community. Walking the Punto murals is one of Salem's hidden
  gems. Most tourists stick to Essex Street and Derby Street, but
  the Point offers art, authenticity, and a different perspective
  on what makes Salem special.
  """)

n("Migrar",
  """
  The Migrar mural is nearby. Its title means to migrate in Spanish.
  This piece in Salem's Point neighborhood reflects the immigrant
  experience that has shaped this community for generations.
  """,
  """
  Migrar, Spanish for to migrate, is a public artwork in Salem's
  Point neighborhood. The piece speaks directly to the immigrant
  experience that has defined this community for over a century.
  Salem's Point neighborhood has been home to successive waves of
  immigrants, each bringing their own culture, language, and
  traditions to this small New England city. Polish families came
  in the early twentieth century to work in the leather factories.
  French Canadians came for the textile mills. More recently,
  Dominican and other Latin American families have made the Point
  their home. Migrar honors this ongoing story of movement,
  adaptation, and community-building. The artwork is part of the
  broader Punto Urban Art Museum collection, which uses public art
  to celebrate and make visible the cultural richness of this
  diverse neighborhood. For visitors to Salem, the Point offers a
  powerful reminder that the city's story is not only about 1692.
  It is also about the ongoing American story of immigration,
  diversity, and the courage to start over in a new place.
  """)

n("Our Lady Guadelupe",
  """
  The Our Lady of Guadalupe artwork is nearby. This piece reflects
  the deep Catholic faith and Latin American cultural heritage
  present in Salem's Point neighborhood.
  """,
  """
  The Our Lady of Guadalupe artwork in Salem's Point neighborhood
  reflects the deep Catholic faith and Latin American cultural
  heritage of many families in this community. Our Lady of Guadalupe
  is one of the most powerful religious and cultural symbols in the
  Americas, revered by millions as a patron and protector. In the
  Point neighborhood, where many residents trace their roots to
  the Dominican Republic and other Latin American countries, this
  image carries profound spiritual and cultural significance. The
  artwork is part of the collection of public art that fills the
  Point's streets, some connected to the Punto Urban Art Museum,
  others created independently by community members. Together, they
  create a visual tapestry of the neighborhood's identity. Salem's
  religious history is complex. Founded by Puritans who persecuted
  those who practiced differently, the city has evolved into a
  place where many faiths and spiritual traditions coexist. Our
  Lady of Guadalupe adds another layer to this ongoing story of
  religious diversity in a city once defined by religious fear.
  """)

n("Pride/Identity",
  """
  The Pride slash Identity artwork is nearby. This piece celebrates
  self-expression and personal truth, themes that resonate in a city
  where people were once persecuted for being different.
  """,
  """
  Pride slash Identity is a public artwork that celebrates self-
  expression, personal truth, and the courage to be who you are.
  In Salem, these themes carry particular weight. In 1692, people
  were accused, imprisoned, and executed for being perceived as
  different. Those who did not conform to Puritan expectations of
  behavior, dress, or belief became targets of suspicion and
  violence. Today, Salem has become a city that celebrates
  difference. The LGBTQ plus community, the pagan and witchcraft
  community, the immigrant community, and the artist community all
  find welcome in a city that learned the hardest possible lesson
  about the dangers of intolerance. Public art like Pride slash
  Identity makes this commitment to inclusion visible in the
  physical fabric of the city. It is a statement that Salem's
  streets belong to everyone. The artwork is located in the Point
  neighborhood area, surrounded by other pieces that explore themes
  of identity, heritage, and belonging.
  """)

n("Super Dali",
  """
  The Super Dali mural is on Peabody Street. This surrealist-
  inspired piece is part of Salem's Punto Urban Art Museum, bringing
  world-class art to a residential neighborhood.
  """,
  """
  Super Dali is a mural at 6 Peabody Street, part of Salem's Punto
  Urban Art Museum. The piece draws inspiration from the surrealist
  tradition, specifically referencing Salvador Dali, one of the
  twentieth century's most recognizable artists. The mural brings
  a touch of surrealist whimsy to the residential streetscape of
  the Point neighborhood. The Punto Urban Art Museum has attracted
  national attention for its ambitious scope and the quality of its
  artists. The project transforms ordinary building walls into
  canvases for extraordinary art, creating a museum without walls
  or admission fees. Super Dali is one of the collection's more
  playful entries, bridging high art and street art in a way that
  is accessible and fun for all viewers. The mural can be found at
  puntourbanartmuseum.org, which provides a map and information
  about all the works in the collection. Walking the Punto murals
  takes about thirty to forty-five minutes at a leisurely pace.
  """)

n("Untitled",
  """
  An untitled public artwork is nearby. Part of Salem's growing
  collection of street art and murals that bring creativity to
  unexpected places.
  """,
  """
  This untitled public artwork is part of Salem's growing collection
  of street art, murals, and installations that bring creative
  expression to the city's public spaces. Untitled works invite
  viewers to bring their own interpretation. Without a title to
  guide the eye, you are free to see what speaks to you. Salem's
  public art scene has expanded significantly in recent years,
  with major projects like the Punto Urban Art Museum in the Point
  neighborhood, the Lady of Salem figureheads on Essex Street, and
  independent installations throughout downtown. The city actively
  supports public art through beautification committees, artist
  partnerships, and community initiatives. For a city famous for
  its dark history, Salem's contemporary art scene is remarkably
  vibrant and diverse. The art does not shy away from Salem's past,
  but it also does not let the past define the city's creative
  future. This piece adds to that ongoing conversation between
  history and imagination.
  """)

n("Verduras",
  """
  The Verduras artwork is nearby. Verduras means vegetables in
  Spanish, connecting to the food culture and daily life of Salem's
  Latin American community.
  """,
  """
  Verduras, which means vegetables in Spanish, is a public artwork
  that celebrates the food culture and daily life of Salem's Latin
  American community. Food is one of the most powerful expressions
  of cultural identity, and in the Point neighborhood, the flavors,
  colors, and ingredients of Caribbean and Latin American cuisine
  are part of the community's character. The artwork transforms a
  simple everyday subject into something worth looking at and
  thinking about. What we eat, where it comes from, and how we
  prepare it all tell stories about who we are and where we come
  from. The Point neighborhood's culinary culture is one of its
  great assets. Visitors who venture beyond the tourist restaurants
  on Derby Street and Essex Street will find authentic Dominican,
  Puerto Rican, and other Latin American food in this neighborhood.
  Verduras is a reminder that some of Salem's best experiences are
  found off the beaten path.
  """)

n("Villa Alegra",
  """
  The Villa Alegra mural is nearby. Part of Salem's Punto Urban Art
  Museum, this piece celebrates joy and community spirit in the
  Point neighborhood.
  """,
  """
  Villa Alegra, which translates roughly to Happy Village or Joyful
  Home, is a mural in Salem's Punto Urban Art Museum collection.
  The piece can be found at puntourbanartmuseum.org with full
  details about the artist and concept. The name captures the
  spirit that the Punto project brings to the Point neighborhood.
  Despite the challenges that come with being a working-class
  immigrant community, there is genuine joy and pride in the Point.
  The murals express this through color, imagery, and story. Villa
  Alegra contributes to the neighborhood's transformation through
  art, adding beauty and meaning to everyday spaces. The Punto
  Urban Art Museum has been recognized as one of the most successful
  public art initiatives in New England, drawing visitors to a
  neighborhood that most Salem tourists never see. It proves that
  art does not need to be behind glass or beyond a ticket counter
  to be powerful. Sometimes the best gallery is the street itself.
  """)

n("Window Phone",
  """
  The Window Phone artwork is nearby. This playful public
  installation adds an element of surprise and creativity to Salem's
  Point neighborhood streetscape.
  """,
  """
  Window Phone is a public artwork in Salem's Point neighborhood
  that plays with the boundary between the interior and exterior of
  buildings. The piece adds an element of surprise and whimsy to
  the streetscape, inviting passersby to look more closely at the
  built environment around them. Public art that transforms everyday
  objects and architectural features into art is particularly
  effective in neighborhoods where people live and work. It makes
  the familiar strange, encouraging residents and visitors to see
  their surroundings with fresh eyes. The Point neighborhood's
  public art collection, anchored by the Punto Urban Art Museum,
  has made this transformation visible and permanent. Every street,
  every building facade, becomes a potential canvas. Window Phone
  is one of the more playful entries in this collection, proving
  that public art does not always need to be serious or political.
  Sometimes it just needs to make you smile and look at the world
  a little differently.
  """)


# ═══════════════════════════════════════════════════════════════════
# CEMETERIES (3)
# ═══════════════════════════════════════════════════════════════════

n("Broad Street Cemetery",
  """
  Broad Street Cemetery is one of Salem's historic burying grounds.
  Less visited than the Charter Street Cemetery, it offers a quiet
  space for reflection among centuries-old headstones.
  """,
  """
  Broad Street Cemetery is one of Salem's several historic burying
  grounds, located west of the downtown core near Broad Street. Less
  visited than the famous Charter Street Cemetery, Broad Street
  offers a quieter, more contemplative experience among centuries-
  old headstones. Salem's cemeteries are remarkable for their age
  and their carved headstones. Colonial-era grave markers feature
  striking imagery: winged skulls, or death's heads, that represent
  the Puritan view of mortality. Later stones show cherubs and
  willows as attitudes toward death softened. The evolution of
  gravestone carving is visible in Salem's cemeteries, making them
  open-air museums of colonial art and belief. Broad Street Cemetery
  provides a less crowded alternative for visitors interested in
  colonial funerary art and the quiet atmosphere of Salem's older
  neighborhoods. Please be respectful when visiting. Stay on paths,
  do not touch or sit on headstones, and remember that these are
  the final resting places of real people who lived in this community
  centuries ago.
  """)

# Adapted from SalemPois.kt
n("Charter Street Cemetery",
  """
  Charter Street Cemetery is one of the oldest burying grounds in
  the United States, established in 1637. Judge John Hathorne,
  ancestor of Nathaniel Hawthorne, is buried here.
  """,
  """
  Charter Street Cemetery, also known as the Old Burying Point, is
  the oldest burying ground in Salem and one of the oldest in the
  entire United States, established in 1637. The cemetery contains
  the graves of several figures connected to the witch trials,
  including Judge John Hathorne, the most zealous of the Salem
  witch trial magistrates and a direct ancestor of author Nathaniel
  Hawthorne. Governor Simon Bradstreet, who helped end the trials,
  is also interred here. Other notable burials include Captain
  Richard More, a Mayflower passenger. The cemetery's weathered
  slate headstones, many featuring carved skulls and winged death's
  heads, are striking examples of early colonial funerary art. The
  adjacent Witch Trials Memorial makes this a natural pairing for
  visitors interested in the 1692 events. The Charter Street
  Cemetery Welcome Center is now open in the seventeenth-century
  Pickman House next to the memorial. Please treat the cemetery
  with respect. Stay on established paths. Do not touch or sit
  on headstones. No rubbings permitted.
  """)

n("Howard Street Cemetery",
  """
  Howard Street Cemetery is Salem's most haunted burial ground.
  It sits near the site where Giles Corey was pressed to death in
  1692 for refusing to enter a plea at his witch trial.
  """,
  """
  Howard Street Cemetery sits on ground steeped in dark history.
  It is located near the site where Giles Corey was pressed to
  death on September 19, 1692. Corey, an eighty-one-year-old farmer,
  refused to enter a plea of guilty or not guilty at his witch
  trial. Under English law, a defendant who refused to plead could
  be subjected to peine forte et dure, strong and hard punishment.
  Heavy stones were placed on Corey's chest over the course of two
  days. Each time his tormentors demanded a plea, his only response
  was: More weight. He died without ever entering a plea, denying
  the court the power to try him and protecting his family's property
  from seizure. Howard Street Cemetery is considered one of Salem's
  most haunted locations. Ghost tour guides report unusual activity
  here, and visitors have described feelings of unease and cold
  spots. The cemetery is less manicured than Charter Street, adding
  to its eerie atmosphere. It is a fitting place to contemplate
  the courage of Giles Corey and the terrible cost of the 1692
  hysteria.
  """)


# ═══════════════════════════════════════════════════════════════════
# TOURS (16)
# ═══════════════════════════════════════════════════════════════════

n("AliJen Charters",
  """
  AliJen Charters operates out of 10 White Street near Salem Harbor.
  Captain Dan Grimes offers fishing trips for cod, haddock, stripers,
  and tuna off the Massachusetts coast.
  """,
  """
  AliJen Charters offers fishing trips out of Salem Harbor, captained
  by Dan Grimes, who holds a U.S. Coast Guard 100-Ton license. Captain
  Dan grew up on the water, working alongside his father on commercial
  fishing boats since he was six years old. Since 2014, he has focused
  on rod and reel fishing, specializing in cod, haddock, stripers,
  and tuna off the coast of Massachusetts. Salem's connection to
  the ocean goes back to its founding. The city was built on fishing
  and maritime trade, and the fishing tradition continues today.
  A trip with AliJen Charters connects you to this living heritage.
  You will sail out of the same harbor that once launched merchant
  ships to the East Indies and privateers against the British fleet.
  The charters accommodate anglers of all levels, from experienced
  fishermen to complete beginners. Departures from 10 White Street.
  Call 781-910-3776 for reservations and pricing. As Captain Dan
  says, it is not about the catch. It is about the adventure.
  """)

n("Bewitched Historical Tours",
  """
  Bewitched Historical Tours departs from the Halloween Museum at
  131 Essex Street. Fun, informative tours led by local historians
  covering what really happened in 1692. Visits twelve or more sites.
  """,
  """
  Bewitched Historical Tours offers fun and informative walking tours
  led by local historians, departing from the Halloween Museum at
  131 Essex Street. The tours cover what really happened in 1692,
  visiting twelve or more historic sites connected to the witch
  trials. Advance purchase is recommended through BewitchedTours.com,
  though tickets may be available at the Halloween Museum if space
  remains. The tour focuses on the real history rather than the
  Hollywood version. You will learn about the specific people
  involved, the social and political tensions that fueled the
  accusations, and the devastating aftermath. Salem's witch trial
  history is more complex and more human than the popular image
  suggests. It was not about pointy hats and broomsticks. It was
  about fear, jealousy, property disputes, and a justice system
  that failed catastrophically. Bewitched Historical Tours brings
  this nuanced story to life on the actual streets where it happened.
  Phone 978-498-4061.
  """)

n("Black Cat Tours",
  """
  Black Cat Tours operates from 234 Essex Street. Daytime historical
  tours and ghostly night tours. Voted one of the ten best ghost
  tours in the United States four years running.
  """,
  """
  Black Cat Tours offers both daytime historical tours and ghostly
  night tours, operating from the Black Cat Curiosity Shoppe at 234
  Essex Street. The company has been voted one of the ten best ghost
  tours in the United States four years in a row. The daytime tours
  focus on Salem's captivating true tales, covering the witch trials,
  maritime history, and literary heritage. The evening tours shift
  into darker territory, exploring Salem's ghost stories and haunted
  locations. The dual format lets visitors choose their experience
  based on their interests and their tolerance for spookiness.
  The Black Cat is a fitting mascot for a Salem tour company. Cats
  have been associated with witchcraft in European folklore for
  centuries, and Salem has embraced the black cat as an unofficial
  city symbol. Tickets are available at the shoppe on Essex Street
  or online at blackcatsalem.com. Walk-in availability varies by
  season. October is the busiest month, and advance booking is
  essential. Phone 978-239-9145.
  """)

n("Lighthouse & Harbor Tours",
  """
  Lighthouse and Harbor Tours depart from 10 Blaney Street. Explore
  Baker's Island Light, Misery Island, and Salem's coastline from
  June through September.
  """,
  """
  Lighthouse and Harbor Tours offer boat-based explorations of
  Salem's islands and coastline, departing from 10 Blaney Street
  near the Salem Ferry Terminal. From June through September, trips
  to Baker's Island Light, Misery Island, and specialty tours are
  available. Overnight stays and camping at Baker's Island Light
  are also offered for a truly unique experience. Baker's Island
  Lighthouse has guided ships into Salem Harbor since the early
  nineteenth century. The island itself is a private community
  accessible only by boat. Misery Island, managed by the Trustees
  of Reservations, offers hiking trails and stunning coastal views.
  Seeing Salem from the water provides a completely different
  perspective on the city. The harbor, the wharves, the lighthouse
  at the end of Derby Wharf, and the coastline stretching toward
  Marblehead all come into view. These boat tours connect you to
  the maritime heritage that defined Salem for centuries. Phone
  978-224-2036 for schedules and booking. Check the website for
  current offerings.
  """)

n("Mahi Harbor Cruises & Private Events",
  """
  Mahi Harbor Cruises depart from Pickering Wharf at 24 Congress
  Street. Daily harbor tours, private events, and custom outings
  from May through October. Boat drinks and food on board.
  """,
  """
  Mahi Harbor Cruises and Private Events operate daily from Pickering
  Wharf at 24 Congress Street, running from May through October.
  The fleet includes the Finback, a fifty-foot sightseeing boat
  for intimate groups, and the Hannah Glover, a spacious vessel
  with a heated main deck and open-air top deck for up to one
  hundred and fifty passengers. Every cruise includes fully stocked
  bars and grillable favorites. Mahi offers standard harbor tours,
  private events, weddings, rehearsal dinners, corporate outings,
  and custom experiences. Pickering Wharf, where the cruises depart,
  is one of Salem's most popular waterfront destinations. The wharf
  area combines shops, restaurants, and maritime atmosphere with
  harbor views. A harbor cruise adds another dimension to the Salem
  experience. From the water, you can see the city's skyline, the
  historic wharves, and the coastline stretching toward Marblehead
  and beyond. Phone Annie at 978-825-0001 or email events at
  mahicruises.com for pricing and availability.
  """)

n("Salem Food Tours",
  """
  Salem Food Tours starts at 159 Derby Street. Award-winning food
  and cultural walking tours since 2012. Sample local restaurants
  and learn about Salem's spice trade history. Wicked fun and
  delicious.
  """,
  """
  Salem Food Tours has been offering award-winning food and cultural
  walking tours since 2012. Tours depart from 159 Derby Street and
  combine delicious tastings at local shops and restaurants with
  stories about Salem's illustrious spice trade history and modern
  culinary scene. Salem was once the pepper capital of the world.
  In the late eighteenth century, Salem merchants controlled a
  significant portion of the global pepper trade, importing the
  spice directly from Sumatra. That history connects directly to
  the food culture you will taste on this tour. Featured on
  Chronicle and a multi-year TripAdvisor Certificate of Excellence
  winner, Salem Food Tours runs year-round and welcomes groups.
  They also offer three-hour private excursions for bridal parties,
  wedding parties, and families. The bridal tour includes cheese,
  wine, bubbly, chocolate, spice tastings, savory bites, and
  artisanal bread. Phone 978-594-8811. Email info at salemfoodtours
  dot com for private event inquiries.
  """)

n("Salem Ghosts Tours and Haunted Pub Crawls",
  """
  Salem Ghosts operates from 210 Essex Street. Google's top-rated
  tour company in Salem. Ghost tours, witch walks, hidden history,
  and haunted pub crawls. Veteran-owned.
  """,
  """
  Salem Ghosts is Google's top-rated and TripAdvisor's award-winning
  tour company, operating from 210 Essex Street. The company offers
  a wide range of experiences, from daytime historical walking tours
  to nighttime ghost hunts and haunted pub crawls. Veteran-owned
  and proudly women- and minority-led, Salem Ghosts employs expert
  local guides who bring Salem's most infamous stories to life.
  Daytime options include the Secrets of Salem Hidden History
  Experience and The Witches and Witchcraft of Salem tour. Evening
  offerings include the Ghosts, Witches and Hauntings ghost tour
  and the extended Echoes of Twilight Dead of Night tour. For
  adults twenty-one and over, the Salem Boos and Booze Haunted Pub
  Crawl combines ghostly tales with drinks at historic taverns.
  Tours visit iconic locations including the Witch House, the Old
  Burying Point Cemetery, and the Ropes Mansion from Hocus Pocus.
  The company hosts student groups, bachelorette parties, corporate
  events, and custom VIP tours. Tours depart daily year-round.
  Call or text 978-219-2380 or visit salemghosts.com.
  """)

n("Salem Historical Tours",
  """
  Salem Historical Tours is Salem's oldest tour company, at 8 Central
  Street. Over twenty-five years of history and ghost tours led by
  certified local guides. Look for the purple sign.
  """,
  """
  Salem Historical Tours is Salem's oldest, most respected, and most
  recommended tour company, celebrating over twenty-five years of
  operation from 8 Central Street. Their certified local guides lead
  history tours covering witchcraft, maritime history, and
  revolutionary history during the mornings and afternoons, and
  ghost and paranormal tours in the afternoons and evenings. The
  history tour explores four centuries of Salem, from its founding
  through maritime glory, revolution, industry, and pop culture.
  The 1692 Witchcraft Walk examines the demographics, theories, and
  personal stories behind the witch trial hysteria. The ghost tours
  take visitors into Salem's darker corners after nightfall. What
  sets Salem Historical Tours apart is the depth of knowledge their
  guides bring. These are not actors reading scripts. They are
  certified historians who can answer detailed questions and provide
  context that goes far beyond the standard tourist narrative. Look
  for their purple sign at 8 Central Street. Phone 978-745-0666.
  Website salemhistoricaltours.com.
  """)

n("Salem Kids Tours",
  """
  Salem Kids Tours starts at 316 Essex Street. Salem's only walking
  tour designed specifically for children and families. All guides
  are certified Massachusetts educators. Ages five and up.
  """,
  """
  Salem Kids Tours is Salem's only walking tour designed specifically
  for children and families, operating from 316 Essex Street. All
  tour guides are certified Massachusetts educators who know how to
  engage young minds with history, mystery, and a little magic.
  The tour is designed for ages five and up, with content that is
  age-appropriate while still being genuinely interesting. The tours
  feature interactive storytelling that keeps children engaged, true
  historical tales that fascinate all ages, and a kid's-eye view
  of Salem's captivating past. Parents consistently call it the best
  thing we did in Salem. Finding age-appropriate activities in Salem
  can be challenging. Many of the ghost tours and haunted attractions
  are designed for adults and can be too frightening for young
  children. Salem Kids Tours fills this gap, offering an experience
  that is educational, entertaining, and safe for the whole family.
  Visit salemkidstours.com to book or email Alicia at alicia at
  salemkidstours.com. Phone 978-766-1103.
  """)

n("Salem Night Tour",
  """
  Salem Night Tour departs nightly at eight from 127 Essex Street.
  Salem's haunt and history tour explores legends, history, and the
  infamous hysteria of 1692 by lantern light.
  """,
  """
  The Salem Night Tour is Salem's nightly haunt and history tour,
  departing at eight every evening from 127 Essex Street. Licensed
  guides lead you through the darkened streets of Salem, weaving
  together legends, history, and the infamous hysteria of 1692.
  The night tour format adds an atmospheric dimension that daytime
  tours cannot match. Salem's colonial buildings, gas-lit streets,
  and ancient cemeteries take on a different character after dark.
  Shadows deepen. The temperature drops. And the stories hit
  differently when you are standing in the dark next to a three-
  hundred-year-old graveyard. The tour covers Salem's major
  historical events and haunted locations, with guides who balance
  genuine historical knowledge with entertaining storytelling.
  This is not a scream-and-jump haunted house experience. It is
  a walking tour that uses the night to enhance the natural
  atmosphere of one of America's most historically rich cities.
  Purchase tickets online at salemghosttours.com. Phone 978-741-1170.
  """)

n("Salem Trolley",
  """
  The Salem Trolley departs from 8 Central Street. Ride the red
  trolley for a one-hour narrated tour covering nearly four hundred
  years of Salem history. Hop-on, hop-off convenience.
  """,
  """
  The Salem Trolley is Salem's original trolley tour, offering a
  one-hour narrated ride through nearly four hundred years of the
  city's diverse history. The distinctive red replica turn-of-the-
  century trolleys depart from 8 Central Street and cover major
  historical sites, neighborhoods, and landmarks that would take
  hours to explore on foot. For visitors with limited time or
  mobility, the trolley provides an efficient and comfortable
  overview of Salem. The trolley also offers Tales and Tombstones
  night tours during July, August, and October. As dusk transforms
  the city, the tour explores Salem's darker side, visiting scenes
  of murders and executions, hearing tales of ghosts, haunted hotels,
  underground passageways, and haunted islands. Beyond tours, Salem
  Trolley provides event transportation aboard their replica trolleys.
  Weddings, corporate events, and private parties can be transported
  in style and comfort. Phone 978-744-5469 or visit salemtrolley.com
  for schedules, tickets, and event booking. The Salem Trolley has
  served the city for over a quarter century.
  """)

n("Salem Uncovered Tours",
  """
  Salem Uncovered Tours meets at 1 Houdini Way. Afternoon witch
  trial history and evening dark history tours led by Salem's expert
  storytellers.
  """,
  """
  Salem Uncovered Tours offers premier walking tours led by expert
  storytellers, meeting at 1 Houdini Way. The company runs afternoon
  tours focused on the tragic history of the witch trials and
  evening tours exploring Salem's broader dark history. The name
  Salem Uncovered suggests peeling back the layers of myth and
  popular culture to reveal the real stories beneath. Salem's
  history has been dramatized, fictionalized, and commercialized
  so extensively that the truth can get lost. Tours like Salem
  Uncovered work to restore historical accuracy while maintaining
  the compelling narrative that makes these stories worth telling.
  The meeting point at Houdini Way connects to another layer of
  Salem's rich cultural history. Harry Houdini, the famous escape
  artist, had connections to the world of spiritualism and the
  supernatural that resonate with Salem's identity. Book tours
  directly through salemuncovered.com. Phone 978-791-2131. Advance
  booking recommended, especially for October visits.
  """)

n("Schooner Fame of Salem",
  """
  The Schooner Fame sails from Pickering Wharf at 86 Wharf Street.
  This replica of an 1812 privateer sails three to four times daily.
  Raise the sails, hear the cannon, and learn Salem's epic maritime
  history.
  """,
  """
  The Schooner Fame is the top-rated boat tour in Salem. This
  beautiful replica of the 1812 privateer Fame sails three to four
  times daily from Pickering Wharf at 86 Wharf Street, right
  downtown among the shops and restaurants. The original Fame was
  a Baltimore clipper-style privateer that captured or destroyed
  over twenty British vessels during the War of 1812. The replica
  offers a hands-on sailing experience. You can help raise the
  sails, listen to the roar of the deck cannon, and learn about
  Salem's epic maritime history from knowledgeable crew members.
  The trip provides stunning views of Salem's waterfront, harbor,
  and coastline that you simply cannot get from shore. Beer, wine,
  drinks, and snacks are available on board. The sunset trips are
  particularly popular and should be booked well in advance. The
  experience is family-friendly for children at least five years
  old. You have not really seen Salem until you have seen it from
  the water. Phone 978-729-7600 or visit schoonerfame.com for
  schedules and tickets.
  """)

n("Spellbound Tours",
  """
  Spellbound Tours meets at the Armory Park bell. Salem's original
  supernatural experience. Frightening, historically accurate tours
  by professional paranormal investigators. Witch trials, vampires,
  and voodoo.
  """,
  """
  Spellbound Tours is Salem's original supernatural tour experience,
  top-rated on TripAdvisor and led by professional paranormal
  investigators. Tours meet at the Armory Park bell and venture into
  Salem's most haunted and historically charged locations. The guides
  are not just storytellers. They are active paranormal investigators
  who bring equipment and experience to every tour. Spellbound covers
  a broader range of supernatural topics than most Salem tours. Yes,
  you will learn about the witch trials. But you will also explore
  New England vampire folklore, voodoo traditions, and other
  supernatural phenomena that have roots in this region. The approach
  is frightening and historically accurate, a combination that
  distinguishes Spellbound from tours that prioritize entertainment
  over substance. Located at 213 Essex Street, the tours are
  accessible from the heart of Salem's downtown. Advance ticket
  purchase through spellboundtours.com is recommended. Phone
  978-740-1876. October dates sell out quickly.
  """)

n("Sunset Sail Salem",
  """
  Sunset Sail Salem departs from 10 Blaney Street. Experience luxury
  sailing on a stunning 1930 schooner yacht. Craft cocktails on
  board. Breathtaking views. Sailing daily.
  """,
  """
  Sunset Sail Salem offers a truly premium sailing experience aboard
  a stunning 1930 schooner yacht, departing from 10 Blaney Street.
  The experience combines luxury, romance, and breathtaking coastal
  views in what the company calls Salem's truly authentic North Shore
  sailing experience. A cash bar with craft cocktails is available
  on board. The schooner sails all day, every day during the season,
  with sunset trips being the most popular and romantic option.
  Sailing out of Salem Harbor on a vintage schooner connects you
  to centuries of maritime tradition. Salem's harbor has launched
  everything from fishing boats and merchant ships to privateers
  and tall ships. The coastline, the islands, and the open water
  have been the backdrop for Salem's story since 1626. For a special
  occasion, a date night, or simply a break from walking tours
  and witch museums, a sunset sail offers a completely different
  Salem experience. Phone 305-697-1024 or visit sailwhenandif.com.
  Book ahead for sunset trips.
  """)

n("Tipples and Mash Tours",
  """
  Tipples and Mash Tours starts at 185 Essex Street. Salem's only
  walking tour of breweries, cideries, and distilleries. Learn
  Salem's brewing history while sampling the modern craft scene.
  """,
  """
  Tipples and Mash Tours offers Salem's only walking tour dedicated
  to breweries, cideries, and distilleries. Starting from 185 Essex
  Street, the tour combines Salem's brewing past with samples from
  the establishments making new history every day. Salem has a long
  and largely forgotten brewing tradition. In the colonial period,
  cider and small beer were everyday beverages. Taverns like the
  Ship Tavern and the Blue Anchor were social centers where news
  traveled, deals were made, and, in 1692, witch trial rumors
  spread. Today, Salem's craft beverage scene has revived this
  tradition with modern artisanship. Local breweries, cideries,
  and distilleries produce a range of drinks that reflect both
  New England tradition and contemporary craft innovation. The
  tour is perfect for visitors who love Salem's history but want
  to experience it through taste rather than just storytelling.
  It is also a great option for groups, date nights, or anyone
  who appreciates craft beverages. Visit tipplesandmash.com for
  schedules and booking.
  """)


# ═══════════════════════════════════════════════════════════════════
# VISITOR INFO (1)
# ═══════════════════════════════════════════════════════════════════

# Adapted from SalemPois.kt
n("National Park Service Visitor Center",
  """
  The National Park Service Visitor Center is the official starting
  point for Salem's Heritage Trail. Rangers provide free maps,
  information, and orientation to Salem's historic sites.
  """,
  """
  The National Park Service Regional Visitor Center is the best place
  to begin your exploration of Salem. Located near New Liberty Street,
  the center serves as the official starting point for the Salem
  Heritage Trail, a red line painted on the sidewalk that guides
  visitors to Salem's major historic sites. Rangers are on hand to
  provide free maps, brochures, and orientation to Salem's many
  attractions. A short film introduces Salem's history, from its
  founding in 1626 through the witch trials, the maritime golden
  age, and the literary legacy of Nathaniel Hawthorne. The visitor
  center is part of the Salem Maritime National Historical Park, the
  first National Historic Site in the United States. From here, you
  can easily reach the Custom House, Derby Wharf, the Peabody Essex
  Museum, and all of downtown Salem's major attractions on foot.
  Free admission. Open nine to five daily. Phone 978-740-1650. Start
  here, get your bearings, and let the Heritage Trail guide you
  through four centuries of American history.
  """)


# ═══════════════════════════════════════════════════════════════════
# SQL Generation
# ═══════════════════════════════════════════════════════════════════

def escape_sql(text):
    """Escape single quotes for SQL."""
    return text.replace("'", "''")


def main():
    with open(INPUT_FILE) as f:
        data = json.load(f)

    wave1_pois = [p for p in data["pois"] if p["category"] in WAVE1_CATEGORIES]

    sql_lines = []
    sql_lines.append("-- ============================================================")
    sql_lines.append("-- Wave 1 Narration Content for Salem Walking Tour")
    sql_lines.append(f"-- {len(wave1_pois)} POIs across {len(WAVE1_CATEGORIES)} categories")
    sql_lines.append("-- Generated by generate_narration_content.py")
    sql_lines.append("-- ============================================================")
    sql_lines.append("")
    sql_lines.append("BEGIN;")
    sql_lines.append("")

    matched = 0
    unmatched = 0
    cat_counts = {}
    total_short_words = 0
    total_long_words = 0

    for poi in wave1_pois:
        name = poi["name"]
        cat = poi["category"]
        cat_counts[cat] = cat_counts.get(cat, 0) + 1

        if name in NARRATIONS:
            narr = NARRATIONS[name]
            short = narr["short_narration"]
            long_text = narr["long_narration"]
            matched += 1
            total_short_words += len(short.split())
            total_long_words += len(long_text.split())

            sql_lines.append(f"-- [{cat}] {name}")
            sql_lines.append(
                f"UPDATE narration_points SET "
                f"short_narration = '{escape_sql(short)}', "
                f"long_narration = '{escape_sql(long_text)}' "
                f"WHERE name = '{escape_sql(name)}';"
            )
            sql_lines.append("")
        else:
            unmatched += 1
            sql_lines.append(f"-- MISSING NARRATION: [{cat}] {name}")
            sql_lines.append("")

    sql_lines.append("COMMIT;")

    os.makedirs(os.path.dirname(OUTPUT_FILE), exist_ok=True)
    with open(OUTPUT_FILE, "w") as f:
        f.write("\n".join(sql_lines))

    # Summary
    print("=" * 60)
    print("NARRATION CONTENT GENERATION SUMMARY")
    print("=" * 60)
    print(f"Total Wave 1 POIs: {len(wave1_pois)}")
    print(f"Narrations written: {matched}")
    print(f"Missing narrations: {unmatched}")
    print()
    print("Breakdown by category:")
    for cat in sorted(cat_counts.keys()):
        count = cat_counts[cat]
        narr_count = sum(
            1 for p in wave1_pois
            if p["category"] == cat and p["name"] in NARRATIONS
        )
        print(f"  {cat}: {narr_count}/{count}")
    print()
    print(f"Total short narration words: {total_short_words:,}")
    print(f"Total long narration words: {total_long_words:,}")
    print(f"Total words: {total_short_words + total_long_words:,}")
    print()
    print(f"SQL output: {OUTPUT_FILE}")
    if unmatched:
        print(f"\nWARNING: {unmatched} POIs have no narration content!")
        for poi in wave1_pois:
            if poi["name"] not in NARRATIONS:
                print(f"  [{poi['category']}] {poi['name']}")


if __name__ == "__main__":
    main()
