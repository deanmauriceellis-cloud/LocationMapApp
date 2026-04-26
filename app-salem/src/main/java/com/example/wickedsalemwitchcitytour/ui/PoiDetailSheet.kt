/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import com.example.locationmapapp.util.DebugLogger
import com.example.locationmapapp.util.FeatureFlags
import com.example.wickedsalemwitchcitytour.R
import com.example.wickedsalemwitchcitytour.content.PoiContentPolicy
import com.example.wickedsalemwitchcitytour.content.model.SalemPoi
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject

/**
 * POI Detail — full-screen dialog (Session 113, iteration 2).
 *
 * Opens when the user taps a POI on the map marker or in the proximity dock.
 * Fills the entire window (no background map visible) so stray taps cannot
 * leak through to the map below. This was the v1 bug: a BottomSheet allowed
 * tapping map markers above it, which opened a second sheet with mismatched
 * audio vs. view.
 *
 * Layout:
 *   - Top 20% of the initial window height: hero image (fitXY stretch)
 *   - Scrollable body: overview row (icon + name + type + address + phone)
 *     + Visit Website button + Overview / About / The Story narrative sections
 *     + "Things You Can Do" action buttons
 *
 * TTS behavior (iteration 2):
 *   - On open, any lingering `sheet_*` TTS segments from a previously-
 *     dismissed sheet are cancelled FIRST so a stale narration cannot play
 *     over a new sheet's view.
 *   - Then the sheet queues, as tagged segments that queue behind whatever
 *     ambient narration is currently playing:
 *       1. Name + type
 *       2. A single contact line: address + phone + website acknowledgement
 *       3. Short narration (if populated)
 *       4. Description (if populated and distinct from Short)
 *       5. Long narration / Pass 2 (if populated and distinct from the above)
 *   - Action buttons are NOT read. UI labels are NOT read. Operator spec.
 *   - Any user action inside the sheet (close, dismiss, website button, any
 *     action button) cancels the sheet's tagged segments so the TTS matches
 *     the view.
 *
 * Verbose lifecycle logging: every fragment callback logs the POI id, so a
 * future crash can be traced end-to-end in the persistent debug log.
 */
@AndroidEntryPoint
class PoiDetailSheet : DialogFragment() {

    private val tourViewModel: TourViewModel by activityViewModels()

    private lateinit var poi: SalemPoi

    /** Stable per-sheet tag used as the utterance-id prefix for this sheet's
     *  TTS segments. Lets [cancelSheetRead] drop only this sheet's queued
     *  speech while leaving unrelated ambient narration alone. */
    private val ttsTag: String by lazy { "sheet_${poi.id}_" }

