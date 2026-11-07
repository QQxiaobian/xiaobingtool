package com.kunming.powerline.ui.layermanage

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunming.powerline.data.local.entity.CableEntity
import com.kunming.powerline.data.local.entity.CameraEntity
import com.kunming.powerline.data.local.entity.CrossingEntity
import com.kunming.powerline.data.local.entity.DangerPointEntity
import com.kunming.powerline.data.local.entity.FavoriteEntity
import com.kunming.powerline.data.local.entity.GuardianEntity
import com.kunming.powerline.data.local.entity.LineEntity
import com.kunming.powerline.data.local.entity.TakeoffPointEntity
import com.kunming.powerline.ui.MapViewModel
import com.kunming.powerline.ui.layer.MapLayer
import com.kunming.powerline.ui.theme.LightPanelTheme
import com.kunming.powerline.ui.theme.PanelBg
import com.kunming.powerline.ui.theme.PanelCard
import com.kunming.powerline.ui.theme.PanelLine
import com.kunming.powerline.ui.theme.PanelText
import com.kunming.powerline.ui.theme.PanelTextHint
import com.kunming.powerline.ui.theme.PanelTextSub
import com.kunming.powerline.ui.theme.PowerRed
import com.kunming.powerline.util.GeoUtils
import com.kunming.powerline.util.VoltagePalette
import kotlinx.coroutines.launch

/** 管理页通用外壳：浅米色 + 顶部标题 + 返回 */
@Composable
fun ManageScaffold(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    LightPanelTheme {
        Column(
            Modifier
                .fillMaxSize()
                .background(PanelBg)
                .statusBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "返回", tint = PanelText)
                }
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PanelText)
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, fontSize = 11.sp, color = PanelTextSub)
                    }
                }
                actions?.invoke()
                Spacer(Modifier.width(6.dp))
            }
            Divider(color = Color(0x14000000), thickness = 0.8.dp)
            content()
        }
    }
}

/** 通用行 */
@Composable
fun ManageRow(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    leadingColor: Color? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(start = 16.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingColor != null) {
            Box(Modifier.size(9.dp).background(leadingColor, CircleShape))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.5.sp, color = PanelText, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 11.5.sp, color = PanelTextSub, lineHeight = 16.sp)
            }
        }
        if (trailing != null) Text(trailing, fontSize = 11.5.sp, color = PanelTextSub)
        if (onEdit != null) {
            Text(
                "编辑",
                fontSize = 11.5.sp,
                color = PowerRed,
                modifier = Modifier
                    .clickable { onEdit() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Default.Delete, "删除", tint = Color(0xFFB0A197), modifier = Modifier.size(17.dp))
            }
        }
    }
    Divider(color = PanelLine.copy(alpha = 0.85f), thickness = 0.6.dp, modifier = Modifier.padding(start = 16.dp))
}

/** 小输入框 */
@Composable
fun ManageField(label: String, value: String, numeric: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 11.5.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PanelText,
            unfocusedTextColor = PanelText,
            disabledTextColor = PanelTextHint,
            focusedContainerColor = PanelCard,
            unfocusedContainerColor = PanelCard,
            disabledContainerColor = Color(0xFFF3ECE1),
            cursorColor = PowerRed,
            focusedBorderColor = PowerRed,
            unfocusedBorderColor = PanelLine,
            disabledBorderColor = PanelLine,
            focusedLabelColor = PowerRed,
            unfocusedLabelColor = PanelTextSub,
            disabledLabelColor = PanelTextHint,
            focusedPlaceholderColor = PanelTextHint,
            unfocusedPlaceholderColor = PanelTextHint
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

/** 底部主按钮 */
@Composable
fun PrimaryAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .background(if (enabled) PowerRed else Color(0x33B71C1C), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * 总入口：按图层分发到具体管理页
 */
@Composable
fun LayerManageHost(
    layer: MapLayer,
    vm: MapViewModel,
    onClose: () -> Unit,
    onToast: (String) -> Unit,
    onFocus: (Double, Double, Double) -> Unit,
    onOpenImport: () -> Unit
) {
    when (layer) {
        MapLayer.OVERHEAD_LINE -> LineListScreen(vm, onClose, onToast, onFocus, onOpenImport)
        MapLayer.CABLE -> CableManageScreen(vm, onClose, onToast, onFocus)
        MapLayer.FAVORITE -> FavoriteManageScreen(vm, onClose, onToast, onFocus)
        MapLayer.TAKEOFF -> TakeoffManageScreen(vm, onClose, onToast, onFocus)
        MapLayer.PHOTO -> PhotoManageScreen(vm, onClose, onToast, onFocus)
        MapLayer.CROSSING -> CrossingManageScreen(vm, onClose, onToast, onFocus)
        MapLayer.CAMERA -> CameraManageScreen(vm, onClose, onToast)
        MapLayer.GUARDIAN -> GuardianManageScreen(vm, onClose, onToast, onFocus)
        MapLayer.DANGER -> DangerManageScreen(vm, onClose, onToast, onFocus)
        MapLayer.THEMATIC -> ThematicLayerScreen(
            vm = vm,
            onBack = onClose,
            onToast = onToast,
            onFocus = onFocus,
            onImport = onOpenImport,
            onDeleteAll = {
                vm.deleteAllThematic()
                onToast("已清空全部专题图层")
            }
        )
        MapLayer.CUSTOM -> CustomManageScreen(vm, onClose, onToast, onOpenImport)
    }
}
