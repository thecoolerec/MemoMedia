package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.Category
import com.example.ui.theme.CategoryDefaultColor
import com.example.ui.theme.CategoryLifeColor
import com.example.ui.theme.CategoryScreenshotColor
import com.example.ui.theme.CategoryTempColor
import com.example.ui.theme.CategoryWorkColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    title: String = "选择分类",
    subtitle: String? = null,
    categories: List<Category>,
    selectedCategoryId: Long? = null,
    onSelectCategory: (Category) -> Unit,
    onCreateCategory: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("category_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(categories, key = { _, category -> category.id }) { index, category ->
                    val isSelected = category.id == selectedCategoryId

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable {
                                onSelectCategory(category)
                                onDismiss()
                            }
                            .testTag("category_option_${category.id}"),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else
                            Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(getCategoryColor(category.name).copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = getCategoryIcon(category.icon),
                                    contentDescription = category.name,
                                    tint = getCategoryColor(category.name),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (category.retentionDays == null) "永久保存" else "保留 ${category.retentionDays} 天",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选择",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    if (index < categories.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 62.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    onDismiss()
                    onCreateCategory()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_create_category"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("新建分类")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

fun getCategoryColor(name: String): Color {
    return when (name) {
        "生活" -> CategoryLifeColor
        "工作" -> CategoryWorkColor
        "临时" -> CategoryTempColor
        "截图" -> CategoryScreenshotColor
        else -> CategoryDefaultColor
    }
}

fun getCategoryIcon(iconName: String?): ImageVector {
    return when (iconName) {
        "local_florist" -> Icons.Default.LocalFlorist
        "work" -> Icons.Default.Work
        "hourglass_empty" -> Icons.Default.HourglassEmpty
        "screenshot_monitor" -> Icons.Default.ScreenshotMonitor
        else -> Icons.Default.Done
    }
}
