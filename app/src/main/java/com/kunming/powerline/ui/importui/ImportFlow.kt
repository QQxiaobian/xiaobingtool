package com.kunming.powerline.ui.importui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kunming.powerline.ui.MapViewModel

/**
 * 创建 / 导入线路 —— 单一入口容器
 *
 * 采用「栈式路由」而非 Navigation-Compose，原因是该模块为一次性流程，
 * 且需与地图页共享 MapViewModel（导入后立即刷新图层）。返回键逐级回退。
 */
@Composable
fun ImportFlow(
    vm: MapViewModel,
    onExit: () -> Unit,
    onToast: (String) -> Unit,
    onFocus: (Double, Double, Double) -> Unit = { _, _, _ -> }
) {
    // home / template / create / towerDisplay / excel / pms / geo / delete / batches / lines / thematic
    var route by remember { mutableStateOf("home") }
    // 从"专题图层"页进入导入时，默认选中专题模式
    var geoStartThematic by remember { mutableStateOf(false) }
    val backToHome = { route = "home" }

    when (route) {
        "home" -> ImportHomeScreen(onBack = onExit, onPick = { route = it })

        // 线路列表 / 管理编辑（与地图图层入口共用同一页面）
        "lines" -> com.kunming.powerline.ui.layermanage.LineListScreen(
            vm = vm,
            onBack = backToHome,
            onToast = onToast,
            onFocus = onFocus,
            onOpenImport = backToHome
        )

        // 专题图层管理（巡视点 / 区域范围 / 通道等）
        "thematic" -> com.kunming.powerline.ui.layermanage.ThematicLayerScreen(
            vm = vm,
            onBack = backToHome,
            onToast = onToast,
            onFocus = onFocus,
            onImport = {
                geoStartThematic = true
                route = "geo"
            },
            onDeleteAll = {
                vm.deleteAllThematic()
                onToast("已清空全部专题图层")
            }
        )

        "template" -> TemplateScreen(generator = vm.templateGenerator, onBack = backToHome)

        "create" -> CreateLineScreen(
            lineRepo = vm.lineRepository,
            onBack = backToHome,
            onCreated = { line ->
                onToast("已创建线路「${line.name}」")
                backToHome()
            }
        )

        "towerDisplay" -> TowerDisplayOptimizerScreen(vm = vm, onBack = backToHome, onToast = onToast)

        "excel" -> ExcelImportScreen(
            excelParser = vm.excelParser,
            lineRepo = vm.lineRepository,
            onBack = backToHome,
            onImported = { result, source ->
                onToast(vm.saveImportSync(result, "excel", source))
                backToHome()
            }
        )

        "pms" -> PmsImportScreen(
            pmsRepository = vm.pmsRepository,
            onBack = backToHome,
            onImported = { batch ->
                onToast("PMS 导入完成：${batch.lineIds.split(',').count { it.isNotBlank() }} 条线路 / ${batch.towerCount} 基杆塔")
                backToHome()
            }
        )

        "geo" -> GeoImportScreen(
            parser = vm.geoParser,
            onBack = backToHome,
            startThematic = geoStartThematic,
            onImported = { result, source ->
                onToast(vm.saveImportSync(result, "geo", source))
                geoStartThematic = false
                backToHome()
            },
            onImportedThematic = { result, _, fileName ->
                onToast(vm.importThematicSync(result, fileName))
                geoStartThematic = false
                backToHome()
            }
        )

        "delete" -> DeleteAllScreen(
            lineCount = vm.stat.value.first,
            towerCount = vm.towers.value.size,
            onBack = backToHome,
            onConfirm = {
                vm.deleteAllLines()
                onToast("已清空全部线路与杆塔数据")
                backToHome()
            }
        )

        "batches" -> BatchListScreen(
            batches = vm.batches.value,
            onBack = backToHome,
            onRollback = { id -> vm.rollbackBatch(id) }
        )
    }
}
