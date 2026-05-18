package com.andrey.mapapp.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "samples",
    foreignKeys = [
        ForeignKey(
            entity = ExpeditionEntity::class,
            parentColumns = ["id"],
            childColumns = ["expeditionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("expeditionId")]
)
data class SampleEntity (
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null,
    val expeditionId: Long,
    val lat: Double,
    val lon: Double,
    val createdAt: Long = System.currentTimeMillis(),
    val title: String? = null,
    val description: String? = null,
    val code: String? = null
)