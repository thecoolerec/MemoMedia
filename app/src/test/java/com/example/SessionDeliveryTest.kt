package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.enum.NotificationMode
import com.example.core.enum.SessionStatus
import com.example.core.model.CaptureSession
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CaptureSessionEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.repository.AppSettingsRepository
import com.example.data.repository.CaptureSessionRepository
import com.example.data.repository.CategoryRepository
import com.example.data.repository.MediaRepository
import com.example.watcher.MediaNotificationManager
import com.example.watcher.QuickClassifyOverlay
import com.example.watcher.SessionDeliveryCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionDeliveryTest {

    private lateinit var db: AppDatabase
    private lateinit var sessionRepository: CaptureSessionRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var mediaRepository: MediaRepository
    private lateinit var settingsRepository: AppSettingsRepository
    private lateinit var notificationManager: MediaNotificationManager
    private lateinit var overlay: QuickClassifyOverlay
    private lateinit var coordinator: SessionDeliveryCoordinator
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        sessionRepository = CaptureSessionRepository(db)
        categoryRepository = CategoryRepository(db)
        mediaRepository = MediaRepository(db)
        settingsRepository = AppSettingsRepository(context)
        notificationManager = MediaNotificationManager(context)
        overlay = QuickClassifyOverlay(context)
        coordinator = SessionDeliveryCoordinator(
            context = context,
            sessionRepository = sessionRepository,
            categoryRepository = categoryRepository,
            mediaRepository = mediaRepository,
            settingsRepository = settingsRepository,
            notificationManager = notificationManager,
            overlay = overlay
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testOverlayFallbackToNotificationWhenNoPermission() {
        runBlocking {
            // Overlay permission is false by default in Robolectric test environment
            val sessionId = db.captureSessionDao().insert(
                CaptureSessionEntity(
                    sourcePackage = "com.camera",
                    mediaType = "IMAGE",
                    startedAt = 1000L,
                    endedAt = 2000L,
                    mediaCount = 1,
                    status = SessionStatus.READY.name,
                    deliveryStatus = "NOT_DELIVERED"
                )
            )

            val session = CaptureSession(
                id = sessionId,
                sourcePackage = "com.camera",
                mediaType = com.example.core.enum.MediaType.IMAGE,
                startedAt = 1000L,
                endedAt = 2000L,
                mediaCount = 1,
                status = SessionStatus.READY,
                deliveryStatus = com.example.core.enum.DeliveryStatus.NOT_DELIVERED
            )

            coordinator.deliverSession(session)

            val updated = db.captureSessionDao().getById(sessionId)
            assertNotNull(updated)
            // Because overlay cannot draw without permission, it must fall back to DELIVERED_NOTIFICATION!
            assertEquals("DELIVERED_NOTIFICATION", updated?.deliveryStatus)
        }
    }

    @Test
    fun testSilentDeliveryMode() {
        runBlocking {
            val sessionId = db.captureSessionDao().insert(
                CaptureSessionEntity(
                    sourcePackage = "com.camera",
                    mediaType = "IMAGE",
                    startedAt = 1000L,
                    endedAt = 2000L,
                    mediaCount = 1,
                    status = SessionStatus.READY.name,
                    deliveryStatus = "NOT_DELIVERED"
                )
            )

            val session = CaptureSession(
                id = sessionId,
                sourcePackage = "com.camera",
                mediaType = com.example.core.enum.MediaType.IMAGE,
                startedAt = 1000L,
                endedAt = 2000L,
                mediaCount = 1,
                notificationMode = NotificationMode.SILENT,
                status = SessionStatus.READY,
                deliveryStatus = com.example.core.enum.DeliveryStatus.NOT_DELIVERED
            )

            coordinator.deliverSession(session)

            val updated = db.captureSessionDao().getById(sessionId)
            assertNotNull(updated)
            assertEquals("DELIVERED_SILENT", updated?.deliveryStatus)
        }
    }
}
