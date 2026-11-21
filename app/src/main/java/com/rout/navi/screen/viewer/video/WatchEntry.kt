package com.rout.navi.screen.viewer.video

/**
 * Satu baris riwayat tontonan.
 *
 * [key] adalah path kanonik berkas, jadi jalur symlink seperti
 * /storage/emulated/0 dan jalur asli /data/media/0 dianggap video yang sama.
 */
data class WatchEntry(
    val key: String,
    val lastWatchedAt: Long,
    val positionMs: Long,
    val durationMs: Long
) {
    /** Video dianggap selesai bila hampir seluruh durasi sudah ditonton. */
    val isFinished: Boolean
        get() = durationMs > 0 && positionMs >= durationMs * FINISHED_RATIO

    /** Posisi untuk melanjutkan; video selesai dimulai dari awal. */
    val resumePositionMs: Long
        get() = if (isFinished) 0L else positionMs.coerceAtLeast(0L)

    /** Kemajuan 0 sampai 100, dipakai penanda di daftar. */
    val percent: Int
        get() = if (durationMs <= 0) 0 else ((positionMs * 100) / durationMs).toInt().coerceIn(0, 100)

    /**
     * Path kanonik berkas, dengan cadangan path asli bila berkas hilang.
     *
     * Hanya dipakai saat membaca data lama. Pencatatan baru memakai path apa
     * adanya, karena path kanonik bisa berubah ketika berkas diganti, dan itu
     * membuat satu video tercatat dua kali.
     */
    companion object {
        private const val FINISHED_RATIO = 0.95

        /**
         * Kunci stabil untuk satu berkas. Dipakai supaya jalur symlink seperti
         * /storage/emulated/0 dan jalur asli /data/media/0 dianggap video yang
         * sama, tanpa ikut berubah saat berkas diganti.
         */
        fun keyOf(path: String): String {
            val kanonik = runCatching { java.io.File(path).canonicalPath }.getOrDefault(path)
            return kanonik.replace('|', '_')
        }

        /** Kunci cadangan: path yang sudah dibersihkan, untuk data lama. */
        fun rawKeyOf(path: String): String = path.replace('|', '_')
    }
}
