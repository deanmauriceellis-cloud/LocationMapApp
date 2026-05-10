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
 * Marker subclass that exposes the protected anchor / alpha / bearing fields so
 * [TiltContainer]'s pass-2 billboard renderer can stamp the icon upright at the
 * tilt-mapped screen position with the same visual centering it would have had
 * in flat mode.
 *
 * Behaves identically to plain [Marker] for normal (untilted) draw — the
 * billboard path lives in [TiltContainer.dispatchDraw] and only kicks in when
 * tilt > 0°.
 */
class BillboardMarker(mv: MapView) : Marker(mv) {
    /** Anchor U (0..1) — fraction of icon width past which the position sits. */
    fun billboardAnchorU(): Float = mAnchorU

    /** Anchor V (0..1) — fraction of icon height past which the position sits. */
    fun billboardAnchorV(): Float = mAnchorV

    /** Marker alpha (0..1). */
    fun billboardAlpha(): Float = mAlpha
}
