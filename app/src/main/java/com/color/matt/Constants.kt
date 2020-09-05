package com.color.matt

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.net.ConnectivityManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.TypedValue
import androidx.appcompat.app.AppCompatDelegate
import com.google.gson.Gson
import com.color.mattdriver.Models.number
import com.google.gson.reflect.TypeToken
import java.io.Serializable
import java.util.*

class Constants {
    val vib_time: Long = 2
    val coll_users = "users"
    val dark_mode = "dark_mode"
    val first_time_launch = "first_time_launch"
    val unknown_email = "unknown_email"
    val organisations = "organisations"
    val country_organisations = "country_organisations"
    val otp_codes = "otp_codes"
    val code_instances = "code_instances"
    val otp_expiration_time: Long = 1000*60
    val my_organisations = "my_organisations"
    val session_data = "session_data"
    val start_loc = "start_loc"
    val end_loc = "end_loc"
    val stop_loc = "stop_loc"
    val routes = "routes"
    val country_routes = "country_routes"
    val distance_threshold = 69
    val driver_locations = "driver_locations"
    val update_interval: Long = (4*1000)
    val coll_meta_data = "coll_meta_data"
    val doc_phone = "doc_phone"
    val pass = "gHH5SGcFemdzqHmNCbjy3AHWiun1"
    val drivers = "drivers"

    fun touch_vibrate(context: Context?){
        val vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(Constants().vib_time, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(vib_time)
        }
    }

