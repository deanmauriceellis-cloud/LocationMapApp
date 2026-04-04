/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.content.model.EventsCalendar
import com.example.wickedsalemwitchcitytour.content.model.TimelineEvent
import org.osmdroid.util.GeoPoint

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module SalemMainActivityEvents.kt"

private const val EVT_DARK = "#1E1E2A"
private const val EVT_SURFACE = "#2A2A3C"
private const val EVT_GOLD = "#C9A84C"
private const val EVT_TEXT = "#F5F0E8"
private const val EVT_TEXT_DIM = "#B8AFA0"
private const val EVT_ORANGE = "#FF9800"

// Event type → display name + color
private val EVENT_TYPE_MAP = mapOf(
    "haunted_tour" to ("Haunted Tour" to "#8B0000"),
    "museum_exhibit" to ("Museum Exhibit" to "#1565C0"),
    "show" to ("Show" to "#7B1FA2"),
    "festival" to ("Festival" to "#E65100"),
    "market" to ("Market" to "#2E7D32"),
    "parade" to ("Parade" to "#C62828"),
    "special_event" to ("Special Event" to "#AD1457"),
    "ghost_tour" to ("Ghost Tour" to "#4A148C"),
    "psychic_fair" to ("Psychic Fair" to "#6A1B9A"),
    "film_fest" to ("Film Festival" to "#0277BD"),
    "arts_festival" to ("Arts Festival" to "#00838F"),
    "heritage" to ("Heritage" to "#4E342E")
)

// ═════════════════════════════════════════════════════════════════════════════
// EVENTS DIALOG — main entry point
// ═════════════════════════════════════════════════════════════════════════════

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showEventsDialog() {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor(EVT_DARK))
    }

    // ── Header ──
    val headerRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(4))
    }
    headerRow.addView(TextView(this).apply {
        text = "Salem Events"
        textSize = 20f
        setTextColor(Color.parseColor(EVT_GOLD))
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    })
    if (eventsViewModel.isOctober) {
        headerRow.addView(TextView(this).apply {
            text = "\uD83C\uDF83 October!"
            textSize = 14f
            setTextColor(Color.parseColor(EVT_ORANGE))
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(8), 0, dp(8), 0)
        })
    }
    headerRow.addView(TextView(this).apply {
        text = "\u2715"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(12), 0, dp(4), 0)
        setOnClickListener { dialog.dismiss() }
    })
    root.addView(headerRow)

    val scroll = ScrollView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
    }
    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(4), dp(12), dp(12))
    }

    // ── "On This Date in 1692" ──
    val onThisDay = eventsViewModel.onThisDay.value
    if (onThisDay.isNotEmpty()) {
        content.addView(buildOnThisDaySection(onThisDay, dp, dialog))
    }

    // ── Today's Events ──
    val todayEvents = eventsViewModel.todayEvents.value
    if (todayEvents.isNotEmpty()) {
        content.addView(sectionHeader(dp, "Happening Today", todayEvents.size))
        for (event in todayEvents) {
            content.addView(buildEventCard(event, dp, dialog))
        }
    }

    // ── Upcoming Events ──
    val upcoming = eventsViewModel.upcomingEvents.value
    if (upcoming.isNotEmpty()) {
        content.addView(sectionHeader(dp, "Upcoming", upcoming.size))
        for (event in upcoming.take(20)) {
            content.addView(buildEventCard(event, dp, dialog))
        }
    }

    // ── October / Seasonal Events ──
    val monthEvents = eventsViewModel.monthEvents.value
    if (monthEvents.isNotEmpty() && monthEvents != todayEvents) {
        val monthName = java.text.DateFormatSymbols().months[
            java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
        ]
        content.addView(sectionHeader(dp, "$monthName Events", monthEvents.size))
        for (event in monthEvents.take(20)) {
            content.addView(buildEventCard(event, dp, dialog))
        }
    }

    // ── Category filter chips ──
    val types = eventsViewModel.getEventTypes()
    if (types.isNotEmpty()) {
        content.addView(TextView(this).apply {
            text = "Browse by Category"
            textSize = 14f
            setTextColor(Color.parseColor(EVT_GOLD))
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(14), 0, dp(6))
        })
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        // Wrap chips in a horizontal scroll
        val chipScroll = android.widget.HorizontalScrollView(this)
        val chipInner = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (type in types) {
            val (displayName, color) = EVENT_TYPE_MAP[type] ?: (type.replace("_", " ")
                .replaceFirstChar { it.uppercase() } to "#666666")
            chipInner.addView(TextView(this).apply {
                text = displayName
                textSize = 12f
                setTextColor(Color.WHITE)
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor(color))
                    cornerRadius = dp(12).toFloat()
                }
                background = bg
                setPadding(dp(10), dp(4), dp(10), dp(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(6) }
                setOnClickListener {
                    dialog.dismiss()
                    showEventsByType(type, displayName)
                }
            })
        }
        chipScroll.addView(chipInner)
        content.addView(chipScroll)
    }

    // ── Empty state ──
    if (todayEvents.isEmpty() && upcoming.isEmpty() && monthEvents.isEmpty() && onThisDay.isEmpty()) {
        content.addView(TextView(this).apply {
            text = "No events currently loaded.\n\nEvents will appear once the content database is populated with Salem's calendar."
            textSize = 14f
            setTextColor(Color.parseColor(EVT_TEXT_DIM))
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(40), dp(16), dp(40))
        })
    }

    scroll.addView(content)
    root.addView(scroll)
    dialog.setContentView(root)
    dialog.show()
}

