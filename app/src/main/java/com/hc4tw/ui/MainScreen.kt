package com.hc4tw.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import com.hc4tw.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import com.hc4tw.logging.LogLevel
import com.hc4tw.ui.theme.DarkSurface
import com.hc4tw.ui.theme.LogDebugColor
import com.hc4tw.ui.theme.LogErrorColor
import com.hc4tw.ui.theme.LogInfoColor
import com.hc4tw.ui.theme.LogWarnColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    val apiKey by viewModel.apiKey.collectAsState()
    val isTestingKey by viewModel.isTestingKey.collectAsState()
    val keyValidationStatus by viewModel.keyValidationStatus.collectAsState()
    val permissionState by viewModel.permissionState.collectAsState()
    val sdkStatus by viewModel.sdkStatus.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncStatus by viewModel.lastSyncStatus.collectAsState()
    val lastSyncMessage by viewModel.lastSyncMessage.collectAsState()
    val lastSyncTime by viewModel.lastSyncTimeFormatted.collectAsState()
    val lastSyncCount by viewModel.lastSyncCount.collectAsState()
    val isBackgroundSyncEnabled by viewModel.isBackgroundSyncEnabled.collectAsState()
    val syncIntervalMinutes by viewModel.syncIntervalMinutes.collectAsState()
    val syncNowDays by viewModel.syncNowDays.collectAsState()
    val forceFullDays by viewModel.forceFullDays.collectAsState()
    val logs by viewModel.logs.collectAsState()

    var isApiKeyVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = "OK",
                duration = androidx.compose.material3.SnackbarDuration.Long
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = "hc4tw Logo",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "hc4tw",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Health Connect bridge for TrendWeight",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshState() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Status")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Card 1: Sync Status & Quick Action
            item {
                StatusCard(
                    isSyncing = isSyncing,
                    lastSyncStatus = lastSyncStatus,
                    lastSyncMessage = lastSyncMessage,
                    lastSyncTime = lastSyncTime,
                    lastSyncCount = lastSyncCount,
                    canSync = permissionState.hasWeightPermission && apiKey.isNotBlank(),
                    selectedDays = syncNowDays,
                    onDaysChanged = { viewModel.setSyncNowDays(it) },
                    onSyncNow = { days, forceFull -> viewModel.syncNow(forceFull = forceFull, daysWindow = days) }
                )
            }

            // Card 2: Health Connect Authorization
            item {
                HealthConnectCard(
                    sdkStatus = sdkStatus,
                    permissionState = permissionState,
                    onRequestPermissions = onRequestPermissions
                )
            }

            // Card 3: TrendWeight API Setup
            item {
                TrendWeightSettingsCard(
                    apiKey = apiKey,
                    isApiKeyVisible = isApiKeyVisible,
                    isTestingKey = isTestingKey,
                    keyValidationStatus = keyValidationStatus,
                    onApiKeyChanged = { viewModel.onApiKeyChanged(it) },
                    onToggleVisibility = { isApiKeyVisible = !isApiKeyVisible },
                    onSaveAndTest = {
                        keyboardController?.hide()
                        viewModel.testAndSaveApiKey()
                    }
                )
            }

            // Card 4: Background Sync
            item {
                SyncOptionsCard(
                    isBackgroundSyncEnabled = isBackgroundSyncEnabled,
                    syncIntervalMinutes = syncIntervalMinutes,
                    hasBackgroundPermission = permissionState.hasBackgroundPermission,
                    onToggleBackgroundSync = { viewModel.toggleBackgroundSync(it) },
                    onIntervalChanged = { viewModel.setSyncInterval(it) },
                    onRequestPermissions = onRequestPermissions
                )
            }

            // Card 5: Diagnostic Logs & Support
            item {
                LogsCard(
                    logs = logs,
                    onShareLogs = { viewModel.shareLogs(context) },
                    onClearLogs = { viewModel.clearLogs() }
                )
            }

            // Inconspicuous App Version Footer
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = viewModel.appVersionDisplay,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StatusCard(
    isSyncing: Boolean,
    lastSyncStatus: String,
    lastSyncMessage: String,
    lastSyncTime: String,
    lastSyncCount: Int,
    canSync: Boolean,
    selectedDays: Int,
    onDaysChanged: (Int) -> Unit,
    onSyncNow: (days: Int, forceFull: Boolean) -> Unit
) {
    var isForceFullChecked by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sync Overview",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                val (badgeColor, badgeText) = when {
                    isSyncing -> MaterialTheme.colorScheme.primary to "SYNCING..."
                    lastSyncStatus == "SUCCESS" -> Color(0xFF2E7D32) to "UP TO DATE"
                    lastSyncStatus == "ERROR" -> MaterialTheme.colorScheme.error to "ERROR"
                    else -> MaterialTheme.colorScheme.outline to "READY"
                }

                Box(
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            val countSuffix = if (lastSyncTime == "Never") "" else " ($lastSyncCount record${if (lastSyncCount == 1) "" else "s"} written)"
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Last Sync: $lastSyncTime$countSuffix", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "Status: $lastSyncMessage",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onSyncNow(selectedDays, isForceFullChecked) },
                    enabled = canSync && !isSyncing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Syncing...")
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sync Now")
                    }
                }

                var isDaysDropdownExpanded by remember { mutableStateOf(false) }

                Box {
                    OutlinedButton(
                        onClick = { isDaysDropdownExpanded = true }
                    ) {
                        Text("Last $selectedDays Days ▼", fontSize = 13.sp)
                    }

                    DropdownMenu(
                        expanded = isDaysDropdownExpanded,
                        onDismissRequest = { isDaysDropdownExpanded = false }
                    ) {
                        val options = listOf(1, 3, 7, 14, 30)
                        options.forEach { days ->
                            val label = if (days == 7) "Last 7 Days (Default)" else "Last $days Days"
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onDaysChanged(days)
                                    isDaysDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Force Full Sync",
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Posts all weigh-ins even if unchanged or previously uploaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = isForceFullChecked,
                    onCheckedChange = { isForceFullChecked = it }
                )
            }
        }
    }
}

