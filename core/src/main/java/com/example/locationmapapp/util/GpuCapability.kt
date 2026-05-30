/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * S308 — adaptive GPU capability detector. The 3D-tilt feature fakes perspective
 * by concatenating a setPolyToPoly matrix onto a HARDWARE-accelerated canvas and
 * drawing the osmdroid basemap tiles THROUGH it (see TiltContainer.dispatchDraw).
 * On weak / virtualized / software GPUs (BlueStacks GLES 3.0, emulators,
 * Chromebook ARC, bad-driver phones) that perspective-textured composite corrupts
 * to magenta #FF00FF / cyan / the bare tilt-plane, while the Canvas-drawn vector
 * overlays stay perfect. Real mobile GPUs (Adreno/Mali/…) composite it correctly.
 *
 * This object runs a ONE-TIME, off-main, cached probe (EGL pbuffer → GL_RENDERER
 * + GL_MAX_TEXTURE_SIZE, plus Build emulator heuristics) and classifies the GPU
 * GOOD / WEAK / UNKNOWN. TiltContainer reads [needsSoftwareTilt] to route the
 * tilted composite through a software (Skia/CPU) layer on everything that is not
 * provably GOOD — correctness over speed, default-safe on hardware we've never
 * seen. GOOD devices stay 100% on the hardware fast path.
 *
 * DEFAULT-SAFE: [verdict] starts UNKNOWN and [needsSoftwareTilt] is true for both
 * WEAK and UNKNOWN, so any GPU we don't positively recognize as a known-good
 * mobile family gets the safe path — and so does the window before the async
 * probe returns on first launch (subsequent launches read the cached verdict
 * synchronously, before any tilt is possible).
 */

package com.example.locationmapapp.util

import android.content.Context
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.os.Build

@Suppress("unused")
private const val MODULE_ID = "(C) Destructive AI Gurus, LLC, 2026 - Module GpuCapability.kt"

// DebugLogger lives in the same package — no import needed.

object GpuCapability {

    enum class Verdict { GOOD, WEAK, UNKNOWN }

    private const val TAG = "GpuCapability"
    private const val PREFS = "gpu_capability"
    // Bump when detection LOGIC changes so a stale cached verdict from an older
    // build (same versionCode — e.g. during dev, or a hotfix that keeps the code)
    // is invalidated and the device re-probes with the new logic.
    private const val DETECTION_LOGIC_VERSION = 4

    /** Compile-time availability (host app passes BuildDefaults flag). */
    @Volatile private var available: Boolean = false

    /** GPU class verdict. UNKNOWN until the probe (or cache) resolves it. */
    @Volatile var verdict: Verdict = Verdict.UNKNOWN
        private set

    /** GL_MAX_TEXTURE_SIZE; conservative default until probed. */
    @Volatile var maxTextureSize: Int = 2048
        private set

    /** Raw GL_RENDERER string (for diagnostics + denylist curation). */
    @Volatile var renderer: String = ""
        private set

    @Volatile private var probeStarted: Boolean = false

    // Known-software / virtualized GL_RENDERER substrings → WEAK.
    private val DENY = listOf(
        "swiftshader", "llvmpipe", "softpipe", "mesa", "android emulator", "emulator",
        "translator", "goldfish", "ranchu", "bluestacks", "virgl", "virtio", "vmware",
        "vbox", "parallels", "microsoft basic render",
    )
    // Known-good MOBILE GPU families → eligible for GOOD. Anything not on this
    // list (desktop passthrough GPUs, ANGLE, unknown) falls through to UNKNOWN →
    // safe path, which is the whole point.
    private val ALLOW = listOf("adreno", "mali", "immortalis", "powervr", "xclipse", "apple")

    /** Called once at startup by the host app with the compile-time flag. */
    fun setAvailable(value: Boolean) {
        available = value
    }

    /**
     * True when the tilted basemap composite must be rasterized on a software
     * layer instead of the hardware GL path. WEAK and UNKNOWN both return true;
     * only a positively-recognized GOOD mobile GPU returns false.
     */
    fun needsSoftwareTilt(): Boolean = available && verdict != Verdict.GOOD

    /**
     * One-time GPU probe. Reads a cached verdict synchronously if present
     * (keyed on Build.FINGERPRINT + versionCode), else runs the EGL probe on a
     * daemon background thread. Safe to call from Application.onCreate.
     */
    fun probe(context: Context) {
        if (probeStarted) return
        probeStarted = true
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = cacheKey(ctx)
        val cached = prefs.getString(key, null)
        if (cached != null) {
            applyCached(cached)
            DebugLogger.i(TAG, "cached: verdict=$verdict maxTex=$maxTextureSize renderer='$renderer'")
            return
        }
        Thread {
            val (v, mt, r) = runProbe()
            verdict = v
            maxTextureSize = mt
            renderer = r
            try {
                prefs.edit().putString(key, "${v.name}|$mt|$r").apply()
            } catch (_: Throwable) {
            }
            DebugLogger.i(
                TAG,
                "probed: verdict=$v maxTex=$mt renderer='$r' emuBuild=${isEmulatorBuild()} " +
                    "model='${Build.MODEL}' hw='${Build.HARDWARE}'",
            )
        }.apply {
            isDaemon = true
            name = "gpu-probe"
            start()
        }
    }

