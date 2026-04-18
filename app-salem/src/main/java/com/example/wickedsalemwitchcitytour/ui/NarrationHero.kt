/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.R
import com.example.wickedsalemwitchcitytour.audio.NarrationHistory
import com.example.wickedsalemwitchcitytour.content.model.SalemPoi
import com.example.wickedsalemwitchcitytour.tour.NarrationSegment
import com.example.wickedsalemwitchcitytour.tour.NarrationState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module NarrationHero.kt"

/**
 * S146 #27 — Persistent top-of-map narration hero banner.
 *
 * Shows an always-visible banner right under the toolbar that reflects what
 * the narration engine is doing:
 *   - Currently speaking: thumbnail + POI title + category + playing icon
 *   - Idle: last-narrated POI lingers (grey idle icon), tapping replays
 *   - Never-narrated: banner hidden
 *
 * Banner is hidden entirely on splash / welcome / witch-trials / find screens
 * via [setHidden] (caller updates this when a full-screen sheet/dialog opens).
 *
 * Tap-through behavior: tapping the banner pans the map to the POI and opens
 * the standard PoiDetailSheet — same routing as the toolbar Jump button.
 */
class NarrationHero(
    private val activity: SalemMainActivity,
    private val mapView: MapView,
    private val narrationState: StateFlow<NarrationState>,
    private val poiLookup: (refId: String) -> SalemPoi?
) {
    private val TAG = "NarrHero"

    private val root: LinearLayout = activity.findViewById(R.id.narrationHeroBanner)
    private val thumb: ImageView = activity.findViewById(R.id.heroThumbnail)
    private val title: TextView = activity.findViewById(R.id.heroTitle)
    private val subtitle: TextView = activity.findViewById(R.id.heroSubtitle)
    private val playState: ImageView = activity.findViewById(R.id.heroPlayState)

    /** Last-narrated entry; survives state.Idle so the banner persists. */
    private var lastShownPoi: SalemPoi? = null
    private var lastShownRefId: String? = null
    private var lastShownTitle: String? = null
    private var currentSegment: NarrationSegment? = null

    /** When true, banner is hidden regardless of narration state (for full-screen overlays). */
    private var hiddenByScreen: Boolean = false

    init {
        root.setOnClickListener { onTap() }
        activity.lifecycleScope.launch {
            activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                narrationState.collect { onStateChanged(it) }
            }
        }
        DebugLogger.i(TAG, "NarrationHero wired")
    }

    /** Called by the activity when a full-screen sheet opens/closes. */
    fun setHidden(hidden: Boolean) {
        if (hiddenByScreen == hidden) return
        hiddenByScreen = hidden
        applyVisibility()
    }

    private fun onStateChanged(state: NarrationState) {
        when (state) {
            is NarrationState.Speaking -> {
                val seg = state.segment ?: return
                currentSegment = seg
                bindSegment(seg, playing = true)
            }
            is NarrationState.Paused -> {
                val seg = state.segment ?: currentSegment ?: return
                bindSegment(seg, playing = false)
            }
            is NarrationState.Idle -> {
                // Keep the last-narrated visible; just flip the play icon to idle.
                currentSegment = null
                if (lastShownRefId != null || lastShownTitle != null) {
                    playState.setImageResource(R.drawable.ic_hero_idle)
                    applyVisibility()
                }
            }
        }
    }

    private fun bindSegment(seg: NarrationSegment, playing: Boolean) {
        val poi = seg.refId?.let { poiLookup(it) }
        lastShownPoi = poi
        lastShownRefId = seg.refId
        lastShownTitle = poi?.name ?: seg.poiName.takeIf { it.isNotBlank() }

        title.text = lastShownTitle.orEmpty()
        subtitle.text = buildSubtitle(poi, seg)
        playState.setImageResource(
            if (playing) R.drawable.ic_hero_playing else R.drawable.ic_hero_idle
        )
        bindThumbnail(poi?.id ?: seg.refId)
        applyVisibility()
    }

    /**
     * Triptych panel 1 if bundled for this POI; generic Katrina avatar otherwise.
     * Asset path: `heroes/{poi_id}.webp` — populated by
     * tools/hero-triptych/sync-to-apk.sh after the campaign completes.
     */
    private fun bindThumbnail(poiId: String?) {
        val bmp = poiId?.let { HeroAssetLoader.loadTriptychThumb(activity, it) }
        if (bmp != null) {
            thumb.setImageBitmap(bmp)
        } else {
            thumb.setImageResource(R.drawable.welcome_katrina_avatar)
        }
    }

    private fun buildSubtitle(poi: SalemPoi?, seg: NarrationSegment): String {
        // Prefer POI category if we have a record; fall back to the
        // NarrationKind label.
        val cat = poi?.category ?: when (seg.kind) {
            com.example.wickedsalemwitchcitytour.tour.NarrationKind.POI -> "POI"
            com.example.wickedsalemwitchcitytour.tour.NarrationKind.ORACLE -> "Oracle"
            null -> ""
        }
        return cat.replace('_', ' ')
    }

    private fun applyVisibility() {
        val shouldShow = !hiddenByScreen && (currentSegment != null || lastShownTitle != null)
        root.visibility = if (shouldShow) View.VISIBLE else View.GONE
    }

    /**
     * Tap handler: pan map to the POI and open the detail sheet.
     * Falls back to replay-only if we don't have coordinates.
     */
    private fun onTap() {
        val poi = lastShownPoi
        val refId = lastShownRefId
        DebugLogger.i(TAG, "Banner tapped — poi=${poi?.name ?: "(null)"} refId=$refId")

        if (poi != null) {
            // Pan camera to the POI (zoom in if needed).
            val mapController = mapView.controller
            mapController.animateTo(GeoPoint(poi.lat, poi.lng))
            if (mapView.zoomLevelDouble < 17.0) {
                mapController.setZoom(17.5)
            }
            // Open the detail sheet.
            PoiDetailSheet.show(poi, activity.supportFragmentManager)
            return
        }

        // No POI record — if we have a history entry, just replay.
        val entry = NarrationHistory.current()
        if (entry != null) {
            DebugLogger.i(TAG, "No POI record; replaying last narration history entry")
            activity.tourViewModel.replayNarrationHistory(entry)
        }
    }

    /** Manually drive the banner (e.g. from the Jump toolbar icon). */
    fun onJumpRequested() {
        onTap()
    }
}
