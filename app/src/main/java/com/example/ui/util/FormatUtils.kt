package com.example.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.getDefault(), "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.getDefault(), "%.0f KB", kb)
        else -> "$bytes B"
    }
}

fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    if (diff < 60_000L) {
        return "刚刚"
    }
    val minutes = diff / 60_000L
    if (minutes < 60) {
        return "${minutes}分钟前"
    }
    val hours = diff / 3600_000L
    if (hours < 24) {
        return "${hours}小时前"
    }
    val days = diff / 86400_000L
    if (days == 1L) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return "昨天 ${timeFormat.format(Date(timestamp))}"
    }
    if (days < 7) {
        return "${days}天前"
    }
    val fullFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return fullFormat.format(Date(timestamp))
}
