/*
 * WickedSalemWitchCityTour v1.5 — Phase 9R.0 / S135
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.tour

import android.content.Context
import android.content.SharedPreferences
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.content.dao.WitchTrialsNewspaperDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module HistoricalHeadlineQueue.kt"

/**
 * Salem 1692 "newspaper dispatch" filler — read chronologically during
 * silence gaps in Historical Mode.
 *
 * S135: Switched from bundled JSON asset to Room DB so we get headlines
 * (headline + headline_summary fields added S130, populated from PG).
 * Spoken format: dateline → headline → full body.
 *
 * 202 dispatches ordered by date. Pointer persists in SharedPreferences.
 * Once all 202 are spoken, loops back to start.
 */
@Singleton
class HistoricalHeadlineQueue @Inject constructor(
    @ApplicationContext private val context: Context,
    private val newspaperDao: WitchTrialsNewspaperDao
) {
    companion object {
        private const val TAG = "HistNewspaper"
        private const val PREFS = "historical_headline_queue"
        private const val KEY_NEXT_INDEX = "newspaper_next_index_v2"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var papers: List<com.example.wickedsalemwitchcitytour.content.model.WitchTrialsNewspaper>? = null
    private val loadMutex = Mutex()

    data class Headline(
        val id: String,
        val date: String,
        val name: String,    // human label (e.g. "Salem 1692 Dispatch — May 10th")
        val text: String,    // full TTS text: dateline + headline + body
        val index: Int,
        val total: Int
    )

    /**
     * Next unread dispatch, or null when exhausted. Idempotent when called
     * without [advance] — caller must advance after successful speak.
     */
    suspend fun pollNext(): Headline? {
        val list = loadOrGet() ?: return null
        if (list.isEmpty()) return null
        var idx = prefs.getInt(KEY_NEXT_INDEX, 0)
        if (idx < 0 || idx >= list.size) {
            DebugLogger.i(TAG, "pollNext: corpus exhausted at idx=$idx — looping back to 0")
            idx = 0
            prefs.edit().putInt(KEY_NEXT_INDEX, 0).apply()
        }
        val n = list[idx]
        val dateline = n.longDate ?: n.date
        val label = "Salem 1692 Dispatch — $dateline"
        return Headline(
            id = n.id,
            date = n.date,
            name = label,
            text = formatDispatch(n),
            index = idx,
            total = list.size
        )
    }

    /** Advance pointer to next. */
    fun advance() {
        val total = papers?.size ?: return
        if (total == 0) return
        val idx = prefs.getInt(KEY_NEXT_INDEX, 0)
        val next = idx + 1
        prefs.edit().putInt(KEY_NEXT_INDEX, next).apply()
        if (next >= total) {
            DebugLogger.i(TAG, "advance: $idx → $next — corpus exhausted ($total), queue now silent")
        } else {
            DebugLogger.i(TAG, "advance: $idx → $next (of $total)")
        }
    }

    /** Reset to the first dispatch — for a fresh replay. */
    fun reset() {
        prefs.edit().putInt(KEY_NEXT_INDEX, 0).apply()
        DebugLogger.i(TAG, "reset to index 0")
    }

    /** Current pointer value. Useful for debug surfaces. */
    fun currentIndex(): Int = prefs.getInt(KEY_NEXT_INDEX, 0)

    /**
     * S135: Spoken format is dateline → headline → full body.
     * Example: "Salem, November 1, 1691. PARRIS STARVES! VILLAGE REBELS!
     * Today, the newly elected committee in Salem Village took direct action..."
     */
    private fun formatDispatch(
        n: com.example.wickedsalemwitchcitytour.content.model.WitchTrialsNewspaper
    ): String {
        val dateline = n.longDate ?: n.date
        val parts = mutableListOf<String>()
        parts.add("Salem, $dateline.")
        if (!n.headline.isNullOrBlank()) {
            parts.add(n.headline.trim())
        }
        parts.add(n.ttsFullText.trim())
        return parts.joinToString(" ")
    }

    private suspend fun loadOrGet(): List<com.example.wickedsalemwitchcitytour.content.model.WitchTrialsNewspaper>? {
        papers?.let { return it }
        loadMutex.withLock {
            papers?.let { return it }
            return try {
                val list = newspaperDao.findAll()
                papers = list
                DebugLogger.i(TAG, "loaded ${list.size} 1692 newspapers from Room DB")
                list
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Failed to load newspapers from Room: ${e.message}", e)
                null
            }
        }
    }
}
