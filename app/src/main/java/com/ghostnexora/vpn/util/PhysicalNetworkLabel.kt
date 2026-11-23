package com.ghostnexora.vpn.util

import android.content.Context
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.ghostnexora.vpn.R

object PhysicalNetworkLabel {
    @JvmStatic
    fun from(context: Context, capabilities: NetworkCapabilities?): String = when {
        capabilities == null -> context.getString(R.string.network_unavailable)
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
            carrier(context) ?: context.getString(R.string.network_cellular)
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        else -> context.getString(R.string.network_other)
    }

    private fun carrier(context: Context): String? {
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return null
        return runCatching {
            val id = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                SubscriptionManager.getActiveDataSubscriptionId()
            } else {
                SubscriptionManager.getDefaultDataSubscriptionId()
            }
            if (id < 0) return null

            val dataSim = telephony.createForSubscriptionId(id)
            usableName(dataSim.simOperatorName) ?: usableName(dataSim.networkOperatorName)
        }.getOrNull()
    }

    private fun usableName(value: String?): String? =
        value?.trim()?.take(64)?.takeIf { name -> name.any(Char::isLetter) }
}
