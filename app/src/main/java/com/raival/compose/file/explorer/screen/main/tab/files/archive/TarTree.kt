package com.raival.compose.file.explorer.screen.main.tab.files.archive

import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.screen.main.tab.files.archive.model.ArchiveNode
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File

class TarTree(
    source: LocalFileHolder
) : ArchiveTree(source) {

    private fun openTarStream(): TarArchiveInputStream = TarArchiveInputStream(
        BufferedInputStream(source.file.inputStream())
    )

    override fun listEntries(): List<ArchiveEntry> = openTarStream().use { tar ->
        val entries = arrayListOf<ArchiveEntry>()

        while (true) {
            val entry: TarArchiveEntry = tar.nextTarEntry ?: break
            entries.add(
                ArchiveEntry(
                    path = entry.name,
                    isDirectory = entry.isDirectory,
                    size = entry.size.coerceAtLeast(0),
                    lastModified = entry.lastModifiedTime?.toMillis() ?: 0L
                )
            )
        }

        entries
    }

    override fun extractEntry(node: ArchiveNode, destinationFile: File): Boolean =
        openExtractionSession().use { it.extract(node, destinationFile) }

    override fun openExtractionSession(): ArchiveExtractionSession =
        object : ArchiveExtractionSession {
            private val tar = openTarStream()

            override fun extract(node: ArchiveNode, destinationFile: File): Boolean {
                return try {
                    // tar hanya bisa dibaca maju, jadi entri dicari secara berurutan.
                    while (true) {
                        val entry: TarArchiveEntry = tar.nextTarEntry ?: return false
                        if (normalizeEntryPath(entry.name) != node.path) continue

                        if (entry.isDirectory) {
                            destinationFile.mkdirs()
                        } else {
                            destinationFile.parentFile?.mkdirs()
                            destinationFile.outputStream().use { output ->
                                tar.copyTo(output)
                            }
                        }
                        return true
                    }
                    @Suppress("UNREACHABLE_CODE")
                    false
                } catch (e: Exception) {
                    logger.logError(e)
                    false
                }
            }

            override fun close() {
                tar.close()
            }
        }
}
