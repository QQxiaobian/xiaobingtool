package com.kunming.powerline.data.parser

import com.kunming.powerline.util.VoltagePalette

/**
 * 统一的导入中间模型：所有格式先解析成 ImportLine，再统一校验、入库
 */
data class ImportTower(
    val towerNumber: String,
    val lon: Double,
    val lat: Double,
    val elevation: Double? = null,
    val type: String? = null,
    val circuitCount: Int = 1,
    val circuitNo: Int = 1,
    val remark: String? = null,
    /** 原始行号，用于错误定位 */
    val sourceRow: Int = -1
)

data class ImportLine(
    val name: String,
    val voltageLevel: Int,
    val startStation: String? = null,
    val endStation: String? = null,
    val inServiceDate: String? = null,
    val remark: String? = null,
    val towers: MutableList<ImportTower> = mutableListOf(),
    /** 线路几何（当只提供线而不提供杆塔时使用） */
    val lineCoords: MutableList<Pair<Double, Double>> = mutableListOf()
)

data class ImportError(
    val row: Int,
    val message: String,
    val rawContent: String = ""
)

data class ParseResult(
    val lines: List<ImportLine>,
    val errors: List<ImportError> = emptyList(),
    val warnings: List<String> = emptyList(),
    val format: String = ""
) {
    val towerCount: Int get() = lines.sumOf { it.towers.size }
    val isEmpty: Boolean get() = lines.isEmpty()
}

/**
 * 统一校验规则（10.8）
 */
object ImportValidator {

    fun validate(lines: List<ImportLine>): Pair<List<ImportLine>, List<ImportError>> {
        val errors = mutableListOf<ImportError>()
        val validLines = mutableListOf<ImportLine>()

        lines.forEach { line ->
            if (line.name.isBlank()) {
                errors.add(ImportError(-1, "线路名称为空，已跳过"))
                return@forEach
            }
            if (line.voltageLevel !in VoltagePalette.levels) {
                errors.add(ImportError(-1, "线路【${line.name}】电压等级 ${line.voltageLevel}kV 非法（仅支持 500/220/110/35）"))
                return@forEach
            }
            val validTowers = mutableListOf<ImportTower>()
            val seen = HashMap<String, Int>()
            line.towers.forEach { t ->
                if (t.towerNumber.isBlank()) {
                    errors.add(ImportError(t.sourceRow, "【${line.name}】杆塔号为空"))
                    return@forEach
                }
                if (!com.kunming.powerline.util.GeoUtils.isValidChinaCoord(t.lon, t.lat)) {
                    errors.add(
                        ImportError(
                            t.sourceRow,
                            "【${line.name}】${t.towerNumber} 经纬度越界 (${t.lon}, ${t.lat})，应为 经度 73~136 / 纬度 3~54"
                        )
                    )
                    return@forEach
                }
                if (t.lon == 0.0 && t.lat == 0.0) {
                    errors.add(ImportError(t.sourceRow, "【${line.name}】${t.towerNumber} 经纬度缺失"))
                    return@forEach
                }
                if (seen.containsKey(t.towerNumber)) {
                    errors.add(ImportError(t.sourceRow, "【${line.name}】杆塔号重复：${t.towerNumber}（第 ${seen[t.towerNumber]} 行已存在）"))
                    return@forEach   // 重复行不重复入库
                }
                seen[t.towerNumber] = t.sourceRow
                validTowers.add(t)
            }
            // 空线路（创建待补录）允许导入；但若原文有杆塔却全部无效，则视为错误
            if (validTowers.isEmpty() && line.lineCoords.isEmpty() && line.towers.isNotEmpty()) {
                errors.add(ImportError(-1, "线路【${line.name}】无有效杆塔数据"))
                return@forEach
            }
            validLines.add(line.copy(towers = validTowers))
        }
        return validLines to errors
    }

    /**
     * 同塔多回识别：同线路不同杆塔号但经纬度差 < 0.0001° 视为同塔
     * 返回 (杆塔号 -> 共用组数量) 提示
     */
    fun detectSameTower(lines: List<ImportLine>): List<String> {
        val msgs = mutableListOf<String>()
        lines.forEach { l ->
            val groups = mutableListOf<MutableList<ImportTower>>()
            l.towers.forEach { t ->
                val g = groups.firstOrNull { it.first().let { f -> com.kunming.powerline.util.GeoUtils.nearlySameLocation(f.lon, f.lat, t.lon, t.lat) } }
                if (g != null) g.add(t) else groups.add(mutableListOf(t))
            }
            groups.filter { it.size > 1 }.forEach { g ->
                msgs.add("【${l.name}】检测到同塔多回：${g.joinToString("/") { it.towerNumber }}")
            }
        }
        return msgs
    }
}
