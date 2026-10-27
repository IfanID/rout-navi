package com.raival.compose.file.explorer.screen.main.tab.files.archive

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.toUuid
import com.raival.compose.file.explorer.screen.main.tab.files.archive.model.ArchiveNode
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ArchiveFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import java.io.Closeable
import java.io.File

/**
 * Satu entri arsip sebelum diubah menjadi pohon [ArchiveNode].
 */
data class ArchiveEntry(
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L
)

/**
 * Sesi ekstraksi menjaga arsip tetap terbuka selama proses berjalan.
 * Format streaming seperti tar dan 7z bergantung pada urutan entri, jadi
 * pemanggil wajib mengekstrak sesuai [ArchiveTree.listEntriesInOrder].
 */
interface ArchiveExtractionSession : Closeable {
    fun extract(node: ArchiveNode, destinationFile: File): Boolean
}

class ArchivePasswordRequiredException : Exception()

/** Arsip valid, tetapi memakai metode kompresi yang belum didukung. */
class ArchiveUnsupportedException(message: String? = null) : Exception(message)

/**
 * Pohon isi arsip di memori. Membangun pohon hanya membaca daftar entri,
 * sedangkan isi file baru diekstrak saat benar-benar dibutuhkan.
 */
abstract class ArchiveTree(val source: LocalFileHolder) {
    var timeStamp = source.lastModified

    val cleanOnExitDir = LocalFileHolder(
        file = File(globalClass.cleanOnExitDir.file, source.uniquePath.toUuid().toString()).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    )
    val extractedFiles = hashMapOf<String, LocalFileHolder>()
    private val nodes = hashMapOf<String, ArchiveNode>()
    private val orderedNodes = arrayListOf<ArchiveNode>()

    private val root = ArchiveNode(
        name = source.displayName,
        path = emptyString,
        isDirectory = true,
        lastModified = 0,
        lastAccessed = 0,
        size = 0
    )

    var isReady = false
        private set

    var isFailed = false
        private set

    open val canModify = false
    open val invalidMessage = R.string.invalid_archive

    /** Membaca daftar entri tanpa mengekstrak isinya. */
    protected abstract fun listEntries(): List<ArchiveEntry>

    /** Ekstrak satu entri ke file tujuan. */
    protected abstract fun extractEntry(node: ArchiveNode, destinationFile: File): Boolean

    open fun openExtractionSession(): ArchiveExtractionSession =
        object : ArchiveExtractionSession {
            override fun extract(node: ArchiveNode, destinationFile: File) =
                extractEntry(node, destinationFile)

            override fun close() = Unit
        }

    /** Hanya arsip yang bisa ditulis (saat ini ZIP) yang mengembalikan true. */
    open fun replaceEntry(node: ArchiveNode, file: LocalFileHolder): Boolean = false

    /** Membuat entri kosong; dipakai untuk menambah file/folder di dalam ZIP. */
    open fun createEntry(path: String, isDirectory: Boolean): ArchiveNode? = null

    open fun createHolder(node: ArchiveNode): ArchiveFileHolder = ArchiveFileHolder(this, node)

    fun createRootContentHolder(): ArchiveFileHolder = createHolder(root)

    /** Entri dalam urutan aslinya di arsip, penting untuk format streaming. */
    fun listEntriesInOrder(): List<ArchiveNode> = orderedNodes.toList()

    fun getRelatedNode(extractedFile: LocalFileHolder): ArchiveNode? {
        if (!extractedFile.uniquePath.startsWith(cleanOnExitDir.uniquePath)) return null
        return findNodeByPath(
            extractedFile.uniquePath.removePrefix(cleanOnExitDir.uniquePath)
                .removePrefix(File.separator)
        )
    }

    fun createExtractionDestinationDirFor(node: ArchiveNode) =
        if (node.parentPath.isEmpty()) cleanOnExitDir.file else File(
            cleanOnExitDir.file,
            node.parentPath
        )

    fun getExtractionDestinationFile(node: ArchiveNode): LocalFileHolder? {
        val file = File(createExtractionDestinationDirFor(node), node.name)

        if (!file.exists()) return null

        return LocalFileHolder(file)
    }

    fun getRootNode() = root

    fun findNodeByPath(path: String) = nodes[path]

    fun reset() {
        isReady = false
        timeStamp = source.lastModified
    }

    fun prepare() {
        isReady = false
        isFailed = false

        try {
            buildTree(listEntries())
        } catch (e: ArchivePasswordRequiredException) {
            isFailed = true
            logger.logError(e)
            globalClass.showMsg(R.string.archive_password_required)
        } catch (e: ArchiveUnsupportedException) {
            isFailed = true
            logger.logError(e)
            globalClass.showMsg(e.message ?: globalClass.getString(R.string.unsupported_archive))
        } catch (e: Exception) {
            isFailed = true
            logger.logError(e)
            // Tampilkan alasan dari library agar masalah format bisa
            // dilaporkan tanpa perlu membuka log aplikasi.
            globalClass.showMsg(
                if (e.message.isNullOrBlank()) {
                    globalClass.getString(invalidMessage)
                } else {
                    globalClass.getString(R.string.archive_open_failed, e.message)
                }
            )
        }

        isReady = true
    }

    fun checkExtractedFiles(): ArrayList<LocalFileHolder> {
        val result = arrayListOf<LocalFileHolder>()
        extractedFiles.forEach { item ->
            val file = item.value
            if (file.hasSourceChanged()) {
                result.add(file)
            }
        }
        return result
    }

    fun addExtractedFile(node: ArchiveNode, file: LocalFileHolder) {
        extractedFiles[node.path] = file
    }

    private fun buildTree(entries: List<ArchiveEntry>) {
        nodes.clear()
        orderedNodes.clear()
        root.children.clear()
        nodes[emptyString] = root

        entries.forEach { entry ->
            val path = normalizeEntryPath(entry.path) ?: return@forEach
            if (path.isEmpty()) return@forEach

            val parts = path.split('/')
            var currentNode = root
            var currentPath = emptyString

            parts.forEachIndexed { index, part ->
                val isLeaf = index == parts.lastIndex
                val isDirectory = !isLeaf || entry.isDirectory
                currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"

                val existingNode = currentNode.children.find { it.name == part }
                val node = existingNode ?: ArchiveNode(
                    name = part,
                    path = currentPath,
                    isDirectory = isDirectory,
                    lastModified = if (isLeaf) entry.lastModified else 0,
                    lastAccessed = 0,
                    size = if (isLeaf && !entry.isDirectory) entry.size else 0
                ).also {
                    currentNode.children.add(it)
                    nodes[currentPath] = it
                }

                currentNode = node
            }

            // Simpan hanya node yang benar-benar berasal dari entri arsip,
            // supaya urutannya tetap sama dengan urutan baca format streaming.
            if (currentNode !== root) {
                orderedNodes.add(currentNode)
            }
        }
    }
}

/**
 * Menyeragamkan pemisah path dan menolak entri yang mencoba keluar dari
 * folder tujuan (path traversal).
 */
internal fun normalizeEntryPath(rawPath: String): String? {
    var path = rawPath.replace('\\', '/')

    while (path.startsWith("./")) {
        path = path.substring(2)
    }

    path = path.trim('/')
    if (path.isEmpty()) return null

    val parts = path.split('/').filter { it.isNotEmpty() && it != "." }
    if (parts.isEmpty() || parts.any { it == ".." }) return null

    return parts.joinToString("/")
}
