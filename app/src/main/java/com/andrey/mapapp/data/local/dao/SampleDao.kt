package com.andrey.mapapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.andrey.mapapp.data.local.entities.SampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SampleDao {
    @Insert(onConflict = OnConflictStrategy.Companion.REPLACE ) // replaces old sample on new with same id
    suspend fun insertItem(sample: SampleEntity)

    @Query("SELECT * FROM samples WHERE expeditionId = :expId")
    fun getSamplesByExpedition(expId: Long): Flow<List<SampleEntity>>

    @Delete
    suspend fun deleteItem(sample: SampleEntity)

    @Query("SELECT * FROM samples WHERE id = :id")
    suspend fun findById(id: Int) : SampleEntity?

    @Query("DELETE FROM samples WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("SELECT * FROM samples")
    fun getAll(): Flow<List<SampleEntity>>

    @Query("DELETE FROM samples")
    suspend fun deleteAll()

    @Query("DELETE FROM sqlite_sequence WHERE name = 'samples'")
    suspend fun resetPrimaryKey()

    @Transaction
    suspend fun clearTableAndResetIndex() {
        deleteAll()
        resetPrimaryKey()
    }

    @Update
    suspend fun updateItem(sample: SampleEntity)

}