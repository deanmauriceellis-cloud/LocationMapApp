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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.R
import com.example.wickedsalemwitchcitytour.content.model.Tour
import com.example.wickedsalemwitchcitytour.content.model.TourPoi
import com.example.wickedsalemwitchcitytour.tour.ActiveTour
import com.example.wickedsalemwitchcitytour.tour.TourRouteLoader
import com.example.wickedsalemwitchcitytour.tour.GeofenceEventType
import com.example.wickedsalemwitchcitytour.tour.NarrationState
import com.example.wickedsalemwitchcitytour.tour.TourGeofenceEvent
import com.example.wickedsalemwitchcitytour.tour.TourState
import com.example.wickedsalemwitchcitytour.tour.TourSummary
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module SalemMainActivityTour.kt"

// ── Salem brand colors ──────────────────────────────────────────────────────
private const val SALEM_PURPLE = "#2D1B4E"
private const val SALEM_GOLD = "#C9A84C"
private const val SALEM_DARK = "#1E1E2A"
private const val SALEM_SURFACE = "#2A2A3C"
private const val SALEM_TEXT = "#F5F0E8"
private const val SALEM_TEXT_DIM = "#B8AFA0"

// ── Tour marker colors ──────────────────────────────────────────────────────
private const val COLOR_COMPLETED = "#4CAF50"  // green
private const val COLOR_CURRENT   = "#2196F3"  // blue
private const val COLOR_UPCOMING  = "#9E9E9E"  // gray
private const val COLOR_ROUTE     = "#C9A84C"  // gold

// Tour HUD overlay markers and polyline
internal var tourRoutePolyline: Polyline? = null
internal val tourStopMarkers = mutableListOf<Marker>()
internal var narrationBarView: View? = null
internal var tourHudView: View? = null
internal var welcomeShown = false

// ═════════════════════════════════════════════════════════════════════════════
// WELCOME DIALOG — shown once after cinematic zoom
// ═════════════════════════════════════════════════════════════════════════════

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showWelcomeDialog() {
    if (welcomeShown) return
    welcomeShown = true

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
    dialog.setCancelable(false)

    // ── Brand title ──
    val title = TextView(this).apply {
        text = "Wicked Salem"
        textSize = 28f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(Typeface.SERIF, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, dp(48), 0, dp(4))
    }
    val subtitle = TextView(this).apply {
        text = "Witch City Tour"
        textSize = 16f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        setTypeface(Typeface.SERIF, Typeface.NORMAL)
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, dp(32))
    }

    // ── Choice cards ──
    fun choiceCard(
        icon: String, label: String, desc: String, onClick: () -> Unit
    ): LinearLayout {
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor(SALEM_SURFACE))
            cornerRadius = dp(12).toFloat()
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = bg
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(24); marginEnd = dp(24); bottomMargin = dp(16)
            }
            // Icon
            addView(TextView(this@showWelcomeDialog).apply {
                text = icon; textSize = 32f
                setPadding(0, 0, dp(16), 0)
            })
            // Text column
            val col = LinearLayout(this@showWelcomeDialog).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(TextView(this@showWelcomeDialog).apply {
                text = label; textSize = 18f
                setTextColor(Color.parseColor(SALEM_GOLD))
                setTypeface(null, Typeface.BOLD)
            })
            col.addView(TextView(this@showWelcomeDialog).apply {
                text = desc; textSize = 13f
                setTextColor(Color.parseColor(SALEM_TEXT))
                setPadding(0, dp(4), 0, 0)
            })
            addView(col)
            setOnClickListener { onClick() }
        }
    }

    val exploreCard = choiceCard(
        "\uD83D\uDDFA", "Explore Salem",
        "See what\u2019s around you. Browse the map freely."
    ) { dialog.dismiss() }

    val tourCard = choiceCard(
        "\uD83D\uDEB6", "Take a Tour",
        "Guided walking tours with GPS narration."
    ) { dialog.dismiss(); showTourSelectionDialog() }

    // ── Hint ──
    val hint = TextView(this).apply {
        text = "Long-press the map to jump to any location"
        textSize = 11f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        gravity = Gravity.CENTER
        setPadding(0, dp(24), 0, dp(16))
    }

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#1A0F2E"), Color.parseColor(SALEM_PURPLE))
        )
        background = bg
        addView(title)
        addView(subtitle)
        addView(exploreCard)
        addView(tourCard)
        addView(hint)
    }

    dialog.setContentView(root)
    dialog.show()
}

// ═════════════════════════════════════════════════════════════════════════════
// TOUR SELECTION DIALOG
// ═════════════════════════════════════════════════════════════════════════════

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showTourSelectionDialog() {
    // Check if a tour is already active — show resume dialog instead
    val currentState = tourViewModel.tourState.value
    if (currentState is TourState.Active || currentState is TourState.Paused) {
        showActiveTourDialog()
        return
    }

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    // ── Header ──
    val titleText = TextView(this).apply {
        text = "Walking Tours"
        textSize = 20f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val closeBtn = TextView(this).apply {
        text = "\u2715"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(12), 0, dp(4), 0)
        setOnClickListener { dialog.dismiss() }
    }
    val headerRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(8))
        addView(titleText)
        addView(closeBtn)
    }

    // ── Content container (populated once tours load) ──
    val listLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), 0, dp(12), dp(12))
    }

    // ── Loading indicator ──
    val loadingLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(48), dp(16), dp(48))
        addView(ProgressBar(this@showTourSelectionDialog).apply {
            isIndeterminate = true
        })
        addView(TextView(this@showTourSelectionDialog).apply {
            text = "Loading tours\u2026"
            textSize = 14f
            setTextColor(Color.parseColor(SALEM_TEXT_DIM))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        })
    }
    listLayout.addView(loadingLayout)

    val scroll = ScrollView(this).apply { addView(listLayout) }
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor(SALEM_DARK))
        addView(headerRow)
        addView(scroll)
    }
    dialog.setContentView(root)
    dialog.show()

    // ── Observe tours and populate when ready ──
    lifecycleScope.launch {
        tourViewModel.availableTours.collectLatest { tours ->
            if (tours.isEmpty()) return@collectLatest
            val allPois = tourViewModel.allPois.value
            runOnUiThread {
                listLayout.removeAllViews()
                populateTourList(listLayout, tours, allPois, dp, dialog)
            }
        }
    }
}

