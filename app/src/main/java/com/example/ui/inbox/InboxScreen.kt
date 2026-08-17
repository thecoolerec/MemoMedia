package com.example.ui.inbox

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.core.model.Category
import com.example.core.model.MediaAsset
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
    var selectedMoreSession by remember { mutableStateOf<SessionWithMedia?>(null) }
    var showCategoryPickerForSession by remember { mutableStateOf<SessionWithMedia?>(null) }
    var activeViewerList by remember { mutableStateOf<List<MediaAsset>>(emptyList()) }
    var activeViewerIndex by remember { mutableIntStateOf(-1) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "待整理",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.totalPendingCount > 0) {
                            Text(
                                text = "发现新媒体，等待你一键归类",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (state.totalPendingCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    icon = Icons.Default.CheckCircle,
                    title = "暂无待整理内容",
                    description = "同步后，尚未分类的照片和视频会显示在这里。",
                    actionLabel = "同步媒体库",
                    onActionClick = { viewModel.refresh() }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("inbox_session_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Pending Capture Sessions
                items(
                    items = state.pendingSessions,
                    key = { "session_${it.session.id}" }
                ) { sessionWithMedia ->
                    var isVisible by remember { mutableStateOf(true) }

                    AnimatedVisibility(
                        visible = isVisible,
                        exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
                    ) {
                        CaptureSessionCard(
                            sessionWithMedia = sessionWithMedia,
                            categories = state.categories,
                            onClassifySession = { category ->
                                isVisible = false
                                viewModel.classifySession(sessionWithMedia.session.id, category)
                                val count = sessionWithMedia.mediaItems.size
                                val retentionInfo = if (category.retentionDays != null) " · 保留 ${category.retentionDays} 天" else ""
                                scope.launch {
                                    snackbarHostState.showSnackbar("${count}项已归入「${category.name}」$retentionInfo")
                                }
                            },
                            onOpenMore = {
                                selectedMoreSession = sessionWithMedia
                            },
                            onOpenAllCategories = {
                                showCategoryPickerForSession = sessionWithMedia
                            },
                            onItemClick = { asset, index ->
                                activeViewerList = sessionWithMedia.mediaItems
                                activeViewerIndex = index
                            }
                        )
                    }
                }

                // Orphan Pending Items (Individual photos not grouped in session)
                if (state.orphanPendingMedia.isNotEmpty()) {
                    item {
                        Text(
                            text = "单张未归类",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    items(
                        items = state.orphanPendingMedia,
                        key = { "orphan_${it.id}" }
                    ) { asset ->
                        var isVisible by remember { mutableStateOf(true) }

                        AnimatedVisibility(
                            visible = isVisible,
                            exit = fadeOut(tween(250)) + shrinkVertically(tween(250))
                        ) {
                            SinglePendingMediaCard(
                                asset = asset,
                                categories = state.categories,
                                onClassify = { category ->
                                    isVisible = false
                                    viewModel.classifySingleMedia(asset.id, category)
                                    val retentionInfo = if (category.retentionDays != null) " · 保留 ${category.retentionDays} 天" else ""
                                    scope.launch {
                                        snackbarHostState.showSnackbar("已归入「${category.name}」$retentionInfo")
                                    }
                                },
                                onClick = {
                                    activeViewerList = state.orphanPendingMedia
                                    activeViewerIndex = state.orphanPendingMedia.indexOf(asset).coerceAtLeast(0)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Media Viewer
    if (activeViewerIndex in activeViewerList.indices) {
        MediaViewer(
            items = activeViewerList,
            initialIndex = activeViewerIndex,
            categories = state.categories,
            tags = emptyList(),
            onDismiss = { activeViewerIndex = -1 },
            onChangeCategory = { asset, category ->
                viewModel.classifySingleMedia(asset.id, category)
                val retentionInfo = if (category.retentionDays != null) " · 保留 ${category.retentionDays} 天" else ""
                scope.launch {
                    snackbarHostState.showSnackbar("已归入「${category.name}」$retentionInfo")
                }
            },
            onAddTag = { _, _ -> },
            onRemoveTag = { _, _ -> },
            onDeleteAsset = { activeViewerIndex = -1 }
        )
    }

    // Full Category Picker Sheet for Session
    showCategoryPickerForSession?.let { sessionWithMedia ->
        CategoryPickerSheet(
            title = "归类 ${sessionWithMedia.mediaItems.size} 项媒体",
            subtitle = "选择目标分类，自动应用对应的生命周期策略",
            categories = state.categories,
            onSelectCategory = { category ->
                viewModel.classifySession(sessionWithMedia.session.id, category)
                showCategoryPickerForSession = null
                val count = sessionWithMedia.mediaItems.size
                val retentionInfo = if (category.retentionDays != null) " · 保留 ${category.retentionDays} 天" else ""
                scope.launch {
                    snackbarHostState.showSnackbar("${count}项已归入「${category.name}」$retentionInfo")
                }
            },
            onCreateCategory = {},
            onDismiss = { showCategoryPickerForSession = null }
        )
    }
}

@Composable
private fun CaptureSessionCard(
    sessionWithMedia: SessionWithMedia,
    categories: List<Category>,
    onClassifySession: (Category) -> Unit,
    onOpenMore: () -> Unit,
    onOpenAllCategories: () -> Unit,
    onItemClick: (MediaAsset, Int) -> Unit
) {
    val session = sessionWithMedia.session
    val items = sessionWithMedia.mediaItems
    var showDropdown by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("capture_session_card_${session.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Source Icon + App Name + Time · N items > + More menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getSourceIcon(session.sourcePackage),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = getSourceTitle(session.sourcePackage),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${formatRelativeTime(session.startedAt)} · ${items.size} 项",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showDropdown = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "更多选项",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    DropdownMenu(
                        expanded = showDropdown,
                        onDismissRequest = { showDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("所有分类...") },
                            onClick = {
                                showDropdown = false
                                onOpenAllCategories()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("查看首张详情") },
                            onClick = {
                                showDropdown = false
                                if (items.isNotEmpty()) {
                                    onItemClick(items[0], 0)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Thumbnail Row (Max 4 displayed, 4th has +N overlay if total > 4)
            val displayItems = items.take(4)
            val overflowCount = items.size - 4

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                displayItems.forEachIndexed { index, asset ->
                    val isLastAndOverflow = index == 3 && overflowCount > 0

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onItemClick(asset, index) }
                    ) {
                        AsyncImage(
                            model = Uri.parse(asset.contentUri),
                            contentDescription = asset.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                        )

                        if (isLastAndOverflow) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .background(Color.Black.copy(alpha = 0.55f)),
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

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Category Action Chips Row (Max 3 categories with retention text + "••• 更多")
            val topCategories = categories.take(3)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                topCategories.forEach { category ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onClassifySession(category) }
                            .testTag("btn_session_${session.id}_classify_${category.id}"),
                        color = getCategoryColor(category.name).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = getCategoryIcon(category.icon),
                                contentDescription = category.name,
                                tint = getCategoryColor(category.name),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val label = if (category.retentionDays != null) {
                                "${category.name} ${category.retentionDays}天"
                            } else {
                                category.name
                            }
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = getCategoryColor(category.name),
                                maxLines = 1
                            )
                        }
                    }
                }

                // "•••" More Categories Button
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onOpenAllCategories() }
                        .testTag("btn_session_${session.id}_more_categories"),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreHoriz,
                            contentDescription = "更多分类",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SinglePendingMediaCard(
    asset: MediaAsset,
    categories: List<Category>,
    onClassify: (Category) -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("single_pending_card_${asset.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = Uri.parse(asset.contentUri),
                contentDescription = asset.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.displayName ?: "新拍摄媒体",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = "${asset.bucketName ?: "相册"} · ${asset.capturedAt?.let { formatRelativeTime(it) } ?: "刚刚"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.take(3).forEach { category ->
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onClassify(category) },
                            color = getCategoryColor(category.name).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = category.name,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = getCategoryColor(category.name)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getSourceTitle(pkg: String?): String {
    return when {
        pkg == null -> "系统相机"
        pkg.contains("camera", ignoreCase = true) -> "系统相机"
        pkg.contains("tencent.mm", ignoreCase = true) -> "微信"
        pkg.contains("tencent.mobileqq", ignoreCase = true) -> "QQ"
        pkg.contains("screenshot", ignoreCase = true) -> "屏幕截图"
        else -> "新媒体"
    }
}

private fun getSourceIcon(pkg: String?): ImageVector {
    return when {
        pkg == null -> Icons.Default.CameraAlt
        pkg.contains("camera", ignoreCase = true) -> Icons.Default.CameraAlt
        pkg.contains("tencent.mm", ignoreCase = true) || pkg.contains("tencent.mobileqq", ignoreCase = true) -> Icons.AutoMirrored.Filled.Chat
        pkg.contains("screenshot", ignoreCase = true) -> Icons.Default.CropSquare
        else -> Icons.Default.Image
    }
}
