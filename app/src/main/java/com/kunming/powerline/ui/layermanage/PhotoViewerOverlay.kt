package com.kunming.powerline.ui.layermanage

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * 全屏照片查看（支持双指缩放、双击还原）
 */
@Composable
fun PhotoViewerOverlay(path: String, onClose: () -> Unit) {
    val bmp = remember(path) {
        runCatching {
            val f = File(path)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
        }.getOrNull()
    }
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF2000000))
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1.2f) 1f else 2.2f; if (scale == 1f) { offsetX = 0f; offsetY = 0f } }) }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    if (scale > 1f) { offsetX += pan.x; offsetY += pan.y }
                }
            }
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "现场照片",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("图片文件不存在或无法读取\n$path", color = Color(0xFFCCCCCC), fontSize = 12.sp)
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
        ) {
            Icon(Icons.Default.Close, "关闭", tint = Color.White)
        }
        Text(
            "双指缩放 · 双击放大 · 点击右上角关闭",
            color = Color(0x99FFFFFF), fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).clickable { onClose() }
        )
    }
}
