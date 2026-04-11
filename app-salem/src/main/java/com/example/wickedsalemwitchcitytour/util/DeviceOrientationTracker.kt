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
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.example.locationmapapp.util.DebugLogger

/**
 * S115: Listens to the device's fused rotation vector sensor and exposes a
 * compass azimuth in degrees (0=N, 90=E, 180=S, 270=W). Used by the
 * heading-up map rotation in SalemMainActivityTour to fall back on the
 * physical tablet orientation when the user is stationary and the
 * GPS-derived movement bearing has gone stale.
 *
 * ## Sensor selection priority
 *
 *  1. `TYPE_ROTATION_VECTOR` — 9-axis fusion (gyro + accel + mag) done by
 *     the sensor HAL. Best quality, includes magnetic-drift correction.
 *     This is the preferred source. Verified present on the Lenovo
 *     TB305FU via `dumpsys sensorservice` (MTK provider, 50-200 Hz).
 *
 *  2. `TYPE_GEOMAGNETIC_ROTATION_VECTOR` — mag + accel fusion, no gyro.
 *     Noisier but doesn't drift. Used if no rotation vector is available.
 *     Also present on the Lenovo.
 *
 *  3. Neither — [hasSensor] returns false, [currentAzimuthDeg] stays null
 *     forever, and the caller should leave heading-up on GPS bearing only.
 *
 * ## Display rotation handling
 *
 * The raw rotation vector is in device coordinates. We use
 * [SensorManager.remapCoordinateSystem] to transform it into world
 * coordinates relative to the current display orientation, so the azimuth
 * is meaningful regardless of whether the tablet is held in portrait,
 * landscape, reverse portrait, or reverse landscape. The display rotation
 * is queried at each sample via the [getDisplayRotation] lambda, so it
 * stays correct even after configuration changes.
 *
 * ## Threading
 *
 * `registerListener` is called with a main-looper Handler, so
 * [onSensorChanged] runs on the main thread. This lets the [onUpdate]
 * callback safely touch the MapView without a dispatch hop.
 *
 * ## Battery
 *
 * `SENSOR_DELAY_UI` is ~60ms. The actual power cost on this class of
 * tablet is under 3 mW — negligible next to GPS, TTS, and the screen.
 * The caller should still start/stop the tracker with the heading-up
 * toggle and with `onResume`/`onPause` so the sensor isn't running when
 * nothing needs the data.
 */
