package com.rout.navi.screen.main.tab.files.holder

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import com.anggrayudi.storage.file.getBasePath
import com.anggrayudi.storage.file.mimeType
import com.rout.navi.App.Companion.globalClass
import com.rout.navi.R
import com.rout.navi.common.drawableToBitmap
import com.rout.navi.common.emptyString
import com.rout.navi.common.fromJson
import com.rout.navi.common.hasParent
import com.rout.navi.common.isNot
import com.rout.navi.common.toFormattedSize
import com.rout.navi.common.toFormattedTime
import com.rout.navi.screen.main.tab.files.FilesTab
import com.rout.navi.screen.main.tab.files.misc.ContentCount
import com.rout.navi.screen.main.tab.files.misc.DefaultOpeningMethods
import com.rout.navi.screen.main.tab.files.misc.FileMimeType
import com.rout.navi.screen.main.tab.files.misc.FileMimeType.anyFileType
import com.rout.navi.screen.main.tab.files.misc.FileMimeType.codeFileType
import com.rout.navi.screen.main.tab.files.misc.FileMimeType.editableFileType
import com.rout.navi.screen.main.tab.files.misc.FileMimeType.prismPrefsFileType
import com.rout.navi.screen.main.tab.files.misc.FileMimeType.subFileType
import com.rout.navi.screen.main.tab.files.misc.FileMimeType.subtitleBinaryFileType
import com.rout.navi.screen.main.tab.files.provider.RootShell
import com.rout.navi.screen.viewer.audio.AudioPlayerActivity
import com.rout.navi.screen.viewer.document.DocumentViewerActivity
import com.rout.navi.screen.viewer.image.ImageViewerActivity
import com.rout.navi.screen.viewer.pdf.PdfViewerActivity
import com.rout.navi.screen.viewer.video.VideoPlayerActivity
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream

open class LocalFileHolder(val file: File) : ContentHolder() {
    private var folderCount = 0
    private var fileCount = 0
    private var timestamp = -1L

    override val displayName: String by lazy { file.name }

    var details = emptyString

    override val isFolder: Boolean by lazy { file.isDirectory }

    override val lastModified: Long
        get() = file.lastModified().also {
            if (timestamp == -1L) timestamp = it
        }

    override val size: Long by lazy { file.length() }

    override val uniquePath: String by lazy { file.absolutePath }

    override val extension: String by lazy { file.extension.lowercase() }

    override val canAddNewContent: Boolean = true

    override val canRead: Boolean by lazy { file.canRead() }

    override val canWrite: Boolean by lazy { file.canWrite() }

    val mimeType by lazy {
        if (FileMimeType.isTextConfigFile(file.name, extension)) {
            "text/plain"
        } else {
            file.mimeType ?: anyFileType
        }
    }

    val basePath by lazy { file.getBasePath(globalClass) }

    override suspend fun getDetails(): String {
        val separator = " | "

        // Video yang ada di riwayat menampilkan kemajuan tontonannya. Data
        // dicari dengan aturan yang sama seperti saat dicatat, supaya tidak
        // pernah jatuh ke versi lama.
        if (!file.isDirectory && FileMimeType.videoFileType.contains(extension)) {
            val entry = globalClass.preferencesManager.watchEntryFor(file.path)
            val kemajuan = when {
                entry == null -> null
                entry.isFinished -> globalClass.getString(R.string.video_finished)
                // Persen tampil terpisah di ujung kanan baris, jadi keterangan
                // cukup memuat posisi dan durasi tontonan.
                entry.durationMs > 0 -> globalClass.getString(
                    R.string.history_position,
                    entry.positionMs.toFormattedTime(),
                    entry.durationMs.toFormattedTime()
                )
                // Entri lama belum punya durasi; jangan kembali ke keterangan
                // biasa yang menghapus jejak riwayatnya.
                else -> globalClass.getString(R.string.history_missing_progress)
            }
            if (kemajuan != null) return "$kemajuan$separator${getLastModifiedDate()}"
        }

        // Cache lama tidak dipakai lagi: isinya bisa dibuat sebelum riwayat
        // diperbaiki, sehingga keterangan tampak tidak punya informasi riwayat.
        return buildString {
            append(getLastModifiedDate())
            if (file.isDirectory) {
                if (globalClass.preferencesManager.showFolderContentCount && file.canRead()) {
                    append(separator)
                    append(getFormattedFileCount())
                }
            } else {
                append(separator)
                append(file.length().toFormattedSize())
                append(separator)
                append(file.extension)
            }
        }
    }

