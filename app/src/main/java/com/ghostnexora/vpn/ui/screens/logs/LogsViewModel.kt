package com.ghostnexora.vpn.ui.screens.logs

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostnexora.vpn.BuildConfig
import com.ghostnexora.vpn.data.model.LogEntry
import com.ghostnexora.vpn.data.model.LogLevel
import com.ghostnexora.vpn.data.repository.ProfileRepository
import com.ghostnexora.vpn.util.httpInjectorLine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val repository: ProfileRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _selectedLevel = MutableStateFlow<LogLevel?>(null)
    val selectedLevel: StateFlow<LogLevel?> = _selectedLevel.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

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
        }.sortedWith(compareBy<LogEntry> { it.timestamp }.thenBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setLevelFilter(level: LogLevel?) { _selectedLevel.value = level }
    fun onSearchChange(query: String) { _searchQuery.value = query }
    fun clearSearch() { _searchQuery.value = "" }
    fun requestClearLogs() { _uiState.update { it.copy(showClearDialog = true) } }
    fun clearLogs() = confirmClearLogs()
    fun dismissClearDialog() { _uiState.update { it.copy(showClearDialog = false) } }

    fun confirmClearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            _uiState.update { it.copy(showClearDialog = false, snackbarMessage = "Logs deleted") }
        }
    }

    fun exportLogs(uri: Uri, logs: List<LogEntry>) {
        viewModelScope.launch {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8).use { writer ->
                    checkNotNull(writer) { "Android could not open the selected file" }
                    writer.write(exportLogsAsText(logs))
                }
            }
            _uiState.update {
                it.copy(
                    snackbarMessage = result.fold(
                        onSuccess = { "Diagnostic report exported" },
                        onFailure = { error -> "Export failed: ${error.message.orEmpty().take(120)}" }
                    )
                )
            }
        }
    }

    fun clearSnackbar() { _uiState.update { it.copy(snackbarMessage = null) } }

    /** Complete sanitized report suitable for support and issue reproduction. */
    fun exportLogsAsText(logs: List<LogEntry>): String {
        val generatedAt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())
        return buildString {
            appendLine("Ghost Nexora VPN — Diagnostic report")
            appendLine("Generated: $generatedAt")
            appendLine("Application: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android API: ${Build.VERSION.SDK_INT}")
            appendLine("ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Package: ${context.packageName}")
            appendLine("Entries: ${logs.size}")
            appendLine("Secrets: sanitized before storage and export")
            appendLine("============================================================")
            logs.sortedWith(compareBy<LogEntry> { it.timestamp }.thenBy { it.id }).forEach { entry ->
                appendLine(entry.httpInjectorLine())
            }
            appendLine("============================================================")
            appendLine("End of report")
        }
    }

    companion object {
        fun suggestedFileName(): String {
            val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            return "GhostNexoraVPN-diagnostics-$timestamp.txt"
        }
    }
}

data class LogsUiState(
    val showClearDialog: Boolean = false,
    val snackbarMessage: String? = null
)
