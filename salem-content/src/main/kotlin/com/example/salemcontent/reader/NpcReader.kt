/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.salemcontent.reader

import com.example.salemcontent.model.JsonNpc
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object NpcReader {

    /** Read all NPCs and filter to Tier 1 + Tier 2 only. */
    fun read(salemRoot: File): List<JsonNpc> {
        val file = File(salemRoot, "data/json/npcs/_all_npcs.json")
        require(file.exists()) { "NPCs file not found: ${file.absolutePath}" }
        val type = object : TypeToken<List<JsonNpc>>() {}.type
        val all: List<JsonNpc> = Gson().fromJson(file.readText(), type)
        return all.filter { it.tier <= 2 }
    }

    /** Read all NPCs without tier filtering. */
    fun readAll(salemRoot: File): List<JsonNpc> {
        val file = File(salemRoot, "data/json/npcs/_all_npcs.json")
        require(file.exists()) { "NPCs file not found: ${file.absolutePath}" }
        val type = object : TypeToken<List<JsonNpc>>() {}.type
        return Gson().fromJson(file.readText(), type)
    }
}
