package com.ghostnexora.vpn.ui.screens.importexport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostnexora.vpn.data.model.VpnProfile
import com.ghostnexora.vpn.data.repository.ProfileRepository
import com.ghostnexora.vpn.util.ImportResult
import com.ghostnexora.vpn.util.JsonManager
import com.ghostnexora.vpn.util.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            _importState.update { it.copy(isLoading = true, error = null) }

            try {
                val fileName = resolveFileName(uri)
                val result = jsonManager.importFromUri(uri)

                when (result) {
                    is ImportResult.Success -> {
                        _importState.update {
                            it.copy(
                                isLoading = false,
                                selectedUri = uri,
                                fileName = fileName,
                                previewProfiles = result.profiles,
                                sourceName = result.sourceName,
                                validation = ValidationResult(
                                    isValid = true,
                                    message = "Formato válido",
                                    profileCount = result.profiles.size
                                ),
                                error = null
                            )
                        }
                    }

                    is ImportResult.Error -> {
                        _importState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message,
                                previewProfiles = emptyList()
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _importState.update {
                    it.copy(isLoading = false, error = "Error inesperado: ${e.message}")
                }
            }
        }
    }

    fun confirmImport(merge: Boolean) {
        val profiles = _importState.value.previewProfiles
        if (profiles.isEmpty()) return

        viewModelScope.launch {
            _importState.update { it.copy(isLoading = true) }

            if (!merge) {
                repository.deleteAllProfiles()
            }

            repository.saveProfiles(profiles)

            _importState.update {
                it.copy(
                    isLoading = false,
                    importSuccess = true,
                    importedCount = profiles.size
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

    fun toggleProfileSelection(profileId: String) {
        val current = _exportState.value.selectedIds.toMutableSet()
        if (profileId in current) current.remove(profileId) else current.add(profileId)
        _exportState.update { it.copy(selectedIds = current) }
    }

    fun toggleSelectAll(profiles: List<VpnProfile>) {
        val allIds = profiles.map { it.id }.toSet()
        val current = _exportState.value.selectedIds
        _exportState.update {
            it.copy(selectedIds = if (current.size == allIds.size) emptySet() else allIds)
        }
    }

    fun exportSelected(allProfiles: List<VpnProfile>) {
        viewModelScope.launch {
            _exportState.update { it.copy(isLoading = true, error = null) }

            val toExport = resolveExportSelection(allProfiles)
            if (toExport.isEmpty()) {
                _exportState.update { it.copy(isLoading = false, error = "No hay perfiles para exportar") }
                return@launch
            }

            val uri = jsonManager.exportToDownloads(toExport)
            if (uri != null) {
                _exportState.update {
                    it.copy(
                        isLoading = false,
                        exportSuccess = true,
                        exportedCount = toExport.size
                    )
                }
            } else {
                _exportState.update {
                    it.copy(isLoading = false, error = "Error al guardar el archivo en Descargas")
                }
            }
        }
    }

    fun exportToUri(uri: Uri, allProfiles: List<VpnProfile>) {
        viewModelScope.launch {
            _exportState.update { it.copy(isLoading = true, error = null) }

            val toExport = resolveExportSelection(allProfiles)
            if (toExport.isEmpty()) {
                _exportState.update { it.copy(isLoading = false, error = "No hay perfiles para exportar") }
                return@launch
            }

            val ok = jsonManager.exportToUri(uri, toExport)
            if (ok) {
                _exportState.update {
                    it.copy(
                        isLoading = false,
                        exportSuccess = true,
                        exportedCount = toExport.size
                    )
                }
            } else {
                _exportState.update { it.copy(isLoading = false, error = "No se pudo escribir el archivo") }
            }
        }
    }

    fun clearExportMessage() {
        _exportState.update { it.copy(exportSuccess = false, error = null) }
    }

    private fun resolveExportSelection(allProfiles: List<VpnProfile>): List<VpnProfile> {
        return if (_exportState.value.selectedIds.isEmpty()) {
            allProfiles
        } else {
            allProfiles.filter { it.id in _exportState.value.selectedIds }
        }
    }

    private fun resolveFileName(uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: uri.lastPathSegment ?: "archivo.json"
        } catch (e: Exception) {
            "archivo.json"
        }
    }
}

data class ImportUiState(
    val isLoading: Boolean = false,
    val selectedUri: Uri? = null,
    val fileName: String = "",
    val sourceName: String = "",
    val previewProfiles: List<VpnProfile> = emptyList(),
    val validation: ValidationResult? = null,
    val importSuccess: Boolean = false,
    val importedCount: Int = 0,
    val error: String? = null
) {
    val hasFile: Boolean get() = selectedUri != null
    val canImport: Boolean get() = previewProfiles.isNotEmpty() && !isLoading
}

data class ExportUiState(
    val isLoading: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val exportSuccess: Boolean = false,
    val exportedCount: Int = 0,
    val error: String? = null
) {
    val hasSelection: Boolean get() = selectedIds.isNotEmpty()
}
