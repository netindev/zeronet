package tk.netindev.zeronet.service.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object ConnectionStatsManager {
    data class Stats(
        val pingMs: Long = -1,
        val avgUploadKbps: Double = 0.0,
        val avgDownloadKbps: Double = 0.0,
        val sshHost: String = "",
        val payloadName: String = "",
        val connectedSinceEpochMs: Long = -1L,
        val sessionDurationSeconds: Long = 0L,
        val sessionDownloadBytes: Long = 0L,
        val sessionUploadBytes: Long = 0L,
        val city: String = "",
        val region: String = "",
        val country: String = "",
        val ip: String = "",
        val org: String = "",
        val countryCode: String = ""
    )

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats

    @JvmStatic
    fun setPing(pingMs: Long) {
        _stats.value = _stats.value.copy(pingMs = pingMs)
    }

    @JvmStatic
    fun setSpeeds(avgUploadKbps: Double, avgDownloadKbps: Double) {
        _stats.value = _stats.value.copy(avgUploadKbps = avgUploadKbps, avgDownloadKbps = avgDownloadKbps)
    }

    @JvmStatic
    fun setSessionStats(durationSeconds: Long, downloadBytes: Long, uploadBytes: Long) {
        _stats.value = _stats.value.copy(
            sessionDurationSeconds = durationSeconds,
            sessionDownloadBytes = downloadBytes,
            sessionUploadBytes = uploadBytes
        )
    }

    @JvmStatic
    fun setHostAndPayload(sshHost: String, payloadName: String) {
        _stats.value = _stats.value.copy(sshHost = sshHost, payloadName = payloadName)
    }

    @JvmStatic
    fun setConnectedSince(epochMs: Long) {
        _stats.value = _stats.value.copy(connectedSinceEpochMs = epochMs)
    }

    @JvmStatic
    fun setGeo(city: String, region: String, country: String) {
        _stats.value = _stats.value.copy(city = city, region = region, country = country)
    }

    @JvmStatic
    fun setGeoDetailed(ip: String, city: String, region: String, country: String, org: String) {
        _stats.value = _stats.value.copy(ip = ip, city = city, region = region, country = country, org = org)
    }

    @JvmStatic
    fun setGeoDetailed(ip: String, city: String, region: String, country: String, org: String, countryCode: String) {
        _stats.value = _stats.value.copy(ip = ip, city = city, region = region, country = country, org = org, countryCode = countryCode)
    }

    @JvmStatic
    fun reset() {
        _stats.value = Stats()
    }
}
