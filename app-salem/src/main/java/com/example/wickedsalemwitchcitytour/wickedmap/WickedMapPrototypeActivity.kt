/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

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
 */
class WickedMapPrototypeActivity : AppCompatActivity() {

    private var archive: TileArchive? = null
    private var historicalArchive: TileArchive? = null

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
            text = "WickedMap prototype — 1851 McIntyre @ 70% (S286 P1)"
            setTextColor(0xFFFFFFFF.toInt())
        }
        val closeBtn = Button(this).apply {
            text = "Close"
            setOnClickListener { finish() }
        }
        hud.addView(
            label,
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f,
            )
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

        setContentView(root)

        val archiveFile = File(getExternalFilesDir(null), "salem_tiles.sqlite")
        if (!archiveFile.exists()) {
            // Fallback: extract from assets if OfflineTileManager hasn't run yet.
            assets.open("salem_tiles.sqlite").use { input ->
                archiveFile.outputStream().use { out -> input.copyTo(out) }
            }
        }

        archive = TileArchive(archiveFile).also {
            mapView.attachArchive(it)
        }

        // S286 Phase 1: hardcoded 1851 McIntyre overlay at 70% opacity to
        // smoke-test the historical-maps pipeline end-to-end. UI picker (FAB
        // + bottom-sheet) lands in Phase 2.
        historicalArchive = TileArchive(archiveFile, "Historical-1851")
        mapView.addOverlay(HistoricalTileOverlay(historicalArchive, opacityAlpha = 178))

        PolygonLibrary.load(this)
        val water = PolygonLibrary.byKind("water")
        val cemeteries = PolygonLibrary.byKind("cemetery")
        mapView.addOverlay(AnimatedWaterOverlay(water))
        mapView.addOverlay(FireflyOverlay(cemeteries))
    }

    override fun onDestroy() {
        super.onDestroy()
        archive?.close()
        historicalArchive?.close()
    }
}
