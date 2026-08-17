package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CaptureSessionEntity
import com.example.data.local.entity.MediaAssetEntity
import com.example.data.local.entity.MediaTagEntity
import com.example.data.local.entity.SourceRuleEntity
import com.example.data.local.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaAssetDao {
    @Query("SELECT * FROM media_asset WHERE status != 'DELETED' ORDER BY captured_at DESC, added_at DESC")
    fun observeTimeline(): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_asset WHERE primary_category_id IS NULL AND status NOT IN ('DELETED', 'PENDING_DELETE', 'MISSING') ORDER BY captured_at DESC, added_at DESC")
    fun observePending(): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_asset WHERE primary_category_id IS NULL AND status NOT IN ('DELETED', 'PENDING_DELETE', 'MISSING') ORDER BY captured_at DESC, added_at DESC")
    fun observeUnclassified(): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_asset WHERE primary_category_id = :categoryId AND status != 'DELETED' ORDER BY captured_at DESC, added_at DESC")
    fun observeByCategory(categoryId: Long): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_asset WHERE capture_session_id = :sessionId AND status != 'DELETED' ORDER BY captured_at DESC")
    fun observeBySession(sessionId: Long): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_asset WHERE capture_session_id = :sessionId AND status != 'DELETED' ORDER BY captured_at DESC")
    suspend fun getBySession(sessionId: Long): List<MediaAssetEntity>

    @Query("SELECT * FROM media_asset WHERE expire_at IS NOT NULL AND expire_at <= :now AND status != 'DELETED'")
    suspend fun findExpired(now: Long): List<MediaAssetEntity>

    @Query("SELECT * FROM media_asset WHERE status = 'EXPIRED' ORDER BY expire_at ASC")
    fun observeExpired(): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_asset WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MediaAssetEntity?

    @Query("SELECT * FROM media_asset WHERE content_uri = :contentUri LIMIT 1")
    suspend fun getByContentUri(contentUri: String): MediaAssetEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM media_asset WHERE content_uri = :contentUri)")
    suspend fun exists(contentUri: String): Boolean

    @Query("SELECT content_uri FROM media_asset WHERE status != 'DELETED'")
    suspend fun getAllKnownContentUris(): List<String>

    @Query("SELECT MAX(added_at) FROM media_asset")
    suspend fun getMaxAddedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(asset: MediaAssetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(assets: List<MediaAssetEntity>): List<Long>

    @Update
    suspend fun update(asset: MediaAssetEntity)

    @Update
    suspend fun updateAll(assets: List<MediaAssetEntity>)

    @Query("UPDATE media_asset SET primary_category_id = :categoryId, expire_at = :expireAt, status = 'CLASSIFIED', updated_at = :now WHERE id = :id")
    suspend fun updateCategoryAndExpire(id: Long, categoryId: Long, expireAt: Long?, now: Long)

    @Query("UPDATE media_asset SET primary_category_id = :categoryId, expire_at = :expireAt, status = 'CLASSIFIED', updated_at = :now WHERE capture_session_id = :sessionId")
    suspend fun updateSessionCategory(sessionId: Long, categoryId: Long, expireAt: Long?, now: Long)

    @Query("UPDATE media_asset SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, now: Long)

    @Query("UPDATE media_asset SET status = 'DELETED', updated_at = :now WHERE id IN (:ids)")
    suspend fun markDeleted(ids: List<Long>, now: Long)

    @Query("SELECT COUNT(*) FROM media_asset WHERE status != 'DELETED'")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_asset WHERE primary_category_id IS NULL AND status NOT IN ('DELETED', 'PENDING_DELETE', 'MISSING')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_asset WHERE primary_category_id IS NULL AND status NOT IN ('DELETED', 'PENDING_DELETE', 'MISSING')")
    fun observeUnclassifiedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_asset WHERE primary_category_id = :categoryId AND status != 'DELETED'")
    fun observeCountByCategory(categoryId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM media_asset WHERE status = 'EXPIRED'")
    fun observeExpiredCount(): Flow<Int>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM category ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category ORDER BY sort_order ASC, id ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM category WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): CategoryEntity?

    @Query("SELECT * FROM category WHERE system_key = :systemKey LIMIT 1")
    suspend fun getBySystemKey(systemKey: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>): List<Long>

    @Update
    suspend fun update(category: CategoryEntity)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM category WHERE id = :id AND is_system = 0")
    suspend fun deleteById(id: Long)

    @Query("UPDATE media_asset SET primary_category_id = NULL, capture_session_id = NULL, expire_at = NULL, status = 'PENDING', updated_at = :now WHERE primary_category_id = :categoryId")
    suspend fun moveMediaBackToPending(categoryId: Long, now: Long)

    @Query("UPDATE source_rule SET target_category_id = NULL, auto_classify = 0, enabled = 0 WHERE target_category_id = :categoryId")
    suspend fun nullifySourceRuleCategory(categoryId: Long)

    @Transaction
    suspend fun deleteCategoryAndUnlink(id: Long): Boolean {
        val category = getById(id) ?: return false
        if (category.isSystem) return false

        moveMediaBackToPending(id, System.currentTimeMillis())
        nullifySourceRuleCategory(id)
        deleteById(id)
        return true
    }
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tag ORDER BY name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tag WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Query("""
        SELECT t.* FROM tag t
        INNER JOIN media_tag mt ON t.id = mt.tag_id
        WHERE mt.media_id = :mediaId
    """)
    fun observeTagsForMedia(mediaId: Long): Flow<List<TagEntity>>

    @Query("""
        SELECT t.* FROM tag t
        INNER JOIN media_tag mt ON t.id = mt.tag_id
        WHERE mt.media_id = :mediaId
    """)
    suspend fun getTagsForMedia(mediaId: Long): List<TagEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTagToMedia(mediaTag: MediaTagEntity)

    @Query("DELETE FROM media_tag WHERE media_id = :mediaId AND tag_id = :tagId")
    suspend fun removeTagFromMedia(mediaId: Long, tagId: Long)
}

@Dao
interface CaptureSessionDao {
    @Query("SELECT * FROM capture_session ORDER BY started_at DESC")
    fun observeAll(): Flow<List<CaptureSessionEntity>>

    @Query("SELECT * FROM capture_session WHERE status = 'READY' AND delivery_status = 'NOT_DELIVERED' ORDER BY started_at DESC")
    suspend fun getUndeliveredReadySessions(): List<CaptureSessionEntity>

    @Query("SELECT * FROM capture_session WHERE status = 'READY' OR status = 'COLLECTING' ORDER BY started_at DESC")
    fun observeActiveSessions(): Flow<List<CaptureSessionEntity>>

    @Query("SELECT * FROM capture_session WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CaptureSessionEntity?

    @Query("SELECT * FROM capture_session WHERE status = 'COLLECTING' ORDER BY started_at DESC LIMIT 1")
    suspend fun getLatestCollectingSession(): CaptureSessionEntity?

    @Query("SELECT * FROM capture_session WHERE status = 'COLLECTING' ORDER BY started_at DESC")
    suspend fun getAllCollectingSessions(): List<CaptureSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: CaptureSessionEntity): Long

    @Update
    suspend fun update(session: CaptureSessionEntity)

    @Query("UPDATE capture_session SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE capture_session SET delivery_status = :deliveryStatus WHERE id = :id")
    suspend fun updateDeliveryStatus(id: Long, deliveryStatus: String)

    @Query("UPDATE capture_session SET media_count = media_count + 1, ended_at = :endedAt WHERE id = :id")
    suspend fun incrementMediaCount(id: Long, endedAt: Long)
}

@Dao
interface SourceRuleDao {
    @Query("SELECT * FROM source_rule ORDER BY priority DESC, id ASC")
    fun observeAll(): Flow<List<SourceRuleEntity>>

    @Query("SELECT * FROM source_rule WHERE enabled = 1 ORDER BY priority DESC, id ASC")
    suspend fun getActiveRules(): List<SourceRuleEntity>

    @Query("SELECT * FROM source_rule WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SourceRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: SourceRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(rules: List<SourceRuleEntity>): List<Long>

    @Update
    suspend fun update(rule: SourceRuleEntity)

    @Delete
    suspend fun delete(rule: SourceRuleEntity)
}
