/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.audio

import android.content.Context
import android.content.SharedPreferences

/**
 * AudioControl — S145 Must-Have #45 universal audio control singleton.
 *
 * Holds the user-facing audio preferences surfaced from the toolbar speaker
 * icon. SharedPreferences-backed so state persists across launches.
 *
 * Four POI/Oracle group toggles + one splash voiceover toggle + one detail
 * level cycle. Call sites consult [isPoiSpeechEnabled] / [isOracleSpeechEnabled]
 * before enqueuing narration; the NarrationManager itself stays dumb.
 *
 * Grouping (S154 amendment: WITCH_SHOP moved MEANINGFUL → BUSINESSES to align
 * with the PG-13 content-strip policy — witch shops are commercial and strip
 * unless `merchant_tier >= 1`, same as any other business):
 *   - MEANINGFUL → HISTORICAL_BUILDINGS, CIVIC, WORSHIP
 *   - AMBIENT    → PARKS_REC, EDUCATION
 *   - BUSINESSES → FOOD_DRINK, SHOPPING, LODGING, HEALTHCARE, ENTERTAINMENT,
 *                  AUTO_SERVICES, OFFICES, TOUR_COMPANIES, PSYCHIC, FINANCE,
 *                  FUEL_CHARGING, TRANSIT, PARKING, EMERGENCY, WITCH_SHOP,
 *                  SERVICES (S200 — operator+lawyer-confirmed commercial)
 *
 * Unknown categories default to MEANINGFUL (safe fallback for historic content
 * that has not yet been classified).
 */
object AudioControl {

    private const val PREFS_NAME = "audio_control_v1"

    private const val PREF_ORACLE            = "oracle_on"
    private const val PREF_MEANINGFUL        = "meaningful_on"
    private const val PREF_AMBIENT           = "ambient_on"
    private const val PREF_BUSINESSES        = "businesses_on"
    private const val PREF_DETAIL            = "detail_level"
    private const val PREF_LINGER_AND_LISTEN = "linger_and_listen"
    // S272 — TTS master switch: single explicit kill above all source toggles.
    // Default ON; persisted. The runtime availability flag (engineReady &&
    // routeUsable) is NOT persisted — it's pushed from NarrationManager.onInit
    // and SalemMainActivity's AudioDeviceCallback.
    private const val PREF_TTS_MASTER        = "tts_master_on"

    enum class Group { MEANINGFUL, AMBIENT, BUSINESSES }

    enum class DetailLevel { BRIEF, STANDARD, DEEP }

    interface ChangeListener { fun onAudioControlChanged() }

    private val listeners = mutableListOf<ChangeListener>()
    private var prefs: SharedPreferences? = null

    /**
     * S217 — "Show All POIs" FAB override.
     *
     * Operator rule: when the FAB is on, ALL POIs narrate, regardless of:
     *   - tour-mode gate (is_tour_poi / Layers checkboxes)
     *   - audio-group toggles (Meaningful / Ambient / Businesses)
     *   - historical-mode gate
     *
     * In-memory only: the FAB always starts OFF on cold launch. Pushed from
     * [com.example.wickedsalemwitchcitytour.ui.SalemMainActivity.refreshHistoricalModeForActiveTour]
     * whenever the FAB or tour state changes.
     */
    @Volatile
    private var showAllOverride: Boolean = false

    // S272 — runtime TTS availability tracked as two distinct signals so the
    // engine-ready push from NarrationManager and the route-usable push from
    // SalemMainActivity's AudioDeviceCallback do not clobber each other.
    @Volatile private var engineReady: Boolean = false
    @Volatile private var routeUsable: Boolean = true  // optimistic on cold start

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun requirePrefs(): SharedPreferences =
        prefs ?: error("AudioControl.init(context) must be called before use")

