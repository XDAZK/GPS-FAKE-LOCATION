package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MockHistoryEntity::class, SavedRouteEntity::class, FavoritePlaceEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MockDatabase : RoomDatabase() {

    abstract fun mockDao(): MockDao

    companion object {
        @Volatile
        private var INSTANCE: MockDatabase? = null

        fun getInstance(context: Context): MockDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MockDatabase::class.java,
                    "geosim_mock_database.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
