package com.kunming.powerline.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.kunming.powerline.ui.theme.HintGray
import com.kunming.powerline.ui.theme.PowerRedLight

/**
 * 底图选择（10 种组合）
 */
@Composable
fun BasemapPickerSheet(
    current: BasemapType,
    onPick: (BasemapType) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        Modifier
            .width(268.dp)
            .background(Color(0xF5101418), RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("底图切换", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("关闭", color = HintGray, fontSize = 11.5.sp, modifier = Modifier.clickable { onDismiss() })
        }
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            BasemapType.entries.forEachIndexed { i, t ->
                val active = t == current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (active) Color(0x33B71C1C) else Color.Transparent)
                        .clickable { onPick(t) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${i + 1}.",
                        color = if (active) PowerRedLight else Color(0x99FFFFFF),
                        fontSize = 11.sp, modifier = Modifier.width(20.dp)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title,
                            color = if (active) Color.White else Color(0xDDDDDDDD),
                            fontSize = 12.5.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(t.desc, color = Color(0x77888888), fontSize = 10.sp)
                    }
                    if (active) {
                        Icon(Icons.Default.Check, null, tint = PowerRedLight, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}
