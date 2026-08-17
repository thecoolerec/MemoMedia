package com.example.watcher

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.example.LocalMediaApplication
import com.example.core.enum.ReconcileMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReconcileJobService : JobService() {
    companion object {
        const val JOB_ID = 5002

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val component = ComponentName(context, BootReconcileJobService::class.java)
            val builder = JobInfo.Builder(JOB_ID, component)
                .setMinimumLatency(1000L) // Wait a sec after boot
                .setOverrideDeadline(10000L) // Must run within 10s
            
            scheduler.schedule(builder.build())
        }

        fun cancel(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            scheduler.cancel(JOB_ID)
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        val app = applicationContext as? LocalMediaApplication ?: return false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.mediaReconciler.reconcile(ReconcileMode.BOOT_CATCHUP)
                app.retentionScanner.scanAndMarkExpired()
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true
    }
}
