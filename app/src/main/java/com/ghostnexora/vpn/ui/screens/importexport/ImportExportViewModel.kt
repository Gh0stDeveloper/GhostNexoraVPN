package com.ghostnexora.vpn.ui.screens.importexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.data.repository.ProfileRepository
import com.ghostnexora.vpn.util.ImportResult
import com.ghostnexora.vpn.util.JsonManager
import com.ghostnexora.vpn.util.ProfileFingerprint
import com.ghostnexora.vpn.util.ProfileTechnicalSummaries
import com.ghostnexora.vpn.util.ProfileTechnicalSummary
import com.ghostnexora.vpn.util.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImportExportViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val jsonManager: JsonManager,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    private val _exportState = MutableStateFlow(ExportUiState())
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    val allProfiles: StateFlow<List<VpnProfile>> = repository.allProfiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setImportPassword(value: String) {
        _importState.update { it.copy(password = value, error = null) }
    }

    fun setExportPassword(value: String) {
        _exportState.update { it.copy(password = value, error = null) }
    }

    fun setExportPasswordConfirmation(value: String) {
        _exportState.update { it.copy(passwordConfirmation = value, error = null) }
    }

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            val password = _importState.value.password.toCharArray()
            try {
                loadImportResult(
                    fileName = resolveFileName(uri),
                    sourceLabel = "Archivo",
                    loader = { jsonManager.importFromUri(uri, password.takeIf { it.isNotEmpty() }) },
                    selectedUri = uri
                )
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun retryEncryptedImport() {
        val uri = _importState.value.selectedUri ?: return
        onFilePicked(uri)
    }

    fun onTextProvided(rawText: String, sourceLabel: String = "Portapapeles") {
        viewModelScope.launch {
            val password = _importState.value.password.toCharArray()
            try {
                loadImportResult(
                    fileName = "${sourceLabel.lowercase().replace(' ', '_')}.txt",
                    sourceLabel = sourceLabel,
                    loader = { jsonManager.importFromString(rawText, password.takeIf { it.isNotEmpty() }) },
                    selectedUri = null
                )
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun confirmImport(merge: Boolean) {
        val profiles = _importState.value.previewProfiles
        if (profiles.isEmpty()) return
        viewModelScope.launch {
            _importState.update { it.copy(isLoading = true, error = null) }
            val existing = if (merge) repository.allProfiles.first() else emptyList()
            val (unique, skipped) = ProfileFingerprint.uniqueAgainst(profiles, existing)
            if (!merge) repository.deleteAllProfiles()
            if (unique.isNotEmpty()) repository.saveProfiles(unique)
            _importState.update {
                it.copy(
                    isLoading = false,
                    importSuccess = true,
                    importedCount = unique.size,
                    skippedDuplicateCount = skipped,
                    password = ""
                )
            }
        }
    }

    fun resetImport() {
        _importState.value = ImportUiState()
    }

    fun clearImportMessage() {
        _importState.update { it.copy(importSuccess = false, error = null) }
    }

    private suspend fun loadImportResult(
        fileName: String,
        sourceLabel: String,
        loader: suspend () -> ImportResult,
        selectedUri: Uri?
    ) {
        _importState.update { it.copy(isLoading = true, error = null) }
        try {
            when (val result = loader()) {
                is ImportResult.Success -> {
                    val existing = repository.allProfiles.first()
                    val (_, duplicateCount) = ProfileFingerprint.uniqueAgainst(result.profiles, existing)
                    _importState.update {
                        it.copy(
                            isLoading = false,
                            selectedUri = selectedUri,
                            fileName = fileName,
                            previewProfiles = result.profiles,
                            technicalSummaries = result.profiles.map(ProfileTechnicalSummaries::from),
                            sourceName = result.sourceName.ifBlank { sourceLabel },
                            validation = ValidationResult(
                                true,
                                if (duplicateCount > 0) {
                                    "Configuración válida · $duplicateCount posible(s) duplicado(s) al fusionar"
                                } else {
                                    "Configuración válida y lista para importar"
                                },
                                result.profiles.size
                            ),
                            duplicateCount = duplicateCount,
                            passwordRequired = false,
                            error = null
                        )
                    }
                }

                is ImportResult.PasswordRequired -> _importState.update {
                    it.copy(
                        isLoading = false,
                        selectedUri = selectedUri,
                        fileName = fileName,
                        sourceName = sourceLabel,
                        passwordRequired = true,
                        previewProfiles = emptyList(),
                        technicalSummaries = emptyList(),
                        validation = null,
                        duplicateCount = 0,
                        error = result.message
                    )
                }

                is ImportResult.Error -> _importState.update {
                    it.copy(
                        isLoading = false,
                        selectedUri = selectedUri,
                        fileName = fileName,
                        sourceName = sourceLabel,
                        error = result.message,
                        previewProfiles = emptyList(),
                        technicalSummaries = emptyList(),
                        duplicateCount = 0,
                        validation = ValidationResult(false, result.message, 0)
                    )
                }
            }
        } catch (error: Throwable) {
            _importState.update {
                it.copy(isLoading = false, error = "Error inesperado: ${error.message?.take(120).orEmpty()}")
            }
        }
    }

    fun toggleProfileSelection(profileId: String) {
        val current = _exportState.value.selectedIds.toMutableSet()
        if (profileId in current) current.remove(profileId) else current.add(profileId)
        _exportState.update { it.copy(selectedIds = current) }
    }

    fun toggleSelectAll(profiles: List<VpnProfile>) {
        val allIds = profiles.map(VpnProfile::id).toSet()
        val current = _exportState.value.selectedIds
        _exportState.update {
            it.copy(selectedIds = if (current.size == allIds.size) emptySet() else allIds)
        }
    }

    fun exportSelected(allProfiles: List<VpnProfile>) {
        viewModelScope.launch {
            val selection = resolveExportSelection(allProfiles)
            val password = validateExport(selection) ?: return@launch
            _exportState.update { it.copy(isLoading = true, error = null) }
            try {
                val uri = jsonManager.exportToDownloads(selection, password)
                if (uri != null) {
                    markExportSuccess(selection.size)
                } else {
                    _exportState.update { it.copy(isLoading = false, error = "No se pudo guardar el archivo cifrado") }
                }
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun exportToUri(uri: Uri, allProfiles: List<VpnProfile>) {
        viewModelScope.launch {
            val selection = resolveExportSelection(allProfiles)
            val password = validateExport(selection) ?: return@launch
            _exportState.update { it.copy(isLoading = true, error = null) }
            try {
                val ok = jsonManager.exportToUri(uri, selection, password)
                if (ok) markExportSuccess(selection.size)
                else _exportState.update { it.copy(isLoading = false, error = "No se pudo escribir el archivo cifrado") }
            } finally {
                password.fill('\u0000')
            }
        }
    }

    fun clearExportMessage() {
        _exportState.update { it.copy(exportSuccess = false, error = null) }
    }

    private fun validateExport(selection: List<VpnProfile>): CharArray? {
        val state = _exportState.value
        when {
            selection.isEmpty() -> _exportState.update { it.copy(error = "No hay perfiles para exportar") }
            state.password.length < 10 -> _exportState.update { it.copy(error = "La contraseña debe tener al menos 10 caracteres") }
            state.password != state.passwordConfirmation -> _exportState.update { it.copy(error = "Las contraseñas no coinciden") }
            else -> return state.password.toCharArray()
        }
        return null
    }

    private fun markExportSuccess(count: Int) {
        _exportState.update {
            it.copy(
                isLoading = false,
                exportSuccess = true,
                exportedCount = count,
                password = "",
                passwordConfirmation = ""
            )
        }
    }

    private fun resolveExportSelection(allProfiles: List<VpnProfile>): List<VpnProfile> =
        if (_exportState.value.selectedIds.isEmpty()) allProfiles
        else allProfiles.filter { it.id in _exportState.value.selectedIds }

    private fun resolveFileName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
        } ?: uri.lastPathSegment ?: "configuracion.gnx"
    }.getOrDefault("configuracion.gnx")
}

data class ImportUiState(
    val isLoading: Boolean = false,
    val selectedUri: Uri? = null,
    val fileName: String = "",
    val sourceName: String = "",
    val previewProfiles: List<VpnProfile> = emptyList(),
    val technicalSummaries: List<ProfileTechnicalSummary> = emptyList(),
    val validation: ValidationResult? = null,
    val duplicateCount: Int = 0,
    val password: String = "",
    val passwordRequired: Boolean = false,
    val importSuccess: Boolean = false,
    val importedCount: Int = 0,
    val skippedDuplicateCount: Int = 0,
    val error: String? = null
) {
    val hasFile: Boolean get() = selectedUri != null
    val canImport: Boolean get() = previewProfiles.isNotEmpty() && !isLoading
}

data class ExportUiState(
    val isLoading: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val password: String = "",
    val passwordConfirmation: String = "",
    val exportSuccess: Boolean = false,
    val exportedCount: Int = 0,
    val error: String? = null
) {
    val hasSelection: Boolean get() = selectedIds.isNotEmpty()
    val passwordValid: Boolean get() = password.length >= 10 && password == passwordConfirmation
}