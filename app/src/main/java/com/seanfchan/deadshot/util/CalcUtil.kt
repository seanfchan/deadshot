package com.seanfchan.deadshot.util

object CalcUtil {
    /**
     * http://www.movable-type.co.uk/scripts/latlong.html
     * a is the square of half the chord length between the points
     * c is the angular distance in radians
     * return distance in meters
     */
    fun getHaversineDistance(latitudeDegrees1: Double, longitudeDegrees1: Double,
                             latitudeDegrees2: Double, longitudeDegrees2: Double): Double {
        val latitude1 = Math.toRadians(latitudeDegrees1)
        val latitude2 = Math.toRadians(latitudeDegrees2)
        val deltaLatitude = Math.toRadians(latitudeDegrees2 - latitudeDegrees1)
        val deltaLongitude = Math.toRadians(longitudeDegrees2 - longitudeDegrees1)
        val a = Math.pow(Math.sin(deltaLatitude / 2.0), 2.0) +
                (Math.cos(latitude1) * Math.cos(latitude2) * Math.pow(Math.sin(deltaLongitude / 2.0), 2.0))
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return CalcConstants.EARTH_MEAN_RADIUS_METERS * c
    }

    /**
     * Distance by pythagoras theorem
     * Can be used when distances are small
     */
    fun getDistanceWithAltitude(distance: Double, height1: Double, height2: Double): Double {
        return Math.sqrt(Math.pow(distance, 2.0) + Math.pow(height2 - height1, 2.0))
    }
}
