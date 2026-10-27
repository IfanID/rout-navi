package com.raival.compose.file.explorer.screen.main.tab.files.zip

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.screen.main.tab.files.archive.ArchiveEntry
import com.raival.compose.file.explorer.screen.main.tab.files.archive.ArchiveExtractionSession
import com.raival.compose.file.explorer.screen.main.tab.files.archive.ArchiveTree
import com.raival.compose.file.explorer.screen.main.tab.files.archive.model.ArchiveNode
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ArchiveFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ZipFileHolder
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile as JavaZipFile

class ZipTree(
    source: LocalFileHolder,
    private val password: String? = null
) : ArchiveTree(source) {
    override val canModify = true
    override val invalidMessage = R.string.invalid_zip

    override fun listEntries(): List<ArchiveEntry> = JavaZipFile(source.file).use { zipFile ->
        zipFile.entries().asSequence().map { entry ->
            ArchiveEntry(
                path = entry.name,
                isDirectory = entry.isDirectory,
                size = entry.size.coerceAtLeast(0),
                lastModified = entry.lastModifiedTime?.toMillis() ?: 0L
            )
        }.toList()
    }

    override fun createHolder(node: ArchiveNode): ArchiveFileHolder =
        ZipFileHolder(this, node)

    override fun createEntry(path: String, isDirectory: Boolean): ArchiveNode? {
        val entryPath = if (isDirectory) path.trimEnd('/') + "/" else path
        val params = ZipParameters().apply {
            fileNameInZip = entryPath
            isOverrideExistingFilesInZip = false
        }

        return try {
            ZipFile(source.file).use { zipFile ->
                zipFile.addStream(ByteArrayInputStream(ByteArray(0)), params)
            }
            prepare()
            findNodeByPath(path.trimEnd('/'))
        } catch (e: Exception) {
            logger.logError(e)
            null
        }
    }

    override fun replaceEntry(node: ArchiveNode, file: LocalFileHolder): Boolean {
        return try {
            ZipFile(source.file).use { zipFile ->
                zipFile.addFile(
                    file.file,
                    ZipParameters().apply {
                        fileNameInZip = node.path
                        isOverrideExistingFilesInZip = true
                    }
                )
            }
            true
        } catch (e: Exception) {
            logger.logError(e)
            false
        }
    }

    override fun extractEntry(node: ArchiveNode, destinationFile: File): Boolean =
        openExtractionSession().use { it.extract(node, destinationFile) }

    override fun openExtractionSession(): ArchiveExtractionSession =
        object : ArchiveExtractionSession {
            private val zipFile = ZipFile(source.file, password?.toCharArray())

            override fun extract(node: ArchiveNode, destinationFile: File): Boolean {
                return try {
                    val header = zipFile.getFileHeader(node.path) ?: return false

                    if (header.isDirectory) {
                        destinationFile.mkdirs()
                    } else {
                        destinationFile.parentFile?.mkdirs()
                        zipFile.getInputStream(header).use { input ->
                            destinationFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                    true
                } catch (e: ZipException) {
                    logger.logError(e)
                    if (e.type == ZipException.Type.WRONG_PASSWORD) {
                        globalClass.showMsg(R.string.wrong_archive_password)
                    }
                    false
                } catch (e: Exception) {
                    logger.logError(e)
                    false
                }
            }

            override fun close() {
                zipFile.close()
            }
        }
}
