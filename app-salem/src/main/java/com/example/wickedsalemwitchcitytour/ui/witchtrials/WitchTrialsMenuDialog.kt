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
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.ViewGroup
import android.graphics.BitmapFactory
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.locationmapapp.ui.menu.MenuPrefs
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.content.model.SalemPoi
import com.example.wickedsalemwitchcitytour.content.model.WitchTrialsArticle
import com.example.wickedsalemwitchcitytour.content.model.WitchTrialsNewspaper
import com.example.wickedsalemwitchcitytour.content.model.WitchTrialsNpcBio
import com.example.wickedsalemwitchcitytour.ui.SalemMainActivity
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Calendar

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
private const val TTS_TAG_NEWS = "witchtrials_newspaper"
private const val TTS_TAG_BIO = "witchtrials_bio"

/** Thread-safe bitmap cache for portrait thumbnails. Shared across browse/detail. */
private val portraitCache = LruCache<String, android.graphics.Bitmap>(60)

/** Load a portrait bitmap off the main thread, set it on the ImageView when ready. */
private fun SalemMainActivity.loadPortraitAsync(
    assetPath: String,
    imageView: ImageView,
    targetSize: Int = 0
) {
    portraitCache.get(assetPath)?.let { imageView.setImageBitmap(it); return }
    lifecycleScope.launch {
        val bmp = withContext(Dispatchers.IO) {
            try {
                val opts = if (targetSize > 0) {
                    // Decode bounds first for downsampling
                    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, boundsOpts) }
                    val w = boundsOpts.outWidth; val h = boundsOpts.outHeight
                    val sample = maxOf(1, minOf(w, h) / targetSize)
                    BitmapFactory.Options().apply { inSampleSize = sample }
                } else null
                assets.open(assetPath).use { BitmapFactory.decodeStream(it, null, opts) }
            } catch (e: Exception) {
                DebugLogger.w("WitchTrials", "portrait load failed: $assetPath — ${e.message}")
                null
            }
        }
        bmp?.let {
            portraitCache.put(assetPath, it)
            imageView.setImageBitmap(it)
        }
    }
}

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
    ) { showWitchTrialsNewspaperBrowserDialog() }

    val peopleCard = panelCard(
        icon = "\uD83D\uDC65",
        title = "The People of Salem 1692",
        desc = "Forty-nine principal figures: judges, accusers, accused, clergy."
    ) { showWitchTrialsPeopleBrowserDialog() }

    val historicSitesCard = panelCard(
        icon = "\uD83C\uDFDB",   // 🏛 classical building
        title = "Historic Sites of Salem",
        desc = "117 historic buildings, home sites, cemeteries, and landmarks across four centuries."
    ) { showHistoricSitesBrowserDialog() }

    // ── Today in 1692 card placeholder (populated async) ──
    val todayCardContainer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), 0, dp(20), dp(14))
    }

    val listLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(todayCardContainer)
        addView(historyCard)
        addView(newspapersCard)
        addView(peopleCard)
        addView(historicSitesCard)
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

    // ── Load "Today in 1692" card ──
    lifecycleScope.launch {
        val card = buildTodayIn1692Card(dp)
        if (card != null) {
            todayCardContainer.addView(card)
        }
    }
}

// ── Phase 9X.7 — "Today in 1692" card (S133) ─────────────────────────

/**
 * Build a "Today in 1692" card by matching the current calendar month-day
 * against newspaper dates. Returns null if no newspaper falls within ±3 days.
 *
 * - Exact match: "Today in 1692" — shows headline + summary.
 * - Near match (±1-3 days): "Near this day in 1692" — shows the closest.
 */
private suspend fun SalemMainActivity.buildTodayIn1692Card(
    dp: (Int) -> Int
): LinearLayout? {
    val cal = Calendar.getInstance()
    val month = cal.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
    val day = cal.get(Calendar.DAY_OF_MONTH)

    // Try exact match first
    val exact = witchTrialsViewModel.getNewspapersByMonthDay(month, day)
    if (exact.isNotEmpty()) {
        val paper = exact.first()
        return buildTodayCard(
            dp = dp,
            label = "Today in 1692",
            date = paper.longDate ?: paper.date,
            headline = paper.headline ?: "SALEM DISPATCH",
            summary = paper.headlineSummary ?: paper.summary ?: paper.lede ?: "",
            onClick = { showWitchTrialsNewspaperDetailDialog(paper) }
        )
    }

    // Try ±1-3 day window, expanding until we find something
    for (offset in 1..3) {
        for (delta in listOf(-offset, offset)) {
            val probe = Calendar.getInstance().apply {
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                add(Calendar.DAY_OF_MONTH, delta)
            }
            val probeMonth = probe.get(Calendar.MONTH) + 1
            val probeDay = probe.get(Calendar.DAY_OF_MONTH)
            val nearby = witchTrialsViewModel.getNewspapersByMonthDay(probeMonth, probeDay)
            if (nearby.isNotEmpty()) {
                val paper = nearby.first()
                val daysLabel = if (kotlin.math.abs(delta) == 1) "Yesterday" else "${kotlin.math.abs(delta)} days ago"
                val prefix = if (delta < 0) daysLabel else "In ${ kotlin.math.abs(delta)} day${if (kotlin.math.abs(delta) > 1) "s" else ""}"
                return buildTodayCard(
                    dp = dp,
                    label = "Near this day in 1692",
                    date = paper.longDate ?: paper.date,
                    headline = paper.headline ?: "SALEM DISPATCH",
                    summary = paper.headlineSummary ?: paper.summary ?: paper.lede ?: "",
                    onClick = { showWitchTrialsNewspaperDetailDialog(paper) }
                )
            }
        }
    }

    return null // No newspaper within ±3 days of today
}

private fun SalemMainActivity.buildTodayCard(
    dp: (Int) -> Int,
    label: String,
    date: String,
    headline: String,
    summary: String,
    onClick: () -> Unit
): LinearLayout {
    val cardBg = GradientDrawable().apply {
        setColor(Color.parseColor("#2A1F3E"))
        cornerRadius = dp(14).toFloat()
        setStroke(dp(2), Color.parseColor(SALEM_GOLD))
    }

    val labelText = TextView(this).apply {
        text = "\uD83D\uDD6F\uFE0F  $label"  // candle emoji
        textSize = 11f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(null, Typeface.BOLD)
        letterSpacing = 0.12f
    }

    val dateText = TextView(this).apply {
        text = date
        textSize = 18f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(Typeface.SERIF, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(2))
    }

    val headlineText = TextView(this).apply {
        text = headline
        textSize = 15f
        setTextColor(Color.parseColor(SALEM_TEXT))
        setTypeface(Typeface.SERIF, Typeface.BOLD)
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(0, dp(4), 0, dp(2))
    }

    val summaryText = TextView(this).apply {
        text = firstSentence(summary)
        textSize = 13f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(0, dp(2), 0, dp(4))
    }

    val readBtn = TextView(this).apply {
        text = "Read \u25B6"
        textSize = 12f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(null, Typeface.BOLD)
        setPadding(dp(14), dp(6), dp(14), dp(6))
        val pillBg = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.parseColor(SALEM_GOLD))
        }
        background = pillBg
    }

    val footerRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        setPadding(0, dp(4), 0, 0)
        addView(readBtn)
    }

    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = cardBg
        setPadding(dp(18), dp(16), dp(18), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        isClickable = true
        isFocusable = true
        addView(labelText)
        addView(dateText)
        addView(headlineText)
        addView(summaryText)
        addView(footerRow)
        setOnClickListener { onClick() }
    }
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

    // Body — render paragraphs with entity cross-links
    val bodyText = TextView(this).apply {
        textSize = 15f
        setTextColor(Color.parseColor(SALEM_TEXT))
        setLineSpacing(dp(4).toFloat(), 1.15f)
        setPadding(dp(24), dp(8), dp(24), dp(24))
        // Plain text initially; linked text set after index load
        text = article.body
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

    // Cross-link NPC names in the body text (S133)
    lifecycleScope.launch {
        witchTrialsViewModel.ensureLinkIndexes()
        val linked = renderLinkedText(
            text = article.body,
            bioIndex = witchTrialsViewModel.bioIndex,
            nameIndex = witchTrialsViewModel.nameIndex,
            excludeNpcId = null,
            onEntityTap = { link -> handleEntityLink(link, dialog) }
        )
        bodyText.text = linked
        bodyText.movementMethod = LinkMovementMethod.getInstance()
        // Disable highlight on tap — ClickableSpan handles color
        bodyText.highlightColor = Color.TRANSPARENT
    }
}

