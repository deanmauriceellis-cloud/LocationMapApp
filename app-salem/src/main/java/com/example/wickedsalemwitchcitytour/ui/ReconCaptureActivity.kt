/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.locationmapapp.util.DebugLogger
import com.example.wickedsalemwitchcitytour.R
import java.io.File

/**
 * Full-screen in-app capture activity. We own the window: explicit "X" in
 * the top-left lets the user bail at any time, and the back button maps to
 * the same cancel path.
 *
 * Caller passes [EXTRA_OUT_FILE_PATH] — the file we write the JPEG into.
 * On success we return [RESULT_OK]; on cancel/error we return
 * [RESULT_CANCELED] and the caller deletes the empty temp file.
 */
class ReconCaptureActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var hud: TextView
    private lateinit var shutterBtn: ImageView
    private lateinit var closeBtn: ImageView

    private var imageCapture: ImageCapture? = null
    private var outFile: File? = null
    private var capturing: Boolean = false
    private var autoFire: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep screen on while framing; saves the user wondering why the
        // tablet locked mid-recon.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_recon_capture)

        previewView = findViewById(R.id.reconPreview)
        hud         = findViewById(R.id.reconHud)
        shutterBtn  = findViewById(R.id.reconShutterBtn)
        closeBtn    = findViewById(R.id.reconCloseBtn)

        val path = intent.getStringExtra(EXTRA_OUT_FILE_PATH)
        if (path.isNullOrBlank()) {
            DebugLogger.e(TAG, "Missing EXTRA_OUT_FILE_PATH")
            Toast.makeText(this, "Camera setup failed", Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
            finish()
            return
        }
        outFile = File(path)
        autoFire = intent.getBooleanExtra(EXTRA_AUTO_FIRE, false)

        closeBtn.setOnClickListener { cancelAndFinish() }
        shutterBtn.setOnClickListener { capture() }

        if (autoFire) {
            // Hide the framing UI — operator just wants the photo.
            hud.text = "Capturing…"
            shutterBtn.visibility = android.view.View.GONE
            closeBtn.visibility = android.view.View.GONE
            previewView.visibility = android.view.View.INVISIBLE
        } else {
            hud.text = "Recon  •  tap shutter to capture"
        }
        startCamera()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                val selector = CameraSelector.DEFAULT_BACK_CAMERA

                provider.unbindAll()
                if (autoFire) {
                    // Headless — no preview surface bound. The activity
                    // window stays present (so onCreate's keep-screen-on
                    // and lifecycle-bound CameraX still work) but the
                    // operator never sees a viewfinder.
                    provider.bindToLifecycle(this, selector, capture)
                } else {
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    provider.bindToLifecycle(this, selector, preview, capture)
                }
                imageCapture = capture
                DebugLogger.i(TAG, "CameraX bound (autoFire=$autoFire)")

                if (autoFire) {
                    previewView.postDelayed({ capture() }, AUTO_FIRE_SETTLE_MS)
                }
            } catch (t: Throwable) {
                DebugLogger.e(TAG, "CameraX bind failed: ${t.message}")
                Toast.makeText(this, "Camera failed: ${t.message}", Toast.LENGTH_LONG).show()
                cancelAndFinish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capture() {
        if (capturing) return
        val capture = imageCapture
        val target = outFile
        if (capture == null || target == null) {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show()
            return
        }
        capturing = true
        shutterBtn.isEnabled = false
        hud.text = "Capturing…"

        val opts = ImageCapture.OutputFileOptions.Builder(target).build()
        capture.takePicture(
            opts,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    DebugLogger.i(TAG, "Image saved → ${target.absolutePath}")
                    setResult(RESULT_OK)
                    finish()
                }

                override fun onError(exc: ImageCaptureException) {
                    DebugLogger.e(TAG, "takePicture error: ${exc.message}")
                    Toast.makeText(this@ReconCaptureActivity,
                        "Capture failed: ${exc.message}", Toast.LENGTH_LONG).show()
                    capturing = false
                    shutterBtn.isEnabled = true
                    hud.text = "Capture failed — try again"
                }
            }
        )
    }

    private fun cancelAndFinish() {
        setResult(RESULT_CANCELED)
        finish()
    }

    @Deprecated("Standard Activity back-press override; kept for older API surfaces")
    override fun onBackPressed() {
        cancelAndFinish()
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    companion object {
        private const val TAG = "ReconCaptureActivity"
        const val EXTRA_OUT_FILE_PATH = "extra_out_file_path"
        // S232 — when true, skip the preview-and-shutter UI: bind the camera
        // headlessly, fire the shutter once autofocus settles, then finish.
        // Used by the always-visible toolbar camera icon so a single tap
        // produces a photo without operator interaction.
        const val EXTRA_AUTO_FIRE = "extra_auto_fire"
        // Brief settle window before firing the shutter in auto-fire mode.
        // ~250 ms gives autofocus enough time on the Lenovo without a visible
        // UI flicker.
        private const val AUTO_FIRE_SETTLE_MS = 300L
    }
}
