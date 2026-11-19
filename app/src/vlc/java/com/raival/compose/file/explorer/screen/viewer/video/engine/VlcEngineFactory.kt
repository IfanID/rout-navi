package com.raival.compose.file.explorer.screen.viewer.video.engine

import android.content.Context
import android.net.Uri
import com.raival.compose.file.explorer.screen.viewer.video.model.VideoPlayerState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Versi VLC: Media3 untuk berkas umum, libVLC untuk sisanya.
 *
 * Dipilih Gradle saat membangun varian vlc yang membawa paket libVLC.
 */
object VlcEngineFactory : EngineFactory {

    override val fallbackName: String = "libvlc"

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
    ): PlaybackEngine = LibVlcEngine(context, uri, state)
}