class DeviceOrientationTracker(
    context: Context,
    private val getDisplayRotation: () -> Int,
    private val onUpdate: () -> Unit,
    /**
     * S115: Callback returning the user's current (lat, lng) or null if no
     * GPS fix yet. Used to apply magnetic declination correction so the
     * exposed azimuth is relative to TRUE north (matching Google Maps,
     * street signs, and paper maps) instead of magnetic north.
     */
    private val getUserLocation: () -> Pair<Double, Double>? = { null },
    private val onAccuracyChanged: ((Int) -> Unit)? = null
) : SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /** The best available rotation sensor. Null if the device exposes neither. */
    private val rotationSensor: Sensor?

    /** Human-readable label for logs / diagnostics. */
    private val sourceLabel: String

    @Volatile
    var currentAzimuthDeg: Double? = null
        private set

    @Volatile
    var lastUpdateMs: Long = 0L
        private set

    /**
     * Current accuracy reported by the sensor:
     *   -1 = no reading yet, 0 = unreliable, 1 = low, 2 = medium, 3 = high.
     * Exposed mostly so the caller can flag "please do the figure-8 dance"
     * UX if the accuracy ever drops below medium.
     */
    @Volatile
    var currentAccuracy: Int = -1
        private set

    /**
     * S115: "Static mode" detection. When consecutive raw samples stay
     * within [STATIC_SAMPLE_THRESHOLD_DEG] for at least
     * [STATIC_SAMPLE_MIN_COUNT] readings, the tablet is considered
     * physically still (not being rotated by the user). The caller uses
     * this to crank smoothing way down and absorb magnetometer noise.
     *
     * One real rotation sample (> threshold) resets the counter, and the
     * caller bumps smoothing back up to the responsive setting.
     */
    @Volatile
    var isInStaticMode: Boolean = false
        private set

    /**
     * S115: One-shot flag raised at the moment the tracker enters static mode
     * (i.e., the user finished rotating the tablet). The caller consumes it
     * via [consumeJustEnteredStaticMode] and uses it to SNAP the smoothed
     * heading value to the current raw azimuth — committing the in-progress
     * rotation immediately instead of grinding it out through the heavy
     * static-mode smoothing over ~20 seconds.
     */
    @Volatile
    private var justEnteredStaticMode: Boolean = false

    private var consecutiveStaticSamples: Int = 0
    private var previousRawAzimuthDeg: Double? = null

    /**
     * S115: Last computed magnetic declination for the user's location, in
     * degrees (east positive). Cached across samples because declination
     * changes by sub-degree amounts per mile — recomputing every ~30s is
     * plenty, and it avoids constructing a GeomagneticField 16x/second.
     */
    private var cachedDeclinationDeg: Float = 0f
    private var cachedDeclinationTimeMs: Long = 0L
    private var loggedInitialDeclination: Boolean = false

    private var registered: Boolean = false
    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    companion object {
        /** Max degrees between consecutive samples that still counts as "static". */
        private const val STATIC_SAMPLE_THRESHOLD_DEG: Double = 1.5

        /** Number of consecutive static samples required before we enter static mode. */
        private const val STATIC_SAMPLE_MIN_COUNT: Int = 3

        /** S115: How often to recompute the magnetic declination. Declination changes
         *  by sub-degree amounts per tens of miles, so once every 30 seconds is plenty. */
        private const val DECLINATION_CACHE_TTL_MS: Long = 30_000L
    }

    init {
        val rv = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val geomag = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        when {
            rv != null -> {
                rotationSensor = rv
                sourceLabel = "ROTATION_VECTOR (9-axis fused: gyro+accel+mag)"
            }
            geomag != null -> {
                rotationSensor = geomag
                sourceLabel = "GEOMAGNETIC_ROTATION_VECTOR (mag+accel, no gyro)"
            }
            else -> {
                rotationSensor = null
                sourceLabel = "none available"
            }
        }
        DebugLogger.i("DEVICE-ORIENT", "init — source=$sourceLabel")
    }

    /** @return true if the device has some rotation-sensing capability. */
    fun hasSensor(): Boolean = rotationSensor != null

    /**
     * S115: Returns true exactly once per "static mode ON" transition. The
     * caller (applyHeadingUpRotation) consumes it to snap the smoothed
     * heading to the current raw azimuth, committing the just-finished
     * rotation immediately instead of letting α=0.05 grind it out over
     * dozens of seconds.
     */
    fun consumeJustEnteredStaticMode(): Boolean {
        if (justEnteredStaticMode) {
            justEnteredStaticMode = false
            return true
        }
        return false
    }

    /**
     * Start listening. Idempotent — safe to call multiple times. No-op if
     * the device has no rotation sensor (the caller can still check with
     * [hasSensor] to avoid scheduling ticks that will never fire).
     */
    fun start() {
        if (registered) return
        val sensor = rotationSensor
        if (sensor == null) {
            DebugLogger.w(
                "DEVICE-ORIENT",
                "start skipped — no rotation sensor available on this device"
            )
            return
        }
        val ok = sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_UI,
            Handler(Looper.getMainLooper())
        )
        registered = ok
        DebugLogger.i(
            "DEVICE-ORIENT",
            "start — registered=$ok source=$sourceLabel delay=SENSOR_DELAY_UI"
        )
    }

    /** Stop listening. Idempotent. */
    fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(this)
        registered = false
        DebugLogger.i("DEVICE-ORIENT", "stop — unregistered")
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Remap axes based on current display rotation so the azimuth is
        // computed relative to the top edge of the screen (which is the
        // axis the map canvas rotates around).
        val displayRotation = getDisplayRotation()
        val (axisX, axisY) = when (displayRotation) {
            Surface.ROTATION_0 -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        val remapOk = SensorManager.remapCoordinateSystem(
            rotationMatrix, axisX, axisY, remappedMatrix
        )
        if (!remapOk) {
            // Axes collided (shouldn't happen with our fixed table above).
            // Fall back to raw rotation matrix.
            System.arraycopy(rotationMatrix, 0, remappedMatrix, 0, 9)
        }

        SensorManager.getOrientation(remappedMatrix, orientationAngles)
        val azimuthRad = orientationAngles[0].toDouble()
        val magneticAzimuthDeg = (Math.toDegrees(azimuthRad) + 360.0) % 360.0

        // S115: Convert magnetic azimuth → true azimuth using
        // android.hardware.GeomagneticField. The sensor HAL returns
        // heading relative to magnetic north; the user expects TRUE
        // north (matches Google Maps, paper maps, street signs). In
        // Salem MA the correction is about +14° (magnetic north points
        // 14° west of true north, so we add 14° to convert).
        //
        // The correction is cached per [DECLINATION_CACHE_TTL_MS] because
        // it changes by sub-degree amounts per tens of miles of travel.
        val nowMs = System.currentTimeMillis()
        if (nowMs - cachedDeclinationTimeMs >= DECLINATION_CACHE_TTL_MS) {
            val loc = getUserLocation()
            if (loc != null) {
                val (lat, lng) = loc
                try {
                    val field = GeomagneticField(
                        lat.toFloat(),
                        lng.toFloat(),
                        /* altitude = */ 0f,
                        nowMs
                    )
                    val newDeclination = field.declination
                    if (!loggedInitialDeclination ||
                        kotlin.math.abs(newDeclination - cachedDeclinationDeg) >= 0.5f) {
                        DebugLogger.i(
                            "DEVICE-ORIENT",
                            "declination updated — ${"%.2f".format(newDeclination)}° at " +
                                "${"%.5f".format(lat)},${"%.5f".format(lng)} " +
                                "(adding to magnetic → true)"
                        )
                        loggedInitialDeclination = true
                    }
                    cachedDeclinationDeg = newDeclination
                    cachedDeclinationTimeMs = nowMs
                } catch (e: Exception) {
                    DebugLogger.e("DEVICE-ORIENT", "GeomagneticField failed: ${e.message}")
                }
            }
        }
        val azimuthDeg = (magneticAzimuthDeg + cachedDeclinationDeg.toDouble() + 360.0) % 360.0

        // S115: Static-mode detection. Compute the shortest angular distance
        // between this sample and the previous one. Small deltas accumulate
        // toward the static threshold; any large delta resets the counter.
        //
        // Shortest-angle math handles the wrap at 0/360 — a transition from
        // 359° to 1° is a 2° rotation, not 358°.
        val prev = previousRawAzimuthDeg
        if (prev != null) {
            var diff = ((azimuthDeg - prev + 540.0) % 360.0) - 180.0
            if (diff < 0) diff = -diff
            if (diff <= STATIC_SAMPLE_THRESHOLD_DEG) {
                if (consecutiveStaticSamples < STATIC_SAMPLE_MIN_COUNT * 2) {
                    consecutiveStaticSamples++
                }
                if (!isInStaticMode && consecutiveStaticSamples >= STATIC_SAMPLE_MIN_COUNT) {
                    isInStaticMode = true
                    justEnteredStaticMode = true
                    DebugLogger.i(
                        "DEVICE-ORIENT",
                        "static mode ON — ${consecutiveStaticSamples} consecutive samples within ${STATIC_SAMPLE_THRESHOLD_DEG}° — " +
                            "SNAP flag raised (caller should commit smoothed → raw)"
                    )
                }
            } else {
                if (isInStaticMode) {
                    DebugLogger.i(
                        "DEVICE-ORIENT",
                        "static mode OFF — rotation detected (Δ=${"%.1f".format(diff)}°)"
                    )
                }
                consecutiveStaticSamples = 0
                isInStaticMode = false
            }
        }
        previousRawAzimuthDeg = azimuthDeg

        currentAzimuthDeg = azimuthDeg
        lastUpdateMs = System.currentTimeMillis()

        // S115: Trace every raw sensor sample so the post-mortem log shows
        // the full azimuth stream (not just the apply/static-transition
        // events). Critical for diagnosing rotation smoothness issues
        // where samples get lost between the sensor and the map.
        DebugLogger.d(
            "ORIENT-RAW",
            "azimuth=${"%.1f".format(azimuthDeg)}° " +
                "(mag=${"%.1f".format(magneticAzimuthDeg)}°, decl=${"%.1f".format(cachedDeclinationDeg)}°, " +
                "static=${isInStaticMode})"
        )

        onUpdate()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (accuracy != currentAccuracy) {
            currentAccuracy = accuracy
            val label = when (accuracy) {
                SensorManager.SENSOR_STATUS_UNRELIABLE -> "UNRELIABLE"
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "LOW"
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "MEDIUM"
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "HIGH"
                else -> "UNKNOWN($accuracy)"
            }
            DebugLogger.i("DEVICE-ORIENT", "accuracy → $label")
            // Notify the activity so it can surface a calibration prompt to
            // the user (e.g., toast) when accuracy drops below MEDIUM.
            onAccuracyChanged?.invoke(accuracy)
        }
    }
}
