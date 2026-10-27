package com.raival.compose.file.explorer.screen.main.tab.files.archive.model

import com.raival.compose.file.explorer.common.emptyString
import java.io.File

data class ArchiveNode(
    val name: String,
    val path: String,
    val size: Long = 0L,
    val isDirectory: Boolean = true,
    val lastModified: Long = 0L,
    val lastAccessed: Long = 0L,
    val children: MutableList<ArchiveNode> = mutableListOf()
) {
    val extension: String
        get() {
            if (this.isDirectory) {
                return emptyString
            }

            val dotIndex = this.name.lastIndexOf('.')

            // Jika ada titik dan bukan karakter pertama, ambil bagian setelahnya.
            return if (dotIndex > 0) {
                this.name.substring(dotIndex + 1).lowercase()
            } else {
                emptyString
            }
        }

    val parentPath: String
        get() {
            // Hapus garis miring di akhir agar folder seperti "folder/" tetap benar.
            val path = this.path.removeSuffix(File.separator)

            val lastSlashIndex = path.lastIndexOf(File.separatorChar)

            return if (lastSlashIndex == -1) {
                emptyString
            } else {
                path.substring(0, lastSlashIndex)
            }
        }

    fun listFilesAndEmptyDirs(): List<ArchiveNode> {
        val result = mutableListOf<ArchiveNode>()

        // Antrean folder yang akan dikunjungi, dimulai dari node ini.
        val directoryQueue = ArrayDeque<ArchiveNode>()
        if (isDirectory) {
            directoryQueue.add(this)
        } else {
            result.add(this)
            return result
        }

        while (directoryQueue.isNotEmpty()) {
            val dir = directoryQueue.removeFirst()
            val children = dir.children

            if (children.isEmpty()) {
                result.add(dir)
            } else {
                for (child in children) {
                    if (child.isDirectory) {
                        directoryQueue.add(child)
                    } else {
                        result.add(child)
                    }
                }
            }
        }

        return result
    }
}
