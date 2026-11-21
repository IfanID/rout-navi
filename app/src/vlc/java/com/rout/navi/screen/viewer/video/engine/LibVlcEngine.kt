package com.rout.navi.screen.viewer.video.engine

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
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
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

/**
 * Mesin pemutar cadangan berbasis libVLC.
 *
 * Dipakai untuk format yang tidak didukung ExoPlayer. Urutan pemasangan
 * mengikuti VLC android: layout video dipasang lebih dulu lewat attachViews,
 * lalu media dimuat dan diputar setelahnya. Urutan terbalik membuat libVLC
 * membuka output video berulang tanpa tujuan dan layar jadi hitam.
 */
class LibVlcEngine(
    private val context: Context,
    override val uri: Uri,
    private val state: MutableStateFlow<VideoPlayerState>
) : PlaybackEngine {

    override val name: String = "libvlc"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionJob: Job? = null

    private var libVLC: LibVLC? = null
    private var player: MediaPlayer? = null
    private var media: Media? = null

    private var layout: VLCVideoLayout? = null
    private var viewsAttached = false

    private var prepared = false
    private var released = false
    private var subtitleLoaded = false
    private var mediaDescriptor: ParcelFileDescriptor? = null

    // Keadaan sebelum layar ditinggal, dipakai melanjutkan video setelah kembali.
    private var pausedPosition = 0L
    private var resumeOnReturn = false

    // ---------------------------------------------------------------- layout

    override fun attachHost(host: ViewGroup) {
        val newLayout = VLCVideoLayout(host.context)
        if (layout !== newLayout && viewsAttached) {
            // View baru dari Compose: surface lama tidak dipakai lagi.
            runCatching { player?.detachViews() }
            viewsAttached = false
        }
        layout = newLayout
        host.addView(
            newLayout,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        attachViewsIfPossible()
    }

    override fun releaseHost() {
        layout = null
        viewsAttached = false
        VideoPlayerLog.info("layout dilepas dari Compose")
    }

    private fun attachViewsIfPossible() {
        val player = player ?: return
        val layout = layout ?: return
        if (viewsAttached) return
        player.attachViews(layout, null, true, false)
        viewsAttached = true
        VideoPlayerLog.info("attachViews ${layout.width}x${layout.height}")
    }

    // ------------------------------------------------------------ pemutaran

    // Posisi awal dari riwayat, dibaca sekali saat prepare.
    private var pendingStartPosition = 0L

    override var startPositionMs: Long
        get() = pendingStartPosition
        set(value) { pendingStartPosition = value.coerceAtLeast(0L) }

    override suspend fun prepare() {
        if (released) return
        if (prepared) {
            // Rotasi atau layar dipasang ulang: pakai pemutar yang sama.
            VideoPlayerLog.info("init ulang, pemutar dipakai lagi; uri=$uri")
            withContext(Dispatchers.Main) { attachViewsIfPossible() }
            return
        }
        prepared = true
        VideoPlayerLog.info("libvlc init uri=$uri scheme=${uri.scheme}")

        state.update {
            it.copy(
                isLoading = true,
                isReady = false,
                error = null,
                title = uri.name ?: globalClass.getString(R.string.unknown)
            )
        }

        try {
            withContext(Dispatchers.IO) {
                // Library native harus dimuat sebelum method native apa pun.
                LibVLC.loadLibraries()
                val vlc = LibVLC(context, arrayListOf("--no-video-title-show"))
                VideoPlayerLog.info(
                    "libvlc ${runCatching { LibVLC.version() }.getOrDefault("?")}"
                )
                val newPlayer = MediaPlayer(vlc)
                val newMedia = createMedia(vlc, uri).apply { setHWDecoderEnabled(true, false) }

                if (released) {
                    newMedia.release()
                    newPlayer.release()
                    vlc.release()
                    releaseMediaDescriptor()
                    return@withContext
                }

                libVLC = vlc
                player = newPlayer
                media = newMedia
                VideoPlayerLog.info("media siap, panjang=${newMedia.duration} ms")
            }

            if (released) return

            withContext(Dispatchers.Main) {
                val activePlayer = player ?: return@withContext
                attachViewsIfPossible()
                activePlayer.media = media
                activePlayer.setEventListener { event -> handleEvent(event) }
                VideoPlayerLog.info("play() dipanggil")
                activePlayer.play()
                // Lanjut dari posisi terakhir bila riwayat menyimpannya.
                val start = startPositionMs
                if (start > 0) {
                    activePlayer.time = start
                    VideoPlayerLog.info("libvlc mulai dari posisi $start")
                }
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

    private fun createMedia(vlc: LibVLC, uri: Uri): Media {
        if (uri.scheme != "content") {
            VideoPlayerLog.info("media dari lokasi ${uri.scheme}")
            return Media(vlc, uri)
        }
        val descriptor = globalClass.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("openFileDescriptor mengembalikan null untuk $uri")
        mediaDescriptor = descriptor
        VideoPlayerLog.info("media dari fd stat=${runCatching { descriptor.statSize }.getOrDefault(-1L)}")
        return Media(vlc, descriptor.fileDescriptor)
    }

    private fun releaseMediaDescriptor() {
        runCatching { mediaDescriptor?.close() }
        mediaDescriptor = null
    }

    // ---------------------------------------------------------------- status

    private fun handleEvent(event: MediaPlayer.Event) {
        if (released) return
        val player = player ?: return
        when (event.type) {
            MediaPlayer.Event.Opening ->
                state.update { it.copy(isLoading = true, error = null) }

            MediaPlayer.Event.Playing -> {
                VideoPlayerLog.info("Playing length=${player.length}")
                state.update {
                    it.copy(
                        isPlaying = true,
                        isLoading = false,
                        isReady = true,
                        isEnded = false,
                        error = null,
                        duration = player.length
                    )
                }
                loadInitialSubtitleOnce()
                refreshTracks()
            }

            MediaPlayer.Event.Paused ->
                state.update { it.copy(isPlaying = false) }

            MediaPlayer.Event.Stopped ->
                state.update { it.copy(isPlaying = false) }

            MediaPlayer.Event.EndReached -> {
                VideoPlayerLog.info("EndReached")
                state.update {
                    it.copy(
                        isPlaying = false,
                        isEnded = true,
                        currentPosition = it.duration,
                        showControls = true
                    )
                }
            }

            MediaPlayer.Event.TimeChanged ->
                state.update { it.copy(currentPosition = event.timeChanged) }

            MediaPlayer.Event.LengthChanged ->
                state.update {
                    it.copy(duration = event.lengthChanged.takeIf { value -> value > 0 } ?: it.duration)
                }

            MediaPlayer.Event.Buffering ->
                state.update { it.copy(isLoading = event.buffering < 100f) }

            MediaPlayer.Event.Vout -> {
                VideoPlayerLog.info("vout muncul attached=$viewsAttached")
                state.update { it.copy(isReady = true) }
            }

            MediaPlayer.Event.EncounteredError -> {
                VideoPlayerLog.error("EncounteredError")
                state.update {
                    it.copy(
                        isPlaying = false,
                        isLoading = false,
                        error = globalClass.getString(R.string.video_playback_failed)
                    )
                }
            }

            MediaPlayer.Event.ESAdded,
            MediaPlayer.Event.ESDeleted,
            MediaPlayer.Event.ESSelected -> refreshTracks()
        }
    }

    private fun startPositionTracking() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (!released) {
                player?.let { active ->
                    if (active.isPlaying) {
                        state.update {
                            it.copy(
                                currentPosition = active.time,
                                duration = active.length.takeIf { len -> len > 0 } ?: it.duration
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
        if (!state.value.isMuted) player.volume = 100
        resumeOnReturn = false

        if (state.value.isEnded) {
            // Setelah EndReached input sudah dilepas; stop() lalu play() memuat ulang.
            VideoPlayerLog.info("playPause: ulang dari awal")
            runCatching { player.stop() }
            runCatching { player.play() }
            state.update {
                it.copy(isEnded = false, isPlaying = true, isLoading = true, currentPosition = 0)
            }
            return
        }

        VideoPlayerLog.info("playPause playing=${player.isPlaying}")
        if (player.isPlaying) player.pause() else player.play()
    }

    override fun pause() {
        val player = player ?: return
        val wasPlaying = runCatching { player.isPlaying }.getOrDefault(false)
        pausedPosition = runCatching { player.time }.getOrDefault(0L).coerceAtLeast(0L)
        resumeOnReturn = wasPlaying
        VideoPlayerLog.info("pause volume=${player.volume} playing=$wasPlaying pos=$pausedPosition")
        runCatching { player.setVolume(0) }
        runCatching { player.pause() }
    }

    override fun resumeAfterReturn() {
        val player = player ?: return
        val shouldResume = resumeOnReturn
        val target = pausedPosition
        resumeOnReturn = false
        if (!shouldResume) {
            VideoPlayerLog.info("resume dilewati, video tadi dijeda pengguna")
            return
        }
        VideoPlayerLog.info("resume pos=$target playing=${player.isPlaying} attached=$viewsAttached")
        if (!state.value.isMuted) player.volume = 100
        attachViewsIfPossible()
        player.play()
        player.setTime(target)
        state.update { it.copy(isPlaying = true, isEnded = false, currentPosition = target) }
    }

    override fun stopPlayback() {
        val player = player ?: return
        VideoPlayerLog.info("stopPlayback playing=${runCatching { player.isPlaying }.getOrNull()}")
        runCatching { player.setVolume(0) }
        runCatching { player.setAudioTrack(-1) }
        runCatching { player.stop() }
    }

    override fun seekTo(position: Long) {
        val player = player ?: return
        val duration = state.value.duration
        if (state.value.isEnded) {
            runCatching { player.stop() }
            runCatching { player.play() }
        }
        player.setTime(position.coerceIn(0, if (duration > 0) duration else position))
        state.update { it.copy(currentPosition = position, isEnded = false, isPlaying = true) }
    }

    override fun setSpeed(speed: Float) {
        val safe = speed.coerceIn(0.25f, 4f)
        VideoPlayerLog.info("setSpeed $safe")
        player?.rate = safe
        state.update { it.copy(playbackSpeed = safe) }
    }

    override fun setMuted(muted: Boolean) {
        player?.volume = if (muted) 0 else 100
        state.update { it.copy(isMuted = muted) }
    }

    // ------------------------------------------------------------ track

    override fun refreshTracks() {
        val player = player ?: return
        runCatching {
            val audioTracks = player.audioTracks
                ?.map { VideoTrackInfo(it.id, it.name ?: "Audio ${it.id}") }
                .orEmpty()
            val subtitleTracks = player.spuTracks
                ?.map { VideoTrackInfo(it.id, it.name ?: "Subtitle ${it.id}") }
                .orEmpty()
            VideoPlayerLog.info(
                "track audio=${audioTracks.size} subtitle=${subtitleTracks.size} " +
                        "aktif=${player.audioTrack}/${player.spuTrack}"
            )
            state.update {
                it.copy(
                    audioTracks = audioTracks,
                    subtitleTracks = subtitleTracks,
                    selectedAudioTrack = player.audioTrack,
                    selectedSubtitleTrack = player.spuTrack
                )
            }
        }
    }

    override fun selectAudioTrack(trackId: Int) {
        player?.audioTrack = trackId
        refreshTracks()
    }

    override fun selectSubtitleTrack(trackId: Int) {
        // "Tidak ada" juga membuang simpanan, sama seperti varian Media3.
        if (trackId < 0) SubtitleFiles.clearFor(uri)
        player?.spuTrack = trackId
        refreshTracks()
    }

    override fun addSubtitle(uri: Uri) {
        val player = player ?: return
        val videoUri = this.uri
        scope.launch {
            val stored = withContext(Dispatchers.IO) { SubtitleFiles.saveFor(videoUri, uri) }
            if (stored == null) {
                VideoPlayerLog.error("subtitle gagal disalin dari $uri")
                state.update {
                    it.copy(error = globalClass.getString(R.string.video_subtitle_failed))
                }
                return@launch
            }
            runCatching {
                player.addSlave(
                    org.videolan.libvlc.interfaces.IMedia.Slave.Type.Subtitle,
                    Uri.fromFile(stored),
                    true
                )
            }.onSuccess {
                VideoPlayerLog.info("subtitle ditambahkan ${stored.absolutePath}")
                refreshTracks()
            }.onFailure { VideoPlayerLog.error(it) }
        }
    }

    /**
     * Memuat subtitle awal sekali per pemutaran. Subtitle yang pernah dipilih
     * untuk video ini didahulukan, baru kemudian subtitle bersebelahan nama.
     */
    private fun loadInitialSubtitleOnce() {
        if (subtitleLoaded) return
        subtitleLoaded = true
        val player = player ?: return
        val saved = SubtitleFiles.savedFor(uri)
        val candidate = saved ?: SubtitleFiles.siblingOf(uri) ?: return
        runCatching {
            player.addSlave(
                org.videolan.libvlc.interfaces.IMedia.Slave.Type.Subtitle,
                Uri.fromFile(candidate),
                true
            )
            VideoPlayerLog.info(
                "subtitle awal ${if (saved != null) "tersimpan" else "bersebelahan"} ${candidate.name}"
            )
        }
    }

    // ---------------------------------------------------------------- skala

    override fun setScaleMode(mode: VideoScaleMode) {
        player?.videoScale = when (mode) {
            VideoScaleMode.BEST_FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
            VideoScaleMode.FIT_SCREEN -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            VideoScaleMode.FILL -> MediaPlayer.ScaleType.SURFACE_FILL
            VideoScaleMode.ORIGINAL -> MediaPlayer.ScaleType.SURFACE_ORIGINAL
        }
        state.update { it.copy(scaleMode = mode) }
    }

    // ----------------------------------------------------------------- tutup

    override fun release() {
        VideoPlayerLog.info(
            "libvlc release uri=$uri playing=${runCatching { player?.isPlaying }.getOrNull()}"
        )
        released = true
        positionJob?.cancel()
        positionJob = null

        player?.let { active ->
            runCatching { active.setEventListener(null) }
            runCatching { active.setVolume(0) }
            runCatching { active.setAudioTrack(-1) }
            runCatching { active.pause() }
            runCatching { active.stop() }
        }
        if (viewsAttached) {
            runCatching { player?.detachViews() }
            viewsAttached = false
        }
        player?.let { active ->
            val start = System.currentTimeMillis()
            runCatching { active.release() }
            VideoPlayerLog.info("player.release selesai ${System.currentTimeMillis() - start} ms")
        }
        runCatching { media?.release() }
        runCatching { libVLC?.release() }
        releaseMediaDescriptor()
        player = null
        media = null
        libVLC = null
        layout = null
        VideoPlayerLog.info("libvlc release selesai")
    }
}
