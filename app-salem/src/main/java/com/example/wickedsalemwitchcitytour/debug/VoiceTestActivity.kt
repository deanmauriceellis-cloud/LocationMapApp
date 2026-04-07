package com.example.wickedsalemwitchcitytour.debug

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

/**
 * Debug activity for auditioning TTS voices with adjustable effects.
 * Lists all available English voices with human-readable names,
 * per-voice test buttons, pitch/speed sliders, and effect presets.
 */
class VoiceTestActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var selectedVoice: Voice? = null
    private var voices: List<Voice> = emptyList()

    private lateinit var statusText: TextView
    private lateinit var pitchSlider: SeekBar
    private lateinit var speedSlider: SeekBar
    private lateinit var pitchLabel: TextView
    private lateinit var speedLabel: TextView
    private lateinit var voiceContainer: LinearLayout

    private val testPhrase = "Welcome to the Salem Witch Tour, Everyone is invited!"

    private val dp by lazy { resources.displayMetrics.density }
    private fun dp(v: Int) = (v * dp).toInt()

    /** Human-readable names for Google TTS voice codes */
    private val voiceNames = mapOf(
        // en-US voices
        "en-us-x-iob-local" to "US 1 — Female (warm)",
        "en-us-x-iog-local" to "US 2 — Male (deep)",
        "en-us-x-iol-local" to "US 3 — Female (bright)",
        "en-us-x-iom-local" to "US 4 — Male (clear)",
        "en-us-x-sfg-local" to "US 5 — Female (smooth)",
        "en-us-x-tpc-local" to "US 6 — Female (narrator)",
        "en-us-x-tpd-local" to "US 7 — Male (narrator)",
        "en-us-x-tpf-local" to "US 8 — Female (crisp)",
        "en-US-language"     to "US Default",
        // en-GB voices
        "en-gb-x-gba-local" to "UK 1 — Female",
        "en-gb-x-gbb-local" to "UK 2 — Male",
        "en-gb-x-gbc-local" to "UK 3 — Female (soft)",
        "en-gb-x-gbd-local" to "UK 4 — Male (formal)",
        "en-gb-x-gbg-local" to "UK 5 — Female (bright)",
        "en-gb-x-rjs-local" to "UK 6 — Male (warm)",
        "en-GB-language"     to "UK Default",
        // en-AU voices
        "en-au-x-aub-local" to "AU 1 — Female",
        "en-au-x-aud-local" to "AU 2 — Male",
        "en-au-x-auf-local" to "AU 3 — Female (bright)",
        "en-au-x-aug-local" to "AU 4 — Male (relaxed)",
        "en-AU-language"     to "AU Default",
        // en-IN voices
        "en-in-x-end-local" to "India 1 — Female",
        "en-in-x-ene-local" to "India 2 — Male",
        "en-IN-language"     to "India Default",
    )

    private fun friendlyName(voice: Voice): String {
        return voiceNames[voice.name] ?: voice.name
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1a1a2e"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }

        // Title
        column.addView(TextView(this).apply {
            text = "Voice Audition"
            textSize = 24f
            setTextColor(Color.parseColor("#FFD700"))
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })

        // Status
        statusText = TextView(this).apply {
            text = "Initializing TTS..."
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, dp(12))
        }
        column.addView(statusText)

        // Phrase display
        column.addView(TextView(this).apply {
            text = "\"$testPhrase\""
            textSize = 13f
            setTextColor(Color.parseColor("#AAAAAA"))
            setTypeface(null, Typeface.ITALIC)
            setPadding(0, 0, 0, dp(16))
        })

        // ── Pitch slider ──
        pitchLabel = TextView(this).apply {
            text = "Pitch: 1.00x"
            textSize = 14f
            setTextColor(Color.WHITE)
        }
        column.addView(pitchLabel)
        pitchSlider = SeekBar(this).apply {
            max = 200
            progress = 100
            setPadding(0, 0, 0, dp(12))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val v = (progress.coerceAtLeast(25)) / 100f
                    pitchLabel.text = "Pitch: ${"%.2f".format(v)}x"
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        column.addView(pitchSlider)

        // ── Speed slider ──
        speedLabel = TextView(this).apply {
            text = "Speed: 1.00x"
            textSize = 14f
            setTextColor(Color.WHITE)
        }
        column.addView(speedLabel)
        speedSlider = SeekBar(this).apply {
            max = 200
            progress = 100
            setPadding(0, 0, 0, dp(16))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                    val v = (progress.coerceAtLeast(25)) / 100f
                    speedLabel.text = "Speed: ${"%.2f".format(v)}x"
                }
                override fun onStartTrackingTouch(sb: SeekBar) {}
                override fun onStopTrackingTouch(sb: SeekBar) {}
            })
        }
        column.addView(speedSlider)

        // ── Master test + stop buttons ──
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
        }
        btnRow.addView(Button(this).apply {
            text = "TEST"
            textSize = 16f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#FFD700"))
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 2f).apply { marginEnd = dp(8) }
            setOnClickListener { speak() }
        })
        btnRow.addView(Button(this).apply {
            text = "STOP"
            textSize = 14f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC0000"))
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            setOnClickListener { tts?.stop() }
        })
        column.addView(btnRow)

        // ── Presets ──
        column.addView(TextView(this).apply {
            text = "Presets"
            textSize = 16f
            setTextColor(Color.parseColor("#FFD700"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })

        val presetRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(4) }
        }
        val presetRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = dp(16) }
        }
        fun addPreset(row: LinearLayout, label: String, pitch: Int, speed: Int) {
            row.addView(Button(this).apply {
                text = label
                textSize = 11f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#333355"))
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = dp(4) }
                setOnClickListener {
                    pitchSlider.progress = pitch
                    speedSlider.progress = speed
                    speak()
                }
            })
        }
        addPreset(presetRow1, "Normal", 100, 100)
        addPreset(presetRow1, "Deep", 55, 85)
        addPreset(presetRow1, "High", 160, 110)
        addPreset(presetRow1, "Slow", 100, 60)
        addPreset(presetRow2, "Fast", 100, 150)
        addPreset(presetRow2, "Creepy", 50, 65)
        addPreset(presetRow2, "Whisper", 80, 70)
        addPreset(presetRow2, "Booming", 40, 80)
        column.addView(presetRow1)
        column.addView(presetRow2)

        // ── Voice list ──
        column.addView(TextView(this).apply {
            text = "Voices (tap to select + hear)"
            textSize = 16f
            setTextColor(Color.parseColor("#FFD700"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })

        voiceContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        column.addView(voiceContainer)

        root.addView(column)
        setContentView(root)

        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            statusText.text = "TTS init failed"
            return
        }

        tts?.language = Locale.US

        val allVoices = tts?.voices ?: emptySet()
        voices = allVoices
            .filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
            .sortedWith(compareBy({ it.locale.country }, { it.name }))

        statusText.text = "${voices.size} offline English voices"
        populateVoiceButtons()
    }

    private fun populateVoiceButtons() {
        voiceContainer.removeAllViews()

        var currentCountry = ""
        var voiceNum = 0

        for (voice in voices) {
            val country = voice.locale.displayCountry
            if (country != currentCountry) {
                currentCountry = country
                voiceNum = 0
                voiceContainer.addView(TextView(this).apply {
                    text = "── $country (${voice.locale.country}) ──"
                    textSize = 14f
                    setTextColor(Color.parseColor("#FFD700"))
                    setTypeface(null, Typeface.BOLD)
                    setPadding(0, dp(12), 0, dp(6))
                })
            }
            voiceNum++

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(6), dp(8), dp(6))
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    bottomMargin = dp(2)
                }
            }

            // Friendly name
            row.addView(TextView(this).apply {
                text = friendlyName(voice)
                textSize = 14f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.5f)
            })

            // Technical code (small)
            row.addView(TextView(this).apply {
                text = voice.name
                textSize = 9f
                setTextColor(Color.parseColor("#555555"))
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            })

            // Test button
            row.addView(Button(this).apply {
                text = "\u25B6"  // play triangle
                textSize = 16f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#4CAF50"))
                layoutParams = LinearLayout.LayoutParams(dp(50), dp(40))
                setOnClickListener {
                    selectedVoice = voice
                    statusText.text = "Selected: ${friendlyName(voice)}"
                    highlightSelected(voice)
                    speak()
                }
            })

            row.tag = voice.name
            voiceContainer.addView(row)
        }
    }

    private fun highlightSelected(voice: Voice) {
        for (i in 0 until voiceContainer.childCount) {
            val child = voiceContainer.getChildAt(i)
            if (child is LinearLayout) {
                val isSelected = child.tag == voice.name
                child.setBackgroundColor(
                    if (isSelected) Color.parseColor("#1a3a1a") else Color.TRANSPARENT
                )
            }
        }
    }

    private fun speak() {
        val engine = tts ?: return
        engine.stop()

        selectedVoice?.let { engine.voice = it }
        engine.setPitch(pitchSlider.progress.coerceAtLeast(25) / 100f)
        engine.setSpeechRate(speedSlider.progress.coerceAtLeast(25) / 100f)

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        engine.speak(testPhrase, TextToSpeech.QUEUE_FLUSH, params, "voice_test")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
