/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.wickedsalemwitchcitytour.R
import java.io.IOException

/**
 * Historical-map overlay picker — full-screen dialog (S287 redesign).
 *
 * Tile-tap commits, no "Use this" button (per
 * feedback_no_confirm_button_when_tap_is_unambiguous.md). Vertical list of
 * 5 full-thumbnail cards (None + 4 historical years). Tap a card → emit
 * year to listener → dismiss.
 *
 * Opacity slider lives on the WickedMap canvas itself; not part of the
 * picker. Class name retained for git-history continuity (was a
 * BottomSheetDialogFragment in S286/early S287).
 */
class HistoricalMapsBottomSheet : DialogFragment() {

    interface Listener {
        fun onHistoricalYearChanged(year: String?)   // null = None
        fun onHistoricalOpacityChanged(percent: Int)
    }

    private var listener: Listener? = null

    fun setListener(l: Listener) { listener = l }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dlg = super.onCreateDialog(savedInstanceState)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        return dlg
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.sheet_historical_maps, container, false)
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentYear = prefs.getString(KEY_YEAR, null)

        val column = view.findViewById<LinearLayout>(R.id.histTileColumn)
        val closeBtn = view.findViewById<Button>(R.id.histCloseBtn)

        TILES.forEach { tile ->
            val card = buildTile(tile, selected = tile.year == currentYear)
            card.setOnClickListener {
                if (com.example.wickedsalemwitchcitytour.BuildConfig.DEBUG) {
                    com.example.locationmapapp.util.DebugLogger.d(
                        "HistoricalPicker",
                        "tile tap → year=${tile.year} title='${tile.title}' (was=$currentYear)",
                    )
                }
                prefs.edit().putString(KEY_YEAR, tile.year).apply()
                listener?.onHistoricalYearChanged(tile.year)
                dismiss()
            }
            column.addView(card)
        }

        closeBtn.setOnClickListener { dismiss() }
    }

    private fun buildTile(tile: TileSpec, selected: Boolean): View {
        val ctx = requireContext()

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp(8)
            layoutParams = lp
            background = tileBackground(selected)
            setPadding(dp(10), dp(10), dp(14), dp(10))
            isClickable = true
            isFocusable = true
        }

        // Thumbnail
        val thumb = ImageView(ctx).apply {
            val lp = LinearLayout.LayoutParams(dp(140), dp(96))
            lp.marginEnd = dp(14)
            layoutParams = lp
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = thumbBackground()
            clipToOutline = true
            if (tile.assetName != null) {
                try {
                    ctx.assets.open("historical-thumbs/${tile.assetName}").use { ins ->
                        setImageBitmap(android.graphics.BitmapFactory.decodeStream(ins))
                    }
                } catch (e: IOException) {
                    setImageResource(android.R.color.darker_gray)
                }
            } else {
                setImageResource(android.R.color.transparent)
            }
        }
        row.addView(thumb)

        // Text column
        val textCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            lp.gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = lp
        }

        textCol.addView(TextView(ctx).apply {
            text = tile.title
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        textCol.addView(TextView(ctx).apply {
            text = tile.source
            setTextColor(0xFFC9B87A.toInt())
            textSize = 12f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp(3)
            layoutParams = lp
        })

        textCol.addView(TextView(ctx).apply {
            text = tile.description
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 12f
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            lp.topMargin = dp(4)
            layoutParams = lp
        })

        row.addView(textCol)
        return row
    }

    private fun tileBackground(selected: Boolean): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setStroke(dp(2), if (selected) 0xFFFFC107.toInt() else 0x33FFFFFF.toInt())
            setColor(if (selected) 0x33FFC107.toInt() else 0x33000000)
        }

    private fun thumbBackground(): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(6).toFloat()
            setColor(0xFF1a1812.toInt())
        }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    data class TileSpec(
        val title: String,
        val source: String,
        val description: String,
        val year: String?,
        val assetName: String?,
    )

    companion object {
        const val TAG = "HistoricalMapsSheet"

        const val PREFS = "historical_maps"
        const val KEY_YEAR = "year"           // null = None / disabled
        const val KEY_OPACITY = "opacity_pct" // 0..100

        private val TILES = listOf(
            TileSpec(
                title = "None — modern map only",
                source = "WickedMap basemap",
                description = "Hide the historical overlay; show modern Salem streets, buildings, and shoreline only.",
                year = null,
                assetName = "thumb_modern.webp",
            ),
            // S306 — 1692 Salem Village + 1911 Walker Atlas pulled to slim the
            // bundle (operator: keep 1700 + 1851). Their tiles are removed from
            // salem_tiles.sqlite (providers Historical-1692/1911) and stored at
            // tools/tile-bake/dist/historical-removed-1692-1911-20260529.sqlite
            // (+ full pre-pull backup salem_tiles.sqlite.preS306-all4historical-
            // 20260529.bak). Re-add = INSERT those provider rows back + restore
            // the TileSpec entry; no re-georef needed.
            TileSpec(
                title = "1700 — Part of Salem",
                source = "Phillips / Perley · U. of Virginia",
                description = "Reconstructed downtown Salem c. 1700. North & South Rivers, Mill Pond, Castle Hill Neck.",
                year = "1700",
                assetName = "thumb_1700.webp",
            ),
            TileSpec(
                title = "1851 — McIntyre City Map",
                source = "Henry McIntyre · BPL Leventhal",
                description = "High-detail mid-19th-century city map. 8-GCP precision georef — sits cleanly on modern streets.",
                year = "1851",
                assetName = "thumb_1851.webp",
            ),
            // S306 — 1911 Walker Atlas pulled with 1692 (see note above).
        )

        fun readYear(ctx: Context): String? =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_YEAR, "1851")

        fun readOpacityPercent(ctx: Context): Int =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_OPACITY, 70)
    }
}
