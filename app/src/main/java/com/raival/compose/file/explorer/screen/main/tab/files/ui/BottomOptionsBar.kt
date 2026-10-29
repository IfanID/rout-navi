package com.raival.compose.file.explorer.screen.main.tab.files.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
                val isCrossTab = tab.isCrossTabSelectionEnabled
                val isSelectionMode = tab.selectionModeEnabled
                // Nama mode menyebut objek yang dipilih/dibuka, bukan penjelasan.
                val modeName = stringResource(
                    when {
                        isCrossTab && isSelectionMode -> R.string.select_across_tabs
                        isCrossTab -> R.string.browse_across_tabs
                        isSelectionMode -> R.string.select_across_folders
                        else -> R.string.browse_across_folders
                    }
                )
                val stateLabel = stringResource(
                    if (isSelectionMode) R.string.enabled else R.string.disabled
                )
                val badgeText = stringResource(
                    if (isSelectionMode) R.string.selection_toggle_on
                    else R.string.selection_toggle_off
                )
                // Keterangan aksi untuk pembaca layar dan baris kedua label.
                val toggleHint = stringResource(
                    if (isSelectionMode) R.string.selection_toggle_on_hint
                    else R.string.selection_toggle_off_hint
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
                val badgeBackground = if (isSelectionMode) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                }
                val pillShape = RoundedCornerShape(50)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .block(shape = RectangleShape)
                        .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tombol pil ON/OFF yang menonjol agar mudah ditekan.
                    Box(
                        modifier = Modifier
                            .clip(pillShape)
                            .background(badgeBackground)
                            .border(1.dp, borderColor, pillShape)
                            .clickable { tab.toggleSelectionMode() }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                            .semantics {
                                contentDescription = "$modeName ($stateLabel) — $toggleHint"
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

                    // Nama mode saja; keterangan ON/OFF hanya untuk pembaca layar.
                    Text(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                        text = modeName,
                        fontSize = 12.sp,
                        fontWeight = if (isSelectionMode) FontWeight.Medium else FontWeight.Normal,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Jumlah pilihan tetap di kanan, sebelum tombol Batal.
                    Text(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        text = stringResource(
                            R.string.selection_count_folders,
                            state.selectedCount,
                            state.selectedFolderCount
                        ),
                        textAlign = TextAlign.End,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )

                    // Tombol Batal berbentuk pil dengan ikon.
                    Row(
                        modifier = Modifier
                            .clip(pillShape)
                            .clickable { tab.cancelSelection() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Box(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.cancel),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
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
