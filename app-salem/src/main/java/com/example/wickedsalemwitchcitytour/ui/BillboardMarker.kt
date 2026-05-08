/*
 * WickedSalemApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module BillboardMarker.kt"

/**
 * S232 — kept as a thin pass-through subclass of `Marker` even after the real
 * billboard logic was reverted, because every site that constructed
 * `Marker(binding.mapView)` was sed-replaced with `BillboardMarker(...)` and
 * undoing those would be churn. Now identical behaviour to `Marker`.
 *
 * The intended billboard behaviour (counter the parent [TiltContainer]'s tilt
 * matrix so the icon stays screen-upright) needed `Canvas.setMatrix(identity)`,
 * which is unreliable on hardware-accelerated canvases (see the deprecation
 * notes on `Canvas.getMatrix`). At max tilt the icons either rendered at bogus
 * positions or got clipped out entirely. Reverted to stock draw so markers
 * remain visible and tilt with the perspective plane.
 */
class BillboardMarker(mv: MapView) : Marker(mv)
