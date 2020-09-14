package com.color.matt.Fragments

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Switch
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.color.matt.Constants
import com.color.matt.R
import com.google.common.reflect.Reflection.getPackageName


class MainSettings : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private val ARG_PARAM1 = "param1"
    private val ARG_PARAM2 = "param2"
    lateinit var listener: MainSettingsInterface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if(context is MainSettingsInterface){
            listener = context
        }
    }

    var didUserSignUp: () -> Unit = {}

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val va = inflater.inflate(R.layout.fragment_main_settings, container, false)
        val night_mode_switch: Switch = va.findViewById(R.id.night_mode_switch)
        val dark_mode_text: TextView = va.findViewById(R.id.dark_mode_text)
        val join_organisation_layout: RelativeLayout = va.findViewById(R.id.join_organisation_layout)
        val sign_in_layout: RelativeLayout = va.findViewById(R.id.sign_in_layout)
        val sign_in_view: RelativeLayout = va.findViewById(R.id.sign_in_view)

        val money: RelativeLayout = va.findViewById(R.id.money)
        money.setOnTouchListener { v, event -> true }

        if(Constants().SharedPreferenceManager(context!!).isDarkModeOn()){
            dark_mode_text.text = "Turn off Dark Mode"
        }
        night_mode_switch.isChecked = Constants().SharedPreferenceManager(context!!).isDarkModeOn()

        night_mode_switch.setOnCheckedChangeListener { buttonView, isChecked ->
            Constants().dark_mode(context!!)
            if(isChecked) dark_mode_text.text = "Turn off Dark Mode"
            else dark_mode_text.text = "Turn on Dark Mode"
        }

        join_organisation_layout.setOnClickListener {
            Constants().touch_vibrate(context!!)
        }

        sign_in_layout.setOnClickListener {
            listener.onSettingsSignIn()
            Constants().touch_vibrate(context!!)
        }

        if(Constants().SharedPreferenceManager(context!!).getPersonalInfo()!!.email.equals(Constants().unknown_email)){
//            sign_in_view.visibility = View.VISIBLE
        }

        didUserSignUp = {
            sign_in_view.visibility = View.GONE
        }

        val app_vid: TextView = va.findViewById(R.id.app_vid)
        try {
            val pInfo: PackageInfo = context!!.packageManager.getPackageInfo(context!!.getPackageName(), 0)
            val version = pInfo.versionName
            app_vid.text = "Version ${version}"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }


        return va
    }

    companion object {

        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            MainSettings().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }


    interface MainSettingsInterface{
        fun onSettingsSwitchNightMode()
        fun onSettingsSignIn()
    }

}