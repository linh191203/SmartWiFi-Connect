package com.smartwificonnect.data

import com.smartwificonnect.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ApiClient {
    private val serviceCache = ConcurrentHashMap<String, WifiApiService>()

    fun clearServiceCache() {
        serviceCache.clear()
    }

    fun getService(rawBaseUrl: String, enableDebugLogs: Boolean): WifiApiService {
        val normalizedBaseUrl = normalizeBaseUrl(rawBaseUrl)
        val authToken = BuildConfig.API_AUTH_TOKEN.trim()
        val cacheKey = if (authToken.isEmpty()) normalizedBaseUrl else "$normalizedBaseUrl|$authToken"
        return serviceCache.getOrPut(cacheKey) {
            createRetrofit(normalizedBaseUrl, authToken, enableDebugLogs).create(WifiApiService::class.java)
        }
    }

    private fun createRetrofit(baseUrl: String, authToken: String, enableDebugLogs: Boolean): Retrofit {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (authToken.isNotEmpty()) {
            clientBuilder.addInterceptor { chain ->
                val authenticatedRequest: Request = chain.request()
                    .newBuilder()
                    .header("Authorization", "Bearer $authToken")
                    .build()
                chain.proceed(authenticatedRequest)
            }
        }

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
        val configured = value.trim().ifEmpty { BuildConfig.API_BASE_URL.trim() }
        val fallback = "https://api.smartwifi.example.com/"
        val normalized = configured.ifEmpty { fallback }
        return if (normalized.endsWith("/")) normalized else "$normalized/"
    }
}
