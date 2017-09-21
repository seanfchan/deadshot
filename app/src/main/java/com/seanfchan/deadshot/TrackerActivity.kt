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
import android.widget.EditText
import android.widget.TextView
import com.seanfchan.deadshot.api.APRSEntry
import com.seanfchan.deadshot.api.APRSService
import com.seanfchan.deadshot.util.CalcUtil
import com.seanfchan.deadshot.util.Constants
import com.seanfchan.deadshot.util.Util
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

class TrackerActivity : AppCompatActivity() {

    val ACCESS_FINE_LOCATION_CODE = 100

    lateinit var aprsService: APRSService
    lateinit var subscriptions: CompositeDisposable
    lateinit var currentUserLocation: LocationHolder
    lateinit var lastAPRSEntry: APRSEntry

    lateinit var bearingText: TextView
    lateinit var pitchText: TextView
    lateinit var azimuthText: TextView
    lateinit var currentPitchText: TextView

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
        bearingText = findViewById(R.id.bearing)
        pitchText = findViewById(R.id.pitch)
        targetBox = findViewById(R.id.target_box)
        azimuthText = findViewById(R.id.azimuth)
        currentPitchText = findViewById(R.id.current_pitch)
        targetBoxWidth = targetBox.layoutParams.width
        targetBoxHeight = targetBox.layoutParams.height
        lastAPRSEntry = APRSEntry()

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

