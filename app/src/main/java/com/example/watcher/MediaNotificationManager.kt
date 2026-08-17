package com.example.watcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.core.model.CaptureSession
import com.example.core.model.Category

class MediaNotificationManager(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_MONITOR = "media_monitor_channel"
        const val CHANNEL_ALERT = "media_alert_channel"
        const val NOTIFICATION_ID_MONITOR = 1001
        const val NOTIFICATION_ID_ALERT = 2001
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val monitorChannel = NotificationChannel(
                CHANNEL_MONITOR,
                "媒体实时监听服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持媒体库变化监听，实时发现新照片与视频"
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT,
                "新照片快速分类提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "新拍摄或保存的照片到达时提醒快速整理"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(monitorChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    fun buildForegroundNotification(): Notification {
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setContentTitle("媒体实时整理服务运行中")
            .setContentText("正在监听系统相册、截图与第三方应用新照片")
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun getSessionNotificationId(sessionId: Long): Int {
        val hash = (sessionId xor (sessionId ushr 32)).toInt()
        return if (hash == NOTIFICATION_ID_MONITOR) hash + 100 else hash
    }

    fun showSessionReadyNotification(session: CaptureSession, categories: List<Category>) {
        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("OPEN_TAB", "inbox")
            putExtra("SESSION_ID", session.id)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            session.id.toInt(),
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sourceTitle = getSourceDisplayName(session.sourcePackage)
        val title = "$sourceTitle · 发现 ${session.mediaCount} 项新媒体"
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setContentTitle(title)
            .setContentText("点击进入待整理，或直接点击下方按钮一键分类")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        // Add top 3 action buttons for one-tap classification
        val topCategories = categories.take(3)
        for (category in topCategories) {
            val actionIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_CLASSIFY
                putExtra(NotificationActionReceiver.EXTRA_SESSION_ID, session.id)
                putExtra(NotificationActionReceiver.EXTRA_CATEGORY_ID, category.id)
                putExtra(NotificationActionReceiver.EXTRA_CATEGORY_NAME, category.name)
            }
            val actionPendingIntent = PendingIntent.getBroadcast(
                context,
                (session.id * 100 + category.id).toInt(),
                actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, category.name, actionPendingIntent)
        }

        notificationManager.notify(getSessionNotificationId(session.id), builder.build())
    }

    fun cancelSessionNotification(sessionId: Long) {
        notificationManager.cancel(getSessionNotificationId(sessionId))
    }

    fun cancelAlertNotification() {
        notificationManager.cancel(NOTIFICATION_ID_ALERT)
    }

    private fun getSourceDisplayName(pkg: String?): String {
        return when {
            pkg == null -> "系统相机"
            pkg.contains("camera", ignoreCase = true) -> "系统相机"
            pkg.contains("tencent.mm", ignoreCase = true) -> "微信"
            pkg.contains("tencent.mobileqq", ignoreCase = true) -> "QQ"
            pkg.contains("screenshot", ignoreCase = true) -> "系统截图"
            else -> "新媒体"
        }
    }
}
