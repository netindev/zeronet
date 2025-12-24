package tk.netindev.zeronet.vpn.network

import java.util.Locale

class CIDR(private val ip: String, val len: Int) {
    override fun toString(): String {
        return String.format(Locale.ENGLISH, "%s/%d", ip, len)
    }

    val int: Long
        get() = getInt(ip)

    companion object {
        fun getInt(ipAddress: String): Long {
            val ipArr: Array<String?> =
                ipAddress.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var ip: Long = 0
            ip += ipArr[0]!!.toLong() shl 24
            ip += ipArr[1]!!.toInt().toLong() shl 16
            ip += ipArr[2]!!.toInt().toLong() shl 8
            ip += ipArr[3]!!.toInt().toLong()
            return ip
        }
    }
}