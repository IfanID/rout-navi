package com.raival.compose.file.explorer.screen.viewer.video.model

/** Satu track audio atau subtitle yang dilaporkan libVLC. */
data class VideoTrackInfo(
    val id: Int,
    val name: String
)

/** Mode tampilan video, sama dengan daftar skala libVLC. */
enum class VideoScaleMode {
    BEST_FIT,
    FIT_SCREEN,
    FILL,
    ORIGINAL
}

data class VideoPlayerState(
    val isReady: Boolean = false,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
    val isEnded: Boolean = false,
    val error: String? = null,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val isMuted: Boolean = false,
    val showControls: Boolean = true,
    val isLocked: Boolean = false,
    val title: String = "",
    val audioTracks: List<VideoTrackInfo> = emptyList(),
    val subtitleTracks: List<VideoTrackInfo> = emptyList(),
    val selectedAudioTrack: Int = -1,
    val selectedSubtitleTrack: Int = -1,
    val scaleMode: VideoScaleMode = VideoScaleMode.BEST_FIT
)
