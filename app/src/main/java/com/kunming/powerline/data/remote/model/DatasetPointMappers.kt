package com.kunming.powerline.data.remote.model

import com.kunming.powerline.data.local.entity.CableEntity
import com.kunming.powerline.data.local.entity.CameraEntity
import com.kunming.powerline.data.local.entity.CrossingEntity
import com.kunming.powerline.data.local.entity.CustomOverlayEntity
import com.kunming.powerline.data.local.entity.DangerLayerEntity
import com.kunming.powerline.data.local.entity.DangerPointEntity
import com.kunming.powerline.data.local.entity.FavoriteEntity
import com.kunming.powerline.data.local.entity.GuardianEntity
import com.kunming.powerline.data.local.entity.TakeoffPointEntity

/**
 * 点位类实体 ↔ 数据集 DTO（1.6.4）
 *
 * 单独放一个纯映射文件，让 [com.kunming.powerline.data.repository.DataSyncRepository]
 * 只关心「取数据 / 写数据」，字段对应关系集中在一处，改字段不容易漏。
 */
object DatasetPointMappers {

    // ---------------- 实体 → DTO ----------------

    fun favorite(e: FavoriteEntity) = DatasetFavoriteDto(
        id = e.id, name = e.name, category = e.category,
        lon = e.lon, lat = e.lat, note = e.note, createdAt = e.createdAt
    )

    fun takeoff(e: TakeoffPointEntity) = DatasetTakeoffDto(
        id = e.id, name = e.name, lon = e.lon, lat = e.lat,
        altitude = e.altitude, note = e.note
    )

    fun camera(e: CameraEntity) = DatasetCameraDto(
        id = e.id, name = e.name, lon = e.lon, lat = e.lat,
        rtspUrl = e.rtspUrl, status = e.status, remark = e.remark
    )

    fun guardian(e: GuardianEntity) = DatasetGuardianDto(
        id = e.id, name = e.name, phone = e.phone, section = e.responsibilitySection,
        lon = e.lastLon, lat = e.lastLat, updatedAt = e.updatedAt
    )

    fun dangerLayer(e: DangerLayerEntity) = DatasetDangerLayerDto(
        id = e.id, name = e.name, fileName = e.fileName, lineName = e.lineName,
        visible = e.visible, remark = e.remark, createdAt = e.createdAt
    )

    fun danger(e: DangerPointEntity, lineName: String?) = DatasetDangerDto(
        id = e.id, layerId = e.layerId, lineId = e.lineId, lineName = lineName,
        type = e.type, level = e.level, measure = e.measure,
        lon = e.lon, lat = e.lat, towerNumber = e.towerNumber, remark = e.remark,
        seqNo = e.seqNo, towerSpan = e.towerSpan, measuredAt = e.measuredAt,
        heightM = e.heightM, spanM = e.spanM, distSmallTowerM = e.distSmallTowerM,
        groundType = e.groundType, horizM = e.horizM, vertM = e.vertM,
        clearanceM = e.clearanceM, defectLevel = e.defectLevel,
        safeDistanceM = e.safeDistanceM, vegetationType = e.vegetationType
    )

    fun crossing(e: CrossingEntity, lineName: String?) = DatasetCrossingDto(
        id = e.id, lineId = e.lineId, lineName = lineName,
        crossType = e.crossType, crossObject = e.crossObject, distance = e.distance,
        lon = e.lon, lat = e.lat, towerNumber = e.towerNumber, remark = e.remark
    )

    fun cable(e: CableEntity) = DatasetCableDto(
        id = e.id, name = e.name, voltageLevel = e.voltageLevel,
        geometryGeojson = e.geometryGeojson, startNode = e.startNode,
        endNode = e.endNode, lengthMeter = e.lengthMeter, remark = e.remark
    )

    fun overlay(e: CustomOverlayEntity) = DatasetOverlayDto(
        id = e.id, type = e.type, name = e.name, geometryGeojson = e.geometryGeojson,
        color = e.color, textSize = e.textSize, extra = e.extra, createdAt = e.createdAt
    )

    // ---------------- DTO → 实体 ----------------

    fun favorite(d: DatasetFavoriteDto) = FavoriteEntity(
        id = d.id, name = d.name.ifBlank { "收藏点" }, category = d.category,
        lon = d.lon, lat = d.lat, note = d.note,
        createdAt = d.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
    )

    fun takeoff(d: DatasetTakeoffDto) = TakeoffPointEntity(
        id = d.id, name = d.name.ifBlank { "起飞点" },
        lon = d.lon, lat = d.lat, altitude = d.altitude, note = d.note
    )

    fun camera(d: DatasetCameraDto) = CameraEntity(
        id = d.id, name = d.name, lon = d.lon, lat = d.lat,
        rtspUrl = d.rtspUrl, status = d.status, remark = d.remark
    )

    fun guardian(d: DatasetGuardianDto) = GuardianEntity(
        id = d.id, name = d.name.ifBlank { "护线员" }, phone = d.phone,
        responsibilitySection = d.section, lastLon = d.lon, lastLat = d.lat,
        updatedAt = d.updatedAt
    )

    fun dangerLayer(d: DatasetDangerLayerDto) = DangerLayerEntity(
        id = d.id, name = d.name.ifBlank { "危险点图层" }, fileName = d.fileName,
        lineName = d.lineName, visible = d.visible,
        featureCount = 0, remark = d.remark,
        createdAt = d.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
    )

    fun danger(d: DatasetDangerDto, lineId: String?) = DangerPointEntity(
        id = d.id, layerId = d.layerId, lineId = lineId ?: d.lineId,
        type = d.type.ifBlank { d.groundType ?: "危险点" },
        level = d.level, measure = d.measure,
        lon = d.lon, lat = d.lat, towerNumber = d.towerNumber, remark = d.remark,
        seqNo = d.seqNo, towerSpan = d.towerSpan, measuredAt = d.measuredAt,
        heightM = d.heightM, spanM = d.spanM, distSmallTowerM = d.distSmallTowerM,
        groundType = d.groundType, horizM = d.horizM, vertM = d.vertM,
        clearanceM = d.clearanceM, defectLevel = d.defectLevel,
        safeDistanceM = d.safeDistanceM, vegetationType = d.vegetationType
    )

    fun crossing(d: DatasetCrossingDto, lineId: String?) = CrossingEntity(
        id = d.id, lineId = lineId ?: d.lineId,
        crossType = d.crossType.ifBlank { "其他" }, crossObject = d.crossObject,
        distance = d.distance, lon = d.lon, lat = d.lat,
        towerNumber = d.towerNumber, remark = d.remark
    )

    fun cable(d: DatasetCableDto) = CableEntity(
        id = d.id, name = d.name.ifBlank { "电缆段" }, voltageLevel = d.voltageLevel,
        geometryGeojson = d.geometryGeojson, startNode = d.startNode,
        endNode = d.endNode, lengthMeter = d.lengthMeter, remark = d.remark
    )

    fun overlay(d: DatasetOverlayDto) = CustomOverlayEntity(
        id = d.id, type = d.type, name = d.name, geometryGeojson = d.geometryGeojson,
        color = d.color, textSize = d.textSize, extra = d.extra,
        createdAt = d.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
    )
}
