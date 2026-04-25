/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import android.os.Bundle
import android.widget.FrameLayout
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

        // HUD label so we know we're in the prototype.
        val label = TextView(this).apply {
            text = "WickedMap prototype — pan/pinch, water animates"
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x88000000.toInt())
            setPadding(24, 16, 24, 16)
        }
        root.addView(
            label,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
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

        PolygonLibrary.load(this)
        val water = PolygonLibrary.byKind("water")
        val cemeteries = PolygonLibrary.byKind("cemetery")
        mapView.addOverlay(AnimatedWaterOverlay(water))
        mapView.addOverlay(FireflyOverlay(cemeteries))
    }

    override fun onDestroy() {
        super.onDestroy()
        archive?.close()
    }
}
