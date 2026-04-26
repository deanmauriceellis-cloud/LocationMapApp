package com.example.locationmapapp.core.routing

/** A geographic point in WGS84/NAD83-equivalent decimal degrees. */
data class RoutingLatLng(val lat: Double, val lng: Double)

/** Reference to a node in a [RoutingBundle], post-load. */
data class NodeRef(val internalIdx: Int, val nodeId: Long, val lat: Double, val lng: Double)

/** One step of a routed path, between two nodes along one edge. */
data class RouteEdgeStep(
    val edgeId: Long,
    val fullname: String,
    val mtfcc: String?,
    val lengthM: Double,
    /** Polyline points in the direction of travel (always at least 2). */
    val polyline: List<RoutingLatLng>,
)

/**
 * Result of a routing call. Geometry is the concatenated polyline (start->end);
 * `edges` exposes the per-edge breakdown for turn-by-turn synthesis.
 */
data class RouteResult(
    val geometry: List<RoutingLatLng>,
    val distanceM: Double,
    val durationS: Double,
    val edges: List<RouteEdgeStep>,
)
