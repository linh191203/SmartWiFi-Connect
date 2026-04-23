package com.example.smartwificonnect.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface WifiApiService {
    @GET("health")
    suspend fun getHealth(): HealthData

    @POST("api/v1/ocr/parse")
    suspend fun parseOcr(@Body request: OcrParseRequest): ApiEnvelope<ParsedWifiData>
}
