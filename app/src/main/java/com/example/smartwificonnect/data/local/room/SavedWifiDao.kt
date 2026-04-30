package com.example.smartwificonnect.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete

@Dao
interface SavedWifiDao {
    /**
     * Insert a new WiFi record into the database.
     */
    @Insert
    suspend fun insert(entity: SavedWifiEntity): Long

    /**
     * Get the most recent WiFi record.
     */
    @Query("SELECT * FROM wifi_scan_history ORDER BY createdAtMillis DESC, id DESC LIMIT 1")
    suspend fun getLatest(): SavedWifiEntity?

    /**
     * Get all WiFi records, ordered by most recent first, with a limit.
     */
    @Query("SELECT * FROM wifi_scan_history ORDER BY createdAtMillis DESC, id DESC LIMIT :limit")
    suspend fun getAll(limit: Int = 50): List<SavedWifiEntity>

    /**
     * Delete a WiFi record by ID.
     */
    @Query("DELETE FROM wifi_scan_history WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    /**
     * Delete all WiFi records.
     */
    @Query("DELETE FROM wifi_scan_history")
    suspend fun deleteAll(): Int

    /**
     * Get a specific WiFi record by ID.
     */
    @Query("SELECT * FROM wifi_scan_history WHERE id = :id")
    suspend fun getById(id: Long): SavedWifiEntity?
}
