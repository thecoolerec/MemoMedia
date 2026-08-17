package com.example.watcher

import com.example.core.enum.MediaType
import com.example.core.enum.SessionStatus
import com.example.core.model.CaptureSession
import com.example.core.model.MediaAsset
import com.example.data.repository.AppSettingsRepository
import com.example.data.repository.CaptureSessionRepository
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CaptureSessionAggregator(
    private val sessionRepository: CaptureSessionRepository,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: AppSettingsRepository,
    private val scope: CoroutineScope
) {
    private val mutex = Mutex()
    private var activeSession: CaptureSession? = null
    private var lastAssetAddedAt: Long = 0L
    private var quietPeriodJob: Job? = null

    private val _sessionReadyEvents = MutableSharedFlow<CaptureSession>(extraBufferCapacity = 10)
    val sessionReadyEvents: SharedFlow<CaptureSession> = _sessionReadyEvents.asSharedFlow()

    suspend fun recoverStaleSessions() = mutex.withLock {
        val collectingSessions = sessionRepository.getAllCollectingSessions()
        val now = System.currentTimeMillis()
        for (session in collectingSessions) {
            val quietPeriodMs = settingsRepository.getSnapshot().quietPeriodSeconds * 1000L
            if (now - session.endedAt > quietPeriodMs) {
                // Stale, finalize it
                sessionRepository.updateStatus(session.id, SessionStatus.READY.name)
            } else {
                // Still active within quiet window, resume it
                activeSession = session
                lastAssetAddedAt = session.endedAt
                scheduleQuietPeriod()
            }
        }
    }

    suspend fun aggregate(asset: MediaAsset, notificationMode: com.example.core.enum.NotificationMode? = null): Long = mutex.withLock {
        val now = asset.addedAt
        val current = activeSession
        val windowMs = settingsRepository.getSnapshot().aggregationWindowSeconds * 1000L

        val gap = now - lastAssetAddedAt
        val shouldJoin = current != null &&
                current.status == SessionStatus.COLLECTING &&
                current.sourcePackage == asset.ownerPackage &&
                current.mediaType == asset.mediaType &&
                gap in 0..windowMs

        val sessionId: Long
        if (shouldJoin) {
            val joiningSession = checkNotNull(current)
            sessionId = joiningSession.id
            sessionRepository.incrementMediaCount(sessionId, now)
            activeSession = joiningSession.copy(
                mediaCount = joiningSession.mediaCount + 1,
                endedAt = now
            )
        } else {
            // If previous session was collecting, finalize it
            if (current != null && current.status == SessionStatus.COLLECTING) {
                sessionRepository.updateStatus(current.id, SessionStatus.READY.name)
                _sessionReadyEvents.emit(current.copy(status = SessionStatus.READY))
            }

            val newSession = CaptureSession(
                sourcePackage = asset.ownerPackage,
                mediaType = asset.mediaType,
                startedAt = now,
                endedAt = now,
                mediaCount = 1,
                status = SessionStatus.COLLECTING,
                notificationMode = notificationMode
            )
            sessionId = sessionRepository.save(newSession)
            activeSession = newSession.copy(id = sessionId)
        }

        lastAssetAddedAt = now
        scheduleQuietPeriod()
        return@withLock sessionId
    }

    private fun scheduleQuietPeriod() {
        quietPeriodJob?.cancel()
        val quietPeriodMs = settingsRepository.getSnapshot().quietPeriodSeconds * 1000L
        quietPeriodJob = scope.launch(Dispatchers.Default) {
            delay(quietPeriodMs)
            mutex.withLock {
                val current = activeSession
                if (current != null && current.status == SessionStatus.COLLECTING) {
                    sessionRepository.updateStatus(current.id, SessionStatus.READY.name)
                    val readySession = current.copy(status = SessionStatus.READY)
                    activeSession = null
                    _sessionReadyEvents.emit(readySession)
                }
            }
        }
    }
}
