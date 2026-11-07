package com.kunming.powerline.ui.layer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 专题图层定义（左侧齿轮面板）
 */
enum class MapLayer(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val manageHint: String,
    val defaultOn: Boolean = true
) {
    OVERHEAD_LINE("overhead", "架空线路图层", Icons.Filled.Power, "线路列表、电压筛选、创建/导入线路"),
    CABLE("cable", "输电电缆图层", Icons.Filled.Cable, "电缆段增删改查"),
    FAVORITE("favorite", "收藏点图层", Icons.Filled.Star, "收藏点增删改查、分类"),
    TAKEOFF("takeoff", "无人机起飞点图层", Icons.Filled.AirplanemodeActive, "起飞点坐标、海拔、备注"),
    PHOTO("photo", "影像图层", Icons.Filled.PhotoCamera, "照片上传、按杆塔浏览、点击全屏"),
    CROSSING("crossing", "交叉跨越图层", Icons.Filled.CallSplit, "交叉对象、跨越距离校验"),
    CAMERA("camera", "视频监控图层", Icons.Filled.Videocam, "点击打开播放器预览"),
    GUARDIAN("guardian", "护线员图层", Icons.Filled.PersonPinCircle, "护线员位置、姓名、责任段"),
    DANGER("danger", "危险点图层", Icons.Filled.Warning, "危险点类型、等级、措施"),
    THEMATIC("thematic", "专题图层", Icons.Filled.Map, "巡视点位、区域范围等点/线/面图层"),
    CUSTOM("custom", "自定义图层", Icons.Filled.Layers, "绘制结果（线/面/文字标注）");

    companion object {
        fun byId(id: String): MapLayer? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 地图图层 ID 常量（MapLibre style layer / source 命名）
 */
object LayerIds {
    const val SRC_LINE = "src-line"
    const val LYR_LINE = "lyr-line"
    const val LYR_LINE_CASING = "lyr-line-casing"
    const val LYR_LINE_HIGHLIGHT = "lyr-line-highlight"
    const val SRC_LINE_LABEL = "src-line-label"
    const val LYR_LINE_LABEL = "lyr-line-label"
    /** 线路中点名称（短线也保证能显示） */
    const val SRC_LINE_LABEL_POINT = "src-line-label-point"
    const val LYR_LINE_LABEL_POINT = "lyr-line-label-point"

    const val SRC_CABLE = "src-cable"
    const val LYR_CABLE = "lyr-cable"

    const val SRC_TOWER = "src-tower"
    const val LYR_TOWER = "lyr-tower"
    const val LYR_TOWER_LABEL = "lyr-tower-label"
    const val LYR_TOWER_SELECT = "lyr-tower-select"

    // 实时定位（蓝点 + 精度圈 + 方向箭头）
    const val SRC_MY_LOCATION = "src-my-location"
    const val LYR_MY_LOCATION_ACCURACY = "lyr-my-location-accuracy"
    const val LYR_MY_LOCATION_HEADING = "lyr-my-location-heading"
    const val LYR_MY_LOCATION = "lyr-my-location"
    const val ICON_HEADING = "icon-heading"

    const val SRC_FAVORITE = "src-favorite"
    const val LYR_FAVORITE = "lyr-favorite"

    const val SRC_TAKEOFF = "src-takeoff"
    const val LYR_TAKEOFF = "lyr-takeoff"

    const val SRC_PHOTO = "src-photo"
    const val LYR_PHOTO = "lyr-photo"

    const val SRC_CROSSING = "src-crossing"
    const val LYR_CROSSING = "lyr-crossing"

    const val SRC_CAMERA = "src-camera"
    const val LYR_CAMERA = "lyr-camera"

    const val SRC_GUARDIAN = "src-guardian"
    const val LYR_GUARDIAN = "lyr-guardian"

    const val SRC_DANGER = "src-danger"
    const val LYR_DANGER = "lyr-danger"

    // 专题图层（导入的点 / 线 / 面要素）
    const val SRC_THEMATIC = "src-thematic"
    const val LYR_THEMATIC_FILL = "lyr-thematic-fill"
    const val LYR_THEMATIC_LINE = "lyr-thematic-line"
    const val LYR_THEMATIC_POINT = "lyr-thematic-point"
    const val LYR_THEMATIC_LABEL = "lyr-thematic-label"
    /** 密集专题图层的名称（启用碰撞避让） */
    const val LYR_THEMATIC_LABEL_DENSE = "lyr-thematic-label-dense"

    /**
     * 区域名称（行政区划 / 巡视区域 / 边界线）
     *
     * 独立成层：既避免 MapLibre 在 Polygon 上放置文字不稳定的问题，
     * 也让区域名在低缩放级别就可见（不再需要放大到 10 级）。
     * 面用质心、线用中点生成独立点要素，名称落在区域内部。
     */
    const val SRC_AREA_LABEL = "src-area-label"
    const val LYR_AREA_LABEL = "lyr-area-label"
    /** 密集区域名称（>120 个要素时启用碰撞避让，避免低缩放糊满屏） */
    const val LYR_AREA_LABEL_DENSE = "lyr-area-label-dense"

    /**
     * 业务点位名称（收藏 / 起飞 / 交叉 / 视频 / 护线 / 危险）
     *
     * 图标始终显示，文字统一收进这一层并设 minZoom，
     * 低缩放时不再排版几千条文字 —— 这是收藏点图层卡顿的主因。
     */
    const val SRC_POINT_LABEL = "src-point-label"
    const val LYR_POINT_LABEL = "lyr-point-label"

    const val SRC_CUSTOM = "src-custom"
    const val LYR_CUSTOM_LINE = "lyr-custom-line"
    const val LYR_CUSTOM_FILL = "lyr-custom-fill"
    const val LYR_CUSTOM_TEXT = "lyr-custom-text"

    const val SRC_PREVIEW = "src-preview"
    const val LYR_PREVIEW_LINE = "lyr-preview-line"
    const val LYR_PREVIEW_FILL = "lyr-preview-fill"

    const val SRC_ROUTE = "src-route"
    const val LYR_ROUTE = "lyr-route"

    const val SRC_SEARCH = "src-search"
    const val LYR_SEARCH = "lyr-search"

    const val SRC_DRAW = "src-draw"
    const val LYR_DRAW_LINE = "lyr-draw-line"
    const val LYR_DRAW_FILL = "lyr-draw-fill"
    const val LYR_DRAW_POINT = "lyr-draw-point"
    const val LYR_DRAW_VERTEX = "lyr-draw-vertex"

    // 图标
    const val ICON_TOWER = "icon-tower"
    const val ICON_TOWER_SELECTED = "icon-tower-sel"
    const val ICON_FAVORITE = "icon-favorite"
    const val ICON_TAKEOFF = "icon-takeoff"
    const val ICON_PHOTO = "icon-photo"
    const val ICON_CROSSING = "icon-crossing"
    const val ICON_CAMERA = "icon-camera"
    const val ICON_GUARDIAN = "icon-guardian"
    const val ICON_DANGER = "icon-danger"
    const val ICON_SEARCH = "icon-search"
}
