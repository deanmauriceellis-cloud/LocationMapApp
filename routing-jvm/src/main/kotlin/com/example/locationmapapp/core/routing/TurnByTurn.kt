package com.example.locationmapapp.core.routing

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.roundToInt

/** Plain-text turn-by-turn step ready to render in a UI. */
data class TurnStep(
    /** Cumulative distance from start to the START of this step, metres. */
    val distanceFromStartM: Double,
    /** Length of this step (the straight along one named street), metres. */
    val lengthM: Double,
    /** The street/path name this step travels along; "" for unnamed segments. */
    val streetName: String,
    /** Human-readable instruction (e.g., "Turn left onto Essex Street"). */
    val instruction: String,
    /** Type tag — useful for picking an icon/voice cue. */
    val maneuver: Maneuver,
    /** Bearing along this step at its first segment, degrees clockwise from N. */
    val bearingDeg: Double,
    /** Polyline of just this step. */
    val polyline: List<RoutingLatLng>,
)

enum class Maneuver { DEPART, CONTINUE, SLIGHT_LEFT, LEFT, SHARP_LEFT,
    SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, U_TURN, ARRIVE }

/**
 * Group a [RouteResult]'s per-edge steps into named-street segments and emit
 * turn-by-turn instructions. Consecutive edges with the same `fullname` are
 * merged; turn type at each transition is derived from bearing change.
 *
 * Bearing thresholds (signed, degrees, +CW = right):
 *   -15..+15  = continue
 *   +15..+45  = slight right
 *   +45..+135 = right
 *   +135..+180 = sharp right (or u-turn)
 *   (mirrored for left)
 *
 * "Unnamed segment" handling: if a transition is between two unnamed runs,
 * we still emit a maneuver based on bearing — the user still needs to know
 * to turn even if the path has no name.
 */
object TurnByTurn {

    fun synthesize(route: RouteResult): List<TurnStep> {
        if (route.edges.isEmpty()) return emptyList()

        // Group consecutive edges by (fullname). Empty-name edges form their own runs.
        val runs = ArrayList<MutableList<RouteEdgeStep>>()
        var currentName: String? = null
        var current: MutableList<RouteEdgeStep>? = null
        for (e in route.edges) {
            if (current == null || e.fullname != currentName) {
                current = ArrayList()
                runs.add(current)
                currentName = e.fullname
            }
            current.add(e)
        }

        val out = ArrayList<TurnStep>(runs.size + 1)
        var cumulativeM = 0.0
        var prevBearing: Double? = null
        for ((i, run) in runs.withIndex()) {
            val runLength = run.sumOf { it.lengthM }
            val runPoly = mergeRunPolyline(run)
            if (runPoly.size < 2) continue
            val firstBearing = bearingDeg(runPoly[0], runPoly[1])

            val maneuver: Maneuver
            val instruction: String
            val streetLabel = run.first().fullname.ifEmpty { "unnamed path" }
            if (i == 0) {
                maneuver = Maneuver.DEPART
                instruction = if (run.first().fullname.isEmpty())
                    "Head ${compass(firstBearing)}"
                else
                    "Head ${compass(firstBearing)} on $streetLabel"
            } else {
                val turn = classifyTurn(prevBearing!!, firstBearing)
                maneuver = turn
                instruction = phraseTurn(turn, run.first().fullname)
            }
            out.add(
                TurnStep(
                    distanceFromStartM = cumulativeM,
                    lengthM = runLength,
                    streetName = run.first().fullname,
                    instruction = withDistance(instruction, runLength),
                    maneuver = maneuver,
                    bearingDeg = firstBearing,
                    polyline = runPoly,
                )
            )
            cumulativeM += runLength
            prevBearing = bearingDeg(runPoly[runPoly.size - 2], runPoly.last())
        }
        // Arrive step always last.
        out.add(
            TurnStep(
                distanceFromStartM = cumulativeM,
                lengthM = 0.0,
                streetName = out.lastOrNull()?.streetName ?: "",
                instruction = "Arrive at destination",
                maneuver = Maneuver.ARRIVE,
                bearingDeg = prevBearing ?: 0.0,
                polyline = emptyList(),
            )
        )
        return out
    }

