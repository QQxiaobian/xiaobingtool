package com.kunming.powerline.data.remote

import com.kunming.powerline.data.remote.model.DatasetBundleDto
import com.kunming.powerline.data.remote.model.DatasetMetaDto
import com.kunming.powerline.data.remote.model.ImportAckDto
import com.kunming.powerline.data.remote.model.PmsLedgerBundle
import com.kunming.powerline.data.remote.model.PmsLineDto
import com.kunming.powerline.data.remote.model.PmsTowerDto
import com.kunming.powerline.data.remote.model.PmsUnitsDto
import com.kunming.powerline.data.remote.model.ServerFileDto
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

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

    // ---------------- NAS 数据集（图层 / 线路数据下载与上传） ----------------

    /** 列出服务器上已有数据集（仅元数据，不含要素，传输量小） */
    @GET("api/datasets")
    suspend fun listDatasets(): List<DatasetMetaDto>

    /** 下载一个完整数据集 */
    @GET("api/datasets/{id}")
    suspend fun downloadDataset(@Path("id") id: String): DatasetBundleDto

    /** 上传 / 覆盖一个数据集（同 id 覆盖，version 由服务端递增） */
    @POST("api/datasets")
    suspend fun uploadDataset(@Body dataset: DatasetBundleDto): ImportAckDto

    @DELETE("api/datasets/{id}")
    suspend fun deleteDataset(@Path("id") id: String): ImportAckDto

    // ---------------- NAS 原始文件库（1.3.0：KML/KMZ/GeoJSON/GPX/SHP） ----------------

    /** 列出服务器 files 目录里可直接下载的原始文件（仅元数据） */
    @GET("api/files")
    suspend fun listServerFiles(): List<ServerFileDto>

    /** 流式下载原始文件字节；@Streaming 避免大文件一次性进内存 */
    @Streaming
    @GET("api/files/{id}/download")
    suspend fun downloadServerFile(@Path("id") id: String): ResponseBody

    /**
     * 上传原始文件。multipart 的 filename 用 ASCII 占位，
     * 真实（可能含中文）文件名放在 X-File-Name，UTF-8 URL 编码后传输。
     */
    @Multipart
    @POST("api/files")
    suspend fun uploadServerFile(
        @Part file: MultipartBody.Part,
        @Header("X-File-Name") encodedName: String
    ): ImportAckDto

    @DELETE("api/files/{id}")
    suspend fun deleteServerFile(@Path("id") id: String): ImportAckDto
}
