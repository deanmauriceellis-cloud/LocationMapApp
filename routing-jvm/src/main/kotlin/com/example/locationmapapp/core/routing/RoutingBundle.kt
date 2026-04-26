package com.example.locationmapapp.core.routing

import kotlin.math.max
import kotlin.math.min

/**
 * In-memory CSR (compressed sparse row) representation of the Salem routing graph.
 * Built once from `salem-routing-graph.sqlite` by [RoutingBundleLoader] and held
 * for the life of the process.
 *
 * The graph is undirected for pedestrians: every edge in the bundle file is
 * stored twice in the CSR (once per direction). The original edge id and its
 * polyline are stored once in the edge tables; the per-direction adjacency
 * holds an `edgeIdx` and a `reversed` flag so the polyline can be emitted in
 * the direction of travel.
 *
 * KNN snap uses planar SRID-4269 degree distance (raw squared lat/lng diff)
 * to match TigerLine's `tiger.nearest_walkable_node()` `<->` semantic — see
 * tools/routing-bake/SCHEMA.md.
 */
class RoutingBundle internal constructor(
    /** Number of nodes. */
    val nodeCount: Int,
    /** External (TigerLine) node ids, indexed by internal idx. */
    private val nodeIds: LongArray,
    private val nodeLat: DoubleArray,
    private val nodeLng: DoubleArray,
    private val nodeWalkable: BooleanArray,

    /** CSR adjacency: per node, edgeOffset[i]..edgeOffset[i+1] index into adjEdgeIdx/adjNeighbor. */
    private val edgeOffset: IntArray,
    private val adjNeighbor: IntArray,
    private val adjEdgeIdx: IntArray,
    private val adjReversed: BooleanArray,

    /** Per-edge tables, indexed by edgeIdx. */
    private val edgeId: LongArray,
    private val edgeLengthM: DoubleArray,
    private val edgeFullname: Array<String>,
    private val edgeMtfcc: Array<String?>,
    /** Polyline points, packed: lat0,lng0,lat1,lng1,... per edge. */
    private val edgePolylinePacked: Array<DoubleArray>,

    /** Walkable-node spatial grid index for fast KNN. */
    private val grid: NodeGrid,

    /** Bundle metadata (passthrough from the SQLite meta table). */
    val meta: Map<String, String>,
) {
    val walkingPaceMps: Double get() = meta["walking_pace_mps"]?.toDoubleOrNull() ?: 1.4

    fun nodeAt(idx: Int): NodeRef = NodeRef(idx, nodeIds[idx], nodeLat[idx], nodeLng[idx])
    fun isWalkable(idx: Int): Boolean = nodeWalkable[idx]

    /** Iterate adjacency of node [u]. The lambda receives (neighborInternalIdx, edgeIdx, reversed). */
    inline fun forEachAdj(u: Int, body: (Int, Int, Boolean) -> Unit) {
        val start = edgeOffsetOf(u)
        val end = edgeOffsetOf(u + 1)
        for (k in start until end) {
            body(adjNeighborAt(k), adjEdgeIdxAt(k), adjReversedAt(k))
        }
    }

    /** KNN snap to the nearest walkable node, planar degree distance. */
    fun nearestWalkableNode(lat: Double, lng: Double): NodeRef? {
        val nodeIdx = grid.nearestWalkable(lat, lng, this) ?: return null
        return nodeAt(nodeIdx)
    }

    fun edgeLengthM(edgeIdx: Int): Double = edgeLengthM[edgeIdx]
    fun edgeFullname(edgeIdx: Int): String = edgeFullname[edgeIdx]
    fun edgeMtfcc(edgeIdx: Int): String? = edgeMtfcc[edgeIdx]
    fun edgeIdExternal(edgeIdx: Int): Long = edgeId[edgeIdx]

    /** Returns the polyline for [edgeIdx] in the direction implied by [reversed]. */
    fun edgePolyline(edgeIdx: Int, reversed: Boolean): List<RoutingLatLng> {
        val packed = edgePolylinePacked[edgeIdx]
        val n = packed.size / 2
        val out = ArrayList<RoutingLatLng>(n)
        if (!reversed) {
            var i = 0
            while (i < packed.size) { out.add(RoutingLatLng(packed[i], packed[i + 1])); i += 2 }
        } else {
            var i = packed.size - 2
            while (i >= 0) { out.add(RoutingLatLng(packed[i], packed[i + 1])); i -= 2 }
        }
        return out
    }

    // Inlined accessors so forEachAdj stays branchless.
    @PublishedApi internal fun edgeOffsetOf(i: Int): Int = edgeOffset[i]
    @PublishedApi internal fun adjNeighborAt(k: Int): Int = adjNeighbor[k]
    @PublishedApi internal fun adjEdgeIdxAt(k: Int): Int = adjEdgeIdx[k]
    @PublishedApi internal fun adjReversedAt(k: Int): Boolean = adjReversed[k]

    /** Map external node id -> internal idx. Used during loading and tests. */
    fun internalIdxOf(externalNodeId: Long): Int? = nodeIdToIdx[externalNodeId]
    private val nodeIdToIdx: Map<Long, Int> by lazy {
        HashMap<Long, Int>(nodeIds.size).also { m ->
            for (i in nodeIds.indices) m[nodeIds[i]] = i
        }
    }

    /** Cell-grid spatial index over walkable nodes. Fixed cell size (~0.0015° ≈ 165m lat). */
    internal class NodeGrid(
        private val minLat: Double,
        private val minLng: Double,
        private val cellLat: Double,
        private val cellLng: Double,
        private val rows: Int,
        private val cols: Int,
        /** cells[row*cols+col] = list of walkable internal node indices in that cell */
        private val cells: Array<IntArray>,
    ) {
        fun nearestWalkable(lat: Double, lng: Double, bundle: RoutingBundle): Int? {
            val centerRow = ((lat - minLat) / cellLat).toInt().coerceIn(0, rows - 1)
            val centerCol = ((lng - minLng) / cellLng).toInt().coerceIn(0, cols - 1)
            var best = -1
            var bestD2 = Double.MAX_VALUE
            // Expanding ring search. At the planar-degree scale a 3-ring search
            // covers ~500m which always contains *something* in Salem coverage.
            for (ring in 0..16) {
                val r0 = max(0, centerRow - ring); val r1 = min(rows - 1, centerRow + ring)
                val c0 = max(0, centerCol - ring); val c1 = min(cols - 1, centerCol + ring)
                for (r in r0..r1) for (c in c0..c1) {
                    if (ring > 0 && r != r0 && r != r1 && c != c0 && c != c1) continue
                    for (idx in cells[r * cols + c]) {
                        val dlat = bundle.nodeLat[idx] - lat
                        val dlng = bundle.nodeLng[idx] - lng
                        val d2 = dlat * dlat + dlng * dlng
                        if (d2 < bestD2) { bestD2 = d2; best = idx }
                    }
                }
                if (best >= 0 && ring >= 1) return best
            }
            return if (best >= 0) best else null
        }
    }

    companion object {
        /**
         * Build a [RoutingBundle] from the parallel arrays the loader populates.
         * Constructs the CSR adjacency and the spatial grid index.
         *
         * @param srcNodeIdx source internal node index per raw edge (NOT external id)
         * @param tgtNodeIdx target internal node index per raw edge
         */
        fun build(
            nodeIds: LongArray,
            nodeLat: DoubleArray,
            nodeLng: DoubleArray,
            nodeWalkable: BooleanArray,
            edgeId: LongArray,
            srcNodeIdx: IntArray,
            tgtNodeIdx: IntArray,
            edgeLengthM: DoubleArray,
            edgeFullname: Array<String>,
            edgeMtfcc: Array<String?>,
            edgePolylinePacked: Array<DoubleArray>,
            meta: Map<String, String>,
        ): RoutingBundle {
            val n = nodeIds.size
            val e = edgeId.size

            // Count adjacency per node (each edge contributes to BOTH endpoints).
            val degree = IntArray(n)
            for (i in 0 until e) {
                degree[srcNodeIdx[i]]++
                degree[tgtNodeIdx[i]]++
            }
            val edgeOffset = IntArray(n + 1)
            for (i in 0 until n) edgeOffset[i + 1] = edgeOffset[i] + degree[i]

            val total = edgeOffset[n]
            val adjNeighbor = IntArray(total)
            val adjEdgeIdx = IntArray(total)
            val adjReversed = BooleanArray(total)
            val cursor = IntArray(n) // running insertion cursor per node
            for (i in 0 until e) {
                val s = srcNodeIdx[i]
                val t = tgtNodeIdx[i]
                run {
                    val k = edgeOffset[s] + cursor[s]; cursor[s]++
                    adjNeighbor[k] = t; adjEdgeIdx[k] = i; adjReversed[k] = false
                }
                run {
                    val k = edgeOffset[t] + cursor[t]; cursor[t]++
                    adjNeighbor[k] = s; adjEdgeIdx[k] = i; adjReversed[k] = true
                }
            }

            // Build the spatial grid over walkable nodes only (snap target).
            val walkableLats = DoubleArray(n)
            val walkableLngs = DoubleArray(n)
            var walkN = 0
            var minLat = Double.POSITIVE_INFINITY; var maxLat = Double.NEGATIVE_INFINITY
            var minLng = Double.POSITIVE_INFINITY; var maxLng = Double.NEGATIVE_INFINITY
            for (i in 0 until n) {
                if (nodeWalkable[i]) {
                    walkableLats[walkN] = nodeLat[i]; walkableLngs[walkN] = nodeLng[i]; walkN++
                    if (nodeLat[i] < minLat) minLat = nodeLat[i]
                    if (nodeLat[i] > maxLat) maxLat = nodeLat[i]
                    if (nodeLng[i] < minLng) minLng = nodeLng[i]
                    if (nodeLng[i] > maxLng) maxLng = nodeLng[i]
                }
            }
            val cellLat = 0.0015 // ~165m at 42.5°
            val cellLng = 0.0020 // ~165m at 42.5°
            val rows = max(1, ((maxLat - minLat) / cellLat).toInt() + 1)
            val cols = max(1, ((maxLng - minLng) / cellLng).toInt() + 1)
            val tally = IntArray(rows * cols)
            for (i in 0 until n) {
                if (!nodeWalkable[i]) continue
                val r = ((nodeLat[i] - minLat) / cellLat).toInt().coerceIn(0, rows - 1)
                val c = ((nodeLng[i] - minLng) / cellLng).toInt().coerceIn(0, cols - 1)
                tally[r * cols + c]++
            }
            val cells = Array(rows * cols) { IntArray(tally[it]) }
            val cur = IntArray(rows * cols)
            for (i in 0 until n) {
                if (!nodeWalkable[i]) continue
                val r = ((nodeLat[i] - minLat) / cellLat).toInt().coerceIn(0, rows - 1)
                val c = ((nodeLng[i] - minLng) / cellLng).toInt().coerceIn(0, cols - 1)
                val cellIdx = r * cols + c
                cells[cellIdx][cur[cellIdx]++] = i
            }
            val grid = NodeGrid(minLat, minLng, cellLat, cellLng, rows, cols, cells)

            return RoutingBundle(
                nodeCount = n,
                nodeIds = nodeIds,
                nodeLat = nodeLat,
                nodeLng = nodeLng,
                nodeWalkable = nodeWalkable,
                edgeOffset = edgeOffset,
                adjNeighbor = adjNeighbor,
                adjEdgeIdx = adjEdgeIdx,
                adjReversed = adjReversed,
                edgeId = edgeId,
                edgeLengthM = edgeLengthM,
                edgeFullname = edgeFullname,
                edgeMtfcc = edgeMtfcc,
                edgePolylinePacked = edgePolylinePacked,
                grid = grid,
                meta = meta,
            )
        }
    }
}
