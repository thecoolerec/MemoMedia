package com.example.ui.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LocalMediaApplication
import com.example.core.enum.NotificationMode
import com.example.core.model.MediaAsset
import com.example.core.model.needsOrganization
import com.example.media.MediaDeletionHelper
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
    val notificationMode: String = "OVERLAY",
    val aggregationWindowSeconds: Int = 8,
    val showExpiredDialog: Boolean = false,
    val pendingDeleteRequest: IntentSenderRequest? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LocalMediaApplication
    private val mediaRepository = app.mediaRepository
    private val retentionScanner = app.retentionScanner
    private val reconciler = app.mediaReconciler
    private val settingsRepository = app.appSettingsRepository

    private val _isScanning = MutableStateFlow(false)
    private val _isRetentionScanning = MutableStateFlow(false)
    private val _expiredItems = MutableStateFlow<List<MediaAsset>>(emptyList())
    private val _activeBatch = MutableStateFlow<List<MediaAsset>>(emptyList())
    private val _remainingAssets = MutableStateFlow<List<MediaAsset>>(emptyList())
    private val _showExpiredDialog = MutableStateFlow(false)
    private val _pendingDeleteRequest = MutableStateFlow<IntentSenderRequest?>(null)

    private data class ActionState(
        val isScanning: Boolean,
        val isRetention: Boolean,
        val expired: List<MediaAsset>,
        val showExpired: Boolean,
        val deleteRequest: IntentSenderRequest?
    )

    private val actionFlow = combine(
        _isScanning,
        _isRetentionScanning,
        _expiredItems,
        _showExpiredDialog,
        _pendingDeleteRequest
    ) { isScanning, isRetention, expired, showExpired, deleteReq ->
        ActionState(isScanning, isRetention, expired, showExpired, deleteReq)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        mediaRepository.observeTimeline(),
        actionFlow,
        settingsRepository.settings
    ) { allMedia, actions, config ->
        val manageableMedia = allMedia.filter {
            it.primaryCategoryId != null || it.needsOrganization
        }
        val classified = manageableMedia.count { it.primaryCategoryId != null }
        val unclassified = manageableMedia.count { it.needsOrganization }
        val total = classified + unclassified
        val totalBytes = manageableMedia.sumOf { it.sizeBytes ?: 0L }

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
            isServiceRunning = config.isServiceRunning,
            isOverlayPermissionGranted = overlayGranted,
            notificationMode = config.defaultNotificationMode.name,
            aggregationWindowSeconds = config.aggregationWindowSeconds,
            showExpiredDialog = actions.showExpired,
            pendingDeleteRequest = actions.deleteRequest
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState()
    )

    fun triggerFullScan() {
        viewModelScope.launch {
            _isScanning.value = true
            reconciler.reconcile(forceFullScan = true)
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

    fun setNotificationMode(modeStr: String) {
        val mode = runCatching { NotificationMode.valueOf(modeStr) }.getOrDefault(NotificationMode.OVERLAY)
        settingsRepository.setNotificationMode(mode)
    }

    fun setAggregationWindow(seconds: Int) {
        settingsRepository.setAggregationWindow(seconds)
    }

    fun toggleBackgroundService(enable: Boolean) {
        if (enable) {
            app.monitoringController.enable()
        } else {
            app.monitoringController.disable()
        }
    }

    fun getOverlayPermissionIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${getApplication<Application>().packageName}")
        )
    }

    fun requestDeleteExpiredMedia(items: List<MediaAsset>) {
        viewModelScope.launch {
            val result = app.deleteMediaUseCase.execute(items)
            handleDeleteResult(result)
        }
    }

    fun confirmDeletedInDb() {
        viewModelScope.launch {
            val active = _activeBatch.value
            val remaining = _remainingAssets.value
            val result = app.deleteMediaUseCase.onUserConsentResult(active, remaining, true)
            handleDeleteResult(result)
        }
    }

    fun cancelDeleteRequest() {
        viewModelScope.launch {
            val active = _activeBatch.value
            val remaining = _remainingAssets.value
            app.deleteMediaUseCase.onUserConsentResult(active, remaining, false)
            _activeBatch.value = emptyList()
            _remainingAssets.value = emptyList()
            _pendingDeleteRequest.value = null
        }
    }

    private fun handleDeleteResult(result: com.example.domain.DeleteMediaResult) {
        when (result) {
            is com.example.domain.DeleteMediaResult.NeedsUserConsent -> {
                _activeBatch.value = result.activeBatchAssets
                _remainingAssets.value = result.remainingAssets
                _pendingDeleteRequest.value = result.intentSenderRequest
            }
            is com.example.domain.DeleteMediaResult.Success -> {
                _activeBatch.value = emptyList()
                _remainingAssets.value = emptyList()
                _pendingDeleteRequest.value = null
                _expiredItems.value = emptyList()
                _showExpiredDialog.value = false
            }
            is com.example.domain.DeleteMediaResult.Failure -> {
                _activeBatch.value = emptyList()
                _remainingAssets.value = emptyList()
                _pendingDeleteRequest.value = null
            }
        }
    }
}
