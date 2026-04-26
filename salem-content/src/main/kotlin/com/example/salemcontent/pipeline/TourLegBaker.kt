/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.salemcontent.pipeline

import com.example.locationmapapp.core.routing.JdbcRoutingBundleLoader
import com.example.locationmapapp.core.routing.Router
import com.example.locationmapapp.core.routing.RoutingLatLng
import java.io.File

/**
 * Pre-computes walking legs between consecutive [OutputTourStop]s in each tour
 * using the same `salem-routing-graph.sqlite` bundle the runtime Android
 * Router uses. Output rows go to the `tour_legs` table so the runtime tour
 * player can draw polylines without loading the bundle or running Dijkstra.
 *
 * Distances are bit-exact with the runtime Router (and with TigerLine's
 * `tiger.route_walking()`, per the parity tests in `:routing-jvm`).
 *
 * Skipped pairs (logged, not fatal):
 *  - Either stop's POI is missing from the tour-POI set (no coords available)
 *  - The router returns null (no walkable path between the snapped nodes)
 *  - The router returns a degenerate empty result (both stops snap to the
 *    same node — typically two stops within ~165m at the same intersection)
 */
object TourLegBaker {

    data class Result(
        val legs: List<OutputTourLeg>,
        val skippedNoCoords: Int,
        val skippedNoRoute: Int,
        val skippedDegenerate: Int,
    )

    fun bake(
        tourPois: List<OutputTourPoi>,
        tourStops: List<OutputTourStop>,
        bundleFile: File,
    ): Result {
        require(bundleFile.exists()) { "routing bundle not found: ${bundleFile.absolutePath}" }

        val bundle = JdbcRoutingBundleLoader.load(bundleFile)
        val router = Router(bundle)
        val poiCoords: Map<String, RoutingLatLng> =
            tourPois.associate { it.id to RoutingLatLng(it.lat, it.lng) }

        val legs = ArrayList<OutputTourLeg>(tourStops.size)
        var noCoords = 0
        var noRoute = 0
        var degenerate = 0

        val byTour = tourStops.groupBy { it.tourId }
        for ((tourId, stopsRaw) in byTour) {
            val stops = stopsRaw.sortedBy { it.stopOrder }
            for (i in 0 until stops.size - 1) {
                val a = stops[i]
                val b = stops[i + 1]
                val ac = poiCoords[a.poiId]
                val bc = poiCoords[b.poiId]
                if (ac == null || bc == null) {
                    println(
                        "  [TourLegBaker] skip $tourId stop ${a.stopOrder}->${b.stopOrder}: " +
                            "missing coords for ${a.poiId} or ${b.poiId}"
                    )
                    noCoords++
                    continue
                }
                val r = router.route(ac.lat, ac.lng, bc.lat, bc.lng)
                if (r == null) {
                    println(
                        "  [TourLegBaker] skip $tourId stop ${a.stopOrder}->${b.stopOrder}: " +
                            "router returned null (no walkable path)"
                    )
                    noRoute++
                    continue
                }
                if (r.geometry.isEmpty()) {
                    println(
                        "  [TourLegBaker] skip $tourId stop ${a.stopOrder}->${b.stopOrder}: " +
                            "degenerate route (stops snap to same node)"
                    )
                    degenerate++
                    continue
                }
                legs += OutputTourLeg(
                    tourId = tourId,
                    fromStopOrder = a.stopOrder,
                    toStopOrder = b.stopOrder,
                    fromPoiId = a.poiId,
                    toPoiId = b.poiId,
                    distanceM = r.distanceM,
                    durationS = r.durationS,
                    edgeCount = r.edges.size,
                    geometry = encodePolyline(r.geometry),
                )
            }
        }
        return Result(legs, noCoords, noRoute, degenerate)
    }

    private fun encodePolyline(pts: List<RoutingLatLng>): String =
        buildString(pts.size * 22) {
            for ((i, p) in pts.withIndex()) {
                if (i > 0) append(';')
                append(p.lat)
                append(',')
                append(p.lng)
            }
        }
}
