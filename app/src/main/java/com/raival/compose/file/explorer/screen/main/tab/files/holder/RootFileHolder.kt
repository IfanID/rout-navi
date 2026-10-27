package com.raival.compose.file.explorer.screen.main.tab.files.holder

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.screen.main.tab.files.misc.ContentCount
import com.raival.compose.file.explorer.screen.main.tab.files.provider.RootShell
import java.io.File

class RootFileHolder : ContentHolder() {
    companion object {
        const val rootDir = "/"
    }

    /**
     * Root tidak memakai daftar folder hardcode. Isi "/" dibaca dinamis
     * seperti MaterialFiles, lalu diurutkan berdasarkan nama oleh
     * listSortedContent (nama menaik, folder dulu).
     */
    var contentsCount = ContentCount()
    val content = arrayListOf<LocalFileHolder>()

    override val uniquePath = rootDir
    override val displayName = globalClass.getString(R.string.root_dir)
    override val isFolder = true
    override val lastModified = 0L
    override val size = 0L
    override val extension = emptyString
    override val canAddNewContent = false
    override val canRead = true
    override val canWrite = false

    override suspend fun listContent(): ArrayList<out ContentHolder> {
        // Root dibaca dinamis seperti MaterialFiles: semua entri "/" diambil
        // dari provider root, bukan dari daftar folder hardcode.
        val entries: List<LocalFileHolder> = RootShell.list(File(rootDir))
            ?: File(rootDir).listFiles()
                ?.map { RootLocalFileHolder(it, it.isDirectory, it.length(), it.lastModified()) }
                ?: emptyList()

        content.clear()
        content.addAll(entries)

        val fileCount = content.count { !it.isFolder }
        contentsCount = ContentCount(
            files = fileCount,
            folders = content.size - fileCount
        )

        return ArrayList(content)
    }

    override suspend fun getParent() = null
    override suspend fun getContentCount() = contentsCount
    override suspend fun findFile(name: String) = content.find { it.displayName == name }
    override suspend fun isValid() = true
}
