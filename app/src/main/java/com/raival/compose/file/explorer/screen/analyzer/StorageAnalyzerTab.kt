package com.raival.compose.file.explorer.screen.analyzer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.screen.analyzer.model.StorageNode
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.task.CopyTask
import com.raival.compose.file.explorer.screen.main.tab.files.task.CopyTaskParameters
import com.raival.compose.file.explorer.screen.main.tab.files.task.DeleteTask
import com.raival.compose.file.explorer.screen.main.tab.files.task.DeleteTaskParameters
import com.raival.compose.file.explorer.screen.main.tab.Tab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tab analisis penyimpanan: seluruh isi penyimpanan dipindai sekali di awal,
 * lalu navigasi antar folder hanya membaca pohon hasil pindai di memori.
 * Pemindaian tetap berjalan saat pengguna pindah tab; hanya dibatalkan bila
 * tabnya ditutup.
 */
class StorageAnalyzerTab(private val rootFolder: File) : Tab() {
    override val id = globalClass.generateUid()
    override val header = globalClass.getString(R.string.storage_analyzer)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var scanJob: Job? = null

    /** Penanda pemindaian terbaru agar job lama tidak menimpa state job baru. */
    private var scanGeneration = 0

    var rootNode: StorageNode? = null
        private set

    var currentNode by mutableStateOf<StorageNode?>(null)
        private set

    val entries = mutableStateListOf<StorageNode>()

    var totalSize by mutableLongStateOf(0L)
        private set

    var totalItems by mutableIntStateOf(0)
        private set

    var scannedFiles by mutableIntStateOf(0)
        private set

    /** Ukuran berkas yang sudah terbaca selama pemindaian berjalan. */
    var scannedSize by mutableLongStateOf(0L)
        private set

    var isScanning by mutableStateOf(false)
        private set

    var scanFailed by mutableStateOf(false)
        private set

    private var hasScanned = false

    val canGoUp: Boolean
        get() = currentNode?.parent != null

