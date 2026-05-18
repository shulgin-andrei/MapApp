package com.andrey.mapapp.data.network

import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    @GET("v1/archive")
    suspend fun getHistoryWeather(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("start_date") startDate: String, // format - "YYYY-MM-DD"
        @Query("end_date") endDate: String,
        @Query("hourly") hourly: String = "wind_speed_10m,wind_direction_10m",
        @Query("timezone") timezone: String = "GMT"
    ): WeatherResponse
}