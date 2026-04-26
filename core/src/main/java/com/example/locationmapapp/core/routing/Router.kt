package com.example.locationmapapp.core.routing

import java.util.PriorityQueue

/**
 * On-device walking router over a [RoutingBundle]. Pure Kotlin — no Android
 * dependency — so it runs in JVM unit tests as well as on-device.
 *
 * The router mirrors TigerLine's `tiger.route_walking()` contract: planar
 * SRID-4269 KNN snap, undirected pedestrian graph, walking pace from
 * `meta.walking_pace_mps`. Returned distances match the live SQL function
 * exactly for any pair of inputs whose snap nodes lie inside the bundle's bbox.
 */
class Router(private val bundle: RoutingBundle) {

    private val pace: Double = bundle.walkingPaceMps

    /** Walking pace in metres per second (from the bundle meta table). */
    fun bundlePaceMps(): Double = pace

    /** Snap to nearest walkable node. Null only if the bundle is empty. */
    fun nearestWalkableNode(lat: Double, lng: Double): NodeRef? =
        bundle.nearestWalkableNode(lat, lng)

    /**
     * Single-pair walking route. Returns null if no walkable path exists,
     * or a degenerate result (empty geometry, distance 0) if both endpoints
     * snap to the same node.
     */
    fun route(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double): RouteResult? {
        val s = bundle.nearestWalkableNode(fromLat, fromLng) ?: return null
        val t = bundle.nearestWalkableNode(toLat, toLng) ?: return null
        return routeBetween(s, t)
    }

    /**
     * Multi-stop walking route. Routes pairwise between consecutive stops
     * and concatenates. Returns null if any segment fails or fewer than 2
     * stops were given.
     */
    fun routeMulti(stops: List<RoutingLatLng>): RouteResult? {
        if (stops.size < 2) return null
        val parts = ArrayList<RouteResult>(stops.size - 1)
        for (i in 0 until stops.size - 1) {
            val seg = route(stops[i].lat, stops[i].lng, stops[i + 1].lat, stops[i + 1].lng)
                ?: return null
            parts.add(seg)
        }
        return concat(parts)
    }

    private fun routeBetween(s: NodeRef, t: NodeRef): RouteResult {
        if (s.internalIdx == t.internalIdx) {
            return RouteResult(emptyList(), 0.0, 0.0, emptyList())
        }

        val n = bundle.nodeCount
        val dist = DoubleArray(n) { Double.POSITIVE_INFINITY }
        val prevNode = IntArray(n) { -1 }
        val prevEdgeIdx = IntArray(n) { -1 }
        val prevReversed = BooleanArray(n)
        dist[s.internalIdx] = 0.0

        // Open set as a binary heap over (distance, nodeIdx). Lazy deletion via dist check.
        val heap = PriorityQueue<LongArray>(64) { a, b ->
            java.lang.Double.compare(java.lang.Double.longBitsToDouble(a[0]), java.lang.Double.longBitsToDouble(b[0]))
        }
        heap.add(longArrayOf(java.lang.Double.doubleToRawLongBits(0.0), s.internalIdx.toLong()))

        while (heap.isNotEmpty()) {
            val top = heap.poll() ?: break
            val d = java.lang.Double.longBitsToDouble(top[0])
            val u = top[1].toInt()
            if (d > dist[u]) continue
            if (u == t.internalIdx) break
            bundle.forEachAdj(u) { v, edgeIdx, reversed ->
                val nd = d + bundle.edgeLengthM(edgeIdx)
                if (nd < dist[v]) {
                    dist[v] = nd
                    prevNode[v] = u
                    prevEdgeIdx[v] = edgeIdx
                    prevReversed[v] = reversed
                    heap.add(longArrayOf(java.lang.Double.doubleToRawLongBits(nd), v.toLong()))
                }
            }
        }

        if (prevNode[t.internalIdx] == -1) {
            // Unreachable.
            return RouteResult(emptyList(), 0.0, 0.0, emptyList())
        }

        // Reconstruct edge list back to front, then reverse.
        val edgeStack = ArrayDeque<Int>()
        val reversedStack = ArrayDeque<Boolean>()
        var cur = t.internalIdx
        while (cur != s.internalIdx) {
            edgeStack.addLast(prevEdgeIdx[cur])
            reversedStack.addLast(prevReversed[cur])
            cur = prevNode[cur]
        }
        val edgeIdxOrdered = edgeStack.toList().asReversed()
        val reversedOrdered = reversedStack.toList().asReversed()

        val steps = ArrayList<RouteEdgeStep>(edgeIdxOrdered.size)
        val geometry = ArrayList<RoutingLatLng>(edgeIdxOrdered.size + 1)
        var totalM = 0.0
        for (i in edgeIdxOrdered.indices) {
            val eIdx = edgeIdxOrdered[i]
            val rev = reversedOrdered[i]
            val poly = bundle.edgePolyline(eIdx, rev)
            val lengthM = bundle.edgeLengthM(eIdx)
            totalM += lengthM
            steps.add(
                RouteEdgeStep(
                    edgeId = bundle.edgeIdExternal(eIdx),
                    fullname = bundle.edgeFullname(eIdx),
                    mtfcc = bundle.edgeMtfcc(eIdx),
                    lengthM = lengthM,
                    polyline = poly,
                )
            )
            // Append polyline points; skip the first point of every segment after the first
            // to avoid duplicating the join point.
            val skipFirst = i > 0
            for (j in poly.indices) {
                if (skipFirst && j == 0) continue
                geometry.add(poly[j])
            }
        }
        return RouteResult(geometry, totalM, totalM / pace, steps)
    }

    private fun concat(parts: List<RouteResult>): RouteResult {
        val geom = ArrayList<RoutingLatLng>()
        val edges = ArrayList<RouteEdgeStep>()
        var totalM = 0.0
        for ((i, p) in parts.withIndex()) {
            edges.addAll(p.edges)
            for ((j, pt) in p.geometry.withIndex()) {
                if (i > 0 && j == 0) continue
                geom.add(pt)
            }
            totalM += p.distanceM
        }
        return RouteResult(geom, totalM, totalM / pace, edges)
    }
}
