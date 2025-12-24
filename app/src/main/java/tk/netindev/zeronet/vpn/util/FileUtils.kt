package tk.netindev.zeronet.vpn.util

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Scanner

object FileUtils {
    @JvmStatic
    fun readFromRaw(context: Context, resId: Int): String {
        val `in` = context.resources.openRawResource(resId)
        val scanner = Scanner(`in`, "UTF-8")
            .useDelimiter("\\A")
        val sb = StringBuilder()
        while (scanner.hasNext()) {
            sb.append(scanner.next())
        }
        scanner.close()
        return sb.toString()
    }

    @JvmStatic
    fun saveTextFile(file: File, contents: String?): Boolean {
        try {
            if (!file.exists()) {
                file.createNewFile()
            }
            val writer = FileWriter(file, false)
            writer.write(contents)
            writer.close()
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }
}
