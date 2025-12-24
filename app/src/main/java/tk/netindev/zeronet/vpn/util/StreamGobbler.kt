package tk.netindev.zeronet.vpn.util

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

class StreamGobbler(inputStream: InputStream?, private val listener: Listener?) : Thread() {
    private val reader: BufferedReader = BufferedReader(InputStreamReader(inputStream))
    private val writer: MutableList<String?>? = null

    override fun run() {
        while (true) {
            try {
                val line: String?
                if ((this.reader.readLine().also { line = it }) != null) {
                    this.writer?.add(line)
                    this.listener?.online(line)
                    continue
                }
            } catch (_: IOException) {
                // ignored
            }
            try {
                this.reader.close()
            } catch (_: IOException) {
                // ignored
            }
            return
        }
    }

    fun interface Listener {
        fun online(log: String?)
    }
}
