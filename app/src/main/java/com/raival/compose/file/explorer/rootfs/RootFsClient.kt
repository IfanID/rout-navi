package com.raival.compose.file.explorer.rootfs

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.ExtendedFile
import com.topjohnwu.superuser.nio.FileSystemManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RootFsEntry(
    val name: String,
    val isDirectory: Boolean,
    val isSymlink: Boolean,
    val isBrokenSymlink: Boolean,
    val size: Long,
    val modified: Long,
)

/**
 * Klien ke filesystem NIO di proses root. Semua pembacaan direktori terjadi
 * di proses root; aplikasi hanya menerima daftar nama beserta metadatanya.
 */
object RootFsClient {
    private const val RETRY_DELAY_MS = 60_000L

    private val serviceLock = Mutex()
    private var fileSystem: FileSystemManager? = null

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    private var lastFailureAt = 0L

    init {
        // FLAG_MOUNT_MASTER wajib: tanpa ini shell root mewarisi mount
        // namespace aplikasi sehingga /data/data hanya terlihat sebagian.
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(60L)
        )
    }

    suspend fun isRootAvailable(): Boolean {
        return try {
            val available = withContext(Dispatchers.IO) { Shell.getShell().isRoot }
            if (available) lastError = null else lastError = "shell bukan root"
            available
        } catch (e: Throwable) {
            lastError = "shell root gagal: ${e.message}"
            false
        }
    }

    suspend fun list(directory: File): List<RootFsEntry>? = serviceLock.withLock {
        // Service yang gagal jangan dicoba ulang di setiap folder.
        if (System.currentTimeMillis() - lastFailureAt < RETRY_DELAY_MS) {
            return@withLock null
        }

        val rootFileSystem = ensureFileSystem() ?: return@withLock null

        try {
            val remoteDirectory: ExtendedFile =
                rootFileSystem.getFile(directory.absolutePath)
            val children = remoteDirectory.listFiles() ?: return@withLock null

            ArrayList<RootFsEntry>(children.size).apply {
                children.forEach { child ->
                    val isDirectory = child.isDirectory
                    val isSymlink = child.isSymlink

                    add(
                        RootFsEntry(
                            name = child.name,
                            isDirectory = isDirectory,
                            isSymlink = isSymlink,
                            isBrokenSymlink = isSymlink && !child.exists(),
                            size = if (isDirectory) 0L else child.length(),
                            modified = child.lastModified(),
                        )
                    )
                }
            }.also {
                lastError = null
            }
        } catch (e: Throwable) {
            lastError = "NIO gagal ${directory.absolutePath}: ${e.message}"
            fileSystem = null
            lastFailureAt = System.currentTimeMillis()
            null
        }
    }

    suspend fun openInputStream(path: File): InputStream? = serviceLock.withLock {
        val rootFileSystem = ensureFileSystem() ?: return@withLock null

        try {
            rootFileSystem.getFile(path.absolutePath).newInputStream().also {
                lastError = null
            }
        } catch (e: Throwable) {
            lastError = "buka file NIO gagal ${path.absolutePath}: ${e.message}"
            null
        }
    }

    suspend fun stat(path: File): RootFsEntry? = serviceLock.withLock {
        val rootFileSystem = ensureFileSystem() ?: return@withLock null

        try {
            val file = rootFileSystem.getFile(path.absolutePath)
            val isDirectory = file.isDirectory
            val isSymlink = file.isSymlink

            RootFsEntry(
                name = file.name,
                isDirectory = isDirectory,
                isSymlink = isSymlink,
                isBrokenSymlink = isSymlink && !file.exists(),
                size = if (isDirectory) 0L else file.length(),
                modified = file.lastModified(),
            ).also {
                lastError = null
            }
        } catch (e: Throwable) {
            lastError = "stat NIO gagal ${path.absolutePath}: ${e.message}"
            null
        }
    }

    suspend fun exists(path: File): Boolean? = serviceLock.withLock {
        val rootFileSystem = ensureFileSystem() ?: return@withLock null

        try {
            rootFileSystem.getFile(path.absolutePath).exists()
        } catch (e: Throwable) {
            lastError = "exists NIO gagal ${path.absolutePath}: ${e.message}"
            null
        }
    }

    /**
     * Menyambung service root lebih awal supaya akses pertama (misalnya
     * dialog Markah atau folder sistem) tidak menunggu proses bind.
     * Mengembalikan true kalau filesystem root siap dipakai.
     */
    suspend fun warmUp(): Boolean = serviceLock.withLock {
        if (fileSystem != null) return@withLock true

        // Jangan mengulang bind yang baru saja gagal.
        if (System.currentTimeMillis() - lastFailureAt < RETRY_DELAY_MS) {
            return@withLock false
        }

        ensureFileSystem() != null
    }

    private suspend fun ensureFileSystem(): FileSystemManager? {
        fileSystem?.let { return it }
        if (!isRootAvailable()) return null

        return withContext(Dispatchers.Main.immediate) {
            try {
                withTimeout(15_000L) {
                    suspendCancellableCoroutine<FileSystemManager?> { continuation ->
                        val connection = object : ServiceConnection {
                            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                                if (binder == null) {
                                    if (continuation.isActive) continuation.resume(null)
                                    return
                                }

                                try {
                                    val newFileSystem = FileSystemManager.getRemote(binder)
                                    fileSystem = newFileSystem
                                    if (continuation.isActive) continuation.resume(newFileSystem)
                                } catch (e: Throwable) {
                                    if (continuation.isActive) {
                                        continuation.resumeWithException(e)
                                    }
                                }
                            }

                            override fun onServiceDisconnected(name: ComponentName?) {
                                fileSystem = null
                                if (continuation.isActive) continuation.resume(null)
                            }

                            override fun onBindingDied(name: ComponentName?) {
                                fileSystem = null
                                if (continuation.isActive) continuation.resume(null)
                            }

                            override fun onNullBinding(name: ComponentName?) {
                                fileSystem = null
                                if (continuation.isActive) continuation.resume(null)
                            }
                        }

                        RootService.bind(
                            Intent(globalClass, RootFsService::class.java),
                            connection
                        )

                        continuation.invokeOnCancellation {
                            runCatching { RootService.unbind(connection) }
                        }
                    }
                }
            } catch (e: Throwable) {
                lastError = "root service gagal: ${e.message}"
                lastFailureAt = System.currentTimeMillis()
                null
            }
        }
    }
}
