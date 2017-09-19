package com.seanfchan.deadshot

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.seanfchan.deadshot.api.APRSService

class MainActivity : AppCompatActivity() {

    lateinit var application: DeadShot
    lateinit var aprsService: APRSService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        application = getApplication() as DeadShot
        aprsService = application.aprsService
    }
}
