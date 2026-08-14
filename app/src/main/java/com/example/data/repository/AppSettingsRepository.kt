package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.core.enum.NotificationMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val isServiceRunning: Boolean = true,
    val aggregationWindowSeconds: Int = 8,
    val quietPeriodSeconds: Int = 2,
    val defaultNotificationMode: NotificationMode = NotificationMode.OVERLAY,
    val overlayEnabled: Boolean = true,
    val retentionScanIntervalHours: Int = 6
)

class AppSettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun getSnapshot(): AppSettings = _settings.value

    private fun loadSettings(): AppSettings {
        return AppSettings(
            isServiceRunning = prefs.getBoolean("service_running", true),
            aggregationWindowSeconds = prefs.getInt("aggregation_window", 8),
            quietPeriodSeconds = prefs.getInt("quiet_period", 2),
            defaultNotificationMode = runCatching {
                NotificationMode.valueOf(prefs.getString("notification_mode", "OVERLAY") ?: "OVERLAY")
            }.getOrDefault(NotificationMode.OVERLAY),
            overlayEnabled = prefs.getBoolean("overlay_enabled", true),
            retentionScanIntervalHours = prefs.getInt("retention_scan_interval", 6)
        )
    }

    fun setServiceRunning(running: Boolean) {
        prefs.edit().putBoolean("service_running", running).apply()
        _settings.value = _settings.value.copy(isServiceRunning = running)
    }

    fun setAggregationWindow(seconds: Int) {
        prefs.edit().putInt("aggregation_window", seconds).apply()
        _settings.value = _settings.value.copy(aggregationWindowSeconds = seconds)
    }

    fun setQuietPeriod(seconds: Int) {
        prefs.edit().putInt("quiet_period", seconds).apply()
        _settings.value = _settings.value.copy(quietPeriodSeconds = seconds)
    }

    fun setNotificationMode(mode: NotificationMode) {
        prefs.edit().putString("notification_mode", mode.name).apply()
        _settings.value = _settings.value.copy(defaultNotificationMode = mode)
    }

    fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("overlay_enabled", enabled).apply()
        _settings.value = _settings.value.copy(overlayEnabled = enabled)
    }
}
