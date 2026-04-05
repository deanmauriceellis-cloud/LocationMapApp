/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.salemcontent.data

import com.example.salemcontent.pipeline.OutputTourPoi
import com.example.salemcontent.pipeline.Provenance

/**
 * Manually curated Salem tour POIs — Phase 5.
 * GPS coordinates verified against Google Maps / OpenStreetMap.
 * Every POI includes provenance metadata.
 */
object SalemPois {

    private val now = System.currentTimeMillis()
    private val curated = Provenance("manual_curated", 1.0f, "2026-04-03", now, now, 0L)
    private val nps = Provenance("manual_curated", 1.0f, "2026-04-03", now, now, 0L)

    fun all(): List<OutputTourPoi> = witchTrialsSites + maritimeSites + museums +
        literarySites + parksLandmarks + visitorServices

    // ═══════════════════════════════════════════════════════════════════
    // Witch Trials Sites
    // ═══════════════════════════════════════════════════════════════════

    val witchTrialsSites = listOf(
        OutputTourPoi(
            id = "witch_trials_memorial",
            name = "Salem Witch Trials Memorial",
            lat = 42.5126, lng = -70.8964,
            address = "24 Liberty St, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["memorial","historic"]""",
            shortNarration = "You are approaching the Salem Witch Trials Memorial. Dedicated in 1992 on the three hundredth anniversary of the trials. Twenty stone benches, each inscribed with the name of a victim, line this quiet park.",
            longNarration = "The Salem Witch Trials Memorial stands as a solemn tribute to the twenty innocent people executed during the witch hysteria of 1692. Designed by architect James Cutler and artist Maggie Smith, it was dedicated on the tercentenary of the trials in August 1992. Nobel Laureate Elie Wiesel delivered the dedication speech. Twenty granite benches jut from the stone walls, each bearing the name, method, and date of execution of a victim. The entrance features the victims' protests of innocence, their words literally cut off mid-sentence by the stone walls. Locust trees cast dappled shadows over the space, chosen for their historical association with the gallows. This memorial serves as a permanent reminder of what happens when fear, intolerance, and injustice go unchecked.",
            description = "Solemn memorial to the 20 victims of the 1692 witch trials. Twenty stone benches inscribed with victims' names and execution dates.",
            historicalPeriod = "1692 / dedicated 1992",
            admissionInfo = "Free",
            hours = "Open 24 hours",
            geofenceRadiusM = 40,
            priority = 1,
            provenance = curated
        ),
        OutputTourPoi(
            id = "salem_witch_museum",
            name = "Salem Witch Museum",
            lat = 42.5159, lng = -70.8937,
            address = "19½ Washington Square N, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["museum","historic"]""",
            shortNarration = "The Salem Witch Museum occupies a former church at the edge of Salem Common. Inside, thirteen stage sets with life-size figures tell the story of the 1692 witch trials through light and narration.",
            longNarration = "The Salem Witch Museum is one of the most visited attractions in Salem. Housed in a former mid-nineteenth century church, it uses thirteen stage sets with life-size figures to dramatize the events of the 1692 witch hysteria. The main presentation takes visitors through the accusations, the trials, and the executions, with particular focus on key figures like Tituba, Bridget Bishop, and Rebecca Nurse. A second exhibition called Witches: Evolving Perceptions traces the concept of the witch from biblical times through modern Wicca. The building itself, a Gothic Revival structure originally built in 1846, adds to the atmosphere. The museum has been operating since 1972 and has become one of Salem's most iconic landmarks, visible from across Salem Common with its distinctive pointed turrets.",
            description = "Premier museum of the 1692 witch trials. Life-size stage sets recreate the hysteria. Second exhibit traces witch mythology through history.",
            historicalPeriod = "1692 / museum since 1972",
            admissionInfo = "$18 adults, $13.50 children 6-14, free under 6",
            hours = "10:00 AM - 5:00 PM daily (extended October hours)",
            phone = "(978) 744-1692",
            website = "https://salemwitchmuseum.com",
            geofenceRadiusM = 50,
            priority = 1,
            provenance = curated
        ),
        OutputTourPoi(
            id = "witch_house",
            name = "The Witch House / Jonathan Corwin House",
            lat = 42.5167, lng = -70.8884,
            address = "310½ Essex St, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["historic_house","museum"]""",
            shortNarration = "This is the Witch House, the only structure still standing with direct ties to the Salem witch trials of 1692. Judge Jonathan Corwin lived here and conducted preliminary examinations of accused witches.",
            longNarration = "The Witch House is the only remaining building in Salem with direct connections to the witchcraft trials of 1692. Built around 1675, it was the home of Judge Jonathan Corwin, one of the magistrates who conducted preliminary examinations of accused witches. Several of these examinations likely took place in this very house. Corwin served on the Court of Oyer and Terminer that tried and convicted the accused. The house has been restored to its seventeenth-century appearance and serves as a museum of life in the late sixteen hundreds. Period furnishings, apothecary items, and architectural details give visitors a window into Puritan daily life. The building was nearly demolished in the 1940s but was saved and moved slightly from its original location to accommodate road widening.",
            description = "Only surviving building directly tied to the 1692 witch trials. Home of Judge Jonathan Corwin, where examinations of accused witches took place.",
            historicalPeriod = "c. 1675",
            admissionInfo = "$12 adults, $10 seniors/students, $8 children 6-14",
            hours = "10:00 AM - 5:00 PM (seasonal, mid-March to November)",
            phone = "(978) 744-8815",
            website = "https://thewitchhouse.org",
            geofenceRadiusM = 30,
            priority = 1,
            provenance = curated
        ),
        OutputTourPoi(
            id = "proctors_ledge",
            name = "Proctor's Ledge Memorial",
            lat = 42.5195, lng = -70.9029,
            address = "7 Pope St, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["memorial","execution_site"]""",
            shortNarration = "This is Proctor's Ledge, confirmed in 2016 as the site where nineteen people were hanged for witchcraft in 1692. A memorial was dedicated here in July 2017.",
            longNarration = "Proctor's Ledge is the confirmed execution site of the Salem witch trials. For centuries, the exact location of the hangings was debated, with Gallows Hill often cited. In January 2016, a research team led by historian Emerson Baker used historical documents, topographic analysis, and witness testimonies to conclusively identify this small rocky outcropping as the true execution site. The first victim, Bridget Bishop, was hanged here on June 10, 1692. Over the next four months, eighteen more people followed her to the gallows. The memorial, dedicated on July 19, 2017, the three hundred and twenty-fifth anniversary of the hangings of five victims on that date in 1692, consists of stone walls inscribed with the names and execution dates of all nineteen victims hanged here. Giles Corey, pressed to death at a different location, is also commemorated.",
            description = "Confirmed execution site of the 1692 witch trials. 19 people were hanged here. Memorial dedicated 2017.",
            historicalPeriod = "1692 / memorial 2017",
            admissionInfo = "Free",
            hours = "Open dawn to dusk",
            geofenceRadiusM = 35,
            priority = 1,
            provenance = curated
        ),
        OutputTourPoi(
            id = "charter_street_cemetery",
            name = "Charter Street Cemetery / Old Burying Point",
            lat = 42.5132, lng = -70.8951,
            address = "51 Charter St, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["cemetery","historic"]""",
            shortNarration = "Charter Street Cemetery is one of the oldest burying grounds in the United States, established in 1637. Judge John Hathorne, ancestor of Nathaniel Hawthorne, is buried here.",
            longNarration = "Charter Street Cemetery, also known as the Old Burying Point, is the oldest burying ground in Salem and one of the oldest in the entire United States, established in 1637. The cemetery contains the graves of several figures connected to the witch trials, including Judge John Hathorne, the most zealous of the Salem witch trial magistrates and a direct ancestor of author Nathaniel Hawthorne. Governor Simon Bradstreet, who helped end the trials, is also interred here. Other notable burials include Captain Richard More, a Mayflower passenger. The cemetery's weathered slate headstones, many featuring carved skulls and winged death's heads, are striking examples of early colonial funerary art. The adjacent Witch Trials Memorial makes this a natural pairing for visitors interested in the 1692 events.",
            description = "One of America's oldest cemeteries (1637). Contains graves of witch trial judge Hathorne and Governor Bradstreet. Adjacent to Witch Trials Memorial.",
            historicalPeriod = "1637",
            admissionInfo = "Free",
            hours = "Daylight hours",
            geofenceRadiusM = 50,
            priority = 2,
            provenance = curated
        ),
        OutputTourPoi(
            id = "salem_jail_site",
            name = "Old Salem Jail Site",
            lat = 42.5176, lng = -70.8924,
            address = "Federal St & St. Peter's St, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["historic_site","jail"]""",
            shortNarration = "Near this location stood the Salem jail where accused witches were held in 1692. Conditions were appalling. Several accused died in custody before trial.",
            description = "Site of the 1692 jail where accused witches were held. Multiple accused died in these terrible conditions before ever reaching trial.",
            historicalPeriod = "1692",
            admissionInfo = "Free (exterior site marker)",
            geofenceRadiusM = 30,
            priority = 3,
            provenance = curated
        ),
        OutputTourPoi(
            id = "court_house_site",
            name = "Court House Site (1692 Trials)",
            lat = 42.5153, lng = -70.8928,
            address = "70 Washington St, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["historic_site","courthouse"]""",
            shortNarration = "Near this spot on Washington Street, the Court of Oyer and Terminer convened in 1692 to try the accused witches. It was here that the condemned were sentenced to death.",
            description = "Site where the Court of Oyer and Terminer tried accused witches in 1692. The original building no longer stands.",
            historicalPeriod = "1692",
            admissionInfo = "Free (exterior marker)",
            geofenceRadiusM = 25,
            priority = 3,
            provenance = curated
        ),
        OutputTourPoi(
            id = "judge_hathorne_home",
            name = "Judge Hathorne's Home Site",
            lat = 42.5165, lng = -70.8913,
            address = "118 Washington St, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["historic_site","magistrate"]""",
            shortNarration = "Near this location on Washington Street stood the home of Judge John Hathorne, the most relentless interrogator during the 1692 witch trials. His leading questions and presumption of guilt helped seal the fate of many accused.",
            longNarration = "Judge John Hathorne lived near this spot on Washington Street. He was the chief examiner during the Salem witch trials, presiding over the preliminary hearings that determined whether the accused would face trial. Hathorne was notorious for his aggressive interrogation style. He assumed guilt before asking a single question, demanding that the accused explain why they were afflicting their accusers rather than allowing them to defend themselves. His most famous exchange was with Rebecca Nurse, the elderly grandmother whom he badgered relentlessly despite her protests of innocence. Unlike fellow magistrate Samuel Sewall, Hathorne never expressed regret for his role. He continued to serve in public office until his death in 1717. His great-great-grandson, the author Nathaniel Hawthorne, added a W to the family name, widely believed to be an attempt to distance himself from this ancestor's dark legacy.",
            description = "Site of Judge John Hathorne's home. The most aggressive witch trial interrogator, ancestor of Nathaniel Hawthorne. No structure remains.",
            historicalPeriod = "1692",
            admissionInfo = "Free (exterior site marker)",
            geofenceRadiusM = 25,
            priority = 3,
            provenance = curated
        ),
        OutputTourPoi(
            id = "sheriff_corwin_home",
            name = "Sheriff Corwin's Home Site",
            lat = 42.5155, lng = -70.8920,
            address = "148 Washington St, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["historic_site","sheriff"]""",
            shortNarration = "Near this spot on Washington Street lived High Sheriff George Corwin, the man who carried out the arrests and executions during the Salem witch trials. He personally seized the property of the accused.",
            longNarration = "High Sheriff George Corwin lived near this location on Washington Street. As the officer responsible for executing the warrants and sentences of the Court of Oyer and Terminer, Corwin played a central role in the physical enforcement of the witch trials. He arrested the accused, transported them to jail, and oversaw their executions at Proctor's Ledge. Corwin was also responsible for the pressing death of Giles Corey, who refused to enter a plea and was subjected to heavy stones placed on his chest over two days until he died. Corwin also seized the property and livestock of the condemned, enriching himself in the process. After the trials ended, he was so reviled that when he died in 1696 at age thirty, the family of one of his victims attached his body for debt. His remains were reportedly hidden in the basement of this house to protect them from further retribution.",
            description = "Site of High Sheriff George Corwin's home. He carried out arrests, executions, and the pressing death of Giles Corey. No structure remains.",
            historicalPeriod = "1692",
            admissionInfo = "Free (exterior site marker)",
            geofenceRadiusM = 25,
            priority = 3,
            provenance = curated
        ),
        OutputTourPoi(
            id = "rebecca_nurse_homestead",
            name = "Rebecca Nurse Homestead",
            lat = 42.5630, lng = -70.9380,
            address = "149 Pine St, Danvers, MA 01923",
            category = "witch_trials",
            subcategories = """["historic_house","museum","danvers"]""",
            shortNarration = "The Rebecca Nurse Homestead stands in what was Salem Village, now Danvers. Rebecca Nurse, a pious seventy-one-year-old grandmother, was among the most sympathetic victims of the trials.",
            longNarration = "The Rebecca Nurse Homestead is one of the most important surviving sites of the Salem witch trials. Located in Danvers, the original Salem Village, this was the home of Rebecca Nurse, a seventy-one-year-old grandmother whose arrest and execution shocked many colonists and helped turn public opinion against the trials. Despite a jury initially finding her not guilty, the judges sent the jury back. She was convicted and hanged on July 19, 1692. The homestead includes the original 1678 house, a reproduction of the 1672 Salem Village meetinghouse, and a family cemetery where Rebecca's remains are believed to be interred. The site offers one of the most authentic connections to life in seventeenth-century Salem Village.",
            description = "Home of Rebecca Nurse, wrongfully executed in 1692. Includes original house, reproduction meetinghouse, and family cemetery in Danvers (original Salem Village).",
            historicalPeriod = "1678",
            admissionInfo = "$8 adults, $6 seniors/students, $4 children",
            hours = "Seasonal — check website",
            website = "https://rebeccanurse.org",
            geofenceRadiusM = 60,
            requiresTransportation = true,
            priority = 2,
            provenance = curated
        ),
        // ── New POIs from Kate seed data (Session 83) ──────────────────
        OutputTourPoi(
            id = "noyes_home_site",
            name = "Rev. Nicholas Noyes Home Site",
            lat = 42.5159, lng = -70.8920,
            address = "90 Washington St, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["historic_site","minister"]""",
            shortNarration = "Reverend Nicholas Noyes lived near this spot. He attended examinations, pressured prisoners to confess, and stood at the gallows exhorting the condemned. Sarah Good cursed him from the rope: I am no more a witch than you are a wizard, and if you take away my life, God will give you blood to drink.",
            longNarration = "Reverend Nicholas Noyes was born in 1647, educated at Harvard, and served as the assistant minister of Salem's First Church. He brought unwavering theological certainty to the witch trials. Noyes attended examinations, visited prisoners in jail demanding confessions, and stood at the foot of the gallows pressing the condemned to repent. His most notorious moment came on July 19, 1692, when he exhorted Sarah Good to confess before her hanging. Good, defiant to the end, turned to him and spoke the words that would haunt his remaining years: I am no more a witch than you are a wizard, and if you take away my life, God will give you blood to drink. When he died in 1717, reportedly choking on his own blood from a hemorrhage, the community whispered that Good's prophecy had been fulfilled. Unlike his colleague Samuel Sewall, who publicly repented in 1697, Noyes never expressed doubt or remorse.",
            description = "Approximate site of the home of Rev. Nicholas Noyes, the most aggressive clerical supporter of the 1692 witch executions. Sarah Good cursed him from the gallows.",
            historicalPeriod = "1692",
            admissionInfo = "Free (street viewing, original building gone)",
            geofenceRadiusM = 30,
            priority = 3,
            provenance = curated
        ),
        OutputTourPoi(
            id = "bridget_bishop_home",
            name = "Bridget Bishop's Home Site",
            lat = 42.5148, lng = -70.8930,
            address = "71 Washington St, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["historic_site","victim"]""",
            shortNarration = "Bridget Bishop lived near this spot and ran a tavern that drew complaints for serving cider late and permitting games of shovelboard. She wore a red bodice, had been married three times, and had been accused of witchcraft once before in sixteen eighty. She was the perfect first target.",
            longNarration = "Bridget Bishop was the first person executed in the Salem witch trials, hanged at Proctor's Ledge on June 10, 1692. She was approximately sixty years old. She had been married three times, ran an unlicensed tavern near this location, and wore clothing that scandalized Puritan sensibilities, particularly a red bodice that was entered into evidence at her trial. She had been tried for witchcraft once before, in 1680, and acquitted. When examined before the magistrates, she said simply: I am no witch. I know not what a witch is. She was chosen first because she had no powerful family to object, no faction to protect her, and a reputation that the community had already convicted years before the court convened.",
            description = "Approximate site of Bridget Bishop's home and tavern. She was the first person executed in the Salem witch trials, hanged June 10, 1692.",
            historicalPeriod = "1692",
            admissionInfo = "Free (street viewing, original building gone)",
            geofenceRadiusM = 30,
            priority = 2,
            provenance = curated
        ),
        OutputTourPoi(
            id = "first_church_1692_site",
            name = "First Church Meetinghouse 1692 Site",
            lat = 42.5190, lng = -70.8907,
            address = "231 Essex St, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["historic_site","church"]""",
            shortNarration = "The First Church meetinghouse stood near this spot in 1692. It was the largest gathering place in Salem Town. When the examination hearings outgrew the smaller spaces in Salem Village, they moved here to accommodate the crowds.",
            description = "Site of the First Church of Salem's 1692 meetinghouse where preliminary witch trial examinations were held. Now the Daniel Low Building.",
            historicalPeriod = "1692",
            admissionInfo = "Free (exterior viewing, original building gone)",
            geofenceRadiusM = 30,
            priority = 3,
            provenance = curated
        ),
        OutputTourPoi(
            id = "ship_tavern_site",
            name = "Ship Tavern Site",
            lat = 42.5195, lng = -70.8895,
            address = "188 Essex St, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["historic_site","tavern"]""",
            shortNarration = "The Ship Tavern stood near this location on Essex Street. In 1692, taverns were where news traveled, rumors spread, and public opinion formed. It was in places like this that the accusations gained their terrible momentum.",
            description = "Approximate site of the Ship Tavern, a key public gathering place in 1692 where trial rumors and accusations spread through the community.",
            historicalPeriod = "1692",
            admissionInfo = "Free (street viewing, original building gone)",
            geofenceRadiusM = 25,
            priority = 4,
            provenance = curated
        ),
        OutputTourPoi(
            id = "philip_english_house",
            name = "Philip English's Great House Site",
            lat = 42.5210, lng = -70.8930,
            address = "Essex St & English St, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["historic_site","merchant"]""",
            shortNarration = "Philip English's mansion stood near this corner. Born Philippe d'Anglois on the Isle of Jersey, he built a fortune of twenty-one ships, fourteen buildings, and a warehouse empire. In 1692, both he and his wife Mary were accused of witchcraft.",
            longNarration = "Philip English was born Philippe d'Anglois on the Isle of Jersey in 1651. He arrived in Salem in the 1670s and built the largest commercial fortune in Essex County: twenty-one vessels, fourteen buildings, a warehouse on the wharf, and this mansion on Essex Street. When the accusations reached Salem's elite in 1692, both Philip and Mary were named. They escaped imprisonment and fled to New York. They returned after the trials, but Mary died in 1694 from the effects of imprisonment. English spent forty-two years pursuing restitution. When Sheriff Corwin died in 1696, English reportedly attempted to seize the sheriff's body for debt. English Street preserves his name.",
            description = "Site of Philip English's grand mansion. Salem's wealthiest merchant, accused of witchcraft with his wife. English Street is named for him.",
            historicalPeriod = "1692",
            admissionInfo = "Free (street viewing, original building gone)",
            geofenceRadiusM = 30,
            priority = 3,
            provenance = curated
        ),
        OutputTourPoi(
            id = "ann_pudeator_home",
            name = "Ann Pudeator Home Site",
            lat = 42.5205, lng = -70.8950,
            address = "35 Washington Square N, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["historic_site","victim"]""",
            shortNarration = "Ann Pudeator lived near this location. She was a widow who worked as a nurse and healer. Her knowledge of medicine and herbs was used as evidence against her in court. She was hanged on September twenty-second, 1692.",
            description = "Approximate site of the home of Ann Pudeator, a widow and healer hanged September 22, 1692. Her medical skills were used as evidence of witchcraft. Private residence.",
            historicalPeriod = "1692",
            admissionInfo = "Free (exterior viewing only — private residence)",
            geofenceRadiusM = 25,
            priority = 3,
            provenance = curated
        ),
        OutputTourPoi(
            id = "alice_parker_home",
            name = "Alice Parker Home Site",
            lat = 42.5162, lng = -70.8880,
            address = "54 Derby St, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["historic_site","victim"]""",
            shortNarration = "Alice Parker lived near this spot on Derby Street. She was the wife of a fisherman, an ordinary woman with no power, no wealth, and no means of defense. She was hanged on September twenty-second, 1692.",
            description = "Approximate site of Alice Parker's home. A fisherman's wife hanged September 22, 1692, in the last mass execution.",
            historicalPeriod = "1692",
            admissionInfo = "Free (street viewing, original building gone)",
            geofenceRadiusM = 25,
            priority = 3,
            provenance = curated
        ),
        OutputTourPoi(
            id = "blue_anchor_tavern",
            name = "Blue Anchor Tavern Site",
            lat = 42.5168, lng = -70.8872,
            address = "60 Derby St, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["historic_site","tavern"]""",
            shortNarration = "The Blue Anchor Tavern stood near this spot on Derby Street, close to the wharves where Salem's ships docked. In 1692, sailors, merchants, and townsfolk gathered here. Rumors traveled fast in taverns like this.",
            description = "Site of the Blue Anchor Tavern near Salem's wharves. Waterfront taverns were where rumors spread and accusations gained momentum in 1692.",
            historicalPeriod = "1692",
            admissionInfo = "Free (street viewing, original building gone)",
            geofenceRadiusM = 25,
            priority = 4,
            provenance = curated
        )
    )