/** Populate tour list content into the given layout. */
@SuppressLint("SetTextI18n")
private fun SalemMainActivity.populateTourList(
    listLayout: LinearLayout,
    tours: List<Tour>,
    allPois: List<TourPoi>,
    dp: (Int) -> Int,
    dialog: Dialog
) {
    // ── Group tours by theme ──
    val witchTrialsTours = tours.filter { it.theme == "essential_highlights" }
    val otherTours = tours.filter { it.theme != "essential_highlights" }

    if (witchTrialsTours.isNotEmpty()) {
        listLayout.addView(sectionHeader("Salem Witch Trials", dp))
        for (tour in witchTrialsTours) {
            listLayout.addView(buildTourCard(tour, dp, dialog))
        }
    }
    if (otherTours.isNotEmpty()) {
        listLayout.addView(sectionHeader("Extended Tours", dp))
        for (tour in otherTours) {
            listLayout.addView(buildTourCard(tour, dp, dialog))
        }
    }

    // ── POI category toggles ──
    data class PoiCategoryInfo(val id: String, val label: String, val count: Int)
    val categoryMap = allPois.groupBy { it.category }
    val categoryOrder = listOf(
        "witch_trials" to "Witch Trial Sites",
        "maritime" to "Maritime & Historical",
        "museums" to "Museums & Cultural",
        "literary_sites" to "Literary Sites",
        "parks_landmarks" to "Parks & Landmarks",
        "visitor_services" to "Visitor Services"
    )
    val categories = categoryOrder.mapNotNull { (id, label) ->
        val count = categoryMap[id]?.size ?: return@mapNotNull null
        PoiCategoryInfo(id, label, count)
    }

    if (categories.isNotEmpty()) {
        listLayout.addView(sectionHeader("Points of Interest", dp))
        listLayout.addView(TextView(this).apply {
            text = "These categories are shown on the map during your tour"
            textSize = 12f
            setTextColor(Color.parseColor(SALEM_TEXT_DIM))
            setPadding(dp(4), 0, 0, dp(8))
        })
        for (cat in categories) {
            listLayout.addView(categoryToggle(cat.id, cat.label, cat.count, dp))
        }
    }

    // ── Special options: Time Budget + Build Your Own ──
    listLayout.addView(sectionHeader("Customize", dp))

    fun specialBtn(label: String, subtitle: String, onClick: () -> Unit): LinearLayout {
        val cardBg = GradientDrawable().apply {
            setColor(Color.parseColor("#33C9A84C")) // gold tint
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), Color.parseColor(SALEM_GOLD))
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            addView(TextView(this@populateTourList).apply {
                text = label
                textSize = 15f
                setTextColor(Color.parseColor(SALEM_GOLD))
                setTypeface(null, Typeface.BOLD)
            })
            addView(TextView(this@populateTourList).apply {
                text = subtitle
                textSize = 12f
                setTextColor(Color.parseColor(SALEM_TEXT_DIM))
                setPadding(0, dp(2), 0, 0)
            })
            setOnClickListener { onClick() }
        }
    }

    listLayout.addView(specialBtn(
        "\u23F1 I Have X Minutes",
        "Pick a time budget \u2014 we\u2019ll build the perfect tour"
    ) { dialog.dismiss(); showTimeBudgetDialog() })

    listLayout.addView(specialBtn(
        "\u270E Build Your Own Tour",
        "Browse all POIs, pick stops, auto-optimize route"
    ) { dialog.dismiss(); showCustomTourBuilder() })
}

// ── Section header helper ──
private fun SalemMainActivity.sectionHeader(text: String, dp: (Int) -> Int): TextView {
    return TextView(this).apply {
        this.text = text.uppercase()
        textSize = 12f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(null, Typeface.BOLD)
        letterSpacing = 0.1f
        setPadding(dp(4), dp(16), 0, dp(8))
    }
}

// ── POI category toggle row ──
@SuppressLint("SetTextI18n")
private fun SalemMainActivity.categoryToggle(
    categoryId: String, label: String, count: Int, dp: (Int) -> Int
): LinearLayout {
    val checked = booleanArrayOf(true) // default: all categories on
    val rowBg = GradientDrawable().apply {
        setColor(Color.parseColor(SALEM_SURFACE))
        cornerRadius = dp(6).toFloat()
    }
    val checkMark = TextView(this).apply {
        text = "\u2611"
        textSize = 18f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setPadding(0, 0, dp(12), 0)
    }
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rowBg
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(4) }
        addView(checkMark)
        addView(TextView(this@categoryToggle).apply {
            text = "$label ($count)"
            textSize = 14f
            setTextColor(Color.parseColor(SALEM_TEXT))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        setOnClickListener {
            checked[0] = !checked[0]
            checkMark.text = if (checked[0]) "\u2611" else "\u2610"
            checkMark.setTextColor(
                Color.parseColor(if (checked[0]) SALEM_GOLD else SALEM_TEXT_DIM)
            )
        }
    }
}

