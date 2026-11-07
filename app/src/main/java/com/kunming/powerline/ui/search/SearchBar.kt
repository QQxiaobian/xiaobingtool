package com.kunming.powerline.ui.search

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kunming.powerline.ui.SearchHit
import com.kunming.powerline.ui.theme.HintGray
import com.kunming.powerline.ui.theme.PowerRedLight

/**
 * 顶部搜索栏 + 感叹号帮助入口
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onHelpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focus = LocalFocusManager.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("点击搜索地点、线路、电缆、收藏点...", fontSize = 12.5.sp, color = Color(0xFF9A9086), maxLines = 1)
                },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF5C544A), modifier = Modifier.size(19.dp)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            onQueryChange("")
                            onSearch("")
                        }) {
                            Icon(Icons.Default.Clear, null, tint = Color(0xFF5C544A), modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.5.sp, color = Color(0xFF2B2B2B)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xF5FFFDF8),
                    unfocusedContainerColor = Color(0xF5FFFDF8),
                    focusedBorderColor = Color(0x33000000),
                    unfocusedBorderColor = Color(0x22000000),
                    cursorColor = PowerRedLight
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    onSearch(query)
                })
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xE6B71C1C))
                .clickable { onHelpClick() },
            contentAlignment = Alignment.Center
        ) {
            Text("!", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 搜索语法帮助弹窗
 */
@Composable
fun SearchHelpDialog(onDismiss: () -> Unit) {
    val rows = listOf(
        "杆塔" to "线路名称 杆塔号，如「220kV某甲线 12」，定位并高亮",
        "地名/POI" to "直接输入地名、收藏点、起飞点、全景影像名",
        "经纬度" to "「103.0232 25.2203」，支持度分秒自动识别",
        "照片" to "「照片 关键字」检索现场影像",
        "起飞点" to "「起飞点 关键词」检索无人机起飞点",
        "视频" to "「视频 关键词」检索视频监控台账",
        "护线员" to "「护线员 人名」定位护线员实时位置",
        "杆塔模糊" to "「杆塔 关键词」全文模糊搜索杆塔"
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = com.kunming.powerline.ui.theme.CreamBackground,
        shape = RoundedCornerShape(18.dp),
        title = {
            Text("搜索语法说明", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF2B2B2B))
        },
        text = {
            LazyColumn(Modifier.height(360.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(rows) { (k, v) ->
                    Row {
                        Box(
                            Modifier
                                .width(74.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x1FB71C1C))
                                .padding(vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(k, fontSize = 11.5.sp, color = Color(0xFFB71C1C), fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(v, fontSize = 12.5.sp, color = Color(0xFF444444), modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            Text(
                "知道了", color = Color(0xFFB71C1C), fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onDismiss() }.padding(10.dp)
            )
        }
    )
}

/**
 * 搜索结果列表（BottomSheet 内容）
 */
@Composable
fun SearchResultList(
    hits: List<SearchHit>,
    onPick: (SearchHit) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier.fillMaxWidth().height(260.dp)) {
        items(hits) { h ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(h) }
                    .padding(horizontal = 16.dp, vertical = 11.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFF222222))
                    Spacer(Modifier.width(8.dp))
                    Text(h.kindLabel(), fontSize = 10.sp, color = Color(0xFF8D6E63))
                }
                Text(
                    h.subtitle, fontSize = 11.5.sp, color = Color(0xFF777777),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            androidx.compose.material3.Divider(color = Color(0x14000000), thickness = 0.7.dp)
        }
    }
}

fun SearchHit.kindLabel(): String = when (kind) {
    "tower" -> "杆塔"
    "line" -> "线路"
    "cable" -> "电缆"
    "favorite" -> "收藏点"
    "takeoff" -> "起飞点"
    "camera" -> "视频"
    "guardian" -> "护线员"
    "photo" -> "照片"
    "coord" -> "坐标"
    else -> ""
}
