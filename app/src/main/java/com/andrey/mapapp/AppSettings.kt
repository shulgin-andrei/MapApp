package com.andrey.mapapp

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        const val KEY_WIND_PERIOD = "wind_period_months"
        const val KEY_SHOW_FULL_ROSE = "show_full_rose"
    }
    fun saveWindPeriod(months: Int) {
        prefs.edit().putInt(KEY_WIND_PERIOD, months).apply()
    }

    fun getWindPeriod(): Int {
        return prefs.getInt(KEY_WIND_PERIOD, 1)
    }

    fun setRoseDisplayMode(showFull: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_FULL_ROSE, showFull).apply()
    }

    fun isFullRoseEnabled(): Boolean {
        return prefs.getBoolean(KEY_SHOW_FULL_ROSE, true) // по умолчанию роза
    }
}