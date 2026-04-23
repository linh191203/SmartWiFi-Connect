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

data class SavedConnectedWifiDraft(
    val ssid: String,
    val password: String?,
    val security: String,
    val sourceFormat: String,
    val passwordSaved: Boolean,
)

data class SavedConnectedWifiRecord(
    val id: Long,
    val ssid: String,
    val password: String?,
    val security: String,
    val sourceFormat: String,
    val passwordSaved: Boolean,
    val lastConnectedAtMillis: Long,
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
        db.execSQL(
            """
            CREATE TABLE $TABLE_SAVED_WIFI_NETWORKS (
                $COLUMN_SAVED_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SAVED_SSID TEXT NOT NULL UNIQUE,
                $COLUMN_SAVED_PASSWORD TEXT,
                $COLUMN_SAVED_SECURITY TEXT NOT NULL,
                $COLUMN_SAVED_SOURCE_FORMAT TEXT NOT NULL,
                $COLUMN_SAVED_PASSWORD_SAVED INTEGER NOT NULL,
                $COLUMN_SAVED_LAST_CONNECTED_AT_MILLIS INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_SAVED_WIFI_NETWORKS (
                    $COLUMN_SAVED_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_SAVED_SSID TEXT NOT NULL UNIQUE,
                    $COLUMN_SAVED_PASSWORD TEXT,
                    $COLUMN_SAVED_SECURITY TEXT NOT NULL,
                    $COLUMN_SAVED_SOURCE_FORMAT TEXT NOT NULL,
                    $COLUMN_SAVED_PASSWORD_SAVED INTEGER NOT NULL,
                    $COLUMN_SAVED_LAST_CONNECTED_AT_MILLIS INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
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

    fun upsertSavedNetwork(record: SavedConnectedWifiDraft): SavedConnectedWifiRecord {
        val lastConnectedAtMillis = System.currentTimeMillis()
        val existingId = findSavedNetworkIdBySsid(record.ssid)
        val values = ContentValues().apply {
            put(COLUMN_SAVED_SSID, record.ssid)
            if (record.password == null) {
                putNull(COLUMN_SAVED_PASSWORD)
            } else {
                put(COLUMN_SAVED_PASSWORD, record.password)
            }
            put(COLUMN_SAVED_SECURITY, record.security)
            put(COLUMN_SAVED_SOURCE_FORMAT, record.sourceFormat)
            put(COLUMN_SAVED_PASSWORD_SAVED, if (record.passwordSaved) 1 else 0)
            put(COLUMN_SAVED_LAST_CONNECTED_AT_MILLIS, lastConnectedAtMillis)
        }

        val id = if (existingId == null) {
            writableDatabase.insertOrThrow(TABLE_SAVED_WIFI_NETWORKS, null, values)
        } else {
            writableDatabase.update(
                TABLE_SAVED_WIFI_NETWORKS,
                values,
                "$COLUMN_SAVED_ID = ?",
                arrayOf(existingId.toString()),
            )
            existingId
        }

        return SavedConnectedWifiRecord(
            id = id,
            ssid = record.ssid,
            password = record.password,
            security = record.security,
            sourceFormat = record.sourceFormat,
            passwordSaved = record.passwordSaved,
            lastConnectedAtMillis = lastConnectedAtMillis,
        )
    }

    fun getSavedNetworksCount(): Int {
        return readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_SAVED_WIFI_NETWORKS",
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) 0 else cursor.getInt(0)
        }
    }

    fun getLatestSavedNetwork(): SavedConnectedWifiRecord? {
        val sortOrder = "$COLUMN_SAVED_LAST_CONNECTED_AT_MILLIS DESC, $COLUMN_SAVED_ID DESC"
        return readableDatabase.query(
            TABLE_SAVED_WIFI_NETWORKS,
            SAVED_NETWORK_COLUMNS,
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

            val passwordIndex = cursor.getColumnIndexOrThrow(COLUMN_SAVED_PASSWORD)
            SavedConnectedWifiRecord(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SAVED_ID)),
                ssid = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SAVED_SSID)),
                password = if (cursor.isNull(passwordIndex)) null else cursor.getString(passwordIndex),
                security = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SAVED_SECURITY)),
                sourceFormat = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SAVED_SOURCE_FORMAT)),
                passwordSaved = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SAVED_PASSWORD_SAVED)) == 1,
                lastConnectedAtMillis = cursor.getLong(
                    cursor.getColumnIndexOrThrow(COLUMN_SAVED_LAST_CONNECTED_AT_MILLIS),
                ),
            )
        }
    }

    fun getSavedNetworks(): List<SavedConnectedWifiRecord> {
        val sortOrder = "$COLUMN_SAVED_LAST_CONNECTED_AT_MILLIS DESC, $COLUMN_SAVED_ID DESC"
        return readableDatabase.query(
            TABLE_SAVED_WIFI_NETWORKS,
            SAVED_NETWORK_COLUMNS,
            null,
            null,
            null,
            null,
            sortOrder,
            null,
        ).use { cursor ->
            val records = mutableListOf<SavedConnectedWifiRecord>()
            val passwordIndex = cursor.getColumnIndexOrThrow(COLUMN_SAVED_PASSWORD)
            while (cursor.moveToNext()) {
                records += SavedConnectedWifiRecord(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SAVED_ID)),
                    ssid = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SAVED_SSID)),
                    password = if (cursor.isNull(passwordIndex)) null else cursor.getString(passwordIndex),
                    security = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SAVED_SECURITY)),
                    sourceFormat = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SAVED_SOURCE_FORMAT)),
                    passwordSaved = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SAVED_PASSWORD_SAVED)) == 1,
                    lastConnectedAtMillis = cursor.getLong(
                        cursor.getColumnIndexOrThrow(COLUMN_SAVED_LAST_CONNECTED_AT_MILLIS),
                    ),
                )
            }
            records
        }
    }

    fun deleteSavedNetworkById(id: Long): Boolean {
        val rows = writableDatabase.delete(
            TABLE_SAVED_WIFI_NETWORKS,
            "$COLUMN_SAVED_ID = ?",
            arrayOf(id.toString()),
        )
        return rows > 0
    }

    fun clearSavedNetworks(): Int {
        return writableDatabase.delete(TABLE_SAVED_WIFI_NETWORKS, null, null)
    }

    private fun findSavedNetworkIdBySsid(ssid: String): Long? {
        return readableDatabase.query(
            TABLE_SAVED_WIFI_NETWORKS,
            arrayOf(COLUMN_SAVED_ID),
            "$COLUMN_SAVED_SSID = ?",
            arrayOf(ssid),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_SAVED_ID))
        }
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
        private const val COLUMN_CREATED_AT_MILLIS = "created_at_millis"

        private const val TABLE_SAVED_WIFI_NETWORKS = "saved_wifi_networks"
        private const val COLUMN_SAVED_ID = "id"
        private const val COLUMN_SAVED_SSID = "ssid"
        private const val COLUMN_SAVED_PASSWORD = "password"
        private const val COLUMN_SAVED_SECURITY = "security"
        private const val COLUMN_SAVED_SOURCE_FORMAT = "source_format"
        private const val COLUMN_SAVED_PASSWORD_SAVED = "password_saved"
        private const val COLUMN_SAVED_LAST_CONNECTED_AT_MILLIS = "last_connected_at_millis"

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

        private val SAVED_NETWORK_COLUMNS = arrayOf(
            COLUMN_SAVED_ID,
            COLUMN_SAVED_SSID,
            COLUMN_SAVED_PASSWORD,
            COLUMN_SAVED_SECURITY,
            COLUMN_SAVED_SOURCE_FORMAT,
            COLUMN_SAVED_PASSWORD_SAVED,
            COLUMN_SAVED_LAST_CONNECTED_AT_MILLIS,
        )
    }
}