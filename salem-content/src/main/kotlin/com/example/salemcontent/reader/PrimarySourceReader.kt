/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.salemcontent.reader

import com.example.salemcontent.model.JsonPrimarySource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object PrimarySourceReader {

    /**
     * Read primary sources, selecting the most impactful ~200.
     * Prioritizes: examinations, depositions, and documents with shorter token counts
     * (more suitable for TTS narration).
     */
    fun read(salemRoot: File, limit: Int = 200): List<JsonPrimarySource> {
        val file = File(salemRoot, "data/json/primary_sources/_all_primary_sources.json")
        require(file.exists()) { "Primary sources file not found: ${file.absolutePath}" }
        val type = object : TypeToken<List<JsonPrimarySource>>() {}.type
        val all: List<JsonPrimarySource> = Gson().fromJson(file.readText(), type)

        // Priority order: examinations and depositions first, then by token count
        val priorityTypes = listOf("examination", "deposition", "complaint", "warrant", "indictment")
        return all
            .sortedWith(
                compareByDescending<JsonPrimarySource> { it.docType in priorityTypes }
                    .thenBy { it.tokenCount ?: Int.MAX_VALUE }
            )
            .take(limit)
    }
}
