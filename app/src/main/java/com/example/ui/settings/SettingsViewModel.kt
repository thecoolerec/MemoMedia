package com.example.ui.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LocalMediaApplication
import com.example.core.model.MediaAsset
import com.example.watcher.MediaJobService
import com.example.watcher.MediaMonitorService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val totalMediaCount: Int = 0,
    val classifiedCount: Int = 0,
    val unclassifiedCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val expiredMediaItems: List<MediaAsset> = emptyList(),
    val isScanning: Boolean = false,
    val isRetentionScanning: Boolean = false,
    val isServiceRunning: Boolean = true,
    val isOverlayPermissionGranted: Boolean = false,
    val notificationMode: String = "IMMEDIATE",
    val aggregationWindowSeconds: Int = 10,
    val showExpiredDialog: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LocalMediaApplication
    private val mediaRepository = app.mediaRepository
    private val retentionScanner = app.retentionScanner
    private val reconciler = app.mediaReconciler
    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _isScanning = MutableStateFlow(false)
    private val _isRetentionScanning = MutableStateFlow(false)
    private val _expiredItems = MutableStateFlow<List<MediaAsset>>(emptyList())
    private val _showExpiredDialog = MutableStateFlow(false)
    private val _isServiceRunning = MutableStateFlow(true)
    private val _notificationMode = MutableStateFlow(prefs.getString("notification_mode", "IMMEDIATE") ?: "IMMEDIATE")
    private val _aggregationWindow = MutableStateFlow(prefs.getInt("aggregation_window", 10))

    private data class ActionState(
        val isScanning: Boolean,
        val isRetention: Boolean,
        val expired: List<MediaAsset>,
        val showExpired: Boolean
    )

    private data class ConfigState(
        val isService: Boolean,
        val notifMode: String,
        val aggWin: Int
    )

    private val actionFlow = combine(
        _isScanning,
        _isRetentionScanning,
        _expiredItems,
        _showExpiredDialog
    ) { isScanning, isRetention, expired, showExpired ->
        ActionState(isScanning, isRetention, expired, showExpired)
    }

    private val configFlow = combine(
        _isServiceRunning,
        _notificationMode,
        _aggregationWindow
    ) { isService, notifMode, aggWin ->
        ConfigState(isService, notifMode, aggWin)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        mediaRepository.observeTimeline(),
        actionFlow,
        configFlow
    ) { allMedia, actions, config ->
        val total = allMedia.size
        val classified = allMedia.count { it.primaryCategoryId != null }
        val unclassified = total - classified
        val totalBytes = allMedia.sumOf { it.sizeBytes ?: 0L }

        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(getApplication())
        } else true

        SettingsUiState(
            totalMediaCount = total,
            classifiedCount = classified,
            unclassifiedCount = unclassified,
            totalSizeBytes = totalBytes,
            expiredMediaItems = actions.expired,
            isScanning = actions.isScanning,
            isRetentionScanning = actions.isRetention,
            isServiceRunning = config.isService,
            isOverlayPermissionGranted = overlayGranted,
            notificationMode = config.notifMode,
            aggregationWindowSeconds = config.aggWin,
            showExpiredDialog = actions.showExpired
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState()
    )

    fun triggerFullScan() {
        viewModelScope.launch {
            _isScanning.value = true
            reconciler.reconcile()
            _isScanning.value = false
        }
    }

    fun triggerRetentionScan() {
        viewModelScope.launch {
            _isRetentionScanning.value = true
            val summary = retentionScanner.scanAndMarkExpired()
            _expiredItems.value = summary.expiredItems
            _isRetentionScanning.value = false
            _showExpiredDialog.value = true
        }
    }

    fun closeExpiredDialog() {
        _showExpiredDialog.value = false
    }

    fun setNotificationMode(mode: String) {
        _notificationMode.value = mode
        prefs.edit().putString("notification_mode", mode).apply()
    }

    fun setAggregationWindow(seconds: Int) {
        _aggregationWindow.value = seconds
        prefs.edit().putInt("aggregation_window", seconds).apply()
    }

    fun toggleBackgroundService(enable: Boolean) {
        _isServiceRunning.value = enable
        val context = getApplication<Application>()
        if (enable) {
            MediaMonitorService.start(context)
            MediaJobService.schedule(context)
        } else {
            MediaMonitorService.stop(context)
        }
    }

    fun getOverlayPermissionIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${getApplication<Application>().packageName}")
        )
    }

    fun deleteExpiredMedia(items: List<MediaAsset>) {
        viewModelScope.launch {
            val ids = items.map { it.id }
            mediaRepository.markDeleted(ids)
            _expiredItems.value = emptyList()
            _showExpiredDialog.value = false
        }
    }
}
