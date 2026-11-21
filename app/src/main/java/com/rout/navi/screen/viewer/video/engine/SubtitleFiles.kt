package com.rout.navi.screen.viewer.video.engine

import android.net.Uri
import com.rout.navi.App.Companion.globalClass
import java.io.File
import java.security.MessageDigest

/**
 * Berkas subtitle yang dipakai bersama oleh mesin Media3 dan libVLC.
 */
object SubtitleFiles {

    /** Ekstensi subtitle yang dikenali kedua mesin. */
    private val EXTENSIONS = listOf("srt", "ass", "ssa", "vtt", "smi", "ttml")

    /** Subtitle yang namanya sama dengan video dan berada di folder yang sama. */
    fun siblingOf(videoUri: Uri): File? {
        val path = videoUri.path ?: return null
        val videoFile = runCatching { File(path) }.getOrNull() ?: return null
        val parent = videoFile.parentFile ?: return null
        val base = videoFile.nameWithoutExtension
        return EXTENSIONS.map { File(parent, "$base.$it") }.firstOrNull { it.exists() }
    }

    /**
     * Menyimpan subtitle pilihan pengguna supaya melekat pada videonya.
     * Berkas diletakkan di penyimpanan aplikasi, bukan cache, karena cache
     * bisa dibersihkan Android kapan saja.
     */
    fun saveFor(videoUri: Uri, subtitleUri: Uri): File? = runCatching {
        // Satu video satu subtitle simpanan; yang lama dibuang lebih dulu.
        clearFor(videoUri)
        val target = File(storeFolder(), "${keyOf(videoUri)}_${safeName(subtitleUri)}")
        val input = globalClass.contentResolver.openInputStream(subtitleUri) ?: return null
        input.use { source ->
            target.outputStream().use { output -> source.copyTo(output) }
        }
        // Berkas kosong sama saja gagal bagi kedua mesin.
        if (target.length() == 0L) {
            target.delete()
            null
        } else {
            target
        }
    }.getOrNull()

    /** Subtitle yang pernah dipilih untuk video ini. */
    fun savedFor(videoUri: Uri): File? {
        val prefix = "${keyOf(videoUri)}_"
        return storeFolder().listFiles()
            ?.firstOrNull { it.isFile && it.name.startsWith(prefix) }
    }

    /** Membuang subtitle simpanan, dipakai saat pengguna memilih "Tidak ada". */
    fun clearFor(videoUri: Uri) {
        val prefix = "${keyOf(videoUri)}_"
        storeFolder().listFiles()
            ?.filter { it.name.startsWith(prefix) }
            ?.forEach { it.delete() }
    }

    /** Tipe MIME subtitle berdasarkan ekstensi, dipakai Media3. */
    fun mimeTypeOf(file: File): String = when (file.extension.lowercase()) {
        "srt" -> "application/x-subrip"
        "ass", "ssa" -> "text/x-ssa"
        "vtt" -> "text/vtt"
        "ttml" -> "application/ttml+xml"
        "smi" -> "application/x-sami"
        else -> "application/x-subrip"
    }

    private fun storeFolder(): File =
        File(globalClass.filesDir, "subtitles").apply { mkdirs() }

    /** Penanda video dari URI-nya, dipakai sebagai awalan nama berkas. */
    private fun keyOf(videoUri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(videoUri.toString().toByteArray())
        return digest.take(8)
            .joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    /**
     * Nama aman dari content URI penyimpanan. URI-nya membawa path lengkap
     * seperti "primary:Documents/Episode 729/file.srt", jadi garis miring harus
     * dibuang lebih dulu atau folder tujuan tidak pernah ada.
     */
    private fun safeName(uri: Uri): String {
        val raw = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() }
            ?: "subtitle.srt"
        val cleaned = raw.replace(Regex("[^A-Za-z0-9._ ()-]"), "_")
        val extension = cleaned.substringAfterLast('.', "").lowercase()
        return if (extension in EXTENSIONS) cleaned else "$cleaned.srt"
    }
}
