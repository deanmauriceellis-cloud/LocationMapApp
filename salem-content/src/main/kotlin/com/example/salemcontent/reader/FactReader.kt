/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.salemcontent.reader

import com.example.salemcontent.model.JsonFact
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

object FactReader {

    /** Read facts filtered to public/semi_private confidentiality. */
    fun read(salemRoot: File): List<JsonFact> {
        val file = File(salemRoot, "data/json/facts/_all_facts.json")
        require(file.exists()) { "Facts file not found: ${file.absolutePath}" }
        val type = object : TypeToken<List<JsonFact>>() {}.type
        val all: List<JsonFact> = Gson().fromJson(file.readText(), type)
        return all.filter { it.confidentiality in listOf("public", "semi_private") }
    }
}
