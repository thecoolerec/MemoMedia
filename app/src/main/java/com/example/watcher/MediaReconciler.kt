package com.example.watcher

import com.example.core.enum.MediaStatus
import com.example.core.enum.ReconcileMode
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

    suspend fun reconcile(forceFullScan: Boolean): Int {
        val mode = if (forceFullScan) ReconcileMode.FULL_REPAIR else ReconcileMode.LIVE_INCREMENTAL
        return reconcile(mode)
    }

    suspend fun reconcile(mode: ReconcileMode = ReconcileMode.LIVE_INCREMENTAL): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
            val maxKnownAddedAt = mediaRepository.getMaxAddedAt()
            val isInitial = maxKnownAddedAt == 0L

            val effectiveMode = if (isInitial && mode == ReconcileMode.LIVE_INCREMENTAL) {
                ReconcileMode.INITIAL_BACKFILL
            } else {
                mode
            }

            val systemMediaList = when (effectiveMode) {
                ReconcileMode.INITIAL_BACKFILL, ReconcileMode.FULL_REPAIR -> {
                    mediaStoreDataSource.queryAll()
                }
                ReconcileMode.LIVE_INCREMENTAL, ReconcileMode.BOOT_CATCHUP -> {
                    if (maxKnownAddedAt > 0L) {
                        // Query media added after max known addedAt (minus 5s cushion for clock skew)
                        mediaStoreDataSource.queryLatest((maxKnownAddedAt / 1000L) - 5L)
                    } else {
                        mediaStoreDataSource.queryAll()
                    }
                }
            }

            var newlyImportedCount = 0
            val categoriesMap = categoryRepository.getAll().associateBy { it.id }
            val activeRules = sourceRuleRepository.getActiveRules()
            val now = System.currentTimeMillis()
            val bootCatchupHorizonMs = now - (10 * 60 * 1000L) // 10 minutes

            for (sysMedia in systemMediaList) {
                if (mediaRepository.exists(sysMedia.contentUri)) {
                    continue
                }

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
                    status = MediaStatus.UNCLASSIFIED,
                    createdAt = now,
                    updatedAt = now
                )

                // Evaluate rule with snapshot to avoid N+1 queries
                val routingDecision = ruleEngine.evaluateWithSnapshot(asset, activeRules, categoriesMap)

                if (routingDecision.autoClassify && routingDecision.categoryId != null) {
                    val targetCategory = categoriesMap[routingDecision.categoryId]
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
                    val shouldCreateSession = when (effectiveMode) {
                        ReconcileMode.INITIAL_BACKFILL, ReconcileMode.FULL_REPAIR -> false
                        ReconcileMode.LIVE_INCREMENTAL -> true
                        ReconcileMode.BOOT_CATCHUP -> asset.addedAt >= bootCatchupHorizonMs
                    }

                    if (shouldCreateSession) {
                        val sessionId = aggregator.aggregate(asset, routingDecision.notificationMode)
                        asset = asset.copy(
                            captureSessionId = sessionId,
                            status = MediaStatus.PENDING
                        )
                        mediaRepository.insert(asset)
                    } else {
                        // Historical backfill or old boot catchup: import as UNCLASSIFIED without session
                        asset = asset.copy(
                            captureSessionId = null,
                            status = MediaStatus.UNCLASSIFIED
                        )
                        mediaRepository.insert(asset)
                    }
                }
                newlyImportedCount++
            }

            // If full scan or repair requested, check for missing/deleted files
            if (effectiveMode == ReconcileMode.FULL_REPAIR) {
                val knownUris = mediaRepository.getAllKnownContentUris()
                val currentSystemUris = systemMediaList.map { it.contentUri }.toSet()
                val missingUris = knownUris.filter { !currentSystemUris.contains(it) }
                if (missingUris.isNotEmpty()) {
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