    // ── Getters ─────────────────────────────────────────────────────────
    // S168: Oracle (Witch-Trials newspaper narrations) default OFF — operator
    // direction. Users opt in via Audio Control → Oracle toggle when they want
    // the historical-source layer on top of the POI + ambient narration.
    fun isOracleEnabled(): Boolean     = requirePrefs().getBoolean(PREF_ORACLE, false)
    fun isMeaningfulEnabled(): Boolean = requirePrefs().getBoolean(PREF_MEANINGFUL, true)
    fun isAmbientEnabled(): Boolean    = requirePrefs().getBoolean(PREF_AMBIENT, true)
    // S149: Businesses default OFF — operator direction 2026-04-18. AutoZone,
    // retail, etc. should only narrate once the user explicitly opts in via
    // the Audio Control popup. Oracle + Meaningful + Ambient remain default-on
    // so the historic-tour experience fires out of the box.
    fun isBusinessesEnabled(): Boolean     = requirePrefs().getBoolean(PREF_BUSINESSES, false)
    fun isLingerAndListenEnabled(): Boolean = requirePrefs().getBoolean(PREF_LINGER_AND_LISTEN, false)

    // S168: install default flipped from STANDARD → DEEP so first-launch users
    // hear `long_narration` (falling back to short when long is missing). They
    // can dial back via Audio Control (long-press) if they want shorter clips.
    fun detailLevel(): DetailLevel =
        DetailLevel.values().getOrElse(requirePrefs().getInt(PREF_DETAIL, DetailLevel.DEEP.ordinal)) {
            DetailLevel.DEEP
        }

    // ── Setters ─────────────────────────────────────────────────────────
    fun setOracleEnabled(on: Boolean) {
        requirePrefs().edit().putBoolean(PREF_ORACLE, on).apply()
        com.example.locationmapapp.util.DebugLogger.i("AudioControl", "Oracle Newspaper → $on (pref=$PREF_ORACLE)")
        notifyListeners()
    }
    fun setMeaningfulEnabled(on: Boolean) { requirePrefs().edit().putBoolean(PREF_MEANINGFUL, on).apply(); notifyListeners() }
    fun setAmbientEnabled(on: Boolean)    { requirePrefs().edit().putBoolean(PREF_AMBIENT, on).apply(); notifyListeners() }
    fun setBusinessesEnabled(on: Boolean)     { requirePrefs().edit().putBoolean(PREF_BUSINESSES, on).apply(); notifyListeners() }
    fun setLingerAndListenEnabled(on: Boolean) { requirePrefs().edit().putBoolean(PREF_LINGER_AND_LISTEN, on).apply(); notifyListeners() }

    fun setDetailLevel(level: DetailLevel) {
        requirePrefs().edit().putInt(PREF_DETAIL, level.ordinal).apply()
        notifyListeners()
    }

    fun cycleDetailLevel(): DetailLevel {
        val next = when (detailLevel()) {
            DetailLevel.BRIEF    -> DetailLevel.STANDARD
            DetailLevel.STANDARD -> DetailLevel.DEEP
            DetailLevel.DEEP     -> DetailLevel.BRIEF
        }
        setDetailLevel(next)
        return next
    }

    // ── Category → group mapping ────────────────────────────────────────
    fun groupForCategory(categoryRaw: String?): Group {
        val c = categoryRaw?.uppercase() ?: return Group.MEANINGFUL
        return when (c) {
            "HISTORICAL_BUILDINGS", "HISTORICAL_LANDMARKS",
            "CIVIC", "WORSHIP"                                        -> Group.MEANINGFUL
            "PARKS_REC", "EDUCATION"                                 -> Group.AMBIENT
            "FOOD_DRINK", "SHOPPING", "LODGING", "HEALTHCARE",
            "ENTERTAINMENT", "AUTO_SERVICES", "OFFICES",
            "TOUR_COMPANIES", "PSYCHIC", "FINANCE",
            "FUEL_CHARGING", "TRANSIT", "PARKING", "EMERGENCY",
            "WITCH_SHOP", "SERVICES"                                 -> Group.BUSINESSES
            else                                                     -> Group.MEANINGFUL
        }
    }

