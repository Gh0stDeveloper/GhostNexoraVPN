package com.ghostnexora.vpn.data.local

import androidx.room.*
import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.LogLevel
import kotlinx.coroutines.flow.Flow

/**
 * DAO para lectura y escritura de entradas de log.
 */
@Dao
interface LogDao {

    // ── Queries ───────────────────────────────────────────────────────────

    /** Todos los logs, más recientes primero */
    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC, id DESC")
    fun getAllLogs(): Flow<List<LogEntry>>

    /** Logs de un perfil específico */
    @Query("""
        SELECT * FROM log_entries 
        WHERE profile_id = :profileId 
        ORDER BY timestamp DESC, id DESC
    """)
    fun getLogsForProfile(profileId: String): Flow<List<LogEntry>>

    /** Logs filtrados por nivel */
    @Query("""
        SELECT * FROM log_entries 
        WHERE level = :level 
        ORDER BY timestamp DESC, id DESC
    """)
    fun getLogsByLevel(level: LogLevel): Flow<List<LogEntry>>

    /** Últimas N entradas para vista rápida en Dashboard */
    @Query("SELECT * FROM log_entries ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 50): Flow<List<LogEntry>>

    /** Solo errores recientes */
    @Query("""
        SELECT * FROM log_entries 
        WHERE level = 'ERROR' 
        ORDER BY timestamp DESC, id DESC
        LIMIT :limit
    """)
    fun getRecentErrors(limit: Int = 10): Flow<List<LogEntry>>

    // ── Insert ────────────────────────────────────────────────────────────

    @Insert
    suspend fun insertLog(entry: LogEntry)

    @Insert
    suspend fun insertLogs(entries: List<LogEntry>)

    // ── Delete ────────────────────────────────────────────────────────────

    @Query("DELETE FROM log_entries")
    suspend fun clearAllLogs()

    /** Elimina logs más antiguos que el timestamp dado (limpieza automática) */
    @Query("DELETE FROM log_entries WHERE timestamp < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)

    /** Mantiene solo los últimas N entradas */
    @Query("""
        DELETE FROM log_entries 
        WHERE id NOT IN (
            SELECT id FROM log_entries 
            ORDER BY timestamp DESC, id DESC
            LIMIT :keepCount
        )
    """)
    suspend fun keepOnly(keepCount: Int = 500)
}
