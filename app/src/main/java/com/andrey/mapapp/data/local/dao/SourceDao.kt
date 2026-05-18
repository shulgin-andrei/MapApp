package com.andrey.mapapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.andrey.mapapp.data.local.entities.SourceEntity
import kotlinx.coroutines.flow.Flow

@Dao()
interface SourceDao {
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE)
    suspend fun insertSource(source: SourceEntity)

    @Query("SELECT * FROM pollution_sources WHERE expeditionId = :expId")
    fun getSourcesByExpedition(expId: Long): kotlinx.coroutines.flow.Flow<List<SourceEntity>>

    @Query("SELECT * FROM pollution_sources")
    fun getAllSources(): Flow<List<SourceEntity>>

    @Query("DELETE FROM pollution_sources WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM pollution_sources WHERE id = :id")
    suspend fun findById(id: Int): SourceEntity?


    @Query("DELETE FROM pollution_sources")
    suspend fun deleteAll()

    @Query("DELETE FROM sqlite_sequence WHERE name = 'pollution_sources'")
    suspend fun resetPrimaryKey()

    @Query("UPDATE pollution_sources SET windDataJson = :json WHERE id = :sourceId")
    suspend fun updateWindData(sourceId: Int, json: String)

    @Transaction
    suspend fun clearTableAndResetIndex() {
        deleteAll()
        resetPrimaryKey()
    }
}