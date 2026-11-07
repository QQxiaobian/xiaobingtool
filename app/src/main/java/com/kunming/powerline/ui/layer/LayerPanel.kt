package com.kunming.powerline.ui.layer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunming.powerline.ui.theme.PanelBg
import com.kunming.powerline.ui.theme.PanelLine
import com.kunming.powerline.ui.theme.PanelText
import com.kunming.powerline.ui.theme.PanelTextSub
import com.kunming.powerline.ui.theme.PowerRed
import com.kunming.powerline.util.VoltagePaletteUi

/**
 * 图层面板（左侧齿轮弹出）
 */
@Composable
fun LayerPanel(
    visible: Boolean,
    layerState: Map<String, Boolean>,
    onToggle: (MapLayer) -> Unit,
    onManage: (MapLayer) -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(visible) {
        Box(
            Modifier
                .fillMaxWidth(0.86f)
                .background(PanelBg, RoundedCornerShape(0.dp, 18.dp, 18.dp, 0.dp))
        ) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "专题图层", color = PanelText, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                    )
                    Box(
                        Modifier.size(30.dp).clip(RoundedCornerShape(15.dp))
                            .background(Color(0x14000000)).clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close, null, tint = PanelTextSub,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                Text(
                    "开启后在地图上显示对应专题要素",
                    color = PanelTextSub, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                Divider(color = PanelLine, thickness = 1.dp)
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(MapLayer.entries) { layer ->
                        val on = layerState[layer.id] ?: layer.defaultOn
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(layer) }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                layer.icon, null,
                                tint = if (on) PowerRed else Color(0xFFA79C90),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(layer.title, color = PanelText, fontSize = 14.sp)
                                Text(
                                    layer.manageHint, color = PanelTextSub, fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFEFE7DA))
                                    .clickable { onManage(layer) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("管理", color = PanelTextSub, fontSize = 11.sp)
                                    Icon(
                                        Icons.Default.ChevronRight, null,
                                        tint = PanelTextSub, modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            RedSwitch(checked = on) { onToggle(layer) }
                        }
                        Divider(
                            color = PanelLine, thickness = 0.6.dp,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 图例（固定左下角）
 */
@Composable
fun VoltageLegend(
    visible: Boolean,
    filter: Set<Int>,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(visible) {
        Column(
            modifier
                .background(Color(0xE6FDF7EE), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text("电压等级", color = PanelTextSub, fontSize = 10.sp)
            VoltagePaletteUi.styles.forEach { s ->
                val on = filter.contains(s.level)
                Row(
                    Modifier.clickable { onToggle(s.level) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .width(22.dp)
                            .height(s.widthDp.dp.coerceAtMost(6.dp))
                            .background(if (on) s.color else Color(0x33000000), RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        s.label,
                        color = if (on) PanelText else Color(0x661C1B1B),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

/**
 * 红色圆角胶囊开关
 */
@Composable
fun RedSwitch(
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        Modifier
            .width(42.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    !enabled -> Color(0xFFE2D9CC)
                    checked -> PowerRed
                    else -> Color(0xFFD6CBBB)
                }
            )
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Box(
            Modifier
                .padding(2.dp)
                .size(20.dp)
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(RoundedCornerShape(10.dp))
                .background(if (checked) Color.White else Color(0xFFBFC5CC))
        )
    }
}
