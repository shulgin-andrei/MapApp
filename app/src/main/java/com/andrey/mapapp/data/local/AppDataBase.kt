package com.andrey.mapapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.andrey.mapapp.data.local.dao.ExpeditionDao
import com.andrey.mapapp.data.local.dao.SampleDao
import com.andrey.mapapp.data.local.dao.SourceDao
import com.andrey.mapapp.data.local.entities.ExpeditionEntity
import com.andrey.mapapp.data.local.entities.SampleEntity
import com.andrey.mapapp.data.local.entities.SourceEntity

@Database(
    entities = [
        SampleEntity::class,
        SourceEntity::class,
        ExpeditionEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDataBase : RoomDatabase() {

    // variables for interacting with DAO
    abstract fun sampleDao(): SampleDao
    abstract fun sourceDao(): SourceDao
    abstract fun expeditionDao(): ExpeditionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDataBase? = null
        fun createDataBase(context: Context): AppDataBase {
            // Singleton realization of database
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDataBase::class.java,
                    "appDataBase.db"
                )
                    // TRUNCATE for safety of Database Inspector
                    .setJournalMode(JournalMode.TRUNCATE)
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}