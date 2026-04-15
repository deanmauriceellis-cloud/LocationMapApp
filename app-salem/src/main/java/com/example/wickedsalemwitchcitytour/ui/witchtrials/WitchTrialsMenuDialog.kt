/*
 * WickedSalemWitchCityTour v1.5 — Phase 9X (Session 127; history grid S129)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * "The Salem Witch Trials" 3-panel sub-menu, opened from the welcome
 * dialog's hero card. The History panel now opens a real 4×4 tile grid
 * backed by salem_witch_trials_articles (S128 generation, S129 UI wiring).
 */

package com.example.wickedsalemwitchcitytour.ui.witchtrials

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.locationmapapp.ui.menu.MenuPrefs
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.content.model.WitchTrialsArticle
import com.example.wickedsalemwitchcitytour.ui.SalemMainActivity
import kotlinx.coroutines.launch

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module WitchTrialsMenuDialog.kt"

// Match the existing Salem brand palette from SalemMainActivityTour.kt.
private const val SALEM_PURPLE = "#2D1B4E"
private const val SALEM_GOLD = "#C9A84C"
private const val SALEM_DARK = "#1E1E2A"
private const val SALEM_SURFACE = "#2A2A3C"
private const val SALEM_TEXT = "#F5F0E8"
private const val SALEM_TEXT_DIM = "#B8AFA0"

private const val TTS_TAG = "witchtrials_article"

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
    ) { showWitchTrialsHistoryDialog() }

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

// ── Phase 9X.3 — History 4×4 tile grid (S129) ────────────────────────

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showWitchTrialsHistoryDialog() {
    DebugLogger.i("WitchTrials", "showWitchTrialsHistoryDialog")

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val titleText = TextView(this).apply {
        text = "Salem Witch Trials — History"
        textSize = 20f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(Typeface.SERIF, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    val closeBtn = TextView(this).apply {
        text = "\u2715"; textSize = 22f
        setTextColor(Color.WHITE)
        setPadding(dp(16), 0, dp(8), 0)
        setOnClickListener { dialog.dismiss() }
    }
    val headerRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(20), dp(16), dp(16), dp(8))
        addView(titleText)
        addView(closeBtn)
    }

    val subtitle = TextView(this).apply {
        text = "Sixteen tiles — pre-1692 through the long memory. Tap any tile to read."
        textSize = 12f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        setPadding(dp(20), 0, dp(20), dp(12))
    }

    val grid = GridLayout(this).apply {
        rowCount = 4
        columnCount = 4
        useDefaultMargins = false
        setPadding(dp(12), dp(4), dp(12), dp(16))
    }

    val loadingText = TextView(this).apply {
        text = "Loading…"
        textSize = 14f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(40), dp(24), dp(40))
    }

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#1A0F2E"), Color.parseColor(SALEM_PURPLE))
        )
        background = bg
        addView(headerRow)
        addView(subtitle)
        addView(loadingText)
    }

    dialog.setContentView(root)
    dialog.setOnDismissListener {
        tourViewModel.cancelSegmentsWithTag(TTS_TAG)
    }
    dialog.show()

    lifecycleScope.launch {
        try {
            DebugLogger.i("WitchTrials", "history grid: calling getAllArticles()")
            val articles = witchTrialsViewModel.getAllArticles().sortedBy { it.tileOrder }
            DebugLogger.i("WitchTrials", "history grid: loaded ${articles.size} articles")

            root.removeView(loadingText)
            root.addView(ScrollView(this@showWitchTrialsHistoryDialog).apply {
                addView(grid)
            })

            val cellWidth = (resources.displayMetrics.widthPixels - dp(24)) / 4
            DebugLogger.i("WitchTrials", "history grid: cellWidth=$cellWidth screenW=${resources.displayMetrics.widthPixels}")
            for (article in articles) {
                grid.addView(buildHistoryTile(article, cellWidth, dp) {
                    showWitchTrialsTileDetailDialog(article)
                })
            }
            DebugLogger.i("WitchTrials", "history grid: added ${articles.size} tiles to GridLayout")
        } catch (e: Exception) {
            DebugLogger.e("WitchTrials", "history grid FAILED: ${e.message}", e)
            loadingText.text = "Failed to load: ${e.message}"
        }
    }
}

private fun SalemMainActivity.buildHistoryTile(
    article: WitchTrialsArticle,
    cellWidth: Int,
    dp: (Int) -> Int,
    onClick: () -> Unit
): LinearLayout {
    val iconGlyph = tileKindGlyph(article.tileKind)

    val bg = GradientDrawable().apply {
        setColor(Color.parseColor(SALEM_SURFACE))
        cornerRadius = dp(10).toFloat()
        setStroke(dp(1), Color.parseColor("#3A3A50"))
    }

    val icon = TextView(this).apply {
        text = iconGlyph
        textSize = 22f
        gravity = Gravity.CENTER
    }

    val periodText = TextView(this).apply {
        text = article.periodLabel ?: article.tileKind.replaceFirstChar { it.uppercase() }
        textSize = 9f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(null, Typeface.BOLD)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_HORIZONTAL
    }

    val title = TextView(this).apply {
        text = article.title
        textSize = 11f
        setTextColor(Color.parseColor(SALEM_TEXT))
        setTypeface(Typeface.SERIF, Typeface.BOLD)
        maxLines = 3
        ellipsize = android.text.TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_HORIZONTAL
    }

    val teaser = TextView(this).apply {
        text = article.teaser
        textSize = 9f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        maxLines = 3
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(0, dp(4), 0, 0)
    }

    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        background = bg
        setPadding(dp(8), dp(10), dp(8), dp(10))
        val params = GridLayout.LayoutParams().apply {
            width = cellWidth - dp(8)
            height = dp(150)
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }
        layoutParams = params
        isClickable = true
        isFocusable = true
        addView(icon)
        addView(periodText)
        addView(title)
        addView(teaser)
        setOnClickListener { onClick() }
    }
}

