/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import android.graphics.Canvas

/**
 * Anything drawn on top of tiles. The view calls tick() once per frame before
 * draw() — overlays use tick() to advance animation state, draw() to paint.
 */
interface MapOverlay {
    fun tick(frameTimeMs: Long) {}
    fun draw(canvas: Canvas, camera: CameraState)
}