    // ── Dialog configuration ────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full-screen style with no floating chrome. We draw our own
        // background in the layout via @color/salemBackground.
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        DebugLogger.i(TAG, "onCreate saved=${savedInstanceState != null}")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.BLACK))
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        // Defensive: re-assert full-screen after the window is attached.
        // Some OEMs (Lenovo included) can reapply default dialog insets on
        // start; this ensures the sheet always fills the screen.
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT
        )
        DebugLogger.i(TAG, "onStart id=${if (::poi.isInitialized) poi.id else "pre-view"}")
    }

    override fun onResume() {
        super.onResume()
        DebugLogger.i(TAG, "onResume id=${if (::poi.isInitialized) poi.id else "pre-view"}")
    }

    override fun onPause() {
        DebugLogger.i(TAG, "onPause id=${if (::poi.isInitialized) poi.id else "pre-view"}")
        super.onPause()
    }

    override fun onStop() {
        DebugLogger.i(TAG, "onStop id=${if (::poi.isInitialized) poi.id else "pre-view"}")
        super.onStop()
    }

    override fun onDestroyView() {
        DebugLogger.i(TAG, "onDestroyView id=${if (::poi.isInitialized) poi.id else "pre-view"}")
        super.onDestroyView()
    }

    override fun onDestroy() {
        DebugLogger.i(TAG, "onDestroy id=${if (::poi.isInitialized) poi.id else "pre-view"}")
        super.onDestroy()
    }

    override fun onCancel(dialog: DialogInterface) {
        DebugLogger.i(TAG, "onCancel id=${if (::poi.isInitialized) poi.id else "pre-view"}")
        cancelSheetRead()
        super.onCancel(dialog)
    }

    override fun onDismiss(dialog: DialogInterface) {
        DebugLogger.i(TAG, "onDismiss id=${if (::poi.isInitialized) poi.id else "pre-view"}")
        cancelSheetRead()
        super.onDismiss(dialog)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        DebugLogger.i(TAG, "onConfigurationChanged id=${if (::poi.isInitialized) poi.id else "pre-view"}")
    }

    // ── View creation ───────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val poiJson = requireArguments().getString(ARG_POI_JSON)
            ?: throw IllegalArgumentException("PoiDetailSheet missing $ARG_POI_JSON")
        poi = parseSalemPoi(poiJson)
            ?: throw IllegalArgumentException("PoiDetailSheet: invalid POI payload")
        DebugLogger.i(
            TAG,
            "onCreateView id=${poi.id} name=${poi.name} category=${poi.category} " +
                "hasShort=${!poi.shortNarration.isNullOrBlank()} " +
                "hasDesc=${!poi.description.isNullOrBlank()} " +
                "hasLong=${!poi.longNarration.isNullOrBlank()} " +
                "hasAddr=${!poi.address.isNullOrBlank()} " +
                "hasPhone=${!poi.phone.isNullOrBlank()} " +
                "hasSite=${!poi.website.isNullOrBlank()}"
        )
        return inflater.inflate(R.layout.poi_detail_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val stripped = PoiContentPolicy.shouldStripContent(poi)
        DebugLogger.i(TAG, "onViewCreated id=${poi.id} stripped=$stripped merchantTier=${poi.merchantTier}")

        if (stripped) {
            bindStrippedHero(view)
            bindStrippedOverview(view)
            hideNarrativeChrome(view)
            queueStrippedReadThrough()
        } else {
            bindHero(view)
            bindOverview(view)
            bindWebsiteButton(view)
            val (shortText, aboutText, storyText) = bindNarrationSections(view)
            bindActions(view)
            queueSheetReadThrough(shortText, aboutText, storyText)
        }
    }

    // ── Stripped-mode render path (S154, PG-13 / legal-safety) ──────────
    //
    // Non-licensed commercial POIs render as: full-screen painterly category
    // hero + name + category + address + phone + plain-text website + hours.
    // No narration, no historical_note, no action buttons, no TTS body read.
    // TTS plays only the POI name so the blind/driving user still gets an
    // audio confirmation of the tap.

    private fun bindStrippedHero(view: View) {
        val heroContainer = view.findViewById<ViewGroup>(R.id.heroContainer)
        val heroImage = view.findViewById<ImageView>(R.id.heroImage)
        val heroOverlay = view.findViewById<TextView>(R.id.heroOverlayLabel)
        val btnClose = view.findViewById<TextView>(R.id.btnClose)

        // Painterly category graphic fills a large hero — ~55% of screen,
        // then overview + contact fields sit below.
        val screenH = resources.displayMetrics.heightPixels
        heroContainer.layoutParams.height = (screenH * 0.55).toInt()
        heroContainer.requestLayout()

        heroImage.scaleType = ImageView.ScaleType.CENTER_CROP
        // S154 (second pass): use PoiHeroResolver Tier 2 so every stripped
        // POI gets a hash-pinned category variant from poi-icons/{category}/
        // — 40-256 existing painterly options per category with subcategory
        // + spooky-style variants. forceCategoryFallback=true skips the per-
        // POI Tier 0/1 heroes (which must not ship for non-licensed
        // commercial POIs; a separate bundle-prune script removes them).
        val result = PoiHeroResolver.applyTo(requireContext(), poi, heroImage, forceCategoryFallback = true)
        heroOverlay.visibility =
            if (result is PoiHeroResolver.HeroResult.RedPlaceholder) View.VISIBLE else View.GONE

        btnClose.setOnClickListener {
            DebugLogger.i(TAG, "close clicked (stripped) id=${poi.id}")
            cancelSheetRead()
            dismiss()
        }
    }

    private fun bindStrippedOverview(view: View) {
        // No overview icon thumbnail — the hero carries the visual weight.
        view.findViewById<ImageView>(R.id.overviewIcon).visibility = View.GONE

        view.findViewById<TextView>(R.id.overviewName).text = poi.name
        view.findViewById<TextView>(R.id.overviewType).text = displayCategory(poi.category)

        bindInertText(view, R.id.overviewAddress, poi.address)
        bindInertText(view, R.id.overviewPhone, poi.phone)
        bindInertText(view, R.id.overviewWebsite, poi.website)
        bindInertText(view, R.id.overviewHours, poi.hours ?: poi.hoursText)
    }

    private fun bindInertText(view: View, id: Int, value: String?) {
        val tv = view.findViewById<TextView>(id)
        val v = value?.takeIf { it.isNotBlank() }
        if (v != null) {
            tv.text = v
            tv.visibility = View.VISIBLE
            tv.setOnClickListener(null)
            tv.isClickable = false
        }
    }

    private fun hideNarrativeChrome(view: View) {
        // Hide everything below the contact row: dividers, narrative sections,
        // Things-You-Can-Do header, action button container, and the big
        // Visit-Website button.
        intArrayOf(
            R.id.btnWebsite,
            R.id.divider1,
            R.id.labelShort, R.id.bodyShort,
            R.id.labelAbout, R.id.bodyAbout,
            R.id.labelStory, R.id.bodyStory,
            R.id.divider2,
            R.id.labelActions,
            R.id.actionsContainer
        ).forEach { id -> view.findViewById<View>(id)?.visibility = View.GONE }
    }

    private fun queueStrippedReadThrough() {
        tourViewModel.cancelSheetReading(SHEET_TAG_PREFIX)
        val line = PoiContentPolicy.strippedAnnouncement(poi)
        tourViewModel.speakSheetSection(ttsTag, line, poi.name)
        DebugLogger.i(TAG, "tts enqueue stripped id=${poi.id} len=${line.length}")
    }

    // ── Hero strip ──────────────────────────────────────────────────────

    private fun bindHero(view: View) {
        val heroContainer = view.findViewById<ViewGroup>(R.id.heroContainer)
        val heroImage = view.findViewById<ImageView>(R.id.heroImage)
        val heroOverlay = view.findViewById<TextView>(R.id.heroOverlayLabel)
        val btnClose = view.findViewById<TextView>(R.id.btnClose)

        // 20% of the initial screen height — fixed per operator spec.
        val screenH = resources.displayMetrics.heightPixels
        heroContainer.layoutParams.height = (screenH * 0.20).toInt()
        heroContainer.requestLayout()

        val result = PoiHeroResolver.applyTo(requireContext(), poi, heroImage)
        val isRed = result is PoiHeroResolver.HeroResult.RedPlaceholder
        heroOverlay.visibility = if (isRed) View.VISIBLE else View.GONE
        DebugLogger.i(TAG, "hero resolved id=${poi.id} red=$isRed")

        btnClose.setOnClickListener {
            DebugLogger.i(TAG, "close clicked id=${poi.id}")
            cancelSheetRead()
            dismiss()
        }
    }

    // ── Overview row ────────────────────────────────────────────────────

    private fun bindOverview(view: View) {
        PoiHeroResolver.applyTo(
            requireContext(),
            poi,
            view.findViewById<ImageView>(R.id.overviewIcon)
        )

        view.findViewById<TextView>(R.id.overviewName).text = poi.name
        view.findViewById<TextView>(R.id.overviewType).text =
            displayCategory(poi.category)

        view.findViewById<TextView>(R.id.overviewAddress).apply {
            val addr = poi.address
            if (!addr.isNullOrBlank()) {
                text = addr
                visibility = View.VISIBLE
                setOnClickListener {
                    DebugLogger.i(TAG, "tap-to-speak ADDRESS id=${poi.id}")
                    interruptAndSpeak("Located at $addr")
                }
            }
        }
        view.findViewById<TextView>(R.id.overviewPhone).apply {
            val phone = poi.phone
            if (!phone.isNullOrBlank()) {
                text = phone
                visibility = View.VISIBLE
            }
        }
    }

    // ── Website button ──────────────────────────────────────────────────

    private fun bindWebsiteButton(view: View) {
        val btn = view.findViewById<TextView>(R.id.btnWebsite)
        // S180: V1 zero-network — Visit Website button is hidden entirely.
        // Re-enable for V2 by flipping FeatureFlags.V1_OFFLINE_ONLY.
        if (FeatureFlags.V1_OFFLINE_ONLY) {
            btn.visibility = View.GONE
            return
        }
        val site = poi.website?.takeIf { it.isNotBlank() } ?: return
        btn.visibility = View.VISIBLE
        btn.setOnClickListener {
            DebugLogger.i(TAG, "website clicked id=${poi.id} url=$site")
            cancelSheetRead()
            val hostActivity = this.activity as? SalemMainActivity
            if (hostActivity != null) {
                hostActivity.showFullScreenWebView(site, poi.name)
            } else {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(site)))
                } catch (e: Exception) {
                    DebugLogger.w(TAG, "Fallback browser failed: ${e.message}")
                }
            }
        }
    }

    // ── Narration sections ──────────────────────────────────────────────

    private fun bindNarrationSections(view: View): Triple<String?, String?, String?> {
        val labelShort = view.findViewById<TextView>(R.id.labelShort)
        val bodyShort = view.findViewById<TextView>(R.id.bodyShort)
        val labelAbout = view.findViewById<TextView>(R.id.labelAbout)
        val bodyAbout = view.findViewById<TextView>(R.id.bodyAbout)
        val labelStory = view.findViewById<TextView>(R.id.labelStory)
        val bodyStory = view.findViewById<TextView>(R.id.bodyStory)

        val shortText = poi.shortNarration?.takeIf { it.isNotBlank() }
        val aboutText = poi.description?.takeIf { it.isNotBlank() && it != shortText }
        val storyText = poi.longNarration?.takeIf { it.isNotBlank() }
            ?.takeIf { it != shortText && it != aboutText }

        if (shortText != null) {
            bodyShort.text = shortText
            val tapToSpeak = View.OnClickListener {
                DebugLogger.i(TAG, "tap-to-speak OVERVIEW id=${poi.id}")
                interruptAndSpeak(shortText)
            }
            labelShort.setOnClickListener(tapToSpeak)
            bodyShort.setOnClickListener(tapToSpeak)
        } else {
            labelShort.visibility = View.GONE
            bodyShort.visibility = View.GONE
        }

        if (aboutText != null) {
            labelAbout.visibility = View.VISIBLE
            bodyAbout.visibility = View.VISIBLE
            bodyAbout.text = aboutText
            val tapToSpeak = View.OnClickListener {
                DebugLogger.i(TAG, "tap-to-speak ABOUT id=${poi.id}")
                interruptAndSpeak(aboutText)
            }
            labelAbout.setOnClickListener(tapToSpeak)
            bodyAbout.setOnClickListener(tapToSpeak)
        }

        if (storyText != null) {
            labelStory.visibility = View.VISIBLE
            bodyStory.visibility = View.VISIBLE
            bodyStory.text = storyText
            val tapToSpeak = View.OnClickListener {
                DebugLogger.i(TAG, "tap-to-speak STORY id=${poi.id}")
                interruptAndSpeak(storyText)
            }
            labelStory.setOnClickListener(tapToSpeak)
            bodyStory.setOnClickListener(tapToSpeak)
        }

        return Triple(shortText, aboutText, storyText)
    }

    /** Interrupt all current TTS (ambient + sheet) and speak this text immediately. */
    private fun interruptAndSpeak(text: String) {
        tourViewModel.stopNarration()
        tourViewModel.speakSheetSection(ttsTag, text, poi.name)
    }

    // ── Action buttons (rendered only — NOT narrated) ───────────────────

    private fun bindActions(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.actionsContainer)
        container.removeAllViews()
        val actions = PoiActionSynthesizer.buildActions(poi)

        val density = resources.displayMetrics.density
        val dp = { v: Int -> (v * density).toInt() }

        // The website visit is already rendered as the big primary button
        // above the narrative sections; skip it here to avoid a duplicate.
        val visibleActions = actions.filterNot {
            it is PoiActionSynthesizer.Action.VisitWebsite && poi.website?.isNotBlank() == true
        }

        if (visibleActions.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = "No additional actions."
                textSize = 13f
                setTextColor(Color.parseColor("#B8AFA0"))
                setPadding(0, dp(8), 0, 0)
            }
            container.addView(empty)
            return
        }

        for (action in visibleActions) {
            val btn = TextView(requireContext()).apply {
                text = when (action) {
                    is PoiActionSynthesizer.Action.VisitWebsite -> action.label
                    is PoiActionSynthesizer.Action.Call -> "${action.label}  ·  ${action.phone}"
                    is PoiActionSynthesizer.Action.Directions -> action.label
                    is PoiActionSynthesizer.Action.Hours -> "${action.label}  ·  ${action.hours}"
                    is PoiActionSynthesizer.Action.Unknown -> action.label
                }
                textSize = 14f
                setTextColor(Color.parseColor("#F5F0E8"))
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#2A2A3C"))
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), Color.parseColor("#C9A84C"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }

                val interactive = action !is PoiActionSynthesizer.Action.Hours &&
                        action !is PoiActionSynthesizer.Action.Unknown
                isClickable = interactive
                isFocusable = interactive
                if (interactive) {
                    setOnClickListener { onActionClicked(action) }
                } else {
                    alpha = 0.85f
                }
            }
            container.addView(btn)
        }
    }

    private fun onActionClicked(action: PoiActionSynthesizer.Action) {
        DebugLogger.i(TAG, "action clicked id=${poi.id} action=${action::class.simpleName}")
        cancelSheetRead()
        when (action) {
            is PoiActionSynthesizer.Action.VisitWebsite -> {
                // S180: V1 zero-network — never launch the embedded browser.
                if (!FeatureFlags.V1_OFFLINE_ONLY) {
                    (activity as? SalemMainActivity)?.showFullScreenWebView(action.url, poi.name)
                }
            }
            is PoiActionSynthesizer.Action.Call -> {
                try {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${action.phone}")))
                } catch (e: Exception) {
                    DebugLogger.w(TAG, "No dialer available: ${e.message}")
                }
            }
            is PoiActionSynthesizer.Action.Directions -> {
                // S175: route on the bundled Salem graph and draw the polyline
                // on the in-app map. Falls back to a straight-line route inside
                // WalkingDirections if the bundle isn't loaded.
                val host = activity as? SalemMainActivity
                if (host != null) {
                    host.walkTo(org.osmdroid.util.GeoPoint(action.lat, action.lng))
                    dismiss()
                } else if (!FeatureFlags.V1_OFFLINE_ONLY) {
                    // S180: V1 zero-network — geo: ACTION_VIEW fallback gated.
                    // (No-op in V1; on-device walkTo is the only path.)
                    val label = Uri.encode(poi.name)
                    val geo = Uri.parse("geo:${action.lat},${action.lng}?q=${action.lat},${action.lng}($label)")
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, geo))
                    } catch (e: Exception) {
                        DebugLogger.w(TAG, "No maps app available: ${e.message}")
                    }
                }
            }
            is PoiActionSynthesizer.Action.Hours -> { /* inline display only */ }
            is PoiActionSynthesizer.Action.Unknown -> { /* no-op */ }
        }
    }

    // ── TTS read-through (trimmed per S113 iteration 2 operator spec) ──

    private fun queueSheetReadThrough(
        shortText: String?,
        aboutText: String?,
        storyText: String?
    ) {
        // Drop ANY lingering sheet segments from a previously-dismissed
        // sheet, regardless of POI. Prevents audio-vs-view mismatch when
        // the user opens a second sheet before the first's read finishes.
        tourViewModel.cancelSheetReading(SHEET_TAG_PREFIX)

        val name = poi.name

        // Segment 1: name + category (brief intro, no address/phone — those
        // are tappable in the UI for on-demand reading).
        val intro = "${name}. ${displayCategory(poi.category)}."
        tourViewModel.speakSheetSection(ttsTag, intro, name)
        DebugLogger.i(TAG, "tts enqueue intro id=${poi.id} len=${intro.length}")

        // Segment 2+: narrative sections in visual order.
        // Overview first (matches what the user sees on screen).
        shortText?.let {
            tourViewModel.speakSheetSection(ttsTag, it, name)
            DebugLogger.i(TAG, "tts enqueue overview id=${poi.id} len=${it.length}")
        }
        aboutText?.let {
            tourViewModel.speakSheetSection(ttsTag, it, name)
            DebugLogger.i(TAG, "tts enqueue about id=${poi.id} len=${it.length}")
        }
        storyText?.let {
            tourViewModel.speakSheetSection(ttsTag, it, name)
            DebugLogger.i(TAG, "tts enqueue story id=${poi.id} len=${it.length}")
        }
    }

    private fun cancelSheetRead() {
        // Drop all sheet-tagged segments (prefix match), not just this
        // sheet's. If a user rapidly taps a second POI, the first sheet's
        // onDismiss fires AFTER the second sheet has already queued its
        // segments — cancelling only our own tag would work, but blanket
        // prefix cancellation is simpler and equivalent in practice.
        tourViewModel.cancelSheetReading(SHEET_TAG_PREFIX)
        DebugLogger.i(TAG, "cancelSheetRead id=${if (::poi.isInitialized) poi.id else "pre-view"}")
    }

    companion object {
        private const val TAG = "PoiDetailSheet"
        private const val ARG_POI_JSON = "poi_json"
        /** Common prefix for every sheet-initiated TTS utterance. */
        private const val SHEET_TAG_PREFIX = "sheet_"

        fun show(poi: SalemPoi, fragmentManager: FragmentManager) {
            DebugLogger.i(TAG, "show() request id=${poi.id} name=${poi.name}")
            (fragmentManager.findFragmentByTag(TAG_SHOW) as? PoiDetailSheet)?.dismissAllowingStateLoss()
            val sheet = PoiDetailSheet()
            sheet.arguments = Bundle().apply {
                putString(ARG_POI_JSON, salemPoiToJson(poi))
            }
            sheet.show(fragmentManager, TAG_SHOW)
        }

        private const val TAG_SHOW = "poi_detail_sheet"
    }
}

