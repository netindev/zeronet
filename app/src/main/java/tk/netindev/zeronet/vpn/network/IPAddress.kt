package tk.netindev.zeronet.vpn.network

import tk.netindev.zeronet.vpn.network.CIDR
import java.math.BigInteger
import java.util.Locale

class IPAddress : Comparable<IPAddress?> {
    private val netAddress: BigInteger
    @JvmField
    var networkMask: Int
    @JvmField
    val included: Boolean
    private val isV4: Boolean
    var firstAddress: BigInteger? = null
        get() {
            if (field == null) {
                field = getMaskedAddress(false)
            }
            return field
        }
        private set
    var lastAddress: BigInteger? = null
        get() {
            if (field == null) {
                field = getMaskedAddress(true)
            }
            return field
        }
        private set

    override fun compareTo(other: IPAddress?): Int {
        val comp = this.firstAddress!!.compareTo(other?.firstAddress!!)
        if (comp != 0) {
            return comp
        }
        return other.networkMask.compareTo(networkMask)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is IPAddress) {
            return super.equals(other)
        }
        val cast = other
        return (this.networkMask == cast.networkMask) &&
                cast.firstAddress == this.firstAddress
    }

    constructor(cidr: CIDR, include: Boolean) {
        this.included = include
        this.netAddress = BigInteger.valueOf(cidr.int)
        this.networkMask = cidr.len
        this.isV4 = true
    }


    private fun getMaskedAddress(one: Boolean): BigInteger {
        var numAddress = this.netAddress
        val numBits: Int
        if (this.isV4) {
            numBits = 32 - this.networkMask
        } else {
            numBits = 128 - this.networkMask
        }
        for (i in 0..<numBits) {
            if (one) {
                numAddress = numAddress.setBit(i)
            } else {
                numAddress = numAddress.clearBit(i)
            }
        }
        return numAddress
    }

    override fun toString(): String {
        if (this.isV4) {
            return String.format(Locale.US, "%s/%d", this.iPv4Address, this.networkMask)
        } else {
            return String.format(Locale.US, "%s/%d", this.iPv6Address, this.networkMask)
        }
    }

    internal constructor(
        baseAddress: BigInteger, mask: Int, included: Boolean,
        isV4: Boolean
    ) {
        this.netAddress = baseAddress
        this.networkMask = mask
        this.included = included
        this.isV4 = isV4
    }

    fun split(): Array<IPAddress?> {
        val firstHalf = IPAddress(
            this.firstAddress!!, this.networkMask + 1,
            this.included, this.isV4
        )
        val secondHalf = IPAddress(
            firstHalf.lastAddress!!.add(BigInteger.ONE),
            this.networkMask + 1, this.included, this.isV4
        )
        return arrayOf(firstHalf, secondHalf) as Array<IPAddress?>
    }

    val iPv4Address: String
        get() {
            val ip = this.netAddress.toLong()
            return String.format(
                Locale.US, "%d.%d.%d.%d", (ip shr 24) % 256, (ip shr 16) % 256,
                (ip shr 8) % 256, ip % 256
            )
        }

    val iPv6Address: String
        get() {
            var bigInteger = this.netAddress

            var ipV6: String? = null
            var lastPart = true
            while (bigInteger.compareTo(BigInteger.ZERO) > 0) {
                val part =
                    bigInteger.mod(BigInteger.valueOf(0x10000)).toLong()
                if (ipV6 != null || part != 0L) {
                    if (ipV6 == null && !lastPart) {
                        ipV6 = ":"
                    }
                    if (lastPart) {
                        ipV6 = String.format(Locale.US, "%x", part)
                    } else {
                        ipV6 = String.format(Locale.US, "%x:%s", part, ipV6)
                    }
                }
                bigInteger = bigInteger.shiftRight(16)
                lastPart = false
            }
            if (ipV6 == null) {
                return "::"
            }
            return ipV6
        }

    fun containsNet(ipAddress: IPAddress): Boolean {
        val ourFirst = this.firstAddress!!
        val ourLast = this.lastAddress!!
        val netFirst = ipAddress.firstAddress!!
        val netLast = ipAddress.lastAddress!!

        val a = ourFirst.compareTo(netFirst) != 1
        val b = ourLast.compareTo(netLast) != -1

        return a && b
    }

    override fun hashCode(): Int {
        var result = networkMask
        result = 31 * result + included.hashCode()
        result = 31 * result + isV4.hashCode()
        result = 31 * result + netAddress.hashCode()
        result = 31 * result + (firstAddress?.hashCode() ?: 0)
        result = 31 * result + (lastAddress?.hashCode() ?: 0)
        result = 31 * result + iPv4Address.hashCode()
        result = 31 * result + iPv6Address.hashCode()
        return result
    }
}
