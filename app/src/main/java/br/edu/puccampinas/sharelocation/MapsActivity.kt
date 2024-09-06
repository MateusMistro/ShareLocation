package br.edu.puccampinas.sharelocation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.ActivityCompat

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import br.edu.puccampinas.sharelocation.databinding.ActivityMapsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var lastLocation: Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var db: FirebaseFirestore

    companion object{
        private const val LOCATION_REQUEST_CODE = 1
        private const val LOCATION_UPDATE_INTERVAL = 1000L // 1 segundo
    }

    private val handler = Handler(Looper.getMainLooper())
    private val locationUpdateRunnable = object : Runnable {
        override fun run() {
            getCurrentLocation()
            fetchAllUsersAndUpdateMarkers()
            handler.postDelayed(this, LOCATION_UPDATE_INTERVAL)
        }
    }

    private var shouldUpdateCamera = true
    private var currentMarker: Marker? = null
    private val userMarkers = mutableMapOf<String, Marker>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        binding.btnLogout.setOnClickListener {
            // Parar o serviço de localização
            stopLocationService()

            // Obter o usuário atual
            val user = FirebaseAuth.getInstance().currentUser
            user?.let {
                val userId = it.uid
                val zeroLocationMap = hashMapOf(
                    "latitude" to 0.0,
                    "longitude" to 0.0
                )

                db.collection("pessoa").document(userId)
                    .update(zeroLocationMap as Map<String, Any>)
                    .addOnSuccessListener {
                        // Remover o marcador do usuário logado
                        val marker = userMarkers[userId]
                        marker?.remove()
                        userMarkers.remove(userId)

                        // Realizar o logout
                        auth.signOut()
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            baseContext,
                            "Erro ao atualizar localização: $e",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            } ?: run {
                // Se não há usuário logado, apenas realizar o logout
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        }



        // Start the location updates
        handler.post(locationUpdateRunnable)

        // Start the foreground service for location updates
        val serviceIntent = Intent(this, LocationService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.setOnMarkerClickListener(this)

        setUpMap()

        // Fetch and place markers for all users
        fetchAllUsersAndUpdateMarkers()
    }

    private fun setUpMap(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_REQUEST_CODE)
            return
        }
        mMap.isMyLocationEnabled = true
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_REQUEST_CODE)
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                lastLocation = location
                val currentLatLong = LatLng(location.latitude, location.longitude)
                fetchUserNameAndPlaceMarker(currentLatLong)  // Fetch user name and place marker
                salvarLocalizacaonoFirestore(location.latitude, location.longitude)
                if (shouldUpdateCamera) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLong, 12f))
                    shouldUpdateCamera = false
                }
            }
        }
    }

    private fun fetchUserNameAndPlaceMarker(location: LatLng) {
        val user = FirebaseAuth.getInstance().currentUser
        user?.let {
            db.collection("pessoa").document(it.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null) {
                        val userName = document.getString("nome")
                        if (userName != null) {
                            placeMarkerOnMap(location, userName)
                        } else {
                            placeMarkerOnMap(location, "Usuário Desconhecido")
                        }
                    } else {
                        placeMarkerOnMap(location, "Usuário Desconhecido")
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        baseContext,
                        "Erro ao buscar nome do usuário: $e",
                        Toast.LENGTH_SHORT
                    ).show()
                    placeMarkerOnMap(location, "Erro ao buscar nome")
                }
        }
    }

    private fun fetchAllUsersAndUpdateMarkers() {
        db.collection("pessoa")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val latitude = document.getDouble("latitude")
                    val longitude = document.getDouble("longitude")
                    val userName = document.getString("nome")
                    val userId = document.id

                    if (latitude != null && longitude != null && userName != null) {
                        val location = LatLng(latitude, longitude)
                        if (userMarkers.containsKey(userId)) {
                            // Atualiza a posição do marcador existente
                            userMarkers[userId]?.position = location
                        } else {
                            // Cria um novo marcador com cor azul
                            val markerOptions = MarkerOptions()
                                .position(location)
                                .title(userName)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                            val marker = mMap.addMarker(markerOptions)
                            if (marker != null) {
                                userMarkers[userId] = marker
                            }
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    baseContext,
                    "Erro ao buscar dados: $e",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }


    private fun placeMarkerOnMap(currentLatLong: LatLng, title: String) {
        // Remove the current marker if it exists
        currentMarker?.remove()

        // Cria um novo marcador com cor azul
        val markerOptions = MarkerOptions()
            .position(currentLatLong)
            .title(title)
            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        currentMarker = mMap.addMarker(markerOptions)
    }


    override fun onMarkerClick(p0: Marker) = false

    private fun salvarLocalizacaonoFirestore(latitude: Double, longitude: Double) {
        val pessoaMap = hashMapOf(
            "latitude" to latitude,
            "longitude" to longitude
        )

        val user = FirebaseAuth.getInstance().currentUser
        user?.let {
            db.collection("pessoa").document(it.uid)
                .update(pessoaMap as Map<String, Any>)
                .addOnSuccessListener {

                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        baseContext,
                        "Erro ao enviar dados: $e",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop the location updates
        handler.removeCallbacks(locationUpdateRunnable)
    }

    private fun stopLocationService() {
        val stopIntent = Intent(this, LocationService::class.java)
        stopService(stopIntent)
    }


}