    // ═══════════════════════════════════════════════════════════════════
    // Maritime & National Historic Sites
    // ═══════════════════════════════════════════════════════════════════

    val maritimeSites = listOf(
        OutputTourPoi(
            id = "salem_maritime_nhp",
            name = "Salem Maritime National Historical Park",
            lat = 42.5197, lng = -70.8845,
            address = "160 Derby St, Salem, MA 01970",
            category = "maritime",
            subcategories = """["nps","historic","free"]""",
            shortNarration = "Salem Maritime National Historical Park is the first National Historic Site in the United States, established in 1938. It preserves Salem's rich maritime heritage from the era when Salem was one of the wealthiest ports in America.",
            longNarration = "Salem Maritime National Historical Park was the first National Historic Site established in the United States, designated by Congress in 1938. The park preserves and interprets the maritime history of New England and the United States. During the late eighteenth century, Salem was one of the richest cities in America, its wealth built on international trade. Ships from Salem sailed to the East Indies, China, Africa, and beyond. The park includes twelve historic structures, a replica tall ship, and approximately ten acres of land along the waterfront. Key sites within the park include the Custom House, where Nathaniel Hawthorne worked as surveyor, Derby Wharf, the Scale House, and the West India Goods Store. Rangers offer free tours and programs throughout the season.",
            description = "First National Historic Site in the US (1938). Twelve historic structures, Derby Wharf, Custom House, and Friendship tall ship replica. Free admission.",
            historicalPeriod = "18th-19th century / NPS 1938",
            admissionInfo = "Free (some guided programs have fees)",
            hours = "9:00 AM - 5:00 PM daily",
            phone = "(978) 740-1650",
            website = "https://www.nps.gov/sama",
            geofenceRadiusM = 80,
            priority = 1,
            provenance = nps
        ),
        OutputTourPoi(
            id = "custom_house",
            name = "Custom House",
            lat = 42.5194, lng = -70.8846,
            address = "178 Derby St, Salem, MA 01970",
            category = "maritime",
            subcategories = """["nps","historic","hawthorne"]""",
            shortNarration = "The Custom House is where Nathaniel Hawthorne worked as surveyor from 1846 to 1849. It was here, he claimed, that he discovered the scarlet letter that inspired his famous novel.",
            description = "Federal-era custom house where Nathaniel Hawthorne worked as surveyor. Inspired the opening of The Scarlet Letter. Part of Salem Maritime NHP.",
            historicalPeriod = "1819",
            admissionInfo = "Free (within Salem Maritime NHP)",
            geofenceRadiusM = 30,
            priority = 2,
            provenance = nps
        ),
        OutputTourPoi(
            id = "derby_wharf",
            name = "Derby Wharf",
            lat = 42.5191, lng = -70.8827,
            address = "Derby St, Salem, MA 01970",
            category = "maritime",
            subcategories = """["nps","wharf","waterfront"]""",
            shortNarration = "Derby Wharf extends nearly half a mile into Salem Harbor. Built by the wealthy merchant Elias Hasket Derby, it was once the center of Salem's international trade empire.",
            description = "Half-mile wharf built by merchant Elias Hasket Derby. Friendship tall ship replica moored here. Derby Wharf Light Station at the end. Part of Salem Maritime NHP.",
            historicalPeriod = "1762",
            admissionInfo = "Free",
            hours = "Open dawn to dusk",
            geofenceRadiusM = 60,
            priority = 2,
            provenance = nps
        ),
        OutputTourPoi(
            id = "derby_wharf_light",
            name = "Derby Wharf Light Station",
            lat = 42.5163, lng = -70.8787,
            address = "End of Derby Wharf, Salem, MA 01970",
            category = "maritime",
            subcategories = """["nps","lighthouse","waterfront"]""",
            shortNarration = "The Derby Wharf Light Station stands at the far end of the half-mile wharf. This small square lighthouse has guided vessels into Salem Harbor since 1871.",
            longNarration = "The Derby Wharf Light Station sits at the outermost point of Derby Wharf, marking the entrance to Salem Harbor. The current structure, a small square brick tower, was built in 1871 to replace an earlier wooden lighthouse. It guided merchant ships, fishing boats, and later pleasure craft safely into port. The light was automated in 1977 and is now maintained by the Coast Guard as an active aid to navigation. Walking the full length of the wharf to reach this lighthouse offers panoramic views of Salem Harbor, Marblehead Neck, and the open Atlantic. On a clear day you can see all the way to Baker's Island. The walk out and back is one of Salem's most peaceful experiences.",
            description = "1871 lighthouse at the tip of Derby Wharf. Active aid to navigation. Panoramic harbor views from the half-mile walk along the wharf.",
            historicalPeriod = "1871",
            admissionInfo = "Free",
            hours = "Dawn to dusk",
            geofenceRadiusM = 30,
            priority = 3,
            provenance = nps
        ),
        OutputTourPoi(
            id = "narbonne_house",
            name = "Narbonne House",
            lat = 42.5197, lng = -70.8855,
            address = "71 Essex St, Salem, MA 01970",
            category = "maritime",
            subcategories = """["nps","historic_house","colonial"]""",
            shortNarration = "The Narbonne House dates to around 1675, making it one of the oldest houses in Salem. It sits within the Salem Maritime National Historical Park and illustrates how ordinary families lived in colonial Salem.",
            longNarration = "The Narbonne House, built around 1675, is one of the oldest surviving houses in Salem and a rare example of a modest colonial dwelling. Unlike the grand merchant homes that dominate Salem's historic landscape, this house tells the story of working-class life in colonial New England. Multiple generations of families lived here over more than three centuries, each leaving architectural layers that archaeologists and historians have carefully documented. The house sits within the Salem Maritime National Historical Park and provides a fascinating contrast to the nearby Custom House and grand wharf buildings. Its survival is remarkable. While wealthy merchants built and rebuilt in fashionable styles, this humble First Period house endured virtually unchanged.",
            description = "One of Salem's oldest houses (c. 1675). Rare example of modest colonial dwelling within Salem Maritime NHP. Architectural layers from 300+ years.",
            historicalPeriod = "c. 1675",
            admissionInfo = "Free (exterior only; interior by ranger tour)",
            hours = "Seasonal — check with NPS",
            geofenceRadiusM = 25,
            priority = 3,
            provenance = nps
        )
    )