    fun getCurrency(country_code: String):String{
        val retMap: Map<String, String> = Gson().fromJson(
            "{\"BD\": \"BDT\", \"BE\": \"EUR\", \"BF\": \"XOF\", \"BG\": \"BGN\", \"BA\": \"BAM\", \"BB\": \"BBD\", \"WF\": \"XPF\", \"BL\": \"EUR\", \"BM\": \"BMD\", \"BN\": \"BND\", \"BO\": \"BOB\", \"BH\": \"BHD\", \"BI\": \"BIF\", \"BJ\": \"XOF\", \"BT\": \"BTN\", \"JM\": \"JMD\", \"BV\": \"NOK\", \"BW\": \"BWP\", \"WS\": \"WST\", \"BQ\": \"USD\", \"BR\": \"BRL\", \"BS\": \"BSD\", \"JE\": \"GBP\", \"BY\": \"BYR\", \"BZ\": \"BZD\", \"RU\": \"RUB\", \"RW\": \"RWF\", \"RS\": \"RSD\", \"TL\": \"USD\", \"RE\": \"EUR\", \"TM\": \"TMT\", \"TJ\": \"TJS\", \"RO\": \"RON\", \"TK\": \"NZD\", \"GW\": \"XOF\", \"GU\": \"USD\", \"GT\": \"GTQ\", \"GS\": \"GBP\", \"GR\": \"EUR\", \"GQ\": \"XAF\", \"GP\": \"EUR\", \"JP\": \"JPY\", \"GY\": \"GYD\", \"GG\": \"GBP\", \"GF\": \"EUR\", \"GE\": \"GEL\", \"GD\": \"XCD\", \"GB\": \"GBP\", \"GA\": \"XAF\", \"SV\": \"USD\", \"GN\": \"GNF\", \"GM\": \"GMD\", \"GL\": \"DKK\", \"GI\": \"GIP\", \"GH\": \"GHS\", \"OM\": \"OMR\", \"TN\": \"TND\", \"JO\": \"JOD\", \"HR\": \"HRK\", \"HT\": \"HTG\", \"HU\": \"HUF\", \"HK\": \"HKD\", \"HN\": \"HNL\", \"HM\": \"AUD\", \"VE\": \"VEF\", \"PR\": \"USD\", \"PS\": \"ILS\", \"PW\": \"USD\", \"PT\": \"EUR\", \"SJ\": \"NOK\", \"PY\": \"PYG\", \"IQ\": \"IQD\", \"PA\": \"PAB\", \"PF\": \"XPF\", \"PG\": \"PGK\", \"PE\": \"PEN\", \"PK\": \"PKR\", \"PH\": \"PHP\", \"PN\": \"NZD\", \"PL\": \"PLN\", \"PM\": \"EUR\", \"ZM\": \"ZMK\", \"EH\": \"MAD\", \"EE\": \"EUR\", \"EG\": \"EGP\", \"ZA\": \"ZAR\", \"EC\": \"USD\", \"IT\": \"EUR\", \"VN\": \"VND\", \"SB\": \"SBD\", \"ET\": \"ETB\", \"SO\": \"SOS\", \"ZW\": \"ZWL\", \"SA\": \"SAR\", \"ES\": \"EUR\", \"ER\": \"ERN\", \"ME\": \"EUR\", \"MD\": \"MDL\", \"MG\": \"MGA\", \"MF\": \"EUR\", \"MA\": \"MAD\", \"MC\": \"EUR\", \"UZ\": \"UZS\", \"MM\": \"MMK\", \"ML\": \"XOF\", \"MO\": \"MOP\", \"MN\": \"MNT\", \"MH\": \"USD\", \"MK\": \"MKD\", \"MU\": \"MUR\", \"MT\": \"EUR\", \"MW\": \"MWK\", \"MV\": \"MVR\", \"MQ\": \"EUR\", \"MP\": \"USD\", \"MS\": \"XCD\", \"MR\": \"MRO\", \"IM\": \"GBP\", \"UG\": \"UGX\", \"TZ\": \"TZS\", \"MY\": \"MYR\", \"MX\": \"MXN\", \"IL\": \"ILS\", \"FR\": \"EUR\", \"IO\": \"USD\", \"SH\": \"SHP\", \"FI\": \"EUR\", \"FJ\": \"FJD\", \"FK\": \"FKP\", \"FM\": \"USD\", \"FO\": \"DKK\", \"NI\": \"NIO\", \"NL\": \"EUR\", \"NO\": \"NOK\", \"NA\": \"NAD\", \"VU\": \"VUV\", \"NC\": \"XPF\", \"NE\": \"XOF\", \"NF\": \"AUD\", \"NG\": \"NGN\", \"NZ\": \"NZD\", \"NP\": \"NPR\", \"NR\": \"AUD\", \"NU\": \"NZD\", \"CK\": \"NZD\", \"XK\": \"EUR\", \"CI\": \"XOF\", \"CH\": \"CHF\", \"CO\": \"COP\", \"CN\": \"CNY\", \"CM\": \"XAF\", \"CL\": \"CLP\", \"CC\": \"AUD\", \"CA\": \"CAD\", \"CG\": \"XAF\", \"CF\": \"XAF\", \"CD\": \"CDF\", \"CZ\": \"CZK\", \"CY\": \"EUR\", \"CX\": \"AUD\", \"CR\": \"CRC\", \"CW\": \"ANG\", \"CV\": \"CVE\", \"CU\": \"CUP\", \"SZ\": \"SZL\", \"SY\": \"SYP\", \"SX\": \"ANG\", \"KG\": \"KGS\", \"KE\": \"KES\", \"SS\": \"SSP\", \"SR\": \"SRD\", \"KI\": \"AUD\", \"KH\": \"KHR\", \"KN\": \"XCD\", \"KM\": \"KMF\", \"ST\": \"STD\", \"SK\": \"EUR\", \"KR\": \"KRW\", \"SI\": \"EUR\", \"KP\": \"KPW\", \"KW\": \"KWD\", \"SN\": \"XOF\", \"SM\": \"EUR\", \"SL\": \"SLL\", \"SC\": \"SCR\", \"KZ\": \"KZT\", \"KY\": \"KYD\", \"SG\": \"SGD\", \"SE\": \"SEK\", \"SD\": \"SDG\", \"DO\": \"DOP\", \"DM\": \"XCD\", \"DJ\": \"DJF\", \"DK\": \"DKK\", \"VG\": \"USD\", \"DE\": \"EUR\", \"YE\": \"YER\", \"DZ\": \"DZD\", \"US\": \"USD\", \"UY\": \"UYU\", \"YT\": \"EUR\", \"UM\": \"USD\", \"LB\": \"LBP\", \"LC\": \"XCD\", \"LA\": \"LAK\", \"TV\": \"AUD\", \"TW\": \"TWD\", \"TT\": \"TTD\", \"TR\": \"TRY\", \"LK\": \"LKR\", \"LI\": \"CHF\", \"LV\": \"EUR\", \"TO\": \"TOP\", \"LT\": \"LTL\", \"LU\": \"EUR\", \"LR\": \"LRD\", \"LS\": \"LSL\", \"TH\": \"THB\", \"TF\": \"EUR\", \"TG\": \"XOF\", \"TD\": \"XAF\", \"TC\": \"USD\", \"LY\": \"LYD\", \"VA\": \"EUR\", \"VC\": \"XCD\", \"AE\": \"AED\", \"AD\": \"EUR\", \"AG\": \"XCD\", \"AF\": \"AFN\", \"AI\": \"XCD\", \"VI\": \"USD\", \"IS\": \"ISK\", \"IR\": \"IRR\", \"AM\": \"AMD\", \"AL\": \"ALL\", \"AO\": \"AOA\", \"AQ\": \"\", \"AS\": \"USD\", \"AR\": \"ARS\", \"AU\": \"AUD\", \"AT\": \"EUR\", \"AW\": \"AWG\", \"IN\": \"INR\", \"AX\": \"EUR\", \"AZ\": \"AZN\", \"IE\": \"EUR\", \"ID\": \"IDR\", \"UA\": \"UAH\", \"QA\": \"QAR\", \"MZ\": \"MZN\"}",
            object : TypeToken<HashMap<String?, String?>?>() {}.type
        )

        Log.e("SignUpPhone",retMap.get("KE").toString())
        return retMap.get(country_code).toString()
    }/*gets a country's currency code from its country code*/

