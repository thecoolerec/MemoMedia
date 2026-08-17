package com.example.core.model

import com.example.core.enum.DeliveryStatus
import com.example.core.enum.ExpireAction
import com.example.core.enum.MediaStatus
import com.example.core.enum.MediaType
import com.example.core.enum.NotificationMode
import com.example.core.enum.SessionStatus

data class MediaAsset(
    val id: Long = 0,
    val mediaStoreId: Long,
    val contentUri: String,
    val mediaType: MediaType,
    val mimeType: String? = null,
    val displayName: String? = null,
    val ownerPackage: String? = null,
    val relativePath: String? = null,
    val bucketName: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null,
    val capturedAt: Long? = null,
    val addedAt: Long,
    val primaryCategoryId: Long? = null,
    val captureSessionId: Long? = null,
    val expireAt: Long? = null,
    val status: MediaStatus,
    val indexedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Category(
    val id: Long = 0,
    val systemKey: String? = null,
    val name: String,
    val icon: String? = null,
    val sortOrder: Int = 0,
    val retentionDays: Int? = null, // null means permanent retention
    val expireAction: ExpireAction = ExpireAction.REVIEW_DELETE,
    val notificationMode: NotificationMode? = NotificationMode.OVERLAY,
    val indexingEnabled: Boolean = true,
    val isSystem: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Tag(
    val id: Long = 0,
    val name: String
)

data class CaptureSession(
    val id: Long = 0,
    val sourcePackage: String? = null,
    val mediaType: MediaType = MediaType.IMAGE,
    val startedAt: Long,
    val endedAt: Long,
    val mediaCount: Int,
    val status: SessionStatus = SessionStatus.COLLECTING,
    val deliveryStatus: DeliveryStatus = DeliveryStatus.NOT_DELIVERED,
    val notificationMode: NotificationMode? = NotificationMode.OVERLAY
)

data class MediaRoutingDecision(
    val matchedRule: SourceRule? = null,
    val categoryId: Long? = null,
    val autoClassify: Boolean = false,
    val notificationMode: NotificationMode = NotificationMode.OVERLAY,
    val indexingEnabled: Boolean = true
)

data class SourceRule(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val sourcePackage: String? = null,
    val relativePathPattern: String? = null,
    val bucketPattern: String? = null,
    val mediaType: MediaType? = null,
    val targetCategoryId: Long? = null,
    val notificationMode: NotificationMode? = NotificationMode.OVERLAY,
    val autoClassify: Boolean = false
)

data class SystemMedia(
    val mediaStoreId: Long,
    val contentUri: String,
    val mediaType: MediaType,
    val mimeType: String?,
    val displayName: String?,
    val ownerPackage: String?,
    val relativePath: String?,
    val bucketName: String?,
    val width: Int?,
    val height: Int?,
    val sizeBytes: Long?,
    val dateTaken: Long?,
    val dateAdded: Long
)
