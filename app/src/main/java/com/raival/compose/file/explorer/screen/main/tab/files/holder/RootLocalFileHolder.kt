package com.raival.compose.file.explorer.screen.main.tab.files.holder

import com.raival.compose.file.explorer.common.toFormattedSize
import com.raival.compose.file.explorer.rootfs.RootFsClient
import com.raival.compose.file.explorer.screen.main.tab.files.provider.RootShell
import java.io.File
import java.io.InputStream

/**
 * Entri yang dibaca lewat root shell.
 *
 * Metadata disimpan dari hasil `stat` karena [File] biasa bisa gagal membaca
 * atribut folder yang dilindungi SELinux. Hanya bisa dibaca; operasi tulis
 * tetap ditolak supaya app tidak mencoba mengubah sistem tanpa izin.
 */
class RootLocalFileHolder(
    file: File,
    private val folder: Boolean,
    private val length: Long,
    private val modified: Long,
    val isSymlink: Boolean = false,
    val isBrokenSymlink: Boolean = false,
) : LocalFileHolder(file) {
    override val isFolder: Boolean get() = folder
    override val size: Long get() = length
    override val lastModified: Long get() = modified
    override val canRead: Boolean get() = true
    override val canWrite: Boolean get() = false

    // Entri berasal dari listing root, jadi jangan ditolak hanya karena
    // File.exists() tidak bisa menembus folder induk yang dilindungi.
    override suspend fun isValid(): Boolean = true

    // Turunan folder ini juga harus dibaca lewat root agar tidak kembali
    // memakai daftar parsial dari proses aplikasi.
    override suspend fun listChildren(): List<LocalFileHolder>? =
        RootShell.list(file) ?: super.listChildren()

    // Isi file root dibaca lewat pipe dari root service NIO.
    override suspend fun openInputStream(): InputStream? =
        RootFsClient.openInputStream(file) ?: super.openInputStream()

    /**
     * Parent tetap berada di konteks root supaya back dan breadcrumb tidak
     * berhenti karena LocalFileHolder biasa tidak bisa membaca /data.
     */
    override suspend fun getParent(): ContentHolder? {
        val parent = file.parentFile ?: return null

        if (parent.absolutePath == File.separator) {
            return RootFileHolder()
        }

        return RootLocalFileHolder(
            file = parent,
            folder = true,
            length = parent.length(),
            modified = parent.lastModified(),
        )
    }

    override suspend fun getDetails(): String = buildString {
        append(getLastModifiedDate())
        if (!folder) {
            append(" | ")
            // `size` dipakai agar tidak bentrok dengan StringBuilder.length.
            append(size.toFormattedSize())
            append(" | ")
            append(extension)
        }
    }
}