// ────────────────────────────────────────────────────────────────────────
// Argument passing helpers
//
// SalemPoi is a Room @Entity data class, not Parcelable, so we
// round-trip just the fields the sheet actually displays through JSON.
// ────────────────────────────────────────────────────────────────────────

private fun salemPoiToJson(poi: SalemPoi): String = JSONObject().apply {
    put("id", poi.id)
    put("name", poi.name)
    put("lat", poi.lat)
    put("lng", poi.lng)
    putOpt("address", poi.address)
    put("category", poi.category)
    putOpt("short_narration", poi.shortNarration)
    putOpt("long_narration", poi.longNarration)
    putOpt("description", poi.description)
    putOpt("image_asset", poi.imageAsset)
    putOpt("phone", poi.phone)
    putOpt("website", poi.website)
    putOpt("hours", poi.hours)
    putOpt("hours_text", poi.hoursText)
    putOpt("action_buttons", poi.actionButtons)
    put("ad_priority", poi.adPriority)
    put("merchant_tier", poi.merchantTier)
    put("geofence_radius_m", poi.geofenceRadiusM)
}.toString()

private fun parseSalemPoi(s: String): SalemPoi? {
    return try {
        val o = JSONObject(s)
        SalemPoi(
            id = o.getString("id"),
            name = o.getString("name"),
            lat = o.getDouble("lat"),
            lng = o.getDouble("lng"),
            address = o.optString("address", "").takeIf { it.isNotEmpty() },
            category = o.getString("category"),
            shortNarration = o.optString("short_narration", "").takeIf { it.isNotEmpty() },
            longNarration = o.optString("long_narration", "").takeIf { it.isNotEmpty() },
            description = o.optString("description", "").takeIf { it.isNotEmpty() },
            imageAsset = o.optString("image_asset", "").takeIf { it.isNotEmpty() },
            phone = o.optString("phone", "").takeIf { it.isNotEmpty() },
            website = o.optString("website", "").takeIf { it.isNotEmpty() },
            hours = o.optString("hours", "").takeIf { it.isNotEmpty() },
            hoursText = o.optString("hours_text", "").takeIf { it.isNotEmpty() },
            actionButtons = o.optString("action_buttons", "").takeIf { it.isNotEmpty() },
            adPriority = o.optInt("ad_priority", 0),
            merchantTier = o.optInt("merchant_tier", 0),
            geofenceRadiusM = o.optInt("geofence_radius_m", 40)
        )
    } catch (_: Exception) {
        null
    }
}

/** Convert SalemPoi.category (e.g. "FOOD_DRINK") to display name (e.g. "Food & Drink") */
private fun displayCategory(category: String): String {
    return category.replace('_', ' ').lowercase()
        .split(' ').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
