package com.rout.navi.screen.viewer.video

import android.util.Log

/**
 * Catatan pemutar video. Hanya ditulis ke logcat; berkas log sudah tidak
 * ditulis ke penyimpanan supaya `.prism/logs` tetap bersih.
 */
object VideoPlayerLog {
    private const val TAG = "VideoPlayer"

    fun info(message: String) {
        Log.i(TAG, message)
    }

    fun error(message: String) {
        Log.e(TAG, message)
    }

    fun error(throwable: Throwable) = error(throwable.stackTraceToString())
}
