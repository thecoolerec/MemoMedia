package com.example.watcher

import com.example.core.enum.MediaType
import com.example.core.enum.SessionStatus
import com.example.core.model.CaptureSession
import com.example.core.model.MediaAsset
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
    private val scope: CoroutineScope
) {
    private val mutex = Mutex()
    private var activeSession: CaptureSession? = null
    private var lastAssetAddedAt: Long = 0L
    private var quietPeriodJob: Job? = null

    private val _sessionReadyEvents = MutableSharedFlow<CaptureSession>(extraBufferCapacity = 10)
    val sessionReadyEvents: SharedFlow<CaptureSession> = _sessionReadyEvents.asSharedFlow()

    suspend fun aggregate(asset: MediaAsset): Long = mutex.withLock {
        val now = asset.addedAt
        val current = activeSession

        val shouldJoin = current != null &&
                current.status == SessionStatus.COLLECTING &&
                current.sourcePackage == asset.ownerPackage &&
                current.mediaType == asset.mediaType &&
                (now - lastAssetAddedAt) <= 8_000L // 8s join window

        val sessionId: Long
        if (shouldJoin && current != null) {
            sessionId = current.id
            sessionRepository.incrementMediaCount(sessionId, now)
            activeSession = current.copy(
                mediaCount = current.mediaCount + 1,
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
                status = SessionStatus.COLLECTING
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
        quietPeriodJob = scope.launch(Dispatchers.Default) {
            delay(2_000L) // 2s quiet period after last photo
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
