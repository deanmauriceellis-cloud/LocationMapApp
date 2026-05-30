/*
 * WickedSalemWitchCityTour v1.5 — S306 (perf/stability Tier 1 — bitmap downsampling)
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */

package com.example.wickedsalemwitchcitytour.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.IOException

/**
 * S306 — shared two-pass asset decoder with `inSampleSize` downsampling.
 *
 * Extracts the defensive bounds-probe-then-sampled-decode pattern that lived
 * privately in [HeroAssetLoader.decodeAsset] (S255) so the live bitmap paths
 * that were decoding full-resolution into display-sized views can share it.
 *
 * Why this exists (PerfStabilityReview_20260529 Tier 1): the S299/S301 graphics
 * redo shipped 384×384 ghost portraits, 1152×512 heroes, and 1152×512 poi-icons
 * that were being decoded at full ARGB_8888 resolution (576 KB / 2.25 MB each)
 * for markers/dock-circles rendered at ≤64 dp. Under 3D tilt the extended
 * viewport makes many of these resident at once → the Large-Object-Space
 * pressure that tipped the (always-heavy) tilt geometry into OOM. Decoding to
 * the actual target size cuts each bitmap 4–16× with no visible change at the
 * rendered size. These are NOT map tiles — downsampling them carries zero
 * blank-wedge risk (the operator's hard tilt-coverage constraint is untouched).
 */
object SampledAssetDecoder {

    /**
     * Decode [assetPath] downsampled so the result is no smaller than
     * [reqW]×[reqH] (power-of-2 `inSampleSize`, same rule as the original
     * [HeroAssetLoader] cap). Returns null on missing asset or decode failure.
     * `inJustDecodeBounds` first pass reads only the header, so the full
     * full-resolution bitmap is never materialised.
     */
    fun decode(context: Context, assetPath: String, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = calcSampleSize(bounds.outWidth, bounds.outHeight, reqW, reqH)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.assets.open(assetPath).use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (_: IOException) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Largest power-of-2 `inSampleSize` that keeps BOTH decoded dimensions
     * ≥ the requested size (so the bitmap is never upscaled at render). For a
     * 384² source at req 192 → 2 (→192²); a 1152×512 source at req 125 → 4
     * (→288×128). A source already at/under target returns 1 (no downsample).
     */
    fun calcSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        var sample = 1
        while (srcW / (sample * 2) >= reqW && srcH / (sample * 2) >= reqH) {
            sample *= 2
        }
        return sample
    }
}
