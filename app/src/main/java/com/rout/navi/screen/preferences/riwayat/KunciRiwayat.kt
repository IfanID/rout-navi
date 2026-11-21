package com.rout.navi.screen.preferences.riwayat

/**
 * Aturan kunci riwayat tontonan. Ditulis di satu tempat supaya pencatatan,
 * pencarian, dan penghapusan tidak pernah memakai aturan berbeda.
 *
 * Masukan bisa berupa jalur biasa, URI file://, atau URI content:// dari
 * FileProvider. Semuanya dikembalikan ke jalur berkas biasa lebih dulu.
 */
object KunciRiwayat {

    /**
     * Kunci satu berkas: jalur kanonik folder induk ditambah nama berkas.
     *
     * Folder induk dipakai dengan jalur kanonik supaya /storage/emulated/0 dan
     * /data/media/0 menjadi sama, tetapi berkasnya sendiri tidak memakai
     * canonicalPath. canonicalPath sebuah berkas ikut berubah ketika isinya
     * atau atributnya diperbarui, dan itu membuat satu video tercatat dua kali.
     */
    fun dari(path: String): String {
        val berkas = berkasDari(path).absoluteFile
        val induk = berkas.parentFile
        val jalurInduk = induk?.let { folder ->
            runCatching { folder.canonicalPath }.getOrDefault(folder.absolutePath)
        } ?: ""
        // Android tidak membedakan huruf besar-kecil pada penyimpanan, jadi
        // "rout" dan "Rout" adalah folder yang sama. Kuncinya diseragamkan ke
        // huruf kecil supaya tidak muncul dua catatan untuk satu berkas.
        val mentah = if (jalurInduk.isEmpty()) berkas.path else "$jalurInduk/${berkas.name}"
        return mentah.lowercase().replace('|', '_')
    }

    /**
     * Berkas dari jalur atau URI. URI `content://` dari FileProvider membawa
     * jalur asli di parameter `path`, jadi dipakai lebih dulu.
     */
    fun berkasDari(path: String): java.io.File {
        val dibuka = runCatching { android.net.Uri.decode(path) }.getOrDefault(path)
        if (!dibuka.startsWith("content://")) {
            return java.io.File(dibuka.substringBefore('?'))
        }
        val jalur = dibuka.substringAfter("path=", "").substringBefore('&')
        return if (jalur.startsWith("/")) java.io.File(jalur) else java.io.File(dibuka)
    }

    /** Apakah dua masukan merujuk berkas yang sama. */
    fun sama(a: String, b: String): Boolean = dari(a) == dari(b)
}
