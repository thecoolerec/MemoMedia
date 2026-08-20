package com.example.core.model

enum class MediaSourceKind {
    CAMERA,
    WECHAT,
    QQ,
    SCREENSHOT,
    DOWNLOADS,
    OTHER
}

data class MediaSourceInfo(
    val key: String,
    val title: String,
    val kind: MediaSourceKind,
    val detail: String? = null
)

/**
 * Best-effort source inference for user-facing grouping.
 *
 * MediaStore.OWNER_PACKAGE_NAME is useful when available, but it is not guaranteed to be
 * populated. For that reason source attribution also considers RELATIVE_PATH, bucket name and
 * display name. This intentionally stays heuristic: it is a presentation/grouping hint, not a
 * security boundary or a reliable record of which process created the file.
 */
object MediaSourceResolver {
    fun resolve(asset: MediaAsset): MediaSourceInfo {
        val owner = asset.ownerPackage.orEmpty().lowercase()
        val path = asset.relativePath.orEmpty().replace('\\', '/').lowercase()
        val bucket = asset.bucketName.orEmpty().lowercase()
        val name = asset.displayName.orEmpty().lowercase()
        val combined = "$path $bucket $name"

        if (
            owner.contains("com.tencent.mm") ||
            combined.contains("weixin") ||
            combined.contains("wechat") ||
            combined.contains("micromsg")
        ) {
            return MediaSourceInfo(
                key = "wechat",
                title = "微信",
                kind = MediaSourceKind.WECHAT,
                detail = asset.relativePath ?: asset.bucketName
            )
        }

        if (
            owner.contains("com.tencent.mobileqq") ||
            owner.contains("com.tencent.tim") ||
            combined.contains("mobileqq") ||
            path.contains("/qq/") ||
            bucket == "qq"
        ) {
            return MediaSourceInfo(
                key = "qq",
                title = "QQ",
                kind = MediaSourceKind.QQ,
                detail = asset.relativePath ?: asset.bucketName
            )
        }

        if (
            combined.contains("screenshot") ||
            combined.contains("screen_shot") ||
            combined.contains("screenshots") ||
            name.startsWith("screenshot_")
        ) {
            return MediaSourceInfo(
                key = "screenshots",
                title = "屏幕截图",
                kind = MediaSourceKind.SCREENSHOT,
                detail = asset.relativePath ?: asset.bucketName
            )
        }

        if (
            path.startsWith("download/") ||
            path.startsWith("downloads/") ||
            bucket == "download" ||
            bucket == "downloads"
        ) {
            return MediaSourceInfo(
                key = "downloads",
                title = "下载",
                kind = MediaSourceKind.DOWNLOADS,
                detail = asset.relativePath ?: asset.bucketName
            )
        }

        if (
            owner.contains("camera") ||
            owner.contains("camera2") ||
            path.startsWith("dcim/camera") ||
            bucket == "camera"
        ) {
            return MediaSourceInfo(
                key = "camera",
                title = "相机",
                kind = MediaSourceKind.CAMERA,
                detail = asset.relativePath ?: asset.bucketName
            )
        }

        val bucketTitle = asset.bucketName?.trim().orEmpty()
        if (bucketTitle.isNotEmpty()) {
            return MediaSourceInfo(
                key = "bucket:${bucketTitle.lowercase()}",
                title = bucketTitle,
                kind = MediaSourceKind.OTHER,
                detail = asset.relativePath
            )
        }

        val pathTitle = asset.relativePath
            ?.trim('/')
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
        if (pathTitle != null) {
            return MediaSourceInfo(
                key = "path:${pathTitle.lowercase()}",
                title = pathTitle,
                kind = MediaSourceKind.OTHER,
                detail = asset.relativePath
            )
        }

        return MediaSourceInfo(
            key = "other",
            title = "其他来源",
            kind = MediaSourceKind.OTHER
        )
    }
}
