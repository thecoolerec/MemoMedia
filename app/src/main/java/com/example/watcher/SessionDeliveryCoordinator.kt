package com.example.watcher

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.example.core.enum.DeliveryStatus
import com.example.core.enum.NotificationMode
import com.example.core.model.CaptureSession
import com.example.core.model.DeliveryResult
import com.example.data.repository.AppSettingsRepository
import com.example.data.repository.CaptureSessionRepository
import com.example.data.repository.CategoryRepository
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SessionDeliveryCoordinator(
    private val context: Context,
    private val sessionRepository: CaptureSessionRepository,
    private val categoryRepository: CategoryRepository,
    private val mediaRepository: MediaRepository,
    private val settingsRepository: AppSettingsRepository,
    private val notificationManager: MediaNotificationManager,
    private val overlay: QuickClassifyOverlay
) {
    suspend fun deliverSession(session: CaptureSession): DeliveryResult {
        val categories = withContext(Dispatchers.IO) { categoryRepository.getAll() }
        val items = withContext(Dispatchers.IO) { mediaRepository.getBySession(session.id) }
        val settings = withContext(Dispatchers.IO) { settingsRepository.getSnapshot() }

        val targetMode = session.notificationMode ?: settings.defaultNotificationMode

        return when (targetMode) {
            NotificationMode.SILENT -> {
                withContext(Dispatchers.IO) {
                    sessionRepository.updateDeliveryStatus(session.id, DeliveryStatus.DELIVERED_SILENT.name)
                }
                DeliveryResult.Success(NotificationMode.SILENT)
            }
            NotificationMode.NOTIFICATION, NotificationMode.HEADS_UP -> {
                val result = notificationManager.showSessionReadyNotification(session, items, categories)
                if (result is DeliveryResult.Success) {
                    withContext(Dispatchers.IO) {
                        sessionRepository.updateDeliveryStatus(session.id, DeliveryStatus.DELIVERED_NOTIFICATION.name)
                    }
                }
                result
            }
            NotificationMode.OVERLAY -> {
                val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else {
                    true
                }

                if (settings.overlayEnabled && hasOverlayPermission) {
                    val overlayResult = withContext(Dispatchers.Main) {
                        overlay.show(session, items, categories)
                    }
                    if (overlayResult is DeliveryResult.Success) {
                        withContext(Dispatchers.IO) {
                            sessionRepository.updateDeliveryStatus(session.id, DeliveryStatus.DELIVERED_OVERLAY.name)
                        }
                        overlayResult
                    } else {
                        // Fallback to notification when overlay display fails
                        val notifResult = notificationManager.showSessionReadyNotification(session, items, categories)
                        if (notifResult is DeliveryResult.Success) {
                            withContext(Dispatchers.IO) {
                                sessionRepository.updateDeliveryStatus(session.id, DeliveryStatus.DELIVERED_NOTIFICATION.name)
                            }
                        }
                        notifResult
                    }
                } else {
                    // Fallback to Notification when overlay is disabled in settings or permission is not granted
                    val notifResult = notificationManager.showSessionReadyNotification(session, items, categories)
                    if (notifResult is DeliveryResult.Success) {
                        withContext(Dispatchers.IO) {
                            sessionRepository.updateDeliveryStatus(session.id, DeliveryStatus.DELIVERED_NOTIFICATION.name)
                        }
                    }
                    notifResult
                }
            }
        }
    }
}

