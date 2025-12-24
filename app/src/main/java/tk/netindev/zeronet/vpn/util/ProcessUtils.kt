package tk.netindev.zeronet.vpn.util

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader

object ProcessUtils {
    @Throws(IOException::class)
    fun findProcessId(process: String?): Int {
        for (command in mutableListOf<String?>("ps -ef", "ps -A", "toolbox ps")) {
            val proc = Runtime.getRuntime().exec(command)
            try {
                BufferedReader(InputStreamReader(proc.inputStream)).use { reader ->
                    return reader.lines()
                        .filter { line: String? ->
                            !line!!.contains("PID") && line.contains(
                                process!!
                            )
                        }
                        .map { line: String? ->
                            line!!.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }
                                .toTypedArray()
                        }
                        .mapToInt { parts: Array<String>? ->
                            for (part in parts!!) {
                                try {
                                    return@mapToInt part.toInt()
                                } catch (_: NumberFormatException) {
                                    // ignored
                                }
                            }
                            -1
                        }
                        .filter { pid: Int -> pid > 0 }
                        .findFirst()
                        .orElse(-1)
                }
            } finally {
                proc.destroy()
            }
        }
        return -1
    }

    @JvmOverloads
    @Throws(Exception::class)
    fun killProcess(file: File?, signal: String? = "-9") {
        val procName = file?.getName()
        val procPath = file?.getCanonicalPath()
        val prefixes = mutableListOf<String?>("", "busybox ", "toolbox ")

        var procId: Int
        var attempts = 0

        while ((findProcessId(procName).also { procId = it }) != -1) {
            attempts++

            for (prefix in prefixes) {
                tryExec(prefix + "killall " + signal + " " + procName)
                tryExec(prefix + "killall " + signal + " " + procPath)
            }

            tryExec("kill $signal $procId")

            Thread.sleep(1000)

            if (attempts > 4) {
                throw Exception("Cannot kill: " + file?.absolutePath)
            }
        }
    }

    private fun tryExec(command: String?) {
        try {
            Runtime.getRuntime().exec(command)
        } catch (_: IOException) {
            // ignored
        }
    }
}
