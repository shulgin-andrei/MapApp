package com.andrey.mapapp.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "planned_points",
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sourceId"])]
)
data class PlannedPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sourceId: Int,
    val latitude: Double,
    val longitude: Double,
    val distance: Double,
    val weight: Double, // убрать
    val isVisited: Boolean = false
)