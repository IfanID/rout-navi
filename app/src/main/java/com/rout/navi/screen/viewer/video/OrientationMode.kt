package com.rout.navi.screen.viewer.video

import android.content.pm.ActivityInfo

/**
 * Pilihan orientasi layar pemutar video.
 *
 * [AUTO_VIDEO] memilih potret atau lanskap dari bentuk video yang sedang
 * diputar, sehingga video vertikal tidak dilihat miring. Mode lain memaksa
 * orientasi tetap tanpa peduli isi video.
 */
enum class OrientationMode {
    AUTO_VIDEO,
    LANDSCAPE,
    PORTRAIT,
    SENSOR_LANDSCAPE,
    SENSOR_PORTRAIT;

    /** Nilai yang diteruskan ke Activity.requestedOrientation. */
    val screenOrientation: Int
        get() = when (this) {
            AUTO_VIDEO -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            SENSOR_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

    /**
     * Orientasi efektif untuk video yang sedang diputar.
     *
     * Ukuran video dari mesin pemutar sudah berupa ukuran tampilan, jadi video
     * ponsel yang disimpan mendatar lalu diberi penanda putar sudah terhitung
     * benar sebagai potret. Video tanpa ukuran mengikuti sensor, bukan memaksa
     * ke satu arah.
     */
    fun resolve(videoWidth: Int, videoHeight: Int): Int = when (this) {
        AUTO_VIDEO -> when {
            videoWidth <= 0 || videoHeight <= 0 ->
                ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR

            isLandscape(videoWidth, videoHeight) ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }

        else -> screenOrientation
    }

    /** Mode berikutnya untuk tombol HUD: otomatis, lanskap, potret. */
    val nextForHud: OrientationMode
        get() = when (this) {
            AUTO_VIDEO -> LANDSCAPE
            LANDSCAPE -> PORTRAIT
            else -> AUTO_VIDEO
        }

    companion object {
        /**
         * Bentuk video dengan toleransi 10 persen. Video hampir persegi
         * dihitung potret supaya tidak berubah arah di sekitar titik tengah.
         */
        fun isLandscape(videoWidth: Int, videoHeight: Int): Boolean =
            videoWidth > 0 && videoHeight > 0 && videoWidth * 10 >= videoHeight * 11
    }
}
