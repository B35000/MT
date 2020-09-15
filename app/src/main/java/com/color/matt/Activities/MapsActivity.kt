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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.color.matt.Constants
import com.color.matt.Fragments.MainSettings
import com.color.matt.Fragments.ViewRoute
import com.color.matt.R
import com.color.matt.Utilities.Apis
import com.color.matt.databinding.ActivityMapsBinding
import com.color.mattdriver.Models.directions_data
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.maps.android.PolyUtil
import java.io.Serializable
import java.math.RoundingMode
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.schedule

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

        remove_bus_route_details()

//        if(!intent.hasExtra(constants.intent_source)){
//            Constants().maintain_theme(applicationContext)
//        }
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
        showLoadingScreen()
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
                        hideLoadingScreen()
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
        val t = Calendar.getInstance().timeInMillis
        for(org in organisations){
            val org_listener = db.collection(constants.organisations)
                .document(org.country!!)
                .collection(constants.country_organisations)
                .document(org.org_id!!)
                .collection(constants.driver_locations)
                .whereGreaterThanOrEqualTo("creation_time", t)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        Log.w(TAG, "listen:error", e)
                        return@addSnapshotListener
                    }

                    for (dc in snapshots!!.documentChanges) {
                        if(dc.type.equals(DocumentChange.Type.ADDED)){
//                            Log.d(TAG, "New location: ${dc.document.data}")
                        }
                        else if(dc.type.equals(DocumentChange.Type.MODIFIED)){
//                            Log.d(TAG, "Modified location: ${dc.document.data}")
                        }
                        else if(dc.type.equals(DocumentChange.Type.REMOVED)){
//                            Log.d(TAG, "Removed location: ${dc.document.data}")
                        }

                        val pos_id = dc.document["pos_id"] as String
                        val creation_time = dc.document["creation_time"] as Long
                        val user = dc.document["user"] as String
                        val loc = Gson().fromJson(dc.document["loc"] as String, LatLng::class.java)
                        val organisation = dc.document["organisation"] as String
                        val route = dc.document["route"] as String

                        val driverPos = driver_pos(pos_id,creation_time,user,loc,organisation,route)
                        Log.e(TAG, "checking if location item is already there")
                        if(!is_location_contained(driverPos) && Calendar.getInstance().timeInMillis-creation_time <= constants.update_limit){
                            put_position_item(driverPos)
                            added_postitions.add(driverPos.pos_id)
                            Log.e(TAG, "its not! adding")
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
    var auto_adjust_to_my_loc = false
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
            auto_adjust_to_my_loc = false
            binding.locateIcon.setImageResource(R.drawable.locate_icon)

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
            remove_multiple_routes()
        }

        binding.searchButton.setOnClickListener {
            Constants().touch_vibrate(applicationContext)
            val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()

            val fields: List<Place.Field> = Arrays.asList(Place.Field.NAME, Place.Field.LAT_LNG)
            val intent: Intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
                .setCountry(user!!.phone.country_name_code)
                .build(this)
            startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
            remove_multiple_routes()
        }

        binding.findMeCardview.setOnLongClickListener {
            if(!auto_adjust_to_my_loc){
                Toast.makeText(applicationContext,"Auto-location on",Toast.LENGTH_SHORT).show()
                auto_adjust_to_my_loc = true
                binding.locateIcon.setImageResource(R.drawable.locate_icon_green)
            }else{
                Toast.makeText(applicationContext,"Auto-location off",Toast.LENGTH_SHORT).show()
                auto_adjust_to_my_loc = false
                binding.locateIcon.setImageResource(R.drawable.locate_icon)
            }

            true
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val place: Place = Autocomplete.getPlaceFromIntent(data!!)
                Log.e("MapActivity", "Place: " + place.name+" and latlng: "+place.latLng!!.latitude)
                hideKeyboard()
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mMap.cameraPosition.target, mMap.cameraPosition.zoom-0.5f))
                if(set_custom_point.equals("starting")){
                    custom_set_start_loc = place.latLng
                    custom_set_start_desc = place.name!!
                    optimum_paths.clear()
                    Handler().postDelayed({
                        if(last_searched_place!=null)when_search_place_result_gotten(last_searched_place!!)
                    }, 50)
                }else if(set_custom_point.equals("ending")){
                    optimum_paths.clear()
                    Handler().postDelayed({
                        when_search_place_result_gotten(place)
                    }, 50)
                }else{
                    custom_set_start_loc = null
                    Handler().postDelayed({
                        optimum_paths.clear()
                        when_search_place_result_gotten(place)
                    }, 50)
                }

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

                    if(!mLastKnownLocations.isEmpty()){
                        val l = mLastKnownLocations.get(mLastKnownLocations.lastIndex)
                        record_view(item.key, LatLng(l.latitude,l.longitude))
                    }else{
                        record_view(item.key, LatLng(0.0,0.0))
                    }
                }else{
                    remove_bus_route(item.key)
                    remove_bus_route_details()

                    if(last_searched_place!=null){
                        when_search_place_result_gotten(last_searched_place!!)
                    }
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
        if(auto_adjust_to_my_loc) {
            Log.e("when_location_gotten","adjusting to my location")
            move_cam_to_my_location()
        }

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
            my_marker!!.position = LatLng(last_loc.latitude, last_loc.longitude)
        }else{
            my_marker = mMap.addMarker(op)
        }

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
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(ll.latitude, ll.longitude), mMap.cameraPosition.zoom))
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
        Constants().maintain_theme(applicationContext)
        if(intent.hasExtra(constants.intent_source)){
        }
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
            if(is_location_picker_open){
                close_location_picker()
            }
            else if(is_showing_multiple_routes){
                remove_multiple_routes()
                binding.findMeCardview.performClick()
            }
            else {
                if (doubleBackToExitPressedOnce) {
                    super.onBackPressed()
                    return
                }
                this.doubleBackToExitPressedOnce = true
                Toast.makeText(this, "Press BACK again to exit", Toast.LENGTH_SHORT).show()
                Handler().postDelayed(Runnable { doubleBackToExitPressedOnce = false }, 2000)
            }
        }
    }




    val driver_map_markers: HashMap<String, Marker> = HashMap()
    val driver_map_marker_trail: HashMap<String, ArrayList<Circle>> = HashMap()
    fun set_drivers_on_map(driver: String){
        val drivers_last_locations = get_drivers_last_few_locations(positions.get(driver)!!)
        val drivers_last_location = drivers_last_locations[drivers_last_locations.lastIndex].loc

        if(driver_map_markers.containsKey(driver)){
            driver_map_markers.get(driver)!!.position = drivers_last_location
//            driver_map_markers.remove(driver)
        }else{
            val op = MarkerOptions().position(drivers_last_location)
            val final_icon: BitmapDrawable?  = getDrawable(R.drawable.bus_loc) as BitmapDrawable

            val height = 108
            val width = 55
            if(final_icon!=null) {
                val b: Bitmap = final_icon.bitmap
                val smallMarker: Bitmap = Bitmap.createScaledBitmap(b, width, height, false)
                op.icon(BitmapDescriptorFactory.fromBitmap(smallMarker))
            }

            val driver_marker = mMap.addMarker(op)
            driver_marker.tag = driver
            driver_map_markers.put(driver,driver_marker)
        }

        if(driver_map_marker_trail.containsKey(driver)){
            for(circle in driver_map_marker_trail.get(driver)!!){
                circle.remove()
            }
            driver_map_marker_trail.get(driver)!!.clear()
            driver_map_marker_trail.remove(driver)
        }


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
        Handler().postDelayed({
            addPulsatingEffect(drivers_last_locations[drivers_last_locations.lastIndex].loc, driver)
        }, 100)

//        if(viewed_driver.equals(driver))move_camera(drivers_last_locations[drivers_last_locations.lastIndex].loc)
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
//        val sorted_list: ArrayList<driver_pos> = ArrayList()
//        for(item in drivers_positions.sortedWith(compareBy({ it.creation_time }))){
//            sorted_list.add(item)
//        }

        val last_3_list: ArrayList<driver_pos> = ArrayList()
        last_3_list.addAll(drivers_positions.takeLast(3))

        return last_3_list

    }





    fun draw_bus_route(driver: String){
        remove_bus_route(viewed_driver)
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
            if(!viewed_driver.equals(""))remove_drawn_route(get_drivers_route(viewed_driver)!!.route_id)
            var c = Color.BLACK
            if(constants.SharedPreferenceManager(applicationContext).isDarkModeOn()){
                c = Color.WHITE
            }
            draw_route(entire_path, c,drivers_root.route_id)
            add_marker(drivers_root.set_start_pos!!, constants.start_loc, constants.start_loc)
            add_marker(drivers_root.set_end_pos!!, constants.end_loc, constants.end_loc)
            for (item in drivers_root.added_bus_stops) {
                add_marker(item.stop_location, constants.stop_loc, item.creation_time.toString())
            }
            viewed_driver = driver
//            Handler().postDelayed({ show_all_markers() }, 500)
        }
    }

    fun remove_bus_route(driver: String){
        for(item in added_markers.values){
            item.remove()
        }
        added_markers.clear()
        if(!driver.equals("")){
//            Toast.makeText(applicationContext, "removing ${get_drivers_route(driver)!!.route_id}",Toast.LENGTH_SHORT).show()
            remove_drawn_route(get_drivers_route(driver)!!.route_id)
//            remove_all_drawn_route()
        }
        viewed_driver = ""
    }

    val added_markers: HashMap<String,Marker> = HashMap()
    fun add_marker(lat_lng:LatLng, type: String, name: String){
        var op = MarkerOptions().position(lat_lng)
        var final_icon: BitmapDrawable?  = null

        var height = 77
        var width = 30

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
            height = 51
            width = 20
            final_icon = icon
        }
        else if(type.equals(constants.specific_route_end)){
            val icon = getDrawable(R.drawable.specific_route_end) as BitmapDrawable
            final_icon = icon
        }

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

    var drawn_polyline: HashMap<String,ArrayList<Polyline>> = HashMap()
    fun draw_route(entire_paths: MutableList<List<LatLng>>, color: Int, route_id: String){
        for (i in 0 until entire_paths.size) {
            if(constants.SharedPreferenceManager(applicationContext).isDarkModeOn()){
                val op = PolylineOptions()
                    .addAll(entire_paths[i])
                    .width(5f)
                    .color(color)
                if(drawn_polyline.containsKey(route_id)){
                    drawn_polyline.get(route_id)!!.add(mMap.addPolyline(op))
                }else{
                    val p = ArrayList<Polyline>()
                    p.add(mMap.addPolyline(op))
                    drawn_polyline.put(route_id,p)
                }
//                drawn_polyline.put(route_id, mMap.addPolyline(op))
            }else{
                val op = PolylineOptions()
                    .addAll(entire_paths[i])
                    .width(5f)
                    .color(color)
                if(drawn_polyline.containsKey(route_id)){
                    drawn_polyline.get(route_id)!!.add(mMap.addPolyline(op))
                }else{
                    val p = ArrayList<Polyline>()
                    p.add(mMap.addPolyline(op))
                    drawn_polyline.put(route_id,p)
                }
//                drawn_polyline.put(route_id, mMap.addPolyline(op))
            }

        }

    }

    fun remove_drawn_route(route_id: String){
        if(drawn_polyline.isNotEmpty() && !route_id.equals("")){
            if(drawn_polyline.containsKey(route_id)){
                for(line in drawn_polyline.get(route_id)!!){
                    line.remove()
                }
                drawn_polyline.remove(route_id)
            }
            for(item in search_place_circle){
                item.remove()
            }
            search_place_circle.clear()
        }
    }

    fun remove_all_drawn_route(){
        for(poly in drawn_polyline.keys){
            val all_lines = drawn_polyline.get(poly)!!
            for(line in all_lines){
                line.remove()
            }
        }

        drawn_polyline.clear()
        for(item in search_place_circle){
            item.remove()
        }
        search_place_circle.clear()
    }

    fun get_drivers_route(driver_id: String): route?{
        if(!driver_id.equals("")) {
            val last_route = get_drivers_last_few_locations(positions.get(driver_id)!!)
            val route = last_route.get(last_route.lastIndex)

            for (item in routes) {
                if (route.route_id.equals(item.route_id)) {
                    return item
                }
            }
        }

        return null
    }

    fun show_all_markers(added_markers: ArrayList<LatLng>){
        if(added_markers.isNotEmpty()) {
            val builder: LatLngBounds.Builder = LatLngBounds.Builder()
            for (marker in added_markers) {
                builder.include(marker)
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
        binding.destinationTextview.text = "To: ${route.ending_pos_desc}"
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

        binding.sourceTextview.setOnClickListener {
//            binding.searchButton.performClick()
        }
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




    var search_place_circle: ArrayList<Circle> = ArrayList()
    var custom_set_start_loc: LatLng? = null
    var custom_set_start_desc = ""
    var is_picking_source_location = false
    var is_location_picker_open = false
    var last_searched_place: Place? = null
    fun when_search_place_result_gotten(place: Place){
//        showLoadingScreen()
        last_searched_place = place
        whenNetworkAvailable()
        remove_bus_route_details()

        binding.title.text = "Searched Route"
        binding.destinationTextview.text = "To: ${place.name}"

        if(custom_set_start_loc!=null){
            binding.sourceTextview.text = "From your set location"
            if(!custom_set_start_desc.equals("")) binding.sourceTextview.text = "From: ${custom_set_start_desc}"
            val user_loc = custom_set_start_loc
            if(optimum_paths.isEmpty())getLocationsRoute(user_loc!!,place.latLng!!, place)
            else calculate_and_show_routes(user_loc!!, place)
        }
        else{
            if(mLastKnownLocations.isNotEmpty()){
                binding.sourceTextview.text = "From your location"
                val user_loc = mLastKnownLocations.get(mLastKnownLocations.lastIndex)
                val user_lat_lg = LatLng(user_loc.latitude,user_loc.longitude)

                if(optimum_paths.isEmpty())getLocationsRoute(user_lat_lg,place.latLng!!, place)
                else calculate_and_show_routes(user_lat_lg,place)
            }
            else{
                binding.sourceTextview.text = "No set place"
                calculate_and_show_routes(place.latLng!!,place)
            }
        }
        binding.setSourceLocation.visibility = View.VISIBLE
        binding.setDestinationLocation.visibility = View.VISIBLE

        binding.setSourceLocation.setOnClickListener {
//            if(!is_picking_source_location) {
//                is_picking_source_location = true
//                binding.setStartLocTextview.text = getString(R.string.done)
//                binding.setStartLocImageview.setImageResource(R.drawable.check_icon)
//                when_back_pressed_from_setting_location = {
//                    is_picking_source_location = false
//                    binding.setStartLocTextview.text = getString(R.string.set)
//                    binding.setStartLocImageview.setImageResource(R.drawable.up_arrow)
//                    when_back_pressed_from_setting_location = {}
//                }
//                open_location_picker()
//            }else{
//                Constants().touch_vibrate(applicationContext)
//                custom_set_start_loc = mMap.getCameraPosition().target
//                close_location_picker()
//                remove_multiple_routes()
//                when_search_place_result_gotten(place)
//            }
            set_new_starting_location()
        }

        binding.setDestinationLocation.setOnClickListener {
            set_new_ending_location()
        }

        binding.sourceTextview.setOnClickListener {
            if(is_showing_multiple_routes){
                binding.setSourceLocation.performClick()
            }
        }

        binding.destinationTextview.setOnClickListener {
            if(is_showing_multiple_routes){
                binding.setDestinationLocation.performClick()
            }
        }

        binding.searchButtonLayout.visibility = View.GONE

        set_custom_point = ""

        var start_loc: LatLng? = null
        if(custom_set_start_loc!=null){
            start_loc = custom_set_start_loc
        } else if(mLastKnownLocations.isNotEmpty()) {
            val user_loc = mLastKnownLocations.get(mLastKnownLocations.lastIndex)
            start_loc = LatLng(user_loc.latitude, user_loc.longitude)
        }

        if(start_loc!=null) {
            var cLat: Double = (start_loc.latitude + place.latLng!!.latitude) / 2
            var cLon: Double = (start_loc.longitude + place.latLng!!.longitude) / 2

            //add skew and arcHeight to move the midPoint

            //add skew and arcHeight to move the midPoint
            val end = place.latLng!!
            val start = start_loc

            if (Math.abs(start.longitude - end.longitude) < 0.0001) {
                cLon -= 0.0195
            } else {
                cLat += 0.0195
            }

            val tDelta = 1.0 / 100
            var t = 0.0
            val alLatLng: ArrayList<LatLng> = ArrayList()
            if(distance_in_meters_to(start,end)>2000) {
                while (t <= 1.0) {
                    val oneMinusT = 1.0 - t
                    val t2 = Math.pow(t, 2.0)
                    val lon: Double =
                        oneMinusT * oneMinusT * start.longitude + 2 * oneMinusT * t * cLon + t2 * end.longitude
                    val lat: Double =
                        oneMinusT * oneMinusT * start.latitude + 2 * oneMinusT * t * cLat + t2 * end.latitude
                    alLatLng.add(LatLng(lat, lon))
                    t += tDelta
                }
            }else{
                alLatLng.add(start)
            }
            alLatLng.add(end)

            // draw polyline

            // draw polyline
            val line = PolylineOptions()
            line.width(5f)
            line.color(Color.DKGRAY)
            if (constants.SharedPreferenceManager(applicationContext).isDarkModeOn()) {
                line.color(Color.LTGRAY)
            }
            line.addAll(alLatLng)
            if (f != null) f!!.remove()
            f = mMap.addPolyline(line)
//            show_all_markers(alLatLng)
        }

        add_marker(place.latLng!!, constants.end_loc, constants.end_loc)
    }

    var set_custom_point = ""
    fun set_new_ending_location(){
        set_custom_point = "ending"
        Constants().touch_vibrate(applicationContext)
        val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()

        val fields: List<Place.Field> = Arrays.asList(Place.Field.NAME, Place.Field.LAT_LNG)
        val intent: Intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .setCountry(user!!.phone.country_name_code)
            .build(this)
        startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)

    }

    fun set_new_starting_location(){
        set_custom_point = "starting"
        Constants().touch_vibrate(applicationContext)
        val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()

        val fields: List<Place.Field> = Arrays.asList(Place.Field.NAME, Place.Field.LAT_LNG)
        val intent: Intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .setCountry(user!!.phone.country_name_code)
            .build(this)
        startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
    }

    var when_back_pressed_from_setting_location: () -> Unit = {}

    fun open_location_picker(){
//        binding.finishCreateRoute.visibility = View.VISIBLE
        binding.setLocationPin.visibility = View.VISIBLE
        binding.searchPlace.visibility = View.GONE
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mMap.cameraPosition.target, mMap.cameraPosition.zoom+1f))
        is_location_picker_open = true

    }

    fun close_location_picker(){
//        binding.finishCreateRoute.visibility = View.GONE
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mMap.cameraPosition.target, mMap.cameraPosition.zoom-1f))
        binding.setLocationPin.visibility = View.GONE
        binding.searchPlace.visibility = View.VISIBLE
        when_back_pressed_from_setting_location()
        is_location_picker_open = false

    }
    





    var last_calc_start_loc: LatLng? = null
    var last_cal_place_loc: Place? = null
    val selected_routes: ArrayList<String> = ArrayList()
    var f: Polyline? = null
    fun calculate_and_show_routes(start_loc: LatLng, place: Place){
        val lat_lng = place.latLng!!
        if(last_calc_start_loc==null
            || !last_calc_start_loc!!.latitude.equals(start_loc.latitude)
            || !last_calc_start_loc!!.longitude.equals(start_loc.longitude)
            || last_cal_place_loc==null
            || !place.name!!.equals(last_cal_place_loc!!.name)){
            selected_routes.clear()
            op_route_pos.clear()
            added_routes_to_op_route_pos.clear()
        }

        var closest_routes:ArrayList<route> = ArrayList()
        var added_routes: ArrayList<String> = ArrayList()
        var closest_to_me_routes:ArrayList<String> = ArrayList()
        for(route in routes){
            if(!route.disabled) {
                if(selected_routes.contains(route.route_id)){
                    closest_routes.add(route)
                    closest_to_me_routes.add(route.route_id)
                    added_routes.add(route.route_id)

                    if(!selected_routes.contains(route.route_id))selected_routes.add(route.route_id)
                }else {
                    if(does_route_pass_optimum_route(route)){
                        closest_routes.add(route)
                        closest_to_me_routes.add(route.route_id)
                        added_routes.add(route.route_id)

                        if(!selected_routes.contains(route.route_id))selected_routes.add(route.route_id)
                    }
                }
            }
        }
        var additional_routes:ArrayList<route> = ArrayList()

        if(additional_routes.isNotEmpty())closest_routes.addAll(additional_routes)
        load_multiple_routes(closest_routes,lat_lng,start_loc, place)

        //        val rad = 300.0
        val rad = constants.search_distance_threshold.toDouble()
        var circleOptions = CircleOptions()
        circleOptions.center(place.latLng!!)
        circleOptions.radius(rad)
        circleOptions.fillColor(Color.parseColor("#2271cce7"))
        circleOptions.strokeWidth(0f)

        search_place_circle.add(mMap.addCircle(circleOptions))

        circleOptions = CircleOptions()
        circleOptions.center(start_loc)
        circleOptions.radius(rad)
        circleOptions.fillColor(Color.parseColor("#2271cce7"))
        circleOptions.strokeWidth(0f)

//        search_place_circle.add(mMap.addCircle(circleOptions))

        add_marker(lat_lng, constants.end_loc, constants.end_loc)
        add_marker(start_loc, constants.start_loc, constants.start_loc)

//        for(path in optimum_paths){
//            val p: MutableList<List<LatLng>> = ArrayList()
//            p.addAll(path)
//            draw_route(p,Color.WHITE,Calendar.getInstance().timeInMillis.toString())
//        }

        last_calc_start_loc = start_loc
        last_cal_place_loc = place

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mMap.cameraPosition.target, mMap.cameraPosition.zoom+0.5f))
    }

    fun get_closest_distance_to_route(set_route :route, my_location:LatLng) : Long {
        val entire_path: MutableList<List<LatLng>> = ArrayList()
        if (set_route.route_directions_data!!.routes.isNotEmpty()) {
            val route = set_route.route_directions_data!!.routes[0]
            for (leg in route.legs) {
                Log.e(TAG, "leg start adress: ${leg.start_address}")
                for (step in leg.steps) {
                    Log.e(TAG, "step maneuver: ${step.maneuver}")
                    val pathh: List<LatLng> = PolyUtil.decode(step.polyline.points)
                    entire_path.add(pathh)
                }
            }
        }

        var closest_point = entire_path[0][0]
        var its_distance_to_me = distance_in_meters_to(closest_point, my_location)
        for(path in entire_path){
            for(point in path){
                if(distance_in_meters_to(point, my_location)<its_distance_to_me){
                    //this point is shorter
                    closest_point = point
                    its_distance_to_me = distance_in_meters_to(point, my_location)
                }
            }
        }

        return its_distance_to_me
    }

    fun distance_in_meters_to(lat_lng: LatLng, other_lat_lng: LatLng): Long{
        val loc1 = Location("")
        loc1.latitude = lat_lng.latitude
        loc1.longitude = lat_lng.longitude

        val loc2 = Location("")
        loc2.latitude = other_lat_lng.latitude
        loc2.longitude = other_lat_lng.longitude

        val distanceInMeters = loc1.distanceTo(loc2)
        return distanceInMeters.toLong()
    }



    var selected_route_objects: ArrayList<route> = ArrayList()
    var selected_op_route = ""
    val search_loaded_route_colors: HashMap<String,Int> = HashMap()
    val search_loaded_routes: ArrayList<route> = ArrayList()
    var item_pos = 0
    internal inner class routesListAdapter(val place: String, val searched_place: Place): RecyclerView.Adapter<ViewHolderRoutes>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolderRoutes {
            val vh = ViewHolderRoutes(LayoutInflater.from(baseContext).inflate(R.layout.recycler_item_direction_route, viewGroup, false))
            return vh
        }

        override fun onBindViewHolder(v: ViewHolderRoutes, position: Int) {
            val item = op_route_pos[place]!!.get(position)
            var item_color = search_loaded_route_colors.get(item.route_id)
            if(!selected_op_route.equals("")){
                if(!selected_op_route.equals(place)){
                    if(constants.SharedPreferenceManager(applicationContext).isDarkModeOn()){
                        item_color = Color.LTGRAY
                    }else{
                        item_color = Color.GRAY
                    }
                }
            }

            var dis = 0
            for(leg in item.route_directions_data!!.routes[0].legs){
                dis+=leg.distance.value
            }

            if(dis>0.0){
                val dis_km = (dis/1000).toBigDecimal().setScale(2, RoundingMode.HALF_EVEN).toDouble().toString()
//                v.drivers_number_card.visibility = View.VISIBLE
                v.drivers_number.text = "${dis_km} km"
            }

            v.route_card.setCardBackgroundColor(item_color!!)

            val op_route: ArrayList<route> = op_route_pos.get(place)!!
            v.loaded_route_relative.setOnClickListener {
                if(!selected_op_route.equals(place)) {
                    item_pos = place.toInt()
                    if(selected_route_objects.isNotEmpty()){
                        remove_drawn_route_pins(selected_route_objects)
                        selected_route_objects.clear()
                    }

                    selected_route_objects.addAll(op_route)
                    selected_op_route = place

                    val item_colors: ArrayList<Int> = ArrayList()
                    for (r in op_route) {
                        item_colors.add(search_loaded_route_colors.get(r.route_id)!!)
                    }

                    draw_specific_routes(selected_route_objects, item_colors)
                    binding.searchedRoutesRecyclerview.adapter!!.notifyDataSetChanged()
//                    binding.searchedRoutesRecyclerview.scrollToPosition(item_pos)
                }
            }

        }

        override fun getItemCount():Int {
            return op_route_pos[place]!!.size
        }

    }

    internal inner class ViewHolderRoutes (view: View) : RecyclerView.ViewHolder(view) {
        val route_card: CardView = view.findViewById(R.id.route_card)
//        val drivers_number_card: CardView = view.findViewById(R.id.drivers_number_card)
        val drivers_number: TextView = view.findViewById(R.id.drivers_number)
        val loaded_route_relative: RelativeLayout = view.findViewById(R.id.loaded_route_relative)
    }



    internal inner class routesListContainerAdapter(val place: Place): RecyclerView.Adapter<ViewHolderRoutesContainer>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolderRoutesContainer {
            val vh = ViewHolderRoutesContainer(LayoutInflater.from(baseContext).inflate(R.layout.recycler_item_direction_route_container, viewGroup, false))
            return vh
        }

        override fun onBindViewHolder(v: ViewHolderRoutesContainer, position: Int) {
            val op_route: ArrayList<route> = op_route_pos.get(position.toString())!!

            v.specific_route_items.adapter = routesListAdapter(position.toString(),place)
            v.specific_route_items.layoutManager = LinearLayoutManager(applicationContext,LinearLayoutManager.HORIZONTAL, false)
            v.specific_route_items.scrollToPosition(item_pos)

            if(!selected_op_route.equals("")){
                if(!selected_op_route.equals(position.toString())){
                    v.route_container.setBackgroundColor(Color.TRANSPARENT)
                }
            }

            v.specific_route_items.setOnClickListener {
//                item_pos = position
//                if(selected_route_objects.isEmpty()){
//                    selected_route_objects.addAll(op_route)
//                    selected_op_route = position.toString()
//
//                    val item_colors: ArrayList<Int> = ArrayList()
//                    for(r in op_route){
//                        item_colors.add(search_loaded_route_colors.get(r.route_id)!!)
//                    }
//
//                    draw_specific_routes(selected_route_objects,item_colors)
//
//                    binding.searchedRoutesRecyclerview.adapter = routesListContainerAdapter(place)
//                    binding.searchedRoutesRecyclerview.layoutManager = LinearLayoutManager(applicationContext,LinearLayoutManager.HORIZONTAL, false)
//                    binding.searchedRoutesRecyclerview.scrollToPosition(item_pos)
//                }else{
//                    selected_route_objects.clear()
//                    selected_op_route = ""
//                    when_search_place_result_gotten(place)
//                }
            }
        }

        override fun getItemCount():Int {
            return op_route_pos.size
        }

    }

    internal inner class ViewHolderRoutesContainer (view: View) : RecyclerView.ViewHolder(view) {
        val route_container: RelativeLayout = view.findViewById(R.id.route_container)
        val specific_route_items: RecyclerView = view.findViewById(R.id.specific_route_items)
    }




    var is_showing_multiple_routes = false
    var open_bus_route = ""
    fun load_multiple_routes(routes: ArrayList<route>, lat_lng: LatLng, my_lat_lng: LatLng, place: Place){
        search_loaded_routes.clear()

        remove_bus_route(viewed_driver)
        remove_all_drawn_route()
        add_marker(lat_lng, constants.end_loc, constants.end_loc)
        add_marker(my_lat_lng, constants.start_loc, constants.start_loc)
        for(drivers_root in routes){
            val entire_path: MutableList<List<LatLng>> = ArrayList()
            if (drivers_root.route_directions_data!!.routes.isNotEmpty()) {
                val route = drivers_root.route_directions_data!!.routes[0]
                for (leg in route.legs) {
                    for (step in leg.steps) {
                        val pathh: List<LatLng> = PolyUtil.decode(step.polyline.points)
                        entire_path.add(pathh)
                    }
                }
            }
            var cc = get_specific_route_color()
            search_loaded_routes.add(drivers_root)
            if(!search_loaded_route_colors.containsKey(drivers_root.route_id)){
                search_loaded_route_colors.put(drivers_root.route_id, cc)
            }else{
                cc = search_loaded_route_colors.get(drivers_root.route_id)!!
            }
//            draw_route(entire_path,cc,drivers_root.route_id)

//            for (item in drivers_root.added_bus_stops) {
//                add_marker(item.stop_location, constants.stop_loc, item.creation_time.toString())
//            }
        }
        Handler().postDelayed({
            val array = ArrayList<LatLng>()
            array.add(lat_lng)
            array.add(my_lat_lng)
            show_all_markers(array)
        }, 50)
        is_showing_multiple_routes = true

        if(op_route_pos.isNotEmpty()) {
            selected_op_route = "0"
            binding.searchedRoutesRecyclerview.visibility = View.VISIBLE
            binding.searchedRoutesRecyclerview.adapter = routesListContainerAdapter(place)
            binding.searchedRoutesRecyclerview.layoutManager =
                LinearLayoutManager(applicationContext, LinearLayoutManager.HORIZONTAL, false)
            binding.searchedRoutesRecyclerview.scrollToPosition(item_pos)

            hideLoadingScreen()

            val op_route: ArrayList<route> = op_route_pos.get(selected_op_route)!!
            selected_route_objects.clear()
            item_pos = 0
            selected_route_objects.addAll(op_route)
            val item_colors: ArrayList<Int> = ArrayList()
            for (r in op_route) {
                item_colors.add(search_loaded_route_colors.get(r.route_id)!!)
            }

            draw_specific_routes(selected_route_objects, item_colors)
        }else{
            binding.searchedRoutesRecyclerview.visibility = View.GONE
        }
    }

    fun remove_multiple_routes(){
        if(is_showing_multiple_routes) {
            is_showing_multiple_routes = false

            for (item in added_markers.values) {
                item.remove()
            }
            added_markers.clear()
            remove_all_drawn_route()

//            if(open_bus_route.equals(""))draw_bus_route(open_bus_route)
            for(item in search_place_circle){
                item.remove()
            }
            search_place_circle.clear()
        }
        binding.setDestinationLocation.visibility = View.GONE
        binding.setSourceLocation.visibility = View.GONE
        binding.searchedRoutesRecyclerview.visibility = View.GONE
        remove_bus_route_details()
        last_searched_place = null
        optimum_paths.clear()
        if(f!=null)f!!.remove()
        custom_set_start_desc = ""
        binding.searchButtonLayout.visibility = View.VISIBLE
    }

    fun draw_specific_routes(routes_to_draw: ArrayList<route>, colors: ArrayList<Int>){
//        remove_bus_route(viewed_driver)
        remove_all_drawn_route()

        for(route_to_draw in routes_to_draw){
            val color = colors.get(routes_to_draw.indexOf(route_to_draw))


            val entire_path: MutableList<List<LatLng>> = ArrayList()
            if (route_to_draw.route_directions_data!!.routes.isNotEmpty()) {
                val route = route_to_draw.route_directions_data!!.routes[0]
                for (leg in route.legs) {
                    Log.e(TAG, "leg start adress: ${leg.start_address}")
                    for (step in leg.steps) {
                        Log.e(TAG, "step maneuver: ${step.maneuver}")
                        val pathh: List<LatLng> = PolyUtil.decode(step.polyline.points)
                        entire_path.add(pathh)
                    }
                }
            }
            add_marker(entire_path.get(entire_path.lastIndex).get(entire_path.get(entire_path.lastIndex).lastIndex), constants.specific_route_end, route_to_draw.route_id)
            draw_route(entire_path,color,route_to_draw.route_id)

            for (item in route_to_draw.added_bus_stops) {
                add_marker(item.stop_location, constants.stop_loc, item.creation_time.toString())
            }
        }
    }

    fun remove_drawn_route_pins(routes_to_draw: ArrayList<route>){
        for(item in routes_to_draw){
            if(added_markers.containsKey(item.route_id)){
                added_markers.get(item.route_id)!!.remove()
                added_markers.remove(item.route_id)
            }
            for(stop in item.added_bus_stops){
                if(added_markers.containsKey(stop.creation_time.toString())){
                    added_markers.get(stop.creation_time.toString())!!.remove()
                    added_markers.remove(stop.creation_time.toString())
                }
            }

        }

    }




    protected fun getDisplayPulseRadius(radius: Float): Float {
        val diff: Float = mMap.getMaxZoomLevel() - mMap.getCameraPosition().zoom
        if (diff < 3) return radius
        if (diff < 3.7) return radius * (diff / 2)
        if (diff < 4.5) return radius * diff
        if (diff < 5.5) return radius * diff * 1.5f
        if (diff < 7) return radius * diff * 2f
        if (diff < 7.8) return radius * diff * 3.5f
        if (diff < 8.5) return (radius * diff) * 5
        if (diff < 10) return radius * diff * 10f
        if (diff < 12) return radius * diff * 18f
        if (diff < 13) return radius * diff * 28f
        if (diff < 16) return radius * diff * 40f
        return if (diff < 18) radius * diff * 60 else radius * diff * 80
    }

    private var lastUserCircleList: HashMap<String,Circle> = HashMap()
    private val pulseDuration: Long = 2000
    private var lastPulseAnimatorList: HashMap<String,ValueAnimator> = HashMap()
    var rad = 200f
    private fun addPulsatingEffect(userLatlng: LatLng, driver: String) {
        if (lastPulseAnimatorList.containsKey(driver)) {
            lastPulseAnimatorList.get(driver)!!.cancel()
        }
        if (lastUserCircleList.containsKey(driver)) lastUserCircleList.get(driver)!!.center = userLatlng
        var lastPulse = valueAnimate(ValueAnimator.AnimatorUpdateListener { animation ->
            if (lastUserCircleList.containsKey(driver)) {
                lastUserCircleList.get(driver)!!.setRadius((animation.getAnimatedValue() as Float).toDouble())
                var col = Color.parseColor("#2271cce7")
                if (constants.SharedPreferenceManager(applicationContext).isDarkModeOn()) {
                    col = Color.GRAY
                }
                lastUserCircleList.get(driver)!!.fillColor = adjustAlpha(col, (rad - (animation.getAnimatedValue() as Float)) / rad)
            } else {
                var col = Color.parseColor("#2271cce7")
                if (constants.SharedPreferenceManager(applicationContext).isDarkModeOn()) {
                    col = Color.GRAY
                }
                var lastCircle = mMap.addCircle(CircleOptions()
                        .center(userLatlng)
                        .radius((animation.getAnimatedValue() as Float).toDouble())
                        .fillColor(col)
                        .strokeWidth(0f)
                )
                if(lastUserCircleList.containsKey(driver)){
                    lastUserCircleList.remove(driver)
                }
                lastUserCircleList.put(driver,lastCircle)
            }
        })
        if(lastPulseAnimatorList.containsKey(driver)){
            lastPulseAnimatorList.remove(driver)
        }
        lastPulseAnimatorList.put(driver,lastPulse!!)
    }

    fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Math.round(Color.alpha(color) * factor)
