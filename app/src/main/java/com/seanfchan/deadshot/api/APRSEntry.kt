package com.seanfchan.deadshot.api

import com.google.gson.annotations.SerializedName

class APRSEntry {
    @SerializedName("name")
    var name: String? = null

    @SerializedName("type")
    var type: String? = null

    @SerializedName("time")
    var time: String? = null

    @SerializedName("lasttime")
    var lastTime: String? = null

    @SerializedName("lat")
    var lat: String? = null

    @SerializedName("lng")
    var lng: String? = null

    @SerializedName("altitude")
    var altitude: String? = null
}
