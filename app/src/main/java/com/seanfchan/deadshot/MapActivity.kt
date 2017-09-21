package com.seanfchan.deadshot

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.TextView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.*
import com.seanfchan.deadshot.api.APRSEntry
import com.seanfchan.deadshot.cache.LocationEntryCache
import io.reactivex.disposables.Disposable
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import com.google.android.gms.maps.model.BitmapDescriptor

class MapActivity : AppCompatActivity(), GoogleMap.InfoWindowAdapter {
    private val CAR_TYPE = "car"

    private lateinit var mapView: MapView
    private lateinit var map: GoogleMap
    private lateinit var prefix: String
    private lateinit var locationEntryCache: LocationEntryCache

    private var subscription: Disposable? = null
    private var alreadyZoomed: Boolean = false
    private val alreadyDrawnMarkerIds: MutableSet<String> = HashSet()
    private val currentPolylines: MutableSet<Polyline> = HashSet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationEntryCache = (application as DeadShot).locationEntryCache
        prefix = resources.getString(R.string.callsign) + '-'
        setContentView(R.layout.activity_map)
        mapView = findViewById(R.id.mapview)
        mapView.onCreate(savedInstanceState)
        val backButton: View = findViewById(R.id.back_button)
        backButton.setOnClickListener {
            finish()
        }
        setUpMapView()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStart() {
        super.onStart()
        startPolling()
    }

    override fun onStop() {
        super.onStop()
        stopPolling()
    }

    private fun startPolling() {
        locationEntryCache.startPolling()
        subscription = locationEntryCache
                .cacheUpdatedObservable()
                .doOnNext {
                    syncCacheToMap()
                }
                .subscribe()
    }

    private fun stopPolling() {
        locationEntryCache.stopPolling()
        subscription?.dispose()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    private fun setUpMapView() {
        mapView.getMapAsync { googleMap ->
            map = googleMap
            map.uiSettings.isZoomGesturesEnabled = true
            map.uiSettings.isZoomControlsEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true
            map.setPadding(resources.getDimensionPixelOffset(R.dimen.back_button_padding), 0, 0, 0)
            map.setInfoWindowAdapter(this)

            try {
                map.isMyLocationEnabled = true
            } catch (ignored: SecurityException) {

            }
            syncCacheToMap()
        }
    }

    override fun getInfoContents(marker: Marker?): View {
        @SuppressLint("InflateParams")
        val infoWindow = layoutInflater.inflate(R.layout.info_window_layout, null)
        val altitude: TextView = infoWindow.findViewById(R.id.altitude)
        val time: TextView = infoWindow.findViewById(R.id.time)
        altitude.text = marker?.title
        time.text = marker?.snippet
        return infoWindow
    }

    override fun getInfoWindow(marker: Marker?): View? {
        return null
    }

    private fun syncCacheToMap() {
        val trackerMap: MutableMap<String, MutableList<APRSEntry>> = HashMap()
        populateTrackerMap(trackerMap, locationEntryCache.entries())
        renderMapWithTrackerMap(trackerMap)
    }

    private fun populateTrackerMap(trackerMap: MutableMap<String, MutableList<APRSEntry>>, entries: List<APRSEntry>) {
        for (entry in entries) {
            if (entry.name == null) {
                continue
            }
            val key = entry.name?.replace(prefix, "") ?: continue
            if (!trackerMap.containsKey(key)) {
                trackerMap.put(key, ArrayList())
            }
            trackerMap[key]?.add(entry)
        }
    }

    private fun renderMapWithTrackerMap(trackerMap: MutableMap<String, MutableList<APRSEntry>>) {
        val builder = LatLngBounds.Builder()
        if (trackerMap.isEmpty()) {
            return
        }
        clearPolylines()
        val listOfTrackers: MutableCollection<MutableList<APRSEntry>> = trackerMap.values
        listOfTrackers.forEach {
            val listOfPositions = it
            listOfPositions.forEach { builder.include(renderEntry(it,
                    listOfPositions.indexOf(it) == (listOfPositions.count() - 1))) }
            renderPolyline(listOfPositions)
        }
        zoomMapIfNeeded(builder.build())
    }

    private fun renderEntry(entry: APRSEntry, isLast: Boolean): LatLng {
        val position = positionFromEntry(entry)
        val id = markerIdFromEntry(entry)

        if (alreadyDrawnMarkerIds.contains(id)) {
            return position
        }
        alreadyDrawnMarkerIds.add(id)

        val time: String = formattedTimeFromEntry(entry)
        val altitude: String = formattedAltitudeFromEntry(entry)

        map.addMarker(createMarker(altitude, time, position, entry.type == CAR_TYPE, isLast))
        return position
    }

    private fun markerIdFromEntry(entry: APRSEntry): String {
        return entry.name + entry.time
    }

    private fun positionFromEntry(entry: APRSEntry): LatLng {
        val lat: String = entry.lat ?: return LatLng(0.0, 0.0)
        val lng: String = entry.lng ?: return LatLng(0.0, 0.0)
        return LatLng(lat.toDouble(), lng.toDouble())
    }

    private fun formattedTimeFromEntry(entry: APRSEntry): String {
        val time: String = entry.time as String
        return SimpleDateFormat.getTimeInstance(SimpleDateFormat.LONG).format(Date(time.toLong() * 1000))
    }

    private fun formattedAltitudeFromEntry(entry: APRSEntry): String {
        if (entry.altitude != null) {
            val roundedAltitude = (entry.altitude as String).toDouble().toInt()
            return (roundedAltitude.toString()) + " " + getString(R.string.meters_unit)
        } else {
            return resources.getString(R.string.no_altitude)
        }
    }

    private fun createMarker(altitude: String, time: String, position: LatLng, isCar: Boolean, isLast: Boolean):
            MarkerOptions {
        val iconResource: Int
        if (isCar) {
            iconResource = R.drawable.car_icon
        } else if (isLast) {
            iconResource = R.drawable.balloon_icon
        } else {
            iconResource = R.drawable.balloon_point
        }
        return MarkerOptions()
                .title(altitude)
                .snippet(time)
                .position(position)
                .anchor(0.5f, 0.5f)
                .icon(getMarkerIconFromDrawable(getDrawable(iconResource)))
    }

    private fun clearPolylines() {
        currentPolylines.forEach {
            it.remove()
        }
        currentPolylines.clear()
    }

    private fun zoomMapIfNeeded(bounds: LatLngBounds) {
        if (!alreadyZoomed) {
            map.setOnMapLoadedCallback {
                val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds,
                        resources.getDimensionPixelOffset(R.dimen.viewport_padding))
                map.moveCamera(cameraUpdate)
            }
            alreadyZoomed = true
        }
    }

    private fun renderPolyline(entries: List<APRSEntry>) {
        val polylinePointList: MutableList<LatLng> = ArrayList()
        for (entry in entries) {
            val lat: String = entry.lat ?: continue
            val lng: String = entry.lng ?: continue
            polylinePointList.add(LatLng(lat.toDouble(), lng.toDouble()))
        }
        currentPolylines.add(map.addPolyline(
                PolylineOptions()
                        .width(resources.getDimensionPixelOffset(R.dimen.line_width).toFloat())
                        .color(resources.getColor(R.color.line_color))
                        .addAll(polylinePointList)
        ))
    }

    private fun getMarkerIconFromDrawable(drawable: Drawable): BitmapDescriptor {
        val canvas = Canvas()
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        canvas.setBitmap(bitmap)
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}