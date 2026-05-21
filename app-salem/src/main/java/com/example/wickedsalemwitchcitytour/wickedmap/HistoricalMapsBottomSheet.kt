/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.example.wickedsalemwitchcitytour.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Historical-map year + opacity picker (S286 Phase 2).
 *
 * Per-row card model: a tap toggles between "None" and one of the available
 * Historical-YYYY providers. Opacity slider runs 0..100%, mapped to paint
 * alpha 0..255 on the consuming overlay. Selections persist in
 * SharedPreferences("historical_maps") and are restored when the host
 * activity re-creates the sheet.
 *
 * Phase 2 ships with 1851 tiles only — the 1874/1906/1911 cards are visible
 * but marked unavailable so the operator can confirm the picker UX before
 * Phase 3 bakes the remaining maps.
 */
class HistoricalMapsBottomSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onHistoricalYearChanged(year: String?)   // null = None
        fun onHistoricalOpacityChanged(percent: Int)
    }

    private var listener: Listener? = null

    fun setListener(l: Listener) { listener = l }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.sheet_historical_maps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val currentYear = prefs.getString(KEY_YEAR, null)
        val currentOpacity = prefs.getInt(KEY_OPACITY, 70).coerceIn(0, 100)

        val row = view.findViewById<LinearLayout>(R.id.histYearRow)
        val slider = view.findViewById<SeekBar>(R.id.histOpacitySlider)
        val opacityLabel = view.findViewById<TextView>(R.id.histOpacityLabel)
        val closeBtn = view.findViewById<Button>(R.id.histCloseBtn)

        val cards = mutableListOf<View>()
        YEARS.forEach { (label, year, available) ->
            val card = buildCard(label, year, available, year == currentYear)
            card.setOnClickListener {
                if (!available && year != null) return@setOnClickListener
                cards.forEach { c -> highlightCard(c, c === card) }
                prefs.edit().putString(KEY_YEAR, year).apply()
                listener?.onHistoricalYearChanged(year)
            }
            row.addView(card)
            cards.add(card)
        }

        slider.progress = currentOpacity
        opacityLabel.text = "${currentOpacity}%"
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val pct = progress.coerceIn(0, 100)
                opacityLabel.text = "${pct}%"
                listener?.onHistoricalOpacityChanged(pct)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                prefs.edit().putInt(KEY_OPACITY, sb?.progress ?: 70).apply()
            }
        })

        closeBtn.setOnClickListener { dismiss() }
    }

    private fun buildCard(label: String, year: String?, available: Boolean, selected: Boolean): View {
        val ctx = requireContext()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val lp = LinearLayout.LayoutParams(dp(96), dp(72))
            lp.marginEnd = dp(8)
            layoutParams = lp
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = cardBackground(selected, available)
            isClickable = available || year == null
            isFocusable = isClickable
        }
        val labelView = TextView(ctx).apply {
            text = label
            setTextColor(if (available || year == null) Color.WHITE else 0xFF888888.toInt())
            textSize = 14f
            gravity = android.view.Gravity.CENTER
        }
        card.addView(labelView)
        if (!available && year != null) {
            val tag = TextView(ctx).apply {
                text = "soon"
                setTextColor(0xFFAAAAAA.toInt())
                textSize = 11f
                gravity = android.view.Gravity.CENTER
            }
            card.addView(tag)
        }
        return card
    }

    private fun highlightCard(card: View, selected: Boolean) {
        val available = card.isClickable
        card.background = cardBackground(selected, available)
    }

    private fun cardBackground(selected: Boolean, available: Boolean): GradientDrawable {
        val bg = GradientDrawable()
        bg.cornerRadius = dp(8).toFloat()
        bg.setStroke(dp(2), if (selected) 0xFFFFC107.toInt() else 0x66FFFFFF.toInt())
        bg.setColor(if (!available) 0x33000000 else if (selected) 0x55FFC107.toInt() else 0x44000000)
        return bg
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    companion object {
        const val TAG = "HistoricalMapsSheet"

        const val PREFS = "historical_maps"
        const val KEY_YEAR = "year"           // null = None / disabled
        const val KEY_OPACITY = "opacity_pct" // 0..100

        // (label, providerYearOrNull, available)
        // S286 Phase 3: all 4 historical years baked (coarse 4-corner georef).
        private val YEARS = listOf(
            Triple("None", null, true),
            Triple("1851 McIntyre", "1851", true),
            Triple("1874 Hopkins", "1874", true),
            Triple("1906 Sanborn", "1906", true),
            Triple("1911 Walker", "1911", true),
        )

        fun readYear(ctx: Context): String? =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_YEAR, "1851")

        fun readOpacityPercent(ctx: Context): Int =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_OPACITY, 70)
    }
}
