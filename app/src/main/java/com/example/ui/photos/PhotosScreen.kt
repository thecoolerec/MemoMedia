package com.example.ui.photos

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.model.needsOrganization
import com.example.ui.components.AppleToolbarButton
import com.example.ui.components.CategoryPickerSheet
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MediaThumbnail
import com.example.ui.components.MediaViewer
import com.example.ui.components.getCategoryColor
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    viewModel: PhotosViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showBatchCategoryPicker by remember { mutableStateOf(false) }
    var activeViewerIndex by remember { mutableIntStateOf(-1) }

    val categoriesMap = remember(state.categories) {
        state.categories.associateBy { it.id }
    }

    val pendingCount = remember(state.items) {
        state.items.count { it.needsOrganization }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.confirmDeletedInDb()
            scope.launch { snackbarHostState.showSnackbar("所选项目已删除") }
        } else {
            viewModel.cancelDeleteRequest()
        }
    }

    androidx.compose.runtime.LaunchedEffect(state.pendingDeleteRequest) {
        val request = state.pendingDeleteRequest
        if (request != null) {
            deleteLauncher.launch(request)
        }
    }

    BackHandler(enabled = state.isSelectionMode || state.isSearchActive) {
        if (state.isSelectionMode) {
            viewModel.clearSelection()
        } else if (state.isSearchActive) {
            viewModel.setSearchActive(false)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.isSearchActive) {
                // Search Bar Top Bar
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.setSearchActive(false) },
                            modifier = Modifier.testTag("btn_exit_search")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "退出搜索"
                            )
                        }

                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("搜索照片名、相册...", fontSize = 14.sp) },
                            singleLine = true,
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "清除")
                                    }
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("input_search_photos")
                        )
                    }
                }
            } else {
                LargeTopAppBar(
                    title = {
                        Text(
                            text = if (state.isSelectionMode) "已选择 ${state.selectedMediaIds.size} 项" else "照片",
                            style = if (state.isSelectionMode) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        if (state.isSelectionMode) {
                            AppleToolbarButton(
                                icon = Icons.Default.SelectAll,
                                contentDescription = "全选",
                                onClick = { viewModel.selectAll() },
                                testTag = "btn_select_all"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AppleToolbarButton(
                                icon = Icons.Default.Close,
                                contentDescription = "取消选择",
                                onClick = { viewModel.clearSelection() },
                                testTag = "btn_cancel_selection"
                            )
                        } else {
                            AppleToolbarButton(
                                icon = Icons.Default.Search,
                                contentDescription = "搜索",
                                onClick = { viewModel.setSearchActive(true) },
                                testTag = "btn_search_photos"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AppleToolbarButton(
                                icon = Icons.Default.Checklist,
                                contentDescription = "批量选择",
                                onClick = { viewModel.toggleSelectionMode() },
                                testTag = "btn_toggle_selection"
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            // Batch Action Bottom Bar
            if (state.isSelectionMode && state.selectedMediaIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("batch_action_bar"),
                    tonalElevation = 6.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showBatchCategoryPicker = true },
                            modifier = Modifier.testTag("btn_batch_classify")
                        ) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("批量归类 (${state.selectedMediaIds.size})")
                        }

                        OutlinedButton(
                            onClick = { viewModel.deleteSelected() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("btn_batch_delete")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("删除")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Category Filter Chip Row (Pill design, no noisy count numbers)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "全部" Chip
                FilterChip(
                    selected = state.selectedCategoryId == null,
                    onClick = { viewModel.selectCategoryFilter(null) },
                    label = { Text("全部") },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("filter_chip_all")
                )

                // "待整理" Chip
                FilterChip(
                    selected = state.selectedCategoryId == -1L,
                    onClick = { viewModel.selectCategoryFilter(-1L) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("待整理")
                            if (pendingCount > 0) {
                                Spacer(modifier = Modifier.width(5.dp))
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("filter_chip_pending")
                )

                // Category Chips
                state.categories.forEach { category ->
                    val isSelected = state.selectedCategoryId == category.id

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectCategoryFilter(category.id) },
                        label = { Text(category.name) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = getCategoryColor(category.name).copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("filter_chip_${category.id}")
                    )
                }
            }

            // Photos Grid (4 columns, 2dp spacing, edge-to-edge feel)
            if (state.filteredItems.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.PhotoLibrary,
                    title = if (state.selectedCategoryId == -1L) "暂无待整理内容" else "暂无照片",
                    description = if (state.selectedCategoryId == -1L)
                        "同步后，尚未分类的照片和视频会显示在这里。"
                    else
                        "暂无符合当前筛选条件的照片。",
                    actionLabel = "同步媒体库",
                    onActionClick = { viewModel.refreshMediaStore() }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("photos_grid"),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(
                        items = state.filteredItems,
                        key = { _, it -> it.id }
                    ) { index, asset ->
                        val isSelected = state.selectedMediaIds.contains(asset.id)
                        val category = asset.primaryCategoryId?.let { categoriesMap[it] }

                        MediaThumbnail(
                            asset = asset,
                            category = category,
                            isSelected = isSelected,
                            isSelectionMode = state.isSelectionMode,
                            onClick = {
                                if (state.isSelectionMode) {
                                    viewModel.toggleItemSelection(asset.id)
                                } else {
                                    activeViewerIndex = index
                                    viewModel.openDetail(asset)
                                }
                            },
                            onLongClick = {
                                if (!state.isSelectionMode) {
                                    viewModel.toggleSelectionMode()
                                }
                                viewModel.toggleItemSelection(asset.id)
                            }
                        )
                    }
                }
            }
        }
    }

    // Full Screen Media Viewer
    if (activeViewerIndex in state.filteredItems.indices) {
        MediaViewer(
            items = state.filteredItems,
            initialIndex = activeViewerIndex,
            categories = state.categories,
            tags = state.selectedAssetTags,
            onDismiss = {
                activeViewerIndex = -1
                viewModel.closeDetail()
            },
            onChangeCategory = { asset, category ->
                viewModel.assignCategoryToSingle(asset, category)
                val retentionInfo = if (category.retentionDays != null) " · 保留 ${category.retentionDays} 天" else ""
                scope.launch {
                    snackbarHostState.showSnackbar("已归入「${category.name}」$retentionInfo")
                }
            },
            onAddTag = { asset, tagName ->
                viewModel.addTagToDetail(tagName)
            },
            onRemoveTag = { asset, tagId ->
                viewModel.removeTagFromDetail(tagId)
            },
            onDeleteAsset = { asset ->
                viewModel.deleteAsset(asset)
                activeViewerIndex = -1
            }
        )
    }

    // Batch Category Picker Sheet
    if (showBatchCategoryPicker) {
        CategoryPickerSheet(
            title = "批量归类 (${state.selectedMediaIds.size} 项)",
            subtitle = "选择目标主分类，系统将自动应用分类的生命周期留存策略",
            categories = state.categories,
            onSelectCategory = { category ->
                viewModel.assignCategoryToSelected(category)
                showBatchCategoryPicker = false
                val retentionInfo = if (category.retentionDays != null) " · 保留 ${category.retentionDays} 天" else ""
                scope.launch {
                    snackbarHostState.showSnackbar("所选项目已归入「${category.name}」$retentionInfo")
                }
            },
            onCreateCategory = { /* handled in categories screen */ },
            onDismiss = { showBatchCategoryPicker = false }
        )
    }
}
