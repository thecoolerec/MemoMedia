package com.example.ui.categories

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LocalMediaApplication
import com.example.core.enum.NotificationMode
import com.example.core.model.Category
import com.example.core.model.MediaAsset
import com.example.core.model.SourceRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryWithStats(
    val category: Category,
    val mediaCount: Int,
    val totalSizeBytes: Long,
    val previewMedia: List<MediaAsset> = emptyList()
)

data class CategoriesUiState(
    val categoryStats: List<CategoryWithStats> = emptyList(),
    val sourceRules: List<SourceRule> = emptyList(),
    val isCreateCategoryDialogOpen: Boolean = false,
    val isEditCategoryDialogOpen: Boolean = false,
    val editingCategory: Category? = null,
    val isCreateRuleDialogOpen: Boolean = false
)

class CategoriesViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as LocalMediaApplication
    private val categoryRepository = app.categoryRepository
    private val mediaRepository = app.mediaRepository
    private val sourceRuleRepository = app.sourceRuleRepository

    private val _isCreateCategoryDialogOpen = MutableStateFlow(false)
    private val _isEditCategoryDialogOpen = MutableStateFlow(false)
    private val _editingCategory = MutableStateFlow<Category?>(null)
    private val _isCreateRuleDialogOpen = MutableStateFlow(false)

    // Combine data flows first
    private val dataFlow = combine(
        categoryRepository.observeAll(),
        mediaRepository.observeTimeline(),
        sourceRuleRepository.observeAll()
    ) { categories, mediaList, rules ->
        val stats = categories.map { cat ->
            val items = mediaList.filter { it.primaryCategoryId == cat.id }
            val totalSize = items.sumOf { it.sizeBytes ?: 0L }
            CategoryWithStats(
                category = cat,
                mediaCount = items.size,
                totalSizeBytes = totalSize,
                previewMedia = items.take(4)
            )
        }
        Pair(stats, rules)
    }

    // Combine dialog states
    private val dialogFlow = combine(
        _isCreateCategoryDialogOpen,
        _isEditCategoryDialogOpen,
        _editingCategory,
        _isCreateRuleDialogOpen
    ) { isCreateCat, isEditCat, editCat, isCreateRule ->
        DialogState(isCreateCat, isEditCat, editCat, isCreateRule)
    }

    private data class DialogState(
        val isCreateCategory: Boolean,
        val isEditCategory: Boolean,
        val editingCategory: Category?,
        val isCreateRule: Boolean
    )

    val uiState: StateFlow<CategoriesUiState> = combine(
        dataFlow,
        dialogFlow
    ) { (stats, rules), dialogState ->
        CategoriesUiState(
            categoryStats = stats,
            sourceRules = rules,
            isCreateCategoryDialogOpen = dialogState.isCreateCategory,
            isEditCategoryDialogOpen = dialogState.isEditCategory,
            editingCategory = dialogState.editingCategory,
            isCreateRuleDialogOpen = dialogState.isCreateRule
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CategoriesUiState()
    )

    fun openCreateCategoryDialog() {
        _isCreateCategoryDialogOpen.value = true
    }

    fun closeCreateCategoryDialog() {
        _isCreateCategoryDialogOpen.value = false
    }

    fun openEditCategoryDialog(category: Category) {
        _editingCategory.value = category
        _isEditCategoryDialogOpen.value = true
    }

    fun closeEditCategoryDialog() {
        _editingCategory.value = null
        _isEditCategoryDialogOpen.value = false
    }

    fun saveCategory(name: String, retentionDays: Int?, icon: String) {
        viewModelScope.launch {
            val newCat = Category(
                name = name,
                retentionDays = retentionDays,
                icon = icon,
                isSystem = false
            )
            categoryRepository.save(newCat)
            closeCreateCategoryDialog()
        }
    }

    fun updateCategory(id: Long, name: String, retentionDays: Int?, icon: String) {
        viewModelScope.launch {
            val existing = categoryRepository.getById(id)
            if (existing != null) {
                val updated = existing.copy(
                    name = name,
                    retentionDays = retentionDays,
                    icon = icon
                )
                categoryRepository.update(updated)
            }
            closeEditCategoryDialog()
        }
    }

    fun deleteCategory(categoryId: Long) {
        viewModelScope.launch {
            categoryRepository.delete(categoryId)
        }
    }

    fun openCreateRuleDialog() {
        _isCreateRuleDialogOpen.value = true
    }

    fun closeCreateRuleDialog() {
        _isCreateRuleDialogOpen.value = false
    }

    fun saveSourceRule(
        name: String,
        sourcePackage: String?,
        relativePathPattern: String?,
        targetCategoryId: Long?,
        notificationMode: NotificationMode,
        autoClassify: Boolean
    ) {
        viewModelScope.launch {
            val rule = SourceRule(
                name = name,
                sourcePackage = sourcePackage?.ifBlank { null },
                relativePathPattern = relativePathPattern?.ifBlank { null },
                targetCategoryId = targetCategoryId,
                notificationMode = notificationMode,
                autoClassify = autoClassify,
                enabled = true
            )
            sourceRuleRepository.save(rule)
            closeCreateRuleDialog()
        }
    }

    fun toggleRuleActive(rule: SourceRule) {
        viewModelScope.launch {
            sourceRuleRepository.update(rule.copy(enabled = !rule.enabled))
        }
    }

    fun deleteRule(rule: SourceRule) {
        viewModelScope.launch {
            sourceRuleRepository.delete(rule)
        }
    }
}
