package com.raival.compose.file.explorer.screen.main.tab.files.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.emptyString
import com.raival.compose.file.explorer.common.isValidAsFileName
import com.raival.compose.file.explorer.common.ui.Space
import com.raival.compose.file.explorer.screen.main.tab.files.FilesTab
import com.raival.compose.file.explorer.screen.main.tab.files.holder.LocalFileHolder
import com.raival.compose.file.explorer.screen.main.tab.files.task.CompressFormat
import com.raival.compose.file.explorer.screen.main.tab.files.task.CompressTaskParameters
import java.io.File

@Composable
fun FileCompressionDialog(
    show: Boolean,
    tab: FilesTab,
    onDismissRequest: () -> Unit
) {
    if (show) {
        val listContent by remember(tab.activeFolderContent) {
            mutableStateOf(tab.activeFolderContent.map { it.displayName }.toTypedArray())
        }

        // Format terakhir dipakai lagi saat dialog dibuka berikutnya.
        val initialFormat = remember {
            CompressFormat.values()
                .firstOrNull { it.name == globalClass.preferencesManager.defaultCompressFormat }
                ?: CompressFormat.ZIP
        }

        var newNameInput by remember { mutableStateOf(tab.activeFolder.displayName) }
        var selectedFormat by remember { mutableStateOf(initialFormat) }
        var formatMenuExpanded by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }

        val baseName = CompressFormat.removeSuffix(newNameInput.trim())
        val archiveFileName = "$baseName.${selectedFormat.extension}"

        LaunchedEffect(newNameInput, selectedFormat) {
            error = if (newNameInput.isBlank()) {
                emptyString
            } else if (baseName.isEmpty() || !newNameInput.isValidAsFileName()) {
                globalClass.getString(R.string.invalid_file_name)
            } else if (listContent.contains(archiveFileName)) {
                globalClass.getString(R.string.similar_file_exists)
            } else {
                emptyString
            }
        }

        Dialog(
            onDismissRequest = onDismissRequest,
        ) {
            Card(
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        Text(
                            modifier = Modifier.fillMaxWidth(),
                            text = stringResource(R.string.create_archive),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Space(8.dp)
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            TextField(
                                modifier = Modifier.weight(1f),
                                value = newNameInput,
                                onValueChange = {
                                    newNameInput = it
                                },
                                label = { Text(text = stringResource(R.string.name)) },
                                singleLine = true,
                                shape = RoundedCornerShape(6.dp),
                                colors = TextFieldDefaults.colors(
                                    errorIndicatorColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                isError = error.isNotEmpty(),
                                supportingText = if (error.isNotEmpty()) {
                                    { Text(error) }
                                } else null
                            )

                            // Pemilih format di kanan kolom nama, mirip WinRAR.
                            Box {
                                OutlinedButton(
                                    modifier = Modifier.height(56.dp),
                                    onClick = { formatMenuExpanded = true },
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = selectedFormat.displayName,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = null
                                    )
                                }

                                DropdownMenu(
                                    expanded = formatMenuExpanded,
                                    onDismissRequest = { formatMenuExpanded = false }
                                ) {
                                    CompressFormat.values().forEach { format ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${format.displayName}  (.${format.extension})"
                                                )
                                            },
                                            leadingIcon = if (format == selectedFormat) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Done,
                                                        contentDescription = null
                                                    )
                                                }
                                            } else null,
                                            onClick = {
                                                selectedFormat = format
                                                formatMenuExpanded = false

                                                // Simpan pilihan sebagai format default.
                                                globalClass.preferencesManager.defaultCompressFormat =
                                                    format.name
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = stringResource(R.string.compress_format_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (error.isEmpty() && baseName.isNotEmpty()) {
                                    onDismissRequest()
                                    globalClass.taskManager.runTask(
                                        tab.compressTaskHolder!!.id,
                                        CompressTaskParameters(
                                            destPath = File(
                                                (tab.activeFolder as LocalFileHolder).file,
                                                archiveFileName
                                            ).absolutePath,
                                            format = selectedFormat
                                        )
                                    )
                                } else {
                                    globalClass.showMsg(
                                        globalClass.getString(
                                            R.string.archive_name_must_end_with,
                                            selectedFormat.extension
                                        )
                                    )
                                }
                            },
                            enabled = error.isEmpty() && newNameInput.isNotBlank(),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.create),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onDismissRequest()
                            },
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.minimize),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                    }
                }
            }
        }
    }
}