@Composable
fun HealthConnectCard(
    sdkStatus: Int,
    permissionState: com.hc4tw.health.HealthPermissionState,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.HealthAndSafety,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Health Connect Access",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                sdkStatus == HealthConnectClient.SDK_UNAVAILABLE -> {
                    Text(
                        text = "Health Connect is not supported on this device. (Requires Android 8+).",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    Text(
                        text = "Health Connect requires an update from Google Play Store.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        val uri = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }) {
                        Text("Update Health Connect")
                    }
                }
                permissionState.hasAllPermissions -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (permissionState.hasBackgroundPermission)
                                "Access granted (Weight, Body Fat & Background Sync)"
                            else
                                "Access granted (Weight & Body Fat)",
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                permissionState.hasWeightPermission -> {
                    var showManageHelpDialog by remember { mutableStateOf(false) }

                    if (showManageHelpDialog) {
                        AlertDialog(
                            onDismissRequest = { showManageHelpDialog = false },
                            title = { Text("How to Manage Permissions", fontWeight = FontWeight.Bold) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("1. Tap 'Manage Permissions' below to open Health Connect.")
                                    Text("2. Tap 'hc4tw' under 'Your health apps'.")
                                    Text("3. Toggle Weight or Body Fat permissions ON or OFF as desired.")
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showManageHelpDialog = false }) {
                                    Text("Got It")
                                }
                            }
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (permissionState.hasBackgroundPermission)
                                    "Access granted (Weight & Background Sync)"
                                else
                                    "Access granted (Weight only)",
                                color = Color(0xFF2E7D32),
                                fontWeight = FontWeight.Medium,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = onRequestPermissions) {
                                Text("Manage Permissions")
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(onClick = { showManageHelpDialog = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                    contentDescription = "How to Manage Permissions",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                permissionState.hasBodyFatPermission -> {
                    Column {
                        Text(
                            text = "Weight permission is required to sync with TrendWeight.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Grant Weight Permission")
                        }
                    }
                }
                else -> {
                    Text(
                        text = "Permission is required to read your weight and body fat records.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Grant Health Connect Permissions")
                    }
                }
            }
        }
    }
}

