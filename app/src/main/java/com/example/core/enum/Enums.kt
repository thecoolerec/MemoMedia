package com.example.core.enum

enum class MediaType {
    IMAGE,
    VIDEO
}

enum class MediaStatus {
    PENDING,
    CLASSIFIED,
    EXPIRED,
    PENDING_DELETE,
    DELETED,
    MISSING
}

enum class SessionStatus {
    COLLECTING,
    READY,
    CLASSIFIED,
    DISMISSED
}

enum class NotificationMode {
    OVERLAY,
    HEADS_UP,
    SILENT
}

enum class ExpireAction {
    REVIEW_DELETE,
    AUTO_DELETE
}
