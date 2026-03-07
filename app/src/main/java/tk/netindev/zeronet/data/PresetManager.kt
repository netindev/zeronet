package tk.netindev.zeronet.data

import android.content.Context
import org.json.JSONObject
import tk.netindev.zeronet.service.config.Settings
import java.io.File
import java.io.InputStream

data class PresetData(
    val name: String,
    val fileName: String,
    val customPayloadEnabled: Boolean = false,
    val customPayloadText: String = "",
    val tunnelType: String = "SSH_DIRECT",
    val remoteProxyHost: String = "",
    val remoteProxyPort: String = "0",
    val sniHost: String = "",
    val dnsttDnsServer: String = "",
    val dnsttTunnelDomain: String = "",
    val dnsttPublicKey: String = "",
    val sshHost: String = "",
    val sshPort: String = "22",
    val sshUsername: String = "",
    val sshPassword: String = "",
    val lastModified: Long = 0L,
)

object PresetManager {

    private fun getPresetsDir(context: Context): File {
        val dir = File(context.filesDir, "presets")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listPresets(context: Context): List<PresetData> {
        val dir = getPresetsDir(context)
        val files = dir.listFiles { f -> f.extension == "zero" } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.mapNotNull { file ->
            try {
                parsePresetFile(file)
            } catch (_: Exception) {
                null
            }
        }
    }

    fun savePreset(context: Context, name: String): File {
        val settings = Settings(context)
        val sshConfig = settings.getSshConfig()
        val dnsttConfig = settings.getDnsttConfig()

        val json = JSONObject().apply {
            put("name", name)
            put("customPayloadEnabled", settings.getCustomPayloadEnabled())
            put("customPayloadText", settings.getString(Settings.CUSTOM_PAYLOAD_KEY))
            put("tunnelType", settings.getString(Settings.TUNNEL_TYPE_KEY, "SSH_DIRECT"))
            put("remoteProxyHost", settings.getString(Settings.REMOTE_PROXY_HOST_KEY))
            put("remoteProxyPort", settings.getString(Settings.REMOTE_PROXY_PORT_KEY))
            put("sniHost", settings.getString(Settings.SSL_SNI_HOST_KEY, ""))
            put("dnsttDnsServer", dnsttConfig.dnsServer)
            put("dnsttTunnelDomain", dnsttConfig.tunnelDomain)
            put("dnsttPublicKey", dnsttConfig.publicKey)
            put("sshHost", sshConfig.host)
            put("sshPort", sshConfig.port.toString())
            put("sshUsername", sshConfig.username)
            put("sshPassword", sshConfig.password)
        }

        val safeName = name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val file = File(getPresetsDir(context), "${safeName}.zero")
        file.writeText(json.toString(2))
        return file
    }

    fun loadPreset(context: Context, fileName: String): PresetData? {
        val file = File(getPresetsDir(context), fileName)
        if (!file.exists()) return null
        return try {
            parsePresetFile(file)
        } catch (_: Exception) {
            null
        }
    }

    fun applyPreset(context: Context, preset: PresetData) {
        val settings = Settings(context)
        settings.setCustomPayloadEnabled(preset.customPayloadEnabled)
        settings.setString(Settings.CUSTOM_PAYLOAD_KEY, preset.customPayloadText)
        settings.setString(Settings.TUNNEL_TYPE_KEY, preset.tunnelType)
        settings.setString(Settings.REMOTE_PROXY_HOST_KEY, preset.remoteProxyHost)
        settings.setString(Settings.REMOTE_PROXY_PORT_KEY, preset.remoteProxyPort)
        settings.setString(Settings.SSL_SNI_HOST_KEY, preset.sniHost)
        settings.setLastConfigIsPredefined(!preset.customPayloadEnabled)

        settings.setSshConfig(
            Settings.SshConfig(
                host = preset.sshHost,
                port = preset.sshPort.toIntOrNull() ?: 22,
                username = preset.sshUsername,
                password = preset.sshPassword
            )
        )

        settings.setDnsttConfig(
            Settings.DnsttConfig(
                dnsServer = preset.dnsttDnsServer,
                tunnelDomain = preset.dnsttTunnelDomain,
                publicKey = preset.dnsttPublicKey
            )
        )
    }

    fun deletePreset(context: Context, fileName: String): Boolean {
        val file = File(getPresetsDir(context), fileName)
        return file.delete()
    }

    fun getPresetFile(context: Context, fileName: String): File {
        return File(getPresetsDir(context), fileName)
    }

    fun importPreset(context: Context, inputStream: InputStream, originalName: String): PresetData? {
        return try {
            val content = inputStream.bufferedReader().readText()
            val json = JSONObject(content)
            val name = json.optString("name", originalName.removeSuffix(".zero"))
            val safeName = name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val file = File(getPresetsDir(context), "${safeName}.zero")
            file.writeText(json.toString(2))
            parsePresetFile(file)
        } catch (_: Exception) {
            null
        }
    }

    private fun parsePresetFile(file: File): PresetData {
        val json = JSONObject(file.readText())
        return PresetData(
            name = json.optString("name", file.nameWithoutExtension),
            fileName = file.name,
            customPayloadEnabled = json.optBoolean("customPayloadEnabled", false),
            customPayloadText = json.optString("customPayloadText", ""),
            tunnelType = json.optString("tunnelType", "SSH_DIRECT"),
            remoteProxyHost = json.optString("remoteProxyHost", ""),
            remoteProxyPort = json.optString("remoteProxyPort", "0"),
            sniHost = json.optString("sniHost", ""),
            dnsttDnsServer = json.optString("dnsttDnsServer", ""),
            dnsttTunnelDomain = json.optString("dnsttTunnelDomain", ""),
            dnsttPublicKey = json.optString("dnsttPublicKey", ""),
            sshHost = json.optString("sshHost", ""),
            sshPort = json.optString("sshPort", "22"),
            sshUsername = json.optString("sshUsername", ""),
            sshPassword = json.optString("sshPassword", ""),
            lastModified = file.lastModified(),
        )
    }
}
