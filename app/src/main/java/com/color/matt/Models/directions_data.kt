package com.color.mattdriver.Models

data class directions_data(var geocoded_waypoints: List<GeocodedWaypoint>, var routes: List<Route>, var status: String){

    data class GeocodedWaypoint(
        var geocoder_status: String,
        var place_id: String,
        var types: List<String>
    )

    data class Route(
        var bounds: Bounds,
        var copyrights: String,
        var legs: List<Leg>,
        var overview_polyline: OverviewPolyline,
        var summary: String,
        var warnings: List<Any>,
        var waypoint_order: List<Any>
    )

    data class Bounds(
        var northeast: Northeast,
        var southwest: Southwest
    )

    data class Leg(
        var distance: Distance,
        var duration: Duration,
        var end_address: String,
        var end_location: EndLocation,
        var start_address: String,
        var start_location: StartLocation,
        var steps: List<Step>,
        var traffic_speed_entry: List<Any>,
        var via_waypoint: List<Any>
    )

    data class OverviewPolyline(
        var points: String
    )

    data class Northeast(
        var lat: Double,
        var lng: Double
    )

    data class Southwest(
        var lat: Double,
        var lng: Double
    )

    data class Distance(
        var text: String,
        var value: Int
    )

    data class Duration(
        var text: String,
        var value: Int
    )

    data class EndLocation(
        var lat: Double,
        var lng: Double
    )

    data class StartLocation(
        var lat: Double,
        var lng: Double
    )

    data class Step(
        var distance: DistanceX,
        var duration: DurationX,
        var end_location: EndLocationX,
        var html_instructions: String,
        var maneuver: String,
        var polyline: Polyline,
        var start_location: StartLocationX,
        var travel_mode: String
    )

    data class DistanceX(
        var text: String,
        var value: Int
    )

    data class DurationX(
        var text: String,
        var value: Int
    )

    data class EndLocationX(
        var lat: Double,
        var lng: Double
    )

    data class Polyline(
        var points: String
    )

    data class StartLocationX(
        var lat: Double,
        var lng: Double
    )
}