/**
 * Navigate to the appropriate detail dialog for a tapped entity link.
 * Optionally dismisses [parentDialog] before navigating.
 */
private fun SalemMainActivity.handleEntityLink(link: EntityLink, parentDialog: Dialog? = null) {
    when (link) {
        is EntityLink.Npc -> {
            val role = roleTypeOf(link.bio)
            DebugLogger.i("WitchTrials", "entity link tap: npc=${link.id}")
            showWitchTrialsBioDetailDialog(link.bio, role)
        }
        is EntityLink.NewspaperDate -> {
            DebugLogger.i("WitchTrials", "entity link tap: newspaper=${link.date}")
            lifecycleScope.launch {
                val paper = witchTrialsViewModel.getNewspaperByDate(link.date)
                if (paper != null) {
                    showWitchTrialsNewspaperDetailDialog(paper)
                }
            }
        }
    }
}

// ── Phase 9X.4 — Newspaper browser dialog (S130) ──────────────────────

private val CRISIS_PHASE_LABELS = mapOf(
    -1 to "All",
    0 to "Pre-crisis",
    1 to "Ignition",
    2 to "Accusation",
    3 to "Examinations",
    4 to "Court of O&T",
    5 to "Mass trials",
    6 to "Aftermath"
)

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showWitchTrialsNewspaperBrowserDialog() {
    DebugLogger.i("WitchTrials", "showWitchTrialsNewspaperBrowserDialog")

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val titleText = TextView(this).apply {
        text = "Salem Witch Trials — The Oracle Newspaper"
        textSize = 18f
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
        setPadding(dp(20), dp(14), dp(16), dp(6))
        addView(titleText)
        addView(closeBtn)
    }

    val countText = TextView(this).apply {
        text = "202 articles — November 1691 through May 1693. Tap any to read."
        textSize = 12f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        setPadding(dp(20), 0, dp(20), dp(10))
    }

    // Filter chip row (horizontal scroll)
    val chipRowLL = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(12), dp(2), dp(12), dp(8))
    }
    val chipRow = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(chipRowLL)
    }

    val listLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(4), dp(12), dp(24))
    }
    val scroll = ScrollView(this).apply { addView(listLayout) }

    val loadingText = TextView(this).apply {
        text = "Loading 202 newspapers…"
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
        addView(countText)
        addView(chipRow)
        addView(loadingText)
    }

    dialog.setContentView(root)
    dialog.setOnDismissListener {
        tourViewModel.cancelSegmentsWithTag(TTS_TAG_NEWS)
    }
    dialog.show()

    lifecycleScope.launch {
        try {
            DebugLogger.i("WitchTrials", "newspapers: calling getAllNewspapers()")
            val all = witchTrialsViewModel.getAllNewspapers().sortedBy { it.date }
            DebugLogger.i("WitchTrials", "newspapers: loaded ${all.size}")

            root.removeView(loadingText)
            root.addView(scroll)

            val phases = (listOf(-1) + all.map { it.crisisPhase }.distinct().sorted())
            var activePhase = -1
            val chipViews = mutableMapOf<Int, TextView>()

            fun rebuildList() {
                listLayout.removeAllViews()
                val filtered = if (activePhase == -1) all else all.filter { it.crisisPhase == activePhase }
                if (filtered.isEmpty()) {
                    listLayout.addView(TextView(this@showWitchTrialsNewspaperBrowserDialog).apply {
                        text = "No articles for this phase."
                        textSize = 13f
                        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
                        setPadding(dp(24), dp(40), dp(24), dp(40))
                    })
                } else {
                    for (paper in filtered) {
                        listLayout.addView(buildNewspaperRow(paper, dp) {
                            showWitchTrialsNewspaperDetailDialog(paper)
                        })
                    }
                }
                DebugLogger.i("WitchTrials", "newspapers: rebuilt list phase=$activePhase rows=${filtered.size}")
            }

            fun applyChipStyle(view: TextView, selected: Boolean) {
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor(if (selected) SALEM_GOLD else SALEM_SURFACE))
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), Color.parseColor(SALEM_GOLD))
                }
                view.background = bg
                view.setTextColor(Color.parseColor(if (selected) SALEM_DARK else SALEM_TEXT))
            }

            for (phase in phases) {
                val label = CRISIS_PHASE_LABELS[phase] ?: "Phase $phase"
                val chip = TextView(this@showWitchTrialsNewspaperBrowserDialog).apply {
                    text = label
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    isClickable = true
                    isFocusable = true
                }
                applyChipStyle(chip, phase == activePhase)
                chip.setOnClickListener {
                    if (activePhase == phase) return@setOnClickListener
                    val prev = activePhase
                    activePhase = phase
                    chipViews[prev]?.let { applyChipStyle(it, false) }
                    applyChipStyle(chip, true)
                    rebuildList()
                }
                chipRowLL.addView(chip, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) })
                chipViews[phase] = chip
            }

            rebuildList()
        } catch (e: Exception) {
            DebugLogger.e("WitchTrials", "newspapers FAILED: ${e.message}", e)
            loadingText.text = "Failed to load: ${e.message}"
        }
    }
}

private fun SalemMainActivity.buildNewspaperRow(
    paper: WitchTrialsNewspaper,
    dp: (Int) -> Int,
    onClick: () -> Unit
): LinearLayout {
    val phaseLabel = CRISIS_PHASE_LABELS[paper.crisisPhase] ?: "Phase ${paper.crisisPhase}"

    // Line 1: "MMM D, 1692: HEADLINE IN ALL CAPS" — old-school tabloid
    val datePart = shortTabloidDate(paper.longDate, paper.date)
    val headline = paper.headline ?: fallbackHeadline(paper)
    val headlineText = TextView(this).apply {
        text = "$datePart:  $headline"
        textSize = 17f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(Typeface.SERIF, Typeface.BOLD)
        setLineSpacing(dp(2).toFloat(), 1.1f)
    }

    // Line 2: single-sentence event summary
    val summaryLine = (paper.headlineSummary
        ?: paper.summary?.takeIf { it.isNotBlank() }
        ?: paper.lede
        ?: "").let { firstSentence(it) }
    val summaryText = TextView(this).apply {
        text = summaryLine
        textSize = 14f
        setTextColor(Color.parseColor(SALEM_TEXT))
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(0, dp(6), 0, 0)
    }

    // Phase chip in a small footer row on the right
    val phaseChip = TextView(this).apply {
        text = phaseLabel
        textSize = 10f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        setPadding(dp(8), dp(2), dp(8), dp(2))
        val cbg = GradientDrawable().apply {
            setColor(Color.parseColor("#3B2A6A"))
            cornerRadius = dp(8).toFloat()
        }
        background = cbg
    }
    val footerRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        setPadding(0, dp(8), 0, 0)
        addView(phaseChip)
    }

    val bg = GradientDrawable().apply {
        setColor(Color.parseColor(SALEM_SURFACE))
        cornerRadius = dp(10).toFloat()
        setStroke(dp(1), Color.parseColor("#3A3A50"))
    }
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = bg
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(5); bottomMargin = dp(7) }
        isClickable = true
        isFocusable = true
        addView(headlineText)
        addView(summaryText)
        addView(footerRow)
        setOnClickListener { onClick() }
    }
}

