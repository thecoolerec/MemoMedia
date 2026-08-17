package com.example.data.repository

import com.example.core.enum.MediaStatus
import com.example.core.model.Category
import com.example.core.model.CaptureSession
import com.example.core.model.MediaAsset
import com.example.core.model.SourceRule
import com.example.core.model.Tag
import com.example.data.local.AppDatabase
import com.example.data.local.entity.MediaTagEntity
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MediaRepository(private val db: AppDatabase) {
    private val dao = db.mediaAssetDao()

    fun observeTimeline(): Flow<List<MediaAsset>> =
        dao.observeTimeline().map { list -> list.map { it.toDomain() } }

    fun observePending(): Flow<List<MediaAsset>> =
        dao.observePending().map { list -> list.map { it.toDomain() } }

    fun observeUnclassified(): Flow<List<MediaAsset>> =
        dao.observeUnclassified().map { list -> list.map { it.toDomain() } }

    fun observeByCategory(categoryId: Long): Flow<List<MediaAsset>> =
        dao.observeByCategory(categoryId).map { list -> list.map { it.toDomain() } }

    fun observeBySession(sessionId: Long): Flow<List<MediaAsset>> =
        dao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }

    fun observeExpired(): Flow<List<MediaAsset>> =
        dao.observeExpired().map { list -> list.map { it.toDomain() } }

    fun observeTotalCount(): Flow<Int> = dao.observeTotalCount()
    fun observePendingCount(): Flow<Int> = dao.observePendingCount()
    fun observeUnclassifiedCount(): Flow<Int> = dao.observeUnclassifiedCount()
    fun observeExpiredCount(): Flow<Int> = dao.observeExpiredCount()
    fun observeCountByCategory(categoryId: Long): Flow<Int> = dao.observeCountByCategory(categoryId)

    suspend fun getById(id: Long): MediaAsset? = dao.getById(id)?.toDomain()
    suspend fun getByContentUri(contentUri: String): MediaAsset? = dao.getByContentUri(contentUri)?.toDomain()
    suspend fun getBySession(sessionId: Long): List<MediaAsset> = dao.getBySession(sessionId).map { it.toDomain() }
    suspend fun exists(contentUri: String): Boolean = dao.exists(contentUri)
    suspend fun getAllKnownContentUris(): List<String> = dao.getAllKnownContentUris()
    suspend fun getMaxAddedAt(): Long = dao.getMaxAddedAt() ?: 0L

    suspend fun insert(asset: MediaAsset): Long = dao.insert(asset.toEntity())
    suspend fun insertAll(assets: List<MediaAsset>): List<Long> = dao.insertAll(assets.map { it.toEntity() })
    suspend fun update(asset: MediaAsset) = dao.update(asset.toEntity())

    suspend fun assignCategory(mediaId: Long, categoryId: Long, expireAt: Long?) {
        dao.updateCategoryAndExpire(mediaId, categoryId, expireAt, System.currentTimeMillis())
    }

    suspend fun assignSessionCategory(sessionId: Long, categoryId: Long, expireAt: Long?) {
        dao.updateSessionCategory(sessionId, categoryId, expireAt, System.currentTimeMillis())
    }

    suspend fun updateStatus(mediaId: Long, status: MediaStatus) {
        dao.updateStatus(mediaId, status.name, System.currentTimeMillis())
    }

    suspend fun markDeleted(ids: List<Long>) {
        dao.markDeleted(ids, System.currentTimeMillis())
    }

    suspend fun findExpired(now: Long = System.currentTimeMillis()): List<MediaAsset> =
        dao.findExpired(now).map { it.toDomain() }
}

class CategoryRepository(private val db: AppDatabase) {
    private val dao = db.categoryDao()

    fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAll(): List<Category> =
        dao.getAll().map { it.toDomain() }

    suspend fun getById(id: Long): Category? = dao.getById(id)?.toDomain()
    suspend fun getByName(name: String): Category? = dao.getByName(name)?.toDomain()
    suspend fun getBySystemKey(systemKey: String): Category? = dao.getBySystemKey(systemKey)?.toDomain()

    suspend fun save(category: Category): Long = dao.insert(category.toEntity())
    suspend fun update(category: Category) = dao.update(category.toEntity())
    suspend fun delete(id: Long): Boolean = dao.deleteCategoryAndUnlink(id)
}

class TagRepository(private val db: AppDatabase) {
    private val dao = db.tagDao()

    fun observeAll(): Flow<List<Tag>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeTagsForMedia(mediaId: Long): Flow<List<Tag>> =
        dao.observeTagsForMedia(mediaId).map { list -> list.map { it.toDomain() } }

    suspend fun getTagsForMedia(mediaId: Long): List<Tag> =
        dao.getTagsForMedia(mediaId).map { it.toDomain() }

    suspend fun createTag(name: String): Long {
        val existing = dao.getByName(name)
        if (existing != null) return existing.id
        return dao.insert(Tag(name = name).toEntity())
    }

    suspend fun addTagToMedia(mediaId: Long, tagId: Long) {
        dao.addTagToMedia(MediaTagEntity(mediaId = mediaId, tagId = tagId))
    }

    suspend fun removeTagFromMedia(mediaId: Long, tagId: Long) {
        dao.removeTagFromMedia(mediaId, tagId)
    }
}

class CaptureSessionRepository(private val db: AppDatabase) {
    private val dao = db.captureSessionDao()

    fun observeActiveSessions(): Flow<List<CaptureSession>> =
        dao.observeActiveSessions().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): CaptureSession? = dao.getById(id)?.toDomain()
    suspend fun getLatestCollectingSession(): CaptureSession? = dao.getLatestCollectingSession()?.toDomain()

    suspend fun getUndeliveredReadySessions(): List<CaptureSession> = dao.getUndeliveredReadySessions().map { it.toDomain() }
    suspend fun getAllCollectingSessions(): List<CaptureSession> = dao.getAllCollectingSessions().map { it.toDomain() }

    suspend fun save(session: CaptureSession): Long = dao.insert(session.toEntity())
    suspend fun update(session: CaptureSession) = dao.update(session.toEntity())
    suspend fun updateStatus(id: Long, status: String) = dao.updateStatus(id, status)
    suspend fun updateDeliveryStatus(id: Long, deliveryStatus: String) = dao.updateDeliveryStatus(id, deliveryStatus)
    suspend fun incrementMediaCount(id: Long, endedAt: Long) = dao.incrementMediaCount(id, endedAt)
}

class SourceRuleRepository(private val db: AppDatabase) {
    private val dao = db.sourceRuleDao()

    fun observeAll(): Flow<List<SourceRule>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getActiveRules(): List<SourceRule> =
        dao.getActiveRules().map { it.toDomain() }

    suspend fun save(rule: SourceRule): Long = dao.insert(rule.toEntity())
    suspend fun update(rule: SourceRule) = dao.update(rule.toEntity())
    suspend fun delete(rule: SourceRule) = dao.delete(rule.toEntity())
}
