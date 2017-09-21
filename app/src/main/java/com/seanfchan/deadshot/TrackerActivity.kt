package com.seanfchan.deadshot

import android.hardware.SensorManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.seanfchan.deadshot.api.APRSEntry
import com.seanfchan.deadshot.api.APRSResponse
import com.seanfchan.deadshot.api.APRSService
import com.seanfchan.deadshot.util.Constants
import com.seanfchan.deadshot.util.Util
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class TrackerActivity : AppCompatActivity() {

    val ACCESS_FINE_LOCATION_CODE = 100

    lateinit var aprsService: APRSService
    lateinit var subscriptions: CompositeDisposable
    lateinit var currentUserLocation: LocationHolder
    var azimuthDegrees: Double = 0.0
    var pitchDegree: Double = 0.0

    lateinit var accuracyContainer: View
    var grayColor: Int = 0
    var redColor: Int = 0
    var yellowColor: Int = 0
    var greenColor: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracker)

        val DeadShot = application as DeadShot
        aprsService = DeadShot.aprsService
        subscriptions = CompositeDisposable()
        currentUserLocation = LocationHolder()

        val res = resources
        accuracyContainer = findViewById(R.id.accuracy_container)

        grayColor = res.getColor(R.color.gray)
        redColor = res.getColor(R.color.red)
        yellowColor = res.getColor(R.color.yellow)
        greenColor = res.getColor(R.color.green)

        accuracyContainer.setBackgroundColor(grayColor)
    }

    override fun onResume() {
        super.onResume()
        subscriptions.add(aprsPollingObservable().subscribe())
        setupGPSSensor()
        setUpRotationSensor()
    }

    override fun onStop() {
        super.onStop()
        subscriptions.clear()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            ACCESS_FINE_LOCATION_CODE -> {
                if (Util.isPermissionGranted(grantResults)) {
                    setupGPSSensor()
                } else {
                    // Don't proceed
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    fun aprsPollingObservable(): Observable<List<APRSEntry>> {
        return Observable.interval(Constants.APRS_POLLING_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .observeOn(Schedulers.io())
                .flatMap {
                    aprsService.getAPRData(Constants.CALL_SIGN)
                }
                .doOnNext {
                    handleAPRSResponse(it)
                }
    }

    fun handleAPRSResponse(entries: List<APRSEntry>) {
        if (entries.isEmpty()) {
            return
        }
    }

    fun setupGPSSensor() {
        subscriptions.add(Util.getGPSSensorStream(this, ACCESS_FINE_LOCATION_CODE)
                .doOnNext {
                    currentUserLocation.lat = it.latitude
                    currentUserLocation.long = it.longitude
                    currentUserLocation.altitude = it.altitude
                }
                .subscribe()
        )
    }

    fun setUpRotationSensor() {
        subscriptions.add(Util.getRotationVectorObservable(this)
                .observeOn(Schedulers.computation())
                .doOnNext {
                    val vectors = it.values
                    val rotMat = FloatArray(9)
                    val azimuthAboutYAxis = FloatArray(9)
                    val pitchAboutZAxis = FloatArray(9)
                    val orientation = FloatArray(3)

                    SensorManager.getRotationMatrixFromVector(rotMat, vectors)
                    SensorManager.remapCoordinateSystem(rotMat, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, azimuthAboutYAxis)
                    currentUserLocation.azimuthDegrees = ((Math.toDegrees(SensorManager.getOrientation(azimuthAboutYAxis, orientation)[0].toDouble())))

                    SensorManager.remapCoordinateSystem(rotMat, SensorManager.AXIS_Z, SensorManager.AXIS_X, pitchAboutZAxis)
                    currentUserLocation.pitchDegree = ((Math.toDegrees(SensorManager.getOrientation(pitchAboutZAxis, orientation)[1].toDouble())))
                }
                .subscribe()
        )
    }

    class LocationHolder {
        var lat: Double = 0.0
        var long: Double = 0.0
        var altitude: Double = 0.0
        var azimuthDegrees: Double = 0.0
        var pitchDegree: Double = 0.0
    }
}
