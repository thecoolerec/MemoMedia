package com.example.policy

import com.example.core.enum.MediaStatus
import com.example.core.model.Category
import com.example.core.model.MediaAsset
import com.example.data.repository.CategoryRepository
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExpiredSummary(
    val totalCount: Int,
    val totalSizeBytes: Long,
    val categoryBreakdown: Map<String, Int>,
    val expiredItems: List<MediaAsset>
)

class RetentionScanner(
    private val mediaRepository: MediaRepository,
    private val categoryRepository: CategoryRepository
) {
    suspend fun scanAndMarkExpired(now: Long = System.currentTimeMillis()): ExpiredSummary = withContext(Dispatchers.IO) {
        val expired = mediaRepository.findExpired(now)
        val categories = categoryRepository.getAll().associateBy { it.id }

        // Update items to EXPIRED status in database
        for (item in expired) {
            if (item.status != MediaStatus.EXPIRED) {
                mediaRepository.updateStatus(item.id, MediaStatus.EXPIRED)
            }
        }

        var totalSize = 0L
        val breakdown = mutableMapOf<String, Int>()

        for (item in expired) {
            totalSize += item.sizeBytes ?: 0L
            val catName = item.primaryCategoryId?.let { categories[it]?.name } ?: "未分类"
            breakdown[catName] = (breakdown[catName] ?: 0) + 1
        }

        ExpiredSummary(
            totalCount = expired.size,
            totalSizeBytes = totalSize,
            categoryBreakdown = breakdown,
            expiredItems = expired
        )
    }
}
