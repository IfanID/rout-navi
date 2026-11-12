package com.raival.compose.file.explorer.screen.analyzer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.toFormattedSize
import com.raival.compose.file.explorer.screen.analyzer.StorageAnalyzerTab
import com.raival.compose.file.explorer.screen.analyzer.model.StorageNode
import kotlin.math.roundToInt

/**
 * Isi tab analisis penyimpanan. Tekan lama untuk masuk mode pilih, lalu
 * hapus atau pindahkan beberapa entri sekaligus.
 */
@Composable
fun ColumnScope.StorageAnalyzerContentView(tab: StorageAnalyzerTab) {
    LaunchedEffect(tab.id) {
        tab.refreshIfNeeded()
    }

    val selected = remember { mutableStateMapOf<String, StorageNode>() }
    var selectionMode by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }

    fun clearSelection() {
        selected.clear()
        selectionMode = false
    }

    fun toggleSelection(entry: StorageNode) {
        val key = entry.file.absolutePath
        if (selected.containsKey(key)) {
            selected.remove(key)
            if (selected.isEmpty()) selectionMode = false
        } else {
            selected[key] = entry
            selectionMode = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SummaryCard(tab)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (tab.canGoUp) {
                IconButton(onClick = { tab.onBackPressed() }) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = stringResource(R.string.go_up)
                    )
                }
            }
            Text(
                modifier = Modifier.weight(1f),
                text = tab.currentNode?.file?.absolutePath.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = { tab.refresh() },
                enabled = !tab.isScanning
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.scan_again)
                )
            }
        }

        if (tab.isScanning) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
        }

        if (selectionMode) {
            SelectionBar(
                count = selected.size,
                onClear = { clearSelection() },
                onMove = { showMove = true },
                onDelete = { confirmDelete = true }
            )
        }

        when {
            tab.scanFailed && !tab.isScanning -> Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = stringResource(R.string.storage_analyzer_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            !tab.isScanning && tab.entries.isEmpty() -> Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = stringResource(R.string.storage_analyzer_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(tab.entries, key = { it.file.absolutePath }) { entry ->
                StorageEntryRow(
                    entry = entry,
                    parentSize = tab.totalSize,
                    isSelected = selected.containsKey(entry.file.absolutePath),
                    onClick = {
                        if (selectionMode) {
                            toggleSelection(entry)
                        } else if (entry.isFolder) {
                            tab.openFolder(entry)
                        }
                    },
                    onLongClick = {
                        if (!selected.containsKey(entry.file.absolutePath)) {
                            toggleSelection(entry)
                        }
                    }
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.storage_delete_multi_confirm, selected.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        tab.deleteEntries(selected.values.toList())
                        confirmDelete = false
                        clearSelection()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showMove) {
        MoveToDialog(
            tab = tab,
            targets = selected.values.toList(),
            onDismiss = { showMove = false },
            onMoved = { clearSelection() }
        )
    }
}

@Composable
private fun SelectionBar(
    count: Int,
    onClear: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClear) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.storage_clear_selection),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.storage_selected_count, count),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        IconButton(onClick = onMove) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                contentDescription = stringResource(R.string.storage_move),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** Pemilih folder tujuan; memakai pohon hasil pindai sehingga tanpa IO. */
@Composable
private fun MoveToDialog(
    tab: StorageAnalyzerTab,
    targets: List<StorageNode>,
    onDismiss: () -> Unit,
    onMoved: () -> Unit
) {
    var picker by remember { mutableStateOf(tab.currentNode) }
    val node = picker ?: return
    val canMove = targets.isNotEmpty() && targets.all { tab.canMoveTo(it, node.file) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.storage_move_to)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (node.parent != null) {
                        IconButton(onClick = { picker = node.parent }) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowUpward,
                                contentDescription = stringResource(R.string.go_up)
                            )
                        }
                    }
                    Text(
                        modifier = Modifier.weight(1f),
                        text = node.file.absolutePath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val folders = node.children
                    .filter { it.isFolder }
                    .sortedBy { it.name.lowercase() }

                if (folders.isEmpty()) {
                    Text(
                        modifier = Modifier.padding(vertical = 8.dp),
                        text = stringResource(R.string.storage_no_subfolder),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(folders, key = { it.file.absolutePath }) { child ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { picker = child }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp),
                                    text = child.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canMove,
                onClick = {
                    tab.moveEntries(targets, node.file)
                    onMoved()
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.storage_move_here))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun SummaryCard(tab: StorageAnalyzerTab) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (tab.isScanning) {
                    stringResource(R.string.storage_scanned_size)
                } else {
                    stringResource(R.string.total_size)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                // Saat memindai, angka ini bertambah mengikuti berkas yang dibaca.
                text = if (tab.isScanning) {
                    tab.scannedSize.toFormattedSize()
                } else {
                    tab.totalSize.toFormattedSize()
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (tab.isScanning) {
                    stringResource(R.string.analyzing_file_count, tab.scannedFiles)
                } else {
                    stringResource(R.string.total_items, tab.totalItems)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StorageEntryRow(
    entry: StorageNode,
    parentSize: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val fraction = if (parentSize > 0L) {
        (entry.size.toFloat() / parentSize.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    // Persentase dibulatkan; nilai kecil tetap ditandai agar tidak tampil 0%.
    val percent = fraction * 100f
    val percentText = stringResource(
        R.string.storage_percent,
        when {
            percent >= 1f -> percent.roundToInt().toString()
            entry.size > 0L -> "<1"
            else -> "0"
        }
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    Color.Transparent
                }
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics { selected = isSelected }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (entry.isFolder) {
                Icons.Rounded.Folder
            } else {
                Icons.AutoMirrored.Rounded.InsertDriveFile
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.size.toFormattedSize(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            val detail = when {
                !entry.accessible -> "$percentText · " + stringResource(R.string.inaccessible)
                entry.isFolder -> "$percentText · " + stringResource(R.string.total_items, entry.itemCount)
                else -> percentText
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
