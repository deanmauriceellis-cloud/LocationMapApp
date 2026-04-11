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
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import com.example.locationmapapp.util.DebugLogger

/**
 * S115: Detects whether the device is physically stationary using the
 * `TYPE_SIGNIFICANT_MOTION` trigger sensor. Used to freeze the GPS marker,
 * journey polyline, trigger rings, and movement bearing when the tablet is
 * sitting still on a desk (or any stationary surface) so GPS jitter stops
 * shaking the visible layer around.
 *
 * ## Why significant motion (not step detector or accelerometer)
 *
 *  - `TYPE_SIGNIFICANT_MOTION` is designed specifically for this use case.
 *    It fires only when the device detects a "significant" change in
 *    motion — walking, pickup, being carried — and ignores hand tremor,
 *    desk vibrations from typing, and other background noise.
 *
 *  - `TYPE_STEP_DETECTOR` would be more accurate for walking specifically,
 *    but on API 29+ it requires the `ACTIVITY_RECOGNITION` runtime
 *    permission. Adding a new permission prompt for a jitter-fix feature
 *    isn't worth the UX friction. Revisit if significant motion proves
 *    too conservative.
 *
 *  - Raw accelerometer would work but needs its own threshold logic to
 *    distinguish "real motion" from "gravity + noise." Significant motion
 *    is the HAL's already-tuned answer to that exact problem.
 *
 * ## Trigger sensor lifecycle
 *
 * Trigger sensors are one-shot. After firing, the sensor disarms itself
 * and has to be manually re-armed with `requestTriggerSensor`. We re-arm
 * inside the callback so motion is always being watched for.
 *
 * ## Warmup window
 *
 * `isStationary` returns false for the first [warmupMs] milliseconds after
 * [start] is called, even if no motion has been detected. This prevents a
 * false-freeze on cold start when the user is actively walking — the step
 * doesn't register as "significant" until the sensor has had time to
 * observe the motion pattern, and we don't want to freeze the marker for
 * 5 seconds during that window.
 *
 * After warmup, the logic is: `stationary = (now - lastMotionMs) >= thresholdMs`.
 */
class MotionTracker(
    context: Context,
    private val warmupMs: Long = 10_000L,
    private val stationaryThresholdMs: Long = 5_000L
) {
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sigMotionSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

    /** Wall-clock millis of the most recent significant-motion trigger. */
    @Volatile
    var lastMotionEventMs: Long = 0L
        private set

    /** Wall-clock millis at which [start] was last called. */
    @Volatile
    var startedAtMs: Long = 0L
        private set

    /** Total significant-motion events observed since [start]. Diagnostic only. */
    @Volatile
    var eventCount: Int = 0
        private set

    private var armed: Boolean = false

    private val triggerListener = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent) {
            lastMotionEventMs = System.currentTimeMillis()
            eventCount++
            DebugLogger.i(
                "MOTION",
                "significant motion #$eventCount — " +
                    "values=${event.values.joinToString(",") { "%.2f".format(it) }}"
            )
            // Re-arm for the next event. The sensor is one-shot so without
            // this call it will never fire again.
            val sensor = sigMotionSensor
            if (sensor != null) {
                armed = sensorManager.requestTriggerSensor(this, sensor)
                if (!armed) {
                    DebugLogger.w("MOTION", "re-arm failed — trigger sensor disabled")
                }
            }
        }
    }

    /** @return true if the device has significant-motion capability. */
    fun hasSensor(): Boolean = sigMotionSensor != null

    /**
     * Start watching for motion. Idempotent — safe to call multiple times.
     * No-op if the device has no significant-motion sensor.
     */
    fun start() {
        if (armed) return
        val sensor = sigMotionSensor
        if (sensor == null) {
            DebugLogger.w("MOTION", "start skipped — no significant-motion sensor")
            return
        }
        startedAtMs = System.currentTimeMillis()
        armed = sensorManager.requestTriggerSensor(triggerListener, sensor)
        DebugLogger.i(
            "MOTION",
            "start — armed=$armed warmup=${warmupMs / 1000}s threshold=${stationaryThresholdMs / 1000}s"
        )
    }

    /**
     * Stop watching for motion. Idempotent. The sensor trigger is cancelled
     * and [lastMotionEventMs] is preserved so the caller can read the state
     * after stop.
     */
    fun stop() {
        val sensor = sigMotionSensor ?: return
        if (!armed) return
        sensorManager.cancelTriggerSensor(triggerListener, sensor)
        armed = false
        DebugLogger.i("MOTION", "stop — unarmed (events this run=$eventCount)")
    }

    /**
     * True when the device has been still for at least [stationaryThresholdMs]
     * AND the tracker has been running for at least [warmupMs]. Before the
     * warmup period elapses, always returns false — see the class doc for
     * why.
     */
    fun isStationary(): Boolean {
        if (!hasSensor()) return false  // fail-safe: unknown state → not stationary
        val now = System.currentTimeMillis()
        if (startedAtMs == 0L || (now - startedAtMs) < warmupMs) return false
        return (now - lastMotionEventMs) >= stationaryThresholdMs
    }

    /**
     * Human-readable snapshot for log output:
     *   "stationary (30s since last motion)"
     *   "moving (motion 2s ago, eventCount=5)"
     *   "warming up (3s of 10s)"
     */
    fun statusString(): String {
        val now = System.currentTimeMillis()
        if (!hasSensor()) return "no sensor"
        if (startedAtMs == 0L) return "not started"
        val runMs = now - startedAtMs
        if (runMs < warmupMs) return "warming up (${runMs / 1000}s of ${warmupMs / 1000}s)"
        // S115 cosmetic: when lastMotionEventMs is still 0 the sensor has
        // never fired, so "X since last motion" would print seconds since
        // the epoch. Report "never" instead, and time since tracker start
        // as the useful quantity.
        val sinceMotionLabel = if (lastMotionEventMs == 0L) {
            "never fired (${runMs / 1000}s since start)"
        } else {
            "${(now - lastMotionEventMs) / 1000}s since last motion"
        }
        val isStill = (now - lastMotionEventMs) >= stationaryThresholdMs
        return if (isStill) {
            "stationary ($sinceMotionLabel, events=$eventCount)"
        } else {
            "moving ($sinceMotionLabel, events=$eventCount)"
        }
    }
}
