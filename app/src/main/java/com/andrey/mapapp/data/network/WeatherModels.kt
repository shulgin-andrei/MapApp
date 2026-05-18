package com.andrey.mapapp.data.network

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    @SerializedName("hourly")
    val hourly: HourlyData
)

data class HourlyData(
    @SerializedName("time")
    val time: List<String>,
    @SerializedName("wind_speed_10m")
    val windSpeeds: List<Double>,
    @SerializedName("wind_direction_10m")
    val windDirections: List<Int>
)