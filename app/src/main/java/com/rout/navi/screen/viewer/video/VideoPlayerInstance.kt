package com.rout.navi.screen.viewer.video

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.rout.navi.App.Companion.globalClass
import com.rout.navi.R
import com.rout.navi.screen.viewer.ViewerInstance
import com.rout.navi.screen.viewer.video.engine.EngineChoice
import com.rout.navi.screen.viewer.video.engine.EngineFactory
import com.rout.navi.screen.viewer.video.engine.EngineFactoryProvider
import com.rout.navi.screen.viewer.video.engine.FormatSupport
import com.rout.navi.screen.viewer.video.engine.Media3Engine
import com.rout.navi.screen.viewer.video.engine.PlaybackEngine
import com.rout.navi.screen.viewer.video.model.VideoPlayerState
import com.rout.navi.screen.viewer.video.model.VideoScaleMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Pemutar video yang memilih mesinnya sendiri.
 *
 * Media3 ExoPlayer dipakai lebih dulu supaya percepatan 2x mulus seperti
 * YouTube. Bila berkasnya tidak didukung ExoPlayer, pemutar berpindah ke
 * libVLC yang cakupan formatnya seluas aplikasi VLC.
 *
 * Mesin mana yang tersedia ditentukan [engineFactory], yang isinya berbeda per
 * varian aplikasi. Lapisan Compose hanya berbicara dengan kelas ini, jadi
 * perilaku UI sama untuk semua mesin.
 */
