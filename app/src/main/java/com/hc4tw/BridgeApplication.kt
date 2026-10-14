package com.hc4tw

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hc4tw.data.PreferencesManager
import com.hc4tw.logging.AppLogger
import com.hc4tw.sync.SyncWorker
import java.util.concurrent.TimeUnit

class BridgeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        val pInfo = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
        } catch (_: Exception) {
            null
        }

        val vName = pInfo?.versionName ?: "1.0.0"
        @Suppress("DEPRECATION")
        val vCode = pInfo?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong()
        } ?: 3L

        AppLogger.i("BridgeApplication", "Application started: hc4tw v$vName (build $vCode)")

        val prefs = PreferencesManager(this)
        if (prefs.isBackgroundSyncEnabled) {
            schedulePeriodicSync(prefs.syncIntervalMinutes)
        }
    }

    fun schedulePeriodicSync(intervalMinutes: Long = PreferencesManager(this).syncIntervalMinutes) {
        val validInterval = intervalMinutes.coerceAtLeast(15L)
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(validInterval, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            syncRequest
        )
        val intervalText = if ((validInterval % 60) == 0L) "${validInterval / 60}-hour" else "$validInterval-minute"
        AppLogger.i("BridgeApplication", "Scheduled background periodic sync ($intervalText interval)")
    }

    fun cancelPeriodicSync() {
        WorkManager.getInstance(this).cancelUniqueWork(SyncWorker.WORK_NAME)
        AppLogger.i("BridgeApplication", "Cancelled background periodic sync")
    }
}
