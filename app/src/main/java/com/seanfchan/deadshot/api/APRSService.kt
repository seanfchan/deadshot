package com.seanfchan.deadshot.api

import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.Query

interface APRSService {
    @GET("/")
    fun getAPRData(@Query("latestOnly") what: String = "true") : Observable<List<APRSEntry>>

    @GET("/")
    fun getAllAPRData() : Observable<List<APRSEntry>>
}
