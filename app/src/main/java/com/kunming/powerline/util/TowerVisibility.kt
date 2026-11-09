package com.kunming.powerline.util

import com.kunming.powerline.data.local.entity.LineEntity
import com.kunming.powerline.data.local.entity.TowerEntity

/**
 * 杆塔点图标的可见性规则（1.6.0）。
 *
 * 线路与电缆早已按电压等级筛选过滤，但杆塔此前始终全部绘制，
 * 导致关掉某个电压等级后线路消失、杆塔点却还留在图上。
 * 现在杆塔与线路使用同一套筛选：被关闭的等级不再画杆塔，画面只保留可见等级的线路。
 */
object TowerVisibility {

    /** 找不到所属线路时的兜底电压等级（与主界面默认色一致） */
    const val FALLBACK_VOLTAGE = 110

    fun voltageOf(lineMap: Map<String, LineEntity>, tower: TowerEntity): Int =
        lineMap[tower.lineId]?.voltageLevel ?: FALLBACK_VOLTAGE

    fun filter(
        towers: List<TowerEntity>,
        lines: List<LineEntity>,
        voltageFilter: Set<Int>
    ): List<TowerEntity> {
        if (voltageFilter.isEmpty()) return emptyList()
        val lineMap = lines.associateBy { it.id }
        return towers.filter { voltageOf(lineMap, it) in voltageFilter }
    }
}
