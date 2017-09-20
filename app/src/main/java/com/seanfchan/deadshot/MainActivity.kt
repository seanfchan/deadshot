package com.seanfchan.deadshot

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener2
import android.hardware.SensorManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.seanfchan.deadshot.api.APRSService
import com.seanfchan.deadshot.util.Util
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

class MainActivity : AppCompatActivity() {

    val ACCESS_FINE_LOCATION_CODE = 100
    lateinit var application: DeadShot
    lateinit var aprsService: APRSService
    lateinit var compositeDisposable: CompositeDisposable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        compositeDisposable = CompositeDisposable()

        application = getApplication() as DeadShot
        aprsService = application.aprsService
    }

    override fun onResume() {
        super.onResume()
        setUpRotationObservable()
        setUpLocationObservable()
    }

    override fun onStop() {
        compositeDisposable.clear()
        super.onStop()
    }

    fun setUpLocationObservable() {
        compositeDisposable.add(Util.getGPSSensorStream(this, ACCESS_FINE_LOCATION_CODE)
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.computation())
                .doOnNext({
                    it.altitude
                    it.latitude
                    it.longitude
                })
                .subscribe())
    }

    fun setUpRotationObservable() {
        compositeDisposable.add(Util.getRotationVectorObservable(this)
                .doOnNext {
                    val rotMat = FloatArray(9)
                    val azimuthAboutYAxis = FloatArray(9)
                    val pitchAboutZAxis = FloatArray(9)
                    val vectors = it.values
                    val orientation = FloatArray(3)

                    SensorManager.getRotationMatrixFromVector(rotMat, vectors)
                    SensorManager.remapCoordinateSystem(rotMat, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, azimuthAboutYAxis)
                    SensorManager.remapCoordinateSystem(rotMat, SensorManager.AXIS_Z, SensorManager.AXIS_X, pitchAboutZAxis)
                    val azimuth: Int = ((Math.toDegrees(SensorManager.getOrientation(pitchAboutZAxis, orientation)[1].toDouble()))).toInt()
                    Log.e("SEAN", String.format("azimuth: %d", azimuth))
                }
                .subscribe()
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            ACCESS_FINE_LOCATION_CODE -> {
                if (Util.isPermissionGranted(grantResults)) {
                    setUpLocationObservable()
                } else {
                    // Don't proceed
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}
