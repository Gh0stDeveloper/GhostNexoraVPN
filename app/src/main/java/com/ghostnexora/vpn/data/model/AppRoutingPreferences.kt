package com.ghostnexora.vpn.data.model

enum class AppRoutingMode(
    val id: String,
    val label: String,
    val description: String
) {
    ALL(
        id = "all",
        label = "Todas las aplicaciones",
        description = "Todo el tráfico compatible usa la VPN. Ghost Nexora VPN se excluye para evitar bucles."
    ),
    ONLY_SELECTED(
        id = "only_selected",
        label = "Solo seleccionadas",
        description = "Únicamente las aplicaciones elegidas entran al túnel."
    ),
    EXCLUDE_SELECTED(
        id = "exclude_selected",
        label = "Excluir seleccionadas",
        description = "Todas las aplicaciones usan la VPN excepto las elegidas."
    );

    companion object {
        fun fromId(value: String?): AppRoutingMode = entries.firstOrNull { it.id == value } ?: ALL
    }
}

data class AppRoutingPreferences(
    val mode: AppRoutingMode = AppRoutingMode.ALL,
    val packages: Set<String> = emptySet()
) {
    val normalizedPackages: Set<String>
        get() = packages
            .asSequence()
            .map(String::trim)
            .filter { it.isNotBlank() && PACKAGE_PATTERN.matches(it) }
            .toSortedSet()

    val requiresSelection: Boolean get() = mode == AppRoutingMode.ONLY_SELECTED
    val isValid: Boolean get() = !requiresSelection || normalizedPackages.isNotEmpty()

    companion object {
        private val PACKAGE_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
    }
}