@SuppressLint("SetTextI18n")
private fun SalemMainActivity.buildTourCard(
    tour: Tour,
    dp: (Int) -> Int,
    parentDialog: Dialog
): LinearLayout {
    val cardBg = GradientDrawable().apply {
        setColor(Color.parseColor(SALEM_SURFACE))
        cornerRadius = dp(8).toFloat()
    }

    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = cardBg
        setPadding(dp(16), dp(12), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }

        // Tour name
        addView(TextView(this@buildTourCard).apply {
            text = tour.name
            textSize = 16f
            setTextColor(Color.parseColor(SALEM_GOLD))
            setTypeface(null, Typeface.BOLD)
        })

        // Description
        addView(TextView(this@buildTourCard).apply {
            text = tour.description
            textSize = 13f
            setTextColor(Color.parseColor(SALEM_TEXT))
            setPadding(0, dp(4), 0, dp(8))
        })

        // Stats row: stops, time, distance, difficulty
        val statsRow = LinearLayout(this@buildTourCard).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun statChip(label: String): TextView = TextView(this@buildTourCard).apply {
            text = label
            textSize = 11f
            setTextColor(Color.parseColor(SALEM_TEXT_DIM))
            val chipBg = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = dp(4).toFloat()
            }
            background = chipBg
            setPadding(dp(6), dp(2), dp(6), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(6) }
        }
        statsRow.addView(statChip("${tour.stopCount} stops"))
        statsRow.addView(statChip("${tour.estimatedMinutes} min"))
        statsRow.addView(statChip("%.1f km".format(tour.distanceKm)))
        statsRow.addView(statChip(tour.difficulty.replaceFirstChar { it.uppercase() }))
        addView(statsRow)

        // Start button
        val startBtn = TextView(this@buildTourCard).apply {
            text = "Start Tour"
            textSize = 14f
            setTextColor(Color.parseColor(SALEM_DARK))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            val btnBg = GradientDrawable().apply {
                setColor(Color.parseColor(SALEM_GOLD))
                cornerRadius = dp(6).toFloat()
            }
            background = btnBg
            setPadding(dp(16), dp(8), dp(16), dp(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
            setOnClickListener {
                parentDialog.dismiss()
                tourViewModel.startTour(tour.id)
                DebugLogger.i("Tour", "Starting tour: ${tour.name}")
            }
        }
        addView(startBtn)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// ACTIVE TOUR DIALOG (when user taps Tours while a tour is running)
// ═════════════════════════════════════════════════════════════════════════════

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showActiveTourDialog() {
    val activeTour = when (val state = tourViewModel.tourState.value) {
        is TourState.Active -> state.activeTour
        is TourState.Paused -> state.activeTour
        else -> return
    }
    val isPaused = tourViewModel.tourState.value is TourState.Paused

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor(SALEM_DARK))
        setPadding(dp(16), dp(12), dp(16), dp(16))
    }

    // Header
    val headerRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    headerRow.addView(TextView(this).apply {
        text = activeTour.tour.name
        textSize = 18f
        setTextColor(Color.parseColor(SALEM_GOLD))
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

    // Progress
    root.addView(TextView(this).apply {
        val p = activeTour.progress
        text = "Stop ${p.currentStopIndex + 1} of ${activeTour.stops.size} — " +
                "${p.completedStops.size} completed, ${p.skippedStops.size} skipped"
        textSize = 13f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        setPadding(0, dp(8), 0, dp(4))
    })

    // Progress bar
    root.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = activeTour.stops.size
        progress = activeTour.progress.completedStops.size
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(6)
        ).apply { topMargin = dp(4); bottomMargin = dp(12) }
    })

    // Current stop info
    val currentPoi = activeTour.currentPoi
    if (currentPoi != null) {
        root.addView(TextView(this).apply {
            text = "Current: ${currentPoi.name}"
            textSize = 15f
            setTextColor(Color.parseColor(SALEM_TEXT))
            setTypeface(null, Typeface.BOLD)
        })
        if (currentPoi.address.isNotBlank()) {
            root.addView(TextView(this).apply {
                text = currentPoi.address
                textSize = 12f
                setTextColor(Color.parseColor(SALEM_TEXT_DIM))
                setPadding(0, dp(2), 0, dp(4))
            })
        }
        val dist = tourViewModel.distanceToStop(activeTour.progress.currentStopIndex)
        if (dist != null) {
            root.addView(TextView(this).apply {
                text = if (dist < 1000) "%.0f m away".format(dist)
                       else "%.1f km away".format(dist / 1000)
                textSize = 12f
                setTextColor(Color.parseColor(SALEM_GOLD))
                setPadding(0, 0, 0, dp(8))
            })
        }
    }

    // Stop list in a scroll view
    val stopScroll = ScrollView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
    }
    val stopList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    for ((i, stop) in activeTour.stops.withIndex()) {
        val poi = activeTour.pois.firstOrNull { it.id == stop.poiId } ?: continue
        val isCompleted = i in activeTour.progress.completedStops
        val isSkipped = i in activeTour.progress.skippedStops
        val isCurrent = i == activeTour.progress.currentStopIndex

        val color = when {
            isCompleted -> COLOR_COMPLETED
            isCurrent -> COLOR_CURRENT
            isSkipped -> SALEM_TEXT_DIM
            else -> SALEM_TEXT
        }
        val prefix = when {
            isCompleted -> "\u2713 "
            isSkipped -> "\u2014 "
            isCurrent -> "\u25B6 "
            else -> "${i + 1}. "
        }
        stopList.addView(TextView(this).apply {
            text = "$prefix${poi.name}"
            textSize = 13f
            setTextColor(Color.parseColor(color))
            if (isCurrent) setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(3), dp(4), dp(3))
        })
    }
    stopScroll.addView(stopList)
    root.addView(stopScroll)

    // Action buttons row
    val btnRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, 0)
    }

    fun actionBtn(label: String, bgColor: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor(bgColor))
                cornerRadius = dp(6).toFloat()
            }
            background = bg
            setPadding(dp(12), dp(8), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener { onClick() }
        }

    if (isPaused) {
        btnRow.addView(actionBtn("Resume", "#4CAF50") { dialog.dismiss(); tourViewModel.resumeTour() })
    } else {
        btnRow.addView(actionBtn("Next", COLOR_CURRENT) { dialog.dismiss(); tourViewModel.advanceToNextStop() })
        btnRow.addView(actionBtn("Skip", "#FF9800") { dialog.dismiss(); tourViewModel.skipStop() })
        btnRow.addView(actionBtn("Pause", "#9E9E9E") { dialog.dismiss(); tourViewModel.pauseTour() })
    }
    btnRow.addView(actionBtn("End", "#F44336") { dialog.dismiss(); tourViewModel.endTour() })

    root.addView(btnRow)

    dialog.setContentView(root)
    dialog.show()
}

