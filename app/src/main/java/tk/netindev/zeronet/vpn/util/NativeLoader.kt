package tk.netindev.zeronet.vpn.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.text.TextUtils
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipFile

object NativeLoader {
    private const val TAG = "NL"

    @SuppressLint("SetWorldReadable")
    private fun loadFromZip(
        context: Context,
        libName: String?,
        destFile: File,
        arch: String?
    ): Boolean {
        var zipFile: ZipFile? = null
        var stream: InputStream? = null
        try {
            zipFile = ZipFile(context.applicationInfo.sourceDir)
            var entry = zipFile.getEntry("lib/$arch/$libName.so")
            if (entry == null) {
                entry = zipFile.getEntry("jni/$arch/$libName.so")
                if (entry == null) throw Exception("Unable to find file in apk:lib/$arch/$libName")
            }
            stream = zipFile.getInputStream(entry)
            val out: OutputStream = FileOutputStream(destFile)
            val buf = ByteArray(4096)
            var len: Int
            while ((stream.read(buf).also { len = it }) > 0) {
                Thread.yield()
                out.write(buf, 0, len)
            }
            out.close()
            destFile.setReadable(true, false)
            destFile.setExecutable(true, false)
            destFile.setWritable(true)
            return true
        } catch (e: Exception) {
            Log.e(TAG, e.message!!)
        } finally {
            if (stream != null) {
                try {
                    stream.close()
                } catch (e: Exception) {
                    Log.e(TAG, e.message!!)
                }
            }
            if (zipFile != null) {
                try {
                    zipFile.close()
                } catch (e: Exception) {
                    Log.e(TAG, e.message!!)
                }
            }
        }
        return false
    }

    @JvmStatic
    fun loadNativeBinary(context: Context, libName: String?, destLocalFile: File): File? {
        try {
            val fileNativeBin = File(getNativeLibraryDir(context), "$libName.so")
            if (fileNativeBin.exists()) {
                if (fileNativeBin.canExecute()) return fileNativeBin
                else {
                    setExecutable(fileNativeBin)

                    if (fileNativeBin.canExecute()) return fileNativeBin
                }
            } else if (destLocalFile.exists()) {
                if (destLocalFile.canExecute()) return destLocalFile
                else {
                    setExecutable(destLocalFile)

                    if (destLocalFile.canExecute()) return destLocalFile
                }
            }
            val abisList: MutableList<String?> = ArrayList()
            val abis = Build.SUPPORTED_ABIS
            if (abis != null) {
                for (abi in abis) {
                    if (!TextUtils.isEmpty(abi)) {
                        abisList.add(abi)
                    }
                }
            }
            for (folder in abisList) {
                if (loadFromZip(context, libName, destLocalFile, folder)) {
                    return destLocalFile
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, e.message, e)
        }
        return null
    }

    private fun setExecutable(fileBin: File) {
        fileBin.setReadable(true)
        fileBin.setExecutable(true)
        fileBin.setWritable(false)
        fileBin.setWritable(true, true)
    }

    private fun getNativeLibraryDir(context: Context): String? {
        val appInfo = context.applicationInfo
        return appInfo.nativeLibraryDir
    }
}

