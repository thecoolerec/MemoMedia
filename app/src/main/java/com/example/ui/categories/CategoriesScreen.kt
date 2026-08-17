package com.example.ui.categories

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.enum.NotificationMode
import com.example.core.model.Category
import com.example.core.model.SourceRule
import com.example.ui.components.getCategoryColor
import com.example.ui.components.getCategoryIcon
import com.example.ui.util.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<CategoryWithStats?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "分类",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (selectedTabIndex == 0) {
                                viewModel.openCreateCategoryDialog()
                            } else {
                                viewModel.openCreateRuleDialog()
                            }
                        },
                        modifier = Modifier.testTag("btn_add_category_or_rule")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "新建")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("分类相册 (${state.categoryStats.size})") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("分流规则 (${state.sourceRules.size})") }
                )
            }

            if (selectedTabIndex == 0) {
                CategoryGridTab(
                    categoryStats = state.categoryStats,
                    onEdit = { viewModel.openEditCategoryDialog(it) },
                    onDelete = { pendingDelete = it }
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

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${item.category.name}」？") },
            text = {
                Text(
                    if (item.mediaCount > 0) {
                        "分类中的 ${item.mediaCount} 项会回到待整理，相关自动分类规则也会停用。媒体文件本身不会被删除。"
                    } else {
                        "相关自动分类规则会停用。媒体文件本身不会被删除。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCategory(item.category.id)
                        pendingDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_category")
                ) {
                    Text("删除分类", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消")
                }
            }
        )
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
private fun CategoryGridTab(
    categoryStats: List<CategoryWithStats>,
    onEdit: (Category) -> Unit,
    onDelete: (CategoryWithStats) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .testTag("categories_grid"),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = categoryStats,
            key = { it.category.id }
        ) { item ->
            CategoryCard2Column(
                item = item,
                onEdit = { onEdit(item.category) },
                onDelete = { onDelete(item) }
            )
        }
    }
}

@Composable
private fun CategoryCard2Column(
    item: CategoryWithStats,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val category = item.category
    val catColor = getCategoryColor(category.name)
    var showMenu by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .testTag("category_card_${category.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Album 2x2 Collage Preview or Hero Cover
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .background(catColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.previewMedia.isNotEmpty()) {
                    // Render 2x2 collage preview
                    val previews = item.previewMedia.take(4)
                    if (previews.size == 1) {
                        AsyncImage(
                            model = Uri.parse(previews[0].contentUri),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                AsyncImage(
                                    model = Uri.parse(previews[0].contentUri),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.weight(1f).fillMaxSize()
                                )
                                if (previews.size > 1) {
                                    Spacer(modifier = Modifier.width(1.dp))
                                    AsyncImage(
                                        model = Uri.parse(previews[1].contentUri),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.weight(1f).fillMaxSize()
                                    )
                                }
                            }
                            if (previews.size > 2) {
                                Spacer(modifier = Modifier.height(1.dp))
                                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    AsyncImage(
                                        model = Uri.parse(previews[2].contentUri),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.weight(1f).fillMaxSize()
                                    )
                                    Spacer(modifier = Modifier.width(1.dp))
                                    if (previews.size > 3) {
                                        AsyncImage(
                                            model = Uri.parse(previews[3].contentUri),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.weight(1f).fillMaxSize()
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxSize()
                                                .background(catColor.copy(alpha = 0.2f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Icon(
                        imageVector = getCategoryIcon(category.icon),
                        contentDescription = null,
                        tint = catColor,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Category Icon Badge top right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getCategoryIcon(category.icon),
                            contentDescription = null,
                            tint = catColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Info Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "更多",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("编辑分类") },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                            )
                            if (!category.isSystem) {
                                DropdownMenuItem(
                                    text = { Text("删除分类", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        onDelete()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "${item.mediaCount} 项 · ${formatFileSize(item.totalSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Retention Badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (category.retentionDays != null)
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                    else
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = if (category.retentionDays != null) "保留 ${category.retentionDays} 天" else "永久保留",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (category.retentionDays != null)
                            MaterialTheme.colorScheme.onTertiaryContainer
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
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
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (!rule.sourcePackage.isNullOrBlank()) {
                                Text(
                                    text = "来源: ${rule.sourcePackage}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!rule.relativePathPattern.isNullOrBlank()) {
                                Text(
                                    text = "路径: ${rule.relativePathPattern}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "目标分类: ${targetCat?.name ?: "未设置"}",
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
                        label = { Text("保留天数 (过期自动进入清理建议)") },
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
                    Text("分类时静默处理（不弹通知）")
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
                        onSave(name.trim(), sourcePackage, relativePathPattern, selectedCategoryId, notificationMode, true)
                    }
                },
                enabled = name.isNotBlank() && selectedCategoryId != null
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
