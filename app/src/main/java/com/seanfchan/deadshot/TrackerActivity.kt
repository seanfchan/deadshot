package com.seanfchan.deadshot

import android.app.AlertDialog
import android.hardware.GeomagneticField
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import com.seanfchan.deadshot.api.APRSEntry
import com.seanfchan.deadshot.api.APRSResponse
import com.seanfchan.deadshot.api.APRSService
import com.seanfchan.deadshot.util.CalcUtil
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
    lateinit var lastAPRSEntry: APRSEntry

    lateinit var callSignButton: Button
    lateinit var targetBox: View

    lateinit var accuracyContainer: View
    var hasReceivedLocation: Boolean = true
    var hasReceivedRotation: Boolean = true
    var hasReceivedAPRSEntry: Boolean = true
    var grayColor: Int = 0
    var redColor: Int = 0
    var yellowColor: Int = 0
    var greenColor: Int = 0

    var callSignToQuery: String? = Constants.CALL_SIGN

    var targetBoxWidth: Int = 0
    var targetBoxHeight: Int = 0

    enum class Half {
        LEFT,
        RIGHT
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracker)

        val DeadShot = application as DeadShot
        aprsService = DeadShot.aprsService
        subscriptions = CompositeDisposable()
        currentUserLocation = LocationHolder()

        val res = resources
        accuracyContainer = findViewById(R.id.accuracy_container)
        targetBox = findViewById(R.id.target_box)
        targetBoxWidth = targetBox.layoutParams.width
        targetBoxHeight = targetBox.layoutParams.height
        lastAPRSEntry = APRSEntry()

        callSignButton = findViewById(R.id.call_sign)
        callSignButton.setOnClickListener {
            showCallSignDialog()
        }

        grayColor = res.getColor(R.color.gray)
        redColor = res.getColor(R.color.red)
        yellowColor = res.getColor(R.color.yellow)
        greenColor = res.getColor(R.color.green)

        accuracyContainer.setBackgroundColor(grayColor)
    }

    override fun onResume() {
        super.onResume()
        setup()
    }

    override fun onStop() {
        super.onStop()
        cleanUp()
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

    fun showCallSignDialog() {
        val dialog = AlertDialog.Builder(this)
        val callSignLayout = LayoutInflater.from(this).inflate(R.layout.dialog_call_sign, null)
        val callSignEditText = callSignLayout.findViewById<EditText>(R.id.call_sign)

        dialog.setView(callSignLayout)
                .setPositiveButton("Set", { _, _ ->
                    callSignToQuery = callSignEditText.text.toString()
                    cleanUp()
                    setup()
                    setCallSignButton(callSignToQuery)
                })
                .setNegativeButton("Cancel", { _, _ ->  })

        dialog.show()
    }

    fun aprsPollingObservable(callSign: String?): Observable<APRSResponse> {
//        return Observable.interval(0, Constants.APRS_POLLING_INTERVAL_SECONDS, TimeUnit.SECONDS)
//                .observeOn(Schedulers.io())
//                .flatMap {
//                    aprsService.getAPRData(callSign ?: Constants.CALL_SIGN)
//                }
//                .doOnNext {
//                    handleAPRSResponse(it)
//                }

        return Observable.interval(0, 1, TimeUnit.SECONDS)
                .flatMap {
                    currentUserLocation.lat = 37.776797
                    currentUserLocation.long = -122.416603
                    currentUserLocation.altitude = 10.0
                    hasReceivedLocation = true

                    val aprsEntry: APRSEntry = APRSEntry()
                    aprsEntry.name = Constants.CALL_SIGN
                    aprsEntry.lng = "-122.4163504"
                    aprsEntry.lat = "37.7801809"

                    val response: APRSResponse = APRSResponse()
                    response.result = "ok"
                    val entries = ArrayList<APRSEntry>(1)
                    entries.add(aprsEntry)
                    response.entries = entries
                    Observable.just(response)
                }
                .doOnNext {
                    handleAPRSResponse(it)
                }
    }

    fun handleAPRSResponse(response: APRSResponse) {
        if (!response.isSuccess()) {
            return
        }

        val entries = response.entries
        if (entries == null || entries.isEmpty()) {
            return
        }

        val result = findAPRSEntry(callSignToQuery, response.entries)
        if (result != null) {
            lastAPRSEntry = result
            hasReceivedAPRSEntry = true
        }

        return
    }

    fun valuesForCalculationsAreValid(entry: APRSEntry): Boolean {
        return hasReceivedLocation
                && hasReceivedRotation
                && hasReceivedAPRSEntry
                && !entry.lat.isNullOrBlank()
                && !entry.lng.isNullOrBlank()
    }

    fun doTheMathyThings() {
        if (!valuesForCalculationsAreValid(lastAPRSEntry)) {
            return
        }

        val userLat = currentUserLocation.lat
        val userLong = currentUserLocation.long
        val azimuthDegrees = currentUserLocation.azimuthDegrees
        val pitchDegrees = currentUserLocation.pitchDegree
        val payloadLat = lastAPRSEntry.lat?.toDouble()
        val payloadLong = lastAPRSEntry.lng?.toDouble()

        if (payloadLat == null || payloadLong == null) {
            return
        }

        val distanceResult = FloatArray(3)
        Location.distanceBetween(userLat, userLong, payloadLat, payloadLong, distanceResult)
        val distance = distanceResult[0]

        val deltaX = payloadLong - userLong
        val half: Half
        if (deltaX < 0) {
            half = Half.LEFT
        } else {
            half = Half.RIGHT
        }

        Location.distanceBetween(userLat, userLong, payloadLat, userLong, distanceResult)
        val yDistance = distanceResult[0]

        val radFromNorth = Math.acos(yDistance / distance.toDouble())
        //Calculations for x from center of screen, this is assuming the phone is 0.3 meter, ~1 foot, away from your eyes
        val xDistanceMetersFromNorthOnScreen = (Math.tan(radFromNorth) * Constants.VIEWPORT_TO_PHONE)
        val xDistanceInchesFromNorthOnScreen = CalcUtil.convertMetersToInch(xDistanceMetersFromNorthOnScreen)
        val pixelsOffsetOnScreen = Util.getScreenDensityDpi(this) * xDistanceInchesFromNorthOnScreen
        val xOnScreen: Double
        if (half == Half.LEFT) {
            xOnScreen = -pixelsOffsetOnScreen
        } else {
            xOnScreen = pixelsOffsetOnScreen
        }
        setPositionOfTarget(xOnScreen.toInt() - CalcUtil.convertDegreeToPixels(this, azimuthDegrees - currentUserLocation.declination).toInt(), 0)
    }

    fun setPositionOfTarget(xPos: Int, yPos: Int) {
        targetBox.x = Util.getScreenSize(this).x / 2 + xPos - (targetBoxWidth / 2.0F)
        targetBox.y = Util.getScreenSize(this).y / 2 + yPos - (targetBoxHeight / 2.0F)
    }

    fun findAPRSEntry(callSign: String?, entries: List<APRSEntry>?): APRSEntry? {
        if (callSign.isNullOrBlank() || entries == null) {
            return null
        }

        for (entry in entries) {
            if (entry.name == callSign) {
                return entry
            }
        }
        return null
    }

    fun setup() {
        subscriptions.add(aprsPollingObservable(callSignToQuery).subscribe())
        setupGPSSensor()
        setUpRotationSensor()
    }

    fun cleanUp() {
        subscriptions.clear()
        hasReceivedLocation = false
        hasReceivedRotation = false
    }

    fun setupGPSSensor() {
//        subscriptions.add(Util.getGPSSensorStream(this, ACCESS_FINE_LOCATION_CODE)
//                .doOnNext {
//                    currentUserLocation.lat = it.latitude
//                    currentUserLocation.long = it.longitude
//                    currentUserLocation.altitude = it.altitude
//                    hasReceivedLocation = true
//
//                    val aprsEntry: APRSEntry = APRSEntry()
//                    aprsEntry.lng = "-122.4163504"
//                    aprsEntry.lat = "37.7801809"
//                    lastAPRSEntry = aprsEntry
//                    hasReceivedAPRSEntry = true
//
//                    doTheMathyThings()
//                }
//                .subscribe()
//        )

        currentUserLocation.lat = 37.776797
        currentUserLocation.long = -122.416603
        currentUserLocation.altitude = 10.0
        hasReceivedLocation = true

        currentUserLocation.declination = GeomagneticField(currentUserLocation.lat.toFloat(),
                currentUserLocation.long.toFloat(),
                currentUserLocation.altitude.toFloat(),
                System.currentTimeMillis()).declination.toDouble()
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
                    hasReceivedRotation = true

                    Log.e("SEAN", String.format("azimuth: %f", currentUserLocation.azimuthDegrees - currentUserLocation.declination))

                    doTheMathyThings()
                }
                .subscribe()
        )
    }

    fun setCallSignButton(text: String?) {
        if (text.isNullOrBlank()) {
            callSignButton.setText(R.string.call_sign_button)
        } else {
            callSignButton.text = text
        }
    }

    class LocationHolder {
        var lat: Double = 0.0
        var long: Double = 0.0
        var altitude: Double = 0.0
        var azimuthDegrees: Double = 0.0
        var pitchDegree: Double = 0.0
        var declination: Double = 0.0
    }
}
