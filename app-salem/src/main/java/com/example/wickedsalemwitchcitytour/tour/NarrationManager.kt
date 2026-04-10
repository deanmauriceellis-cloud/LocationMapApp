/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.tour

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.content.SalemContentRepository
import com.example.wickedsalemwitchcitytour.content.model.TourPoi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module NarrationManager.kt"

/**
 * NarrationManager — Android TextToSpeech wrapper for guided tour narration.
 *
 * Manages a queue of narration segments (short/long narration, transitions,
 * primary source quotes) and exposes observable state for the UI.
 *
 * Key design points:
 *   - Speech rate 0.9x for historical content (clear, deliberate delivery)
 *   - Queue-based: segments play in order, can be skipped
 *   - Respects phone ringer mode (silent/vibrate = no speech)
 *   - Speed control: 0.75x / 0.9x / 1.0x / 1.25x
 */
@Singleton
class NarrationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: SalemContentRepository
) : TextToSpeech.OnInitListener {

    private val TAG = "NarrationMgr"

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    /** Cached Voice objects by ID for fast switching */
    private val voiceCache = mutableMapOf<String, android.speech.tts.Voice>()

    /** Whether auto-narration is enabled (user toggle). */
    var autoNarrationEnabled: Boolean = true

    /** Current speech rate multiplier. */
    var speechRate: Float = 0.9f
        set(value) {
            field = value
            tts?.setSpeechRate(value)
            DebugLogger.i(TAG, "Speech rate → ${value}x")
        }

    /** Narration segment queue. */
    private val queue = ArrayDeque<NarrationSegment>()

    /** Currently speaking segment. */
    private var currentSegment: NarrationSegment? = null

    private val _state = MutableStateFlow<NarrationState>(NarrationState.Idle)
    val state: StateFlow<NarrationState> = _state.asStateFlow()

    // ── Initialization ──────────────────────────────────────────────────

    fun initialize() {
        if (tts != null) return
        tts = TextToSpeech(context, this)
        DebugLogger.i(TAG, "TTS initializing…")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            ttsReady = result != TextToSpeech.LANG_MISSING_DATA &&
                       result != TextToSpeech.LANG_NOT_SUPPORTED
            tts?.setSpeechRate(speechRate)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    DebugLogger.d(TAG, "TTS start: $utteranceId")
                }

                override fun onDone(utteranceId: String?) {
                    DebugLogger.i(TAG, "TTS done: $utteranceId (queue remaining: ${queue.size})")
                    currentSegment = null
                    playNext()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    DebugLogger.e(TAG, "TTS ERROR: $utteranceId (queue remaining: ${queue.size})")
                    currentSegment = null
                    playNext()
                }
            })

            DebugLogger.i(TAG, "TTS ready — Locale.US, rate=${speechRate}x")

            // Cache voice objects for category-based switching
            val voices = tts?.voices ?: emptySet()
            for (v in voices) {
                if (v.locale.language == "en" && !v.isNetworkConnectionRequired) {
                    voiceCache[v.name] = v
                }
            }
            val needed = CategoryVoiceMap.allUsedVoices
            val found = needed.count { it in voiceCache }
            DebugLogger.i(TAG, "Voice cache: $found/${needed.size} category voices available, ${voiceCache.size} total English offline")
        } else {
            ttsReady = false
            DebugLogger.e(TAG, "TTS init failed: status=$status")
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        DebugLogger.i(TAG, "TTS shutdown")
    }

    // ── Narration API ───────────────────────────────────────────────────

    /** Speak the short approach narration for a POI. */
    fun speakShortNarration(poi: TourPoi) {
        val text = poi.shortNarration ?: return
        enqueue(NarrationSegment(
            id = "short_${poi.id}",
            text = text,
            type = SegmentType.SHORT_NARRATION,
            poiName = poi.name
        ))
    }

    /** Speak the full at-location narration for a POI. */
    fun speakLongNarration(poi: TourPoi) {
        val text = poi.longNarration ?: return
        enqueue(NarrationSegment(
            id = "long_${poi.id}",
            text = text,
            type = SegmentType.LONG_NARRATION,
            poiName = poi.name
        ))
    }

    /** Speak a primary source quote. */
    fun speakQuote(text: String, sourceName: String) {
        enqueue(NarrationSegment(
            id = "quote_${System.currentTimeMillis()}",
            text = text,
            type = SegmentType.QUOTE,
            poiName = sourceName
        ))
    }

    /** Speak a walking transition between stops. */
    fun speakTransition(transitionText: String) {
        enqueue(NarrationSegment(
            id = "transition_${System.currentTimeMillis()}",
            text = transitionText,
            type = SegmentType.TRANSITION,
            poiName = ""
        ))
    }

    /** Speak arbitrary text (for ambient mode hints). */
    fun speakHint(text: String, poiName: String, voiceId: String? = null) {
        DebugLogger.i(TAG, "speakHint called: poi=$poiName voice=${voiceId ?: "default"} ttsReady=$ttsReady text=${text.take(40)}...")
        enqueue(NarrationSegment(
            id = "hint_${System.currentTimeMillis()}",
            text = text,
            type = SegmentType.HINT,
            poiName = poiName,
            voiceId = voiceId
        ))
    }

    /**
     * S113 — Speak arbitrary text with a caller-supplied tag embedded in the
     * utterance id. Lets the caller later cancel segments by tag via
     * [cancelSegmentsWithTag]. Used by PoiDetailSheet to interrupt its own
     * read-through on user click / sheet dismiss without affecting unrelated
     * ambient narration still queued.
     */
    fun speakTaggedHint(tag: String, text: String, poiName: String, voiceId: String? = null) {
        enqueue(NarrationSegment(
            id = "${tag}_${System.nanoTime()}",
            text = text,
            type = SegmentType.HINT,
            poiName = poiName,
            voiceId = voiceId
        ))
    }

    /**
     * S113 — Remove every queued segment whose id starts with the given tag,
     * and if the currently-speaking segment has that tag, stop it and move on
     * to the next non-tagged segment. Unrelated narration continues.
     */
    fun cancelSegmentsWithTag(tag: String) {
        val beforeSize = queue.size
        val iter = queue.iterator()
        while (iter.hasNext()) {
            if (iter.next().id.startsWith(tag)) iter.remove()
        }
        val removedQueued = beforeSize - queue.size

        val current = currentSegment
        if (current != null && current.id.startsWith(tag)) {
            tts?.stop()
            currentSegment = null
            DebugLogger.i(TAG, "cancelSegmentsWithTag($tag): killed current + removed $removedQueued queued")
            playNext()
        } else if (removedQueued > 0) {
            DebugLogger.i(TAG, "cancelSegmentsWithTag($tag): removed $removedQueued queued (current unaffected)")
        }
    }

    // ── Playback Controls ───────────────────────────────────────────────

    fun pause() {
        if (_state.value is NarrationState.Speaking) {
            tts?.stop()
            _state.value = NarrationState.Paused(currentSegment)
            DebugLogger.i(TAG, "Paused")
        }
    }

    fun resume() {
        val state = _state.value
        if (state is NarrationState.Paused) {
            val segment = state.segment
            if (segment != null) {
                speak(segment)
            } else {
                playNext()
            }
        }
    }

    fun stop() {
        tts?.stop()
        queue.clear()
        currentSegment = null
        _state.value = NarrationState.Idle
        DebugLogger.i(TAG, "Stopped — queue cleared")
    }

    /** Skip the current segment and play the next one. */
    fun skip() {
        tts?.stop()
        currentSegment = null
        DebugLogger.i(TAG, "Skipped current segment")
        playNext()
    }

    /** True if TTS is currently producing audio. */
    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    // ── Speed Control ───────────────────────────────────────────────────

    fun setSpeed(rate: Float) {
        speechRate = rate.coerceIn(0.5f, 2.0f)
    }

    fun cycleSpeed(): Float {
        val speeds = listOf(0.75f, 0.9f, 1.0f, 1.25f)
        val currentIndex = speeds.indexOfFirst { Math.abs(it - speechRate) < 0.01f }
        val nextIndex = (currentIndex + 1) % speeds.size
        setSpeed(speeds[nextIndex])
        return speechRate
    }

    // ── Queue Management ────────────────────────────────────────────────

    private fun enqueue(segment: NarrationSegment) {
        if (!ttsReady) {
            DebugLogger.w(TAG, "TTS not ready — skipping: ${segment.id}")
            return
        }
        if (!shouldSpeak()) {
            DebugLogger.d(TAG, "Phone silent/vibrate — skipping: ${segment.id}")
            return
        }

        queue.addLast(segment)
        DebugLogger.i(TAG, "Enqueued: ${segment.type} — ${segment.poiName} (queue: ${queue.size})")

        // If nothing is playing, start immediately
        if (_state.value is NarrationState.Idle) {
            playNext()
        }
    }

    private fun playNext() {
        val next = queue.removeFirstOrNull()
        if (next == null) {
            DebugLogger.i(TAG, "playNext: queue empty → Idle")
            _state.value = NarrationState.Idle
            return
        }
        DebugLogger.i(TAG, "playNext: ${next.type} — ${next.poiName} (${queue.size} remaining)")
        speak(next)
    }

    private fun speak(segment: NarrationSegment) {
        currentSegment = segment
        _state.value = NarrationState.Speaking(segment)

        // Switch to category voice if specified
        val voiceId = segment.voiceId
        if (voiceId != null) {
            val voice = voiceCache[voiceId]
            if (voice != null) {
                tts?.voice = voice
            } else {
                DebugLogger.w(TAG, "Voice not found: $voiceId — using current")
            }
        }

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(segment.text, TextToSpeech.QUEUE_FLUSH, params, segment.id)
        DebugLogger.i(TAG, "Speaking: ${segment.type} — ${segment.poiName} voice=${voiceId ?: "default"}")
    }

    /** Check phone ringer mode — don't speak if silent or vibrate. */
    private fun shouldSpeak(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return true
        return audioManager.ringerMode == AudioManager.RINGER_MODE_NORMAL
    }
}

/** A queued narration segment. */
data class NarrationSegment(
    val id: String,
    val text: String,
    val type: SegmentType,
    val poiName: String,
    /** TTS voice ID to use (null = keep current voice) */
    val voiceId: String? = null
)

enum class SegmentType {
    SHORT_NARRATION,
    LONG_NARRATION,
    QUOTE,
    TRANSITION,
    HINT
}

/** Observable narration state. */
sealed class NarrationState {
    data object Idle : NarrationState()
    data class Speaking(val segment: NarrationSegment?) : NarrationState()
    data class Paused(val segment: NarrationSegment?) : NarrationState()
}