    override suspend fun isValid(): Boolean = file.exists()

    override suspend fun listContent(): ArrayList<LocalFileHolder> {
        folderCount = 0
        fileCount = 0

        val children = listChildren() ?: return arrayListOf()

        // Hitung di sini supaya getContentCount() tidak perlu membaca ulang.
        children.forEach { if (it.isFolder) folderCount++ else fileCount++ }

        return ArrayList(children)
    }

    /**
     * Folder sistem seperti /data hanya menampilkan sebagian isi bagi proses
     * aplikasi biasa. Kalau root tersedia, daftar dari shell root dipakai agar
     * semua entri muncul, bukan hanya folder yang kebetulan bisa dibaca.
     */
    protected open suspend fun listChildren(): List<LocalFileHolder>? {
        if (!isFolder) return null

        val direct = file.listFiles()

        if (direct != null && !RootShell.prefersRootListing(file)) {
            // Folder penyimpanan dibaca langsung supaya operasi tulis tetap
            // jalan. Pengecualian: entri seperti Android/data dan Android/obb
            // tidak bisa dibaca app biasa, jadi daftarnya diambil lewat root
            // agar isinya bisa dibuka dan tidak ditandai terkunci.
            if (direct.none { !it.canRead() }) {
                return direct.map { LocalFileHolder(it) }
            }
        }

        RootShell.list(file)?.let { return it }

        return direct?.map { LocalFileHolder(it) }
    }

    override suspend fun getParent(): ContentHolder? =
        file.parentFile?.let { LocalFileHolder(it) }

    override fun open(
        context: Context,
        anonymous: Boolean,
        skipSupportedExtensions: Boolean,
        customMimeType: String?
    ) {
        val defaultOpeningMethods =
            fromJson<DefaultOpeningMethods>(globalClass.preferencesManager.defaultOpeningMethods)
                ?: DefaultOpeningMethods()
        defaultOpeningMethods.openingMethods.forEach {
            if (it.extension == extension) {
                openFileWithPackage(context, it.packageName, it.className)
                return
            }
        }

        if (handleSupportedFiles(skipSupportedExtensions, context)) {
            return
        }

        Intent(Intent.ACTION_VIEW).let { newIntent ->
            newIntent.setDataAndType(
                createUri(),
                customMimeType ?: if (anonymous) anyFileType else mimeType
            )

            newIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                        or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                        or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            try {
                context.startActivity(newIntent)
            } catch (_: ActivityNotFoundException) {
                if (!anonymous) {
                    open(context, anonymous = true, skipSupportedExtensions = true, null)
                } else {
                    globalClass.showMsg(R.string.no_app_can_open_file)
                }
            } catch (e: Exception) {
                with(globalClass) {
                    logger.logError(e)
                    showMsg(getString(R.string.failed_to_open_this_file))
                }
            }
        }
    }

    override suspend fun getContentCount(): ContentCount {
        if (fileCount == 0 && folderCount == 0) {
            if (RootShell.prefersRootListing(file)) {
                // Folder sistem: hitung lewat jalur root yang sama dengan
                // listContent agar angkanya tidak parsial.
                listContent()
            } else {
                file.listFiles()?.let { list ->
                    list.forEach {
                        if (it.isFile) fileCount++ else folderCount++
                    }
                }
            }
        }

        return ContentCount(fileCount, folderCount)
    }

    override suspend fun createSubFile(name: String, onCreated: (ContentHolder?) -> Unit) {
        File(file, name).let { newFile ->
            if (newFile.createNewFile()) {
                onCreated(LocalFileHolder(newFile))
                return
            }
        }
        onCreated(null)
    }

    override suspend fun createSubFolder(name: String, onCreated: (ContentHolder?) -> Unit) {
        File(file, name).let { newFolder ->
            if (newFolder.exists() || newFolder.mkdir()) {
                onCreated(LocalFileHolder(newFolder))
                return
            }
        }
        onCreated(null)
    }

    override suspend fun findFile(name: String): LocalFileHolder? {
        File(file, name).let {
            if (it.exists()) {
                return LocalFileHolder(it)
            }
        }
        return null
    }

    fun exists() = runBlocking { isValid() }

    fun hasSourceChanged() = timestamp isNot -1L && lastModified isNot timestamp

    fun resetCachedTimestamp() {
        timestamp = lastModified
    }

