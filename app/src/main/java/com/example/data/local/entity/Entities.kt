package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_asset",
    indices = [
        Index(value = ["content_uri"], unique = true),
        Index(value = ["captured_at"]),
        Index(value = ["added_at"]),
        Index(value = ["primary_category_id"]),
        Index(value = ["expire_at"]),
        Index(value = ["status"]),
        Index(value = ["owner_package"])
    ]
)
data class MediaAssetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "media_store_id")
    val mediaStoreId: Long,

    @ColumnInfo(name = "content_uri")
    val contentUri: String,

    @ColumnInfo(name = "media_type")
    val mediaType: String,

    @ColumnInfo(name = "mime_type")
    val mimeType: String? = null,

    @ColumnInfo(name = "display_name")
    val displayName: String? = null,

    @ColumnInfo(name = "owner_package")
    val ownerPackage: String? = null,

    @ColumnInfo(name = "relative_path")
    val relativePath: String? = null,

    @ColumnInfo(name = "bucket_name")
    val bucketName: String? = null,

    val width: Int? = null,
    val height: Int? = null,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long? = null,

    @ColumnInfo(name = "captured_at")
    val capturedAt: Long? = null,

    @ColumnInfo(name = "added_at")
    val addedAt: Long,

    @ColumnInfo(name = "primary_category_id")
    val primaryCategoryId: Long? = null,

    @ColumnInfo(name = "capture_session_id")
    val captureSessionId: Long? = null,

    @ColumnInfo(name = "expire_at")
    val expireAt: Long? = null,

    val status: String,

    @ColumnInfo(name = "indexed_at")
    val indexedAt: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "category",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    val icon: String? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "retention_days")
    val retentionDays: Int? = null,

    @ColumnInfo(name = "expire_action")
    val expireAction: String,

    @ColumnInfo(name = "notification_mode")
    val notificationMode: String? = null,

    @ColumnInfo(name = "indexing_enabled")
    val indexingEnabled: Boolean = true,

    @ColumnInfo(name = "is_system")
    val isSystem: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(tableName = "tag")
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String
)

@Entity(
    tableName = "media_tag",
    primaryKeys = ["media_id", "tag_id"]
)
data class MediaTagEntity(
    @ColumnInfo(name = "media_id")
    val mediaId: Long,

    @ColumnInfo(name = "tag_id")
    val tagId: Long
)

@Entity(
    tableName = "capture_session",
    indices = [
        Index(value = ["started_at"]),
        Index(value = ["status"])
    ]
)
data class CaptureSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "source_package")
    val sourcePackage: String? = null,

    @ColumnInfo(name = "media_type")
    val mediaType: String,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "ended_at")
    val endedAt: Long,

    @ColumnInfo(name = "media_count")
    val mediaCount: Int,

    val status: String,

    @ColumnInfo(name = "delivery_status")
    val deliveryStatus: String = "NOT_DELIVERED"
)

@Entity(
    tableName = "source_rule",
    indices = [
        Index(value = ["priority"])
    ]
)
data class SourceRuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    val enabled: Boolean,

    val priority: Int,

    @ColumnInfo(name = "source_package")
    val sourcePackage: String? = null,

    @ColumnInfo(name = "relative_path_pattern")
    val relativePathPattern: String? = null,

    @ColumnInfo(name = "bucket_pattern")
    val bucketPattern: String? = null,

    @ColumnInfo(name = "media_type")
    val mediaType: String? = null,

    @ColumnInfo(name = "target_category_id")
    val targetCategoryId: Long? = null,

    @ColumnInfo(name = "notification_mode")
    val notificationMode: String? = null,

    @ColumnInfo(name = "auto_classify")
    val autoClassify: Boolean = false
)
