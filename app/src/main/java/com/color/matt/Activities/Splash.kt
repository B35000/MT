package com.color.matt.Activities

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.color.matt.Constants
import com.color.matt.R
import com.color.matt.Utilities.GpsUtils
import com.color.matt.databinding.ActivitySplashBinding
import com.color.mattdriver.Models.driver
import com.color.mattdriver.Models.number
import com.color.mattdriver.Models.organisation
import com.color.mattdriver.Models.route
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.hbb20.CountryCodePicker
import java.util.*

class Splash : AppCompatActivity() {
    val TAG = "SplashScreen"
    private lateinit var binding: ActivitySplashBinding
    var ACCESS_FINE_LOCATION_CODE = 3310
    private var mAuth: FirebaseAuth? = null
    val constants = Constants()
    var organisations: ArrayList<organisation> = ArrayList()
    var positions: HashMap<String,ArrayList<MapsActivity.driver_pos>> = HashMap()
    var routes: ArrayList<route> = ArrayList()
    val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
//        constants.SharedPreferenceManager(applicationContext).store_current_data("")

        mAuth = FirebaseAuth.getInstance()
        GpsUtils(this).turnGPSOn(object : GpsUtils.onGpsListener {
            override fun gpsStatus(isGPSEnable: Boolean) {
                openMap()
            }
        })
        Constants().maintain_theme(applicationContext)
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == ACCESS_FINE_LOCATION_CODE) {
                openMap() // flag maintain before get location
            }
        }
    }

    fun openMap(){
        if(constants.SharedPreferenceManager(applicationContext).getPersonalInfo()!=null){
            if(constants.SharedPreferenceManager(applicationContext).get_current_data().equals("")){
                //theres no data, we nee to load stuff first
                load_organisations()
            }else {
                val intent = Intent(this, MapsActivity::class.java)
                intent.putExtra(constants.intent_source, constants.splashActivity)
                startActivity(intent)
                overridePendingTransition(0, 0)
                finish()
            }
        }else{
            startAnonymousSignUp()
        }

    }

    fun startAnonymousSignUp(){
        if(isOnline()) {
            mAuth!!.signInAnonymously().addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInAnonymously:success")
                    val user = mAuth!!.currentUser

                    val tm: TelephonyManager = applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                    val locale: String = tm.getNetworkCountryIso()
                    val ccp = CountryCodePicker(applicationContext)
                    ccp.setDefaultCountryUsingNameCode(locale)
                    ccp.setAutoDetectedCountry(true)

                    val the_number = number(0,
                        ccp.selectedCountryCodeWithPlus,
                        ccp.selectedCountryName,
                        ccp.selectedCountryNameCode
                    )
                    constants.SharedPreferenceManager(applicationContext)
                        .setPersonalInfo(the_number,constants.unknown_email,"", Calendar.getInstance().timeInMillis,user!!.uid)
                    openMap()
                } else {
                    // If sign in fails, display a message to the user.
                    Log.e(TAG, "signInAnonymously:failure", task.exception)
                }
            }
        }else{

        }
    }

    fun isOnline(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val netInfo = cm.activeNetworkInfo
        //should check null because in airplane mode it will be null
        return netInfo != null && netInfo.isConnected
    }

    fun load_organisations(){
        val user = constants.SharedPreferenceManager(applicationContext).getPersonalInfo()!!
        organisations.clear()
        db.collection(constants.organisations)
            .document(user.phone.country_name)
            .collection(constants.country_organisations)
            .get().addOnSuccessListener {
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
                            for(item in organisations){
                                if(item.org_id.equals(org_id)){
                                    item.drivers.add(driver)
                                }
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
        routes.clear()
        db.collection(constants.organisations)
            .document(user.phone.country_name)
            .collection(constants.country_routes)
            .get().addOnSuccessListener {
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
                openMap()
            }
    }

    fun store_session_data(){
        val session = Gson().toJson(
            MapsActivity.session_data(organisations, routes, positions)
        )
        constants.SharedPreferenceManager(applicationContext).store_current_data(session)
    }



}