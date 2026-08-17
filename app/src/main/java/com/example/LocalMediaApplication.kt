package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.repository.AppSettingsRepository
import com.example.data.repository.CategoryRepository
import com.example.data.repository.CaptureSessionRepository
import com.example.data.repository.MediaRepository
import com.example.data.repository.SourceRuleRepository
import com.example.data.repository.TagRepository
import com.example.media.MediaStoreDataSource
import com.example.policy.CategoryPolicyEngine
import com.example.policy.DefaultCategoryPolicyEngine
import com.example.policy.DefaultSourceRuleEngine
import com.example.policy.RetentionScanner
import com.example.policy.SourceRuleEngine
import com.example.watcher.CaptureSessionAggregator
import com.example.watcher.MediaJobService
import com.example.watcher.MediaNotificationManager
import com.example.watcher.MediaReconciler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocalMediaApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Data layer
    lateinit var database: AppDatabase
        private set
    lateinit var mediaRepository: MediaRepository
        private set
    lateinit var categoryRepository: CategoryRepository
        private set
    lateinit var tagRepository: TagRepository
        private set
    lateinit var captureSessionRepository: CaptureSessionRepository
        private set
    lateinit var sourceRuleRepository: SourceRuleRepository
        private set
    lateinit var appSettingsRepository: AppSettingsRepository
        private set

    // Media layer
    lateinit var mediaStoreDataSource: MediaStoreDataSource
        private set

    // Policy layer
    lateinit var ruleEngine: SourceRuleEngine
        private set
    lateinit var policyEngine: CategoryPolicyEngine
        private set
    lateinit var retentionScanner: RetentionScanner
        private set

    // Watcher layer
    lateinit var captureSessionAggregator: CaptureSessionAggregator
        private set
    lateinit var mediaReconciler: MediaReconciler
        private set
    lateinit var notificationManager: MediaNotificationManager
        private set
    lateinit var quickClassifyOverlay: com.example.watcher.QuickClassifyOverlay
        private set
    lateinit var sessionDeliveryCoordinator: com.example.watcher.SessionDeliveryCoordinator
        private set

    // Domain use cases
    lateinit var classifyMediaUseCase: com.example.domain.ClassifyMediaUseCase
        private set
    lateinit var classifySessionUseCase: com.example.domain.ClassifySessionUseCase
        private set
    lateinit var deleteMediaUseCase: com.example.domain.DeleteMediaUseCase
        private set

    override fun onCreate() {
        super.onCreate()

        // Init database & repositories
        database = AppDatabase.getInstance(this)
        mediaRepository = MediaRepository(database)
        categoryRepository = CategoryRepository(database)
        tagRepository = TagRepository(database)
        captureSessionRepository = CaptureSessionRepository(database)
        sourceRuleRepository = SourceRuleRepository(database)
        appSettingsRepository = AppSettingsRepository(this)

        // Init media source
        mediaStoreDataSource = MediaStoreDataSource(this)

        // Init policy engines
        ruleEngine = DefaultSourceRuleEngine(sourceRuleRepository, categoryRepository)
        policyEngine = DefaultCategoryPolicyEngine()
        retentionScanner = RetentionScanner(mediaRepository, categoryRepository)

        // Init aggregator & reconciler
        captureSessionAggregator = CaptureSessionAggregator(
            captureSessionRepository,
            mediaRepository,
            appSettingsRepository,
            appScope
        )
        mediaReconciler = MediaReconciler(
            mediaStoreDataSource,
            mediaRepository,
            categoryRepository,
            sourceRuleRepository,
            ruleEngine,
            policyEngine,
            captureSessionAggregator
        )
        notificationManager = MediaNotificationManager(this)
        quickClassifyOverlay = com.example.watcher.QuickClassifyOverlay(this)
        sessionDeliveryCoordinator = com.example.watcher.SessionDeliveryCoordinator(
            this,
            captureSessionRepository,
            categoryRepository,
            mediaRepository,
            appSettingsRepository,
            notificationManager,
            quickClassifyOverlay
        )

        // Init use cases
        classifyMediaUseCase = com.example.domain.ClassifyMediaUseCase(
            mediaRepository,
            categoryRepository,
            policyEngine
        )
        classifySessionUseCase = com.example.domain.ClassifySessionUseCase(
            mediaRepository,
            captureSessionRepository,
            classifyMediaUseCase,
            notificationManager
        )
        deleteMediaUseCase = com.example.domain.DeleteMediaUseCase(
            this,
            mediaRepository,
            mediaStoreDataSource
        )

        // Schedule JobService
        MediaJobService.schedule(this)

        // Launch periodic retention scanning & initial reconcile
        appScope.launch {
            // Give DB seed a moment if first time
            delay(500L)
            AppDatabase.seedDefaultData(database)

            // Initial reconcile
            mediaReconciler.reconcile(forceFullScan = false)
            retentionScanner.scanAndMarkExpired()

            // Periodic retention check loop (interval from settings)
            while (isActive) {
                val intervalHours = appSettingsRepository.getSnapshot().retentionScanIntervalHours
                delay(intervalHours * 3600 * 1000L)
                retentionScanner.scanAndMarkExpired()
            }
        }
    }
}
