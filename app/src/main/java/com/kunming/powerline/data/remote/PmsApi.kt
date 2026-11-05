package com.kunming.powerline.data.remote

import com.kunming.powerline.data.remote.model.ImportAckDto
import com.kunming.powerline.data.remote.model.PmsLedgerBundle
import com.kunming.powerline.data.remote.model.PmsLineDto
import com.kunming.powerline.data.remote.model.PmsTowerDto
import com.kunming.powerline.data.remote.model.PmsUnitsDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * PMS / 生产管理系统内网接口
 * BASE_URL 在 local.properties 中通过 PMS_BASE_URL 配置
 */
interface PmsApi {

    @GET("api/lines")
    suspend fun getLines(
        @Query("unit") unit: String? = null,
        @Query("voltage") voltage: Int? = null,
        @Query("keyword") keyword: String? = null
    ): List<PmsLineDto>

    @GET("api/towers")
    suspend fun getTowers(@Query("lineId") lineId: String): List<PmsTowerDto>

    @GET("api/units")
    suspend fun getUnits(): PmsUnitsDto

    /** 批量拉取台账（线路 + 杆塔 + 导地线 + 交叉跨越） */
    @GET("api/import/pms-ledger")
    suspend fun pullLedger(
        @Query("lineIds") lineIds: String,
        @Query("includeTowers") includeTowers: Boolean = true,
        @Query("includeCrossings") includeCrossings: Boolean = true,
        @Query("includeConductors") includeConductors: Boolean = true
    ): PmsLedgerBundle

    @POST("api/import/pms-ledger")
    suspend fun pushLedger(@Body bundle: PmsLedgerBundle): ImportAckDto
}
