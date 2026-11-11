package com.raival.compose.file.explorer.screen.viewer.document

import android.net.Uri
import android.text.format.Formatter
import com.anggrayudi.storage.extension.toDocumentFile
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.App.Companion.logger
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.name
import com.raival.compose.file.explorer.common.toDisplayPath
import com.raival.compose.file.explorer.common.toFormattedDate
import com.raival.compose.file.explorer.screen.viewer.ViewerInstance
import com.raival.compose.file.explorer.screen.viewer.document.parser.DocumentContent
import com.raival.compose.file.explorer.screen.viewer.document.parser.DocumentParser
import com.raival.compose.file.explorer.screen.viewer.document.webview.documentMime
import java.io.IOException

/**
 * Instance penampil dokumen Microsoft Office. Konten diurai sekali oleh
 * [prepare] lalu dibaca langsung oleh Compose dari [content].
 */
class DocumentViewerInstance(
    override val uri: Uri,
    override val id: String
) : ViewerInstance {

    companion object {
        /** Batas ukuran berkas, konsisten dengan batas text editor (15 MB). */
        const val MAX_SIZE_BYTES = 15L * 1024 * 1024
    }

    /** Alasan gagal memuat dokumen; dipakai layar untuk menampilkan pesan. */
    var failureReason: String = emptyString
        private set

    var content: DocumentContent? = null
        private set

    val name: String by lazy {
        uri.name ?: globalClass.getString(R.string.unknown)
    }

    /** Ekstensi nama berkas, tanpa titik dan huruf kecil. */
    val ext: String by lazy {
        name.substringAfterLast('.', emptyString).lowercase()
    }

    /** Content type dokumen menurut ekstensinya, untuk respons /doc/. */
    val mime: String by lazy { documentMime(ext) }

    /** Halaman HTML vendor yang merender dokumen ini. */
    val viewerPage: String by lazy { when (ext) {
        "docx", "docm", "dotx" -> "docx.html"
        "xlsx", "xls", "xlsm", "xlsb", "xltx" -> "xlsx.html"
        "pptx", "ppsx", "pptm", "potx" -> "pptx.html"
        else -> "unsupported.html"
    } }

    val info: List<Pair<String, String>> by lazy {
        val size = runCatching {
            globalClass.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L
        }.getOrDefault(0L)

        listOf(
            globalClass.getString(R.string.name) to name,
            globalClass.getString(R.string.size) to Formatter.formatFileSize(globalClass, size),
            globalClass.getString(R.string.path) to uri.toDisplayPath(),
            globalClass.getString(R.string.last_modified) to
                    (uri.toDocumentFile(globalClass)?.lastModified() ?: 0L).toFormattedDate()
        )
    }

    /**
     * Mengurai dokumen di thread pemanggil (layar memanggil dari Dispatchers.IO).
     * Berkas terlalu besar sengaja ditolak karena POI butuh memori jauh lebih
     * besar daripada ukuran berkasnya.
     */
    fun prepare(onPrepared: (success: Boolean) -> Unit) {
        try {
            val size = globalClass.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0L

            if (size > MAX_SIZE_BYTES) {
                failureReason = globalClass.getString(
                    R.string.document_too_large,
                    Formatter.formatFileSize(globalClass, MAX_SIZE_BYTES)
                )
                onPrepared(false)
                return
            }

            val extension = name.substringAfterLast('.', emptyString).lowercase()
            val stream = globalClass.contentResolver.openInputStream(uri)
                ?: throw IOException("Tidak bisa membuka stream dokumen")

            stream.use { content = DocumentParser.parse(extension, it) }
            onPrepared(true)
        } catch (e: Exception) {
            logger.logError(e)
            failureReason = globalClass.getString(R.string.failed_to_read_document)
            onPrepared(false)
        }
    }

    override fun onClose() {
        // Konten hanya berupa teks hasil uraian; tidak ada resource native.
        content = null
    }
}
