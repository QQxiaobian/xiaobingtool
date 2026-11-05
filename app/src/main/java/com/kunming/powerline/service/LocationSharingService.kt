package com.kunming.powerline.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kunming.powerline.MainActivity
import com.kunming.powerline.R
import com.kunming.powerline.data.local.AppDatabase
import com.kunming.powerline.data.local.entity.TrackPointEntity
import com.kunming.powerline.data.location.AmapLocationClient
import com.kunming.powerline.data.location.FixLocation
import com.kunming.powerline.data.location.LocationShareSocket
import com.kunming.powerline.data.prefs.SettingsStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 巡检定位 / 位置共享前台服务
 * - 通知栏常驻（Android 12+ 需 foregroundServiceType=location）
 * - 高德定位 1 秒一次
 * - 轨迹写 Room，同时 WebSocket 上报共享房间
 * - 被系统杀死后由 WorkManager + AlarmManager 唤醒重连
 */
@AndroidEntryPoint
class LocationSharingService : Service() {

    @Inject lateinit var amap: AmapLocationClient
    @Inject lateinit var socket: LocationShareSocket
    @Inject lateinit var settings: SettingsStore
    @Inject lateinit var db: AppDatabase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectJob: Job? = null
    private var roomId: String = ""
    private var userId: String = ""

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {}
        }
        roomId = intent?.getStringExtra(EXTRA_ROOM).orEmpty()
        userId = intent?.getStringExtra(EXTRA_USER).orEmpty()

        startForegroundCompat("正在准备定位…")

        scope.launch {
            if (userId.isBlank()) userId = settings.ensureUserId()
            if (roomId.isNotBlank()) {
                socket.connect(roomId, userId)
            }
        }

        _running.value = true
        startCollect()
        amap.start(intervalMs = 1000L, highAccuracy = true, once = false)

        // 网络恢复后自动重连
        collectJob = scope.launch {
            socket.events.collect { msg ->
                if (msg.startsWith("连接失败")) {
                    delay(3000)
                    if (roomId.isNotBlank()) socket.connect(roomId, userId)
                }
            }
        }
        return START_STICKY
    }

    private fun startCollect() {
        scope.launch {
            amap.locations.collect { fix -> onFix(fix) }
        }
        scope.launch {
            amap.errors.collect { e -> updateNotification("定位异常：$e") }
        }
    }

    private var lastNotifyTs = 0L
    private var lastTrackTs = 0L

    private fun onFix(fix: FixLocation) {
        _lastFix.value = fix
        socket.send(fix.lon, fix.lat, fix.speed)

        // 轨迹：每 3 秒落库一条，避免数据膨胀
        val now = System.currentTimeMillis()
        if (now - lastTrackTs > 3000) {
            lastTrackTs = now
            scope.launch {
                try {
                    db.miscDao().insertTrack(
                        TrackPointEntity(
                            lon = fix.lon, lat = fix.lat, speed = fix.speed,
                            accuracy = fix.accuracy, ts = fix.time, roomId = roomId
                        )
                    )
                } catch (_: Throwable) {
                }
            }
        }

        if (now - lastNotifyTs > 5000) {
            lastNotifyTs = now
            updateNotification(
                String.format("经度 %.5f  纬度 %.5f  精度 %.0fm", fix.lon, fix.lat, fix.accuracy)
            )
        }
    }

    // ------------------------------------------------------------------
    // 通知
    // ------------------------------------------------------------------
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "巡检定位服务", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "后台持续定位与位置共享"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, LocationSharingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("输电线路智能巡检 · 定位中")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_location)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .addAction(0, "停止共享", stopPi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startForegroundCompat(text: String) {
        val n = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFY_ID, n)
        }
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFY_ID, buildNotification(text))
        } catch (_: Throwable) {
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        _running.value = false
        amap.stop()
        socket.close()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "location_sharing"
        const val NOTIFY_ID = 1001
        const val ACTION_START = "com.kunming.powerline.START_LOCATION"
        const val ACTION_STOP = "com.kunming.powerline.STOP_LOCATION"
        const val EXTRA_ROOM = "room"
        const val EXTRA_USER = "user"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _lastFix = MutableStateFlow<FixLocation?>(null)
        val lastFix: StateFlow<FixLocation?> = _lastFix.asStateFlow()

        fun start(context: Context, room: String, user: String) {
            val i = Intent(context, LocationSharingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ROOM, room)
                putExtra(EXTRA_USER, user)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, LocationSharingService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
