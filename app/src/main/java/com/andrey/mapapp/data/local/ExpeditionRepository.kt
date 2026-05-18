package com.andrey.mapapp.data.local

import android.content.SharedPreferences
import com.andrey.mapapp.data.local.dao.ExpeditionDao
import com.andrey.mapapp.data.local.entities.ExpeditionEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExpeditionRepository private constructor(
    private val expeditionDao: ExpeditionDao,
    private val prefs: SharedPreferences
) {
    // current Id saved in flow
    private val _currentExpeditionId = MutableStateFlow<Long?>(null)
    val currentExpeditionId = _currentExpeditionId.asStateFlow()
    val allExpeditions = expeditionDao.getAllExpeditions()

    init {
        // checking if there was an active expedition before
        val lastId = prefs.getLong("LAST_EXP_ID", -1L)
        if (lastId != -1L) {
            _currentExpeditionId.value = lastId
        }
    }

    // getting or creating new expedition
    suspend fun getOrCreateActiveId(): Long {
        val existingId = _currentExpeditionId.value
        if (existingId != null && existingId != 0L) {
            val dbExp = expeditionDao.findById(existingId)
            if (dbExp != null) {
                return existingId
            }
        }

        // default
        val newId = expeditionDao.insertExpedition(
            ExpeditionEntity(
                name = "Новая экспедиция",
                dateCreated = System.currentTimeMillis()
            )
        )
        setActiveExpedition(newId)
        return newId
    }

    // current exp
    fun setActiveExpedition(id: Long) {
        prefs.edit().putLong("LAST_EXP_ID", id).commit()
        _currentExpeditionId.value = id
    }

    suspend fun deleteExpedition(expedition: ExpeditionEntity) {
        expeditionDao.deleteExpedition(expedition)
        // Если удалили активную, сбрасываем ID
        if (_currentExpeditionId.value == expedition.id) {
            _currentExpeditionId.value = null
            prefs.edit().remove("LAST_EXP_ID").apply()
        }
    }

    companion object {
        @Volatile private var instance: ExpeditionRepository? = null

        // singleton
        fun getInstance(dao: ExpeditionDao, prefs: SharedPreferences): ExpeditionRepository {
            return instance ?: synchronized(this) {
                instance ?: ExpeditionRepository(dao, prefs).also { instance = it }
            }
        }
    }
}