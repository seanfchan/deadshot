package com.seanfchan.deadshot.api

import io.reactivex.Observable
import retrofit2.http.GET
import retrofit2.http.Query

interface APRSService {
    @GET("get")
    fun getAPRData(@Query("name") name: String, @Query("what") what: String = "loc",
                   @Query("apikey") apiKey: String = "105203.iYQjtqjtpgUFIr", @Query("format") format: String = "json") :
            Observable<APRSResponse>
}
