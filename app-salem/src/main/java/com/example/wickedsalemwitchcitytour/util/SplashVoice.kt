/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.locationmapapp.util.DebugLogger
import java.util.Locale

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module SplashVoice.kt"

/**
 * S149: process-scoped TTS that warms up inside Application.onCreate so the
 * splash screen can speak immediately instead of paying the ~3–4s engine bind
 * latency against its 5s animation budget. SplashActivity holds the visual
 * splash until onDone fires (or a safety cap elapses).
 *
 * Lifecycle:
 *   - [initEarly] is called from WickedSalemApp.onCreate — triggers TTS engine
 *     binding and locale/rate/pitch setup in the background.
 *   - [speak] queues the welcome line. If the engine is already ready it plays
 *     at once; otherwise the request is deferred until init finishes.
 *   - onDone / onError from UtteranceProgressListener invokes the caller's
 *     onFinished callback on the main thread so the caller can proceed.
 *   - [shutdown] releases the engine — called by SplashActivity once the
 *     splash hands off to the main activity, since the welcome line never
 *     needs to replay within a single process lifetime.
 */
object SplashVoice {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var state: State = State.NOT_STARTED

    // One pending speak request. Splash only ever issues one, so no queue needed.
    private var pendingText: String? = null
    private var pendingUtteranceId: String? = null
    private var pendingOnFinished: (() -> Unit)? = null

    private enum class State { NOT_STARTED, INITIALIZING, READY, FAILED }

    @Synchronized
    fun initEarly(context: Context) {
        if (state != State.NOT_STARTED) return
        state = State.INITIALIZING
        DebugLogger.i("SplashVoice", "initEarly — binding TTS engine")
        tts = TextToSpeech(context.applicationContext) { status ->
            onInitComplete(status)
        }
    }

    @Synchronized
    private fun onInitComplete(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            DebugLogger.e("SplashVoice", "TTS init failed (status=$status)")
            state = State.FAILED
            firePendingFinished()
            return
        }
        tts?.apply {
            language = Locale.US
            setSpeechRate(0.95f)
            setPitch(1.05f)
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    DebugLogger.i("SplashVoice", "speaking — utteranceId=$utteranceId")
                }
                override fun onDone(utteranceId: String?) {
                    DebugLogger.i("SplashVoice", "onDone — utteranceId=$utteranceId")
                    firePendingFinished()
                }
                @Deprecated("required override on older API levels")
                override fun onError(utteranceId: String?) {
                    DebugLogger.e("SplashVoice", "onError — utteranceId=$utteranceId")
                    firePendingFinished()
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    DebugLogger.e("SplashVoice", "onError — utteranceId=$utteranceId code=$errorCode")
                    firePendingFinished()
                }
            })
        }
        state = State.READY
        DebugLogger.i("SplashVoice", "TTS engine ready")
        val text = pendingText
        val id = pendingUtteranceId
        if (text != null && id != null) {
            pendingText = null
            pendingUtteranceId = null
            DebugLogger.i("SplashVoice", "flushing deferred speak — $text")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        }
    }

    /**
     * Speak [text]. [onFinished] is invoked on the main thread when the utterance
     * completes, errors, or when the engine has already failed to initialize. It
     * is invoked at most once per [speak] call.
     */
    @Synchronized
    fun speak(text: String, utteranceId: String, onFinished: () -> Unit) {
        pendingOnFinished = onFinished
        when (state) {
            State.READY -> {
                DebugLogger.i("SplashVoice", "speak (ready) — $text")
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            }
            State.INITIALIZING, State.NOT_STARTED -> {
                DebugLogger.i("SplashVoice", "speak (deferred, engine not ready) — $text")
                pendingText = text
                pendingUtteranceId = utteranceId
            }
            State.FAILED -> {
                DebugLogger.e("SplashVoice", "speak ignored — engine previously failed to init")
                firePendingFinished()
            }
        }
    }

    @Synchronized
    fun shutdown() {
        DebugLogger.i("SplashVoice", "shutdown")
        tts?.apply {
            stop()
            shutdown()
        }
        tts = null
        state = State.NOT_STARTED
        pendingText = null
        pendingUtteranceId = null
        pendingOnFinished = null
    }

    private fun firePendingFinished() {
        val cb = pendingOnFinished
        pendingOnFinished = null
        if (cb != null) mainHandler.post { cb() }
    }
}
