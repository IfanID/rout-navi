package com.raival.compose.file.explorer.screen.main.tab.files.task

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.toFormattedDate
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ContentHolder
import net.lingala.zip4j.ZipFile
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.Date

class CompressTask(
    val sourceContent: List<ContentHolder>
) : Task() {
    private var parameters: CompressTaskParameters? = null
    private var pendingContent = arrayListOf<TaskContentItem>()

    override val metadata = System.currentTimeMillis().toFormattedDate().let { time ->
        TaskMetadata(
            id = id,
            creationTime = time,
            title = globalClass.resources.getString(R.string.compress),
            subtitle = globalClass.resources.getString(R.string.task_subtitle, sourceContent.size),
            displayDetails = sourceContent.joinToString(", ") { it.displayName },
            fullDetails = buildString {
                sourceContent.forEach { source ->
                    append(source.displayName)
                    append("\n")
                }
                append("\n")
                append(time)
            },
            isCancellable = true,
            canMoveToBackground = true
        )
    }
    override val progressMonitor = TaskProgressMonitor(
        status = TaskStatus.PENDING,
        taskTitle = metadata.title,
    )

    override fun getCurrentStatus() = progressMonitor.status

    override suspend fun validate() = sourceContent.find { !it.isValid() } == null

    private fun markAsFailed(info: String) {
        progressMonitor.apply {
            status = TaskStatus.FAILED
            summary = info
            progress = 0f
        }
    }

    private fun markAsAborted() {
        progressMonitor.apply {
            status = TaskStatus.PAUSED
            summary = globalClass.getString(R.string.task_aborted)
        }
    }

    override suspend fun run() {
        if (parameters == null) {
            markAsFailed(globalClass.getString(R.string.unable_to_continue_task))
            return
        }
        run(parameters!!)
    }

    override suspend fun run(params: TaskParameters) {
        val paramsData = params as CompressTaskParameters
        parameters = paramsData
        progressMonitor.status = TaskStatus.RUNNING
        protect = false

        // Check abortion early
        if (aborted) {
            markAsAborted()
            return
        }

        if (sourceContent.isEmpty()) {
            markAsFailed(globalClass.resources.getString(R.string.task_summary_no_src))
            return
        }

        progressMonitor.apply {
            processName = globalClass.resources.getString(R.string.preparing)
            progress = 0.05f
        }

        val basePath = sourceContent[0].getParent()?.uniquePath ?: emptyString

        if (pendingContent.isEmpty()) {
            sourceContent.forEach { content ->
                pendingContent.add(
                    TaskContentItem(
                        content = content,
                        relativePath = content.uniquePath.removePrefix("/$basePath"),
                        status = TaskContentStatus.PENDING
                    )
                )
            }
        }

        progressMonitor.apply {
            totalContent = pendingContent.size
            processName = globalClass.getString(R.string.compressing)
            progress = 0.1f
        }

        val destination = File(paramsData.destPath)

        // Cek pembatalan sebelum mulai menulis.
        if (aborted) {
            markAsAborted()
            return
        }

        val finished = try {
            when (paramsData.format) {
                CompressFormat.ZIP -> compressToZip(destination)
                CompressFormat.SEVEN_ZIP -> compressToSevenZip(destination)
                CompressFormat.TAR -> compressToTar(destination)
            }
        } catch (e: Throwable) {
            // Tangkap Throwable agar kegagalan pembuat arsip (misalnya kelas
            // codec yang tidak tersedia) tidak membuat aplikasi berhenti.
            logger.logError(e)
            destination.delete()
            resetPendingContent()
            markAsFailed(
                globalClass.resources.getString(
                    R.string.task_summary_failed,
                    e.message ?: e.javaClass.simpleName
                )
            )
            return
        }

        if (!finished) {
            // Arsip setengah jadi dihapus agar percobaan berikutnya bersih.
            destination.delete()
            resetPendingContent()
            markAsAborted()
            return
        }

        if (progressMonitor.status == TaskStatus.RUNNING) {
            progressMonitor.apply {
                status = TaskStatus.SUCCESS
                progress = 1.0f
                remainingContent = 0
                processName = globalClass.getString(R.string.completed)
                summary = globalClass.getString(R.string.task_completed)
            }

            // Arsip yang baru dibuat langsung muncul di folder yang sedang dibuka.
            withContext(Dispatchers.Main) {
                (globalClass.mainActivityManager.getActiveTab() as? FilesTab)?.quickReloadFiles()
            }
        }
    }

    private fun compressToZip(destination: File): Boolean {
        ZipFile(destination).use { zipOut ->
            pendingContent.forEachIndexed { index, item ->
                if (aborted) return false
                if (item.status != TaskContentStatus.PENDING) return@forEachIndexed

                updateProgress(index, item.content)

                if (item.content.isFolder) {
                    zipOut.addFolder(File(item.content.uniquePath))
                } else {
                    zipOut.addFile(File(item.content.uniquePath))
                }
                item.status = TaskContentStatus.SUCCESS
            }
        }
        return true
    }

    private fun compressToSevenZip(destination: File): Boolean {
        SevenZOutputFile(destination).use { output ->
            pendingContent.forEachIndexed { index, item ->
                if (aborted) return false
                if (item.status != TaskContentStatus.PENDING) return@forEachIndexed

                updateProgress(index, item.content)

                collectEntries(item.content).let { entries ->
                    entries.forEachIndexed { entryIndex, entry ->
                        if (aborted) return false
                        updateEntryProgress(index, entryIndex, entries.size, item.content)

                        val sevenZEntry = SevenZArchiveEntry().apply {
                            name = entry.entryName
                            isDirectory = entry.isDirectory
                            // Simpan waktu modifikasi agar pembaca tidak perlu
                            // menebak dan agar tanggal file tetap terjaga.
                            setLastModifiedDate(Date(entry.file.lastModified()))
                        }

                        if (!entry.isDirectory) {
                            sevenZEntry.size = entry.file.length()
                        }

                        output.putArchiveEntry(sevenZEntry)
                        if (!entry.isDirectory) {
                            entry.file.inputStream().use { output.write(it) }
                        }
                        output.closeArchiveEntry()
                    }
                }
                item.status = TaskContentStatus.SUCCESS
            }
            output.finish()
        }
        return true
    }

    private fun compressToTar(destination: File): Boolean {
        FileOutputStream(destination).use { fileStream ->
            BufferedOutputStream(fileStream).use { bufferedStream ->
                TarArchiveOutputStream(bufferedStream).use { tar ->
                    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                    tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)

                    pendingContent.forEachIndexed { index, item ->
                        if (aborted) return false
                        if (item.status != TaskContentStatus.PENDING) return@forEachIndexed

                        updateProgress(index, item.content)

                        collectEntries(item.content).let { entries ->
                            entries.forEachIndexed { entryIndex, entry ->
                                if (aborted) return false
                                updateEntryProgress(index, entryIndex, entries.size, item.content)

                                val tarEntry = TarArchiveEntry(entry.file, entry.entryName)

                                if (entry.isDirectory) {
                                    tarEntry.size = 0
                                }

                                tar.putArchiveEntry(tarEntry)
                                if (!entry.isDirectory) {
                                    entry.file.inputStream().use { it.copyTo(tar) }
                                }
                                tar.closeArchiveEntry()
                            }
                        }
                        item.status = TaskContentStatus.SUCCESS
                    }
                }
            }
        }
        return true
    }

    private fun updateProgress(index: Int, content: ContentHolder) {
        val progressPercent = 0.1f + (0.9f * (index.toFloat() / pendingContent.size))

        progressMonitor.apply {
            contentName = content.displayName
            remainingContent = pendingContent.size - (index + 1)
            progress = progressPercent
        }
    }

    /**
     * Progres per entri di dalam satu item terpilih agar bilah progres tidak
     * melompat dari 10% langsung ke 100% saat mengarsipkan satu folder besar.
     */
    private fun updateEntryProgress(
        index: Int,
        entryIndex: Int,
        entryCount: Int,
        content: ContentHolder
    ) {
        val itemProgress = if (entryCount <= 0) 1f else entryIndex.toFloat() / entryCount
        val overallProgress = (index + itemProgress) / pendingContent.size

        progressMonitor.apply {
            contentName = content.displayName
            remainingContent = pendingContent.size - (index + 1)
            progress = 0.1f + (0.9f * overallProgress)
        }
    }

    private fun resetPendingContent() {
        pendingContent.forEach { it.status = TaskContentStatus.PENDING }
    }

    private data class CompressEntry(
        val file: File,
        val entryName: String,
        val isDirectory: Boolean
    )

    /**
     * Mengumpulkan file dan folder secara rekursif dengan path relatif di
     * dalam arsip. Folder kosong tetap disertakan.
     */
    private fun collectEntries(content: ContentHolder): List<CompressEntry> {
        val root = File(content.uniquePath)
        val result = arrayListOf<CompressEntry>()

        fun walk(file: File, entryName: String) {
            val isDirectory = file.isDirectory
            val name = if (isDirectory) entryName.trimEnd('/') + "/" else entryName

            result.add(CompressEntry(file, name, isDirectory))

            if (!isDirectory) return

            val children = file.listFiles()?.sortedBy { it.name.lowercase() } ?: return
            children.forEach { child ->
                // Symlink dilewati agar tidak mengikuti folder di luar pilihan.
                if (isSymbolicLink(child)) return@forEach
                walk(child, "$name${child.name}")
            }
        }

        walk(root, content.displayName.ifEmpty { root.name })
        return result
    }

    private fun isSymbolicLink(file: File): Boolean = try {
        Files.isSymbolicLink(file.toPath())
    } catch (e: Exception) {
        false
    }

    override fun setParameters(params: TaskParameters) {
        parameters = params as CompressTaskParameters
    }

    override suspend fun continueTask() {
        if (parameters == null) {
            markAsFailed(globalClass.getString(R.string.unable_to_continue_task))
            return
        }
        run(parameters!!)
    }
}
