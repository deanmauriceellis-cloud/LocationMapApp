/*
 * WickedSalemWitchCityTour v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * S231 — runtime parser + evaluator for the splash decision tree.
 *
 * Loads splash_tree_v1.json from assets (baked from cache-proxy/data/ by the
 * publish-splash-tree.js script). Provides:
 *   - SplashTree.fromAssets(context) — parse + cache
 *   - SplashEngine.pick(tree, ctx?) — bucket selection + variant pick + slot fill
 *
 * Mirrors cache-proxy/lib/splash-tree-types.js + admin-splash-tree.js
 * selectBucket/fillSlots so the editor preview pane and the device match.
 */
package com.example.wickedsalemwitchcitytour.splash

import android.content.Context
import com.example.locationmapapp.util.DebugLogger
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlin.random.Random

private const val TAG = "SplashTree"
private const val ASSET_NAME = "splash_tree_v1.json"

// ──────────────────────────────────────────────────────────────────────────
// Schema (mirrors cache-proxy/lib/splash-tree-types.js)
// ──────────────────────────────────────────────────────────────────────────

data class Variant(
    val id: String,
    val text: String,
    val notes: String? = null,
    val weight: Double? = null,
)

data class Trigger(
    @SerializedName("distance_min_mi") val distanceMinMi: Double? = null,
    @SerializedName("distance_max_mi") val distanceMaxMi: Double? = null,
    val movement: List<String>? = null,
    @SerializedName("place_kind") val placeKind: List<String>? = null,
    @SerializedName("town_name") val townName: String? = null,
    @SerializedName("no_gps") val noGps: Boolean? = null,
)

data class Bucket(
    val id: String,
    val label: String,
    val priority: Int,
    val trigger: Trigger,
    val variants: List<Variant>,
)

data class SplashTree(
    @SerializedName("schema_version") val schemaVersion: Int,
    @SerializedName("updated_at") val updatedAt: String? = null,
    val buckets: List<Bucket>,
    val fallback: Bucket,
) {
    companion object {
        @Volatile private var cached: SplashTree? = null

        /** Load + parse the bundled tree. Cached after first call. */
        fun fromAssets(context: Context): SplashTree? {
            cached?.let { return it }
            return try {
                val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
                val tree = Gson().fromJson(raw, SplashTree::class.java)
                if (tree.schemaVersion != 1) {
                    DebugLogger.w(TAG, "schema_version=${tree.schemaVersion}, expected 1 — proceeding anyway")
                }
                cached = tree
                DebugLogger.i(TAG, "loaded tree: ${tree.buckets.size} buckets + fallback, " +
                    "updated=${tree.updatedAt ?: "?"}")
                tree
            } catch (t: Throwable) {
                DebugLogger.e(TAG, "load failed: ${t.message}")
                null
            }
        }

        /** Drop the cache — exposed for tests. */
        fun resetCache() { cached = null }
    }
}

// ──────────────────────────────────────────────────────────────────────────
// Engine: bucket selection + slot fill
// ──────────────────────────────────────────────────────────────────────────

object SplashEngine {

    /**
     * Pick a (bucket, variant, rendered text) for the given context. `ctx` may
     * be null when no GPS / no last-known fix is available; the fallback
     * bucket fires in that case (no_gps trigger).
     */
    fun pick(tree: SplashTree, ctx: LocationContext?): Pick {
        val bucket = selectBucket(tree, ctx)
            ?: return Pick(null, null, "Welcome to Katrina's Mystic Visitors Guide.")
        val variant = selectVariant(bucket.variants)
            ?: return Pick(bucket, null, "Welcome to Katrina's Mystic Visitors Guide.")
        val rendered = fillSlots(variant.text, ctx)
        return Pick(bucket, variant, rendered)
    }

    private fun selectBucket(tree: SplashTree, ctx: LocationContext?): Bucket? {
        // Priority desc; ties broken by array order (stable in Kotlin sortedByDescending).
        for (b in tree.buckets.sortedByDescending { it.priority }) {
            if (triggerMatches(b.trigger, ctx)) return b
        }
        return tree.fallback
    }

    private fun triggerMatches(t: Trigger, ctx: LocationContext?): Boolean {
        if (t.noGps == true) return ctx == null
        if (ctx == null) return false
        if (t.distanceMinMi != null && ctx.miles < t.distanceMinMi) return false
        if (t.distanceMaxMi != null && ctx.miles > t.distanceMaxMi) return false
        if (t.movement != null && !t.movement.contains(ctx.movement.name)) return false
        if (t.placeKind != null && !t.placeKind.contains(ctx.placeKind.name)) return false
        if (t.townName != null && ctx.town != t.townName) return false
        return true
    }

    private fun selectVariant(variants: List<Variant>): Variant? {
        if (variants.isEmpty()) return null
        val totalWeight = variants.sumOf { it.weight ?: 1.0 }
        if (totalWeight <= 0.0) return variants.random()
        var pick = Random.nextDouble() * totalWeight
        for (v in variants) {
            pick -= (v.weight ?: 1.0)
            if (pick <= 0.0) return v
        }
        return variants.last()
    }

    /**
     * Replace `{slot}` tokens in a template with the corresponding LocationContext
     * value (or empty string if the slot has no value). Same set the editor
     * pane uses, kept in sync with cache-proxy/lib/admin-splash-tree.js fillSlots.
     */
    fun fillSlots(template: String, ctx: LocationContext?): String {
        if (ctx == null) {
            // No-context slots are all empty.
            return template.replace(SLOT_RE) { "" }
        }
        return template.replace(SLOT_RE) { m ->
            slotValue(m.groupValues[1], ctx) ?: ""
        }
    }

    private fun slotValue(name: String, ctx: LocationContext): String? = when (name) {
        "miles"          -> formatMiles(ctx.miles)
        "miles_int"      -> ctx.milesInt.toString()
        "city"           -> ctx.city
        "near_city"      -> ctx.nearCity
        "town"           -> ctx.town
        "county"         -> ctx.county
        "state"          -> ctx.state
        "state_long"     -> ctx.stateLong
        "compass"        -> ctx.compass
        "compass_short"  -> ctx.compassShort
        "movement"       -> ctx.movement.name.lowercase()
        "place"          -> ctx.place
        else             -> null
    }

    private fun formatMiles(m: Double): String =
        if (m >= 10) m.toInt().toString() else "%.1f".format(m)

    private val SLOT_RE = Regex("""\{([a-z_]+)\}""")
}

/** Result of [SplashEngine.pick]. `bucket` and `variant` are null only on catastrophic load failure. */
data class Pick(val bucket: Bucket?, val variant: Variant?, val text: String)

// Suppress unused-import warning when Gson's JsonObject helper isn't directly referenced.
@Suppress("unused")
private val keepImport: JsonObject? = null
