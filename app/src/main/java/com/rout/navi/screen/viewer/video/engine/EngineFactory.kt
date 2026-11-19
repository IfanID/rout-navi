package com.rout.navi.screen.viewer.video.engine

import android.content.Context
import android.net.Uri
import com.rout.navi.screen.viewer.video.model.VideoPlayerState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Pabrik mesin pemutar.
 *
 * Isinya berbeda per versi aplikasi. Versi ringan hanya punya Media3, versi
 * penuh menambah libVLC sebagai cadangan. Kode di luar paket ini cukup memanggil
 * [createMedia3] dan [createFallback] tanpa tahu mesin mana yang tersedia.
 */
interface EngineFactory {

    /** Mesin Media3 untuk berkas yang didukung perangkat. */
    fun createMedia3(
        context: Context,
        uri: Uri,
        state: MutableStateFlow<VideoPlayerState>,
        onUnsupported: () -> Unit
    ): PlaybackEngine

    /**
     * Mesin cadangan yang boleh dipakai sekarang, atau `null` bila perkakas
     * pemutar di versi ini tidak membawanya. `null` berarti berkas dengan
     * format asing langsung ditolak dengan pesan yang jelas.
     */
    fun createFallback(
        context: Context,
        uri: Uri,
        state: MutableStateFlow<VideoPlayerState>
    ): PlaybackEngine?

    /** Nama mesin cadangan untuk log, kosong bila tidak ada. */
    val fallbackName: String
}
