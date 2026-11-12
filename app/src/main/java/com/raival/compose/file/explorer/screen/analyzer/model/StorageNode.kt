package com.raival.compose.file.explorer.screen.analyzer.model

import java.io.File

/**
 * Simpul pohon hasil pemindaian. Ukuran folder dihitung dari seluruh isinya
 * sekali di awal, sehingga membuka folder tidak memerlukan pemindaian lagi.
 */
class StorageNode(
    val file: File,
    val isFolder: Boolean,
    var accessible: Boolean,
    var parent: StorageNode?
) {
    val children = mutableListOf<StorageNode>()

    /** Total ukuran isi folder (rekursif), atau ukuran berkas. */
    var size: Long = 0L

    /** Jumlah berkas di dalam folder (rekursif), atau 1 untuk berkas. */
    var itemCount: Int = 0

    val name: String
        get() = file.name
}
