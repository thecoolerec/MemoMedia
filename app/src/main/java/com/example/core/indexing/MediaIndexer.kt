package com.example.core.indexing

import com.example.core.model.MediaAsset

/**
 * Extensible interface for media indexing (Metadata, PHash, Vector Embeddings, OCR, Face).
 */
interface MediaIndexer {
    val id: String

    fun supports(asset: MediaAsset): Boolean

    suspend fun index(asset: MediaAsset)

    suspend fun remove(asset: MediaAsset)
}

/**
 * Standard V0.1 Metadata Indexer implementation.
 */
class MetadataIndexer : MediaIndexer {
    override val id: String = "metadata"

    override fun supports(asset: MediaAsset): Boolean = true

    override suspend fun index(asset: MediaAsset) {
        // Metadata indexing logic
    }

    override suspend fun remove(asset: MediaAsset) {
        // Cleanup indexed metadata
    }
}
