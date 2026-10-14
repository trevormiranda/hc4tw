package com.hc4tw.sync

import android.content.Context
import com.hc4tw.data.PreferencesManager
import com.hc4tw.health.HealthConnectManager
import com.hc4tw.logging.AppLogger
import com.hc4tw.trendweight.MeasurementPayload
import com.hc4tw.trendweight.TrendWeightClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class SyncResult(
    val success: Boolean,
    val syncedCount: Int,
    val failedCount: Int,
    val message: String
)

class SyncManager(
    private val context: Context,
    private val healthConnectManager: HealthConnectManager = HealthConnectManager(context),
    private val trendWeightClient: TrendWeightClient = TrendWeightClient(),
    private val prefs: PreferencesManager = PreferencesManager(context)
) {

    suspend fun performSync(
        forceFull: Boolean = false,
        daysWindow: Int = prefs.syncNowDays
    ): SyncResult = withContext(Dispatchers.IO) {
        val days = daysWindow.coerceAtLeast(1)
        AppLogger.i(TAG, "Starting sync (forceFull=$forceFull, daysWindow=$days)...")

        val apiKey = prefs.trendWeightApiKey
        if (apiKey.isBlank()) {
            val msg = "TrendWeight API key is not configured"
            AppLogger.w(TAG, msg)
            updateStatus("ERROR", msg)
            return@withContext SyncResult(false, 0, 0, msg)
        }

        if (!healthConnectManager.isAvailable()) {
            val msg = "Health Connect is not available on this device"
            AppLogger.e(TAG, msg)
            updateStatus("ERROR", msg)
            return@withContext SyncResult(false, 0, 0, msg)
        }

        val permState = healthConnectManager.getPermissionState()
        if (!permState.hasWeightPermission) {
            val msg = "Health Connect Weight read permission is required to sync"
            AppLogger.w(TAG, msg)
            updateStatus("ERROR", msg)
            return@withContext SyncResult(false, 0, 0, msg)
        }

        val now = Instant.now()
        val endTime = now.plus(1, ChronoUnit.DAYS)
        val startTime = now.minus(days.toLong(), ChronoUnit.DAYS)

        val weights = healthConnectManager.readWeightRecords(startTime, endTime)
        if (weights.isEmpty()) {
            val msg = "No weight records found in Health Connect in the last $days day(s)"
            AppLogger.i(TAG, msg)
            updateStatus("SUCCESS", "No records found in Health Connect")
            return@withContext SyncResult(true, 0, 0, msg)
        }

        val bodyFats = healthConnectManager.readBodyFatRecords(startTime, endTime)

        val zoneId = ZoneId.systemDefault()

        // Track the highest lastModifiedTime across all records
        val highestWeightModified = weights.maxOfOrNull { it.lastModifiedTime.toEpochMilli() } ?: 0L
        val highestFatModified = bodyFats.maxOfOrNull { it.lastModifiedTime.toEpochMilli() } ?: 0L
        val currentMaxModified = maxOf(highestWeightModified, highestFatModified)

        // Check if any records were newly added or modified in Health Connect since last sync
        val newOrModifiedWeights = if (!forceFull && prefs.lastWatermark > 0) {
            weights.filter { it.lastModifiedTime.toEpochMilli() > prefs.lastWatermark }
        } else {
            weights
        }

        val newOrModifiedFats = if (!forceFull && prefs.lastWatermark > 0) {
            bodyFats.filter { it.lastModifiedTime.toEpochMilli() > prefs.lastWatermark }
        } else {
            bodyFats
        }

        // Find dates that have new/modified weight or body fat records
        val modifiedDates = (
            newOrModifiedWeights.map { it.time.atZone(zoneId).toLocalDate() } +
            newOrModifiedFats.map { it.time.atZone(zoneId).toLocalDate() }
        ).toSet()

        if (!forceFull && prefs.lastWatermark > 0 && modifiedDates.isEmpty()) {
            val msg = "Up to date (no new or modified weigh-ins)"
            AppLogger.i(TAG, "No new or modified weigh-ins in Health Connect since last sync")
            updateStatus("SUCCESS", msg)
            return@withContext SyncResult(true, 0, 0, msg)
        }

        // Group weights by local calendar date (YYYY-MM-DD), keeping the latest weigh-in of the day
        val weightsByDate = weights.groupBy { it.time.atZone(zoneId).toLocalDate() }
        val dailyWeights = mutableMapOf<LocalDate, Double>()

        for ((date, recordList) in weightsByDate) {
            val latestRecord = recordList.maxByOrNull { it.time } ?: continue
            dailyWeights[date] = latestRecord.weightKg
            if (recordList.size > 1) {
                val formattedKg = String.format(java.util.Locale.US, "%.2f", latestRecord.weightKg)
                val formattedLbs = String.format(java.util.Locale.US, "%.1f", latestRecord.weightKg * 2.2046226218)
                AppLogger.i(TAG, "Found ${recordList.size} weigh-ins for $date. TrendWeight supports 1 entry per day; using latest weigh-in at ${latestRecord.time} ($formattedKg kg / $formattedLbs lbs).")
            }
        }

        // Map daily body fats, pairing the closest body fat record on that day
        val dailyFats = mutableMapOf<LocalDate, Double>()
        for (f in bodyFats) {
            val localDate = f.time.atZone(zoneId).toLocalDate()
            // Convert percentage (e.g. 19.8) to ratio (0.198) for TrendWeight
            dailyFats[localDate] = f.percentage / 100.0
        }

        val sortedDates = dailyWeights.keys.sorted()

        // Fetch existing measurements from TrendWeight ONLY on first sync (when app has no record of last sync)
        val isFirstSync = prefs.lastWatermark == 0L
        val existingTrendWeightMap = if (isFirstSync && !forceFull) {
            trendWeightClient.getExistingMeasurements(apiKey).getOrDefault(emptyMap())
        } else {
            emptyMap()
        }

        val allCandidatePayloads = sortedDates.mapNotNull { date ->
            val dateStr = date.toString() // YYYY-MM-DD
            val weightKg = dailyWeights[date] ?: return@mapNotNull null
            val fatRatio = dailyFats[date]

            MeasurementPayload(
                date = dateStr,
                weightKg = weightKg,
                fatRatio = fatRatio
            )
        }

        // Filter out payloads that are identical to what was previously synced or already on TrendWeight
        val payloads = allCandidatePayloads.filter { candidate ->
            val dateStr = candidate.date
            val candidateValStr = String.format(java.util.Locale.US, "%.3f|%.4f", candidate.weightKg, candidate.fatRatio ?: 0.0)

            if (forceFull) {
                true
            } else if (isFirstSync) {
                val existing = existingTrendWeightMap[dateStr]
                if (existing != null && isSameMeasurement(existing, candidate)) {
                    val formattedKg = String.format(java.util.Locale.US, "%.2f", candidate.weightKg)
                    AppLogger.i(TAG, "Skipping $dateStr: TrendWeight already has identical reading ($formattedKg kg)")
                    false
                } else {
                    true
                }
            } else {
                val lastSyncedVal = prefs.getLastSyncedValue(dateStr)
                if (lastSyncedVal == candidateValStr) {
                    false
                } else {
                    val formattedKg = String.format(java.util.Locale.US, "%.2f", candidate.weightKg)
                    AppLogger.i(TAG, "Detected new/changed weigh-in for $dateStr: $formattedKg kg")
                    true
                }
            }
        }

        if (payloads.isEmpty()) {
            val msg = "Up to date (no new or modified weigh-ins)"
            AppLogger.i(TAG, "All Health Connect measurements already match last sync. No updates needed.")
            if (currentMaxModified > 0) {
                prefs.lastWatermark = currentMaxModified
            }
            updateStatus("SUCCESS", msg, 0)
            return@withContext SyncResult(true, 0, 0, msg)
        }

        AppLogger.i(TAG, "Syncing ${payloads.size} changed daily measurement(s) to TrendWeight...")

        var successCount = 0
        var failCount = 0

        val batchResult = trendWeightClient.postMeasurements(apiKey, payloads)
        if (batchResult.isSuccess) {
            successCount = payloads.size
            for (p in payloads) {
                val valStr = String.format(java.util.Locale.US, "%.3f|%.4f", p.weightKg, p.fatRatio ?: 0.0)
                prefs.setLastSyncedValue(p.date, valStr)
            }
        } else {
            AppLogger.w(TAG, "Batch sync failed (${batchResult.exceptionOrNull()?.message}), falling back to individual posts...")
            for (payload in payloads) {
                val singleResult = trendWeightClient.postMeasurement(apiKey, payload)
                if (singleResult.isSuccess) {
                    successCount++
                    val valStr = String.format(java.util.Locale.US, "%.3f|%.4f", payload.weightKg, payload.fatRatio ?: 0.0)
                    prefs.setLastSyncedValue(payload.date, valStr)
                } else {
                    failCount++
                    AppLogger.w(TAG, "Failed to post ${payload.date}: ${singleResult.exceptionOrNull()?.message}")
                }
            }
        }

        if (currentMaxModified > 0) {
            prefs.lastWatermark = currentMaxModified
        }

        val overallSuccess = failCount == 0
        val finalStatus = if (overallSuccess) "SUCCESS" else "PARTIAL_ERROR"
        val statusMsg = "Synced $successCount/${payloads.size} day(s) successfully"

        AppLogger.i(TAG, "Sync finished: $statusMsg (Failures: $failCount)")
        updateStatus(finalStatus, statusMsg, successCount)

        SyncResult(overallSuccess, successCount, failCount, statusMsg)
    }

    private fun isSameMeasurement(existing: MeasurementPayload, candidate: MeasurementPayload): Boolean {
        val weightMatches = abs(existing.weightKg - candidate.weightKg) < 0.005
        val fatMatches = when {
            existing.fatRatio == null && candidate.fatRatio == null -> true
            existing.fatRatio != null && candidate.fatRatio != null -> abs(existing.fatRatio - candidate.fatRatio) < 0.0005
            else -> false
        }
        return weightMatches && fatMatches
    }

    private fun updateStatus(status: String, message: String, syncedCount: Int = 0) {
        prefs.lastSyncTimestamp = System.currentTimeMillis()
        prefs.lastSyncStatus = status
        prefs.lastSyncMessage = message
        prefs.lastSyncCount = syncedCount
    }

    companion object {
        private const val TAG = "SyncManager"
    }
}
