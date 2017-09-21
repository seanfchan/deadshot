package com.seanfchan.deadshot

import android.app.Application
import com.facebook.stetho.Stetho
import com.facebook.stetho.okhttp3.StethoInterceptor
import com.seanfchan.deadshot.api.APRSService
import com.seanfchan.deadshot.cache.LocationEntryCache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

class DeadShot : Application() {

    lateinit var aprsService: APRSService
    lateinit var locationEntryCache: LocationEntryCache

    override fun onCreate() {
        super.onCreate()

        Stetho.initializeWithDefaults(this)
        setupService()
    }

    fun setupService() {
        val headerInjector = object : Interceptor {
            override fun intercept(chain: Interceptor.Chain?): Response {
                var request = chain!!.request()

                val headerBuilder = request.headers().newBuilder()
                        .add("User-Agent", "periscope-camera-space-program/v0.1 (https://www.pscp.tv)")

                request = request.newBuilder()
                        .headers(headerBuilder.build())
                        .build()
                return chain.proceed(request)
            }
        }

        val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(headerInjector)
                .addNetworkInterceptor(StethoInterceptor())
                .build()

        val retrofit = Retrofit.Builder()
                .baseUrl("https://periscope-aprs.herokuapp.com/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build()

        aprsService = retrofit.create(APRSService::class.java)
        locationEntryCache = LocationEntryCache(aprsService)
    }
}
