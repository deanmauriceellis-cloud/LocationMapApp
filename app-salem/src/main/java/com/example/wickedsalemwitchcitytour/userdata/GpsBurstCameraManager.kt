/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.userdata

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.example.locationmapapp.data.location.LocationManager as AppLocationManager
import com.example.locationmapapp.util.DebugLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor

/**
 * S228 — second top-bar camera button: GPS-burst mode for post-walk POI/path
 * alignment QC.
 *
 * Behaviour:
 *   - Toggle ON  → bind a headless CameraX [ImageCapture] use case to the host
 *                  activity lifecycle (no preview surface) and start collecting
 *                  the [AppLocationManager] flow at ~1 Hz.
 *   - Each fix  → if at least [THROTTLE_MS] has elapsed since the previous
 *                  shot, fire the rear camera, write a JPEG to a temp dir,
 *                  bake EXIF (GPS lat/lon/alt/speed/track + compass heading +
 *                  device make/model + timestamp), then publish to MediaStore
 *                  Pictures/[GALLERY_FOLDER]/.
 *   - Toggle OFF → cancel the location job, unbind the camera, toast the
 *                  shot count.
 *
 * Notes:
 *   - State is session-scoped; `setEnabled(true)` won't auto-resurrect after
 *     activity recreation. Operator must re-toggle.
 *   - Distinct from [KatrinaCameraManager] on purpose — that path opens a
 *     full-screen capture activity with a preview + shutter button. Burst
 *     must be silent and non-interrupting.
 *   - Uses its own FusedLocationProvider subscription via [AppLocationManager];
 *     GMS coalesces multiple subscriptions, so the existing MainViewModel
 *     subscription is unaffected.
 *   - Filename pattern: `burst_YYYYMMDD-HHMMSS_<lat>_<lon>.jpg` so the gallery
 *     thumbnail strip already shows time + position without opening EXIF.
 *     Negative coordinates have the `-` swapped to `n` so MediaStore display
 *     names parse cleanly across file managers.
 *   - R8 will dead-code every reference to this class in retail builds because
 *     the call sites are gated by [com.example.wickedsalemwitchcitytour.ui.BuildDefaults.GPS_BURST_ENABLED]
 *     (a `const val` wired to `BuildConfig.RECON_DEFAULTS = false` for release).
 */