    fun construct_elapsed_time(time: Long): String{
        val a_year = 1000L*60L*60L*24L*365L
        val a_month = 1000L*60L*60L*24L*30L
        val a_week = 1000L*60L*60L*24L*7L
        val a_day = 1000L*60L*60L*24L
        val an_hour = 1000L*60L*60L
        val a_minute = 1000L*60L
        val a_second = 1000L

        if(time>=a_year){
            //time is greater than a year, we will parse the time in years
            val time_in_years = (time.toDouble()/a_year.toDouble()).toInt()
            var t = "yrs."
            if(time_in_years==1) t = "yr."
            return time_in_years.toString()+t
        }
        else if(time>=a_month){
            //time is greater than a month, we will parse the time in months
            val time_in_months = (time.toDouble()/a_month.toDouble()).toInt()
            var t = "mo."
            if(time_in_months==1) t = "mo."
            return time_in_months.toString()+t
        }
        else if(time>=a_week){
            //time is greater than a week, we will parse the time in week
            val time_in_weeks = (time.toDouble()/a_week.toDouble()).toInt()
            var t = "wks."
            if(time_in_weeks==1) t = "wk."
            return time_in_weeks.toString()+t
        }
        else if(time>=a_day){
            //time is greater than a day, we will parse the time in day
            val time_in_days = (time.toDouble()/a_day.toDouble()).toInt()
            var t = "d."
            if(time_in_days==1) t = "d."
            return time_in_days.toString()+t
        }
        else if(time>=an_hour){
            //time is greater than an hour, we will parse the time in hours
            val time_in_hours = (time.toDouble()/an_hour.toDouble()).toInt()
            var t = "hrs."
            if(time_in_hours==1) t = "hr."
            return time_in_hours.toString()+ t
        }
        else if(time>=a_minute){
            //time is greater than a minute, we will parse the time in minutes
            val time_in_minutes = (time.toDouble()/a_minute.toDouble()).toInt()
            var t = "min."
            if(time_in_minutes==1) t = "min."
            return time_in_minutes.toString()+ t
        }
        else{
            val time_in_seconds = (time.toDouble()/a_second.toDouble()).toInt()
            var t = "sec."
            if(time_in_seconds==1) t = "sec."
            return time_in_seconds.toString()+t
        }

    }

