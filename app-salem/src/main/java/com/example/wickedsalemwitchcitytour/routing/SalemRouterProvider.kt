package com.example.wickedsalemwitchcitytour.routing

import android.content.Context
import com.example.locationmapapp.core.routing.Router
import com.example.locationmapapp.core.routing.RoutingBundle
import com.example.locationmapapp.core.routing.RoutingBundleLoader
import com.example.locationmapapp.util.DebugLogger
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the lazy-loaded Salem [Router]. The bundle is shipped as an APK asset
 * (`assets/routing/salem-routing-graph.sqlite`); on first access we copy it
 * to internal storage so SQLiteDatabase can open it as a real file, then
 * build the in-memory CSR graph once and hold the [Router] for the life of
 * the process.
 *
 * Loading the 4 MB bundle takes ~80-150 ms on the Lenovo (most of that is
 * SQLite cursor traversal). Callers that have a UI thread should call
 * [routerOrNull] on a background dispatcher; subsequent calls are O(1).
 */
@Singleton
class SalemRouterProvider @Inject constructor(
    private val context: Context,
) {
    @Volatile private var cached: Router? = null
    private val lock = Any()

    /**
     * Get the router. Blocks the caller on first invocation while the bundle
     * is unpacked + parsed. Returns null if the bundle is missing or corrupt
     * (logged once; consumer should treat as "Directions feature unavailable").
     */
    fun routerOrNull(): Router? {
        cached?.let { return it }
        synchronized(lock) {
            cached?.let { return it }
            val r = try {
                val t0 = System.currentTimeMillis()
                val file = ensureBundleOnDisk()
                val bundle: RoutingBundle = RoutingBundleLoader.load(file)
                val r = Router(bundle)
                DebugLogger.i(
                    TAG,
                    "Routing bundle loaded in ${System.currentTimeMillis() - t0}ms " +
                        "(${bundle.meta["edge_count"]} edges, ${bundle.meta["walkable_node_count"]} walkable nodes)",
                )
                r
            } catch (e: Throwable) {
                DebugLogger.e(TAG, "Failed to load routing bundle: ${e.message}", e)
                null
            }
            cached = r
            return r
        }
    }

    private fun ensureBundleOnDisk(): File {
        val outDir = File(context.filesDir, "routing").apply { mkdirs() }
        val outFile = File(outDir, BUNDLE_NAME)
        // Copy if missing or if asset has been updated (size mismatch).
        val assetSize = context.assets.openFd("routing/$BUNDLE_NAME").use { it.length }
        if (!outFile.exists() || outFile.length() != assetSize) {
            context.assets.open("routing/$BUNDLE_NAME").use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            DebugLogger.i(TAG, "Unpacked routing bundle ($assetSize bytes) to $outFile")
        }
        return outFile
    }

    companion object {
        private const val TAG = "SalemRouterProvider"
        private const val BUNDLE_NAME = "salem-routing-graph.sqlite"
    }
}
