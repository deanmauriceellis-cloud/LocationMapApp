/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.R
import com.example.wickedsalemwitchcitytour.splash.LocationContext
import com.example.wickedsalemwitchcitytour.splash.LocationContextBuilder
import com.example.wickedsalemwitchcitytour.splash.PlaceKind
import com.example.wickedsalemwitchcitytour.splash.PlaceResolver
import com.example.wickedsalemwitchcitytour.splash.SplashEngine
import com.example.wickedsalemwitchcitytour.splash.SplashTree
import com.example.wickedsalemwitchcitytour.util.SplashVoice
import com.google.android.gms.location.LocationServices
import java.util.concurrent.atomic.AtomicBoolean

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

    private val launched = AtomicBoolean(false)

    companion object {
        const val EXTRA_FROM_SPLASH = "from_splash"
        private const val ANIMATION_DURATION_MS = 5000L
        private const val TEXT_FADE_DELAY_MS = 1200L
        private const val TEXT_FADE_DURATION_MS = 800L
        // Hard safety cap — splash will never hang longer than this, even if the
        // TTS engine never reports onDone (engine crash, missing voice data, etc).
        private const val SPLASH_SAFETY_CAP_MS = 15_000L
        private const val SPLASH_UTTERANCE_ID = "splash_welcome"

        // S231 — splash line is now picked from the bundled splash_tree_v1.json
        // decision tree (authored in the web admin Splash Tree tab). This list
        // is the LAST-RESORT fallback used only if the tree asset fails to
        // load entirely. Keep the legacy line as the catch-all default.
        private val SPLASH_LEGACY_FALLBACK = arrayOf(
            "Welcome to Katrina's Mystic Visitors Guide, Historic Salem Tour App.",
        )

        // Hard cap on how long we wait for FusedLocationProvider's last-known
        // fix before giving up and rendering UNKNOWN. The first-fix path on
        // a cold start has to bind the GMS client and round-trip — observed
        // ~200–400 ms in practice on the Lenovo. 800 ms keeps comfortable
        // headroom; the splash budget waits for TTS onDone (multiple seconds)
        // so this never delays the visible flow.
        private const val LAST_LOCATION_TIMEOUT_MS = 800L

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

        // Speak the welcome line via the process-scoped SplashVoice engine
        // (warmed up in WickedSalemApp.onCreate). Splash does NOT transition on
        // a fixed timer — it waits for the TTS utterance to complete, so the
        // user always hears the full welcome. Safety cap below guarantees the
        // splash never hangs if the engine never reports onDone.
        // S231 — pick from the splash decision tree. Async because we want to
        // grab the last-known GPS fix first (cached, near-instant). Falls back
        // to the legacy line if the tree asset fails to load.
        pickAndSpeak()

        // Slow zoom-in on the Katrina image (Ken Burns-style).
        splashImage.scaleX = 1.0f
        splashImage.scaleY = 1.0f
        splashImage.animate()
            .scaleX(1.15f).scaleY(1.15f)
            .setDuration(ANIMATION_DURATION_MS)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        // Fade in the title text after a short delay.
        textContainer.animate()
            .alpha(1f)
            .setStartDelay(TEXT_FADE_DELAY_MS)
            .setDuration(TEXT_FADE_DURATION_MS)
            .start()

        // Safety cap — fires only if SplashVoice never reports onDone (engine
        // failure, missing voice data, etc). launchMainActivity is idempotent.
        splashImage.postDelayed({
            if (!launched.get()) {
                DebugLogger.e("SplashActivity", "SPLASH_SAFETY_CAP_MS reached — forcing transition")
                launchMainActivity()
            }
        }, SPLASH_SAFETY_CAP_MS)
    }

    /**
     * S231 — splash line picker. Loads the decision tree, fetches the
     * last-known GPS fix (with a tight timeout), resolves the polygon
     * context, picks a bucket + variant, and hands off to SplashVoice.
     *
     * Failure modes — all fall through to the legacy fallback line so the
     * splash still speaks something:
     *   - tree asset missing / corrupt
     *   - location permission not yet granted (fresh install)
     *   - getLastLocation returns null (device just booted)
     *   - SecurityException from gated APIs
     */
    private fun pickAndSpeak() {
        val tree = SplashTree.fromAssets(this)
        if (tree == null) {
            val fallback = SPLASH_LEGACY_FALLBACK.random()
            DebugLogger.w("SplashActivity", "splash tree unavailable → legacy fallback: $fallback")
            speakAndLaunch(fallback)
            return
        }

        val handler = Handler(Looper.getMainLooper())
        val consumed = AtomicBoolean(false)

        val onContextResolved: (LocationContext?) -> Unit = { ctx ->
            if (consumed.compareAndSet(false, true)) {
                val pick = SplashEngine.pick(tree, ctx)
                DebugLogger.i(
                    "SplashActivity",
                    "splash pick: bucket=${pick.bucket?.id ?: "—"} " +
                        "variant=${pick.variant?.id ?: "—"} " +
                        "ctx=${ctx?.let { "place=${it.place} miles=${it.miles} mvmt=${it.movement}" } ?: "null"} " +
                        "→ ${pick.text}"
                )
                speakAndLaunch(pick.text)
            }
        }

        // Timeout: if FusedLocationProvider doesn't return a cached fix in
        // LAST_LOCATION_TIMEOUT_MS, proceed with no context (UNKNOWN bucket).
        val timeoutRunnable = Runnable {
            DebugLogger.w("SplashActivity", "last-location timeout → UNKNOWN bucket")
            onContextResolved(null)
        }
        handler.postDelayed(timeoutRunnable, LAST_LOCATION_TIMEOUT_MS)

        val hasLocationPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            // Fresh install — permission not yet granted. The first launch
            // legitimately has no GPS context; UNKNOWN bucket is the right
            // answer. Skip the timeout race and resolve immediately.
            handler.removeCallbacks(timeoutRunnable)
            onContextResolved(null)
            return
        }

        try {
            val client = LocationServices.getFusedLocationProviderClient(this)
            client.lastLocation
                .addOnSuccessListener { loc ->
                    handler.removeCallbacks(timeoutRunnable)
                    // If timeout already fired the pick, skip the (now wasted)
                    // PlaceResolver call — splash already picked + spoke.
                    if (consumed.get()) return@addOnSuccessListener
                    if (loc == null) {
                        onContextResolved(null)
                        return@addOnSuccessListener
                    }
                    val place = try {
                        PlaceResolver.resolve(this, loc.latitude, loc.longitude)
                    } catch (t: Throwable) {
                        DebugLogger.e("SplashActivity", "PlaceResolver failed: ${t.message}")
                        null
                    }
                    val ctx = LocationContextBuilder.build(
                        loc,
                        place?.takeIf { it.kind != PlaceKind.OFFGRID },
                    )
                    onContextResolved(ctx)
                }
                .addOnFailureListener { e ->
                    handler.removeCallbacks(timeoutRunnable)
                    DebugLogger.w("SplashActivity", "lastLocation failed: ${e.message}")
                    onContextResolved(null)
                }
        } catch (se: SecurityException) {
            // Race: permission may have been revoked between the check and
            // the call. Fall back to UNKNOWN.
            handler.removeCallbacks(timeoutRunnable)
            DebugLogger.w("SplashActivity", "SecurityException on lastLocation: ${se.message}")
            onContextResolved(null)
        }
    }

    private fun speakAndLaunch(line: String) {
        SplashVoice.speak(line, SPLASH_UTTERANCE_ID) {
            DebugLogger.i("SplashActivity", "splash TTS finished → transitioning")
            launchMainActivity()
        }
    }

    private fun launchMainActivity() {
        if (!launched.compareAndSet(false, true)) return
        SplashVoice.shutdown()
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
