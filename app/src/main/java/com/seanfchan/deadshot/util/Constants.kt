package com.seanfchan.deadshot.util

object Constants {
    const val APRS_POLLING_INTERVAL_SECONDS = 30L
    const val CALL_SIGN: String = "KK6GIP-11"

    // All in meters
    const val WIDTH_OF_PHONE = 0.0726
    const val HALF_WIDTH_OF_PHONE = 0.0381
    const val VIEWPORT_TO_PHONE = 0.15
    val METERS_ON_SCREEN_PER_DEGREE = Math.tan(Math.toRadians(1.0)) * VIEWPORT_TO_PHONE
    val INCHES_ON_SCREEN_PER_DEGREE = CalcUtil.convertMetersToInch(METERS_ON_SCREEN_PER_DEGREE)
}
