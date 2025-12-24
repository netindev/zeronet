package tk.netindev.zeronet.data

data class AppSettings(
    val redirectUdp: Boolean = false,
    val udpRemoteGateway: String = "",
    val redirectDns: Boolean = false,
    val primaryDns: String = "8.8.8.8",
    val enableTethering: Boolean = false,
    val sshPingInterval: Int = 30,
    val appFilterList: List<String> = emptyList(),
    val disableTcpDelay: Boolean = false,
    val threadPoolCount: Int = 8
)