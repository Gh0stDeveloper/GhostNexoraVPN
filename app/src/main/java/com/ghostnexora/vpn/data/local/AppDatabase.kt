package com.ghostnexora.vpn.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.VpnProfile

/**
 * Base de datos Room principal de Ghost Nexora VPN.
 *
 * Versión: 2
 * Entidades: VpnProfile, LogEntry
 *
 * Patrón Singleton para evitar múltiples instancias concurrentes.
 */
@Database(
    entities = [VpnProfile::class, LogEntry::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun logDao(): LogDao

    companion object {
        private const val DB_NAME = "ghost_nexora.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DB_NAME
            )
                // En producción usar Migrations en lugar de destructive
                .fallbackToDestructiveMigration()
                .build()
    }
}