/** "November 22, 1691" → "Nov 22, 1691" (fallback: raw date). */
private fun shortTabloidDate(longDate: String?, isoDate: String): String {
    if (!longDate.isNullOrBlank()) {
        // "November 22, 1691" → split on space, abbreviate the month
        val parts = longDate.split(" ")
        if (parts.size == 3) {
            val month = parts[0].take(3)  // "November" → "Nov"
            return "$month ${parts[1]} ${parts[2]}"
        }
        return longDate
    }
    return isoDate
}

/** Cheap headline derived from summary if LLM headline is missing. */
private fun fallbackHeadline(paper: WitchTrialsNewspaper): String {
    val basis = paper.summary ?: paper.lede ?: ""
    val firstDozen = firstSentence(basis).take(60).uppercase()
    return if (firstDozen.isBlank()) "SALEM DISPATCH" else firstDozen
}

/** Return everything up to and including the first sentence terminator. */
private fun firstSentence(text: String): String {
    if (text.isBlank()) return ""
    val trimmed = text.trim()
    // Look for the first ., !, or ? that is followed by whitespace or end-of-string
    val regex = Regex("""[.!?](?=\s|$)""")
    val match = regex.find(trimmed)
    return if (match != null) trimmed.substring(0, match.range.last + 1) else trimmed
}

// ── Phase 9X.4 — Newspaper detail dialog (S130→S137 WebView rewrite) ──

