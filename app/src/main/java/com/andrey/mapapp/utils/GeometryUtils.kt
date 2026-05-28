package com.andrey.mapapp.utils

import org.osmdroid.util.GeoPoint
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object GeometryUtils {
    // fun for calculating points based on distance from source
    fun calculateTargetPoint(center: GeoPoint, distance: Double, bearing: Double): GeoPoint {
        val earthRadius = 6371000.0 // earth radius in meters

        val lat1 = Math.toRadians(center.latitude)
        val lon1 = Math.toRadians(center.longitude)
        val bearingRad = Math.toRadians(bearing)
        val distRatio = distance / earthRadius

        val lat2Rad = asin(
            sin(lat1) * cos(distRatio) +
                    cos(lat1) * sin(distRatio) * cos(bearingRad)
        )
        val lon2Rad = lon1 + atan2(
            sin(bearingRad) * sin(distRatio) * cos(lat1),
            cos(distRatio) - sin(lat1) * sin(lat2Rad)
        )

        return GeoPoint(Math.toDegrees(lat2Rad), Math.toDegrees(lon2Rad))
    }
}