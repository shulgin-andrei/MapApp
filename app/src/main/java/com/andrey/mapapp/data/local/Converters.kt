package com.andrey.mapapp.data.local

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.util.GeoPoint

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromGeoPointList(value: List<GeoPoint>) : String {
        // making simple json string [lat, long] out of GeoPoint objects
        val data = value.map { listOf(it.latitude, it.longitude)}
        return gson.toJson(data)
    }

    @TypeConverter
    fun toGeoPointList(value: String) : List<GeoPoint> {
        val listType = object : TypeToken<List<List<Double>>>() {}.type
        val data: List<List<Double>> = gson.fromJson(value, listType)
        return data.map { GeoPoint(it[0], it[1]) }
    }
}