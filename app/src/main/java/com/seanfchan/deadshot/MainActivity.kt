package com.seanfchan.deadshot

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener2
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import com.seanfchan.deadshot.api.APRSService
import com.seanfchan.deadshot.util.Util
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

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
        setUpCompassObservable()
        setUpLocationObservable()
    }

    override fun onStop() {
        compositeDisposable.clear()
        super.onStop()
    }

    fun setUpLocationObservable() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationObservable = Observable.create({ emitter: ObservableEmitter<Location> ->
            val listener = object : LocationListener {
                override fun onLocationChanged(p0: Location?) {
                    if (p0 != null) {
                        emitter.onNext(p0)
                    }
                }

                override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {
                }

                override fun onProviderEnabled(p0: String?) {
                }

                override fun onProviderDisabled(p0: String?) {
                }
            }

            emitter.setCancellable {
                locationManager.removeUpdates(listener)
                emitter.onComplete()
            }

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, TimeUnit.SECONDS.toMillis(5), 0f, listener)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), ACCESS_FINE_LOCATION_CODE )
                emitter.onComplete()
            }
        })

        compositeDisposable.add(locationObservable.subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.computation())
                .doOnNext({
                    it.altitude
                    it.latitude
                    it.longitude
                })
                .subscribe())
    }

    fun setUpCompassObservable() {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        if (sensor == null) {
            Toast.makeText(this, "You do not have the sensors to create a compass", Toast.LENGTH_SHORT).show()
            return
        }

        val compassEventObservable = Observable.create<SensorEvent> {
            emitter: ObservableEmitter<SensorEvent> ->
            val listener = object : SensorEventListener2 {
                override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
                }

                override fun onFlushCompleted(p0: Sensor?) {
                }

                override fun onSensorChanged(event: SensorEvent) {
                    emitter.onNext(event)
                }
            }

            emitter.setCancellable({
                sensorManager.unregisterListener(listener, sensor)
                emitter.onComplete()
            })

            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        compositeDisposable.add(compassEventObservable
                .doOnNext {
                    it.values
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
