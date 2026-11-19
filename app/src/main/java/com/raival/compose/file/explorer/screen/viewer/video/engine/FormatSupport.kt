package com.raival.compose.file.explorer.screen.viewer.video.engine

import android.net.Uri
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType

/** Mesin yang dipilih untuk satu berkas. */
enum class EngineChoice { MEDIA3, VLC }

/**
 * Gerbang format berkas.
 *
 * Media3 hanya dipakai untuk kontainer yang memang dikenali extractor-nya,
 * supaya berkas yang jelas butuh libVLC tidak dicoba dua kali. Berkas di luar
 * tabel tetap masuk Media3 sebagai percobaan pertama, lalu boleh pindah ke
 * libVLC bila ternyata gagal.
 *
 * Tabel mengacu pada Media3 1.11.1 (19 November 2026). Tinjau ulang setiap
 * dependensi media3 dinaikkan.
 */
object FormatSupport {

    // Kontainer video yang extractor Media3 kenali. Codec tetap bergantung
    // decoder perangkat, jadi kegagalan codec masih ditangani gerbang codec
    // di Media3Engine.
    private val MEDIA3_VIDEO = setOf(
        "mp4", "m4v", "mov", "3gp", "3g2", "3gp2", "3gpp",
        "mkv", "webm", "ts", "m2ts", "f4v",
        "h264", "h265", "hevc", "amr"
    )

    // Kontainer audio yang extractor Media3 kenali.
    private val MEDIA3_AUDIO = setOf(
        "mp3", "mp2", "mp1", "mpga", "mpa",
        "aac", "adts", "adt", "acc",
        "m4a", "m4b", "m4r", "m4p", "4mp",
        "flac", "wav", "w64",
        "ogg", "oga", "opus", "spx",
        "aif", "aifc", "aiff", "caf",
        "mka"
    )

    private val MEDIA3_EXTENSIONS = MEDIA3_VIDEO + MEDIA3_AUDIO

    /**
     * Ekstensi media yang dikenali aplikasi tetapi tidak masuk daftar Media3.
     * Diturunkan dari [FileMimeType] supaya tabel ikut bertambah ketika daftar
     * ekstensi di sana diperluas.
     */
    val VLC_ONLY: Set<String> =
        (FileMimeType.videoFileType.asSequence() + FileMimeType.audioFileType.asSequence())
            .map { it.lowercase() }
            .filterNot { it in MEDIA3_EXTENSIONS }
            .toSet()

    /**
     * Ekstensi yang tidak dikenali tetap dicoba di Media3 lebih dulu agar
     * berkas dari document provider tanpa nama tetap punya kesempatan diputar
     * dengan percepatan yang mulus.
     */
    fun choose(uri: Uri): EngineChoice {
        val extension = extensionOf(uri) ?: return EngineChoice.MEDIA3
        return if (extension in VLC_ONLY) EngineChoice.VLC else EngineChoice.MEDIA3
    }

    /** Ekstensi huruf kecil tanpa titik, atau null bila tidak bisa dibaca. */
    fun extensionOf(uri: Uri): String? {
        // lastPathSegment sudah di-decode, jadi nama berkas asli terbaca.
        val name = uri.lastPathSegment ?: uri.path ?: return null
        val cleaned = name.substringAfterLast('/')
        val dot = cleaned.lastIndexOf('.')
        if (dot <= 0 || dot == cleaned.lastIndex) return null
        return cleaned.substring(dot + 1).lowercase()
    }
}