/** HTML-escape for safe embedding in WebView content. */
private fun htmlEscape(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

/**
 * Render text as HTML with NPC name auto-linking and explicit [[markup]].
 * Returns HTML with `<a class="npc-link" href="npc://id">Name</a>` links.
 */
private fun renderHtmlWithLinks(
    text: String,
    bioIndex: Map<String, WitchTrialsNpcBio>,
    nameIndex: List<Pair<String, WitchTrialsNpcBio>>
): String {
    data class HtmlLink(val start: Int, val end: Int, val href: String, val display: String)

    // Phase 1: resolve explicit [[npc:id]] / [[newspaper:YYYY-MM-DD]] markup
    val markupPattern = Regex("""\[\[(npc|newspaper|date|event):([^\]]+)\]\]""")
    val links = mutableListOf<HtmlLink>()
    val sb = StringBuilder()
    var lastEnd = 0

    for (match in markupPattern.findAll(text)) {
        sb.append(text, lastEnd, match.range.first)
        val kind = match.groupValues[1]
        val value = match.groupValues[2]
        val insertStart = sb.length
        when (kind) {
            "npc" -> {
                val bio = bioIndex[value]
                val displayName = bio?.let { it.displayName ?: it.name } ?: value
                sb.append(displayName)
                if (bio != null) links.add(HtmlLink(insertStart, sb.length, "npc://$value", displayName))
            }
            "newspaper", "date" -> {
                sb.append(value)
                links.add(HtmlLink(insertStart, sb.length, "newspaper://$value", value))
            }
            else -> sb.append(value)
        }
        lastEnd = match.range.last + 1
    }
    sb.append(text, lastEnd, text.length)
    val cleaned = sb.toString()

    // Phase 2: auto-detect NPC names (longest-first greedy, skip covered regions)
    val covered = links.map { it.start until it.end }.toMutableList()
    for ((name, bio) in nameIndex) {
        var searchFrom = 0
        while (searchFrom < cleaned.length) {
            val idx = cleaned.indexOf(name, searchFrom, ignoreCase = false)
            if (idx < 0) break
            val end = idx + name.length
            val validStart = idx == 0 || !cleaned[idx - 1].isLetter()
            val validEnd = end >= cleaned.length || !cleaned[end].isLetter()
            val overlapping = covered.any { r -> idx < r.last + 1 && end > r.first }
            if (validStart && validEnd && !overlapping) {
                links.add(HtmlLink(idx, end, "npc://${bio.id}", name))
                covered.add(idx until end)
            }
            searchFrom = end
        }
    }

    // Build HTML — walk through the cleaned text, escaping plain segments, inserting <a> tags
    val sorted = links.sortedBy { it.start }
    val html = StringBuilder()
    var pos = 0
    for (link in sorted) {
        html.append(htmlEscape(cleaned.substring(pos, link.start)))
        html.append("<a class=\"npc-link\" href=\"${link.href}\">${htmlEscape(link.display)}</a>")
        pos = link.end
    }
    html.append(htmlEscape(cleaned.substring(pos)))
    return html.toString()
}

/**
 * Build the full "The Oracle" newspaper HTML page for a WebView.
 * Colonial newspaper aesthetic with dark Salem palette.
 */
private fun buildNewspaperHtml(
    paper: WitchTrialsNewspaper,
    bioIndex: Map<String, WitchTrialsNpcBio>,
    nameIndex: List<Pair<String, WitchTrialsNpcBio>>
): String {
    val phaseLabel = htmlEscape(CRISIS_PHASE_LABELS[paper.crisisPhase] ?: "Phase ${paper.crisisPhase}")
    val headline = htmlEscape(paper.headline ?: fallbackHeadline(paper))
    val dateline = htmlEscape(paper.longDate ?: paper.date)
    val dayOfWeek = paper.dayOfWeek?.let { htmlEscape(it) } ?: ""
    val deck = paper.headlineSummary?.takeIf { it.isNotBlank() }
        ?: paper.summary?.takeIf { it.isNotBlank() }
        ?: paper.lede?.takeIf { it.isNotBlank() }
        ?: ""

    // Body: use body_points as paragraphs (not bullets), fallback to ttsFullText
    val bodyHtml = buildString {
        val points = runCatching {
            val arr = JSONArray(paper.bodyPoints)
            (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotBlank() }
        }.getOrElse { emptyList() }

        if (points.isNotEmpty()) {
            for (pt in points) {
                append("<p>")
                append(renderHtmlWithLinks(pt, bioIndex, nameIndex))
                append("</p>\n")
            }
        } else {
            // Split ttsFullText into paragraphs on double newlines or single newlines
            val paragraphs = paper.ttsFullText
                .split(Regex("\n\n+|\n"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
            if (paragraphs.size > 1) {
                for (p in paragraphs) {
                    append("<p>")
                    append(renderHtmlWithLinks(p, bioIndex, nameIndex))
                    append("</p>\n")
                }
            } else {
                // Single block — wrap in one paragraph
                append("<p>")
                append(renderHtmlWithLinks(paper.ttsFullText.trim(), bioIndex, nameIndex))
                append("</p>\n")
            }
        }
    }

    val deckHtml = if (deck.isNotBlank()) "<div class=\"deck\">${htmlEscape(deck)}</div>" else ""

    return """<!DOCTYPE html>
<html><head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
<style>
*{box-sizing:border-box;margin:0;padding:0}
body{
  background:linear-gradient(180deg,#1A0F2E 0%,#2D1B4E 100%);
  color:#F5F0E8;
  font-family:Georgia,'Times New Roman',serif;
  padding:20px 22px 48px;
  -webkit-text-size-adjust:none;
}
.masthead{
  text-align:center;
  padding:4px 0 10px;
  border-bottom:3px double #C9A84C;
}
.masthead-title{
  font-size:38px;
  color:#C9A84C;
  letter-spacing:8px;
  font-variant:small-caps;
  font-weight:bold;
  line-height:1.1;
}
.masthead-sub{
  font-size:10px;
  color:#B8AFA0;
  letter-spacing:3px;
  text-transform:uppercase;
  margin-top:3px;
}
.info-row{
  display:flex;
  justify-content:space-between;
  align-items:center;
  font-size:11px;
  color:#B8AFA0;
  padding:8px 0;
  border-bottom:1px solid #3A3A50;
}
.phase{
  font-size:10px;
  color:#C9A84C;
  text-transform:uppercase;
  letter-spacing:2px;
  font-weight:bold;
}
.headline{
  font-size:26px;
  font-weight:bold;
  color:#F5F0E8;
  text-align:center;
  margin:16px 0 8px;
  line-height:1.2;
  letter-spacing:0.5px;
}
.deck{
  font-size:15px;
  font-style:italic;
  color:#D4C8A8;
  text-align:center;
  margin:0 0 14px;
  line-height:1.5;
}
.rule{border:none;border-top:1px solid #3A3A50;margin:12px 0 16px}
.body p{
  font-size:16px;
  line-height:1.7;
  margin:0 0 14px;
  text-align:justify;
  text-indent:1.5em;
}
.body p:first-child{text-indent:0}
.body p:first-child::first-letter{
  font-size:3.2em;
  float:left;
  line-height:0.8;
  padding:4px 8px 0 0;
  color:#C9A84C;
  font-weight:bold;
}
a.npc-link{
  color:#C9A84C;
  text-decoration:underline;
  text-decoration-style:dotted;
  text-underline-offset:2px;
}
.footer{
  margin-top:24px;
  padding-top:12px;
  border-top:1px solid #3A3A50;
  font-size:10px;
  color:#6A6270;
  text-align:center;
  line-height:1.6;
}
</style>
</head>
<body>
<div class="masthead">
  <div class="masthead-title">The Oracle</div>
  <div class="masthead-sub">Salem Village, Massachusetts</div>
</div>
<div class="info-row">
  <span>$dateline${if (dayOfWeek.isNotEmpty()) " &middot; $dayOfWeek" else ""}</span>
  <span class="phase">$phaseLabel</span>
</div>
<div class="headline">$headline</div>
$deckHtml
<hr class="rule">
<div class="body">
$bodyHtml</div>
<div class="footer">
  Events: ${paper.eventCount} &middot; Facts: ${paper.factCount} &middot; Primary Sources: ${paper.primarySourceCount}<br>
  Source: Salem Corpus
</div>
</body></html>"""
}

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showWitchTrialsNewspaperDetailDialog(paper: WitchTrialsNewspaper) {
    DebugLogger.i("WitchTrials", "showNewspaperDetail date=${paper.date} phase=${paper.crisisPhase}")

    val activity = this
    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val prefs = getSharedPreferences(MenuPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val narratorMode = prefs.getBoolean(MenuPrefs.PREF_NARRATOR_MODE_ENABLED, false)

    var speaking = false

    // Close button
    val closeBtn = TextView(this).apply {
        text = "\u2715"; textSize = 22f
        setTextColor(Color.WHITE)
        setPadding(dp(16), 0, dp(8), 0)
        setOnClickListener { dialog.dismiss() }
    }
    val topBar = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        setPadding(dp(8), dp(4), dp(8), 0)
        addView(closeBtn)
    }

    // WebView for the newspaper content
    val webView = android.webkit.WebView(this).apply {
        setBackgroundColor(Color.parseColor("#1A0F2E"))
        settings.apply {
            javaScriptEnabled = false
            builtInZoomControls = false
            displayZoomControls = false
        }
        webViewClient = object : android.webkit.WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, url: String?): Boolean {
                url ?: return false
                when {
                    url.startsWith("npc://") -> {
                        val npcId = url.removePrefix("npc://")
                        DebugLogger.i("WitchTrials", "newspaper link tap: npc=$npcId")
                        activity.lifecycleScope.launch {
                            activity.witchTrialsViewModel.ensureLinkIndexes()
                            val bio = activity.witchTrialsViewModel.bioIndex[npcId]
                            if (bio != null) {
                                val role = roleTypeOf(bio)
                                activity.showWitchTrialsBioDetailDialog(bio, role)
                            }
                        }
                        return true
                    }
                    url.startsWith("newspaper://") -> {
                        val date = url.removePrefix("newspaper://")
                        DebugLogger.i("WitchTrials", "newspaper link tap: newspaper=$date")
                        activity.lifecycleScope.launch {
                            val np = activity.witchTrialsViewModel.getNewspaperByDate(date)
                            if (np != null) activity.showWitchTrialsNewspaperDetailDialog(np)
                        }
                        return true
                    }
                    else -> return false
                }
            }
        }
    }

    // Speak button bar (native, below the WebView)
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
        tourViewModel.cancelSegmentsWithTag(TTS_TAG_NEWS)
        tourViewModel.speakSheetSection(
            tag = TTS_TAG_NEWS,
            text = paper.ttsFullText,
            label = paper.longDate ?: paper.date,
            voiceId = null
        )
        speaking = true
        speakBtn.text = "\u25A0 Stop"
    }

    fun stopSpeaking() {
        tourViewModel.cancelSegmentsWithTag(TTS_TAG_NEWS)
        speaking = false
        speakBtn.text = "\u25B6 Speak"
    }

    speakBtn.setOnClickListener { if (speaking) stopSpeaking() else startSpeaking() }

    val speakRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(16), dp(8), dp(16), dp(12))
        addView(speakBtn)
    }

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#1A0F2E"), Color.parseColor(SALEM_PURPLE))
        )
        background = bg
        addView(topBar)
        addView(webView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        addView(speakRow)
    }

    dialog.setContentView(root)
    dialog.setOnDismissListener {
        tourViewModel.cancelSegmentsWithTag(TTS_TAG_NEWS)
    }
    dialog.show()

    if (narratorMode) {
        DebugLogger.i("WitchTrials", "narrator-mode on — auto-speaking ${paper.date}")
        startSpeaking()
    }

    // Load HTML content with NPC cross-links
    lifecycleScope.launch {
        witchTrialsViewModel.ensureLinkIndexes()
        val html = buildNewspaperHtml(
            paper,
            witchTrialsViewModel.bioIndex,
            witchTrialsViewModel.nameIndex
        )
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}

// ── Phase 9X.5 — People of Salem 1692 browser (S131) ──────────────────

