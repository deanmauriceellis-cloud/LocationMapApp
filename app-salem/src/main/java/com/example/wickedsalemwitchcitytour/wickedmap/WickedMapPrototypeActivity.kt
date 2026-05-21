/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Prototype launcher — exercises WickedMapView + AnimatedWaterOverlay with
 * the salem_tiles.sqlite archive extracted to external files dir by
 * OfflineTileManager on app start. Self-contained: no osmdroid, no Hilt DI,
 * no shared state with SalemMainActivity.
 *
 * S286 Phase 2: hosts the historical-map FAB picker. Pick a year (1851 /
 * 1874 / 1906 / 1911 / None) + opacity; selections persist in
 * SharedPreferences("historical_maps") and survive activity recreate.
 */
class WickedMapPrototypeActivity : AppCompatActivity(), HistoricalMapsBottomSheet.Listener {

    private var archive: TileArchive? = null
    private var archiveFile: File? = null
    private var historicalArchive: TileArchive? = null
    private val historicalOverlay = HistoricalTileOverlay()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        val mapView = WickedMapView(this)
        root.addView(
            mapView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        )

        // HUD bar (label + Close button) so we know we're in the prototype
        // and can get back out. S286 P1 added the Close — Lenovo gesture-nav
        // doesn't surface a back affordance here.
        val hud = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0x88000000.toInt())
            setPadding(24, 16, 16, 16)
            gravity = Gravity.CENTER_VERTICAL
        }
        val label = TextView(this).apply {
            text = "WickedMap prototype — historical-map picker (S286 P2)"
            setTextColor(0xFFFFFFFF.toInt())
        }
        val closeBtn = Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        }
        hud.addView(
            label,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        hud.addView(
            closeBtn,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        )
        root.addView(
            hud,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )
        )

        // Picker FAB — bottom-right, small. Drawn programmatically so the
        // prototype stays single-file. Uses a tinted background drawable +
        // an emoji glyph so we don't need a new vector asset for Phase 2.
        val pickerFab = TextView(this).apply {
            text = "🗺"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF1B5E20.toInt())
                setStroke(dp(2), 0xFFFFC107.toInt())
            }
            elevation = dp(6).toFloat()
            setOnClickListener {
                HistoricalMapsBottomSheet().also { sheet ->
                    sheet.setListener(this@WickedMapPrototypeActivity)
                    sheet.show(supportFragmentManager, HistoricalMapsBottomSheet.TAG)
                }
            }
        }
        val pickerLp = FrameLayout.LayoutParams(dp(56), dp(56)).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            marginEnd = dp(16)
            bottomMargin = dp(24)
        }
        root.addView(pickerFab, pickerLp)

        setContentView(root)

        val af = File(getExternalFilesDir(null), "salem_tiles.sqlite")
        if (!af.exists()) {
            // Fallback: extract from assets if OfflineTileManager hasn't run yet.
            assets.open("salem_tiles.sqlite").use { input ->
                af.outputStream().use { out -> input.copyTo(out) }
            }
        }
        archiveFile = af

        archive = TileArchive(af).also { mapView.attachArchive(it) }

        // Restore last picker selection. Default year=1851, opacity=70 so a
        // fresh install still shows something interesting.
        val startYear = HistoricalMapsBottomSheet.readYear(this)
        val startOpacityPct = HistoricalMapsBottomSheet.readOpacityPercent(this)
        applyYear(startYear)
        historicalOverlay.opacityAlpha = percentToAlpha(startOpacityPct)
        mapView.addOverlay(historicalOverlay)

        PolygonLibrary.load(this)
        val water = PolygonLibrary.byKind("water")
        val cemeteries = PolygonLibrary.byKind("cemetery")
        mapView.addOverlay(AnimatedWaterOverlay(water))
        mapView.addOverlay(FireflyOverlay(cemeteries))
    }

    override fun onHistoricalYearChanged(year: String?) {
        applyYear(year)
    }

    override fun onHistoricalOpacityChanged(percent: Int) {
        historicalOverlay.opacityAlpha = percentToAlpha(percent)
    }

    /**
     * Swap the historical overlay's underlying TileArchive to a new year, or
     * null it out when the picker chooses "None". Closes the previous archive
     * to release its SQLite handle + LruCache.
     */
    private fun applyYear(year: String?) {
        val af = archiveFile ?: return
        val prev = historicalArchive
        if (year == null) {
            historicalOverlay.archive = null
            historicalArchive = null
        } else {
            val next = TileArchive(af, "Historical-$year")
            historicalOverlay.archive = next
            historicalArchive = next
        }
        prev?.close()
    }

    private fun percentToAlpha(pct: Int): Int =
        (pct.coerceIn(0, 100) * 255 / 100)

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        archive?.close()
        historicalArchive?.close()
    }
}
