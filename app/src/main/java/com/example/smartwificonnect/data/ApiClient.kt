package com.example.smartwificonnect.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ApiClient {
    private val serviceCache = ConcurrentHashMap<String, WifiApiService>()

    fun getService(rawBaseUrl: String, enableDebugLogs: Boolean): WifiApiService {
        val normalizedBaseUrl = normalizeBaseUrl(rawBaseUrl)
        return serviceCache.getOrPut(normalizedBaseUrl) {
            createRetrofit(normalizedBaseUrl, enableDebugLogs).create(WifiApiService::class.java)
        }
    }

    private fun createRetrofit(baseUrl: String, enableDebugLogs: Boolean): Retrofit {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (enableDebugLogs) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            clientBuilder.addInterceptor(logging)
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(clientBuilder.build())
            .build()
    }

    private fun normalizeBaseUrl(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return "http://10.0.2.2:8080/"
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
