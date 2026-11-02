package com.v2ray.ang.ui.main

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Very coarse continent outlines used only to colour the decorative globe. They are approximations
 * (a few dozen vertices per landmass), not survey data, and say nothing about server locations.
 */
internal object WorldLandMask {
    /** Each array is x0, y0, x1, y1, ... as longitude, latitude in degrees. */
    private val polygons: List<FloatArray> = listOf(
        // north_america
        floatArrayOf(-168f, 66f, -160f, 70f, -140f, 70f, -125f, 70f, -95f, 72f, -80f, 68f, -62f, 58f, -55f, 50f, -66f, 44f, -70f, 41f, -76f, 35f, -81f, 30f, -80f, 25f, -84f, 29f, -90f, 29f, -97f, 26f, -97f, 21f, -105f, 20f, -110f, 24f, -114f, 31f, -117f, 32f, -122f, 37f, -124f, 43f, -125f, 49f, -135f, 58f, -146f, 61f, -152f, 59f, -165f, 55f, -163f, 60f),
        // central_america
        floatArrayOf(-97f, 21f, -92f, 18f, -88f, 21f, -84f, 15f, -83f, 10f, -78f, 8f, -80f, 8f, -86f, 11f, -92f, 15f, -95f, 17f),
        // greenland
        floatArrayOf(-73f, 78f, -60f, 82f, -30f, 83f, -20f, 78f, -22f, 70f, -43f, 60f, -50f, 64f, -56f, 70f),
        // south_america
        floatArrayOf(-78f, 8f, -72f, 12f, -62f, 10f, -52f, 5f, -50f, 0f, -35f, -5f, -39f, -15f, -41f, -22f, -48f, -26f, -53f, -34f, -58f, -38f, -65f, -41f, -66f, -47f, -69f, -52f, -72f, -54f, -75f, -48f, -73f, -38f, -71f, -30f, -70f, -18f, -76f, -14f, -81f, -5f, -80f, 0f),
        // europe
        floatArrayOf(-10f, 36f, -9f, 43f, -2f, 44f, -5f, 48f, 2f, 51f, 8f, 54f, 10f, 57f, 5f, 60f, 10f, 64f, 18f, 70f, 28f, 71f, 40f, 67f, 33f, 60f, 30f, 55f, 40f, 50f, 36f, 45f, 28f, 41f, 24f, 38f, 20f, 40f, 14f, 44f, 12f, 38f, 16f, 38f, 8f, 44f, 3f, 43f, -5f, 36f),
        // africa
        floatArrayOf(-17f, 21f, -10f, 30f, -5f, 36f, 10f, 37f, 20f, 32f, 32f, 31f, 35f, 28f, 43f, 12f, 51f, 12f, 46f, 3f, 40f, -5f, 40f, -15f, 35f, -25f, 27f, -34f, 18f, -34f, 12f, -18f, 13f, -8f, 9f, 0f, 8f, 4f, -8f, 4f, -14f, 10f, -17f, 15f),
        // asia
        floatArrayOf(36f, 45f, 40f, 50f, 33f, 60f, 40f, 67f, 60f, 69f, 80f, 73f, 100f, 77f, 115f, 73f, 140f, 72f, 170f, 69f, 180f, 65f, 163f, 59f, 156f, 51f, 142f, 52f, 140f, 46f, 131f, 42f, 129f, 35f, 122f, 40f, 121f, 30f, 117f, 23f, 108f, 21f, 106f, 10f, 100f, 14f, 103f, 2f, 98f, 8f, 97f, 17f, 92f, 22f, 88f, 22f, 80f, 15f, 77f, 8f, 73f, 17f, 70f, 22f, 62f, 25f, 57f, 26f, 56f, 24f, 59f, 22f, 52f, 16f, 43f, 13f, 35f, 28f, 36f, 32f, 36f, 37f, 28f, 41f),
        // australia
        floatArrayOf(114f, -22f, 122f, -18f, 130f, -12f, 136f, -12f, 142f, -11f, 146f, -19f, 153f, -26f, 150f, -37f, 141f, -38f, 135f, -34f, 129f, -32f, 115f, -34f),
        // japan
        floatArrayOf(130f, 31f, 135f, 34f, 140f, 36f, 142f, 40f, 141f, 45f, 145f, 43f, 142f, 38f, 140f, 35f, 135f, 33f, 131f, 33f),
        // uk
        floatArrayOf(-5f, 50f, 1f, 51f, 2f, 53f, -2f, 57f, -6f, 58f, -5f, 54f),
        // iceland
        floatArrayOf(-24f, 65f, -14f, 66f, -14f, 64f, -22f, 63f),
        // borneo
        floatArrayOf(109f, 1f, 117f, 7f, 119f, 1f, 116f, -4f, 110f, -3f),
        // sumatra
        floatArrayOf(95f, 5f, 105f, -6f, 103f, -5f, 98f, 1f),
        // new_guinea
        floatArrayOf(131f, -1f, 141f, -3f, 150f, -10f, 141f, -9f, 134f, -4f),
        // madagascar
        floatArrayOf(44f, -25f, 50f, -15f, 49f, -12f, 44f, -17f),
        // new_zealand
        floatArrayOf(172f, -35f, 178f, -38f, 174f, -41f, 166f, -46f, 172f, -41f),
    )

    fun isLand(lonDeg: Float, latDeg: Float): Boolean = polygons.any { contains(it, lonDeg, latDeg) }

    /** Even-odd ray casting. */
    private fun contains(poly: FloatArray, lon: Float, lat: Float): Boolean {
        var inside = false
        val n = poly.size / 2
        var j = n - 1
        for (i in 0 until n) {
            val xi = poly[2 * i]
            val yi = poly[2 * i + 1]
            val xj = poly[2 * j]
            val yj = poly[2 * j + 1]
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }
}

/** Points of a Fibonacci sphere with a land flag and longitude each (computed once). */
internal class GlobePoints(val x: FloatArray, val y: FloatArray, val z: FloatArray, val land: BooleanArray, val lonDeg: FloatArray) {
    val size: Int get() = x.size

    companion object {
        /**
         * Longitude 0 faces the viewer (+z) and east points to +x, so with north up the map is not mirrored.
         */
        fun build(count: Int): GlobePoints {
            val xs = FloatArray(count)
            val ys = FloatArray(count)
            val zs = FloatArray(count)
            val land = BooleanArray(count)
            val lons = FloatArray(count)
            val golden = (1.0 + sqrt(5.0)) / 2.0
            for (i in 0 until count) {
                val theta = 2.0 * PI * i / golden
                val phi = acos(1.0 - 2.0 * (i + 0.5) / count)
                val x = (cos(theta) * sin(phi)).toFloat()
                val y = cos(phi).toFloat()
                val z = (sin(theta) * sin(phi)).toFloat()
                xs[i] = x
                ys[i] = y
                zs[i] = z
                val lat = Math.toDegrees(asin(y.toDouble())).toFloat()
                val lon = Math.toDegrees(atan2(x.toDouble(), z.toDouble())).toFloat()
                lons[i] = lon
                land[i] = WorldLandMask.isLand(lon, lat)
            }
            return GlobePoints(xs, ys, zs, land, lons)
        }
    }
}
