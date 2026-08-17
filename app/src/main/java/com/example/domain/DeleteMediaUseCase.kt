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
    data class NeedsUserConsent(val intentSenderRequest: IntentSenderRequest, val targetIds: List<Long>) : DeleteMediaResult
    data class Failure(val error: Throwable) : DeleteMediaResult
}

class DeleteMediaUseCase(
    private val context: Context,
    private val mediaRepository: MediaRepository,
    private val mediaStoreDataSource: MediaStoreDataSource
) {
    suspend fun execute(assets: List<MediaAsset>): DeleteMediaResult = withContext(Dispatchers.IO) {
        if (assets.isEmpty()) return@withContext DeleteMediaResult.Success(0)

        val ids = assets.map { it.id }
        // Mark PENDING_DELETE in database
        for (id in ids) {
            mediaRepository.updateStatus(id, MediaStatus.PENDING_DELETE)
        }

        val uris = assets.map { it.contentUri }
        val deleteRequest = MediaDeletionHelper.createDeleteRequestOrDeleteDirectly(context, uris)

        if (deleteRequest != null) {
            return@withContext DeleteMediaResult.NeedsUserConsent(deleteRequest, ids)
        } else {
            // Direct delete attempted on Android 10 and below, verify now
            return@withContext verifyAndFinalize(assets)
        }
    }

    suspend fun onUserConsentResult(assets: List<MediaAsset>, success: Boolean): DeleteMediaResult = withContext(Dispatchers.IO) {
        if (success) {
            verifyAndFinalize(assets)
        } else {
            for (asset in assets) {
                mediaRepository.updateStatus(asset.id, MediaStatus.DELETE_FAILED)
            }
            DeleteMediaResult.Failure(Exception("User cancelled deletion"))
        }
    }

    private suspend fun verifyAndFinalize(assets: List<MediaAsset>): DeleteMediaResult {
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
        return DeleteMediaResult.Success(reallyDeletedIds.size)
    }
}