    /** Memindai ulang folder aktif (atau akar bila pohon belum ada). */
    fun refresh() {
        val target = currentNode
        val folder = target?.file ?: rootFolder
        val generation = ++scanGeneration
        scanJob?.cancel()
        hasScanned = true

        scanJob = scope.launch {
            isScanning = true
            scanFailed = false
            scannedFiles = 0
            scannedSize = 0L

            try {
                val fresh = withContext(Dispatchers.IO) {
                    try {
                        StorageScanner.scan(folder) { count, bytes ->
                            scannedFiles = count
                            scannedSize = bytes
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                }

                if (fresh == null) {
                    if (generation == scanGeneration) scanFailed = true
                    return@launch
                }

                if (generation != scanGeneration) return@launch

                if (target == null) {
                    rootNode = fresh
                    showNode(fresh)
                } else {
                    applyRescan(target, fresh)
                }
            } finally {
                if (generation == scanGeneration) isScanning = false
            }
        }
    }

    /** Pindai saat layar pertama kali tampil. */
    fun refreshIfNeeded() {
        if (!hasScanned && !isScanning) refresh()
    }

    fun openFolder(entry: StorageNode) {
        if (!entry.isFolder) return
        showNode(entry)
    }

    override fun onBackPressed(): Boolean {
        val parent = currentNode?.parent ?: return false
        showNode(parent)
        return true
    }

    /**
     * Menghapus semua entri terpilih dalam satu tugas. Mengikuti preferensi
     * aplikasi: dipindahkan ke recycle bin bila opsi itu aktif.
     */
    fun deleteEntries(targets: List<StorageNode>) {
        if (targets.isEmpty()) return
        val holders = targets.map { LocalFileHolder(it.file) }
        val recycleBinPrefix = globalClass.recycleBinDir.file.absolutePath + File.separator
        val allInRecycleBin = targets.all { it.file.absolutePath.startsWith(recycleBinPrefix) }

        if (!allInRecycleBin && globalClass.preferencesManager.moveToRecycleBin) {
            val newDir = File(globalClass.recycleBinDir.file, System.currentTimeMillis().toString())
            if (!newDir.mkdirs()) {
                globalClass.showMsg(globalClass.getString(R.string.unable_to_move_to_recycle_bin))
                return
            }
            globalClass.taskManager.addTaskAndRun(
                CopyTask(holders, deleteSourceFiles = true),
                CopyTaskParameters(LocalFileHolder(newDir))
            )
        } else {
            globalClass.taskManager.addTaskAndRun(
                DeleteTask(holders),
                DeleteTaskParameters()
            )
        }
        pruneAll(targets)
    }

    /** Memindahkan entri terpilih ke folder [destination]. */
    fun moveEntries(targets: List<StorageNode>, destination: File) {
        if (targets.isEmpty()) return
        globalClass.taskManager.addTaskAndRun(
            CopyTask(targets.map { LocalFileHolder(it.file) }, deleteSourceFiles = true),
            CopyTaskParameters(LocalFileHolder(destination))
        )

        // Bila tujuan ada di dalam pohon, statistiknya ikut disesuaikan.
        val destinationNode = findNode(destination.absolutePath)
        val movedSize = targets.sumOf { it.size }
        val movedItems = targets.sumOf { it.itemCount }
        pruneAll(targets)
        if (destinationNode != null) {
            var node: StorageNode? = destinationNode
            while (node != null) {
                node.size += movedSize
                node.itemCount += movedItems
                node = node.parent
            }
            currentNode?.let { showNode(it) }
        }
    }

    /** Folder tujuan valid: bukan dirinya sendiri, bukan turunannya, dan beda folder. */
    fun canMoveTo(entry: StorageNode, destination: File): Boolean {
        if (destination.absolutePath == entry.file.parentFile?.absolutePath) return false
        if (!entry.isFolder) return true
        val sourcePrefix = entry.file.absolutePath + File.separator
        return !(destination.absolutePath + File.separator).startsWith(sourcePrefix)
    }

    /**
     * Membuang entri dari pohon setelah operasi dimulai, lalu menyesuaikan
     * ukuran seluruh leluhurnya supaya tampilan langsung konsisten.
     */
    private fun pruneAll(targets: List<StorageNode>) {
        targets.forEach { entry ->
            val parent = entry.parent ?: return@forEach
            parent.children.remove(entry)

            var node: StorageNode? = parent
            while (node != null) {
                node.size = (node.size - entry.size).coerceAtLeast(0L)
                node.itemCount = (node.itemCount - entry.itemCount).coerceAtLeast(0)
                node = node.parent
            }
        }

        currentNode?.let { showNode(it) }
    }

    /** Mengganti isi [target] dengan hasil pindai [fresh] dan menyesuaikan leluhur. */
    private fun applyRescan(target: StorageNode, fresh: StorageNode) {
        val sizeDelta = fresh.size - target.size
        val itemDelta = fresh.itemCount - target.itemCount

        target.children.clear()
        fresh.children.forEach { child ->
            child.parent = target
            target.children.add(child)
        }
        target.size = fresh.size
        target.itemCount = fresh.itemCount
        target.accessible = fresh.accessible

        var node = target.parent
        while (node != null) {
            node.size = (node.size + sizeDelta).coerceAtLeast(0L)
            node.itemCount = (node.itemCount + itemDelta).coerceAtLeast(0)
            node = node.parent
        }

        showNode(target)
    }

    private fun showNode(node: StorageNode) {
        currentNode = node
        entries.clear()
        entries.addAll(node.children.sortedByDescending { it.size })
        totalSize = node.size
        totalItems = node.itemCount
        requestHomeToolbarUpdate()
    }

    /** Mencari simpul berdasarkan path di dalam pohon yang sudah dipindai. */
    private fun findNode(path: String): StorageNode? {
        val root = rootNode ?: return null
        val pending = ArrayDeque<StorageNode>()
        pending.addLast(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            if (node.file.absolutePath == path) return node
            node.children.forEach { pending.addLast(it) }
        }
        return null
    }

    override fun onTabRemoved() {
        scanJob?.cancel()
    }

    override suspend fun getTitle() = when {
        currentNode == null -> globalClass.getString(R.string.storage_analyzer)
        currentNode?.parent == null -> globalClass.getString(R.string.internal_storage)
        else -> currentNode?.name.orEmpty()
    }

    override suspend fun getSubtitle() =
        currentNode?.file?.absolutePath ?: rootFolder.absolutePath
}