class GpsBurstCameraManager(
    private val activity: AppCompatActivity,
    private val locationManager: AppLocationManager,
) : SensorEventListener {

    private val context: Context by lazy { activity.applicationContext }
    private val sensorManager: SensorManager? by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    private val rotationVector: Sensor? by lazy {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    @Volatile private var latestAzimuthDeg: Float? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private val mainExecutor: Executor by lazy { ContextCompat.getMainExecutor(context) }

    private var locationJob: Job? = null
    private var tickerJob: Job? = null
    @Volatile private var lastLoc: Location? = null
    @Volatile private var shotsThisSession: Int = 0
    @Volatile private var enabled: Boolean = false

    private var permissionLauncher: ActivityResultLauncher<String>? = null

    fun registerPermissionLauncher(launcher: ActivityResultLauncher<String>) {
        this.permissionLauncher = launcher
    }

    /**
     * Operator toggled the burst icon. Idempotent — calling with the current
     * state is a no-op.
     */
    fun setEnabled(enable: Boolean) {
        if (enable == enabled) return
        if (enable) start() else stop()
    }

    fun isEnabled(): Boolean = enabled

    private fun start() {
        // CAMERA permission — re-prompt if not yet granted. The launcher
        // callback will call [setEnabled(true)] again on grant.
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            DebugLogger.i(TAG, "start: requesting CAMERA permission")
            permissionLauncher?.launch(Manifest.permission.CAMERA)
            return
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(activity, "GPS-burst needs Location permission", Toast.LENGTH_LONG).show()
            return
        }

        enabled = true
        shotsThisSession = 0
        lastLoc = null
        DebugLogger.i(TAG, "start — burst ON, tick=${TICK_MS}ms (time-driven, GPS-independent), gallery=Pictures/$GALLERY_FOLDER")
        bindCamera()
        startLocationCollection()
        startShotTicker()
        Toast.makeText(
            activity,
            "Rapid Recon ON — auto-shoot every ${TICK_MS / 1000}s",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun stop() {
        enabled = false
        tickerJob?.cancel()
        tickerJob = null
        locationJob?.cancel()
        locationJob = null
        try { cameraProvider?.unbindAll() } catch (t: Throwable) {
            DebugLogger.w(TAG, "unbindAll failed: ${t.message}")
        }
        imageCapture = null
        DebugLogger.i(TAG, "stop — burst OFF, $shotsThisSession photos this session")
        Toast.makeText(
            activity,
            "Rapid Recon OFF — $shotsThisSession photos saved",
            Toast.LENGTH_SHORT
        ).show()
    }

    fun onCameraPermissionResult(granted: Boolean) {
        if (granted) {
            // Operator was asking for ON when we hit the prompt — finish the start now.
            start()
        } else {
            enabled = false
            Toast.makeText(activity, "Camera permission denied — burst not started", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .build()
                // Belt-and-suspenders — also force flash off on the live instance so
                // any auto-flash logic added later by CameraX defaults can't switch
                // it on mid-walk.
                capture.flashMode = ImageCapture.FLASH_MODE_OFF
                provider.unbindAll()
                provider.bindToLifecycle(
                    activity,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    capture,
                )
                imageCapture = capture
                DebugLogger.i(TAG, "CameraX bound (ImageCapture only, no preview)")
            } catch (t: Throwable) {
                DebugLogger.e(TAG, "bindCamera failed: ${t.message}")
                Toast.makeText(activity, "Burst camera bind failed: ${t.message}", Toast.LENGTH_LONG).show()
                stop()
            }
        }, mainExecutor)
    }

    @Suppress("MissingPermission") // Checked in start()
    private fun startLocationCollection() {
        // S231: location flow is now a passive cache feeder for EXIF stamping.
        // Shot cadence is driven by [startShotTicker] at TICK_MS, so flaky GPS
        // (Doze, indoors, stationary-coalescing) cannot starve the camera.
        // Each fix updates [lastLoc]; the ticker reads whatever's most recent.
        locationJob = activity.lifecycleScope.launch {
            locationManager.getLocationUpdates(intervalMs = 500L, minIntervalMs = 500L)
                .collect { loc -> lastLoc = loc }
        }
    }

    private fun startShotTicker() {
        tickerJob = activity.lifecycleScope.launch {
            while (isActive && enabled) {
                fireShot(lastLoc)
                delay(TICK_MS)
            }
        }
    }

    private fun fireShot(loc: Location?) {
        val capture = imageCapture
        if (capture == null) {
            DebugLogger.w(TAG, "fireShot: imageCapture not yet bound, skipping")
            return
        }

        val tmpDir = File(context.getExternalFilesDir(null), "gps-burst-tmp")
        if (!tmpDir.exists()) tmpDir.mkdirs()

        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        // MediaStore display names tolerate '-' but several file managers misparse
        // a leading '-' on a token; swap negatives to 'n' to keep filenames clean.
        // When GPS hasn't delivered a fix yet (or has stalled), filename uses
        // "noGPS" so the gap is obvious in the gallery thumbnail strip.
        val coordSuffix = if (loc != null) {
            val latStr = "%.5f".format(loc.latitude).replace("-", "n")
            val lonStr = "%.5f".format(loc.longitude).replace("-", "n")
            "${latStr}_${lonStr}"
        } else "noGPS"
        val displayName = "burst_${ts}_${coordSuffix}.jpg"
        val tmpFile = File(tmpDir, displayName)
        val azimuthAtCapture = latestAzimuthDeg

        val opts = ImageCapture.OutputFileOptions.Builder(tmpFile).build()
        capture.takePicture(opts, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                try {
                    writeExif(tmpFile, loc, azimuthAtCapture)
                } catch (t: Throwable) {
                    DebugLogger.e(TAG, "writeExif failed: ${t.message}")
                }
                val publishedUri = publishToMediaStore(tmpFile, displayName)
                try { tmpFile.delete() } catch (_: Throwable) { /* best effort */ }
                shotsThisSession++

                if (shotsThisSession == 1 && publishedUri != null) {
                    activity.runOnUiThread {
                        Toast.makeText(
                            activity,
                            "First burst photo saved → Pictures/$GALLERY_FOLDER",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                val accStr = loc?.let { "  acc=${"%.1f".format(it.accuracy)}m" } ?: "  acc=--"
                DebugLogger.i(
                    TAG,
                    "burst #$shotsThisSession  $displayName$accStr" +
                        (azimuthAtCapture?.let { "  hdg=${"%.0f".format(it)}°" } ?: "")
                )
            }

            override fun onError(e: ImageCaptureException) {
                DebugLogger.e(TAG, "takePicture error code=${e.imageCaptureError}: ${e.message}")
            }
        })
    }

    // ─────────────────────────────────────────────────────────────────────
    // Sensor (compass heading) lifecycle
    // ─────────────────────────────────────────────────────────────────────

    fun onResume() {
        rotationVector?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun onPause() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val rotMatrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(rotMatrix, event.values)
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotMatrix, orientation)
        var azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (azimuthDeg < 0f) azimuthDeg += 360f
        latestAzimuthDeg = azimuthDeg
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { /* no-op */ }

    // ─────────────────────────────────────────────────────────────────────
    // EXIF + MediaStore publish (parallel to KatrinaCameraManager's path,
    // duplicated rather than refactored to keep the existing recon-camera
    // code untouched for this iteration).
    // ─────────────────────────────────────────────────────────────────────

    private fun writeExif(file: File, loc: Location?, azimuthDeg: Float?) {
        val exif = ExifInterface(file.absolutePath)

        exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER ?: "")
        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL ?: "")
        exif.setAttribute(
            ExifInterface.TAG_SOFTWARE,
            "WickedSalem Rapid-Recon (Android ${Build.VERSION.RELEASE})"
        )

        val nowFmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        val nowStr = nowFmt.format(Date())
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, nowStr)
        exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, nowStr)

        if (loc != null) {
            exif.setLatLong(loc.latitude, loc.longitude)
            if (loc.hasAltitude()) exif.setAltitude(loc.altitude)
            if (loc.hasSpeed()) {
                val kmh = (loc.speed * 3.6).toString()
                exif.setAttribute(ExifInterface.TAG_GPS_SPEED, "$kmh/1")
                exif.setAttribute(ExifInterface.TAG_GPS_SPEED_REF, "K")
            }
            if (loc.hasBearing()) {
                exif.setAttribute(ExifInterface.TAG_GPS_TRACK, "${loc.bearing}/1")
                exif.setAttribute(ExifInterface.TAG_GPS_TRACK_REF, "T")
            }
            val procMethod = if (loc.hasAccuracy()) {
                "GPS (accuracy=${"%.1f".format(loc.accuracy)}m)"
            } else "GPS"
            exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, procMethod)

            val fixTime = if (loc.time > 0) loc.time else System.currentTimeMillis()
            val gpsTimeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            val gpsDateFmt = SimpleDateFormat("yyyy:MM:dd", Locale.US)
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, gpsTimeFmt.format(Date(fixTime)))
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, gpsDateFmt.format(Date(fixTime)))
        } else {
            exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, "NO_FIX")
        }

        if (azimuthDeg != null) {
            // azimuthDeg from TYPE_ROTATION_VECTOR is relative to MAGNETIC north.
            // If we have a GPS fix, convert to TRUE north via local declination
            // (GeomagneticField — WMM model). Without a fix, fall back to magnetic.
            val magAz = ((azimuthDeg % 360f) + 360f) % 360f
            if (loc != null) {
                val geo = GeomagneticField(
                    loc.latitude.toFloat(),
                    loc.longitude.toFloat(),
                    if (loc.hasAltitude()) loc.altitude.toFloat() else 0f,
                    if (loc.time > 0) loc.time else System.currentTimeMillis()
                )
                val trueAz = ((magAz + geo.declination) % 360f + 360f) % 360f
                exif.setAttribute(
                    ExifInterface.TAG_GPS_IMG_DIRECTION,
                    "${(trueAz * 100).toInt()}/100"
                )
                exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "T")
            } else {
                exif.setAttribute(
                    ExifInterface.TAG_GPS_IMG_DIRECTION,
                    "${(magAz * 100).toInt()}/100"
                )
                exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "M")
            }
        }

        val note = StringBuilder("WickedSalem Rapid-Recon v2")
        if (loc != null) {
            note.append(" | acc=").append("%.1f".format(loc.accuracy)).append("m")
            if (loc.hasSpeed()) note.append(" spd=").append("%.2f".format(loc.speed)).append("m/s")
            if (loc.hasAltitude()) note.append(" alt=").append("%.1f".format(loc.altitude)).append("m")
        } else {
            note.append(" | gps=NO_FIX")
        }
        azimuthDeg?.let { note.append(" hdg=").append("%.0f".format(it)).append("°") }
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, note.toString())

        exif.saveAttributes()
    }

    private fun publishToMediaStore(src: File, displayName: String): android.net.Uri? {
        val resolver = context.contentResolver
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/$GALLERY_FOLDER"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { inp -> inp.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (t: Throwable) {
            DebugLogger.e(TAG, "publishToMediaStore failed: ${t.message}")
            null
        }
    }

    companion object {
        private const val TAG = "GpsBurstCameraManager"
        // S231 — switched from GPS-fix-driven (THROTTLE_MS) to time-driven
        // (TICK_MS) so flaky/coalesced GPS can't starve the camera. 1 s tick.
        private const val TICK_MS = 1_000L
        private const val GALLERY_FOLDER = "WickedSalemRecon"
    }
}