    // ═══════════════════════════════════════════════════════════════════
    // Museums & Cultural
    // ═══════════════════════════════════════════════════════════════════

    val museums = listOf(
        OutputTourPoi(
            id = "peabody_essex_museum",
            name = "Peabody Essex Museum",
            lat = 42.5216, lng = -70.8897,
            address = "161 Essex St, Salem, MA 01970",
            category = "museum",
            subcategories = """["art","maritime","cultural"]""",
            shortNarration = "The Peabody Essex Museum is one of the oldest continuously operating museums in America, founded in 1799. Its collections span art, culture, and maritime history from around the world.",
            longNarration = "The Peabody Essex Museum, known as PEM, is one of the oldest continuously operating museums in the United States, with roots dating to the East India Marine Society founded in 1799 by Salem sea captains. The museum's collections are vast. Over 1.8 million works span art, architecture, and culture from New England, maritime art, Asian art and culture, and more. Highlights include Yin Yu Tang, a two-hundred-year-old Chinese house transported and rebuilt inside the museum. The museum also holds significant collections of American decorative arts, fashion, and photography. Its modern wing, designed by Moshe Safdie, provides world-class gallery spaces. PEM regularly hosts major traveling exhibitions and is a cornerstone cultural institution of the North Shore.",
            description = "World-class museum founded 1799. Over 1.8 million works spanning art, maritime history, Asian culture. Features a 200-year-old Chinese house.",
            historicalPeriod = "Founded 1799",
            admissionInfo = "$22 adults, $20 seniors, $12 students, free under 16",
            hours = "10:00 AM - 5:00 PM Tue-Sun (closed Mondays except holidays)",
            phone = "(978) 745-9500",
            website = "https://www.pem.org",
            geofenceRadiusM = 50,
            priority = 1,
            provenance = curated
        ),
        OutputTourPoi(
            id = "house_seven_gables",
            name = "The House of the Seven Gables",
            lat = 42.5218, lng = -70.8827,
            address = "115 Derby St, Salem, MA 01970",
            category = "museum",
            subcategories = """["historic_house","literary","hawthorne"]""",
            shortNarration = "The House of the Seven Gables is the setting for Nathaniel Hawthorne's famous 1851 novel. Built in 1668, it is the oldest surviving timber-frame mansion in New England.",
            longNarration = "The House of the Seven Gables, built in 1668, is the oldest surviving timber-frame mansion in New England and the inspiration for Nathaniel Hawthorne's 1851 Gothic novel of the same name. Hawthorne's cousin Susanna Ingersoll lived here and her stories of the house's history inspired his masterwork. The campus includes several historic houses, including Hawthorne's own birthplace, which was moved here from its original Union Street location. Guided tours take visitors through the mansion, including its famous secret staircase, period rooms, and the stunning seaside gardens. The settlement also operates educational programs focused on immigrant history, as the neighborhood was home to waves of new Americans in the nineteenth and twentieth centuries.",
            description = "Oldest surviving timber-frame mansion in New England (1668). Inspired Hawthorne's novel. Campus includes Hawthorne's birthplace and seaside gardens.",
            historicalPeriod = "1668",
            admissionInfo = "$19 adults, $12 children 6-12, free under 6",
            hours = "10:00 AM - 5:00 PM daily",
            phone = "(978) 744-0991",
            website = "https://www.7gables.org",
            geofenceRadiusM = 50,
            priority = 1,
            provenance = curated
        ),
        OutputTourPoi(
            id = "pioneer_village",
            name = "Pioneer Village / Salem 1630",
            lat = 42.5290, lng = -70.8763,
            address = "98 West Ave, Salem, MA 01970",
            category = "museum",
            subcategories = """["living_history","colonial"]""",
            shortNarration = "Pioneer Village is a recreation of Salem's earliest settlement in 1630. It is the oldest living history museum in the United States, built in 1930 for the city's tercentenary.",
            description = "Oldest living history museum in the US (1930). Recreates Salem's 1630 settlement with thatched cottages, dugouts, and period demonstrations.",
            historicalPeriod = "Depicts 1630 / built 1930",
            admissionInfo = "Check website for current admission",
            hours = "Seasonal — June through October",
            geofenceRadiusM = 50,
            priority = 3,
            provenance = curated
        ),
        OutputTourPoi(
            id = "witch_dungeon_museum",
            name = "Witch Dungeon Museum",
            lat = 42.5234, lng = -70.8903,
            address = "16 Lynde St, Salem, MA 01970",
            category = "witch_trials",
            subcategories = """["museum","theater"]""",
            shortNarration = "The Witch Dungeon Museum features a live reenactment of a 1692 witch trial, adapted from actual court transcripts. After the show, tour the recreated dungeon.",
            description = "Live reenactment of a 1692 witch trial based on court transcripts. Includes tour of recreated dungeon where accused were held.",
            admissionInfo = "$14 adults, $12 children",
            hours = "10:00 AM - 5:00 PM (seasonal)",
            phone = "(978) 741-3570",
            geofenceRadiusM = 30,
            priority = 3,
            provenance = curated
        ),
        OutputTourPoi(
            id = "new_england_pirate_museum",
            name = "New England Pirate Museum",
            lat = 42.5195, lng = -70.8877,
            address = "274 Derby St, Salem, MA 01970",
            category = "museum",
            subcategories = """["pirate","family"]""",
            shortNarration = "The New England Pirate Museum explores the Golden Age of Piracy and Salem's connections to buccaneering. Walk through recreated scenes including a ship and a pirate's cave.",
            description = "Museum exploring Salem's pirate connections and the Golden Age of Piracy. Includes walk-through recreations of ship and cave scenes.",
            admissionInfo = "Check website for current admission",
            hours = "Seasonal",
            geofenceRadiusM = 30,
            priority = 4,
            provenance = curated
        )
    )

