package com.example.ui.inbox

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.core.model.CaptureSession
import com.example.core.model.Category
import com.example.core.model.MediaAsset
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.MediaDetailDialog
import com.example.ui.components.getCategoryColor
import com.example.ui.components.getCategoryIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: InboxViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "待整理收件箱",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "共 ${state.totalPendingCount} 项新媒体待归类",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier.testTag("btn_refresh_inbox")
                    ) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新")
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
                    title = "太棒了，所有照片都已整理！",
                    description = "新拍摄或截图的照片会自动汇聚在此处，支持批量一键归类与自动留存管理。",
                    actionLabel = "同步新媒体",
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
                // Render session groups
                items(
                    items = state.pendingSessions,
                    key = { "session_${it.session.id}" }
                ) { sessionWithMedia ->
                    CaptureSessionCard(
                        sessionWithMedia = sessionWithMedia,
                        categories = state.categories,
                        onClassifySession = { category ->
                            viewModel.classifySession(sessionWithMedia.session.id, category)
                        },
                        onItemClick = { asset ->
                            viewModel.openDetail(asset)
                        }
                    )
                }

                // Render individual/orphan pending items
                if (state.orphanPendingMedia.isNotEmpty()) {
                    item {
                        Text(
                            text = "单张媒体",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }

                    items(
                        items = state.orphanPendingMedia,
                        key = { "orphan_${it.id}" }
                    ) { asset ->
                        SinglePendingMediaCard(
                            asset = asset,
                            categories = state.categories,
                            onClassify = { category ->
                                viewModel.classifySingleMedia(asset.id, category)
                            },
                            onClick = { viewModel.openDetail(asset) }
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
            tags = emptyList(),
            onDismiss = { viewModel.closeDetail() },
            onChangeCategory = { category ->
                viewModel.classifySingleMedia(asset.id, category)
                viewModel.closeDetail()
            },
            onAddTag = {},
            onRemoveTag = {},
            onDeleteAsset = { viewModel.closeDetail() }
        )
    }
}

@Composable
private fun CaptureSessionCard(
    sessionWithMedia: SessionWithMedia,
    categories: List<Category>,
    onClassifySession: (Category) -> Unit,
    onItemClick: (MediaAsset) -> Unit
) {
    val session = sessionWithMedia.session
    val items = sessionWithMedia.mediaItems
    val dateFormat = remember { SimpleDateFormat("HH:mm · MM月dd日", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("capture_session_card_${session.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${getSourceTitle(session.sourcePackage)} · 连拍/批量汇聚",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${dateFormat.format(Date(session.startedAt))} · 共 ${items.size} 张",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Horizontal Scrollable Thumbnail Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { asset ->
                    AsyncImage(
                        model = Uri.parse(asset.contentUri),
                        contentDescription = asset.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onItemClick(asset) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Quick Classify Buttons
            Text(
                text = "整组一键归类：",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onClassifySession(category) }
                            .testTag("btn_session_${session.id}_classify_${category.id}"),
                        color = getCategoryColor(category.name).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = getCategoryIcon(category.icon),
                                contentDescription = category.name,
                                tint = getCategoryColor(category.name),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = getCategoryColor(category.name)
                            )
                        }
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("single_pending_card_${asset.id}"),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
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
                    .size(60.dp)
                    .clip(RoundedCornerShape(10.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = asset.displayName ?: "未命名媒体",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = asset.bucketName ?: "相册",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { category ->
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
        pkg.contains("screenshot", ignoreCase = true) -> "截图"
        else -> "第三方应用"
    }
}
