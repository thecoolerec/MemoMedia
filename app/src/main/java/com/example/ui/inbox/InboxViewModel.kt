package com.example.ui.inbox

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LocalMediaApplication
import com.example.core.enum.SessionStatus
import com.example.core.model.CaptureSession
import com.example.core.model.Category
import com.example.core.model.MediaAsset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SessionWithMedia(
    val session: CaptureSession,
    val mediaItems: List<MediaAsset>
)

data class InboxUiState(
    val pendingSessions: List<SessionWithMedia> = emptyList(),
    val orphanPendingMedia: List<MediaAsset> = emptyList(),
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
        sessionRepository.observeActiveSessions(),
        mediaRepository.observePending(),
        categoryRepository.observeAll(),
        _isRefreshing,
        _selectedAssetForDetail
    ) { sessions, pendingMedia, categories, isRefreshing, detailAsset ->
        val sessionMediaMap = pendingMedia.filter { it.captureSessionId != null }
            .groupBy { it.captureSessionId!! }

        val sessionsWithMedia = sessions.mapNotNull { session ->
            val items = sessionMediaMap[session.id] ?: emptyList()
            if (items.isNotEmpty()) {
                SessionWithMedia(session, items)
            } else null
        }

        val orphanMedia = pendingMedia.filter { it.captureSessionId == null }

        InboxUiState(
            pendingSessions = sessionsWithMedia,
            orphanPendingMedia = orphanMedia,
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

    fun classifySession(sessionId: Long, category: Category) {
        viewModelScope.launch {
            app.classifySessionUseCase(sessionId, category.id)
        }
    }

    fun classifySingleMedia(mediaId: Long, category: Category) {
        viewModelScope.launch {
            app.classifyMediaUseCase(mediaId, category.id)
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
            app.mediaReconciler.reconcile(forceFullScan = true)
            _isRefreshing.value = false
        }
    }
}
