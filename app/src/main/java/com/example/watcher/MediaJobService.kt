package com.example.watcher

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.LocalMediaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MediaJobService : JobService() {

    companion object {
        const val JOB_ID = 5001

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val component = ComponentName(context, MediaJobService::class.java)
            val builder = JobInfo.Builder(JOB_ID, component)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                    )
                )
                builder.addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
                    )
                )
                builder.setTriggerContentMaxDelay(1500L)
                builder.setTriggerContentUpdateDelay(500L)
            }

            scheduler.schedule(builder.build())
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        val app = applicationContext as? LocalMediaApplication ?: return false

        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.mediaReconciler.reconcile(forceFullScan = false)
            } finally {
                // Re-schedule since TriggerContentUri jobs do not repeat automatically
                schedule(this@MediaJobService)
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true
    }
}
