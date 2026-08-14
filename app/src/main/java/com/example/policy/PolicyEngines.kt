package com.example.policy

import com.example.core.enum.NotificationMode
import com.example.core.model.Category
import com.example.core.model.MediaAsset
import com.example.core.model.SourceRule
import com.example.data.repository.SourceRuleRepository

data class SourceRuleResult(
    val matchedRule: SourceRule? = null,
    val categoryId: Long? = null,
    val notificationMode: NotificationMode = NotificationMode.OVERLAY,
    val autoClassify: Boolean = false
)

interface SourceRuleEngine {
    suspend fun evaluate(asset: MediaAsset): SourceRuleResult
    fun evaluateWithSnapshot(
        asset: MediaAsset,
        activeRules: List<SourceRule>,
        categories: Map<Long, Category>
    ): SourceRuleResult
}

class DefaultSourceRuleEngine(
    private val ruleRepository: SourceRuleRepository
) : SourceRuleEngine {

    override suspend fun evaluate(asset: MediaAsset): SourceRuleResult {
        val rules = ruleRepository.getActiveRules()
        return evaluateWithRules(asset, rules, null)
    }

    override fun evaluateWithSnapshot(
        asset: MediaAsset,
        activeRules: List<SourceRule>,
        categories: Map<Long, Category>
    ): SourceRuleResult {
        val screenshotCatId = categories.values.find { it.name == "截图" }?.id
        return evaluateWithRules(asset, activeRules, screenshotCatId)
    }

    private fun evaluateWithRules(
        asset: MediaAsset,
        rules: List<SourceRule>,
        screenshotCategoryId: Long?
    ): SourceRuleResult {
        for (rule in rules) {
            if (matches(rule, asset)) {
                return SourceRuleResult(
                    matchedRule = rule,
                    categoryId = rule.targetCategoryId,
                    notificationMode = rule.notificationMode ?: NotificationMode.OVERLAY,
                    autoClassify = rule.autoClassify && rule.targetCategoryId != null
                )
            }
        }

        // Default fallback if no custom rule matched:
        // If relativePath or bucket or displayName contains Screenshot/截图, default to Screenshot category
        val path = (asset.relativePath ?: "") + (asset.bucketName ?: "") + (asset.displayName ?: "")
        if (path.contains("Screenshot", ignoreCase = true) || path.contains("截图")) {
            return SourceRuleResult(
                matchedRule = null,
                categoryId = screenshotCategoryId ?: 4L,
                notificationMode = NotificationMode.SILENT,
                autoClassify = true
            )
        }

        return SourceRuleResult(
            matchedRule = null,
            categoryId = null,
            notificationMode = NotificationMode.OVERLAY,
            autoClassify = false
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
            val pattern = rule.relativePathPattern.replace("%", ".*").replace("_", ".")
            val path = asset.relativePath ?: ""
            if (!Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(path)) {
                return false
            }
        }

        // Match bucket name pattern
        if (!rule.bucketPattern.isNullOrBlank()) {
            val pattern = rule.bucketPattern.replace("%", ".*").replace("_", ".")
            val bucket = asset.bucketName ?: ""
            if (!Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(bucket)) {
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
