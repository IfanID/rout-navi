package com.raival.compose.file.explorer.screen.main.tab.files.holder

import com.raival.compose.file.explorer.screen.main.tab.files.archive.model.ArchiveNode
import com.raival.compose.file.explorer.screen.main.tab.files.zip.ZipTree

/**
 * Holder khusus ZIP. Keberadaannya dipakai kode lama untuk membatasi operasi
 * tulis (tambah/ubah/hapus isi) hanya pada arsip ZIP.
 */
class ZipFileHolder(
    val zipTree: ZipTree,
    node: ArchiveNode,
) : ArchiveFileHolder(zipTree, node) {
    override fun createHolder(node: ArchiveNode) = ZipFileHolder(zipTree, node)
}
