package com.example.watcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.LocalMediaApplication
import com.example.core.enum.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CLASSIFY = "com.example.ACTION_CLASSIFY_SESSION"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_CATEGORY_ID = "extra_category_id"
        const val EXTRA_CATEGORY_NAME = "extra_category_name"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CLASSIFY) {
            val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
            val categoryId = intent.getLongExtra(EXTRA_CATEGORY_ID, -1L)
            val categoryName = intent.getStringExtra(EXTRA_CATEGORY_NAME) ?: "已分类"

            if (sessionId != -1L && categoryId != -1L) {
                val app = context.applicationContext as? LocalMediaApplication
                if (app != null) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val category = app.categoryRepository.getById(categoryId)
                            val items = app.mediaRepository.getBySession(sessionId)
                            if (items.isNotEmpty()) {
                                // Calculate expireAt based on asset capturedAt, not current time
                                for (item in items) {
                                    val expireAt = category?.let {
                                        app.policyEngine.calculateExpireAt(item.capturedAt ?: item.addedAt, it)
                                    }
                                    app.mediaRepository.assignCategory(item.id, categoryId, expireAt)
                                }
                            }
                            
                            app.captureSessionRepository.updateStatus(sessionId, SessionStatus.CLASSIFIED.name)

                            // Cancel alert notification
                            app.notificationManager.cancelAlertNotification()

                            CoroutineScope(Dispatchers.Main).launch {
                                Toast.makeText(context, "已归类到「$categoryName」", Toast.LENGTH_SHORT).show()
                            }
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}
