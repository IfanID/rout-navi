package com.rout.navi.screen.viewer.video.engine

import android.content.Context
import android.net.Uri
import com.rout.navi.screen.viewer.video.model.VideoPlayerState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Versi utama, hanya Media3 ExoPlayer.
 *
 * libVLC tidak ikut dikemas dan kelasnya tidak ada di classpath, jadi tidak ada
 * rujukan yang bisa jatuh ke `NoClassDefFoundError`. Berkas dengan format yang
 * hanya bisa diputar libVLC mendapat pesan, bukan crash, karena [createFallback]
 * mengembalikan `null`.
 */
object Media3OnlyEngineFactory : EngineFactory {

    override val fallbackName: String = ""

    override fun createMedia3(
        context: Context,
        uri: Uri,
        state: MutableStateFlow<VideoPlayerState>,
        onUnsupported: () -> Unit
    ): PlaybackEngine = Media3Engine(context, uri, state, onUnsupported)

    override fun createFallback(
        context: Context,
        uri: Uri,
        state: MutableStateFlow<VideoPlayerState>
    ): PlaybackEngine? = null
}
