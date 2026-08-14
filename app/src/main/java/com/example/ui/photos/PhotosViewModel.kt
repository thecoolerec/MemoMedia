package com.example.ui.photos

import android.app.Application
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LocalMediaApplication
import com.example.core.enum.MediaStatus
import com.example.core.model.Category
import com.example.core.model.MediaAsset
import com.example.core.model.Tag
import com.example.media.MediaDeletionHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PhotosUiState(
    val items: List<MediaAsset> = emptyList(),
    val filteredItems: List<MediaAsset> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null, // null means "All"
    val isSelectionMode: Boolean = false,
    val selectedMediaIds: Set<Long> = emptySet(),
    val selectedAssetForDetail: MediaAsset? = null,
    val selectedAssetTags: List<Tag> = emptyList(),
    val isRefreshing: Boolean = false,
    val pendingDeleteRequest: IntentSenderRequest? = null
)

class PhotosViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LocalMediaApplication
    private val mediaRepository = app.mediaRepository
    private val categoryRepository = app.categoryRepository
    private val tagRepository = app.tagRepository

    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _isSelectionMode = MutableStateFlow(false)
    private val _selectedMediaIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _selectedAssetForDetail = MutableStateFlow<MediaAsset?>(null)
    private val _selectedAssetTags = MutableStateFlow<List<Tag>>(emptyList())
    private val _isRefreshing = MutableStateFlow(false)
    private val _pendingDeleteRequest = MutableStateFlow<IntentSenderRequest?>(null)
    private var pendingDeleteIds = listOf<Long>()

    val uiState: StateFlow<PhotosUiState> = combine(
        mediaRepository.observeTimeline(),
        categoryRepository.observeAll(),
        _selectedCategoryId,
        _isSelectionMode,
        _selectedMediaIds,
        _selectedAssetForDetail,
        _selectedAssetTags,
        _isRefreshing,
        _pendingDeleteRequest
    ) { params ->
        @Suppress("UNCHECKED_CAST")
        val items = params[0] as List<MediaAsset>
        @Suppress("UNCHECKED_CAST")
        val categories = params[1] as List<Category>
        val categoryId = params[2] as? Long
        val isSelectionMode = params[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val selectedMediaIds = params[4] as Set<Long>
        val detailAsset = params[5] as? MediaAsset
        @Suppress("UNCHECKED_CAST")
        val detailTags = params[6] as List<Tag>
        val isRefreshing = params[7] as Boolean
        val deleteReq = params[8] as? IntentSenderRequest

        val filtered = when (categoryId) {
            null -> items // All
            -1L -> items.filter { it.status == MediaStatus.PENDING } // 待整理
            else -> items.filter { it.primaryCategoryId == categoryId }
        }

        PhotosUiState(
            items = items,
            filteredItems = filtered,
            categories = categories,
            selectedCategoryId = categoryId,
            isSelectionMode = isSelectionMode,
            selectedMediaIds = selectedMediaIds,
            selectedAssetForDetail = detailAsset,
            selectedAssetTags = detailTags,
            isRefreshing = isRefreshing,
            pendingDeleteRequest = deleteReq
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PhotosUiState()
    )

    fun selectCategoryFilter(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
    }

    fun openDetail(asset: MediaAsset) {
        _selectedAssetForDetail.value = asset
        viewModelScope.launch {
            _selectedAssetTags.value = tagRepository.getTagsForMedia(asset.id)
        }
    }

    fun closeDetail() {
        _selectedAssetForDetail.value = null
        _selectedAssetTags.value = emptyList()
    }

    fun toggleSelectionMode() {
        _isSelectionMode.value = !_isSelectionMode.value
        if (!_isSelectionMode.value) {
            _selectedMediaIds.value = emptySet()
        }
    }

    fun toggleItemSelection(mediaId: Long) {
        val current = _selectedMediaIds.value.toMutableSet()
        if (current.contains(mediaId)) {
            current.remove(mediaId)
        } else {
            current.add(mediaId)
        }
        _selectedMediaIds.value = current
        if (current.isEmpty()) {
            _isSelectionMode.value = false
        }
    }

    fun selectAll() {
        _selectedMediaIds.value = uiState.value.filteredItems.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedMediaIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun assignCategoryToSelected(category: Category) {
        val selected = _selectedMediaIds.value
        viewModelScope.launch {
            for (id in selected) {
                val asset = mediaRepository.getById(id)
                val expireAt = app.policyEngine.calculateExpireAt(
                    asset?.capturedAt ?: asset?.addedAt ?: System.currentTimeMillis(),
                    category
                )
                mediaRepository.assignCategory(id, category.id, expireAt)
            }
            clearSelection()
        }
    }

    fun assignCategoryToSingle(asset: MediaAsset, category: Category) {
        viewModelScope.launch {
            val expireAt = app.policyEngine.calculateExpireAt(
                asset.capturedAt ?: asset.addedAt,
                category
            )
            mediaRepository.assignCategory(asset.id, category.id, expireAt)
            _selectedAssetForDetail.value = mediaRepository.getById(asset.id)
        }
    }

    fun addTagToDetail(tagName: String) {
        val asset = _selectedAssetForDetail.value ?: return
        viewModelScope.launch {
            val tagId = tagRepository.createTag(tagName)
            tagRepository.addTagToMedia(asset.id, tagId)
            _selectedAssetTags.value = tagRepository.getTagsForMedia(asset.id)
        }
    }

    fun removeTagFromDetail(tagId: Long) {
        val asset = _selectedAssetForDetail.value ?: return
        viewModelScope.launch {
            tagRepository.removeTagFromMedia(asset.id, tagId)
            _selectedAssetTags.value = tagRepository.getTagsForMedia(asset.id)
        }
    }

    fun deleteAsset(asset: MediaAsset) {
        pendingDeleteIds = listOf(asset.id)
        val deleteRequest = MediaDeletionHelper.createDeleteRequestOrDeleteDirectly(
            getApplication(),
            listOf(asset.contentUri)
        )
        if (deleteRequest != null) {
            _pendingDeleteRequest.value = deleteRequest
        } else {
            confirmDeletedInDb()
            closeDetail()
        }
    }

    fun deleteSelected() {
        val selectedIds = _selectedMediaIds.value.toList()
        pendingDeleteIds = selectedIds
        val uris = uiState.value.items.filter { selectedIds.contains(it.id) }.map { it.contentUri }
        val deleteRequest = MediaDeletionHelper.createDeleteRequestOrDeleteDirectly(
            getApplication(),
            uris
        )
        if (deleteRequest != null) {
            _pendingDeleteRequest.value = deleteRequest
        } else {
            confirmDeletedInDb()
        }
    }

    fun confirmDeletedInDb() {
        viewModelScope.launch {
            if (pendingDeleteIds.isNotEmpty()) {
                mediaRepository.markDeleted(pendingDeleteIds)
                pendingDeleteIds = emptyList()
            }
            clearSelection()
            closeDetail()
            _pendingDeleteRequest.value = null
        }
    }

    fun cancelDeleteRequest() {
        pendingDeleteIds = emptyList()
        _pendingDeleteRequest.value = null
    }

    fun refreshMediaStore() {
        viewModelScope.launch {
            _isRefreshing.value = true
            app.mediaReconciler.reconcile(forceFullScan = true)
            app.retentionScanner.scanAndMarkExpired()
            _isRefreshing.value = false
        }
    }
}
