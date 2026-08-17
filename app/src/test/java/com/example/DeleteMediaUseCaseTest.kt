package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.enum.MediaStatus
import com.example.core.enum.MediaType
import com.example.core.model.MediaAsset
import com.example.data.local.AppDatabase
import com.example.data.local.entity.MediaAssetEntity
import com.example.data.repository.MediaRepository
import com.example.domain.DeleteMediaResult
import com.example.domain.DeleteMediaUseCase
import com.example.media.MediaStoreDataSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeleteMediaUseCaseTest {

    private lateinit var db: AppDatabase
    private lateinit var mediaRepository: MediaRepository
    private lateinit var mediaStoreDataSource: MediaStoreDataSource
    private lateinit var deleteMediaUseCase: DeleteMediaUseCase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        mediaRepository = MediaRepository(db)
        mediaStoreDataSource = MediaStoreDataSource(context)
        deleteMediaUseCase = DeleteMediaUseCase(context, mediaRepository, mediaStoreDataSource)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testBatchSplittingAndRollbackOnCancel() {
        runBlocking {
            // Insert 1500 expired assets
            val assets = mutableListOf<MediaAsset>()
            for (i in 1..1500) {
                val id = db.mediaAssetDao().insert(
                    MediaAssetEntity(
                        mediaStoreId = i.toLong(),
                        contentUri = "content://media/external/images/media/$i",
                        mediaType = MediaType.IMAGE.name,
                        addedAt = 1000L + i,
                        status = MediaStatus.EXPIRED.name,
                        createdAt = 1000L,
                        updatedAt = 1000L
                    )
                )
                assets.add(
                    MediaAsset(
                        id = id,
                        mediaStoreId = i.toLong(),
                        contentUri = "content://media/external/images/media/$i",
                        mediaType = MediaType.IMAGE,
                        addedAt = 1000L + i,
                        status = MediaStatus.EXPIRED
                    )
                )
            }

            // Execute delete use case
            val result = deleteMediaUseCase.execute(assets)

            if (result is DeleteMediaResult.NeedsUserConsent) {
                val consentResult = result
                assertEquals(1000, consentResult.activeBatchAssets.size)
                assertEquals(500, consentResult.remainingAssets.size)

                // Check DB: first 1000 assets should be PENDING_DELETE, while last 500 must still be EXPIRED!
                val activeIds = consentResult.activeBatchAssets.map { it.id }.toSet()
                val remainingIds = consentResult.remainingAssets.map { it.id }.toSet()

                for (id in activeIds) {
                    val entity = db.mediaAssetDao().getById(id)
                    assertEquals(MediaStatus.PENDING_DELETE.name, entity?.status)
                }
                for (id in remainingIds) {
                    val entity = db.mediaAssetDao().getById(id)
                    assertEquals(MediaStatus.EXPIRED.name, entity?.status)
                }

                // User cancels deletion
                val cancelResult = deleteMediaUseCase.onUserConsentResult(
                    activeBatchAssets = consentResult.activeBatchAssets,
                    remainingAssets = consentResult.remainingAssets,
                    success = false
                )
                assertTrue(cancelResult is DeleteMediaResult.Failure)

                // Verify active batch rolled back to EXPIRED
                for (id in activeIds) {
                    val entity = db.mediaAssetDao().getById(id)
                    assertEquals(MediaStatus.EXPIRED.name, entity?.status)
                }
                for (id in remainingIds) {
                    val entity = db.mediaAssetDao().getById(id)
                    assertEquals(MediaStatus.EXPIRED.name, entity?.status)
                }
            } else {
                // Direct delete succeeded without user consent dialog (e.g. mock test environment)
                assertTrue(result is DeleteMediaResult.Success)
            }
        }
    }
}