    private fun cacheKey(ctx: Context): String {
        val vc = try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode
            else @Suppress("DEPRECATION") pi.versionCode.toLong()
        } catch (_: Throwable) {
            0L
        }
        return "gpucap${DETECTION_LOGIC_VERSION}_${Build.FINGERPRINT.hashCode()}_$vc"
    }

    private fun applyCached(s: String) {
        val parts = s.split("|", limit = 3)
        verdict = try {
            Verdict.valueOf(parts[0])
        } catch (_: Throwable) {
            Verdict.UNKNOWN
        }
        maxTextureSize = parts.getOrNull(1)?.toIntOrNull() ?: 2048
        renderer = parts.getOrNull(2) ?: ""
    }

    /** EGL 1×1 pbuffer probe. Any throw / EGL failure → WEAK (default-safe). */
    private fun runProbe(): Triple<Verdict, Int, String> {
        var r = ""
        var mt = 0
        var eglOk = false
        var dpy: EGLDisplay? = null
        var ctx: EGLContext? = null
        var surf: EGLSurface? = null
        try {
            dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            val ver = IntArray(2)
            if (dpy != null && dpy != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(dpy, ver, 0, ver, 1)) {
                val cfgAttrs = intArrayOf(
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_NONE,
                )
                val cfgs = arrayOfNulls<EGLConfig>(1)
                val nc = IntArray(1)
                if (EGL14.eglChooseConfig(dpy, cfgAttrs, 0, cfgs, 0, 1, nc, 0) && nc[0] > 0) {
                    val ctxAttrs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                    ctx = EGL14.eglCreateContext(dpy, cfgs[0], EGL14.EGL_NO_CONTEXT, ctxAttrs, 0)
                    val pbAttrs = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
                    surf = EGL14.eglCreatePbufferSurface(dpy, cfgs[0], pbAttrs, 0)
                    if (ctx != null && ctx != EGL14.EGL_NO_CONTEXT && surf != null &&
                        surf != EGL14.EGL_NO_SURFACE && EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
                    ) {
                        r = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
                        val mtb = IntArray(1)
                        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, mtb, 0)
                        mt = mtb[0]
                        eglOk = true
                    }
                }
            }
        } catch (t: Throwable) {
            DebugLogger.i(TAG, "probe EGL threw: ${t.message}")
        } finally {
            try {
                if (dpy != null && dpy != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (surf != null) EGL14.eglDestroySurface(dpy, surf)
                    if (ctx != null) EGL14.eglDestroyContext(dpy, ctx)
                    EGL14.eglTerminate(dpy)
                }
            } catch (_: Throwable) {
            }
        }
        val rl = r.lowercase()
        val v = when {
            !eglOk -> Verdict.WEAK
            isEmulatorBuild() || DENY.any { rl.contains(it) } || mt <= 4096 -> Verdict.WEAK
            ALLOW.any { rl.contains(it) } && mt >= 8192 -> Verdict.GOOD
            else -> Verdict.UNKNOWN
        }
        return Triple(v, if (mt > 0) mt else 2048, r)
    }

    private fun isEmulatorBuild(): Boolean {
        // Strongest spoof-proof Build signal: an x86/x86_64 ABI. Emulators and
        // app-players (BlueStacks, AVD, Genymotion, …) run on x86 hosts and CANNOT
        // hide it even while spoofing an ARM model + GPU. BlueStacks reports
        // GL_RENDERER='Adreno (TM) 650', MODEL='SM-G998B', HARDWARE='exynos2100' —
        // all fake — but SUPPORTED_ABIS is x86_64. A real ARM phone never lists an
        // x86 ABI; the rare genuine x86 Android device is itself weak → WEAK is safe.
        if (Build.SUPPORTED_ABIS.any { it.lowercase().startsWith("x86") }) return true
        val fp = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val hw = Build.HARDWARE.lowercase()
        val product = Build.PRODUCT.lowercase()
        val brand = Build.BRAND.lowercase()
        val manuf = Build.MANUFACTURER.lowercase()
        return fp.contains("generic") || fp.startsWith("google/sdk_gphone") || fp.contains("emulator") ||
            fp.contains("bluestacks") || fp.contains("vbox") ||
            model.contains("emulator") || model.contains("android sdk built for") || model.contains("sdk_gphone") ||
            hw in listOf("goldfish", "ranchu", "vbox86", "ttvm", "nox") ||
            product.contains("sdk") || product.contains("emulator") || product.contains("vbox") ||
            brand.startsWith("generic") ||
            manuf.contains("genymotion") || manuf.contains("bluestacks")
    }
}
