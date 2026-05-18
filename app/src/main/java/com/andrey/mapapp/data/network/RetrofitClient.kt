package com.andrey.mapapp.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// singleton client
object RetrofitClient {
    private const val BASE_URL = "https://archive-api.open-meteo.com/"

    val apiService: WeatherApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApiService::class.java)
    }
}