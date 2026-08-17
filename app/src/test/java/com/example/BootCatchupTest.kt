package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.enum.MediaStatus
import com.example.core.enum.MediaType
import com.example.core.enum.ReconcileMode
import com.example.core.enum.ExpireAction
import com.example.core.model.CaptureSession
import com.example.core.model.MediaAsset
import com.example.core.model.SystemMedia
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.SourceRuleEntity
import com.example.data.repository.AppSettingsRepository
import com.example.data.repository.CaptureSessionRepository
import com.example.data.repository.CategoryRepository
import com.example.data.repository.MediaRepository
import com.example.data.repository.SourceRuleRepository
import com.example.media.MediaStoreDataSource
import com.example.policy.DefaultCategoryPolicyEngine
import com.example.policy.DefaultSourceRuleEngine
import com.example.watcher.CaptureSessionAggregator
import com.example.watcher.MediaReconciler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class FakeMediaStoreDataSource(context: Context) : MediaStoreDataSource(context) {
    val fakeMedia = mutableListOf<SystemMedia>()

    override suspend fun queryAll(): List<SystemMedia> {
        return fakeMedia.toList()
    }

    override suspend fun queryLatest(sinceAddedAt: Long): List<SystemMedia> {
        return fakeMedia.filter { it.dateAdded >= sinceAddedAt }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootCatchupTest {

    private lateinit var db: AppDatabase
    private lateinit var mediaRepository: MediaRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var sourceRuleRepository: SourceRuleRepository
    private lateinit var sessionRepository: CaptureSessionRepository
    private lateinit var settingsRepository: AppSettingsRepository
    private lateinit var fakeMediaStoreDataSource: FakeMediaStoreDataSource
    private lateinit var aggregator: CaptureSessionAggregator
    private lateinit var reconciler: MediaReconciler
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        mediaRepository = MediaRepository(db)
        categoryRepository = CategoryRepository(db)
        sourceRuleRepository = SourceRuleRepository(db)
        sessionRepository = CaptureSessionRepository(db)
        settingsRepository = AppSettingsRepository(context)

        fakeMediaStoreDataSource = FakeMediaStoreDataSource(context)
        val ruleEngine = DefaultSourceRuleEngine(sourceRuleRepository, categoryRepository)
        val policyEngine = DefaultCategoryPolicyEngine()
        val scope = CoroutineScope(Dispatchers.Unconfined)

        aggregator = CaptureSessionAggregator(sessionRepository, mediaRepository, settingsRepository, scope)
        reconciler = MediaReconciler(
            mediaStoreDataSource = fakeMediaStoreDataSource,
            mediaRepository = mediaRepository,
            categoryRepository = categoryRepository,
            sourceRuleRepository = sourceRuleRepository,
            ruleEngine = ruleEngine,
            policyEngine = policyEngine,
            aggregator = aggregator
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testBootCatchupHorizonFiltering() {
        runBlocking {
            val now = System.currentTimeMillis()
            val recentTime = now - (2 * 60 * 1000L) // 2 minutes ago (within 10m horizon)
            val oldTime = now - (60 * 60 * 1000L) // 1 hour ago (outside 10m horizon)

            fakeMediaStoreDataSource.fakeMedia.add(
                SystemMedia(
                    mediaStoreId = 101L,
                    contentUri = "content://media/external/images/media/101",
                    mediaType = MediaType.IMAGE,
                    mimeType = "image/jpeg",
                    displayName = "recent.jpg",
                    ownerPackage = "com.camera",
                    relativePath = "DCIM/Camera/",
                    bucketName = "Camera",
                    width = 1920,
                    height = 1080,
                    sizeBytes = 1024000L,
                    dateTaken = recentTime,
                    dateAdded = recentTime
                )
            )
            fakeMediaStoreDataSource.fakeMedia.add(
                SystemMedia(
                    mediaStoreId = 102L,
                    contentUri = "content://media/external/images/media/102",
                    mediaType = MediaType.IMAGE,
                    mimeType = "image/jpeg",
                    displayName = "old.jpg",
                    ownerPackage = "com.camera",
                    relativePath = "DCIM/Camera/",
                    bucketName = "Camera",
                    width = 1920,
                    height = 1080,
                    sizeBytes = 1024000L,
                    dateTaken = oldTime,
                    dateAdded = oldTime
                )
            )

            // Run BOOT_CATCHUP
            val result = reconciler.reconcile(ReconcileMode.BOOT_CATCHUP)
            assertEquals(2, result)

            val recentAsset = db.mediaAssetDao().getById(1L)
            val oldAsset = db.mediaAssetDao().getById(2L)

            assertNotNull(recentAsset)
            assertNotNull(oldAsset)

            // Recent asset should be PENDING and assigned to a session
            assertEquals(MediaStatus.PENDING.name, recentAsset?.status)
            assertNotNull(recentAsset?.captureSessionId)

            // Old asset should still be explicitly pending, but should not create
            // a noisy notification/session during boot catch-up.
            assertEquals(MediaStatus.PENDING.name, oldAsset?.status)
            assertNull(oldAsset?.captureSessionId)
        }
    }

    @Test
    fun initialBackfillLeavesExistingMediaForUserToOrganize() {
        runBlocking {
            val now = System.currentTimeMillis()
            val screenshotCategoryId = db.categoryDao().insert(
                CategoryEntity(
                    name = "截图",
                    systemKey = "screenshots",
                    retentionDays = 30,
                    expireAction = ExpireAction.REVIEW_DELETE.name,
                    isSystem = true,
                    createdAt = now,
                    updatedAt = now
                )
            )
            db.sourceRuleDao().insert(
                SourceRuleEntity(
                    name = "截图自动分类",
                    enabled = true,
                    priority = 100,
                    relativePathPattern = "%Screenshots%",
                    targetCategoryId = screenshotCategoryId,
                    autoClassify = true
                )
            )
            fakeMediaStoreDataSource.fakeMedia.add(
                SystemMedia(
                    mediaStoreId = 201L,
                    contentUri = "content://media/external/images/media/201",
                    mediaType = MediaType.IMAGE,
                    mimeType = "image/png",
                    displayName = "Screenshot_201.png",
                    ownerPackage = null,
                    relativePath = "Pictures/Screenshots/",
                    bucketName = "Screenshots",
                    width = 1080,
                    height = 2400,
                    sizeBytes = 500_000L,
                    dateTaken = now - 60_000L,
                    dateAdded = now - 60_000L
                )
            )

            assertEquals(1, reconciler.reconcile(ReconcileMode.INITIAL_BACKFILL))

            val asset = db.mediaAssetDao().getById(1L)
            assertEquals(MediaStatus.PENDING.name, asset?.status)
            assertNull(asset?.primaryCategoryId)
            assertNull(asset?.captureSessionId)
        }
    }
}
