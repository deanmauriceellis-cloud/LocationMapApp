/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.audio

/**
 * NarrationHistory — S145 Must-Have #45 rolling narration history.
 *
 * Ring buffer of the last [CAPACITY] narration entries for the nav cluster
 * (First / Prev / Next / Jump). Session-lifetime only — cleared on process
 * death, not persisted to disk.
 *
 * Per operator direction S145 Q4 Option 3: the First button jumps to the
 * oldest live entry in the rolling window, not to session-start.
 */
object NarrationHistory {

    private const val CAPACITY = 25

    enum class Kind { POI, ORACLE }

    data class Entry(
        val kind: Kind,
        val title: String,
        val text: String,
        /** POI id for POI entries; newspaper article id or figure id for Oracle entries. */
        val refId: String?,
        val voiceId: String?,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val buffer = ArrayDeque<Entry>()
    private var cursor: Int = -1

    @Synchronized
    fun add(entry: Entry) {
        if (buffer.size == CAPACITY) buffer.removeFirst()
        buffer.addLast(entry)
        cursor = buffer.size - 1
    }

    @Synchronized
    fun current(): Entry? = buffer.getOrNull(cursor)

    @Synchronized
    fun first(): Entry? {
        if (buffer.isEmpty()) return null
        cursor = 0
        return buffer.first()
    }

    @Synchronized
    fun prev(): Entry? {
        if (buffer.isEmpty()) return null
        if (cursor > 0) cursor--
        return buffer.getOrNull(cursor)
    }

    @Synchronized
    fun next(): Entry? {
        if (buffer.isEmpty()) return null
        if (cursor < buffer.size - 1) cursor++
        return buffer.getOrNull(cursor)
    }

    @Synchronized
    fun size(): Int = buffer.size

    @Synchronized
    fun clear() {
        buffer.clear()
        cursor = -1
    }
}
