package tk.netindev.zeronet.vpn.external

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import tk.netindev.zeronet.service.util.AppLog.d
import tk.netindev.zeronet.service.util.AppLog.e
import tk.netindev.zeronet.vpn.util.NetworkUtils
import tk.netindev.zeronet.vpn.util.NativeLoader.loadNativeBinary
import tk.netindev.zeronet.vpn.util.ProcessUtils
import tk.netindev.zeronet.vpn.util.StreamGobbler
import java.io.File
import java.io.FileDescriptor
import java.io.IOException

class Tun2SocksService(
    private val context: Context,
    private val vpnInterfaceFileDescriptor: ParcelFileDescriptor?,
    private val vpnInterfaceMTU: Int,
    private val vpnIpAddress: String?,
    private val vpnNetMask: String?,
    private val socksServerAddress: String?,
    private val udpgwServerAddress: String?,
    private val dnsResolverAddress: String?,
    private val udpgwTransparentDNS: Boolean
) : Thread(), StreamGobbler.Listener {

    private var tun2SocksProcess: Process? = null

    private var fileTun2Socks: File? = null

    override fun run() {
        d(TAG, "Service starting")
        try {
            val stringBuilder = StringBuilder()
            this.fileTun2Socks =
                loadNativeBinary(context, TUN2SOCKS_BIN, File(context.filesDir, TUN2SOCKS_BIN))
            if (this.fileTun2Socks == null) {
                throw IOException("Tun2Socks not found")
            }
            if (vpnInterfaceFileDescriptor != null) {
                val sockPath = File(ContextCompat.getDataDir(context), "sock_path")
                try {
                    if (!sockPath.exists()) {
                        sockPath.createNewFile()
                    }
                } catch (e: IOException) {
                    e(TAG, "Error: " + e.message)
                    throw e
                }
                stringBuilder.append(this.fileTun2Socks!!.getCanonicalPath())
                stringBuilder.append(" --netif-ipaddr ").append(this.vpnIpAddress)
                stringBuilder.append(" --netif-netmask ").append(this.vpnNetMask)
                stringBuilder.append(" --socks-server-addr ").append(this.socksServerAddress)
                stringBuilder.append(" --tunmtu ").append(this.vpnInterfaceMTU)
                stringBuilder.append(" --tunfd ").append(this.vpnInterfaceFileDescriptor.fd)
                stringBuilder.append(" --sock ").append(sockPath.absolutePath)
                stringBuilder.append(" --loglevel " + 3)

                if (this.udpgwServerAddress != null) {
                    if (udpgwTransparentDNS) {
                        stringBuilder.append(" --udpgw-transparent-dns")
                    }
                    stringBuilder.append(" --udpgw-remote-server-addr ")
                        .append(this.udpgwServerAddress)
                }
                if (this.dnsResolverAddress != null) {
                    stringBuilder.append(" --dnsgw ").append(this.dnsResolverAddress)
                }

                this.tun2SocksProcess = Runtime.getRuntime().exec(stringBuilder.toString())

                val stdoutGobbler = StreamGobbler(this.tun2SocksProcess!!.inputStream, this)
                val stderrGobbler = StreamGobbler(this.tun2SocksProcess!!.errorStream, this)

                stdoutGobbler.start()
                stderrGobbler.start()

                if (!this.sendParcelFileDescriptor(this.vpnInterfaceFileDescriptor, sockPath)) {
                    throw IOException("Couldn't send file descriptor.")
                }
                d(TAG, "Service started")
                this.tun2SocksProcess!!.waitFor()
            }
        } catch (e: IOException) {
            e(TAG, "IOException", e)
        } catch (e: Exception) {
            if (e !is InterruptedException) {
                d(TAG, "Error: $e")
            }
        }
        this.tun2SocksProcess = null
        d(TAG, "Service stopped")
    }

    @Synchronized
    override fun interrupt() {
        super.interrupt()
        if (this.tun2SocksProcess != null) {
            this.tun2SocksProcess!!.destroy()
        }
        try {
            if (this.fileTun2Socks != null) {
                ProcessUtils.killProcess(this.fileTun2Socks)
            }
        } catch (_: Exception) {
        }
        this.tun2SocksProcess = null
        this.fileTun2Socks = null
    }

    override fun online(log: String?) {
        d(TAG, log!!)
    }

    @Throws(InterruptedException::class)
    private fun sendParcelFileDescriptor(
        fileDescriptor: ParcelFileDescriptor,
        toFile: File
    ): Boolean {
        for (tries in 10 downTo 0) {
            try {
                val localSocket = LocalSocket()
                localSocket.connect(
                    LocalSocketAddress(
                        toFile.absolutePath,
                        LocalSocketAddress.Namespace.FILESYSTEM
                    )
                )
                localSocket.setFileDescriptorsForSend(
                    arrayOf<FileDescriptor?>(
                        fileDescriptor.fileDescriptor
                    )
                )
                localSocket.getOutputStream().write(42)
                localSocket.shutdownOutput()
                localSocket.close()
                return true
            } catch (_: IOException) {
                sleep(500)
            }
        }
        d(TAG, "Unable to send FD")
        return false
    }

    companion object {
        private const val TAG = "Tun2SocksService"
        private const val TUN2SOCKS_BIN = "tun2socks"
    }
}
