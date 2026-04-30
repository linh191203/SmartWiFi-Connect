package com.example.smartwificonnect.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class SavedWifiRecordDraft(
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
)

data class SavedWifiRecord(
    val id: Long,
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
                $COLUMN_AI_CONFIDENCE REAL,
                $COLUMN_AI_SUGGESTION TEXT NOT NULL,
                $COLUMN_AI_RECOMMENDATION TEXT NOT NULL,
                $COLUMN_AI_SHOULD_AUTO_CONNECT INTEGER NOT NULL,
                $COLUMN_AI_FLAGS TEXT NOT NULL,
                $COLUMN_FUZZY_BEST_MATCH TEXT,
                $COLUMN_FUZZY_SCORE REAL,
                $COLUMN_CREATED_AT_MILLIS INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        // Speed up latest/history listing and SSID-based filtering.
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_wifi_history_created_at ON $TABLE_WIFI_SCAN_HISTORY($COLUMN_CREATED_AT_MILLIS DESC)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_wifi_history_ssid ON $TABLE_WIFI_SCAN_HISTORY($COLUMN_SSID)",
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
            put(COLUMN_PASSWORD, PasswordCipher.encrypt(record.password))
            put(COLUMN_SOURCE_FORMAT, record.sourceFormat)
            if (record.confidence == null) {
                putNull(COLUMN_CONFIDENCE)
            } else {
                put(COLUMN_CONFIDENCE, record.confidence)
            }
            if (record.aiConfidence == null) {
                putNull(COLUMN_AI_CONFIDENCE)
            } else {
                put(COLUMN_AI_CONFIDENCE, record.aiConfidence)
            }
            put(COLUMN_AI_SUGGESTION, record.aiSuggestion)
            put(COLUMN_AI_RECOMMENDATION, record.aiRecommendation)
            put(COLUMN_AI_SHOULD_AUTO_CONNECT, if (record.aiShouldAutoConnect) 1 else 0)
            put(COLUMN_AI_FLAGS, encodeFlags(record.aiFlags))
            if (record.fuzzyBestMatch == null) {
                putNull(COLUMN_FUZZY_BEST_MATCH)
            } else {
                put(COLUMN_FUZZY_BEST_MATCH, record.fuzzyBestMatch)
            }
            if (record.fuzzyScore == null) {
                putNull(COLUMN_FUZZY_SCORE)
            } else {
                put(COLUMN_FUZZY_SCORE, record.fuzzyScore)
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
            aiConfidence = record.aiConfidence,
            aiSuggestion = record.aiSuggestion,
            aiRecommendation = record.aiRecommendation,
            aiShouldAutoConnect = record.aiShouldAutoConnect,
            aiFlags = record.aiFlags,
            fuzzyBestMatch = record.fuzzyBestMatch,
            fuzzyScore = record.fuzzyScore,
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

            cursor.toSavedWifiRecord()
        }
    }

    fun getAll(limit: Int = 50): List<SavedWifiRecord> {
        val sortOrder = "$COLUMN_CREATED_AT_MILLIS DESC, $COLUMN_ID DESC"
        return readableDatabase.query(
            TABLE_WIFI_SCAN_HISTORY,
            ALL_COLUMNS,
            null,
            null,
            null,
            null,
            sortOrder,
            limit.coerceAtLeast(1).toString(),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.toSavedWifiRecord())
                }
            }
        }
    }

    fun deleteById(id: Long): Boolean {
        return writableDatabase.delete(
            TABLE_WIFI_SCAN_HISTORY,
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
        ) > 0
    }

    fun deleteAll(): Int {
        return writableDatabase.delete(TABLE_WIFI_SCAN_HISTORY, null, null)
    }

    private fun Cursor.toSavedWifiRecord(): SavedWifiRecord {
        val confidenceIndex = getColumnIndexOrThrow(COLUMN_CONFIDENCE)
        val aiConfidenceIndex = getColumnIndexOrThrow(COLUMN_AI_CONFIDENCE)
        val fuzzyBestMatchIndex = getColumnIndexOrThrow(COLUMN_FUZZY_BEST_MATCH)
        val fuzzyScoreIndex = getColumnIndexOrThrow(COLUMN_FUZZY_SCORE)
        return SavedWifiRecord(
            id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
            baseUrl = getString(getColumnIndexOrThrow(COLUMN_BASE_URL)),
            ocrText = getString(getColumnIndexOrThrow(COLUMN_OCR_TEXT)),
            ssid = getString(getColumnIndexOrThrow(COLUMN_SSID)),
            password = PasswordCipher.decrypt(getString(getColumnIndexOrThrow(COLUMN_PASSWORD))),
            sourceFormat = getString(getColumnIndexOrThrow(COLUMN_SOURCE_FORMAT)),
            confidence = if (isNull(confidenceIndex)) null else getDouble(confidenceIndex),
            aiConfidence = if (isNull(aiConfidenceIndex)) null else getDouble(aiConfidenceIndex),
            aiSuggestion = getString(getColumnIndexOrThrow(COLUMN_AI_SUGGESTION)),
            aiRecommendation = getString(getColumnIndexOrThrow(COLUMN_AI_RECOMMENDATION)),
            aiShouldAutoConnect = getInt(getColumnIndexOrThrow(COLUMN_AI_SHOULD_AUTO_CONNECT)) == 1,
            aiFlags = decodeFlags(getString(getColumnIndexOrThrow(COLUMN_AI_FLAGS))),
            fuzzyBestMatch = if (isNull(fuzzyBestMatchIndex)) null else getString(fuzzyBestMatchIndex),
            fuzzyScore = if (isNull(fuzzyScoreIndex)) null else getDouble(fuzzyScoreIndex),
            createdAtMillis = getLong(getColumnIndexOrThrow(COLUMN_CREATED_AT_MILLIS)),
        )
    }

    companion object {
        private const val DATABASE_NAME = "smart_wifi_connect.db"
        private const val DATABASE_VERSION = 2

        private const val TABLE_WIFI_SCAN_HISTORY = "wifi_scan_history"
        private const val COLUMN_ID = "id"
        private const val COLUMN_BASE_URL = "base_url"
        private const val COLUMN_OCR_TEXT = "ocr_text"
        private const val COLUMN_SSID = "ssid"
        private const val COLUMN_PASSWORD = "password"
        private const val COLUMN_SOURCE_FORMAT = "source_format"
        private const val COLUMN_CONFIDENCE = "confidence"
        private const val COLUMN_AI_CONFIDENCE = "ai_confidence"
        private const val COLUMN_AI_SUGGESTION = "ai_suggestion"
        private const val COLUMN_AI_RECOMMENDATION = "ai_recommendation"
        private const val COLUMN_AI_SHOULD_AUTO_CONNECT = "ai_should_auto_connect"
        private const val COLUMN_AI_FLAGS = "ai_flags"
        private const val COLUMN_FUZZY_BEST_MATCH = "fuzzy_best_match"
        private const val COLUMN_FUZZY_SCORE = "fuzzy_score"
        private const val COLUMN_CREATED_AT_MILLIS = "created_at_millis"

        private val ALL_COLUMNS = arrayOf(
            COLUMN_ID,
            COLUMN_BASE_URL,
            COLUMN_OCR_TEXT,
            COLUMN_SSID,
            COLUMN_PASSWORD,
            COLUMN_SOURCE_FORMAT,
            COLUMN_CONFIDENCE,
            COLUMN_AI_CONFIDENCE,
            COLUMN_AI_SUGGESTION,
            COLUMN_AI_RECOMMENDATION,
            COLUMN_AI_SHOULD_AUTO_CONNECT,
            COLUMN_AI_FLAGS,
            COLUMN_FUZZY_BEST_MATCH,
            COLUMN_FUZZY_SCORE,
            COLUMN_CREATED_AT_MILLIS,
        )

        private fun encodeFlags(flags: List<String>): String = flags.joinToString(separator = "|")

        private fun decodeFlags(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split("|").mapNotNull { value ->
                val trimmed = value.trim()
                if (trimmed.isBlank()) null else trimmed
            }
        }
    }
}
