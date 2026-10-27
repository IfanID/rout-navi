package com.raival.compose.file.explorer.screen.main.tab.files.holder

import android.content.Context
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.showMsg
import com.raival.compose.file.explorer.common.toFormattedSize
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.archive.ArchiveTree
import com.raival.compose.file.explorer.screen.main.tab.files.archive.model.ArchiveNode
import com.raival.compose.file.explorer.screen.main.tab.files.misc.ContentCount
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Holder umum untuk isi arsip apa pun. Operasi tulis hanya tersedia bila
 * [ArchiveTree.canModify] bernilai true (saat ini hanya ZIP).
 */
open class ArchiveFileHolder(
    val archiveTree: ArchiveTree,
    val node: ArchiveNode,
) : ContentHolder() {
    override val uniquePath = node.path
    override val displayName = node.name
    var details = emptyString

    override val isFolder = node.isDirectory
    override val lastModified = node.lastModified
    override val size = node.size
    override val extension = node.extension
    override val canRead = true
    override val canWrite = archiveTree.canModify
    override val canAddNewContent = archiveTree.canModify

    private var filesCount = 0
    private var foldersCount = 0

    private var contentListCount = ContentCount()

    open fun createHolder(node: ArchiveNode): ArchiveFileHolder =
        ArchiveFileHolder(archiveTree, node)

    override suspend fun getDetails(): String {
        if (details.isNotEmpty()) return details

        return createDetails().also {
            details = it
        }
    }

    override suspend fun listContent(): ArrayList<ArchiveFileHolder> {
        filesCount = 0
        foldersCount = 0

        if (!archiveTree.isReady) {
            archiveTree.prepare()
        }

        // Tab yang di-reload bisa menyimpan node lama, jadi ambil ulang node
        // berdasarkan path agar isinya selalu sinkron dengan pohon terbaru.
        val newNode = archiveTree.findNodeByPath(uniquePath)

        if (newNode == null) return arrayListOf()

        return newNode.children
            .map {
                createHolder(it).also { holder ->
                    if (holder.node.isDirectory) foldersCount++ else filesCount++
                }
            }
            .toCollection(arrayListOf()).also {
                contentListCount = ContentCount(filesCount, foldersCount)
            }
    }

    override suspend fun getParent(): ContentHolder? {
        if (!node.path.contains(File.separator) && node.path.isNotEmpty()) {
            return createHolder(archiveTree.getRootNode())
        }

        var parentPath = node.parentPath

        while (parentPath.isNotEmpty()) {
            val parentNode = archiveTree.findNodeByPath(parentPath)

            if (parentNode != null) {
                return createHolder(parentNode)
            }

            parentPath = ArchiveNode(emptyString, parentPath).parentPath
        }

        return archiveTree.source.getParent()
    }

    override suspend fun createSubFile(name: String, onCreated: (ContentHolder?) -> Unit) {
        val path = "${if (uniquePath.isEmpty()) emptyString else "$uniquePath/"}$name"
        archiveTree.createEntry(path, false)?.let {
            onCreated(createHolder(it))
        } ?: onCreated(null)
    }

    override suspend fun createSubFolder(name: String, onCreated: (ContentHolder?) -> Unit) {
        val path = "${if (uniquePath.isEmpty()) emptyString else "$uniquePath/"}$name"
        archiveTree.createEntry(path, true)?.let {
            onCreated(createHolder(it))
        } ?: onCreated(null)
    }

    override suspend fun getContentCount() = contentListCount

    override suspend fun findFile(name: String) = node.children.find { it.name == name }?.let {
        createHolder(it)
    }

    override suspend fun isValid() = if (archiveTree.isReady) {
        (archiveTree.source.isValid() && archiveTree.findNodeByPath(uniquePath) != null).also { isValid ->
            if (!isValid) globalClass.archiveManager.validateArchiveTrees()
        }
    } else {
        archiveTree.source.isValid().also { isValid ->
            if (!isValid) globalClass.archiveManager.validateArchiveTrees()
        }
    }

    override fun open(
        context: Context,
        anonymous: Boolean,
        skipSupportedExtensions: Boolean,
        customMimeType: String?
    ) {
        val file = archiveTree.getExtractionDestinationFile(node)

        if (file != null) {
            file.open(context, anonymous, skipSupportedExtensions, customMimeType)
        } else {
            (globalClass.mainActivityManager.getActiveTab() as? FilesTab)?.extractArchiveHolderForPreview(
                this
            ) {
                val file = archiveTree.getExtractionDestinationFile(node)
                if (file != null) {
                    archiveTree.addExtractedFile(node, file)
                    file.open(context, anonymous, skipSupportedExtensions, customMimeType)
                } else {
                    showMsg(R.string.failed_to_extract_file)
                }
            }
        }
    }

    suspend fun extractForPreview(): String {
        val dir = archiveTree.createExtractionDestinationDirFor(node)
        val destinationFile = File(dir, node.name)

        try {
            archiveTree.openExtractionSession().use { session ->
                session.extract(node, destinationFile)
            }
        } catch (e: Exception) {
            logger.logError(e)
        }

        return dir.absolutePath
    }

    private suspend fun createDetails(): String {
        val separator = " | "
        return buildString {
            append(getLastModifiedDate())
            if (node.isDirectory) {
                if (globalClass.preferencesManager.showFolderContentCount) {
                    append(separator)
                    append(getFormattedFileCount())
                }
            } else {
                append(separator)
                append(node.size.toFormattedSize())
                append(separator)
                append(node.extension)
            }
        }
    }

    private suspend fun getFormattedFileCount(): String {
        if (filesCount == 0 && foldersCount == 0) {
            runBlocking {
                listContent().forEach {
                    if (it.node.isDirectory) foldersCount++
                    else filesCount++
                }
            }
        }

        return getFormattedFileCount(
            filesCount,
            foldersCount
        )
    }
}