// ═════════════════════════════════════════════════════════════════════════════
// TOUR STATE OBSERVER — call from observeViewModel()
// ═════════════════════════════════════════════════════════════════════════════

internal fun SalemMainActivity.observeTourState() {
    lifecycleScope.launch {
        tourViewModel.tourState.collectLatest { state ->
            when (state) {
                is TourState.Idle -> {
                    clearTourOverlays()
                    removeTourHud()
                }
                is TourState.Loading -> {
                    statusLineManager.set(StatusLineManager.Priority.TOUR, "Loading tour…")
                }
                is TourState.Active -> {
                    drawTourRoute(state.activeTour)
                    updateTourHud(state.activeTour)
                    val poi = state.activeTour.currentPoi
                    val stopNum = state.activeTour.progress.currentStopIndex + 1
                    val total = state.activeTour.stops.size
                    statusLineManager.set(
                        StatusLineManager.Priority.TOUR,
                        "Tour: $stopNum/$total — ${poi?.name ?: "Unknown"}"
                    )
                }
                is TourState.Paused -> {
                    val poi = state.activeTour.currentPoi
                    statusLineManager.set(
                        StatusLineManager.Priority.TOUR,
                        "Tour paused — ${poi?.name ?: "Unknown"}"
                    )
                }
                is TourState.Completed -> {
                    clearTourOverlays()
                    removeTourHud()
                    statusLineManager.clear(StatusLineManager.Priority.TOUR)
                    showTourCompletionDialog(state.summaryStats)
                }
                is TourState.Error -> {
                    clearTourOverlays()
                    removeTourHud()
                    statusLineManager.clear(StatusLineManager.Priority.TOUR)
                    Toast.makeText(this@observeTourState, state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// GEOFENCE EVENT OBSERVER — approach/entry/exit notifications
// ═════════════════════════════════════════════════════════════════════════════

internal fun SalemMainActivity.observeGeofenceEvents() {
    lifecycleScope.launch {
        tourViewModel.geofenceEvents.collectLatest { event ->
            when (event.type) {
                GeofenceEventType.APPROACH -> {
                    Toast.makeText(
                        this@observeGeofenceEvents,
                        "Approaching: ${event.poiName}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                GeofenceEventType.ENTRY -> {
                    Toast.makeText(
                        this@observeGeofenceEvents,
                        "Arrived: ${event.poiName}",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Center map on the POI
                    binding.mapView.controller.animateTo(
                        GeoPoint(event.poi.lat, event.poi.lng), 18.0, 800L
                    )
                }
                GeofenceEventType.EXIT -> {
                    // Silent — transition narration handled by TourEngine
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// NARRATION STATE OBSERVER + CONTROLS BAR
// ═════════════════════════════════════════════════════════════════════════════

internal fun SalemMainActivity.observeNarrationState() {
    lifecycleScope.launch {
        tourViewModel.narrationState.collectLatest { state ->
            when (state) {
                is NarrationState.Speaking -> showNarrationBar(state)
                is NarrationState.Paused -> showNarrationBar(state)
                is NarrationState.Idle -> removeNarrationBar()
            }
        }
    }
}

@SuppressLint("SetTextI18n")
private fun SalemMainActivity.showNarrationBar(state: NarrationState) {
    removeNarrationBar()

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val segment = when (state) {
        is NarrationState.Speaking -> state.segment
        is NarrationState.Paused -> state.segment
        else -> null
    }
    val isPaused = state is NarrationState.Paused

    val bar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.parseColor("#E81A0F2E"))
        setPadding(dp(12), dp(6), dp(12), dp(6))
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // Speaker icon + segment info
    val infoLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    infoLayout.addView(TextView(this).apply {
        val label = segment?.poiName ?: "Narration"
        text = if (isPaused) "\u23F8 $label" else "\u1F50A $label"
        textSize = 12f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(null, Typeface.BOLD)
        maxLines = 1
    })
    infoLayout.addView(TextView(this).apply {
        text = segment?.type?.name?.replace("_", " ")?.lowercase()
            ?.replaceFirstChar { it.uppercase() } ?: ""
        textSize = 10f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
    })
    bar.addView(infoLayout)

    // Control buttons
    fun ctrlBtn(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 16f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(4), dp(8), dp(4))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(2) }
        setOnClickListener { onClick() }
    }

    if (isPaused) {
        bar.addView(ctrlBtn("\u25B6") { tourViewModel.resumeNarration() })  // Play
    } else {
        bar.addView(ctrlBtn("\u23F8") { tourViewModel.pauseNarration() })   // Pause
    }
    bar.addView(ctrlBtn("\u23ED") { tourViewModel.skipNarration() })         // Skip
    bar.addView(ctrlBtn("\u23F9") { tourViewModel.stopNarration() })         // Stop
    bar.addView(ctrlBtn("\u23E9") {                                          // Speed
        val newSpeed = tourViewModel.cycleNarrationSpeed()
        Toast.makeText(this, "Speed: ${newSpeed}x", Toast.LENGTH_SHORT).show()
    })

    // Insert narration bar above the tour HUD (below map, above HUD)
    val parent = binding.mapView.parent as? ViewGroup ?: return
    val hudIndex = tourHudView?.let { parent.indexOfChild(it) } ?: parent.childCount
    parent.addView(bar, hudIndex)
    narrationBarView = bar
}

internal fun SalemMainActivity.removeNarrationBar() {
    narrationBarView?.let {
        (it.parent as? ViewGroup)?.removeView(it)
    }
    narrationBarView = null
}

// ═════════════════════════════════════════════════════════════════════════════
// TOUR ROUTE OVERLAY — polyline + numbered stop markers
// ═════════════════════════════════════════════════════════════════════════════

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.drawTourRoute(activeTour: ActiveTour) {
    clearTourOverlays()

    // Tour 1 (tour_essentials) = downtown street walk — always use downtown route
    val downtownRoute = TourRouteLoader.loadDowntownRoute(this)
    if (downtownRoute.isNotEmpty()) {
        val polyline = Polyline().apply {
            setPoints(downtownRoute)
            outlinePaint.color = Color.parseColor(COLOR_ROUTE)
            outlinePaint.strokeWidth = 5f
            outlinePaint.isAntiAlias = true
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
        }
        binding.mapView.overlays.add(polyline)
        tourRoutePolyline = polyline
        DebugLogger.i("SalemMainActivityTour", "Drew downtown route: ${downtownRoute.size} points")
        binding.mapView.invalidate()
        return
    }

    // Fallback for other tours: load OSRM route segments from tour JSON
    val segments = TourRouteLoader.loadRouteSegments(this, activeTour.tour.id)
    if (segments.isNotEmpty()) {
        val allRoutePoints = mutableListOf<GeoPoint>()
        for (seg in segments) {
            if (allRoutePoints.isEmpty()) {
                allRoutePoints.addAll(seg.points)
            } else if (seg.points.isNotEmpty()) {
                allRoutePoints.addAll(seg.points.drop(1))
            }
        }
        val polyline = Polyline().apply {
            setPoints(allRoutePoints)
            outlinePaint.color = Color.parseColor(COLOR_ROUTE)
            outlinePaint.strokeWidth = 5f
            outlinePaint.isAntiAlias = true
        }
        binding.mapView.overlays.add(polyline)
        tourRoutePolyline = polyline
    }

    // Stop markers — only for tours with OSRM route segments
    for ((i, stop) in activeTour.stops.withIndex()) {
        val poi = activeTour.pois.firstOrNull { it.id == stop.poiId } ?: continue
        val isCompleted = i in activeTour.progress.completedStops
        val isSkipped = i in activeTour.progress.skippedStops
        val isCurrent = i == activeTour.progress.currentStopIndex

        val color = when {
            isCompleted -> Color.parseColor(COLOR_COMPLETED)
            isCurrent -> Color.parseColor(COLOR_CURRENT)
            else -> Color.parseColor(COLOR_UPCOMING)
        }

        val marker = Marker(binding.mapView).apply {
            position = GeoPoint(poi.lat, poi.lng)
            title = "${i + 1}. ${poi.name}"
            snippet = when {
                isCompleted -> "Completed"
                isSkipped -> "Skipped"
                isCurrent -> "Current stop"
                else -> "Upcoming"
            }
            icon = MarkerIconHelper.createNumberedCircle(this@drawTourRoute, i + 1, color)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            setOnMarkerClickListener { m, _ ->
                showTourStopDetail(poi, i, activeTour)
                true
            }
        }
        binding.mapView.overlays.add(marker)
        tourStopMarkers.add(marker)
    }

    binding.mapView.invalidate()
}

internal fun SalemMainActivity.clearTourOverlays() {
    tourRoutePolyline?.let { binding.mapView.overlays.remove(it) }
    tourRoutePolyline = null
    for (m in tourStopMarkers) binding.mapView.overlays.remove(m)
    tourStopMarkers.clear()
    binding.mapView.invalidate()
}

// ═════════════════════════════════════════════════════════════════════════════
// TOUR HUD — bottom bar showing current stop + controls
// ═════════════════════════════════════════════════════════════════════════════

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.updateTourHud(activeTour: ActiveTour) {
    removeTourHud()

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }
    val poi = activeTour.currentPoi ?: return

    val hud = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.parseColor("#E8${SALEM_PURPLE.removePrefix("#")}"))
        setPadding(dp(12), dp(8), dp(12), dp(8))
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // Stop info (left side)
    val infoLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    infoLayout.addView(TextView(this).apply {
        val n = activeTour.progress.currentStopIndex + 1
        text = "Stop $n/${activeTour.stops.size}: ${poi.name}"
        textSize = 13f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(null, Typeface.BOLD)
        maxLines = 1
    })
    val dist = tourViewModel.distanceToStop(activeTour.progress.currentStopIndex)
    if (dist != null) {
        infoLayout.addView(TextView(this).apply {
            text = if (dist < 1000) "%.0f m".format(dist) else "%.1f km".format(dist / 1000)
            textSize = 11f
            setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        })
    }
    hud.addView(infoLayout)

    // Action buttons (right side)
    fun hudBtn(label: String, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label
        textSize = 11f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#44FFFFFF"))
            cornerRadius = dp(4).toFloat()
        }
        background = bg
        setPadding(dp(8), dp(4), dp(8), dp(4))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(4) }
        setOnClickListener { onClick() }
    }

    hud.addView(hudBtn("Next") { tourViewModel.advanceToNextStop() })
    hud.addView(hudBtn("Skip") { tourViewModel.skipStop() })
    hud.addView(hudBtn("Info") { showActiveTourDialog() })

    // Add HUD at the bottom of the map container
    val parent = binding.mapView.parent as? ViewGroup ?: return
    parent.addView(hud)
    tourHudView = hud
}

internal fun SalemMainActivity.removeTourHud() {
    tourHudView?.let {
        (it.parent as? ViewGroup)?.removeView(it)
    }
    tourHudView = null
}

// ═════════════════════════════════════════════════════════════════════════════
// TOUR STOP DETAIL — tap a numbered marker
// ═════════════════════════════════════════════════════════════════════════════

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showTourStopDetail(poi: TourPoi, stopIndex: Int, activeTour: ActiveTour) {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor(SALEM_DARK))
        setPadding(dp(16), dp(12), dp(16), dp(16))
    }

    // Header
    val headerRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    headerRow.addView(TextView(this).apply {
        text = "${stopIndex + 1}. ${poi.name}"
        textSize = 18f
        setTextColor(Color.parseColor(SALEM_GOLD))
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

    // Address
    if (poi.address.isNotBlank()) {
        root.addView(TextView(this).apply {
            text = poi.address
            textSize = 13f
            setTextColor(Color.parseColor(SALEM_TEXT_DIM))
            setPadding(0, dp(4), 0, dp(4))
        })
    }

    // Category + period
    val metaLine = buildList {
        add(poi.category)
        poi.historicalPeriod?.let { add(it) }
    }.joinToString(" \u2022 ")
    root.addView(TextView(this).apply {
        text = metaLine
        textSize = 12f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setPadding(0, 0, 0, dp(8))
    })

    // Description / narration
    val scroll = ScrollView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
    }
    val descLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    poi.description?.let {
        descLayout.addView(TextView(this).apply {
            text = it
            textSize = 14f
            setTextColor(Color.parseColor(SALEM_TEXT))
            setPadding(0, 0, 0, dp(8))
        })
    }

    // Admission / hours / contact
    poi.admissionInfo?.let {
        descLayout.addView(infoRow(dp, "Admission", it))
    }
    poi.hours?.let {
        descLayout.addView(infoRow(dp, "Hours", it))
    }
    poi.phone?.let {
        descLayout.addView(infoRow(dp, "Phone", it))
    }
    poi.website?.let {
        descLayout.addView(infoRow(dp, "Website", it))
    }
    if (poi.requiresTransportation) {
        descLayout.addView(TextView(this).apply {
            text = "\u26A0 This site requires transportation (not walkable from downtown Salem)"
            textSize = 12f
            setTextColor(Color.parseColor("#FF9800"))
            setPadding(0, dp(6), 0, 0)
        })
    }

    scroll.addView(descLayout)
    root.addView(scroll)

    // Action buttons row
    val actionBtnRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(8), 0, 0)
    }

    fun detailBtn(label: String, bgColor: String, weight: Float = 1f, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(if (bgColor == SALEM_GOLD) Color.parseColor(SALEM_DARK) else Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor(bgColor))
                cornerRadius = dp(6).toFloat()
            }
            background = bg
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
                marginEnd = dp(6)
            }
            setOnClickListener { onClick() }
        }

    // Show on Map
    actionBtnRow.addView(detailBtn("Show on Map", SALEM_GOLD) {
        dialog.dismiss()
        binding.mapView.controller.animateTo(GeoPoint(poi.lat, poi.lng), 17.0, 800L)
    })

    // Walk Here — get walking directions
    actionBtnRow.addView(detailBtn("Walk Here", SALEM_SURFACE) {
        dialog.dismiss()
        walkTo(GeoPoint(poi.lat, poi.lng))
    })

    // Narrate — speak the long narration on demand
    if (poi.longNarration != null) {
        actionBtnRow.addView(detailBtn("\uD83D\uDD0A Narrate", SALEM_SURFACE) {
            tourViewModel.narratePoi(poi)
        })
    }

    root.addView(actionBtnRow)

    dialog.setContentView(root)
    dialog.show()
}

private fun SalemMainActivity.infoRow(dp: (Int) -> Int, label: String, value: String): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(2), 0, dp(2))
        addView(TextView(this@infoRow).apply {
            text = "$label: "
            textSize = 12f
            setTextColor(Color.parseColor(SALEM_TEXT_DIM))
            setTypeface(null, Typeface.BOLD)
        })
        addView(TextView(this@infoRow).apply {
            text = value
            textSize = 12f
            setTextColor(Color.parseColor(SALEM_TEXT))
        })
    }

// ═════════════════════════════════════════════════════════════════════════════
// TOUR COMPLETION DIALOG
// ═════════════════════════════════════════════════════════════════════════════

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showTourCompletionDialog(summary: TourSummary) {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor(SALEM_DARK))
        setPadding(dp(24), dp(24), dp(24), dp(24))
        gravity = Gravity.CENTER_HORIZONTAL
    }

    // Title
    root.addView(TextView(this).apply {
        text = "Tour Complete!"
        textSize = 24f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
    })

    root.addView(TextView(this).apply {
        text = summary.tourName
        textSize = 16f
        setTextColor(Color.parseColor(SALEM_TEXT))
        gravity = Gravity.CENTER
        setPadding(0, dp(8), 0, dp(16))
    })

    // Stats
    fun statLine(label: String, value: String) {
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
            addView(TextView(this@showTourCompletionDialog).apply {
                text = "$label: "
                textSize = 14f
                setTextColor(Color.parseColor(SALEM_TEXT_DIM))
            })
            addView(TextView(this@showTourCompletionDialog).apply {
                text = value
                textSize = 14f
                setTextColor(Color.parseColor(SALEM_TEXT))
                setTypeface(null, Typeface.BOLD)
            })
        })
    }

    statLine("Stops completed", "${summary.completedStops} / ${summary.totalStops}")
    statLine("Stops skipped", "${summary.skippedStops}")
    statLine("Time", "${summary.totalTimeMinutes} minutes")
    statLine("Distance", if (summary.totalDistanceM < 1000) "%.0f m".format(summary.totalDistanceM)
                          else "%.1f km".format(summary.totalDistanceM / 1000))
    statLine("Completion", "${summary.completionPercent}%")

    // Dismiss button
    root.addView(TextView(this).apply {
        text = "Done"
        textSize = 16f
        setTextColor(Color.parseColor(SALEM_DARK))
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor(SALEM_GOLD))
            cornerRadius = dp(6).toFloat()
        }
        background = bg
        setPadding(dp(24), dp(12), dp(24), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(20) }
        setOnClickListener {
            dialog.dismiss()
            tourViewModel.dismissCompletion()
        }
    })

    dialog.setContentView(root)
    dialog.show()
}

