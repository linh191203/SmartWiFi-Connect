package com.example.smartwificonnect.data.local.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson

@Entity(tableName = "wifi_scan_history")
data class SavedWifiEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val baseUrl: String,
    val ocrText: String,
    val ssid: String,
    val password: String,
    val sourceFormat: String,
    val confidence: Double?,
    val aiConfidence: Double?,
    val aiSuggestion: String,
    val aiRecommendation: String,
    val aiShouldAutoConnect: Boolean,
    val aiFlags: List<String>,
    val fuzzyBestMatch: String?,
    val fuzzyScore: Double?,
    val createdAtMillis: Long,
)

// Type converter for List<String> (aiFlags)
class ListStringConverter {
    private val gson = Gson()

    @TypeConverter
    fun fromString(value: String?): List<String> {
        return if (value.isNullOrEmpty()) emptyList()
        else gson.fromJson(value, Array<String>::class.java).toList()
    }

    @TypeConverter
    fun fromList(list: List<String>?): String {
        return if (list.isNullOrEmpty()) "[]" else gson.toJson(list)
    }
}
