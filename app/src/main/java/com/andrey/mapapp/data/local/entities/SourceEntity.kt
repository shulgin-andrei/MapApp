package com.andrey.mapapp.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.andrey.mapapp.data.local.enums.SourceTypeEnum
import org.osmdroid.util.GeoPoint

@Entity(tableName = "pollution_sources",
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
data class SourceEntity (
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null,
    // based on source type there has to be different amounts of points
    val expeditionId: Long,
    val type : SourceTypeEnum,
    val title: String,
    val description: String,
    val geometry: List<GeoPoint>,
    val hazardLevel: Double = 1.0, // approximate thing, dunno how to measure this
    var windDataJson: String? = null // data for windRozeOverlay
)