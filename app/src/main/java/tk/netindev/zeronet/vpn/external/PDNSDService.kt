package tk.netindev.zeronet.vpn.external

import android.content.Context
import tk.netindev.zeronet.R
import tk.netindev.zeronet.service.util.AppLog.d
import tk.netindev.zeronet.service.util.AppLog.e
import tk.netindev.zeronet.vpn.util.NetworkUtils
import tk.netindev.zeronet.vpn.util.FileUtils.readFromRaw
import tk.netindev.zeronet.vpn.util.FileUtils.saveTextFile
import tk.netindev.zeronet.vpn.util.NativeLoader.loadNativeBinary
import tk.netindev.zeronet.vpn.util.ProcessUtils
import tk.netindev.zeronet.vpn.util.StreamGobbler
import java.io.File
import java.io.IOException
import java.util.Locale

class PDNSDService(
    private val context: Context,
    private val dnsHosts: Array<String?>,
    private val dnsPort: Int,
    private val serviceHost: String?,
    private val servicePort: Int
) : Thread() {

    private var process: Process? = null
    private var file: File? = null

    override fun run() {
        d(TAG, "Service starting")
        try {
            this.file = loadNativeBinary(context, PDNSD_BIN, File(context.filesDir, PDNSD_BIN))
            if (this.file == null) {
                throw IOException("PDNSD not found")
            }
            val configuration = deployConfiguration(
                context.filesDir,
                dnsHosts,
                dnsPort,
                serviceHost,
                servicePort
            )

            val command = file!!.getCanonicalPath() + " -v9 -c " + configuration

            this.process = Runtime.getRuntime().exec(command)

            val listener: StreamGobbler.Listener = StreamGobbler.Listener {}

            val gobblerOut = StreamGobbler(this.process!!.inputStream, listener)
            val gobblerErr = StreamGobbler(this.process!!.errorStream, listener)

            gobblerOut.start()
            gobblerErr.start()

            d(TAG, "Service started")

            process!!.waitFor()
        } catch (e: IOException) {
            e(TAG, "Error: ", e)
        } catch (e: Exception) {
            if (e !is InterruptedException) {
                d(TAG, "Error: $e")
            }
        }
        this.process = null
        d(TAG, "Service stopped")
    }

    @Synchronized
    override fun interrupt() {
        super.interrupt()
        if (this.process != null) {
            this.process!!.destroy()
        }
        try {
            if (this.file != null) {
                ProcessUtils.killProcess(this.file)
            }
        } catch (_: Exception) {
        }
        this.process = null
        this.file = null
    }

    @Throws(IOException::class)
    private fun deployConfiguration(
        directory: File, dnsHosts: Array<String?>, dnsPort: Int,
        serviceHost: String?, servicePort: Int
    ): File {
        val content = readFromRaw(this.context, R.raw.pdnsd_local)

        val serverDns = StringBuilder()
        for (i in dnsHosts.indices) {
            serverDns.append(
                String.format(
                    Locale.US,
                    PDNSD_SERVER,
                    "server" + (i + 1),
                    dnsHosts[i],
                    dnsPort
                )
            )
        }

        val configuration = String.format(
            content,
            serverDns,
            directory.getCanonicalPath(),
            serviceHost,
            servicePort
        )

        val file = File(directory, "pdnsd.conf")
        if (file.exists()) {
            file.delete()
        }
        saveTextFile(file, configuration)

        val cache = File(directory, "pdnsd.cache")
        if (!cache.exists()) {
            try {
                cache.createNewFile()
            } catch (e: Exception) {
                e(TAG, "Exception", e)
            }
        }

        return file
    }


    companion object {
        private const val TAG = "PDNSDService"
        private const val PDNSD_SERVER =
            "server {\n label= \"%1\$s\";\n ip = %2\$s;\n port = %3\$d;\n uptest = none;\n }\n"
        private const val PDNSD_BIN = "pdnsd"
    }
}