private fun tileKindGlyph(kind: String): String = when (kind) {
    "intro" -> "\uD83C\uDFDA\uFE0F"       // classical building
    "month" -> "\uD83C\uDF19"              // crescent moon
    "fallout" -> "\u2696\uFE0F"            // scales of justice
    "closing" -> "\uD83D\uDCDC"            // scroll
    "epilogue" -> "\uD83D\uDD6F\uFE0F"     // candle
    else -> "\uD83D\uDCD6"                 // open book
}

// ── Phase 9X.3 — Tile detail dialog (S129) ────────────────────────────

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showWitchTrialsTileDetailDialog(article: WitchTrialsArticle) {
    DebugLogger.i("WitchTrials", "showTileDetail id=${article.id} order=${article.tileOrder}")

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val prefs = getSharedPreferences(MenuPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val narratorMode = prefs.getBoolean(MenuPrefs.PREF_NARRATOR_MODE_ENABLED, false)

    // Speak state — flip when TTS is running for this article
    var speaking = false

    val closeBtn = TextView(this).apply {
        text = "\u2715"; textSize = 22f
        setTextColor(Color.WHITE)
        setPadding(dp(16), 0, dp(8), 0)
        setOnClickListener { dialog.dismiss() }
    }

    val topBar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        setPadding(dp(8), dp(8), dp(8), 0)
        addView(closeBtn)
    }

    val periodText = TextView(this).apply {
        text = article.periodLabel ?: ""
        textSize = 12f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(null, Typeface.BOLD)
        setPadding(dp(24), dp(4), dp(24), dp(4))
    }

    val titleText = TextView(this).apply {
        text = article.title
        textSize = 22f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(Typeface.SERIF, Typeface.BOLD)
        setPadding(dp(24), dp(4), dp(24), dp(12))
    }

    val teaserText = TextView(this).apply {
        text = article.teaser
        textSize = 14f
        setTextColor(Color.parseColor(SALEM_TEXT))
        setTypeface(null, Typeface.ITALIC)
        setPadding(dp(24), 0, dp(24), dp(16))
    }

    // Body — render paragraphs (split on blank lines or \n\n)
    val bodyText = TextView(this).apply {
        text = article.body
        textSize = 15f
        setTextColor(Color.parseColor(SALEM_TEXT))
        setLineSpacing(dp(4).toFloat(), 1.15f)
        setPadding(dp(24), dp(8), dp(24), dp(24))
    }

    // Speak button — toggles TTS on/off
    val speakBtn = TextView(this).apply {
        text = "\u25B6 Speak"
        textSize = 14f
        setTextColor(Color.parseColor(SALEM_TEXT))
        setTypeface(null, Typeface.BOLD)
        setPadding(dp(24), dp(10), dp(24), dp(10))
        val pillBg = GradientDrawable().apply {
            setColor(Color.parseColor("#3B2A6A"))
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), Color.parseColor(SALEM_GOLD))
        }
        background = pillBg
        isClickable = true
        isFocusable = true
    }

    fun startSpeaking() {
        tourViewModel.cancelSegmentsWithTag(TTS_TAG)
        tourViewModel.speakSheetSection(
            tag = TTS_TAG,
            text = article.body,
            label = article.title,
            voiceId = null
        )
        speaking = true
        speakBtn.text = "\u25A0 Stop"
    }

    fun stopSpeaking() {
        tourViewModel.cancelSegmentsWithTag(TTS_TAG)
        speaking = false
        speakBtn.text = "\u25B6 Speak"
    }

    speakBtn.setOnClickListener {
        if (speaking) stopSpeaking() else startSpeaking()
    }

    val speakRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(16), dp(4), dp(16), dp(16))
        addView(speakBtn)
    }

    val contentColumn = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(topBar)
        addView(periodText)
        addView(titleText)
        addView(teaserText)
        addView(speakRow)
        addView(bodyText)
    }

    val scroll = ScrollView(this).apply {
        addView(contentColumn)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#1A0F2E"), Color.parseColor(SALEM_PURPLE))
        )
        background = bg
        addView(scroll)
    }

    dialog.setContentView(root)
    dialog.setOnDismissListener {
        tourViewModel.cancelSegmentsWithTag(TTS_TAG)
    }
    dialog.show()

    if (narratorMode) {
        DebugLogger.i("WitchTrials", "narrator-mode on — auto-speaking ${article.id}")
        startSpeaking()
    }
}

// ── Phase 4, 5 placeholders ───────────────────────────────────────────

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
