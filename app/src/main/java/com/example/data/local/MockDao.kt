package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MockDao {

    // History
    @Query("SELECT * FROM mock_history ORDER BY timestamp DESC LIMIT 50")
    fun getAllHistory(): Flow<List<MockHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: MockHistoryEntity): Long

    @Query("DELETE FROM mock_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM mock_history")
    suspend fun clearAllHistory()

    // Saved Routes
    @Query("SELECT * FROM saved_routes ORDER BY createdAt DESC")
    fun getAllSavedRoutes(): Flow<List<SavedRouteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: SavedRouteEntity): Long

    @Delete
    suspend fun deleteRoute(route: SavedRouteEntity)

    @Query("DELETE FROM saved_routes WHERE id = :id")
    suspend fun deleteRouteById(id: Long)

    // Favorite Places
    @Query("SELECT * FROM favorite_places ORDER BY id DESC")
    fun getAllFavorites(): Flow<List<FavoritePlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(place: FavoritePlaceEntity): Long

    @Delete
    suspend fun deleteFavorite(place: FavoritePlaceEntity)
}
