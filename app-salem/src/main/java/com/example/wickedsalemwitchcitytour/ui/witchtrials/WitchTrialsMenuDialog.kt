/*
 * WickedSalemWitchCityTour v1.5 — Phase 9X (Session 127)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * "The Salem Witch Trials" 3-panel sub-menu, opened from the welcome
 * dialog's hero card. Phase 1 ships the menu shell; Phases 3-5 wire
 * each panel to its real implementation.
 */

package com.example.wickedsalemwitchcitytour.ui.witchtrials

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.ui.SalemMainActivity

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module WitchTrialsMenuDialog.kt"

// Match the existing Salem brand palette from SalemMainActivityTour.kt.
private const val SALEM_PURPLE = "#2D1B4E"
private const val SALEM_GOLD = "#C9A84C"
private const val SALEM_DARK = "#1E1E2A"
private const val SALEM_SURFACE = "#2A2A3C"
private const val SALEM_TEXT = "#F5F0E8"
private const val SALEM_TEXT_DIM = "#B8AFA0"

@SuppressLint("SetTextI18n")
fun SalemMainActivity.showWitchTrialsMenuDialog() {
    DebugLogger.i("WitchTrials", "showWitchTrialsMenuDialog")

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    // ── Header ──
    val titleText = TextView(this).apply {
        text = "The Salem Witch Trials"
        textSize = 22f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(Typeface.SERIF, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val closeBtn = TextView(this).apply {
        text = "\u2715"
        textSize = 22f
        setTextColor(Color.WHITE)
        setPadding(dp(16), 0, dp(8), 0)
        setOnClickListener { dialog.dismiss() }
    }
    val headerRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(20), dp(16), dp(16), dp(12))
        addView(titleText)
        addView(closeBtn)
    }

    // ── Subtitle ──
    val subtitle = TextView(this).apply {
        text = "1692 Salem, in three lenses"
        textSize = 13f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        setPadding(dp(20), 0, dp(20), dp(20))
    }

    // ── Panel cards (stacked vertically inside the menu) ──
    fun panelCard(
        icon: String,
        title: String,
        desc: String,
        onClick: () -> Unit
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
                marginStart = dp(20); marginEnd = dp(20); bottomMargin = dp(14)
            }
            addView(TextView(this@showWitchTrialsMenuDialog).apply {
                text = icon; textSize = 32f
                setPadding(0, 0, dp(16), 0)
            })
            val col = LinearLayout(this@showWitchTrialsMenuDialog).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            col.addView(TextView(this@showWitchTrialsMenuDialog).apply {
                text = title; textSize = 17f
                setTextColor(Color.parseColor(SALEM_GOLD))
                setTypeface(null, Typeface.BOLD)
            })
            col.addView(TextView(this@showWitchTrialsMenuDialog).apply {
                text = desc; textSize = 12f
                setTextColor(Color.parseColor(SALEM_TEXT))
                setPadding(0, dp(4), 0, 0)
            })
            addView(col)
            setOnClickListener { onClick() }
        }
    }

    val historyCard = panelCard(
        icon = "\uD83D\uDCDC",
        title = "The Salem Witch Trials History",
        desc = "Sixteen chronological tiles from pre-1692 through the 1693 fallout."
    ) { showWitchTrialsHistoryPlaceholder() }

    val newspapersCard = panelCard(
        icon = "\uD83D\uDCF0",
        title = "The Oracle Newspaper",
        desc = "202 period newspaper articles, browsable by date and crisis phase."
    ) { showWitchTrialsNewspapersPlaceholder() }

    val peopleCard = panelCard(
        icon = "\uD83D\uDC65",
        title = "The People of Salem 1692",
        desc = "Forty-nine principal figures: judges, accusers, accused, clergy."
    ) { showWitchTrialsPeoplePlaceholder() }

    val listLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(historyCard)
        addView(newspapersCard)
        addView(peopleCard)
    }
    val scroll = ScrollView(this).apply { addView(listLayout) }

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#1A0F2E"), Color.parseColor(SALEM_PURPLE))
        )
        background = bg
        addView(headerRow)
        addView(subtitle)
        addView(scroll)
    }

    dialog.setContentView(root)
    dialog.show()
}

// ── Phase 1 placeholders — real implementations land in Phases 3, 4, 5 ──

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showWitchTrialsHistoryPlaceholder() {
    showComingSoonDialog(
        title = "The Salem Witch Trials History",
        body = "Coming in Phase 3: a 4\u00D74 grid of 16 chronological tiles, " +
               "each with a 500-1000 word LLM-drafted article. Tap to read; " +
               "Narrator Mode auto-speaks the body."
    )
}

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showWitchTrialsNewspapersPlaceholder() {
    showComingSoonDialog(
        title = "The Oracle Newspaper",
        body = "Coming in Phase 4: browse all 202 Salem newspapers " +
               "(1691-11-01 through 1693-05-09), filtered by crisis phase, " +
               "with TTS playback per article."
    )
}

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showWitchTrialsPeoplePlaceholder() {
    showComingSoonDialog(
        title = "The People of Salem 1692",
        body = "Coming in Phase 5: 49 principal figures with hand-drawn " +
               "pencil-sketch portraits, biographies, and cross-links to the " +
               "tiles and newspapers where they appear."
    )
}

@SuppressLint("SetTextI18n")
private fun SalemMainActivity.showComingSoonDialog(title: String, body: String) {
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val titleText = TextView(this).apply {
        text = title
        textSize = 20f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(Typeface.SERIF, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val closeBtn = TextView(this).apply {
        text = "\u2715"
        textSize = 20f
        setTextColor(Color.WHITE)
        setPadding(dp(16), 0, dp(4), 0)
        setOnClickListener { dialog.dismiss() }
    }
    val headerRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(20), dp(16), dp(12), dp(12))
        addView(titleText)
        addView(closeBtn)
    }

    val bodyText = TextView(this).apply {
        text = body
        textSize = 14f
        setTextColor(Color.parseColor(SALEM_TEXT))
        setPadding(dp(24), dp(40), dp(24), dp(40))
        gravity = Gravity.CENTER
    }

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(Color.parseColor(SALEM_DARK))
        addView(headerRow)
        addView(bodyText)
    }

    dialog.setContentView(root)
    dialog.show()
}
