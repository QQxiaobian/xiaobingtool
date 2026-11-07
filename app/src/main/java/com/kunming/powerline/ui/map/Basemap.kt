package com.kunming.powerline.ui.map

import com.kunming.powerline.BuildConfig

/**
 * 底图类型（10 种组合）
 */
enum class BasemapType(
    val title: String,
    val desc: String,
    val isServer: Boolean = false
) {
    YN_SATELLITE("云南天地图影像", "省级高清卫星影像（默认）"),
    YN_SATELLITE_ROAD("云南天地图影像 + 路网注记", "影像叠加自制在线地图注记"),
    YN_VECTOR("云南天地图电子地图", "省级电子地图"),
    YN_TERRAIN("云南天地图地形晕渲", "地形晕渲底图"),
    CN_SATELLITE("天地图 - 全国影像", "全国卫星影像"),
    CN_SATELLITE_ROAD("天地图 - 全国影像 + 自制在线地图", "全国影像叠加注记"),
    YN_SATELLITE_SERVER("云南天地图影像（服务器版）", "自建瓦片服务器", true),
    CN_SATELLITE_SERVER("全国天地图影像（服务器版）", "自建瓦片服务器", true),
    YN_SATELLITE_SERVER_ROAD("云南天地图影像（服务器版）+ 在线路网", "自建瓦片 + 在线路网", true),
    YN_VECTOR_SERVER("云南天地图电子地图（服务器版）", "自建瓦片服务器电子地图", true);

    val hasRoadOverlay: Boolean
        get() = this == YN_SATELLITE_ROAD || this == CN_SATELLITE_ROAD || this == YN_SATELLITE_SERVER_ROAD
}

/**
 * 天地图 WMTS 栅格瓦片源构造
 * 使用 RESTful WMTS：{s}.tianditu.gov.cn/{layer}_w/wmts?SERVICE=WMTS...&tk=KEY
 */
object TileSourceFactory {

    private const val YN_SUB = "https://t{s}.tianditu.gov.cn"
    private val SUBDOMAINS = arrayOf("0", "1", "2", "3", "4", "5", "6", "7")

    fun tiandituUrl(layer: String, key: String): String =
        "$YN_SUB/$layer" + "_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=$layer" +
            "&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk=$key"

    private fun key(): String =
        BuildConfig.TIANDITU_KEY_YUNNAN.ifBlank { BuildConfig.TIANDITU_KEY }

    /** 主底图 URL */
    fun primaryTileUrl(type: BasemapType): String {
        val k = key()
        return when (type) {
            BasemapType.YN_SATELLITE,
            BasemapType.YN_SATELLITE_ROAD,
            BasemapType.YN_SATELLITE_SERVER,
            BasemapType.YN_SATELLITE_SERVER_ROAD -> tiandituUrl("img", k)

            BasemapType.YN_VECTOR,
            BasemapType.YN_VECTOR_SERVER -> tiandituUrl("vec", k)

            BasemapType.YN_TERRAIN -> tiandituUrl("ter", k)

            BasemapType.CN_SATELLITE,
            BasemapType.CN_SATELLITE_SERVER,
            BasemapType.CN_SATELLITE_ROAD -> tiandituUrl("img", BuildConfig.TIANDITU_KEY.ifBlank { k })
        }
    }

    /** 服务器版瓦片 URL（自建） */
    fun serverTileUrl(type: BasemapType): String {
        val base = BuildConfig.TILE_SERVER_URL
        return when (type) {
            BasemapType.YN_SATELLITE_SERVER, BasemapType.YN_SATELLITE_SERVER_ROAD -> base.replace("tiles/", "tiles/yn_img/")
            BasemapType.CN_SATELLITE_SERVER -> base.replace("tiles/", "tiles/cn_img/")
            BasemapType.YN_VECTOR_SERVER -> base.replace("tiles/", "tiles/yn_vec/")
            else -> base
        }
    }

    /** 注记 / 路网叠加瓦片 */
    fun overlayTileUrl(type: BasemapType): String? = when (type) {
        BasemapType.YN_SATELLITE_ROAD, BasemapType.YN_SATELLITE_SERVER_ROAD -> tiandituUrl("cia", key())
        BasemapType.CN_SATELLITE_ROAD -> tiandituUrl("cia", BuildConfig.TIANDITU_KEY.ifBlank { key() })
        else -> null
    }

    fun subdomains(): Array<String> = SUBDOMAINS

    /**
     * 展开 {s} 子域名占位符为多 URL 列表。
     * MapLibre raster source 不自动替换 {s}（Leaflet 语法），需预先展开，
     * 把多个子域名 URL 放进 tiles 数组由 MapLibre 轮询实现负载均衡。
     */
    private fun expandTiles(url: String): List<String> =
        if (url.contains("{s}")) SUBDOMAINS.map { url.replace("{s}", it) } else listOf(url)

    private fun tilesJson(urls: List<String>): String =
        urls.joinToString(",") { """"$it"""" }

    /**
     * 生成 MapLibre Style JSON
     * - raster 源 + raster 图层，tileSize 256
     * - 世界背景色避免瓦片未加载时花屏
     */
    fun buildStyleJson(type: BasemapType, extraTileUrls: List<Pair<String, String>> = emptyList()): String {
        val sb = StringBuilder()
        sb.append("""{"version":8,"name":"${type.name}","sources":{""")

        val primaryUrl = if (type.isServer) serverTileUrl(type) else primaryTileUrl(type)
        val primaryTiles = tilesJson(expandTiles(primaryUrl))
        sb.append(
            """"basemap":{"type":"raster","tiles":[$primaryTiles],"tileSize":256,"minzoom":0,"maxzoom":18,"scheme":"xyz"}"""
        )

        val overlay = overlayTileUrl(type)
        if (overlay != null) {
            val overlayTiles = tilesJson(expandTiles(overlay))
            sb.append(
                ""","roadnet":{"type":"raster","tiles":[$overlayTiles],"tileSize":256,"minzoom":0,"maxzoom":18,"scheme":"xyz"}"""
            )
        }

        extraTileUrls.forEach { (id, url) ->
            sb.append(""","$id":{"type":"raster","tiles":["$url"],"tileSize":256,"minzoom":0,"maxzoom":18}""")
        }

        sb.append("""},"layers":[""")
        // 背景
        sb.append("""{"id":"background","type":"background","paint":{"background-color":"#0E1116"}}""")
        // 主底图
        sb.append(""",{"id":"basemap-layer","type":"raster","source":"basemap","paint":{"raster-opacity":1.0,"raster-fade-duration":150}}""")
        if (overlay != null) {
            sb.append(""",{"id":"roadnet-layer","type":"raster","source":"roadnet","paint":{"raster-opacity":0.95}}""")
        }
        extraTileUrls.forEachIndexed { i, (id, _) ->
            sb.append(""",{"id":"extra-$i","type":"raster","source":"$id","paint":{"raster-opacity":0.9}}""")
        }
        sb.append("]}")

        // 字体字形与精灵（矢量标注需要），使用 MapLibre 官方 CDN
        val json = sb.toString()
        return json.replaceFirst("\"version\":8", "\"version\":8,\"glyphs\":\"https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf\"")
    }
}
