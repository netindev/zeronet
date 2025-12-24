package tk.netindev.zeronet.vpn.network

import tk.netindev.zeronet.vpn.network.CIDR
import java.util.PriorityQueue
import java.util.TreeSet
import java.util.Vector

class NetworkSpace {
    private val addressTree = TreeSet<IPAddress>()

    fun getNetworks(included: Boolean): MutableCollection<IPAddress?> {
        val ips = Vector<IPAddress?>()
        for (ipAddress in addressTree) {
            if (ipAddress.included == included) {
                ips.add(ipAddress)
            }
        }
        return ips
    }

    fun clear() {
        this.addressTree.clear()
    }

    fun addIP(cidr: CIDR, include: Boolean) {
        this.addressTree.add(IPAddress(cidr, include))
    }

    private fun generateIPList(): TreeSet<IPAddress> {
        val networks = PriorityQueue(addressTree)
        val done = TreeSet<IPAddress>()

        var currentNet = networks.poll()
        if (currentNet == null) {
            return done
        }
        while (currentNet != null) {
            val nextNet = networks.poll()

            if (nextNet == null || currentNet.lastAddress!!.compareTo(nextNet.firstAddress) < 0) {
                done.add(currentNet)
                currentNet = nextNet
            } else {
                if (currentNet.firstAddress == nextNet.firstAddress && currentNet.networkMask >= nextNet.networkMask) {
                    if (currentNet.included == nextNet.included) {
                        currentNet = nextNet
                    } else {
                        val newNetworks = nextNet.split()
                        if (!networks.contains(newNetworks[1])) {
                            networks.add(newNetworks[1])
                        }
                        if (newNetworks[0]!!.lastAddress != currentNet.lastAddress) {
                            if (!networks.contains(newNetworks[0])) {
                                networks.add(newNetworks[0])
                            }
                        }
                    }
                } else {
                    if (currentNet.included != nextNet.included) {
                        val newNetworks = currentNet.split()
                        if (newNetworks[1]!!.networkMask == nextNet.networkMask) {
                            networks.add(nextNet)
                        } else {
                            networks.add(newNetworks[1])
                            networks.add(nextNet)
                        }
                        currentNet = newNetworks[0]
                    }
                }
            }
        }
        return done
    }

    val positiveIPList: MutableCollection<IPAddress?>
        get() {
            val sortedIPList =
                this.generateIPList()
            val vector =
                Vector<IPAddress?>()
            for (ipAddress in sortedIPList) {
                if (ipAddress.included) {
                    vector.add(ipAddress)
                }
            }
            return vector
        }
}
