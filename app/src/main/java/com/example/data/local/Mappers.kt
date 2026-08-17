package com.example.data.local

import com.example.core.enum.ExpireAction
import com.example.core.enum.MediaStatus
import com.example.core.enum.MediaType
import com.example.core.enum.NotificationMode
import com.example.core.enum.SessionStatus
import com.example.core.model.Category
import com.example.core.model.CaptureSession
import com.example.core.model.MediaAsset
import com.example.core.model.SourceRule
import com.example.core.model.Tag
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.CaptureSessionEntity
import com.example.data.local.entity.MediaAssetEntity
import com.example.data.local.entity.SourceRuleEntity
import com.example.data.local.entity.TagEntity

fun MediaAssetEntity.toDomain(): MediaAsset = MediaAsset(
    id = id,
    mediaStoreId = mediaStoreId,
    contentUri = contentUri,
    mediaType = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.IMAGE),
    mimeType = mimeType,
    displayName = displayName,
    ownerPackage = ownerPackage,
    relativePath = relativePath,
    bucketName = bucketName,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    capturedAt = capturedAt,
    addedAt = addedAt,
    primaryCategoryId = primaryCategoryId,
    captureSessionId = captureSessionId,
    expireAt = expireAt,
    status = runCatching { MediaStatus.valueOf(status) }.getOrDefault(MediaStatus.PENDING),
    indexedAt = indexedAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun MediaAsset.toEntity(): MediaAssetEntity = MediaAssetEntity(
    id = id,
    mediaStoreId = mediaStoreId,
    contentUri = contentUri,
    mediaType = mediaType.name,
    mimeType = mimeType,
    displayName = displayName,
    ownerPackage = ownerPackage,
    relativePath = relativePath,
    bucketName = bucketName,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    capturedAt = capturedAt,
    addedAt = addedAt,
    primaryCategoryId = primaryCategoryId,
    captureSessionId = captureSessionId,
    expireAt = expireAt,
    status = status.name,
    indexedAt = indexedAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    systemKey = systemKey,
    name = name,
    icon = icon,
    sortOrder = sortOrder,
    retentionDays = retentionDays,
    expireAction = runCatching { ExpireAction.valueOf(expireAction) }.getOrDefault(ExpireAction.REVIEW_DELETE),
    notificationMode = notificationMode?.let { runCatching { NotificationMode.valueOf(it) }.getOrNull() } ?: NotificationMode.OVERLAY,
    indexingEnabled = indexingEnabled,
    isSystem = isSystem,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    systemKey = systemKey,
    name = name,
    icon = icon,
    sortOrder = sortOrder,
    retentionDays = retentionDays,
    expireAction = expireAction.name,
    notificationMode = notificationMode?.name,
    indexingEnabled = indexingEnabled,
    isSystem = isSystem,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun TagEntity.toDomain(): Tag = Tag(id = id, name = name)
fun Tag.toEntity(): TagEntity = TagEntity(id = id, name = name)

fun CaptureSessionEntity.toDomain(): CaptureSession = CaptureSession(
    id = id,
    sourcePackage = sourcePackage,
    mediaType = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.IMAGE),
    startedAt = startedAt,
    endedAt = endedAt,
    mediaCount = mediaCount,
    status = runCatching { SessionStatus.valueOf(status) }.getOrDefault(SessionStatus.COLLECTING),
    deliveryStatus = runCatching { com.example.core.enum.DeliveryStatus.valueOf(deliveryStatus) }.getOrDefault(com.example.core.enum.DeliveryStatus.NOT_DELIVERED),
    notificationMode = notificationMode?.let { runCatching { NotificationMode.valueOf(it) }.getOrNull() }
)

fun CaptureSession.toEntity(): CaptureSessionEntity = CaptureSessionEntity(
    id = id,
    sourcePackage = sourcePackage,
    mediaType = mediaType.name,
    startedAt = startedAt,
    endedAt = endedAt,
    mediaCount = mediaCount,
    status = status.name,
    deliveryStatus = deliveryStatus.name,
    notificationMode = notificationMode?.name
)

fun SourceRuleEntity.toDomain(): SourceRule = SourceRule(
    id = id,
    name = name,
    enabled = enabled,
    priority = priority,
    sourcePackage = sourcePackage,
    relativePathPattern = relativePathPattern,
    bucketPattern = bucketPattern,
    mediaType = mediaType?.let { runCatching { MediaType.valueOf(it) }.getOrNull() },
    targetCategoryId = targetCategoryId,
    notificationMode = notificationMode?.let { runCatching { NotificationMode.valueOf(it) }.getOrNull() },
    autoClassify = autoClassify
)

fun SourceRule.toEntity(): SourceRuleEntity = SourceRuleEntity(
    id = id,
    name = name,
    enabled = enabled,
    priority = priority,
    sourcePackage = sourcePackage,
    relativePathPattern = relativePathPattern,
    bucketPattern = bucketPattern,
    mediaType = mediaType?.name,
    targetCategoryId = targetCategoryId,
    notificationMode = notificationMode?.name,
    autoClassify = autoClassify
)