//        Log.e("adjustAlpha","adjusted alpha is ${alpha}")
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }

    protected fun valueAnimate( updateListener: ValueAnimator.AnimatorUpdateListener?): ValueAnimator? {
//        Log.d("valueAnimate: ", "called")
        val va = ValueAnimator.ofFloat(0f, rad)
        va.duration = pulseDuration
        va.addUpdateListener(updateListener)
        va.interpolator = LinearOutSlowInInterpolator()
        va.start()
        return va
    }




    fun record_view(driver: String, location: LatLng){
        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        val route = get_drivers_route(driver)!!
        val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()!!

        val ref = db.collection(constants.organisations)
            .document(user.phone.country_name)
            .collection(constants.country_routes)
            .document(route.route_id)
            .collection(constants.views)
            .document()

        val doc = hashMapOf(
            "creation_time" to Calendar.getInstance().timeInMillis,
            "driver_id" to driver,
            "route" to route.route_id,
            "view_id" to ref.id,
            "viewer_id" to uid,
            "viewer_lat_lng" to Gson().toJson(location)
        )

        ref.set(doc).addOnSuccessListener {
            whenNetworkAvailable()
            Log.e(TAG,"Recorded view!")
        }.addOnFailureListener {
            whenNetworkLost()
        }

    }

    fun Activity.hideKeyboard() {
        hideKeyboard(currentFocus ?: View(this))
    }

    fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    val optimum_paths: ArrayList<List<List<LatLng>>> = ArrayList()
    fun getLocationsRoute(source: LatLng, destination: LatLng, place: Place){
        showLoadingScreen()
        var https = "https://maps.googleapis.com/maps/api/directions/json?origin=${source.latitude},${source.longitude}&alternatives=true&destination=${destination.latitude},${destination.longitude}&key=${Apis().directions_api}"

        if(distance_in_meters_to(source,destination)>=(30*1000)){
            https = "https://maps.googleapis.com/maps/api/directions/json?origin=${source.latitude},${source.longitude}&alternatives=false&destination=${destination.latitude},${destination.longitude}&key=${Apis().directions_api}"
        }
        val queue = Volley.newRequestQueue(this)
        val stringRequest = JsonObjectRequest(Request.Method.GET, https,null, Response.Listener
        { response ->
            val directionsData = Gson().fromJson(response.toString(), directions_data::class.java)
            Log.e("MapsActivity", "Data parsed: "+directionsData.status)

            optimum_paths.clear()
            if(directionsData.routes.isNotEmpty()){
                for(route in directionsData.routes) {
                    val entire_path: MutableList<List<LatLng>> = ArrayList()
                    for (leg in route.legs) {
                        for (step in leg.steps) {
                            val pathh: List<LatLng> = PolyUtil.decode(step.polyline.points)
                            entire_path.add(pathh)
                        }
                    }
                    optimum_paths.add(entire_path)
                }
            }
            calculate_and_show_routes(source,place)
            hideLoadingScreen()
        }, Response.ErrorListener {
            Log.e("MapsActivity","It didnt work! "+it.message.toString())
            whenNetworkLost()
        })

        queue.add(stringRequest)

    }




    var op_route_pos: HashMap<String, ArrayList<route>> = HashMap()
    var added_routes_to_op_route_pos: HashMap<String, ArrayList<String>> = HashMap()
    fun does_route_pass_optimum_route(route: route): Boolean{
        if(optimum_paths.isNotEmpty()){
            val matching_legs: ArrayList<List<LatLng>> = ArrayList()
            for(optimum_route in optimum_paths){
                val path_pos = optimum_paths.indexOf(optimum_route)
                for(leg in optimum_route){
                    for(pot_route_leg in route.route_directions_data!!.routes[0].legs){
                        for (step in pot_route_leg.steps) {
                            val pathh: List<LatLng> = PolyUtil.decode(step.polyline.points)
                            if(do_legs_match_direction(leg,pathh)){
                                if(op_route_pos.containsKey(path_pos.toString())){
                                    if(!added_routes_to_op_route_pos.get(path_pos.toString())!!.contains(route.route_id)){
                                        added_routes_to_op_route_pos.get(path_pos.toString())!!.add(route.route_id)
                                        op_route_pos.get(path_pos.toString())!!.add(route)
                                    }
                                }else{
                                    val list = ArrayList<route>()
                                    val id_list = ArrayList<String>()
                                    list.add(route)
                                    id_list.add(route.route_id)
                                    added_routes_to_op_route_pos.put(path_pos.toString(),id_list)
                                    op_route_pos.put(path_pos.toString(), list)
                                }
                                matching_legs.add(leg)
//                                return true
                            }
                        }
                    }
                }
            }

            if(matching_legs.size>=1){
                return true
            }
        }
        return false
    }

    fun do_legs_match_direction(optimum_route_leg: List<LatLng>, route_leg: List<LatLng>): Boolean{
        val close_items = ArrayList<LatLng>()
        for(item in optimum_route_leg){
            val item_pos = optimum_route_leg.indexOf(item)
            if(route_leg.lastIndex > item_pos){
                val route_leg_item = route_leg.get(item_pos)

                if(distance_in_meters_to(item,route_leg_item) <= constants.route_leg_distance_threshold){
                    close_items.add(route_leg_item)
                }
            }
        }

        if(close_items.size >= 3) return true

        return false
    }

    fun get_specific_route_color(): Int{
        val random = Random()
        if(constants.SharedPreferenceManager(applicationContext).isDarkModeOn()){
            var r = (random.nextInt(205)+50)
            var g = (random.nextInt(205)+50)
            var b = (random.nextInt(205)+50)
            return  Color.argb(255, r, g, b)
        }else{
            var r = (random.nextInt(305)-60)
            var g = (random.nextInt(305)-60)
            var b = (random.nextInt(305)-60)
            return Color.argb(255, r, g, b)
        }
    }


}