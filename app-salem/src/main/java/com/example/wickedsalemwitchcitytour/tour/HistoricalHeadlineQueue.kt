/*
 * WickedSalemWitchCityTour v1.5 — Phase 9R.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.tour

import android.content.Context
import android.content.SharedPreferences
import com.example.locationmapapp.util.DebugLogger
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module HistoricalHeadlineQueue.kt"

/**
 * Salem 1692 "newspaper dispatch" filler — read chronologically during
 * silence gaps in Historical Mode.
 *
 * Source: bundled JSON asset `assets/salem_1692_newspapers.json` — 202
 * period-voiced dispatches from SalemIntelligence's `/salem-1692/export`
 * corpus (each with pre-composed `ttsFullText` of ~2000-3500 chars, roughly
 * 2-3 min of TTS each). Ordered by date.
 *
 * Semantics:
 *  - [pollNext] returns the next unread dispatch, or null once all 202
 *    have been spoken. The caller advances the pointer via [advance]
 *    after successful TTS enqueue.
 *  - Pointer persists across app restarts in SharedPreferences so the
 *    listener picks up where they left off on the next walk.
 *  - **No-repeat semantics**: once all 202 are spoken, the queue stays
 *    silent until [reset] is invoked. No wraparound.
 *
 * Reader is async (JSON parse on Dispatchers.IO) but the JSON is ~1 MB so
 * first-load cost is minor (<100 ms typical).
 */
@Singleton
class HistoricalHeadlineQueue @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "HistNewspaper"
        private const val PREFS = "historical_headline_queue"
        // New key (v2) — isolates from the legacy v1 timeline_events pointer
        // so switching the source doesn't carry a stale index forward.
        private const val KEY_NEXT_INDEX = "newspaper_next_index_v2"
        private const val ASSET = "salem_1692_newspapers.json"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private data class Newspaper(
        val id: Int,
        val date: String,
        @SerializedName("longDate") val longDate: String?,
        @SerializedName("dayOfWeek") val dayOfWeek: String?,
        @SerializedName("crisisPhase") val crisisPhase: String?,
        val lede: String?,
        val summary: String?,
        @SerializedName("ttsFullText") val ttsFullText: String
    )

    private data class Bundle(
        val version: Int,
        val count: Int,
        val newspapers: List<Newspaper>
    )

    @Volatile private var newspapers: List<Newspaper>? = null
    private val loadMutex = Mutex()

    data class Headline(
        val id: String,
        val date: String,
        val name: String,    // human label (e.g. "Salem 1692 Dispatch — May 10th")
        val text: String,    // full TTS text, date prefix included
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
        // Overnight test mode: loop back to start when corpus is exhausted
        // instead of going silent. (Operator request to keep the stream
        // running through the night for stress data.)
        if (idx < 0 || idx >= list.size) {
            DebugLogger.i(TAG, "pollNext: corpus exhausted at idx=$idx — looping back to 0")
            idx = 0
            prefs.edit().putInt(KEY_NEXT_INDEX, 0).apply()
        }
        val n = list[idx]
        val label = "Salem 1692 Dispatch — ${n.longDate ?: n.date}"
        return Headline(
            id = n.id.toString(),
            date = n.date,
            name = label,
            text = formatDispatch(n),
            index = idx,
            total = list.size
        )
    }

    /** Advance pointer to next. Stops at end (no loop). */
    fun advance() {
        val total = newspapers?.size ?: return
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
     * Compose the spoken text. SI's `ttsFullText` is already TTS-ready;
     * we simply prefix a dateline ("Today in Salem, …") for the newsreader
     * framing and avoid pronouncing the ISO date.
     */
    private fun formatDispatch(n: Newspaper): String {
        val dateline = n.longDate ?: n.date
        return "Salem, $dateline. ${n.ttsFullText.trim()}"
    }

    private suspend fun loadOrGet(): List<Newspaper>? {
        newspapers?.let { return it }
        loadMutex.withLock {
            newspapers?.let { return it }
            return try {
                val loaded = withContext(Dispatchers.IO) {
                    context.assets.open(ASSET).bufferedReader().use { reader ->
                        Gson().fromJson(reader, Bundle::class.java)
                    }
                }
                val list = loaded.newspapers
                newspapers = list
                DebugLogger.i(TAG,
                    "loaded ${list.size} 1692 newspaper dispatches from $ASSET (v${loaded.version})")
                list
            } catch (e: Exception) {
                DebugLogger.e(TAG, "Failed to load $ASSET: ${e.message}", e)
                null
            }
        }
    }
}
