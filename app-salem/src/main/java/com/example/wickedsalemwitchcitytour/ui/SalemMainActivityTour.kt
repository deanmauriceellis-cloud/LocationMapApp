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
import com.example.wickedsalemwitchcitytour.content.PoiContentPolicy
import com.example.wickedsalemwitchcitytour.content.model.Tour
import com.example.wickedsalemwitchcitytour.content.model.TourPoi
import com.example.wickedsalemwitchcitytour.tour.ActiveTour
import com.example.wickedsalemwitchcitytour.tour.GeofenceEventType
import com.example.wickedsalemwitchcitytour.tour.NarrationState
import com.example.wickedsalemwitchcitytour.tour.TourGeofenceEvent
import com.example.wickedsalemwitchcitytour.tour.TourState
import com.example.wickedsalemwitchcitytour.tour.TourSummary
import com.example.wickedsalemwitchcitytour.ui.witchtrials.showWitchTrialsMenuDialog
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

// S179: tour polyline visually identical to point-to-point directions.
// Mirrors DIR_ROUTE_COLOR / DIR_ROUTE_BORDER in SalemMainActivityDirections.
private const val TOUR_ROUTE_COLOR  = "#22C55E"
private const val TOUR_ROUTE_BORDER = "#15803D"

// Tour HUD overlay markers and polyline
internal var tourRoutePolyline: Polyline? = null
internal var tourRouteBorderPolyline: Polyline? = null
internal val tourStopMarkers = mutableListOf<Marker>()
internal val tourTurnMarkers = mutableListOf<Marker>()
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

    // ── Katrina avatar medallion — brand continuity with splash + launcher icon ──
    val creepster = try { androidx.core.content.res.ResourcesCompat.getFont(this, R.font.creepster) } catch (e: Exception) { null }
    val avatar = ImageView(this).apply {
        setImageResource(R.drawable.welcome_katrina_avatar)
        scaleType = ImageView.ScaleType.CENTER_CROP
        layoutParams = LinearLayout.LayoutParams(dp(120), dp(120)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(32)
            bottomMargin = dp(8)
        }
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
    }

    // ── Brand title — Creepster matches splash typography for continuity ──
    val title = TextView(this).apply {
        text = "Katrina's Mystic Visitors Guide"
        textSize = 26f
        setTextColor(Color.parseColor(SALEM_GOLD))
        if (creepster != null) typeface = creepster else setTypeface(Typeface.SERIF, Typeface.BOLD)
        letterSpacing = 0.06f
        gravity = Gravity.CENTER
        maxLines = 2
        setPadding(dp(24), dp(8), dp(24), dp(4))
        setShadowLayer(6f, 2f, 3f, Color.parseColor("#88000000"))
    }
    val subtitle = TextView(this).apply {
        text = "A mystic guide to historic Salem"
        textSize = 14f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        setTypeface(Typeface.SERIF, Typeface.ITALIC)
        gravity = Gravity.CENTER
        setPadding(0, 0, 0, dp(28))
    }

    // ── Card factory — horizontal row: [round Katrina icon] | title / blurb ──
    //
    // S144 operator direction: every entry point on the hub card has its own
    // Katrina pose (tour-guide / scholar / explorer). Icon stays on the left
    // to minimize horizontal waste; the text column gets descriptive single-
    // line title + single-line action blurb.
    fun katrinaCard(
        iconRes: Int,
        title: String,
        blurb: String,
        isHero: Boolean,
        onClick: () -> Unit
    ): LinearLayout {
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor(if (isHero) "#1F1830" else SALEM_SURFACE))
            if (isHero) setStroke(dp(2), Color.parseColor(SALEM_GOLD))
            cornerRadius = dp(if (isHero) 14 else 12).toFloat()
        }
        val iconSize = if (isHero) dp(84) else dp(64)
        val vPad = if (isHero) dp(18) else dp(14)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = bg
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), vPad, dp(18), vPad)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(24); marginEnd = dp(24); bottomMargin = dp(12)
            }

            // Katrina avatar — round-clipped
            addView(ImageView(this@showWelcomeDialog).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    marginEnd = dp(14)
                }
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setOval(0, 0, view.width, view.height)
                    }
                }
            })

            // Text column — title (one line, truncate) + blurb (one line, truncate)
            addView(LinearLayout(this@showWelcomeDialog).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                addView(TextView(this@showWelcomeDialog).apply {
                    text = title
                    textSize = if (isHero) 18f else 16f
                    setTextColor(Color.parseColor(SALEM_GOLD))
                    setTypeface(Typeface.SERIF, Typeface.BOLD)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                addView(TextView(this@showWelcomeDialog).apply {
                    text = blurb
                    textSize = 12f
                    setTextColor(Color.parseColor(SALEM_TEXT))
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(3), 0, 0)
                })
            })
            setOnClickListener { onClick() }
        }
    }

    val tourCard = katrinaCard(
        R.drawable.welcome_card_tour,
        "Guided Walking Tours of Salem",
        "Let Katrina narrate the witch-city streets.",
        isHero = true
    ) { dialog.dismiss(); showTourSelectionDialog() }

    val trialsCard = katrinaCard(
        R.drawable.welcome_card_trials,
        "The Salem Witch Trials of 1692",
        "History, newspapers, and the souls of 1692.",
        isHero = false
    ) { dialog.dismiss(); showWitchTrialsMenuDialog() }

    val exploreCard = katrinaCard(
        R.drawable.welcome_card_explore,
        "Explore Salem on Your Own",
        "Wander wherever your curiosity leads.",
        isHero = false
    ) { dialog.dismiss() }

    // ── Hint ──
    val hint = TextView(this).apply {
        text = "Long-press the map to leap anywhere in Salem."
        textSize = 11f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        setTypeface(Typeface.SERIF, Typeface.ITALIC)
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
        addView(avatar)
        addView(title)
        addView(subtitle)
        addView(tourCard)
        addView(trialsCard)
        addView(exploreCard)
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
    // S146 — Salem Heritage Trail is the featured / marquee tour: pulled out of
    // the Extended bucket and shown first with a gold-border "FEATURED" card.
    val heritageTours = tours.filter { it.theme == "HERITAGE_TRAIL" }
    val witchTrialsTours = tours.filter { it.theme == "essential_highlights" }
    val otherTours = tours.filter { it.theme != "essential_highlights" && it.theme != "HERITAGE_TRAIL" }

    if (heritageTours.isNotEmpty()) {
        listLayout.addView(sectionHeader("Featured — Salem Heritage Trail", dp))
        for (tour in heritageTours) {
            listLayout.addView(buildTourCard(tour, dp, dialog, featured = true))
        }
    }
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
    parentDialog: Dialog,
    featured: Boolean = false
): LinearLayout {
    val cardBg = GradientDrawable().apply {
        setColor(Color.parseColor(if (featured) "#3F2D1B4E" else SALEM_SURFACE))
        cornerRadius = dp(8).toFloat()
        if (featured) setStroke(dp(2), Color.parseColor(SALEM_GOLD))
    }

    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = cardBg
        setPadding(dp(16), dp(12), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }

        // FEATURED badge (top of card, only when featured)
        if (featured) {
            addView(TextView(this@buildTourCard).apply {
                text = "\u2605 FEATURED"
                textSize = 10f
                setTextColor(Color.parseColor(SALEM_DARK))
                setTypeface(null, Typeface.BOLD)
                letterSpacing = 0.18f
                gravity = Gravity.CENTER
                val badgeBg = GradientDrawable().apply {
                    setColor(Color.parseColor(SALEM_GOLD))
                    cornerRadius = dp(3).toFloat()
                }
                background = badgeBg
                setPadding(dp(8), dp(2), dp(8), dp(2))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(6) }
            })
        }

        // Tour name
        addView(TextView(this@buildTourCard).apply {
            text = tour.name
            textSize = if (featured) 18f else 16f
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
            // S186: tour state transitions flip the Layers pref source
            // (explore vs tour) and the narration gate. Refresh both before
            // applying transition-specific UI so the map and gate stay in sync.
            refreshHistoricalModeForActiveTour()
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
internal suspend fun SalemMainActivity.drawTourRoute(activeTour: ActiveTour) {
    clearTourOverlays()

    // S179: tour polyline is computed live against the bundled Salem walking
    // graph via the same Router used for point-to-point directions. The
    // pre-baked routeToNext / tour_legs assets from S178 are no longer
    // consulted at runtime — geometry comes straight from
    // WalkingDirections.getMultiStopRoute. Internal polyline waypoints stay
    // hidden; only numbered stop markers and turn-by-turn intersection
    // markers are drawn.
    val route = tourViewModel.computeTourPolyline(activeTour)
    if (route != null && route.polyline.isNotEmpty()) {
        val border = Polyline().apply {
            setPoints(route.polyline)
            outlinePaint.color = Color.parseColor(TOUR_ROUTE_BORDER)
            outlinePaint.strokeWidth = 10f
            outlinePaint.isAntiAlias = true
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
        }
        binding.mapView.overlays.add(border)
        tourRouteBorderPolyline = border

        val routeLine = Polyline().apply {
            setPoints(route.polyline)
            outlinePaint.color = Color.parseColor(TOUR_ROUTE_COLOR)
            outlinePaint.strokeWidth = 6f
            outlinePaint.isAntiAlias = true
            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
        }
        binding.mapView.overlays.add(routeLine)
        tourRoutePolyline = routeLine

        // Intersection / turn markers along the route — same treatment as
        // point-to-point directions. Skip plain "continue" steps to avoid
        // clutter on long multi-leg tours.
        for (instruction in route.instructions) {
            if (instruction.text.isBlank()) continue
            if (instruction.text.lowercase().contains("continue")) continue
            val marker = Marker(binding.mapView).apply {
                position = instruction.location
                title = instruction.text
                snippet = "%.0f m".format(instruction.distanceM)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = MarkerIconHelper.dot(this@drawTourRoute, Color.parseColor(TOUR_ROUTE_COLOR))
            }
            binding.mapView.overlays.add(marker)
            tourTurnMarkers.add(marker)
        }

        DebugLogger.i("SalemMainActivityTour",
            "Drew tour '${activeTour.tour.id}' live route: ${route.polyline.size} pts, %.1fkm, ${route.instructions.size} turns".format(route.distanceKm))
    } else {
        DebugLogger.w("SalemMainActivityTour",
            "Tour '${activeTour.tour.id}': could not compute multi-stop route")
    }

    // Numbered stop markers — drawn above the polyline regardless of
    // whether the router produced geometry, so the user always sees the
    // ordered tour stops on the map.
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
    tourRouteBorderPolyline?.let { binding.mapView.overlays.remove(it) }
    tourRouteBorderPolyline = null
    for (m in tourStopMarkers) binding.mapView.overlays.remove(m)
    tourStopMarkers.clear()
    for (m in tourTurnMarkers) binding.mapView.overlays.remove(m)
    tourTurnMarkers.clear()
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

    // S154: strip AI-generated description for non-licensed commercial tour
    // stops. TourPoi is a projection without merchant_tier, so use the
    // category-only gate — safe for V1 (no merchants licensed yet).
    val stripped = PoiContentPolicy.shouldStripByCategory(poi.category)
    if (!stripped) {
        poi.description?.let {
            descLayout.addView(TextView(this).apply {
                text = it
                textSize = 14f
                setTextColor(Color.parseColor(SALEM_TEXT))
                setPadding(0, 0, 0, dp(8))
            })
        }
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

    // Narrate — speak the long narration on demand. Hidden for stripped
    // commercial POIs (S154): their "narration" is just name+address, which
    // the user already sees, so the on-demand button adds nothing.
    if (poi.longNarration != null && !stripped) {
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

/**
 * Call from the GPS update observer to keep the tour engine informed of current location.
 *
 * S125: now takes optional speed + bearing from the fix so the narration
 * extension can speed-gate its movement-bearing update. Walk-sim and manual
 * injections pass null for both (their positions are ground truth, and the
 * 1 m position-delta check is sufficient).
 */
internal fun SalemMainActivity.updateTourLocation(
    point: GeoPoint,
    speedMps: Float? = null,
    bearingDeg: Float? = null
) {
    tourViewModel.onLocationUpdate(point)

    // Phase 9T: Feed location to narration geofence manager (drives ENTRY/APPROACH events).
    val nearby = narrationGeofenceManager.checkPosition(point.latitude, point.longitude)

    // S112: Push the user position into the narration extension's file-scope cache,
    // and refresh the proximity dock from the current queue snapshot.
    updateNarrationUserPosition(point.latitude, point.longitude, speedMps, bearingDeg)

    // S115: If heading-up rotation is enabled, apply the smoothed bearing to
    // the map canvas. Must run AFTER updateNarrationUserPosition so that
    // getLastMovementBearing() reflects this fix.
    if (isHeadingUpMode()) {
        applyHeadingUpRotation()
    }

    // S110: Feed the same nearby list to the POI encounter tracker, which opens /
    // updates / closes "I got close to this POI" rows in user_data.db. The tracker
    // applies its own 100m proximity filter (narrower than the manager's 300m
    // nearby radius) so only "actually walked past it" encounters get recorded.
    poiEncounterTracker.recordObservation(nearby, System.currentTimeMillis())

    // Also check street corridors
    corridorManager?.checkPosition(point.latitude, point.longitude)?.forEach { trigger ->
        DebugLogger.i("SalemMainActivity", "Corridor triggered: ${trigger.corridor.name} (${trigger.distanceM.toInt()}m)")
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// S115: HEADING-UP MAP ROTATION
// ═════════════════════════════════════════════════════════════════════════════
//
// When enabled, the map canvas rotates so the user's direction of travel
// points up on the screen. Source of truth is `lastMovementBearing` computed
// in updateNarrationUserPosition from consecutive GPS fixes (≥ 1m moves).
//
// Three pieces of polish prevent visual swimming:
//
//   1. EMA smoothing. Each new bearing is blended with the previous smoothed
//      value (weight SMOOTHING_ALPHA on the new sample). Handles angle wrap
//      correctly — we blend unit vectors on the compass, not raw degrees, so
//      a north-ish bearing jumping between 358° and 2° stays near 0°.
//
//   2. 5° hysteresis. Small changes under HYSTERESIS_DEG don't trigger a
//      setMapOrientation call. The on-screen result is "rotation updates in
//      perceptible steps, not every fix".
//
//   3. Stationary freeze. `lastMovementBearing` only updates when the user
//      has moved ≥ 1m (BEARING_UPDATE_MIN_MOVE_M in the narration file). If
//      the user is standing still, lastMovementBearing stays put and we
//      re-apply the same rotation — no jitter while dwelling at the Witch
//      House. The `isHeadingUpMode()` check gates the whole thing so North-up
//      mode costs nothing per fix.

/** S115: File-scope smoothed bearing in degrees. Null = no bearing applied yet. */
private var smoothedHeadingDeg: Double? = null

/** S115: Last rotation we actually called setMapOrientation with (for hysteresis).
 *  Null until the first apply, or after a reset — the first apply after a reset
 *  always lands regardless of delta. */
private var lastAppliedRotationDeg: Float? = null

/** S115: EMA weight on the newest bearing sample. Lower = smoother, slower to react.
 *  This is the "walking" value — GPS bearing updates every few seconds and we
 *  want to filter out individual fix noise without lagging real direction changes. */
private const val HEADING_SMOOTHING_ALPHA = 0.25

/** S115: EMA weight used when the device orientation tracker reports static mode.
 *  Much heavier smoothing (~5x) absorbs magnetometer noise without sacrificing
 *  responsiveness, because in static mode the tablet isn't rotating anyway —
 *  every sample we get is either a real no-change or HAL noise, and we'd rather
 *  average out the noise than track it. Used when tablet is sitting still
 *  (on desk OR held perfectly motionless). */
private const val HEADING_SMOOTHING_ALPHA_STATIC = 0.05

/** S115: Reactive EMA weight used when the sensor is the source AND the
 *  tracker is NOT in static mode (user is actively turning the tablet).
 *  Each sample is intentional movement that deserves near-immediate response.
 *  At 0.85 a single sample covers 85% of the gap, so a 90° flick closes in
 *  one sample from 90° → ~14° — and the SNAP-on-static-entry transition
 *  (see applyHeadingUpRotation) closes the remaining ~14° on the very next
 *  sample once rotation stops. */
private const val HEADING_SMOOTHING_ALPHA_REACTIVE = 0.85

/** S115: Rotation changes under this many degrees are ignored (anti-jitter).
 *  Walking / normal default. */
private const val HEADING_HYSTERESIS_DEG = 5.0

/** S115: Tighter hysteresis when the person is stationary and actively turning
 *  the tablet. We want small deliberate rotations to land immediately, not get
 *  held back by a 5° gate. */
private const val HEADING_HYSTERESIS_DEG_REACTIVE = 2.0

/**
 * S115: Maximum age of the GPS-derived movement bearing before we fall back
 * to the device orientation sensor. Below this the user is considered to be
 * "actively walking" and their direction of travel is the source of truth.
 * Above it the walker has stopped (or slowed enough that bearing is stale),
 * and the tablet's physical orientation takes over so the map rotates as the
 * user pans around to look at buildings.
 *
 * 3 seconds is chosen to be longer than a single GPS interval (5s worst case
 * at walking speed) with a safety margin — we don't want to flap between
 * sources on every fix.
 */
private const val STATIONARY_FALLBACK_MS: Long = 3_000L

/**
 * S115: Called from updateTourLocation on every GPS fix, AND from the
 * DeviceOrientationTracker callback on every sensor update (rate-limited to
 * ~4 Hz) when heading-up mode is on.
 *
 * ## Hybrid heading source
 *
 * Selects the best source each call:
 *
 *   - **GPS movement bearing** when it was updated within
 *     [STATIONARY_FALLBACK_MS]. This is the "user is walking" branch —
 *     direction of travel is the source of truth and is immune to magnetic
 *     interference in downtown Salem's brick + wrought iron.
 *
 *   - **Device azimuth from the orientation sensor** when the GPS bearing
 *     is stale. This is the "user has stopped at a POI and is turning to
 *     look around" branch — the map rotates with the tablet so the
 *     buildings on screen match the buildings in the user's sightline.
 *
 *   - **Stale GPS bearing as last-resort fallback** if the sensor path
 *     has no reading yet (cold start, no sensor, etc.). Better than nothing.
 *
 * The chosen target is fed through the same EMA smoothing + 5° hysteresis
 * that walk-only mode used. The crossfade between sources happens
 * automatically — when we switch from GPS bearing to sensor, the EMA blends
 * from the old smoothed value toward the new sensor value over ~5 samples
 * (~1.25 seconds at the 250ms sensor apply rate). No explicit transition
 * logic needed.
 */
internal fun SalemMainActivity.applyHeadingUpRotation() {
    val moveBearing = getLastMovementBearing()
    val moveBearingAgeMs = getLastMovementBearingAgeMs()

    // S195 — GPS-only source. Operator direction: only GPS-derived bearing
    // drives map rotation; the magnetometer/gyro path was removed because
    // it fought GPS noise in the field. When the user stops moving the
    // bearing simply freezes (last walking direction) until they move ≥1m
    // again — that's the intended behavior.
    val targetBearing: Double = moveBearing ?: run {
        DebugLogger.d("HEADING-UP", "skip — no GPS movement bearing yet")
        return
    }

    val smoothed = blendBearingDeg(smoothedHeadingDeg, targetBearing, HEADING_SMOOTHING_ALPHA)
    smoothedHeadingDeg = smoothed

    val targetRotation = (-smoothed).toFloat()
    val previouslyApplied = lastAppliedRotationDeg
    if (previouslyApplied != null) {
        val delta = shortestAngleDeltaDeg(
            previouslyApplied.toDouble(),
            targetRotation.toDouble()
        )
        if (delta < HEADING_HYSTERESIS_DEG) {
            DebugLogger.d(
                "HEADING-UP",
                "skip hysteresis — target=${targetRotation.toInt()}° " +
                    "applied=${previouslyApplied.toInt()}° Δ=${"%.1f".format(delta)}° " +
                    "(raw=${targetBearing.toInt()}°, smoothed=${smoothed.toInt()}°)"
            )
            return
        }
        DebugLogger.i(
            "HEADING-UP",
            "apply — rotation=${targetRotation.toInt()}° " +
                "(raw=${targetBearing.toInt()}°, smoothed=${smoothed.toInt()}°, " +
                "Δ=${"%.1f".format(delta)}°, " +
                "bearingAge=${if (moveBearingAgeMs == Long.MAX_VALUE) "never" else "${moveBearingAgeMs}ms"})"
        )
    } else {
        DebugLogger.i(
            "HEADING-UP",
            "apply — rotation=${targetRotation.toInt()}° " +
                "(raw=${targetBearing.toInt()}°, smoothed=${smoothed.toInt()}°, FIRST APPLY, " +
                "bearingAge=${if (moveBearingAgeMs == Long.MAX_VALUE) "never" else "${moveBearingAgeMs}ms"})"
        )
    }
    lastAppliedRotationDeg = targetRotation
    binding.mapView.setMapOrientation(targetRotation)
}

/**
 * S115: Called from the heading-up toggle click handler to apply the current
 * best-available heading immediately, so the user doesn't have to wait for
 * the next GPS fix or sensor tick to see the map rotate. Delegates to the
 * full hybrid source selector in [applyHeadingUpRotation] but bypasses
 * hysteresis so the first rotation always lands.
 */
internal fun SalemMainActivity.applyHeadingUpRotationImmediate() {
    // Reset the hysteresis reference so the first apply after toggle-on
    // always commits, even if the target is within 5° of the previous
    // (stale) value.
    lastAppliedRotationDeg = null
    applyHeadingUpRotation()
}

/**
 * S115: Called from the heading-up toggle when the user turns it off. Snaps the
 * map back to north-up and clears the smoothed state so the next ON press
 * starts from a clean slate.
 */
internal fun SalemMainActivity.resetHeadingUpRotation() {
    smoothedHeadingDeg = null
    lastAppliedRotationDeg = null
    binding.mapView.setMapOrientation(0f)
    binding.mapView.invalidate()
}

/**
 * Blend two compass bearings with exponential moving average, correctly
 * handling angle wrap. Both inputs and the return are in degrees [0, 360).
 *
 * Implemented by averaging unit vectors on the (sin, cos) compass rather than
 * raw degrees, which avoids the bug where a 358° → 2° transition averages to
 * 180° if you do it naively.
 */
private fun blendBearingDeg(prev: Double?, new: Double, alpha: Double): Double {
    if (prev == null) return new
    val prevRad = Math.toRadians(prev)
    val newRad = Math.toRadians(new)
    val x = (1.0 - alpha) * kotlin.math.cos(prevRad) + alpha * kotlin.math.cos(newRad)
    val y = (1.0 - alpha) * kotlin.math.sin(prevRad) + alpha * kotlin.math.sin(newRad)
    val blended = Math.toDegrees(kotlin.math.atan2(y, x))
    return (blended + 360.0) % 360.0
}

/**
 * Smallest signed angular distance between two bearings in degrees, returned
 * as an absolute value in [0, 180]. Used for the hysteresis check.
 */
private fun shortestAngleDeltaDeg(a: Double, b: Double): Double {
    var diff = ((b - a + 540.0) % 360.0) - 180.0
    if (diff < 0) diff = -diff
    return diff
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