    // ═══════════════════════════════════════════════════════════════════
    // Literary Sites
    // ═══════════════════════════════════════════════════════════════════

    val literarySites = listOf(
        OutputTourPoi(
            id = "hawthorne_birthplace",
            name = "Hawthorne's Birthplace",
            lat = 42.5218, lng = -70.8827,
            address = "115 Derby St (on Seven Gables campus), Salem, MA 01970",
            category = "literary",
            subcategories = """["hawthorne","historic_house"]""",
            shortNarration = "Nathaniel Hawthorne was born in this house on July 4, 1804. It was originally located on Union Street and was moved to the House of the Seven Gables campus in 1958.",
            description = "Birth home of Nathaniel Hawthorne (1804). Moved to Seven Gables campus in 1958. Period furnishings from the Federal era.",
            historicalPeriod = "1750 (house) / Hawthorne born 1804",
            admissionInfo = "Included with House of the Seven Gables admission",
            geofenceRadiusM = 25,
            priority = 2,
            provenance = curated
        ),
        OutputTourPoi(
            id = "hawthorne_statue",
            name = "Nathaniel Hawthorne Statue",
            lat = 42.5209, lng = -70.8903,
            address = "Hawthorne Blvd & Essex St, Salem, MA 01970",
            category = "literary",
            subcategories = """["hawthorne","monument"]""",
            shortNarration = "This bronze statue of Nathaniel Hawthorne depicts Salem's most famous author seated with a book. He was born in Salem in 1804 and set several of his most famous works here.",
            description = "Bronze statue of Salem's most famous author. Hawthorne's works include The Scarlet Letter, The House of the Seven Gables, and Young Goodman Brown.",
            historicalPeriod = "Statue erected 1925",
            admissionInfo = "Free",
            geofenceRadiusM = 20,
            priority = 3,
            provenance = curated
        ),
        OutputTourPoi(
            id = "hawthorne_hotel",
            name = "Hawthorne Hotel",
            lat = 42.5207, lng = -70.8936,
            address = "18 Washington Square W, Salem, MA 01970",
            category = "literary",
            subcategories = """["hawthorne","hotel","historic"]""",
            shortNarration = "The Hawthorne Hotel, built in 1925, is one of the grand Federal-style hotels of New England. Reputedly one of the most haunted hotels in America, it overlooks Salem Common.",
            description = "Historic 1925 hotel overlooking Salem Common. Named for Nathaniel Hawthorne. Reportedly one of America's most haunted hotels.",
            historicalPeriod = "1925",
            admissionInfo = "Hotel (public areas accessible)",
            phone = "(978) 744-4080",
            website = "https://www.hawthornehotel.com",
            geofenceRadiusM = 40,
            priority = 3,
            provenance = curated
        ),
        OutputTourPoi(
            id = "castle_dismal",
            name = "Castle Dismal (Manning House)",
            lat = 42.5205, lng = -70.8862,
            address = "10½ Herbert St (now 21 Union St), Salem, MA 01970",
            category = "literary",
            subcategories = """["hawthorne","historic_house","childhood"]""",
            shortNarration = "This was the Manning family home where young Nathaniel Hawthorne spent much of his boyhood. He called it Castle Dismal, reflecting the gloomy, reclusive years he spent reading and dreaming in its upper rooms.",
            longNarration = "After the death of Nathaniel Hawthorne's father, a sea captain who died of yellow fever in Suriname when Nathaniel was just four years old, the family moved in with the Manning relatives at this house on Herbert Street. Young Hawthorne lived here through much of his childhood and into his twenties, spending long solitary years reading voraciously and beginning to write. He nicknamed the house Castle Dismal, a name that captures the brooding, isolated quality of those formative years. It was in this house that Hawthorne developed the introspective, shadow-haunted sensibility that would define his literary voice. The building has been altered over the centuries but still stands, a quiet landmark in Hawthorne's personal geography of Salem.",
            description = "Hawthorne's boyhood home, nicknamed Castle Dismal. Where the future author spent reclusive years reading and developing his literary voice.",
            historicalPeriod = "Early 1800s",
            admissionInfo = "Free (exterior only, private residence)",
            geofenceRadiusM = 20,
            priority = 4,
            provenance = curated
        ),
        OutputTourPoi(
            id = "scarlet_letter_house",
            name = "14 Mall Street (Scarlet Letter House)",
            lat = 42.5213, lng = -70.8896,
            address = "14 Mall St, Salem, MA 01970",
            category = "literary",
            subcategories = """["hawthorne","historic_house","writing"]""",
            shortNarration = "At 14 Mall Street, Nathaniel Hawthorne wrote The Scarlet Letter in the autumn and winter of 1849. He had just been fired from his position at the Custom House, and the loss drove him to produce his masterpiece.",
            longNarration = "It was at this modest house on Mall Street that Nathaniel Hawthorne wrote The Scarlet Letter, the novel that would make him one of the most important American authors. In June 1849, Hawthorne was abruptly removed from his position as surveyor at the Custom House, a political casualty of the change in presidential administration. Financially desperate and deeply bitter, he channeled his anger and his intimate knowledge of Salem's Puritan history into a sustained burst of creative energy. Working through the autumn and winter of 1849, he produced the novel in a matter of months. His wife Sophia later recalled that he would read chapters aloud to her, and that the final scene left him with a headache so intense he went to bed. The Scarlet Letter was published in March 1850 and was an immediate sensation.",
            description = "Where Hawthorne wrote The Scarlet Letter (1849-1850) after being fired from the Custom House. The novel was written in a burst of creative fury.",
            historicalPeriod = "1849-1850",
            admissionInfo = "Free (exterior only, private residence)",
            geofenceRadiusM = 20,
            priority = 3,
            provenance = curated
        )
    )