internal enum class RoleType(val label: String, val plural: String, val color: String) {
    JUDGE("Judge", "Judges", "#C62828"),              // scarlet
    ACCUSER("Accuser", "Accusers", "#C9A84C"),        // gold
    ACCUSED("Accused", "Accused", "#9E9E9E"),         // gray
    CLERGY("Clergy", "Clergy", "#7B3FA8"),            // purple
    OFFICIAL("Official", "Officials", "#BDBDBD"),     // silver
    OTHER("Figure", "Others", "#5C6670")
}

/** Derive a role bucket from the free-text `role` + `faction` fields. */
private fun roleTypeOf(bio: WitchTrialsNpcBio): RoleType {
    val text = (bio.role + " " + (bio.faction ?: "")).lowercase()
    return when {
        // Judges come first — "examiner" and "justice" both count
        text.contains("judge") || text.contains("examiner") ||
            text.contains("justice") || text.contains("magistrate") ->
            RoleType.JUDGE
        text.contains("minister") || text.contains("reverend") ||
            text.contains("clergy") || text.contains("preacher") ||
            bio.id.startsWith("rev_") || bio.name.startsWith("Rev.") ->
            RoleType.CLERGY
        text.contains("afflicted") || text.contains("accuser") ||
            text.contains("complaint filer") || text.contains("parish clerk") ||
            text.contains("putnam/accuser") ->
            RoleType.ACCUSER
        text.contains("accused") || text.contains("hanged") ||
            text.contains("executed") || text.contains("pressed") ||
            text.contains("prison") || text.contains("confessor") ||
            text.contains("recanter") || text.contains("victim") ->
            RoleType.ACCUSED
        text.contains("governor") || text.contains("constable") ||
            text.contains("physician") || text.contains("doctor") ||
            text.contains("deputy") || text.contains("militia") ||
            text.contains("merchant") || text.contains("intellectual") ->
            RoleType.OFFICIAL
        else -> RoleType.OTHER
    }
}

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showWitchTrialsPeopleBrowserDialog() {
    DebugLogger.i("WitchTrials", "showWitchTrialsPeopleBrowserDialog")

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val titleText = TextView(this).apply {
        text = "The People of Salem 1692"
        textSize = 18f
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
        setPadding(dp(20), dp(14), dp(16), dp(6))
        addView(titleText)
        addView(closeBtn)
    }

    val countText = TextView(this).apply {
        text = "Forty-nine principal figures. Tap any name to read the bio."
        textSize = 12f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        setPadding(dp(20), 0, dp(20), dp(10))
    }

    val chipRowLL = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(12), dp(2), dp(12), dp(8))
    }
    val chipRow = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(chipRowLL)
    }

    val listLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(4), dp(12), dp(24))
    }
    val scroll = ScrollView(this).apply { addView(listLayout) }

    val loadingText = TextView(this).apply {
        text = "Loading 49 figures…"
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
        addView(countText)
        addView(chipRow)
        addView(loadingText)
    }

    dialog.setContentView(root)
    dialog.setOnDismissListener {
        tourViewModel.cancelSegmentsWithTag(TTS_TAG_BIO)
    }
    dialog.show()

    lifecycleScope.launch {
        try {
            DebugLogger.i("WitchTrials", "people: calling getAllBios()")
            val raw = witchTrialsViewModel.getAllBios()
            DebugLogger.i("WitchTrials", "people: loaded ${raw.size}")

            val withRole = raw.map { it to roleTypeOf(it) }
                // Sort: role bucket order, then tier, then name
                .sortedWith(compareBy({ it.second.ordinal }, { it.first.tier }, { it.first.name }))

            root.removeView(loadingText)
            root.addView(scroll)

            val presentRoles = withRole.map { it.second }.distinct()
            val chipOrder: List<RoleType?> = listOf(null) + RoleType.values().filter { it in presentRoles }

            var activeRole: RoleType? = null
            val chipViews = mutableMapOf<RoleType?, TextView>()

            fun rebuildList() {
                listLayout.removeAllViews()
                val filtered = if (activeRole == null) withRole else withRole.filter { it.second == activeRole }
                if (filtered.isEmpty()) {
                    listLayout.addView(TextView(this@showWitchTrialsPeopleBrowserDialog).apply {
                        text = "No figures in this bucket."
                        textSize = 13f
                        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
                        setPadding(dp(24), dp(40), dp(24), dp(40))
                    })
                } else {
                    for ((bio, role) in filtered) {
                        listLayout.addView(buildBioRow(bio, role, dp) {
                            showWitchTrialsBioDetailDialog(bio, role)
                        })
                    }
                }
                DebugLogger.i("WitchTrials", "people: rebuilt list role=${activeRole?.label ?: "All"} rows=${filtered.size}")
            }

            fun applyChipStyle(view: TextView, selected: Boolean, role: RoleType?) {
                val accentColor = role?.color ?: SALEM_GOLD
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor(if (selected) accentColor else SALEM_SURFACE))
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), Color.parseColor(accentColor))
                }
                view.background = bg
                view.setTextColor(Color.parseColor(if (selected) SALEM_DARK else SALEM_TEXT))
            }

            for (role in chipOrder) {
                val label = role?.plural ?: "All"
                val chip = TextView(this@showWitchTrialsPeopleBrowserDialog).apply {
                    text = label
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    isClickable = true
                    isFocusable = true
                }
                applyChipStyle(chip, role == activeRole, role)
                chip.setOnClickListener {
                    if (activeRole == role) return@setOnClickListener
                    val prev = activeRole
                    activeRole = role
                    chipViews[prev]?.let { applyChipStyle(it, false, prev) }
                    applyChipStyle(chip, true, role)
                    rebuildList()
                }
                chipRowLL.addView(chip, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) })
                chipViews[role] = chip
            }

            rebuildList()
        } catch (e: Exception) {
            DebugLogger.e("WitchTrials", "people FAILED: ${e.message}", e)
            loadingText.text = "Failed to load: ${e.message}"
        }
    }
}

private fun SalemMainActivity.buildBioRow(
    bio: WitchTrialsNpcBio,
    role: RoleType,
    dp: (Int) -> Int,
    onClick: () -> Unit
): LinearLayout {
    // Line 1: NAME (bold serif gold, 17sp)
    val nameText = TextView(this).apply {
        text = bio.name
        textSize = 17f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(Typeface.SERIF, Typeface.BOLD)
    }

    // Line 2: short role descriptor, italic
    val descText = TextView(this).apply {
        text = bio.role
        textSize = 13f
        setTextColor(Color.parseColor(SALEM_TEXT))
        setTypeface(null, Typeface.ITALIC)
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(0, dp(4), 0, 0)
    }

    // Role chip (right-aligned footer)
    val roleChip = TextView(this).apply {
        text = role.label
        textSize = 10f
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        letterSpacing = 0.12f
        setPadding(dp(10), dp(3), dp(10), dp(3))
        val cbg = GradientDrawable().apply {
            setColor(Color.parseColor(role.color))
            cornerRadius = dp(8).toFloat()
        }
        background = cbg
    }
    // Dates spanned right of role chip
    val datesSpan = buildDatesSpan(bio)
    val datesText = TextView(this).apply {
        text = datesSpan
        textSize = 11f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        setPadding(dp(10), 0, 0, 0)
    }
    val footerRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setPadding(0, dp(8), 0, 0)
        addView(roleChip)
        addView(datesText)
    }

    // Circular thumbnail from portrait asset — loaded async to avoid ANR
    val thumb: ImageView? = bio.portraitAsset?.takeIf { it.isNotBlank() }?.let { assetPath ->
        ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            val size = dp(48)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(12)
            }
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            setBackgroundColor(Color.parseColor(SALEM_SURFACE))
            loadPortraitAsync(assetPath, this, targetSize = 96)
        }
    }

    val textColumn = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        addView(nameText)
        addView(descText)
        addView(footerRow)
    }

    val bg = GradientDrawable().apply {
        setColor(Color.parseColor(SALEM_SURFACE))
        cornerRadius = dp(10).toFloat()
        setStroke(dp(1), Color.parseColor(role.color))
    }
    return LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = bg
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(5); bottomMargin = dp(7) }
        isClickable = true
        isFocusable = true
        thumb?.let { addView(it) }
        addView(textColumn)
        setOnClickListener { onClick() }
    }
}

