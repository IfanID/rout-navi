package com.raival.compose.file.explorer.screen.main.tab.files.task

/**
 * Format arsip yang bisa dibuat aplikasi. RAR sengaja tidak ada karena
 * junrar hanya mendukung pembacaan, bukan pembuatan.
 */
enum class CompressFormat(val extension: String) {
    ZIP("zip"),
    SEVEN_ZIP("7z"),
    TAR("tar");

    val displayName: String
        get() = when (this) {
            ZIP -> "ZIP"
            SEVEN_ZIP -> "7Z"
            TAR -> "TAR"
        }

    companion object {
        private val suffixes = listOf(
            ".tar.bz2", ".tar.gz", ".tar.xz", ".tbz2", ".tbz", ".txz",
            ".zip", ".7z", ".tar"
        )

        /** Membuang akhiran arsip, termasuk akhiran gabungan seperti .tar.gz. */
        fun removeSuffix(name: String): String {
            suffixes.forEach { suffix ->
                if (name.endsWith(suffix, ignoreCase = true)) {
                    return name.dropLast(suffix.length)
                }
            }
            return name
        }
    }
}
