package com.example.core.search

import com.example.core.model.MediaAsset

sealed interface MediaSearchQuery {
    data class SimilarImage(val mediaId: Long) : MediaSearchQuery
    data class Text(val text: String) : MediaSearchQuery
}

data class MediaSearchResult(
    val asset: MediaAsset,
    val score: Float = 1.0f,
    val matchedReason: String? = null
)

interface MediaSearchEngine {
    suspend fun search(query: MediaSearchQuery): List<MediaSearchResult>
}
