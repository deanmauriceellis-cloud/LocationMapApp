/*
 * WickedSalemWitchCityTour v1.0
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 */

package com.example.wickedsalemwitchcitytour.wickedmap

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

object MercatorMath {

    const val TILE_SIZE = 256

    fun lonToTileX(lon: Double, zoom: Int): Double {
        val n = 1L shl zoom
        return (lon + 180.0) / 360.0 * n
    }

    fun latToTileY(lat: Double, zoom: Int): Double {
        val n = 1L shl zoom
        val latRad = Math.toRadians(lat)
        return (1.0 - ln(tan(latRad) + 1.0 / Math.cos(latRad)) / PI) / 2.0 * n
    }

    fun tileXToLon(x: Double, zoom: Int): Double {
        val n = 1L shl zoom
        return x / n * 360.0 - 180.0
    }

    fun tileYToLat(y: Double, zoom: Int): Double {
        val n = 1L shl zoom
        val latRad = atan(sinh(PI * (1.0 - 2.0 * y / n)))
        return Math.toDegrees(latRad)
    }

    /**
     * osmdroid SqliteArchive key encoding.
     * key = (((z << z) + x) << z) + y
     */
    fun osmdroidKey(z: Int, x: Int, y: Int): Long {
        val zL = z.toLong()
        val xL = x.toLong()
        val yL = y.toLong()
        return (((zL shl z) + xL) shl z) + yL
    }
}
