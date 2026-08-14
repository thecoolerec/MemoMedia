package com.example.watcher

import com.example.core.enum.MediaStatus
import com.example.core.model.MediaAsset
import com.example.data.repository.CategoryRepository
import com.example.data.repository.MediaRepository
import com.example.data.repository.SourceRuleRepository
import com.example.media.MediaStoreDataSource
import com.example.policy.CategoryPolicyEngine
import com.example.policy.SourceRuleEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MediaReconciler(
    private val mediaStoreDataSource: MediaStoreDataSource,
    private val mediaRepository: MediaRepository,
    private val categoryRepository: CategoryRepository,
    private val sourceRuleRepository: SourceRuleRepository,
    private val ruleEngine: SourceRuleEngine,
    private val policyEngine: CategoryPolicyEngine,
    private val aggregator: CaptureSessionAggregator
) {
    private val mutex = Mutex()

    suspend fun reconcile(forceFullScan: Boolean = false): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            val maxKnownAddedAt = if (forceFullScan) 0L else mediaRepository.getMaxAddedAt()
            val systemMediaList = if (maxKnownAddedAt > 0L) {
                // Query only media added after the latest known addedAt (minus 5s cushion for clock skew)
                mediaStoreDataSource.queryLatest((maxKnownAddedAt / 1000L) - 5L)
            } else {
                mediaStoreDataSource.queryAll()
            }

            var newlyImportedCount = 0
            val categoriesMap = categoryRepository.getAll().associateBy { it.id }
            val activeRules = sourceRuleRepository.getActiveRules()

            for (sysMedia in systemMediaList) {
                if (mediaRepository.exists(sysMedia.contentUri)) {
                    continue
                }

                val now = System.currentTimeMillis()
                var asset = MediaAsset(
                    mediaStoreId = sysMedia.mediaStoreId,
                    contentUri = sysMedia.contentUri,
                    mediaType = sysMedia.mediaType,
                    mimeType = sysMedia.mimeType,
                    displayName = sysMedia.displayName,
                    ownerPackage = sysMedia.ownerPackage,
                    relativePath = sysMedia.relativePath,
                    bucketName = sysMedia.bucketName,
                    width = sysMedia.width,
                    height = sysMedia.height,
                    sizeBytes = sysMedia.sizeBytes,
                    capturedAt = sysMedia.dateTaken,
                    addedAt = sysMedia.dateAdded,
                    status = MediaStatus.PENDING,
                    createdAt = now,
                    updatedAt = now
                )

                // Evaluate rule with snapshot to avoid N+1 queries
                val ruleResult = ruleEngine.evaluateWithSnapshot(asset, activeRules, categoriesMap)

                if (ruleResult.autoClassify && ruleResult.categoryId != null) {
                    val targetCategory = categoriesMap[ruleResult.categoryId]
                    if (targetCategory != null) {
                        val expireAt = policyEngine.calculateExpireAt(
                            asset.capturedAt ?: asset.addedAt,
                            targetCategory
                        )
                        asset = asset.copy(
                            primaryCategoryId = targetCategory.id,
                            expireAt = expireAt,
                            status = MediaStatus.CLASSIFIED
                        )
                    }
                    mediaRepository.insert(asset)
                } else {
                    if (forceFullScan) {
                        // Historical scan: do not aggregate into active CaptureSession
                        mediaRepository.insert(asset)
                    } else {
                        // Live import: Aggregate into capture session
                        val sessionId = aggregator.aggregate(asset)
                        asset = asset.copy(
                            captureSessionId = sessionId,
                            status = MediaStatus.PENDING
                        )
                        mediaRepository.insert(asset)
                    }
                }
                newlyImportedCount++
            }

            // If full scan requested, check for missing/deleted files
            if (forceFullScan) {
                val knownUris = mediaRepository.getAllKnownContentUris()
                val currentSystemUris = systemMediaList.map { it.contentUri }.toSet()
                val missingUris = knownUris.filter { !currentSystemUris.contains(it) }
                if (missingUris.isNotEmpty()) {
                    // Check individual existence before marking deleted
                    val reallyDeletedIds = mutableListOf<Long>()
                    for (uri in missingUris) {
                        val asset = mediaRepository.getByContentUri(uri)
                        if (asset != null && !mediaStoreDataSource.exists(android.net.Uri.parse(asset.contentUri))) {
                            reallyDeletedIds.add(asset.id)
                        }
                    }
                    if (reallyDeletedIds.isNotEmpty()) {
                        mediaRepository.markDeleted(reallyDeletedIds)
                    }
                }
            }

            newlyImportedCount
        }
    }
}
