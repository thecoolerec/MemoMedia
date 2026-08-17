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
    private var activeBatchAssets = listOf<MediaAsset>()
    private var remainingAssets = listOf<MediaAsset>()

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
                app.classifyMediaUseCase(id, category.id)
            }
            clearSelection()
        }
    }

    fun assignCategoryToSingle(asset: MediaAsset, category: Category) {
        viewModelScope.launch {
            app.classifyMediaUseCase(asset.id, category.id)
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
        viewModelScope.launch {
            val result = app.deleteMediaUseCase.execute(listOf(asset))
            handleDeleteResult(result)
        }
    }

    fun deleteSelected() {
        val selectedIds = _selectedMediaIds.value.toList()
        viewModelScope.launch {
            val targetAssets = uiState.value.items.filter { selectedIds.contains(it.id) }
            val result = app.deleteMediaUseCase.execute(targetAssets)
            handleDeleteResult(result)
        }
    }

    fun confirmDeletedInDb() {
        viewModelScope.launch {
            val result = app.deleteMediaUseCase.onUserConsentResult(activeBatchAssets, remainingAssets, true)
            handleDeleteResult(result)
        }
    }

    fun cancelDeleteRequest() {
        viewModelScope.launch {
            app.deleteMediaUseCase.onUserConsentResult(activeBatchAssets, remainingAssets, false)
            activeBatchAssets = emptyList()
            remainingAssets = emptyList()
            _pendingDeleteRequest.value = null
        }
    }

    private fun handleDeleteResult(result: com.example.domain.DeleteMediaResult) {
        when (result) {
            is com.example.domain.DeleteMediaResult.NeedsUserConsent -> {
                activeBatchAssets = result.activeBatchAssets
                remainingAssets = result.remainingAssets
                _pendingDeleteRequest.value = result.intentSenderRequest
            }
            is com.example.domain.DeleteMediaResult.Success -> {
                activeBatchAssets = emptyList()
                remainingAssets = emptyList()
                _pendingDeleteRequest.value = null
                clearSelection()
                closeDetail()
            }
            is com.example.domain.DeleteMediaResult.Failure -> {
                activeBatchAssets = emptyList()
                remainingAssets = emptyList()
                _pendingDeleteRequest.value = null
                clearSelection()
            }
        }
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
