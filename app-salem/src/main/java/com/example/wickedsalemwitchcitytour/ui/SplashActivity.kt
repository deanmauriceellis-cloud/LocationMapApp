/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.R

@Suppress("unused")
private const val MODULE_ID = "(C) Dean Maurice Ellis, 2026 - Module SplashActivity.kt"

/**
 * SplashActivity — Branded launch screen for Katrina's Mystic Visitors Guide.
 *
 * Flow: Katrina image with slow zoom → title "Katrina's Mystic Visitors Guide" +
 * subtitle "Historic Salem Tour App" fade-in → crossfade to SalemMainActivity
 * with EXTRA_FROM_SPLASH flag so the map performs the cinematic zoom-in.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null

    companion object {
        const val EXTRA_FROM_SPLASH = "from_splash"
        private const val ANIMATION_DURATION_MS = 5000L
        private const val TEXT_FADE_DELAY_MS = 1200L
        private const val TEXT_FADE_DURATION_MS = 800L

        // Twelve Katrina mood variants — one is picked at random on every
        // launch so the app feels alive across sessions. Add to this list
        // (and drop the matching drawable-nodpi/ JPEG) to expand the pool.
        private val KATRINA_SPLASHES = intArrayOf(
            R.drawable.splash_katrina_01,
            R.drawable.splash_katrina_02,
            R.drawable.splash_katrina_03,
            R.drawable.splash_katrina_04,
            R.drawable.splash_katrina_05,
            R.drawable.splash_katrina_06,
            R.drawable.splash_katrina_07,
            R.drawable.splash_katrina_08,
            R.drawable.splash_katrina_09,
            R.drawable.splash_katrina_10,
            R.drawable.splash_katrina_11,
            R.drawable.splash_katrina_12,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugLogger.i("SplashActivity", "onCreate() — launching branded splash")

        // Full-screen immersive
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        setContentView(R.layout.activity_splash)

        val splashImage = findViewById<ImageView>(R.id.splashImage)
        val textContainer = findViewById<android.view.View>(R.id.splashTextContainer)

        val pickedSplash = KATRINA_SPLASHES.random()
        splashImage.setImageResource(pickedSplash)
        DebugLogger.i("SplashActivity", "Katrina splash picked: resId=$pickedSplash (pool size=${KATRINA_SPLASHES.size})")

        // Play voiceover audio — always on per S145 operator direction
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.splash_voiceover)?.apply {
                setVolume(1.0f, 1.0f)
                start()
            }
            DebugLogger.i("SplashActivity", "Splash voiceover playing")
        } catch (e: Exception) {
            DebugLogger.e("SplashActivity", "Voiceover failed", e)
        }

        // Slow zoom-in on the Katrina image (Ken Burns-style)
        splashImage.scaleX = 1.0f
        splashImage.scaleY = 1.0f
        splashImage.animate()
            .scaleX(1.15f).scaleY(1.15f)
            .setDuration(ANIMATION_DURATION_MS)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        // Fade in the title text after a short delay
        textContainer.animate()
            .alpha(1f)
            .setStartDelay(TEXT_FADE_DELAY_MS)
            .setDuration(TEXT_FADE_DURATION_MS)
            .start()

        // When animation finishes, transition to main activity
        splashImage.postDelayed({
            launchMainActivity()
        }, ANIMATION_DURATION_MS)
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    private fun launchMainActivity() {
        mediaPlayer?.release()
        mediaPlayer = null
        DebugLogger.i("SplashActivity", "Splash complete → launching SalemMainActivity")
        val intent = Intent(this, SalemMainActivity::class.java).apply {
            putExtra(EXTRA_FROM_SPLASH, true)
        }
        startActivity(intent)

        // Crossfade transition
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    // Prevent back button from cancelling splash
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Intentionally empty — splash cannot be dismissed
    }
}
