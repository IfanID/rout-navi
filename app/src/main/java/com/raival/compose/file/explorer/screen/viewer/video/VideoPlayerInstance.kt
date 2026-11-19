package com.raival.compose.file.explorer.screen.viewer.video

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.name
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import com.raival.compose.file.explorer.screen.viewer.video.model.VideoPlayerState
import com.raival.compose.file.explorer.screen.viewer.video.model.VideoScaleMode
import com.raival.compose.file.explorer.screen.viewer.video.model.VideoTrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.File

/**
 * Mesin pemutar video berbasis libVLC.
 *
 * Urutan pemasangan mengikuti VLC android: layout video dipasang lebih dulu
 * lewat attachViews(layout), lalu media dimuat dan diputar setelahnya. Urutan
 * terbalik membuat libVLC mencoba membuka output video berulang kali tanpa
 * tujuan dan layar jadi hitam.
 */
class VideoPlayerInstance(
    override val uri: Uri,
    override val id: String
) : ViewerInstance {

    private val _playerState = MutableStateFlow(VideoPlayerState())
    val playerState: StateFlow<VideoPlayerState> = _playerState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionJob: Job? = null

    private var libVLC: LibVLC? = null
    private var player: MediaPlayer? = null
    private var media: Media? = null

    private var layout: VLCVideoLayout? = null
    private var viewsAttached = false

    private var prepared = false
    private var closed = false
    private var subtitleLoaded = false
    private var mediaDescriptor: ParcelFileDescriptor? = null

    private var audioManager: AudioManager? = null

    // Keadaan sebelum layar ditinggal, dipakai melanjutkan video setelah kembali.
    private var pausedPosition = 0L
    private var resumeOnReturn = false

    // ---------------------------------------------------------------- layout

    /**
     * Dipanggil dari factory AndroidView. Layout langsung dipasang ke pemutar
     * yang sudah ada; kalau pemutar belum siap, layout disimpan dan dipasang
     * saat pemutar dibuat.
     */
    fun attachLayout(layout: VLCVideoLayout) {
        if (this.layout !== layout && viewsAttached) {
            // View baru dari Compose: surface lama tidak dipakai lagi.
            runCatching { player?.detachViews() }
            viewsAttached = false
        }
        this.layout = layout
        attachViewsIfPossible()
    }

    /**
     * Melepas referensi layout tanpa memutus surface dari pemutar. Compose bisa
     * membuang view saat layar dibangun ulang; memutus surface di sini membuat
     * jalur video libVLC hilang dan gambar tidak kembali walau suara tetap ada.
     */
    fun releaseLayout(layout: VLCVideoLayout) {
        if (this.layout !== layout) return
        this.layout = null
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

    suspend fun initializePlayer(context: Context, uri: Uri) {
        if (closed) return
        if (prepared) {
            // Rotasi atau layar dipasang ulang: pakai pemutar yang sama.
            VideoPlayerLog.info("init ulang, pemutar dipakai lagi; uri=$uri")
            withContext(Dispatchers.Main) { attachViewsIfPossible() }
            return
        }
        prepared = true
        VideoPlayerLog.info("init uri=$uri scheme=${uri.scheme}")

        _playerState.update {
            it.copy(
                isLoading = true,
                isReady = false,
                error = null,
                title = uri.name ?: globalClass.getString(R.string.unknown)
            )
        }

        val appContext = context.applicationContext
        audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        try {
            withContext(Dispatchers.IO) {
                // Library native harus dimuat sebelum method native apa pun.
                LibVLC.loadLibraries()
                val vlc = LibVLC(appContext, arrayListOf("--no-video-title-show"))
                VideoPlayerLog.info(
                    "libvlc ${runCatching { LibVLC.version() }.getOrDefault("?")}"
                )
                val newPlayer = MediaPlayer(vlc)
                val newMedia = createMedia(vlc, uri).apply { setHWDecoderEnabled(true, false) }

                if (closed) {
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

            if (closed) return

            withContext(Dispatchers.Main) {
                val activePlayer = player ?: return@withContext
                attachViewsIfPossible()
                activePlayer.media = media
                activePlayer.setEventListener { event -> handleEvent(event) }
                VideoPlayerLog.info("play() dipanggil")
                activePlayer.play()
            }
            startPositionTracking()
        } catch (e: Throwable) {
            VideoPlayerLog.error(e)
            _playerState.update {
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
        if (closed) return
        val player = player ?: return
        when (event.type) {
            MediaPlayer.Event.Opening ->
                _playerState.update { it.copy(isLoading = true, error = null) }

            MediaPlayer.Event.Playing -> {
                VideoPlayerLog.info("Playing length=${player.length}")
                _playerState.update {
                    it.copy(
                        isPlaying = true,
                        isLoading = false,
                        isReady = true,
                        isEnded = false,
                        error = null,
                        duration = player.length
                    )
                }
                loadSiblingSubtitleOnce()
                refreshTracks()
            }

            MediaPlayer.Event.Paused ->
                _playerState.update { it.copy(isPlaying = false) }

            MediaPlayer.Event.Stopped ->
                _playerState.update { it.copy(isPlaying = false) }

            MediaPlayer.Event.EndReached -> {
                VideoPlayerLog.info("EndReached")
                _playerState.update {
                    it.copy(
                        isPlaying = false,
                        isEnded = true,
                        currentPosition = it.duration,
                        showControls = true
                    )
                }
            }

            MediaPlayer.Event.TimeChanged ->
                _playerState.update { it.copy(currentPosition = event.timeChanged) }

            MediaPlayer.Event.LengthChanged ->
                _playerState.update {
                    it.copy(duration = event.lengthChanged.takeIf { value -> value > 0 } ?: it.duration)
                }

            MediaPlayer.Event.Buffering ->
                _playerState.update { it.copy(isLoading = event.buffering < 100f) }

            MediaPlayer.Event.Vout -> {
                VideoPlayerLog.info("vout muncul attached=$viewsAttached")
                _playerState.update { it.copy(isReady = true) }
            }

            MediaPlayer.Event.EncounteredError -> {
                VideoPlayerLog.error("EncounteredError")
                _playerState.update {
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
            while (true) {
                player?.let { active ->
                    if (active.isPlaying) {
                        _playerState.update {
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

    fun playPause() {
        val player = player ?: return
        if (!_playerState.value.isMuted) player.volume = 100
        resumeOnReturn = false

        if (_playerState.value.isEnded) {
            // Setelah EndReached input sudah dilepas; stop() lalu play() memuat ulang.
            VideoPlayerLog.info("playPause: ulang dari awal")
            runCatching { player.stop() }
            runCatching { player.play() }
            _playerState.update {
                it.copy(isEnded = false, isPlaying = true, isLoading = true, currentPosition = 0)
            }
            return
        }

        VideoPlayerLog.info("playPause playing=${player.isPlaying}")
        if (player.isPlaying) player.pause() else player.play()
    }

    /** Jeda sekaligus mengingat posisi, dipakai saat layar ditinggal. */
    fun pause() {
        val player = player ?: return
        val wasPlaying = runCatching { player.isPlaying }.getOrDefault(false)
        pausedPosition = runCatching { player.time }.getOrDefault(0L).coerceAtLeast(0L)
        resumeOnReturn = wasPlaying
        VideoPlayerLog.info("pause volume=${player.volume} playing=$wasPlaying pos=$pausedPosition")
        runCatching { player.setVolume(0) }
        runCatching { player.pause() }
    }

    /** Melanjutkan video di posisi terakhir setelah layar kembali aktif. */
    fun resumeAfterReturn() {
        val player = player ?: return
        val shouldResume = resumeOnReturn
        val target = pausedPosition
        resumeOnReturn = false
        if (!shouldResume) {
            VideoPlayerLog.info("resume dilewati, video tadi dijeda pengguna")
            return
        }
        VideoPlayerLog.info("resume pos=$target playing=${player.isPlaying} attached=$viewsAttached")
        if (!_playerState.value.isMuted) player.volume = 100
        attachViewsIfPossible()
        player.play()
        player.setTime(target)
        _playerState.update { it.copy(isPlaying = true, isEnded = false, currentPosition = target) }
    }

    /** Dipakai saat activity benar-benar ditutup. */
    fun stopPlayback() {
        val player = player ?: return
        VideoPlayerLog.info("stopPlayback playing=${runCatching { player.isPlaying }.getOrNull()}")
        runCatching { player.setVolume(0) }
        runCatching { player.setAudioTrack(-1) }
        runCatching { player.stop() }
    }

    fun seekTo(position: Long) {
        val player = player ?: return
        val duration = _playerState.value.duration
        if (_playerState.value.isEnded) {
            runCatching { player.stop() }
            runCatching { player.play() }
        }
        player.setTime(position.coerceIn(0, if (duration > 0) duration else position))
        _playerState.update { it.copy(currentPosition = position, isEnded = false, isPlaying = true) }
    }

    fun seekBy(delta: Long) {
        val state = _playerState.value
        seekTo((state.currentPosition + delta).coerceIn(0, state.duration))
    }

    fun setSpeed(speed: Float) {
        val safe = speed.coerceIn(0.25f, 4f)
        VideoPlayerLog.info("setSpeed $safe")
        player?.rate = safe
        _playerState.update { it.copy(playbackSpeed = safe) }
    }

    fun toggleMute() {
        val player = player ?: return
        val muted = !_playerState.value.isMuted
        player.volume = if (muted) 0 else 100
        _playerState.update { it.copy(isMuted = muted) }
    }

    fun toggleControls() {
        _playerState.update { it.copy(showControls = !it.showControls) }
    }

    fun toggleLock() {
        _playerState.update { it.copy(isLocked = !it.isLocked, showControls = false) }
    }

    // ------------------------------------------------------------ track & lainnya

    fun refreshTracks() {
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
            _playerState.update {
                it.copy(
                    audioTracks = audioTracks,
                    subtitleTracks = subtitleTracks,
                    selectedAudioTrack = player.audioTrack,
                    selectedSubtitleTrack = player.spuTrack
                )
            }
        }
    }

    fun selectAudioTrack(trackId: Int) {
        player?.audioTrack = trackId
        refreshTracks()
    }

    fun selectSubtitleTrack(trackId: Int) {
        player?.spuTrack = trackId
        refreshTracks()
    }

    fun addSubtitleFromUri(uri: Uri) {
        val player = player ?: return
        scope.launch {
            val path = withContext(Dispatchers.IO) { copySubtitleToCache(uri) } ?: return@launch
            runCatching {
                player.addSlave(IMedia.Slave.Type.Subtitle, Uri.fromFile(File(path)), true)
            }.onSuccess {
                VideoPlayerLog.info("subtitle ditambahkan $path")
                refreshTracks()
            }.onFailure { VideoPlayerLog.error(it) }
        }
    }

    /** Memuat subtitle bersebelahan nama berkas, sekali saja per pemutaran. */
    private fun loadSiblingSubtitleOnce() {
        if (subtitleLoaded) return
        subtitleLoaded = true
        val player = player ?: return
        val videoFile = runCatching { File(uri.path ?: return) }.getOrNull() ?: return
        val base = videoFile.nameWithoutExtension
        val candidate = listOf("srt", "ass", "ssa", "vtt", "smi", "ttml")
            .map { File(videoFile.parentFile, "$base.$it") }
            .firstOrNull { it.exists() } ?: return
        runCatching {
            player.addSlave(IMedia.Slave.Type.Subtitle, Uri.fromFile(candidate), true)
            VideoPlayerLog.info("subtitle bersebelahan ${candidate.name}")
        }
    }

    private fun copySubtitleToCache(uri: Uri): String? = runCatching {
        val resolver = globalClass.contentResolver
        val folder = File(globalClass.cacheDir, "subtitles").apply { mkdirs() }
        val target = File(folder, "${System.currentTimeMillis()}_${uri.lastPathSegment ?: "subtitle"}")
        resolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target.absolutePath
    }.getOrNull()

    // ---------------------------------------------------------------- skala

    fun setScaleMode(mode: VideoScaleMode) {
        player?.videoScale = when (mode) {
            VideoScaleMode.BEST_FIT -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
            VideoScaleMode.FIT_SCREEN -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            VideoScaleMode.FILL -> MediaPlayer.ScaleType.SURFACE_FILL
            VideoScaleMode.ORIGINAL -> MediaPlayer.ScaleType.SURFACE_ORIGINAL
        }
        _playerState.update { it.copy(scaleMode = mode) }
    }

    fun cycleScaleMode() {
        val modes = VideoScaleMode.entries
        val next = modes[(modes.indexOf(_playerState.value.scaleMode) + 1) % modes.size]
        setScaleMode(next)
    }

    fun retry() {
        scope.launch { initializePlayer(globalClass, uri) }
    }

    // ---------------------------------------------------------------- volume

    fun systemVolumePercent(): Int = runCatching {
        val manager = audioManager ?: return@runCatching 0
        val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) 0 else manager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }.getOrDefault(0)

    fun setSystemVolumePercent(percent: Int) {
        runCatching {
            val manager = audioManager ?: return
            val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (percent.coerceIn(0, 100) * max / 100f).toInt()
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
    }

    // ----------------------------------------------------------------- tutup

    override fun onClose() {
        VideoPlayerLog.info("onClose uri=$uri playing=${runCatching { player?.isPlaying }.getOrNull()}")
        closed = true
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
        VideoPlayerLog.info("onClose selesai")
    }
}
