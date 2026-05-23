package com.andrey.mapapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.andrey.mapapp.data.local.entities.PlannedPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlannedPointsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<PlannedPointEntity>)

    @Query("SELECT * FROM planned_points WHERE sourceId = :sourceId")
    fun getPointsBySource(sourceId: Int): Flow<List<PlannedPointEntity>>

    @Query("DELETE FROM planned_points WHERE sourceId = :sourceId")
    suspend fun deleteBySourceId(sourceId: Int)

    @Query("""
        SELECT pp.* FROM planned_points pp 
        INNER JOIN pollution_sources s ON pp.sourceId = s.id 
        WHERE s.expeditionId = :expeditionId
    """)
    fun getPointsByExpedition(expeditionId: Long): Flow<List<PlannedPointEntity>>
}