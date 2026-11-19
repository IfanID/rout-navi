package com.raival.compose.file.explorer.screen.viewer.video

import android.content.pm.ApplicationInfo
import android.util.Log
import com.raival.compose.file.explorer.App.Companion.globalClass
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Catatan pemutar video untuk build debug. Ditulis ke
 * `.prism/logs/video-player.log` supaya bisa dibaca tanpa adb ketika
 * pemutaran gagal di perangkat.
 */
object VideoPlayerLog {
    private const val TAG = "VideoPlayer"
    private const val FILE_NAME = "video-player.log"
    private const val MAX_BYTES = 256 * 1024

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val lock = Any()

    // Build release tidak menulis berkas log, hanya logcat.
    private val isDebuggable by lazy {
        (globalClass.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun info(message: String) {
        Log.i(TAG, message)
        if (isDebuggable) append("I", message)
    }

    fun error(message: String) {
        Log.e(TAG, message)
        if (isDebuggable) append("E", message)
    }

    fun error(throwable: Throwable) = error(throwable.stackTraceToString())

    private fun append(level: String, message: String) {
        runCatching {
            val folder = File(globalClass.appFiles.file, "logs")
            if (!folder.exists()) folder.mkdirs()
            val file = File(folder, FILE_NAME)
            synchronized(lock) {
                if (file.length() > MAX_BYTES) file.delete()
                file.appendText("${timeFormat.format(Date())} $level $message\n")
            }
        }
    }
}
