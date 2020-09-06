package com.color.matt.Fragments

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import com.color.matt.Constants
import com.color.matt.R
import com.color.mattdriver.Models.driver
import com.color.mattdriver.Models.organisation
import com.color.mattdriver.Models.route
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson

class ViewRoute : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null
    private val ARG_PARAM1 = "param1"
    private val ARG_PARAM2 = "param2"
    private val ARG_ORG = "ARG_ORG"
    private val ARG_ROUTE = "ARG_ROUTE"
    private val ARG_DRIVER = "ARG_DRIVER"
    private lateinit var my_organisation: organisation
    private lateinit var my_route: route
    private lateinit var listener: ViewRouteInterface
    private lateinit var driva : driver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
            my_organisation = Gson().fromJson(it.getString(ARG_ORG), organisation::class.java)
            my_route = Gson().fromJson(it.getString(ARG_ROUTE), route::class.java)
            driva = Gson().fromJson(it.getString(ARG_DRIVER), driver::class.java)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if(context is ViewRouteInterface){
            listener = context
        }
    }



    var when_route_picked: (route: String) -> Unit = {}

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val va = inflater.inflate(R.layout.fragment_view_route, container, false)
        val set_route_layout: RelativeLayout = va.findViewById(R.id.set_route_layout)
        val source_text: TextView = va.findViewById(R.id.source_text)
        val destination_text: TextView = va.findViewById(R.id.destination_text)
        val stops_text: TextView = va.findViewById(R.id.stops_text)
        val organisation_name: TextView = va.findViewById(R.id.organisation_name)

        val money: RelativeLayout = va.findViewById(R.id.money)

        money.setOnTouchListener { v, event -> true }


        organisation_name.text = "${my_organisation.name}, ${my_organisation.country}"
        stops_text.text = "${my_route.added_bus_stops.size} stops."
        destination_text.text = my_route.ending_pos_desc
        source_text.text = my_route.starting_pos_desc

        set_route_layout.setOnClickListener {

        }

        return va
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String, org: String, route: String, driver: String) =
            ViewRoute().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                    putString(ARG_ORG,org)
                    putString(ARG_ROUTE,route)
                    putString(ARG_DRIVER,driver)
                }
            }
    }

    interface ViewRouteInterface{

    }
}