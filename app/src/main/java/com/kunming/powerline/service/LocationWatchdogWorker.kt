package com.kunming.powerline.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kunming.powerline.data.prefs.SettingsStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 开机 / 升级后拉起守护任务
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            LocationWatchdogWorker.enqueue(context)
        }
    }
}

/**
 * 守护 Worker：若用户开启了位置共享但服务未运行，则重新拉起
 */
class LocationWatchdogWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun settings(): SettingsStore
    }

    override suspend fun doWork(): Result {
        return try {
            val settings = EntryPointAccessors.fromApplication(context, WorkerEntryPoint::class.java).settings()
            val enabled = settings.shareEnabled.first()
            if (enabled && !LocationSharingService.running.value) {
                val room = settings.roomId.first()
                val user = settings.ensureUserId()
                Log.i("Watchdog", "重启定位服务，room=$room")
                LocationSharingService.start(context, room, user)
            }
            Result.success()
        } catch (t: Throwable) {
            Log.w("Watchdog", "重启失败", t)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_NAME = "location_watchdog"

        fun enqueue(context: Context) {
            val req = PeriodicWorkRequestBuilder<LocationWatchdogWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}
