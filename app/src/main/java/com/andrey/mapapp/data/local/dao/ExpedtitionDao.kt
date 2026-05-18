package com.andrey.mapapp.data.local.dao


import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.andrey.mapapp.data.local.entities.ExpeditionEntity
import com.andrey.mapapp.data.local.entities.SourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpeditionDao {
    @Query("SELECT * FROM expeditions ORDER BY dateCreated DESC")
    fun getAllExpeditions(): Flow<List<ExpeditionEntity>>

    @Insert
    suspend fun insertExpedition(expedition: ExpeditionEntity): Long

    @Delete
    suspend fun deleteExpedition(expedition: ExpeditionEntity)

    @Query("SELECT * FROM expeditions WHERE id = :Id")
    suspend fun findById(Id: Long): ExpeditionEntity?

    @Query("DELETE FROM expeditions WHERE id = :expId")
    suspend fun deleteById(expId: Long)

    @Query("DELETE FROM expeditions")
    suspend fun deleteAll()

    @Query("DELETE FROM sqlite_sequence WHERE name = 'expeditions'")
    suspend fun resetPrimaryKey()
    @Transaction
    suspend fun clearTableAndResetIndex() {
        deleteAll()
        resetPrimaryKey()
    }

    // Получение экспедиции вместе с вложенными источниками
    @Transaction
    @Query("SELECT * FROM expeditions WHERE id = :expeditionId")
    suspend fun getExpeditionWithSources(expeditionId: Long): ExpeditionWithSources
}

// Вспомогательный класс для связи один-ко-многим
data class ExpeditionWithSources(
    @Embedded val expedition: ExpeditionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "expeditionId"
    )
    val sources: List<SourceEntity>
)