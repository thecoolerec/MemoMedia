package com.example.domain

import com.example.core.enum.MediaStatus
import com.example.core.enum.SessionStatus
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
    private val mediaRepository: MediaRepository,
    private val captureSessionRepository: CaptureSessionRepository,
    private val classifyMediaUseCase: ClassifyMediaUseCase,
    private val notificationManager: MediaNotificationManager? = null
) {
    suspend operator fun invoke(sessionId: Long, categoryId: Long): Int = withContext(Dispatchers.IO) {
        val items = mediaRepository.getBySession(sessionId)
        var classifiedCount = 0
        for (item in items) {
            if (classifyMediaUseCase(item.id, categoryId)) {
                classifiedCount++
            }
        }
        captureSessionRepository.updateStatus(sessionId, SessionStatus.CLASSIFIED.name)
        notificationManager?.cancelSessionNotification(sessionId)
        classifiedCount
    }
}
