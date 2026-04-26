package com.example.locationmapapp.core.routing

import android.database.sqlite.SQLiteDatabase
import java.io.File

/** Loads a [RoutingBundle] from a `salem-routing-graph.sqlite` file. */
object RoutingBundleLoader {

    private const val EXPECTED_SCHEMA_VERSION = 1

    /**
     * Open the SQLite at [file] (read-only) and stream its contents into a
     * [RoutingBundle]. Throws [IllegalStateException] if the schema version
     * is unknown.
     */
    fun load(file: File): RoutingBundle {
        require(file.exists()) { "routing bundle not found: $file" }
        val db = SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
        )
        try {
            val meta = readMeta(db)
            val schemaVersion = meta["schema_version"]?.toIntOrNull() ?: 0
            check(schemaVersion == EXPECTED_SCHEMA_VERSION) {
                "Unsupported routing bundle schema version $schemaVersion " +
                    "(expected $EXPECTED_SCHEMA_VERSION)"
            }
            val nodeCount = meta["node_count"]?.toIntOrNull() ?: 0
            val edgeCount = meta["edge_count"]?.toIntOrNull() ?: 0
            check(nodeCount > 0 && edgeCount > 0) {
                "Routing bundle reports zero nodes/edges (corrupt?)"
            }

            val (nodeIds, nodeLat, nodeLng, nodeWalkable, idToIdx) =
                readNodes(db, nodeCount)

            val (edgeId, srcIdx, tgtIdx, edgeLength, edgeFull, edgeMtfcc, edgePoly) =
                readEdges(db, edgeCount, idToIdx)

            return RoutingBundle.build(
                nodeIds = nodeIds,
                nodeLat = nodeLat,
                nodeLng = nodeLng,
                nodeWalkable = nodeWalkable,
                edgeId = edgeId,
                srcNodeIdx = srcIdx,
                tgtNodeIdx = tgtIdx,
                edgeLengthM = edgeLength,
                edgeFullname = edgeFull,
                edgeMtfcc = edgeMtfcc,
                edgePolylinePacked = edgePoly,
                meta = meta,
            )
        } finally {
            db.close()
        }
    }

    private fun readMeta(db: SQLiteDatabase): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        db.rawQuery("SELECT key, value FROM meta", null).use { c ->
            while (c.moveToNext()) out[c.getString(0)] = c.getString(1)
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

    private fun readNodes(db: SQLiteDatabase, expected: Int): Nodes {
        val ids = LongArray(expected)
        val lat = DoubleArray(expected)
        val lng = DoubleArray(expected)
        val walkable = BooleanArray(expected)
        val idToIdx = HashMap<Long, Int>(expected * 2)
        var i = 0
        db.rawQuery("SELECT id, lat, lng, walkable FROM nodes ORDER BY id", null).use { c ->
            while (c.moveToNext()) {
                val nodeId = c.getLong(0)
                ids[i] = nodeId
                lat[i] = c.getDouble(1)
                lng[i] = c.getDouble(2)
                walkable[i] = c.getInt(3) != 0
                idToIdx[nodeId] = i
                i++
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

    private fun readEdges(db: SQLiteDatabase, expected: Int, idToIdx: HashMap<Long, Int>): Edges {
        val ids = LongArray(expected)
        val srcIdx = IntArray(expected)
        val tgtIdx = IntArray(expected)
        val length = DoubleArray(expected)
        val fullname = Array(expected) { "" }
        val mtfcc = arrayOfNulls<String>(expected)
        val polylines = Array(expected) { DoubleArray(0) }
        var i = 0
        db.rawQuery(
            "SELECT id, source, target, length_m, mtfcc, fullname, geom_polyline FROM edges",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val s = idToIdx[c.getLong(1)] ?: error("edge ${c.getLong(0)} references unknown source ${c.getLong(1)}")
                val t = idToIdx[c.getLong(2)] ?: error("edge ${c.getLong(0)} references unknown target ${c.getLong(2)}")
                ids[i] = c.getLong(0)
                srcIdx[i] = s
                tgtIdx[i] = t
                length[i] = c.getDouble(3)
                mtfcc[i] = if (c.isNull(4)) null else c.getString(4)
                fullname[i] = c.getString(5) ?: ""
                polylines[i] = parsePolyline(c.getString(6))
                i++
            }
        }
        check(i == expected) { "edge row count $i != meta.edge_count $expected" }
        return Edges(ids, srcIdx, tgtIdx, length, fullname, mtfcc, polylines)
    }

    /** "lat,lng;lat,lng;..." -> packed [lat0, lng0, lat1, lng1, ...]. */
    internal fun parsePolyline(src: String): DoubleArray {
        if (src.isEmpty()) return DoubleArray(0)
        // Pre-count to size the array exactly.
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
