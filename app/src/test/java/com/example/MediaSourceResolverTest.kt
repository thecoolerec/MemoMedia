package com.example

import com.example.core.enum.MediaStatus
import com.example.core.enum.MediaType
import com.example.core.model.MediaAsset
import com.example.core.model.MediaSourceKind
import com.example.core.model.MediaSourceResolver
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSourceResolverTest {

    @Test
    fun `wechat path wins when owner package is missing`() {
        val asset = asset(
            ownerPackage = null,
            relativePath = "Pictures/WeiXin/",
            bucketName = "WeiXin"
        )

        val source = MediaSourceResolver.resolve(asset)

        assertEquals(MediaSourceKind.WECHAT, source.kind)
        assertEquals("微信", source.title)
    }

    @Test
    fun `missing owner does not automatically mean camera`() {
        val asset = asset(
            ownerPackage = null,
            relativePath = "Pictures/Misc/",
            bucketName = "Misc"
        )

        val source = MediaSourceResolver.resolve(asset)

        assertEquals(MediaSourceKind.OTHER, source.kind)
        assertEquals("Misc", source.title)
    }

    @Test
    fun `camera directory is recognized as camera`() {
        val asset = asset(
            ownerPackage = null,
            relativePath = "DCIM/Camera/",
            bucketName = "Camera"
        )

        val source = MediaSourceResolver.resolve(asset)

        assertEquals(MediaSourceKind.CAMERA, source.kind)
        assertEquals("相机", source.title)
    }

    @Test
    fun `screenshot directory is recognized before generic bucket`() {
        val asset = asset(
            ownerPackage = null,
            relativePath = "Pictures/Screenshots/",
            bucketName = "Screenshots"
        )

        val source = MediaSourceResolver.resolve(asset)

        assertEquals(MediaSourceKind.SCREENSHOT, source.kind)
        assertEquals("屏幕截图", source.title)
    }

    private fun asset(
        ownerPackage: String?,
        relativePath: String?,
        bucketName: String?
    ) = MediaAsset(
        mediaStoreId = 1L,
        contentUri = "content://media/external/images/media/1",
        mediaType = MediaType.IMAGE,
        ownerPackage = ownerPackage,
        relativePath = relativePath,
        bucketName = bucketName,
        addedAt = 1_000L,
        status = MediaStatus.UNCLASSIFIED
    )
}