    // ═══════════════════════════════════════════════════════════════════
    // Parks & Landmarks
    // ═══════════════════════════════════════════════════════════════════

    val parksLandmarks = listOf(
        OutputTourPoi(
            id = "salem_common",
            name = "Salem Common",
            lat = 42.5218, lng = -70.8953,
            address = "31 Washington Square, Salem, MA 01970",
            category = "park",
            subcategories = """["green_space","historic"]""",
            shortNarration = "Salem Common is a nine-acre park in the heart of Salem. Used as common grazing land since the sixteen thirties, it is now surrounded by grand homes and anchored by the Witch Museum and Hawthorne Hotel.",
            description = "Nine-acre historic common in downtown Salem. Used since the 1630s. Surrounded by grand architecture, museums, and the Hawthorne Hotel.",
            historicalPeriod = "1630s",
            admissionInfo = "Free",
            hours = "Open 24 hours",
            geofenceRadiusM = 80,
            priority = 2,
            provenance = curated
        ),
        OutputTourPoi(
            id = "winter_island",
            name = "Winter Island Park",
            lat = 42.5253, lng = -70.8665,
            address = "50 Winter Island Rd, Salem, MA 01970",
            category = "park",
            subcategories = """["beach","lighthouse","camping"]""",
            shortNarration = "Winter Island Park offers beaches, camping, and the historic Fort Pickering lighthouse. During the witch trials, accused witches were held in a makeshift jail on this island.",
            description = "Waterfront park with beaches, camping, and Fort Pickering Light. Historical site — accused witches were jailed here in 1692.",
            historicalPeriod = "Fort 1643 / Lighthouse 1871",
            admissionInfo = "Parking fee in season",
            hours = "Dawn to dusk",
            geofenceRadiusM = 100,
            priority = 3,
            provenance = curated
        ),
        OutputTourPoi(
            id = "salem_willows",
            name = "Salem Willows Park",
            lat = 42.5310, lng = -70.8667,
            address = "167 Fort Ave, Salem, MA 01970",
            category = "park",
            subcategories = """["amusement","beach","family"]""",
            shortNarration = "Salem Willows is a seaside amusement area dating to the eighteen fifties. Named for the willow trees planted in 1801, it features an arcade, food stands, and waterfront views.",
            description = "Historic seaside park since the 1850s. Named for willows planted in 1801. Arcade games, food stands, beaches, and harbor views.",
            historicalPeriod = "1801 (willows) / amusement area 1850s",
            admissionInfo = "Free (parking and attractions have fees)",
            geofenceRadiusM = 80,
            priority = 3,
            provenance = curated
        ),
        OutputTourPoi(
            id = "roger_conant_statue",
            name = "Roger Conant Statue",
            lat = 42.5186, lng = -70.8950,
            address = "Brown St & Washington Square, Salem, MA 01970",
            category = "landmark",
            subcategories = """["monument","photo_op"]""",
            shortNarration = "This statue of Roger Conant, the founder of Salem in 1626, is one of the most photographed landmarks in the city. Visitors often mistake his Puritan cloak for a witch's outfit.",
            description = "Statue of Salem's founder Roger Conant (1626). Often mistaken for a witch due to his Puritan cloak. One of Salem's most photographed landmarks.",
            historicalPeriod = "Statue 1913 / Conant founded Salem 1626",
            admissionInfo = "Free",
            geofenceRadiusM = 20,
            priority = 2,
            provenance = curated
        ),
        OutputTourPoi(
            id = "ropes_mansion",
            name = "Ropes Mansion & Garden",
            lat = 42.5216, lng = -70.8916,
            address = "318 Essex St, Salem, MA 01970",
            category = "landmark",
            subcategories = """["historic_house","garden","hocus_pocus"]""",
            shortNarration = "The Ropes Mansion is a grand Colonial and Federal-style house dating to the seventeen twenties. Film fans may recognize it as the Allison house from the 1993 movie Hocus Pocus.",
            description = "1727 mansion with beautiful formal gardens. Famous as the Allison house in Hocus Pocus (1993). Owned by Peabody Essex Museum.",
            historicalPeriod = "1727",
            admissionInfo = "Free (exterior and gardens)",
            hours = "Gardens open daylight hours",
            geofenceRadiusM = 30,
            priority = 2,
            provenance = curated
        ),
        OutputTourPoi(
            id = "chestnut_street",
            name = "Chestnut Street Historic District",
            lat = 42.5174, lng = -70.8972,
            address = "Chestnut St, Salem, MA 01970",
            category = "landmark",
            subcategories = """["architecture","federal_style","residential"]""",
            shortNarration = "Chestnut Street is often called one of the most beautiful streets in America. Lined with grand Federal-style mansions built by Salem's wealthy sea captains in the early eighteen hundreds.",
            description = "One of America's most beautiful streets. Grand Federal-style mansions built by sea captains in the early 1800s. Part of the McIntire Historic District.",
            historicalPeriod = "Early 1800s",
            admissionInfo = "Free (public street, private homes)",
            geofenceRadiusM = 60,
            priority = 3,
            provenance = curated
        ),
        OutputTourPoi(
            id = "mcintire_historic_district",
            name = "McIntire Historic District",
            lat = 42.5178, lng = -70.8955,
            address = "Chestnut St & Federal St, Salem, MA 01970",
            category = "landmark",
            subcategories = """["architecture","federal_style","national_register"]""",
            shortNarration = "The McIntire Historic District is named for architect Samuel McIntire, who designed many of the grand mansions in this neighborhood. It is one of the finest collections of Federal-era architecture in the United States.",
            longNarration = "The McIntire Historic District encompasses several blocks of Salem's most architecturally significant streets, including Chestnut, Federal, and Essex Streets. Named for Samuel McIntire, a self-taught woodcarver and architect who became one of the most important builders of the Federal period, the district contains dozens of grand mansions built between roughly 1780 and 1830, when Salem was one of the wealthiest cities in America. McIntire's signature style combined elegant Federal proportions with exquisite carved ornament. His Hamilton Hall, built in 1805 as a social hall for Salem's elite, is a masterpiece of the style. The district is listed on the National Register of Historic Places and is considered one of the finest intact collections of Federal architecture in the country.",
            description = "One of America's finest Federal-era architecture districts. Named for architect Samuel McIntire. Grand mansions from Salem's maritime golden age.",
            historicalPeriod = "1780-1830",
            admissionInfo = "Free (public streets, private homes)",
            geofenceRadiusM = 80,
            priority = 3,
            provenance = curated
        )
    )

