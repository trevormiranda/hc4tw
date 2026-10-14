package com.hc4tw.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hc4tw.logging.AppLogger

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        AppLogger.i(TAG, "Background periodic sync triggered by WorkManager")
        val syncManager = SyncManager(applicationContext)

        return try {
            val result = syncManager.performSync(forceFull = false)
            if (result.success) {
                AppLogger.i(TAG, "Background sync completed successfully")
                Result.success()
            } else if (result.failedCount > 0 && result.syncedCount == 0) {
                AppLogger.w(TAG, "Background sync failed completely, will retry later")
                Result.retry()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Exception during background sync execution", e)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "TrendWeightPeriodicSync"
        private const val TAG = "SyncWorker"
    }
}
