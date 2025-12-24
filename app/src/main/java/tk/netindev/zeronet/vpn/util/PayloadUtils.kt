package tk.netindev.zeronet.vpn.util

import androidx.collection.ArrayMap
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.Random
import java.util.regex.Pattern

object PayloadUtils {
    private val lastRotateList: MutableMap<Int, Int> = ArrayMap()
    private var lastPayload: String = ""

    fun formatCustomPayload(hostname: String, port: Int, payload: String): String {
        var payload = payload
        val replacements: MutableMap<String, String> = ArrayMap()

        replacements["[method]"] = "CONNECT"
        replacements["[host]"] = hostname
        replacements["[port]"] = port.toString()
        replacements["[host_port]"] = "$hostname:$port"
        replacements["[protocol]"] = "HTTP/1.0"
        replacements["[ssh]"] = "$hostname:$port"

        replacements["[crlf]"] = "\r\n"
        replacements["[cr]"] = "\r"
        replacements["[lf]"] = "\n"
        replacements["[lfcr]"] = "\n\r"

        replacements["\\n"] = "\n"
        replacements["\\r"] = "\r"

        val userAgent = System.getProperty("http.agent") ?: "Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.130 Safari/537.36"
        replacements["[ua]"] = userAgent

        for ((key, value) in replacements) {
            payload = payload.replace(key.lowercase(Locale.getDefault()), value)
        }

        payload = parseRandom(parseRotate(payload))

        return payload
    }

    @Throws(IOException::class)
    fun injectSplitPayload(requestPayload: String, outputStream: OutputStream): Boolean {
        return if (requestPayload.contains("[delay_split]")) {
            val parts = requestPayload.split(Pattern.quote("[delay_split]").toRegex())
            for ((i, chunk) in parts.withIndex()) {
                if (!injectSimpleSplit(chunk, outputStream)) {
                    writeString(outputStream, chunk)
                }
                if (i < parts.size - 1) Thread.sleep(1000)
            }
            true
        } else {
            injectSimpleSplit(requestPayload, outputStream)
        }
    }

    @Throws(IOException::class)
    private fun injectSimpleSplit(requestPayload: String, outputStream: OutputStream): Boolean {
        if (requestPayload.contains("[split]")) {
            val parts = requestPayload.split(Pattern.quote("[split]").toRegex())
            for (chunk in parts) writeString(outputStream, chunk)
            return true
        }
        return false
    }

    @Throws(IOException::class)
    private fun writeString(outputStream: OutputStream, str: String) {
        outputStream.write(str.toByteArray(StandardCharsets.ISO_8859_1))
        outputStream.flush()
    }

    fun parseRotate(payload: String): String {
        var payload = payload
        val matcher = Pattern.compile("\\[rotate=(.*?)]").matcher(payload)

        if (payload != lastPayload) {
            restartRotateAndRandom()
            lastPayload = payload
        }

        var index = 0
        while (matcher.find()) {
            val group = matcher.group(1) ?: continue
            val options = group.split(";")
            if (options.isEmpty()) continue

            val lastIndex = lastRotateList[index] ?: -1
            val selected = (lastIndex + 1) % options.size

            payload = payload.replace(matcher.group(0)!!, options[selected])
            lastRotateList[index] = selected
            index++
        }

        return payload
    }

    fun parseRandom(payload: String): String {
        var payload = payload
        val matcher = Pattern.compile("\\[random=(.*?)]").matcher(payload)
        val random = Random()

        while (matcher.find()) {
            val group = matcher.group(1) ?: continue
            val options = group.split(";")
            if (options.isEmpty()) continue

            val selected = random.nextInt(options.size)
            payload = payload.replace(matcher.group(0)!!, options[selected])
        }

        return payload
    }

    fun restartRotateAndRandom() {
        lastRotateList.clear()
    }
}
