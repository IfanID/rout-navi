package com.raival.compose.file.explorer.screen.main.tab.files.archive

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.screen.main.tab.files.archive.model.ArchiveNode
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.IOException

class SevenZTree(
    source: LocalFileHolder,
    private val password: String? = null
) : ArchiveTree(source) {

    private fun openSevenZ(): SevenZFile = SevenZFile.builder()
        .setFile(source.file)
        // Sebagian arsip 7z tidak menyimpan nama entri; pakai nama bawaan
        // agar pembangunan pohon tidak gagal karena nama null.
        .setUseDefaultNameForUnnamedEntries(true)
        .apply {
            if (!password.isNullOrEmpty()) {
                setPassword(password)
            }
        }
        .get()

    override fun listEntries(): List<ArchiveEntry> {
        val entries = arrayListOf<ArchiveEntry>()

        try {
            openSevenZ().use { sevenZ ->
                while (true) {
                    val entry = sevenZ.nextEntry ?: break
                    val name = entry.name ?: continue
                    entries.add(
                        ArchiveEntry(
                            path = name,
                            isDirectory = entry.isDirectory,
                            size = entry.size.coerceAtLeast(0),
                            // 7z tidak wajib menyimpan waktu modifikasi. Getter
                            // akan melempar UnsupportedOperationException bila
                            // timestamp tidak ada, jadi periksa dulu.
                            lastModified = if (entry.hasLastModifiedDate) {
                                entry.lastModifiedTime.toMillis()
                            } else {
                                0L
                            }
                        )
                    )
                }
            }
        } catch (e: PasswordRequiredException) {
            throw ArchivePasswordRequiredException()
        } catch (e: IOException) {
            throw e.toArchiveOpenException()
        }

        return entries
    }

    override fun extractEntry(node: ArchiveNode, destinationFile: File): Boolean =
        openExtractionSession().use { it.extract(node, destinationFile) }

    override fun openExtractionSession(): ArchiveExtractionSession =
        object : ArchiveExtractionSession {
            private val sevenZ: SevenZFile = try {
                openSevenZ()
            } catch (e: PasswordRequiredException) {
                throw ArchivePasswordRequiredException()
            } catch (e: IOException) {
                throw e.toArchiveOpenException()
            }

            override fun extract(node: ArchiveNode, destinationFile: File): Boolean {
                return try {
                    // 7z dibaca berurutan; pencarian dilakukan dengan maju
                    // sampai entri yang diminta ditemukan.
                    while (true) {
                        val entry = sevenZ.nextEntry ?: return false
                        if (normalizeEntryPath(entry.name) != node.path) continue

                        if (entry.isDirectory) {
                            destinationFile.mkdirs()
                        } else {
                            destinationFile.parentFile?.mkdirs()
                            // Stream harus dibuat dari entri hasil nextEntry
                            // agar SevenZFile bisa menemukan blok datanya.
                            sevenZ.getInputStream(entry).use { input ->
                                destinationFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                        return true
                    }
                    @Suppress("UNREACHABLE_CODE")
                    false
                } catch (e: PasswordRequiredException) {
                    logger.logError(e)
                    globalClass.showMsg(
                        if (password.isNullOrEmpty()) {
                            R.string.archive_password_required
                        } else {
                            R.string.wrong_archive_password
                        }
                    )
                    false
                } catch (e: Exception) {
                    logger.logError(e)
                    false
                }
            }

            override fun close() {
                sevenZ.close()
            }
        }

    /**
     * 7z mendukung beberapa codec yang belum bisa dibaca commons-compress,
     * misalnya BCJ2 dan PPMd. Pesan khusus ini lebih jelas daripada
     * "arsip tidak valid" karena berkasnya sebenarnya sehat.
     */
    private fun IOException.toArchiveOpenException(): Exception {
        val detail = message ?: return this
        val unsupported = detail.contains("Multi input/output stream coders", true) ||
                detail.contains("Unsupported compression method", true) ||
                detail.contains("not yet supported", true)

        return if (unsupported) {
            logger.logError(this)
            ArchiveUnsupportedException(globalClass.getString(R.string.archive_unsupported_compression))
        } else {
            this
        }
    }
}
