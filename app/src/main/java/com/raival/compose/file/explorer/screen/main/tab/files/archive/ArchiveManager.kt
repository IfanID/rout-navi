package com.raival.compose.file.explorer.screen.main.tab.files.archive

import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.isNot
import com.raival.compose.file.explorer.common.removeIf
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.holder.VirtualFileHolder
import kotlinx.coroutines.runBlocking

class ArchiveManager {
    private val archiveList = hashMapOf<LocalFileHolder, ArchiveTree>()

    /**
     * Membuang pohon arsip yang berkas sumbernya sudah tidak ada.
     *
     * @return kumpulan path arsip yang tidak valid.
     */
    suspend fun validateArchiveTrees(): Set<String> {
        val invalidTrees =
            archiveList.values.filter { !it.source.isValid() }.map { it.source.uniquePath }

        archiveList.removeIf { localFileHolder, _ ->
            runBlocking { !localFileHolder.isValid() }
        }

        return invalidTrees.toSet()
    }

    suspend fun checkForSourceChanges(): Boolean {
        var foundChanges = false
        archiveList.values.forEach { archiveTree ->
            if (archiveTree.source.lastModified isNot archiveTree.timeStamp ||
                archiveTree.checkExtractedFiles().isNotEmpty()
            ) {
                foundChanges = true
            }
        }
        return foundChanges
    }

    fun openArchive(archive: LocalFileHolder) {
        val existingTreeKey = archiveList.keys.find { archive.uniquePath == it.uniquePath }

        if (existingTreeKey != null) {
            archiveList[existingTreeKey]?.let { existingTree ->
                if (existingTree.timeStamp == archive.lastModified) {
                    openTree(existingTree)
                    return
                } else {
                    archiveList.remove(existingTreeKey)
                }
            }
        }

        val tree = ArchiveFormat.createTree(archive)

        if (tree == null) {
            globalClass.showMsg(R.string.unsupported_archive)
            return
        }

        archiveList[archive] = tree
        openTree(tree)
    }

    private fun openTree(tree: ArchiveTree) {
        globalClass.mainActivityManager.let { mainManager ->
            val tab = mainManager.getActiveTab()
            if (tab is FilesTab) {
                if (tab.activeFolder is VirtualFileHolder) {
                    mainManager.replaceCurrentTabWith(
                        tab = FilesTab(
                            tree.createRootContentHolder()
                        ),
                        keepCurrentTabAsParent = true
                    )
                } else {
                    tab.openFolder(tree.createRootContentHolder())
                }
            } else {
                mainManager.replaceCurrentTabWith(
                    FilesTab(tree.createRootContentHolder())
                )
            }
        }
    }
}
