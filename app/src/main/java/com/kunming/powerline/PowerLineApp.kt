package com.kunming.powerline

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.log.Logger

@HiltAndroidApp
class PowerLineApp : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            MapLibre.getInstance(
                this,
                BuildConfig.TIANDITU_KEY.ifBlank { null },
                WellKnownTileServer.MapLibre
            )
        } catch (t: Throwable) {
            Log.e("PowerLineApp", "MapLibre 初始化失败：${t.message}", t)
        }
        try {
            Logger.setVerbosity(if (BuildConfig.DEBUG_MODE) Logger.INFO else Logger.WARN)
        } catch (_: Throwable) {
        }
    }
}