private fun buildDatesSpan(bio: WitchTrialsNpcBio): String {
    val b = bio.bornYear
    val d = bio.diedYear
    return when {
        b != null && d != null -> "$b – $d"
        b != null -> "b. $b"
        d != null -> "d. $d"
        else -> ""
    }
}

// ── Phase 9X.5 — Bio detail dialog (S131) ─────────────────────────────

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showWitchTrialsBioDetailDialog(bio: WitchTrialsNpcBio, role: RoleType) {
    DebugLogger.i("WitchTrials", "showBioDetail id=${bio.id} role=${role.label}")

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val prefs = getSharedPreferences(MenuPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    val narratorMode = prefs.getBoolean(MenuPrefs.PREF_NARRATOR_MODE_ENABLED, false)

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

    // Portrait hero image — loaded async from bundled assets
    val portraitView: ImageView? = bio.portraitAsset?.takeIf { it.isNotBlank() }?.let { assetPath ->
        ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            val size = dp(160)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
                }
            }
            val borderBg = GradientDrawable().apply {
                setColor(Color.parseColor(SALEM_SURFACE))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(2), Color.parseColor(role.color))
            }
            background = borderBg
            loadPortraitAsync(assetPath, this)
        }
    }

    val roleEyebrow = TextView(this).apply {
        text = role.label.uppercase()
        textSize = 11f
        setTextColor(Color.parseColor(role.color))
        setTypeface(null, Typeface.BOLD)
        letterSpacing = 0.15f
        setPadding(dp(24), dp(4), dp(24), dp(2))
    }

    val nameText = TextView(this).apply {
        text = bio.name
        textSize = 24f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(Typeface.SERIF, Typeface.BOLD)
        setPadding(dp(24), dp(2), dp(24), dp(4))
    }

    val datesText = TextView(this).apply {
        val span = buildDatesSpan(bio)
        val age = bio.ageIn1692?.takeIf { it > 0 }?.let { " · age $it in 1692" } ?: ""
        text = if (span.isBlank() && age.isBlank()) "" else "$span$age"
        textSize = 13f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        setTypeface(null, Typeface.ITALIC)
        setPadding(dp(24), 0, dp(24), dp(10))
    }

    val shortBioText = TextView(this).apply {
        text = bio.role
        textSize = 15f
        setTextColor(Color.parseColor(SALEM_TEXT))
        setTypeface(null, Typeface.ITALIC)
        setLineSpacing(dp(3).toFloat(), 1.15f)
        setPadding(dp(24), 0, dp(24), dp(14))
    }

    val outcomeText = bio.historicalOutcome?.takeIf { it.isNotBlank() }?.let { outcome ->
        TextView(this).apply {
            text = "Outcome: $outcome"
            textSize = 13f
            setTextColor(Color.parseColor(SALEM_TEXT_DIM))
            setLineSpacing(dp(2).toFloat(), 1.15f)
            setPadding(dp(24), 0, dp(24), dp(14))
        }
    }

    // Long-bio body — render `## Header\n\ntext` sections with gold subheadings
    val bodyContainer = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(6), dp(24), dp(28))
    }
    // Render plain initially; cross-linked text applied after index load
    renderBioBody(bio.bio, bodyContainer, dp, excludeNpcId = null, onEntityTap = null)

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

    val ttsChunks = chunkForTts(ttsTextForBio(bio))

    fun startSpeaking() {
        tourViewModel.cancelSegmentsWithTag(TTS_TAG_BIO)
        DebugLogger.i("WitchTrials", "bio speak: ${ttsChunks.size} chunks, lengths=${ttsChunks.map { it.length }}")
        for ((i, chunk) in ttsChunks.withIndex()) {
            tourViewModel.speakSheetSection(
                tag = TTS_TAG_BIO,
                text = chunk,
                label = if (ttsChunks.size == 1) bio.name else "${bio.name} (${i + 1}/${ttsChunks.size})",
                voiceId = null
            )
        }
        speaking = true
        speakBtn.text = "\u25A0 Stop"
    }

    fun stopSpeaking() {
        tourViewModel.cancelSegmentsWithTag(TTS_TAG_BIO)
        speaking = false
        speakBtn.text = "\u25B6 Speak"
    }

    speakBtn.setOnClickListener { if (speaking) stopSpeaking() else startSpeaking() }

    val speakRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(16), dp(4), dp(16), dp(16))
        addView(speakBtn)
    }

    val contentColumn = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(topBar)
        portraitView?.let { addView(it) }
        addView(roleEyebrow)
        addView(nameText)
        addView(datesText)
        addView(shortBioText)
        outcomeText?.let { addView(it) }
        addView(speakRow)
        addView(bodyContainer)
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
        tourViewModel.cancelSegmentsWithTag(TTS_TAG_BIO)
    }
    dialog.show()

    if (narratorMode) {
        DebugLogger.i("WitchTrials", "narrator-mode on — auto-speaking ${bio.id}")
        startSpeaking()
    }

    // Cross-link NPC names in the bio body (S133)
    lifecycleScope.launch {
        witchTrialsViewModel.ensureLinkIndexes()
        bodyContainer.removeAllViews()
        renderBioBody(
            bio = bio.bio,
            container = bodyContainer,
            dp = dp,
            excludeNpcId = bio.id,
            onEntityTap = { link -> handleEntityLink(link) }
        )
    }
}

/** Render the `## Header\n\nparagraph\n\nparagraph` bio format into TextViews.
 *  When [onEntityTap] is non-null, paragraph text gets entity-linked spans. */
private fun SalemMainActivity.renderBioBody(
    bio: String,
    container: LinearLayout,
    dp: (Int) -> Int,
    excludeNpcId: String?,
    onEntityTap: ((EntityLink) -> Unit)?
) {
    val blocks = bio.split(Regex("\n\n+"))
    for (block in blocks) {
        val t = block.trim()
        if (t.isEmpty()) continue
        if (t.startsWith("## ")) {
            val header = t.removePrefix("## ").trim()
            container.addView(TextView(this).apply {
                text = header
                textSize = 13f
                setTextColor(Color.parseColor(SALEM_GOLD))
                setTypeface(null, Typeface.BOLD)
                letterSpacing = 0.1f
                setPadding(0, dp(14), 0, dp(4))
            })
        } else {
            container.addView(TextView(this).apply {
                textSize = 15f
                setTextColor(Color.parseColor(SALEM_TEXT))
                setLineSpacing(dp(4).toFloat(), 1.2f)
                setPadding(0, dp(2), 0, dp(8))
                if (onEntityTap != null && witchTrialsViewModel.linkIndexesReady) {
                    val linked = renderLinkedText(
                        text = t,
                        bioIndex = witchTrialsViewModel.bioIndex,
                        nameIndex = witchTrialsViewModel.nameIndex,
                        excludeNpcId = excludeNpcId,
                        onEntityTap = onEntityTap
                    )
                    text = linked
                    movementMethod = LinkMovementMethod.getInstance()
                    highlightColor = Color.TRANSPARENT
                } else {
                    text = t
                }
            })
        }
    }
}

