package com.rout.navi.screen.viewer.video.engine

import android.net.Uri
import com.rout.navi.App.Companion.globalClass
import java.io.File

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

    /** Menyalin subtitle pilihan pengguna ke cache supaya bisa dibaca mesin. */
    fun copyToCache(uri: Uri): File? = runCatching {
        val folder = File(globalClass.cacheDir, "subtitles").apply { mkdirs() }
        val name = uri.lastPathSegment ?: "subtitle"
        val target = File(folder, "${System.currentTimeMillis()}_$name")
        globalClass.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        target
    }.getOrNull()

    /** Tipe MIME subtitle berdasarkan ekstensi, dipakai Media3. */
    fun mimeTypeOf(file: File): String = when (file.extension.lowercase()) {
        "srt" -> "application/x-subrip"
        "ass", "ssa" -> "text/x-ssa"
        "vtt" -> "text/vtt"
        "ttml" -> "application/ttml+xml"
        "smi" -> "application/x-sami"
        else -> "application/x-subrip"
    }
}
