package com.ghostnexora.vpn.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.VpnProfile

/**
 * Base de datos Room principal de Ghost Nexora VPN.
 *
 * Versión: 3
 * Entidades: VpnProfile, LogEntry
 *
 * Patrón Singleton para evitar múltiples instancias concurrentes.
 */
@Database(
    entities = [VpnProfile::class, LogEntry::class],
    version = 3,
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
                .addMigrations(MIGRATION_2_3)
                // The UI reads logs in the main process while GhostVpnService
                // writes them from :vpn. This keeps Flow observers live across
                // that process boundary.
                .enableMultiInstanceInvalidation()
                .fallbackToDestructiveMigration()
                .build()

        /**
         * Conserva todos los perfiles 1.0.36 y activa TLS estricto como valor
         * seguro hasta que el usuario elija compatibilidad SNI por perfil.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE vpn_profiles " +
                        "ADD COLUMN tls_verification_mode TEXT NOT NULL DEFAULT 'strict'"
                )
            }
        }
    }
}
