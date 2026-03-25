package com.example.smartwificonnect.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class SavedWifiRecordDraft(
    val baseUrl: String,
    val ocrText: String,
    val ssid: String,
    val password: String,
    val sourceFormat: String,
    val confidence: Double?,
)

data class SavedWifiRecord(
    val id: Long,
    val baseUrl: String,
    val ocrText: String,
    val ssid: String,
    val password: String,
    val sourceFormat: String,
    val confidence: Double?,
    val createdAtMillis: Long,
)

class WifiHistoryDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_WIFI_SCAN_HISTORY (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_BASE_URL TEXT NOT NULL,
                $COLUMN_OCR_TEXT TEXT NOT NULL,
                $COLUMN_SSID TEXT NOT NULL,
                $COLUMN_PASSWORD TEXT NOT NULL,
                $COLUMN_SOURCE_FORMAT TEXT NOT NULL,
                $COLUMN_CONFIDENCE REAL,
                $COLUMN_CREATED_AT_MILLIS INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WIFI_SCAN_HISTORY")
        onCreate(db)
    }

    fun insert(record: SavedWifiRecordDraft): SavedWifiRecord {
        val createdAtMillis = System.currentTimeMillis()
        val values = ContentValues().apply {
            put(COLUMN_BASE_URL, record.baseUrl)
            put(COLUMN_OCR_TEXT, record.ocrText)
            put(COLUMN_SSID, record.ssid)
            put(COLUMN_PASSWORD, record.password)
            put(COLUMN_SOURCE_FORMAT, record.sourceFormat)
            if (record.confidence == null) {
                putNull(COLUMN_CONFIDENCE)
            } else {
                put(COLUMN_CONFIDENCE, record.confidence)
            }
            put(COLUMN_CREATED_AT_MILLIS, createdAtMillis)
        }

        val id = writableDatabase.insertOrThrow(TABLE_WIFI_SCAN_HISTORY, null, values)
        return SavedWifiRecord(
            id = id,
            baseUrl = record.baseUrl,
            ocrText = record.ocrText,
            ssid = record.ssid,
            password = record.password,
            sourceFormat = record.sourceFormat,
            confidence = record.confidence,
            createdAtMillis = createdAtMillis,
        )
    }

    fun getLatest(): SavedWifiRecord? {
        val sortOrder = "$COLUMN_CREATED_AT_MILLIS DESC, $COLUMN_ID DESC"
        return readableDatabase.query(
            TABLE_WIFI_SCAN_HISTORY,
            ALL_COLUMNS,
            null,
            null,
            null,
            null,
            sortOrder,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }

            val confidenceIndex = cursor.getColumnIndexOrThrow(COLUMN_CONFIDENCE)
            SavedWifiRecord(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                baseUrl = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BASE_URL)),
                ocrText = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_OCR_TEXT)),
                ssid = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SSID)),
                password = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PASSWORD)),
                sourceFormat = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOURCE_FORMAT)),
                confidence = if (cursor.isNull(confidenceIndex)) null else cursor.getDouble(confidenceIndex),
                createdAtMillis = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT_MILLIS)),
            )
        }
    }

    companion object {
        private const val DATABASE_NAME = "smart_wifi_connect.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_WIFI_SCAN_HISTORY = "wifi_scan_history"
        private const val COLUMN_ID = "id"
        private const val COLUMN_BASE_URL = "base_url"
        private const val COLUMN_OCR_TEXT = "ocr_text"
        private const val COLUMN_SSID = "ssid"
        private const val COLUMN_PASSWORD = "password"
        private const val COLUMN_SOURCE_FORMAT = "source_format"
        private const val COLUMN_CONFIDENCE = "confidence"
        private const val COLUMN_CREATED_AT_MILLIS = "created_at_millis"

        private val ALL_COLUMNS = arrayOf(
            COLUMN_ID,
            COLUMN_BASE_URL,
            COLUMN_OCR_TEXT,
            COLUMN_SSID,
            COLUMN_PASSWORD,
            COLUMN_SOURCE_FORMAT,
            COLUMN_CONFIDENCE,
            COLUMN_CREATED_AT_MILLIS,
        )
    }
}