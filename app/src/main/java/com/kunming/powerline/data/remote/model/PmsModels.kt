package com.kunming.powerline.data.remote.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * PMS / 生产管理系统台账模型
 */
@JsonClass(generateAdapter = true)
data class PmsLineDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "lineName") val lineName: String = "",
    @Json(name = "voltageLevel") val voltageLevel: String = "",
    @Json(name = "startStation") val startStation: String? = null,
    @Json(name = "endStation") val endStation: String? = null,
    @Json(name = "inServiceDate") val inServiceDate: String? = null,
    @Json(name = "unitName") val unitName: String? = null,
    @Json(name = "lineLength") val lineLength: Double? = null,
    @Json(name = "remark") val remark: String? = null
)

@JsonClass(generateAdapter = true)
data class PmsTowerDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "lineId") val lineId: String? = null,
    @Json(name = "lineName") val lineName: String? = null,
    @Json(name = "towerNumber") val towerNumber: String = "",
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "elevation") val elevation: Double? = null,
    @Json(name = "towerType") val towerType: String? = null,
    @Json(name = "circuitCount") val circuitCount: Int? = 1,
    @Json(name = "circuitNo") val circuitNo: Int? = 1
)

@JsonClass(generateAdapter = true)
data class PmsCrossingDto(
    @Json(name = "lineId") val lineId: String? = null,
    @Json(name = "lineName") val lineName: String? = null,
    @Json(name = "crossType") val crossType: String = "",
    @Json(name = "crossObject") val crossObject: String? = null,
    @Json(name = "distance") val distance: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "towerNumber") val towerNumber: String? = null
)

@JsonClass(generateAdapter = true)
data class PmsConductorDto(
    @Json(name = "lineId") val lineId: String? = null,
    @Json(name = "lineName") val lineName: String? = null,
    @Json(name = "phase") val phase: String? = null,
    @Json(name = "model") val model: String? = null,
    @Json(name = "sectionArea") val sectionArea: Double? = null,
    @Json(name = "splitCount") val splitCount: Int? = null
)

@JsonClass(generateAdapter = true)
data class PmsLedgerBundle(
    @Json(name = "lines") val lines: List<PmsLineDto> = emptyList(),
    @Json(name = "towers") val towers: List<PmsTowerDto> = emptyList(),
    @Json(name = "crossings") val crossings: List<PmsCrossingDto> = emptyList(),
    @Json(name = "conductors") val conductors: List<PmsConductorDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class PmsUnitsDto(
    @Json(name = "units") val units: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ImportAckDto(
    @Json(name = "success") val success: Boolean = true,
    @Json(name = "message") val message: String? = null,
    @Json(name = "count") val count: Int = 0
)
