/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.locationmapapp.util

import android.content.Context
import com.example.locationmapapp.data.model.FavoriteEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module FavoritesManager.kt"

class FavoritesManager(context: Context) {

    private val prefs = context.getSharedPreferences("favorites_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val KEY = "favorites_list"

    fun getFavorites(): List<FavoriteEntry> {
        val json = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<FavoriteEntry>>() {}.type)
        } catch (e: Exception) {
            DebugLogger.e("FavoritesManager", "Parse error", e)
            emptyList()
        }
    }

    fun addFavorite(entry: FavoriteEntry) {
        val list = getFavorites().toMutableList()
        // Avoid duplicates by osm key
        list.removeAll { it.osmType == entry.osmType && it.osmId == entry.osmId }
        list.add(0, entry)
        save(list)
        DebugLogger.i("FavoritesManager", "Added favorite: ${entry.name} (${list.size} total)")
    }

    fun removeFavorite(osmType: String, osmId: Long) {
        val list = getFavorites().toMutableList()
        list.removeAll { it.osmType == osmType && it.osmId == osmId }
        save(list)
        DebugLogger.i("FavoritesManager", "Removed favorite: $osmType/$osmId (${list.size} total)")
    }

    fun isFavorite(osmType: String, osmId: Long): Boolean {
        return getFavorites().any { it.osmType == osmType && it.osmId == osmId }
    }

    fun getCount(): Int = getFavorites().size

    private fun save(list: List<FavoriteEntry>) {
        prefs.edit().putString(KEY, gson.toJson(list)).apply()
    }
}
