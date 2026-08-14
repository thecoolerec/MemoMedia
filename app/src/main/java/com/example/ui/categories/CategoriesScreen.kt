package com.example.ui.categories

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.enum.NotificationMode
import com.example.core.model.Category
import com.example.core.model.SourceRule
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "分类与规则管理",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTabIndex == 0) {
                        viewModel.openCreateCategoryDialog()
                    } else {
                        viewModel.openCreateRuleDialog()
                    }
                },
                modifier = Modifier.testTag("add_category_or_rule_fab"),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加")
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("分类列表 (${state.categoryStats.size})") },
                    icon = { Icon(Icons.Default.Category, contentDescription = null) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("分流规则 (${state.sourceRules.size})") },
                    icon = { Icon(Icons.Default.Rule, contentDescription = null) }
                )
            }

            if (selectedTabIndex == 0) {
                CategoriesListTab(
                    categoryStats = state.categoryStats,
                    onEdit = { viewModel.openEditCategoryDialog(it) },
                    onDelete = { viewModel.deleteCategory(it.id) }
                )
            } else {
                RulesListTab(
                    rules = state.sourceRules,
                    categories = state.categoryStats.map { it.category },
                    onToggleActive = { viewModel.toggleRuleActive(it) },
                    onDelete = { viewModel.deleteRule(it) }
                )
            }
        }
    }

    if (state.isCreateCategoryDialogOpen) {
        CategoryEditorDialog(
            category = null,
            onDismiss = { viewModel.closeCreateCategoryDialog() },
            onSave = { name, retention, icon ->
                viewModel.saveCategory(name, retention, icon)
            }
        )
    }

    if (state.isEditCategoryDialogOpen && state.editingCategory != null) {
        CategoryEditorDialog(
            category = state.editingCategory,
            onDismiss = { viewModel.closeEditCategoryDialog() },
            onSave = { name, retention, icon ->
                viewModel.updateCategory(state.editingCategory!!.id, name, retention, icon)
            }
        )
    }

    if (state.isCreateRuleDialogOpen) {
        RuleEditorDialog(
            categories = state.categoryStats.map { it.category },
            onDismiss = { viewModel.closeCreateRuleDialog() },
            onSave = { name, pkg, path, catId, notif, autoClassify ->
                viewModel.saveSourceRule(name, pkg, path, catId, notif, autoClassify)
            }
        )
    }
}

@Composable
private fun CategoriesListTab(
    categoryStats: List<CategoryWithStats>,
    onEdit: (Category) -> Unit,
    onDelete: (Category) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(categoryStats, key = { it.category.id }) { item ->
            CategoryCard(
                item = item,
                onEdit = { onEdit(item.category) },
                onDelete = { onDelete(item.category) }
            )
        }
        item {
            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun CategoryCard(
    item: CategoryWithStats,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val category = item.category
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("category_item_${category.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.icon?.ifBlank { "📁" } ?: "📁",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (category.isSystem) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "系统默认",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${item.mediaCount} 项 (${formatSize(item.totalSizeBytes)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (category.retentionDays != null) "保留 ${category.retentionDays} 天" else "永久保留",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (category.retentionDays != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑分类")
            }

            if (!category.isSystem) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除分类",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun RulesListTab(
    rules: List<SourceRule>,
    categories: List<Category>,
    onToggleActive: (SourceRule) -> Unit,
    onDelete: (SourceRule) -> Unit
) {
    if (rules.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "暂无自动分流规则",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "可设置如“微信图片自动归入临时，静默通知”等规则",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(rules, key = { it.id }) { rule ->
                val targetCat = categories.find { it.id == rule.targetCategoryId }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rule.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (!rule.sourcePackage.isNullOrBlank()) {
                                Text(
                                    text = "来源应用: ${rule.sourcePackage}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!rule.relativePathPattern.isNullOrBlank()) {
                                Text(
                                    text = "路径匹配: ${rule.relativePathPattern}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "目标分类: ${targetCat?.name ?: "未设置"} | 通知: ${rule.notificationMode?.name ?: "默认"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { onToggleActive(rule) }
                        )

                        IconButton(onClick = { onDelete(rule) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除规则",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditorDialog(
    category: Category?,
    onDismiss: () -> Unit,
    onSave: (name: String, retentionDays: Int?, icon: String) -> Unit
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var icon by remember { mutableStateOf(category?.icon ?: "📁") }
    var retentionDaysText by remember { mutableStateOf(category?.retentionDays?.toString() ?: "") }
    var isPermanent by remember { mutableStateOf(category?.retentionDays == null) }

    val presetIcons = listOf("🌟", "💼", "⏳", "📸", "📝", "🛒", "🎨", "📁", "🔒", "💡")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (category == null) "新建分类" else "编辑分类")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("分类名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "选择图标",
                    style = MaterialTheme.typography.labelMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetIcons.take(5).forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (icon == emoji) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { icon = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetIcons.drop(5).forEach { emoji ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (icon == emoji) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { icon = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(emoji)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "永久保留",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = isPermanent,
                        onCheckedChange = {
                            isPermanent = it
                            if (it) retentionDaysText = ""
                        }
                    )
                }

                if (!isPermanent) {
                    OutlinedTextField(
                        value = retentionDaysText,
                        onValueChange = { retentionDaysText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("保留天数 (过期自动进入回收建议)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val days = if (isPermanent) null else retentionDaysText.toIntOrNull()
                    if (name.isNotBlank()) {
                        onSave(name.trim(), days, icon)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditorDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        sourcePackage: String?,
        relativePathPattern: String?,
        targetCategoryId: Long?,
        notificationMode: NotificationMode,
        autoClassify: Boolean
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var sourcePackage by remember { mutableStateOf("") }
    var relativePathPattern by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id) }
    var notificationMode by remember { mutableStateOf(NotificationMode.SILENT) }
    var autoClassify by remember { mutableStateOf(true) }

    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建分流规则") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称 (如: 微信图片归档)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = relativePathPattern,
                    onValueChange = { relativePathPattern = it },
                    label = { Text("路径匹配 (如: Pictures/WeiXin)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = sourcePackage,
                    onValueChange = { sourcePackage = it },
                    label = { Text("来源应用包名 (可选)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = categoryDropdownExpanded,
                    onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = categories.find { it.id == selectedCategoryId }?.name ?: "选择目标分类",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("自动归入分类") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text("${cat.icon ?: "📁"} ${cat.name}") },
                                onClick = {
                                    selectedCategoryId = cat.id
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("自动静默分类 (不打扰通知)")
                    Switch(
                        checked = notificationMode == NotificationMode.SILENT,
                        onCheckedChange = {
                            notificationMode = if (it) NotificationMode.SILENT else NotificationMode.HEADS_UP
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), sourcePackage, relativePathPattern, selectedCategoryId, notificationMode, autoClassify)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("添加规则")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
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