// ═════════════════════════════════════════════════════════════════════════════
// "ON THIS DATE IN 1692" SECTION
// ═════════════════════════════════════════════════════════════════════════════

@SuppressLint("SetTextI18n")
private fun SalemMainActivity.buildOnThisDaySection(
    events: List<TimelineEvent>,
    dp: (Int) -> Int,
    parentDialog: Dialog
): LinearLayout {
    val section = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#331A0F2E"))
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), Color.parseColor("#8B0000"))
        }
        background = bg
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) }
    }

    section.addView(TextView(this).apply {
        text = "\uD83D\uDCDC On This Date in 1692"
        textSize = 15f
        setTextColor(Color.parseColor("#E57373"))
        setTypeface(null, Typeface.BOLD)
    })

    for (event in events) {
        section.addView(TextView(this).apply {
            text = "${event.name}"
            textSize = 13f
            setTextColor(Color.parseColor(EVT_TEXT))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(2))
        })
        section.addView(TextView(this).apply {
            text = event.description
            textSize = 12f
            setTextColor(Color.parseColor(EVT_TEXT_DIM))
            maxLines = 4
        })
        if (event.poiId != null) {
            section.addView(TextView(this).apply {
                text = "\u2192 View related site on map"
                textSize = 11f
                setTextColor(Color.parseColor(EVT_GOLD))
                setPadding(0, dp(2), 0, 0)
                setOnClickListener {
                    parentDialog.dismiss()
                    // Look up the POI and center map
                    val poi = tourViewModel.allPois.value.firstOrNull { it.id == event.poiId }
                    if (poi != null) {
                        binding.mapView.controller.animateTo(GeoPoint(poi.lat, poi.lng), 17.0, 800L)
                    }
                }
            })
        }
    }

    return section
}

// ═════════════════════════════════════════════════════════════════════════════
// EVENT CARD
// ═════════════════════════════════════════════════════════════════════════════

