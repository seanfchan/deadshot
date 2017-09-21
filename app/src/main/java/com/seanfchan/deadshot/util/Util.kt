package com.seanfchan.deadshot.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Point
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
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import java.util.concurrent.TimeUnit

object Util {
    fun isPermissionGranted(grantResults: IntArray): Boolean {
        return grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
    }

    fun getGPSSensorStream(activity: Activity, requestPermissionCode: Int): Observable<Location> {
        val appContext = activity.applicationContext
        val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return Observable.create({ emitter: ObservableEmitter<Location> ->
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

            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, TimeUnit.SECONDS.toMillis(5), 0f, listener)
            } else {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), requestPermissionCode)
                emitter.onComplete()
            }
        })
    }

    fun getRotationVectorObservable(activity: Activity): Observable<SensorEvent> {
        val appContext = activity.applicationContext
        val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensor == null) {
            Toast.makeText(activity, "You do not have the sensors to create a compass", Toast.LENGTH_SHORT).show()
            return Observable.error(Error("Do not have the correct sensors for a rotation vector fusion sensor"))
        }

        return Observable.create<SensorEvent> {
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
    }

    fun getSensorObservable(activity: Activity, sensorType: Int): Observable<SensorEvent> {
        val appContext = activity.applicationContext
        val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sensorManager.getDefaultSensor(sensorType)
        if (sensor == null) {
            Toast.makeText(activity, "You do not have the sensors to create a compass", Toast.LENGTH_SHORT).show()
            return Observable.error(Error("Do not have the correct sensors for a rotation vector fusion sensor"))
        }

        return Observable.create<SensorEvent> {
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

            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun getScreenSize(c: Context): Point {
        val display = (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        val size = Point()
        display.getSize(size)
        return size
    }

    fun getDisplayMetrics(c: Context): DisplayMetrics {
        return c.resources.displayMetrics
    }

    fun getScreenDensityDpi(c: Context): Int {
        return getDisplayMetrics(c).densityDpi
    }
}
