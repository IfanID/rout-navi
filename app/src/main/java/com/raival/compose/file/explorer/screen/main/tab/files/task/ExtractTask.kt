package com.raival.compose.file.explorer.screen.main.tab.files.task

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.isNot
import com.raival.compose.file.explorer.common.toFormattedDate
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.archive.ArchiveFormat
import com.raival.compose.file.explorer.screen.main.tab.files.holder.ArchiveFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Mengekstrak seluruh isi arsip ke folder tujuan. Format yang didukung
 * mengikuti [ArchiveFormat] (ZIP, RAR, 7z, tar, gz, xz, bz2, lzma).
 */
class ExtractTask(
    val archive: LocalFileHolder,
    private val password: String? = null
) : Task() {
    private var parameters: ExtractTaskParameters? = null
    private val pendingFiles = arrayListOf<TaskContentItem>()

    override val metadata = createTaskMetadata()
    override val progressMonitor = TaskProgressMonitor(
        status = TaskStatus.PENDING,
        taskTitle = metadata.title
    )

    override fun getCurrentStatus() = progressMonitor.status

    override suspend fun validate() = archive.isValid() && archive.canRead

    override suspend fun run() {
        val params = parameters
        if (params == null) {
            markAsFailed(globalClass.getString(R.string.unable_to_continue_task))
            return
        }
        run(params)
    }

    override suspend fun run(params: TaskParameters) {
        parameters = params as? ExtractTaskParameters

        if (parameters == null) {
            markAsFailed(globalClass.getString(R.string.unable_to_continue_task))
            return
        }

        progressMonitor.status = TaskStatus.RUNNING
        progressMonitor.processName = globalClass.getString(R.string.extracting)

        if (aborted) {
            markAsAborted()
            return
        }

        try {
            extract()
        } catch (e: Exception) {
            handleTaskError(e)
        }
    }

    override suspend fun continueTask() {
        parameters?.let { run(it) } ?: markAsFailed(
            globalClass.getString(R.string.unable_to_continue_task)
        )
    }

    override fun setParameters(params: TaskParameters) {
        parameters = params as? ExtractTaskParameters
    }

    private suspend fun extract() {
        val params = parameters ?: return
        val destinationRoot = File(params.destinationPath)

        if (!destinationRoot.exists() && !destinationRoot.mkdirs()) {
            markAsFailed(globalClass.getString(R.string.extract_destination_invalid))
            return
        }

        if (!destinationRoot.isDirectory) {
            markAsFailed(globalClass.getString(R.string.extract_destination_invalid))
            return
        }

        val tree = ArchiveFormat.createTree(archive, params.password ?: password)

        if (tree == null) {
            markAsFailed(globalClass.getString(R.string.unsupported_archive))
            return
        }

        tree.prepare()

        if (!tree.isReady) {
            markAsFailed(globalClass.getString(R.string.failed_to_extract_archive))
            return
        }

        if (tree.isFailed) {
            markAsFailed(globalClass.getString(R.string.failed_to_extract_archive))
            return
        }

        // Saat melanjutkan task setelah konflik, daftar yang sudah dibuat
        // dipakai kembali agar status per file tidak hilang.
        if (pendingFiles.isEmpty()) {
            tree.listEntriesInOrder().forEach { node ->
                pendingFiles.add(
                    TaskContentItem(
                        content = ArchiveFileHolder(tree, node),
                        relativePath = node.path,
                        status = TaskContentStatus.PENDING
                    )
                )
            }
        }

        progressMonitor.totalContent = pendingFiles.size
        progressMonitor.remainingContent = pendingFiles.size

        if (pendingFiles.isEmpty()) {
            finalizeTask()
            return
        }

        tree.openExtractionSession().use { session ->
            pendingFiles.forEachIndexed { index, item ->
                if (aborted) {
                    progressMonitor.status = TaskStatus.PAUSED
                    return
                }

                if (item.status isNot TaskContentStatus.PENDING
                    && item.status isNot TaskContentStatus.REPLACE
                    && item.status isNot TaskContentStatus.CONFLICT
                ) {
                    return@forEachIndexed
                }

                val node = (item.content as ArchiveFileHolder).node
                updateProgress(index, node.name)

                if (item.status == TaskContentStatus.CONFLICT && !handleConflict(item)) {
                    return
                }

                if (item.status == TaskContentStatus.SKIP) {
                    return@forEachIndexed
                }

                val destinationFile = resolveDestinationFile(destinationRoot, node.path)

                if (destinationFile == null) {
                    item.status = TaskContentStatus.FAILED
                    return@forEachIndexed
                }

                if (node.isDirectory) {
                    if (destinationFile.exists() && !destinationFile.isDirectory) {
                        if (!handleConflict(item)) return

                        if (item.status == TaskContentStatus.SKIP) {
                            return@forEachIndexed
                        }

                        if (item.status == TaskContentStatus.REPLACE) {
                            destinationFile.delete()
                        }
                    }

                    item.status = if (destinationFile.isDirectory || destinationFile.mkdirs()) {
                        TaskContentStatus.SUCCESS
                    } else {
                        TaskContentStatus.FAILED
                    }
                    return@forEachIndexed
                }

                if (destinationFile.exists() && item.status == TaskContentStatus.PENDING) {
                    if (!handleConflict(item)) return

                    if (item.status == TaskContentStatus.SKIP) {
                        return@forEachIndexed
                    }
                }

                if (item.status == TaskContentStatus.REPLACE && destinationFile.exists()) {
                    if (destinationFile.isDirectory) {
                        destinationFile.deleteRecursively()
                    } else {
                        destinationFile.delete()
                    }
                }

                if (item.status == TaskContentStatus.PENDING ||
                    item.status == TaskContentStatus.REPLACE
                ) {
                    item.status = if (session.extract(node, destinationFile)) {
                        TaskContentStatus.SUCCESS
                    } else {
                        TaskContentStatus.FAILED
                    }
                }
            }
        }

        finalizeTask()
    }

    private fun resolveDestinationFile(root: File, entryPath: String): File? {
        val destination = File(root, entryPath)
        val rootPath = root.canonicalPath.trimEnd(File.separatorChar) + File.separator

        return if (destination.canonicalPath.startsWith(rootPath)) destination else null
    }

    private fun updateProgress(index: Int, itemName: String) {
        progressMonitor.apply {
            contentName = itemName
            remainingContent = pendingFiles.size - (index + 1)
            progress = (index + 1f) / pendingFiles.size
        }
    }

    private fun handleConflict(item: TaskContentItem): Boolean {
        return when (commonConflictResolution) {
            TaskContentStatus.SKIP -> {
                item.status = TaskContentStatus.SKIP
                true
            }

            TaskContentStatus.ASK -> {
                item.status = TaskContentStatus.CONFLICT
                progressMonitor.status = TaskStatus.CONFLICT
                globalClass.taskManager.taskInterceptor.interceptTask(item, this)
                false
            }

            TaskContentStatus.REPLACE -> {
                item.status = TaskContentStatus.REPLACE
                true
            }

            else -> true
        }
    }

    private suspend fun finalizeTask() {
        if (progressMonitor.status != TaskStatus.RUNNING) return

        val hasFailure = pendingFiles.any { it.status == TaskContentStatus.FAILED }

        progressMonitor.status = if (hasFailure) TaskStatus.FAILED else TaskStatus.SUCCESS
        progressMonitor.progress = 1f
        progressMonitor.remainingContent = 0
        progressMonitor.summary = buildString {
            pendingFiles.forEach { content ->
                append(content.content.displayName)
                append(" -> ")
                append(content.status.name)
                append("\n")
            }
        }

        // Hasil ekstraksi langsung muncul jika tujuannya folder yang sedang dibuka.
        withContext(Dispatchers.Main) {
            (globalClass.mainActivityManager.getActiveTab() as? FilesTab)?.quickReloadFiles()
        }
    }

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

    private fun handleTaskError(e: Exception) {
        logger.logError(e)
        markAsFailed(
            globalClass.resources.getString(
                R.string.task_summary_failed,
                e.message ?: globalClass.getString(R.string.unknown_error)
            )
        )
    }

    private fun createTaskMetadata(): TaskMetadata {
        val time = System.currentTimeMillis().toFormattedDate()

        return TaskMetadata(
            id = id,
            creationTime = time,
            title = globalClass.resources.getString(R.string.extract),
            subtitle = globalClass.resources.getString(R.string.task_subtitle, 1),
            displayDetails = archive.displayName,
            fullDetails = buildString {
                append(archive.displayName)
                append("\n\n")
                append(time)
            },
            isCancellable = true,
            canMoveToBackground = true
        )
    }
}
