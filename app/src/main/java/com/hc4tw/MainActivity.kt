package com.hc4tw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.hc4tw.health.HealthConnectManager
import com.hc4tw.logging.AppLogger
import com.hc4tw.ui.MainScreen
import com.hc4tw.ui.MainViewModel
import com.hc4tw.ui.theme.TrendWeightTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val healthConnectManager by lazy { HealthConnectManager(this) }

    private val permissionLauncher = registerForActivityResult(
        healthConnectManager.createPermissionContract()
    ) { grantedPermissions ->
        AppLogger.i("MainActivity", "Permission callback received: $grantedPermissions")
        viewModel.refreshState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TrendWeightTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        viewModel = viewModel,
                        onRequestPermissions = {
                            val state = viewModel.permissionState.value
                            if (!state.hasWeightPermission || !state.hasBodyFatPermission) {
                                permissionLauncher.launch(healthConnectManager.permissions)
                            } else if (!state.hasBackgroundPermission) {
                                permissionLauncher.launch(setOf(HealthConnectManager.PERMISSION_READ_BACKGROUND))
                            } else {
                                healthConnectManager.openPermissionSettings(this)
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshState()
    }
}
