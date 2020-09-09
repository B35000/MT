package com.color.matt.Activities

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.core.app.ActivityCompat
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.color.matt.Constants
import com.color.matt.Fragments.MainSettings
import com.color.matt.Fragments.ViewRoute
import com.color.matt.R
import com.color.matt.Utilities.Apis
import com.color.matt.databinding.ActivityMapsBinding
import com.color.mattdriver.Models.driver
import com.color.mattdriver.Models.organisation
import com.color.mattdriver.Models.route
import com.google.android.gms.location.*
import com.google.android.gms.maps.*

import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import java.io.Serializable
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.schedule
import com.google.maps.android.PolyUtil

class MapsActivity : AppCompatActivity(),
    OnMapReadyCallback,
    GoogleMap.OnMyLocationClickListener,
    GoogleMap.OnMyLocationButtonClickListener,
    GoogleMap.OnMarkerClickListener,
    MainSettings.MainSettingsInterface
{
    val TAG = "MapsActivity"
    val _settings = "_settings"
    val _view_route = "_view_route"
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private val locationRequestCode = 1000
    private var wayLatitude = 0.0
    private var wayLongitude = 0.0
    var has_set_my_location = false

    val constants = Constants()
    val db = Firebase.firestore
    var organisations: ArrayList<organisation> = ArrayList()
    var routes: ArrayList<route> = ArrayList()
    var positions: HashMap<String,ArrayList<driver_pos>> = HashMap()
    var added_postitions: ArrayList<String> = ArrayList()
    var driver_listeners: ArrayList<ListenerRegistration> = ArrayList()

    var mapView: View? = null
    val ZOOM = 15f
    val ZOOM_FOCUSED = 16f
    lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    var mLastKnownLocations: ArrayList<Location> = ArrayList()
    var my_marker: Marker? = null
    var my_marker_trailing_circle: Circle? = null
    var AUTOCOMPLETE_REQUEST_CODE = 1

    var is_loading = false


    override fun onCreate(savedInstanceState: Bundle?) {
        Log.e(TAG,"onCreate")
        super.onCreate(savedInstanceState)
        binding = ActivityMapsBinding.inflate(layoutInflater)

        setContentView(binding.root)
        val actionBar: ActionBar = supportActionBar!!
        actionBar.hide()

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapView = mapFragment.view
        mapFragment.getMapAsync(this)

        set_up_getting_my_location()
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        Places.initialize(applicationContext, Apis().places_api_key)
        val placesClient: PlacesClient = Places.createClient(this)

        if(!constants.SharedPreferenceManager(applicationContext).get_current_data().equals("")){
            set_session_data()
            set_up_driver_listeners(false)
        }
        load_organisations()

        binding.settings.setOnClickListener {
            constants.touch_vibrate(applicationContext)
            supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                .replace(binding.money.id,MainSettings.newInstance("",""),_settings).commit()
        }

        set_network_change_receiver()
    }

    fun set_network_change_receiver(){
        val networkCallback: ConnectivityManager.NetworkCallback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network?) {
                // network available
                whenNetworkAvailable()
            }

            override fun onLost(network: Network?) {
                // network unavailable
                whenNetworkLost()
            }
        }

        val connectivityManager: ConnectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } else {
            val request: NetworkRequest = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }

        if(!constants.isOnline(applicationContext)){
            whenNetworkLost()
        }
    }

    fun whenNetworkLost(){
        val alpha_hidden = constants.dp_to_px(-20f, applicationContext)
        val alpha_shown = 0f
        val duration = 200L
        val delay = 1000L
        val mHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message?) {
                binding.networkRelative.visibility = View.VISIBLE
                binding.noInternetText.text = getString(R.string.no_internet_connection)
                binding.noInternetText.setBackgroundColor(resources.getColor(R.color.red))
                val valueAnimator = ValueAnimator.ofFloat(alpha_hidden, alpha_shown)
                val listener = ValueAnimator.AnimatorUpdateListener{
                    val value = it.animatedValue as Float
                    binding.networkRelative.translationY = value
                }
                valueAnimator.addUpdateListener(listener)
                valueAnimator.interpolator = LinearOutSlowInInterpolator()
                valueAnimator.duration = duration
                valueAnimator.start()
            }
        }

        Timer().schedule(100){
            val message = mHandler.obtainMessage()
            message.sendToTarget()
        }
    }

    fun whenNetworkAvailable(){
        val alpha_hidden = constants.dp_to_px(-20f, applicationContext)
        val alpha_shown = 0f
        val duration = 200L
        val delay = 1000L

        val mHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message?) {
                binding.noInternetText.text = getString(R.string.back_online)
                binding.noInternetText.setBackgroundColor(resources.getColor(R.color.green))
                val valueAnimator = ValueAnimator.ofFloat(alpha_shown, alpha_hidden)
                val listener = ValueAnimator.AnimatorUpdateListener{
                    val value = it.animatedValue as Float
                    binding.networkRelative.translationY = value
                    if(value==alpha_hidden){
                        binding.networkRelative.visibility = View.GONE
                    }
                }
                valueAnimator.addUpdateListener(listener)
                valueAnimator.interpolator = LinearOutSlowInInterpolator()
                valueAnimator.duration = duration
                valueAnimator.startDelay = delay
                valueAnimator.start()
            }
        }

        Timer().schedule(100){
            val message = mHandler.obtainMessage()
            message.sendToTarget()
        }
    }




    fun load_organisations(){
        val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()!!

        db.collection(constants.organisations)
            .document(user.phone.country_name)
            .collection(constants.country_organisations)
            .get().addOnSuccessListener {
                organisations.clear()
                if(it.documents.isNotEmpty()){
                    for(item in it.documents){
                        val org_id = item["org_id"] as String
                        val org_name = item["name"] as String
                        val country = item["country"] as String
                        val creation_time = item["creation_time"] as Long

                        val org = organisation(org_name,creation_time)
                        org.org_id = org_id
                        org.country = country

                        organisations.add(org)
                    }
                }
                load_organisation_drivers()
            }
    }

    var driver_iter = 0
    fun load_organisation_drivers(){
        driver_iter = 0
        for(org in organisations){
            val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()!!
            val time = Calendar.getInstance().timeInMillis
            db.collection(constants.organisations)
                .document(user.phone.country_name)
                .collection(constants.country_organisations)
                .document(org.org_id!!)
                .collection(constants.drivers)
                .get().addOnSuccessListener {
                    if(!it.isEmpty){
                        for(doc in it.documents) {
                            val driver_id = doc["driver_id"] as String
                            val org_id = doc["org_id"] as String
                            val join_time = doc["join_time"] as Long

                            val driver = driver(driver_id,org_id, join_time)
                            if(org.org_id.equals(org_id)){
                                org.drivers.add(driver)
                            }
                        }
                    }
                    driver_iter+=1
                    if(driver_iter >= organisations.size){
                        //were done
                        load_routes()
                    }
                }.addOnFailureListener{
                }
        }
    }

    fun load_routes(){
        val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()!!

        db.collection(constants.organisations)
            .document(user.phone.country_name)
            .collection(constants.country_routes)
            .get().addOnSuccessListener {
                routes.clear()
                if(it.documents.isNotEmpty()){
                    for(item in it.documents) {
                        val organisation_id = item["organisation_id"] as String
                        val creation_time = item["creation_time"] as Long
                        val route_id = item["route_id"] as String
                        val country = item["country"] as String
                        val creater = item["creater"] as String

                        val route = Gson().fromJson(item["route"].toString(), route::class.java)
                        var disabled = false
                        if(item.contains("disabled")){
                            disabled = item["disabled"] as Boolean
                        }
                        route.disabled = disabled

                        routes.add(route)
                    }
                }
                store_session_data()
                set_up_driver_listeners(false)
            }
    }

    var org_pos = 0
    fun load_all_drivers_first(){
        org_pos = 0
        for(org in organisations) {
            db.collection(constants.organisations)
                .document(org.country!!)
                .collection(constants.country_organisations)
                .document(org.org_id!!)
                .collection(constants.driver_locations)
                .get().addOnSuccessListener {
                    if(!it.isEmpty){
                        for(document in it.documents){
                            val pos_id = document["pos_id"] as String
                            val creation_time = document["creation_time"] as Long
                            val user = document["user"] as String
                            val loc = Gson().fromJson(document["loc"] as String, LatLng::class.java)
                            val organisation = document["organisation"] as String
                            val route = document["route"] as String

                            val driverPos = driver_pos(pos_id,creation_time,user,loc,organisation,route)
                            if(!is_location_contained(driverPos) && Calendar.getInstance().timeInMillis-creation_time <= constants.update_limit){
                                put_position_item(driverPos)
                                added_postitions.add(driverPos.pos_id)
                            }
                        }
                    }
                    org_pos+=1
                    if(org_pos>=organisations.size){
                        //were done
                        set_up_driver_listeners(true)
//                        if(positions.isNotEmpty())set_all_drivers()
                    }
                }
        }
    }





    fun set_up_driver_listeners(has_attempted_to_load_driver_pos: Boolean){
        if(positions.isEmpty() && !has_attempted_to_load_driver_pos){
            load_all_drivers_first()
        }else{
            set_driver_listener_updates()
        }
    }

    fun set_driver_listener_updates(){
        remove_driver_liseners()
        for(org in organisations){
            val org_listener = db.collection(constants.organisations)
                .document(org.country!!)
                .collection(constants.country_organisations)
                .document(org.org_id!!)
                .collection(constants.driver_locations)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Log.w(TAG, "listen:error", e)
                        return@addSnapshotListener
                    }

                    for (dc in snapshots!!.documentChanges) {
                        if(dc.type.equals(DocumentChange.Type.ADDED)){
//                            Log.d(TAG, "New location: ${dc.document.data}")
                        }else if(dc.type.equals(DocumentChange.Type.MODIFIED)){
//                            Log.d(TAG, "Modified location: ${dc.document.data}")
                        }else if(dc.type.equals(DocumentChange.Type.REMOVED)){
//                            Log.d(TAG, "Removed location: ${dc.document.data}")
                        }

                        val pos_id = dc.document["pos_id"] as String
                        val creation_time = dc.document["creation_time"] as Long
                        val user = dc.document["user"] as String
                        val loc = Gson().fromJson(dc.document["loc"] as String, LatLng::class.java)
                        val organisation = dc.document["organisation"] as String
                        val route = dc.document["route"] as String

                        val driverPos = driver_pos(pos_id,creation_time,user,loc,organisation,route)
                        if(!is_location_contained(driverPos) && Calendar.getInstance().timeInMillis-creation_time <= constants.update_limit){
                            put_position_item(driverPos)
                            added_postitions.add(driverPos.pos_id)
                            when_driver_position_updated(driverPos)
                        }
                    }
            }
            driver_listeners.add(org_listener)
        }

    }

    fun remove_driver_liseners(){
        if(driver_listeners.isNotEmpty()){
            for(item in driver_listeners){
                item.remove()
            }
            driver_listeners.clear()
        }
    }

    fun when_driver_position_updated(driverPos: driver_pos){
        Log.e(TAG,"Updated driver loc: ${driverPos.pos_id}")
        set_drivers_on_map(driverPos.driver_id)
    }

    class driver_pos(var pos_id: String, var creation_time: Long, var driver_id: String, var loc: LatLng, var organisation_id: String, var route_id: String){

    }

    fun is_location_contained(driverPos: driver_pos): Boolean{
//        for(item in positions){
//            if(item.pos_id.equals(driverPos.pos_id)){
//                return true
//            }
//        }
        return added_postitions.contains(driverPos.pos_id)
    }

    fun put_position_item(locationPos: driver_pos){
        if(positions.containsKey(locationPos.driver_id)){
            positions.get(locationPos.driver_id)!!.add(locationPos)
        }else{
            val drivers_locations: ArrayList<driver_pos> = ArrayList()
            drivers_locations.add(locationPos)
            positions.put(locationPos.driver_id,drivers_locations)
        }
    }






    //map part

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        if(Constants().SharedPreferenceManager(applicationContext).isDarkModeOn()) {
            val success = googleMap.setMapStyle(MapStyleOptions(resources.getString(R.string.style_json)))
            if (!success) {
                Log.e("mapp", "Style parsing failed.")
            }
        }
        mMap.setOnMyLocationClickListener(this)

        mMap.setOnMyLocationButtonClickListener(this)
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL)
        mMap.setIndoorEnabled(false)
        mMap.setBuildingsEnabled(false)
        mMap.setOnMarkerClickListener(this)
        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.uiSettings.isCompassEnabled = true


        binding.findMeCardview.setOnClickListener {
            move_cam_to_my_location()
        }

        binding.searchPlace.setOnClickListener{
            Constants().touch_vibrate(applicationContext)
            val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()

            val fields: List<Place.Field> = Arrays.asList(Place.Field.NAME, Place.Field.LAT_LNG)
            val intent: Intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                .setCountry(user!!.phone.country_name_code)
                .build(this)
            startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
        }


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val place: Place = Autocomplete.getPlaceFromIntent(data!!)
                Log.e("MapActivity", "Place: " + place.name+" and latlng: "+place.latLng!!.latitude)
                when_search_place_result_gotten(place)
                whenNetworkAvailable()
            }
            else if (resultCode == AutocompleteActivity.RESULT_ERROR) {
                val status = Autocomplete.getStatusFromIntent(data!!)
                Log.e("MapsActivity", status.statusMessage.toString())
                whenNetworkLost()
            }
            else if (resultCode == Activity.RESULT_CANCELED) {
                // The user canceled the operation.
            }
        }

    }

    override fun onMyLocationClick(p0: Location) {

    }

    override fun onMyLocationButtonClick(): Boolean {
        return false
    }

    var viewed_driver = ""
    override fun onMarkerClick(p: Marker?): Boolean {
        move_camera(p!!.position)
        for(item in driver_map_markers){
            if(item.value.tag!!.equals(p.tag)){
                if(viewed_driver.equals("")){
                    draw_bus_route(item.key)
                    set_bus_route_details(item.key)
                }else{
                    remove_bus_route()
                    remove_bus_route_details()
                }
            }
        }
       return false
    }

    fun move_camera(pos: LatLng){
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, mMap.cameraPosition.zoom))
    }





    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            locationRequestCode -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                ) {
                    set_up_getting_my_location()
                } else {
//                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun set_up_getting_my_location(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION), locationRequestCode)
        } else{
            mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            locationRequest = LocationRequest.create()
            locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            locationRequest.setInterval(constants.update_interval)


            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    if (locationResult == null) {
                        return
                    }
                    for (location in locationResult.locations) {
                        if (location != null) {
                            wayLatitude = location.latitude
                            wayLongitude = location.longitude
                            Log.e(TAG,"wayLatitude: ${wayLatitude} longitude: ${wayLongitude}")
                            if(!has_set_my_location){
                                has_set_my_location = true
                                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), ZOOM))
                            }
                            mLastKnownLocations.add(location)
                            when_location_gotten()
                        }
                    }
                }
            }
            mFusedLocationClient.requestLocationUpdates(locationRequest,locationCallback,null)

        }
    }

    fun when_location_gotten(){
        val last_loc = mLastKnownLocations.get(mLastKnownLocations.lastIndex)
        val ll = LatLng(last_loc.latitude,last_loc.longitude)
        load_my_location_on_map()
    }

    private fun load_my_location_on_map() {
        val last_loc = mLastKnownLocations.get(mLastKnownLocations.lastIndex)

        var lat_lng = LatLng(last_loc.latitude,last_loc.longitude)
        var op = MarkerOptions().position(lat_lng)
        var final_icon: BitmapDrawable?  = getDrawable(R.drawable.my_location_icon) as BitmapDrawable

        val height = 30
        val width = 30
        if(final_icon!=null) {
            val b: Bitmap = final_icon.bitmap
            val smallMarker: Bitmap = Bitmap.createScaledBitmap(b, width, height, false)
            op.icon(BitmapDescriptorFactory.fromBitmap(smallMarker))
        }

        if(my_marker!=null){
            my_marker!!.remove()
        }
        my_marker = mMap.addMarker(op)

        my_marker_trailing_circle?.remove()

        val circleOptions = CircleOptions()
        circleOptions.center(lat_lng)
        circleOptions.radius(300.0)
        circleOptions.fillColor(Color.parseColor("#2271cce7"))
        circleOptions.strokeWidth(0f)

        my_marker_trailing_circle = mMap.addCircle(circleOptions)

    }

    fun move_cam_to_my_location(){
        if(mLastKnownLocations.isNotEmpty()) {
            val last_loc = mLastKnownLocations.get(mLastKnownLocations.lastIndex)
            val ll = LatLng(last_loc.latitude, last_loc.longitude)
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(ll.latitude, ll.longitude), ZOOM))
        }else{
            set_up_getting_my_location()
        }
    }

    override fun onSettingsSwitchNightMode() {

    }

    override fun onSettingsSignIn() {

    }



    fun hideLoadingScreen(){
        is_loading = false
        binding.mapsLoadingScreen.visibility = View.GONE
    }

    fun showLoadingScreen(){
        is_loading = true
        binding.mapsLoadingScreen.visibility = View.VISIBLE
        binding.mapsLoadingScreen.setOnTouchListener { v, _ -> true }

    }

    fun dpToPx(dp: Int): Int {
        return (dp * Resources.getSystem().getDisplayMetrics().density).toInt()
    }


    class session_data(var organisations: ArrayList<organisation>,var routes: ArrayList<route>, var postitions: HashMap<String,ArrayList<driver_pos>>): Serializable

    fun store_session_data(){
        val session = Gson().toJson(session_data(organisations, routes, positions))
        constants.SharedPreferenceManager(applicationContext).store_current_data(session)
    }

    fun set_session_data(){
        val session = constants.SharedPreferenceManager(applicationContext).get_current_data()
        if(!session.equals("")){
            //its not empty
            var session_obj = Gson().fromJson(session,session_data::class.java)
            organisations = session_obj.organisations
            positions = session_obj.postitions
            added_postitions.clear()
            for(item in positions.values){
                for(pos in item){
                    added_postitions.add(pos.pos_id)
                }
            }
            routes = session_obj.routes
        }
    }





    override fun onDestroy() {
        Log.e(TAG,"onDestroy")
        super.onDestroy()
        if (this.mFusedLocationClient != null) {
            mFusedLocationClient.removeLocationUpdates(locationCallback)
        }
        store_session_data()
        remove_driver_liseners()
    }

    override fun onStop() {
        super.onStop()
        Log.e(TAG,"onStop")

    }

    override fun onStart() {
        Log.e(TAG,"onStart")
        super.onStart()
        set_session_data()
    }

    override fun onPause() {
        Log.e(TAG,"onPause")
        super.onPause()
    }

    override fun onResume() {
        Log.e(TAG,"onResume")
        super.onResume()
    }

    private var doubleBackToExitPressedOnce = false
    override fun onBackPressed() {
        if (supportFragmentManager.fragments.size > 1) {
            val trans = supportFragmentManager.beginTransaction()
            trans.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right)
            val currentFragPos = supportFragmentManager.fragments.size - 1

            trans.remove(supportFragmentManager.fragments.get(currentFragPos))
            trans.commit()
            supportFragmentManager.popBackStack()

        } else {
            if (doubleBackToExitPressedOnce) {
                super.onBackPressed()
                return
            }

            this.doubleBackToExitPressedOnce = true
            Toast.makeText(this, "Press BACK again to exit", Toast.LENGTH_SHORT).show()

            Handler().postDelayed(Runnable { doubleBackToExitPressedOnce = false }, 2000)
        }
    }




    val driver_map_markers: HashMap<String, Marker> = HashMap()
    val driver_map_marker_trail: HashMap<String, ArrayList<Circle>> = HashMap()
    fun set_drivers_on_map(driver: String){
        val drivers_last_locations = get_drivers_last_few_locations(positions.get(driver)!!)

        val op = MarkerOptions().position(drivers_last_locations[drivers_last_locations.lastIndex].loc)
        val final_icon: BitmapDrawable?  = getDrawable(R.drawable.bus_loc) as BitmapDrawable

        val height = 108
        val width = 55
        if(final_icon!=null) {
            val b: Bitmap = final_icon.bitmap
            val smallMarker: Bitmap = Bitmap.createScaledBitmap(b, width, height, false)
            op.icon(BitmapDescriptorFactory.fromBitmap(smallMarker))
        }

        if(driver_map_markers.containsKey(driver)){
            driver_map_markers.get(driver)!!.remove()
            driver_map_markers.remove(driver)
        }

        if(driver_map_marker_trail.containsKey(driver)){
            for(circle in driver_map_marker_trail.get(driver)!!){
                circle.remove()
            }
            driver_map_marker_trail.get(driver)!!.clear()
            driver_map_marker_trail.remove(driver)
        }

        val driver_marker = mMap.addMarker(op)
        driver_marker.tag = driver
        driver_map_markers.put(driver,driver_marker)

        for(last_loc in drivers_last_locations){
            val circleOptions = CircleOptions()
            circleOptions.center(last_loc.loc)
            circleOptions.radius(1.0)
            if(constants.SharedPreferenceManager(applicationContext).isDarkModeOn()) {
                circleOptions.fillColor(Color.LTGRAY)
            }else{
                circleOptions.fillColor(Color.GREEN)
            }
            circleOptions.strokeWidth(0f)
            val circle = mMap.addCircle(circleOptions)

            if(!driver_map_marker_trail.containsKey(driver)){
                driver_map_marker_trail.put(driver,ArrayList<Circle>())
            }
            driver_map_marker_trail.get(driver)!!.add(circle)
        }
    }

    fun set_all_drivers(){
        for(driver in positions.keys){
            val drivers_last_locations = get_drivers_last_few_locations(positions.get(driver)!!)

            val op = MarkerOptions().position(drivers_last_locations[drivers_last_locations.lastIndex].loc)
            val final_icon: BitmapDrawable?  = getDrawable(R.drawable.bus_loc) as BitmapDrawable

            val height = 108
            val width = 55
            if(final_icon!=null) {
                val b: Bitmap = final_icon.bitmap
                val smallMarker: Bitmap = Bitmap.createScaledBitmap(b, width, height, false)
                op.icon(BitmapDescriptorFactory.fromBitmap(smallMarker))
            }

            if(driver_map_markers.containsKey(driver)){
                driver_map_markers.get(driver)!!.remove()
                driver_map_markers.remove(driver)
            }

            if(driver_map_marker_trail.containsKey(driver)){
                for(circle in driver_map_marker_trail.get(driver)!!){
                    circle.remove()
                }
                driver_map_marker_trail.get(driver)!!.clear()
                driver_map_marker_trail.remove(driver)
            }

            val driver_marker = mMap.addMarker(op)
            driver_map_markers.put(driver,driver_marker)

            for(last_loc in drivers_last_locations){
                val circleOptions = CircleOptions()
                circleOptions.center(last_loc.loc)
                circleOptions.radius(1.0)
                if(constants.SharedPreferenceManager(applicationContext).isDarkModeOn()) {
                    circleOptions.fillColor(Color.LTGRAY)
                }else{
                    circleOptions.fillColor(Color.GREEN)
                }
                circleOptions.strokeWidth(0f)
                val circle = mMap.addCircle(circleOptions)

                if(!driver_map_marker_trail.containsKey(driver)){
                    driver_map_marker_trail.put(driver,ArrayList<Circle>())
                }
                driver_map_marker_trail.get(driver)!!.add(circle)
            }
        }
    }

    fun get_drivers_last_few_locations(drivers_positions: ArrayList<driver_pos>): ArrayList<driver_pos>{
        val sorted_list: ArrayList<driver_pos> = ArrayList()
        for(item in drivers_positions.sortedWith(compareBy({ it.creation_time }))){
            sorted_list.add(item)
        }

        val last_3_list: ArrayList<driver_pos> = ArrayList()
        last_3_list.addAll(sorted_list.takeLast(3))

        return last_3_list

    }





    fun draw_bus_route(driver: String){
        remove_bus_route()
        val drivers_root = get_drivers_route(driver)
        if(drivers_root!=null) {
            val entire_path: MutableList<List<LatLng>> = ArrayList()
            if (drivers_root.route_directions_data!!.routes.isNotEmpty()) {
                val route = drivers_root.route_directions_data!!.routes[0]
                for (leg in route.legs) {
                    Log.e(TAG, "leg start adress: ${leg.start_address}")
                    for (step in leg.steps) {
                        Log.e(TAG, "step maneuver: ${step.maneuver}")
                        val pathh: List<LatLng> = PolyUtil.decode(step.polyline.points)
                        entire_path.add(pathh)
                    }
                }
            }
            remove_drawn_route()
            draw_route(entire_path)
            add_marker(drivers_root.set_start_pos!!, constants.start_loc, constants.start_loc)
            add_marker(drivers_root.set_end_pos!!, constants.end_loc, constants.end_loc)
            for (item in drivers_root.added_bus_stops) {
                add_marker(item.stop_location, constants.stop_loc, item.creation_time.toString())
            }
            viewed_driver = driver
            Handler().postDelayed({ show_all_markers() }, 500)
        }
    }

    fun remove_bus_route(){
        for(item in added_markers.values){
            item.remove()
        }
        added_markers.clear()
        remove_drawn_route()
        viewed_driver = ""
    }

    val added_markers: HashMap<String,Marker> = HashMap()
    fun add_marker(lat_lng:LatLng, type: String, name: String){
        var op = MarkerOptions().position(lat_lng)
        var final_icon: BitmapDrawable?  = null

        if(type.equals(constants.start_loc)){
            val icon = getDrawable(R.drawable.starting_location_pin) as BitmapDrawable
            final_icon = icon
        }
        else if(type.equals(constants.end_loc)){
            val icon = getDrawable(R.drawable.ending_location_pin) as BitmapDrawable
            final_icon = icon
        }
        else if(type.equals(constants.stop_loc)){
            val icon = getDrawable(R.drawable.stop_location_pin) as BitmapDrawable
            final_icon = icon
        }

        val height = 95
        val width = 35
        if(final_icon!=null) {
            val b: Bitmap = final_icon.bitmap
            val smallMarker: Bitmap = Bitmap.createScaledBitmap(b, width, height, false)
            op.icon(BitmapDescriptorFactory.fromBitmap(smallMarker))
        }

        if(added_markers.containsKey(name)){
            added_markers.get(name)!!.remove()
        }
        val new_marker = mMap.addMarker(op)
        added_markers.put(name, new_marker)
    }

    var drawn_polyline: ArrayList<Polyline> = ArrayList()
    fun draw_route(entire_paths: MutableList<List<LatLng>>){

        for (i in 0 until entire_paths.size) {
            if(constants.SharedPreferenceManager(applicationContext).isDarkModeOn()){
                val op = PolylineOptions()
                    .addAll(entire_paths[i])
                    .width(5f)
                    .color(Color.WHITE)
                drawn_polyline.add(mMap.addPolyline(op))
            }else{
                val op = PolylineOptions()
                    .addAll(entire_paths[i])
                    .width(5f)
                    .color(Color.BLACK)
                drawn_polyline.add(mMap.addPolyline(op))
            }

        }

    }

    fun remove_drawn_route(){
        if(drawn_polyline.isNotEmpty()){
            for(item in drawn_polyline){
                item.remove()
            }
            drawn_polyline.clear()
        }
    }

    fun get_drivers_route(driver_id: String): route?{
        val last_route = get_drivers_last_few_locations(positions.get(driver_id)!!)
        val route = last_route.get(last_route.lastIndex)

        for(item in routes){
            if(route.route_id.equals(item.route_id)){
                return item
            }
        }

        return null
    }

    fun show_all_markers(){
        if(added_markers.values.isNotEmpty()) {
            val builder: LatLngBounds.Builder = LatLngBounds.Builder()
            for (marker in added_markers.values) {
                builder.include(marker.position)
            }
            val bounds = builder.build()
            val padding = dpToPx(120)
            val cu: CameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
            mMap.animateCamera(cu)
        }
    }

    fun set_bus_route_details(driver: String){
        val route = get_drivers_route(driver)
        val orga = get_route_org(get_drivers_route(driver)!!)!!

        binding.title.text = "On Route"
        binding.sourceTextview.text = "From: ${route!!.starting_pos_desc}"
        binding.destinationTextview.text = "To: ${route!!.ending_pos_desc}"
        binding.viewLayout.visibility = View.VISIBLE

        binding.viewLayout.setOnClickListener {
            constants.touch_vibrate(applicationContext)
            val r = Gson().toJson(route)
            val d = Gson().toJson(get_org_driver(orga, driver)!!)
            val o = Gson().toJson(orga)
            supportFragmentManager.beginTransaction().setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left)
                .replace(binding.money.id,ViewRoute.newInstance("","", o, r, d),_view_route).commit()
        }
    }

    fun remove_bus_route_details(){
        binding.title.text = "Click a bus icon"
        binding.sourceTextview.text = "To see the route it's going"
        binding.destinationTextview.text = ""
        binding.viewLayout.visibility = View.GONE
    }

    fun get_route_org(route: route): organisation?{
        for(organ in organisations){
            if(organ.org_id.equals(route.org_id)){
                return organ
            }
        }
        return null
    }

    fun get_org_driver(org: organisation, driver_id: String): driver?{
        for(driv in org.drivers){
            if(driv.driver_id.equals(driver_id)){
                return driv
            }
        }
        return null
    }


    fun when_search_place_result_gotten(place: Place){
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.latLng!!.latitude, place.latLng!!.longitude), mMap.cameraPosition.zoom))
        whenNetworkAvailable()



    }


}