    fun getAppsHandlingFile(mimeType: String = emptyString): List<OpenWithActivityHolder> {
        val packageManager: PackageManager = globalClass.packageManager

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(createUri(), mimeType.ifEmpty { this@LocalFileHolder.mimeType })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
        }

        val appsList = ArrayList<OpenWithActivityHolder>()

        packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_ALL
        ).onEach {
            globalClass.grantUriPermission(
                it.activityInfo.packageName,
                createUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            appsList.add(
                OpenWithActivityHolder(
                    label = it.activityInfo.loadLabel(packageManager).toString(),
                    name = it.activityInfo.name,
                    packageName = it.activityInfo.packageName,
                    icon = it.activityInfo.loadIcon(packageManager).drawableToBitmap(),
                )
            )
        }

        return appsList
    }

    fun openFileWithPackage(
        context: Context,
        packageName: String,
        className: String,
        extras: Bundle? = null,
        useDirectFileUri: Boolean = false
    ) {
        // Pemutar video dan audio berada di paket yang sama, jadi file:// aman
        // dipakai dan libVLC bisa membacanya langsung tanpa FileProvider.
        val uri = if (useDirectFileUri) Uri.fromFile(file) else createUri()

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                        or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                        or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            if (extras != null) putExtras(extras)
            setPackage(packageName)
            setClassName(packageName, className)
        }

        if (intent.resolveActivity(globalClass.packageManager) != null) {
            context.startActivity(intent)
        } else {
            globalClass.showMsg("No app found to open this file.")
        }
    }

    /**
     * Membuka video di pemutar. Posisi awal diambil dari riwayat tontonan bila
     * sakelar lanjut otomatis menyala, jadi klik pada video yang pernah
     * ditonton langsung berlanjut tanpa perlu ke halaman Histori.
     */
    fun openVideoWithResume(context: Context, fromStart: Boolean = false) {
        val extras = Bundle()
        val resumeEnabled = com.rout.navi.screen.viewer.video.VideoPlayerPreferences
            .get(globalClass).resumePlayback
        val position = if (resumeEnabled && !fromStart) {
            runBlocking {
                com.rout.navi.screen.main.tab.files.provider.StorageProvider
                    .getWatchEntry(file.path)?.resumePositionMs ?: 0L
            }
        } else {
            0L
        }
        if (fromStart) {
            runBlocking {
                globalClass.preferencesManager.clearWatchPosition(file.path)
            }
        }
        extras.putLong(VideoPlayerActivity.EXTRA_START_POSITION, position)
        openFileWithPackage(
            context,
            context.packageName,
            VideoPlayerActivity::class.java.name,
            extras = extras,
            useDirectFileUri = true
        )
    }

    fun writeText(text: String) {
        file.writeText(text)
    }

    fun readText() = file.readText()

    /** Implementasi default memakai file lokal; holder root akan override. */
    open suspend fun openInputStream(): InputStream? = try {
        file.inputStream()
    } catch (e: Exception) {
        null
    }

    private fun handleSupportedFiles(skipSupportedExtensions: Boolean, context: Context): Boolean {
        if (prismPrefsFileType == extension) {
            val activeTab = globalClass.mainActivityManager.getActiveTab()
            if (activeTab is FilesTab) {
                activeTab.toggleImportPrefsDialog(this)
                return true
            }
        }

        if (FileMimeType.supportedArchiveFileType.contains(extension)) {
            if (isApk() && skipSupportedExtensions) return false
            globalClass.archiveManager.openArchive(this)
            return true
        }

        if (skipSupportedExtensions) return false

        // VobSub menyimpan gambar di balik ekstensi .sub, isinya biner sehingga
        // tidak bisa dibuka sebagai teks. MicroDVD (.sub teks) tetap dibuka.
        if (subtitleBinaryFileType.contains(extension) ||
            (extension == subFileType && isVobSubFile())
        ) {
            globalClass.showMsg(R.string.subtitle_bitmap_not_supported)
            return true
        }

        if (FileMimeType.isTextConfigFile(file.name, extension) ||
            codeFileType.contains(extension) || editableFileType.contains(extension)
        ) {
            // .ts dipakai dua hal berbeda: TypeScript dan transport stream MPEG.
            // Isi berkasnya yang menentukan, bukan ukurannya.
            if (!(extension in FileMimeType.videoFileType && isMpegTransportStream())) {
                globalClass.textEditorManager.openTextEditor(
                    this,
                    context
                )
                return true
            }
        }

        // Ekstensi yang tidak terdaftar tetap dikenali dari MIME yang dikirim
        // sistem, kecuali playlist karena pemutar ini hanya memutar satu berkas.
        val mime = mimeType.lowercase()
        val isPlaylistMime = "mpegurl" in mime || "scpls" in mime || "playlist" in mime

        if (FileMimeType.videoFileType.contains(extension) ||
            (mime.startsWith("video/") && !isPlaylistMime)
        ) {
            openVideoWithResume(context)
            return true
        }

        if (FileMimeType.audioFileType.contains(extension) ||
            (mime.startsWith("audio/") && !isPlaylistMime)
        ) {
            openFileWithPackage(
                context,
                context.packageName,
                AudioPlayerActivity::class.java.name,
                useDirectFileUri = true
            )
            return true
        }

        if (FileMimeType.imageFileType.contains(extension)) {
            // Dibuka dari kategori gambar (mis. tab Home): seluruh foto yang
            // tampil di kategori dikirim agar geseran pager melintasi folder
            // — persis urutan daftar yang sedang dilihat. Selain itu hanya
            // folder asal yang dikirim, jadi pager terbatas foto sefolder.
            val extras = Bundle().apply {
                putString(ImageViewerActivity.EXTRA_NAME, file.name)
                val categoryPaths = (globalClass.mainActivityManager.getActiveTab() as? FilesTab)
                    ?.takeIf { tab ->
                        val folder = tab.activeFolder
                        folder is VirtualFileHolder && folder.type == VirtualFileHolder.IMAGE
                    }
                    ?.let { tab ->
                        ArrayList(
                            tab.activeFolderContent
                                .filterIsInstance<LocalFileHolder>()
                                .filter { FileMimeType.imageFileType.contains(it.extension) }
                                .map { it.file.absolutePath }
                        )
                    }
                if (categoryPaths != null && categoryPaths.size > 1) {
                    putStringArrayList(ImageViewerActivity.EXTRA_PATHS, categoryPaths)
                }
                putString(ImageViewerActivity.EXTRA_FOLDER, file.parent)
            }
            openFileWithPackage(
                context,
                context.packageName,
                ImageViewerActivity::class.java.name,
                extras
            )
            return true
        }

        if (FileMimeType.pdfFileType.contains(extension)) {
            openFileWithPackage(
                context,
                context.packageName,
                PdfViewerActivity::class.java.name
            )
            return true
        }

        // Dokumen Office modern dibaca oleh penampil dokumen bawaan.
        if (FileMimeType.officeDocumentFileType.contains(extension)) {
            openFileWithPackage(
                context,
                context.packageName,
                DocumentViewerActivity::class.java.name
            )
            return true
        }

        return false
    }

    /**
     * VobSub (.sub) menyimpan paket MPEG-PS, bukan teks. Cek 4 byte awal
     * (`00 00 01 BA`..`BF`) supaya .sub teks MicroDVD tidak ikut tertolak.
     */
    private fun isVobSubFile(): Boolean {
        if (!file.isFile || file.length() < 4) return false

        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(4)
                stream.read(header) == 4 &&
                        header[0] == 0x00.toByte() &&
                        header[1] == 0x00.toByte() &&
                        header[2] == 0x01.toByte() &&
                        (header[3].toInt() and 0xFF) in 0xBA..0xBF
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Transport stream MPEG memakai paket berukuran tetap yang diawali 0x47.
     * Paket 188 byte dipakai .ts, paket 192 byte dipakai .m2ts dengan 4 byte
     * waktu tambahan di depan. Membaca dua paket awal sudah cukup untuk
     * memisahkan video dari TypeScript yang isinya teks biasa.
     */
    private fun isMpegTransportStream(): Boolean {
        if (!file.isFile || file.length() < 189) return false

        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(200)
                val read = stream.read(header)
                if (read <= 0) return@use false
                val sync = 0x47.toByte()
                when {
                    read > 188 && header[0] == sync && header[188] == sync -> true
                    read > 196 && header[4] == sync && header[196] == sync -> true
                    else -> false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun createUri() = FileProvider.getUriForFile(
        globalClass,
        "com.rout.navi.provider",
        file
    )

    fun hasParent(parent: LocalFileHolder): Boolean =
        file.absolutePath.hasParent(parent.file.absolutePath)

    suspend fun getFormattedFileCount(): String {
        val contentCount = getContentCount()

        return getFormattedFileCount(
            contentCount.files,
            contentCount.folders
        )
    }
}