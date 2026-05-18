package com.ghostnexora.vpn.ui.screens.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.LogLevel
import com.ghostnexora.vpn.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val repository: ProfileRepository
) : ViewModel() {

    // ── Filtro de nivel ───────────────────────────────────────────────────
    private val _selectedLevel = MutableStateFlow<LogLevel?>(null)
    val selectedLevel: StateFlow<LogLevel?> = _selectedLevel.asStateFlow()

    // ── Búsqueda por texto ────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // ── UI State ──────────────────────────────────────────────────────────
    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    // ══════════════════════════════════════════════════════════════════════
    // LISTA DE LOGS REACTIVA
    // Combina todos los logs con filtro de nivel y búsqueda en memoria
    // ══════════════════════════════════════════════════════════════════════

    @OptIn(ExperimentalCoroutinesApi::class)
    val logs: StateFlow<List<LogEntry>> = combine(
        repository.allLogs,
        _selectedLevel,
        _searchQuery
    ) { all, level, query ->
        all.filter { entry ->
            val matchLevel = level == null || entry.level == level
            val matchQuery = query.isBlank() ||
                entry.message.contains(query, ignoreCase = true) ||
                entry.tag.contains(query, ignoreCase = true)
            matchLevel && matchQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ══════════════════════════════════════════════════════════════════════
    // ACCIONES
    // ══════════════════════════════════════════════════════════════════════

    fun setLevelFilter(level: LogLevel?) {
        _selectedLevel.value = level
    }

    fun onSearchChange(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun requestClearLogs() {
        _uiState.update { it.copy(showClearDialog = true) }
    }

    fun clearLogs() {
        confirmClearLogs()
    }

    fun dismissClearDialog() {
        _uiState.update { it.copy(showClearDialog = false) }
    }

    fun confirmClearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            _uiState.update {
                it.copy(
                    showClearDialog  = false,
                    snackbarMessage  = "Logs eliminados"
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /** Exporta los logs actuales como texto plano */
    fun exportLogsAsText(logs: List<LogEntry>): String {
        return buildString {
            appendLine("═══════════════════════════════════════")
            appendLine("  Ghost Nexora VPN — Registro de Logs")
            appendLine("═══════════════════════════════════════")
            appendLine()
            logs.forEach { entry ->
                appendLine(
                    "[${entry.dateTimeFormatted}] " +
                    "[${entry.level.label}] " +
                    "[${entry.tag}] " +
                    entry.message
                )
            }
            appendLine()
            appendLine("Total: ${logs.size} entrada(s)")
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// UI STATE
// ══════════════════════════════════════════════════════════════════════════

data class LogsUiState(
    val showClearDialog: Boolean = false,
    val snackbarMessage: String? = null
)
