package com.raival.compose.file.explorer.screen.analyzer

import com.raival.compose.file.explorer.screen.analyzer.model.StorageNode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

/**
 * Pemindai pohon folder untuk layar analisis penyimpanan.
 *
 * Seluruh isi folder dihitung sekali di awal, lalu hasilnya disimpan sebagai
 * pohon di memori. Pemindaian read-only: hanya atribut berkas yang dibaca.
 */
object StorageScanner {
    /** Jumlah simpul antar laporan progres. */
    private const val PROGRESS_STEP = 64

    /** Jumlah simpul antar pemeriksaan pembatalan. */
    private const val CANCELLATION_STEP = 256

    /**
     * Memindai [root] beserta seluruh keturunannya lalu mengembalikan pohon
     * dengan ukuran terisi dari bawah ke atas. [onProgress] menerima jumlah
     * berkas yang sudah dibaca.
     */
    suspend fun scan(root: File, onProgress: (files: Int, bytes: Long) -> Unit = { _, _ -> }): StorageNode {
        var scannedFiles = 0
        var scannedBytes = 0L
        var visited = 0

        suspend fun build(file: File, parent: StorageNode?): StorageNode {
            visited++
            if (visited % CANCELLATION_STEP == 0) {
                currentCoroutineContext().ensureActive()
            }

            if (!file.isDirectory) {
                val node = StorageNode(
                    file = file,
                    isFolder = false,
                    accessible = true,
                    parent = parent
                )
                node.size = file.length()
                node.itemCount = 1
                scannedFiles++
                scannedBytes += node.size
                if (scannedFiles % PROGRESS_STEP == 0) onProgress(scannedFiles, scannedBytes)
                return node
            }

            val children = file.listFiles()
            val node = StorageNode(
                file = file,
                isFolder = true,
                accessible = children != null,
                parent = parent
            )
            children?.forEach { child ->
                val childNode = build(child, node)
                node.children.add(childNode)
                node.size += childNode.size
                node.itemCount += childNode.itemCount
            }
            return node
        }

        val tree = build(root, null)
        onProgress(scannedFiles, scannedBytes)
        return tree
    }
}
