package tk.netindev.zeronet.vpn.tunnel

import android.os.Parcel
import android.os.Parcelable

class TunnelVpnSettings: Parcelable {
    @JvmField
	var socksServer: String?
    @JvmField
	val dnsForward: Boolean
    @JvmField
	val dnsResolver: Array<String?>?
    @JvmField
	val udpResolver: String?
    @JvmField
	val excludeIps: Array<String?>?
    @JvmField
	val udpDnsRelay: Boolean

    @JvmField
	val enableFilterApps: Boolean
    @JvmField
	val filterBypassMode: Boolean
    @JvmField
	val filterApps: Array<String?>?

    @JvmField
	var enableTethering: Boolean

    constructor(
        socksServer: String?, dnsForward: Boolean, dnsResolver: Array<String?>?,
        udpDnsRelay: Boolean, udpResolver: String?, excludeIps: Array<String?>?,
        enableFilterApps: Boolean, filterBypassMode: Boolean, filterApps: Array<String?>?,
        enableTethering: Boolean
    ) {
        this.socksServer = socksServer
        this.dnsForward = dnsForward
        this.udpDnsRelay = udpDnsRelay
        this.dnsResolver = dnsResolver
        this.udpResolver = udpResolver
        this.excludeIps = excludeIps

        this.enableFilterApps = enableFilterApps
        this.filterBypassMode = filterBypassMode
        this.filterApps = filterApps

        this.enableTethering = enableTethering
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(this.socksServer)
        parcel.writeInt(if (this.dnsForward) 1 else 0)
        parcel.writeInt(if (this.udpDnsRelay) 1 else 0)
        parcel.writeStringArray(this.dnsResolver)
        parcel.writeString(this.udpResolver)
        parcel.writeStringArray(this.excludeIps)
        parcel.writeInt(if (this.filterBypassMode) 1 else 0)
        parcel.writeStringArray(this.filterApps)
        parcel.writeInt(if (this.enableFilterApps) 1 else 0)
        parcel.writeInt(if (this.enableTethering) 1 else 0)
    }

    constructor(parcel: Parcel) {
        this.socksServer = parcel.readString()
        this.dnsForward = parcel.readInt() == 1
        this.udpDnsRelay = parcel.readInt() == 1
        this.dnsResolver = parcel.createStringArray()
        this.udpResolver = parcel.readString()
        this.excludeIps = parcel.createStringArray()
        this.filterBypassMode = parcel.readInt() == 1
        this.filterApps = parcel.createStringArray()
        this.enableFilterApps = parcel.readInt() == 1
        this.enableTethering = parcel.readInt() == 1
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<TunnelVpnSettings?> =
            object : Parcelable.Creator<TunnelVpnSettings?> {
                override fun createFromParcel(parcel: Parcel): TunnelVpnSettings {
                    return TunnelVpnSettings(parcel)
                }

                override fun newArray(size: Int): Array<TunnelVpnSettings?> {
                    return arrayOfNulls(size)
                }
            }
    }
}
