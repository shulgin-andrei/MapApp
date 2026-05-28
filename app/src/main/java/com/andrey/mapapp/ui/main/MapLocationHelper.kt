package com.andrey.mapapp.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapLocationHelper(
    private val activity: ComponentActivity,
    private val onLocationCaptured: (GeoPoint) -> Unit
) {
    private var mapView: MapView? = null
    private var myLocationOverlay: MyLocationNewOverlay? = null

    fun attachMapView(mapView: MapView) {
        this.mapView = mapView
    }
    // launcher for an sample via gps
    private val requestLocationPermissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            getCurrentLocationAndSave()
        } else {
            Toast.makeText(activity, "Без GPS нельзя определить точку пробы", Toast.LENGTH_SHORT).show()
        }
    }

    // launcher for geo position
    private val requestGpsOverlayLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            activateMyLocationOverlay()
        } else {
            Toast.makeText(activity, "Не удалось включить GPS: нет разрешений", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkPermissionAndGetLocation() {
        if (hasLocationPermissions()) {
            getCurrentLocationAndSave()
        } else {
            requestLocationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    fun checkLocationPermissionAndEnableGps() {
        if (hasLocationPermissions()) {
            activateMyLocationOverlay()
        } else {
            requestGpsOverlayLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    fun disableMyLocation() {
        myLocationOverlay?.disableMyLocation()
    }

    fun enableMyLocationIfInitialized() {
        myLocationOverlay?.enableMyLocation()
    }

    private fun hasLocationPermissions(): Boolean {
        val fineLoc = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLoc = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fineLoc == PackageManager.PERMISSION_GRANTED || coarseLoc == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocationAndSave() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                onLocationCaptured(GeoPoint(location.latitude, location.longitude))
            } else {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { freshLocation ->
                        if (freshLocation != null) {
                            onLocationCaptured(GeoPoint(freshLocation.latitude, freshLocation.longitude))
                        } else {
                            Toast.makeText(activity, "Включите GPS датчик телефона", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
    }

    private fun activateMyLocationOverlay() {
        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) {
            Toast.makeText(activity, "Включите геолокацию в настройках телефона!", Toast.LENGTH_LONG).show()
            return
        }

        if (myLocationOverlay == null) {
            val provider = GpsMyLocationProvider(activity)
            myLocationOverlay = MyLocationNewOverlay(provider, mapView).apply {
                setDrawAccuracyEnabled(true)
            }
            mapView!!.overlays.add(myLocationOverlay)
        }

        myLocationOverlay?.apply {
            enableMyLocation()
            enableFollowLocation()
            myLocation?.let { userPoint ->
                mapView!!.controller.animateTo(userPoint)
                mapView!!.controller.setZoom(18.0)
            }
        }
    }
}