@SuppressLint("SetTextI18n")
private fun SalemMainActivity.buildEventCard(
    event: EventsCalendar,
    dp: (Int) -> Int,
    parentDialog: Dialog
): LinearLayout {
    val (typeName, typeColor) = EVENT_TYPE_MAP[event.eventType]
        ?: (event.eventType.replace("_", " ").replaceFirstChar { it.uppercase() } to "#666666")

    val card = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor(EVT_SURFACE))
            cornerRadius = dp(6).toFloat()
        }
        background = bg
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) }
    }

    // Title row: name + type badge
    val titleRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    titleRow.addView(TextView(this).apply {
        text = event.name
        textSize = 14f
        setTextColor(Color.parseColor(EVT_TEXT))
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    })
    titleRow.addView(TextView(this).apply {
        text = typeName
        textSize = 10f
        setTextColor(Color.WHITE)
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor(typeColor))
            cornerRadius = dp(8).toFloat()
        }
        background = bg
        setPadding(dp(6), dp(2), dp(6), dp(2))
    })
    card.addView(titleRow)

    // Date
    val dateText = buildList {
        event.startDate?.let { add(it) }
        event.endDate?.let { if (it != event.startDate) add("to $it") }
    }.joinToString(" ")
    if (dateText.isNotBlank()) {
        card.addView(TextView(this).apply {
            text = dateText
            textSize = 11f
            setTextColor(Color.parseColor(EVT_GOLD))
            setPadding(0, dp(2), 0, 0)
        })
    }

    // Description
    event.description?.let { desc ->
        card.addView(TextView(this).apply {
            text = desc
            textSize = 12f
            setTextColor(Color.parseColor(EVT_TEXT_DIM))
            setPadding(0, dp(4), 0, 0)
            maxLines = 3
        })
    }

    // Details row: hours, admission
    val details = buildList {
        event.hours?.let { add("Hours: $it") }
        event.admission?.let { add(it) }
    }.joinToString(" \u2022 ")
    if (details.isNotBlank()) {
        card.addView(TextView(this).apply {
            text = details
            textSize = 11f
            setTextColor(Color.parseColor(EVT_TEXT_DIM))
            setPadding(0, dp(4), 0, 0)
        })
    }

    // Action row: Walk Here (if venue has a POI)
    if (event.venuePoiId != null) {
        card.addView(TextView(this).apply {
            text = "\u2192 Show venue on map"
            textSize = 11f
            setTextColor(Color.parseColor(EVT_GOLD))
            setPadding(0, dp(4), 0, 0)
            setOnClickListener {
                parentDialog.dismiss()
                val poi = tourViewModel.allPois.value.firstOrNull { it.id == event.venuePoiId }
                if (poi != null) {
                    binding.mapView.controller.animateTo(GeoPoint(poi.lat, poi.lng), 17.0, 800L)
                } else {
                    Toast.makeText(this@buildEventCard, "Venue not found in tour POIs", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    return card
}

// ═════════════════════════════════════════════════════════════════════════════
// EVENTS BY TYPE — filtered view
// ═════════════════════════════════════════════════════════════════════════════

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showEventsByType(type: String, displayName: String) {
    val events = eventsViewModel.getEventsByType(type)
    if (events.isEmpty()) {
        Toast.makeText(this, "No $displayName events found", Toast.LENGTH_SHORT).show()
        return
    }

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor(EVT_DARK))
    }

    // Header
    val headerRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(8))
    }
    headerRow.addView(TextView(this).apply {
        text = "$displayName (${events.size})"
        textSize = 18f
        setTextColor(Color.parseColor(EVT_GOLD))
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    })
    headerRow.addView(TextView(this).apply {
        text = "\u2715"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(12), 0, dp(4), 0)
        setOnClickListener { dialog.dismiss() }
    })
    root.addView(headerRow)

    val scroll = ScrollView(this)
    val list = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), 0, dp(12), dp(12))
    }
    for (event in events) {
        list.addView(buildEventCard(event, dp, dialog))
    }
    scroll.addView(list)
    root.addView(scroll)

    dialog.setContentView(root)
    dialog.show()
}

// ═════════════════════════════════════════════════════════════════════════════
// HELPERS
// ═════════════════════════════════════════════════════════════════════════════

private fun SalemMainActivity.sectionHeader(dp: (Int) -> Int, title: String, count: Int): TextView =
    TextView(this).apply {
        text = "$title ($count)"
        textSize = 15f
        setTextColor(Color.parseColor(EVT_GOLD))
        setTypeface(null, Typeface.BOLD)
        setPadding(dp(4), dp(12), 0, dp(6))
    }
