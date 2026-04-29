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
import com.example.wickedsalemwitchcitytour.audio.AudioControl
import com.example.wickedsalemwitchcitytour.audio.NarrationHistory
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

    /**
     * S141: set to true immediately before any intentional `tts.stop()` call
     * (cancel, pause, skip, shutdown). Android TTS fires `onError` on the
     * aborted utterance; without this flag the error handler can't tell
     * "we aborted it" from "TTS actually failed" and every cancel logged at
     * E-level. Set-and-reset inside the one thread owning the TTS listener.
     */
    @Volatile private var intentionallyStopping: Boolean = false

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
                    // S193: the chunker (line ~495 in speak()) submits one
                    // tts.speak per chunk plus one playSilentUtterance per
                    // inter-chunk gap. onDone fires for EVERY utteranceId —
                    // chunk sub-ids ("$id-c$n") and pause sub-ids ("$id-p$n")
                    // included. Only treat the segment as finished when the
                    // LAST chunk fires, which by design carries the bare
                    // segment.id. Intermediate ids must NOT clear
                    // currentSegment or call playNext, otherwise the app
                    // flips to Idle mid-narration and any new enqueue (e.g.
                    // 1692 newspaper from the silence-fill observer)
                    // QUEUE_FLUSHes the rest of the POI chunks. Demo bug.
                    val seg = currentSegment
                    if (seg == null || utteranceId != seg.id) {
                        DebugLogger.d(TAG, "TTS chunk done: $utteranceId (mid-segment)")
                        return
                    }
                    DebugLogger.i(TAG, "TTS done: $utteranceId (queue remaining: ${queue.size})")
                    currentSegment = null
                    playNext()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    val flushed = intentionallyStopping
                    if (flushed) intentionallyStopping = false
                    val seg = currentSegment
                    // S193: same chunk-aware gate as onDone. Errors on
                    // intermediate chunk/pause ids do NOT end the segment;
                    // only an intentional stop OR an error on the final
                    // chunk advances. Without this gate a transient TTS
                    // glitch on chunk 1 wipes currentSegment and the silence-
                    // fill observer immediately steals the queue.
                    if (!flushed && seg != null && utteranceId != seg.id) {
                        DebugLogger.w(TAG, "TTS chunk error: $utteranceId (mid-segment, ignored)")
                        return
                    }
                    if (flushed) {
                        DebugLogger.i(TAG, "TTS cancelled: $utteranceId (queue remaining: ${queue.size})")
                    } else {
                        DebugLogger.e(TAG, "TTS ERROR: $utteranceId (queue remaining: ${queue.size})")
                    }
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

            // S168 — flush any segments that were enqueued during the engine
            // bind window. Without this kick, the queue sits on a ready engine
            // forever because the deferred-enqueue path above no longer calls
            // playNext(). Race window is usually <50ms but can be seconds on
            // cold-boot if the TTS service was evicted.
            if (queue.isNotEmpty() && _state.value is NarrationState.Idle) {
                DebugLogger.i(TAG, "Flushing ${queue.size} deferred segment(s) now that TTS is ready")
                playNext()
            }
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

    /**
     * Pick the narration text variant based on [AudioControl.detailLevel].
     * S145 Q6 Option 1 — graceful fallback: Deep prefers long, falls back to short, falls back to name.
     */
    private fun pickVariantText(poi: TourPoi): String = when (AudioControl.detailLevel()) {
        AudioControl.DetailLevel.BRIEF    -> poi.name
        AudioControl.DetailLevel.STANDARD -> poi.shortNarration ?: poi.name
        AudioControl.DetailLevel.DEEP     -> poi.longNarration ?: poi.shortNarration ?: poi.name
    }

    /** Speak the short approach narration for a POI. */
    fun speakShortNarration(poi: TourPoi) {
        val text = pickVariantText(poi)
        enqueue(NarrationSegment(
            id = "short_${poi.id}",
            text = text,
            type = SegmentType.SHORT_NARRATION,
            poiName = poi.name,
            kind = NarrationKind.POI,
            category = poi.category,
            refId = poi.id
        ))
    }

    /** Speak the full at-location narration for a POI. */
    fun speakLongNarration(poi: TourPoi) {
        val text = pickVariantText(poi)
        enqueue(NarrationSegment(
            id = "long_${poi.id}",
            text = text,
            type = SegmentType.LONG_NARRATION,
            poiName = poi.name,
            kind = NarrationKind.POI,
            category = poi.category,
            refId = poi.id
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
    fun speakHint(text: String, poiName: String, voiceId: String? = null, category: String? = null) {
        DebugLogger.i(TAG, "speakHint called: poi=$poiName voice=${voiceId ?: "default"} ttsReady=$ttsReady text=${text.take(40)}...")
        enqueue(NarrationSegment(
            id = "hint_${System.currentTimeMillis()}",
            text = text,
            type = SegmentType.HINT,
            poiName = poiName,
            voiceId = voiceId,
            kind = if (category != null) NarrationKind.POI else null,
            category = category
        ))
    }

    /**
     * S113 — Speak arbitrary text with a caller-supplied tag embedded in the
     * utterance id. Lets the caller later cancel segments by tag via
     * [cancelSegmentsWithTag]. Used by PoiDetailSheet to interrupt its own
     * read-through on user click / sheet dismiss without affecting unrelated
     * ambient narration still queued.
     */
    fun speakTaggedHint(tag: String, text: String, poiName: String, voiceId: String? = null, category: String? = null, userInitiated: Boolean = false) {
        // S145 — infer kind from tag prefix (Witch-Trials / newspaper / oracle = ORACLE;
        // sheet_* / poi_* = POI). Unknown tags fall through (no gate).
        // S197 — userInitiated=true bypasses AudioControl group gates so an
        // explicit Speak-button tap inside Witch-Trials / POI-sheet always
        // narrates regardless of the speaker-menu toggles (which gate the
        // ambient/auto-triggered queue, not user taps).
        val inferredKind = inferKindFromTag(tag)
        enqueue(NarrationSegment(
            id = "${tag}_${System.nanoTime()}",
            text = text,
            type = SegmentType.HINT,
            poiName = poiName,
            voiceId = voiceId,
            kind = inferredKind,
            category = category,
            refId = tag,
            userInitiated = userInitiated
        ))
    }

    /**
     * S150 field-test fix: full-body narration segment (distinct from [speakTaggedHint]
     * which was mistakenly used for both the short orientation line AND the
     * body text, causing every utterance to be tagged `SegmentType.HINT`).
     * Use for historical body content so history panel / nav cluster
     * classification, and any future body-vs-hint gating, behave correctly.
     */
    fun speakTaggedNarration(tag: String, text: String, poiName: String, voiceId: String? = null, category: String? = null, userInitiated: Boolean = false) {
        val inferredKind = inferKindFromTag(tag)
        enqueue(NarrationSegment(
            id = "${tag}_${System.nanoTime()}",
            text = text,
            type = SegmentType.LONG_NARRATION,
            poiName = poiName,
            voiceId = voiceId,
            kind = inferredKind,
            category = category,
            refId = tag,
            userInitiated = userInitiated
        ))
    }

    private fun inferKindFromTag(tag: String): NarrationKind? = when {
        tag.startsWith("bio_") || tag.startsWith("newspaper_") ||
        tag.startsWith("oracle_") || tag.startsWith("witchtrials_") -> NarrationKind.ORACLE
        tag.startsWith("sheet_") || tag.startsWith("poi_")         -> NarrationKind.POI
        else -> null
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
            intentionallyStopping = true
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
            intentionallyStopping = true
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
        intentionallyStopping = true
        tts?.stop()
        queue.clear()
        currentSegment = null
        _state.value = NarrationState.Idle
        DebugLogger.i(TAG, "Stopped — queue cleared")
    }

    /** Skip the current segment and play the next one. */
    fun skip() {
        intentionallyStopping = true
        tts?.stop()
        currentSegment = null
        DebugLogger.i(TAG, "Skipped current segment")
        playNext()
    }

    /**
     * S145 #45 — Replay a [NarrationHistory.Entry] (First / Prev / Next nav buttons).
     * Stops current playback, clears the queue, and speaks the requested entry
     * without re-pushing it to history (isReplay=true).
     */
    fun replayHistoryEntry(entry: com.example.wickedsalemwitchcitytour.audio.NarrationHistory.Entry) {
        intentionallyStopping = true
        tts?.stop()
        queue.clear()
        currentSegment = null
        val segment = NarrationSegment(
            id = "replay_${System.nanoTime()}",
            text = entry.text,
            type = SegmentType.HINT,
            poiName = entry.title,
            voiceId = entry.voiceId,
            kind = when (entry.kind) {
                com.example.wickedsalemwitchcitytour.audio.NarrationHistory.Kind.POI    -> NarrationKind.POI
                com.example.wickedsalemwitchcitytour.audio.NarrationHistory.Kind.ORACLE -> NarrationKind.ORACLE
            },
            refId = entry.refId,
            isReplay = true
        )
        DebugLogger.i(TAG, "Replay from history: ${entry.kind} — ${entry.title}")
        speak(segment)
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
        if (!shouldSpeak()) {
            DebugLogger.d(TAG, "Phone silent/vibrate — skipping: ${segment.id}")
            return
        }

        // S145 #45 — AudioControl group/oracle gate.
        // S197 — explicit user-initiated taps bypass the gate; the speaker-menu
        // toggles are intended to mute AMBIENT / auto-triggered narration only,
        // not the in-screen Speak button (Witch-Trials article, POI sheet).
        if (!segment.userInitiated) {
            when (segment.kind) {
                NarrationKind.POI -> {
                    if (!AudioControl.isPoiSpeechEnabled(segment.category)) {
                        DebugLogger.d(TAG, "AudioControl gate: POI group muted (cat=${segment.category}) — dropping ${segment.id}")
                        return
                    }
                }
                NarrationKind.ORACLE -> {
                    if (!AudioControl.isOracleSpeechEnabled()) {
                        DebugLogger.d(TAG, "AudioControl gate: Oracle muted — dropping ${segment.id}")
                        return
                    }
                }
                null -> { /* legacy / transition / quote — no gate */ }
            }
        }

        queue.addLast(segment)
        DebugLogger.i(TAG, "Enqueued: ${segment.type} — ${segment.poiName} (queue: ${queue.size})")

        // S168 — if the TTS engine hasn't finished binding yet (first-POI
        // race: the splash can fire a geofence ENTRY ~10ms before the engine
        // reports SUCCESS), leave the segment in the queue. onInit flushes
        // the queue once it's ready. Previously this path DROPPED the
        // segment, which worked only because callers enqueued two back-to-back
        // clips per POI (hint + body). After the S168 "one narration per
        // visit" collapse, a dropped first segment means silence.
        if (!ttsReady) {
            DebugLogger.i(TAG, "TTS not ready yet — deferring ${segment.id} (queue: ${queue.size})")
            return
        }

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

        // S145 #45 — push to rolling history on real narration dispatch (not re-plays).
        if (segment.kind != null && !segment.isReplay) {
            NarrationHistory.add(NarrationHistory.Entry(
                kind = when (segment.kind) {
                    NarrationKind.POI    -> NarrationHistory.Kind.POI
                    NarrationKind.ORACLE -> NarrationHistory.Kind.ORACLE
                },
                title = segment.poiName,
                text = segment.text,
                refId = segment.refId,
                voiceId = segment.voiceId
            ))
        }

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
        // S192: chunk on punctuation so the TTS engine handles long narration
        // gracefully and we get natural pauses between phrases. Last chunk
        // carries segment.id so the OnUtteranceCompleted callback still fires
        // exactly once at the end. Earlier chunks use derived ids.
        val chunks = chunkOnPunctuation(segment.text)
        if (chunks.size <= 1) {
            tts?.speak(segment.text, TextToSpeech.QUEUE_FLUSH, params, segment.id)
        } else {
            chunks.forEachIndexed { i, pair ->
                val (chunk, pauseAfterMs) = pair
                val isFirst = i == 0
                val isLast = i == chunks.size - 1
                val mode = if (isFirst) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val uid = if (isLast) segment.id else "${segment.id}-c$i"
                tts?.speak(chunk, mode, params, uid)
                if (!isLast && pauseAfterMs > 0L) {
                    tts?.playSilentUtterance(pauseAfterMs, TextToSpeech.QUEUE_ADD, "${segment.id}-p$i")
                }
            }
        }
        DebugLogger.i(TAG, "Speaking: ${segment.type} — ${segment.poiName} voice=${voiceId ?: "default"} chunks=${chunks.size}")
    }

    /**
     * S192 smart punctuation chunker. Two-stage:
     *   1. Split text into sentences at `.` `!` `?` followed by whitespace,
     *      with guards for abbreviations ("Mr. Hawthorne", "U.S.", "i.e."),
     *      single-letter initials ("J. R. R."), and numbers (3.14).
     *   2. For each sentence longer than TARGET_CHUNK_CHARS, sub-split at
     *      `;` `:` `,` and quote boundaries, but only when the partial chunk
     *      is at least MIN_SUB_CHARS so tight phrases like "Salem, Massachusetts"
     *      stay intact.
     *   3. Merge any chunk pair where BOTH are tiny (< MIN_KEEP_CHARS) so we
     *      don't ship dribbles to the engine.
     *
     * Returns (chunk, pauseAfterMs) pairs. Last chunk has pause=0L.
     */
    private fun chunkOnPunctuation(text: String): List<Pair<String, Long>> {
        val sentences = splitIntoSentences(text)
        val raw = mutableListOf<Pair<String, Long>>()
        for ((idx, sent) in sentences.withIndex()) {
            val isLastSent = idx == sentences.lastIndex
            val pauseAfter = if (isLastSent) 0L else PAUSE_PERIOD
            if (sent.length <= TARGET_CHUNK_CHARS) {
                raw.add(sent to pauseAfter)
            } else {
                val subs = subdivideLongSentence(sent)
                for ((j, sub) in subs.withIndex()) {
                    val subLast = j == subs.lastIndex
                    val p = if (subLast) pauseAfter else sub.second
                    raw.add(sub.first to p)
                }
            }
        }
        return mergeTinyChunks(raw)
    }

    private fun splitIntoSentences(text: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            sb.append(c)
            if (c == '.' || c == '!' || c == '?' || c == '…') {
                // S197: a closing quote may sit between the terminator and the
                // space (`said." Then he left.`). Peek past it when locating
                // the boundary so quoted dialog doesn't merge with the next
                // sentence.
                var k = i + 1
                val quoteFollows = k < text.length &&
                    (text[k] == '"' || text[k] == '\'' || text[k] == '”' || text[k] == '’')
                if (quoteFollows) k++
                val nextIsBoundary = k >= text.length || text[k].isWhitespace()
                if (nextIsBoundary && !isAbbreviation(sb, c) && !isNumberPeriod(text, i, c)) {
                    if (quoteFollows) sb.append(text[i + 1])
                    val sent = sb.toString().trim()
                    if (sent.isNotEmpty()) out.add(sent)
                    sb.clear()
                    if (quoteFollows) i++
                    while (i + 1 < text.length && text[i + 1].isWhitespace()) i++
                }
            }
            i++
        }
        val tail = sb.toString().trim()
        if (tail.isNotEmpty()) out.add(tail)
        return out
    }

    /** True if the period at end of `sb` is part of a known abbreviation or initial. */
    private fun isAbbreviation(sb: StringBuilder, term: Char): Boolean {
        if (term != '.') return false
        // Get the last word (alpha chars before the period)
        val s = sb.toString()
        if (s.length < 2) return false
        // Walk backwards from the period to find the start of the token
        var k = s.length - 2
        while (k >= 0 && (s[k].isLetter())) k--
        val tokStart = k + 1
        val tok = s.substring(tokStart, s.length - 1).lowercase()
        if (tok.isEmpty()) return false
        if (tok in ABBREVIATIONS) return true
        // Single-letter initial like "J." (preceded by whitespace, start, or another initial)
        if (tok.length == 1 && tokStart > 0) {
            val prev = s[tokStart - 1]
            if (prev.isWhitespace() || prev == '.') return true
        }
        if (tok.length == 1 && tokStart == 0) return true
        return false
    }

    /** True if the period is between digits (e.g. "3.14"). */
    private fun isNumberPeriod(text: String, i: Int, term: Char): Boolean {
        if (term != '.') return false
        val prev = if (i > 0) text[i - 1] else ' '
        val next = if (i + 1 < text.length) text[i + 1] else ' '
        return prev.isDigit() && next.isDigit()
    }

    /**
     * Sub-split a long sentence at `;` `:` `,` or closing quotes. Only splits
     * when the running chunk has reached MIN_SUB_CHARS so we don't fragment
     * "He said," from its quoted payload.
     */
    private fun subdivideLongSentence(sent: String): List<Pair<String, Long>> {
        val out = mutableListOf<Pair<String, Long>>()
        val sb = StringBuilder()
        var i = 0
        while (i < sent.length) {
            val c = sent[i]
            sb.append(c)
            val pause = when (c) {
                ',' -> PAUSE_COMMA
                ';' -> PAUSE_SEMI
                ':' -> PAUSE_COLON
                '"', '”', '’' -> PAUSE_QUOTE
                '—', '–' -> PAUSE_DASH       // S197 em-/en-dash mid-sentence beat
                '…' -> PAUSE_ELLIPSIS        // S197 mid-sentence ellipsis
                else -> 0L
            }
            if (pause > 0L && sb.length >= MIN_SUB_CHARS) {
                val nextIsBoundary = i + 1 >= sent.length || sent[i + 1].isWhitespace()
                if (nextIsBoundary) {
                    val chunk = sb.toString().trim()
                    if (chunk.isNotEmpty()) out.add(chunk to pause)
                    sb.clear()
                    while (i + 1 < sent.length && sent[i + 1].isWhitespace()) i++
                }
            }
            i++
        }
        val tail = sb.toString().trim()
        if (tail.isNotEmpty()) out.add(tail to 0L)
        return out
    }

    /** Merge an adjacent pair when BOTH halves are below MIN_KEEP_CHARS. */
    private fun mergeTinyChunks(chunks: List<Pair<String, Long>>): List<Pair<String, Long>> {
        if (chunks.size < 2) return chunks
        val out = mutableListOf<Pair<String, Long>>()
        for (pair in chunks) {
            val (text, pause) = pair
            val prev = out.lastOrNull()
            if (prev != null && prev.first.length < MIN_KEEP_CHARS && text.length < MIN_KEEP_CHARS) {
                out[out.lastIndex] = "${prev.first} $text" to pause
            } else {
                out.add(pair)
            }
        }
        return out
    }

    companion object {
        private const val PAUSE_COMMA    = 100L
        private const val PAUSE_SEMI     = 200L
        private const val PAUSE_COLON    = 200L
        private const val PAUSE_PERIOD   = 300L
        private const val PAUSE_QUOTE    = 200L
        private const val PAUSE_DASH     = 200L   // S197 em-/en-dash
        private const val PAUSE_ELLIPSIS = 300L   // S197

        private const val TARGET_CHUNK_CHARS = 280   // sentence kept whole if ≤ this
        private const val MIN_SUB_CHARS      = 40    // S197: was 60 — earlier commas now get a beat
        private const val MIN_KEEP_CHARS     = 30    // pairs both shorter than this get merged

        // Lower-case words that, when followed by ".", do NOT end a sentence.
        // S197: expanded with street/road, additional honorifics, era, state,
        // and academic abbreviations common in Salem narration.
        private val ABBREVIATIONS = setOf(
            // titles
            "mr", "mrs", "ms", "dr", "jr", "sr",
            "capt", "rev", "hon", "gov", "pres", "maj", "gen", "lt", "sgt",
            "adm", "col", "cpl", "pvt", "sen", "rep", "prof", "fr", "br",
            "esq", "phd", "md",
            // company / academic
            "co", "inc", "ltd", "corp", "vol", "no", "pp", "ed", "et", "al",
            // street / locality
            "st", "ave", "blvd", "rd", "ln", "mt", "ft",
            // era
            "bc", "ad", "ca",
            // states / regions
            "mass", "conn", "vt", "calif", "fla", "penn", "pa",
            // misc shorthand
            "etc", "vs", "approx", "fig",
            // multi-period acronyms
            "u.s", "u.k", "i.e", "e.g"
        )
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
    val voiceId: String? = null,
    /** S145 #45 — kind drives AudioControl gating (POI → category-group; ORACLE → oracle toggle). */
    val kind: NarrationKind? = null,
    /** S145 #45 — POI category for group mapping (null = no gate). */
    val category: String? = null,
    /** S145 #45 — POI id or Oracle tag identifier, for NarrationHistory reference. */
    val refId: String? = null,
    /** S145 #45 — replays from NarrationHistory skip re-adding themselves back to history. */
    val isReplay: Boolean = false,
    /** S197 — explicit user tap (Speak button) bypasses AudioControl group/oracle gates. */
    val userInitiated: Boolean = false
)

enum class NarrationKind { POI, ORACLE }

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
