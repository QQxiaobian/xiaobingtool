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
    onToast: (String) -> Unit
) {
    // home / template / create / towerDisplay / excel / pms / geo / delete / batches
    var route by remember { mutableStateOf("home") }
    val backToHome = { route = "home" }

    when (route) {
        "home" -> ImportHomeScreen(onBack = onExit, onPick = { route = it })

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
            onImported = { result, source ->
                onToast(vm.saveImportSync(result, "geo", source))
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
