package com.kunming.powerline.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kunming.powerline.data.local.entity.ThematicFeatureEntity
import com.kunming.powerline.data.local.entity.ThematicLayerEntity
import com.kunming.powerline.ui.theme.LightPanelTheme
import com.kunming.powerline.ui.theme.PanelBg
import com.kunming.powerline.ui.theme.PanelCard
import com.kunming.powerline.ui.theme.PanelLine
import com.kunming.powerline.ui.theme.PanelText
import com.kunming.powerline.ui.theme.PanelTextSub
import com.kunming.powerline.ui.theme.PowerRed

/**
 * 点击专题区域 / 点位 / 线后弹出的信息卡
 *
 * 行政区划、巡视区域这类数据，KML 里通常只有 `<name>` 和 `<description>`，
 * 地图上放不下完整备注，这里给出全量信息：
 * 名称、所属图层、类型、KML 备注（description）、其余属性（adcode 等）。
 */
@Composable
fun ThematicInfoDialog(
    feature: ThematicFeatureEntity,
    layer: ThematicLayerEntity?,
    onDismiss: () -> Unit,
    onFocus: () -> Unit,
    onConvertToLine: (() -> Unit)? = null,
    onMove: (() -> Unit)? = null
) {
    val props = ThematicLabels.parseProps(feature.propertiesJson)
    val desc = ThematicLabels.descriptionOf(feature.propertiesJson)
    // 属性表里去掉名称与备注，剩下的是 adcode、面积之类的原始字段
    val extraAttrs = props.filterKeys { k ->
        !k.equals("name", true) && !k.equals("description", true) && !k.equals("desc", true) &&
            !k.equals("remark", true) && !k.equals("note", true) && !k.equals("备注", true) &&
            !k.equals("说明", true) && !k.equals("描述", true)
    }.entries.take(10)

    val color = layer?.let { Color(it.color) } ?: PowerRed
    val title = feature.name?.takeIf { it.isNotBlank() }
        ?: layer?.name?.takeIf { it.isNotBlank() }
        ?: "${kindLabel(feature.kind)} ${feature.seq + 1}"

    LightPanelTheme {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PanelBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            title,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = PanelText
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        buildString {
                            append(kindLabel(feature.kind))
                            layer?.name?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                            feature.lon?.let { lon ->
                                feature.lat?.let { lat ->
                                    append(" · ").append("%.5f, %.5f".format(lon, lat))
                                }
                            }
                        },
                        fontSize = 11.5.sp,
                        color = PanelTextSub
                    )

                    DividerLine()

                    Column(
                        Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        if (!desc.isNullOrBlank()) {
                            SectionLabel("备注")
                            Text(
                                desc,
                                fontSize = 13.sp,
                                color = PanelText,
                                lineHeight = 20.sp
                            )
                        } else {
                            Text(
                                "该要素没有备注信息",
                                fontSize = 12.sp,
                                color = PanelTextSub
                            )
                        }
                        if (extraAttrs.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            SectionLabel("属性")
                            extraAttrs.forEach { (k, v) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        k,
                                        fontSize = 11.5.sp,
                                        color = PanelTextSub,
                                        modifier = Modifier.width(86.dp)
                                    )
                                    Text(
                                        v,
                                        fontSize = 12.sp,
                                        color = PanelText,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    DividerLine()

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DialogAction("定位") {
                            onFocus()
                            onDismiss()
                        }
                        if (onConvertToLine != null) {
                            DialogAction("转为线路") {
                                onConvertToLine()
                                onDismiss()
                            }
                        }
                        if (onMove != null) {
                            DialogAction("改图层") {
                                onMove()
                            }
                        }
                        DialogAction("关闭", primary = true) { onDismiss() }
                    }
                }
            }
        }
    }
}

@Composable
private fun DividerLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .height(1.dp)
            .background(PanelLine)
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        color = PanelTextSub,
        modifier = Modifier.padding(bottom = 3.dp)
    )
}

@Composable
private fun DialogAction(text: String, primary: Boolean = false, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.5.sp,
        color = if (primary) PowerRed else PanelText,
        fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 8.dp)
    )
}

private fun kindLabel(kind: String): String = when (kind) {
    "point" -> "点位"
    "line" -> "线"
    "polygon" -> "区域"
    else -> kind
}