/** Strip markdown headers so TTS reads a clean narrative. */
private fun ttsTextForBio(bio: WitchTrialsNpcBio): String {
    val sb = StringBuilder()
    sb.append(bio.name).append(". ").append(bio.role).append(".\n\n")
    val cleaned = bio.bio.lineSequence().filterNot { it.startsWith("## ") }.joinToString("\n")
    sb.append(cleaned)
    return sb.toString()
}

/**
 * Android TTS caps each utterance at ~4000 chars (getMaxSpeechInputLength()).
 * Split long text at sentence boundaries so multi-thousand-char bios speak
 * in sequence instead of erroring out. NarrationManager enqueues each chunk
 * as a separate segment with a unique id but matching tag, so the Stop pill
 * still cancels every chunk via cancelSegmentsWithTag prefix match.
 */
private const val TTS_CHUNK_MAX = 3500

private fun chunkForTts(text: String): List<String> {
    if (text.length <= TTS_CHUNK_MAX) return listOf(text)
    // Split into paragraphs first, then sentences. Each chunk accumulates
    // paragraphs/sentences until it would exceed TTS_CHUNK_MAX.
    val sentencePattern = Regex("""[^.!?]+[.!?]+(?:\s+|$)""")
    val pieces: List<String> = text.split(Regex("\n\n+"))
        .flatMap { para ->
            if (para.length <= TTS_CHUNK_MAX) listOf(para)
            else sentencePattern.findAll(para).map { it.value }.toList().ifEmpty { listOf(para) }
        }

    val chunks = mutableListOf<String>()
    val current = StringBuilder()
    for (piece in pieces) {
        if (piece.length > TTS_CHUNK_MAX) {
            // Flush current, then hard-split the oversized piece.
            if (current.isNotEmpty()) {
                chunks.add(current.toString().trim()); current.setLength(0)
            }
            var remaining = piece
            while (remaining.length > TTS_CHUNK_MAX) {
                chunks.add(remaining.substring(0, TTS_CHUNK_MAX))
                remaining = remaining.substring(TTS_CHUNK_MAX)
            }
            if (remaining.isNotBlank()) current.append(remaining)
        } else if (current.length + piece.length + 2 > TTS_CHUNK_MAX && current.isNotEmpty()) {
            chunks.add(current.toString().trim())
            current.setLength(0)
            current.append(piece)
        } else {
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(piece)
        }
    }
    if (current.isNotBlank()) chunks.add(current.toString().trim())
    return chunks
}

// ═════════════════════════════════════════════════════════════════════════════
// Historic Sites of Salem — S134 browser + detail
// ═════════════════════════════════════════════════════════════════════════════

private const val TTS_TAG_SITE = "historic_site"

/** Era filter labels and year ranges for the Historic Sites browser. */
private data class EraFilter(val label: String, val minYear: Int, val maxYear: Int)

private val ERA_FILTERS = listOf(
    EraFilter("All", 0, 9999),
    EraFilter("1692 & Before", 0, 1692),
    EraFilter("Colonial 1693–1776", 1693, 1776),
    EraFilter("Federal 1777–1850", 1777, 1850),
)

@SuppressLint("SetTextI18n")
internal fun SalemMainActivity.showHistoricSitesBrowserDialog() {
    DebugLogger.i("WitchTrials", "showHistoricSitesBrowserDialog")

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    val titleText = TextView(this).apply {
        text = "Historic Sites of Salem"
        textSize = 18f
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
        setPadding(dp(20), dp(14), dp(16), dp(6))
        addView(titleText)
        addView(closeBtn)
    }

    val countText = TextView(this).apply {
        text = "Loading historic sites…"
        textSize = 12f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        setPadding(dp(20), 0, dp(20), dp(10))
    }

    // Era filter chip row
    val chipRowLL = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(dp(12), dp(2), dp(12), dp(8))
    }
    val chipRow = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(chipRowLL)
    }

    val listLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(4), dp(12), dp(24))
    }
    val scroll = ScrollView(this).apply { addView(listLayout) }

    val loadingText = TextView(this).apply {
        text = "Loading historic sites…"
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
        addView(countText)
        addView(chipRow)
        addView(loadingText)
    }

    dialog.setContentView(root)
    dialog.setOnDismissListener {
        tourViewModel.cancelSegmentsWithTag(TTS_TAG_SITE)
    }
    dialog.show()

    lifecycleScope.launch {
        try {
            val all = witchTrialsViewModel.getHistoricSites()
                .sortedWith(compareBy<SalemPoi> { it.yearEstablished ?: Int.MAX_VALUE }.thenBy { it.name })

            root.removeView(loadingText)
            root.addView(scroll)

            var activeIdx = 0   // 0 = "All"
            val chipViews = mutableMapOf<Int, TextView>()

            fun matchesEra(poi: SalemPoi, era: EraFilter): Boolean {
                if (era.minYear == 0 && era.maxYear == 9999) return true
                val yr = poi.yearEstablished ?: return false
                return yr in era.minYear..era.maxYear
            }

            fun rebuildList() {
                listLayout.removeAllViews()
                val era = ERA_FILTERS[activeIdx]
                val filtered = if (activeIdx == 0) all else all.filter { matchesEra(it, era) }

                countText.text = if (activeIdx == 0) {
                    "${all.size} historic sites across four centuries. Tap any to read."
                } else {
                    "${filtered.size} sites — ${era.label}"
                }

                if (filtered.isEmpty()) {
                    listLayout.addView(TextView(this@showHistoricSitesBrowserDialog).apply {
                        text = "No sites for this era."
                        textSize = 13f
                        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
                        setPadding(dp(24), dp(40), dp(24), dp(40))
                    })
                } else {
                    for (poi in filtered) {
                        listLayout.addView(buildHistoricSiteRow(poi, dp) {
                            showHistoricSiteDetailDialog(poi)
                        })
                    }
                }
            }

            fun applyChipStyle(view: TextView, selected: Boolean) {
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor(if (selected) SALEM_GOLD else SALEM_SURFACE))
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), Color.parseColor(SALEM_GOLD))
                }
                view.background = bg
                view.setTextColor(Color.parseColor(if (selected) SALEM_DARK else SALEM_TEXT))
            }

            for ((idx, era) in ERA_FILTERS.withIndex()) {
                val chip = TextView(this@showHistoricSitesBrowserDialog).apply {
                    text = era.label
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    isClickable = true
                    isFocusable = true
                }
                applyChipStyle(chip, idx == activeIdx)
                chip.setOnClickListener {
                    if (activeIdx == idx) return@setOnClickListener
                    val prev = activeIdx
                    activeIdx = idx
                    chipViews[prev]?.let { applyChipStyle(it, false) }
                    applyChipStyle(chip, true)
                    rebuildList()
                }
                chipRowLL.addView(chip, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) })
                chipViews[idx] = chip
            }

            rebuildList()
        } catch (e: Exception) {
            DebugLogger.e("WitchTrials", "historic sites FAILED: ${e.message}", e)
            loadingText.text = "Failed to load: ${e.message}"
        }
    }
}

