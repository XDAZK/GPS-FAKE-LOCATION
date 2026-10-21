package com.example.service

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.GeoPoint

/**
 * Persists active mock GPS state into SharedPreferences.
 * Modeled after Sriharan-S/elsewhere MockStateStore.
 *
 * Ensures that if Android kills the service in the background due to low memory,
 * when START_STICKY restarts the service with a null Intent, the service can
 * instantly resume mocking the exact same location without dropping to real GPS!
 */
object MockStateStore {
    private const val PREFS_NAME = "geosim_mock_state"
    private const val KEY_IS_MOCKING = "is_mocking"
    private const val KEY_LAT = "mock_lat"
    private const val KEY_LNG = "mock_lng"
    private const val KEY_ALT = "mock_alt"
    private const val KEY_NAME = "mock_name"
    private const val KEY_IS_ROUTE = "is_route"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setActivePoint(context: Context, point: GeoPoint, isRoute: Boolean = false) {
        prefs(context).edit().apply {
            putBoolean(KEY_IS_MOCKING, true)
            putFloat(KEY_LAT, point.latitude.toFloat())
            putFloat(KEY_LNG, point.longitude.toFloat())
            putFloat(KEY_ALT, point.altitude.toFloat())
            putString(KEY_NAME, point.name)
            putBoolean(KEY_IS_ROUTE, isRoute)
        }.apply()
    }

    fun getActivePoint(context: Context): GeoPoint? {
        val sp = prefs(context)
        if (!sp.getBoolean(KEY_IS_MOCKING, false)) return null
        val lat = sp.getFloat(KEY_LAT, Float.NaN).toDouble()
        val lng = sp.getFloat(KEY_LNG, Float.NaN).toDouble()
        if (lat.isNaN() || lng.isNaN()) return null
        val alt = sp.getFloat(KEY_ALT, 15f).toDouble()
        val name = sp.getString(KEY_NAME, "Vị trí đã lưu") ?: "Vị trí đã lưu"
        return GeoPoint(latitude = lat, longitude = lng, altitude = alt, name = name)
    }

    fun isRouteActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IS_ROUTE, false)

    fun clearActive(context: Context) {
        prefs(context).edit().apply {
            putBoolean(KEY_IS_MOCKING, false)
            remove(KEY_LAT)
            remove(KEY_LNG)
            remove(KEY_NAME)
            remove(KEY_IS_ROUTE)
        }.apply()
    }
}