    fun get_formatted_time(time: Long): String{
        val cal = Calendar.getInstance()
        cal.timeInMillis = time

        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
        val year = cal.get(Calendar.YEAR)

        return "${day} ${month}, ${year}"

    }

    inner class SharedPreferenceManager(val applicationContext: Context){
        fun setPersonalInfo(phone: number, email: String, name: String, sign_up_time: Long, uid: String){
            val user = user(phone,email,name, sign_up_time,uid)
            val pref: SharedPreferences = applicationContext.getSharedPreferences(coll_users, Context.MODE_PRIVATE)
            pref.edit().clear().putString(coll_users, Gson().toJson(user)).apply()
        }

        fun setPerson(usr: user){
            val pref: SharedPreferences = applicationContext.getSharedPreferences(coll_users, Context.MODE_PRIVATE)
            pref.edit().clear().putString(coll_users, Gson().toJson(usr)).apply()
        }

        fun isFirstTimeLaunch(): Boolean{
            val pref: SharedPreferences = applicationContext.getSharedPreferences(first_time_launch, Context.MODE_PRIVATE)
            val va = pref.getBoolean(first_time_launch, true)

            return va
        }

        fun setFirstTimeLaunch(value: Boolean){
            val pref: SharedPreferences = applicationContext.getSharedPreferences(first_time_launch, Context.MODE_PRIVATE)
            pref.edit().putBoolean(first_time_launch,value).apply()
        }

        fun getPersonalInfo(): user?{
            val pref: SharedPreferences = applicationContext.getSharedPreferences(coll_users, Context.MODE_PRIVATE)
            val user_str = pref.getString(coll_users, "")

            if(user_str==""){
                return null
            }else{
                return Gson().fromJson(user_str, user::class.java)
            }
        }

        fun isDarkModeOn(): Boolean{
            val pref: SharedPreferences = applicationContext.getSharedPreferences(dark_mode, Context.MODE_PRIVATE)
            return pref.getBoolean(dark_mode, false)
        }

        fun setDarkMode(is_dark_on: Boolean){
            val pref: SharedPreferences = applicationContext.getSharedPreferences(dark_mode, Context.MODE_PRIVATE)
            pref.edit().putBoolean(dark_mode, is_dark_on).apply()
        }

        fun store_current_data(data: String){
            val pref: SharedPreferences = applicationContext.getSharedPreferences(session_data, Context.MODE_PRIVATE)
            pref.edit().clear().putString(session_data, data).apply()
        }

        fun get_current_data(): String{
            val pref: SharedPreferences = applicationContext.getSharedPreferences(session_data, Context.MODE_PRIVATE)
            return pref.getString(session_data,"")!!
        }

    }

    inner class user(var phone: number, val email: String, var name: String, val sign_up_time: Long, val uid: String): Serializable


    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val netInfo = cm.activeNetworkInfo
        //should check null because in airplane mode it will be null
        return netInfo != null && netInfo.isConnected
    }

    fun dark_mode(context: Context){
        if(!SharedPreferenceManager(context).isDarkModeOn()){
            setGucciTheme(AppCompatDelegate.MODE_NIGHT_YES)
            SharedPreferenceManager(context).setDarkMode(true)
        }else{
            setGucciTheme(AppCompatDelegate.MODE_NIGHT_NO)
            SharedPreferenceManager(context).setDarkMode(false)
        }
    }

    fun maintain_theme(context: Context){
        if(SharedPreferenceManager(context).isDarkModeOn()){
            setGucciTheme(AppCompatDelegate.MODE_NIGHT_YES)
            SharedPreferenceManager(context).setDarkMode(true)
        }else{
            setGucciTheme(AppCompatDelegate.MODE_NIGHT_NO)
            SharedPreferenceManager(context).setDarkMode(false)
        }
    }

    fun setGucciTheme(mode: Int) {
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun dp_to_px(dip: Float, context: Context): Float {
        val r: Resources = context.getResources()
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dip, r.getDisplayMetrics())
    }




}