@Composable
fun TrendWeightSettingsCard(
    apiKey: String,
    isApiKeyVisible: Boolean,
    isTestingKey: Boolean,
    keyValidationStatus: String?,
    onApiKeyChanged: (String) -> Unit,
    onToggleVisibility: () -> Unit,
    onSaveAndTest: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TrendWeight API Key",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChanged,
                label = { Text("TrendWeight API Key") },
                placeholder = { Text("Enter your API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onToggleVisibility) {
                        Icon(
                            imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isApiKeyVisible) "Hide Key" else "Show Key"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSaveAndTest() })
            )

            if (keyValidationStatus != null) {
                Spacer(modifier = Modifier.height(4.dp))
                val isSuccess = keyValidationStatus.startsWith("Valid")
                Text(
                    text = keyValidationStatus,
                    color = if (isSuccess) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://trendweight.com/settings"))
                        context.startActivity(browserIntent)
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Get API Key", fontSize = 13.sp)
                }

                Button(
                    onClick = onSaveAndTest,
                    enabled = apiKey.isNotBlank() && !isTestingKey
                ) {
                    if (isTestingKey) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verifying...")
                    } else {
                        Text("Save & Test")
                    }
                }
            }
        }
    }
}

@Composable
fun SyncOptionsCard(
    isBackgroundSyncEnabled: Boolean,
    syncIntervalMinutes: Long,
    hasBackgroundPermission: Boolean,
    onToggleBackgroundSync: (Boolean) -> Unit,
    onIntervalChanged: (Long) -> Unit,
    onRequestPermissions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Background Sync",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            val currentIntervalLabel = when {
                syncIntervalMinutes < 60 -> "$syncIntervalMinutes minutes"
                syncIntervalMinutes == 60L -> "1 hour (Default)"
                (syncIntervalMinutes % 60) == 0L -> "${syncIntervalMinutes / 60} hours"
                else -> "$syncIntervalMinutes minutes"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Periodic Sync",
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = when {
                            !hasBackgroundPermission -> "Requires Health Connect background permission"
                            isBackgroundSyncEnabled -> "Enabled ($currentIntervalLabel)"
                            else -> "Periodically sync new weigh-ins"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!hasBackgroundPermission) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isBackgroundSyncEnabled && hasBackgroundPermission,
                    onCheckedChange = onToggleBackgroundSync,
                    enabled = hasBackgroundPermission
                )
            }

            if (!hasBackgroundPermission) {
                var showBgHelpDialog by remember { mutableStateOf(false) }

                if (showBgHelpDialog) {
                    AlertDialog(
                        onDismissRequest = { showBgHelpDialog = false },
                        title = { Text("How to Grant Background Permission", fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("1. Tap 'Grant Background Permission' below to open Health Connect.")
                                Text("2. Tap 'hc4tw' under 'Your health apps'.")
                                Text("3. Tap 'Additional access' near the bottom.")
                                Text("4. Turn ON 'Access data in the background'.")
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showBgHelpDialog = false }) {
                                Text("Got It")
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onRequestPermissions) {
                        Text("Grant Background Permission")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = { showBgHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "How to Grant Background Permission",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (isBackgroundSyncEnabled) {
                Spacer(modifier = Modifier.height(12.dp))

                var isDropdownExpanded by remember { mutableStateOf(false) }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { isDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sync Interval: $currentIntervalLabel")
                    }

                    DropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false }
                    ) {
                        val intervals = listOf(
                            15L to "15 minutes",
                            30L to "30 minutes",
                            60L to "1 hour (Default)",
                            180L to "3 hours",
                            360L to "6 hours",
                            720L to "12 hours",
                            1440L to "24 hours (1 day)"
                        )
                        intervals.forEach { (mins, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onIntervalChanged(mins)
                                    isDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "⚠️ Note: Shorter sync intervals (e.g. 15 or 30 minutes) check for new weigh-ins more frequently, which may impact battery life.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (syncIntervalMinutes < 60) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun LogsCard(
    logs: List<com.hc4tw.logging.LogEntry>,
    onShareLogs: () -> Unit,
    onClearLogs: () -> Unit
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom as new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Diagnostic Logs (${logs.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Row {
                    IconButton(onClick = onShareLogs) {
                        Icon(Icons.Default.Share, contentDescription = "Share Logs")
                    }
                    IconButton(onClick = onClearLogs) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Logs")
                    }
                }
            }

            Text(
                text = "Use 'Share Logs' if you encounter any issues and need to share diagnostic logs for troubleshooting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 280.dp)
                    .background(DarkSurface, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "No log messages recorded yet.",
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(logs, key = { it.id }) { log ->
                            val color = when (log.level) {
                                LogLevel.DEBUG -> LogDebugColor
                                LogLevel.INFO -> LogInfoColor
                                LogLevel.WARN -> LogWarnColor
                                LogLevel.ERROR -> LogErrorColor
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = log.toDisplayString(),
                                    color = color,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
