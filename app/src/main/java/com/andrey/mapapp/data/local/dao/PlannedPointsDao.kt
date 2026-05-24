package com.andrey.mapapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("UPDATE planned_points SET isVisited = 1 WHERE id = :pointId")
    suspend fun markAsVisited(pointId: Int)

    @Query("""
        SELECT pp.* FROM planned_points pp 
        INNER JOIN pollution_sources s ON pp.sourceId = s.id 
        WHERE s.expeditionId = :expeditionId
    """)
    fun getPointsByExpedition(expeditionId: Long): Flow<List<PlannedPointEntity>>
    @Query("DELETE FROM planned_points")
    suspend fun deleteAll()

    @Query("DELETE FROM sqlite_sequence WHERE name = 'planned_points'")
    suspend fun resetPrimaryKey()

    @Transaction
    suspend fun clearTableAndResetIndex() {
        deleteAll()
        resetPrimaryKey()
    }

}