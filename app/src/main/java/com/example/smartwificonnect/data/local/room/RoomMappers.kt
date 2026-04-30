package com.example.smartwificonnect.data.local.room

import com.example.smartwificonnect.data.local.PasswordCipher
import com.example.smartwificonnect.data.local.SavedWifiRecord
import com.example.smartwificonnect.data.local.SavedWifiRecordDraft

fun SavedWifiRecordDraft.toEntity(createdAtMillis: Long): SavedWifiEntity = SavedWifiEntity(
    baseUrl = baseUrl,
    ocrText = ocrText,
    ssid = ssid,
    password = PasswordCipher.encrypt(password),
    sourceFormat = sourceFormat,
    confidence = confidence,
    aiConfidence = aiConfidence,
    aiSuggestion = aiSuggestion,
    aiRecommendation = aiRecommendation,
    aiShouldAutoConnect = aiShouldAutoConnect,
    aiFlags = aiFlags,
    fuzzyBestMatch = fuzzyBestMatch,
    fuzzyScore = fuzzyScore,
    createdAtMillis = createdAtMillis,
)

fun SavedWifiEntity.toDomain(): SavedWifiRecord = SavedWifiRecord(
    id = id,
    baseUrl = baseUrl,
    ocrText = ocrText,
    ssid = ssid,
    password = PasswordCipher.decrypt(password),
    sourceFormat = sourceFormat,
    confidence = confidence,
    aiConfidence = aiConfidence,
    aiSuggestion = aiSuggestion,
    aiRecommendation = aiRecommendation,
    aiShouldAutoConnect = aiShouldAutoConnect,
    aiFlags = aiFlags,
    fuzzyBestMatch = fuzzyBestMatch,
    fuzzyScore = fuzzyScore,
    createdAtMillis = createdAtMillis,
)
