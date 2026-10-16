package com.hc4tw.health

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.hc4tw.logging.AppLogger
import java.time.Instant

data class HealthWeightRecord(
    val time: Instant,
    val weightKg: Double,
    val lastModifiedTime: Instant = time
)

data class HealthBodyFatRecord(
    val time: Instant,
    val percentage: Double,
    val lastModifiedTime: Instant = time
)

data class HealthPermissionState(
    val hasWeightPermission: Boolean = false,
    val hasBodyFatPermission: Boolean = false,
    val hasBackgroundPermission: Boolean = false
) {
    val hasAllPermissions: Boolean get() = hasWeightPermission && hasBodyFatPermission
    val hasRequiredWeightPermission: Boolean get() = hasWeightPermission
}

class HealthConnectManager(private val context: Context) {

    private val healthConnectClient: HealthConnectClient? by lazy {
        try {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else {
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to initialize HealthConnectClient", e)
            null
        }
    }

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        PERMISSION_READ_BACKGROUND
    )

    fun getSdkStatus(): Int {
        return HealthConnectClient.getSdkStatus(context)
    }

    fun isAvailable(): Boolean {
        return getSdkStatus() == HealthConnectClient.SDK_AVAILABLE
    }

    fun createPermissionContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    fun openPermissionSettings(context: Context) {
        val intentsToTry = listOf(
            android.content.Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").apply {
                putExtra(android.content.Intent.EXTRA_PACKAGE_NAME, context.packageName)
            },
            android.content.Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"),
            android.content.Intent("android.health.connect.action.HEALTH_CONNECT_SETTINGS"),
            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", context.packageName, null)
            }
        )

        for (intent in intentsToTry) {
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    AppLogger.d(TAG, "Launching Health Connect settings intent: ${intent.action}")
                    context.startActivity(intent)
                    return
                }
            } catch (e: Exception) {
                AppLogger.d(TAG, "Intent failed: ${intent.action} (${e.message})")
            }
        }
    }

    suspend fun getPermissionState(): HealthPermissionState {
        val client = healthConnectClient ?: return HealthPermissionState()
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            val hasWeight = granted.contains(HealthPermission.getReadPermission(WeightRecord::class))
            val hasFat = granted.contains(HealthPermission.getReadPermission(BodyFatRecord::class))
            val hasBackground = granted.contains(PERMISSION_READ_BACKGROUND)
            AppLogger.d(TAG, "Health Connect permissions state - Weight: $hasWeight, BodyFat: $hasFat, Background: $hasBackground (Granted set: $granted)")
            HealthPermissionState(
                hasWeightPermission = hasWeight,
                hasBodyFatPermission = hasFat,
                hasBackgroundPermission = hasBackground
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to check Health Connect permissions", e)
            HealthPermissionState()
        }
    }

    suspend fun hasAllPermissions(): Boolean = getPermissionState().hasAllPermissions

    suspend fun readWeightRecords(startTime: Instant, endTime: Instant): List<HealthWeightRecord> {
        val client = healthConnectClient ?: return emptyList()
        if (!getPermissionState().hasWeightPermission) {
            AppLogger.d(TAG, "Skipping readWeightRecords: Weight permission not granted")
            return emptyList()
        }

        return try {
            AppLogger.d(TAG, "Reading weight records from $startTime to $endTime")
            val request = ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = client.readRecords(request)
            val results = response.records.map { record ->
                HealthWeightRecord(
                    time = record.time,
                    weightKg = record.weight.inKilograms,
                    lastModifiedTime = record.metadata.lastModifiedTime
                )
            }.sortedBy { it.time }

            AppLogger.i(TAG, "Retrieved ${results.size} weight record(s) from Health Connect:")
            for (r in results) {
                val weightLbs = r.weightKg * 2.2046226218
                val formattedKg = String.format(java.util.Locale.US, "%.2f", r.weightKg)
                val formattedLbs = String.format(java.util.Locale.US, "%.1f", weightLbs)
                AppLogger.i(TAG, "  - ${r.time}: $formattedKg kg ($formattedLbs lbs)")
            }
            results
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error querying weight records from Health Connect", e)
            emptyList()
        }
    }

    suspend fun readBodyFatRecords(startTime: Instant, endTime: Instant): List<HealthBodyFatRecord> {
        val client = healthConnectClient ?: return emptyList()
        if (!getPermissionState().hasBodyFatPermission) {
            AppLogger.d(TAG, "Skipping readBodyFatRecords: Body Fat permission not granted")
            return emptyList()
        }

        return try {
            val request = ReadRecordsRequest(
                recordType = BodyFatRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = client.readRecords(request)
            val results = response.records.map { record ->
                HealthBodyFatRecord(
                    time = record.time,
                    percentage = record.percentage.value,
                    lastModifiedTime = record.metadata.lastModifiedTime
                )
            }.sortedBy { it.time }

            AppLogger.i(TAG, "Retrieved ${results.size} body fat records from Health Connect")
            results
        } catch (e: Exception) {
            AppLogger.w(TAG, "Body fat records query failed or permission denied: ${e.message}")
            emptyList()
        }
    }

    companion object {
        private const val TAG = "HealthConnectManager"
        const val PERMISSION_READ_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    }
}
