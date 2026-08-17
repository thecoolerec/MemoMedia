package com.example.watcher

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.example.core.enum.DeliveryStatus
import com.example.core.enum.NotificationMode
import com.example.core.model.CaptureSession
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
    suspend fun deliverSession(session: CaptureSession) = withContext(Dispatchers.Main) {
        val categories = withContext(Dispatchers.IO) { categoryRepository.getAll() }
        val items = withContext(Dispatchers.IO) { mediaRepository.getBySession(session.id) }
        val settings = withContext(Dispatchers.IO) { settingsRepository.getSnapshot() }

        val targetMode = session.notificationMode ?: settings.defaultNotificationMode

        when (targetMode) {
            NotificationMode.SILENT -> {
                withContext(Dispatchers.IO) {
                    sessionRepository.updateDeliveryStatus(session.id, DeliveryStatus.DELIVERED_SILENT.name)
                }
            }
            NotificationMode.NOTIFICATION, NotificationMode.HEADS_UP -> {
                notificationManager.showSessionReadyNotification(session, categories)
                withContext(Dispatchers.IO) {
                    sessionRepository.updateDeliveryStatus(session.id, DeliveryStatus.DELIVERED_NOTIFICATION.name)
                }
            }
            NotificationMode.OVERLAY -> {
                val hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Settings.canDrawOverlays(context)
                } else {
                    true
                }

                if (hasOverlayPermission) {
                    overlay.show(session, items, categories)
                    withContext(Dispatchers.IO) {
                        sessionRepository.updateDeliveryStatus(session.id, DeliveryStatus.DELIVERED_OVERLAY.name)
                    }
                } else {
                    // Fallback to Notification when overlay permission is not granted
                    notificationManager.showSessionReadyNotification(session, categories)
                    withContext(Dispatchers.IO) {
                        sessionRepository.updateDeliveryStatus(session.id, DeliveryStatus.DELIVERED_NOTIFICATION.name)
                    }
                }
            }
        }
    }
}
