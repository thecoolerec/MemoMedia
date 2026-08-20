package com.example.ui.inbox

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.core.model.Category
import com.example.core.model.MediaAsset
import com.example.core.model.MediaSourceKind
import com.example.ui.components.AppleToolbarButton
import com.example.ui.components.CategoryPickerSheet
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MediaViewer
import com.example.ui.components.getCategoryColor
import com.example.ui.components.getCategoryIcon
import com.example.ui.util.formatRelativeTime
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: InboxViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var categoryPickerGroup by remember { mutableStateOf<PendingSourceGroup?>(null) }
    var activeViewerList by remember { mutableStateOf<List<MediaAsset>>(emptyList()) }
    var activeViewerIndex by remember { mutableIntStateOf(-1) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "待整理",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                actions = {
                    if (state.isRefreshing) {
                        Box(
                            modifier = Modifier.size(44.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    } else {
                        AppleToolbarButton(
                            icon = Icons.Default.Refresh,
                            contentDescription = "重新扫描媒体库",
                            onClick = viewModel::refresh,
                            testTag = "btn_refresh_inbox"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (state.totalPendingCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    icon = Icons.Default.CheckCircle,
                    title = "已经整理完了",
                    description = "新出现且尚未归类的照片和视频会显示在这里。",
                    actionLabel = if (state.isRefreshing) null else "重新扫描媒体库",
                    onActionClick = { viewModel.refresh() }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("inbox_source_list"),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = state.sourceGroups,
                    key = { "source_${it.source.key}" }
                ) { group ->
                    var visible by remember(group.source.key) { mutableStateOf(true) }
                    AnimatedVisibility(
                        visible = visible,
                        exit = fadeOut(tween(220)) + shrinkVertically(tween(220))
                    ) {
                        SourceGroupCard(
                            group = group,
                            categories = state.categories,
                            onClassifyGroup = { category ->
                                visible = false
                                viewModel.classifySourceGroup(group, category)
                                val retention = category.retentionDays?.let { " · 保留 $it 天" }.orEmpty()
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "${group.mediaItems.size} 项已归入「${category.name}」$retention"
                                    )
                                }
                            },
                            onOpenAllCategories = { categoryPickerGroup = group },
                            onItemClick = { index ->
                                activeViewerList = group.mediaItems
                                activeViewerIndex = index
                            }
                        )
                    }
                }
            }
        }
    }

    if (activeViewerIndex in activeViewerList.indices) {
        MediaViewer(
            items = activeViewerList,
            initialIndex = activeViewerIndex,
            categories = state.categories,
            tags = emptyList(),
            onDismiss = { activeViewerIndex = -1 },
            onChangeCategory = { asset, category ->
                viewModel.classifySingleMedia(asset.id, category)
                val retention = category.retentionDays?.let { " · 保留 $it 天" }.orEmpty()
                scope.launch {
                    snackbarHostState.showSnackbar("已归入「${category.name}」$retention")
                }
            },
            onAddTag = { _, _ -> },
            onRemoveTag = { _, _ -> },
            onDeleteAsset = { activeViewerIndex = -1 }
        )
    }

    categoryPickerGroup?.let { group ->
        CategoryPickerSheet(
            title = "归类 ${group.mediaItems.size} 项${group.source.title}媒体",
            subtitle = "这一组来自 ${group.source.detail ?: group.source.title}",
            categories = state.categories,
            onSelectCategory = { category ->
                viewModel.classifySourceGroup(group, category)
                categoryPickerGroup = null
                val retention = category.retentionDays?.let { " · 保留 $it 天" }.orEmpty()
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "${group.mediaItems.size} 项已归入「${category.name}」$retention"
                    )
                }
            },
            onCreateCategory = {},
            onDismiss = { categoryPickerGroup = null }
        )
    }
}

@Composable
private fun SourceGroupCard(
    group: PendingSourceGroup,
    categories: List<Category>,
    onClassifyGroup: (Category) -> Unit,
    onOpenAllCategories: () -> Unit,
    onItemClick: (Int) -> Unit
) {
    val source = group.source
    val items = group.mediaItems

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("source_group_${source.key}"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            SourceGroupHeader(group)

            Spacer(modifier = Modifier.height(12.dp))

            ThumbnailStrip(
                items = items,
                onItemClick = onItemClick
            )

            Spacer(modifier = Modifier.height(12.dp))

            QuickCategoryRow(
                categories = categories,
                onClassify = onClassifyGroup,
                onMore = onOpenAllCategories
            )
        }
    }
}

@Composable
private fun SourceGroupHeader(group: PendingSourceGroup) {
    val source = group.source
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = sourceIcon(source.kind),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val detail = source.detail?.trim('/')
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${group.mediaItems.size} 项",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = formatRelativeTime(group.latestAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThumbnailStrip(
    items: List<MediaAsset>,
    onItemClick: (Int) -> Unit
) {
    val displayItems = items.take(4)
    val overflowCount = items.size - displayItems.size

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        displayItems.forEachIndexed { index, asset ->
            val showOverflow = index == displayItems.lastIndex && overflowCount > 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(78.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onItemClick(index) }
            ) {
                AsyncImage(
                    model = Uri.parse(asset.contentUri),
                    contentDescription = asset.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (showOverflow) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.48f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+$overflowCount",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickCategoryRow(
    categories: List<Category>,
    onClassify: (Category) -> Unit,
    onMore: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        categories.take(3).forEach { category ->
            val categoryColor = getCategoryColor(category.name)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onClassify(category) },
                color = categoryColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = getCategoryIcon(category.icon),
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = categoryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onMore),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "更多分类",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun sourceIcon(kind: MediaSourceKind): ImageVector = when (kind) {
    MediaSourceKind.CAMERA -> Icons.Default.CameraAlt
    MediaSourceKind.WECHAT,
    MediaSourceKind.QQ -> Icons.AutoMirrored.Filled.Chat
    MediaSourceKind.SCREENSHOT -> Icons.Default.CropSquare
    MediaSourceKind.DOWNLOADS -> Icons.Default.Download
    MediaSourceKind.OTHER -> Icons.Default.Image
}
