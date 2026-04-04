/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.salemcontent.reader

import com.example.salemcontent.model.JsonEvent
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object EventReader {

    /** Read all anchor events + mandatory events. */
    fun read(salemRoot: File): List<JsonEvent> {
        val file = File(salemRoot, "data/json/events/_all_events.json")
        require(file.exists()) { "Events file not found: ${file.absolutePath}" }
        val type = object : TypeToken<List<JsonEvent>>() {}.type
        return Gson().fromJson<List<JsonEvent>>(file.readText(), type)
    }
}
