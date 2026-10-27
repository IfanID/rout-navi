package com.raival.compose.file.explorer.screen.textEditor.language.subtitle

import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.assFileType
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.lrcFileType
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.srtFileType
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.ssaFileType
import com.raival.compose.file.explorer.screen.main.tab.files.misc.FileMimeType.vttFileType
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry

/**
 * Pembuat bahasa TextMate untuk berkas subtitle.
 *
 * Dibuat lewat factory, bukan kelas turunan, karena grammar dimuat dari aset dan
 * bisa gagal ditemukan. Kalau itu terjadi fungsi mengembalikan null supaya
 * pemanggil bisa jatuh ke [io.github.rosemoe.sora.lang.EmptyLanguage] (teks polos)
 * alih-alih membuat editor crash.
 */
object SubtitleCodeLanguage {
    const val SCOPE_SRT = "source.srt"
    const val SCOPE_VTT = "source.vtt"
    const val SCOPE_ASS = "source.ass"
    const val SCOPE_LRC = "source.lrc"

    /** Scope grammar khusus untuk sebuah ekstensi. Null berarti tanpa highlight. */
    fun scopeFor(extension: String): String? = when (extension) {
        srtFileType -> SCOPE_SRT
        vttFileType -> SCOPE_VTT
        assFileType, ssaFileType -> SCOPE_ASS
        lrcFileType -> SCOPE_LRC
        else -> null
    }

    fun create(scopeName: String): Language? {
        val registry = GrammarRegistry.getInstance()

        // Factory Sora melempar IllegalArgumentException kalau scope tidak ada,
        // jadi keberadaan grammar diperiksa lebih dulu.
        if (registry.findGrammar(scopeName) == null) return null

        return try {
            TextMateLanguage.create(
                scopeName,
                registry,
                ThemeRegistry.getInstance(),
                true
            )
        } catch (_: Exception) {
            null
        }
    }
}
