package tk.netindev.zeronet.vpn.util

data class PrivateAddress(
    val ipAddress: String,
    val subnet: String,
    val prefixLength: Int,
    val router: String
)