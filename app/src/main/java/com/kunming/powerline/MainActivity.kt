package com.kunming.powerline

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kunming.powerline.ui.MapScreen
import com.kunming.powerline.ui.common.PermissionHelper
import com.kunming.powerline.ui.theme.PowerLineTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 单 Activity + Compose 架构入口
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] != true) {
            // 用户拒绝后引导到系统设置（后台定位必须"始终允许"）
            PermissionHelper.openAppSettings(this)
        }
    }

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PowerLineTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF0E1116))) {
                    MapScreen()
                }
            }
            LaunchedEffect(Unit) {
                if (!PermissionHelper.hasLocation(this@MainActivity)) {
                    locationPermission.launch(PermissionHelper.foregroundLocationPermissions())
                } else if (!PermissionHelper.hasNotification(this@MainActivity)) {
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}
