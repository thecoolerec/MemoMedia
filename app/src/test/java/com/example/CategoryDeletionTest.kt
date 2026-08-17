package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.enum.ExpireAction
import com.example.core.enum.MediaStatus
import com.example.core.enum.MediaType
import com.example.data.local.AppDatabase
import com.example.data.local.entity.CaptureSessionEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.MediaAssetEntity
import com.example.data.local.entity.SourceRuleEntity
import com.example.data.repository.CategoryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategoryDeletionTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: CategoryRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = CategoryRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deletingCategoryMovesItsMediaBackToPendingAndDisablesRule() = runBlocking {
        val now = System.currentTimeMillis()
        val categoryId = db.categoryDao().insert(
            CategoryEntity(
                name = "项目资料",
                retentionDays = 7,
                expireAction = ExpireAction.REVIEW_DELETE.name,
                isSystem = false,
                createdAt = now,
                updatedAt = now
            )
        )
        val sessionId = db.captureSessionDao().insert(
            CaptureSessionEntity(
                mediaType = MediaType.IMAGE.name,
                startedAt = now,
                endedAt = now,
                mediaCount = 1,
                status = "CLASSIFIED"
            )
        )
        val mediaId = db.mediaAssetDao().insert(
            MediaAssetEntity(
                mediaStoreId = 301L,
                contentUri = "content://media/301",
                mediaType = MediaType.IMAGE.name,
                addedAt = now,
                primaryCategoryId = categoryId,
                captureSessionId = sessionId,
                expireAt = now + 86_400_000L,
                status = MediaStatus.CLASSIFIED.name,
                createdAt = now,
                updatedAt = now
            )
        )
        val ruleId = db.sourceRuleDao().insert(
            SourceRuleEntity(
                name = "项目资料自动分类",
                enabled = true,
                priority = 10,
                targetCategoryId = categoryId,
                autoClassify = true
            )
        )

        assertTrue(repository.delete(categoryId))

        val media = db.mediaAssetDao().getById(mediaId)
        assertEquals(MediaStatus.PENDING.name, media?.status)
        assertNull(media?.primaryCategoryId)
        assertNull(media?.captureSessionId)
        assertNull(media?.expireAt)
        assertEquals(listOf(mediaId), db.mediaAssetDao().observePending().first().map { it.id })

        val rule = db.sourceRuleDao().getById(ruleId)
        assertNotNull(rule)
        assertNull(rule?.targetCategoryId)
        assertFalse(rule?.autoClassify ?: true)
        assertFalse(rule?.enabled ?: true)
        assertNull(db.categoryDao().getById(categoryId))
    }

    @Test
    fun systemCategoryCannotBeDeletedOrUnlinked() = runBlocking {
        val now = System.currentTimeMillis()
        val categoryId = db.categoryDao().insert(
            CategoryEntity(
                name = "系统分类",
                systemKey = "system-test",
                expireAction = ExpireAction.REVIEW_DELETE.name,
                isSystem = true,
                createdAt = now,
                updatedAt = now
            )
        )
        val mediaId = db.mediaAssetDao().insert(
            MediaAssetEntity(
                mediaStoreId = 302L,
                contentUri = "content://media/302",
                mediaType = MediaType.IMAGE.name,
                addedAt = now,
                primaryCategoryId = categoryId,
                status = MediaStatus.CLASSIFIED.name,
                createdAt = now,
                updatedAt = now
            )
        )

        assertFalse(repository.delete(categoryId))
        assertNotNull(db.categoryDao().getById(categoryId))
        assertEquals(categoryId, db.mediaAssetDao().getById(mediaId)?.primaryCategoryId)
    }
}
