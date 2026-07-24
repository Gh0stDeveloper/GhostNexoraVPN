package com.ghostnexora.vpn.data.model

data class VpnTrafficStats(
    val receivedBytes: Long = 0L,
    val sentBytes: Long = 0L,
    val downloadBytesPerSecond: Long = 0L,
    val uploadBytesPerSecond: Long = 0L,
    val reconnectCount: Int = 0,
    val latencyMs: Long = 0L,
    val networkType: String = "--",
    val protocol: String = "--"
)
