package com.example.smartwificonnect.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface WifiApiService {
    @GET("health")
    suspend fun getHealth(): HealthData

    @POST("api/v1/ocr/parse")
    suspend fun parseOcr(@Body request: OcrParseRequest): ApiEnvelope<ParsedWifiData>

    @POST("api/ai/validate")
    suspend fun validateAi(@Body request: AiValidateRequest): ApiEnvelope<AiValidateData>

    @POST("api/v1/ssid/fuzzy-match")
    suspend fun fuzzyMatchSsid(@Body request: SsidFuzzyMatchRequest): ApiEnvelope<SsidFuzzyMatchData>

    @POST("api/networks")
    suspend fun saveNetwork(@Body request: SaveNetworkRequest): ApiEnvelope<SaveNetworkResponse>

    @GET("api/networks")
    suspend fun getNetworks(
        @retrofit2.http.Query("page") page: Int = 1,
        @retrofit2.http.Query("limit") limit: Int = 20,
    ): ApiEnvelope<NetworkListData>
}