    override fun onPause() {
        super.onPause()
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
                })
                .setNegativeButton("Cancel", { _, _ -> })

        dialog.show()
    }

    fun handleAPRSResponse(entries: List<APRSEntry>?) {
        if (entries == null || entries.isEmpty()) {
            return
        }

        val result = findAPRSEntry(callSignToQuery, entries)
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
        val payloadAltitude = lastAPRSEntry.altitude?.toDouble()

        if (payloadLat == null || payloadLong == null || payloadAltitude == null) {
            return
        }

        setText(currentUserLocation.azimuthDegrees.toString(), azimuthText)

        val distanceResult = FloatArray(3)
        Location.distanceBetween(userLat, userLong, payloadLat, payloadLong, distanceResult)
        val distance = distanceResult[0]

        val payloadHalf = getHalf(userLong, payloadLong)
        val userHalf = getHalfBasedOnTrueNorth(currentUserLocation.azimuthDegrees)

        val bearing = CalcUtil.getBearing(userLat, userLong, payloadLat, payloadLong)
        setText(bearing.toString(), bearingText)

        Location.distanceBetween(userLat, userLong, payloadLat, userLong, distanceResult)
        val yDistance = distanceResult[0]

        val radFromNorth = Math.acos(yDistance / distance.toDouble())
        val degreeFromNorth = Math.toDegrees(radFromNorth)

        val azimuth = currentUserLocation.azimuthDegrees
        val totalDegrees: Double
        if (payloadHalf == userHalf) {
            totalDegrees = currentUserLocation.azimuthDegrees - bearing
        } else {
            if (payloadHalf == Half.LEFT) {
                val clockwiseDegree = 360 - bearing - azimuth
                val counterClockwiseDegree = bearing + azimuth
                if (clockwiseDegree < counterClockwiseDegree) {
                    totalDegrees = clockwiseDegree
                } else {
                    totalDegrees = -counterClockwiseDegree
                }
            } else {
                val clockwiseDegree = 360 - azimuth - bearing
                val counterClockwiseDegree = azimuth + bearing
                if (clockwiseDegree < counterClockwiseDegree) {
                    totalDegrees = clockwiseDegree
                } else {
                    totalDegrees = -counterClockwiseDegree
                }
            }
        }

        val rad = Math.toRadians(totalDegrees)

        //Calculations for x from center of screen, this is assuming the phone is 0.3 meter, ~1 foot, away from your eyes
        val xDistanceMetersFromNorthOnScreen = (Math.tan(rad) * Constants.VIEWPORT_TO_PHONE)
        val xDistanceInchesFromNorthOnScreen = CalcUtil.convertMetersToInch(xDistanceMetersFromNorthOnScreen)
        val pixelsOffsetOnScreen = Util.getScreenDensityDpi(this) * xDistanceInchesFromNorthOnScreen
//        val xOnScreen: Double
//        if (half == Half.LEFT) {
//            xOnScreen = -pixelsOffsetOnScreen
//        } else {
//            xOnScreen = pixelsOffsetOnScreen
//        }

        val deltaZ = payloadAltitude - currentUserLocation.altitude
        val radPitch = Math.atan(deltaZ / distance)
        setText(currentUserLocation.pitchDegree.toString(), currentPitchText)
        setText(Math.toDegrees(radPitch).toString(), pitchText)
//        Log.e("SEAN", String.format("pitch: %f", Math.toDegrees(radPitch)))
        val yDistanceMetersFromCenterOnScreen = Math.tan(radPitch) * Constants.VIEWPORT_TO_PHONE
        val yDistanceInchesFromCenterOnScreen = CalcUtil.convertMetersToInch(yDistanceMetersFromCenterOnScreen)
        val yPixelOffsetOnScreen = Util.getScreenDensityDpi(this) * yDistanceInchesFromCenterOnScreen
        val yOnScreen: Double
        if (deltaZ < 0) {
            yOnScreen = yPixelOffsetOnScreen
        } else {
            yOnScreen = -yPixelOffsetOnScreen
        }

        Log.e("SEAN", String.format("azimuth: %f", currentUserLocation.azimuthDegrees))
//        Log.e("SEAN", String.format("pitch: %f", currentUserLocation.pitchDegree))


//        setPositionOfTarget((xOnScreen + CalcUtil.convertDegreeToPixels(this, azimuthDegrees + currentUserLocation.declination)).toInt(), (yOnScreen + CalcUtil.convertDegreeToPixels(this, pitchDegrees)).toInt())
        setPositionOfTarget(-pixelsOffsetOnScreen.toInt(), (yOnScreen + CalcUtil.convertDegreeToPixels(this, pitchDegrees)).toInt())
//        Log.e("SEAN", String.format("azimuth: %f", currentUserLocation.azimuthDegrees + currentUserLocation.declination))
    }

    fun setPositionOfTarget(xPos: Int, yPos: Int) {
        targetBox.x = Util.getScreenSize(this).x / 2 + xPos - (targetBoxWidth / 2.0F)
        targetBox.y = Util.getScreenSize(this).y / 2 + yPos - (targetBoxHeight / 2.0F)
    }

    fun setText(text: String, textView: TextView) {
        Observable.fromCallable {
            textView.text = text
        }
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe()
    }

    fun findAPRSEntry(callSign: String?, entries: List<APRSEntry>?): APRSEntry? {
        if (callSign.isNullOrBlank() || entries == null) {
            return null
        }

        for (entry in entries.reversed()) {
            if (entry.name == callSign) {
                return entry
            }
        }
        return null
    }

    fun setup() {
        val locationboardEntryCache = (application as DeadShot).locationEntryCache
        locationboardEntryCache.startPolling()
        subscriptions.add(locationboardEntryCache.cacheUpdatedObservable().subscribe {
            handleAPRSResponse(locationboardEntryCache.entries())
        })
        setupGPSSensor()
        setUpLocationSensors()
    }

    fun cleanUp() {
        subscriptions.clear()
        hasReceivedLocation = false
        hasReceivedRotation = false
    }

    fun setupGPSSensor() {
        subscriptions.add(Util.getGPSSensorStream(this, ACCESS_FINE_LOCATION_CODE)
                .doOnNext {
                    currentUserLocation.lat = it.latitude
                    currentUserLocation.long = it.longitude
                    currentUserLocation.altitude = it.altitude
                    hasReceivedLocation = true

                    val geomagneticField = GeomagneticField(currentUserLocation.lat.toFloat(),
                            currentUserLocation.long.toFloat(),
                            currentUserLocation.altitude.toFloat(),
                            System.currentTimeMillis())

                    currentUserLocation.declination = geomagneticField.declination.toDouble()
                    currentUserLocation.inclination = geomagneticField.inclination.toDouble()

                    doTheMathyThings()
                }
                .subscribe()
        )
    }

    fun setUpLocationSensors() {
        subscriptions.add(Util.getRotationVectorObservable(this)
                .observeOn(Schedulers.computation())
                .doOnNext {
                    val vectors = it.values
                    val rotMat = FloatArray(9)
                    val azimuthAboutYAxis = FloatArray(9)
                    val pitchAboutZAxis = FloatArray(9)
                    val orientation = FloatArray(3)

                    SensorManager.getRotationMatrixFromVector(rotMat, vectors)
                    SensorManager.remapCoordinateSystem(rotMat, SensorManager.AXIS_X, SensorManager.AXIS_Z, azimuthAboutYAxis)
                    currentUserLocation.azimuthDegrees = (Math.toDegrees(SensorManager.getOrientation(azimuthAboutYAxis, orientation)[0].toDouble()) - currentUserLocation.declination + 360) % 360

                    SensorManager.remapCoordinateSystem(rotMat, SensorManager.AXIS_Z, SensorManager.AXIS_X, pitchAboutZAxis)
                    currentUserLocation.pitchDegree = ((Math.toDegrees(SensorManager.getOrientation(pitchAboutZAxis, orientation)[1].toDouble())))
                    hasReceivedRotation = true

                    doTheMathyThings()
                }
                .subscribe()
        )
    }

    fun getHalf(originLong: Double, targetLong: Double): Half {
        val deltaLong = originLong - targetLong
        if (deltaLong < 0) {
            return Half.RIGHT
        } else {
            return Half.LEFT
        }
    }

    fun getHalfBasedOnTrueNorth(degree: Double): Half {
        if (degree >= 0 && degree <= 180) {
            return Half.RIGHT
        } else {
            return Half.LEFT
        }
    }

    class LocationHolder {
        var lat: Double = 0.0
        var long: Double = 0.0
        var altitude: Double = 0.0
        var azimuthDegrees: Double = 0.0
        var pitchDegree: Double = 0.0
        var declination: Double = 0.0
        var inclination: Double = 0.0
    }
}