class VideoPlayerInstance(
    override val uri: Uri,
    override val id: String
) : ViewerInstance {

    private val _playerState = MutableStateFlow(VideoPlayerState())
    val playerState: StateFlow<VideoPlayerState> = _playerState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Perpindahan mesin tidak boleh berjalan di dalam callback ExoPlayer;
    // pelepasan pemutar dari dalam callback bisa mengganggu antrean event-nya.
    private val mainHandler = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var host: ViewGroup? = null

    private var engine: PlaybackEngine? = null
    private var closed = false

    /**
     * Perkakas mesin sesuai varian aplikasi. Varian dipilih Gradle lewat
     * [EngineFactoryProvider], sehingga kode bersama ini tidak merujuk kelas
     * libVLC sama sekali. Kelas libVLC hanya ada di varian vlc.
     */
    private val engineFactory: EngineFactory = EngineFactoryProvider.get()

    // Gerbang format: berkas yang jelas hanya bisa diputar libVLC tidak
    // dilewatkan Media3 sama sekali.
    private val engineChoice = FormatSupport.choose(uri)

    // Mesin dipilih sekali. Setelah terkunci, hanya boleh berpindah satu arah
    // dari Media3 ke libVLC, dan hanya sekali.
    private var engineLocked = false
    private var useVlcOnly = engineChoice == EngineChoice.VLC
    private var switchInProgress = false

    private var audioManager: AudioManager? = null

    // ---------------------------------------------------------------- layout

    /**
     * Dipanggil dari factory AndroidView. Mesin memasang view videonya ke host
     * ini; kalau mesin belum dibuat, host disimpan dan dipasangi nanti.
     */
    fun attachHost(newHost: ViewGroup) {
        if (host === newHost) return
        host = newHost
        // Selama mesin belum ada, host cukup disimpan. Setelah terkunci,
        // hanya mesin pemegang kunci yang boleh memasang view.
        if (engineLocked) engine?.attachHost(newHost)
    }

    fun releaseHost() {
        if (engineLocked) engine?.releaseHost()
        host = null
    }

    // ------------------------------------------------------------ pemutaran

    suspend fun initializePlayer(context: Context, uri: Uri) {
        if (closed) return
        appContext = context.applicationContext
        audioManager = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        if (engine != null) {
            // Rotasi atau layar dipasang ulang: mesin yang sama dipakai lagi.
            VideoPlayerLog.info("init ulang, mesin ${engine?.name} dipakai lagi")
        }
        engine?.prepare() ?: run {
            val created = ensureEngine() ?: return
            created.prepare()
        }
    }

    private fun ensureEngine(): PlaybackEngine? {
        engine?.let { return it }
        val context = appContext ?: return null
        val created = createEngine(context) ?: return null
        engine = created
        engineLocked = true
        VideoPlayerLog.info(
            "mesin dipilih: ${
                if (created is Media3Engine) "media3" else engineFactory.fallbackName
            } (ekstensi=${FormatSupport.extensionOf(uri) ?: "tidak ada"})"
        )
        host?.let { created.attachHost(it) }
        return created
    }

    /**
     * Mesin awal untuk berkas ini. Hasil `null` berarti formatnya hanya bisa
     * diputar mesin cadangan, tetapi varian ini tidak membawanya.
     */
    private fun createEngine(context: Context): PlaybackEngine? =
        if (useVlcOnly) {
            engineFactory.createFallback(context, uri, _playerState) ?: run {
                showEngineUnavailable()
                null
            }
        } else {
            engineFactory.createMedia3(context, uri, _playerState) {
                mainHandler.post { scope.launch { switchToVlc() } }
            }
        }

    /** Pesan untuk berkas yang butuh mesin cadangan yang tidak ikut dikemas. */
    private fun showEngineUnavailable() {
        VideoPlayerLog.info("format butuh mesin cadangan, varian ini tidak membawanya")
        _playerState.update {
            it.copy(
                isLoading = false,
                isReady = false,
                isPlaying = false,
                error = globalClass.getString(R.string.video_format_needs_full_version)
            )
        }
    }

    /** Memindahkan pemutaran ke libVLC setelah Media3 menyerah. */
    private suspend fun switchToVlc() {
        if (closed) return
        // Penjaga bentrok: satu perpindahan saja, dan hanya dari Media3.
        if (switchInProgress) return
        if (useVlcOnly) return
        if (engine !is Media3Engine && engine != null) return
        // Varian ringan tidak punya cadangan; beri pesan alih-alih gagal diam.
        val context = appContext ?: return
        if (engineFactory.createFallback(context, uri, _playerState) == null) {
            engine?.release()
            engine = null
            host?.removeAllViews()
            showEngineUnavailable()
            return
        }
        switchInProgress = true
        useVlcOnly = true
        VideoPlayerLog.info("pindah mesin: media3 ke ${engineFactory.fallbackName}")

        engine?.release()
        engine = null
        host?.removeAllViews()

        _playerState.update {
            it.copy(
                isLoading = true,
                isReady = false,
                isPlaying = false,
                isEnded = false,
                error = null,
                currentPosition = 0L,
                audioTracks = emptyList(),
                subtitleTracks = emptyList(),
                selectedAudioTrack = -1,
                selectedSubtitleTrack = -1
            )
        }

        val fallback = engineFactory.createFallback(context, uri, _playerState)
        if (fallback == null) {
            showEngineUnavailable()
            switchInProgress = false
            return
        }
        engine = fallback
        host?.let { fallback.attachHost(it) }
        fallback.prepare()
        // Kecepatan dan mute yang sedang aktif dibawa ke mesin baru.
        _playerState.value.playbackSpeed.let { if (it != 1f) fallback.setSpeed(it) }
        if (_playerState.value.isMuted) fallback.setMuted(true)
    }

    // ------------------------------------------------------------- perintah

    fun playPause() {
        engine?.playPause()
    }

    /** Jeda sekaligus mengingat posisi, dipakai saat layar ditinggal. */
    fun pause() {
        engine?.pause()
    }

    /** Melanjutkan video di posisi terakhir setelah layar kembali aktif. */
    fun resumeAfterReturn() {
        engine?.resumeAfterReturn()
    }

    /** Dipakai saat activity benar-benar ditutup. */
    fun stopPlayback() {
        engine?.stopPlayback()
    }

    fun seekTo(position: Long) {
        engine?.seekTo(position)
    }

    fun seekBy(delta: Long) {
        val state = _playerState.value
        seekTo((state.currentPosition + delta).coerceIn(0, state.duration))
    }

    fun setSpeed(speed: Float) {
        engine?.setSpeed(speed)
    }

    fun toggleMute() {
        val muted = !_playerState.value.isMuted
        engine?.setMuted(muted)
    }

    fun toggleControls() {
        _playerState.update { it.copy(showControls = !it.showControls) }
    }

    fun toggleLock() {
        _playerState.update { it.copy(isLocked = !it.isLocked, showControls = false) }
    }

    // ------------------------------------------------------------ track & lainnya

    fun refreshTracks() {
        engine?.refreshTracks()
    }

    fun selectAudioTrack(trackId: Int) {
        engine?.selectAudioTrack(trackId)
    }

    fun selectSubtitleTrack(trackId: Int) {
        engine?.selectSubtitleTrack(trackId)
    }

    fun addSubtitleFromUri(uri: Uri) {
        engine?.addSubtitle(uri)
    }

    // ---------------------------------------------------------------- skala

    fun setScaleMode(mode: VideoScaleMode) {
        engine?.setScaleMode(mode)
    }

    fun cycleScaleMode() {
        val modes = VideoScaleMode.entries
        val next = modes[(modes.indexOf(_playerState.value.scaleMode) + 1) % modes.size]
        setScaleMode(next)
    }

    fun retry() {
        scope.launch {
            VideoPlayerLog.info("retry diminta, mesin ${engine?.name}")
            engine?.release()
            engine = null
            host?.removeAllViews()
            _playerState.update {
                VideoPlayerState(title = it.title, scaleMode = it.scaleMode)
            }
            ensureEngine()?.prepare()
        }
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
        VideoPlayerLog.info("onClose uri=$uri mesin=${engine?.name}")
        closed = true
        engine?.release()
        engine = null
        host = null
        scope.cancel()
        VideoPlayerLog.info("onClose selesai")
    }
}
