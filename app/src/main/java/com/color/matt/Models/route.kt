package com.color.mattdriver.Models

import com.google.android.gms.maps.model.LatLng

class route(var creation_time: Long,var org_id: String) {
    var route_id = ""

    var starting_pos_desc: String = ""
    var ending_pos_desc: String = ""

    var set_start_pos: LatLng? = null
    var set_end_pos: LatLng? = null

    var start_pos_geo_data: geo_data.reverseGeoData? = null
    var end_pos_geo_data: geo_data.reverseGeoData? = null

    var added_bus_stops: ArrayList<bus_stop> = ArrayList()
    var route_directions_data: directions_data? = null

    var creater: String = ""
    var country: String = ""

    class route_list(var routes: ArrayList<route>)
}