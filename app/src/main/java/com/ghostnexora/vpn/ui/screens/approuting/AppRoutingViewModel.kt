package com.ghostnexora.vpn.ui.screens.approuting

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ghostnexora.vpn.data.model.AppRoutingMode
import com.ghostnexora.vpn.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AppRoutingViewModel @Inject constructor(
    private val repository: ProfileRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppRoutingUiState())
    val uiState: StateFlow<AppRoutingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.appRoutingPreferences.collect { preferences ->
                _uiState.update {
                    it.copy(
                        mode = preferences.mode,
                        selectedPackages = preferences.normalizedPackages
                    )
                }
            }
        }
        refreshApps()
    }

    fun refreshApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = runCatching { withContext(Dispatchers.IO) { loadLaunchableApps() } }
            result.onSuccess { apps ->
                _uiState.update { it.copy(isLoading = false, apps = apps, error = null) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message?.take(160) ?: "No se pudo leer la lista de aplicaciones"
                    )
                }
            }
        }
    }

    fun setSearchQuery(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    fun setMode(mode: AppRoutingMode) {
        _uiState.update { it.copy(mode = mode, savedMessage = null) }
        viewModelScope.launch {
            repository.setAppRoutingMode(mode)
            _uiState.update { it.copy(savedMessage = "Modo de aplicaciones actualizado. Se aplicará en la próxima conexión.") }
        }
    }

    fun togglePackage(packageName: String) {
        val next = _uiState.value.selectedPackages.toMutableSet().apply {
            if (!add(packageName)) remove(packageName)
        }.toSet()
        _uiState.update { it.copy(selectedPackages = next, savedMessage = null) }
        viewModelScope.launch {
            repository.setAppRoutingPackages(next)
            _uiState.update { it.copy(savedMessage = "Selección guardada. Se aplicará en la próxima conexión.") }
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedPackages = emptySet(), savedMessage = null) }
        viewModelScope.launch {
            repository.setAppRoutingPackages(emptySet())
            _uiState.update { it.copy(savedMessage = "Selección eliminada") }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(savedMessage = null, error = null) }
    }

    private fun loadLaunchableApps(): List<InstalledAppItem> {
        val manager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            manager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }

        return activities
            .asSequence()
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val packageName = activityInfo.packageName.orEmpty()
                if (packageName.isBlank() || packageName == context.packageName) return@mapNotNull null
                val applicationInfo = activityInfo.applicationInfo ?: return@mapNotNull null
                val label = runCatching { applicationInfo.loadLabel(manager).toString() }
                    .getOrDefault(packageName)
                    .ifBlank { packageName }
                InstalledAppItem(
                    packageName = packageName,
                    label = label,
                    isSystem = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
                )
            }
            .distinctBy(InstalledAppItem::packageName)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, InstalledAppItem::label))
            .toList()
    }
}

data class InstalledAppItem(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
)

data class AppRoutingUiState(
    val isLoading: Boolean = true,
    val mode: AppRoutingMode = AppRoutingMode.ALL,
    val selectedPackages: Set<String> = emptySet(),
    val apps: List<InstalledAppItem> = emptyList(),
    val searchQuery: String = "",
    val savedMessage: String? = null,
    val error: String? = null
) {
    val filteredApps: List<InstalledAppItem>
        get() {
            val query = searchQuery.trim()
            if (query.isBlank()) return apps
            return apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }

    val selectionWarning: String?
        get() = if (mode == AppRoutingMode.ONLY_SELECTED && selectedPackages.isEmpty()) {
            "Selecciona al menos una aplicación. La VPN rechazará la conexión para evitar una regla ambigua."
        } else {
            null
        }
}