// ═════════════════════════════════════════════════════════════════════════════
// FEED LOCATION TO TOUR ENGINE
// ═════════════════════════════════════════════════════════════════════════════

/** Call from the GPS update observer to keep the tour engine informed of current location. */
internal fun SalemMainActivity.updateTourLocation(point: GeoPoint) {
    tourViewModel.onLocationUpdate(point)

    // Phase 9T: Feed location to narration geofence manager + update proximity dock
    narrationGeofenceManager?.let { mgr ->
        val nearby = mgr.checkPosition(point.latitude, point.longitude)
        proximityDock?.update(
            point.latitude, point.longitude,
            nearby.map { it.point }
        )

        // Also check street corridors
        corridorManager?.checkPosition(point.latitude, point.longitude)?.forEach { trigger ->
            DebugLogger.i("SalemMainActivity", "Corridor triggered: ${trigger.corridor.name} (${trigger.distanceM.toInt()}m)")
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// CUSTOM TOUR BUILDER — "Build Your Own" flow
// ═════════════════════════════════════════════════════════════════════════════

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showCustomTourBuilder() {
    val allPois = tourViewModel.allPois.value
    if (allPois.isEmpty()) {
        Toast.makeText(this, "No POIs available", Toast.LENGTH_SHORT).show()
        return
    }

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val selectedIds = mutableSetOf<String>()

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor(SALEM_DARK))
    }

    // ── Header ──
    val headerRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(4))
    }
    headerRow.addView(TextView(this).apply {
        text = "Build Your Own Tour"
        textSize = 18f
        setTextColor(Color.parseColor(SALEM_GOLD))
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

    // ── Summary bar (updates dynamically) ──
    val summaryText = TextView(this).apply {
        text = "0 stops selected"
        textSize = 13f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        setPadding(dp(16), dp(4), dp(16), dp(8))
    }
    root.addView(summaryText)

    // ── POI list grouped by category ──
    val scroll = ScrollView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )
    }
    val listLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), 0, dp(12), dp(8))
    }

    val categories = allPois.groupBy { it.category }.toSortedMap()
    val checkboxViews = mutableMapOf<String, TextView>() // poiId → checkbox view

    fun updateSummary() {
        val count = selectedIds.size
        val selected = allPois.filter { it.id in selectedIds }
        if (count == 0) {
            summaryText.text = "0 stops selected"
        } else {
            val distKm = tourViewModel.totalRouteDistanceKm(tourViewModel.optimizeRoute(selected))
            val estMin = tourViewModel.estimateTourMinutes(selected)
            summaryText.text = "$count stops \u2022 %.1f km \u2022 ~${estMin} min".format(distKm)
        }
    }

    for ((category, pois) in categories) {
        // Category header
        listLayout.addView(TextView(this).apply {
            text = category.replaceFirstChar { it.uppercase() }
            textSize = 14f
            setTextColor(Color.parseColor(SALEM_GOLD))
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(10), 0, dp(4))
        })

        for (poi in pois.sortedBy { it.priority }) {
            val itemRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(8), dp(6))
                val ripple = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
                setBackgroundResource(ripple.resourceId)
            }

            val checkbox = TextView(this).apply {
                text = "\u2610" // unchecked box
                textSize = 18f
                setTextColor(Color.parseColor(SALEM_TEXT))
                setPadding(0, 0, dp(8), 0)
            }
            checkboxViews[poi.id] = checkbox

            val nameLabel = TextView(this).apply {
                text = poi.name
                textSize = 13f
                setTextColor(Color.parseColor(SALEM_TEXT))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val priorityLabel = TextView(this).apply {
                text = when (poi.priority) { 1 -> "\u2605"; 2 -> "\u2605"; else -> "" }
                textSize = 12f
                setTextColor(Color.parseColor(SALEM_GOLD))
            }

            itemRow.addView(checkbox)
            itemRow.addView(nameLabel)
            itemRow.addView(priorityLabel)

            itemRow.setOnClickListener {
                if (poi.id in selectedIds) {
                    selectedIds.remove(poi.id)
                    checkbox.text = "\u2610"
                    checkbox.setTextColor(Color.parseColor(SALEM_TEXT))
                } else {
                    selectedIds.add(poi.id)
                    checkbox.text = "\u2611"
                    checkbox.setTextColor(Color.parseColor(COLOR_COMPLETED))
                }
                updateSummary()
            }

            listLayout.addView(itemRow)
        }
    }

    scroll.addView(listLayout)
    root.addView(scroll)

    // ── Action buttons ──
    val btnRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(8), dp(12), dp(12))
    }

    fun actionBtnFlat(label: String, bgColor: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = 14f
            setTextColor(if (bgColor == SALEM_GOLD) Color.parseColor(SALEM_DARK) else Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor(bgColor))
                cornerRadius = dp(6).toFloat()
            }
            background = bg
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener { onClick() }
        }

    btnRow.addView(actionBtnFlat("Select All", SALEM_SURFACE) {
        allPois.forEach { selectedIds.add(it.id) }
        checkboxViews.forEach { (_, v) -> v.text = "\u2611"; v.setTextColor(Color.parseColor(COLOR_COMPLETED)) }
        updateSummary()
    })
    btnRow.addView(actionBtnFlat("Clear", SALEM_SURFACE) {
        selectedIds.clear()
        checkboxViews.forEach { (_, v) -> v.text = "\u2610"; v.setTextColor(Color.parseColor(SALEM_TEXT)) }
        updateSummary()
    })
    btnRow.addView(actionBtnFlat("Start Tour", SALEM_GOLD) {
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "Select at least one stop", Toast.LENGTH_SHORT).show()
            return@actionBtnFlat
        }
        val selected = allPois.filter { it.id in selectedIds }
        dialog.dismiss()
        tourViewModel.startCustomTour(selected)
    })

    root.addView(btnRow)

    dialog.setContentView(root)
    dialog.show()
}

