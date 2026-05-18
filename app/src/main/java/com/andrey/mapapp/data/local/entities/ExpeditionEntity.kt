package com.andrey.mapapp.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

// 1. Новая таблица
@Entity(tableName = "expeditions")
data class ExpeditionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dateCreated: Long = System.currentTimeMillis()
)