    private fun mergeRunPolyline(run: List<RouteEdgeStep>): List<RoutingLatLng> {
        val out = ArrayList<RoutingLatLng>()
        for ((i, e) in run.withIndex()) {
            for ((j, p) in e.polyline.withIndex()) {
                if (i > 0 && j == 0) continue
                out.add(p)
            }
        }
        return out
    }

    private fun classifyTurn(prevBearing: Double, newBearing: Double): Maneuver {
        var d = newBearing - prevBearing
        // Normalize to (-180, 180]
        while (d > 180.0) d -= 360.0
        while (d <= -180.0) d += 360.0
        return when {
            d in -15.0..15.0 -> Maneuver.CONTINUE
            d > 15.0 && d <= 45.0 -> Maneuver.SLIGHT_RIGHT
            d > 45.0 && d <= 135.0 -> Maneuver.RIGHT
            d > 135.0 -> if (d >= 170.0) Maneuver.U_TURN else Maneuver.SHARP_RIGHT
            d < -15.0 && d >= -45.0 -> Maneuver.SLIGHT_LEFT
            d < -45.0 && d >= -135.0 -> Maneuver.LEFT
            else -> if (d <= -170.0) Maneuver.U_TURN else Maneuver.SHARP_LEFT
        }
    }

    private fun phraseTurn(m: Maneuver, name: String): String {
        val onto = if (name.isEmpty()) "" else " onto $name"
        return when (m) {
            Maneuver.CONTINUE -> if (name.isEmpty()) "Continue straight" else "Continue on $name"
            Maneuver.SLIGHT_LEFT -> "Slight left$onto"
            Maneuver.LEFT -> "Turn left$onto"
            Maneuver.SHARP_LEFT -> "Sharp left$onto"
            Maneuver.SLIGHT_RIGHT -> "Slight right$onto"
            Maneuver.RIGHT -> "Turn right$onto"
            Maneuver.SHARP_RIGHT -> "Sharp right$onto"
            Maneuver.U_TURN -> "Make a U-turn$onto"
            Maneuver.DEPART -> "Depart"
            Maneuver.ARRIVE -> "Arrive at destination"
        }
    }

    private fun withDistance(instruction: String, lengthM: Double): String {
        if (lengthM <= 0.0) return instruction
        val label = formatDistance(lengthM)
        return "$instruction · $label"
    }

    private fun formatDistance(m: Double): String {
        // PG-13 sensibility / US tour audience — present in feet/miles for short
        // distances, miles with one decimal for longer. Sub-100ft rounds to 50/100/150ft.
        val ft = m * 3.28084
        return when {
            ft < 75 -> "${(ft / 25).roundToInt() * 25} ft"
            ft < 528 -> "${(ft / 50).roundToInt() * 50} ft" // up to ~0.1 mi
            ft < 5280 -> "${(ft / 100).roundToInt() * 100} ft"
            else -> "${"%.1f".format(m / 1609.344)} mi"
        }
    }

    /** Initial bearing from a→b in degrees clockwise from N. */
    private fun bearingDeg(a: RoutingLatLng, b: RoutingLatLng): Double {
        val lat1 = a.lat * PI / 180
        val lat2 = b.lat * PI / 180
        val dlon = (b.lng - a.lng) * PI / 180
        val y = sin(dlon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dlon)
        var deg = atan2(y, x) * 180.0 / PI
        if (deg < 0) deg += 360.0
        return deg
    }

    private fun compass(deg: Double): String {
        val dirs = arrayOf("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest")
        val idx = ((deg + 22.5) / 45.0).toInt() % 8
        return dirs[idx]
    }

    /** Find the step active at [progressM] cumulative metres from start. */
    fun activeStepIndex(steps: List<TurnStep>, progressM: Double): Int {
        for (i in steps.indices) {
            val s = steps[i]
            if (progressM < s.distanceFromStartM + s.lengthM) return i
        }
        return steps.size - 1
    }

    /** Convenience for "X ft · Continue on Essex Street" inline rendering. */
    fun renderShortDistance(m: Double): String = formatDistance(m)
}
