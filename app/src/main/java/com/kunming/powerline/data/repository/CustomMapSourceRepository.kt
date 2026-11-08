package com.kunming.powerline.data.repository

import com.kunming.powerline.data.local.dao.MiscDao
import com.kunming.powerline.data.local.entity.OnlineMapEntity
import com.kunming.powerline.ui.map.CustomMapSource
import com.kunming.powerline.ui.map.CustomSourceSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自定义地图源仓库：增删改查 + 渲染规格转换。
 *
 * 只有 enabled 的源会进入 [specs]；其中 asBase=true 的源（最多一个）替代内置底图，
 * 其余启用源作为半透明叠加层。
 *
 * 注意：online_map 相关 DAO 方法定义在 MiscDao（与地图组 / 轨迹 / 离线包同组）。
 */
@Singleton
class CustomMapSourceRepository @Inject constructor(
    private val dao: MiscDao
) {
    val sources: Flow<List<OnlineMapEntity>> = dao.observeOnlineMaps()

    val specs: Flow<List<CustomSourceSpec>> = sources.map { list ->
        list.filter { it.enabled }.mapNotNull { e ->
            CustomMapSource.spec(
                id = e.id,
                name = e.name,
                template = e.tileUrl,
                subdomains = e.subdomains,
                tileSize = e.tileSize,
                minZoom = e.minZoom,
                maxZoom = e.maxZoom,
                scheme = if (e.isTms) "tms" else "xyz",
                opacity = e.opacity,
                asBase = e.asBase
            )
        }
    }

    suspend fun list(): List<OnlineMapEntity> =
        withContext(Dispatchers.IO) { dao.getOnlineMaps() }

    suspend fun save(e: OnlineMapEntity) =
        withContext(Dispatchers.IO) { dao.upsertOnlineMap(e) }

    suspend fun delete(id: String) =
        withContext(Dispatchers.IO) { dao.deleteOnlineMap(id) }

    suspend fun toggle(id: String, enabled: Boolean) =
        withContext(Dispatchers.IO) { dao.setOnlineMapEnabled(id, enabled) }

    /** id 为 null 表示取消自定义主底图，回到内置底图 */
    suspend fun setBase(id: String?) = withContext(Dispatchers.IO) {
        dao.clearBaseFlags()
        if (id != null) dao.setAsBase(id)
    }

    fun newId(): String = "CMS-" + UUID.randomUUID().toString().replace("-", "").take(10)
}
