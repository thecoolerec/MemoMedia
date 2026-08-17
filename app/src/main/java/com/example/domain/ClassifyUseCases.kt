package com.example.domain

import androidx.room.withTransaction
import com.example.core.enum.SessionStatus
import com.example.data.local.AppDatabase
import com.example.data.repository.CaptureSessionRepository
import com.example.data.repository.CategoryRepository
import com.example.data.repository.MediaRepository
import com.example.policy.CategoryPolicyEngine
import com.example.watcher.MediaNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ClassifyMediaUseCase(
    private val mediaRepository: MediaRepository,
    private val categoryRepository: CategoryRepository,
    private val policyEngine: CategoryPolicyEngine
) {
    suspend operator fun invoke(mediaId: Long, categoryId: Long): Boolean = withContext(Dispatchers.IO) {
        val asset = mediaRepository.getById(mediaId) ?: return@withContext false
        val category = categoryRepository.getById(categoryId) ?: return@withContext false
        val expireAt = policyEngine.calculateExpireAt(asset.capturedAt ?: asset.addedAt, category)
        mediaRepository.assignCategory(mediaId, categoryId, expireAt)
        true
    }
}

class ClassifySessionUseCase(
    private val db: AppDatabase,
    private val mediaRepository: MediaRepository,
    private val captureSessionRepository: CaptureSessionRepository,
    private val categoryRepository: CategoryRepository,
    private val policyEngine: CategoryPolicyEngine,
    private val notificationManager: MediaNotificationManager? = null
) {
    suspend operator fun invoke(sessionId: Long, categoryId: Long): Int = withContext(Dispatchers.IO) {
        val category = categoryRepository.getById(categoryId) ?: return@withContext 0

        val classifiedCount = db.withTransaction {
            val items = mediaRepository.getBySession(sessionId)
            for (item in items) {
                val expireAt = policyEngine.calculateExpireAt(item.capturedAt ?: item.addedAt, category)
                mediaRepository.assignCategory(item.id, categoryId, expireAt)
            }
            captureSessionRepository.updateStatus(sessionId, SessionStatus.CLASSIFIED.name)
            items.size
        }

        // Notification is cancelled only after the transaction is successfully committed
        if (classifiedCount > 0) {
            notificationManager?.cancelSessionNotification(sessionId)
        }
        classifiedCount
    }
}

