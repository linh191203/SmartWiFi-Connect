package com.example.smartwificonnect.data.local.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [SavedWifiEntity::class], version = 1, exportSchema = false)
@TypeConverters(ListStringConverter::class)
abstract class SmartWiFiDatabase : RoomDatabase() {
    abstract fun savedWifiDao(): SavedWifiDao

    companion object {
        @Volatile
        private var instance: SmartWiFiDatabase? = null

        fun getInstance(context: Context): SmartWiFiDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    SmartWiFiDatabase::class.java,
                    "smart_wifi_connect_room.db"
                )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
