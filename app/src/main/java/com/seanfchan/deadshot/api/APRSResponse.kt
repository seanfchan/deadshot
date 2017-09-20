package com.seanfchan.deadshot.api

import com.google.gson.annotations.SerializedName

class APRSResponse {
    @SerializedName("command")
    var command: String? = null

    @SerializedName("result")
    var result: String? = null

    @SerializedName("what")
    var what: String? = null

    @SerializedName("found")
    var found: Int = 0

    @SerializedName("entries")
    var entries: List<APRSEntry>? = null

    fun isSuccess(): Boolean {
        when (result) {
            "ok" -> return true
            else -> return false
        }
    }
}
