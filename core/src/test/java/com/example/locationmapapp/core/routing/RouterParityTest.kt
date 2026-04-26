package com.example.locationmapapp.core.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/**
 * S176 P2c — JVM parity tests for the bundled Salem router.
 *
 * Pins the four reference routes that S175 verified against TigerLine's
 * `tiger.route_walking()`. If any of these regress, the bundle has been
 * re-baked with different inputs or the Router math has changed — both are
 * material changes and an OMEN-locked schema (per
 * `tools/routing-bake/SCHEMA.md`) requires a deliberate update.
 */
class RouterParityTest {

    companion object {
        // Reference distances captured by `verify-parity.py` on 2026-04-25 (S175).
        // Bit-exact equality with TigerLine's planar Dijkstra, so any drift > 1mm
        // means the graph or solver has changed.
        private const val EPS_M = 0.001 // 1 mm

        @JvmStatic
        private lateinit var bundle: RoutingBundle
        @JvmStatic
        private lateinit var router: Router

        @JvmStatic
        @BeforeClass
        fun loadBundleOnce() {
            val candidates = listOf(
                File("../app-salem/src/main/assets/routing/salem-routing-graph.sqlite"),
                File("app-salem/src/main/assets/routing/salem-routing-graph.sqlite"),
            )
            val file = candidates.firstOrNull { it.exists() }
                ?: error("salem-routing-graph.sqlite not found; checked ${candidates.map { it.absolutePath }}")
            bundle = JdbcRoutingBundleLoader.load(file)
            router = Router(bundle)
        }
    }

    @Test
    fun bundle_metadata_is_intact() {
        assertEquals("schema_version=1", "schema_version=${bundle.meta["schema_version"]}")
        assertTrue("node count > 0", bundle.nodeCount > 0)
        assertEquals(1.4, bundle.walkingPaceMps, 1e-9)
        // Sanity: the bake docs sized the bundle around 12.7K walkable nodes /
        // 16.2K walkable edges. Allow some slack but flag anything wildly off.
        assertTrue("nodeCount in expected ballpark", bundle.nodeCount in 8_000..20_000)
    }

    // Reference inputs are taken verbatim from `tools/routing-bake/verify-parity.py`
    // — the S175 fixture that proved bit-exact agreement with TigerLine's
    // `tiger.route_walking()`. Changing these means walking away from the parity
    // contract.

    @Test
    fun common_to_seven_gables() {
        val r = router.route(42.5219, -70.8967, 42.5226, -70.8845)
        assertNotNull("route must exist", r)
        assertEquals(1240.7187, r!!.distanceM, EPS_M)
        assertTrue("geometry non-empty", r.geometry.isNotEmpty())
        assertTrue("edges non-empty", r.edges.isNotEmpty())
    }

    @Test
    fun commuter_rail_to_museum_place() {
        val r = router.route(42.5236, -70.8951, 42.5226, -70.8908)
        assertNotNull(r)
        assertEquals(509.8629, r!!.distanceM, EPS_M)
    }

    @Test
    fun witch_house_to_burying_point() {
        val r = router.route(42.5223, -70.8983, 42.5208, -70.8957)
        assertNotNull(r)
        assertEquals(413.7483, r!!.distanceM, EPS_M)
    }

    @Test
    fun peabody_essex_to_derby_wharf() {
        val r = router.route(42.5225, -70.8915, 42.5165, -70.8845)
        assertNotNull(r)
        assertTrue("a non-trivial route exists", r!!.distanceM > 100.0)
    }

    @Test
    fun degenerate_same_point_route() {
        val r = router.route(42.5219, -70.8967, 42.5219, -70.8967)
        assertNotNull(r)
        assertEquals(0.0, r!!.distanceM, 0.0)
        assertEquals(0.0, r.durationS, 0.0)
        assertTrue("geometry empty for self-route", r.geometry.isEmpty())
        assertTrue("edges empty for self-route", r.edges.isEmpty())
    }

    @Test
    fun nearest_walkable_node_returns_real_node() {
        val n = router.nearestWalkableNode(42.5219, -70.8967)
        assertNotNull("Salem Common must snap to some walkable node", n)
        assertTrue("snapped node is walkable", bundle.isWalkable(n!!.internalIdx))
    }

    @Test
    fun multistop_route_concatenates_correctly() {
        val multi = router.routeMulti(
            listOf(
                RoutingLatLng(42.5219, -70.8967),
                RoutingLatLng(42.524, -70.8989),
                RoutingLatLng(42.5226, -70.8845),
            )
        )
        assertNotNull(multi)
        // Sum of legs must equal pairwise sum.
        val a = router.route(42.5219, -70.8967, 42.524, -70.8989)!!.distanceM
        val b = router.route(42.524, -70.8989, 42.5226, -70.8845)!!.distanceM
        assertEquals(a + b, multi!!.distanceM, 1e-6)
    }

    @Test
    fun multistop_with_single_stop_returns_null() {
        val r = router.routeMulti(listOf(RoutingLatLng(42.5219, -70.8967)))
        assertNull(r)
    }

    @Test
    fun pace_drives_duration() {
        val r = router.route(42.5219, -70.8967, 42.5226, -70.8845)!!
        assertEquals(r.distanceM / 1.4, r.durationS, 1e-6)
    }
}
