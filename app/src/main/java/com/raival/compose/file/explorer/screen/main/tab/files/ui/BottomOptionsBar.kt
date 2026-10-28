package com.raival.compose.file.explorer.screen.main.tab.files.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.block
import com.raival.compose.file.explorer.common.detectVerticalSwipe
import com.raival.compose.file.explorer.common.ui.Space
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.task.CopyTask

@Composable
fun BottomOptionsBar(tab: FilesTab) {
    val state = tab.bottomOptionsBarState.collectAsState().value

    AnimatedVisibility(
        visible = state.showQuickOptions && tab.selectedFiles.isNotEmpty(),
        enter = expandIn(expandFrom = Alignment.TopCenter) + slideInVertically(
            initialOffsetY = { it }),
        exit = shrinkOut(shrinkTowards = Alignment.BottomCenter) + slideOutVertically(
            targetOffsetY = { it })

    ) {
        Column {
            HorizontalDivider()

            // Strip mode lintas pilihan, hanya tampil setelah diaktifkan
            // lewat menu opsi. Posisinya tepat di atas toolbar pilihan.
            if (tab.isCrossSelectionActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .block(shape = RectangleShape)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isCrossTab = tab.isCrossTabSelectionEnabled
                    val modeName = stringResource(
                        if (isCrossTab) R.string.select_across_tabs
                        else R.string.select_across_folders
                    )
                    val isSelectionMode = tab.selectionModeEnabled
                    val stateLabel = stringResource(
                        if (isSelectionMode) R.string.enabled else R.string.disabled
                    )
                    val badgeText = stringResource(
                        if (isSelectionMode) R.string.selection_toggle_on
                        else R.string.selection_toggle_off
                    )
                    val accentColor = if (isSelectionMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val borderColor = if (isSelectionMode) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    }
                    val badgeShape = RoundedCornerShape(8.dp)

                    // Badge teks ON/OFF: hanya garis dan warna teks yang
                    // berubah, tanpa latar isi supaya menyatu dengan toolbar.
                    Box(
                        modifier = Modifier
                            .clip(badgeShape)
                            .border(1.dp, borderColor, badgeShape)
                            .clickable { tab.toggleSelectionMode() }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .semantics {
                                contentDescription = "$modeName ($stateLabel)"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = accentColor
                        )
                    }
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = modeName,
                        fontSize = 12.sp,
                        fontWeight = if (isSelectionMode) FontWeight.Medium else FontWeight.Normal,
                        color = accentColor
                    )
                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                        text = stringResource(
                            R.string.selection_count_folders,
                            tab.selectedFiles.size,
                            tab.countSelectedFolders()
                        ),
                        textAlign = TextAlign.End,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        modifier = Modifier
                            .clickable { tab.cancelSelection() }
                            .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        text = stringResource(R.string.cancel),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .block(shape = RectangleShape)
                    .detectVerticalSwipe(
                        onSwipeUp = {
                            tab.toggleFileOptionsMenu(tab.selectedFiles[tab.selectedFiles.keys.first()]!!)
                        }
                    )
            ) {
                // Delete
                IconButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        tab.toggleDeleteConfirmationDialog(true)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                // Cut
                IconButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        globalClass.taskManager.addTask(
                            CopyTask(
                                tab.selectedFiles.values.toList(),
                                deleteSourceFiles = true
                            )
                        )
                        tab.unselectAllFiles()
                    }
                ) {
                    Icon(imageVector = Icons.Rounded.ContentCut, contentDescription = null)
                }

                // Copy
                IconButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        globalClass.taskManager.addTask(
                            CopyTask(
                                tab.selectedFiles.values.toList(),
                                deleteSourceFiles = false
                            )
                        )
                        tab.unselectAllFiles()
                    }
                ) {
                    Icon(imageVector = Icons.Rounded.FileCopy, contentDescription = null)
                }

                // Rename
                IconButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        tab.toggleRenameDialog(true)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FormatColorText,
                        contentDescription = null
                    )
                }

                // Properties
                IconButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        tab.toggleFilePropertiesDialog(true)
                    }
                ) {
                    Icon(imageVector = Icons.Rounded.Info, contentDescription = null)
                }
            }
        }
    }

    HorizontalDivider()

    Row(
        Modifier
            .fillMaxWidth()
            .block(shape = RectangleShape)
            .detectVerticalSwipe(
                onSwipeUp = {
                    tab.toggleBookmarksDialog(true)
                }
            )
    ) {
        if (state.showEmptyRecycleBinButton) {
            BottomOptionsBarButton(Icons.Rounded.DeleteSweep, stringResource(R.string.empty)) {
                tab.unselectAllFiles(false)
                tab.activeFolderContent.forEach {
                    tab.selectedFiles[it.uniquePath] = it
                }
                tab.quickReloadFiles()
                tab.toggleDeleteConfirmationDialog(true)
            }
        } else {
            BottomOptionsBarButton(Icons.Rounded.AddTask, stringResource(R.string.task)) {
                tab.toggleTasksPanel(true)
            }
        }

        BottomOptionsBarButton(Icons.Rounded.Search, stringResource(R.string.search)) {
            tab.toggleSearchPenal(true)
        }

        if (!state.showEmptyRecycleBinButton && state.showCreateNewContentButton) {
            BottomOptionsBarButton(Icons.Rounded.Add, stringResource(R.string.create)) {
                tab.toggleCreateNewFileDialog(true)
            }
        }

        if (state.showMoreOptionsButton && tab.selectedFiles.isNotEmpty()) {
            BottomOptionsBarButton(Icons.Rounded.SelectAll, stringResource(R.string.select_all)) {
                if (tab.selectedFiles.size == tab.activeFolderContent.size) {
                    tab.unselectAllFiles()
                } else {
                    tab.unselectAllFiles(false)
                    tab.activeFolderContent.forEach {
                        tab.selectedFiles[it.uniquePath] = it
                    }
                    tab.quickReloadFiles()
                }
            }

            BottomOptionsBarButton(Icons.Rounded.MoreVert, stringResource(R.string.options)) {
                tab.toggleFileOptionsMenu(tab.selectedFiles[tab.selectedFiles.keys.first()]!!)
            }
        } else {
            BottomOptionsBarButton(Icons.AutoMirrored.Rounded.Sort, stringResource(R.string.sort)) {
                tab.toggleSortingMenu(true)
            }

            BottomOptionsBarButton(Icons.Rounded.Bookmark, stringResource(R.string.bookmarks)) {
                tab.toggleBookmarksDialog(true)
            }
        }
    }
}

@Composable
fun RowScope.BottomOptionsBarButton(
    imageVector: ImageVector,
    text: String,
    view: @Composable () -> Unit = {},
    onClick: () -> Unit
) {
    val preferencesManager = globalClass.preferencesManager

    Column(
        modifier = Modifier
            .weight(1f)
            .clickable {
                onClick()
            }
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!preferencesManager.showBottomBarLabels) {
            Space(size = 4.dp)
        }

        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = imageVector,
            contentDescription = null
        )

        Space(size = 4.dp)

        if (preferencesManager.showBottomBarLabels) {
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        view()
    }
}
