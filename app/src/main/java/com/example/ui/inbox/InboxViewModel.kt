package com.example.ui.inbox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LocalMediaApplication
import com.example.core.enum.SessionStatus
import com.example.core.model.Category
import com.example.core.model.MediaAsset
import com.example.core.model.MediaSourceInfo
import com.example.core.model.MediaSourceResolver
import com.example.core.model.needsOrganization
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PendingSourceGroup(
    val source: MediaSourceInfo,
    val mediaItems: List<MediaAsset>
) {
    val latestAt: Long
        get() = mediaItems.maxOfOrNull { it.addedAt } ?: 0L
}

data class InboxUiState(
    val sourceGroups: List<PendingSourceGroup> = emptyList(),
    val totalPendingCount: Int = 0,
    val categories: List<Category> = emptyList(),
    val isRefreshing: Boolean = false,
    val selectedAssetForDetail: MediaAsset? = null
)

class InboxViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LocalMediaApplication
    private val mediaRepository = app.mediaRepository
    private val categoryRepository = app.categoryRepository
    private val sessionRepository = app.captureSessionRepository

    private val _isRefreshing = MutableStateFlow(false)
    private val _selectedAssetForDetail = MutableStateFlow<MediaAsset?>(null)

    val uiState: StateFlow<InboxUiState> = combine(
        mediaRepository.observePending(),
        categoryRepository.observeAll(),
        _isRefreshing,
        _selectedAssetForDetail
    ) { pendingMedia, categories, isRefreshing, detailAsset ->
        val groups = pendingMedia
            .groupBy { MediaSourceResolver.resolve(it).key }
            .values
            .map { items ->
                val sortedItems = items.sortedByDescending { it.addedAt }
                PendingSourceGroup(
                    source = MediaSourceResolver.resolve(sortedItems.first()),
                    mediaItems = sortedItems
                )
            }
            .sortedByDescending { it.latestAt }

        InboxUiState(
            sourceGroups = groups,
            totalPendingCount = pendingMedia.size,
            categories = categories,
            isRefreshing = isRefreshing,
            selectedAssetForDetail = detailAsset
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        InboxUiState()
    )

    fun classifySourceGroup(group: PendingSourceGroup, category: Category) {
        viewModelScope.launch {
            val relatedSessionIds = group.mediaItems.mapNotNull { it.captureSessionId }.distinct()
            group.mediaItems.forEach { asset ->
                app.classifyMediaUseCase(asset.id, category.id)
            }
            for (sessionId in relatedSessionIds) {
                closeSessionIfFullyClassified(sessionId)
            }
        }
    }

    fun classifySingleMedia(mediaId: Long, category: Category) {
        viewModelScope.launch {
            val asset = mediaRepository.getById(mediaId)
            app.classifyMediaUseCase(mediaId, category.id)
            asset?.captureSessionId?.let { closeSessionIfFullyClassified(it) }
        }
    }

    private suspend fun closeSessionIfFullyClassified(sessionId: Long) {
        val sessionItems = mediaRepository.getBySession(sessionId)
        if (sessionItems.isNotEmpty() && sessionItems.none { it.needsOrganization }) {
            sessionRepository.updateStatus(sessionId, SessionStatus.CLASSIFIED.name)
            app.notificationManager.cancelSessionNotification(sessionId)
        }
    }

    fun openDetail(asset: MediaAsset) {
        _selectedAssetForDetail.value = asset
    }

    fun closeDetail() {
        _selectedAssetForDetail.value = null
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                app.mediaReconciler.reconcile(forceFullScan = true)
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
