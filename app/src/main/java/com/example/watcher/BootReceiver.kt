package com.example.watcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.LocalMediaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Schedule JobService
            MediaJobService.schedule(context)

            // Reconcile missed media during reboot
            val app = context.applicationContext as? LocalMediaApplication
            if (app != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    app.mediaReconciler.reconcile(forceFullScan = true)
                    app.retentionScanner.scanAndMarkExpired()
                }
            }
        }
    }
}
