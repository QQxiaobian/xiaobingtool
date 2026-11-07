package com.kunming.powerline.ui.map

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.maps.MapView

/**
 * Compose 容器：用 AndroidView 嵌入 MapView，并把生命周期透传过去
 */
@Composable
fun MapLibreView(
    modifier: Modifier = Modifier,
    controller: MapController,
    initialBasemap: BasemapType,
    serverTileBase: String = com.kunming.powerline.BuildConfig.TILE_SERVER_URL,
    onMapReady: (MapController) -> Unit = {},
    onTap: (lat: Double, lon: Double, x: Float, y: Float) -> Unit = { _, _, _, _ -> },
    onLongPress: (lat: Double, lon: Double, x: Float, y: Float) -> Unit = { _, _, _, _ -> }
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
    }
    var initialized = remember { arrayOf(false) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.fillMaxSize()
    ) { view ->
        if (!initialized[0]) {
            initialized[0] = true
            view.onCreate(null)
            view.getMapAsync { map ->
                controller.attach(map)
                map.uiSettings.apply {
                    isCompassEnabled = false
                    isRotateGesturesEnabled = true
                    isTiltGesturesEnabled = true
                    isZoomGesturesEnabled = true
                    isScrollGesturesEnabled = true
                    isDoubleTapGesturesEnabled = true
                    setLogoEnabled(false)
                    setAttributionMargins(6, 6, 6, 6)
                }
                map.addOnMapClickListener { latLng ->
                    val px = controller.latLngToPixel(latLng)
                    onTap(latLng.latitude, latLng.longitude, px?.x ?: 0f, px?.y ?: 0f)
                    true
                }
                map.addOnMapLongClickListener { latLng ->
                    val px = controller.latLngToPixel(latLng)
                    onLongPress(latLng.latitude, latLng.longitude, px?.x ?: 0f, px?.y ?: 0f)
                    true
                }
                onMapReady(controller)
                controller.loadBasemap(initialBasemap, keepCamera = false, tileServerBase = serverTileBase)
            }
        }
    }

    // 底图切换 / 服务器瓦片地址变更：保留 CameraPosition 重建样式
    LaunchedEffect(initialBasemap, serverTileBase) {
        if (controller.map != null) {
            controller.loadBasemap(
                initialBasemap,
                keepCamera = true,
                tileServerBase = serverTileBase
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {}
}
