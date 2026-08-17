package com.example.watcher

import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import com.example.LocalMediaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MediaMonitorService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var imagesObserver: ContentObserver? = null
    private var videosObserver: ContentObserver? = null
    private var overlay: QuickClassifyOverlay? = null
    private var debounceJob: Job? = null

    companion object {
        const val ACTION_START = "com.example.ACTION_START_MONITOR"
        const val ACTION_STOP = "com.example.ACTION_STOP_MONITOR"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, MediaMonitorService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MediaMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as LocalMediaApplication
        if (!app.appSettingsRepository.getSnapshot().isServiceRunning) {
            stopSelf()
            return
        }

        isRunning = true
        overlay = QuickClassifyOverlay(this)

        startForeground(
            MediaNotificationManager.NOTIFICATION_ID_MONITOR,
            app.notificationManager.buildForegroundNotification()
        )

        registerObservers(app)
        observeSessionReadyEvents(app)

        // Perform initial reconcile and delivery on start
        serviceScope.launch {
            app.captureSessionAggregator.recoverStaleSessions()
            app.mediaReconciler.reconcile(forceFullScan = false)
            deliverPendingSessions(app)
        }
    }

    private suspend fun deliverPendingSessions(app: LocalMediaApplication) {
        val undelivered = app.captureSessionRepository.getUndeliveredReadySessions()
        for (session in undelivered) {
            app.sessionDeliveryCoordinator.deliverSession(session)
        }
    }

    private suspend fun deliverSession(app: LocalMediaApplication, session: com.example.core.model.CaptureSession) {
        app.sessionDeliveryCoordinator.deliverSession(session)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = applicationContext as? LocalMediaApplication
        if (intent?.action == ACTION_STOP || (app != null && !app.appSettingsRepository.getSnapshot().isServiceRunning)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun registerObservers(app: LocalMediaApplication) {
        val handler = Handler(Looper.getMainLooper())

        imagesObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                triggerDebouncedReconcile(app)
            }
        }

        videosObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                triggerDebouncedReconcile(app)
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            imagesObserver!!
        )
        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            videosObserver!!
        )
    }

    private fun triggerDebouncedReconcile(app: LocalMediaApplication) {
        debounceJob?.cancel()
        debounceJob = serviceScope.launch {
            delay(500L) // 500ms debounce
            app.mediaReconciler.reconcile(forceFullScan = false)
        }
    }

    private fun observeSessionReadyEvents(app: LocalMediaApplication) {
        serviceScope.launch {
            app.captureSessionAggregator.sessionReadyEvents.collectLatest { session ->
                deliverSession(app, session)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        imagesObserver?.let { contentResolver.unregisterContentObserver(it) }
        videosObserver?.let { contentResolver.unregisterContentObserver(it) }
        overlay?.dismiss()
        serviceScope.cancel()
    }
}
