package com.andrey.mapapp.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "planned_points")
data class PlannedPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sourceId: Int,
    val latitude: Double,
    val longitude: Double,
    val distance: Double,
    val weight: Double,
    val isVisited: Boolean = false
)