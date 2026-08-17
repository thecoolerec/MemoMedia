package com.example.domain

import android.content.Context
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import com.example.core.enum.MediaStatus
import com.example.core.model.MediaAsset
import com.example.data.repository.MediaRepository
import com.example.media.MediaDeletionHelper
import com.example.media.MediaStoreDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface DeleteMediaResult {
    data class Success(val deletedCount: Int) : DeleteMediaResult
    data class NeedsUserConsent(
        val intentSenderRequest: IntentSenderRequest,
        val activeBatchAssets: List<MediaAsset>,
        val remainingAssets: List<MediaAsset>
    ) : DeleteMediaResult
    data class Failure(val error: Throwable) : DeleteMediaResult
}

class DeleteMediaUseCase(
    private val context: Context,
    private val mediaRepository: MediaRepository,
    private val mediaStoreDataSource: MediaStoreDataSource
) {
    companion object {
        const val DELETE_BATCH_SIZE = 1000
    }

    suspend fun execute(assets: List<MediaAsset>): DeleteMediaResult = withContext(Dispatchers.IO) {
        if (assets.isEmpty()) return@withContext DeleteMediaResult.Success(0)

        val currentBatch = assets.take(DELETE_BATCH_SIZE)
        val remaining = assets.drop(DELETE_BATCH_SIZE)

        // Mark PENDING_DELETE ONLY for current batch
        val ids = currentBatch.map { it.id }
        for (id in ids) {
            mediaRepository.updateStatus(id, MediaStatus.PENDING_DELETE)
        }

        val uris = currentBatch.map { it.contentUri }
        val deleteRequest = MediaDeletionHelper.createDeleteRequestOrDeleteDirectly(context, uris)

        if (deleteRequest != null) {
            DeleteMediaResult.NeedsUserConsent(
                intentSenderRequest = deleteRequest,
                activeBatchAssets = currentBatch,
                remainingAssets = remaining
            )
        } else {
            // Direct delete attempted on Android 10 and below, verify now
            val batchDeletedCount = verifyAndFinalize(currentBatch)
            if (remaining.isNotEmpty()) {
                val nextResult = execute(remaining)
                when (nextResult) {
                    is DeleteMediaResult.Success -> DeleteMediaResult.Success(batchDeletedCount + nextResult.deletedCount)
                    else -> nextResult
                }
            } else {
                DeleteMediaResult.Success(batchDeletedCount)
            }
        }
    }

    suspend fun onUserConsentResult(
        activeBatchAssets: List<MediaAsset>,
        remainingAssets: List<MediaAsset>,
        success: Boolean
    ): DeleteMediaResult = withContext(Dispatchers.IO) {
        if (success) {
            val batchDeletedCount = verifyAndFinalize(activeBatchAssets)
            if (remainingAssets.isNotEmpty()) {
                execute(remainingAssets)
            } else {
                DeleteMediaResult.Success(batchDeletedCount)
            }
        } else {
            // User cancelled: Revert active batch from PENDING_DELETE back to EXPIRED
            for (asset in activeBatchAssets) {
                mediaRepository.updateStatus(asset.id, MediaStatus.EXPIRED)
            }
            DeleteMediaResult.Failure(Exception("User cancelled deletion"))
        }
    }

    suspend fun verifyAndFinalize(assets: List<MediaAsset>): Int {
        val reallyDeletedIds = mutableListOf<Long>()
        for (asset in assets) {
            val exists = mediaStoreDataSource.exists(Uri.parse(asset.contentUri))
            if (!exists) {
                reallyDeletedIds.add(asset.id)
            } else {
                mediaRepository.updateStatus(asset.id, MediaStatus.DELETE_FAILED)
            }
        }
        if (reallyDeletedIds.isNotEmpty()) {
            mediaRepository.markDeleted(reallyDeletedIds)
        }
        return reallyDeletedIds.size
    }
}

