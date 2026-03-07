package tk.netindev.zeronet.data

import android.os.Parcelable
import android.os.Parcel

data class TunnelConfig(
    val sshHost: String = "",
    val sshPort: Int = 22,
    val sshUsername: String = "",
    val sshPassword: String = "",
    val sshKeyPath: String = "",
    val localPort: Int = 1080,

    val operator: String = "",
    val payload: String = "",
    val isCustomPayload: Boolean = false,
    val customPayloadText: String = "",
    val autoTimAds: Boolean = false,

    val remoteProxyHost: String = "",
    val remoteProxyPort: Int = 0,
    val remoteProxyUsername: String = "",
    val remoteProxyPassword: String = "",

    val dnsForward: Boolean = true,
    val dnsResolver: String = "8.8.8.8",
    val udpForward: Boolean = false,
    val udpResolver: String = "8.8.8.8",
    val enableTethering: Boolean = false,
    val pingInterval: Int = 30,

    val tunnelType: TunnelType = TunnelType.SSH_DIRECT
) : Parcelable {
    
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(sshHost)
        parcel.writeInt(sshPort)
        parcel.writeString(sshUsername)
        parcel.writeString(sshPassword)
        parcel.writeString(sshKeyPath)
        parcel.writeInt(localPort)
        parcel.writeString(operator)
        parcel.writeString(payload)
        parcel.writeByte(if (isCustomPayload) 1 else 0)
        parcel.writeString(customPayloadText)
        parcel.writeByte(if (autoTimAds) 1 else 0)
        parcel.writeString(remoteProxyHost)
        parcel.writeInt(remoteProxyPort)
        parcel.writeString(remoteProxyUsername)
        parcel.writeString(remoteProxyPassword)
        parcel.writeByte(if (dnsForward) 1 else 0)
        parcel.writeString(dnsResolver)
        parcel.writeByte(if (udpForward) 1 else 0)
        parcel.writeString(udpResolver)
        parcel.writeByte(if (enableTethering) 1 else 0)
        parcel.writeInt(pingInterval)
        parcel.writeString(tunnelType.name)
    }
    
    override fun describeContents(): Int {
        return 0
    }
    
    companion object CREATOR : Parcelable.Creator<TunnelConfig> {
        override fun createFromParcel(parcel: Parcel): TunnelConfig {
            return TunnelConfig(
                sshHost = parcel.readString() ?: "",
                sshPort = parcel.readInt(),
                sshUsername = parcel.readString() ?: "",
                sshPassword = parcel.readString() ?: "",
                sshKeyPath = parcel.readString() ?: "",
                localPort = parcel.readInt(),
                operator = parcel.readString() ?: "",
                payload = parcel.readString() ?: "",
                isCustomPayload = parcel.readByte() != 0.toByte(),
                customPayloadText = parcel.readString() ?: "",
                autoTimAds = parcel.readByte() != 0.toByte(),
                remoteProxyHost = parcel.readString() ?: "",
                remoteProxyPort = parcel.readInt(),
                remoteProxyUsername = parcel.readString() ?: "",
                remoteProxyPassword = parcel.readString() ?: "",
                dnsForward = parcel.readByte() != 0.toByte(),
                dnsResolver = parcel.readString() ?: "",
                udpForward = parcel.readByte() != 0.toByte(),
                udpResolver = parcel.readString() ?: "",
                enableTethering = parcel.readByte() != 0.toByte(),
                pingInterval = parcel.readInt(),
                tunnelType = TunnelType.valueOf(parcel.readString() ?: "SSH_DIRECT")
            )
        }
        
        override fun newArray(size: Int): Array<TunnelConfig?> {
            return arrayOfNulls(size)
        }
    }
}

enum class TunnelType {
    SSH_DIRECT,
    SSH_PROXY,
    SSH_SSL_TUNNEL,
    DNSTT
}
