package com.example.policy

import com.example.core.enum.NotificationMode
import com.example.core.model.Category
import com.example.core.model.MediaAsset
import com.example.core.model.MediaRoutingDecision
import com.example.core.model.SourceRule
import com.example.data.repository.CategoryRepository
import com.example.data.repository.SourceRuleRepository

fun sqlLikePatternToRegex(pattern: String): Regex {
    val sb = StringBuilder()
    for (ch in pattern) {
        when (ch) {
            '%' -> sb.append(".*")
            '_' -> sb.append(".")
            '\\', '.', '^', '$', '(', ')', '[', ']', '{', '}', '+', '*', '?', '|' -> {
                sb.append('\\').append(ch)
            }
            else -> sb.append(ch)
        }
    }
    return Regex(sb.toString(), RegexOption.IGNORE_CASE)
}

interface SourceRuleEngine {
    suspend fun evaluate(asset: MediaAsset): MediaRoutingDecision
    fun evaluateWithSnapshot(
        asset: MediaAsset,
        activeRules: List<SourceRule>,
        categories: Map<Long, Category>,
        defaultNotificationMode: NotificationMode = NotificationMode.OVERLAY
    ): MediaRoutingDecision
}

class DefaultSourceRuleEngine(
    private val ruleRepository: SourceRuleRepository,
    private val categoryRepository: CategoryRepository? = null
) : SourceRuleEngine {

    override suspend fun evaluate(asset: MediaAsset): MediaRoutingDecision {
        val rules = ruleRepository.getActiveRules()
        val categories = categoryRepository?.getAll()?.associateBy { it.id } ?: emptyMap()
        return evaluateWithSnapshot(asset, rules, categories)
    }

    override fun evaluateWithSnapshot(
        asset: MediaAsset,
        activeRules: List<SourceRule>,
        categories: Map<Long, Category>,
        defaultNotificationMode: NotificationMode
    ): MediaRoutingDecision {
        val screenshotCat = categories.values.find { it.systemKey == "screenshots" || it.name == "截图" }

        for (rule in activeRules) {
            if (matches(rule, asset)) {
                val targetCategory = rule.targetCategoryId?.let { categories[it] }
                val notificationMode = rule.notificationMode
                    ?: targetCategory?.notificationMode
                    ?: defaultNotificationMode

                return MediaRoutingDecision(
                    matchedRule = rule,
                    categoryId = rule.targetCategoryId,
                    autoClassify = rule.autoClassify && rule.targetCategoryId != null,
                    notificationMode = notificationMode,
                    indexingEnabled = targetCategory?.indexingEnabled ?: true
                )
            }
        }

        // Default fallback if no custom rule matched:
        // If relativePath or bucket or displayName contains Screenshot/截图, default to Screenshot category
        val path = (asset.relativePath ?: "") + "/" + (asset.bucketName ?: "") + "/" + (asset.displayName ?: "")
        if (path.contains("Screenshot", ignoreCase = true) || path.contains("截图")) {
            return MediaRoutingDecision(
                matchedRule = null,
                categoryId = screenshotCat?.id,
                autoClassify = screenshotCat != null,
                notificationMode = screenshotCat?.notificationMode ?: NotificationMode.SILENT,
                indexingEnabled = screenshotCat?.indexingEnabled ?: false
            )
        }

        return MediaRoutingDecision(
            matchedRule = null,
            categoryId = null,
            autoClassify = false,
            notificationMode = defaultNotificationMode,
            indexingEnabled = true
        )
    }

    private fun matches(rule: SourceRule, asset: MediaAsset): Boolean {
        // Match package if specified
        if (!rule.sourcePackage.isNullOrBlank()) {
            if (asset.ownerPackage == null || !asset.ownerPackage.contains(rule.sourcePackage, ignoreCase = true)) {
                return false
            }
        }

        // Match media type if specified
        if (rule.mediaType != null && rule.mediaType != asset.mediaType) {
            return false
        }

        // Match relative path pattern (SQL LIKE style, e.g. %Screenshots%)
        if (!rule.relativePathPattern.isNullOrBlank()) {
            val regex = sqlLikePatternToRegex(rule.relativePathPattern)
            val path = asset.relativePath ?: ""
            if (!regex.containsMatchIn(path)) {
                return false
            }
        }

        // Match bucket name pattern
        if (!rule.bucketPattern.isNullOrBlank()) {
            val regex = sqlLikePatternToRegex(rule.bucketPattern)
            val bucket = asset.bucketName ?: ""
            if (!regex.containsMatchIn(bucket)) {
                return false
            }
        }

        return true
    }
}

interface CategoryPolicyEngine {
    fun calculateExpireAt(capturedAt: Long, category: Category): Long?
}

class DefaultCategoryPolicyEngine : CategoryPolicyEngine {
    override fun calculateExpireAt(capturedAt: Long, category: Category): Long? {
        val days = category.retentionDays ?: return null
        val millisInDay = 86_400_000L
        return capturedAt + (days * millisInDay)
    }
}