/** Build a single row for the historic sites list. */
private fun SalemMainActivity.buildHistoricSiteRow(
    poi: SalemPoi,
    dp: (Int) -> Int,
    onClick: () -> Unit
): LinearLayout {
    // Line 1: Name (gold, serif)
    val nameText = TextView(this).apply {
        text = poi.name
        textSize = 16f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(Typeface.SERIF, Typeface.BOLD)
    }

    // Line 2: Year + address
    val yearStr = poi.yearEstablished?.toString() ?: ""
    val addrStr = poi.address?.takeIf { it.isNotBlank() } ?: ""
    val metaLine = listOf(yearStr, addrStr).filter { it.isNotBlank() }.joinToString(" — ")
    val metaText = TextView(this).apply {
        text = metaLine
        textSize = 12f
        setTextColor(Color.parseColor(SALEM_TEXT_DIM))
        setPadding(0, dp(2), 0, 0)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    // Line 3: Historical note preview
    val preview = (poi.historicalNote ?: poi.description ?: poi.shortDescription ?: "")
        .take(120).let { if (it.length >= 120) "$it…" else it }
    val previewText = TextView(this).apply {
        text = preview
        textSize = 13f
        setTextColor(Color.parseColor(SALEM_TEXT))
        setPadding(0, dp(4), 0, 0)
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    // Era badge
    val eraBadge = poi.yearEstablished?.let { yr ->
        val eraLabel = when {
            yr <= 1692 -> "1692"
            yr <= 1776 -> "Colonial"
            yr <= 1850 -> "Federal"
            else -> "${yr}s"
        }
        TextView(this).apply {
            text = eraLabel
            textSize = 10f
            setTextColor(Color.parseColor(SALEM_TEXT_DIM))
            setPadding(dp(8), dp(2), dp(8), dp(2))
            val cbg = GradientDrawable().apply {
                setColor(Color.parseColor("#3B2A6A"))
                cornerRadius = dp(8).toFloat()
            }
            background = cbg
        }
    }

    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(nameText)
        if (metaLine.isNotBlank()) addView(metaText)
        if (preview.isNotBlank()) addView(previewText)
        eraBadge?.let {
            addView(LinearLayout(this@buildHistoricSiteRow).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, dp(6), 0, 0)
                addView(it)
            })
        }
    }

    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val rowBg = GradientDrawable().apply {
            setColor(Color.parseColor(SALEM_SURFACE))
            cornerRadius = dp(10).toFloat()
        }
        background = rowBg
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }
        addView(col)
        setOnClickListener { onClick() }
    }
}

/** Full-screen detail view for a single historic site. */
@SuppressLint("SetTextI18n")
private fun SalemMainActivity.showHistoricSiteDetailDialog(poi: SalemPoi) {
    DebugLogger.i("WitchTrials", "showHistoricSiteDetailDialog: ${poi.id}")

    val density = resources.displayMetrics.density
    val dp = { v: Int -> (v * density).toInt() }

    val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

    // ── Header ──
    val titleText = TextView(this).apply {
        text = poi.name
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
        setPadding(dp(20), dp(14), dp(16), dp(6))
        addView(titleText)
        addView(closeBtn)
    }

    // ── Year + era eyebrow ──
    val yearStr = poi.yearEstablished?.let { yr ->
        val eraLabel = when {
            yr <= 1692 -> "Salem 1692 & Before"
            yr <= 1776 -> "Colonial Era (1693–1776)"
            yr <= 1850 -> "Federal & Maritime (1777–1850)"
            else -> "Modern"
        }
        "Est. $yr — $eraLabel"
    }
    val eyebrowText = yearStr?.let {
        TextView(this).apply {
            text = it
            textSize = 13f
            setTextColor(Color.parseColor(SALEM_TEXT_DIM))
            setPadding(dp(20), dp(2), dp(20), dp(4))
        }
    }

    // ── Address ──
    val addressText = poi.address?.takeIf { it.isNotBlank() }?.let {
        TextView(this).apply {
            text = "\uD83D\uDCCD $it"  // 📍
            textSize = 13f
            setTextColor(Color.parseColor(SALEM_TEXT_DIM))
            setPadding(dp(20), dp(2), dp(20), dp(12))
        }
    }

    // ── Body (historical note, with cross-linking) ──
    val bodyContent = poi.historicalNote
        ?: poi.description
        ?: poi.shortDescription
        ?: "No historical information available for this site."

    val bodyText = TextView(this).apply {
        text = bodyContent
        textSize = 16f
        setTextColor(Color.parseColor(SALEM_TEXT))
        setPadding(dp(20), dp(8), dp(20), dp(20))
        setLineSpacing(dp(3).toFloat(), 1.2f)
        movementMethod = LinkMovementMethod.getInstance()
    }

    // Apply cross-linking if indexes are ready (S133 EntityLinkRenderer)
    lifecycleScope.launch {
        witchTrialsViewModel.ensureLinkIndexes()
        if (witchTrialsViewModel.linkIndexesReady) {
            val linked = renderLinkedText(
                text = bodyContent,
                bioIndex = witchTrialsViewModel.bioIndex,
                nameIndex = witchTrialsViewModel.nameIndex,
                excludeNpcId = null,
                onEntityTap = { link -> handleEntityLink(link, dialog) }
            )
            bodyText.text = linked
            bodyText.highlightColor = Color.TRANSPARENT
        }
    }

    // ── TTS button ──
    // ── TTS speak/stop toggle ──
    val ttsChunks = chunkForTts(bodyContent)
    var speaking = false
    val speakBtn = TextView(this).apply {
        text = "\u25B6 Speak"
        textSize = 14f
        setTextColor(Color.parseColor(SALEM_GOLD))
        setTypeface(null, Typeface.BOLD)
        setPadding(dp(20), dp(12), dp(20), dp(12))
        isClickable = true
        isFocusable = true
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor(SALEM_SURFACE))
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), Color.parseColor(SALEM_GOLD))
        }
        background = bg
    }
    fun startSpeaking() {
        tourViewModel.cancelSegmentsWithTag(TTS_TAG_SITE)
        for ((i, chunk) in ttsChunks.withIndex()) {
            tourViewModel.speakSheetSection(
                tag = TTS_TAG_SITE,
                text = chunk,
                label = if (ttsChunks.size == 1) poi.name else "${poi.name} (${i + 1}/${ttsChunks.size})",
                voiceId = null
            )
        }
        speaking = true
        speakBtn.text = "\u25A0 Stop"
    }
    fun stopSpeaking() {
        tourViewModel.cancelSegmentsWithTag(TTS_TAG_SITE)
        speaking = false
        speakBtn.text = "\u25B6 Speak"
    }
    speakBtn.setOnClickListener { if (speaking) stopSpeaking() else startSpeaking() }
    val ttsRow = LinearLayout(this).apply {
        setPadding(dp(20), dp(8), dp(20), dp(20))
        addView(speakBtn)
    }

    // ── Scroll body ──
    val bodyLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        eyebrowText?.let { addView(it) }
        addressText?.let { addView(it) }
        addView(bodyText)
        addView(ttsRow)
    }
    val scroll = ScrollView(this).apply { addView(bodyLayout) }

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#1A0F2E"), Color.parseColor(SALEM_PURPLE))
        )
        background = bg
        addView(headerRow)
        addView(scroll)
    }

    dialog.setContentView(root)
    dialog.setOnDismissListener {
        tourViewModel.cancelSegmentsWithTag(TTS_TAG_SITE)
    }
    dialog.show()
}

