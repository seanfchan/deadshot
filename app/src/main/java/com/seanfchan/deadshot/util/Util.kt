package com.seanfchan.deadshot.util

import android.content.pm.PackageManager

object Util {
    fun isPermissionGranted(grantResults: IntArray): Boolean {
        return grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
    }
}
