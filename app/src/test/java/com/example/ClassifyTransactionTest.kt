package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.enum.ExpireAction
import com.example.core.enum.MediaStatus
import com.example.core.enum.MediaType
import com.example.core.enum.SessionStatus
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CaptureSessionEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.MediaAssetEntity
import com.example.data.repository.CaptureSessionRepository
import com.example.data.repository.CategoryRepository
import com.example.data.repository.MediaRepository
import com.example.domain.ClassifySessionUseCase
import com.example.policy.DefaultCategoryPolicyEngine
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
class ClassifyTransactionTest {

    private lateinit var db: AppDatabase
    private lateinit var mediaRepository: MediaRepository
    private lateinit var sessionRepository: CaptureSessionRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var policyEngine: DefaultCategoryPolicyEngine
    private lateinit var useCase: ClassifySessionUseCase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        mediaRepository = MediaRepository(db)
        sessionRepository = CaptureSessionRepository(db)
        categoryRepository = CategoryRepository(db)
        policyEngine = DefaultCategoryPolicyEngine()
        useCase = ClassifySessionUseCase(
            db = db,
            mediaRepository = mediaRepository,
            captureSessionRepository = sessionRepository,
            categoryRepository = categoryRepository,
            policyEngine = policyEngine,
            notificationManager = null
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testClassifySessionAtomicity() {
        runBlocking {
            // Insert category
        val categoryId = db.categoryDao().insert(
            CategoryEntity(
                name = "临时",
                systemKey = "temporary",
                sortOrder = 1,
                retentionDays = 7,
                expireAction = ExpireAction.AUTO_DELETE.name,
                indexingEnabled = true,
                isSystem = true,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )

        // Insert session
        val sessionId = db.captureSessionDao().insert(
            CaptureSessionEntity(
                sourcePackage = "com.camera",
                mediaType = MediaType.IMAGE.name,
                startedAt = 1000L,
                endedAt = 2000L,
                mediaCount = 2,
                status = SessionStatus.READY.name
            )
        )

        // Insert 2 media items attached to session
        val asset1Id = db.mediaAssetDao().insert(
            MediaAssetEntity(
                mediaStoreId = 1L,
                contentUri = "content://media/1",
                mediaType = MediaType.IMAGE.name,
                addedAt = 1000L,
                capturedAt = 1000L,
                captureSessionId = sessionId,
                status = MediaStatus.PENDING.name,
                createdAt = 1000L,
                updatedAt = 1000L
            )
        )
        val asset2Id = db.mediaAssetDao().insert(
            MediaAssetEntity(
                mediaStoreId = 2L,
                contentUri = "content://media/2",
                mediaType = MediaType.IMAGE.name,
                addedAt = 1500L,
                capturedAt = 1500L,
                captureSessionId = sessionId,
                status = MediaStatus.PENDING.name,
                createdAt = 1500L,
                updatedAt = 1500L
            )
        )

        val resultCount = useCase(sessionId, categoryId)
        assertEquals(2, resultCount)

        // Verify session is marked CLASSIFIED
        val session = db.captureSessionDao().getById(sessionId)
        assertEquals(SessionStatus.CLASSIFIED.name, session?.status)

        // Verify both assets are marked CLASSIFIED with primaryCategoryId and calculated expireAt
        val a1 = db.mediaAssetDao().getById(asset1Id)
        val a2 = db.mediaAssetDao().getById(asset2Id)
        assertEquals(MediaStatus.CLASSIFIED.name, a1?.status)
        assertEquals(categoryId, a1?.primaryCategoryId)
        assertNotNull(a1?.expireAt)
        assertEquals(MediaStatus.CLASSIFIED.name, a2?.status)
        assertEquals(categoryId, a2?.primaryCategoryId)
        assertNotNull(a2?.expireAt)
        }
    }
}
