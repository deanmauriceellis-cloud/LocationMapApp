/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.graphics.Color
import android.view.View
import android.widget.TextView
import com.example.locationmapapp.util.DebugLogger

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module StatusLineManager.kt"

/**
 * Priority-based status line manager.
 *
 * Owns the toolbar status line TextView and displays the highest-priority
 * active entry. When cleared, falls through to the next highest.
 */
class StatusLineManager(private val statusView: TextView) {

    private val TAG = "StatusLineManager"

    enum class Priority(val level: Int) {
        GPS_IDLE(0),
        SILENT_FILL(1),
        IDLE_POPULATE(2),
        FIND_FILTER(3),
        POPULATE(4),
        AIRCRAFT_FOLLOW(5),
        VEHICLE_FOLLOW(6),
        GEOFENCE_ALERT(7)
    }

    data class StatusEntry(
        val priority: Priority,
        val text: String,
        val bgColor: Int = Color.TRANSPARENT,
        val textColor: Int = Color.parseColor("#B0FFFFFF"),
        val onClick: (() -> Unit)? = null
    )

    private val entries = mutableMapOf<Priority, StatusEntry>()

    /**
     * Set/update a priority level. Refreshes display if this becomes the top priority.
     */
    fun set(entry: StatusEntry) {
        entries[entry.priority] = entry
        refresh()
    }

    /** Convenience: set a simple text entry at the given priority. */
    fun set(priority: Priority, text: String, onClick: (() -> Unit)? = null) {
        set(StatusEntry(priority, text, onClick = onClick))
    }

    /** Set with custom background color (for geofence alerts). */
    fun set(priority: Priority, text: String, bgColor: Int, textColor: Int = Color.WHITE, onClick: (() -> Unit)? = null) {
        set(StatusEntry(priority, text, bgColor, textColor, onClick))
    }

    /** Clear a priority level. Falls back to next highest. */
    fun clear(priority: Priority) {
        entries.remove(priority)
        refresh()
    }

    /** Convenience for updating the GPS idle status line. */
    fun updateIdle(lat: Double, lon: Double, speedMph: Double? = null, tempF: Int? = null, description: String? = null) {
        val parts = mutableListOf<String>()
        parts.add("%.4f, %.4f".format(lat, lon))
        if (speedMph != null && speedMph > 1.0) {
            parts.add("%.0f mph".format(speedMph))
        }
        if (tempF != null) {
            val wxDesc = description?.let { " $it" } ?: ""
            parts.add("${tempF}°F$wxDesc")
        }
        set(StatusEntry(Priority.GPS_IDLE, parts.joinToString("  •  ")))
    }

    /** Current displayed text (for debug endpoint). */
    fun currentText(): String = topEntry()?.text ?: ""

    /** Current displayed priority (for debug endpoint). */
    fun currentPriority(): Priority? = topEntry()?.priority

    private fun topEntry(): StatusEntry? {
        return entries.values.maxByOrNull { it.priority.level }
    }

    private fun refresh() {
        val top = topEntry()
        if (top != null) {
            statusView.text = top.text
            statusView.setBackgroundColor(top.bgColor)
            statusView.setTextColor(top.textColor)
            statusView.visibility = View.VISIBLE
            if (top.onClick != null) {
                statusView.isClickable = true
                statusView.setOnClickListener { top.onClick.invoke() }
            } else {
                statusView.isClickable = false
                statusView.setOnClickListener(null)
            }
        } else {
            statusView.text = ""
            statusView.visibility = View.VISIBLE
        }
    }
}
