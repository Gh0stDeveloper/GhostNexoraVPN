package com.ghostnexora.vpn.data.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Entidad principal que representa un perfil VPN.
 * Mapeada directamente a la tabla Room y al JSON de importación/exportación.
 */
@Entity(tableName = "vpn_profiles")
data class VpnProfile(

    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "host")
    val host: String = "",

    @ColumnInfo(name = "port")
    val port: Int = 443,

    @ColumnInfo(name = "username")
    val username: String = "",

    @ColumnInfo(name = "password")
    val password: String = "",

    // Campo legado para compatibilidad con versiones previas
    @ColumnInfo(name = "method")
    val method: String = "ssh",

    @ColumnInfo(name = "connection_mode")
    val connectionMode: String = ConnectionMode.SSH_DIRECT.id,

    @ColumnInfo(name = "ssl_enabled")
    val sslEnabled: Boolean = false,

    @ColumnInfo(name = "sni")
    val sni: String = "",

    @ColumnInfo(name = "payload")
    val payload: String = "",

    // Proxy embebido (proxy_host, proxy_port, proxy_type)
    @Embedded
    val proxy: ProxyConfig = ProxyConfig(),

    // Tags almacenados como String separado por comas, convertidos en lista
    @ColumnInfo(name = "tags")
    val tagsRaw: String = "",

    @ColumnInfo(name = "notes")
    val notes: String = "",

    @ColumnInfo(name = "enabled")
    val enabled: Boolean = true,

    @ColumnInfo(name = "last_used")
    val lastUsed: String = "",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false
) {
    /** Convierte el campo raw en lista limpia de tags */
    val tags: List<String>
        get() = tagsRaw
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    /** Representación legible del servidor */
    val serverAddress: String
        get() = "$host:$port"

    /** Indica si el perfil tiene credenciales configuradas */
    val hasCredentials: Boolean
        get() = username.isNotEmpty() && password.isNotEmpty()

    /** Modo real seleccionado, con compatibilidad para perfiles antiguos */
    val selectedMode: ConnectionMode
        get() = ConnectionMode.fromStored(connectionMode, method, sslEnabled)

    /** Etiqueta amigable para mostrar en UI */
    val connectionModeLabel: String
        get() = selectedMode.label

    /** True si el perfil requiere un host SNI */
    val requiresSni: Boolean
        get() = selectedMode.requiresSni

    /** True si el perfil requiere proxy */
    val requiresProxy: Boolean
        get() = selectedMode.requiresProxy

    /** True si el perfil requiere payload */
    val requiresPayload: Boolean
        get() = selectedMode.requiresPayload

    companion object {
        /** Crea un perfil vacío listo para edición */
        fun empty() = VpnProfile(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis()
        )

        /** Convierte lista de tags a String raw para Room */
        fun tagsToRaw(tags: List<String>): String =
            tags.joinToString(",")
    }
}
