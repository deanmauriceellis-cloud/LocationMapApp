/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.salemcontent.reader

import com.example.salemcontent.model.JsonBuilding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object BuildingReader {

    fun read(salemRoot: File): List<JsonBuilding> {
        val file = File(salemRoot, "data/json/buildings/_all_buildings.json")
        require(file.exists()) { "Buildings file not found: ${file.absolutePath}" }
        val type = object : TypeToken<List<JsonBuilding>>() {}.type
        return Gson().fromJson(file.readText(), type)
    }
}
