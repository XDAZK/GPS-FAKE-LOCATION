package com.example.data.repository

import com.example.data.local.FavoritePlaceEntity
import com.example.data.local.MockDao
import com.example.data.local.MockHistoryEntity
import com.example.data.local.SavedRouteEntity
import com.example.data.model.GeoPoint
import com.example.util.GeoUtils
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

class MockLocationRepository(private val dao: MockDao) {

    val history: Flow<List<MockHistoryEntity>> = dao.getAllHistory()
    val savedRoutes: Flow<List<SavedRouteEntity>> = dao.getAllSavedRoutes()
    val favorites: Flow<List<FavoritePlaceEntity>> = dao.getAllFavorites()

    suspend fun recordHistory(latitude: Double, longitude: Double, name: String, mode: String = "POINT") {
        dao.insertHistory(
            MockHistoryEntity(
                latitude = latitude,
                longitude = longitude,
                name = name.ifEmpty { String.format(java.util.Locale.US, "%.5f, %.5f", latitude, longitude) },
                mode = mode
            )
        )
    }

    suspend fun deleteHistory(id: Long) = dao.deleteHistoryById(id)
    suspend fun clearHistory() = dao.clearAllHistory()

    suspend fun saveRoute(name: String, points: List<GeoPoint>): Long {
        val jsonArray = JSONArray()
        points.forEach { pt ->
            val obj = JSONObject().apply {
                put("lat", pt.latitude)
                put("lng", pt.longitude)
                put("alt", pt.altitude)
                put("name", pt.name)
            }
            jsonArray.put(obj)
        }
        val distance = GeoUtils.calculateTotalDistance(points)
        val entity = SavedRouteEntity(
            name = name,
            waypointsJson = jsonArray.toString(),
            totalDistanceMeters = distance,
            waypointCount = points.size
        )
        return dao.insertRoute(entity)
    }

    suspend fun deleteRoute(id: Long) = dao.deleteRouteById(id)

    fun parseRouteWaypoints(jsonString: String): List<GeoPoint> {
        val result = mutableListOf<GeoPoint>()
        try {
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    GeoPoint(
                        latitude = obj.optDouble("lat", 0.0),
                        longitude = obj.optDouble("lng", 0.0),
                        altitude = obj.optDouble("alt", 15.0),
                        name = obj.optString("name", "")
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    suspend fun addFavorite(name: String, latitude: Double, longitude: Double, category: String = "CUSTOM") {
        dao.insertFavorite(
            FavoritePlaceEntity(
                name = name,
                latitude = latitude,
                longitude = longitude,
                category = category
            )
        )
    }

    suspend fun removeFavorite(place: FavoritePlaceEntity) = dao.deleteFavorite(place)
}
