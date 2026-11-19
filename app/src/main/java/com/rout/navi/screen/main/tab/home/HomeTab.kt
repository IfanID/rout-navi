package com.rout.navi.screen.main.tab.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rout.navi.App.Companion.globalClass
import com.rout.navi.R
import com.rout.navi.common.emptyString
import com.rout.navi.screen.main.tab.Tab
import com.rout.navi.screen.main.tab.apps.AppsTab
import com.rout.navi.screen.main.tab.files.FilesTab
import com.rout.navi.screen.main.tab.files.holder.LocalFileHolder
import com.rout.navi.screen.main.tab.files.holder.VirtualFileHolder
import com.rout.navi.screen.main.tab.files.holder.VirtualFileHolder.Companion.ARCHIVE
import com.rout.navi.screen.main.tab.files.holder.VirtualFileHolder.Companion.AUDIO
import com.rout.navi.screen.main.tab.files.holder.VirtualFileHolder.Companion.DOCUMENT
import com.rout.navi.screen.main.tab.files.holder.VirtualFileHolder.Companion.IMAGE
import com.rout.navi.screen.main.tab.files.holder.VirtualFileHolder.Companion.VIDEO
import com.rout.navi.screen.main.tab.files.provider.StorageProvider
import com.rout.navi.screen.main.tab.files.provider.RootShell
import com.rout.navi.screen.main.tab.home.holder.HomeCategory
import com.rout.navi.screen.main.tab.home.holder.RecentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class HomeTab : Tab() {
    override val id = globalClass.generateUid()
    val scope = CoroutineScope(Dispatchers.IO)
    override val header = globalClass.getString(R.string.home_tab_header)
    val recentFiles = mutableStateListOf<RecentFile>()
    val pinnedFiles = mutableStateListOf<LocalFileHolder>()

    var showCustomizeHomeTabDialog by mutableStateOf(false)

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        requestHomeToolbarUpdate()
    }

    override suspend fun getSubtitle() = emptyString

    override suspend fun getTitle() = globalClass.getString(R.string.home_tab_title)

    suspend fun getPinnedFiles() {
        pinnedFiles.clear()
        pinnedFiles.addAll(
            globalClass.preferencesManager.pinnedFiles.map { path ->
                RootShell.holderFor(File(path))
            }
        )
    }

    fun fetchRecentFiles() {
        if (recentFiles.isNotEmpty()) return

        scope.launch {
            recentFiles.addAll(getRecentFiles())
        }
    }

    fun getMainCategories() = arrayListOf<HomeCategory>().apply {
        val mainActivityManager = globalClass.mainActivityManager
        add(
            HomeCategory(
                name = globalClass.getString(R.string.images),
                icon = Icons.Rounded.Image,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(IMAGE))
                    )
                }
            )
        )

        add(
            HomeCategory(
                name = globalClass.getString(R.string.videos),
                icon = Icons.Rounded.VideoFile,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(VIDEO))
                    )
                }
            )
        )

        add(
            HomeCategory(
                name = globalClass.getString(R.string.audios),
                icon = Icons.Rounded.AudioFile,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(AUDIO))
                    )
                }
            )
        )

        add(
            HomeCategory(
                name = globalClass.getString(R.string.documents),
                icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(DOCUMENT))
                    )
                }
            )
        )

        add(
            HomeCategory(
                name = globalClass.getString(R.string.archives),
                icon = Icons.Rounded.Archive,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        FilesTab(VirtualFileHolder(ARCHIVE))
                    )
                }
            )
        )

        add(
            HomeCategory(
                name = globalClass.getString(R.string.apps),
                icon = Icons.Rounded.Android,
                onClick = {
                    mainActivityManager.replaceCurrentTabWith(
                        AppsTab()
                    )
                }
            )
        )
    }

    private fun getRecentFiles(): ArrayList<RecentFile> {
        return arrayListOf<RecentFile>().apply {
            addAll(
                StorageProvider.getRawRecentFiles(
                    recentHours = 24,
                    limit = 25
                )
            )
        }
    }

    fun removePinnedFile(file: LocalFileHolder) {
        pinnedFiles.remove(file)
        globalClass.preferencesManager.pinnedFiles = pinnedFiles.map { it.uniquePath }.toSet()
    }
}