    // ═══════════════════════════════════════════════════════════════════
    // Visitor Services
    // ═══════════════════════════════════════════════════════════════════

    val visitorServices = listOf(
        OutputTourPoi(
            id = "nps_visitor_center",
            name = "NPS Regional Visitor Center",
            lat = 42.5216, lng = -70.8869,
            address = "2 New Liberty St, Salem, MA 01970",
            category = "visitor_services",
            subcategories = """["nps","information","heritage_trail"]""",
            shortNarration = "The National Park Service Regional Visitor Center is the official starting point for the Salem Heritage Trail. Rangers provide maps, information, and orientation to Salem's historic sites.",
            description = "Official NPS visitor center and start of the Heritage Trail. Free maps, brochures, film, and ranger information. Start your Salem visit here.",
            admissionInfo = "Free",
            hours = "9:00 AM - 5:00 PM daily",
            phone = "(978) 740-1650",
            geofenceRadiusM = 30,
            priority = 1,
            provenance = nps
        ),
        OutputTourPoi(
            id = "salem_mbta_station",
            name = "Salem MBTA Station",
            lat = 42.5240, lng = -70.8989,
            address = "252 Bridge St, Salem, MA 01970",
            category = "visitor_services",
            subcategories = """["transit","commuter_rail"]""",
            shortNarration = "Salem's MBTA commuter rail station connects the city to Boston's North Station. The Newburyport and Rockport lines stop here, making Salem an easy day trip from Boston.",
            description = "Commuter rail station connecting Salem to Boston's North Station via Newburyport/Rockport line. ~30 minute ride. Seasonal express service in October.",
            hours = "Per MBTA schedule",
            website = "https://www.mbta.com/stops/place-ER-0168",
            geofenceRadiusM = 50,
            priority = 2,
            provenance = curated
        ),
        OutputTourPoi(
            id = "salem_ferry_terminal",
            name = "Salem Ferry Terminal",
            lat = 42.5185, lng = -70.8789,
            address = "10 Blaney St, Salem, MA 01970",
            category = "visitor_services",
            subcategories = """["ferry","transit","seasonal"]""",
            shortNarration = "The Salem Ferry provides seasonal high-speed catamaran service to Boston's Long Wharf. The scenic fifty-minute ride across the harbor is an attraction in itself.",
            description = "Seasonal high-speed ferry to Boston's Long Wharf (~50 min). Operates late June through October. Scenic harbor crossing.",
            hours = "Seasonal — June through October",
            website = "https://www.salemferry.com",
            seasonal = true,
            geofenceRadiusM = 40,
            priority = 2,
            provenance = curated
        ),
        OutputTourPoi(
            id = "museum_place_garage",
            name = "Museum Place Garage",
            lat = 42.5213, lng = -70.8880,
            address = "1 New Liberty St, Salem, MA 01970",
            category = "visitor_services",
            subcategories = """["parking","ev_charging"]""",
            shortNarration = "Museum Place Garage is the most central parking garage in Salem, adjacent to the Peabody Essex Museum and NPS Visitor Center.",
            description = "Central parking garage near PEM and NPS Visitor Center. $1.25/hr. EV charging available. Best option for downtown Salem.",
            hours = "Open 24 hours",
            geofenceRadiusM = 30,
            priority = 4,
            provenance = curated
        ),
        OutputTourPoi(
            id = "south_harbor_garage",
            name = "South Harbor Garage",
            lat = 42.5168, lng = -70.8860,
            address = "10 Congress St, Salem, MA 01970",
            category = "visitor_services",
            subcategories = """["parking","ev_charging"]""",
            shortNarration = "South Harbor Garage is an alternative parking option near the waterfront, convenient for Derby Street, Pickering Wharf, and the House of the Seven Gables.",
            description = "Waterfront parking garage near Pickering Wharf and Derby Street. EV charging available. Good for waterfront attractions.",
            hours = "Open 24 hours",
            geofenceRadiusM = 30,
            priority = 4,
            provenance = curated
        )
    )
}
