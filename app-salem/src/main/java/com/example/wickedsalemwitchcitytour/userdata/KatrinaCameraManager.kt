/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.userdata

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.ui.ReconCaptureActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Top-bar Camera button — captures a recon photo and bakes a full EXIF
 * record (GPS lat/lon/alt/speed/track + camera compass direction +
 * timestamp + device make/model).
 *
 * Uses raw [FusedLocationProviderClient.getLastLocation] so EXIF carries the
 * user's actual coordinates, bypassing [com.example.wickedsalemwitchcitytour.ui.MainViewModel]'s
 * Samantha-statue clamp for out-of-bbox fixes.
 *
 * Photos land in MediaStore Pictures/WickedSalem-Recon/ — visible via USB
 * MTP without adb pull, and indexed by the system gallery.
 */
class KatrinaCameraManager(
    private val activity: AppCompatActivity
) : SensorEventListener {

    // Lazy because the manager is constructed during the activity's field
    // initializer block, BEFORE super.onCreate() — at that point
    // activity.applicationContext is still null. Deferring these lookups to
    // first access (always after onCreate) sidesteps the NPE.
    private val context: Context by lazy { activity.applicationContext }
    private val sensorManager: SensorManager? by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    }
    private val rotationVector: Sensor? by lazy {
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    private val fusedClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @Volatile private var latestAzimuthDeg: Float? = null

    private var pendingTmpFile: File? = null
    private var pendingLocation: Location? = null
    private var pendingAzimuthAtCapture: Float? = null

    private var captureLauncher: ActivityResultLauncher<Intent>? = null
    private var permissionLauncher: ActivityResultLauncher<String>? = null

    fun registerLaunchers(
        captureLauncher: ActivityResultLauncher<Intent>,
        permissionLauncher: ActivityResultLauncher<String>
    ) {
        this.captureLauncher = captureLauncher
        this.permissionLauncher = permissionLauncher
    }

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

    fun requestCapture() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher?.launch(Manifest.permission.CAMERA)
            return
        }
        launchCameraIntent()
    }

    fun onCameraPermissionResult(granted: Boolean) {
        if (granted) {
            launchCameraIntent()
        } else {
            Toast.makeText(activity, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchCameraIntent() {
        // Snapshot azimuth at intent-launch — best approximation of "where the
        // user is pointing" before the activity transition consumes UI focus.
        pendingAzimuthAtCapture = latestAzimuthDeg

        // Snapshot most-recent device GPS (unclamped, raw FusedClient).
        val hasFineLoc = ContextCompat.checkSelfPermission(activity,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFineLoc) {
            try {
                fusedClient.lastLocation.addOnSuccessListener { loc ->
                    pendingLocation = loc
                }
            } catch (se: SecurityException) {
                DebugLogger.w(TAG, "lastLocation SecurityException: ${se.message}")
            }
        }

        val tmpDir = File(activity.getExternalFilesDir(null), "recon-camera-tmp")
        if (!tmpDir.exists()) tmpDir.mkdirs()
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val tmpFile = File(tmpDir, "recon_$ts.jpg")
        pendingTmpFile = tmpFile

        val intent = Intent(activity, ReconCaptureActivity::class.java).apply {
            putExtra(ReconCaptureActivity.EXTRA_OUT_FILE_PATH, tmpFile.absolutePath)
        }

        val launcher = captureLauncher
        if (launcher == null) {
            DebugLogger.e(TAG, "captureLauncher not registered")
            Toast.makeText(activity, "Camera launcher not ready", Toast.LENGTH_SHORT).show()
            return
        }
        launcher.launch(intent)
    }

    fun onCaptureResult(resultCode: Int) {
        val tmpFile = pendingTmpFile
        pendingTmpFile = null
        if (resultCode != Activity.RESULT_OK) {
            tmpFile?.delete()
            return
        }
        if (tmpFile == null || !tmpFile.exists() || tmpFile.length() <= 0L) {
            DebugLogger.w(TAG, "onCaptureResult: tmp file missing or empty")
            Toast.makeText(activity, "Camera returned no image", Toast.LENGTH_SHORT).show()
            return
        }

        // If the pre-launch lastLocation hadn't returned yet, give it one more shot.
        if (pendingLocation == null) {
            val hasFineLoc = ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (hasFineLoc) {
                try {
                    fusedClient.lastLocation
                        .addOnSuccessListener { loc ->
                            pendingLocation = loc
                            finishWriteAndPublish(tmpFile)
                        }
                        .addOnFailureListener { finishWriteAndPublish(tmpFile) }
                    return
                } catch (_: SecurityException) {
                    // fall through to publish without GPS
                }
            }
        }
        finishWriteAndPublish(tmpFile)
    }

    private fun finishWriteAndPublish(tmpFile: File) {
        try {
            writeExif(tmpFile, pendingLocation, pendingAzimuthAtCapture)
        } catch (t: Throwable) {
            DebugLogger.e(TAG, "writeExif failed: ${t.message}")
        }

        val outName = tmpFile.name
        val publishedUri = publishToMediaStore(tmpFile, outName)
        try { tmpFile.delete() } catch (_: Throwable) { /* best effort */ }

        val msg = if (publishedUri != null) {
            val loc = pendingLocation
            if (loc != null) {
                "Saved $outName  •  ${"%.5f".format(loc.latitude)}, ${"%.5f".format(loc.longitude)}"
            } else {
                "Saved $outName  •  no GPS fix yet"
            }
        } else {
            "Saved $outName (gallery publish failed)"
        }
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show()
        DebugLogger.i(TAG, msg)

        pendingLocation = null
        pendingAzimuthAtCapture = null
    }

    private fun writeExif(file: File, loc: Location?, azimuthDeg: Float?) {
        val exif = ExifInterface(file.absolutePath)

        exif.setAttribute(ExifInterface.TAG_MAKE, Build.MANUFACTURER ?: "")
        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL ?: "")
        exif.setAttribute(
            ExifInterface.TAG_SOFTWARE,
            "WickedSalem recon (Android ${Build.VERSION.RELEASE})"
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
            } else {
                "GPS"
            }
            exif.setAttribute(ExifInterface.TAG_GPS_PROCESSING_METHOD, procMethod)

            val fixTime = if (loc.time > 0) loc.time else System.currentTimeMillis()
            val gpsTimeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            val gpsDateFmt = SimpleDateFormat("yyyy:MM:dd", Locale.US)
            exif.setAttribute(ExifInterface.TAG_GPS_TIMESTAMP, gpsTimeFmt.format(Date(fixTime)))
            exif.setAttribute(ExifInterface.TAG_GPS_DATESTAMP, gpsDateFmt.format(Date(fixTime)))
        }

        if (azimuthDeg != null) {
            val az = ((azimuthDeg % 360f) + 360f) % 360f
            exif.setAttribute(
                ExifInterface.TAG_GPS_IMG_DIRECTION,
                "${(az * 100).toInt()}/100"
            )
            exif.setAttribute(ExifInterface.TAG_GPS_IMG_DIRECTION_REF, "M")
        }

        val note = StringBuilder("WickedSalem recon v1")
        loc?.let {
            note.append(" | acc=").append("%.1f".format(it.accuracy)).append("m")
            if (it.hasSpeed()) note.append(" spd=").append("%.2f".format(it.speed)).append("m/s")
            if (it.hasAltitude()) note.append(" alt=").append("%.1f".format(it.altitude)).append("m")
        }
        azimuthDeg?.let { note.append(" hdg=").append("%.0f".format(it)).append("°") }
        exif.setAttribute(ExifInterface.TAG_USER_COMMENT, note.toString())

        exif.saveAttributes()
    }

    private fun publishToMediaStore(src: File, displayName: String): Uri? {
        val resolver = context.contentResolver
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/WickedSalem-Recon"
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
            DebugLogger.i(TAG, "Published recon photo: $uri")
            uri
        } catch (t: Throwable) {
            DebugLogger.e(TAG, "publishToMediaStore failed: ${t.message}")
            null
        }
    }

    companion object {
        private const val TAG = "KatrinaCameraManager"
    }
}
