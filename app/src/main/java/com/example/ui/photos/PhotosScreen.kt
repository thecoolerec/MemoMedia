package com.example.ui.photos

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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.core.enum.MediaStatus
import com.example.ui.components.CategoryPickerSheet
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MediaDetailDialog
import com.example.ui.components.MediaThumbnail
import com.example.ui.components.getCategoryColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    viewModel: PhotosViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showBatchCategoryPicker by remember { mutableStateOf(false) }

    val categoriesMap = remember(state.categories) {
        state.categories.associateBy { it.id }
    }

    val pendingCount = remember(state.items) {
        state.items.count { it.status == MediaStatus.PENDING }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (state.isSelectionMode) "已选择 ${state.selectedMediaIds.size} 项" else "相册图库",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (!state.isSelectionMode) {
                            Text(
                                text = "共 ${state.items.size} 项媒体 · ${state.filteredItems.size} 项显示",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (state.isSelectionMode) {
                        IconButton(
                            onClick = { viewModel.selectAll() },
                            modifier = Modifier.testTag("btn_select_all")
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "全选")
                        }
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.testTag("btn_cancel_selection")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "取消选择")
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.refreshMediaStore() },
                            modifier = Modifier.testTag("btn_refresh_media")
                        ) {
                            if (state.isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "同步媒体")
                            }
                        }
                        IconButton(
                            onClick = { viewModel.toggleSelectionMode() },
                            modifier = Modifier.testTag("btn_toggle_selection")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "批量选择")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Batch Action Bottom Bar
            if (state.isSelectionMode && state.selectedMediaIds.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("batch_action_bar"),
                    tonalElevation = 8.dp,
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
            // Category Filter Chip Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "All" Chip
                FilterChip(
                    selected = state.selectedCategoryId == null,
                    onClick = { viewModel.selectCategoryFilter(null) },
                    label = { Text("全部 (${state.items.size})") },
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
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.error, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$pendingCount",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag("filter_chip_pending")
                )

                // Categories Chips
                state.categories.forEach { category ->
                    val isSelected = state.selectedCategoryId == category.id
                    val count = state.items.count { it.primaryCategoryId == category.id }

                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectCategoryFilter(category.id) },
                        label = { Text("${category.name} ($count)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = getCategoryColor(category.name).copy(alpha = 0.25f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("filter_chip_${category.id}")
                    )
                }
            }

            // Photos Grid
            if (state.filteredItems.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Default.PhotoLibrary,
                    title = if (state.selectedCategoryId == -1L) "待整理箱已清空" else "相册为空",
                    description = if (state.selectedCategoryId == -1L)
                        "所有媒体已整理完毕，尽享井井有条的本地媒体库！"
                    else
                        "媒体库暂无此分类的照片。点击右上角刷新按钮可从系统 MediaStore 重新同步。",
                    actionLabel = "重新扫描图库",
                    onActionClick = { viewModel.refreshMediaStore() }
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("photos_grid"),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = state.filteredItems,
                        key = { it.id }
                    ) { asset ->
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

    // Detail Dialog
    state.selectedAssetForDetail?.let { asset ->
        MediaDetailDialog(
            asset = asset,
            categories = state.categories,
            tags = state.selectedAssetTags,
            onDismiss = { viewModel.closeDetail() },
            onChangeCategory = { category -> viewModel.assignCategoryToSingle(asset, category) },
            onAddTag = { tagName -> viewModel.addTagToDetail(tagName) },
            onRemoveTag = { tagId -> viewModel.removeTagFromDetail(tagId) },
            onDeleteAsset = { viewModel.deleteAsset(asset) }
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
            },
            onCreateCategory = { /* handled in categories screen */ },
            onDismiss = { showBatchCategoryPicker = false }
        )
    }
}
