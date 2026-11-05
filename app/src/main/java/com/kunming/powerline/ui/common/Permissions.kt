package com.kunming.powerline.ui.common

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * 权限合规（11.5）
 * - Android 12+ 后台定位需引导用户去系统设置选择"始终允许"
 * - Android 13+ 需运行时申请 POST_NOTIFICATIONS
 */
object PermissionHelper {

    fun hasLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasBackgroundLocation(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun hasNotification(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun foregroundLocationPermissions(): Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    /** 打开应用详情页，让用户选择"始终允许" */
    fun openAppSettings(context: Context) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(i)
    }
}

/**
 * 前台定位权限请求组合器
 */
@Composable
fun rememberLocationPermissionRequest(
    onGranted: () -> Unit,
    onDenied: () -> Unit = {}
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) onGranted() else onDenied()
    }
    return {
        launcher.launch(PermissionHelper.foregroundLocationPermissions())
    }
}

/**
 * 通用的简单提示状态
 */
class ToastState {
    val message: MutableState<String?> = mutableStateOf(null)
    fun show(msg: String) {
        message.value = msg
    }
}

@Composable
fun rememberToastState(): ToastState = remember { ToastState() }
