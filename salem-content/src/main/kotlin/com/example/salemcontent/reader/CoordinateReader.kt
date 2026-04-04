/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.salemcontent.reader

import com.example.salemcontent.model.BuildingCoordinate
import java.io.File

/**
 * Parses buildingCoordinates.ts to extract building grid positions.
 * The TS file contains an array of BuildingDef objects with id, name, x, y, zone, type.
 */
object CoordinateReader {

    private val ENTRY_REGEX = Regex(
        """id:\s*['"](\w+)['"].*?name:\s*['"](.+?)['"].*?x:\s*(\d+).*?y:\s*(\d+).*?zone:\s*['"](\w+)['"].*?type:\s*['"](\w+)['"]""",
        RegexOption.DOT_MATCHES_ALL
    )

    fun read(salemRoot: File): List<BuildingCoordinate> {
        val file = File(salemRoot, "client/src/data/buildingCoordinates.ts")
        require(file.exists()) { "Coordinates file not found: ${file.absolutePath}" }
        val text = file.readText()

        // Parse each object block between { }
        val results = mutableListOf<BuildingCoordinate>()
        val blockRegex = Regex("""\{[^}]+\}""")
        for (match in blockRegex.findAll(text)) {
            val block = match.value
            val entry = ENTRY_REGEX.find(block) ?: continue
            results.add(
                BuildingCoordinate(
                    id = entry.groupValues[1],
                    name = entry.groupValues[2],
                    x = entry.groupValues[3].toInt(),
                    y = entry.groupValues[4].toInt(),
                    zone = entry.groupValues[5],
                    type = entry.groupValues[6]
                )
            )
        }
        return results
    }
}
