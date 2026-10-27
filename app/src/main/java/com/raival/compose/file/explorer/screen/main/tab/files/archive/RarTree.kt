package com.raival.compose.file.explorer.screen.main.tab.files.archive

import com.github.junrar.Archive
import com.github.junrar.exception.RarException
import com.github.junrar.exception.WrongPasswordException
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.screen.main.tab.files.archive.model.ArchiveNode
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import java.io.File

class RarTree(
    source: LocalFileHolder,
    private val password: String? = null
) : ArchiveTree(source) {

    private fun openArchive(): Archive = Archive(source.file).apply {
        if (!password.isNullOrEmpty()) {
            setPassword(password)
        }
    }

    override fun listEntries(): List<ArchiveEntry> {
        val archive = try {
            openArchive()
        } catch (e: WrongPasswordException) {
            throw ArchivePasswordRequiredException()
        }

        return archive.use {
            it.fileHeaders.mapNotNull { header ->
                val name = header.fileName ?: return@mapNotNull null
                ArchiveEntry(
                    path = name,
                    isDirectory = header.isDirectory,
                    size = header.fullUnpackSize.coerceAtLeast(0),
                    lastModified = header.lastModifiedTime?.toMillis() ?: 0L
                )
            }
        }
    }

    override fun extractEntry(node: ArchiveNode, destinationFile: File): Boolean =
        openExtractionSession().use { it.extract(node, destinationFile) }

    override fun openExtractionSession(): ArchiveExtractionSession =
        object : ArchiveExtractionSession {
            private val archive = try {
                openArchive()
            } catch (e: WrongPasswordException) {
                throw ArchivePasswordRequiredException()
            } catch (e: Exception) {
                logger.logError(e)
                null
            }

            override fun extract(node: ArchiveNode, destinationFile: File): Boolean {
                val archive = archive ?: return false

                return try {
                    val header = archive.fileHeaders.firstOrNull { fileHeader ->
                        val name = fileHeader.fileName ?: return@firstOrNull false
                        normalizeEntryPath(name) == node.path
                    } ?: return false

                    if (header.isDirectory) {
                        destinationFile.mkdirs()
                    } else {
                        destinationFile.parentFile?.mkdirs()
                        destinationFile.outputStream().use { output ->
                            archive.extractFile(header, output)
                        }
                    }
                    true
                } catch (e: WrongPasswordException) {
                    logger.logError(e)
                    globalClass.showMsg(R.string.wrong_archive_password)
                    false
                } catch (e: RarException) {
                    logger.logError(e)
                    false
                } catch (e: Exception) {
                    logger.logError(e)
                    false
                }
            }

            override fun close() {
                archive?.close()
            }
        }
}
