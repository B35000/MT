package com.color.mattdriver.Models

import java.io.Serializable

class organisation(var name: String, var creation_time: Long): Serializable{
    var country: String? = null
    var org_id: String? = null
    var admins: admin = admin(ArrayList<String>())
    var drivers: ArrayList<driver> = ArrayList()
    var deactivated_drivers: deactive_drivers = deactive_drivers(ArrayList<String>())

    class organisation_list(var organisations: ArrayList<organisation>):Serializable
    class admin(var admins: ArrayList<String> = ArrayList())
    class deactive_drivers(var deactivated_drivers: ArrayList<String>)
}