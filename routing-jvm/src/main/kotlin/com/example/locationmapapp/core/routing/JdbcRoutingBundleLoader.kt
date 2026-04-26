package com.example.locationmapapp.core.routing

import java.io.File
import java.sql.DriverManager

/**
 * JVM-only loader that mirrors the Android `RoutingBundleLoader` (in `:core`)
 * but uses sqlite-jdbc instead of `SQLiteDatabase`. Used by the
 * `:salem-content` bake pipeline and by `:routing-jvm`'s parity tests.
 *
 * If the Android-side `RoutingBundleLoader` changes its schema expectations
 * or column reads, this loader must be updated to match — otherwise the
 * parity checks become meaningless and the bake will diverge from runtime.
 */
object JdbcRoutingBundleLoader {

    private const val EXPECTED_SCHEMA_VERSION = 1

    fun load(file: File): RoutingBundle {
        require(file.exists()) { "routing bundle not found: ${file.absolutePath}" }

        // Opening :memory: first forces the JDBC driver to register, then we
        // open the real DB read-only via a URL with `mode=ro`.
        Class.forName("org.sqlite.JDBC")
        val url = "jdbc:sqlite:${file.absolutePath}"
        DriverManager.getConnection(url).use { conn ->
            conn.createStatement().use { it.execute("PRAGMA query_only = ON") }

            val meta = readMeta(conn)
            val schemaVersion = meta["schema_version"]?.toIntOrNull() ?: 0
            check(schemaVersion == EXPECTED_SCHEMA_VERSION) {
                "Unsupported routing bundle schema version $schemaVersion (expected $EXPECTED_SCHEMA_VERSION)"
            }
            val nodeCount = meta["node_count"]?.toIntOrNull() ?: 0
            val edgeCount = meta["edge_count"]?.toIntOrNull() ?: 0
            check(nodeCount > 0 && edgeCount > 0) {
                "Routing bundle reports zero nodes/edges (corrupt?)"
            }

            val (nodeIds, nodeLat, nodeLng, nodeWalkable, idToIdx) = readNodes(conn, nodeCount)
            val edges = readEdges(conn, edgeCount, idToIdx)

            return RoutingBundle.build(
                nodeIds = nodeIds,
                nodeLat = nodeLat,
                nodeLng = nodeLng,
                nodeWalkable = nodeWalkable,
                edgeId = edges.ids,
                srcNodeIdx = edges.srcIdx,
                tgtNodeIdx = edges.tgtIdx,
                edgeLengthM = edges.length,
                edgeFullname = edges.fullname,
                edgeMtfcc = edges.mtfcc,
                edgePolylinePacked = edges.polylines,
                meta = meta,
            )
        }
    }

    private fun readMeta(conn: java.sql.Connection): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT key, value FROM meta").use { rs ->
                while (rs.next()) out[rs.getString(1)] = rs.getString(2)
            }
        }
        return out
    }

    private data class Nodes(
        val ids: LongArray,
        val lat: DoubleArray,
        val lng: DoubleArray,
        val walkable: BooleanArray,
        val idToIdx: HashMap<Long, Int>,
    )

    private fun readNodes(conn: java.sql.Connection, expected: Int): Nodes {
        val ids = LongArray(expected)
        val lat = DoubleArray(expected)
        val lng = DoubleArray(expected)
        val walkable = BooleanArray(expected)
        val idToIdx = HashMap<Long, Int>(expected * 2)
        var i = 0
        conn.createStatement().use { st ->
            st.executeQuery("SELECT id, lat, lng, walkable FROM nodes ORDER BY id").use { rs ->
                while (rs.next()) {
                    val nodeId = rs.getLong(1)
                    ids[i] = nodeId
                    lat[i] = rs.getDouble(2)
                    lng[i] = rs.getDouble(3)
                    walkable[i] = rs.getInt(4) != 0
                    idToIdx[nodeId] = i
                    i++
                }
            }
        }
        check(i == expected) { "node row count $i != meta.node_count $expected" }
        return Nodes(ids, lat, lng, walkable, idToIdx)
    }

    private data class Edges(
        val ids: LongArray,
        val srcIdx: IntArray,
        val tgtIdx: IntArray,
        val length: DoubleArray,
        val fullname: Array<String>,
        val mtfcc: Array<String?>,
        val polylines: Array<DoubleArray>,
    )

    private fun readEdges(conn: java.sql.Connection, expected: Int, idToIdx: HashMap<Long, Int>): Edges {
        val ids = LongArray(expected)
        val srcIdx = IntArray(expected)
        val tgtIdx = IntArray(expected)
        val length = DoubleArray(expected)
        val fullname = Array(expected) { "" }
        val mtfcc = arrayOfNulls<String>(expected)
        val polylines = Array(expected) { DoubleArray(0) }
        var i = 0
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT id, source, target, length_m, mtfcc, fullname, geom_polyline FROM edges"
            ).use { rs ->
                while (rs.next()) {
                    val sId = rs.getLong(2)
                    val tId = rs.getLong(3)
                    val s = idToIdx[sId] ?: error("edge ${rs.getLong(1)} references unknown source $sId")
                    val t = idToIdx[tId] ?: error("edge ${rs.getLong(1)} references unknown target $tId")
                    ids[i] = rs.getLong(1)
                    srcIdx[i] = s
                    tgtIdx[i] = t
                    length[i] = rs.getDouble(4)
                    val mtf = rs.getString(5); mtfcc[i] = if (rs.wasNull()) null else mtf
                    fullname[i] = rs.getString(6) ?: ""
                    polylines[i] = parsePolyline(rs.getString(7) ?: "")
                    i++
                }
            }
        }
        check(i == expected) { "edge row count $i != meta.edge_count $expected" }
        return Edges(ids, srcIdx, tgtIdx, length, fullname, mtfcc, polylines)
    }

    /** "lat,lng;lat,lng;..." → packed [lat0, lng0, lat1, lng1, ...]. Mirrors RoutingBundleLoader.parsePolyline. */
    private fun parsePolyline(src: String): DoubleArray {
        if (src.isEmpty()) return DoubleArray(0)
        var pairs = 1
        for (ch in src) if (ch == ';') pairs++
        val out = DoubleArray(pairs * 2)
        var write = 0
        var start = 0
        var i = 0
        while (i <= src.length) {
            if (i == src.length || src[i] == ';') {
                val pair = src.substring(start, i)
                val comma = pair.indexOf(',')
                out[write++] = pair.substring(0, comma).toDouble()
                out[write++] = pair.substring(comma + 1).toDouble()
                start = i + 1
            }
            i++
        }
        return out
    }
}