// ═════════════════════════════════════════════════════════════════════════════
// TIME-BUDGET TOUR — "I have X minutes"
// ═════════════════════════════════════════════════════════════════════════════

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showTimeBudgetDialog() {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor(SALEM_DARK))
        setPadding(dp(16), dp(12), dp(16), dp(16))
    }

    // Header
    val headerRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    headerRow.addView(TextView(this).apply {
        text = "How much time do you have?"
        textSize = 18f
        setTextColor(Color.parseColor(SALEM_GOLD))
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

    root.addView(TextView(this).apply {
        text = "We'll pick the best stops that fit your schedule and optimize the walking route."
        textSize = 13f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        setPadding(0, dp(8), 0, dp(16))
    })

    // Time options
    data class TimeOption(val minutes: Int, val label: String, val description: String)
    val options = listOf(
        TimeOption(30,  "30 min",   "Quick highlights \u2022 4-5 must-see sites"),
        TimeOption(60,  "1 hour",   "Witch Trials essentials + maritime"),
        TimeOption(90,  "1.5 hours","Full themed tour"),
        TimeOption(120, "2 hours",  "Extended exploration"),
        TimeOption(180, "3+ hours", "Complete Salem experience")
    )

    // Preview area (updated when user taps an option)
    val previewLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(8), 0, 0)
    }

    for (option in options) {
        val cardBg = GradientDrawable().apply {
            setColor(Color.parseColor(SALEM_SURFACE))
            cornerRadius = dp(8).toFloat()
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            setPadding(dp(16), dp(12), dp(16), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            val ripple = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
            setBackgroundResource(ripple.resourceId)
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = option.label
            textSize = 16f
            setTextColor(Color.parseColor(SALEM_GOLD))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        // Preview stop count
        val startPoint = lastGpsPoint?.let { GeoPoint(it.latitude, it.longitude) }
        val preview = tourViewModel.buildTimeBudgetTour(option.minutes, startPoint)
        val distKm = tourViewModel.totalRouteDistanceKm(preview)

        titleRow.addView(TextView(this).apply {
            text = "${preview.size} stops \u2022 %.1f km".format(distKm)
            textSize = 12f
            setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        })
        card.addView(titleRow)

        card.addView(TextView(this).apply {
            text = option.description
            textSize = 13f
            setTextColor(Color.parseColor(SALEM_TEXT))
            setPadding(0, dp(4), 0, 0)
        })

        // Show preview POI names
        if (preview.isNotEmpty()) {
            val names = preview.take(5).joinToString(", ") { it.name } +
                    if (preview.size > 5) " + ${preview.size - 5} more" else ""
            card.addView(TextView(this).apply {
                text = names
                textSize = 11f
                setTextColor(Color.parseColor(SALEM_TEXT_DIM))
                setPadding(0, dp(4), 0, 0)
                maxLines = 2
            })
        }

        card.setOnClickListener {
            if (preview.isEmpty()) {
                Toast.makeText(this, "No stops available for this time budget", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            val tourName = "${option.label} Salem Tour"
            tourViewModel.startCustomTour(preview, tourName)
        }

        root.addView(card)
    }

    root.addView(previewLayout)

    val scrollWrap = ScrollView(this).apply { addView(root) }
    dialog.setContentView(scrollWrap)
    dialog.show()
}
