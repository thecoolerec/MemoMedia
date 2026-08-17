package com.example.watcher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.data.repository.AppSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MonitoringController(
    private val context: Context,
    private val appSettingsRepository: AppSettingsRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun enable() {
        scope.launch {
            appSettingsRepository.setServiceRunning(true)
            applyCurrentStateInternal()
        }
    }

    fun disable() {
        scope.launch {
            appSettingsRepository.setServiceRunning(false)
            applyCurrentStateInternal()
        }
    }

    fun applyCurrentState() {
        scope.launch {
            applyCurrentStateInternal()
        }
    }

    fun onBoot() {
        scope.launch {
            val settings = appSettingsRepository.getSnapshot()
            val hasPermissions = hasMediaPermission(context)
            if (settings.isServiceRunning && hasPermissions) {
                MediaJobService.schedule(context)
                BootReconcileJobService.schedule(context)
            } else {
                MediaJobService.cancel(context)
                BootReconcileJobService.cancel(context)
            }
        }
    }

    private suspend fun applyCurrentStateInternal() {
        val settings = appSettingsRepository.getSnapshot()
        val hasPermissions = hasMediaPermission(context)

        if (settings.isServiceRunning && hasPermissions) {
            MediaMonitorService.start(context)
            MediaJobService.schedule(context)
        } else {
            MediaMonitorService.stop(context)
            MediaJobService.cancel(context)
            BootReconcileJobService.cancel(context)
        }
    }

    companion object {
        fun hasMediaPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
}
