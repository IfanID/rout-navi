package com.rout.navi.screen.viewer.video.engine

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.rout.navi.App.Companion.globalClass
import com.rout.navi.R
import com.rout.navi.common.name
import com.rout.navi.screen.viewer.video.VideoPlayerLog
import com.rout.navi.screen.viewer.video.model.VideoPlayerState
import com.rout.navi.screen.viewer.video.model.VideoScaleMode
import com.rout.navi.screen.viewer.video.model.VideoTrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Mesin pemutar utama berbasis Media3 ExoPlayer.
 *
 * Perpindahan kecepatan di ExoPlayer memakai pemroses tempo Sonic yang tidak
 * membuang buffer audio, jadi tahan layar 2x berjalan mulus seperti YouTube.
 * Berkas yang tidak bisa dibaca ExoPlayer dilaporkan lewat [onUnsupported] agar
 * pemutar berpindah ke libVLC.
 */
@OptIn(UnstableApi::class)
class Media3Engine(
    private val context: Context,
    override val uri: Uri,
    private val state: MutableStateFlow<VideoPlayerState>,
    private val onUnsupported: () -> Unit
) : PlaybackEngine {

    override val name: String = "media3"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionJob: Job? = null

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null

    private var prepared = false
    private var released = false
    private var fallbackRequested = false
    private var subtitleLoaded = false

    /** Penanda sudah pernah benar-benar berjalan; dipakai memutus keputusan fallback. */
    private var everPlayed = false

    // Keadaan sebelum layar ditinggal, dipakai melanjutkan video setelah kembali.
    private var pausedPosition = 0L
    private var resumeOnReturn = false

    /** Subtitle yang ikut dimuat bersama video utama. */
    private val subtitleConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()

    /** Pemetaan id track bulat ke grup track Media3. */
    private var mappedTracks: List<MappedTrack> = emptyList()

    private data class MappedTrack(
        val id: Int,
        val group: Tracks.Group,
        val index: Int,
        val type: Int
    )

    // ---------------------------------------------------------------- layout

    override fun attachHost(host: ViewGroup) {
        val view = playerView ?: PlayerView(host.context).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }.also { playerView = it }

        view.player = player
        if (view.parent !== host) {
            (view.parent as? ViewGroup)?.removeView(view)
            host.addView(
                view,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    override fun releaseHost() {
        // PlayerView disimpan supaya surface bisa dipasang lagi tanpa memuat
        // ulang media saat layar dibangun ulang.
        VideoPlayerLog.info("host media3 dilepas dari Compose")
    }

    // ------------------------------------------------------------ pemutaran

    override suspend fun prepare() {
        if (released) return
        if (prepared) {
            VideoPlayerLog.info("media3 init ulang, pemutar dipakai lagi; uri=$uri")
            withContext(Dispatchers.Main) { playerView?.player = player }
            return
        }
        prepared = true
        VideoPlayerLog.info("media3 init uri=$uri scheme=${uri.scheme}")

        state.update {
            it.copy(
                isLoading = true,
                isReady = false,
                error = null,
                title = uri.name ?: globalClass.getString(R.string.unknown)
            )
        }

        try {
            withContext(Dispatchers.Main) {
                createPlayer()
                loadSiblingSubtitleOnce()
                loadMedia(0L, playWhenReady = true)
            }
            startPositionTracking()
        } catch (e: Throwable) {
            VideoPlayerLog.error(e)
            state.update {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                    error = globalClass.getString(R.string.video_playback_failed)
                )
            }
        }
    }

    private fun createPlayer() {
        val audoAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        val exo = ExoPlayer.Builder(context)
            // Fokus audio diurus lapisan Compose, jadi ExoPlayer tidak mengurusnya.
            .setAudioAttributes(audoAttributes, false)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exo.addListener(listener)
        player = exo

        playerView?.player = exo
        exo.volume = if (state.value.isMuted) 0f else 1f
    }

    private fun loadMedia(startPosition: Long, playWhenReady: Boolean) {
        val player = player ?: return
        val item = MediaItem.Builder()
            .setUri(uri)
            .setSubtitleConfigurations(subtitleConfigs.toList())
            .build()
        player.setMediaItem(item, startPosition.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = playWhenReady
        player.setPlaybackSpeed(state.value.playbackSpeed)
    }

    private fun buildSubtitleConfig(file: File): MediaItem.SubtitleConfiguration =
        MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
            .setMimeType(SubtitleFiles.mimeTypeOf(file))
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

    /** Memuat subtitle bersebelahan nama berkas, sekali saja per pemutaran. */
    private fun loadSiblingSubtitleOnce() {
        if (subtitleLoaded) return
        subtitleLoaded = true
        val candidate = SubtitleFiles.siblingOf(uri) ?: return
        subtitleConfigs += buildSubtitleConfig(candidate)
        VideoPlayerLog.info("subtitle bersebelahan ${candidate.name}")
    }

    // ---------------------------------------------------------------- status

    private val listener = object : Player.Listener {

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> state.update { it.copy(isLoading = true) }

                Player.STATE_READY -> state.update {
                    it.copy(
                        isLoading = false,
                        isReady = true,
                        isEnded = false,
                        error = null,
                        duration = durationOrFallback(it.duration)
                    )
                }

                Player.STATE_ENDED -> {
                    VideoPlayerLog.info("media3 EndReached")
                    state.update {
                        it.copy(
                            isLoading = false,
                            isPlaying = false,
                            isEnded = true,
                            currentPosition = it.duration,
                            showControls = true
                        )
                    }
                }

                else -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) everPlayed = true
            state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            rebuildTracks(tracks)
            ensureAudioDecodable(tracks)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                state.update { it.copy(isReady = true) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            VideoPlayerLog.error(
                "media3 ${error.errorCodeName} (${error.errorCode}): ${error.message}"
            )
            if (shouldFallback(error)) {
                requestFallback()
                return
            }
            state.update {
                it.copy(
                    isPlaying = false,
                    isLoading = false,
                    error = globalClass.getString(R.string.video_playback_failed)
                )
            }
        }
    }

    /**
     * Berkas hilang atau tanpa izin tidak perlu dicoba lagi di libVLC.
     * Berkas yang sudah sempat berjalan juga tidak dipindah mesin, supaya
     * pemutaran tidak terulang dari awal hanya karena satu segmen rusak.
     * Selain itu, ExoPlayer dianggap tidak sanggup dan pemutar pindah mesin.
     */
    private fun shouldFallback(error: PlaybackException): Boolean {
        if (everPlayed) return false
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> false

            else -> true
        }
    }

    private fun requestFallback() {
        if (fallbackRequested) return
        fallbackRequested = true
        // Diamkan mesin ini lebih dulu supaya tidak ada dua mesin yang
        // berbunyi bersamaan selama perpindahan.
        player?.let { active ->
            active.volume = 0f
            active.pause()
        }
        VideoPlayerLog.info("media3 menyerahkan berkas ke libvlc")
        onUnsupported()
    }

    /** Menolak berkas yang audionya tidak punya decoder di perangkat ini. */
    private fun ensureAudioDecodable(tracks: Tracks) {
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (audioGroups.isEmpty()) return
        val supported = audioGroups.any { group ->
            (0 until group.length).any { group.isTrackSupported(it) }
        }
        if (!supported) {
            VideoPlayerLog.info("media3 tidak punya decoder audio, pindah ke libvlc")
            requestFallback()
        }
    }

    private fun durationOrFallback(current: Long): Long {
        val fromPlayer = player?.duration ?: C.TIME_UNSET
        val safe = if (fromPlayer != C.TIME_UNSET && fromPlayer > 0) fromPlayer else current
        return safe.coerceAtLeast(0L)
    }

    private fun startPositionTracking() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (!released) {
                player?.let { active ->
                    if (active.isPlaying) {
                        state.update {
                            it.copy(
                                currentPosition = active.currentPosition.coerceAtLeast(0L),
                                duration = durationOrFallback(it.duration)
                            )
                        }
                    }
                }
                delay(250)
            }
        }
    }

    // ------------------------------------------------------------- perintah

    override fun playPause() {
        val player = player ?: return
        resumeOnReturn = false
        if (state.value.isEnded) {
            VideoPlayerLog.info("playPause media3: ulang dari awal")
            player.seekTo(0L)
            player.play()
            state.update {
                it.copy(isEnded = false, isPlaying = true, isLoading = true, currentPosition = 0)
            }
            return
        }
        VideoPlayerLog.info("playPause media3 playing=${player.isPlaying}")
        if (player.isPlaying) player.pause() else player.play()
    }

    override fun pause() {
        val player = player ?: return
        pausedPosition = player.currentPosition.coerceAtLeast(0L)
        resumeOnReturn = player.isPlaying
        VideoPlayerLog.info("pause media3 playing=$resumeOnReturn pos=$pausedPosition")
        player.pause()
    }

    override fun resumeAfterReturn() {
        val player = player ?: return
        val shouldResume = resumeOnReturn
        val target = pausedPosition
        resumeOnReturn = false
        if (!shouldResume) {
            VideoPlayerLog.info("resume media3 dilewati, video tadi dijeda pengguna")
            return
        }
        VideoPlayerLog.info("resume media3 pos=$target")
        player.seekTo(target)
        player.play()
        state.update { it.copy(isPlaying = true, isEnded = false, currentPosition = target) }
    }

    override fun stopPlayback() {
        val player = player ?: return
        VideoPlayerLog.info("stopPlayback media3 playing=${player.isPlaying}")
        player.volume = 0f
        player.pause()
    }

    override fun seekTo(position: Long) {
        val player = player ?: return
        val duration = state.value.duration
        val target = position.coerceIn(0, if (duration > 0) duration else position)
        player.seekTo(target)
        state.update { it.copy(currentPosition = target, isEnded = false) }
    }

    override fun setSpeed(speed: Float) {
        val safe = speed.coerceIn(0.25f, 4f)
        VideoPlayerLog.info("setSpeed media3 $safe")
        player?.setPlaybackSpeed(safe)
        state.update { it.copy(playbackSpeed = safe) }
    }

    override fun setMuted(muted: Boolean) {
        player?.volume = if (muted) 0f else 1f
        state.update { it.copy(isMuted = muted) }
    }

    // ------------------------------------------------------------ track

    override fun refreshTracks() {
        player?.let { rebuildTracks(it.currentTracks) }
    }

    private fun rebuildTracks(tracks: Tracks) {
        val mapped = mutableListOf<MappedTrack>()
        val audio = mutableListOf<VideoTrackInfo>()
        val subtitles = mutableListOf<VideoTrackInfo>()
        var nextId = 0
        var selectedAudio = -1
        var selectedSubtitle = -1

        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_AUDIO && group.type != C.TRACK_TYPE_TEXT) continue
            for (index in 0 until group.length) {
                if (!group.isTrackSupported(index)) continue
                val format = group.getTrackFormat(index)
                val id = nextId++
                val label = format.label
                    ?: format.language?.takeIf { it.isNotBlank() && it != "und" }
                    ?: if (group.type == C.TRACK_TYPE_AUDIO) "Audio ${audio.size + 1}"
                    else "Subtitle ${subtitles.size + 1}"
                mapped += MappedTrack(id, group, index, group.type)
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    audio += VideoTrackInfo(id, label)
                    if (group.isTrackSelected(index)) selectedAudio = id
                } else {
                    subtitles += VideoTrackInfo(id, label)
                    if (group.isTrackSelected(index)) selectedSubtitle = id
                }
            }
        }

        mappedTracks = mapped
        VideoPlayerLog.info(
            "track media3 audio=${audio.size} subtitle=${subtitles.size} " +
                    "aktif=$selectedAudio/$selectedSubtitle"
        )
        state.update {
            it.copy(
                audioTracks = audio,
                subtitleTracks = subtitles,
                selectedAudioTrack = selectedAudio,
                selectedSubtitleTrack = selectedSubtitle
            )
        }
    }

    override fun selectAudioTrack(trackId: Int) {
        applyTrackSelection(trackId, C.TRACK_TYPE_AUDIO)
    }

    override fun selectSubtitleTrack(trackId: Int) {
        applyTrackSelection(trackId, C.TRACK_TYPE_TEXT)
    }

    private fun applyTrackSelection(trackId: Int, type: Int) {
        val player = player ?: return
        val builder = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(type)
            .setTrackTypeDisabled(type, trackId < 0)
        val target = mappedTracks.firstOrNull { it.id == trackId && it.type == type }
        if (target != null) {
            builder.setOverrideForType(
                TrackSelectionOverride(target.group.mediaTrackGroup, listOf(target.index))
            )
        }
        player.trackSelectionParameters = builder.build()
        refreshTracks()
    }

    override fun addSubtitle(uri: Uri) {
        scope.launch {
            val cached = withContext(Dispatchers.IO) { SubtitleFiles.copyToCache(uri) }
                ?: return@launch
            val player = player ?: return@launch
            // Subtitle baru hanya bisa ikut kalau media item disusun ulang.
            // Posisi dipertahankan supaya pemutaran lanjut di tempat semula.
            val position = player.currentPosition.coerceAtLeast(0L)
            val wasPlaying = player.isPlaying
            subtitleConfigs += buildSubtitleConfig(cached)
            withContext(Dispatchers.Main) {
                loadMedia(position, playWhenReady = wasPlaying)
            }
            VideoPlayerLog.info("subtitle ditambahkan ${cached.absolutePath}")
        }
    }

    // ---------------------------------------------------------------- skala

    override fun setScaleMode(mode: VideoScaleMode) {
        playerView?.resizeMode = when (mode) {
            VideoScaleMode.BEST_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            // VLC "fit screen" ikut layar dengan memotong sisi yang berlebih.
            VideoScaleMode.FIT_SCREEN -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            VideoScaleMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            VideoScaleMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        state.update { it.copy(scaleMode = mode) }
    }

    // ----------------------------------------------------------------- tutup

    override fun release() {
        VideoPlayerLog.info("media3 release uri=$uri")
        released = true
        positionJob?.cancel()
        positionJob = null

        playerView?.let { view ->
            view.player = null
            (view.parent as? ViewGroup)?.removeView(view)
        }
        playerView = null

        player?.let { active ->
            runCatching { active.removeListener(listener) }
            runCatching { active.stop() }
            runCatching { active.release() }
            VideoPlayerLog.info("media3 player.release selesai")
        }
        player = null
        VideoPlayerLog.info("media3 release selesai")
    }
}
