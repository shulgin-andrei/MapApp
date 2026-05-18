package com.andrey.mapapp.utils

import com.andrey.mapapp.data.network.WeatherResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class WindAnalyzer {

    fun process(response: WeatherResponse): List<WindStat> {
        val directions = response.hourly.windDirections
        val speeds = response.hourly.windSpeeds
        val totalCount = directions.size

        // separation in 8 segments for WindRose
        val counts = IntArray(8)
        val speedSums = DoubleArray(8)

        for (i in directions.indices) {
            val deg = directions[i]
            val speed = speeds[i]

            // separating in 8 segments for WindRose
            val index = (((deg + 22.5) % 360) / 45).toInt()

            counts[index]++
            speedSums[index] += speed
        }

        // making WindStat objects
        return (0..7).map { i ->
            WindStat(
                directionIndex = i,
                frequency = (counts[i].toDouble() / totalCount) * 100,
                avgSpeed = if (counts[i] > 0) speedSums[i] / counts[i] else 0.0
            )
        }
    }

    companion object {
        private val gson = Gson()

        fun packStats(stats: List<WindStat>): String {
            return Gson().toJson(stats)
        }

        fun unpackStats(json: String): List<WindStat> {
            val type = object : TypeToken<List<WindStat>>() {}.type
            return Gson().fromJson(json, type)
        }
    }

}