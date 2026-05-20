package com.ghostnexora.vpn.ui.screens.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostnexora.vpn.data.model.ConnectionMode
import com.ghostnexora.vpn.data.model.ProxyConfig
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.data.repository.ProfileRepository
import com.ghostnexora.vpn.util.isValidHost
import com.ghostnexora.vpn.util.isValidPort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateEditViewModel @Inject constructor(
    private val repository: ProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateEditUiState())
    val uiState: StateFlow<CreateEditUiState> = _uiState.asStateFlow()

    fun loadProfile(profileId: String?) {
        if (profileId == null) {
            _uiState.update { it.copy(isEditMode = false, isLoading = false) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val profile = repository.getProfileById(profileId)
            if (profile != null) {
                _uiState.update {
                    it.copy(
                        isEditMode = true,
                        isLoading = false,
                        profileId = profile.id,
                        name = profile.name,
                        host = profile.host,
                        port = profile.port.toString(),
                        username = profile.username,
                        password = profile.password,
                        method = profile.method,
                        connectionMode = profile.connectionMode,
                        sslEnabled = profile.sslEnabled,
                        sni = profile.sni,
                        payload = profile.payload,
                        proxyHost = profile.proxy.host,
                        proxyPort = profile.proxy.port.takeIf { p -> p > 0 }?.toString() ?: "",
                        proxyType = profile.proxy.type,
                        tags = profile.tagsRaw,
                        notes = profile.notes,
                        enabled = profile.enabled
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Perfil no encontrado") }
            }
        }
    }

    fun onNameChange(v: String) = _uiState.update { it.copy(name = v, nameError = null) }
    fun onHostChange(v: String) = _uiState.update { it.copy(host = v, hostError = null) }
    fun onPortChange(v: String) = _uiState.update { it.copy(port = v, portError = null) }
    fun onUsernameChange(v: String) = _uiState.update { it.copy(username = v) }
    fun onPasswordChange(v: String) = _uiState.update { it.copy(password = v) }

    fun onConnectionModeChange(mode: ConnectionMode) {
        _uiState.update {
            it.copy(
                connectionMode = mode.id,
                method = mode.family,
                sslEnabled = mode.usesTls,
                proxyType = if (mode.requiresProxy && it.proxyType.isBlank()) "http" else it.proxyType
            )
        }
    }

    fun onSslChange(v: Boolean) = _uiState.update { it.copy(sslEnabled = v) }
    fun onSniChange(v: String) = _uiState.update { it.copy(sni = v) }
    fun onPayloadChange(v: String) = _uiState.update { it.copy(payload = v) }
    fun onProxyHostChange(v: String) = _uiState.update { it.copy(proxyHost = v) }
    fun onProxyPortChange(v: String) = _uiState.update { it.copy(proxyPort = v) }
    fun onProxyTypeChange(v: String) = _uiState.update { it.copy(proxyType = v) }
    fun onTagsChange(v: String) = _uiState.update { it.copy(tags = v) }
    fun onNotesChange(v: String) = _uiState.update { it.copy(notes = v) }
    fun onEnabledChange(v: Boolean) = _uiState.update { it.copy(enabled = v) }
    fun togglePasswordVisible() = _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    fun toggleAdvancedSection() = _uiState.update { it.copy(showAdvanced = !it.showAdvanced) }
    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun validate(): Boolean {
        val s = _uiState.value
        val mode = s.selectedMode
        var valid = true

        if (s.name.isBlank()) {
            _uiState.update { it.copy(nameError = "El nombre es obligatorio") }
            valid = false
        }
        if (s.host.isBlank()) {
            _uiState.update { it.copy(hostError = "El host es obligatorio") }
            valid = false
        } else if (!s.host.trim().isValidHost()) {
            _uiState.update { it.copy(hostError = "Host inválido (dominio o IP)") }
            valid = false
        }
        if (s.port.isBlank() || !s.port.isValidPort()) {
            _uiState.update { it.copy(portError = "Puerto inválido (1–65535)") }
            valid = false
        }

        if (mode.family == "ssh" || mode.family == "ssl") {
            if (s.username.isBlank()) {
                _uiState.update { it.copy(error = "El usuario SSH es obligatorio") }
                valid = false
            }
            if (s.password.isBlank()) {
                _uiState.update { it.copy(error = "La contraseña SSH es obligatoria") }
                valid = false
            }
        }

        if (mode.requiresSni && s.sni.isBlank()) {
            _uiState.update { it.copy(error = "El modo seleccionado requiere un SNI") }
            valid = false
        }
        if (mode.requiresProxy) {
            if (s.proxyHost.isBlank()) {
                _uiState.update { it.copy(error = "El modo seleccionado requiere proxy") }
                valid = false
            }
            if (s.proxyPort.isBlank()) {
                _uiState.update { it.copy(error = "El puerto del proxy es obligatorio") }
                valid = false
            } else if (!s.proxyPort.isValidPort()) {
                _uiState.update { it.copy(error = "Puerto de proxy inválido") }
                valid = false
            }
        }
        if (mode.requiresPayload && s.payload.isBlank()) {
            _uiState.update { it.copy(error = "El modo seleccionado requiere payload") }
            valid = false
        }

        return valid
    }

    fun save() {
        if (!validate()) return

        val s = _uiState.value
        val mode = s.selectedMode
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val profile = VpnProfile(
                id = s.profileId,
                name = s.name.trim(),
                host = s.host.trim(),
                port = s.port.toIntOrNull() ?: 443,
                username = s.username.trim(),
                password = s.password.trim(),
                method = mode.family,
                connectionMode = mode.id,
                sslEnabled = mode.usesTls,
                sni = s.sni.trim(),
                payload = s.payload.trim(),
                proxy = ProxyConfig(
                    host = s.proxyHost.trim(),
                    port = s.proxyPort.toIntOrNull() ?: 0,
                    type = s.proxyType.trim()
                ),
                tagsRaw = s.tags.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString(","),
                notes = s.notes.trim(),
                enabled = s.enabled
            )

            if (s.isEditMode) repository.updateProfile(profile) else repository.saveProfile(profile)

            _uiState.update { it.copy(isSaving = false, savedSuccessfully = true) }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// UI STATE
// ══════════════════════════════════════════════════════════════════════════

data class CreateEditUiState(
    val isEditMode: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val error: String? = null,

    val profileId: String = VpnProfile.empty().id,

    val name: String = "",
    val host: String = "",
    val port: String = "443",
    val username: String = "",
    val password: String = "",
    val method: String = "ssh",
    val connectionMode: String = ConnectionMode.SSH_DIRECT.id,
    val sslEnabled: Boolean = false,
    val sni: String = "",
    val payload: String = "",
    val proxyHost: String = "",
    val proxyPort: String = "",
    val proxyType: String = "",
    val tags: String = "",
    val notes: String = "",
    val enabled: Boolean = true,

    val passwordVisible: Boolean = false,
    val showAdvanced: Boolean = false,

    val nameError: String? = null,
    val hostError: String? = null,
    val portError: String? = null
) {
    val title: String get() = if (isEditMode) "Editar Perfil" else "Nuevo Perfil"
    val hasErrors: Boolean get() = nameError != null || hostError != null || portError != null
    val selectedMode: ConnectionMode get() = ConnectionMode.fromStored(connectionMode, method, sslEnabled)
}

val VPN_METHODS = listOf("ssh", "v2ray", "shadowsocks", "wireguard", "trojan", "vless")
val PROXY_TYPES = listOf("", "http", "socks5")
