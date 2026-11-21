package com.rout.navi.screen.viewer.video.engine

import android.net.Uri
import android.view.ViewGroup
import com.rout.navi.screen.viewer.video.model.VideoScaleMode

/**
 * Kontrak satu mesin pemutar.
 *
 * Pemutar video memakai Media3 ExoPlayer lebih dulu karena perpindahan
 * kecepatan di sana memakai pemroses tempo yang tidak membuang buffer audio.
 * Bila berkasnya tidak didukung ExoPlayer, pemutar berpindah ke libVLC supaya
 * cakupan formatnya tetap seluas VLC.
 *
 * Semua mesin menulis ke state yang sama, jadi lapisan Compose tidak perlu tahu
 * mesin mana yang sedang dipakai.
 */
interface PlaybackEngine {

    /** Nama mesin, dipakai untuk log. */
    val name: String

    val uri: Uri

    /** Memasang view video ke host milik Compose. */
    fun attachHost(host: ViewGroup)

    /**
     * Melepas referensi host tanpa memutus surface video. Compose bisa membuang
     * view saat layar dibangun ulang; memutus surface di sini membuat gambar
     * tidak kembali walau suaranya masih jalan.
     */
    fun releaseHost()

    suspend fun prepare()

    /**
     * Posisi awal sebelum media dimuat. Diterapkan sekali saat [prepare],
     * sehingga tidak ada permintaan seek yang mengganggu buffer awal.
     */
    var startPositionMs: Long
        get() = 0L
        set(_) {}

    fun playPause()

    fun pause()

    fun resumeAfterReturn()

    fun stopPlayback()

    fun seekTo(position: Long)

    fun setSpeed(speed: Float)

    fun setMuted(muted: Boolean)

    fun refreshTracks()

    fun selectAudioTrack(trackId: Int)

    fun selectSubtitleTrack(trackId: Int)

    fun addSubtitle(uri: Uri)

    fun setScaleMode(mode: VideoScaleMode)

    fun release()
}
