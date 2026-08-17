package com.example.core.enum

enum class MediaType {
    IMAGE,
    VIDEO
}

enum class MediaStatus {
    UNCLASSIFIED,
    PENDING,
    CLASSIFIED,
    EXPIRED,
    PENDING_DELETE,
    DELETED,
    DELETE_FAILED,
    MISSING
}

enum class ReconcileMode {
    INITIAL_BACKFILL,
    LIVE_INCREMENTAL,
    BOOT_CATCHUP,
    FULL_REPAIR
}

enum class SessionStatus {
    COLLECTING,
    READY,
    CLASSIFIED,
    DISMISSED
}

enum class NotificationMode {
    OVERLAY,
    NOTIFICATION,
    HEADS_UP,
    SILENT
}

enum class DeliveryStatus {
    NOT_DELIVERED,
    DELIVERED_OVERLAY,
    DELIVERED_NOTIFICATION,
    DELIVERED_SILENT
}

enum class ExpireAction {
    REVIEW_DELETE,
    AUTO_DELETE
}