    fun isPoiSpeechEnabled(categoryRaw: String?): Boolean {
        // S273: Show-All-POI override no longer bypasses audio-group gates.
        // Operator intent: "Show me everything around me, but if I unchecked
        // Ships & Services, don't NARRATE businesses." Visibility and audio
        // are independent concerns — showAllOverride still bypasses the
        // tour/layers visibility gates (see NarrationGeofenceManager.kt:262
        // and SalemMainActivityNarration.kt:973), but audio routing always
        // respects the user's per-group toggle.
        val group = groupForCategory(categoryRaw)
        val enabled = when (group) {
            Group.MEANINGFUL -> isMeaningfulEnabled()
            Group.AMBIENT    -> isAmbientEnabled()
            Group.BUSINESSES -> isBusinessesEnabled()
        }
        if (com.example.wickedsalemwitchcitytour.BuildConfig.DEBUG) {
            com.example.locationmapapp.util.DebugLogger.d(
                "AudioControl",
                "isPoiSpeechEnabled cat=$categoryRaw group=$group enabled=$enabled " +
                    "(showAll=$showAllOverride meaningful=${isMeaningfulEnabled()} " +
                    "ambient=${isAmbientEnabled()} businesses=${isBusinessesEnabled()})"
            )
        }
        return enabled
    }

    /** S217 — true when the "Show All POIs" FAB is on (operator override). */
    fun isShowAllOverride(): Boolean = showAllOverride

    /** S217 — pushed from SalemMainActivity when the FAB or tour state changes.
     *  S273: scope narrowed — visibility-only bypass; audio-group gates still apply. */
    fun setShowAllOverride(on: Boolean) {
        if (showAllOverride == on) return
        showAllOverride = on
        com.example.locationmapapp.util.DebugLogger.i(
            "AudioControl",
            "Show-All-POI override → $on (bypasses tour/layers VISIBILITY only; " +
                "audio group gates still apply)"
        )
        notifyListeners()
    }

    fun isOracleSpeechEnabled(): Boolean = isOracleEnabled()

    // ── S272: TTS master mute + runtime availability ────────────────────
    fun isTtsMasterEnabled(): Boolean = requirePrefs().getBoolean(PREF_TTS_MASTER, true)

    fun setTtsMasterEnabled(on: Boolean) {
        requirePrefs().edit().putBoolean(PREF_TTS_MASTER, on).apply()
        com.example.locationmapapp.util.DebugLogger.i("AudioControl", "TTS master → $on")
        notifyListeners()
    }

    /** True iff engine is initialized AND a TTS-friendly audio output exists. */
    fun isTtsAvailable(): Boolean = engineReady && routeUsable

    /** Pushed by NarrationManager on TTS init success/failure/shutdown. */
    fun setEngineReady(ready: Boolean) {
        if (engineReady == ready) return
        val before = isTtsAvailable()
        engineReady = ready
        com.example.locationmapapp.util.DebugLogger.i("AudioControl", "engineReady → $ready (avail before=$before after=${isTtsAvailable()})")
        if (before != isTtsAvailable()) notifyListeners()
    }

    /** Pushed by SalemMainActivity's AudioDeviceCallback on output device changes. */
    fun setRouteUsable(usable: Boolean) {
        if (routeUsable == usable) return
        val before = isTtsAvailable()
        routeUsable = usable
        com.example.locationmapapp.util.DebugLogger.i("AudioControl", "routeUsable → $usable (avail before=$before after=${isTtsAvailable()})")
        if (before != isTtsAvailable()) notifyListeners()
    }

    /** Effective gate consulted by NarrationManager: master ON AND TTS path usable. */
    fun isTtsEffectivelyOn(): Boolean = isTtsMasterEnabled() && isTtsAvailable()

    // ── Listener registration ───────────────────────────────────────────
    fun addListener(l: ChangeListener)    { if (!listeners.contains(l)) listeners.add(l) }
    fun removeListener(l: ChangeListener) { listeners.remove(l) }
    private fun notifyListeners() { for (l in listeners) l.onAudioControlChanged() }
}
