package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mock_history")
data class MockHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mode: String = "POINT" // "POINT" or "ROUTE"
)

@Entity(tableName = "saved_routes")
data class SavedRouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val waypointsJson: String, // serialized JSON list of points
    val totalDistanceMeters: Double,
    val waypointCount: Int,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorite_places")
data class FavoritePlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val category: String = "CUSTOM"
)
