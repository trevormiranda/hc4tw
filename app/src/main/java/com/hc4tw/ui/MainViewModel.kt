package com.hc4tw.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hc4tw.BridgeApplication
import com.hc4tw.data.PreferencesManager
import com.hc4tw.health.HealthConnectManager
import com.hc4tw.health.HealthPermissionState
import com.hc4tw.logging.AppLogger
import com.hc4tw.logging.LogEntry
import com.hc4tw.sync.SyncManager
import com.hc4tw.trendweight.TrendWeightClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val healthManager = HealthConnectManager(application)
    private val trendWeightClient = TrendWeightClient()
    private val syncManager = SyncManager(application, healthManager, trendWeightClient, prefs)

    val logs: StateFlow<List<LogEntry>> = AppLogger.logsFlow

    private val _apiKey = MutableStateFlow(prefs.trendWeightApiKey)
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _isTestingKey = MutableStateFlow(false)
    val isTestingKey: StateFlow<Boolean> = _isTestingKey.asStateFlow()

    private val _keyValidationStatus = MutableStateFlow<String?>(null)
    val keyValidationStatus: StateFlow<String?> = _keyValidationStatus.asStateFlow()

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

    private val _permissionState = MutableStateFlow(HealthPermissionState())
    val permissionState: StateFlow<HealthPermissionState> = _permissionState.asStateFlow()

    private val _sdkStatus = MutableStateFlow(healthManager.getSdkStatus())
    val sdkStatus: StateFlow<Int> = _sdkStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncStatus = MutableStateFlow(prefs.lastSyncStatus)
    val lastSyncStatus: StateFlow<String> = _lastSyncStatus.asStateFlow()

    private val _lastSyncMessage = MutableStateFlow(prefs.lastSyncMessage)
    val lastSyncMessage: StateFlow<String> = _lastSyncMessage.asStateFlow()

    private val _lastSyncTimeFormatted = MutableStateFlow(formatTime(prefs.lastSyncTimestamp))
    val lastSyncTimeFormatted: StateFlow<String> = _lastSyncTimeFormatted.asStateFlow()

    private val _lastSyncCount = MutableStateFlow(prefs.lastSyncCount)
    val lastSyncCount: StateFlow<Int> = _lastSyncCount.asStateFlow()

    private val _isBackgroundSyncEnabled = MutableStateFlow(prefs.isBackgroundSyncEnabled)
    val isBackgroundSyncEnabled: StateFlow<Boolean> = _isBackgroundSyncEnabled.asStateFlow()

    private val _syncIntervalMinutes = MutableStateFlow(prefs.syncIntervalMinutes)
    val syncIntervalMinutes: StateFlow<Long> = _syncIntervalMinutes.asStateFlow()

    private val _syncNowDays = MutableStateFlow(prefs.syncNowDays)
    val syncNowDays: StateFlow<Int> = _syncNowDays.asStateFlow()

    private val _forceFullDays = MutableStateFlow(prefs.forceFullDays)
    val forceFullDays: StateFlow<Int> = _forceFullDays.asStateFlow()

    val appVersionDisplay: String by lazy {
        val pInfo = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                application.packageManager.getPackageInfo(application.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                application.packageManager.getPackageInfo(application.packageName, 0)
            }
        } catch (_: Exception) {
            null
        }
        val vName = pInfo?.versionName ?: "1.0.0"
        @Suppress("DEPRECATION")
        val vCode = pInfo?.let {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong()
        } ?: 3L
        "hc4tw v$vName (build $vCode)"
    }

    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    init {
        refreshState()
    }

    fun refreshState() {
        viewModelScope.launch {
            _sdkStatus.value = healthManager.getSdkStatus()
            val state = healthManager.getPermissionState()
            _permissionState.value = state
            _hasPermissions.value = state.hasRequiredWeightPermission
            _apiKey.value = prefs.trendWeightApiKey

            if (!state.hasBackgroundPermission && prefs.isBackgroundSyncEnabled) {
                prefs.isBackgroundSyncEnabled = false
                val app = getApplication<BridgeApplication>()
                app.cancelPeriodicSync()
            }

            _isBackgroundSyncEnabled.value = prefs.isBackgroundSyncEnabled && state.hasBackgroundPermission
            _syncIntervalMinutes.value = prefs.syncIntervalMinutes
            _syncNowDays.value = prefs.syncNowDays
            _forceFullDays.value = prefs.forceFullDays
            updateSyncStatusDisplay()
        }
    }

    private fun updateSyncStatusDisplay() {
        _lastSyncStatus.value = prefs.lastSyncStatus
        _lastSyncMessage.value = prefs.lastSyncMessage
        _lastSyncTimeFormatted.value = formatTime(prefs.lastSyncTimestamp)
        _lastSyncCount.value = prefs.lastSyncCount
    }

    fun onApiKeyChanged(newKey: String) {
        _apiKey.value = newKey
        _keyValidationStatus.value = null
    }

    fun testAndSaveApiKey() {
        val key = _apiKey.value.trim()
        if (key.isBlank()) {
            viewModelScope.launch { _userMessage.emit("Please enter a TrendWeight API key.") }
            return
        }

        viewModelScope.launch {
            _isTestingKey.value = true
            _keyValidationStatus.value = "Testing connection..."

            val result = withContext(Dispatchers.IO) {
                trendWeightClient.verifyApiKey(key)
            }

            _isTestingKey.value = false
            if (result.isSuccess) {
                prefs.trendWeightApiKey = key
                _keyValidationStatus.value = "Valid key! Saved successfully."
                _userMessage.emit("API key verified and saved.")
            } else {
                val err = result.exceptionOrNull()?.message ?: "Validation failed"
                _keyValidationStatus.value = "Error: $err"
                _userMessage.emit("Failed to verify API key: $err")
            }
        }
    }

    fun syncNow(forceFull: Boolean = false, daysWindow: Int? = null) {
        if (_isSyncing.value) return

        val days = daysWindow ?: if (forceFull) prefs.forceFullDays else prefs.syncNowDays

        viewModelScope.launch {
            _isSyncing.value = true
            val syncResult = syncManager.performSync(forceFull = forceFull, daysWindow = days)
            _isSyncing.value = false
            updateSyncStatusDisplay()

            if (syncResult.success) {
                _userMessage.emit(syncResult.message)
            } else {
                _userMessage.emit("Sync failed: ${syncResult.message}")
            }
        }
    }

    fun setSyncNowDays(days: Int) {
        val validDays = days.coerceAtLeast(1)
        prefs.syncNowDays = validDays
        _syncNowDays.value = validDays
    }

    fun setForceFullDays(days: Int) {
        val validDays = days.coerceAtLeast(1)
        prefs.forceFullDays = validDays
        _forceFullDays.value = validDays
    }

    fun toggleBackgroundSync(enabled: Boolean) {
        val hasBg = _permissionState.value.hasBackgroundPermission
        if (!hasBg && enabled) return

        prefs.isBackgroundSyncEnabled = enabled
        _isBackgroundSyncEnabled.value = enabled
        val app = getApplication<BridgeApplication>()
        if (enabled) {
            val interval = prefs.syncIntervalMinutes
            app.schedulePeriodicSync(interval)
            val intervalText = formatIntervalText(interval)
            viewModelScope.launch { _userMessage.emit("Background sync enabled ($intervalText interval)") }
        } else {
            app.cancelPeriodicSync()
            viewModelScope.launch { _userMessage.emit("Background sync disabled") }
        }
    }

    fun setSyncInterval(minutes: Long) {
        val validInterval = minutes.coerceAtLeast(15L)
        prefs.syncIntervalMinutes = validInterval
        _syncIntervalMinutes.value = validInterval
        val app = getApplication<BridgeApplication>()
        if (_isBackgroundSyncEnabled.value) {
            app.schedulePeriodicSync(validInterval)
            val intervalText = formatIntervalText(validInterval)
            viewModelScope.launch { _userMessage.emit("Background sync interval set to $intervalText") }
        }
    }

    private fun formatIntervalText(minutes: Long): String {
        return when {
            minutes < 60 -> "$minutes minutes"
            minutes == 60L -> "1 hour"
            minutes % 60 == 0L -> "${minutes / 60} hours"
            else -> "$minutes minutes"
        }
    }

    fun copyLogsToClipboard(context: Context) {
        val success = AppLogger.copyToClipboard(context)
        viewModelScope.launch {
            if (success) {
                _userMessage.emit("Diagnostic logs copied to clipboard!")
            } else {
                _userMessage.emit("Failed to copy logs to clipboard.")
            }
        }
    }

    fun shareLogs(context: Context) {
        val report = AppLogger.exportFormatted(context)
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, report)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share TrendWeight Diagnostic Logs")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    }

    fun clearLogs() {
        AppLogger.clear()
        viewModelScope.launch {
            _userMessage.emit("Diagnostic logs cleared.")
        }
    }

    private fun formatTime(epoch: Long): String {
        if (epoch <= 0) return "Never"
        val sdf = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
        return sdf.format(Date(epoch))
    }
}
