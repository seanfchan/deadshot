package com.seanfchan.deadshot

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.crashlytics.android.Crashlytics
import io.fabric.sdk.android.Fabric

abstract class BaseActivity: AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fabric.with(this, Crashlytics())
    }
}
