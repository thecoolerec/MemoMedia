package com.example.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoDelete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.MediaAsset
import com.example.ui.components.MediaThumbnail
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", style = MaterialTheme.typography.headlineSmall) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                StorageSummaryCard(
                    state = state,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item { SettingsSectionHeader("媒体管理") }
            item {
                SettingsRow(
                    icon = Icons.Default.Refresh,
                    title = "媒体库同步",
                    subtitle = if (state.isScanning) "正在重新读取设备媒体" else "重新扫描照片和视频",
                    value = if (state.isScanning) null else "立即同步",
                    onClick = if (state.isScanning) null else viewModel::triggerFullScan,
                    modifier = Modifier.testTag("rescan_mediastore_button"),
                    trailingContent = if (state.isScanning) {
                        { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
                    } else null
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.Storage,
                    title = "存储占用",
                    subtitle = "${state.totalMediaCount} 项媒体",
                    value = formatSize(state.totalSizeBytes)
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.AutoDelete,
                    title = "过期媒体",
                    subtitle = "按分类保留策略检查",
                    value = when {
                        state.isRetentionScanning -> "扫描中"
                        state.expiredMediaItems.isNotEmpty() -> "${state.expiredMediaItems.size} 项"
                        else -> "检查"
                    },
                    onClick = if (state.isRetentionScanning) null else viewModel::triggerRetentionScan,
                    modifier = Modifier.testTag("scan_expired_media_button")
                )
            }

            item { SettingsSectionHeader("自动整理") }
            item {
                SettingsRow(
                    icon = Icons.Default.Layers,
                    title = "后台监听",
                    subtitle = "发现新媒体后自动加入待整理",
                    trailingContent = {
                        Switch(
                            checked = state.isServiceRunning,
                            onCheckedChange = viewModel::toggleBackgroundService
                        )
                    }
                )
                SettingsDivider()
                SettingsRow(
                    icon = Icons.Default.PictureInPictureAlt,
                    title = "快速分类悬浮窗",
                    subtitle = if (state.isOverlayPermissionGranted) {
                        "可在其他应用上方快速分类"
                    } else {
                        "需要系统悬浮窗权限"
                    },
                    value = if (state.isOverlayPermissionGranted) "已允许" else "去授权",
                    onClick = if (state.isOverlayPermissionGranted) null else {
                        {
                            if (context is Activity) {
                                context.startActivity(viewModel.getOverlayPermissionIntent())
                            }
                        }
                    }
                )
                SettingsDivider()
                SettingsChoiceGroup(
                    icon = Icons.Default.Timer,
                    title = "图片聚合间隔",
                    subtitle = "连续产生的媒体会合并为一组",
                    options = listOf(5 to "5 秒", 8 to "8 秒", 15 to "15 秒", 30 to "30 秒"),
                    selectedValue = state.aggregationWindowSeconds,
                    onSelect = viewModel::setAggregationWindow
                )
            }

            item { SettingsSectionHeader("通知") }
            item {
                SettingsChoiceGroup(
                    icon = Icons.Default.Notifications,
                    title = "新媒体提醒",
                    subtitle = "选择发现新照片后的提醒方式",
                    options = listOf(
                        "OVERLAY" to "悬浮窗",
                        "NOTIFICATION" to "系统通知",
                        "HEADS_UP" to "强提醒",
                        "SILENT" to "静默"
                    ),
                    selectedValue = state.notificationMode,
                    onSelect = viewModel::setNotificationMode
                )
            }

            item { SettingsSectionHeader("权限与隐私") }
            item {
                SettingsRow(
                    icon = Icons.Default.Security,
                    title = "本地处理说明",
                    subtitle = "照片、分类与规则均保存在设备本地"
                )
            }

            item { SettingsSectionHeader("其他") }
            item {
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = "关于 MemoMedia",
                    value = "1.0"
                )
            }
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.confirmDeletedInDb()
        } else {
            viewModel.cancelDeleteRequest()
        }
    }

    LaunchedEffect(state.pendingDeleteRequest) {
        state.pendingDeleteRequest?.let { deleteLauncher.launch(it) }
    }

    if (state.showExpiredDialog) {
        ExpiredMediaDialog(
            expiredItems = state.expiredMediaItems,
            onDismiss = viewModel::closeExpiredDialog,
            onConfirmDelete = { viewModel.requestDeleteExpiredMedia(state.expiredMediaItems) }
        )
    }
}

@Composable
private fun StorageSummaryCard(state: SettingsUiState, modifier: Modifier = Modifier) {
    val progress = if (state.totalMediaCount > 0) {
        state.classifiedCount.toFloat() / state.totalMediaCount.toFloat()
    } else 0f

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(64.dp),
                    strokeWidth = 6.dp,
                    trackColor = MaterialTheme.colorScheme.surface
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.width(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("媒体库", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${state.totalMediaCount} 项 · ${formatSize(state.totalSizeBytes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${state.classifiedCount} 项已整理 · ${state.unclassifiedCount} 项待整理",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun <T> SettingsChoiceGroup(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    options: List<Pair<T, String>>,
    selectedValue: T,
    onSelect: (T) -> Unit
) {
    SettingsRow(icon = icon, title = title, subtitle = subtitle)
    Column(
        modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.chunked(2).forEach { rowOptions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowOptions.forEach { (value, label) ->
                    val selected = value == selectedValue
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clickable { onSelect(value) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        },
                        contentColor = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (rowOptions.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExpiredMediaDialog(
    expiredItems: List<MediaAsset>,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("过期媒体") },
        text = {
            if (expiredItems.isEmpty()) {
                Text("当前没有需要清理的媒体。")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "以下 ${expiredItems.size} 项媒体已超过分类的保留期限。删除后需要通过系统确认。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(expiredItems, key = { it.id }) { item ->
                            MediaThumbnail(
                                asset = item,
                                onClick = {},
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (expiredItems.isNotEmpty()) {
                Button(
                    onClick = onConfirmDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("删除 ${expiredItems.size} 项") }
            } else {
                Button(onClick = onDismiss) { Text("完成") }
            }
        },
        dismissButton = {
            if (expiredItems.isNotEmpty()) {
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    )
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.getDefault(), "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.getDefault(), "%.1f KB", kb)
        else -> "$bytes B"
    }
}
