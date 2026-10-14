package com.hc4tw.logging

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.hc4tw.BuildConfig
import com.hc4tw.data.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
) {
    fun formattedTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return sdf.format(Date(timestamp))
    }

    fun toDisplayString(): String {
        return "[${formattedTimestamp()}] [${level.name}] [$tag] $message"
    }
}

object AppLogger {
    private const val TAG = "AppLogger"
    private const val MAX_LOGS = 200
    private const val LOG_FILE_NAME = "app_logs.txt"

    private var nextId = 1L
    private val logList = mutableListOf<LogEntry>()
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    private var logFile: File? = null

    fun init(context: Context) {
        val file = File(context.filesDir, LOG_FILE_NAME)
        logFile = file
        loadLogsFromFile(file)
    }

    private fun loadLogsFromFile(file: File) {
        if (!file.exists()) return
        try {
            val lines = file.readLines().takeLast(MAX_LOGS)
            synchronized(logList) {
                logList.clear()
                for (line in lines) {
                    val entry = parseLogLine(line)
                    if (entry != null) {
                        logList.add(entry)
                        if (entry.id >= nextId) nextId = entry.id + 1
                    }
                }
                _logsFlow.value = logList.toList()
            }
            // Rewrite trimmed lines back to disk so file stays bounded at MAX_LOGS
            file.writeText(logList.joinToString("\n") { it.toDisplayString() } + if (logList.isNotEmpty()) "\n" else "")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load logs from file", e)
        }
    }

    private fun parseLogLine(line: String): LogEntry? {
        // Expected format: [yyyy-MM-dd HH:mm:ss.SSS] [LEVEL] [TAG] Message
        return try {
            val regex = """^\[([\d\- :.]+)\] \[([A-Z]+)\] \[([^]]+)\] (.*)$""".toRegex()
            val match = regex.find(line) ?: return null
            val dateStr = match.groupValues[1]
            val levelStr = match.groupValues[2]
            val tag = match.groupValues[3]
            val message = match.groupValues[4]

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
            val time = sdf.parse(dateStr)?.time ?: System.currentTimeMillis()
            val level = LogLevel.valueOf(levelStr)
            LogEntry(nextId++, time, level, tag, message)
        } catch (_: Exception) {
            null
        }
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        log(LogLevel.ERROR, tag, fullMessage)
    }

    @Synchronized
    private fun log(level: LogLevel, tag: String, message: String) {
        // Output to system Logcat only in debug builds
        if (BuildConfig.DEBUG) {
            when (level) {
                LogLevel.DEBUG -> Log.d(tag, message)
                LogLevel.INFO -> Log.i(tag, message)
                LogLevel.WARN -> Log.w(tag, message)
                LogLevel.ERROR -> Log.e(tag, message)
            }
        }

        val entry = LogEntry(
            id = nextId++,
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )

        var needFileTrim = false
        synchronized(logList) {
            if (logList.size >= MAX_LOGS) {
                logList.removeAt(0)
                needFileTrim = true
            }
            logList.add(entry)
            _logsFlow.value = logList.toList()
        }

        // Persist line to disk, trimming file if count exceeded MAX_LOGS
        try {
            val file = logFile ?: return
            if (needFileTrim) {
                val content = synchronized(logList) {
                    logList.joinToString("\n") { it.toDisplayString() } + "\n"
                }
                file.writeText(content)
            } else {
                file.appendText(entry.toDisplayString() + "\n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to append log to file", e)
        }
    }

    fun clear() {
        synchronized(logList) {
            logList.clear()
            _logsFlow.value = emptyList()
        }
        try {
            logFile?.writeText("")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear log file", e)
        }
        i(TAG, "Logs cleared by user")
    }

    /**
     * Generates a complete diagnostic report with system and configuration metadata,
     * ideal for sharing or troubleshooting issues.
     */
    fun exportFormatted(context: Context): String {
        val prefs = PreferencesManager(context)
        val maskedKey = prefs.getMaskedApiKey()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        val nowStr = sdf.format(Date())

        val pInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (_: Exception) {
            null
        }

        val vName = pInfo?.versionName ?: "1.0.0"
        @Suppress("DEPRECATION")
        val vCode = pInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode else it.versionCode.toLong()
        } ?: 1L

        val sb = StringBuilder()
        sb.appendLine("=================================================================")
        sb.appendLine("hc4tw (Health Connect bridge for TrendWeight) - Diagnostic Report")
        sb.appendLine("=================================================================")
        sb.appendLine("Generated At:       $nowStr")
        sb.appendLine("App Version:        v$vName (build $vCode)")
        sb.appendLine("Android OS:         Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Device:             ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
        sb.appendLine("TrendWeight Key:    ${if (prefs.trendWeightApiKey.isNotBlank()) "Configured ($maskedKey)" else "NOT SET"}")
        sb.appendLine("Background Sync:    ${if (prefs.isBackgroundSyncEnabled) "ENABLED (${prefs.syncIntervalMinutes}-min interval)" else "DISABLED"}")
        sb.appendLine("Last Sync Status:   ${prefs.lastSyncStatus} (${prefs.lastSyncMessage})")
        sb.appendLine("Last Sync Time:     ${if (prefs.lastSyncTimestamp > 0) sdf.format(Date(prefs.lastSyncTimestamp)) else "Never"}")
        sb.appendLine("=================================================================")
        sb.appendLine("Log History (${logList.size} entries):")
        sb.appendLine("-----------------------------------------------------------------")

        synchronized(logList) {
            if (logList.isEmpty()) {
                sb.appendLine("(No logs recorded yet)")
            } else {
                for (entry in logList) {
                    sb.appendLine(entry.toDisplayString())
                }
            }
        }
        sb.appendLine("=================================================================")
        return sb.toString()
    }

    /**
     * Copies the formatted diagnostic report directly to the system clipboard.
     */
    fun copyToClipboard(context: Context): Boolean {
        return try {
            val report = exportFormatted(context)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("TrendWeight Bridge Diagnostic Logs", report)
            clipboard.setPrimaryClip(clip)
            i(TAG, "Diagnostic report copied to clipboard (${report.length} chars)")
            true
        } catch (e: Exception) {
            e(TAG, "Failed to copy logs to clipboard", e)
            false
        }
    }
}
